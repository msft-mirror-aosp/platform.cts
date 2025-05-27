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
import com.android.cts.input.UinputMouse
import com.android.cts.input.inputeventmatchers.withActionButton
import com.android.cts.input.inputeventmatchers.withAxisValue
import com.android.cts.input.inputeventmatchers.withButtonState
import com.android.cts.input.inputeventmatchers.withCoords
import com.android.cts.input.inputeventmatchers.withMotionAction
import com.android.cts.input.inputeventmatchers.withRelativeMotion
import com.android.cts.input.inputeventmatchers.withSourceIncluding
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
class MouseCaptureTest {
    companion object {
        val commonMatcher = allOf(
            withSourceIncluding(InputDevice.SOURCE_MOUSE_RELATIVE),
            withToolType(MotionEvent.TOOL_TYPE_MOUSE),
        )
        val withoutMotionMatcher = allOf(
            withCoords(Point(0, 0)),
            withRelativeMotion(0, 0),
        )
        val noopMoveMatcher = allOf(
            withMotionAction(MotionEvent.ACTION_MOVE),
            withCoords(Point(0, 0)),
            withRelativeMotion(0, 0),
        )
    }

    private lateinit var mouse: UinputMouse
    private lateinit var verifier: BlockingQueueEventVerifier
    private lateinit var activity: PointerCaptureActivity

    @get:Rule
    val testName = TestName()
    @get:Rule
    val activityScenarioRule = ActivityScenarioRule(PointerCaptureActivity::class.java)

    @Before
    fun setUp() {
        activityScenarioRule.scenario.onActivity { activity = it }

        mouse = UinputMouse(InstrumentationRegistry.getInstrumentation())
        verifier = activity.verifier

        PollingCheck.waitFor { activity.hasWindowFocus() }
        activity.ensurePointerCaptured()
        // TODO(b/411389468): enable InputVerifier to check the captured pointer events produced.
    }

    @After
    fun tearDown() {
        if (this::mouse.isInitialized) {
            mouse.close()
        }
    }

    @Test
    fun testMotionReportedCorrectly() {
        mouse.move(-4, 2)
        mouse.sync()

        verifier.assertReceivedMotion(allOf(
            withMotionAction(MotionEvent.ACTION_MOVE),
            withCoords(Point(-4, 2)),
            withRelativeMotion(-4, 2),
            commonMatcher,
        ))

        mouse.move(0, 10)
        mouse.sync()

        verifier.assertReceivedMotion(allOf(
            withMotionAction(MotionEvent.ACTION_MOVE),
            withCoords(Point(0, 10)),
            withRelativeMotion(0, 10),
            commonMatcher,
        ))
    }

