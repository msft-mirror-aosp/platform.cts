/*
 * Copyright (C) 2015 The Android Open Source Project
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
package android.view.cts

import android.Manifest
import android.graphics.Point
import android.graphics.PointF
import android.os.Handler
import android.os.Looper
import android.platform.test.annotations.AppModeSdkSandbox
import android.platform.test.annotations.RequiresFlagsDisabled
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.view.InputDevice
import android.view.InputDevice.SOURCE_MOUSE
import android.view.MotionEvent
import android.view.MotionEvent.ACTION_DOWN
import android.view.MotionEvent.ACTION_POINTER_DOWN
import android.view.MotionEvent.AXIS_GESTURE_PINCH_SCALE_FACTOR
import android.view.MotionEvent.TOOL_TYPE_FINGER
import android.view.ScaleGestureDetector
import android.view.ScaleGestureDetector.OnScaleGestureListener
import android.view.ViewConfiguration
import androidx.test.annotation.UiThreadTest
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import com.android.compatibility.common.util.AdoptShellPermissionsRule
import com.android.cts.input.MotionEventBuilder
import com.android.cts.input.PointerBuilder
import com.android.cts.input.ScaleEventType
import com.android.cts.input.ScaleGestureDetectorActivity
import com.android.cts.input.TestScaleGestureListener
import com.android.cts.input.UinputTouchScreen
import com.android.cts.input.VirtualDisplayActivityScenario
import com.android.cts.input.hasCurrentSpan
import com.android.cts.input.hasCurrentSpanXY
import com.android.cts.input.hasEventTime
import com.android.cts.input.hasFocus
import com.android.cts.input.hasPreviousSpan
import com.android.cts.input.hasPreviousSpanXY
import com.android.cts.input.hasScaleFactor
import com.android.cts.input.hasTimeDelta
import com.android.cts.input.hasType
import com.android.cts.input.isInProgress
import com.android.hardware.input.Flags.FLAG_SCALE_GESTURE_DETECTOR_USE_EVENTS_CLASSIFICATION
import com.google.common.truth.Truth.assertThat
import java.time.Duration.ofMillis
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.sqrt
import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.greaterThan
import org.hamcrest.Matchers.lessThan
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith

@MediumTest
@RunWith(AndroidJUnit4::class)
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
class ScaleGestureDetectorTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private var spanSlop: Int = 0
    private var spanSlopRadius: Int = 0
    private var minSpan: Int = 0
    private val doubleTapMinTimeMillis: Long = 50L

    @get:Rule(order = 0)
    var mAdoptShellPermissionsRule: AdoptShellPermissionsRule =
        AdoptShellPermissionsRule(
            InstrumentationRegistry.getInstrumentation().getUiAutomation(),
            Manifest.permission.START_ACTIVITIES_FROM_SDK_SANDBOX,
        )

    @get:Rule(order = 1)
    val virtualDisplayRule =
        VirtualDisplayActivityScenario.Rule<ScaleGestureDetectorActivity>(TestName())

    @get:Rule
    val mCheckFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @Before
    fun setup() {
        minSpan = ViewConfiguration.get(virtualDisplayRule.activity).scaledMinimumScalingSpan
        spanSlopRadius = ViewConfiguration.get(virtualDisplayRule.activity).scaledTouchSlop
        spanSlop = spanSlopRadius * 2
    }

    @UiThreadTest
    @Test
    fun constructor_defaultValues() {
        val detector =
            ScaleGestureDetector(
                this.virtualDisplayRule.activity,
                ScaleGestureDetector.SimpleOnScaleGestureListener(),
            )

        assertThat(detector.isInProgress).isFalse()
        assertThat(detector.isQuickScaleEnabled).isTrue()
        assertThat(detector.isStylusScaleEnabled).isTrue()
    }

    @Test
    fun constructor_withHandler_defaultValues() {
        val detector =
            ScaleGestureDetector(
                this.virtualDisplayRule.activity,
                ScaleGestureDetector.SimpleOnScaleGestureListener(),
                Handler(Looper.getMainLooper()),
            )

        assertThat(detector.isInProgress).isFalse()
        assertThat(detector.isQuickScaleEnabled).isTrue()
        assertThat(detector.isStylusScaleEnabled).isTrue()
    }

    @Test
    fun twoFingers_spanGreaterThanMinSpan_initialMoveGreaterThanSpanSlop_startsStream() {
        val listener = TestScaleGestureListener()
        val detector = createDetector(listener)
        val pointer0 = PointF(100f, 200f)
        val pointer1Down = pointer0.withOffset(minSpan.toFloat(), 0f)
        val pointer1Move = pointer1Down.withOffset(spanSlop.toFloat(), 1f)
        detector.onTouchEvent(down(pointer0))
        detector.onTouchEvent(pointerDown(index = 1, listOf(pointer0, pointer1Down)))

        detector.isQuickScaleEnabled

        assertThat(listener.events).isEmpty()
        detector.onTouchEvent(move(listOf(pointer0, pointer1Move)))

        assertThat(
            listener.pollEvent(),
            allOf(
                hasType(ScaleEventType.SCALE_BEGIN),
                isInProgress(false),
                hasFocus(midpoint(pointer0, pointer1Move)),
                hasScaleFactor(1f),
                hasCurrentSpan(distance(pointer0, pointer1Move).hypot()),
                hasCurrentSpanXY(distance(pointer0, pointer1Move)),
                hasPreviousSpan(distance(pointer0, pointer1Move).hypot()),
                hasPreviousSpanXY(distance(pointer0, pointer1Move)),
            ),
        )
        assertThat(
            listener.pollEvent(),
            allOf(
                hasType(ScaleEventType.SCALE),
                isInProgress(true),
                hasFocus(midpoint(pointer0, pointer1Move)),
                hasScaleFactor(1f),
                hasCurrentSpan(distance(pointer0, pointer1Move).hypot()),
                hasCurrentSpanXY(distance(pointer0, pointer1Move)),
                hasPreviousSpan(distance(pointer0, pointer1Move).hypot()),
                hasPreviousSpanXY(distance(pointer0, pointer1Move)),
            ),
        )
        assertThat(listener.events).isEmpty()
        assertThat(detector.isInProgress).isTrue()
    }

    @Test
    fun twoFingers_pinchIn_setsPreviousAndCurrentFocusAndSpan_scaleFactorIsLessThanOne() {
        val listener = TestScaleGestureListener()
        val detector = createDetector(listener)
        val pointer0 = PointF(100f, 200f)
        val pointer1Down = pointer0.withOffset(minSpan.toFloat(), 0f)
        val pointer1InitialMove = pointer1Down.withOffset(5f * spanSlop, 4f * spanSlop)
        val pointer1FinalMove = pointer1InitialMove.withOffset(-2f * spanSlop, -3f * spanSlop)
        detector.onTouchEvent(down(pointer0))
        detector.onTouchEvent(pointerDown(index = 1, listOf(pointer0, pointer1Down)))
        detector.onTouchEvent(move(listOf(pointer0, pointer1InitialMove)))
        assertThat(listener.pollEvent(), allOf(hasType(ScaleEventType.SCALE_BEGIN)))
        assertThat(listener.pollEvent(), allOf(hasType(ScaleEventType.SCALE)))
        assertThat(listener.events).isEmpty()
        assertThat(detector.isInProgress).isTrue()

        detector.onTouchEvent(move(listOf(pointer0, pointer1FinalMove)))

        val initialSpan = distance(pointer0, pointer1InitialMove)
        val finalSpan = distance(pointer0, pointer1FinalMove)
        assertThat(
            listener.pollEvent(),
            allOf(
                hasType(ScaleEventType.SCALE),
                isInProgress(true),
                hasFocus(midpoint(pointer0, pointer1FinalMove)),
                hasScaleFactor(finalSpan.hypot() / initialSpan.hypot()),
                hasScaleFactor(lessThan(1f)),
                hasCurrentSpan(finalSpan.hypot()),
                hasCurrentSpanXY(finalSpan),
                hasPreviousSpan(initialSpan.hypot()),
                hasPreviousSpanXY(initialSpan),
            ),
        )
        assertThat(detector.isInProgress).isTrue()
    }

    @Test
    fun twoFingers_pinchOut_setsPreviousAndCurrentFocusAndSpan_scaleFactorIsGreaterThanOne() {
        val listener = TestScaleGestureListener()
        val detector = createDetector(listener)
        val pointer0 = PointF(100f, 200f)
        val pointer1Down = pointer0.withOffset(minSpan.toFloat(), 0f)
        val pointer1InitialMove = pointer1Down.withOffset(5f * spanSlop, 4f * spanSlop)
        val pointer1FinalMove = pointer1InitialMove.withOffset(2f * spanSlop, 3f * spanSlop)
        detector.onTouchEvent(down(pointer0))
        detector.onTouchEvent(pointerDown(index = 1, listOf(pointer0, pointer1Down)))
        detector.onTouchEvent(move(listOf(pointer0, pointer1InitialMove)))
        assertThat(listener.pollEvent(), allOf(hasType(ScaleEventType.SCALE_BEGIN)))
        assertThat(listener.pollEvent(), allOf(hasType(ScaleEventType.SCALE)))
        assertThat(listener.events).isEmpty()
        assertThat(detector.isInProgress).isTrue()

        detector.onTouchEvent(move(listOf(pointer0, pointer1FinalMove)))

        val initialSpan = distance(pointer0, pointer1InitialMove)
        val finalSpan = distance(pointer0, pointer1FinalMove)
        assertThat(
            listener.pollEvent(),
            allOf(
                hasType(ScaleEventType.SCALE),
                isInProgress(true),
                hasFocus(midpoint(pointer0, pointer1FinalMove)),
                hasScaleFactor(finalSpan.hypot() / initialSpan.hypot()),
                hasScaleFactor(greaterThan(1f)),
                hasCurrentSpan(finalSpan.hypot()),
                hasCurrentSpanXY(finalSpan),
                hasPreviousSpan(initialSpan.hypot()),
                hasPreviousSpanXY(initialSpan),
            ),
        )
        assertThat(listener.events).isEmpty()
        assertThat(detector.isInProgress).isTrue()
    }

    @Test
    fun twoFingers_multipleMoves_updatesPreviousFocusAndSpan() {
        val listener = TestScaleGestureListener()
        val detector = createDetector(listener)
        val pointer0 = PointF(100f, 200f)
        val pointer1Down = pointer0.withOffset(minSpan.toFloat(), 0f)
        val pointer1InitialMove = pointer1Down.withOffset(10f * spanSlop, 10f * spanSlop)
        val pointer1IntermediateMove = pointer1InitialMove.withOffset(1f * spanSlop, 2f * spanSlop)
        val pointer1FinalMove = pointer1IntermediateMove.withOffset(3f * spanSlop, 4f * spanSlop)
        detector.onTouchEvent(down(pointer0))
        detector.onTouchEvent(pointerDown(index = 1, listOf(pointer0, pointer1Down)))
        detector.onTouchEvent(move(listOf(pointer0, pointer1InitialMove)))
        detector.onTouchEvent(move(listOf(pointer0, pointer1IntermediateMove)))
        assertThat(listener.pollEvent(), hasType(ScaleEventType.SCALE_BEGIN))
        assertThat(listener.pollEvent(), hasType(ScaleEventType.SCALE))
        assertThat(listener.pollEvent(), hasType(ScaleEventType.SCALE))
        assertThat(listener.events).isEmpty()
        assertThat(detector.isInProgress).isTrue()

        detector.onTouchEvent(move(listOf(pointer0, pointer1FinalMove)))

        val intermediateSpan = distance(pointer0, pointer1IntermediateMove)
        val finalSpan = distance(pointer0, pointer1FinalMove)
        assertThat(
            listener.pollEvent(),
            allOf(
                hasType(ScaleEventType.SCALE),
                isInProgress(true),
                hasFocus(midpoint(pointer0, pointer1FinalMove)),
                hasScaleFactor(finalSpan.hypot() / intermediateSpan.hypot()),
                hasCurrentSpan(finalSpan.hypot()),
                hasCurrentSpanXY(finalSpan),
                hasPreviousSpan(intermediateSpan.hypot()),
                hasPreviousSpanXY(intermediateSpan),
            ),
        )
        assertThat(detector.isInProgress).isTrue()
    }

    @Test
    fun twoFingers_eventTime_and_timeDelta_areCorrect() {
        val listener = TestScaleGestureListener()
        val detector = createDetector(listener)
        val pointer0 = PointF(100f, 200f)
        val pointer1Down = pointer0.withOffset(minSpan.toFloat(), 0f)
        val pointer1Move = pointer1Down.withOffset(spanSlop.toFloat(), 1f)
        detector.onTouchEvent(down(pointer0, eventTime = 100))
        detector.onTouchEvent(
            pointerDown(index = 1, listOf(pointer0, pointer1Down), eventTime = 113)
        )
        assertThat(listener.events).isEmpty()

        detector.onTouchEvent(move(listOf(pointer0, pointer1Move), eventTime = 137))

        assertThat(
            listener.pollEvent(),
            allOf(hasType(ScaleEventType.SCALE_BEGIN), hasEventTime(137), hasTimeDelta(0)),
        )
        assertThat(
            listener.pollEvent(),
            allOf(hasType(ScaleEventType.SCALE), hasEventTime(137), hasTimeDelta(0)),
        )
        assertThat(listener.events).isEmpty()

        detector.onTouchEvent(move(listOf(pointer0, pointer1Move), eventTime = 239))

        assertThat(
            listener.pollEvent(),
            allOf(hasType(ScaleEventType.SCALE), hasEventTime(239), hasTimeDelta(102)),
        )
        assertThat(listener.events).isEmpty()

        detector.onTouchEvent(pointerUp(index = 1, listOf(pointer0, pointer1Move), eventTime = 250))

        assertThat(
            listener.pollEvent(),
            allOf(hasType(ScaleEventType.SCALE_END), hasEventTime(250), hasTimeDelta(11)),
        )
        assertThat(listener.events).isEmpty()
    }

    @Test
    fun twoFingers_inProgress_liftPointer_endsStream() {
        val listener = TestScaleGestureListener()
        val detector = createDetector(listener)
        val pointer0 = PointF(100f, 200f)
        val pointer1Down = pointer0.withOffset(minSpan.toFloat(), 0f)
        val pointer1Move = pointer1Down.withOffset(spanSlop.toFloat(), 1f)
        detector.onTouchEvent(down(pointer0))
        detector.onTouchEvent(pointerDown(index = 1, listOf(pointer0, pointer1Down)))
        detector.onTouchEvent(move(listOf(pointer0, pointer1Move)))
        assertThat(listener.events).isNotEmpty()
        assertThat(detector.isInProgress).isTrue()
        listener.events.clear()

        detector.onTouchEvent(pointerUp(index = 1, listOf(pointer0, pointer1Move)))

        assertThat(
            listener.pollEvent(),
            allOf(
                hasType(ScaleEventType.SCALE_END),
                isInProgress(true),
                hasFocus(pointer0),
                hasScaleFactor(1.0f),
                hasCurrentSpan(distance(pointer0, pointer1Move).hypot()),
                hasCurrentSpanXY(distance(pointer0, pointer1Move)),
                hasPreviousSpan(distance(pointer0, pointer1Move).hypot()),
                hasPreviousSpanXY(distance(pointer0, pointer1Move)),
            ),
        )
        assertThat(listener.events).isEmpty()
        assertThat(detector.isInProgress).isFalse()
    }

    @Test
    fun twoFingers_spanLessThanMinSpan_doesNotStartStream() {
        val listener = TestScaleGestureListener()
        val detector = createDetector(listener)
        val pointer0 = PointF(100f, 200f)
        val pointer1Down = pointer0.withOffset(0f, minSpan - spanSlop - 2f)
        val pointer1Move = pointer0.withOffset(0f, minSpan - 1f)
        detector.onTouchEvent(down(pointer0))
        detector.onTouchEvent(pointerDown(index = 1, listOf(pointer0, pointer1Down)))
        assertThat(listener.events).isEmpty()

        // Move by > spanSlop however making currentSpan < minSpan.
        detector.onTouchEvent(move(listOf(pointer0, pointer1Move)))

        assertThat(listener.events).isEmpty()
        assertThat(detector.isInProgress).isFalse()
    }

    @Test
    fun twoFingers_inProgress_spanBecomesLessThanMinSpan_endsStream() {
        val listener = TestScaleGestureListener()
        val detector = createDetector(listener)
        val pointer0 = PointF(100f, 200f)
        val pointer1Down = pointer0.withOffset(0f, minSpan.toFloat())
        val pointer1InitialMove = pointer0.withOffset(0f, minSpan + spanSlop + 1f)
        val pointer1FinalMove = pointer0.withOffset(0f, minSpan - 1f)
        detector.onTouchEvent(down(pointer0))
        detector.onTouchEvent(pointerDown(index = 1, listOf(pointer0, pointer1Down)))
        detector.onTouchEvent(move(listOf(pointer0, pointer1InitialMove)))
        assertThat(listener.pollEvent(), allOf(hasType(ScaleEventType.SCALE_BEGIN)))
        assertThat(listener.pollEvent(), allOf(hasType(ScaleEventType.SCALE)))
        assertThat(listener.events).isEmpty()
        assertThat(detector.isInProgress).isTrue()

        detector.onTouchEvent(move(listOf(pointer0, pointer1FinalMove)))

        assertThat(listener.pollEvent(), hasType(ScaleEventType.SCALE_END))
        assertThat(detector.isInProgress).isFalse()
    }

    @Test
    fun twoFingers_onScaleBeginReturnsFalse_doesNotCallOnScale_inProgressIsFalse() {
        val listener = TestScaleGestureListener(onScaleBeginReturnValue = false)
        val detector = createDetector(listener)
        val pointer0 = PointF(100f, 200f)
        val pointer1Down = pointer0.withOffset(4f * minSpan, 0f)
        val pointer1InitialMove = pointer1Down.withOffset(5f * spanSlop, 7f * spanSlop)
        val pointer1FinalMove = pointer1InitialMove.withOffset(10f * spanSlop, 10f * spanSlop)
        detector.onTouchEvent(down(pointer0))
        detector.onTouchEvent(pointerDown(index = 1, listOf(pointer0, pointer1Down)))
        assertThat(listener.events).isEmpty()
        detector.onTouchEvent(move(listOf(pointer0, pointer1InitialMove)))
        detector.onTouchEvent(pointerUp(index = 1, listOf(pointer0, pointer1FinalMove)))

        assertThat(listener.pollEvent(), hasType(ScaleEventType.SCALE_BEGIN))
        assertThat(listener.events).isEmpty()
        assertThat(detector.isInProgress).isFalse()
    }

    @Test
    fun twoFingers_onScaleReturnsFalse_doesNotUpdatePreviousFocusAndSpan() {
        val listener = TestScaleGestureListener(onScaleReturnValue = false)
        val detector = createDetector(listener)
        val pointer0 = PointF(100f, 200f)
        val pointer1Down = pointer0.withOffset(minSpan.toFloat(), 0f)
        val pointer1InitialMove = pointer1Down.withOffset(10f * spanSlop, 10f * spanSlop)
        val pointer1IntermediateMove = pointer1InitialMove.withOffset(1f * spanSlop, 2f * spanSlop)
        val pointer1FinalMove = pointer1IntermediateMove.withOffset(3f * spanSlop, 4f * spanSlop)
        detector.onTouchEvent(down(pointer0))
        detector.onTouchEvent(pointerDown(index = 1, listOf(pointer0, pointer1Down)))
        detector.onTouchEvent(move(listOf(pointer0, pointer1InitialMove)))
        detector.onTouchEvent(move(listOf(pointer0, pointer1IntermediateMove)))
        assertThat(listener.pollEvent(), hasType(ScaleEventType.SCALE_BEGIN))
        assertThat(listener.pollEvent(), hasType(ScaleEventType.SCALE))
        assertThat(listener.pollEvent(), hasType(ScaleEventType.SCALE))
        assertThat(listener.events).isEmpty()
        assertThat(detector.isInProgress).isTrue()

        detector.onTouchEvent(move(listOf(pointer0, pointer1FinalMove)))

        val initialSpan = distance(pointer0, pointer1InitialMove)
        val finalSpan = distance(pointer0, pointer1FinalMove)
        assertThat(
            listener.pollEvent(),
            allOf(
                hasType(ScaleEventType.SCALE),
                isInProgress(true),
                hasFocus(midpoint(pointer0, pointer1FinalMove)),
                hasScaleFactor(finalSpan.hypot() / initialSpan.hypot()),
                hasCurrentSpan(finalSpan.hypot()),
                hasCurrentSpanXY(finalSpan),
                hasPreviousSpan(initialSpan.hypot()),
                hasPreviousSpanXY(initialSpan),
            ),
        )
        assertThat(listener.events).isEmpty()
        assertThat(detector.isInProgress).isTrue()
    }

    @Test
    fun threeFingers_calculatesFocusAndSpan() {
        val listener = TestScaleGestureListener()
        val detector = createDetector(listener)
        val radius = minSpan.toFloat()
        val triangleCenter = PointF(500f, 500f)
        val pointer0Down = triangleCenter.withOffset(0f, -2 * radius)
        val pointer0Move = triangleCenter.withOffset(0f, -radius)
        val pointer1 =
            triangleCenter.withOffset(
                -cos(PI / 6).toFloat() * radius,
                cos(PI / 3).toFloat() * radius,
            )
        val pointer2 =
            triangleCenter.withOffset(
                cos(PI / 6).toFloat() * radius,
                cos(PI / 3).toFloat() * radius,
            )
        detector.onTouchEvent(down(pointer0Down))
        detector.onTouchEvent(pointerDown(index = 1, listOf(pointer0Down, pointer1)))
        detector.onTouchEvent(pointerDown(index = 2, listOf(pointer0Down, pointer1, pointer2)))

        detector.onTouchEvent(move(listOf(pointer0Move, pointer1, pointer2)))

        val expectedSpanXY = PointF(2 * sqrt(3f) * radius / 3, 2 * 2 * radius / 3)
        assertThat(
            listener.pollEvent(),
            allOf(
                hasType(ScaleEventType.SCALE_BEGIN),
                isInProgress(false),
                hasFocus(triangleCenter),
                hasScaleFactor(1.0f),
                hasCurrentSpan(expectedSpanXY.hypot()),
                hasCurrentSpanXY(expectedSpanXY),
                hasPreviousSpan(expectedSpanXY.hypot()),
                hasPreviousSpanXY(expectedSpanXY),
            ),
        )
        assertThat(
            listener.pollEvent(),
            allOf(
                hasType(ScaleEventType.SCALE),
                isInProgress(true),
                hasFocus(triangleCenter),
                hasScaleFactor(1.0f),
                hasCurrentSpan(expectedSpanXY.hypot()),
                hasCurrentSpanXY(expectedSpanXY),
                hasPreviousSpan(expectedSpanXY.hypot()),
                hasPreviousSpanXY(expectedSpanXY),
            ),
        )
        assertThat(listener.events).isEmpty()
        assertThat(detector.isInProgress).isTrue()
    }

    @Test
    fun threeFingers_liftPointer_spanRemainsGreaterThanMinSpan_restartsStream() {
        val listener = TestScaleGestureListener()
        val detector = createDetector(listener)
        val pointer0 = PointF(100f, 200f)
        val pointer1 = pointer0.withOffset(4f * minSpan, 0f)
        val pointer2 = pointer0.withOffset(0f, 4f * minSpan)
        detector.onTouchEvent(down(pointer0))
        detector.onTouchEvent(pointerDown(index = 1, listOf(pointer0, pointer1)))
        detector.onTouchEvent(pointerDown(index = 2, listOf(pointer0, pointer1, pointer2)))
        detector.onTouchEvent(
            move(
                listOf(
                    pointer0,
                    pointer1,
                    pointer2.withOffset(-4f * spanSlop.toFloat(), -4f * spanSlop.toFloat()),
                )
            )
        )
        assertThat(listener.events).isNotEmpty()
        listener.events.clear()

        detector.onTouchEvent(pointerUp(index = 2, listOf(pointer0, pointer1, pointer2)))

        assertThat(listener.pollEvent(), hasType(ScaleEventType.SCALE_END))
        assertThat(
            listener.pollEvent(),
            allOf(
                hasType(ScaleEventType.SCALE_BEGIN),
                isInProgress(false),
                hasFocus(midpoint(pointer0, pointer1)),
                hasScaleFactor(1.0f),
                hasCurrentSpan(distance(pointer0, pointer1).hypot()),
                hasCurrentSpanXY(distance(pointer0, pointer1)),
                hasPreviousSpan(distance(pointer0, pointer1).hypot()),
                hasPreviousSpanXY(distance(pointer0, pointer1)),
            ),
        )
        assertThat(listener.events).isEmpty()
        assertThat(detector.isInProgress).isTrue()
    }

    @Test
    fun threeFingers_liftPointer_spanBecomesLessThanMinSpan_endsStream() {
        val listener = TestScaleGestureListener()
        val detector = createDetector(listener)
        val pointer0 = PointF(100f, 200f)
        val pointer1 = pointer0.withOffset(minSpan - 1f, 0f)
        val pointer2 = pointer0.withOffset(4f * minSpan, 4f * minSpan)
        detector.onTouchEvent(down(pointer0))
        detector.onTouchEvent(pointerDown(index = 1, listOf(pointer0, pointer1)))
        detector.onTouchEvent(pointerDown(index = 2, listOf(pointer0, pointer1, pointer2)))
        detector.onTouchEvent(
            move(
                listOf(
                    pointer0,
                    pointer1,
                    pointer2.withOffset(-4f * spanSlop.toFloat(), -4f * spanSlop.toFloat()),
                )
            )
        )
        assertThat(listener.events).isNotEmpty()
        listener.events.clear()

        detector.onTouchEvent(pointerUp(index = 2, listOf(pointer0, pointer1, pointer2)))

        assertThat(listener.pollEvent(), hasType(ScaleEventType.SCALE_END))
        assertThat(listener.events).isEmpty()
        assertThat(detector.isInProgress).isFalse()
    }

    @Test
    fun quickScale_spanGreaterThanSpanSlop_startsStream() {
        val listener = TestScaleGestureListener()
        val detector = createDetector(listener)
        val move = PointF(345f, spanSlopRadius + 1f)
        val pointerDown = PointF(100f, 200f)
        val pointerMove = pointerDown.withOffset(move)

        detector.onTouchEvent(down(pointerDown))
        detector.onTouchEvent(up(pointerDown))
        detector.onTouchEvent(down(pointerDown, eventTime = doubleTapMinTimeMillis))
        detector.onTouchEvent(move(listOf(pointerMove)))
        assertThat(
            listener.pollEvent(),
            allOf(
                hasType(ScaleEventType.SCALE_BEGIN),
                isInProgress(false),
                hasFocus(pointerDown),
                hasScaleFactor(1.0f),
                hasCurrentSpan(2 * move.y),
                hasCurrentSpanXY(PointF(2 * move.x, 2 * move.y)),
                hasPreviousSpan(2 * move.y),
                hasPreviousSpanXY(PointF(2 * move.x, 2 * move.y)),
            ),
        )
        assertThat(
            listener.pollEvent(),
            allOf(
                hasType(ScaleEventType.SCALE),
                isInProgress(true),
                hasFocus(pointerDown),
                hasScaleFactor(1.0f),
                hasCurrentSpan(2 * move.y),
                hasCurrentSpanXY(PointF(2 * move.x, 2 * move.y)),
                hasPreviousSpan(2 * move.y),
                hasPreviousSpanXY(PointF(2 * move.x, 2 * move.y)),
            ),
        )
        assertThat(listener.events).isEmpty()
        assertThat(detector.isInProgress).isTrue()
    }

    @Test
    fun quickScale_spanLessThanSpanSlop_doesNotStartStream() {
        val listener = TestScaleGestureListener()
        val detector = createDetector(listener)
        val move = PointF(345f, spanSlopRadius - 1f)
        val pointerDown = PointF(100f, 200f)
        val pointerMove = pointerDown.withOffset(move)

        detector.onTouchEvent(down(pointerDown))
        detector.onTouchEvent(up(pointerDown))
        detector.onTouchEvent(down(pointerDown, eventTime = doubleTapMinTimeMillis))
        detector.onTouchEvent(move(listOf(pointerMove)))

        assertThat(listener.events).isEmpty()
        assertThat(detector.isInProgress).isFalse()
    }

    @Test
    fun quickScale_moveDown_thenMoveDown_setsPrevAndCurrSpanAndFocus_scaleFactorIsGreaterThanOne() {
        val listener = TestScaleGestureListener()
        val detector = createDetector(listener)
        val initialMove = PointF(145f, spanSlop.toFloat())
        val finalMove = initialMove.withOffset(100f, 123f)
        val pointerDown = PointF(100f, 200f)
        val pointerInitialMove = pointerDown.withOffset(initialMove)
        val pointerFinalMove = pointerDown.withOffset(finalMove)
        detector.onTouchEvent(down(pointerDown))
        detector.onTouchEvent(up(pointerDown))
        detector.onTouchEvent(down(pointerDown, eventTime = doubleTapMinTimeMillis))
        detector.onTouchEvent(move(listOf(pointerInitialMove)))
        assertThat(listener.pollEvent(), hasType(ScaleEventType.SCALE_BEGIN))
        assertThat(listener.pollEvent(), hasType(ScaleEventType.SCALE))
        assertThat(listener.events).isEmpty()
        assertThat(detector.isInProgress).isTrue()

        detector.onTouchEvent(move(listOf(pointerFinalMove)))

        assertThat(
            listener.pollEvent(),
            allOf(
                hasType(ScaleEventType.SCALE),
                isInProgress(true),
                hasFocus(pointerDown),
                hasScaleFactor(0.5f + 0.5f * finalMove.y / initialMove.y),
                hasScaleFactor(greaterThan(1f)),
                hasCurrentSpan(2 * finalMove.y),
                hasCurrentSpanXY(PointF(2 * finalMove.x, 2 * finalMove.y)),
                hasPreviousSpan(2 * initialMove.y),
                hasPreviousSpanXY(PointF(2 * initialMove.x, 2 * initialMove.y)),
            ),
        )
        assertThat(listener.events).isEmpty()
        assertThat(detector.isInProgress).isTrue()
    }

    @Test
    fun quickScale_moveDown_thenMoveUp_setPrevAndCurrSpanAndFocus_scaleFactorIsLessThanOne() {
        val listener = TestScaleGestureListener()
        val detector = createDetector(listener)
        val initialMove = PointF(145f, 123 + spanSlopRadius.toFloat())
        val finalMove = initialMove.withOffset(100f, -120f)
        val pointerDown = PointF(100f, 200f)
        val pointerInitialMove = pointerDown.withOffset(initialMove)
        val pointerFinalMove = pointerDown.withOffset(finalMove)
        detector.onTouchEvent(down(pointerDown))
        detector.onTouchEvent(up(pointerDown))
        detector.onTouchEvent(down(pointerDown, eventTime = doubleTapMinTimeMillis))
        detector.onTouchEvent(move(listOf(pointerInitialMove)))
        assertThat(listener.pollEvent(), hasType(ScaleEventType.SCALE_BEGIN))
        assertThat(listener.pollEvent(), hasType(ScaleEventType.SCALE))
        assertThat(listener.events).isEmpty()
        assertThat(detector.isInProgress).isTrue()

        detector.onTouchEvent(move(listOf(pointerFinalMove)))

        assertThat(
            listener.pollEvent(),
            allOf(
                hasType(ScaleEventType.SCALE),
                isInProgress(true),
                hasFocus(pointerDown),
                hasScaleFactor(0.5f + 0.5f * finalMove.y / initialMove.y),
                hasScaleFactor(lessThan(1f)),
                hasCurrentSpan(2 * finalMove.y),
                hasCurrentSpanXY(PointF(2 * finalMove.x, 2 * finalMove.y)),
                hasPreviousSpan(2 * initialMove.y),
                hasPreviousSpanXY(PointF(2 * initialMove.x, 2 * initialMove.y)),
            ),
        )
        assertThat(listener.events).isEmpty()
        assertThat(detector.isInProgress).isTrue()
    }

    @Test
    fun quickScale_moveUp_thenMoveDown_setsPrevAndCurrSpanAndFocus_scaleFactorIsGreaterThanOne() {
        val listener = TestScaleGestureListener()
        val detector = createDetector(listener)
        val initialMove = PointF(145f, -123 - spanSlopRadius.toFloat())
        val finalMove = initialMove.withOffset(100f, 120f)
        val pointerDown = PointF(100f, 200f)
        val pointerInitialMove = pointerDown.withOffset(initialMove)
        val pointerFinalMove = pointerDown.withOffset(finalMove)
        detector.onTouchEvent(down(pointerDown))
        detector.onTouchEvent(up(pointerDown))
        detector.onTouchEvent(down(pointerDown, eventTime = doubleTapMinTimeMillis))
        detector.onTouchEvent(move(listOf(pointerInitialMove)))
        assertThat(listener.pollEvent(), hasType(ScaleEventType.SCALE_BEGIN))
        assertThat(listener.pollEvent(), hasType(ScaleEventType.SCALE))
        assertThat(listener.events).isEmpty()
        assertThat(detector.isInProgress).isTrue()

        detector.onTouchEvent(move(listOf(pointerFinalMove)))

        assertThat(
            listener.pollEvent(),
            allOf(
                hasType(ScaleEventType.SCALE),
                isInProgress(true),
                hasFocus(pointerDown),
                hasScaleFactor(1.5f - 0.5f * finalMove.y / initialMove.y),
                hasScaleFactor(greaterThan(1f)),
                hasCurrentSpan(-2 * finalMove.y),
                hasCurrentSpanXY(PointF(2 * finalMove.x, -2 * finalMove.y)),
                hasPreviousSpan(-2 * initialMove.y),
                hasPreviousSpanXY(PointF(2 * initialMove.x, -2 * initialMove.y)),
            ),
        )
        assertThat(listener.events).isEmpty()
        assertThat(detector.isInProgress).isTrue()
    }

    @Test
    fun quickScale_moveUp_thenMoveUp_setPreviousAndCurrentSpanAndFocus_scaleFactorIsLessThanOne() {
        val listener = TestScaleGestureListener()
        val detector = createDetector(listener)
        val initialMove = PointF(145f, -50 - spanSlopRadius.toFloat())
        val finalMove = initialMove.withOffset(100f, -70f)
        val pointerDown = PointF(300f, 400f)
        val pointerInitialMove = pointerDown.withOffset(initialMove)
        val pointerFinalMove = pointerDown.withOffset(finalMove)
        detector.onTouchEvent(down(pointerDown))
        detector.onTouchEvent(up(pointerDown))
        detector.onTouchEvent(down(pointerDown, eventTime = doubleTapMinTimeMillis))
        detector.onTouchEvent(move(listOf(pointerInitialMove)))
        assertThat(listener.pollEvent(), hasType(ScaleEventType.SCALE_BEGIN))
        assertThat(listener.pollEvent(), hasType(ScaleEventType.SCALE))
        assertThat(listener.events).isEmpty()
        assertThat(detector.isInProgress).isTrue()

        detector.onTouchEvent(move(listOf(pointerFinalMove)))

        assertThat(
            listener.pollEvent(),
            allOf(
                hasType(ScaleEventType.SCALE),
                isInProgress(true),
                hasFocus(pointerDown),
                hasScaleFactor(1.5f - 0.5f * finalMove.y / initialMove.y),
                hasScaleFactor(lessThan(1f)),
                hasCurrentSpan(-2 * finalMove.y),
                hasCurrentSpanXY(PointF(2 * finalMove.x, -2 * finalMove.y)),
                hasPreviousSpan(-2 * initialMove.y),
                hasPreviousSpanXY(PointF(2 * initialMove.x, -2 * initialMove.y)),
            ),
        )
        assertThat(listener.events).isEmpty()
        assertThat(detector.isInProgress).isTrue()
    }

    @Test
    fun quickScale_inProgress_liftPointer_endsStream() {
        val listener = TestScaleGestureListener()
        val detector = createDetector(listener)
        val initialMove = PointF(145f, 1 + spanSlopRadius.toFloat())
        val finalMove = initialMove.withOffset(100f, 123f)
        val pointerDown = PointF(100f, 200f)
        val pointerInitialMove = pointerDown.withOffset(initialMove)
        val pointerFinalMove = pointerDown.withOffset(finalMove)
        detector.onTouchEvent(down(pointerDown))
        detector.onTouchEvent(up(pointerDown))
        detector.onTouchEvent(down(pointerDown, eventTime = doubleTapMinTimeMillis))
        detector.onTouchEvent(move(listOf(pointerInitialMove)))
        detector.onTouchEvent(move(listOf(pointerFinalMove)))
        assertThat(listener.pollEvent(), hasType(ScaleEventType.SCALE_BEGIN))
        assertThat(listener.pollEvent(), hasType(ScaleEventType.SCALE))
        assertThat(listener.pollEvent(), hasType(ScaleEventType.SCALE))
        assertThat(listener.events).isEmpty()

        detector.onTouchEvent(up(pointerFinalMove))

        assertThat(
            listener.pollEvent(),
            allOf(
                hasType(ScaleEventType.SCALE_END),
                isInProgress(true),
                hasFocus(pointerDown),
                hasScaleFactor(1f),
                hasCurrentSpan(2 * finalMove.y),
                hasCurrentSpanXY(PointF(2 * finalMove.x, 2 * finalMove.y)),
                hasPreviousSpan(2 * finalMove.y),
                hasPreviousSpanXY(PointF(2 * finalMove.x, 2 * finalMove.y)),
            ),
        )
        assertThat(listener.events).isEmpty()
        assertThat(detector.isInProgress).isFalse()
    }

    @Test
    fun quickScale_scaleBecomesLessThanSpanSlop_doesNotEndStream() {
        val listener = TestScaleGestureListener()
        val detector = createDetector(listener)
        val pointerDown = PointF(100f, 200f)
        val pointerInitialMove = pointerDown.withOffset(0f, spanSlopRadius.toFloat() + 100)
        val pointerFinalMove = pointerDown.withOffset(0f, 1f)
        detector.onTouchEvent(down(pointerDown))
        detector.onTouchEvent(up(pointerDown))
        detector.onTouchEvent(down(pointerDown, eventTime = doubleTapMinTimeMillis))
        detector.onTouchEvent(move(listOf(pointerInitialMove)))
        assertThat(listener.pollEvent(), hasType(ScaleEventType.SCALE_BEGIN))
        assertThat(listener.pollEvent(), hasType(ScaleEventType.SCALE))
        assertThat(listener.events).isEmpty()
        assertThat(detector.isInProgress).isTrue()

        detector.onTouchEvent(move(listOf(pointerFinalMove)))

        assertThat(listener.pollEvent(), hasType(ScaleEventType.SCALE))
        assertThat(listener.events).isEmpty()
        assertThat(detector.isInProgress).isTrue()
    }

    @Test
    fun quickScale_prevSpanLessThanSpanSlop_scaleFactorIsOne() {
        val listener = TestScaleGestureListener()
        val detector = createDetector(listener)
        val pointerDown = PointF(100f, 200f)
        val pointerInitialMove = pointerDown.withOffset(0f, spanSlopRadius + 10f)
        val pointerSecondMove = pointerDown.withOffset(0f, spanSlopRadius - 1f)
        val pointerFinalMove = pointerDown.withOffset(0f, spanSlopRadius + 10f)
        detector.onTouchEvent(down(pointerDown))
        detector.onTouchEvent(up(pointerDown))
        detector.onTouchEvent(down(pointerDown, eventTime = doubleTapMinTimeMillis))
        detector.onTouchEvent(move(listOf(pointerInitialMove)))
        detector.onTouchEvent(move(listOf(pointerSecondMove)))
        assertThat(listener.pollEvent(), hasType(ScaleEventType.SCALE_BEGIN))
        assertThat(listener.pollEvent(), hasType(ScaleEventType.SCALE))
        assertThat(listener.pollEvent(), hasType(ScaleEventType.SCALE))
        assertThat(listener.events).isEmpty()
        listener.events.clear()

        detector.onTouchEvent(move(listOf(pointerFinalMove)))

        assertThat(listener.pollEvent().scaleFactor).isEqualTo(1f)
        assertThat(listener.events).isEmpty()
        assertThat(detector.isInProgress).isTrue()
    }

    @Test
    fun quickScale_onScaleReturnsFalse_doesNotUpdatePreviousFocusAndSpan() {
        val listener = TestScaleGestureListener(onScaleReturnValue = false)
        val detector = createDetector(listener)
        val initialMove = PointF(0f, 1f + spanSlopRadius)
        val secondMove = initialMove.withOffset(0f, 123f)
        val finalMove = secondMove.withOffset(0f, 67f)
        val pointerDown = PointF(100f, 200f)
        val pointerInitialMove = pointerDown.withOffset(initialMove)
        val pointerSecondMove = pointerDown.withOffset(secondMove)
        val pointerFinalMove = pointerDown.withOffset(finalMove)
        detector.onTouchEvent(down(pointerDown))
        detector.onTouchEvent(up(pointerDown))
        detector.onTouchEvent(down(pointerDown, eventTime = doubleTapMinTimeMillis))
        detector.onTouchEvent(move(listOf(pointerInitialMove)))
        detector.onTouchEvent(move(listOf(pointerSecondMove)))
        assertThat(listener.pollEvent(), hasType(ScaleEventType.SCALE_BEGIN))
        assertThat(listener.pollEvent(), hasType(ScaleEventType.SCALE))
        assertThat(listener.pollEvent(), hasType(ScaleEventType.SCALE))
        assertThat(listener.events).isEmpty()
        listener.events.clear()

        detector.onTouchEvent(move(listOf(pointerFinalMove)))

        assertThat(
            listener.pollEvent(),
            allOf(
                hasType(ScaleEventType.SCALE),
                isInProgress(true),
                hasFocus(pointerDown),
                hasScaleFactor(0.5f + 0.5f * finalMove.y / initialMove.y),
                hasCurrentSpan(2 * finalMove.y),
                hasCurrentSpanXY(PointF(2 * finalMove.x, 2 * finalMove.y)),
                hasPreviousSpan(2 * initialMove.y),
                hasPreviousSpanXY(PointF(2 * initialMove.x, 2 * initialMove.y)),
            ),
        )
        assertThat(detector.isInProgress).isTrue()
    }

    @Test
    fun quickScale_onScaleBeginReturnsFalse_doesNotCallOnScale_inProgressIsFalse() {
        val listener = TestScaleGestureListener(onScaleBeginReturnValue = false)
        val detector = createDetector(listener)
        val pointerDown = PointF(100f, 200f)
        val pointerInitialMove = pointerDown.withOffset(0f, 100f + spanSlopRadius)
        detector.onTouchEvent(down(pointerDown))
        detector.onTouchEvent(up(pointerDown))
        detector.onTouchEvent(down(pointerDown, eventTime = doubleTapMinTimeMillis))

        detector.onTouchEvent(move(listOf(pointerInitialMove)))

        assertThat(listener.pollEvent(), hasType(ScaleEventType.SCALE_BEGIN))
        assertThat(listener.events).isEmpty()
        assertThat(detector.isInProgress).isFalse()
    }

    @Test
    fun quickScale_quickScaleDisabled_doesNotStartStream() {
        val listener = TestScaleGestureListener()
        val detector = createDetector(listener)
        detector.isQuickScaleEnabled = false
        val pointerDown = PointF(100f, 200f)
        val pointerMove = pointerDown.withOffset(345f, spanSlopRadius + 10f)

        detector.onTouchEvent(down(pointerDown))
        detector.onTouchEvent(up(pointerDown))
        detector.onTouchEvent(down(pointerDown, eventTime = doubleTapMinTimeMillis))
        detector.onTouchEvent(move(listOf(pointerMove)))

        assertThat(detector.isQuickScaleEnabled).isFalse()
        assertThat(listener.events).isEmpty()
        assertThat(detector.isInProgress).isFalse()
    }

    @Test
    fun stylusScale_buttonDown_startsStream() {
        val listener = TestScaleGestureListener()
        val detector = createDetector(listener)
        val pointerDown = PointF(100f, 200f)
        val pointerMove = pointerDown.withOffset(0f, spanSlopRadius + 1f)

        detector.onTouchEvent(down(pointerDown, buttonState = MotionEvent.BUTTON_STYLUS_PRIMARY))
        assertThat(listener.events).isEmpty()
        assertThat(detector.isInProgress).isFalse()

        detector.onTouchEvent(
            move(listOf(pointerMove), buttonState = MotionEvent.BUTTON_STYLUS_PRIMARY)
        )

        val expectedSpan = 2 * (pointerMove.y - pointerDown.y)
        val expectedSpanXY = PointF(0f, expectedSpan)
        assertThat(
            listener.pollEvent(),
            allOf(
                hasType(ScaleEventType.SCALE_BEGIN),
                isInProgress(false),
                hasFocus(pointerDown),
                hasScaleFactor(1.0f),
                hasCurrentSpan(expectedSpan),
                hasCurrentSpanXY(expectedSpanXY),
                hasPreviousSpan(expectedSpan),
                hasPreviousSpanXY(expectedSpanXY),
            ),
        )
        assertThat(
            listener.pollEvent(),
            allOf(
                hasType(ScaleEventType.SCALE),
                isInProgress(true),
                hasFocus(pointerDown),
                hasScaleFactor(1.0f),
                hasCurrentSpan(expectedSpan),
                hasCurrentSpanXY(expectedSpanXY),
                hasPreviousSpan(expectedSpan),
                hasPreviousSpanXY(expectedSpanXY),
            ),
        )
        assertThat(listener.events).isEmpty()
        assertThat(detector.isInProgress).isTrue()
    }

    @Test
    fun stylusScale_stylusScaleDisabled_doesNotStartStream() {
        val listener = TestScaleGestureListener()
        val detector = createDetector(listener)
        detector.isStylusScaleEnabled = false
        val pointerDown = PointF(100f, 200f)
        val pointerMove = pointerDown.withOffset(0f, spanSlopRadius + 200f)

        detector.onTouchEvent(down(pointerDown, buttonState = MotionEvent.BUTTON_STYLUS_PRIMARY))
        assertThat(listener.events).isEmpty()
        assertThat(detector.isInProgress).isFalse()

        detector.onTouchEvent(
            move(listOf(pointerMove), buttonState = MotionEvent.BUTTON_STYLUS_PRIMARY)
        )

        assertThat(detector.isStylusScaleEnabled).isFalse()
        assertThat(listener.events).isEmpty()
        assertThat(detector.isInProgress).isFalse()
    }

    @Test
    fun stylusScale_inProgress_buttonUp_endsStream() {
        val listener = TestScaleGestureListener()
        val detector = createDetector(listener)
        val pointerDown = PointF(100f, 200f)
        val pointerMove = pointerDown.withOffset(0f, spanSlopRadius + 1f)
        val pointerFinalMove = pointerDown.withOffset(0f, 2 * spanSlopRadius + 50f)
        detector.onTouchEvent(down(pointerDown, buttonState = MotionEvent.BUTTON_STYLUS_PRIMARY))
        detector.onTouchEvent(
            move(listOf(pointerMove), buttonState = MotionEvent.BUTTON_STYLUS_PRIMARY)
        )
        assertThat(listener.pollEvent(), hasType(ScaleEventType.SCALE_BEGIN))
        assertThat(listener.pollEvent(), hasType(ScaleEventType.SCALE))
        assertThat(listener.events).isEmpty()
        assertThat(detector.isInProgress).isTrue()

        detector.onTouchEvent(move(listOf(pointerMove)))

        val expectedSpan = 2 * (pointerMove.y - pointerDown.y)
        val expectedSpanXY = PointF(0f, expectedSpan)
        assertThat(
            listener.pollEvent(),
            allOf(
                hasType(ScaleEventType.SCALE_END),
                isInProgress(true),
                hasFocus(pointerDown),
                hasScaleFactor(1.0f),
                hasCurrentSpan(expectedSpan),
                hasCurrentSpanXY(expectedSpanXY),
                hasPreviousSpan(expectedSpan),
                hasPreviousSpanXY(expectedSpanXY),
            ),
        )
        assertThat(listener.events).isEmpty()
        assertThat(detector.isInProgress).isFalse()
    }

    @RequiresFlagsEnabled(FLAG_SCALE_GESTURE_DETECTOR_USE_EVENTS_CLASSIFICATION)
    @Test
    fun classifiedPinch_down_pointerDown_startsStream() {
        val listener = TestScaleGestureListener()
        val detector = createDetector(listener)
        val cursor = PointF(100f, 200f)
        val pointer0 = cursor.withOffset(-2f, 0f)
        val pointer1 = cursor.withOffset(2f, 0f)

        detector.onTouchEvent(
            MotionEventBuilder(ACTION_DOWN, SOURCE_MOUSE)
                .pointer(
                    PointerBuilder(0, TOOL_TYPE_FINGER)
                        .xy(pointer0)
                        .axis(AXIS_GESTURE_PINCH_SCALE_FACTOR, 1f)
                )
                .eventTime(113)
                .classification(MotionEvent.CLASSIFICATION_PINCH)
                .build()
        )
        detector.onTouchEvent(
            MotionEventBuilder(
                ACTION_POINTER_DOWN,
                SOURCE_MOUSE
            )
                .pointer(
                    PointerBuilder(0, TOOL_TYPE_FINGER)
                        .xy(pointer0)
                        .axis(AXIS_GESTURE_PINCH_SCALE_FACTOR, 1f)
                )
                .pointer(PointerBuilder(1, TOOL_TYPE_FINGER).xy(pointer1))
                .pointerIndex(1)
                .eventTime(113)
                .classification(MotionEvent.CLASSIFICATION_PINCH)
                .build()
        )

        val expectedSpan = PointF(pointer1.x - pointer0.x, 0f)
        assertThat(
            listener.pollEvent(),
            allOf(
                hasType(ScaleEventType.SCALE_BEGIN),
                isInProgress(false),
                hasFocus(cursor),
                hasScaleFactor(1f),
                hasCurrentSpan(expectedSpan.hypot()),
                hasCurrentSpanXY(expectedSpan),
                hasPreviousSpan(expectedSpan.hypot()),
                hasPreviousSpanXY(expectedSpan),
                hasEventTime(113),
                hasTimeDelta(0),
            ),
        )
        assertThat(listener.events).isEmpty()
        assertThat(detector.isInProgress).isTrue()
    }

    @RequiresFlagsEnabled(FLAG_SCALE_GESTURE_DETECTOR_USE_EVENTS_CLASSIFICATION)
    @Test
    fun classifiedPinch_onScaleBeginReturnsFalse_doesNotStartStream() {
        val listener = TestScaleGestureListener(onScaleBeginReturnValue = false)
        val detector = createDetector(listener)
        val cursor = PointF(500f, 600f)
        val pointer0 = cursor.withOffset(-400f, 0f)
        val pointer1 = cursor.withOffset(400f, 0f)

        detector.onTouchEvent(
            MotionEventBuilder(ACTION_DOWN, SOURCE_MOUSE)
                .pointer(
                    PointerBuilder(0, TOOL_TYPE_FINGER)
                        .xy(pointer0)
                        .axis(AXIS_GESTURE_PINCH_SCALE_FACTOR, 1f)
                )
                .eventTime(100)
                .classification(MotionEvent.CLASSIFICATION_PINCH)
                .build()
        )
        assertThat(listener.events).isEmpty()

        detector.onTouchEvent(
            MotionEventBuilder(
                ACTION_POINTER_DOWN,
                SOURCE_MOUSE
            )
                .pointer(
                    PointerBuilder(0, TOOL_TYPE_FINGER)
                        .xy(pointer0)
                        .axis(AXIS_GESTURE_PINCH_SCALE_FACTOR, 1f)
                )
                .pointer(PointerBuilder(1, TOOL_TYPE_FINGER).xy(pointer1))
                .pointerIndex(1)
                .eventTime(113)
                .classification(MotionEvent.CLASSIFICATION_PINCH)
                .build()
        )
        assertThat(listener.pollEvent(), hasType(ScaleEventType.SCALE_BEGIN))
        assertThat(listener.events).isEmpty()
        assertThat(detector.isInProgress).isFalse()
    }

    @RequiresFlagsEnabled(FLAG_SCALE_GESTURE_DETECTOR_USE_EVENTS_CLASSIFICATION)
    @Test
    fun classifiedPinch_multipleMoves_callsOnScale_setPrevAndCurrSpanTimeAndFocus() {
        val listener = TestScaleGestureListener()
        val detector = createDetector(listener)
        val cursor = PointF(150f, 200f)
        val pointer0Down = cursor.withOffset(-50f, 0f)
        val pointer1Down = cursor.withOffset(50f, 0f)
        val pointer0InitialMove = cursor.withOffset(-55f, 0f)
        val pointer1InitialMove = cursor.withOffset(55f, 0f)
        val pointer0FinalMove = cursor.withOffset(-62f, 0f)
        val pointer1FinalMove = cursor.withOffset(62f, 0f)
        detector.onTouchEvent(
            MotionEventBuilder(ACTION_DOWN, SOURCE_MOUSE)
                .pointer(
                    PointerBuilder(0, TOOL_TYPE_FINGER)
                        .xy(pointer0Down)
                        .axis(AXIS_GESTURE_PINCH_SCALE_FACTOR, 1f)
                )
                .eventTime(100)
                .classification(MotionEvent.CLASSIFICATION_PINCH)
                .build()
        )
        detector.onTouchEvent(
            MotionEventBuilder(
                ACTION_POINTER_DOWN,
                SOURCE_MOUSE
            )
                .pointer(
                    PointerBuilder(0, TOOL_TYPE_FINGER)
                        .xy(pointer0Down)
                        .axis(AXIS_GESTURE_PINCH_SCALE_FACTOR, 1f)
                )
                .pointer(PointerBuilder(1, TOOL_TYPE_FINGER).xy(pointer1Down))
                .pointerIndex(1)
                .eventTime(113)
                .classification(MotionEvent.CLASSIFICATION_PINCH)
                .build()
        )
        assertThat(listener.pollEvent(), hasType(ScaleEventType.SCALE_BEGIN))
        assertThat(listener.events).isEmpty()
        assertThat(detector.isInProgress).isTrue()

        detector.onTouchEvent(
            MotionEventBuilder(MotionEvent.ACTION_MOVE, SOURCE_MOUSE)
                .pointer(
                    PointerBuilder(0, TOOL_TYPE_FINGER)
                        .xy(pointer0InitialMove)
                        .axis(AXIS_GESTURE_PINCH_SCALE_FACTOR, 1.678f)
                )
                .pointer(PointerBuilder(1, TOOL_TYPE_FINGER).xy(pointer1InitialMove))
                .eventTime(137)
                .classification(MotionEvent.CLASSIFICATION_PINCH)
                .build()
        )

        val expectedInitialPrevSpan = PointF(pointer1Down.x - pointer0Down.x, 0f)
        val expectedInitialSpan = PointF(pointer1InitialMove.x - pointer0InitialMove.x, 0f)
        assertThat(
            listener.pollEvent(),
            allOf(
                hasType(ScaleEventType.SCALE),
                isInProgress(true),
                hasFocus(cursor),
                hasScaleFactor(1.678f),
                hasCurrentSpan(expectedInitialSpan.x),
                hasCurrentSpanXY(expectedInitialSpan),
                hasPreviousSpan(expectedInitialPrevSpan.x),
                hasPreviousSpanXY(expectedInitialPrevSpan),
                hasEventTime(137),
                hasTimeDelta(24),
            ),
        )
        assertThat(listener.events).isEmpty()
        assertThat(detector.isInProgress).isTrue()

        detector.onTouchEvent(
            MotionEventBuilder(MotionEvent.ACTION_MOVE, SOURCE_MOUSE)
                .pointer(
                    PointerBuilder(0, TOOL_TYPE_FINGER)
                        .xy(pointer0FinalMove)
                        .axis(AXIS_GESTURE_PINCH_SCALE_FACTOR, 0.789f)
                )
                .pointer(PointerBuilder(1, TOOL_TYPE_FINGER).xy(pointer1FinalMove))
                .eventTime(142)
                .classification(MotionEvent.CLASSIFICATION_PINCH)
                .build()
        )

        val expectedFinalPrevSpan = PointF(pointer1InitialMove.x - pointer0InitialMove.x, 0f)
        val expectedFinalSpan = PointF(pointer1FinalMove.x - pointer0FinalMove.x, 0f)
        assertThat(
            listener.pollEvent(),
            allOf(
                hasType(ScaleEventType.SCALE),
                isInProgress(true),
                hasFocus(cursor),
                hasScaleFactor(0.789f),
                hasCurrentSpan(expectedFinalSpan.x),
                hasCurrentSpanXY(expectedFinalSpan),
                hasPreviousSpan(expectedFinalPrevSpan.x),
                hasPreviousSpanXY(expectedFinalPrevSpan),
                hasEventTime(142),
                hasTimeDelta(5),
            ),
        )
        assertThat(listener.events).isEmpty()
        assertThat(detector.isInProgress).isTrue()
    }

    @RequiresFlagsEnabled(FLAG_SCALE_GESTURE_DETECTOR_USE_EVENTS_CLASSIFICATION)
    @Test
    fun classifiedPinch_multipleMoves_onScaleReturnsFalse_doesNotUpdatePrevSpan() {
        val listener = TestScaleGestureListener(onScaleReturnValue = false)
        val detector = createDetector(listener)
        val cursor = PointF(150f, 200f)
        val pointer0Down = cursor.withOffset(-50f, 0f)
        val pointer1Down = cursor.withOffset(50f, 0f)
        val pointer0InitialMove = cursor.withOffset(-57f, 0f)
        val pointer1InitialMove = cursor.withOffset(57f, 0f)
        val pointer0FinalMove = cursor.withOffset(-80f, 0f)
        val pointer1FinalMove = cursor.withOffset(80f, 0f)
        detector.onTouchEvent(
            MotionEventBuilder(ACTION_DOWN, SOURCE_MOUSE)
                .pointer(
                    PointerBuilder(0, TOOL_TYPE_FINGER)
                        .xy(pointer0Down)
                        .axis(AXIS_GESTURE_PINCH_SCALE_FACTOR, 1f)
                )
                .eventTime(100)
                .classification(MotionEvent.CLASSIFICATION_PINCH)
                .build()
        )
        detector.onTouchEvent(
            MotionEventBuilder(
                ACTION_POINTER_DOWN,
                SOURCE_MOUSE
            )
                .pointer(
                    PointerBuilder(0, TOOL_TYPE_FINGER)
                        .xy(pointer0Down)
                        .axis(AXIS_GESTURE_PINCH_SCALE_FACTOR, 1f)
                )
                .pointer(PointerBuilder(1, TOOL_TYPE_FINGER).xy(pointer1Down))
                .pointerIndex(1)
                .eventTime(113)
                .classification(MotionEvent.CLASSIFICATION_PINCH)
                .build()
        )
        detector.onTouchEvent(
            MotionEventBuilder(MotionEvent.ACTION_MOVE, SOURCE_MOUSE)
                .pointer(
                    PointerBuilder(0, TOOL_TYPE_FINGER)
                        .xy(pointer0InitialMove)
                        .axis(AXIS_GESTURE_PINCH_SCALE_FACTOR, 1.678f)
                )
                .pointer(PointerBuilder(1, TOOL_TYPE_FINGER).xy(pointer1InitialMove))
                .eventTime(137)
                .classification(MotionEvent.CLASSIFICATION_PINCH)
                .build()
        )
        assertThat(listener.pollEvent(), hasType(ScaleEventType.SCALE_BEGIN))
        assertThat(listener.pollEvent(), hasType(ScaleEventType.SCALE))
        assertThat(listener.events).isEmpty()
        assertThat(detector.isInProgress).isTrue()

        detector.onTouchEvent(
            MotionEventBuilder(MotionEvent.ACTION_MOVE, SOURCE_MOUSE)
                .pointer(
                    PointerBuilder(0, TOOL_TYPE_FINGER)
                        .xy(pointer0FinalMove)
                        .axis(AXIS_GESTURE_PINCH_SCALE_FACTOR, 0.789f)
                )
                .pointer(PointerBuilder(1, TOOL_TYPE_FINGER).xy(pointer1FinalMove))
                .eventTime(142)
                .classification(MotionEvent.CLASSIFICATION_PINCH)
                .build()
        )

        val expectedPrevSpan = PointF(pointer1Down.x - pointer0Down.x, 0f)
        val expectedSpan = PointF(pointer1FinalMove.x - pointer0FinalMove.x, 0f)
        assertThat(
            listener.pollEvent(),
            allOf(
                hasType(ScaleEventType.SCALE),
                isInProgress(true),
                hasFocus(cursor),
                hasScaleFactor(0.789f),
                hasCurrentSpan(expectedSpan.x),
                hasCurrentSpanXY(expectedSpan),
                hasPreviousSpan(expectedPrevSpan.x),
                hasPreviousSpanXY(expectedPrevSpan),
                hasEventTime(142),
                hasTimeDelta(29),
            ),
        )
        assertThat(listener.events).isEmpty()
        assertThat(detector.isInProgress).isTrue()
    }

    @RequiresFlagsEnabled(FLAG_SCALE_GESTURE_DETECTOR_USE_EVENTS_CLASSIFICATION)
    @Test
    fun classifiedPinch_pointerUp_up_endsStream() {
        val listener = TestScaleGestureListener()
        val detector = createDetector(listener)
        val cursor = PointF(102f, 196f)
        val pointer0Down = cursor.withOffset(-2f, 0f)
        val pointer1Down = cursor.withOffset(2f, 0f)
        val pointer0Move = cursor.withOffset(-8f, 0f)
        val pointer1Move = cursor.withOffset(8f, 0f)
        detector.onTouchEvent(
            MotionEventBuilder(ACTION_DOWN, SOURCE_MOUSE)
                .pointer(
                    PointerBuilder(0, TOOL_TYPE_FINGER)
                        .xy(pointer0Down)
                        .axis(AXIS_GESTURE_PINCH_SCALE_FACTOR, 1f)
                )
                .eventTime(200)
                .classification(MotionEvent.CLASSIFICATION_PINCH)
                .build()
        )
        detector.onTouchEvent(
            MotionEventBuilder(
                ACTION_POINTER_DOWN,
                SOURCE_MOUSE
            )
                .pointer(
                    PointerBuilder(0, TOOL_TYPE_FINGER)
                        .xy(pointer0Down)
                        .axis(AXIS_GESTURE_PINCH_SCALE_FACTOR, 1f)
                )
                .pointer(PointerBuilder(1, TOOL_TYPE_FINGER).xy(pointer1Down))
                .pointerIndex(1)
                .eventTime(227)
                .classification(MotionEvent.CLASSIFICATION_PINCH)
                .build()
        )
        detector.onTouchEvent(
            MotionEventBuilder(MotionEvent.ACTION_MOVE, SOURCE_MOUSE)
                .pointer(
                    PointerBuilder(0, TOOL_TYPE_FINGER)
                        .xy(pointer0Move)
                        .axis(AXIS_GESTURE_PINCH_SCALE_FACTOR, 0.925f)
                )
                .pointer(PointerBuilder(1, TOOL_TYPE_FINGER).xy(pointer1Move))
                .eventTime(250)
                .classification(MotionEvent.CLASSIFICATION_PINCH)
                .build()
        )
        assertThat(listener.pollEvent(), hasType(ScaleEventType.SCALE_BEGIN))
        assertThat(listener.pollEvent(), hasType(ScaleEventType.SCALE))
        assertThat(listener.events).isEmpty()
        assertThat(detector.isInProgress).isTrue()

        detector.onTouchEvent(
            MotionEventBuilder(
                MotionEvent.ACTION_POINTER_UP,
                SOURCE_MOUSE
            )
                .pointer(
                    PointerBuilder(0, TOOL_TYPE_FINGER)
                        .xy(pointer0Move)
                        .axis(AXIS_GESTURE_PINCH_SCALE_FACTOR, 2.897f)
                )
                .pointer(PointerBuilder(1, TOOL_TYPE_FINGER).xy(pointer1Move))
                .pointerIndex(1)
                .eventTime(351)
                .classification(MotionEvent.CLASSIFICATION_PINCH)
                .build()
        )
        detector.onTouchEvent(
            MotionEventBuilder(MotionEvent.ACTION_UP, SOURCE_MOUSE)
                .pointer(
                    PointerBuilder(0, TOOL_TYPE_FINGER)
                        .xy(pointer0Move)
                        .axis(AXIS_GESTURE_PINCH_SCALE_FACTOR, 1.923f)
                )
                .eventTime(351)
                .classification(MotionEvent.CLASSIFICATION_PINCH)
                .build()
        )

        val expectedSpan = PointF(pointer1Move.x - pointer0Move.x, 0f)
        assertThat(
            listener.pollEvent(),
            allOf(
                hasType(ScaleEventType.SCALE_END),
                isInProgress(true),
                hasFocus(cursor),
                hasScaleFactor(0.925f),
                hasCurrentSpan(expectedSpan.x),
                hasCurrentSpanXY(expectedSpan),
                hasPreviousSpan(expectedSpan.x),
                hasPreviousSpanXY(expectedSpan),
                hasEventTime(351),
                hasTimeDelta(101),
            ),
        )
        assertThat(listener.events).isEmpty()
        assertThat(detector.isInProgress).isFalse()
    }

    @RequiresFlagsEnabled(FLAG_SCALE_GESTURE_DETECTOR_USE_EVENTS_CLASSIFICATION)
    @Test
    fun classifiedPinch_interruptedByNonPinchEvent_endsStream() {
        val listener = TestScaleGestureListener()
        val detector = createDetector(listener)
        val cursor = PointF(100f, 200f)
        val pointer0Down = cursor.withOffset(-50f, 0f)
        val pointer1Down = cursor.withOffset(50f, 0f)
        val pointer1Move = cursor.withOffset(250f, 300f)
        detector.onTouchEvent(
            MotionEventBuilder(ACTION_DOWN, SOURCE_MOUSE)
                .pointer(
                    PointerBuilder(0, TOOL_TYPE_FINGER)
                        .xy(pointer0Down)
                        .axis(AXIS_GESTURE_PINCH_SCALE_FACTOR, 1f)
                )
                .classification(MotionEvent.CLASSIFICATION_PINCH)
                .build()
        )
        detector.onTouchEvent(
            MotionEventBuilder(
                ACTION_POINTER_DOWN,
                SOURCE_MOUSE
            )
                .pointer(
                    PointerBuilder(0, TOOL_TYPE_FINGER)
                        .xy(pointer0Down)
                        .axis(AXIS_GESTURE_PINCH_SCALE_FACTOR, 1f)
                )
                .pointer(PointerBuilder(1, TOOL_TYPE_FINGER).xy(pointer1Down))
                .pointerIndex(1)
                .classification(MotionEvent.CLASSIFICATION_PINCH)
                .build()
        )
        assertThat(listener.pollEvent(), hasType(ScaleEventType.SCALE_BEGIN))
        assertThat(listener.events).isEmpty()
        assertThat(detector.isInProgress).isTrue()

        detector.onTouchEvent(
            MotionEventBuilder(MotionEvent.ACTION_MOVE, SOURCE_MOUSE)
                .pointer(PointerBuilder(0, TOOL_TYPE_FINGER).xy(pointer0Down))
                .pointer(PointerBuilder(1, TOOL_TYPE_FINGER).xy(pointer1Move))
                .eventTime(150)
                .classification(MotionEvent.CLASSIFICATION_NONE)
                .build()
        )

        assertThat(listener.pollEvent(), hasType(ScaleEventType.SCALE_END))
    }

    @RequiresFlagsEnabled(FLAG_SCALE_GESTURE_DETECTOR_USE_EVENTS_CLASSIFICATION)
    @Test
    fun classifiedPinch_interruptsNonPinchStream_endsStream() {
        val listener = TestScaleGestureListener()
        val detector = createDetector(listener)
        val pointer0 = PointF(100f, 200f)
        val pointer1Down = pointer0.withOffset(minSpan.toFloat(), 0f)
        val pointer1Move = pointer1Down.withOffset(spanSlop.toFloat(), 1f)

        // Start a regular two-finger scale gesture
        detector.onTouchEvent(
            MotionEventBuilder(MotionEvent.ACTION_DOWN, InputDevice.SOURCE_TOUCHSCREEN)
                .eventTime(100)
                .pointer(PointerBuilder(0, TOOL_TYPE_FINGER).xy(pointer0))
                .build()
        )
        detector.onTouchEvent(
            MotionEventBuilder(MotionEvent.ACTION_POINTER_DOWN, InputDevice.SOURCE_TOUCHSCREEN)
                .eventTime(110)
                .pointer(PointerBuilder(0, TOOL_TYPE_FINGER).xy(pointer0))
                .pointer(PointerBuilder(1, TOOL_TYPE_FINGER).xy(pointer1Down))
                .pointerIndex(1)
                .build()
        )
        detector.onTouchEvent(
            MotionEventBuilder(MotionEvent.ACTION_MOVE, InputDevice.SOURCE_TOUCHSCREEN)
                .eventTime(120)
                .pointer(PointerBuilder(0, TOOL_TYPE_FINGER).xy(pointer0))
                .pointer(PointerBuilder(1, TOOL_TYPE_FINGER).xy(pointer1Move))
                .build()
        )
        assertThat(listener.pollEvent(), hasType(ScaleEventType.SCALE_BEGIN))
        assertThat(listener.pollEvent(), hasType(ScaleEventType.SCALE))
        assertThat(listener.events).isEmpty()
        assertThat(detector.isInProgress).isTrue()

        // Interrupt with a classified pinch event
        val pointer1Pinch = pointer1Move.withOffset(50f, 50f)
        detector.onTouchEvent(
            MotionEventBuilder(MotionEvent.ACTION_MOVE, SOURCE_MOUSE)
                .pointer(
                    PointerBuilder(0, TOOL_TYPE_FINGER)
                        .xy(pointer0)
                        .axis(AXIS_GESTURE_PINCH_SCALE_FACTOR, 1.227f)
                )
                .pointer(PointerBuilder(1, TOOL_TYPE_FINGER).xy(pointer1Pinch))
                .classification(MotionEvent.CLASSIFICATION_PINCH)
                .build()
        )

        assertThat(listener.pollEvent(), hasType(ScaleEventType.SCALE_END))
        assertThat(listener.events).isEmpty()
        assertThat(detector.isInProgress).isFalse()
    }

    @RequiresFlagsEnabled(FLAG_SCALE_GESTURE_DETECTOR_USE_EVENTS_CLASSIFICATION)
    @Test
    fun classifiedPinch_cancel_endsStream() {
        val listener = TestScaleGestureListener()
        val detector = createDetector(listener)
        val cursor = PointF(100f, 200f)
        val pointer0 = cursor.withOffset(-5f, 0f)
        val pointer1 = cursor.withOffset(5f, 0f)
        detector.onTouchEvent(
            MotionEventBuilder(ACTION_DOWN, SOURCE_MOUSE)
                .pointer(
                    PointerBuilder(0, TOOL_TYPE_FINGER)
                        .xy(pointer0)
                        .axis(AXIS_GESTURE_PINCH_SCALE_FACTOR, 1f)
                )
                .eventTime(100)
                .classification(MotionEvent.CLASSIFICATION_PINCH)
                .build()
        )
        detector.onTouchEvent(
            MotionEventBuilder(
                ACTION_POINTER_DOWN,
                SOURCE_MOUSE
            )
                .pointer(
                    PointerBuilder(0, TOOL_TYPE_FINGER)
                        .xy(pointer0)
                        .axis(AXIS_GESTURE_PINCH_SCALE_FACTOR, 1.453f)
                )
                .pointer(PointerBuilder(1, TOOL_TYPE_FINGER).xy(pointer1))
                .pointerIndex(1)
                .eventTime(113)
                .classification(MotionEvent.CLASSIFICATION_PINCH)
                .build()
        )
        assertThat(listener.pollEvent(), hasType(ScaleEventType.SCALE_BEGIN))
        assertThat(listener.events).isEmpty()
        assertThat(detector.isInProgress).isTrue()

        detector.onTouchEvent(
            MotionEventBuilder(MotionEvent.ACTION_CANCEL, SOURCE_MOUSE)
                .pointer(PointerBuilder(0, TOOL_TYPE_FINGER).xy(pointer0))
                .pointer(PointerBuilder(1, TOOL_TYPE_FINGER).xy(pointer1))
                .eventTime(120)
                .build()
        )

        assertThat(listener.pollEvent(), hasType(ScaleEventType.SCALE_END))
        assertThat(listener.events).isEmpty()
        assertThat(detector.isInProgress).isFalse()
    }

    @RequiresFlagsDisabled(FLAG_SCALE_GESTURE_DETECTOR_USE_EVENTS_CLASSIFICATION)
    @Test
    fun classifiedPinch_flagDisabled_handledAsTwoFingerGesture() {
        val listener = TestScaleGestureListener()
        val detector = createDetector(listener)
        val cursor = PointF(100f, 200f)
        val pointer0Down = cursor.withOffset(-minSpan - 100f, 0f)
        val pointer1Down = cursor.withOffset(minSpan + 100f, 0f)
        val pointer0Move = pointer0Down.withOffset(-spanSlop - 10f, 0f)
        val pointer1Move = pointer1Down.withOffset(spanSlop + 10f, 0f)

        detector.onTouchEvent(
            MotionEventBuilder(ACTION_DOWN, SOURCE_MOUSE)
                .pointer(
                    PointerBuilder(0, TOOL_TYPE_FINGER)
                        .xy(pointer0Down)
                        .axis(AXIS_GESTURE_PINCH_SCALE_FACTOR, 1f)
                )
                .eventTime(100)
                .classification(MotionEvent.CLASSIFICATION_PINCH)
                .build()
        )
        detector.onTouchEvent(
            MotionEventBuilder(
                ACTION_POINTER_DOWN,
                SOURCE_MOUSE
            )
                .pointer(
                    PointerBuilder(0, TOOL_TYPE_FINGER)
                        .xy(pointer0Down)
                        .axis(AXIS_GESTURE_PINCH_SCALE_FACTOR, 1.453f)
                )
                .pointer(PointerBuilder(1, TOOL_TYPE_FINGER).xy(pointer1Down))
                .pointerIndex(1)
                .eventTime(113)
                .classification(MotionEvent.CLASSIFICATION_PINCH)
                .build()
        )
        assertThat(listener.events).isEmpty()
        assertThat(detector.isInProgress).isFalse()

        detector.onTouchEvent(
            MotionEventBuilder(MotionEvent.ACTION_MOVE, SOURCE_MOUSE)
                .pointer(
                    PointerBuilder(0, TOOL_TYPE_FINGER)
                        .xy(pointer0Move)
                        .axis(AXIS_GESTURE_PINCH_SCALE_FACTOR, 1.678f)
                )
                .pointer(PointerBuilder(1, TOOL_TYPE_FINGER).xy(pointer1Move))
                .eventTime(137)
                .classification(MotionEvent.CLASSIFICATION_PINCH)
                .build()
        )

        assertThat(
            listener.pollEvent(),
            allOf(
                hasType(ScaleEventType.SCALE_BEGIN),
                isInProgress(false),
                hasFocus(cursor),
                hasScaleFactor(1f),
                hasCurrentSpan(distance(pointer0Move, pointer1Move).hypot()),
                hasCurrentSpanXY(distance(pointer0Move, pointer1Move)),
                hasPreviousSpan(distance(pointer0Move, pointer1Move).hypot()),
                hasPreviousSpanXY(distance(pointer0Move, pointer1Move)),
            ),
        )
        assertThat(
            listener.pollEvent(),
            allOf(
                hasType(ScaleEventType.SCALE),
                isInProgress(true),
                hasFocus(cursor),
                hasScaleFactor(1f),
                hasCurrentSpan(distance(pointer0Move, pointer1Move).hypot()),
                hasCurrentSpanXY(distance(pointer0Move, pointer1Move)),
                hasPreviousSpan(distance(pointer0Move, pointer1Move).hypot()),
                hasPreviousSpanXY(distance(pointer0Move, pointer1Move)),
            ),
        )
        assertThat(listener.events).isEmpty()
        assertThat(detector.isInProgress).isTrue()
    }

    @Test
    fun touchScreen_twoFingers_endToEnd() {
        val pointer0Down = PointF(100f, 200f)
        val pointer0Move = pointer0Down.withOffset(0f - spanSlop, -20f)
        val pointer1Down = PointF(300f + minSpan, 400f + minSpan)
        val pointer1Move = pointer1Down.withOffset(30f, 40f)

        UinputTouchScreen(
            instrumentation,
            virtualDisplayRule.virtualDisplay.display
        ).use { touchScreen ->
            val firstPointer = touchScreen.touchDown(pointer0Down.toIntPoint())
            val secondPointer = touchScreen.touchDown(pointer1Down.toIntPoint())
            secondPointer.moveTo(pointer1Move.toIntPoint())
            Thread.sleep(ofMillis(50))
            firstPointer.moveTo(pointer0Move.toIntPoint())
            secondPointer.lift()
            firstPointer.lift()
        }

        val expectedInitialFocus = midpoint(pointer0Down, pointer1Move)
        val expectedInitialSpan = distance(pointer0Down, pointer1Move)
        val expectedFinalFocus = midpoint(pointer0Move, pointer1Move)
        val expectedFinalSpan = distance(pointer0Move, pointer1Move)
        val listener = virtualDisplayRule.activity.listener

        assertThat(
            listener.pollEvent(),
            allOf(
                hasType(ScaleEventType.SCALE_BEGIN),
                isInProgress(false),
                hasFocus(expectedInitialFocus),
                hasScaleFactor(1.0f),
                hasCurrentSpanXY(expectedInitialSpan),
                hasCurrentSpan(expectedInitialSpan.hypot()),
                hasPreviousSpanXY(expectedInitialSpan),
                hasPreviousSpan(expectedInitialSpan.hypot()),
            ),
        )
        assertThat(
            listener.pollEvent(),
            allOf(
                hasType(ScaleEventType.SCALE),
                isInProgress(true),
                hasFocus(expectedInitialFocus),
                hasScaleFactor(1.0f),
                hasCurrentSpanXY(expectedInitialSpan),
                hasCurrentSpan(expectedInitialSpan.hypot()),
                hasPreviousSpanXY(expectedInitialSpan),
                hasPreviousSpan(expectedInitialSpan.hypot()),
            ),
        )
        assertThat(
            listener.pollEvent(),
            allOf(
                hasType(ScaleEventType.SCALE),
                isInProgress(true),
                hasFocus(expectedFinalFocus),
                hasScaleFactor(expectedFinalSpan.hypot() / expectedInitialSpan.hypot()),
                hasCurrentSpanXY(expectedFinalSpan),
                hasCurrentSpan(expectedFinalSpan.hypot()),
                hasPreviousSpanXY(expectedInitialSpan),
                hasPreviousSpan(expectedInitialSpan.hypot()),
            ),
        )
        assertThat(
            listener.pollEvent(),
            allOf(
                hasType(ScaleEventType.SCALE_END),
                isInProgress(true),
                hasFocus(pointer0Move),
                hasScaleFactor(1f),
                hasCurrentSpanXY(expectedFinalSpan),
                hasCurrentSpan(expectedFinalSpan.hypot()),
                hasPreviousSpanXY(expectedFinalSpan),
                hasPreviousSpan(expectedFinalSpan.hypot()),
            ),
        )
        assertThat(listener.events).isEmpty()
    }

    private fun down(pointer: PointF, eventTime: Long = 0, buttonState: Int = 0) =
        motionEvent(
            MotionEvent.ACTION_DOWN,
            listOf(pointer),
            eventTime = eventTime,
            buttonState = buttonState,
        )

    private fun pointerDown(index: Int, pointers: List<PointF>, eventTime: Long = 0) =
        motionEvent(
            MotionEvent.ACTION_POINTER_DOWN,
            pointers,
            eventTime = eventTime,
            pointerIndex = index,
        )

    private fun move(pointers: List<PointF>, eventTime: Long = 0, buttonState: Int = 0) =
        motionEvent(
            MotionEvent.ACTION_MOVE,
            pointers,
            eventTime = eventTime,
            buttonState = buttonState,
        )

    private fun pointerUp(index: Int, pointers: List<PointF>, eventTime: Long = 0) =
        motionEvent(
            MotionEvent.ACTION_POINTER_UP,
            pointers,
            eventTime = eventTime,
            pointerIndex = index,
        )

    private fun up(pointer: PointF, eventTime: Long = 0, buttonState: Int = 0) =
        motionEvent(
            MotionEvent.ACTION_UP,
            listOf(pointer),
            eventTime = eventTime,
            buttonState = buttonState,
        )

    private fun motionEvent(
        action: Int,
        pointers: List<PointF>,
        eventTime: Long = 0L,
        buttonState: Int = 0,
        pointerIndex: Int? = null,
    ): MotionEvent {
        val pointers =
            pointers.mapIndexed { id, pointer -> PointerBuilder(id, TOOL_TYPE_FINGER).xy(pointer) }

        val builder = MotionEventBuilder(action, InputDevice.SOURCE_TOUCHSCREEN)
            .eventTime(eventTime)
            .buttonState(buttonState)
        pointers.forEach(builder::pointer)

        if (pointerIndex != null) {
            builder.pointerIndex(pointerIndex)
        }

        return builder.build()
    }

    fun PointF.hypot() = kotlin.math.hypot(this.x, this.y)

    fun midpoint(a: PointF, b: PointF) = PointF(a.x + 0.5f * (b.x - a.x), a.y + 0.5f * (b.y - a.y))

    fun distance(a: PointF, b: PointF) = PointF(abs(b.x - a.x), abs(b.y - a.y))

    fun PointF.withOffset(p: PointF) = PointF(this.x + p.x, this.y + p.y)

    fun PointF.withOffset(x: Float, y: Float) = PointF(this.x + x, this.y + y)

    fun PointF.toIntPoint() = Point(round(this.x).toInt(), round(this.y).toInt())

    fun createDetector(listener: OnScaleGestureListener) =
        ScaleGestureDetector(
            this.virtualDisplayRule.activity,
            listener,
            Handler(Looper.getMainLooper()),
        )
}
