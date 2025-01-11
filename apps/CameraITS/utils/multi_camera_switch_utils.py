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
"""Utility functions for multi camera switch tests."""

import logging
import math
import numpy

import camera_properties_utils
import image_processing_utils
import its_session_utils
import opencv_processing_utils
import preview_processing_utils

_ARUCO_MARKERS_COUNT = 4
_CH_FULL_SCALE = 255
_CONVERGED_STATE = 2


def check_orientation_and_flip(props, img, img_name_stem, suffix):
  """Checks the sensor orientation and flips image.

  The preview stream captures are flipped based on the sensor
  orientation while using the front camera. In such cases, check the
  sensor orientation and flip the image if needed.

  Args:
    props: obj; camera properties object.
    img: numpy array; image.
    img_name_stem: str; prefix for the img name to be saved.
    suffix: str; suffix for naming image.
  Returns:
    numpy array of the image.
  """
  img = preview_processing_utils.mirror_preview_image_by_sensor_orientation(
      props['android.sensor.orientation'], img)
  image_processing_utils.write_image(img / _CH_FULL_SCALE,
                                     f'{img_name_stem}_{suffix}.png')
  return img


def do_ae_check(
    img1, img2, file_stem, patch_color, suffix1, suffix2, rel_tol, abs_tol):
  """Check two images' luma change is within specified tolerance.

  Args:
    img1: first image.
    img2: second image.
    file_stem: str; path to file.
    patch_color: str; color of the patch to be tested.
    suffix1: str; suffix for the first image file name.
    suffix2: str; suffix for the second image file name.
    rel_tol: float; relative threshold for delta between brightness.
    abs_tol: float; absolute threshold for delta between brightness.
  Returns:
    failed_ae_msg: str; failed AE check messages if any. None otherwise.
    y1_avg: float; y_avg value for the first image.
    y2_avg: float; y_avg value for the second image.
  """
  failed_ae_msg = []
  y1 = opencv_processing_utils.extract_y(
      img1, f'{file_stem}_{suffix1}_y.png')
  y1_avg = numpy.average(y1)
  logging.debug('%s y avg: %.4f', suffix1, y1_avg)

  y2 = opencv_processing_utils.extract_y(
      img2, f'{file_stem}_{suffix2}_y.png')
  y2_avg = numpy.average(y2)
  logging.debug('%s y avg: %.4f', suffix2, y2_avg)
  y_avg_change_percent = (abs(y2_avg - y1_avg) / y1_avg) * 100
  logging.debug('Y avg change percentage: %.4f', y_avg_change_percent)

  if not math.isclose(y1_avg, y2_avg, rel_tol=rel_tol, abs_tol=abs_tol):
    failed_ae_msg.append('Y avg change is greater than threshold value for '
                         f'patches: {patch_color} '
                         f'diff: {abs(y2_avg - y1_avg):.4f} '
                         f'ATOL: {abs_tol} '
                         f'RTOL: {rel_tol} '
                         f'{suffix1} y avg: {y1_avg:.4f} '
                         f'{suffix2} y avg: {y2_avg:.4f} ')
  return failed_ae_msg, y1_avg, y2_avg


def do_af_check(img1, img2, suffix1, suffix2):
  """Checks the AF behavior between two images.

  Args:
    img1: image captured from first camera.
    img2: image captured from second camera.
    suffix1: str; suffix used to save the first image.
    suffix2: str; suffix used to save the second image.
  Returns:
    failed_af_msg: Failed AF check messages if any. None otherwise.
    sharpness1: sharpness value for first image.
    sharpness2: sharpness value for second image.
  """
  failed_af_msg = []
  sharpness1 = image_processing_utils.compute_image_sharpness(img1)
  logging.debug('Sharpness for %s image: %.2f', suffix1, sharpness1)
  sharpness2 = image_processing_utils.compute_image_sharpness(img2)
  logging.debug('Sharpness for %s image: %.2f', suffix2, sharpness2)

  if sharpness2 < sharpness1:
    failed_af_msg.append(f'Sharpness should be higher for {suffix2} lens. '
                         f'{suffix2} sharpness: {sharpness2:.4f} '
                         f'{suffix1} sharpness: {sharpness1:.4f}')
  return failed_af_msg, sharpness1, sharpness2


def do_awb_check(img1, img2, c_atol, patch_color, suffix1, suffix2):
  """Checks total chroma (saturation) difference between two images.

  Args:
    img1: first image.
    img2: second image.
    c_atol: float; threshold for delta C.
    patch_color: str; color of the patch to be tested.
    suffix1: str; suffix for the first image.
    suffix2: str; suffix for the second image.
  Returns:
    failed_awb_msg: failed AWB check messages or None.
  """
  failed_awb_msg = []
  l1, a1, b1 = image_processing_utils.get_lab_means(img1, suffix1)
  l2, a2, b2 = image_processing_utils.get_lab_means(img2, suffix2)

  # Calculate Delta C
  delta_c = numpy.sqrt(abs(a1 - a2)**2 + abs(b1 - b2)**2)
  logging.debug('Delta C: %.4f', delta_c)

  if delta_c > c_atol:
    failed_awb_msg.append('Delta C is greater than the threshold value for '
                          f'patch: {patch_color} '
                          f'Delta C ATOL: {c_atol} '
                          f'Delta C: {delta_c:.4f} '
                          f'{suffix1} L, a, b means: {l1:.4f}, '
                          f'{a1:.4f}, {b1:.4f}'
                          f'{suffix2} L, a, b means: {l2:.4f}, '
                          f'{a2:.4f}, {b2:.4f}')
  return failed_awb_msg


