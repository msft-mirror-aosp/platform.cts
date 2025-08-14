/*
 * Copyright (C) 2016 The Android Open Source Project
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

package android.media.audio.cts;

import static android.Manifest.permission.MODIFY_AUDIO_SETTINGS_PRIVILEGED;
import static android.app.AppOpsManager.MODE_ALLOWED;
import static android.app.AppOpsManager.MODE_IGNORED;
import static android.app.AppOpsManager.OPSTR_PLAY_AUDIO;
import static android.media.AudioAttributes.ALLOW_CAPTURE_BY_NONE;
import static android.media.AudioAttributes.ALLOW_CAPTURE_BY_SYSTEM;
import static android.media.AudioAttributes.FLAG_BYPASS_MUTE;
import static android.media.AudioManager.ADJUST_MUTE;
import static android.media.AudioManager.ADJUST_UNMUTE;
import static android.media.AudioManager.STREAM_NOTIFICATION;
import static android.media.AudioPlaybackConfiguration.MUTED_BY_APP_OPS;
import static android.media.AudioPlaybackConfiguration.MUTED_BY_CLIENT_VOLUME;
import static android.media.AudioPlaybackConfiguration.MUTED_BY_STREAM_VOLUME;
import static android.media.AudioPlaybackConfiguration.MUTED_BY_VOLUME_SHAPER;
import static android.media.AudioTrack.WRITE_NON_BLOCKING;
import static android.media.cts.AudioHelper.createSoundDataInShortByteBuffer;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.compatibility.common.util.AppOpsUtils.getOpMode;
import static com.android.compatibility.common.util.AppOpsUtils.setOpMode;
import static com.android.media.audio.Flags.FLAG_RING_MY_CAR;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.Manifest;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RawRes;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioAttributes.CapturePolicy;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioPlaybackConfiguration;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.media.VolumeShaper;
import android.media.audio.Flags;
import android.media.audio.cts.AudioTestUtil.SleepAssertIntEquals;
import android.media.cts.TestUtils;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Parcel;
import android.os.Process;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.util.Log;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.FrameworkSpecificTest;
import com.android.internal.annotations.GuardedBy;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

@FrameworkSpecificTest
@RunWith(JUnit4.class)
public class AudioPlaybackConfigurationTest {
    private static final String TAG = "AudioPlaybackConfigurationTest";

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final int TEST_TIMING_TOLERANCE_MS = 150;
    /** acceptable timeout for the time it takes for a prepared MediaPlayer to have an audio device
     * selected and reported when starting to play */
    private static final int PLAY_ROUTING_TIMING_TOLERANCE_MS = 500;
    private static final int TEST_TIMEOUT_SOUNDPOOL_LOAD_MS = 3000;
    private static final long MEDIAPLAYER_PREPARE_TIMEOUT_MS = 2000;

    private static final int TEST_AUDIO_TRACK_SAMPLERATE = 48000;
    private static final double TEST_AUDIO_TRACK_FREQUENCY = 440.0;
    private static final int TEST_AUDIO_TRACK_CHANNELS = 2;
    private static final int TEST_AUDIO_TRACK_PLAY_SECONDS = 2;
    private static final double TEST_AUDIO_TRACK_SWEEP = 0;

    // volume shaper duration in milliseconds.
    private static final long VOLUME_SHAPER_DURATION_MS = 10;

    private static final VolumeShaper.Configuration SHAPER_MUTE =
            new VolumeShaper.Configuration.Builder()
                    .setInterpolatorType(VolumeShaper.Configuration.INTERPOLATOR_TYPE_LINEAR)
                    .setCurve(new float[] { 0.f, 1.f } /* times */,
                            new float[] { 1.f, 0.f } /* volumes */)
                    .setDuration(VOLUME_SHAPER_DURATION_MS)
                    .build();

    /**
     * Duplicating from {@link AudioPlaybackConfiguration} to make sure tests run properly
     * without the newest SDK.
     **/
    private static final int MUTED_BY_PORT_VOLUME = (1 << 6);

    // not declared inside test so it can be released in case of failure
    private MediaPlayer mMp;
    private SoundPool mSp;
    private AudioTrack mAt;

    private static final int TEST_USAGE = AudioAttributes.USAGE_NOTIFICATION;
    private static final int TEST_CONTENT = AudioAttributes.CONTENT_TYPE_MOVIE;
    private static final int TEST_STREAM_FOR_USAGE = STREAM_NOTIFICATION;
    // A combination of less common flags are used intentionally to clearly identify attributes
    private static final int TEST_ATTRIBUTE_FLAGS = AudioAttributes.FLAG_MUTE_HAPTIC;
    private boolean mIsPrivileged = false; // if the player was setup with privileged permission
    private final int mUid = Process.myUid();
    private final int mPid = Process.myPid();

    private final SafeWaitObject mMediaPlayerLock = new SafeWaitObject();
    private final Context mContext = getInstrumentation().getTargetContext();

    @After
    public void tearDown() throws Exception {
        dropShellPermissionIdentity();
        // try/catch for every method in case the tests left the objects in various states
        if (mMp != null) {
            try {
                mMp.stop();
            } catch (Exception e) {
                Log.e(TAG, "Exception in MediaPlayer stop: " + e);
            }
            mMp.release();
            mMp = null;
        }
        if (mSp != null) {
            mSp.release();
            mSp = null;
        }
        if (mAt != null) {
            try {
                mAt.stop();
            } catch (Exception e) {
                Log.e(TAG, "Exception in AudioTrack stop: " + e);
            }
            mAt.release();
            mAt = null;
        }
    }

    // test writing to/ reading from a Parcel for an AudioPlaybackConfiguration instance.
    // Since we can't create an AudioPlaybackConfiguration directly, we first need to
    // play something to get one.
    // FIXME: b/402529329 create and use AudioPlaybackConfiguration test API to test serialization
    @Test
    public void testParcelableWriteToParcel() throws Exception {
        if (!isValidPlatform("testParcelableWriteToParcel")) return;

        // create a player, make it play so we can get an AudioPlaybackConfiguration instance
        AudioManager am = new AudioManager(mContext);
        assertNotNull("Could not create AudioManager", am);
        if (isRingerModeSilent(am)) {
            Log.w(TAG, "skipping testParcelableWriteToParcel for RINGER_MODE_SILENT");
            return;
        }

        final int session = am.generateAudioSessionId();
        final AudioAttributes aa = createTestAudioAttributes(session);
        List<AudioPlaybackConfiguration> configs;
        mMp = createPreparedMediaPlayer(R.raw.sine1khzs40dblong, aa, session);
        startMediaPlayerWithCheck(am, mMp, aa, null);
        configs = am.getActivePlaybackConfigurations();
        assertTrue("No playback reported", configs.size() > 0);
        stopMediaPlayerWithCheck(am, mMp, aa, null);

        AudioPlaybackConfiguration configToParcel = null;
        for (AudioPlaybackConfiguration config : configs) {
            if (config.getAudioAttributes().equals(aa)) {
                configToParcel = config;
                break;
            }
        }

        assertNotNull("Configuration not found during playback", configToParcel);
        assertEquals(0, configToParcel.describeContents());

        final Parcel srcParcel = Parcel.obtain();
        final Parcel dstParcel = Parcel.obtain();

        configToParcel.writeToParcel(srcParcel, 0 /*no public flags for parcelling operations*/);
        dstParcel.appendFrom(srcParcel, 0 /*offset*/, srcParcel.dataSize() /*size*/);
        dstParcel.setDataPosition(0);
        final AudioPlaybackConfiguration restoredConfig =
                AudioPlaybackConfiguration.CREATOR.createFromParcel(dstParcel);

        assertEquals("Marshalled/restored AudioAttributes don't match",
                configToParcel.getAudioAttributes(), restoredConfig.getAudioAttributes());
    }

    @Test
    public void testGetterMediaPlayer() throws Exception {
        if (!isValidPlatform("testGetterMediaPlayer")) return;

        AudioManager am = new AudioManager(mContext);
        assertNotNull("Could not create AudioManager", am);
        if (isRingerModeSilent(am)) {
            Log.w(TAG, "skipping testGetterMediaPlayer for RINGER_MODE_SILENT");
            return;
        }

        final int session = am.generateAudioSessionId();

        final AudioAttributes aa = createTestAudioAttributes(session);

        final Set<AudioPlaybackConfiguration> oldConfigs =
                new HashSet<AudioPlaybackConfiguration>(am.getActivePlaybackConfigurations());

        mMp = createPreparedMediaPlayer(R.raw.sine1khzs40dblong, aa, session);
        List<AudioPlaybackConfiguration> newConfigs = am.getActivePlaybackConfigurations();
        assertEquals(
                "inactive MediaPlayer, number of configs shouldn't have changed",
                0,
                getAddedPlayerConfigs(oldConfigs, newConfigs, session, aa).size());
        startMediaPlayerWithCheck(am, mMp, aa, null /* callback */);
        newConfigs = am.getActivePlaybackConfigurations();
        assertEquals("Expect at least one config after start", 1, newConfigs.size());

        stopMediaPlayerWithCheck(am, mMp, aa, null /* callback */);

        // verify "privileged" fields aren't available through reflection
        final AudioPlaybackConfiguration config = newConfigs.get(0);
        final Class<?> confClass = config.getClass();
        final Method getClientUidMethod = confClass.getDeclaredMethod("getClientUid");
        final Method getClientPidMethod = confClass.getDeclaredMethod("getClientPid");
        final Method getPlayerTypeMethod = confClass.getDeclaredMethod("getPlayerType");
        final Method getSessionIdMethod = confClass.getDeclaredMethod("getSessionId");
        try {
            Integer uid = (Integer) getClientUidMethod.invoke(config, (Object[]) null);
            assertEquals("uid isn't protected", -1 /*expected*/, uid.intValue());
            Integer pid = (Integer) getClientPidMethod.invoke(config, (Object[]) null);
            assertEquals("pid isn't protected", -1 /*expected*/, pid.intValue());
            Integer type = (Integer) getPlayerTypeMethod.invoke(config, (Object[]) null);
            assertEquals("player type isn't protected", -1 /*expected*/, type.intValue());
            Integer sessionId = (Integer) getSessionIdMethod.invoke(config, (Object[]) null);
            assertEquals("session ID isn't protected", 0 /*expected*/, sessionId.intValue());
        } catch (Exception e) {
            fail("Exception thrown during reflection on config privileged fields"+ e);
        }
        assertEquals("spatialized field isn't protected", false, config.isSpatialized());
        assertEquals("sample rate field isn't protected", 0, config.getSampleRate());
        assertEquals("channel mask field isn't protected", 0, config.getChannelMask());
    }

    @Test
    public void testCallbackMediaPlayer() throws Exception {
        if (!isValidPlatform("testCallbackMediaPlayer")) return;

        doTestCallbackMediaPlayer(false /* no custom Handler for callback */);
    }

    @Test
    public void testCallbackMediaPlayerHandler() throws Exception {
        if (!isValidPlatform("testCallbackMediaPlayerHandler")) return;
        doTestCallbackMediaPlayer(true /* use custom Handler for callback */);
    }

    private void doTestCallbackMediaPlayer(boolean useHandlerInCallback) throws Exception {
        final Handler h;
        if (useHandlerInCallback) {
            HandlerThread handlerThread = new HandlerThread(TAG);
            handlerThread.start();
            h = new Handler(handlerThread.getLooper());
        } else {
            h = null;
        }

        AudioManager am = new AudioManager(mContext);
        assertNotNull("Could not create AudioManager", am);
        if (isRingerModeSilent(am)) {
            Log.w(TAG, "skipping doTestCallbackMediaPlayer for RINGER_MODE_SILENT");
            return;
        }

        final int sessionId = am.generateAudioSessionId();
        final AudioAttributes aa = createTestAudioAttributes(sessionId);

        final MyAudioPlaybackCallback callback = new MyAudioPlaybackCallback(sessionId, aa);
        MyAudioPlaybackCallback registeredCallback = null;

        try {
            mMp = createPreparedMediaPlayer(R.raw.sine1khzs40dblong, aa, sessionId);
            am.registerAudioPlaybackCallback(callback, h /*handler*/);
            registeredCallback = callback;

            startMediaPlayerWithCheck(am, mMp, aa, callback);

            pauseMediaPlayerWithCheck(am, mMp, aa, callback);

            // unregister callback and start playback again
            am.unregisterAudioPlaybackCallback(callback);
            registeredCallback = null;

            startMediaPlayerWithCheck(am, mMp, aa, null /* callback */);
            stopMediaPlayerWithCheck(am, mMp, aa, null /* callback */);
        } finally {
            if (registeredCallback != null) {
                am.unregisterAudioPlaybackCallback(registeredCallback);
            }
            if (h != null) {
                h.getLooper().quit();
            }
        }
    }

    @Test
    public void testCallbackMediaPlayerRelease() throws Exception {

        final HandlerThread handlerThread = new HandlerThread(TAG);
        handlerThread.start();
        final Handler h = new Handler(handlerThread.getLooper());

        AudioManager am = new AudioManager(mContext);
        assertNotNull("Could not create AudioManager", am);
        if (isRingerModeSilent(am)) {
            Log.w(TAG, "skipping testCallbackMediaPlayerRelease for RINGER_MODE_SILENT");
            return;
        }

        final int sessionId = am.generateAudioSessionId();
        final AudioAttributes aa = createTestAudioAttributes(sessionId);
        final MyAudioPlaybackCallback callback = new MyAudioPlaybackCallback(sessionId, aa);
        MyAudioPlaybackCallback registeredCallback = null;

        try {
            mMp = createPreparedMediaPlayer(R.raw.sine1khzs40dblong, aa, sessionId);
            am.registerAudioPlaybackCallback(callback, h /*handler*/);
            registeredCallback = callback;

            startMediaPlayerWithCheck(am, mMp, aa, callback);

            // release the player without stopping or pausing it first
            callback.reset();
            mMp.release();

            assertTrue("onPlaybackConfigChanged should have been called for PLAYER_STATE_RELEASED",
                    callback.waitForCallbacks(1, TEST_TIMING_TOLERANCE_MS));
            assertEquals(
                    "Should not have any matched active players after release",
                    0 /*expected*/,
                    callback.getMediaPlayerConfigs().size());
        } finally {
            if (registeredCallback != null) {
                am.unregisterAudioPlaybackCallback(registeredCallback);
            }
            if (h != null) {
                h.getLooper().quit();
            }
        }
    }

    @Test
    public void testGetterSoundPool() throws Exception {
        if (!isValidPlatform("testSoundPool")) return;
        AudioManager am = new AudioManager(mContext);
        assertNotNull("Could not create AudioManager", am);

        final int sessionId = am.generateAudioSessionId();
        final AudioAttributes spAttributes =
                (new AudioAttributes.Builder())
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setAllowedCapturePolicy(ALLOW_CAPTURE_BY_SYSTEM)
                        .build();
        final MyAudioPlaybackCallback callback =
                new MyAudioPlaybackCallback(sessionId, spAttributes);
        MyAudioPlaybackCallback registeredCallback = null;
        int streamId = 0; // SoundPool.play() return non-zero streamID if successful
        try {
            am.registerAudioPlaybackCallback(callback, null /*handler*/);
            registeredCallback = callback;

            mSp = createSoundPool(sessionId, spAttributes);
            streamId = playSoundPool(mSp, mContext);

            Thread.sleep(TEST_TIMING_TOLERANCE_MS);

            mSp.autoPause();
            Thread.sleep(TEST_TIMING_TOLERANCE_MS);

            // query how many active players after pausing
            final List<AudioPlaybackConfiguration> configs = am.getActivePlaybackConfigurations();
            int nbActiveSpPlayersAfterPause = 0;
            for (AudioPlaybackConfiguration apc : configs) {
                if (apc.getPlayerState() == AudioPlaybackConfiguration.PLAYER_STATE_STARTED
                        && apc.getSessionId() == sessionId) {
                    nbActiveSpPlayersAfterPause++;
                }
            }
            assertEquals(
                    "Should not have any active SoundPool player after pausing",
                    0,
                    nbActiveSpPlayersAfterPause);
        } finally {
            stopSoundPool(mSp, streamId);
            if (registeredCallback != null) {
                am.unregisterAudioPlaybackCallback(callback);
            }
        }
    }

    @ApiTest(apis = {"android.media.AudioManager#getActivePlaybackConfigurations",
            "android.media.AudioManager.AudioPlaybackCallback#onPlaybackConfigChanged"})
    @Test
    public void testGetterAndCallbackConsistency() throws Exception {
        if (!isValidPlatform("testGetterAndCallbackConsistency")) return;

        AudioManager am = new AudioManager(mContext);
        assertNotNull("Could not create AudioManager", am);
        if (isRingerModeSilent(am)) {
            Log.w(TAG, "skipping testGetterAndCallbackConsistency for RINGER_MODE_SILENT");
            return;
        }

        final int soundPoolSessionId = am.generateAudioSessionId();
        final int mediaSessionId = am.generateAudioSessionId();
        final AudioAttributes aa = createTestAudioAttributes(mediaSessionId);
        final MyAudioPlaybackCallback callback = new MyAudioPlaybackCallback(mediaSessionId, aa);
        am.registerAudioPlaybackCallback(callback, null /*handler*/);

        final AudioAttributes spAttributes =
                (new AudioAttributes.Builder())
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setAllowedCapturePolicy(ALLOW_CAPTURE_BY_SYSTEM)
                        .build();
        mSp = createSoundPool(soundPoolSessionId, spAttributes);

        mMp = createPreparedMediaPlayer(R.raw.sine1khzs40dblong, aa, mediaSessionId);
        int streamId = 0; // SoundPool.play() return non-zero streamID if successful
        try {
            streamId = playSoundPool(mSp, mContext);
            callback.reset();
            startMediaPlayerWithCheck(am, mMp, aa, callback);

            mSp.autoPause();
            stopMediaPlayerWithCheck(am, mMp, aa, callback);
        } finally {
            stopSoundPool(mSp, streamId);
            am.unregisterAudioPlaybackCallback(callback);
        }
    }

    private void testGetAudioDeviceInfoMediaPlayerStart(boolean enableRoutedDeviceIdsFlag)
            throws Exception {
        if (!isValidPlatform("testGetAudioDeviceInfoMediaPlayerStart")) return;

        final HandlerThread handlerThread = new HandlerThread(TAG);
        handlerThread.start();
        final Handler h = new Handler(handlerThread.getLooper());

        AudioManager am = new AudioManager(mContext);
        assertNotNull("Could not create AudioManager", am);
        if (isRingerModeSilent(am)) {
            Log.w(TAG, "skipping testGetAudioDeviceInfoMediaPlayerStart for RINGER_MODE_SILENT");
            return;
        }

        final int sessionId = am.generateAudioSessionId();
        final AudioAttributes aa = createTestAudioAttributes(sessionId);
        final MyAudioPlaybackCallback callback = new MyAudioPlaybackCallback(sessionId, aa);
        MyAudioPlaybackCallback registeredCallback = null;

        try {
            adoptShellPermissionIdentity(Manifest.permission.MODIFY_AUDIO_ROUTING);
            mMp = createPreparedMediaPlayer(R.raw.sine1khzs40dblong, aa, sessionId);
            am.registerAudioPlaybackCallback(callback, h /*handler*/);
            registeredCallback = callback;

            startMediaPlayerWithCheck(am, mMp, aa, callback);
            synchronized (mMediaPlayerLock) {
                // wait for the new configuration to propagate
                assertTrue(
                        "No matching AudioAttributes from CB in time",
                        mMediaPlayerLock.waitFor(
                                TEST_TIMING_TOLERANCE_MS + PLAY_ROUTING_TIMING_TOLERANCE_MS,
                                () ->
                                        hasDevice(
                                                callback.getMediaPlayerConfigs(),
                                                aa,
                                                enableRoutedDeviceIdsFlag)));
            }
            stopMediaPlayerWithCheck(am, mMp, aa, callback);
        } finally {
            if (registeredCallback != null) {
                am.unregisterAudioPlaybackCallback(registeredCallback);
            }
            dropShellPermissionIdentity();
            if (h != null) {
                h.getLooper().quit();
            }
        }
    }

    @Test
    public void testGetAudioDeviceInfoMediaPlayerStart() throws Exception {
        testGetAudioDeviceInfoMediaPlayerStart(false /*enableRoutedDeviceIdsFlag*/);
    }

    @RequiresFlagsEnabled(Flags.FLAG_ROUTED_DEVICE_IDS)
    @Test
    public void testGetAudioDeviceInfosMediaPlayerStart() throws Exception {
        testGetAudioDeviceInfoMediaPlayerStart(true /*enableRoutedDeviceIdsFlag*/);
    }

    @ApiTest(apis = {"android.media.AudioManager#getActivePlaybackConfigurations",
            "android.media.AudioManager.AudioPlaybackCallback#onPlaybackConfigChanged",
            "android.media.AudioManager.AudioPlaybackCallback#isMuted",
            "android.media.AudioManager.AudioPlaybackCallback#getMutedBy"})
    @Test
    public void testAudioTrackMuteFromAppOpsNotification() throws Exception {
        if (isWatch()) {
            Log.w(TAG, "Skip testAudioTrackMuteFromAppOpsNotification for Wear");
            return;
        }
        if (!isValidPlatform("testAudioTrackMuteFromAppOpsNotification")) return;

        AudioManager am = new AudioManager(mContext);
        final int sessionId = am.generateAudioSessionId();
        final AudioAttributes aa = createTestAudioAttributes(sessionId);
        initializeAudioTrack(aa, sessionId);
        checkMuteFromAppOpsNotification(new MyPlayer(mAt), aa);
    }

    @ApiTest(apis = {"android.media.AudioManager#getActivePlaybackConfigurations",
            "android.media.AudioManager.AudioPlaybackCallback#onPlaybackConfigChanged",
            "android.media.AudioManager.AudioPlaybackCallback#isMuted",
            "android.media.AudioManager.AudioPlaybackCallback#getMutedBy"})
    @Test
    public void testMediaPlayerMuteFromAppOpsNotification() throws Exception {
        if (isWatch()) {
            Log.w(TAG, "Skip testMediaPlayerMuteFromAppOpsNotification for Wear");
            return;
        }
        if (!isValidPlatform("testMediaPlayerMuteFromAppOpsNotification")) return;

        AudioManager am = new AudioManager(mContext);
        final int sessionId = am.generateAudioSessionId();
        final AudioAttributes aa = createTestAudioAttributes(sessionId);
        initializeMediaPlayer(aa, sessionId);
        checkMuteFromAppOpsNotification(new MyPlayer(mMp), aa);
    }

    private void checkMuteFromAppOpsNotification(
            @NonNull MyPlayer player, @NonNull AudioAttributes aa) throws Exception {
        verifyMuteUnmuteNotifications(
                /* start= */ player.mPlay,
                /* mute= */ () -> {
                    try {
                        setOpMode(mContext.getPackageName(), OPSTR_PLAY_AUDIO, MODE_IGNORED);
                    } catch (IOException e) {
                        fail("Failed to set AppOps ignore for play audio: " + e);
                    }
                },
                /* unmute= */ () -> {
                    try {
                        if (getOpMode(mContext.getPackageName(), OPSTR_PLAY_AUDIO)
                                != MODE_ALLOWED) {
                            setOpMode(
                                    mContext.getPackageName(), OPSTR_PLAY_AUDIO, MODE_ALLOWED);
                        }
                    } catch (IOException e) {
                        fail("Failed to set AppOps allow for play audio: " + e);
                    }
                },
                /* muteChangesActiveState= */ true,
                MUTED_BY_APP_OPS,
                player.mSessionId,
                aa);
    }

    @ApiTest(apis = {"android.media.AudioManager#getActivePlaybackConfigurations",
            "android.media.AudioManager.AudioPlaybackCallback#onPlaybackConfigChanged",
            "android.media.AudioManager.AudioPlaybackCallback#isMuted",
            "android.media.AudioManager.AudioPlaybackCallback#getMutedBy"})
    @Test
    public void testAudioTrackMuteFromStreamVolumeNotification() throws Exception {
        if (isAutomotive()) {
            Log.w(TAG, "Skip testAudioTrackMuteFromStreamVolumeNotification for Auto");
            return;
        }
        if (!isValidPlatform("testAudioTrackMuteFromStreamVolumeNotification")) return;

        AudioManager am = new AudioManager(mContext);
        final int sessionId = am.generateAudioSessionId();
        final AudioAttributes aa = createTestAudioAttributes(sessionId);
        initializeAudioTrack(aa, sessionId);
        checkMuteFromStreamVolumeNotification(new MyPlayer(mAt), aa);
    }

    /**
     * Verify that a privileged player with AudioAttributes.FLAG_BYPASS_MUTE doesn't get muted
     * @throws Exception on failure
     */
    @RequiresFlagsEnabled(FLAG_RING_MY_CAR)
    @ApiTest(apis = {"android.media.AudioAttributes#FLAG_BYPASS_MUTE"})
    @Test
    public void testAudioTrackBypassMute() throws Exception {
        if (isAutomotive()) {
            Log.w(TAG, "Skip testAudioTrackBypassMute for Auto");
            return;
        }
        if (!isValidPlatform("testAudioTrackBypassMute")) return;
        AudioManager am = new AudioManager(mContext);
        final int sessionId = am.generateAudioSessionId();
        final AudioAttributes aa = new AudioAttributes.Builder()
                .setUsage(TEST_USAGE)
                .setContentType(TEST_CONTENT)
                .setFlags(FLAG_BYPASS_MUTE)
                .setTestId(sessionId)
                .build();
        final int useableVolumeIndex = am.getStreamMinVolume(TEST_STREAM_FOR_USAGE)
                + (am.getStreamMaxVolume(TEST_STREAM_FOR_USAGE)
                - am.getStreamMinVolume(TEST_STREAM_FOR_USAGE)) / 2;
        final SleepAssertIntEquals volumeCheck = new SleepAssertIntEquals(
                /*maxWaitMs=*/ 2000, /*periodMs*/ 100, mContext);
        try {
            // prepare playback and playback monitoring callback
            adoptShellPermissionIdentity(Manifest.permission.MODIFY_AUDIO_ROUTING);
            initializeAudioTrack(aa, sessionId);
            final MyAudioPlaybackCallback callback = new MyAudioPlaybackCallback(sessionId, aa);
            am.registerAudioPlaybackCallback(callback, null /*handler*/);
            dropShellPermissionIdentity();

            // make sure notifications are at non-zero volume, and then mute them
            am.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
            am.setStreamVolume(TEST_STREAM_FOR_USAGE, useableVolumeIndex, 0 /*flags*/);
            adjustMuteStreamVolume(am);

            volumeCheck.assertEqualsSleep(/*expected*/ 0,
                    () -> am.getStreamVolume(TEST_STREAM_FOR_USAGE),
                    "AudioManager.getStreamVolume(" + TEST_STREAM_FOR_USAGE + ") should be 0");

            // test predicate: the player is playing and is not muted
            Predicate<List<AudioPlaybackConfiguration>> verifyPlayerNotMuted = l -> {
                int i = -1;
                Log.i(TAG, "-- callback received " + l.size() + " configs");
                for (AudioPlaybackConfiguration apc : l) {
                    i++;
                    Log.i(TAG, "callback apc(" + i + ")=" + apc);
                    // is it the player we're looking for?
                    if (apc.getAudioAttributes().getTestId() != sessionId) {
                        Log.i(TAG, "apc " + i + " is not for session " + sessionId);
                        return false;
                    }
                    // is it started?
                    if (apc.getPlayerState() != AudioPlaybackConfiguration.PLAYER_STATE_STARTED) {
                        Log.i(TAG, "apc " + i + " is not STARTED");
                        return false;
                    }
                    // is it muted by the stream volume?
                    if ((apc.getMutedBy() & AudioPlaybackConfiguration.MUTED_BY_PORT_VOLUME) != 0) {
                        Log.i(TAG, "apc " + i + " mutedBy = 0x"
                                + Integer.toHexString(apc.getMutedBy()));
                        return false;
                    }
                    Log.i(TAG, "predicate validated");
                    return true;
                }
                Log.i(TAG, "predicate remained false");
                return false;
            };
            callback.setPredicate(verifyPlayerNotMuted);

            mAt.play();
            Log.i(TAG, "track started");
            assertTrue("onPlaybackConfigChanged predicate remained false",
                    callback.waitForPredicate(
                            TEST_TIMING_TOLERANCE_MS + PLAY_ROUTING_TIMING_TOLERANCE_MS));
        } finally {
            adjustUnMuteStreamVolume(am);
            am.setStreamVolume(TEST_STREAM_FOR_USAGE, useableVolumeIndex, 0 /*flags*/);
            volumeCheck.assertEqualsSleep(/*expected*/ useableVolumeIndex,
                    () -> am.getStreamVolume(TEST_STREAM_FOR_USAGE),
                    "finally: AudioManager.getStreamVolume(" + TEST_STREAM_FOR_USAGE + ")");
        }
    }


    @ApiTest(apis = {"android.media.AudioManager#getActivePlaybackConfigurations",
            "android.media.AudioManager.AudioPlaybackCallback#onPlaybackConfigChanged",
            "android.media.AudioManager.AudioPlaybackCallback#isMuted",
            "android.media.AudioManager.AudioPlaybackCallback#getMutedBy"})
    @Test
    public void testMediaPlayerMuteFromStreamVolumeNotification() throws Exception {
        if (isAutomotive()) {
            Log.w(TAG, "Skip testMediaPlayerMuteFromStreamVolumeNotification for Auto");
            return;
        }
        if (!isValidPlatform("testMediaPlayerMuteFromStreamVolumeNotification")) return;

        AudioManager am = new AudioManager(mContext);
        final int sessionId = am.generateAudioSessionId();
        final AudioAttributes aa = createTestAudioAttributes(sessionId);
        initializeMediaPlayer(aa, sessionId);
        checkMuteFromStreamVolumeNotification(new MyPlayer(mMp), aa);
    }

    private void checkMuteFromStreamVolumeNotification(MyPlayer player, @NonNull AudioAttributes aa)
            throws Exception {
        AudioManager am = new AudioManager(mContext);
        assertNotNull("Could not create AudioManager", am);

        if (am.isVolumeFixed()) {
            Log.w(TAG, "Skipping testMuteFromStreamVolumeNotification, device has volume fixed.");
            return;
        }

        verifyMuteUnmuteNotifications(
                /* start= */ player.mPlay,
                /* mute= */ () -> adjustMuteStreamVolume(am),
                /* unmute= */ () -> adjustUnMuteStreamVolume(am),
                /* muteChangesActiveState= */ false,
                MUTED_BY_STREAM_VOLUME | MUTED_BY_PORT_VOLUME,
                player.mSessionId,
                aa);
    }

    @ApiTest(apis = {"android.media.AudioManager#getActivePlaybackConfigurations",
            "android.media.AudioManager.AudioPlaybackCallback#onPlaybackConfigChanged",
            "android.media.AudioManager.AudioPlaybackCallback#isMuted",
            "android.media.AudioManager.AudioPlaybackCallback#getMutedBy"})
    @Test
    public void testAudioTrackMuteFromClientVolumeNotification() throws Exception {
        if (!isValidPlatform("testAudioTrackMuteFromClientVolumeNotification")) return;

        AudioManager am = new AudioManager(mContext);
        final int sessionId = am.generateAudioSessionId();
        final AudioAttributes aa = createTestAudioAttributes(sessionId);
        initializeAudioTrack(aa, sessionId);
        checkMuteFromClientVolumeNotification(new MyPlayer(mAt), aa);
    }

    @ApiTest(apis = {"android.media.AudioManager#getActivePlaybackConfigurations",
            "android.media.AudioManager.AudioPlaybackCallback#onPlaybackConfigChanged",
            "android.media.AudioManager.AudioPlaybackCallback#isMuted",
            "android.media.AudioManager.AudioPlaybackCallback#getMutedBy"})
    @Test
    public void testMediaPlayerMuteFromClientVolumeNotification() throws Exception {
        if (!isValidPlatform("testMediaPlayerMuteFromClientVolumeNotification")) return;

        AudioManager am = new AudioManager(mContext);
        final int sessionId = am.generateAudioSessionId();
        final AudioAttributes aa = createTestAudioAttributes(sessionId);
        initializeMediaPlayer(aa, sessionId);
        checkMuteFromClientVolumeNotification(new MyPlayer(mMp), aa);
    }

    private void checkMuteFromClientVolumeNotification(MyPlayer player, @NonNull AudioAttributes aa)
            throws Exception {
        verifyMuteUnmuteNotifications(
                /* start= */ player.mPlay,
                /* mute= */ () -> player.mSetClientVolume.accept(0.f),
                /* unmute= */ () -> player.mSetClientVolume.accept(1.f),
                /* muteChangesActiveState= */ true,
                MUTED_BY_CLIENT_VOLUME,
                player.mSessionId,
                aa);
    }

    @ApiTest(apis = {"android.media.AudioManager#getActivePlaybackConfigurations",
            "android.media.AudioManager.AudioPlaybackCallback#onPlaybackConfigChanged",
            "android.media.AudioManager.AudioPlaybackCallback#isMuted",
            "android.media.AudioManager.AudioPlaybackCallback#getMutedBy"})
    @Test
    public void testAudioTrackMuteFromVolumeShaperNotification() throws Exception {
        if (!isValidPlatform("testAudioTrackMuteFromVolumeShaperNotification")) return;

        AudioManager am = new AudioManager(mContext);
        final int sessionId = am.generateAudioSessionId();
        final AudioAttributes aa = createTestAudioAttributes(sessionId);
        initializeAudioTrack(aa, sessionId);
        checkMuteFromVolumeShaperNotification(new MyPlayer(mAt), aa, sessionId);
    }

    @ApiTest(apis = {"android.media.AudioManager#getActivePlaybackConfigurations",
            "android.media.AudioManager.AudioPlaybackCallback#onPlaybackConfigChanged",
            "android.media.AudioManager.AudioPlaybackCallback#isMuted",
            "android.media.AudioManager.AudioPlaybackCallback#getMutedBy"})
    @Test
    public void testMediaPlayerMuteFromVolumeShaperNotification() throws Exception {
        if (!isValidPlatform("testMediaPlayerMuteFromVolumeShaperNotification")) return;

        AudioManager am = new AudioManager(mContext);
        final int sessionId = am.generateAudioSessionId();
        final AudioAttributes aa = createTestAudioAttributes(sessionId);
        initializeMediaPlayer(aa, sessionId);
        checkMuteFromVolumeShaperNotification(new MyPlayer(mMp), aa, sessionId);
    }

    private void checkMuteFromVolumeShaperNotification(MyPlayer player, @NonNull AudioAttributes aa,
            int sessionId) throws Exception {
        try (VolumeShaper myMuteShaper = player.mCreateVolumeShaper.apply(SHAPER_MUTE)) {
            verifyMuteUnmuteNotifications(
                    /* start= */ player.mPlay,
                    /* mute= */ () -> {
                        myMuteShaper.apply(VolumeShaper.Operation.PLAY);
                    },
                    /* unmute= */ () -> {
                        myMuteShaper.replace(
                                SHAPER_MUTE, VolumeShaper.Operation.REVERSE, /* join= */ false);
                        myMuteShaper.apply(VolumeShaper.Operation.PLAY);
                    },
                    /* muteChangesActiveState= */ true,
                    MUTED_BY_VOLUME_SHAPER,
                    player.mSessionId,
                    aa);
        } catch (Exception e) {
            fail("Exception thrown during verifyMuteUnmuteNotifications " + e);
        }
    }

    private void verifyMuteUnmuteNotifications(
            Runnable start,
            Runnable mute,
            Runnable unmute,
            boolean muteChangesActiveState,
            int checkFlag,
            int sessionId,
            @NonNull AudioAttributes aa)
            throws Exception {
        AudioManager am = new AudioManager(mContext);
        assertNotNull("Could not create AudioManager", am);
        if (isRingerModeSilent(am)) {
            Log.w(TAG, "skipping verifyMuteUnmuteNotifications for RINGER_MODE_SILENT");
            return;
        }

        Thread.sleep(TEST_TIMING_TOLERANCE_MS + PLAY_ROUTING_TIMING_TOLERANCE_MS);

        final MyAudioPlaybackCallback callback = new MyAudioPlaybackCallback(sessionId, aa);
        MyAudioPlaybackCallback registeredCallback = null;

        try {
            am.registerAudioPlaybackCallback(callback, null /*handler*/);
            registeredCallback = callback;

            Predicate<List<AudioPlaybackConfiguration>> verifyMediaPlayerStarted = l ->
            {
                if (l.size() != 1) {
                    return false;
                }
                AudioPlaybackConfiguration apc = l.get(0);
                if (apc.getAudioAttributes().getTestId() != sessionId) {
                    return false;
                }
                if (apc.getPlayerState() != AudioPlaybackConfiguration.PLAYER_STATE_STARTED) {
                    return false;
                }
                return true;
            };

            callback.setPredicate(verifyMediaPlayerStarted);

            // start playing audio
            start.run();

            if (muteChangesActiveState) {
                assertTrue(
                        "onPlaybackConfigChanged predicate remained false",
                        callback.waitForPredicate(
                                TEST_TIMING_TOLERANCE_MS + PLAY_ROUTING_TIMING_TOLERANCE_MS));
            }

            Thread.sleep(TEST_TIMING_TOLERANCE_MS + PLAY_ROUTING_TIMING_TOLERANCE_MS);

            // mute with Runnable
            callback.reset();
            mute.run();

            if (muteChangesActiveState) {
                assertTrue("onPlaybackConfigChanged for PLAYER_UPDATE_MUTED expected",
                        callback.waitForCallbacks(1,
                                TEST_TIMING_TOLERANCE_MS + PLAY_ROUTING_TIMING_TOLERANCE_MS));
            } else {
                Thread.sleep(TEST_TIMING_TOLERANCE_MS + PLAY_ROUTING_TIMING_TOLERANCE_MS);
            }

            checkMutedApi(checkFlag, sessionId);

            // unmute with Runnable
            callback.reset();
            unmute.run();

            if (muteChangesActiveState) {
                assertTrue("onPlaybackConfigChanged for PLAYER_UPDATE_MUTED expected after unmute",
                        callback.waitForCallbacks(1,
                                TEST_TIMING_TOLERANCE_MS + PLAY_ROUTING_TIMING_TOLERANCE_MS));
            } else {
                Thread.sleep(TEST_TIMING_TOLERANCE_MS + PLAY_ROUTING_TIMING_TOLERANCE_MS);
            }
        } finally {
            if (registeredCallback != null) {
                am.unregisterAudioPlaybackCallback(registeredCallback);
            }
            unmute.run();
        }
    }

    private void adjustUnMuteStreamVolume(AudioManager am) {
        try {
            adoptShellPermissionIdentity(MODIFY_AUDIO_SETTINGS_PRIVILEGED);
            am.adjustStreamVolume(TEST_STREAM_FOR_USAGE, ADJUST_UNMUTE, /* flags= */ 0);
        } catch (Exception e) {
            fail("Exception thrown during adjustStreamVolume unmute " + e);
        } finally {
            dropShellPermissionIdentity();
        }
    }

    private void adjustMuteStreamVolume(AudioManager am) {
        try {
            adoptShellPermissionIdentity(MODIFY_AUDIO_SETTINGS_PRIVILEGED);
            am.adjustStreamVolume(TEST_STREAM_FOR_USAGE, ADJUST_MUTE, /* flags= */ 0);
        } catch (Exception e) {
            fail("Exception thrown during adjustStreamVolume mute " + e);
        } finally {
            dropShellPermissionIdentity();
        }
    }

    private void checkMutedApi(int checkFlag, int sessionId) {
        try {
            adoptShellPermissionIdentity(Manifest.permission.MODIFY_AUDIO_ROUTING);

            AudioPlaybackConfiguration currentConfiguration =
                    findConfiguration(checkFlag, sessionId);
            assertTrue("APC should be muted", currentConfiguration.isMuted());
            assertTrue(
                    "APC muted by wrong source",
                    (currentConfiguration.getMutedBy() & checkFlag) != 0);
        } finally {
            dropShellPermissionIdentity();
        }
    }

    private AudioPlaybackConfiguration findConfiguration(int muteHint, int sessionId) {
        AudioManager am = new AudioManager(mContext);
        List<AudioPlaybackConfiguration> configList = am.getActivePlaybackConfigurations();
        AudioPlaybackConfiguration result = null;
        for (AudioPlaybackConfiguration config : configList) {
            if (config.getClientUid() == mUid
                    && config.getClientPid() == mPid
                    && config.getSessionId() == sessionId
                    && config.getAudioDeviceInfo() != null
                    && config.getAudioAttributes().getUsage() == TEST_USAGE
                    && config.getAudioAttributes().getContentType() == TEST_CONTENT) {
                Log.v(
                        TAG,
                        "AudioPlaybackConfiguration "
                                + config
                                + " uid("
                                + config.getClientUid()
                                + "/"
                                + mUid
                                + ") pid("
                                + config.getClientPid()
                                + "/"
                                + mPid
                                + ") sessionId("
                                + config.getSessionId()
                                + "/"
                                + sessionId
                                + ")");
                result = config;
                if ((config.getMutedBy() & muteHint) != 0) {
                    break;
                }
            }
        }
        assertNotNull("Could not find AudioPlaybackConfiguration for uid " + mUid, result);
        return result;
    }

    private void initializeAudioTrack(@NonNull AudioAttributes aa, int sessionId) {
        final int bufferSizeInBytes =
                TEST_AUDIO_TRACK_PLAY_SECONDS * TEST_AUDIO_TRACK_SAMPLERATE
                        * TEST_AUDIO_TRACK_CHANNELS * Short.BYTES;

        ByteBuffer audioData = createSoundDataInShortByteBuffer(
                bufferSizeInBytes / Short.BYTES,
                TEST_AUDIO_TRACK_SAMPLERATE, TEST_AUDIO_TRACK_FREQUENCY,
                TEST_AUDIO_TRACK_SWEEP);

        mAt = new AudioTrack.Builder()
                .setAudioAttributes(aa)
                .setAudioFormat(new AudioFormat.Builder()
                        .setSampleRate(TEST_AUDIO_TRACK_SAMPLERATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build())
                .setBufferSizeInBytes(bufferSizeInBytes)
                .setSessionId(sessionId)
                .build();
        mAt.write(audioData, audioData.remaining(), WRITE_NON_BLOCKING);
    }

    private void initializeMediaPlayer(@NonNull AudioAttributes aa, int sessionId)
            throws Exception {
        AudioManager am = new AudioManager(mContext);
        assertNotNull("Could not create AudioManager", am);

        mMp = createPreparedMediaPlayer(R.raw.sine1khzs40dblong, aa, sessionId);
    }

    @Nullable
    private MediaPlayer createPreparedMediaPlayer(
            @RawRes int resID, @NonNull AudioAttributes aa, int session) throws Exception {
        final TestUtils.Monitor onPreparedCalled = new TestUtils.Monitor();
        final MediaPlayer mp = createPlayer(resID, aa, session);
        mp.setOnPreparedListener(mp1 -> onPreparedCalled.signal());
        mp.prepare();
        onPreparedCalled.waitForSignal(MEDIAPLAYER_PREPARE_TIMEOUT_MS);
        assertTrue(
                "MediaPlayer wasn't prepared in under " + MEDIAPLAYER_PREPARE_TIMEOUT_MS + " ms",
                onPreparedCalled.isSignalled());
        return mp;
    }

    private MediaPlayer createPlayer(@RawRes int resID, @NonNull AudioAttributes aa, int session)
            throws IOException {
        MediaPlayer mp = new MediaPlayer();
        mp.setAudioAttributes(aa);
        mp.setAudioSessionId(session);
        AssetFileDescriptor afd = mContext.getResources().openRawResourceFd(resID);
        try {
            mp.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
        } finally {
            afd.close();
        }
        return mp;
    }

    private SoundPool createSoundPool(int session, @NonNull AudioAttributes aa) {
        SoundPool sp =
                new SoundPool.Builder()
                        .setAudioAttributes(aa)
                        .setMaxStreams(1)
                        .setAudioSessionId(session)
                        .build();
        return sp;
    }

    /** Loads a track and plays it with the passed {@link SoundPool}. */
    private static int playSoundPool(SoundPool sp, Context context) throws InterruptedException {
        final Object loadLock = new Object();
        assertNotNull("SoundPool must not be NULL", sp);
        final SoundPool zepool = sp;
        AtomicBoolean spLoaded = new AtomicBoolean(false);
        // load a sound and play it once load completion is reported
        sp.setOnLoadCompleteListener(
                new SoundPool.OnLoadCompleteListener() {
                    @Override
                    public void onLoadComplete(SoundPool soundPool, int sampleId, int status) {
                        assertEquals("Receiving load completion for wrong SoundPool", zepool, sp);
                        assertEquals("Load completion error", 0 /*success expected*/, status);
                        synchronized (loadLock) {
                            spLoaded.set(true);
                            loadLock.notify();
                        }
                    }
                });
        final int loadId = sp.load(context, R.raw.sine1320hz5sec, 1/*priority*/);
        synchronized (loadLock) {
            while (!spLoaded.get()) {
                loadLock.wait(TEST_TIMEOUT_SOUNDPOOL_LOAD_MS);
            }
        }

        int res = sp.play(loadId, 1.0f /*leftVolume*/, 1.0f /*rightVolume*/, 1 /*priority*/,
                0 /*loop*/, 1.0f/*rate*/);
        // FIXME SoundPool activity is not reported yet, but exercise creation/release with
        //       an AudioPlaybackCallback registered
        assertTrue("Error playing sound through SoundPool", res > 0);
        return res;
    }

    private static void stopSoundPool(SoundPool sp, final int streamId) {
        if (sp != null) {
            sp.stop(streamId);
        }
    }

    @GuardedBy("mMediaPlayerLock")
    private boolean startMediaPlayer(final @NonNull MediaPlayer mp) {
        mp.start();
        return mMediaPlayerLock.waitFor(TEST_TIMING_TOLERANCE_MS, () -> mp.isPlaying());
    }

    @GuardedBy("mMediaPlayerLock")
    private boolean stopMediaPlayer(final MediaPlayer mp) {
        mp.stop();
        return mMediaPlayerLock.waitFor(TEST_TIMING_TOLERANCE_MS, () -> !mp.isPlaying());
    }

    @GuardedBy("mMediaPlayerLock")
    private boolean pauseMediaPlayer(final MediaPlayer mp) {
        mp.pause();
        return mMediaPlayerLock.waitFor(TEST_TIMING_TOLERANCE_MS, () -> !mp.isPlaying());
    }

    /**
     * Start MediaPlayer with AudioPlaybackConfigurations check, after player start successfully: -
     * The number of matched AudioManager.getActivePlaybackConfigurations must at least increase
     * exactly by 1 - If AudioPlaybackCallback registered, number of matched
     * AudioPlaybackConfiguration must increase exactly by 1
     */
    private void startMediaPlayerWithCheck(
            @NonNull AudioManager am,
            @NonNull MediaPlayer mp,
            @NonNull AudioAttributes aa,
            final MyAudioPlaybackCallback callback)
            throws Exception {
        synchronized (mMediaPlayerLock) {
            assertFalse("MediaPlayer already started", mp.isPlaying());

            final Set<AudioPlaybackConfiguration> oldConfigs =
                    new HashSet<AudioPlaybackConfiguration>(am.getActivePlaybackConfigurations());
            Predicate<List<AudioPlaybackConfiguration>> verifyMediaPlayerStarted = l ->
            {
                if (l.size() != 1) {
                    return false;
                }
                AudioPlaybackConfiguration apc = l.get(0);
                if (apc.getAudioAttributes().getTestId() != aa.getTestId()) {
                    return false;
                }
                if (apc.getPlayerState() != AudioPlaybackConfiguration.PLAYER_STATE_STARTED) {
                    return false;
                }
                return true;
            };

            if (callback != null) {
                callback.reset();
                callback.setPredicate(verifyMediaPlayerStarted);
            }

            assertTrue("MediaPlayer start failed", startMediaPlayer(mp));

            if (callback != null) {
                assertTrue(
                        "onPlaybackConfigChanged predicate remained false",
                        callback.waitForPredicate(
                                TEST_TIMING_TOLERANCE_MS + PLAY_ROUTING_TIMING_TOLERANCE_MS));
                final List<AudioPlaybackConfiguration> configs = callback.getMediaPlayerConfigs();
                if (configs.size() != 0) {
                    assertTrue(
                            "Active player, attributes "
                                    + aa
                                    + " not expected in policy "
                                    + configs.get(0).getAudioAttributes().getAllowedCapturePolicy()
                                    + " vs "
                                    + aa.getAllowedCapturePolicy(),
                            hasAttr(configs, aa));
                }
            }
        }
    }

    @GuardedBy("mMediaPlayerLock")
    private void pauseOrStopMediaPlayerCheck(final MyAudioPlaybackCallback callback,
            final AudioAttributes aa)
            throws Exception {

        if (callback != null) {
            assertNotNull("Callback must not be NULL", callback);
            assertTrue(
                    "pauseOrStopMediaPlayerCheck predicate remained false",
                    callback.waitForPredicate(
                            TEST_TIMING_TOLERANCE_MS + PLAY_ROUTING_TIMING_TOLERANCE_MS));
        }
    }

    private void pauseMediaPlayerWithCheck(
            @NonNull AudioManager am,
            @NonNull MediaPlayer mp,
            @NonNull AudioAttributes aa,
            final MyAudioPlaybackCallback callback)
            throws Exception {
        synchronized (mMediaPlayerLock) {
            assertTrue("MediaPlayer should have started", mp.isPlaying());

            // predicate checks that the testId in the AudioAttributes of the player, whose
            // state is to be checked, is either not found, or if found, that it's not in a
            // STARTED state
            Predicate<List<AudioPlaybackConfiguration>> verifyMediaPlayerNotStarted = l ->
            {
                if (l.size() == 0) {
                    return true;
                }
                for (AudioPlaybackConfiguration apc : l) {
                    if ((apc.getAudioAttributes().getTestId() == aa.getTestId())
                            && (apc.getPlayerState()
                                    == AudioPlaybackConfiguration.PLAYER_STATE_STARTED)) {
                        return false;
                    }
                }
                return true;
            };
            if (callback != null) {
                callback.reset();
                callback.setPredicate(verifyMediaPlayerNotStarted);
            }
            final Set<AudioPlaybackConfiguration> oldConfigs =
                    new HashSet<AudioPlaybackConfiguration>(am.getActivePlaybackConfigurations());
            assertTrue("MediaPlayer pause failed", pauseMediaPlayer(mMp));
            pauseOrStopMediaPlayerCheck(callback, aa);
        }
    }

    private void stopMediaPlayerWithCheck(
            @NonNull AudioManager am,
            @NonNull MediaPlayer mp,
            @NonNull AudioAttributes aa,
            final MyAudioPlaybackCallback callback)
            throws Exception {
        synchronized (mMediaPlayerLock) {
            assertTrue("MediaPlayer should have started", mp.isPlaying());

            // predicate checks that the testId in the AudioAttributes of the player, whose
            // state is to be checked, is either not found, or if found, that it's not in a
            // STARTED state
            Predicate<List<AudioPlaybackConfiguration>> verifyMediaPlayerNotStarted =
                    l -> {
                        if (l.size() == 0) {
                            return true;
                        }
                        for (AudioPlaybackConfiguration apc : l) {
                            if ((apc.getAudioAttributes().getTestId() == aa.getTestId())
                                    && (apc.getPlayerState()
                                            == AudioPlaybackConfiguration.PLAYER_STATE_STARTED)) {
                                return false;
                            }
                        }
                        return true;
                    };
            if (callback != null) {
                callback.reset();
                callback.setPredicate(verifyMediaPlayerNotStarted);
            }
            final Set<AudioPlaybackConfiguration> oldConfigs =
                    new HashSet<AudioPlaybackConfiguration>(am.getActivePlaybackConfigurations());
            assertTrue("MediaPlayer stop failed", stopMediaPlayer(mMp));
            pauseOrStopMediaPlayerCheck(callback, aa);
        }
    }

    private class MyAudioPlaybackCallback extends AudioManager.AudioPlaybackCallback {
        private final Object mCbLock = new Object();

        @GuardedBy("mCbLock")
        private int mCalled;

        @GuardedBy("mCbLock")
        private List<AudioPlaybackConfiguration> mConfigs;

        @GuardedBy("mCbLock")
        private int mSessionId;

        @GuardedBy("mCbLock")
        private AudioAttributes mAudioAttributes;

        @GuardedBy("mCbLock")
        private Predicate<List<AudioPlaybackConfiguration>> mPredicate;

        @GuardedBy("mCbLock")
        private boolean mPredicateSatisfied;

        final TestUtils.Monitor mOnCalledMonitor = new TestUtils.Monitor();

        void reset() {
            synchronized (mCbLock) {
                mCalled = 0;
                mConfigs = new ArrayList<AudioPlaybackConfiguration>();
                mPredicate = null;
                mPredicateSatisfied = false;
                Log.i(TAG, "MyAudioPlaybackCallback reset");
            }
            mOnCalledMonitor.reset();
        }

        int getCbInvocationNumber() {
            synchronized (mCbLock) {
                return mCalled;
            }
        }

        List<AudioPlaybackConfiguration> getMediaPlayerConfigs() {
            synchronized (mCbLock) {
                return mConfigs;
            }
        }

        MyAudioPlaybackCallback(int session, @NonNull AudioAttributes attributes) {
            mAudioAttributes = attributes;
            mSessionId = session;
            reset();
        }

        @GuardedBy("mCbLock")
        private List<AudioPlaybackConfiguration> filterConfigsWithCurrentPlayer(
                final List<AudioPlaybackConfiguration> configs) {
            List<AudioPlaybackConfiguration> result = new ArrayList<AudioPlaybackConfiguration>();
            for (final AudioPlaybackConfiguration config : configs) {
                try {
                    if (isPlayerConfigMatches(config, mSessionId, mAudioAttributes)) {
                        result.add(config);
                    }
                } catch (Exception e) {
                    fail("Exception thrown during reflection on config privileged fields" + e);
                }
            }
            return result;
        }

        @Override
        public void onPlaybackConfigChanged(List<AudioPlaybackConfiguration> configs) {
            synchronized (mCbLock) {
                mConfigs = filterConfigsWithCurrentPlayer(configs);
                Log.i(
                        TAG,
                        "onPlaybackConfigChanged sessionId:" + mSessionId + " cfgs: " + mConfigs);
                // add counter and signal when there is a matching AudioPlaybackConfiguration
                if (!mIsPrivileged || mConfigs.size() != 0) {
                    mCalled++;
                    mOnCalledMonitor.signal();
                }
                Log.i(TAG, "onPlaybackConfigChanged predicate " + mPredicate);
                if (!mPredicateSatisfied && mPredicate != null && mPredicate.test(mConfigs)) {
                    Log.i(TAG, "onPlaybackConfigChanged predicateSatisfied");
                    mPredicateSatisfied = true;
                    mCbLock.notifyAll();
                }
            }
        }

        boolean waitForCallbacks(int calledCount, long timeoutMs)
                throws InterruptedException {
            int signalsCounted = mOnCalledMonitor.waitForCountedSignals(calledCount, timeoutMs);
            return (signalsCounted >= calledCount);
        }

        void setPredicate(Predicate<List<AudioPlaybackConfiguration>> predicate) {
            synchronized (mCbLock) {
                mPredicate = predicate;
                if (!mPredicateSatisfied && mPredicate != null && mPredicate.test(mConfigs)) {
                    mPredicateSatisfied = true;
                    mCbLock.notifyAll();
                }
            }
        }

        boolean waitForPredicate(long timeoutMs) throws InterruptedException {
            synchronized (mCbLock) {
                if (mPredicateSatisfied) {
                    return true;
                }
                if (timeoutMs == 0) {
                    return false;
                }
                long deadline = System.currentTimeMillis() + timeoutMs;
                while (!mPredicateSatisfied) {
                    long waitTime = deadline - System.currentTimeMillis();
                    if (waitTime <= 0) {
                        break;
                    }
                    mCbLock.wait(waitTime);
                }
                return mPredicateSatisfied;
            }
        }
    }

    private static class MyPlayer {
        final Runnable mPlay;
        final Consumer<Float> mSetClientVolume;
        final Function<VolumeShaper.Configuration, VolumeShaper> mCreateVolumeShaper;
        final int mSessionId;

        MyPlayer(AudioTrack at) {
            mPlay = at::play;
            mSetClientVolume = at::setVolume;
            mCreateVolumeShaper = at::createVolumeShaper;
            mSessionId = at.getAudioSessionId();
        }

        MyPlayer(MediaPlayer mp) {
            mPlay = mp::start;
            mSetClientVolume = mp::setVolume;
            mCreateVolumeShaper = mp::createVolumeShaper;
            mSessionId = mp.getAudioSessionId();
        }
    }

    private static boolean hasAttr(List<AudioPlaybackConfiguration> configs, AudioAttributes aa) {
        for (AudioPlaybackConfiguration apc : configs) {
            if (apc.getAudioAttributes().getContentType() == aa.getContentType()
                    && apc.getAudioAttributes().getUsage() == aa.getUsage()
                    && apc.getAudioAttributes().getFlags() == aa.getFlags()
                    && anonymizeCapturePolicy(apc.getAudioAttributes().getAllowedCapturePolicy())
                            == anonymizeCapturePolicy(aa.getAllowedCapturePolicy())) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasDevice(List<AudioPlaybackConfiguration> configs, AudioAttributes aa,
                                     boolean enableRoutedDeviceIdsFlag) {
        for (AudioPlaybackConfiguration apc : configs) {
            if (apc.getAudioAttributes().getContentType() == aa.getContentType()
                    && apc.getAudioAttributes().getUsage() == aa.getUsage()
                    && apc.getAudioAttributes().getFlags() == aa.getFlags()
                    && anonymizeCapturePolicy(apc.getAudioAttributes().getAllowedCapturePolicy())
                            == aa.getAllowedCapturePolicy()
                    && apc.getAudioDeviceInfo() != null
                    && (!enableRoutedDeviceIdsFlag || apc.getAudioDeviceInfos().size() > 0)) {
                return true;
            }
        }
        return false;
    }

    /** ALLOW_CAPTURE_BY_SYSTEM is anonymized to ALLOW_CAPTURE_BY_NONE. */
    @CapturePolicy
    private static int anonymizeCapturePolicy(@CapturePolicy int policy) {
        if (policy == ALLOW_CAPTURE_BY_SYSTEM) {
            return ALLOW_CAPTURE_BY_NONE;
        }
        return policy;
    }

    private boolean isValidPlatform(String testName) {
        if (!mContext.getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_AUDIO_OUTPUT)) {
            Log.w(TAG,"AUDIO_OUTPUT feature not found. This system might not have a valid "
                    + "audio output HAL, skipping test " + testName);
            return false;
        }
        return true;
    }

    private boolean isWatch() {
        return mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH);
    }

    private boolean isAutomotive() {
        return mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE);
    }

    private void adoptShellPermissionIdentity(String permission) {
        getInstrumentation().getUiAutomation().adoptShellPermissionIdentity(permission);
        mIsPrivileged = true;
    }

    private void dropShellPermissionIdentity() {
        if (mIsPrivileged) {
            getInstrumentation().getUiAutomation().dropShellPermissionIdentity();
            mIsPrivileged = false;
        }
    }

    /**
     * For privileged player, compare sessionId. For non-privileged player, compare usage,
     * contentType, and flags in AudioAttributes.
     */
    private boolean isPlayerConfigMatches(
            final AudioPlaybackConfiguration config,
            int sessionId,
            @NonNull final AudioAttributes oldAa) {
        if (mIsPrivileged) {
            return config.getSessionId() == sessionId;
        } else {
            final AudioAttributes newAa = config.getAudioAttributes();
            return newAa.getTestId() == sessionId;
        }
    }

    private List<AudioPlaybackConfiguration> getAddedPlayerConfigs(
            final Set<AudioPlaybackConfiguration> oldConfigs,
            final List<AudioPlaybackConfiguration> newConfigs,
            int sessionId,
            @NonNull final AudioAttributes oldAa) {
        List<AudioPlaybackConfiguration> addedConfigs = new ArrayList<AudioPlaybackConfiguration>();

        for (final AudioPlaybackConfiguration config : newConfigs) {
            if (!oldConfigs.contains(config) && isPlayerConfigMatches(config, sessionId, oldAa)) {
                addedConfigs.add(config);
            }
        }

        return addedConfigs;
    }

    private boolean isRingerModeSilent(AudioManager am) {
        if (am == null) {
            am = new AudioManager(mContext);
        }
        assertNotNull("Could not create AudioManager", am);

        return am.getRingerMode() == AudioManager.RINGER_MODE_SILENT;
    }

    private AudioAttributes createTestAudioAttributes(long testId) {
        return new AudioAttributes.Builder()
                .setUsage(TEST_USAGE)
                .setContentType(TEST_CONTENT)
                .setFlags(TEST_ATTRIBUTE_FLAGS)
                .setTestId(testId)
                .build();
    }
}
