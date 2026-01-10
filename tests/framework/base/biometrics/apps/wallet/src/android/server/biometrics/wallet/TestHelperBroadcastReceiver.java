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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.biometrics.BiometricManager;
import android.server.biometrics.util.WalletTestHelperConstants;

public class TestHelperBroadcastReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        final PendingResult pendingResult = goAsync();
        final Context appContext = context.getApplicationContext();
        new Thread(
                        () -> {
                            try {
                                Intent broadcast =
                                        new Intent(
                                                WalletTestHelperConstants.BACKGROUND_INTENT_RESULT);
                                broadcast.setPackage("android.server.biometrics.cts");
                                try {
                                    BiometricManager bm =
                                            appContext.getSystemService(BiometricManager.class);
                                    bm.getBiometricSensorStrengths();
                                } catch (Exception e) {
                                    broadcast.putExtra(
                                            WalletTestHelperConstants
                                                    .BACKGROUND_INTENT_EXTRA_EXCEPTION,
                                            e);
                                }
                                appContext.sendBroadcast(broadcast);
                            } finally {
                                pendingResult.finish();
                            }
                        })
                .start();
    }
}
