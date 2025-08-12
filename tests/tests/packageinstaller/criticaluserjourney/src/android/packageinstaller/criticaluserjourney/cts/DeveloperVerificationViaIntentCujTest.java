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
import static android.content.pm.PackageInstaller.DEVELOPER_VERIFICATION_POLICY_BLOCK_FAIL_CLOSED;
import static android.content.pm.PackageInstaller.DEVELOPER_VERIFICATION_POLICY_BLOCK_FAIL_OPEN;

import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import org.junit.Rule;
import org.junit.Test;

@RequiresFlagsEnabled(FLAG_VERIFICATION_SERVICE)
public class DeveloperVerificationViaIntentCujTest extends DeveloperVerificationTestBase {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Test
    public void newInstall_launchGrantPermission_policyOpen_UnknownError_userAbort_failed()
            throws Exception {
        setDeveloperVerificationResult(
                TEST_APP_PACKAGE_NAME,
                DEVELOPER_VERIFICATION_POLICY_BLOCK_FAIL_OPEN,
                RESPONSE_INCOMPLETE_UNKNOWN);

        startInstallationViaIntent();

        grantPermissionAndAbortOnDeveloperVerificationDialog(
                /* isBypassAllowed= */ true, /* isAppUpdating= */ false);

        assertTestPackageNotInstalled();
    }

    @Test
    public void newInstall_launchGrantPermission_policyOpen_NetworkError_userAbort_failed()
            throws Exception {
        setDeveloperVerificationResult(
                TEST_APP_PACKAGE_NAME,
                DEVELOPER_VERIFICATION_POLICY_BLOCK_FAIL_OPEN,
                RESPONSE_INCOMPLETE_NETWORK);

        startInstallationViaIntent();

        grantPermissionAndAbortOnDeveloperVerificationDialog(
                /* isBypassAllowed= */ true, /* isAppUpdating= */ false);

        assertTestPackageNotInstalled();
    }

    @Test
    public void newInstall_launchGrantPermission_policyOpen_VerificationFailure_userAbort_failed()
            throws Exception {
        setDeveloperVerificationResult(
                TEST_APP_PACKAGE_NAME,
                DEVELOPER_VERIFICATION_POLICY_BLOCK_FAIL_OPEN,
                RESPONSE_COMPLETE_WITH_REJECT);

        startInstallationViaIntent();
        // Actual developer verification failure cannot be bypassed
        grantPermissionAndAbortOnDeveloperVerificationDialog(
                /* isBypassAllowed= */ false, /* isAppUpdating= */ false);

        assertTestPackageNotInstalled();
    }

    @Test
    public void newInstall_launchGrantPermission_policyOpen_UnknownError_userBypass_success()
            throws Exception {
        setDeveloperVerificationResult(
                TEST_APP_PACKAGE_NAME,
                DEVELOPER_VERIFICATION_POLICY_BLOCK_FAIL_OPEN,
                RESPONSE_INCOMPLETE_UNKNOWN);

        startInstallationViaIntent();

        grantPermissionAndBypassOnDeveloperVerificationDialog(/* isAppUpdating= */ false);

        assertTestPackageInstalled();
    }

    @Test
    public void newInstall_launchGrantPermission_policyOpen_NetworkError_userBypass_success()
            throws Exception {
        setDeveloperVerificationResult(
                TEST_APP_PACKAGE_NAME,
                DEVELOPER_VERIFICATION_POLICY_BLOCK_FAIL_OPEN,
                RESPONSE_INCOMPLETE_NETWORK);

        startInstallationViaIntent();

        grantPermissionAndBypassOnDeveloperVerificationDialog(/* isAppUpdating= */ false);

        assertTestPackageInstalled();
    }

    @Test
    public void newInstall_launchGrantPermission_policyClosed_UnknownError_userAbort_failed()
            throws Exception {
        setDeveloperVerificationResult(
                TEST_APP_PACKAGE_NAME,
                DEVELOPER_VERIFICATION_POLICY_BLOCK_FAIL_CLOSED,
                RESPONSE_INCOMPLETE_UNKNOWN);

        startInstallationViaIntent();

        grantPermissionAndAbortOnDeveloperVerificationDialog(
                /* isBypassAllowed= */ false, /* isAppUpdating= */ false);

        assertTestPackageNotInstalled();
    }

