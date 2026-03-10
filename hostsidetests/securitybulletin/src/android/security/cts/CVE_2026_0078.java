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

package android.security.cts;

import static com.google.common.truth.TruthJUnit.assume;

import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;
import com.android.tradefed.testtype.junit4.DeviceTestRunOptions;
import android.platform.test.annotations.AsbSecurityTest;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2026_0078 extends NonRootSecurityTestCase {

    static final String TEST_PKG = "android.security.cts.CVE_2026_0078_test";
    static final String TEST_APK = "CVE-2026-0078.apk";
    static final String TEST_ADMIN = TEST_PKG + ".PocDeviceAdminReceiver";

    @AsbSecurityTest(cveBugId = 445418705)
    @Test
    public void testPocCVE_2026_0078() throws Exception {
        ITestDevice device = getDevice();
        String adminComponent = TEST_PKG + "/" + TEST_ADMIN;
        try {
            installPackage(TEST_APK);
            device.executeShellCommand("dpm set-active-admin " + adminComponent);

            runDeviceTests(new DeviceTestRunOptions(TEST_PKG)
                    .setTestClassName(TEST_PKG + ".DeviceTest")
                    .setTestMethodName("testSetGlobalProxyWithOversizedStrings"));
        } catch (Exception e) {
            assume().that(e).isNull();
        } finally {
            device.executeShellCommand("dpm remove-active-admin " + adminComponent);
        }
    }
}
