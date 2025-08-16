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

package android.media.audio.app;

import static android.media.AudioManager.RINGER_MODE_NORMAL;
import static android.media.AudioManager.RINGER_MODE_VIBRATE;
import static android.media.AudioManager.STREAM_ALARM;
import static android.media.AudioManager.STREAM_MUSIC;
import static android.media.AudioManager.STREAM_NOTIFICATION;
import static android.media.AudioManager.STREAM_RING;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.media.AudioManager;
import android.os.Vibrator;
import android.util.Log;

import androidx.test.InstrumentationRegistry;

import com.android.compatibility.common.util.AmUtils;
import com.android.compatibility.common.util.SystemUtil;

import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class AudioServiceRebootTest {

    private static final String TAG = "AudioServiceRebootTest";
    private static final String PERSISTENCE_STATE_FILE = "audio_service_reboot_test_state.ser";

    // Test values
    private static final int TEST_RING_VOLUME = 2;
    private static final int TEST_ALARM_VOLUME = 3;
    private static final int TEST_NOTIFICATION_VOLUME = 4;
    private static final int TEST_MUSIC_VOLUME = 5;

    private static final int[] MODIFIED_STREAMS = {
        STREAM_MUSIC,
        STREAM_ALARM,
        STREAM_RING,
        STREAM_NOTIFICATION
    };

    private static class AudioState implements Serializable {
        private static final long serialVersionUID = 1L;
        final Map<Integer, Integer> volumes = new HashMap<>();
        final Map<Integer, Boolean> mutes = new HashMap<>();
        int ringerMode = -1;
    }

    private final Context mContext = InstrumentationRegistry.getTargetContext();
    private final AudioManager mAm = mContext.getSystemService(AudioManager.class);

    @Test
    public void testVolumePersistence_preReboot() throws Exception {
        validateAssumptions();
        assumeStreamVolumeInRange(STREAM_MUSIC, TEST_MUSIC_VOLUME);
        assumeStreamVolumeInRange(STREAM_RING, TEST_RING_VOLUME);
        assumeStreamVolumeInRange(STREAM_ALARM, TEST_ALARM_VOLUME);

        setup();

        mAm.setStreamVolume(STREAM_MUSIC, TEST_MUSIC_VOLUME, 0);
        mAm.setStreamVolume(STREAM_ALARM, TEST_ALARM_VOLUME, 0);
        mAm.adjustStreamVolume(STREAM_RING, AudioManager.ADJUST_MUTE, 0);

        mAm.waitForAudioHandlerBarrier();
        AmUtils.waitForBroadcastBarrier();

        assertThat(mAm.getStreamVolume(STREAM_MUSIC)).isEqualTo(TEST_MUSIC_VOLUME);
        assertThat(mAm.getStreamVolume(STREAM_ALARM)).isEqualTo(TEST_ALARM_VOLUME);
        assertThat(mAm.isStreamMute(STREAM_RING)).isTrue();
    }

    @Test
    public void testVolumePersistence_postReboot() {
        assertThat(mAm.getStreamVolume(STREAM_MUSIC)).isEqualTo(TEST_MUSIC_VOLUME);
        assertThat(mAm.getStreamVolume(STREAM_ALARM)).isEqualTo(TEST_ALARM_VOLUME);
        // TODO this doesn't persist
        // assertThat(mAm.isStreamMute(STREAM_RING)).isTrue();
    }

    @Test
    public void testRingerModeImpliedMute_preReboot() throws Exception {
        validateAssumptions();
        assumeTrue("DUT does not have vibrate mode",
                mContext.getSystemService(Vibrator.class)
                        .hasVibrator());
        assumeTrue(
                "This device's ringer mode does not affect the streams of interest",
                mAm.isStreamAffectedByRingerMode(STREAM_RING)
                && mAm.isStreamAffectedByRingerMode(STREAM_NOTIFICATION));
        assumeStreamVolumeInRange(STREAM_RING, TEST_RING_VOLUME);
        assumeStreamVolumeInRange(STREAM_NOTIFICATION, TEST_NOTIFICATION_VOLUME);

        // Ringer mode is NORMAL following this call
        setup();
        // Set RING, NOTIF volume to non-zero
        mAm.setStreamVolume(STREAM_RING, TEST_RING_VOLUME, 0);
        mAm.setStreamVolume(STREAM_NOTIFICATION, TEST_NOTIFICATION_VOLUME, 0);

        mAm.waitForAudioHandlerBarrier();
        AmUtils.waitForBroadcastBarrier();
        assertThat(mAm.getStreamVolume(STREAM_NOTIFICATION))
                .isEqualTo(TEST_NOTIFICATION_VOLUME);

        setRingerModeAndWait(RINGER_MODE_VIBRATE);

        assertThat(mAm.getRingerMode()).isEqualTo(RINGER_MODE_VIBRATE);
        assertThat(mAm.isStreamMute(STREAM_RING)).isTrue();
        assertThat(mAm.isStreamMute(STREAM_NOTIFICATION)).isTrue();
    }

    @Test
    public void testRingerModeImpliedMute_postReboot() {
        assertThat(mAm.getRingerMode()).isEqualTo(RINGER_MODE_VIBRATE);
        assertThat(mAm.isStreamMute(STREAM_RING)).isTrue();
        assertThat(mAm.isStreamMute(STREAM_NOTIFICATION)).isTrue();
    }

    // Not actually a test, used to restore the original volume/ringer mode
    // Relies on `setup()` being called at the beginning of every test method
    @Test
    public void testPersistence_teardown() throws IOException, ClassNotFoundException {
        // needed to set volumes
        try {
            setRingerModeAndWait(RINGER_MODE_NORMAL);
        } catch (Exception e) {
            Log.e(TAG, "Unexpected: ", e);
        }
        AudioState originalState = readState();
        if (originalState == null) return;
        for (var entry : originalState.volumes.entrySet()) {
            mAm.setStreamVolume(entry.getKey(), entry.getValue(), 0);
        }
        for (var entry : originalState.mutes.entrySet()) {
            mAm.adjustStreamVolume(
                    entry.getKey(),
                    entry.getValue() ? AudioManager.ADJUST_MUTE : AudioManager.ADJUST_UNMUTE,
                    0);
        }
        if (originalState.ringerMode != -1) {
            SystemUtil.runWithShellPermissionIdentity(() ->
                    mAm.setRingerMode(originalState.ringerMode));
        }
        mAm.waitForAudioHandlerBarrier();
        AmUtils.waitForBroadcastBarrier();
    }

    // switches to MODE_NORMAL
    private void setup() throws Exception {
        AudioState originalState = new AudioState();
        originalState.ringerMode = mAm.getRingerMode();
        setRingerModeAndWait(RINGER_MODE_NORMAL);
        for (int stream : MODIFIED_STREAMS) {
            originalState.volumes.put(stream, mAm.getStreamVolume(stream));
            originalState.mutes.put(stream, mAm.isStreamMute(stream));
        }
        saveState(originalState);
    }

    private void setRingerModeAndWait(int mode) throws Exception {
        if (mode == mAm.getRingerMode()) return;
        SystemUtil.runWithShellPermissionIdentity(() -> mAm.setRingerMode(mode));
        mAm.waitForAudioHandlerBarrier();
    }


    private void validateAssumptions() {
        assumeFalse(
                "This device has fixed volume, so the test is not applicable",
                mAm.isVolumeFixed());
        for (int stream : MODIFIED_STREAMS) {
            assumeTrue("Test inapplicable due to stream aliased: " + stream,
                    SystemUtil.runWithShellPermissionIdentity(
                            () -> mAm.getStreamTypeAlias(stream) == stream));
        }
    }

    private void assumeStreamVolumeInRange(int stream, int val) {
        assumeTrue(val >= mAm.getStreamMinVolume(stream) &&
                val <= mAm.getStreamMaxVolume(stream));
    }

    private void saveState(AudioState state) throws IOException {
        File stateFile = new File(mContext.getFilesDir(), PERSISTENCE_STATE_FILE);
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(stateFile))) {
            oos.writeObject(state);
        }
    }

    private AudioState readState() throws IOException, ClassNotFoundException {
        File stateFile = new File(mContext.getFilesDir(), PERSISTENCE_STATE_FILE);
        if (!stateFile.exists()) {
            Log.w(TAG, "Missing state file");
            return null;
        }
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(stateFile))) {
            return (AudioState) ois.readObject();
        }
    }
}
