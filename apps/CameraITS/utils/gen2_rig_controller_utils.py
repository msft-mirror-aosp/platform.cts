# Copyright 2025 The Android Open Source Project
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
"""Utility functions for gen2 rig hardware."""

import logging
import os
import struct
import subprocess
import time

import capture_request_utils
import cv2
import image_processing_utils
import ip_chart_extraction_utils as ce
import ip_chart_pattern_detector as pd
import numpy as np
import pyudev
import sensor_fusion_utils
import serial


# baudrates used for lights and servo controllers
_ARDUINO_BAUDRATE = 9600  # baudrate as set in firmware
_ROTATOR_BAUDRATE = 115200  # baudrate as set in firmware
_MAX_CHANNEL_ID = 5

# servo controller commands
_LSS_COMMAND_START = '#'
_LSS_COMMAND_END = '\r'
_LSS_CONFIG_MAX_SPEED_RPM = 'CSR'
_LSS_CONFIG_ANGULAR_STIFFNESS = 'CAS'
_LSS_CONFIG_ANGULAR_HOLDING_STIFFNESS = 'CAH'
_LSS_CONFIG_ANGULAR_ACCELERATION = 'CAA'
_LSS_CONFIG_ANGULAR_DECELERATION = 'CAD'
_LSS_ACTION_MOVE = 'D'
_LSS_ACTION_RELATIVE_MOVE = 'MD'
_LSS_ACTION_HOLD = 'H'
_LSS_ACTION_LIMP = 'L'
_LSS_CONFIG_FILTER_POSITION_COUNT = 'CFPC'
_LSS_MODIFIER_TIMED = 'T'
_LSS_ACTION_QUERY_POSITION = 'QD'

# servo controller configuration
_DEFAULT_MAX_SPEED_RPM = 45
_DEFAULT_ANGULAR_STIFFNESS = -10
_DEFAULT_ANGULAR_HOLDING_STIFFNESS = -10
_DEFAULT_ANGULAR_ACCELERATION = 35
_DEFAULT_ANGULAR_DECELERATION = 20
_DEFAULT_FILTER_POSITION_COUNT = 5

# servo controller configuration for sensor fusion (SF)
_DEFAULT_MAX_SPEED_RPM_SF = 12
_ANGULAR_ACCELERATION_STABILIZATION = 30
_ANGULAR_DECELERATION_STABILIZATION = 30
_ANGULAR_ACCELERATION_SF = 5
_ANGULAR_DECELERATION_SF = 5
_ANGULAR_STIFFNESS_SF = -6
_ANGULAR_HOLDING_STIFFNESS_SF = -4
_FILTER_POSITION_COUNT_SF = 2
_OVERSHOOT_ANGLE_SF = 5
_OVERSHOOT_LIMP_TIME = 0.05
_WAIT_TIME_SF = 0.25
_MOVE_TIME_SF = 0.35

# Position of origin.
_POSITION_0_DEGREE = '0'
_SERVO_ANGLE_SCALE_FACTOR = 10
_MIN_SERVO_POSITION = -180
_MAX_SERVO_POSITION = 180

# Orthogonal rotation constants
_ANGLE_DIFF_THRESHOLD = 0.5  # degrees
_ORTHOGONAL_ANGLE = 90  # degrees
_ORTHOGONAL_CAPTURE_FORMAT_STR = 'yuv'
_ORTHOGONAL_POSITION_MAX_TRIES = 5
_ROTATION_WAIT_TIME = 3  # seconds

_ARDUINO_BRIGHTNESS_MAX = 1
_ARDUINO_BRIGHTNESS_MIN = 0
_ARDUINO_LIGHT_START_BYTE = 100
_ARDUINO_CMD_LENGTH = 3
_WAIT_FOR_ROTATOR_MOVEMENT = 2
_WAIT_FOR_CMD_COMPLETION = 1
_WAIT_FOR_CONFIG_COMPLETION = 0.2

