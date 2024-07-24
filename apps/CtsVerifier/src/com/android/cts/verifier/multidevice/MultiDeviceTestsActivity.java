/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.cts.verifier.multidevice;

import com.android.cts.verifier.HostTestsActivity;
import com.android.cts.verifier.HostTestsActivity.HostTestCategory;
import com.android.cts.verifier.R;

/** Activity for general multi-device tests in CtsVerifier. */
public class MultiDeviceTestsActivity extends HostTestsActivity {
  private static final String TAG = "MultiDeviceTestsActivity";

  public MultiDeviceTestsActivity() {
    super(
        R.string.nfc_tests_dialog_title,
        R.string.nfc_tests_dialog_content,
        new HostTestCategory("Multidevice Tests")
            .addTest(
                "CtsNfcHceMultiDeviceTestCases#test_conflicting_non_payment",
                "CtsNfcHceMultiDeviceTestCases#test_conflicting_non_payment")
            .addTest(
                "CtsNfcHceMultiDeviceTestCases#test_conflicting_non_payment_prefix",
                "CtsNfcHceMultiDeviceTestCases#test_conflicting_non_payment_prefix")
            .addTest(
                "CtsNfcHceMultiDeviceTestCases#test_dual_non_payment",
                "CtsNfcHceMultiDeviceTestCases#test_dual_non_payment")
            .addTest(
                "CtsNfcHceMultiDeviceTestCases#test_dual_payment_service",
                "CtsNfcHceMultiDeviceTestCases#test_dual_payment_service")
            .addTest(
                "CtsNfcHceMultiDeviceTestCases#test_dynamic_aid_emulator",
                "CtsNfcHceMultiDeviceTestCases#test_dynamic_aid_emulator")
            .addTest(
                "CtsNfcHceMultiDeviceTestCases#test_foreground_non_payment",
                "CtsNfcHceMultiDeviceTestCases#test_foreground_non_payment")
            .addTest(
                "CtsNfcHceMultiDeviceTestCases#test_foreground_payment_emulator",
                "CtsNfcHceMultiDeviceTestCases#test_foreground_payment_emulator")
            .addTest(
                "CtsNfcHceMultiDeviceTestCases#test_large_num_aids",
                "CtsNfcHceMultiDeviceTestCases#test_large_num_aids")
            .addTest(
                "CtsNfcHceMultiDeviceTestCases#test_offhost_service",
                "CtsNfcHceMultiDeviceTestCases#test_offhost_service")
            .addTest(
                "CtsNfcHceMultiDeviceTestCases#test_on_and_offhost_service",
                "CtsNfcHceMultiDeviceTestCases#test_on_and_offhost_service")
            .addTest(
                "CtsNfcHceMultiDeviceTestCases#test_other_prefix",
                "CtsNfcHceMultiDeviceTestCases#test_other_prefix")
            .addTest(
                "CtsNfcHceMultiDeviceTestCases#test_payment_prefix_emulator",
                "CtsNfcHceMultiDeviceTestCases#test_payment_prefix_emulator")
            .addTest(
                "CtsNfcHceMultiDeviceTestCases#test_prefix_payment_emulator_2",
                "CtsNfcHceMultiDeviceTestCases#test_prefix_payment_emulator_2")
            .addTest(
                "CtsNfcHceMultiDeviceTestCases#test_protocol_params",
                "CtsNfcHceMultiDeviceTestCases#test_protocol_params")
            .addTest(
                "CtsNfcHceMultiDeviceTestCases#test_screen_off_payment",
                "CtsNfcHceMultiDeviceTestCases#test_screen_off_payment")
            .addTest(
                "CtsNfcHceMultiDeviceTestCases#test_screen_on_only_off_host_service",
                "CtsNfcHceMultiDeviceTestCases#test_screen_on_only_off_host_service")
            .addTest(
                "CtsNfcHceMultiDeviceTestCases#test_single_non_payment_service",
                "CtsNfcHceMultiDeviceTestCases#test_single_non_payment_service")
            .addTest(
                "CtsNfcHceMultiDeviceTestCases#test_single_payment_service",
                "CtsNfcHceMultiDeviceTestCases#test_single_payment_service")
            .addTest(
                "CtsNfcHceMultiDeviceTestCases#test_tap_50_times",
                "CtsNfcHceMultiDeviceTestCases#test_tap_50_times")
            .addTest(
                "CtsNfcHceMultiDeviceTestCases#test_throughput",
                "CtsNfcHceMultiDeviceTestCases#test_throughput"));
  }
}
