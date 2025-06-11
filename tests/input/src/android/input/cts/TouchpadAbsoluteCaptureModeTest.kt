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

package android.input.cts

import android.graphics.Point
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.compatibility.common.util.PollingCheck
import com.android.cts.input.BlockingQueueEventVerifier
import com.android.cts.input.EvdevInputEventCodes.Companion.BTN_LEFT
import com.android.cts.input.EvdevInputEventCodes.Companion.BTN_RIGHT
import com.android.cts.input.EvdevInputEventCodes.Companion.BTN_TOOL_DOUBLETAP
import com.android.cts.input.EvdevInputEventCodes.Companion.BTN_TOOL_FINGER
import com.android.cts.input.EvdevInputEventCodes.Companion.MT_TOOL_FINGER
import com.android.cts.input.EvdevInputEventCodes.Companion.MT_TOOL_PALM
import com.android.cts.input.UinputTouchPad
import com.android.cts.input.inputeventmatchers.withActionButton
import com.android.cts.input.inputeventmatchers.withAxisValue
import com.android.cts.input.inputeventmatchers.withButtonState
import com.android.cts.input.inputeventmatchers.withCoords
import com.android.cts.input.inputeventmatchers.withCoordsForPointerIndex
import com.android.cts.input.inputeventmatchers.withFlags
import com.android.cts.input.inputeventmatchers.withMotionAction
import com.android.cts.input.inputeventmatchers.withPointerCount
import com.android.cts.input.inputeventmatchers.withPointerIdForPointerIndex
import com.android.cts.input.inputeventmatchers.withRelativeMotion
import com.android.cts.input.inputeventmatchers.withRelativeMotionForPointerIndex
import com.android.cts.input.inputeventmatchers.withSource
import com.android.cts.input.inputeventmatchers.withToolType
import com.android.cts.input.inputeventmatchers.withToolTypeForPointerIndex
import kotlin.math.PI
import org.hamcrest.Matchers.allOf
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith

@MediumTest
@RunWith(AndroidJUnit4::class)
class TouchpadAbsoluteCaptureModeTest {
    private lateinit var touchpad: UinputTouchPad
    private lateinit var verifier: BlockingQueueEventVerifier
    private lateinit var activity: PointerCaptureActivity

    @get:Rule
    val testName = TestName()
    @get:Rule
    val rule = ActivityScenarioRule(PointerCaptureActivity::class.java)

    @Before
    fun setUp() {
        rule.scenario.onActivity { activity = it }

        touchpad = UinputTouchPad(InstrumentationRegistry.getInstrumentation(), activity.display)
        verifier = activity.verifier

        PollingCheck.waitFor { activity.hasWindowFocus() }
        activity.ensurePointerCaptured()
        // TODO(b/411389468): enable InputVerifier to check the captured pointer events produced.
    }

    @After
    fun tearDown() {
        if (this::touchpad.isInitialized) {
            touchpad.close()
        }
    }

    @Test
    fun testOneFinger_MotionReportedCorrectly() {
        val pointer = Point(50, 100)
        touchpad.sendDown(0, pointer)
        touchpad.sendBtnTouch(true)
        touchpad.sendBtn(BTN_TOOL_FINGER, true)
        touchpad.sync()

        verifier.assertReceivedMotion(allOf(
            withMotionAction(MotionEvent.ACTION_DOWN),
            withPointerCount(1),
            withCoords(Point(50, 100)),
            withRelativeMotion(0, 0),
            withToolType(MotionEvent.TOOL_TYPE_FINGER),
            withSource(InputDevice.SOURCE_TOUCHPAD),
        ))

        pointer.offset(2, -1)
        touchpad.sendMove(0, pointer)
        touchpad.sync()

        verifier.assertReceivedMotion(allOf(
            withMotionAction(MotionEvent.ACTION_MOVE),
            withPointerCount(1),
            withCoords(Point(52, 99)),
            withRelativeMotion(2, -1),
            withToolType(MotionEvent.TOOL_TYPE_FINGER),
            withSource(InputDevice.SOURCE_TOUCHPAD),
        ))

        touchpad.sendUp(0)
        touchpad.sendBtnTouch(false)
        touchpad.sendBtn(BTN_TOOL_FINGER, false)
        touchpad.sync()

        val commonMatcher = allOf(
            withPointerCount(1),
            withCoords(Point(52, 99)),
            withRelativeMotion(0, 0),
            withToolType(MotionEvent.TOOL_TYPE_FINGER),
            withSource(InputDevice.SOURCE_TOUCHPAD)
        )
        // The current pointer capture implementation will send a redundant MOVE event before the UP
        // even though the pointer location hasn't changed since the last MOVE event. Accept this,
        // but don't fail if it's not there, so that we can improve this in future without having to
        // update the CTS test.
        verifier.acceptOptionalMotion(allOf(
            withMotionAction(MotionEvent.ACTION_MOVE),
            commonMatcher,
        ))
        verifier.assertReceivedMotion(allOf(
            withMotionAction(MotionEvent.ACTION_UP),
            commonMatcher,
        ))
    }

