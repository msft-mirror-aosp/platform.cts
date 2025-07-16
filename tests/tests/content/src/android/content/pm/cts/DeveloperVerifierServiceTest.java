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
import static android.content.pm.PackageInstaller.DEVELOPER_VERIFICATION_POLICY_BLOCK_FAIL_OPEN;
import static android.content.pm.PackageInstaller.DEVELOPER_VERIFICATION_POLICY_NONE;
import static android.content.pm.PackageInstaller.DEVELOPER_VERIFICATION_USER_RESPONSE_INSTALL_ANYWAY;
import static android.content.pm.verify.developer.DeveloperVerificationSession.DEVELOPER_VERIFICATION_INCOMPLETE_UNKNOWN;
import static android.content.pm.verify.developer.DeveloperVerificationStatus.APP_METADATA_VERIFICATION_STATUS_BAD;
import static android.content.pm.verify.developer.DeveloperVerificationStatus.APP_METADATA_VERIFICATION_STATUS_UNDEFINED;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.expectThrows;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.SigningDetails;
import android.content.pm.SigningInfo;
import android.content.pm.verify.developer.DeveloperVerificationSession;
import android.content.pm.verify.developer.DeveloperVerificationStatus;
import android.content.pm.verify.developer.DeveloperVerifierService;
import android.content.pm.verify.developer.IDeveloperVerificationSessionInterface;
import android.content.pm.verify.developer.IDeveloperVerifierService;
import android.net.Uri;
import android.os.Parcel;
import android.os.PersistableBundle;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.AppModeNonSdkSandbox;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.annotation.NonNull;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.SystemUtil;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

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
    private static final int RANDOM_SESSION_ID = 100;
    private static final long ASYNC_TIMEOUT_SECONDS = 5;

    private static final long TEST_TIMEOUT_MILLIS = 1000L;
    private static final long TEST_EXTENDED_TIMEOUT_MILLIS = 2000L;

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

    @Test
    public void testGetDeveloperVerificationUserConfirmationInfoThrowsWithoutPermission() {
        expectThrows(
                SecurityException.class,
                () ->
                        mPackageInstaller.getDeveloperVerificationUserConfirmationInfo(
                                RANDOM_SESSION_ID));
    }

    @Test
    public void testSetDeveloperVerificationUserResponseThrowsWithoutPermission() {
        expectThrows(
                SecurityException.class,
                () ->
                        mPackageInstaller.setDeveloperVerificationUserResponse(
                                RANDOM_SESSION_ID,
                                DEVELOPER_VERIFICATION_USER_RESPONSE_INSTALL_ANYWAY));
    }

    @Test
    public void testSessionParamsSetExtensionParams() {
        PackageInstaller.SessionParams params =
                new PackageInstaller.SessionParams(
                        PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        PersistableBundle extensionParams = new PersistableBundle();
        extensionParams.putString("key", "value");
        params.setExtensionParams(extensionParams);
        // Test Parcel
        Parcel parcel = Parcel.obtain();
        params.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        PackageInstaller.SessionParams paramsFromParcel =
                PackageInstaller.SessionParams.CREATOR.createFromParcel(parcel);
        assertThat(paramsFromParcel.extensionParams.getString("key")).isEqualTo("value");
    }

    @Test
    public void testDeveloperVerificationUserConfirmationInfoParcel() {
        PackageInstaller.DeveloperVerificationUserConfirmationInfo info =
                new PackageInstaller.DeveloperVerificationUserConfirmationInfo(
                        DEVELOPER_VERIFICATION_POLICY_BLOCK_FAIL_OPEN,
                        PackageInstaller.DeveloperVerificationUserConfirmationInfo
                                .DEVELOPER_VERIFICATION_USER_ACTION_NEEDED_REASON_UNKNOWN);
        Parcel parcel = Parcel.obtain();
        info.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        PackageInstaller.DeveloperVerificationUserConfirmationInfo infoFromParcel =
                PackageInstaller.DeveloperVerificationUserConfirmationInfo.CREATOR.createFromParcel(
                        parcel);
        assertThat(infoFromParcel.getVerificationPolicy()).isEqualTo(info.getVerificationPolicy());
        assertThat(infoFromParcel.getUserActionNeededReason())
                .isEqualTo(info.getUserActionNeededReason());
    }

    @Test
    public void testDeveloperVerificationSessionMethods() throws Exception {
        DeveloperVerificationSession session =
                new DeveloperVerificationSession(
                        RANDOM_SESSION_ID,
                        RANDOM_SESSION_ID,
                        EMPTY_APP_PACKAGE_NAME,
                        Uri.parse(EMPTY_APP_APK),
                        new SigningInfo(),
                        new ArrayList<>(),
                        null,
                        DEVELOPER_VERIFICATION_POLICY_BLOCK_FAIL_OPEN,
                        (IDeveloperVerificationSessionInterface)
                                new TesIDeveloperVerificationSessionInterface());
        // Test parcel
        Parcel parcel = Parcel.obtain();
        session.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        DeveloperVerificationSession sessionFromParcel =
                DeveloperVerificationSession.CREATOR.createFromParcel(parcel);
        // Test simple getters
        assertThat(sessionFromParcel.getId()).isEqualTo(RANDOM_SESSION_ID);
        assertThat(sessionFromParcel.getInstallSessionId()).isEqualTo(RANDOM_SESSION_ID);
        assertThat(sessionFromParcel.getPolicy())
                .isEqualTo(DEVELOPER_VERIFICATION_POLICY_BLOCK_FAIL_OPEN);
        assertThat(sessionFromParcel.getPackageName()).isEqualTo(EMPTY_APP_PACKAGE_NAME);
        assertThat(sessionFromParcel.getStagedPackageUri()).isEqualTo(Uri.parse(EMPTY_APP_APK));
        assertThat(sessionFromParcel.getSigningInfo()).isNotNull();
        assertThat(sessionFromParcel.getSigningInfo().getSigningDetails())
                .isEqualTo(SigningDetails.UNKNOWN);
        assertThat(sessionFromParcel.getDeclaredLibraries()).isEmpty();
        assertThat(sessionFromParcel.getExtensionParams()).isEqualTo(PersistableBundle.EMPTY);
        // Test binder methods
        // Test getTimeoutTimeMillis
        int testVerificationId1 = 1;
        assertThat(sessionFromParcel.getTimeoutTime())
                .isEqualTo(Instant.ofEpochMilli(TEST_TIMEOUT_MILLIS));
        // Test extendTimeoutMillis
        int testVerificationId2 = 2;
        long testAdditionalMillis = 500L;
        assertThat(sessionFromParcel.extendTimeout(Duration.ofMillis(0L)))
                .isEqualTo(Duration.ofMillis(TEST_EXTENDED_TIMEOUT_MILLIS));
        // Test setVerificationPolicy
        int testVerificationId3 = 3;
        int testPolicy = DEVELOPER_VERIFICATION_POLICY_BLOCK_FAIL_CLOSED;
        assertThat(sessionFromParcel.setPolicy(DEVELOPER_VERIFICATION_POLICY_BLOCK_FAIL_OPEN))
                .isTrue();
        // Test reportVerificationIncomplete
        sessionFromParcel.reportVerificationIncomplete(DEVELOPER_VERIFICATION_INCOMPLETE_UNKNOWN);
        // Test reportVerificationComplete
        DeveloperVerificationStatus testStatus =
                new DeveloperVerificationStatus.Builder().setVerified(true).build();
        PersistableBundle testExtensionResponse = new PersistableBundle();
        testExtensionResponse.putString("testKey", "testValue");
        sessionFromParcel.reportVerificationComplete(testStatus, testExtensionResponse);
        // Test reportVerificationBypassed
        sessionFromParcel.reportVerificationBypassed(/* bypassReason= */ 20);
    }

    private static class TesIDeveloperVerificationSessionInterface
            extends IDeveloperVerificationSessionInterface.Stub {

        @Override
        public long getTimeoutTimeMillis(int verificationId) {
            return TEST_TIMEOUT_MILLIS;
        }

        @Override
        public long extendTimeoutMillis(int verificationId, long additionalMillis) {
            return TEST_EXTENDED_TIMEOUT_MILLIS;
        }

        @Override
        public boolean setVerificationPolicy(int verificationId, int policy) {
            return true; // Test returning true
        }

        @Override
        public void reportVerificationIncomplete(int verificationId, int reason) {}

        @Override
        public void reportVerificationComplete(
                int verificationId,
                DeveloperVerificationStatus status,
                PersistableBundle extensionResponse) {}

        @Override
        public void reportVerificationBypassed(int verificationId, int bypassReason) {}
    }

    @Test
    public void testDeveloperVerificationStatusMethods() {
        // Test default values
        DeveloperVerificationStatus status = new DeveloperVerificationStatus.Builder().build();
        assertThat(status.isVerified()).isFalse();
        assertThat(status.isLiteVerification()).isFalse();
        assertThat(status.getAppMetadataVerificationStatus())
                .isEqualTo(APP_METADATA_VERIFICATION_STATUS_UNDEFINED);
        assertThat(status.getFailureMessage()).isNull();
        // Test all values set and through parcel
        status =
                new DeveloperVerificationStatus.Builder()
                        .setVerified(true)
                        .setLiteVerification(true)
                        .setAppMetadataVerificationStatus(APP_METADATA_VERIFICATION_STATUS_BAD)
                        .setFailureMessage("failure message")
                        .build();
        Parcel parcel = Parcel.obtain();
        status.writeToParcel(parcel, status.describeContents());
        parcel.setDataPosition(0);
        DeveloperVerificationStatus statusFromParcel =
                DeveloperVerificationStatus.CREATOR.createFromParcel(parcel);
        assertThat(statusFromParcel.isVerified()).isTrue();
        assertThat(statusFromParcel.isLiteVerification()).isTrue();
        assertThat(statusFromParcel.getAppMetadataVerificationStatus())
                .isEqualTo(APP_METADATA_VERIFICATION_STATUS_BAD);
        assertThat(statusFromParcel.getFailureMessage()).isEqualTo("failure message");
    }

    @Test
    public void testDeveloperVerifierServiceBinderMethods() throws Exception {
        CompletableFuture<String> onPackageNameAvailableCalled = new CompletableFuture<>();
        CompletableFuture<String> onVerificationCancelledCalled = new CompletableFuture<>();
        CompletableFuture<DeveloperVerificationSession> onVerificationRequiredCalled =
                new CompletableFuture<>();
        CompletableFuture<DeveloperVerificationSession> onVerificationRetryCalled =
                new CompletableFuture<>();
        CompletableFuture<Integer> onVerificationTimeoutCalled = new CompletableFuture<>();

        final TestDeveloperVerifierService myService =
                new TestDeveloperVerifierService(
                        onPackageNameAvailableCalled,
                        onVerificationCancelledCalled,
                        onVerificationRequiredCalled,
                        onVerificationRetryCalled,
                        onVerificationTimeoutCalled);
        assertThat(myService.onBind(null)).isNull();
        assertThat(myService.onBind(new Intent())).isNull();
        IDeveloperVerifierService.Stub binder =
                (IDeveloperVerifierService.Stub)
                        myService.onBind(new Intent(PackageManager.ACTION_VERIFY_DEVELOPER));
        assertThat(binder).isNotNull();

        // Test onPackageNameAvailable
        binder.onPackageNameAvailable(EMPTY_APP_PACKAGE_NAME);
        assertThat(onPackageNameAvailableCalled.get(ASYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .isEqualTo(EMPTY_APP_PACKAGE_NAME);

        // Test onVerificationCancelled
        binder.onVerificationCancelled(EMPTY_APP_PACKAGE_NAME);
        assertThat(onVerificationCancelledCalled.get(ASYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .isEqualTo(EMPTY_APP_PACKAGE_NAME);

        // Test onVerificationRequired
        DeveloperVerificationSession sessionForRequired =
                new DeveloperVerificationSession(
                        RANDOM_SESSION_ID,
                        RANDOM_SESSION_ID,
                        EMPTY_APP_PACKAGE_NAME,
                        Uri.parse(EMPTY_APP_APK),
                        new SigningInfo(),
                        new ArrayList<>(),
                        null,
                        DEVELOPER_VERIFICATION_POLICY_BLOCK_FAIL_OPEN,
                        null);
        binder.onVerificationRequired(sessionForRequired);
        assertThat(onVerificationRequiredCalled.get(ASYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .isEqualTo(sessionForRequired);

        // Test onVerificationRetry
        DeveloperVerificationSession sessionForRetry =
                new DeveloperVerificationSession(
                        RANDOM_SESSION_ID + 1,
                        RANDOM_SESSION_ID + 1,
                        EMPTY_APP_PACKAGE_NAME,
                        Uri.parse(EMPTY_APP_APK),
                        new SigningInfo(),
                        new ArrayList<>(),
                        null,
                        DEVELOPER_VERIFICATION_POLICY_BLOCK_FAIL_OPEN,
                        null);
        binder.onVerificationRetry(sessionForRetry);
        assertThat(onVerificationRetryCalled.get(ASYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .isEqualTo(sessionForRetry);

        // Test onVerificationTimeout
        final int testVerificationId = RANDOM_SESSION_ID + 2;
        binder.onVerificationTimeout(testVerificationId);
        assertThat(onVerificationTimeoutCalled.get(ASYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .isEqualTo(testVerificationId);
    }

    private static class TestDeveloperVerifierService extends DeveloperVerifierService {
        private final CompletableFuture<String> mOnPackageNameAvailableCalled;
        private final CompletableFuture<String> mOnVerificationCancelledCalled;
        private final CompletableFuture<DeveloperVerificationSession> mOnVerificationRequiredCalled;
        private final CompletableFuture<DeveloperVerificationSession> mOnVerificationRetryCalled;
        private final CompletableFuture<Integer> mOnVerificationTimeoutCalled;

        TestDeveloperVerifierService(
                CompletableFuture<String> onPackageNameAvailableCalled,
                CompletableFuture<String> onVerificationCancelledCalled,
                CompletableFuture<DeveloperVerificationSession> onVerificationRequiredCalled,
                CompletableFuture<DeveloperVerificationSession> onVerificationRetryCalled,
                CompletableFuture<Integer> onVerificationTimeoutCalled) {
            mOnPackageNameAvailableCalled = onPackageNameAvailableCalled;
            mOnVerificationCancelledCalled = onVerificationCancelledCalled;
            mOnVerificationRequiredCalled = onVerificationRequiredCalled;
            mOnVerificationRetryCalled = onVerificationRetryCalled;
            mOnVerificationTimeoutCalled = onVerificationTimeoutCalled;
        }

        @Override
        public void onPackageNameAvailable(@NonNull String packageName) {
            mOnPackageNameAvailableCalled.complete(packageName);
        }

        @Override
        public void onVerificationCancelled(@NonNull String packageName) {
            mOnVerificationCancelledCalled.complete(packageName);
        }

        @Override
        public void onVerificationRequired(@NonNull DeveloperVerificationSession session) {
            mOnVerificationRequiredCalled.complete(session);
        }

        @Override
        public void onVerificationRetry(@NonNull DeveloperVerificationSession session) {
            mOnVerificationRetryCalled.complete(session);
        }

        @Override
        public void onVerificationTimeout(int verificationId) {
            mOnVerificationTimeoutCalled.complete(verificationId);
        }
    }
}
