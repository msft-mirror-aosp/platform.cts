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

import static org.junit.Assume.assumeNoException;
import static org.junit.Assume.assumeTrue;

import android.platform.test.annotations.AsbSecurityTest;

import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.Map;

@RunWith(DeviceJUnit4ClassRunner.class)
public class Bug_407764858 extends NonRootSecurityTestCase {
    private static final String TEST_APP = "Bug-407764858-test.apk";
    private static final String TEST_PKG = "com.android.security.cts.bug_407764858_test";
    private static final String TEST_CLASS =
            "com.android.security.cts.bug_407764858_test.DeviceTest";
    private static final String TEST_METHOD = "testCrossProfileIntentPolicyBypass";

    /**
     * An app test, which uses this host Java test to launch an Android instrumented test
     */
    @Test
    @AsbSecurityTest(cveBugId = 407764858)
    public void testWithApp() {
        ITestDevice device = null;
        int newUser = -1;
        try {
            device = getDevice();
            assumeTrue("could not disable root", device.disableAdbRoot());
            assumeTrue("Test requires multiple users", device.isMultiUserSupported());
            newUser = createWorkUser(device, "TestWork");
            assumeTrue("Unable to create test user", device.startUser(newUser, /* wait */ true));
            installPackage(TEST_APP, "--user " + newUser);
            runDeviceTestsWithArgs(
                    TEST_PKG,
                    TEST_CLASS,
                    TEST_METHOD,
                    newUser,
                    Collections.emptyMap());
        } catch (Exception e) {
            assumeNoException(e);
        } finally {
            try {
                if (newUser != -1) {
                    // Stop user
                    device.stopUser(newUser);
                    // Remove user
                    device.removeUser(newUser);
                }
            } catch (Exception e) {
                LogUtil.CLog.e("failed to clean up work user %d: %e", newUser, e);
            }
        }
    }

    private int createWorkUser(ITestDevice device, String name)
            throws DeviceNotAvailableException, IllegalStateException {
        String command =
                "pm create-user --profileOf 0 --managed " + name;
        final String output = device.executeShellCommand(command);
        if (output.startsWith("Success")) {
            try {
                return Integer.parseInt(output.substring(output.lastIndexOf(" ")).trim());
            } catch (NumberFormatException e) {
                LogUtil.CLog.e("Failed to parse result: %s", output);
            }
        }
        throw new IllegalStateException(String.format("Failed to create user: %s", output));
    }

    private boolean runDeviceTestsWithArgs(
            String pkgName,
            String testClassName,
            String testMethodName,
            int userId,
            Map<String, String> testArgs)
            throws DeviceNotAvailableException {
        final String testRunner = "androidx.test.runner.AndroidJUnitRunner";
        final long defaultTestTimeoutMs = 60 * 1000L;
        final long defaultMaxTimeoutToOutputMs = 60 * 1000L; // 1min
        return runDeviceTests(getDevice(),
                testRunner,
                pkgName,
                testClassName,
                testMethodName,
                userId,
                defaultTestTimeoutMs,
                defaultMaxTimeoutToOutputMs,
                /* maxInstrumentationTimeoutMillis */ 0L,
                /* checkResults */ true,
                /* isHiddenApiCheckDisabled */ false,
                testArgs);
    }
}
