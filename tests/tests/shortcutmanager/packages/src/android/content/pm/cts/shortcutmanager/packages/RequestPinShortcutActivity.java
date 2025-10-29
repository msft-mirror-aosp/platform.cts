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

import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.content.pm.cts.shortcutmanager.common.Constants;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.PersistableBundle;
import android.util.Log;

import androidx.annotation.Nullable;

import java.util.List;

public class RequestPinShortcutActivity extends Activity {

    public static final String TAG = "RequestPinShortcutActivity";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "activity started");
    }

    @Override
    protected void onResume() {
        super.onResume();
        final ShortcutManager shortcutManager = getSystemService(ShortcutManager.class);
        assertTrue(shortcutManager.isRequestPinShortcutSupported());
        final PersistableBundle extras = new PersistableBundle();
        extras.putString(Constants.EXTRA_REPLY_ACTION,
                getIntent().getStringExtra(Constants.EXTRA_REPLY_ACTION));
        extras.putString(Constants.LABEL, "Bal Test Shortcut");
        final ShortcutInfo shortcut = new ShortcutInfo.Builder(this, "bal_test_shortcut")
                .setShortLabel("Bal Test Shortcut")
                .setExtras(extras)
                .setIntent((new Intent(Intent.ACTION_VIEW)).setData(Uri.parse("https://google.com")))
                .build();
        final IntentSender pinCallback = getIntent().getParcelableExtra(
                Constants.EXTRA_TARGET_INTENT, IntentSender.class);
        runOnForegroundWithRetry(() -> {
            shortcutManager.requestPinShortcut(shortcut, pinCallback);
            Log.i(TAG, "requested pin shortcut");
            finish();
        });
    }

    void runOnForegroundWithRetry(Runnable runnable) {
        ActivityManager am = getSystemService(ActivityManager.class);
        // AM will only return our process since we don't have REAL_GET_TASKS or
        // INTERACT_ACROSS_USERS.
        List<ActivityManager.RunningAppProcessInfo> processes = am.getRunningAppProcesses();
        Log.i(TAG, "Checking if activity is in foreground");
        if (processes != null && !processes.isEmpty() && processes.getFirst().importance <=
                ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
            Log.i(TAG, "Activity is now in foreground");
            runnable.run();
            return;
        }
        // Try again in one second.
        (new Handler(getMainLooper()))
                .postDelayed(() -> runOnForegroundWithRetry(runnable), 1000);
    }
}
