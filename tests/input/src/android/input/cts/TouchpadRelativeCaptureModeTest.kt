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
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.compatibility.common.util.ApiTest
import com.android.compatibility.common.util.WindowUtil
import com.android.cts.input.BlockingQueueEventVerifier
import com.android.cts.input.EvdevInputEventCodes.Companion.BTN_LEFT
import com.android.cts.input.EvdevInputEventCodes.Companion.BTN_TOOL_DOUBLETAP
import com.android.cts.input.EvdevInputEventCodes.Companion.BTN_TOOL_FINGER
import com.android.cts.input.UinputTouchPad
import com.android.cts.input.inputeventmatchers.withActionButton
import com.android.cts.input.inputeventmatchers.withAxisValue
import com.android.cts.input.inputeventmatchers.withButtonState
import com.android.cts.input.inputeventmatchers.withMotionAction
import com.android.cts.input.inputeventmatchers.withNegativeAxisValue
import com.android.cts.input.inputeventmatchers.withPointerCount
import com.android.cts.input.inputeventmatchers.withPositiveAxisValue
import com.android.cts.input.inputeventmatchers.withSource
import com.android.cts.input.inputeventmatchers.withToolType
import com.android.hardware.input.Flags
import java.util.concurrent.TimeUnit
import org.hamcrest.Matcher
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.allOf
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith

@ApiTest(apis = ["android.view.View#requestPointerCapture"])
@MediumTest
@RequiresFlagsEnabled(Flags.FLAG_POINTER_CAPTURE_MODES)
@RunWith(AndroidJUnit4::class)
class TouchpadRelativeCaptureModeTest {
    companion object {
        val commonMatcher = allOf(
            withToolType(MotionEvent.TOOL_TYPE_MOUSE),
            withSource(InputDevice.SOURCE_MOUSE_RELATIVE),
            withPointerCount(1),
        )
    }

    private lateinit var touchpad: UinputTouchPad
    private lateinit var verifier: BlockingQueueEventVerifier
    private lateinit var activity: PointerCaptureActivity

    @get:Rule
    val testName = TestName()
    @get:Rule
    val checkFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()
    @get:Rule
    val rule = ActivityScenarioRule(PointerCaptureActivity::class.java)

    @Before
    fun setUp() {
        rule.scenario.onActivity { activity = it }

        touchpad = UinputTouchPad(InstrumentationRegistry.getInstrumentation(), activity.display)
        verifier = activity.verifier

        WindowUtil.waitForFocus(activity)
        activity.ensurePointerCaptured(View.POINTER_CAPTURE_MODE_RELATIVE)
    }

    @After
    fun tearDown() {
        if (this::touchpad.isInitialized) {
            touchpad.close()
        }
    }

    /**
     * Takes one event plus any more that appear in the queue within a timeout, and checks that they
     * all match the given matcher.
     */
    fun assertAllReceivedMotions(matcher: Matcher<MotionEvent>) {
        verifier.assertReceivedMotion(matcher, "first MotionEvent did not match")
        var event = verifier.queue.poll(5000, TimeUnit.MILLISECONDS)
        while (event != null && event is MotionEvent) {
            assertThat("subsequent MotionEvent did not match", event, matcher)
            event = verifier.queue.poll(5000, TimeUnit.MILLISECONDS)
        }
    }

    @Test
    fun testOneFingerMove() {
        val pointer = Point(500, 500)
        touchpad.sendDown(0, pointer)
        touchpad.sendBtnTouch(true)
        touchpad.sendBtn(BTN_TOOL_FINGER, true)
        touchpad.sync()
        for (i in 0..2) {
            Thread.sleep(5)
            pointer.offset(100, -10)
            touchpad.sendMove(0, pointer)
            touchpad.sync()
        }
        touchpad.sendUp(0)
        touchpad.sendBtnTouch(false)
        touchpad.sendBtn(BTN_TOOL_FINGER, false)
        touchpad.sync()

        assertAllReceivedMotions(allOf(
            withMotionAction(MotionEvent.ACTION_MOVE),
            // We want to check that the reported motions are in the right direction, but leave the
            // actual magnitude of the motion unspecified to allow scale factors to be tweaked.
            withPositiveAxisValue(MotionEvent.AXIS_X),
            withPositiveAxisValue(MotionEvent.AXIS_RELATIVE_X),
            withNegativeAxisValue(MotionEvent.AXIS_Y),
            withNegativeAxisValue(MotionEvent.AXIS_RELATIVE_Y),
            commonMatcher,
        ))
    }

