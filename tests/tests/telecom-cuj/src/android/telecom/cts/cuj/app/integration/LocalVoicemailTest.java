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
package android.telecom.cts.cuj.app.integration;

import static android.telecom.cts.apps.ShellCommandExecutor.COMMAND_SET_LOCAL_VOICEMAIL_PACKAGE;
import static android.telecom.cts.apps.ShellCommandExecutor.executeShellCommand;
import static android.telecom.cts.apps.TelecomTestApp.ManagedConnectionServiceApp;

import static com.android.compatibility.common.util.ShellIdentityUtils.invokeMethodWithShellPermissions;
import static com.android.compatibility.common.util.ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.content.ComponentName;
import android.media.AudioManager;
import android.os.Bundle;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.telecom.Call;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telecom.cts.apps.AppControlWrapper;
import android.telecom.cts.cuj.BaseAppVerifier;
import android.telecom.cts.cuj.CujLocalVoicemailService;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiTest;
import com.android.server.telecom.flags.Flags;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/** Tests for local voicemail APIs and functionality. */
@RunWith(JUnit4.class)
@RequiresFlagsEnabled(Flags.FLAG_LOCAL_VOICEMAIL)
public class LocalVoicemailTest extends BaseAppVerifier {

    public static final int STATE_TIMEOUT_MILLIS = 5000;
    private TelecomManager mTelecomManager;
    private AudioManager mAudioManager;

    @Before
    public void setUp() throws Exception {
        super.setUp();

        mTelecomManager = mContext.getSystemService(TelecomManager.class);
        mAudioManager = mContext.getSystemService(AudioManager.class);
    }

    /**
     * Verifies that {@link TelecomManager#isLocalVoicemailSupported()} throws a security exception
     * if the caller does not hold the required permission.
     */
    @Test
    @ApiTest(apis = {"android.telecom.TelecomManager#isLocalVoicemailSupported"})
    public void testIsLocalVoicemailSupportedNoPermission() {
        assumeTrue("Skipped for devices with no FEATURE_TELECOM", mShouldTestTelecom);

        SecurityException caughtException = null;
        try {
            mTelecomManager.isLocalVoicemailSupported();
            fail("Expected SecurityException to be thrown when caller doesn't have permission.");
        } catch (SecurityException se) {
            // Expected; we wanted this to throw!
            caughtException = se;
        }
        assertNotNull("Expected SecurityException for unpermitted called", caughtException);

        assertThrows(
                "Expected SecurityException for unpermitted called",
                SecurityException.class,
                () -> mTelecomManager.isLocalVoicemailSupported());
    }

    /**
     * Verifies that overriding the local voicemail service package via the telecom shell command
     * works as expected and that {@link TelecomManager#isLocalVoicemailSupported()} reflects the
     * fact a local vm service is defined.
     */
    @Test
    @ApiTest(apis = {"android.telecom.TelecomManager#isLocalVoicemailSupported"})
    public void testIsLocalVoicemailSupported() throws Exception {
        assumeTrue("Skipped for devices with no FEATURE_TELECOM", mShouldTestTelecom);

        // We don't really know if the device supports it or not
        boolean wasEnabled =
                invokeMethodWithShellPermissions(
                        mTelecomManager, tm -> tm.isLocalVoicemailSupported());

        try {
            configureTestLocalVoicemailServiceAndVerify();
        } finally {
            resetTestLocalVoicemailService();

            // Ensure that the supported state is restored after the test completes.
            boolean isNowEnabled =
                    invokeMethodWithShellPermissions(
                            mTelecomManager, tm -> tm.isLocalVoicemailSupported());
            assertEquals(
                    "Expected the initial `isLocalVoicemailSupported` state to be restored "
                            + "after the test.",
                    wasEnabled,
                    isNowEnabled);
        }
    }

    /**
     * Verifies that {@link TelecomManager#getLocalVoicemailTimeout(PhoneAccountHandle)} checks for
     * the required permission.
     */
    @Test
    @ApiTest(apis = {"android.telecom.TelecomManager#getLocalVoicemailTimeout"})
    public void testGetLocalVoicemailTimeoutNoPermission() throws Exception {
        assumeTrue("Skipped for devices with no FEATURE_TELECOM", mShouldTestTelecom);
        assumeTrue("Skipped for devices with no dialer role", mSupportsManagedCalls);

        PhoneAccountHandle invalidPhac =
                new PhoneAccountHandle(new ComponentName("foo", "bar"), "90210");
        assertThrows(
                "Expected IllegalArgumentException for invalid account.",
                IllegalArgumentException.class,
                () -> {
                    Duration duration =
                            invokeMethodWithShellPermissions(
                                    mTelecomManager,
                                    tm -> tm.getLocalVoicemailTimeout(invalidPhac));
                });
    }

