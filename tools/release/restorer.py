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

"""Restores android-xts.zip from chunked zip file.

Usage:
  restorer.py
  --chunked_zip_path [path of android-xts-chunked.zip]
  --output_dir [path of output directory]
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
      '--chunked_zip_path', required=True, help='Path to a chunked zip file'
  )
  parser.add_argument(
      '--output_dir',
      help=(
          'Path to the output directory. If unset, it will be derived from'
          ' [chunked_zip_path].'
      ),
  )
  return parser.parse_args()


def main():
  args = _get_args()
  chunked_zip_path = args.chunked_zip_path
  restored_dir = args.output_dir
  if not restored_dir:
    if chunked_zip_path.endswith('-chunked.zip'):
      restored_dir = chunked_zip_path[: -len('-chunked.zip')]
    else:
      restored_dir = os.path.splitext(chunked_zip_path)[0] + '_restored'

  with tempfile.TemporaryDirectory() as temp_dir:
    # 1. Unzip android-cts-chunked.zip
    print(f'Unzipping {chunked_zip_path}...')
    chunked_extract_dir = os.path.join(temp_dir, 'chunked')
    zip_util.ZipUtil.unzip_file(chunked_zip_path, chunked_extract_dir)

    # 2. Read the index json file
    index_path = os.path.join(
        chunked_extract_dir, chunk_util.CHUNKS_INDEX_FILENAME
    )
    with open(index_path, 'r') as f:
      index = json.load(f)

    # 3. Restore files
    os.makedirs(restored_dir, exist_ok=True)
    chunks_dir = os.path.join(chunked_extract_dir, chunk_util.CHUNKS_DIR_NAME)

    symlinks = []
    for entry in index:
      rel_path = entry['path']
      mod_time_str = entry.get('mod_time')
      mode = entry.get('mode')
      symlink_target = entry.get('symlink_target')
      chunks_data = entry['chunks']
      if symlink_target is not None:
        symlinks.append(entry)
        continue

      print(f'Restoring {rel_path}...')
      target_path = os.path.join(restored_dir, rel_path)
      chunks = [chunk_util.ChunkInfo.from_dict(c) for c in chunks_data]
      chunk_util.restore_file(target_path, chunks_dir, chunks)

      if mode is not None:
        os.chmod(target_path, mode)

      if mod_time_str:
        # datetime.fromisoformat() handles 'Z' suffix since Python 3.11.
        # For older versions, replace 'Z' with '+00:00'.
        dt = datetime.datetime.fromisoformat(
            mod_time_str.replace('Z', '+00:00')
        )
        mtime = dt.timestamp()
        os.utime(target_path, (mtime, mtime))

    for entry in symlinks:
      rel_path = entry['path']
      mod_time_str = entry.get('mod_time')
      symlink_target = entry.get('symlink_target')

      print(f'Restoring {rel_path}...')
      target_path = os.path.join(restored_dir, rel_path)
      # Use lexists to check if a broken link or incorrect file is in the way
      if os.path.lexists(target_path):
        os.remove(target_path)

      output_dir = os.path.dirname(target_path)
      if output_dir and not os.path.exists(output_dir):
        os.makedirs(output_dir)
      # Recreate the symlink
      os.symlink(symlink_target, target_path)

      if mod_time_str:
        # datetime.fromisoformat() handles 'Z' suffix since Python 3.11.
        # For older versions, replace 'Z' with '+00:00'.
        dt = datetime.datetime.fromisoformat(
            mod_time_str.replace('Z', '+00:00')
        )
        mtime = dt.timestamp()
        os.utime(target_path, (mtime, mtime))

if __name__ == '__main__':
  main()
