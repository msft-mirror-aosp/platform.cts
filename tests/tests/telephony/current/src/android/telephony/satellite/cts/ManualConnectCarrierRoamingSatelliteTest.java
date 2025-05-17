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

package android.telephony.satellite.cts;

import static android.telephony.mockmodem.MockSimService.MOCK_SIM_PROFILE_ID_TWN_CHT;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.radio.RadioError;
import android.os.PersistableBundle;
import android.os.SystemClock;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.Telephony;
import android.telephony.CarrierConfigManager;
import android.telephony.SmsManager;
import android.telephony.SubscriptionManager;
import android.telephony.cts.util.DefaultSmsAppHelper;
import android.telephony.satellite.SatelliteManager;
import android.telephony.satellite.stub.SatelliteModemState;
import android.telephony.satellite.stub.SatelliteResult;
import android.text.TextUtils;
import android.util.Pair;

import com.android.internal.telephony.RILConstants;
import com.android.internal.telephony.flags.Flags;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

public class ManualConnectCarrierRoamingSatelliteTest extends CarrierRoamingSatelliteTestBase {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final String TAG = "ManualConnectCarrierRoamingSatelliteTest";
    private static final int ESOS_SLOT_ID = SLOT_ID_0;
    private static final int ESOS_SIM_PROFILE_ID = MOCK_SIM_PROFILE_ID_TWN_CHT;
    private static final String ESOS_PHONE_NUMBER = PHONE_NUMBER_0;
    private static final String SMS_SEND_ACTION = "CTS_SMS_SEND_ACTION";
    private static final String TEST_DEST_ADDR = "1234567890";

    /**
     * Setup before all tests.
     * @throws Exception exception
     */
    @BeforeClass
    public static void beforeAllTests() throws Exception {
        logd(TAG, "beforeAllTests");
        sActiveSubscriptionRequired = false;
        if (!shouldTestSatelliteWithMockService()) return;

        beforeAllCarrierRoamingTestsBase();
        setUpManualConnectTestEnvironment(
            ESOS_SLOT_ID, ESOS_SIM_PROFILE_ID, ESOS_PHONE_NUMBER, true, true);
    }

    /**
     * Cleanup resources after all tests.
     * @throws Exception exception
     */
    @AfterClass
    public static void afterAllTests() throws Exception {
        logd(TAG, "afterAllTests");
        if (!shouldTestSatelliteWithMockService()) return;
        cleanUpManualConnectTestEnvironment(ESOS_SLOT_ID, ESOS_SIM_PROFILE_ID);
        afterAllCarrierRoamingTestsBase();
    }

    @Before
    public void setUp() throws Exception {
        logd(TAG, "setUp()");
        if (!shouldTestManualConnectCarrierRoaming()) return;
        assumeTrue(sMockSatelliteServiceManager != null);

        moveSimToInService(ESOS_SLOT_ID, ESOS_SIM_PROFILE_ID);
        assertTrue(sMockSatelliteServiceManager.setSatelliteIgnoreCellularServiceState(true));
        assertTrue(sMockSatelliteServiceManager.setSatelliteTnScanningSupport(false, false, true));
        assertTrue(sMockSatelliteServiceManager.setSupportDisableSatelliteWhileEnableInProgress(
                false, true));
        sMockSatelliteServiceManager.setErrorCode(SatelliteResult.SATELLITE_RESULT_SUCCESS);
        sMockSatelliteServiceManager.setWaitToSend(false);
        sMockSatelliteServiceManager.setShouldRespondTelephony(true);
        sMockSatelliteServiceManager.setShouldRespondEnableRequest(true);
        sMockSatelliteServiceManager.mIsPointingUiOverridden = false;
        setUpSatelliteAccessAllowedAtDefaultTestLocation();
    }

