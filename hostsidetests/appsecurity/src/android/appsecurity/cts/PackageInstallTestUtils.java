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

package android.appsecurity.cts;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.util.FileUtil;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Utility class with methods to verify APKs from the package's resources or a build install / fail
 * to install as required by the test.
 */
public class PackageInstallTestUtils {
    private static final String TEST_APK_RESOURCE_PREFIX = "/pkgsigverify/";
    private static final String INSTALL_ARG_FORCE_QUERYABLE = "--force-queryable";
    private static final String INSTALL_ARG_BYPASS_LOW_TARGET_SDK_BLOCK =
            "--bypass-low-target-sdk-block";

    /**
     * Asserts the provided {@code apkFilenameInResources} installs successfully on the provided
     * {@code device}.
     */
    public static void assertInstallOnDeviceSucceeds(
            String apkFilenameInResources, ITestDevice device) throws Exception {
        String installResult =
                installPackageFromResourceOnDevice(apkFilenameInResources, false, device);
        if (installResult != null) {
            fail("Failed to install " + apkFilenameInResources + ": " + installResult);
        }
    }

    /**
     * Asserts the provided {@code apkFilenameInResources} fails to install on the provided {@code
     * device}.
     */
    public static void assertInstallOnDeviceFails(String apkFilenameInResources, ITestDevice device)
            throws Exception {
        String installResult =
                installPackageFromResourceOnDevice(apkFilenameInResources, false, device);
        if (installResult == null) {
            fail("Install of " + apkFilenameInResources + " succeeded but was expected to fail");
        }
    }

    /**
     * Installs the specified {@code apkFilenameInResources} as an {@code ephemeral} package on the
     * provided {@code device}, returning the output of the install command.
     *
     * <p>A String containing the error message will be returned if an error is encountered during
     * the install, otherwise a null String is returned on success.
     */
    public static String installPackageFromResourceOnDevice(
            String apkFilenameInResources, boolean ephemeral, ITestDevice device)
            throws IOException, DeviceNotAvailableException {
        // ITestDevice.installPackage API requires the APK to be installed be a File. We thus
        // copy the requested resource into a temporary file, attempt to install it, and delete the
        // file during cleanup.
        File apkFile = null;
        try {
            apkFile = getFileFromResource(apkFilenameInResources);
            if (ephemeral) {
                return device.adbInstallPackage(
                        apkFile,
                        true,
                        "--ephemeral",
                        INSTALL_ARG_FORCE_QUERYABLE,
                        INSTALL_ARG_BYPASS_LOW_TARGET_SDK_BLOCK);
            } else {
                return device.adbInstallPackage(
                        apkFile,
                        true,
                        INSTALL_ARG_FORCE_QUERYABLE,
                        INSTALL_ARG_BYPASS_LOW_TARGET_SDK_BLOCK);
            }
        } finally {
            cleanUpFile(apkFile);
            device.deleteFile("/data/local/tmp/" + apkFile.getName());
        }
    }

    /**
     * Asserts the specified {@code apkName} as built from the {@code buildInfo} installs
     * successfully on the provided {@code device}.
     */
    public static void assertInstallOnDeviceFromBuildSucceeds(
            String apkName, ITestDevice device, IBuildInfo buildInfo) throws Exception {
        String result = installApkOnDeviceFromBuild(apkName, device, buildInfo);
        assertNull("failed to install " + apkName + ", Reason: " + result, result);
    }

    /**
     * Asserts the specified {@code apkName} as built from the {@code buildInfo} fails to install on
     * the provided {@code device}.
     */
    public static void assertInstallOnDeviceFromBuildFails(
            String apkName, ITestDevice device, IBuildInfo buildInfo) throws Exception {
        String result = installApkOnDeviceFromBuild(apkName, device, buildInfo);
        assertNotNull("Successfully installed " + apkName + " when failure was expected", result);
    }

    /**
     * Installs the specified {@code apkName} as built from the {@code buildInfo} on the provided
     * {@code device}, returning the output of the install command.
     *
     * <p>A String containing the error message will be returned if an error is encountered during
     * the install, otherwise a null String is returned on success.
     */
    public static String installApkOnDeviceFromBuild(
            String apkName, ITestDevice device, IBuildInfo buildInfo) throws Exception {
        CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(buildInfo);
        File apk = buildHelper.getTestFile(apkName);
        try {
            return device.installPackage(
                    apk,
                    true,
                    INSTALL_ARG_FORCE_QUERYABLE,
                    INSTALL_ARG_BYPASS_LOW_TARGET_SDK_BLOCK);
        } finally {
            device.deleteFile("/data/local/tmp/" + apk.getName());
        }
    }

    /**
     * Returns a {@link File} in a temporary directory for the specified {@code
     * filenameInResources}.
     */
    public static File getFileFromResource(String filenameInResources)
            throws IOException, IllegalArgumentException {
        String fullResourceName = TEST_APK_RESOURCE_PREFIX + filenameInResources;
        File tempDir = FileUtil.createTempDir("pkginstalltest");
        File file = new File(tempDir, filenameInResources);
        InputStream in = PackageInstallTestUtils.class.getResourceAsStream(fullResourceName);
        if (in == null) {
            throw new IllegalArgumentException("Resource not found: " + fullResourceName);
        }
        OutputStream out = new BufferedOutputStream(new FileOutputStream(file));
        byte[] buf = new byte[65536];
        int chunkSize;
        while ((chunkSize = in.read(buf)) != -1) {
            out.write(buf, 0, chunkSize);
        }
        out.close();
        return file;
    }

    /** Deletes the specified {@code file} and its temporary directory. */
    public static void cleanUpFile(File file) {
        if (file != null && file.exists()) {
            file.delete();
            // Delete the parent dir as well which is a temp dir
            File parent = file.getParentFile();
            if (parent.exists()) {
                parent.delete();
            }
        }
    }
}
