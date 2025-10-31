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

package com.android.cts.input

import android.app.Instrumentation
import android.view.InputDevice.SOURCE_GAMEPAD
import android.view.InputDevice.SOURCE_JOYSTICK
import android.view.InputDevice.SOURCE_KEYBOARD
import com.android.cts.input.EvdevInputEventCodes.Companion.EV_KEY
import com.android.cts.input.EvdevInputEventCodes.Companion.EV_KEY_PRESS
import com.android.cts.input.EvdevInputEventCodes.Companion.EV_KEY_RELEASE
import com.android.cts.input.EvdevInputEventCodes.Companion.EV_SYN
import com.android.cts.input.EvdevInputEventCodes.Companion.SYN_REPORT

private fun createGamepadRegisterCommand(): UinputRegisterCommand {
    val configurationItems = listOf(
        ConfigurationItem("UI_SET_EVBIT", listOf("EV_KEY", "EV_ABS")),
        ConfigurationItem(
            "UI_SET_KEYBIT",
            listOf(
                "BTN_SOUTH",
                "BTN_EAST",
                "BTN_NORTH",
                "BTN_WEST",
                "BTN_TL",
                "BTN_TR",
                "BTN_TL2",
                "BTN_TR2",
                "BTN_SELECT",
                "BTN_START",
                "BTN_MODE",
                "BTN_THUMBL",
                "BTN_THUMBR",
            )
        ),
        ConfigurationItem(
            "UI_SET_ABSBIT",
            listOf("ABS_X", "ABS_Y", "ABS_Z", "ABS_RZ", "ABS_THROTTLE")
        )
    )

    val absInfo = mapOf(
        "ABS_X" to AbsInfo(0, -127, 127, 0, 0, 0),
        "ABS_Y" to AbsInfo(0, -127, 127, 0, 0, 0),
        "ABS_Z" to AbsInfo(0, -127, 127, 0, 0, 0),
        "ABS_RZ" to AbsInfo(0, -127, 127, 0, 0, 0),
        "ABS_THROTTLE" to AbsInfo(0, 0, 255, 0, 0, 0),
    )

    return UinputRegisterCommand(
        id = 1,
        name = "Test Gamepad (USB)",
        vid = 0x18d1,
        pid = 0xabcd,
        bus = "usb",
        port = "usb:1",
        configuration = configurationItems,
        absInfo = absInfo,
    )
}

/**
 * A gamepad that currently only sends a single button.
 */
class UinputGamepad(instrumentation: Instrumentation) : UinputDevice(
    instrumentation,
    SOURCE_KEYBOARD or SOURCE_GAMEPAD or SOURCE_JOYSTICK,
    createGamepadRegisterCommand(),
    null // display
) {
    // store the keys that are currently down
    private val keysDown = mutableSetOf<Int>()

    fun injectKeyDown(scanCode: Int) {
        if (!keysDown.add(scanCode)) {
            throw IllegalArgumentException("Key $scanCode is already down")
        }
        injectEvents(EV_KEY, scanCode, EV_KEY_PRESS)
        injectEvents(EV_SYN, SYN_REPORT, 0)
    }

    fun injectKeyUp(scanCode: Int) {
        if (!keysDown.remove(scanCode)) {
            throw IllegalArgumentException("Key $scanCode is not down")
        }
        injectEvents(EV_KEY, scanCode, EV_KEY_RELEASE)
        injectEvents(EV_SYN, SYN_REPORT, 0)
    }
}
