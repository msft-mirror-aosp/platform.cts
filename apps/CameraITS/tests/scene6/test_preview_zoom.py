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
"""Verify preview zoom ratio scales circle sizes correctly."""

import logging
import os.path
import subprocess

import cv2
from mobly import test_runner

import its_base_test
import camera_properties_utils
import its_session_utils
import preview_processing_utils
import video_processing_utils
import zoom_capture_utils


_CIRCLISH_RTOL = 0.1  # contour area vs ideal circle area pi*((w+h)/4)**2
_CRF = 23
_CV2_RED = (0, 0, 255)  # color (B, G, R) in cv2 to draw lines
_FPS = 30
_MP4V = 'mp4v'
_NAME = os.path.splitext(os.path.basename(__file__))[0]
_NUM_STEPS = 50


# Note: b/284232490: 1080p could be 1088. 480p could be 704 or 640 too.
#       Use for tests not sensitive to variations of 1080p or 480p.
# TODO: b/370841141 - Remove usage of VIDEO_PREVIEW_QUALITY_SIZE.
#                     Create and use get_supported_video_sizes instead of
#                     get_supported_video_qualities.
_VIDEO_PREVIEW_QUALITY_SIZE = {
    # 'HIGH' and 'LOW' not included as they are DUT-dependent
    '4KDC': '4096x2160',
    '2160P': '3840x2160',
    'QHD': '2560x1440',
    '2k': '2048x1080',
    '1080P': '1920x1080',
    '720P': '1280x720',
    '480P': '720x480',
    'VGA': '640x480',
    'CIF': '352x288',
    'QVGA': '320x240',
    'QCIF': '176x144',
}


def get_largest_video_size(cam, camera_id):
  """Returns the largest supported video size and its area.

  Determine largest supported video size and its area from
  get_supported_video_qualities.

  Args:
    cam: camera object.
    camera_id: str; camera ID.

  Returns:
    max_size: str; largest supported video size in the format 'widthxheight'.
    max_area: int; area of the largest supported video size.
  """
  supported_video_qualities = cam.get_supported_video_qualities(camera_id)
  logging.debug('Supported video profiles & IDs: %s',
                supported_video_qualities)

  quality_keys = [
      quality.split(':')[0]
      for quality in supported_video_qualities
  ]
  logging.debug('Quality keys: %s', quality_keys)

  supported_video_sizes = [
      _VIDEO_PREVIEW_QUALITY_SIZE[key]
      for key in quality_keys
      if key in _VIDEO_PREVIEW_QUALITY_SIZE
  ]
  logging.debug('Supported video sizes: %s', supported_video_sizes)

  if not supported_video_sizes:
    raise AssertionError('No supported video sizes found!')

  size_to_area = lambda s: int(s.split('x')[0])*int(s.split('x')[1])
  max_size = max(supported_video_sizes, key=size_to_area)

  logging.debug('Largest video size: %s', max_size)
  return size_to_area(max_size)


def compress_video(input_filename, output_filename, crf=_CRF):
  """Compresses the given video using ffmpeg."""

  ffmpeg_cmd = [
      'ffmpeg',
      '-i', input_filename,   # Input file
      '-c:v', 'libx264',      # Use H.264 codec
      '-crf', str(crf),       # Set Constant Rate Factor (adjust for quality)
      '-preset', 'medium',    # Encoding speed/compression balance
      '-c:a', 'copy',         # Copy audio stream without re-encoding
      output_filename         # Output file
  ]

  with open(os.devnull, 'w') as devnull:
    subprocess.run(ffmpeg_cmd, stdout=devnull,
                   stderr=subprocess.STDOUT, check=False)


