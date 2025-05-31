/*
 * Copyright (C) 2018 The Android Open Source Project
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

package android.content.cts;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.app.ActivityManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.SystemClock;
import android.platform.test.annotations.AppModeSdkSandbox;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
@RunWith(AndroidJUnit4.class)
public final class ContextMoreTest {
    private static final int SLEEP_DURATION_MS = 10 * 1000;

    /**
     * Test for {@link Context#getSystemService)}.
     *
     * <p>Call it repeatedly from multiple threads, and: - Make sure
     * getSystemService(ActivityManager) will always return non-null. - If ContextImpl.mServiceCache
     * is accessible via reflection, clear it once in a while and make sure getSystemService() still
     * returns non-null.
     */
    @LargeTest
    @Test
    public void testGetSystemService_multiThreaded() throws Exception {
        // # of times the tester Runnable has been executed.
        final AtomicInteger totalCount = new AtomicInteger(0);

        // # of times the tester Runnable has failed.
        final AtomicInteger failCount = new AtomicInteger(0);

        // Run the threads until this becomes true.
        final AtomicBoolean stop = new AtomicBoolean(false);

        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final Object[] serviceCache = findServiceCache(context);
        if (serviceCache == null) {
            assertWithMessage("mServiceCache not found.").fail();
        }

        final Runnable tester =
                () -> {
                    for (; ; ) {
                        final int pass = totalCount.incrementAndGet();

                        final Object service = context.getSystemService(ActivityManager.class);
                        if (service == null) {
                            failCount.incrementAndGet(); // Fail!
                        }

                        if (stop.get()) {
                            return;
                        }

                        // Yield the CPU.
                        SystemClock.sleep(0);

                        // Once in a while, force clear mServiceCache.
                        if ((serviceCache != null) && ((pass % 7) == 0)) {
                            Arrays.fill(serviceCache, null);
                        }
                    }
                };

        final int NUM_THREADS = 20;

        // Create and start the threads...
        final Thread[] threads = new Thread[NUM_THREADS];
        for (int i = 0; i < NUM_THREADS; i++) {
            threads[i] = new Thread(tester);
        }
        for (int i = 0; i < NUM_THREADS; i++) {
            threads[i].start();
        }

        SystemClock.sleep(SLEEP_DURATION_MS);

        stop.set(true);

        // Wait for them to stop...
        for (int i = 0; i < NUM_THREADS; i++) {
            threads[i].join();
        }

        assertThat(failCount.get()).isEqualTo(0);
        assertWithMessage(
                        "totalCount must be bigger than "
                                + NUM_THREADS
                                + " but was "
                                + totalCount.get())
                .that(totalCount.get())
                .isGreaterThan(NUM_THREADS);
    }

    /** Find a field by name using reflection. */
    private static Object readServiceCacheField(Object instance) {
        try {
            final Field field = instance.getClass().getDeclaredField("mServiceCache");
            field.setAccessible(true);
            return field.get(instance);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            return null;
        }
    }

    /**
     * Try to find the mServiceCache field from a Context. Returns null if none found.
     */
    private static Object[] findServiceCache(Context context) {
        // Find the base context.
        while (context instanceof ContextWrapper) {
            context = ((ContextWrapper) context).getBaseContext();
        }
        // Try to find the mServiceCache field.
        final Object serviceCache = readServiceCacheField(context);
        if (serviceCache instanceof Object[]) {
            return (Object[]) serviceCache;
        }
        return null;
    }
}
