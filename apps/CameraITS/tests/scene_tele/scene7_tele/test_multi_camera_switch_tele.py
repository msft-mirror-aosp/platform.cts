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
"""Verify that the switch from wide to tele has similar RGB values."""


import logging
import math
import os.path
import pathlib

import cv2
from mobly import test_runner
import numpy as np

import its_base_test
import camera_properties_utils
import image_processing_utils
import its_session_utils
import multi_camera_switch_utils
import preview_processing_utils


_AE_ATOL = 15.0
_AE_RTOL = 0.15  # 15%
_AF_ATOL = 0.15
_AF_RTOL = 0.15  # 15%
_AWB_ATOL_AB = 10  # ATOL for A and B means in LAB color space
_AWB_ATOL_L = 3  # ATOL for L means in LAB color space
_COLORS = ('r', 'g', 'b', 'gray')
_COLOR_GRAY = _COLORS[3]
_LENS_SUFFIX_W = 'w'
_LENS_SUFFIX_TELE = 'tele'
_MP4_FORMAT = '.mp4'
_NAME = os.path.splitext(os.path.basename(__file__))[0]
_PATCH_MARGIN = 50  # Pixels
_ZOOM_RANGE_W_TELE = (1.8, 5.0)  # W/Tele crossover range
_ZOOM_STEP = 0.02


def _do_af_check(w_img, tele_img):
  """Checks the AF difference between the wide and tele img.

  Args:
    w_img: image captured using wide lens.
    tele_img: image captured using tele lens.

  Returns:
    failed_af_msg: failed AF check messages if any. None otherwise.
    sharpness_w: sharpness value for wide lens.
    sharpness_tele: sharpness value for tele lens.
  """
  failed_af_msg = []
  sharpness_w = image_processing_utils.compute_image_sharpness(w_img)
  logging.debug('Sharpness for wide patch: %.2f', sharpness_w)
  sharpness_tele = image_processing_utils.compute_image_sharpness(tele_img)
  logging.debug('Sharpness for tele patch: %.2f', sharpness_tele)

  if not math.isclose(
      sharpness_tele, sharpness_w, rel_tol=_AF_RTOL, abs_tol=_AF_ATOL):
    failed_af_msg.append('Sharpness delta is too high between wide and tele. '
                         f'Sharpness tele: {sharpness_tele:.4f} '
                         f'Sharpness wide: {sharpness_w:.4f}')
  return failed_af_msg, sharpness_w, sharpness_tele


