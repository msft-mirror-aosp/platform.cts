/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.packageinstaller.criticaluserjourney.cts;

import android.platform.test.annotations.AppModeFull;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for PackageInstaller CUJs via PackageInstaller.Session APIs.
 */
@RunWith(AndroidJUnit4.class)
@AppModeFull
public class InstallationViaSessionTest extends PackageInstallerCujTestBase {

    @Test
    public void newInstall_launchGrantPermission_installButton_success() throws Exception {
        startInstallationViaPackageInstallerSession();

        waitForUiIdle();

        clickSettingsButton();

        toggleToGrantRequestInstallPackagesPermission();

        clickInstallButton();

        assertInstallSuccess();
        assertTestPackageInstalled();
    }

    @Test
    public void newInstall_launchGrantPermission_backKey_failed() throws Exception {
        startInstallationViaPackageInstallerSession();

        waitForUiIdle();

        clickSettingsButton();

        toggleToGrantRequestInstallPackagesPermission();

        pressBack();

        assertInstallFailureAborted();
        assertTestPackageNotInstalled();
    }

    @Test
    public void newInstall_launchGrantPermission_touchOutside_failed() throws Exception {
        startInstallationViaPackageInstallerSession();

        waitForUiIdle();

        clickSettingsButton();

        toggleToGrantRequestInstallPackagesPermission();

        touchOutside();

        assertInstallFailureAborted();
        assertTestPackageNotInstalled();
    }

    @Test
    public void newInstall_launchGrantPermission_cancelButton_failed() throws Exception {
        startInstallationViaPackageInstallerSession();

        waitForUiIdle();

        clickSettingsButton();

        toggleToGrantRequestInstallPackagesPermission();

        clickCancelButton();

        assertInstallFailureAborted();
        assertTestPackageNotInstalled();
    }

    @Test
    public void newInstall_launchButNoGrantPermission_failed() throws Exception {
        startInstallationViaPackageInstallerSession();

        waitForUiIdle();

        clickSettingsButton();

        exitGrantPermissionSettings();

        assertInstallFailureAborted();
        assertTestPackageNotInstalled();
    }

    @Test
    public void newInstall_noLaunchGrantPermission_installButton_success() throws Exception {
        grantRequestInstallPackagesPermission();

        startInstallationViaPackageInstallerSession();

        waitForUiIdle();

        clickInstallButton();

        assertInstallSuccess();
        assertTestPackageInstalled();
    }

    @Test
    public void newInstall_noLaunchGrantPermission_backKey_failed() throws Exception {
        grantRequestInstallPackagesPermission();

        startInstallationViaPackageInstallerSession();

        waitForUiIdle();

        pressBack();

        assertInstallFailureAborted();
        assertTestPackageNotInstalled();
    }

    @Test
    public void newInstall_noLaunchGrantPermission_touchOutside_failed() throws Exception {
        grantRequestInstallPackagesPermission();

        startInstallationViaPackageInstallerSession();

        waitForUiIdle();

        touchOutside();

        assertInstallFailureAborted();
        assertTestPackageNotInstalled();
    }

    @Test
    public void newInstall_noLaunchGrantPermission_cancelButton_failed() throws Exception {
        grantRequestInstallPackagesPermission();

        startInstallationViaPackageInstallerSession();

        waitForUiIdle();

        clickCancelButton();

        assertInstallFailureAborted();
        assertTestPackageNotInstalled();
    }

    @Test
    public void update_launchGrantPermission_updateButton_success() throws Exception {
        installTestPackage();

        startInstallationUpdateViaPackageInstallerSession();

        waitForUiIdle();

        clickSettingsButton();

        toggleToGrantRequestInstallPackagesPermission();

        clickUpdateButton();

        assertInstallSuccess();
        assertTestPackageLabelV2Installed();
    }

    @Test
    public void update_launchGrantPermission_backKey_failed() throws Exception {
        installTestPackage();

        startInstallationUpdateViaPackageInstallerSession();

        waitForUiIdle();

        clickSettingsButton();

        toggleToGrantRequestInstallPackagesPermission();

        pressBack();

        assertInstallFailureAborted();
        assertTestPackageInstalled();
    }

    @Test
    public void update_launchGrantPermission_touchOutside_failed() throws Exception {
        installTestPackage();

        startInstallationUpdateViaPackageInstallerSession();

        waitForUiIdle();

        clickSettingsButton();

        toggleToGrantRequestInstallPackagesPermission();

        touchOutside();

        assertInstallFailureAborted();
        assertTestPackageInstalled();
    }

    @Test
    public void update_launchGrantPermission_cancelButton_failed() throws Exception {
        installTestPackage();

        startInstallationUpdateViaPackageInstallerSession();

        waitForUiIdle();

        clickSettingsButton();

        toggleToGrantRequestInstallPackagesPermission();

        clickCancelButton();

        assertInstallFailureAborted();
        assertTestPackageInstalled();
    }

    @Test
    public void update_launchButNoGrantPermission_failed() throws Exception {
        installTestPackage();

        startInstallationUpdateViaPackageInstallerSession();

        waitForUiIdle();

        clickSettingsButton();

        exitGrantPermissionSettings();

        assertInstallFailureAborted();
        assertTestPackageInstalled();
    }

    @Test
    public void update_noLaunchGrantPermission_updateButton_success() throws Exception {
        installTestPackage();

        grantRequestInstallPackagesPermission();

        startInstallationUpdateViaPackageInstallerSession();

        waitForUiIdle();

        clickUpdateButton();

        assertInstallSuccess();
        assertTestPackageLabelV2Installed();
    }

    @Test
    public void update_noLaunchGrantPermission_backKey_failed() throws Exception {
        installTestPackage();

        grantRequestInstallPackagesPermission();

        startInstallationUpdateViaPackageInstallerSession();

        waitForUiIdle();

        pressBack();

        assertInstallFailureAborted();
        assertTestPackageInstalled();
    }

    @Test
    public void update_noLaunchGrantPermission_touchOutside_failed() throws Exception {
        installTestPackage();

        grantRequestInstallPackagesPermission();

        startInstallationUpdateViaPackageInstallerSession();

        waitForUiIdle();

        touchOutside();

        assertInstallFailureAborted();
        assertTestPackageInstalled();
    }

    @Test
    public void update_noLaunchGrantPermission_cancelButton_failed() throws Exception {
        installTestPackage();

        grantRequestInstallPackagesPermission();

        startInstallationUpdateViaPackageInstallerSession();

        waitForUiIdle();

        clickCancelButton();

        assertInstallFailureAborted();
        assertTestPackageInstalled();
    }
}
