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
"""Verify that the switch from UW to W has similar RGB values."""


import logging
import os.path
import pathlib

from mobly import test_runner

import its_base_test
import camera_properties_utils
import image_processing_utils
import its_session_utils
import opencv_processing_utils
import preview_processing_utils


_AE_ATOL = 7.0
_AE_RTOL = 0.04  # 4%
_ARUCO_MARKERS_COUNT = 4
_AWB_ATOL_AB = 10  # ATOL for A and B means in LAB color space
_AWB_ATOL_L = 3  # ATOL for L means in LAB color space
_CH_FULL_SCALE = 255
_COLORS = ('r', 'g', 'b', 'gray')
_COLOR_GRAY = _COLORS[3]
_CONVERGED_STATE = 2
_IMG_FORMAT = 'png'
_LENS_SUFFIX_UW = 'uw'
_LENS_SUFFIX_W = 'w'
_MP4_FORMAT = '.mp4'
_NAME = os.path.splitext(os.path.basename(__file__))[0]
_PATCH_MARGIN = 50  # pixels
_RECORDING_DURATION = 400  # milliseconds
_SENSOR_ORIENTATIONS = (90, 270)
_SKIP_INITIAL_FRAMES = 15
_ZOOM_RANGE_UW_W = (0.95, 2.05)  # UW/W crossover range
_ZOOM_STEP = 0.01


def _do_af_check(uw_img, w_img):
  """Checks the AF behavior between the uw and w img.

  Args:
    uw_img: image captured using UW lens.
    w_img: image captured using W lens.

  Returns:
    failed_af_msg: Failed AF check messages if any. None otherwise.
    sharpness_uw: sharpness value for UW lens
    sharpness_w: sharpness value for W lens
  """
  failed_af_msg = []
  sharpness_uw = image_processing_utils.compute_image_sharpness(uw_img)
  logging.debug('Sharpness for UW patch: %.2f', sharpness_uw)
  sharpness_w = image_processing_utils.compute_image_sharpness(w_img)
  logging.debug('Sharpness for W patch: %.2f', sharpness_w)

  if sharpness_w < sharpness_uw:
    failed_af_msg.append('Sharpness should be higher for W lens.'
                         f'sharpness_w: {sharpness_w:.4f} '
                         f'sharpness_uw: {sharpness_uw:.4f}')
  return failed_af_msg, sharpness_uw, sharpness_w


def _extract_main_patch(corners, ids, img_rgb, img_path, lens_suffix):
  """Extracts the main rectangle patch from the captured frame.

  Find aruco markers in the captured image and detects if the
  expected number of aruco markers have been found or not.
  It then, extracts the main rectangle patch and saves it
  without the aruco markers in it.

  Args:
    corners: list of detected corners.
    ids: list of int ids for each ArUco markers in the input_img.
    img_rgb: An openCV image in RGB order.
    img_path: Path to save the image.
    lens_suffix: str; suffix used to save the image.
  Returns:
    rectangle_patch: numpy float image array of the rectangle patch.
  """
  rectangle_patch = opencv_processing_utils.get_patch_from_aruco_markers(
      img_rgb, corners, ids)
  patch_path = img_path.with_name(
      f'{img_path.stem}_{lens_suffix}_patch{img_path.suffix}')
  image_processing_utils.write_image(rectangle_patch/_CH_FULL_SCALE, patch_path)
  return rectangle_patch


def _find_aruco_markers(img, img_path, lens_suffix):
  """Detect ArUco markers in the input image.

  Args:
    img: input img with ArUco markers.
    img_path: path to save the image.
    lens_suffix: suffix used to save the image.
  Returns:
    corners: list of detected corners.
    ids: list of int ids for each ArUco markers in the input_img.
  """
  aruco_path = img_path.with_name(
      f'{img_path.stem}_{lens_suffix}_aruco{img_path.suffix}')
  corners, ids, _ = opencv_processing_utils.find_aruco_markers(
      img, aruco_path)
  if len(ids) != _ARUCO_MARKERS_COUNT:
    raise AssertionError(
        f'{_ARUCO_MARKERS_COUNT} ArUco markers should be detected.')
  return corners, ids


