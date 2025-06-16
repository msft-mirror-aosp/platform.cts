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

package android.content.pm.cts;

import static android.content.pm.Flags.FLAG_VERIFICATION_SERVICE;
import static android.content.pm.PackageInstaller.DEVELOPER_VERIFICATION_POLICY_BLOCK_FAIL_CLOSED;
import static android.content.pm.PackageInstaller.DEVELOPER_VERIFICATION_POLICY_NONE;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.expectThrows;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageInstaller;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.AppModeNonSdkSandbox;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.SystemUtil;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@AppModeFull
@AppModeNonSdkSandbox
@RequiresFlagsEnabled(FLAG_VERIFICATION_SERVICE)
public class DeveloperVerifierServiceTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private final Context mContext = InstrumentationRegistry.getInstrumentation().getContext();

    private final PackageInstaller mPackageInstaller =
            mContext.getPackageManager().getPackageInstaller();

    @Test
    public void testGetVerificationPolicy() throws Exception {
        // Test without permission
        expectThrows(
                SecurityException.class, () -> mPackageInstaller.getDeveloperVerificationPolicy());
        // Test with permission
        SystemUtil.runWithShellPermissionIdentity(
                () -> {
                    final int defaultPolicy = mPackageInstaller.getDeveloperVerificationPolicy();
                    assertThat(defaultPolicy).isAtLeast(DEVELOPER_VERIFICATION_POLICY_NONE);
                    assertThat(defaultPolicy)
                            .isAtMost(DEVELOPER_VERIFICATION_POLICY_BLOCK_FAIL_CLOSED);
                },
                android.Manifest.permission.DEVELOPER_VERIFICATION_AGENT);
    }

    @Test
    public void testSetVerificationPolicyFails() throws Exception {
        // Anyone can check the system verifier package name as it's not protected by any permission
        final ComponentName verifierComponentName =
                mPackageInstaller.getDeveloperVerificationServiceProvider();
        final String verifierPackageName =
                verifierComponentName == null ? null : verifierComponentName.getPackageName();
        // Test changing verification policy without permission
        expectThrows(
                SecurityException.class,
                () ->
                        mPackageInstaller.setDeveloperVerificationPolicy(
                                DEVELOPER_VERIFICATION_POLICY_BLOCK_FAIL_CLOSED));
        // Test changing verification policy with permission
        SystemUtil.runWithShellPermissionIdentity(
                () -> {
                    if (verifierPackageName == null) {
                        // If there is no system verifier, the API returns false
                        assertThat(
                                        mPackageInstaller.setDeveloperVerificationPolicy(
                                                DEVELOPER_VERIFICATION_POLICY_BLOCK_FAIL_CLOSED))
                                .isFalse();
                    } else {
                        // Otherwise, the API throws IllegalStateException because the caller isn't
                        // the system verifier.
                        expectThrows(
                                IllegalStateException.class,
                                () ->
                                        mPackageInstaller.setDeveloperVerificationPolicy(
                                                DEVELOPER_VERIFICATION_POLICY_BLOCK_FAIL_CLOSED));
                    }
                },
                android.Manifest.permission.DEVELOPER_VERIFICATION_AGENT);
        // If there is no system verifier, even Shell cannot change the policy; otherwise, Shell
        // cannot set the policy to an invalid number. Either way, expect the command to fail.
        final int invalidPolicy = 100;
        assertThat(runSetDefaultVerificationPolicy(invalidPolicy)).startsWith("Failure");
    }

    private static int getDefaultVerificationPolicy() {
        String policyStr =
                SystemUtil.runShellCommand(
                                "pm get-developer-verification-policy --user "
                                        + ActivityManager.getCurrentUser())
                        .trim();
        return Integer.parseInt(policyStr);
    }

    private static void setDefaultVerificationPolicy(
            @PackageInstaller.DeveloperVerificationPolicy int policy) {
        runSetDefaultVerificationPolicy(policy);
        assertThat(getDefaultVerificationPolicy()).isEqualTo(policy);
    }

    private static String runSetDefaultVerificationPolicy(
            @PackageInstaller.DeveloperVerificationPolicy int policy) {
        return SystemUtil.runShellCommand(
                "pm set-developer-verification-policy --user "
                        + ActivityManager.getCurrentUser()
                        + " "
                        + policy);
    }
}
