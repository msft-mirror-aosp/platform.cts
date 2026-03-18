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

import static android.telephony.SmsManager.RESULT_ERROR_NO_SERVICE;
import static android.telephony.cts.msgupgrade.MessageTestParamsHelper.MessageType;

import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.provider.Telephony;
import android.service.messaging.AlternativeMessageTransportService;
import android.telephony.cts.MessageUpgradeUtils;
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
        Log.d(TAG, "Received new message upgrade request. Uri: " + contentUri);

        MessageTestParamsHelper paramsHelper = new MessageTestParamsHelper(getContentResolver());
        MessageTestParamsHelper.UpgradeParams upgradeParams =
                paramsHelper.getUpgradeParamsIfAvailable(contentUri);
        if (upgradeParams == null) {
            Log.d(TAG, "Unable to parse upgrade parameters for URI: " + contentUri);
            return;
        }

        Executor delayedExecutor =
                CompletableFuture.delayedExecutor(upgradeParams.delayMs(), TimeUnit.MILLISECONDS);
        delayedExecutor.execute(
                () -> {
                    int status = upgradeParams.status();
                    upgradeStatus.accept(status);
                    broadcastUpgradeStatus(contentUri, status);

                    if (status == UPGRADE_STATUS_ACCEPTED) {
                        triggerDatabaseUpdate(
                                contentUri,
                                upgradeParams.messageState(),
                                upgradeParams.messageType());
                    }
                });
    }

    private void broadcastUpgradeStatus(Uri uri, int status) {
        Intent intent = new Intent(ACTION_MESSAGE_UPGRADE_RECEIVED);
        intent.putExtra(EXTRA_MESSAGE_URI, uri);
        intent.putExtra(EXTRA_UPGRADE_STATUS, status);
        intent.setPackage("android.telephony.cts");
        sendBroadcast(intent);
    }

    private void triggerDatabaseUpdate(
            Uri uri, MessageUpgradeUtils.MessageState state, MessageType type) {
        if (type == MessageType.SMS) {
            simulateSmsUpdate(uri, state);
        }
        // TODO(b/481642941): Implement MMS update simulation
    }

    private void simulateSmsUpdate(Uri smsUri, MessageUpgradeUtils.MessageState state) {
        if (smsUri == null) return;

        ContentValues values = new ContentValues();

        switch (state) {
            case MessageUpgradeUtils.MessageState.SENT_AND_DELIVERED -> {
                values.put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT);
                values.put(Telephony.Sms.STATUS, Telephony.Sms.STATUS_COMPLETE);
                values.put(Telephony.Sms.ERROR_CODE, 0);
            }

            case MessageUpgradeUtils.MessageState.SENT_BUT_PENDING -> {
                values.put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT);
                values.put(Telephony.Sms.STATUS, Telephony.Sms.STATUS_PENDING);
            }

            case MessageUpgradeUtils.MessageState.FAILED_TO_SEND -> {
                values.put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_FAILED);
                values.put(Telephony.Sms.ERROR_CODE, RESULT_ERROR_NO_SERVICE);
            }

            case MessageUpgradeUtils.MessageState.FAILED_TO_DELIVER -> {
                values.put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT);
                values.put(Telephony.Sms.STATUS, Telephony.Sms.STATUS_FAILED);
            }
        }

        values.put(Telephony.Sms.DATE_SENT, System.currentTimeMillis());

        try {
            int rows = getContentResolver().update(smsUri, values, null, null);
            Log.d(TAG, "Simulated " + state + " for " + smsUri + ". Rows: " + rows);
        } catch (Exception e) {
            Log.e(TAG, "Simulation failed: " + e.getMessage());
        }
    }
}
