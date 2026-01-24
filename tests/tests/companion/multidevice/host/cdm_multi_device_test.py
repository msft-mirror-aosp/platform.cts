#!/usr/bin/env python3
# Lint as: python3
"""
Test core CDM APIs involving multiple devices on mobly.
Run: atest CtsCompanionDeviceManagerMultiDeviceTestCases
"""

import sys

import api_flags_utils
import cdm_base_test
import test_utils

from android.platform.test.annotations import ApiTest, CddTest
from mobly import asserts
from mobly import test_runner
from time import sleep

@CddTest(requirements = ["3.16/C-1-1", "3.16/C-1-2"])
class CompanionDeviceManagerTestClass(cdm_base_test.BaseTestClass):

    @ApiTest(apis=[
            'android.companion.CompanionDeviceManager#associate(android.companion.AssociationRequest, android.companion.CompanionDeviceManager.Callback, android.os.Handler)',
            'android.companion.CompanionDeviceManager#getMyAssociations()'
    ])
    def test_associate_createsAssociation_classicBluetooth(self):
        """Test that CDM can create association with another BT device"""

        # Skip if device is a watch
        test_utils.assume_not_watch(self.primary)

        # Create association
        self.secondary.cdm.btBecomeDiscoverable(cdm_base_test.BT_DISCOVERABLE_TIME)
        secondary_id = self.primary.cdm.associate(self.secondary.address)

        # Assert association was created
        associations = self.primary.cdm.getMyAssociations()
        asserts.assert_true(secondary_id in associations, 'Association not found.')


    @ApiTest(apis=[
            'android.companion.CompanionDeviceManager#buildPermissionTransferUserConsentIntent(int)',
            'android.companion.CompanionDeviceManager#attachSystemDataTransport(int, java.io.InputStream, java.io.OutputStream)',
            'android.companion.CompanionDeviceManager#detachSystemDataTransport(int)',
            'android.companion.CompanionDeviceManager#startSystemDataTransfer(int, java.util.concurrent.Executor, android.os.OutcomeReceiver)'
    ])
    def test_permissions_sync(self):
        """Test that CDM can perform permissions sync from one device to another via BT"""

        # Skip if either device is a watch
        test_utils.assume_not_watch(self.primary)
        test_utils.assume_not_watch(self.secondary)

        # Assert both devices are on same build type (debug vs user)
        test_utils.assert_build_types_match(self.primary, self.secondary)

        # If on user build, assert AVF compliance for peer profiles
        test_utils.assert_attestation_verified(self.primary, self.secondary)

        # Create associations
        self.secondary.cdm.btBecomeDiscoverable(cdm_base_test.BT_DISCOVERABLE_TIME)
        secondary_id = self.primary.cdm.associate(self.secondary.address)

        self.primary.cdm.btBecomeDiscoverable(cdm_base_test.BT_DISCOVERABLE_TIME)
        primary_id = self.secondary.cdm.associate(self.primary.address)

        # Start permissions sync and wait for completion
        self.bt_pair_devices()
        self.secondary.cdm.attachServerSocket(primary_id)
        self.primary.cdm.attachClientSocket(secondary_id)
        self.primary.cdm.requestPermissionTransferUserConsent(secondary_id)
        self.primary.cdm.startPermissionsSync(secondary_id)


    @ApiTest(apis=[
            'android.companion.CompanionDeviceManager#removeBond(int)'
    ])
    def test_removeBond_associatedDevice_succeeds(self):
        """This tests that CDM can remove bluetooth bond from an associated device."""

        # Skip if device is a watch
        test_utils.assume_not_watch(self.primary)

        # Skip if removeBond API flag is disabled
        api_flags_utils.assume_enabled(self.primary, 'android.companion', 'unpair_associated_device')

        # Associate and assert successful pairing
        self.secondary.cdm.btBecomeDiscoverable(cdm_base_test.BT_DISCOVERABLE_TIME)
        secondary_id = self.primary.cdm.associate(self.secondary.address)
        self.bt_pair_devices()
        sleep(cdm_base_test.OPERATION_DELAY_TIME)
        asserts.assert_true(self.secondary.address in self.primary.paired_devices(), 'Pairing unsuccessful.')

        # Remove BT pairing via CDM and assert success
        asserts.assert_true(self.primary.cdm.removeBond(secondary_id), "Unpairing failed.")
        sleep(cdm_base_test.OPERATION_DELAY_TIME)
        asserts.assert_false(self.secondary.address in self.primary.paired_devices(), 'Devices should not be paired.')


    @ApiTest(apis=[
            'android.companion.CompanionDeviceManager#associate(android.companion.AssociationRequest, android.companion.CompanionDeviceManager.Callback, android.os.Handler)',
    ])
    def test_association_persistence_after_reboot(self):
        """Test that association persists after a device reboot"""

        # Skip if device is a watch
        test_utils.assume_not_watch(self.primary)

        # Create association
        self.secondary.cdm.btBecomeDiscoverable(cdm_base_test.BT_DISCOVERABLE_TIME)
        secondary_id = self.primary.cdm.associate(self.secondary.address)

        # Reboot the primary device
        self.primary.reboot()

        # The association should be remaining.
        associations = self.primary.cdm.getMyAssociations()
        asserts.assert_true(secondary_id in associations, 'Association not found.')


    @ApiTest(apis=[
            'android.companion.CompanionDeviceManager#startObservingDevicePresence(android.companion.ObservingDevicePresenceRequest)',
            'android.companion.CompanionDeviceManager#stopObservingDevicePresence(android.companion.ObservingDevicePresenceRequest)'
    ])
    def test_startObservingDevicePresence_observesEvents_bt_classic(self):
        """
        This tests that CDM can listen for BT classic device presence events from
        associated devices.
        """

        # Skip if either device is a watch
        test_utils.assume_not_watch(self.primary)
        test_utils.assume_not_watch(self.secondary)

        # Skip if device presence API flag is disabled
        api_flags_utils.assume_enabled(self.primary, 'android.companion', 'device_presence')

        # Associate and start observing
        self.secondary.cdm.btBecomeDiscoverable(cdm_base_test.BT_DISCOVERABLE_TIME)
        secondary_id = self.primary.cdm.associate(self.secondary.address)
        self.primary.cdm.startObservingDevicePresence(secondary_id)

        # Assert classic bluetooth pairing is detected
        self.bt_pair_devices()
        connected = test_utils.wait(lambda: self.primary.cdm.isAssociationBtConnected(secondary_id))
        asserts.assert_true(connected, 'Device appearance was not observed.')

        # Assert bluetooth unpair is detected
        self.bt_unpair_devices()
        gone = test_utils.wait(lambda: not self.primary.cdm.isAssociationBtConnected(secondary_id))
        asserts.assert_true(gone, 'Device disappearance was not observed.')

        # Stop observing device presence
        self.primary.cdm.stopObservingDevicePresence(secondary_id)


    @ApiTest(apis=[
            'android.companion.CompanionDeviceManager#setLocalMetadata(int, java.lang.String, java.lang.String)',
            'android.companion.CompanionDeviceManager#attachSystemDataTransport(int, java.io.InputStream, java.io.OutputStream)',
            'android.companion.CompanionDeviceManager#detachSystemDataTransport(int)',
    ])
    def test_setLocalMetadata_broadcastsToAssociatedDevices(self):
        """
        This tests that CDM can set local metadata and broadcast it to
        associated devices.
        """

        # Skip if either device is a watch
        test_utils.assume_not_watch(self.primary)
        test_utils.assume_not_watch(self.secondary)

        # Skip if data sync API flag is disabled
        api_flags_utils.assume_enabled(self.primary, 'android.companion', 'enable_data_sync')
        api_flags_utils.assume_enabled(self.secondary, 'android.companion', 'enable_data_sync')

        # Assert both devices are on same build type (debug vs user)
        test_utils.assert_build_types_match(self.primary, self.secondary)

        # If on user build, assert AVF compliance for peer profiles
        test_utils.assert_attestation_verified(self.primary, self.secondary)

        # Create associations
        self.secondary.cdm.btBecomeDiscoverable(cdm_base_test.BT_DISCOVERABLE_TIME)
        secondary_id = self.primary.cdm.associate(self.secondary.address)

        self.primary.cdm.btBecomeDiscoverable(cdm_base_test.BT_DISCOVERABLE_TIME)
        primary_id = self.secondary.cdm.associate(self.primary.address)

        # Set local metadata _before_ transports are attached
        user_id = self.primary.adb.current_user_id
        shell_command = f'cmd companiondevice set-local-metadata {0} A lorem ipsum'.format(user_id)
        self.primary.adb.shell(shell_command)

        # Attach transports
        self.bt_pair_devices()
        self.secondary.cdm.attachServerSocket(primary_id)
        self.primary.cdm.attachClientSocket(secondary_id)

        # Assert that current metadata is broadcasted.
        sleep(cdm_base_test.OPERATION_DELAY_TIME)
        association = self.secondary.cdm.getAssociationInfo(primary_id)
        asserts.assert_is_not_none(association, 'Missing association info.')
        metadata = association.get('metadata').get('A')
        asserts.assert_is_not_none(metadata, 'Did not receive metadata.')
        asserts.assert_equal('ipsum', metadata.get('lorem'), 'Received incorrect metadata.')

        # Set local metadata _after_ transports are attached
        shell_command = f'cmd companiondevice set-local-metadata {0} B version 1'.format(user_id)
        self.primary.adb.shell(shell_command)

        # Assert that updated metadata is broadcasted
        sleep(cdm_base_test.OPERATION_DELAY_TIME)
        association = self.secondary.cdm.getAssociationInfo(primary_id)
        asserts.assert_is_not_none(association, 'Missing association info.')
        metadata = association.get('metadata').get('B')
        asserts.assert_is_not_none(metadata, 'Did not receive metadata.')
        asserts.assert_equal(1, metadata.get('version'), 'Received incorrect metadata.')


    @ApiTest(apis=[
            'android.companion.CompanionDeviceManager#requestHandoff(int, int, java.util.concurrent.Executor, android.companion.CompanionDeviceManager.HandoffRequestCallback)',
            'android.companion.CompanionDeviceManager#registerRemoteTaskListener(java.util.concurrent.Executor, android.companion.CompanionDeviceManager.RemoteTaskListener)',
            'android.companion.CompanionDeviceManager#unregisterRemoteTaskListener(android.companion.CompanionDeviceManager.RemoteTaskListener)',
            'android.app.Activity#onHandoffActivityDataRequested(android.app.HandoffActivityDataRequestInfo)',
    ])
    def test_handoff(self):
        """Test that handoff can exchange data between devices"""

        # Assert both devices are on same build type (debug vs user)
        test_utils.assert_build_types_match(self.primary, self.secondary)

        # If on user build, assert AVF compliance for peer profiles
        test_utils.assert_attestation_verified(self.primary, self.secondary)

        # Skip if task continuity flag is disabled
        api_flags_utils.assume_enabled(self.primary, 'companion', 'task_continuity')
        api_flags_utils.assume_enabled(self.secondary, 'companion', 'task_continuity')

        # Create associations
        self.secondary.cdm.btBecomeDiscoverable(cdm_base_test.BT_DISCOVERABLE_TIME)
        secondary_id = self.primary.cdm.associate(self.secondary.address)

        self.primary.cdm.btBecomeDiscoverable(cdm_base_test.BT_DISCOVERABLE_TIME)
        primary_id = self.secondary.cdm.associate(self.primary.address)

        # Enable handoff and attach transports
        self.bt_pair_devices()
        self.primary.cdm.enableHandoffForAssociation(secondary_id)
        self.secondary.cdm.enableHandoffForAssociation(primary_id)
        self.secondary.cdm.attachServerSocket(primary_id)
        self.primary.cdm.attachClientSocket(secondary_id)

        # Launch handoff activity on secondary device with attached "data" to hand off between devices
        handoff_extras_data = 1
        task_id = self.secondary.cdm.launchHandoffActivity(handoff_extras_data)
        # Expect task information to be sent to primary device
        didSynchronizeTask = self.primary.cdm.wasRemoteTaskReceived(task_id)
        asserts.assert_true(didSynchronizeTask, 'Task not synchronized.')

        # Request handoff and expect success
        status = self.primary.cdm.requestTaskHandoffAndGetStatus(secondary_id, task_id)
        asserts.assert_equal(0, status, 'Expected success.')

        # Get data from handoff activity on secondary device to be accurate
        launched_handoff_activity_data = self.primary.cdm.waitForHandoff()
        asserts.assert_equal(handoff_extras_data, launched_handoff_activity_data, 'Invalid data received.')


if __name__ == '__main__':
    # Take test args
    if '--' in sys.argv:
        index = sys.argv.index('--')
        sys.argv = sys.argv[:1] + sys.argv[index + 1:]
    test_runner.main()
