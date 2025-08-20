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
import static com.android.sts.common.DumpsysUtils.hasActivityResumed;
import static com.android.sts.common.SystemUtil.poll;
import static com.android.sts.common.SystemUtil.withSetting;

import static com.google.common.truth.TruthJUnit.assume;

import android.platform.test.annotations.AsbSecurityTest;

import com.android.sts.common.LockSettingsUtil;
import com.android.sts.common.SystemUtil.Namespace;
import com.android.sts.common.UserUtils.SecondaryUser;
import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.DeviceTestRunOptions;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2025_48527 extends NonRootSecurityTestCase {

    @AsbSecurityTest(cveBugId = 378088320)
    @Test
    public void testPocCVE_2025_48527() throws Exception {
        try {
            final ITestDevice device = getDevice();
            final int primaryUserId = device.getCurrentUser();
            final SecondaryUser workUser = new SecondaryUser(device);
            try (AutoCloseable asWorkUser =
                    workUser.name("CVE_2025_48527_Work_User").managed(primaryUserId).withUser()) {
                // Install helper app in work user.
                final int workUserId = workUser.getTestUserId();
                installPackageAsUser(
                        device,
                        "CVE-2025-48527-helper.apk",
                        true /* grantPermission */,
                        workUserId);

                // Enable screen lock for the work user.
                final String pin = "1234";
                try (AutoCloseable lockScreen =
                        new LockSettingsUtil(device, workUserId).withPin(pin)) {
                    // Disable the 'sensitive notification content'.
                    try (AutoCloseable withPocAutofillServiceAsDefault =
                            withSetting(
                                    device,
                                    Namespace.SECURE,
                                    "lock_screen_allow_private_notifications",
                                    "0",
                                    workUserId)) {
                        // Enable the 'notification history'.
                        try (AutoCloseable withPocAutofillNotificationServiceAsDefault =
                                withSetting(
                                        device,
                                        Namespace.SECURE,
                                        "notification_history_enabled",
                                        "1",
                                        primaryUserId)) {
                            // Launch PocActivity from the helper app to switch to the work profile
                            // and generate a notification within it.
                            final String pocActivity =
                                    "android.security.cts.CVE_2025_48527_helper/.PocActivity";
                            runAndCheck(
                                    device, "am start -n " + pocActivity + " --user " + workUserId);
                            assume().withMessage("Unable to launch PocActivity")
                                    .that(
                                            poll(
                                                    () -> {
                                                        try {
                                                            return hasActivityResumed(
                                                                    device, pocActivity);
                                                        } catch (Exception throwBack) {
                                                            throw new IllegalStateException(
                                                                    throwBack);
                                                        }
                                                    }))
                                    .isTrue();

                            // Switch to primary user.
                            assume().withMessage("Unable to switch to primary user.")
                                    .that(device.switchUser(primaryUserId))
                                    .isTrue();

                            // Install test app in primary user.
                            installPackageAsUser(
                                    device,
                                    "CVE-2025-48527-test.apk",
                                    false /* grantPermission */,
                                    primaryUserId);

                            // Run DeviceTest.
                            runDeviceTests(
                                    new DeviceTestRunOptions(
                                                    "android.security.cts.CVE_2025_48527_test")
                                            .addInstrumentationArg(
                                                    "workUserId", String.valueOf(workUserId))
                                            .setDisableHiddenApiCheck(true));
                        }
                    }
                }
            }
        } catch (Exception e) {
            assume().that(e).isNull();
        }
    }
}
