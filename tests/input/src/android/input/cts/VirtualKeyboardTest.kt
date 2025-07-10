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

import android.hardware.input.InputManager
import android.hardware.input.VirtualKeyEvent
import android.hardware.input.VirtualKeyboard
import android.hardware.input.VirtualKeyboardConfig
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.view.Display.INVALID_DISPLAY
import android.view.KeyEvent
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.android.compatibility.common.util.PollingCheck
import com.android.compatibility.common.util.SystemUtil
import com.android.cts.input.inputeventmatchers.withKeyAction
import com.android.cts.input.inputeventmatchers.withKeyCode
import com.android.cts.input.inputeventmatchers.withModifierState
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.allOf
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import platform.test.desktop.DisplayPeripheral
import platform.test.desktop.DisplaySize
import platform.test.desktop.PeripheralDeviceTestRule
import platform.test.desktop.PeripheralType
import platform.test.desktop.SimulatedDisplayDevice

/**
 * Create virtual keyboard device and inject key events.
 */
@MediumTest
@RunWith(AndroidJUnit4::class)
@RequiresFlagsEnabled(com.android.hardware.input.Flags.FLAG_CREATE_VIRTUAL_KEYBOARD_API)
class VirtualKeyboardTest {
    @get:Rule
    val activityRule =
        ActivityScenarioRule<KeyboardCaptureActivity>(KeyboardCaptureActivity::class.java)

    @get:Rule
    val peripheralDeviceRule = PeripheralDeviceTestRule()

    @get:Rule
    val checkFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    private lateinit var activity: KeyboardCaptureActivity
    private lateinit var inputManager: InputManager
    private lateinit var virtualKeyboard: VirtualKeyboard

    @Before
    fun setUp() {
        activityRule.getScenario().onActivity {
            inputManager = it.getSystemService(InputManager::class.java)
            activity = it
        }
        PollingCheck.waitFor { activity.hasWindowFocus() }
    }

    @After
    fun tearDown() {
        if (this::virtualKeyboard.isInitialized) {
            virtualKeyboard.close()
        }
    }

    private fun createVirtualKeyboard(displayId: Int) {
        SystemUtil.runWithShellPermissionIdentity(
            {
                val config = VirtualKeyboardConfig.Builder()
                    .setInputDeviceName("testDevice")
                    .setLanguageTag("en-Latn-US")
                    .setLayoutType("qwerty")
                    .setAssociatedDisplayId(displayId)
                virtualKeyboard = inputManager.createVirtualKeyboard(config.build())
            },
            "android.permission.INJECT_KEY_EVENTS",
        )
    }

    private fun assertReceivedEvent(keyCode: Int, action: Int, metaState: Int) {
        activity.verifier.assertReceivedKey(allOf(
            withKeyCode(keyCode),
            withKeyAction(action),
            withModifierState(metaState),
        ))
    }

    @Test
    fun testInjectSimpleEvents() {
        createVirtualKeyboard(INVALID_DISPLAY)

        // Down event with no modifiers.
        virtualKeyboard.sendKeyEvent(
            VirtualKeyEvent.Builder()
                .setKeyCode(KeyEvent.KEYCODE_A)
                .setAction(VirtualKeyEvent.ACTION_DOWN)
                .build()
        )
        // Up event with no modifiers.
        virtualKeyboard.sendKeyEvent(
            VirtualKeyEvent.Builder()
                .setKeyCode(KeyEvent.KEYCODE_A)
                .setAction(VirtualKeyEvent.ACTION_UP)
                .build()
        )

        assertReceivedEvent(KeyEvent.KEYCODE_A, KeyEvent.ACTION_DOWN, 0)
        assertReceivedEvent(KeyEvent.KEYCODE_A, KeyEvent.ACTION_UP, 0)
    }

