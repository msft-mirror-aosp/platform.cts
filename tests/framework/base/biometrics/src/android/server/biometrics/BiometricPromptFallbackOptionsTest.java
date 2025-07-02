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

package android.server.biometrics;

import static android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_WEAK;
import static android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL;

import static com.android.server.biometrics.nano.BiometricServiceStateProto.STATE_AUTH_IDLE;
import static com.android.server.biometrics.nano.BiometricServiceStateProto.STATE_AUTH_STARTED_UI_SHOWING;
import static com.android.server.biometrics.nano.BiometricServiceStateProto.STATE_SHOWING_DEVICE_CREDENTIAL;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import android.content.DialogInterface;
import android.hardware.biometrics.BiometricPrompt;
import android.hardware.biometrics.BiometricTestSession;
import android.hardware.biometrics.FallbackOption;
import android.hardware.biometrics.Flags;
import android.hardware.biometrics.SensorProperties;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.server.biometrics.util.Utils;
import android.util.Log;

import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.Executor;

public class BiometricPromptFallbackOptionsTest extends BiometricTestBase {
    private static final String TAG = "BiometricPromptFallbackOptionsTest";
    private static final int MAX_FALLBACK_OPTIONS = 4;
    private static final int TIMEOUT_MS = 3000;

    final BiometricPrompt.AuthenticationCallback mCallback =
            mock(BiometricPrompt.AuthenticationCallback.class);
    final Handler mHandler = new Handler(Looper.getMainLooper());

    @Before
    public void before() {
        assumeTrue(Utils.isFirstApiLevel29orGreater());
    }

    @ApiTest(
            apis = {
                "android.hardware.biometrics.BiometricPrompt.Builder#setAllowedAuthenticators",
                "android.hardware.biometrics.BiometricPrompt#authenticate",
            })
    @RequiresFlagsEnabled({Flags.FLAG_BP_FALLBACK_OPTIONS, Flags.FLAG_ADD_FALLBACK})
    @Test
    public void testFallbackOptions_testDefaultCancel() throws Exception {
        for (SensorProperties props : mSensorProperties) {
            if (props.getSensorStrength() == SensorProperties.STRENGTH_CONVENIENCE) {
                continue;
            }

            Log.d(TAG, "testFallbackOptions_testDefaultCancel, sensor: " + props.getSensorId());

            try (BiometricTestSession session =
                    mBiometricManager.createTestSession(props.getSensorId())) {

                setUpNonConvenienceSensorEnrollment(props, session);

                // Show biometric prompt
                final Executor executor = mHandler::post;
                final BiometricPrompt.Builder builder =
                        new BiometricPrompt.Builder(mContext)
                                .setTitle("Title")
                                .setSubtitle("Subtitle")
                                .setDescription("Description")
                                .setConfirmationRequired(true)
                                .setAllowBackgroundAuthentication(true)
                                .setAllowedSensorIds(
                                        new ArrayList<>(
                                                Collections.singletonList(props.getSensorId())));
                createAndShowPrompt(BIOMETRIC_WEAK, builder, mCallback, executor);

                // Find cancel button
                final UiObject2 cancelButton = findView(BUTTON_ID_CANCEL);
                assertThat(cancelButton).isNotNull();
                assertThat(cancelButton.getVisibleBounds()).isNotNull();

                // Click cancel button
                cancelButton.click();
                mInstrumentation.waitForIdleSync();
                waitForState(STATE_AUTH_IDLE);
            }
        }
    }

