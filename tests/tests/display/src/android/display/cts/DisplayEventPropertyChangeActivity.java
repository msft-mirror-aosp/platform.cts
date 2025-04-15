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

package android.display.cts;

import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;
import android.view.Window;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.Executors;

/** An Activity to help with frame rate testing. */
public class DisplayEventPropertyChangeActivity extends Activity {
    private static final String TAG = "DisplayEventPropertyChangeActivity";
    private static final long FRAME_RATE_SWITCH_GRACE_PERIOD_NANOSECONDS = 2 * 1_000_000_000L;
    private static final long STABLE_FRAME_RATE_WAIT_NANOSECONDS = 1 * 1_000_000_000L;
    private static final long POST_BUFFER_INTERVAL_NANOSECONDS = 500_000_000L;
    private static final int PRECONDITION_WAIT_MAX_ATTEMPTS = 5;
    private static final long PRECONDITION_WAIT_TIMEOUT_NANOSECONDS = 20 * 1_000_000_000L;
    private static final long PRECONDITION_VIOLATION_WAIT_TIMEOUT_NANOSECONDS = 3 * 1_000_000_000L;
    private static final float FRAME_RATE_TOLERANCE = 0.01f;
    private static final float FPS_TOLERANCE_FOR_FRAME_RATE_OVERRIDE = 5;

    private DisplayManager mDisplayManager;
    private SurfaceView mSurfaceView;
    private final Object mLock = new Object();
    private Surface mSurface = null;
    private float mReportedDisplayRefreshRate;
    private ArrayList<Float> mRefreshRateChangedEvents = new ArrayList<Float>();

    private long mLastBufferPostTime;

    private enum ActivityState {
        RUNNING,
        PAUSED,
        DESTROYED
    }

    private ActivityState mActivityState;

    SurfaceHolder.Callback mSurfaceHolderCallback =
            new SurfaceHolder.Callback() {
                @Override
                public void surfaceCreated(SurfaceHolder holder) {
                    synchronized (mLock) {
                        mSurface = holder.getSurface();
                        mLock.notify();
                    }
                }

                @Override
                public void surfaceDestroyed(SurfaceHolder holder) {
                    synchronized (mLock) {
                        mSurface = null;
                        mLock.notify();
                    }
                }

                @Override
                public void surfaceChanged(
                        SurfaceHolder holder, int format, int width, int height) {}
            };

    DisplayManager.DisplayListener mDisplayListener =
            new DisplayManager.DisplayListener() {
                @Override
                public void onDisplayAdded(int displayId) {}

                @Override
                public void onDisplayChanged(int displayId) {
                    synchronized (mLock) {
                        float refreshRate = getDisplay().getRefreshRate();
                        if (refreshRate != mReportedDisplayRefreshRate) {
                            Log.i(
                                    TAG,
                                    String.format(
                                            "Frame rate changed: (%.2f) --> (%.2f)",
                                            mReportedDisplayRefreshRate, refreshRate));
                            mReportedDisplayRefreshRate = refreshRate;
                            mRefreshRateChangedEvents.add(refreshRate);
                            mLock.notify();
                        }
                    }
                }

                @Override
                public void onDisplayRemoved(int displayId) {}
            };

    private static class PreconditionViolatedException extends RuntimeException {}

    private static class FrameRateTimeoutException extends RuntimeException {
        FrameRateTimeoutException(float appRequestedFrameRate, float deviceRefreshRate) {
            this.appRequestedFrameRate = appRequestedFrameRate;
            this.deviceRefreshRate = deviceRefreshRate;
        }

        public float appRequestedFrameRate;
        public float deviceRefreshRate;
    }