# Constants for strings used to find serial port
_ARDUINO_STR = 'Arduino'
_LIGHTS_STR = 'lights'
_ROTATOR_STR = 'rotator'
_STR_340 = '340'
_MEGA_STR = 'Mega'
DEFAULT_GEN2_ROTATOR_NAME = 'gen2_rotator'
DEFAULT_GEN2_LIGHTS_NAME = 'gen2_lights'


def _check_channel(channel):
  """Checks if the channel used is a valid number or not.

  Args:
    channel: int; channel id used in config file
  Raises:
    ValueError if the channel id is not valid
  """
  if not (channel <= _MAX_CHANNEL_ID):
    raise ValueError('Channel id is not valid.')


def _rotator_write(serial_port, channel, command, value=None):
  """Writes command to the rotator board.

  Args:
    serial_port: serial port to be used for communication
    channel: int; channel id for rotator
    command: List of bytes; the command send to the rotator board.
    value: Integer; the parameter value send to the rotator board.
  """
  tmp = f'{channel}{command}'
  if value is not None:
    tmp += str(value)
  msg = (f'{_LSS_COMMAND_START}{tmp}{_LSS_COMMAND_END}').encode()
  logging.debug('Writing message to rotator board: %s', msg)
  serial_port.write(msg)


def _get_angle_from_qr_code(qr_code):
  """Get the correction angle from the QR code."""
  alpha = qr_code[:, :, 3]
  y, x = np.nonzero(alpha > 0)
  points = np.column_stack((x, y))
  if not points.any():
    raise ValueError('No points found in QR code.')
  _, _, angle = cv2.minAreaRect(points)
  logging.debug('Using alpha channel angle %.2f', angle)
  return round(angle / _ORTHOGONAL_ANGLE) * _ORTHOGONAL_ANGLE - angle


def get_usb_devices_connected():
  """Checks and lists the details of USB devices.
  """
  devices = pyudev.Context()
  device_list = devices.list_devices(subsystem='tty', ID_BUS='usb')
  logging.debug('Getting the list of connected devices')
  for device in device_list:
    port = device['DEVNAME']
    logging.debug('Getting properties for device: %s', port)
    command = f'udevadm info -q property -n {port}'
    try:
      property_list = subprocess.check_output(command, shell=True)
      logging.debug('------Device %s properties-----', port)
      logging.debug(property_list)
      logging.debug('-------------------------------')
    except subprocess.CalledProcessError as error:
      logging.exception(error)


def find_serial_port(name):
  """Determine the serial port for gen2 rig controllers and open.

  serial port details: udevadm info -q property --name=<port-name>

  Args:
    name: str; string of device to locate (ie. 'gen2_motor', 'gen2_lights')
  Returns:
    serial port object
  """
  port_name = None
  devices = pyudev.Context()
  for device in devices.list_devices(subsystem='tty', ID_BUS='usb'):
    logging.debug('Checking device: %s', device)
    if _LIGHTS_STR in name:
      logging.debug('Finding serial port for lights')
      if _ARDUINO_STR in device['ID_VENDOR_FROM_DATABASE']:
        if _MEGA_STR in  device['ID_MODEL_FROM_DATABASE']:
          port_name = device['DEVNAME']
          logging.debug('Lighting controller port_name: %s', port_name)
          return serial.Serial(port_name, _ARDUINO_BAUDRATE, timeout=1)

    if _ROTATOR_STR in name:
      logging.debug('Finding serial port for rotator')
      if _STR_340 in device['ID_MODEL_FROM_DATABASE']:
        port_name = device['DEVNAME']
        logging.debug('Rotator controller port_name: %s', port_name)
        return serial.Serial(port_name, _ROTATOR_BAUDRATE, timeout=1)

  if port_name is None:
    logging.debug('Failed to find the serial port.')
    return None


