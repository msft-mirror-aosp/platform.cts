#!/usr/bin/env python3
# Lint as: python3
"""
Base class for setting up devices for CDM functionalities.
"""

from mobly import base_test
from mobly import utils
from mobly.controllers import android_device
from time import sleep

CDM_SNIPPET_PACKAGE = 'android.companion.cts.multidevice'

BT_DISCOVERABLE_TIME = 15
OPERATION_DELAY_TIME = 5

class BaseTestClass(base_test.BaseTestClass):

    def setup_class(self):
        # Declare that two Android devices are needed.
        self.primary, self.secondary = self.register_controller(
            android_device, min_number=2)

        def _setup_device(device):
            device.load_snippet('cdm', CDM_SNIPPET_PACKAGE)
            # Enable bluetooth and enable receivers
            device.cdm.btEnable()

            # Clean up existing associations
            device.cdm.disassociateAll()

            # Clear bluetooth bonds
            self.clear_bonded_devices(device)

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
        self.primary.cdm.disassociateAll()
        self.secondary.cdm.disassociateAll()

        self.clear_bonded_devices(self.primary)
        self.clear_bonded_devices(self.secondary)

    def clear_bonded_devices(self, ad: android_device.AndroidDevice):
        """Remove bluetooth bonds"""
        paired_devices = ad.cdm.btGetPairedDevices()
        for device in paired_devices:
            ad.cdm.btUnpairDevice(device['Address'])
            sleep(OPERATION_DELAY_TIME)

    def _execute_on_devices(self, func, raise_on_exception=True):
        # Executes a function on both primary and secondary devices concurrently to same time
        utils.concurrent_exec(
            func,
            ((self.primary,), (self.secondary,)),
            max_workers=2,
            raise_on_exception=raise_on_exception)
