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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Provides utilities to listen to lifecycle events of the {@link TestAppFunctionService}.
 */
public class TestAppFunctionServiceLifecycleReceiver extends BroadcastReceiver {
    private static final String ACTION_SERVICE_ON_CREATE = "oncreate";
    private static final String ACTION_SERVICE_ON_DESTROY = "ondestroy";

    private static volatile CountDownLatch sOnCreateLatch = new CountDownLatch(1);
    private static volatile CountDownLatch sOnDestroyedLatch = new CountDownLatch(1);

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ACTION_SERVICE_ON_CREATE.equals(intent.getAction())) {
            sOnCreateLatch.countDown();
        } else if (ACTION_SERVICE_ON_DESTROY.equals(intent.getAction())) {
            sOnDestroyedLatch.countDown();
        }
    }

    /**
     * Resets the latch and enables another wait cycle. Should never call this method with any
     * other methods in parallel.
     */
    public static void reset() {
        sOnCreateLatch = new CountDownLatch(1);
        sOnDestroyedLatch = new CountDownLatch(1);
    }

    /**
     * Blocks the current thread until {@link TestAppFunctionService#onDestroy()} is invoked, or the
     * specified timeout elapses.
     *
     * @param timeout The duration to wait for.
     * @param unit    The unit of time for the timeout value.
     * @return True if the onDestroy was invoked within the timeout, false otherwise.
     * @throws InterruptedException If the current thread is interrupted while waiting.
     */
    public static boolean waitForServiceOnDestroy(long timeout, TimeUnit unit)
            throws InterruptedException {
        return sOnDestroyedLatch.await(timeout, unit);
    }

    /**
     * Blocks the current thread until {@link TestAppFunctionService#onCreate()} is invoked, or the
     * specified timeout elapses.
     *
     * @param timeout The duration to wait for.
     * @param unit    The unit of time for the timeout value.
     * @return True if the onCreate was invoked within the timeout, false otherwise.
     * @throws InterruptedException If the current thread is interrupted while waiting.
     */
    public static boolean waitForServiceOnCreate(long timeout, TimeUnit unit)
            throws InterruptedException {
        return sOnCreateLatch.await(timeout, unit);
    }

    /** Notifies that {@link TestAppFunctionService} is created. */
    public static void notifyOnCreateInvoked(Context context) {
        notifyEvent(context, ACTION_SERVICE_ON_CREATE);
    }

    /** Notifies that {@link TestAppFunctionService} is destroyed. */
    public static void notifyOnDestroyInvoked(Context context) {
        notifyEvent(context, ACTION_SERVICE_ON_DESTROY);
    }

    private static void notifyEvent(Context context, String action) {
        Intent intent = new Intent(context, TestAppFunctionServiceLifecycleReceiver.class);
        intent.setAction(action);
        intent.setFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        context.sendBroadcast(intent);
    }
}
