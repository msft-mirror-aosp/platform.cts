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

package android.security.cts;

import static com.google.common.truth.TruthJUnit.assume;

import android.platform.test.annotations.AsbSecurityTest;

import com.android.sts.common.UserUtils.SecondaryUser;
import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.DeviceTestRunOptions;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2025_32326 extends NonRootSecurityTestCase {

    @Test
    @AsbSecurityTest(cveBugId = 365739560)
    public void testPocCVE_2025_32326() {
        try {
            ITestDevice device = getDevice();
            final String helperApk = "CVE-2025-32326-helper.apk";
            final String helperApkPath = "/data/local/tmp/" + helperApk;
            final String helperPkgName = "android.security.cts.CVE_2025_32326_helper";

            // Push helper apk into '/data/local/tmp'
            try (AutoCloseable withHelperAppInTempFolder =
                    withHelperAppInTempFolder(device, helperPkgName, helperApkPath, helperApk)) {
                // Create a restricted user
                try (AutoCloseable asRestrictedUser =
                        new SecondaryUser(device)
                                .name("CVE_2025_32326_restricted_user")
                                .restricted()
                                .withUser()) {
                    // Install test app and run device test
                    installPackage("CVE-2025-32326-test.apk");
                    runDeviceTests(new DeviceTestRunOptions("android.security.cts.CVE_2025_32326"));
                }
            }
        } catch (Exception e) {
            assume().that(e).isNull();
        }
    }

    private AutoCloseable withHelperAppInTempFolder(
            ITestDevice device, String helperPkgName, String helperApkPath, String helperApk) {
        pocPusher.appendBitness(false);

        // Push the helper APK to '/data/local/tmp'
        try {
            pocPusher.pushFile(helperApk, helperApkPath);
        } catch (Exception e) {
            assume().that(e).isNull();
        }
        return () -> {
            try {
                device.deleteFile(helperApkPath);
                uninstallPackage(helperPkgName);
            } catch (Exception e) {
                CLog.d("Exception occurred while removing helper app: " + e.toString());
            }
        };
    }
}
