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
"""Utility functions to manage and interact with devices for ITS."""

import logging
import os
import subprocess
import time

from snippet_uiautomator import uiautomator


ITS_TEST_ACTIVITY = 'com.android.cts.verifier/.camera.its.ItsDefaultTestActivity'
ITS_SENSOR_FUSION_ACTIVITY = 'com.android.cts.verifier/.camera.its.SensorFusionTestActivity'
CAMERA_ITS_SENSOR_FUSION_TEST_TEXT = 'Camera ITS Sensor Fusion Rig Test'
CTS_VERIFIER_PKG = 'com.android.cts.verifier'
SYSTEM_USER = '0'
WAIT_TIME_SEC = 5
CAMERA_ITS_TEST_TEXT = 'Camera ITS Test'
NAVIGATE_UP_DESCRIPTION = 'Navigate up'
RPC_TIMEOUT_SEC = 180

def run(cmd):
  """Replacement for os.system, with hiding of stdout+stderr messages.

  Args:
    cmd: Command to be executed in string format.
  """
  with open(os.devnull, 'wb') as devnull:
    subprocess.check_call(cmd.split(), stdout=devnull, stderr=subprocess.STDOUT)


def run_adb_shell_command(device_id, command):
  """Run adb shell command on device.

  Args:
    device_id: serial id of device.
    command: adb command to run on device.
  Returns:
    output: adb command output
  Raises:
    RuntimeError: An error when running adb command.
  """
  adb_command = f'adb -s {device_id} shell {command}'
  output = subprocess.run(adb_command, capture_output=True, shell=True,
                          check=False)
  if 'Exception occurred' in str(output):
    raise RuntimeError(output)
  return output


def is_dut_tablet_or_desktop(device_id):
  """Checks if the dut is tablet or desktop.

  Args:
    device_id: serial id of device under test
  Returns:
    True, if the device under test is a tablet.
    False otherwise.
  """
  adb_command = 'getprop ro.build.characteristics'
  output = run_adb_shell_command(device_id, adb_command)
  logging.debug('adb command output: %s', output)
  if output is not None and (
      ('tablet' in str(output).lower()) or
      ('desktop' in str(output).lower())
  ):
    logging.debug('Device under test is a tablet/desktop.')
    return True
  logging.debug('Device under test is a phone')
  return False


def start_its_test_activity(device_id):
  """Starts ItsTestActivity, waking the device if necessary.

  Args:
    device_id: str; ID of the device.
  """
  start_its_test_activity_with_name(device_id, ITS_TEST_ACTIVITY)


def start_its_test_activity_with_name(device_id, activity_name):
  """Starts the specified test activity, waking the device if necessary.

  Args:
    device_id: str; ID of the device.
    activity_name: str; name of the activity
  """
  run(f'adb -s {device_id} shell input keyevent KEYCODE_WAKEUP')
  run(f'adb -s {device_id} shell input keyevent KEYCODE_MENU')
  run(f'adb -s {device_id} shell input keyevent KEYCODE_ESCAPE')
  run(f'adb -s {device_id} shell am start -n '
      f'{activity_name} --activity-brought-to-front '
      '--activity-reorder-to-front')


def get_current_user(device_id):
  """Returns the current user on the device."""
  adb_command = 'am get-current-user'
  output = run_adb_shell_command(
      device_id, adb_command).stdout.decode('utf-8').strip()
  logging.debug('Current user: %s', output)
  return output