def get_position(serial_port, channel):
  """Get the current position of the servo."""
  _rotator_write(serial_port, channel, _LSS_ACTION_QUERY_POSITION, value='')
  response = serial_port.readline().decode('utf-8').strip()
  logging.debug('Position response from rotator: %s', response)
  position = response.split(_LSS_ACTION_QUERY_POSITION)[-1]
  return int(position) / _SERVO_ANGLE_SCALE_FACTOR


def configure_rotator(serial_port, channel):
  """Configure rotator with default settings.

  Args:
    serial_port: serial port to be used for communication
    channel: int; channel used by rotator
  """
  _check_channel(channel)
  _set_max_speed_rpm(serial_port, channel, _DEFAULT_MAX_SPEED_RPM)
  _set_angular_stiffness(serial_port, channel, _DEFAULT_ANGULAR_STIFFNESS)
  _set_angular_holding_stiffness(serial_port, channel,
                                 _DEFAULT_ANGULAR_HOLDING_STIFFNESS)
  _set_angular_acceleration(serial_port, channel, _DEFAULT_ANGULAR_ACCELERATION)
  _set_angular_deceleration(serial_port, channel, _DEFAULT_ANGULAR_DECELERATION)


def _set_max_speed_rpm(serial_port, channel, value):
  _rotator_write(serial_port, channel, _LSS_CONFIG_MAX_SPEED_RPM, value)


def _set_angular_stiffness(serial_port, channel, value):
  _rotator_write(serial_port, channel, _LSS_CONFIG_ANGULAR_STIFFNESS, value)


def _set_angular_holding_stiffness(serial_port, channel, value):
  _rotator_write(serial_port, channel, _LSS_CONFIG_ANGULAR_HOLDING_STIFFNESS,
                 value)


def _set_angular_acceleration(serial_port, channel, value):
  _rotator_write(serial_port, channel, _LSS_CONFIG_ANGULAR_ACCELERATION, value)


def _set_angular_deceleration(serial_port, channel, value):
  _rotator_write(serial_port, channel, _LSS_CONFIG_ANGULAR_DECELERATION, value)


def _set_filter_position_count(serial_port, channel, value):
  _rotator_write(serial_port, channel, _LSS_CONFIG_FILTER_POSITION_COUNT, value)


def _move_to(serial_port, channel, position):
  _rotator_write(serial_port, channel, _LSS_ACTION_MOVE, position)
  # Wait for two seconds.
  time.sleep(_WAIT_FOR_ROTATOR_MOVEMENT)


def _move_to_timed(serial_port, channel, position, move_time):
  """Rotate servo to the specified direction in a timed fashion.

  Args:
    serial_port: obj; the serial port
    channel: int; channel used by rotator
    position: int; the position in degrees to rotate to
    move_time: float; time required to allow for movement in seconds
  """
  position_scaled = position * _SERVO_ANGLE_SCALE_FACTOR
  move_time_ms = int(move_time * 1000)
  value = f'{position_scaled}{_LSS_MODIFIER_TIMED}{move_time_ms}'
  _rotator_write(serial_port, channel, _LSS_ACTION_MOVE, value)


def _relative_move_to(serial_port, channel, degree):
  """Send command to move servo relatively by the specified degree.

  Args:
    serial_port: obj; the serial port
    channel: int; channel used by rotator
    degree: int; Amount in degrees to move the servo relatively
  """
  _rotator_write(serial_port, channel, _LSS_ACTION_RELATIVE_MOVE, degree)
  # Wait for two seconds.
  time.sleep(_WAIT_FOR_ROTATOR_MOVEMENT)


