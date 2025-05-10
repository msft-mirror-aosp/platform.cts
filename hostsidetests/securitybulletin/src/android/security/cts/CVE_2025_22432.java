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

import static com.android.sts.common.SystemUtil.withSetting;

import static com.google.common.truth.TruthJUnit.assume;

import android.platform.test.annotations.AsbSecurityTest;

import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.DeviceTestRunOptions;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2025_22432 extends NonRootSecurityTestCase {

    @AsbSecurityTest(cveBugId = 376461726)
    @Test
    public void testPocCVE_2025_22432() {
        try {
            // Install 'helper-app' and 'test-app'.
            installPackage("CVE-2025-22432-test-app.apk");
            installPackage("CVE-2025-22432-helper-app.apk");

            final String callRedirectionTimeout = "5000";
            try (AutoCloseable withCallRedirectionTimeout =
                    withSetting(
                            getDevice(),
                            "secure",
                            "telecom.user_defined_call_redirection_timeout",
                            callRedirectionTimeout)) {
                // Run the test.
                runDeviceTests(
                        new DeviceTestRunOptions("android.security.cts.CVE_2025_22432_test")
                                .addInstrumentationArg(
                                        "callRedirectionTimeout", callRedirectionTimeout));
            }

        } catch (Exception e) {
            assume().that(e).isNull();
        }
    }
}
