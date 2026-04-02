# Copyright 2022 The Android Open Source Project
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
"""Verifies that flash is fired when lighting conditions are dark."""


import logging
import math
import os.path
import pathlib

import cv2
from mobly import test_runner
import numpy as np

import its_base_test
import camera_properties_utils
import gen2_rig_controller_utils
import image_processing_utils
import its_session_utils
import lighting_control_utils
import opencv_processing_utils
import ui_interaction_utils

_BGR = 'BGR'
_JETPACK_CAMERA_APP_PACKAGE_NAME = 'com.google.jetpackcamera'
_MEAN_DELTA_ATOL = 15  # mean used for reflective charts
_PATCH_H = 0.25  # center 25%
_PATCH_W = 0.25
_PATCH_X = 0.5 - _PATCH_W/2
_PATCH_Y = 0.5 - _PATCH_H/2
_SAVE_IMAGE_DELAY = 10  # empirically determined
_TEST_NAME = os.path.splitext(os.path.basename(__file__))[0]
_ZOOM_1X = 1.0
_ZOOM_MAX = 1.5  # avoid physical camera switch to tele


class AutoFlashTest(its_base_test.UiAutomatorItsBaseTest):
  """Test that flash is fired when lighting conditions are dark using JCA."""

  def setup_class(self):
    super().setup_class()
    self.ui_app = _JETPACK_CAMERA_APP_PACKAGE_NAME
    # restart CtsVerifier to ensure that correct flags are set
    ui_interaction_utils.force_stop_app(
        self.dut, its_base_test.CTS_VERIFIER_PKG)
    self.dut.adb.shell(
        'am start -n com.android.cts.verifier/.CtsVerifierActivity')
    # establish connection with lighting controller
    self.use_gen2 = (self.lighting_cntl ==
                     gen2_rig_controller_utils.DEFAULT_GEN2_LIGHTS_NAME)
    self.lighting_control_port = lighting_control_utils.lighting_control(
        self.lighting_cntl, self.lighting_ch, self.use_gen2)

  def teardown_test(self):
    ui_interaction_utils.force_stop_app(self.dut, self.ui_app)
    if self.lighting_control_port:
      self.lighting_control_port.close()

  def test_auto_flash(self):
    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:
      props = cam.get_camera_properties()
      props = cam.override_with_hidden_physical_camera_props(props)
      test_name = os.path.join(self.log_path, _TEST_NAME)
      z_range = props['android.control.zoomRatioRange']
      z_min, z_max = float(z_range[0]), float(z_range[1])
      z_max = _ZOOM_MAX if z_max > _ZOOM_MAX else z_max
      zoom_ratios = (z_min, _ZOOM_1X, z_max)
      # close camera after props retrieved, so that ItsTestActivity can open it
      cam.close_camera()

      # check SKIP conditions
      first_api_level = its_session_utils.get_first_api_level(self.dut.serial)
      lens_facing = props['android.lens.facing']
      facing_front = (
          lens_facing == camera_properties_utils.LENS_FACING['FRONT'])
      should_run_front = (
          facing_front and
          first_api_level >= its_session_utils.ANDROID15_API_LEVEL
      )
      should_run_rear = (
          camera_properties_utils.flash(props) and
          first_api_level >= its_session_utils.ANDROID13_API_LEVEL
      )
      camera_properties_utils.skip_unless(should_run_front or should_run_rear)

      # turn OFF lights to darken scene
      lighting_control_utils.set_lighting_state(
          self.lighting_control_port, self.lighting_ch,
          lighting_control_utils.LIGHT_OFF, self.use_gen2)

      # take captures with no flash as baseline
      logging.debug('Taking captures with no flash.')
      no_flash_caps = list(cam.do_jca_captures_across_zoom_ratios(
          self.dut, self.log_path,
          ui_interaction_utils.FLASH_MODE_OFF_CONTENT_DESC,
          lens_facing, zoom_ratios=zoom_ratios
      ))

      # take captures with auto flash enabled
      logging.debug('Taking captures with auto flash enabled.')
      auto_flash_caps = list(cam.do_jca_captures_across_zoom_ratios(
          self.dut, self.log_path,
          ui_interaction_utils.FLASH_MODE_AUTO_CONTENT_DESC,
          lens_facing, zoom_ratios=zoom_ratios,
          save_image_delay=_SAVE_IMAGE_DELAY
      ))

      failed_zoom_ratios = {}
      marginal_pass_zoom_ratios = {}

      # Identify physical ID at 1x zoom for switch avoidance
      baseline_1x_physical_id = None
      for requested_zoom, cap in zip(zoom_ratios, no_flash_caps):
        if math.isclose(requested_zoom, _ZOOM_1X):
          baseline_1x_physical_id = cap.physical_id
          break

      for requested_zoom, cap_off, cap_auto in zip(
          zoom_ratios, no_flash_caps, auto_flash_caps):
        path_off = pathlib.Path(cap_off.capture_path)
        path_auto = pathlib.Path(cap_auto.capture_path)
        physical_id_off = cap_off.physical_id
        physical_id_auto = cap_auto.physical_id
        zoom_off = cap_off.zoom_ratio
        zoom_auto = cap_auto.zoom_ratio

        logging.debug(
            'Requested Zoom Ratio: %f, No Flash Zoom: %f, Auto Flash Zoom: %f, '
            'No Flash Physical ID: %s, Auto Flash Physical ID: %s',
            requested_zoom, zoom_off, zoom_auto,
            physical_id_off, physical_id_auto)

        # Skip zoom ratios that cause physical camera switch
        if (requested_zoom > _ZOOM_1X and
            physical_id_off != baseline_1x_physical_id):
          logging.debug(
              'Skip zoom ratio %f due to physical camera switch. '
              'Physical camera id at 1x zoom: %s, '
              'Physical camera id at %f zoom: %s',
              requested_zoom, baseline_1x_physical_id,
              requested_zoom, physical_id_off)
          continue

        # process no flash image
        no_flash_capture_path = path_off.with_name(
            f'{path_off.stem}_no_flash_{zoom_off}{path_off.suffix}'
        )
        os.rename(path_off, no_flash_capture_path)
        cv2_no_flash_image = cv2.imread(str(no_flash_capture_path))
        y = opencv_processing_utils.convert_to_y(cv2_no_flash_image, _BGR)
        # Add a color channel dimension for interoperability
        y = np.expand_dims(y, axis=2)
        patch = image_processing_utils.get_image_patch(
            y, _PATCH_X, _PATCH_Y, _PATCH_W, _PATCH_H
        )
        no_flash_mean = image_processing_utils.compute_image_means(patch)[0]
        image_processing_utils.write_image(y, f'{test_name}_no_flash_Y.jpg')
        logging.debug('No flash frames Y mean: %.4f', no_flash_mean)

        # process auto flash image
        auto_flash_capture_path = path_auto.with_name(
            f'{path_auto.stem}_auto_flash_{zoom_auto}{path_auto.suffix}'
        )
        os.rename(path_auto, auto_flash_capture_path)
        cv2_auto_flash_image = cv2.imread(str(auto_flash_capture_path))
        y = opencv_processing_utils.convert_to_y(cv2_auto_flash_image, _BGR)
        # Add a color channel dimension for interoperability
        y = np.expand_dims(y, axis=2)
        patch = image_processing_utils.get_image_patch(
            y, _PATCH_X, _PATCH_Y, _PATCH_W, _PATCH_H
        )
        flash_mean = image_processing_utils.compute_image_means(patch)[0]
        image_processing_utils.write_image(y, f'{test_name}_auto_flash_Y.jpg')
        logging.debug('Flash frames Y mean: %.4f', flash_mean)

        # confirm correct behavior
        mean_delta = flash_mean - no_flash_mean
        if mean_delta <= _MEAN_DELTA_ATOL:
          failed_zoom_ratios[str(requested_zoom)] = mean_delta
        elif mean_delta <= (
            _MEAN_DELTA_ATOL * its_session_utils.MARGINAL_PASS_FACTOR_FLASH):
          marginal_pass_zoom_ratios[str(requested_zoom)] = mean_delta

      if marginal_pass_zoom_ratios:
        marginal_pass_message = (
            f'{its_session_utils.MARGINAL_PASSING_MESSAGE}\n')
        for ratio, delta in marginal_pass_zoom_ratios.items():
          marginal_pass_message += (
              f'Ratio: {ratio} | Mean FLASH-OFF: {delta:.3f}\n')
        logging.warning(marginal_pass_message)

      if failed_zoom_ratios:
        error_message = f'ATOL: {_MEAN_DELTA_ATOL}:\n'
        for ratio, delta in failed_zoom_ratios.items():
          error_message += f'Ratio: {ratio} | Mean FLASH-OFF: {delta:.3f}\n'
        raise AssertionError(error_message)

      # turn lights back ON
      lighting_control_utils.set_lighting_state(
          self.lighting_control_port, self.lighting_ch,
          lighting_control_utils.LIGHT_ON, self.use_gen2)


if __name__ == '__main__':
  test_runner.main()
