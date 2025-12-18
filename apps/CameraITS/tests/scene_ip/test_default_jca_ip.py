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
"""Ensure the captures from the default camera app and JCA are consistent."""

import logging
import math
import os
import pathlib
import threading
import time
import types

import camera_properties_utils
import image_processing_utils
import gen2_rig_controller_utils
import ip_chart_extraction_utils as ce
import ip_chart_pattern_detector as pd
import ip_metrics_utils
import its_base_test
import its_device_utils
import its_session_utils
from mobly import test_runner
from mobly.controllers.android_device_lib import adb
import sensor_fusion_utils
from snippet_uiautomator import uiautomator
import ui_interaction_utils


_CAMERA_HARDWARE_LEVEL_MAPPING = types.MappingProxyType({
    0: 'LIMITED',
    1: 'FULL',
    2: 'LEGACY',
    3: 'LEVEL_3',
    4: 'EXTERNAL',
})
_JETPACK_CAMERA_APP_PACKAGE_NAME = 'com.google.jetpackcamera'
_AWB_DIFF_THRESHOLD = 4
_BRIGHTNESS_DIFF_THRESHOLD = 10
_NAME = os.path.splitext(os.path.basename(__file__))[0]
_COMMON_IMG_ARS_ATOL = 0.01
_COLOR_DIFF_THRESHOLD = 6
_CLOSE_CAMERA_WAIT_TIME_SECONDS = 5


def get_jca_ar(default_capture_path):
  """Returns the aspect ratio to be used in JCA.

  Args:
    default_capture_path: default camera app capture path
  Returns:
    jca_ar: aspect ratio to be used by JCA
  Raises:
    AssertionError: If JCA does not support the aspect ratio
  """
  default_ar = ip_metrics_utils.get_aspect_ratio(default_capture_path)
  logging.debug('Default camera app aspect ratio: %.2f', default_ar)
  if (math.isclose(default_ar, 3/4, abs_tol=_COMMON_IMG_ARS_ATOL) or
      math.isclose(default_ar, 4/3, abs_tol=_COMMON_IMG_ARS_ATOL)):
    jca_ar = ui_interaction_utils.THREE_TO_FOUR_ASPECT_RATIO_DESC
  elif (math.isclose(default_ar, 9/16, abs_tol=_COMMON_IMG_ARS_ATOL) or
        math.isclose(default_ar, 16/9, abs_tol=_COMMON_IMG_ARS_ATOL)):
    jca_ar = ui_interaction_utils.NINE_TO_SIXTEEN_ASPECT_RATIO_DESC
  elif math.isclose(default_ar, 1, abs_tol=_COMMON_IMG_ARS_ATOL):
    jca_ar = ui_interaction_utils.ONE_TO_ONE_ASPECT_RATIO_DESC
  else:
    raise AssertionError('Aspect ratio not supported by JCA')
  logging.debug('Using %s for JCA captures.', jca_ar)
  return jca_ar


