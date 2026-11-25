/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

//! Restore original package from a chunked directory.

#![allow(clippy::unnecessary_sort_by)]

use anyhow::{Context, Result};
use clap::Parser;
use cts_chunker::chunk_util::{self, IndexEntry};
use std::fs;
use std::os::fd::AsRawFd;
use std::os::unix::fs::PermissionsExt;
use std::path::PathBuf;
use std::time::Instant;

#[derive(Parser, Debug)]
struct Args {
    #[arg(long, help = "Path to the chunked directory")]
    chunked_dir_path: PathBuf,

    #[arg(long, help = "Path to output directory where files will be restored")]
    output_dir_path: PathBuf,
}

fn main() -> Result<()> {
    let args = Args::parse();
    let chunked_dir_path = args.chunked_dir_path;
    let restored_dir = args.output_dir_path;

    // 1. Parse index JSON file directly from the chunked directory
    println!("Loading the index file _chunks_index.json from {:?}", chunked_dir_path);
    let index_path = chunked_dir_path.join(chunk_util::CHUNKS_INDEX_FILENAME);
    let index_file = fs::File::open(&index_path)
        .with_context(|| format!("Unable to locate required index catalog at: {:?}", index_path))?;
    let reader = std::io::BufReader::new(index_file);
    let index: Vec<IndexEntry> =
        serde_json::from_reader(reader).with_context(|| "Index catalog parsing failed")?;

    // 2. Restore filesystem items
    println!("Restoring modules to {:?}", restored_dir);
    let restore_start_time = Instant::now();
    fs::create_dir_all(&restored_dir).with_context(|| {
        format!("Failed creating restored destination folder: {:?}", restored_dir)
    })?;
    let chunks_dir = chunked_dir_path.join(chunk_util::CHUNKS_DIR_NAME);

    let mut files = Vec::new();
    let mut symlinks = Vec::new();

    for entry in index {
        if entry.symlink_target.is_some() {
            symlinks.push(entry);
        } else {
            files.push(entry);
        }
    }

    // Pre-create all unique parent directories beforehand in a single thread
    // to avoid concurrent create_dir_all call contention in the worker threads.
    let mut parent_dirs = std::collections::HashSet::new();
    for entry in &files {
        let target_path = restored_dir.join(&entry.path);
        if let Some(parent) = target_path.parent() {
            parent_dirs.insert(parent.to_path_buf());
        }
    }

    let mut parent_dirs: Vec<std::path::PathBuf> = parent_dirs.into_iter().collect();
    parent_dirs.sort_by(|a, b| a.as_os_str().len().cmp(&b.as_os_str().len()));

    for parent in parent_dirs {
        if !parent.exists() {
            fs::create_dir_all(&parent)
                .with_context(|| format!("Failed to create parent directory: {:?}", parent))?;
        }
    }

    // Now restore files in parallel using a thread pool.
    let num_workers = std::thread::available_parallelism()
        .map(|n| n.get() * 4) // Multi-threaded I/O bound tasks benefit from high concurrency
        .unwrap_or(32)
        .max(4);

    let (tx, rx) = std::sync::mpsc::channel::<IndexEntry>();
    let rx = std::sync::Arc::new(std::sync::Mutex::new(rx));

    let mut workers = Vec::with_capacity(num_workers);
    for _ in 0..num_workers {
        let rx_clone = rx.clone();
        let chunks_dir_clone = chunks_dir.clone();
        let restored_dir_clone = restored_dir.clone();

        workers.push(std::thread::spawn(move || -> Result<()> {
            loop {
                let entry = {
                    let rx_lock = rx_clone
                        .lock()
                        .map_err(|e| anyhow::anyhow!("Mutex lock poisoned: {}", e))?;
                    match rx_lock.recv() {
                        Ok(item) => item,
                        Err(_) => break, // Channel is closed/empty
                    }
                };

                let target_path = restored_dir_clone.join(&entry.path);
                let chunks = entry.chunks.clone();

                chunk_util::restore_file(&target_path, &chunks_dir_clone, &chunks)
                    .with_context(|| format!("Restoration failed for item: {:?}", target_path))?;

                // Restore exact permission modes
                fs::set_permissions(&target_path, fs::Permissions::from_mode(entry.mode))
                    .with_context(|| {
                        format!("Failed applying permissions configuration on: {:?}", target_path)
                    })?;
            }
            Ok(())
        }));
    }

    // Send files to worker threads
    for entry in files {
        let _ = tx.send(entry);
    }
    drop(tx); // Signal threads that no more files are coming

    // Wait for all workers to finish and check for errors
    for worker in workers {
        match worker.join() {
            Ok(Ok(())) => {}
            Ok(Err(e)) => return Err(e),
            Err(e) => anyhow::bail!("Worker thread panicked: {:?}", e),
        }
    }

    // Second pass: restore symlinks once parent folders are fully populated (sequential since they are fast)
    for entry in &symlinks {
        let rel_path = &entry.path;
        let target_path = restored_dir.join(rel_path);
        let link_target = entry.symlink_target.as_ref().unwrap();

        // Clean up any stale/existing items
        if target_path.exists() || target_path.is_symlink() {
            fs::remove_file(&target_path).with_context(|| {
                format!("Failed removing existing path element: {:?}", target_path)
            })?;
        }

        if let Some(parent) = target_path.parent() {
            if !parent.exists() {
                fs::create_dir_all(parent)?;
            }
        }

        // Recreate symlink
        std::os::unix::fs::symlink(link_target, &target_path).with_context(|| {
            format!("Symlink mapping failed from {:?} to {:?}", link_target, target_path)
        })?;
    }
    println!("Restoring modules took {:.2}s", restore_start_time.elapsed().as_secs_f64());

    // 3. Clean up the chunked directory after successful restoration.
    //
    // Unlinking the index is the commit point that cts-tradefed relies on:
    // while the index exists every chunk blob is intact and the restore can be
    // re-run; once it is gone testcases/ is complete. It must therefore be
    // unlinked on its own, before parallel_remove_dir_all deletes any blob in
    // an unspecified order, and the restored data must be on disk first so a
    // host crash cannot leave a missing index next to truncated files.
    // syncfs flushes only the filesystem holding the restored tree (rather
    // than every mount on the host, as sync would) and reports writeback
    // errors, so a failed flush aborts before the commit point.
    let restored_dir_file = fs::File::open(&restored_dir)
        .with_context(|| format!("Failed to open restored directory: {:?}", restored_dir))?;
    nix::unistd::syncfs(restored_dir_file.as_raw_fd())
        .with_context(|| format!("Failed to sync restored directory: {:?}", restored_dir))?;
    fs::remove_file(&index_path)
        .with_context(|| format!("Failed to remove index catalog: {:?}", index_path))?;
    fs::File::open(&chunked_dir_path)
        .and_then(|dir| dir.sync_all())
        .with_context(|| format!("Failed to sync chunked directory: {:?}", chunked_dir_path))?;

    println!("Cleaning up the chunked directory: {:?}", chunked_dir_path);
    let clean_up_start_time = Instant::now();
    parallel_remove_dir_all(&chunked_dir_path)
        .with_context(|| format!("Failed to clean up chunked directory: {:?}", chunked_dir_path))?;
    println!(
        "Cleaning up the chunked directory took {:.2}s",
        clean_up_start_time.elapsed().as_secs_f64()
    );

    Ok(())
}

