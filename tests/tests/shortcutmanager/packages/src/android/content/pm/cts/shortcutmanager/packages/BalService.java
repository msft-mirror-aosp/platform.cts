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
package android.content.pm.cts.shortcutmanager.packages;

import android.app.ActivityOptions;
import android.app.Service;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.cts.shortcutmanager.common.Constants;
import android.content.pm.cts.shortcutmanager.common.ReplyUtil;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;

public class BalService extends Service {

    private final Handler mHandler = new Handler(Looper.getMainLooper());

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i("BalService", "Service started");
        mHandler.postDelayed(() -> {
            try {
                intent.getParcelableExtra(Constants.EXTRA_TARGET_INTENT, IntentSender.class)
                    .sendIntent(this, 0, null, null, ActivityOptions.makeBasic()
                        .setPendingIntentBackgroundActivityStartMode(
                            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS)
                        .toBundle(), null, null);
            } catch (IntentSender.SendIntentException e) {
                throw new RuntimeException(e);
            }
            ReplyUtil.sendSuccessReply(this, intent.getStringExtra(Constants.EXTRA_REPLY_ACTION));
            Log.i("BalService", "Started intent from background");
        }, 10 * 1000);
        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
