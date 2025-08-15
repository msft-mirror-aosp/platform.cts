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
"""Verifies android.tonemap.mode parameter is applied correctly for
uniform and non-uniform distribution."""


import logging
import os.path
import numpy as np
from matplotlib import pyplot as plt
from mobly import test_runner

import its_base_test
import camera_properties_utils
import capture_request_utils
import image_processing_utils
import its_session_utils

_BLUE = 'blue'
_BRIGHTNESS_INCREASE_THRESHOLD = 0.08  # 8%
_BRIGHTNESS_DELTA_THRESHOLD = 0.15  # 15%
_EXPONENTIAL_NONUNIFORM_THRESHOLD = 0.01  # 1%
_CONTRAST_CURVE = 0
_CURVE = 'curve'
_DEFAULT = 'default'
_DEFAULT_VALIDATION = 'default_validation'
_EXPONENTIAL = 'exponential'
_FAST = 1
_GREEN = 'green'
_IS_LINEAR_TONEMAP = 'is_linear_tonemap'
_LINEAR = 'linear'
_METADATA = 'metadata'
_MODE = 'mode'
_NAME = os.path.splitext(os.path.basename(__file__))[0]
_NONUNIFORM = 'nonuniform'
_NUM_CAPTURES = 3
_NUM_FRAMES_PER_CAPTURE = 8
_PATCH_H = 0.2  # center 20%
_PATCH_W = 0.2  # center 20%
_PATCH_X = 0.5 - _PATCH_W/2
_PATCH_Y = 0.5 - _PATCH_H/2
_RED = 'red'
_RGB_R_CH = 0
_TONEMAP_CURVE = 'android.tonemap.curve'
_YUV = 'yuv'

_BRIGHTNESS_CHECK = [  # brightness checks between curves
    (_DEFAULT, _LINEAR, _BRIGHTNESS_INCREASE_THRESHOLD, '>='),
    (_DEFAULT, _DEFAULT_VALIDATION, _BRIGHTNESS_DELTA_THRESHOLD,
     '<='),
    (_LINEAR, _EXPONENTIAL, _BRIGHTNESS_INCREASE_THRESHOLD, '>='),
    (_EXPONENTIAL, _NONUNIFORM, _EXPONENTIAL_NONUNIFORM_THRESHOLD,
     '<=')
]

_EXPONENTIAL_CURVE = (
    0.0000, 0.0000, 0.0667, 0.0044, 0.1333, 0.0178, 0.2000, 0.0400,
    0.2667, 0.0711, 0.3333, 0.1111, 0.4000, 0.1600, 0.4667, 0.2178,
    0.5333, 0.2844, 0.6000, 0.3600, 0.6667, 0.4444, 0.7333, 0.5378,
    0.8000, 0.6400, 0.8667, 0.7511, 0.9333, 0.8711, 1.0000, 1.0000
)

# Non-uniform curve is a modified exponential curve characterized
# by having unequally spaced x-coordinates
_NONUNIFORM_CURVE = (
    0.0000, 0.0000, 0.0455, 0.0021, 0.0667, 0.0044, 0.0909, 0.0083,
    0.1364, 0.0186, 0.1818, 0.0331, 0.2273, 0.0517, 0.2727, 0.0744,
    0.3182, 0.1012, 0.3636, 0.1322, 0.4091, 0.1674, 0.4545, 0.2066,
    0.5000, 0.2500, 0.6667, 0.4444, 0.8333, 0.6944, 1.0000, 1.0000
)

_TONEMAP_MODES_CONFIG = {
    _DEFAULT: {_MODE: _FAST, _CURVE: None},
    _DEFAULT_VALIDATION: {_MODE: _CONTRAST_CURVE, _CURVE: None},
    _LINEAR: {_MODE: None, _CURVE: None, _IS_LINEAR_TONEMAP: True},
    _EXPONENTIAL: {_MODE: _CONTRAST_CURVE, _CURVE: _EXPONENTIAL_CURVE},
    _NONUNIFORM: {_MODE: _CONTRAST_CURVE, _CURVE: _NONUNIFORM_CURVE}
}