    @Test
    fun testOneFinger_TouchDimensionsPassedThrough() {
        touchpad.sendDown(0, Point(0, 0))
        touchpad.sendTouchDimensions(0, 250, 120)
        touchpad.sendToolDimensions(0, 400, 200)
        touchpad.sendBtnTouch(true)
        touchpad.sendBtn(BTN_TOOL_FINGER, true)
        touchpad.sync()

        verifier.assertReceivedMotion(allOf(
            withMotionAction(MotionEvent.ACTION_DOWN),
            withPointerCount(1),
            withAxisValue(MotionEvent.AXIS_TOUCH_MAJOR, 250f),
            withAxisValue(MotionEvent.AXIS_TOUCH_MINOR, 120f),
            withAxisValue(MotionEvent.AXIS_TOOL_MAJOR, 400f),
            withAxisValue(MotionEvent.AXIS_TOOL_MINOR, 200f),
            withToolType(MotionEvent.TOOL_TYPE_FINGER),
            withSource(InputDevice.SOURCE_TOUCHPAD),
        ))
    }

    @Test
    fun testOneFinger_OrientationCalculatedCorrectly() {
        touchpad.sendDown(0, Point(0, 0))
        touchpad.sendOrientation(0, -3)
        touchpad.sendBtnTouch(true)
        touchpad.sendBtn(BTN_TOOL_FINGER, true)
        touchpad.sync()

        verifier.assertReceivedMotion(allOf(
            withMotionAction(MotionEvent.ACTION_DOWN),
            withAxisValue(MotionEvent.AXIS_ORIENTATION, (-3f * PI / 8f).toFloat()),
            withSource(InputDevice.SOURCE_TOUCHPAD),
        ))

        touchpad.sendOrientation(0, 0)
        touchpad.sync()

        verifier.assertReceivedMotion(allOf(
            withMotionAction(MotionEvent.ACTION_MOVE),
            withAxisValue(MotionEvent.AXIS_ORIENTATION, 0f),
            withSource(InputDevice.SOURCE_TOUCHPAD),
        ))

        touchpad.sendOrientation(0, 4)
        touchpad.sync()

        verifier.assertReceivedMotion(allOf(
            withMotionAction(MotionEvent.ACTION_MOVE),
            withAxisValue(MotionEvent.AXIS_ORIENTATION, (PI / 2f).toFloat()),
            withSource(InputDevice.SOURCE_TOUCHPAD),
        ))
    }


    @Test
    fun testOnePalm_neverReported() {
        val pointer = Point(50, 100)
        touchpad.sendDown(0, pointer)
        touchpad.sendToolType(0, MT_TOOL_PALM)
        touchpad.sendBtnTouch(true)
        touchpad.sendBtn(BTN_TOOL_FINGER, true)
        touchpad.sync()

        pointer.offset(1, 0)
        touchpad.sendMove(0, pointer)
        touchpad.sync()

        touchpad.sendUp(0)
        touchpad.sendBtnTouch(false)
        touchpad.sendBtn(BTN_TOOL_FINGER, false)
        touchpad.sync()

        // No events should have been reported for any of the activity in this test.
        verifier.assertNoEvents()
    }

