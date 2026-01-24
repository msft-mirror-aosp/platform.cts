# Copyright 2022 The Android Open Source Project
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
"""Verifies JPEG and YUV still capture images are pixel-wise matching."""


import cv2
import logging
import os.path
from mobly import test_runner

import its_base_test
import camera_properties_utils
import capture_request_utils
import image_processing_utils
import its_session_utils
import numpy as np

_MAX_IMG_SIZE = (1920, 1080)
_NAME = os.path.splitext(os.path.basename(__file__))[0]
_TEST_REQUIRED_MPC = 33
_THRESHOLD_MAX_RMS_DIFF_YUV_JPEG = 0.03  # YUV/JPEG bit exactness threshold
_THRESHOLD_MAX_RMS_DIFF_USE_CASE = 0.1  # Catch swapped color channels
_THRESHOLD_MAX_E76_DIFF_USE_CASE_ANDROID17 = 5.0  # For Android API 17 and above
_USE_CASE_PREVIEW = 1
_USE_CASE_STILL_CAPTURE = 2
_USE_CASE_VIDEO_RECORD = 3
_USE_CASE_PREVIEW_VIDEO_STILL = 4
_USE_CASE_VIDEO_CALL = 5
_USE_CASE_NAME_MAP = {
    _USE_CASE_PREVIEW: 'preview',
    _USE_CASE_STILL_CAPTURE: 'still_capture',
    _USE_CASE_VIDEO_RECORD: 'video_record',
    _USE_CASE_PREVIEW_VIDEO_STILL: 'preview_video_still',
    _USE_CASE_VIDEO_CALL: 'video_call'
}


def _get_lab_mean_values(img):
  """Computes the mean values of the 'L', 'A', and 'B' channels.

  Converts the img from RGB to CIELAB color space and calculates the mean values
  of L, A and B channels only for the non-transparent regions of the image

  Args:
    img: img array in RGB colorspace. Expected to be float32 [0.0, 1.0].
  Returns:
    mean_l, mean_a, mean_b: mean value of l, a, b channels
  """

  # The scaling below assumes cv2.COLOR_RGB2LAB on uint8 input
  img_lab = cv2.cvtColor(img, cv2.COLOR_RGB2LAB)
  mean_l = np.mean(img_lab[:, :, 0])
  mean_a = np.mean(img_lab[:, :, 1])
  mean_b = np.mean(img_lab[:, :, 2])
  logging.debug('L, A, B values: %.2f %.2f %.2f', mean_l, mean_a, mean_b)
  return mean_l, mean_a, mean_b


def _get_delta_e76(patch1, patch2):
  """Computes the CIE76 delta E value between two image patches.

  Euclidean distance between two colors in CIELAB color space.

  Args:
    patch1: first image patch for comparison.
    patch2: second image patch for comparison.

  Returns:
    delta_e76: delta E76 value between patch1 and patch2.
  """
  l_1, a_1, b_1 = _get_lab_mean_values(patch1)
  l_2, a_2, b_2 = _get_lab_mean_values(patch2)
  return np.sqrt((l_1 - l_2)**2 + (a_1 - a_2)**2 + (b_1 - b_2)**2)


