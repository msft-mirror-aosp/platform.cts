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
import android.content.Context;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.os.SystemClock;
import android.server.wm.WindowManagerStateHelper;
import android.view.Display;

import com.android.cts.input.UinputTouchDevice;
import com.android.cts.input.UinputTouchScreen;

/** Helper class for injecting a sequence of motion event to simulate a gesture swipe. */
public class BackGestureTouchHelper implements AutoCloseable {

    /**
     * Do a back gesture and trigger a back event from it. Attempt to simulate human behavior, so
     * don't wait for animations.
     */
    static void triggerBackEventByGesture(WindowManagerStateHelper wmState, int displayId) {
        final Rect bounds = wmState.getDisplay(displayId).getDisplayRect();
        int midHeight = bounds.top + bounds.height() / 2;
        int midWidth = bounds.left + bounds.width() / 2;
        try (BackGestureTouchHelper session = new BackGestureTouchHelper(displayId)) {
            session.quickSwipe(0, midHeight, midWidth, midHeight);
        }
    }

    private static final int INJECT_INPUT_DELAY_MILLIS = 5;
    private int mStartX;
    private int mStartY;
    private int mEndX;
    private int mEndY;
    private long mStartDownTime = -1;
    private long mNextEventTime = -1;
    private final UinputTouchScreen mTouchScreen;
    private UinputTouchDevice.Pointer mPointer;

    public BackGestureTouchHelper(int displayId) {
        final Instrumentation instrumentation = getInstrumentation();
        final Context context = instrumentation.getContext();
        final Display display =
                context.getSystemService(DisplayManager.class).getDisplay(displayId);
        mTouchScreen = new UinputTouchScreen(instrumentation, display);
    }

    @Override
    public void close() {
        if (mTouchScreen != null) {
            mTouchScreen.close();
        }
    }

    public void beginSwipe(int startX, int startY) {
        mStartX = startX;
        mStartY = startY;
        mStartDownTime = SystemClock.uptimeMillis();
        mPointer = mTouchScreen.touchDown(startX, startY);
    }

    public void continueSwipe(int endX, int endY) {
        final int steps = 10;
        if (mPointer == null) {
            throw new RuntimeException("Haven't start");
        }
        mEndX = endX;
        mEndY = endY;
        // inject in every INJECT_INPUT_DELAY_MILLIS ms.
        final int delayMillis = INJECT_INPUT_DELAY_MILLIS;
        mNextEventTime = mStartDownTime + delayMillis;
        final int stepGapX = (mEndX - mStartX) / steps;
        final int stepGapY = (mEndY - mStartY) / steps;
        for (int i = 0; i < steps; i++) {
            SystemClock.sleep(delayMillis);
            final int nextX = mStartX + stepGapX * i;
            final int nextY = mStartY + stepGapY * i;
            mPointer.moveTo(nextX, nextY);
            mNextEventTime += delayMillis;
        }
    }

    public void finishSwipe() {
        if (mPointer == null) {
            return;
        }
        mPointer.moveTo(mEndX, mEndY);
        mPointer.lift();
        resetSwipe();
    }

    public void cancelSwipe() {
        if (mPointer == null) {
            return;
        }
        mPointer.close();
        resetSwipe();
    }

    void quickSwipe(int startX, int startY, int endX, int endY) {
        beginSwipe(startX, startY);
        continueSwipe(endX, endY);
        SystemClock.sleep(INJECT_INPUT_DELAY_MILLIS);
        finishSwipe();
    }

    private void resetSwipe() {
        mStartDownTime = -1;
        mNextEventTime = -1;
        mPointer = null;
    }

    public boolean isSwipe() {
        return mStartDownTime > 0 || mNextEventTime > 0;
    }
}
