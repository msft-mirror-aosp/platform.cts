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
package com.example.pcc.host.test.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;

public class PccForegroundService extends EmptyPccService {

    private static final String CHANNEL_ID = "pcc_test_channel";
    private static final int NOTIFICATION_ID = 1;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        NotificationManager notificationManager = getSystemService(NotificationManager.class);

        // Create the NotificationChannel (required for Android 8.0+)
        NotificationChannel channel =
                new NotificationChannel(CHANNEL_ID, "PCC Test", NotificationManager.IMPORTANCE_LOW);
        notificationManager.createNotificationChannel(channel);

        // Build the notification
        Notification notification =
                new Notification.Builder(this, CHANNEL_ID)
                        .setContentTitle("PCC Test Service")
                        .setContentText("Running...")
                        .setSmallIcon(android.R.drawable.ic_dialog_info) // Default system icon
                        .build();

        // Promote the service to the foreground
        startForeground(NOTIFICATION_ID, notification);

        return START_NOT_STICKY;
    }
}
