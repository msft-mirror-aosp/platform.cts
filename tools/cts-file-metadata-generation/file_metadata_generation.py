#!/usr/bin/env python3
#
# Copyright (C) 2025 The Android Open Source Project
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

import sys
import argparse
import os
import xml.etree.ElementTree as etree
import subprocess

import file_metadata_pb2 as metadata_pb2
import google.protobuf.text_format as text_format

"""
Generated metadata for all files in the CTS testcases directory.

Usage:
  file_metadata_generation.py
  --testcases_dir [CTS testcases directory]
  --aapt2_tool [path of the aapt2 tool]
  --sdk_version [current sdk version]
  --output [output file]
"""

def _get_args():
    """Parses input arguments."""
    parser = argparse.ArgumentParser()
    parser.add_argument(
        '--testcases_dir', required=True,
        help='The directory of CTS testcases.')
    parser.add_argument(
        '--output', required=True,
        help='The output file of the metadata report.')
    parser.add_argument(
        '--aapt2_tool', required=True,
        help='The path of the aapt2 tool.'
    )
    parser.add_argument(
        '--sdk_version', required=True,
        help='The current sdk version.'
    )
    return parser.parse_args()


def _get_module_name(file_path: str, top_directory: str) -> str:
    """Gets the module of the given file."""
    relative_file_path = file_path.removeprefix(top_directory + '/')
    if '/' in relative_file_path:
        # Case1: The file is under a module directory. For example,
        # "android-cts/testcases/CtsAccessibilityTestCases/arm64/xxx.apk" belongs to the module
        # "CtsAccessibilityTestCases".
        return relative_file_path.split('/')[0]
    else:
        # Case2: The file doesn't belong to any module directory. For example,
        # "android-cts/testcases/CtsAccessibilityTestCases.apk" belongs to the module
        # CtsAccessibilityTestCases.
        return _get_base_file_name(relative_file_path)


def _get_base_file_name(file_name: str) -> str:
    """Gets the base name of the file, excluding the extension."""
    return os.path.splitext(file_name)[0]


def _get_test_suite_file_path(file_path: str, top_directory: str) -> str:
    """Gets the relative path of given the file under the test suite directory."""
    test_suite_directory = '/'.join(top_directory.split('/')[:-2])
    return file_path.removeprefix(test_suite_directory + '/')


def _handle_unspecified_file(metadata: metadata_pb2.FileMetadata) -> None:
    """Handles the file with an unspecified type."""
    metadata.file_type = metadata_pb2.FileMetadata.FileType.TYPE_UNSPECIFIED


def _handle_config_file(metadata: metadata_pb2.FileMetadata, file_path: str) -> None:
    """Extracts the information from a configuration file and adds to the FileMetadata proto."""
    metadata.file_type = metadata_pb2.FileMetadata.FileType.TYPE_CONFIG

    def _get_values(
            root_element: etree.Element,
            target_element: str,
            filter_rules: dict[str, str],
            target_attr: str
    ) -> list[str]:
        values = set()
        for e in root_element.findall(target_element):
            if all(e.get(key) == value for key, value in filter_rules.items()):
                values.add(e.get(target_attr))
        return list(values)

    with open(os.path.join(file_path), 'r') as file:
        root = etree.parse(file).getroot()

    component = _get_values(root, 'option', {'key': 'component'}, 'value')
    sim_card_token = _get_values(root, 'option', {'key': 'token'}, 'value')
    runners = _get_values(root, 'test', {}, 'class')
    parameters = _get_values(root, 'option', {'key': 'parameter'}, 'value')
    target_preparers = _get_values(root, 'target_preparer', {}, 'class')
    mainline_modules = set()
    for element in root.findall('object'):
        mainline_modules = mainline_modules.union(
            _get_values(
                element,
                'option',
                {'name': 'mainline-module-package-name'},
                'value',
            )
        )


    assert len(component) <= 1 and len(sim_card_token) <= 1 and runners

    metadata.config_summary.CopyFrom(metadata_pb2.ConfigFileSummary(
        component=component[0] if component else None,
        test_runner=runners,
        sim_card_token=sim_card_token[0] if sim_card_token else None,
        mainline_module_package_name=list(mainline_modules),
        parameter=parameters,
        target_preparer=target_preparers,
    ))


