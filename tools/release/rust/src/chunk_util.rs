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

use anyhow::{Context, Result};
use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::fs;
use std::io::Write;
use std::path::{Path, PathBuf};

/// The subdirectory name within the zipped bundle where the unique chunk blobs are stored,
/// named by [`blob_file_name`].
pub const CHUNKS_DIR_NAME: &str = "_chunks";

/// The filename of the generated JSON index that maps archive file paths back to their chunks.
pub const CHUNKS_INDEX_FILENAME: &str = "_chunks_index.json";

/// Number of digits in a blob file name. The fixed width keeps the lexicographic order of
/// the names, in which soong_zip stores zip entries, equal to their numeric order.
pub const BLOB_NAME_WIDTH: usize = 8;

/// Number of blobs that fit in [`BLOB_NAME_WIDTH`] digits.
const MAX_BLOBS: usize = 10usize.pow(BLOB_NAME_WIDTH as u32);

/// Metadata descriptor for an individual file chunk.
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct ChunkInfo {
    /// The number of the blob holding this chunk, see [`BlobWriter`].
    pub blob: u32,
    /// The offset in bytes where this chunk resides in the original unchunked file.
    pub offset: u64,
}

/// Descriptor mapping a file target to its chunks, mode, and symlink target.
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct IndexEntry {
    /// The relative path of the file or symbolic link inside the zipped package.
    pub path: String,
    /// The UNIX file permission mode (e.g. read, write, execute bits).
    pub mode: u32,
    /// The destination link target if this entry represents a UNIX symbolic link.
    pub symlink_target: Option<String>,
    /// The list of constituent chunks making up the target file's content.
    pub chunks: Vec<ChunkInfo>,
}

/// Returns the file name of blob number `blob` within the chunks directory.
pub fn blob_file_name(blob: u32) -> String {
    format!("{:0width$}", blob, width = BLOB_NAME_WIDTH)
}

/// Stores unique chunks as blobs in a chunks directory, numbered in the order in which
/// they are first added.
///
/// The chunker adds chunks in the order the restorer first reads them, so the blob names
/// sort in restore order. soong_zip stores zip entries sorted by name, so extracting the
/// package then creates the blobs in restore order too, and filesystems mostly lay them
/// out on disk in creation order. On rotational disks that turns the restore into mostly
/// sequential reads instead of a seek per chunk. Chunks repeated later keep the number of
/// their first use, so repeated runs of chunks are contiguous as well.
pub struct BlobWriter {
    chunks_dir: PathBuf,
    blobs: HashMap<[u8; 32], u32>,
}

impl BlobWriter {
    /// Creates a writer for `chunks_dir`, which must be missing or empty: blobs are named
    /// by number rather than by content, so a blob left over from an earlier run would be
    /// reused with the wrong content.
    pub fn new(chunks_dir: &Path) -> Result<Self> {
        fs::create_dir_all(chunks_dir)
            .with_context(|| format!("Failed to create chunks directory: {:?}", chunks_dir))?;
        let mut entries = fs::read_dir(chunks_dir)
            .with_context(|| format!("Failed to list chunks directory: {:?}", chunks_dir))?;
        anyhow::ensure!(
            entries.next().is_none(),
            "Chunks directory is not empty: {:?}",
            chunks_dir
        );
        Ok(Self { chunks_dir: chunks_dir.to_path_buf(), blobs: HashMap::new() })
    }

    /// Returns the number of the blob holding `data`, writing the blob the first time
    /// `data` is added.
    fn add(&mut self, data: &[u8]) -> Result<u32> {
        let digest: [u8; 32] = ring::digest::digest(&ring::digest::SHA256, data)
            .as_ref()
            .try_into()
            .context("Unexpected SHA-256 digest length")?;
        if let Some(&blob) = self.blobs.get(&digest) {
            return Ok(blob);
        }
        anyhow::ensure!(
            self.blobs.len() < MAX_BLOBS,
            "Too many unique chunks for {}-digit blob names",
            BLOB_NAME_WIDTH
        );
        let blob = u32::try_from(self.blobs.len()).context("Too many unique chunks")?;
        let blob_path = self.chunks_dir.join(blob_file_name(blob));
        fs::write(&blob_path, data)
            .with_context(|| format!("Failed to write chunk blob: {:?}", blob_path))?;
        self.blobs.insert(digest, blob);
        Ok(blob)
    }
}

