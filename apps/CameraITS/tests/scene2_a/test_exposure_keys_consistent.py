# Copyright 2025 The Android Open Source Project
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
"""Validate that exposure values agree when AE is either on or off."""

import logging
import os.path
import camera_properties_utils
import capture_request_utils
import image_processing_utils
import its_base_test
import its_session_utils
import matplotlib.pyplot as plt
from mobly import test_runner
import numpy as np
from scipy.ndimage import gaussian_filter

_NAME = os.path.splitext(os.path.basename(__file__))[0]
_SAD_THRESHOLD = 0.04 # No block should be >= 4% different
_BLOCK_SIZE = 16
_JPG_FORMAT = 'jpg'


def split_luma_image(luma_img):
  """Split the input image into regions of _BLOCK_SIZE x _BLOCK_SIZE.

  Args:
    luma_img: The input image (luma only)

  Returns:
    A 5-dimensional array. The first two dimensions are the block index,
    and the next two dimensions are the coordinates of each pixel within the
    block.
  """
  width, height, _ = luma_img.shape
  num_blocks_x = int(width / _BLOCK_SIZE)
  num_blocks_y = int(height / _BLOCK_SIZE)
  return (
      luma_img[: _BLOCK_SIZE * num_blocks_x, : _BLOCK_SIZE * num_blocks_y, :]
      .reshape(num_blocks_x, _BLOCK_SIZE, num_blocks_y, _BLOCK_SIZE, 1)
      .swapaxes(1, 2)
  )


def calc_max_avg_exposure_sad(cap_no_ae, cap_ae, name_with_log_path):
  """Calculate the max avg region SAD of the AE capture and the non-AE capture.

  Splits the image into 16x16 luma regions and calculates the sum of absolute
  difference (SAD) for each of them averaged by the number of pixels in the
  region. Returns the maximum of these.

  Args:
    cap_no_ae: Camera capture object without auto exposure.
    cap_ae: Camera capture object with auto exposure.

  Returns:
    The sum of absolute differences for the two images.
  """
  suffix = f'_fmt={_JPG_FORMAT}.{_JPG_FORMAT}'
  img_no_ae = image_processing_utils.decompress_jpeg_to_yuv_image(cap_no_ae)
  image_processing_utils.write_image(
      img_no_ae, f'{name_with_log_path}_no_ae{suffix}', is_yuv=True
  )

  img_ae = image_processing_utils.decompress_jpeg_to_yuv_image(cap_ae)
  image_processing_utils.write_image(
      img_ae, f'{name_with_log_path}{suffix}', is_yuv=True
  )

  luma_no_ae = img_no_ae[:, :, 0:1]
  image_processing_utils.write_image(
      luma_no_ae, f'{name_with_log_path}_luma_no_ae{suffix}'
  )

  luma_ae = img_ae[:, :, 0:1]
  image_processing_utils.write_image(
      luma_ae, f'{name_with_log_path}_luma{suffix}'
  )

  # Blur the captures to remove noise
  luma_no_ae_blur = gaussian_filter(luma_no_ae, sigma=3)
  image_processing_utils.write_image(
      luma_no_ae_blur, f'{name_with_log_path}_luma_no_ae_blur{suffix}'
  )

  luma_ae_blur = gaussian_filter(luma_ae, sigma=3)
  image_processing_utils.write_image(
      luma_ae_blur, f'{name_with_log_path}_luma_blur{suffix}'
  )

  luma_sad = np.abs(np.subtract(luma_no_ae_blur, luma_ae_blur))
  fig, ax = plt.subplots()
  im = ax.imshow(luma_sad, cmap='Reds')
  cbar = fig.colorbar(im, ax=ax)
  cbar.set_label('SAD')
  plt.savefig(f'{name_with_log_path}_sad.png', dpi=300, bbox_inches='tight')

  blocks_ae = split_luma_image(luma_ae_blur)
  blocks_no_ae = split_luma_image(luma_no_ae_blur)

  # Calculate the sum of absolute difference per block, take the average per
  # pixel and return the maximum of these.
  block_avg_sads = np.sum(
      np.abs(np.subtract(blocks_ae, blocks_no_ae)), axis=(2, 3)
  ) / (_BLOCK_SIZE**2)
  max_index = np.argmax(block_avg_sads)
  x, y, _ = np.unravel_index(max_index, block_avg_sads.shape)

  max_block_luma_ae = blocks_ae[x, y, :, :, :]
  max_block_luma_no_ae = blocks_no_ae[x, y, :, :, :]
  image_processing_utils.write_image(
      max_block_luma_ae, f'{name_with_log_path}_max_block{suffix}'
  )
  image_processing_utils.write_image(
      max_block_luma_no_ae, f'{name_with_log_path}_max_block_no_ae{suffix}'
  )

  return block_avg_sads[x, y, 0]


