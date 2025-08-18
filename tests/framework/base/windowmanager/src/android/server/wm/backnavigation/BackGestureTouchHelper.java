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
package android.server.wm.backnavigation;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import android.app.Instrumentation;
import android.graphics.Rect;
import android.os.SystemClock;
import android.server.wm.WindowManagerStateHelper;
import android.view.InputDevice;
import android.view.MotionEvent;

/** Helper class for injecting a sequence of motion event to simulate a gesture swipe. */
public class BackGestureTouchHelper implements AutoCloseable {

    /**
     * Do a back gesture and trigger a back event from it. Attempt to simulate human behavior, so
     * don't wait for animations.
     */
    public void triggerBackEventByGesture(WindowManagerStateHelper wmState) {
        if (mSwiping) return;
        final Rect bounds = wmState.getDisplay(mDisplayId).getDisplayRect();
        int midHeight = bounds.top + bounds.height() / 2;
        int midWidth = bounds.left + bounds.width() / 2;
        quickSwipe(0, midHeight, midWidth, midHeight);
    }

    private static final int GESTURE_DURATION_MS = 50;
    private final Instrumentation mInstrumentation;
    private final int mDisplayId;
    private long mDownTime;
    private int mStartX;
    private int mStartY;
    private int mEndX;
    private int mEndY;
    private boolean mSwiping = false;

    public BackGestureTouchHelper(int displayId) {
        mInstrumentation = getInstrumentation();
        mDisplayId = displayId;
    }

    @Override
    public void close() {
        if (mSwiping) {
            cancelSwipe();
        }
    }

    public void beginSwipe(int startX, int startY) {
        if (mSwiping) return;
        mStartX = startX;
        mStartY = startY;
        mDownTime = SystemClock.uptimeMillis();
        sendPointer(mDownTime, mDownTime, MotionEvent.ACTION_DOWN, mStartX, mStartY);
        mSwiping = true;
    }

    public void continueSwipe(int endX, int endY) {
        if (!mSwiping) return;
        final int steps = 10;
        final int stepDuration = GESTURE_DURATION_MS / steps;
        long eventTime = mDownTime;

        mEndX = endX;
        mEndY = endY;
        final int stepGapX = (mEndX - mStartX) / steps;
        final int stepGapY = (mEndY - mStartY) / steps;
        for (int i = 0; i < steps; i++) {
            eventTime += stepDuration;
            final int nextX = mStartX + stepGapX * i;
            final int nextY = mStartY + stepGapY * i;
            sendPointer(mDownTime, eventTime, MotionEvent.ACTION_MOVE, nextX, nextY);
        }
    }

    public void finishSwipe() {
        if (!mSwiping) return;
        sendPointer(
                mDownTime, mDownTime + GESTURE_DURATION_MS, MotionEvent.ACTION_UP, mEndX, mEndY);
        mSwiping = false;
    }

    public void cancelSwipe() {
        if (!mSwiping) return;
        sendPointer(
                mDownTime,
                mDownTime + GESTURE_DURATION_MS,
                MotionEvent.ACTION_CANCEL,
                mEndX,
                mEndY);
        mSwiping = false;
    }

    private void quickSwipe(int startX, int startY, int endX, int endY) {
        beginSwipe(startX, startY);
        continueSwipe(endX, endY);
        finishSwipe();
    }

    private void sendPointer(long downTime, long eventTime, int action, float x, float y) {
        final MotionEvent.PointerProperties[] pointerProperties = {
            new MotionEvent.PointerProperties()
        };
        pointerProperties[0].id = 0;
        pointerProperties[0].toolType = MotionEvent.TOOL_TYPE_FINGER;

        final MotionEvent.PointerCoords[] pointerCoords = {new MotionEvent.PointerCoords()};
        pointerCoords[0].x = x;
        pointerCoords[0].y = y;

        final MotionEvent event =
                MotionEvent.obtain(
                        downTime,
                        eventTime,
                        action,
                        1, // pointerCount
                        pointerProperties,
                        pointerCoords,
                        0, // metaState
                        0, // buttonState
                        1.0f, // xPrecision
                        1.0f, // yPrecision
                        mDisplayId + 1000, // deviceId
                        0, // edgeFlags
                        InputDevice.SOURCE_TOUCHSCREEN,
                        mDisplayId);
        event.setDisplayId(mDisplayId);
        mInstrumentation.getUiAutomation().injectInputEvent(event, true);
    }
}
