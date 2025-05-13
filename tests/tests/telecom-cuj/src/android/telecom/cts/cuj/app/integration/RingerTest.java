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

package android.telecom.cts.cuj.app.integration;

import static android.media.AudioAttributes.USAGE_NOTIFICATION_RINGTONE;
import static android.media.Utils.SYNCHRONIZED_VIBRATION;
import static android.media.Utils.VIBRATION_URI_PARAM;
import static android.telecom.Call.STATE_DISCONNECTED;
import static android.telecom.Call.STATE_RINGING;
import static android.telecom.cts.apps.ShellCommandExecutor.executeShellCommand;
import static android.telecom.cts.apps.TelecomTestApp.ConnectionServiceVoipAppMain;
import static android.telecom.cts.apps.TelecomTestApp.ManagedConnectionServiceApp;
import static android.telecom.cts.apps.TelecomTestApp.TransactionalVoipAppMain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeNotNull;
import static org.junit.Assume.assumeTrue;

import android.app.NotificationManager;
import android.media.AudioManager;
import android.media.AudioManager.AudioPlaybackCallback;
import android.media.AudioPlaybackConfiguration;
import android.media.RingtoneManager;
import android.media.audio.Flags;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.Settings;
import android.telecom.PhoneAccountHandle;
import android.telecom.VideoProfile;
import android.telecom.cts.apps.AppControlWrapper;
import android.telecom.cts.cuj.BaseAppVerifier;
import android.telecom.cts.cuj.TestUtils;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.CddTest;
import com.android.compatibility.common.util.ShellIdentityUtils;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This test class should test common ringer and vibrations/haptics scenarios that involve only a
 * single application.
 */
@RunWith(JUnit4.class)
public class RingerTest extends BaseAppVerifier {
    private static final String EVENT_FOR_TEST = "android.telecom.event.TEST";
    private static final long WAIT_FOR_STATE_CHANGE_TIMEOUT_MS = 10000;
    private static final long WAIT_FOR_NO_RING_TIMEOUT_MS = 1000;
    private static final long WAIT_FOR_VIBRATOR_LOGS_UPDATE_TIMEOUT_MS = 10000;
    private static final int OFF = 0;
    private static final int ON = 1;
    private static final Pattern CURRENT_VIBRATIONS_PATTERN =
            Pattern.compile("CurrentVibration:.*?Recent vibrations:");
    private static final Pattern RINGTONE_VIBRATION_PATTERN =
            Pattern.compile("mUsage=RINGTONE");
    private static final String QUERY_VIBRATOR_MANAGER_COMMAND = "dumpsys vibrator_manager";
    private static final String TAG = "RingerTest";

