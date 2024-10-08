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

package android.security.cts;

import static com.android.sts.common.CommandUtil.runAndCheck;

import static com.google.common.truth.TruthJUnit.assume;

import android.platform.test.annotations.AsbSecurityTest;

import com.android.sts.common.UserUtils.SecondaryUser;
import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.DeviceTestRunOptions;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2024_40382 extends NonRootSecurityTestCase {

    @AsbSecurityTest(cveBugId = 296915959)
    @Test
    public void testPocCVE_2024_40382() {
        try {
            ITestDevice device = getDevice();
            final int primaryUserId = device.getCurrentUser();

            // Install test-app in primary user
            installPackage("CVE-2024-40382.apk", "-g");

            // Create a user, 'cve_2024_40382_user', and switch to it
            try (AutoCloseable asSecondaryUser =
                    new SecondaryUser(device).name("cve_2024_40382_user").doSwitch().withUser()) {

                // Capture Screenshot
                runAndCheck(device, "input keyevent KEYCODE_SYSRQ");

                // Switch back to primary user
                assume().withMessage("Failed to switch user back to primary user")
                        .that(device.switchUser(primaryUserId))
                        .isTrue();

                // Run DeviceTest
                runDeviceTests(
                        new DeviceTestRunOptions("android.security.cts.CVE_2024_40382")
                                .setDisableHiddenApiCheck(true));
            }
        } catch (Exception e) {
            assume().that(e).isNull();
        }
    }
}
