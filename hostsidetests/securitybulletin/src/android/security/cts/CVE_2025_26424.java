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
public class CVE_2025_26424 extends NonRootSecurityTestCase {

    @Test
    @AsbSecurityTest(cveBugId = 341253275)
    public void testPocCVE_2025_26424() {
        try {
            // Install 'helper-app' in primary user.
            final ITestDevice device = getDevice();
            final int primaryUserId = device.getCurrentUser();
            installPackageAsUser(
                    device,
                    "CVE-2025-26424-helper.apk",
                    false /* grantPermission */,
                    primaryUserId);

            // Create a new user.
            try (AutoCloseable asSecondaryUser =
                    new SecondaryUser(device)
                            .name("CVE_2025_26424_test_user")
                            .doSkipSetupWizard()
                            .withUser()) {
                // Run the 'helper-app' DeviceTest in primary user to create a VPN connection.
                runDeviceTests(
                        new DeviceTestRunOptions("android.security.cts.CVE_2025_26424_helper")
                                .setUserId(primaryUserId));

                final int secondaryUserId = device.getCurrentUser();
                // Install 'test-app' in secondary user.
                installPackageAsUser(
                        device,
                        "CVE-2025-26424-test.apk",
                        false /* grantPermission */,
                        secondaryUserId);

                // Run the test on the secondary user to fetch the VPN key store alias of
                // primary user.
                runDeviceTests(
                        new DeviceTestRunOptions("android.security.cts.CVE_2025_26424_test")
                                .setDisableHiddenApiCheck(true));
            }
        } catch (Exception e) {
            assume().that(e).isNull();
        }
    }
}
