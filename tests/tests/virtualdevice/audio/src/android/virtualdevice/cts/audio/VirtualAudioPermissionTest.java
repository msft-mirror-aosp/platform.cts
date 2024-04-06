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
package android.virtualdevice.cts.audio;

import static android.Manifest.permission.GRANT_RUNTIME_PERMISSIONS;
import static android.Manifest.permission.MODIFY_AUDIO_ROUTING;
import static android.Manifest.permission.RECORD_AUDIO;
import static android.media.AudioFormat.CHANNEL_IN_MONO;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeNotNull;
import static org.junit.Assume.assumeTrue;

import android.app.Activity;
import android.companion.virtual.VirtualDeviceManager.VirtualDevice;
import android.companion.virtual.VirtualDeviceParams;
import android.companion.virtual.audio.VirtualAudioDevice;
import android.companion.virtual.flags.Flags;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.display.VirtualDisplay;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.UserHandle;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.view.Display;
import android.virtualdevice.cts.common.VirtualDeviceRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.FlakyTest;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Tests for permission behavior with VirtualAudioDevice
 */
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "VirtualDeviceManager cannot be accessed by instant apps")
public class VirtualAudioPermissionTest {
    private static final AudioFormat INJECTION_FORMAT = new AudioFormat.Builder()
            .setSampleRate(44100)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setChannelMask(CHANNEL_IN_MONO)
            .build();

    @Rule
    public VirtualDeviceRule mVirtualDeviceRule = VirtualDeviceRule.withAdditionalPermissions(
            MODIFY_AUDIO_ROUTING, GRANT_RUNTIME_PERMISSIONS);

    @Mock
    VirtualAudioDevice.AudioConfigurationChangeCallback mAudioConfigurationChangeCallback;

