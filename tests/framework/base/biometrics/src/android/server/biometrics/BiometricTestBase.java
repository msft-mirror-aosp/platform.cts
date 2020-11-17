/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.server.biometrics;

import android.server.wm.ActivityManagerTestBase;
import android.server.wm.Condition;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.function.BooleanSupplier;

/**
 * Base class for biometric tests, containing common useful logic.
 */
public class BiometricTestBase extends ActivityManagerTestBase {

    private static final String TAG = "BiometricTestBase";

    /**
     * Waits for the service to become idle
     * @param state Current service state
     * @throws Exception
     */
    protected void waitForIdleService(@NonNull SensorStates state) throws Exception {
        for (int i = 0; i < 10; i++) {
            if (!state.areAllSensorsIdle()) {
                Log.d(TAG, "Not idle yet..");
                Thread.sleep(300);
            } else {
                return;
            }
        }
        Log.d(TAG, "Timed out waiting for idle");
    }

    protected void waitForBusySensor(@NonNull SensorStates state, int sensorId) throws Exception {
        for (int i = 0; i < 10; i++) {
            if (!state.sensorStates.get(sensorId).isBusy()) {
                Log.d(TAG, "Not busy yet..");
                Thread.sleep(300);
            } else {
                return;
            }
        }
        Log.d(TAG, "Timed out waiting to become busy");
    }

    protected static void waitFor(@NonNull String message, @NonNull BooleanSupplier condition) {
        Condition.waitFor(new Condition<>(message, condition)
                .setRetryIntervalMs(500)
                .setRetryLimit(20));
    }
}
