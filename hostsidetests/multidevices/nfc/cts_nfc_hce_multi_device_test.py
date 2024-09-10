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

These tests require one phone and one PN532 board (or two phones), one acting as
a card emulator and the other acting as an NFC reader. The devices should be
placed back to back.
"""

import sys
import logging

from mobly import asserts
from mobly import base_test
from mobly import test_runner
from mobly import utils
from mobly.controllers import android_device
from mobly.controllers import android_device_lib
from mobly.snippet import errors

_LOG = logging.getLogger(__name__)
logging.basicConfig(level=logging.INFO)
try:
    import pn532
    from pn532.nfcutils import (parse_protocol_params, create_select_apdu, poll_and_transact,
                                get_apdus)
except ImportError:
    _LOG.warning("Cannot import PN532 library")

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
_SERVICE_PACKAGE = "com.android.nfc.service"
_ACCESS_SERVICE = _SERVICE_PACKAGE + ".AccessService"
_OFFHOST_SERVICE = _SERVICE_PACKAGE + ".OffHostService"
_LARGE_NUM_AIDS_SERVICE = _SERVICE_PACKAGE + ".LargeNumAidsService"
_PAYMENT_SERVICE_1 = _SERVICE_PACKAGE + ".PaymentService1"
_PAYMENT_SERVICE_2 = _SERVICE_PACKAGE + ".PaymentService2"
_PAYMENT_SERVICE_DYNAMIC_AIDS = _SERVICE_PACKAGE + ".PaymentServiceDynamicAids"
_PREFIX_ACCESS_SERVICE = _SERVICE_PACKAGE + ".PrefixAccessService"
_PREFIX_PAYMENT_SERVICE_1 = _SERVICE_PACKAGE + ".PrefixPaymentService1"
_PREFIX_TRANSPORT_SERVICE_2 = _SERVICE_PACKAGE + ".PrefixTransportService2"
_SCREEN_OFF_PAYMENT_SERVICE = _SERVICE_PACKAGE + ".ScreenOffPaymentService"
_SCREEN_ON_ONLY_OFF_HOST_SERVICE = _SERVICE_PACKAGE + ".ScreenOnOnlyOffHostService"
_THROUGHPUT_SERVICE = _SERVICE_PACKAGE + ".ThroughputService"
_TRANSPORT_SERVICE_1 = _SERVICE_PACKAGE + ".TransportService1"
_TRANSPORT_SERVICE_2 = _SERVICE_PACKAGE + ".TransportService2"

_NUM_POLLING_LOOPS = 50
_FAILED_TAG_MSG =  "Reader did not detect tag, transaction not attempted."
_FAILED_TRANSACTION_MSG = "Transaction failed, check device logs for more information."

class CtsNfcHceMultiDeviceTestCases(base_test.BaseTestClass):

    def _set_up_emulator(self, *args, start_emulator_fun=None, service_list=[],
                 expected_service=None, is_payment=False, preferred_service=None,
                 payment_default_service=None):
        """
        Sets up emulator device for multidevice tests.
        :param is_payment: bool
            Whether test is setting up payment services. If so, this function will register
            this app as the default wallet.
        :param start_emulator_fun: fun
            Custom function to start the emulator activity. If not present,
            startSimpleEmulatorActivity will be used.
        :param service_list: list
            List of services to set up. Only used if a custom function is not called.
        :param expected_service: String
            Class name of the service expected to handle the APDUs.
        :param preferred_service: String
            Service to set as preferred service, if any.
        :param payment_default_service: String
            For payment tests only: the default payment service that is expected to handle APDUs.
        :param args: arguments for start_emulator_fun, if any

        :return:
        """
        role_held_handler = self.emulator.nfc_emulator.asyncWaitForRoleHeld(
            'RoleHeld')
        if start_emulator_fun is not None:
            start_emulator_fun(*args)
        else:
            if preferred_service is None:
                self.emulator.nfc_emulator.startSimpleEmulatorActivity(service_list,
                                                                       expected_service, is_payment)
            else:
                self.emulator.nfc_emulator.startSimpleEmulatorActivityWithPreferredService(
                    service_list, expected_service, preferred_service, is_payment
                )

        if is_payment:
            role_held_handler.waitAndGet('RoleHeld', _NFC_TIMEOUT_SEC)
            if payment_default_service is None:
                raise Exception("Must define payment_default_service for payment tests.")
            self.emulator.nfc_emulator.waitForService(payment_default_service)

    def _set_up_reader_and_assert_transaction(self, start_reader_fun=None, expected_service=None,
                                              is_offhost=False):
        """
        Sets up reader device, and asserts successful APDU transaction
        :param start_reader_fun: function
                Function to start reader activity on reader phone if PN532 is not enabled.
        :param expected_service: string
                Class name of the service expected to handle the APDUs on the emulator device.
        :param is_offhost: bool
                Whether service to handle APDUs is offhost or not.
        :return:
        """
        if self.pn532:
            if expected_service is None:
                raise Exception('expected_service must be defined.')
            command_apdus, response_apdus = get_apdus(self.emulator.nfc_emulator, expected_service)
            tag_detected, transacted = poll_and_transact(self.pn532, command_apdus, response_apdus)
            asserts.assert_true(tag_detected, _FAILED_TAG_MSG)
            asserts.assert_true(transacted, _FAILED_TRANSACTION_MSG)
        else:
            handler_snippet = self.reader.nfc_reader if is_offhost else (
                self.emulator.nfc_emulator)

            test_pass_handler = handler_snippet.asyncWaitForTestPass('ApduSuccess')
            if start_reader_fun is None:
                raise Exception('start_reader_fun must be defined.')
            start_reader_fun()
            test_pass_handler.waitAndGet('ApduSuccess', _NFC_TIMEOUT_SEC)

    def setup_class(self):
        """
        Sets up class by registering an emulator device, enabling NFC, and loading snippets.

        If a PN532 serial path is found, it uses this to configure the device. Otherwise, set up a
        second phone as a reader device.
        """
        self.pn532 = None

        # This tracks the error message for a setup failure.
        # It is set to None only if the entire setup_class runs successfully.
        self._setup_failure_reason = 'Failed to find Android device(s).'

        # Indicates if the setup failure should block (FAIL) or not block (SKIP) test cases.
        # Blocking failures indicate that something unexpectedly went wrong during test setup,
        # and the user should have it fixed.
        # Non-blocking failures indicate that the device(s) did not meet the test requirements,
        # and the test does not need to be run.
        self._setup_failure_should_block_tests = True

        try:
            devices = self.register_controller(android_device)[:2]
            if len(devices) == 1:
                self.emulator = devices[0]
            else:
                self.emulator, self.reader = devices

            self._setup_failure_reason = (
                'Cannot load emulator snippet. Is NfcEmulatorTestApp.apk '
                'installed on the emulator?'
            )
            self.emulator.load_snippet(
                'nfc_emulator', 'com.android.nfc.emulator'
            )
            self.emulator.adb.shell(['svc', 'nfc', 'enable'])
            self.emulator.debug_tag = 'emulator'
            if (
                not self.emulator.nfc_emulator.isNfcSupported() or
                not self.emulator.nfc_emulator.isNfcHceSupported()
            ):
                self._setup_failure_reason = f'NFC is not supported on {self.emulator}'
                self._setup_failure_should_block_tests = False
                return

            if (
                hasattr(self.emulator, 'dimensions')
                and 'pn532_serial_path' in self.emulator.dimensions
            ):
                pn532_serial_path = self.emulator.dimensions["pn532_serial_path"]
            else:
                pn532_serial_path = self.user_params.get("pn532_serial_path", "")

            if len(pn532_serial_path) > 0:
                self._setup_failure_reason = 'Failed to connect to PN532 board.'
                self.pn532 = pn532.PN532(pn532_serial_path)
                self.pn532.mute()
            else:
                self._setup_failure_reason = 'Two devices are not present.'
                _LOG.info("No value provided for pn532_serial_path. Defaulting to two-device " +
                          "configuration.")
                if len(devices) < 2:
                    return
                self._setup_failure_reason = (
                    'Cannot load reader snippet. Is NfcReaderTestApp.apk '
                    'installed on the reader?'
                )
                self.reader.load_snippet('nfc_reader', 'com.android.nfc.reader')
                self.reader.adb.shell(['svc', 'nfc', 'enable'])
                self.reader.debug_tag = 'reader'
                if not self.reader.nfc_reader.isNfcSupported():
                    self._setup_failure_reason = f'NFC is not supported on {self.reader}'
                    self._setup_failure_should_block_tests = False
                    return
        except Exception as e:
            _LOG.warning('setup_class failed with error %s', e)
            return
        self._setup_failure_reason = None

    def setup_test(self):
        """
        Turns emulator/reader screen on and unlocks between tests as some tests will
        turn the screen off.
        """
        if self._setup_failure_should_block_tests:
            asserts.assert_true(
                self._setup_failure_reason is None, self._setup_failure_reason
            )
        else:
            asserts.skip_if(
                self._setup_failure_reason is not None, self._setup_failure_reason
            )

        self.emulator.nfc_emulator.logInfo("*** TEST START: " + self.current_test_info.name +
                                           " ***")
        self.emulator.nfc_emulator.turnScreenOn()
        self.emulator.nfc_emulator.pressMenu()
        if not self.pn532:
            self.reader.nfc_reader.turnScreenOn()
            self.reader.nfc_reader.pressMenu()

    def on_fail(self, record):
        if self.user_params.get('take_bug_report_on_fail', False):
            test_name = record.test_name
            self.emulator.take_bug_report(
                test_name=self.emulator.debug_tag + "_" + test_name,
                destination=self.current_test_info.output_path,
            )
            if self.pn532 is None:
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
        self._set_up_emulator(
            service_list=[_TRANSPORT_SERVICE_1],
            expected_service=_TRANSPORT_SERVICE_1
        )

        self._set_up_reader_and_assert_transaction(
            expected_service=_TRANSPORT_SERVICE_1,
            start_reader_fun=self.reader.nfc_reader.startSingleNonPaymentReaderActivity if not
            self.pn532 else None
        )

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
        self._set_up_emulator(
            service_list=[_PAYMENT_SERVICE_1],
            expected_service=_PAYMENT_SERVICE_1,
            is_payment=True,
            payment_default_service=_PAYMENT_SERVICE_1
        )

        self._set_up_reader_and_assert_transaction(
            expected_service=_PAYMENT_SERVICE_1,
            start_reader_fun=self.reader.nfc_reader.startSinglePaymentReaderActivity if not
            self.pn532 else None)

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
        self._set_up_emulator(
            service_list=[_PAYMENT_SERVICE_1,_PAYMENT_SERVICE_2],
            expected_service=_PAYMENT_SERVICE_1,
            is_payment=True,
            payment_default_service=_PAYMENT_SERVICE_1
        )

        self._set_up_reader_and_assert_transaction(
            expected_service=_PAYMENT_SERVICE_1,
            start_reader_fun=self.reader.nfc_reader.startDualPaymentReaderActivity
            if not self.pn532 else None)

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
        self._set_up_emulator(
            service_list=[_PAYMENT_SERVICE_1, _PAYMENT_SERVICE_2],
            preferred_service=_PAYMENT_SERVICE_2,
            expected_service=_PAYMENT_SERVICE_2,
            is_payment=True,
            payment_default_service=_PAYMENT_SERVICE_2
        )

        self._set_up_reader_and_assert_transaction(
            expected_service=_PAYMENT_SERVICE_2, start_reader_fun=
            self.reader.nfc_reader.startForegroundPaymentReaderActivity
            if not self.pn532 else None)

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
        self._set_up_emulator(
            start_emulator_fun=self.emulator.nfc_emulator.startDynamicAidEmulatorActivity,
            payment_default_service=_PAYMENT_SERVICE_DYNAMIC_AIDS
        )

        self._set_up_reader_and_assert_transaction(
            expected_service=_PAYMENT_SERVICE_DYNAMIC_AIDS, start_reader_fun=
            self.reader.nfc_reader.startDynamicAidReaderActivity if not self.pn532 else
            None)

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
        self._set_up_emulator(
            start_emulator_fun=self.emulator.nfc_emulator.startPrefixPaymentEmulatorActivity,
            payment_default_service=_PREFIX_PAYMENT_SERVICE_1,
            is_payment=True
        )

        self._set_up_reader_and_assert_transaction(
            expected_service=_PREFIX_PAYMENT_SERVICE_1,
            start_reader_fun=self.reader.nfc_reader.startPrefixPaymentReaderActivity if not
            self.pn532 else None
        )

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
        self._set_up_emulator(
            start_emulator_fun=self.emulator.nfc_emulator.startPrefixPaymentEmulator2Activity,
            payment_default_service=_PREFIX_PAYMENT_SERVICE_1,
            is_payment=True
        )

        self._set_up_reader_and_assert_transaction(
            expected_service=_PREFIX_PAYMENT_SERVICE_1,
            start_reader_fun=self.reader.nfc_reader.startPrefixPaymentReader2Activity if not
            self.pn532 else None
        )

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
        self._set_up_emulator(
            start_emulator_fun=self.emulator.nfc_emulator.startDualNonPaymentPrefixEmulatorActivity)

        self._set_up_reader_and_assert_transaction(
            expected_service=_PREFIX_ACCESS_SERVICE,
            start_reader_fun=self.reader.nfc_reader.startDualNonPaymentPrefixReaderActivity if not
            self.pn532 else None
        )

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
        self._set_up_emulator(
            False, start_emulator_fun=self.emulator.nfc_emulator.startOffHostEmulatorActivity)

        self._set_up_reader_and_assert_transaction(
            expected_service=_OFFHOST_SERVICE,
            is_offhost=True,
            start_reader_fun=self.reader.nfc_reader.startOffHostReaderActivity
            if not self.pn532 else None)

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
        self._set_up_emulator(
            start_emulator_fun=self.emulator.nfc_emulator.startOnAndOffHostEmulatorActivity)

        self._set_up_reader_and_assert_transaction(
            expected_service=_TRANSPORT_SERVICE_1,
            is_offhost=True,
            start_reader_fun=self.reader.nfc_reader.startOnAndOffHostReaderActivity
            if not self.pn532 else None)

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
        self._set_up_emulator(
            service_list=[_TRANSPORT_SERVICE_2, _ACCESS_SERVICE],
            expected_service=_TRANSPORT_SERVICE_2,
            is_payment=False
        )

        self._set_up_reader_and_assert_transaction(
            expected_service = _TRANSPORT_SERVICE_2,
            start_reader_fun=self.reader.nfc_reader.startDualNonPaymentReaderActivity if not
            self.pn532 else None)

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
        self._set_up_emulator(
            service_list=[_TRANSPORT_SERVICE_1, _TRANSPORT_SERVICE_2],
            preferred_service=_TRANSPORT_SERVICE_2,
            expected_service=_TRANSPORT_SERVICE_2,
            is_payment=False
        )

        self._set_up_reader_and_assert_transaction(
            expected_service=_TRANSPORT_SERVICE_2, start_reader_fun=
            self.reader.nfc_reader.startForegroundNonPaymentReaderActivity
            if not self.pn532 else None)

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
        if self.pn532:
            command_apdus, response_apdus = get_apdus(self.emulator.nfc_emulator,
                                                      _THROUGHPUT_SERVICE)
            poll_and_transact(self.pn532, command_apdus, response_apdus)
        else:
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
         1. Verifies 50 successful APDU exchanges.
         """
        self._set_up_emulator(
            service_list=[_TRANSPORT_SERVICE_1],
            expected_service=_TRANSPORT_SERVICE_1
        )

        if self.pn532:
            command_apdus, response_apdus = get_apdus(self.emulator.nfc_emulator,
                                                      _TRANSPORT_SERVICE_1)
        for i in range(50):
            if self.pn532:
                tag_detected, transacted = poll_and_transact(self.pn532, command_apdus,
                                                             response_apdus)
                asserts.assert_true(
                    tag_detected, _FAILED_TAG_MSG
                )
                asserts.assert_true(transacted, _FAILED_TRANSACTION_MSG)
            else:
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
        self._set_up_emulator(
            start_emulator_fun=self.emulator.nfc_emulator.startLargeNumAidsEmulatorActivity
        )

        self._set_up_reader_and_assert_transaction(
            expected_service=_LARGE_NUM_AIDS_SERVICE, start_reader_fun=
            self.reader.nfc_reader.startLargeNumAidsReaderActivity
            if not self.pn532 else None)

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
        self._set_up_emulator(
            start_emulator_fun=self.emulator.nfc_emulator.startScreenOffPaymentEmulatorActivity,
            payment_default_service=_SCREEN_OFF_PAYMENT_SERVICE,
            is_payment=True
        )

        screen_off_handler = self.emulator.nfc_emulator.asyncWaitForScreenOff(
            'ScreenOff')
        self.emulator.nfc_emulator.turnScreenOff()
        screen_off_handler.waitAndGet('ScreenOff', _NFC_TIMEOUT_SEC)

        self._set_up_reader_and_assert_transaction(
            expected_service=_SCREEN_OFF_PAYMENT_SERVICE,
            start_reader_fun=self.reader.nfc_reader.startScreenOffPaymentReaderActivity if not
            self.pn532 else None
        )

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
        self._set_up_emulator(service_list=[_TRANSPORT_SERVICE_1,_TRANSPORT_SERVICE_2],
                              expected_service=_TRANSPORT_SERVICE_2, is_payment=False)
        if self.pn532:
            command_apdus, response_apdus = get_apdus(self.emulator.nfc_emulator, _TRANSPORT_SERVICE_2)
            poll_and_transact(self.pn532, command_apdus[:1], response_apdus[:1])
        else:
            self.reader.nfc_reader.startConflictingNonPaymentReaderActivity()
        self.emulator.nfc_emulator.selectItem()

        if self.pn532:
            tag_detected, transacted = poll_and_transact(self.pn532, command_apdus, response_apdus)
            asserts.assert_true(
                tag_detected, _FAILED_TAG_MSG
            )
            asserts.assert_true(transacted, _FAILED_TRANSACTION_MSG)
        else:
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
        self._set_up_emulator(
            start_emulator_fun=
                self.emulator.nfc_emulator.startConflictingNonPaymentPrefixEmulatorActivity,
            is_payment=False
        )
        if self.pn532:
            command_apdus, response_apdus = get_apdus(self.emulator.nfc_emulator,
                                                      _PREFIX_TRANSPORT_SERVICE_2)
            poll_and_transact(self.pn532, command_apdus[:1], response_apdus[:1])
        else:
            self.reader.nfc_reader.startConflictingNonPaymentPrefixReaderActivity()

        self.emulator.nfc_emulator.selectItem()

        if self.pn532:
            tag_detected, transacted = poll_and_transact(self.pn532, command_apdus, response_apdus)
            asserts.assert_true(tag_detected, _FAILED_TAG_MSG)
            asserts.assert_true(transacted, _FAILED_TRANSACTION_MSG)
        else:
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
        success = False
        self._set_up_emulator(
            service_list=[],
            expected_service=""
        )
        if self.pn532:
            for i in range(_NUM_POLLING_LOOPS):
                tag = self.pn532.poll_a()
                msg = None
                if tag is not None:
                    success, msg = parse_protocol_params(tag.sel_res, tag.ats)
                    self.pn532.mute()
                    break
                self.pn532.mute()
            asserts.assert_true(success, msg if msg is not None else _FAILED_TAG_MSG)
        else:
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
        self._set_up_emulator(
            start_emulator_fun=self.emulator.nfc_emulator.startScreenOnOnlyOffHostEmulatorActivity
        )
        self.emulator.nfc_emulator.turnScreenOff()
        screen_off_handler = self.emulator.nfc_emulator.asyncWaitForScreenOff(
            'ScreenOff')
        screen_off_handler.waitAndGet('ScreenOff', _NFC_TIMEOUT_SEC)
        if not self.pn532:
            self.reader.nfc_reader.closeActivity()

        self._set_up_reader_and_assert_transaction(
            expected_service=_SCREEN_ON_ONLY_OFF_HOST_SERVICE,
            is_offhost=True,
            start_reader_fun=self.reader.nfc_reader.startScreenOnOnlyOffHostReaderActivity if not
            self.pn532 else None
        )

        if self.pn532:
            self.pn532.mute()
        else:
            self.reader.nfc_reader.closeActivity()

        #Tests APDU exchange with screen on.
        screen_on_handler = self.emulator.nfc_emulator.asyncWaitForScreenOn(
            'ScreenOn')
        self.emulator.nfc_emulator.pressMenu()
        screen_on_handler.waitAndGet('ScreenOn', _NFC_TIMEOUT_SEC)

        self._set_up_reader_and_assert_transaction(
            expected_service=_SCREEN_ON_ONLY_OFF_HOST_SERVICE,
            is_offhost=True,
            start_reader_fun=self.reader.nfc_reader.startScreenOnOnlyOffHostReaderActivity if
            not self.pn532 else None
        )

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
        self._set_up_emulator(
            service_list=[_PAYMENT_SERVICE_1],
            expected_service=_PAYMENT_SERVICE_1,
            is_payment=True,
            payment_default_service=_PAYMENT_SERVICE_1
        )

        self.emulator.nfc_emulator.setNfcState(False)
        self.emulator.nfc_emulator.setNfcState(True)

        self._set_up_reader_and_assert_transaction(
            expected_service=_PAYMENT_SERVICE_1,
            start_reader_fun=self.reader.nfc_reader.startSinglePaymentReaderActivity if not
            self.pn532 else None)

    def teardown_test(self):
        if hasattr(self, 'emulator') and hasattr(self.emulator, 'nfc_emulator'):
            self.emulator.nfc_emulator.closeActivity()
            self.emulator.nfc_emulator.logInfo("*** TEST END: " + self.current_test_info.name +
                                               " ***")
        param_list = []
        if self.pn532:
            self.pn532.reset_buffers()
            self.pn532.mute()
            param_list = [[self.emulator]]
        elif hasattr(self, 'reader') and hasattr(self.reader, 'nfc_reader'):
            self.reader.nfc_reader.closeActivity()
            self.reader.nfc_reader.logInfo("*** TEST END: " + self.current_test_info.name + " ***")
            param_list = [[self.emulator], [self.reader]]
        utils.concurrent_exec(lambda d: d.services.create_output_excerpts_all(
            self.current_test_info),
                              param_list=param_list,
                              raise_on_exception=True)

if __name__ == '__main__':
    # Take test args
    if '--' in sys.argv:
        index = sys.argv.index('--')
        sys.argv = sys.argv[:1] + sys.argv[index + 1:]
    test_runner.main()