def relative_move(serial_port, channel, degree=0):
  """Move servo by the specified degree in the corresponding direction.

  Args:
    serial_port: serial port to be used for communication
    channel: int; channel used by rotator
    degree: float; Amount in degrees to move the servo
  """
  logging.debug('Moving servo %s relatively by %s degrees', channel, degree)
  _relative_move_to(
      serial_port, channel, int(round(degree * _SERVO_ANGLE_SCALE_FACTOR)))
  # Hold the angular position after movement
  _rotator_write(serial_port, channel, _LSS_ACTION_HOLD)


def rotate(serial_port, channel, position_degree=0):
  """Rotate servo to the specified direction.

  Args:
    serial_port: serial port to be used for communication
    channel: int; channel used by rotator
    position_degree: float; Position in degrees to move the servo
      Default position is set to 0 degrees which is the center position.
      A full circle is from -180 to 180 degrees.
      Positive value will move the servo in clockwise direction.
      Negative value will move the servo  in anti-clockwise direction.

  Returns:
    Command response.
  """
  if _MIN_SERVO_POSITION <= position_degree <= _MAX_SERVO_POSITION:
    if position_degree != 0:
      position_degree = int(position_degree * _SERVO_ANGLE_SCALE_FACTOR)
      position = str(position_degree)
    else:
      position = _POSITION_0_DEGREE
    logging.debug('Moving servo %s to position %s', channel, position)
    _move_to(serial_port, channel, position)
    response = f'Moving servo {channel} to direction {position_degree}'
    # Hold the angular position after movement
    _rotator_write(serial_port, channel, _LSS_ACTION_HOLD)
    return response
  else:
    logging.debug('Not a valid servo position: %s', position_degree)
    return None


def rotate_to_orthogonal_position(
    cam, log_path, motor_port, motor_channel):
  """Rotate servo to orthogonal position using center QR code angle.

  Args:
    cam: its_session_utils.ItsSession camera object
    log_path: str; path to save images
    motor_port: serial port to be used for communication
    motor_channel: int; channel used by rotator
  Raises:
    AssertionError: If motor failed to rotate to orthogonal position.
  """
  num_tries = 0
  while num_tries < _ORTHOGONAL_POSITION_MAX_TRIES:
    img = cam.do_orthogonal_position_capture()
    image_path = os.path.join(log_path, f'orthogonal_position_{num_tries}.jpg')
    image_processing_utils.write_image(img, image_path)

    default_qr_code, _ = ce.get_feature_from_image(
        image_path,
        'default_qr_code',
        log_path,
        pd.TestChartFeature.CENTER_QR_CODE,
    )
    cv2.imwrite(
        os.path.join(log_path, f'default_qr_code_{num_tries}.png'),
        default_qr_code
    )
    correction_angle = _get_angle_from_qr_code(default_qr_code)
    logging.debug(
        'QR code correction angle before moving: %s', correction_angle
    )
    if abs(correction_angle) < _ANGLE_DIFF_THRESHOLD:
      break
    relative_move(motor_port, motor_channel, round(correction_angle, 1))
    time.sleep(_ROTATION_WAIT_TIME)
    num_tries += 1
  else:
    raise AssertionError('Failed to rotate to orthogonal position.')


def rotation_rig_sensor_fusion(rotate_cntl, rotate_ch, num_rotations, angles):
  """Rotate the device for sensor fusion tests with discrete rotations.

  Args:
    rotate_cntl: str to identify 'gen2_rotator' controller.
    rotate_ch: str to identify rotation channel number.
    num_rotations: int number of rotations.
    angles: tuple of ints; (starting, ending) angles to move to.
  """
  starting_angle, ending_angle = angles
  logging.debug('angles: %s, %s', starting_angle, ending_angle)
  logging.debug('Controller: %s, ch: %s', rotate_cntl, rotate_ch)
  serial_port = find_serial_port(rotate_cntl)
  if not serial_port:
    raise AssertionError('Failed to find the serial port.')
  logging.debug('found serial port')
  channel = int(rotate_ch)
  _check_channel(channel)

  # configure motor
  _set_sensor_fusion_params(serial_port, channel)

  # initialize servo at starting angle
  logging.debug('Moving servo to starting position')
  _move_to(serial_port, channel, starting_angle * _SERVO_ANGLE_SCALE_FACTOR)
  get_position(serial_port, channel)

  # rotate phone
  for _ in range(num_rotations):
    _move_to(serial_port, channel, ending_angle * _SERVO_ANGLE_SCALE_FACTOR)
    get_position(serial_port, channel)
    _move_to(serial_port, channel, starting_angle * _SERVO_ANGLE_SCALE_FACTOR)
    get_position(serial_port, channel)

  # reset rotator parameters and move back to origin
  _reset_params_to_default(serial_port, channel)
  logging.debug('Finished rotations for sensor fusion, moving to origin')
  _move_to(serial_port, channel, 0)
  logging.debug(
      'Position after moving to origin: %s', get_position(serial_port, channel)
  )


