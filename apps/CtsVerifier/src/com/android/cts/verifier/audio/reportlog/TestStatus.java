/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.cts.verifier.audio.reportlog;

/** Defines constants representing the status of a test. */
public enum TestStatus {
    TEST_STATUS_UNSPECIFIED(0),

    // The test passed.
    TEST_STATUS_PASSED(1),

    // The test failed.
    TEST_STATUS_FAILED(2),

    // The test was skipped.
    TEST_STATUS_SKIPPED(3),

    // The test was not run.
    TEST_STATUS_NOT_RUN(4),

    // The test was skipped because the device does not meet the requirements (e.g., not handheld,
    // lacks peripheral support, or is an emulator).
    TEST_STATUS_SKIPPED_UNSUPPORTED_DEVICE(5),

    // The test is considered passed because all its sub-tests or checks were executed,
    // regardless of their individual outcomes.
    TEST_STATUS_PASSED_ON_COMPLETION(6);

    private final int mValue;

    TestStatus(int value) {
        mValue = value;
    }

    public int getValue() {
        return mValue;
    }

    public static TestStatus fromValue(int value) {
        for (TestStatus status : TestStatus.values()) {
            if (status.mValue == value) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown TestStatus value: " + value);
    }
}
