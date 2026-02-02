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

package android.telephony.cts.msgupgrade;

import android.content.Intent;
import android.net.Uri;
import android.service.messaging.AlternativeMessageTransportService;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class CtsAlternativeMessageTransportService extends AlternativeMessageTransportService {
    private static final String TAG = CtsAlternativeMessageTransportService.class.getSimpleName();
    private static final String ACTION_MESSAGE_UPGRADE_RECEIVED =
            "android.telephony.cts.msgupgrade.ACTION_MESSAGE_UPGRADE_RECEIVED";
    private static final String EXTRA_MESSAGE_URI = "message_uri";
    private static final String EXTRA_UPGRADE_STATUS =
            "android.telephony.cts.msgupgrade.EXTRA_UPGRADE_STATUS";

    @Override
    public void onMessageUpgradeRequested(
            @NonNull Uri contentUri, @NonNull final Consumer<Integer> upgradeStatus) {
        Log.d(TAG, "Received new message upgrade request.");
        MessageTestParamsHelper params = new MessageTestParamsHelper(getContentResolver());
        MessageTestParamsHelper.UpgradeParams upgradeParams =
                params.getUpgradeParamsIfAvailable(contentUri);
        Executor delayedExecutor =
                CompletableFuture.delayedExecutor(upgradeParams.delayMs(), TimeUnit.MILLISECONDS);
        delayedExecutor.execute(
                () -> {
                    if (upgradeParams instanceof MessageTestParamsHelper.UpgradeReady) {
                        int status =
                                ((MessageTestParamsHelper.UpgradeReady) upgradeParams).status();
                        upgradeStatus.accept(status);

                        // Send broadcast to notify test after accepting the request
                        Intent intent = new Intent(ACTION_MESSAGE_UPGRADE_RECEIVED);
                        intent.putExtra(EXTRA_MESSAGE_URI, contentUri);
                        intent.putExtra(EXTRA_UPGRADE_STATUS, status);
                        intent.setPackage("android.telephony.cts");
                        sendBroadcast(intent);
                    }
                });
    }
}
