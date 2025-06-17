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
package android.hardware.input.cts.tests.virtualdevices

import android.hardware.input.InputManager
import android.hardware.input.VirtualKeyEvent
import android.hardware.input.VirtualKeyboard
import android.hardware.input.cts.virtualcreators.VirtualInputDeviceCreator
import android.hardware.input.cts.virtualcreators.VirtualInputEventCreator
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.view.InputEvent
import android.view.KeyEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
@RequiresFlagsEnabled(com.android.hardware.input.Flags.FLAG_DISABLE_SETTINGS_FOR_VIRTUAL_DEVICES)
class VirtualKeyboardKeyRemappingTest : VirtualDeviceSettingTestCase() {

    @get:Rule val checkFlagRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    private val inputManager =
        getInstrumentation().targetContext.getSystemService(InputManager::class.java)
    private var existingRemapping: Map<Int, Int> = mutableMapOf()
    private lateinit var virtualKeyboard: VirtualKeyboard

    override fun onSetupSetting() {
        mRule.runWithTemporaryPermission({
            existingRemapping = inputManager.modifierKeyRemapping
            inputManager.clearAllModifierKeyRemappings()

            inputManager.remapModifierKey(KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_SHIFT_LEFT)
        }, android.Manifest.permission.REMAP_MODIFIER_KEYS)
    }

    override fun onSetUpVirtualInputDevice() {
        virtualKeyboard = VirtualInputDeviceCreator.createAndPrepareKeyboard(
            mVirtualDevice,
            DEVICE_NAME, mVirtualDisplay.display
        ).device
    }

    override fun onTearDownSetting() {
        mRule.runWithTemporaryPermission({
            inputManager.clearAllModifierKeyRemappings()
            existingRemapping.forEach { entry ->
                inputManager.remapModifierKey(entry.key, entry.value)
            }
        }, android.Manifest.permission.REMAP_MODIFIER_KEYS)
    }

    @Test
    fun virtualKeyboard_shouldNotApplyKeyRemapping() {
        virtualKeyboard.sendKeyEvent(
            VirtualKeyEvent.Builder()
                .setKeyCode(KeyEvent.KEYCODE_ALT_LEFT)
                .setAction(VirtualKeyEvent.ACTION_DOWN)
                .build()
        )
        virtualKeyboard.sendKeyEvent(
            VirtualKeyEvent.Builder()
                .setKeyCode(KeyEvent.KEYCODE_ALT_LEFT)
                .setAction(VirtualKeyEvent.ACTION_UP)
                .build()
        )
        verifyEvents(
            listOf<InputEvent>(
                VirtualInputEventCreator.createKeyboardEvent(
                    KeyEvent.ACTION_DOWN,
                    KeyEvent.KEYCODE_ALT_LEFT,
                    KeyEvent.META_ALT_LEFT_ON or KeyEvent.META_ALT_ON
                ),
                VirtualInputEventCreator.createKeyboardEvent(
                    KeyEvent.ACTION_UP,
                    KeyEvent.KEYCODE_ALT_LEFT
                )
            )
        )
    }

    companion object {
        private const val DEVICE_NAME = "CtsVirtualKeyboardTestDevice"
    }
}
