# Copyright 2023 The Android Open Source Project
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
"""Tests for opencv_processing_utils."""


import math
import numpy as np
import os
import unittest

import cv2

import opencv_processing_utils

# ArUco marker parameters for test images.
LEFT_ARUCO_ID = 0
RIGHT_ARUCO_ID = 1
TEST_IMG_ARUCO = '4_arucos_with_squares.png'
TEST_IMG_ARUCO_ROTATED = '4_arucos_with_squares_rotated.png'
TEST_IMG_SLANTED_EDGE = 'slanted_edge.png'


class Cv2ImageProcessingUtilsTests(unittest.TestCase):
  """Unit tests for this module."""

  def setUp(self):
    super().setUp()
    self.aruco_dict = cv2.aruco.getPredefinedDictionary(cv2.aruco.DICT_4X4_100)
    self.aruco_params = cv2.aruco.DetectorParameters()
    self.aruco_image_path = os.path.join(
        opencv_processing_utils.TEST_IMG_DIR, TEST_IMG_ARUCO)
    self.aruco_img = cv2.imread(self.aruco_image_path)
    self.aruco_rotated_image_path = os.path.join(
        opencv_processing_utils.TEST_IMG_DIR, TEST_IMG_ARUCO_ROTATED)
    self.aruco_rotated_img = cv2.imread(self.aruco_rotated_image_path)
    self.slanted_edge_image_path = os.path.join(
        opencv_processing_utils.TEST_IMG_DIR, TEST_IMG_SLANTED_EDGE)
    self.slanted_edge_img = cv2.imread(self.slanted_edge_image_path)

  def test_get_angle_identify_rotated_chessboard_angle(self):
    """Unit test to check extracted angles from images."""
    # Array of the image files and angles containing rotated chessboards.
    test_cases = [
        ('', 0),
        ('_15_ccw', -15),
        ('_30_ccw', -30),
        ('_45_ccw', -45),
        ('_60_ccw', -60),
        ('_75_ccw', -75),
    ]
    test_fails = ''

    # For each rotated image pair (normal, wide), check angle against expected.
    for suffix, angle in test_cases:
      # Define image paths.
      normal_img_path = os.path.join(
          opencv_processing_utils.TEST_IMG_DIR,
          f'rotated_chessboards/normal{suffix}.jpg')
      wide_img_path = os.path.join(
          opencv_processing_utils.TEST_IMG_DIR,
          f'rotated_chessboards/wide{suffix}.jpg')

      # Load and color-convert images.
      normal_img = cv2.cvtColor(cv2.imread(normal_img_path), cv2.COLOR_BGR2GRAY)
      wide_img = cv2.cvtColor(cv2.imread(wide_img_path), cv2.COLOR_BGR2GRAY)

      # Assert angle as expected.
      normal = opencv_processing_utils.get_angle(normal_img)
      wide = opencv_processing_utils.get_angle(wide_img)
      valid_angles = (angle, angle+90)  # try both angle & +90 due to squares
      e_msg = (f'\n Rotation angle test failed: {angle}, extracted normal: '
               f'{normal:.2f}, wide: {wide:.2f}, valid_angles: {valid_angles}')
      matched_angles = False
      for a in valid_angles:
        if (math.isclose(normal, a,
                         abs_tol=opencv_processing_utils.ANGLE_CHECK_TOL) and
            math.isclose(wide, a,
                         abs_tol=opencv_processing_utils.ANGLE_CHECK_TOL)):
          matched_angles = True

      if not matched_angles:
        test_fails += e_msg

    self.assertEqual(len(test_fails), 0, test_fails)

  def test_calc_chart_scaling(self):
    """Unit test to check chart scaling rules."""
    # Wide (61-90deg FoV) in 22cm rig
    self.assertEqual(opencv_processing_utils.calc_chart_scaling(22, 80), 0.67)
    # TELE (41-60deg FoV) in 55cm rig
    self.assertEqual(opencv_processing_utils.calc_chart_scaling(55, 40), 0.5)
    # No scaling rule is found for input FoV, default is used
    self.assertIsNone(opencv_processing_utils.calc_chart_scaling(50, 115))
    # No scaling rule is found for input distance, default is used
    self.assertIsNone(opencv_processing_utils.calc_chart_scaling(200, 65))

  def _detect_aruco_markers(self, image):
    """Helper to load image and detect ArUco markers."""
    gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
    corners, ids, _ = cv2.aruco.detectMarkers(gray, self.aruco_dict,
                                              parameters=self.aruco_params)
    return corners, ids

  def test_rotated_scene_with_aruco_markers(self):
    """Unit test for detect_180_degree_rotation_with_aruco_markers - True."""
    corners, ids = self._detect_aruco_markers(self.aruco_rotated_img)
    self.assertIsNotNone(
        corners, f'Could not load image: {self.aruco_rotated_image_path}')
    result = (
        opencv_processing_utils.detect_180_degree_rotation_with_aruco_markers(
            corners, ids, LEFT_ARUCO_ID, RIGHT_ARUCO_ID))
    self.assertTrue(result)

  def test_non_rotated_scene_with_aruco_markers(self):
    """Unit test for detect_180_degree_rotation_with_aruco_markers - False."""
    corners, ids = self._detect_aruco_markers(self.aruco_img)
    self.assertIsNotNone(corners, f'Could not load image: {self.aruco_image_path}')
    result = (
        opencv_processing_utils.detect_180_degree_rotation_with_aruco_markers(
            corners, ids, LEFT_ARUCO_ID, RIGHT_ARUCO_ID))
    self.assertFalse(result)

  def test_missing_marker_raises_error(self):
    """Unit test for detect_180_degree_rotation_with_aruco_markers - Missing."""
    corners, ids = self._detect_aruco_markers(self.slanted_edge_img)
    self.assertIsNotNone(
        corners, f'Could not load image: {self.slanted_edge_image_path}')
    with self.assertRaises(ValueError):
      opencv_processing_utils.detect_180_degree_rotation_with_aruco_markers(
          corners, ids, LEFT_ARUCO_ID, RIGHT_ARUCO_ID)

if __name__ == '__main__':
  unittest.main()
