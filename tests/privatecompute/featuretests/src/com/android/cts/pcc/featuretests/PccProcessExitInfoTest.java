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

package com.android.cts.pcc.featuretests;

import static android.app.privatecompute.flags.Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT;

import static com.android.cts.pcc.featuretests.services.PccVictimService.COMMAND_EXIT;
import static com.android.cts.pcc.featuretests.services.PccVictimService.EXTRA_COMMAND;

import static org.junit.Assert.assertNotNull;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.app.privatecompute.PccClient;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Process;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.cts.pcc.featuretests.services.PccVictimService;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(BedsteadJUnit4.class)
@RequiresFlagsEnabled(FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
public class PccProcessExitInfoTest {
    private Context mContext;
    private String mPackageName;
    private ActivityManager mActivityManager;
    private int mPccId;

    @ClassRule @Rule public static final DeviceState sDeviceState = new DeviceState();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setup() {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mActivityManager = mContext.getSystemService(ActivityManager.class);
        mPackageName = mContext.getPackageName();
        mPccId = 30000 + Process.myUid() - Process.FIRST_APPLICATION_UID;
    }

    @Test
    public void verifyCleanExitReason() throws Exception {
        // 1. Send exit command
        sendCommand(COMMAND_EXIT);

        // 2. Wait for REASON_EXIT_SELF
        ApplicationExitInfo exitInfo = waitForExitReason(ApplicationExitInfo.REASON_EXIT_SELF);

        assertNotNull("Could not find exit record", exitInfo);
    }

    /**
     * Helper to poll the system for the exit reason. Android updates this asynchronously, so we
     * must poll.
     */
    private ApplicationExitInfo waitForExitReason(int expectedReason) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        long timeout = 5000L; // 5 seconds timeout

        while (System.currentTimeMillis() - startTime < timeout) {
            // Get most recent reasons (limit 5 is usually enough)
            List<ApplicationExitInfo> reasons =
                    mActivityManager.getHistoricalProcessExitReasons(null, 0, 5);

            for (ApplicationExitInfo info : reasons) {
                // Check if this record matches our process AND the expected reason
                if (info.getRealUid() == mPccId && info.getReason() == expectedReason) {
                    return info;
                }
            }

            // Sleep briefly to avoid hammering the system
            Thread.sleep(200);
        }
        return null;
    }

    private void sendCommand(String command) throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final IBinder[] binder = new IBinder[1];
        ServiceConnection connection =
                new ServiceConnection() {
                    @Override
                    public void onServiceConnected(ComponentName name, IBinder service) {
                        binder[0] = service;
                        latch.countDown();
                    }

                    @Override
                    public void onServiceDisconnected(ComponentName name) {}
                };

        ComponentName serviceComponent =
                new ComponentName(mPackageName, PccVictimService.class.getName());
        Intent intent = new Intent();
        intent.setComponent(serviceComponent);
        mContext.bindService(intent, connection, Context.BIND_AUTO_CREATE);

        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new RuntimeException("Failed to bind to service: " + serviceComponent);
        }
        PccClient pccClient = PccClient.createInstance(mContext, binder[0]);

        Bundle data = new Bundle();
        data.putString(EXTRA_COMMAND, command);

        pccClient.sendData(data);

        mContext.unbindService(connection);
    }
}
