#!/usr/bin/env python3
# Lint as: python3
"""
Test core CDM APIs involving multiple devices on mobly.
Run: atest CtsCompanionDeviceManagerMultiDeviceTestCases
"""

import api_flags_utils
import cdm_base_test

from mobly import asserts
from mobly import test_runner
from time import sleep

class CompanionDeviceManagerTestClass(cdm_base_test.BaseTestClass):

    def test_associate_createsAssociation_classicBluetooth(self):
        """Test that CDM can create association with another BT device"""

        # Skip if device is a watch
        asserts.skip_if(self.primary.cdm.isWatch(), 'Cannot create association as a watch.')

        secondary_address = self.secondary.cdm.btGetAddress()

        # Create association
        self.secondary.cdm.btBecomeDiscoverable(cdm_base_test.BT_DISCOVERABLE_TIME)
        secondary_id = self.primary.cdm.associate(secondary_address)

        # Assert association was created
        associations = self.primary.cdm.getMyAssociations()
        asserts.assert_true(secondary_id in associations, 'Association not found.')


    def test_permissions_sync(self):
        """Test that CDM can perform permissions sync from one device to another via BT"""

        # Skip if either device is a watch
        asserts.skip_if(self.primary.cdm.isWatch(), 'Cannot create association as a watch.')
        asserts.skip_if(self.secondary.cdm.isWatch(), 'Cannot create association as a watch.')

        # Assume both devices are on same build type (debug vs user)
        primary_debuggable = self.primary.build_info['debuggable']
        secondary_debuggable = self.secondary.build_info['debuggable']
        asserts.skip_if(primary_debuggable != secondary_debuggable, 'Both devices must be on the same type of build')

        # If on user build, assume AVF compliance for peer profiles
        if not primary_debuggable:
            primary_attestation = self.primary.cdm.generateAttestation()
            secondary_attestation = self.secondary.cdm.generateAttestation()
            primary_verified = self.secondary.cdm.verifyAttestation(primary_attestation)
            secondary_verified = self.primary.cdm.verifyAttestation(secondary_attestation)
            asserts.skip_if(not primary_verified, 'Secondary device failed to verify primary device')
            asserts.skip_if(not secondary_verified, 'Primary device failed to verify secondary device')

        primary_address = self.primary.cdm.btGetAddress()
        secondary_address = self.secondary.cdm.btGetAddress()

        # Create associations
        self.primary.cdm.btBecomeDiscoverable(cdm_base_test.BT_DISCOVERABLE_TIME)
        primary_id = self.secondary.cdm.associate(primary_address)

        self.secondary.cdm.btBecomeDiscoverable(cdm_base_test.BT_DISCOVERABLE_TIME)
        secondary_id = self.primary.cdm.associate(secondary_address)

        # Create bond
        self.secondary.cdm.btStartAutoAcceptIncomingPairRequest()
        self.primary.cdm.btDiscoverAndGetResults()
        self.primary.cdm.btPairDevice(secondary_address)
        sleep(cdm_base_test.OPERATION_DELAY_TIME)

        # Start permissions sync and wait for completion
        self.secondary.cdm.attachServerSocket(primary_id)
        self.primary.cdm.attachClientSocket(secondary_id)
        self.primary.cdm.requestPermissionTransferUserConsent(secondary_id)
        self.primary.cdm.startPermissionsSync(secondary_id)


    def test_removeBond_associatedDevice_succeeds(self):
        """This tests that CDM can remove bluetooth bond from an associated device."""

        # Skip if device is a watch
        asserts.skip_if(self.primary.cdm.isWatch(), 'Cannot create association as a watch.')

        # Skip if removeBond API flag is disabled
        api_flags_utils.assume_enabled(self.primary, 'unpair_associated_device')

        secondary_address = self.secondary.cdm.btGetAddress()

        # Associate
        self.secondary.cdm.btBecomeDiscoverable(cdm_base_test.BT_DISCOVERABLE_TIME)
        secondary_id = self.primary.cdm.associate(secondary_address)

        # Create classic bluetooth pairing
        self.secondary.cdm.btStartAutoAcceptIncomingPairRequest()
        self.primary.cdm.btDiscoverAndGetResults()
        self.primary.cdm.btPairDevice(secondary_address)

        # Assert bluetooth pairing success
        paired_devices = map(lambda device: device['Address'], self.primary.cdm.btGetPairedDevices())
        asserts.assert_true(secondary_address in paired_devices, 'Pairing unsuccessful.')

        # Remove BT pairing via CDM and assert success
        asserts.assert_true(self.primary.cdm.removeBond(secondary_id), "Unpairing failed.")
        sleep(cdm_base_test.OPERATION_DELAY_TIME)
        paired_devices = map(lambda device: device['Address'], self.primary.cdm.btGetPairedDevices())
        asserts.assert_false(secondary_address in paired_devices, 'Devices should not be paired.')


if __name__ == '__main__':
    # Take test args
    if '--' in sys.argv:
        index = sys.argv.index('--')
        sys.argv = sys.argv[:1] + sys.argv[index + 1:]
    test_runner.main()