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
import static com.android.sts.common.SystemUtil.poll;

import static com.google.common.truth.TruthJUnit.assume;

import android.platform.test.annotations.AsbSecurityTest;

import com.android.sts.common.UserUtils.SecondaryUser;
import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.DeviceTestRunOptions;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2024_49722 extends NonRootSecurityTestCase {

    @Test
    @AsbSecurityTest(cveBugId = 341688848)
    public void testPocCVE_2024_49722() {
        try {
            // Install test app in the primary user
            final ITestDevice device = getDevice();
            final int primaryUserId = device.getCurrentUser();
            installPackage("CVE-2024-49722.apk");

            // Create new user
            try (AutoCloseable asSecondaryUser =
                    new SecondaryUser(getDevice())
                            .name("CVE_2024_49722_user")
                            .doSwitch()
                            .doSkipSetupWizard()
                            .withUser()) {
                // Take screenshot in the secondary user
                runAndCheck(device, "input keyevent KEYCODE_SYSRQ");

                // Wait for screenshot to get saved in the secondary user
                final int secondaryUserId = device.getCurrentUser();
                final String screenshotQueryCommand =
                        String.format(
                                "content query --user %d --projection _id --uri"
                                        + " content://media/external/images/media/",
                                secondaryUserId);
                assume().withMessage("Unable to save the screenshot in the secondary user")
                        .that(
                                poll(
                                        () -> {
                                            try {
                                                String commandOutput =
                                                        runAndCheck(device, screenshotQueryCommand)
                                                                .getStdout();
                                                CLog.e("Command Output = " + commandOutput);
                                                return commandOutput.contains("Row");
                                            } catch (DeviceNotAvailableException e) {
                                                return false;
                                            }
                                        }))
                        .isTrue();

                // Switch back to primary user
                assume().withMessage("Unable to switch back to original user")
                        .that(device.switchUser(primaryUserId))
                        .isTrue();

                // Run device test in primary user
                runDeviceTests(
                        new DeviceTestRunOptions("android.security.cts.CVE_2024_49722")
                                .addInstrumentationArg(
                                        "secondaryUserId", String.valueOf(secondaryUserId)));
            }
        } catch (Exception e) {
            assume().that(e).isNull();
        }
    }
}
