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
"""Tests for ui_interaction_utils."""

import unittest
import unittest.mock

import numpy.testing as npt
from snippet_uiautomator import uidevice
from snippet_uiautomator import uiobject2

import its_device_utils
import ui_interaction_utils

_LOG_PATH = '/foo/bar/baz'
_SCREENSHOT_PREFIX_KEYWORD_ARGUMENT = 'prefix'


def _get_mock_ui_object(visibility):
  """Returns a mock snippet-uiautomator UI object with specified visibility."""
  mock_ui_object = unittest.mock.create_autospec(
      uiobject2.UiObject2, instance=True)
  mock_ui_object.wait.exists.return_value = visibility
  return mock_ui_object


def _get_mock_dut(visibility):
  """Returns a mock Android device object with specified UI visibility."""
  # Mock used because accessing the `ui` attribute is unwieldy with autospec.
  mock_dut = unittest.mock.Mock()
  mock_ui = unittest.mock.create_autospec(uidevice.UiDevice, instance=True)
  mock_ui.wait.exists.return_value = visibility
  mock_dut.ui.return_value = mock_ui
  return mock_dut


class UiInteractionUtilsTest(unittest.TestCase):
  """Unit tests for this module."""

  def setUp(self):
    super().setUp()
    self.mock_visible_ui_object = _get_mock_ui_object(True)
    self.mock_not_visible_ui_object = _get_mock_ui_object(False)
    self.mock_call_on_fail = unittest.mock.Mock()
    self.mock_visible_dut = _get_mock_dut(True)
    self.mock_not_visible_dut = _get_mock_dut(False)
    self.addCleanup(unittest.mock.patch.stopall)
    unittest.mock.patch.object(its_device_utils, 'run', autospec=True).start()

  def test_verify_ui_object_visible_with_visible_object_and_call(self):
    ui_interaction_utils.verify_ui_object_visible(
        self.mock_visible_ui_object, call_on_fail=self.mock_call_on_fail)
    self.mock_call_on_fail.assert_not_called()

  def test_verify_ui_object_visible_with_not_visible_object_no_call(self):
    with self.assertRaises(AssertionError):
      ui_interaction_utils.verify_ui_object_visible(
          self.mock_not_visible_ui_object)

  def test_verify_ui_object_visible_with_not_visible_object_and_call(self):
    with self.assertRaises(AssertionError):
      ui_interaction_utils.verify_ui_object_visible(
          self.mock_not_visible_ui_object, call_on_fail=self.mock_call_on_fail)
    self.mock_call_on_fail.assert_called_once()

  def test_open_jca_viewfinder_success(self):
    ui_interaction_utils.open_jca_viewfinder(self.mock_visible_dut, _LOG_PATH)
    self.mock_visible_dut.take_screenshot.assert_called_once()
    mock_args, mock_kwargs = self.mock_visible_dut.take_screenshot.call_args
    self.assertEqual(mock_args, (_LOG_PATH,))
    self.assertEqual(mock_kwargs[_SCREENSHOT_PREFIX_KEYWORD_ARGUMENT],
                     ui_interaction_utils.VIEWFINDER_VISIBLE_PREFIX)

  @unittest.mock.patch.object(ui_interaction_utils,
                              'verify_ui_object_visible',
                              autospec=True)
  def test_open_jca_viewfinder_fail(self, _):
    with self.assertRaises(AssertionError):
      ui_interaction_utils.open_jca_viewfinder(
          self.mock_not_visible_dut, _LOG_PATH)
    self.mock_not_visible_dut.take_screenshot.assert_called_once()
    mock_args, mock_kwargs = (
        self.mock_not_visible_dut.take_screenshot.call_args
    )
    self.assertEqual(mock_args, (_LOG_PATH,))
    self.assertEqual(mock_kwargs[_SCREENSHOT_PREFIX_KEYWORD_ARGUMENT],
                     ui_interaction_utils.VIEWFINDER_NOT_VISIBLE_PREFIX)
    self.mock_not_visible_dut.ui.dump.assert_called_once()

  def test_match_zoom_ratios_with_leading_result_1_0_and_duplicates(self):
    request_zoom_ratios = [0.85, 1, 1.5, 2.1, 2.5]
    result_zoom_ratios = [1.0, 0.86, 1.02, 1.51, 2.12, 2.13, 2.52]
    matched_zoom_ratios = ui_interaction_utils.match_zoom_ratios(
        result_zoom_ratios, request_zoom_ratios
    )
    npt.assert_array_almost_equal(
        [0.86, 1.02, 1.51, 2.12, 2.52],
        matched_zoom_ratios
    )

  def test_match_zoom_ratios_with_both_leading_ratio_1_0(self):
    request_zoom_ratios = [
        1.0, 1.33, 1.67, 2.0, 2.33, 2.67, 3.0, 3.33, 3.67, 4.0]
    result_zoom_ratios = [
        1.0, 1.33000004, 1.66999996, 2.0, 2.32999992,
        2.67000008, 3.0, 3.32999992, 3.67000008, 4.0]
    # Special case where the expected result matches a function input
    expected_matched_zoom_ratios = result_zoom_ratios
    matched_zoom_ratios = ui_interaction_utils.match_zoom_ratios(
        result_zoom_ratios, request_zoom_ratios
    )
    npt.assert_array_almost_equal(
        expected_matched_zoom_ratios,
        matched_zoom_ratios
    )

  def test_match_zoom_ratios_with_mismatch_at_end(self):
    request_zoom_ratios = [1.0, 1.5, 1.8, 2.1, 2.5]
    result_zoom_ratios = [1.0, 1.501, 1.802, 2.102, 4.0]
    with self.assertRaises(ValueError):
      ui_interaction_utils.match_zoom_ratios(
          result_zoom_ratios, request_zoom_ratios
      )

  def test_match_zoom_ratios_with_mismatch_in_middle(self):
    request_zoom_ratios = [1.0, 1.5, 1.8, 2.1, 2.5]
    result_zoom_ratios = [1.0, 1.501, 18, 2.102, 2.501]
    with self.assertRaises(ValueError):
      ui_interaction_utils.match_zoom_ratios(
          result_zoom_ratios, request_zoom_ratios
      )

  def test_match_zoom_ratios_with_mismatch_in_length(self):
    request_zoom_ratios = [1.0, 1.5, 1.8, 2.1, 2.5]
    result_zoom_ratios = [1.0, 1.501, 2.102, 2.501]
    with self.assertRaises(ValueError):
      ui_interaction_utils.match_zoom_ratios(
          result_zoom_ratios, request_zoom_ratios
      )

  def test_match_zoom_ratios_simple_one_to_one(self):
    request_zoom_ratios = [1.0, 2.0, 3.0]
    result_zoom_ratios = [1.01, 2.02, 3.03]
    matched_zoom_ratios = ui_interaction_utils.match_zoom_ratios(
        result_zoom_ratios, request_zoom_ratios
    )
    npt.assert_array_almost_equal([1.01, 2.02, 3.03], matched_zoom_ratios)

  def test_match_zoom_ratios_with_skipped_results_no_leading_one(self):
    request_zoom_ratios = [1.5, 2.5]
    result_zoom_ratios = [1.2, 1.51, 2.0, 2.52, 3.0]
    matched_zoom_ratios = ui_interaction_utils.match_zoom_ratios(
        result_zoom_ratios, request_zoom_ratios
    )
    npt.assert_array_almost_equal([1.51, 2.52], matched_zoom_ratios)

  def test_match_zoom_ratios_with_longer_result_list(self):
    request_zoom_ratios = [2.0, 4.0]
    result_zoom_ratios = [1.0, 1.5, 2.01, 2.5, 3.0, 3.5, 4.02, 4.5]
    matched_zoom_ratios = ui_interaction_utils.match_zoom_ratios(
        result_zoom_ratios, request_zoom_ratios
    )
    npt.assert_array_almost_equal([2.01, 4.02], matched_zoom_ratios)

  def test_match_zoom_ratios_exact_match(self):
    request_zoom_ratios = [1.0, 1.5, 2.0]
    result_zoom_ratios = [1.0, 1.5, 2.0]
    matched_zoom_ratios = ui_interaction_utils.match_zoom_ratios(
        result_zoom_ratios, request_zoom_ratios
    )
    npt.assert_array_almost_equal([1.0, 1.5, 2.0], matched_zoom_ratios)

  def test_match_zoom_ratios_request_list_longer_than_results(self):
    request_zoom_ratios = [1.0, 2.0, 3.0, 4.0]
    result_zoom_ratios = [1.01, 2.02, 3.03]
    with self.assertRaises(ValueError):
      ui_interaction_utils.match_zoom_ratios(
          result_zoom_ratios, request_zoom_ratios
      )

  def test_match_zoom_ratios_duplicate_1_0_in_request(self):
    request_zoom_ratios = [1.0, 1.0, 2.0]
    result_zoom_ratios = [1.01, 1.02, 2.03]
    matched_zoom_ratios = ui_interaction_utils.match_zoom_ratios(
        result_zoom_ratios, request_zoom_ratios
    )
    npt.assert_array_almost_equal([1.01, 1.02, 2.03], matched_zoom_ratios)

  def test_match_zoom_ratios_duplicate_1_0_in_result(self):
    request_zoom_ratios = [1.0, 2.0, 3.0]
    result_zoom_ratios = [1.01, 1.02, 2.03, 3.01]
    matched_zoom_ratios = ui_interaction_utils.match_zoom_ratios(
        result_zoom_ratios, request_zoom_ratios
    )
    npt.assert_array_almost_equal([1.01, 2.03, 3.01], matched_zoom_ratios)


if __name__ == '__main__':
  unittest.main()
