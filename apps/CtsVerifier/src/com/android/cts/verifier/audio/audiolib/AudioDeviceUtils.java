/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.cts.verifier.audio.audiolib;

import android.media.AudioDeviceInfo;

import java.util.HashMap;

/**
 * Utility methods for AudioDevices
 */
public class AudioDeviceUtils {
    public static final String TAG = "AudioDeviceUtils";
    /*
     * Channel Mask Utilities
     */
    private static final HashMap<Integer, String> sDeviceTypeStrings =
            new HashMap<Integer, String>();

    private static void initDeviceTypeStrings() {
        sDeviceTypeStrings.put(AudioDeviceInfo.TYPE_UNKNOWN, "UNKNOWN");
        sDeviceTypeStrings.put(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE, "BUILTIN_EARPIECE");
        sDeviceTypeStrings.put(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, "BUILTIN_SPEAKER");
        sDeviceTypeStrings.put(AudioDeviceInfo.TYPE_WIRED_HEADSET, "WIRED_HEADSET");
        sDeviceTypeStrings.put(AudioDeviceInfo.TYPE_WIRED_HEADPHONES, "WIRED_HEADPHONES");
        sDeviceTypeStrings.put(AudioDeviceInfo.TYPE_LINE_ANALOG, "LINE_ANALOG");
        sDeviceTypeStrings.put(AudioDeviceInfo.TYPE_LINE_DIGITAL, "LINE_DIGITAL");
        sDeviceTypeStrings.put(AudioDeviceInfo.TYPE_BLUETOOTH_SCO, "BLUETOOTH_SCO");
        sDeviceTypeStrings.put(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, "BLUETOOTH_A2DP");
        sDeviceTypeStrings.put(AudioDeviceInfo.TYPE_HDMI, "HDMI");
        sDeviceTypeStrings.put(AudioDeviceInfo.TYPE_HDMI_ARC, "HDMI_ARC");
        sDeviceTypeStrings.put(AudioDeviceInfo.TYPE_USB_DEVICE, "USB_DEVICE");
        sDeviceTypeStrings.put(AudioDeviceInfo.TYPE_USB_ACCESSORY, "USB_ACCESSORY");
        sDeviceTypeStrings.put(AudioDeviceInfo.TYPE_DOCK, "DOCK");
        sDeviceTypeStrings.put(AudioDeviceInfo.TYPE_FM, "FM");
        sDeviceTypeStrings.put(AudioDeviceInfo.TYPE_BUILTIN_MIC, "BUILTIN_MIC");
        sDeviceTypeStrings.put(AudioDeviceInfo.TYPE_FM_TUNER, "FM_TUNER");
        sDeviceTypeStrings.put(AudioDeviceInfo.TYPE_TV_TUNER, "TV_TUNER");
        sDeviceTypeStrings.put(AudioDeviceInfo.TYPE_TELEPHONY, "TELEPHONY");
        sDeviceTypeStrings.put(AudioDeviceInfo.TYPE_AUX_LINE, "AUX_LINE");
        sDeviceTypeStrings.put(AudioDeviceInfo.TYPE_IP, "IP");
        sDeviceTypeStrings.put(AudioDeviceInfo.TYPE_BUS, "BUS");
        sDeviceTypeStrings.put(AudioDeviceInfo.TYPE_USB_HEADSET, "USB_HEADSET");
        sDeviceTypeStrings.put(AudioDeviceInfo.TYPE_HEARING_AID, "HEARING_AID");
        sDeviceTypeStrings.put(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE,
                "BUILTIN_SPEAKER_SAFE");
        sDeviceTypeStrings.put(AudioDeviceInfo.TYPE_REMOTE_SUBMIX, "REMOTE_SUBMIX");
        sDeviceTypeStrings.put(AudioDeviceInfo.TYPE_BLE_HEADSET, "BLE_HEADSET");
        sDeviceTypeStrings.put(AudioDeviceInfo.TYPE_BLE_SPEAKER, "BLE_SPEAKER");
        sDeviceTypeStrings.put(AudioDeviceInfo.TYPE_ECHO_REFERENCE, "ECHO_REFERENCE");
        sDeviceTypeStrings.put(AudioDeviceInfo.TYPE_HDMI_EARC, "HDMI_EARC");
        sDeviceTypeStrings.put(AudioDeviceInfo.TYPE_BLE_BROADCAST, "BLE_BROADCAST");
    }

    static {
        initDeviceTypeStrings();
    }

    /**
     * @param deviceType The AudioDeviceInfo type ID of the desired device.
     * @return a human-readable full device type name.
     */
    public static String getDeviceTypeName(int deviceType) {
        String typeName = sDeviceTypeStrings.get(deviceType);
        return typeName != null ? "TYPE_" + typeName : "invalid type";
    }

    /**
     * @param deviceType The AudioDeviceInfo type ID of the desired device.
     * @return a human-readable abreviated device type name.
     */
    public static String getShortDeviceTypeName(int deviceType) {
        String typeName = sDeviceTypeStrings.get(deviceType);
        return typeName != null ? typeName : "invalid type";
    }

    /**
     * @param deviceInfo
     * @return A human-readable description of the specified DeviceInfo
     */
    public static String formatDeviceName(AudioDeviceInfo deviceInfo) {
        StringBuilder sb = new StringBuilder();
        if (deviceInfo != null) {
            sb.append(deviceInfo.getProductName());
            sb.append(" - " + getDeviceTypeName(deviceInfo.getType()));
        } else {
            sb.append("null");
        }

        return sb.toString();
    }

    /**
     * @param deviceInfo Specifies the audio device to characterize.
     * @return true if the device is (probably) a Mic
     */
    public static boolean isMicDevice(AudioDeviceInfo deviceInfo) {
        if (deviceInfo == null || !deviceInfo.isSource()) {
            return false;
        }

        switch (deviceInfo.getType()) {
            case AudioDeviceInfo.TYPE_BUILTIN_MIC:
            case AudioDeviceInfo.TYPE_WIRED_HEADSET:
            case AudioDeviceInfo.TYPE_USB_HEADSET:
                return true;

            default:
                return false;
        }
    }
}
