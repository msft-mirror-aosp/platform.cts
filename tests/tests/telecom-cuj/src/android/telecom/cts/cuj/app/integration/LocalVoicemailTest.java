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

    @Before
    public void setUp() throws Exception {
        super.setUp();

        mTelecomManager = mContext.getSystemService(TelecomManager.class);
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
                "Expected IllegalArgumentException for duration above limit.",
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
                "android.telecom.TelecomManager#getLocalVoicemailTimeout",
                "android.telecom.TelecomManager#setLocalVoicemailTimeout"
            })
    public void testGetSetLocalVoicemailTimeout() throws Exception {
        assumeTrue("Skipped for devices with no FEATURE_TELECOM", mShouldTestTelecom);
        assumeTrue("Skipped for devices with no dialer role", mSupportsManagedCalls);

        AppControlWrapper managedApp = null;
        Duration originalDuration = TelecomManager.LOCAL_VOICEMAIL_DISABLED;
        try {
            managedApp = bindToApp(ManagedConnectionServiceApp);
            registerAcctAndVerify(managedApp, MANAGED_DEFAULT_ACCOUNT_1);

            originalDuration =
                    invokeMethodWithShellPermissions(
                            mTelecomManager,
                            tm ->
                                    tm.getLocalVoicemailTimeout(
                                            MANAGED_DEFAULT_ACCOUNT_1.getAccountHandle()));

            Duration newDuration = Duration.ofSeconds(90);
            invokeMethodWithShellPermissionsNoReturn(
                    mTelecomManager,
                    tm ->
                            tm.setLocalVoicemailTimeout(
                                    MANAGED_DEFAULT_ACCOUNT_1.getAccountHandle(), newDuration));

            Duration storedDuration =
                    invokeMethodWithShellPermissions(
                            mTelecomManager,
                            tm ->
                                    tm.getLocalVoicemailTimeout(
                                            MANAGED_DEFAULT_ACCOUNT_1.getAccountHandle()));

            assertEquals("Saved does not match expected.", newDuration, storedDuration);
        } finally {
            // Lambdas need to be final.
            final Duration toReset = originalDuration;
            invokeMethodWithShellPermissionsNoReturn(
                    mTelecomManager,
                    tm ->
                            tm.setLocalVoicemailTimeout(
                                    MANAGED_DEFAULT_ACCOUNT_1.getAccountHandle(), toReset));
            unregisterAcctAndVerify(managedApp, MANAGED_DEFAULT_ACCOUNT_1);
            tearDownApp(managedApp);
        }
    }

    @Test
    @ApiTest(
            apis = {
                "android.telecom.TelecomManager#getLocalVoicemailTimeout",
                "android.telecom.TelecomManager#setLocalVoicemailTimeout"
            })
    public void testGetSetLocalVoicemailTimeoutBoundsCheck() throws Exception {
        assumeTrue("Skipped for devices with no FEATURE_TELECOM", mShouldTestTelecom);
        assumeTrue("Skipped for devices with no dialer role", mSupportsManagedCalls);

        AppControlWrapper managedApp = null;
        Duration originalDuration = TelecomManager.LOCAL_VOICEMAIL_DISABLED;
        try {
            managedApp = bindToApp(ManagedConnectionServiceApp);
            Bundle boundsBundle = new Bundle();
            boundsBundle.putLong(PhoneAccount.EXTRA_LOCAL_VOICEMAIL_MINIMUM_TIMEOUT_MILLIS, 1000L);
            boundsBundle.putLong(PhoneAccount.EXTRA_LOCAL_VOICEMAIL_MAXIMUM_TIMEOUT_MILLIS, 60000L);
            PhoneAccount accountWithBounds =
                    MANAGED_DEFAULT_ACCOUNT_1.toBuilder().setExtras(boundsBundle).build();
            registerAcctAndVerify(managedApp, accountWithBounds);

            originalDuration =
                    invokeMethodWithShellPermissions(
                            mTelecomManager,
                            tm ->
                                    tm.getLocalVoicemailTimeout(
                                            MANAGED_DEFAULT_ACCOUNT_1.getAccountHandle()));

            assertThrows(
                    "Expected IllegalArgumentException for duration above limit.",
                    IllegalArgumentException.class,
                    () ->
                            invokeMethodWithShellPermissionsNoReturn(
                                    mTelecomManager,
                                    tm ->
                                            tm.setLocalVoicemailTimeout(
                                                    MANAGED_DEFAULT_ACCOUNT_1.getAccountHandle(),
                                                    Duration.ofSeconds(90000))));

            assertThrows(
                    "Expected IllegalArgumentException for duration below limit.",
                    IllegalArgumentException.class,
                    () ->
                            invokeMethodWithShellPermissionsNoReturn(
                                    mTelecomManager,
                                    tm ->
                                            tm.setLocalVoicemailTimeout(
                                                    MANAGED_DEFAULT_ACCOUNT_1.getAccountHandle(),
                                                    Duration.ofMillis(10))));
        } finally {
            // Lambdas need to be final.
            final Duration toReset = originalDuration;
            invokeMethodWithShellPermissionsNoReturn(
                    mTelecomManager,
                    tm ->
                            tm.setLocalVoicemailTimeout(
                                    MANAGED_DEFAULT_ACCOUNT_1.getAccountHandle(), toReset));
            unregisterAcctAndVerify(managedApp, MANAGED_DEFAULT_ACCOUNT_1);
            tearDownApp(managedApp);
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
        Duration originalDuration = TelecomManager.LOCAL_VOICEMAIL_DISABLED;
        try {
            managedApp = bindToApp(ManagedConnectionServiceApp);
            configureTestLocalVoicemailServiceAndVerify();

            originalDuration =
                    invokeMethodWithShellPermissions(
                            mTelecomManager,
                            tm ->
                                    tm.getLocalVoicemailTimeout(
                                            MANAGED_DEFAULT_ACCOUNT_1.getAccountHandle()));

            // Start by assuming that the timeout is quite short; 1 sec.
            // This way we can ensure the local voicemail service picks up quickly without this test
            // becoming an absolute time vortex.
            invokeMethodWithShellPermissionsNoReturn(
                    mTelecomManager,
                    tm ->
                            tm.setLocalVoicemailTimeout(
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
            managedApp.setCallState(incomingCallId, Call.STATE_DISCONNECTED, true, null);

            assertTrue(
                    "Expected unbind from the local voicemail service.",
                    CujLocalVoicemailService.getUnbindLatch()
                            .await(STATE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
        } finally {
            resetTestLocalVoicemailService();
            final Duration restoredDuration = originalDuration;
            invokeMethodWithShellPermissionsNoReturn(
                    mTelecomManager,
                    tm ->
                            tm.setLocalVoicemailTimeout(
                                    MANAGED_DEFAULT_ACCOUNT_1.getAccountHandle(),
                                    restoredDuration));
            unregisterAcctAndVerify(managedApp, MANAGED_DEFAULT_ACCOUNT_1);
            tearDownApp(managedApp);
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
        Duration originalDuration = TelecomManager.LOCAL_VOICEMAIL_DISABLED;
        try {
            managedApp = bindToApp(ManagedConnectionServiceApp);
            configureTestLocalVoicemailServiceAndVerify();

            originalDuration =
                    invokeMethodWithShellPermissions(
                            mTelecomManager,
                            tm ->
                                    tm.getLocalVoicemailTimeout(
                                            MANAGED_DEFAULT_ACCOUNT_1.getAccountHandle()));

            // Start by assuming that the timeout is quite short; 1 sec.
            // This way we can ensure the local voicemail service picks up quickly without this test
            // becoming an absolute time vortex.
            invokeMethodWithShellPermissionsNoReturn(
                    mTelecomManager,
                    tm ->
                            tm.setLocalVoicemailTimeout(
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

            // We can't validate disconnect directly since it isn't in an InCallService, but we can
            // confirm we unbound.
            assertTrue(
                    "Expected unbind from the local voicemail service.",
                    CujLocalVoicemailService.getUnbindLatch()
                            .await(STATE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
        } finally {
            resetTestLocalVoicemailService();
            final Duration restoredDuration = originalDuration;
            invokeMethodWithShellPermissionsNoReturn(
                    mTelecomManager,
                    tm ->
                            tm.setLocalVoicemailTimeout(
                                    MANAGED_DEFAULT_ACCOUNT_1.getAccountHandle(),
                                    restoredDuration));
            unregisterAcctAndVerify(managedApp, MANAGED_DEFAULT_ACCOUNT_1);
            tearDownApp(managedApp);
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
}
