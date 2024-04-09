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

from mobly import asserts
from mobly import base_test
from mobly import test_runner
from mobly import utils
from mobly.controllers import android_device

# Timeout to give the NFC service time to perform async actions such as
# discover tags.
_NFC_TIMEOUT_SEC = 10


class CtsNfcHceMultiDeviceTestCases(base_test.BaseTestClass):
    def setup_class(self):
        """
        Sets up class by registering two devices, enabling nfc on them,
        and loading snippets.
        """
        self.emulator, self.reader = self.register_controller(android_device,
                                                              min_number=2)[:2]
        self.reader.load_snippet('nfc_reader',
                                 'com.android.cts.nfc.multidevice.reader')
        self.emulator.load_snippet('nfc_emulator',
                                   'com.android.cts.nfc.multidevice.emulator')

        self.reader.adb.shell(['svc', 'nfc', 'enable'])
        self.emulator.adb.shell(['svc', 'nfc', 'enable'])

        self.reader.debug_tag = 'reader'
        self.emulator.debug_tag = 'emulator'

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

    def teardown_test(self):
        self.emulator.nfc_emulator.closeActivity()
        self.reader.nfc_reader.closeActivity()
        utils.concurrent_exec(lambda d: d.services.create_output_excerpts_all(
            self.current_test_info),
                              param_list=[[self.emulator], [self.reader]],
                              raise_on_exception=True)


if __name__ == '__main__':
    # Take test args
    if '--' in sys.argv:
        index = sys.argv.index('--')
        sys.argv = sys.argv[:1] + sys.argv[index + 1:]
    test_runner.main()
