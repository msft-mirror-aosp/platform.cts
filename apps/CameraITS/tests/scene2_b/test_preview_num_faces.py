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
"""Verifies 3 faces with different skin tones are detected in preview."""


import logging
import os.path

import cv2
from mobly import test_runner

import its_base_test
import camera_properties_utils
import image_processing_utils
import its_session_utils
import opencv_processing_utils
import preview_processing_utils
import video_processing_utils

_CV2_GREEN = (0, 255, 0)
_CV2_LINE_THICKNESS = 3
_FD_MODE_OFF, _FD_MODE_SIMPLE, _FD_MODE_FULL = 0, 1, 2
_FRAME_INDEX = -1  # last frame
_NAME = os.path.splitext(os.path.basename(__file__))[0]
_PREVIEW_FACES_MIN_NUM = 3
_PREVIEW_RECORDING_DURATION_SECONDS = 3
_RGB_FULL_CHANNEL = 255
_TEST_REQUIRED_MPC = 34
_VALID_FD_MODES = {_FD_MODE_OFF, _FD_MODE_SIMPLE, _FD_MODE_FULL}


def _do_preview_recording_and_retrieve_result(
    dut, cam, preview_size, fd_mode, log_path):
  """Issue a preview request and read back the preview recording object.

  Args:
    dut: obj; Android controller device object.
    cam: obj; Camera obj.
    preview_size: str; Preview resolution at which to record. ex. "1920x1080".
    fd_mode: int; STATISTICS_FACE_DETECT_MODE. Set if not None.
    log_path: str; Log path to save preview recording.

  Returns:
    result: obj; Recording object.
  """
  # Record preview video with face detection.
  result = cam.do_preview_recording(
      preview_size, _PREVIEW_RECORDING_DURATION_SECONDS, stabilize=False,
      zoom_ratio=None, face_detect_mode=fd_mode)
  dut.adb.pull(
      [result['recordedOutputPath'], log_path])
  logging.debug('Preview recording with face detection is completed.')

  return result


def _draw_face_rectangles(result, faces, preview_img):
  """Draw boxes around faces in green and save image.

  Args:
    result: obj; Recorded object returned from ItsService.
    faces: list; List of dicts with face information.
    preview_img: str; Numpy image array.
  """
  # draw boxes around faces in green
  crop_region = result['captureMetadata'][_FRAME_INDEX][
      'android.scaler.cropRegion']
  faces_cropped = opencv_processing_utils.correct_faces_for_crop(
      faces, preview_img, crop_region)
  for (l, r, t, b) in faces_cropped:
    cv2.rectangle(
        preview_img, (l, t), (r, b), _CV2_GREEN, _CV2_LINE_THICKNESS)


class PreviewNumFacesTest(its_base_test.ItsBaseTest):
  """Test face detection with different skin tones in preview."""

  def test_preview_num_faces(self):
    """Test face detection."""
    log_path = self.log_path
    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:
      props = cam.get_camera_properties()
      props = cam.override_with_hidden_physical_camera_props(props)

      # Load chart for scene.
      its_session_utils.load_scene(
          cam, props, self.scene, self.tablet, self.chart_distance,
          log_path=log_path)

      # Check media performance class.
      should_run = camera_properties_utils.face_detect(props)
      media_performance_class = its_session_utils.get_media_performance_class(
          self.dut.serial)
      if media_performance_class >= _TEST_REQUIRED_MPC and not should_run:
        its_session_utils.raise_mpc_assertion_error(
            _TEST_REQUIRED_MPC, _NAME, media_performance_class)

      # Check skip conditions.
      camera_properties_utils.skip_unless(should_run)
      mono_camera = camera_properties_utils.mono_camera(props)
      fd_modes = props['android.statistics.info.availableFaceDetectModes']

      cam.do_3a(mono_camera=mono_camera)
      for fd_mode in fd_modes:
        logging.debug('Face detection mode: %d', fd_mode)
        if fd_mode not in _VALID_FD_MODES:
          raise AssertionError(f'FD mode {fd_mode} not in MODES! '
                               f'MODES: {_VALID_FD_MODES}')

        # Find largest preview size string and set as recording size.
        preview_size = preview_processing_utils.get_max_preview_test_size(
            cam, self.camera_id)
        logging.debug('Preview size used for recording: %s', preview_size)

        # Issue a preview request and read back the preview recording object.
        result = _do_preview_recording_and_retrieve_result(
            self.dut, cam, preview_size, fd_mode, log_path)
        preview_file_name = (
            result['recordedOutputPath'].split('/')[-1])
        logging.debug('Recorded preview name: %s', preview_file_name)

        # Get last key frames from the preview recording.
        preview_img = (
            video_processing_utils.extract_last_key_frame_from_recording(
                log_path, preview_file_name))

        # Check face detect mode is correctly set.
        fd_mode_cap = (
            result['captureMetadata'][_FRAME_INDEX][
                'android.statistics.faceDetectMode'])
        if fd_mode_cap != fd_mode:
          raise AssertionError(f'metadata {fd_mode_cap} != req {fd_mode}')

        # 0 faces should be returned for OFF mode.
        # Skip remaining checks for _FD_MODE_OFF if no faces were detected.
        faces = result['captureMetadata'][_FRAME_INDEX][
            'android.statistics.faces']
        if fd_mode == _FD_MODE_OFF:
          if faces:
            raise AssertionError(f'Error: faces detected in OFF: {faces}')
          continue

        # If front camera, flip preview image to match camera capture.
        file_name_stem = os.path.join(log_path, _NAME)
        if (props['android.lens.facing'] ==
            camera_properties_utils.LENS_FACING['FRONT']):
          preview_img = (
              preview_processing_utils.mirror_preview_image_by_sensor_orientation(
                  props['android.sensor.orientation'], preview_img))
        else:
          file_name_stem = os.path.join(log_path, 'rear_preview')

        # Draw boxes around faces in green and save image.
        _draw_face_rectangles(result, faces, preview_img)

        # Save image with green rectangles
        img_name = f'{file_name_stem}_fd_mode_{fd_mode}.jpg'
        image_processing_utils.write_image(
            preview_img / _RGB_FULL_CHANNEL, img_name)

        # Check if the expected number of faces were detected.
        num_faces = len(faces)
        if num_faces != _PREVIEW_FACES_MIN_NUM:
          raise AssertionError(f'Face detection in preview found {num_faces}'
                               f' faces, but expected {_PREVIEW_FACES_MIN_NUM}')
        logging.debug('Face detection in preview found %d faces', num_faces)

if __name__ == '__main__':
  test_runner.main()