    @Test
    fun testFingerTurningIntoPalm_cancelled() {
        var pointer = Point(50, 100)
        touchpad.sendDown(0, pointer)
        touchpad.sendToolType(0, MT_TOOL_FINGER)
        touchpad.sendBtnTouch(true)
        touchpad.sendBtn(BTN_TOOL_FINGER, true)
        touchpad.sync()

        verifier.assertReceivedMotion(allOf(
            withMotionAction(MotionEvent.ACTION_DOWN),
            withToolType(MotionEvent.TOOL_TYPE_FINGER),
            withPointerCount(1),
            withSource(InputDevice.SOURCE_TOUCHPAD),
        ))

        pointer.offset(1, 0)
        touchpad.sendMove(0, pointer)
        touchpad.sendToolType(0, MT_TOOL_PALM)
        touchpad.sync()

        verifier.assertReceivedMotion(allOf(
            withMotionAction(MotionEvent.ACTION_MOVE),
            withPointerCount(1),
            withSource(InputDevice.SOURCE_TOUCHPAD),
        ))
        verifier.assertReceivedMotion(allOf(
            withMotionAction(MotionEvent.ACTION_CANCEL),
            withPointerCount(1),
            withSource(InputDevice.SOURCE_TOUCHPAD),
        ))

        pointer.offset(1, 0)
        touchpad.sendMove(0, pointer)
        touchpad.sync()

        touchpad.sendUp(0)
        touchpad.sendBtnTouch(false)
        touchpad.sendBtn(BTN_TOOL_FINGER, false)
        touchpad.sync()

        verifier.assertNoEvents()
    }

    @Test
    fun testPalmTurningIntoFinger_reported() {
        var pointer = Point(50, 100)
        touchpad.sendDown(0, pointer)
        touchpad.sendToolType(0, MT_TOOL_PALM)
        touchpad.sendBtnTouch(true)
        touchpad.sendBtn(BTN_TOOL_FINGER, true)
        touchpad.sync()

        // No events should be reported, since the pointer has started as a palm.

        pointer.offset(1, 0)
        touchpad.sendMove(0, pointer)
        touchpad.sendToolType(0, MT_TOOL_FINGER)
        touchpad.sync()

        verifier.assertReceivedMotion(allOf(
            withMotionAction(MotionEvent.ACTION_DOWN),
            withPointerCount(1),
            withCoords(pointer),
            withRelativeMotion(0, 0),
            withSource(InputDevice.SOURCE_TOUCHPAD),
        ))

        pointer.offset(1, 0)
        touchpad.sendMove(0, pointer)
        touchpad.sync()

        verifier.assertReceivedMotion(allOf(
            withMotionAction(MotionEvent.ACTION_MOVE),
            withPointerCount(1),
            withCoords(pointer),
            withRelativeMotion(1, 0),
            withSource(InputDevice.SOURCE_TOUCHPAD),
        ))
    }

    @Test
    fun testFingerArrivingAfterPalm_onlyFingerReported() {
        var palmPointer = Point(50, 100)
        touchpad.sendDown(0, palmPointer)
        touchpad.sendToolType(0, MT_TOOL_PALM)
        touchpad.sendBtnTouch(true)
        touchpad.sendBtn(BTN_TOOL_FINGER, true)
        touchpad.sync()

        // No events should be reported, since the first pointer is a palm.

        var fingerPointer = Point(100, 150)
        touchpad.sendDown(1, fingerPointer)
        touchpad.sendToolType(1, MT_TOOL_FINGER)
        touchpad.sendBtn(BTN_TOOL_FINGER, false)
        touchpad.sendBtn(BTN_TOOL_DOUBLETAP, true)
        touchpad.sync()

        verifier.assertReceivedMotion(allOf(
            withMotionAction(MotionEvent.ACTION_DOWN),
            withPointerCount(1),
            withCoords(fingerPointer),
            withSource(InputDevice.SOURCE_TOUCHPAD),
        ))

        palmPointer.offset(2, 2)
        touchpad.sendMove(0, palmPointer)
        fingerPointer.offset(-2, -2)
        touchpad.sendMove(1, fingerPointer)
        touchpad.sync()

        verifier.assertReceivedMotion(allOf(
            withMotionAction(MotionEvent.ACTION_MOVE),
            withPointerCount(1),
            withCoords(fingerPointer),
            withRelativeMotion(-2, -2),
            withSource(InputDevice.SOURCE_TOUCHPAD),
        ))
    }

