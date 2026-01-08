/*
 * Copyright (C) 2026 The Android Open Source Project
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

package android.server.biometrics.wallet;

import android.app.Activity;
import android.content.Intent;
import android.hardware.biometrics.BiometricManager;
import android.os.Bundle;
import android.server.biometrics.util.WalletTestHelperConstants;

import java.util.Map;

public class TestHelperActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getBiometricSensorStrengthsAndBroadcastResults();
        finish();
    }

    /**
     * Retrieves biometric sensor strengths and broadcasts the results.
     *
     * <p>This method queries {@link BiometricManager#getBiometricSensorStrengths} and packages the
     * data into two parallel integer arrays: one for sensor modalities and another for their
     * corresponding {@link BiometricSensorStrength} values.
     */
    private void getBiometricSensorStrengthsAndBroadcastResults() {
        Intent broadcastIntent = new Intent(WalletTestHelperConstants.INTENT_RESULT);
        broadcastIntent.setPackage("android.server.biometrics.cts");
        try {
            BiometricManager bm = getSystemService(BiometricManager.class);
            Map<Integer, Integer> biometricSensorStrengths = bm.getBiometricSensorStrengths();
            int[] modalities = new int[biometricSensorStrengths.size()];
            int[] strengths = new int[biometricSensorStrengths.size()];
            int i = 0;
            for (Map.Entry<Integer, Integer> entry : biometricSensorStrengths.entrySet()) {
                modalities[i] = entry.getKey();
                strengths[i] = entry.getValue();
                i++;
            }
            broadcastIntent.putExtra(WalletTestHelperConstants.INTENT_EXTRA_MODALITIES, modalities);
            broadcastIntent.putExtra(WalletTestHelperConstants.INTENT_EXTRA_STRENGTHS, strengths);
        } catch (Exception e) {
            broadcastIntent.putExtra(WalletTestHelperConstants.INTENT_EXTRA_EXCEPTION, e);
        }
        sendBroadcast(broadcastIntent);
    }
}
