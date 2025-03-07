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

import static com.android.sts.common.DumpsysUtils.hasActivityResumed;
import static com.android.sts.common.SystemUtil.Namespace;
import static com.android.sts.common.SystemUtil.poll;
import static com.android.sts.common.SystemUtil.withSetting;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeNoException;

import android.platform.test.annotations.AsbSecurityTest;

import com.android.sts.common.CommandUtil;
import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class Bug_386950836 extends NonRootSecurityTestCase {
    private static final String PACKAGE = "android.security.cts.BUG_386950836";

    @AsbSecurityTest(cveBugId = 386950836)
    @Test
    public void testNullBindService_serviceDisabled_cannotTriggerBAL() {
        try {
            ITestDevice device = getDevice();

            installPackage("BUG-386950836-test.apk");

            // Enable and disable the test app's AccessibilityService
            try (AutoCloseable enableAccessibility =
                    withSetting(
                            device,
                            Namespace.SECURE,
                            "enabled_accessibility_services",
                            PACKAGE + "/" + PACKAGE + ".A11yService")) {
                CommandUtil.runAndCheck(
                        device, "settings delete secure enabled_accessibility_services");

                // Try to trigger the BAL
                CommandUtil.runAndCheck(
                        device, "am broadcast -a " + PACKAGE + ".bal -p " + PACKAGE);

                // Assert that the activity did not launch.
                assertThat(pollHasActivityResumed(device, PACKAGE + "/.MainActivity")).isFalse();
            }
        } catch (Exception e) {
            assumeNoException(e);
        }
    }

    private boolean pollHasActivityResumed(ITestDevice device, String componentName)
            throws Exception {
        return poll(
                () -> {
                    try {
                        return hasActivityResumed(device, componentName);
                    } catch (Exception e) {
                        return false;
                    }
                });
    }
}