    @Test
    fun testInjectModifierEvents() {
        createVirtualKeyboard(INVALID_DISPLAY)

        virtualKeyboard.sendKeyEvent(
            VirtualKeyEvent.Builder()
                .setKeyCode(KeyEvent.KEYCODE_CTRL_LEFT)
                .setAction(VirtualKeyEvent.ACTION_DOWN)
                .build()
        )
        virtualKeyboard.sendKeyEvent(
            VirtualKeyEvent.Builder()
                .setKeyCode(KeyEvent.KEYCODE_SHIFT_LEFT)
                .setAction(VirtualKeyEvent.ACTION_DOWN)
                .build()
        )
        virtualKeyboard.sendKeyEvent(
            VirtualKeyEvent.Builder()
                .setKeyCode(KeyEvent.KEYCODE_ALT_LEFT)
                .setAction(VirtualKeyEvent.ACTION_DOWN)
                .build()
        )

        virtualKeyboard.sendKeyEvent(
            VirtualKeyEvent.Builder()
                .setKeyCode(KeyEvent.KEYCODE_CTRL_LEFT)
                .setAction(VirtualKeyEvent.ACTION_UP)
                .build()
        )
        virtualKeyboard.sendKeyEvent(
            VirtualKeyEvent.Builder()
                .setKeyCode(KeyEvent.KEYCODE_SHIFT_LEFT)
                .setAction(VirtualKeyEvent.ACTION_UP)
                .build()
        )
        virtualKeyboard.sendKeyEvent(
            VirtualKeyEvent.Builder()
                .setKeyCode(KeyEvent.KEYCODE_ALT_LEFT)
                .setAction(VirtualKeyEvent.ACTION_UP)
                .build()
        )

        // Down events.
        assertReceivedEvent(
            KeyEvent.KEYCODE_CTRL_LEFT,
            KeyEvent.ACTION_DOWN,
            KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        )
        assertReceivedEvent(
            KeyEvent.KEYCODE_SHIFT_LEFT,
            KeyEvent.ACTION_DOWN,
            KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
                    or KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
        )
        assertReceivedEvent(
            KeyEvent.KEYCODE_ALT_LEFT,
            KeyEvent.ACTION_DOWN,
            KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
                    or KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
                    or KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
        )

        // Up events.
        assertReceivedEvent(
            KeyEvent.KEYCODE_CTRL_LEFT,
            KeyEvent.ACTION_UP,
            KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
                    or KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
        )
        assertReceivedEvent(
            KeyEvent.KEYCODE_SHIFT_LEFT,
            KeyEvent.ACTION_UP,
            KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
        )
        assertReceivedEvent(KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.ACTION_UP, 0)
    }

    @Test
    fun testInjectMetaEvents() {
        createVirtualKeyboard(INVALID_DISPLAY)

        virtualKeyboard.sendKeyEvent(
            VirtualKeyEvent.Builder()
                .setKeyCode(KeyEvent.KEYCODE_META_LEFT)
                .setAction(VirtualKeyEvent.ACTION_DOWN)
                .build()
        )
        virtualKeyboard.sendKeyEvent(
            VirtualKeyEvent.Builder()
                .setKeyCode(KeyEvent.KEYCODE_META_LEFT)
                .setAction(VirtualKeyEvent.ACTION_UP)
                .build()
        )

        assertReceivedEvent(
            KeyEvent.KEYCODE_META_LEFT,
            KeyEvent.ACTION_DOWN,
            KeyEvent.META_META_ON or KeyEvent.META_META_LEFT_ON
        )
        assertReceivedEvent(KeyEvent.KEYCODE_META_LEFT, KeyEvent.ACTION_UP, 0)
    }

    @Test
    fun testCreateVirtualKeyboardOnUnownedDisplay() {
        // Create a virtual display that is not owned by this instrumentation.
        val response =
            peripheralDeviceRule.requestPeripherals(
                DisplayPeripheral(PeripheralType.SIMULATED, DisplaySize.SIZE_1080P)
            )

        assertThat(
            "No virtual display created",
            response.devices.filter { it.connected }.isNotEmpty()
        )
        assertThrows(RuntimeException::class.java) {
            val display: SimulatedDisplayDevice = response.devices[0] as SimulatedDisplayDevice
            createVirtualKeyboard(display.displayId)
        }
    }
}
