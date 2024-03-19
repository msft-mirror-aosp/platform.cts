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
"""Verifies JPEG still capture images are correct in the complex scene."""


import logging
import os.path

from mobly import test_runner
import numpy as np

import its_base_test
import camera_properties_utils
import capture_request_utils
import image_processing_utils
import its_session_utils


_NAME = os.path.splitext(os.path.basename(__file__))[0]
_NUM_STEPS = 8
_ZOOM_RATIO_MAX = 8  # too high zoom ratios will eventualy reduce entropy
_ZOOM_RATIO_MIN = 1  # low zoom ratios don't fill up FoV
_ZOOM_RATIO_THRESH = 2  # some zoom ratio needed to fill up FoV


class JpegHighEntropyTest(its_base_test.ItsBaseTest):
  """Tests JPEG still capture with a complex scene.

  Steps zoom ratio to ensure the complex scene fills the camera FoV.
  """

  def test_jpeg_high_entropy(self):
    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:
      props = cam.get_camera_properties()
      props = cam.override_with_hidden_physical_camera_props(props)

      # Load chart for scene
      its_session_utils.load_scene(
          cam, props, self.scene, self.tablet,
          its_session_utils.CHART_DISTANCE_NO_SCALING)

      # Check skip conditions
      camera_properties_utils.skip_unless(
          camera_properties_utils.zoom_ratio_range(props))

      # Determine test zoom range
      zoom_max = float(props['android.control.zoomRatioRange'][1])  # max value
      logging.debug('Zoom max value: %.2f', zoom_max)
      if zoom_max < _ZOOM_RATIO_THRESH:
        raise AssertionError(f'Maximum zoom ratio < {_ZOOM_RATIO_THRESH}x')
      zoom_max = min(zoom_max, _ZOOM_RATIO_MAX)
      zoom_ratios = np.arange(
          _ZOOM_RATIO_MIN, zoom_max,
          (zoom_max - _ZOOM_RATIO_MIN) / (_NUM_STEPS - 1))
      zoom_ratios = np.append(zoom_ratios, zoom_max)
      logging.debug('Testing zoom range: %s', zoom_ratios)

      # Do captures over zoom range
      req = capture_request_utils.auto_capture_request()
      for zoom_ratio in zoom_ratios:
        req['android.control.zoomRatio'] = zoom_ratio
        logging.debug('zoom ratio: %.3f', zoom_ratio)
        cam.do_3a(zoom_ratio=zoom_ratio)
        cap = cam.do_capture(req, cam.CAP_JPEG)

        # save JPEG image
        img = image_processing_utils.convert_capture_to_rgb_image(
            cap, props=props)
        img_name = os.path.join(self.log_path, _NAME)
        image_processing_utils.write_image(
            img, f'{img_name}_{round(zoom_ratio, 2)}.jpg')
        img = image_processing_utils.convert_image_to_uint8(img)
        # TODO: b/310805430 - add spoofing checks

if __name__ == '__main__':
  test_runner.main()
