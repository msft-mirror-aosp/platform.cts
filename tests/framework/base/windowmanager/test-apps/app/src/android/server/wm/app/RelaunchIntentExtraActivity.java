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

package android.server.wm.app;

import static android.server.wm.app.Components.RelaunchIntentExtraActivity.RELAUNCH_TARGET_ACTIVITY;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/** This activity will start a new activity with forwarding the extras from its received Intent. */
public class RelaunchIntentExtraActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. Get the Intent that started this Activity
        final Intent receivedIntent = getIntent();

        // 2. Create the Intent for the new Activity
        final Intent nextIntent = new Intent().setComponent(RELAUNCH_TARGET_ACTIVITY);

        // 3. Extract the extras from the received Intent and put them into the new one
        final Bundle extras = receivedIntent.getExtras();
        if (extras != null) {
            nextIntent.putExtras(extras);
        }

        // 4. Launch the new Activity
        startActivity(nextIntent);

        finish();
    }
}
