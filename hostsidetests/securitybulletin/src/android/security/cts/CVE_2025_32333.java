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

import static com.android.sts.common.CommandUtil.runAndCheck;

import static com.google.common.truth.Truth.assertWithMessage;
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
public class CVE_2025_32333 extends NonRootSecurityTestCase {

    @Test
    @AsbSecurityTest(cveBugId = 409780975)
    public void testPocCVE_2025_32333() {
        try {
            // Install test app in primary user
            final ITestDevice device = getDevice();
            final int primaryUserId = device.getCurrentUser();
            installPackageAsUser("CVE-2025-32333.apk", false /* grantPermission */, primaryUserId);

            // Check whether 'Install unknown apps' permission is granted to test app or not
            final String testAppPackageName = "android.security.cts.CVE_2025_32333";
            String commandOutput =
                    runAndCheck(
                                    device,
                                    "appops query-op REQUEST_INSTALL_PACKAGES allow --user "
                                            + primaryUserId)
                            .getStdout();
            assume().withMessage("Test app already has Install unknown apps' permission")
                    .that(commandOutput.contains(testAppPackageName))
                    .isFalse();

            // Create new user
            final SecondaryUser secondaryUser = new SecondaryUser(device);
            try (AutoCloseable asSecondaryUser =
                    secondaryUser
                            .name("CVE_2025_32333_user")
                            .doSwitch()
                            .doSkipSetupWizard()
                            .withUser()) {
                // Install test-app in the secondary user.
                final int secondaryUserId = secondaryUser.getTestUserId();
                installPackageAsUser(
                        "CVE-2025-32333.apk", false /* grantPermission */, secondaryUserId);

                // Run device test for test app to reproduce the vulnerability
                runDeviceTests(
                        new DeviceTestRunOptions(testAppPackageName)
                                .addInstrumentationArg(
                                        "primaryUserId", String.valueOf(primaryUserId)));

                // Switch back to primary user
                assume().withMessage("Unable to switch back to original user")
                        .that(device.switchUser(primaryUserId))
                        .isTrue();

                // Without fix, 'Install unknown apps' permission is granted for the test app
                commandOutput =
                        runAndCheck(
                                        device,
                                        "appops query-op REQUEST_INSTALL_PACKAGES allow --user "
                                                + primaryUserId)
                                .getStdout();
                assertWithMessage(
                                "Device is vulnerable to b/409780975. Cross-user permission grant"
                                    + " is possible due to user id injection in 'SpaActivity' from"
                                    + " Intent data scheme specific part")
                        .that(commandOutput.contains(testAppPackageName))
                        .isFalse();
            }
        } catch (Exception e) {
            assume().that(e).isNull();
        }
    }
}