    /**
     * Test the scenario where a new MANAGED incoming call is created and transitions to RINGING
     * while the ringer is in NORMAL mode and "Vibrations & haptics" are enabled.
     *
     * <h3> Test Steps: </h3>
     * <ul>
     *  1. create a managed call that is backed by a {@link android.telecom.ConnectionService }
     *  via {@link android.telecom.TelecomManager#addNewIncomingCall(PhoneAccountHandle, Bundle)}
     * <p>
     *  2. verify that the call rang audibly and {@link AudioPlaybackCallback} was triggered.
     * <p>
     *  3. inspect the vibrator_manager dumpsys to ensure that a current ringtone vibration logged.
     * <p>
     *  4. disconnect the call
     */
    @Ignore // TODO(b/393989489): Diagnose flakiness and re-enable.
    @Test
    @CddTest(requirements = {"7.4.1.2/H-0-2"})
    @ApiTest(apis = {"android.telecom.TelecomManager#addNewIncomingCall"})
    public void testIncomingCall_RingAndVibrate() throws Exception {
        assumeTrue(mShouldTestTelecom);
        assumeTrue(mSupportsManagedCalls);
        assumeTrue(hasVibrator());

        // Configure the audio manager and register the audio playback callback:
        LinkedBlockingQueue<Boolean> queue = new LinkedBlockingQueue<>(1);
        AudioPlaybackCallback callback = createAudioPlaybackCallback(queue);
        AudioManager audioManager =
                configureAudioManager(AudioManager.RINGER_MODE_NORMAL, callback);

        // Configure the "Vibrations & haptics" settings:
        configureVibrationSettings(ON);

        AppControlWrapper managedApp = null;
        try {
            managedApp = bindToApp(ManagedConnectionServiceApp);
            String mt = addIncomingCallAndVerify(managedApp);

            // Verify that the call rang:
            verifyCallIsInState(mt, STATE_RINGING);
            Boolean ringing = queue.poll(WAIT_FOR_STATE_CHANGE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            assertNotNull("Telecom should have played a ringtone, timed out waiting for "
                            + "state change", ringing);
            assertTrue("Telecom should have played a ringtone.", ringing);

            // Verify that the device is vibrating by inspecting the vibrator dumpsys:
            waitForRingtoneVibrationLogOrTimeout();

            // Disconnect the call:
            answerViaInCallServiceAndVerify(mt, VideoProfile.STATE_AUDIO_ONLY);
            setCallStateAndVerify(managedApp, mt, STATE_DISCONNECTED);
        } finally {
            tearDownApp(managedApp);
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                    audioManager, am -> am.unregisterAudioPlaybackCallback(callback));
        }
    }

    /**
     * Test the scenario where a new MANAGED incoming call is created and transitions to RINGING
     * while the ringer is in NORMAL mode and "Vibrations & haptics" are disabled.
     *
     * <h3> Test Steps: </h3>
     * <ul>
     *  1. create a managed call that is backed by a {@link android.telecom.ConnectionService }
     *  via {@link android.telecom.TelecomManager#addNewIncomingCall(PhoneAccountHandle, Bundle)}
     * <p>
     *  2. verify that the call rang audibly and {@link AudioPlaybackCallback} was triggered.
     * <p>
     *  3. inspect the vibrator_manager dumpsys to ensure that no current ringtone vibration logged.
     * <p>
     *  4. disconnect the call
     */
    @Ignore // TODO(b/393989489): Diagnose flakiness and re-enable.
    @Test
    @CddTest(requirements = {"7.4.1.2/H-0-2"})
    @ApiTest(apis = {"android.telecom.TelecomManager#addNewIncomingCall"})
    public void testIncomingCallVibrationDisabled_RingAndNoVibrate() throws Exception {
        assumeTrue(mShouldTestTelecom);
        assumeTrue(mSupportsManagedCalls);
        assumeTrue(hasVibrator());

        // Configure the audio manager and register the audio playback callback:
        LinkedBlockingQueue<Boolean> queue = new LinkedBlockingQueue<>(1);
        AudioPlaybackCallback callback = createAudioPlaybackCallback(queue);
        AudioManager audioManager =
                configureAudioManager(AudioManager.RINGER_MODE_NORMAL, callback);

        // Configure the "Vibrations & haptics" settings:
        configureVibrationSettings(OFF);

        AppControlWrapper managedApp = null;
        try {
            managedApp = bindToApp(ManagedConnectionServiceApp);
            String mt = addIncomingCallAndVerify(managedApp);

            // Verify that the call rang:
            verifyCallIsInState(mt, STATE_RINGING);
            Boolean ringing = queue.poll(WAIT_FOR_STATE_CHANGE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            assertNotNull("Telecom should have played a ringtone, timed out waiting for "
                            + "state change", ringing);
            assertTrue("Telecom should have played a ringtone.", ringing);

            // Verify that the device is not vibrating by inspecting the vibrator dumpsys:
            waitToEnsureNoRingtoneVibrationLog();

            // Disconnect the call:
            answerViaInCallServiceAndVerify(mt, VideoProfile.STATE_AUDIO_ONLY);
            setCallStateAndVerify(managedApp, mt, STATE_DISCONNECTED);
        } finally {
            tearDownApp(managedApp);
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                    audioManager, am -> am.unregisterAudioPlaybackCallback(callback));
        }
    }

