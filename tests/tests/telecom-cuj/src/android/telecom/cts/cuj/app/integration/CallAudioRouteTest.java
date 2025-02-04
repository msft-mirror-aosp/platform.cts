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

import static android.telecom.Call.STATE_ACTIVE;
import static android.telecom.Call.STATE_DISCONNECTED;
import static android.telecom.cts.apps.ShellCommandExecutor.COMMAND_WAIT_FOR_AUDIO_ACTIVE;
import static android.telecom.cts.apps.ShellCommandExecutor.COMMAND_WAIT_FOR_AUDIO_OPS_COMPLETE;
import static android.telecom.cts.apps.ShellCommandExecutor.executeShellCommand;
import static android.telecom.cts.apps.TelecomTestApp.ConnectionServiceVoipAppClone;
import static android.telecom.cts.apps.TelecomTestApp.ConnectionServiceVoipAppMain;
import static android.telecom.cts.apps.TelecomTestApp.ManagedConnectionServiceApp;
import static android.telecom.cts.apps.TelecomTestApp.TransactionalVoipAppClone;
import static android.telecom.cts.apps.TelecomTestApp.TransactionalVoipAppMain;

import android.os.Bundle;
import android.os.OutcomeReceiver;
import android.telecom.CallAttributes;
import android.telecom.CallControlCallback;
import android.telecom.CallEndpoint;
import android.telecom.CallEventCallback;
import android.telecom.PhoneAccountHandle;
import android.telecom.cts.apps.AppControlWrapper;
import android.telecom.cts.cuj.BaseAppVerifier;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.List;
import java.util.concurrent.Executor;

/** This test class should test common calling scenarios that involve only a single application */
@RunWith(JUnit4.class)
public class CallAudioRouteTest extends BaseAppVerifier {
    @After
    public void waitOnHandlers() throws Exception {
        executeShellCommand(
                InstrumentationRegistry.getInstrumentation(), COMMAND_WAIT_FOR_AUDIO_OPS_COMPLETE);
    }

