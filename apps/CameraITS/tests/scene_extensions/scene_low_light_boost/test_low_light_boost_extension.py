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
"""Verify low light boost api is activated correctly when requested."""


import cv2
import logging
import matplotlib.pyplot as plt
import os.path

from mobly import test_runner
import numpy as np

import camera_properties_utils
import capture_request_utils
import image_processing_utils
import its_base_test
import its_session_utils
import lighting_control_utils
import video_processing_utils

_AE_LOW_LIGHT_BOOST_MODE = 6  # The preview frame number to capture
_EXPECTED_NUM_OF_BOXES = 20  # The captured image must result in 20 detected
                             # boxes since the test scene has 20 boxes
_EXTENSION_NIGHT = 4  # CameraExtensionCharacteristics#EXTENSION_NIGHT
_EXTENSION_NONE = -1  # Use Camera2 instead of a Camera Extension
_MIN_SIZE = 1280*720  # 720P
_NAME = os.path.splitext(os.path.basename(__file__))[0]
_NUM_FRAMES_TO_WAIT = 40  # The preview frame number to capture
_TABLET_BRIGHTNESS_REAR_CAMERA = '6'  # Target brightness on a supported tablet
_TABLET_BRIGHTNESS_FRONT_CAMERA = '12'  # Target brightness on a supported
                                        # tablet
_TAP_COORDINATES = (500, 500)  # Location to tap tablet screen via adb

_BOX_PADDING = 5
_CROP_PADDING = 10
_BOX_MIN_SIZE = 20
_MAX_ASPECT_RATIO = 1.2
_MIN_ASPECT_RATIO = 0.8

_AVG_DELTA_LUMINANCE_THRESHOLD = 18
_AVG_LUMINANCE_THRESHOLD = 90

_BOUNDING_BOX_COLOR = (0, 255, 0)
_TEXT_COLOR = (255, 255, 255)

_RED_HSV_RANGE_LOWER_1 = np.array([0, 100, 100])
_RED_HSV_RANGE_UPPER_1 = np.array([20, 255, 255])
_RED_HSV_RANGE_LOWER_2 = np.array([170, 100, 100])
_RED_HSV_RANGE_UPPER_2 = np.array([179, 255, 255])

_CAPTURE_REQUEST = {
    'android.control.mode': 1,
    'android.control.aeMode': _AE_LOW_LIGHT_BOOST_MODE,
    'android.control.awbMode': 1,
    'android.control.afMode': 1,
    'android.colorCorrection.mode': 1,
    'android.shading.mode': 1,
    'android.tonemap.mode': 1,
    'android.lens.opticalStabilizationMode': 0,
    'android.control.videoStabilizationMode': 0,
    'android.control.aeTargetFpsRange': [10, 30],
}


def _crop(img):
  """Crops the captured image according to the red square outline.

  Args:
    img: numpy array; captured image
  Returns:
    numpy array of the cropped image or the original image if the crop region
    isn't found
  """
  hsv_img = cv2.cvtColor(img, cv2.COLOR_BGR2HSV)
  # Define boundary of the red box in HSV which is the region to crop
  # We create two masks and combine them
  mask_1 = cv2.inRange(hsv_img, _RED_HSV_RANGE_LOWER_1, _RED_HSV_RANGE_UPPER_1)
  mask_2 = cv2.inRange(hsv_img, _RED_HSV_RANGE_LOWER_2, _RED_HSV_RANGE_UPPER_2)
  mask = mask_1 + mask_2

  contours, _ = cv2.findContours(mask, cv2.RETR_EXTERNAL,
                                 cv2.CHAIN_APPROX_SIMPLE)

  max_area = 20
  max_box = None

  # Find the largest box that is closest to square
  for c in contours:
    x, y, w, h = cv2.boundingRect(c)
    aspect_ratio = w / h
    if _MIN_ASPECT_RATIO < aspect_ratio < _MAX_ASPECT_RATIO:
      area = w * h
      if area > max_area:
        max_area = area
        max_box = (x, y, w, h)

  # If the box is found then return the cropped image
  # otherwise the original image is returned
  if max_box:
    x, y, w, h = max_box
    cropped_img = img[
        y+_CROP_PADDING:y+h-_CROP_PADDING,
        x+_CROP_PADDING:x+w-_CROP_PADDING
    ]
    return cropped_img

  return img