def _set_stabilization_params(serial_port, channel):
  """Set parameters for the rotator for stabilization tests."""
  _set_max_speed_rpm(serial_port, channel, _DEFAULT_MAX_SPEED_RPM_SF)
  _set_angular_stiffness(serial_port, channel, _ANGULAR_STIFFNESS_SF)
  _set_angular_holding_stiffness(
      serial_port, channel, _ANGULAR_HOLDING_STIFFNESS_SF)
  _set_angular_acceleration(
      serial_port, channel, _ANGULAR_ACCELERATION_STABILIZATION)
  _set_angular_deceleration(
      serial_port, channel, _ANGULAR_DECELERATION_STABILIZATION)
  _set_filter_position_count(serial_port, channel, _FILTER_POSITION_COUNT_SF)


def _set_sensor_fusion_params(serial_port, channel):
  """Set parameters for the rotator for sensor fusion tests."""
  _set_max_speed_rpm(serial_port, channel, _DEFAULT_MAX_SPEED_RPM_SF)
  _set_angular_stiffness(serial_port, channel, _ANGULAR_STIFFNESS_SF)
  _set_angular_holding_stiffness(
      serial_port, channel, _ANGULAR_HOLDING_STIFFNESS_SF)
  _set_angular_acceleration(
      serial_port, channel, _ANGULAR_ACCELERATION_SF)
  _set_angular_deceleration(
      serial_port, channel, _ANGULAR_DECELERATION_SF)
  _set_filter_position_count(serial_port, channel, _FILTER_POSITION_COUNT_SF)


def _reset_params_to_default(serial_port, channel):
  """Reset parameters to default for the rotator."""
  _set_max_speed_rpm(serial_port, channel, _DEFAULT_MAX_SPEED_RPM)
  _set_angular_stiffness(serial_port, channel, _DEFAULT_ANGULAR_STIFFNESS)
  _set_angular_holding_stiffness(serial_port, channel,
                                 _DEFAULT_ANGULAR_HOLDING_STIFFNESS)
  _set_angular_acceleration(
      serial_port, channel, _DEFAULT_ANGULAR_ACCELERATION)
  _set_angular_deceleration(
      serial_port, channel, _DEFAULT_ANGULAR_DECELERATION)
  _set_filter_position_count(
      serial_port, channel, _DEFAULT_FILTER_POSITION_COUNT)
  time.sleep(_WAIT_FOR_CONFIG_COMPLETION)


