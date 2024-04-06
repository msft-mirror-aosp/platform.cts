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

import android.app.Dialog;
import android.content.Context;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.audio.Flags;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.webkit.WebView;

import com.android.cts.verifier.R;
import com.android.cts.verifier.audio.audiolib.AudioDeviceUtils;
import com.android.cts.verifier.libs.ui.HtmlFormatter;

import java.util.Set;

public class AudioDevicesDialog extends Dialog implements OnClickListener {
    private Context mContext;
    private AudioManager mAudioManager;

    private WebView mInfoPanel;

    AudioDevicesDialog(Context context) {
        super(context);

        mContext = context;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mAudioManager = mContext.getSystemService(AudioManager.class);
        mAudioManager.registerAudioDeviceCallback(new ConnectionListener(), new Handler());

        setTitle(mContext.getString(R.string.audio_devicesupport_title));

        setContentView(R.layout.audio_devices_dialog);
        getWindow().setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);

        findViewById(R.id.audio_devices_done).setOnClickListener(this);
        mInfoPanel = (WebView) findViewById(R.id.audio_devices_info);

        displayDeviceSupport();
    }

    /**
     * Build and show an HTML page with the device info.
     */
    void displayDeviceSupport() {
        final int headerLevel = 2;

        HtmlFormatter mHtmlFormatter = new HtmlFormatter();
        mHtmlFormatter.openDocument();

        // Supported Devices
        mHtmlFormatter.openHeading(headerLevel)
                .appendText(mContext.getString(R.string.audio_general_supporteddevices))
                .closeHeading(headerLevel);
        mHtmlFormatter.appendText(mContext.getString(R.string.audio_supported_devices_explain))
                .appendBreak()
                .appendBreak();

        if (!Flags.supportedDeviceTypesApi()) {
            // Can't determine support (i.e. the AudioManager.getSupporteDeviceTypes() not
            // unflagged
            mHtmlFormatter.openItalic()
                    .appendText(mContext.getString(R.string.audio_devicesupport_cantdetermine))
                    .closeItalic()
                    .appendBreak();
        } else {
            // Inputs
            mHtmlFormatter.openBold()
                    .appendText(mContext.getString(R.string.audio_general_inputscolon))
                    .appendBreak()
                    .closeBold();

            Set<Integer> inputDevices =
                    mAudioManager.getSupportedDeviceTypes(AudioManager.GET_DEVICES_INPUTS);
            mHtmlFormatter.openBulletList();
            for (Integer deviceType : inputDevices) {
                mHtmlFormatter.openListItem()
                        .appendText(AudioDeviceUtils.getShortDeviceTypeName(deviceType))
                        .closeListItem();
            }
            mHtmlFormatter.closeBulletList();

            // Outputs
            mHtmlFormatter.openBold()
                    .appendText(mContext.getString(R.string.audio_general_outputscolon))
                    .appendBreak()
                    .closeBold();

            Set<Integer> outputDevices =
                    mAudioManager.getSupportedDeviceTypes(AudioManager.GET_DEVICES_OUTPUTS);
            mHtmlFormatter.openBulletList();
            for (Integer deviceType : outputDevices) {
                mHtmlFormatter.openListItem()
                        .appendText(AudioDeviceUtils.getShortDeviceTypeName(deviceType))
                        .closeListItem();
            }
            mHtmlFormatter.closeBulletList();
        }

        // Available Devices
        mHtmlFormatter.openHeading(headerLevel)
                .appendText(mContext.getString(R.string.audio_general_availabledevices))
                .closeHeading(headerLevel);
        mHtmlFormatter.appendText(mContext.getString(R.string.audio_available_devices_explain))
                .appendBreak()
                .appendBreak();

        // Inputs
        mHtmlFormatter.openBold()
                .appendText(mContext.getString(R.string.audio_general_inputscolon))
                .appendBreak()
                .closeBold();

        mHtmlFormatter.openBulletList();
        AudioDeviceInfo[] inputDevices =
                mAudioManager.getDevices(AudioManager.GET_DEVICES_INPUTS);
        for (AudioDeviceInfo deviceInfo : inputDevices) {
            mHtmlFormatter.openListItem()
                    .appendText(AudioDeviceUtils.getShortDeviceTypeName(deviceInfo.getType()))
                    .closeListItem();
        }
        mHtmlFormatter.closeBulletList();

        // Outputs
        mHtmlFormatter.openBold()
                .appendText(mContext.getString(R.string.audio_general_outputscolon))
                .appendBreak()
                .closeBold();

        AudioDeviceInfo[] outputDevices =
                mAudioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
        mHtmlFormatter.openBulletList();
        for (AudioDeviceInfo deviceInfo : outputDevices) {
            mHtmlFormatter.openListItem()
                    .appendText(AudioDeviceUtils.getShortDeviceTypeName(deviceInfo.getType()))
                    .closeListItem();
        }
        mHtmlFormatter.closeBulletList();

        mHtmlFormatter.closeDocument();

        mInfoPanel.loadData(mHtmlFormatter.toString(), "text/html; charset=utf-8", "utf-8");
    }

    //
    // OnClickListener
    //
    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.audio_devices_done) {
            dismiss();
        }
    }

    class ConnectionListener extends AudioDeviceCallback {
        ConnectionListener() {}

        //
        // AudioDevicesManager.OnDeviceConnectionListener
        //
        @Override
        public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
            displayDeviceSupport();
        }

        @Override
        public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
            displayDeviceSupport();
        }
    }
}
