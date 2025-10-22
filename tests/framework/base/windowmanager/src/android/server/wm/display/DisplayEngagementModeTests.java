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

package android.server.wm.display;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.platform.test.annotations.Presubmit;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.server.wm.WindowManagerState;
import android.view.WindowManager;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.SystemUtil;
import com.android.window.flags.Flags;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Tests that verify the behavior of display engagement mode.
 *
 * <p>Build/Install/Run: atest CtsWindowManagerDeviceDisplay:DisplayEngagementModeTests
 */
@Presubmit
public class DisplayEngagementModeTests extends WindowContextTestBase {
    @RequiresFlagsEnabled(Flags.FLAG_DEVICE_ENGAGEMENT_MODE)
    @ApiTest(
            apis = {
                "android.view.WindowManager#setDisplayEngagementMode",
                "android.view.WindowManager#getDisplayEngagementMode"
            })
    @Test
    public void testDeviceEngagementMode() {
        final WindowManagerState.DisplayContent display =
                createManagedVirtualDisplaySession().setSimulateDisplay(true).createDisplay();
        final Context windowContext = createWindowContext(display.mId);
        final WindowManager wm = windowContext.getSystemService(WindowManager.class);

        final int visualsOnEngagementMode = WindowManager.ENGAGEMENT_MODE_FLAG_VISUALS_ON;
        SystemUtil.runWithShellPermissionIdentity(
                () -> {
                    wm.setDisplayEngagementMode(display.mId, visualsOnEngagementMode);
                });

        final int firstResult = wm.getDisplayEngagementMode(display.mId);
        assertThat(firstResult).isEqualTo(visualsOnEngagementMode);

        final int audioOnEngagementMode = WindowManager.ENGAGEMENT_MODE_FLAG_AUDIO_ON;
        SystemUtil.runWithShellPermissionIdentity(
                () -> {
                    wm.setDisplayEngagementMode(display.mId, audioOnEngagementMode);
                });

        final int secondResult = wm.getDisplayEngagementMode(display.mId);
        assertThat(secondResult).isEqualTo(audioOnEngagementMode);
    }

    @RequiresFlagsEnabled(Flags.FLAG_DEVICE_ENGAGEMENT_MODE)
    @ApiTest(
            apis = {
                "android.view.WindowManager#registerDisplayEngagementModeCallback",
                "android.view.WindowManager#unregisterDisplayEngagementModeCallback"
            })
    @Test
    public void testDeviceEngagementModeCallback() throws InterruptedException {
        final WindowManagerState.DisplayContent display =
                createManagedVirtualDisplaySession().setSimulateDisplay(true).createDisplay();
        final Context windowContext = createWindowContext(display.mId);
        final WindowManager wm = windowContext.getSystemService(WindowManager.class);
        final CountDownLatch latch = new CountDownLatch(1);
        final int expectedMode = WindowManager.ENGAGEMENT_MODE_FLAG_AUDIO_ON;

        final Consumer<WindowManager.DisplayEngagementModeState> callback =
                state -> {
                    if (state.getDisplayId() == display.mId
                            && state.getEngagementModeFlags() == expectedMode) {
                        latch.countDown();
                    }
                };

        wm.registerDisplayEngagementModeCallback(Runnable::run, callback);

        try {
            SystemUtil.runWithShellPermissionIdentity(
                    () -> {
                        wm.setDisplayEngagementMode(display.mId, expectedMode);
                    });
            assertTrue(
                    "Callback was not invoked within the timeout",
                    latch.await(5, TimeUnit.SECONDS));
        } finally {
            wm.unregisterDisplayEngagementModeCallback(callback);
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_DEVICE_ENGAGEMENT_MODE)
    @ApiTest(
            apis = {
                "android.view.WindowManager#registerDisplayEngagementModeCallback",
                "android.view.WindowManager#unregisterDisplayEngagementModeCallback"
            })
    @Test
    public void testDeviceEngagementModeCallback_doubleRegistration() throws InterruptedException {
        final WindowManagerState.DisplayContent display =
                createManagedVirtualDisplaySession().setSimulateDisplay(true).createDisplay();
        final Context windowContext = createWindowContext(display.mId);
        final WindowManager wm = windowContext.getSystemService(WindowManager.class);
        final CountDownLatch latch = new CountDownLatch(1);
        final int expectedMode = WindowManager.ENGAGEMENT_MODE_FLAG_AUDIO_ON;

        final Consumer<WindowManager.DisplayEngagementModeState> callback =
                state -> {
                    if (state.getDisplayId() == display.mId
                            && state.getEngagementModeFlags() == expectedMode) {
                        latch.countDown();
                    }
                };

        wm.registerDisplayEngagementModeCallback(Runnable::run, callback);
        // Registering the same callback again should be a no-op.
        wm.registerDisplayEngagementModeCallback(Runnable::run, callback);

        try {
            SystemUtil.runWithShellPermissionIdentity(
                    () -> {
                        wm.setDisplayEngagementMode(display.mId, expectedMode);
                    });
            assertTrue(
                    "Callback was not invoked within the timeout",
                    latch.await(5, TimeUnit.SECONDS));
        } finally {
            wm.unregisterDisplayEngagementModeCallback(callback);
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_DEVICE_ENGAGEMENT_MODE)
    @ApiTest(
            apis = {
                "android.view.WindowManager#registerDisplayEngagementModeCallback",
                "android.view.WindowManager#unregisterDisplayEngagementModeCallback"
            })
    @Test
    public void testDeviceEngagementModeCallback_unregisterInCallback()
            throws InterruptedException {
        final WindowManagerState.DisplayContent display =
                createManagedVirtualDisplaySession().setSimulateDisplay(true).createDisplay();
        final Context windowContext = createWindowContext(display.mId);
        final WindowManager wm = windowContext.getSystemService(WindowManager.class);
        final CountDownLatch latch = new CountDownLatch(1);
        final int firstExpectedMode = WindowManager.ENGAGEMENT_MODE_FLAG_AUDIO_ON;
        final int secondExpectedMode = WindowManager.ENGAGEMENT_MODE_FLAG_VISUALS_ON;

        final Consumer<WindowManager.DisplayEngagementModeState> callback =
                new Consumer<WindowManager.DisplayEngagementModeState>() {
                    @Override
                    public void accept(WindowManager.DisplayEngagementModeState state) {
                        if (state.getDisplayId() == display.mId
                                && state.getEngagementModeFlags() == firstExpectedMode) {
                            latch.countDown();
                            wm.unregisterDisplayEngagementModeCallback(this);
                        }
                    }
                };

        wm.registerDisplayEngagementModeCallback(Runnable::run, callback);

        try {
            SystemUtil.runWithShellPermissionIdentity(
                    () -> {
                        wm.setDisplayEngagementMode(display.mId, firstExpectedMode);
                    });
            assertTrue(
                    "Callback was not invoked within the timeout",
                    latch.await(5, TimeUnit.SECONDS));

            // This second change should not trigger the callback.
            SystemUtil.runWithShellPermissionIdentity(
                    () -> {
                        wm.setDisplayEngagementMode(display.mId, secondExpectedMode);
                    });
        } finally {
            // The callback should already be unregistered, but we try to unregister again here to
            // ensure that unregistering a non-existent callback doesn't cause a crash.
            wm.unregisterDisplayEngagementModeCallback(callback);
        }
    }
}
