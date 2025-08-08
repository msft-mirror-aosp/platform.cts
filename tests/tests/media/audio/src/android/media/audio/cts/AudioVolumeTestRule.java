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

package android.media.audio.cts;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.media.mediatestutils.TestUtils.getFutureForIntent;

import android.Manifest;
import android.app.AutomaticZenRule;
import android.app.Instrumentation;
import android.app.NotificationManager;
import android.content.Context;
import android.media.AudioManager;
import android.media.cts.Utils;
import android.util.Log;

import com.android.compatibility.common.util.AmUtils;

import org.junit.rules.ExternalResource;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class AudioVolumeTestRule extends ExternalResource {
    private static final String TAG = "AudioVolumeTestRule";

    public static final int INIT_VOL = 1;

    private static final int FUTURE_WAIT_SECS = 5;

    private final Context mContext;
    private final AudioManager mAudioManager;
    private final NotificationManager mNm;

    private int mOriginalRingerMode;
    private final Map<Integer, Integer> mOriginalStreamVolumes = new HashMap<>();
    private final Map<Integer, Boolean> mOriginalStreamMutes = new HashMap<>();
    private NotificationManager.Policy mOriginalNotificationPolicy;
    private int mOriginalZen;

    public AudioVolumeTestRule(Context context) {
        mContext = context;
        mAudioManager = mContext.getSystemService(AudioManager.class);
        mNm = mContext.getSystemService(NotificationManager.class);
    }

    public final NotificationManager.Policy getOriginalNotificationPolicy() {
        return mOriginalNotificationPolicy;
    }

    @Override
    public void before() throws Exception {
        final int[] streamTypes = {
            AudioManager.STREAM_VOICE_CALL,
            AudioManager.STREAM_SYSTEM,
            AudioManager.STREAM_RING,
            AudioManager.STREAM_MUSIC,
            AudioManager.STREAM_ALARM,
            AudioManager.STREAM_NOTIFICATION,
            AudioManager.STREAM_DTMF,
            AudioManager.STREAM_ACCESSIBILITY,
        };
        mOriginalRingerMode = mAudioManager.getRingerMode();
        final Instrumentation instrumentation = getInstrumentation();

        try {
            instrumentation
                    .getUiAutomation()
                    .adoptShellPermissionIdentity(
                            Manifest.permission.MODIFY_AUDIO_SETTINGS_PRIVILEGED,
                            Manifest.permission.STATUS_BAR_SERVICE);

            for (int streamType : streamTypes) {
                if (mAudioManager.getStreamTypeAlias(streamType) == streamType) {
                    mOriginalStreamVolumes.put(
                            streamType, mAudioManager.getStreamVolume(streamType));
                    mOriginalStreamMutes.put(streamType, mAudioManager.isStreamMute(streamType));
                }
            }
        } finally {
            instrumentation.getUiAutomation().dropShellPermissionIdentity();
        }

        try {
            Utils.toggleNotificationPolicyAccess(mContext.getPackageName(), instrumentation, true);

            mOriginalNotificationPolicy = mNm.getNotificationPolicy();
            mOriginalZen = mNm.getCurrentInterruptionFilter();
            mAudioManager.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
            var future =
                    getFutureForIntent(
                            mContext,
                            AudioManager.RINGER_MODE_CHANGED_ACTION,
                            i ->
                                    (i != null)
                                            && i.getIntExtra(AudioManager.EXTRA_RINGER_MODE, -1)
                                                    == AudioManager.RINGER_MODE_NORMAL);
            if (mOriginalZen != NotificationManager.INTERRUPTION_FILTER_ALL) {
                mNm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL);
            }
            var intent = future.get(FUTURE_WAIT_SECS, TimeUnit.SECONDS);
        } finally {
            Utils.toggleNotificationPolicyAccess(mContext.getPackageName(), instrumentation, false);
        }

        try {
            instrumentation
                    .getUiAutomation()
                    .adoptShellPermissionIdentity(
                            Manifest.permission.MODIFY_AUDIO_SETTINGS_PRIVILEGED,
                            Manifest.permission.STATUS_BAR_SERVICE);
            for (int streamType : streamTypes) {
                if (mAudioManager.getStreamTypeAlias(streamType) == streamType) {
                    mAudioManager.setStreamVolume(streamType, INIT_VOL, /* flags= */ 0);
                    mAudioManager.adjustStreamVolume(
                            streamType, AudioManager.ADJUST_UNMUTE, /* flags= */ 0);
                }
            }
        } finally {
            instrumentation.getUiAutomation().dropShellPermissionIdentity();
        }

        mAudioManager.waitForAudioHandlerBarrier();
        // Reduce flake due to late intent delivery
        AmUtils.waitForBroadcastIdle();
    }

    @Override
    public void after() {
        final Instrumentation instrumentation = getInstrumentation();

        try {
            try {
                Utils.toggleNotificationPolicyAccess(
                        mContext.getPackageName(), instrumentation, true);
            } catch (IOException e) {
                Log.e(TAG, "Failed to toggle notification policy access", e);
            }
            mAudioManager.setRingerMode(mOriginalRingerMode);

            mNm.setNotificationPolicy(mOriginalNotificationPolicy);
            if (mNm.getCurrentInterruptionFilter() != mOriginalZen) {
                mNm.setInterruptionFilter(mOriginalZen);
            }
            Map<String, AutomaticZenRule> rules = mNm.getAutomaticZenRules();
            for (String ruleId : rules.keySet()) {
                mNm.removeAutomaticZenRule(ruleId);
            }

            // Recover the volume and the ringer mode that the test may have overwritten.
            for (Map.Entry<Integer, Integer> e : mOriginalStreamVolumes.entrySet()) {
                mAudioManager.setStreamVolume(
                        e.getKey(), e.getValue(), AudioManager.FLAG_ALLOW_RINGER_MODES);
            }
        } finally {
            try {
                Utils.toggleNotificationPolicyAccess(
                        mContext.getPackageName(), instrumentation, false);
            } catch (IOException e) {
                Log.e(TAG, "Failed to toggle notification policy access", e);
            }
        }

        for (Map.Entry<Integer, Boolean> e : mOriginalStreamMutes.entrySet()) {
            if (mAudioManager.isStreamMute(e.getKey()) != e.getValue()) {
                mAudioManager.adjustStreamVolume(
                        e.getKey(),
                        e.getValue() ? AudioManager.ADJUST_MUTE : AudioManager.ADJUST_UNMUTE,
                        /* flags= */ 0);
            }
        }
    }
}
