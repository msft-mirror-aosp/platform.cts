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

package android.car.cts;

import static android.Manifest.permission.MODIFY_AUDIO_SETTINGS_PRIVILEGED;
import static android.car.Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS;
import static android.car.cts.utils.CarAudioUtils.usageToString;
import static android.car.media.CarAudioManager.AUDIO_FEATURE_FOCUS_ENFORCEMENT;
import static android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION;
import static android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.car.feature.Flags;
import android.car.media.CarAudioManager;
import android.car.media.EnforceableAudioFocusCallback;
import android.car.media.EnforcedAudioFocusInfo;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Process;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.permissions.PermissionContextImpl;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@RunWith(Parameterized.class)
@AppModeFull(reason = "Instant Apps cannot get car related permissions")
public final class CarAudioFocusEnforcementTest extends AbstractCarTestCase {

    private static final String TAG = CarAudioFocusEnforcementTest.class.getSimpleName();

    private static final long TEST_PLAYER_TIMEOUT_MS = 500;
    private static final long TEST_CALLBACK_TIMEOUT_MS = 100;

    private final int mAudioUsageToEnforce;
    private TestEnforceableAudioFocusCallback mEnforceableAudioFocusCallback;
    private AudioFocusRequest mFocusRequest;
    private MediaPlayer mMediaPlayer;
    private boolean mShouldBeSilenced;
    private AudioManager mAudioManager;
    private CarAudioManager mCarAudioManager;
    private PermissionContextImpl mPermissionContext;

    /**
     * Creates the test with the given audio usage.
     *
     * @param audioUsageToEnforce audio usage to test focus enforcement on
     */
    public CarAudioFocusEnforcementTest(int audioUsageToEnforce) {
        mAudioUsageToEnforce = audioUsageToEnforce;
    }

    /**
     * Provides the audio usages to test.
     *
     * @return the audio usages to test
     */
    @Parameters
    public static Collection<Object> data() {
        return List.of(
                AudioAttributes.USAGE_UNKNOWN,
                AudioAttributes.USAGE_MEDIA,
                AudioAttributes.USAGE_ALARM,
                AudioAttributes.USAGE_NOTIFICATION,
                AudioAttributes.USAGE_NOTIFICATION_RINGTONE,
                AudioAttributes.USAGE_NOTIFICATION_EVENT,
                AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY,
                AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE,
                AudioAttributes.USAGE_ASSISTANCE_SONIFICATION,
                AudioAttributes.USAGE_GAME,
                AudioAttributes.USAGE_ASSISTANT);
    }

    @Before
    public void setUp() throws Exception {
        mPermissionContext =
                TestApis.permissions()
                        .withPermission(
                                MODIFY_AUDIO_SETTINGS_PRIVILEGED,
                                PERMISSION_CAR_CONTROL_AUDIO_SETTINGS);
        mAudioManager = mContext.getSystemService(AudioManager.class);
        mCarAudioManager = getCar().getCarManager(CarAudioManager.class);
        assertWithMessage("CarAudioManager instance").that(mCarAudioManager).isNotNull();
        assumeTrue(
                "Audio focus enforcement is not supported",
                mCarAudioManager.isAudioFeatureEnabled(AUDIO_FEATURE_FOCUS_ENFORCEMENT));
        mShouldBeSilenced =
                isUsageSupported(
                        mCarAudioManager.getEnforceableAudioAttributeUsages(),
                        mAudioUsageToEnforce);
        mEnforceableAudioFocusCallback = new TestEnforceableAudioFocusCallback();
        Executor executor = Executors.newSingleThreadExecutor();
        createAndStartMediaPlayerWithUsage(mAudioUsageToEnforce);
        mCarAudioManager.setEnforceableAudioFocusCallback(executor, mEnforceableAudioFocusCallback);
    }