def rotation_rig(rotate_cntl, rotate_ch, num_rotations, angles):
  """Rotate the phone n times using rotate_cntl and rotate_ch defined.

  rotate_ch is hard wired and must be determined from physical setup.
  If using Gen2 rig, serial port must be initialized and communication must be
  established before rotation.

  Note that these configuration parameters are derived based on
  trial and error. So make sure to experiment on the rotator when
  making any changes.

  Args:
    rotate_cntl: str to identify 'gen2_rotator' controller.
    rotate_ch: str to identify rotation channel number.
    num_rotations: int number of rotations.
    angles: list of ints; servo angle to move to.
  """

  logging.debug('Controller: %s, ch: %s', rotate_cntl, rotate_ch)
  if len(angles) != 2:
    raise ValueError(
        f'angles should contain 2 values, but it contains {len(angles)}')
  try:
    serial_port = find_serial_port(rotate_cntl)
    logging.debug('found serial port')
    channel = int(rotate_ch)
    _check_channel(channel)

    _set_stabilization_params(serial_port, channel)
    # initialize servo at starting angle
    logging.debug('Moving servo to starting position')
    _move_to(serial_port, channel, angles[0] * _SERVO_ANGLE_SCALE_FACTOR)

    # rotate phone
    for _ in range(num_rotations):
      # Move to target angle
      _move_to_timed(
          serial_port, channel, angles[1] + _OVERSHOOT_ANGLE_SF, _MOVE_TIME_SF)
      time.sleep(_WAIT_TIME_SF)
      # limp
      _rotator_write(serial_port, channel, _LSS_ACTION_LIMP)
      time.sleep(_OVERSHOOT_LIMP_TIME)
      # Move back to starting angle
      _move_to_timed(
          serial_port, channel, angles[0] - _OVERSHOOT_ANGLE_SF, _MOVE_TIME_SF)
      time.sleep(_WAIT_TIME_SF)
      # limp
      _rotator_write(serial_port, channel, _LSS_ACTION_LIMP)
      time.sleep(_OVERSHOOT_LIMP_TIME)
    logging.debug('Finished rotations')

    # reset rotator parameters and move back to origin
    _reset_params_to_default(serial_port, channel)
    logging.debug('Moving servo to origin')
    _move_to(serial_port, channel, 0)
  except Exception as e:
    logging.debug('An unexpected error occurred: %s', e)
    raise


def set_light_brightness(serial_port, channel, brightness_level, delay=0):
  """Set the light to specified brightness.

  Args:
    serial_port: object; serial port
    channel: str for lighting channel
    brightness_level: int value of brightness.
    delay: int; time in seconds
  """
  def to_char(digit):
    return digit + ord('0')

  cmd = [struct.pack('B', i) for i in [
      _ARDUINO_LIGHT_START_BYTE, to_char(channel), to_char(brightness_level)]]
  logging.debug('Lighting cmd: %s', cmd)
  for item in cmd:
    serial_port.write(item)
  time.sleep(delay)


def set_lighting_state(serial_port, channel, state):
  """Control the lights in gen2 rig.

  Args:
    serial_port: serial port object
    channel: str for lighting channel
    state: str 'ON/OFF'
  """
  logging.debug('Setting the lights state to: %s', state)
  if state == 'ON':
    level = 1
  elif state == 'OFF':
    level = 0
  else:
    raise ValueError(f'Lighting state not defined correctly: {state}')
  set_light_brightness(serial_port, channel, level,
                       delay=_WAIT_FOR_CMD_COMPLETION)


def setup_gen2_rig(rotator_ch, lighting_ch):
  """Set up the gen2 rig and establish communication with ports.

  Args:
    rotator_ch: str to identify rotation channel number.
    lighting_ch: str to identify lighting channel number.
  Returns:
    motor_port: serial port object for rotator
    lights_port: serial port object for lights
  """
  motor_channel = int(rotator_ch)
  lights_channel = int(lighting_ch)
  lights_port = find_serial_port(DEFAULT_GEN2_LIGHTS_NAME)
  if lights_port:
    sensor_fusion_utils.establish_serial_comm(lights_port)
    set_lighting_state(lights_port, lights_channel, 'ON')
  motor_port = find_serial_port(DEFAULT_GEN2_ROTATOR_NAME)
  if motor_port:
    configure_rotator(motor_port, motor_channel)
    rotate(motor_port, motor_channel)
  return motor_port, lights_port
