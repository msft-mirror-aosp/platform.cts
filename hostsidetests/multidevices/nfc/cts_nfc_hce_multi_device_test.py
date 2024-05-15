#  Copyright (C) 2024 The Android Open Source Project
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.

# Lint as: python3
"""CTS Tests that verify NFC HCE features.

These tests require two phones, one acting as a card emulator and the other
acting as an NFC reader. The two phones should be placed back to back.
"""

import sys
import logging

from mobly import asserts
from mobly import base_test
from mobly import test_runner
from mobly import utils
from mobly.controllers import android_device
from mobly.snippet import errors

# Timeout to give the NFC service time to perform async actions such as
# discover tags.
_NFC_TIMEOUT_SEC = 10
_NFC_TECH_A_POLLING_ON = (0x1 #NfcAdapter.FLAG_READER_NFC_A
                          | 0x10 #NfcAdapter.FLAG_READER_NFC_BARCODE
                          | 0x80 #NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK
                          )
_NFC_TECH_A_POLLING_OFF = (0x10 #NfcAdapter.FLAG_READER_NFC_BARCODE
                           | 0x80 #NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK
                           )
_NFC_TECH_A_LISTEN_ON = 0x1 #NfcAdapter.FLAG_LISTEN_NFC_PASSIVE_A
_NFC_TECH_F_LISTEN_ON = 0x4 #NfcAdapter.FLAG_LISTEN_NFC_PASSIVE_F
_NFC_LISTEN_OFF = 0x0 #NfcAdapter.FLAG_LISTEN_DISABLE

