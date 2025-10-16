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

import android.view.ScaleGestureDetector
import android.view.ScaleGestureDetector.OnScaleGestureListener
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class TestScaleGestureListener(
    val events: LinkedBlockingQueue<ScaleEvent> = LinkedBlockingQueue<ScaleEvent>(),
    val onScaleBeginReturnValue: Boolean = true,
    val onScaleReturnValue: Boolean = true,
) : OnScaleGestureListener {
    override fun onScale(detector: ScaleGestureDetector): Boolean {
        events.add(scaleEvent(ScaleEventType.SCALE, detector))
        return onScaleReturnValue
    }

    override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
        events.add(scaleEvent(ScaleEventType.SCALE_BEGIN, detector))
        return onScaleBeginReturnValue
    }

    override fun onScaleEnd(detector: ScaleGestureDetector) {
        events.add(scaleEvent(ScaleEventType.SCALE_END, detector))
    }

    fun pollEvent(): ScaleEvent {
        return events.poll(5, TimeUnit.SECONDS)
    }
}
