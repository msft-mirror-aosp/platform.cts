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

package com.android.cts.verifier.camera.webcam;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

/**
 * Test for Device as Webcam feature.
 * This test activity requires a USB connection to a computer, and a corresponding host-side run of
 * the python scripts found in the DeviceAsWebcam directory.
 */
public class WebcamTestActivity extends PassFailButtons.Activity {
    private static final String TAG = WebcamTestActivity.class.getSimpleName();
    private static final String ACTION_WEBCAM_RESULT =
            "com.android.cts.verifier.camera.webcam.ACTION_WEBCAM_RESULT";
    private static final String WEBCAM_RESULTS = "camera.webcam.extra.RESULTS";

    private static final String RESULT_PASS = "PASS";
    private static final String RESULT_FAIL = "FAIL";

    private final ResultReceiver mResultsReceiver = new ResultReceiver();
    private boolean mReceiverRegistered = false;

    private TestState mTestState;

    private Button mYesButton;
    private Button mNoButton;
    private Button mDoneButton;
    private View mPassButton;
    private TextView mInstructionTextView;

    private String mResultsFromScript = RESULT_FAIL;

    private enum TestState {
        ON_CREATE,
        USB_MANAGER_UNAVAILABLE,
        WEBCAM_NOT_SUPPORTED,
        RESULTS_RECEIVED,
        RESULTS_PASS_FRAMES_PASS,
        RESULTS_FAIL_FRAMES_PASS,
        RESULTS_PASS_FRAMES_FAIL,
        RESULTS_FAIL_FRAMES_FAIL
    }

