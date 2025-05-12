/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
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

import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.DeviceTestRunOptions;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2025_22425 extends NonRootSecurityTestCase {

    @AsbSecurityTest(cveBugId = 364604008)
    @Test
    public void testPocCVE_2025_22425() {
        final String helperApk = "CVE-2025-22425-helper.apk";
        ITestDevice device = getDevice();

        try (AutoCloseable withHelperApp =
                withHelperApp(
                        device,
                        "android.security.cts.CVE_2025_22425_helper",
                        "/data/local/tmp/" + helperApk,
                        helperApk)) {

            // Install test app
            installPackage("CVE-2025-22425-test.apk");

            // Run the device-side test
            runDeviceTests(new DeviceTestRunOptions("android.security.cts.CVE_2025_22425"));
        } catch (Exception e) {
            assume().that(e).isNull();
        }
    }

    private AutoCloseable withHelperApp(
            ITestDevice device, String helperPackage, String appPath, String apkName) {
        pocPusher.appendBitness(false);

        // Push the helper APK to /data/local/tmp/
        try {
            pocPusher.pushFile(apkName, appPath);
        } catch (Exception e) {
            assume().that(e).isNull();
        }
        return () -> {
            try {
                device.deleteFile(appPath);
                uninstallPackage(helperPackage);
            } catch (Exception ignored) {
                // Ignore unexpected exceptions
            }
        };
    }
}
