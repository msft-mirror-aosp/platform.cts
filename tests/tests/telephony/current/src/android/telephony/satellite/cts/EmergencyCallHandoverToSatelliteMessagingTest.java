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

import static android.telephony.AccessNetworkConstants.AccessNetworkType.EUTRAN;
import static android.telephony.AccessNetworkConstants.AccessNetworkType.GERAN;
import static android.telephony.AccessNetworkConstants.AccessNetworkType.UTRAN;
import static android.telephony.CarrierConfigManager.ImsEmergency.KEY_CROSS_STACK_REDIAL_TIMER_SEC_INT;
import static android.telephony.CarrierConfigManager.ImsEmergency.KEY_EMERGENCY_CALL_SETUP_TIMER_ON_CURRENT_NETWORK_SEC_INT;
import static android.telephony.CarrierConfigManager.ImsEmergency.KEY_EMERGENCY_CDMA_PREFERRED_NUMBERS_STRING_ARRAY;
import static android.telephony.CarrierConfigManager.ImsEmergency.KEY_EMERGENCY_DOMAIN_PREFERENCE_INT_ARRAY;
import static android.telephony.CarrierConfigManager.ImsEmergency.KEY_EMERGENCY_DOMAIN_PREFERENCE_ROAMING_INT_ARRAY;
import static android.telephony.CarrierConfigManager.ImsEmergency.KEY_EMERGENCY_LTE_PREFERRED_AFTER_NR_FAILED_BOOL;
import static android.telephony.CarrierConfigManager.ImsEmergency.KEY_EMERGENCY_NETWORK_SCAN_TYPE_INT;
import static android.telephony.CarrierConfigManager.ImsEmergency.KEY_EMERGENCY_OVER_CS_ROAMING_SUPPORTED_ACCESS_NETWORK_TYPES_INT_ARRAY;
import static android.telephony.CarrierConfigManager.ImsEmergency.KEY_EMERGENCY_OVER_CS_SUPPORTED_ACCESS_NETWORK_TYPES_INT_ARRAY;
import static android.telephony.CarrierConfigManager.ImsEmergency.KEY_EMERGENCY_OVER_IMS_ROAMING_SUPPORTED_3GPP_NETWORK_TYPES_INT_ARRAY;
import static android.telephony.CarrierConfigManager.ImsEmergency.KEY_EMERGENCY_OVER_IMS_SUPPORTED_3GPP_NETWORK_TYPES_INT_ARRAY;
import static android.telephony.CarrierConfigManager.ImsEmergency.KEY_EMERGENCY_REQUIRES_IMS_REGISTRATION_BOOL;
import static android.telephony.CarrierConfigManager.ImsEmergency.KEY_EMERGENCY_REQUIRES_VOLTE_ENABLED_BOOL;
import static android.telephony.CarrierConfigManager.ImsEmergency.KEY_EMERGENCY_SCAN_TIMER_SEC_INT;
import static android.telephony.CarrierConfigManager.ImsEmergency.KEY_MAXIMUM_CELLULAR_SEARCH_TIMER_SEC_INT;
import static android.telephony.CarrierConfigManager.ImsEmergency.KEY_MAXIMUM_NUMBER_OF_EMERGENCY_TRIES_OVER_VOWIFI_INT;
import static android.telephony.CarrierConfigManager.ImsEmergency.KEY_PREFER_IMS_EMERGENCY_WHEN_VOICE_CALLS_ON_CS_BOOL;
import static android.telephony.CarrierConfigManager.ImsEmergency.KEY_QUICK_CROSS_STACK_REDIAL_TIMER_SEC_INT;
import static android.telephony.CarrierConfigManager.ImsEmergency.KEY_START_QUICK_CROSS_STACK_REDIAL_TIMER_WHEN_REGISTERED_BOOL;
import static android.telephony.CarrierConfigManager.ImsEmergency.REDIAL_TIMER_DISABLED;
import static android.telephony.CarrierConfigManager.ImsEmergency.SCAN_TYPE_NO_PREFERENCE;
import static android.telephony.CarrierConfigManager.ImsWfc.KEY_EMERGENCY_CALL_OVER_EMERGENCY_PDN_BOOL;
import static android.telephony.NetworkRegistrationInfo.REGISTRATION_STATE_HOME;
import static android.telephony.PreciseCallState.PRECISE_CALL_STATE_ACTIVE;
import static android.telephony.mockmodem.MockSimService.MOCK_SIM_PROFILE_ID_TWN_CHT;
import static android.telephony.mockmodem.MockSimService.MOCK_SIM_PROFILE_ID_TWN_FET;
import static android.telephony.satellite.SatelliteManager.EMERGENCY_CALL_TO_SATELLITE_HANDOVER_TYPE_SOS;
import static android.telephony.satellite.SatelliteManager.EMERGENCY_CALL_TO_SATELLITE_HANDOVER_TYPE_T911;

import static com.android.internal.telephony.satellite.SatelliteController.TIMEOUT_TYPE_EMERGENCY_CALL_MONITORING_DURATION_MILLIS;

import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.telecom.Call;
import android.telecom.PhoneAccount;
import android.telecom.TelecomManager;
import android.telephony.AccessNetworkConstants;
import android.telephony.BarringInfo;
import android.telephony.CallState;
import android.telephony.CarrierConfigManager;
import android.telephony.DisconnectCause;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.PreciseCallState;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;
import android.telephony.cts.InCallServiceStateValidator;
import android.telephony.emergency.EmergencyNumber;
import android.telephony.ims.ImsCallProfile;
import android.telephony.ims.ImsCallSessionListener;
import android.telephony.ims.ImsManager;
import android.telephony.ims.ImsMmTelManager;
import android.telephony.ims.ImsStreamMediaProfile;
import android.telephony.ims.MediaQualityStatus;
import android.telephony.ims.cts.ConferenceHelper;
import android.telephony.ims.cts.ImsServiceConnector;
import android.telephony.ims.cts.ImsUtils;
import android.telephony.ims.cts.TestMmTelFeature;
import android.telephony.ims.cts.TestImsCallSessionImpl;
import android.telephony.ims.cts.TestImsService;
import android.telephony.ims.feature.MmTelFeature;
import android.telephony.mockmodem.MockEmergencyRegResult;
import android.telephony.mockmodem.MockModemManager;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.util.SparseArray;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ShellIdentityUtils;
import com.android.internal.telephony.SmsApplication;
import com.android.internal.telephony.flags.Flags;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/** CTS tests for ImsCall . */
@RunWith(AndroidJUnit4.class)
public class EmergencyCallHandoverToSatelliteMessagingTest extends SatelliteImsCallingBase {
    private static final String LOG_TAG = "EmergencyCallHandoverToSatelliteMessagingTest";

    private static final int MANUAL_CONNECT_SLOT_ID = SLOT_ID_0;
    private static final int MANUAL_CONNECT_SIM_PROFILE_ID = MOCK_SIM_PROFILE_ID_TWN_FET;
    private static final String MANUAL_CONNECT_PHONE_NUMBER = PHONE_NUMBER_0;
    private static final int AUTO_CONNECT_SLOT_ID = SLOT_ID_1;
    private static final int AUTO_CONNECT_SIM_PROFILE_ID = MOCK_SIM_PROFILE_ID_TWN_CHT;
    private static final String AUTO_CONNECT_PHONE_NUMBER = PHONE_NUMBER_1;

    // The timeout to wait result in milliseconds
    private static final long WAIT_FOR_STATE_CHANGE_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(10);
    private static final long WAIT_REQUEST_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(3);
    private static boolean sIsMultiSimDevice = false;
    private static boolean sVoLteEnabled = false;

    static {
        initializeLatches();
    }

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    @BeforeClass
    public static void beforeAllTests() throws Exception {
        logd(LOG_TAG, "beforeAllTests");
        if (!ImsUtils.shouldTestImsCall()) return;
        if (!shouldTestSatelliteWithMockService()) return;
        beforeAllCarrierRoamingTestsBase();
        sServiceConnector = new ImsServiceConnector(InstrumentationRegistry.getInstrumentation());

        grantSatellitePermission();
        setupMockSatelliteService();
        assertTrue(sMockSatelliteServiceManager.setSatelliteControllerTimeoutDuration(false,
                TIMEOUT_TYPE_EMERGENCY_CALL_MONITORING_DURATION_MILLIS, 500));
        sIsMultiSimDevice = sTelephonyManager.isMultiSimEnabled();
    }

