/*
 * Copyright (C) 2026 The Android Open Source Project
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

package android.os.cts.multisensory.util;

import android.os.SystemClock;
import android.os.Vibrator;

import java.util.function.BooleanSupplier;

/** A listener for vibrator state changes that can be used to wait for vibrations to occur. */
public class MultisensoryVibratorListener implements Vibrator.OnVibratorStateChangedListener {

    private static final long TIMEOUT_MILLIS = 3_000L;

    private boolean mIsCurrentlyVibrating = false;
    private boolean mEverVibrated = false;

    @Override
    public synchronized void onVibratorStateChanged(boolean isVibrating) {
        mIsCurrentlyVibrating = isVibrating;
        if (isVibrating) {
            mEverVibrated = true;
        }
        notifyAll();
    }

    /** Reset the internal state of the listener */
    public synchronized void reset() {
        mEverVibrated = false;
        mIsCurrentlyVibrating = false;
    }

    /**
     * Wait for the vibrator to be idle (not vibrating).
     *
     * @return true if the vibrator is idle within the timeout, false otherwise.
     */
    public synchronized boolean waitForIdle() throws InterruptedException {
        return waitForConditionWithTimeout(() -> !mIsCurrentlyVibrating);
    }

    /**
     * Wait for any vibration to be detected.
     *
     * @return true if a vibration was detected within the timeout, false otherwise.
     */
    public synchronized boolean awaitAnyVibration() throws InterruptedException {
        return waitForConditionWithTimeout(() -> mEverVibrated);
    }

    private synchronized boolean waitForConditionWithTimeout(BooleanSupplier condition)
            throws InterruptedException {
        long now = SystemClock.uptimeMillis();
        long deadline = now + TIMEOUT_MILLIS;
        while (!condition.getAsBoolean() && now < deadline) {
            wait(deadline - now);
            now = SystemClock.uptimeMillis();
        }
        return condition.getAsBoolean();
    }
}
