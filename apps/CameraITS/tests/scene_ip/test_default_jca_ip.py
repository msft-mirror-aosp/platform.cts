# Copyright 2024 The Android Open Source Project
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
"""Ensure the captures from the default camera app and JCA are consistent."""

import logging
import os
import pathlib
import types

import camera_properties_utils
import ip_chart_extraction_utils as ce
import ip_chart_pattern_detector as pd
import ip_metrics_utils
import its_base_test
import its_device_utils
import its_session_utils
from mobly import test_runner
from snippet_uiautomator import uiautomator
import ui_interaction_utils


_CAMERA_HARDWARE_LEVEL_MAPPING = types.MappingProxyType({
    0: 'LIMITED',
    1: 'FULL',
    2: 'LEGACY',
    3: 'LEVEL_3',
    4: 'EXTERNAL',
})
_JETPACK_CAMERA_APP_PACKAGE_NAME = 'com.google.jetpackcamera'


class DefaultJcaImageParityClassTest(its_base_test.ItsBaseTest):
  """Test for default camera and JCA image parity."""

  def setup_class(self):
    super().setup_class()
    self.dut.services.register(
        uiautomator.ANDROID_SERVICE_NAME, uiautomator.UiAutomatorService
    )

  def teardown_test(self):
    ui_interaction_utils.force_stop_app(
        self.dut, _JETPACK_CAMERA_APP_PACKAGE_NAME
    )

  def on_fail(self, record):
    super().on_fail(record)
    self.dut.take_screenshot(self.log_path, prefix='on_test_fail')

  def test_default_jca_capture_ip(self):
    """Check default camera and JCA app image consistency."""

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
      camera_hardware_level = _CAMERA_HARDWARE_LEVEL_MAPPING[
          props.get('android.info.supportedHardwareLevel')
      ]
      logging.debug('Camera hardware level: %s', camera_hardware_level)
      first_api_level = its_session_utils.get_first_api_level(self.dut.serial)
      is_tablet = its_device_utils.is_dut_tablet(self.dut.serial)
      # Skip the test if camera is not primary or if it is a tablet
      is_primary_camera = self.hidden_physical_id is None
      camera_properties_utils.skip_unless(
          not is_tablet and
          is_primary_camera and
          first_api_level >= its_session_utils.ANDROID16_API_LEVEL
      )
      # close camera after props have been retrieved
      cam.close_camera()
      device_id = self.dut.serial

      # Get default camera app pkg name
      pkg_name = cam.get_default_camera_pkg()
      logging.debug('Default camera pkg name: %s', pkg_name)
      ui_interaction_utils.default_camera_app_dut_setup(device_id, pkg_name)

      # Launch ItsTestActivity
      its_device_utils.start_its_test_activity(device_id)
      if self.dut.ui(text='OK').wait.exists(
          timeout=ui_interaction_utils.WAIT_INTERVAL_FIVE_SECONDS
      ):
        self.dut.ui(text='OK').click.wait()

      # Take capture with default camera app
      device_img_path = ui_interaction_utils.launch_and_take_capture(
          dut=self.dut,
          pkg_name=pkg_name,
          camera_facing=camera_facing,
          log_path=self.log_path,
      )
      ui_interaction_utils.pull_img_files(
          device_id, device_img_path, self.log_path
      )
      default_img_name = pathlib.Path(device_img_path).name
      default_path = os.path.join(self.log_path, default_img_name)
      logging.debug('Default capture img name: %s', default_img_name)
      default_capture_path = pathlib.Path(default_path)
      default_capture_path = default_capture_path.with_name(
          f'{default_capture_path.stem}_default{default_capture_path.suffix}'
      )
      os.rename(default_path, default_capture_path)

      # Take JCA capture with UI
      jca_capture_path = ui_interaction_utils.launch_jca_and_capture(
          self.dut,
          self.log_path,
          camera_facing=props['android.lens.facing']
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

      # Extract FULL_CHART from the captured image.
      _, _ = (
          ce.get_feature_from_image(
              default_capture_path,
              'default_full_chart',
              self.log_path,
              pd.TestChartFeature.FULL_CHART,
          )
      )

      _, _ = ce.get_feature_from_image(
          jca_capture_path,
          'jca_full_chart',
          self.log_path,
          pd.TestChartFeature.FULL_CHART,
      )

      default_qr_code, _ = ce.get_feature_from_image(
          default_capture_path,
          'default_qr_code',
          self.log_path,
          pd.TestChartFeature.CENTER_QR_CODE,
      )

      jca_qr_code, _ = ce.get_feature_from_image(
          jca_capture_path,
          'jca_qr_code',
          self.log_path,
          pd.TestChartFeature.CENTER_QR_CODE,
      )

      logging.debug('Checking if FOV match between default and jca captures')
      size_match = ip_metrics_utils.check_if_qr_code_size_match(
          default_qr_code, jca_qr_code
      )
      logging.debug('Default and JCA size matches: %s', size_match)

      # Get cropped dynamic range patch cells
      default_dynamic_range_patch_cells = (
          ce.get_cropped_dynamic_range_patch_cells(
              default_capture_path, self.log_path, 'default')
      )
      jca_dynamic_range_patch_cells = ce.get_cropped_dynamic_range_patch_cells(
          jca_capture_path, self.log_path, 'jca'
      )

      # Get brightness diff between default and jca captures
      mean_brightness_diff = ip_metrics_utils.do_brightness_check(
          default_dynamic_range_patch_cells, jca_dynamic_range_patch_cells
      )
      logging.debug('mean_brightness_diff: %f', mean_brightness_diff)


if __name__ == '__main__':
  test_runner.main()
