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

import static android.os.cts.batterysaving.common.Values.APP_25_PACKAGE;
import static android.os.cts.batterysaving.common.Values.getRandomInt;

import static com.android.compatibility.common.util.AmUtils.runStopApp;
import static com.android.compatibility.common.util.BatteryUtils.assumeBatterySaverFeature;
import static com.android.compatibility.common.util.BatteryUtils.enableBatterySaver;
import static com.android.compatibility.common.util.BatteryUtils.runDumpsysBatteryUnplug;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.alarmmanager.util.AlarmManagerDeviceConfigHelper;
import android.app.AlarmManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.cts.batterysaving.common.BatterySavingCtsCommon.Payload;
import android.os.cts.batterysaving.common.BatterySavingCtsCommon.Payload.TestServiceRequest;
import android.os.cts.batterysaving.common.BatterySavingCtsCommon.Payload.TestServiceRequest.SetAlarmRequest;
import android.os.cts.batterysaving.common.BatterySavingCtsCommon.Payload.TestServiceRequest.StartServiceRequest;
import android.os.cts.batterysaving.common.Values;
import android.util.Log;

import androidx.test.filters.LargeTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.PollingCheck;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * CTS for battery saver alarm throttling
 *
 * <p>atest $ANDROID_BUILD_TOP/cts/tests/tests/batterysaving/src/android/os/cts/batterysaving
 * /BatterySaverAlarmTest.java
 */
@LargeTest
@RunWith(AndroidJUnit4.class)
public class BatterySaverAlarmTest extends BatterySavingTestBase {
    private static final String TAG = "BatterySaverAlarmTest";

    private static final long ONE_SECOND = TimeUnit.SECONDS.toMillis(1);
    private static final long WAIT_SLOP_FOR_ABSENCE = ONE_SECOND;
    private static final long POLLING_WAIT_MILLIS = 2 * ONE_SECOND;
    // Extra long timeout to let AppSchedulingThread Looper to catch up with
    // battery saver state.
    private static final long POLLING_WAIT_FOR_HANDLER_FLUSH = 30 * ONE_SECOND;

    // Tweaked alarm manager constants to facilitate testing
    private static final long ALLOW_WHILE_IDLE_COMPAT_WINDOW = 12 * ONE_SECOND;
    private static final long ALLOW_WHILE_IDLE_QUOTA_PRE_S = 3;

    private void updateAlarmManagerConstants() {
        mAlarmManagerDeviceConfigStateHelper
                .with("min_interval", 0L)
                .with("min_futurity", 0L)
                .with("allow_while_idle_compat_quota", ALLOW_WHILE_IDLE_QUOTA_PRE_S)
                .with("allow_while_idle_compat_window", ALLOW_WHILE_IDLE_COMPAT_WINDOW)
                .commitAndAwaitPropagation();
    }

    private void resetAlarmManagerConstants() {
        mAlarmManagerDeviceConfigStateHelper.restoreAll();
    }

    // Use a different broadcast action every time.
    private final String ACTION = "BATTERY_SAVER_ALARM_TEST_ALARM_ACTION_" + Values.getRandomInt();

    private final AtomicInteger mAlarmCount = new AtomicInteger();
    private final AtomicInteger mLastAlarmRequestCode = new AtomicInteger(5);

