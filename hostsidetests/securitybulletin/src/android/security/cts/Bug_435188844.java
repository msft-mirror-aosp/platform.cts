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

import static java.util.Collections.singletonMap;

import android.platform.test.annotations.AsbSecurityTest;

import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Map;
import java.util.function.Consumer;

@RunWith(DeviceJUnit4ClassRunner.class)
public class Bug_435188844 extends NonRootSecurityTestCase {
    private static final String TEST_PROVIDER_APK = "Bug-435188844-provider.apk";
    private static final String TEST_APK = "Bug-435188844-test.apk";
    private static final String TEST_PKG = "android.security.cts.bug_435188844.test";
    private static final String TEST_CLASS = "DeviceTest";
    private static final String TEST_METHOD_EXTERNAL_PROVIDER =
            "testCrossProfileExtraContentProvider";
    private static final String TEST_METHOD_EXTERNAL_ITEMS =
            "testCrossProfileItemsFromExtraContentProvider";

    @Test
    @AsbSecurityTest(cveBugId = 435188844)
    public void testBug_435188844_external_provider() {
        runTest(
                args -> {
                    try {
                        runDeviceTestsWithArgs(
                                TEST_PKG,
                                TEST_PKG + "." + TEST_CLASS,
                                TEST_METHOD_EXTERNAL_PROVIDER,
                                args);
                    } catch (DeviceNotAvailableException e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    @Test
    @AsbSecurityTest(cveBugId = 435188844)
    public void testBug_435188844_external_items() {
        runTest(
                args -> {
                    try {
                        runDeviceTestsWithArgs(
                                TEST_PKG,
                                TEST_PKG + "." + TEST_CLASS,
                                TEST_METHOD_EXTERNAL_ITEMS,
                                args);
                    } catch (DeviceNotAvailableException e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    private boolean runDeviceTestsWithArgs(
            String pkgName,
            String testClassName,
            String testMethodName,
            Map<String, String> testArgs)
            throws DeviceNotAvailableException {
        final String testRunner = "androidx.test.runner.AndroidJUnitRunner";
        final long defaultTestTimeoutMs = 60 * 1000L;
        final long defaultMaxTimeoutToOutputMs = 60 * 1000L; // 1min
        return runDeviceTests(
                getDevice(),
                testRunner,
                pkgName,
                testClassName,
                testMethodName,
                /* userId */ null,
                defaultTestTimeoutMs,
                defaultMaxTimeoutToOutputMs,
                /* maxInstrumentationTimeoutMillis */ 0L,
                /* checkResults */ true,
                /* isHiddenApiCheckDisabled */ false,
                testArgs);
    }

    private void runTest(Consumer<Map<String, String>> testBlock) {
        ITestDevice device = null;
        int newUser = -1;
        Throwable testException = null;
        try {
            device = getDevice();
            assumeTrue("Test requires multiple users", device.isMultiUserSupported());
            newUser = device.createUser("CtsUser", /* guest */ true, /* ephemeral */ false);
            assumeTrue("Unable to create test user", device.startUser(newUser, /* wait */ true));

            installPackage(TEST_PROVIDER_APK, "--user " + newUser);
            installPackage(TEST_APK);

            Map<String, String> args = singletonMap("target_user", Integer.toString(newUser));

            testBlock.accept(args);
        } catch (Throwable e) {
            testException = e;
        } finally {
            try {
                if (newUser != -1) {
                    // Stop user 'CTSUser'
                    device.stopUser(newUser);

                    // Remove user 'CTSUser'
                    device.removeUser(newUser);
                }
            } catch (Exception e) {
                CLog.e("failed to clean up guest user %d: %e", newUser, e);
                if (testException != null) {
                    testException.addSuppressed(e);
                }
            }
        }
        assumeNoException(testException);
    }
}