class ExposureKeysConsistentTest(its_base_test.ItsBaseTest):
  """Test for inconsistencies in exposure metadata and the resulting image.

  Uses JPEG captures as the output format.

  Steps:
  - Takes one capture with auto exposure on.
  - Using values the above CaptureResult, applies the following keys to a
  capture
    request with auto exposure off:
        - Sensor sensitivity
        - Post raw sensitivity boost
        - Exposure time
        - Frame duration
  - Validates that the two images are roughly the same via SAD.
  """

  def test_exposure_keys_consistent(self):
    logging.debug('Starting %s', _NAME)

    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id,
    ) as cam:
      props = cam.get_camera_properties()
      props = cam.override_with_hidden_physical_camera_props(props)
      log_path = self.log_path
      name_with_log_path = os.path.join(log_path, _NAME)

      # Load chart for scene
      its_session_utils.load_scene(
          cam, props, self.scene, self.tablet, self.chart_distance
      )

      # Capture for each available reprocess format
      sizes = capture_request_utils.get_available_output_sizes(_JPG_FORMAT, props)

      size = None
      for i in range(len(sizes)):
        if (sizes[i][0] >= _BLOCK_SIZE and sizes[i][1] >= _BLOCK_SIZE):
          size =  sizes[i]
          break

      camera_properties_utils.skip_unless(size is not None)

      logging.info(f'capture width: {size[0]}')
      logging.info(f'capture height: {size[1]}')
      out_surface = {'width': size[0], 'height': size[1], 'format': _JPG_FORMAT}

      # Create req, do caps and determine SAD
      req = capture_request_utils.auto_capture_request()
      req['android.control.aeMode'] = 1  # ON
      cam.do_3a()
      cap_ae = cam.do_capture([req], out_surface)[0]
      cr = cap_ae['metadata']
      sensor_sensitivity = cr['android.sensor.sensitivity']
      exposure_time = cr['android.sensor.exposureTime']
      post_raw_sensitivity_boost = cr['android.control.postRawSensitivityBoost']
      frame_duration = cr['android.sensor.frameDuration']

      logging.info(f'sensor_sensitivity: {sensor_sensitivity}')
      logging.info(f'exposure_time: {exposure_time}')
      logging.info(f'post_raw_sensitivity_boost: {post_raw_sensitivity_boost}')
      logging.info(f'frame_duration: {frame_duration}')

      req['android.control.aeMode'] = 0  # OFF
      req['android.sensor.sensitivity'] = sensor_sensitivity
      req['android.sensor.exposureTime'] = exposure_time
      if post_raw_sensitivity_boost is not None:
        req['android.control.postRawSensitivityBoost'] = (
            post_raw_sensitivity_boost
        )
      req['android.sensor.frameDuration'] = frame_duration
      cap_no_ae = cam.do_capture([req], out_surface)[0]

      sad = calc_max_avg_exposure_sad(
          cap_no_ae['data'], cap_ae['data'], name_with_log_path
      )

      logging.info(f'Max block SAD: {sad} (threshold: {_SAD_THRESHOLD})')
      if sad > _SAD_THRESHOLD:
        raise AssertionError(
            f'Max block SAD greater than threshold: {sad} / {_SAD_THRESHOLD}'
        )


if __name__ == '__main__':
  test_runner.main()
