#!/usr/bin/env python3
# Lint as: python3
"""
Base class for setting up devices for CDM functionalities.
"""

from mobly import base_test
from mobly import utils
from mobly.controllers import android_device
from test_utils import wait
from time import sleep

CDM_SNIPPET_PACKAGE = 'android.companion.cts.multidevice'

BT_DISCOVERABLE_TIME = 15
OPERATION_DELAY_TIME = 5

def paired_devices(self):
    return map(lambda device: device['Address'], self.cdm.btGetPairedDevices())

def clear_bonds(self):
    for device in self.paired_devices():
        try:
            self.cdm.btUnpairDevice(device)
            wait(lambda: device.address not in self.paired_devices())
        except Exception as e:
            pass

class BaseTestClass(base_test.BaseTestClass):

    def setup_class(self):
        android_device.AndroidDevice.paired_devices = paired_devices
        android_device.AndroidDevice.clear_bonds = clear_bonds

        # Declare that two Android devices are needed.
        self.primary, self.secondary = self.register_controller(
            android_device, min_number=2)

        def _setup_device(device):
            device.load_snippet('cdm', CDM_SNIPPET_PACKAGE)
            # Enable bluetooth and enable receivers
            device.cdm.btEnable()
            device.address = device.cdm.btGetAddress()

            # Clean up existing associations
            device.cdm.disassociateAll()

            # Clear bluetooth bonds
            device.clear_bonds()

        self._execute_on_devices(_setup_device)

    def setup_test(self):

        def _setup_device(device):
            # Touch the screen to make sure the device is wake up for each test run.
            device.adb.shell('input keyevent KEYCODE_WAKEUP')
            device.adb.shell('input keyevent KEYCODE_MENU')
            device.adb.shell('input keyevent KEYCODE_HOME')

        self._execute_on_devices(_setup_device)

    def teardown_test(self):
        """Clean up tests"""

        def _teardown_device(device):
            # Remove all associations
            device.cdm.disassociateAll()

            # Clear bluetooth bonds
            device.clear_bonds()

        self._execute_on_devices(_teardown_device)

    def bt_pair_devices(self):
        """Pair two devices using BT classic bond"""
        self.primary.cdm.btBecomeDiscoverable(BT_DISCOVERABLE_TIME)
        self.primary.cdm.btStartAutoAcceptIncomingPairRequest()
        self.secondary.cdm.btDiscoverAndGetResults()
        self.secondary.cdm.btPairDevice(self.primary.address)
        wait(lambda: self.secondary.address in self.primary.paired_devices())

    def bt_unpair_devices(self):
        """Unpair two devices connected with BT classic bond"""
        self.secondary.cdm.btUnpairDevice(self.primary.address)
        wait(lambda: self.secondary.address not in self.primary.paired_devices())

    def _execute_on_devices(self, func, raise_on_exception=True):
        # Executes a function on both primary and secondary devices concurrently to same time
        utils.concurrent_exec(
            func,
            ((self.primary,), (self.secondary,)),
            max_workers=2,
            raise_on_exception=raise_on_exception)

