# Copyright 2023 The Android Open Source Project
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
"""Verify that the switch from UW to W has similar RGB values."""


import logging
import os.path
import pathlib

from mobly import test_runner
import numpy as np

import ip_chart_extraction_utils as ce
import ip_metrics_utils
import its_base_test
import camera_properties_utils
import image_processing_utils
import its_session_utils
import gen2_rig_controller_utils
import multi_camera_switch_utils
import preview_processing_utils


_AWB_DIFF_THRESHOLD = 4
_BRIGHTNESS_DIFF_THRESHOLD = 4
_DYNAMIC_PATCH_MID_TONE_START_IDX = 5
_DYNAMIC_PATCH_MID_TONE_END_IDX = 15
_LENS_SUFFIX_UW = 'uw'
_LENS_SUFFIX_W = 'w'
_MP4_FORMAT = '.mp4'
_NAME = os.path.splitext(os.path.basename(__file__))[0]
_ZOOM_RANGE_UW_W = (0.95, 2.05)  # UW/W crossover range
_ZOOM_STEP = 0.01


def _get_mean_brightness(patch_list):
  """Calculate mean brightness from patch list.

  Args:
    patch_list: list; of patches from grey scale.

  Returns:
    brightness value: float; mean brightness value.
  """
  brightness_values = []
  for patch in patch_list:
    mean_l, _, _ = ip_metrics_utils.get_lab_mean_values(patch)
    brightness_values.append(mean_l)
  return np.mean(
      brightness_values[
          _DYNAMIC_PATCH_MID_TONE_START_IDX:_DYNAMIC_PATCH_MID_TONE_END_IDX
      ])


def _do_brightness_check(patch_list_w, patch_list_uw):
  """Computes brightness diff between two sets of capture images.

  Args:
    patch_list_w: list; camera dynamic range patch cells.
    patch_list_uw: list; second set of camera dynamic range patch cells.

  Returns:
    marginal_brightness_pass: bool; True if passing marginally.
    marginal_pass_msg: string; String indicating marginal pass.
    e_msg: string; Error message if test fails.
  """
  brightness_1st = _get_mean_brightness(patch_list_w)
  brightness_2nd = _get_mean_brightness(patch_list_uw)
  # Below print statements are for logging purpose.
  # Do not replace with logging.
  print(f'{_NAME}_ae_w_y_avgs: ', brightness_1st)
  print(f'{_NAME}_ae_uw_y_avgs: ', brightness_2nd)

  # Check that the brightness difference is smaller than the max threshold
  brightness_diff = brightness_1st - brightness_2nd
  logging.debug('Brightness difference between w and uw: %.2f',
                brightness_diff)
  e_msg = ''
  marginal_pass_msg = ''
  marginal_brightness_pass = False
  if (abs(brightness_diff) > (
      _BRIGHTNESS_DIFF_THRESHOLD * its_session_utils.MARGINAL_PASS_FACTOR)
      and abs(brightness_diff) <= (_BRIGHTNESS_DIFF_THRESHOLD)):
    marginal_brightness_pass = True
    marginal_pass_msg += (
        f'Marginally passing brightness check. '
        f'Actual: {brightness_diff:.2f}, '
        f'Expected: {_BRIGHTNESS_DIFF_THRESHOLD:.1f}. ')
  if brightness_diff > _BRIGHTNESS_DIFF_THRESHOLD:
    e_msg += (
        f'The brightness difference between wide and ultra-wide cameras'
        f'for greyscale cells exceeds the threshold. '
        f'Actual: {brightness_diff:.2f}, '
        f'Expected: {_BRIGHTNESS_DIFF_THRESHOLD:.1f}'
    )
  return marginal_brightness_pass, marginal_pass_msg, e_msg


def _do_awb_check(mean_white_balance_diff, marginal_pass_msg, e_msg):
  """Computes AWB diff between two sets of capture images.

  Args:
    mean_white_balance_diff: float; AWB diff between two images.
    marginal_pass_msg: string; String indicating marginal pass.
    e_msg: string; Error message if test fails.

  Returns:
    marginal_awb_pass: bool; True if passing marginally.
    marginal_pass_msg: string; String indicating marginal pass.
    e_msg: string; Error message if test fails.
  """

  logging.debug('Mean white balance diff: %f', mean_white_balance_diff)
  print(f'{_NAME}_mean_white_balance_diff: {mean_white_balance_diff}')

  marginal_awb_pass = False
  if (abs(mean_white_balance_diff) > (
      _AWB_DIFF_THRESHOLD * its_session_utils.MARGINAL_PASS_FACTOR)
      and abs(mean_white_balance_diff) <= (_AWB_DIFF_THRESHOLD)):
    marginal_awb_pass = True
    marginal_pass_msg += (
        f'Marginally passing awb check. '
        f'Actual: {mean_white_balance_diff:.2f}, '
        f'Expected: {_AWB_DIFF_THRESHOLD:.1f}. '
        )

  if abs(mean_white_balance_diff) > _AWB_DIFF_THRESHOLD:
    e_msg += (
        f'The AWB difference between wide and ultra-wide cameras'
        f'for greyscale cells exceeds the threshold. '
        f'Actual: {mean_white_balance_diff:.2f}, '
        f'Expected: {_AWB_DIFF_THRESHOLD:.1f}'
    )
  return marginal_awb_pass, marginal_pass_msg, e_msg


