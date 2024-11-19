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

package android.app.cts;

import static android.app.stubs.LocalForegroundServiceMedia.ACTION_START_FGSM_RESULT;

import static com.android.compatibility.common.util.SystemUtil.runShellCommand;

import static org.junit.Assert.assertTrue;

import android.accessibilityservice.AccessibilityService;
import android.app.ActivityManager;
import android.app.Instrumentation;
import android.app.cts.android.app.cts.tools.WaitForBroadcast;
import android.app.cts.android.app.cts.tools.WatchUidRunner;
import android.app.stubs.CommandReceiver;
import android.app.stubs.LocalForegroundServiceMedia;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.ServiceInfo;
import android.os.Bundle;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.SystemUtil;
import com.android.media.flags.Flags;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ActivityManagerNotifyMediaFGSTypeTest {
    private static final String TAG = ActivityManagerNotifyMediaFGSTypeTest.class.getName();
    private static final String STUB_PACKAGE_NAME = "android.app.stubs";
    private static final String PACKAGE_NAME_APP1 = "com.android.app1";
    private static final int WAITFOR_MSEC = 10000;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();
    private Context mContext;
    private Context mTargetContext;
    private Instrumentation mInstrumentation;
    private ActivityManager mActivityManager;


    @Before
    public void setUp() throws Exception {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mContext = mInstrumentation.getContext();
        mTargetContext = mInstrumentation.getTargetContext();
        mActivityManager = mContext.getSystemService(ActivityManager.class);
        CtsAppTestUtils.turnScreenOn(mInstrumentation, mContext);
        cleanUp();
    }

    @After
    public void tearDown() throws Exception {
        cleanUp();
    }

    private void cleanUp() {
        SystemUtil.runWithShellPermissionIdentity(() -> {
            mActivityManager.forceStopPackage(PACKAGE_NAME_APP1);
        });
        // Make sure we are in Home screen.
        mInstrumentation.getUiAutomation().performGlobalAction(
                AccessibilityService.GLOBAL_ACTION_HOME);
    }

    private int setupMediaForegroundService() throws Exception {
        ApplicationInfo app1Info = mContext.getPackageManager().getApplicationInfo(
                PACKAGE_NAME_APP1, 0);
        WatchUidRunner uid1Watcher = new WatchUidRunner(mInstrumentation, app1Info.uid,
                WAITFOR_MSEC);
        try {
            WaitForBroadcast waiter = new WaitForBroadcast(mTargetContext);

            // Put APP1 in TOP state.
            CommandReceiver.sendCommand(mContext, CommandReceiver.COMMAND_START_ACTIVITY,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP1, 0, null);
            uid1Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_TOP);

            // Start the media foreground service in APP1.
            waiter.prepare(ACTION_START_FGSM_RESULT);
            Bundle bundle = new Bundle();
            bundle.putInt(LocalForegroundServiceMedia.EXTRA_FOREGROUND_SERVICE_TYPE,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            CommandReceiver.sendCommand(mContext,
                    CommandReceiver.COMMAND_START_FOREGROUND_SERVICE_MEDIA, PACKAGE_NAME_APP1,
                    PACKAGE_NAME_APP1, 0, bundle);

            // Stop the activity.
            CommandReceiver.sendCommand(mContext, CommandReceiver.COMMAND_STOP_ACTIVITY,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP1, 0, null);

            uid1Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_FG_SERVICE);
            Intent resultIntent = waiter.doWait(WAITFOR_MSEC);
            return resultIntent.getIntExtra(LocalForegroundServiceMedia.FGSM_NOTIFICATION_ID, -1);
        } finally {
            uid1Watcher.finish();
        }
    }

    private void cleanUpMediaForegroundService() throws Exception {
        ApplicationInfo app1Info = mContext.getPackageManager().getApplicationInfo(
                PACKAGE_NAME_APP1, 0);
        WatchUidRunner uid1Watcher = new WatchUidRunner(mInstrumentation, app1Info.uid,
                WAITFOR_MSEC);
        try {
            WaitForBroadcast waiter = new WaitForBroadcast(mTargetContext);

            // Stop the media foreground service in APP1.
            waiter.prepare(ACTION_START_FGSM_RESULT);
            CommandReceiver.sendCommand(mContext,
                    CommandReceiver.COMMAND_STOP_FOREGROUND_SERVICE_MEDIA, PACKAGE_NAME_APP1,
                    PACKAGE_NAME_APP1, 0, null);
            uid1Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_CACHED_EMPTY);
        } finally {
            uid1Watcher.finish();
        }
    }

    @Test
    @RequiresFlagsEnabled(
            Flags.FLAG_ENABLE_NOTIFYING_ACTIVITY_MANAGER_WITH_MEDIA_SESSION_STATUS_CHANGE)
    public void testNotifyInactiveMediaForegroundService() throws Exception {
        ApplicationInfo app1Info = mContext.getPackageManager().getApplicationInfo(
                PACKAGE_NAME_APP1, 0);
        WatchUidRunner uid1Watcher = new WatchUidRunner(mInstrumentation, app1Info.uid,
                WAITFOR_MSEC);
        // start a media fgs
        final int notificationId = setupMediaForegroundService();
        assertTrue("Failed to start media foreground service with notification",
                notificationId > 0);
        runShellCommand(mInstrumentation,
                String.format("am set-media-foreground-service inactive --user %d %s %d",
                        mContext.getUserId(), PACKAGE_NAME_APP1, notificationId));

        uid1Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_SERVICE);
        cleanUpMediaForegroundService();
        uid1Watcher.finish();
    }
}