    @After
    public void cleanUp() throws Exception {
        if (mShouldBeSilenced && mEnforceableAudioFocusCallback != null) {
            mEnforceableAudioFocusCallback.reset();
        }
        if (mFocusRequest != null) {
            mAudioManager.abandonAudioFocusRequest(mFocusRequest);
        }
        if (mShouldBeSilenced && mEnforceableAudioFocusCallback != null) {
            // Wait for a short period to make sure unsilencing is called
            mEnforceableAudioFocusCallback.waitForCallback(/* count= */ 4);
        }
        if (mMediaPlayer != null) {
            mMediaPlayer.stop();
            mMediaPlayer.release();
        }
        mCarAudioManager.clearEnforceableAudioFocusCallback();
        mCarAudioManager.setEnforceableAudioFocusEnabled(/* enabled= */ false);
        mPermissionContext.close();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_AUDIO_FOCUS_ENFORCEMENT)
    public void waitForCallback_withManageableUsageAndCriticalAudioUsageFocus() throws Exception {
        assumeTrue(
                "Audio focus enforcement is not supported for usage " + getUsageString(),
                mShouldBeSilenced);
        createAudioFocusRequestAndRequestFocus();

        boolean called = mEnforceableAudioFocusCallback.waitForCallback(/* count= */ 10);

        assertWithMessage(
                        "Enforced audio focus callback for critical usage and usage "
                                + getUsageString())
                .that(called)
                .isTrue();
        List<EnforcedAudioFocusInfo> infos = mEnforceableAudioFocusCallback.getEnforcedInfos();
        assertWithMessage(
                        "Enforced audio focus info count for active critical audio and usage %s",
                        getUsageString())
                .that(infos)
                .hasSize(1);
        EnforcedAudioFocusInfo info = infos.getFirst();
        assertWithMessage(
                        "Enforced audio focus info UID for active critical audio and usage %s",
                        getUsageString())
                .that(info.getUid())
                .isEqualTo(Process.myUid());
        assertWithMessage(
                        "Enforced audio focus info usage for active critical audio and usage %s",
                        getUsageString())
                .that(info.getAudioAttributes().getUsage())
                .isEqualTo(mAudioUsageToEnforce);
        assertWithMessage(
                        "Enforced audio focus info silenced status for usage %s and active"
                                + " critical audio",
                        getUsageString())
                .that(info.isSilenced())
                .isEqualTo(mShouldBeSilenced);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_AUDIO_FOCUS_ENFORCEMENT)
    public void waitForCallback_withManageableUsageAndForcedEnabled() throws Exception {
        assumeTrue(
                "Audio focus enforcement is not supported for usage " + getUsageString(),
                mShouldBeSilenced);
        mCarAudioManager.setEnforceableAudioFocusEnabled(/* enabled= */ true);

        boolean called = mEnforceableAudioFocusCallback.waitForCallback(/* count= */ 10);

        assertWithMessage("Enforced audio focus callback for managed usage " + getUsageString())
                .that(called)
                .isTrue();
        List<EnforcedAudioFocusInfo> infos = mEnforceableAudioFocusCallback.getEnforcedInfos();
        assertWithMessage("Enforced audio focus info count for usage %s", getUsageString())
                .that(infos)
                .hasSize(1);
        EnforcedAudioFocusInfo info = infos.getFirst();
        assertWithMessage("Enforced audio focus info UID for usage %s", getUsageString())
                .that(info.getUid())
                .isEqualTo(Process.myUid());
        assertWithMessage("Enforced audio focus info for usage %s", getUsageString())
                .that(info.getAudioAttributes().getUsage())
                .isEqualTo(mAudioUsageToEnforce);
        assertWithMessage(
                        "Enforced audio focus info silenced status for usage %s", getUsageString())
                .that(info.isSilenced())
                .isEqualTo(mShouldBeSilenced);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_AUDIO_FOCUS_ENFORCEMENT)
    public void waitForCallback_withUnmanageableUsageAndForcedEnabled() throws Exception {
        assumeFalse(
                "Audio focus enforcement is supported for usage " + getUsageString(),
                mShouldBeSilenced);
        mCarAudioManager.setEnforceableAudioFocusEnabled(/* enabled= */ true);

        boolean called = mEnforceableAudioFocusCallback.waitForCallback(/* count= */ 2);

        assertWithMessage("Enforced audio focus callback for unmanaged usage " + getUsageString())
                .that(called)
                .isFalse();
        List<EnforcedAudioFocusInfo> infos = mEnforceableAudioFocusCallback.getEnforcedInfos();
        assertWithMessage("Enforced audio focus info count for usage %s", getUsageString())
                .that(infos)
                .isEmpty();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_AUDIO_FOCUS_ENFORCEMENT)
    public void waitForCallback_withUnmanageableUsageAndCriticalAudioUsageFocus() throws Exception {
        assumeFalse(
                "Audio focus enforcement is supported for usage " + getUsageString(),
                mShouldBeSilenced);
        createAudioFocusRequestAndRequestFocus();

        boolean called = mEnforceableAudioFocusCallback.waitForCallback(/* count= */ 2);

        assertWithMessage(
                        "Enforced audio focus callback for critical usage and unmanaged usage "
                                + getUsageString())
                .that(called)
                .isFalse();
        List<EnforcedAudioFocusInfo> infos = mEnforceableAudioFocusCallback.getEnforcedInfos();
        assertWithMessage(
                        "Enforced audio focus info count for active critical audio and usage %s",
                        getUsageString())
                .that(infos)
                .isEmpty();
    }