    @ApiTest(apis = {"android.hardware.biometrics.BiometricPrompt.Builder#addFallbackOption"})
    @RequiresFlagsEnabled({Flags.FLAG_BP_FALLBACK_OPTIONS, Flags.FLAG_ADD_FALLBACK})
    @Test
    public void testFallbackOptions_testFallbackOptionPageWithCredential() throws Exception {
        for (SensorProperties props : mSensorProperties) {
            if (props.getSensorStrength() == SensorProperties.STRENGTH_CONVENIENCE) {
                continue;
            }

            Log.d(TAG, "testMoreOptionsButton_simpleBiometricAuth, sensor: " + props.getSensorId());

            try (BiometricTestSession session =
                            mBiometricManager.createTestSession(props.getSensorId());
                    CredentialSession credentialSession = new CredentialSession()) {
                credentialSession.setCredential();
                setUpNonConvenienceSensorEnrollment(props, session);

                // Show biometric prompt
                final Executor executor = mHandler::post;
                final BiometricPrompt.Builder builder =
                        new BiometricPrompt.Builder(mContext)
                                .setTitle("Title")
                                .setSubtitle("Subtitle")
                                .setDescription("Description")
                                .setConfirmationRequired(true)
                                .setAllowBackgroundAuthentication(true)
                                .addFallbackOption(
                                        "Fallback 1", 0, executor, (dialog, which) -> {});
                createAndShowPrompt(
                        DEVICE_CREDENTIAL | BIOMETRIC_WEAK, builder, mCallback, executor);

                // Find fallback page button
                final UiObject2 fallbackPageButton = findView(BUTTON_ID_FALLBACK);
                assertThat(fallbackPageButton).isNotNull();
                assertThat(fallbackPageButton.getVisibleBounds()).isNotNull();

                // Click fallback page button
                fallbackPageButton.click();
                mDevice.waitForIdle();

                // Wait for transition between pages
                final BySelector credentialSelector = By.res(FALLBACK_PAGE_CREDENTIAL_BUTTON);
                mDevice.wait(Until.hasObject(credentialSelector), TIMEOUT_MS);

                // Verify credential button is first option
                final UiObject2 credentialButton = mDevice.findObject(credentialSelector);
                assertThat(credentialButton).isNotNull();
                assertThat(credentialButton.getVisibleBounds()).isNotNull();

                // Click credential button
                credentialButton.click();
                mInstrumentation.waitForIdleSync();
                waitForState(STATE_SHOWING_DEVICE_CREDENTIAL);
                successfullyEnterCredential();
            }
        }
    }

    @ApiTest(apis = {"android.hardware.biometrics.BiometricPrompt.Builder#addFallbackOption"})
    @RequiresFlagsEnabled({Flags.FLAG_BP_FALLBACK_OPTIONS, Flags.FLAG_ADD_FALLBACK})
    @Test
    public void testFallbackOptions_singleFallbackAsNegativeButton() throws Exception {
        for (SensorProperties props : mSensorProperties) {
            if (props.getSensorStrength() == SensorProperties.STRENGTH_CONVENIENCE) {
                continue;
            }

            Log.d(
                    TAG,
                    "testFallbackOptions_singleFallbackAsNegativeButton, sensor: "
                            + props.getSensorId());

            try (BiometricTestSession session =
                    mBiometricManager.createTestSession(props.getSensorId())) {

                setUpNonConvenienceSensorEnrollment(props, session);

                // Show prompt with one fallback
                final String fallbackText = "Use fallback";
                final DialogInterface.OnClickListener listener =
                        mock(DialogInterface.OnClickListener.class);
                final Executor executor = mHandler::post;
                final BiometricPrompt.Builder builder =
                        new BiometricPrompt.Builder(mContext)
                                .setTitle("Title")
                                .setSubtitle("Subtitle")
                                .setDescription("Description")
                                .setConfirmationRequired(true)
                                .setAllowBackgroundAuthentication(true)
                                .addFallbackOption(fallbackText, 0, executor, listener);
                createAndShowPrompt(BIOMETRIC_WEAK, builder, mCallback, executor);

                // Find fallback as negative button
                final UiObject2 negativeButton = findView(BUTTON_ID_NEGATIVE);
                assertThat(negativeButton).isNotNull();
                assertThat(negativeButton.getText()).isEqualTo(fallbackText);

                // Click fallback
                negativeButton.click();
                mInstrumentation.waitForIdleSync();
                waitForState(STATE_AUTH_IDLE);

                // Verify fallback listener
                verify(listener).onClick(any(), anyInt());
            }
        }
    }

