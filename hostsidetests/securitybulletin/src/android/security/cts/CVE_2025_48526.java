/*
 * Copyright 2025 The Android Open Source Project
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
public class CVE_2025_48526 extends NonRootSecurityTestCase {

    @AsbSecurityTest(cveBugId = 407764858)
    @Test
    public void testPocCVE_2025_48526() throws Exception {
        try {
            final ITestDevice device = getDevice();
            final int primaryUserId = device.getCurrentUser();

            // Create Work User
            SecondaryUser workUser = new SecondaryUser(device);
            try (AutoCloseable asWorkUser =
                    workUser.name("CVE_2025_48526_Work_User").managed(primaryUserId).withUser()) {
                // Install test app in secondary user and run device test.
                final int workUserId = workUser.getTestUserId();
                installPackageAsUser(
                        device, "CVE-2025-48526.apk", false /* grantPermission */, workUserId);
                runDeviceTests(
                        new DeviceTestRunOptions("android.security.cts.CVE_2025_48526")
                                .setUserId(workUserId));
            }
        } catch (Exception e) {
            assume().that(e).isNull();
        }
    }
}
