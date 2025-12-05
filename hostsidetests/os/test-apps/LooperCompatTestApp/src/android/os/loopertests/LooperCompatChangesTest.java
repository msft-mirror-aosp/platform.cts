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

package android.os.loopercompattests;

import static org.junit.Assert.assertTrue;

import android.os.Handler;
import android.os.HandlerThread;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.permissions.annotations.EnsureHasPermission;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tests for {@link android.os.Looper} compat changes.
 *
 * <p>These test methods have additional setup and post run checks done host side by {@link
 * android.os.cts.LooperCompatChangesHostTest}.
 *
 * <p>The setup adds an override for the change id being tested, and the post run step checks if
 * that change id has been logged to statsd.
 */
@RunWith(BedsteadJUnit4.class)
public final class LooperCompatChangesTest {
    private static final String LOG_COMPAT_CHANGE = "android.permission.LOG_COMPAT_CHANGE";
    private static final String READ_COMPAT_CHANGE_CONFIG =
            "android.permission.READ_COMPAT_CHANGE_CONFIG";
    public static final String OVERRIDE_COMPAT_CHANGE_CONFIG_ON_RELEASE_BUILD =
            "android.permission.OVERRIDE_COMPAT_CHANGE_CONFIG_ON_RELEASE_BUILD";
    private static final String INTERACT_ACROSS_USERS_FULL =
            "android.permission.INTERACT_ACROSS_USERS_FULL";

    private void checkLooperInterruptCompat(String threadName, boolean interruptExpected)
            throws InterruptedException {
        final HandlerThread thread = new HandlerThread(threadName);
        thread.start();
        Handler handler = new Handler(thread.getLooper());
        final CountDownLatch testComplete = new CountDownLatch(1);
        final AtomicBoolean interruptSeen = new AtomicBoolean(!interruptExpected);

        try {
            handler.post(
                    () -> {
                        Thread.currentThread().interrupt();
                    });
            handler.post(
                    () -> {
                        interruptSeen.set(Thread.currentThread().isInterrupted());
                        testComplete.countDown();
                    });

            testComplete.await();
            assertTrue(interruptSeen.get() == interruptExpected);
        } finally {
            thread.quit();
        }
    }

    /* Test run by LooperCompatChangesHostTest.testLooperClearsThreadInterruptedEnabled */
    @Test
    @EnsureHasPermission({
        LOG_COMPAT_CHANGE,
        READ_COMPAT_CHANGE_CONFIG,
        OVERRIDE_COMPAT_CHANGE_CONFIG_ON_RELEASE_BUILD,
        INTERACT_ACROSS_USERS_FULL
    })
    public void checkLooperClearedInterrupts() throws InterruptedException {
        checkLooperInterruptCompat("checkLooperClearedInterrupts", false);
    }

    /* Test run by LooperCompatChangesHostTest.testLooperClearsThreadInterruptedDisabled */
    @Test
    @EnsureHasPermission({
        LOG_COMPAT_CHANGE,
        READ_COMPAT_CHANGE_CONFIG,
        OVERRIDE_COMPAT_CHANGE_CONFIG_ON_RELEASE_BUILD,
        INTERACT_ACROSS_USERS_FULL
    })
    public void checkLooperRetainedInterrupts() throws InterruptedException {
        checkLooperInterruptCompat("checkInterruptSeen", true);
    }
}