    @Test
    public void newInstall_launchGrantPermission_policyClosed_NetworkError_userAbort_failed()
            throws Exception {
        setDeveloperVerificationResult(
                TEST_APP_PACKAGE_NAME,
                DEVELOPER_VERIFICATION_POLICY_BLOCK_FAIL_CLOSED,
                RESPONSE_INCOMPLETE_NETWORK);

        startInstallationViaIntent();

        grantPermissionAndAbortOnDeveloperVerificationDialog(
                /* isBypassAllowed= */ false, /* isAppUpdating= */ false);

        assertTestPackageNotInstalled();
    }

    @Test
    public void newInstall_launchGrantPermission_policyClosed_VerificationFailure_userAbort_failed()
            throws Exception {
        setDeveloperVerificationResult(
                TEST_APP_PACKAGE_NAME,
                DEVELOPER_VERIFICATION_POLICY_BLOCK_FAIL_CLOSED,
                RESPONSE_COMPLETE_WITH_REJECT);

        startInstallationViaIntent();

        grantPermissionAndAbortOnDeveloperVerificationDialog(
                /* isBypassAllowed= */ false, /* isAppUpdating= */ false);

        assertTestPackageNotInstalled();
    }

    @Test
    public void newInstall_launchGrantPermission_policyClosed_NetworkFailure_userRetry_success()
            throws Exception {
        setDeveloperVerificationResult(
                TEST_APP_PACKAGE_NAME,
                DEVELOPER_VERIFICATION_POLICY_BLOCK_FAIL_CLOSED,
                RESPONSE_INCOMPLETE_NETWORK, // retry once
                RESPONSE_INCOMPLETE_NETWORK, // retry twice
                RESPONSE_COMPLETE_WITH_PASS);

        startInstallationViaIntent();

        grantPermissionAndRetryOnDeveloperVerificationDialog(
                /* isAppUpdating= */ true, /* retryCount= */ 2);

        assertTestPackageInstalled();
    }

    @Test
    public void update_launchGrantPermission_policyOpen_UnknownError_userAbort_failed()
            throws Exception {
        installTestPackage();

        setDeveloperVerificationResult(
                TEST_APP_PACKAGE_NAME,
                DEVELOPER_VERIFICATION_POLICY_BLOCK_FAIL_OPEN,
                RESPONSE_INCOMPLETE_UNKNOWN);

        startInstallationUpdateViaIntent();

        grantPermissionAndAbortOnDeveloperVerificationDialog(
                /* isBypassAllowed= */ true, /* isAppUpdating= */ true);

        // Assert that the currently installed version is still the old version
        assertTestPackageInstalled();
    }

    @Test
    public void update_launchGrantPermission_policyOpen_NetworkError_userAbort_failed()
            throws Exception {
        installTestPackage();

        setDeveloperVerificationResult(
                TEST_APP_PACKAGE_NAME,
                DEVELOPER_VERIFICATION_POLICY_BLOCK_FAIL_OPEN,
                RESPONSE_INCOMPLETE_NETWORK);

        startInstallationUpdateViaIntent();

        grantPermissionAndAbortOnDeveloperVerificationDialog(
                /* isBypassAllowed= */ true, /* isAppUpdating= */ true);

        // Assert that the currently installed version is still the old version
        assertTestPackageInstalled();
    }

    @Test
    public void update_launchGrantPermission_policyOpen_VerificationFailure_userAbort_failed()
            throws Exception {
        installTestPackage();

        setDeveloperVerificationResult(
                TEST_APP_PACKAGE_NAME,
                DEVELOPER_VERIFICATION_POLICY_BLOCK_FAIL_OPEN,
                RESPONSE_COMPLETE_WITH_REJECT);

        startInstallationUpdateViaIntent();
        // Actual developer verification failure cannot be bypassed
        grantPermissionAndAbortOnDeveloperVerificationDialog(
                /* isBypassAllowed= */ false, /* isAppUpdating= */ true);

        // Assert that the currently installed version is still the old version
        assertTestPackageInstalled();
    }

