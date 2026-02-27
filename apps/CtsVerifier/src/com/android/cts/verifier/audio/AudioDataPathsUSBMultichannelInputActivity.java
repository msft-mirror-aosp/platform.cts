/*
 * Copyright 2026 The Android Open Source Project
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
import android.widget.TextView;

import com.android.compatibility.common.util.CddTest;
import com.android.cts.verifier.R;
import com.android.cts.verifier.audio.audiolib.AudioDeviceUtils;

import org.hyphonate.megaaudio.common.BuilderBase;
import org.hyphonate.megaaudio.player.AudioSourceProvider;
import org.hyphonate.megaaudio.player.sources.SparseChannelAudioSourceProvider;
import org.hyphonate.megaaudio.recorder.AudioSinkProvider;
import org.hyphonate.megaaudio.recorder.sinks.AppCallbackAudioSinkProvider;

/**
 * Activity for testing USB 4-Channel Input Data Path.
 */
@CddTest(requirement="5.6/H-1-11")
public class AudioDataPathsUSBMultichannelInputActivity extends AudioDataPathsBaseActivity {
    private static final String TAG = "AudioDataPathsUSBMultichannelInputActivity";
    private static final String SECTION_DATA_PATHS_USB_MULTICHANNEL_INPUT =
            "data_paths_usb_multichannel_input";
    private int mUsbInterfaceSupport;

    private int mDetectedEncoding = BuilderBase.ENCODING_PCM_FLOAT;
    private int mDetectedSampleRate = 48000;
    private int mDetectedInChannelCount = 4;
    private int mDetectedOutChannelCount = 4;
    private AudioDeviceInfo mUsbInDevice;
    private AudioDeviceInfo mUsbOutDevice;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setContentView(R.layout.audio_datapaths_usb);
        super.onCreate(savedInstanceState);
        mUsbInterfaceSupport = AudioDeviceUtils.supportsUsbAudioInterface(this);

        updateUsbCapabilities();

        setInfoResources(R.string.audio_datapaths_usb_multichannel_input_test,
                R.string.audio_datapaths_usb_multichannel_input_info, -1);

