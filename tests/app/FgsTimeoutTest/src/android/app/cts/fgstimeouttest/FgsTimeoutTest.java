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
package android.app.cts.fgstimeouttest;

import static android.app.cts.fgstimeouttesthelper.FgsTimeoutHelper.FGS0;
import static android.app.cts.fgstimeouttesthelper.FgsTimeoutHelper.FGS1;
import static android.app.cts.fgstimeouttesthelper.FgsTimeoutHelper.FGS2;
import static android.app.cts.fgstimeouttesthelper.FgsTimeoutHelper.HELPER_PACKAGE;
import static android.app.cts.fgstimeouttesthelper.FgsTimeoutHelper.TAG;
import static android.app.cts.fgstimeouttesthelper.FgsTimeoutHelper.flattenComponentName;
import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC;
import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING;
import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE;

import static com.android.compatibility.common.util.TestUtils.waitUntil;

import static com.google.common.truth.Truth.assertThat;

import android.app.Flags;
import android.app.Service;
import android.app.cts.fgstimeouttesthelper.FgsTimeoutHelper;
import android.app.cts.fgstimeouttesthelper.FgsTimeoutMessage;
import android.app.cts.fgstimeouttesthelper.FgsTimeoutMessageReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.PowerExemptionManager;
import android.os.SystemClock;
import android.os.UserHandle;
import android.platform.test.annotations.Presubmit;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.DeviceConfig;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.AnrMonitor;
import com.android.compatibility.common.util.DeviceConfigStateHelper;
import com.android.compatibility.common.util.ShellUtils;
import com.android.compatibility.common.util.SystemUtil;
import com.android.server.am.nano.ServiceRecordProto;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

/**
 * Tests for the {@link Service#onTimeout(int, int)} API.
 */
@Presubmit
public class FgsTimeoutTest {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    protected static final Context sContext = FgsTimeoutHelper.sContext;
    public static DeviceConfigStateHelper sDeviceConfig;

    public static final long WAIT_TIMEOUT = 10_000;

    /**
     * Timeout for FGS used throughout this test.
     * It's shorter than the default value to speed up the test.
     */
    public static final long SHORTENED_TIMEOUT = 5_000;

    /**
     * This is the timeout between Context.startForegroundService() and Service.startForeground().
     * Within this duration, the app is temp-allowlisted, so any FGS could be started.
     * This will affect some of the tests so we shorten this too.
     *
     * Here, we use the same value as SHORTENED_TIMEOUT.
     */
    public static final long SHORTENED_START_SERVICE_TIMEOUT = 5_000;

    @BeforeClass
    public static void setUpClass() throws Exception {
        sDeviceConfig = new DeviceConfigStateHelper(DeviceConfig.NAMESPACE_ACTIVITY_MANAGER);
        ShellUtils.runShellCommand("cmd device_config set_sync_disabled_for_tests until_reboot");
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        sDeviceConfig.close();
    }

    @Before
    public void setUp() throws Exception {
        updateDeviceConfig("media_processing_fgs_timeout_duration", SHORTENED_TIMEOUT, false);
        updateDeviceConfig("data_sync_fgs_timeout_duration", SHORTENED_TIMEOUT, false);
        updateDeviceConfig("service_start_foreground_timeout_ms", SHORTENED_START_SERVICE_TIMEOUT,
                true); // Only verify the last change (skip the other ones to speed up the test)

        // Drop any pending messages
        CallProvider.clearMessageQueue();
    }

    @After
    public void tearDown() throws Exception {
        forceStopHelperApps();
    }

    private static void updateDeviceConfig(String key, long value) throws Exception {
        updateDeviceConfig(key, value, /* verify= */ true);
    }

    private static void updateDeviceConfig(String key, long value, boolean verify)
            throws Exception {
        Log.d(TAG, "updateDeviceConfig: setting " + key + " to " + value);
        sDeviceConfig.set(key, String.valueOf(value));

        if (verify) {
            waitUntil("`dumpsys activity settings` didn't update", () -> {
                final String dumpsys = ShellUtils.runShellCommand(
                        "dumpsys activity settings");

                // Look each line, rather than just doing a contains() check, so we can print
                // the current value.
                for (String line : dumpsys.split("\\n", -1)) {
                    if (!line.contains(" " + key + "=")) {
                        continue;
                    }
                    Log.d(TAG, "Current config: " + line);
                    if (line.endsWith("=" + value)) {
                        return true;
                    }
                }
                return false;
            });
        }
    }