    /**
     * Test the scenario where a new MANAGED incoming call is created and transitions to RINGING
     * while "Vibrations & haptics" are disabled but the ringer is in SILENT mode.
     *
     * <h3> Test Steps: </h3>
     * <ul>
     *  1. create a managed call that is backed by a {@link android.telecom.ConnectionService }
     *  via {@link android.telecom.TelecomManager#addNewIncomingCall(PhoneAccountHandle, Bundle)}
     * <p>
     *  2. verify that the call did not rang audibly and {@link AudioPlaybackCallback} was not
     *  triggered.
     * <p>
     *  3. inspect the vibrator_manager dumpsys to ensure that no current ringtone vibration logged.
     * <p>
     *  4. disconnect the call
     */
    @Test
    @CddTest(requirements = {"7.4.1.2/H-0-2"})
    @ApiTest(apis = {"android.telecom.TelecomManager#addNewIncomingCall"})
    public void testIncomingCallSilentMode_NoRingAndNoVibrate() throws Exception {
        assumeTrue(mShouldTestTelecom);
        assumeTrue(mSupportsManagedCalls);
        assumeTrue(hasVibrator());

        // Configure the audio manager and register the audio playback callback:
        LinkedBlockingQueue<Boolean> queue = new LinkedBlockingQueue<>(1);
        AudioPlaybackCallback callback = createAudioPlaybackCallback(queue);
        AudioManager audioManager =
                configureAudioManager(AudioManager.RINGER_MODE_SILENT, callback);

        // Configure the "Vibrations & haptics" settings:
        configureVibrationSettings(ON);

        AppControlWrapper managedApp = null;
        try {
            managedApp = bindToApp(ManagedConnectionServiceApp);
            String mt = addIncomingCallAndVerify(managedApp);

            // Verify that the call did not audibly ring:
            verifyCallIsInState(mt, STATE_RINGING);
            Boolean ringing = queue.poll(WAIT_FOR_NO_RING_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            assertNull("Telecom should not have played a ringtone since ringer is "
                            + "in SILENT mode", ringing);

            // Verify that the device is not vibrating by inspecting the vibrator dumpsys:
            waitToEnsureNoRingtoneVibrationLog();

            // Disconnect the call:
            answerViaInCallServiceAndVerify(mt, VideoProfile.STATE_AUDIO_ONLY);
            setCallStateAndVerify(managedApp, mt, STATE_DISCONNECTED);
        } finally {
            tearDownApp(managedApp);
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                    audioManager, am -> am.unregisterAudioPlaybackCallback(callback));
        }
    }

