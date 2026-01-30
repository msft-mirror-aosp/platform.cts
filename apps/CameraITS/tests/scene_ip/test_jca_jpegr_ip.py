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
"""Ensure that the JPEG_R JCA preview snapshot and captured images are consistent."""

import logging
import os
import pathlib
import threading
import time

import camera_properties_utils
import gen2_rig_controller_utils
import image_processing_utils
import ip_chart_extraction_utils as ce
import ip_metrics_utils
import its_base_test
import its_device_utils
import its_session_utils
from mobly import test_runner
from mobly.controllers.android_device_lib import adb
import sensor_fusion_utils
from snippet_uiautomator import uiautomator
import ui_interaction_utils


_JETPACK_CAMERA_APP_PACKAGE_NAME = 'com.google.jetpackcamera'
_NAME = os.path.splitext(os.path.basename(__file__))[0]
_SCREEN_RECORDING_PATH = f'/sdcard/{_NAME}_screen_recording.mp4'
_AWB_DIFF_THRESHOLD = 4  # Determined empirically
_CLOSE_CAMERA_WAIT_TIME_SECONDS = 5
# Known Issue b/398591036 - [Instagram] Bad color optimization in the
# Instagram camera
# its_session_utils.get_backported_issue_is_fixed will check if the issue is
# listed as fixed applied fix with the following file:
# platform/build/backported_fixes/applied_fixes/ki398591036.txtpb
_KNOWN_ISSUE_398591036 = 398591036


