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

import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.DeviceTestRunOptions;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2025_48580 extends NonRootSecurityTestCase {

    @Test
    @AsbSecurityTest(cveBugId = 393582077)
    public void testPocCVE_2025_48580() {
        try {
            final ITestDevice device = getDevice();
            final int primaryUserId = device.getCurrentUser();

            // Install the required packages.
            installPackageAsUser(
                    "CVE-2025-48580-test.apk", false /* grantPermission */, primaryUserId);
            installPackageAsUser(
                    "CVE-2025-48580-helper.apk", false /* grantPermission */, primaryUserId);

            // Grant POST_NOTIFICATIONS permission to the helper app
            // and assume it succeeds (output should be empty on success).
            assume().withMessage("Failed to grant POST_NOTIFICATIONS permission")
                    .that(
                            device.executeShellCommand(
                                    "pm grant android.security.cts.CVE_2025_48580_helper"
                                            + " android.permission.POST_NOTIFICATIONS"))
                    .isEmpty();

            // Run device test.
            runDeviceTests(new DeviceTestRunOptions("android.security.cts.CVE_2025_48580_test"));
        } catch (Exception e) {
            assume().that(e).isNull();
        } finally {
            try {
                // Delete the photo file created by the PoC if it exists. Matches
                // Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                final String filePath = "/sdcard/Pictures/CVE_2025_48580.jpg";
                if (getDevice().doesFileExist(filePath)) {
                    getDevice().executeShellCommand("rm " + filePath);
                }
            } catch (Exception e) {
                // Ignore cleanup errors
            }
        }
    }
}
