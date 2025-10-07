#!/usr/bin/env python3
# Lint as: python3
"""
Utility class for checking CDM API flags on test devices.
"""

from mobly import asserts
from mobly.controllers import android_device
from mobly.tools import device_flags

NAMESPACE = 'companion'

def assume_enabled(ad: android_device.AndroidDevice, package_name: str, flag_name: str):
    """Assume that a CDM API flag is enabled on the android device.

    If the device is either missing the flag or the flag is disabled, then skip this test.
    The flag name must omit the flag namespace and package name.

    Args:
        ad: android device controller
        package_name: package name of the API flag to assume enabled.
        flag_name: name of the API flag to assume enabled.
    """
    flags = device_flags.DeviceFlags(ad)
    enabled = flags.get_value(NAMESPACE, f'{package_name}.{flag_name}')
    asserts.skip_if(not enabled, f'{package_name}.{flag_name} must be enabled for this test.')
