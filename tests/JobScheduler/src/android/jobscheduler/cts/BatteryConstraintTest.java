/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.jobscheduler.cts;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.annotation.TargetApi;
import android.app.job.JobInfo;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.BatteryManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Schedules jobs with the {@link android.app.job.JobScheduler} that have battery constraints. */
@TargetApi(26)
@RunWith(AndroidJUnit4.class)
public class BatteryConstraintTest extends BaseJobSchedulerTest {
    private static final String TAG = "BatteryConstraintTest";
    private static final String TWM_HARDWARE_FEATURE =
            "com.google.clockwork.hardware.traditional_watch_mode";

    /** Unique identifier for the job scheduled by this suite of tests. */
    private static final int BATTERY_JOB_ID = BatteryConstraintTest.class.hashCode();

    private static final int BATTERY_STATE_SETTLE_TIME_MS = 2_000;

    private JobInfo.Builder mBuilder;
    private int mLowBatteryWarningLevel = 15;
    /**
     * Record of the previous state of power save mode trigger level to reset it after the test
     * finishes.
     */
    private int mPreviousLowPowerTriggerLevel;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();

        mLowBatteryWarningLevel = Resources.getSystem().getInteger(
                     Resources.getSystem().getIdentifier(
                             "config_lowBatteryWarningLevel", "integer", "android"));
        // Disable power save mode as some devices may turn off Android when power save mode is
        // enabled, causing the test to fail.
        mPreviousLowPowerTriggerLevel = Settings.Global.getInt(getContext().getContentResolver(),
                Settings.Global.LOW_POWER_MODE_TRIGGER_LEVEL, -1);
        Settings.Global.putInt(getContext().getContentResolver(),
                Settings.Global.LOW_POWER_MODE_TRIGGER_LEVEL, 0);

