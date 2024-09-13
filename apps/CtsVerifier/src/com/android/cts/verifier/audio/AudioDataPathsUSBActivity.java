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

package com.android.cts.verifier.audio;

import android.media.AudioDeviceInfo;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.android.cts.verifier.R;
import com.android.cts.verifier.audio.audiolib.AudioDeviceUtils;

// MegaAudio
import org.hyphonate.megaaudio.player.AudioSourceProvider;
import org.hyphonate.megaaudio.player.sources.SparseChannelAudioSourceProvider;
import org.hyphonate.megaaudio.recorder.AudioSinkProvider;
import org.hyphonate.megaaudio.recorder.sinks.AppCallbackAudioSinkProvider;

public class AudioDataPathsUSBActivity extends AudioDataPathsBaseActivity {
    private static final String TAG = "AudioDataPathsUSBActivity";

    private int mUsbDeviceSupport;
    private int mUsbHeadsetSupport;
    private boolean mCanRunTest;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setContentView(R.layout.audio_datapaths_usb);

        mUsbDeviceSupport = AudioDeviceUtils.supportsUsbAudioInterface(this);
        mUsbHeadsetSupport  = AudioDeviceUtils.supportsUsbHeadset(this);
        mCanRunTest = mUsbDeviceSupport != AudioDeviceUtils.SUPPORTSDEVICE_NO
                || mUsbHeadsetSupport != AudioDeviceUtils.SUPPORTSDEVICE_NO;

        super.onCreate(savedInstanceState);
        setInfoResources(
                R.string.audio_datapaths_USB_test, R.string.audio_datapaths_USB_info, -1);

        enableTestButtons(mCanRunTest);
    }

    void gatherTestModules(TestManager testManager) {
        AudioSourceProvider leftSineSourceProvider = new SparseChannelAudioSourceProvider(
                SparseChannelAudioSourceProvider.CHANNELMASK_LEFT);
        AudioSourceProvider rightSineSourceProvider = new SparseChannelAudioSourceProvider(
                SparseChannelAudioSourceProvider.CHANNELMASK_RIGHT);

        AudioSinkProvider analysisSinkProvider =
                new AppCallbackAudioSinkProvider(mAnalysisCallbackHandler);

        TestModule testModule;

        // These just make it easier to turn on/off various categories
        boolean doUsbDevice = mUsbDeviceSupport != AudioDeviceUtils.SUPPORTSDEVICE_NO;
        boolean doUsbHeadset = mUsbHeadsetSupport != AudioDeviceUtils.SUPPORTSDEVICE_NO;

        //
        // USB Device
        //
        if (doUsbDevice) {
            // Signal Presence
            testModule = new TestModule(
                    AudioDeviceInfo.TYPE_USB_DEVICE, 48000, 2,
                    AudioDeviceInfo.TYPE_USB_DEVICE, 48000, 2);
            testModule.setSectionTitle("USB Device");
            testModule.setSources(leftSineSourceProvider, analysisSinkProvider);
            testModule.setDescription("Dev:2:L Dev:2");
            testModule.setAnalysisChannel(0);
            testManager.addTestModule(testModule);

            testModule = new TestModule(
                    AudioDeviceInfo.TYPE_USB_DEVICE, 48000, 2,
                    AudioDeviceInfo.TYPE_USB_DEVICE, 48000, 2);
            testModule.setSources(rightSineSourceProvider, analysisSinkProvider);
            testModule.setDescription("Dev:2:R Dev:2");
            testModule.setAnalysisChannel(1);
            testManager.addTestModule(testModule);

            // Signal Absence
            testModule = new TestModule(
                    AudioDeviceInfo.TYPE_USB_DEVICE, 48000, 2,
                    AudioDeviceInfo.TYPE_USB_DEVICE, 48000, 2);
            testModule.setSources(leftSineSourceProvider, analysisSinkProvider);
            testModule.setDescription("Dev:2:L Dev:2 [Cross Talk]");
            testModule.setAnalysisChannel(1);
            testModule.setAnalysisType(TestModule.TYPE_SIGNAL_ABSENCE);
            testManager.addTestModule(testModule);

            testModule = new TestModule(
                    AudioDeviceInfo.TYPE_USB_DEVICE, 48000, 2,
                    AudioDeviceInfo.TYPE_USB_DEVICE, 48000, 2);
            testModule.setSources(rightSineSourceProvider, analysisSinkProvider);
            testModule.setDescription("Dev:2:R Dev:2 [Cross Talk]");
            testModule.setAnalysisChannel(0);
            testModule.setAnalysisType(TestModule.TYPE_SIGNAL_ABSENCE);
            testManager.addTestModule(testModule);
        }

        //
        // USB Headset
        //
        if (doUsbHeadset) {
            testModule = new TestModule(
                    AudioDeviceInfo.TYPE_USB_HEADSET, 48000, 2,
                    AudioDeviceInfo.TYPE_USB_HEADSET, 48000, 2);
            testModule.setSectionTitle("USB Headset");
            testModule.setSources(leftSineSourceProvider, analysisSinkProvider);
            testModule.setDescription("Headset:2:L Headset:2");
            testModule.setAnalysisChannel(0);
            testManager.addTestModule(testModule);
            testModule = new TestModule(
                    AudioDeviceInfo.TYPE_USB_HEADSET, 48000, 2,
                    AudioDeviceInfo.TYPE_USB_HEADSET, 48000, 2);
            testModule.setSources(rightSineSourceProvider, analysisSinkProvider);
            testModule.setDescription("Headset:2:R Headset:2");
            testModule.setAnalysisChannel(1);
            testManager.addTestModule(testModule);
        }
    }

    void postValidateTestDevices(int numValidTestModules) {
        AudioDeviceUtils.validateUsbDevice(this);

        TextView promptView = findViewById(R.id.audio_datapaths_deviceprompt);
        if (mIsHandheld) {
            if (mUsbDeviceSupport == AudioDeviceUtils.SUPPORTSDEVICE_YES
                    && mUsbHeadsetSupport == AudioDeviceUtils.SUPPORTSDEVICE_YES) {
                if (mTestManager.countTestedTestModules() != 0) {
                    // There are already tested devices in the list, so they must be attaching
                    // another test peripheral
                    promptView.setText(
                            getResources().getString(R.string.audio_datapaths_usb_nextperipheral));
                } else {
                    promptView.setText(
                            getResources().getString(R.string.audio_datapaths_usb_nodevices));
                    enableTestButtons(false);
                }
                promptView.setVisibility(numValidTestModules == 0 ? View.VISIBLE : View.GONE);
            } else if (mUsbDeviceSupport == AudioDeviceUtils.SUPPORTSDEVICE_NO
                    && mUsbHeadsetSupport == AudioDeviceUtils.SUPPORTSDEVICE_NO) {
                promptView.setText(
                        getResources().getString(R.string.audio_datapaths_usb_nosupport));
            } else {
                // AudioDeviceUtils.SUPPORTSDEVICE_UNDETERMINED
                promptView.setText(
                        getResources().getString(R.string.audio_datapaths_usb_undetermined));
                enableTestButtons(false);
            }
        } else {
            promptView.setVisibility(View.GONE);
        }

        enableTestButtons(numValidTestModules != 0);
    }

    protected boolean hasPeripheralSupport() {
        return mCanRunTest;
    }
}