    private final Context mContext = getInstrumentation().getTargetContext();
    private int mVirtualDeviceId;
    private int mVirtualDisplayId;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        assumeNotNull(mContext.getSystemService(AudioManager.class));
        assumeTrue(
                mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_MICROPHONE));
    }

    private void setupVirtualDevice(int audioPolicy) {
        VirtualDeviceParams params = new VirtualDeviceParams.Builder()
                .setDevicePolicy(VirtualDeviceParams.POLICY_TYPE_AUDIO, audioPolicy)
                .build();
        VirtualDevice virtualDevice = mVirtualDeviceRule.createManagedVirtualDevice(params);
        mVirtualDeviceId = virtualDevice.getDeviceId();
        VirtualDisplay virtualDisplay = mVirtualDeviceRule.createManagedVirtualDisplay(
                virtualDevice, VirtualDeviceRule.TRUSTED_VIRTUAL_DISPLAY_CONFIG);
        mVirtualDisplayId = virtualDisplay.getDisplay().getDisplayId();

        VirtualAudioDevice virtualAudioDevice = virtualDevice.createVirtualAudioDevice(
                virtualDisplay, Runnable::run, mAudioConfigurationChangeCallback);
        virtualAudioDevice.startAudioInjection(INJECTION_FORMAT);
    }

    @RequiresFlagsEnabled({
            android.companion.virtualdevice.flags.Flags.FLAG_DEVICE_AWARE_RECORD_AUDIO_PERMISSION,
            android.media.audiopolicy.Flags.FLAG_RECORD_AUDIO_DEVICE_AWARE_PERMISSION,
            android.permission.flags.Flags.FLAG_DEVICE_AWARE_PERMISSION_APIS_ENABLED,
            android.permission.flags.Flags.FLAG_DEVICE_AWARE_PERMISSIONS_ENABLED})
    @Test
    public void audioInjection_defaultDevice_works() {
        assertThat(getPermissionState(Display.DEFAULT_DISPLAY))
                .isEqualTo(PackageManager.PERMISSION_GRANTED);
        recordAudio(Display.DEFAULT_DISPLAY);
    }

    @RequiresFlagsEnabled({
            android.companion.virtualdevice.flags.Flags.FLAG_DEVICE_AWARE_RECORD_AUDIO_PERMISSION,
            android.media.audiopolicy.Flags.FLAG_RECORD_AUDIO_DEVICE_AWARE_PERMISSION,
            android.permission.flags.Flags.FLAG_DEVICE_AWARE_PERMISSION_APIS_ENABLED,
            android.permission.flags.Flags.FLAG_DEVICE_AWARE_PERMISSIONS_ENABLED})
    @Test
    public void audioInjection_virtualDevice_permissionNotGranted() {
        setupVirtualDevice(VirtualDeviceParams.DEVICE_POLICY_CUSTOM);
        assertThat(getPermissionState(mVirtualDisplayId))
                .isEqualTo(PackageManager.PERMISSION_DENIED);
        assertThrows(UnsupportedOperationException.class, () -> recordAudio(mVirtualDisplayId));
    }

    @RequiresFlagsEnabled({
            android.companion.virtualdevice.flags.Flags.FLAG_DEVICE_AWARE_RECORD_AUDIO_PERMISSION,
            Flags.FLAG_VDM_PUBLIC_APIS,
            android.media.audiopolicy.Flags.FLAG_RECORD_AUDIO_DEVICE_AWARE_PERMISSION,
            android.permission.flags.Flags.FLAG_DEVICE_AWARE_PERMISSION_APIS_ENABLED,
            android.permission.flags.Flags.FLAG_DEVICE_AWARE_PERMISSIONS_ENABLED})
    @Test
    public void audioInjection_virtualDeviceWithMicrophone_permissionGranted() {
        setupVirtualDevice(VirtualDeviceParams.DEVICE_POLICY_CUSTOM);
        assertThat(getPermissionState(mVirtualDisplayId))
                .isEqualTo(PackageManager.PERMISSION_DENIED);
        assertThrows(UnsupportedOperationException.class, () -> recordAudio(mVirtualDisplayId));

        grantPermission(mVirtualDeviceId);
        assertThat(getPermissionState(mVirtualDisplayId))
                .isEqualTo(PackageManager.PERMISSION_GRANTED);

        recordAudio(mVirtualDisplayId);
    }

    @RequiresFlagsEnabled({
            android.companion.virtualdevice.flags.Flags.FLAG_DEVICE_AWARE_RECORD_AUDIO_PERMISSION,
            Flags.FLAG_VDM_PUBLIC_APIS,
            android.media.audiopolicy.Flags.FLAG_RECORD_AUDIO_DEVICE_AWARE_PERMISSION,
            android.permission.flags.Flags.FLAG_DEVICE_AWARE_PERMISSION_APIS_ENABLED,
            android.permission.flags.Flags.FLAG_DEVICE_AWARE_PERMISSIONS_ENABLED})
    @FlakyTest
    @Test
    public void audioInjection_virtualDeviceWithoutMicrophone_permissionGrantedOnlyOnDefaultDevice() {
        // The POLICY_TYPE_AUDIO for the VirtualDevice is set to DEVICE_POLICY_DEFAULT.
        // Thus device-awareness for the RECORD_AUDIO permission is disabled and falls back to the
        // default device.
        setupVirtualDevice(VirtualDeviceParams.DEVICE_POLICY_DEFAULT);
        assertThat(getPermissionState(Display.DEFAULT_DISPLAY))
                .isEqualTo(PackageManager.PERMISSION_GRANTED);
        assertThat(getPermissionState(mVirtualDisplayId))
                .isEqualTo(PackageManager.PERMISSION_GRANTED);
        recordAudio(mVirtualDisplayId);
    }

    private void grantPermission(int deviceId) {
        Context deviceContext = mContext.createDeviceContext(deviceId);
        deviceContext.getPackageManager().grantRuntimePermission("android.virtualdevice.cts.audio",
                RECORD_AUDIO, UserHandle.of(deviceContext.getUserId()));
    }

    private int getPermissionState(int displayId) {
        PermissionActivity permissionActivity = mVirtualDeviceRule.startActivityOnDisplaySync(
                displayId, PermissionActivity.class);
        int permissionState = permissionActivity.checkSelfPermission(RECORD_AUDIO);
        permissionActivity.finish();
        return permissionState;
    }

    private void recordAudio(int displayId) {
        PermissionActivity permissionActivity = mVirtualDeviceRule.startActivityOnDisplaySync(
                displayId, PermissionActivity.class);
        permissionActivity.recordAudio();
        permissionActivity.finish();
    }

    public static class PermissionActivity extends Activity {
        void recordAudio() {
            AudioRecord audioRecord = new AudioRecord.Builder()
                    .setContext(this)
                    .setAudioSource(MediaRecorder.AudioSource.DEFAULT)
                    .setAudioFormat(new AudioFormat.Builder().setSampleRate(
                            44100).setChannelMask(
                            AudioFormat.CHANNEL_IN_MONO).setEncoding(
                            AudioFormat.ENCODING_PCM_16BIT).build())
                    .build();

            audioRecord.startRecording();

            audioRecord.stop();
            audioRecord.release();
        }
    }
}
