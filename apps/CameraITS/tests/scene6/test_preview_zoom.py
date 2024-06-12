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
import math
import os.path

import cv2
from mobly import test_runner

import its_base_test
import camera_properties_utils
import its_session_utils
import preview_processing_utils
import video_processing_utils
import zoom_capture_utils


_CIRCLISH_RTOL = 0.05  # contour area vs ideal circle area pi*((w+h)/4)**2
_CV2_RED = (0, 0, 255)  # color (B, G, R) in cv2 to draw lines
_FLOAT_TOL = 0.01
_JPEG = '.jpg'
_NAME = os.path.splitext(os.path.basename(__file__))[0]
_NUM_STEPS = 100  # TODO: b/332322632 - improve test runtime


def save_image_as_jpg(img_name, img_bgr, quality=85):
  """Saves an image as a JPEG with specified quality.

  Args:
      img_name: string; filename (with or without extension).
      img_bgr: numpy.ndarray; image data in BGR format.
      quality: int; JPEG quality (0-100, higher is better).
  """

  base_name, _ = os.path.splitext(img_name)  # Remove existing extension
  new_img_name = base_name + _JPEG

  _, encoded_img = cv2.imencode(_JPEG, img_bgr,
                                [cv2.IMWRITE_JPEG_QUALITY, quality])
  logging.debug('save image: %s', new_img_name)

  with open(new_img_name, 'wb') as f:
    f.write(encoded_img)


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

      # get max preview size
      preview_size = preview_processing_utils.get_max_preview_test_size(
          cam, self.camera_id)
      size = [int(x) for x in preview_size.split('x')]
      logging.debug('preview_size = %s', preview_size)
      logging.debug('size = %s', size)

      # Determine test zoom range and step size
      z_range = props['android.control.zoomRatioRange']
      logging.debug('z_range = %s', str(z_range))
      z_min, z_max, z_step_size = zoom_capture_utils.get_zoom_params(
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
      last_circle_radius = 0
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

        if not math.isclose(last_circle_radius, circle[2], rel_tol=_FLOAT_TOL):
          save_image_as_jpg(img_path, img_bgr)
          last_circle_radius = circle[2]

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

      if not zoom_capture_utils.verify_preview_zoom_results(
          test_data, size, z_max, z_min, z_step_size):
        raise AssertionError(f'{_NAME} failed! Check test_log.DEBUG for errors')

if __name__ == '__main__':
  test_runner.main()
