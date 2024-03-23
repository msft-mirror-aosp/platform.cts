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
"""Verify that frames from UW and W cameras are not distorted."""

import logging
import os
import copy
import cv2
import math
import numpy as np

from mobly import test_runner

import its_base_test
import camera_properties_utils
import image_processing_utils
import its_session_utils
import preview_stabilization_utils
import zoom_capture_utils

_CHESSBOARD_CORNERS = 24
_DIST_TOL = 0.1
_GYRO = 'gyro'
_MAX_ZOOM = 3.0  # UW and W camera covered by 3x zoom
_MAX_ITER = 30
_ACCURACY = 0.001
_NAME = os.path.splitext(os.path.basename(__file__))[0]
_NUM_STEPS = 10


def get_chart_coverage(image, corners):
  """Calculates the chessboard chart coverage in the image.

  Args:
    image: image containing chessboard
    corners: corners of chessboard

  Returns:
    chart_coverage: percentage of the image covered by chart corners
    chart_diagonal_pixels: pixel count from the first corner to the last corner
  """
  first_corner = corners[0].tolist()[0]
  logging.debug('first_corner: %s', first_corner)
  last_corner = corners[-1].tolist()[0]
  logging.debug('last_corner: %s', last_corner)
  chart_diagonal_pixels = math.dist(first_corner, last_corner)
  logging.debug('chart_diagonal_pixels: %s', chart_diagonal_pixels)

  # Calculate chart coverage relative to image diagonal
  image_diagonal = np.sqrt(image.shape[0]**2 + image.shape[1]**2)
  logging.debug('image.shape: %s', image.shape)
  logging.debug('Image diagonal (pixels): %s', image_diagonal)
  chart_coverage = chart_diagonal_pixels / image_diagonal * 100
  logging.debug('Chart coverage: %s', chart_coverage)

  return chart_coverage, chart_diagonal_pixels


def calculate_distortion_error(pattern_size, image_path):
  """Calculates the Distortion Error of the image.

  Args:
    pattern_size: (int, int) chessboard corners.
    image_path: image path

  Returns:
    normalized_distortion_error_percentage: normalized distortion error
      percentage. None if all corners based on pattern_size not found.
    chart_coverage: percentage of the image covered by chessboard chart
  """

  image = cv2.imread(image_path)

  # Convert the image to grayscale
  gray_image = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)

  # Find the checkerboard corners
  found_corners, corners_pass1 = cv2.findChessboardCorners(gray_image,
                                                           pattern_size)
  logging.debug('Found corners: %s', found_corners)
  logging.debug('corners_pass1: %s', corners_pass1)

  if not found_corners:
    logging.debug('Checker pattern not found.')
    return None, None

  # Refine corners
  criteria = (cv2.TERM_CRITERIA_EPS + cv2.TERM_CRITERIA_MAX_ITER, _MAX_ITER,
              _ACCURACY)
  corners = cv2.cornerSubPix(gray_image, corners_pass1, (11, 11), (-1, -1),
                             criteria)
  logging.debug('Refined Corners: %s', corners)

  chart_coverage, chart_diagonal_pixels = get_chart_coverage(image, corners)
  logging.debug('Chart coverage: %s', chart_coverage)

  cv2.drawChessboardCorners(image, pattern_size, corners, found_corners)
  image_processing_utils.write_image(image / 255.0, image_path)

  # Calculate the ideal checkerboard points. These are just corners at unit
  # intervals, of the same dimensions as pattern_size. Looks like..
  # [[ 0.  0.  0.]
  #  [ 1.  0.  0.]
  #  [ 2.  0.  0.]
  #   ...
  #  [21. 23.  0.]
  #  [22. 23.  0.]
  #  [23. 23.  0.]]
  ideal_points = np.zeros((pattern_size[0] * pattern_size[1], 3), np.float32)
  ideal_points[:,:2] = (
      np.mgrid[0:pattern_size[0], 0:pattern_size[1]].T.reshape(-1, 2)
  )

  # Calculate the distortion error
  # Do this by:
  # 1) Calibrate the camera from the detected checkerboard points
  # 2) Project the ideal points, using the camera calibration data.
  # 3) Except, do not use distortion coefficients so we model ideal pinhole
  # 4) Calculate the error of the detected corners relative to the ideal
  # 5) Normalize the average error by the size of the chart
  ret, matrix, dist_coeffs, rotation_vector, translation_vector = (
      cv2.calibrateCamera([ideal_points], [corners], gray_image.shape[::-1],
                          None, None)
  )
  logging.debug('Projection error: %s dist_coeffs: %s', ret, dist_coeffs)

  projected_points = cv2.projectPoints(ideal_points, rotation_vector[0],
                                       translation_vector[0], matrix, None)
  # Reshape projected points to 2D array
  projected = projected_points[0].reshape(-1, 2)
  logging.debug('projected: %s', projected)

  # Calculate the error
  error = projected[0] - corners
  total_distortion_error = np.mean(np.linalg.norm(error, axis=1))
  logging.debug('Total distortion error: %s', total_distortion_error)

  # Calculate the normalized error in pixels
  normalized_distortion_error = total_distortion_error / corners.size
  logging.debug('Normalized average distortion error: %s',
                normalized_distortion_error)

  # Calculate as a percentage of the chart diagonal
  normalized_distortion_error_percentage = (
      normalized_distortion_error / chart_diagonal_pixels * 100
  )
  logging.debug('Normalized percent distortion error: %s',
                normalized_distortion_error_percentage)

  return normalized_distortion_error_percentage, chart_coverage


