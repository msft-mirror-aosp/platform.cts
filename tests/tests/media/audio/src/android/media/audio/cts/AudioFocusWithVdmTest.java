/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static android.Manifest.permission.MODIFY_AUDIO_ROUTING;
import static android.Manifest.permission.MODIFY_AUDIO_SETTINGS_PRIVILEGED;
import static android.Manifest.permission.QUERY_AUDIO_STATE;
import static android.companion.virtual.VirtualDeviceParams.DEVICE_POLICY_CUSTOM;
import static android.companion.virtual.VirtualDeviceParams.POLICY_TYPE_AUDIO;
import static android.media.AudioManager.AUDIOFOCUS_GAIN;
import static android.media.AudioManager.AUDIOFOCUS_LOSS;
import static android.media.AudioManager.AUDIOFOCUS_REQUEST_FAILED;
import static android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.Uninterruptibles.tryAcquireUninterruptibly;

import static org.junit.Assume.assumeNotNull;

import android.annotation.SuppressLint;
import android.companion.virtual.VirtualDeviceManager.VirtualDevice;
import android.companion.virtual.VirtualDeviceParams;
import android.companion.virtualdevice.flags.Flags;
import android.content.Context;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.virtualdevice.cts.common.VirtualDeviceRule;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.internal.annotations.GuardedBy;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@SuppressLint("MissingCheckFlagsRule") // TODO: b/463342925 - remove once fixed
@RunWith(JUnitParamsRunner.class)
@AppModeFull(reason = "Virtual device manager cannot be accessed by instant apps")
public class AudioFocusWithVdmTest {

    private static final VirtualDeviceParams VIRTUAL_DEVICE_PARAMS_CUSTOM_POLICY =
            new VirtualDeviceParams.Builder()
                    .setDevicePolicy(POLICY_TYPE_AUDIO, DEVICE_POLICY_CUSTOM)
                    .build();

    @Rule
    public VirtualDeviceRule mVirtualDeviceRule = VirtualDeviceRule.withAdditionalPermissions(
            MODIFY_AUDIO_SETTINGS_PRIVILEGED, // ensures focus request is independent of proc state
            QUERY_AUDIO_STATE);

    private boolean mMultiFocusEnabled;

    @Before
    public void setUp() throws Exception {

        // Since the mVirtualDeviceManager is configured with default device policy
        // expect focus lost if multi focus is not enabled
        AudioManager audioManager =
                InstrumentationRegistry.getInstrumentation()
                        .getTargetContext()
                        .getSystemService(AudioManager.class);
        assumeNotNull(audioManager);
        mMultiFocusEnabled = audioManager.isMultiAudioFocusEnabled();
    }

    /**
     * The test below tests the following scenario:
     *
     * 1. There's media playback going on on non-VDM context.
     * 2. Audio focus is requested within VDM context, where the virtual device associated
     *    with the context has custom device policy for audio.
     *
     * It is expected that the native player doesn't loose the focus and at the same time,
     * focus requested for VDM context will be granted.
     */
    @Test
    public void testAudioFocusRequestOnVdmContextWithCustomDevicePolicy() {
        final Context defaultContext = getApplicationContext();
        final VirtualDevice vd =
                mVirtualDeviceRule.createManagedVirtualDevice(VIRTUAL_DEVICE_PARAMS_CUSTOM_POLICY);
        try (PlaybackHelperForTest defaultDevicePlayback =
                        new PlaybackHelperForTest(defaultContext);
                PlaybackHelperForTest vdmDevicePlayback =
                        new PlaybackHelperForTest(vd.createContext())) {
            // Audio playing within default context starts first, focus should be granted.
            int defaultFocusRequestResult = defaultDevicePlayback.requestFocus();
            assertThat(defaultFocusRequestResult).isEqualTo(AUDIOFOCUS_REQUEST_GRANTED);
            defaultDevicePlayback.startPlayback();

            int vdmFocusRequestResult = vdmDevicePlayback.requestFocus();
            assertThat(vdmFocusRequestResult).isEqualTo(AUDIOFOCUS_REQUEST_GRANTED);
            // None of the players should lose focus.
            assertThat(defaultDevicePlayback.getLastFocusChange().isEmpty()).isTrue();
            assertThat(vdmDevicePlayback.getLastFocusChange().isEmpty()).isTrue();
        }
    }

