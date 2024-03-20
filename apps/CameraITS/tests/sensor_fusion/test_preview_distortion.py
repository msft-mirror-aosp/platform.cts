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
"""Verify that frames are not distorted at various zoom levels."""

import logging
import os

from mobly import test_runner

import its_base_test
import camera_properties_utils
import its_session_utils
import preview_stabilization_utils

_GYRO = 'gyro'
_NAME = os.path.splitext(os.path.basename(__file__))[0]


class PreviewDistortionTest(its_base_test.ItsBaseTest):
  """Test that frames are not distorted at various zoom levels.

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
      logging.debug('preview_size = %s', preview_size)

      # recording preview
      z_range = props['android.control.zoomRatioRange']
      try:
        capture_results, file_list, z_min, z_max = (
            preview_stabilization_utils.preview_over_zoom_range(
                self.dut, cam, preview_size, z_range, log_path)
        )
        logging.debug('z_min: %.2f z_max: %.2f', z_min, z_max)
      except ValueError as e:
        camera_properties_utils.skip_unless(False, e)

      # Get gyro events
      logging.debug('Reading out inertial sensor events')
      gyro_events = cam.get_sensor_events()[_GYRO]
      logging.debug('Number of gyro samples %d', len(gyro_events))

      for capture_result, img_name in zip(capture_results, file_list):
        z = float(capture_result['android.control.zoomRatio'])

        logging.debug('Zoom: %.2f, image name: %s', z, img_name)

        img_name = f'{os.path.join(log_path, img_name)}'

        # TODO: b/314017176 - Plot and print [z,distortion].
        #                     Verify distortion in TOL.


if __name__ == '__main__':
  test_runner.main()

