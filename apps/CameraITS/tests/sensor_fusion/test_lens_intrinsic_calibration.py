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
"""Verify that lens intrinsics changes when OIS is triggered."""

import logging
import math
import numpy as np
import os

from mobly import test_runner
from matplotlib import pylab
import matplotlib.pyplot

import its_base_test
import camera_properties_utils
import its_session_utils
import preview_stabilization_utils
import sensor_fusion_utils
import video_processing_utils

_HIGH_RES_SIZE = '3840x2160'  # Resolution for 4K quality
_NAME = os.path.splitext(os.path.basename(__file__))[0]
_MIN_PHONE_MOVEMENT_ANGLE = 5  # degrees
_PRINCIPAL_POINT_THRESH = 1  # Threshold for principal point changes in pixels.
_START_FRAME = 30  # give 3A some frames to warm up
_VIDEO_DELAY_TIME = 5.5  # seconds


def _get_preview_test_size(cam, camera_id):
  """Finds the max preview size to be tested.

  If the device supports the _HIGH_RES_SIZE preview size then
  it uses that for testing, otherwise uses the max supported
  preview size.

  Args:
    cam: camera object
    camera_id: str; camera device id under test

  Returns:
    preview_test_size: str; wxh resolution of the size to be tested
  """

  lowest_res_area = video_processing_utils.LOWEST_RES_TESTED_AREA
  resolution_to_area = lambda s: int(s.split('x')[0])*int(s.split('x')[1])
  supported_preview_sizes = cam.get_supported_preview_sizes(camera_id)
  supported_preview_sizes = [size for size in supported_preview_sizes
                             if resolution_to_area(size)
                             >= lowest_res_area]
  logging.debug('Supported preview resolutions: %s', supported_preview_sizes)

  if _HIGH_RES_SIZE in supported_preview_sizes:
    preview_test_size = _HIGH_RES_SIZE
  else:
    preview_test_size = supported_preview_sizes[-1]

  logging.debug('Selected preview resolution: %s', preview_test_size)

  return preview_test_size


def calculate_principal_point(f_x, f_y, c_x, c_y, s):
  """Calculates the principal point of a camera given its intrinsic parameters.

  Args:
    f_x: Horizontal focal length.
    f_y: Vertical focal length.
    c_x: X coordinate of the optical axis.
    c_y: Y coordinate of the optical axis.
    s: Skew parameter.

  Returns:
    A numpy array containing the principal point coordinates (px, py).
  """

  # Create the camera calibration matrix
  transform_k = np.array([[f_x, s, c_x],
                          [0, f_y, c_y],
                          [0, 0, 1]])

  # The Principal point is the intersection of the optical axis with the
  # image plane. Since the optical axis passes through the camera center
  # (defined by K), the principal point coordinates are simply the
  # projection of the camera center onto the image plane.
  principal_point = np.dot(transform_k, np.array([0, 0, 1]))

  # Normalize by the homogeneous coordinate
  px = principal_point[0] / principal_point[2]
  py = principal_point[1] / principal_point[2]

  return px, py


def plot_principal_points(principal_points_dist, start_frame,
                          video_quality, plot_name_stem):
  """Plot principal points values vs Camera frames.

  Args:
    principal_points_dist: array of principal point distances in pixels/frame
    start_frame: int value of start frame
    video_quality: str for video quality identifier
    plot_name_stem: str; name of the plot
  """

  pylab.figure(video_quality)
  frames = range(start_frame, len(principal_points_dist)+start_frame)
  pylab.title(f'Lens Intrinsics vs frame {video_quality}')
  pylab.plot(frames, principal_points_dist, '-ro', label='dist')
  pylab.xlabel('Frame #')
  pylab.ylabel('Principal points in pixels')
  matplotlib.pyplot.savefig(f'{plot_name_stem}.png')
  pylab.close(video_quality)