    @ApiTest(apis = {"android.hardware.biometrics.BiometricPrompt.Builder#addFallbackOption"})
    @RequiresFlagsEnabled({Flags.FLAG_BP_FALLBACK_OPTIONS, Flags.FLAG_ADD_FALLBACK})
    @Test
    public void testFallbackOptions_testFallbackOptionPressed() throws Exception {
        for (SensorProperties props : mSensorProperties) {
            if (props.getSensorStrength() == SensorProperties.STRENGTH_CONVENIENCE) {
                continue;
            }

            Log.d(
                    TAG,
                    "testFallbackOptions_testFallbackOptionPressed, sensor: "
                            + props.getSensorId());

            try (BiometricTestSession session =
                    mBiometricManager.createTestSession(props.getSensorId())) {

                setUpNonConvenienceSensorEnrollment(props, session);

                // Show prompt with fallback and credential for fallback page
                final String fallbackText = "Fallback option";
                final DialogInterface.OnClickListener listener =
                        mock(DialogInterface.OnClickListener.class);
                final Executor executor = mHandler::post;
                final BiometricPrompt.Builder builder =
                        new BiometricPrompt.Builder(mContext)
                                .setTitle("Title")
                                .setSubtitle("Subtitle")
                                .setDescription("Description")
                                .setConfirmationRequired(true)
                                .setAllowBackgroundAuthentication(true)
                                .addFallbackOption(fallbackText, 0, executor, listener);
                createAndShowPrompt(
                        DEVICE_CREDENTIAL | BIOMETRIC_WEAK, builder, mCallback, executor);

                // Find fallback page button
                final UiObject2 fallbackPageButton = findView(BUTTON_ID_FALLBACK);
                assertThat(fallbackPageButton).isNotNull();
                fallbackPageButton.click();
                mDevice.waitForIdle();

                // Find fallback button
                final BySelector fallbackSelector = By.text(fallbackText);
                mDevice.wait(Until.hasObject(fallbackSelector), TIMEOUT_MS);
                final UiObject2 fallbackButton = mDevice.findObject(fallbackSelector);
                assertThat(fallbackButton).isNotNull();

                // Click fallback button
                fallbackButton.click();
                mInstrumentation.waitForIdleSync();
                waitForState(STATE_AUTH_IDLE);

                // Verify fallback listener
                verify(listener).onClick(any(), anyInt());
            }
        }
    }

    @ApiTest(apis = {"android.hardware.biometrics.BiometricPrompt.Builder#addFallbackOption"})
    @RequiresFlagsEnabled({Flags.FLAG_BP_FALLBACK_OPTIONS, Flags.FLAG_ADD_FALLBACK})
    @Test
    public void testFallbackOptions_fourFallbacks() throws Exception {
        for (SensorProperties props : mSensorProperties) {
            if (props.getSensorStrength() == SensorProperties.STRENGTH_CONVENIENCE) {
                continue;
            }

            Log.d(TAG, "testFallbackOptions_fourFallbacks, sensor: " + props.getSensorId());

            // Set up prompt with 4 fallbacks
            final String[] fallbackTexts = {"Fallback 1", "Fallback 2", "Fallback 3", "Fallback 4"};

            for (int i = 0; i < fallbackTexts.length; i++) {
                try (BiometricTestSession session =
                        mBiometricManager.createTestSession(props.getSensorId())) {

                    setUpNonConvenienceSensorEnrollment(props, session);

                    final DialogInterface.OnClickListener[] listeners = {
                        mock(DialogInterface.OnClickListener.class),
                        mock(DialogInterface.OnClickListener.class),
                        mock(DialogInterface.OnClickListener.class),
                        mock(DialogInterface.OnClickListener.class)
                    };
                    final Executor executor = mHandler::post;
                    final BiometricPrompt.Builder builder =
                            new BiometricPrompt.Builder(mContext)
                                    .setTitle("Title")
                                    .setSubtitle("Subtitle")
                                    .setDescription("Description")
                                    .setConfirmationRequired(true)
                                    .setAllowBackgroundAuthentication(true)
                                    .addFallbackOption(fallbackTexts[0], 0, executor, listeners[0])
                                    .addFallbackOption(fallbackTexts[1], 0, executor, listeners[1])
                                    .addFallbackOption(fallbackTexts[2], 0, executor, listeners[2])
                                    .addFallbackOption(fallbackTexts[3], 0, executor, listeners[3]);
                    createAndShowPrompt(
                            DEVICE_CREDENTIAL | BIOMETRIC_WEAK, builder, mCallback, executor);

                    // Find fallback page button
                    final UiObject2 fallbackPageButton = findView(BUTTON_ID_FALLBACK);
                    assertThat(fallbackPageButton).isNotNull();
                    fallbackPageButton.click();
                    mDevice.waitForIdle();

                    // Test each fallback button
                    final BySelector fallbackSelector = By.text(fallbackTexts[i]);
                    mDevice.wait(Until.hasObject(fallbackSelector), TIMEOUT_MS);
                    final UiObject2 fallbackButton = mDevice.findObject(fallbackSelector);
                    assertThat(fallbackButton).isNotNull();
                    fallbackButton.click();
                    mInstrumentation.waitForIdleSync();
                    waitForState(STATE_AUTH_IDLE);

                    verify(listeners[i]).onClick(any(), anyInt());
                }
            }
        }
    }

