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

import com.android.sts.common.UserUtils;
import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.DeviceTestRunOptions;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2025_22420 extends NonRootSecurityTestCase {

    @AsbSecurityTest(cveBugId = 337775777)
    @Test
    public void testPocCVE_2025_22420() {
        try {
            // Install the app.
            final ITestDevice device = getDevice();
            installPackageAsUser(
                    "CVE-2025-22420.apk", true /* grantPermission */, device.getCurrentUser());

            // Create a secondary user
            final UserUtils.SecondaryUser secondaryUser = new UserUtils.SecondaryUser(device);
            try (AutoCloseable asSecondaryUser = secondaryUser.withUser()) {
                // Run the test
                runDeviceTests(
                        new DeviceTestRunOptions("android.security.cts.CVE_2025_22420")
                                .addInstrumentationArg(
                                        "secondaryUserId",
                                        String.valueOf(secondaryUser.getTestUserId())));
            }
        } catch (Exception e) {
            assume().that(e).isNull();
        }
    }
}
