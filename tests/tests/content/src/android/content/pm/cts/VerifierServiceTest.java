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

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInstaller;
import android.os.Build;
import android.os.ConditionVariable;
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
    private static final String VERIFIER_APP_TIMEOUT_APK_PATH =
            SAMPLE_APK_BASE + "CtsTestVerifierAppTimeout.apk";
    private static final String VERIFIER_APP_INCOMPLETE_UNKNOWN_APK_PATH =
            SAMPLE_APK_BASE + "CtsTestVerifierAppIncompleteUnknown.apk";
    private static final String EMPTY_APP_APK = SAMPLE_APK_BASE
            + "CtsContentEmptyTestApp.apk";
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
    private static final long DEFAULT_TIMEOUT_MS = isLowRamDevice()
            ? TimeUnit.SECONDS.toMillis(60) : TimeUnit.SECONDS.toMillis(15);
    private final ConditionVariable mServiceConnectedLatch = new ConditionVariable();
    private final ConditionVariable mVerificationRequiredLatch = new ConditionVariable();
    private CompletableFuture<String> mNameAvailableLatch;
    private CompletableFuture<String> mVerificationCancelledLatch;

    private final Context mContext = InstrumentationRegistry.getInstrumentation().getContext();

    private final TestVerifierBroadcastReceiver mBroadcastReceiver =
            new TestVerifierBroadcastReceiver();

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
        installPackage(VERIFIER_APP_APK_PATH);

        // Register to listen for the broadcasts from the test verifier service
        final IntentFilter intentFilter = getIntentFilter();
        mContext.registerReceiver(mBroadcastReceiver, intentFilter, Context.RECEIVER_EXPORTED);

        // Reset the latches
        mServiceConnectedLatch.close();
        mVerificationRequiredLatch.close();
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
    }

    @Test
    @RequiresFlagsEnabled(FLAG_VERIFICATION_SERVICE)
    public void testInstallSuccess() throws Exception {
        installPackageWithPackageName(EMPTY_APP_APK, EMPTY_APP_PACKAGE_NAME);
        assertThat(mServiceConnectedLatch.block(DEFAULT_TIMEOUT_MS)).isTrue();
        assertThat(mNameAvailableLatch.get(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)).isEqualTo(
                EMPTY_APP_PACKAGE_NAME);
        assertThat(mVerificationRequiredLatch.block(DEFAULT_TIMEOUT_MS)).isTrue();
    }

    @Test
    @RequiresFlagsEnabled(FLAG_VERIFICATION_SERVICE)
    public void testSessionAbandon() throws Exception {
        PackageInstaller pi = mContext.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(EMPTY_APP_PACKAGE_NAME);
        final int sessionId = pi.createSession(params);
        assertThat(sessionId).isGreaterThan(0);
        assertThat(mNameAvailableLatch.get(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)).isEqualTo(
                EMPTY_APP_PACKAGE_NAME);
        pi.abandonSession(sessionId);
        assertThat(mVerificationCancelledLatch.get(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS))
                .isEqualTo(EMPTY_APP_PACKAGE_NAME);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_VERIFICATION_SERVICE)
    public void testVerifierCrash() throws Exception {
        // Install the crash version of the verifier
        uninstallPackage(VERIFIER_APP_PACKAGE_NAME);
        installPackage(VERIFIER_APP_CRASH_APK_PATH);
        // Install a test app and expecting the install to fail
        installPackageExpectingError(EMPTY_APP_APK,
                "Failure [INSTALL_FAILED_VERIFICATION_FAILURE:"
                        + " A verifier agent is available on device but cannot be connected.]");
    }

    @Test
    @RequiresFlagsEnabled(FLAG_VERIFICATION_SERVICE)
    public void testVerifierReject() throws Exception {
        // Install the reject version of the verifier
        uninstallPackage(VERIFIER_APP_PACKAGE_NAME);
        installPackage(VERIFIER_APP_REJECT_APK_PATH);
        // Install a test app and expecting the install to fail
        installPackageExpectingError(EMPTY_APP_APK,
                "Failure [INSTALL_FAILED_VERIFICATION_FAILURE:"
                        + " Verifier rejected the installation with message: " + REJECT_MESSAGE);
        assertThat(mServiceConnectedLatch.block(DEFAULT_TIMEOUT_MS)).isTrue();
        assertThat(mVerificationRequiredLatch.block(DEFAULT_TIMEOUT_MS)).isTrue();
    }

    @Test
    @RequiresFlagsEnabled(FLAG_VERIFICATION_SERVICE)
    public void testVerifierTimeout() throws Exception {
        final String existingTimeoutValueInDeviceConfig =
                getTimeoutValueFromDeviceConfig(PROPERTY_VERIFICATION_REQUEST_TIMEOUT_MILLIS);
        try {
            // Install the timeout version of the verifier
            uninstallPackage(VERIFIER_APP_PACKAGE_NAME);
            installPackage(VERIFIER_APP_TIMEOUT_APK_PATH);
            // Reduce the default timeout duration to reduce test run time
            setTimeoutValueInDeviceConfig(PROPERTY_VERIFICATION_REQUEST_TIMEOUT_MILLIS,
                    String.valueOf(2000)); // The system will wait for 2 seconds
            // Install a test app and expecting the install to fail
            installPackageExpectingError(EMPTY_APP_APK,
                    "Failure [INSTALL_FAILED_VERIFICATION_FAILURE:"
                            + " Verification timed out;"
                            + " missing a response from the verifier within the time limit");
            assertThat(mServiceConnectedLatch.block(DEFAULT_TIMEOUT_MS)).isTrue();
            assertThat(mVerificationRequiredLatch.block(DEFAULT_TIMEOUT_MS)).isTrue();
        } finally {
            setTimeoutValueInDeviceConfig(PROPERTY_VERIFICATION_REQUEST_TIMEOUT_MILLIS,
                    existingTimeoutValueInDeviceConfig);
        }
    }

    @Test
    @RequiresFlagsEnabled(FLAG_VERIFICATION_SERVICE)
    public void testVerifierIncompleteUnknown() throws Exception {
        // Install the incomplete-unknown version of the verifier
        uninstallPackage(VERIFIER_APP_PACKAGE_NAME);
        installPackage(VERIFIER_APP_INCOMPLETE_UNKNOWN_APK_PATH);
        // Install a test app and expecting the install to fail
        installPackageExpectingError(EMPTY_APP_APK,
                "Failure [INSTALL_FAILED_INTERNAL_ERROR:"
                        + " Verification cannot be completed for unknown reasons.");
        assertThat(mServiceConnectedLatch.block(DEFAULT_TIMEOUT_MS)).isTrue();
        assertThat(mVerificationRequiredLatch.block(DEFAULT_TIMEOUT_MS)).isTrue();
    }

    private void installPackage(String apkPath) {
        assertThat(SystemUtil.runShellCommand("pm install " + apkPath)).isEqualTo("Success\n");
    }

    private void installPackageWithPackageName(String apkPath, String packageName) {
        assertThat(SystemUtil.runShellCommand(
                "pm install --pkg " + packageName + " " + apkPath)).isEqualTo("Success\n");
    }

    private void installPackageExpectingError(String apkPath, String err) {
        assertThat(SystemUtil.runShellCommand("pm install " + apkPath)).contains(err);
    }

    private void uninstallPackage(String packageName) {
        SystemUtil.runShellCommand("pm uninstall " + packageName);
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
                    mVerificationRequiredLatch.open();
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
        if (currentValue != value) {
            // Only change the value if the current value is different
            stateManager.set(value);
        }
    }
}
