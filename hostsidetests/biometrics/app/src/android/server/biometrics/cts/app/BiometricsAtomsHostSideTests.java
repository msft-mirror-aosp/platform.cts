/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.server.biometrics.cts.app;

import static android.Manifest.permission.MANAGE_SECURE_LOCK_DEVICE;
import static android.Manifest.permission.TEST_BIOMETRIC;
import static android.hardware.biometrics.SensorProperties.STRENGTH_CONVENIENCE;
import static android.hardware.biometrics.SensorProperties.STRENGTH_STRONG;
import static android.server.biometrics.cts.FingerprintHostsideConstants.FACE_AUTH_ACQUIRED_MESSAGES;
import static android.server.biometrics.cts.FingerprintHostsideConstants.FACE_ENROLL_ACQUIRED_MESSAGES;
import static android.server.biometrics.cts.FingerprintHostsideConstants.FINGERPRINT_AUTH_ACQUIRED_MESSAGES;
import static android.server.biometrics.cts.FingerprintHostsideConstants.FINGERPRINT_ENROLL_ACQUIRED_MESSAGES;

import static com.android.compatibility.common.util.SystemUtil.runShellCommand;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;

import android.app.Instrumentation;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.hardware.biometrics.BiometricTestSession;
import android.hardware.biometrics.SensorProperties;
import android.os.CancellationSignal;
import android.security.authenticationpolicy.AuthenticationPolicyManager;
import android.security.authenticationpolicy.DisableSecureLockDeviceParams;
import android.security.authenticationpolicy.EnableSecureLockDeviceParams;
import android.server.biometrics.util.SensorStates;
import android.server.biometrics.util.Utils;
import android.server.wm.LockScreenSession;
import android.server.wm.UiDeviceUtils;
import android.server.wm.WindowManagerStateHelper;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.BySelector;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.Until;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.PollingCheck;
import com.android.server.biometrics.nano.SensorStateProto;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@RunWith(AndroidJUnit4.class)
public class BiometricsAtomsHostSideTests {

    private static final String TAG = "BiometricsAtomsHostSideTests";

    private static final long WAIT_MS = 2000;
    private static final long TIMEOUT = 5_000;
    private static final String VIEW_BIOMETRIC_PROMPT_ID = "biometric_scrollview";
    private static final String VIEW_BIOMETRIC_PROMPT_CONFIRM_ID = "button_confirm";
    private static final String SECURE_LOCK_DEVICE_BIOMETRIC_AUTH_ID =
            "secure_lock_device_biometric_auth_content";

    private Instrumentation mInstrumentation;
    private UiDevice mDevice;
    private int mUserId;
    private AuthenticationPolicyManager mAuthenticationPolicyManager;
    private BiometricManager mBiometricManager;
    private List<SensorProperties> mSensorProperties;
    private String mUiPackage;
    private Context mContext;

    @Before
    public void setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mInstrumentation
                .getUiAutomation()
                .adoptShellPermissionIdentity(TEST_BIOMETRIC, MANAGE_SECURE_LOCK_DEVICE);
        mDevice = UiDevice.getInstance(mInstrumentation);

        mContext = mInstrumentation.getContext();
        mUserId = mContext.getUserId();
        mAuthenticationPolicyManager = mContext.getSystemService(AuthenticationPolicyManager.class);
        mBiometricManager = mContext.getSystemService(BiometricManager.class);
        // ignore the legacy HIDL interface for all tests
        mSensorProperties = filterSensorProperties(mBiometricManager.getSensorProperties());
        mUiPackage = mBiometricManager.getUiPackage();

        assumeTrue(!mSensorProperties.isEmpty());