    /**
     * The test below tests the following scenario:
     *
     * <p>1. There's media playback going on on non-VDM context.
     *
     * <p>2. Audio focus is requested within VDM context, where the virtual device associated with
     * the context has default device policy for audio.
     *
     * <p>It is expected that the first player will loose audio focus if the multi audio focus is
     * not enabled.
     */
    @Test
    public void testAudioFocusRequestOnVdmContextWithDefaultDevicePolicy() {
        final Context defaultContext = getApplicationContext();
        final VirtualDevice vd = mVirtualDeviceRule.createManagedVirtualDevice();
        try (PlaybackHelperForTest defaultDevicePlayback =
                        new PlaybackHelperForTest(defaultContext);
                PlaybackHelperForTest vdmDevicePlayback =
                        new PlaybackHelperForTest(vd.createContext())) {

            // Audio playing within default context starts first, focus should be granted.
            int defaultFocusRequestResult = defaultDevicePlayback.requestFocus();
            assertThat(defaultFocusRequestResult).isEqualTo(AUDIOFOCUS_REQUEST_GRANTED);
            defaultDevicePlayback.startPlayback();

            int vdmFocusRequestResult = vdmDevicePlayback.requestFocus();
            assertThat(vdmFocusRequestResult).isEqualTo(AUDIOFOCUS_REQUEST_GRANTED);

            // Since the mVirtualDeviceManager is configured with default device policy
            // expect focus lost if multi focus is not enabled
            AudioManager audioManager = defaultContext.getSystemService(AudioManager.class);
            assumeNotNull(audioManager);
            boolean expectedFocusLost = !audioManager.isMultiAudioFocusEnabled();

            if (expectedFocusLost) {
                assertThat(defaultDevicePlayback.getLastFocusChange().isPresent()).isTrue();
                assertThat(defaultDevicePlayback.getLastFocusChange().get())
                        .isEqualTo(AUDIOFOCUS_LOSS);
            }
            assertThat(vdmDevicePlayback.getLastFocusChange().isEmpty()).isTrue();
        }
    }

    // This test behavior consistency is independent of the FLAG_AUDIO_FOCUS_ENVIRONMENTS value
    @Parameters({"false", "true"})
    @Test
    public void testAudioFocusRequestsOnVdmContextAndPermission(boolean hasPermission) {
        // Test a VirtualDevice with and without the MODIFY_AUDIO_ROUTING permission
        verifyAudioFocusRequestsOnVirtualDeviceAndCustomAudioPolicy(hasPermission);
    }

    @RequiresFlagsEnabled(Flags.FLAG_AUDIO_FOCUS_ENVIRONMENTS)
    @Parameters({"false", "true"})
    @Test
    public void testAudioFocusRequestsWithMultiplePlayersAndPermission(boolean hasPermission) {
        // Test multiple players on default and VirtualDevice with and without
        // the MODIFY_AUDIO_ROUTING permission
        verifyAudioFocusRequestsWithMultiplePlayers(hasPermission);
    }

    /**
     * Test cleanup of audio focus requests when the associated VirtualDevice/context is closed,
     * confirming environment destruction.
     */
    @RequiresFlagsEnabled(Flags.FLAG_AUDIO_FOCUS_ENVIRONMENTS)
    @Parameters({"false", "true"})
    @Test
    public void testAudioFocusEnvironmentCleanupOnVdmCloseAndPermission(boolean hasPermission) {
        // Test audio focus behavior on multiple virtual devices when a virtual device is closed
        // with and without the MODIFY_AUDIO_ROUTING permission
        verifyAudioFocusEnvironmentCleanupOnVdmClose(hasPermission);
    }