    @Test
    fun testFingerAndFingerTurningIntoPalm_partiallyCancelled() {
        var alwaysFingerPointer = Point(50, 0)
        touchpad.sendDown(0, alwaysFingerPointer)
        touchpad.sendToolType(0, MT_TOOL_FINGER)

        var eventualPalmPointer = Point(250, 0)
        touchpad.sendDown(1, eventualPalmPointer)
        touchpad.sendToolType(1, MT_TOOL_FINGER)

        touchpad.sendBtnTouch(true)
        touchpad.sendBtn(BTN_TOOL_DOUBLETAP, true)
        touchpad.sync()

        verifier.assertReceivedMotion(allOf(
            withMotionAction(MotionEvent.ACTION_DOWN),
            withPointerCount(1),
            withCoords(alwaysFingerPointer),
            withToolType(MotionEvent.TOOL_TYPE_FINGER),
            withSource(InputDevice.SOURCE_TOUCHPAD),
        ))
        verifier.assertReceivedMotion(allOf(
            withMotionAction(MotionEvent.ACTION_POINTER_DOWN, 1),
            withPointerCount(2),
            withCoordsForPointerIndex(0, alwaysFingerPointer),
            withToolTypeForPointerIndex(0, MotionEvent.TOOL_TYPE_FINGER),
            withCoordsForPointerIndex(1, eventualPalmPointer),
            withToolTypeForPointerIndex(1, MotionEvent.TOOL_TYPE_FINGER),
            withSource(InputDevice.SOURCE_TOUCHPAD),
        ))

        alwaysFingerPointer.offset(1, 0)
        touchpad.sendMove(0, alwaysFingerPointer)
        eventualPalmPointer.offset(1, 0)
        touchpad.sendMove(1, eventualPalmPointer)
        touchpad.sendToolType(1, MT_TOOL_PALM)
        touchpad.sync()

        verifier.assertReceivedMotion(allOf(
            withMotionAction(MotionEvent.ACTION_MOVE),
            withPointerCount(2),
            withCoordsForPointerIndex(0, alwaysFingerPointer),
            withCoordsForPointerIndex(1, eventualPalmPointer),
            withSource(InputDevice.SOURCE_TOUCHPAD),
        ))
        verifier.assertReceivedMotion(allOf(
            withMotionAction(MotionEvent.ACTION_POINTER_UP, 1),
            withFlags(MotionEvent.FLAG_CANCELED),
            withPointerCount(2),
            withCoordsForPointerIndex(0, alwaysFingerPointer),
            withCoordsForPointerIndex(1, eventualPalmPointer),
            withSource(InputDevice.SOURCE_TOUCHPAD),
        ))
    }

    @Test
    fun testFingerAndPalmTurningIntoFinger_reported() {
        var alwaysFingerPointer = Point(50, 0)
        touchpad.sendDown(0, alwaysFingerPointer)
        touchpad.sendToolType(0, MT_TOOL_FINGER)

        var eventualFingerPointer = Point(250, 0)
        touchpad.sendDown(1, eventualFingerPointer)
        touchpad.sendToolType(1, MT_TOOL_PALM)

        touchpad.sendBtnTouch(true)
        touchpad.sendBtn(BTN_TOOL_DOUBLETAP, true)
        touchpad.sync()

        verifier.assertReceivedMotion(allOf(
            withMotionAction(MotionEvent.ACTION_DOWN),
            withPointerCount(1),
            withCoords(alwaysFingerPointer),
            withToolType(MotionEvent.TOOL_TYPE_FINGER),
            withSource(InputDevice.SOURCE_TOUCHPAD),
        ))

        alwaysFingerPointer.offset(1, 0)
        touchpad.sendMove(0, alwaysFingerPointer)

        eventualFingerPointer.offset(1, 0)
        touchpad.sendMove(1, eventualFingerPointer)
        touchpad.sendToolType(1, MT_TOOL_FINGER)
        touchpad.sync()

        verifier.assertReceivedMotion(allOf(
            withMotionAction(MotionEvent.ACTION_MOVE),
            withPointerCount(1),
            withCoords(alwaysFingerPointer),
            withSource(InputDevice.SOURCE_TOUCHPAD),
        ))
        verifier.assertReceivedMotion(allOf(
            withMotionAction(MotionEvent.ACTION_POINTER_DOWN, 1),
            withPointerCount(2),
            withCoordsForPointerIndex(0, alwaysFingerPointer),
            withCoordsForPointerIndex(1, eventualFingerPointer),
            withSource(InputDevice.SOURCE_TOUCHPAD),
        ))
    }

