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

import static android.media.AudioManager.AUDIOFOCUS_GAIN;
import static android.media.AudioManager.AUDIOFOCUS_NONE;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.app.Instrumentation;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.media.cts.Utils;
import android.os.Handler;
import android.os.HandlerThread;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.compatibility.common.util.FrameworkSpecificTest;
import com.android.compatibility.common.util.SystemUtil;
import com.android.media.mediatestutils.CancelAllFuturesRule;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@RunWith(BedsteadJUnit4.class)
@FrameworkSpecificTest
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")

public class MultiAudioFocusTest {
    private static final String TAG = "MultiAudioFocusTest";

    private static final int TEST_TIMING_TOLERANCE_MS = 200;

    private static final AudioAttributes ATTR_DRIVE_DIR = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build();
    private static final AudioAttributes ATTR_MEDIA = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build();

    private Context mContext;
    private AudioManager mAM;
    private NotificationManager mNM;
    private Instrumentation mInstrumentation;
    /** notification volume to restore */
    private int mInitialNotificationVolume;
    /** ringer mode to restore */
    private int mInitialRingerMode;
    private boolean mMultiFocusEnabled;
    private static final AudioAttributes NOTIFICATION_ATTRIBUTES = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION).build();

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Rule
    public final CancelAllFuturesRule mCancelRule = new CancelAllFuturesRule();

    @Before
    public void setUp() throws Exception {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mContext = mInstrumentation.getTargetContext();
        mAM = mContext.getSystemService(AudioManager.class);
        mNM = mContext.getSystemService(NotificationManager.class);

        mMultiFocusEnabled = mAM.isMultiAudioFocusEnabled();

        mInitialRingerMode = mAM.getRingerMode();
        // set Zen to off (interruption filter set to ALL) and ringer mode to NORMAL
        try {
            Utils.toggleNotificationPolicyAccess(
                    mContext.getPackageName(), mInstrumentation , true);

            SystemUtil.runWithShellPermissionIdentity(
                    () -> {
                        mNM.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL);
                        mAM.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                        mInitialNotificationVolume = mAM.getStreamVolume(
                                AudioAttributes.toLegacyStreamType(NOTIFICATION_ATTRIBUTES));
                    },
                    Manifest.permission.STATUS_BAR_SERVICE);
        } finally {
            Utils.toggleNotificationPolicyAccess(
                    mContext.getPackageName(), mInstrumentation, false);
        }

    }

    @After
    public void teardown() throws Exception {
        try {
            // restore ringer mode and notification volume
            Utils.toggleNotificationPolicyAccess(
                    mContext.getPackageName(), mInstrumentation, true);
            SystemUtil.runWithShellPermissionIdentity(
                    () -> {
                        mAM.setStreamVolume(
                                AudioAttributes.toLegacyStreamType(NOTIFICATION_ATTRIBUTES),
                                mInitialNotificationVolume, 0);
                        mAM.setRingerMode(mInitialRingerMode);
                    },
                    Manifest.permission.STATUS_BAR_SERVICE);
            Utils.toggleNotificationPolicyAccess(mContext.getPackageName(), mInstrumentation,
                    false);
        } finally {
            Utils.toggleNotificationPolicyAccess(
                    mContext.getPackageName(), mInstrumentation, false);
        }
    }

    //-----------------------------------
    // Test cases
    //-----------------------------------

    @Test
    public void testAudioFocusRequestGainLoss() throws Exception {
        assumeCarIsNotEnabled();
        assumeMultiFocus();
        final AudioAttributes[] attributes = { ATTR_DRIVE_DIR, ATTR_MEDIA };
        doTestTwoPlayersGain(AUDIOFOCUS_GAIN, AUDIOFOCUS_GAIN, attributes, false /*no handler*/);
    }

    @Test
    public void testAudioFocusRequestGainLossHandler() throws Exception {
        assumeCarIsNotEnabled();
        assumeMultiFocus();
        final AudioAttributes[] attributes = { ATTR_DRIVE_DIR, ATTR_MEDIA };
        doTestTwoPlayersGain(AUDIOFOCUS_GAIN, AUDIOFOCUS_GAIN, attributes, true /*with handler*/);
    }


    //-----------------------------------
    // Test utilities
    //-----------------------------------

    /**
     * Test focus request and abandon between two focus owners
     * @param gainTypeForFirstPlayer focus gain of the focus owner on bottom (== 1st focus request)
     * @param gainTypeForSecondPlayer focus gain of the focus owner on top (== 2nd focus request)
     * @param attributes Audio attributes for first and second player, in order.
     * @param useHandlerInListener listener on handler thread or not
     * @throws Exception when test fails
     */
    private void doTestTwoPlayersGain(int gainTypeForFirstPlayer, int gainTypeForSecondPlayer,
            AudioAttributes[] attributes, boolean useHandlerInListener) throws Exception {
        final int nbFocusOwners = 2;
        if (nbFocusOwners != attributes.length) {
            throw new IllegalArgumentException("Invalid test: invalid number of attributes");
        }
        final AudioFocusRequest[] focusRequests = new AudioFocusRequest[nbFocusOwners];
        final FocusChangeListener[] focusListeners = new FocusChangeListener[nbFocusOwners];
        final int[] focusGains = { gainTypeForFirstPlayer, gainTypeForSecondPlayer };

        // no focus loss is expected
        final int expectedLoss = AUDIOFOCUS_NONE;

        final Handler h;
        if (useHandlerInListener) {
            HandlerThread handlerThread = new HandlerThread(TAG);
            handlerThread.start();
            h = new Handler(handlerThread.getLooper());
        } else {
            h = null;
        }

        try {
            for (int i = 0; i < nbFocusOwners; i++) {
                focusListeners[i] = new FocusChangeListener();
                if (h != null) {
                    focusRequests[i] = new AudioFocusRequest.Builder(focusGains[i])
                            .setAudioAttributes(attributes[i])
                            .setOnAudioFocusChangeListener(focusListeners[i], h /*handler*/)
                            .build();
                } else {
                    focusRequests[i] = new AudioFocusRequest.Builder(focusGains[i])
                            .setAudioAttributes(attributes[i])
                            .setOnAudioFocusChangeListener(focusListeners[i])
                            .build();
                }
            }

            // focus owner 0 requests focus with gainTypeForFirstPlayer,
            int res = mAM.requestAudioFocus(focusRequests[0]);
            assertEquals("1st focus request failed",
                    AudioManager.AUDIOFOCUS_REQUEST_GRANTED, res);
            // then focus owner 1 requests focus with gainTypeForSecondPlayer
            res = mAM.requestAudioFocus(focusRequests[1]);
            assertEquals("2nd focus request failed", AudioManager.AUDIOFOCUS_REQUEST_GRANTED, res);
            focusListeners[0].waitForFocusChange("doTestTwoPlayersGain",
                    TEST_TIMING_TOLERANCE_MS, /* shouldAcquire= */ false);
            assertEquals("Focus loss was dispatched", expectedLoss,
                    focusListeners[0].getFocusChangeAndReset());
            // then 1 abandons focus
            res = mAM.abandonAudioFocusRequest(focusRequests[1]);
            assertEquals("1st abandon failed", AudioManager.AUDIOFOCUS_REQUEST_GRANTED, res);
            focusRequests[1] = null;
            focusListeners[0].waitForFocusChange("doTestTwoPlayersGain",
                    TEST_TIMING_TOLERANCE_MS, /* shouldAcquire= */ false);
            // verify there was no focus change because focus user 0 was kicked out of stack
            assertEquals("Focus change was dispatched", AudioManager.AUDIOFOCUS_NONE,
                    focusListeners[0].getFocusChangeAndReset());
            // then 0 abandons focus
            res = mAM.abandonAudioFocusRequest(focusRequests[0]);
            assertEquals("2nd abandon failed", AudioManager.AUDIOFOCUS_REQUEST_GRANTED, res);
            focusRequests[0] = null;
        } finally {
            for (int i = 0; i < nbFocusOwners; i++) {
                if (focusRequests[i] != null) {
                    mAM.abandonAudioFocusRequest(focusRequests[i]);
                }
            }
            if (h != null) {
                h.getLooper().quit();
            }
        }
    }

    private void assumeCarIsNotEnabled() {
        assumeFalse("Car audio service is enabled", isCar());
    }

    private void assumeMultiFocus() {
        assumeTrue("Multi-audio focus is not enabled", mMultiFocusEnabled);
    }

    protected boolean isCar() {
        return mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE);
    }

    private static class FocusChangeListener implements OnAudioFocusChangeListener {
        private final Object mLock = new Object();
        private final Semaphore mChangeEventSignal = new Semaphore(0);
        private int mFocusChange = AudioManager.AUDIOFOCUS_NONE;

        int getFocusChangeAndReset() {
            final int change;
            synchronized (mLock) {
                change = mFocusChange;
                mFocusChange = AudioManager.AUDIOFOCUS_NONE;
            }
            mChangeEventSignal.drainPermits();
            return change;
        }

        void waitForFocusChange(String caller, long timeoutMs, boolean shouldAcquire)
                throws Exception {
            boolean acquired = mChangeEventSignal.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS);
            assertWithMessage(caller + " wait acquired").that(acquired).isEqualTo(shouldAcquire);
        }

        @Override
        public void onAudioFocusChange(int focusChange) {
            Log.i(TAG, "onAudioFocusChange:" + focusChange + " listener:" + this);
            synchronized (mLock) {
                mFocusChange = focusChange;
            }
            mChangeEventSignal.release();
        }
    }
}
