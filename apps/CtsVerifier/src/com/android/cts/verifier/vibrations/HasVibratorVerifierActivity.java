/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.cts.verifier.vibrations;

import android.content.res.Resources;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.compatibility.common.util.ApiTest;
import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;
import com.android.cts.verifier.features.FeatureUtil;

import java.util.Locale;

/**
 * This activity validates the result of {@link Vibrator#hasVibrator} API.
 *
 * A test is considered a positive scenario when hasVibrator returns true, otherwise the test is
 * a negative scenario.
 */
@ApiTest(apis = {"android.os.Vibrator#hasVibrator"})
public class HasVibratorVerifierActivity extends PassFailButtons.Activity {

    private static final int TEST_DURATION = 4_000;
    private static final int COUNT_DOWN_INTERVAL = 1_000;

    private boolean mHasVibrator = false;
    private int mTargetUsage = VibrationAttributes.USAGE_UNKNOWN;
    private int mCounter = TEST_DURATION / COUNT_DOWN_INTERVAL;
    private Vibrator mVibrator;
    private Animation mShakeAnimation;
    private TextView mVibrateCountdownTextView;
    private TextView mTestResultTextView;
    private TextView mDidDeviceVibrateTextView;
    private LinearLayout mResultButtonsLayout;
    private Button mVibrateButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_has_vibrator);

        setPassFailButtonClickListeners();
        getPassButton().setEnabled(false);

        mVibrateCountdownTextView = findViewById(R.id.vibrate_countdown_textview);
        mTestResultTextView = findViewById(R.id.test_result_textview);
        mDidDeviceVibrateTextView = findViewById(R.id.did_device_vibrate_textview);
        mResultButtonsLayout = findViewById(R.id.layout_result_buttons);
        mVibrateButton = findViewById(R.id.vibrate_button);
        TextView hasVibratorApiResultTextView = findViewById(R.id.has_vibrator_api_result_textview);
        Button yesButton = findViewById(R.id.yes_button);
        Button noButton = findViewById(R.id.no_button);

        mShakeAnimation = AnimationUtils.loadAnimation(this, R.anim.horizontal_shake);

        VibratorManager vibratorManager = getSystemService(VibratorManager.class);
        if (vibratorManager == null) {
            throw new IllegalStateException(
                    "Something went wrong while creating the VibratorManager");
        }
        mVibrator = vibratorManager.getDefaultVibrator();
        mHasVibrator = mVibrator.hasVibrator();
        resolveTargetUsage();

        hasVibratorApiResultTextView.setText(
                mHasVibrator ? R.string.yes_string : R.string.no_string);

        mVibrateButton.setOnClickListener(v -> {
            startVibrating();
            updateScreenStateToStartedTesting();
            startAnimationIfRequired();
            startTestCountdown();
        });

        yesButton.setOnClickListener(v -> onYesButtonClicked());
        noButton.setOnClickListener(v -> onNoButtonClicked());
    }

    /**
     * Resolves the target {@link VibrationAttributes} usage to use for testing.
     */
    private void resolveTargetUsage() {
        if (!mHasVibrator) {
            mTargetUsage = VibrationAttributes.USAGE_UNKNOWN;
            return;
        }

        // 1. Prefer default USAGE_UNKNOWN if it has active intensity.
        if (isUsageIntensityActive(VibrationAttributes.USAGE_UNKNOWN)) {
            mTargetUsage = VibrationAttributes.USAGE_UNKNOWN;
            return;
        }

        // 2. On XR devices where general touch haptics are OFF by default, fallback to
        // USAGE_HARDWARE_FEEDBACK so physical actuator is exercised without being blocked.
        if (FeatureUtil.isXrHeadset(this)) {
            mTargetUsage = VibrationAttributes.USAGE_HARDWARE_FEEDBACK;
            return;
        }

        // 3. Fall back to default USAGE_UNKNOWN for non-XR devices.
        mTargetUsage = VibrationAttributes.USAGE_UNKNOWN;
    }

    private boolean isUsageIntensityActive(int usage) {
        try {
            return mVibrator.getDefaultVibrationIntensity(usage)
                    > Vibrator.VIBRATION_INTENSITY_OFF;
        } catch (Exception | LinkageError e) {
            // Fall back to querying framework resources if method is unavailable or fails.
        }

        try {
            Resources res = Resources.getSystem();
            int resId;
            if (usage == VibrationAttributes.USAGE_MEDIA) {
                resId = res.getIdentifier(
                        "config_defaultMediaVibrationIntensity", "integer", "android");
            } else {
                resId = res.getIdentifier(
                        "config_defaultHapticFeedbackIntensity", "integer", "android");
            }
            if (resId > 0 && res.getInteger(resId) > Vibrator.VIBRATION_INTENSITY_OFF) {
                return true;
            }
        } catch (Exception e) {
            // Resource not found or inaccessible; treat intensity as inactive.
        }
        return false;
    }

    private void startVibrating() {
        VibrationEffect effect = VibrationEffect.createOneShot(
                TEST_DURATION, VibrationEffect.MAX_AMPLITUDE);
        VibrationAttributes attrs = new VibrationAttributes.Builder()
                .setUsage(mTargetUsage)
                .build();
        mVibrator.vibrate(effect, attrs);
    }

    private void startTestCountdown() {
        new CountDownTimer(TEST_DURATION, COUNT_DOWN_INTERVAL) {
            public void onTick(long millisUntilFinished) {
                mVibrateCountdownTextView.setText(
                        String.format(Locale.getDefault(),
                                getString(R.string.has_vibrator_test_running_text), mCounter));
                mCounter--;
            }

            public void onFinish() {
                updateScreenStateToFinishedTesting();
                mCounter = TEST_DURATION / COUNT_DOWN_INTERVAL;
                mVibrateCountdownTextView.clearAnimation();
            }
        }.start();
    }

    /**
     * If Vibrator#hasVibrator API indicated the device has a vibrator, and the device vibrated,
     * then the test passed. Otherwise, if the device vibrated despite the API indicating the device
     * has no vibrator then the test failed.
     */
    private void onYesButtonClicked() {
        mTestResultTextView.setVisibility(View.VISIBLE);
        getPassButton().setEnabled(mHasVibrator);
        if (mHasVibrator) {
            mTestResultTextView.setText(R.string.has_vibrator_test_vibrate_and_pass_message);
        } else {
            mTestResultTextView.setText(R.string.has_vibrator_test_no_vibrate_and_fail_message);
        }
    }

    /**
     * If Vibrator#hasVibrator API indicated the device has no vibrator, and the device did not
     * vibrate, then the test passed. Otherwise, if the device did vibrate despite the API
     * indicating the device has no vibrator then the test failed.
     */
    private void onNoButtonClicked() {
        mTestResultTextView.setVisibility(View.VISIBLE);
        getPassButton().setEnabled(!mHasVibrator);
        if (!mHasVibrator) {
            mTestResultTextView.setText(R.string.has_vibrator_test_no_vibrate_and_pass_message);
        } else {
            mTestResultTextView.setText(R.string.has_vibrator_test_vibrate_and_fail_message);
        }
    }

    private void updateScreenStateToStartedTesting() {
        mTestResultTextView.setVisibility(View.GONE);
        mVibrateButton.setVisibility(View.GONE);
        mDidDeviceVibrateTextView.setVisibility(View.GONE);
        mResultButtonsLayout.setVisibility(View.GONE);
        mVibrateCountdownTextView.setVisibility(View.VISIBLE);
    }

    private void updateScreenStateToFinishedTesting() {
        mVibrateButton.setVisibility(View.VISIBLE);
        mDidDeviceVibrateTextView.setVisibility(View.VISIBLE);
        mResultButtonsLayout.setVisibility(View.VISIBLE);
        mVibrateCountdownTextView.setVisibility(View.GONE);
    }

    private void startAnimationIfRequired() {
        if (mHasVibrator) {
            mVibrateCountdownTextView.clearAnimation();
            mVibrateCountdownTextView.startAnimation(mShakeAnimation);
        }
    }
}