    /**
     * Test the scenario where a new MANAGED incoming call is created and transitions to RINGING
     * while the ringer is in VIBRATE mode and "Vibrations & haptics" are enabled.
     *
     * <h3> Test Steps: </h3>
     * <ul>
     *  1. create a managed call that is backed by a {@link android.telecom.ConnectionService }
     *  via {@link android.telecom.TelecomManager#addNewIncomingCall(PhoneAccountHandle, Bundle)}
     * <p>
     *  2. verify that the call did not ring audibly and that {@link AudioPlaybackCallback} was not
     *  triggered.
     * <p>
     *  3. inspect the vibrator_manager dumpsys to ensure that a current ringtone vibration logged.
     * <p>
     *  4. disconnect the call
     */
    @Ignore // TODO(b/393989489): Diagnose flakiness and re-enable.
    @Test
    @CddTest(requirements = {"7.4.1.2/H-0-2"})
    @ApiTest(apis = {"android.telecom.TelecomManager#addNewIncomingCall"})
    public void testIncomingCallVibrateMode_VibrateAndNoRing() throws Exception {
        assumeTrue(mShouldTestTelecom);
        assumeTrue(mSupportsManagedCalls);
        assumeTrue(hasVibrator());
        assumeFalse(TestUtils.isRingtoneVibrationSupported(mContext));

        // Configure the audio manager and register the audio playback callback:
        LinkedBlockingQueue<Boolean> queue = new LinkedBlockingQueue<>(1);
        AudioPlaybackCallback callback = createAudioPlaybackCallback(queue);
        AudioManager audioManager =
                configureAudioManager(AudioManager.RINGER_MODE_VIBRATE, callback);

        // Configure the "Vibrations & haptics" settings:
        configureVibrationSettings(ON);

        AppControlWrapper managedApp = null;
        try {
            managedApp = bindToApp(ManagedConnectionServiceApp);
            String mt = addIncomingCallAndVerify(managedApp);

            // Verify that the call did not ring:
            verifyCallIsInState(mt, STATE_RINGING);
            Boolean ringing = queue.poll(WAIT_FOR_NO_RING_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            assertNull("Telecom should not have played a ringtone since ringer is in "
                            + "VIBRATE mode", ringing);

            // Verify that the device is vibrating by inspecting the vibrator dumpsys:
            waitForRingtoneVibrationLogOrTimeout();

            // Disconnect the call:
            answerViaInCallServiceAndVerify(mt, VideoProfile.STATE_AUDIO_ONLY);
            setCallStateAndVerify(managedApp, mt, STATE_DISCONNECTED);
        } finally {
            tearDownApp(managedApp);
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                    audioManager, am -> am.unregisterAudioPlaybackCallback(callback));
        }
    }

    /**
     * Test the scenario where a new MANAGED incoming call is created and transitions to RINGING
     * while the ringer is in VIBRATE mode and "Vibrations & haptics" are enabled. While the MT
     * call is still RINGING, change the ringer mode to NORMAL mode.
     *
     * <h3> Test Steps: </h3>
     * <ul>
     *  1. create a managed call that is backed by a {@link android.telecom.ConnectionService }
     *  via {@link android.telecom.TelecomManager#addNewIncomingCall(PhoneAccountHandle, Bundle)}
     * <p>
     *  2. verify that the call did not ring audibly and that {@link AudioPlaybackCallback} was not
     *  triggered.
     * <p>
     *  3. inspect the vibrator_manager dumpsys to ensure that a current ringtone vibration logged.
     * <p>
     *  4. while the MT call is still RINGING, change the ringer mode to NORMAL mode.
     * <p>
     *  5. verify that the call is now ringing audibly, {@link AudioPlaybackCallback} was
     *  triggered, and a current ringtone vibration is still logged in the dumpsys.
     *  <p>
     *  6. disconnect the call
     */
    @Ignore // TODO(b/393989489): Diagnose flakiness and re-enable.
    @Test
    @CddTest(requirements = {"7.4.1.2/H-0-2"})
    @ApiTest(apis = {"android.telecom.TelecomManager#addNewIncomingCall"})
    public void testIncomingCallVibrateModeEnableRinger_VibrateAndRing() throws Exception {
        assumeTrue(mShouldTestTelecom);
        assumeTrue(mSupportsManagedCalls);
        assumeTrue(hasVibrator());

        // Configure the audio manager and register the audio playback callback:
        LinkedBlockingQueue<Boolean> queue = new LinkedBlockingQueue<>(1);
        AudioPlaybackCallback callback = createAudioPlaybackCallback(queue);
        AudioManager audioManager =
                configureAudioManager(AudioManager.RINGER_MODE_VIBRATE, callback);

        // Configure the "Vibrations & haptics" settings:
        configureVibrationSettings(ON);

        AppControlWrapper managedApp = null;
        try {
            managedApp = bindToApp(ManagedConnectionServiceApp);
            String mt = addIncomingCallAndVerify(managedApp);

            // Verify that the call is not ringing audibly:
            verifyCallIsInState(mt, STATE_RINGING);
            Boolean originalRingingState =
                    queue.poll(WAIT_FOR_NO_RING_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!TestUtils.isRingtoneVibrationSupported(mContext)) {
                assertNull("Telecom should not have played a ringtone since ringer is in "
                        + "VIBRATE mode", originalRingingState);
            }
            // Verify that the device is vibrating by inspecting the vibrator dumpsys:
            waitForRingtoneVibrationLogOrTimeout();

            // While the MT call is still ringing, change the ringer mode to “normal” mode:
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(audioManager,
                    am -> am.setRingerMode(AudioManager.RINGER_MODE_NORMAL));

            // Verify that the call is now audibly ringing:
            Boolean updatedRingingState =
                    queue.poll(WAIT_FOR_STATE_CHANGE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            assertNotNull("Telecom should have played a ringtone, timed out waiting for "
                    + "state change", updatedRingingState);
            assertTrue("Telecom should have played a ringtone.", updatedRingingState);

            // Verify that the device is still vibrating by inspecting the vibrator dumpsys:
            waitForRingtoneVibrationLogOrTimeout();

            // Disconnect the call:
            answerViaInCallServiceAndVerify(mt, VideoProfile.STATE_AUDIO_ONLY);
            setCallStateAndVerify(managedApp, mt, STATE_DISCONNECTED);
        } finally {
            tearDownApp(managedApp);
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                    audioManager, am -> am.unregisterAudioPlaybackCallback(callback));
        }
    }

