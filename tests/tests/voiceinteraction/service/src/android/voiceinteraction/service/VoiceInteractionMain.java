/*
 * Copyright (C) 2015 The Android Open Source Project
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

package android.voiceinteraction.service;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
import android.voiceinteraction.common.Utils;
import android.widget.TextView;

public class VoiceInteractionMain extends Activity {
    static final String TAG = "VoiceInteractionMain";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView tv = new TextView(this);
        tv.setText("VoiceInteractionMain");
        setContentView(tv);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Ensure that when the activity is reused (e.g., across multiple test iterations),
        // it always uses the latest intent extras provided by the test suite.
        setIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // We use an OnGlobalLayoutListener to ensure the view hierarchy is fully established,
        // attached, and laid out before starting the interaction service.
        // This prevents a race condition where the system's AssistStructure capture might
        // find an empty window if it's triggered before the first layout pass completes.
        final View decor = getWindow().getDecorView();
        decor.getViewTreeObserver()
                .addOnGlobalLayoutListener(
                        new ViewTreeObserver.OnGlobalLayoutListener() {
                            @Override
                            public void onGlobalLayout() {
                                decor.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                                startInteractionService();
                            }
                        });
    }

    private void startInteractionService() {
        Intent intent = new Intent();
        String targetService = getIntent().getStringExtra("target_service");
        if (targetService != null) {
            intent.setClassName(this, targetService);
        } else {
            intent.setComponent(new ComponentName(this, MainInteractionService.class));
        }
        intent.putExtra(Utils.KEY_TEST_EVENT, Utils.VIS_NORMAL_TEST);
        final Bundle intentExtras = getIntent().getExtras();
        if (intentExtras != null) {
            intent.putExtras(intentExtras);
        }
        ComponentName serviceName = startService(intent);
        Log.i(TAG, "Started service: " + serviceName);
    }
}
