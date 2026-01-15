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

import static com.google.common.truth.TruthJUnit.assume;

import android.platform.test.annotations.AsbSecurityTest;

import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.DeviceTestRunOptions;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2025_48605 extends NonRootSecurityTestCase {

    @Test
    @AsbSecurityTest(cveBugId = 395640609)
    public void testPocCVE_2025_48605() {
        try {
            // Install the required applications.
            installPackage("CVE-2025-48605-dpc.apk", "-t");
            installPackage("CVE-2025-48605-helper.apk");
            installPackage("CVE-2025-48605-test.apk");

            // Set lock and 'PocAdminReceiver' as device-owner.
            try (AutoCloseable lockAndSetDeviceOwner = lockAndSetDeviceOwner()) {
                // Run DeviceTest.
                runDeviceTests(
                        new DeviceTestRunOptions("android.security.cts.CVE_2025_48605_test"));
            }
        } catch (Exception e) {
            assume().fail();
        }
    }

    private AutoCloseable lockAndSetDeviceOwner() throws Exception {
        // Set lock screen pin.
        final ITestDevice device = getDevice();
        runAndCheck(device, "locksettings set-pin 1234");

        // Verify if pin is set.
        final String output = device.executeShellCommand("locksettings verify --old 1234");
        assume().withMessage("Failed to set lock")
                .that(output)
                .contains("Lock credential verified successfully");

        // Set the 'PocDeviceAdminReceiver' as device-owner using device policy manager.
        final int userId = device.getCurrentUser();
        final String componentName =
                "android.security.cts.CVE_2025_48605_dpc/.PocDeviceAdminReceiver";
        assume().withMessage("Unable to set device owner")
                .that(device.setDeviceOwner(componentName, userId))
                .isTrue();

        // Return 'AutoCloseable' to remove lock and the 'PocDeviceAdminReceiver' as device-owner.
        return () -> {
            // Unset Lock screen pin.
            runAndCheck(device, "locksettings clear --old 1234");

            // Remove admin.
            device.removeAdmin(componentName, userId);
        };
    }
}