        mBuilder = new JobInfo.Builder(BATTERY_JOB_ID, kJobServiceComponent);
    }

    @Override
    @After
    public void tearDown() throws Exception {
        mJobScheduler.cancel(BATTERY_JOB_ID);
        // Put battery service back in to normal operation.
        SystemUtil.runShellCommand(getInstrumentation(), "cmd battery reset");

        // Reset power save mode to its previous state.
        if (mPreviousLowPowerTriggerLevel == -1) {
            Settings.Global.putString(getContext().getContentResolver(),
                    Settings.Global.LOW_POWER_MODE_TRIGGER_LEVEL, null);
        } else {
            Settings.Global.putInt(getContext().getContentResolver(),
                    Settings.Global.LOW_POWER_MODE_TRIGGER_LEVEL, mPreviousLowPowerTriggerLevel);
        }

        super.tearDown();
    }

    // --------------------------------------------------------------------------------------------
    // Positives - schedule jobs under conditions that require them to pass.
    // --------------------------------------------------------------------------------------------

    /**
     * Schedule a job that requires the device is charging, when the battery reports it is plugged
     * in.
     */
    @Test
    public void testChargingConstraintExecutes() throws Exception {
        setBatteryState(true, 100);
        verifyChargingState(true);

        kTestEnvironment.setExpectedExecutions(1);
        kTestEnvironment.setExpectedWaitForRun();
        mJobScheduler.schedule(mBuilder.setRequiresCharging(true).build());
        assertJobReady(BATTERY_JOB_ID);
        kTestEnvironment.readyToRun();

        assertWithMessage("Job with charging constraint did not fire on power.")
                .that(kTestEnvironment.awaitExecution())
                .isTrue();
    }

    /**
     * Schedule a job that requires the device is not critical, when the battery reports it is
     * plugged in.
     */
    @Test
    public void testBatteryNotLowConstraintExecutes_withPower() throws Exception {
        setBatteryState(true, 100);
        SystemClock.sleep(BATTERY_STATE_SETTLE_TIME_MS);
        verifyChargingState(true);
        verifyBatteryNotLowState(true);

        kTestEnvironment.setExpectedExecutions(1);
        kTestEnvironment.setExpectedWaitForRun();
        mJobScheduler.schedule(mBuilder.setRequiresBatteryNotLow(true).build());
        assertJobReady(BATTERY_JOB_ID);
        kTestEnvironment.readyToRun();

        assertWithMessage("Job with battery not low constraint did not fire on power.")
                .that(kTestEnvironment.awaitExecution())
                .isTrue();
    }

    /**
     * Schedule a job that requires the device is not critical, when the battery reports it is not
     * plugged in but has sufficient power.
     */
    @Test
    public void testBatteryNotLowConstraintExecutes_withoutPower() throws Exception {
        // "Without power" test case is valid only for devices with a battery.
        if (!hasBattery()) {
            return;
        }

        setBatteryState(false, 100);
        SystemClock.sleep(BATTERY_STATE_SETTLE_TIME_MS);
        verifyChargingState(false);
        verifyBatteryNotLowState(true);

        kTestEnvironment.setExpectedExecutions(1);
        kTestEnvironment.setExpectedWaitForRun();
        mJobScheduler.schedule(mBuilder.setRequiresBatteryNotLow(true).build());
        assertJobReady(BATTERY_JOB_ID);
        kTestEnvironment.readyToRun();

        assertWithMessage("Job with battery not low constraint did not fire on power.")
                .that(kTestEnvironment.awaitExecution())
                .isTrue();
    }

    // --------------------------------------------------------------------------------------------
    // Negatives - schedule jobs under conditions that require that they fail.
    // --------------------------------------------------------------------------------------------

    /**
     * Schedule a job that requires the device is charging, and assert if failed when the device is
     * not on power.
     */
    @Test
    public void testChargingConstraintFails() throws Exception {
        // "Without power" test case is valid only for devices with a battery.
        if (!hasBattery()) {
            return;
        }

        setBatteryState(false, 100);
        verifyChargingState(false);

        kTestEnvironment.setExpectedExecutions(0);
        kTestEnvironment.setExpectedWaitForRun();
        mJobScheduler.schedule(mBuilder.setRequiresCharging(true).build());
        assertJobWaiting(BATTERY_JOB_ID);
        assertJobNotReady(BATTERY_JOB_ID);
        kTestEnvironment.readyToRun();

        assertWithMessage("Job with charging constraint fired while not on power.")
                .that(kTestEnvironment.awaitExecution(250))
                .isFalse();
        assertJobWaiting(BATTERY_JOB_ID);
        assertJobNotReady(BATTERY_JOB_ID);

        // Ensure the job runs once the device is plugged in.
        kTestEnvironment.setExpectedExecutions(1);
        kTestEnvironment.setExpectedWaitForRun();
        kTestEnvironment.setContinueAfterStart();
        setBatteryState(true, 100);
        verifyChargingState(true);
        kTestEnvironment.setExpectedStopped();
        assertJobReady(BATTERY_JOB_ID);
        kTestEnvironment.readyToRun();
        assertWithMessage("Job with charging constraint did not fire on power.")
                .that(kTestEnvironment.awaitExecution())
                .isTrue();

        // And check that the job is stopped if the device is unplugged while it is running.
        setBatteryState(false, 100);
        verifyChargingState(false);
        assertWithMessage("Job with charging constraint did not stop when power removed.")
                .that(kTestEnvironment.awaitStopped())
                .isTrue();
    }

    /**
     * Schedule a job that requires the device is not critical, and assert it failed when the
     * battery level is critical and not on power.
     */
    @Test
    public void testBatteryNotLowConstraintFails_withoutPower() throws Exception {
        // "Without power" test case is valid only for devices with a battery.
        if (!hasBattery()) {
            return;
        }
        if (getContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH)
                && getContext().getPackageManager().hasSystemFeature(TWM_HARDWARE_FEATURE)) {
            return;
        }

        setBatteryState(false, mLowBatteryWarningLevel);
        // setBatteryState() waited for the charging/not-charging state to formally settle,
        // but battery level reporting lags behind that.  wait a moment to let that happen
        // before proceeding.
        SystemClock.sleep(BATTERY_STATE_SETTLE_TIME_MS);
        verifyChargingState(false);
        verifyBatteryNotLowState(false);

        kTestEnvironment.setExpectedExecutions(0);
        kTestEnvironment.setExpectedWaitForRun();
        mJobScheduler.schedule(mBuilder.setRequiresBatteryNotLow(true).build());
        assertJobWaiting(BATTERY_JOB_ID);
        assertJobNotReady(BATTERY_JOB_ID);
        kTestEnvironment.readyToRun();

        assertWithMessage("Job with battery not low constraint fired while level critical.")
                .that(kTestEnvironment.awaitExecution(250))
                .isFalse();
        assertJobWaiting(BATTERY_JOB_ID);
        assertJobNotReady(BATTERY_JOB_ID);

        // Ensure the job runs once the device's battery level is not low.
        kTestEnvironment.setExpectedExecutions(1);
        kTestEnvironment.setExpectedWaitForRun();
        kTestEnvironment.setContinueAfterStart();
        setBatteryState(false, 50);
        SystemClock.sleep(BATTERY_STATE_SETTLE_TIME_MS);
        verifyChargingState(false);
        verifyBatteryNotLowState(true);
        kTestEnvironment.setExpectedStopped();
        assertJobReady(BATTERY_JOB_ID);
        kTestEnvironment.readyToRun();
        assertWithMessage("Job with not low constraint did not fire when charge increased.")
                .that(kTestEnvironment.awaitExecution())
                .isTrue();

        // And check that the job is stopped if battery goes low again.
        setBatteryState(false, mLowBatteryWarningLevel);
        setBatteryState(false, mLowBatteryWarningLevel - 1);
        SystemClock.sleep(BATTERY_STATE_SETTLE_TIME_MS);
        verifyChargingState(false);
        verifyBatteryNotLowState(false);
        assertWithMessage("Job with not low constraint did not stop when battery went low.")
                .that(kTestEnvironment.awaitStopped())
                .isTrue();
    }

    private boolean hasBattery() throws Exception {
        Intent batteryInfo =
                getContext()
                        .registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        boolean present = batteryInfo.getBooleanExtra(BatteryManager.EXTRA_PRESENT, true);

        if (!present) {
            Log.i(TAG, "Device doesn't have a battery.");
        }
        return present;
    }

    private void verifyChargingState(boolean charging) throws Exception {
        boolean curCharging =
                Boolean.parseBoolean(
                        SystemUtil.runShellCommand(
                                        getInstrumentation(),
                                        "cmd jobscheduler get-battery-charging")
                                .trim());

        assertThat(curCharging).isEqualTo(charging);
    }

    private void verifyBatteryNotLowState(boolean notLow) throws Exception {
        boolean curNotLow =
                Boolean.parseBoolean(
                        SystemUtil.runShellCommand(
                                        getInstrumentation(),
                                        "cmd jobscheduler get-battery-not-low")
                                .trim());
        assertThat(notLow).isEqualTo(curNotLow);
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryState = getContext().registerReceiver(null, filter);
        assertThat(!batteryState.getBooleanExtra(BatteryManager.EXTRA_BATTERY_LOW, notLow))
                .isEqualTo(notLow);
    }
}