    /**
     * Start MEDIA_PROCESSING FGS, and make sure the timeout callback is called.
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_INTRODUCE_NEW_SERVICE_ONTIMEOUT_CALLBACK)
    public void testTimeout() throws Exception {
        final long serviceStartTime = SystemClock.uptimeMillis();

        // Start the service
        startForegroundService(FGS0, FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING);
        final int startId = waitForMethodCall(FGS0, "onStartCommand").getServiceStartId();
        assertFgsRunning(FGS0);

        // Wait for onTimeout()
        Thread.sleep(SHORTENED_TIMEOUT);

        FgsTimeoutMessage m = waitForMethodCall(FGS0, "onTimeout");
        assertThat(m.getServiceStartId()).isEqualTo(startId);
        assertThat(m.getFgsType()).isEqualTo(FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING);

        // Timeout should happen after SHORTENED_TIMEOUT
        assertThat(m.getTimestamp()).isAtLeast(serviceStartTime + SHORTENED_TIMEOUT);
        assertFgsRunning(FGS0);

        // Stop the service
        sContext.stopService(new Intent().setComponent(FGS0));
        waitForMethodCall(FGS0, "onDestroy");
        assertServiceNotRunning(FGS0);

        // Wait for the timeout + extra duration
        Thread.sleep(SHORTENED_TIMEOUT + 5000);
        // Make sure onTimeout() didn't happen. (If it did, onTimeout() would send a message,
        // which would break the below ensureNoMoreMessages().)
        CallProvider.ensureNoMoreMessages();
    }

    /**
     * Make sure, if a media_processing fgs doesn't stop, the app gets ANRed.
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_INTRODUCE_NEW_SERVICE_ONTIMEOUT_CALLBACK)
    public void testAnr() throws Exception {
        final int anrExtraTimeout = 10_000;
        updateDeviceConfig("fgs_anr_extra_wait_duration", anrExtraTimeout, /* verify= */ true);

        try (AnrMonitor monitor = AnrMonitor.start(InstrumentationRegistry.getInstrumentation(),
                HELPER_PACKAGE)) {
            final long startTime = SystemClock.uptimeMillis();
            // Start the service
            startForegroundService(FGS0, FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING);
            waitForMethodCall(FGS0, "onStartCommand");
            assertFgsRunning(FGS0);

            // Wait for the timeout + extra duration
            Thread.sleep(SHORTENED_TIMEOUT + anrExtraTimeout + 2000);

            // Wait for the ANR.
            final long anrTime = monitor.waitForAnrAndReturnUptime(60_000);
            // The ANR time should be after the timeout + the ANR grace period.
            assertThat(anrTime).isAtLeast(startTime + SHORTENED_TIMEOUT + anrExtraTimeout);
        }
    }

    /**
     * Same as {@link #testTimeout}, but with another "normal" FGS running. The result should
     * be the same.
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_INTRODUCE_NEW_SERVICE_ONTIMEOUT_CALLBACK)
    public void testTimeout_withAnotherFgs() throws Exception {
        final long serviceStartTime = SystemClock.uptimeMillis();

        // Start a time-restricted fgs
        startForegroundService(FGS0, FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING);
        final int startId = waitForMethodCall(FGS0, "onStartCommand").getServiceStartId();
        assertFgsRunning(FGS0);

        // Start a non-time-restricted fgs
        startForegroundService(FGS1, FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        waitForMethodCall(FGS1, "onStartCommand");
        assertFgsRunning(FGS1);

        // Wait for the timeout
        Thread.sleep(SHORTENED_TIMEOUT);

        FgsTimeoutMessage m = waitForMethodCall(FGS0, "onTimeout");
        assertThat(m.getServiceStartId()).isEqualTo(startId);
        assertThat(m.getFgsType()).isEqualTo(FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING);

        // Timeout should happen after SHORTENED_TIMEOUT.
        assertThat(m.getTimestamp()).isAtLeast(serviceStartTime + SHORTENED_TIMEOUT);
        assertFgsRunning(FGS1);

        // Stop both services.
        sContext.stopService(new Intent().setComponent(FGS0));
        waitForMethodCall(FGS0, "onDestroy");
        assertServiceNotRunning(FGS0);

        sContext.stopService(new Intent().setComponent(FGS1));
        waitForMethodCall(FGS1, "onDestroy");
        assertServiceNotRunning(FGS1);

        CallProvider.ensureNoMoreMessages();
    }

    /**
     * Start a MEDIA_PROCESSING fgs, using Context.startService, not Context.startForegroundService.
     * Then make sure onTimeout() is called.
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_INTRODUCE_NEW_SERVICE_ONTIMEOUT_CALLBACK)
    public void testStart_startService() throws Exception {
        // The helper app is in the background and can't start a BG service, so we need to put
        // it in the temp-allowlsit first.
        tempAllowlistPackage(HELPER_PACKAGE, 5000);

        final long serviceStartTime = SystemClock.uptimeMillis();
        FgsTimeoutMessageReceiver.sendMessage(newMessage()
                .setComponentName(FGS0)
                .setDoCallStartService(true)
                .setDoCallStartForeground(true)
                .setFgsType(FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING)
                .setStartCommandResult(Service.START_NOT_STICKY));
        waitForAckMessage();
        final int startId = waitForMethodCall(FGS0, "onStartCommand").getServiceStartId();
        assertFgsRunning(FGS0);

        // Wait for onTimeout()
        Thread.sleep(SHORTENED_TIMEOUT);
        FgsTimeoutMessage m = waitForMethodCall(FGS0, "onTimeout");
        assertThat(m.getServiceStartId()).isEqualTo(startId);
        assertThat(m.getFgsType()).isEqualTo(FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING);
        // Timeout should happen after SHORTENED_TIMEOUT.
        assertThat(m.getTimestamp()).isAtLeast(serviceStartTime + SHORTENED_TIMEOUT);
        assertFgsRunning(FGS0);

        // Stop the service.
        sContext.stopService(new Intent().setComponent(FGS0));
        waitForMethodCall(FGS0, "onDestroy");
        assertServiceNotRunning(FGS0);

        // Wait for the timeout + extra duration
        Thread.sleep(SHORTENED_TIMEOUT + 5000);
        // Make sure onTimeout() didn't happen. (If it did, onTimeout() would send a message,
        // which would break the below ensureNoMoreMessages().)
        CallProvider.ensureNoMoreMessages();
    }

    /**
     * Start MEDIA_PROCESSING FGS and stop it, and make sure nothing throws,
     * and the service's onDestroy() gets called.
     * - Start FGS0 (in the helper app) with the MEDIA_PROCESSING FGS
     * - Stop it with Context.stopService().
     * - Wait until the timeout time and make sure the timeout callback won't be called.
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_INTRODUCE_NEW_SERVICE_ONTIMEOUT_CALLBACK)
    public void testStop_stopService() throws Exception {
        // Start the service
        startForegroundService(FGS0, FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING);
        waitForMethodCall(FGS0, "onStartCommand");
        assertFgsRunning(FGS0);

        // Stop the service
        sContext.stopService(new Intent().setComponent(FGS0));
        waitForMethodCall(FGS0, "onDestroy");
        assertServiceNotRunning(FGS0);

        // Wait for the timeout + extra duration
        Thread.sleep(SHORTENED_TIMEOUT + 5000);
        // Make sure onTimeout() didn't happen. (If it did, onTimeout() would send a message,
        // which would break the below ensureNoMoreMessages().)
        CallProvider.ensureNoMoreMessages();
    }

    /**
     * Start MEDIA_PROCESSING FGS and stop it, and make sure nothing throws,
     * and the service's onDestroy() gets called.
     * - Start FGS0 (in the helper app) with the MEDIA_PROCESSING FGS
     * - Stop it with Service.stopSelf().
     * - Wait until the timeout time and make sure the timeout callback won't be called.
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_INTRODUCE_NEW_SERVICE_ONTIMEOUT_CALLBACK)
    public void testStop_stopSelf() throws Exception {
        // Start the service
        startForegroundService(FGS0, FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING);
        waitForMethodCall(FGS0, "onStartCommand");
        assertFgsRunning(FGS0);

        // Stop the service, using Service.stopSelf()
        FgsTimeoutMessageReceiver
                .sendMessage(newMessage().setDoCallStopSelf(true).setComponentName(FGS0));
        waitForAckMessage();
        waitForMethodCall(FGS0, "onDestroy");
        assertServiceNotRunning(FGS0);

        // Wait for the timeout + extra duration
        Thread.sleep(SHORTENED_TIMEOUT + 5000);
        // Make sure onTimeout() didn't happen. (If it did, onTimeout() would send a message,
        // which would break the below ensureNoMoreMessages().)
        CallProvider.ensureNoMoreMessages();
    }

    /**
     * Make sure another FGS *can* be started, if an app has a time-restricted FGS and
     * other kinds of FGS.
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_INTRODUCE_NEW_SERVICE_ONTIMEOUT_CALLBACK)
    public void testCanStartAnotherFgsFromTimeRestrictedFgs() throws Exception {
        // Here, we want the MEDIA_PROCESSING timeout to be significantly larger than the
        // startForeground() timeout, because we want to check the state between them.
        updateDeviceConfig("media_processing_fgs_timeout_duration",
                SHORTENED_START_SERVICE_TIMEOUT + 60_000);

        // Start a time-restricted FGS
        startForegroundService(FGS0, FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING);
        waitForMethodCall(FGS0, "onStartCommand");
        assertFgsRunning(FGS0);

        // Start another FGS, that's not time-restricted
        startForegroundService(FGS1, FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        waitForMethodCall(FGS1, "onStartCommand");
        assertFgsRunning(FGS1);

        // Because of the first Context.startForegroundService() for FGS0, the helper app is
        // temp-allowlisted for this duration, so another Context.startForegroundService() would
        // automatically succeed.
        // We wait until the temp-allowlist expires, so startForegroundService() would fail.
        Thread.sleep(SHORTENED_START_SERVICE_TIMEOUT);

        // Let the helper app call Context.startForegroundService, which should succeed.
        FgsTimeoutMessageReceiver.sendMessage(
                newMessage().setDoCallStartForegroundService(true)
                        .setComponentName(FGS2)
                        .setDoCallStartForeground(true)
                        .setFgsType(FOREGROUND_SERVICE_TYPE_DATA_SYNC));
        waitForAckMessage();
        // FGS2 should now be running too
        waitForMethodCall(FGS2, "onStartCommand");
        assertFgsRunning(FGS2);

        // Stop the services.
        sContext.stopService(new Intent().setComponent(FGS0));
        waitForMethodCall(FGS0, "onDestroy");
        assertServiceNotRunning(FGS0);

        sContext.stopService(new Intent().setComponent(FGS1));
        waitForMethodCall(FGS1, "onDestroy");
        assertServiceNotRunning(FGS1);

        sContext.stopService(new Intent().setComponent(FGS2));
        waitForMethodCall(FGS2, "onDestroy");
        assertServiceNotRunning(FGS2);

        CallProvider.ensureNoMoreMessages();
    }

    /**
     * Change the FGS type from a time-restricted fgs to another type.
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_INTRODUCE_NEW_SERVICE_ONTIMEOUT_CALLBACK)
    public void testTypeChange_fromTimeRestricted_toAnother() throws Exception {
        // Temp-allowlist the helper app for the entire test, so the app can call startForeground()
        // any time.
        tempAllowlistPackage(HELPER_PACKAGE, 10 * 60 * 1000);

        startForegroundService(FGS0, FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING);
        waitForMethodCall(FGS0, "onStartCommand");

        // Verify the FGS type
        ServiceRecordProto sr = assertFgsRunning(FGS0);
        assertThat(sr.foreground.foregroundServiceType)
                .isEqualTo(FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING);

        // Change the FGS type to SPECIAL_USE.
        Thread.sleep(1000);
        FgsTimeoutMessageReceiver.sendMessage(newMessage()
                .setComponentName(FGS0)
                .setDoCallStartForeground(true)
                .setFgsType(FOREGROUND_SERVICE_TYPE_SPECIAL_USE));
        waitForAckMessage();

        // The FGS type should be different now.
        sr = assertFgsRunning(FGS0);
        assertThat(sr.foreground.foregroundServiceType)
                .isEqualTo(FOREGROUND_SERVICE_TYPE_SPECIAL_USE);

        // Change the FGS type back to MEDIA_PROCESSING again.
        Thread.sleep(1000);
        FgsTimeoutMessageReceiver.sendMessage(newMessage()
                .setComponentName(FGS0)
                .setDoCallStartForeground(true)
                .setFgsType(FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING));
        waitForAckMessage();

        // Verify the FGS type
        sr = assertFgsRunning(FGS0);
        assertThat(sr.foreground.foregroundServiceType)
                .isEqualTo(FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING);
    }

    private static void ensureHelperAppNotRunning() throws Exception {
        // Wait until the process is actually gone.
        // We need it because 1) kill is async and 2) the ack is sent before the kill anyway
        waitUntil("Process still running",
                () -> !DumpProtoUtils.processExists(FGS0.getPackageName()));
    }

    /**
     * Force-stop the helper app. It'll also remove the test app from temp-allowlist.
     */
    private static void forceStopHelperApps() throws Exception {
        SystemUtil.runShellCommand("am force-stop " + HELPER_PACKAGE);
        untempAllowlistPackage(HELPER_PACKAGE);
        ensureHelperAppNotRunning();
    }

    private static FgsTimeoutMessage newMessage() {
        return new FgsTimeoutMessage();
    }

    private static FgsTimeoutMessage waitForNextMessage() {
        return CallProvider.waitForNextMessage(WAIT_TIMEOUT);
    }

    public static void waitForAckMessage() {
        CallProvider.waitForAckMessage(WAIT_TIMEOUT);
    }

    public static void startForegroundService(ComponentName cn, int fgsTypes) {
        startForegroundService(cn, fgsTypes, Service.START_NOT_STICKY);
    }

    public static void startForegroundService(ComponentName cn, int fgsTypes,
            int startCommandResult) {
        Log.i(TAG, "startForegroundService: Starting " + cn
                + " types=0x" + Integer.toHexString(fgsTypes));
        FgsTimeoutMessage startMessage = newMessage()
                .setComponentName(cn)
                .setDoCallStartForeground(true)
                .setFgsType(fgsTypes)
                .setStartCommandResult(startCommandResult);

        // Actual intent to start the FGS.
        Intent i = new Intent().setComponent(cn);
        FgsTimeoutHelper.setMessage(i, startMessage);

        sContext.startForegroundService(i);
    }

    public static FgsTimeoutMessage waitForMethodCall(ComponentName cn, String methodName) {
        Log.i(TAG, "waitForMethodCall: waiting for " + methodName + " from " + cn);
        FgsTimeoutMessage m = waitForNextMessage();

        String expected = flattenComponentName(cn) + "." + methodName;
        if (m.getMethodName() == null) {
            Assert.fail("Waited for " + expected + " but received: " + m);
        }
        assertThat(flattenComponentName(m.getComponentName()) + "." + m.getMethodName())
                    .isEqualTo(expected);
        return m;
    }

    /**
     * Make sure a specified FGS is running.
     */
    public static ServiceRecordProto assertFgsRunning(ComponentName cn) {
        final ServiceRecordProto srp = DumpProtoUtils.findServiceRecord(cn);
        if (srp == null) {
            Assert.fail("Service " + cn + " is not running");
        }
        if (srp.foreground == null) {
            Assert.fail("Service " + cn + " is running, but is not an FGS");
        }

        return srp;
    }

    public static void assertServiceNotRunning(ComponentName cn) {
        final ServiceRecordProto srp = DumpProtoUtils.findServiceRecord(cn);
        assertThat(srp).isNull();
    }

    public static void tempAllowlistPackage(String packageName, int durationMillis) {
        final PowerExemptionManager pem = sContext.getSystemService(PowerExemptionManager.class);
        SystemUtil.runWithShellPermissionIdentity(
                () -> pem.addToTemporaryAllowList(packageName, PowerExemptionManager.REASON_OTHER,
                        TAG, durationMillis));
    }

    public static void untempAllowlistPackage(String packageName) {
        SystemUtil.runShellCommand("cmd deviceidle tempwhitelist -r "
                + " -u " + UserHandle.getUserId(android.os.Process.myUid())
                + " " + packageName);
    }
}