    @AfterClass
    public static void afterAllTests() throws Exception {
        logd(LOG_TAG, "afterAllTests");
        if (!shouldTestSatelliteWithMockService()) return;
        assertTrue(sMockSatelliteServiceManager.setCtsMode(false));
        assertTrue(sMockSatelliteServiceManager.setSatelliteControllerTimeoutDuration(true,
                TIMEOUT_TYPE_EMERGENCY_CALL_MONITORING_DURATION_MILLIS, 0));
        unregisterTestLocationProvider();
        resetSatelliteAccessControlOverlayConfigs();
        assertTrue(sMockSatelliteServiceManager.restoreSatelliteServicePackageName());
        waitFor(2000);
        afterAllCarrierRoamingTestsBase();
        sServiceConnector = null;
    }

    @Before
    public void beforeTest() throws Exception {
        logd(LOG_TAG, "beforeTest");
        assumeTrue(shouldTestEmergencyHandoverToSatelliteMessaging());

        if (sMockModemManager != null) {
            sMockModemManager.resetImsAllLatchCountdown();
        }
    }

    @After
    public void afterTest() throws Exception {
        logd(LOG_TAG, "afterTest");
        if (!shouldTestEmergencyHandoverToSatelliteMessaging()) {
            return;
        }

        if (!mCalls.isEmpty() && (mCurrentCallId != null)) {
            Call call = mCalls.get(mCurrentCallId);
            call.disconnect();
        }

        initializeLatches();

        if (sServiceConnector != null && sIsBound) {
            TestImsService imsService = sServiceConnector.getCarrierService();
            sServiceConnector.disconnectCarrierImsService();
            sIsBound = false;
            imsService.waitForExecutorFinish();
        }

        tearDownEmergencyCalling();
    }