    /**
     * Verifies that {@link TelecomManager#getLocalVoicemailTimeout(PhoneAccountHandle)} throws an
     * {@link IllegalArgumentException} if the passed {@link PhoneAccountHandle} does not exist.
     */
    @Test
    @ApiTest(apis = {"android.telecom.TelecomManager#getLocalVoicemailTimeout"})
    public void testGetLocalVoicemailTimeoutInvalid() throws Exception {
        assumeTrue("Skipped for devices with no FEATURE_TELECOM", mShouldTestTelecom);
        assumeTrue("Skipped for devices with no dialer role", mSupportsManagedCalls);

        PhoneAccountHandle invalidPhac =
                new PhoneAccountHandle(new ComponentName("foo", "bar"), "90210");

        assertThrows(
                "Expected IllegalArgumentException for invalid phone account.",
                IllegalArgumentException.class,
                () -> {
                    Duration duration =
                            invokeMethodWithShellPermissions(
                                    mTelecomManager,
                                    tm -> tm.getLocalVoicemailTimeout(invalidPhac));
                });
    }

    @Test
    @ApiTest(
            apis = {
                "android.telecom.TelecomManager#isLocalVoicemailEnabled",
                "android.telecom.TelecomManager#getLocalVoicemailTimeout",
                "android.telecom.TelecomManager#enableLocalVoicemail",
                "android.telecom.TelecomManager#disableLocalVoicemail"
            })
    public void testGetSetLocalVoicemailTimeout() throws Exception {
        assumeTrue("Skipped for devices with no FEATURE_TELECOM", mShouldTestTelecom);
        assumeTrue("Skipped for devices with no dialer role", mSupportsManagedCalls);

        AppControlWrapper managedApp = null;
        try {
            configureTestLocalVoicemailServiceAndVerify();
            managedApp = bindToApp(ManagedConnectionServiceApp);
            registerAcctAndVerify(managedApp, MANAGED_DEFAULT_ACCOUNT_1);

            Duration newDuration = Duration.ofSeconds(90);
            invokeMethodWithShellPermissionsNoReturn(
                    mTelecomManager,
                    tm ->
                            tm.enableLocalVoicemail(
                                    MANAGED_DEFAULT_ACCOUNT_1.getAccountHandle(), newDuration));

            Duration storedDuration =
                    invokeMethodWithShellPermissions(
                            mTelecomManager,
                            tm ->
                                    tm.getLocalVoicemailTimeout(
                                            MANAGED_DEFAULT_ACCOUNT_1.getAccountHandle()));

            assertEquals("Saved does not match expected.", newDuration, storedDuration);
        } finally {
            tearDownApp(managedApp);
            invokeMethodWithShellPermissionsNoReturn(
                    mTelecomManager,
                    tm -> tm.disableLocalVoicemail(MANAGED_DEFAULT_ACCOUNT_1.getAccountHandle()));
            resetTestLocalVoicemailService();
            unregisterAcctAndVerify(managedApp, MANAGED_DEFAULT_ACCOUNT_1);
        }
    }

    @Test
    @ApiTest(
            apis = {
                "android.telecom.TelecomManager#isLocalVoicemailEnabled",
                "android.telecom.TelecomManager#getLocalVoicemailTimeout",
                "android.telecom.TelecomManager#enableLocalVoicemail",
                "android.telecom.TelecomManager#disableLocalVoicemail"
            })
    public void testGetSetLocalVoicemailTimeoutBoundsCheck() throws Exception {
        assumeTrue("Skipped for devices with no FEATURE_TELECOM", mShouldTestTelecom);
        assumeTrue("Skipped for devices with no dialer role", mSupportsManagedCalls);

        AppControlWrapper managedApp = null;
        try {
            configureTestLocalVoicemailServiceAndVerify();
            managedApp = bindToApp(ManagedConnectionServiceApp);
            Bundle boundsBundle = new Bundle();
            boundsBundle.putLong(PhoneAccount.EXTRA_LOCAL_VOICEMAIL_MINIMUM_TIMEOUT_MILLIS, 1000L);
            boundsBundle.putLong(PhoneAccount.EXTRA_LOCAL_VOICEMAIL_MAXIMUM_TIMEOUT_MILLIS, 60000L);
            PhoneAccount accountWithBounds =
                    MANAGED_DEFAULT_ACCOUNT_1.toBuilder().setExtras(boundsBundle).build();
            registerAcctAndVerify(managedApp, accountWithBounds);

            assertThrows(
                    "Expected IllegalArgumentException for duration above limit.",
                    IllegalArgumentException.class,
                    () ->
                            invokeMethodWithShellPermissionsNoReturn(
                                    mTelecomManager,
                                    tm ->
                                            tm.enableLocalVoicemail(
                                                    MANAGED_DEFAULT_ACCOUNT_1.getAccountHandle(),
                                                    Duration.ofSeconds(90000))));

            assertThrows(
                    "Expected IllegalArgumentException for duration below limit.",
                    IllegalArgumentException.class,
                    () ->
                            invokeMethodWithShellPermissionsNoReturn(
                                    mTelecomManager,
                                    tm ->
                                            tm.enableLocalVoicemail(
                                                    MANAGED_DEFAULT_ACCOUNT_1.getAccountHandle(),
                                                    Duration.ofMillis(10))));
        } finally {
            tearDownApp(managedApp);
            invokeMethodWithShellPermissionsNoReturn(
                    mTelecomManager,
                    tm -> tm.disableLocalVoicemail(MANAGED_DEFAULT_ACCOUNT_1.getAccountHandle()));
            resetTestLocalVoicemailService();
            unregisterAcctAndVerify(managedApp, MANAGED_DEFAULT_ACCOUNT_1);
        }
    }

