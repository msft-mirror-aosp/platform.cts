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
import static android.content.pm.PackageInstaller.VERIFICATION_POLICY_BLOCK_FAIL_CLOSED;
import static android.content.pm.PackageInstaller.VERIFICATION_POLICY_BLOCK_FAIL_OPEN;
import static android.content.pm.PackageInstaller.VERIFICATION_POLICY_BLOCK_FAIL_WARN;
import static android.content.pm.PackageInstaller.VERIFICATION_POLICY_NONE;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;
import static org.testng.Assert.expectThrows;

import android.Manifest;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IIntentReceiver;
import android.content.IIntentSender;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.SharedLibraryInfo;
import android.content.pm.SigningInfo;
import android.content.pm.verify.pkg.VerificationSession;
import android.os.Build;
import android.os.Bundle;
import android.os.ConditionVariable;
import android.os.IBinder;
import android.os.RemoteException;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.AppModeNonSdkSandbox;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.DeviceConfigStateManager;
import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
@AppModeFull
@AppModeNonSdkSandbox
public class VerifierServiceTest {
    private static final String VERIFIER_APP_PACKAGE_NAME = "com.android.cts.testverifierapp";
    private static final String SAMPLE_APK_BASE = "/data/local/tmp/cts/content/";
    private static final String VERIFIER_APP_APK_PATH =
            SAMPLE_APK_BASE + "CtsTestVerifierApp.apk";
    private static final String VERIFIER_APP_CRASH_APK_PATH =
            SAMPLE_APK_BASE + "CtsTestVerifierAppCrash.apk";
    private static final String VERIFIER_APP_REJECT_APK_PATH =
            SAMPLE_APK_BASE + "CtsTestVerifierAppReject.apk";
    private static final String VERIFIER_APP_REJECT_WITH_POLICY_OVERRIDE_APK_PATH =
            SAMPLE_APK_BASE + "CtsTestVerifierAppRejectWithPolicyOverride.apk";
    private static final String VERIFIER_APP_TIMEOUT_APK_PATH =
            SAMPLE_APK_BASE + "CtsTestVerifierAppTimeout.apk";
    private static final String VERIFIER_APP_INCOMPLETE_UNKNOWN_APK_PATH =
            SAMPLE_APK_BASE + "CtsTestVerifierAppIncompleteUnknown.apk";
    private static final String VERIFIER_APP_INCOMPLETE_NETWORK_UNAVAILABLE_APK_PATH =
            SAMPLE_APK_BASE + "CtsTestVerifierAppIncompleteNetworkUnavailable.apk";
    private static final String EMPTY_APP_APK = SAMPLE_APK_BASE
            + "CtsContentEmptyTestApp.apk";
    private static final String EMPTY_APP_APK_DECLARING_LIBRARY = SAMPLE_APK_BASE
            + "CtsContentDeclaringLibrary.apk";
    private static final String EMPTY_APP_APK_DECLARING_SDK_LIBRARY = SAMPLE_APK_BASE
            + "CtsContentDeclaringSdkLibrary.apk";
    private static final String EMPTY_APP_APK_DECLARING_STATIC_LIBRARY = SAMPLE_APK_BASE
            + "CtsContentDeclaringStaticLibrary.apk";
    private static final String EMPTY_APP_PACKAGE_NAME = "android.content.cts.emptytestapp";
    private static final String ACTION_SERVICE_CONNECTED =
            "android.content.pm.cts.verify.SERVICE_CONNECTED";
    private static final String ACTION_NAME_RECEIVED =
            "android.content.pm.cts.verify.NAME_RECEIVED";
    private static final String ACTION_CANCELLED_RECEIVED =
            "android.content.pm.cts.verify.CANCELLED_RECEIVED";
    private static final String ACTION_REQUEST_RECEIVED =
            "android.content.pm.cts.verify.REQUEST_RECEIVED";
    private static final String REJECT_MESSAGE = "You shall not pass!";
    private static final String NAMESPACE_PACKAGE_MANAGER_SERVICE = "package_manager_service";
    private static final String PROPERTY_VERIFICATION_REQUEST_TIMEOUT_MILLIS =
            "verification_request_timeout_millis";
    private static final String PROPERTY_VERIFIER_CONNECTION_TIMEOUT_MILLIS =
            "verifier_connection_timeout_millis";
    static final String EXTRA_VERIFICATION_SESSION = "android.content.pm.cts.verify.session";