    private void createAudioFocusRequestAndRequestFocus() throws Exception {
        TestAudioFocusListener focusListener = new TestAudioFocusListener();
        mFocusRequest =
                new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setOnAudioFocusChangeListener(focusListener)
                        .setAudioAttributes(
                                new AudioAttributes.Builder()
                                        .setUsage(USAGE_VOICE_COMMUNICATION)
                                        .build())
                        .build();
        if (mAudioManager.requestAudioFocus(mFocusRequest) == AUDIOFOCUS_REQUEST_GRANTED) {
            return;
        }
        mFocusRequest = null;
        throw new Exception("Audio focus request failed");
    }

    private boolean isUsageSupported(int[] enforceableUsages, int usageToEnforce) {
        for (int enforceableUsage : enforceableUsages) {
            if (enforceableUsage == usageToEnforce) {
                return true;
            }
        }
        return false;
    }

    private String getUsageString() {
        return usageToString(mAudioUsageToEnforce);
    }

    private void createAndStartMediaPlayerWithUsage(int usageToEnforce) throws Exception {
        var preparedLatch = new CountDownLatch(1);
        var audioAttributes = new AudioAttributes.Builder().setUsage(usageToEnforce).build();
        var mediaPlayer = new MediaPlayer();
        mediaPlayer.setAudioAttributes(audioAttributes);
        try (var resource = mContext.getResources().openRawResourceFd(R.raw.test_music)) {
            mediaPlayer.setDataSource(
                    resource.getFileDescriptor(), resource.getStartOffset(), resource.getLength());
        }
        mediaPlayer.setOnPreparedListener(mp1 -> preparedLatch.countDown());
        mediaPlayer.prepare();
        assertWithMessage("MediaPlayer did not prepare in %s ms", TEST_PLAYER_TIMEOUT_MS)
                .that(preparedLatch.await(TEST_PLAYER_TIMEOUT_MS, MILLISECONDS))
                .isTrue();
        mediaPlayer.start();
        mMediaPlayer = mediaPlayer;
    }

    private static final class TestEnforceableAudioFocusCallback
            implements EnforceableAudioFocusCallback {

        private final List<EnforcedAudioFocusInfo> mEnforcedAudioFocusInfos = new ArrayList<>();
        CountDownLatch mEnforcementLatch = new CountDownLatch(1);

        @Override
        public void onEnforcedAudioFocusChanged(@NonNull List<EnforcedAudioFocusInfo> infos) {
            mEnforcedAudioFocusInfos.addAll(infos);
            mEnforcementLatch.countDown();
        }

        List<EnforcedAudioFocusInfo> getEnforcedInfos() {
            return new ArrayList<>(mEnforcedAudioFocusInfos);
        }

        boolean waitForCallback(int count) throws InterruptedException {
            return mEnforcementLatch.await(count * TEST_CALLBACK_TIMEOUT_MS, MILLISECONDS);
        }

        void reset() {
            mEnforcementLatch = new CountDownLatch(1);
        }
    }

    private static final class TestAudioFocusListener
            implements AudioManager.OnAudioFocusChangeListener {

        @Override
        public void onAudioFocusChange(int focusChange) {
            Log.i(TAG, "onAudioFocusChange: " + focusChange);
        }
    }
}
