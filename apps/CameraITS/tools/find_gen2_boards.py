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
"""Script to find all Gen2 lights and rotators connected to a host."""
import logging
import gen2_rig_controller_utils
import pyudev

_ID_MODEL_KEY = 'ID_MODEL_FROM_DATABASE'
_PORT_NAME_KEY = 'DEVNAME'
_INITIALIZED_TIME_KEY = 'USEC_INITIALIZED'


def find_gen2_boards():
  """Finds all Gen2 lights and rotators connected to a host."""
  lights = []
  rotators = []
  devices = pyudev.Context()
  for device in devices.list_devices(subsystem='tty', ID_BUS='usb'):
    if gen2_rig_controller_utils.MEGA_STR in device.properties[_ID_MODEL_KEY]:
      lights.append(device)
    if gen2_rig_controller_utils.STR_340 in device.properties[_ID_MODEL_KEY]:
      rotators.append(device)
  if len(lights) != len(rotators):
    raise ValueError('Number of lights and rotators do not match. '
                     'Please make sure that equal numbers of lights and '
                     'rotators are connected.')
  lights.sort(key=lambda x: x.properties[_INITIALIZED_TIME_KEY])
  rotators.sort(key=lambda x: x.properties[_INITIALIZED_TIME_KEY])
  logging.info('Lights and rotators in order of initialization:')
  for light, rotator in zip(lights, rotators):
    logging.info(
        'Light port: %s, Rotator port: %s',
        light.properties[_PORT_NAME_KEY],
        rotator.properties[_PORT_NAME_KEY]
    )

if __name__ == '__main__':
  logging.basicConfig(level=logging.INFO)
  find_gen2_boards()
