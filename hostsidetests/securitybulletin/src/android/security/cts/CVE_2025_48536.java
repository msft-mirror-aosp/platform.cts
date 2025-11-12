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
import static com.android.sts.common.UserUtils.SecondaryUser;

import static com.google.common.truth.TruthJUnit.assume;

import android.platform.test.annotations.AsbSecurityTest;

import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.DeviceTestRunOptions;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2025_48536 extends NonRootSecurityTestCase {

    @Test
    @AsbSecurityTest(cveBugId = 388034510)
    public void testPocCVE_2025_48536() {
        try {
            final ITestDevice device = getDevice();
            final String pkgName = "com.google.android.apps.search.assistant.surfaces.voice.devapp";

            // Create a secondary user.
            final SecondaryUser secondaryUser = new SecondaryUser(device);
            int secondaryUserId;
            try (AutoCloseable withSecondaryUser = secondaryUser.withUser();
                    AutoCloseable withSecondaryUserSwitched =
                            switchToSecondaryUser(
                                    secondaryUserId = secondaryUser.getTestUserId(), device)) {
                // Install test-app in secondary user.
                installPackageAsUser(
                        "CVE-2025-48536.apk", true /* grant permission */, secondaryUserId);

                // Run the test with secondary user profile as the slice permissions get revoked
                // for this package once the test uninstalls this package keeping the primary user's
                // permissions untouched hence leaving the device in an unchanged state.
                runDeviceTests(new DeviceTestRunOptions(pkgName).setUserId(secondaryUserId));
            }
        } catch (Exception e) {
            assume().that(e).isNull();
        }
    }

    // Switch to secondary user and skip the initial user setup wizard.
    private AutoCloseable switchToSecondaryUser(int secondaryUserId, ITestDevice device)
            throws Exception {
        // Fetch the initial settings value.
        final String initialValueForUserSetupComplete =
                device.getSetting(secondaryUserId, "secure", "user_setup_complete");

        // Set the required settings value to skip the user setup wizard.
        assume().withMessage("Could not skip user setup wizard for secondary user!!!")
                .that(setUserSetupCompleteSetting(secondaryUserId, "1", device))
                .isTrue();

        // Switch to secondary user.
        assume().withMessage("Could not switch to secondary user profile!!!")
                .that(device.switchUser(secondaryUserId))
                .isTrue();

        return () -> {
            // Roll back to the initial state of the device.
            setUserSetupCompleteSetting(secondaryUserId, initialValueForUserSetupComplete, device);
            assume().withMessage("Could not switch back to primary user profile!!!")
                    .that(device.switchUser(device.getMainUserId()))
                    .isTrue();
        };
    }

    // Using adb commands to set the required settings for secondary user to skip the user setup
    // wizard as 'UserUtils' utility is failing to do so.
    private boolean setUserSetupCompleteSetting(
            int secondaryUserId, String value, ITestDevice device) throws Exception {
        runAndCheck(
                device,
                String.format(
                        "settings --user %s put secure user_setup_complete %s",
                        secondaryUserId, value));
        return device.getSetting(secondaryUserId, "secure", "user_setup_complete").equals("1");
    }
}
