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
 * limitations under the License
 */

package android.server.wm.settings

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Helper class to save, set, and restore transition_animation_scale preferences. */
class TransitionAnimationScaleSession(scale: Float) :
    SettingsSession<Float>(
        Settings.Global.getUriFor(Settings.Global.TRANSITION_ANIMATION_SCALE),
        Settings.Global::getFloat,
        Settings.Global::putFloat
    ) {

    init {
        set(scale)
    }

    override fun close() {
        // Wait for the restored setting to apply before we continue on with the next test
        val waitLock = CountDownLatch(1)
        val context: Context = getInstrumentation().targetContext
        context.contentResolver
            .registerContentObserver(
                mUri,
                false,
                object : ContentObserver(Handler(Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean) {
                        waitLock.countDown()
                    }
                }
            )
        super.close()
        try {
            if (!waitLock.await(2, TimeUnit.SECONDS)) {
                Log.e(TAG, "TransitionAnimationScaleSession value not restored")
            }
        } catch (impossible: InterruptedException) {
            // no-op when close
        }
    }

    companion object {
        private const val TAG = "TransitionAnimationScaleSession"
    }
}