    private VirtualDevice createVirtualDeviceWithModifyAudioRoutingPermission(
            boolean hasPermission) {
        return mVirtualDeviceRule.runWithAdditionalTemporaryPermissions(
                () ->
                        mVirtualDeviceRule.createManagedVirtualDevice(
                                VIRTUAL_DEVICE_PARAMS_CUSTOM_POLICY),
                hasPermission ? new String[] {MODIFY_AUDIO_ROUTING} : new String[0]);
    }

    private void verifyAudioFocusRequestsWithMultiplePlayers(boolean hasPermission) {
        final VirtualDevice vd = createVirtualDeviceWithModifyAudioRoutingPermission(hasPermission);

        Context defaultContext = getApplicationContext();
        Context vdmContext = vd.createContext();

        try (PlaybackHelperForTest defaultDevicePlayback1 =
                        new PlaybackHelperForTest(defaultContext);
                PlaybackHelperForTest defaultDevicePlayback2 =
                        new PlaybackHelperForTest(defaultContext);
                PlaybackHelperForTest vdmDevicePlayback1 = new PlaybackHelperForTest(vdmContext);
                PlaybackHelperForTest vdmDevicePlayback2 =
                        new PlaybackHelperForTest(vdmContext); ) {
            // First audio playing on default context, focus should be granted.
            int defaultFocusRequestResult1 = defaultDevicePlayback1.requestFocus();
            assertThat(defaultFocusRequestResult1).isEqualTo(AUDIOFOCUS_REQUEST_GRANTED);
            defaultDevicePlayback1.startPlayback();

            // Second audio playing on default context, focus should be granted and first player
            // should lose focus.
            int defaultFocusRequestResult2 = defaultDevicePlayback2.requestFocus();
            assertThat(defaultFocusRequestResult2).isEqualTo(AUDIOFOCUS_REQUEST_GRANTED);

            // Expect focus lost if multi focus is not enabled
            if (!mMultiFocusEnabled) {
                assertThat(defaultDevicePlayback1.getLastFocusChange().isPresent()).isTrue();
                assertThat(defaultDevicePlayback1.getLastFocusChange().get())
                        .isEqualTo(AUDIOFOCUS_LOSS);
            }

            defaultDevicePlayback2.startPlayback();
            defaultDevicePlayback1.clearLastFocusChange();

            // First audio playing on VDM context, focus should be granted. Default context player
            // should not be affected.
            int vdmFocusRequestResult1 = vdmDevicePlayback1.requestFocus();
            assertThat(vdmFocusRequestResult1).isEqualTo(AUDIOFOCUS_REQUEST_GRANTED);
            assertThat(defaultDevicePlayback1.getLastFocusChange().isEmpty()).isTrue();
            assertThat(defaultDevicePlayback2.getLastFocusChange().isEmpty()).isTrue();
            vdmDevicePlayback1.startPlayback();

            // Second audio playing on VDM context, focus should be granted. Default context player
            // should not be affected.
            int vdmFocusRequestResult2 = vdmDevicePlayback2.requestFocus();
            assertThat(vdmFocusRequestResult2).isEqualTo(AUDIOFOCUS_REQUEST_GRANTED);

            // with separate audio environments created with the MODIFY_AUDIO_ROUTING permission,
            // the second VDM context playback also notifies the first VDM player of focus lost
            // if (desktop) multi focus is not enabled
            if (hasPermission && !mMultiFocusEnabled) {
                assertThat(vdmDevicePlayback1.getLastFocusChange().isPresent()).isTrue();
                assertThat(vdmDevicePlayback1.getLastFocusChange().get())
                        .isEqualTo(AUDIOFOCUS_LOSS);
            } else {
                assertThat(vdmDevicePlayback1.getLastFocusChange().isEmpty()).isTrue();
            }
            assertThat(defaultDevicePlayback1.getLastFocusChange().isEmpty()).isTrue();
            assertThat(defaultDevicePlayback2.getLastFocusChange().isEmpty()).isTrue();
        }

        vd.close();
    }

