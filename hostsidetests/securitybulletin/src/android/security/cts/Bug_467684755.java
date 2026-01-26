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

import static org.junit.Assume.assumeNoException;

import android.platform.test.annotations.AsbSecurityTest;

import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class Bug_467684755 extends NonRootSecurityTestCase {
    private static final String TEST_APP = "Bug-467684755-test.apk";
    private static final String TEST_PACKAGE = "com.android.security.cts.bug_467684755_test";
    private static final String TEST_CLASS = TEST_PACKAGE + ".DeviceTest";
    private static final String TEST_METHOD = "testWalletContextualLocationsServiceGetCardInfo";

    /** An app test that uses this host Java test to launch an Android instrumented test. */
    @Test
    @AsbSecurityTest(cveBugId = 467684755)
    public void testWithApp() {
        try {
            installPackage(TEST_APP);
            runDeviceTests(TEST_PACKAGE, TEST_CLASS, TEST_METHOD);
        } catch (Exception e) {
            assumeNoException(e);
        }
    }
}