def click_on_app_icon(dut, log_path, scene_name):
  """Clicks on the Camera ITS app icon.

    In order to click on the app icon, we need to find the app in the scrollable
    menu and then click on it.

    Args:
      dut: android_device object.
      log_path: Path to save screenshot if setup fails.
      scene_name: Name of the scene.
  """
  if scene_name == 'sensor_fusion' or scene_name == 'feature_combination':
    activity_name = ITS_SENSOR_FUSION_ACTIVITY
    text_name = CAMERA_ITS_SENSOR_FUSION_TEST_TEXT
  else:
    activity_name = ITS_TEST_ACTIVITY
    text_name = CAMERA_ITS_TEST_TEXT

  dut.services.register(
      uiautomator.ANDROID_SERVICE_NAME, uiautomator.UiAutomatorService
  )
  # Increase the RPC timeout to 3 minutes for slow UI operations.
  original_timeout = dut.ui._ui._conn.gettimeout()
  dut.ui._ui._conn.settimeout(RPC_TIMEOUT_SEC)
  try:
    dut.adb.shell(['input', 'keyevent', 'KEYCODE_WAKEUP'])
    dut.adb.shell(['input', 'keyevent', 'KEYCODE_MENU'])
    # Dismiss keyguard and wait a moment for the system to settle
    dut.adb.shell(['wm', 'dismiss-keyguard'])
    time.sleep(WAIT_TIME_SEC)

    # First, try to start the activity directly as it's more robust.
    # Note: --activity-brought-to-front and --activity-reorder-to-front are used
    # to ensure the activity is shown if it was already running in the background.
    logging.debug('Attempting to start %s directly.', activity_name)
    dut.adb.shell(['am', 'start', '-n', activity_name,
                   '--activity-brought-to-front',
                   '--activity-reorder-to-front'])
    time.sleep(WAIT_TIME_SEC)

    # Check if already on the correct page.
    if dut.ui(text=text_name).wait.exists(WAIT_TIME_SEC) and \
       dut.ui(desc=NAVIGATE_UP_DESCRIPTION).wait.exists(WAIT_TIME_SEC):
      logging.debug('Successfully launched %s directly.', activity_name)
      dut.ui(desc=NAVIGATE_UP_DESCRIPTION).click()
      time.sleep(WAIT_TIME_SEC)

    scrollable_menu = dut.ui(scrollable=True)
    if scrollable_menu.wait.exists(WAIT_TIME_SEC):
      logging.debug('Scrolling down to find CameraITS')
      if dut.ui(text=text_name).exists:
        found = True
      else:
        found = scrollable_menu.scroll.down(text=text_name)

      if not found:
        dut.take_screenshot(log_path, prefix='failed_to_find_CameraITS')
        logging.error('Failed to find Camera ITS Test icon in the menu.')
      else:
        logging.debug('Found CameraITS icon, clicking.')
        dut.ui(text=text_name).click()
        time.sleep(WAIT_TIME_SEC)
  finally:
    dut.ui._ui._conn.settimeout(original_timeout)


def pull_file_from_content_provider(
    device_id, content_location, directory_on_host, file_name):
  """Searches and pulls a file from the content provider given a file name.

  If file name is not provided, the base name of the file on host is used.
  This function is a workaround for HSUM devices that do not support adb pull
  as system user 0.

  Args:
    device_id: serial id of device.
    content_location: content provider location.
    file_path_on_host: path to store the file on host.
    file_name: name of the file to pull. If not provided, the base name of the
      file on host will be used.
  Returns:
    URI of the file from the content provider.
  Raises:
    ValueError: If the file is not found in the content provider output.
  """
  current_user_id = get_current_user(device_id)
  adb_command = (
      f'content query --user {current_user_id} '
      f'--uri {content_location} --projection _id:_data:datetaken '
      '--sort \\"datetaken DESC\\"'
  )
  output = run_adb_shell_command(
      device_id, adb_command).stdout.decode('utf-8').strip()
  for line in output.splitlines():
    if file_name.lower() in line.lower():
      logging.debug(
          'Found file %s in content provider output: %s', file_name, line
      )
      content_id = line.split('_id=')[1].split(',')[0]
      file_path_on_device = line.split('_data=')[1].split(',')[0]
      file_name_on_device = os.path.basename(file_path_on_device)
      file_path_on_host = os.path.join(directory_on_host, file_name_on_device)
      adb_command = (
          f'content read --uri {content_location}/{content_id} '
          f'--user {current_user_id} > {file_path_on_host}'
      )
      run_adb_shell_command(device_id, adb_command)
      return f'{content_location}/{content_id}'
  raise ValueError(
      f'File {file_name} not found in content provider output: {output}')
