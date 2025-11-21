/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.cts.launcherapps.simpleapp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;

/**
 * A simple activity to install for various users to test LauncherApps.
 */
public class SimpleActivity extends Activity {
    public static String ACTIVITY_LAUNCHED_ACTION =
            "com.android.cts.launchertests.LauncherAppsTests.LAUNCHED_ACTION";
    private static final int ACTION_ANR = 3;
    private static final int ACTION_NONE = 0;
    private static final String EXTRA_ACTION = "action";


    private static final String TAG = "SimpleActivity";

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        Intent intent = getIntent();
        int action = intent.getIntExtra(EXTRA_ACTION, ACTION_NONE);
        if (action == ACTION_ANR) {
            try {
                Thread.sleep(3600 * 1000);
            } catch (InterruptedException e) {
            }
        }

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        Log.i(TAG, "Created for user " + android.os.Process.myUserHandle());
    }

    @Override
    public void onStart() {
        super.onStart();
        Intent reply = new Intent();
        reply.setAction(ACTIVITY_LAUNCHED_ACTION);
        sendBroadcast(reply);

        final WindowInsetsController insetsController = getWindow().getInsetsController();
        if (insetsController != null) {
            insetsController.hide(WindowInsets.Type.navigationBars());
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent.getExtras().getBoolean("finish")) {
            finish();
        }
    }
}
