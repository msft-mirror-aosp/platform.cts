#!/usr/bin/env python3
# Lint as: python3
"""
Utility class for various test usage.
"""

import time

from collections.abc import Callable
from mobly.controllers import android_device
from mobly import asserts

WAIT_DEFAULT_TIMEOUT = 5
WAIT_DEFAULT_POLLING_INTERVAL = 0.2

def wait(condition: Callable[[], bool], timeout: int = WAIT_DEFAULT_TIMEOUT, interval: int = WAIT_DEFAULT_POLLING_INTERVAL) -> bool:
    """
    Wait until condition becomes true before timing out.
    Return true if condition is met, and false otherwise.
    """
    start_time = time.time()
    while not condition():
        elapsed_time = time.time() - start_time
        if elapsed_time >= timeout:
            return False
        time.sleep(interval)
    return True


def assume_not_watch(device: android_device.AndroidDevice):
    """
    Check if device is a watch
    """
    asserts.skip_if(device.cdm.isWatch(), 'Cannot create association as a watch.')


def assert_build_types_match(primary: android_device.AndroidDevice, secondary: android_device.AndroidDevice):
    """
    Check if both devices are on the same build type (debuggable vs prod)
    """
    primary_debuggable = primary.build_info['debuggable'] == '1'
    secondary_debuggable = secondary.build_info['debuggable'] == '1'
    asserts.assert_equal(primary_debuggable, secondary_debuggable, 'Both devices must be on the same type of build')


def assert_attestation_verified(primary: android_device.AndroidDevice, secondary: android_device.AndroidDevice):
    """
    Check if device attestation can be verified by peer device
    """
    primary_debuggable = primary.build_info['debuggable'] == '1'
    secondary_debuggable = secondary.build_info['debuggable'] == '1'

    if not primary_debuggable and not secondary_debuggable:
        primary_attestation = primary.cdm.generateAttestation()
        secondary_attestation = secondary.cdm.generateAttestation()
        primary_verified = secondary.cdm.verifyAttestation(primary_attestation)
        secondary_verified = primary.cdm.verifyAttestation(secondary_attestation)
        asserts.assert_true(primary_verified, 'Secondary device failed to verify primary device')
        asserts.assert_true(secondary_verified, 'Primary device failed to verify secondary device')
