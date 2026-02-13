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

import com.google.common.collect.ImmutableSet;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;

import java.util.Collection;
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
    public static String getDeviceTypeName(
        @AudioDeviceInfo.AudioDeviceType int deviceType) {
        String typeName = sDeviceTypeStrings.get(deviceType);
        return typeName != null ? "TYPE_" + typeName : "invalid type";
    }

    /**
     * @param deviceType The AudioDeviceInfo type ID of the desired device.
     * @return a human-readable abreviated device type name.
     */
    public static String getShortDeviceTypeName(
        @AudioDeviceInfo.AudioDeviceType int deviceType) {
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

        AudioManager audioManager = context.getSystemService(AudioManager.class);
        Set<Integer> outputDeviceTypeIds =
                audioManager.getSupportedDeviceTypes(AudioManager.GET_DEVICES_OUTPUTS);
        if (LOG) {
            Log.d(TAG, "Output Device Types:");
            for (Integer type : outputDeviceTypeIds) {
                Log.d(TAG, "  " + getDeviceTypeName(type));
            }
        }

        Set<Integer> inputDeviceTypeIds =
                audioManager.getSupportedDeviceTypes(AudioManager.GET_DEVICES_INPUTS);
        if (LOG) {
            Log.d(TAG, "Input Device Types:");
            for (Integer type : inputDeviceTypeIds) {
                Log.d(TAG, "  " + getDeviceTypeName(type));
            }
        }

        if (outputDeviceTypeIds.contains(AudioDeviceInfo.TYPE_USB_HEADSET)
                && inputDeviceTypeIds.contains(AudioDeviceInfo.TYPE_USB_HEADSET)) {
            return SUPPORTSDEVICE_YES;
        } else {
            return SUPPORTSDEVICE_NO;
        }
    }

    /**
     * Determine device support for a USB interface or headset peripheral.
     *
     * @param context The application context.
     * @return the SUPPORTSDEVICE_ constant indicating support.
     */
    public static int supportsUsbAudio(Context context) {
        if (LOG) {
            Log.d(TAG, "supportsUsbAudio()");
        }
        int hasInterface = supportsUsbAudioInterface(context);
        int hasHeadset = supportsUsbHeadset(context);
        if (LOG) {
            Log.d(TAG, "  hasInterface:" + hasInterface + " hasHeadset:" + hasHeadset);
        }

        // At least one is YES, so YES.
        if (hasInterface == SUPPORTSDEVICE_YES || hasHeadset == SUPPORTSDEVICE_YES) {
            return SUPPORTSDEVICE_YES;
        }

        // Both are NO, so NO
        if (hasInterface == SUPPORTSDEVICE_NO && hasHeadset == SUPPORTSDEVICE_NO) {
            return SUPPORTSDEVICE_NO;
        }

        // Some mixture of NO and UNDETERMINED, so UNDETERMINED
        return SUPPORTSDEVICE_UNDETERMINED;
    }

    /**
     * Supported USB Device information
     * A USB device that is known to work with the test suite, and any latency offset.
     */
    private static class ValidUsbDevice {
        final String name;
        final int vendorId;
        final int productId;
        final double latencyOffsetMills;

        ValidUsbDevice(String name, int vendorId, int productId, double latencyOffsetMills) {
            this.name = name;
            this.vendorId = vendorId;
            this.productId = productId;
            this.latencyOffsetMills = latencyOffsetMills;
        }

        ValidUsbDevice(String name, int vendorId, int productId) {
            this(name, vendorId, productId, 0.0);
        }

        private boolean matches(UsbDevice usbDevice) {
            return usbDevice.getVendorId() == vendorId
                    && usbDevice.getProductId() == productId;
        }

        private static final Set<ValidUsbDevice> VALID_USB_DEVICES =
                ImmutableSet.of(
                    new ValidUsbDevice("Google_Adapter_001", 0x18D1, 0x5025),
                    new ValidUsbDevice("Google_Adapter_002", 0x18D1, 0x5034),
                    new ValidUsbDevice("Xumee_Adapter_001", 0x0BDA, 0x4BE2),
                    new ValidUsbDevice("Xumee_Adapter_002", 0x3302, 0x56c5),
                    new ValidUsbDevice("Moshi_Adapter_001", 0x282B, 0x0033),
                    new ValidUsbDevice("Anker_Adapter_001", 0x0572, 0x1B08, 3.23),
                    new ValidUsbDevice("Realtek_ALC5686_Adapter_001", 0x0BDA, 0x4BD1)
                );
        /**
         * @param usbDevice The USB device to check.
         * @return the ValidUsbDevice if it is known to work with the test suite, or null if it is not.
         */
        @Nullable static final ValidUsbDevice getValidatedUsbDevice(@NonNull UsbDevice usbDevice) {
            for (ValidUsbDevice validUsbDevice : VALID_USB_DEVICES) {
                if (validUsbDevice.matches(usbDevice)) {
                    if (LOG) {
                        Log.d(TAG, "Found valid USB device: " + validUsbDevice.name
                                + " vendorId: " + String.format("0x%04x", validUsbDevice.vendorId)
                                + " productId: "
                                + String.format("0x%04x", validUsbDevice.productId)
                                + " latencyOffsetMills: " + validUsbDevice.latencyOffsetMills);
                    }
                    return validUsbDevice;
                }
            }
            if (LOG) {
                Log.d(TAG, "USB device not found in valid USB devices list: "
                        + String.format("0x%04x", usbDevice.getVendorId())
                        + " "
                        + String.format("0x%04x", usbDevice.getProductId()));
            }
            return null;
        }
    }



    /**
     * Returns the UsbDevice corresponding to any connected USB peripheral.
     * @param context The Application Context.
     * @return the UsbDevice corresponding to any connected USB peripheral.
     */
    public static UsbDevice[] getConnectedUsbDevice(Context context) {
        UsbManager usbManager = context.getSystemService(UsbManager.class);

        if (usbManager == null) {
            Log.e(TAG, "Can't get UsbManager!");
        } else {
            HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
            Collection<UsbDevice> devices = deviceList.values();
            UsbDevice[] deviceArray = new UsbDevice[1];
            deviceArray = (UsbDevice[]) devices.toArray(deviceArray);
            return deviceArray;
        }

        return null;
    }

    public static class UsbDeviceReport {
        public boolean isValid;
        public double latencyOffset; // round-trip latency relative to Google
    }

    /**
     * Checks for any connected USB peripheral that is a valid USB Audio headset adapter.
     * Displays a warning dialog if validity can not be determined.
     * @param context The application context.
     * @return a report with information about validity and latency
     */
    public static UsbDeviceReport validateUsbDevice(Context context) {
        AudioManager audioManager = context.getSystemService(AudioManager.class);

        // Determine if the connected device is a USB Headset
        AudioDeviceInfo inputUsbHeadset = null;
        for (AudioDeviceInfo devInfo : audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)) {
            if (devInfo.getType() == AudioDeviceInfo.TYPE_USB_HEADSET) {
                inputUsbHeadset = devInfo;
                break;
            }
        }

        AudioDeviceInfo outputUsbHeadset = null;
        for (AudioDeviceInfo devInfo : audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            if (devInfo.getType() == AudioDeviceInfo.TYPE_USB_HEADSET) {
                outputUsbHeadset = devInfo;
                break;
            }
        }

        UsbDeviceReport report = new UsbDeviceReport();
        if (inputUsbHeadset != null && outputUsbHeadset != null) {
            // Now see if it is a compatible USB adapter
            UsbDevice[] usbDevices = AudioDeviceUtils.getConnectedUsbDevice(context);
            ValidUsbDevice validUsbDeviceBeingUsed = null;
            int numValidUsbDevices = 0;
            if (usbDevices == null) {
                Log.d(TAG, "No USB device found");
            }else{
                for (UsbDevice usbDevice : usbDevices) {
                    ValidUsbDevice validatedUsbDevice = ValidUsbDevice.getValidatedUsbDevice(usbDevice);
                    if (validatedUsbDevice != null) {
                        validUsbDeviceBeingUsed = validatedUsbDevice;
                        numValidUsbDevices++;
                    }
                }
                if (validUsbDeviceBeingUsed == null) {
                    UsbDeviceWarningDialog warningDialog = new UsbDeviceWarningDialog(context);
                    warningDialog.show();
                } else {
                    if (numValidUsbDevices > 1) {
                        UsbMultipleDeviceWarningDialog warningDialog =
                                new UsbMultipleDeviceWarningDialog(context);
                        warningDialog.show();
                    }
                    report.isValid = true;
                    report.latencyOffset = validUsbDeviceBeingUsed.latencyOffsetMills;
                }
            }
        }
        return report;
    }

    /**
     * @param streamType The AudioTrack stream type ID.
     * @return a human-readable stream type name.
     */
    public static String streamTypeToString(int streamType) {
        return switch (streamType) {
            case AudioManager.STREAM_VOICE_CALL -> "STREAM_VOICE_CALL";
            case AudioManager.STREAM_SYSTEM -> "STREAM_SYSTEM";
            case AudioManager.STREAM_RING -> "STREAM_RING";
            case AudioManager.STREAM_MUSIC -> "STREAM_MUSIC";
            case AudioManager.STREAM_ALARM -> "STREAM_ALARM";
            case AudioManager.STREAM_NOTIFICATION -> "STREAM_NOTIFICATION";
            case AudioManager.STREAM_BLUETOOTH_SCO -> "STREAM_BLUETOOTH_SCO";
            case AudioManager.STREAM_SYSTEM_ENFORCED -> "STREAM_SYSTEM_ENFORCED";
            case AudioManager.STREAM_DTMF -> "STREAM_DTMF";
            case AudioManager.STREAM_TTS -> "STREAM_TTS";
            case AudioManager.STREAM_ACCESSIBILITY -> "STREAM_ACCESSIBILITY";
            case AudioManager.STREAM_ASSISTANT -> "STREAM_ASSISTANT";
            default -> "UNKNOWN_STREAM_TYPE(" + streamType + ")";
        };
    }

    /**
     * @param channelConfig The AudioTrack channel configuration ID.
     * @return a human-readable channel configuration name.
     */
    public static String channelConfigToString(int channelConfig) {
        return switch (channelConfig) {
            case AudioFormat.CHANNEL_OUT_MONO -> "CHANNEL_OUT_MONO";
            case AudioFormat.CHANNEL_OUT_STEREO -> "CHANNEL_OUT_STEREO";
            case AudioFormat.CHANNEL_OUT_QUAD -> "CHANNEL_OUT_QUAD";
            case AudioFormat.CHANNEL_OUT_5POINT1 -> "CHANNEL_OUT_5POINT1";
            case AudioFormat.CHANNEL_OUT_7POINT1 -> "CHANNEL_OUT_7POINT1";
            case AudioFormat.CHANNEL_OUT_DEFAULT -> "CHANNEL_OUT_DEFAULT";
            default -> "UNKNOWN_CHANNEL_CONFIG(" + channelConfig + ")";
        };
    }

    /**
     * @param audioFormat The AudioTrack audio format ID.
     * @return a human-readable audio format name.
     */
    public static String audioFormatToString(int audioFormat) {
        return switch (audioFormat) {
            case AudioFormat.ENCODING_PCM_8BIT -> "ENCODING_PCM_8BIT";
            case AudioFormat.ENCODING_PCM_16BIT -> "ENCODING_PCM_16BIT";
            case AudioFormat.ENCODING_PCM_FLOAT -> "ENCODING_PCM_FLOAT";
            case AudioFormat.ENCODING_AC3 -> "ENCODING_AC3";
            case AudioFormat.ENCODING_E_AC3 -> "ENCODING_E_AC3";
            case AudioFormat.ENCODING_DTS -> "ENCODING_DTS";
            case AudioFormat.ENCODING_DTS_HD -> "ENCODING_DTS_HD";
            case AudioFormat.ENCODING_MP3 -> "ENCODING_MP3";
            case AudioFormat.ENCODING_AAC_LC -> "ENCODING_AAC_LC";
            case AudioFormat.ENCODING_AAC_HE_V1 -> "ENCODING_AAC_HE_V1";
            case AudioFormat.ENCODING_AAC_HE_V2 -> "ENCODING_AAC_HE_V2";
            default -> "UNKNOWN_AUDIO_FORMAT(" + audioFormat + ")";
        };
    }

    /**
     * @param state The AudioTrack state ID.
     * @return a human-readable audio track state name.
     */
    public static String audioTrackStateToString(int state) {
        return switch (state) {
            case AudioTrack.STATE_UNINITIALIZED -> "STATE_UNINITIALIZED";
            case AudioTrack.STATE_INITIALIZED -> "STATE_INITIALIZED";
            case AudioTrack.STATE_NO_STATIC_DATA -> "STATE_NO_STATIC_DATA";
            default -> "UNKNOWN_STATE(" + state + ")";
        };
    }
}
