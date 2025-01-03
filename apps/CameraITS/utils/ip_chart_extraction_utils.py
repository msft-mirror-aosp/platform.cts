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
"""Utility functions for extracting IP chart features."""

import logging
import os
import pathlib
import subprocess

import cv2
import ip_chart_pattern_detector as pd


def get_feature_from_image(
    img, output_file_name, log_path, feature=pd.TestChartFeature.FULL_CHART
):
  """Get feature extracted from the captured image.

  Args:
    img: path to the captured image.
    output_file_name: output filename.
    log_path: path to save the extracted chart.
    feature: test chart feature to extract from the image.
  Returns:
    feature_image: extracted feature image in BGRA color space image with only
    the test chart features in query image, any pixel not within feature will be
    fully transparent. Returns None in case of any error.
    output_file_path: path to the extracted chart.
  """
  # Convert image to grayscale
  img_bgr = cv2.imread(img)
  feature_image = pd.get_test_chart_features_from_image(
      img_bgr,
      test_chart_features=[feature],
  )
  if (
      pd.detect_pattern(
          query_image=cv2.cvtColor(feature_image, cv2.COLOR_BGRA2BGR)
      )
      is None
  ):
    raise ValueError('Feature not found in the image.')
  else:
    logging.debug('Feature found in the image.')
    cv2.imwrite(
        os.path.join(log_path, output_file_name + '.png'),
        feature_image,
    )
    return feature_image, os.path.join(log_path, output_file_name + '.png')
