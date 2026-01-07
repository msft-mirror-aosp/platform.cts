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
"""Utility functions for Default camera app and JCA image Parity metrics."""

import logging
import math

import camera_properties_utils
import cv2
import numpy as np

_DYNAMIC_PATCH_MID_TONE_START_IDX = 5
_DYNAMIC_PATCH_MID_TONE_END_IDX = 15
AR_REL_TOL = 0.1
EXPECTED_BRIGHTNESS_50 = 50.0
# Exact values are TBD. This is a placeholder value.
MAX_DELTA_AB_ABSOLUTE_ERROR = 10.0
MAX_DELTA_E76_ABSOLUTE_ERROR = 15.0
MAX_CELL_DELTA_AB_ABSOLUTE_ERROR = 2.0
MAX_AVG_RELATIVE_AB_TOL = 5
MAX_BRIGHTNESS_DIFF_ABSOLUTE_ERROR = 10.0
MAX_BRIGHTNESS_DIFF_RELATIVE_ERROR = 8.0
MAX_DELTA_AB_WHITE_BALANCE_ABSOLUTE_ERROR = 6.0
MAX_DELTA_AB_WHITE_BALANCE_RELATIVE_ERROR = 3.0
# This is the height of center QR code on feature chart in cm
CENTER_QR_CODE_CM = 5
FOV_REL_TOL = 0.1
# Reference values for A and B channels
# https://babelcolor.com/index_htm_files/RGB%20Coordinates%20of%20the%20Macbeth%20ColorChecker.pdf
MCC_AB_VALUES = (
    (13.5, 14.06),
    (18.13, 17.81),
    (-4.88, -21.93),
    (-13.10, 21.91),
    (8.84, -25.40),
    (-33.40, -0.199),
    (36.07, 57.10),
    (10.41, -45.96),
    (48.24, 16.25),
    (22.98, -21.59),
    (-23.71, 57.26),
    (19.36, 67.86),
    (14.18, -50.30),
    (-38.34, 31.37),
    (53.38, 28.19),
    (4.04, 79.82),
    (49.99, -14.57),
    (-28.63, -28.64),
)


def check_if_qr_code_size_match(img1, img2):
  """Checks if the size of two images are the same or not.

  Args:
    img1: first image array in BGRA format
    img2: second image array in BGRA format
  Returns:
    True if the size of two images are the same, False otherwise
  """
  # Extract the alpha channel
  alpha_channel_1 = img1[:, :, 3]
  alpha_channel_2 = img2[:, :, 3]

  # Find the non-zero (non-transparent) pixels
  y1_indices, x1_indices = np.where(alpha_channel_1 != 0)
  y2_indices, x2_indices = np.where(alpha_channel_2 != 0)

  # Get the bounding box of the non-transparent region
  min_x1 = np.min(x1_indices)
  min_y1 = np.min(y1_indices)
  max_x1 = np.max(x1_indices)
  max_y1 = np.max(y1_indices)

  min_x2 = np.min(x2_indices)
  min_y2 = np.min(y2_indices)
  max_x2 = np.max(x2_indices)
  max_y2 = np.max(y2_indices)

  # Crop the image to the bounding box
  non_tranpsarent_patch_1 = img1[min_y1:max_y1 + 1, min_x1:max_x1 + 1]
  non_tranpsarent_patch_2 = img2[min_y2:max_y2 + 1, min_x2:max_x2 + 1]

  height1, width1 = non_tranpsarent_patch_1.shape[:2]
  logging.debug('Height 1: %s, Width 1: %s', height1, width1)
  ar_1 = width1 / height1
  logging.debug('Aspect ratio 1: %.2f', ar_1)
  if not math.isclose(ar_1, 1, rel_tol=AR_REL_TOL):
    raise ValueError(
        'Aspect ratio of the non-transparent region of the image 1 is not 1:1.'
    )
  height2, width2 = non_tranpsarent_patch_2.shape[:2]
  logging.debug('Height 2: %s, Width 2: %s', height2, width2)
  ar_2 = width2 / height2
  logging.debug('Aspect ratio 2: %.2f', ar_2)
  if not math.isclose(ar_2, 1, rel_tol=AR_REL_TOL):
    raise ValueError(
        'Aspect ratio of the non-transparent region of the image 2 is not 1:1.'
    )
  return math.isclose(height1, height2, rel_tol=AR_REL_TOL)