    /**
     * Test the scenario where a client application requests to switch the current {@link
     * android.telecom.CallEndpoint}
     *
     * <h3>Test Steps: </h3>
     *
     * <ul>
     *   1. create a managed call that is backed by a {@link android.telecom.ConnectionService } via
     *   {@link android.telecom.TelecomManager#addNewIncomingCall(PhoneAccountHandle, Bundle)}
     *   <p>2. collect the current {@link CallEndpoint} via {@link
     *   android.telecom.CallEventCallback#onCallEndpointChanged(CallEndpoint)}
     *   <p>3. collect the available {@link CallEndpoint}s via {@link
     *   android.telecom.CallEventCallback#onAvailableCallEndpointsChanged(List)}
     *   <p>4. find another endpoint that is not the current endpoint and request an audio endpoint
     *   switch via {@link android.telecom.CallControl#requestCallEndpointChange(CallEndpoint,
     *   Executor, OutcomeReceiver)}
     *   <p>
     * </ul>
     *
     * Assert the current {@link CallEndpoint} is switched successfully
     */
    @Test
    public void testBasicAudioSwitchTest_ManagedConnectionServiceApp() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        executeShellCommand(
                InstrumentationRegistry.getInstrumentation(), COMMAND_WAIT_FOR_AUDIO_OPS_COMPLETE);
        AppControlWrapper managedApp = null;
        try {
            managedApp = bindToApp(ManagedConnectionServiceApp);
            verifySwitchEndpoints(managedApp);
        } finally {
            tearDownApp(managedApp);
        }
    }

    /**
     * Test the scenario where a client application requests to switch the current {@link
     * android.telecom.CallEndpoint}
     *
     * <h3>Test Steps: </h3>
     *
     * <ul>
     *   1. create a self-managed call that is backed by a {@link android.telecom.ConnectionService}
     *   via {@link android.telecom.TelecomManager#addNewIncomingCall(PhoneAccountHandle, Bundle)}
     *   <p>2. collect the current {@link CallEndpoint} via {@link
     *   android.telecom.CallEventCallback#onCallEndpointChanged(CallEndpoint)}
     *   <p>3. collect the available {@link CallEndpoint}s via {@link
     *   android.telecom.CallEventCallback#onAvailableCallEndpointsChanged(List)}
     *   <p>4. find another endpoint that is not the current endpoint and request an audio endpoint
     *   switch via {@link android.telecom.CallControl#requestCallEndpointChange(CallEndpoint,
     *   Executor, OutcomeReceiver)}
     *   <p>
     * </ul>
     *
     * Assert the current {@link CallEndpoint} is switched successfully
     */
    @Test
    public void testBasicAudioSwitchTest_ConnectionServiceVoipAppMain() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        executeShellCommand(
                InstrumentationRegistry.getInstrumentation(), COMMAND_WAIT_FOR_AUDIO_OPS_COMPLETE);
        AppControlWrapper voipCsApp = null;
        try {
            voipCsApp = bindToApp(ConnectionServiceVoipAppMain);
            verifySwitchEndpoints(voipCsApp);
        } finally {
            tearDownApp(voipCsApp);
        }
    }

    /**
     * Test the scenario where a client application requests to switch the current {@link
     * android.telecom.CallEndpoint}
     *
     * <h3>Test Steps: </h3>
     *
     * <ul>
     *   1. create a self-managed call that is backed by a {@link android.telecom.ConnectionService}
     *   via {@link android.telecom.TelecomManager#addNewIncomingCall(PhoneAccountHandle, Bundle)}
     *   <p>2. collect the current {@link CallEndpoint} via {@link
     *   android.telecom.CallEventCallback#onCallEndpointChanged(CallEndpoint)}
     *   <p>3. collect the available {@link CallEndpoint}s via {@link
     *   android.telecom.CallEventCallback#onAvailableCallEndpointsChanged(List)}
     *   <p>4. find another endpoint that is not the current endpoint and request an audio endpoint
     *   switch via {@link android.telecom.CallControl#requestCallEndpointChange(CallEndpoint,
     *   Executor, OutcomeReceiver)}
     *   <p>
     * </ul>
     *
     * Assert the current {@link CallEndpoint} is switched successfully
     */
    @Test
    public void testBasicAudioSwitchTest_ConnectionServiceVoipAppClone() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        executeShellCommand(
                InstrumentationRegistry.getInstrumentation(), COMMAND_WAIT_FOR_AUDIO_OPS_COMPLETE);
        AppControlWrapper voipCsApp = null;

        try {
            voipCsApp = bindToApp(ConnectionServiceVoipAppClone);
            verifySwitchEndpoints(voipCsApp);
        } finally {
            tearDownApp(voipCsApp);
        }
    }

    /**
     * Test the scenario where a client application requests to switch the current {@link
     * android.telecom.CallEndpoint}
     *
     * <h3>Test Steps: </h3>
     *
     * <ul>
     *   1. create a self-managed call that is backed by a {@link android.telecom.ConnectionService
     *   } via {@link android.telecom.TelecomManager#addCall(CallAttributes, Executor,
     *   OutcomeReceiver, CallControlCallback, CallEventCallback)}
     *   <p>2. collect the current {@link CallEndpoint} via {@link
     *   android.telecom.CallEventCallback#onCallEndpointChanged(CallEndpoint)}
     *   <p>3. collect the available {@link CallEndpoint}s via {@link
     *   android.telecom.CallEventCallback#onAvailableCallEndpointsChanged(List)}
     *   <p>4. find another endpoint that is not the current endpoint and request an audio endpoint
     *   switch via {@link android.telecom.CallControl#requestCallEndpointChange(CallEndpoint,
     *   Executor, OutcomeReceiver)}
     *   <p>
     * </ul>
     *
     * Assert the current {@link CallEndpoint} is switched successfully
     */
    @Test
    public void testBasicAudioSwitchTest_TransactionalVoipAppMain() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        executeShellCommand(
                InstrumentationRegistry.getInstrumentation(), COMMAND_WAIT_FOR_AUDIO_OPS_COMPLETE);
        AppControlWrapper transactionalApp = null;
        try {
            transactionalApp = bindToApp(TransactionalVoipAppMain);
            verifySwitchEndpoints(transactionalApp);
        } finally {
            tearDownApp(transactionalApp);
        }
    }

    /**
     * Test the scenario where a client application requests to switch the current {@link
     * android.telecom.CallEndpoint}
     *
     * <h3>Test Steps: </h3>
     *
     * <ul>
     *   1. create a self-managed call that is backed by a {@link android.telecom.ConnectionService
     *   } via {@link android.telecom.TelecomManager#addCall(CallAttributes, Executor,
     *   OutcomeReceiver, CallControlCallback, CallEventCallback)}
     *   <p>2. collect the current {@link CallEndpoint} via {@link
     *   android.telecom.CallEventCallback#onCallEndpointChanged(CallEndpoint)}
     *   <p>3. collect the available {@link CallEndpoint}s via {@link
     *   android.telecom.CallEventCallback#onAvailableCallEndpointsChanged(List)}
     *   <p>4. find another endpoint that is not the current endpoint and request an audio endpoint
     *   switch via {@link android.telecom.CallControl#requestCallEndpointChange(CallEndpoint,
     *   Executor, OutcomeReceiver)}
     *   <p>
     * </ul>
     *
     * Assert the current {@link CallEndpoint} is switched successfully
     */
    @Test
    public void testBasicAudioSwitchTest_TransactionalVoipAppClone() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        executeShellCommand(
                InstrumentationRegistry.getInstrumentation(), COMMAND_WAIT_FOR_AUDIO_OPS_COMPLETE);
        AppControlWrapper transactionalApp = null;
        try {
            transactionalApp = bindToApp(TransactionalVoipAppClone);
            verifySwitchEndpoints(transactionalApp);
        } finally {
            tearDownApp(transactionalApp);
        }
    }

    private void verifySwitchEndpoints(AppControlWrapper appControlWrapper) throws Exception {
        String mo = addOutgoingCallAndVerify(appControlWrapper);
        setCallStateAndVerify(appControlWrapper, mo, STATE_ACTIVE);
        executeShellCommand(
                InstrumentationRegistry.getInstrumentation(), COMMAND_WAIT_FOR_AUDIO_ACTIVE);
        switchToAnotherCallEndpoint(appControlWrapper, mo);
        setCallStateAndVerify(appControlWrapper, mo, STATE_DISCONNECTED);
    }
}