def _find_boxes(image):
  """Finds boxes in the captured image for computing luminance.

  The boxes are detected by finding the contours by applying a threshold
  followed erosion.

  Args:
    image: numpy array; the captured image
  Returns:
    array; an array of boxes, where each box is (x, y, w, h)
  """
  gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
  blur = cv2.GaussianBlur(gray, (3, 3), 0)

  thresh = cv2.adaptiveThreshold(
      blur, 255, cv2.ADAPTIVE_THRESH_MEAN_C, cv2.THRESH_BINARY, 31, -5)

  kernel = np.ones((3, 3), np.uint8)
  eroded = cv2.erode(thresh, kernel, iterations=1)

  contours, _ = cv2.findContours(eroded, cv2.RETR_EXTERNAL,
                                 cv2.CHAIN_APPROX_SIMPLE)
  boxes = []

  for c in contours:
    x, y, w, h = cv2.boundingRect(c)
    aspect_ratio = w / h
    if (w > _BOX_MIN_SIZE and h > _BOX_MIN_SIZE and
        _MIN_ASPECT_RATIO < aspect_ratio < _MAX_ASPECT_RATIO):
      boxes.append((x, y, w, h))
  return boxes


def _compute_luminance_regions(image, boxes):
  """Compute the luminance for each box.

  Args:
    image: numpy array; captured image.
    boxes: array; array of boxes where each box is (x, y, w, h).
  Returns:
    Array of tuples where each tuple is (box, luminance).
  """
  intensities = []
  for b in boxes:
    x, y, w, h = b
    left = int(x + _BOX_PADDING)
    top = int(y + _BOX_PADDING)
    right = int(x + w - _BOX_PADDING)
    bottom = int(y + h - _BOX_PADDING)
    box = image[top:bottom, left:right]
    box_xyz = cv2.cvtColor(box, cv2.COLOR_BGR2XYZ)
    intensity = int(np.mean(box_xyz[1]))
    intensities.append((b, intensity))
  return intensities


def _draw_luminance(image, intensities):
  """Draws the luminance for each box. Useful for debugging.

  Args:
    image: numpy array; captured image.
    intensities: array; array of tuples (box, luminance intensity).
  """
  for (b, intensity) in intensities:
    x, y, w, h = b
    left = int(x + _BOX_PADDING)
    top = int(y + _BOX_PADDING)
    right = int(x + w - _BOX_PADDING)
    bottom = int(y + h - _BOX_PADDING)
    cv2.rectangle(image, (left, top), (right, bottom), _BOUNDING_BOX_COLOR, 2)
    cv2.putText(image, f'{intensity}', (x, y - 10),
                cv2.FONT_HERSHEY_PLAIN, 1, _TEXT_COLOR, 1, 2)


def _compute_avg(results):
  """Computes the average luminance of the first 6 boxes.

  Args:
    results: A list of tuples where each tuple is (box, luminance)
  Returns:
    float; The average luminance of the first 6 boxes
  """
  luminance_values = [luminance for _, luminance in results[:6]]
  avg = sum(luminance_values) / len(luminance_values)
  return avg


def _compute_avg_delta_of_successive_boxes(results):
  """Computesthe delta of successive boxes and takes the average of the first 5.

  Args:
    results: A list of tuples where each tuple is (box, luminance)
  Returns:
    float; The average of the first 5 deltas of successive boxes
  """
  luminance_values = [luminance for _, luminance in results[:6]]
  delta = [luminance_values[i] - luminance_values[i - 1]
           for i in range(1, len(luminance_values))]
  avg = sum(delta) / len(delta)
  return avg