    @After
    public void tearDown() throws Exception {
        logd(TAG, "tearDown()");
        if (!shouldTestManualConnectCarrierRoaming()) return;
        assumeTrue(sMockSatelliteServiceManager != null);

        assertTrue(sMockSatelliteServiceManager.setSatelliteIgnoreCellularServiceState(false));
        assertTrue(sMockSatelliteServiceManager.setSatelliteTnScanningSupport(true, false, false));
        assertTrue(sMockSatelliteServiceManager.setSupportDisableSatelliteWhileEnableInProgress(
                true, false));

        // Move satellite to off state to clean up all pending resources
        // and reset telephony states.
        moveSatelliteToOffState();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CARRIER_ROAMING_NB_IOT_NTN)
    public void testCarrierRoamingNtnEligible() throws Exception {
        if (!shouldTestManualConnectCarrierRoaming()) return;

        CarrierRoamingNtnListenerTest carrierRoamingNtnListener =
            new CarrierRoamingNtnListenerTest();
        ServiceStateListenerTest serviceStateListener = registerServiceStateListener();
        adoptShellIdentity();
        boolean originalWifiState = sWifiManager.isWifiEnabled();
        logd(TAG, "originalWifiState: " + originalWifiState);

        try {
            // Make the device is connected to a TN cell
            // Disable satellite PLMNs so that the network is considered as a TN network
            disableSatellitePlmns(ESOS_SLOT_ID);
            // Move device to out of service state
            sMockModemManager.changeNetworkService(ESOS_SLOT_ID, ESOS_SIM_PROFILE_ID, false);
            assertTrue(serviceStateListener.waitUntilOutOfService());
            // Move device to in service state
            sMockModemManager.changeNetworkService(ESOS_SLOT_ID, ESOS_SIM_PROFILE_ID, true);
            assertTrue(serviceStateListener.waitUntilInService());

            // Get NTN eligibility immediately after registering
            sTelephonyManager.registerTelephonyCallback(
                getContext().getMainExecutor(), carrierRoamingNtnListener);
            assertTrue(carrierRoamingNtnListener.waitForNtnEligible(1));
            assertFalse(carrierRoamingNtnListener.getNtnEligible());
            carrierRoamingNtnListener.clearModeChanges();

            // Enable satellite PLMNs to make sure the device is registered to a NTN
            enableCarrierRoamingSatelliteConfigs(ESOS_SLOT_ID,
                CarrierConfigManager.CARRIER_ROAMING_NTN_CONNECT_MANUAL);

            if (originalWifiState) {
                logd(TAG, "Disabling wifi");
                sWifiManager.setWifiEnabled(false);
                sWifiStateReceiver.setWifiExpectedState(false);
                assertTrue(sWifiStateReceiver.waitUntilWifiStateChanged());
                carrierRoamingNtnListener.clearModeChanges();
            }

            // Network is lost
            logd(TAG, "Move device to out of service state");
            sMockModemManager.changeNetworkService(ESOS_SLOT_ID, ESOS_SIM_PROFILE_ID, false);
            assertTrue(serviceStateListener.waitUntilOutOfService());
            // The disconnected state will be processed only after hysteresis timeout
            assertFalse(carrierRoamingNtnListener.waitForNtnEligible(1));
            carrierRoamingNtnListener.clearModeChanges();

            // Callback is received after hysteresis timeout
            assertTrue(carrierRoamingNtnListener.waitForNtnEligible(1));
            assertTrue(carrierRoamingNtnListener.getNtnEligible());
        } finally {
            if (originalWifiState) {
                logd(TAG, "Restroing wifi enabled state");
                sWifiManager.setWifiEnabled(true);
                sWifiStateReceiver.setWifiExpectedState(true);
                assertTrue(sWifiStateReceiver.waitUntilWifiStateChanged());
            }
            sTelephonyManager.unregisterTelephonyCallback(carrierRoamingNtnListener);
            sTelephonyManager.unregisterTelephonyCallback(serviceStateListener);
            dropShellIdentity();
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CARRIER_ROAMING_NB_IOT_NTN)
    public void testSendSms_success() throws Exception {
        logd(TAG, "testSendSms_success");
        if (!shouldTestManualConnectCarrierRoaming()) return;

        // Test non-default SMS app
        sendSms(TEST_DEST_ADDR, Activity.RESULT_OK);

        // Test default SMS app
        DefaultSmsAppHelper.ensureDefaultSmsApp();
        sendSms(TEST_DEST_ADDR, Activity.RESULT_OK);
        DefaultSmsAppHelper.stopBeingDefaultSmsApp();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CARRIER_ROAMING_NB_IOT_NTN)
    public void testReceiveSms_success() throws Exception {
        logd(TAG, "testReceiveSms_success");
        if (!shouldTestManualConnectCarrierRoaming()) return;

        receiveSmsSuccessfully();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CARRIER_ROAMING_NB_IOT_NTN)
    public void testSendSms_failure() throws Exception {
        logd(TAG, "testSendSms_failure");
        if (!shouldTestManualConnectCarrierRoaming()) return;

        // Set the SMS error code and RIL error code
        sMockModemManager.setSendSmsErrorCode(
            ESOS_SLOT_ID, RadioError.NETWORK_REJECT, RILConstants.NETWORK_ERR);
        try {
            // Test non-default SMS app
            sendSms(TEST_DEST_ADDR, SmsManager.RESULT_RIL_NETWORK_ERR);

            // Test default SMS app
            DefaultSmsAppHelper.ensureDefaultSmsApp();
            sendSms(TEST_DEST_ADDR, SmsManager.RESULT_RIL_NETWORK_ERR);
            DefaultSmsAppHelper.stopBeingDefaultSmsApp();
        } finally {
            // Reset the SMS error code and RIL error code
            sMockModemManager.setSendSmsErrorCode(
                ESOS_SLOT_ID, RadioError.NONE, RILConstants.SUCCESS);
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CARRIER_ROAMING_NB_IOT_NTN)
    public void testRequestSatelliteDisplayName() {
        logd("testRequestSatelliteDisplayName: sEsosSubId=" + sEsosSubId);
        if (!shouldTestManualConnectCarrierRoaming()) return;

        grantSatellitePermission();
        try {
            Pair<CharSequence, Integer> pairResult = requestSatelliteDisplayName();
            if (pairResult == null) {
                fail("requestSatelliteDisplayName: null");
            }
            assertNull(pairResult.second);
            if (TextUtils.isEmpty(pairResult.first)) {
                assumeTrue(sEsosSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID);

                String displayName = "Satellite";
                PersistableBundle bundle = new PersistableBundle();
                bundle.putString(
                        CarrierConfigManager.KEY_SATELLITE_DISPLAY_NAME_STRING, displayName);
                overrideCarrierConfig(sEsosSubId, bundle);

                pairResult = requestSatelliteDisplayName();
                if (pairResult == null) {
                    fail("requestSatelliteDisplayName: null");
                }
                assertTrue(TextUtils.equals(displayName, (CharSequence) pairResult.first));
                assertNull(pairResult.second);
            }
        } finally {
            revokeSatellitePermission();
        }
    }

    private static boolean shouldTestManualConnectCarrierRoaming() {
        if (!shouldTestSatelliteWithMockService()) return false;
        if (!isActiveSubId(sEsosSubId)) {
            logd(TAG, "Skip the test because the ESOS subId is not active.");
            return false;
        }
        return true;
    }

    private static void sendSms(String destAddr, int resultCode) throws Exception {
        logd(TAG, "sendSms destAddr:" + destAddr + ", resultCode:" + resultCode);

        // Satellite modem will be in NOT_CONNECTED state after being powered on
        enableSatelliteMode();

        // Register callbacks for sending SMS state changes
        SatelliteTransmissionUpdateCallbackTest transmissionUpdateCallback =
            startTransmissionUpdates();
        SmsMmsBroadcastReceiver sendReceiver = registerSmsMmsBroadcastReceiver(SMS_SEND_ACTION);
        PendingIntent sendPendingIntent = createSendPendingIntent();

        try {
            getSmsManager().sendTextMessage(destAddr, null,
                String.valueOf(SystemClock.elapsedRealtimeNanos()),
                sendPendingIntent, null);

            // Datagram transfer state should change from IDLE to WAITING_TO_CONNECT
            assertTrue(transmissionUpdateCallback.waitUntilOnSendDatagramStateChanged(1));
            assertThat(transmissionUpdateCallback.getNumOfSendDatagramStateChanges()).isEqualTo(1);
            assertThat(transmissionUpdateCallback.getSendDatagramStateChange(0)).isEqualTo(
                    new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                            SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_WAITING_TO_CONNECT,
                            1, SatelliteManager.SATELLITE_RESULT_SUCCESS));

            // Move satellite to IN_SERVICE state
            transmissionUpdateCallback.clearSendDatagramStateChanges();
            sMockSatelliteServiceManager.sendOnSatelliteModemStateChanged(
                    SatelliteModemState.SATELLITE_MODEM_STATE_IN_SERVICE);

            // Datagram transfer state should change from WAITING_TO_CONNECT to SENDING,
            // SEND_SUCCESS, and then IDLE
            assertTrue(transmissionUpdateCallback.waitUntilOnSendDatagramStateChanged(3));
            assertThat(transmissionUpdateCallback.getNumOfSendDatagramStateChanges()).isEqualTo(3);
            assertThat(transmissionUpdateCallback.getSendDatagramStateChange(0)).isEqualTo(
                    new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                            SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SENDING,
                            1, SatelliteManager.SATELLITE_RESULT_SUCCESS));
            if (resultCode == Activity.RESULT_OK) {
                assertThat(transmissionUpdateCallback.getSendDatagramStateChange(1)).isEqualTo(
                        new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                                SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SEND_SUCCESS,
                                0, SatelliteManager.SATELLITE_RESULT_SUCCESS));
            } else {
                assertThat(transmissionUpdateCallback.getSendDatagramStateChange(1)).isEqualTo(
                        new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                                SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SEND_FAILED,
                                0, SatelliteManager.SATELLITE_RESULT_NETWORK_ERROR));
            }
            assertThat(transmissionUpdateCallback.getSendDatagramStateChange(2)).isEqualTo(
                    new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                            SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                            0, SatelliteManager.SATELLITE_RESULT_SUCCESS));

            assertTrue(sendReceiver.waitForBroadcast(1));
            assertEquals(resultCode, sendReceiver.getResultCode());

            // Move satellite to off state to clean up all pending resources
            // and reset telephony satellite states.
            moveSatelliteToOffState();
        } finally {
            getContext().unregisterReceiver(sendReceiver);
            stopTransmissionUpdates(transmissionUpdateCallback);
        }
    }

    private static void receiveSmsSuccessfully() throws Exception {
        logd(TAG, "receiveSmsSuccessfully: starting...");

        // Satellite modem will be in NOT_CONNECTED state after being powered on
        enableSatelliteMode();

        // Register callbacks for receiving SMS state changes
        SatelliteTransmissionUpdateCallbackTest transmissionUpdateCallback =
            startTransmissionUpdates();
        SmsMmsBroadcastReceiver receiveReceiver =
            registerSmsMmsBroadcastReceiver(Telephony.Sms.Intents.SMS_RECEIVED_ACTION);
        try {
            // Set device not aligned to satellite
            sSatelliteManager.setDeviceAlignedWithSatellite(false);

            // Move satellite to IN_SERVICE state
            sMockSatelliteServiceManager.sendOnSatelliteModemStateChanged(
                    SatelliteModemState.SATELLITE_MODEM_STATE_IN_SERVICE);

            // MT SMS polling should not be triggered since device is not aligned to satellite
            assertFalse(transmissionUpdateCallback.waitUntilOnSendDatagramStateChanged(1));

            // Set device aligned to satellite
            sSatelliteManager.setDeviceAlignedWithSatellite(true);

            // MT SMS polling should be triggered, and send datagram transfer state should change from
            // IDLE to SENDING, SEND_SUCCESS, and then IDLE
            assertTrue(transmissionUpdateCallback.waitUntilOnSendDatagramStateChanged(3));
            assertThat(transmissionUpdateCallback.getNumOfSendDatagramStateChanges()).isEqualTo(3);
            SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument
                firstSendDatagramStateChange = transmissionUpdateCallback.getSendDatagramStateChange(0);
            assertThat(firstSendDatagramStateChange).isEqualTo(
                    new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                            SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SENDING,
                            1, SatelliteManager.SATELLITE_RESULT_SUCCESS));
            assertEquals(SatelliteManager.DATAGRAM_TYPE_CHECK_PENDING_INCOMING_SMS,
                firstSendDatagramStateChange.datagramType);
            assertThat(transmissionUpdateCallback.getSendDatagramStateChange(1)).isEqualTo(
                    new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                            SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SEND_SUCCESS,
                            0, SatelliteManager.SATELLITE_RESULT_SUCCESS));
            assertThat(transmissionUpdateCallback.getSendDatagramStateChange(2)).isEqualTo(
                    new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                            SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                            0, SatelliteManager.SATELLITE_RESULT_SUCCESS));

            // Trigger incoming SMS
            assertTrue(sMockModemManager.triggerIncomingSms(ESOS_SLOT_ID));

            // Receive datagram transfer state should change from IDLE to RECEIVING, RECEIVE_SUCCESS,
            // and then IDLE
            assertTrue(transmissionUpdateCallback.waitUntilOnReceiveDatagramStateChanged(3));
            assertThat(transmissionUpdateCallback.getNumOfReceiveDatagramStateChanges()).isEqualTo(3);
            assertThat(transmissionUpdateCallback.getReceiveDatagramStateChange(0)).isEqualTo(
                    new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                            SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVING,
                            0, SatelliteManager.SATELLITE_RESULT_SUCCESS));
            assertThat(transmissionUpdateCallback.getReceiveDatagramStateChange(1)).isEqualTo(
                    new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                            SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVE_SUCCESS,
                            0, SatelliteManager.SATELLITE_RESULT_SUCCESS));
            assertThat(transmissionUpdateCallback.getReceiveDatagramStateChange(2)).isEqualTo(
                    new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                            SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                            0, SatelliteManager.SATELLITE_RESULT_SUCCESS));

            // Receive SMS broadcast should be received
            assertTrue(receiveReceiver.waitForBroadcast(1));

            // Move satellite to off state to clean up all pending resources
            // and reset telephony satellite states.
            moveSatelliteToOffState();
        } finally {
            getContext().unregisterReceiver(receiveReceiver);
            stopTransmissionUpdates(transmissionUpdateCallback);
        }
    }

    private static SmsMmsBroadcastReceiver registerSmsMmsBroadcastReceiver(String action) {
        SmsMmsBroadcastReceiver smsReceiver = new SmsMmsBroadcastReceiver();
        smsReceiver.setAction(action);
        getContext().registerReceiver(smsReceiver, new IntentFilter(smsReceiver.getAction()),
                Context.RECEIVER_EXPORTED_UNAUDITED);
        return smsReceiver;
    }

    private static PendingIntent createSendPendingIntent() {
        Intent sendIntent = new Intent(SMS_SEND_ACTION).setPackage(getContext().getPackageName());
        return PendingIntent.getBroadcast(getContext(), 0,
                sendIntent, PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_MUTABLE);
    }
}