class JcaJpegRImageParityClassTest(its_base_test.ItsBaseTest):
  """Test for JCA preview and captured image parity."""

  # TODO(ruchamk): Move the setup_gen2rig method in a util class to reduce code
  # duplication.
  def _setup_gen2rig(self):
    logging.debug('Setting up gen2 rig')
    # Configure and setup gen2 rig
    if self.rotator_cntl == 'None':
      logging.debug('Gen2 rig motor is not available.')
      self.motor_port = None
      self.motor_channel = None
    else:
      self.motor_channel = int(self.rotator_ch)
      self.motor_port = gen2_rig_controller_utils.find_serial_port(
          self.rotator_cntl)
      if self.motor_port:
        gen2_rig_controller_utils.configure_rotator(
            self.motor_port, self.motor_channel)
        gen2_rig_controller_utils.rotate(self.motor_port, self.motor_channel)
        logging.debug('Successfully configured and rotated the gen2 rig.')

    if self.lighting_cntl == 'None':
      logging.debug('Gen2 rig lights is not available.')
      self.lighting_port = None
      self.lighting_channel = None
    else:
      lights_channel = int(self.lighting_ch)
      lights_port = gen2_rig_controller_utils.find_serial_port(self.lighting_cntl)
      if lights_port:
        sensor_fusion_utils.establish_serial_comm(lights_port)
        gen2_rig_controller_utils.set_lighting_state(
            lights_port, lights_channel, 'ON')
        logging.debug('Successfully configured and turned ON the gen2 rig lights.')

  def setup_class(self):
    super().setup_class()
    self.dut.services.register(
        uiautomator.ANDROID_SERVICE_NAME, uiautomator.UiAutomatorService
    )
    gen2_rig_controller_utils.get_usb_devices_connected()
    self.dut.adb.shell(['input', 'keyevent', 'KEYCODE_WAKEUP'])
    time.sleep(its_base_test.TABLET_CMD_DELAY_SEC)
    self.dut.adb.shell('svc power stayon usb')
    # start screen recording
    def start_screen_recording():
      self.dut.adb.shell(f'screenrecord {_SCREEN_RECORDING_PATH}')
    self.thread = threading.Thread(target=start_screen_recording)
    self.thread.start()

  def teardown_test(self):
    ui_interaction_utils.force_stop_app(
        self.dut, _JETPACK_CAMERA_APP_PACKAGE_NAME
    )
    try:
      self.dut.adb.shell(['pkill', '-SIGINT', 'screenrecord'])
    except adb.AdbError as e:
      logging.debug('Could not kill screenrecord process: %s', e)
    if self.thread:
      self.thread.join()

    self.dut.adb.pull([_SCREEN_RECORDING_PATH, self.log_path])

    if self.rotator_cntl == 'gen2_rotator':
      # Release the serial ports properly after the test
      motor_port = gen2_rig_controller_utils.find_serial_port(self.rotator_cntl)
      if motor_port:
        motor_port.close()
    if self.lighting_cntl == 'gen2_lights':
      # Lights will go back to default state after the test
      lights_port = gen2_rig_controller_utils.find_serial_port(
          self.lighting_cntl
      )
      if lights_port:
        lights_port.close()

  def on_fail(self, record):
    super().on_fail(record)
    self.dut.take_screenshot(self.log_path, prefix='on_test_fail')

  def test_jca_jpegr_ip(self):
    """Check JCA preview and captured images are consistent."""

    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:
      props = cam.get_camera_properties()
      props = cam.override_with_hidden_physical_camera_props(props)
      if (props['android.lens.facing']
          == camera_properties_utils.LENS_FACING['FRONT']):
        camera_facing = 'front'
      else:
        camera_facing = 'rear'
      logging.debug('Camera facing: %s', camera_facing)

      first_api_level = its_session_utils.get_first_api_level(self.dut.serial)
      is_dut_tablet_or_desktop = its_device_utils.is_dut_tablet_or_desktop(
          self.dut.serial)

      # close camera after props have been retrieved
      cam.close_camera()
      device_id = self.dut.serial

      # Set up gen2 rig
      self._setup_gen2rig()

      camera_ids = cam.get_camera_ids()
      logging.debug('Camera ids on device: %s', camera_ids)

      # Launch ItsTestActivity
      its_device_utils.start_its_test_activity(device_id)
      if self.dut.ui(text='OK').wait.exists(
          timeout=ui_interaction_utils.WAIT_INTERVAL_FIVE_SECONDS
      ):
        self.dut.ui(text='OK').click.wait()

      # Check if HDR is supported on JCA or not
      supports_hdr = ui_interaction_utils.jca_hdr_supported(
          self.dut, self.log_path, props['android.lens.facing'])
      # Check if JPEG_R is supported by the device
      supports_jpeg_r = camera_properties_utils.jpeg_r(props)

      # Skip the test if camera is not primary or if it is a tablet and
      # if HDR is not supported on JCA
      is_primary_camera = self.hidden_physical_id is None
      camera_properties_utils.skip_unless(
          not is_dut_tablet_or_desktop and
          is_primary_camera and
          (
            first_api_level >= its_session_utils.ANDROID17_API_LEVEL or
            cam.is_backported_issue_resolved(_KNOWN_ISSUE_398591036)
          ) and
          supports_hdr and
          supports_jpeg_r
      )

      # Ensure that the device is orthogonal and then close camera
      if self.rotator_cntl != 'None' and self.lighting_cntl != 'None':
        gen2_rig_controller_utils.rotate_to_orthogonal_position(
            cam, self.log_path, self.motor_port, self.motor_channel)
      cam.close_camera()
      ui_interaction_utils.force_stop_app(
          self.dut, its_device_utils.CTS_VERIFIER_PKG
      )
      time.sleep(_CLOSE_CAMERA_WAIT_TIME_SECONDS)

      # Take HDR capture and preview snapshot on JCA
      jca_capture_path, jca_preview_path = (
          ui_interaction_utils.launch_jca_and_capture(
              self.dut,
              self.log_path,
              camera_facing=props['android.lens.facing'],
              enable_hdr=True,
              take_preview_snap=True
          )
      )
      ui_interaction_utils.pull_img_files(
          device_id, jca_capture_path, self.log_path
      )
      img_name = pathlib.Path(jca_capture_path).name
      jca_path = os.path.join(self.log_path, img_name)
      logging.debug('JCA capture img name: %s', img_name)
      jca_capture_path = pathlib.Path(jca_path)
      jca_capture_path = jca_capture_path.with_name(
          f'{jca_capture_path.stem}_jca{jca_capture_path.suffix}'
      )
      os.rename(jca_path, jca_capture_path)

      jca_preview_path = pathlib.Path(jca_preview_path)
      preview_path = os.path.join(self.log_path, jca_preview_path)
      jca_preview_path = pathlib.Path(preview_path)
      jca_preview_path = jca_preview_path.with_name(
          f'{jca_preview_path.stem}_jca{jca_preview_path.suffix}'
      )
      os.rename(preview_path, jca_preview_path)

      # Decompress UltraHDR image to RGB image
      jca_capture_rgb_path = jca_capture_path.with_name(
          f'{jca_capture_path.stem}_rgb{jca_capture_path.suffix}'
      )
      image_processing_utils.decompress_ultrahdr_image(
          jca_capture_path, jca_capture_rgb_path
      )

      # Compare the JCA capture and preview images
      # Get cropped dynamic range patch cells
      jca_capture_dynamic_range_patch_cells = (
          ce.get_cropped_dynamic_range_patch_cells(
              jca_capture_rgb_path, self.log_path, 'jca_capture')
      )
      jca_preview_dynamic_range_patch_cells = ce.get_cropped_dynamic_range_patch_cells(
          jca_preview_path, self.log_path, 'jca_preview'
      )

      # Get white balance diff between JCA capture and preview
      mean_white_balance_diff = ip_metrics_utils.do_white_balance_check(
          jca_capture_dynamic_range_patch_cells, jca_preview_dynamic_range_patch_cells,
          'capture', 'preview'
      )
      marginal_pass_msg = []
      e_msg = []
      marginal_while_balance_pass = False
      if (abs(mean_white_balance_diff) > (
          _AWB_DIFF_THRESHOLD * its_session_utils.MARGINAL_PASS_FACTOR)
          and
          abs(mean_white_balance_diff) <= (_AWB_DIFF_THRESHOLD)):
        marginal_while_balance_pass = True
        marginal_pass_msg.append('Marginally passing white balance diff check.')

      # Restart ItsTestActivity to use ItsSession known issue check.
      its_device_utils.start_its_test_activity(device_id)
      cam.__init__(
          device_id=self.dut.serial, camera_id=self.camera_id,
          hidden_physical_id=self.hidden_physical_id
      )

      # Logging for data collection
      result_str = f'{_NAME}_mean_white_balance_diff: {mean_white_balance_diff}'
      print(f'{_NAME}_{result_str}')
      logging.debug('%s', result_str)
      if abs(mean_white_balance_diff) > _AWB_DIFF_THRESHOLD:
        e_msg.append('Device fails the white balance difference criteria.')
      if e_msg:
        if (
            first_api_level < its_session_utils.ANDROID17_API_LEVEL
            and not cam.is_backported_issue_resolved(_KNOWN_ISSUE_398591036)
        ):
          raise AssertionError(
              f'{its_session_utils.NOT_YET_MANDATED_MESSAGE}\n'
              f'https://issuetracker.google.com/issues/{_KNOWN_ISSUE_398591036} is'
              f' not marked fixed on this device.\n\n{e_msg}'
          )
      else:  # Check for marginal pass
        if marginal_while_balance_pass:
          logging.warning('its_session_utils.MARGINAL_PASSING_MESSAGE\n %s',
                          marginal_pass_msg)


if __name__ == '__main__':
  test_runner.main()