class MultiCameraSwitchTeleTest(its_base_test.ItsBaseTest):
  """Test that the switch from wide to tele lens has similar RGB values.

  This test uses various zoom ratios within range android.control.zoomRatioRange
  to capture images and find the point when the physical camera changes
  to determine the crossover point of change from wide to tele lens.
  It does preview recording at wide to tele crossover point to verify that
  the AE, AWB and AF behavior remains the same.
  """

  def test_multi_camera_switch(self):
    # Handle subdirectory
    self.scene = 'scene7_tele'
    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:
      props = cam.get_camera_properties()
      props = cam.override_with_hidden_physical_camera_props(props)
      failed_awb_msg = []
      failed_ae_msg = []
      failed_af_msg = []

      # check SKIP conditions
      first_api_level = its_session_utils.get_first_api_level(self.dut.serial)
      multi_camera_switch_utils.check_lens_switch_conditions(
          props, first_api_level, _ZOOM_RANGE_W_TELE)

      # Set up scene and configure preview size
      its_session_utils.load_scene(
          cam, props, self.scene, self.tablet,
          # Ensure markers are large enough by loading unscaled chart
          its_session_utils.CHART_DISTANCE_NO_SCALING, log_path=self.log_path)
      preview_test_size = preview_processing_utils.get_max_preview_test_size(
          cam, self.camera_id)
      cam.do_3a()

      # Determine test zoom range
      zoom_range = props['android.control.zoomRatioRange']
      z_min, z_max = float(zoom_range[0]), float(zoom_range[1])
      z_min = max(z_min, _ZOOM_RANGE_W_TELE[0])  # Force W
      z_max = min(z_max, _ZOOM_RANGE_W_TELE[1])

      try:
        # Start dynamic preview recording and collect results
        capture_results, file_list = (
            preview_processing_utils.preview_over_zoom_range(
                self.dut, cam, preview_test_size, z_min,
                z_max, _ZOOM_STEP, self.log_path)
        )

        # Find the crossover point where the camera switches
        # TODO(b/388895676): refine search of crossover point
        converged_state_counter, lens_changed, converged_state, counter = (
            multi_camera_switch_utils.find_crossover_point(capture_results))

      except Exception as e:
        # Remove all the files except mp4 recording in case of any error
        # TODO(b/356881117): optimize frame extraction
        for filename in os.listdir(self.log_path):
          file_path = os.path.join(self.log_path, filename)
          if os.path.isfile(file_path) and not filename.endswith(_MP4_FORMAT):
            os.remove(file_path)
        raise AssertionError('Error during crossover check') from e

      # Raise error if lens did not switch within the range
      if not lens_changed:
        e_msg = 'Crossover point not found. Try running the test again!'
        raise AssertionError(e_msg)

      # Raise error if 3A does not converge after the lens change
      if not converged_state:
        e_msg = '3A not converged after the lens change.'
        raise AssertionError(e_msg)

      # Process capture results and get camera properties
      img_w_file, img_tele_file, min_focus_distance_tele = (
          multi_camera_switch_utils.get_camera_properties_and_log(
              cam, capture_results, file_list, counter,
              converged_state_counter, _LENS_SUFFIX_W, _LENS_SUFFIX_TELE)
      )

      # Remove unwanted frames and only save the wide and
      # tele crossover point frames along with mp4 recording
      its_session_utils.remove_frame_files(self.log_path, [
          os.path.join(self.log_path, img_w_file),
          os.path.join(self.log_path, img_tele_file)])

      # Add suffix to the wide and tele image files
      w_path = pathlib.Path(os.path.join(self.log_path, img_w_file))
      w_name = w_path.with_name(f'{w_path.stem}_w{w_path.suffix}')
      os.rename(os.path.join(self.log_path, img_w_file), w_name)

      tele_path = pathlib.Path(os.path.join(self.log_path, img_tele_file))
      tele_name = (
          tele_path.with_name(f'{tele_path.stem}_tele{tele_path.suffix}'))
      os.rename(os.path.join(self.log_path, img_tele_file), tele_name)

      # Convert wide and tele img to numpy array
      w_img = image_processing_utils.convert_image_to_numpy_array(
          str(w_name))
      tele_img = image_processing_utils.convert_image_to_numpy_array(
          str(tele_name))

      # Check the sensor orientation and flip image
      if (props['android.lens.facing'] ==
          camera_properties_utils.LENS_FACING['FRONT']):
        img_name_stem = os.path.join(self.log_path, 'flipped_preview_w')
        w_img = image_processing_utils.check_orientation_and_flip(
            props, w_img, img_name_stem
        )
        img_name_stem = os.path.join(self.log_path, 'flipped_preview_tele')
        tele_img = image_processing_utils.check_orientation_and_flip(
            props, tele_img, img_name_stem
        )

      # Find ArUco markers in the image with wide lens
      # and extract the outer box patch
      corners, ids = multi_camera_switch_utils.find_aruco_markers(
          w_img, w_path, 'w')
      w_chart_patch = multi_camera_switch_utils.extract_main_patch(
          corners, ids, w_img, w_path, 'w')
      w_four_patches = image_processing_utils.get_four_quadrant_patches(
          w_chart_patch, w_path, 'w', _PATCH_MARGIN)

      # Find ArUco markers in the image with tele lens
      # and extract the outer box patch
      corners, ids = multi_camera_switch_utils.find_aruco_markers(
          tele_img, tele_path, 'tele')
      tele_chart_patch = multi_camera_switch_utils.extract_main_patch(
          corners, ids, tele_img, tele_path, 'tele')
      tele_four_patches = image_processing_utils.get_four_quadrant_patches(
          tele_chart_patch, tele_path, 'tele', _PATCH_MARGIN)

      ae_w_y_avgs = {}
      ae_tele_y_avgs = {}

      for w_patch, tele_patch, patch_color in zip(
          w_four_patches, tele_four_patches, _COLORS):
        logging.debug('Checking for quadrant color: %s', patch_color)

        # AE Check: Extract the Y component from rectangle patch
        file_stem = f'{os.path.join(self.log_path, _NAME)}'
        ae_msg, w_y_avg, tele_y_avg = multi_camera_switch_utils.do_ae_check(
            w_patch, tele_patch, file_stem, patch_color, 'w', 'tele', _AE_RTOL,
            _AE_ATOL)
        if ae_msg:
          failed_ae_msg.append(f'{ae_msg}\n')
        ae_w_y_avgs.update({patch_color: f'{w_y_avg:.4f}'})
        ae_tele_y_avgs.update({patch_color: f'{tele_y_avg:.4f}'})

        # AWB Check : Verify that delta Cab are within the limits
        if camera_properties_utils.awb_regions(props):
          c_atol = _AWB_ATOL_L if patch_color == _COLOR_GRAY else _AWB_ATOL_AB
          awb_msg = image_processing_utils.do_awb_check(
              w_patch, tele_patch, c_atol, patch_color, 'W', 'tele')
          if awb_msg:
            failed_awb_msg.append(f'{awb_msg}\n')

      # Below print statements are for logging purpose.
      # Do not replace with logging.
      print(f'{_NAME}_ae_w_y_avgs: ', ae_w_y_avgs)
      print(f'{_NAME}_ae_tele_y_avgs: ', ae_tele_y_avgs)

      # Skip the AF check FF->FF
      if min_focus_distance_tele == 0:
        logging.debug('AF check skipped for this device.')
      else:
        # AF check using slanted edge
        w_slanted_edge_patch = image_processing_utils.get_slanted_edge_patch(
            w_chart_patch, w_path, 'w', _PATCH_MARGIN)
        tele_slanted_edge_patch = image_processing_utils.get_slanted_edge_patch(
            tele_chart_patch, tele_path, 'tele', _PATCH_MARGIN)
        failed_af_msg, sharpness_w, sharpness_tele = (
            multi_camera_switch_utils.do_af_check(
                w_slanted_edge_patch, tele_slanted_edge_patch, _LENS_SUFFIX_W,
                _LENS_SUFFIX_TELE))
        print(f'{_NAME}_w_sharpness: {sharpness_w:.4f}')
        print(f'{_NAME}_tele_sharpness: {sharpness_tele:.4f}')

      if failed_awb_msg or failed_ae_msg or failed_af_msg:
        error_msg = multi_camera_switch_utils.get_error_msg(
            failed_awb_msg, failed_ae_msg, failed_af_msg)
        raise AssertionError(f'{_NAME} failed with following errors:\n'
                             f'{error_msg}')

if __name__ == '__main__':
  test_runner.main()
