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
import static android.server.wm.app.Components.TaskMoveTestActivity.ACTION_CHECK_IS_TASK_MOVE_ALLOWED;
import static android.server.wm.app.Components.TaskMoveTestActivity.ACTION_NOTIFY_TASK_MOVE_ALLOWED_RESULT;
import static android.server.wm.app.Components.TaskMoveTestActivity.EXTRA_DISPLAY_ID_KEY;
import static android.server.wm.app.Components.TaskMoveTestActivity.EXTRA_SYNC_EXCEPTION_KEY;
import static android.server.wm.app.Components.TaskMoveTestActivity.EXTRA_TASK_MOVE_ALLOWED_RESULT;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

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
        final IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_NOTIFY_TASK_MOVE_ALLOWED_RESULT);
        return filter;
    }

    protected void assertIsTaskMoveAllowedOnDisplayThrownException(
            int displayId, Class<?> exceptionClass) {
        final Intent response = askIfTaskMoveAllowedOnDisplay(displayId);
        assertTrue(response.getParcelableExtra(EXTRA_SYNC_EXCEPTION_KEY, exceptionClass) != null);
    }

    protected boolean getIsTaskMoveAllowedOnDisplay(int displayId) {
        final Intent response = askIfTaskMoveAllowedOnDisplay(displayId);

        if (!response.hasExtra(EXTRA_TASK_MOVE_ALLOWED_RESULT)) {
            if (response.hasExtra(EXTRA_SYNC_EXCEPTION_KEY)) {
                fail(
                        "The activity notified that the isTaskMoveAllowedOnDisplay request thrown"
                                + " an exception: "
                                + response.getParcelableExtra(
                                                EXTRA_SYNC_EXCEPTION_KEY, Exception.class)
                                        .getMessage());
            } else {
                fail(
                        "The activity has not notified about task movability on display "
                                + displayId
                                + ".");
            }
        }

        return response.getBooleanExtra(EXTRA_TASK_MOVE_ALLOWED_RESULT, false);
    }

    // Fails the test if the activity does not respond.
    private Intent askIfTaskMoveAllowedOnDisplay(int displayId) {
        logAlways("Sending ACTION_CHECK_IS_TASK_MOVE_ALLOWED intent with displayId = " + displayId);
        mContext.sendBroadcast(
                new Intent(ACTION_CHECK_IS_TASK_MOVE_ALLOWED)
                        .setFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                        .putExtra(EXTRA_DISPLAY_ID_KEY, displayId));
        final boolean notified = awaitBroadcast(ACTION_NOTIFY_TASK_MOVE_ALLOWED_RESULT);
        final Intent intent =
                mBroadcastsContentsReceived.get(ACTION_NOTIFY_TASK_MOVE_ALLOWED_RESULT);

        if (!notified || intent == null) {
            fail(
                    "The activity has not notified about task movability on display "
                            + displayId
                            + ".");
        }

        return intent;
    }

    protected void assumeTaskMoveAllowedOnDisplay(int displayId) {
        assumeTrue(
                "Only test when task moving is allowed on the display.",
                getIsTaskMoveAllowedOnDisplay(displayId));
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