class YuvJpegCaptureSamenessTest(its_base_test.ItsBaseTest):
  """Test capturing a single frame as both YUV and JPEG outputs."""

  def test_yuv_jpeg_capture_sameness(self):
    logging.debug('Starting %s', _NAME)
    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:
      props = cam.get_camera_properties()
      props = cam.override_with_hidden_physical_camera_props(props)
      log_path = self.log_path

      # check media performance class
      should_run = camera_properties_utils.stream_use_case(props)
      media_performance_class = its_session_utils.get_media_performance_class(
          self.dut.serial)
      if media_performance_class >= _TEST_REQUIRED_MPC and not should_run:
        its_session_utils.raise_mpc_assertion_error(
            _TEST_REQUIRED_MPC, _NAME, media_performance_class)

      # check SKIP conditions
      camera_properties_utils.skip_unless(should_run)

      # Load chart for scene
      its_session_utils.load_scene(
          cam, props, self.scene, self.tablet, self.chart_distance)

      # Find the maximum mandatory size supported by all use cases
      display_size = cam.get_display_size()
      max_camcorder_profile_size = cam.get_max_camcorder_profile_size(
          self.camera_id)
      size_bound = min([_MAX_IMG_SIZE, display_size,
                        max_camcorder_profile_size],
                       key=lambda t: int(t[0])*int(t[1]))

      logging.debug('display_size %s, max_camcorder_profile_size %s, '
                    'size_bound %s', display_size, max_camcorder_profile_size,
                    size_bound)
      first_api_level = its_session_utils.get_first_api_level(self.dut.serial)
      w, h = capture_request_utils.get_available_output_sizes(
          'yuv', props, max_size=size_bound)[0]
      jpeg_sizes = capture_request_utils.get_available_output_sizes(
          'jpeg', props, match_ar_size=(w, h))

      should_skip = not jpeg_sizes
      # skip since no jpeg size with the same aspect ratio as YUV was found
      skip_msg = 'same jpeg and yuv aspect ratio not found'
      camera_properties_utils.skip_unless(not should_skip, skip_msg)

      jpeg_w, jpeg_h = w, h
      same_jpeg_and_yuv_available = (w, h) in jpeg_sizes
      # no jpeg size found, which is the same as YUV
      skip_msg = ('same jpeg + yuv sizes not available within threshold '
                  f'first_api_level {first_api_level}')
      should_skip = (
          first_api_level < its_session_utils.ANDROID15_API_LEVEL and
          not same_jpeg_and_yuv_available)
      camera_properties_utils.skip_unless(not should_skip, skip_msg)
      if not same_jpeg_and_yuv_available:
        # Get the first size with the same AR as YUV
        jpeg_w, jpeg_h = jpeg_sizes[0]

      # Create requests
      fmt_yuv = {'format': 'yuv', 'width': w, 'height': h,
                 'useCase': _USE_CASE_STILL_CAPTURE}
      fmt_jpg = {'format': 'jpeg', 'width': jpeg_w, 'height': jpeg_h,
                 'useCase': _USE_CASE_STILL_CAPTURE}
      logging.debug(
          'YUV width: %d, height: %d, JPEG width %d height %d',
          w, h, jpeg_w, jpeg_h)

      cam.do_3a()
      req = capture_request_utils.auto_capture_request()
      req['android.jpeg.quality'] = 100

      cap_yuv, cap_jpg = cam.do_capture(req, [fmt_yuv, fmt_jpg])
      rgb_yuv = image_processing_utils.convert_capture_to_rgb_image(
          cap_yuv, True)
      file_stem = os.path.join(log_path, _NAME)
      image_processing_utils.write_image(rgb_yuv, f'{file_stem}_yuv.jpg')
      rgb_jpg = image_processing_utils.convert_capture_to_rgb_image(
          cap_jpg, True)
      image_processing_utils.write_image(rgb_jpg, f'{file_stem}_jpg.jpg')

      if jpeg_w != w:
        scale_factor = w / jpeg_w
        rgb_jpg = cv2.resize(
            rgb_jpg, None, fx=scale_factor, fy=scale_factor)
        image_processing_utils.write_image(
            rgb_jpg, f'{file_stem}_jpg_downscaled.jpg')

      rms_diff = image_processing_utils.compute_image_rms_difference_3d(
          rgb_yuv, rgb_jpg)
      msg = f'RMS diff: {rms_diff:.4f}'
      logging.debug('%s', msg)
      print(f'test_yuv_jpeg_capture_sameness_rms_diff: {rms_diff:.4f}')
      marginal_pass_msg = []
      if rms_diff >= _THRESHOLD_MAX_RMS_DIFF_YUV_JPEG:
        raise AssertionError(
            f'{msg}, ATOL: {_THRESHOLD_MAX_RMS_DIFF_YUV_JPEG:.4f}')
      else:
        marginal_pass_tol = (_THRESHOLD_MAX_RMS_DIFF_YUV_JPEG *
                             its_session_utils.MARGINAL_PASS_FACTOR)
        if rms_diff >= marginal_pass_tol:
          marginal_pass_msg.append(f'{msg}, ATOL: {marginal_pass_tol:.4f}')

      # Create requests for all use cases, and make sure they are at least
      # similar enough with the STILL_CAPTURE YUV. For example, the color
      # channels must be valid.
      num_tests = 0
      num_fail = 0
      for use_case in _USE_CASE_NAME_MAP:
        num_tests += 1
        cam.do_3a()
        fmt_yuv_use_case = {'format': 'yuv', 'width': w, 'height': h,
                            'useCase': use_case}
        cap_yuv_use_case = cam.do_capture(req, [fmt_yuv_use_case])
        rgb_yuv_use_case = image_processing_utils.convert_capture_to_rgb_image(
            cap_yuv_use_case, True)
        use_case_name = _USE_CASE_NAME_MAP[use_case]
        logging.debug('Use Case: %s, rgb_yuv_use_case shape: %s',
                      use_case_name, rgb_yuv_use_case.shape)
        image_processing_utils.write_image(
            rgb_yuv_use_case, f'{file_stem}_yuv_{use_case_name}.jpg')

        # Check delta E76
        if first_api_level >= its_session_utils.ANDROID17_API_LEVEL:
          e76_diff = _get_delta_e76(rgb_yuv, rgb_yuv_use_case)
          msg = (f'E76 diff for single {use_case_name} use case & still '
                 f'capture YUV: {e76_diff:.4f}')
          logging.debug('%s', msg)
          marginal_pass_tol_use_case = (
              _THRESHOLD_MAX_E76_DIFF_USE_CASE_ANDROID17 *
              its_session_utils.MARGINAL_PASS_FACTOR
          )
          if e76_diff >= _THRESHOLD_MAX_E76_DIFF_USE_CASE_ANDROID17:
            logging.error('%s, ATOL: %.2f', msg,
                          _THRESHOLD_MAX_E76_DIFF_USE_CASE_ANDROID17)
            num_fail += 1
          else:
            if e76_diff >= marginal_pass_tol_use_case:
              marginal_pass_msg.append(
                  f'Marginal pass for use case {use_case_name}, '
                  f'E76 diff: {e76_diff:.4f}, '
                  f'ATOL: {marginal_pass_tol_use_case:.4f}')

        # Check RMS difference if first API level < 17
        else:
          rms_diff = image_processing_utils.compute_image_rms_difference_3d(
              rgb_yuv, rgb_yuv_use_case)
          msg = (f'RMS diff for single {use_case_name} use case & still '
                 f'capture YUV: {rms_diff:.4f}')
          logging.debug('%s', msg)
          if rms_diff >= _THRESHOLD_MAX_RMS_DIFF_USE_CASE:
            raise AssertionError(
                f'{msg}, ATOL: {_THRESHOLD_MAX_RMS_DIFF_USE_CASE:.4f}')
          marginal_pass_tol = (_THRESHOLD_MAX_RMS_DIFF_USE_CASE *
                               its_session_utils.MARGINAL_PASS_FACTOR)
          if rms_diff >= marginal_pass_tol:
            marginal_pass_msg.append(f'{msg}, ATOL: {marginal_pass_tol:.4f}')

      if marginal_pass_msg:
        for msg in marginal_pass_msg:
          logging.warning('%s\n%s',
                          its_session_utils.MARGINAL_PASSING_MESSAGE, msg)
      if num_fail > 0:
        raise AssertionError(f'Number of fails: {num_fail} / {num_tests}')

if __name__ == '__main__':
  test_runner.main()
