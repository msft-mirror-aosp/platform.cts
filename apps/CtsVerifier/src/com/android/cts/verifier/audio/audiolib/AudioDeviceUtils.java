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

import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.audio.Flags;
import android.util.Log;

import java.util.HashMap;
import java.util.Set;

/**
 * Utility methods for AudioDevices
 */
public class AudioDeviceUtils {
    private static final String TAG = "AudioDeviceUtils";
    private static final boolean LOG = false;

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

    // return codes for various supports device methods()
    // Does not
    public static final int SUPPORTSDEVICE_NO = 0;
    // Does
    public static final int SUPPORTSDEVICE_YES = 1;
    // AudioManager.getSupportedDeviceTypes() is not implemented
    public static final int SUPPORTSDEVICE_UNDETERMINED = 2;

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

    /**
     * Determine device support for an analog headset.
     *
     * @param context The application context.
     * @return the SUPPORTSDEVICE_ constant indicating support.
     */
    public static int supportsAnalogHeadset(Context context) {
        if (LOG) {
            Log.d(TAG, "supportsAnalogHeadset()");
        }
        if (!Flags.supportedDeviceTypesApi()) {
            return SUPPORTSDEVICE_UNDETERMINED;
        }

        // TYPE_LINE_ANALOG?
        AudioManager audioManager = context.getSystemService(AudioManager.class);

        Set<Integer> deviceTypeIds =
                audioManager.getSupportedDeviceTypes(AudioManager.GET_DEVICES_OUTPUTS);
        if (LOG) {
            for (Integer type : deviceTypeIds) {
                Log.d(TAG, "  " + getDeviceTypeName(type));
            }
        }
        return deviceTypeIds.contains(AudioDeviceInfo.TYPE_WIRED_HEADSET)
                ? SUPPORTSDEVICE_YES : SUPPORTSDEVICE_NO;
    }

    /**
     * Determine device support for a USB audio interface.
     *
     * @param context The application context.
     * @return the SUPPORTSDEVICE_ constant indicating support.
     */
    public static int supportsUsbAudioInterface(Context context) {
        if (LOG) {
            Log.d(TAG, "supportsUsbAudioInterface()");
        }
        if (!Flags.supportedDeviceTypesApi()) {
            return SUPPORTSDEVICE_UNDETERMINED;
        }

        AudioManager audioManager = context.getSystemService(AudioManager.class);
        Set<Integer> deviceTypeIds =
                audioManager.getSupportedDeviceTypes(AudioManager.GET_DEVICES_OUTPUTS);
        if (LOG) {
            for (Integer type : deviceTypeIds) {
                Log.d(TAG, "  " + getDeviceTypeName(type));
            }
        }
        return deviceTypeIds.contains(AudioDeviceInfo.TYPE_USB_DEVICE)
                ? SUPPORTSDEVICE_YES : SUPPORTSDEVICE_NO;
    }

    /**
     * Determine device support for a USB headset peripheral.
     *
     * @param context The application context.
     * @return the SUPPORTSDEVICE_ constant indicating support.
     */
    public static int supportsUsbHeadset(Context context) {
        if (LOG) {
            Log.d(TAG, "supportsUsbHeadset()");
        }
        if (!Flags.supportedDeviceTypesApi()) {
            return SUPPORTSDEVICE_UNDETERMINED;
        }

        AudioManager audioManager = context.getSystemService(AudioManager.class);
        Set<Integer> deviceTypeIds =
                audioManager.getSupportedDeviceTypes(AudioManager.GET_DEVICES_OUTPUTS);
        if (LOG) {
            for (Integer type : deviceTypeIds) {
                Log.d(TAG, "  " + getDeviceTypeName(type));
            }
        }
        return deviceTypeIds.contains(AudioDeviceInfo.TYPE_USB_HEADSET)
                ? SUPPORTSDEVICE_YES : SUPPORTSDEVICE_NO;
    }

    /**
     * Determine device support for a USB interface or headset peripheral.
     *
     * @param context The application context.
     * @return the SUPPORTSDEVICE_ constant indicating support.
     */
    public static int supportsUsbAudio(Context context) {
        if (LOG) {
            Log.d(TAG, "supportsUsbHeadset()");
        }
        int hasInterface = supportsUsbAudioInterface(context);
        int hasHeadset = supportsUsbHeadset(context);
        if (LOG) {
            Log.d(TAG, "  hasInterface:" + hasInterface + " hasHeadset:" + hasHeadset);
        }
        if (hasInterface == SUPPORTSDEVICE_UNDETERMINED
                || hasHeadset == SUPPORTSDEVICE_UNDETERMINED) {
            return SUPPORTSDEVICE_UNDETERMINED;
        }

        if (hasInterface != hasHeadset) {
            return SUPPORTSDEVICE_UNDETERMINED;
        }

        // they are both the same if we get here
        return hasInterface;
    }
}
