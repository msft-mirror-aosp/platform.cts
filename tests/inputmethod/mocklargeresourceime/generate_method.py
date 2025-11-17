#!/usr/bin/python3
#
# Copyright 2025 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the 'License');
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an 'AS IS' BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# A simple python script to generate res/xml/method.xml.
#
# Usage:
#   $ python3 generate_method.py --num_subtypes 200 > res/xml/method.xml
#

import argparse
import datetime

def generate_input_method_xml(num_subtypes=200, str_value="@string/long_string"):
  """
  Generates XML content for an Android input method with a specified number of subtypes
  and prints it to standard output.

  Args:
    num_subtypes (int): The number of <subtype> elements to generate.
    str_value (str): The string value or resource to use for subtype attributes. Defaults to
        '@string/long_string'. It can be an immediate value (e.g. 'FooBar') or a resource
        reference (e.g. '@string/another_string').
  """
  current_year = datetime.datetime.now().year
  xml_content = f"""<?xml version="1.0" encoding="utf-8"?>
<!--
  Copyright {current_year} The Android Open Source Project

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
-->

<input-method xmlns:android="http://schemas.android.com/apk/res/android"
              android:settingsActivity="com.example.SettingsActivity"
              android:supportsSwitchingToNextInputMethod="true"
>
"""

  for i in range(1, num_subtypes + 1):
    subtype_block = f"""    <subtype android:label="subtype{i}"
        android:subtypeId="{i}"
        android:overridesImplicitlyEnabledSubtype="true"
        android:languageTag="{str_value}"
        android:imeSubtypeMode="subtypemode{i}"
        android:imeSubtypeExtraValue="{str_value}"
        android:physicalKeyboardHintLanguageTag="{str_value}"
        android:physicalKeyboardHintLayoutType="{str_value}"
        android:imeSubtypeLocale="{str_value}"> </subtype>
"""
    xml_content += subtype_block

  xml_content += "</input-method>\n"

  print(xml_content)

if __name__ == '__main__':
  parser = argparse.ArgumentParser(
      description="Generates an Android input method XML with a large number of subtypes.",
      epilog="Example: python3 generate_method.py --num_subtypes 200 > res/xml/method.xml")
  parser.add_argument('--num_subtypes', type=int, default=200,
                      help='The number of <subtype> elements to generate.')
  parser.add_argument('--str_value', type=str, default="@string/long_string",
                      help="The string value for subtype attributes (e.g., '@string/long_string').")
  args = parser.parse_args()
  generate_input_method_xml(args.num_subtypes, args.str_value)
