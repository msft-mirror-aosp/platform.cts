# Copyright 2016 The Android Open Source Project
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
"""Verifies image is not flipped or mirrored."""


import logging
import os
import types

import cv2
from mobly import test_runner

import its_base_test
import camera_properties_utils
import capture_request_utils
import image_processing_utils
import its_session_utils
import opencv_processing_utils

_ARUCO_MARKERS_NOT_FOUND_MESSAGE = (
    'ArUco markers not found in all orientations. '
    'Please check that all 4 markers are visible in scene.'
)
_CV2_FLIP_ACROSS_X_AXIS = 0  # flip across x axis (flip)
_CV2_FLIP_ACROSS_Y_AXIS = 1  # flip across y axis (mirror)
_FAILURE_MESSAGE = (
    'Image is {orientation}, ArUco markers found in this orientation.')
_NAME = os.path.splitext(os.path.basename(__file__))[0]
_NUM_ARUCO_MARKERS = 4
_ROTATED_RESULTS = types.MappingProxyType({
    'nominal': 'rotated',
    'flip': 'mirrored',
    'mirror': 'flipped'
})
_TOP_LEFT_ARUCO_MARKER_ID = 0
_TOP_RIGHT_ARUCO_MARKER_ID = 1
_TOP_ARUCO_PAIR = (_TOP_LEFT_ARUCO_MARKER_ID, _TOP_RIGHT_ARUCO_MARKER_ID)
_VGA_W, _VGA_H = 640, 480


def _get_orientation_map(img_bgr):
  """Creates a map of images with different orientations."""
  return {
      'nominal': img_bgr,
      'flip': cv2.flip(img_bgr, _CV2_FLIP_ACROSS_X_AXIS),
      'mirror': cv2.flip(img_bgr, _CV2_FLIP_ACROSS_Y_AXIS),
  }


def _do_capture_and_convert_to_uint8(cam, props, name_with_log_path):
  """Captures and processes an image for ArUco marker detection.

  Args:
    cam: An open its session.
    props: Properties of cam.
    name_with_log_path: file with log_path to save the captured image.

  Returns:
    A numpy array BGR image.
  """
  cam.do_3a()
  req = capture_request_utils.auto_capture_request()
  fmt = {'format': 'yuv', 'width': _VGA_W, 'height': _VGA_H}
  cap = cam.do_capture(req, fmt)
  img = image_processing_utils.convert_capture_to_rgb_image(cap, props=props)
  image_processing_utils.write_image(
      img, f'{name_with_log_path}_capture_for_aruco_detection.jpg')
  img_bgr = cv2.cvtColor(image_processing_utils.convert_image_to_uint8(img),
                         cv2.COLOR_RGB2BGR)
  return img_bgr


def _check_rotated_aruco(corners, ids, orientation, first_api_level):
  """Checks for rotated ArUco markers and raises an error if needed.

  Args:
    corners: list of detected ArUco markers corners.
    ids: list of int ids for each detected ArUco markers.
    orientation: str, the orientation of the processed image.
    first_api_level: int, the first API level value.

  Raises:
    AssertionError: if the image is rotated and the first API level is not
    Android 15, and if rotated ArUco markers are found in flip or mirror images.
  """
  if opencv_processing_utils.detect_180_degree_rotation_with_aruco_markers(
      corners, ids, *_TOP_ARUCO_PAIR):
    if (orientation == 'nominal' and
        first_api_level < its_session_utils.ANDROID15_API_LEVEL):
      logging.warning(
          'Image is %s, ArUco markers found in '
          'this orientation. Allowing test to pass for first_api_level < '
          'Android 15.', _ROTATED_RESULTS[orientation])
    else:
      raise AssertionError(_FAILURE_MESSAGE.format(
          orientation=_ROTATED_RESULTS[orientation]))


def _check_nominal_orientation(orientation):
  """Raises an error if Aruco markers found in non-nominal orientation."""
  if orientation != 'nominal':
    raise AssertionError(_FAILURE_MESSAGE.format(orientation=orientation))
  logging.debug('ArUco markers found in nominal orientation.')


def test_image_orientation_with_aruco_markers(
    cam, props, first_api_level, name_with_log_path):
  """Test if image is flipped or mirrored using ArUco markers.

  Args:
    cam: An open its session.
    props: Properties of cam.
    first_api_level: int; first API level value.
    name_with_log_path: file with log_path to save the captured image.
  """
  img_bgr = _do_capture_and_convert_to_uint8(cam, props, name_with_log_path)
  orientation_map = _get_orientation_map(img_bgr)

  for orientation, chart in orientation_map.items():
    logging.debug('Finding ArUco markers in %s orientation.', orientation)
    try:
      corners, ids, _ = opencv_processing_utils.find_aruco_markers(
          chart, f'{name_with_log_path}_aruco_chart.jpg', _NUM_ARUCO_MARKERS)
    except AssertionError:
      logging.debug('Aruco markers not found in %s orientation.', orientation)
      corners, ids = [], []

    if len(ids) == _NUM_ARUCO_MARKERS:
      _check_rotated_aruco(corners, ids, orientation, first_api_level)
      _check_nominal_orientation(orientation)
      return  # PASS: Non-rotated ArUco markers found in nominal orientation.

  # if no markers found in all orientations
  raise AssertionError(_ARUCO_MARKERS_NOT_FOUND_MESSAGE)


class FlipMirrorTest(its_base_test.ItsBaseTest):
  """Test to verify if the image is flipped or mirrored."""

  def test_flip_mirror(self):
    """Test if image is properly oriented."""
    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:
      props = cam.get_camera_properties()
      props = cam.override_with_hidden_physical_camera_props(props)
      name_with_log_path = os.path.join(self.log_path, _NAME)
      first_api_level = its_session_utils.get_first_api_level(self.dut.serial)

      # check SKIP conditions
      camera_properties_utils.skip_unless(
          not camera_properties_utils.mono_camera(props))

      # load chart for scene
      its_session_utils.load_scene(
          cam, props, self.scene, self.tablet, self.chart_distance,
          chart_scaling=self.chart_scaling)

      # test that image is not flipped, mirrored, or rotated
      test_image_orientation_with_aruco_markers(
          cam, props, first_api_level, name_with_log_path)


if __name__ == '__main__':
  test_runner.main()
