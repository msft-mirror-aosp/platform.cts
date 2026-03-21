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

"""Utils for file splitting and restoration."""

import hashlib
import os
import shutil
from typing import List

from fastcdc import fastcdc


CHUNKS_DIR_NAME = '_chunks'
CHUNKS_INDEX_FILENAME = '_chunks_index.json'


class ChunkInfo:
  """Contains metadata for a chunk."""

  def __init__(self, sha256: str, offset: int):
    self.sha256 = sha256
    self.offset = offset

  def to_dict(self):
    return {'sha256': self.sha256, 'offset': self.offset}

  @classmethod
  def from_dict(cls, data):
    return cls(data['sha256'], data['offset'])


def chunk_file(
    path: str, chunks_dir: str, avg_chunk_size_kb: int = 64
) -> List[ChunkInfo]:
  """Divides a file into chunks and saves them in chunksDir."""
  if os.path.getsize(path) == 0:
    return []

  if not os.path.exists(chunks_dir):
    os.makedirs(chunks_dir)

  chunk_list = []
  seen_chunks = set()

  avg_size = avg_chunk_size_kb * 1024
  min_size = avg_size // 4
  max_size = avg_size * 4

  with open(path, 'rb') as source:
    for chunk in fastcdc(
        source,
        min_size=min_size,
        avg_size=avg_size,
        max_size=max_size,
        fat=True,
        hf=hashlib.sha256,
    ):
      if chunk.hash not in seen_chunks:
        seen_chunks.add(chunk.hash)
        chunk_path = os.path.join(chunks_dir, chunk.hash)
        with open(chunk_path, 'wb') as f:
          f.write(chunk.data)

      chunk_list.append(ChunkInfo(chunk.hash, chunk.offset))

  return chunk_list


def restore_file(path: str, chunks_dir: str, chunks: List[ChunkInfo]):
  """Restores a file from its chunks."""
  output_dir = os.path.dirname(path)
  if output_dir and not os.path.exists(output_dir):
    os.makedirs(output_dir)

  with open(path, 'wb') as output_file:
    for chunk_info in chunks:
      chunk_path = os.path.join(chunks_dir, chunk_info.sha256)
      with open(chunk_path, 'rb') as f:
        shutil.copyfileobj(f, output_file)
