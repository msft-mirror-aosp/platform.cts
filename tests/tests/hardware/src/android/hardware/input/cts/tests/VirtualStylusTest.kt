/*
 * Copyright 2023 The Android Open Source Project
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
package android.hardware.input.cts.tests

import android.companion.virtual.flags.Flags
import android.hardware.input.VirtualStylus
import android.hardware.input.VirtualStylusButtonEvent
import android.hardware.input.VirtualStylusMotionEvent
import android.hardware.input.cts.virtualcreators.VirtualInputDeviceCreator
import android.hardware.input.cts.virtualcreators.VirtualInputEventCreator
import android.platform.test.annotations.RequiresFlagsEnabled
import android.view.InputDevice
import android.view.InputEvent
import android.view.MotionEvent
import androidx.test.filters.SmallTest
import junitparams.JUnitParamsRunner
import junitparams.Parameters
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RequiresFlagsEnabled(Flags.FLAG_VIRTUAL_STYLUS)
@SmallTest
@RunWith(JUnitParamsRunner::class)
class VirtualStylusTest : VirtualDeviceTestCase() {
    private lateinit var mVirtualStylus: VirtualStylus

    override fun onSetUpVirtualInputDevice() {
        mVirtualStylus = VirtualInputDeviceCreator.createAndPrepareStylus(
            mVirtualDevice,
            DEVICE_NAME, mVirtualDisplay.display
        ).device
        // We expect to get the exact coordinates in the view that were injected using the
        // stylus. Touch resampling could result in the generation of additional "fake" touch
        // events. To disable resampling, request unbuffered dispatch.
        mTestActivity.window.decorView.requestUnbufferedDispatch(
            InputDevice.SOURCE_STYLUS
        )
    }

    @Parameters(method = "allToolTypes")
    @Test
    fun sendTouchEvents(toolType: Int) {
        val x = 50
        val y = 50
        // The number of move events that are sent between the down and up event.
        val moveEventCount = 5
        val expectedEvents: MutableList<InputEvent> = ArrayList(moveEventCount + 2)
        // The builder is used for all events in this test. So properties all events have in common
        // are set here.
        val builder: VirtualStylusMotionEvent.Builder = VirtualStylusMotionEvent.Builder()
            .setToolType(toolType)

        // Down event
        mVirtualStylus.sendMotionEvent(
            builder
                .setAction(VirtualStylusMotionEvent.ACTION_DOWN)
                .setX(x)
                .setY(y)
                .setPressure(255)
                .build()
        )
        expectedEvents.add(
            VirtualInputEventCreator.createStylusTouchMotionEvent(
                MotionEvent.ACTION_DOWN,
                x.toFloat(),
                y.toFloat(),
                toolType
            )
        )

        // Next we send a bunch of ACTION_MOVE events. Each one with a different x and y coordinate.
        builder.setAction(VirtualStylusMotionEvent.ACTION_MOVE)
        for (i in 1..moveEventCount) {
            builder.setX(x + i)
                .setY(y + i)
                .setPressure(255)
            mVirtualStylus.sendMotionEvent(builder.build())
            expectedEvents.add(
                VirtualInputEventCreator.createStylusTouchMotionEvent(
                    MotionEvent.ACTION_MOVE,
                    (x + i).toFloat(),
                    (y + i).toFloat(),
                    toolType
                )
            )
        }

        // Up event
        mVirtualStylus.sendMotionEvent(
            builder
                .setAction(VirtualStylusMotionEvent.ACTION_UP)
                .setX(x + moveEventCount)
                .setY(y + moveEventCount)
                .build()
        )
        expectedEvents.add(
            VirtualInputEventCreator.createStylusTouchMotionEvent(
                MotionEvent.ACTION_UP,
                (x + moveEventCount).toFloat(),
                (y + moveEventCount).toFloat(),
                toolType
            )
        )

        verifyEvents(expectedEvents)
    }

    @Parameters(method = "allButtonCodes")
    @Test
    fun sendTouchEvents_withButtonPressed(buttonCode: Int) {
        val startX = 50
        val startY = 50
        val endX = 60
        val endY = 60
        val toolType: Int = VirtualStylusMotionEvent.TOOL_TYPE_STYLUS
        moveStylusWithButtonPressed(
            startX,
            startY,
            endX,
            endY,
            pressure = 255,
            buttonCode,
            toolType
        )

        verifyEvents(
            listOf<InputEvent>(
                VirtualInputEventCreator.createStylusTouchMotionEvent(
                    MotionEvent.ACTION_DOWN,
                    startX.toFloat(),
                    startY.toFloat(),
                    toolType,
                    buttonCode
                ),
                VirtualInputEventCreator.createStylusTouchMotionEvent(
                    MotionEvent.ACTION_BUTTON_PRESS,
                    startX.toFloat(),
                    startY.toFloat(),
                    toolType,
                    buttonCode
                ),
                VirtualInputEventCreator.createStylusTouchMotionEvent(
                    MotionEvent.ACTION_MOVE,
                    startX.toFloat(),
                    endY.toFloat(),
                    toolType,
                    buttonCode
                ),
                VirtualInputEventCreator.createStylusTouchMotionEvent(
                    MotionEvent.ACTION_MOVE,
                    endX.toFloat(),
                    endY.toFloat(),
                    toolType,
                    buttonCode
                ),
                VirtualInputEventCreator.createStylusTouchMotionEvent(
                    MotionEvent.ACTION_BUTTON_RELEASE,
                    endX.toFloat(),
                    endY.toFloat(),
                    toolType
                ),
                VirtualInputEventCreator.createStylusTouchMotionEvent(
                    MotionEvent.ACTION_UP,
                    endX.toFloat(),
                    endY.toFloat(),
                    toolType
                )
            )
        )
    }

    @Test
    fun sendTouchEvents_withTilt() {
        verifyStylusTouchWithTilt(
            tiltXDegrees = 0,
            tiltYDegrees = 0,
            expectedTiltDegrees = 0,
            expectedOrientationDegrees = 0
        )
        verifyStylusTouchWithTilt(
            tiltXDegrees = 90,
            tiltYDegrees = 0,
            expectedTiltDegrees = 90,
            expectedOrientationDegrees = -90
        )
        verifyStylusTouchWithTilt(
            tiltXDegrees = -90,
            tiltYDegrees = 0,
            expectedTiltDegrees = 90,
            expectedOrientationDegrees = 90
        )
        verifyStylusTouchWithTilt(
            tiltXDegrees = 0,
            tiltYDegrees = 90,
            expectedTiltDegrees = 90,
            expectedOrientationDegrees = 0
        )
        verifyStylusTouchWithTilt(
            tiltXDegrees = 0,
            tiltYDegrees = -90,
            expectedTiltDegrees = 90,
            expectedOrientationDegrees = -180
        )
        verifyStylusTouchWithTilt(
            tiltXDegrees = 90,
            tiltYDegrees = -90,
            expectedTiltDegrees = 90,
            expectedOrientationDegrees = -135
        )
        verifyStylusTouchWithTilt(
            tiltXDegrees = 90,
            tiltYDegrees = 90,
            expectedTiltDegrees = 90,
            expectedOrientationDegrees = -45
        )
        verifyStylusTouchWithTilt(
            tiltXDegrees = -90,
            tiltYDegrees = 90,
            expectedTiltDegrees = 90,
            expectedOrientationDegrees = 45
        )
        verifyStylusTouchWithTilt(
            tiltXDegrees = -90,
            tiltYDegrees = -90,
            expectedTiltDegrees = 90,
            expectedOrientationDegrees = 135
        )
    }

    @Parameters(method = "allToolTypes")
    @Test
    fun sendHoverEvents(toolType: Int) {
        val x0 = 50
        val y0 = 50
        val x1 = 60
        val y1 = 60
        val pressure = 0

        sendMotionEvent(VirtualStylusMotionEvent.ACTION_DOWN, x0, y0, pressure, toolType)
        sendMotionEvent(VirtualStylusMotionEvent.ACTION_MOVE, x0, y1, pressure, toolType)
        sendMotionEvent(VirtualStylusMotionEvent.ACTION_MOVE, x1, y1, pressure, toolType)
        sendMotionEvent(VirtualStylusMotionEvent.ACTION_UP, x1, y1, pressure, toolType)

        verifyEvents(
            listOf<InputEvent>(
                VirtualInputEventCreator.createStylusHoverMotionEvent(
                    MotionEvent.ACTION_HOVER_ENTER,
                    x0.toFloat(),
                    y0.toFloat(),
                    toolType
                ),
                VirtualInputEventCreator.createStylusHoverMotionEvent(
                    MotionEvent.ACTION_HOVER_MOVE,
                    x0.toFloat(),
                    y0.toFloat(),
                    toolType
                ),
                VirtualInputEventCreator.createStylusHoverMotionEvent(
                    MotionEvent.ACTION_HOVER_MOVE,
                    x0.toFloat(),
                    y1.toFloat(),
                    toolType
                ),
                VirtualInputEventCreator.createStylusHoverMotionEvent(
                    MotionEvent.ACTION_HOVER_MOVE,
                    x1.toFloat(),
                    y1.toFloat(),
                    toolType
                ),
                VirtualInputEventCreator.createStylusHoverMotionEvent(
                    MotionEvent.ACTION_HOVER_EXIT,
                    x1.toFloat(),
                    y1.toFloat(),
                    toolType
                )
            )
        )
    }

    @Parameters(method = "allButtonCodes")
    @Test
    fun sendHoverEvents_withButtonAlwaysPressed(buttonCode: Int) {
        val startX = 60
        val startY = 60
        val endX = 50
        val endY = 50
        val toolType: Int = VirtualStylusMotionEvent.TOOL_TYPE_STYLUS
        moveStylusWithButtonPressed(
            startX,
            startY,
            endX,
            endY,
            pressure = 0,
            buttonCode,
            toolType
        )

        verifyEvents(
            listOf<InputEvent>(
                VirtualInputEventCreator.createStylusHoverMotionEvent(
                    MotionEvent.ACTION_HOVER_ENTER,
                    startX.toFloat(),
                    startY.toFloat(),
                    toolType,
                    buttonCode
                ),
                VirtualInputEventCreator.createStylusHoverMotionEvent(
                    MotionEvent.ACTION_HOVER_MOVE,
                    startX.toFloat(),
                    startY.toFloat(),
                    toolType,
                    buttonCode
                ),
                VirtualInputEventCreator.createStylusHoverMotionEvent(
                    MotionEvent.ACTION_HOVER_MOVE,
                    startX.toFloat(),
                    endY.toFloat(),
                    toolType,
                    buttonCode
                ),
                VirtualInputEventCreator.createStylusHoverMotionEvent(
                    MotionEvent.ACTION_HOVER_MOVE,
                    endX.toFloat(),
                    endY.toFloat(),
                    toolType,
                    buttonCode
                ),
                VirtualInputEventCreator.createStylusHoverMotionEvent(
                    MotionEvent.ACTION_HOVER_EXIT,
                    endX.toFloat(),
                    endY.toFloat(),
                    toolType,
                    buttonCode
                )
            )
        )
    }

    @Parameters(method = "allButtonCodes")
    @Test
    fun stylusButtonPressRelease_withoutHoverOrTouch(buttonCode: Int) {
        mVirtualStylus.sendButtonEvent(
            VirtualStylusButtonEvent.Builder()
                .setAction(VirtualStylusButtonEvent.ACTION_BUTTON_PRESS)
                .setButtonCode(buttonCode)
                .build()
        )
        mVirtualStylus.sendButtonEvent(
            VirtualStylusButtonEvent.Builder()
                .setAction(VirtualStylusButtonEvent.ACTION_BUTTON_RELEASE)
                .setButtonCode(buttonCode)
                .build()
        )

        assertNoMoreEvents()
    }

    @Test
    fun sendTouchEvent_withoutCreateVirtualDevicePermission_throwsException() {
        val x = 50
        val y = 50
        mRule.runWithoutPermissions {
            assertThrows(SecurityException::class.java) {
                mVirtualStylus.sendMotionEvent(
                    VirtualStylusMotionEvent.Builder()
                        .setAction(VirtualStylusMotionEvent.ACTION_DOWN)
                        .setX(x)
                        .setY(y)
                        .setPressure(255)
                        .setToolType(VirtualStylusMotionEvent.TOOL_TYPE_STYLUS)
                        .build()
                )
            }
        }
    }

    private fun verifyStylusTouchWithTilt(
        tiltXDegrees: Int,
        tiltYDegrees: Int,
        expectedTiltDegrees: Int,
        expectedOrientationDegrees: Int
    ) {
        val x0 = 60
        val y0 = 60
        val x1 = 50
        val y1 = 50
        val pressure = 255
        val toolType: Int = VirtualStylusMotionEvent.TOOL_TYPE_STYLUS

        sendMotionEvent(
            VirtualStylusMotionEvent.ACTION_DOWN,
            x0,
            y0,
            pressure,
            toolType,
            tiltXDegrees,
            tiltYDegrees
        )
        sendMotionEvent(
            VirtualStylusMotionEvent.ACTION_MOVE,
            x0,
            y1,
            pressure,
            toolType,
            tiltXDegrees,
            tiltYDegrees
        )
        sendMotionEvent(
            VirtualStylusMotionEvent.ACTION_MOVE,
            x1,
            y1,
            pressure,
            toolType,
            tiltXDegrees,
            tiltYDegrees
        )
        sendMotionEvent(
            VirtualStylusMotionEvent.ACTION_UP,
            x1,
            y1,
            pressure,
            toolType,
            tiltXDegrees,
            tiltYDegrees
        )

        val expectedTiltRadians = Math.toRadians(expectedTiltDegrees.toDouble()).toFloat()
        val expectedOrientationRadians =
            Math.toRadians(expectedOrientationDegrees.toDouble()).toFloat()
        verifyEvents(
            listOf<InputEvent>(
                VirtualInputEventCreator.createStylusTouchMotionEvent(
                    MotionEvent.ACTION_DOWN,
                    x0.toFloat(),
                    y0.toFloat(),
                    toolType,
                    expectedTiltRadians,
                    expectedOrientationRadians
                ),
                VirtualInputEventCreator.createStylusTouchMotionEvent(
                    MotionEvent.ACTION_MOVE,
                    x0.toFloat(),
                    y1.toFloat(),
                    toolType,
                    expectedTiltRadians,
                    expectedOrientationRadians
                ),
                VirtualInputEventCreator.createStylusTouchMotionEvent(
                    MotionEvent.ACTION_MOVE,
                    x1.toFloat(),
                    y1.toFloat(),
                    toolType,
                    expectedTiltRadians,
                    expectedOrientationRadians
                ),
                VirtualInputEventCreator.createStylusTouchMotionEvent(
                    MotionEvent.ACTION_UP,
                    x1.toFloat(),
                    y1.toFloat(),
                    toolType,
                    expectedTiltRadians,
                    expectedOrientationRadians
                )
            )
        )
    }

    private fun moveStylusWithButtonPressed(
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        pressure: Int,
        buttonCode: Int,
        toolType: Int
    ) {
        mVirtualStylus.sendButtonEvent(
            VirtualStylusButtonEvent.Builder()
                .setAction(VirtualStylusButtonEvent.ACTION_BUTTON_PRESS)
                .setButtonCode(buttonCode)
                .build()
        )
        sendMotionEvent(VirtualStylusMotionEvent.ACTION_DOWN, startX, startY, pressure, toolType)
        sendMotionEvent(VirtualStylusMotionEvent.ACTION_MOVE, startX, endY, pressure, toolType)
        sendMotionEvent(VirtualStylusMotionEvent.ACTION_MOVE, endX, endY, pressure, toolType)
        sendMotionEvent(VirtualStylusMotionEvent.ACTION_UP, endX, endY, pressure, toolType)
        mVirtualStylus.sendButtonEvent(
            VirtualStylusButtonEvent.Builder()
                .setAction(VirtualStylusButtonEvent.ACTION_BUTTON_RELEASE)
                .setButtonCode(buttonCode)
                .build()
        )
    }

    private fun sendMotionEvent(
        action: Int,
        x: Int,
        y: Int,
        pressure: Int,
        toolType: Int,
        tiltX: Int = 0,
        tiltY: Int = 0
    ) {
        mVirtualStylus.sendMotionEvent(
            VirtualStylusMotionEvent.Builder()
                .setAction(action)
                .setToolType(toolType)
                .setX(x)
                .setY(y)
                .setTiltX(tiltX)
                .setTiltY(tiltY)
                .setPressure(pressure)
                .build()
        )
    }

    private fun allButtonCodes(): Array<Int> = arrayOf(
        VirtualStylusButtonEvent.BUTTON_PRIMARY,
        VirtualStylusButtonEvent.BUTTON_SECONDARY,
    )

    private fun allToolTypes(): Array<Int> = arrayOf(
        VirtualStylusMotionEvent.TOOL_TYPE_STYLUS,
        VirtualStylusMotionEvent.TOOL_TYPE_ERASER,
    )

    companion object {
        private const val DEVICE_NAME = "CtsVirtualStylusTestDevice"
    }
}