        UiDeviceUtils.pressWakeupButton();
        UiDeviceUtils.pressUnlockButton();
    }

    private static List<SensorProperties> filterSensorProperties(
            @NonNull List<SensorProperties> properties) {
        final int aidlFpSensorId = Utils.getAidlFingerprintSensorId();
        final int aidlFaceSensorId = Utils.getAidlFaceSensorId();

        return properties.stream().filter(p -> {
            final int id = p.getSensorId();
            try {
                if (isFingerprint(id) && aidlFpSensorId != -1) {
                    return id == aidlFpSensorId;
                } else if (isFace(id) && aidlFaceSensorId != -1) {
                    return id == aidlFaceSensorId;
                }
            } catch (Throwable t) {
                throw new IllegalStateException("Failed to check modality", t);
            }
            return false;
        }).collect(Collectors.toList());
    }

    private static List<SensorProperties> filterWeakOrGreaterSensorProperties(
            @NonNull List<SensorProperties> properties) {
        return properties.stream()
                .filter(p -> p.getSensorStrength() != STRENGTH_CONVENIENCE)
                .collect(Collectors.toList());
    }

    private static List<SensorProperties> filterStrongFingerprintSensorProperties(
            @NonNull List<SensorProperties> properties) {
        return properties.stream()
                .filter(p -> p.getSensorStrength() == STRENGTH_STRONG)
                .filter(
                        p -> {
                            try {
                                return isFingerprint(p.getSensorId());
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        })
                .collect(Collectors.toList());
    }

    @After
    public void teardown() {
        mInstrumentation.waitForIdleSync();
        try {
            Utils.waitForIdleService();
        } catch (Throwable t) {
            Log.e(TAG, "Unable to await sensor idle", t);
        }

        mInstrumentation.getUiAutomation().dropShellPermissionIdentity();
    }

    @Test
    public void testEnroll() throws Exception {
        for (SensorProperties prop : mSensorProperties) {
            final int sensorId = prop.getSensorId();
            try (BiometricTestSession session = mBiometricManager.createTestSession(sensorId)) {
                session.startEnroll(mUserId);
                Utils.waitForBusySensor(sensorId);

                for (int code : getAcquiredCodesForEnroll(sensorId)) {
                    session.notifyAcquired(mUserId, code);
                    mInstrumentation.waitForIdleSync();
                }

                session.finishEnroll(mUserId);
                Utils.waitForIdleService();
            }
        }
        mInstrumentation.waitForIdleSync();
    }

    @Test
    public void testEnrollThenCleanUp() throws Exception {
        for (SensorProperties prop : mSensorProperties) {
            final int sensorId = prop.getSensorId();
            try (BiometricTestSession session = mBiometricManager.createTestSession(sensorId)) {
                session.startEnroll(mUserId);
                Utils.waitForBusySensor(sensorId);

                mInstrumentation.waitForIdleSync();

                session.finishEnroll(mUserId);
                Utils.waitForIdleService();

                mInstrumentation.waitForIdleSync();

                // We only enroll on the framework side so when the enumeration starts, there won't
                // be any identifier reported from the Hal side which will cause dangling framework.
                session.cleanupInternalState(mUserId);
                Utils.waitForBusySensor(sensorId);
                Utils.waitForIdleService();
            }
        }
        mInstrumentation.waitForIdleSync();
    }

    @Test
    public void testAuthenticateWithBiometricPrompt() throws Exception {
        // TODO(b/253318030): No API beyond bp (doesn't allow convenience) - need new test API
        for (SensorProperties prop : filterWeakOrGreaterSensorProperties(mSensorProperties)) {
            final int sensorId = prop.getSensorId();
            try (BiometricTestSession session = mBiometricManager.createTestSession(sensorId)) {
                session.startEnroll(mUserId);
                Utils.waitForBusySensor(sensorId);
                session.finishEnroll(mUserId);
                Utils.waitForIdleService();

                final Executor executor = mInstrumentation.getContext().getMainExecutor();
                final TestAuthCallback callback = new TestAuthCallback(TAG);
                final BiometricPrompt prompt = new BiometricPrompt.Builder(
                        mInstrumentation.getContext())
                        .setTitle("Title")
                        .setSubtitle("Subtitle")
                        .setDescription("Description")
                        .setNegativeButton("Negative Button", executor, (dialog, which) -> {})
                        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                        .setAllowBackgroundAuthentication(true)
                        .build();
                prompt.authenticate(new CancellationSignal(), executor, callback);

                Utils.waitForBusySensor(sensorId);
                mDevice.wait(Until.hasObject(getBySelector(VIEW_BIOMETRIC_PROMPT_ID)), WAIT_MS);

                for (int code : getAcquiredCodesForAuthenticate(sensorId)) {
                    session.notifyAcquired(mUserId, code);
                    mInstrumentation.waitForIdleSync();
                }

                session.acceptAuthentication(mUserId);
                Utils.waitForIdleService();

                // The framework may require confirmation even if not requested by the API
                final UiObject2 confirmButton = mDevice.wait(Until.findObject(
                        getBySelector(VIEW_BIOMETRIC_PROMPT_CONFIRM_ID)), WAIT_MS);
                if (confirmButton != null) {
                    Log.d(TAG, "click confirmButton");
                    confirmButton.click();
                }
                mDevice.wait(Until.gone(getBySelector(VIEW_BIOMETRIC_PROMPT_ID)), WAIT_MS);
                callback.awaitResult();

                assertThat(callback.isAuthenticatedWithResult()).isTrue();
            }
        }
        mInstrumentation.waitForIdleSync();
    }

    @Test
    public void testSecureLockDeviceStateChanged() throws Exception {
        assumeTrue(
                "Device must support secure lock screen",
                mContext.getPackageManager()
                        .hasSystemFeature(PackageManager.FEATURE_SECURE_LOCK_SCREEN));

        final List<SensorProperties> strongFingerprintSensorProperties =
                filterStrongFingerprintSensorProperties(mSensorProperties);
        assumeTrue(
                "Device must have at least one strong fingerprint sensor",
                !strongFingerprintSensorProperties.isEmpty());

        final SensorProperties prop = strongFingerprintSensorProperties.getFirst();
        int sensorId = prop.getSensorId();

        try (BiometricTestSession session = mBiometricManager.createTestSession(sensorId);
                LockScreenSession lockScreenSession =
                        new LockScreenSession(mInstrumentation, new WindowManagerStateHelper())) {
            lockScreenSession.setLockCredential();

            // Enroll a strong biometric, which is required to enable the feature.
            session.startEnroll(mUserId);
            Utils.waitForBusySensor(sensorId);
            session.finishEnroll(mUserId);
            Utils.waitForIdleService();

            assumeTrue(
                    "Device must support secure lock device",
                    mAuthenticationPolicyManager.getSecureLockDeviceAvailability()
                            == AuthenticationPolicyManager.SUCCESS);

            // Set test mode to true to prevent ADB/USB ports disabled
            mAuthenticationPolicyManager.setSecureLockDeviceTestStatus(true);

            // Test SecureLockDeviceStateChanged.SecureLockDeviceEventType.ENABLED
            // Enable secure lock device
            mAuthenticationPolicyManager.enableSecureLockDevice(
                    new EnableSecureLockDeviceParams("Enabling for atom test"));
            assertThat(mAuthenticationPolicyManager.isSecureLockDeviceEnabled()).isTrue();

            // Test SecureLockDeviceStateChanged.SecureLockDeviceEventType.DISABLED_MANUALLY
            // Disable manually
            mAuthenticationPolicyManager.disableSecureLockDevice(
                    new DisableSecureLockDeviceParams("Disabling manually for atom test"));
            assertThat(mAuthenticationPolicyManager.isSecureLockDeviceEnabled()).isFalse();

            // Test SecureLockDeviceStateChanged.SecureLockDeviceEventType.ENABLED
            // Re-enable secure lock device
            mAuthenticationPolicyManager.enableSecureLockDevice(
                    new EnableSecureLockDeviceParams("Re-enabling for 2FA test"));
            assertThat(mAuthenticationPolicyManager.isSecureLockDeviceEnabled()).isTrue();

            // Set test mode to false to require two-factor authentication to disable
            mAuthenticationPolicyManager.setSecureLockDeviceTestStatus(false);

            // Test SecureLockDeviceStateChanged.SecureLockDeviceEventType.
            // DISABLED_TWO_FACTOR_AUTHENTICATION
            // Successful primary authentication for first step of two-factor authentication:
            lockScreenSession.unlock();

            PollingCheck.waitFor(
                    TIMEOUT,
                    () -> {
                        try {
                            String dumpsysOutput =
                                    runShellCommand(mInstrumentation, "dumpsys lock_settings");
                            // After primary auth, the flags should be exactly
                            // STRONG_BIOMETRIC_AUTH_REQUIRED_FOR_SECURE_LOCK_DEVICE (0x1000).
                            String expectedLine = "userId=" + mUserId + ", primaryAuthFlags=1000";
                            return dumpsysOutput.contains(expectedLine);
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to check strong auth flags via dumpsys", e);
                            return false;
                        }
                    },
                    "strong auth flags were not updated after primary auth during "
                            + "secure lock device");

            // Re-introduce permissions reset by the previous lock session call
            mInstrumentation
                    .getUiAutomation()
                    .adoptShellPermissionIdentity(TEST_BIOMETRIC, MANAGE_SECURE_LOCK_DEVICE);

            // Wait for second-factor biometric auth
            mDevice.wait(
                    Until.hasObject(getBySelector(SECURE_LOCK_DEVICE_BIOMETRIC_AUTH_ID)), WAIT_MS);
            Utils.waitForBusySensor(sensorId);

            // Successful biometric authentication to complete two-factor authentication
            session.acceptAuthentication(mUserId);

            // Wait for keyguard to report transition to GONE - only then does
            // SecureLockDeviceInteractor run onGoneTransitionFinished to disable secure lock
            // device.
            lockScreenSession.waitForKeyguardGone();

            if (mAuthenticationPolicyManager.isSecureLockDeviceEnabled()) {
                // Disabling after biometric auth if onGoneTransitionFinished does not finish in
                // time during test
                mAuthenticationPolicyManager.disableSecureLockDevice(
                        new DisableSecureLockDeviceParams("Disabling after biometric auth"));
            }
            // Poll to wait for the asynchronous state change to complete.
            PollingCheck.waitFor(
                    TIMEOUT,
                    () -> !mAuthenticationPolicyManager.isSecureLockDeviceEnabled(),
                    "Secure lock device was not disabled after successful two factor "
                            + "credential and strong biometric authentication");
        } finally {
            // Re-introduce permissions in case of errors
            mInstrumentation
                    .getUiAutomation()
                    .adoptShellPermissionIdentity(TEST_BIOMETRIC, MANAGE_SECURE_LOCK_DEVICE);
            if (mAuthenticationPolicyManager.isSecureLockDeviceEnabled()) {
                mAuthenticationPolicyManager.disableSecureLockDevice(
                        new DisableSecureLockDeviceParams("Disabling for test cleanup"));
            }
            mAuthenticationPolicyManager.setSecureLockDeviceTestStatus(false);
        }
    }

    private BySelector getBySelector(String id) {
        return By.res(mUiPackage, id);
    }

    private static List<Integer> getAcquiredCodesForEnroll(int sensorId) throws Exception {
        if (isFace(sensorId)) {
            return FACE_ENROLL_ACQUIRED_MESSAGES;
        } else if (isFingerprint(sensorId)) {
            return FINGERPRINT_ENROLL_ACQUIRED_MESSAGES;
        }
        throw new IllegalStateException("unexpected sensor type");
    }

    private static List<Integer> getAcquiredCodesForAuthenticate(int sensorId) throws Exception {
        if (isFace(sensorId)) {
            return FACE_AUTH_ACQUIRED_MESSAGES;
        } else if (isFingerprint(sensorId)) {
            return FINGERPRINT_AUTH_ACQUIRED_MESSAGES;
        }
        throw new IllegalStateException("unexpected sensor type");
    }

    private static boolean isFace(int sensorId) throws Exception {
        return isSensorModality(sensorId, SensorStateProto.FACE);
    }

    private static boolean isFingerprint(int sensorId) throws Exception {
        return isSensorModality(sensorId, SensorStateProto.FINGERPRINT);
    }

    private static boolean isSensorModality(int sensorId, int modality) throws Exception {
        final Map<Integer, SensorStates.SensorState> states =
                Utils.getBiometricServiceCurrentState().mSensorStates.sensorStates;
        if (states.containsKey(sensorId)) {
            return states.get(sensorId).getModality() == modality;
        }
        return false;
    }
}
