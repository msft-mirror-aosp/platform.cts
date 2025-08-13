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
import android.content.Intent;
import android.util.Log;
import android.widget.TextView;

public class HelperActivity extends Activity {

    @Override
    public void onResume() {
        try {
            super.onResume();

            // Add a 'TextView' with a unique string.
            final TextView textView = new TextView(this);
            textView.setText(CVE_2025_32331.mUniqueString);
            setContentView(textView);

            // Send broadcast to 'CVE_2025_32331' informing 'HelperActivity' was launched.
            sendBroadcast(
                    new Intent(CVE_2025_32331.mBroadcastAction)
                            .putExtra(CVE_2025_32331.mHelperActivityLaunched, true));
        } catch (Exception e) {
            Log.d(
                    CVE_2025_32331.TAG,
                    String.format("Exception occurred in HelperActivity: %s", e.getMessage()));
        }
    }
}