def _plot_results(results, file_stem):
  """Plots the computed luminance for each box.

  Args:
    results: A list of tuples where each tuple is (box, luminance)
    file_stem: The output file where the plot is saved
  """
  luminance_values = [luminance for _, luminance in results]
  box_labels = [f'Box {i + 1}' for i in range(len(results))]

  plt.figure(figsize=(10, 6))
  plt.plot(box_labels, luminance_values, marker='o', linestyle='-', color='b')
  plt.scatter(box_labels, luminance_values, color='r')

  plt.title('Luminance for each Box')
  plt.xlabel('Boxes')
  plt.ylabel('Luminance (pixel intensity)')
  plt.grid('True')
  plt.xticks(rotation=45)
  plt.savefig(f'{file_stem}_luminance_plot.png', dpi=300)
  plt.close()


def _plot_successive_difference(results, file_stem):
  """Plots the successive difference in luminance between each box.

  Args:
    results: A list of tuples where each tuple is (box, luminance)
    file_stem: The output file where the plot is saved
  """
  luminance_values = [luminance for _, luminance in results]
  delta = [luminance_values[i] - luminance_values[i - 1]
           for i in range(1, len(luminance_values))]
  box_labels = [f'Box {i} to Box {i + 1}' for i in range(1, len(results))]

  plt.figure(figsize=(10, 6))
  plt.plot(box_labels, delta, marker='o', linestyle='-', color='b')
  plt.scatter(box_labels, delta, color='r')

  plt.title('Difference in Luminance Between Successive Boxes')
  plt.xlabel('Box Transition')
  plt.ylabel('Luminance Difference')
  plt.grid('True')
  plt.xticks(rotation=45)
  file = f'{file_stem}_luminance_difference_between_successive_boxes_plot.png'
  plt.savefig(file, dpi=300)
  plt.close()


def _analyze_capture(file_stem, img):
  """Analyze a captured frame to check if it meets low light boost criteria.

  The capture is cropped first, then detects for boxes, and then computes the
  luminance of each box.

  Args:
    file_stem: The file prefix for results saved
    img: numpy array; The captured image loaded by cv2 as and available for
         analysis
  """
  cv2.imwrite(f'{file_stem}_original_low_light_boost.jpg', img)
  img = _crop(img)
  cv2.imwrite(f'{file_stem}_low_light_boost.jpg', img)
  boxes = _find_boxes(img)
  if len(boxes) != _EXPECTED_NUM_OF_BOXES:
    raise AssertionError('The captured image failed to detect the expected '
                         'number of boxes. '
                         'Check the captured image to see if the image was '
                         'correctly captured and try again. '
                         f'Actual: {len(boxes)}, '
                         f'Expected: {_EXPECTED_NUM_OF_BOXES}')

  regions = _compute_luminance_regions(img, boxes)

  _draw_luminance(img, regions)
  cv2.imwrite(f'{file_stem}_low_light_boost_result.jpg', img)

  # Sorted so each column is read left to right
  sorted_regions = sorted(regions, key=lambda r: (r[0][0], r[0][1]))
  # Reorder this so the regions are increasing in luminance according to the
  # Hilbert curve arrangement pattern of the grid
  # See scene_low_light_boost_reference.png which indicates the order of each
  # box
  hilbert_ordered = [
      sorted_regions[17],
      sorted_regions[13],
      sorted_regions[12],
      sorted_regions[16],
      sorted_regions[15],
      sorted_regions[14],
      sorted_regions[10],
      sorted_regions[11],
      sorted_regions[7],
      sorted_regions[6],
      sorted_regions[2],
      sorted_regions[3],
      sorted_regions[4],
      sorted_regions[8],
      sorted_regions[9],
      sorted_regions[5],
  ]
  _plot_results(hilbert_ordered, file_stem)
  _plot_successive_difference(hilbert_ordered, file_stem)
  avg = _compute_avg(hilbert_ordered)
  delta_avg = _compute_avg_delta_of_successive_boxes(hilbert_ordered)
  logging.debug('average luminance of the 6 boxes: %.2f', avg)
  logging.debug('average difference in luminance of 5 successive boxes: %.2f',
                delta_avg)
  if avg < _AVG_LUMINANCE_THRESHOLD:
    raise AssertionError('Average luminance of the first 6 boxes did not '
                         'meet minimum requirements for low light boost '
                         'criteria. '
                         f'Actual: {avg:.2f}, '
                         f'Expected: {_AVG_LUMINANCE_THRESHOLD}')
  if delta_avg < _AVG_DELTA_LUMINANCE_THRESHOLD:
    raise AssertionError('The average difference in luminance of the first 5 '
                         'successive boxes did not meet minimum requirements '
                         'for low light boost criteria. '
                         f'Actual: {delta_avg:.2f}, '
                         f'Expected: {_AVG_DELTA_LUMINANCE_THRESHOLD}')