def get_lab_mean_values(img):
  """Computes the mean values of the 'L', 'A', and 'B' channels.

  Converts the img from RGB to CIELAB color space and calculates the mean values
  of L, A and B channels only for the non-transparent regions of the image

  Args:
    img: img array in RGB colorspace.
  Returns:
    mean_l, mean_a, mean_b: mean value of l, a, b channels
  """
  img_lab = cv2.cvtColor(img, cv2.COLOR_RGB2LAB)
  img_lab = img_lab.astype(np.uint32)
  mean_l = np.mean(img_lab[:, :, 0]) * 100 / 255
  mean_a = np.mean(img_lab[:, :, 1]) - 128
  mean_b = np.mean(img_lab[:, :, 2]) - 128
  logging.debug('L, A, B values: %.2f %.2f %.2f', mean_l, mean_a, mean_b)
  return mean_l, mean_a, mean_b


def get_brightness_variation(
    default_brightness_values, jca_brightness_values
):
  """Gets the brightness variation between default and jca color cells.

  Args:
    default_brightness_values: The default brightness values of the greyscale
      cells
    jca_brightness_values: The jca brightness values of the greyscale cells

  Returns:
    mean_delta_ab_diff: mean delta ab diff between default and jca rounded
      up to 2 places
  """
  default_brightness = np.mean(default_brightness_values)
  jca_brightness = np.mean(jca_brightness_values)

  default_ref_brightness_diff = default_brightness - EXPECTED_BRIGHTNESS_50
  jca_ref_brightness_diff = jca_brightness - EXPECTED_BRIGHTNESS_50
  default_jca_brightness_diff = jca_brightness - default_brightness
  logging.debug('default_ref_brightness_diff: %.2f',
                default_ref_brightness_diff)
  logging.debug('jca_ref_brightness_diff: %.2f',
                jca_ref_brightness_diff)
  logging.debug('default_jca_brightness_diff: %.2f',
                default_jca_brightness_diff)

  # Check that the brightness difference default and jca to the reference do not
  # exceed the max absolute error
  if (default_ref_brightness_diff > MAX_BRIGHTNESS_DIFF_ABSOLUTE_ERROR) or (
      jca_ref_brightness_diff > MAX_BRIGHTNESS_DIFF_ABSOLUTE_ERROR
  ):
    e_msg = (
        f'The brightness of default and jca for greyscale cells exceeds the'
        f' threshold. Actual default: {default_ref_brightness_diff:.2f}, Actual'
        f' jca: {default_jca_brightness_diff:.2f}, Expected:'
        f' {MAX_BRIGHTNESS_DIFF_ABSOLUTE_ERROR:.1f}'
    )
    logging.debug(e_msg)
  # Check that the brightness between default and jca does not exceed the
  # max relative error
  if (default_jca_brightness_diff > MAX_BRIGHTNESS_DIFF_RELATIVE_ERROR):
    e_msg = (
        f'The brightness difference between default and jca for greyscale cells'
        f' exceeds the threshold. Actual: {default_jca_brightness_diff:.2f}, '
        f'Expected: {MAX_BRIGHTNESS_DIFF_RELATIVE_ERROR:.1f}'
    )
    logging.debug(e_msg)
  return default_jca_brightness_diff


def do_brightness_check(default_patch_list, jca_patch_list):
  """Computes brightness diff between default and jca capture images.

  Args:
    default_patch_list: default camera dynamic range patch cells
    jca_patch_list: jca camera dynamic range patch cells

  Returns:
    mean_brightness_diff: mean brightness diff between default and jca
  """
  default_brightness_values = []
  for patch in default_patch_list:
    mean_l, _, _ = get_lab_mean_values(patch)
    default_brightness_values.append(mean_l)
  jca_brightness_values = []
  for patch in jca_patch_list:
    mean_l, _, _ = get_lab_mean_values(patch)
    jca_brightness_values.append(mean_l)

  default_rounded_values = [round(float(x), 2)
                            for x in default_brightness_values]
  jca_rounded_values = [round(float(x), 2) for x in jca_brightness_values]

  logging.debug('default_brightness_values: %s', default_rounded_values)
  logging.debug('jca_brightness_values: %s', jca_rounded_values)

  mean_brightness_diff = get_brightness_variation(
      default_brightness_values[
          _DYNAMIC_PATCH_MID_TONE_START_IDX:_DYNAMIC_PATCH_MID_TONE_END_IDX
      ],
      jca_brightness_values[
          _DYNAMIC_PATCH_MID_TONE_START_IDX:_DYNAMIC_PATCH_MID_TONE_END_IDX
      ],
  )
  logging.debug(
      'Brightness difference between default and jca: %.2f',
      mean_brightness_diff,
  )
  return round(float(mean_brightness_diff), 2)


