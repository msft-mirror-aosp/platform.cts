/*
 * Copyright (C) 2026 The Android Open Source Project
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
public class CVE_2025_48629 extends NonRootSecurityTestCase {

    @AsbSecurityTest(cveBugId = 352518318)
    @Test
    public void testPocCVE_2025_48629() {
        try {
            // Check whether a default recognizer is already set
            final ITestDevice device = getDevice();
            final String output =
                    runAndCheck(
                                    device,
                                    "cmd package resolve-activity -a"
                                            + " android.speech.action.RECOGNIZE_SPEECH")
                            .getStdout()
                            .trim();
            assume().withMessage("Default speech recognizer is already set: ")
                    .that(output.isEmpty() || output.contains("No activity found"))
                    .isTrue();

            // Install test app and run device test
            installPackage("CVE-2025-48629.apk");
            runDeviceTests(new DeviceTestRunOptions("android.security.cts.CVE_2025_48629"));
        } catch (Exception e) {
            assume().that(e).isNull();
        }
    }
}
