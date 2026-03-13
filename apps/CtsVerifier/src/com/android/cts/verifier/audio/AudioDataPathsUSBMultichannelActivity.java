/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.cts.verifier.audio;

import static com.android.cts.verifier.TestListActivity.sCurrentDisplayMode;
import static com.android.cts.verifier.TestListAdapter.setTestNameSuffix;

import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;

import com.android.cts.verifier.R;
import com.android.cts.verifier.audio.audiolib.AudioDeviceUtils;

import org.hyphonate.megaaudio.player.AudioSourceProvider;
import org.hyphonate.megaaudio.player.sources.SparseChannelAudioSourceProvider;
import org.hyphonate.megaaudio.recorder.AudioSinkProvider;
import org.hyphonate.megaaudio.recorder.sinks.AppCallbackAudioSinkProvider;

/** Tests for 8-channel USB audio output support. */
public class AudioDataPathsUSBMultichannelActivity extends AudioDataPathsBaseActivity {
    private static final String TAG = "MultichannelUSBActivity";

    // ReportLog Schema
    private static final String SECTION_DATA_PATHS_USB_MULTICHANNEL =
            "data_paths_usb_multichannel_output";
    private static final int TEST_QUAD_CHANNELS = 4;
    private static final int TEST_FIVE_DOT_ONE_CHANNELS = 6;
    private static final int TEST_SEVEN_DOT_ONE_CHANNELS = 8;
    private static final int TEST_SAMPLE_RATE = 48000;
    public static final int TEST_IN_CHANNEL_COUNT = 2;

    private int mUsbInterfaceSupport;

    private AudioDeviceInfo mUsbInDevice;
    private AudioDeviceInfo mUsbOutDevice;
    private final AudioDeviceCallback mLocalConnectListener =
            new AudioDeviceCallback() {
                @Override
                public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
                    handleDeviceConnection();
                }

