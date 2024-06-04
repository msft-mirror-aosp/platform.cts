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
 * Tests for PackageInstaller CUJs via startActivity.
 */
@RunWith(AndroidJUnit4.class)
@AppModeFull
public class InstallationViaIntentTest extends PackageInstallerCujTestBase {

    @Test
    public void newInstall_launchGrantPermission_installButton_success() throws Exception {
        startInstallationViaIntent();

        waitForUiIdle();

        clickSettingsButton();

        toggleAllowFromSource();

        clickInstallButton();

        assertInstallSuccessDialogAndClickDoneButton();
        assertTestPackageInstalled();
    }

    @Test
    public void newInstall_launchGrantPermission_backKey_failed() throws Exception {
        startInstallationViaIntent();

        waitForUiIdle();

        clickSettingsButton();

        toggleAllowFromSource();

        pressBack();

        assertTestPackageNotInstalled();
    }

    @Test
    public void newInstall_launchGrantPermission_touchOutside_failed() throws Exception {
        startInstallationViaIntent();

        waitForUiIdle();

        clickSettingsButton();

        toggleAllowFromSource();

        touchOutside();

        assertTestPackageNotInstalled();
    }

    @Test
    public void newInstall_launchGrantPermission_cancelButton_failed() throws Exception {
        startInstallationViaIntent();

        waitForUiIdle();

        clickSettingsButton();

        toggleAllowFromSource();

        clickCancelButton();

        assertTestPackageNotInstalled();
    }

    @Test
    public void newInstall_launchButNoGrantPermission_failed() throws Exception {
        startInstallationViaIntent();

        waitForUiIdle();

        clickSettingsButton();

        exitAllowFromSettings();

        assertTestPackageNotInstalled();
    }

    @Test
    public void newInstall_noLaunchGrantPermission_installButton_success() throws Exception {
        grantRequestInstallPackagesPermission();

        startInstallationViaIntent();

        waitForUiIdle();

        clickInstallButton();

        assertInstallSuccessDialogAndClickDoneButton();
        assertTestPackageInstalled();
    }

    @Test
    public void newInstall_noLaunchGrantPermission_backKey_failed() throws Exception {
        grantRequestInstallPackagesPermission();

        startInstallationViaIntent();

        waitForUiIdle();

        pressBack();

        assertTestPackageNotInstalled();
    }

    @Test
    public void newInstall_noLaunchGrantPermission_touchOutside_failed() throws Exception {
        grantRequestInstallPackagesPermission();

        startInstallationViaIntent();

        waitForUiIdle();

        touchOutside();

        assertTestPackageNotInstalled();
    }

    @Test
    public void newInstall_noLaunchGrantPermission_cancelButton_failed() throws Exception {
        grantRequestInstallPackagesPermission();

        startInstallationViaIntent();

        waitForUiIdle();

        clickCancelButton();

        assertTestPackageNotInstalled();
    }

    @Test
    public void update_launchGrantPermission_updateButton_success() throws Exception {
        installTestPackage();

        startInstallationUpdateViaIntent();

        waitForUiIdle();

        clickSettingsButton();

        toggleAllowFromSource();

        clickUpdateButton();

        assertInstallSuccessDialogAndClickDoneButton();
        assertTestPackageLabelV2Installed();
    }

    @Test
    public void update_launchGrantPermission_backKey_failed() throws Exception {
        installTestPackage();

        startInstallationUpdateViaIntent();

        waitForUiIdle();

        clickSettingsButton();

        toggleAllowFromSource();

        pressBack();

        assertTestPackageInstalled();
    }

    @Test
    public void update_launchGrantPermission_touchOutside_failed() throws Exception {
        installTestPackage();

        startInstallationUpdateViaIntent();

        waitForUiIdle();

        clickSettingsButton();

        toggleAllowFromSource();

        touchOutside();

        assertTestPackageInstalled();
    }

    @Test
    public void update_launchGrantPermission_cancelButton_failed() throws Exception {
        installTestPackage();

        startInstallationUpdateViaIntent();

        waitForUiIdle();

        clickSettingsButton();

        toggleAllowFromSource();

        clickCancelButton();

        assertTestPackageInstalled();
    }

    @Test
    public void update_launchButNoGrantPermission_failed() throws Exception {
        installTestPackage();

        startInstallationUpdateViaIntent();

        waitForUiIdle();

        clickSettingsButton();

        exitAllowFromSettings();

        assertTestPackageInstalled();
    }