    private void verifyAudioFocusRequestsOnVirtualDeviceAndCustomAudioPolicy(
            boolean hasPermission) {
        final VirtualDevice vd = createVirtualDeviceWithModifyAudioRoutingPermission(hasPermission);

        Context defaultContext = getApplicationContext();
        Context vdmContext = vd.createContext();

        try (PlaybackHelperForTest defaultDevicePlayback =
                        new PlaybackHelperForTest(defaultContext);
                PlaybackHelperForTest vdmDevicePlayback = new PlaybackHelperForTest(vdmContext); ) {
            // Audio focus request on default context, focus should be granted.
            assertThat(defaultDevicePlayback.requestFocus()).isEqualTo(AUDIOFOCUS_REQUEST_GRANTED);
            defaultDevicePlayback.startPlayback();

            // VDM context player requests focus which is granted.
            // Default context player is not affected.
            assertThat(vdmDevicePlayback.requestFocus()).isEqualTo(AUDIOFOCUS_REQUEST_GRANTED);
            assertThat(defaultDevicePlayback.getLastFocusChange().isEmpty()).isTrue();
            vdmDevicePlayback.startPlayback();

            // Default context player abandons focus. Vdm player is not affected.
            assertThat(defaultDevicePlayback.abandonFocus()).isEqualTo(AUDIOFOCUS_REQUEST_GRANTED);
            assertThat(vdmDevicePlayback.getLastFocusChange().isEmpty()).isTrue();

            // Default context player requests focus. Vdm player is not affected.
            assertThat(defaultDevicePlayback.requestFocus()).isEqualTo(AUDIOFOCUS_REQUEST_GRANTED);
            assertThat(vdmDevicePlayback.getLastFocusChange().isEmpty()).isTrue();

            // VDM context player abandons focus.
            // Default context player is not affected.
            assertThat(vdmDevicePlayback.abandonFocus()).isEqualTo(AUDIOFOCUS_REQUEST_GRANTED);
            assertThat(defaultDevicePlayback.getLastFocusChange().isEmpty()).isTrue();
        }

        vd.close();
    }

    private void verifyAudioFocusEnvironmentCleanupOnVdmClose(boolean hasPermission) {
        Context defaultContext = getApplicationContext();
        PlaybackHelperForTest defaultDevicePlayback = new PlaybackHelperForTest(defaultContext);

        int defaultFocusRequestResult = defaultDevicePlayback.requestFocus();
        assertThat(defaultFocusRequestResult).isEqualTo(AUDIOFOCUS_REQUEST_GRANTED);
        defaultDevicePlayback.startPlayback();

        // Create a virtual device with custom audio policy for audio focus isolated environment.
        final VirtualDevice vd = createVirtualDeviceWithModifyAudioRoutingPermission(hasPermission);
        PlaybackHelperForTest vdmPlayback = new PlaybackHelperForTest(vd.createContext());

        // Request focus in the virtual device
        int vdmFocusRequestResult = vdmPlayback.requestFocus();
        assertThat(vdmFocusRequestResult).isEqualTo(AUDIOFOCUS_REQUEST_GRANTED);
        vdmPlayback.startPlayback();

        // The playback on the default context retains focus due to isolation
        assertThat(defaultDevicePlayback.getLastFocusChange().isEmpty()).isTrue();

        // Create a second virtual device. This creates a second isolated audio focus environment
        // if it has the permission.
        VirtualDevice vd2 = createVirtualDeviceWithModifyAudioRoutingPermission(hasPermission);
        PlaybackHelperForTest vdmPlayback2 = new PlaybackHelperForTest(vd2.createContext());

        // Playback in the second virtual device acquires focus.
        int vdmFocusRequestResult2 = vdmPlayback2.requestFocus();
        assertThat(vdmFocusRequestResult2).isEqualTo(AUDIOFOCUS_REQUEST_GRANTED);
        vdmPlayback2.startPlayback();

        vd.close();

        // Closing the first virtual device will trigger the cleanup path for the first audio
        // focus environment and lose audio focus for the first virtual playback if it was created,
        // which only happens when having the permission and is not already (desktop) multi focus
        // enabled
        if (hasPermission && !mMultiFocusEnabled) {
            assertThat(vdmPlayback.getLastFocusChange().isPresent()).isTrue();
            assertThat(vdmPlayback.getLastFocusChange().get()).isEqualTo(AUDIOFOCUS_LOSS);
        }

        // Default and second virtual audio focus environments are not affected
        assertThat(defaultDevicePlayback.getLastFocusChange().isEmpty()).isTrue();
        assertThat(vdmPlayback2.getLastFocusChange().isEmpty()).isTrue();

        vd2.close();

        defaultDevicePlayback.close();
        vdmPlayback.close();
        vdmPlayback2.close();
    }