    @ApiTest(apis = {"android.hardware.biometrics.BiometricPrompt.Builder#addFallbackOption"})
    @RequiresFlagsEnabled({Flags.FLAG_BP_FALLBACK_OPTIONS, Flags.FLAG_ADD_FALLBACK})
    @Test
    public void testFallbackOptions_customNegativeWithFallback() throws Exception {
        for (SensorProperties props : mSensorProperties) {
            if (props.getSensorStrength() == SensorProperties.STRENGTH_CONVENIENCE) {
                continue;
            }

            Log.d(
                    TAG,
                    "testFallbackOptions_customNegativeWithFallback, sensor: "
                            + props.getSensorId());

            try (BiometricTestSession session =
                    mBiometricManager.createTestSession(props.getSensorId())) {

                setUpNonConvenienceSensorEnrollment(props, session);

                // Set up prompt with fallback and custom setNegative
                final String negativeText = "Negative";
                final DialogInterface.OnClickListener listener =
                        mock(DialogInterface.OnClickListener.class);
                final Executor executor = mHandler::post;
                final BiometricPrompt.Builder builder =
                        new BiometricPrompt.Builder(mContext)
                                .setTitle("Title")
                                .setSubtitle("Subtitle")
                                .setDescription("Description")
                                .setConfirmationRequired(true)
                                .setAllowBackgroundAuthentication(true)
                                .addFallbackOption("Fallback", 0, executor, (dialog, which) -> {})
                                .setNegativeButton(negativeText, executor, listener);
                createAndShowPrompt(
                        DEVICE_CREDENTIAL | BIOMETRIC_WEAK, builder, mCallback, executor);

                // Find negative button and verify it is the custom set
                final UiObject2 negativeButton = findView(BUTTON_ID_NEGATIVE);
                assertThat(negativeButton).isNotNull();
                assertThat(negativeButton.getText()).isEqualTo(negativeText);

                // Click negative
                negativeButton.click();
                mInstrumentation.waitForIdleSync();
                waitForState(STATE_AUTH_IDLE);

                // Verify negative listener is triggered
                verify(listener).onClick(any(), anyInt());
            }
        }
    }

