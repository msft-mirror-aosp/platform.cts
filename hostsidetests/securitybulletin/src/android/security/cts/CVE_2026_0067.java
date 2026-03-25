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

import static com.android.sts.common.NativePoc.Bitness.ONLY32;
import static com.android.sts.common.NativePoc.Bitness.ONLY64;
import static com.android.sts.common.NativePocCrashAsserter.assertNoCrash;

import static com.google.common.truth.TruthJUnit.assume;

import android.platform.test.annotations.AsbSecurityTest;

import com.android.sts.common.NativePoc;
import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;
import com.android.sts.common.util.TombstoneUtils;
import com.android.sts.common.util.TombstoneUtils.Config.BacktraceFilterPattern;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2026_0067 extends NonRootSecurityTestCase {

    @AsbSecurityTest(cveBugId = 470967228)
    @Test
    public void testPocCVE_2026_0067() {
        try {
            // Configure 'TombstoneUtils.Config'.
            final String binaryName = "CVE-2026-0067";
            final TombstoneUtils.Config crashConfig =
                    new TombstoneUtils.Config()
                            .setProcessPatterns(binaryName)
                            .setBacktraceIncludes(
                                    new BacktraceFilterPattern(
                                            "libdng_sdk.so", "dng_stream::TagValue_urational"))
                            .setSignals(TombstoneUtils.Signals.SIGABRT)
                            .setAbortMessageIncludes("ubsan: negate-overflow");

            // Without fix, sanitizer 'UBSan' is enabled leading to crash of native PoC due to
            // integer overflow in the 'TagValue_urational' function of class 'dng_stream'
            // which is detected and test fails.
            // With fix, 'UBSan' is disabled and when malformed stream is parsed, an exception is
            // thrown and test passes.
            final String abi = getDevice().getProperty("ro.product.cpu.abi");
            NativePoc.builder()
                    .pocName(binaryName)
                    .bitness((abi.contains("x86_64") || abi.contains("arm64")) ? ONLY64 : ONLY32)
                    .asserter(assertNoCrash(crashConfig))
                    .build()
                    .run(this);
        } catch (Exception e) {
            assume().that(e).isNull();
        }
    }
}
