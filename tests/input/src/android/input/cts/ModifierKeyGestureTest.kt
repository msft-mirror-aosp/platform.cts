/*
 * Copyright 2024 The Android Open Source Project
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

import android.hardware.input.InputManager
import android.view.KeyEvent
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.compatibility.common.util.PollingCheck
import com.android.cts.input.BlockingQueueEventVerifier
import com.android.cts.input.CaptureEventActivity
import com.android.cts.input.EvdevInputEventCodes.Companion.KEY_LEFTALT
import com.android.cts.input.EvdevInputEventCodes.Companion.KEY_LEFTMETA
import com.android.cts.input.EvdevInputEventCodes.Companion.KEY_Q
import com.android.cts.input.UinputKeyboard
import com.android.cts.input.inputeventmatchers.withKeyAction
import com.android.cts.input.inputeventmatchers.withKeyCode
import com.android.cts.input.inputeventmatchers.withModifierState
import org.hamcrest.Matchers
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Create virtual keyboard devices and inject a 'hardware' key event to test certain key gestures
 * are properly handled.
 */
@MediumTest
@RunWith(AndroidJUnit4::class)
class ModifierKeyGestureTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @get:Rule
    val rule = ActivityScenarioRule<CaptureEventActivity>(CaptureEventActivity::class.java)

    private lateinit var activity: CaptureEventActivity
    private lateinit var verifier: BlockingQueueEventVerifier
    private lateinit var inputManager: InputManager

    @Before
    fun setUp() {
        rule.getScenario().onActivity {
            inputManager = it.getSystemService(InputManager::class.java)
            activity = it
            verifier = activity.verifier
        }
        inputManager.resetLockedModifierState()
        PollingCheck.waitFor { activity.hasWindowFocus() }
    }

    @After
    fun tearDown() {
        if (this::inputManager.isInitialized) {
            inputManager.resetLockedModifierState()
        }
    }

    @Test
    fun testMetaAlt_toggleCapsLock_forKeyboardWithNoCapsLockKey() {
        UinputKeyboard(
            instrumentation,
            listOf("KEY_Q", "KEY_LEFTALT", "KEY_LEFTMETA")
        ).use { keyboardDevice ->
            // Issue: notifyConfigurationChanged() policy call happens on reader
            // thread and blocks it for ~250ms for some low end devices. But the
            // InputDeviceListener on main thread process device change fast and
            // the test starts injecting events while reader thread is blocked.
            // This has weird artifacts because "assertNoEvents" would continue
            // even though reader hasn't actually processed the EventHub events.
            // 
            // Short term fix: inject "key Q" and wait reader thread to fully process
            // the events before injecting the Meta+Alt key combination. This way
            // we ensure reader is free to process Meta and Alt key presses and
            // the CapsLock toggle processing.
            keyboardDevice.injectKeyDown(KEY_Q)
            keyboardDevice.injectKeyUp(KEY_Q)
            verifier.assertReceivedKey(
                Matchers.allOf(
                    withKeyCode(KeyEvent.KEYCODE_Q),
                    withKeyAction(KeyEvent.ACTION_DOWN)
                )
            )
            verifier.assertReceivedKey(
                Matchers.allOf(
                    withKeyCode(KeyEvent.KEYCODE_Q),
                    withKeyAction(KeyEvent.ACTION_UP)
                )
            )

            keyboardDevice.injectKeyDown(KEY_LEFTMETA)
            keyboardDevice.injectKeyDown(KEY_LEFTALT)
            keyboardDevice.injectKeyUp(KEY_LEFTALT)
            keyboardDevice.injectKeyUp(KEY_LEFTMETA)

            activity.assertNoEvents()

            keyboardDevice.injectKeyDown(KEY_Q)
            keyboardDevice.injectKeyUp(KEY_Q)
            verifier.assertReceivedKey(
                Matchers.allOf(
                    withKeyCode(KeyEvent.KEYCODE_Q),
                    withKeyAction(KeyEvent.ACTION_DOWN),
                    withModifierState(KeyEvent.META_CAPS_LOCK_ON)
                )
            )
            verifier.assertReceivedKey(
                Matchers.allOf(
                    withKeyCode(KeyEvent.KEYCODE_Q),
                    withKeyAction(KeyEvent.ACTION_UP),
                    withModifierState(KeyEvent.META_CAPS_LOCK_ON)
                )
            )
        }
    }
}
