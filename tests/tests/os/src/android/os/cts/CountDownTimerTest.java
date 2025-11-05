/*
 * Copyright (C) 2008 The Android Open Source Project
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

package android.os.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.os.CountDownTimer;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class CountDownTimerTest {
    private static final long OFFSET = 200;
    private static final long MILLISINFUTURE = 4500;
    private static final long INTERVAL = 1000;

    private long mStartTime;
    private TestCountdownTimer mTimer;

    static class TestCountdownTimer extends CountDownTimer {

        final CountDownLatch mLatch = new CountDownLatch(1);
        final ArrayList<Long> mTickTimes = new ArrayList<>();

        TestCountdownTimer() {
            super(MILLISINFUTURE, INTERVAL);
        }

        @Override
        public void onTick(long millisUntilFinished) {
            mTickTimes.add(System.currentTimeMillis());
        }

        @Override
        public void onFinish() {
            mLatch.countDown();
        }
    }

    @Before
    public void setUp() throws Exception {
        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(() -> mTimer = new TestCountdownTimer());
        mStartTime = System.currentTimeMillis();
        mTimer.start();
    }

    @Test
    public void testCountDownTimer() {
        int count = (int) (MILLISINFUTURE / INTERVAL);
        final long TIMEOUT_MSEC = MILLISINFUTURE + INTERVAL + OFFSET * count;
        assertTrue(waitForAction(TIMEOUT_MSEC));
        assertEqualsTickTime(mTimer.mTickTimes, OFFSET);
    }

    @Test
    public void testCountDownTimerCancel() {
        final long DELAY_MSEC = INTERVAL + OFFSET;
        assertTrue(DELAY_MSEC < MILLISINFUTURE);
        assertFalse(waitForAction(DELAY_MSEC));
        mTimer.cancel();
        final long TIMEOUT_MSEC = MILLISINFUTURE + INTERVAL;
        assertFalse(waitForAction(TIMEOUT_MSEC));
        // it will call onTick after start countDownTimer, so count plus 1;
        int count = Long.valueOf(DELAY_MSEC / INTERVAL).intValue() + 1;
        assertEquals(count, mTimer.mTickTimes.size());
        assertEqualsTickTime(mTimer.mTickTimes, OFFSET);
    }

    private void assertEqualsTickTime(ArrayList<Long> tickTimes, long offset) {
        for (int i = 0; i < tickTimes.size(); i++) {
            long tickTime = tickTimes.get(i);
            long expecTickTime = mStartTime + i * INTERVAL;
            assertTrue(Math.abs(expecTickTime - tickTime) < offset);
        }
    }

    /**
     * Wait for an action to complete.
     *
     * @param time The time to wait.
     */
    private boolean waitForAction(long time) {
        try {
            return mTimer.mLatch.await(time, TimeUnit.MILLISECONDS);
        } catch (final InterruptedException e) {
            fail("error occurs when wait for an action: " + e);
            return false;
        }
    }
}