    @Test
    fun testButtonEventsAndStates() {
        val withMotionMatcher = allOf(
            withCoords(Point(4, 4)),
            withRelativeMotion(4, 4),
        )

        mouse.pressButton(BTN_LEFT)
        mouse.sync()

        verifier.assertReceivedMotion(allOf(
            withMotionAction(MotionEvent.ACTION_DOWN),
            withActionButton(0),
            withButtonState(MotionEvent.BUTTON_PRIMARY),
            withoutMotionMatcher,
            commonMatcher,
        ))

        verifier.assertReceivedMotion(allOf(
            withMotionAction(MotionEvent.ACTION_BUTTON_PRESS),
            withActionButton(MotionEvent.BUTTON_PRIMARY),
            withButtonState(MotionEvent.BUTTON_PRIMARY),
            withoutMotionMatcher,
            commonMatcher,
        ))

        mouse.move(4, 4)
        mouse.sync()

        verifier.assertReceivedMotion(allOf(
            withMotionAction(MotionEvent.ACTION_MOVE),
            withActionButton(0),
            withButtonState(MotionEvent.BUTTON_PRIMARY),
            withMotionMatcher,
            commonMatcher,
        ))

        mouse.pressButton(BTN_RIGHT)
        mouse.sync()

        verifier.assertReceivedMotion(allOf(
            withMotionAction(MotionEvent.ACTION_BUTTON_PRESS),
            withActionButton(MotionEvent.BUTTON_SECONDARY),
            withButtonState(MotionEvent.BUTTON_PRIMARY or MotionEvent.BUTTON_SECONDARY),
            withoutMotionMatcher,
            commonMatcher,
        ))

        mouse.move(4, 4)
        mouse.sync()

        verifier.assertReceivedMotion(allOf(
            withMotionAction(MotionEvent.ACTION_MOVE),
            withActionButton(0),
            withButtonState(MotionEvent.BUTTON_PRIMARY or MotionEvent.BUTTON_SECONDARY),
            withMotionMatcher,
            commonMatcher,
        ))

        mouse.releaseButton(BTN_LEFT)
        mouse.sync()

        verifier.assertReceivedMotion(allOf(
            withMotionAction(MotionEvent.ACTION_BUTTON_RELEASE),
            withActionButton(MotionEvent.BUTTON_PRIMARY),
            withButtonState(MotionEvent.BUTTON_SECONDARY),
            withoutMotionMatcher,
            commonMatcher,
        ))

        mouse.move(4, 4)
        mouse.sync()

        verifier.assertReceivedMotion(allOf(
            withMotionAction(MotionEvent.ACTION_MOVE),
            withActionButton(0),
            withButtonState(MotionEvent.BUTTON_SECONDARY),
            withMotionMatcher,
        ))

        mouse.releaseButton(BTN_RIGHT)
        mouse.sync()

        verifier.assertReceivedMotion(allOf(
            withMotionAction(MotionEvent.ACTION_BUTTON_RELEASE),
            withActionButton(MotionEvent.BUTTON_SECONDARY),
            withButtonState(0),
            withoutMotionMatcher,
        ))

        verifier.assertReceivedMotion(allOf(
            withMotionAction(MotionEvent.ACTION_UP),
            withActionButton(0),
            withButtonState(0),
            withoutMotionMatcher,
            commonMatcher,
        ))

        mouse.move(4, 4)
        mouse.sync()

        // Because we don't have a concept of hovering during pointer capture, the action should
        // still be MOVE even though no buttons are down.
        verifier.assertReceivedMotion(allOf(
            withMotionAction(MotionEvent.ACTION_MOVE),
            withActionButton(0),
            withButtonState(0),
            withMotionMatcher,
            commonMatcher,
        ))
    }

    /** Regression test for b/405820964. */
    @Test
    fun testMoveAndButtonChangeInSameFrameReportedAsSeparateEvents() {
        mouse.move(10, 2)
        mouse.pressButton(BTN_LEFT)
        mouse.sync()

        verifier.assertReceivedMotion(allOf(
            withMotionAction(MotionEvent.ACTION_MOVE),
            withCoords(Point(10, 2)),
            withRelativeMotion(10, 2),
            commonMatcher,
        ))

        verifier.assertReceivedMotion(allOf(
            withMotionAction(MotionEvent.ACTION_DOWN),
            withActionButton(0),
            withButtonState(MotionEvent.BUTTON_PRIMARY),
            withoutMotionMatcher,
            commonMatcher,
        ))

        verifier.assertReceivedMotion(allOf(
            withMotionAction(MotionEvent.ACTION_BUTTON_PRESS),
            withActionButton(MotionEvent.BUTTON_PRIMARY),
            withButtonState(MotionEvent.BUTTON_PRIMARY),
            withoutMotionMatcher,
            commonMatcher,
        ))

        mouse.move(-2, -5)
        mouse.releaseButton(BTN_LEFT)
        mouse.sync()

        verifier.assertReceivedMotion(allOf(
            withMotionAction(MotionEvent.ACTION_MOVE),
            withCoords(Point(-2, -5)),
            withRelativeMotion(-2, -5),
            withButtonState(MotionEvent.BUTTON_PRIMARY),
            commonMatcher,
        ))

        verifier.assertReceivedMotion(allOf(
            withMotionAction(MotionEvent.ACTION_BUTTON_RELEASE),
            withActionButton(MotionEvent.BUTTON_PRIMARY),
            withButtonState(0),
            withoutMotionMatcher,
            commonMatcher,
        ))

        verifier.assertReceivedMotion(allOf(
            withMotionAction(MotionEvent.ACTION_UP),
            withActionButton(0),
            withButtonState(0),
            withoutMotionMatcher,
            commonMatcher,
        ))
    }

