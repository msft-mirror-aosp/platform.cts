/*
 * Copyright (C) 2026 The Android Open Source Project
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

package android.telephony.satellite.cts;

import static android.telephony.satellite.SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE;
import static android.telephony.satellite.SatelliteManager.SATELLITE_RESULT_MODEM_ERROR;
import static android.telephony.satellite.SatelliteManager.SATELLITE_RESULT_NO_RESOURCES;
import static android.telephony.satellite.SatelliteManager.SATELLITE_RESULT_REQUEST_ABORTED;
import static android.telephony.satellite.SatelliteManager.SATELLITE_RESULT_REQUEST_NOT_SUPPORTED;
import static android.telephony.satellite.SatelliteManager.SATELLITE_RESULT_SUCCESS;

import static com.android.internal.telephony.satellite.SatelliteController.TIMEOUT_TYPE_DEMO_POINTING_ALIGNED_DURATION_MILLIS;
import static com.android.internal.telephony.satellite.SatelliteController.TIMEOUT_TYPE_DEMO_POINTING_NOT_ALIGNED_DURATION_MILLIS;
import static com.android.internal.telephony.satellite.SatelliteController.TIMEOUT_TYPE_WAIT_FOR_SATELLITE_ENABLING_RESPONSE;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.annotation.NonNull;
import android.telephony.SubscriptionManager;
import android.telephony.satellite.NtnSignalStrength;
import android.telephony.satellite.SatelliteDatagram;
import android.telephony.satellite.SatelliteManager;
import android.telephony.satellite.stub.NTRadioTechnology;
import android.telephony.satellite.stub.SatelliteModemState;
import android.telephony.satellite.stub.SatelliteResult;
import android.util.Pair;

import com.android.internal.telephony.satellite.DatagramController;

import org.junit.BeforeClass;
import org.junit.Test;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class SatelliteManagerForNbIotNtnTest extends SatelliteManagerOnMockServiceTestBase {
    @BeforeClass
    public static void beforeAllTests() throws Exception {
        beforeAllSatelliteManagerTestsOnMockService(new int[] {NTRadioTechnology.NB_IOT_NTN});
    }

    @Test
    public void testSatelliteModemStateChangedForNbIot() {
        try {
            grantSatellitePermission();
            assertTrue(isSatelliteProvisioned());

            SatelliteModemStateCallbackTest callback = new SatelliteModemStateCallbackTest();
            boolean originalEnabledState = isSatelliteEnabled();
            boolean registerCallback = false;
            if (originalEnabledState) {
                registerCallback = true;

                long registerResult =
                        sSatelliteManager.registerForModemStateChanged(
                                getContext().getMainExecutor(), callback);
                assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerResult);
                assertTrue(callback.waitUntilResult(1));

                requestSatelliteEnabled(false);

                assertTrue(callback.waitUntilModemOff());
                assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback.modemState);
                assertFalse(isSatelliteEnabled());
                callback.clearModemStates();
            }
            if (!registerCallback) {
                long registerResult =
                        sSatelliteManager.registerForModemStateChanged(
                                getContext().getMainExecutor(), callback);
                assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerResult);
                assertTrue(callback.waitUntilResult(1));
                assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback.modemState);
            }

            assertTrue(sMockSatelliteServiceManager.connectSatelliteGatewayService());
            sMockSatelliteServiceManager.clearMockPointingUiActivityStatusChanges();
            requestSatelliteEnabled(true);
            assertTrue(callback.waitUntilResult(2));
            assertEquals(2, callback.getTotalCountOfModemStates());
            assertEquals(
                    SatelliteManager.SATELLITE_MODEM_STATE_ENABLING_SATELLITE,
                    callback.getModemState(0));
            assertEquals(
                    SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED,
                    callback.getModemState(1));
            assertTrue(isSatelliteEnabled());
            assertTrue(
                    sMockSatelliteServiceManager.waitForRemoteSatelliteGatewayServiceConnected(1));

            callback.clearModemStates();
            requestSatelliteEnabled(false);
            assertTrue(callback.waitUntilResult(2));
            assertEquals(2, callback.getTotalCountOfModemStates());
            assertEquals(
                    SatelliteManager.SATELLITE_MODEM_STATE_DISABLING_SATELLITE,
                    callback.getModemState(0));
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback.getModemState(1));
            assertFalse(isSatelliteEnabled());

            assertTrue(
                    sMockSatelliteServiceManager.setSatelliteListeningTimeoutDuration(
                            TEST_SATELLITE_LISTENING_TIMEOUT_MILLIS));

            // Verify state transitions: OFF -> ENABLING_SATELLITE -> NOT_CONNECTED -> IDLE
            callback.clearModemStates();
            requestSatelliteEnabled(true);
            assertTrue(callback.waitUntilResult(3));
            assertTrue(isSatelliteEnabled());
            assertEquals(
                    SatelliteManager.SATELLITE_MODEM_STATE_ENABLING_SATELLITE,
                    callback.getModemState(0));
            assertEquals(
                    SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED,
                    callback.getModemState(1));
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_IDLE, callback.getModemState(2));

            callback.clearModemStates();
            requestSatelliteEnabled(false);
            assertTrue(callback.waitUntilResult(2));
            assertEquals(2, callback.getTotalCountOfModemStates());
            assertEquals(
                    SatelliteManager.SATELLITE_MODEM_STATE_DISABLING_SATELLITE,
                    callback.getModemState(0));
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback.getModemState(1));
            assertFalse(isSatelliteEnabled());

            assertTrue(
                    sMockSatelliteServiceManager.setSatelliteListeningTimeoutDuration(
                            TEST_SATELLITE_LISTENING_FOREVER_TIMEOUT_MILLIS));

            callback.clearModemStates();
            requestSatelliteEnabled(true);
            assertTrue(callback.waitUntilResult(2));
            assertEquals(2, callback.getTotalCountOfModemStates());
            assertEquals(
                    SatelliteManager.SATELLITE_MODEM_STATE_ENABLING_SATELLITE,
                    callback.getModemState(0));
            assertEquals(
                    SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED,
                    callback.getModemState(1));
            assertTrue(isSatelliteEnabled());

            assertTrue(
                    sMockSatelliteServiceManager.setSatelliteListeningTimeoutDuration(
                            TEST_SATELLITE_LISTENING_TIMEOUT_MILLIS));

            // Verify state transitions when sending: NOT_CONNECTED -> CONNECTED -> TRANSFERRING
            // -> CONNECTED -> IDLE
            sMockSatelliteServiceManager.clearListeningEnabledList();
            callback.clearModemStates();
            sendDatagramWithoutResponse();
            verifyNbIotStateTransitionsWithSendingOnConnected(callback, true);

            // Verify state transitions when receiving: IDLE -> NOT_CONNECTED -> CONNECTED
            // -> TRANSFERRING -> CONNECTED -> IDLE
            verifyNbIotStateTransitionsWithReceivingOnIdle(callback, true);

            // TODO (b/399426859): Re-enable this test once the bug is fixed.
            // Verify no state transition on IDLE state
            // verifyNbIotStateTransitionsWithTransferringFailureOnIdle(callback);

            // Verify state transition: IDLE -> NOT_CONNECTED -> POWER_OFF
            verifyNbIotStateTransitionsWithSendingAborted(callback);

            // Verify state transitions: POWER_OFF -> NOT_CONNECTED
            callback.clearModemStates();
            requestSatelliteEnabled(true);
            assertTrue(callback.waitUntilResult(2));
            assertEquals(2, callback.getTotalCountOfModemStates());
            assertEquals(
                    SatelliteManager.SATELLITE_MODEM_STATE_ENABLING_SATELLITE,
                    callback.getModemState(0));
            assertEquals(
                    SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED,
                    callback.getModemState(1));
            assertTrue(isSatelliteEnabled());

            assertTrue(
                    sMockSatelliteServiceManager.setSatelliteListeningTimeoutDuration(
                            TEST_SATELLITE_LISTENING_FOREVER_TIMEOUT_MILLIS));

            // Verify state transitions when sending: NOT_CONNECTED -> CONNECTED -> TRANSFERRING
            // -> CONNECTED
            sMockSatelliteServiceManager.clearListeningEnabledList();
            callback.clearModemStates();
            sendDatagramWithoutResponse();
            verifyNbIotStateTransitionsWithSendingOnConnected(callback, false);

            // Verify state transitions: CONNECTED -> POWER_OFF
            callback.clearModemStates();
            requestSatelliteEnabled(false);
            assertTrue(callback.waitUntilResult(2));
            assertEquals(2, callback.getTotalCountOfModemStates());
            assertEquals(
                    SatelliteManager.SATELLITE_MODEM_STATE_DISABLING_SATELLITE,
                    callback.getModemState(0));
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback.getModemState(1));
            assertFalse(isSatelliteEnabled());
            assertTrue(
                    sMockSatelliteServiceManager.waitForRemoteSatelliteGatewayServiceDisconnected(
                            1));

            // Verify state transitions: POWER_OFF -> NOT_CONNECTED
            callback.clearModemStates();
            requestSatelliteEnabled(true);
            assertTrue(callback.waitUntilResult(2));
            assertEquals(2, callback.getTotalCountOfModemStates());
            assertEquals(
                    SatelliteManager.SATELLITE_MODEM_STATE_ENABLING_SATELLITE,
                    callback.getModemState(0));
            assertEquals(
                    SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED,
                    callback.getModemState(1));
            assertTrue(isSatelliteEnabled());

            // Move to CONNECTED state
            callback.clearModemStates();
            sMockSatelliteServiceManager.sendOnSatelliteModemStateChanged(
                    SatelliteManager.SATELLITE_MODEM_STATE_CONNECTED);
            assertTrue(callback.waitUntilResult(1));
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_CONNECTED, callback.modemState);

            // Verify state transitions: CONNECTED -> TRANSFERRING -> CONNECTED
            verifyNbIotStateTransitionsWithReceivingOnConnected(callback);

            sSatelliteManager.unregisterForModemStateChanged(callback);
            assertTrue(sMockSatelliteServiceManager.setSatelliteListeningTimeoutDuration(0));
            assertTrue(sMockSatelliteServiceManager.restoreSatelliteGatewayServicePackageName());
        } finally {
            revokeSatellitePermission();
        }
    }

    @Test
    public void testRequestSatelliteEnabled_OffToDemoToP2p_SuccessfulResponse() {
        /*
         * Test scenario:
         * 1) Enable request with demo mode
         * 2) Satellite should move to ENABLING state
         * 3) Enable request with P2P mode
         * 4) Successful response from modem for the first enable request
         * 5) Satellite should move to NOT_CONNECTED state and in demo mode
         * 6) Successful response from modem for the second enable request
         * 7) Satellite should stay at NOT_CONNECTED state and in P2P mode
         */
        grantSatellitePermission();
        assertTrue(isSatelliteProvisioned());

        logd("testRequestSatelliteEnabled_OffToDemoToP2p_SuccessfulResponse: starting...");
        SatelliteModemStateCallbackTest callback = new SatelliteModemStateCallbackTest();
        long registerResult =
                sSatelliteManager.registerForModemStateChanged(
                        getContext().getMainExecutor(), callback);
        assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerResult);
        assertTrue(callback.waitUntilResult(1));
        if (isSatelliteEnabled()) {
            logd(
                    "testRequestSatelliteEnabled_OffToDemoToP2p_SuccessfulResponse: disabling"
                            + " satellite... (1)");
            requestSatelliteEnabled(false);
            assertTrue(callback.waitUntilModemOff());
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback.modemState);
            assertFalse(isSatelliteEnabled());
            callback.clearModemStates();
        }

        sMockSatelliteServiceManager.setShouldRespondEnableRequest(false);
        assertTrue(
                sMockSatelliteServiceManager.setSatelliteControllerTimeoutDuration(
                        false,
                        TIMEOUT_TYPE_WAIT_FOR_SATELLITE_ENABLING_RESPONSE,
                        WAIT_FOREVER_TIMEOUT_MILLIS));

        // Move to enabling state
        logd(
                "testRequestSatelliteEnabled_OffToDemoToP2p_SuccessfulResponse: enabling"
                        + " satellite with demo mode... (2)");
        sMockSatelliteServiceManager.clearRequestSatelliteEnabledPermits();
        LinkedBlockingQueue<Integer> firstEnableResult =
                requestSatelliteEnabledWithoutWaitingForResult(true, true, false);
        assertTrue(sMockSatelliteServiceManager.waitForEventOnRequestSatelliteEnabled(1));
        assertTrue(callback.waitUntilResult(1));
        assertEquals(1, callback.getTotalCountOfModemStates());
        assertEquals(
                SatelliteManager.SATELLITE_MODEM_STATE_ENABLING_SATELLITE,
                callback.getModemState(0));
        assertFalse(isSatelliteEnabled());

        // Change to real mode while enabling demo mode is in progress
        logd(
                "testRequestSatelliteEnabled_OffToDemoToP2p_SuccessfulResponse: updating to real"
                        + " mode ... (3)");
        callback.clearModemStates();
        LinkedBlockingQueue<Integer> secondEnableResult =
                requestSatelliteEnabledWithoutWaitingForResult(true, false, false);
        // Wait for some time to make sure SatelliteController receive the second request
        waitFor(500);

        // Send a successful response for the first enable request
        logd(
                "testRequestSatelliteEnabled_OffToDemoToP2p_SuccessfulResponse: responding to"
                        + " the first enable request... (4)");
        sMockSatelliteServiceManager.clearRequestSatelliteEnabledPermits();
        callback.clearModemStates();
        assertTrue(
                sMockSatelliteServiceManager.respondToRequestSatelliteEnabled(
                        true, SatelliteModemState.SATELLITE_MODEM_STATE_OUT_OF_SERVICE));
        assertResult(firstEnableResult, SATELLITE_RESULT_SUCCESS);
        assertTrue(callback.waitUntilResult(1));
        assertEquals(
                SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED, callback.getModemState(0));
        assertTrue(isSatelliteEnabled());
        verifyDemoMode(true);
        // The second enable request should be pushed to modem now
        assertTrue(sMockSatelliteServiceManager.waitForEventOnRequestSatelliteEnabled(1));

        // Send a successful response for the second enable request
        logd(
                "testRequestSatelliteEnabled_OffToDemoToP2p_SuccessfulResponse: responding to"
                        + " the second enable request... (5)");
        assertTrue(
                sMockSatelliteServiceManager.respondToRequestSatelliteEnabled(
                        true, MockSatelliteService.NOT_UPDATED_SATELLITE_MODEM_STATE));
        assertResult(secondEnableResult, SATELLITE_RESULT_SUCCESS);
        assertTrue(isSatelliteEnabled());
        verifyDemoMode(false);

        // Restore the original states
        sSatelliteManager.unregisterForModemStateChanged(callback);
        sMockSatelliteServiceManager.setShouldRespondEnableRequest(true);
        assertTrue(
                sMockSatelliteServiceManager.setSatelliteControllerTimeoutDuration(
                        true, TIMEOUT_TYPE_WAIT_FOR_SATELLITE_ENABLING_RESPONSE, 0));
        sMockSatelliteServiceManager.clearSatelliteEnableRequestQueues();
        revokeSatellitePermission();
    }

    @Test
    public void testSendKeepAliveDatagramInNotConnectedState() {
        grantSatellitePermission();
        assertTrue(isSatelliteProvisioned());

        SatelliteManagerTestBase.SatelliteModemStateCallbackTest callback =
                new SatelliteManagerTestBase.SatelliteModemStateCallbackTest();
        boolean originalEnabledState = isSatelliteEnabled();
        boolean registerCallback = false;
        if (originalEnabledState) {
            registerCallback = true;

            long registerResult =
                    sSatelliteManager.registerForModemStateChanged(
                            getContext().getMainExecutor(), callback);
            assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerResult);
            assertTrue(callback.waitUntilResult(1));

            requestSatelliteEnabled(false);

            assertTrue(callback.waitUntilModemOff());
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback.modemState);
            assertFalse(isSatelliteEnabled());
            callback.clearModemStates();
        }
        if (!registerCallback) {
            long registerResult =
                    sSatelliteManager.registerForModemStateChanged(
                            getContext().getMainExecutor(), callback);
            assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerResult);
            assertTrue(callback.waitUntilResult(1));
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback.modemState);
        }

        assertTrue(sMockSatelliteServiceManager.connectSatelliteGatewayService());
        sMockSatelliteServiceManager.clearMockPointingUiActivityStatusChanges();
        requestSatelliteEnabled(true);
        assertTrue(callback.waitUntilResult(2));
        assertEquals(2, callback.getTotalCountOfModemStates());
        assertEquals(
                SatelliteManager.SATELLITE_MODEM_STATE_ENABLING_SATELLITE,
                callback.getModemState(0));
        assertEquals(
                SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED, callback.getModemState(1));
        assertTrue(isSatelliteEnabled());
        assertTrue(sMockSatelliteServiceManager.waitForRemoteSatelliteGatewayServiceConnected(1));

        SatelliteManagerTestBase.SatelliteTransmissionUpdateCallbackTest datagramCallback =
                startTransmissionUpdates();
        String mText = "This is a test datagram message from user";
        SatelliteDatagram datagram = new SatelliteDatagram(mText.getBytes());
        LinkedBlockingQueue<Integer> sosResultListener = new LinkedBlockingQueue<>(1);
        LinkedBlockingQueue<Integer> keepAliveResultListener = new LinkedBlockingQueue<>(1);
        sMockSatelliteServiceManager.clearMockPointingUiActivityStatusChanges();
        sMockSatelliteServiceManager.clearSentSatelliteDatagramInfo();

        // Send SOS satellite datagram
        datagramCallback.clearSendDatagramRequested();
        sSatelliteManager.sendDatagram(
                DATAGRAM_TYPE_SOS_MESSAGE,
                datagram,
                true,
                getContext().getMainExecutor(),
                sosResultListener::offer);

        // Expected datagram transfer state transitions: IDLE -> WAITING_FOR_CONNECTED
        assertTrue(datagramCallback.waitUntilOnSendDatagramStateChanged(1));
        assertThat(datagramCallback.getNumOfSendDatagramStateChanges()).isEqualTo(1);
        assertThat(datagramCallback.getSendDatagramStateChange(0))
                .isEqualTo(
                        new SatelliteManagerTestBase.SatelliteTransmissionUpdateCallbackTest
                                .DatagramStateChangeArgument(
                                SatelliteManager
                                        .SATELLITE_DATAGRAM_TRANSFER_STATE_WAITING_TO_CONNECT,
                                1,
                                SatelliteManager.SATELLITE_RESULT_SUCCESS));
        assertTrue(datagramCallback.waitUntilOnSendDatagramRequested(1));
        assertEquals(1, datagramCallback.getNumOfSendDatagramRequestedChanges());
        assertEquals(DATAGRAM_TYPE_SOS_MESSAGE, datagramCallback.getSendDatagramRequestedType(0));

        // Send keepAlive satellite datagram
        datagramCallback.clearSendDatagramStateChanges();
        datagramCallback.clearSendDatagramRequested();
        callback.clearModemStates();
        sSatelliteManager.sendDatagram(
                SatelliteManager.DATAGRAM_TYPE_KEEP_ALIVE,
                datagram,
                true,
                getContext().getMainExecutor(),
                keepAliveResultListener::offer);
        assertTrue(datagramCallback.waitUntilOnSendDatagramRequested(1));
        assertEquals(1, datagramCallback.getNumOfSendDatagramRequestedChanges());
        assertEquals(
                SatelliteManager.DATAGRAM_TYPE_KEEP_ALIVE,
                datagramCallback.getSendDatagramRequestedType(0));

        // Modem state state should not be updated
        assertFalse(callback.waitUntilResult(1));
        // WAITING_FOR_CONNECTED will be broadcasted again after sending the keepAlive
        // datagram
        assertTrue(datagramCallback.waitUntilOnSendDatagramStateChanged(1));
        assertThat(datagramCallback.getNumOfSendDatagramStateChanges()).isEqualTo(1);
        assertThat(datagramCallback.getSendDatagramStateChange(0))
                .isEqualTo(
                        new SatelliteManagerTestBase.SatelliteTransmissionUpdateCallbackTest
                                .DatagramStateChangeArgument(
                                SatelliteManager
                                        .SATELLITE_DATAGRAM_TRANSFER_STATE_WAITING_TO_CONNECT,
                                1,
                                SatelliteManager.SATELLITE_RESULT_SUCCESS));

        Integer errorCode;
        try {
            errorCode = keepAliveResultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail(
                    "testSendSatelliteDatagram_success: Got InterruptedException in waiting"
                            + " for the sendDatagram result code");
            return;
        }
        assertNotNull(errorCode);
        assertThat(errorCode).isEqualTo(SatelliteManager.SATELLITE_RESULT_SUCCESS);
        assertTrue(sMockSatelliteServiceManager.waitForEventOnSendSatelliteDatagram(1));

        // Move satellite to CONNECTED state
        datagramCallback.clearSendDatagramStateChanges();
        sMockSatelliteServiceManager.sendOnSatelliteModemStateChanged(
                SatelliteManager.SATELLITE_MODEM_STATE_CONNECTED);

        // The SOS datagram should be sent
        int expectedNumberOfEvents = 3;
        assertTrue(callback.waitUntilResult(expectedNumberOfEvents));
        assertEquals(expectedNumberOfEvents, callback.getTotalCountOfModemStates());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_CONNECTED, callback.getModemState(0));
        assertEquals(
                SatelliteManager.SATELLITE_MODEM_STATE_DATAGRAM_TRANSFERRING,
                callback.getModemState(1));
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_CONNECTED, callback.getModemState(2));

        // Expected datagram transfer state transitions: WAITING_FOR_CONNECTED -> SENDING
        // -> SEND_SUCCESS -> IDLE
        assertTrue(datagramCallback.waitUntilOnSendDatagramStateChanged(3));
        assertThat(datagramCallback.getNumOfSendDatagramStateChanges()).isEqualTo(3);
        assertThat(datagramCallback.getSendDatagramStateChange(0))
                .isEqualTo(
                        new SatelliteManagerTestBase.SatelliteTransmissionUpdateCallbackTest
                                .DatagramStateChangeArgument(
                                SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SENDING,
                                1,
                                SatelliteManager.SATELLITE_RESULT_SUCCESS));
        assertThat(datagramCallback.getSendDatagramStateChange(1))
                .isEqualTo(
                        new SatelliteManagerTestBase.SatelliteTransmissionUpdateCallbackTest
                                .DatagramStateChangeArgument(
                                SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SEND_SUCCESS,
                                0,
                                SatelliteManager.SATELLITE_RESULT_SUCCESS));
        assertThat(datagramCallback.getSendDatagramStateChange(2))
                .isEqualTo(
                        new SatelliteManagerTestBase.SatelliteTransmissionUpdateCallbackTest
                                .DatagramStateChangeArgument(
                                SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                                0,
                                SatelliteManager.SATELLITE_RESULT_SUCCESS));

        sSatelliteManager.stopTransmissionUpdates(
                datagramCallback, getContext().getMainExecutor(), keepAliveResultListener::offer);
        sSatelliteManager.unregisterForModemStateChanged(callback);
        assertTrue(sMockSatelliteServiceManager.setSatelliteListeningTimeoutDuration(0));
        assertTrue(sMockSatelliteServiceManager.restoreSatelliteGatewayServicePackageName());
        revokeSatellitePermission();
    }

    @Test
    public void testRegisterForSelectedNbIotSatelliteSubscriptionChanged() {
        logd("testRegisterForSelectedNbIotSatelliteSubscriptionChanged: start");
        grantSatellitePermission();

        SelectedNbIotSatelliteSubscriptionCallbackTest
                selectedNbIotSatelliteSubscriptionCallbackTest =
                        new SelectedNbIotSatelliteSubscriptionCallbackTest();

        /* register callback for satellite subscription id changed event */
        @SatelliteManager.SatelliteResult
        int registerError =
                sSatelliteManager.registerForSelectedNbIotSatelliteSubscriptionChanged(
                        getContext().getMainExecutor(),
                        selectedNbIotSatelliteSubscriptionCallbackTest);
        assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerError);

        /* Wait for the callback to be called */
        assertTrue(selectedNbIotSatelliteSubscriptionCallbackTest.waitUntilResult(1));

        /* Verify whether notified and requested subscription are equal */
        Pair<Integer, Integer> pairResult = requestSelectedNbIotSatelliteSubscriptionId();
        assertEquals(
                selectedNbIotSatelliteSubscriptionCallbackTest.mSelectedSubId,
                (long) pairResult.first);
        assertNull(pairResult.second);

        /* unregister */
        sSatelliteManager.unregisterForSelectedNbIotSatelliteSubscriptionChanged(
                selectedNbIotSatelliteSubscriptionCallbackTest);

        revokeSatellitePermission();
    }

    @Test
    public void testRequestSatelliteEnabled_OffToP2pToEmergency_SuccessfulResponse() {
        /*
         * Test scenario:
         * 1) Enable request with P2P mode
         * 2) Satellite should move to ENABLING state
         * 3) Enable request with emergency mode
         * 4) Successful response from modem for the first enable request
         * 5) Satellite should move to NOT_CONNECTED state and in P2P mode
         * 6) Successful response from modem for the second enable request
         * 7) Satellite should stay at NOT_CONNECTED state and in emergency mode
         */
        grantSatellitePermission();
        assertTrue(isSatelliteProvisioned());

        logd("testRequestSatelliteEnabled_OffToP2pToEmergency_SuccessfulResponse: starting...");
        SatelliteModemStateCallbackTest callback = new SatelliteModemStateCallbackTest();
        long registerResult =
                sSatelliteManager.registerForModemStateChanged(
                        getContext().getMainExecutor(), callback);
        assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerResult);
        assertTrue(callback.waitUntilResult(1));
        if (isSatelliteEnabled()) {
            logd(
                    "testRequestSatelliteEnabled_OffToP2pToEmergency_SuccessfulResponse: disabling"
                            + " satellite... (1)");
            requestSatelliteEnabled(false);
            assertTrue(callback.waitUntilModemOff());
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback.modemState);
            assertFalse(isSatelliteEnabled());
            callback.clearModemStates();
        }

        sMockSatelliteServiceManager.setShouldRespondEnableRequest(false);
        assertTrue(
                sMockSatelliteServiceManager.setSatelliteControllerTimeoutDuration(
                        false,
                        TIMEOUT_TYPE_WAIT_FOR_SATELLITE_ENABLING_RESPONSE,
                        WAIT_FOREVER_TIMEOUT_MILLIS));

        // Move to enabling state
        logd(
                "testRequestSatelliteEnabled_OffToP2pToEmergency_SuccessfulResponse: enabling"
                        + " satellite for P2P SMS... (2)");
        sMockSatelliteServiceManager.clearRequestSatelliteEnabledPermits();
        LinkedBlockingQueue<Integer> firstEnableResult =
                requestSatelliteEnabledWithoutWaitingForResult(true, false, false);
        assertTrue(sMockSatelliteServiceManager.waitForEventOnRequestSatelliteEnabled(1));
        assertTrue(callback.waitUntilResult(1));
        assertEquals(1, callback.getTotalCountOfModemStates());
        assertEquals(
                SatelliteManager.SATELLITE_MODEM_STATE_ENABLING_SATELLITE,
                callback.getModemState(0));
        assertFalse(isSatelliteEnabled());

        // Change to real mode while enabling demo mode is in progress
        logd(
                "testRequestSatelliteEnabled_OffToP2pToEmergency_SuccessfulResponse: updating to "
                        + "emergency mode... (3)");
        callback.clearModemStates();
        LinkedBlockingQueue<Integer> secondEnableResult =
                requestSatelliteEnabledWithoutWaitingForResult(true, false, true);
        // Wait for some time to make sure SatelliteController receive the second request
        waitFor(500);

        // Send a successful response for the first enable request
        logd(
                "testRequestSatelliteEnabled_OffToP2pToEmergency_SuccessfulResponse: responding to"
                        + " the first enable request... (4)");
        sMockSatelliteServiceManager.clearRequestSatelliteEnabledPermits();
        callback.clearModemStates();
        assertTrue(
                sMockSatelliteServiceManager.respondToRequestSatelliteEnabled(
                        true, SatelliteModemState.SATELLITE_MODEM_STATE_OUT_OF_SERVICE));
        assertResult(firstEnableResult, SATELLITE_RESULT_SUCCESS);
        assertTrue(callback.waitUntilResult(1));
        assertEquals(
                SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED, callback.getModemState(0));
        assertTrue(isSatelliteEnabled());
        verifyEmergencyMode(false);
        // The second enable request should be pushed to modem now
        assertTrue(sMockSatelliteServiceManager.waitForEventOnRequestSatelliteEnabled(1));

        // Send a successful response for the second enable request
        logd(
                "testRequestSatelliteEnabled_OffToP2pToEmergency_SuccessfulResponse: responding to"
                        + " the second enable request... (5)");
        assertTrue(
                sMockSatelliteServiceManager.respondToRequestSatelliteEnabled(
                        true, MockSatelliteService.NOT_UPDATED_SATELLITE_MODEM_STATE));
        assertResult(secondEnableResult, SATELLITE_RESULT_SUCCESS);
        assertTrue(isSatelliteEnabled());
        verifyEmergencyMode(true);

        // Restore the original states
        sSatelliteManager.unregisterForModemStateChanged(callback);
        sMockSatelliteServiceManager.setShouldRespondEnableRequest(true);
        assertTrue(
                sMockSatelliteServiceManager.setSatelliteControllerTimeoutDuration(
                        true, TIMEOUT_TYPE_WAIT_FOR_SATELLITE_ENABLING_RESPONSE, 0));
        sMockSatelliteServiceManager.clearSatelliteEnableRequestQueues();
        revokeSatellitePermission();
    }

    @Test
    public void testRequestSatelliteEnabled_OffToP2pToEmergency_FailureResponse() {
        /*
         * Test scenario:
         * 1) Enable request with P2P mode
         * 2) Satellite should move to ENABLING state
         * 3) Enable request with emergency mode
         * 4) Successful response from modem for the first enable request
         * 5) Satellite should move to NOT_CONNECTED state and in P2P mode
         * 6) Failure response from modem for the second enable request
         * 7) Satellite should stay at NOT_CONNECTED state and in P2P mode
         */
        grantSatellitePermission();
        assertTrue(isSatelliteProvisioned());

        logd("testRequestSatelliteEnabled_OffToP2pToEmergency_FailureResponse: starting...");
        SatelliteModemStateCallbackTest callback = new SatelliteModemStateCallbackTest();
        long registerResult =
                sSatelliteManager.registerForModemStateChanged(
                        getContext().getMainExecutor(), callback);
        assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerResult);
        assertTrue(callback.waitUntilResult(1));
        if (isSatelliteEnabled()) {
            logd(
                    "testRequestSatelliteEnabled_OffToP2pToEmergency_FailureResponse: disabling"
                            + " satellite... (1)");
            requestSatelliteEnabled(false);
            assertTrue(callback.waitUntilModemOff());
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback.modemState);
            assertFalse(isSatelliteEnabled());
            callback.clearModemStates();
        }

        sMockSatelliteServiceManager.setShouldRespondEnableRequest(false);
        assertTrue(
                sMockSatelliteServiceManager.setSatelliteControllerTimeoutDuration(
                        false,
                        TIMEOUT_TYPE_WAIT_FOR_SATELLITE_ENABLING_RESPONSE,
                        WAIT_FOREVER_TIMEOUT_MILLIS));

        // Move to enabling state
        logd(
                "testRequestSatelliteEnabled_OffToP2pToEmergency_FailureResponse: enabling"
                        + " satellite for P2P SMS... (2)");
        sMockSatelliteServiceManager.clearRequestSatelliteEnabledPermits();
        LinkedBlockingQueue<Integer> firstEnableResult =
                requestSatelliteEnabledWithoutWaitingForResult(true, false, false);
        assertTrue(sMockSatelliteServiceManager.waitForEventOnRequestSatelliteEnabled(1));
        assertTrue(callback.waitUntilResult(1));
        assertEquals(1, callback.getTotalCountOfModemStates());
        assertEquals(
                SatelliteManager.SATELLITE_MODEM_STATE_ENABLING_SATELLITE,
                callback.getModemState(0));
        assertFalse(isSatelliteEnabled());

        // Change to real mode while enabling demo mode is in progress
        logd(
                "testRequestSatelliteEnabled_OffToP2pToEmergency_FailureResponse: updating to "
                        + "emergency mode... (3)");
        callback.clearModemStates();
        LinkedBlockingQueue<Integer> secondEnableResult =
                requestSatelliteEnabledWithoutWaitingForResult(true, false, true);
        // Wait for some time to make sure SatelliteController receive the second request
        waitFor(500);

        // Send a successful response for the first enable request
        logd(
                "testRequestSatelliteEnabled_OffToP2pToEmergency_FailureResponse: responding to"
                        + " the first enable request... (4)");
        sMockSatelliteServiceManager.clearRequestSatelliteEnabledPermits();
        callback.clearModemStates();
        assertTrue(
                sMockSatelliteServiceManager.respondToRequestSatelliteEnabled(
                        true, SatelliteModemState.SATELLITE_MODEM_STATE_OUT_OF_SERVICE));
        assertResult(firstEnableResult, SATELLITE_RESULT_SUCCESS);
        assertTrue(callback.waitUntilResult(1));
        assertEquals(
                SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED, callback.getModemState(0));
        assertTrue(isSatelliteEnabled());
        verifyEmergencyMode(false);
        // The second enable request should be pushed to modem now
        assertTrue(sMockSatelliteServiceManager.waitForEventOnRequestSatelliteEnabled(1));

        // Send a failure response for the second enable request
        logd(
                "testRequestSatelliteEnabled_OffToP2pToEmergency_FailureResponse: responding to"
                        + " the second enable request... (5)");
        sMockSatelliteServiceManager.setErrorCode(
                SatelliteResult.SATELLITE_RESULT_REQUEST_NOT_SUPPORTED);
        assertTrue(
                sMockSatelliteServiceManager.respondToRequestSatelliteEnabled(
                        true, MockSatelliteService.NOT_UPDATED_SATELLITE_MODEM_STATE));
        assertResult(secondEnableResult, SATELLITE_RESULT_REQUEST_NOT_SUPPORTED);
        assertTrue(isSatelliteEnabled());
        verifyEmergencyMode(false);

        // Restore the original states
        sMockSatelliteServiceManager.setErrorCode(SatelliteResult.SATELLITE_RESULT_SUCCESS);
        sSatelliteManager.unregisterForModemStateChanged(callback);
        sMockSatelliteServiceManager.setShouldRespondEnableRequest(true);
        assertTrue(
                sMockSatelliteServiceManager.setSatelliteControllerTimeoutDuration(
                        true, TIMEOUT_TYPE_WAIT_FOR_SATELLITE_ENABLING_RESPONSE, 0));
        sMockSatelliteServiceManager.clearSatelliteEnableRequestQueues();
        revokeSatellitePermission();
    }

    @Test
    public void testRequestSatelliteEnabled_DemoToP2p_SuccessfulResponse() {
        /*
         * Test scenario:
         * 1) Enable request with demo mode
         * 2) Satellite should move to ENABLING state
         * 3) Successful response from modem for the first enable request
         * 4) Satellite should move to NOT_CONNECTED state and in demo mode
         * 5) Enable request with P2P mode
         * 6) Satellite should move to ENABLING state
         * 7) Successful response from modem for the second enable request
         * 8) Satellite should move to NOT_CONNECTED state and in P2P mode
         */
        grantSatellitePermission();
        assertTrue(isSatelliteProvisioned());

        logd("testRequestSatelliteEnabled_DemoToP2p_SuccessfulResponse: starting...");
        SatelliteModemStateCallbackTest callback = new SatelliteModemStateCallbackTest();
        long registerResult =
                sSatelliteManager.registerForModemStateChanged(
                        getContext().getMainExecutor(), callback);
        assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerResult);
        assertTrue(callback.waitUntilResult(1));
        if (isSatelliteEnabled()) {
            logd(
                    "testRequestSatelliteEnabled_DemoToP2p_SuccessfulResponse: disabling"
                            + " satellite... (1)");
            requestSatelliteEnabled(false);
            assertTrue(callback.waitUntilModemOff());
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback.modemState);
            assertFalse(isSatelliteEnabled());
            callback.clearModemStates();
        }

        // Enable satellite with demo mode
        logd(
                "testRequestSatelliteEnabled_DemoToP2p_SuccessfulResponse: enabling"
                        + " satellite with demo mode... (2)");
        callback.clearModemStates();
        sMockSatelliteServiceManager.clearRequestSatelliteEnabledPermits();
        requestSatelliteEnabled(true, true, SATELLITE_RESULT_SUCCESS);
        assertTrue(sMockSatelliteServiceManager.waitForEventOnRequestSatelliteEnabled(1));
        assertTrue(callback.waitUntilResult(2));
        assertEquals(2, callback.getTotalCountOfModemStates());
        assertEquals(
                SatelliteManager.SATELLITE_MODEM_STATE_ENABLING_SATELLITE,
                callback.getModemState(0));
        assertEquals(
                SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED, callback.getModemState(1));
        assertTrue(isSatelliteEnabled());
        verifyDemoMode(true);

        // Change to P2P mode
        logd(
                "testRequestSatelliteEnabled_DemoToP2p_SuccessfulResponse: updating to real mode"
                        + " ... (3)");
        callback.clearModemStates();
        requestSatelliteEnabled(true, false, SATELLITE_RESULT_SUCCESS);
        verifyDemoMode(false);

        // Restore the original states
        sSatelliteManager.unregisterForModemStateChanged(callback);
        sMockSatelliteServiceManager.setShouldRespondEnableRequest(true);
        assertTrue(
                sMockSatelliteServiceManager.setSatelliteControllerTimeoutDuration(
                        true, TIMEOUT_TYPE_WAIT_FOR_SATELLITE_ENABLING_RESPONSE, 0));
        sMockSatelliteServiceManager.clearSatelliteEnableRequestQueues();
        revokeSatellitePermission();
    }

    @Test
    public void testRequestSatelliteEnabled_P2pToEmergency_SuccessfulResponse() {
        /*
         * Test scenario:
         * 1) Enable request with P2P mode
         * 2) Satellite should move to ENABLING state
         * 3) Successful response from modem for the first enable request
         * 4) Satellite should move to NOT_CONNECTED state and in P2P mode
         * 5) Enable request with emergency mode
         * 6) Satellite should move to ENABLING state
         * 7) Successful response from modem for the second enable request
         * 8) Satellite should move to NOT_CONNECTED state and in emergency mode
         */
        grantSatellitePermission();
        assertTrue(isSatelliteProvisioned());

        logd("testRequestSatelliteEnabled_P2pToEmergency_SuccessfulResponse: starting...");
        SatelliteModemStateCallbackTest callback = new SatelliteModemStateCallbackTest();
        long registerResult =
                sSatelliteManager.registerForModemStateChanged(
                        getContext().getMainExecutor(), callback);
        assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerResult);
        assertTrue(callback.waitUntilResult(1));
        if (isSatelliteEnabled()) {
            logd(
                    "testRequestSatelliteEnabled_P2pToEmergency_SuccessfulResponse: disabling"
                            + " satellite... (1)");
            requestSatelliteEnabled(false);
            assertTrue(callback.waitUntilModemOff());
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback.modemState);
            assertFalse(isSatelliteEnabled());
            callback.clearModemStates();
        }

        // Enable satellite with P2P mode
        logd(
                "testRequestSatelliteEnabled_P2pToEmergency_SuccessfulResponse: enabling"
                        + " satellite with P2P mode... (2)");
        callback.clearModemStates();
        sMockSatelliteServiceManager.clearRequestSatelliteEnabledPermits();
        requestSatelliteEnabled(true, false, SATELLITE_RESULT_SUCCESS);
        assertTrue(sMockSatelliteServiceManager.waitForEventOnRequestSatelliteEnabled(1));
        assertTrue(callback.waitUntilResult(2));
        assertEquals(2, callback.getTotalCountOfModemStates());
        assertEquals(
                SatelliteManager.SATELLITE_MODEM_STATE_ENABLING_SATELLITE,
                callback.getModemState(0));
        assertEquals(
                SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED, callback.getModemState(1));
        assertTrue(isSatelliteEnabled());
        verifyEmergencyMode(false);

        // Change to emergency mode
        logd(
                "testRequestSatelliteEnabled_P2pToEmergency_SuccessfulResponse: updating to"
                        + " emergency mode... (3)");
        callback.clearModemStates();
        LinkedBlockingQueue<Integer> secondEnableResult =
                requestSatelliteEnabledWithoutWaitingForResult(true, false, true);
        assertResult(secondEnableResult, SATELLITE_RESULT_SUCCESS);
        verifyEmergencyMode(true);

        // Restore the original states
        sSatelliteManager.unregisterForModemStateChanged(callback);
        sMockSatelliteServiceManager.setShouldRespondEnableRequest(true);
        assertTrue(
                sMockSatelliteServiceManager.setSatelliteControllerTimeoutDuration(
                        true, TIMEOUT_TYPE_WAIT_FOR_SATELLITE_ENABLING_RESPONSE, 0));
        sMockSatelliteServiceManager.clearSatelliteEnableRequestQueues();
        revokeSatellitePermission();
    }

    @Test
    public void testRequestSatelliteEnabled_OffToDemoToP2pToOff_FailureResponseForDisable() {
        /*
         * Test scenario:
         * 1) Enable request with demo mode
         * 2) Satellite should move to ENABLING state
         * 3) Enable request with P2P mode
         * 4) Disable request
         * 5) Satellite should move to DISABLING state
         * 6) Failure response from modem for the disable request
         * 7) Satellite should move back to ENABLING state
         * 8) Successful response for the first enable request
         * 9) Satellite should move to NOT_CONNECTED state and in demo mode
         * 10) Successful response for the second enable request
         * 11) Satellite should stay at NOT_CONNECTED state and in P2P mode
         */
        grantSatellitePermission();
        assertTrue(isSatelliteProvisioned());

        logd(
                "testRequestSatelliteEnabled_OffToDemoToP2pToOff_FailureResponseForDisable: "
                        + "starting...");
        SatelliteModemStateCallbackTest callback = new SatelliteModemStateCallbackTest();
        long registerResult =
                sSatelliteManager.registerForModemStateChanged(
                        getContext().getMainExecutor(), callback);
        assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerResult);
        assertTrue(callback.waitUntilResult(1));
        if (isSatelliteEnabled()) {
            logd(
                    "testRequestSatelliteEnabled_OffToDemoToP2pToOff_FailureResponseForDisable: "
                            + "disabling satellite... (1)");
            requestSatelliteEnabled(false);
            assertTrue(callback.waitUntilModemOff());
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback.modemState);
            assertFalse(isSatelliteEnabled());
            callback.clearModemStates();
        }

        sMockSatelliteServiceManager.setShouldRespondEnableRequest(false);
        assertTrue(
                sMockSatelliteServiceManager.setSatelliteControllerTimeoutDuration(
                        false,
                        TIMEOUT_TYPE_WAIT_FOR_SATELLITE_ENABLING_RESPONSE,
                        WAIT_FOREVER_TIMEOUT_MILLIS));

        // Move to enabling state
        logd(
                "testRequestSatelliteEnabled_OffToDemoToP2pToOff_FailureResponseForDisable:"
                        + " enabling satellite with demo mode... (2)");
        sMockSatelliteServiceManager.clearRequestSatelliteEnabledPermits();
        LinkedBlockingQueue<Integer> firstEnableResult =
                requestSatelliteEnabledWithoutWaitingForResult(true, true, false);
        assertTrue(sMockSatelliteServiceManager.waitForEventOnRequestSatelliteEnabled(1));
        assertTrue(callback.waitUntilResult(1));
        assertEquals(1, callback.getTotalCountOfModemStates());
        assertEquals(
                SatelliteManager.SATELLITE_MODEM_STATE_ENABLING_SATELLITE,
                callback.getModemState(0));
        assertFalse(isSatelliteEnabled());

        // Change to real mode while enabling demo mode is in progress
        logd(
                "testRequestSatelliteEnabled_OffToDemoToP2pToOff_FailureResponseForDisable: "
                        + "updating to real mode ... (3)");
        callback.clearModemStates();
        LinkedBlockingQueue<Integer> secondEnableResult =
                requestSatelliteEnabledWithoutWaitingForResult(true, false, false);
        // Wait for some time to make sure SatelliteController receive the second request
        waitFor(500);

        // Disable satellite while enabling and enable attributes updating are in progress
        logd(
                "testRequestSatelliteEnabled_OffToDemoToP2pToOff_FailureResponseForDisable:"
                        + " disabling satellite... (4)");
        callback.clearModemStates();
        sMockSatelliteServiceManager.clearRequestSatelliteEnabledPermits();
        LinkedBlockingQueue<Integer> disableResult =
                requestSatelliteEnabledWithoutWaitingForResult(false, false, false);
        assertTrue(sMockSatelliteServiceManager.waitForEventOnRequestSatelliteEnabled(1));
        assertTrue(callback.waitUntilResult(1));
        assertEquals(1, callback.getTotalCountOfModemStates());
        assertEquals(
                SatelliteManager.SATELLITE_MODEM_STATE_DISABLING_SATELLITE,
                callback.getModemState(0));

        // Send a failure response for the disable request
        logd(
                "testRequestSatelliteEnabled_OffToDemoToP2pToOff_FailureResponseForDisable: "
                        + "responding to the disable request... (5)");
        callback.clearModemStates();
        sMockSatelliteServiceManager.setErrorCode(SatelliteResult.SATELLITE_RESULT_NO_RESOURCES);
        assertTrue(
                sMockSatelliteServiceManager.respondToRequestSatelliteEnabled(
                        false, MockSatelliteService.NOT_UPDATED_SATELLITE_MODEM_STATE));
        assertResult(disableResult, SATELLITE_RESULT_NO_RESOURCES);
        assertTrue(callback.waitUntilResult(1));
        assertEquals(
                SatelliteManager.SATELLITE_MODEM_STATE_ENABLING_SATELLITE,
                callback.getModemState(0));

        // Send a successful response for the first enable request
        logd(
                "testRequestSatelliteEnabled_OffToDemoToP2pToOff_FailureResponseForDisable: "
                        + "responding to the first enable request... (6)");
        callback.clearModemStates();
        sMockSatelliteServiceManager.clearRequestSatelliteEnabledPermits();
        sMockSatelliteServiceManager.setErrorCode(SatelliteResult.SATELLITE_RESULT_SUCCESS);
        assertTrue(
                sMockSatelliteServiceManager.respondToRequestSatelliteEnabled(
                        true, SatelliteModemState.SATELLITE_MODEM_STATE_OUT_OF_SERVICE));
        assertResult(firstEnableResult, SATELLITE_RESULT_SUCCESS);
        assertTrue(callback.waitUntilResult(1));
        assertEquals(1, callback.getTotalCountOfModemStates());
        assertEquals(
                SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED, callback.getModemState(0));
        verifyDemoMode(true);
        // The enable attributes update request should be pushed to modem now
        assertTrue(sMockSatelliteServiceManager.waitForEventOnRequestSatelliteEnabled(1));

        // Send a successful response for the second enable request
        logd(
                "testRequestSatelliteEnabled_OffToDemoToP2pToOff_FailureResponseForDisable: "
                        + "responding to the second enable request... (6)");
        callback.clearModemStates();
        assertTrue(
                sMockSatelliteServiceManager.respondToRequestSatelliteEnabled(
                        true, MockSatelliteService.NOT_UPDATED_SATELLITE_MODEM_STATE));
        assertResult(secondEnableResult, SATELLITE_RESULT_SUCCESS);
        verifyDemoMode(false);

        // Restore the original states
        sSatelliteManager.unregisterForModemStateChanged(callback);
        assertTrue(
                sMockSatelliteServiceManager.setSatelliteControllerTimeoutDuration(
                        true, TIMEOUT_TYPE_WAIT_FOR_SATELLITE_ENABLING_RESPONSE, 0));
        revokeSatellitePermission();
    }

    @Test
    public void testRequestSatelliteEnabled_ModemCrashDuringDisable() {
        /*
         * Test scenario:
         * 1) Send disable request to modem
         * 2) Satellite should move to DISABLING state
         * 3) Modem crash before responding to framework
         * 4) Modem come back up
         * 5) Framework abort the request and move to OFF state
         */
        grantSatellitePermission();
        assertTrue(isSatelliteProvisioned());

        logd("testRequestSatelliteEnabled_ModemCrashDuringDisable: starting...");
        SatelliteModemStateCallbackTest callback = new SatelliteModemStateCallbackTest();
        long registerResult =
                sSatelliteManager.registerForModemStateChanged(
                        getContext().getMainExecutor(), callback);
        assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerResult);
        assertTrue(callback.waitUntilResult(1));

        sMockSatelliteServiceManager.setShouldRespondEnableRequest(false);
        assertTrue(
                sMockSatelliteServiceManager.setSatelliteControllerTimeoutDuration(
                        false,
                        TIMEOUT_TYPE_WAIT_FOR_SATELLITE_ENABLING_RESPONSE,
                        WAIT_FOREVER_TIMEOUT_MILLIS));

        // Move to disabling state
        logd("testRequestSatelliteEnabled_ModemCrashDuringDisable: disabling satellite (1)");
        sMockSatelliteServiceManager.clearRequestSatelliteEnabledPermits();
        callback.clearModemStates();
        LinkedBlockingQueue<Integer> disableResult =
                requestSatelliteEnabledWithoutWaitingForResult(false, false, false);
        assertTrue(sMockSatelliteServiceManager.waitForEventOnRequestSatelliteEnabled(1));
        assertTrue(callback.waitUntilResult(1));
        assertEquals(1, callback.getTotalCountOfModemStates());
        assertEquals(
                SatelliteManager.SATELLITE_MODEM_STATE_DISABLING_SATELLITE,
                callback.getModemState(0));
        assertTrue(isSatelliteEnabled());

        // Mocking modem crash scenario
        logd("testRequestSatelliteEnabled_ModemCrashDuringDisable: mocking modem crash (2)");
        callback.clearModemStates();
        assertTrue(sMockSatelliteServiceManager.connectExternalSatelliteService());
        assertTrue(sMockSatelliteServiceManager.waitForRemoteSatelliteServiceConnected(1));

        // The disable request should be aborted
        assertResult(disableResult, SATELLITE_RESULT_MODEM_ERROR);
        assertTrue(callback.waitUntilResult(1));
        assertEquals(1, callback.getTotalCountOfModemStates());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback.getModemState(0));
        assertFalse(isSatelliteEnabled());

        // Restore original binding state
        logd(
                "testRequestSatelliteEnabled_ModemCrashDuringDisable: restoring mock satellite "
                        + "service (3)");
        sMockSatelliteServiceManager.resetSatelliteService();
        assertTrue(sMockSatelliteServiceManager.connectSatelliteService());
        sMockSatelliteServiceManager.setShouldRespondEnableRequest(true);
        assertTrue(
                sMockSatelliteServiceManager.setSatelliteControllerTimeoutDuration(
                        true, TIMEOUT_TYPE_WAIT_FOR_SATELLITE_ENABLING_RESPONSE, 0));

        // Telephony will disable satellite when vendor service is connected. This will
        // interfere with the below test and make the test flaky.
        // Enable satellite should succeed
        logd("testRequestSatelliteEnabled_ModemCrashDuringDisable: enabling satellite (4)");
        callback.clearModemStates();
        requestSatelliteEnabled(true);
        assertTrue(callback.waitUntilResult(2));
        assertEquals(2, callback.getTotalCountOfModemStates());
        assertEquals(
                SatelliteManager.SATELLITE_MODEM_STATE_ENABLING_SATELLITE,
                callback.getModemState(0));
        assertEquals(
                SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED, callback.getModemState(1));
        assertTrue(isSatelliteEnabled());

        assertTrue(sMockSatelliteServiceManager.stopExternalSatelliteService());
        assertTrue(sMockSatelliteServiceManager.waitForExternalSatelliteServiceDisconnected(1));
        sSatelliteManager.unregisterForModemStateChanged(callback);
        sMockSatelliteServiceManager.clearSatelliteEnableRequestQueues();
        revokeSatellitePermission();
    }

    @Test
    public void testRequestSatelliteEnabled_ModemCrashDuringEnable() {
        /*
         * Test scenario:
         * 1) Send enable request to modem
         * 2) Satellite should move to ENABLING state
         * 3) Modem crash before responding to framework
         * 4) Modem come back up
         * 5) Framework abort the request and move to OFF state
         */
        grantSatellitePermission();
        assertTrue(isSatelliteProvisioned());

        logd("testRequestSatelliteEnabled_ModemCrashDuringEnable: starting...");
        SatelliteModemStateCallbackTest callback = new SatelliteModemStateCallbackTest();
        long registerResult =
                sSatelliteManager.registerForModemStateChanged(
                        getContext().getMainExecutor(), callback);
        assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerResult);
        assertTrue(callback.waitUntilResult(1));

        if (isSatelliteEnabled()) {
            logd("testRequestSatelliteEnabled_ModemCrashDuringEnable: disabling satellite... (1)");
            requestSatelliteEnabled(false);
            assertTrue(callback.waitUntilModemOff());
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback.modemState);
            assertFalse(isSatelliteEnabled());
            callback.clearModemStates();
        }

        sMockSatelliteServiceManager.setShouldRespondEnableRequest(false);
        assertTrue(
                sMockSatelliteServiceManager.setSatelliteControllerTimeoutDuration(
                        false,
                        TIMEOUT_TYPE_WAIT_FOR_SATELLITE_ENABLING_RESPONSE,
                        WAIT_FOREVER_TIMEOUT_MILLIS));

        // Move to enabling state
        logd("testRequestSatelliteEnabled_ModemCrashDuringEnable: enabling satellite (2)");
        sMockSatelliteServiceManager.clearRequestSatelliteEnabledPermits();
        callback.clearModemStates();
        LinkedBlockingQueue<Integer> enableResult =
                requestSatelliteEnabledWithoutWaitingForResult(true, false, false);
        assertTrue(sMockSatelliteServiceManager.waitForEventOnRequestSatelliteEnabled(1));
        assertTrue(callback.waitUntilResult(1));
        assertEquals(1, callback.getTotalCountOfModemStates());
        assertEquals(
                SatelliteManager.SATELLITE_MODEM_STATE_ENABLING_SATELLITE,
                callback.getModemState(0));

        // Mocking modem crash scenario
        logd("testRequestSatelliteEnabled_ModemCrashDuringEnable: mocking modem crash (3)");
        callback.clearModemStates();
        assertTrue(sMockSatelliteServiceManager.connectExternalSatelliteService());
        assertTrue(sMockSatelliteServiceManager.waitForRemoteSatelliteServiceConnected(1));

        // The enable request should be aborted
        assertResult(enableResult, SATELLITE_RESULT_MODEM_ERROR);
        assertTrue(callback.waitUntilResult(1));
        assertEquals(1, callback.getTotalCountOfModemStates());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback.getModemState(0));
        assertFalse(isSatelliteEnabled());

        // Restore original binding state
        logd(
                "testRequestSatelliteEnabled_ModemCrashDuringEnable: restoring mock satellite "
                        + "service (4)");
        sMockSatelliteServiceManager.resetSatelliteService();
        assertTrue(sMockSatelliteServiceManager.connectSatelliteService());
        sMockSatelliteServiceManager.setShouldRespondEnableRequest(true);
        assertTrue(
                sMockSatelliteServiceManager.setSatelliteControllerTimeoutDuration(
                        true, TIMEOUT_TYPE_WAIT_FOR_SATELLITE_ENABLING_RESPONSE, 0));

        // Enable satellite should succeed
        logd("testRequestSatelliteEnabled_ModemCrashDuringEnable: enabling satellite (5)");
        callback.clearModemStates();
        requestSatelliteEnabled(true);
        assertTrue(callback.waitUntilResult(2));
        assertEquals(2, callback.getTotalCountOfModemStates());
        assertEquals(
                SatelliteManager.SATELLITE_MODEM_STATE_ENABLING_SATELLITE,
                callback.getModemState(0));
        assertEquals(
                SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED, callback.getModemState(1));
        assertTrue(isSatelliteEnabled());

        assertTrue(sMockSatelliteServiceManager.stopExternalSatelliteService());
        assertTrue(sMockSatelliteServiceManager.waitForExternalSatelliteServiceDisconnected(1));
        sSatelliteManager.unregisterForModemStateChanged(callback);
        sMockSatelliteServiceManager.clearSatelliteEnableRequestQueues();
        revokeSatellitePermission();
    }

    @Test
    public void testRequestSatelliteEnabled_ModemCrashDuringEnableEnableDisable() {
        /*
         * Test scenario:
         * 1) Send enable request to modem with demo mode
         * 2) Send enable request to modem with P2P mode
         * 3) Send a disable request to modem
         * 4) Modem crash before responding to framework
         * 5) Modem come back up
         * 6) Framework abort all requests and move to OFF state
         */
        grantSatellitePermission();
        assertTrue(isSatelliteProvisioned());

        logd("testRequestSatelliteEnabled_ModemCrashDuringEnableEnableDisable: starting...");
        SatelliteModemStateCallbackTest callback = new SatelliteModemStateCallbackTest();
        long registerResult =
                sSatelliteManager.registerForModemStateChanged(
                        getContext().getMainExecutor(), callback);
        assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerResult);
        assertTrue(callback.waitUntilResult(1));

        if (isSatelliteEnabled()) {
            logd(
                    "testRequestSatelliteEnabled_ModemCrashDuringEnableEnableDisable: disabling"
                            + " satellite... (1)");
            requestSatelliteEnabled(false);
            assertTrue(callback.waitUntilModemOff());
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback.modemState);
            assertFalse(isSatelliteEnabled());
            callback.clearModemStates();
        }

        sMockSatelliteServiceManager.setShouldRespondEnableRequest(false);
        assertTrue(
                sMockSatelliteServiceManager.setSatelliteControllerTimeoutDuration(
                        false,
                        TIMEOUT_TYPE_WAIT_FOR_SATELLITE_ENABLING_RESPONSE,
                        WAIT_FOREVER_TIMEOUT_MILLIS));

        // Move to enabling state
        logd(
                "testRequestSatelliteEnabled_ModemCrashDuringEnableEnableDisable: enabling"
                        + " satellite (2)");
        sMockSatelliteServiceManager.clearRequestSatelliteEnabledPermits();
        callback.clearModemStates();
        LinkedBlockingQueue<Integer> firstEnableResult =
                requestSatelliteEnabledWithoutWaitingForResult(true, true, false);
        assertTrue(sMockSatelliteServiceManager.waitForEventOnRequestSatelliteEnabled(1));
        assertTrue(callback.waitUntilResult(1));
        assertEquals(1, callback.getTotalCountOfModemStates());
        assertEquals(
                SatelliteManager.SATELLITE_MODEM_STATE_ENABLING_SATELLITE,
                callback.getModemState(0));

        // Change to real mode while enabling demo mode is in progress
        logd(
                "testRequestSatelliteEnabled_ModemCrashDuringEnableEnableDisable: "
                        + "updating to real mode ... (3)");
        callback.clearModemStates();
        LinkedBlockingQueue<Integer> secondEnableResult =
                requestSatelliteEnabledWithoutWaitingForResult(true, false, false);
        // Wait for some time to make sure SatelliteController receive the second request
        waitFor(500);

        // Disable satellite while enabling and enable attributes updating are in progress
        logd(
                "testRequestSatelliteEnabled_ModemCrashDuringEnableEnableDisable: disabling"
                        + " satellite... (4)");
        callback.clearModemStates();
        sMockSatelliteServiceManager.clearRequestSatelliteEnabledPermits();
        LinkedBlockingQueue<Integer> disableResult =
                requestSatelliteEnabledWithoutWaitingForResult(false, false, false);
        assertTrue(sMockSatelliteServiceManager.waitForEventOnRequestSatelliteEnabled(1));
        assertTrue(callback.waitUntilResult(1));
        assertEquals(1, callback.getTotalCountOfModemStates());
        assertEquals(
                SatelliteManager.SATELLITE_MODEM_STATE_DISABLING_SATELLITE,
                callback.getModemState(0));

        // Mocking modem crash scenario
        logd(
                "testRequestSatelliteEnabled_ModemCrashDuringEnableEnableDisable: mocking modem"
                        + " crash (5)");
        callback.clearModemStates();
        assertTrue(sMockSatelliteServiceManager.connectExternalSatelliteService());
        assertTrue(sMockSatelliteServiceManager.waitForRemoteSatelliteServiceConnected(1));

        // All requests should be aborted
        assertResult(firstEnableResult, SATELLITE_RESULT_MODEM_ERROR);
        assertResult(secondEnableResult, SATELLITE_RESULT_MODEM_ERROR);
        assertResult(disableResult, SATELLITE_RESULT_MODEM_ERROR);
        assertTrue(callback.waitUntilResult(1));
        assertEquals(1, callback.getTotalCountOfModemStates());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback.getModemState(0));
        assertFalse(isSatelliteEnabled());

        // Restore original binding state
        logd(
                "testRequestSatelliteEnabled_ModemCrashDuringEnableEnableDisable: restoring mock  "
                        + "satellite service (6)");
        sMockSatelliteServiceManager.resetSatelliteService();
        assertTrue(sMockSatelliteServiceManager.connectSatelliteService());
        sMockSatelliteServiceManager.setShouldRespondEnableRequest(true);
        assertTrue(
                sMockSatelliteServiceManager.setSatelliteControllerTimeoutDuration(
                        true, TIMEOUT_TYPE_WAIT_FOR_SATELLITE_ENABLING_RESPONSE, 0));

        // Enable satellite should succeed
        logd(
                "testRequestSatelliteEnabled_ModemCrashDuringEnableEnableDisable: enabling"
                        + " satellite (7)");
        callback.clearModemStates();
        requestSatelliteEnabled(true);
        assertTrue(callback.waitUntilResult(2));
        assertEquals(2, callback.getTotalCountOfModemStates());
        assertEquals(
                SatelliteManager.SATELLITE_MODEM_STATE_ENABLING_SATELLITE,
                callback.getModemState(0));
        assertEquals(
                SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED, callback.getModemState(1));
        assertTrue(isSatelliteEnabled());

        assertTrue(sMockSatelliteServiceManager.stopExternalSatelliteService());
        assertTrue(sMockSatelliteServiceManager.waitForExternalSatelliteServiceDisconnected(1));
        sSatelliteManager.unregisterForModemStateChanged(callback);
        sMockSatelliteServiceManager.clearSatelliteEnableRequestQueues();
        revokeSatellitePermission();
    }

    @Test
    public void testDemoSimulator() {
        logd("testDemoSimulator: start");

        grantSatellitePermission();
        assertTrue(isSatelliteProvisioned());
        assertTrue(isSatelliteEnabled());

        SatelliteModemStateCallbackTest stateCallback = new SatelliteModemStateCallbackTest();
        sSatelliteManager.registerForModemStateChanged(
                getContext().getMainExecutor(), stateCallback);
        assertTrue(stateCallback.waitUntilResult(1));

        NtnSignalStrengthCallbackTest ntnSignalStrengthCallback =
                new NtnSignalStrengthCallbackTest();
        /* register callback for non-terrestrial network signal strength changed event */
        sSatelliteManager.registerForNtnSignalStrengthChanged(
                getContext().getMainExecutor(), ntnSignalStrengthCallback);

        assertTrue(
                sMockSatelliteServiceManager.setSatelliteControllerTimeoutDuration(
                        false, TIMEOUT_TYPE_DEMO_POINTING_ALIGNED_DURATION_MILLIS, 5));
        assertTrue(
                sMockSatelliteServiceManager.setSatelliteControllerTimeoutDuration(
                        false, TIMEOUT_TYPE_DEMO_POINTING_NOT_ALIGNED_DURATION_MILLIS, 10));

        try {
            logd("testDemoSimulator: Disable satellite");
            requestSatelliteEnabled(false);
            assertTrue(stateCallback.waitUntilModemOff());
            assertFalse(isSatelliteEnabled());
            stateCallback.clearModemStates();

            logd("testDemoSimulator: Enable satellite for demo mode");
            stateCallback.clearModemStates();
            ntnSignalStrengthCallback.drainPermits();
            requestSatelliteEnabledForDemoMode(true);
            assertTrue(stateCallback.waitUntilResult(2));
            assertTrue(isSatelliteEnabled());
            assertTrue(ntnSignalStrengthCallback.waitUntilResult(1));
            assertEquals(
                    NtnSignalStrength.NTN_SIGNAL_STRENGTH_NONE,
                    ntnSignalStrengthCallback.mNtnSignalStrength.getLevel());

            logd("testDemoSimulator: Set device aligned with satellite");
            stateCallback.clearModemStates();
            ntnSignalStrengthCallback.drainPermits();
            sSatelliteManager.setDeviceAlignedWithSatellite(true);
            assertTrue(stateCallback.waitUntilResult(1));
            assertTrue(ntnSignalStrengthCallback.waitUntilResult(1));
            assertEquals(
                    NtnSignalStrength.NTN_SIGNAL_STRENGTH_MODERATE,
                    ntnSignalStrengthCallback.mNtnSignalStrength.getLevel());

            logd("testDemoSimulator: Set device not aligned with satellite");
            stateCallback.clearModemStates();
            ntnSignalStrengthCallback.drainPermits();
            sSatelliteManager.setDeviceAlignedWithSatellite(false);
            assertTrue(stateCallback.waitUntilResult(1));
            assertTrue(ntnSignalStrengthCallback.waitUntilResult(1));
            assertEquals(
                    NtnSignalStrength.NTN_SIGNAL_STRENGTH_NONE,
                    ntnSignalStrengthCallback.mNtnSignalStrength.getLevel());

            logd("testDemoSimulator: Disable satellite for demo mode");
            stateCallback.clearModemStates();
            requestSatelliteEnabledForDemoMode(false);
            assertTrue(stateCallback.waitUntilResult(2));
            assertFalse(isSatelliteEnabled());
        } finally {
            assertTrue(
                    sMockSatelliteServiceManager.setSatelliteControllerTimeoutDuration(
                            true, TIMEOUT_TYPE_DEMO_POINTING_ALIGNED_DURATION_MILLIS, 0));
            assertTrue(
                    sMockSatelliteServiceManager.setSatelliteControllerTimeoutDuration(
                            true, TIMEOUT_TYPE_DEMO_POINTING_NOT_ALIGNED_DURATION_MILLIS, 0));

            sSatelliteManager.unregisterForNtnSignalStrengthChanged(ntnSignalStrengthCallback);
            sSatelliteManager.unregisterForModemStateChanged(stateCallback);
            revokeSatellitePermission();
        }
    }

    private void sendDatagramWithoutResponse() {
        SatelliteTransmissionUpdateCallbackTest transmissionUpdateCallback =
                startTransmissionUpdates();

        LinkedBlockingQueue<Integer> resultListener = new LinkedBlockingQueue<>(1);
        String mText = "This is a test datagram message from user";
        SatelliteDatagram datagram = new SatelliteDatagram(mText.getBytes());

        transmissionUpdateCallback.clearSendDatagramRequested();
        sSatelliteManager.sendDatagram(
                DATAGRAM_TYPE_SOS_MESSAGE,
                datagram,
                true,
                getContext().getMainExecutor(),
                resultListener::offer);
        assertTrue(transmissionUpdateCallback.waitUntilOnSendDatagramRequested(1));
        assertEquals(1, transmissionUpdateCallback.getNumOfSendDatagramRequestedChanges());
        assertEquals(
                DATAGRAM_TYPE_SOS_MESSAGE,
                transmissionUpdateCallback.getSendDatagramRequestedType(0));

        Integer errorCode;
        try {
            errorCode = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail(
                    "sendDatagramWithoutResponse: Got InterruptedException in waiting"
                            + " for the sendDatagram result code, ex="
                            + ex);
            return;
        }
        assertNull(errorCode);

        // Expected datagram transfer state transitions: IDLE -> WAITING_FOR_CONNECTED
        assertTrue(transmissionUpdateCallback.waitUntilOnSendDatagramStateChanged(1));
        assertThat(transmissionUpdateCallback.getNumOfSendDatagramStateChanges()).isEqualTo(1);
        assertThat(transmissionUpdateCallback.getSendDatagramStateChange(0))
                .isEqualTo(
                        new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                                SatelliteManager
                                        .SATELLITE_DATAGRAM_TRANSFER_STATE_WAITING_TO_CONNECT,
                                1,
                                SatelliteManager.SATELLITE_RESULT_SUCCESS));

        stopTransmissionUpdates(transmissionUpdateCallback);
    }

    private void verifyNbIotStateTransitionsWithSendingOnConnected(
            @NonNull SatelliteModemStateCallbackTest callback, boolean moveToIdleState) {
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED, callback.modemState);
        callback.clearModemStates();

        SatelliteTransmissionUpdateCallbackTest transmissionUpdateCallback =
                startTransmissionUpdates();

        // Move satellite to CONNECTED state
        sMockSatelliteServiceManager.sendOnSatelliteModemStateChanged(
                SatelliteManager.SATELLITE_MODEM_STATE_CONNECTED);

        int expectedNumberOfEvents = moveToIdleState ? 4 : 3;
        assertTrue(callback.waitUntilResult(expectedNumberOfEvents));
        assertEquals(expectedNumberOfEvents, callback.getTotalCountOfModemStates());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_CONNECTED, callback.getModemState(0));
        assertEquals(
                SatelliteManager.SATELLITE_MODEM_STATE_DATAGRAM_TRANSFERRING,
                callback.getModemState(1));
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_CONNECTED, callback.getModemState(2));
        if (moveToIdleState) {
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_IDLE, callback.getModemState(3));
        }

        // Expected datagram transfer state transitions: WAITING_FOR_CONNECTED -> SENDING
        // -> SEND_SUCCESS -> IDLE
        assertTrue(transmissionUpdateCallback.waitUntilOnSendDatagramStateChanged(3));
        assertThat(transmissionUpdateCallback.getNumOfSendDatagramStateChanges()).isEqualTo(3);
        assertThat(transmissionUpdateCallback.getSendDatagramStateChange(0))
                .isEqualTo(
                        new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                                SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SENDING,
                                1,
                                SatelliteManager.SATELLITE_RESULT_SUCCESS));
        assertThat(transmissionUpdateCallback.getSendDatagramStateChange(1))
                .isEqualTo(
                        new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                                SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SEND_SUCCESS,
                                0,
                                SatelliteManager.SATELLITE_RESULT_SUCCESS));
        assertThat(transmissionUpdateCallback.getSendDatagramStateChange(2))
                .isEqualTo(
                        new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                                SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                                0,
                                SatelliteManager.SATELLITE_RESULT_SUCCESS));

        stopTransmissionUpdates(transmissionUpdateCallback);
    }

    private void verifyNbIotStateTransitionsWithTransferringFailureOnIdle(
            @NonNull SatelliteModemStateCallbackTest callback) {
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_IDLE, callback.modemState);

        SatelliteTransmissionUpdateCallbackTest transmissionUpdateCallback =
                startTransmissionUpdates();

        // Test sending failure
        LinkedBlockingQueue<Integer> resultListener = new LinkedBlockingQueue<>(1);
        String mText = "This is a test datagram message from user";
        SatelliteDatagram datagram = new SatelliteDatagram(mText.getBytes());

        callback.clearModemStates();
        sMockSatelliteServiceManager.setDatagramControllerTimeoutDuration(
                false, DatagramController.TIMEOUT_TYPE_DATAGRAM_WAIT_FOR_CONNECTED_STATE, 1000);
        // Return failure for the request to disable cellular scanning when exiting IDLE state.
        sMockSatelliteServiceManager.setEnableCellularScanningErrorCode(
                SatelliteManager.SATELLITE_RESULT_SERVICE_ERROR);
        sSatelliteManager.sendDatagram(
                DATAGRAM_TYPE_SOS_MESSAGE,
                datagram,
                true,
                getContext().getMainExecutor(),
                resultListener::offer);

        assertFalse(callback.waitUntilResult(1));
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_IDLE, callback.modemState);

        // Expected datagram transfer state transitions: IDLE -> WAITING_FOR_CONNECTED -> FAILED
        // -> IDLE.
        assertTrue(transmissionUpdateCallback.waitUntilOnSendDatagramStateChanged(3));
        assertThat(transmissionUpdateCallback.getNumOfSendDatagramStateChanges()).isEqualTo(3);
        assertThat(transmissionUpdateCallback.getSendDatagramStateChange(0))
                .isEqualTo(
                        new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                                SatelliteManager
                                        .SATELLITE_DATAGRAM_TRANSFER_STATE_WAITING_TO_CONNECT,
                                1,
                                SatelliteManager.SATELLITE_RESULT_SUCCESS));
        assertThat(transmissionUpdateCallback.getSendDatagramStateChange(1))
                .isEqualTo(
                        new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                                SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SEND_FAILED,
                                1,
                                SatelliteManager.SATELLITE_RESULT_NOT_REACHABLE));
        assertThat(transmissionUpdateCallback.getSendDatagramStateChange(2))
                .isEqualTo(
                        new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                                SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                                0,
                                SatelliteManager.SATELLITE_RESULT_SUCCESS));

        // Datagram wait for connected state timer should have timed out and the send request should
        // have been aborted.
        Integer errorCode;
        try {
            errorCode = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail(
                    "verifyNbIotStateTransitionsWithTransferringFailureOnIdle: Got "
                            + "InterruptedException in waiting for the sendDatagram result code"
                            + ", ex="
                            + ex);
            return;
        }
        assertNotNull(errorCode);
        assertEquals(SatelliteManager.SATELLITE_RESULT_NOT_REACHABLE, (long) errorCode);

        // Test receiving failure
        resultListener.clear();
        sMockSatelliteServiceManager.setEnableCellularScanningErrorCode(
                SatelliteResult.SATELLITE_RESULT_ERROR);
        callback.clearModemStates();
        sSatelliteManager.pollPendingDatagrams(
                getContext().getMainExecutor(), resultListener::offer);

        assertFalse(callback.waitUntilResult(1));
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_IDLE, callback.modemState);

        assertTrue(transmissionUpdateCallback.waitUntilOnReceiveDatagramStateChanged(3));
        assertThat(transmissionUpdateCallback.getNumOfReceiveDatagramStateChanges()).isEqualTo(3);
        assertThat(transmissionUpdateCallback.getReceiveDatagramStateChange(0))
                .isEqualTo(
                        new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                                SatelliteManager
                                        .SATELLITE_DATAGRAM_TRANSFER_STATE_WAITING_TO_CONNECT,
                                0,
                                SatelliteManager.SATELLITE_RESULT_SUCCESS));
        assertThat(transmissionUpdateCallback.getReceiveDatagramStateChange(1))
                .isEqualTo(
                        new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                                SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVE_FAILED,
                                0,
                                SatelliteManager.SATELLITE_RESULT_NOT_REACHABLE));
        assertThat(transmissionUpdateCallback.getReceiveDatagramStateChange(2))
                .isEqualTo(
                        new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                                SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                                0,
                                SatelliteManager.SATELLITE_RESULT_SUCCESS));

        // Datagram wait for connected state timer should have timed out and the poll request should
        // have been aborted.
        try {
            errorCode = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail(
                    "verifyNbIotStateTransitionsWithTransferringFailureOnIdle: Got "
                            + "InterruptedException in waiting for the pollPendingDatagrams result"
                            + " code, ex="
                            + ex);
            return;
        }
        assertNotNull(errorCode);
        assertEquals(SatelliteManager.SATELLITE_RESULT_NOT_REACHABLE, (long) errorCode);

        sMockSatelliteServiceManager.setEnableCellularScanningErrorCode(
                SatelliteManager.SATELLITE_RESULT_SUCCESS);
        sMockSatelliteServiceManager.setDatagramControllerTimeoutDuration(
                true, DatagramController.TIMEOUT_TYPE_DATAGRAM_WAIT_FOR_CONNECTED_STATE, 0);
        stopTransmissionUpdates(transmissionUpdateCallback);
    }

    private void verifyNbIotStateTransitionsWithSendingAborted(
            @NonNull SatelliteModemStateCallbackTest callback) {
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_IDLE, callback.modemState);

        SatelliteTransmissionUpdateCallbackTest transmissionUpdateCallback =
                startTransmissionUpdates();

        LinkedBlockingQueue<Integer> resultListener = new LinkedBlockingQueue<>(1);
        String mText = "This is a test datagram message from user";
        SatelliteDatagram datagram = new SatelliteDatagram(mText.getBytes());

        callback.clearModemStates();
        sSatelliteManager.sendDatagram(
                DATAGRAM_TYPE_SOS_MESSAGE,
                datagram,
                true,
                getContext().getMainExecutor(),
                resultListener::offer);

        Integer errorCode;
        try {
            errorCode = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail(
                    "verifyNbIotStateTransitionsWithSendingAborted: Got InterruptedException"
                            + " in waiting for the sendDatagram result code, ex="
                            + ex);
            return;
        }
        assertNull(errorCode);

        assertTrue(callback.waitUntilResult(1));
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED, callback.modemState);

        // Turn off satellite modem. The send request should be aborted.
        callback.clearModemStates();
        sMockSatelliteServiceManager.sendOnSatelliteModemStateChanged(
                SatelliteManager.SATELLITE_MODEM_STATE_OFF);

        try {
            errorCode = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail(
                    "verifyNbIotStateTransitionsWithSendingAborted: Got InterruptedException"
                            + " in waiting for the sendDatagram result code, ex="
                            + ex);
            return;
        }
        assertNotNull(errorCode);
        assertEquals(SATELLITE_RESULT_REQUEST_ABORTED, (long) errorCode);

        // Expected datagram transfer state transitions: IDLE -> WAITING_FOR_CONNECTED -> FAILED
        // -> IDLE.
        assertTrue(transmissionUpdateCallback.waitUntilOnSendDatagramStateChanged(3));
        assertThat(transmissionUpdateCallback.getNumOfSendDatagramStateChanges()).isEqualTo(3);
        assertThat(transmissionUpdateCallback.getSendDatagramStateChange(0))
                .isEqualTo(
                        new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                                SatelliteManager
                                        .SATELLITE_DATAGRAM_TRANSFER_STATE_WAITING_TO_CONNECT,
                                1,
                                SatelliteManager.SATELLITE_RESULT_SUCCESS));
        assertThat(transmissionUpdateCallback.getSendDatagramStateChange(1))
                .isEqualTo(
                        new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                                SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SEND_FAILED,
                                1,
                                SATELLITE_RESULT_REQUEST_ABORTED));
        assertThat(transmissionUpdateCallback.getSendDatagramStateChange(2))
                .isEqualTo(
                        new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                                SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                                0,
                                SatelliteManager.SATELLITE_RESULT_SUCCESS));

        assertTrue(callback.waitUntilResult(1));
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback.modemState);
        stopTransmissionUpdates(transmissionUpdateCallback);
    }

    private void verifyNbIotStateTransitionsWithReceivingOnIdle(
            @NonNull SatelliteModemStateCallbackTest callback, boolean moveToIdleState) {
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_IDLE, callback.modemState);

        SatelliteTransmissionUpdateCallbackTest transmissionUpdateCallback =
                startTransmissionUpdates();

        // Verify state transitions: IDLE -> NOT_CONNECTED
        callback.clearModemStates();
        sMockSatelliteServiceManager.clearPollPendingDatagramPermits();
        sMockSatelliteServiceManager.sendOnPendingDatagrams();
        assertFalse(sMockSatelliteServiceManager.waitForEventOnPollPendingSatelliteDatagrams(1));
        assertTrue(callback.waitUntilResult(1));
        assertEquals(1, callback.getTotalCountOfModemStates());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED, callback.modemState);
        // Expected datagram transfer state transitions: IDLE -> WAITING_FOR_CONNECTED
        assertTrue(transmissionUpdateCallback.waitUntilOnReceiveDatagramStateChanged(1));
        assertThat(transmissionUpdateCallback.getNumOfReceiveDatagramStateChanges()).isEqualTo(1);
        assertThat(transmissionUpdateCallback.getReceiveDatagramStateChange(0))
                .isEqualTo(
                        new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                                SatelliteManager
                                        .SATELLITE_DATAGRAM_TRANSFER_STATE_WAITING_TO_CONNECT,
                                0,
                                SatelliteManager.SATELLITE_RESULT_SUCCESS));

        // Verify state transitions: NOT_CONNECTED -> CONNECTED -> TRANSFERRING
        callback.clearModemStates();
        transmissionUpdateCallback.clearReceiveDatagramStateChanges();
        sMockSatelliteServiceManager.sendOnSatelliteModemStateChanged(
                SatelliteManager.SATELLITE_MODEM_STATE_CONNECTED);
        assertTrue(callback.waitUntilResult(2));
        assertEquals(2, callback.getTotalCountOfModemStates());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_CONNECTED, callback.getModemState(0));
        assertEquals(
                SatelliteManager.SATELLITE_MODEM_STATE_DATAGRAM_TRANSFERRING,
                callback.getModemState(1));
        // Telephony should send the request pollPendingDatagrams to modem
        assertTrue(sMockSatelliteServiceManager.waitForEventOnPollPendingSatelliteDatagrams(1));

        // Expected datagram transfer state transitions: WAITING_FOR_CONNECTED -> RECEIVING
        assertTrue(transmissionUpdateCallback.waitUntilOnReceiveDatagramStateChanged(1));
        assertThat(transmissionUpdateCallback.getNumOfReceiveDatagramStateChanges()).isEqualTo(1);
        assertThat(transmissionUpdateCallback.getReceiveDatagramStateChange(0))
                .isEqualTo(
                        new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                                SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVING,
                                0,
                                SatelliteManager.SATELLITE_RESULT_SUCCESS));

        verifyNbIotStateTransitionsWithDatagramReceivedOnTransferring(
                callback, moveToIdleState, transmissionUpdateCallback);
        stopTransmissionUpdates(transmissionUpdateCallback);
    }

    private void verifyNbIotStateTransitionsWithDatagramReceivedOnTransferring(
            @NonNull SatelliteModemStateCallbackTest callback,
            boolean moveToIdleState,
            SatelliteTransmissionUpdateCallbackTest transmissionUpdateCallback) {
        assertEquals(
                SatelliteManager.SATELLITE_MODEM_STATE_DATAGRAM_TRANSFERRING, callback.modemState);

        SatelliteDatagramCallbackTest satelliteDatagramCallback =
                new SatelliteDatagramCallbackTest();
        sSatelliteManager.registerForIncomingDatagram(
                getContext().getMainExecutor(), satelliteDatagramCallback);

        String receivedText = "This is a test datagram message from satellite";
        android.telephony.satellite.stub.SatelliteDatagram receivedDatagram =
                new android.telephony.satellite.stub.SatelliteDatagram();
        receivedDatagram.data = receivedText.getBytes();

        callback.clearModemStates();
        transmissionUpdateCallback.clearReceiveDatagramStateChanges();
        sMockSatelliteServiceManager.sendOnSatelliteDatagramReceived(receivedDatagram, 0);

        assertTrue(satelliteDatagramCallback.waitUntilResult(1));
        assertArrayEquals(
                satelliteDatagramCallback.mDatagram.getSatelliteDatagram(),
                receivedText.getBytes());

        int expectedNumberOfEvents = moveToIdleState ? 2 : 1;
        assertTrue(callback.waitUntilResult(expectedNumberOfEvents));
        assertEquals(expectedNumberOfEvents, callback.getTotalCountOfModemStates());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_CONNECTED, callback.getModemState(0));
        if (moveToIdleState) {
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_IDLE, callback.getModemState(1));
        }

        // Expected datagram transfer state transitions: RECEIVING -> RECEIVE_SUCCESS -> IDLE
        assertTrue(transmissionUpdateCallback.waitUntilOnReceiveDatagramStateChanged(2));
        assertThat(transmissionUpdateCallback.getNumOfReceiveDatagramStateChanges()).isEqualTo(2);
        assertThat(transmissionUpdateCallback.getReceiveDatagramStateChange(0))
                .isEqualTo(
                        new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                                SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVE_SUCCESS,
                                0,
                                SatelliteManager.SATELLITE_RESULT_SUCCESS));
        assertThat(transmissionUpdateCallback.getReceiveDatagramStateChange(1))
                .isEqualTo(
                        new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                                SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                                0,
                                SatelliteManager.SATELLITE_RESULT_SUCCESS));

        sSatelliteManager.unregisterForIncomingDatagram(satelliteDatagramCallback);
    }

    protected void verifyNbIotStateTransitionsWithReceivingOnConnected(
            @NonNull SatelliteModemStateCallbackTest callback) {
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_CONNECTED, callback.modemState);

        SatelliteTransmissionUpdateCallbackTest transmissionUpdateCallback =
                startTransmissionUpdates();

        SatelliteDatagramCallbackTest satelliteDatagramCallback =
                new SatelliteDatagramCallbackTest();
        sSatelliteManager.registerForIncomingDatagram(
                getContext().getMainExecutor(), satelliteDatagramCallback);

        String receivedText = "This is a test datagram message from satellite";
        android.telephony.satellite.stub.SatelliteDatagram receivedDatagram =
                new android.telephony.satellite.stub.SatelliteDatagram();
        receivedDatagram.data = receivedText.getBytes();

        // Verify state transitions: CONNECTED -> TRANSFERRING -> CONNECTED
        callback.clearModemStates();
        transmissionUpdateCallback.clearReceiveDatagramStateChanges();
        sMockSatelliteServiceManager.sendOnSatelliteDatagramReceived(receivedDatagram, 0);

        assertTrue(satelliteDatagramCallback.waitUntilResult(1));
        assertArrayEquals(
                satelliteDatagramCallback.mDatagram.getSatelliteDatagram(),
                receivedText.getBytes());

        int expectedNumberOfEvents = 2;
        assertTrue(callback.waitUntilResult(expectedNumberOfEvents));
        assertEquals(expectedNumberOfEvents, callback.getTotalCountOfModemStates());
        assertEquals(
                SatelliteManager.SATELLITE_MODEM_STATE_DATAGRAM_TRANSFERRING,
                callback.getModemState(0));
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_CONNECTED, callback.getModemState(1));

        // Expected datagram transfer state transitions: IDLE -> RECEIVE_SUCCESS -> IDLE
        assertTrue(transmissionUpdateCallback.waitUntilOnReceiveDatagramStateChanged(2));
        assertThat(transmissionUpdateCallback.getNumOfReceiveDatagramStateChanges()).isEqualTo(2);
        assertThat(transmissionUpdateCallback.getReceiveDatagramStateChange(0))
                .isEqualTo(
                        new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                                SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVE_SUCCESS,
                                0,
                                SatelliteManager.SATELLITE_RESULT_SUCCESS));
        assertThat(transmissionUpdateCallback.getReceiveDatagramStateChange(1))
                .isEqualTo(
                        new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                                SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                                0,
                                SatelliteManager.SATELLITE_RESULT_SUCCESS));

        sSatelliteManager.unregisterForIncomingDatagram(satelliteDatagramCallback);
        stopTransmissionUpdates(transmissionUpdateCallback);
    }

    @Test
    public void testRequestSelectedNbIotSatelliteSubscriptionId() {
        logd("testRequestSelectedNbIotSatelliteSubscriptionId:");
        grantSatellitePermission();
        try {
            Pair<Integer, Integer> pairResult = requestSelectedNbIotSatelliteSubscriptionId();
            assertNotEquals(SubscriptionManager.INVALID_SUBSCRIPTION_ID, (long) pairResult.first);
        } finally {
            revokeSatellitePermission();
        }
    }
}
