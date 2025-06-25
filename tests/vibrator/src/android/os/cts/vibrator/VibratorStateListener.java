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

package android.os.cts.vibrator;

import android.os.SystemClock;
import android.os.Vibrator;

import java.util.function.BooleanSupplier;

/** Listener implementation for test assertions on the vibrator state. */
public final class VibratorStateListener implements Vibrator.OnVibratorStateChangedListener {
    private int mStartVibrationCount;
    private int mStopVibrationCount;
    private Boolean mInitialState;
    private boolean mIsVibrating;

    @Override
    public synchronized void onVibratorStateChanged(boolean isVibrating) {
        if (mInitialState == null) {
            mInitialState = isVibrating;
        } else if (isVibrating) {
            mStartVibrationCount++;
        } else {
            mStopVibrationCount++;
        }
        mIsVibrating = isVibrating;
        notifyAll();
    }

    /** Returns the first vibrator state received within given timeout. */
    public synchronized Boolean getInitialStateWithTimeout(long timeoutMs)
            throws InterruptedException {
        waitForCondition(() -> mInitialState != null, timeoutMs);
        return mInitialState;
    }

    /** Returns current vibrator state once it gets to the expected state or after timeout. */
    public synchronized boolean getCurrentStateWithTimeout(boolean expected, long timeoutMs)
            throws InterruptedException {
        waitForCondition(() -> mIsVibrating == expected, timeoutMs);
        return mIsVibrating;
    }

    public synchronized int getStartCount() {
        return mStartVibrationCount;
    }

    /**
     * Returns the number of times the state received was {@code true} (excluding the initial state)
     * once it gets to the expected minimum count or after timeout.
     */
    public synchronized int getStartCountWithTimeout(int minCount, long timeoutMs)
            throws InterruptedException {
        waitForCondition(() -> mStartVibrationCount >= minCount, timeoutMs);
        return mStartVibrationCount;
    }

    /**
     * Returns the number of times the state received was {@code false} (excluding the initial
     * state) once it gets to the expected minimum count or after timeout.
     */
    public synchronized int getStopCountWithTimeout(int minCount, long timeoutMs)
            throws InterruptedException {
        waitForCondition(() -> mStopVibrationCount >= minCount, timeoutMs);
        return mStopVibrationCount;
    }

    private synchronized void waitForCondition(BooleanSupplier condition, long timeoutMs)
            throws InterruptedException {
        long now = SystemClock.uptimeMillis();
        long deadline = now + timeoutMs;
        while (!condition.getAsBoolean() && now < deadline) {
            wait(deadline - now);
            now = SystemClock.uptimeMillis();
        }
    }
}
