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
 * limitations under the License.
 */

package com.android.cts.input

import android.app.Instrumentation
import android.view.InputDevice.SOURCE_MOUSE
import com.android.cts.input.EvdevInputEventCodes.Companion.EV_KEY
import com.android.cts.input.EvdevInputEventCodes.Companion.EV_REL
import com.android.cts.input.EvdevInputEventCodes.Companion.EV_SYN
import com.android.cts.input.EvdevInputEventCodes.Companion.REL_HWHEEL
import com.android.cts.input.EvdevInputEventCodes.Companion.REL_HWHEEL_HI_RES
import com.android.cts.input.EvdevInputEventCodes.Companion.REL_WHEEL
import com.android.cts.input.EvdevInputEventCodes.Companion.REL_WHEEL_HI_RES
import com.android.cts.input.EvdevInputEventCodes.Companion.REL_X
import com.android.cts.input.EvdevInputEventCodes.Companion.REL_Y
import com.android.cts.input.EvdevInputEventCodes.Companion.SYN_REPORT

private fun createMouseRegisterCommand(): UinputRegisterCommand {
    val configurationItems = listOf(
        ConfigurationItem("UI_SET_EVBIT", listOf("EV_KEY", "EV_REL")),
        ConfigurationItem(
            "UI_SET_KEYBIT",
            listOf("BTN_LEFT", "BTN_MIDDLE", "BTN_RIGHT")
        ),
        ConfigurationItem(
            "UI_SET_RELBIT",
            listOf(
                "REL_X",
                "REL_Y",
                "REL_WHEEL",
                "REL_WHEEL_HI_RES",
                "REL_HWHEEL",
                "REL_HWHEEL_HI_RES",
            )
        ),
    )

    return UinputRegisterCommand(
        id = 1,
        name = "Test Mouse (USB)",
        vid = 0x18d1,
        pid = 0xabcd,
        bus = "usb",
        port = "usb:1",
        configuration = configurationItems,
        absInfo = emptyMap(),
    )
}

/**
 * A three-button mouse.
 */
class UinputMouse(instrumentation: Instrumentation) : UinputDevice(
    instrumentation,
    SOURCE_MOUSE,
    createMouseRegisterCommand(),
    null // display
) {
    companion object {
        const val HI_RES_WHEEL_UNITS_PER_TICK = 120
    }

    private val pressedButtons = mutableSetOf<Int>()

    fun pressButton(button: Int) {
        if (!pressedButtons.add(button)) {
            throw IllegalStateException("Button $button is already pressed")
        }
        injectEvents(EV_KEY, button, 1)
    }

    fun releaseButton(button: Int) {
        if (!pressedButtons.remove(button)) {
            throw IllegalStateException("Button $button is not pressed")
        }
        injectEvents(EV_KEY, button, 0)
    }

    fun move(dx: Int, dy: Int) {
        injectEvents(EV_REL, REL_X, dx)
        injectEvents(EV_REL, REL_Y, dy)
    }

    fun scrollVertically(amount: Float) {
        injectEvents(EV_REL, REL_WHEEL, amount.toInt())
        injectEvents(EV_REL, REL_WHEEL_HI_RES, (amount * HI_RES_WHEEL_UNITS_PER_TICK).toInt())
    }

    fun scrollHorizontally(amount: Float) {
        injectEvents(EV_REL, REL_HWHEEL, amount.toInt())
        injectEvents(EV_REL, REL_HWHEEL_HI_RES, (amount * HI_RES_WHEEL_UNITS_PER_TICK).toInt())
    }

    fun sync() {
        injectEvents(EV_SYN, SYN_REPORT, 0)
    }
}
