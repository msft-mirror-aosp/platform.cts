# Copyright 2014 The Android Open Source Project
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
"""Verifies RAW and YUV images are similar."""


import logging
import os.path
from mobly import test_runner

import numpy as np

import its_base_test
import camera_properties_utils
import capture_request_utils
import image_processing_utils
import its_session_utils

_MAX_IMG_SIZE = (1920, 1080)
_NAME = os.path.splitext(os.path.basename(__file__))[0]
_NUM_RAW_CHANNELS = 4  # r, gr, gb, b
_PATCH_H = 0.1  # center 10%
_PATCH_W = 0.1
_PATCH_X = 0.5 - _PATCH_W/2
_PATCH_Y = 0.5 - _PATCH_H/2
_THRESHOLD_MAX_RMS_DIFF = 0.035


def apply_lens_shading_map(color_plane, black_level, white_level, lsc_map):
  """Apply the lens shading map to the color plane.

  Args:
    color_plane: 2D np array for color plane with values [0.0, 1.0].
    black_level: float; black level for the color plane.
    white_level: int; full scale for the color plane.
    lsc_map: 2D np array lens shading matching size of color_plane.

  Returns:
    color_plane with lsc applied.
  """
  logging.debug('color plane pre-lsc min, max: %.4f, %.4f',
                np.min(color_plane), np.max(color_plane))
  color_plane = (np.multiply((color_plane * white_level - black_level),
                             lsc_map)
                 + black_level) / white_level
  logging.debug('color plane post-lsc min, max: %.4f, %.4f',
                np.min(color_plane), np.max(color_plane))
  return color_plane


def populate_lens_shading_map(img_shape, lsc_map):
  """Helper function to create LSC coeifficients for RAW image.

  Args:
    img_shape: tuple; RAW image shape.
    lsc_map: 2D low resolution array with lens shading map values.

  Returns:
    value for lens shading map at point (x, y) in the image.
  """
  img_w, img_h = img_shape[1], img_shape[0]
  map_w, map_h = lsc_map.shape[1], lsc_map.shape[0]

  x, y = np.meshgrid(np.arange(img_w), np.arange(img_h))

  # (u,v) is lsc map location, values [0, map_w-1], [0, map_h-1]
  # Vectorized calculations
  u = x * (map_w - 1) / (img_w - 1)
  v = y * (map_h - 1) / (img_h - 1)
  u_min = np.floor(u).astype(int)
  v_min = np.floor(v).astype(int)
  u_frac = u - u_min
  v_frac = v - v_min
  u_max = np.where(u_frac > 0, u_min + 1, u_min)
  v_max = np.where(v_frac > 0, v_min + 1, v_min)

  # Gather LSC values, handling edge cases (optional)
  lsc_tl = lsc_map[(v_min, u_min)]
  lsc_tr = lsc_map[(v_min, u_max)]
  lsc_bl = lsc_map[(v_max, u_min)]
  lsc_br = lsc_map[(v_max, u_max)]

  # Bilinear interpolation (vectorized)
  lsc_t = lsc_tl * (1 - u_frac) + lsc_tr * u_frac
  lsc_b = lsc_bl * (1 - u_frac) + lsc_br * u_frac

  return lsc_t * (1 - v_frac) + lsc_b * v_frac


def unpack_lsc_map_from_metadata(metadata):
  """Get lens shading correction map from metadata and turn into 3D array.

  Args:
    metadata: dict; metadata from RAW capture.

  Returns:
    3D numpy array of lens shading maps.
  """
  lsc_metadata = metadata['android.statistics.lensShadingCorrectionMap']
  lsc_map_w, lsc_map_h = lsc_metadata['width'], lsc_metadata['height']
  lsc_map = lsc_metadata['map']
  logging.debug(
      'lensShadingCorrectionMap (H, W): (%d, %d)', lsc_map_h, lsc_map_w
  )
  return np.array(lsc_map).reshape(lsc_map_h, lsc_map_w, _NUM_RAW_CHANNELS)


