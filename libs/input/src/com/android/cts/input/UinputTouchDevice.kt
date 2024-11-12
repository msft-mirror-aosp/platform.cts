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

package com.android.cts.input

import android.app.Instrumentation
import android.graphics.Point
import android.view.Display
import android.view.Surface

// Attempt to transform coordinates from the logical display (screen) space to the physical display
// space. To do this, we assume the unrotated logical display has the same aspect ratio as the
// physical display. This will not work for cases where the aspect ration of the logical display
// is different than that of the physical display.
@Suppress("DEPRECATION")
private fun transformFromScreenToTouchDeviceSpace(x: Int, y: Int, display: Display): Point {
    val unrotatedLogicalPoint = when (display.rotation) {
        Surface.ROTATION_0 -> Point(x, y)
        Surface.ROTATION_90 -> Point(display.height - 1 - y, x)
        Surface.ROTATION_180 -> Point(
            display.width - 1 - x,
            display.height - 1 - y
        )

        Surface.ROTATION_270 -> Point(y, display.width - 1 - x)
        else -> throw IllegalStateException("unexpected display rotation ${display.rotation}")
    }

    val unrotatedLogicalWidth = when (display.rotation) {
        Surface.ROTATION_0, Surface.ROTATION_180 -> display.width
        else -> display.height
    }
    val unrotatedLogicalHeight = when (display.rotation) {
        Surface.ROTATION_0, Surface.ROTATION_180 -> display.height
        else -> display.width
    }

    return Point(
        unrotatedLogicalPoint.x * display.mode.physicalWidth / unrotatedLogicalWidth,
        unrotatedLogicalPoint.y * display.mode.physicalHeight / unrotatedLogicalHeight,
    )
}

/**
 * Helper class for configuring and interacting with a [UinputDevice] that uses the evdev
 * multitouch protocol.
 */