    private final View.OnClickListener mYesButtonListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (mResultsFromScript.equals(RESULT_PASS)) {
                mTestState = TestState.RESULTS_PASS_FRAMES_PASS;
            } else {
                mTestState = TestState.RESULTS_FAIL_FRAMES_PASS;
            }
            updateButtonsAndInstructions();
        }
    };

    private final View.OnClickListener mNoButtonListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (mResultsFromScript.equals(RESULT_PASS)) {
                mTestState = TestState.RESULTS_PASS_FRAMES_FAIL;
            } else {
                mTestState = TestState.RESULTS_FAIL_FRAMES_FAIL;
            }
            updateButtonsAndInstructions();
        }
    };

    private final View.OnClickListener mDoneButtonListener =
            new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    switch (mTestState) {
                        case WEBCAM_NOT_SUPPORTED -> {
                            // Skip modeled as a "pass"
                            setTestResultAndFinish(true);
                        }
                        case RESULTS_PASS_FRAMES_PASS -> setTestResultAndFinish(true);
                        case USB_MANAGER_UNAVAILABLE,
                                RESULTS_FAIL_FRAMES_FAIL,
                                RESULTS_PASS_FRAMES_FAIL,
                                RESULTS_FAIL_FRAMES_PASS ->
                                setTestResultAndFinish(false);
                        default -> {
                            // Do nothing.
                        }
                    }
                }
            };

    class ResultReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!ACTION_WEBCAM_RESULT.equals(intent.getAction())) {
                return;
            }

            if (mTestState != TestState.ON_CREATE) {
                Log.w(
                        TAG,
                        String.format(
                                "Received unexpected '%s' broadcast. Expected test state: '%s';"
                                        + " current test state: '%s'. Ignoring.",
                                ACTION_WEBCAM_RESULT,
                                TestState.ON_CREATE.name(),
                                mTestState.name()));
                return;
            }

            Log.v(TAG, String.format("Received broadcast: '%s'", ACTION_WEBCAM_RESULT));
            String results = intent.getStringExtra(WEBCAM_RESULTS);
            if (RESULT_PASS.equals(results)) {
                mTestState = TestState.RESULTS_RECEIVED;
                mResultsFromScript = RESULT_PASS;
            } else if (RESULT_FAIL.equals(results)) {
                mTestState = TestState.RESULTS_RECEIVED;
                mResultsFromScript = RESULT_FAIL;
            } else {
                Log.w(
                        TAG,
                        String.format(
                                "Found invalid result in broadcast. expected: oneof('%s', '%s');"
                                        + " actual: '%s'",
                                RESULT_PASS, RESULT_FAIL, results));
            }

            updateButtonsAndInstructions();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.camera_webcam);

        setInfoResources(R.string.camera_webcam_test, R.string.camera_webcam_test_info, -1);

        mYesButton = (Button) findViewById(R.id.frames_pass_button);
        mYesButton.setOnClickListener(mYesButtonListener);

        mNoButton = (Button) findViewById(R.id.frames_fail_button);
        mNoButton.setOnClickListener(mNoButtonListener);

        mPassButton = getPassButton();
        setPassFailButtonClickListeners();

        mInstructionTextView = (TextView) findViewById(R.id.webcam_instruction_text);

        mDoneButton = (Button) findViewById(R.id.camera_webcam_done_button_id);
        mDoneButton.setOnClickListener(mDoneButtonListener);

        UsbManager usbManager = getSystemService(UsbManager.class);
        if (usbManager == null) {
            Log.e(TAG, "Could not connect to UsbManager");
            mTestState = TestState.USB_MANAGER_UNAVAILABLE;
        } else {
            boolean webcamSupported = usbManager.isUvcGadgetSupportEnabled();
            Log.v(TAG, "UVC Gadget Supported: " + webcamSupported);
            mTestState = webcamSupported ? TestState.ON_CREATE : TestState.WEBCAM_NOT_SUPPORTED;
        }

        updateButtonsAndInstructions();
    }

    @Override
    protected void onResume() {
        super.onResume();

        IntentFilter filter = new IntentFilter(ACTION_WEBCAM_RESULT);
        registerReceiver(mResultsReceiver, filter, Context.RECEIVER_EXPORTED);
        mReceiverRegistered = true;

    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (mReceiverRegistered) {
            unregisterReceiver(mResultsReceiver);
        }
    }

    private void updateButtonsAndInstructions() {
        switch (mTestState) {
            case ON_CREATE -> {
                mPassButton.setEnabled(false);
                mYesButton.setEnabled(false);
                mNoButton.setEnabled(false);
                mDoneButton.setEnabled(false);
                mInstructionTextView.setText(R.string.camera_webcam_start_text);
            }
            case USB_MANAGER_UNAVAILABLE -> {
                mPassButton.setEnabled(false);
                mYesButton.setEnabled(false);
                mNoButton.setEnabled(false);
                mDoneButton.setEnabled(true);
                mInstructionTextView.setText(R.string.camera_webcam_no_usbmanager_text);
            }
            case RESULTS_RECEIVED -> {
                // Once the results are received when the script is complete,
                // enable the buttons that will allow the user to indicate
                // whether the frames from the webcam that were displayed as part
                // of the test had any issues
                mYesButton.setEnabled(true);
                mNoButton.setEnabled(true);
                mInstructionTextView.setText(R.string.camera_webcam_confirm_frames_text);
            }
            case WEBCAM_NOT_SUPPORTED -> {
                mPassButton.setEnabled(false);
                mYesButton.setEnabled(false);
                mNoButton.setEnabled(false);
                mDoneButton.setEnabled(true);
                mInstructionTextView.setText(R.string.camera_webcam_not_supported_text);
            }
            case RESULTS_PASS_FRAMES_PASS -> {
                mDoneButton.setEnabled(true);
                mInstructionTextView.setText(R.string.camera_webcam_results_pass_frames_pass_text);
            }
            case RESULTS_FAIL_FRAMES_PASS -> {
                mDoneButton.setEnabled(true);
                mInstructionTextView.setText(R.string.camera_webcam_results_fail_frames_pass_text);
            }
            case RESULTS_PASS_FRAMES_FAIL -> {
                mDoneButton.setEnabled(true);
                mInstructionTextView.setText(R.string.camera_webcam_results_pass_frames_fail_text);
            }
            case RESULTS_FAIL_FRAMES_FAIL -> {
                mDoneButton.setEnabled(true);
                mInstructionTextView.setText(R.string.camera_webcam_results_fail_frames_fail_text);
            }
        }
    }
}
