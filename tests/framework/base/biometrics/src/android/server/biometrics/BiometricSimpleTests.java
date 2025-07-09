/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.server.biometrics;

import static android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG;
import static android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_WEAK;
import static android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL;
import static android.hardware.biometrics.BiometricManager.Authenticators.IDENTITY_CHECK;
import static android.hardware.biometrics.BiometricManager.TYPE_FACE;
import static android.hardware.biometrics.BiometricManager.TYPE_FINGERPRINT;
import static android.hardware.biometrics.SensorProperties.STRENGTH_STRONG;
import static android.hardware.biometrics.SensorProperties.STRENGTH_WEAK;

import static com.android.server.biometrics.nano.BiometricServiceStateProto.STATE_AUTH_IDLE;
import static com.android.server.biometrics.nano.BiometricServiceStateProto.STATE_AUTH_STARTED_UI_SHOWING;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import android.content.pm.PackageManager;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricManager.Authenticators;
import android.hardware.biometrics.BiometricPrompt;
import android.hardware.biometrics.BiometricTestSession;
import android.hardware.biometrics.Flags;
import android.hardware.biometrics.IdentityCheckStatus;
import android.hardware.biometrics.SensorProperties;
import android.os.CancellationSignal;
import android.os.SystemClock;
import android.platform.test.annotations.Presubmit;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.server.biometrics.util.BiometricServiceState;
import android.server.biometrics.util.SensorStates;
import android.server.biometrics.util.TestSessionList;
import android.server.biometrics.util.Utils;
import android.util.Log;

import androidx.test.uiautomator.UiObject2;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.CddTest;
import com.android.server.biometrics.nano.SensorStateProto;

import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Simple tests.
 */
@Presubmit
public class BiometricSimpleTests extends BiometricTestBase {
    private static final String TAG = "BiometricTests/Simple";

    /**
     * Tests that enrollments created via {@link BiometricTestSession} show up in the
     * biometric dumpsys.
     */
    @ApiTest(apis = {
            "android.hardware.biometrics."
                    + "BiometricTestSession#startEnroll",
            "android.hardware.biometrics."
                    + "BiometricTestSession#finishEnroll"})
    @Test
    public void testEnroll() throws Exception {
        assumeTrue(Utils.isFirstApiLevel29orGreater());
        for (SensorProperties prop : mSensorProperties) {
            try (BiometricTestSession session =
                         mBiometricManager.createTestSession(prop.getSensorId())) {
                enrollForSensor(session, prop.getSensorId());
            }
        }
    }

