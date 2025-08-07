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

import static org.junit.Assume.assumeTrue;

import com.android.compatibility.common.util.SystemUtil;

import org.junit.Before;

public class DeveloperVerificationTestBase extends InstallationTestBase {
    static final int RESPONSE_INCOMPLETE_UNKNOWN = 3;
    static final int RESPONSE_INCOMPLETE_NETWORK = 4;
    static final int RESPONSE_COMPLETE_WITH_REJECT = 2;

    @Before
    @Override
    public void setup() throws Exception {
        super.setup();
        // Developer verification CUJs are only available on Pia V2
        assumeTrue(sUsePiaV2);
    }

    void grantPermissionAndAbortOnDeveloperVerificationDialog(
            boolean assertBypassAllowed, boolean isAppUpdating) throws Exception {
        waitForUiIdle();

        clickSettingsButton();

        toggleToGrantRequestInstallPackagesPermission();

        if (isAppUpdating) {
            assertTestAppUpdateDialog();
        } else {
            assertTestAppInstallDialog();
        }

        if (isAppUpdating) {
            clickUpdateButton(
                    /* checkInstallingDialog= */ false,
                    /* isUpdatedViaPackageUri= */ false,
                    /* expectingDeveloperVerificationDialog= */ true);
        } else {
            clickInstallButton(
                    /* checkInstallingDialog= */ true,
                    /* expectingDeveloperVerificationDialog= */ true);
        }

        assertDeveloperVerificationUserConfirmationDialog(assertBypassAllowed, isAppUpdating);

        // OK means user aborts the developer verification. Expect the installation to fail.
        clickOkButton();

        // Expecting the App Not Installed dialog in the end
        assertAppNotInstalledDialog();
        clickCloseButton();
    }

    void grantPermissionAndBypassOnDeveloperVerificationDialog(boolean isAppUpdating)
            throws Exception {
        waitForUiIdle();

        clickSettingsButton();

        toggleToGrantRequestInstallPackagesPermission();

        if (isAppUpdating) {
            assertTestAppUpdateDialog();
        } else {
            assertTestAppInstallDialog();
        }

        if (isAppUpdating) {
            clickUpdateButton(
                    /* checkInstallingDialog= */ false,
                    /* isUpdatedViaPackageUri= */ false,
                    /* expectingDeveloperVerificationDialog= */ true);
        } else {
            clickInstallButton(
                    /* checkInstallingDialog= */ true,
                    /* expectingDeveloperVerificationDialog= */ true);
        }

        assertDeveloperVerificationUserConfirmationDialog(
                /* assertBypassAllowed= */ true, isAppUpdating);

        clickInstallWithoutVerifyingButton(isAppUpdating);
    }

    static void setDeveloperVerificationResult(String packageToInstall, int policy, int result) {
        SystemUtil.runShellCommand(
                String.format(
                        "pm set-developer-verification-result %s %d %d",
                        packageToInstall, policy, result));
    }
}
