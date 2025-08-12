/*
 * Copyright 2025 The Android Open Source Project
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

package android.input.cts.hostside

import android.compat.cts.CompatChangeGatingTestCase
import android.platform.test.flag.junit.host.DeviceFlags
import com.android.hardware.input.Flags
import com.android.tradefed.log.LogUtil.CLog
import com.google.common.collect.ImmutableSet

class MouseToTouchTest : CompatChangeGatingTestCase() {

    fun testEnabled_clickToTouch() {
        if (!aconfigFlagEnabled()) {
            // CompatChangeGatingTestCase is JUnit3, which is incompatible with flag rule, and does
            // not have assumption failure neither (b/324474892).
            // As a workaround, manually invoke the check and return early.
            CLog.w("aconfig flag is not enabled, skipping test")
            return
        }

        runDeviceCompatTest(
            TEST_APP_PACKAGE,
            TEST_CLASS,
            "testEnabled_clickToTouch",
            ImmutableSet.of(MOUSE_TO_TOUCH_COMPAT_CHANGE_ID),
            ImmutableSet.of()
        )
    }

    fun testEnabled_rightClickAsIs() {
        if (!aconfigFlagEnabled()) {
            // Workaround for CompatChangeGatingTestCase being JUnit3 (b/324474892).
            CLog.w("aconfig flag is not enabled, skipping test")
            return
        }

        runDeviceCompatTest(
            TEST_APP_PACKAGE,
            TEST_CLASS,
            "testEnabled_rightClickAsIs",
            ImmutableSet.of(MOUSE_TO_TOUCH_COMPAT_CHANGE_ID),
            ImmutableSet.of()
        )
    }

    fun testDisabled_click() {
        if (!aconfigFlagEnabled()) {
            // Workaround for CompatChangeGatingTestCase being JUnit3 (b/324474892).
            CLog.w("aconfig flag is not enabled, skipping test")
            return
        }

        runDeviceCompatTest(
            TEST_APP_PACKAGE,
            TEST_CLASS,
            "testDisabled_click",
            ImmutableSet.of(),
            ImmutableSet.of(MOUSE_TO_TOUCH_COMPAT_CHANGE_ID)
        )
    }

    fun aconfigFlagEnabled(): Boolean {
        val flags = DeviceFlags.createDeviceFlags(device)
        return flags.getFlagValue(Flags.FLAG_MOUSE_TO_TOUCH_PER_APP_COMPAT)?.toBoolean() ?: false
    }

    companion object {
        const val TEST_APP_PACKAGE = "android.input.cts.hostside.app"
        const val TEST_CLASS = ".MouseToTouchCompatChangeTest"

        const val MOUSE_TO_TOUCH_COMPAT_CHANGE_ID = 413207127L
    }
}