class CtsNfcHceMultiDeviceTestCases(base_test.BaseTestClass):
    def setup_class(self):
        """
        Sets up class by registering two devices, enabling nfc on them,
        and loading snippets.
        """
        self.emulator, self.reader = self.register_controller(android_device,
                                                              min_number=2)[:2]
        self.reader.load_snippet('nfc_reader',
                                 'com.android.nfc.reader')
        self.emulator.load_snippet('nfc_emulator',
                                   'com.android.nfc.emulator')

        self.reader.adb.shell(['svc', 'nfc', 'enable'])
        self.emulator.adb.shell(['svc', 'nfc', 'enable'])

        self.reader.debug_tag = 'reader'
        self.emulator.debug_tag = 'emulator'

    def setup_test(self):
        """
        Turns emulator/reader screen on and unlocks between tests as some tests will
        turn the screen off.
        """
        self.emulator.nfc_emulator.logInfo("*** TEST START: " + self.current_test_info.name + " ***")
        self.reader.nfc_reader.logInfo("*** TEST START: " + self.current_test_info.name + " ***")
        self.emulator.nfc_emulator.turnScreenOn()
        self.emulator.nfc_emulator.pressMenu()
        self.reader.nfc_reader.turnScreenOn()
        self.reader.nfc_reader.pressMenu()

    def on_fail(self, record):
        test_name = record.test_name
        self.emulator.take_bug_report(
            test_name=self.emulator.debug_tag + "_" + test_name,
            destination=self.current_test_info.output_path,
        )
        self.reader.take_bug_report(
            test_name=self.reader.debug_tag + "_" + test_name,
            destination=self.current_test_info.output_path,
        )


    #@CddTest(requirements = {"7.4.4/C-2-2", "7.4.4/C-1-2"})
    def test_single_non_payment_service(self):
        """Tests successful APDU exchange between non-payment service and
        reader.

        Test Steps:
        1. Start emulator activity and set up non-payment HCE Service.
        2. Set callback handler on emulator for when a TestPass event is
        received.
        3. Start reader activity, which should trigger APDU exchange between
        reader and emulator.

        Verifies:
        1. Verifies a successful APDU exchange between the emulator and
        Transport Service after
        _NFC_TIMEOUT_SEC.
        """
        self.emulator.nfc_emulator.startSingleNonPaymentEmulatorActivity()

        test_pass_handler = self.emulator.nfc_emulator.asyncWaitForTestPass(
            'ApduSuccess')
        self.reader.nfc_reader.startSingleNonPaymentReaderActivity()
        test_pass_event = test_pass_handler.waitAndGet('ApduSuccess',
                                                       _NFC_TIMEOUT_SEC)

        asserts.assert_is_not_none(test_pass_event,
                                   'ApduSuccess event was not received.')

    #@CddTest(requirements = {"7.4.4/C-2-2", "7.4.4/C-1-2", "9.1/C-0-1"})
    def test_single_payment_service(self):
        """Tests successful APDU exchange between payment service and
        reader.

        Test Steps:
        1. Set callback handler on emulator for when the instrumentation app is
        set to default wallet app.
        2. Start emulator activity and wait for the role to be set.
        2. Set callback handler on emulator for when a TestPass event is
        received.
        3. Start reader activity, which should trigger APDU exchange between
        reader and emulator.

        Verifies:
        1. Verifies emulator device sets the instrumentation emulator app to the
        default wallet app.
        2. Verifies a successful APDU exchange between the emulator and
        Transport Service after _NFC_TIMEOUT_SEC.
        """
        # Wait for instrumentation app to hold onto wallet role before starting
        # reader
        role_held_handler = self.emulator.nfc_emulator.asyncWaitForRoleHeld(
            'RoleHeld')
        self.emulator.nfc_emulator.startSinglePaymentEmulatorActivity()
        role_held_handler.waitAndGet('RoleHeld', _NFC_TIMEOUT_SEC)

        test_pass_handler = self.emulator.nfc_emulator.asyncWaitForTestPass(
            'ApduSuccess')
        self.reader.nfc_reader.startSinglePaymentReaderActivity()
        test_pass_handler.waitAndGet('ApduSuccess', _NFC_TIMEOUT_SEC)

    #@CddTest(requirements = {"7.4.4/C-2-2", "7.4.4/C-1-2", "9.1/C-0-1"})
    def test_dual_payment_service(self):
        """Tests successful APDU exchange between a payment service and
        reader when two payment services are set up on the emulator.

        Test Steps:
        1. Set callback handler on emulator for when the instrumentation app is
        set to default wallet app.
        2. Start emulator activity and wait for the role to be set.
        2. Set callback handler on emulator for when a TestPass event is
        received.
        3. Start reader activity, which should trigger APDU exchange between
        reader and emulator.

        Verifies:
        1. Verifies a successful APDU exchange between the emulator and the
        payment service.
        """
        role_held_handler = self.emulator.nfc_emulator.asyncWaitForRoleHeld(
            'RoleHeld')
        self.emulator.nfc_emulator.startDualPaymentEmulatorActivity()
        role_held_handler.waitAndGet('RoleHeld', _NFC_TIMEOUT_SEC)

        test_pass_handler = self.emulator.nfc_emulator.asyncWaitForTestPass(
            'ApduSuccess')
        self.reader.nfc_reader.startDualPaymentReaderActivity()
        test_pass_handler.waitAndGet('ApduSuccess', _NFC_TIMEOUT_SEC)

    #@CddTest(requirements = {"7.4.4/C-2-2", "7.4.4/C-1-2", "9.1/C-0-1"})
    def test_foreground_payment_emulator(self):
        """Tests successful APDU exchange between non-default payment service and
        reader when the foreground app sets a preference for the non-default
        service.

        Test Steps:
        1. Set callback handler on emulator for when the instrumentation app is
        set to default wallet app.
        2. Start emulator activity and wait for the role to be set.
        2. Set callback handler on emulator for when a TestPass event is
        received.
        3. Start reader activity, which should trigger APDU exchange between
        reader and emulator.

        Verifies:
        1. Verifies a successful APDU exchange between the emulator and the
        preferred service.
        """
        role_held_handler = self.emulator.nfc_emulator.asyncWaitForRoleHeld(
            'RoleHeld')
        self.emulator.nfc_emulator.startForegroundPaymentEmulatorActivity()
        role_held_handler.waitAndGet('RoleHeld', _NFC_TIMEOUT_SEC)

        test_pass_handler = self.emulator.nfc_emulator.asyncWaitForTestPass(
            'ApduSuccess')
        self.reader.nfc_reader.startForegroundPaymentReaderActivity()
        test_pass_handler.waitAndGet('ApduSuccess', _NFC_TIMEOUT_SEC)

    #@CddTest(requirements = {"7.4.4/C-2-2", "7.4.4/C-1-2"})
    def test_dynamic_aid_emulator(self):
        """Tests successful APDU exchange between payment service and reader
        when the payment service has registered dynamic AIDs.

        Test Steps:
        1. Set callback handler on emulator for when the instrumentation app is
        set to default wallet app.
        2. Start emulator activity and wait for the role to be set.
        2. Set callback handler on emulator for when a TestPass event is
        received.
        3. Start reader activity, which should trigger APDU exchange between
        reader and emulator.

        Verifies:
        1. Verifies a successful APDU exchange between the emulator and the
        payment service with dynamic AIDs.
        """
        role_held_handler = self.emulator.nfc_emulator.asyncWaitForRoleHeld(
            'RoleHeld')
        self.emulator.nfc_emulator.startDynamicAidEmulatorActivity()
        role_held_handler.waitAndGet('RoleHeld', _NFC_TIMEOUT_SEC)

        test_pass_handler = self.emulator.nfc_emulator.asyncWaitForTestPass(
            'ApduSuccess')
        self.reader.nfc_reader.startDynamicAidReaderActivity()
        test_pass_handler.waitAndGet('ApduSuccess', _NFC_TIMEOUT_SEC)

    #@CddTest(requirements = {"7.4.4/C-2-2", "7.4.4/C-1-2", "9.1/C-0-1"})
    def test_payment_prefix_emulator(self):
        """Tests successful APDU exchange between payment service and reader
        when the payment service has statically registered prefix AIDs.

        Test Steps:
        1. Set callback handler on emulator for when the instrumentation app is
        set to default wallet app.
        2. Start emulator activity and wait for the role to be set.
        2. Set callback handler on emulator for when a TestPass event is
        received.
        3. Start reader activity, which should trigger APDU exchange between
        reader and emulator.

        Verifies:
        1. Verifies a successful APDU exchange between the emulator and the
        payment service with prefix AIDs.
        """
        role_held_handler = self.emulator.nfc_emulator.asyncWaitForRoleHeld(
            'RoleHeld')
        self.emulator.nfc_emulator.startPrefixPaymentEmulatorActivity()
        role_held_handler.waitAndGet('RoleHeld', _NFC_TIMEOUT_SEC)

        test_pass_handler = self.emulator.nfc_emulator.asyncWaitForTestPass(
            'ApduSuccess')
        self.reader.nfc_reader.startPrefixPaymentReaderActivity()
        test_pass_handler.waitAndGet('ApduSuccess', _NFC_TIMEOUT_SEC)

    #@CddTest(requirements = {"7.4.4/C-2-2", "7.4.4/C-1-2", "9.1/C-0-1"})
    def test_prefix_payment_emulator_2(self):
        """Tests successful APDU exchange between payment service and reader
        when the payment service has statically registered prefix AIDs.
        Identical to the test above, except PrefixPaymentService2 is set up
        first in the emulator activity.

        Test Steps:
        1. Set callback handler on emulator for when the instrumentation app is
        set to default wallet app.
        2. Start emulator activity and wait for the role to be set.
        2. Set callback handler on emulator for when a TestPass event is
        received.
        3. Start reader activity, which should trigger APDU exchange between
        reader and emulator.

        Verifies:
        1. Verifies a successful APDU exchange between the emulator and the
        payment service with prefix AIDs.
        """
        role_held_handler = self.emulator.nfc_emulator.asyncWaitForRoleHeld(
            'RoleHeld')
        self.emulator.nfc_emulator.startPrefixPaymentEmulator2Activity()
        role_held_handler.waitAndGet('RoleHeld', _NFC_TIMEOUT_SEC)

        test_pass_handler = self.emulator.nfc_emulator.asyncWaitForTestPass(
            'ApduSuccess')
        self.reader.nfc_reader.startPrefixPaymentReader2Activity()
        test_pass_handler.waitAndGet('ApduSuccess', _NFC_TIMEOUT_SEC)

    #@CddTest(requirements = {"7.4.4/C-2-2", "7.4.4/C-1-2"})
    def test_other_prefix(self):
        """Tests successful APDU exchange when the emulator dynamically
        registers prefix AIDs for a non-payment service.

        Test steps:
        1. Start emulator activity.
        2. Set callback handler on emulator for when ApduSuccess event is
        received.
        3. Start reader activity, which should trigger APDU exchange between
        reader and emulator.

        Verifies:
        1. Verifies successful APDU sequence exchange.

        """
        self.emulator.nfc_emulator.startDualNonPaymentPrefixEmulatorActivity()
        test_pass_handler = self.emulator.nfc_emulator.asyncWaitForTestPass(
            'ApduSuccess')
        self.reader.nfc_reader.startDualNonPaymentPrefixReaderActivity()
        test_pass_handler.waitAndGet('ApduSuccess', _NFC_TIMEOUT_SEC)

    #@CddTest(requirements = {"7.4.4/C-2-2", "7.4.4/C-1-2"})
    def test_offhost_service(self):
        """Tests successful APDU exchange between offhost service and reader.

        Test Steps:
        1. Start emulator activity.
        2. Set callback handler for when reader TestPass event is received.
        3. Start reader activity, which should trigger APDU exchange between
        reader and emulator.

        Verifies:
        1. Verifies a successful APDU exchange inside the reader.
        We cannot verify the APDUs in the emulator since we don't have access to the secure element.
        """
        self.emulator.nfc_emulator.startOffHostEmulatorActivity(False)
        test_pass_handler = self.reader.nfc_reader.asyncWaitForTestPass('ApduSuccess')
        self.reader.nfc_reader.startOffHostReaderActivity()
        test_pass_handler.waitAndGet('ApduSuccess', _NFC_TIMEOUT_SEC)

    #@CddTest(requirements = {"7.4.4/C-2-2", "7.4.4/C-1-2"})
    def test_on_and_offhost_service(self):
        """Tests successful APDU exchange between when reader selects both an on-host and off-host
        service.

        Test Steps:
        1. Start emulator activity.
        2. Set callback handler for when reader TestPass event is received.
        3. Start reader activity, which should trigger APDU exchange between
        reader and emulator.

        Verifies:
        1. Verifies a successful APDU exchange inside the reader.
        We cannot verify the APDUs in the emulator since we don't have access to the secure element.
        """
        self.emulator.nfc_emulator.startOnAndOffHostEmulatorActivity()
        test_pass_handler = self.reader.nfc_reader.asyncWaitForTestPass('ApduSuccess')
        self.reader.nfc_reader.startOnAndOffHostReaderActivity()
        test_pass_handler.waitAndGet('ApduSuccess', _NFC_TIMEOUT_SEC)

    #@CddTest(requirements = {"7.4.4/C-2-2", "7.4.4/C-1-2"})
    def test_dual_non_payment(self):
        """Tests successful APDU exchange between transport service and reader
        when two non-payment services are enabled.

        Test Steps:
        1. Start emulator activity which sets up TransportService2 and
        AccessService.
        2. Set callback handler on emulator for when a TestPass event is
        received.
        3. Start reader activity, which should trigger APDU exchange between
        reader and emulator.

        Verifies:
        1. Verifies a successful APDU exchange between the emulator and the
        transport service.
        """
        self.emulator.nfc_emulator.startDualNonPaymentEmulatorActivity()
        test_pass_handler = self.emulator.nfc_emulator.asyncWaitForTestPass(
            'ApduSuccess')
        self.reader.nfc_reader.startDualNonPaymentReaderActivity()
        test_pass_handler.waitAndGet('ApduSuccess', _NFC_TIMEOUT_SEC)

    #@CddTest(requirements = {"7.4.4/C-2-2", "7.4.4/C-1-2"})
    def test_foreground_non_payment(self):
        """Tests successful APDU exchange between non-payment service and
          reader when the foreground app sets a preference for the
          non-default service.

          Test Steps:
          1. Start emulator activity which sets up TransportService1 and
          TransportService2
          2. Set callback handler on emulator for when a TestPass event is
          received.
          3. Start reader activity, which should trigger APDU exchange between
          reader and non-default service.

          Verifies:
          1. Verifies a successful APDU exchange between the emulator and the
          transport service.
          """
        self.emulator.nfc_emulator.startForegroundNonPaymentEmulatorActivity()
        test_pass_handler = self.emulator.nfc_emulator.asyncWaitForTestPass(
            'ApduSuccess')
        self.reader.nfc_reader.startForegroundNonPaymentReaderActivity()
        test_pass_handler.waitAndGet('ApduSuccess', _NFC_TIMEOUT_SEC)

    #@CddTest(requirements = {"7.4.4/C-2-2", "7.4.4/C-1-2"})
    def test_throughput(self):
        """Tests that APDU sequence exchange occurs with under 60ms per APDU.

         Test Steps:
         1. Start emulator activity.
         2. Set callback handler on emulator for when a TestPass event is
         received.
         3. Start reader activity, which should trigger APDU exchange between
         reader and non-default service.

         Verifies:
         1. Verifies a successful APDU exchange between the emulator and the
         transport service with the duration averaging under 60 ms per single
         exchange.
         """
        self.emulator.nfc_emulator.startThroughputEmulatorActivity()
        test_pass_handler = self.emulator.nfc_emulator.asyncWaitForTestPass(
            'ApduUnderThreshold')
        self.reader.nfc_reader.startThroughputReaderActivity()
        test_pass_handler.waitAndGet('ApduUnderThreshold', _NFC_TIMEOUT_SEC)

    #@CddTest(requirements = {"7.4.4/C-2-2", "7.4.4/C-1-2"})
    def test_tap_50_times(self):
        """Tests that 50 consecutive APDU exchanges are successful.

        Test Steps:
         1. Start emulator activity.
         2. Perform the following sequence 50 times:
            a. Set callback handler on emulator for when a TestPass event is
            received.
            b. Start reader activity.
            c. Wait for successful APDU exchange.
            d. Close reader activity.

         Verifies:
         1. Verifies 50 ApduSuccess events are received in a row.
         """
        self.emulator.nfc_emulator.startTapTestEmulatorActivity()
        for i in range(50):
            test_pass_handler = self.emulator.nfc_emulator.asyncWaitForTestPass(
                'ApduSuccess'
            )
            self.reader.nfc_reader.startTapTestReaderActivity()
            test_pass_handler.waitAndGet('ApduSuccess', _NFC_TIMEOUT_SEC)
            self.reader.nfc_reader.closeActivity()

    #@CddTest(requirements = {"7.4.4/C-2-2", "7.4.4/C-1-2"})
    def test_large_num_aids(self):
        """Tests that a long APDU sequence (256 commands/responses) is
        successfully exchanged.

        Test Steps:
         1. Start emulator activity.
         2. Set callback handler on emulator for when a TestPass event is
         received.
         3. Start reader activity.
         4. Wait for successful APDU exchange.

         Verifies:
         1. Verifies successful APDU exchange.
         """
        # This test requires a larger timeout due to large number of AIDs
        large_timeout = 60
        self.emulator.nfc_emulator.startLargeNumAidsEmulatorActivity()
        test_pass_handler = self.emulator.nfc_emulator.asyncWaitForTestPass(
            'ApduSuccess'
        )
        self.reader.nfc_reader.startLargeNumAidsReaderActivity()
        test_pass_handler.waitAndGet('ApduSuccess', large_timeout)

    #@CddTest(requirements = {"7.4.4/C-2-2", "7.4.4/C-1-2"})
    def test_screen_off_payment(self):
        """Tests that APDU exchange occurs when device screen is off.

        Test Steps:
        1. Set callback handler on emulator for when the instrumentation app is
        set to default wallet app.
        2. Start emulator activity and wait for the role to be set.
        3. Set callback handler for when screen is off.
        4. Turn emulator screen off and wait for event.
        5. Set callback handler on emulator for when a TestPass event is
         received.
        6. Start reader activity, which should trigger successful APDU exchange.
        7. Wait for successful APDU exchange.

        Verifies:
        1. Verifies default wallet app is set.
        2. Verifies screen is turned off on the emulator.
        3. Verifies successful APDU exchange with emulator screen off.
        """
        role_held_handler = self.emulator.nfc_emulator.asyncWaitForRoleHeld(
            'RoleHeld'
        )
        self.emulator.nfc_emulator.startScreenOffPaymentEmulatorActivity()
        role_held_handler.waitAndGet('RoleHeld', _NFC_TIMEOUT_SEC)

        screen_off_handler = self.emulator.nfc_emulator.asyncWaitForScreenOff(
            'ScreenOff')
        self.emulator.nfc_emulator.turnScreenOff()
        screen_off_handler.waitAndGet('ScreenOff', _NFC_TIMEOUT_SEC)

        test_pass_handler = self.emulator.nfc_emulator.asyncWaitForTestPass(
            'ApduSuccess'
        )
        self.reader.nfc_reader.startScreenOffPaymentReaderActivity()
        test_pass_handler.waitAndGet('ApduSuccess', _NFC_TIMEOUT_SEC)

    #@CddTest(requirements = {"7.4.4/C-2-2", "7.4.4/C-1-2"})
    def test_conflicting_non_payment(self):
        """ This test registers two non-payment services with conflicting AIDs,
        selects a service to use, and ensures the selected service exchanges
        an APDU sequence with the reader.

        Test Steps:
        1. Start emulator.
        2. Start reader.
        3. Select a service on the emulator device from the list of services.
        4. Disable polling on the reader.
        5. Set a callback handler on the emulator for a successful APDU
        exchange.
        6. Re-enable polling on the reader, which should trigger the APDU
        exchange with the selected service.

        Verifies:
        1. Verifies APDU exchange is successful between the reader and the
        selected service.
        """
        self.emulator.nfc_emulator.startConflictingNonPaymentEmulatorActivity()
        self.reader.nfc_reader.startConflictingNonPaymentReaderActivity()
        self.emulator.nfc_emulator.selectItem()
        self.reader.nfc_reader.setPollTech(_NFC_TECH_A_POLLING_OFF)
        test_pass_handler = self.emulator.nfc_emulator.asyncWaitForTestPass(
            'ApduSuccess'
        )
        self.reader.nfc_reader.setPollTech(_NFC_TECH_A_POLLING_ON)
        test_pass_handler.waitAndGet('ApduSuccess', _NFC_TIMEOUT_SEC)

    #@CddTest(requirements = {"7.4.4/C-2-2", "7.4.4/C-1-2"})
    def test_conflicting_non_payment_prefix(self):
        """ This test registers two non-payment services with conflicting
        prefix AIDs, selects a service to use, and ensures the selected
        service exchanges an APDU sequence with the reader.

        Test Steps:
        1. Start emulator.
        2. Start reader.
        3. Select a service on the emulator device from the list of services.
        4. Disable polling on the reader.
        5. Set a callback handler on the emulator for a successful APDU
        exchange.
        6. Re-enable polling on the reader, which should trigger the APDU
        exchange with the selected service.

        Verifies:
        1. Verifies APDU exchange is successful between the reader and the
        selected service.
        """
        (self.emulator.nfc_emulator
         .startConflictingNonPaymentPrefixEmulatorActivity())
        self.reader.nfc_reader.startConflictingNonPaymentPrefixReaderActivity()
        self.emulator.nfc_emulator.selectItem()
        self.reader.nfc_reader.setPollTech(_NFC_TECH_A_POLLING_OFF)
        test_pass_handler = self.emulator.nfc_emulator.asyncWaitForTestPass(
            'ApduSuccess'
        )
        self.reader.nfc_reader.setPollTech(_NFC_TECH_A_POLLING_ON)
        test_pass_handler.waitAndGet('ApduSuccess', _NFC_TIMEOUT_SEC)

    #@CddTest(requirements = {"7.4.4/C-2-2", "7.4.4/C-1-2"})
    def test_protocol_params(self):
        """ Tests that the Nfc-A and ISO-DEP protocol parameters are being
        set correctly.

        Test Steps:
        1. Start emulator.
        2. Start callback handler on reader for when a TestPass event is
        received.
        3. Start reader.
        4. Wait for success event to be sent.

        Verifies:
        1. Verifies Nfc-A and ISO-DEP protocol parameters are set correctly.
        """
        self.emulator.nfc_emulator.startProtocolParamsEmulatorActivity()
        test_pass_handler = self.reader.nfc_reader.asyncWaitForTestPass(
            'TestPass')
        self.reader.nfc_reader.startProtocolParamsReaderActivity()
        test_pass_handler.waitAndGet('TestPass', _NFC_TIMEOUT_SEC)

    #@CddTest(requirements = {"7.4.4/C-2-2", "7.4.4/C-1-2"})
    def test_screen_on_only_off_host_service(self):
        """
        Test Steps:
        1. Start emulator and turn screen off.
        2. Start callback handler on reader for when a TestPass event is
        received.
        3. Start reader activity, which should trigger callback handler.
        4. Ensure expected APDU is received.
        5. Close reader and turn screen off on the emulator.

        Verifies:
        1. Verifies correct APDU response when screen is off.
        2. Verifies correct APDU response between reader and off-host service
        when screen is on.
        """
        #Tests APDU exchange with screen off.
        self.emulator.nfc_emulator.startScreenOnOnlyOffHostEmulatorActivity()
        self.emulator.nfc_emulator.turnScreenOff()
        screen_off_handler = self.emulator.nfc_emulator.asyncWaitForScreenOff(
            'ScreenOff')
        screen_off_handler.waitAndGet('ScreenOff', _NFC_TIMEOUT_SEC)
        test_pass_handler = (
            self.reader.nfc_reader.asyncWaitForTestPass(
                'ApduSuccessScreenOff'))
        self.reader.nfc_reader.startScreenOnOnlyOffHostReaderActivity()
        test_pass_handler.waitAndGet('ApduSuccessScreenOff', _NFC_TIMEOUT_SEC)
        self.reader.nfc_reader.closeActivity()

        #Tests APDU exchange with screen on.
        screen_on_handler = self.emulator.nfc_emulator.asyncWaitForScreenOn(
            'ScreenOn')
        self.emulator.nfc_emulator.pressMenu()
        screen_on_handler.waitAndGet('ScreenOn', _NFC_TIMEOUT_SEC)
        test_pass_handler = self.reader.nfc_reader.asyncWaitForTestPass(
            'ApduSuccessScreenOn')
        self.reader.nfc_reader.startScreenOnOnlyOffHostReaderActivity()

        test_pass_handler.waitAndGet('ApduSuccessScreenOn', _NFC_TIMEOUT_SEC)

    #@CddTest(requirements = {"7.4.4/C-2-2", "7.4.4/C-1-2"})
    def test_single_non_payment_service_with_listen_tech_disabled(self):
        """Tests successful APDU exchange between non-payment service and
        reader does not proceed when Type-a listen tech is disabled.

        Test Steps:
        1. Start emulator activity and set up non-payment HCE Service.
        2. Set listen tech to disabled on the emulator.
        3. Set callback handler on emulator for when a TestPass event is
        received.
        4. Start reader activity and verify transaction does not proceed.
        5. Set listen tech to Type-A on the emulator.
        6. This should trigger APDU exchange between reader and emulator.

        Verifies:
        1. Verifies that no APDU exchange occurs when the listen tech is disabled.
        2. Verifies a successful APDU exchange between the emulator and
        Transport Service after _NFC_TIMEOUT_SEC.
        """
        self.emulator.nfc_emulator.startSingleNonPaymentEmulatorActivity()
        # Set listen off
        self.emulator.nfc_emulator.setListenTech(_NFC_LISTEN_OFF)

        test_pass_handler = self.emulator.nfc_emulator.asyncWaitForTestPass(
            'ApduSuccess')
        self.reader.nfc_reader.startSingleNonPaymentReaderActivity()
        with asserts.assert_raises(
                errors.CallbackHandlerTimeoutError,
                "Transaction completed when listen tech is disabled",
        ):
            test_pass_handler.waitAndGet('ApduSuccess', _NFC_TIMEOUT_SEC)

        # Set listen on
        self.emulator.nfc_emulator.setListenTech(_NFC_TECH_A_LISTEN_ON)
        test_pass_handler.waitAndGet('ApduSuccess', _NFC_TIMEOUT_SEC)


    #@CddTest(requirements = {"7.4.4/C-2-2", "7.4.4/C-1-2"})
    def test_single_non_payment_service_with_listen_tech_poll_tech_mismatch(self):
        """Tests successful APDU exchange between non-payment service and
        reader does not proceed when emulator listen tech mismatches reader poll tech.

        Test Steps:
        1. Start emulator activity and set up non-payment HCE Service.
        2. Set listen tech to Type-F on the emulator.
        3. Set callback handler on emulator for when a TestPass event is
        received.
        4. Start reader activity and verify transaction does not proceed.
        5. Set listen tech to Type-A on the emulator.
        6. This should trigger APDU exchange between reader and emulator.

        Verifies:
        1. Verifies that no APDU exchange occurs when the listen tech mismatches with poll tech.
        2. Verifies a successful APDU exchange between the emulator and
        Transport Service after _NFC_TIMEOUT_SEC.
        """
        self.emulator.nfc_emulator.startSingleNonPaymentEmulatorActivity()
        # Set listen to Type-F
        self.emulator.nfc_emulator.setListenTech(_NFC_TECH_F_LISTEN_ON)

        test_pass_handler = self.emulator.nfc_emulator.asyncWaitForTestPass(
            'ApduSuccess')
        self.reader.nfc_reader.startSingleNonPaymentReaderActivity()
        with asserts.assert_raises(
                errors.CallbackHandlerTimeoutError,
                "Transaction completed when listen tech is mismatching",
        ):
            test_pass_handler.waitAndGet('ApduSuccess', _NFC_TIMEOUT_SEC)

        # Set listen to Type-A
        self.emulator.nfc_emulator.setListenTech(_NFC_TECH_A_LISTEN_ON)
        test_pass_handler.waitAndGet('ApduSuccess', _NFC_TIMEOUT_SEC)

    def test_single_payment_service_toggle_nfc_off_on(self):
        """Tests successful APDU exchange between payment service and
        reader.

        Test Steps:
        1. Set callback handler on emulator for when the instrumentation app is
        set to default wallet app.
        2. Start emulator activity and wait for the role to be set.
        3. Toggle NFC off and back on the emulator.
        4. Set callback handler on emulator for when a TestPass event is
        received.
        5. Start reader activity, which should trigger APDU exchange between
        reader and emulator.

        Verifies:
        1. Verifies emulator device sets the instrumentation emulator app to the
        default wallet app.
        2. Verifies a successful APDU exchange between the emulator and
        Transport Service after _NFC_TIMEOUT_SEC after toggling NFC off and on.
        """
        # Wait for instrumentation app to hold onto wallet role before starting
        # reader
        role_held_handler = self.emulator.nfc_emulator.asyncWaitForRoleHeld(
            'RoleHeld')
        self.emulator.nfc_emulator.startSinglePaymentEmulatorActivity()
        role_held_handler.waitAndGet('RoleHeld', _NFC_TIMEOUT_SEC)

        self.emulator.nfc_emulator.setNfcState(False)
        self.emulator.nfc_emulator.setNfcState(True)

        test_pass_handler = self.emulator.nfc_emulator.asyncWaitForTestPass(
            'ApduSuccess')
        self.reader.nfc_reader.startSinglePaymentReaderActivity()
        test_pass_handler.waitAndGet('ApduSuccess', _NFC_TIMEOUT_SEC)

    def teardown_test(self):
        self.emulator.nfc_emulator.closeActivity()
        self.reader.nfc_reader.closeActivity()
        utils.concurrent_exec(lambda d: d.services.create_output_excerpts_all(
            self.current_test_info),
                              param_list=[[self.emulator], [self.reader]],
                              raise_on_exception=True)
        self.emulator.nfc_emulator.logInfo("*** TEST END: " + self.current_test_info.name + " ***")
        self.reader.nfc_reader.logInfo("*** TEST END: " + self.current_test_info.name + " ***")


if __name__ == '__main__':
    # Take test args
    if '--' in sys.argv:
        index = sys.argv.index('--')
        sys.argv = sys.argv[:1] + sys.argv[index + 1:]
    test_runner.main()