class DefaultJcaImageParityClassTest(its_base_test.ItsBaseTest):
  """Test for default camera and JCA image parity."""

  def _setup_gen2rig(self):
    logging.debug('Setting up gen2 rig')
    # Configure and setup gen2 rig
    self.motor_channel = int(self.rotator_ch)
    lights_channel = int(self.lighting_ch)
    lights_port = gen2_rig_controller_utils.find_serial_port(self.lighting_cntl)
    if lights_port:
      sensor_fusion_utils.establish_serial_comm(lights_port)
      gen2_rig_controller_utils.set_lighting_state(
          lights_port, lights_channel, 'ON')
    self.motor_port = gen2_rig_controller_utils.find_serial_port(
        self.rotator_cntl)
    if self.motor_port:
      gen2_rig_controller_utils.configure_rotator(
          self.motor_port, self.motor_channel)
      gen2_rig_controller_utils.rotate(self.motor_port, self.motor_channel)

  def setup_class(self):
    super().setup_class()
    self.dut.services.register(
        uiautomator.ANDROID_SERVICE_NAME, uiautomator.UiAutomatorService
    )
    gen2_rig_controller_utils.get_usb_devices_connected()
    # start screen recording
    def start_screen_recording():
      self.dut.adb.shell(
          'screenrecord /sdcard/test_default_jca_ip_screen_recording.mp4')
    self.thread = threading.Thread(target=start_screen_recording)
    self.thread.start()

  def teardown_test(self):
    ui_interaction_utils.force_stop_app(
        self.dut, _JETPACK_CAMERA_APP_PACKAGE_NAME
    )
    try:
      self.dut.adb.shell(['pkill', '-SIGINT', 'screenrecord'])
    except adb.AdbError as e:
      logging.debug('Could not kill screenrecord process: %s', e)
    if self.thread:
      self.thread.join()

    self.dut.adb.pull(['/sdcard/test_default_jca_ip_screen_recording.mp4',
                       self.log_path])

    if self.rotator_cntl == 'gen2_rotator':
      # Release the serial ports properly after the test
      motor_port = gen2_rig_controller_utils.find_serial_port(self.rotator_cntl)
      if motor_port:
        motor_port.close()
    if self.lighting_cntl == 'gen2_lights':
      # Lights will go back to default state after the test
      lights_port = gen2_rig_controller_utils.find_serial_port(
          self.lighting_cntl
      )
      if lights_port:
        lights_port.close()

  def on_fail(self, record):
    super().on_fail(record)
    self.dut.take_screenshot(self.log_path, prefix='on_test_fail')

  def test_default_jca_capture_ip(self):
    """Check default camera and JCA app image consistency."""

    with its_session_utils.ItsSession(
        device_id=self.dut.serial,
        camera_id=self.camera_id,
        hidden_physical_id=self.hidden_physical_id) as cam:
      props = cam.get_camera_properties()
      props = cam.override_with_hidden_physical_camera_props(props)
      if (props['android.lens.facing']
          == camera_properties_utils.LENS_FACING['FRONT']):
        camera_facing = 'front'
      else:
        camera_facing = 'rear'
      logging.debug('Camera facing: %s', camera_facing)
      camera_hardware_level = _CAMERA_HARDWARE_LEVEL_MAPPING[
          props.get('android.info.supportedHardwareLevel')
      ]
      logging.debug('Camera hardware level: %s', camera_hardware_level)
      # logging for data collection
      print(f'{_NAME}_camera_hardware_level: {camera_hardware_level}')
      first_api_level = its_session_utils.get_first_api_level(self.dut.serial)
      is_dut_tablet_or_desktop = its_device_utils.is_dut_tablet_or_desktop(
          self.dut.serial)

      # Skip the test if camera is not primary or if it is a tablet
      is_primary_camera = self.hidden_physical_id is None
      camera_properties_utils.skip_unless(
          not is_dut_tablet_or_desktop and
          is_primary_camera and
          first_api_level >= its_session_utils.ANDROID16_API_LEVEL
      )
      device_id = self.dut.serial

      # Set up gen2 rig controllers
      if self.rotator_cntl == 'None' or self.lighting_cntl == 'None':
        logging.debug('Gen2 rig is not available.')
        self.motor_port = None
        self.motor_channel = None
      else:
        self._setup_gen2rig()

      # Get default camera app pkg name
      pkg_name = cam.get_default_camera_pkg()
      logging.debug('Default camera pkg name: %s', pkg_name)
      camera_ids = cam.get_camera_ids()
      primary_rear_cam = camera_ids.get('primaryRearCameraId')
      primary_front_cam = camera_ids.get('primaryFrontCameraId')
      flip_camera = True
      logging.debug('Camera ids on device: %s', camera_ids)
      if primary_rear_cam is None or primary_front_cam is None:
        logging.debug('Device only has one primary camera')
        flip_camera = False

      if camera_facing == 'front':
        camera_properties_utils.skip_unless(self.camera_id == primary_front_cam)
      if camera_facing == 'rear':
        camera_properties_utils.skip_unless(self.camera_id == primary_rear_cam)

      ui_interaction_utils.default_camera_app_dut_setup(device_id, pkg_name)

      # Launch ItsTestActivity
      its_device_utils.start_its_test_activity(device_id)
      if self.dut.ui(text='OK').wait.exists(
          timeout=ui_interaction_utils.WAIT_INTERVAL_FIVE_SECONDS
      ):
        self.dut.ui(text='OK').click.wait()

      # Ensure that the device is orthogonal and then close camera
      if self.motor_port is not None:
        gen2_rig_controller_utils.rotate_to_orthogonal_position(
            cam, self.log_path, self.motor_port, self.motor_channel)
      cam.close_camera()
      ui_interaction_utils.force_stop_app(
          self.dut, its_device_utils.CTS_VERIFIER_PKG
      )
      time.sleep(_CLOSE_CAMERA_WAIT_TIME_SECONDS)

      # Take capture with default camera app
      device_img_path = ui_interaction_utils.launch_and_take_capture(
          dut=self.dut,
          pkg_name=pkg_name,
          camera_facing=camera_facing,
          log_path=self.log_path,
          flip_camera=flip_camera
      )
      ui_interaction_utils.pull_img_files(
          device_id, device_img_path, self.log_path
      )
      time.sleep(_CLOSE_CAMERA_WAIT_TIME_SECONDS)  # wait for camera to close

      # Check if the image captured is HDR or not
      its_device_utils.start_its_test_activity(device_id)
      cam.__init__(device_id=self.dut.serial, camera_id=self.camera_id,
                   hidden_physical_id=self.hidden_physical_id)
      gainmap_present = cam.check_gain_map_present(device_img_path)
      logging.debug('gainmap_present: %s', gainmap_present)

      default_img_name = pathlib.Path(device_img_path).name
      default_path = os.path.join(self.log_path, default_img_name)
      logging.debug('Default capture img name: %s', default_img_name)
      default_capture_path = pathlib.Path(default_path)
      default_capture_path = default_capture_path.with_name(
          f'{default_capture_path.stem}_default{default_capture_path.suffix}'
      )
      os.rename(default_path, default_capture_path)
      # Get the zoomRatio value used by default camera app
      default_watch_dump_file = os.path.join(
          self.log_path,
          ui_interaction_utils.DEFAULT_CAMERA_WATCH_DUMP_FILE
      )
      jca_ar = get_jca_ar(default_capture_path)
      zoom_method = ui_interaction_utils.get_default_camera_zoom_method(
          default_watch_dump_file)
      logging.debug('Default camera app uses %s to control the zoom.',
                    zoom_method)
      zoom_ratio = 1.0
      if zoom_method == 'cropRegion':
        scaler_crop_region = (
            ui_interaction_utils.get_default_camera_crop_region(
                default_watch_dump_file)
        )
        zoom_ratio = ip_metrics_utils.derive_hal_zoom_ratio(
            props, scaler_crop_region)
      else:
        zoom_ratio = ui_interaction_utils.get_default_camera_zoom_ratio(
            default_watch_dump_file)
      logging.debug('Default camera captures zoomRatio value: %s', zoom_ratio)

      jca_zoom_ratio = None
      if zoom_ratio != 1.0:
        jca_zoom_ratio = zoom_ratio
      video_stabilization = None
      video_stabilization_mode = (
          ui_interaction_utils.get_default_camera_video_stabilization(
              default_watch_dump_file)
      )
      if video_stabilization_mode == 'OFF':
        # Check if device has OIS enabled
        ois_enabled = (
            ui_interaction_utils.get_default_camera_ois_mode(
                default_watch_dump_file)
        )
        if ois_enabled == 'ON':
          video_stabilization = (
              ui_interaction_utils.JCA_VIDEO_STABILIZATION_MODE_OPTICAL
          )
        else:
          video_stabilization = (
              ui_interaction_utils.JCA_VIDEO_STABILIZATION_MODE_OFF
          )
      elif video_stabilization_mode == 'PREVIEW_STABILIZATION':
        video_stabilization = (
            ui_interaction_utils.JCA_VIDEO_STABILIZATION_MODE_ON
        )
      elif video_stabilization_mode == 'ON':
        video_stabilization = (
            ui_interaction_utils.JCA_VIDEO_STABILIZATION_MODE_HIGH_QUALITY
        )
      else:
        logging.warning(
            'Unexpected video stabilization mode: %s. Leaving JCA stabilization'
            ' as default.', video_stabilization_mode
        )
      # Take JCA capture with UI
      ui_interaction_utils.force_stop_app(
          self.dut, its_device_utils.CTS_VERIFIER_PKG
      )
      time.sleep(_CLOSE_CAMERA_WAIT_TIME_SECONDS)

      jca_capture_path, _ = ui_interaction_utils.launch_jca_and_capture(
          self.dut,
          self.log_path,
          camera_facing=props['android.lens.facing'],
          zoom_ratio=jca_zoom_ratio,
          video_stabilization=video_stabilization,
          jca_aspect_ratio=jca_ar,
          enable_hdr=gainmap_present
      )
      ui_interaction_utils.pull_img_files(
          device_id, jca_capture_path, self.log_path
      )
      img_name = pathlib.Path(jca_capture_path).name
      jca_path = os.path.join(self.log_path, img_name)
      logging.debug('JCA capture img name: %s', img_name)
      jca_capture_path = pathlib.Path(jca_path)
      jca_capture_path = jca_capture_path.with_name(
          f'{jca_capture_path.stem}_jca{jca_capture_path.suffix}'
      )
      os.rename(jca_path, jca_capture_path)

      # Decompress UltraHDR image to RGB image
      if gainmap_present:
        default_capture_rgb_path = default_capture_path.with_name(
            f'{default_capture_path.stem}_rgb{default_capture_path.suffix}'
        )
        image_processing_utils.decompress_ultrahdr_image(
            default_capture_path, default_capture_rgb_path
        )
        jca_capture_rgb_path = jca_capture_path.with_name(
            f'{jca_capture_path.stem}_rgb{jca_capture_path.suffix}'
        )
        image_processing_utils.decompress_ultrahdr_image(
            jca_capture_path, jca_capture_rgb_path
        )
        default_capture_path = default_capture_rgb_path
        jca_capture_path = jca_capture_rgb_path
        logging.debug('Decompressed UltraHDR image to RGB image')

      # Extract FULL_CHART from the captured image.
      _, _ = (
          ce.get_feature_from_image(
              default_capture_path,
              'default_full_chart',
              self.log_path,
              pd.TestChartFeature.FULL_CHART,
          )
      )

      _, _ = ce.get_feature_from_image(
          jca_capture_path,
          'jca_full_chart',
          self.log_path,
          pd.TestChartFeature.FULL_CHART,
      )

      default_qr_code, _ = ce.get_feature_from_image(
          default_capture_path,
          'default_qr_code',
          self.log_path,
          pd.TestChartFeature.CENTER_QR_CODE,
      )

      jca_qr_code, _ = ce.get_feature_from_image(
          jca_capture_path,
          'jca_qr_code',
          self.log_path,
          pd.TestChartFeature.CENTER_QR_CODE,
      )

      logging.debug('Checking if FoV match between default and jca captures')
      default_fov = ip_metrics_utils.get_fov_in_degrees(
          default_capture_path, default_qr_code, self.chart_distance)
      logging.debug('Default camera FoV: %.2f', default_fov)

      jca_fov = ip_metrics_utils.get_fov_in_degrees(
          jca_capture_path, jca_qr_code, self.chart_distance)
      logging.debug('JCA camera FoV: %.2f', jca_fov)
      fov_match = True
      if not math.isclose(
          default_fov, jca_fov, rel_tol=ip_metrics_utils.FOV_REL_TOL):
        fov_match = False

      logging.debug(
          'Default and JCA FOV difference within tolerance: %s.\n '
          'Expected: %s, Actual: %s', fov_match,
          ip_metrics_utils.FOV_REL_TOL,
          abs(default_fov - jca_fov) / max(abs(default_fov), abs(jca_fov))
      )
      # logging for data collection
      print(f'{_NAME}_fov_match: {fov_match}')

      # Get cropped dynamic range patch cells
      default_dynamic_range_patch_cells = (
          ce.get_cropped_dynamic_range_patch_cells(
              default_capture_path, self.log_path, 'default')
      )
      jca_dynamic_range_patch_cells = ce.get_cropped_dynamic_range_patch_cells(
          jca_capture_path, self.log_path, 'jca'
      )
      e_msg = []

      # Get brightness diff between default and jca captures
      mean_brightness_diff = ip_metrics_utils.do_brightness_check(
          default_dynamic_range_patch_cells, jca_dynamic_range_patch_cells
      )

      # logging for data collection
      print(f'{_NAME}_mean_brightness_diff: {mean_brightness_diff}')
      logging.debug('mean_brightness_diff: %f', mean_brightness_diff)
      if abs(mean_brightness_diff) > _BRIGHTNESS_DIFF_THRESHOLD:
        e_msg.append('Device fails the brightness difference criteria.')

      marginal_brightness_pass = False
      marginal_pass_msg = []
      if (abs(mean_brightness_diff) > (
          _BRIGHTNESS_DIFF_THRESHOLD * its_session_utils.MARGINAL_PASS_FACTOR)
          and
          abs(mean_brightness_diff) <= (_BRIGHTNESS_DIFF_THRESHOLD)):
        marginal_brightness_pass = True
        marginal_pass_msg.append('Marginally passing brightness check.')

      # Get white balance diff between default and jca captures
      mean_white_balance_diff = ip_metrics_utils.do_white_balance_check(
          default_dynamic_range_patch_cells, jca_dynamic_range_patch_cells,
          'default', 'jca'
      )

      marginal_while_balance_pass = False
      if (abs(mean_white_balance_diff) > (
          _AWB_DIFF_THRESHOLD * its_session_utils.MARGINAL_PASS_FACTOR)
          and
          abs(mean_white_balance_diff) <= (_AWB_DIFF_THRESHOLD)):
        marginal_while_balance_pass = True
        marginal_pass_msg.append('Marginally passing white balance diff check.')

      # Get cropped color cells
      default_cropped_color_cells = ce.get_cropped_color_cells(
          default_capture_path, self.log_path, 'default')

      jca_cropped_color_cells = ce.get_cropped_color_cells(
          jca_capture_path, self.log_path, 'jca')

      mean_delta_ab_diff, _ = ip_metrics_utils.get_color_rendering_variation(
          default_cropped_color_cells, jca_cropped_color_cells, gainmap_present)
      # logging for data collection
      print(f'{_NAME}mean_delta_ab_diff: {mean_delta_ab_diff}')
      logging.debug('mean_delta_ab_diff: %.2f', mean_delta_ab_diff)
      if abs(mean_delta_ab_diff) > _COLOR_DIFF_THRESHOLD:
        e_msg.append('Device fails the color rendering difference criteria.')
      # Check for marginal pass for color rendering check
      marginal_color_pass = False
      if (abs(mean_delta_ab_diff) > (
          _COLOR_DIFF_THRESHOLD * its_session_utils.MARGINAL_PASS_FACTOR)
          and
          abs(mean_delta_ab_diff) <= (_COLOR_DIFF_THRESHOLD)):
        marginal_color_pass = True
        marginal_pass_msg.append('Marginally passing color rendering check.')
      # logging for data collection
      print(f'{_NAME}_mean_white_balance_diff: {mean_white_balance_diff}')
      logging.debug('mean_white_balance_diff: %f', mean_white_balance_diff)
      if abs(mean_white_balance_diff) > _AWB_DIFF_THRESHOLD:
        e_msg.append('Device fails the white balance difference criteria.')
      if not fov_match:
        e_msg.append('Device fails the FOV match check.')

      if e_msg:
        if first_api_level <= its_session_utils.ANDROID17_API_LEVEL:
          raise AssertionError(
              f'{its_session_utils.NOT_YET_MANDATED_MESSAGE}\n\n{e_msg}')
        else:
          raise AssertionError(e_msg)  # Checks mandated starting Android 17
      else:  # Check for marginal pass
        if (marginal_brightness_pass or marginal_while_balance_pass or
            marginal_color_pass):
          logging.warning('%s\n %s', its_session_utils.MARGINAL_PASSING_MESSAGE,
                          marginal_pass_msg)

if __name__ == '__main__':
  test_runner.main()
