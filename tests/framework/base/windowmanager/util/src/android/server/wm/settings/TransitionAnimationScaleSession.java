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

package android.server.wm.settings;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import android.content.Context;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Helper class to save, set, and restore transition_animation_scale preferences. */
public class TransitionAnimationScaleSession extends SettingsSession<Float> {
    private static final String TAG = TransitionAnimationScaleSession.class.getSimpleName();

    public TransitionAnimationScaleSession(float scale) {
        super(Settings.Global.getUriFor(Settings.Global.TRANSITION_ANIMATION_SCALE),
                Settings.Global::getFloat,
                Settings.Global::putFloat);
        set(scale);
    }

    @Override
    public void close() {
        // Wait for the restored setting to apply before we continue on with the next test
        final CountDownLatch waitLock = new CountDownLatch(1);
        final Context context = getInstrumentation().getTargetContext();
        context.getContentResolver().registerContentObserver(mUri, false,
                new ContentObserver(new Handler(Looper.getMainLooper())) {
                    @Override
                    public void onChange(boolean selfChange) {
                        waitLock.countDown();
                    }
                });
        super.close();
        try {
            if (!waitLock.await(2, TimeUnit.SECONDS)) {
                Log.i(TAG, "TransitionAnimationScaleSession value not restored");
            }
        } catch (InterruptedException impossible) {
        }
    }
}