def extract_main_patch(corners, ids, img_rgb, img_path, suffix):
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
    suffix: str; suffix used to save the image.
  Returns:
    rectangle_patch: numpy float image array of the rectangle patch.
  """
  rectangle_patch = opencv_processing_utils.get_patch_from_aruco_markers(
      img_rgb, corners, ids)
  patch_path = img_path.with_name(
      f'{img_path.stem}_{suffix}_patch{img_path.suffix}')
  image_processing_utils.write_image(rectangle_patch/_CH_FULL_SCALE, patch_path)
  return rectangle_patch


def find_aruco_markers(img, img_path, suffix):
  """Detect ArUco markers in the input image.

  Args:
    img: input img with ArUco markers.
    img_path: path to save the image.
    suffix: suffix used to save the image.
  Returns:
    corners: list of detected corners.
    ids: list of int ids for each ArUco markers in the input_img.
  """
  aruco_path = img_path.with_name(
      f'{img_path.stem}_{suffix}_aruco{img_path.suffix}')
  corners, ids, _ = opencv_processing_utils.find_aruco_markers(
      img, aruco_path)
  if len(ids) != _ARUCO_MARKERS_COUNT:
    raise AssertionError(
        f'{_ARUCO_MARKERS_COUNT} ArUco markers should be detected.')
  return corners, ids


def get_error_msg(failed_awb_msg, failed_ae_msg, failed_af_msg):
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


def check_lens_switch_conditions(props, first_api_level, zoom_range_lenses):
  """Check the camera properties for lens switch conditions.

  Args:
    props: Camera properties dictionary.
    first_api_level: First API level.
    zoom_range_lenses: Tuple of two zoom ratio.
  Raises:
      SkipTest: If the device doesn't support the required properties or API
                level.
  """
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
      (zoom_range[0] <= zoom_range_lenses[0] <= zoom_range[1]) and
      (zoom_range[0] <= zoom_range_lenses[1] <= zoom_range[1]))


def find_crossover_point(capture_results):
  """Find the crossover point where the physical camera changes.

  Args:
    capture_results: List of capture results.

  Returns:
    A tuple of (lens_changed, converged_state_counter, physical_id_before)
    lens_changed: Boolean indicating if the lens changed.
    converged_state_counter: Counter for the index of the converged frame.
    physical_id_before: Physical camera ID before the crossover point.
  """
  physical_id_before = None
  counter = 0
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

  return converged_state_counter, lens_changed, converged_state, counter


def get_camera_properties_and_log(cam, capture_results, file_list, counter,
                                  converged_state_counter, lens_suffix1,
                                  lens_suffix2):
  """Get camera properties for the specific cameras and log the information.

  Args:
    cam: An open device session.
    capture_results: List of capture results.
    file_list: List of captured image files.
    counter: Counter for the crossover point.
    converged_state_counter: Counter for the converged state.
    lens_suffix1: Suffix for the first camera.
    lens_suffix2: Suffix for the second camera.
  Returns:
    Tuple of camera properties for both cameras.
  """
  img1_file = file_list[counter-2]
  capture_result_img1 = capture_results[counter-2]
  img1_phy_id = (
      capture_result_img1['android.logicalMultiCamera.activePhysicalId']
  )
  physical_props_img1 = cam.get_camera_properties_by_id(img1_phy_id)
  min_focus_distance_img1 = (
      physical_props_img1['android.lens.info.minimumFocusDistance']
  )
  logging.debug('Min focus distance for %s phy_id: %s is %f',
                lens_suffix1, img1_phy_id, min_focus_distance_img1)

  logging.debug('Capture results %s crossover: %s',
                lens_suffix1, capture_result_img1)
  logging.debug('Capture results %s crossover: %s',
                lens_suffix2, capture_results[counter-1])
  img2_file = file_list[converged_state_counter-1]
  capture_result_img2 = capture_results[converged_state_counter-1]
  logging.debug('Capture results %s crossover converged: %s',
                lens_suffix2, capture_result_img2)
  img2_phy_id = (
      capture_result_img2['android.logicalMultiCamera.activePhysicalId'])
  physical_props_img2 = cam.get_camera_properties_by_id(img2_phy_id)
  min_focus_distance_img2 = (
      physical_props_img2['android.lens.info.minimumFocusDistance']
  )
  logging.debug('Min focus distance for %s phy_id: %s is %f',
                lens_suffix2, img2_phy_id, min_focus_distance_img2)
  return img1_file, img2_file, min_focus_distance_img2
