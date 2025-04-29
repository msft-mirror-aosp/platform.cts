/*
 * Copyright (C) 2020 The Android Open Source Project
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
import android.server.wm.WindowManagerStateHelper
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.compatibility.common.util.PollingCheck
import com.android.compatibility.common.util.ShellUtils
import com.android.compatibility.common.util.UserHelper
import com.android.cts.input.BlockingQueueEventVerifier
import com.android.cts.input.CaptureEventActivity
import com.android.cts.input.inputeventmatchers.withAxisValue
import com.android.cts.input.inputeventmatchers.withMotionAction
import com.android.cts.input.inputeventmatchers.withRawCoords
import com.android.cts.input.inputeventmatchers.withSource
import com.android.cts.input.inputeventmatchers.withToolType
import com.google.common.truth.Truth.assertThat
import org.hamcrest.Matchers.allOf
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private fun getViewCenterOnScreen(v: View): Pair<Int, Int> {
    val location = IntArray(2)
    v.getLocationOnScreen(location)
    val x = location[0] + v.width / 2
    val y = location[1] + v.height / 2
    return Pair(x, y)
}

/**
 * Tests for the 'adb shell input' command.
 */
@MediumTest
@RunWith(AndroidJUnit4::class)
class InputShellCommandTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(CaptureEventActivity::class.java)
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var activity: CaptureEventActivity
    private val displayId = UserHelper().mainDisplayId
    private lateinit var verifier: BlockingQueueEventVerifier

    @Before
    fun setUp() {
        activityRule.getScenario().onActivity {
            activity = it
        }
        PollingCheck.waitFor { activity.hasWindowFocus() }
        WindowManagerStateHelper().waitUntilActivityReadyForInputInjection(
            activity,
            instrumentation,
            "InputShellCommandTest",
            "Waiting for activity"
        )
        verifier = activity.verifier
    }

    /**
     * Check the tool type set by default by "input tap" command
     */
    @Test
    fun testDefaultTapToolType() {
        val (x, y) = getViewCenterOnScreen(activity.window.decorView)

        ShellUtils.runShellCommand("input -d $displayId tap $x $y")
        assertTapToolType(MotionEvent.TOOL_TYPE_FINGER)
    }

    /**
     * Check that the tool type of the injected events changes according to the event source.
     */
    @Test
    fun testTapToolType() {
        val (x, y) = getViewCenterOnScreen(activity.window.decorView)

        ShellUtils.runShellCommand("input touchscreen -d $displayId tap $x $y")
        assertTapToolType(MotionEvent.TOOL_TYPE_FINGER)

        ShellUtils.runShellCommand("input touchpad -d $displayId tap $x $y")
        assertTapToolType(MotionEvent.TOOL_TYPE_FINGER)

        ShellUtils.runShellCommand("input touchnavigation -d $displayId tap $x $y")
        assertTapToolType(MotionEvent.TOOL_TYPE_FINGER)

        ShellUtils.runShellCommand("input stylus -d $displayId tap $x $y")
        assertTapToolType(MotionEvent.TOOL_TYPE_STYLUS)

        ShellUtils.runShellCommand("input mouse -d $displayId tap $x $y")
        assertTapToolType(MotionEvent.TOOL_TYPE_MOUSE)

        ShellUtils.runShellCommand("input trackball -d $displayId tap $x $y")
        assertTapToolType(MotionEvent.TOOL_TYPE_MOUSE)

        ShellUtils.runShellCommand("input joystick -d $displayId tap $x $y")
        assertTapToolType(MotionEvent.TOOL_TYPE_UNKNOWN)
    }

    @Test
    fun testDefaultScroll() {
        ShellUtils.runShellCommand("input -d $displayId scroll")

        verifier.assertReceivedMotion(allOf(
            withSource(InputDevice.SOURCE_ROTARY_ENCODER),
            withMotionAction(MotionEvent.ACTION_SCROLL)),
        )
    }

    @Test
    fun testPointerScroll() {
        val (x, y) = getViewCenterOnScreen(activity.window.decorView)

        ShellUtils.runShellCommand("input mouse -d $displayId scroll $x $y --axis VSCROLL,-1")

        verifier.assertReceivedMotion(allOf(
            withSource(InputDevice.SOURCE_MOUSE),
            withMotionAction(MotionEvent.ACTION_SCROLL),
            withRawCoords(Point(x, y)),
            withAxisValue(MotionEvent.AXIS_VSCROLL, -1f),
        ))
    }

    @Test
    fun testNonPointerScroll() {
        ShellUtils.runShellCommand(
            "input rotaryencoder -d $displayId scroll --axis SCROLL,-8 --axis HSCROLL,2"
        )

        verifier.assertReceivedMotion(allOf(
            withSource(InputDevice.SOURCE_ROTARY_ENCODER),
            withMotionAction(MotionEvent.ACTION_SCROLL),
            withAxisValue(MotionEvent.AXIS_SCROLL, -8f),
            withAxisValue(MotionEvent.AXIS_HSCROLL, 2f),
        ))
    }

    @Test
    fun testInvalidScroll() {
        ShellUtils.runShellCommand("input -d $displayId scroll --axis SCROLL -8")
        ShellUtils.runShellCommand("input -d $displayId scroll --axis scroll,-8")
        ShellUtils.runShellCommand("input -d $displayId scroll --random_option SCROLL,-8")
        ShellUtils.runShellCommand("input -d $displayId scroll --axis X,-8")

        assertThat(activity.getInputEvent()).isNull()
    }

    private fun assertTapToolType(toolType: Int) {
        verifier.assertReceivedMotion(allOf(
            withMotionAction(MotionEvent.ACTION_DOWN),
            withToolType(toolType),
        ))
        verifier.assertReceivedMotion(allOf(
            withMotionAction(MotionEvent.ACTION_UP),
            withToolType(toolType),
        ))
    }
}