    /**
     * Helper class to manage playback client for test.
     */
    private static class PlaybackHelperForTest implements AutoCloseable {
        private final Context mContext;
        private MediaPlayer mMediaPlayer;
        private AudioFocusListenerForTest mFocusListener;
        private final AudioManager mAudioManager;
        private AudioFocusRequest mAudioFocusRequest;

        PlaybackHelperForTest(Context context) {
            mContext = context;
            mAudioManager = context.getSystemService(AudioManager.class);
        }

        int requestFocus() {
            if (mAudioFocusRequest != null) {
                // Abandon previous focus request.
                mAudioManager.abandonAudioFocusRequest(mAudioFocusRequest);
            }
            mFocusListener = new AudioFocusListenerForTest();
            mAudioFocusRequest = new AudioFocusRequest.Builder(AUDIOFOCUS_GAIN)
                    .setOnAudioFocusChangeListener(mFocusListener).build();
            return mAudioManager.requestAudioFocus(mAudioFocusRequest);
        }

        int abandonFocus() {
            if (mAudioFocusRequest == null) {
                // no previous request to abandon
                return AUDIOFOCUS_REQUEST_FAILED;
            }
            mFocusListener = new AudioFocusListenerForTest();
            // Abandon previous focus request.
            return mAudioManager.abandonAudioFocusRequest(mAudioFocusRequest);
        }

        void clearLastFocusChange() {
            if (mFocusListener != null) {
                mFocusListener.clearLastFocusChange();
            }
        }

        Optional<Integer> getLastFocusChange() {
            return mFocusListener == null ? Optional.empty() : mFocusListener.getLastFocusChange();
        }

        void startPlayback() {
            mMediaPlayer = MediaPlayer.create(mContext, R.raw.sine1khzs40dblong);
            mMediaPlayer.start();
        }

        @Override
        public void close() {
            if (mMediaPlayer != null) {
                mMediaPlayer.stop();
                mMediaPlayer.release();
            }
            if (mAudioManager != null && mAudioFocusRequest != null) {
                mAudioManager.abandonAudioFocusRequest(mAudioFocusRequest);
            }
        }
    }

    private static class AudioFocusListenerForTest implements
            AudioManager.OnAudioFocusChangeListener {
        private static final long AUDIO_FOCUS_CHANGE_WAIT_TIMEOUT_MS = 500;
        private final Object mLock = new Object();
        private final Semaphore mChangeEventSignal = new Semaphore(0);
        @GuardedBy("mLock")
        private Optional<Integer> mLastFocusChange = Optional.empty();

        Optional<Integer> getLastFocusChange() {
            Optional<Integer> lastChange;
            synchronized (mLock) {
                lastChange = mLastFocusChange;
            }
            if (lastChange.isEmpty()) {
                boolean unused =
                        tryAcquireUninterruptibly(
                                mChangeEventSignal,
                                AUDIO_FOCUS_CHANGE_WAIT_TIMEOUT_MS,
                                TimeUnit.MILLISECONDS);
            }

            synchronized (mLock) {
                return mLastFocusChange;
            }
        }

        void clearLastFocusChange() {
            synchronized (mLock) {
                mLastFocusChange = Optional.empty();
            }
        }

        @Override
        public void onAudioFocusChange(int focusChange) {
            synchronized (mLock) {
                mLastFocusChange = Optional.of(focusChange);
            }
            mChangeEventSignal.release();
        }
    }
}