def get_neutral_delta_ab(greyscale_cells):
  """Returns the delta ab value for grey scale cells compared to reference.

  Args:
    greyscale_cells: list of grey scale cells

  Returns:
    neutral_delta_ab_values: list of neutral delta ab values for each color cell
  """
  neutral_delta_ab_values = []
  for i, greyscale_cell in enumerate(greyscale_cells):
    _, mean_a, mean_b = get_lab_mean_values(greyscale_cell)
    neutral_delta_ab = np.sqrt(mean_a**2 + mean_b**2)
    logging.debug(
        'Reference delta AB value for greyscale cell %d: %.2f',
        i + 1,
        neutral_delta_ab,
    )
    neutral_delta_ab_values.append(neutral_delta_ab)
  return neutral_delta_ab_values


def get_delta_ab(color_cells_1, color_cells_2):
  """Computes the delta ab value between two color cells.

  Args:
    color_cells_1: first color cells array
    color_cells_2: second color cells array

  Returns:
    delta_ab_values: list of delta ab values for each color cell
  """
  delta_ab_values = []
  for i, (color_cell_1, color_cell_2) in enumerate(
      zip(color_cells_1, color_cells_2)
  ):
    _, mean_a_1, mean_b_1 = get_lab_mean_values(color_cell_1)
    _, mean_a_2, mean_b_2 = get_lab_mean_values(color_cell_2)
    delta_ab = np.sqrt((mean_a_1 - mean_a_2) ** 2 + (mean_b_1 - mean_b_2) ** 2)
    logging.debug('Delta AB value for color cell %d: %.2f', i + 1, delta_ab)
    delta_ab_values.append(delta_ab)
  return delta_ab_values


def get_white_balance_variation(
    greyscale_cells_1, greyscale_cells_2, suffix_1, suffix_2):
  """Computes the white balance variation between two sets of color cells.

  Args:
    greyscale_cells_1: A list of greyscale cells from the first image set.
    greyscale_cells_2: A list of greyscale cells from the second image set.
    suffix_1: The identifier for the first set (e.g., 'default').
    suffix_2: The identifier for the second set (e.g., 'jca').

  Returns:
    The mean delta E difference in the a*b* plane between the two sets.
  """
  neutral_delta_ab_1 = np.mean(
      get_neutral_delta_ab(greyscale_cells_1)
  )
  neutral_delta_ab_2 = np.mean(get_neutral_delta_ab(greyscale_cells_2))
  mean_delta_ab_diff = np.mean(
      get_delta_ab(greyscale_cells_1, greyscale_cells_2)
  )

  logging.debug('%s_neutral_delta_ab: %.2f', suffix_1, neutral_delta_ab_1)
  logging.debug('%s_neutral_delta_ab: %.2f', suffix_2, neutral_delta_ab_2)
  logging.debug(
      '%s_%s_neutral_delta_ab: %.2f', suffix_1, suffix_2, mean_delta_ab_diff
  )

  # Check that the white balance for each set does not exceed the max absolute error.
  if (neutral_delta_ab_1 > MAX_DELTA_AB_WHITE_BALANCE_ABSOLUTE_ERROR) or (
      neutral_delta_ab_2 > MAX_DELTA_AB_WHITE_BALANCE_ABSOLUTE_ERROR
  ):
    logging.debug(
        'White balance of %s and/or %s images exceeds the absolute threshold. '
        'Actual %s value: %.2f, Actual %s value: %.2f, Expected max: %.1f',
        suffix_1,
        suffix_2,
        suffix_1,
        neutral_delta_ab_1,
        suffix_2,
        neutral_delta_ab_2,
        MAX_DELTA_AB_WHITE_BALANCE_ABSOLUTE_ERROR,
    )

  # Check that the white balance between the two sets does not exceed the max relative error.
  if mean_delta_ab_diff > MAX_DELTA_AB_WHITE_BALANCE_RELATIVE_ERROR:
    logging.debug(
        'White balance between %s and %s exceeds the relative threshold. '
        'Actual difference: %.2f, Expected max: %.1f',
        suffix_1,
        suffix_2,
        mean_delta_ab_diff,
        MAX_DELTA_AB_WHITE_BALANCE_RELATIVE_ERROR,
    )
  return mean_delta_ab_diff