    @ApiTest(apis = {"android.hardware.biometrics.BiometricPrompt.Builder#addFallbackOption"})
    @RequiresFlagsEnabled({Flags.FLAG_BP_FALLBACK_OPTIONS, Flags.FLAG_ADD_FALLBACK})
    @Test
    public void testCredentialOnly_singleFallback() throws Exception {
        try (CredentialSession credentialSession = new CredentialSession()) {
            credentialSession.setCredential();

            // Set up credential screen with fallback option
            final String fallbackText = "Use fallback";
            final DialogInterface.OnClickListener listener =
                    mock(DialogInterface.OnClickListener.class);
            final Executor executor = mHandler::post;
            final BiometricPrompt.Builder builder =
                    new BiometricPrompt.Builder(mContext)
                            .setTitle("Title")
                            .setSubtitle("Subtitle")
                            .setDescription("Description")
                            .addFallbackOption(fallbackText, 0, executor, listener)
                            .setAllowBackgroundAuthentication(true);
            createAndShowPrompt(DEVICE_CREDENTIAL, builder, mCallback, executor);

            // Find fallback button
            final UiObject2 fallbackButton = findViewByText(fallbackText);
            assertThat(fallbackButton).isNotNull();
            assertThat(fallbackButton.getText()).isEqualTo(fallbackText);

            // Click fallback
            fallbackButton.click();
            mInstrumentation.waitForIdleSync();
            waitForState(STATE_AUTH_IDLE);

            // Verify listener triggered
            verify(listener).onClick(any(), anyInt());
        }
    }

    @ApiTest(apis = {"android.hardware.biometrics.BiometricPrompt.Builder#addFallbackOption"})
    @RequiresFlagsEnabled({Flags.FLAG_BP_FALLBACK_OPTIONS, Flags.FLAG_ADD_FALLBACK})
    @Test
    public void testCredentialOnly_multipleFallbacks() throws Exception {
        try (CredentialSession credentialSession = new CredentialSession()) {
            credentialSession.setCredential();

            // Set up credential prompt with multiple fallbacks
            final String[] fallbackTexts = {"Fallback 1", "Fallback 2"};
            final DialogInterface.OnClickListener[] listeners = {
                mock(DialogInterface.OnClickListener.class),
                mock(DialogInterface.OnClickListener.class)
            };
            final Executor executor = mHandler::post;
            final BiometricPrompt.Builder builder =
                    new BiometricPrompt.Builder(mContext)
                            .setTitle("Title")
                            .setSubtitle("Subtitle")
                            .setDescription("Description")
                            .addFallbackOption(fallbackTexts[0], 0, executor, listeners[0])
                            .addFallbackOption(fallbackTexts[1], 0, executor, listeners[1])
                            .setAllowBackgroundAuthentication(true);
            createAndShowPrompt(DEVICE_CREDENTIAL, builder, mCallback, executor);

            // Find fallback page button on credential
            final UiObject2 credentialFallbackButton = findView(CREDENTIAL_FALLBACK_BUTTON);
            assertThat(credentialFallbackButton).isNotNull();
            credentialFallbackButton.click();
            mDevice.waitForIdle();

            // Find first fallback
            final BySelector fallbackSelector = By.text(fallbackTexts[0]);
            mDevice.wait(Until.hasObject(fallbackSelector), TIMEOUT_MS);
            final UiObject2 fallbackButton = mDevice.findObject(fallbackSelector);
            assertThat(fallbackButton).isNotNull();

            // Trigger fallback
            fallbackButton.click();
            mInstrumentation.waitForIdleSync();
            waitForState(STATE_AUTH_IDLE);

            // Verify listener
            verify(listeners[0]).onClick(any(), anyInt());
        }
    }

    @ApiTest(
            apis = {
                "android.hardware.biometrics.BiometricPrompt.Builder#addFallbackOption",
                "android.hardware.biometrics.BiometricPrompt#getMaxFallbackOptions"
            })
    @RequiresFlagsEnabled({Flags.FLAG_BP_FALLBACK_OPTIONS, Flags.FLAG_ADD_FALLBACK})
    @Test(expected = IllegalArgumentException.class)
    public void testFallbackOptions_exceedsMaxOptions() throws Exception {
        for (SensorProperties props : mSensorProperties) {
            if (props.getSensorStrength() == SensorProperties.STRENGTH_CONVENIENCE) {
                continue;
            }

            Log.d(TAG, "testFallbackOptions_fourFallbacks, sensor: " + props.getSensorId());

            try (BiometricTestSession session =
                    mBiometricManager.createTestSession(props.getSensorId())) {

                setUpNonConvenienceSensorEnrollment(props, session);

                final Executor executor = mHandler::post;
                final BiometricPrompt.Builder builder =
                        new BiometricPrompt.Builder(mContext)
                                .setTitle("Title")
                                .setSubtitle("Subtitle")
                                .setDescription("Description")
                                .setConfirmationRequired(true)
                                .setAllowBackgroundAuthentication(true);

                // Add 1 more than max fallback options
                for (int i = 0; i < BiometricPrompt.getMaxFallbackOptions() + 1; i++) {
                    builder.addFallbackOption("fallback " + i, 0, executor, (dialog, which) -> {});
                }
            }
        }
    }

