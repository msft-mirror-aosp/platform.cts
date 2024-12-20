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
"""Utility functions to detect a set of patterns in the chart image."""

import logging
import os

import cv2
import image_processing_utils
import numpy as np

_PATTERN_FILE_PATHS = (
    'qr_code.png',
)
_FLANN_INDEX_KDTREE = 1
_FLANN_COUNT_KDTREE = 10
# We only need 4 feature matches to compute a homography. However, use a larger
# number to avoid calculating unstable homographies.
_MIN_FEATURE_MATCHES_BEFORE_RANSAC = 35
_MIN_FEATURE_MATCHES_AFTER_RANSAC = 25
# This multiplier causes an image with max dimension of 1280 pixels to use a
# reprojection threshold of 5 pixels.
_REPROJECTION_THRESHOLD_MULTIPLIER = 5.0 / 1280
_MIN_REPROJECTION_THRESHOLD = 5.0
# These thresholds are customized for SIFT descriptors.
_DISTANCE_THRESHOLD = 300
_RATIO_THRESHOLD = 0.85


def _assert_image_type(image, colorspace):
  if not isinstance(image, np.ndarray):
    raise AssertionError('Image must be a NumPy array.')

  if colorspace == cv2.IMREAD_COLOR:
    if image.ndim != 3:
      raise AssertionError('Color image must have 3 dimensions.')
    if image.shape[2] != 3:
      raise AssertionError('Color image must have 3 channels.')
  elif colorspace == cv2.IMREAD_GRAYSCALE:
    if image.ndim != 2:
      raise AssertionError('Grayscale image must have 2 dimensions.')


def _filter_by_ratio_and_distance_test(keypoints_1, keypoints_2, matches):
  """Filters feature matches with a ratio test and a distance test.

  See Lowe's SIFT paper for an explanation of the ratio test. The implementation
  here is adapted from third_party/OpenCVX/v3_4_0/samples/python/find_obj.py

  Args:
    keypoints_1: first set of keypoints
    keypoints_2: second set of keypoints
    matches: matches between the two sets of keypoints

  Returns:
    filtered first set of keypoints
    filtered second key of keypoints
    filtered matching keypoint pairs
  """
  mkeypoints_1, mkeypoints_2 = [], []
  for match in matches:
    if len(match) != 2:
      continue
    if match[0].distance >= _DISTANCE_THRESHOLD:
      continue
    if match[0].distance >= match[1].distance * _RATIO_THRESHOLD:
      continue
    match = match[0]
    mkeypoints_1.append(keypoints_1[match.queryIdx])
    mkeypoints_2.append(keypoints_2[match.trainIdx])

  filtered_keypoints1 = np.float32([kp.pt for kp in mkeypoints_1])
  filtered_keypoints2 = np.float32([kp.pt for kp in mkeypoints_2])
  filtered_pairs = list(zip(mkeypoints_1, mkeypoints_2))
  return filtered_keypoints1, filtered_keypoints2, list(filtered_pairs)


