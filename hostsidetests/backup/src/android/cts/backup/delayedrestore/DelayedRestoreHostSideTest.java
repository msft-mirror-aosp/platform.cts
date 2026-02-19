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

package android.cts.backup.delayedrestore;

import static org.junit.Assert.assertTrue;

import android.cts.backup.BaseBackupHostSideTest;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.host.HostFlagsValueProvider;
import com.android.compatibility.common.util.BackupUtils;
import com.android.server.backup.Flags;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import java.io.IOException;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
@AppModeFull
public class DelayedRestoreHostSideTest extends BaseBackupHostSideTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            HostFlagsValueProvider.createCheckFlagsRule(this::getDevice);

    private static final String FULL_APK = "DelayedFullRestoreApp.apk";
    private static final String FULL_APP = "android.cts.backup.delayedfullrestoreapp";
    private static final String FULL_APP_TEST_CLASS = FULL_APP + ".DelayedFullRestoreDeviceTest";

    private static final String KV_APK = "DelayedRestoreApp.apk";
    private static final String KV_APP = "android.cts.backup.delayedrestoreapp";
    private static final String KV_APP_TEST_CLASS = KV_APP + ".DelayedRestoreDeviceTest";

    private static final String DEPENDENCY_APK = "DelayedRestoreDependencyApp.apk";
    private static final String DEPENDENCY_APP = "android.cts.backup.delayedrestoredependency";

    private static final String TAG = "DelayedRestoreHostSideTest";

    private static final String LOGCAT_FILTER =
            TAG
                    + ":* DelayedRestoreBackupAgent:* DelayedFullBackupAgent:*"
                    + " TestBlobBackupHelper:* *:S";

    private static final String SCHEDULE_LOG = "Scheduled delayed full restore: true";
    private static final String FULL_RESTORE_LOG = "onDelayedFullRestore called!";
    private static final String HELPER_RESTORE_LOG =
            "delayedRestoreEntity called for key: delayed_backup_file";

    private static final int TIMEOUT_SECS = 30;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        installPackage(KV_APK);
        clearPackageData(KV_APP);
        installPackage(FULL_APK);
        clearPackageData(FULL_APP);

        getDevice().executeShellCommand("pm grant " + KV_APP + " android.permission.SCHEDULE_DELAYED_RESTORE");
        getDevice().executeShellCommand("pm grant " + FULL_APP + " android.permission.SCHEDULE_DELAYED_RESTORE");
    }

    @After
    public void tearDown() throws Exception {
        getDevice().uninstallPackage(KV_APP);
        getDevice().uninstallPackage(FULL_APP);
        getDevice().uninstallPackage(DEPENDENCY_APP);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_DELAYED_RESTORE_API)
    public void testDelayedRestoreForFullBackupApp() throws Exception {
        checkDeviceTest(FULL_APP, FULL_APP_TEST_CLASS, "assertFilesDontExist");
        checkDeviceTest(FULL_APP, FULL_APP_TEST_CLASS, "writeFilesAndAssertSuccess");

        getBackupUtils().backupNowAndAssertSuccess(FULL_APP);

        checkDeviceTest(FULL_APP, FULL_APP_TEST_CLASS, "clearFilesAndAssertSuccess");

        String startLog = mLogcatInspector.mark(TAG);

        getBackupUtils().restoreAndAssertSuccess(BackupUtils.LOCAL_TRANSPORT_TOKEN, FULL_APP);

        checkDeviceTest(FULL_APP, FULL_APP_TEST_CLASS, "assertFilesRestored");

        mLogcatInspector.assertLogcatContainsInOrder(
                LOGCAT_FILTER, TIMEOUT_SECS, startLog, SCHEDULE_LOG);

        installPackage(DEPENDENCY_APK);

        mLogcatInspector.assertLogcatContainsInOrder(
                LOGCAT_FILTER, TIMEOUT_SECS, startLog, FULL_RESTORE_LOG);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_DELAYED_RESTORE_API)
    public void testDelayedRestoreForKeyValueBackupApp() throws Exception {
        checkDeviceTest(KV_APP, KV_APP_TEST_CLASS, "assertFilesDontExist");
        checkDeviceTest(KV_APP, KV_APP_TEST_CLASS, "writeFilesAndAssertSuccess");

        getBackupUtils().backupNowAndAssertSuccess(KV_APP);

        checkDeviceTest(KV_APP, KV_APP_TEST_CLASS, "clearFilesAndAssertSuccess");

        String startLog = mLogcatInspector.mark(TAG);

        getBackupUtils().restoreAndAssertSuccess(BackupUtils.LOCAL_TRANSPORT_TOKEN, KV_APP);

        checkDeviceTest(KV_APP, KV_APP_TEST_CLASS, "assertSomeFilesRestored");

        mLogcatInspector.assertLogcatContainsInOrder(
                LOGCAT_FILTER, TIMEOUT_SECS, startLog, SCHEDULE_LOG);

        installPackage(DEPENDENCY_APK);

        mLogcatInspector.assertLogcatContainsInOrder(
                LOGCAT_FILTER, TIMEOUT_SECS, startLog, HELPER_RESTORE_LOG);

        checkDeviceTest(KV_APP, KV_APP_TEST_CLASS, "assertAllFilesRestored");
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_DELAYED_RESTORE_API)
    public void testDelayedRestoreDeviceSideTests() throws Exception {
        checkDeviceTest(KV_APP, KV_APP_TEST_CLASS, "testRequest_BuilderAndParcelable");
        checkDeviceTest(KV_APP, KV_APP_TEST_CLASS, "testSchedule_ReturnFalse_OutsideRestore");
    }
}