def convert_and_compare_captures(cap_raw, cap_yuv, props,
                                 log_path_with_name, raw_fmt):
  """Helper function to convert and compare RAW and YUV captures.

  Args:
   cap_raw: capture request object with RAW/RAW10/RAW12 format specified
   cap_yuv: capture capture request object with YUV format specified
   props: object from its_session_utils.get_camera_properties().
   log_path_with_name: logging path where artifacts should be stored.
   raw_fmt: string 'raw', 'raw10', or 'raw12' to include in file name

  Returns:
    string "PASS" if test passed, else message for AssertionError.
  """
  shading_mode = cap_raw['metadata']['android.shading.mode']
  lens_shading_applied = props['android.sensor.info.lensShadingApplied']
  control_af_mode = cap_raw['metadata']['android.control.afMode']
  focus_distance = cap_raw['metadata']['android.lens.focusDistance']
  logging.debug('%s capture AF mode: %s', raw_fmt, control_af_mode)
  logging.debug('%s capture focus distance: %s', raw_fmt, focus_distance)
  logging.debug('%s capture shading mode: %d', raw_fmt, shading_mode)
  logging.debug('lensShadingMapApplied: %r', lens_shading_applied)

  # YUV
  img = image_processing_utils.convert_capture_to_rgb_image(cap_yuv)
  image_processing_utils.write_image(
      img, f'{log_path_with_name}_shading={shading_mode}_yuv.jpg', True)
  patch = image_processing_utils.get_image_patch(
      img, _PATCH_X, _PATCH_Y, _PATCH_W, _PATCH_H)
  rgb_means_yuv = image_processing_utils.compute_image_means(patch)
  logging.debug('%s YUV RGB means: %s', raw_fmt, str(rgb_means_yuv))

  # RAW
  # Split RAW to RGB conversion in 2 to allow LSC application (if needed).
  r, gr, gb, b = image_processing_utils.convert_capture_to_planes(
      cap_raw, props=props
  )
  if not lens_shading_applied:  # get from metadata, upsample, and apply
    plot_name_stem_with_log_path = f'{log_path_with_name}_{raw_fmt}'
    black_levels = image_processing_utils.get_black_levels(props, cap_raw)
    white_level = int(props['android.sensor.info.whiteLevel'])
    lsc_maps = unpack_lsc_map_from_metadata(cap_raw['metadata'])
    image_processing_utils.plot_lsc_maps(
        lsc_maps, 'metadata', plot_name_stem_with_log_path
    )
    lsc_map_fs_r = populate_lens_shading_map(r.shape, lsc_maps[:, :, 0])
    lsc_map_fs_gr = populate_lens_shading_map(r.shape, lsc_maps[:, :, 1])
    lsc_map_fs_gb = populate_lens_shading_map(r.shape, lsc_maps[:, :, 2])
    lsc_map_fs_b = populate_lens_shading_map(r.shape, lsc_maps[:, :, 3])
    image_processing_utils.plot_lsc_maps(
        np.dstack((lsc_map_fs_r, lsc_map_fs_gr, lsc_map_fs_gb, lsc_map_fs_b)),
        'fullscale', plot_name_stem_with_log_path
    )
    r = apply_lens_shading_map(
        r[:, :, 0], black_levels[0], white_level, lsc_map_fs_r
    )
    gr = apply_lens_shading_map(
        gr[:, :, 0], black_levels[1], white_level, lsc_map_fs_gr
    )
    gb = apply_lens_shading_map(
        gb[:, :, 0], black_levels[2], white_level, lsc_map_fs_gb
    )
    b = apply_lens_shading_map(
        b[:, :, 0], black_levels[3], white_level, lsc_map_fs_b
    )
  img = image_processing_utils.convert_raw_to_rgb_image(
      r, gr, gb, b, props, cap_raw['metadata']
  )
  image_processing_utils.write_image(
      img, f'{log_path_with_name}_shading={shading_mode}_{raw_fmt}.jpg', True)

  # Shots are 1/2 x 1/2 smaller after conversion to RGB, but patch
  # cropping is relative.
  patch = image_processing_utils.get_image_patch(
      img, _PATCH_X, _PATCH_Y, _PATCH_W, _PATCH_H)
  rgb_means_raw = image_processing_utils.compute_image_means(patch)
  logging.debug('%s RAW RGB means: %s', raw_fmt, str(rgb_means_raw))

  rms_diff = image_processing_utils.compute_image_rms_difference_1d(
      rgb_means_yuv, rgb_means_raw)
  msg = f'{raw_fmt} diff: {rms_diff:.4f}'
  # Log rms-diff, so that it can be written to the report log.
  print(f'test_yuv_plus_{raw_fmt}_rms_diff: {rms_diff:.4f}')
  logging.debug('%s', msg)
  if rms_diff >= _THRESHOLD_MAX_RMS_DIFF:
    return f'{msg}, spec: {_THRESHOLD_MAX_RMS_DIFF}'
  else:
    return 'PASS'


class YuvPlusRawTest(its_base_test.ItsBaseTest):
  """Test capturing a single frame as both YUV and various RAW formats.

  Tests RAW, RAW10 and RAW12 as available.
  """

  def test_yuv_plus_raw(self):
    failure_messages = []
    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:
      props = cam.get_camera_properties()
      props = cam.override_with_hidden_physical_camera_props(props)
      log_path = os.path.join(self.log_path, _NAME)

      # check SKIP conditions
      camera_properties_utils.skip_unless(
          camera_properties_utils.raw_output(props) and
          camera_properties_utils.linear_tonemap(props) and
          not camera_properties_utils.mono_camera(props))

      # Load chart for scene
      its_session_utils.load_scene(
          cam, props, self.scene, self.tablet,
          its_session_utils.CHART_DISTANCE_NO_SCALING)

      # determine compatible RAW formats
      raw_formats = []
      if camera_properties_utils.raw16(props):
        raw_formats.append('raw')
      else:
        logging.debug('Skipping test_yuv_plus_raw')
      if camera_properties_utils.raw10(props):
        raw_formats.append('raw10')
      else:
        logging.debug('Skipping test_yuv_plus_raw10')
      if camera_properties_utils.raw12(props):
        raw_formats.append('raw12')
      else:
        logging.debug('Skipping test_yuv_plus_raw12')

      for raw_fmt in raw_formats:
        req = capture_request_utils.auto_capture_request(
            linear_tonemap=True, props=props, do_af=False)
        max_raw_size = capture_request_utils.get_available_output_sizes(
            raw_fmt, props)[0]
        if capture_request_utils.is_common_aspect_ratio(max_raw_size):
          w, h = capture_request_utils.get_available_output_sizes(
              'yuv', props, _MAX_IMG_SIZE, max_raw_size)[0]
        else:
          w, h = capture_request_utils.get_available_output_sizes(
              'yuv', props, max_size=_MAX_IMG_SIZE)[0]
        out_surfaces = [{'format': raw_fmt},
                        {'format': 'yuv', 'width': w, 'height': h}]
        cam.do_3a(do_af=False)
        cap_raw, cap_yuv = cam.do_capture(req, out_surfaces)
        msg = convert_and_compare_captures(cap_raw, cap_yuv, props,
                                           log_path, raw_fmt)
        if msg != 'PASS':
          failure_messages.append(msg)

      if failure_messages:
        raise AssertionError('\n'.join(failure_messages))


if __name__ == '__main__':
  test_runner.main()

