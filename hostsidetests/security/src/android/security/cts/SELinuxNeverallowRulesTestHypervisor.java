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

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.platform.test.annotations.RestrictedBuildTest;

import com.android.compatibility.common.util.PropertyUtil;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Neverallow Rules SELinux tests to ensure hypervisor devices can only be used by crosvm.
 *
 * <p>This test finds the security context of all supported hypervisor device files, generates a
 * neverallow rule for each of them, and ensures the device's policy does not violate those
 * neverallows.
 *
 * <p>The more general SELinuxNeverallowRulesTest is enough to ensure this for KVM, but the other
 * hypervisors are labelled by vendor policies and so require this roundabout technique.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class SELinuxNeverallowRulesTestHypervisor extends BaseHostJUnit4Test {
    @Test
    @RestrictedBuildTest
    public void testHypervisorNeverallowRules() throws Exception {
        ITestDevice device = getDevice();

        assumeTrue(
                device.getBooleanProperty("ro.boot.hypervisor.vm.supported", false)
                        || device.getBooleanProperty(
                                "ro.boot.hypervisor.protected_vm.supported", false));

        File sepolicyAnalyze = SELinuxHostTest.copyResourceToTempFile("/sepolicy-analyze");
        sepolicyAnalyze.setExecutable(true);

        File devicePolicyFile = SELinuxHostTest.getDevicePolicyFile(device);

        // Hypervisors supported by AVF.
        String command = "ls -Z /dev/kvm /dev/gunyah /dev/gz";
        // There are devices that launched with Android <=15 that use alternate
        // paths, like /dev/qgunyah, from outside crosvm and AVF in their
        // vendor policies. Forbid it on newer devices.
        if (PropertyUtil.getFirstApiLevel(device) >= 36) {
            command += " /dev/*gunyah";
        }

        // Get the security context for the devices.
        //
        // We don't check the exit code because not all of the files will
        // exist. Instead we require that there is at least one result, which
        // must be the case because the device advertised VM support.
        var result = device.executeShellV2Command(command);
        List<String> hypervisorContexts = new ArrayList();
        // `ls` outputs looks like `u:object_r:kvm_device:s0 /dev/kvm`.
        Matcher matcher =
                Pattern.compile("^\\s*[^:]+:[^:]+:([^:]+):", Pattern.MULTILINE)
                        .matcher(result.getStdout());
        while (matcher.find()) {
            hypervisorContexts.add(matcher.group(1));
        }
        assertTrue(
                "Failed to find security context for hypervisor device in `ls` output: "
                        + result.toString(),
                hypervisorContexts.size() > 0);

        for (String context : hypervisorContexts) {
            var ruleStr =
                    String.format(
                            "neverallow {domain -crosvm} %s:chr_file {open ioctl read write};",
                            context);
            var rule = new SELinuxNeverallowRule(ruleStr, new HashMap());
            rule.testNeverallowRule(sepolicyAnalyze, devicePolicyFile);
        }
    }
}
