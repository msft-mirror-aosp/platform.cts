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

import androidx.annotation.NonNull;

import java.util.concurrent.TimeUnit;

public class NonPersistableActivity extends Activity {
    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        StateManager.createStateFile(this);
    }

    @Override
    public void onSaveInstanceState(
            @NonNull Bundle outState, @NonNull PersistableBundle outPersistentState) {
        super.onSaveInstanceState(outState, outPersistentState);
        // Sleep a short duration
        SystemClock.sleep(TimeUnit.SECONDS.toMillis(5));
        // This part should not be reached since system will not wait to finish stopping
        StateManager.createStateFile(this);
    }
}