def _create_tonemap_curve_plot(
    tonemap_data_dict, name_with_log_path):
  """Create plot for tonemap curve.

  Args:
    tonemap_data_dict: Dictionary containing tonemap metadata for various modes.
      Keys are tonemap names (e.g., 'default', 'linear'), values are
      dictionary w/ 'red', 'green', 'blue' keys, each holding the curve data.
    name_with_log_path: file path and name to save tonemap plots in.
  """
  # Prepare a list for looping
  plot_order = [
      (_EXPONENTIAL, 'Exponential Curve Tonemap'),
      (_NONUNIFORM, 'Nonuniform Exponential Curve Tonemap'),
      (_DEFAULT, 'Default Tonemap'),
      (_DEFAULT_VALIDATION, 'Default Validation Tonemap'),
      (_LINEAR, 'Linear Tonemap')]

  plt.clf()  # Clear the current figure
  fig = plt.figure(figsize=(18, 12))  # Layout for 6 subplots
  colors_map = {'red': 'r', 'green': 'g', 'blue': 'b'}
  subplot_index = 1

  for tonemap_type, plot_title in plot_order:
    ax = fig.add_subplot(2, 3, subplot_index)

    # Handle the special case for the input curve
    if tonemap_type == _EXPONENTIAL:
      # Extracting x and y values for exponential curve tonemap
      x_exponential_input, y_exponential_input = _extract_xy(_EXPONENTIAL_CURVE)
      ax.plot(x_exponential_input, y_exponential_input, 'k--',
              label='Exponential input', alpha=0.7)
    if tonemap_type == _NONUNIFORM:
      # Extracting x and y values for exponential, nonuniform curve tonemap
      x_nonuniform_input, y_nonuniform_input = _extract_xy(_NONUNIFORM_CURVE)
      ax.plot(x_nonuniform_input, y_nonuniform_input, 'k--',
              label='Nonuniform input', alpha=0.7)

    metadata_dict = tonemap_data_dict.get(tonemap_type, {})
    for color, color_code in colors_map.items():
      if color in metadata_dict:
        x, y = _extract_xy(metadata_dict[color])
        label = f'{tonemap_type.replace("_", " ").title()} {color} channel'
        ax.plot(x, y, f'-{color_code}.', label=label)
      else:
        logging.warning("'%s' key not found in %s for plotting.",
                        color, tonemap_type)

    ax.set_title(plot_title)
    ax.set_xlabel('Tone map X coordinate')
    ax.set_ylabel('Tone map Y coordinate')
    ax.legend(loc='lower right', numpoints=1, fancybox=True, fontsize=8)
    subplot_index += 1
  plt.tight_layout()
  plt.savefig(f'{name_with_log_path}_plot.png')


def _do_brightness_check(
    patch_1st, patch_2nd, tonemap_1st, tonemap_2nd, threshold,
    comparison_type):
  """Computes brightness difference between two image patches.

  This function compares the red channel brightness of two image patches,
  logging the difference and returning an error message if the difference
  does not meet predefined expectations based on the tonemap modes.

  Args:
    patch_1st: img; patch of first tonemap for comparison.
    patch_2nd: img; patch of second tonemap for comparison.
    tonemap_1st: str; name of the first tonemap.
    tonemap_2nd: str; name of the second tonemap.
    threshold: float; brightness difference threshold.
    comparison_type: str; type of comparison to do ('<=', '>=').

  Returns:
    A tuple containing:
    - e_msg: A string with an error message if a brightness check fails,
              otherwise None.
    - brightness_diff: A float representing the brightness difference.
  """
  e_msg = None

  # Check the R channel brightness as it is most sensitive
  brightness_1st = image_processing_utils.compute_image_means(patch_1st)
  brightness_2nd = image_processing_utils.compute_image_means(patch_2nd)
  brightness_diff = brightness_1st[_RGB_R_CH] - brightness_2nd[_RGB_R_CH]
  logging.debug('Brightness difference between %s and %s: %.2f',
                tonemap_1st, tonemap_2nd, brightness_diff)

  # Check that the brightness difference is smaller than the max threshold
  if comparison_type == '>=':
    if brightness_diff < threshold:
      e_msg = (
          f'The brightness difference between {tonemap_1st} and '
          f'{tonemap_2nd} for greyscale cells did not increase as expected. '
          f'Actual: {brightness_diff:.3f}, '
          f'Expected to be >= {threshold:.2f}')
      logging.debug(e_msg)
  elif comparison_type == '<=':
    if brightness_diff > threshold:
      e_msg = (
          f'The brightness difference between {tonemap_1st} and '
          f'{tonemap_2nd} for greyscale cells exceeds the threshold. '
          f'Actual: {brightness_diff:.3f}, '
          f'Expected: {threshold:.2f}')
  else:
    e_msg = f'Invalid comparison type: {comparison_type}'
    logging.debug(e_msg)
  return e_msg, brightness_diff


def _do_captures_and_extract_patch(
    cam, req, fmt, num_frames_per_cap, tonemap, log_path):
  """Do captures, save image and extract means from center patch.

  Args:
    cam: camera object.
    req: capture request.
    fmt: capture format.
    num_frames_per_cap: int; number of frames per capture
    tonemap: string to determine 'linear' or 'default' tonemap.
    log_path: location to save images.

  Returns:
    A tuple containing:
    - last_cap: The last capture metadata.
    - patch: image patch of center 20% of image.
  """
  last_cap = None
  for i in range(_NUM_CAPTURES):
    cap = cam.do_capture([req]*num_frames_per_cap, fmt)
    last_cap = cap[-1]
    img = image_processing_utils.convert_capture_to_rgb_image(cap[-1])
    patch = image_processing_utils.get_image_patch(
        img, _PATCH_X, _PATCH_Y, _PATCH_W, _PATCH_H)
  image_processing_utils.write_image(
      img, f'{os.path.join(log_path, _NAME)}_{tonemap}.jpg')
  return last_cap, patch


