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

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.webkit.WebView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.android.cts.verifier.R;
import com.android.cts.verifier.audio.audiolib.AudioDeviceUtils;
import com.android.cts.verifier.audio.audiolib.AudioSystemFlags;
import com.android.cts.verifier.audio.audiolib.WaveScopeView;

import org.hyphonate.megaaudio.common.BuilderBase;
import org.hyphonate.megaaudio.duplex.DuplexAudioManager;
import org.hyphonate.megaaudio.player.AudioSourceProvider;
import org.hyphonate.megaaudio.player.sources.SparseChannelAudioSourceProvider;
import org.hyphonate.megaaudio.recorder.AudioSinkProvider;
import org.hyphonate.megaaudio.recorder.sinks.AppCallback;
import org.hyphonate.megaaudio.recorder.sinks.AppCallbackAudioSinkProvider;

import java.util.ArrayList;
import java.util.List;

class AudioLoopbackCalibrationDialog extends Dialog
        implements OnClickListener, AppCallback, AdapterView.OnItemSelectedListener {
    public static final String TAG = "AudioLoopbackCalibrationDialog";

    private Context mContext;
    private AudioManager mAudioManager;

    private AudioDeviceConnectionCallback mConnectionListener;

    private DuplexAudioManager mDuplexAudioManager;

    private AudioSinkProvider mAudioSinkProvider;
    private AppCallback mAudioCallbackHandler;

    private boolean mPlaying;
    private int mActiveChannelIndex = -1;
    private int mInputChannels = 2;
    private int mOutputChannels = 2;
    private int mOutputChannelMask = 0;
    private int mSampleRate = 48000;
    private int mEncoding = BuilderBase.ENCODING_PCM_FLOAT;
    private int mNumDisplayChannels;
    private WaveScopeView mWaveView = null;
    private TextView mStatusText;

    public void setOutputChannelMask(int mask) {
        mOutputChannelMask = mask;
        mOutputChannels = Integer.bitCount(mask);
    }

    public void setConfig(int sampleRate, int encoding) {
        mSampleRate = sampleRate;
        mEncoding = encoding;
    }

    Spinner mInputsSpinner;
    Spinner mOutputsSpinner;

    Spinner mChannelSpinner;
    Button mStartButton;
    private List<Integer> mChannelIndices = new ArrayList<>();

    AudioDeviceInfo[] mInputDevices;
    AudioDeviceInfo[] mOutputDevices;

    AudioDeviceInfo mSelectedInputDevice;
    AudioDeviceInfo mSelectedOutputDevice;

    private AudioDeviceInfo mPreselectedInputDevice;
    private AudioDeviceInfo mPreselectedOutputDevice;

    public void setInputDevice(AudioDeviceInfo device) {
        mPreselectedInputDevice = device;
    }

    public void setOutputDevice(AudioDeviceInfo device) {
        mPreselectedOutputDevice = device;
    }

    AudioLoopbackCalibrationDialog(Context context) {
        this(context, 2, 2);
    }

    AudioLoopbackCalibrationDialog(Context context, int inputChannels, int outputChannels) {
        super(context);

        mContext = context;
        mInputChannels = inputChannels;
        mOutputChannels = outputChannels;

        mAudioManager = context.getSystemService(AudioManager.class);

        mAudioCallbackHandler = this;

        mAudioSinkProvider = new AppCallbackAudioSinkProvider(mAudioCallbackHandler);

        mDuplexAudioManager = new DuplexAudioManager(null, null);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTitle(mContext.getString(R.string.audio_datapaths_calibratetitle));

        setContentView(R.layout.audio_loopback_calibration_dialog);
        getWindow().setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        mWaveView = (WaveScopeView) findViewById(R.id.uap_recordWaveView);
        mWaveView.setBackgroundColor(Color.DKGRAY);
        mWaveView.setTraceColor(Color.WHITE);
        mWaveView.setDisplayMaxMagnitudes(true);
        mWaveView.setDisplayLimits(true);
        mWaveView.setDisplayZero(true);

        mStatusText = (TextView) findViewById(R.id.audio_calibration_status);

        mChannelSpinner = (Spinner) findViewById(R.id.audio_calibration_channel_spinner);
        mChannelSpinner.setOnItemSelectedListener(this);
        mStartButton = (Button) findViewById(R.id.audio_calibration_start);
        mStartButton.setOnClickListener(this);

        ArrayAdapter<String> channelAdapter =
                new ArrayAdapter<>(mContext, android.R.layout.simple_spinner_item);
        channelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        mChannelIndices.clear();
        if (mOutputChannelMask != 0) {
            int[] orderedMaskBits = AudioLoopbackUtilitiesHandler.getOrderedMaskBits();
            for (int maskBit : orderedMaskBits) {
                if ((mOutputChannelMask & maskBit) != 0) {
                    channelAdapter.add(AudioLoopbackUtilitiesHandler.getLabelForMaskBit(maskBit));
                    mChannelIndices.add(Integer.bitCount(mOutputChannelMask & (maskBit - 1)));
                }
            }
        } else {
            for (int i = 0; i < mOutputChannels; i++) {
                String label;
                if (mOutputChannels == 1) {
                    label = "Mono";
                } else if (mOutputChannels == 2) {
                    label =
                            i == 0
                                    ? mContext.getString(R.string.audio_general_left)
                                    : mContext.getString(R.string.audio_general_right);
                } else {
                    label = "Channel" + (i + 1);
                }
                channelAdapter.add(label);
                mChannelIndices.add(i);
            }
        }
        mChannelSpinner.setAdapter(channelAdapter);

        findViewById(R.id.audio_calibration_stop).setOnClickListener(this);
        findViewById(R.id.audio_calibration_done).setOnClickListener(this);

        // Setup the Devices spinners
        mInputsSpinner = (Spinner) findViewById(R.id.input_devices_spinner);
        mInputsSpinner.setOnItemSelectedListener(this);

        mOutputsSpinner = (Spinner) findViewById(R.id.output_devices_spinner);
        mOutputsSpinner.setOnItemSelectedListener(this);


        boolean hasWebView = AudioSystemFlags.supportsWebView(mContext);
        View instructionsView = hasWebView ? new WebView(mContext) : new TextView(mContext);

        LinearLayout instructionsFrame = findViewById(R.id.audio_calibration_info);
        instructionsFrame.addView(instructionsView,
                new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT));

        if (AudioSystemFlags.isWatch(mContext)) {
            ((LinearLayout) findViewById(R.id.audio_calibration_process))
                    .setOrientation(LinearLayout.VERTICAL);
        }

        mConnectionListener = new AudioDeviceConnectionCallback();

        if (hasWebView) {
            ((WebView) instructionsView)
                    .loadUrl("file:///android_asset/html/AudioCalibrationInfo.html");
        } else {
            ((TextView) instructionsView)
                    .setText(R.string.audio_calibration_info);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        mAudioManager.registerAudioDeviceCallback(mConnectionListener, null);
    }

    @Override
    public void onStop() {
        stopAudio();
        mAudioManager.unregisterAudioDeviceCallback(mConnectionListener);
        super.onStop();
    }

    ArrayAdapter fillAdapter(AudioDeviceInfo[] deviceInfos) {
        ArrayAdapter arrayAdapter =
                new ArrayAdapter(mContext, android.R.layout.simple_spinner_item);
        arrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        arrayAdapter.add(mContext.getString(R.string.audio_loopback_calibrate_default));
        if (deviceInfos != null) {
            for (AudioDeviceInfo devInfo : deviceInfos) {
                String devTypeString = AudioDeviceUtils.getDeviceTypeName(devInfo.getType());
                String devNameString = devInfo.getAddress();
                StringBuilder sb = new StringBuilder();
                sb.append(devTypeString);
                // We won't filter out "meaningless" address strings
                // like "0" and "card=1;device=0".
                if (devNameString.length() != 0) {
                    sb.append(" (").append(devNameString).append(")");
                }
                arrayAdapter.add(sb.toString());
            }
        }
        return arrayAdapter;
    }

    private void updateButtons() {
        mStartButton.setEnabled(!mPlaying);
        findViewById(R.id.audio_calibration_stop).setEnabled(mPlaying);
        mInputsSpinner.setEnabled(!mPlaying);
        mInputsSpinner.setAlpha(!mPlaying ? 1.0f : 0.5f);
        mOutputsSpinner.setEnabled(!mPlaying);
        mOutputsSpinner.setAlpha(!mPlaying ? 1.0f : 0.5f);
    }

    private void onAudioLoopbackReady() {
        mPlaying = true;
        mStatusText.setText(R.string.audio_loopback_playing);
        mChannelSpinner.setEnabled(true);
        updateButtons();
    }

    private void onAudioLoopbackError(String message) {
        mPlaying = false;
        mStatusText.setText(message);
        mChannelSpinner.setEnabled(true);
        updateButtons();
    }

    void startAudio(int channelIndex) {
        if (mPlaying) {
            return;
        }

        mActiveChannelIndex = channelIndex;
        mStatusText.setText(R.string.audio_loopback_starting);
        mChannelSpinner.setEnabled(false);

        // Ensure any previous audio is completely torn down before starting new initialization
        // because we might change devices/channels.
        mDuplexAudioManager.unwind();

        int mask = 1 << channelIndex;
        AudioSourceProvider sourceProvider = new SparseChannelAudioSourceProvider(mask);

        // Pre-calculate display channels
        mNumDisplayChannels = mInputChannels;
        if (mSelectedInputDevice != null && AudioDeviceUtils.isMicDevice(mSelectedInputDevice)
                && mInputChannels <= 2) {
            mNumDisplayChannels = 1;
        }

        // Player
        mDuplexAudioManager.setSources(sourceProvider, mAudioSinkProvider);
        mDuplexAudioManager.setPlayerRouteDevice(mSelectedOutputDevice);
        mDuplexAudioManager.setPlayerSampleRate(mSampleRate);
        if (mOutputChannelMask != 0) {
            mDuplexAudioManager.setPlayerChannelMask(mOutputChannelMask);
        } else {
            mDuplexAudioManager.setNumPlayerChannels(mOutputChannels);
        }

        // Recorder
        mDuplexAudioManager.setRecorderRouteDevice(mSelectedInputDevice);
        mDuplexAudioManager.setRecorderSampleRate(mSampleRate);

        // Important: Set these BEFORE buildStreams
        mDuplexAudioManager.setNumRecorderChannels(mNumDisplayChannels);
        mDuplexAudioManager.setEncoding(mEncoding);

        // Open the streams.
        int buildStatus =
                mDuplexAudioManager.buildStreams(BuilderBase.TYPE_OBOE, BuilderBase.TYPE_OBOE);

        mWaveView.setNumChannels(mNumDisplayChannels);

        if (buildStatus != DuplexAudioManager.DUPLEX_SUCCESS) {
            Log.e(TAG, "Bad Duplex Build. buildStatus:0x" + Integer.toHexString(buildStatus));
            onAudioLoopbackError("Failed to build audio streams.");
        } else {
            int startStatus = mDuplexAudioManager.start();

            if (startStatus != DuplexAudioManager.DUPLEX_SUCCESS) {
                Log.e(TAG, "Bad Duplex Start. startStatus:0x" + Integer.toHexString(startStatus));
                mDuplexAudioManager.unwind();
                onAudioLoopbackError("Failed to start audio streams.");
            } else {
                onAudioLoopbackReady();
            }
        }
    }

    void stopAudio() {
        if (!mPlaying) {
            return;
        }

        mStatusText.setText(R.string.audio_loopback_stopping);

        // Use stopWithoutUnwind() instead of unwind() for faster silence.
        mDuplexAudioManager.stopWithoutUnwind();
        mPlaying = false;
        mActiveChannelIndex = -1;

        mStatusText.setText("");
        updateButtons();
    }

    @Override
    public void dismiss() {
        // Full teardown on dismiss
        mDuplexAudioManager.unwind();
        mPlaying = false;
        super.dismiss();
    }

    //
    // OnClickListener
    //
    public void onClick(View v) {
        if (v.getId() == R.id.audio_calibration_start) {
            int position = mChannelSpinner.getSelectedItemPosition();
            if (position != AdapterView.INVALID_POSITION) {
                startAudio(mChannelIndices.get(position));
            }
        } else if (v.getId() == R.id.audio_calibration_stop) {
            stopAudio();
        } else if (v.getId() == R.id.audio_calibration_done) {
            dismiss();
        }
    }

    //
    // MegaAudio AppCallback overrides
    //
    @Override
    public void onDataReady(float[] audioData, int numFrames) {
        mWaveView.setPCMFloatBuff(audioData, mNumDisplayChannels, numFrames);
    }

    //
    // AudioDeviceCallback overrides
    //
    private class AudioDeviceConnectionCallback extends AudioDeviceCallback {
        private void setDeviceSelection(Spinner spinner, AudioDeviceInfo[] devices,
                                        AudioDeviceInfo preselectedDevice) {
            if (preselectedDevice != null) {
                for (int i = 0; i < devices.length; i++) {
                    if (devices[i].getId() == preselectedDevice.getId()) {
                        spinner.setSelection(i + 1);
                        break;
                    }
                }
            }
        }

        void stateChangeHandler() {
            if (mPlaying) {
                stopAudio();
            }

            mInputDevices = mAudioManager.getDevices(AudioManager.GET_DEVICES_INPUTS);
            mInputsSpinner.setAdapter(fillAdapter(mInputDevices));
            setDeviceSelection(mInputsSpinner, mInputDevices, mPreselectedInputDevice);

            mOutputDevices = mAudioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
            mOutputsSpinner.setAdapter(fillAdapter(mOutputDevices));
            setDeviceSelection(mOutputsSpinner, mOutputDevices, mPreselectedOutputDevice);
        }

        @Override
        public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
            stateChangeHandler();
        }

        @Override
        public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
            stateChangeHandler();
        }
    }

    //
    // AdapterView.OnItemSelectedListener overrides
    //
    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        if (parent.getId() == R.id.audio_calibration_channel_spinner) {
            int newChannelIndex = mChannelIndices.get(position);
            if (mPlaying && newChannelIndex != mActiveChannelIndex) {
                mChannelSpinner.setEnabled(false);
                stopAudio();
                startAudio(newChannelIndex);
            }
            return;
        }

        if (mPlaying) {
            stopAudio();
        }
        if (parent.getId() == R.id.input_devices_spinner) {
            if (position == 0) {
                mSelectedInputDevice = null;
            } else {
                mSelectedInputDevice = mInputDevices[position - 1];
            }
        } else {
            if (position == 0) {
                mSelectedOutputDevice = null;
            } else {
                mSelectedOutputDevice = mOutputDevices[position - 1];
            }
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        // NOP
    }
}