    /**
     * Test the scenario where a new MANAGED incoming call is created while the ringer is in
     * VIBRATE mode and "Vibrations & haptics" are enabled if device supports ringtone vibration
     * settings.
     * <p>
     *
     * <h3> Test Steps: </h3>
     *  1. create a managed call that is backed by a {@link android.telecom.ConnectionService }
     *  via {@link android.telecom.TelecomManager#addNewIncomingCall(PhoneAccountHandle, Bundle)}
     * <p>
     *  2. verify that the silent call rang with vibration and that {@link AudioPlaybackCallback}
     *  was triggered.
     * <p>
     *  3. disconnect the call
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_RINGTONE_HAPTICS_CUSTOMIZATION)
    @CddTest(requirements = {"7.4.1.2/H-0-2"})
    @ApiTest(apis = {"android.telecom.TelecomManager#addNewIncomingCall"})
    public void testIncomingCallVibrateMode_vibrationSettingsSupported_VibrateAndRing()
            throws Exception {
        Uri defaultRingtoneUri = RingtoneManager.getActualDefaultRingtoneUri(mContext,
                RingtoneManager.TYPE_RINGTONE);
        assumeNotNull(defaultRingtoneUri);
        Uri testRingtoneUri = defaultRingtoneUri.buildUpon().appendQueryParameter(
                VIBRATION_URI_PARAM, SYNCHRONIZED_VIBRATION).build();

        assumeTrue(mShouldTestTelecom);
        assumeTrue(mSupportsManagedCalls);
        assumeTrue(hasVibrator());
        assumeTrue(TestUtils.isRingtoneVibrationSupported(mContext));

        // Configure the audio manager and register the audio playback callback:
        LinkedBlockingQueue<Boolean> queue = new LinkedBlockingQueue<>(1);
        AudioPlaybackCallback callback = createAudioPlaybackCallback(queue);
        AudioManager audioManager =
                configureAudioManager(AudioManager.RINGER_MODE_VIBRATE, callback);

        // Configure the "Vibrations & haptics" settings:
        configureVibrationSettings(ON);

        AppControlWrapper managedApp = null;
        try {
            ShellIdentityUtils.invokeWithShellPermissions(
                    () -> RingtoneManager.setActualDefaultRingtoneUri(mContext,
                            RingtoneManager.TYPE_RINGTONE, testRingtoneUri));
            managedApp = bindToApp(ManagedConnectionServiceApp);
            String mt = addIncomingCallAndVerify(managedApp);

            // Verify that the call rang:
            verifyCallIsInState(mt, STATE_RINGING);
            Boolean ringing = queue.poll(WAIT_FOR_STATE_CHANGE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            assertNotNull("Telecom should have played a ringtone, timed out waiting for "
                    + "state change", ringing);
            assertTrue("Telecom should have played a ringtone.", ringing);

            // Verify that the device is vibrating by inspecting the vibrator dumpsys:
            waitForRingtoneVibrationLogOrTimeout();

            // Disconnect the call:
            answerViaInCallServiceAndVerify(mt, VideoProfile.STATE_AUDIO_ONLY);
            setCallStateAndVerify(managedApp, mt, STATE_DISCONNECTED);
        } finally {
            // restore the default ringtone
            ShellIdentityUtils.invokeWithShellPermissions(
                    () -> RingtoneManager.setActualDefaultRingtoneUri(mContext,
                            RingtoneManager.TYPE_RINGTONE, defaultRingtoneUri));
            tearDownApp(managedApp);
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                    audioManager, am -> am.unregisterAudioPlaybackCallback(callback));
        }
    }

    /** Verify when DND is off that ringing focus is acquired when the call is in ringing state. */
    @Test
    @CddTest(requirements = {"7.4.1.2/H-0-2"})
    @ApiTest(apis = {"android.telecom.TelecomManager#addNewIncomingCall"})
    public void testIncomingSelfManagedVoipCallWithDndOffGainsFocus() throws Exception {
        assumeTrue("This test requires Telecom to be supported", mShouldTestTelecom);
        AppControlWrapper app = null;
        try {
            app = bindToApp(ConnectionServiceVoipAppMain);
            verifyDndBehavior(app, false /* isDndOn */);
        } finally {
            tearDownApp(app);
        }
    }