def _get_ab_values_from_patches(patch_list):
  """Extracts and rounds a* and b* values from middle tone patches.

  Args:
    patch_list: A list of dynamic range patch cells.

  Returns:
    A tuple containing the list of middle tone patches, the rounded a* values,
    and the rounded b* values.
  """
  a_values = []
  b_values = []
  middle_tone_patch_list = patch_list[
      _DYNAMIC_PATCH_MID_TONE_START_IDX:_DYNAMIC_PATCH_MID_TONE_END_IDX
  ]
  for patch in middle_tone_patch_list:
    _, mean_a, mean_b = get_lab_mean_values(patch)
    a_values.append(mean_a)
    b_values.append(mean_b)

  rounded_a = [round(float(x), 2) for x in a_values]
  rounded_b = [round(float(x), 2) for x in b_values]
  return middle_tone_patch_list, rounded_a, rounded_b


def do_white_balance_check(
    first_patch_list, second_patch_list, suffix1, suffix2):
  """Computes white balance diff between two sets of image patches.

  Args:
    first_patch_list: Camera dynamic range patch cells for the first image.
    second_patch_list: Camera dynamic range patch cells for the second image.
    suffix1: String identifier for the first set of patches (e.g., 'default').
    suffix2: String identifier for the second set of patches (e.g., 'jca').

  Returns:
    The mean neutral delta ab between the two sets, rounded to 2 places.
  """
  first_middle_patches, first_a, first_b = _get_ab_values_from_patches(
      first_patch_list)
  second_middle_patches, second_a, second_b = _get_ab_values_from_patches(
      second_patch_list)

  logging.debug('%s_rounded_a_values: %s', suffix1, first_a)
  logging.debug('%s_rounded_b_values: %s', suffix1, first_b)
  logging.debug('%s_rounded_a_values: %s', suffix2, second_a)
  logging.debug('%s_rounded_b_values: %s', suffix2, second_b)

  mean_neutral_delta_ab = get_white_balance_variation(
      first_middle_patches, second_middle_patches, suffix1, suffix2)
  logging.debug(
      'White balance difference between %s and %s: %.2f',
      suffix1, suffix2, mean_neutral_delta_ab)
  return round(float(mean_neutral_delta_ab), 2)


def _get_non_transparent_pixels(img):
  """Returns the non transparent pixels from BGRA image.
  """
  alpha_channel = img[:, :, 3]

  # Find the non-zero (non-transparent) pixels
  y_indices, x_indices = np.where(alpha_channel != 0)

  # Get the bounding box of the non-transparent region
  min_x = np.min(x_indices)
  min_y = np.min(y_indices)
  max_x = np.max(x_indices)
  max_y = np.max(y_indices)

  # Crop the image to the bounding box
  non_tranpsarent_patch = img[min_y:max_y + 1, min_x:max_x + 1]
  return non_tranpsarent_patch


def get_fov_in_degrees(img_path, qr_code_img, chart_distance):
  """Returns fov measurement in degrees.

  Args:
    img_path: captured img path
    qr_code_img: Extracted center QR code img
    chart_distance: distance between phone and chart in cm
  Returns:
    fov_degrees: FoV measurement in degrees
  """
  img = cv2.imread(img_path)
  img_height, _ = img.shape[:2]
  logging.debug('Height of captured img in pixels: %d', img_height)

  nt_qr_code_img = _get_non_transparent_pixels(qr_code_img)
  qr_code_height, _ = nt_qr_code_img.shape[:2]
  logging.debug('Height of QR code in pixels: %d', qr_code_height)

  # Get captured image height in cm
  height_in_cm = (img_height / qr_code_height) * CENTER_QR_CODE_CM
  logging.debug('Height of captured img in cm: %d', height_in_cm)
  angle_radians = 2 * math.atan(height_in_cm / (2 * chart_distance))
  fov_degrees = math.degrees(angle_radians)
  return fov_degrees


def get_aspect_ratio(img_path):
  """Returns the aspect ratio of the image.

  Args:
    img_path: str; file path
  Returns: aspect ratio of the captured image
  """
  img = cv2.imread(img_path)
  height, width = img.shape[:2]
  logging.debug('Image H: %s, W: %s', height, width)
  aspect_ratio = width / height
  logging.debug('Aspect ratio: %.2f', aspect_ratio)
  return round(float(aspect_ratio), 2)


