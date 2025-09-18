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

import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.host.HostFlagsValueProvider;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.compatibility.common.util.CddTest;
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

@CddTest(requirements = {"9.18/C-3-2"})
@RunWith(DeviceJUnit4ClassRunner.class)
@RequiresFlagsEnabled(FLAG_VERIFICATION_SERVICE)
public class DeveloperVerificationHostsideTest extends BaseHostJUnit4Test
        implements IBuildReceiver {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            HostFlagsValueProvider.createCheckFlagsRule(this::getDevice);

    private static final String TEST_BASE = "DeveloperVerificationHostsideTest";
    private static final String TEST_INSTALL_APK = "CtsStatsdAtomEmptyApp.apk";

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
        if (verifierComponentNameStr.isEmpty()) {
            return;
        }
        String verifierPackageName =
                getPackageNameFromComponentNameString(verifierComponentNameStr);
        assertThat(verifierPackageName).isNotNull();
        String verifierApkPathOnDevice = getApkPath(verifierPackageName);
        assertThat(verifierApkPathOnDevice).isNotNull();
        File verifierApkLocal = pullFile(verifierApkPathOnDevice);
        assertThat(verifierApkLocal).isNotNull();
        // The reinstallation should always go through
        assertThat(mDevice.installPackage(verifierApkLocal, true, "--force-verification")).isNull();
        String verifierApkPathOnDeviceAfterReinstall = getApkPath(verifierPackageName);
        assertThat(verifierApkPathOnDeviceAfterReinstall).isNotNull();
        assertThat(verifierApkPathOnDeviceAfterReinstall).startsWith("/data");
    }

    @Test
    @AppModeFull
    public void testReinstallingVerifierUpdateOwnerSucceeds() throws Exception {
        String verifierComponentNameStr =
                mDevice.executeShellCommand("pm get-developer-verification-service-provider")
                        .trim();
        if (verifierComponentNameStr.isEmpty()) {
            return;
        }
        String verifierPackageName =
                getPackageNameFromComponentNameString(verifierComponentNameStr);
        assertThat(verifierPackageName).isNotNull();
        String updateOwnerPackageName = getUpdateOwnerPackageName(verifierPackageName);
        assertThat(updateOwnerPackageName).isNotNull();
        String updateOwnerApkPathOnDevice = getApkPath(updateOwnerPackageName);
        assertThat(updateOwnerApkPathOnDevice).isNotNull();
        File updateOwnerApkLocal = pullFile(updateOwnerApkPathOnDevice);
        assertThat(updateOwnerApkLocal).isNotNull();
        // The reinstallation should always go through
        assertThat(mDevice.installPackage(updateOwnerApkLocal, true, "--force-verification"))
                .isNull();
        String updateOwnerApkPathOnDeviceAfterReinstall = getApkPath(updateOwnerPackageName);
        assertThat(updateOwnerApkPathOnDeviceAfterReinstall).isNotNull();
        assertThat(updateOwnerApkPathOnDeviceAfterReinstall).startsWith("/data");
    }

    private File pullFile(String pathOnDevice) {
        try {
            final File localTempFile =
                    File.createTempFile(new File(pathOnDevice).getName(), "", sBasePath);
            assertThat(localTempFile).isNotNull();
            assertThat(mDevice.pullFile(pathOnDevice, localTempFile)).isTrue();
            return localTempFile;
        } catch (IOException | DeviceNotAvailableException e) {
            return null;
        }
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

    private String getApkPath(String packageName) throws DeviceNotAvailableException {
        String apkPathStr = mDevice.executeShellCommand("pm path " + packageName).trim();
        int start = apkPathStr.indexOf(':');
        if (start == -1) {
            return null;
        }
        return apkPathStr.substring(start + 1);
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
