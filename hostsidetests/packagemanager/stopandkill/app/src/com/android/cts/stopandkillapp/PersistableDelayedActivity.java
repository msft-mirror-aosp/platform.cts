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

package com.android.cts.stopandkillapp;

import android.app.Activity;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.concurrent.TimeUnit;

/**
 * An activity that is persistable and takes a moderate amount of time (9s) to save its state. This
 * is used to test that the package manager correctly waits for the full 15s timeout when no sibling
 * apps are running in a shared UID.
 */
public class PersistableDelayedActivity extends Activity {
    private static final String TAG = "PersistableDelayedActivity";
    private static final long SLEEP_DURATION_SECONDS = 9;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate");
    }

    @Override
    public void onSaveInstanceState(
            @NonNull Bundle outState, @NonNull PersistableBundle outPersistentState) {
        super.onSaveInstanceState(outState, outPersistentState);
        // Sleep for 9 seconds. This is more than the 3s shared-UID timeout but less than the
        // 15s default timeout.
        SystemClock.sleep(TimeUnit.SECONDS.toMillis(SLEEP_DURATION_SECONDS));
        // This should be reached if we wait for at least 9 seconds.
        StateManager.createStateFile(this);
    }
}
