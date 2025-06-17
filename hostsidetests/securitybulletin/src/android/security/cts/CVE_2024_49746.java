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

import static com.android.sts.common.NativePoc.Bitness.ONLY32;
import static com.android.sts.common.NativePoc.Bitness.ONLY64;
import static com.android.sts.common.NativePocStatusAsserter.assertNotVulnerableExitCode;

import static com.google.common.truth.TruthJUnit.assume;

import android.platform.test.annotations.AsbSecurityTest;

import com.android.sts.common.NativePoc;
import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2024_49746 extends NonRootSecurityTestCase {

    @Test
    @AsbSecurityTest(cveBugId = 359179312)
    public void testPocCVE_2024_49746() {
        try {
            final String abi = getDevice().getProperty("ro.product.cpu.abi");
            boolean is64BitArch = abi.contains("x86_64") || abi.contains("arm64");

            // Run PoC to detect the vulnerability.
            NativePoc.builder()
                    .bitness(is64BitArch ? ONLY64 : ONLY32)
                    .pocName("CVE-2024-49746")
                    .asserter(assertNotVulnerableExitCode())
                    .build()
                    .run(this);
        } catch (Exception e) {
            assume().that(e).isNull();
        }
    }
}
