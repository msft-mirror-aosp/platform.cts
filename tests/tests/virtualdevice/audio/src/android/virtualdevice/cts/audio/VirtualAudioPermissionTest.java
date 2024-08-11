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
import static android.media.AudioFormat.ENCODING_PCM_16BIT;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeFalse;
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
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.media.audiopolicy.AudioMix;
import android.media.audiopolicy.AudioMixingRule;
import android.media.audiopolicy.AudioPolicy;
import android.os.UserHandle;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.view.Display;
import android.virtualdevice.cts.common.VirtualDeviceRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.FeatureUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Tests for permission behavior with VirtualAudioDevice
 */
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "VirtualDeviceManager cannot be accessed by instant apps")
public class VirtualAudioPermissionTest {


    @Rule
    public VirtualDeviceRule mVirtualDeviceRule = VirtualDeviceRule.withAdditionalPermissions(
            MODIFY_AUDIO_ROUTING, GRANT_RUNTIME_PERMISSIONS);

    @Mock
    VirtualAudioDevice.AudioConfigurationChangeCallback mAudioConfigurationChangeCallback;

    private final Context mContext = getInstrumentation().getTargetContext();
    private VirtualDevice mVirtualDevice;
    private VirtualDisplay mVirtualDisplay;
    private int mVirtualDeviceId;
    private int mVirtualDisplayId;
    private AudioPolicy mAudioPolicy;
    private AudioInjector mAudioInjector;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        assumeNotNull(mContext.getSystemService(AudioManager.class));
        assumeTrue(
                mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_MICROPHONE));
    }

    @After
    public void tearDown() throws Exception {
        if (mAudioPolicy != null) {
            mContext.getSystemService(AudioManager.class).unregisterAudioPolicy(mAudioPolicy);
        }
        if (mAudioInjector != null) {
            mAudioInjector.close();
        }
    }

    @RequiresFlagsEnabled({
            android.companion.virtualdevice.flags.Flags.FLAG_DEVICE_AWARE_RECORD_AUDIO_PERMISSION,
            android.media.audiopolicy.Flags.FLAG_RECORD_AUDIO_DEVICE_AWARE_PERMISSION,
            android.permission.flags.Flags.FLAG_DEVICE_AWARE_PERMISSION_APIS_ENABLED,
            android.permission.flags.Flags.FLAG_DEVICE_AWARE_PERMISSIONS_ENABLED})
    @Test
    public void audioInjection_defaultDevice_works() {
        PermissionActivity permissionActivity = launchPermissionActivity(Display.DEFAULT_DISPLAY);
        assertThat(permissionActivity.checkSelfPermission(RECORD_AUDIO))
                .isEqualTo(PackageManager.PERMISSION_GRANTED);
        permissionActivity.recordAudio();
    }

    @RequiresFlagsEnabled({
            android.companion.virtualdevice.flags.Flags.FLAG_DEVICE_AWARE_RECORD_AUDIO_PERMISSION,
            android.media.audiopolicy.Flags.FLAG_RECORD_AUDIO_DEVICE_AWARE_PERMISSION,
            android.permission.flags.Flags.FLAG_DEVICE_AWARE_PERMISSION_APIS_ENABLED,
            android.permission.flags.Flags.FLAG_DEVICE_AWARE_PERMISSIONS_ENABLED})
    @Test
    public void audioInjection_virtualDevice_permissionNotGranted() throws Exception {
        setupVirtualDevice(VirtualDeviceParams.DEVICE_POLICY_CUSTOM);
        setupVirtualAudioDevice();
        PermissionActivity permissionActivity = launchPermissionActivity(mVirtualDisplayId);

        assertThat(permissionActivity.checkSelfPermission(RECORD_AUDIO))
                .isEqualTo(PackageManager.PERMISSION_DENIED);
        assertThrows(IllegalStateException.class, permissionActivity::recordAudio);
    }

    @RequiresFlagsEnabled({
            android.companion.virtualdevice.flags.Flags.FLAG_DEVICE_AWARE_RECORD_AUDIO_PERMISSION,
            Flags.FLAG_VDM_PUBLIC_APIS,
            android.media.audiopolicy.Flags.FLAG_RECORD_AUDIO_DEVICE_AWARE_PERMISSION,
            android.permission.flags.Flags.FLAG_DEVICE_AWARE_PERMISSION_APIS_ENABLED,
            android.permission.flags.Flags.FLAG_DEVICE_AWARE_PERMISSIONS_ENABLED})
    @Test
    public void audioInjection_virtualDeviceWithMicrophone_permissionGranted() throws Exception {
        setupVirtualDevice(VirtualDeviceParams.DEVICE_POLICY_CUSTOM);
        setupVirtualAudioDevice();
        PermissionActivity permissionActivity = launchPermissionActivity(mVirtualDisplayId);

        grantPermission(mVirtualDeviceId);
        assertThat(permissionActivity.checkSelfPermission(RECORD_AUDIO))
                .isEqualTo(PackageManager.PERMISSION_GRANTED);

        permissionActivity.recordAudio();
    }

    @RequiresFlagsEnabled({
            android.companion.virtualdevice.flags.Flags.FLAG_DEVICE_AWARE_RECORD_AUDIO_PERMISSION,
            Flags.FLAG_VDM_PUBLIC_APIS,
            android.media.audiopolicy.Flags.FLAG_RECORD_AUDIO_DEVICE_AWARE_PERMISSION,
            android.permission.flags.Flags.FLAG_DEVICE_AWARE_PERMISSION_APIS_ENABLED,
            android.permission.flags.Flags.FLAG_DEVICE_AWARE_PERMISSIONS_ENABLED})
    @Test
    public void audioInjection_virtualDeviceWithoutMicrophone_permissionGrantedOnlyOnDefaultDevice() {
        // The POLICY_TYPE_AUDIO for the VirtualDevice is set to DEVICE_POLICY_DEFAULT.
        // Thus device-awareness for the RECORD_AUDIO permission is disabled and falls back to the
        // default device.
        setupVirtualDevice(VirtualDeviceParams.DEVICE_POLICY_DEFAULT);

        PermissionActivity permissionActivity = launchPermissionActivity(Display.DEFAULT_DISPLAY);
        assertThat(permissionActivity.checkSelfPermission(RECORD_AUDIO))
                .isEqualTo(PackageManager.PERMISSION_GRANTED);
        permissionActivity = launchPermissionActivity(mVirtualDisplayId);
        assertThat(permissionActivity.checkSelfPermission(RECORD_AUDIO))
                .isEqualTo(PackageManager.PERMISSION_GRANTED);
        permissionActivity.recordAudio();
    }

    @RequiresFlagsEnabled({
            android.companion.virtualdevice.flags.Flags.FLAG_DEVICE_AWARE_RECORD_AUDIO_PERMISSION,
            Flags.FLAG_VDM_PUBLIC_APIS,
            android.media.audiopolicy.Flags.FLAG_RECORD_AUDIO_DEVICE_AWARE_PERMISSION,
            android.permission.flags.Flags.FLAG_DEVICE_AWARE_PERMISSION_APIS_ENABLED,
            android.permission.flags.Flags.FLAG_DEVICE_AWARE_PERMISSIONS_ENABLED})
    @Test
    public void audioInjection_virtualDeviceWithManualAudioPolicy_permissionGrantedOnlyOnDefaultDevice() {
        // Automotive has its own audio policies that don't play well with the VDM-created ones.
        assumeFalse(FeatureUtil.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE));

        // The POLICY_TYPE_AUDIO for the VirtualDevice is set to DEVICE_POLICY_DEFAULT.
        // Thus device-awareness for the RECORD_AUDIO permission is disabled and falls back to the
        // default device.
        setupVirtualDevice(VirtualDeviceParams.DEVICE_POLICY_DEFAULT);
        PermissionActivity permissionActivity = launchPermissionActivity(Display.DEFAULT_DISPLAY);
        setupAudioPolicy(permissionActivity.getAttributionSource().getUid());

        assertThat(permissionActivity.checkSelfPermission(RECORD_AUDIO))
                .isEqualTo(PackageManager.PERMISSION_GRANTED);
        permissionActivity = launchPermissionActivity(mVirtualDisplayId);
        assertThat(permissionActivity.checkSelfPermission(RECORD_AUDIO))
                .isEqualTo(PackageManager.PERMISSION_DENIED);
        assertThrows(IllegalStateException.class, permissionActivity::recordAudio);

        grantPermission(mVirtualDeviceId);
        assertThat(permissionActivity.checkSelfPermission(RECORD_AUDIO))
                .isEqualTo(PackageManager.PERMISSION_GRANTED);
        permissionActivity.recordAudio();
    }

    private void setupVirtualDevice(int audioPolicy) {
        VirtualDeviceParams params = new VirtualDeviceParams.Builder()
                .setDevicePolicy(VirtualDeviceParams.POLICY_TYPE_AUDIO, audioPolicy)
                .build();
        mVirtualDevice = mVirtualDeviceRule.createManagedVirtualDevice(params);
        mVirtualDeviceId = mVirtualDevice.getDeviceId();
        mVirtualDisplay = mVirtualDeviceRule.createManagedVirtualDisplay(mVirtualDevice,
                VirtualDeviceRule.createTrustedVirtualDisplayConfigBuilder());
        mVirtualDisplayId = mVirtualDisplay.getDisplay().getDisplayId();
    }

    private void setupVirtualAudioDevice() throws InterruptedException, TimeoutException {
        CountDownLatch audioDeviceInitializedLatch = new CountDownLatch(1);
        AudioDeviceCallback audioDeviceCallback = new AudioDeviceCallback() {
            @Override
            public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
                for (AudioDeviceInfo device : addedDevices) {
                    if (device.isSource()
                            && device.getType() == AudioDeviceInfo.TYPE_REMOTE_SUBMIX) {
                        audioDeviceInitializedLatch.countDown();
                        return;
                    }
                }
            }
        };

        AudioManager audioManager = mContext.getSystemService(AudioManager.class);
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, null);

        VirtualAudioDevice virtualAudioDevice = mVirtualDevice.createVirtualAudioDevice(
                mVirtualDisplay, Runnable::run, mAudioConfigurationChangeCallback);
        mAudioInjector = new AudioInjector(AudioInjector.createAudioData(), virtualAudioDevice);
        mAudioInjector.startInjection();

        boolean success = audioDeviceInitializedLatch.await(2, TimeUnit.SECONDS);
        audioManager.unregisterAudioDeviceCallback(audioDeviceCallback);
        if (!success) {
            throw new TimeoutException("Timeout while waiting for audio injection initialization");
        }
    }

    private void setupAudioPolicy(int uid) {
        Context deviceContext = mContext.createDeviceContext(mVirtualDeviceId);
        AudioManager audioManager = deviceContext.getSystemService(AudioManager.class);
        assumeNotNull(audioManager);
        AudioMixingRule mixingRule = new AudioMixingRule.Builder()
                .setTargetMixRole(AudioMixingRule.MIX_ROLE_INJECTOR)
                .addMixRule(AudioMixingRule.RULE_MATCH_UID, uid)
                .build();
        AudioMix audioMix = new android.media.audiopolicy.AudioMix.Builder(mixingRule)
                .setRouteFlags(AudioMix.ROUTE_FLAG_LOOP_BACK)
                .setFormat(AudioInjector.INJECTION_FORMAT)
                .build();
        mAudioPolicy = new AudioPolicy.Builder(deviceContext).addMix(audioMix).build();
        int res = audioManager.registerAudioPolicy(mAudioPolicy);
        assertThat(res).isEqualTo(AudioManager.SUCCESS);
        AudioTrack audioTrackSource = mAudioPolicy.createAudioTrackSource(audioMix);
        audioTrackSource.play();
    }

    private void grantPermission(int deviceId) {
        Context deviceContext = mContext.createDeviceContext(deviceId);
        deviceContext.getPackageManager().grantRuntimePermission("android.virtualdevice.cts.audio",
                RECORD_AUDIO, UserHandle.of(deviceContext.getUserId()));
    }

    private PermissionActivity launchPermissionActivity(int displayId) {
        return mVirtualDeviceRule.startActivityOnDisplaySync(displayId, PermissionActivity.class);
    }

    public static class PermissionActivity extends Activity {
        void recordAudio() {
            AudioRecord audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,
                    AudioInjector.SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, ENCODING_PCM_16BIT,
                    AudioInjector.BUFFER_SIZE_IN_BYTES);

            audioRecord.startRecording();

            audioRecord.stop();
            audioRecord.release();
        }
    }
}