    @Test
    public void testE911ToEsosHandover_NtnOnly() throws Exception {
        /*
         * Test scenario:
         * 1. There is only one NTN-only subscription.
         * 2. The emergency call is placed to the test emergency number.
         * 3. The emergency call is handed over to eSOS satellite messaging.
         * 4. Verify the connection event EVENT_DISPLAY_EMERGENCY_MESSAGE is sent
         *    and its contents are correct.
         */
        assumeTrue(shouldTestEmergencyHandoverToSatelliteMessaging());

        boolean supportDomainSelection =
                ShellIdentityUtils.invokeMethodWithShellPermissions(sTelephonyManager,
                        (tm) -> tm.isDomainSelectionSupported());
        assumeFalse(supportDomainSelection);

        LinkedBlockingQueue<List<CallState>> queue = new LinkedBlockingQueue<>();
        TestTelephonyCallbackForCallStateChange testCb =
                new TestTelephonyCallbackForCallStateChange(queue);
        try {
            logd(LOG_TAG, "testE911ToEsosHandover_NtnOnly: setup test environment");
            setUpMockSim(MANUAL_CONNECT_SLOT_ID, MANUAL_CONNECT_SIM_PROFILE_ID,
                MANUAL_CONNECT_PHONE_NUMBER);
            setUpImsCallingTestEnvironment(MANUAL_CONNECT_SLOT_ID);

            sNtnOnlySubId = SubscriptionManager.getSubscriptionId(MANUAL_CONNECT_SLOT_ID);
            assumeTrue(sNtnOnlySubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID);
            setUpSatelliteAccessAllowedAtDefaultTestLocation();
            assertTrue(sMockSatelliteServiceManager.setCtsMode(true));
            setUpNtnOnlySubscription();

            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                    sTelephonyManager, (tm) -> tm.registerTelephonyCallback(Runnable::run, testCb));

            testCb.setTestEmergencyNumber(sTestEmergencyNumbers[0]);
            setupForEmergencyCalling(MANUAL_CONNECT_SLOT_ID, sTestEmergencyNumbers[0]);
            assertTrue(testCb.waitForTestEmergencyNumberConfigured());

            logd(LOG_TAG, "testE911ToEsosHandover_NtnOnly: bind to InCallService");
            bindImsService(MANUAL_CONNECT_SLOT_ID);
            mServiceCallBack = new ServiceCallBack();
            InCallServiceStateValidator.setCallbacks(mServiceCallBack);

            logd(LOG_TAG, "testE911ToEsosHandover_NtnOnly: place outgoing emergency call");
            TelecomManager telecomManager = getContext().getSystemService(TelecomManager.class);
            telecomManager.placeCall(sTestEmergencyUris[0], new Bundle());

            waitForCallSessionToNotBe(null);
            TestImsCallSessionImpl callSession = sServiceConnector.getCarrierService().getMmTelFeature()
                    .getImsCallsession();
            callSession.addTestType(TestImsCallSessionImpl.TEST_TYPE_MO_STAY_AT_ESTABLISHING);

            assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_ADDED, WAIT_FOR_CALL_STATE));
            Call call = getCall(mCurrentCallId);

            assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DIALING, WAIT_FOR_CALL_STATE));

            // Wait for outgoing emergency call
            assertTrue(testCb.waitForOutgoingEmergencyCall(sTestEmergencyNumbers[0]));
            // Wait for the connection event EVENT_DISPLAY_EMERGENCY_MESSAGE sent to Dialer.
            assertTrue(callingTestLatchCountdown(
                        LATCH_EVENT_DISPLAY_EMERGENCY_MESSAGE_RECEIVED, WAIT_FOR_CALL_STATE));
            Pair<String, String> eSosApp = readSatelliteHandoverAppFromOverlayConfig();
            String action = sMockSatelliteServiceManager.readStringFromOverlayConfig(
                    "config_satellite_test_with_esp_replies_intent_action");
            verifyHandoverMessage(EMERGENCY_CALL_TO_SATELLITE_HANDOVER_TYPE_SOS, eSosApp.first,
                    eSosApp.second, action, "", MANUAL_CONNECT_SLOT_ID);

            call.disconnect();
            assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DISCONNECTING, WAIT_FOR_CALL_STATE));
            isCallDisconnected(call, callSession);
            assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_REMOVED, WAIT_FOR_CALL_STATE));
            waitForUnboundService();
        } finally {
            logd(LOG_TAG, "testE911ToEsosHandover_NtnOnly: clean up test environment");
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                    sTelephonyManager, (tm) -> tm.unregisterTelephonyCallback(testCb));
            unregisterTestLocationProvider();
            resetSatelliteAccessControlOverlayConfigs();
            resetSatelliteAccessForSatelliteSubscriptions();
            restoreSupportedMsgAppsForSatelliteSubscriptions();
            restoreDeviceProvisionedState();
            restoreNtnOnlySubscriptions();
            cleanUpImsCallingTestEnvironment(MANUAL_CONNECT_SLOT_ID);
            cleanUpMockSim(MANUAL_CONNECT_SLOT_ID, MANUAL_CONNECT_SIM_PROFILE_ID, false);
        }
    }

    @Test
    public void testE911ToT911Handover_AutoConnect() throws Exception {
        /*
         * Test scenario:
         * 1. There is only one auto connect satellite subscriptions.
         * 2. The device is connected to satellite within histeresis time.
         * 3. The emergency call is placed to the test emergency number.
         * 4. The emergency call is handed over to T911 satellite messaging.
         * 5. Verify the connection event EVENT_DISPLAY_EMERGENCY_MESSAGE is sent
         *    and its contents are correct.
         */
        assumeTrue(shouldTestEmergencyHandoverToSatelliteMessaging());

        boolean supportDomainSelection =
                ShellIdentityUtils.invokeMethodWithShellPermissions(sTelephonyManager,
                        (tm) -> tm.isDomainSelectionSupported());
        assumeFalse(supportDomainSelection);

        LinkedBlockingQueue<List<CallState>> queue = new LinkedBlockingQueue<>();
        TestTelephonyCallbackForCallStateChange testCb =
                new TestTelephonyCallbackForCallStateChange(queue);
        try {
            logd(LOG_TAG, "testE911ToT911Handover_AutoConnect: setup auto connect test environment");
            setUpAutoConnectTestEnvironment(SLOT_ID_0, MOCK_SIM_PROFILE_ID_TWN_FET,
                PHONE_NUMBER_0, true);
            setUpImsCallingTestEnvironment(SLOT_ID_0);
            setUpSatelliteAccessAllowedAtDefaultTestLocation();
            assertTrue(sMockSatelliteServiceManager.setCtsMode(true));

            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                    sTelephonyManager, (tm) -> tm.registerTelephonyCallback(Runnable::run, testCb));

            testCb.setTestEmergencyNumber(sTestEmergencyNumbers[1]);
            setupForEmergencyCalling(SLOT_ID_0, sTestEmergencyNumbers[1]);
            assertTrue(testCb.waitForTestEmergencyNumberConfigured());

            logd(LOG_TAG, "testE911ToT911Handover_AutoConnect: bind to InCallService");
            bindImsService(SLOT_ID_0);
            mServiceCallBack = new ServiceCallBack();
            InCallServiceStateValidator.setCallbacks(mServiceCallBack);

            logd(LOG_TAG, "testE911ToT911Handover_AutoConnect: place outgoing emergency call");
            TelecomManager telecomManager = getContext().getSystemService(TelecomManager.class);
            telecomManager.placeCall(sTestEmergencyUris[1], new Bundle());

            waitForCallSessionToNotBe(null);
            TestImsCallSessionImpl callSession = sServiceConnector.getCarrierService().getMmTelFeature()
                    .getImsCallsession();
            callSession.addTestType(TestImsCallSessionImpl.TEST_TYPE_MO_STAY_AT_ESTABLISHING);

            assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_ADDED, WAIT_FOR_CALL_STATE));
            Call call = getCall(mCurrentCallId);

            assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DIALING, WAIT_FOR_CALL_STATE));

            // Wait for outgoing emergency call
            assertTrue(testCb.waitForOutgoingEmergencyCall(sTestEmergencyNumbers[1]));
            // Wait for the connection event EVENT_DISPLAY_EMERGENCY_MESSAGE sent to Dialer.
            assertTrue(callingTestLatchCountdown(
                        LATCH_EVENT_DISPLAY_EMERGENCY_MESSAGE_RECEIVED, WAIT_FOR_CALL_STATE));
            Pair<String, String> defaultSmsApp = getDefaultSmsApp();
            String action = Intent.ACTION_SENDTO;
            String uri = "smsto:" + sTestEmergencyNumbers[1];
            verifyHandoverMessage(EMERGENCY_CALL_TO_SATELLITE_HANDOVER_TYPE_T911,
                    defaultSmsApp.first, defaultSmsApp.second, action, uri, SLOT_ID_0);

            call.disconnect();
            assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DISCONNECTING, WAIT_FOR_CALL_STATE));
            isCallDisconnected(call, callSession);
            assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_REMOVED, WAIT_FOR_CALL_STATE));
            waitForUnboundService();
        } finally {
            logd(LOG_TAG, "testE911ToT911Handover_AutoConnect: clean up test environment");
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                    sTelephonyManager, (tm) -> tm.unregisterTelephonyCallback(testCb));
            cleanUpImsCallingTestEnvironment(SLOT_ID_0);
            cleanUpMockSim(SLOT_ID_0, MOCK_SIM_PROFILE_ID_TWN_FET, true);
        }
    }

    @Test
    public void testE911ToEsosHandover_Coex() throws Exception {
        /*
         * Test scenario:
         * 1. There are two satellite subscriptions: one manual connect and one auto connect.
         * 2. Both subscriptions are not connected to satellite within histeresis time.
         * 3. The emergency call is placed to the test emergency number.
         * 4. The emergency call is handed over to eSOS satellite messaging.
         * 5. Verify the connection event EVENT_DISPLAY_EMERGENCY_MESSAGE is sent
         *    and its contents are correct.
         */
        assumeTrue(shouldTestEmergencyHandoverToSatelliteMessaging());
        assumeTrue("Skip test on single SIM device", sIsMultiSimDevice);

        boolean supportDomainSelection =
                ShellIdentityUtils.invokeMethodWithShellPermissions(sTelephonyManager,
                        (tm) -> tm.isDomainSelectionSupported());
        assumeFalse(supportDomainSelection);

        LinkedBlockingQueue<List<CallState>> queue = new LinkedBlockingQueue<>();
        TestTelephonyCallbackForCallStateChange testCb =
                new TestTelephonyCallbackForCallStateChange(queue);
        try {
            logd(LOG_TAG, "testE911ToEsosHandover_Coex: setup manual connect test environment");
            setUpManualConnectTestEnvironment(MANUAL_CONNECT_SLOT_ID,
                MANUAL_CONNECT_SIM_PROFILE_ID, MANUAL_CONNECT_PHONE_NUMBER, true, true, false);
            setUpImsCallingTestEnvironment(MANUAL_CONNECT_SLOT_ID);

            logd(LOG_TAG, "testE911ToEsosHandover_Coex: setup auto connect test environment");
            setUpAutoConnectTestEnvironment(AUTO_CONNECT_SLOT_ID, AUTO_CONNECT_SIM_PROFILE_ID,
                AUTO_CONNECT_PHONE_NUMBER, false);

            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                    sTelephonyManager, (tm) -> tm.registerTelephonyCallback(Runnable::run, testCb));

            testCb.setTestEmergencyNumber(sTestEmergencyNumbers[2]);
            setupForEmergencyCalling(MANUAL_CONNECT_SLOT_ID, sTestEmergencyNumbers[2]);
            assertTrue(testCb.waitForTestEmergencyNumberConfigured());

            logd(LOG_TAG, "testE911ToEsosHandover_Coex: bind to InCallService");
            bindImsService(MANUAL_CONNECT_SLOT_ID);
            mServiceCallBack = new ServiceCallBack();
            InCallServiceStateValidator.setCallbacks(mServiceCallBack);

            logd(LOG_TAG, "testE911ToEsosHandover_Coex: place outgoing emergency call");
            TelecomManager telecomManager = getContext().getSystemService(TelecomManager.class);
            telecomManager.placeCall(sTestEmergencyUris[2], new Bundle());

            waitForCallSessionToNotBe(null);
            TestImsCallSessionImpl callSession = sServiceConnector.getCarrierService().getMmTelFeature()
                    .getImsCallsession();
            callSession.addTestType(TestImsCallSessionImpl.TEST_TYPE_MO_STAY_AT_ESTABLISHING);

            assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_ADDED, WAIT_FOR_CALL_STATE));
            Call call = getCall(mCurrentCallId);

            assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DIALING, WAIT_FOR_CALL_STATE));

            // Wait for outgoing emergency call
            assertTrue(testCb.waitForOutgoingEmergencyCall(sTestEmergencyNumbers[2]));
            // Wait for the connection event EVENT_DISPLAY_EMERGENCY_MESSAGE sent to Dialer.
            assertTrue(callingTestLatchCountdown(
                        LATCH_EVENT_DISPLAY_EMERGENCY_MESSAGE_RECEIVED, WAIT_FOR_CALL_STATE));
            Pair<String, String> eSosApp = readSatelliteHandoverAppFromOverlayConfig();
            String action = sMockSatelliteServiceManager.readStringFromOverlayConfig(
                    "config_satellite_test_with_esp_replies_intent_action");
            verifyHandoverMessage(EMERGENCY_CALL_TO_SATELLITE_HANDOVER_TYPE_SOS, eSosApp.first,
                    eSosApp.second, action, "", MANUAL_CONNECT_SLOT_ID);

            call.disconnect();
            assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DISCONNECTING, WAIT_FOR_CALL_STATE));
            isCallDisconnected(call, callSession);
            assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_REMOVED, WAIT_FOR_CALL_STATE));
            waitForUnboundService();
        } finally {
            logd(LOG_TAG, "testE911ToEsosHandover_Coex: clean up test environments");
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                    sTelephonyManager, (tm) -> tm.unregisterTelephonyCallback(testCb));
            cleanUpImsCallingTestEnvironment(MANUAL_CONNECT_SLOT_ID);
            cleanUpManualConnectTestEnvironment(
                MANUAL_CONNECT_SLOT_ID, MANUAL_CONNECT_SIM_PROFILE_ID);
            cleanUpMockSim(AUTO_CONNECT_SLOT_ID, AUTO_CONNECT_SIM_PROFILE_ID, false);
        }
    }

    @Test
    public void testE911ToT911Handover_Coex() throws Exception {
        /*
         * Test scenario:
         * 1. There are two satellite subscriptions: one manual connect and one auto connect.
         * 2. Auto connect subscription is connected to satellite within histeresis time.
         * 3. The emergency call is placed to the test emergency number.
         * 4. The emergency call is handed over to T911 satellite messaging.
         * 5. Verify the connection event EVENT_DISPLAY_EMERGENCY_MESSAGE is sent
         *    and its contents are correct.
         */
        assumeTrue(shouldTestEmergencyHandoverToSatelliteMessaging());
        assumeTrue("Skip test on single SIM device", sIsMultiSimDevice);

        boolean supportDomainSelection =
                ShellIdentityUtils.invokeMethodWithShellPermissions(sTelephonyManager,
                        (tm) -> tm.isDomainSelectionSupported());
        assumeFalse(supportDomainSelection);

        LinkedBlockingQueue<List<CallState>> queue = new LinkedBlockingQueue<>();
        TestTelephonyCallbackForCallStateChange testCb =
                new TestTelephonyCallbackForCallStateChange(queue);
        try {
            logd(LOG_TAG, "testE911ToT911Handover_Coex: setup manual connect test environment");
            setUpManualConnectTestEnvironment(MANUAL_CONNECT_SLOT_ID,
                MANUAL_CONNECT_SIM_PROFILE_ID, MANUAL_CONNECT_PHONE_NUMBER, true, true, false);

            logd(LOG_TAG, "testE911ToT911Handover_Coex: setup auto connect test environment");
            setUpAutoConnectTestEnvironment(AUTO_CONNECT_SLOT_ID, AUTO_CONNECT_SIM_PROFILE_ID,
                AUTO_CONNECT_PHONE_NUMBER, true);
            setUpImsCallingTestEnvironment(AUTO_CONNECT_SLOT_ID);

            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                    sTelephonyManager, (tm) -> tm.registerTelephonyCallback(Runnable::run, testCb));

            testCb.setTestEmergencyNumber(sTestEmergencyNumbers[3]);
            setupForEmergencyCalling(AUTO_CONNECT_SLOT_ID, sTestEmergencyNumbers[3]);
            assertTrue(testCb.waitForTestEmergencyNumberConfigured());

            logd(LOG_TAG, "testE911ToT911Handover_Coex: bind to InCallService");
            bindImsService(AUTO_CONNECT_SLOT_ID);
            mServiceCallBack = new ServiceCallBack();
            InCallServiceStateValidator.setCallbacks(mServiceCallBack);

            logd(LOG_TAG, "testE911ToT911Handover_Coex: place outgoing emergency call");
            TelecomManager telecomManager = getContext().getSystemService(TelecomManager.class);
            telecomManager.placeCall(sTestEmergencyUris[3], new Bundle());

            waitForCallSessionToNotBe(null);
            TestImsCallSessionImpl callSession = sServiceConnector.getCarrierService().getMmTelFeature()
                    .getImsCallsession();
            callSession.addTestType(TestImsCallSessionImpl.TEST_TYPE_MO_STAY_AT_ESTABLISHING);

            assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_ADDED, WAIT_FOR_CALL_STATE));
            Call call = getCall(mCurrentCallId);

            assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DIALING, WAIT_FOR_CALL_STATE));

            // Wait for outgoing emergency call
            assertTrue(testCb.waitForOutgoingEmergencyCall(sTestEmergencyNumbers[3]));
            // Wait for the connection event EVENT_DISPLAY_EMERGENCY_MESSAGE sent to Dialer.
            assertTrue(callingTestLatchCountdown(
                        LATCH_EVENT_DISPLAY_EMERGENCY_MESSAGE_RECEIVED, WAIT_FOR_CALL_STATE));
            Pair<String, String> defaultSmsApp = getDefaultSmsApp();
            String action = Intent.ACTION_SENDTO;
            String uri = "smsto:" + sTestEmergencyNumbers[3];
            verifyHandoverMessage(EMERGENCY_CALL_TO_SATELLITE_HANDOVER_TYPE_T911,
                    defaultSmsApp.first, defaultSmsApp.second, action, uri, AUTO_CONNECT_SLOT_ID);

            call.disconnect();
            assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DISCONNECTING, WAIT_FOR_CALL_STATE));
            isCallDisconnected(call, callSession);
            assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_REMOVED, WAIT_FOR_CALL_STATE));
            waitForUnboundService();
        } finally {
            logd(LOG_TAG, "testE911ToT911Handover_Coex: clean up test environments");
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                    sTelephonyManager, (tm) -> tm.unregisterTelephonyCallback(testCb));
            cleanUpImsCallingTestEnvironment(AUTO_CONNECT_SLOT_ID);
            cleanUpManualConnectTestEnvironment(
                MANUAL_CONNECT_SLOT_ID, MANUAL_CONNECT_SIM_PROFILE_ID);
            cleanUpMockSim(AUTO_CONNECT_SLOT_ID, AUTO_CONNECT_SIM_PROFILE_ID, true);
        }
    }

    @Test
    public void testE911ToEsosHandover_NtnOnly_DS() throws Exception {
        /*
         * Require domain selection to be supported.
         * Test scenario:
         * 1. There is only one NTN-only subscription.
         * 2. The emergency call is placed to the test emergency number.
         * 3. The emergency call is handed over to eSOS satellite messaging.
         * 4. Verify the connection event EVENT_DISPLAY_EMERGENCY_MESSAGE is sent
         *    and its contents are correct.
         */
        assumeTrue(shouldTestEmergencyHandoverToSatelliteMessaging());

        boolean supportDomainSelection =
                ShellIdentityUtils.invokeMethodWithShellPermissions(sTelephonyManager,
                        (tm) -> tm.isDomainSelectionSupported());
        assumeTrue(supportDomainSelection);

        ImsManager imsManager = getContext().getSystemService(ImsManager.class);
        ImsMmTelManager mmTelManager = null;
        LinkedBlockingQueue<List<CallState>> queue = new LinkedBlockingQueue<>();
        TestTelephonyCallbackForCallStateChange testCb =
                new TestTelephonyCallbackForCallStateChange(queue);

        try {
            logd(LOG_TAG, "testE911ToEsosHandover_NtnOnly_DS: setup test environment");
            setUpMockSim(MANUAL_CONNECT_SLOT_ID, MANUAL_CONNECT_SIM_PROFILE_ID,
                MANUAL_CONNECT_PHONE_NUMBER);
            setUpImsCallingTestEnvironment(MANUAL_CONNECT_SLOT_ID);

            sNtnOnlySubId = SubscriptionManager.getSubscriptionId(MANUAL_CONNECT_SLOT_ID);
            assumeTrue(sNtnOnlySubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID);
            setUpSatelliteAccessAllowedAtDefaultTestLocation();
            assertTrue(sMockSatelliteServiceManager.setCtsMode(true));
            setUpNtnOnlySubscription();

            mmTelManager = imsManager.getImsMmTelManager(sNtnOnlySubId);
            sVoLteEnabled = ShellIdentityUtils.invokeMethodWithShellPermissions(mmTelManager,
                    ImsMmTelManager::isAdvancedCallingSettingEnabled);

            sMockModemManager.notifyEmergencyNumberList(MANUAL_CONNECT_SLOT_ID,
                new String[] { sTestEmergencyNumbers[0] });

            // Setup pre-condition
            PersistableBundle bundle = getDefaultPersistableBundle();
            overrideCarrierConfig(sNtnOnlySubId, bundle);

            MockEmergencyRegResult regResult = getEmergencyRegResult(EUTRAN, REGISTRATION_STATE_HOME,
                    NetworkRegistrationInfo.DOMAIN_CS | NetworkRegistrationInfo.DOMAIN_PS,
                    true, true, 0, 0, "", "");
            sMockModemManager.setEmergencyRegResult(MANUAL_CONNECT_SLOT_ID, regResult);

            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                    sTelephonyManager, (tm) -> tm.registerTelephonyCallback(Runnable::run, testCb));

            testCb.setTestEmergencyNumber(sTestEmergencyNumbers[0]);
            setupForEmergencyCalling(MANUAL_CONNECT_SLOT_ID, sTestEmergencyNumbers[0]);
            assertTrue(testCb.waitForTestEmergencyNumberConfigured());

            logd(LOG_TAG, "testE911ToEsosHandover_NtnOnly_DS: bind to InCallService");
            bindImsService(MANUAL_CONNECT_SLOT_ID);
            mServiceCallBack = new ServiceCallBack();
            InCallServiceStateValidator.setCallbacks(mServiceCallBack);

            logd(LOG_TAG, "testE911ToEsosHandover_NtnOnly_DS: place outgoing emergency call");
            TelecomManager telecomManager = getContext().getSystemService(TelecomManager.class);
            telecomManager.placeCall(sTestEmergencyUris[0], new Bundle());

            waitForCallSessionToNotBe(null);
            TestImsCallSessionImpl callSession = sServiceConnector.getCarrierService().getMmTelFeature()
                    .getImsCallsession();
            callSession.addTestType(TestImsCallSessionImpl.TEST_TYPE_MO_STAY_AT_ESTABLISHING);

            assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_ADDED, WAIT_FOR_CALL_STATE));
            Call call = getCall(mCurrentCallId);
            assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DIALING, WAIT_FOR_CALL_STATE));

            // Wait for outgoing emergency call
            assertTrue(testCb.waitForOutgoingEmergencyCall(sTestEmergencyNumbers[0]));
            // Wait for the connection event EVENT_DISPLAY_EMERGENCY_MESSAGE sent to Dialer.
            assertTrue(callingTestLatchCountdown(
                        LATCH_EVENT_DISPLAY_EMERGENCY_MESSAGE_RECEIVED, WAIT_FOR_CALL_STATE));
            Pair<String, String> eSosApp = readSatelliteHandoverAppFromOverlayConfig();
            String action = sMockSatelliteServiceManager.readStringFromOverlayConfig(
                    "config_satellite_test_with_esp_replies_intent_action");
            verifyHandoverMessage(EMERGENCY_CALL_TO_SATELLITE_HANDOVER_TYPE_SOS, eSosApp.first,
                    eSosApp.second, action, "", MANUAL_CONNECT_SLOT_ID);

            call.disconnect();
            assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DISCONNECTING, WAIT_FOR_CALL_STATE));
            isCallDisconnected(call, callSession);
            assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_REMOVED, WAIT_FOR_CALL_STATE));
            waitForUnboundService();
        } finally {
            logd(LOG_TAG, "testE911ToEsosHandover_NtnOnly_DS: clean up test environments");
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                sTelephonyManager, (tm) -> tm.unregisterTelephonyCallback(testCb));
            if (mmTelManager != null) {
                ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mmTelManager,
                    (m) -> m.setAdvancedCallingSettingEnabled(sVoLteEnabled));
            }
            unregisterTestLocationProvider();
            resetSatelliteAccessControlOverlayConfigs();
            resetSatelliteAccessForSatelliteSubscriptions();
            restoreSupportedMsgAppsForSatelliteSubscriptions();
            restoreDeviceProvisionedState();
            restoreNtnOnlySubscriptions();
            cleanUpImsCallingTestEnvironment(MANUAL_CONNECT_SLOT_ID);
            cleanUpMockSim(MANUAL_CONNECT_SLOT_ID, MANUAL_CONNECT_SIM_PROFILE_ID, false);
        }
    }

    @Test
    public void testE911ToT911Handover_AutoConnect_DS() throws Exception {
        /*
         * Require domain selection to be supported.
         * Test scenario:
         * 1. There is only one auto connect satellite subscriptions.
         * 2. The device is connected to satellite within histeresis time.
         * 3. The emergency call is placed to the test emergency number.
         * 4. The emergency call is handed over to T911 satellite messaging.
         * 5. Verify the connection event EVENT_DISPLAY_EMERGENCY_MESSAGE is sent
         *    and its contents are correct.
         */
        assumeTrue(shouldTestEmergencyHandoverToSatelliteMessaging());

        boolean supportDomainSelection =
                ShellIdentityUtils.invokeMethodWithShellPermissions(sTelephonyManager,
                        (tm) -> tm.isDomainSelectionSupported());
        assumeTrue(supportDomainSelection);

        ImsManager imsManager = getContext().getSystemService(ImsManager.class);
        ImsMmTelManager mmTelManager = null;
        LinkedBlockingQueue<List<CallState>> queue = new LinkedBlockingQueue<>();
        TestTelephonyCallbackForCallStateChange testCb =
                new TestTelephonyCallbackForCallStateChange(queue);

        try {
            logd(LOG_TAG, "testE911ToT911Handover_AutoConnect_DS: setup auto connect"
                    + " test environment");
            setUpAutoConnectTestEnvironment(SLOT_ID_0, MOCK_SIM_PROFILE_ID_TWN_FET,
                PHONE_NUMBER_0, true);
            setUpImsCallingTestEnvironment(SLOT_ID_0);
            setUpSatelliteAccessAllowedAtDefaultTestLocation();
            assertTrue(sMockSatelliteServiceManager.setCtsMode(true));

            int subId = SubscriptionManager.getSubscriptionId(SLOT_ID_0);
            assumeTrue(subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID);
            mmTelManager = imsManager.getImsMmTelManager(subId);
            sVoLteEnabled = ShellIdentityUtils.invokeMethodWithShellPermissions(mmTelManager,
                    ImsMmTelManager::isAdvancedCallingSettingEnabled);

            sMockModemManager.notifyEmergencyNumberList(SLOT_ID_0,
                new String[] { sTestEmergencyNumbers[1] });

            // Setup pre-condition
            PersistableBundle bundle = getDefaultPersistableBundle();
            overrideCarrierConfig(subId, bundle);

            MockEmergencyRegResult regResult = getEmergencyRegResult(EUTRAN, REGISTRATION_STATE_HOME,
                    NetworkRegistrationInfo.DOMAIN_CS | NetworkRegistrationInfo.DOMAIN_PS,
                    true, true, 0, 0, "", "");
            sMockModemManager.setEmergencyRegResult(SLOT_ID_0, regResult);

            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                    sTelephonyManager, (tm) -> tm.registerTelephonyCallback(Runnable::run, testCb));

            testCb.setTestEmergencyNumber(sTestEmergencyNumbers[1]);
            setupForEmergencyCalling(SLOT_ID_0, sTestEmergencyNumbers[1]);
            assertTrue(testCb.waitForTestEmergencyNumberConfigured());

            logd(LOG_TAG, "testE911ToT911Handover_AutoConnect_DS: bind to InCallService");
            bindImsService(SLOT_ID_0);
            mServiceCallBack = new ServiceCallBack();
            InCallServiceStateValidator.setCallbacks(mServiceCallBack);

            logd(LOG_TAG, "testE911ToT911Handover_AutoConnect_DS: place outgoing emergency call");
            TelecomManager telecomManager = getContext().getSystemService(TelecomManager.class);
            telecomManager.placeCall(sTestEmergencyUris[1], new Bundle());

            waitForCallSessionToNotBe(null);
            TestImsCallSessionImpl callSession = sServiceConnector.getCarrierService().getMmTelFeature()
                    .getImsCallsession();
            callSession.addTestType(TestImsCallSessionImpl.TEST_TYPE_MO_STAY_AT_ESTABLISHING);

            assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_ADDED, WAIT_FOR_CALL_STATE));
            Call call = getCall(mCurrentCallId);

            assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DIALING, WAIT_FOR_CALL_STATE));

            // Wait for outgoing emergency call
            assertTrue(testCb.waitForOutgoingEmergencyCall(sTestEmergencyNumbers[1]));
            // Wait for the connection event EVENT_DISPLAY_EMERGENCY_MESSAGE sent to Dialer.
            assertTrue(callingTestLatchCountdown(
                        LATCH_EVENT_DISPLAY_EMERGENCY_MESSAGE_RECEIVED, WAIT_FOR_CALL_STATE));
            Pair<String, String> defaultSmsApp = getDefaultSmsApp();
            String action = Intent.ACTION_SENDTO;
            String uri = "smsto:" + sTestEmergencyNumbers[1];
            verifyHandoverMessage(EMERGENCY_CALL_TO_SATELLITE_HANDOVER_TYPE_T911,
                    defaultSmsApp.first, defaultSmsApp.second, action, uri, SLOT_ID_0);

            call.disconnect();
            assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DISCONNECTING, WAIT_FOR_CALL_STATE));
            isCallDisconnected(call, callSession);
            assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_REMOVED, WAIT_FOR_CALL_STATE));
            waitForUnboundService();
        } finally {
            logd(LOG_TAG, "testE911ToT911Handover_AutoConnect_DS: clean up test environments");
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                sTelephonyManager, (tm) -> tm.unregisterTelephonyCallback(testCb));
            if (mmTelManager != null) {
                ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mmTelManager,
                    (m) -> m.setAdvancedCallingSettingEnabled(sVoLteEnabled));
            }
            cleanUpImsCallingTestEnvironment(SLOT_ID_0);
            cleanUpMockSim(SLOT_ID_0, MOCK_SIM_PROFILE_ID_TWN_FET, true);
        }
    }

    @Test
    public void testE911ToEsosHandover_Coex_DS() throws Exception {
        /*
         * Require domain selection to be supported.
         * Test scenario:
         * 1. There are two satellite subscriptions: one manual connect and one auto connect.
         * 2. Both subscriptions are not connected to satellite within histeresis time.
         * 3. The emergency call is placed to the test emergency number.
         * 4. The emergency call is handed over to eSOS satellite messaging.
         * 5. Verify the connection event EVENT_DISPLAY_EMERGENCY_MESSAGE is sent
         *    and its contents are correct.
         */
        assumeTrue(shouldTestEmergencyHandoverToSatelliteMessaging());
        assumeTrue("Skip test on single SIM device", sIsMultiSimDevice);

        boolean supportDomainSelection =
                ShellIdentityUtils.invokeMethodWithShellPermissions(sTelephonyManager,
                        (tm) -> tm.isDomainSelectionSupported());
        assumeTrue(supportDomainSelection);

        ImsManager imsManager = getContext().getSystemService(ImsManager.class);
        ImsMmTelManager mmTelManager = null;
        LinkedBlockingQueue<List<CallState>> queue = new LinkedBlockingQueue<>();
        TestTelephonyCallbackForCallStateChange testCb =
                new TestTelephonyCallbackForCallStateChange(queue);

        try {
            logd(LOG_TAG, "testE911ToEsosHandover_Coex_DS: setup manual connect test environment");
            setUpManualConnectTestEnvironment(MANUAL_CONNECT_SLOT_ID,
                MANUAL_CONNECT_SIM_PROFILE_ID, MANUAL_CONNECT_PHONE_NUMBER, true, true, false);
            setUpImsCallingTestEnvironment(MANUAL_CONNECT_SLOT_ID);

            logd(LOG_TAG, "testE911ToEsosHandover_Coex_DS: setup auto connect test environment");
            setUpAutoConnectTestEnvironment(AUTO_CONNECT_SLOT_ID, AUTO_CONNECT_SIM_PROFILE_ID,
                AUTO_CONNECT_PHONE_NUMBER, false);

            int subId = SubscriptionManager.getSubscriptionId(MANUAL_CONNECT_SLOT_ID);
            assumeTrue(subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID);
            mmTelManager = imsManager.getImsMmTelManager(subId);
            sVoLteEnabled = ShellIdentityUtils.invokeMethodWithShellPermissions(mmTelManager,
                    ImsMmTelManager::isAdvancedCallingSettingEnabled);

            sMockModemManager.notifyEmergencyNumberList(MANUAL_CONNECT_SLOT_ID,
                new String[] { sTestEmergencyNumbers[2] });

            // Setup pre-condition
            PersistableBundle bundle = getDefaultPersistableBundle();
            overrideCarrierConfig(subId, bundle);

            MockEmergencyRegResult regResult = getEmergencyRegResult(EUTRAN, REGISTRATION_STATE_HOME,
                    NetworkRegistrationInfo.DOMAIN_CS | NetworkRegistrationInfo.DOMAIN_PS,
                    true, true, 0, 0, "", "");
            sMockModemManager.setEmergencyRegResult(MANUAL_CONNECT_SLOT_ID, regResult);

            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                    sTelephonyManager, (tm) -> tm.registerTelephonyCallback(Runnable::run, testCb));

            testCb.setTestEmergencyNumber(sTestEmergencyNumbers[2]);
            setupForEmergencyCalling(MANUAL_CONNECT_SLOT_ID, sTestEmergencyNumbers[2]);
            assertTrue(testCb.waitForTestEmergencyNumberConfigured());

            logd(LOG_TAG, "testE911ToEsosHandover_Coex_DS: bind to InCallService");
            bindImsService(MANUAL_CONNECT_SLOT_ID);
            mServiceCallBack = new ServiceCallBack();
            InCallServiceStateValidator.setCallbacks(mServiceCallBack);

            logd(LOG_TAG, "testE911ToEsosHandover_Coex_DS: place outgoing emergency call");
            TelecomManager telecomManager = getContext().getSystemService(TelecomManager.class);
            telecomManager.placeCall(sTestEmergencyUris[2], new Bundle());

            waitForCallSessionToNotBe(null);
            TestImsCallSessionImpl callSession = sServiceConnector.getCarrierService().getMmTelFeature()
                    .getImsCallsession();
            callSession.addTestType(TestImsCallSessionImpl.TEST_TYPE_MO_STAY_AT_ESTABLISHING);

            assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_ADDED, WAIT_FOR_CALL_STATE));
            Call call = getCall(mCurrentCallId);

            assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DIALING, WAIT_FOR_CALL_STATE));

            // Wait for outgoing emergency call
            assertTrue(testCb.waitForOutgoingEmergencyCall(sTestEmergencyNumbers[2]));
            // Wait for the connection event EVENT_DISPLAY_EMERGENCY_MESSAGE sent to Dialer.
            assertTrue(callingTestLatchCountdown(
                        LATCH_EVENT_DISPLAY_EMERGENCY_MESSAGE_RECEIVED, WAIT_FOR_CALL_STATE));
            Pair<String, String> eSosApp = readSatelliteHandoverAppFromOverlayConfig();
            String action = sMockSatelliteServiceManager.readStringFromOverlayConfig(
                    "config_satellite_test_with_esp_replies_intent_action");
            verifyHandoverMessage(EMERGENCY_CALL_TO_SATELLITE_HANDOVER_TYPE_SOS, eSosApp.first,
                    eSosApp.second, action, "", MANUAL_CONNECT_SLOT_ID);

            call.disconnect();
            assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DISCONNECTING, WAIT_FOR_CALL_STATE));
            isCallDisconnected(call, callSession);
            assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_REMOVED, WAIT_FOR_CALL_STATE));
            waitForUnboundService();
        } finally {
            logd(LOG_TAG, "testE911ToEsosHandover_Coex_DS: clean up test environments");
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                sTelephonyManager, (tm) -> tm.unregisterTelephonyCallback(testCb));
            if (mmTelManager != null) {
                ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mmTelManager,
                    (m) -> m.setAdvancedCallingSettingEnabled(sVoLteEnabled));
            }
            cleanUpImsCallingTestEnvironment(MANUAL_CONNECT_SLOT_ID);
            cleanUpManualConnectTestEnvironment(
                MANUAL_CONNECT_SLOT_ID, MANUAL_CONNECT_SIM_PROFILE_ID);
            cleanUpMockSim(AUTO_CONNECT_SLOT_ID, AUTO_CONNECT_SIM_PROFILE_ID, false);
        }
    }

    @Test
    public void testE911ToT911Handover_Coex_DS() throws Exception {
        /*
         * Require domain selection to be supported.
         * Test scenario:
         * 1. There are two satellite subscriptions: one manual connect and one auto connect.
         * 2. Auto connect subscription is connected to satellite within histeresis time.
         * 3. The emergency call is placed to the test emergency number.
         * 4. The emergency call is handed over to T911 satellite messaging.
         * 5. Verify the connection event EVENT_DISPLAY_EMERGENCY_MESSAGE is sent
         *    and its contents are correct.
         */
        assumeTrue(shouldTestEmergencyHandoverToSatelliteMessaging());
        assumeTrue("Skip test on single SIM device", sIsMultiSimDevice);

        boolean supportDomainSelection =
                ShellIdentityUtils.invokeMethodWithShellPermissions(sTelephonyManager,
                        (tm) -> tm.isDomainSelectionSupported());
        assumeTrue(supportDomainSelection);

        ImsManager imsManager = getContext().getSystemService(ImsManager.class);
        ImsMmTelManager mmTelManager = null;
        LinkedBlockingQueue<List<CallState>> queue = new LinkedBlockingQueue<>();
        TestTelephonyCallbackForCallStateChange testCb =
                new TestTelephonyCallbackForCallStateChange(queue);

        try {
            logd(LOG_TAG, "testE911ToT911Handover_Coex_DS: setup manual connect test environment");
            setUpManualConnectTestEnvironment(MANUAL_CONNECT_SLOT_ID,
                MANUAL_CONNECT_SIM_PROFILE_ID, MANUAL_CONNECT_PHONE_NUMBER, true, true, false);

            logd(LOG_TAG, "testE911ToT911Handover_Coex_DS: setup auto connect test environment");
            setUpAutoConnectTestEnvironment(AUTO_CONNECT_SLOT_ID, AUTO_CONNECT_SIM_PROFILE_ID,
                AUTO_CONNECT_PHONE_NUMBER, true);
            setUpImsCallingTestEnvironment(AUTO_CONNECT_SLOT_ID);

            int subId = SubscriptionManager.getSubscriptionId(AUTO_CONNECT_SLOT_ID);
            assumeTrue(subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID);
            mmTelManager = imsManager.getImsMmTelManager(subId);
            sVoLteEnabled = ShellIdentityUtils.invokeMethodWithShellPermissions(mmTelManager,
                    ImsMmTelManager::isAdvancedCallingSettingEnabled);

            sMockModemManager.notifyEmergencyNumberList(AUTO_CONNECT_SLOT_ID,
                new String[] { sTestEmergencyNumbers[3] });

            // Setup pre-condition
            PersistableBundle bundle = getDefaultPersistableBundle();
            overrideCarrierConfig(subId, bundle);

            MockEmergencyRegResult regResult = getEmergencyRegResult(EUTRAN, REGISTRATION_STATE_HOME,
                    NetworkRegistrationInfo.DOMAIN_CS | NetworkRegistrationInfo.DOMAIN_PS,
                    true, true, 0, 0, "", "");
            sMockModemManager.setEmergencyRegResult(AUTO_CONNECT_SLOT_ID, regResult);

            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                    sTelephonyManager, (tm) -> tm.registerTelephonyCallback(Runnable::run, testCb));

            testCb.setTestEmergencyNumber(sTestEmergencyNumbers[3]);
            setupForEmergencyCalling(AUTO_CONNECT_SLOT_ID, sTestEmergencyNumbers[3]);
            assertTrue(testCb.waitForTestEmergencyNumberConfigured());

            logd(LOG_TAG, "testE911ToT911Handover_Coex_DS: bind to InCallService");
            bindImsService(AUTO_CONNECT_SLOT_ID);
            mServiceCallBack = new ServiceCallBack();
            InCallServiceStateValidator.setCallbacks(mServiceCallBack);

            logd(LOG_TAG, "testE911ToT911Handover_Coex_DS: place outgoing emergency call");
            TelecomManager telecomManager = getContext().getSystemService(TelecomManager.class);
            telecomManager.placeCall(sTestEmergencyUris[3], new Bundle());

            waitForCallSessionToNotBe(null);
            TestImsCallSessionImpl callSession = sServiceConnector.getCarrierService().getMmTelFeature()
                    .getImsCallsession();
            callSession.addTestType(TestImsCallSessionImpl.TEST_TYPE_MO_STAY_AT_ESTABLISHING);

            assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_ADDED, WAIT_FOR_CALL_STATE));
            Call call = getCall(mCurrentCallId);

            assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DIALING, WAIT_FOR_CALL_STATE));

            // Wait for outgoing emergency call
            assertTrue(testCb.waitForOutgoingEmergencyCall(sTestEmergencyNumbers[3]));
            // Wait for the connection event EVENT_DISPLAY_EMERGENCY_MESSAGE sent to Dialer.
            assertTrue(callingTestLatchCountdown(
                        LATCH_EVENT_DISPLAY_EMERGENCY_MESSAGE_RECEIVED, WAIT_FOR_CALL_STATE));
            Pair<String, String> defaultSmsApp = getDefaultSmsApp();
            String action = Intent.ACTION_SENDTO;
            String uri = "smsto:" + sTestEmergencyNumbers[3];
            verifyHandoverMessage(EMERGENCY_CALL_TO_SATELLITE_HANDOVER_TYPE_T911,
                    defaultSmsApp.first, defaultSmsApp.second, action, uri, AUTO_CONNECT_SLOT_ID);

            call.disconnect();
            assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DISCONNECTING, WAIT_FOR_CALL_STATE));
            isCallDisconnected(call, callSession);
            assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_REMOVED, WAIT_FOR_CALL_STATE));
            waitForUnboundService();
        } finally {
            logd(LOG_TAG, "testE911ToT911Handover_Coex_DS: clean up test environments");
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                sTelephonyManager, (tm) -> tm.unregisterTelephonyCallback(testCb));
            if (mmTelManager != null) {
                ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mmTelManager,
                    (m) -> m.setAdvancedCallingSettingEnabled(sVoLteEnabled));
            }
            cleanUpImsCallingTestEnvironment(AUTO_CONNECT_SLOT_ID);
            cleanUpManualConnectTestEnvironment(
                MANUAL_CONNECT_SLOT_ID, MANUAL_CONNECT_SIM_PROFILE_ID);
            cleanUpMockSim(AUTO_CONNECT_SLOT_ID, AUTO_CONNECT_SIM_PROFILE_ID, true);
        }
    }

    private static Pair<String, String> readSatelliteHandoverAppFromOverlayConfig() {
        String app = sMockSatelliteServiceManager.readStringFromOverlayConfig(
                "config_oem_enabled_satellite_sos_handover_app");
        if (TextUtils.isEmpty(app)) return new Pair<>("", "");

        String[] appComponent = app.split(";");
        if (appComponent.length == 2) {
            return new Pair<>(appComponent[0], appComponent[1]);
        } else {
            fail("readSatelliteHandoverAppFromOverlayConfig: invalid configured app=" + app);
        }
        return new Pair<>("", "");
    }

    private class TestTelephonyCallbackForCallStateChange extends TelephonyCallback implements
            TelephonyCallback.CallAttributesListener,
            TelephonyCallback.PreciseCallStateListener,
            TelephonyCallback.OutgoingEmergencyCallListener,
            TelephonyCallback.EmergencyNumberListListener {
        LinkedBlockingQueue<List<CallState>> mTestCallStateListeQueue;
        private EmergencyNumber mLastOutgoingEmergencyNumber;
        private String mTestEmergencyNumber;
        private Semaphore mOutgoingEmergencyCallSemaphore = new Semaphore(0);
        private Semaphore mActiveCallStateSemaphore = new Semaphore(0);
        private Semaphore mTestEmergencyNumberSemaphore = new Semaphore(0);
        TestTelephonyCallbackForCallStateChange(LinkedBlockingQueue<List<CallState>> queue) {
            mTestCallStateListeQueue = queue;
        }
        @Override
        public void onCallStatesChanged(@NonNull List<CallState> states) {
            mTestCallStateListeQueue.offer(states);
        }

        @Override
        public void onPreciseCallStateChanged(@NonNull PreciseCallState callState) {
            Log.i(LOG_TAG, "onPreciseCallStateChanged: state=" + callState);
            if (callState.getForegroundCallState() == PreciseCallState.PRECISE_CALL_STATE_ACTIVE) {
                mActiveCallStateSemaphore.release();
            }
        }

        @Override
        public void onOutgoingEmergencyCall(EmergencyNumber emergencyNumber, int subscriptionId) {
            Log.i(LOG_TAG, "onOutgoingEmergencyCall: emergencyNumber=" + emergencyNumber);
            mLastOutgoingEmergencyNumber = emergencyNumber;
            mOutgoingEmergencyCallSemaphore.release();
        }

        @Override
        public void onEmergencyNumberListChanged(@NonNull Map<Integer,
                        List<EmergencyNumber>> emergencyNumberList) {
            if (!TextUtils.isEmpty(mTestEmergencyNumber)) {
                for (List<EmergencyNumber> emergencyNumbers : emergencyNumberList.values()) {
                    Log.i(LOG_TAG, "onEmergencyNumberListChanged: emergencyNumbers="
                            + emergencyNumbers.stream().map(Object::toString).collect(
                                    Collectors.joining(", ")));
                    for (EmergencyNumber emergencyNumber : emergencyNumbers) {
                        if (TextUtils.equals(mTestEmergencyNumber, emergencyNumber.getNumber())) {
                            mTestEmergencyNumberSemaphore.release();
                            break;
                        }
                    }
                }
            }
        }

        public boolean waitForOutgoingEmergencyCall(String expectedNumber) {
            try {
                if (!mOutgoingEmergencyCallSemaphore.tryAcquire(
                        WAIT_FOR_STATE_CHANGE_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                    Log.e(LOG_TAG, "Timed out to receive OutgoingEmergencyCall");
                    return false;
                }
            } catch (InterruptedException ex) {
                Log.e(LOG_TAG, "waitForOutgoingEmergencyCall: ex=" + ex);
                return false;
            }

            // At this point we can only be sure that we got AN update, but not necessarily the one
            // we are looking for; wait until we see the state we want before verifying further.
            waitUntilConditionIsTrueOrTimeout(
                    new Condition() {
                        @Override
                        public Object expected() {
                            return true;
                        }

                        @Override
                        public Object actual() {
                            return mLastOutgoingEmergencyNumber != null
                                    && mLastOutgoingEmergencyNumber.getNumber().equals(
                                            expectedNumber);
                        }
                    },
                    WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                    "Expected emergency number: " + expectedNumber);
            return TextUtils.equals(expectedNumber, mLastOutgoingEmergencyNumber.getNumber());
        }

        public void setTestEmergencyNumber(String testEmergencyNumber) {
            mTestEmergencyNumber = testEmergencyNumber;
        }

        public boolean waitForTestEmergencyNumberConfigured() {
            try {
                if (!mTestEmergencyNumberSemaphore.tryAcquire(
                        WAIT_FOR_STATE_CHANGE_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                    Log.e(LOG_TAG, "Timed out to receive expected test emergency number "
                            + "configured");
                    return false;
                }
            } catch (InterruptedException ex) {
                Log.e(LOG_TAG, "waitForTestEmergencyNumberConfigured: ex=" + ex);
                return false;
            }
            return true;
        }
    }

    private void waitForCallSessionToNotBe(TestImsCallSessionImpl previousCallSession) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return true;
                    }

                    @Override
                    public Object actual() {
                        TestMmTelFeature mmtelfeatue = sServiceConnector.getCarrierService()
                                .getMmTelFeature();
                        return (mmtelfeatue.getImsCallsession() != previousCallSession) ? true
                                : false;
                    }
                }, WAIT_FOR_CALL_STATE, "CallSession Created");
    }

    private static boolean shouldTestEmergencyHandoverToSatelliteMessaging() {
        return ImsUtils.shouldTestImsCall() && shouldTestSatelliteWithMockService();
    }

    /**
     * Returns the default SMS app package name and class name.
     *
     * @return a pair of package name and class name.
     */
    private static Pair<String, String> getDefaultSmsApp() {
        ComponentName componentName =
            SmsApplication.getDefaultSendToApplication(getContext(), false);
        if (componentName == null) {
            return new Pair<>("", "");
        }
        return new Pair<>(componentName.getPackageName(), componentName.getClassName());
    }


    private static PersistableBundle getDefaultPersistableBundle() {
        int[] imsRats = new int[] { EUTRAN };
        int[] csRats = new int[] { UTRAN, GERAN };
        int[] imsRoamRats = new int[] { EUTRAN };
        int[] csRoamRats = new int[] { UTRAN, GERAN };
        int[] domainPreference = new int[] {
                CarrierConfigManager.ImsEmergency.DOMAIN_PS_3GPP,
                CarrierConfigManager.ImsEmergency.DOMAIN_CS,
                CarrierConfigManager.ImsEmergency.DOMAIN_PS_NON_3GPP
                };
        int[] roamDomainPreference = new int[] {
                CarrierConfigManager.ImsEmergency.DOMAIN_PS_3GPP,
                CarrierConfigManager.ImsEmergency.DOMAIN_CS,
                CarrierConfigManager.ImsEmergency.DOMAIN_PS_NON_3GPP
                };
        boolean imsWhenVoiceOnCs = false;
        int maxRetriesOverWiFi = 1;
        int cellularScanTimerSec = 10;
        int maxCellularTimerSec = 0;
        int scanType = SCAN_TYPE_NO_PREFERENCE;
        boolean useEmergencyPdn = true;
        boolean requiresImsRegistration = false;
        boolean requiresVoLteEnabled = false;
        boolean ltePreferredAfterNrFailed = false;
        String[] cdmaPreferredNumbers = new String[] {};
        int crossStackTimer = REDIAL_TIMER_DISABLED;
        int quickCrossStackTimer = REDIAL_TIMER_DISABLED;
        boolean quickTimerWhenInService = true;

        return getPersistableBundle(imsRats, csRats, imsRoamRats, csRoamRats,
                domainPreference, roamDomainPreference, imsWhenVoiceOnCs, maxRetriesOverWiFi,
                useEmergencyPdn, cellularScanTimerSec, maxCellularTimerSec,
                scanType, requiresImsRegistration,
                requiresVoLteEnabled, ltePreferredAfterNrFailed, cdmaPreferredNumbers,
                crossStackTimer, quickCrossStackTimer, quickTimerWhenInService);
    }

    private static PersistableBundle getPersistableBundle(
            @Nullable int[] imsRats, @Nullable int[] csRats,
            @Nullable int[] imsRoamRats, @Nullable int[] csRoamRats,
            @Nullable int[] domainPreference, @Nullable int[] roamDomainPreference,
            boolean imsWhenVoiceOnCs, int maxRetriesOverWiFi, boolean useEmergencyPdn,
            int cellularScanTimerSec, int maxCellularTimerSec,
            int scanType, boolean requiresImsRegistration,
            boolean requiresVoLteEnabled, boolean ltePreferredAfterNrFailed,
            @Nullable String[] cdmaPreferredNumbers,
            int crossStackTimer, int quickCrossStackTimer, boolean quickTimerWhenInService) {

        PersistableBundle bundle  = new PersistableBundle();
        if (imsRats != null) {
            bundle.putIntArray(
                    KEY_EMERGENCY_OVER_IMS_SUPPORTED_3GPP_NETWORK_TYPES_INT_ARRAY, imsRats);
        }
        if (imsRoamRats != null) {
            bundle.putIntArray(
                    KEY_EMERGENCY_OVER_IMS_ROAMING_SUPPORTED_3GPP_NETWORK_TYPES_INT_ARRAY,
                    imsRoamRats);
        }
        if (csRats != null) {
            bundle.putIntArray(
                    KEY_EMERGENCY_OVER_CS_SUPPORTED_ACCESS_NETWORK_TYPES_INT_ARRAY, csRats);
        }
        if (csRoamRats != null) {
            bundle.putIntArray(
                    KEY_EMERGENCY_OVER_CS_ROAMING_SUPPORTED_ACCESS_NETWORK_TYPES_INT_ARRAY,
                    csRoamRats);
        }
        if (domainPreference != null) {
            bundle.putIntArray(KEY_EMERGENCY_DOMAIN_PREFERENCE_INT_ARRAY, domainPreference);
        }
        if (roamDomainPreference != null) {
            bundle.putIntArray(KEY_EMERGENCY_DOMAIN_PREFERENCE_ROAMING_INT_ARRAY,
                    roamDomainPreference);
        }
        bundle.putBoolean(KEY_PREFER_IMS_EMERGENCY_WHEN_VOICE_CALLS_ON_CS_BOOL, imsWhenVoiceOnCs);
        bundle.putInt(KEY_MAXIMUM_NUMBER_OF_EMERGENCY_TRIES_OVER_VOWIFI_INT, maxRetriesOverWiFi);
        bundle.putBoolean(KEY_EMERGENCY_CALL_OVER_EMERGENCY_PDN_BOOL, useEmergencyPdn);
        bundle.putInt(KEY_EMERGENCY_SCAN_TIMER_SEC_INT, cellularScanTimerSec);
        bundle.putInt(KEY_MAXIMUM_CELLULAR_SEARCH_TIMER_SEC_INT, maxCellularTimerSec);
        bundle.putInt(KEY_EMERGENCY_NETWORK_SCAN_TYPE_INT, scanType);
        bundle.putBoolean(KEY_EMERGENCY_REQUIRES_IMS_REGISTRATION_BOOL, requiresImsRegistration);
        bundle.putBoolean(KEY_EMERGENCY_REQUIRES_VOLTE_ENABLED_BOOL, requiresVoLteEnabled);
        bundle.putInt(KEY_EMERGENCY_CALL_SETUP_TIMER_ON_CURRENT_NETWORK_SEC_INT, 0);
        bundle.putBoolean(KEY_EMERGENCY_LTE_PREFERRED_AFTER_NR_FAILED_BOOL,
                ltePreferredAfterNrFailed);

        if (cdmaPreferredNumbers != null) {
            bundle.putStringArray(KEY_EMERGENCY_CDMA_PREFERRED_NUMBERS_STRING_ARRAY,
                    cdmaPreferredNumbers);
        }

        bundle.putInt(KEY_CROSS_STACK_REDIAL_TIMER_SEC_INT, crossStackTimer);
        bundle.putInt(KEY_QUICK_CROSS_STACK_REDIAL_TIMER_SEC_INT, quickCrossStackTimer);
        bundle.putBoolean(KEY_START_QUICK_CROSS_STACK_REDIAL_TIMER_WHEN_REGISTERED_BOOL,
                quickTimerWhenInService);

        return bundle;
    }

    private static MockEmergencyRegResult getEmergencyRegResult(
            @AccessNetworkConstants.RadioAccessNetworkType int accessNetwork,
            @NetworkRegistrationInfo.RegistrationState int regState,
            @NetworkRegistrationInfo.Domain int domain,
            boolean isVopsSupported, boolean isEmcBearerSupported, int emc, int emf,
            @NonNull String mcc, @NonNull String mnc) {
        return new MockEmergencyRegResult(accessNetwork, regState,
                domain, isVopsSupported, isEmcBearerSupported,
                emc, emf, mcc, mnc);
    }
}
