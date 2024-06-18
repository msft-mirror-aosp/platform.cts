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
import android.hardware.input.InputSettings
import android.platform.test.annotations.RequiresFlagsEnabled
import android.view.KeyEvent
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.compatibility.common.util.PollingCheck
import com.android.compatibility.common.util.SystemUtil
import com.android.cts.input.UinputKeyboard
import com.android.hardware.input.Flags.FLAG_KEYBOARD_A11Y_BOUNCE_KEYS_FLAG
import com.android.input.flags.Flags.FLAG_ENABLE_INPUT_FILTER_RUST_IMPL
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Create virtual keyboard devices and test A11Y bounce keys feature.
 */
@MediumTest
@RunWith(AndroidJUnit4::class)
class A11yBounceKeysTest {

    companion object {
        const val KEY_A = 30
        const val THRESHOLD_MILLIS = 500
        const val A11Y_SETTINGS_PROPAGATE_TIME_MILLIS: Long = 100
    }

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @get:Rule
    val activityRule = ActivityScenarioRule<CaptureEventActivity>(CaptureEventActivity::class.java)

    private var savedThreshold = 0
    private lateinit var activity: CaptureEventActivity
    private lateinit var inputManager: InputManager

    @Before
    fun setUp() {
        activityRule.getScenario().onActivity {
            inputManager = it.getSystemService(InputManager::class.java)
            activity = it
        }
        PollingCheck.waitFor { activity.hasWindowFocus() }

        SystemUtil.runWithShellPermissionIdentity(
            {
                savedThreshold = InputSettings.getAccessibilityBounceKeysThreshold(activity)
                InputSettings.setAccessibilityBounceKeysThreshold(activity, THRESHOLD_MILLIS)
            },
            "android.permission.INTERACT_ACROSS_USERS_FULL"
        )
        Thread.sleep(A11Y_SETTINGS_PROPAGATE_TIME_MILLIS)
    }

    @After
    fun tearDown() {
        SystemUtil.runWithShellPermissionIdentity(
            {
                InputSettings.setAccessibilityBounceKeysThreshold(activity, savedThreshold)
            },
            "android.permission.INTERACT_ACROSS_USERS_FULL"
        )
    }

    private fun assertReceivedEventsCorrectlyMapped(numEvents: Int, expectedKeyCode: Int) {
        for (i in 1..numEvents) {
            val lastInputEvent = activity.getInputEvent() as? KeyEvent
            Assert.assertNotNull("Failed to receive key event number $i", lastInputEvent)
            Assert.assertEquals(
                "Key code should be " + KeyEvent.keyCodeToString(expectedKeyCode),
                expectedKeyCode,
                lastInputEvent!!.keyCode
            )
        }
        activity.assertNoEvents()
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ENABLE_INPUT_FILTER_RUST_IMPL, FLAG_KEYBOARD_A11Y_BOUNCE_KEYS_FLAG)
    fun testKeyIgnoredIfPressedWithinBounceThreshold() {
        UinputKeyboard(instrumentation).use { keyboardDevice ->
            activity.assertNoEvents()

            // First key press
            injectKeyDown(keyboardDevice, KEY_A)
            injectKeyUp(keyboardDevice, KEY_A)

            // Second key press should be ignored
            injectKeyDown(keyboardDevice, KEY_A)
            injectKeyUp(keyboardDevice, KEY_A)

            assertReceivedEventsCorrectlyMapped(2, KeyEvent.KEYCODE_A)
        }
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ENABLE_INPUT_FILTER_RUST_IMPL, FLAG_KEYBOARD_A11Y_BOUNCE_KEYS_FLAG)
    fun testKeyAcceptedIfPressedAfterBounceThreshold() {
        UinputKeyboard(instrumentation).use { keyboardDevice ->
            activity.assertNoEvents()

            // First key press
            injectKeyDown(keyboardDevice, KEY_A)
            injectKeyUp(keyboardDevice, KEY_A)

            Thread.sleep(2 * THRESHOLD_MILLIS.toLong())

            // Second key press should not be ignored after threshold time
            injectKeyDown(keyboardDevice, KEY_A)
            injectKeyUp(keyboardDevice, KEY_A)

            assertReceivedEventsCorrectlyMapped(4, KeyEvent.KEYCODE_A)
        }
    }
}
