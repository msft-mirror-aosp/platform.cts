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

package android.backup.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import android.Manifest;
import android.app.backup.BackupManager;
import android.app.backup.BackupObserver;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.permissions.annotations.EnsureHasPermission;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public class PccBackupAgentTest extends BaseBackupCtsTest {
    private static final String PCC_APP_PACKAGE = "android.cts.backup.backupagentprocesspccapp";
    private static final int BACKUP_TIMEOUT = 30_000;

    @ClassRule @Rule public static final DeviceState sDeviceState = new DeviceState();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private BackupManager mBackupManager;
    private TestBackupObserver mBackupObserver;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        mBackupManager = new BackupManager(mInstrumentation.getTargetContext());
        mBackupObserver = new TestBackupObserver();
    }

    @After
    public void tearDown() throws Exception {
        clearBackupDataInLocalTransport(PCC_APP_PACKAGE);

        setLocalTransportParameters("");
    }

    @Test
    @RequiresFlagsEnabled(android.app.privatecompute.flags.Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
    @EnsureHasPermission(Manifest.permission.BACKUP)
    public void testPccAgent_transportEncrypted_runsInPccProcess() throws Exception {
        if (!isBackupSupported()) {
            return;
        }

        setLocalTransportParameters("is_encrypted=true");

        runBackupAndAssertStatus(BackupManager.SUCCESS);
    }

    @Test
    @RequiresFlagsEnabled(android.app.privatecompute.flags.Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
    @EnsureHasPermission(Manifest.permission.BACKUP)
    public void testPccAgent_transportNotEncrypted_backupFails() throws Exception {
        if (!isBackupSupported()) {
            return;
        }

        setLocalTransportParameters("is_encrypted=false");

        runBackupAndAssertStatus(BackupManager.ERROR_BACKUP_NOT_ALLOWED);
    }

    @Test
    @RequiresFlagsEnabled(android.app.privatecompute.flags.Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
    @EnsureHasPermission(Manifest.permission.BACKUP)
    public void testPccAgent_d2d_transportNotEncrypted_backupSucceeds() throws Exception {
        if (!isBackupSupported()) {
            return;
        }

        setLocalTransportParameters("is_encrypted=false,is_device_transfer=true");

        runBackupAndAssertStatus(BackupManager.SUCCESS);
    }

    private void runBackupAndAssertStatus(int expectedStatus) throws Exception {
        getBackupUtils()
                .executeShellCommandSync(
                        String.format(
                                "cmd package unstop --user %d %s",
                                getBackupUtils().getCurrentUserId(), PCC_APP_PACKAGE));

        mBackupManager.requestBackup(new String[] {PCC_APP_PACKAGE}, mBackupObserver, null, 0);
        waitUntilBackupFinished();

        assertEquals(expectedStatus, mBackupObserver.backupStatus);
    }

    private void waitUntilBackupFinished() {
        long startTimeMillis = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTimeMillis < BACKUP_TIMEOUT) {
            if (mBackupObserver.isFinished) {
                return;
            }
        }
        fail("Timeout waiting for backup to finish.");
    }

    private static class TestBackupObserver extends BackupObserver {
        public boolean isFinished;
        public int backupStatus;

        @Override
        public void onResult(String currentBackupPackage, int status) {
            backupStatus = status;
        }

        @Override
        public void backupFinished(int status) {
            isFinished = true;
        }
    }
}
