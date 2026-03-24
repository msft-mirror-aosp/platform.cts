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

"""Utility for zipping and unzipping directories and files."""

import datetime
import os
import stat
import zipfile


class ZipUtil:
  """A utility class for handles zip operations."""

  @staticmethod
  def zip_directory(source_dir, output_zip_path):
    """Zips the contents of a directory.

    Args:
        source_dir: The directory to zip.
        output_zip_path: The path to the resulting zip file.
    """
    with zipfile.ZipFile(output_zip_path, 'w', zipfile.ZIP_DEFLATED) as zipf:
      for root, _, files in os.walk(source_dir):
        for file in files:
          file_path = os.path.join(root, file)
          # The arcname is the path relative to the source_dir
          arcname = os.path.relpath(file_path, source_dir)
          zipf.write(file_path, arcname)

  @staticmethod
  def unzip_file(zip_path, extract_to):
    """Unzips a zip file to a specified directory.

    Args:
        zip_path: The path to the zip file.
        extract_to: The directory to extract contents into.
    """
    if not os.path.exists(extract_to):
      os.makedirs(extract_to)
    with zipfile.ZipFile(zip_path, 'r') as zipf:
      for info in zipf.infolist():
        target_path = os.path.join(extract_to, info.filename)
        is_symlink = stat.S_ISLNK(info.external_attr >> 16)
        if is_symlink:
          link_target = zipf.read(info.filename).decode('utf-8')
          dir_name = os.path.dirname(target_path)
          if not os.path.exists(dir_name) and dir_name:
            os.makedirs(dir_name)
          if os.path.lexists(target_path):
            os.remove(target_path)
          os.symlink(link_target, target_path)
        else:
          zipf.extract(info, extract_to)
          if info.external_attr >> 16 > 0:
            os.chmod(target_path, info.external_attr >> 16)
        date_time = datetime.datetime(*info.date_time)
        ts = date_time.timestamp()
        os.utime(target_path, (ts, ts), follow_symlinks=False)