def verify_lens_intrinsics(recording_obj, gyro_events, test_name, log_path):
  """Verify principal points changes due to OIS changes.

  Args:
    recording_obj: Camcorder recording object.
    gyro_events: Gyroscope events collected while recording.
    test_name: Name of the test
    log_path: Path for the log file

  Returns:
    A dictionary containing the maximum gyro angle, the maximum changes of
    principal point, and a failure message if principal point doesn't change
    due to OIS changes triggered by device motion.
  """

  file_name = recording_obj['recordedOutputPath'].split('/')[-1]
  logging.debug('recorded file name: %s', file_name)
  video_size = recording_obj['videoSize']
  logging.debug('video size: %s', video_size)

  capture_results = recording_obj['captureMetadata']
  file_name_stem = f'{os.path.join(log_path, test_name)}_{video_size}'

  # Extract principal points from capture result
  principal_points = []
  for capture_result in capture_results:
    intrinsic_calibration = capture_result['android.lens.intrinsicCalibration']
    logging.debug('IntrinsicCalibration = %s', str(intrinsic_calibration))

    principal_point = calculate_principal_point(intrinsic_calibration[0],
                                                intrinsic_calibration[1],
                                                intrinsic_calibration[2],
                                                intrinsic_calibration[3],
                                                intrinsic_calibration[4])
    principal_points.append(principal_point)

  # Calculate variations in principal points
  first_point = principal_points[0]
  principal_points_diff = [math.dist(first_point, x) for x in principal_points]

  plot_principal_points(principal_points_diff,
                        _START_FRAME,
                        video_size,
                        file_name_stem)

  max_pp_diff = max(principal_points_diff)

  # Extract gyro rotations
  sensor_fusion_utils.plot_gyro_events(
      gyro_events, f'{test_name}_{video_size}', log_path)
  gyro_rots = sensor_fusion_utils.conv_acceleration_to_movement(
      gyro_events, _VIDEO_DELAY_TIME)
  max_gyro_angle = sensor_fusion_utils.calc_max_rotation_angle(
      gyro_rots, 'Gyro')
  logging.debug(
      'Max deflection (degrees) %s: gyro: %.3f',
      video_size, max_gyro_angle)

  # Assert phone is moved enough during test
  if max_gyro_angle < _MIN_PHONE_MOVEMENT_ANGLE:
    raise AssertionError(
        f'Phone not moved enough! Movement: {max_gyro_angle}, '
        f'THRESH: {_MIN_PHONE_MOVEMENT_ANGLE} degrees')

  failure_msg = None
  if(max_pp_diff > _PRINCIPAL_POINT_THRESH):
    logging.debug('Principal point diff: x = %.2f', max_pp_diff)
  else:
    failure_msg = (
        'Change in principal point not enough with respect to OIS changes. '
        f'video_size: {video_size}, '
        f'Max Principal Point deflection (pixels):  {max_pp_diff:.3f}, '
        f'Max gyro angle: {max_gyro_angle:.3f}, '
        f'THRESHOLD : {_PRINCIPAL_POINT_THRESH}.')

  return {'gyro': max_gyro_angle, 'max_pp_diff': max_pp_diff,
          'failure': failure_msg}


class LensIntrinsicCalibrationTest(its_base_test.ItsBaseTest):
  """Tests if lens intrinsics changes when OIS is triggered.

  Camera is moved in sensor fusion rig on an angle of 15 degrees.
  Speed is set to mimic hand movement (and not be too fast).
  Preview is recorded after rotation rig starts moving, and the
  gyroscope data is dumped.

  Camera movement is extracted from angle of deflection in gyroscope
  movement. Test is a PASS if principal point in lens intrinsics
  changes upon camera movement.
  """

  def test_lens_intrinsic_calibration(self):
    rot_rig = {}
    log_path = self.log_path

    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:

      props = cam.get_camera_properties()
      props = cam.override_with_hidden_physical_camera_props(props)

      # Check if OIS supported
      camera_properties_utils.skip_unless(
          camera_properties_utils.optical_stabilization_supported(props))

      # Initialize rotation rig
      rot_rig['cntl'] = self.rotator_cntl
      rot_rig['ch'] = self.rotator_ch
      if rot_rig['cntl'].lower() != 'arduino':
        raise AssertionError(
            f'You must use the arduino controller for {_NAME}.')

      preview_test_size = _get_preview_test_size(cam, self.camera_id)

      recording_obj = preview_stabilization_utils.collect_data(
          cam, self.tablet_device, preview_test_size, False,
          rot_rig=rot_rig, ois=True)

      # Get gyro events
      logging.debug('Reading out inertial sensor events')
      gyro_events = cam.get_sensor_events()['gyro']
      logging.debug('Number of gyro samples %d', len(gyro_events))

      # Grab the video from the save location on DUT
      self.dut.adb.pull([recording_obj['recordedOutputPath'], log_path])

      stabilization_result = verify_lens_intrinsics(
          recording_obj, gyro_events, _NAME, log_path)

      # Don't change print to logging. Used for KPI.
      print(f'{_NAME}_max_principal_point_diff: ',
            stabilization_result['max_pp_diff'])
      # Assert PASS/FAIL criteria
      if stabilization_result['failure'] is not None:
        first_api_level = its_session_utils.get_first_api_level(self.dut.serial)
        failure_msg = stabilization_result['failure']
        if first_api_level >= its_session_utils.ANDROID15_API_LEVEL:
          raise AssertionError(failure_msg)
        else:
          raise AssertionError(f'{its_session_utils.NOT_YET_MANDATED_MESSAGE}'
                               f'\n\n{failure_msg}')


if __name__ == '__main__':
  test_runner.main()