    /** Verify when DND is off that ringing focus is acquired when the call is in ringing state. */
    @Test
    @CddTest(requirements = {"7.4.1.2/H-0-2"})
    @ApiTest(apis = {"android.telecom.TelecomManager#addCall"})
    public void testIncomingTransactionalVoipCallWithDndOffGainsFocus() throws Exception {
        assumeTrue("This test requires Telecom to be supported", mShouldTestTelecom);
        AppControlWrapper app = null;
        try {
            app = bindToApp(TransactionalVoipAppMain);
            verifyDndBehavior(app, false /* isDndOn */);
        } finally {
            tearDownApp(app);
        }
    }

    /**
     * Verify for a self-managed ConnectionService which adds a new incoming call that Telecom will
     * not try to get audio focus while the call is in ringing state if the device is in DND mode.
     */
    @Test
    @CddTest(requirements = {"7.4.1.2/H-0-2"})
    @ApiTest(apis = {"android.telecom.TelecomManager#addNewIncomingCall"})
    @RequiresFlagsEnabled(com.android.server.telecom.flags.Flags.FLAG_VOIP_DND_FOCUS)
    public void testIncomingSelfManagedVoipCallDuringDndDoesntGainFocus() throws Exception {
        assumeTrue("This test requires Telecom to be supported", mShouldTestTelecom);
        AppControlWrapper app = null;
        try {
            app = bindToApp(ConnectionServiceVoipAppMain);
            verifyDndBehavior(app, true /* isDndOn */);
        } finally {
            tearDownApp(app);
        }
    }

    /**
     * Verify for a transactional voip app which adds a new incoming call that Telecom will not try
     * to get audio focus while the call is in ringing state if the device is in DND mode.
     */
    @Test
    @RequiresFlagsEnabled(com.android.server.telecom.flags.Flags.FLAG_VOIP_DND_FOCUS)
    @CddTest(requirements = {"7.4.1.2/H-0-2"})
    @ApiTest(apis = {"android.telecom.TelecomManager#addCall"})
    public void testIncomingTransactionalVoipCallDuringDndDoesntGainFocus() throws Exception {
        assumeTrue("This test requires Telecom to be supported", mShouldTestTelecom);
        AppControlWrapper app = null;
        try {
            app = bindToApp(TransactionalVoipAppMain);
            verifyDndBehavior(app, true /* isDndOn */);
        } finally {
            tearDownApp(app);
        }
    }