    @Test
    fun testTwoFingers_motionReportedCorrectly() {
        var pointer0 = Point(50, 100)
        touchpad.sendDown(0, pointer0)
        touchpad.sendBtnTouch(true)
        touchpad.sendBtn(BTN_TOOL_FINGER, true)
        touchpad.sync()

        verifier.assertReceivedMotion(allOf(
            withMotionAction(MotionEvent.ACTION_DOWN),
            withSource(InputDevice.SOURCE_TOUCHPAD),
            withPointerCount(1),

            withCoords(pointer0),
            withRelativeMotion(0, 0),
            withToolType(MotionEvent.TOOL_TYPE_FINGER),
        ))

        pointer0.offset(2, -1)
        touchpad.sendMove(0, pointer0)

        val pointer1 = Point(250, 200)
        touchpad.sendDown(1, pointer1)
        touchpad.sendBtn(BTN_TOOL_FINGER, false)
        touchpad.sendBtn(BTN_TOOL_DOUBLETAP, true)
        touchpad.sync()

        verifier.assertReceivedMotion(allOf(
            withMotionAction(MotionEvent.ACTION_MOVE),
            withSource(InputDevice.SOURCE_TOUCHPAD),
            withPointerCount(1),

            withCoords(pointer0),
            withRelativeMotion(2, -1),
            withToolType(MotionEvent.TOOL_TYPE_FINGER),
        ))
        verifier.assertReceivedMotion(allOf(
            withMotionAction(MotionEvent.ACTION_POINTER_DOWN, 1),
            withSource(InputDevice.SOURCE_TOUCHPAD),
            withPointerCount(2),

            withCoordsForPointerIndex(0, pointer0),
            withRelativeMotionForPointerIndex(0, 0f, 0f),
            withToolTypeForPointerIndex(0, MotionEvent.TOOL_TYPE_FINGER),

            withCoordsForPointerIndex(1, pointer1),
            withRelativeMotionForPointerIndex(1, 0f, 0f),
            withToolTypeForPointerIndex(1, MotionEvent.TOOL_TYPE_FINGER),
        ))

        touchpad.sendUp(0)
        pointer1.offset(5, 2)
        touchpad.sendMove(1, pointer1)

        touchpad.sendBtn(BTN_TOOL_DOUBLETAP, false)
        touchpad.sendBtn(BTN_TOOL_FINGER, true)
        touchpad.sync()

        verifier.assertReceivedMotion(allOf(
            withMotionAction(MotionEvent.ACTION_MOVE),
            withSource(InputDevice.SOURCE_TOUCHPAD),
            withPointerCount(2),

            withCoordsForPointerIndex(0, pointer0),
            withRelativeMotionForPointerIndex(0, 0f, 0f),
            withToolTypeForPointerIndex(0, MotionEvent.TOOL_TYPE_FINGER),

            withCoordsForPointerIndex(1, pointer1),
            withRelativeMotionForPointerIndex(1, 5f, 2f),
            withToolTypeForPointerIndex(1, MotionEvent.TOOL_TYPE_FINGER),
        ))
        verifier.assertReceivedMotion(allOf(
            withMotionAction(MotionEvent.ACTION_POINTER_UP, 0),
            withSource(InputDevice.SOURCE_TOUCHPAD),
            withPointerCount(2),

            withCoordsForPointerIndex(0, pointer0),
            withRelativeMotionForPointerIndex(0, 0f, 0f),
            withToolTypeForPointerIndex(0, MotionEvent.TOOL_TYPE_FINGER),

            withCoordsForPointerIndex(1, pointer1),
            withRelativeMotionForPointerIndex(1, 0f, 0f),
            withToolTypeForPointerIndex(1, MotionEvent.TOOL_TYPE_FINGER),
        ))

        touchpad.sendUp(1)
        touchpad.sendBtn(BTN_TOOL_FINGER, false)
        touchpad.sendBtnTouch(false)
        touchpad.sync()

        verifier.acceptOptionalMotion(allOf(
            withMotionAction(MotionEvent.ACTION_MOVE),
            withSource(InputDevice.SOURCE_TOUCHPAD),
            withPointerCount(1),

            withCoords(pointer1),
            withRelativeMotion(0, 0),
            withToolType(MotionEvent.TOOL_TYPE_FINGER),
        ))
        verifier.assertReceivedMotion(allOf(
            withMotionAction(MotionEvent.ACTION_UP),
            withSource(InputDevice.SOURCE_TOUCHPAD),
            withPointerCount(1),

            withCoords(pointer1),
            withRelativeMotion(0, 0),
            withToolType(MotionEvent.TOOL_TYPE_FINGER),
        ))
    }

