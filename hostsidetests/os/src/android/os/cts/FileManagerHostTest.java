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

package android.os.cts;

import static com.google.common.truth.Truth.assertThat;

import android.app.privatecompute.flags.Flags;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.host.HostFlagsValueProvider;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Host side tests for FileManager API.
 *
 * <p>The verification flow for these tests involves multiple stages:
 *
 * <ol>
 *   <li><b>Host Setup:</b> This class installs a helper APK and enables trust for instrumented
 *       clients in the PCC sandbox via {@code cmd pcc_sandbox enable-trust-instrumented-clients}.
 *   <li><b>Host Trigger:</b> This class triggers a device-side test in the helper APK.
 *   <li><b>Device Operation:</b> The helper app enqueues a Move or Copy operation via the {@link
 *       android.os.storage.FileManager}.
 *   <li><b>System Execution:</b> The Android system performs the background file transfer between
 *       the standard app data directory and the protected Private Compute Core (PCC) directory.
 *   <li><b>PCC Verification:</b> Once the system reports completion, the device test binds to a
 *       {@code PccTestService} which runs in an isolated PCC process and verifies the operation
 *       using a {@code ResultReceiver}.
 *   <li><b>Host Confirmation:</b> This host-side test waits for the device tests to complete
 *       successfully, which inherently validates the file operations since verification happens
 *       synchronously on device.
 * </ol>
 */
@RunWith(DeviceJUnit4ClassRunner.class)
@AppModeFull
public class FileManagerHostTest extends BaseHostJUnit4Test {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            HostFlagsValueProvider.createCheckFlagsRule(this::getDevice, this.getClass());

    private static final String HELPER_APK = "CtsFileManagerTestApp.apk";
    private static final String HELPER_PACKAGE = "com.android.cts.filemanager.helper";
    private static final String DEVICE_TEST_CLASS =
            "com.android.cts.filemanager.helper.FileManagerDeviceTest";

    @Before
    public void setUp() throws Exception {
        installPackage(HELPER_APK);
        getDevice().executeShellCommand("cmd pcc_sandbox enable-trust-instrumented-clients");
    }

    @After
    public void tearDown() throws Exception {
        getDevice().executeShellCommand("cmd pcc_sandbox disable-trust-instrumented-clients");
        uninstallPackage(HELPER_PACKAGE);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
    public void testMoveFileToPcc() throws Exception {
        // Run the device-side test to trigger the operation and PCC verification
        assertThat(runDeviceTests(HELPER_PACKAGE, DEVICE_TEST_CLASS, "testTriggerMoveFile"))
                .isTrue();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
    public void testMoveFolderToPcc() throws Exception {
        // Run the device-side test to trigger the operation and PCC verification
        assertThat(runDeviceTests(HELPER_PACKAGE, DEVICE_TEST_CLASS, "testTriggerMoveFolder"))
                .isTrue();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
    public void testCopyFileToPcc() throws Exception {
        // Run the device-side test to trigger the operation and PCC verification
        assertThat(runDeviceTests(HELPER_PACKAGE, DEVICE_TEST_CLASS, "testTriggerCopyFile"))
                .isTrue();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
    public void testCopyFolderToPcc() throws Exception {
        // Run the device-side test to trigger the operation and PCC verification
        assertThat(runDeviceTests(HELPER_PACKAGE, DEVICE_TEST_CLASS, "testTriggerCopyFolder"))
                .isTrue();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
    public void testMoveFileOverwriteToPcc() throws Exception {
        // Run the device-side test to trigger the operation and PCC verification
        assertThat(
                        runDeviceTests(
                                HELPER_PACKAGE, DEVICE_TEST_CLASS, "testTriggerMoveFileOverwrite"))
                .isTrue();
    }
}