    /**
     * Common test method to verify DND behavior.
     *
     * @param app App to test
     * @param isDndOn Whether DND is on or off
     * @throws Exception Catch-all for exceptions
     */
    private void verifyDndBehavior(AppControlWrapper app, boolean isDndOn) throws Exception {
        AudioManager audioManager = mContext.getSystemService(AudioManager.class);
        assertNotNull("AudioManager should not be null", audioManager);
        // Configure the ringer mode to normal; we are testing DND so it can override to whatever
        // makes sense.
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                audioManager, am -> am.setRingerMode(AudioManager.RINGER_MODE_NORMAL));

        NotificationManager notificationManager =
                mContext.getSystemService(NotificationManager.class);
        assertNotNull("NotificationManager should not be null", notificationManager);

        // Cache the current DND mode
        // It's necessary to grant ACCESS_NOTIFICATION_POLICY to read the current DND state.
        // ShellIdentityUtils handles running this with shell permissions.
        Integer originalInterruptionFilter =
                ShellIdentityUtils.invokeWithShellPermissions(
                        notificationManager::getCurrentInterruptionFilter);
        assertNotNull(
                "Original interruption filter should not be null", originalInterruptionFilter);
        Log.d(TAG, "Original DND mode: " + originalInterruptionFilter);

        int desiredDndState =
                isDndOn
                        ? NotificationManager.INTERRUPTION_FILTER_NONE
                        : NotificationManager.INTERRUPTION_FILTER_ALL;
        try {
            // Enable DND (e.g., priority only mode)
            // Grant ACCESS_NOTIFICATION_POLICY to change the DND state.
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                    notificationManager, nm -> nm.setInterruptionFilter(desiredDndState));

            int dndEnabledFilter =
                    ShellIdentityUtils.invokeWithShellPermissions(
                            notificationManager::getCurrentInterruptionFilter);
            assertEquals(desiredDndState, dndEnabledFilter);

            // Start pretending to play music
            acquireAudioFocusForMusic();

            // Make an incoming call with the test app and ensure it is ringing.
            String mt = addIncomingCallAndVerify(app);
            verifyCallIsInState(mt, STATE_RINGING);

            // We will send a connection event through Telecom up to the ics just to make sure that
            // all of the ringer focus checks have happened; the InCallController updates before the
            // ringing focus updates; waiting on a connection event ensures that has completed and
            // we can now rely on focus being obtained by Telecom if it was ever going to be.
            setExpectedEvent(mt, EVENT_FOR_TEST);
            sendConnectionEvent(app, mt, EVENT_FOR_TEST);
            assertTrue(waitOnExpectedEvent(mt));

            if (isDndOn) {
                // Music playback should not have changed due to DND being enabled; in other words
                // Telecom should not have tried to get audio focus because DND is suppressing the
                // call.
                assertFalse(
                        "Expected no audio focus change in ringing due to DND",
                        hasMusicFocusChanged());
            } else {
                // We should have gotten back an indication that focus switched to our listener.
                waitForAndVerifyMusicFocus(
                        true,
                        AudioManager.AUDIOFOCUS_LOSS,
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK);
            }

