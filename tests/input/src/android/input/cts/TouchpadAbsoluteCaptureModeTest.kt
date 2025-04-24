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
import com.android.cts.input.EvdevInputEventCodes.Companion.BTN_TOOL_FINGER
import com.android.cts.input.UinputTouchPad
import com.android.cts.input.inputeventmatchers.withCoords
import com.android.cts.input.inputeventmatchers.withMotionAction
import com.android.cts.input.inputeventmatchers.withPointerCount
import com.android.cts.input.inputeventmatchers.withRelativeMotion
import com.android.cts.input.inputeventmatchers.withSource
import com.android.cts.input.inputeventmatchers.withToolType
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
    fun testOneFingerMotionReportedCorrectly() {
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

    // TODO(b/411389468): add more test cases from CapturedTouchpadEventConverterTest.
}
