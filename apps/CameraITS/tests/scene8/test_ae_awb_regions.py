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
"""Verifies changing AE/AWB regions changes images AE/AWB results."""


import logging
import os.path

import cv2
from mobly import test_runner
import numpy

import its_base_test
import camera_properties_utils
import capture_request_utils
import image_processing_utils
import its_session_utils
import opencv_processing_utils
import video_processing_utils

_AE_AWB_METER_WEIGHT = 1000  # 1 - 1000 with 1000 the highest
_ARUCO_MARKERS_COUNT = 4
_COLORS = ('r', 'g', 'b', 'gray')
_INDEX = 2  # Compare every other frame of preview images
_NAME = os.path.splitext(os.path.basename(__file__))[0]
_NUM_AE_AWB_REGIONS = 4
_NUM_TEST_FRAMES = 1
_REGION_DURATION_MS = 1800
_THRESH_RMS = 2  # Correct behavior is empirically > 4


def _compare_rgb_means_3d(frames):
  """Compare every other frame's RGB's RMS means.

  8 frames are extracted from 8 seconds of preview recording,
  which meters four different AE/AWB regions for 2 seconds each.
  Compares frames from different AE/AWB regions metering.

  Args:
    frames: a list of extracted frames from preview recording.
  Returns:
    failure_messages: (list of strings) list of error messages.
  """
  failure_messages = []
  # Enumerate up to the third to last item in list
  # as to compare it to the last frame
  for i, _ in enumerate(frames[:-_INDEX]):
    rms_diff = image_processing_utils.compute_image_rms_difference_3d(
        frames[i], frames[i+_INDEX])
    logging.debug('frame %d & %d RGB RMS diff: %.2f', i, i+_INDEX, rms_diff)
    if rms_diff < _THRESH_RMS:
      failure_messages.append(
          f'Frame {i} and frame {i+_INDEX} has RGB RMS diff: {rms_diff:.2f}'
          f' THRESH: {_THRESH_RMS}'
      )
  return failure_messages


def _define_metering_regions(img, img_path, chart_path, width, height):
  """Define 4 metering rectangles for AE/AWB regions based on ArUco markers.

  Args:
    img: numpy array; RGB image
    img_path: str; image file location
    chart_path: str; chart file location
    width: int; preview's width in pixels
    height: int; preview's height in pixels

  Returns:
    ae_awb_regions: metering rectangles; AE/AWB metering regions
  """
  # Extract chart coordinates from aruco markers
  aruco_corners, aruco_ids, _ = opencv_processing_utils.find_aruco_markers(
      img, img_path)
  tl, br = opencv_processing_utils.get_chart_boundary_from_aruco_markers(
      aruco_corners, aruco_ids, img, chart_path)

  # Define AE/AWB regions through ArUco markers' positions
  region_1, region_2, region_3, region_4 = (
      opencv_processing_utils.define_metering_rectangle_values(
          tl, br, width, height))

  # Create a dictionary of AE/AWB regions for testing
  ae_awb_regions = {
      'aeAwbRegionOne': region_1,
      'aeAwbRegionTwo': region_2,
      'aeAwbRegionThree': region_3,
      'aeAwbRegionFour': region_4,
  }
  logging.debug('aeAwbRegions: %s', ae_awb_regions)
  return ae_awb_regions


def _extract_and_process_key_frames_from_recording(log_path, file_name):
  """Extract key frames from recordings.

  Args:
    log_path: str; file location
    file_name: str file name for saved video

  Returns:
    dictionary of images
  """
  # TODO: b/330382627 - Add function to preview_processing_utils
  # Extract key frames from video
  key_frame_files = video_processing_utils.extract_key_frames_from_video(
      log_path, file_name)

  # Process key frame files
  key_frames = []
  for file in key_frame_files:
    img = image_processing_utils.convert_image_to_numpy_array(
        os.path.join(log_path, file))
    key_frames.append(img)
  logging.debug('Frame size %d x %d', key_frames[0].shape[1],
                key_frames[0].shape[0])
  return key_frames


class AeAwbRegions(its_base_test.ItsBaseTest):
  """Tests that changing AE and AWB regions changes image's RGB values.

  Test records an 8 seconds preview recording, and meters a different
  AE and AWB region for every 2 seconds. Extracts a frame from each second
  of recording and calculates the RGB's root mean square (RMS) difference
  between every other frame (2 seconds apart). This way, a frame from each
  of the four AE/AWB metering regions is compared to each other.

  """

  def test_ae_awb_regions(self):
    """Test AE and AWB regions."""

    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:
      props = cam.get_camera_properties()
      props = cam.override_with_hidden_physical_camera_props(props)
      log_path = self.log_path
      test_name_with_log_path = os.path.join(log_path, _NAME)

      # Load chart for scene
      its_session_utils.load_scene(
          cam, props, self.scene, self.tablet, self.chart_distance,
          log_path)

      # Check skip conditions
      max_ae_regions = props['android.control.maxRegionsAe']
      first_api_level = its_session_utils.get_first_api_level(self.dut.serial)
      camera_properties_utils.skip_unless(
          first_api_level >= its_session_utils.ANDROID15_API_LEVEL and
          camera_properties_utils.ae_regions(props) and
          not camera_properties_utils.mono_camera(props) and
          max_ae_regions > 0)
      logging.debug('maximum AE regions: %s', max_ae_regions)

      # Find largest preview size to define capture size to find aruco markers
      supported_preview_sizes = cam.get_supported_preview_sizes(self.camera_id)
      logging.debug('Supported preview sizes: %s', supported_preview_sizes)
      preview_size = supported_preview_sizes[-1]
      width = int(preview_size.split('x')[0])
      height = int(preview_size.split('x')[1])
      req = capture_request_utils.auto_capture_request()
      fmt = {'format': 'yuv', 'width': width, 'height': height}
      cam.do_3a(lock_ae=True, lock_awb=True)
      cap = cam.do_capture(req, fmt)

      # Save image and convert to numpy array
      img = image_processing_utils.convert_capture_to_rgb_image(
          cap, props=props)
      img_path = f'{test_name_with_log_path}_aruco_markers.jpg'
      image_processing_utils.write_image(img, img_path)
      img = image_processing_utils.convert_image_to_uint8(img)

      # Define AE/AWB metering regions
      chart_path = f'{test_name_with_log_path}_chart_boundary.jpg'
      ae_awb_regions = _define_metering_regions(
          img, img_path, chart_path, width, height)

      # Do preview recording with pre-defined AE/AWB regions
      recording_obj = cam.do_preview_recording_with_dynamic_ae_awb_region(
          preview_size, ae_awb_regions, _REGION_DURATION_MS)
      logging.debug('Tested quality: %s', recording_obj['quality'])

      # Grab the video from the save location on DUT
      self.dut.adb.pull([recording_obj['recordedOutputPath'], log_path])
      file_name = recording_obj['recordedOutputPath'].split('/')[-1]
      logging.debug('file_name: %s', file_name)

      # Extract and process key frames from preview recording
      key_frames = _extract_and_process_key_frames_from_recording(
          log_path, file_name)

      # Compare RGB's RMS between frames
      failure_messages = _compare_rgb_means_3d(key_frames)

    if failure_messages:
      raise AssertionError('\n'.join(failure_messages))

if __name__ == '__main__':
  test_runner.main()
