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
package android.security.cts.CVE_2022_20477;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

public class PocActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Intent intent = null;
        try {
            intent = new Intent("CVE_2022_20477_action");
            super.onCreate(savedInstanceState);

            // Add Button with text "CVE_2022_20477_PocActivity_is_visible" to PocActivity's
            // ContentView which will be used in CVE_2022_20477.java to detect
            // presence of PocActivity on lockscreen
            Button button = new Button(this);
            button.setText("CVE_2022_20477_PocActivity_is_visible");
            setContentView(button);

            // Show PocActivity on lockscreen. This will set keyguard state as occluded and
            // reproduce the bug
            setShowWhenLocked(true);

            // Send broadcast to be received in CVE_2022_20477.java indicating the occurrence of any
            // exceptions in this activity
            sendBroadcast(intent.putExtra("exceptionMessageKey", (Exception) null));
        } catch (Exception e) {
            try {
                if (intent != null) {
                    sendBroadcast(intent.putExtra("exceptionMessageKey", e));
                }
            } catch (Exception ignored) {
                // Ignore any exceptions
            }
        }
    }
}
