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

package android.app.cts;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Instrumentation;
import android.os.Handler;
import android.os.Looper;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class InstrumentationThreadingTest {
    private static final int WAIT_TIME = 1000;

    private Instrumentation mInstrumentation;
    private volatile boolean mRunOnMainSyncResult;

    @Before
    public void setUp() throws Exception {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
    }

    @Test
    public void testWaitForIdle() throws Exception {
        MockRunnable mr = new MockRunnable();
        assertFalse(mr.isRunCalled());
        mInstrumentation.waitForIdle(mr);
        Thread.sleep(WAIT_TIME);
        assertTrue(mr.isRunCalled());
    }

    @Test
    public void testWaitForIdleSync() throws Exception {
        MockRunnable mr = new MockRunnable();
        assertFalse(mr.isRunCalled());
        new Handler(Looper.getMainLooper()).post(mr);
        mInstrumentation.waitForIdleSync();
        assertTrue(mr.isRunCalled());
    }

    @Test
    public void testRunOnMainSync() throws Exception {
        mRunOnMainSyncResult = false;
        mInstrumentation.runOnMainSync(() -> mRunOnMainSyncResult = true);
        mInstrumentation.waitForIdleSync();
        assertTrue(mRunOnMainSyncResult);
    }

    private static class MockRunnable implements Runnable {
        private boolean mIsRunCalled;

        public void run() {
            mIsRunCalled = true;
        }

        public boolean isRunCalled() {
            return mIsRunCalled;
        }
    }
}