def detect_pattern(query_image: str | np.ndarray):
  """Detects patterns in a query image.

  Searches for a fixed set of patterns in the query image. If one of the
  patterns is found in the query image, a homography between the query image
  and the matching pattern image is returned.

  Args:
    query_image: Query image data, either in the format of a string representing
      the path to the query image file or 3 channel BGR color image data in
      numpy matrix format (same return format as OpenCV `imread(filename)`
      invocation with default parameters).

  Returns:
    (pattern image path, homography as 2-d numpy array, horizontal flip status)
    if the query image matched a pattern image, or None otherwise
  """
  # Initialize SIFT detector and FLANN matcher.
  detector = cv2.SIFT_create(enable_precise_upscale=True)
  flann_params = dict(algorithm=_FLANN_INDEX_KDTREE, trees=_FLANN_COUNT_KDTREE)
  matcher = cv2.FlannBasedMatcher(flann_params, {})

  # Load query image and extract features.
  if isinstance(query_image, str):
    logging.debug('Query image: %s', query_image)
    query_image = cv2.imread(query_image)
  _assert_image_type(query_image, cv2.IMREAD_COLOR)

  query_points, query_descriptors = detector.detectAndCompute(query_image, None)
  logging.debug('%d features in query image', len(query_points))
  if len(query_points) < _MIN_FEATURE_MATCHES_BEFORE_RANSAC:
    logging.debug('Not enough query features.')
    return None

  for pattern_file_path in _PATTERN_FILE_PATHS:
    # Load pattern image.
    path = os.path.join(image_processing_utils.TEST_IMG_DIR, pattern_file_path)
    pattern_image = cv2.imread(path)
    logging.debug('Pattern image: %s', pattern_file_path)

    # Sometimes, the pattern is flipped horizontally in the query image. SIFT
    # features are not flip-invariant. So, try the original pattern image and
    # a horizontally flipped version, and use the version which achieves a
    # larger number of feature matches against the query image.
    max_filtered_match_count = 0
    for flip_horizontal in [False, True]:
      logging.debug('Flip horizontal: %d', flip_horizontal)
      if flip_horizontal:
        transformed_pattern_image = cv2.flip(pattern_image, 1)
      else:
        transformed_pattern_image = pattern_image
      pattern_points, pattern_descriptors = detector.detectAndCompute(
          transformed_pattern_image, None)
      logging.debug('%d features in pattern image', len(pattern_points))

      # Match features.
      raw_matches = matcher.knnMatch(
          pattern_descriptors, trainDescriptors=query_descriptors, k=2)
      filtered_pattern_points, filtered_query_points, _ = (
          _filter_by_ratio_and_distance_test(pattern_points, query_points,
                                             raw_matches))
      filtered_match_count = len(filtered_query_points)
      logging.debug('%d features matches after ratio and distance tests',
                    filtered_match_count)
      if filtered_match_count >= max_filtered_match_count:
        max_filtered_match_count = filtered_match_count
        max_filtered_pattern_points = filtered_pattern_points
        max_filtered_query_points = filtered_query_points
        max_filtered_flip_status = flip_horizontal

    if max_filtered_match_count < _MIN_FEATURE_MATCHES_BEFORE_RANSAC:
      logging.debug('Not enough feature matches before RANSAC.')
      continue

    # Adjust reprojection threshold based on the size of the query image.
    query_height, query_width = query_image.shape[:2]
    query_max_dimension = max(query_height, query_width)
    reprojection_threshold = (
        query_max_dimension * _REPROJECTION_THRESHOLD_MULTIPLIER)
    reprojection_threshold = max(_MIN_REPROJECTION_THRESHOLD,
                                 reprojection_threshold)

    # Compute homography.
    logging.debug('RANSAC reprojection threshold: %f', reprojection_threshold)
    homography, status = cv2.findHomography(max_filtered_pattern_points,
                                            max_filtered_query_points,
                                            cv2.RANSAC, reprojection_threshold)
    ransac_match_count = np.sum(np.ndarray.flatten(status))
    logging.debug('%d feature matches after RANSAC', ransac_match_count)

    if ransac_match_count < _MIN_FEATURE_MATCHES_AFTER_RANSAC:
      logging.debug('Not enough feature matches after RANSAC.')
      continue

    logging.debug('Homography:')
    logging.debug('%.3f, %.3f, %.3f', homography[0][0], homography[0][1],
                  homography[0][2])
    logging.debug('%.3f, %.3f, %.3f', homography[1][0], homography[1][1],
                  homography[1][2])
    logging.debug('%.3f, %.3f, %.3f', homography[2][0], homography[2][1],
                  homography[2][2])
    return (pattern_file_path, homography, max_filtered_flip_status)

  logging.debug('Query image did not match any patterns.')
  return None
