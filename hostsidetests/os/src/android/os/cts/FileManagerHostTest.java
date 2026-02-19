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

import com.android.compatibility.common.util.PollingCheck;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.util.CommandResult;

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
 *   <li><b>Host Trigger:</b> This class installs a helper APK and triggers a device-side test.
 *   <li><b>Device Operation:</b> The helper app enqueues a Move or Copy operation via the {@link
 *       android.os.storage.FileManager}.
 *   <li><b>System Execution:</b> The Android system performs the background file transfer between
 *       the standard app data directory and the protected Private Compute Core (PCC) directory.
 *   <li><b>PCC Verification:</b> Once the system reports completion, the device test binds to a
 *       {@code PccTestService} which runs in a isolated PCC process. This service verifies that it
 *       can both see and <b>write</b> to the files in the target PCC directory.
 *   <li><b>Canary Signal:</b> If and only if the PCC service successfully verifies the files, it
 *       creates a canary file named {@code verification_success.txt} in the PCC data directory.
 *   <li><b>Host Confirmation:</b> This host-side test waits for that canary file to appear using
 *       shell commands. This ensures end-to-end success: the system moved the data, and the
 *       PCC-process has the correct ownership and permissions to use it.
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

    private int mUserId;

    @Before
    public void setUp() throws Exception {
        mUserId = getDevice().getCurrentUser();
        installPackage(HELPER_APK);
    }

    @After
    public void tearDown() throws Exception {
        uninstallPackage(HELPER_PACKAGE);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
    public void testMoveFileToPcc() throws Exception {
        String fileName = "test_file.txt";
        String targetPrefix = "migrated";
        String expectedTargetPath =
                getPccDataPath(mUserId, HELPER_PACKAGE) + "/" + targetPrefix + "/" + fileName;

        // Run the device-side test to trigger the operation and PCC verification
        assertThat(runDeviceTests(HELPER_PACKAGE, DEVICE_TEST_CLASS, "testTriggerMoveFile"))
                .isTrue();

        // Final verification of canary file created by PCC service
        String canaryPath = getPccDataPath(mUserId, HELPER_PACKAGE) + "/verification_success.txt";
        waitForFile(canaryPath, 10000);
        assertThat(fileExists(canaryPath)).isTrue();
        assertThat(fileExists(expectedTargetPath)).isTrue();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
    public void testMoveFolderToPcc() throws Exception {
        String folderName = "test_folder";
        String targetPrefix = "archived";
        String expectedTargetFolderPath =
                getPccDataPath(mUserId, HELPER_PACKAGE) + "/" + targetPrefix + "/" + folderName;

        // Run the device-side test to trigger the operation and PCC verification
        assertThat(runDeviceTests(HELPER_PACKAGE, DEVICE_TEST_CLASS, "testTriggerMoveFolder"))
                .isTrue();

        // Final verification of canary file created by PCC service
        String canaryPath = getPccDataPath(mUserId, HELPER_PACKAGE) + "/verification_success.txt";
        waitForFile(canaryPath, 10000);
        assertThat(fileExists(canaryPath)).isTrue();
        assertThat(directoryExists(expectedTargetFolderPath)).isTrue();
        assertThat(fileExists(expectedTargetFolderPath + "/file1.txt")).isTrue();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
    public void testCopyFileToPcc() throws Exception {
        String fileName = "test_copy_file.txt";
        String targetPrefix = "copied";
        String expectedSourcePath = getAppDataPath(mUserId, HELPER_PACKAGE) + "/files/" + fileName;
        String expectedTargetPath =
                getPccDataPath(mUserId, HELPER_PACKAGE) + "/" + targetPrefix + "/" + fileName;

        // Run the device-side test to trigger the operation and PCC verification
        assertThat(runDeviceTests(HELPER_PACKAGE, DEVICE_TEST_CLASS, "testTriggerCopyFile"))
                .isTrue();

        // Final verification of canary file created by PCC service
        String canaryPath = getPccDataPath(mUserId, HELPER_PACKAGE) + "/verification_success.txt";
        waitForFile(canaryPath, 10000);
        assertThat(fileExists(canaryPath)).isTrue();
        assertThat(fileExists(expectedTargetPath)).isTrue();
        assertThat(fileExists(expectedSourcePath)).isTrue();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
    public void testCopyFolderToPcc() throws Exception {
        String folderName = "test_copy_folder";
        String targetPrefix = "copied_folder";
        String expectedSourceFolderPath =
                getAppDataPath(mUserId, HELPER_PACKAGE) + "/files/" + folderName;
        String expectedTargetFolderPath =
                getPccDataPath(mUserId, HELPER_PACKAGE) + "/" + targetPrefix + "/" + folderName;

        // Run the device-side test to trigger the operation and PCC verification
        assertThat(runDeviceTests(HELPER_PACKAGE, DEVICE_TEST_CLASS, "testTriggerCopyFolder"))
                .isTrue();

        // Final verification of canary file created by PCC service
        String canaryPath = getPccDataPath(mUserId, HELPER_PACKAGE) + "/verification_success.txt";
        waitForFile(canaryPath, 10000);
        assertThat(fileExists(canaryPath)).isTrue();
        assertThat(directoryExists(expectedTargetFolderPath)).isTrue();
        assertThat(fileExists(expectedTargetFolderPath + "/file1.txt")).isTrue();
        assertThat(directoryExists(expectedSourceFolderPath)).isTrue();
        assertThat(fileExists(expectedSourceFolderPath + "/file1.txt")).isTrue();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
    public void testMoveFileOverwriteToPcc() throws Exception {
        String fileName = "test_overwrite.txt";
        String targetPrefix = "overwritten";
        String expectedTargetPath =
                getPccDataPath(mUserId, HELPER_PACKAGE) + "/" + targetPrefix + "/" + fileName;

        // Run the device-side test to trigger the operation and PCC verification
        assertThat(
                        runDeviceTests(
                                HELPER_PACKAGE, DEVICE_TEST_CLASS, "testTriggerMoveFileOverwrite"))
                .isTrue();

        // Final verification of canary file created by PCC service
        String canaryPath = getPccDataPath(mUserId, HELPER_PACKAGE) + "/verification_success.txt";
        waitForFile(canaryPath, 10000);
        assertThat(fileExists(canaryPath)).isTrue();
        assertThat(fileExists(expectedTargetPath)).isTrue();
    }

    /** Returns the standard application data path for the given user and package. */
    private String getAppDataPath(int userId, String packageName) {
        return "/data/user/" + userId + "/" + packageName;
    }

    /**
     * Returns the Private Compute Core (PCC) data path for the given user and package. This
     * directory is protected and only accessible by PCC-authorized processes.
     */
    private String getPccDataPath(int userId, String packageName) {
        return getAppDataPath(userId, packageName) + "-pcc";
    }

    /**
     * Checks if a regular file exists at the given path on the device.
     *
     * <p>This uses the shell {@code [ -f <path> ]} command, which returns an exit code of 0 only if
     * the path exists AND is a regular file (not a directory or symlink).
     */
    private boolean fileExists(String path) throws Exception {
        CommandResult result = getDevice().executeShellV2Command("[ -f " + path + " ]");
        return result.getExitCode() == 0;
    }

    /**
     * Checks if a directory exists at the given path on the device.
     *
     * <p>This uses the shell {@code [ -d <path> ]} command, which returns an exit code of 0 only if
     * the path exists AND is a directory.
     */
    private boolean directoryExists(String path) throws Exception {
        CommandResult result = getDevice().executeShellV2Command("[ -d " + path + " ]");
        return result.getExitCode() == 0;
    }

    /**
     * Polls for the existence of a file at the given path until it appears or the timeout is
     * reached.
     */
    private void waitForFile(String path, long timeoutMs) throws Exception {
        PollingCheck.check(
                "File " + path + " did not appear within " + timeoutMs + "ms",
                timeoutMs,
                () -> fileExists(path));
    }
}