    @Test
    fun testRelativeMotionAxesClearedForNewFingerInSlot() {
        var pointer = Point(50, 100)
        // Put down one finger.
        touchpad.sendDown(0, pointer)
        touchpad.sendBtnTouch(true)
        touchpad.sendBtn(BTN_TOOL_FINGER, true)
        touchpad.sync()

        verifier.assertReceivedMotion(allOf(
            withMotionAction(MotionEvent.ACTION_DOWN),
            withPointerCount(1),
            withCoords(pointer),
            withRelativeMotion(0, 0),
            withSource(InputDevice.SOURCE_TOUCHPAD),
        ))

        // Move it.
        pointer.offset(-3, -3)
        touchpad.sendMove(0, pointer)
        touchpad.sync()

        verifier.assertReceivedMotion(allOf(
            withMotionAction(MotionEvent.ACTION_MOVE),
            withCoords(pointer),
            withRelativeMotion(-3, -3),
            withSource(InputDevice.SOURCE_TOUCHPAD),
        ))

        // Lift it.
        touchpad.sendUp(0)
        touchpad.sendBtnTouch(false)
        touchpad.sendBtn(BTN_TOOL_FINGER, false)
        touchpad.sync()

        verifier.acceptOptionalMotion(allOf(
            withMotionAction(MotionEvent.ACTION_MOVE),
            withCoords(pointer),
            withRelativeMotion(0, 0),
        ))
        verifier.assertReceivedMotion(allOf(
            withMotionAction(MotionEvent.ACTION_UP),
            withCoords(pointer),
            withRelativeMotion(0, 0),
            withSource(InputDevice.SOURCE_TOUCHPAD),
        ))

        // Put down another finger using the same slot. Relative axis values should be cleared.
        pointer = Point(60, 60)
        touchpad.sendDown(0, pointer)
        touchpad.sendBtnTouch(true)
        touchpad.sendBtn(BTN_TOOL_FINGER, true)
        touchpad.sync()

        verifier.assertReceivedMotion(allOf(
            withMotionAction(MotionEvent.ACTION_DOWN),
            withPointerCount(1),
            withCoords(pointer),
            withRelativeMotion(0, 0),
            withSource(InputDevice.SOURCE_TOUCHPAD),
        ))

        pointer.offset(4, -2)
        touchpad.sendMove(0, pointer)
        touchpad.sync()

        verifier.assertReceivedMotion(allOf(
            withMotionAction(MotionEvent.ACTION_MOVE),
            withPointerCount(1),
            withCoords(pointer),
            withRelativeMotion(4, -2),
            withSource(InputDevice.SOURCE_TOUCHPAD),
        ))
    }

    @Test
    fun testPointerIdsReusedAfterLift() {
        // Pointer IDs max out at 31, and so must be reused once a touch is
        // lifted to avoid running out.

        // Put down two fingers, which should get IDs 0 and 1.
        touchpad.sendDown(0, Point(10, 0))
        touchpad.sendDown(1, Point(20, 0))
        touchpad.sendBtnTouch(true)
        touchpad.sendBtn(BTN_TOOL_DOUBLETAP, true)
        touchpad.sync()

        verifier.assertReceivedMotion(allOf(
            withMotionAction(MotionEvent.ACTION_DOWN),
            withPointerCount(1),
            withPointerIdForPointerIndex(0, 0),
        ))
        verifier.assertReceivedMotion(allOf(
            withMotionAction(MotionEvent.ACTION_POINTER_DOWN, 1),
            withPointerCount(2),
            withPointerIdForPointerIndex(0, 0),
            withPointerIdForPointerIndex(1, 1),
        ))

        // Lift the finger in slot 0, freeing up pointer ID 0...
        touchpad.sendUp(0)
        // ...and simultaneously add a finger in slot 2.
        touchpad.sendDown(2, Point(30, 0))
        touchpad.sync()

        verifier.acceptOptionalMotion(allOf(
            withMotionAction(MotionEvent.ACTION_MOVE),
            withPointerCount(2),
            withPointerIdForPointerIndex(0, 0),
            withPointerIdForPointerIndex(1, 1),
        ))
        verifier.assertReceivedMotion(allOf(
            withMotionAction(MotionEvent.ACTION_POINTER_UP, 0),
            withPointerCount(2),
            withPointerIdForPointerIndex(0, 0),
            withPointerIdForPointerIndex(1, 1),
        ))
        // Slot 0 being lifted causes the finger from slot 1 to move up to index 0, but keep its
        // previous ID. The new finger in slot 2 should take ID 0, which was just freed up.
        verifier.assertReceivedMotion(allOf(
            withMotionAction(MotionEvent.ACTION_POINTER_DOWN, 1),
            withPointerCount(2),
            withPointerIdForPointerIndex(0, 1),
            withPointerIdForPointerIndex(1, 0),
        ))
    }

