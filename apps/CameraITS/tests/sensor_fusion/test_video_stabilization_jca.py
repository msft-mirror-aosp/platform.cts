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
"""Verify video is stable during phone movement with JCA."""

import logging
import os
import pathlib
import threading
import time

import camera_properties_utils
import gen2_rig_controller_utils
import its_base_test
import its_session_utils
from mobly import test_runner
import sensor_fusion_utils
import ui_interaction_utils
import video_processing_utils

_IMG_FORMAT = 'png'
_JETPACK_CAMERA_APP_PACKAGE_NAME = 'com.google.jetpackcamera'
_MAX_WIDTH_TESTED = 1920
_MAX_HEIGHT_TESTED = 1080
_MIN_PHONE_MOVEMENT_ANGLE = 5  # Degrees
_NAME = os.path.splitext(os.path.basename(__file__))[0]
_NUM_ROTATIONS = 36
_START_FRAME = 30  # Give 3A 1s to warm up.
_VIDEO_DELAY_TIME = 8  # Seconds
_VIDEO_DURATION = 5.5  # Seconds
_VIDEO_STABILIZATION_FACTOR = 0.7  # 70% of gyro movement allowed.


def _start_rotation_rig_movement(rot_rig, servo_speed):
  """Starts the rotation rig movement in a separate thread.

  Args:
    rot_rig_data: dict with 'cntl' (controller) and 'ch' (channel) defined.
    servo_speed: int; Speed of servo motor.

  Returns:
    A threading.Thread object for the movement.
  """
  controller = rot_rig['cntl']
  channel = rot_rig['ch']

  if controller == gen2_rig_controller_utils.DEFAULT_GEN2_ROTATOR_NAME:
    rotate_func = gen2_rig_controller_utils.rotation_rig
    args = (controller, channel, _NUM_ROTATIONS,
            sensor_fusion_utils.ARDUINO_ANGLES_STABILIZATION)
  else:
    rotate_func = sensor_fusion_utils.rotation_rig
    args = (controller, channel, _NUM_ROTATIONS,
            sensor_fusion_utils.ARDUINO_ANGLES_STABILIZATION, servo_speed,
            sensor_fusion_utils.ARDUINO_MOVE_TIME_STABILIZATION)

  movement_thread = threading.Thread(target=rotate_func, args=args)
  movement_thread.start()
  return movement_thread


def _collect_data(cam, dut, lens_facing, log_path,
                  aspect_ratio, video_quality, rot_rig, servo_speed):
  """Capture a new set of data from the device.

  Captures camera frames while the device is being rotated in the prescribed
  manner.

  Args:
    cam: Camera object.
    dut: An Android controller device object.
    lens_facing: str; Facing of camera.
    log_path: str; Log path where video will be saved.
    aspect_ratio: str; Key string for video aspect ratio defined by JCA.
    rot_rig: dict with 'cntl' and 'ch' defined.
    servo_speed: int; Speed of servo motor.

  Returns:
    output path: Output path for the recording.
  """
  logging.debug('Starting sensor event collection for %s', aspect_ratio)
  ui_interaction_utils.do_jca_video_setup(
      dut,
      log_path,
      facing=lens_facing,
      aspect_ratio=aspect_ratio,
      stabilization_mode=camera_properties_utils.STABILIZATION_MODE_ON,
      video_quality=video_quality,
  )
  # Start camera movement.
  movement_thread = _start_rotation_rig_movement(rot_rig, servo_speed)
  cam.start_sensor_events()
  logging.debug('Gyro Sensor recording started')

  time.sleep(_VIDEO_DELAY_TIME)  # Allow rig to start moving before recording.

  # Record video with JCA and rename it with aspect ratio.
  recording_path = pathlib.Path(
      cam.do_jca_video_capture(dut, log_path, duration=_VIDEO_DURATION * 1000))
  ratio_name = aspect_ratio.replace(' ', '_')
  output_path = (
      recording_path.parent / f'{recording_path.stem}_{ratio_name}.mp4')
  os.rename(recording_path, output_path)
  logging.debug('Output path for recording %s: %s', aspect_ratio, output_path)

  movement_thread.join()  # Wait for movement to stop.
  return output_path


def _initialize_rotation_rig(rotator_cntl, rotator_ch):
  """Initializes and validates rotation rig controller and channel."""
  rot_rig = {'cntl': rotator_cntl, 'ch': rotator_ch}
  if rot_rig['cntl'].lower() not in sensor_fusion_utils.VALID_CONTROLLERS:
    raise AssertionError(f'You must use a valid controller from '
                         f'{sensor_fusion_utils.VALID_CONTROLLERS}.')
  logging.debug('Video qualities tested: %s',
                str(ui_interaction_utils.RATIO_TO_UI_DESCRIPTION.keys()))
  return rot_rig