    private static final long DEFAULT_TIMEOUT_MS = isLowRamDevice()
            ? TimeUnit.SECONDS.toMillis(60) : TimeUnit.SECONDS.toMillis(15);
    private final ConditionVariable mServiceConnectedLatch = new ConditionVariable();
    private CompletableFuture<VerificationSession> mVerificationSession;
    private CompletableFuture<String> mNameAvailableLatch;
    private CompletableFuture<String> mVerificationCancelledLatch;

    private final Context mContext = InstrumentationRegistry.getInstrumentation().getContext();
    private final PackageInstaller mPackageInstaller =
            mContext.getPackageManager().getPackageInstaller();

    private final TestVerifierBroadcastReceiver mBroadcastReceiver =
            new TestVerifierBroadcastReceiver();
    private @PackageInstaller.VerificationPolicy int mDefaultPolicy;
    private String mRequestTimeoutMillis;
    private String mConnectionTimeoutMillis;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setUp() {
        // The following test only works if the device has an ENG build and the test app is signed
        // with the platform signature, which may not always be the case.
        assumeTrue(Build.IS_ENG);
        // First install the test verifier and so that it can be selected as the default verifier
        // service
        // TODO: disable the previously connected verifier in the system
        installPackageWithAdb(VERIFIER_APP_APK_PATH);

        // Register to listen for the broadcasts from the test verifier service
        final IntentFilter intentFilter = getIntentFilter();
        mContext.registerReceiver(mBroadcastReceiver, intentFilter, Context.RECEIVER_EXPORTED);

        // Remember the global default policy and restore it later. Some tests might change it.
        mDefaultPolicy = getDefaultVerificationPolicy();
        // Remember the timeout values and restore them later.
        mRequestTimeoutMillis =
                getTimeoutValueFromDeviceConfig(PROPERTY_VERIFICATION_REQUEST_TIMEOUT_MILLIS);
        mConnectionTimeoutMillis =
                getTimeoutValueFromDeviceConfig(PROPERTY_VERIFIER_CONNECTION_TIMEOUT_MILLIS);
        // Change the timeouts to shorter durations to reduce the test runtime.
        setTimeoutValueInDeviceConfig(PROPERTY_VERIFICATION_REQUEST_TIMEOUT_MILLIS,
                String.valueOf(2000)); // Wait for 2 seconds before a request times out
        setTimeoutValueInDeviceConfig(PROPERTY_VERIFIER_CONNECTION_TIMEOUT_MILLIS,
                String.valueOf(1000)); // Wait for 1 second for the connection to establish

        // Reset the latches
        mServiceConnectedLatch.close();
        mVerificationSession = new CompletableFuture<>();
        mNameAvailableLatch = new CompletableFuture<>();
        mVerificationCancelledLatch = new CompletableFuture<>();
    }

