/*
 * Copyright (C) 2022 The Android Open Source Project
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

import android.annotation.SuppressLint
import android.hardware.input.InputManager
import android.hardware.input.VirtualDpad
import android.hardware.input.VirtualKeyEvent
import android.hardware.input.cts.virtualcreators.VirtualInputDeviceCreator
import android.hardware.input.cts.virtualcreators.VirtualInputEventCreator
import android.platform.test.annotations.RequiresFlagsEnabled
import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@SuppressLint("MissingCheckFlagsRule") // TODO: b/463342925 - remove once fixed
@SmallTest
@RunWith(AndroidJUnit4::class)
class VirtualDpadTest : VirtualDeviceTestCase() {
    private lateinit var mVirtualDpad: VirtualDpad

    override fun onSetUpVirtualInputDevice() {
        mVirtualDpad = VirtualInputDeviceCreator.createAndPrepareDpad(
            mVirtualDevice, DEVICE_NAME,
            mVirtualDisplay.display
        ).device
    }

    @Test
    fun sendKeyEvent() {
        mVirtualDpad.sendKeyEvent(
            VirtualKeyEvent.Builder()
                .setKeyCode(KeyEvent.KEYCODE_DPAD_UP)
                .setAction(VirtualKeyEvent.ACTION_DOWN)
                .build()
        )
        mVirtualDpad.sendKeyEvent(
            VirtualKeyEvent.Builder()
                .setKeyCode(KeyEvent.KEYCODE_DPAD_UP)
                .setAction(VirtualKeyEvent.ACTION_UP)
                .build()
        )
        mVirtualDpad.sendKeyEvent(
            VirtualKeyEvent.Builder()
                .setKeyCode(KeyEvent.KEYCODE_DPAD_CENTER)
                .setAction(VirtualKeyEvent.ACTION_DOWN)
                .build()
        )
        mVirtualDpad.sendKeyEvent(
            VirtualKeyEvent.Builder()
                .setKeyCode(KeyEvent.KEYCODE_DPAD_CENTER)
                .setAction(VirtualKeyEvent.ACTION_UP)
                .build()
        )
        verifyEvents(
            listOf<InputEvent>(
                VirtualInputEventCreator.createDpadEvent(
                    KeyEvent.ACTION_DOWN,
                    KeyEvent.KEYCODE_DPAD_UP
                ),
                VirtualInputEventCreator.createDpadEvent(
                    KeyEvent.ACTION_UP,
                    KeyEvent.KEYCODE_DPAD_UP
                ),
                VirtualInputEventCreator.createDpadEvent(
                    KeyEvent.ACTION_DOWN,
                    KeyEvent.KEYCODE_DPAD_CENTER
                ),
                VirtualInputEventCreator.createDpadEvent(
                    KeyEvent.ACTION_UP,
                    KeyEvent.KEYCODE_DPAD_CENTER
                )
            )
        )
    }

    @Test
    fun rejectsUnsupportedKeyCodes() {
        assertThrows(IllegalArgumentException::class.java) {
            mVirtualDpad.sendKeyEvent(
                VirtualKeyEvent.Builder()
                    .setKeyCode(KeyEvent.KEYCODE_Q)
                    .setAction(VirtualKeyEvent.ACTION_DOWN)
                    .build()
            )
        }
    }

    @Test
    @RequiresFlagsEnabled(com.android.hardware.input.Flags.FLAG_CREATE_VIRTUAL_KEYBOARD_API)
    fun hasAssociatedDisplayId() {
        val inputManager: InputManager =
            mInstrumentation.context.getSystemService(InputManager::class.java)
        val dpadId: Int = mVirtualDpad.inputDeviceId
        assertThat(inputManager.inputDeviceIds.asList()).contains(dpadId)

        val inputDevice: InputDevice = inputManager.getInputDevice(dpadId)!!
        assertThat(inputDevice.name).isEqualTo(DEVICE_NAME)
        assertThat(inputDevice.associatedDisplayId).isEqualTo(mVirtualDisplay.display.displayId)
    }

    companion object {
        private const val DEVICE_NAME = "CtsVirtualDpadTestDevice"
    }
}
