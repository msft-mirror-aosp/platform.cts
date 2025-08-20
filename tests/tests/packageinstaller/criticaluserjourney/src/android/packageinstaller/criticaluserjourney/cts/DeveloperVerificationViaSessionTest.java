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
package android.packageinstaller.criticaluserjourney.cts;

import static android.content.pm.Flags.FLAG_VERIFICATION_SERVICE;
import static android.content.pm.PackageInstaller.DEVELOPER_VERIFICATION_POLICY_BLOCK_FAIL_OPEN;

import android.platform.test.annotations.RequiresFlagsEnabled;

import org.junit.Test;

@RequiresFlagsEnabled(FLAG_VERIFICATION_SERVICE)
public class DeveloperVerificationViaSessionTest extends DeveloperVerificationTestBase {
    // In a multi-package installation, we cannot control the sequence of which app will be verified
    // first, so we don't check for app name in the dialog titles.
    @Test
    public void newInstall_launchGrantPermission_multiPackage_userBypass_success()
            throws Exception {
        setDeveloperVerificationResult(
                TEST_APP_PACKAGE_NAME,
                DEVELOPER_VERIFICATION_POLICY_BLOCK_FAIL_OPEN,
                RESPONSE_INCOMPLETE_NETWORK);
        setDeveloperVerificationResult(
                EMPTY_TEST_APP_PACKAGE_NAME,
                DEVELOPER_VERIFICATION_POLICY_BLOCK_FAIL_OPEN,
                RESPONSE_INCOMPLETE_UNKNOWN);

        startMultiPackageInstallViaPackageInstallerSession(/* isUpdate= */ false);

        clickSettingsButton();

        toggleToGrantRequestInstallPackagesPermission();

        // Confirm install for first package
        clickAndWaitForNewWindow(findPackageInstallerObject(BUTTON_INSTALL_LABEL));

        // Confirm install for second package
        clickAndWaitForNewWindow(findPackageInstallerObject(BUTTON_INSTALL_LABEL));

        // Developer verification dialog for the first package
        assertDeveloperVerificationUserConfirmationDialog(
                /* isBypassAllowed= */ true, /* isAppUpdating= */ false);
        clickInstallWithoutVerifyingButton(
                /* isAppUpdating= */ false, /* expectingMoreDialogs= */ true);

        // Developer verification dialog for the second package
        assertDeveloperVerificationUserConfirmationDialog(
                /* isBypassAllowed= */ true, /* isAppUpdating= */ false);
        clickInstallWithoutVerifyingButton(
                /* isAppUpdating= */ false, /* expectingMoreDialogs= */ false);

        assertTestPackageInstalled();
        assertPackageInstalled(EMPTY_TEST_APP_PACKAGE_NAME);
    }

    @Test
    public void update_launchGrantPermission_multiPackage_userBypass_success() throws Exception {
        installTestPackage();
        installEmptyTestPackage();

        setDeveloperVerificationResult(
                TEST_APP_PACKAGE_NAME,
                DEVELOPER_VERIFICATION_POLICY_BLOCK_FAIL_OPEN,
                RESPONSE_INCOMPLETE_NETWORK);
        setDeveloperVerificationResult(
                EMPTY_TEST_APP_PACKAGE_NAME,
                DEVELOPER_VERIFICATION_POLICY_BLOCK_FAIL_OPEN,
                RESPONSE_INCOMPLETE_UNKNOWN);

        startMultiPackageInstallViaPackageInstallerSession(/* isUpdate= */ true);

        clickSettingsButton();

        toggleToGrantRequestInstallPackagesPermission();

        // Confirm update for first package
        clickAndWaitForNewWindow(findPackageInstallerObject(BUTTON_UPDATE_LABEL));

        // Confirm update for second package
        clickAndWaitForNewWindow(findPackageInstallerObject(BUTTON_UPDATE_LABEL));

        // Developer verification dialog for the first package
        assertDeveloperVerificationUserConfirmationDialog(
                /* isBypassAllowed= */ true, /* isAppUpdating= */ true);
        clickInstallWithoutVerifyingButton(
                /* isAppUpdating= */ true, /* expectingMoreDialogs= */ true);

        // Developer verification dialog for the second package
        assertDeveloperVerificationUserConfirmationDialog(
                /* isBypassAllowed= */ true, /* isAppUpdating= */ true);
        clickInstallWithoutVerifyingButton(
                /* isAppUpdating= */ true, /* expectingMoreDialogs= */ false);

        assertTestPackageVersion2Installed();
        assertPackageVersion2Installed(EMPTY_TEST_APP_PACKAGE_NAME);
    }
}
