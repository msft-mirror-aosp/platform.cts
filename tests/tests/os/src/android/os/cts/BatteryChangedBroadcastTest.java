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

package android.os.cts;

import static android.content.Intent.ACTION_BATTERY_CHANGED;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.SystemUtil;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class BatteryChangedBroadcastTest {

    private static final int WAIT_TO_RECEIVE_THE_BROADCAST_MS = 10000;
    private static final Context CONTEXT =
            InstrumentationRegistry.getInstrumentation().getTargetContext();

    @BeforeClass
    public static void setUpClass() throws InterruptedException {
        SystemUtil.runShellCommand("cmd battery get -f current_now");
        waitAndAssertBroadcastReceived(null, -2);
    }

    @Test
    public void batteryLevelUpdated_broadcastSentImmediately() throws InterruptedException {
        final int currentBatteryLevel = getBatteryChangedIntExtra(BatteryManager.EXTRA_LEVEL);
        final int updatedBatteryLevel = currentBatteryLevel == 100 ? 50 : 100;

        try {
            SystemUtil.runShellCommand("cmd battery set level " + updatedBatteryLevel);
            waitAndAssertBroadcastReceived(BatteryManager.EXTRA_LEVEL, updatedBatteryLevel);

            // trigger the broadcast again to verify that it is sent immediately.
            final int newUpdatedBatteryLevel = updatedBatteryLevel == 100 ? 50 : 100;

            SystemUtil.runShellCommand("cmd battery set level " + newUpdatedBatteryLevel);
            waitAndAssertBroadcastReceived(BatteryManager.EXTRA_LEVEL, newUpdatedBatteryLevel);
        } finally {
            SystemUtil.runShellCommand("cmd battery reset");
        }
    }

    @Test
    public void batteryStatusUpdated_broadcastSentImmediately() throws InterruptedException {
        final int currentBatteryStatus = getBatteryChangedIntExtra(BatteryManager.EXTRA_STATUS);
        final int updatedStatus = currentBatteryStatus == BatteryManager.BATTERY_STATUS_CHARGING
                ? BatteryManager.BATTERY_STATUS_DISCHARGING
                : BatteryManager.BATTERY_STATUS_CHARGING;

        try {
            SystemUtil.runShellCommand("cmd battery set status " + updatedStatus);
            waitAndAssertBroadcastReceived(BatteryManager.EXTRA_STATUS, updatedStatus);

            // trigger the broadcast again to verify that it is sent immediately.
            final int newUpdatedStatus = updatedStatus == BatteryManager.BATTERY_STATUS_CHARGING
                    ? BatteryManager.BATTERY_STATUS_DISCHARGING
                    : BatteryManager.BATTERY_STATUS_CHARGING;

            SystemUtil.runShellCommand("cmd battery set status " + newUpdatedStatus);
            waitAndAssertBroadcastReceived(BatteryManager.EXTRA_STATUS, newUpdatedStatus);
        } finally {
            SystemUtil.runShellCommand("cmd battery reset");
        }
    }

    private int getBatteryChangedIntExtra(String extra) {
        Intent intent = CONTEXT.registerReceiver(null, new IntentFilter(ACTION_BATTERY_CHANGED));
        assertNotNull(intent);
        return intent.getIntExtra(extra, -1);
    }

    private static void waitAndAssertBroadcastReceived(String extra, int updatedValue)
            throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        CONTEXT.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (extra == null) {
                    latch.countDown();
                } else {
                    final int value = intent.getIntExtra(extra, -1);
                    if (value == updatedValue) {
                        latch.countDown();
                    }
                }
            }
        }, new IntentFilter(ACTION_BATTERY_CHANGED));

        if (!latch.await(WAIT_TO_RECEIVE_THE_BROADCAST_MS, TimeUnit.MILLISECONDS)) {
            fail("Timed out waiting for the battery changed broadcast");
        }
    }
}
