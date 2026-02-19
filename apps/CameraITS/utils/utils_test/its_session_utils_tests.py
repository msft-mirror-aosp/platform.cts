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
"""Tests for its_session_utils."""

import unittest
import unittest.mock

import numpy
import operator

import image_processing_utils
import its_session_utils


# intensity values of Y-plane array from ITS testing
_EMPIRICAL_TABLET_OFF_BRIGHTNESS = 0.11
_PLACEHOLDER_SCENE = 'its_session_utils_unittest_scene'
_TEST_IMG_W = 640
_TEST_IMG_H = 480


def _generate_test_image(brightness):
  """Creates a Y plane array with pixel values of brightness.

  Args:
    brightness: float between [0.0, 1.0]

  Returns:
    test_image: Y plane array with elements of value brightness.
  """
  test_image = numpy.zeros((_TEST_IMG_W, _TEST_IMG_H, 1), dtype=float)
  test_image.fill(brightness)
  return test_image


class ItsSessionUtilsTests(unittest.TestCase):
  """Run a suite of unit tests on this module."""

  def setUp(self):
    super().setUp()
    self.test_image_zero = _generate_test_image(0.0)
    self.test_image_half = _generate_test_image(0.5)
    self.test_image_one = _generate_test_image(1.0)
    self.addCleanup(unittest.mock.patch.stopall)
    unittest.mock.patch.object(
        image_processing_utils, 'write_image', autospec=True).start()

  def test_validate_lighting_state_on_at_zero(self):
    with self.assertRaises(AssertionError):
      its_session_utils.validate_lighting(
          self.test_image_zero, _PLACEHOLDER_SCENE)

  def test_validate_lighting_state_on_at_half(self):
    its_session_utils.validate_lighting(
        self.test_image_half, _PLACEHOLDER_SCENE)

  def test_validate_lighting_state_on_at_one(self):
    its_session_utils.validate_lighting(
        self.test_image_one, _PLACEHOLDER_SCENE)

  def test_validate_lighting_state_off_at_zero(self):
    its_session_utils.validate_lighting(
        self.test_image_zero, _PLACEHOLDER_SCENE, state='OFF')

  def test_validate_lighting_state_off_at_half(self):
    with self.assertRaises(AssertionError):
      its_session_utils.validate_lighting(
          self.test_image_half, _PLACEHOLDER_SCENE, state='OFF')

  def test_validate_lighting_state_off_at_one(self):
    with self.assertRaises(AssertionError):
      its_session_utils.validate_lighting(
          self.test_image_one, _PLACEHOLDER_SCENE, state='OFF')

  def test_validate_lighting_state_off_tablet_off_handles_noise(self):
    test_image_tablet_off = _generate_test_image(
        _EMPIRICAL_TABLET_OFF_BRIGHTNESS)
    its_session_utils.validate_lighting(test_image_tablet_off,
                                        _PLACEHOLDER_SCENE,
                                        state='OFF',
                                        tablet_state='OFF')

  def test_check_threshold_with_marginal_pass(self):
    threshold = 100
    marginal_factor = 0.1  # 10%
    context = 'Brightness'

    # Test PASS (operator.gt)
    status, msg = its_session_utils.check_threshold_with_marginal_pass(
        115, threshold, operator.gt, marginal_factor, context)
    self.assertEqual(status, its_session_utils.TestPassingStatus.PASS)
    self.assertIsNone(msg)

    # Test MARGINAL (operator.gt) -> 100 * (1 + 0.1) = 110
    status, msg = its_session_utils.check_threshold_with_marginal_pass(
        105, threshold, operator.gt, marginal_factor, context)
    self.assertEqual(status, its_session_utils.TestPassingStatus.MARGINAL)
    self.assertIn('MARGINAL', msg)

    # Test FAIL (operator.gt)
    status, msg = its_session_utils.check_threshold_with_marginal_pass(
        85, threshold, operator.gt, marginal_factor, context)
    self.assertEqual(status, its_session_utils.TestPassingStatus.FAIL)
    self.assertIn('FAILED', msg)

    # Test PASS (operator.lt)
    status, msg = its_session_utils.check_threshold_with_marginal_pass(
        85, threshold, operator.lt, marginal_factor, context)
    self.assertEqual(status, its_session_utils.TestPassingStatus.PASS)

    # Test MARGINAL (operator.lt) -> 100 * (1 - 0.1) = 90
    status, msg = its_session_utils.check_threshold_with_marginal_pass(
        95, threshold, operator.lt, marginal_factor, context)
    self.assertEqual(status, its_session_utils.TestPassingStatus.MARGINAL)

  def test_check_close_threshold_with_marginal_pass(self):
    threshold = 10.0
    abs_tol = 1.0
    marginal_factor = 0.2  # 20% of tolerance
    context = 'Focus'
    # Marginal boundary: abs_tol * (1 - 0.2) = 0.8 diff

    # Test PASS (diff < 0.8)
    status, msg = its_session_utils.check_close_threshold_with_marginal_pass(
        10.5, threshold, abs_tol, marginal_factor, context)
    self.assertEqual(status, its_session_utils.TestPassingStatus.PASS)

    # Test MARGINAL (diff between 0.8 and 1.0)
    status, msg = its_session_utils.check_close_threshold_with_marginal_pass(
        10.9, threshold, abs_tol, marginal_factor, context)
    self.assertEqual(status, its_session_utils.TestPassingStatus.MARGINAL)
    self.assertIn('marginal', msg)

    # Test FAIL (diff > 1.0)
    status, msg = its_session_utils.check_close_threshold_with_marginal_pass(
        11.1, threshold, abs_tol, marginal_factor, context)
    self.assertEqual(status, its_session_utils.TestPassingStatus.FAIL)
    self.assertIn('FAILED', msg)

    # Test PASS (negative diff)
    status, msg = its_session_utils.check_close_threshold_with_marginal_pass(
        9.3, threshold, abs_tol, marginal_factor, context)
    self.assertEqual(status, its_session_utils.TestPassingStatus.PASS)

if __name__ == '__main__':
  unittest.main()