    /**
     * Verifies that a local voicemail service is able to start when a call ringing timeout is hit
     * and then the remote party disconnects the call.
     */
    @Test
    @ApiTest(apis = {"android.telecom.LocalVoicemailService#onVoicemailRequested"})
    public void testLocalVoicemailRemoteDisconnect() throws Exception {
        assumeTrue("Skipped for devices with no FEATURE_TELECOM", mShouldTestTelecom);
        assumeTrue("Skipped for devices with no dialer role", mSupportsManagedCalls);

        AppControlWrapper managedApp = null;
        try {
            managedApp = bindToApp(ManagedConnectionServiceApp);
            configureTestLocalVoicemailServiceAndVerify();

            // Start by assuming that the timeout is quite short; 1 sec.
            // This way we can ensure the local voicemail service picks up quickly without this test
            // becoming an absolute time vortex.
            invokeMethodWithShellPermissionsNoReturn(
                    mTelecomManager,
                    tm ->
                            tm.enableLocalVoicemail(
                                    MANAGED_DEFAULT_ACCOUNT_1.getAccountHandle(),
                                    Duration.ofSeconds(1)));

            String incomingCallId = addIncomingCallAndVerify(managedApp);

            // We'll wait up to 5 sec for the local vm service to get the call.
            Call.Details call =
                    CujLocalVoicemailService.getRequestedCalls()
                            .poll(STATE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            assertNotNull("Expected the CujLocalVoicemailService to receive the call.", call);
            assertEquals(incomingCallId, call.getId());

            // Disconnect the call as if the remote party dropped it.
            managedApp.setCallState(incomingCallId, Call.STATE_DISCONNECTED, true, new Bundle());

            verifyLocalVoicemailStopped(call);

            assertTrue(
                    "Expected unbind from the local voicemail service.",
                    CujLocalVoicemailService.getUnbindLatch()
                            .await(STATE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
        } finally {
            tearDownApp(managedApp);
            invokeMethodWithShellPermissionsNoReturn(
                    mTelecomManager,
                    tm -> tm.disableLocalVoicemail(MANAGED_DEFAULT_ACCOUNT_1.getAccountHandle()));
            resetTestLocalVoicemailService();
            unregisterAcctAndVerify(managedApp, MANAGED_DEFAULT_ACCOUNT_1);
        }
    }

    /**
     * Verifies that a local voicemail service is able to start when a call ringing timeout is hit
     * and then the local VM service disconnects the call.
     */
    @Test
    @ApiTest(apis = {"android.telecom.LocalVoicemailService#disconnectCall"})
    public void testLocalVoicemailServiceDisconnects() throws Exception {
        assumeTrue("Skipped for devices with no FEATURE_TELECOM", mShouldTestTelecom);
        assumeTrue("Skipped for devices with no dialer role", mSupportsManagedCalls);

        AppControlWrapper managedApp = null;
        try {
            managedApp = bindToApp(ManagedConnectionServiceApp);
            configureTestLocalVoicemailServiceAndVerify();

            // Start by assuming that the timeout is quite short; 1 sec.
            // This way we can ensure the local voicemail service picks up quickly without this test
            // becoming an absolute time vortex.
            invokeMethodWithShellPermissionsNoReturn(
                    mTelecomManager,
                    tm ->
                            tm.enableLocalVoicemail(
                                    MANAGED_DEFAULT_ACCOUNT_1.getAccountHandle(),
                                    Duration.ofSeconds(1)));

            String incomingCallId = addIncomingCallAndVerify(managedApp);

            // We'll wait up to 5 sec for the local vm service to get the call.
            Call.Details call =
                    CujLocalVoicemailService.getRequestedCalls()
                            .poll(STATE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            assertNotNull("Expected the CujLocalVoicemailService to receive the call.", call);
            assertEquals(incomingCallId, call.getId());

            // Drop the call from the local vm service
            CujLocalVoicemailService.disconnectCurrentCall();

            // Since the local VM service initiated the disconnect it won't get the onDisconnected
            // signal.

            // We can't validate disconnect directly since it isn't in an InCallService, but we can
            // confirm we unbound.
            assertTrue(
                    "Expected unbind from the local voicemail service.",
                    CujLocalVoicemailService.getUnbindLatch()
                            .await(STATE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
        } finally {
            tearDownApp(managedApp);
            invokeMethodWithShellPermissionsNoReturn(
                    mTelecomManager,
                    tm -> tm.disableLocalVoicemail(MANAGED_DEFAULT_ACCOUNT_1.getAccountHandle()));
            resetTestLocalVoicemailService();
            unregisterAcctAndVerify(managedApp, MANAGED_DEFAULT_ACCOUNT_1);
        }
    }

    /**
     * Verifies that a call undergoing local voicemail will continue processing while an incoming
     * call rings, but will disconnect when the ringing call is answered.
     */
    @Test
    @ApiTest(
            apis = {
                "android.telecom.LocalVoicemailService#onVoicemailRequested",
                "android.telecom.LocalVoicemailService#onVoicemailStopped"
            })
    public void testIncomingCallDuringLocalVoicemailCall() throws Exception {
        assumeTrue("Skipped for devices with no FEATURE_TELECOM", mShouldTestTelecom);
        assumeTrue("Skipped for devices with no dialer role", mSupportsManagedCalls);

        AppControlWrapper managedApp = null;
        try {
            managedApp = bindToApp(ManagedConnectionServiceApp);
            configureTestLocalVoicemailServiceAndVerify();

            // Start by assuming that the timeout is quite short; 1 sec.
            // This way we can ensure the local voicemail service picks up quickly without this test
            // becoming an absolute time vortex.
            invokeMethodWithShellPermissionsNoReturn(
                    mTelecomManager,
                    tm ->
                            tm.enableLocalVoicemail(
                                    MANAGED_DEFAULT_ACCOUNT_1.getAccountHandle(),
                                    Duration.ofSeconds(1)));

            String incomingCallId = addIncomingCallAndVerify(managedApp);

            // We'll wait up to 5 sec for the local vm service to get the call.
            Call.Details call =
                    CujLocalVoicemailService.getRequestedCalls()
                            .poll(STATE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            assertNotNull("Expected the CujLocalVoicemailService to receive the call.", call);
            assertEquals(incomingCallId, call.getId());

            // Add a second incoming call.
            String incomingCall2Id = addIncomingCallAndVerify(managedApp);

            // There is no direct way to observe that a call is in local vm state, so we will check
            // the audio mode and make sure it is still `MODE_CALL_REDIRECT`.
            assertEquals(
                    "Local voicemail should still be in progress when a ringing call added.",
                    AudioManager.MODE_CALL_REDIRECT,
                    mAudioManager.getMode());

            // Answer the second incoming call.
            managedApp.setCallState(incomingCall2Id, Call.STATE_ACTIVE, true, new Bundle());

            // Make sure the original call got disconnected.
            verifyLocalVoicemailStopped(call);

            assertTrue(
                    "Expected unbind from the local voicemail service.",
                    CujLocalVoicemailService.getUnbindLatch()
                            .await(STATE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
        } finally {
            tearDownApp(managedApp);
            invokeMethodWithShellPermissionsNoReturn(
                    mTelecomManager,
                    tm -> tm.disableLocalVoicemail(MANAGED_DEFAULT_ACCOUNT_1.getAccountHandle()));
            resetTestLocalVoicemailService();
            unregisterAcctAndVerify(managedApp, MANAGED_DEFAULT_ACCOUNT_1);
        }
    }

    /**
     * Verifies that a call undergoing local voicemail will get disconnected if the user places a
     * managed call.
     */
    @Test
    @ApiTest(
            apis = {
                "android.telecom.LocalVoicemailService#onVoicemailRequested",
                "android.telecom.LocalVoicemailService#onVoicemailStopped"
            })
    public void testOutgoingManagedCallDuringLocalVoicemailCall() throws Exception {
        assumeTrue("Skipped for devices with no FEATURE_TELECOM", mShouldTestTelecom);
        assumeTrue("Skipped for devices with no dialer role", mSupportsManagedCalls);

        AppControlWrapper managedApp = null;
        try {
            managedApp = bindToApp(ManagedConnectionServiceApp);
            configureTestLocalVoicemailServiceAndVerify();

            // Start by assuming that the timeout is quite short; 1 sec.
            // This way we can ensure the local voicemail service picks up quickly without this test
            // becoming an absolute time vortex.
            invokeMethodWithShellPermissionsNoReturn(
                    mTelecomManager,
                    tm ->
                            tm.enableLocalVoicemail(
                                    MANAGED_DEFAULT_ACCOUNT_1.getAccountHandle(),
                                    Duration.ofSeconds(1)));

            String initialCallId = addIncomingCallAndVerify(managedApp);

            // We'll wait up to 5 sec for the local vm service to get the call.
            Call.Details call =
                    CujLocalVoicemailService.getRequestedCalls()
                            .poll(STATE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            assertNotNull("Expected the CujLocalVoicemailService to receive the call.", call);
            assertEquals(initialCallId, call.getId());

            // Place an outgoing call.
            String outgoingCallId = addOutgoingCallAndVerify(managedApp);

            // Make sure the original call got disconnected.
            verifyLocalVoicemailStopped(call);

            assertTrue(
                    "Expected unbind from the local voicemail service.",
                    CujLocalVoicemailService.getUnbindLatch()
                            .await(STATE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
        } finally {
            tearDownApp(managedApp);
            invokeMethodWithShellPermissionsNoReturn(
                    mTelecomManager,
                    tm -> tm.disableLocalVoicemail(MANAGED_DEFAULT_ACCOUNT_1.getAccountHandle()));
            resetTestLocalVoicemailService();
            unregisterAcctAndVerify(managedApp, MANAGED_DEFAULT_ACCOUNT_1);
        }
    }

    /** Configures the test local voicemail implementation in the CTS suite. */
    private void configureTestLocalVoicemailServiceAndVerify() throws Exception {
        String result =
                executeShellCommand(
                        InstrumentationRegistry.getInstrumentation(),
                        COMMAND_SET_LOCAL_VOICEMAIL_PACKAGE + "android.telecom.cts.cuj");
        assertTrue(
                "Expected successful shell command override of local VM",
                result.contains("Success"));
        boolean isEnabled =
                invokeMethodWithShellPermissions(
                        mTelecomManager, tm -> tm.isLocalVoicemailSupported());
        assertTrue("Expected local voicemail service to be enabled.", isEnabled);
    }

    /** Disables the test local voicemail service implementation in the CTS suite. */
    private void resetTestLocalVoicemailService() throws Exception {
        String result =
                executeShellCommand(
                        InstrumentationRegistry.getInstrumentation(),
                        COMMAND_SET_LOCAL_VOICEMAIL_PACKAGE + "default");
        assertTrue(
                "Expected successful shell command reset of local VM", result.contains("Success"));
    }

    private void registerAcctAndVerify(AppControlWrapper wrapper, PhoneAccount phoneAccount)
            throws Exception {
        wrapper.registerCustomPhoneAccount(phoneAccount);
        assertTrue(isPhoneAccountRegistered(phoneAccount.getAccountHandle()));
    }

    private void unregisterAcctAndVerify(AppControlWrapper wrapper, PhoneAccount phoneAccount)
            throws Exception {
        wrapper.unregisterPhoneAccountWithHandle(phoneAccount.getAccountHandle());
        assertFalse(isPhoneAccountRegistered(phoneAccount.getAccountHandle()));
    }

    /**
     * Verifies that a specified call has stopped local voicemail processing by waiting for the
     * local voicemail service to get confirmation that local vm stopped for that call.
     *
     * @param call the call.
     * @throws InterruptedException thrown is async operation is interrupted (not expected).
     */
    private static void verifyLocalVoicemailStopped(Call.Details call) throws InterruptedException {
        // Wait for signal from the LocalVoicemailService that local VM stopped.
        Call.Details stoppedCall =
                CujLocalVoicemailService.getStoppedCalls()
                        .poll(STATE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        assertNotNull("Expected a call reported by onVoicemailStopped.", stoppedCall);
        assertEquals(
                "Expected the call reported by onVoicemailStopped be the same as one from "
                        + "onVoicemailRequested.",
                call.getId(),
                stoppedCall.getId());
    }
}