    @Test
    public void update_launchGrantPermission_policyOpen_UnknownError_userBypass_success()
            throws Exception {
        installTestPackage();

        setDeveloperVerificationResult(
                TEST_APP_PACKAGE_NAME,
                DEVELOPER_VERIFICATION_POLICY_BLOCK_FAIL_OPEN,
                RESPONSE_INCOMPLETE_UNKNOWN);

        startInstallationUpdateViaIntent();

        grantPermissionAndBypassOnDeveloperVerificationDialog(/* isAppUpdating= */ true);

        assertTestPackageVersion2Installed();
    }

    @Test
    public void update_launchGrantPermission_policyOpen_NetworkError_userBypass_success()
            throws Exception {
        installTestPackage();

        setDeveloperVerificationResult(
                TEST_APP_PACKAGE_NAME,
                DEVELOPER_VERIFICATION_POLICY_BLOCK_FAIL_OPEN,
                RESPONSE_INCOMPLETE_NETWORK);

        startInstallationUpdateViaIntent();

        grantPermissionAndBypassOnDeveloperVerificationDialog(/* isAppUpdating= */ true);

        assertTestPackageVersion2Installed();
    }

    @Test
    public void update_launchGrantPermission_policyClosed_UnknownError_userAbort_failed()
            throws Exception {
        installTestPackage();

        setDeveloperVerificationResult(
                TEST_APP_PACKAGE_NAME,
                DEVELOPER_VERIFICATION_POLICY_BLOCK_FAIL_CLOSED,
                RESPONSE_INCOMPLETE_UNKNOWN);

        startInstallationUpdateViaIntent();

        grantPermissionAndAbortOnDeveloperVerificationDialog(
                /* isBypassAllowed= */ false, /* isAppUpdating= */ true);

        // Assert that the currently installed version is still the old version
        assertTestPackageInstalled();
    }

    @Test
    public void update_launchGrantPermission_policyClosed_NetworkError_userAbort_failed()
            throws Exception {
        installTestPackage();

        setDeveloperVerificationResult(
                TEST_APP_PACKAGE_NAME,
                DEVELOPER_VERIFICATION_POLICY_BLOCK_FAIL_CLOSED,
                RESPONSE_INCOMPLETE_NETWORK);

        startInstallationUpdateViaIntent();

        grantPermissionAndAbortOnDeveloperVerificationDialog(
                /* isBypassAllowed= */ false, /* isAppUpdating= */ true);

        // Assert that the currently installed version is still the old version
        assertTestPackageInstalled();
    }

    @Test
    public void update_launchGrantPermission_policyClosed_VerificationFailure_userAbort_failed()
            throws Exception {
        installTestPackage();

        setDeveloperVerificationResult(
                TEST_APP_PACKAGE_NAME,
                DEVELOPER_VERIFICATION_POLICY_BLOCK_FAIL_CLOSED,
                RESPONSE_COMPLETE_WITH_REJECT);

        startInstallationUpdateViaIntent();

        grantPermissionAndAbortOnDeveloperVerificationDialog(
                /* isBypassAllowed= */ false, /* isAppUpdating= */ true);

        // Assert that the currently installed version is still the old version
        assertTestPackageInstalled();
    }

    @Test
    public void update_launchGrantPermission_policyClosed_NetworkFailure_userRetry_success()
            throws Exception {
        installTestPackage();

        setDeveloperVerificationResult(
                TEST_APP_PACKAGE_NAME,
                DEVELOPER_VERIFICATION_POLICY_BLOCK_FAIL_CLOSED,
                RESPONSE_INCOMPLETE_NETWORK, // retry once
                RESPONSE_INCOMPLETE_NETWORK, // retry twice
                RESPONSE_COMPLETE_WITH_PASS);

        startInstallationUpdateViaIntent();

        grantPermissionAndRetryOnDeveloperVerificationDialog(
                /* isAppUpdating= */ true, /* retryCount= */ 2);

        assertTestPackageVersion2Installed();
    }
}
