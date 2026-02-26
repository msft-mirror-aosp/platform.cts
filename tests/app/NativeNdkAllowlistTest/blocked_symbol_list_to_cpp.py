#!/usr/bin/env python
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
# limitations under the License.
#

import argparse
import os


def to_cpp_string(content):
  if not content:
    return '""'
  return f'R"%({content})%"'


def main():
  parser = argparse.ArgumentParser(description='Generate C++ map from files')
  parser.add_argument('-o', '--output', required=True, help='Output C++ file')
  parser.add_argument('inputs', nargs='+', help='Input files')
  args = parser.parse_args()

  with open(args.output, 'w', encoding='utf-8') as out:
    out.write(r"""// Auto-generated file. Do not edit!
#include <cstddef>
#include "src/FileContent.h"

extern const FileContent kFileContents[] = {
""")

    for input_file in args.inputs:
      with open(input_file, 'r', encoding='utf-8') as f:
        lines = f.readlines()

      symbol_file_name = ""
      if lines and lines[0].startswith('# '):
        symbol_file_name = lines[0].strip()[2:]

      content = "".join(line for line in lines if not line.startswith('#'))

      cpp_content = to_cpp_string(content)
      cpp_symbol_file_name = to_cpp_string(symbol_file_name)

      out.write(f'    {{ {cpp_symbol_file_name}, {cpp_content} }},\n')

    out.write('    { nullptr, nullptr }\n')
    out.write('};\n')


if __name__ == '__main__':
  main()
