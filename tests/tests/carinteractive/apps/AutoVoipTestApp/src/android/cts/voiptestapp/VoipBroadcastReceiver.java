/*
 * Copyright 2026 The Android Open Source Project
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
package android.cts.voiptestapp;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * {@link BroadcastReceiver} that receives intents from users interacting with the ringing call
 * notification.
 */
public class VoipBroadcastReceiver extends BroadcastReceiver {

    private static final String TAG = "VTA.VoipBroadcastReceiver";

    private static final String PLACE_CALL_INTENT = "PLACE_CALL";
    private static final String ANSWER_INTENT = "ANSWER_CALL";
    private static final String DECLINE_INTENT = "DECLINE_CALL";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(TAG, "Received notification broadcast with action: " + intent.getAction());

        switch (intent.getAction()) {
            case ANSWER_INTENT -> {
                // no-op
            }
            case DECLINE_INTENT -> VoipCallManager.getInstance(context).clearConnections();
            case PLACE_CALL_INTENT -> VoipCallManager.getInstance(context).placeOutgoingCall();
            default -> {
                // no-op
            }
        }

        VoipCallManager.getInstance(context).stopNotificationService();
    }

    /** Returns the PendingIntent for answering a call */
    public static PendingIntent getAnswerPendingIntent(Context context) {
        Intent answerIntent = new Intent();
        answerIntent.setAction(ANSWER_INTENT);
        answerIntent.setComponent(new ComponentName(context, VoipBroadcastReceiver.class));
        answerIntent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        return PendingIntent.getBroadcast(
                context,
                0,
                answerIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /** Returns the PendingIntent for declining a call */
    public static PendingIntent getDeclinePendingIntent(Context context) {
        Intent declineIntent = new Intent();
        declineIntent.setAction(DECLINE_INTENT);
        declineIntent.setComponent(new ComponentName(context, VoipBroadcastReceiver.class));
        declineIntent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        return PendingIntent.getBroadcast(
                context,
                0,
                declineIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }
}