def _assert_stabilization_results(max_cam_gyro_angles, log_path):
  """Asserts whether the video stabilization criteria are met."""
  test_failures = []
  for ratio_name, frame_data in max_cam_gyro_angles.items():
    max_gyro_angles = frame_data['gyro']
    max_camera_angle = frame_data['cam']
    frame_shape = frame_data['frame_shape']
    logging.debug('Resolution for aspect ratio %s: %s',
                  ratio_name, frame_shape)
    # Ensure width is always the larger dimension
    frame_height = min(frame_shape[0], frame_shape[1])
    frame_width = max(frame_shape[0], frame_shape[1])
    if frame_width > _MAX_WIDTH_TESTED or frame_height > _MAX_HEIGHT_TESTED:
      logging.debug('This resolution (%s x %s) is exempted, skipping test.',
                    frame_width, frame_height)
    else:
      if max_camera_angle >= max_gyro_angles * _VIDEO_STABILIZATION_FACTOR:
        test_failures.append(
            f'{ratio_name} video not stabilized enough! '
            f'Max video angle: {max_camera_angle:.3f}, '
            f'Max gyro angle: {max_gyro_angles:.3f}, '
            f'Ratio: {max_camera_angle / max_gyro_angles:.3f}, '
            f'Threshold: {_VIDEO_STABILIZATION_FACTOR}.'
        )
      else:
        its_session_utils.remove_tmp_files(log_path, 'ITS_JCA_*')

  if test_failures:
    raise AssertionError('\n'.join(test_failures))


class VideoStabilizationJCATest(its_base_test.UiAutomatorItsBaseTest):
  """Tests if video is stabilized.

  Camera is moved in sensor fusion rig on an arc of 15 degrees.
  Speed is set to mimic hand movement and not be too fast.
  Video is captured after rotation rig starts moving, and the
  gyroscope data is dumped.

  Video is processed to dump all of the frames to PNG files.
  Camera movement is extracted from frames by determining max
  angle of deflection in video movement vs max angle of deflection
  in gyroscope movement. Test is a PASS if rotation is reduced in video.
  """

  def setup_class(self):
    super().setup_class()
    self.ui_app = _JETPACK_CAMERA_APP_PACKAGE_NAME
    # Restart CtsVerifier to ensure that correct flags are set.
    ui_interaction_utils.force_stop_app(
        self.dut, its_base_test.CTS_VERIFIER_PKG)
    self.dut.adb.shell(
        'am start -n com.android.cts.verifier/.CtsVerifierActivity')

  def teardown_test(self):
    ui_interaction_utils.force_stop_app(self.dut, self.ui_app)

  def test_video_stabilization_jca(self):
    log_path = self.log_path

    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:
      props = cam.get_camera_properties()
      props = cam.override_with_hidden_physical_camera_props(props)

      # Close camera after props retrieved so that ItsTestActivity can open it.
      cam.close_camera()

      first_api_level = its_session_utils.get_first_api_level(self.dut.serial)
      supported_stabilization_modes = props[
          'android.control.availableVideoStabilizationModes']

      camera_properties_utils.skip_unless(
          first_api_level >= its_session_utils.ANDROID16_API_LEVEL and
          camera_properties_utils.STABILIZATION_MODE_ON
          in supported_stabilization_modes and
          camera_properties_utils.stream_use_case(props))

      # Log ffmpeg version being used.
      video_processing_utils.log_ffmpeg_version()

      # Raise error if not FRONT or REAR facing camera.
      lens_facing = props['android.lens.facing']
      camera_properties_utils.check_front_or_rear_camera(props)

      rot_rig = _initialize_rotation_rig(self.rotator_cntl, self.rotator_ch)
      # Initialize connection with controller.
      servo_speed = (
          sensor_fusion_utils.ARDUINO_SERVO_SPEED_STABILIZATION_TABLET
          if self.tablet_device
          else sensor_fusion_utils.ARDUINO_SERVO_SPEED_STABILIZATION)
      max_cam_gyro_angles = {}

      for ratio_tested in ui_interaction_utils.RATIO_TO_UI_DESCRIPTION.keys():
        logging.debug('Testing ratio: %s', ratio_tested)
        # FHD for 9:16, HD for the rest
        video_quality = (
            ui_interaction_utils.JCA_VIDEO_QUALITY_FHD
            if (ui_interaction_utils.NINE_TO_SIXTEEN_ASPECT_RATIO_DESC
                in ratio_tested)
            else ui_interaction_utils.JCA_VIDEO_QUALITY_HD)
        # Record video.
        recording_path = _collect_data(
            cam, self.dut, lens_facing, log_path, ratio_tested, video_quality,
            rot_rig, servo_speed)

        # Get gyro events.
        logging.debug('Reading out inertial sensor events')
        gyro_events = cam.get_sensor_events()['gyro']
        logging.debug('Number of gyro samples %d', len(gyro_events))

        # Extract frames and their resolution from video.
        frames, frame_shape = (
            video_processing_utils.extract_frames_and_frame_shape_from_video(
            log_path, recording_path, _IMG_FORMAT))
        if not frames:
          raise AssertionError('No frames extracted from video.')

        # Extract camera and gyro rotations.
        max_gyro_angle, max_camera_angle, frame_shape, ratio_name = (
            sensor_fusion_utils.extract_camera_gyro_rotations(
                lens_facing, frames, frame_shape, _START_FRAME,
                _VIDEO_DELAY_TIME, gyro_events, log_path, _NAME, ratio_tested))
        max_cam_gyro_angles[ratio_name] = {'gyro': max_gyro_angle,
                                           'cam': max_camera_angle,
                                           'frame_shape': frame_shape}
        # Assert phone is moved enough during test.
        if max_gyro_angle < _MIN_PHONE_MOVEMENT_ANGLE:
          raise AssertionError(
              f'Phone not moved enough! Movement: {max_gyro_angle}, '
              f'THRESH: {_MIN_PHONE_MOVEMENT_ANGLE} degrees')

      _assert_stabilization_results(max_cam_gyro_angles, log_path)

if __name__ == '__main__':
  test_runner.main()
