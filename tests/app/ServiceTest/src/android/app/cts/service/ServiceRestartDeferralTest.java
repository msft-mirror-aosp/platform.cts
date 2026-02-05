/*
 * Copyright (C) 2026 The Android Open Source Project
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

package android.app.cts.service;

import static android.content.Context.BIND_ALLOW_FREEZE;
import static android.content.Context.BIND_AUTO_CREATE;

import static com.android.compatibility.common.util.AmUtils.isProcessRunning;
import static com.android.compatibility.common.util.AmUtils.runStopApp;
import static com.android.compatibility.common.util.TestUtils.waitUntil;

import static com.google.common.truth.Truth.assertWithMessage;

import android.app.stubs.shared.ICommandService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.SystemClock;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.SystemUtil;
import com.android.server.am.Flags;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class ServiceRestartDeferralTest {
    private static final String TAG = "ServiceRestartDeferralTest";

    private static final String HELPER_A_PKG = "com.android.app1";
    private static final String HELPER_B_PKG = "com.android.app2";
    private static final String COMMAND_SERVICE_CLASS = "android.app.stubs.shared.CommandService";
    private static final String LOCAL_SERVICE_CLASS = "android.app.stubs.shared.LocalService";
    private static final int TIMEOUT_SECONDS = 10;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private Context mContext;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    @After
    public void tearDown() throws Exception {
        runStopApp(HELPER_A_PKG);
        runStopApp(HELPER_B_PKG);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_DEFER_SERVICE_RESTART_WHEN_FROZEN)
    public void testServiceRestartDeferralWithFrozenClient() throws Exception {
        final BlockingQueue<IBinder> blockingQueue = new LinkedBlockingQueue<>(1);
        final ServiceConnection conn = createServiceConnection(blockingQueue);
        try {
            final Intent intentA = new Intent().setClassName(HELPER_A_PKG, COMMAND_SERVICE_CLASS);
            // Bind to CommandService in App_A
            Log.i(TAG, "Binding to " + HELPER_A_PKG);
            // BIND_ALLOW_FREEZE flag is used so that App_A can be frozen even with binding from
            // the test app
            final boolean bound =
                    mContext.bindService(
                            intentA,
                            conn,
                            Context.BindServiceFlags.of(BIND_AUTO_CREATE | BIND_ALLOW_FREEZE));
            assertWithMessage("Failed to bind to App1").that(bound).isTrue();
            final ICommandService commandService =
                    ICommandService.Stub.asInterface(
                            blockingQueue.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS));

            // Command App_A to bind to LocalService in App_B
            Log.i(TAG, "Initiating binding from " + HELPER_A_PKG + " to " + HELPER_B_PKG);
            commandService.bindToService(HELPER_B_PKG, LOCAL_SERVICE_CLASS);

            // Wait for App_B to be up
            waitUntil(
                    "Helper B did not start",
                    TIMEOUT_SECONDS,
                    () -> isProcessRunning(HELPER_B_PKG));

            // Freeze App_A
            Log.i(TAG, "Freezing " + HELPER_A_PKG);
            freezeApp(HELPER_A_PKG);

            // Crash App_B
            Log.i(TAG, "Crashing " + HELPER_B_PKG);
            crashApp(HELPER_B_PKG);
            waitUntil(
                    "Helper B did not die", TIMEOUT_SECONDS, () -> !isProcessRunning(HELPER_B_PKG));

            // Verify App_B does not restart while App_B is frozen.
            Log.i(
                    TAG,
                    "Checking that "
                            + HELPER_B_PKG
                            + " doesn't restart while "
                            + HELPER_A_PKG
                            + " is frozen");
            // Intentionally wait for some time to ensure App_B does not restart
            SystemClock.sleep(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
            assertWithMessage("Helper B should not have restarted while Helper A is frozen")
                    .that(isProcessRunning(HELPER_B_PKG))
                    .isFalse();

            // Unfreeze App_A
            Log.i(TAG, "Unfreezing " + HELPER_A_PKG);
            unfreezeApp(HELPER_A_PKG);

            // Verify App_B restarts
            Log.i(TAG, "Waiting for " + HELPER_B_PKG + " to restart");
            waitUntil(
                    "Helper B should restart after A is unfrozen",
                    TIMEOUT_SECONDS,
                    () -> isProcessRunning(HELPER_B_PKG));
        } finally {
            mContext.unbindService(conn);
        }
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_DEFER_SERVICE_RESTART_WHEN_FROZEN)
    public void testServiceRestartDeferralWithFrozenClient_flagDisabled() throws Exception {
        final BlockingQueue<IBinder> blockingQueue = new LinkedBlockingQueue<>(1);
        final ServiceConnection conn = createServiceConnection(blockingQueue);
        try {
            final Intent intentA = new Intent().setClassName(HELPER_A_PKG, COMMAND_SERVICE_CLASS);
            // Bind to CommandService in App_A
            Log.i(TAG, "Binding to " + HELPER_A_PKG);
            // BIND_ALLOW_FREEZE flag is used so that App_A can be frozen even with binding from
            // the test app
            final boolean bound =
                    mContext.bindService(
                            intentA,
                            conn,
                            Context.BindServiceFlags.of(BIND_AUTO_CREATE | BIND_ALLOW_FREEZE));
            assertWithMessage("Failed to bind to App1").that(bound).isTrue();
            final ICommandService commandService =
                    ICommandService.Stub.asInterface(
                            blockingQueue.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS));

            // Command App_A to bind to LocalService in App_B
            Log.i(TAG, "Initiating binding from " + HELPER_A_PKG + " to " + HELPER_B_PKG);
            commandService.bindToService(HELPER_B_PKG, LOCAL_SERVICE_CLASS);

            // Wait for App_B to be up
            waitUntil(
                    "Helper B did not start",
                    TIMEOUT_SECONDS,
                    () -> isProcessRunning(HELPER_B_PKG));

            // Freeze App_A
            Log.i(TAG, "Freezing " + HELPER_A_PKG);
            freezeApp(HELPER_A_PKG);

            // Crash App_B
            Log.i(TAG, "Crashing " + HELPER_B_PKG);
            crashApp(HELPER_B_PKG);
            waitUntil(
                    "Helper B did not die", TIMEOUT_SECONDS, () -> !isProcessRunning(HELPER_B_PKG));

            // Verify App_B does restart while App_A is frozen because the flag is disabled.
            Log.i(
                    TAG,
                    "Checking that "
                            + HELPER_B_PKG
                            + " restarts even if "
                            + HELPER_A_PKG
                            + " is frozen (flag disabled)");
            waitUntil(
                    "Helper B should restart even if Helper A is frozen (flag disabled)",
                    TIMEOUT_SECONDS,
                    () -> isProcessRunning(HELPER_B_PKG));
        } finally {
            mContext.unbindService(conn);
        }
    }

    @NonNull
    private static ServiceConnection createServiceConnection(
            @NonNull BlockingQueue<IBinder> blockingQueue) {
        return new ServiceConnection() {
            @Override
            public void onServiceConnected(@NonNull ComponentName name, @NonNull IBinder service) {
                Log.i(TAG, "Service got connected: " + name);
                blockingQueue.offer(service);
            }

            @Override
            public void onServiceDisconnected(@NonNull ComponentName name) {
                Log.i(TAG, "Service got disconnected: " + name);
            }
        };
    }

    private static void freezeApp(@NonNull String packageName) {
        SystemUtil.runShellCommand("cmd activity freeze " + packageName);
    }

    private static void unfreezeApp(@NonNull String packageName) {
        SystemUtil.runShellCommand("cmd activity unfreeze " + packageName);
    }

    private static void crashApp(@NonNull String packageName) {
        SystemUtil.runShellCommand("am crash " + packageName);
    }
}