def derive_hal_zoom_ratio(props, scaler_crop_region):
  """Derives zoomRatio from scaler crop region.

  Args:
    props: camera properties
    scaler_crop_region: value of android.scaler.cropRegion
  Returns:
    zoom_ratio: zoomRatio derived from cropRegion
  Raises:
    AssertionError in case of invalid scaler cropRegion value
  """
  # Check if scaler_crop_region size is 4 or not
  if len(scaler_crop_region) != 4:
    raise AssertionError('Invalid scaler crop region value')

  # If distortion correction is not supported, use active array size,
  # else, use preCorrection active array size.
  if camera_properties_utils.distortion_correction(props):
    active_array_size = props.get(
        'android.sensor.info.preCorrectionActiveArraySize')
    array_width = active_array_size['right'] - active_array_size['left']
    array_height = active_array_size['bottom'] - active_array_size['top']
  else:
    active_array_size = props.get('android.sensor.info.activeArraySize')
    array_width = active_array_size['right'] - active_array_size['left']
    array_height = active_array_size['bottom'] - active_array_size['top']

  logging.debug('active_array_size: %s', active_array_size)

  left = scaler_crop_region[0]
  top = scaler_crop_region[1]
  right = scaler_crop_region[2]
  bottom = scaler_crop_region[3]

  # Center of the preCorrection/active size
  array_center_x = array_width / 2.0
  array_center_y = array_height / 2.0

  # Re-map crop region to coordinate system centered to
  # (array_center_x, array_center_y).
  crop_region_left = array_center_x - left
  crop_region_top = array_center_y - top
  crop_region_right = right - array_center_x
  crop_region_bottom = bottom - array_center_y

  # Calculate the scaling factor for left, top, bottom, right
  zoom_ratio_left = (max(array_width / (2 * crop_region_left), 1.0)
                     if crop_region_left != 0 else 1.0)
  zoom_ratio_top = (max(array_height / (2 * crop_region_top), 1.0)
                    if crop_region_top != 0 else 1.0)
  zoom_ratio_right = (max(array_width / (2 * crop_region_right), 1.0)
                      if crop_region_right != 0 else 1.0)
  zoom_ratio_bottom = (max(array_height / (2 * crop_region_bottom), 1.0)
                       if crop_region_bottom != 0 else 1.0)

  # Use minimum scaling factor to handle letterboxing or pillarboxing
  zoom_ratio = min(zoom_ratio_left, zoom_ratio_right,
                   zoom_ratio_top, zoom_ratio_bottom)

  logging.debug('Derived zoomRatio: %.2f', zoom_ratio)
  return zoom_ratio


def get_delta_e76(default_color_cells, jca_color_cells):
  """Computes the delta E76 value between two color cells.

  Delta E76 formula:
  http://www.brucelindbloom.com/index.html?Eqn_DeltaE_CIE76.html

  Args:
    default_color_cells: list of default color cells
    jca_color_cells: list of jca color cells

  Returns:
    delta_e76: delta E76 value between default and jca color cells
  """
  delta_e76_values = []
  for i, (default_color_cell, jca_color_cell) in enumerate(
      zip(default_color_cells, jca_color_cells)):
    mean_l_default, mean_a_default, mean_b_default = get_lab_mean_values(
        default_color_cell
    )
    mean_l_jca, mean_a_jca, mean_b_jca = get_lab_mean_values(jca_color_cell)
    delta_e76 = np.sqrt(
        (mean_l_default - mean_l_jca) ** 2
        + (mean_a_default - mean_a_jca) ** 2
        + (mean_b_default - mean_b_jca) ** 2
    )
    logging.debug('Delta E76 value for color cell %d: %.2f', i + 1, delta_e76)
    delta_e76_values.append(delta_e76)
  return delta_e76_values


def get_ref_delta_ab(color_cells):
  """Returns the delta ab value for a color cell compared to the reference values.

  Args:
    color_cells: list of color cells
  Returns:
    ref_delta_ab_values: list of reference delta ab values for each color cell
  """
  ref_delta_ab_values = []
  for i, (ref_a, ref_b) in enumerate(MCC_AB_VALUES):
    _, mean_a, mean_b = get_lab_mean_values(color_cells[i])
    ref_delta_ab = np.sqrt((ref_a - mean_a) ** 2 + (ref_b - mean_b) ** 2)
    logging.debug(
        'Reference delta AB value for color cell %d: %.2f', i + 1, ref_delta_ab
    )
    ref_delta_ab_values.append(ref_delta_ab)
  return ref_delta_ab_values


