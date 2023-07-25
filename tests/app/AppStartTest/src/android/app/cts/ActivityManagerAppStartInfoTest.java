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

import android.app.ActivityManager;
import android.app.ApplicationStartInfo;
import android.app.Instrumentation;
import android.content.Context;
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

@RunWith(AndroidJUnit4.class)
public final class ActivityManagerAppStartInfoTest {
    private static final String TAG = ActivityManagerAppStartInfoTest.class.getSimpleName();

    private static final String STUB_PACKAGE_NAME =
            "com.android.cts.launcherapps.simpleapp";
    private static final String SIMPLE_ACTIVITY = ".SimpleActivity";

    private Context mContext;
    private Instrumentation mInstrumentation;
    private int mStubPackageUid;
    private ActivityManager mActivityManager;

    @Before
    public void setUp() throws Exception {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mContext = mInstrumentation.getContext();
        mStubPackageUid = mContext.getPackageManager().getPackageUid(STUB_PACKAGE_NAME, 0);
        mActivityManager = mContext.getSystemService(ActivityManager.class);

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

        executeShellCmd("monkey -p " + STUB_PACKAGE_NAME
                + " -c android.intent.category.LAUNCHER 1");

        List<ApplicationStartInfo> list =
                ShellIdentityUtils.invokeMethodWithShellPermissions(
                    STUB_PACKAGE_NAME, 1,
                    mActivityManager::getExternalHistoricalProcessStartReasons,
                    android.Manifest.permission.DUMP);

        assertTrue(list != null && list.size() == 1);
        verify(list.get(0), mStubPackageUid, STUB_PACKAGE_NAME);
    }

    @Test
    public void testActivityStart() throws Exception {
        clearHistoricalStartInfo();

        executeShellCmd("am start -n " + STUB_PACKAGE_NAME + "/" + STUB_PACKAGE_NAME
                + SIMPLE_ACTIVITY);

        List<ApplicationStartInfo> list =
                ShellIdentityUtils.invokeMethodWithShellPermissions(
                    STUB_PACKAGE_NAME, 1,
                    mActivityManager::getExternalHistoricalProcessStartReasons,
                    android.Manifest.permission.DUMP);

        assertTrue(list != null && list.size() == 1);
        verify(list.get(0), mStubPackageUid, STUB_PACKAGE_NAME);
    }

    @Test
    public void testAppRemoved() throws Exception {
        // Start an app and make sure its record exists, then verify
        // the record is removed when the app is uninstalled.
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

    @FormatMethod
    private String executeShellCmd(String cmdFormat, Object... args) throws Exception {
        String cmd = String.format(cmdFormat, args);
        String result = SystemUtil.runShellCommand(mInstrumentation, cmd);
        Log.d(TAG, String.format("Output for '%s': %s", cmd, result));
        return result;
    }

    private void verify(ApplicationStartInfo info, int uid, String processName) {
        assertNotNull(info);
        assertEquals(uid, info.getRealUid());
        if (processName != null) {
            assertEquals(processName, info.getProcessName());
        }
    }
}
