/*
 * Copyright (C) 2025 The Android Open Source Project
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

import static android.Manifest.permission.MODIFY_AUDIO_ROUTING;
import static android.Manifest.permission.RECORD_AUDIO;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeNotNull;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.media.audio.Flags;
import android.media.audiopolicy.AudioMix;
import android.media.audiopolicy.AudioMixingRule;
import android.media.audiopolicy.AudioPolicy;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for AudioPolicy creation methods using Builders for AudioTrackSource and AudioRecordSink.
 */
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "Requires system access/permissions for AudioPolicy management")
@RequiresFlagsEnabled(Flags.FLAG_AUDIO_POLICY_BUILDERS_API)
public class AudioPolicyBuilderTest {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Rule
    public final AdoptShellPermissionsRule mPermissionRule =
            new AdoptShellPermissionsRule(
                    InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                    MODIFY_AUDIO_ROUTING,
                    RECORD_AUDIO);

    private Context mContext;
    private AudioPolicy mAudioPolicy;
    private AudioManager mAudioManager;
    private AudioMix mPlaybackCaptureMix;
    private AudioMix mAudioInjectionMix;

    private static final AudioFormat PLAYBACK_FORMAT =
            new AudioFormat.Builder()
                    .setSampleRate(44100)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build();

    private static final AudioFormat RECORDING_FORMAT =
            new AudioFormat.Builder()
                    .setSampleRate(44100)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build();

    private static final int BUFFER_SIZE = 2048;
    private static final int MEDIA_SESSION_ID = 8;

    @Before
    public void setUp() {
        mContext = getInstrumentation().getTargetContext();
        mAudioManager = mContext.getSystemService(AudioManager.class);
        assumeNotNull(mAudioManager);

        setupAudioPolicy();
    }

    private void setupAudioPolicy() {
        // Capturing playback mix (used by the AudioRecord sink)
        mPlaybackCaptureMix = createPlaybackCaptureMix(PLAYBACK_FORMAT);
        // Injecting audio mix (used by the AudioTrack source)
        mAudioInjectionMix = createAudioInjectionMix(RECORDING_FORMAT);

        mAudioPolicy =
                new AudioPolicy.Builder(mContext)
                        .addMix(mPlaybackCaptureMix)
                        .addMix(mAudioInjectionMix)
                        .build();

        int res = mAudioManager.registerAudioPolicy(mAudioPolicy);
        assertThat(res).isEqualTo(AudioManager.SUCCESS);
    }

    private AudioMix createPlaybackCaptureMix(AudioFormat format) {
        AudioMixingRule mixingRule =
                new AudioMixingRule.Builder()
                        .setTargetMixRole(AudioMixingRule.MIX_ROLE_PLAYERS)
                        .addMixRule(AudioMixingRule.RULE_MATCH_AUDIO_SESSION_ID, MEDIA_SESSION_ID)
                        .build();

        return new AudioMix.Builder(mixingRule)
                .setFormat(format)
                .setRouteFlags(AudioMix.ROUTE_FLAG_LOOP_BACK)
                .build();
    }

    private AudioMix createAudioInjectionMix(AudioFormat format) {
        // Mix for injecting audio into recording pipeline (Source)
        AudioMixingRule mixingRule =
                new AudioMixingRule.Builder()
                        .setTargetMixRole(AudioMixingRule.MIX_ROLE_INJECTOR)
                        .addMixRule(AudioMixingRule.RULE_MATCH_AUDIO_SESSION_ID, MEDIA_SESSION_ID)
                        .build();

        return new AudioMix.Builder(mixingRule)
                .setFormat(format)
                .setRouteFlags(AudioMix.ROUTE_FLAG_LOOP_BACK)
                .build();
    }

    @Test
    public void createAudioTrackSource_withBuilder_succeedsAndInitializes() {
        AudioTrack.Builder builder =
                new AudioTrack.Builder()
                        .setAudioAttributes(
                                new AudioAttributes.Builder()
                                        // overwritten
                                        .setUsage(AudioAttributes.USAGE_MEDIA)
                                        .build())
                        .setBufferSizeInBytes(BUFFER_SIZE);

        AudioTrack track = mAudioPolicy.createAudioTrackSource(mAudioInjectionMix, builder);

        assertThat(track).isNotNull();
        assertThat(track.getState()).isEqualTo(AudioTrack.STATE_INITIALIZED);

        // Verify mandatory attribute enforcement (USAGE_VIRTUAL_SOURCE)
        assertThat(track.getAudioAttributes().getUsage())
                .isEqualTo(15 /* AudioAttributes.USAGE_VIRTUAL_SOURCE is @hide */);

        track.release();
    }

    @Test
    public void createAudioRecordSink_withBuilder_succeedsAndInitializes() {
        AudioRecord.Builder builder =
                new AudioRecord.Builder()
                        .setAudioAttributes(
                                new AudioAttributes.Builder()
                                        // overwritten
                                        .setInternalCapturePreset(MediaRecorder.AudioSource.MIC)
                                        .build())
                        .setBufferSizeInBytes(BUFFER_SIZE);

        AudioRecord record = mAudioPolicy.createAudioRecordSink(mPlaybackCaptureMix, builder);

        assertThat(record).isNotNull();
        assertThat(record.getState()).isEqualTo(AudioRecord.STATE_INITIALIZED);

        record.release();
    }
}
