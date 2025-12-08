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
package android.os.cts.batterysaving;

import static com.android.compatibility.common.util.AmUtils.isProcessRunning;
import static com.android.compatibility.common.util.BatteryUtils.resetBatterySaver;
import static com.android.compatibility.common.util.BatteryUtils.runDumpsysBatteryReset;
import static com.android.compatibility.common.util.BatteryUtils.turnOnScreen;
import static com.android.compatibility.common.util.SystemUtil.runCommandAndPrintOnLogcat;
import static com.android.compatibility.common.util.SystemUtil.runShellCommand;
import static com.android.compatibility.common.util.TestUtils.waitUntil;

import android.content.Context;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.BatteryManager;
import android.os.PowerManager;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.BatteryUtils;
import com.android.compatibility.common.util.BeforeAfterRule;
import com.android.compatibility.common.util.OnFailureRule;
import com.android.compatibility.common.util.ProtoUtils;
import com.android.compatibility.common.util.UserSettings;
import com.android.compatibility.common.util.UserSettings.Namespace;
import com.android.server.job.nano.JobSchedulerServiceDumpProto;
import com.android.server.job.nano.StateControllerProto;

import org.junit.Rule;
import org.junit.rules.RuleChain;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/** Base class for battery saving CTS tests. */
public class BatterySavingTestBase {
    private static final String TAG = "BatterySavingTestBase";

    /** Default timeout in seconds for {@link #waitUntil} calls. */
    public static final int DEFAULT_TIMEOUT_SECONDS = 30;

    public static final boolean DEBUG = false;

    protected static final UserSettings sGlobalSettings = new UserSettings(Namespace.GLOBAL);

    protected final BroadcastRpc mRpc = new BroadcastRpc();

    /**
     * A rule to dump information about various system services on test failure, which can help with
     * debugging.
     */
    private final OnFailureRule mDumpOnFailureRule =
            new OnFailureRule(TAG) {
                @Override
                protected void onTestFailure(Statement base, Description description, Throwable t) {
                    runCommandAndPrintOnLogcat(TAG, "dumpsys power");
                    runCommandAndPrintOnLogcat(TAG, "dumpsys alarm");
                    runCommandAndPrintOnLogcat(TAG, "dumpsys jobscheduler");
                    runCommandAndPrintOnLogcat(TAG, "dumpsys content");
                    runCommandAndPrintOnLogcat(TAG, "dumpsys battery");
                }
            };

    /**
     * A rule to initialize the device state before a test (e.g. turn on the screen) and clean up
     * after (e.g. reset battery saver).
     */
    private final BeforeAfterRule mInitializeAndCleanupRule =
            new BeforeAfterRule() {
                @Override
                protected void onBefore(Statement base, Description description) throws Throwable {
                    BatteryUtils.assumeBatterySaverFeature();

                    turnOnScreen(true);
                }

                @Override
                protected void onAfter(Statement base, Description description) throws Throwable {
                    runDumpsysBatteryReset();
                    turnOnScreen(true);
                    resetBatterySaver();
                }
            };

    @Rule
    public RuleChain Rules = RuleChain.outerRule(mInitializeAndCleanupRule)
            .around(mDumpOnFailureRule);

    public String getLogTag() {
        return TAG;
    }

    /** Print a debug log on logcat. */
    public void debug(String message) {
        if (DEBUG || Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(getLogTag(), message);
        }
    }

    /** Wait until {@code dumpsys alarm} shows the expected "Force all apps standby" state. */
    public void waitUntilAlarmForceAppStandby(boolean expected) throws Exception {
        waitUntil("Force all apps standby still " + !expected + " (alarm)", () ->
                runShellCommand("dumpsys alarm").contains("Force all apps standby: " + expected));
    }

    /**
     * Wait until {@code dumpsys jobscheduler} shows the expected "Force all apps standby" state.
     */
    public void waitUntilJobForceAppStandby(boolean expected) throws Exception {
        waitUntil("Force all apps standby still " + !expected + " (job)", () -> {
            JobSchedulerServiceDumpProto proto = ProtoUtils.getProto(
                    InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                    JobSchedulerServiceDumpProto.class,
                    ProtoUtils.DUMPSYS_JOB_SCHEDULER);
            for (StateControllerProto controllerProto : proto.controllers) {
                if (controllerProto.hasBackground()) {
                    return controllerProto.getBackground().appStateTracker.forceAllAppsStandby
                            == expected;
                }
            }
            return false;
        });
    }

    /** Wait until {@code dumpsys activity} shows the expected "mForceBackgroundCheck" state. */
    public void waitUntilForceBackgroundCheck(boolean expected) throws Exception {
        waitUntil("Force background check still " + !expected + " (job)", () ->
                runShellCommand("dumpsys activity").contains("mForceBackgroundCheck=" + expected));
    }

    /** Wait until a given package's process is no longer running. */
    public void waitUntilProcessGone(String packageName) throws Exception {
        waitUntil(
                packageName + " process still running",
                DEFAULT_TIMEOUT_SECONDS,
                () -> !isProcessRunning(packageName));
    }

    public static Context getContext() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    public PackageManager getPackageManager() {
        return getContext().getPackageManager();
    }

    public PowerManager getPowerManager() {
        return getContext().getSystemService(PowerManager.class);
    }

    public BatteryManager getBatteryManager() {
        return getContext().getSystemService(BatteryManager.class);
    }

    public LocationManager getLocationManager() {
        return getContext().getSystemService(LocationManager.class);
    }
}
