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

package android.voiceinteraction.cts.services;

import android.os.PersistableBundle;
import android.os.Process;
import android.os.SharedMemory;
import android.service.voice.AlwaysOnHotwordDetector;
import android.service.voice.HotwordDetectedResult;
import android.service.voice.HotwordDetectionService;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.function.IntConsumer;

public class PccHotwordDetectionService extends HotwordDetectionService {
    static final String TAG = "PccHotwordDetectionService";

    public static final String KEY_TEST_SCENARIO = "test_scenario";
    public static final int SCENARIO_VERIFY_PCC_PROPERTIES = 1;

    // Result codes sent back in the HotwordDetectedResult
    public static final int RESULT_CODE_PCC_UID_SUCCESS = 101;
    public static final int RESULT_CODE_PCC_UID_FAILURE = 102;
    public static final int RESULT_CODE_ISOLATED_SUCCESS = 201; // Process.isIsolated() is false
    public static final int RESULT_CODE_ISOLATED_FAILURE = 202; // Process.isIsolated() is true

    private boolean mVerifyPccProperties = false;
    private int mPccUidResult = 0;
    private int mIsIsolatedResult = 0;

    @Override
    public void onUpdateState(
            @Nullable PersistableBundle options,
            @Nullable SharedMemory sharedMemory,
            long callbackTimeoutMillis,
            @Nullable IntConsumer statusCallback) {
        if (options != null && options.getInt(KEY_TEST_SCENARIO, -1)
                == SCENARIO_VERIFY_PCC_PROPERTIES) {
            Log.d(TAG, "Received request to verify PCC properties");
            mVerifyPccProperties = true;
            mPccUidResult = Process.isPrivateComputeCoreUid(Process.myUid())
                    ? RESULT_CODE_PCC_UID_SUCCESS : RESULT_CODE_PCC_UID_FAILURE;
            mIsIsolatedResult = !Process.isIsolated()
                    ? RESULT_CODE_ISOLATED_SUCCESS : RESULT_CODE_ISOLATED_FAILURE;
        }

        if (statusCallback != null) {
            statusCallback.accept(INITIALIZATION_STATUS_SUCCESS);
        }
    }

    @Override
    public void onDetect(@NonNull AlwaysOnHotwordDetector.EventPayload eventPayload,
            long timeoutMillis, @NonNull Callback callback) {
        Log.d(TAG, "onDetect called");
        if (mVerifyPccProperties) {
            Log.d(TAG, "Sending PCC verification results");
            callback.onDetected(new HotwordDetectedResult.Builder()
                    .setScore(mPccUidResult)
                    .setPersonalizedScore(mIsIsolatedResult)
                    .build());
            // Reset for next test
            mVerifyPccProperties = false;
        } else {
             // Send a generic result for other tests
            callback.onDetected(new HotwordDetectedResult.Builder().setScore(1).build());
        }
    }
}