def _get_error_msg(failed_awb_msg, failed_ae_msg, failed_af_msg):
  """"Returns the error message string.

  Args:
    failed_awb_msg: list of awb error msgs
    failed_ae_msg: list of ae error msgs
    failed_af_msg: list of af error msgs
  Returns:
    error_msg: str; error_msg string
  """
  error_msg = ''
  if failed_awb_msg:
    error_msg = f'{error_msg}----AWB Check----\n'
    for msg in failed_awb_msg:
      error_msg = f'{error_msg}{msg}\n'
  if failed_ae_msg:
    error_msg = f'{error_msg}----AE Check----\n'
    for msg in failed_ae_msg:
      error_msg = f'{error_msg}{msg}\n'
  if failed_af_msg:
    error_msg = f'{error_msg}----AF Check----\n'
    for msg in failed_af_msg:
      error_msg = f'{error_msg}{msg}\n'
  return error_msg


class MultiCameraSwitchTest(its_base_test.ItsBaseTest):
  """Test that the switch from UW to W lens has similar RGB values.

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
      chart_distance = self.chart_distance
      failed_awb_msg = []
      failed_ae_msg = []
      failed_af_msg = []

      # check SKIP conditions
      first_api_level = its_session_utils.get_first_api_level(self.dut.serial)
      camera_properties_utils.skip_unless(
          first_api_level >= its_session_utils.ANDROID15_API_LEVEL and
          camera_properties_utils.zoom_ratio_range(props) and
          camera_properties_utils.logical_multi_camera(props) and
          camera_properties_utils.ae_regions(props))

      # Check the zoom range
      zoom_range = props['android.control.zoomRatioRange']
      logging.debug('zoomRatioRange: %s', zoom_range)
      camera_properties_utils.skip_unless(
          len(zoom_range) > 1 and
          (zoom_range[0] <= _ZOOM_RANGE_UW_W[0] <= zoom_range[1]) and
          (zoom_range[0] <= _ZOOM_RANGE_UW_W[1] <= zoom_range[1]))

      its_session_utils.load_scene(
          cam, props, self.scene, self.tablet, chart_distance)

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

        physical_id_before = None
        counter = 0  # counter for the index of crossover point result
        lens_changed = False
        converged_state_counter = 0
        converged_state = False

        for capture_result in capture_results:
          counter += 1
          ae_state = capture_result['android.control.aeState']
          awb_state = capture_result['android.control.awbState']
          af_state = capture_result['android.control.afState']
          physical_id = capture_result[
              'android.logicalMultiCamera.activePhysicalId']
          if not physical_id_before:
            physical_id_before = physical_id
          zoom_ratio = float(capture_result['android.control.zoomRatio'])
          if physical_id_before == physical_id:
            continue
          else:
            logging.debug('Active physical id changed')
            logging.debug('Crossover zoom ratio point: %f', zoom_ratio)
            physical_id_before = physical_id
            lens_changed = True
            if ae_state == awb_state == af_state == _CONVERGED_STATE:
              converged_state = True
              converged_state_counter = counter
              logging.debug('3A converged at the crossover point')
            break

        # If the frame at crossover point was not converged, then
        # traverse the list of capture results after crossover point
        # to find the converged frame which will be used for AE,
        # AWB and AF checks.
        if not converged_state:
          converged_state_counter = counter
          for capture_result in capture_results[converged_state_counter-1:]:
            converged_state_counter += 1
            ae_state = capture_result['android.control.aeState']
            awb_state = capture_result['android.control.awbState']
            af_state = capture_result['android.control.afState']
            if physical_id_before == capture_result[
                'android.logicalMultiCamera.activePhysicalId']:
              if ae_state == awb_state == af_state == _CONVERGED_STATE:
                logging.debug('3A converged after crossover point.')
                logging.debug('Zoom ratio at converged state after crossover'
                              'point: %f', zoom_ratio)
                converged_state = True
                break

      except Exception as e:
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

      # Raise error is 3A does not converge after the lens change
      if not converged_state:
        e_msg = '3A not converged after the lens change.'
        raise AssertionError(e_msg)

      img_uw_file = file_list[counter-2]
      capture_result_uw = capture_results[counter-2]
      uw_phy_id = (
          capture_result_uw['android.logicalMultiCamera.activePhysicalId']
      )
      physical_props_uw = cam.get_camera_properties_by_id(uw_phy_id)
      min_focus_distance_uw = (
          physical_props_uw['android.lens.info.minimumFocusDistance']
      )
      logging.debug('Min focus distance for UW phy_id: %s is %f',
                    uw_phy_id, min_focus_distance_uw)

      logging.debug('Capture results uw crossover: %s', capture_result_uw)
      logging.debug('Capture results w crossover: %s',
                    capture_results[counter-1])
      img_w_file = file_list[converged_state_counter-1]
      capture_result_w = capture_results[converged_state_counter-1]
      logging.debug('Capture results w crossover converged: %s',
                    capture_result_w)
      w_phy_id = capture_result_w['android.logicalMultiCamera.activePhysicalId']
      physical_props_w = cam.get_camera_properties_by_id(w_phy_id)
      min_focus_distance_w = (
          physical_props_w['android.lens.info.minimumFocusDistance']
      )
      logging.debug('Min focus distance for W phy_id: %s is %f',
                    w_phy_id, min_focus_distance_w)

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

      # Find ArUco markers in the image with UW lens
      # and extract the outer box patch
      corners, ids = _find_aruco_markers(
          uw_img, uw_path, _LENS_SUFFIX_UW)
      uw_chart_patch = _extract_main_patch(
          corners, ids, uw_img, uw_path, _LENS_SUFFIX_UW)
      uw_four_patches = image_processing_utils.get_four_quadrant_patches(
          uw_chart_patch, uw_path, _LENS_SUFFIX_UW, _PATCH_MARGIN)

      # Find ArUco markers in the image with W lens
      # and extract the outer box patch
      corners, ids = _find_aruco_markers(
          w_img, w_path, _LENS_SUFFIX_W)
      w_chart_patch = _extract_main_patch(
          corners, ids, w_img, w_path, _LENS_SUFFIX_W)
      w_four_patches = image_processing_utils.get_four_quadrant_patches(
          w_chart_patch, w_path, _LENS_SUFFIX_W, _PATCH_MARGIN)

      ae_uw_y_avgs = {}
      ae_w_y_avgs = {}

      for uw_patch, w_patch, patch_color in zip(
          uw_four_patches, w_four_patches, _COLORS):
        logging.debug('Checking for quadrant color: %s', patch_color)

        # AE Check: Extract the Y component from rectangle patch
        file_stem = f'{os.path.join(self.log_path, _NAME)}_{patch_color}'
        ae_msg, uw_y_avg, w_y_avg = image_processing_utils.do_ae_check(
            uw_patch, w_patch, file_stem, _LENS_SUFFIX_UW, _LENS_SUFFIX_W,
            _AE_RTOL, _AE_ATOL)
        if ae_msg:
          failed_ae_msg.append(f'{ae_msg}\n')
        ae_uw_y_avgs.update({patch_color: f'{uw_y_avg:.4f}'})
        ae_w_y_avgs.update({patch_color: f'{w_y_avg:.4f}'})

        # AWB Check : Verify that delta Cab are within the limits
        if camera_properties_utils.awb_regions(props):
          cab_atol = _AWB_ATOL_L if patch_color == _COLOR_GRAY else _AWB_ATOL_AB
          awb_msg = image_processing_utils.do_awb_check(
              uw_patch, w_patch, cab_atol, patch_color, _LENS_SUFFIX_UW,
              _LENS_SUFFIX_W)
          if awb_msg:
            failed_awb_msg.append(f'{awb_msg}\n')

      # Below print statements are for logging purpose.
      # Do not replace with logging.
      print(f'{_NAME}_ae_uw_y_avgs: ', ae_uw_y_avgs)
      print(f'{_NAME}_ae_w_y_avgs: ', ae_w_y_avgs)

      # Skip the AF check FF->FF
      if min_focus_distance_w == 0:
        logging.debug('AF check skipped for this device.')
      else:
        # AF check using slanted edge
        uw_slanted_edge_patch = image_processing_utils.get_slanted_edge_patch(
            uw_chart_patch, uw_path, _LENS_SUFFIX_UW, _PATCH_MARGIN)
        w_slanted_edge_patch = image_processing_utils.get_slanted_edge_patch(
            w_chart_patch, w_path, _LENS_SUFFIX_W, _PATCH_MARGIN)
        failed_af_msg, sharpness_uw, sharpness_w = _do_af_check(
            uw_slanted_edge_patch, w_slanted_edge_patch)
        print(f'{_NAME}_uw_sharpness: {sharpness_uw:.4f}')
        print(f'{_NAME}_w_sharpness: {sharpness_w:.4f}')

      if failed_awb_msg or failed_ae_msg or failed_af_msg:
        error_msg = _get_error_msg(failed_awb_msg, failed_ae_msg, failed_af_msg)
        raise AssertionError(f'{_NAME} failed with following errors:\n'
                             f'{error_msg}')

if __name__ == '__main__':
  test_runner.main()