    @Test
    public void update_noLaunchGrantPermission_updateButton_success() throws Exception {
        installTestPackage();

        grantRequestInstallPackagesPermission();

        startInstallationUpdateViaIntent();

        waitForUiIdle();

        clickUpdateButton();

        assertInstallSuccessDialogAndClickDoneButton();
        assertTestPackageLabelV2Installed();
    }

    @Test
    public void update_noLaunchGrantPermission_backKey_failed() throws Exception {
        installTestPackage();

        grantRequestInstallPackagesPermission();

        startInstallationUpdateViaIntent();

        waitForUiIdle();

        pressBack();

        assertTestPackageInstalled();
    }

    @Test
    public void update_noLaunchGrantPermission_touchOutside_failed() throws Exception {
        installTestPackage();

        grantRequestInstallPackagesPermission();

        startInstallationUpdateViaIntent();

        waitForUiIdle();

        touchOutside();

        assertTestPackageInstalled();
    }

    @Test
    public void update_noLaunchGrantPermission_cancelButton_failed() throws Exception {
        installTestPackage();

        grantRequestInstallPackagesPermission();

        startInstallationUpdateViaIntent();

        waitForUiIdle();

        clickCancelButton();

        assertTestPackageInstalled();
    }

    @Test
    public void updateWithPackageUri_launchGrantPermission_updateButton_success() throws Exception {
        installTestPackage();

        startInstallationViaIntentWithPackageUri();

        waitForUiIdle();

        clickSettingsButton();

        toggleAllowFromSource();

        clickUpdateButton(/* checkGPPDialog= */ false);

        assertInstallSuccessDialogAndClickDoneButton();
        assertTestPackageInstalled();
    }

    @Test
    public void updateWithPackageUri_launchGrantPermission_backKey_failed() throws Exception {
        installTestPackage();

        startInstallationViaIntentWithPackageUri();

        waitForUiIdle();

        clickSettingsButton();

        toggleAllowFromSource();

        pressBack();

        assertTestPackageInstalled();
    }

    @Test
    public void updateWithPackageUri_launchGrantPermission_touchOutside_failed() throws Exception {
        installTestPackage();

        startInstallationViaIntentWithPackageUri();

        waitForUiIdle();

        clickSettingsButton();

        toggleAllowFromSource();

        touchOutside();

        assertTestPackageInstalled();
    }

    @Test
    public void updateWithPackageUri_launchGrantPermission_cancelButton_failed() throws Exception {
        installTestPackage();

        startInstallationViaIntentWithPackageUri();

        waitForUiIdle();

        clickSettingsButton();

        toggleAllowFromSource();

        clickCancelButton();

        assertTestPackageInstalled();
    }

    @Test
    public void updateWithPackageUri_launchButNoGrantPermission_failed() throws Exception {
        installTestPackage();

        startInstallationViaIntentWithPackageUri();

        waitForUiIdle();

        clickSettingsButton();

        exitAllowFromSettings();

        assertTestPackageInstalled();
    }

    @Test
    public void updateWithPackageUri_noLaunchGrantPermission_updateButton_success()
            throws Exception {
        installTestPackage();

        grantRequestInstallPackagesPermission();

        startInstallationViaIntentWithPackageUri();

        waitForUiIdle();

        clickUpdateButton(/* checkGPPDialog= */ false);

        assertInstallSuccessDialogAndClickDoneButton();
        assertTestPackageInstalled();
    }

    @Test
    public void updateWithPackageUri_noLaunchGrantPermission_backKey_failed() throws Exception {
        installTestPackage();

        grantRequestInstallPackagesPermission();

        startInstallationViaIntentWithPackageUri();

        waitForUiIdle();

        pressBack();

        assertTestPackageInstalled();
    }

    @Test
    public void updateWithPackageUri_noLaunchGrantPermission_touchOutside_failed()
            throws Exception {
        installTestPackage();

        grantRequestInstallPackagesPermission();

        startInstallationViaIntentWithPackageUri();

        waitForUiIdle();

        touchOutside();

        assertTestPackageInstalled();
    }

    @Test
    public void updateWithPackageUri_noLaunchGrantPermission_cancelButton_failed()
            throws Exception {
        installTestPackage();

        grantRequestInstallPackagesPermission();

        startInstallationViaIntentWithPackageUri();

        waitForUiIdle();

        clickCancelButton();

        assertTestPackageInstalled();
    }
}