/// Parallelized version of `fs::remove_dir_all` to handle directories with
/// hundreds of thousands of files efficiently by distributing unlinking tasks
/// across multiple worker threads.
fn parallel_remove_dir_all(path: &std::path::Path) -> std::io::Result<()> {
    if !path.exists() {
        return Ok(());
    }

    let mut files = Vec::new();
    let mut dirs = Vec::new();

    // Helper to recursively collect files and directories.
    fn collect(
        p: &std::path::Path,
        files: &mut Vec<std::path::PathBuf>,
        dirs: &mut Vec<std::path::PathBuf>,
    ) -> std::io::Result<()> {
        if p.is_dir() {
            dirs.push(p.to_path_buf());
            for entry in std::fs::read_dir(p)? {
                let entry = entry?;
                collect(&entry.path(), files, dirs)?;
            }
        } else {
            files.push(p.to_path_buf());
        }
        Ok(())
    }

    collect(path, &mut files, &mut dirs)?;

    let num_workers =
        std::thread::available_parallelism().map(|n| n.get() * 4).unwrap_or(32).max(4);

    let mut worker_files = vec![Vec::new(); num_workers];
    for (i, file) in files.into_iter().enumerate() {
        worker_files[i % num_workers].push(file);
    }

    let mut workers = Vec::with_capacity(num_workers);
    for chunk in worker_files {
        workers.push(std::thread::spawn(move || {
            for file in chunk {
                let _ = std::fs::remove_file(file);
            }
        }));
    }

    for worker in workers {
        let _ = worker.join();
    }

    // Sort directories by path length in descending order to delete deepest first.
    dirs.sort_by(|a, b| b.as_os_str().len().cmp(&a.as_os_str().len()));

    for dir in dirs {
        std::fs::remove_dir(dir)?;
    }

    Ok(())
}
