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

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Person;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

/** Foreground service for ongoing/ringing calls. */
public class NotificationService extends Service {

    public static final String TAG = "VTA.NotificationService";

    private static final String NOTIFICATION_CHANNEL_ID = "NOTIFICATION_CHANNEL";

    @Override
    public void onCreate() {
        super.onCreate();
        Person person = new Person.Builder().setName(getString(R.string.caller_name)).build();
        // Create notification channel so that it has priority to show up as a HUN.
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        NotificationChannel notificationChannel =
                new NotificationChannel(
                        NOTIFICATION_CHANNEL_ID,
                        getString(R.string.notification_channel_name),
                        NotificationManager.IMPORTANCE_HIGH);

        notificationChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);

        notificationManager.createNotificationChannel(notificationChannel);
        Notification notif =
                new Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
                        .setSmallIcon(R.drawable.app_icon)
                        .setStyle(
                                Notification.CallStyle.forIncomingCall(
                                        person,
                                        VoipBroadcastReceiver.getDeclinePendingIntent(this),
                                        VoipBroadcastReceiver.getAnswerPendingIntent(this)))
                        .build();
        startForeground(1, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL);
        Log.i(TAG, "Starting foreground service notification");
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "Binding to foreground service");
        return new Binder();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.i(TAG, "Foreground service unbinding");
        stopForeground(Service.STOP_FOREGROUND_REMOVE);
        return true;
    }
}
