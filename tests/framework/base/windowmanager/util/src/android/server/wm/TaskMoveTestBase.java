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

import static android.server.wm.StateLogger.logAlways;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.ConditionVariable;
import android.os.UserHandle;
import android.server.wm.app.Components;

import org.junit.After;
import org.junit.Before;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class TaskMoveTestBase extends MultiDisplayTestBase {

    protected static final int TIMEOUT_MS = 2000 * BuildUtils.HW_TIMEOUT_MULTIPLIER;
    protected static final ComponentName TEST_ACTIVITY = Components.TASK_MOVE_TEST_ACTIVITY;

    private Map<String, ConditionVariable> mBroadcastsReceived =
            Collections.synchronizedMap(new HashMap<>());
    private Map<String, Intent> mBroadcastsContentsReceived =
            Collections.synchronizedMap(new HashMap<>());
    private BroadcastReceiver mAppCommunicator =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    logAlways("Received an intent with action " + intent.getAction());
                    mBroadcastsContentsReceived.put(intent.getAction(), intent);
                    getBroadcastReceivedVariable(intent.getAction()).open();
                }
            };

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();

        mBroadcastsReceived.clear();
        mBroadcastsContentsReceived.clear();
        mContext.registerReceiver(mAppCommunicator, getIntentFilter(), Context.RECEIVER_EXPORTED);
        grantBrowserRole();
    }

    @After
    public void tearDown() {
        revokeBrowserRole();
        mContext.unregisterReceiver(mAppCommunicator);
        Components.forceStopPackage();
    }

    private ConditionVariable getBroadcastReceivedVariable(String action) {
        return mBroadcastsReceived.computeIfAbsent(action, k -> new ConditionVariable());
    }

    protected IntentFilter getIntentFilter() {
        return new IntentFilter();
    }

    protected void grantBrowserRole() {
        logAlways("Granting browser role");
        ShellCommandHelper.executeShellCommand(
                "cmd role add-role-holder --user "
                        + UserHandle.myUserId()
                        + " android.app.role.BROWSER "
                        + Components.getPackageName());
    }

    protected void revokeBrowserRole() {
        logAlways("Revoking browser role");
        ShellCommandHelper.executeShellCommand(
                "cmd role remove-role-holder --user "
                        + UserHandle.myUserId()
                        + " android.app.role.BROWSER "
                        + Components.getPackageName());
    }

    protected int createNewDisplay() {
        return createManagedVirtualDisplaySession()
                .setSimulateDisplay(true)
                .setSimulationDisplaySize(1920 /* width */, 1080 /* height */)
                .createDisplay()
                .mId;
    }

    protected boolean awaitBroadcast(String action) {
        final ConditionVariable cv = getBroadcastReceivedVariable(action);
        final boolean notified = cv.block(TIMEOUT_MS);
        cv.close();
        return notified;
    }

    protected void clearBroadcastData(String action) {
        getBroadcastReceivedVariable(action).close();
        mBroadcastsContentsReceived.remove(action);
    }

    protected Intent getIntentOfBroadcast(String action) {
        return mBroadcastsContentsReceived.get(action);
    }
}
