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
            device.adb.shell('input keyevent KEYCODE_WAKEUP')
            device.adb.shell('input keyevent KEYCODE_MENU')
            device.adb.shell('input keyevent KEYCODE_HOME')

            # Enable bluetooth and enable receivers
            device.cdm.btEnable()

            # Clean up existing associations
            device.cdm.disassociateAll()

            # Clear bluetooth bonds
            self.clear_bonded_devices(device)


        # Sets up devices in parallel to save time.
        utils.concurrent_exec(
            _setup_device,
            ((self.primary,), (self.secondary,)),
            max_workers=2,
            raise_on_exception=True)

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