class PreviewDistortionTest(its_base_test.ItsBaseTest):
  """Test that frames from UW and W cameras are not distorted.

  Captures preview frames at different zoom levels. If whole chart is visible
  in the frame, detect the distortion error. Pass the test if distortion error
  is within the pre-determined TOL.
  """

  def test_preview_distortion(self):
    rot_rig = {}
    log_path = self.log_path

    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:

      props = cam.get_camera_properties()
      props = cam.override_with_hidden_physical_camera_props(props)
      camera_properties_utils.skip_unless(
          camera_properties_utils.zoom_ratio_range(props))

      # Raise error if not FRONT or REAR facing camera
      camera_properties_utils.check_front_or_rear_camera(props)

      # Initialize rotation rig
      rot_rig['cntl'] = self.rotator_cntl
      rot_rig['ch'] = self.rotator_ch
      if rot_rig['cntl'].lower() != 'arduino':
        raise AssertionError(
            f'You must use the arduino controller for {_NAME}.')

      preview_size = preview_stabilization_utils.get_max_preview_test_size(
          cam, self.camera_id)
      logging.debug('preview_size: %s', preview_size)

      # Determine test zoom range and step size
      z_range = props['android.control.zoomRatioRange']
      logging.debug('z_range: %s', z_range)

      # Distortion testing needed for UW and W camera. Reduce zoom range enough
      # such that UW and W camera is covered.
      reduced_z_range = copy.deepcopy(z_range)  # deepcopy to prevent updating
                                                # camera properties
      if reduced_z_range[1] > _MAX_ZOOM:
        reduced_z_range[1] = _MAX_ZOOM
      logging.debug('new_z_range = %s', reduced_z_range)

      z_min, z_max, z_step_size = zoom_capture_utils.get_zoom_params(
          reduced_z_range, _NUM_STEPS)
      camera_properties_utils.skip_unless(z_max > z_min)

      # recording preview
      capture_results, file_list = (
          preview_stabilization_utils.preview_over_zoom_range(
              self.dut, cam, preview_size, z_min, z_max, z_step_size, log_path)
      )

      # Get gyro events
      logging.debug('Reading out inertial sensor events')
      gyro_events = cam.get_sensor_events()[_GYRO]
      logging.debug('Number of gyro samples %d', len(gyro_events))

      pattern_size = (_CHESSBOARD_CORNERS, _CHESSBOARD_CORNERS)
      processed_camera_ids = set()
      failure_msg = None
      for capture_result, img_name in zip(capture_results, file_list):
        z = float(capture_result['android.control.zoomRatio'])
        cam_id = capture_result['android.logicalMultiCamera.activePhysicalId']
        logging.debug('Zoom: %.2f, cam_id: %s, img_name: %s',
                      z, cam_id, img_name)
        img_name = f'{os.path.join(log_path, img_name)}'

        if cam_id in processed_camera_ids:
          os.remove(img_name)
        else:
          distortion_error, chart_coverage = (
              calculate_distortion_error(pattern_size, img_name)
          )

          if distortion_error is None:
            logging.debug('Unable to find checkerboard pattern in %s', img_name)
          else:
            processed_camera_ids.add(cam_id)

            # Don't change print to logging. Used for KPI.
            print(f'{_NAME}_zoom: ', z)
            print(f'{_NAME}_camera_id: ', cam_id)
            print(f'{_NAME}_distortion_error: ', distortion_error)
            print(f'{_NAME}_chart_coverage: ', chart_coverage)

            if distortion_error > _DIST_TOL:
              failure_msg = (f'Distortion error {distortion_error} is greater'
                             f'than tolerance {_DIST_TOL}')

      if not processed_camera_ids:
        raise AssertionError(f'{its_session_utils.NOT_YET_MANDATED_MESSAGE}'
                             '\n\nUnable to find corners in the chessboard.')
      if failure_msg is not None:
        raise AssertionError(f'{its_session_utils.NOT_YET_MANDATED_MESSAGE}'
                             f'\n\n{failure_msg}')


if __name__ == '__main__':
  test_runner.main()