    private void postBufferToSurface(int color) {
        mLastBufferPostTime = System.nanoTime();
        Canvas canvas = mSurface.lockCanvas(null);
        canvas.drawColor(color);
        mSurface.unlockCanvasAndPost(canvas);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        synchronized (mLock) {
            mDisplayManager = getSystemService(DisplayManager.class);
            mReportedDisplayRefreshRate = getDisplay().getRefreshRate();
            mDisplayManager.registerDisplayListener(
                    Executors.newSingleThreadExecutor(),
                    DisplayManager.EVENT_TYPE_DISPLAY_REFRESH_RATE,
                    mDisplayListener);
            mSurfaceView = new SurfaceView(this);
            mSurfaceView.setWillNotDraw(false);
            setContentView(
                    mSurfaceView,
                    new ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT));
            mSurfaceView.getHolder().addCallback(mSurfaceHolderCallback);
            Window window = getWindow();
            window.setFrameRatePowerSavingsBalanced(false);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mDisplayManager.unregisterDisplayListener(mDisplayListener);
        synchronized (mLock) {
            mActivityState = ActivityState.DESTROYED;
            mLock.notify();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        synchronized (mLock) {
            mActivityState = ActivityState.PAUSED;
            mLock.notify();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        synchronized (mLock) {
            mActivityState = ActivityState.RUNNING;
            mLock.notify();
        }
    }

    private static boolean frameRatesEqual(float frameRate1, float frameRate2) {
        return Math.abs(frameRate1 - frameRate2) <= FRAME_RATE_TOLERANCE;
    }

    private static boolean frameRatesMatchesOverride(float frameRate1, float frameRate2) {
        return Math.abs(frameRate1 - frameRate2) <= FPS_TOLERANCE_FOR_FRAME_RATE_OVERRIDE;
    }

    // Waits until our SurfaceHolder has a surface and the activity is resumed.
    private void waitForPreconditions() throws InterruptedException {
        assertNotSame(
                "Activity was unexpectedly destroyed", mActivityState, ActivityState.DESTROYED);
        if (mSurface == null || mActivityState != ActivityState.RUNNING) {
            Log.i(
                    TAG,
                    String.format(
                            "Waiting for preconditions. Have surface? %b. Activity resumed? %b.",
                            mSurface != null, mActivityState == ActivityState.RUNNING));
        }
        long nowNanos = System.nanoTime();
        long endTimeNanos = nowNanos + PRECONDITION_WAIT_TIMEOUT_NANOSECONDS;
        while (mSurface == null || mActivityState != ActivityState.RUNNING) {
            long timeRemainingMillis = (endTimeNanos - nowNanos) / 1_000_000;
            assertTrue(
                    String.format(
                            "Timed out waiting for preconditions. Have surface? %b."
                                    + " Activity resumed? %b.",
                            mSurface != null, mActivityState == ActivityState.RUNNING),
                    timeRemainingMillis > 0);
            mLock.wait(timeRemainingMillis);
            assertNotSame(
                    "Activity was unexpectedly destroyed", mActivityState, ActivityState.DESTROYED);
            nowNanos = System.nanoTime();
        }
    }

    // Returns true if we encounter a precondition violation, false otherwise.
    private boolean waitForPreconditionViolation() throws InterruptedException {
        assertNotSame(
                "Activity was unexpectedly destroyed", mActivityState, ActivityState.DESTROYED);
        long nowNanos = System.nanoTime();
        long endTimeNanos = nowNanos + PRECONDITION_VIOLATION_WAIT_TIMEOUT_NANOSECONDS;
        while (mSurface != null && mActivityState == ActivityState.RUNNING) {
            long timeRemainingMillis = (endTimeNanos - nowNanos) / 1_000_000;
            if (timeRemainingMillis <= 0) {
                break;
            }
            mLock.wait(timeRemainingMillis);
            assertNotSame(
                    "Activity was unexpectedly destroyed", mActivityState, ActivityState.DESTROYED);
            nowNanos = System.nanoTime();
        }
        return mSurface == null || mActivityState != ActivityState.RUNNING;
    }

    private void verifyPreconditions() {
        if (mSurface == null || mActivityState != ActivityState.RUNNING) {
            throw new PreconditionViolatedException();
        }
    }

    // Returns true if we reached waitUntilNanos, false if some other event occurred.
    private boolean waitForEvents(long waitUntilNanos) throws InterruptedException {
        mRefreshRateChangedEvents.clear();
        long nowNanos = System.nanoTime();
        while (nowNanos < waitUntilNanos) {
            long surfacePostTime = mLastBufferPostTime + POST_BUFFER_INTERVAL_NANOSECONDS;
            long timeoutNs = Math.min(waitUntilNanos, surfacePostTime) - nowNanos;
            long timeoutMs = timeoutNs / 1_000_000L;
            int remainderNs = (int) (timeoutNs % 1_000_000L);
            // Don't call wait(0, 0) - it blocks indefinitely.
            if (timeoutMs > 0 || remainderNs > 0) {
                mLock.wait(timeoutMs, remainderNs);
            }
            nowNanos = System.nanoTime();
            verifyPreconditions();
            if (!mRefreshRateChangedEvents.isEmpty()) {
                return false;
            }
            if (nowNanos >= surfacePostTime) {
                postBufferToSurface(Color.RED);
            }
        }
        return true;
    }

    private void waitForRefreshRateChange(float expectedRefreshRate) throws InterruptedException {
        long nowNanos = System.nanoTime();
        long gracePeriodEndTimeNanos = nowNanos + FRAME_RATE_SWITCH_GRACE_PERIOD_NANOSECONDS;
        while (true) {
            // Wait until we switch to the expected refresh rate
            while (!frameRatesEqual(mReportedDisplayRefreshRate, expectedRefreshRate)
                    && !waitForEvents(gracePeriodEndTimeNanos)) {
                // Empty
            }
            nowNanos = System.nanoTime();
            if (nowNanos >= gracePeriodEndTimeNanos) {
                throw new FrameRateTimeoutException(
                        expectedRefreshRate, mReportedDisplayRefreshRate);
            }

            // We've switched to a compatible frame rate. Now wait for a while to see if we stay at
            // that frame rate.
            long endTimeNanos = nowNanos + STABLE_FRAME_RATE_WAIT_NANOSECONDS;
            while (endTimeNanos > nowNanos) {
                if (waitForEvents(endTimeNanos)) {
                    Log.i(
                            TAG,
                            String.format(
                                    "Stable frame rate %.2f verified",
                                    mReportedDisplayRefreshRate));
                    return;
                }
                nowNanos = System.nanoTime();
                if (!mRefreshRateChangedEvents.isEmpty()) {
                    break;
                }
            }
        }
    }

    private void observe(float expectedFrameRate, String condition) {
        assertTrue(
                String.format(
                        "%s: Display.getRefreshRate() doesn't match the "
                                + "current refresh. expected: %.2f observed: %.2f",
                        condition, expectedFrameRate, mReportedDisplayRefreshRate),
                frameRatesMatchesOverride(mReportedDisplayRefreshRate, expectedFrameRate));
    }

    private void changeFrameRate(float initialRefreshRate) throws InterruptedException {
        float halfFrameRate = initialRefreshRate / 2;
        mSurface.setFrameRate(
                halfFrameRate,
                Surface.FRAME_RATE_COMPATIBILITY_DEFAULT,
                Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS);
        waitForRefreshRateChange(halfFrameRate);
        observe(halfFrameRate, "setFrameRate(default)");

        mSurface.setFrameRate(
                0,
                Surface.FRAME_RATE_COMPATIBILITY_DEFAULT,
                Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS);
        waitForRefreshRateChange(initialRefreshRate);
        observe(initialRefreshRate, "Reset");
    }

    // The activity being intermittently paused/resumed has been observed to
    // cause test failures in practice, so we run the test with retry logic.
    public void testFrameRateOverride(float initialRefreshRate)
            throws InterruptedException, IOException {
        synchronized (mLock) {
            Log.i(
                    TAG,
                    "testFrameRateOverride started with initial refresh rate "
                            + initialRefreshRate);
            int attempts = 0;
            boolean testPassed = false;
            try {
                while (!testPassed) {
                    waitForPreconditions();
                    try {
                        changeFrameRate(initialRefreshRate);
                        testPassed = true;
                    } catch (PreconditionViolatedException exc) {
                        // The logic below will retry if we're below max attempts.
                    } catch (FrameRateTimeoutException exc) {
                        // Sometimes we get a test timeout failure before we get the
                        // notification that the activity was paused, and it was the pause that
                        // caused the timeout failure. Wait for a bit to see if we get notified
                        // of a precondition violation, and if so, retry the test. Otherwise
                        // fail.
                        assertTrue(
                                String.format(
                                        "Timed out waiting for a stable and compatible frame"
                                                + " rate. requested=%.2f received=%.2f.",
                                        exc.appRequestedFrameRate, exc.deviceRefreshRate),
                                waitForPreconditionViolation());
                    }

                    if (!testPassed) {
                        Log.i(
                                TAG,
                                String.format(
                                        "Preconditions violated while running the test."
                                                + " Have surface? %b. Activity resumed? %b.",
                                        mSurface != null, mActivityState == ActivityState.RUNNING));
                        attempts++;
                        assertTrue(
                                String.format(
                                        "Exceeded %d precondition wait attempts. Giving up.",
                                        PRECONDITION_WAIT_MAX_ATTEMPTS),
                                attempts < PRECONDITION_WAIT_MAX_ATTEMPTS);
                    }
                }
            } finally {
                if (testPassed) {
                    Log.i(TAG, "**** PASS ****");
                } else {
                    Log.i(TAG, "**** FAIL ****");
                }
            }
        }
    }
}
