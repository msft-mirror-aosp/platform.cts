/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.server.wm.overlay;

import static android.server.wm.overlay.Components.OverlayActivity.EXTRA_TOUCHABLE;
import static android.server.wm.overlay.Components.OverlayActivity.EXTRA_OPACITY;
import static android.server.wm.overlay.UntrustedTouchTestService.BACKGROUND_COLOR;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager.LayoutParams;

import androidx.annotation.Nullable;

/** This is an activity for which android:windowIsTranslucent is true. */
public class OverlayActivity extends Activity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        View view = new View(this);
        view.setBackgroundColor(BACKGROUND_COLOR);
        setContentView(view);
        Window window = getWindow();
        Intent intent = getIntent();
        window.getAttributes().alpha = intent.getFloatExtra(EXTRA_OPACITY, 1f);
        if (!intent.getBooleanExtra(EXTRA_TOUCHABLE, false)) {
            window.addFlags(LayoutParams.FLAG_NOT_TOUCHABLE);
        }
    }
}
