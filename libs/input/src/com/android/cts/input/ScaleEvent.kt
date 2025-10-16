/*
 * Copyright 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.cts.input

import android.graphics.PointF
import android.view.ScaleGestureDetector
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.TypeSafeMatcher

enum class ScaleEventType {
    SCALE_BEGIN,
    SCALE,
    SCALE_END,
}

data class ScaleEvent(
    val type: ScaleEventType,
    val isInProgress: Boolean,
    val focus: PointF,
    val scaleFactor: Float,
    val currentSpanXY: PointF,
    val currentSpan: Float,
    val previousSpanXY: PointF,
    val previousSpan: Float,
    val eventTime: Long,
    val timeDelta: Long,
)

fun scaleEvent(type: ScaleEventType, detector: ScaleGestureDetector) =
    ScaleEvent(
        type = type,
        isInProgress = detector.isInProgress,
        focus = PointF(detector.focusX, detector.focusY),
        scaleFactor = detector.scaleFactor,
        currentSpanXY = PointF(detector.currentSpanX, detector.currentSpanY),
        currentSpan = detector.currentSpan,
        previousSpanXY = PointF(detector.previousSpanX, detector.previousSpanY),
        previousSpan = detector.previousSpan,
        eventTime = detector.eventTime,
        timeDelta = detector.timeDelta,
    )

private const val EPSILON = 0.001f

fun hasType(type: ScaleEventType) =
    object : TypeSafeMatcher<ScaleEvent>() {
        override fun matchesSafely(event: ScaleEvent): Boolean {
            return event.type == type
        }

        override fun describeTo(description: Description) {
            description.appendText("type=$type")
        }
    }

fun isInProgress(isInProgress: Boolean) =
    object : TypeSafeMatcher<ScaleEvent>() {
        override fun matchesSafely(event: ScaleEvent): Boolean {
            return event.isInProgress == isInProgress
        }

        override fun describeTo(description: Description) {
            description.appendText("isInProgress=$isInProgress")
        }
    }

fun hasFocus(focus: PointF): Matcher<ScaleEvent> =
    object : TypeSafeMatcher<ScaleEvent>() {
        override fun matchesSafely(event: ScaleEvent): Boolean {
            return (event.focus.x - focus.x) < EPSILON && (event.focus.y - focus.y) < EPSILON
        }

        override fun describeTo(description: Description) {
            description.appendText("focus=$focus")
        }
    }

fun hasScaleFactor(scaleFactor: Float): Matcher<ScaleEvent> =
    object : TypeSafeMatcher<ScaleEvent>() {
        override fun matchesSafely(event: ScaleEvent): Boolean {
            return (event.scaleFactor - scaleFactor) < EPSILON
        }

        override fun describeTo(description: Description) {
            description.appendText("scaleFactor=$scaleFactor")
        }
    }

fun hasScaleFactor(matcher: Matcher<Float>): Matcher<ScaleEvent> =
    object : TypeSafeMatcher<ScaleEvent>() {
        override fun matchesSafely(event: ScaleEvent): Boolean {
            return matcher.matches(event.scaleFactor)
        }

        override fun describeTo(description: Description) {
            description.appendText("scaleFactor")
            matcher.describeTo(description)
        }
    }

fun hasCurrentSpanXY(currentSpanXY: PointF): Matcher<ScaleEvent> =
    object : TypeSafeMatcher<ScaleEvent>() {
        override fun matchesSafely(event: ScaleEvent): Boolean {
            return (event.currentSpanXY.x - currentSpanXY.x) < EPSILON &&
                (event.currentSpanXY.y - currentSpanXY.y) < EPSILON
        }

        override fun describeTo(description: Description) {
            description.appendText("currentSpanXY=$currentSpanXY")
        }
    }

fun hasCurrentSpan(currentSpan: Float): Matcher<ScaleEvent> =
    object : TypeSafeMatcher<ScaleEvent>() {
        override fun matchesSafely(event: ScaleEvent): Boolean {
            return (event.currentSpan - currentSpan) < EPSILON
        }

        override fun describeTo(description: Description) {
            description.appendText("currentSpan=$currentSpan")
        }
    }

fun hasPreviousSpanXY(previousSpanXY: PointF): Matcher<ScaleEvent> =
    object : TypeSafeMatcher<ScaleEvent>() {
        override fun matchesSafely(event: ScaleEvent): Boolean {
            return (event.previousSpanXY.x - previousSpanXY.x) < EPSILON &&
                (event.previousSpanXY.y - previousSpanXY.y) < EPSILON
        }

        override fun describeTo(description: Description) {
            description.appendText("previousSpanXY=$previousSpanXY")
        }
    }

fun hasPreviousSpan(previousSpan: Float): Matcher<ScaleEvent> =
    object : TypeSafeMatcher<ScaleEvent>() {
        override fun matchesSafely(event: ScaleEvent): Boolean {
            return (event.previousSpan - previousSpan) < EPSILON
        }

        override fun describeTo(description: Description) {
            description.appendText("previousSpan=$previousSpan")
        }
    }

fun hasEventTime(eventTime: Long): Matcher<ScaleEvent> =
    object : TypeSafeMatcher<ScaleEvent>() {
        override fun matchesSafely(event: ScaleEvent): Boolean {
            return event.eventTime == eventTime
        }

        override fun describeTo(description: Description) {
            description.appendText("eventTime=$eventTime")
        }
    }

fun hasTimeDelta(timeDelta: Long): Matcher<ScaleEvent> =
    object : TypeSafeMatcher<ScaleEvent>() {
        override fun matchesSafely(event: ScaleEvent): Boolean {
            return event.timeDelta == timeDelta
        }

        override fun describeTo(description: Description) {
            description.appendText("timeDelta=$timeDelta")
        }
    }