class MultiCameraSwitchTest(its_base_test.ItsBaseTest):
  """Test that the switch between cameras has similar RGB values.

  This test uses various zoom ratios within range android.control.zoomRatioRange
  to capture images and find the point when the physical camera changes
  to determine the crossover point of change from UW to W.
  It does preview recording at UW and W crossover point to verify that
  the AE, AWB and AF behavior remains the same.
  """

  def test_multi_camera_switch(self):
    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:
      props = cam.get_camera_properties()
      props = cam.override_with_hidden_physical_camera_props(props)

      # check SKIP conditions
      first_api_level = its_session_utils.get_first_api_level(self.dut.serial)
      vendor_api_level = its_session_utils.get_vendor_api_level(self.dut.serial)
      camera_properties_utils.skip_unless(
          vendor_api_level >= its_session_utils.ANDROID16_API_LEVEL)
      multi_camera_switch_utils.check_lens_switch_conditions(
          props, first_api_level, _ZOOM_RANGE_UW_W)

      # Initialize rotation rig
      gen2_rig_controller_utils.setup_gen2_rig_with_cam(self, cam)

      # Set up scene and configure preview size
      preview_test_size = preview_processing_utils.get_max_preview_test_size(
          cam, self.camera_id)
      cam.do_3a()

      try:
        # Start dynamic preview recording and collect results
        capture_results, file_list = (
            preview_processing_utils.preview_over_zoom_range(
                self.dut, cam, preview_test_size, _ZOOM_RANGE_UW_W[0],
                _ZOOM_RANGE_UW_W[1], _ZOOM_STEP, self.log_path)
        )

        # Find the crossover point where the camera switches
        lens_changed, counter = (
            multi_camera_switch_utils.find_crossover_point(
                cam, capture_results))

      except (AssertionError, RuntimeError) as e:
        # Remove all the files except mp4 recording in case of any error
        for filename in os.listdir(self.log_path):
          file_path = os.path.join(self.log_path, filename)
          if os.path.isfile(file_path) and not filename.endswith(_MP4_FORMAT):
            os.remove(file_path)
        raise AssertionError('Error during crossover check') from e
      # Raise error if lens did not switch within the range
      # _ZOOM_RANGE_UW_W
      # TODO(ruchamk): Add lens_changed to the CameraITS metrics
      if not lens_changed:
        e_msg = 'Crossover point not found. Try running the test again!'
        raise AssertionError(e_msg)

      # Process capture results and get camera properties
      img_uw_file, img_w_file, _ = (
          multi_camera_switch_utils.get_camera_properties_and_log(
              cam, capture_results, file_list, counter,
              _LENS_SUFFIX_UW, _LENS_SUFFIX_W)
      )

      # Remove unwanted frames and only save the UW and
      # W crossover point frames along with mp4 recording
      its_session_utils.remove_frame_files(self.log_path, [
          os.path.join(self.log_path, img_uw_file),
          os.path.join(self.log_path, img_w_file)])

      # Add suffix to the UW and W image files
      uw_path = pathlib.Path(os.path.join(self.log_path, img_uw_file))
      uw_name = uw_path.with_name(f'{uw_path.stem}_uw{uw_path.suffix}')
      os.rename(os.path.join(self.log_path, img_uw_file), uw_name)

      w_path = pathlib.Path(os.path.join(self.log_path, img_w_file))
      w_name = w_path.with_name(f'{w_path.stem}_w{w_path.suffix}')
      os.rename(os.path.join(self.log_path, img_w_file), w_name)

      # Convert UW and W img to numpy array
      uw_img = image_processing_utils.convert_image_to_numpy_array(
          str(uw_name))
      w_img = image_processing_utils.convert_image_to_numpy_array(
          str(w_name))

      # Check the sensor orientation and flip image
      # TODO(leslieshaw): Check to see if flip is necessary
      if (props['android.lens.facing'] ==
          camera_properties_utils.LENS_FACING['FRONT']):
        img_name_stem = os.path.join(self.log_path, 'flipped_preview_uw')
        uw_img = image_processing_utils.check_orientation_and_flip(
            props, uw_img, img_name_stem
        )
        img_name_stem = os.path.join(self.log_path, 'flipped_preview_w')
        w_img = image_processing_utils.check_orientation_and_flip(
            props, w_img, img_name_stem
        )

      w_dynamic_range_patch_cells = (
          ce.get_cropped_dynamic_range_patch_cells(
              w_name, self.log_path, _LENS_SUFFIX_W)
      )
      uw_dynamic_range_patch_cells = (
          ce.get_cropped_dynamic_range_patch_cells(
              uw_name, self.log_path, _LENS_SUFFIX_UW)
      )

      # Get brightness diff between w and uw captures
      marginal_brightness_pass, marginal_pass_msg, e_msg = _do_brightness_check(
          w_dynamic_range_patch_cells, uw_dynamic_range_patch_cells)

      # Get white balance diff between default and jca captures
      mean_white_balance_diff = ip_metrics_utils.do_white_balance_check(
          w_dynamic_range_patch_cells, uw_dynamic_range_patch_cells,
          _LENS_SUFFIX_W, _LENS_SUFFIX_UW
      )
      marginal_awb_pass, marginal_pass_msg, e_msg = _do_awb_check(
          mean_white_balance_diff, marginal_pass_msg, e_msg
      )

      # Raise error message if any
      if e_msg:
        raise AssertionError(e_msg)
      elif (marginal_brightness_pass or marginal_awb_pass):
        logging.warning('%s\n %s', its_session_utils.MARGINAL_PASSING_MESSAGE,
                        marginal_pass_msg)

if __name__ == '__main__':
  test_runner.main()
