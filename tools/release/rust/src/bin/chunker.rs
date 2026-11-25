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

//! Chunk a directory using FastCDC.

use anyhow::{Context, Result};
use clap::Parser;
use cts_chunker::chunk_util::{self, BlobWriter, IndexEntry};
use std::fs;
use std::os::unix::fs::MetadataExt;
use std::path::PathBuf;
use walkdir::WalkDir;

#[derive(Parser, Debug)]
struct Args {
    #[arg(long, help = "Path to the source directory to chunk")]
    source_dir_path: PathBuf,

    #[arg(long, help = "Path to the output directory for chunked files")]
    output_dir_path: PathBuf,

    #[arg(long, default_value_t = 32, help = "Average chunk size in KB")]
    avg_chunk_size_kb: usize,
}

fn main() -> Result<()> {
    let args = Args::parse();
    let source_dir_path = args.source_dir_path;
    let output_dir_path = args.output_dir_path;

    // Start from an empty chunks directory: blobs are named by number rather than by
    // content, so blobs left over from a previous run must not be reused.
    let chunks_dir = output_dir_path.join(chunk_util::CHUNKS_DIR_NAME);
    if chunks_dir.exists() {
        fs::remove_dir_all(&chunks_dir).with_context(|| {
            format!("Failed to remove previous chunks directory: {:?}", chunks_dir)
        })?;
    }
    let mut blobs = BlobWriter::new(&chunks_dir)?;

    let mut index = Vec::new();

    // Walk in a fixed order: blobs are numbered in index order, so the read_dir order,
    // which depends on the filesystem, would make packages differ between build hosts.
    for entry in WalkDir::new(&source_dir_path).follow_links(false).sort_by_file_name() {
        let entry = entry.with_context(|| "Walkdir failed accessing directory layout")?;
        let file_path = entry.path();
        let st_metadata = fs::symlink_metadata(file_path)
            .with_context(|| format!("Failed to query file metadata: {:?}", file_path))?;

        // Skip directories
        if st_metadata.is_dir() {
            continue;
        }

        let rel_path = file_path.strip_prefix(&source_dir_path)?;
        let rel_path_str = rel_path.to_string_lossy().replace('\\', "/");

        let mut symlink_target = None;
        let mut chunks = Vec::new();

        if st_metadata.file_type().is_symlink() {
            let link = fs::read_link(file_path)
                .with_context(|| format!("Failed to read link for: {:?}", file_path))?;
            symlink_target = Some(link.to_string_lossy().into_owned());
        } else if st_metadata.is_file() {
            chunks = chunk_util::chunk_file(file_path, &mut blobs, args.avg_chunk_size_kb)
                .with_context(|| format!("Failed chunking logic on: {:?}", file_path))?;
        }

        index.push(IndexEntry {
            path: rel_path_str,
            mode: st_metadata.mode(),
            symlink_target,
            chunks,
        });
    }

    // Save global chunks index catalog directly in the output directory
    let index_path = output_dir_path.join(chunk_util::CHUNKS_INDEX_FILENAME);
    let index_file = fs::File::create(&index_path)
        .with_context(|| format!("Failed to construct index file: {:?}", index_path))?;
    let writer = std::io::BufWriter::new(index_file);
    serde_json::to_writer_pretty(writer, &index)
        .with_context(|| "Failed to serialize metadata schema layout JSON")?;

    Ok(())
}
