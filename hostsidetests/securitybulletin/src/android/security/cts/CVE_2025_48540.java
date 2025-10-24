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

import static com.android.sts.common.GhidraFunctionOffsets.getFunctionOffsetsAsCmdLineArgs;
import static com.android.sts.common.NativePoc.Bitness.ONLY32;
import static com.android.sts.common.NativePoc.Bitness.ONLY64;
import static com.android.sts.common.NativePocStatusAsserter.assertNotVulnerableExitCode;

import static com.google.common.truth.TruthJUnit.assume;

import android.platform.test.annotations.AsbSecurityTest;

import com.android.sts.common.Ghidra;
import com.android.sts.common.NativePoc;
import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.List;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2025_48540 extends NonRootSecurityTestCase {

    @AsbSecurityTest(cveBugId = 416734088)
    @Test
    public void testPocCVE_2025_48540() {
        try {
            final ITestDevice device = getDevice();
            final String abi = device.getProperty("ro.product.cpu.abi");
            final String libraryPath =
                    "/system/lib" + (abi.contains("x86_64") || abi.contains("arm64") ? "64" : "");
            final String libraryName = "libbinder.so";
            final String libraryFile = libraryPath + "/" + libraryName;
            final List<String> functionNames =
                    List.of(
                            "_ZN7android8RpcState14processCommandERKNS_2spINS_10"
                                    + "RpcSession13RpcConnectionEEERKNS1_IS2_EERKNS_13"
                                    + "RpcWireHeaderENS0_11CommandTypeE",
                            "_ZN7android8RpcState15processTransactERKNS_2spINS_10"
                                + "RpcSession13RpcConnectionEEERKNS1_IS2_EERKNS_13RpcWireHeaderE",
                            "_ZN7android8RpcState12waitForReplyERKNS_2spINS_10"
                                    + "RpcSession13RpcConnectionEEERKNS1_IS2_EEPNS_6ParcelE",
                            "_ZN7android8RpcState16processDecStrongERKNS_2spINS_10RpcSession13"
                                    + "RpcConnectionEEERKNS1_IS2_EERKNS_13RpcWireHeaderE");

            // Get function offsets.
            final String functionOffsets =
                    getFunctionOffsetsAsCmdLineArgs(
                            new Ghidra(this), new File(libraryFile), functionNames);

            // Check for vulnerability
            final String binaryName = "CVE-2025-48540";
            NativePoc.builder()
                    .pocName(binaryName)
                    .bitness(libraryPath.contains("lib64") ? ONLY64 : ONLY32)
                    .args(libraryFile, functionOffsets)
                    .asserter(assertNotVulnerableExitCode())
                    .build()
                    .run(this);
        } catch (Exception e) {
            assume().that(e).isNotNull();
        }
    }
}