    private final BroadcastReceiver mAlarmReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mAlarmCount.incrementAndGet();
            Log.d(TAG, "Alarm received at " + SystemClock.elapsedRealtime());
        }
    };

    private final AlarmManagerDeviceConfigHelper mAlarmManagerDeviceConfigStateHelper =
            new AlarmManagerDeviceConfigHelper();

    @Before
    public void setUp() throws IOException {
        updateAlarmManagerConstants();

        final IntentFilter filter = new IntentFilter(ACTION);
        getContext().registerReceiver(mAlarmReceiver, filter, null,
                new Handler(Looper.getMainLooper()), Context.RECEIVER_EXPORTED);
        assumeBatterySaverFeature();
    }

    @After
    public void tearDown() throws IOException {
        resetAlarmManagerConstants();
        getContext().unregisterReceiver(mAlarmReceiver);
    }

    private void scheduleAlarm(String targetPackage, int type, long triggerMillis)
            throws Exception {
        scheduleAlarm(targetPackage, type, triggerMillis, /*whileIdle=*/ true,
                mLastAlarmRequestCode.incrementAndGet());
    }

    private void scheduleAlarm(String targetPackage, int type, long triggerMillis,
            boolean whileIdle, int requestCode) throws Exception {
        Log.d(TAG, "Setting an alarm at " + triggerMillis + " (in "
                + (triggerMillis - SystemClock.elapsedRealtime()) + "ms)");
        final SetAlarmRequest areq = SetAlarmRequest.newBuilder()
                .setIntentAction(ACTION)
                .setType(type)
                .setAllowWhileIdle(whileIdle)
                .setTriggerTime(triggerMillis)
                .setRequestCode(requestCode)
                .build();
        final Payload response =
                mRpc.sendRequest(
                        targetPackage,
                        Payload.newBuilder()
                                .setTestServiceRequest(
                                        TestServiceRequest.newBuilder().setSetAlarm(areq))
                                .build());
        assertTrue(response.hasTestServiceResponse()
                && response.getTestServiceResponse().getSetAlarmAck());
    }

    /**
     * Return a service in the target package.
     */
    private String startService(String targetPackage, boolean foreground)
            throws Exception {
        final String action = "start_service_" + getRandomInt() + "_fg=" + foreground;

        final Payload response = mRpc.sendRequest(targetPackage,
                Payload.newBuilder().setTestServiceRequest(
                        TestServiceRequest.newBuilder().setStartService(
                                StartServiceRequest.newBuilder()
                                        .setForeground(foreground)
                                        .setAction(action).build()
                        )).build());
        assertTrue(response.hasTestServiceResponse()
                && response.getTestServiceResponse().getStartServiceAck());
        return action;
    }

    private void stopService(String targetPackage) throws Exception {
        final Payload response = mRpc.sendRequest(targetPackage,
                Payload.newBuilder().setTestServiceRequest(
                        TestServiceRequest.newBuilder().setStopService(true).build()).build());
        assertTrue(response.hasTestServiceResponse()
                && response.getTestServiceResponse().getStopServiceAck());
    }

    private void stopPackage(String packageName) throws Exception {
        stopService(packageName);
        runStopApp(packageName, true);
    }

    private static void sleepUninterruptiblyUntil(long untilElapsed) {
        final long now = SystemClock.elapsedRealtime();
        if (now < untilElapsed) {
            SystemClock.sleep(untilElapsed - now);
            if (Thread.currentThread().isInterrupted()) {
                Log.e(TAG, "Thread was interrupted while sleeping!");
            }
        } else {
            Log.d(TAG, "Skipping sleep. Now: " + now + " already past target time " + untilElapsed);
        }
    }

    @Test
    public void testAllowWhileIdleThrottled() throws Exception {
        final String targetPackage = APP_25_PACKAGE;

        runDumpsysBatteryUnplug();
        enableBatterySaver(true);

        stopPackage(targetPackage);

        SystemClock.sleep(ALLOW_WHILE_IDLE_COMPAT_WINDOW);
        // First quota alarms shouldn't be throttled, as there were no alarms in the past window.

        final long firstTrigger = SystemClock.elapsedRealtime() + ONE_SECOND;
        long lastTriggerElapsed = 0;
        for (int i = 0; i < ALLOW_WHILE_IDLE_QUOTA_PRE_S; i++) {
            lastTriggerElapsed = firstTrigger + (i * ONE_SECOND);
            scheduleAlarm(targetPackage, AlarmManager.ELAPSED_REALTIME_WAKEUP, lastTriggerElapsed);
        }
        sleepUninterruptiblyUntil(lastTriggerElapsed);
        PollingCheck.waitFor(POLLING_WAIT_MILLIS,
                () -> (ALLOW_WHILE_IDLE_QUOTA_PRE_S == mAlarmCount.get()),
                "The first quota allow-while-idle alarms didn't fire in battery saver");

        // Subsequent ones should be throttled.
        mAlarmCount.set(0);

        // Check that the alarms scheduled now fire only after
        // (now + MIN_FUTURITY + ALLOW_WHILE_IDLE_WINDOW).
        final long nextAllowedMinElapsed = firstTrigger + ALLOW_WHILE_IDLE_COMPAT_WINDOW;
        final long triggerElapsed2 = SystemClock.elapsedRealtime() + ONE_SECOND;
        final long triggerElapsed3 = triggerElapsed2 + ONE_SECOND;

        assertTrue(
                "Not enough time remaining in window to reliably test throttling",
                triggerElapsed3 + WAIT_SLOP_FOR_ABSENCE < nextAllowedMinElapsed);

        scheduleAlarm(targetPackage, AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerElapsed2);
        scheduleAlarm(targetPackage, AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerElapsed3);

        sleepUninterruptiblyUntil(triggerElapsed3);
        assertEquals(
                "Alarms over quota fired in battery saver",
                0,
                PollingCheck.waitFor(
                                WAIT_SLOP_FOR_ABSENCE, () -> mAlarmCount.get(), count -> count > 0)
                        .intValue());

        sleepUninterruptiblyUntil(nextAllowedMinElapsed);
        PollingCheck.waitFor(
                POLLING_WAIT_MILLIS,
                () -> (2 == mAlarmCount.get()),
                "Follow-up allow-while-idle alarms should go off when in quota");
    }

    @Test
    public void testAllowWhileIdleNotThrottledInFgs() throws Exception {
        final String targetPackage = APP_25_PACKAGE;

        runDumpsysBatteryUnplug();
        enableBatterySaver(true);

        stopPackage(targetPackage);

        // Start an FG service, which should exempt from any throttling.
        startService(targetPackage, true);

        final int overQuota = 10;
        // Should be significantly higher than the quota we've set.
        assertTrue(overQuota > ALLOW_WHILE_IDLE_QUOTA_PRE_S * 2);

        mAlarmCount.set(0);
        long lastTriggerElapsed = 0;
        for (int i = 0; i < overQuota; i++) {
            lastTriggerElapsed = SystemClock.elapsedRealtime() + (i + 1) * 100;
            scheduleAlarm(targetPackage, AlarmManager.ELAPSED_REALTIME_WAKEUP, lastTriggerElapsed);
        }
        sleepUninterruptiblyUntil(lastTriggerElapsed);
        PollingCheck.waitFor(
                POLLING_WAIT_MILLIS,
                () -> (overQuota == mAlarmCount.get()),
                "Allow-while-idle alarms shouldn't be throttled in battery saver after FG service"
                        + " started");

        stopService(targetPackage);
    }

    @Test
    public void testAlarmsThrottled_unblockedOnFgs() throws Exception {
        final String targetPackage = APP_25_PACKAGE;

        runDumpsysBatteryUnplug();
        enableBatterySaver(true);

        stopPackage(targetPackage);

        // When battery saver is enabled, alarms should be blocked.
        final long triggerElapsed = SystemClock.elapsedRealtime() + ONE_SECOND;
        scheduleAlarm(
                targetPackage,
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerElapsed,
                /* whileIdle= */ false,
                1);
        sleepUninterruptiblyUntil(triggerElapsed + WAIT_SLOP_FOR_ABSENCE);
        assertEquals(
                "Non-while-idle alarm should be blocked in battery saver", 0, mAlarmCount.get());

        // Start an FG service -> should unblock the alarm.
        startService(targetPackage, true);
        PollingCheck.waitFor(POLLING_WAIT_MILLIS, () -> mAlarmCount.get() == 1,
                "Alarm should fire for an FG app");
    }

    @Test
    public void testAlarmsThrottled_unblockedOnBsDisabled() throws Exception {
        final String targetPackage = APP_25_PACKAGE;

        runDumpsysBatteryUnplug();
        enableBatterySaver(true);

        stopPackage(targetPackage);

        // When battery saver is enabled, alarms should be blocked.
        final long triggerElapsed = SystemClock.elapsedRealtime() + ONE_SECOND;
        scheduleAlarm(
                targetPackage,
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerElapsed,
                /* whileIdle= */ false,
                1);
        sleepUninterruptiblyUntil(triggerElapsed + WAIT_SLOP_FOR_ABSENCE);
        assertEquals(
                "Non-while-idle alarm should be blocked in battery saver", 0, mAlarmCount.get());

        // Disable EBS -> should unblock the alarm.
        enableBatterySaver(false);
        PollingCheck.waitFor(
                // TODO: b/414451922 - Change to wait for the handler flush when available.
                POLLING_WAIT_FOR_HANDLER_FLUSH,
                () -> mAlarmCount.get() == 1,
                "Alarm should fire after battery saver is disabled");
    }
}
