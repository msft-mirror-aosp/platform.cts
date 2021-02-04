# Copyright 2020 The Android Open Source Project
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
import time


from mobly import asserts
from mobly import base_test
from mobly import utils
from mobly.controllers import android_device





import its_session_utils

DISPLAY_TIMEOUT = 1800000  # ms
CTS_VERIFIER_PKG = 'com.android.cts.verifier'
MBS_PKG_TXT = 'mbs'
MBS_PKG = 'com.google.android.mobly.snippet.bundled'
WAIT_TIME_SEC = 5
SCROLLER_TIMEOUT_MS = 3000
VALID_NUM_DEVICES = (1, 2)
NOT_YET_MANDATED_ALL = 100

# Not yet mandated tests ['test', first_api_level mandatory]
# ie. ['test_test_patterns', 30] is MANDATED for first_api_level >= 30
NOT_YET_MANDATED = {
    'scene0': [['test_solid_color_test_pattern', 31],
               ['test_test_patterns', 30],
               ['test_tonemap_curve', 30]],
    'scene1_1': [['test_ae_precapture_trigger', 28],
                 ['test_channel_saturation', 29]],
    'scene1_2': [],
    'scene2_a': [['test_jpeg_quality', 30]],
    'scene2_b': [['test_auto_per_frame_control', NOT_YET_MANDATED_ALL]],
    'scene2_c': [],
    'scene2_d': [['test_num_faces', 30]],
    'scene2_e': [['test_num_faces', 30], ['test_continuous_picture', 30]],
    'scene3': [],
    'scene4': [],
    'scene5': [],
    'scene6': [['test_zoom', 30]],
    'sensor_fusion': [],
    'scene_change': [['test_scene_change', 31]]
}


class ItsBaseTest(base_test.BaseTestClass):
  """Base test for CameraITS tests.

  Tests inherit from this class execute in the Camera ITS automation systems.
  These systems consist of either:
    1. a device under test (dut) and an external rotation controller
    2. a device under test (dut) and one screen device(tablet)

  Attributes:
    dut: android_device.AndroidDevice, the device under test.
    tablet: android_device.AndroidDevice, the tablet device used to display
        scenes.
  """

  def setup_class(self):
    devices = self.register_controller(android_device, min_number=1)
    self.dut = devices[0]
    self.dut.load_snippet(MBS_PKG_TXT, MBS_PKG)
    self.camera = self.user_params['camera']
    if self.user_params.get('chart_distance'):
      self.chart_distance = float(self.user_params['chart_distance'])
      logging.debug('Chart distance: %s cm', self.chart_distance)
    if self.user_params.get('debug_mode'):
      self.debug_mode = True if self.user_params[
          'debug_mode'] == 'True' else False
    if self.user_params.get('scene'):
      self.scene = self.user_params['scene']
    camera_id_combo = self.parse_hidden_camera_id()
    self.camera_id = camera_id_combo[0]
    if len(camera_id_combo) == 2:
      self.hidden_physical_id = camera_id_combo[1]
    else:
      self.hidden_physical_id = None
    logging.debug('Camera_id:%s', self.camera_id)
    logging.debug('Hidden Physical Camera_id: %s', self.hidden_physical_id)

    num_devices = len(devices)
    if num_devices == 2:  # scenes [0,1,2,3,4,5,6]
      try:
        self.tablet = devices[1]
        self.tablet.load_snippet(MBS_PKG_TXT, MBS_PKG)
        self.tablet_screen_brightness = self.user_params['brightness']
        self.chart_loc_arg = self.user_params['chart_loc_arg']
      except KeyError:
        logging.debug('Not all tablet arguments set.')
    else:  # sensor_fusion or manual run
      try:
        self.fps = int(self.user_params['fps'])
        img_size = self.user_params['img_size'].split(',')
        self.img_w = int(img_size[0])
        self.img_h = int(img_size[1])
        self.test_length = float(self.user_params['test_length'])
        self.rotator_cntl = self.user_params['rotator_cntl']
        self.rotator_ch = self.user_params['rotator_ch']
      except KeyError:
        logging.debug('Not all arguments set. Manual run.')

    self._setup_devices(num_devices)

  def _setup_devices(self, num):
    """Sets up each device in parallel if more than one device."""
    if num not in VALID_NUM_DEVICES:
      raise AssertionError(
          f'Incorrect number of devices! Must be in {str(VALID_NUM_DEVICES)}')
    if num == 1:
      self.setup_dut(self.dut)
    else:
      logic = lambda d: self.setup_dut(d) if d else self.setup_tablet()
      utils.concurrent_exec(
          logic, [(self.dut,), (None,)],
          max_workers=2,
          raise_on_exception=True)

  def setup_dut(self, device):
    self.dut.adb.shell(
        'am start -n com.android.cts.verifier/.CtsVerifierActivity')
    # Wait for the app screen to appear.
    time.sleep(WAIT_TIME_SEC)

  def setup_tablet(self):
    self.tablet.adb.shell(['input', 'keyevent ', 'KEYCODE_WAKEUP'])
    self.tablet.adb.shell('settings put system screen_brightness {}'.format(
        self.tablet_screen_brightness))
    logging.debug('Tablet brightness set to: %d', self.tablet_screen_brightness)
    self.tablet.adb.shell('settings put system screen_off_timeout {}'.format(
        DISPLAY_TIMEOUT))
    self.tablet.adb.shell('am force-stop com.google.android.apps.docs')

  def parse_hidden_camera_id(self):
    """Parse the string of camera ID into an array.

    Returns:
      Array with camera id and hidden_physical camera id.
    """
    camera_id_combo = self.camera.split(':')
    return camera_id_combo


  def determine_not_yet_mandated_tests(self, device_id, scene):
    """Determine not_yet_mandated tests from NOT_YET_MANDATED list & phone info.

    Args:
     device_id: string of device id number.
     scene: scene to which tests belong to.

    Returns:
       dict of not yet mandated tests
    """
    # Initialize not_yet_mandated.
    not_yet_mandated = {}
    not_yet_mandated[scene] = []

    # Determine first API level for device.
    first_api_level = its_session_utils.get_first_api_level(device_id)

    # Determine which test are not yet mandated for first api level.
    tests = NOT_YET_MANDATED[scene]
    for test in tests:
      if test[1] >= first_api_level:
        not_yet_mandated[scene].append(test[0])
    return not_yet_mandated

  def on_pass(self, record):
    logging.debug('%s on PASS.', record.test_name)

  def on_fail(self, record):
    logging.debug('%s on FAIL.', record.test_name)
    if self.user_params.get('scene'):
      not_yet_mandated_tests = self.determine_not_yet_mandated_tests(
          self.dut.serial, self.scene)
      if self.current_test_info.name in not_yet_mandated_tests[self.scene]:
        logging.debug('%s is not yet mandated.', self.current_test_info.name)
        asserts.fail('Not yet mandated test', extras='Not yet mandated test')