class PreviewZoomTest(its_base_test.ItsBaseTest):
  """Verify zoom ratio of preview frames matches values in TotalCaptureResult."""

  def test_preview_zoom(self):
    log_path = self.log_path
    video_processing_utils.log_ffmpeg_version()

    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:

      debug = self.debug_mode

      props = cam.get_camera_properties()
      props = cam.override_with_hidden_physical_camera_props(props)
      camera_properties_utils.skip_unless(
          camera_properties_utils.zoom_ratio_range(props))

      # Load chart for scene
      its_session_utils.load_scene(
          cam, props, self.scene, self.tablet, self.chart_distance)

      # Raise error if not FRONT or REAR facing camera
      camera_properties_utils.check_front_or_rear_camera(props)

      # set TOLs based on camera and test rig params
      if camera_properties_utils.logical_multi_camera(props):
        test_tols, _ = zoom_capture_utils.get_test_tols_and_cap_size(
            cam, props, self.chart_distance, debug)
      else:
        test_tols = {}
        fls = props['android.lens.info.availableFocalLengths']
        for fl in fls:
          test_tols[fl] = (zoom_capture_utils.RADIUS_RTOL,
                           zoom_capture_utils.OFFSET_RTOL)
      logging.debug('Threshold levels to be used for testing: %s', test_tols)

      largest_area = get_largest_video_size(cam, self.camera_id)

      # get max preview size
      preview_size = preview_processing_utils.get_max_preview_test_size(
          cam, self.camera_id, aspect_ratio=None, max_tested_area=largest_area)
      size = [int(x) for x in preview_size.split('x')]
      logging.debug('preview_size = %s', preview_size)
      logging.debug('size = %s', size)

      # Determine test zoom range and step size
      z_range = props['android.control.zoomRatioRange']
      logging.debug('z_range = %s', str(z_range))
      z_min, z_max, z_step_size = zoom_capture_utils.get_preview_zoom_params(
          z_range, _NUM_STEPS)
      camera_properties_utils.skip_unless(
          z_max >= z_min * zoom_capture_utils.ZOOM_MIN_THRESH)

      # recording preview
      capture_results, file_list = (
          preview_processing_utils.preview_over_zoom_range(
              self.dut, cam, preview_size, z_min, z_max, z_step_size, log_path)
      )

      test_data = []
      test_data_index = 0
      # Initialize video writer
      fourcc = cv2.VideoWriter_fourcc(*_MP4V)
      uncompressed_video = os.path.join(log_path,
                                        'output_frames_uncompressed.mp4')
      out = cv2.VideoWriter(uncompressed_video, fourcc, _FPS,
                            (size[0], size[1]))

      for capture_result, img_name in zip(capture_results, file_list):
        z = float(capture_result['android.control.zoomRatio'])
        if camera_properties_utils.logical_multi_camera(props):
          phy_id = capture_result['android.logicalMultiCamera.activePhysicalId']
        else:
          phy_id = None

        # read image
        img_bgr = cv2.imread(os.path.join(log_path, img_name))

        # add path to image name
        img_path = f'{os.path.join(self.log_path, img_name)}'

        # determine radius tolerance of capture
        cap_fl = capture_result['android.lens.focalLength']
        radius_tol, offset_tol = test_tols.get(
            cap_fl,
            (zoom_capture_utils.RADIUS_RTOL, zoom_capture_utils.OFFSET_RTOL)
        )

        # Scale circlish RTOL for low zoom ratios
        if z < 1:
          circlish_rtol = _CIRCLISH_RTOL / z
        else:
          circlish_rtol = _CIRCLISH_RTOL

        # Find the center circle in img and check if it's cropped
        circle = zoom_capture_utils.find_center_circle(
            img_bgr, img_path, size, z, z_min, circlish_rtol=circlish_rtol,
            debug=debug, draw_color=_CV2_RED, write_img=False)

        # Zoom is too large to find center circle
        if circle is None:
          logging.error('Unable to detect circle in %s', img_path)
          break

        out.write(img_bgr)
        # Remove png file
        its_session_utils.remove_file(img_path)

        test_data.append(
            zoom_capture_utils.ZoomTestData(
                result_zoom=z,
                circle=circle,
                radius_tol=radius_tol,
                offset_tol=offset_tol,
                focal_length=cap_fl,
                physical_id=phy_id
            )
        )

        logging.debug('test_data[%d] = %s', test_data_index,
                      test_data[test_data_index])
        test_data_index = test_data_index + 1

      out.release()

      # --- Compress Video ---
      compressed_video = os.path.join(log_path, 'output_frames.mp4')
      compress_video(uncompressed_video, compressed_video)

      os.remove(uncompressed_video)

      plot_name_stem = f'{os.path.join(log_path, _NAME)}'
      if not zoom_capture_utils.verify_preview_zoom_results(
          test_data, size, z_max, z_min, z_step_size, plot_name_stem):
        first_api_level = its_session_utils.get_first_api_level(self.dut.serial)
        failure_msg = f'{_NAME} failed! Check test_log.DEBUG for errors'
        if first_api_level >= its_session_utils.ANDROID15_API_LEVEL:
          raise AssertionError(failure_msg)
        else:
          raise AssertionError(f'{its_session_utils.NOT_YET_MANDATED_MESSAGE}'
                               f'\n\n{failure_msg}')

if __name__ == '__main__':
  test_runner.main()