    @ApiTest(apis = {"android.hardware.biometrics.BiometricPrompt#getMaxFallbackOptions"})
    @RequiresFlagsEnabled({Flags.FLAG_BP_FALLBACK_OPTIONS, Flags.FLAG_ADD_FALLBACK})
    @Test
    public void testGetMaxFallbackOptions() {
        assertThat(BiometricPrompt.getMaxFallbackOptions()).isEqualTo(MAX_FALLBACK_OPTIONS);
    }

    @ApiTest(
            apis = {
                "android.hardware.biometrics.BiometricPrompt.Builder#addFallbackOption",
                "android.hardware.biometrics.BiometricPrompt#getFallbackOptions"
            })
    @RequiresFlagsEnabled({Flags.FLAG_BP_FALLBACK_OPTIONS, Flags.FLAG_ADD_FALLBACK})
    @Test
    public void testGetFallbackOptions() {
        final String fallback1 = "fallback1";
        final String fallback2 = "fallback2";
        final Executor executor = mHandler::post;
        final BiometricPrompt.Builder builder =
                new BiometricPrompt.Builder(mContext)
                        .setTitle("Title")
                        .addFallbackOption(fallback1, 0, executor, (dialog, which) -> {})
                        .addFallbackOption(fallback2, 0, executor, (dialog, which) -> {});
        final BiometricPrompt prompt = builder.build();
        assertThat(prompt.getFallbackOptions().size()).isEqualTo(2);
        assertThat(prompt.getFallbackOptions().get(0).getText().toString()).isEqualTo(fallback1);
        assertThat(prompt.getFallbackOptions().get(1).getText().toString()).isEqualTo(fallback2);
    }

    @ApiTest(
            apis = {
                "android.hardware.biometrics.BiometricPrompt.Builder#addFallbackOption",
                "android.hardware.biometrics.FallbackOption#getText",
                "android.hardware.biometrics.FallbackOption#getIconType",
                "android.hardware.biometrics.FallbackOption#FallbackOption(CharSequence)",
            })
    @RequiresFlagsEnabled({Flags.FLAG_BP_FALLBACK_OPTIONS, Flags.FLAG_ADD_FALLBACK})
    @Test
    public void testFallbackOptionClass() {
        final String text = "Test Fallback";
        final int icon = 1;
        final Executor executor = mHandler::post;
        final BiometricPrompt.Builder builder =
                new BiometricPrompt.Builder(mContext)
                        .setTitle("Title")
                        .addFallbackOption(text, icon, executor, (dialog, which) -> {});
        final BiometricPrompt prompt = builder.build();
        final FallbackOption fallbackOption = prompt.getFallbackOptions().getFirst();
        assertThat(fallbackOption.getText().toString()).isEqualTo(text);
        assertThat(fallbackOption.getIconType()).isEqualTo(icon);
    }

    private void createAndShowPrompt(
            int authenticators,
            BiometricPrompt.Builder builder,
            BiometricPrompt.AuthenticationCallback callback,
            Executor executor)
            throws Exception {
        final BiometricPrompt prompt = builder.setAllowedAuthenticators(authenticators).build();
        prompt.authenticate(new CancellationSignal(), executor, callback);
        waitForState(STATE_AUTH_STARTED_UI_SHOWING);
    }
}
