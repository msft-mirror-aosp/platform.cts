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
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.DeviceTestRunOptions;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2025_32346 extends NonRootSecurityTestCase {

    @Test
    @AsbSecurityTest(cveBugId = 337785563)
    public void testPocCVE_2025_32346() {
        try {
            // Fetch primary user id
            final ITestDevice device = getDevice();
            final int primaryUserId = device.getCurrentUser();

            // Install the test app in primary user
            installPackageAsUser(
                    device, "CVE-2025-32346-test.apk", false /* grantPermission */, primaryUserId);

            // Create a work profile for the primary user
            SecondaryUser workUser = new SecondaryUser(device);
            try (AutoCloseable asWorkUser =
                    workUser.name("CVE_2025_32346_work_user").managed(primaryUserId).withUser()) {
                // Install helper app and run device test of helper app in
                // 'CVE_2025_32346_work_user' to add a contact
                final int workUserId = workUser.getTestUserId();
                final String workProfileContactNumber = "cve_2025_32346";
                installPackageAsUser(
                        device,
                        "CVE-2025-32346-helper.apk",
                        true /* grantPermission */,
                        workUserId);
                runDeviceTests(
                        new DeviceTestRunOptions("android.security.cts.CVE_2025_32346_helper")
                                .setUserId(workUserId)
                                .addInstrumentationArg(
                                        "workProfileContactNumber",
                                        String.valueOf(workProfileContactNumber)));

                // Run device test of test app in primary user and detect the leak of contact
                // created in 'CVE_2025_32346_work_user'
                runDeviceTests(
                        new DeviceTestRunOptions("android.security.cts.CVE_2025_32346_test")
                                .setUserId(primaryUserId)
                                .addInstrumentationArg(
                                        "workProfileContactNumber",
                                        String.valueOf(workProfileContactNumber)));
            }
        } catch (Exception e) {
            assume().that(e).isNull();
        }
    }
}
