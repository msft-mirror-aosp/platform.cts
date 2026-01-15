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

package com.android.tests.privatecompute.host;

import static com.google.common.truth.Truth.assertThat;

import android.app.privatecompute.flags.Flags;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.host.HostFlagsValueProvider;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

/** Host side backup tests for pcc processes. */
@RunWith(DeviceJUnit4ClassRunner.class)
public class PccBackupHostTest extends BaseHostJUnit4Test {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            HostFlagsValueProvider.createCheckFlagsRule(this::getDevice, this.getClass());

    private static final String PCC_BACKUP_TEST_APK = "PccBackupApp.apk";
    private static final String PCC_MALFORMED_BACKUP_TEST_APK = "PccMalformedBackupApp.apk";
    private static final String PCC_BACKUP_APP_PACKAGE = "android.cts.pcc.host.backupapp";
    private static final String PCC_MALFORMED_APP_PACKAGE =
            "android.cts.pcc.host.backupappmalformed";

    @After
    public void tearDown() throws Exception {
        getDevice().uninstallPackage(PCC_BACKUP_APP_PACKAGE);
        getDevice().uninstallPackage(PCC_MALFORMED_APP_PACKAGE);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
    public void testMalformedAppInstallationFails() throws Exception {
        File apkFile = getTestInformation().getDependencyFile(PCC_MALFORMED_BACKUP_TEST_APK, false);

        String installResult = getDevice().installPackage(apkFile, false);

        assertThat(installResult).isNotNull();
        assertThat(installResult).contains("INSTALL_PARSE_FAILED_MANIFEST_MALFORMED");
        assertThat(installResult)
                .contains(
                        "Application has private compute core backup agent without other private"
                                + " compute core components");
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
    public void testAppInstallationWithBackupAgentSucceeds() throws Exception {
        File apkFile = getTestInformation().getDependencyFile(PCC_BACKUP_TEST_APK, false);

        String installResult = getDevice().installPackage(apkFile, true);

        // installPackage returns null on success
        assertThat(installResult).isNull();
    }
}
