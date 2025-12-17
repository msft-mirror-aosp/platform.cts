/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.server.wm.backgroundactivity.appa;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class PipActivity extends Activity {

    private static final String TAG = "PipActivity";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onPause() {
        Log.i(TAG, "onPause");
        Instant t0 = SystemClock.elapsedRealtimeClock().instant();
        super.onPause();

        Log.i(TAG, "enterPictureInPictureMode");
        enterPictureInPictureMode();

        // Try to start background activity once it's onPause(), like after pressing home button.
        final Intent intent = new Intent();
        intent.setClass(this, BackgroundActivity.class);
        Log.i(TAG, "startActivity: " + intent);
        startActivity(intent);

        // Start activity again after 6s to ensure it's really blocked and can't be resumed.
        new Thread() {
            public void run() {
                for (int i : new int[] {4, 6, 8}) {
                    Instant t = SystemClock.elapsedRealtimeClock().instant();
                    Duration duration = Duration.between(t, t0.plus(i, ChronoUnit.SECONDS));
                    if (duration.isPositive()) {
                        SystemClock.sleep(duration.toMillis());
                        Log.i(TAG, "startActivity after " + i + "s: " + intent);
                        startActivity(intent);
                    } else {
                        Log.w(TAG, "[skip] startActivity after " + i + "s: " + intent);
                    }
                }
            }
        }.start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}