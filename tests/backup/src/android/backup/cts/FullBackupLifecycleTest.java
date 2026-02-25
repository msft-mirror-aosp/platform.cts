/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.backup.cts;

import static com.android.compatibility.common.util.BackupUtils.LOCAL_TRANSPORT_TOKEN;

import static org.junit.Assert.assertEquals;

import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.server.backup.Flags;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Verifies that key methods are called in expected order during backup / restore. */
@RunWith(AndroidJUnit4.class)
public class FullBackupLifecycleTest extends BaseBackupCtsTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final String BACKUP_APP_NAME = "android.backup.app";

    private static final int LOCAL_TRANSPORT_CONFORMING_FILE_SIZE = 5 * 1024;

    private static final int TIMEOUT_SECONDS = 30;

    @Before
    public void setUp() throws Exception {
        super.setUp();
    }

    @After
    public void tearDown() throws Exception {
        clearLocalTransportParameters();
        super.tearDown();
    }

    @Test
    public void testExpectedMethodsCalledInOrder() throws Exception {
        if (!isBackupSupported()) {
            return;
        }
        String backupSeparator = markLogcat();

        // Make sure there's something to backup
        createTestFileOfSize(BACKUP_APP_NAME, LOCAL_TRANSPORT_CONFORMING_FILE_SIZE);

        // Request backup and wait for it to complete
        getBackupUtils().backupNowSync(BACKUP_APP_NAME);

        waitForLogcat(
                TIMEOUT_SECONDS, backupSeparator, "onCreate", "Full backup requested", "onDestroy");

        String restoreSeparator = markLogcat();

        // Now request restore and wait for it to complete
        getBackupUtils().restoreSync(LOCAL_TRANSPORT_TOKEN, BACKUP_APP_NAME);

        waitForLogcat(
                TIMEOUT_SECONDS,
                restoreSeparator,
                "onCreate",
                "onRestoreFile",
                "onRestoreFinished",
                "onDestroy");
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_CROSS_PLATFORM_TRANSFER)
    @Test
    public void testOnEstimateFullBackupBytesCalled() throws Exception {
        if (!isBackupSupported()) {
            return;
        }
        String separator = markLogcat();
        // Launch test app and create a small file
        createTestFileOfSize(BACKUP_APP_NAME, 10);

        // Request backup and wait for onEstimateFullBackupBytes event in logcat
        getBackupUtils().backupNowSync(BACKUP_APP_NAME);
        waitForLogcat(
                TIMEOUT_SECONDS,
                separator,
                "onEstimateFullBackupBytes",
                "Full backup requested",
                "Full backup requested");
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_CROSS_PLATFORM_TRANSFER)
    @Test
    public void testOnRestoreFileWithFullRestoreDataInputCalled() throws Exception {
        if (!isBackupSupported()) {
            return;
        }
        String backupSeparator = markLogcat();

        // Make sure there's something to backup
        createTestFileOfSize(BACKUP_APP_NAME, LOCAL_TRANSPORT_CONFORMING_FILE_SIZE);

        // Request backup and wait for it to complete
        getBackupUtils().backupNowSync(BACKUP_APP_NAME);

        waitForLogcat(
                TIMEOUT_SECONDS, backupSeparator, "onCreate", "Full backup requested", "onDestroy");

        String restoreSeparator = markLogcat();

        // Now request restore and wait for it to complete
        getBackupUtils().restoreSync(LOCAL_TRANSPORT_TOKEN, BACKUP_APP_NAME);

        waitForLogcat(
                TIMEOUT_SECONDS,
                restoreSeparator,
                "onCreate",
                "onRestoreFile",
                "onRestoreFile with FullRestoreDataInput",
                "onRestoreFinished",
                "onDestroy");
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_CROSS_PLATFORM_TRANSFER)
    @Test
    public void testCrossPlatformTransfer_transportFlagAndContentVersionSet() throws Exception {
        if (!isBackupSupported()) {
            return;
        }
        enableLocalTransportCrossPlatformTransfer();

        String backupSeparator = markLogcat();

        // Make sure there's something to backup
        createTestFileOfSize(BACKUP_APP_NAME, LOCAL_TRANSPORT_CONFORMING_FILE_SIZE);

        // Request backup and wait for it to complete
        getBackupUtils().backupNowSync(BACKUP_APP_NAME);

        waitForLogcat(
                TIMEOUT_SECONDS,
                backupSeparator,
                "onCreate",
                "Cross platform backup requested",
                "onDestroy");

        String restoreSeparator = markLogcat();

        // Now request restore and wait for it to complete
        getBackupUtils().restoreSync(LOCAL_TRANSPORT_TOKEN, BACKUP_APP_NAME);

        waitForLogcat(
                TIMEOUT_SECONDS,
                restoreSeparator,
                "onCreate",
                "Cross platform restore requested, content version is 1.0",
                "onRestoreFile with app version code ",
                "onRestoreFile with FullRestoreDataInput",
                "onRestoreFinished",
                "onDestroy");
    }

    private void enableLocalTransportCrossPlatformTransfer() throws Exception {
        int userId = getBackupUtils().getCurrentUserId();
        getBackupUtils()
                .executeShellCommandSync(
                        "settings --user "
                                + userId
                                + " put secure backup_local_transport_parameters"
                                + " is_cross_platform_transfer_ios=true");
        String output =
                getBackupUtils()
                        .executeShellCommandAndReturnOutput(
                                "settings get --user "
                                        + userId
                                        + " secure backup_local_transport_parameters");
        assertEquals("is_cross_platform_transfer_ios=true\n", output);
    }

    private void clearLocalTransportParameters() throws Exception {
        int userId = getBackupUtils().getCurrentUserId();
        getBackupUtils()
                .executeShellCommandSync(
                        "settings delete --user "
                                + userId
                                + " secure backup_local_transport_parameters");
    }
}