    @Test
    fun testButtonPressedAndReleasedInSameFrameAsTouch_ReportedWithPointers() {
        // Motion events without any pointers are invalid, so when a button press is reported in the
        // same frame as a touch down, the button press must be reported second. Similarly with a
        // button release and a touch lift.
        touchpad.sendDown(0, Point(50, 100))
        touchpad.sendBtnTouch(true)
        touchpad.sendBtn(BTN_TOOL_FINGER, true)
        touchpad.sendBtn(BTN_LEFT, true)
        touchpad.sync()

        verifier.assertReceivedMotion(allOf(
            withMotionAction(MotionEvent.ACTION_DOWN),
            withPointerCount(1),
            withSource(InputDevice.SOURCE_TOUCHPAD),
        ))
        verifier.assertReceivedMotion(allOf(
            withMotionAction(MotionEvent.ACTION_BUTTON_PRESS),
            withPointerCount(1),
            withActionButton(MotionEvent.BUTTON_PRIMARY),
            withButtonState(MotionEvent.BUTTON_PRIMARY),
            withSource(InputDevice.SOURCE_TOUCHPAD),
        ))

        touchpad.sendUp(0)
        touchpad.sendBtnTouch(false)
        touchpad.sendBtn(BTN_TOOL_FINGER, false)
        touchpad.sendBtn(BTN_LEFT, false)
        touchpad.sync()

        verifier.acceptOptionalMotion(allOf(
            withMotionAction(MotionEvent.ACTION_MOVE),
            withPointerCount(1),
            withButtonState(MotionEvent.BUTTON_PRIMARY),
            withSource(InputDevice.SOURCE_TOUCHPAD),
        ))
        verifier.assertReceivedMotion(allOf(
            withMotionAction(MotionEvent.ACTION_BUTTON_RELEASE),
            withPointerCount(1),
            withActionButton(MotionEvent.BUTTON_PRIMARY),
            withButtonState(0),
            withSource(InputDevice.SOURCE_TOUCHPAD),
        ))
        verifier.assertReceivedMotion(allOf(
            withMotionAction(MotionEvent.ACTION_UP),
            withPointerCount(1),
            withButtonState(0),
            withSource(InputDevice.SOURCE_TOUCHPAD),
        ))
    }

    @Test
    fun testButtonPressedBeforeTouch_ReportedOnceTouchOccurs() {
        // Some touchpads sometimes report a button press before they report the finger touching the
        // pad. In that case we need to wait until the touch comes to report the button press.
        val pointer = Point(50, 100)
        touchpad.sendBtn(BTN_LEFT, true)
        touchpad.sync()
        verifier.assertNoEvents()

        touchpad.sendDown(0, pointer)
        touchpad.sendBtnTouch(true)
        touchpad.sendBtn(BTN_TOOL_FINGER, true)
        touchpad.sync()

        verifier.assertReceivedMotion(allOf(
            withMotionAction(MotionEvent.ACTION_DOWN),
            withPointerCount(1),
            withCoords(pointer),
            withSource(InputDevice.SOURCE_TOUCHPAD),
        ))
        verifier.assertReceivedMotion(allOf(
            withMotionAction(MotionEvent.ACTION_BUTTON_PRESS),
            withPointerCount(1),
            withCoords(pointer),
            withActionButton(MotionEvent.BUTTON_PRIMARY),
            withButtonState(MotionEvent.BUTTON_PRIMARY),
            withSource(InputDevice.SOURCE_TOUCHPAD),
        ))
    }