def get_color_rendering_variation(default_color_cells, jca_color_cells, is_hdr):
  """Gets the color rendering variation between default and jca color cells.

  Args:
    default_color_cells: list of default color cells
    jca_color_cells: list of jca color cells
    is_hdr: True if captured images are HDR, False otherwise

  Returns:
    mean_delta_ab_diff: mean delta ab diff between default and jca
  """
  logging.debug('Doing color accuracy check')
  if is_hdr:
    logging.debug('Images captured are HDR.')

  default_ref_delta_ab_values = get_ref_delta_ab(default_color_cells)
  jca_ref_delta_ab_values = get_ref_delta_ab(jca_color_cells)
  default_jca_delta_ab_values = get_delta_ab(
      default_color_cells, jca_color_cells
  )
  default_jca_delta_e76_values = get_delta_e76(
      default_color_cells, jca_color_cells
  )

  logging.debug('default_ref_delta_ab_values: %s',
                [round(x, 2) for x in default_ref_delta_ab_values])
  logging.debug('jca_ref_delta_ab_values: %s',
                [round(x, 2) for x in jca_ref_delta_ab_values])
  logging.debug('default_jca_delta_ab_values: %s',
                [round(x, 2) for x in default_jca_delta_ab_values])
  logging.debug('default_jca_delta_e76_values: %s',
                [round(x, 2) for x in default_jca_delta_e76_values])

  for i, (
      default_ref_delta_ab,
      jca_ref_delta_ab,
      default_jca_delta_ab,
      default_jca_delta_e76,
  ) in enumerate(
      zip(
          default_ref_delta_ab_values,
          jca_ref_delta_ab_values,
          default_jca_delta_ab_values,
          default_jca_delta_e76_values,
      )
  ):
    # Check that the diff between reference and default/jca does
    # not exceed the max absolute error
    if (default_ref_delta_ab > MAX_DELTA_AB_ABSOLUTE_ERROR) or (
        jca_ref_delta_ab > MAX_DELTA_AB_ABSOLUTE_ERROR
    ):
      e_msg = (
          'Color variation between reference and default/JCA for color cell'
          f' {i + 1} exceeds the threshold. Actual default:'
          f' {default_ref_delta_ab:.2f}, Actual jca: {jca_ref_delta_ab:.2f},'
          f' Expected: {MAX_DELTA_AB_ABSOLUTE_ERROR:.2f}'
      )
      logging.debug(e_msg)

    # Check that the diff between default and jca does not exceed the max
    # absolute error
    if default_jca_delta_ab > MAX_CELL_DELTA_AB_ABSOLUTE_ERROR:
      e_msg = (
          f'Color variation between default and JCA for color cell {i + 1} '
          f'exceeds the threshold. Actual: {default_jca_delta_ab:.2f}, '
          f'Expected: {MAX_CELL_DELTA_AB_ABSOLUTE_ERROR:.2f}'
      )
      logging.debug(e_msg)
    # Check that the diff between default and jca does not exceed the max
    # absolute error
    if default_jca_delta_e76 > MAX_DELTA_E76_ABSOLUTE_ERROR:
      e_msg = (
          f'Color variation between default and JCA for color cell {i + 1} '
          f'exceeds the threshold. Actual: {default_jca_delta_e76:.2f}, '
          f'Expected: {MAX_DELTA_E76_ABSOLUTE_ERROR:.2f}'
      )
      logging.debug(e_msg)

  # Check that the mean delta ab diff between default and jca does not exceed
  # the max relative error
  mean_delta_ab_diff = sum(default_jca_delta_ab_values) / len(
      default_jca_delta_ab_values
  )
  if mean_delta_ab_diff > MAX_AVG_RELATIVE_AB_TOL:
    e_msg = (
        'Average AB error between the default camera and JCA camera is '
        f'too high. Actual: {mean_delta_ab_diff:.2f}, '
        f'Expected: {MAX_AVG_RELATIVE_AB_TOL:.2f}'
    )
    logging.debug(e_msg)
  mean_delta_e76_diff = sum(default_jca_delta_e76_values) / len(
      default_jca_delta_e76_values
  )

  if mean_delta_e76_diff > MAX_DELTA_E76_ABSOLUTE_ERROR:
    e_msg = (
        'Average delta E76 error between default camera and JCA camera '
        f'is too high. Actual: {mean_delta_e76_diff:.2f}, '
        f'Expected: {MAX_DELTA_E76_ABSOLUTE_ERROR:.2f}'
    )
    logging.debug(e_msg)
    # TODO(ruchamk):Raise an error if the threshold exceeds
  return mean_delta_ab_diff, mean_delta_e76_diff

