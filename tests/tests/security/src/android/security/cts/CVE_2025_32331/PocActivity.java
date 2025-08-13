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

package android.security.cts.CVE_2025_32331;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Intent;
import android.util.Log;

public class PocActivity extends Activity {

    @Override
    public void onResume() {
        try {
            super.onResume();

            // Send broadcast to 'CVE_2025_32331' to share the taskId.
            // When 'PocActivity' is pinned or unpinned, 'onResume()' is invoked.
            // Send broadcast to 'CVE_2025_32331', confirming that the app is
            // successfully pinned.
            final Intent intent =
                    new Intent(CVE_2025_32331.mBroadcastAction)
                            .putExtra(CVE_2025_32331.mTaskId, getTaskId());
            final ActivityManager activityManager = getSystemService(ActivityManager.class);
            if (activityManager.getLockTaskModeState() == ActivityManager.LOCK_TASK_MODE_PINNED) {
                intent.putExtra(CVE_2025_32331.mIsAppPinned, true);
            }
            sendBroadcast(intent);
        } catch (Exception e) {
            Log.d(
                    CVE_2025_32331.TAG,
                    String.format(
                            "Exception occurred in PocActivity::onResume: %s", e.getMessage()));
        }
    }

    @Override
    public void onPause() {
        try {
            super.onPause();

            // Send broadcast to 'CVE_2025_32331' to inform that device is going to background.
            sendBroadcast(
                    new Intent(CVE_2025_32331.mBroadcastAction)
                            .putExtra(CVE_2025_32331.mPocActivityOnPause, true));
        } catch (Exception e) {
            Log.d(
                    CVE_2025_32331.TAG,
                    String.format(
                            "Exception occurred in PocActivity::onPause: %s", e.getMessage()));
        }
    }
}
