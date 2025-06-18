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

package com.android.cts.locktask;

import android.app.Activity;
import android.content.Intent;
import android.util.Log;

public class LockTaskActivity extends Activity {
    private static final String TAG = "LockTaskActivity";

    static volatile boolean sIsActivityResumed;

    @Override
    protected void onNewIntent(Intent intent) {
        Log.d(TAG, "onNewIntent: intent=" + intent);
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    @Override
    protected void onCreate(android.os.Bundle savedInstanceState) {
        Log.d(TAG, "onCreate: intent=" + getIntent());
        super.onCreate(savedInstanceState);
        handleIntent(getIntent());
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy");
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume");
        sIsActivityResumed = true;
        super.onResume();
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause");
        sIsActivityResumed = false;
        super.onPause();
    }

    private void handleIntent(Intent intent) {
        if (intent.getBooleanExtra(LockTaskActivityStateHelper.EXTRA_START_LOCK_TASK, false)) {
            Log.d(TAG, "Starting lock task");
            startLockTask();
        }
    }
}