        ((TextView) findViewById(R.id.audio_datapaths_deviceprompt))
                .setText(getString(R.string.audio_datapaths_usb_4channel_input_nodevices));
    }

    @Override
    public void onStart() {
        super.onStart();
        getSystemService(AudioManager.class).registerAudioDeviceCallback(
                mLocalConnectListener, new Handler(Looper.getMainLooper()));
    }

    @Override
    public void onStop() {
        getSystemService(AudioManager.class).unregisterAudioDeviceCallback(mLocalConnectListener);
        super.onStop();
    }

    private final AudioDeviceCallback mLocalConnectListener = new AudioDeviceCallback() {
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
            new Handler(Looper.getMainLooper()).post(() -> {
                updateUsbCapabilities();
                boolean isConnected = isMultichannelUsbConnected();
                if (isConnected) {
                    mUtiltitiesHandler.setChannels(mDetectedInChannelCount, mDetectedOutChannelCount);
                    mUtiltitiesHandler.setConfig(mDetectedSampleRate, mDetectedEncoding);
                    mUtiltitiesHandler.setInputDevice(mUsbInDevice);
                    mUtiltitiesHandler.setOutputDevice(mUsbOutDevice);
                    Log.i(TAG, "Multichannel USB connected. InCh:" + mDetectedInChannelCount
                            + " OutCh:" + mDetectedOutChannelCount
                            + ". Resetting and initializing tests...");
                    mTestManager.reset();
                    mTestManager.initializeTests();
                } else {
                    Log.i(TAG, "Multichannel USB disconnected. Cleaning up...");
                    mTestManager.stopTest();
                    mTestManager.clearTestState();
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

        mDetectedEncoding = getBestSupportedEncoding(mUsbInDevice);
        mDetectedSampleRate = getBestSupportedSampleRate(mUsbInDevice);
        mDetectedInChannelCount = getBestSupportedChannelCount(mUsbInDevice);
        mDetectedOutChannelCount = getBestSupportedChannelCount(mUsbOutDevice);

        Log.i(TAG, "updateUsbCapabilities() mUsbInDevice:" + (mUsbInDevice != null)
                + " encoding: " + mDetectedEncoding
                + " sampleRate: " + mDetectedSampleRate
                + " inChannels: " + mDetectedInChannelCount
                + " outChannels: " + mDetectedOutChannelCount);
    }

    @Override
    protected String getTestCategory() {
        return getString(R.string.audio_datapaths_usb_multichannel_input_test);
    }

    @Override
    public String getReportSectionName() {
        return setTestNameSuffix(sCurrentDisplayMode, SECTION_DATA_PATHS_USB_MULTICHANNEL_INPUT);
    }

    private int getBestSupportedEncoding(AudioDeviceInfo devInfo) {
        if (devInfo == null) {
            return BuilderBase.ENCODING_PCM_FLOAT;
        }
        int[] encodings = devInfo.getEncodings();

        int[] preferred = {
                AudioFormat.ENCODING_PCM_FLOAT,
                AudioFormat.ENCODING_PCM_32BIT,
                AudioFormat.ENCODING_PCM_24BIT_PACKED,
                AudioFormat.ENCODING_PCM_16BIT
        };

        for (int p : preferred) {
            for (int s : encodings) {
                if (p == s) {
                    return encodingToMegaAudio(p);
                }
            }
        }
        return BuilderBase.ENCODING_PCM_FLOAT;
    }

    private int encodingToMegaAudio(int audioFormatEncoding) {
        return switch (audioFormatEncoding) {
            case AudioFormat.ENCODING_PCM_FLOAT -> BuilderBase.ENCODING_PCM_FLOAT;
            case AudioFormat.ENCODING_PCM_32BIT -> BuilderBase.ENCODING_PCM_32BIT;
            case AudioFormat.ENCODING_PCM_24BIT_PACKED -> BuilderBase.ENCODING_PCM_24BIT_PACKED;
            case AudioFormat.ENCODING_PCM_16BIT -> BuilderBase.ENCODING_PCM_I16;
            default -> BuilderBase.ENCODING_PCM_FLOAT;
        };
    }

    private int getBestSupportedSampleRate(AudioDeviceInfo devInfo) {
        if (devInfo == null) {
            return 48000;
        }
        int[] rates = devInfo.getSampleRates();
        int highestRate = 0;
        for (int rate : rates) {
            if (rate == 96000) {
                return 96000;
            }
            if (rate > highestRate) {
                highestRate = rate;
            }
        }
        return highestRate > 0 ? highestRate : 48000;
    }

    private int getBestSupportedChannelCount(AudioDeviceInfo devInfo) {
        if (devInfo == null) {
            return 4;
        }
        int[] counts = devInfo.getChannelCounts();
        int minAtLeast4 = Integer.MAX_VALUE;
        for (int count : counts) {
            if (count >= 4 && count < minAtLeast4) {
                minAtLeast4 = count;
            }
        }
        return minAtLeast4 != Integer.MAX_VALUE ? minAtLeast4 : 4;
    }

    @Override
    protected void gatherTestModules(TestManager testManager) {
        Log.i(TAG, "gatherTestModules()");
        updateUsbCapabilities();
        if (mUsbInDevice == null) {
            Log.w(TAG, "No USB input device found during gatherTestModules()");
            return;
        }
        AudioSinkProvider analysisSinkProvider =
                new AppCallbackAudioSinkProvider(mAnalysisCallbackHandler);

        // We only want to test 4 channels as per CDD 5.6/H-1-11
        for (int channelIndex = 0; channelIndex < 4; channelIndex++) {
            AudioSourceProvider sourceProvider =
                    new SparseChannelAudioSourceProvider(1 << channelIndex);
            TestModule module = new TestModule(
                    AudioDeviceInfo.TYPE_USB_DEVICE, mDetectedSampleRate, mDetectedOutChannelCount,
                    AudioDeviceInfo.TYPE_USB_DEVICE, mDetectedSampleRate, mDetectedInChannelCount);
            module.setSectionTitle("USB Multichannel Input (Analysis Ch: " + channelIndex + ")");
            module.setAnalysisChannel(channelIndex);
            module.mEncoding = mDetectedEncoding;
            module.setSources(sourceProvider, analysisSinkProvider);
            module.setDescription("In:" + mDetectedInChannelCount + " Out:" + mDetectedOutChannelCount
                    + " Ch " + channelIndex + " Enc:" + mDetectedEncoding + " Rate:" + mDetectedSampleRate);

            testManager.addTestModule(module);
        }
    }

    private boolean hasAtLeast4Channels(AudioDeviceInfo dev) {
        if (dev == null) {
            return false;
        }
        for (int count : dev.getChannelCounts()) {
            if (count >= 4) {
                return true;
            }
        }
        return false;
    }

    private boolean isMultichannelUsbConnected() {
        return hasAtLeast4Channels(mUsbInDevice) && hasAtLeast4Channels(mUsbOutDevice);
    }

    @Override
    protected void postValidateTestDevices(int numValidTestModules) {
        Log.i(TAG, "postValidateTestDevices() numValidTestModules: " + numValidTestModules);
        boolean isConnected = isMultichannelUsbConnected();

        TextView promptView = (TextView) findViewById(R.id.audio_datapaths_deviceprompt);

        if (mIsHandheld) {
            if (mIsEmulator) {
                promptView.setText(getResources().getString(
                        R.string.audio_datapaths_emulator_autopass));
            } else if (mUsbInterfaceSupport == AudioDeviceUtils.SUPPORTSDEVICE_YES) {
                if (mTestManager.calculatePass() || isConnected) {
                    promptView.setVisibility(View.GONE);
                } else {
                    promptView.setVisibility(View.VISIBLE);
                }
            } else if (mUsbInterfaceSupport == AudioDeviceUtils.SUPPORTSDEVICE_NO) {
                promptView.setText(getResources().getString(
                        R.string.audio_datapaths_usb_nosupport));
            } else {
                // AudioDeviceUtils.SUPPORTSDEVICE_UNDETERMINED
                promptView.setText(getResources().getString(
                        R.string.audio_datapaths_usb_undetermined));
            }
        } else {
            promptView.setText(getResources().getString(
                    R.string.audio_datapaths_nonhandheld_autopass));
        }

        enableTestButtons(numValidTestModules != 0 && isConnected);
    }

    @Override
    protected boolean hasPeripheralSupport() {
        return mUsbInterfaceSupport != AudioDeviceUtils.SUPPORTSDEVICE_NO;
    }

    @Override
    String getRouteDescription() {
        return "usb_multichannel_input";
    }

    @Override
    protected boolean grantAutoPass() {
        return !mIsHandheld || mIsEmulator
                || Build.VERSION.MEDIA_PERFORMANCE_CLASS < Build.VERSION_CODES.CINNAMON_BUN;
    }
}
