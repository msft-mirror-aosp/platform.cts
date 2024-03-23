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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.ActivityManager;
import android.app.ApplicationStartInfo;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ShellIdentityUtils;
import com.android.compatibility.common.util.SystemUtil;

import com.google.errorprone.annotations.FormatMethod;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.Map;

@RunWith(AndroidJUnit4.class)
public final class ActivityManagerAppStartInfoTest {
    private static final String TAG = ActivityManagerAppStartInfoTest.class.getSimpleName();

    private static final String STUB_PACKAGE_NAME =
            "com.android.cts.launcherapps.simpleapp";
    private static final String SIMPLE_ACTIVITY = ".SimpleActivity";

    private static final int MAX_WAITS_FOR_START = 20;
    private static final int WAIT_FOR_START_MS = 400;

    private Context mContext;
    private Instrumentation mInstrumentation;
    private ActivityManager mActivityManager;
    private PackageManager mPackageManager;

    private int mStubPackageUid;

    @Before
    public void setUp() throws Exception {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mContext = mInstrumentation.getContext();
        mActivityManager = mContext.getSystemService(ActivityManager.class);
        mPackageManager = mContext.getPackageManager();

        mStubPackageUid = mPackageManager.getPackageUid(STUB_PACKAGE_NAME, 0);

        // Disable doze mode for test app
        executeShellCmd("cmd deviceidle whitelist +" + STUB_PACKAGE_NAME);

        // Ensure test app is enabled
        mInstrumentation.getUiAutomation().adoptShellPermissionIdentity(
                android.Manifest.permission.CHANGE_COMPONENT_ENABLED_STATE);
        mContext.getPackageManager().setApplicationEnabledSetting(
                STUB_PACKAGE_NAME,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                0);
    }

    @After
    public void tearDown() throws Exception {
        // Reenable doze mode for test app
        executeShellCmd("cmd deviceidle whitelist -" + STUB_PACKAGE_NAME);

        executeShellCmd("am force-stop " + STUB_PACKAGE_NAME);
        mInstrumentation.getUiAutomation().dropShellPermissionIdentity();
    }

    @Test
    public void testLauncherStart() throws Exception {
        clearHistoricalStartInfo();

        Intent intent =
                mPackageManager.getLaunchIntentForPackage(STUB_PACKAGE_NAME);
        mContext.startActivity(intent);

        ApplicationStartInfo info = waitForAppStart();

        verify(info, STUB_PACKAGE_NAME, STUB_PACKAGE_NAME, intent,
                ApplicationStartInfo.START_REASON_LAUNCHER,
                ApplicationStartInfo.START_TYPE_COLD,
                ApplicationStartInfo.LAUNCH_MODE_STANDARD,
                ApplicationStartInfo.STARTUP_STATE_FIRST_FRAME_DRAWN);

        verifyIds(info, 0, mStubPackageUid, mStubPackageUid, mStubPackageUid);
    }

    @Test
    public void testActivityStart() throws Exception {
        clearHistoricalStartInfo();

        executeShellCmd("am start -n " + STUB_PACKAGE_NAME + "/" + STUB_PACKAGE_NAME
                + SIMPLE_ACTIVITY);

        ApplicationStartInfo info = waitForAppStart();

        Intent intent = new Intent();
        intent.setComponent(ComponentName.createRelative(STUB_PACKAGE_NAME,
                SIMPLE_ACTIVITY));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        verify(info, STUB_PACKAGE_NAME, STUB_PACKAGE_NAME, intent,
                ApplicationStartInfo.START_REASON_START_ACTIVITY,
                ApplicationStartInfo.START_TYPE_COLD,
                ApplicationStartInfo.LAUNCH_MODE_STANDARD,
                ApplicationStartInfo.STARTUP_STATE_FIRST_FRAME_DRAWN);

        verifyIds(info, 0, mStubPackageUid, mStubPackageUid, mStubPackageUid);
    }

