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

package android.input.cts

import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.view.KeyEvent
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.compatibility.common.util.PollingCheck
import com.android.cts.input.BlockingQueueEventVerifier
import com.android.cts.input.EvdevInputEventCodes.Companion.KEY_LEFTMETA
import com.android.cts.input.UinputKeyboard
import com.android.cts.input.inputeventmatchers.withKeyAction
import com.android.cts.input.inputeventmatchers.withKeyCode
import com.android.cts.input.inputeventmatchers.withModifierState
import com.android.hardware.input.Flags.FLAG_REQUEST_KEY_CAPTURE_API
import org.hamcrest.Matchers
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Create virtual keyboard device and inject a system key gesture, and test keyboard capture.
 */
@MediumTest
@RunWith(AndroidJUnit4::class)
@RequiresFlagsEnabled(FLAG_REQUEST_KEY_CAPTURE_API)
class KeyboardCaptureTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @get:Rule
    val activityRule = ActivityScenarioRule(KeyboardCaptureActivity::class.java)

    @get:Rule
    val checkFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    private lateinit var activity: KeyboardCaptureActivity
    private lateinit var verifier: BlockingQueueEventVerifier

    @Before
    fun setUp() {
        activityRule.getScenario().onActivity {
            activity = it
            verifier = activity.verifier
        }
        PollingCheck.waitFor { activity.hasWindowFocus() }
    }

    @Test
    fun testMetaKey_sentToFocusedWindow_withKeyboardCapture() {
        UinputKeyboard(instrumentation, listOf("KEY_LEFTMETA")).use { keyboardDevice ->
            keyboardDevice.injectKeyDown(KEY_LEFTMETA)
            keyboardDevice.injectKeyUp(KEY_LEFTMETA)

            verifier.assertReceivedKey(
                Matchers.allOf(
                    withKeyCode(KeyEvent.KEYCODE_META_LEFT),
                    withKeyAction(KeyEvent.ACTION_DOWN),
                    withModifierState(KeyEvent.META_META_LEFT_ON or KeyEvent.META_META_ON)
                )
            )
            verifier.assertReceivedKey(
                Matchers.allOf(
                    withKeyCode(KeyEvent.KEYCODE_META_LEFT),
                    withKeyAction(KeyEvent.ACTION_UP),
                    withModifierState(0)
                )
            )
        }
    }
}