def _handle_apk_file(
        metadata: metadata_pb2.FileMetadata,
        file_path: str,
        aapt2_tool: str,
        default_sdk_version: int,
) -> None:
    """Extracts information from an APK file and adds to the FileMetadata proto.

    This function uses the `aapt2` tool to extract SDK version infos from the APK file.

    Args:
        metadata: The FileMetadata proto object.
        file_path: The path to the APK file.
        aapt2_tool: The path to the `aapt2` executable.
        default_sdk_version: The default SDK version to use.
    """
    target_sdk_version, min_sdk_version = None, None
    metadata.file_type = metadata_pb2.FileMetadata.FileType.TYPE_APK
    try:
        stdout = subprocess.run(
            [aapt2_tool, 'dump', 'badging', file_path],
            check=True,
            stdout=subprocess.PIPE,
        ).stdout.decode('utf-8').strip()
    except subprocess.CalledProcessError:
        metadata.apk_summary.CopyFrom(metadata_pb2.ApkFileSummary())
        return

    def _get_sdk_version(prefix: str, content: str) -> int | None:
        if content.startswith(prefix):
            raw_sdk_version = content.removeprefix(prefix).strip('\'')
            if raw_sdk_version.isdigit():
                return min(default_sdk_version, int(raw_sdk_version))
            return default_sdk_version
        return None

    for line in stdout.splitlines():
        sdk_version = _get_sdk_version('targetSdkVersion:', line)
        if sdk_version is not None:
            target_sdk_version = sdk_version
        sdk_version = _get_sdk_version('minSdkVersion:', line)
        if sdk_version is not None:
            min_sdk_version = sdk_version
    metadata.apk_summary.CopyFrom(
        metadata_pb2.ApkFileSummary(
            target_sdk_version=target_sdk_version,
            min_sdk_version=min_sdk_version,
        )
    )


def _get_default_file_metadata(
        file_name: str,
        root: str,
        top_directory: str,
        test_modules: set[str],
) -> metadata_pb2.FileMetadata:
    """Creates a FileMetadata proto object.

    Args:
        file_name: The name of the file.
        root: The root directory where the file is located.
        top_directory: The top-level directory being processed.
        test_modules: A set of known test module names.

    Returns:
        A FileMetadata proto object.
    """
    file_path = str(os.path.join(root, file_name))
    module_name = _get_module_name(file_path, top_directory)
    return metadata_pb2.FileMetadata(
        file_path=_get_test_suite_file_path(file_path, top_directory),
        file_name=_get_base_file_name(file_name),
        module_name=module_name,
        is_test_module=module_name in test_modules,
        file_size=os.path.getsize(file_path) / 1024,
    )


def _list_test_modules(top_directory: str) -> set[str]:
    """Lists all test modules in the given directory.

    This function walks through the directory and identifies configuration files (".config") to
    extract the test modules.

    Args:
        top_directory: The directory to scan for test modules.

    Returns:
        A set of test module names.
    """
    test_modules = set()
    for root, _, files in os.walk(top_directory):
        for file_name in files:
            if file_name.endswith('.config'):
                test_modules.add(_get_base_file_name(file_name))
    return test_modules


def _get_metadata(
        top_directory: str,
        aapt2_tool: str,
        default_sdk_version: int,
) -> metadata_pb2.CtsTestFilesMetadata:
    """Generates metadata for all files in the given directory.

    Args:
        top_directory: The directory to process.
        aapt2_tool: The path to the `aapt2` executable.
        default_sdk_version: The default SDK version for APK files.

    Returns:
        A CtsTestFilesMetadata proto object containing metadata for all files.
    """
    test_modules = _list_test_modules(top_directory)
    file_metadata = []
    for root, _, files in os.walk(top_directory):
        for file_name in files:
            file_path = str(os.path.join(root, file_name))
            metadata = _get_default_file_metadata(file_name, root, top_directory, test_modules)
            if file_name.endswith('.config'):
                _handle_config_file(metadata, file_path)
            elif file_name.endswith('.apk'):
                _handle_apk_file(metadata, file_path, aapt2_tool, default_sdk_version)
            else:
                _handle_unspecified_file(metadata)
            file_metadata.append(metadata)
    return metadata_pb2.CtsTestFilesMetadata(file_metadata=file_metadata)


def _generate_output(output_file: str, files_metadata: metadata_pb2.CtsTestFilesMetadata) -> None:
    """Writes the file metadata to the given output file."""
    with open(output_file, 'w') as f:
        f.write(text_format.MessageToString(files_metadata))


def main(argv):
    args = _get_args()

    directory = args.testcases_dir
    aapt2_tool = args.aapt2_tool
    sdk_version = int(args.sdk_version)
    output_file = args.output

    _generate_output(
        output_file,
        _get_metadata(directory, aapt2_tool, sdk_version)
    )


if __name__ == "__main__":
    main(sys.argv)