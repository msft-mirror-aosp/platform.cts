/*
 * Copyright (C) 2015 The Android Open Source Project
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

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;
import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;
import com.android.cts.verifier.audio.audiolib.AudioDeviceUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Audio Frequency Test base activity
 */
public class AudioFrequencyActivity extends PassFailButtons.Activity {
    private static final String TAG = "AudioFrequencyActivity";
    private static final boolean DEBUG = true;

    protected Context mContext;
    protected AudioManager mAudioManager;

    protected AudioDeviceInfo mOutputDevInfo;
    protected AudioDeviceInfo mInputDevInfo;

    // Store AudioDeviceInfo objects for the different types of devices.
    protected Map<Integer, List<AudioDeviceInfo>> mSourceDeviceInfos = new HashMap<>();
    protected Map<Integer, List<AudioDeviceInfo>> mSinkDeviceInfos = new HashMap<>();

    public int mMaxLevel = 0;

    //
    // TODO - These should be refactored into a RefMicActivity class
    // i.e. AudioFrequencyActivity <- RefMicActivity
    private OnBtnClickListener mBtnClickListener = new OnBtnClickListener();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mContext = this;

        mAudioManager = (AudioManager)getSystemService(AUDIO_SERVICE);
        mAudioManager.registerAudioDeviceCallback(new ConnectListener(), new Handler());
        scanPeripheralList(mAudioManager.getDevices(AudioManager.GET_DEVICES_ALL));
    }

    //
    // Common UI Handling
    protected void connectRefMicUI() {
        findViewById(R.id.refmic_tests_yes_btn).setOnClickListener(mBtnClickListener);
        findViewById(R.id.refmic_tests_no_btn).setOnClickListener(mBtnClickListener);
        findViewById(R.id.refmic_test_info_btn).setOnClickListener(mBtnClickListener);

        enableTestUI(false);
    }

    private void showRefMicInfoDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.ref_mic_dlg_caption)
                .setMessage(R.string.ref_mic_dlg_text)
                .setPositiveButton(R.string.audio_general_ok, null)
                .show();
    }

    private class OnBtnClickListener implements OnClickListener {
        @Override
        public void onClick(View v) {
            int id = v.getId();
            if (id == R.id.refmic_tests_yes_btn) {
                recordRefMicStatus(true);
                enableTestUI(true);
                // disable test button so that they will now run the test(s)
                getPassButton().setEnabled(false);
            } else if (id == R.id.refmic_tests_no_btn) {
                recordRefMicStatus(false);
                enableTestUI(false);
                // Allow the user to "pass" the test.
                getPassButton().setEnabled(true);
            } else if (id == R.id.refmic_test_info_btn) {
                showRefMicInfoDialog();
            }
        }
    }

    private void recordRefMicStatus(boolean has) {
        getReportLog().addValue(
                "User reported ref mic availability: ",
                has ? 1.0 : 0,
                ResultType.NEUTRAL,
                ResultUnit.NONE);
    }

    //
    // Overrides
    //
    void enableTestUI(boolean enable) {

    }

    @Override
    public boolean requiresReportLog() {
        return true;
    }

    @Override
    public String getReportFileName() {
        return PassFailButtons.AUDIO_TESTS_REPORT_LOG_NAME;
    }

    void enableLayout(int layoutId, boolean enable) {
        ViewGroup group = (ViewGroup)findViewById(layoutId);
        for (int i = 0; i < group.getChildCount(); i++) {
            group.getChildAt(i).setEnabled(enable);
        }
    }

    public void setMaxLevel() {
        mMaxLevel = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (int)(mMaxLevel), 0);
    }

    public void setMinLevel() {
        mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0);
    }

    public void testMaxLevel() {
        int currentLevel = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        Log.i(TAG, String.format("Max level: %d curLevel: %d", mMaxLevel, currentLevel));
        if (currentLevel != mMaxLevel) {
            new AlertDialog.Builder(this)
                .setTitle(R.string.audio_general_warning)
                .setMessage(R.string.audio_general_level_not_max)
                .setPositiveButton(R.string.audio_general_ok, null)
                .show();
        }
    }

    public int getMaxLevelForStream(int streamType) {
        return mAudioManager.getStreamMaxVolume(streamType);
    }

    public void setLevelForStream(int streamType, int level) {
        try {
            mAudioManager.setStreamVolume(streamType, level, 0);
        } catch (Exception e) {
            Log.e(TAG, "Error setting stream volume: ", e);
        }
    }

    public int getLevelForStream(int streamType) {
        return mAudioManager.getStreamVolume(streamType);
    }

    public void enableUILayout(LinearLayout layout, boolean enable) {
        for (int i = 0; i < layout.getChildCount(); i++) {
            View view = layout.getChildAt(i);
            view.setEnabled(enable);
        }
    }

    protected AudioDeviceInfo getBuiltInMic() {
        List<AudioDeviceInfo> mics = mSourceDeviceInfos.get(AudioDeviceInfo.TYPE_BUILTIN_MIC);
        if (mics == null || mics.isEmpty()) {
            return null;
        }
        AudioDeviceInfo selectedMic = null;
        for (AudioDeviceInfo mic : mics) {
            if (selectedMic == null || mic.getAddress().equals("bottom")) {
                selectedMic = mic;
            }
        }
        return selectedMic;
    }

    protected AudioDeviceInfo getUsbMic() {
        return getUsbMic(null);
    }

    protected AudioDeviceInfo getUsbMic(AudioDeviceInfo excludedDevice) {
        List<AudioDeviceInfo> usbDevices = mSourceDeviceInfos.get(AudioDeviceInfo.TYPE_USB_DEVICE);
        if (usbDevices == null || usbDevices.isEmpty()) {
            return null;
        }
        for (AudioDeviceInfo device : usbDevices) {
            if (excludedDevice == null || device.getId() != excludedDevice.getId()) {
                return device;
            }
        }
        return null;
    }

    protected void showNoBuiltInMicDialog(DialogInterface.OnClickListener confirmListener) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.audio_frequency_test_no_builtin_mic_title)
                .setMessage(
                        R.string.audio_frequency_test_no_builtin_mic_msg)
                .setPositiveButton(
                        R.string.audio_frequency_test_confirm_btn,
                        confirmListener)
                .setNegativeButton(
                        R.string.audio_frequency_test_cancel_btn, null)
                .show();
    }

    protected void showNoPrimaryMicDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.audio_frequency_test_no_primary_mic_title)
                .setMessage(R.string.audio_frequency_test_no_primary_mic_msg)
                .setPositiveButton(R.string.audio_frequency_test_ok_btn, null)
                .show();
    }

    private void scanPeripheralList(AudioDeviceInfo[] devices) {
        mSourceDeviceInfos.clear();
        mSinkDeviceInfos.clear();

        Log.d(TAG, "scanPeripheralList() num: " + devices.length);
        // Any valid peripherals
        for(AudioDeviceInfo devInfo : devices) {
            int type = devInfo.getType();
            if (devInfo.isSource()) {
                if (!mSourceDeviceInfos.containsKey(type)) {
                    mSourceDeviceInfos.put(type, new ArrayList<>());
                }
                mSourceDeviceInfos.get(type).add(devInfo);
            }
            if (devInfo.isSink()) {
                if (!mSinkDeviceInfos.containsKey(type)) {
                    mSinkDeviceInfos.put(type, new ArrayList<>());
                }
                mSinkDeviceInfos.get(type).add(devInfo);
            }
            Log.d(TAG, "scanPeripheralList() devInfo: " + AudioDeviceUtils.formatDeviceName(devInfo)
                    + " isSource: " + devInfo.isSource()
                    + " isSink: " + devInfo.isSink());
        }
    }

    protected AudioDeviceInfo mLatestRoutedDevice;

    private class ConnectListener extends AudioDeviceCallback {
        /*package*/ ConnectListener() {}

        //
        // AudioDevicesManager.OnDeviceConnectionListener
        //
        @Override
        public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
            // Log.i(TAG, "onAudioDevicesAdded() num:" + addedDevices.length);

            scanPeripheralList(mAudioManager.getDevices(AudioManager.GET_DEVICES_ALL));
        }

        @Override
        public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
            // Log.i(TAG, "onAudioDevicesRemoved() num:" + removedDevices.length);

            scanPeripheralList(mAudioManager.getDevices(AudioManager.GET_DEVICES_ALL));
        }
    }

    protected String getEndMessage() {
        return "Routed device: "
                + (mLatestRoutedDevice != null
                        ? AudioDeviceUtils.formatDeviceName(mLatestRoutedDevice)
                        : "null");
    }

//    abstract public void updateConnectStatus();
}