    /** Tests that the corresponding enrolled count is correct. */
    @ApiTest(apis = {"android.hardware.biometrics.BiometricManager#getEnrollmentStatus"})
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_MOVE_FM_API_TO_BM)
    public void testGetEnrollmentCount() throws Exception {
        assumeTrue(Utils.isFirstApiLevel29orGreater());
        mBiometricManager
                .getEnrollmentStatus()
                .forEach((modality, status) -> assertEquals(0, status.getEnrollmentCount()));

        for (SensorProperties prop : mSensorProperties) {
            final int sensorId = prop.getSensorId();
            final SensorStates.SensorState currentSensor =
                    getCurrentState().mSensorStates.sensorStates.get(sensorId);
            final int sensorModality = currentSensor.getModality();
            final int sensorStrength = currentSensor.getCurrentStrength();

            int enrolledModality = 0;

            if (sensorModality == SensorStateProto.FINGERPRINT) {
                enrolledModality = TYPE_FINGERPRINT;
            } else if (sensorModality == SensorStateProto.FACE) {
                enrolledModality = TYPE_FACE;
            }
            final int expectedModality = enrolledModality;

            try (BiometricTestSession session = mBiometricManager.createTestSession(sensorId)) {
                enrollForSensor(session, sensorId);

                mBiometricManager
                        .getEnrollmentStatus()
                        .forEach(
                                (modality, status) -> {
                                    if (modality == expectedModality) {
                                        assertEquals(1, status.getEnrollmentCount());
                                        assertEquals(sensorStrength, status.getStrength());
                                    } else {
                                        assertEquals(0, status.getEnrollmentCount());
                                    }
                                });
            }
        }
    }

    /**
     * Test without USE_BIOMETRIC permission, {@link BiometricManager#getEnrollmentStatus} should
     * throw security exception.
     */
    @ApiTest(apis = {"android.hardware.biometrics.BiometricManager#getEnrollmentStatus"})
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_MOVE_FM_API_TO_BM)
    public void testGetEnrolledFingerprintCount_withoutPermissionFailed() {
        assumeTrue(Utils.isFirstApiLevel29orGreater());
        mInstrumentation.getUiAutomation().dropShellPermissionIdentity();
        SecurityException e =
                assertThrows(SecurityException.class, mBiometricManager::getEnrollmentStatus);
        assertThat(e).hasMessageThat().contains("SET_BIOMETRIC_DIALOG_ADVANCED");
    }

    /**
     * Tests that the sensorIds retrieved via {@link BiometricManager#getSensorProperties()} and the
     * dumpsys are consistent with each other.
     */
    @ApiTest(apis = {"android.hardware.biometrics." + "BiometricManager#getSensorProperties"})
    @Test
    public void testSensorPropertiesAndDumpsysMatch() throws Exception {
        assumeTrue(Utils.isFirstApiLevel29orGreater());
        final BiometricServiceState state = getCurrentState();

        assertEquals(mSensorProperties.size(), state.mSensorStates.sensorStates.size());
        for (SensorProperties prop : mSensorProperties) {
            assertTrue(state.mSensorStates.sensorStates.containsKey(prop.getSensorId()));
        }
    }

    /**
     * Tests that the PackageManager features and biometric dumpsys are consistent with each other.
     */
    @ApiTest(apis = {
            "android.content.pm."
                    + "PackageManager#FEATURE_FINGERPRINT",
            "android.content.pm."
                    + "PackageManager#FEATURE_FACE"})
    @Test
    public void testPackageManagerAndDumpsysMatch() throws Exception {
        assumeTrue(Utils.isFirstApiLevel29orGreater());
        final BiometricServiceState state = getCurrentState();
        final PackageManager pm = mContext.getPackageManager();
        if (mSensorProperties.isEmpty()) {
            assertTrue(state.mSensorStates.sensorStates.isEmpty());

            final File initGsiRc = new File("/system/system_ext/etc/init/init.gsi.rc");
            if (!initGsiRc.exists()) {
                assertFalse(pm.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT));
                assertFalse(pm.hasSystemFeature(PackageManager.FEATURE_FACE));
                assertFalse(pm.hasSystemFeature(PackageManager.FEATURE_IRIS));
            }

            assertTrue(state.mSensorStates.sensorStates.isEmpty());
        } else {
            assertEquals(pm.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT),
                    state.mSensorStates.containsModality(SensorStateProto.FINGERPRINT));
            assertEquals(pm.hasSystemFeature(PackageManager.FEATURE_FACE),
                    state.mSensorStates.containsModality(SensorStateProto.FACE));
            assertEquals(pm.hasSystemFeature(PackageManager.FEATURE_IRIS),
                    state.mSensorStates.containsModality(SensorStateProto.IRIS));
        }
    }

    @ApiTest(apis = {
            "android.hardware.biometrics."
                    + "BiometricManager#canAuthenticate"})
    @Test
    public void testCanAuthenticate_whenNoSensors() {
        assumeTrue(Utils.isFirstApiLevel29orGreater());
        if (mSensorProperties.isEmpty()) {
            assertEquals(BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
                    mBiometricManager.canAuthenticate(Authenticators.BIOMETRIC_WEAK));
            assertEquals(BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
                    mBiometricManager.canAuthenticate(Authenticators.BIOMETRIC_STRONG));
        }
    }

    @ApiTest(apis = {
            "android.hardware.biometrics."
                    + "BiometricPrompt.Builder#setConfirmationRequired",
            "android.hardware.biometrics."
                    + "BiometricPrompt#authenticate",
            "android.hardware.biometrics."
                    + "BiometricPrompt#isConfirmationRequired"})
    @Test
    public void testIsConfirmationRequired() throws Exception {
        assumeTrue(Utils.isFirstApiLevel29orGreater());
        for (SensorProperties props : mSensorProperties) {
            if (props.getSensorStrength() == SensorProperties.STRENGTH_CONVENIENCE) {
                continue;
            }

            Log.d(TAG, "testIsConfirmationRequired, sensor: " + props.getSensorId());

            try (BiometricTestSession session =
                         mBiometricManager.createTestSession(props.getSensorId())) {

                setUpNonConvenienceSensorEnrollment(props, session);

                BiometricPrompt.AuthenticationCallback callback =
                        mock(BiometricPrompt.AuthenticationCallback.class);
                BiometricPrompt prompt = showDefaultBiometricPrompt(props.getSensorId(), callback,
                        new CancellationSignal());

                assertTrue(prompt.isConfirmationRequired());
                successfullyAuthenticate(session, Utils.getUserId(), callback);
            }
        }
    }

    @ApiTest(apis = {
            "android.hardware.biometrics."
                    + "BiometricPrompt.Builder#setAllowedAuthenticators",
            "android.hardware.biometrics."
                    + "BiometricPrompt#authenticate",
            "android.hardware.biometrics."
                    + "BiometricPrompt#getAllowedAuthenticators"})
    @Test
    public void testSetAllowedAuthenticators_weakBiometric() {
        testSetAllowedAuthenticators(Authenticators.BIOMETRIC_WEAK);
    }

    @ApiTest(apis = {
            "android.hardware.biometrics."
                    + "BiometricPrompt.Builder#setAllowedAuthenticators",
            "android.hardware.biometrics."
                    + "BiometricPrompt#authenticate",
            "android.hardware.biometrics."
                    + "BiometricPrompt#getAllowedAuthenticators"})
    @Test
    public void testSetAllowedAuthenticators_strongBiometric() {
        testSetAllowedAuthenticators(Authenticators.BIOMETRIC_STRONG);
    }

    @ApiTest(apis = {
            "android.hardware.biometrics."
                    + "BiometricPrompt.Builder#setAllowedAuthenticators",
            "android.hardware.biometrics."
                    + "BiometricPrompt#authenticate",
            "android.hardware.biometrics."
                    + "BiometricPrompt#getAllowedAuthenticators"})
    @Test
    public void testSetAllowedAuthenticators_credential() {
        testSetAllowedAuthenticators(Authenticators.DEVICE_CREDENTIAL);
    }

    @ApiTest(apis = {
            "android.hardware.biometrics."
                    + "BiometricPrompt.Builder#setAllowedAuthenticators",
            "android.hardware.biometrics."
                    + "BiometricPrompt#authenticate",
            "android.hardware.biometrics."
                    + "BiometricPrompt#getAllowedAuthenticators"})
    @Test
    public void testSetAllowedAuthenticators_weakBiometricAndCredential() {
        testSetAllowedAuthenticators(
                Authenticators.BIOMETRIC_WEAK | Authenticators.DEVICE_CREDENTIAL);
    }

    @ApiTest(apis = {
            "android.hardware.biometrics."
                    + "BiometricPrompt.Builder#setAllowedAuthenticators",
            "android.hardware.biometrics."
                    + "BiometricPrompt#authenticate",
            "android.hardware.biometrics."
                    + "BiometricPrompt#getAllowedAuthenticators"})
    @Test
    public void testSetAllowedAuthenticators_StrongBiometricAndCredential() {
        testSetAllowedAuthenticators(
                Authenticators.BIOMETRIC_STRONG | Authenticators.DEVICE_CREDENTIAL);
    }

    @ApiTest(
            apis = {
                "android.hardware.biometrics.BiometricPrompt.Builder#setAllowedAuthenticators",
                "android.hardware.biometrics.BiometricPrompt#authenticate",
            })
    @RequiresFlagsEnabled(Flags.FLAG_IDENTITY_CHECK_API)
    @Test
    public void testSetAllowedAuthenticators_identityCheck() {
        testSetAllowedAuthenticators(IDENTITY_CHECK);
    }

    @ApiTest(
            apis = {
                "android.hardware.biometrics.BiometricPrompt.Builder#setAllowedAuthenticators",
                "android.hardware.biometrics.BiometricPrompt#authenticate",
            })
    @RequiresFlagsEnabled(Flags.FLAG_IDENTITY_CHECK_API)
    @Test
    public void testSetAllowedAuthenticators_identityCheckAndDeviceCredential() {
        testSetAllowedAuthenticators(IDENTITY_CHECK | DEVICE_CREDENTIAL);
    }

    @ApiTest(
            apis = {
                "android.hardware.biometrics.BiometricPrompt.Builder#setAllowedAuthenticators",
                "android.hardware.biometrics.BiometricPrompt#authenticate",
            })
    @RequiresFlagsEnabled(Flags.FLAG_IDENTITY_CHECK_API)
    @Test
    public void testSetAllowedAuthenticators_identityCheckAndBiometricStrong() {
        testSetAllowedAuthenticators(IDENTITY_CHECK | BIOMETRIC_STRONG);
    }

    @ApiTest(
            apis = {
                "android.hardware.biometrics.BiometricPrompt.Builder#setAllowedAuthenticators",
                "android.hardware.biometrics.BiometricPrompt#authenticate",
            })
    @RequiresFlagsEnabled(Flags.FLAG_IDENTITY_CHECK_API)
    @Test
    public void testSetAllowedAuthenticators_identityCheckAndBiometricWeak() {
        testSetAllowedAuthenticators(IDENTITY_CHECK | BIOMETRIC_WEAK);
    }

    @ApiTest(
            apis = {
                "android.hardware.biometrics.BiometricPrompt.Builder#setAllowedAuthenticators",
                "android.hardware.biometrics.BiometricPrompt#authenticate",
            })
    @RequiresFlagsEnabled({Flags.FLAG_IDENTITY_CHECK_API, Flags.FLAG_IDENTITY_CHECK_TEST_API})
    @Test
    public void
            testBiometricAuth_identityCheckAndDeviceCredential_identityCheckActive_strongSensorAuthenticates()
                    throws Exception {
        assumeTrue(Utils.isFirstApiLevel29orGreater());

        for (SensorProperties props : mSensorProperties) {
            if (props.getSensorStrength() != STRENGTH_STRONG) {
                continue;
            }

            try (BiometricTestSession session =
                            mBiometricManager.createTestSession(props.getSensorId());
                    CredentialSession credentialSession = new CredentialSession()) {
                enableIdentityCheck();

                final BiometricPrompt.AuthenticationCallback authenticationCallback =
                        mock(BiometricPrompt.AuthenticationCallback.class);
                credentialSession.setCredential();
                enrollForSensor(session, props.getSensorId());
                showBiometricPromptWithAuthenticators(
                        IDENTITY_CHECK | DEVICE_CREDENTIAL, authenticationCallback);

                waitForState(STATE_AUTH_STARTED_UI_SHOWING);

                successfullyAuthenticate(session, Utils.getUserId(), authenticationCallback);
            }
        }
    }

    @ApiTest(
            apis = {
                "android.hardware.biometrics.BiometricPrompt.Builder#setAllowedAuthenticators",
                "android.hardware.biometrics.BiometricPrompt#authenticate",
            })
    @RequiresFlagsEnabled({
        Flags.FLAG_IDENTITY_CHECK_API,
        Flags.FLAG_IDENTITY_CHECK_TEST_API,
        Flags.FLAG_BP_FALLBACK_OPTIONS
    })
    @Test
    public void
            testBiometricAuth_identityCheckAndDeviceCredential_identityCheckActive_negativeButtonClicked()
                    throws Exception {
        assumeTrue(Utils.isFirstApiLevel29orGreater());

        for (SensorProperties props : mSensorProperties) {
            if (props.getSensorStrength() != STRENGTH_STRONG) {
                continue;
            }

            try (BiometricTestSession session =
                            mBiometricManager.createTestSession(props.getSensorId());
                    CredentialSession credentialSession = new CredentialSession()) {
                enableIdentityCheck();

                final BiometricPrompt.AuthenticationCallback authenticationCallback =
                        mock(BiometricPrompt.AuthenticationCallback.class);
                credentialSession.setCredential();
                enrollForSensor(session, props.getSensorId());
                showBiometricPromptWithAuthenticators(
                        IDENTITY_CHECK | DEVICE_CREDENTIAL, authenticationCallback);

                assertThat(findView(BUTTON_ID_USE_CREDENTIAL)).isNull();

                waitForState(STATE_AUTH_STARTED_UI_SHOWING);
                findAndPressButton(BUTTON_ID_FALLBACK);
                waitForState(STATE_AUTH_IDLE);

                // Clicking the negative button should not trigger the callback
                verifyNoMoreInteractions(authenticationCallback);
            }
        }
    }

    @ApiTest(
            apis = {
                "android.hardware.biometrics.BiometricPrompt.Builder#setAllowedAuthenticators",
                "android.hardware.biometrics.BiometricPrompt#authenticate",
            })
    @RequiresFlagsEnabled({Flags.FLAG_IDENTITY_CHECK_API, Flags.FLAG_IDENTITY_CHECK_TEST_API})
    @Test
    public void testBiometricAuth_identityCheckAndDeviceCredential_identityCheckInactive()
            throws Exception {
        assumeTrue(Utils.isFirstApiLevel29orGreater());

        for (SensorProperties props : mSensorProperties) {
            if (props.getSensorStrength() != STRENGTH_STRONG) {
                continue;
            }

            try (BiometricTestSession session =
                            mBiometricManager.createTestSession(props.getSensorId());
                    CredentialSession credentialSession = new CredentialSession()) {
                disableIdentityCheck();

                final BiometricPrompt.AuthenticationCallback authenticationCallback =
                        mock(BiometricPrompt.AuthenticationCallback.class);

                credentialSession.setCredential();
                enrollForSensor(session, props.getSensorId());
                showBiometricPromptWithAuthenticators(
                        IDENTITY_CHECK | DEVICE_CREDENTIAL, authenticationCallback);

                waitForState(STATE_AUTH_STARTED_UI_SHOWING);

                successfullyEnterCredential();

                verify(authenticationCallback).onAuthenticationSucceeded(any());
            }
        }
    }

    @ApiTest(
            apis = {
                "android.hardware.biometrics.BiometricPrompt.Builder#setAllowedAuthenticators",
                "android.hardware.biometrics.BiometricPrompt#authenticate",
            })
    @RequiresFlagsEnabled({Flags.FLAG_IDENTITY_CHECK_API, Flags.FLAG_IDENTITY_CHECK_TEST_API})
    @Test
    public void testBiometricAuth_identityCheckAndBiometricWeak_identityCheckActive()
            throws Exception {
        if (!hasWeakAndStrongSensor()) {
            Log.d(TAG, "Skipping test as device does not have weak and strong sensor");
            return;
        }

        assumeTrue(Utils.isFirstApiLevel29orGreater());

        try (CredentialSession credentialSession = new CredentialSession();
                TestSessionList sessionList = new TestSessionList(this)) {
            enableIdentityCheck();

            final BiometricPrompt.AuthenticationCallback authenticationCallback =
                    mock(BiometricPrompt.AuthenticationCallback.class);
            final int strongSensorId = getStrongSensorProperties().getSensorId();
            enrollForRequestedAuthenticators(sessionList, BIOMETRIC_WEAK | BIOMETRIC_STRONG);
            credentialSession.setCredential();

            showBiometricPromptWithAuthenticators(
                    IDENTITY_CHECK | BIOMETRIC_WEAK, authenticationCallback);
            waitForState(STATE_AUTH_STARTED_UI_SHOWING);

            assertThat(getSensorStates().sensorStates.get(getWeakSensorProperties().getSensorId())
                    .isBusy()).isFalse();
            assertThat(getSensorStates().sensorStates.get(strongSensorId).isBusy()).isTrue();

            successfullyAuthenticate(
                    Objects.requireNonNull(sessionList.find(strongSensorId)),
                    Utils.getUserId(),
                    authenticationCallback);
        }
    }

    @ApiTest(
            apis = {
                "android.hardware.biometrics.BiometricPrompt.Builder#setAllowedAuthenticators",
                "android.hardware.biometrics.BiometricPrompt#authenticate",
            })
    @RequiresFlagsEnabled({Flags.FLAG_IDENTITY_CHECK_API, Flags.FLAG_IDENTITY_CHECK_TEST_API})
    @Test
    public void testBiometricAuth_identityCheckAndBiometricWeak_identityCheckInactive()
            throws Exception {
        if (!hasWeakAndStrongSensor()) {
            Log.d(TAG, "Skipping test as device does not have weak and strong sensor");
            return;
        }

        assumeTrue(Utils.isFirstApiLevel29orGreater());

        try (CredentialSession credentialSession = new CredentialSession();
                TestSessionList sessionList = new TestSessionList(this)) {
            disableIdentityCheck();

            final BiometricPrompt.AuthenticationCallback authenticationCallback =
                    mock(BiometricPrompt.AuthenticationCallback.class);

            enrollForRequestedAuthenticators(sessionList, BIOMETRIC_WEAK | BIOMETRIC_STRONG);
            credentialSession.setCredential();

            showBiometricPromptWithAuthenticators(
                    IDENTITY_CHECK | BIOMETRIC_WEAK, authenticationCallback);
            waitForState(STATE_AUTH_STARTED_UI_SHOWING);

            successfullyAuthenticate(
                    Objects.requireNonNull(
                            sessionList.find(getWeakSensorProperties().getSensorId())),
                    Utils.getUserId(),
                    authenticationCallback);
        }
    }

    @ApiTest(
            apis = {
                "android.hardware.biometrics.BiometricPrompt.Builder#setAllowedAuthenticators",
                "android.hardware.biometrics.BiometricPrompt#authenticate",
            })
    @RequiresFlagsEnabled({
        Flags.FLAG_IDENTITY_CHECK_API,
        Flags.FLAG_IDENTITY_CHECK_TEST_API,
        Flags.FLAG_IDENTITY_CHECK_ALL_SURFACES,
        Flags.FLAG_BP_FALLBACK_OPTIONS
    })
    @Test
    public void testBiometricAuth_biometricWeakAndDeviceCredential_identityCheckActive()
            throws Exception {
        if (!hasWeakAndStrongSensor()) {
            Log.d(TAG, "Skipping test as device does not have weak and strong sensor");
            return;
        }

        assumeTrue(Utils.isFirstApiLevel29orGreater());

        try (CredentialSession credentialSession = new CredentialSession();
                TestSessionList sessionList = new TestSessionList(this)) {
            enableIdentityCheck();

            final BiometricPrompt.AuthenticationCallback authenticationCallback =
                    mock(BiometricPrompt.AuthenticationCallback.class);
            final int strongSensorId = getStrongSensorProperties().getSensorId();
            enrollForRequestedAuthenticators(sessionList, BIOMETRIC_WEAK | BIOMETRIC_STRONG);
            credentialSession.setCredential();

            showBiometricPromptWithAuthenticators(
                    BIOMETRIC_WEAK | DEVICE_CREDENTIAL, authenticationCallback);
            waitForState(STATE_AUTH_STARTED_UI_SHOWING);

            assertThat(getSensorStates().sensorStates.get(getWeakSensorProperties().getSensorId())
                    .isBusy()).isFalse();
            assertThat(getSensorStates().sensorStates.get(strongSensorId).isBusy()).isTrue();
            assertThat(findView(BUTTON_ID_USE_CREDENTIAL)).isNull();

            successfullyAuthenticate(
                    Objects.requireNonNull(sessionList.find(strongSensorId)),
                    Utils.getUserId(),
                    authenticationCallback);
        }
    }

    @ApiTest(
            apis = {
                "android.hardware.biometrics.BiometricPrompt.Builder#setAllowedAuthenticators",
                "android.hardware.biometrics.BiometricPrompt#authenticate",
            })
    @RequiresFlagsEnabled({
        Flags.FLAG_IDENTITY_CHECK_API,
        Flags.FLAG_IDENTITY_CHECK_TEST_API,
        Flags.FLAG_IDENTITY_CHECK_ALL_SURFACES,
        Flags.FLAG_BP_FALLBACK_OPTIONS
    })
    @Test
    public void testBiometricAuth_biometricStrongAndDeviceCredential_identityCheckActive()
            throws Exception {
        if (!hasWeakAndStrongSensor()) {
            Log.d(TAG, "Skipping test as device does not have weak and strong sensor");
            return;
        }

        assumeTrue(Utils.isFirstApiLevel29orGreater());

        try (CredentialSession credentialSession = new CredentialSession();
                TestSessionList sessionList = new TestSessionList(this)) {
            enableIdentityCheck();

            final BiometricPrompt.AuthenticationCallback authenticationCallback =
                    mock(BiometricPrompt.AuthenticationCallback.class);
            final int strongSensorId = getStrongSensorProperties().getSensorId();
            enrollForRequestedAuthenticators(sessionList, BIOMETRIC_STRONG);
            credentialSession.setCredential();

            showBiometricPromptWithAuthenticators(
                    BIOMETRIC_STRONG | DEVICE_CREDENTIAL, authenticationCallback);
            waitForState(STATE_AUTH_STARTED_UI_SHOWING);

            assertThat(findView(BUTTON_ID_USE_CREDENTIAL)).isNull();
            successfullyAuthenticate(
                    Objects.requireNonNull(sessionList.find(strongSensorId)),
                    Utils.getUserId(),
                    authenticationCallback);
        }
    }

    @ApiTest(
            apis = {
                "android.hardware.biometrics.BiometricPrompt.Builder#setAllowedAuthenticators",
                "android.hardware.biometrics.BiometricPrompt#authenticate",
            })
    @RequiresFlagsEnabled({
        Flags.FLAG_IDENTITY_CHECK_API,
        Flags.FLAG_IDENTITY_CHECK_TEST_API,
        Flags.FLAG_IDENTITY_CHECK_ALL_SURFACES,
        Flags.FLAG_BP_FALLBACK_OPTIONS
    })
    @Test
    public void testBiometricAuth_deviceCredential_identityCheckActive() throws Exception {
        if (!hasWeakAndStrongSensor()) {
            Log.d(TAG, "Skipping test as device does not have weak and strong sensor");
            return;
        }

        assumeTrue(Utils.isFirstApiLevel29orGreater());

        try (CredentialSession credentialSession = new CredentialSession()) {
            enableIdentityCheck();

            final BiometricPrompt.AuthenticationCallback authenticationCallback =
                    mock(BiometricPrompt.AuthenticationCallback.class);
            credentialSession.setCredential();

            showBiometricPromptWithAuthenticators(DEVICE_CREDENTIAL, authenticationCallback);
            waitForState(STATE_AUTH_STARTED_UI_SHOWING);

            successfullyEnterCredential();
        } finally {
            mBiometricManager.setIdentityCheckTestStatus(
                    new IdentityCheckStatus.Builder()
                            .setIdentityCheckValueForTestAvailable(false)
                            .setIdentityCheckActive(false)
                            .build());
        }
    }

    private void enrollForRequestedAuthenticators(TestSessionList sessionList, int authenticators)
            throws Exception {
        if ((authenticators & BIOMETRIC_WEAK) == BIOMETRIC_WEAK) {
            final int weakSensorId = getWeakSensorProperties().getSensorId();
            final BiometricTestSession weakSensorSession =
                    mBiometricManager.createTestSession(weakSensorId);
            sessionList.put(weakSensorId, weakSensorSession);
            enrollForSensor(weakSensorSession, weakSensorId);
        }
        if ((authenticators & BIOMETRIC_STRONG) == BIOMETRIC_STRONG) {
            final int strongSensorId = getStrongSensorProperties().getSensorId();
            final BiometricTestSession strongSensorSession =
                    mBiometricManager.createTestSession(strongSensorId);
            sessionList.put(strongSensorId, strongSensorSession);
            enrollForSensor(strongSensorSession, strongSensorId);
        }
    }

    private SensorProperties getWeakSensorProperties() {
        return mSensorProperties.stream()
                .filter(sensorProperties -> sensorProperties.getSensorStrength() == STRENGTH_WEAK)
                .findFirst()
                .orElse(null);
    }

    private SensorProperties getStrongSensorProperties() {
        return mSensorProperties.stream()
                .filter(sensorProperties -> sensorProperties.getSensorStrength() == STRENGTH_STRONG)
                .findFirst()
                .orElse(null);
    }

    private boolean hasWeakAndStrongSensor() {
        return mSensorProperties.stream()
                        .anyMatch(
                                sensorProperties ->
                                        sensorProperties.getSensorStrength() == STRENGTH_STRONG)
                && mSensorProperties.stream()
                        .anyMatch(
                                sensorProperties ->
                                        sensorProperties.getSensorStrength() == STRENGTH_WEAK);
    }

    private void enableIdentityCheck() {
        mBiometricManager.setIdentityCheckTestStatus(
                new IdentityCheckStatus.Builder()
                        .setIdentityCheckValueForTestAvailable(true)
                        .setIdentityCheckActive(true)
                        .build());
    }

    private void disableIdentityCheck() {
        mBiometricManager.setIdentityCheckTestStatus(
                new IdentityCheckStatus.Builder()
                        .setIdentityCheckValueForTestAvailable(true)
                        .setIdentityCheckActive(false)
                        .build());
    }

    private void testSetAllowedAuthenticators(int authenticators) {
        assumeTrue(Utils.isFirstApiLevel29orGreater());
        BiometricPrompt prompt = showBiometricPromptWithAuthenticators(authenticators);
        assertEquals(authenticators, prompt.getAllowedAuthenticators());
    }

    @ApiTest(apis = {
            "android.hardware.biometrics."
                    + "BiometricManager#canAuthenticate",
            "android.hardware.biometrics."
                    + "BiometricPrompt.Builder#setAllowedAuthenticators",
            "android.hardware.biometrics."
                    + "BiometricPrompt#authenticate"})
    @Test
    public void testInvalidInputs() {
        assumeTrue(Utils.isFirstApiLevel29orGreater());

        //TODO(b/347123256): Update once mandatory biometrics becomes public
        final int mandatoryBiometricsBit = 1 << 16;
        for (int i = 0; i < 32; i++) {
            final int authenticator = 1 << i;
            // If it's a public constant, no need to test
            if (Utils.isPublicAuthenticatorConstant(authenticator)) {
                continue;
            }

            if (authenticator == mandatoryBiometricsBit && !Flags.identityCheckApi()) {
                continue;
            }

            // Test canAuthenticate(int)
            assertThrows("Invalid authenticator in canAuthenticate must throw exception: "
                            + authenticator,
                    Exception.class,
                    () -> mBiometricManager.canAuthenticate(authenticator));

            // Test BiometricPrompt
            assertThrows("Invalid authenticator in authenticate must throw exception: "
                            + authenticator,
                    Exception.class,
                    () -> showBiometricPromptWithAuthenticators(authenticator));
        }
    }

    /**
     * When device credential is not enrolled, check the behavior for
     * 1) BiometricManager#canAuthenticate(DEVICE_CREDENTIAL)
     * 2) BiometricPrompt#setAllowedAuthenticators(DEVICE_CREDENTIAL)
     * 3) @deprecated BiometricPrompt#setDeviceCredentialAllowed(true)
     */
    @ApiTest(apis = {
            "android.hardware.biometrics."
                    + "BiometricManager#canAuthenticate",
            "android.hardware.biometrics."
                    + "BiometricPrompt.Builder#setAllowedAuthenticators",
            "android.hardware.biometrics."
                    + "BiometricPrompt.Builder#setDeviceCredentialAllowed",
            "android.hardware.biometrics."
                    + "BiometricPrompt#authenticate"})
    @Test
    public void testWhenCredentialNotEnrolled() throws Exception {
        assumeTrue(Utils.isFirstApiLevel29orGreater());
        // First case above
        final int result = mBiometricManager.canAuthenticate(BiometricManager
                .Authenticators.DEVICE_CREDENTIAL);
        assertEquals(BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED, result);

        // Second case above
        BiometricPrompt.AuthenticationCallback callback =
                mock(BiometricPrompt.AuthenticationCallback.class);
        showCredentialOnlyBiometricPrompt(callback, new CancellationSignal(),
                false /* shouldShow */);
        verify(callback).onAuthenticationError(
                eq(BiometricPrompt.BIOMETRIC_ERROR_NO_DEVICE_CREDENTIAL),
                any());

        // Third case above. Since the deprecated API is intended to allow credential in addition
        // to biometrics, we should be receiving BIOMETRIC_ERROR_NO_BIOMETRICS.
        final boolean noSensors = mSensorProperties.isEmpty();
        int expectedError;
        if (noSensors) {
            expectedError = BiometricPrompt.BIOMETRIC_ERROR_NO_DEVICE_CREDENTIAL;
        } else if (hasOnlyConvenienceSensors()) {
            expectedError = BiometricPrompt.BIOMETRIC_ERROR_HW_NOT_PRESENT;
        } else {
            expectedError = BiometricPrompt.BIOMETRIC_ERROR_NO_BIOMETRICS;
        }
        callback = mock(BiometricPrompt.AuthenticationCallback.class);
        showDeviceCredentialAllowedBiometricPrompt(callback, new CancellationSignal(),
                false /* shouldShow */);
        verify(callback).onAuthenticationError(
                eq(expectedError),
                any());
    }

    private boolean hasOnlyConvenienceSensors() {
        for (SensorProperties sensor : mSensorProperties) {
            if (sensor.getSensorStrength() != SensorProperties.STRENGTH_CONVENIENCE) {
                return false;
            }
        }
        return true;
    }

    /**
     * When device credential is enrolled, check the behavior for
     * 1) BiometricManager#canAuthenticate(DEVICE_CREDENTIAL)
     * 2a) Successfully authenticating BiometricPrompt#setAllowedAuthenticators(DEVICE_CREDENTIAL)
     * 2b) Cancelling authentication for the above
     * 3a) @deprecated BiometricPrompt#setDeviceCredentialALlowed(true)
     * 3b) Cancelling authentication for the above
     * 4) Cancelling auth for options 2) and 3)
     */
    @ApiTest(apis = {
            "android.hardware.biometrics."
                    + "BiometricManager#canAuthenticate",
            "android.hardware.biometrics."
                    + "BiometricPrompt.Builder#setAllowedAuthenticators",
            "android.hardware.biometrics."
                    + "BiometricPrompt.Builder#setDeviceCredentialAllowed",
            "android.hardware.biometrics."
                    + "BiometricPrompt.AuthenticationCallback#onAuthenticationSucceeded",
            "android.hardware.biometrics."
                    + "BiometricPrompt#authenticate"})
    @Test
    public void testWhenCredentialEnrolled() throws Exception {
        assumeTrue(Utils.isFirstApiLevel29orGreater());
        //TODO: b/331955301 need to update Auto biometric UI
        assumeFalse(isCar());
        try (CredentialSession session = new CredentialSession()) {
            session.setCredential();

            // First case above
            final int result = mBiometricManager.canAuthenticate(BiometricManager
                    .Authenticators.DEVICE_CREDENTIAL);
            assertEquals(BiometricManager.BIOMETRIC_SUCCESS, result);

            // 2a above
            BiometricPrompt.AuthenticationCallback callback =
                    mock(BiometricPrompt.AuthenticationCallback.class);
            showCredentialOnlyBiometricPrompt(callback, new CancellationSignal(),
                    true /* shouldShow */);
            successfullyEnterCredential();
            verify(callback).onAuthenticationSucceeded(any());

            // 2b above
            CancellationSignal cancel = new CancellationSignal();
            callback = mock(BiometricPrompt.AuthenticationCallback.class);
            showCredentialOnlyBiometricPrompt(callback, cancel, true /* shouldShow */);
            cancelAuthentication(cancel);
            verify(callback).onAuthenticationError(eq(BiometricPrompt.BIOMETRIC_ERROR_CANCELED),
                    any());

            // 3a above
            callback = mock(BiometricPrompt.AuthenticationCallback.class);
            showDeviceCredentialAllowedBiometricPrompt(callback, new CancellationSignal(),
                    true /* shouldShow */);
            successfullyEnterCredential();
            verify(callback).onAuthenticationSucceeded(any());

            // 3b above
            cancel = new CancellationSignal();
            callback = mock(BiometricPrompt.AuthenticationCallback.class);
            showDeviceCredentialAllowedBiometricPrompt(callback, cancel, true /* shouldShow */);
            cancelAuthentication(cancel);
            verify(callback).onAuthenticationError(eq(BiometricPrompt.BIOMETRIC_ERROR_CANCELED),
                    any());
        }
    }

    @CddTest(requirements = {"7.3.10/C-4-2"})
    @ApiTest(apis = {
            "android.hardware.biometrics."
                    + "BiometricManager#canAuthenticate",
            "android.hardware.biometrics."
                    + "BiometricPrompt.AuthenticationCallback#onAuthenticationError"})
    @Test
    public void testSimpleBiometricAuth_convenience() throws Exception {
        assumeTrue(Utils.isFirstApiLevel29orGreater());
        for (SensorProperties props : mSensorProperties) {
            if (props.getSensorStrength() != SensorProperties.STRENGTH_CONVENIENCE) {
                continue;
            }

            Log.d(TAG, "testSimpleBiometricAuth_convenience, sensor: " + props.getSensorId());

            try (BiometricTestSession session =
                         mBiometricManager.createTestSession(props.getSensorId())) {

                // Let's just try to check+auth against WEAK, since CONVENIENCE isn't even
                // exposed to public BiometricPrompt APIs (as intended).
                final int authenticatorStrength = Authenticators.BIOMETRIC_WEAK;
                assertNotEquals("Sensor: " + props.getSensorId()
                                + ", strength: " + props.getSensorStrength(),
                        BiometricManager.BIOMETRIC_SUCCESS,
                        mBiometricManager.canAuthenticate(authenticatorStrength));

                enrollForSensor(session, props.getSensorId());

                assertNotEquals("Sensor: " + props.getSensorId()
                                + ", strength: " + props.getSensorStrength(),
                        BiometricManager.BIOMETRIC_SUCCESS,
                        mBiometricManager.canAuthenticate(authenticatorStrength));

                BiometricPrompt.AuthenticationCallback callback =
                        mock(BiometricPrompt.AuthenticationCallback.class);

                showDefaultBiometricPrompt(props.getSensorId(), callback,
                        new CancellationSignal());

                verify(callback).onAuthenticationError(anyInt(), any());
            }
        }
    }

    /**
     * Tests that the values specified through the public APIs are shown on the BiometricPrompt UI
     * when biometric auth is requested.
     *
     * Upon successful authentication, checks that the result is
     * {@link BiometricPrompt#AUTHENTICATION_RESULT_TYPE_BIOMETRIC}
     *
     * TODO(b/236763921): fix this test and unignore.
     */
    @Ignore
    @CddTest(requirements = {"7.3.10/C-4-2"})
    @ApiTest(apis = {
            "android.hardware.biometrics."
                    + "BiometricManager#canAuthenticate",
            "android.hardware.biometrics."
                    + "BiometricPrompt.Builder#setTitle",
            "android.hardware.biometrics."
                    + "BiometricPrompt.Builder#setSubtitle",
            "android.hardware.biometrics."
                    + "BiometricPrompt.Builder#setDescription",
            "android.hardware.biometrics."
                    + "BiometricPrompt.Builder#setNegativeButton",
            "android.hardware.biometrics."
                    + "BiometricPrompt.AuthenticationCallback#onAuthenticationSucceeded",
            "android.hardware.biometrics."
                    + "BiometricPrompt#authenticate",
            "android.hardware.biometrics."
                    + "BiometricPrompt.AuthenticationResult#getAuthenticationType"})
    @Test
    public void testSimpleBiometricAuth_nonConvenience() throws Exception {
        assumeTrue(Utils.isFirstApiLevel29orGreater());
        for (SensorProperties props : mSensorProperties) {
            if (props.getSensorStrength() == SensorProperties.STRENGTH_CONVENIENCE) {
                continue;
            }

            Log.d(TAG, "testSimpleBiometricAuth, sensor: " + props.getSensorId());

            try (BiometricTestSession session =
                         mBiometricManager.createTestSession(props.getSensorId())) {

                setUpNonConvenienceSensorEnrollment(props, session);

                final Random random = new Random();
                final String randomTitle = String.valueOf(random.nextInt(10000));
                final String randomSubtitle = String.valueOf(random.nextInt(10000));
                final String randomDescription = String.valueOf(random.nextInt(10000));
                final String randomNegativeButtonText = String.valueOf(random.nextInt(10000));

                BiometricPrompt.AuthenticationCallback callback =
                        mock(BiometricPrompt.AuthenticationCallback.class);
                showDefaultBiometricPromptWithContents(
                        props.getSensorId(),
                        Utils.getUserId(),
                        true /* requireConfirmation */,
                        callback,
                        randomTitle,
                        randomSubtitle,
                        randomDescription,
                        null /* contentView */,
                        randomNegativeButtonText);

                final UiObject2 actualTitle = findView(TITLE_VIEW);
                final UiObject2 actualSubtitle = findView(SUBTITLE_VIEW);
                final UiObject2 actualDescription = findView(DESCRIPTION_VIEW);
                final UiObject2 actualNegativeButton = findView(BUTTON_ID_NEGATIVE);
                assertEquals(randomTitle, actualTitle.getText());
                assertEquals(randomSubtitle, actualSubtitle.getText());
                assertEquals(randomDescription, actualDescription.getText());
                assertEquals(randomNegativeButtonText, actualNegativeButton.getText());

                // Finish auth
                successfullyAuthenticate(session, Utils.getUserId(), callback);
            }
        }
    }

    /**
     * Tests that the values specified through the public APIs are shown on the BiometricPrompt UI
     * when credential auth is requested.
     *
     * Upon successful authentication, checks that the result is
     * {@link BiometricPrompt#AUTHENTICATION_RESULT_TYPE_BIOMETRIC}
     */
    @ApiTest(apis = {
            "android.hardware.biometrics."
                    + "BiometricPrompt.Builder#setTitle",
            "android.hardware.biometrics."
                    + "BiometricPrompt.Builder#setSubtitle",
            "android.hardware.biometrics."
                    + "BiometricPrompt.Builder#setDescription",
            "android.hardware.biometrics."
                    + "BiometricPrompt#authenticate",
            "android.hardware.biometrics."
                    + "BiometricPrompt.AuthenticationResult#getAuthenticationType"})
    @Test
    public void testSimpleCredentialAuth() throws Exception {
        assumeTrue(Utils.isFirstApiLevel29orGreater());
        //TODO: b/331955301 need to update Auto biometric UI
        assumeFalse(isCar());
        try (CredentialSession session = new CredentialSession()) {
            session.setCredential();

            final Random random = new Random();
            final String randomTitle = String.valueOf(random.nextInt(10000));
            final String randomSubtitle = String.valueOf(random.nextInt(10000));
            final String randomDescription = String.valueOf(random.nextInt(10000));

            CountDownLatch latch = new CountDownLatch(1);
            BiometricPrompt.AuthenticationCallback callback =
                    new BiometricPrompt.AuthenticationCallback() {
                        @Override
                        public void onAuthenticationSucceeded(
                                BiometricPrompt.AuthenticationResult result) {
                            assertEquals("Must be TYPE_CREDENTIAL",
                                    BiometricPrompt.AUTHENTICATION_RESULT_TYPE_DEVICE_CREDENTIAL,
                                    result.getAuthenticationType());
                            latch.countDown();
                        }
                    };
            showCredentialOnlyBiometricPromptWithContents(callback, new CancellationSignal(),
                    true /* shouldShow */, randomTitle, randomSubtitle, randomDescription,
                    null /* contentView */);

            // These views aren't available on wear devices.
            if (!isWatch()) {
                final UiObject2 actualTitle = findView(TITLE_VIEW);
                final UiObject2 actualSubtitle = findView(SUBTITLE_VIEW);
                final UiObject2 actualDescription = findView(DESCRIPTION_VIEW);
                assertEquals(randomTitle, actualTitle.getText());
                assertEquals(randomSubtitle, actualSubtitle.getText());
                assertEquals(randomDescription, actualDescription.getText());
            }
            // Finish auth
            successfullyEnterCredential();
            latch.await(3, TimeUnit.SECONDS);
        }
    }

    /**
     * Tests that cancelling auth succeeds, and that ERROR_CANCELED is received.
     */
    @ApiTest(apis = {
            "android.hardware.biometrics."
                    + "BiometricPrompt.AuthenticationCallback#onAuthenticationError"})
    @Test
    public void testBiometricCancellation() throws Exception {
        assumeTrue(Utils.isFirstApiLevel29orGreater());
        for (SensorProperties props : mSensorProperties) {
            if (props.getSensorStrength() == SensorProperties.STRENGTH_CONVENIENCE) {
                continue;
            }

            try (BiometricTestSession session =
                         mBiometricManager.createTestSession(props.getSensorId())) {
                enrollForSensor(session, props.getSensorId());

                BiometricPrompt.AuthenticationCallback callback =
                        mock(BiometricPrompt.AuthenticationCallback.class);
                CancellationSignal cancellationSignal = new CancellationSignal();

                showDefaultBiometricPrompt(props.getSensorId(), callback, cancellationSignal);

                cancelAuthentication(cancellationSignal);
                verify(callback).onAuthenticationError(eq(BiometricPrompt.BIOMETRIC_ERROR_CANCELED),
                        any());
                verifyNoMoreInteractions(callback);
            }
        }
    }

    /**
     * Tests that {@link BiometricManager#getLastAuthenticationTime(int)} result changes
     * appropriately for DEVICE_CREDENTIAL after a PIN unlock.
     */
    @Test
    public void testGetLastAuthenticationTime_unlockWithCorrectDeviceCredential() throws Exception {
        try (CredentialSession credentialSession = new CredentialSession()) {
            credentialSession.setCredential();

            final long startTime = SystemClock.elapsedRealtime();

            credentialSession.verifyCredential();

            // There's a race between the auth token being sent to keystore2 and the
            // getLastAuthenticationTime() call, so retry if we don't get a valid time.
            long lastAuthTime = BiometricManager.BIOMETRIC_NO_AUTHENTICATION;
            for (int i = 0; i < 10; i++) {
                lastAuthTime = mBiometricManager.getLastAuthenticationTime(
                        DEVICE_CREDENTIAL);
                if (lastAuthTime != BiometricManager.BIOMETRIC_NO_AUTHENTICATION) {
                    break;
                }

                Thread.sleep(100);
            }

            assertThat(lastAuthTime).isGreaterThan(startTime);
        }
    }

    /**
     * Tests that {@link BiometricManager#getLastAuthenticationTime(int)} result does not change
     * when an incorrect PIN is entered.
     */
    @Test
    public void testGetLastAuthenticationTime_unlockWithIncorrectDeviceCredential()
            throws Exception {
        try (CredentialSession credentialSession = new CredentialSession()) {
            credentialSession.setCredential();

            final long initialLastAuthTime = mBiometricManager.getLastAuthenticationTime(
                    DEVICE_CREDENTIAL);

            credentialSession.verifyIncorrectCredential();

            long lastAuthTime = mBiometricManager.getLastAuthenticationTime(
                    DEVICE_CREDENTIAL);

            assertThat(lastAuthTime).isEqualTo(initialLastAuthTime);
        }
    }

    /**
     * Tests that {@link BiometricManager#getLastAuthenticationTime(int)} result returns
     * {@link BiometricManager#BIOMETRIC_NO_AUTHENTICATION} if there is no password/PIN set.
     */
    @Test
    public void testGetLastAuthenticationTime_noCredential() throws Exception {
        final long lastAuthTime = mBiometricManager.getLastAuthenticationTime(
                DEVICE_CREDENTIAL);

        assertThat(lastAuthTime).isEqualTo(BiometricManager.BIOMETRIC_NO_AUTHENTICATION);
    }
}
