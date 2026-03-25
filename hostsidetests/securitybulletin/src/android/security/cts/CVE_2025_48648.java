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
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.DeviceTestRunOptions;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2025_48648 extends NonRootSecurityTestCase {

    @Test
    @AsbSecurityTest(cveBugId = 396667508)
    public void testPocCVE_2025_48648() {
        try {
            // Enable properties for running 'SDK Sandbox'.
            try (AutoCloseable adservicesConfig = withAdservicesConfigs()) {
                // Install required APKs.
                installPackage("CVE-2025-48648-sdk.apk");
                installPackage("CVE-2025-48648-helper.apk");
                installPackage("CVE-2025-48648-test.apk");

                // Start the 'test-app'.
                runDeviceTests(
                        new DeviceTestRunOptions("android.security.cts.CVE_2025_48648_test"));
            }
        } catch (Exception e) {
            assume().that(e).isNull();
        }
    }

    // Helper method to set multiple 'device_config' keys of 'adservices' namespace to enable
    // 'SDK Sandbox' and return an 'AutoCloseable' for cleanup.
    private AutoCloseable withAdservicesConfigs() throws DeviceNotAvailableException {
        final Map<String, String> oldValues = new HashMap<>();
        final String namespace = "adservices";
        final ITestDevice device = getDevice();
        final String config = "device_config";
        final List<String> keys =
                Arrays.asList(
                        "adservice_system_service_enabled",
                        "global_kill_switch",
                        "disable_sdk_sandbox",
                        "sdksandbox_customized_sdk_context_enabled");
        for (String key : keys) {
            // Store current value to restore it later
            oldValues.put(
                    key,
                    runAndCheck(device, String.format("%s get %s %s", config, namespace, key))
                            .getStdout()
                            .trim());

            // Verify execution by setting true for the key
            // 'sdksandbox_customized_sdk_context_enabled' and false for others.
            runAndCheck(
                    device,
                    String.format(
                            "%s put %s %s %s",
                            config,
                            namespace,
                            key,
                            key.equals("sdksandbox_customized_sdk_context_enabled")
                                    ? "true"
                                    : "false"));
        }
        return () -> {
            for (String key : keys) {
                final String oldValue = oldValues.get(key);
                if (oldValue.isEmpty() || oldValue.equals("null")) {
                    runAndCheck(device, String.format("%s delete %s %s", config, namespace, key));
                } else {
                    runAndCheck(
                            device,
                            String.format("%s put %s %s %s", config, namespace, key, oldValue));
                }
            }
        };
    }
}
