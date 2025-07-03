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

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
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

    private static final String SAMPLE_APK_BASE = "/data/local/tmp/cts/content/";
    private static final String EMPTY_APP_APK = SAMPLE_APK_BASE + "CtsContentEmptyTestApp.apk";
    private static final String EMPTY_APP_PACKAGE_NAME = "android.content.cts.emptytestapp";

    private final Context mContext = InstrumentationRegistry.getInstrumentation().getContext();

    private final PackageManager mPackageManager = mContext.getPackageManager();
    private final PackageInstaller mPackageInstaller = mPackageManager.getPackageInstaller();

    @Test
    public void testGetVerificationPolicy() throws Exception {
        // Test without permission
        expectThrows(SecurityException.class, mPackageInstaller::getDeveloperVerificationPolicy);
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
        // Test changing verification policy with permission. API still throws SecurityException
        // because the caller isn't the developer verifier.
        SystemUtil.runWithShellPermissionIdentity(
                () ->
                        expectThrows(
                                SecurityException.class,
                                () ->
                                        mPackageInstaller.setDeveloperVerificationPolicy(
                                                DEVELOPER_VERIFICATION_POLICY_BLOCK_FAIL_CLOSED)),
                android.Manifest.permission.DEVELOPER_VERIFICATION_AGENT);
    }

    @Test
    public void testIsAppMetadataVerifiedDefaultIsFalse()
            throws PackageManager.NameNotFoundException {
        final int userId = mContext.getUserId();
        try {
            // Use adb to install an app. By default an app's metadata is not verified, if the
            // installation didn't go through the developer verification process
            assertThat(
                            SystemUtil.runShellCommand(
                                    String.format(
                                            "pm install --user %d %s", userId, EMPTY_APP_APK)))
                    .isEqualTo("Success\n");
            final PackageInfo packageInfo =
                    mPackageManager.getPackageInfo(EMPTY_APP_PACKAGE_NAME, 0);
            assertThat(packageInfo).isNotNull();
            assertThat(packageInfo.isAppMetadataVerified()).isFalse();
        } finally {
            SystemUtil.runShellCommand(
                    String.format("pm uninstall --user %d %s", userId, EMPTY_APP_PACKAGE_NAME));
        }
    }

    @Test
    public void testGetDeveloperVerificationPolicyDelegatePackageFails() {
        // Only the developer verifier can query the policy delegate.
        expectThrows(
                SecurityException.class,
                mPackageInstaller::getDeveloperVerificationPolicyDelegatePackage);
        SystemUtil.runWithShellPermissionIdentity(
                () ->
                        expectThrows(
                                SecurityException.class,
                                mPackageInstaller::getDeveloperVerificationPolicyDelegatePackage),
                android.Manifest.permission.DEVELOPER_VERIFICATION_AGENT);
    }
}