def _extract_xy(data_list):
  """Extract x and y coordinates from a list of x and y coordinates.

  Args:
    data_list: list; of x and y coordinates in the same list.
  Returns:
    x_values: list; of x-coordinates only.
    y_values: list; of y-coordinates only.
  """
  x_values = data_list[0::2]
  y_values = data_list[1::2]
  return x_values, y_values

# TODO: b/438764039 - configure gen2 rig before the test runs
class TonemapSequence(its_base_test.ItsBaseTest):
  """Test tonemap curves with default, linear, exponential & nonuniform pins."""

  def test_tonemap_sequence(self):
    name_with_log_path = os.path.join(self.log_path, _NAME)
    assertion_errors = []

    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:
      props = cam.get_camera_properties()
      props = cam.override_with_hidden_physical_camera_props(props)
      first_api_level = its_session_utils.get_first_api_level(self.dut.serial)
      max_curve_points = props['android.tonemap.maxCurvePoints']
      logging.debug('Max curve points: %s', max_curve_points)

      camera_properties_utils.skip_unless(
          first_api_level >= its_session_utils.ANDROID17_API_LEVEL and
          camera_properties_utils.tonemap_mode(props, _FAST) and
          camera_properties_utils.tonemap_contrast_curve(props) and
          camera_properties_utils.max_curve_points(props)
      )
      log_path = self.log_path

      # Define formats
      largest_yuv = capture_request_utils.get_largest_format('yuv', props)
      match_ar = (largest_yuv['width'], largest_yuv['height'])
      fmt = capture_request_utils.get_near_vga_yuv_format(
          props, match_ar=match_ar)

      # Set params based on per_frame_control & read_3a
      camera_properties_utils.log_minimum_focus_distance(props)
      manual_and_per_frame_control = (
          camera_properties_utils.per_frame_control(props) and
          camera_properties_utils.read_3a(props))

      tonemap_results = {}
      out_surface = {
          'width': fmt['width'], 'height': fmt['height'], 'format': _YUV}
      for tonemap, config in _TONEMAP_MODES_CONFIG.items():
        logging.info('Processing tonemap mode: %s', tonemap)
        num_frames_per_cap = _NUM_FRAMES_PER_CAPTURE  # Default for auto
        use_linear_tonemap = config.get(_IS_LINEAR_TONEMAP, False)

        # Determine the base capture request based on control type
        if manual_and_per_frame_control:
          logging.debug('PER_FRAME_CONTROL supported.')
          num_frames_per_cap = 1
          sens, exp, _, _, f_dist = cam.do_3a(
              do_af=True, get_results=True, out_surfaces=out_surface)
          req = capture_request_utils.manual_capture_request(
              sens, exp, f_dist, use_linear_tonemap, props
              if use_linear_tonemap else None)
        else:
          logging.debug('PER_FRAME_CONTROL not supported.')
          num_frames_per_cap = _NUM_FRAMES_PER_CAPTURE
          cam.do_3a(do_af=True, out_surfaces=out_surface)
          req = capture_request_utils.auto_capture_request(
              linear_tonemap=use_linear_tonemap, props=props
              if use_linear_tonemap else None, do_af=True)

        # Apply specific tonemap mode and curve if defined
        if config[_MODE] is not None:
          req['android.tonemap.mode'] = config[_MODE]
        if config[_CURVE] is not None:
          req[_TONEMAP_CURVE] = {
              _RED: config[_CURVE],
              _GREEN: config[_CURVE],
              _BLUE: config[_CURVE]
          }
        if tonemap == _DEFAULT_VALIDATION:
          req[_TONEMAP_CURVE] = {
              _RED: default_metadata[_RED],
              _GREEN: default_metadata[_GREEN],
              _BLUE: default_metadata[_BLUE]
          }

        cap, patch = (
            _do_captures_and_extract_patch(
                cam, req, fmt, num_frames_per_cap, tonemap, log_path))
        tonemap_results[tonemap] = {
            _METADATA: cap[_METADATA][_TONEMAP_CURVE],
            'patch': patch
        }
        if tonemap == _DEFAULT:
          default_metadata = cap[_METADATA][_TONEMAP_CURVE]

      # create tonemap curve plots
      _create_tonemap_curve_plot(
          {k: v[_METADATA] for k, v in tonemap_results.items()},
          name_with_log_path
      )

      for tm1, tm2, threshold, comparison_type in _BRIGHTNESS_CHECK:
        if tm1 in tonemap_results and tm2 in tonemap_results:
          e_msg, diff = _do_brightness_check(
              tonemap_results[tm1]['patch'], tonemap_results[tm2]['patch'],
              tm1, tm2, threshold, comparison_type
          )
          print(f'{_NAME}_{tm1}_{tm2}_mean_brightness_diff: {diff:.3f}')
          logging.debug('%s_%s_mean_brightness_diff: %.3f', tm1, tm2, diff)
          if e_msg:
            assertion_errors.append(e_msg)
        else:
          error_msg = f'Data not available for {tm1} vs {tm2}.'
          logging.error(error_msg)
          assertion_errors.append(error_msg)

      if assertion_errors:
        raise AssertionError('\n'.join(assertion_errors))

if __name__ == '__main__':
  test_runner.main()
