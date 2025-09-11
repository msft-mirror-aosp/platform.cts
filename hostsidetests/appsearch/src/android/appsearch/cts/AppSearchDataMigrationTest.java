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

package android.appsearch.cts;

import static com.google.common.truth.Truth.assertWithMessage;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.annotation.Nonnull;

/**
 * Test to cover data migration for AppSearch.
 *
 * <p>This only runs on vm-enabled devices.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class AppSearchDataMigrationTest extends AppSearchHostTestBase {
    private static final String TAG = "AppSearchDataMigrationTest";
    private static final String TARGET_PKG_A = "android.appsearch.app.helper_a";
    private static final long DEFAULT_INSTRUMENTATION_TIMEOUT_MS = 600_000; // 10min

    private int mPrimaryUserId;
    private String mVmDisabled = "true";

    @Before
    public void setUp() throws Exception {
        mVmDisabled =
                getDevice()
                        .executeShellCommand(
                                "device_config get appsearch isolated_storage_disabled");
        getDevice()
                .executeShellCommand(
                        "device_config override appsearch isolated_storage_disabled true");
        rebootAndWaitUntilReady();

        mPrimaryUserId = getDevice().getPrimaryUserId();
        installPackageAsUser(TARGET_APK_A, /* grantPermission= */ true, mPrimaryUserId);
        runDeviceTestAsUserInPkgA("clearStressTestDbs", mPrimaryUserId);
    }

    @After
    public void tearDown() throws Exception {
        runDeviceTestAsUserInPkgA("clearStressTestDbs", mPrimaryUserId);
        uninstallPackage(TARGET_PKG_A);
        getDevice()
                .executeShellCommand(
                        "device_config put appsearch isolated_storage_disabled " + mVmDisabled);
        rebootAndWaitUntilReady();
    }

    @Test
    public void testAppSearchDataMigration() throws Exception {
        runDeviceTestAsUserInPkgA("setUpStressTestDbs", mPrimaryUserId);
        getDevice()
                .executeShellCommand(
                        "device_config override appsearch isolated_storage_disabled false");
        rebootAndWaitUntilReady();

        // TODO(b/430289015) better way to check migration finishes.
        Thread.sleep(240_000); // 4min
        runDeviceTestAsUserInPkgA("verifyStressTestDbs", mPrimaryUserId);
    }
}
