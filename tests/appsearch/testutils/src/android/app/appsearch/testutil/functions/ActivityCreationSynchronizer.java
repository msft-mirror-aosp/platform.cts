/*
 * Copyright (C) 2024 The Android Open Source Project
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
package android.app.appsearch.testutil.functions;

import android.app.Activity;
import android.os.Bundle;

import androidx.annotation.Nullable;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** An activity class that enables waiting for its own creation. */
public class ActivityCreationSynchronizer extends Activity {

    private static volatile CountDownLatch sLatch = new CountDownLatch(1);

    /**
     * Called within the Activity's onCreate() lifecycle method.
     * Signals that the Activity has been fully created.
     */
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sLatch.countDown();
        finish();
    }

    /**
     * Resets the latch and enables another wait cycle. Should never call this with
     * {@link #waitForActivityCreated} in parallel.
     */
    public static void reset() {
        sLatch = new CountDownLatch(1);
    }

    /**
     * Blocks the current thread until the Activity is created or the specified timeout elapses.
     * Should never call this with {@link #reset()} in parallel.
     *
     * @param timeout The duration to wait for Activity creation.
     * @param unit    The unit of time for the timeout value.
     * @return True if the Activity was created within the timeout, false otherwise.
     * @throws InterruptedException If the current thread is interrupted while waiting.
     */
    public static boolean waitForActivityCreated(long timeout, TimeUnit unit)
            throws InterruptedException {
        return sLatch.await(timeout, unit);
    }
}