    @Test
    fun testButtonReleasedAfterTouchLifts_ReportedWithLift() {
        // When all fingers are lifted from a touchpad, we should release any buttons that are down,
        // since we won't be able to report them being lifted later if no pointers are present.
        touchpad.sendDown(0, Point(50, 100))
        touchpad.sendBtnTouch(true)
        touchpad.sendBtn(BTN_TOOL_FINGER, true)
        touchpad.sendBtn(BTN_LEFT, true)
        touchpad.sync()

        verifier.assertReceivedMotion(withMotionAction(MotionEvent.ACTION_DOWN))
        verifier.assertReceivedMotion(withMotionAction(MotionEvent.ACTION_BUTTON_PRESS))

        touchpad.sendUp(0)
        touchpad.sendBtnTouch(false)
        touchpad.sendBtn(BTN_TOOL_FINGER, false)
        touchpad.sync()

        verifier.acceptOptionalMotion(allOf(
            withMotionAction(MotionEvent.ACTION_MOVE),
            withPointerCount(1),
            withButtonState(MotionEvent.BUTTON_PRIMARY),
            withSource(InputDevice.SOURCE_TOUCHPAD),
        ))
        verifier.assertReceivedMotion(allOf(
            withMotionAction(MotionEvent.ACTION_BUTTON_RELEASE),
            withPointerCount(1),
            withActionButton(MotionEvent.BUTTON_PRIMARY),
            withButtonState(0),
            withSource(InputDevice.SOURCE_TOUCHPAD),
        ))
        verifier.assertReceivedMotion(allOf(
            withMotionAction(MotionEvent.ACTION_UP),
            withPointerCount(1),
            withButtonState(0),
            withSource(InputDevice.SOURCE_TOUCHPAD),
        ))

        touchpad.sendBtn(BTN_LEFT, false)
        touchpad.sync()
        // There shouldn't be any events, as we reported the button release early.
        verifier.assertNoEvents()
    }

    @Test
    fun testMultipleButtonsPressedDuringTouch_ReportedCorrectly() {
        val pointer = Point(50, 100)
        touchpad.sendDown(0, pointer)
        touchpad.sendBtnTouch(true)
        touchpad.sendBtn(BTN_TOOL_FINGER, true)
        touchpad.sync()
        verifier.assertReceivedMotion(allOf(
            withMotionAction(MotionEvent.ACTION_DOWN),
            withCoords(pointer),
            withButtonState(0),
        ))

        touchpad.sendBtn(BTN_LEFT, true)
        touchpad.sync()
        verifier.acceptOptionalMotion(allOf(
            withMotionAction(MotionEvent.ACTION_MOVE),
            withCoords(pointer),
            withButtonState(0),
        ))
        verifier.assertReceivedMotion(allOf(
            withMotionAction(MotionEvent.ACTION_BUTTON_PRESS),
            withCoords(pointer),
            withActionButton(MotionEvent.BUTTON_PRIMARY),
            withButtonState(MotionEvent.BUTTON_PRIMARY),
        ))

        touchpad.sendBtn(BTN_RIGHT, true)
        touchpad.sync()
        verifier.acceptOptionalMotion(allOf(
            withMotionAction(MotionEvent.ACTION_MOVE),
            withCoords(pointer),
            withButtonState(MotionEvent.BUTTON_PRIMARY),
        ))
        verifier.assertReceivedMotion(allOf(
            withMotionAction(MotionEvent.ACTION_BUTTON_PRESS),
            withCoords(pointer),
            withActionButton(MotionEvent.BUTTON_SECONDARY),
            withButtonState(MotionEvent.BUTTON_PRIMARY or MotionEvent.BUTTON_SECONDARY),
        ))

        touchpad.sendBtn(BTN_LEFT, false)
        touchpad.sync()
        verifier.acceptOptionalMotion(allOf(
            withMotionAction(MotionEvent.ACTION_MOVE),
            withCoords(pointer),
            withButtonState(MotionEvent.BUTTON_PRIMARY or MotionEvent.BUTTON_SECONDARY),
        ))
        verifier.assertReceivedMotion(allOf(
            withMotionAction(MotionEvent.ACTION_BUTTON_RELEASE),
            withCoords(pointer),
            withActionButton(MotionEvent.BUTTON_PRIMARY),
            withButtonState(MotionEvent.BUTTON_SECONDARY),
        ))

        touchpad.sendBtn(BTN_RIGHT, false)
        touchpad.sync()
        verifier.acceptOptionalMotion(allOf(
            withMotionAction(MotionEvent.ACTION_MOVE),
            withCoords(pointer),
            withButtonState(MotionEvent.BUTTON_SECONDARY),
        ))
        verifier.assertReceivedMotion(allOf(
            withMotionAction(MotionEvent.ACTION_BUTTON_RELEASE),
            withCoords(pointer),
            withActionButton(MotionEvent.BUTTON_SECONDARY),
            withButtonState(0),
        ))
    }
}
