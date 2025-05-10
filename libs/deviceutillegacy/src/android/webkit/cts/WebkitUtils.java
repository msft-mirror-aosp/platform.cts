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

package android.webkit.cts;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import com.android.compatibility.common.util.PollingCheck;

import com.google.common.util.concurrent.SettableFuture;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Helper methods for common webkit test tasks.
 *
 * <p>
 * This should remain functionally equivalent to androidx.webkit.WebkitUtils.
 * Modifications to this class should be reflected in that class as necessary. See
 * http://go/modifying-webview-cts.
 */
public final class WebkitUtils {

    private static final String TAG = "WebViewCts";

    /**
     * Arbitrary timeout for tests. This is intended to be used with {@link TimeUnit#MILLISECONDS}
     * so that this can represent 20 seconds.
     *
     * <p class=note><b>Note:</b> only use this timeout value for the unexpected case, not for the
     * correct case, as this exceeds the time recommendation for {@link
     * androidx.test.filters.MediumTest}.
     */
    public static final long TEST_TIMEOUT_MS = 20000L; // 20s.

    // A handler for the main thread.
    private static final Handler sMainHandler = new Handler(Looper.getMainLooper());

    /**
     * Executes a callable asynchronously on the main thread, returning a future for the result.
     *
     * @param callable the {@link Callable} to execute.
     * @return a {@link Future} representing the result of {@code callable}.
     */
    public static <T> Future<T> onMainThread(final Callable<T> callable)  {
        final SettableFuture<T> future = SettableFuture.create();
        sMainHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    future.set(callable.call());
                } catch (Throwable t) {
                    future.setException(t);
                }
            }
        });
        return future;
    }

    /**
     * Executes a runnable asynchronously on the main thread.
     *
     * @param runnable the {@link Runnable} to execute.
     */
    public static void onMainThread(final Runnable runnable)  {
        sMainHandler.post(runnable);
    }

    /**
     * Executes a callable synchronously on the main thread, returning its result. This re-throws
     * any exceptions on the thread this is called from. This means callers may use {@link
     * org.junit.Assert} methods within the {@link Callable} if they invoke this method from the
     * instrumentation thread.
     *
     * <p class="note"><b>Note:</b> this should not be called from the UI thread.
     *
     * @param callable the {@link Callable} to execute.
     * @return the result of the {@link Callable}.
     */
    public static <T> T onMainThreadSync(final Callable<T> callable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new IllegalStateException("This cannot be called from the UI thread.");
        }
        return waitForFuture(onMainThread(callable));
    }

    /**
     * Executes a {@link Runnable} synchronously on the main thread. This is similar to {@link
     * android.app.Instrumentation#runOnMainSync(Runnable)}, with the main difference that this
     * re-throws exceptions on the calling thread. This is useful if {@code runnable} contains any
     * {@link org.junit.Assert} methods, or otherwise throws an Exception.
     *
     * <p class="note"><b>Note:</b> this should not be called from the UI thread.
     *
     * @param runnable the {@link Runnable} to execute.
     */
    public static void onMainThreadSync(final Runnable runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new IllegalStateException("This cannot be called from the UI thread.");
        }
        final SettableFuture<Void> exceptionPropagatingFuture = SettableFuture.create();
        onMainThread(new Runnable() {
            @Override
            public void run() {
                try {
                    runnable.run();
                    exceptionPropagatingFuture.set(null);
                } catch (Throwable t) {
                    exceptionPropagatingFuture.setException(t);
                }
            }
        });
        waitForFuture(exceptionPropagatingFuture);
    }


    /**
     * Waits for {@code future} and returns its value (or times out). If {@code future} has an
     * associated Exception, this will re-throw that Exception on the instrumentation thread
     * (wrapping with an unchecked Exception if necessary, to avoid requiring callers to declare
     * checked Exceptions).
     *
     * @param future the {@link Future} representing a value of interest.
     * @return the value {@code future} represents.
     */
    public static <T> T waitForFuture(Future<T> future) {
        try {
            return future.get(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (ExecutionException e) {
            // ExecutionException means this Future has an associated Exception that we should
            // re-throw on the current thread. We throw the cause instead of ExecutionException,
            // since ExecutionException itself isn't interesting, and might mislead those debugging
            // test failures to suspect this method is the culprit (whereas the root cause is from
            // another thread).
            Throwable cause = e.getCause();
            // If the cause is an unchecked Throwable type, re-throw as-is.
            if (cause instanceof Error) throw (Error) cause;
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            // Otherwise, wrap this in an unchecked Exception so callers don't need to declare
            // checked Exceptions.
            throw new RuntimeException(cause);
        } catch (InterruptedException | TimeoutException e) {
            // Don't call e.getCause() for either of these. Unlike ExecutionException, these don't
            // wrap the root cause, but rather are themselves interesting. Again, we wrap these
            // checked Exceptions with an unchecked Exception for the caller's convenience.
            //
            // Although we might be tempted to handle InterruptedException by calling
            // Thread.currentThread().interrupt(), this is not correct in this case. The interrupted
            // thread was likely a different thread than the current thread, so there's nothing
            // special we need to do.
            throw new RuntimeException(e);
        }
    }

    /**
     * Takes an element out of the {@link BlockingQueue} (or times out).
     */
    public static <T> T waitForNextQueueElement(BlockingQueue<T> queue) {
        try {
            T value = queue.poll(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (value == null) {
                // {@code null} is the special value which means {@link BlockingQueue#poll} has
                // timed out (also: there's no risk for collision with real values, because
                // BlockingQueue does not allow null entries). Instead of returning this special
                // value, let's throw a proper TimeoutException to stay consistent with {@link
                // #waitForFuture}.
                throw new TimeoutException(
                        "Timeout while trying to take next entry from BlockingQueue");
            }
            return value;
        } catch (TimeoutException | InterruptedException e) {
            // Don't handle InterruptedException specially, since it indicates that a different
            // Thread was interrupted, not this one.
            throw new RuntimeException(e);
        }
    }

    /**
     * Asserts that the device is connected to at least one available network. This enforces CTS
     * this setup requirement https://source.android.com/docs/compatibility/cts/setup#wifi-and-ipv6.
     *
     * @param ctx the {@link Context} the test is running in.
     * @return an array of connected Networks with at least one valid connected Network.
     * @throws CtsRequirementException if the device has no network connections.
     */
    public static Network[] checkNetworkAvailable(Context ctx) {
        ConnectivityManager connectivityManager = ctx.getSystemService(ConnectivityManager.class);
        Network[] networks = connectivityManager.getAllNetworks();
        if (networks.length == 0) {
            throw new CtsRequirementException(
                    "The CTS test suite requires the device should be connected to an available"
                        + " network. Refer to "
                        + "https://source.android.com/docs/compatibility/cts/setup#wifi-and-ipv6");
        }
        return networks;
    }

    /**
     * Polling to check that the screen is on and the CTS test Activity has window focus. This is
     * required for any tests which interact with UI elements, such as by tapping buttons.
     *
     * @param activity the {@link Activity} the test is running in.
     * @throws CtsRequirementException if we cannot obtain window focus.
     */
    public static void checkForWindowFocus(Activity activity) {
        PowerManager powerManager = activity.getSystemService(PowerManager.class);
        if (powerManager != null && !powerManager.isInteractive()) {
            throw new CtsRequirementException(
                    "The CTS test suite requires the device screen to be on. Please turn on the"
                        + " device screen. Refer to "
                        + "https://source.android.com/docs/compatibility/cts/setup#device-config");
        }
        KeyguardManager keyguardManager = activity.getSystemService(KeyguardManager.class);
        if (keyguardManager != null && keyguardManager.inKeyguardRestrictedInputMode()) {
            throw new CtsRequirementException(
                    "The CTS test suite requires the device screen to be unlocked. Please unlock"
                        + " the device. Refer to "
                        + "https://source.android.com/docs/compatibility/cts/setup#device-config");
        }
        try {
            new PollingCheck(TEST_TIMEOUT_MS) {
                @Override
                protected boolean check() {
                    Log.i(TAG, "Checking for window focus for activity " + activity);
                    return activity.hasWindowFocus();
                }
            }.run();
        } catch (Throwable t) {
            String message =
                    "The device screen is on but the CTS test Activity does not have focus."
                            + " Please check to make sure there is no user input dialog which is"
                            + " stealing focus.";
            throw new CtsRequirementException(message, t);
        }
    }

    // Do not instantiate this class.
    private WebkitUtils() {}
}
