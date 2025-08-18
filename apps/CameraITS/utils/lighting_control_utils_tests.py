# Copyright 2025 The Android Open Source Project
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
"""Tests for lighting_control_utils."""


import unittest

import lighting_control_utils

_ASSERT_MSG = 'No lighting control: need to control lights manually.'
_LIGHTING_CONTROLLER_EMPTY = ''
_LIGHTING_CHANNEL_EMPTY = ''
_LOGGING_DEBUG_LEVEL = 'DEBUG'


class TestLightingControl(unittest.TestCase):
  """Unit tests for the lighting_control function."""

  # Not testing other mode as correct output is not possible to test without
  # physical hardware.
  def test_empty_lighting_control(self):
    """Tests lighting_control manual mode returns None and logs correctly."""
    lighting_cntl = _LIGHTING_CONTROLLER_EMPTY
    lighting_ch = _LIGHTING_CHANNEL_EMPTY

    with self.assertLogs(level=_LOGGING_DEBUG_LEVEL) as logs:
      returned_port = lighting_control_utils.lighting_control(
          lighting_cntl, lighting_ch)
    self.assertIsNone(returned_port)

if __name__ == '__main__':
  unittest.main()
