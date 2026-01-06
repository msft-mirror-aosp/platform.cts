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

package com.android.tests.developerverification.host;

import static android.content.pm.Flags.FLAG_VERIFICATION_SERVICE;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeNotNull;

import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.host.HostFlagsValueProvider;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.compatibility.common.util.CddTest;
import com.android.ddmlib.Log;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.util.FileUtil;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@CddTest(requirements = {"9.18/C-3-2"})
@RunWith(DeviceJUnit4ClassRunner.class)
@RequiresFlagsEnabled(FLAG_VERIFICATION_SERVICE)
public class DeveloperVerificationHostsideTest extends BaseHostJUnit4Test
        implements IBuildReceiver {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            HostFlagsValueProvider.createCheckFlagsRule(this::getDevice, this.getClass());

    private static final String TAG = "DeveloperVerificationHostsideTest";
    private static final String TEST_BASE = "DeveloperVerificationHostsideTest";
    private static final String TEST_INSTALL_APK = "CtsStatsdAtomEmptyApp.apk";
    private static final String NO_VERIFIER_RESULT = "No verification service provider specified.";

    private ITestDevice mDevice;
    private static File sBasePath;

    private IBuildInfo mCtsBuildInfo;

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mCtsBuildInfo = buildInfo;
    }

    @Before
    public void setUp() throws Exception {
        mDevice = getDevice();
        if (sBasePath == null) {
            sBasePath = FileUtil.createTempDir(TEST_BASE);
            sBasePath.deleteOnExit();
        }
    }

    @Test
    @AppModeFull
    public void testAdbInstallSucceeds() throws Exception {
        CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(mCtsBuildInfo);
        assertThat(mDevice.installPackage(buildHelper.getTestFile(TEST_INSTALL_APK), true))
                .isNull();
    }

    @Test
    @AppModeFull
    public void testReinstallingVerifierSucceeds() throws Exception {
        String verifierComponentNameStr =
                mDevice.executeShellCommand("pm get-developer-verification-service-provider")
                        .trim();
        // No verifier installed
        assumeFalse(NO_VERIFIER_RESULT, NO_VERIFIER_RESULT.equals(verifierComponentNameStr));

        String verifierPackageName =
                getPackageNameFromComponentNameString(verifierComponentNameStr);
        assertThat(verifierPackageName).isNotNull();
        String updateOwnerPackageName = getUpdateOwnerPackageName(verifierPackageName);
        List<String> verifierApksPathsOnDevice = getApksPaths(verifierPackageName);
        assertThat(verifierApksPathsOnDevice).isNotEmpty();
        List<File> verifierApksLocal = pullFiles(verifierApksPathsOnDevice);
        assertThat(verifierApksLocal).isNotEmpty();
        // Use --update-ownership and -i to make sure the original update ownership info is not lost
        ArrayList<String> extraArgs = new ArrayList<>();
        extraArgs.add("--force-verification");
        if (updateOwnerPackageName != null) {
            extraArgs.add("--update-ownership");
            extraArgs.add("-i " + updateOwnerPackageName);
        }
        // The reinstallation should always go through
        assertThat(
                        mDevice.installPackages(
                                verifierApksLocal, true, extraArgs.toArray(new String[0])))
                .isNull();
        List<String> verifierApkPathsOnDeviceAfterReinstall = getApksPaths(verifierPackageName);
        assertThat(verifierApkPathsOnDeviceAfterReinstall).isNotEmpty();
        assertThat(verifierApkPathsOnDeviceAfterReinstall.getFirst()).startsWith("/data");
        // Make sure that the update ownership is preserved
        if (updateOwnerPackageName != null) {
            assertThat(getUpdateOwnerPackageName(verifierPackageName))
                    .isEqualTo(updateOwnerPackageName);
        }
    }

    @Test
    @AppModeFull
    public void testReinstallingVerifierUpdateOwnerSucceeds() throws Exception {
        String verifierComponentNameStr =
                mDevice.executeShellCommand("pm get-developer-verification-service-provider")
                        .trim();
        // No verifier installed
        assumeFalse(NO_VERIFIER_RESULT, NO_VERIFIER_RESULT.equals(verifierComponentNameStr));

        String verifierPackageName =
                getPackageNameFromComponentNameString(verifierComponentNameStr);
        assertThat(verifierPackageName).isNotNull();
        String updateOwnerPackageName = getUpdateOwnerPackageName(verifierPackageName);
        // It is not required that the verifier must have an update owner. Skip if it doesn't.
        assumeNotNull(updateOwnerPackageName);
        List<String> updateOwnerApksPathsOnDevice = getApksPaths(updateOwnerPackageName);
        assertThat(updateOwnerApksPathsOnDevice).isNotEmpty();
        List<File> updateOwnerApksLocal = pullFiles(updateOwnerApksPathsOnDevice);
        assertThat(updateOwnerApksLocal).isNotEmpty();
        // The reinstallation should always go through
        assertThat(mDevice.installPackages(updateOwnerApksLocal, true, "--force-verification"))
                .isNull();
        List<String> updateOwnerApksPathsOnDeviceAfterReinstall =
                getApksPaths(updateOwnerPackageName);
        assertThat(updateOwnerApksPathsOnDeviceAfterReinstall).isNotEmpty();
        assertThat(updateOwnerApksPathsOnDeviceAfterReinstall.getFirst()).startsWith("/data");
    }

    private List<File> pullFiles(List<String> pathsOnDevice) {
        List<File> localTempFiles = new ArrayList<>();
        for (String pathOnDevice : pathsOnDevice) {
            try {
                final File localTempFile =
                        File.createTempFile(new File(pathOnDevice).getName(), ".apk", sBasePath);
                assertThat(localTempFile).isNotNull();
                assertThat(mDevice.pullFile(pathOnDevice, localTempFile)).isTrue();
                localTempFiles.add(localTempFile);
            } catch (IOException | DeviceNotAvailableException e) {
                Log.e(TAG, "Failed to pull file " + pathOnDevice);
            }
        }
        return localTempFiles;
    }

    private static String getPackageNameFromComponentNameString(String componentNameStr) {
        // Get the substring between { and /
        int start = componentNameStr.indexOf('{');
        int end = componentNameStr.indexOf('/');
        if (start == -1 || end == -1) {
            return null;
        }
        return componentNameStr.substring(start + 1, end);
    }

    private List<String> getApksPaths(String packageName) throws DeviceNotAvailableException {
        String apksPathsStr = mDevice.executeShellCommand("pm path " + packageName).trim();
        ArrayList<String> results = new ArrayList<>();
        String[] apksPaths = apksPathsStr.split("\n");
        for (String apkPath : apksPaths) {
            int start = apkPath.indexOf(':');
            if (start == -1) {
                continue;
            }
            results.add(apkPath.substring(start + 1));
        }
        return results;
    }

    private String getUpdateOwnerPackageName(String packageName)
            throws DeviceNotAvailableException {
        String updateOwnerStr =
                mDevice.executeShellCommand(
                                "dumpsys package " + packageName + " | grep updateOwner")
                        .trim();
        int start = updateOwnerStr.indexOf('=');
        if (start == -1) {
            return null;
        }
        return updateOwnerStr.substring(start + 1);
    }
}
