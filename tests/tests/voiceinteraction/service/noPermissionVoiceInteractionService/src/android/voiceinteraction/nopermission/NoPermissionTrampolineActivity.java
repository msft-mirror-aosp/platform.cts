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

package android.voiceinteraction.nopermission;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.TextView;

public class NoPermissionTrampolineActivity extends Activity {
    private static final String TAG = "NoPermissionTrampoline";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView tv = new TextView(this);
        tv.setText("NoPermissionTrampolineActivity");
        setContentView(tv);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Ensure that when the activity is reused, it always uses the latest intent extras
        // provided by the test suite for consistent test results across iterations.
        setIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Defer starting the interaction service until the first global layout pass.
        // This ensures the window is fully added and views are laid out before a session
        // starts, preventing race conditions where AssistStructure captures an empty window.
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
        intent.setComponent(new ComponentName(this, NoPermissionInteractionService.class));

        final Bundle intentExtras = getIntent().getExtras();
        if (intentExtras != null) {
            intent.putExtras(intentExtras);
        }

        ComponentName serviceName = startService(intent);
        Log.i(TAG, "Started service: " + serviceName);
    }
}
