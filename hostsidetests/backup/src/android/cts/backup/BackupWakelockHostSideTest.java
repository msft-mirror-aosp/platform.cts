/*
 * Copyright (C) 2020 The Android Open Source Project
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
 * limitations under the License
 */

package android.cts.backup;

import static android.cts.backup.KeyValueBackupRestoreHostSideTest.KEY_VALUE_RESTORE_APP_APK;
import static android.cts.backup.KeyValueBackupRestoreHostSideTest.KEY_VALUE_RESTORE_APP_PACKAGE;
import static android.cts.backup.KeyValueBackupRestoreHostSideTest.KEY_VALUE_RESTORE_DEVICE_TEST_NAME;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.platform.test.annotations.AppModeFull;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Locale;

/**
 * Test for checking that backup/restore operations acquire wakelock at the start and release it at
 * the end.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
@AppModeFull
public class BackupWakelockHostSideTest extends BaseBackupHostSideTest {
    private static final String TAG = "BackupWakelockHostSideTest";

    private static final String LOGCAT_FILTER =
            "BackupManagerService:* PerformBackupTask:* KeyValueBackupTask:* PFTBT:* "
                    + TAG + ":* *:S";
    private static final String KEY_VALUE_START = "Beginning backup of ";
    private static final String KEY_VALUE_SUCCESS_LOG = "K/V backup pass finished";
    private static final String RESTOREATINSTALL_LOG =
            "restoreAtInstall pkg=" + KEY_VALUE_RESTORE_APP_PACKAGE;
    private static final String RESTORECOMPLETE_LOG = "Restore complete";

    // %d should be replaced with user ID.
    private static final String WAKELOCK_ACQUIRED_LOG_FORMAT = "Acquired wakelock:*backup*-%d";
    private static final String WAKELOCK_RELEASED_LOG_FORMAT = "Released wakelock:*backup*-%d";

    private static final int TIMEOUT_SECS = 30;

    private int mCurrentUserId;

    private static String getWakelockAcquiredLog(int userId) {
        return String.format(Locale.ENGLISH, WAKELOCK_ACQUIRED_LOG_FORMAT, userId);
    }

    private static String getWakelockReleasedLog(int userId) {
        return String.format(Locale.ENGLISH, WAKELOCK_RELEASED_LOG_FORMAT, userId);
    }

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();

        mCurrentUserId = getBackupUtils().getCurrentUserId();
        installPackageAsUser(
                KEY_VALUE_RESTORE_APP_APK, /* grantPermission */ false, mCurrentUserId);
        clearPackageData(KEY_VALUE_RESTORE_APP_PACKAGE);
    }

    @After
    public void tearDown() throws Exception {
        clearBackupDataInLocalTransport(KEY_VALUE_RESTORE_APP_PACKAGE);
        assertNull(uninstallPackage(KEY_VALUE_RESTORE_APP_PACKAGE));
    }

    /**
     * Test that verifies requestBackup of key/value app acquires wakelock at the start and releases
     * it at the end.
     */
    @Test
    public void testRequestBackup_forKeyValue_acquiresAndReleasesWakelock() throws Exception {
        runDeviceSideProcedure("saveSharedPreferencesAndNotifyBackupManager");

        String startLog = mLogcatInspector.mark(TAG);
        runDeviceSideProcedure("requestBackup");

        mLogcatInspector.assertLogcatContainsInOrder(
                LOGCAT_FILTER,
                TIMEOUT_SECS,
                startLog,
                getWakelockAcquiredLog(mCurrentUserId),
                KEY_VALUE_START,
                KEY_VALUE_SUCCESS_LOG,
                getWakelockReleasedLog(mCurrentUserId));
    }

    /**
     * Test that verifies restore at install of key/value app acquires wakelock at the start and
     * releases it at the end.
     */
    @Test
    public void testRestoreAtInstall_forKeyValue_acquiresAndReleasesWakelock() throws Exception {
        runDeviceSideProcedure("saveSharedPreferencesAndNotifyBackupManager");
        runDeviceSideProcedure("requestBackup");
        assertNull(uninstallPackage(KEY_VALUE_RESTORE_APP_PACKAGE));

        String startLog = mLogcatInspector.mark(TAG);
        installPackageAsUser(
                KEY_VALUE_RESTORE_APP_APK, /* grantPermission */ false, mCurrentUserId);

        mLogcatInspector.assertLogcatContainsInOrder(
                LOGCAT_FILTER,
                TIMEOUT_SECS,
                startLog,
                RESTOREATINSTALL_LOG,
                getWakelockAcquiredLog(mCurrentUserId),
                RESTORECOMPLETE_LOG,
                getWakelockReleasedLog(mCurrentUserId));
    }

    private void runDeviceSideProcedure(String procedure) throws Exception {
        assertTrue("Device-side procedure failed: " + procedure,
                runDeviceTests(KEY_VALUE_RESTORE_APP_PACKAGE, KEY_VALUE_RESTORE_DEVICE_TEST_NAME,
                        procedure));
    }
}
