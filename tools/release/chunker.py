#!/usr/bin/env python3
#
# Copyright (C) 2026 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License

"""Chunk android-xts.zip using FastCDC.

Usage:
  chunker.py
  --source_zip_path [path of android-xts.zip]
  --output_zip_path [path of output zip file]
  --avg_chunk_size_kb [average chunk size]
"""

import argparse
import datetime
import json
import os
import tempfile
import chunk_util
import zip_util


def _get_args():
  """Parses input arguments."""
  parser = argparse.ArgumentParser()
  parser.add_argument(
      '--source_zip_path', required=True, help='Path to a zip file to chunk'
  )
  parser.add_argument(
      '--output_zip_path',
      help=(
          'Path to chunked zip file. If unset, it will be saved as'
          ' [source_zip_path]_chunked.zip.'
      ),
  )
  parser.add_argument(
      '--avg_chunk_size_kb', default=128, help='Average chunk size in KB'
  )
  return parser.parse_args()


def main():
  args = _get_args()
  source_zip_path = args.source_zip_path
  output_zip_path = args.output_zip_path
  if not output_zip_path:
    output_zip_path = os.path.splitext(source_zip_path)[0] + '-chunked.zip'
  avg_chunk_size_kb = args.avg_chunk_size_kb

  with tempfile.TemporaryDirectory() as temp_dir:
    # 1. Unzip android-cts.zip
    print(f'Unzipping {source_zip_path}...')
    extract_dir = os.path.join(temp_dir, 'extracted')
    zip_util.ZipUtil.unzip_file(source_zip_path, extract_dir)

    # 2. Chunk all files
    chunked_dir = os.path.join(temp_dir, 'chunked')
    chunks_dir = os.path.join(chunked_dir, chunk_util.CHUNKS_DIR_NAME)
    os.makedirs(chunks_dir, exist_ok=True)

    index = []
    for root, _, files in os.walk(extract_dir):
      for file in files:
        file_path = os.path.join(root, file)
        rel_path = os.path.relpath(file_path, extract_dir)

        print(f'Chunking {rel_path}...')
        st = os.stat(file_path)
        chunks = chunk_util.chunk_file(file_path, chunks_dir, avg_chunk_size_kb)

        # Time JSON marshaling uses RFC3339 format with 'Z' suffix for UTC.
        mod_time = (
            datetime.datetime.fromtimestamp(
                st.st_mtime, tz=datetime.timezone.utc
            )
            .isoformat()
            .replace('+00:00', 'Z')
        )

        index.append({
            'path': rel_path,
            'mod_time': mod_time,
            'mode': st.st_mode,
            'chunks': [c.to_dict() for c in chunks],
        })

    # 3. Save the index json file
    with open(
        os.path.join(chunked_dir, chunk_util.CHUNKS_INDEX_FILENAME), 'w'
    ) as f:
      json.dump(index, f, indent=2)

    # 4. Zip the chunked directory
    print(f'Zipping the chunked directory to {output_zip_path}...')
    zip_util.ZipUtil.zip_directory(chunked_dir, output_zip_path)


if __name__ == '__main__':
  main()