open class UinputTouchDevice(
    instrumentation: Instrumentation,
    private val display: Display,
    private val registerCommand: UinputRegisterCommand,
    source: Int,
) : AutoCloseable {

    val uinputDevice = UinputDevice(instrumentation, source, registerCommand, display)

    private fun injectEvent(events: IntArray) {
        uinputDevice.injectEvents(events.joinToString(
            prefix = "[",
            postfix = "]",
            separator = ",",
        ))
    }

    fun sendBtnTouch(isDown: Boolean) {
        injectEvent(intArrayOf(EV_KEY, BTN_TOUCH, if (isDown) 1 else 0))
    }

    fun sendBtn(btnCode: Int, isDown: Boolean) {
        injectEvent(intArrayOf(EV_KEY, btnCode, if (isDown) 1 else 0))
    }

    fun sendDown(id: Int, location: Point, toolType: Int? = null) {
        injectEvent(intArrayOf(EV_ABS, ABS_MT_SLOT, id))
        injectEvent(intArrayOf(EV_ABS, ABS_MT_TRACKING_ID, id))
        if (toolType != null) injectEvent(intArrayOf(EV_ABS, ABS_MT_TOOL_TYPE, toolType))
        injectEvent(intArrayOf(EV_ABS, ABS_MT_POSITION_X, location.x))
        injectEvent(intArrayOf(EV_ABS, ABS_MT_POSITION_Y, location.y))
    }

    fun sendMove(id: Int, location: Point) {
        // Use same events of down.
        sendDown(id, location)
    }

    fun sendUp(id: Int) {
        injectEvent(intArrayOf(EV_ABS, ABS_MT_SLOT, id))
        injectEvent(intArrayOf(EV_ABS, ABS_MT_TRACKING_ID, INVALID_TRACKING_ID))
    }

    fun sendToolType(id: Int, toolType: Int) {
        injectEvent(intArrayOf(EV_ABS, ABS_MT_SLOT, id))
        injectEvent(intArrayOf(EV_ABS, ABS_MT_TOOL_TYPE, toolType))
    }

    fun sendPressure(pressure: Int) {
        injectEvent(intArrayOf(EV_ABS, ABS_MT_PRESSURE, pressure))
    }

    fun sync() {
        injectEvent(intArrayOf(EV_SYN, SYN_REPORT, 0))
    }

    fun delay(delayMs: Int) {
        uinputDevice.injectDelay(delayMs)
    }

    fun getDeviceId(): Int {
        return uinputDevice.deviceId
    }

    override fun close() {
        uinputDevice.close()
    }

    private val pointerIds = mutableSetOf<Int>()

    /**
     * Send a new pointer to the screen, generating an ACTION_DOWN if there aren't any other
     * pointers currently down, or an ACTION_POINTER_DOWN otherwise.
     */
    fun touchDown(x: Int, y: Int): Pointer {
        val pointerId = firstUnusedPointerId()
        pointerIds.add(pointerId)
        return Pointer(pointerId, x, y)
    }

    private fun firstUnusedPointerId(): Int {
        var id = 0
        while (pointerIds.contains(id)) {
            id++
        }
        return id
    }

    private fun removePointer(id: Int) {
        pointerIds.remove(id)
    }

    private val pointerCount get() = pointerIds.size

    /**
     * A single pointer interacting with the screen. This class simplifies the interactions by
     * removing the need to separately manage the pointer id.
     * Works in the screen coordinate space.
     */
    inner class Pointer(
        private val id: Int,
        x: Int,
        y: Int,
    ) : AutoCloseable {
        private var active = true
        init {
            // Send ACTION_DOWN or ACTION_POINTER_DOWN
            sendBtnTouch(true)
            sendDown(id, transformFromScreenToTouchDeviceSpace(x, y, display), MT_TOOL_FINGER)
            sync()
        }

        /**
         * Send ACTION_MOVE
         * The coordinates provided here should be relative to the screen edge, rather than the
         * window corner. That is, the location should be in the same coordinate space as that
         * returned by View::getLocationOnScreen API rather than View::getLocationInWindow.
         */
        fun moveTo(x: Int, y: Int) {
            if (!active) {
                throw IllegalStateException("Pointer $id is not active, can't move to ($x, $y)")
            }
            sendMove(id, transformFromScreenToTouchDeviceSpace(x, y, display))
            sync()
        }

        fun lift() {
            if (!active) {
                throw IllegalStateException("Pointer $id is not active, already lifted?")
            }
            if (pointerCount == 1) {
                sendBtnTouch(false)
            }
            sendUp(id)
            sync()
            active = false
            removePointer(id)
        }

        /**
         * Send a cancel if this pointer hasn't yet been lifted
         */
        override fun close() {
            if (!active) {
                return
            }
            sendToolType(id, MT_TOOL_PALM)
            sync()
            lift()
        }
    }

    companion object {
        const val EV_SYN = 0
        const val EV_KEY = 1
        const val EV_ABS = 3
        const val ABS_MT_SLOT = 0x2f
        const val ABS_MT_POSITION_X = 0x35
        const val ABS_MT_POSITION_Y = 0x36
        const val ABS_MT_TOOL_TYPE = 0x37
        const val ABS_MT_TRACKING_ID = 0x39
        const val ABS_MT_PRESSURE = 0x3a
        const val BTN_TOUCH = 0x14a
        const val BTN_TOOL_PEN = 0x140
        const val BTN_TOOL_FINGER = 0x145
        const val BTN_TOOL_DOUBLETAP = 0x14d
        const val BTN_TOOL_TRIPLETAP = 0x14e
        const val BTN_TOOL_QUADTAP = 0x14f
        const val BTN_TOOL_QUINTTAP = 0x148
        const val SYN_REPORT = 0
        const val MT_TOOL_FINGER = 0
        const val MT_TOOL_PEN = 1
        const val MT_TOOL_PALM = 2
        const val INVALID_TRACKING_ID = -1

        fun toolBtnForFingerCount(numFingers: Int): Int {
            return when (numFingers) {
                1 -> BTN_TOOL_FINGER
                2 -> BTN_TOOL_DOUBLETAP
                3 -> BTN_TOOL_TRIPLETAP
                4 -> BTN_TOOL_QUADTAP
                5 -> BTN_TOOL_QUINTTAP
                else -> throw IllegalArgumentException("Number of fingers must be between 1 and 5")
            }
        }
    }
}