/// Divides a file into content-defined chunks using FastCDC and stores the unique ones in
/// `blobs`.
pub fn chunk_file(
    path: &Path,
    blobs: &mut BlobWriter,
    avg_chunk_size_kb: usize,
) -> Result<Vec<ChunkInfo>> {
    let metadata = fs::symlink_metadata(path)
        .with_context(|| format!("Failed to query metadata for file: {:?}", path))?;
    if metadata.len() == 0 {
        return Ok(Vec::new());
    }

    let file = fs::File::open(path)?;
    let avg_size = avg_chunk_size_kb * 1024;
    let min_size = avg_size / 4;
    let max_size = avg_size * 4;

    let chunker = fastcdc::v2020::StreamCDC::with_level(
        file,
        min_size,
        avg_size,
        max_size,
        fastcdc::v2020::Normalization::Level2,
    );

    let mut chunk_infos = Vec::new();

    for result in chunker {
        let chunk = result?;
        let blob = blobs.add(&chunk.data)?;
        chunk_infos.push(ChunkInfo { blob, offset: chunk.offset });
    }

    Ok(chunk_infos)
}

/// Rebuilds the target file by concatenating its unique chunks in sequence.
pub fn restore_file(path: &Path, chunks_dir: &Path, chunks: &[ChunkInfo]) -> Result<()> {
    if let Some(parent) = path.parent() {
        if !parent.exists() {
            fs::create_dir_all(parent).with_context(|| {
                format!("Failed to create parent folder structure for file: {:?}", path)
            })?;
        }
    }

    // A previous interrupted restore may have left this file behind with its
    // final, possibly read-only, mode applied; File::create cannot truncate
    // such a file, so remove it first to keep retries idempotent.
    if fs::symlink_metadata(path).is_ok() {
        fs::remove_file(path)
            .with_context(|| format!("Failed to remove existing target file: {:?}", path))?;
    }

    let mut output_file = fs::File::create(path)
        .with_context(|| format!("Failed to create target restored file: {:?}", path))?;

    for chunk_info in chunks {
        let chunk_path = chunks_dir.join(blob_file_name(chunk_info.blob));
        let chunk_data = fs::read(&chunk_path)
            .with_context(|| format!("Failed to read chunk blob: {:?}", chunk_path))?;
        output_file.write_all(&chunk_data)?;
    }

    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use tempfile::TempDir;

    #[test]
    fn test_empty_file() {
        let temp_dir = TempDir::new().unwrap();
        let file_path = temp_dir.path().join("empty.txt");
        fs::write(&file_path, "").unwrap();

        let chunks_dir = temp_dir.path().join("chunks");
        let mut blobs = BlobWriter::new(&chunks_dir).unwrap();
        let chunks = chunk_file(&file_path, &mut blobs, 32).unwrap();
        assert!(chunks.is_empty());

        let restored_path = temp_dir.path().join("restored_empty.txt");
        restore_file(&restored_path, &chunks_dir, &chunks).unwrap();
        assert!(restored_path.exists());
        assert_eq!(fs::read_to_string(&restored_path).unwrap(), "");
    }

    #[test]
    fn test_chunk_and_restore_identity() {
        let temp_dir = TempDir::new().unwrap();
        let file_path = temp_dir.path().join("sample.txt");
        let original_content =
            "Content defined chunking (CDC) logic translation in Rust.\n".repeat(200);
        fs::write(&file_path, &original_content).unwrap();

        let chunks_dir = temp_dir.path().join("chunks");
        let mut blobs = BlobWriter::new(&chunks_dir).unwrap();
        let chunks = chunk_file(&file_path, &mut blobs, 32).unwrap();
        assert!(!chunks.is_empty());

        let restored_path = temp_dir.path().join("sample_restored.txt");
        restore_file(&restored_path, &chunks_dir, &chunks).unwrap();
        assert!(restored_path.exists());

        let restored_content = fs::read_to_string(&restored_path).unwrap();
        assert_eq!(original_content, restored_content);
    }

    #[test]
    fn test_restore_overwrites_read_only_leftover() {
        use std::os::unix::fs::PermissionsExt;

        let temp_dir = TempDir::new().unwrap();
        let file_path = temp_dir.path().join("sample.txt");
        let original_content = "Restored content.\n".repeat(200);
        fs::write(&file_path, &original_content).unwrap();

        let chunks_dir = temp_dir.path().join("chunks");
        let mut blobs = BlobWriter::new(&chunks_dir).unwrap();
        let chunks = chunk_file(&file_path, &mut blobs, 32).unwrap();

        // Simulate a file left behind read-only by an interrupted restore.
        let restored_path = temp_dir.path().join("sample_restored.txt");
        fs::write(&restored_path, "stale").unwrap();
        fs::set_permissions(&restored_path, fs::Permissions::from_mode(0o444)).unwrap();

        restore_file(&restored_path, &chunks_dir, &chunks).unwrap();
        assert_eq!(fs::read_to_string(&restored_path).unwrap(), original_content);
    }

    /// Returns `len` pseudo-random bytes, so that FastCDC finds varied chunk boundaries.
    fn random_bytes(seed: u64, len: usize) -> Vec<u8> {
        let mut state = seed.wrapping_mul(0x9E37_79B9_7F4A_7C15) | 1;
        (0..len)
            .map(|_| {
                state ^= state << 13;
                state ^= state >> 7;
                state ^= state << 17;
                (state >> 24) as u8
            })
            .collect()
    }

    /// Chunks `path` into `blobs` and returns the blob numbers of its chunks.
    fn blob_numbers(path: &Path, blobs: &mut BlobWriter) -> Vec<u32> {
        chunk_file(path, blobs, 32).unwrap().iter().map(|chunk| chunk.blob).collect()
    }

    #[test]
    fn test_blobs_are_numbered_in_first_use_order() {
        let temp_dir = TempDir::new().unwrap();
        let first = temp_dir.path().join("first.bin");
        let second = temp_dir.path().join("second.bin");
        let copy = temp_dir.path().join("copy_of_first.bin");
        fs::write(&first, random_bytes(1, 300_000)).unwrap();
        fs::write(&second, random_bytes(2, 300_000)).unwrap();
        fs::copy(&first, &copy).unwrap();

        let chunks_dir = temp_dir.path().join("chunks");
        let mut blobs = BlobWriter::new(&chunks_dir).unwrap();
        let first_blobs = blob_numbers(&first, &mut blobs);
        let second_blobs = blob_numbers(&second, &mut blobs);
        let copy_blobs = blob_numbers(&copy, &mut blobs);

        // New chunks are numbered consecutively as they are first seen, and
        // repeated chunks keep the number of their first use.
        let first_count = first_blobs.len() as u32;
        let total = first_count + second_blobs.len() as u32;
        assert!(first_count > 1 && second_blobs.len() > 1);
        assert_eq!(first_blobs, (0..first_count).collect::<Vec<_>>());
        assert_eq!(second_blobs, (first_count..total).collect::<Vec<_>>());
        assert_eq!(copy_blobs, first_blobs);

        let mut names: Vec<String> = fs::read_dir(&chunks_dir)
            .unwrap()
            .map(|entry| entry.unwrap().file_name().into_string().unwrap())
            .collect();
        names.sort();
        assert_eq!(names, (0..total).map(blob_file_name).collect::<Vec<_>>());
    }

    #[test]
    fn test_blob_file_names_sort_in_numeric_order() {
        assert_eq!(blob_file_name(42), "00000042");
        let names: Vec<String> =
            [0, 9, 10, 99, 100, 12_345_678, 99_999_999].into_iter().map(blob_file_name).collect();
        assert!(names.iter().all(|name| name.len() == BLOB_NAME_WIDTH));
        assert!(names.windows(2).all(|pair| pair[0] < pair[1]), "not sorted: {:?}", names);
    }

    #[test]
    fn test_blob_writer_rejects_non_empty_dir() {
        let temp_dir = TempDir::new().unwrap();
        let chunks_dir = temp_dir.path().join("chunks");
        fs::create_dir(&chunks_dir).unwrap();
        fs::write(chunks_dir.join(blob_file_name(0)), "stale").unwrap();
        assert!(BlobWriter::new(&chunks_dir).is_err());
    }

    #[test]
    fn test_restore_files_sharing_chunks() {
        let temp_dir = TempDir::new().unwrap();
        let shared = random_bytes(3, 200_000);
        let contents = [
            shared.clone(),
            [random_bytes(4, 100_000), shared.clone()].concat(),
            [shared, random_bytes(5, 50_000)].concat(),
        ];

        let chunks_dir = temp_dir.path().join("chunks");
        let mut blobs = BlobWriter::new(&chunks_dir).unwrap();
        let mut chunk_lists = Vec::new();
        for (i, content) in contents.iter().enumerate() {
            let path = temp_dir.path().join(format!("source{}", i));
            fs::write(&path, content).unwrap();
            chunk_lists.push(chunk_file(&path, &mut blobs, 32).unwrap());
        }
        let references: usize = chunk_lists.iter().map(Vec::len).sum();
        assert!(fs::read_dir(&chunks_dir).unwrap().count() < references, "nothing was shared");

        for (i, (content, chunks)) in contents.iter().zip(&chunk_lists).enumerate() {
            let restored_path = temp_dir.path().join(format!("restored{}", i));
            restore_file(&restored_path, &chunks_dir, chunks).unwrap();
            assert_eq!(&fs::read(&restored_path).unwrap(), content);
        }
    }

    #[test]
    fn test_index_stores_blob_numbers() {
        let chunk = ChunkInfo { blob: 7, offset: 4096 };
        let json = serde_json::to_string(&chunk).unwrap();
        assert_eq!(json, r#"{"blob":7,"offset":4096}"#);
        assert_eq!(serde_json::from_str::<ChunkInfo>(&json).unwrap(), chunk);
    }
}