    @Test
    fun testLowResolutionVerticalScrolling() {
        mouse.scrollVertically(1f)
        mouse.sync()

        verifier.acceptOptionalMotion(noopMoveMatcher)
        verifier.assertReceivedMotion(allOf(
            withMotionAction(MotionEvent.ACTION_SCROLL),
            withAxisValue(MotionEvent.AXIS_VSCROLL, 1f),
            withAxisValue(MotionEvent.AXIS_HSCROLL, 0f),
            commonMatcher,
        ))

        mouse.scrollVertically(-1f)
        mouse.sync()

        verifier.acceptOptionalMotion(noopMoveMatcher)
        verifier.assertReceivedMotion(allOf(
            withMotionAction(MotionEvent.ACTION_SCROLL),
            withAxisValue(MotionEvent.AXIS_VSCROLL, -1f),
            withAxisValue(MotionEvent.AXIS_HSCROLL, 0f),
            commonMatcher,
        ))
    }

    @Test
    fun testLowResolutionHorizontalScrolling() {
        mouse.scrollHorizontally(1f)
        mouse.sync()

        verifier.acceptOptionalMotion(noopMoveMatcher)
        verifier.assertReceivedMotion(allOf(
            withMotionAction(MotionEvent.ACTION_SCROLL),
            withAxisValue(MotionEvent.AXIS_VSCROLL, 0f),
            withAxisValue(MotionEvent.AXIS_HSCROLL, 1f),
            commonMatcher,
        ))

        mouse.scrollHorizontally(-1f)
        mouse.sync()

        verifier.acceptOptionalMotion(noopMoveMatcher)
        verifier.assertReceivedMotion(allOf(
            withMotionAction(MotionEvent.ACTION_SCROLL),
            withAxisValue(MotionEvent.AXIS_VSCROLL, 0f),
            withAxisValue(MotionEvent.AXIS_HSCROLL, -1f),
            commonMatcher,
        ))
    }

    @Test
    fun testHighResolutionVerticalScrolling() {
        mouse.scrollVertically(0.5f)
        mouse.sync()

        verifier.acceptOptionalMotion(noopMoveMatcher)
        verifier.assertReceivedMotion(allOf(
            withMotionAction(MotionEvent.ACTION_SCROLL),
            withAxisValue(MotionEvent.AXIS_VSCROLL, 0.5f),
            withAxisValue(MotionEvent.AXIS_HSCROLL, 0f),
            commonMatcher,
        ))

        mouse.scrollVertically(-1.4f)
        mouse.sync()

        verifier.acceptOptionalMotion(noopMoveMatcher)
        verifier.assertReceivedMotion(allOf(
            withMotionAction(MotionEvent.ACTION_SCROLL),
            withAxisValue(MotionEvent.AXIS_VSCROLL, -1.4f),
            withAxisValue(MotionEvent.AXIS_HSCROLL, 0f),
            commonMatcher,
        ))
    }

    @Test
    fun testHighResolutionHorizontalScrolling() {
        mouse.scrollHorizontally(0.5f)
        mouse.sync()

        verifier.acceptOptionalMotion(noopMoveMatcher)
        verifier.assertReceivedMotion(allOf(
            withMotionAction(MotionEvent.ACTION_SCROLL),
            withAxisValue(MotionEvent.AXIS_VSCROLL, 0f),
            withAxisValue(MotionEvent.AXIS_HSCROLL, 0.5f),
            commonMatcher,
        ))

        mouse.scrollHorizontally(-1.4f)
        mouse.sync()

        verifier.acceptOptionalMotion(noopMoveMatcher)
        verifier.assertReceivedMotion(allOf(
            withMotionAction(MotionEvent.ACTION_SCROLL),
            withAxisValue(MotionEvent.AXIS_VSCROLL, 0f),
            withAxisValue(MotionEvent.AXIS_HSCROLL, -1.4f),
            commonMatcher,
        ))
    }
}
