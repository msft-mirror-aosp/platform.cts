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

import logging
import os

from mobly import base_test
from mobly.controllers import android_device
from mobly.controllers.android_device_lib import adb

_AUTO_BRIGHTNESS_OFF = 0
_MAX_BRIGHTNESS = 255
_SCREEN_OFF_TIMEOUT_MS = 3600000  # 1 hour
_STAY_ON_WHILE_PLUGGED_IN = 3
_WIDEVINE_WEBSITE = 'widevine.com'


class SecurePlaybackBaseTest(base_test.BaseTestClass):
  """Base test class for secure playback tests."""

  def setup_class(self):
    devices = self.register_controller(android_device, min_number=1)
    self.dut = devices[0]
    self.dut.adb.shell('input keyevent KEYCODE_WAKEUP')
    self.dut.adb.shell('input keyevent KEYCODE_MENU')
    self.dut.adb.shell('am force-stop com.android.cts.verifier')
    try:
      self.dut.adb.shell(
          f'settings put system screen_brightness {_MAX_BRIGHTNESS}'
      )
      self.dut.adb.shell(
          'settings put system screen_auto_brightness_adj '
          f'{_AUTO_BRIGHTNESS_OFF}'
      )
    except adb.AdbError:
      logging.info(
          'Failed to set screen brightness. '
          'Please ensure that it is set manually.'
      )
    # Force device to stay awake
    self.dut.adb.shell(
        'settings put global stay_on_while_plugged_in '
        f'{_STAY_ON_WHILE_PLUGGED_IN}'
    )
    # Force device to keep screen on
    self.dut.adb.shell(
        f'settings put system screen_off_timeout {_SCREEN_OFF_TIMEOUT_MS}'
    )
    # Hide the navigation bar
    self.dut.adb.shell(
        'settings put global policy_control immersive.navigation=*')
    if not os.environ.get('SECURE_PLAYBACK_TEST_APP_TOP'):
      raise ValueError(
          'SECURE_PLAYBACK_TEST_APP_TOP environment variable not set. '
          'Please run source build/envsetup.sh'
      )
    try:
      self.video_scaling = float(self.user_params.get('video_scaling', 1.0))
    except ValueError:
      logging.error('Failed to parse video_scaling from user params')
      self.video_scaling = 1.0

    try:
      self.dut.adb.shell(f'ping -c1 {_WIDEVINE_WEBSITE}')
    except adb.AdbError:
      raise ValueError(
          'Cannot connect to Widevine. Please make sure that the device is '
          'connected to a Wi-Fi network that can reach the internet.'
      )

  def teardown_class(self):
    self.dut.adb.shell('am force-stop com.android.cts.verifier')
    # Un-hide the navigation bar
    self.dut.adb.shell('settings put global policy_control null*')