    /**
     * Start an app and make sure its record exists, then verify
     * the record is removed when the app is uninstalled.
     */
    @Test
    public void testAppRemoved() throws Exception {
        testActivityStart();

        executeShellCmd("pm uninstall " + STUB_PACKAGE_NAME);

        List<ApplicationStartInfo> list =
                ShellIdentityUtils.invokeMethodWithShellPermissions(
                    STUB_PACKAGE_NAME, 1,
                    mActivityManager::getExternalHistoricalProcessStartReasons,
                    android.Manifest.permission.DUMP);

        assertTrue(list != null && list.size() == 0);
    }

    private void clearHistoricalStartInfo() throws Exception {
        executeShellCmd("am clear-start-info --user all " + STUB_PACKAGE_NAME);
    }

    /** Query the app start info object until it indicates the startup is complete. */
    private ApplicationStartInfo waitForAppStart() {
        List<ApplicationStartInfo> list;

        for (int i = 0; i < MAX_WAITS_FOR_START; i++) {
            list = ShellIdentityUtils.invokeMethodWithShellPermissions(
                    STUB_PACKAGE_NAME, 1,
                    mActivityManager::getExternalHistoricalProcessStartReasons,
                    android.Manifest.permission.DUMP);

            if (list != null && list.size() == 1
                    && list.get(0).getStartupState()
                        == ApplicationStartInfo.STARTUP_STATE_FIRST_FRAME_DRAWN) {
                return list.get(0);
            }
            sleep(WAIT_FOR_START_MS);
        }

        fail("The app didn't finish starting in time.");
        return null;
    }

    private void sleep(long timeout) {
        try {
            Thread.sleep(timeout);
        } catch (InterruptedException e) {
        }
    }

    @FormatMethod
    private String executeShellCmd(String cmdFormat, Object... args) throws Exception {
        String cmd = String.format(cmdFormat, args);
        String result = SystemUtil.runShellCommand(mInstrumentation, cmd);
        Log.d(TAG, String.format("Output for '%s': %s", cmd, result));
        return result;
    }

    private void verifyIds(ApplicationStartInfo info,
            int pid, int realUid, int packageUid, int definingUid) {
        assertNotNull(info);

        assertEquals(pid, info.getPid());
        assertEquals(realUid, info.getRealUid());
        assertEquals(definingUid, info.getDefiningUid());
        assertEquals(packageUid, info.getPackageUid());
    }

    /**
     * Verify that the info matches the passed state.
     * Null arguments are skipped in verification.
     */
    private void verify(ApplicationStartInfo info,
            String packageName, String processName, Intent intent,
            int reason, int startType, int launchMode, int startupState) {
        assertNotNull(info);

        if (packageName != null) {
            assertTrue(packageName.equals(info.getPackageName()));
        }

        if (processName != null) {
            assertTrue(processName.equals(info.getProcessName()));
        }

        if (intent != null) {
            assertTrue(intent.filterEquals(info.getIntent()));
        }

        assertEquals(reason, info.getReason());
        assertEquals(startType, info.getStartType());
        assertEquals(launchMode, info.getLaunchMode());
        assertEquals(startupState, info.getStartupState());

        // Check that the appropriate timestamps exist based on the startup state
        // and that they're in the right order.
        Map<Integer, Long> timestamps = info.getStartupTimestamps();
        if (startupState == ApplicationStartInfo.STARTUP_STATE_STARTED) {
            Long launchTimestamp = timestamps.get(ApplicationStartInfo.START_TIMESTAMP_LAUNCH);
            assertTrue(launchTimestamp != null);
            assertTrue(launchTimestamp > 0);
        }

        if (startupState == ApplicationStartInfo.STARTUP_STATE_FIRST_FRAME_DRAWN) {
            Long launchTimestamp = timestamps.get(ApplicationStartInfo.START_TIMESTAMP_LAUNCH);
            assertTrue(launchTimestamp != null);
            assertTrue(launchTimestamp > 0);

            Long bindApplicationTimestamp = timestamps.get(
                    ApplicationStartInfo.START_TIMESTAMP_BIND_APPLICATION);
            assertTrue(bindApplicationTimestamp != null);
            assertTrue(bindApplicationTimestamp > 0);

            assertTrue(launchTimestamp < bindApplicationTimestamp);

            // TODO: Debug why START_TIMESTAMP_APPLICATION_ONCREATE and
            // START_TIMESTAMP_FIRST_FRAME do not appear in this case as well.
        }
    }
}
