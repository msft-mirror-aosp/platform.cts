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

package com.android.cts.verifier.audio;

import android.app.Activity;
import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.view.View;
import android.widget.Button;

import com.android.cts.verifier.R;

class AudioLoopbackUtilitiesHandler implements View.OnClickListener {
    /**
     * Positional channel mask bits and labels ordered by "commonality" across configurations
     * (Stereo, Quad, 5.1, 7.1).
     *
     * <p>Mask Mapping Table:
     * | Label | Bit    | Quad | 5.1 | 7.1 | Description   |
     * |-------|--------|------|-----|-----|---------------|
     * | FL    | 0x04   | X    | X   | X   | Front Left    |
     * | FR    | 0x08   | X    | X   | X   | Front Right   |
     * | BL    | 0x40   | X    | X   | X   | Back Left     |
     * | BR    | 0x80   | X    | X   | X   | Back Right    |
     * | FC    | 0x10   |      | X   | X   | Front Center  |
     * | LFE   | 0x20   |      | X   | X   | Low Frequency |
     * | SL    | 0x800  |      |     | X   | Side Left     |
     * | SR    | 0x1000 |      |     | X   | Side Right    |
     *
     * <p>This order allows users to calibrate/verify common subchannels first as they move through
     * different multichannel tests.
     */
    private static final int[] sMaskBits = {
        AudioFormat.CHANNEL_OUT_FRONT_LEFT,
        AudioFormat.CHANNEL_OUT_FRONT_RIGHT,
        AudioFormat.CHANNEL_OUT_BACK_LEFT,
        AudioFormat.CHANNEL_OUT_BACK_RIGHT,
        AudioFormat.CHANNEL_OUT_FRONT_CENTER,
        AudioFormat.CHANNEL_OUT_LOW_FREQUENCY,
        AudioFormat.CHANNEL_OUT_SIDE_LEFT,
        AudioFormat.CHANNEL_OUT_SIDE_RIGHT,
        AudioFormat.CHANNEL_OUT_FRONT_LEFT_OF_CENTER,
        AudioFormat.CHANNEL_OUT_FRONT_RIGHT_OF_CENTER,
        AudioFormat.CHANNEL_OUT_BACK_CENTER
    };

    private static final String[] sMaskLabels = {
        "FL", "FR", "BL", "BR", "FC", "LFE", "SL", "SR", "FLc", "FRc", "BC"
    };

    /**
     * Returns an array of positional channel mask bits, ordered by "commonality" (e.g. FL/FR first,
     * then BL/BR, then FC/LFE, etc.).
     */
    public static int[] getOrderedMaskBits() {
        return sMaskBits;
    }

    /**
     * Returns the human-readable label (e.g. "FL", "FC") for a specific positional channel bit.
     * Returns null if the bit is not found in the standard list.
     */
    public static String getLabelForMaskBit(int maskBit) {
        for (int i = 0; i < sMaskBits.length; i++) {
            if (sMaskBits[i] == maskBit) {
                return sMaskLabels[i];
            }
        }
        return null;
    }

    Context mContext;

    private Button mCalibrateButton;
    private Button mDevicesButton;

    private int mInputChannels = 2;
    private int mOutputChannels = 2;
    private int mOutputChannelMask = 0;
    private int mSampleRate = 48000;
    private int mEncoding = 2; // ENCODING_PCM_FLOAT

    private AudioDeviceInfo mInputDevice;
    private AudioDeviceInfo mOutputDevice;

    AudioLoopbackUtilitiesHandler(Activity activity) {
        mContext = activity;
        mCalibrateButton = activity.findViewById(R.id.audio_utilities_calibrate_button);
        mCalibrateButton.setOnClickListener(this);

        mDevicesButton = activity.findViewById(R.id.audio_utilities_devices_button);
        mDevicesButton.setOnClickListener(this);
    }

    public void setEnabled(boolean enable) {
        mCalibrateButton.setEnabled(enable);
        mDevicesButton.setEnabled(enable);
    }

    public void setChannels(int inputChannels, int outputChannels) {
        mInputChannels = inputChannels;
        mOutputChannels = outputChannels;
        mOutputChannelMask = 0;
    }

    public void setChannelMasks(int inputChannels, int outputChannelMask) {
        mInputChannels = inputChannels;
        mOutputChannelMask = outputChannelMask;
        mOutputChannels = Integer.bitCount(outputChannelMask);
    }

    public void setConfig(int sampleRate, int encoding) {
        mSampleRate = sampleRate;
        mEncoding = encoding;
    }

    public void setInputDevice(AudioDeviceInfo device) {
        mInputDevice = device;
    }

    public void setOutputDevice(AudioDeviceInfo device) {
        mOutputDevice = device;
    }

    //
    // View.OnClickHandler
    //
    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.audio_utilities_calibrate_button) {
            AudioLoopbackCalibrationDialog dialog =
                    new AudioLoopbackCalibrationDialog(mContext, mInputChannels, mOutputChannels);
            if (mOutputChannelMask != 0) {
                dialog.setOutputChannelMask(mOutputChannelMask);
            }
            dialog.setConfig(mSampleRate, mEncoding);
            dialog.setInputDevice(mInputDevice);
            dialog.setOutputDevice(mOutputDevice);
            dialog.show();
        } else if (id == R.id.audio_utilities_devices_button) {
            (new AudioDevicesDialog(mContext)).show();
        }
    }
}
