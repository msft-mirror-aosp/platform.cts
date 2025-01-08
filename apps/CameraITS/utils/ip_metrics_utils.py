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

import numpy as np

AR_REL_TOL = 0.1
# 20% tolerance as per CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION
FOV_REL_TOL = 0.2


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
  return math.isclose(height1, height2, rel_tol=FOV_REL_TOL)
