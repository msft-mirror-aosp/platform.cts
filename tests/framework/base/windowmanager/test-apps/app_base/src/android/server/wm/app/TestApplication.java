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

package android.server.wm.app;

import static android.os.Process.SIGNAL_QUIT;
import static android.os.Process.myPid;
import static android.os.Process.sendSignal;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Application of WM test app. */
public class TestApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        MainThreadUnresponsivenessDetector.register(this);
    }

    /**
     * A mechanism to detect if the Android main thread becomes unresponsive after an Activity is
     * created, and if so, dumps its call stack to the log.
     */
    private static class MainThreadUnresponsivenessDetector
            implements Application.ActivityLifecycleCallbacks {

        private static final String TAG = "MainThreadMonitor";
        private static final long UNRESPONSIVE_CHECK_DELAY_MS = 500; // 0.5 seconds
        private static final long UNRESPONSIVE_THRESHOLD_MS = 2500; // 2.5 seconds
        private final Handler mMainHandler = new Handler(Looper.getMainLooper());
        private final ConcurrentMap<Activity, Thread> mMonitorThreads = new ConcurrentHashMap<>();

        static void register(@NonNull Application application) {
            application.registerActivityLifecycleCallbacks(
                    new MainThreadUnresponsivenessDetector());
            Log.d(TAG, "MainThreadUnresponsivenessDetector registered.");
        }

        @Override
        public void onActivityCreated(
                @NonNull Activity activity, @Nullable Bundle savedInstanceState) {
            final String activityName = activity.getClass().getSimpleName();
            Log.d(
                    TAG,
                    "Activity created: "
                            + activityName
                            + ". Starting main thread responsiveness check.");

            // Initialize a new CountDownLatch for each activity creation check
            final CountDownLatch latch = new CountDownLatch(1);

            // Post delay for 0.5s in case it is stuck on traversal, which is also posted.
            mMainHandler.postDelayed(
                    () -> {
                        // This runnable executes on the main thread.
                        // If it runs, the main thread is responsive, so signal the latch.
                        latch.countDown();
                        Log.d(
                                TAG,
                                "Main thread responsive. Latch counted down. Activity="
                                        + activityName);
                    },
                    UNRESPONSIVE_CHECK_DELAY_MS);

            // Start a background thread to wait for the latch
            final Thread monitorThread =
                    new Thread(
                            () -> {
                                try {
                                    // Wait for the main thread to signal responsiveness within the
                                    // threshold
                                    if (!latch.await(
                                            UNRESPONSIVE_THRESHOLD_MS, TimeUnit.MILLISECONDS)) {
                                        // If await returns false, the timeout occurred, meaning the
                                        // main thread is
                                        // unresponsive
                                        Log.e(
                                                TAG,
                                                "Main thread unresponsive for more than "
                                                        + UNRESPONSIVE_THRESHOLD_MS
                                                        + "ms after activity creation!"
                                                        + " Activity="
                                                        + activityName);
                                        dumpMainThreadCallStack();
                                        dumpAllThreadCallStack();
                                    } else {
                                        Log.d(
                                                TAG,
                                                "Main thread responded within "
                                                        + UNRESPONSIVE_THRESHOLD_MS
                                                        + "ms. Activity="
                                                        + activityName);
                                    }
                                } catch (InterruptedException e) {
                                    Log.w(
                                            TAG,
                                            "Monitor thread interrupted. Activity=" + activityName,
                                            e);
                                    // Restore the interrupted status
                                    Thread.currentThread().interrupt();
                                } finally {
                                    // Clean up: reset latch and monitor thread
                                    mMonitorThreads.remove(activity);
                                }
                            },
                            "MainThreadResponsivenessMonitor");
            mMonitorThreads.put(activity, monitorThread);
            monitorThread.start();
        }

        /** Dumps the main thread's current call stack to Logcat. */
        private void dumpMainThreadCallStack() {
            Log.e(TAG, "--- Dumping Main Thread Call Stack ---");
            for (Thread thread : Thread.getAllStackTraces().keySet()) {
                if (thread.getName().equals("main")) { // The main thread is always named "main"
                    final StringWriter sw = new StringWriter();
                    final PrintWriter pw = new PrintWriter(sw);
                    for (StackTraceElement ste : thread.getStackTrace()) {
                        pw.println("\tat " + ste.toString());
                    }
                    pw.flush();
                    Log.e(TAG, sw.toString());
                    break; // Found the main thread, no need to continue
                }
            }
            Log.e(TAG, "--- End Main Thread Call Stack ---");
        }

        /** Dumps all threads to an ANR file stored as '/data/anr/trace_*' */
        private void dumpAllThreadCallStack() {
            Log.w(TAG, "Dumping all thread stacks now...");
            sendSignal(myPid(), SIGNAL_QUIT);
        }

        @Override
        public void onActivityDestroyed(@NonNull Activity activity) {
            // When an activity is destroyed, if a monitor thread is still running for it,
            // it indicates a potential issue or just a very quick responsiveness check completion.
            // It's good practice to ensure resources are released, though the latch handles most of
            // it.
            // If the monitor thread is still active (e.g., if it's waiting for a very long time
            // or was interrupted), we can try to interrupt it.
            final Thread monitorThread = mMonitorThreads.get(activity);
            if (monitorThread != null && monitorThread.isAlive()) {
                monitorThread.interrupt(); // Attempt to interrupt the waiting thread
                Log.d(
                        TAG,
                        "Activity destroyed: "
                                + activity.getClass().getSimpleName()
                                + ". Interrupted monitor thread if active.");
            }
        }

        // --- Unused ActivityLifecycleCallbacks methods ---
        @Override
        public void onActivityStarted(@NonNull Activity activity) {}

        @Override
        public void onActivityResumed(@NonNull Activity activity) {}

        @Override
        public void onActivityPaused(@NonNull Activity activity) {}

        @Override
        public void onActivityStopped(@NonNull Activity activity) {}

        @Override
        public void onActivitySaveInstanceState(
                @NonNull Activity activity, @NonNull Bundle outState) {}
    }
}