                @Override
                public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
                    handleDeviceConnection();
                }

                private void handleDeviceConnection() {
                    Log.i(TAG, "handleDeviceConnection()");
                    new Handler(Looper.getMainLooper())
                            .post(
                                    () -> {
                                        updateUsbCapabilities();
                                        if (areUsbDevicesAvailable()) {
                                            mTestManager.reset();
                                            mTestManager.initializeTests();
                                        }
                                    });
                }
            };

    private void updateUsbCapabilities() {
        AudioManager audioManager = getSystemService(AudioManager.class);
        mUsbInDevice = null;
        AudioDeviceInfo[] inputs = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS);
        for (AudioDeviceInfo dev : inputs) {
            if (dev.getType() == AudioDeviceInfo.TYPE_USB_DEVICE) {
                mUsbInDevice = dev;
                break;
            }
        }
        if (mUsbInDevice == null) {
            return;
        }

        mUsbOutDevice = null;
        AudioDeviceInfo[] outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
        for (AudioDeviceInfo dev : outputs) {
            if (dev.getType() == AudioDeviceInfo.TYPE_USB_DEVICE) {
                mUsbOutDevice = dev;
                break;
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        getSystemService(AudioManager.class)
                .registerAudioDeviceCallback(
                        mLocalConnectListener, new Handler(Looper.getMainLooper()));
    }

    @Override
    public void onStop() {
        getSystemService(AudioManager.class).unregisterAudioDeviceCallback(mLocalConnectListener);
        super.onStop();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setContentView(R.layout.audio_datapaths_usb_multichannel);

        mUsbInterfaceSupport = AudioDeviceUtils.supportsUsbAudioInterface(this);

        CheckBox manualModeCheckBox = findViewById(R.id.audio_datapaths_usb_manual_mode);
        manualModeCheckBox.setOnCheckedChangeListener(
                (buttonView, isChecked) -> setManualMode(isChecked));

        super.onCreate(savedInstanceState);
        setInfoResources(
                R.string.audio_datapaths_USB_multichannel_test,
                R.string.audio_datapaths_USB_multichannel_info,
                /* viewId= */ -1);

        TextView promptView = findViewById(R.id.audio_datapaths_deviceprompt);

        mUtiltitiesHandler.setChannelMasks(
                TEST_IN_CHANNEL_COUNT, AudioFormat.CHANNEL_OUT_7POINT1_SURROUND);

        if (grantAutoPass()) {
            updateAutoPassMessage(promptView);
            getPassButton().setEnabled(/* enabled= */ true);
        }
        enableTestButtons(mUsbInterfaceSupport != AudioDeviceUtils.SUPPORTSDEVICE_NO);
        promptView.setText(getString(R.string.audio_datapaths_usb_multichannel_nodevices));
        updateUsbCapabilities();
    }

    @Override
    protected String getTestCategory() {
        return getString(R.string.audio_datapaths_USB_multichannel_test);
    }

    @Override
    public String getReportSectionName() {
        return setTestNameSuffix(sCurrentDisplayMode, SECTION_DATA_PATHS_USB_MULTICHANNEL);
    }

    @Override
    void gatherTestModules(TestManager testManager) {
        if (!areUsbDevicesAvailable() || !mIsHandheld || mIsEmulator) {
            return;
        }
        addMultichannelModules(testManager, TEST_QUAD_CHANNELS);
        addMultichannelModules(testManager, TEST_FIVE_DOT_ONE_CHANNELS);
        addMultichannelModules(testManager, TEST_SEVEN_DOT_ONE_CHANNELS);
    }

    private boolean areUsbDevicesAvailable() {
        return mUsbInDevice != null && mUsbOutDevice != null;
    }

    private void addMultichannelModules(TestManager testManager, int channelCount) {
        AudioSinkProvider analysisSinkProvider =
                new AppCallbackAudioSinkProvider(mAnalysisCallbackHandler);

        int channelMask;
        String configName;
        switch (channelCount) {
            case TEST_QUAD_CHANNELS -> {
                channelMask = AudioFormat.CHANNEL_OUT_QUAD;
                configName = "Quad";
            }
            case TEST_FIVE_DOT_ONE_CHANNELS -> {
                channelMask = AudioFormat.CHANNEL_OUT_5POINT1;
                configName = "5.1";
            }
            case TEST_SEVEN_DOT_ONE_CHANNELS -> {
                channelMask = AudioFormat.CHANNEL_OUT_7POINT1_SURROUND;
                configName = "7.1";
            }
            default -> {
                Log.e(TAG, "Unsupported channel count: " + channelCount);
                configName = "Unknown";
                channelMask = AudioFormat.CHANNEL_OUT_STEREO;
            }
        }

        int[] orderedMaskBits = AudioLoopbackUtilitiesHandler.getOrderedMaskBits();
        for (int maskBit : orderedMaskBits) {
            if ((channelMask & maskBit) != 0) {
                int channelIndex = Integer.bitCount(channelMask & (maskBit - 1));
                AudioSourceProvider sourceProvider =
                        new SparseChannelAudioSourceProvider(1 << channelIndex);
                TestModule testModule =
                        new TestModule(
                                AudioDeviceInfo.TYPE_USB_DEVICE,
                                TEST_SAMPLE_RATE,
                                channelCount,
                                AudioDeviceInfo.TYPE_USB_DEVICE,
                                TEST_SAMPLE_RATE,
                                TEST_IN_CHANNEL_COUNT); // Input is usually Stereo
                if (channelCount > 2) {
                    testModule.setChannelMask(channelMask);
                }
                testModule.setSectionTitle("USB " + channelCount + " Channels");
                testModule.setSources(sourceProvider, analysisSinkProvider);
                String label = AudioLoopbackUtilitiesHandler.getLabelForMaskBit(maskBit);
                testModule.setDescription(configName + ": " + label);
                // For Manual Mode, we expect the user to patch the output channel to Input Left (0)
                // For Auto Mode (Mixer), it should also appear on Input Left (0) or Right (1)
                // Odd output channels (indices 0, 2, ...) -> Left Input (0)
                // Even output channels (indices 1, 3, ...) -> Right Input (1)
                testModule.setAnalysisChannel(channelIndex % TEST_IN_CHANNEL_COUNT);
                testManager.addTestModule(testModule);
            }
        }
    }

    @Override
    void postValidateTestDevices(int numValidTestModules) {
        TextView promptView = findViewById(R.id.audio_datapaths_deviceprompt);

        if (grantAutoPass()) {
            updateAutoPassMessage(promptView);
        }
        if (mUsbInterfaceSupport == AudioDeviceUtils.SUPPORTSDEVICE_YES) {
            int visibility =
                    mTestManager.calculatePass()
                            ? View.GONE
                            : (numValidTestModules == 0 ? View.VISIBLE : View.GONE);
            promptView.setVisibility(visibility);
        } else if (mUsbInterfaceSupport == AudioDeviceUtils.SUPPORTSDEVICE_NO) {
            promptView.setText(getResources().getString(R.string.audio_datapaths_usb_nosupport));
        } else {
            promptView.setText(getResources().getString(R.string.audio_datapaths_usb_undetermined));
        }

        enableTestButtons(numValidTestModules != 0);
    }

    @Override
    protected boolean hasPeripheralSupport() {
        return mUsbInterfaceSupport != AudioDeviceUtils.SUPPORTSDEVICE_NO;
    }

    @Override
    String getRouteDescription() {
        return "usb_multichannel";
    }

    @Override
    protected boolean grantAutoPass() {
        return !mIsHandheld
                || mIsEmulator
                || Build.VERSION.MEDIA_PERFORMANCE_CLASS < Build.VERSION_CODES.CINNAMON_BUN;
    }

    private void updateAutoPassMessage(TextView promptView) {
        enableTestButtons(/* enabled= */ false);
        if (!mIsHandheld) {
            promptView.setText(
                    getResources().getString(R.string.audio_datapaths_nonhandheld_autopass));
            return;
        }
        if (mIsEmulator) {
            promptView.setText(
                    getResources().getString(R.string.audio_datapaths_emulator_autopass));
            return;
        }
        promptView.setText(
                getResources()
                        .getString(
                                R.string.audio_datapaths_media_class_autopass,
                                Build.VERSION.MEDIA_PERFORMANCE_CLASS,
                                Build.VERSION_CODES.CINNAMON_BUN));
    }
}