def _capture_and_analyze(cam, file_stem, camera_id, preview_size, extension,
                         mirror_output):
  """Capture a preview frame and then analyze it.

  Args:
    cam: ItsService to send commands
    file_stem: File prefix for captured images
    camera_id: Camera ID under test
    preview_size: Target size of preview
    extension: Extension mode or -1 to use Camera2
    mirror_output: If the output should be mirrored across the vertical axis
  """
  frame_bytes = cam.do_capture_preview_frame(camera_id,
                                             preview_size,
                                             _NUM_FRAMES_TO_WAIT,
                                             extension,
                                             _CAPTURE_REQUEST)
  logging.debug('received image')
  np_array = np.frombuffer(frame_bytes, dtype=np.uint8)
  img_rgb = cv2.imdecode(np_array, cv2.IMREAD_COLOR)
  if mirror_output:
    img_rgb = cv2.flip(img_rgb, 1)
  _analyze_capture(file_stem, img_rgb)


class LowLightBoostTest(its_base_test.ItsBaseTest):
  """Tests low light boost mode under dark lighting conditions.

  The test checks if low light boost AE mode is available. The test is skipped
  if it is not available for Camera2 and Camera Extensions Night Mode.

  Low light boost is enabled and a frame from the preivew stream is captured
  for analysis. The analysis applies the following operations:
    1. crops the region defined by a red square outline
    2. detects the presence of 20 boxes
    3. computes the luminance bounded by each box
    4. determines the average luminance of the 6 darkest boxes according to the
      Hilbert curve arrangement of the grid.
    5. determines the average difference in luminance of the 6 successive
      darkest boxes.
    6. Checks for passing criteria: the avg luminance must be at least 90 or
      greater, the avg difference in luminance between successive boxes must be
      at least 18 or greater.
  """

  def test_low_light_boost(self):
    self.scene = 'scene_low_light_boost'
    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:
      props = cam.get_camera_properties()
      props = cam.override_with_hidden_physical_camera_props(props)
      test_name = os.path.join(self.log_path, _NAME)

      # Check SKIP conditions
      # Determine if DUT is at least Android 15
      first_api_level = its_session_utils.get_first_api_level(self.dut.serial)
      camera_properties_utils.skip_unless(
          first_api_level >= its_session_utils.ANDROID15_API_LEVEL)

      # Determine if low light boost is available
      is_low_light_boost_supported = (
          cam.is_low_light_boost_available(self.camera_id, _EXTENSION_NONE))
      is_low_light_boost_supported_night = (
          cam.is_low_light_boost_available(self.camera_id, _EXTENSION_NIGHT))
      should_run = (is_low_light_boost_supported or
                    is_low_light_boost_supported_night)
      camera_properties_utils.skip_unless(should_run)

      tablet_name_unencoded = self.tablet.adb.shell(
          ['getprop', 'ro.build.product']
      )
      tablet_name = str(tablet_name_unencoded.decode('utf-8')).strip()
      logging.debug('Tablet name: %s', tablet_name)

      if tablet_name == its_session_utils.LEGACY_TABLET_NAME:
        raise AssertionError(f'Incompatible tablet! Please use a tablet with '
                             'display brightness of at least '
                             f'{its_session_utils.DEFAULT_TABLET_BRIGHTNESS} '
                             'according to '
                             f'{its_session_utils.TABLET_REQUIREMENTS_URL}.')

      # Establish connection with lighting controller
      arduino_serial_port = lighting_control_utils.lighting_control(
          self.lighting_cntl, self.lighting_ch)

      # Turn OFF lights to darken scene
      lighting_control_utils.set_lighting_state(
          arduino_serial_port, self.lighting_ch, 'OFF')

      # Check that tablet is connected and turn it off to validate lighting
      self.turn_off_tablet()

      # Validate lighting, then setup tablet
      cam.do_3a(do_af=False)
      cap = cam.do_capture(
          capture_request_utils.auto_capture_request(), cam.CAP_YUV)
      y_plane, _, _ = image_processing_utils.convert_capture_to_planes(cap)
      its_session_utils.validate_lighting(
          y_plane, self.scene, state='OFF', log_path=self.log_path,
          tablet_state='OFF')
      self.setup_tablet()

      its_session_utils.load_scene(
          cam, props, self.scene, self.tablet, self.chart_distance,
          lighting_check=False, log_path=self.log_path)

      # Tap tablet to remove gallery buttons
      if self.tablet:
        self.tablet.adb.shell(
            f'input tap {_TAP_COORDINATES[0]} {_TAP_COORDINATES[1]}')

      # Determine preivew width and height to test
      supported_preview_sizes = cam.get_supported_preview_sizes(self.camera_id)
      logging.debug('supported_preview_sizes: %s', supported_preview_sizes)
      supported_video_qualities = cam.get_supported_video_qualities(
          self.camera_id)
      logging.debug(
          'Supported video profiles and ID: %s', supported_video_qualities)
      target_preview_size, _ = (
          video_processing_utils.get_lowest_preview_video_size(
              supported_preview_sizes, supported_video_qualities, _MIN_SIZE))
      logging.debug('target_preview_size: %s', target_preview_size)

      # Set tablet brightness to darken scene
      props = cam.get_camera_properties()
      if (props['android.lens.facing'] ==
          camera_properties_utils.LENS_FACING['BACK']):
        self.set_screen_brightness(_TABLET_BRIGHTNESS_REAR_CAMERA)
      elif (props['android.lens.facing'] ==
            camera_properties_utils.LENS_FACING['FRONT']):
        self.set_screen_brightness(_TABLET_BRIGHTNESS_FRONT_CAMERA)
      else:
        logging.debug('Only front and rear camera supported. '
                      'Skipping for camera ID %s',
                      self.camera_id)
        camera_properties_utils.skip_unless(False)

      cam.do_3a()

      # mirror the capture across the vertical axis if captured by front facing
      # camera
      should_mirror = (props['android.lens.facing'] ==
                       camera_properties_utils.LENS_FACING['FRONT'])

      # do camera2 capture
      if is_low_light_boost_supported:
        logging.debug('capture frame using camera2')
        file_stem = f'{test_name}_camera2'
        _capture_and_analyze(cam, file_stem, self.camera_id,
                             target_preview_size, _EXTENSION_NONE,
                             should_mirror)

      # do camera extensions night mode capture
      if is_low_light_boost_supported_night:
        logging.debug('capture frame using night mode extension')
        file_stem = f'{test_name}_camera_extension'
        _capture_and_analyze(cam, file_stem, self.camera_id,
                             target_preview_size, _EXTENSION_NIGHT,
                             should_mirror)


if __name__ == '__main__':
  test_runner.main()
