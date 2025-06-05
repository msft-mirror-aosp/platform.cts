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

package android.server.wm;

import android.Manifest;
import android.graphics.Rect;
import android.os.SystemProperties;
import android.window.WindowInfosListenerForTest;

import androidx.annotation.NonNull;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.SystemUtil;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Provides utility methods derived from the latest versions of {@link CtsWindowInfoUtils}.
 * These methods couldn't be directly ported due to their dependencies on Android framework code
 * from SDK 36. Instead, they've been adapted to ensure compatibility with CTS tests
 * from the CTS 15 branch.
 *
 * This class is exclusively used by CTS tests built from the CTS 15 branch and
 * must not be merged into other CTS branches.
 */
public final class CtsWindowInfoUtilsSdk35 {

    private static final int HW_TIMEOUT_MULTIPLIER = SystemProperties.getInt(
            "ro.hw_timeout_multiplier", 1);

    /**
     * Waits until the window specified by {@code predicate} is present, at the expected level
     * of the composition hierarchy, and hasn't had geometry changes for 200ms.
     */
    public static boolean waitForNthWindowFromTop(@NonNull Duration timeout,
            @NonNull Predicate<WindowInfosListenerForTest.WindowInfo> predicate,
            int expectedOrder) throws InterruptedException {
        var latch = new CountDownLatch(1);
        var satisfied = new AtomicBoolean();

        var windowNotOccluded = new Consumer<List<WindowInfosListenerForTest.WindowInfo>>() {
            private Timer mTimer = new Timer();
            private TimerTask mTask = null;
            private Rect mPreviousBounds = new Rect(0, 0, -1, -1);

            private void resetState() {
                if (mTask != null) {
                    mTask.cancel();
                    mTask = null;
                }
                mPreviousBounds.set(0, 0, -1, -1);
            }

            @Override
            public void accept(List<WindowInfosListenerForTest.WindowInfo> windowInfos) {
                if (satisfied.get()) {
                    return;
                }

                WindowInfosListenerForTest.WindowInfo targetWindowInfo = null;
                ArrayList<WindowInfosListenerForTest.WindowInfo> aboveWindowInfos =
                        new ArrayList<>();
                for (var windowInfo : windowInfos) {
                    if (predicate.test(windowInfo)) {
                        targetWindowInfo = windowInfo;
                        break;
                    }
                    if (windowInfo.isTrustedOverlay || !windowInfo.isVisible) {
                        continue;
                    }
                    aboveWindowInfos.add(windowInfo);
                }

                if (targetWindowInfo == null) {
                    // The window isn't present. If we have an active timer, we need to cancel it
                    // as it's possible the window was previously present and has since disappeared.
                    resetState();
                    return;
                }
                int currentOrder = 0;
                for (var windowInfo : aboveWindowInfos) {
                    if (targetWindowInfo.displayId == windowInfo.displayId
                            && Rect.intersects(targetWindowInfo.bounds, windowInfo.bounds)) {
                        if (currentOrder < expectedOrder) {
                            currentOrder++;
                            continue;
                        }
                        // The window is occluded. If we have an active timer, we need to cancel it
                        // as it's possible the window was previously not occluded and now is
                        // occluded.
                        resetState();
                        return;
                    }
                }
                if (currentOrder != expectedOrder) {
                    resetState();
                    return;
                }

                if (targetWindowInfo.bounds.equals(mPreviousBounds)) {
                    // The window matches previously found bounds. Let the active timer continue.
                    return;
                }

                // The window is present and not occluded but has different bounds than
                // previously seen or this is the first time we've detected the window. If
                // there's an active timer, cancel it. Schedule a task to toggle the latch in 200ms.
                resetState();
                mPreviousBounds.set(targetWindowInfo.bounds);
                mTask = new TimerTask() {
                    @Override
                    public void run() {
                        satisfied.set(true);
                        latch.countDown();
                    }
                };
                mTimer.schedule(mTask, 200L * HW_TIMEOUT_MULTIPLIER);
            }
        };
        runWithSurfaceFlingerPermission(() -> {
            var listener = new WindowInfosListenerForTest();
            try {
                listener.addWindowInfosListener(windowNotOccluded);
                latch.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
            } finally {
                listener.removeWindowInfosListener(windowNotOccluded);
            }
        });

        return satisfied.get();
    }

    private static void runWithSurfaceFlingerPermission(@NonNull InterruptableRunnable runnable)
            throws InterruptedException {
        Set<String> shellPermissions =
                InstrumentationRegistry.getInstrumentation().getUiAutomation()
                        .getAdoptedShellPermissions();
        if (shellPermissions.isEmpty()) {
            SystemUtil.runWithShellPermissionIdentity(runnable::run,
                    Manifest.permission.ACCESS_SURFACE_FLINGER);
        } else if (shellPermissions.contains(Manifest.permission.ACCESS_SURFACE_FLINGER)) {
            runnable.run();
        } else {
            throw new IllegalStateException(
                    "waitForWindowOnTop called with adopted shell permissions that don't include "
                            + "ACCESS_SURFACE_FLINGER");
        }
    }

    private interface InterruptableRunnable {
        void run() throws InterruptedException;
    }
}