    private static IntentFilter getIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_SERVICE_CONNECTED);
        intentFilter.addAction(ACTION_NAME_RECEIVED);
        intentFilter.addAction(ACTION_CANCELLED_RECEIVED);
        intentFilter.addAction(ACTION_REQUEST_RECEIVED);
        return intentFilter;
    }

    @After
    public void tearDown() {
        uninstallPackage(VERIFIER_APP_PACKAGE_NAME);
        uninstallPackage(EMPTY_APP_PACKAGE_NAME);
        try {
            mContext.unregisterReceiver(mBroadcastReceiver);
        } catch (IllegalArgumentException e) {
            // ignored
        }
        SystemUtil.runWithShellPermissionIdentity(
                () -> mPackageInstaller.setVerificationPolicy(mDefaultPolicy),
                android.Manifest.permission.VERIFICATION_AGENT
        );
        setTimeoutValueInDeviceConfig(PROPERTY_VERIFICATION_REQUEST_TIMEOUT_MILLIS,
                mRequestTimeoutMillis);
        setTimeoutValueInDeviceConfig(PROPERTY_VERIFIER_CONNECTION_TIMEOUT_MILLIS,
                mConnectionTimeoutMillis);
    }

    private int getDefaultVerificationPolicy() {
        return SystemUtil.runWithShellPermissionIdentity(
                () -> mPackageInstaller.getVerificationPolicy(),
                android.Manifest.permission.VERIFICATION_AGENT
        );
    }

    private void setDefaultVerificationPolicy(
            @PackageInstaller.VerificationPolicy int policy) {
        SystemUtil.runWithShellPermissionIdentity(
                () -> {
                    assertThat(mPackageInstaller.setVerificationPolicy(policy)).isTrue();
                    assertThat(mPackageInstaller.getVerificationPolicy()).isEqualTo(policy);
                },
                android.Manifest.permission.VERIFICATION_AGENT
        );
    }

    @Test
    @RequiresFlagsEnabled(FLAG_VERIFICATION_SERVICE)
    public void testInstallSuccess() throws Exception {
        assertInstallPackageWithSession(EMPTY_APP_APK, EMPTY_APP_PACKAGE_NAME);
        assertThat(mServiceConnectedLatch.block(DEFAULT_TIMEOUT_MS)).isTrue();
        assertThat(mNameAvailableLatch.get(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)).isEqualTo(
                EMPTY_APP_PACKAGE_NAME);
        VerificationSession session =
                mVerificationSession.get(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        assertThat(session.getId()).isGreaterThan(0);
        assertThat(session.getPackageName()).isEqualTo(EMPTY_APP_PACKAGE_NAME);
        assertThat(session.getInstallSessionId()).isGreaterThan(0);
        assertThat(session.getStagedPackageUri().getScheme()).isEqualTo("file");
        assertThat(session.getStagedPackageUri().toString()).contains("vmdl");
        SigningInfo sessionSigningInfo = session.getSigningInfo();
        assertThat(sessionSigningInfo).isNotNull();
        final PackageInfo packageInfoExpected = mContext.getPackageManager().getPackageInfo(
                EMPTY_APP_PACKAGE_NAME, PackageManager.GET_SIGNING_CERTIFICATES);
        assertThat(packageInfoExpected).isNotNull();
        final SigningInfo expectedSigningInfo = packageInfoExpected.signingInfo;
        assertThat(expectedSigningInfo.getApkContentsSigners()).isEqualTo(
                sessionSigningInfo.getApkContentsSigners());
    }

    @Test
    @RequiresFlagsEnabled(FLAG_VERIFICATION_SERVICE)
    public void testInstallSuccessWithDynamicLib() throws Exception {
        assertInstallPackageWithSession(EMPTY_APP_APK_DECLARING_LIBRARY, EMPTY_APP_PACKAGE_NAME);
        VerificationSession session =
                mVerificationSession.get(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        List<SharedLibraryInfo> libs = session.getDeclaredLibraries();
        assertThat(libs).hasSize(1);
        SharedLibraryInfo lib = libs.get(0);
        assertThat(lib.getName()).isEqualTo(EMPTY_APP_PACKAGE_NAME);
        assertThat(lib.getType()).isEqualTo(SharedLibraryInfo.TYPE_DYNAMIC);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_VERIFICATION_SERVICE)
    public void testInstallSuccessWithSdkLib() throws Exception {
        assertInstallPackageWithSession(
                EMPTY_APP_APK_DECLARING_SDK_LIBRARY, EMPTY_APP_PACKAGE_NAME);
        VerificationSession session =
                mVerificationSession.get(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        List<SharedLibraryInfo> libs = session.getDeclaredLibraries();
        assertThat(libs).hasSize(1);
        SharedLibraryInfo lib = libs.get(0);
        assertThat(lib.getName()).isEqualTo(EMPTY_APP_PACKAGE_NAME);
        assertThat(lib.getType()).isEqualTo(SharedLibraryInfo.TYPE_SDK_PACKAGE);
        assertThat(lib.getLongVersion()).isEqualTo(1);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_VERIFICATION_SERVICE)
    public void testInstallSuccessWithStaticLib() throws Exception {
        assertInstallPackageWithSession(
                EMPTY_APP_APK_DECLARING_STATIC_LIBRARY, EMPTY_APP_PACKAGE_NAME);
        try {
            VerificationSession session =
                    mVerificationSession.get(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            List<SharedLibraryInfo> libs = session.getDeclaredLibraries();
            assertThat(libs).hasSize(1);
            SharedLibraryInfo lib = libs.get(0);
            assertThat(lib.getName()).isEqualTo(EMPTY_APP_PACKAGE_NAME);
            assertThat(lib.getType()).isEqualTo(SharedLibraryInfo.TYPE_STATIC);
            assertThat(lib.getLongVersion()).isEqualTo(1);
        } finally {
            // The package name of the static library has the version number as a suffix
            uninstallPackage(EMPTY_APP_PACKAGE_NAME + "_1");
        }
    }

    @Test
    @RequiresFlagsEnabled(FLAG_VERIFICATION_SERVICE)
    public void testSessionAbandon() throws Exception {
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(EMPTY_APP_PACKAGE_NAME);
        final int sessionId = mPackageInstaller.createSession(params);
        assertThat(sessionId).isGreaterThan(0);
        assertThat(mNameAvailableLatch.get(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)).isEqualTo(
                EMPTY_APP_PACKAGE_NAME);
        mPackageInstaller.abandonSession(sessionId);
        assertThat(mVerificationCancelledLatch.get(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS))
                .isEqualTo(EMPTY_APP_PACKAGE_NAME);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_VERIFICATION_SERVICE)
    public void testVerifierCrashWithPolicyNone() throws Exception {
        setDefaultVerificationPolicy(VERIFICATION_POLICY_NONE);
        // Install the crash version of the verifier
        installPackageWithAdb(VERIFIER_APP_CRASH_APK_PATH);
        // Install a test app and expecting the install to pass
        assertInstallPackageWithSession(EMPTY_APP_APK, EMPTY_APP_PACKAGE_NAME);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_VERIFICATION_SERVICE)
    public void testVerifierCrashWithPolicyOpen() throws Exception {
        setDefaultVerificationPolicy(VERIFICATION_POLICY_BLOCK_FAIL_OPEN);
        assertInstallPackageWithSession(EMPTY_APP_APK, EMPTY_APP_PACKAGE_NAME);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_VERIFICATION_SERVICE)
    public void testVerifierCrashWithPolicyWarning() throws Exception {
        setDefaultVerificationPolicy(VERIFICATION_POLICY_BLOCK_FAIL_WARN);
        assertInstallPackageWithSession(EMPTY_APP_APK, EMPTY_APP_PACKAGE_NAME);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_VERIFICATION_SERVICE)
    public void testVerifierCrashWithPolicyClosed() throws Exception {
        setDefaultVerificationPolicy(VERIFICATION_POLICY_BLOCK_FAIL_CLOSED);
        // Install the crash version of the verifier
        installPackageWithAdb(VERIFIER_APP_CRASH_APK_PATH);
        // Install a test app and expecting the install to fail
        assertInstallPackageWithSession(EMPTY_APP_APK, EMPTY_APP_PACKAGE_NAME,
                Optional.of(PackageInstaller.STATUS_FAILURE_ABORTED),
                Optional.of(PackageInstaller.VERIFICATION_FAILED_REASON_UNKNOWN),
                Optional.of("INSTALL_FAILED_VERIFICATION_FAILURE:"
                        + " A verifier agent is available on device but cannot be connected."));
    }

    @Test
    @RequiresFlagsEnabled(FLAG_VERIFICATION_SERVICE)
    public void testVerifierRejectWithPolicyNone() throws Exception {
        setDefaultVerificationPolicy(VERIFICATION_POLICY_NONE);
        // Install the reject version of the verifier
        installPackageWithAdb(VERIFIER_APP_REJECT_APK_PATH);
        // Install a test app and expecting the install to pass
        assertInstallPackageWithSession(EMPTY_APP_APK, EMPTY_APP_PACKAGE_NAME);
        // Verify the policy received in the session
        assertThat(mVerificationSession.get(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .getVerificationPolicy()).isEqualTo(VERIFICATION_POLICY_NONE);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_VERIFICATION_SERVICE)
    public void testVerifierRejectWithPolicyOpen() throws Exception {
        setDefaultVerificationPolicy(VERIFICATION_POLICY_BLOCK_FAIL_OPEN);
        installPackageWithAdb(VERIFIER_APP_REJECT_APK_PATH);
        // Install a test app and expecting the install to fail
        assertInstallPackageWithSession(EMPTY_APP_APK, EMPTY_APP_PACKAGE_NAME,
                Optional.of(PackageInstaller.STATUS_FAILURE_ABORTED),
                Optional.of(PackageInstaller.VERIFICATION_FAILED_REASON_PACKAGE_BLOCKED),
                Optional.of("INSTALL_FAILED_VERIFICATION_FAILURE:"
                        + " Verifier rejected the installation with message: " + REJECT_MESSAGE));
        assertThat(mVerificationSession.get(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .getVerificationPolicy()).isEqualTo(VERIFICATION_POLICY_BLOCK_FAIL_OPEN);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_VERIFICATION_SERVICE)
    public void testVerifierRejectWithPolicyWarning() throws Exception {
        setDefaultVerificationPolicy(VERIFICATION_POLICY_BLOCK_FAIL_WARN);
        installPackageWithAdb(VERIFIER_APP_REJECT_APK_PATH);
        assertInstallPackageWithSession(EMPTY_APP_APK, EMPTY_APP_PACKAGE_NAME,
                Optional.of(PackageInstaller.STATUS_FAILURE_ABORTED),
                Optional.of(PackageInstaller.VERIFICATION_FAILED_REASON_PACKAGE_BLOCKED),
                Optional.of("INSTALL_FAILED_VERIFICATION_FAILURE:"
                        + " Verifier rejected the installation with message: " + REJECT_MESSAGE));
        assertThat(mVerificationSession.get(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .getVerificationPolicy()).isEqualTo(VERIFICATION_POLICY_BLOCK_FAIL_WARN);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_VERIFICATION_SERVICE)
    public void testVerifierRejectPolicyClosed() throws Exception {
        setDefaultVerificationPolicy(VERIFICATION_POLICY_BLOCK_FAIL_CLOSED);
        installPackageWithAdb(VERIFIER_APP_REJECT_APK_PATH);
        assertInstallPackageWithSession(EMPTY_APP_APK, EMPTY_APP_PACKAGE_NAME,
                Optional.of(PackageInstaller.STATUS_FAILURE_ABORTED),
                Optional.of(PackageInstaller.VERIFICATION_FAILED_REASON_PACKAGE_BLOCKED),
                Optional.of("INSTALL_FAILED_VERIFICATION_FAILURE:"
                        + " Verifier rejected the installation with message: " + REJECT_MESSAGE));
        assertThat(mVerificationSession.get(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .getVerificationPolicy()).isEqualTo(VERIFICATION_POLICY_BLOCK_FAIL_CLOSED);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_VERIFICATION_SERVICE)
    public void testVerifierTimeoutPolicyNone() throws Exception {
        setDefaultVerificationPolicy(VERIFICATION_POLICY_NONE);
        installPackageWithAdb(VERIFIER_APP_TIMEOUT_APK_PATH);
        assertInstallPackageWithSession(EMPTY_APP_APK, EMPTY_APP_PACKAGE_NAME);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_VERIFICATION_SERVICE)
    public void testVerifierTimeoutPolicyOpen() throws Exception {
        setDefaultVerificationPolicy(VERIFICATION_POLICY_BLOCK_FAIL_OPEN);
        installPackageWithAdb(VERIFIER_APP_TIMEOUT_APK_PATH);
        assertInstallPackageWithSession(EMPTY_APP_APK, EMPTY_APP_PACKAGE_NAME);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_VERIFICATION_SERVICE)
    public void testVerifierTimeoutPolicyWarning() throws Exception {
        setDefaultVerificationPolicy(VERIFICATION_POLICY_BLOCK_FAIL_WARN);
        installPackageWithAdb(VERIFIER_APP_TIMEOUT_APK_PATH);
        assertInstallPackageWithSession(EMPTY_APP_APK, EMPTY_APP_PACKAGE_NAME);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_VERIFICATION_SERVICE)
    public void testVerifierTimeoutPolicyClosed() throws Exception {
        setDefaultVerificationPolicy(VERIFICATION_POLICY_BLOCK_FAIL_CLOSED);
        installPackageWithAdb(VERIFIER_APP_TIMEOUT_APK_PATH);
        assertInstallPackageWithSession(EMPTY_APP_APK, EMPTY_APP_PACKAGE_NAME,
                Optional.of(PackageInstaller.STATUS_FAILURE_ABORTED),
                Optional.of(PackageInstaller.VERIFICATION_FAILED_REASON_UNKNOWN),
                Optional.of("INSTALL_FAILED_VERIFICATION_FAILURE:"
                        + " Verification timed out;"
                        + " missing a response from the verifier within the time limit"));
    }

    @Test
    @RequiresFlagsEnabled(FLAG_VERIFICATION_SERVICE)
    public void testVerifierIncompleteUnknownPolicyNone() throws Exception {
        setDefaultVerificationPolicy(VERIFICATION_POLICY_NONE);
        // Install the incomplete-unknown version of the verifier
        installPackageWithAdb(VERIFIER_APP_INCOMPLETE_UNKNOWN_APK_PATH);
        // Install a test app and expecting the install to pass
        assertInstallPackageWithSession(EMPTY_APP_APK, EMPTY_APP_PACKAGE_NAME);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_VERIFICATION_SERVICE)
    public void testVerifierIncompleteUnknownPolicyOpen() throws Exception {
        setDefaultVerificationPolicy(VERIFICATION_POLICY_BLOCK_FAIL_OPEN);
        installPackageWithAdb(VERIFIER_APP_INCOMPLETE_UNKNOWN_APK_PATH);
        assertInstallPackageWithSession(EMPTY_APP_APK, EMPTY_APP_PACKAGE_NAME);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_VERIFICATION_SERVICE)
    public void testVerifierIncompleteUnknownPolicyWarning() throws Exception {
        setDefaultVerificationPolicy(VERIFICATION_POLICY_BLOCK_FAIL_WARN);
        installPackageWithAdb(VERIFIER_APP_INCOMPLETE_UNKNOWN_APK_PATH);
        assertInstallPackageWithSession(EMPTY_APP_APK, EMPTY_APP_PACKAGE_NAME);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_VERIFICATION_SERVICE)
    public void testVerifierIncompleteUnknownPolicyClosed() throws Exception {
        setDefaultVerificationPolicy(VERIFICATION_POLICY_BLOCK_FAIL_CLOSED);
        installPackageWithAdb(VERIFIER_APP_INCOMPLETE_UNKNOWN_APK_PATH);
        // Install a test app and expecting the install to fail
        assertInstallPackageWithSession(EMPTY_APP_APK, EMPTY_APP_PACKAGE_NAME,
                Optional.of(PackageInstaller.STATUS_FAILURE_ABORTED),
                Optional.of(PackageInstaller.VERIFICATION_FAILED_REASON_UNKNOWN),
                Optional.of("INSTALL_FAILED_VERIFICATION_FAILURE:"
                        + " Verification cannot be completed because of unknown reasons."));
    }

    @Test
    @RequiresFlagsEnabled(FLAG_VERIFICATION_SERVICE)
    public void testVerifierIncompleteNetworkUnavailablePolicyNone() throws Exception {
        setDefaultVerificationPolicy(VERIFICATION_POLICY_NONE);
        // Install the incomplete-unknown version of the verifier
        installPackageWithAdb(VERIFIER_APP_INCOMPLETE_NETWORK_UNAVAILABLE_APK_PATH);
        // Install a test app and expecting the install to pass
        assertInstallPackageWithSession(EMPTY_APP_APK, EMPTY_APP_PACKAGE_NAME);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_VERIFICATION_SERVICE)
    public void testVerifierIncompleteNetworkUnavailablePolicyOpen() throws Exception {
        setDefaultVerificationPolicy(VERIFICATION_POLICY_BLOCK_FAIL_OPEN);
        installPackageWithAdb(VERIFIER_APP_INCOMPLETE_NETWORK_UNAVAILABLE_APK_PATH);
        assertInstallPackageWithSession(EMPTY_APP_APK, EMPTY_APP_PACKAGE_NAME);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_VERIFICATION_SERVICE)
    public void testVerifierIncompleteNetworkUnavailablePolicyWarning() throws Exception {
        setDefaultVerificationPolicy(VERIFICATION_POLICY_BLOCK_FAIL_WARN);
        installPackageWithAdb(VERIFIER_APP_INCOMPLETE_NETWORK_UNAVAILABLE_APK_PATH);
        assertInstallPackageWithSession(EMPTY_APP_APK, EMPTY_APP_PACKAGE_NAME);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_VERIFICATION_SERVICE)
    public void testVerifierIncompleteNetworkUnavailablePolicyClosed() throws Exception {
        setDefaultVerificationPolicy(VERIFICATION_POLICY_BLOCK_FAIL_CLOSED);
        installPackageWithAdb(VERIFIER_APP_INCOMPLETE_NETWORK_UNAVAILABLE_APK_PATH);
        // Install a test app and expecting the install to fail
        assertInstallPackageWithSession(EMPTY_APP_APK, EMPTY_APP_PACKAGE_NAME,
                Optional.of(PackageInstaller.STATUS_FAILURE_ABORTED),
                Optional.of(PackageInstaller.VERIFICATION_FAILED_REASON_NETWORK_UNAVAILABLE),
                Optional.of("INSTALL_FAILED_VERIFICATION_FAILURE:"
                        + " Verification cannot be completed because of unavailable network."));
    }

    @Test
    @RequiresFlagsEnabled(FLAG_VERIFICATION_SERVICE)
    public void testSetVerificationPolicyFails() throws Exception {
        // set without permission
        expectThrows(SecurityException.class,
                () -> mPackageInstaller.setVerificationPolicy(
                        VERIFICATION_POLICY_BLOCK_FAIL_CLOSED));
        // set an invalid value
        assertThat(SystemUtil.runWithShellPermissionIdentity(
                () -> mPackageInstaller.setVerificationPolicy(-1),
                android.Manifest.permission.VERIFICATION_AGENT
        )).isEqualTo(false);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_VERIFICATION_SERVICE)
    public void testVerifierOverridingPolicy() throws Exception {
        // Set the default policy to closed
        setDefaultVerificationPolicy(VERIFICATION_POLICY_BLOCK_FAIL_CLOSED);
        // Install a verifier that overrides the policy to none but rejects the verification
        installPackageWithAdb(VERIFIER_APP_REJECT_WITH_POLICY_OVERRIDE_APK_PATH);
        // Install a test app and expecting the install to pass
        assertInstallPackageWithSession(EMPTY_APP_APK, EMPTY_APP_PACKAGE_NAME);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_VERIFICATION_SERVICE)
    public void testVerificationSessionAPIsThrowAfterFinish() throws Exception {
        assertInstallPackageWithSession(
                EMPTY_APP_APK, EMPTY_APP_PACKAGE_NAME);
        VerificationSession session =
                mVerificationSession.get(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        SystemUtil.runWithShellPermissionIdentity(
                () -> expectThrows(IllegalStateException.class,
                        () -> session.extendTimeRemaining(100)),
                android.Manifest.permission.VERIFICATION_AGENT
        );
        SystemUtil.runWithShellPermissionIdentity(
                () -> expectThrows(IllegalStateException.class,
                        () -> session.setVerificationPolicy(VERIFICATION_POLICY_BLOCK_FAIL_WARN)),
                android.Manifest.permission.VERIFICATION_AGENT
        );
    }

    // Only used to install the verifier package
    private void installPackageWithAdb(String apkPath) {
        assertThat(SystemUtil.runShellCommand("pm install " + apkPath)).isEqualTo("Success\n");
    }

    private void uninstallPackage(String packageName) {
        SystemUtil.runShellCommand("pm uninstall " + packageName);
    }

    // Install the test apk with the verifier enabled and assert install success
    private void assertInstallPackageWithSession(String apkPath, String apkName) throws Exception {
        assertInstallPackageWithSession(apkPath, apkName, Optional.empty(),
                Optional.empty(), Optional.empty());
    }

    // Install the test apk with the verifier enabled and assert install failure with code and msg
    private void assertInstallPackageWithSession(String apkPath, String apkName,
            Optional<Integer> statusCodeExpected, Optional<Integer> reasonExpected,
            Optional<String> errMsgExpected) throws Exception {
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.installFlags |= PackageManager.INSTALL_REPLACE_EXISTING;
        params.setAppPackageName(apkName);
        final int sessionId = mPackageInstaller.createSession(params);
        PackageInstaller.Session session = mPackageInstaller.openSession(sessionId);
        writeFileToSession(session, apkPath);

        CompletableFuture<Integer> failureReasonReceived = new CompletableFuture<>();
        CompletableFuture<Integer> statusCode = new CompletableFuture<>();
        CompletableFuture<String> errMsg = new CompletableFuture<>();
        SystemUtil.runWithShellPermissionIdentity(() -> {
            session.commit(new IntentSender((IIntentSender) new IIntentSender.Stub() {
                @Override
                public void send(int code, Intent intent, String resolvedType,
                        IBinder allowlistToken, IIntentReceiver finishedReceiver,
                        String requiredPermission, Bundle options) throws RemoteException {
                    failureReasonReceived.complete(intent.getIntExtra(
                            PackageInstaller.EXTRA_VERIFICATION_FAILURE_REASON,
                            Integer.MIN_VALUE));
                    statusCode.complete(intent.getIntExtra(
                            PackageInstaller.EXTRA_STATUS, Integer.MIN_VALUE));
                    errMsg.complete(
                            intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE));
                }
            }));

            if (statusCodeExpected.isPresent() && reasonExpected.isPresent()
                    && errMsgExpected.isPresent()) {
                // Expecting failure with a reason code
                assertThat(failureReasonReceived.get(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS))
                        .isEqualTo(reasonExpected.get());
                assertThat(statusCode.get(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS))
                        .isEqualTo(statusCodeExpected.get());
                assertThat(errMsg.get(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS))
                        .contains(errMsgExpected.get());
            } else {
                // Expecting success
                assertThat(errMsg.get(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS))
                        .isEqualTo("INSTALL_SUCCEEDED: Session installed");
                assertThat(statusCode.get(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS))
                        .isEqualTo(PackageInstaller.STATUS_SUCCESS);
            }
        }, Manifest.permission.INSTALL_PACKAGES); // Avoid user confirmation dialog
    }

    private static void writeFullStream(InputStream inputStream, OutputStream outputStream)
            throws IOException {
        byte[] buffer = new byte[1024];
        int length;
        while ((length = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, length);
        }
    }

    private static void writeFileToSession(PackageInstaller.Session session, String apkPath)
            throws IOException {
        final File apkFile = new File(apkPath);
        try (
                OutputStream os = session.openWrite("base.apk", 0, apkFile.length());
                InputStream is = new FileInputStream(apkFile)) {
            writeFullStream(is, os);
        }
    }

    private class TestVerifierBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            switch (action) {
                case ACTION_SERVICE_CONNECTED:
                    mServiceConnectedLatch.open();
                    break;
                case ACTION_NAME_RECEIVED:
                    mNameAvailableLatch.complete(intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME));
                    break;
                case ACTION_CANCELLED_RECEIVED:
                    mVerificationCancelledLatch.complete(
                            intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME));
                    break;
                case ACTION_REQUEST_RECEIVED:
                    mVerificationSession.complete(intent.getParcelableExtra(
                            EXTRA_VERIFICATION_SESSION, VerificationSession.class));
                    break;
            }
        }
    }

    private static boolean isLowRamDevice() {
        return InstrumentationRegistry.getInstrumentation().getContext()
                .getSystemService(ActivityManager.class).isLowRamDevice();
    }

    private String getTimeoutValueFromDeviceConfig(String propertyName) {
        final DeviceConfigStateManager stateManager = new DeviceConfigStateManager(mContext,
                NAMESPACE_PACKAGE_MANAGER_SERVICE, propertyName);
        return stateManager.get();
    }

    private void setTimeoutValueInDeviceConfig(String propertyName, String value) {
        final DeviceConfigStateManager stateManager = new DeviceConfigStateManager(mContext,
                NAMESPACE_PACKAGE_MANAGER_SERVICE, propertyName);
        String currentValue = stateManager.get();
        if (!Objects.equals(currentValue, value)) {
            // Only change the value if the current value is different
            stateManager.set(value);
        }
    }
}