    @Test
    fun testClickAndDrag() {
        val pointer = Point(500, 500)
        touchpad.sendDown(0, pointer)
        touchpad.sendBtnTouch(true)
        touchpad.sendBtn(BTN_TOOL_FINGER, true)
        touchpad.sendBtn(BTN_LEFT, true)
        touchpad.sync()

        verifier.assertReceivedMotion(allOf(
            withMotionAction(MotionEvent.ACTION_DOWN),
            withButtonState(MotionEvent.BUTTON_PRIMARY),
            commonMatcher,
        ))
        verifier.assertReceivedMotion(allOf(
            withMotionAction(MotionEvent.ACTION_BUTTON_PRESS),
            withActionButton(MotionEvent.BUTTON_PRIMARY),
            withButtonState(MotionEvent.BUTTON_PRIMARY),
            commonMatcher,
        ))

        // We have to pause here, otherwise the Gestures library will discard the motion as click
        // wiggle. The wait time must be longer than the "One Finger Click Wiggle Timeout"
        // gesture property.
        Thread.sleep(210)
        for (i in 0..4) {
            Thread.sleep(5)
            pointer.offset(100, -10)
            touchpad.sendMove(0, pointer)
            touchpad.sync()
        }
        assertAllReceivedMotions(allOf(
            withMotionAction(MotionEvent.ACTION_MOVE),
            withButtonState(MotionEvent.BUTTON_PRIMARY),
            // We want to check that the reported motions are in the right direction, but leave the
            // actual magnitude of the motion unspecified to allow scale factors to be tweaked.
            withPositiveAxisValue(MotionEvent.AXIS_X),
            withPositiveAxisValue(MotionEvent.AXIS_RELATIVE_X),
            withNegativeAxisValue(MotionEvent.AXIS_Y),
            withNegativeAxisValue(MotionEvent.AXIS_RELATIVE_Y),
            commonMatcher,
        ))

        touchpad.sendUp(0)
        touchpad.sendBtnTouch(false)
        touchpad.sendBtn(BTN_TOOL_FINGER, false)
        touchpad.sendBtn(BTN_LEFT, false)
        touchpad.sync()
        verifier.assertReceivedMotion(allOf(
            withMotionAction(MotionEvent.ACTION_BUTTON_RELEASE),
            withActionButton(MotionEvent.BUTTON_PRIMARY),
            withButtonState(0),
            commonMatcher,
        ))
        verifier.assertReceivedMotion(allOf(
            withMotionAction(MotionEvent.ACTION_UP),
            withButtonState(0),
            commonMatcher,
        ))
    }

    private fun twoFingerSwipe(xOffset: Int, yOffset: Int) {
        val pointer0 = Point(500, 500)
        val pointer1 = Point(700, 500)

        touchpad.sendBtnTouch(true)
        touchpad.sendBtn(BTN_TOOL_DOUBLETAP, true)
        touchpad.sendDown(0, pointer0)
        touchpad.sendDown(1, pointer1)
        touchpad.sync()
        Thread.sleep(5)

        for (rep in 0 until 10) {
            pointer0.offset(xOffset, yOffset)
            touchpad.sendMove(0, pointer0)
            pointer1.offset(xOffset, yOffset)
            touchpad.sendMove(1, pointer1)
            touchpad.sync()
            Thread.sleep(5)
        }

        touchpad.sendUp(0)
        touchpad.sendUp(1)
        touchpad.sendBtn(BTN_TOOL_DOUBLETAP, false)
        touchpad.sendBtnTouch(false)
        touchpad.sync()
    }

    @Test
    fun testTwoFingerVerticalScroll() {
        twoFingerSwipe(0, 40)

        assertAllReceivedMotions(allOf(
            withMotionAction(MotionEvent.ACTION_SCROLL),
            // We want to check that the reported motions are in the right direction, but leave the
            // actual magnitude of the motion unspecified to allow scale factors to be tweaked.
            withAxisValue(MotionEvent.AXIS_HSCROLL, 0f),
            withPositiveAxisValue(MotionEvent.AXIS_VSCROLL),
            commonMatcher,
        ))
    }

    @Test
    fun testTwoFingerHorizontalScroll() {
        twoFingerSwipe(40, 0)

        assertAllReceivedMotions(allOf(
            withMotionAction(MotionEvent.ACTION_SCROLL),
            // We want to check that the reported motions are in the right direction, but leave the
            // actual magnitude of the motion unspecified to allow scale factors to be tweaked.
            withPositiveAxisValue(MotionEvent.AXIS_HSCROLL),
            withAxisValue(MotionEvent.AXIS_VSCROLL, 0f),
            commonMatcher,
        ))
    }
}