            // Disconnect the incoming call
            setCallStateAndVerify(app, mt, STATE_DISCONNECTED);
        } finally {
            // Restore the cached DND state
            // Ensure the original DND state is restored even if the test assertions fail.
            final int filterToRestore = originalInterruptionFilter;
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                    notificationManager, nm -> nm.setInterruptionFilter(filterToRestore));
            Log.d(TAG, "Restored DND mode to: " + filterToRestore);

            // Verify DND is restored
            Integer restoredInterruptionFilter =
                    ShellIdentityUtils.invokeWithShellPermissions(
                            notificationManager::getCurrentInterruptionFilter);
            assertEquals(
                    "DND mode should be restored to the original state",
                    originalInterruptionFilter,
                    restoredInterruptionFilter);
        }
    }

    private static Boolean isCurrentVibrationInDumpsys() throws Exception {
        String result =
                executeShellCommand(
                        InstrumentationRegistry.getInstrumentation(),
                        QUERY_VIBRATOR_MANAGER_COMMAND);
        String currentVibrations = null;

        Matcher currentVibrationsMatcher = CURRENT_VIBRATIONS_PATTERN.matcher(result);

        while (currentVibrationsMatcher.find()) {
            currentVibrations = currentVibrationsMatcher.group(0);
        }

        if (currentVibrations != null) {
            Log.d(TAG, "isCurrentVibrationInDumpsys: currentVibrations=" + currentVibrations);
            Matcher ringtoneMatcher = RINGTONE_VIBRATION_PATTERN.matcher(currentVibrations);
            if (ringtoneMatcher.find()) {
                Log.d(TAG, "isCurrentVibrationInDumpsys: true");
                return true;
            }
        }
        Log.d(TAG, "isCurrentVibrationInDumpsys: false");
        return false;
    }

    private AudioManager configureAudioManager(int ringerMode, AudioPlaybackCallback callback) {
        AudioManager audioManager = mContext.getSystemService(AudioManager.class);
        assertNotNull("AudioManager should not be null", audioManager);

        // Configure the ringer mode:
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(audioManager,
                am -> am.setRingerMode(ringerMode));

        // Need to register as shell or we will never get callbacks for telecom.
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                audioManager,
                am ->
                        am.registerAudioPlaybackCallback(
                                callback, new Handler(Looper.getMainLooper())));

        return audioManager;
    }

    private AudioPlaybackCallback createAudioPlaybackCallback(LinkedBlockingQueue<Boolean> queue) {
        return new AudioPlaybackCallback() {
            @Override
            public void onPlaybackConfigChanged(List<AudioPlaybackConfiguration> configs) {
                super.onPlaybackConfigChanged(configs);
                boolean isPlayingRingtone = configs.stream()
                        .anyMatch(c -> c.getAudioAttributes().getUsage()
                                == USAGE_NOTIFICATION_RINGTONE);
                if (isPlayingRingtone && queue.isEmpty()) {
                    queue.add(isPlayingRingtone);
                }
            }
        };
    }

    void configureVibrationSettings (int vibrationSetting) {
        Settings settings = mContext.getSystemService(Settings.class);
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(settings,
                s -> Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.VIBRATE_ON, vibrationSetting));
    }

    private boolean hasVibrator() {
        return mContext.getSystemService(Vibrator.class).hasVibrator();
    }

    void waitToEnsureNoRingtoneVibrationLog() throws Exception {
        sleep(WAIT_FOR_NO_RING_TIMEOUT_MS);
        assertFalse("Current vibration should not be in dumpsys",
                isCurrentVibrationInDumpsys());
    }

    void waitForRingtoneVibrationLogOrTimeout() throws Exception {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return true;
                    }

                    @Override
                    public Object actual () throws Exception {
                        return isCurrentVibrationInDumpsys();
                    }
                },
                WAIT_FOR_VIBRATOR_LOGS_UPDATE_TIMEOUT_MS,
                "Current ringtone vibration never logged to dumpsys before timeout."
        );
    }

    void waitUntilConditionIsTrueOrTimeout(Condition condition, long timeout,
            String description) throws Exception {
        final long start = System.currentTimeMillis();
        while (!Objects.equals(condition.expected(), condition.actual())
                && System.currentTimeMillis() - start < timeout) {
            sleep(50);
        }
        assertEquals(description, condition.expected(), condition.actual());
    }

    protected interface Condition {
        Object expected();
        Object actual() throws Exception;
    }

    void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
        }
    }
}
