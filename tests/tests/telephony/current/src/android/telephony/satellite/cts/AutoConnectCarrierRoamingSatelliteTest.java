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

import static android.telephony.mockmodem.MockModemConfigBase.SimInfoChangedResult.SIM_INFO_TYPE_MCC_MNC;
import static android.telephony.mockmodem.MockSimService.MOCK_SIM_PROFILE_ID_TWN_CHT;
import static android.telephony.satellite.SatelliteManager.NT_RADIO_TECHNOLOGY_LTE_DTC;
import static android.telephony.satellite.cts.ManualConnectCarrierRoamingSatelliteTest.createSendPendingIntent;
import static android.telephony.satellite.cts.ManualConnectCarrierRoamingSatelliteTest.registerSmsMmsBroadcastReceiver;

import static junit.framework.Assert.assertEquals;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.app.Activity;
import android.app.PendingIntent;
import android.hardware.radio.AccessNetwork;
import android.hardware.radio.network.NetworkInfo;
import android.hardware.radio.network.SatelliteTechnology;
import android.os.PersistableBundle;
import android.os.SystemClock;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.Telephony;
import android.telephony.AccessNetworkConstants;
import android.telephony.CarrierConfigManager;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.ServiceState;
import android.telephony.SignalThresholdInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;
import android.telephony.ims.ImsManager;
import android.telephony.ims.ImsMmTelManager;
import android.telephony.satellite.PlmnSatelliteConfig;
import android.telephony.satellite.SatelliteManager;

import androidx.annotation.RequiresPermission;

import com.android.internal.telephony.flags.Flags;
import com.android.internal.telephony.nano.PersistAtomsProto;
import com.android.internal.telephony.nano.PersistAtomsProto.IncomingSms;
import com.android.internal.telephony.nano.PersistAtomsProto.OutgoingSms;
import com.android.internal.telephony.satellite.SatelliteServiceUtils;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class AutoConnectCarrierRoamingSatelliteTest extends CarrierRoamingSatelliteTestBase {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final String TAG = "AutoConnectCarrierRoamingSatelliteTest";
    private static final String SMS_SEND_ACTION = "CTS_SMS_SEND_ACTION";
    private static final String TEST_DEST_ADDR = "1234567890";
    private static final String SATELLITE_PLMN = "46692";

    /**
     * Setup before all tests.
     *
     * @throws Exception exception
     */
    @BeforeClass
    public static void beforeAllTests() throws Exception {
        logd(TAG, "beforeAllTests");

        sActiveSubscriptionRequired = false;
        if (!shouldTestSatelliteWithMockService()) return;

        TimeUnit.MILLISECONDS.sleep(30000);
        beforeAllCarrierRoamingTestsBase();
    }

    /**
     * Cleanup resources after all tests.
     *
     * @throws Exception exception
     */
    @AfterClass
    public static void afterAllTests() throws Exception {
        logd(TAG, "afterAllTests");
        if (!shouldTestSatelliteWithMockService()) return;
        afterAllCarrierRoamingTestsBase();
    }

    @Before
    public void setUp() throws Exception {
        logd(TAG, "setUp()");
        if (!shouldTestSatelliteWithMockService()) return;
        setUpAutoConnectTestEnvironment(
                SLOT_ID_0, MOCK_SIM_PROFILE_ID_TWN_CHT, PHONE_NUMBER_0, true);
        clearAllEventsInMockServiceManagers();
    }

    @After
    public void tearDown() throws Exception {
        logd(TAG, "tearDown()");
        if (!shouldTestSatelliteWithMockService()) return;
        sMockModemManager.setSatelliteTechnology(SLOT_ID_0, SatelliteTechnology.SAT_TECH_NONE);
        cleanUpMockSim(SLOT_ID_0, MOCK_SIM_PROFILE_ID_TWN_CHT, true);
    }

    @Test
    @Ignore("b/438236284 - Need to fix and re-enable this test.")
    public void testCarrierRoamingNtnModeListener() throws Exception {
        logd(TAG, "testCarrierRoamingNtnModeListener");
        if (!shouldTestSatelliteWithMockService()) return;

        CarrierRoamingNtnListenerTest listener = new CarrierRoamingNtnListenerTest();
        listener.clearModeChanges();

        adoptShellIdentity();
        sTelephonyManager.registerTelephonyCallback(getContext().getMainExecutor(), listener);
        try {
            // Get NTN mode immediately after registering
            assertTrue(listener.waitForModeChanged(1));
            assertTrue(listener.getNtnMode());
            listener.clearModeChanges();

            // Satellite network is lost, no callback as hysteresis timeout is not expired
            sMockModemManager.changeNetworkService(SLOT_ID_0, MOCK_SIM_PROFILE_ID_TWN_CHT, false);
            assertFalse(listener.waitForModeChanged(1));
            listener.clearModeChanges();

            // Callback is received after hysteresis timeout
            assertTrue(listener.waitForModeChanged(1));
            assertFalse(listener.getNtnMode());

            // Move back to satellite in service mode
            sMockModemManager.changeNetworkService(SLOT_ID_0, MOCK_SIM_PROFILE_ID_TWN_CHT,
                    true);
            assertTrue(listener.waitForModeChanged(1));
            assertTrue(listener.getNtnMode());
            listener.clearModeChanges();
        } finally {
            sTelephonyManager.unregisterTelephonyCallback(listener);
            dropShellIdentity();
        }
    }

    @Test
    @Ignore("b/438236284 - Need to fix and re-enable this test.")
    public void testQuerySatelliteEntitlementService_success() throws Exception {
        logd(TAG, "testQuerySatelliteEntitlementService_success");
        if (!shouldTestSatelliteWithMockService()) return;
        testQuerySatelliteEntitlementService_success(
                SLOT_ID_0, CarrierConfigManager.CARRIER_ROAMING_NTN_CONNECT_AUTOMATIC);
    }

    @Test
    @RequiresPermission(Manifest.permission.SATELLITE_COMMUNICATION)
    public void testRequestEntitlementRefresh_Success_And_BypassThrottling() throws Exception {
        logd(TAG, "testRequestEntitlementRefresh_Success_And_BypassThrottling");
        if (!shouldTestSatelliteWithMockService()) return;
        testRequestEntitlementRefresh_Success_And_BypassThrottling(SLOT_ID_0);
    }

    @Test
    @RequiresPermission(Manifest.permission.SATELLITE_COMMUNICATION)
    public void testRequestEntitlementRefresh_NoPermission_ThrowsSecurityException() {
        logd(TAG, "testRequestEntitlementRefresh_NoPermission_ThrowsSecurityException");
        if (!shouldTestSatelliteWithMockService()) return;
        testRequestEntitlementRefresh_NoPermission_ThrowsSecurityException(SLOT_ID_0);
    }

    @Test
    @RequiresPermission(Manifest.permission.SATELLITE_COMMUNICATION)
    public void testRequestEntitlementRefresh_NullArguments_ThrowsNPE() {
        logd(TAG, "testRequestEntitlementRefresh_NullArguments_ThrowsNPE");
        if (!shouldTestSatelliteWithMockService()) return;
        testRequestEntitlementRefresh_NullArguments_ThrowsNPE(SLOT_ID_0);
    }

    @Test
    @RequiresPermission(Manifest.permission.SATELLITE_COMMUNICATION)
    public void testRequestEntitlementRefresh_InvalidSubId() throws Exception {
        logd(TAG, "testRequestEntitlementRefresh_InvalidSubId");
        if (!shouldTestSatelliteWithMockService()) return;
        testRequestEntitlementRefresh_InvalidSubId(SubscriptionManager.INVALID_SUBSCRIPTION_ID);
    }

    @Test
    @RequiresPermission(Manifest.permission.SATELLITE_COMMUNICATION)
    public void testNotifyEntitlementStatusChanged() throws Exception {
        logd(TAG, "testNotifyEntitlementStatusChanged");
        if (!shouldTestSatelliteWithMockService()) return;
        testNotifyEntitlementStatusChanged(SLOT_ID_0);
    }

    @Test
    @RequiresPermission(Manifest.permission.SATELLITE_COMMUNICATION)
    public void testNotifyEntitlementStatusChanged_NoPermission_ThrowsSecurityException()
            throws Exception {
        logd(TAG, "testNotifyEntitlementStatusChanged_NoPermission_ThrowsSecurityException");
        if (!shouldTestSatelliteWithMockService()) return;
        testNotifyEntitlementStatusChanged_NoPermission_ThrowsSecurityException(SLOT_ID_0);
    }

    @Test
    @Ignore("b/438236293 - Need to fix and re-enable this test.")
    public void testSatelliteConstrainedNetwork() throws Exception {
        logd(TAG, "testSatelliteConstrainedNetwork");
        if (!shouldTestSatelliteWithMockService()) return;
        testSatelliteConstrainedNetwork(SLOT_ID_0);
    }

    @Test
    @Ignore("b/438236293 - Need to fix and re-enable this test.")
    public void testNoSatelliteConstrainedNetworkConnection_WithNonConstrainedDataMode()
            throws Exception {
        logd(TAG, "testNoConstrainedNetworkConnection");
        if (!shouldTestSatelliteWithMockService()) return;
        testNoSatelliteConstrainedNetworkConnection_WithNonConstrainedDataMode(SLOT_ID_0);
    }

    @Test
    @Ignore("b/438236293 - Need to fix and re-enable this test.")
    public void testNoSatelliteConstrainedNetworkConnection_WithBandwidthNotConstrainedCapability()
            throws Exception {
        logd(TAG, "testNoConstrainedNetworkConnection");
        if (!shouldTestSatelliteWithMockService()) return;
        testNoSatelliteConstrainedNetworkConnection_WithBandwidthNotConstrainedCapability(
                SLOT_ID_0);
    }

    private void sendSmsAutoConnect(String destAddr, int resultCode) throws Exception {
        logd(TAG, "sendSmsAutoConnect destAddr:" + destAddr + ", resultCode:" + resultCode);

        CarrierRoamingNtnListenerTest ntnListener = new CarrierRoamingNtnListenerTest();
        ntnListener.clearModeChanges();

        adoptShellIdentity();
        sTelephonyManager.registerTelephonyCallback(getContext().getMainExecutor(), ntnListener);

        try {
            assertTrue(ntnListener.waitForModeChanged(1));
            assertTrue(ntnListener.getNtnMode());
            ntnListener.clearModeChanges();

            SmsMmsBroadcastReceiver sendReceiver = registerSmsMmsBroadcastReceiver(SMS_SEND_ACTION);
            PendingIntent sendPendingIntent = createSendPendingIntent();

            try {
                // Send SMS
                getSmsManager()
                        .sendTextMessage(
                                destAddr,
                                null,
                                String.valueOf(SystemClock.elapsedRealtimeNanos()),
                                sendPendingIntent,
                                null);

                assertTrue(sendReceiver.waitForBroadcast(1));
                Assert.assertEquals(resultCode, sendReceiver.getResultCode());

            } finally {
                getContext().unregisterReceiver(sendReceiver);
            }

        } finally {
            sTelephonyManager.unregisterTelephonyCallback(ntnListener);
            dropShellIdentity();
        }
    }

    private void receiveSmsAutoConnect() throws Exception {
        logd(TAG, "receiveSmsAutoConnect");

        CarrierRoamingNtnListenerTest ntnListener = new CarrierRoamingNtnListenerTest();
        ntnListener.clearModeChanges();

        adoptShellIdentity();
        sTelephonyManager.registerTelephonyCallback(getContext().getMainExecutor(), ntnListener);

        try {
            assertTrue(ntnListener.waitForModeChanged(1));
            assertTrue(ntnListener.getNtnMode());
            ntnListener.clearModeChanges();

            SmsMmsBroadcastReceiver receiveReceiver =
                    registerSmsMmsBroadcastReceiver(Telephony.Sms.Intents.SMS_RECEIVED_ACTION);

            try {
                // Trigger Incoming SMS
                assertTrue(
                        "Failed to trigger incoming SMS",
                        sMockModemManager.triggerIncomingSms(SLOT_ID_0));

                assertTrue(receiveReceiver.waitForBroadcast(1));

            } finally {
                getContext().unregisterReceiver(receiveReceiver);
            }

        } finally {
            // Unregister listener and restore connectivity
            sTelephonyManager.unregisterTelephonyCallback(ntnListener);
            dropShellIdentity();
        }
    }

    @Test
    public void testSmsAtomCheckPlmn_AutoConnect() throws Exception {
        logd(TAG, "testSmsAtomCheckPlmn_AutoConnect");
        if (!shouldTestSatelliteWithMockService()) return;

        // Clear existing atoms
        sMockSatelliteServiceManager.executeTelephonyDebugServiceDumpsys(
                "--clearatoms", "--saveFileImmediately");

        // Send SMS
        sendSmsAutoConnect(TEST_DEST_ADDR, Activity.RESULT_OK);

        // Receive SMS
        receiveSmsAutoConnect();

        // Verify Atoms (Same as before)
        PersistAtomsProto.PersistAtoms atoms =
                sMockSatelliteServiceManager.pullMetricsAtomsViaDumpsys(false);
        assertNotNull("PersistAtoms should not be null", atoms);

        boolean outgoingSmsFound = false;
        if (atoms.outgoingSms != null) {
            for (OutgoingSms atom : atoms.outgoingSms) {
                if (atom.isNtn || atom.isNbIotNtn) {
                    logd(TAG, "Found OutgoingSms atom. PLMN: " + atom.plmn);
                    assertEquals("Outgoing SMS PLMN should match", SATELLITE_PLMN, atom.plmn);
                    outgoingSmsFound = true;
                }
            }
        }
        assertTrue("Did not find NTN OutgoingSms atom with correct PLMN", outgoingSmsFound);

        boolean incomingSmsFound = false;
        if (atoms.incomingSms != null) {
            for (IncomingSms atom : atoms.incomingSms) {
                if (atom.isNtn || atom.isNbIotNtn) {
                    logd(TAG, "Found IncomingSms atom. PLMN: " + atom.plmn);
                    assertEquals("Incoming SMS PLMN should match", SATELLITE_PLMN, atom.plmn);
                    incomingSmsFound = true;
                }
            }
        }
        assertTrue("Did not find NTN IncomingSms atom with correct PLMN", incomingSmsFound);
    }

    @Test
    public void testConfigureEmergencyAndDisasterPlmns() throws Exception {
        logd(TAG, "testConfigureEmergencyAndDisasterPlmns");
        if (!shouldTestSatelliteWithMockService()) return;

        assumeTrue(
                sMockSatelliteServiceManager.overrideSatelliteEntilementQueryConditions(
                        true, true));
        try {
            logd(TAG, "testConfigureEmergencyAndDisasterPlmns: test entitlement disabled");
            int subId = SubscriptionManager.getSubscriptionId(SLOT_ID_0);
            sMockModemManager.clearEventOnSetSatellitePlmn();
            sMockModemManager.clearEventOnSetSatelliteEnabledForCarrier();
            sMockSatelliteServiceManager.clearEventOnSetSatellitePlmn();
            sMockSatelliteServiceManager.clearEventOnSetSatelliteEnabledForCarrier();
            prepareValidDisabledEntitlementStatus();
            enableSatelliteEntitlementSupport(subId);

            // Telephony should have requested the modem to disable satellite for the carrier.
            waitForAccessRestrictionReason(subId,
                    SatelliteManager.SATELLITE_COMMUNICATION_RESTRICTION_REASON_ENTITLEMENT);
            waitForSatelliteDisabledForCarrier(SLOT_ID_0);
            // Verify that the PLMN list come from carrier config.
            String satellitePlmn = sMockModemManager.getSimInfo(SLOT_ID_0, SIM_INFO_TYPE_MCC_MNC);
            List<String> expectedTelephonyCarrierPlmnList = new ArrayList<>();
            List<String> expectedConfiguredCarrierPlmnList = new ArrayList<>();
            expectedTelephonyCarrierPlmnList.add(satellitePlmn);
            expectedConfiguredCarrierPlmnList.add(satellitePlmn);
            waitForCarrierPlmnListConfigured(SLOT_ID_0, expectedConfiguredCarrierPlmnList);
            waitForCarrierPlmnListAvailableInTelephony(subId, expectedTelephonyCarrierPlmnList);

            // Enable emergency and disaster services support for the carrier.
            logd(TAG, "testConfigureEmergencyAndDisasterPlmns: test emergency and disaster"
                    + " services enabled");
            expectedConfiguredCarrierPlmnList.clear();
            expectedConfiguredCarrierPlmnList.add("00101");
            expectedConfiguredCarrierPlmnList.add("10101");
            expectedTelephonyCarrierPlmnList.add("00101");
            expectedTelephonyCarrierPlmnList.add("10101");
            enableEmergencyAndDisasterServicesSupport(SLOT_ID_0,
                    ImmutableMap.of("00101", ImmutableList.of(
                            NetworkRegistrationInfo.SERVICE_TYPE_EMERGENCY_SMS)),
                    ImmutableMap.of("10101", ImmutableList.of(
                            NetworkRegistrationInfo.SERVICE_TYPE_SMS)));
            waitForSatelliteEnabledForCarrier(SLOT_ID_0);
            waitForCarrierPlmnListConfigured(SLOT_ID_0, expectedConfiguredCarrierPlmnList);
            waitForCarrierPlmnListAvailableInTelephony(subId, expectedTelephonyCarrierPlmnList);
        } finally {
            logd(TAG, "testConfigureEmergencyAndDisasterPlmns: restore test environment");
            sMockSatelliteServiceManager.overrideSatelliteEntilementQueryConditions(false, false);
            sMockSatelliteServiceManager.overrideSatelliteEntilementStatusResponseForCtsTest(
                    null, false);
        }
    }

    private PersistableBundle createBundle(String key, int[] value) {
        PersistableBundle bundle = new PersistableBundle();
        bundle.putIntArray(key, value);
        return bundle;
    }

    /**
     * Validates if SignalThresholdInfo build for NGRAN succeeds or fails as expected.
     *
     * @param signalMeasurementType The signal measurement type.
     * @param thresholds The thresholds to test.
     * @param expectException True if build() should throw IllegalArgumentException.
     * @return True if the build() behavior matches expectException, false otherwise.
     */
    public static boolean validateThresholds(
            int signalMeasurementType, int[] thresholds, boolean expectException) {
        String testCaseDescription =
                "Type="
                        + signalMeasurementType
                        + ", Thresh="
                        + Arrays.toString(thresholds)
                        + ", ExpectException="
                        + expectException;
        try {
            new SignalThresholdInfo.Builder()
                    .setRadioAccessNetworkType(AccessNetworkConstants.AccessNetworkType.NGRAN)
                    .setSignalMeasurementType(signalMeasurementType)
                    .setThresholds(thresholds)
                    .setHysteresisMs(0)
                    .setHysteresisDb(0)
                    .build();
            if (expectException) {
                loge("IllegalArgumentException was expected for " + testCaseDescription);
                return false;
            } else {
                return true;
            }
        } catch (IllegalArgumentException e) {
            if (expectException) {
                return true;
            } else {
                loge(
                        "IllegalArgumentException was not expected for "
                                + testCaseDescription
                                + ", but got: "
                                + e.getMessage());
                return false;
            }
        } catch (Exception e) {
            loge("Unexpected exception for " + testCaseDescription + ", " + e.getMessage());
            return false;
        }
    }

    /**
     * Tests the validation(e.g., length, out of bounds) of {@link
     * CarrierConfigManager#KEY_NTN_5G_NR_SSRSRQ_THRESHOLDS_INT_ARRAY}.
     */
    @Test
    public void testNtn5gNrSsrsrpThresholdsValidCheck() throws Exception {
        if (!shouldTestSatelliteWithMockService()) {
            logd(TAG, "Skipping testNtn5gNrSsrsrpThresholds: Mock service not available.");
            return;
        }

        int subId = SubscriptionManager.getSubscriptionId(SLOT_ID_0);
        if (!isActiveSubId(subId)) {
            logd(
                    TAG,
                    "Skipping testNtn5gNrSsrsrpThresholds: No active subscription on slot "
                            + SLOT_ID_0);
            return;
        }

        CarrierConfigManager carrierConfigManager =
                getContext().getSystemService(CarrierConfigManager.class);
        assertTrue("CarrierConfigManager should not be null", carrierConfigManager != null);

        try {
            int[] validThresholds = new int[] {-120, -110, -100, -90};
            logd(TAG, "Testing valid values: " + Arrays.toString(validThresholds));
            overrideCarrierConfig(
                    subId,
                    createBundle(
                            CarrierConfigManager.KEY_NTN_5G_NR_SSRSRP_THRESHOLDS_INT_ARRAY,
                            validThresholds));
            assertTrue(
                    validateThresholds(
                            SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_SSRSRP,
                            carrierConfigManager
                                    .getConfigForSubId(subId)
                                    .getIntArray(
                                            CarrierConfigManager
                                                    .KEY_NTN_5G_NR_SSRSRP_THRESHOLDS_INT_ARRAY),
                            false));
            overrideCarrierConfig(subId, null);

            int[] boundaryThresholds = new int[] {-140, -100, -60, -44};
            logd(TAG, "Testing boundary values: " + Arrays.toString(boundaryThresholds));
            overrideCarrierConfig(
                    subId,
                    createBundle(
                            CarrierConfigManager.KEY_NTN_5G_NR_SSRSRP_THRESHOLDS_INT_ARRAY,
                            boundaryThresholds));
            assertTrue(
                    validateThresholds(
                            SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_SSRSRP,
                            carrierConfigManager
                                    .getConfigForSubId(subId)
                                    .getIntArray(
                                            CarrierConfigManager
                                                    .KEY_NTN_5G_NR_SSRSRP_THRESHOLDS_INT_ARRAY),
                            false));
            overrideCarrierConfig(subId, null);

            int[] outOfBoundsLower = new int[] {-141, -110, -100, -90};
            logd(TAG, "Testing out of bounds lower: " + Arrays.toString(outOfBoundsLower));
            overrideCarrierConfig(
                    subId,
                    createBundle(
                            CarrierConfigManager.KEY_NTN_5G_NR_SSRSRP_THRESHOLDS_INT_ARRAY,
                            outOfBoundsLower));
            assertTrue(
                    validateThresholds(
                            SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_SSRSRP,
                            carrierConfigManager
                                    .getConfigForSubId(subId)
                                    .getIntArray(
                                            CarrierConfigManager
                                                    .KEY_NTN_5G_NR_SSRSRP_THRESHOLDS_INT_ARRAY),
                            true));
            overrideCarrierConfig(subId, null);

            int[] outOfBoundsUpper = new int[] {-120, -110, -100, -43};
            logd(TAG, "Testing out of bounds upper: " + Arrays.toString(outOfBoundsUpper));
            overrideCarrierConfig(
                    subId,
                    createBundle(
                            CarrierConfigManager.KEY_NTN_5G_NR_SSRSRP_THRESHOLDS_INT_ARRAY,
                            outOfBoundsUpper));
            assertTrue(
                    validateThresholds(
                            SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_SSRSRP,
                            carrierConfigManager
                                    .getConfigForSubId(subId)
                                    .getIntArray(
                                            CarrierConfigManager
                                                    .KEY_NTN_5G_NR_SSRSRP_THRESHOLDS_INT_ARRAY),
                            true));
            overrideCarrierConfig(subId, null);
        } finally {
            // Final restoration to ensure defaults are set
            overrideCarrierConfig(subId, null);
            logd(TAG, "Test finished, restored default carrier config.");
        }
    }

    /**
     * Tests the validation(e.g., length, out of bounds) of {@link
     * CarrierConfigManager#KEY_NTN_5G_NR_SSSINR_THRESHOLDS_INT_ARRAY}.
     */
    @Test
    public void testNtn5gNrSssinrThresholdsValidCheck() throws Exception {
        if (!shouldTestSatelliteWithMockService()) {
            logd(TAG, "Skipping testNtn5gNrSssinrThresholds: Mock service not available.");
            return;
        }

        int subId = SubscriptionManager.getSubscriptionId(SLOT_ID_0);
        if (!isActiveSubId(subId)) {
            logd(
                    TAG,
                    "Skipping testNtn5gNrSssinrThresholds: No active subscription on slot "
                            + SLOT_ID_0);
            return;
        }

        CarrierConfigManager carrierConfigManager =
                getContext().getSystemService(CarrierConfigManager.class);
        assertTrue("CarrierConfigManager should not be null", carrierConfigManager != null);

        try {
            int[] validThresholds = new int[] {-20, 0, 15, 35};
            logd(TAG, "Testing valid values: " + Arrays.toString(validThresholds));
            overrideCarrierConfig(
                    subId,
                    createBundle(
                            CarrierConfigManager.KEY_NTN_5G_NR_SSSINR_THRESHOLDS_INT_ARRAY,
                            validThresholds));
            assertTrue(
                    validateThresholds(
                            SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_SSSINR,
                            carrierConfigManager
                                    .getConfigForSubId(subId)
                                    .getIntArray(
                                            CarrierConfigManager
                                                    .KEY_NTN_5G_NR_SSSINR_THRESHOLDS_INT_ARRAY),
                            false));
            overrideCarrierConfig(subId, null);

            int[] boundaryThresholds = new int[] {-23, 0, 20, 40};
            logd(TAG, "Testing boundary values: " + Arrays.toString(boundaryThresholds));
            overrideCarrierConfig(
                    subId,
                    createBundle(
                            CarrierConfigManager.KEY_NTN_5G_NR_SSSINR_THRESHOLDS_INT_ARRAY,
                            boundaryThresholds));
            assertTrue(
                    validateThresholds(
                            SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_SSSINR,
                            carrierConfigManager
                                    .getConfigForSubId(subId)
                                    .getIntArray(
                                            CarrierConfigManager
                                                    .KEY_NTN_5G_NR_SSSINR_THRESHOLDS_INT_ARRAY),
                            false));
            overrideCarrierConfig(subId, null);

            int[] outOfBoundsLower = new int[] {-24, 0, 15, 35};
            logd(TAG, "Testing out of bounds lower: " + Arrays.toString(outOfBoundsLower));
            overrideCarrierConfig(
                    subId,
                    createBundle(
                            CarrierConfigManager.KEY_NTN_5G_NR_SSSINR_THRESHOLDS_INT_ARRAY,
                            outOfBoundsLower));
            assertTrue(
                    validateThresholds(
                            SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_SSSINR,
                            carrierConfigManager
                                    .getConfigForSubId(subId)
                                    .getIntArray(
                                            CarrierConfigManager
                                                    .KEY_NTN_5G_NR_SSSINR_THRESHOLDS_INT_ARRAY),
                            true));
            overrideCarrierConfig(subId, null);

            int[] outOfBoundsUpper = new int[] {-20, 0, 15, 41};
            logd(TAG, "Testing out of bounds upper: " + Arrays.toString(outOfBoundsUpper));
            overrideCarrierConfig(
                    subId,
                    createBundle(
                            CarrierConfigManager.KEY_NTN_5G_NR_SSSINR_THRESHOLDS_INT_ARRAY,
                            outOfBoundsUpper));
            assertTrue(
                    validateThresholds(
                            SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_SSSINR,
                            carrierConfigManager
                                    .getConfigForSubId(subId)
                                    .getIntArray(
                                            CarrierConfigManager
                                                    .KEY_NTN_5G_NR_SSSINR_THRESHOLDS_INT_ARRAY),
                            true));
            overrideCarrierConfig(subId, null);
        } finally {
            // Final restoration to ensure defaults are set
            overrideCarrierConfig(subId, null);
            logd(TAG, "Test finished, restored default carrier config.");
        }
    }

    /**
     * Tests the validation(e.g., length, out of bounds) of {@link
     * CarrierConfigManager#KEY_NTN_5G_NR_SSRSRQ_THRESHOLDS_INT_ARRAY}.
     */
    @Test
    public void testNtn5gNrSsrsrqThresholdsValidCheck() throws Exception {
        if (!shouldTestSatelliteWithMockService()) {
            logd(TAG, "Skipping testNtn5gNrSsrsrqThresholds: Mock service not available.");
            return;
        }

        int subId = SubscriptionManager.getSubscriptionId(SLOT_ID_0);
        if (!isActiveSubId(subId)) {
            logd(
                    TAG,
                    "Skipping testNtn5gNrSsrsrqThresholds: No active subscription on slot "
                            + SLOT_ID_0);
            return;
        }

        CarrierConfigManager carrierConfigManager =
                getContext().getSystemService(CarrierConfigManager.class);
        assertTrue("CarrierConfigManager should not be null", carrierConfigManager != null);

        try {
            int[] validThresholds = new int[] {-40, -30, -10, 10};
            logd(TAG, "Testing valid values: " + Arrays.toString(validThresholds));
            overrideCarrierConfig(
                    subId,
                    createBundle(
                            CarrierConfigManager.KEY_NTN_5G_NR_SSRSRQ_THRESHOLDS_INT_ARRAY,
                            validThresholds));
            assertTrue(
                    validateThresholds(
                            SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_SSRSRQ,
                            carrierConfigManager
                                    .getConfigForSubId(subId)
                                    .getIntArray(
                                            CarrierConfigManager
                                                    .KEY_NTN_5G_NR_SSRSRQ_THRESHOLDS_INT_ARRAY),
                            false));
            overrideCarrierConfig(subId, null);

            int[] boundaryThresholds = new int[] {-43, -20, 0, 20};
            logd(TAG, "Testing boundary values: " + Arrays.toString(boundaryThresholds));
            overrideCarrierConfig(
                    subId,
                    createBundle(
                            CarrierConfigManager.KEY_NTN_5G_NR_SSRSRQ_THRESHOLDS_INT_ARRAY,
                            boundaryThresholds));
            assertTrue(
                    validateThresholds(
                            SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_SSRSRQ,
                            carrierConfigManager
                                    .getConfigForSubId(subId)
                                    .getIntArray(
                                            CarrierConfigManager
                                                    .KEY_NTN_5G_NR_SSRSRQ_THRESHOLDS_INT_ARRAY),
                            false));
            overrideCarrierConfig(subId, null);

            int[] outOfBoundsLower = new int[] {-44, -30, -20, -10};
            logd(TAG, "Testing out of bounds lower: " + Arrays.toString(outOfBoundsLower));
            overrideCarrierConfig(
                    subId,
                    createBundle(
                            CarrierConfigManager.KEY_NTN_5G_NR_SSRSRQ_THRESHOLDS_INT_ARRAY,
                            outOfBoundsLower));
            assertTrue(
                    validateThresholds(
                            SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_SSRSRQ,
                            carrierConfigManager
                                    .getConfigForSubId(subId)
                                    .getIntArray(
                                            CarrierConfigManager
                                                    .KEY_NTN_5G_NR_SSRSRQ_THRESHOLDS_INT_ARRAY),
                            true));
            overrideCarrierConfig(subId, null);

            int[] outOfBoundsUpper = new int[] {-40, -30, -10, 21};
            logd(TAG, "Testing out of bounds upper: " + Arrays.toString(outOfBoundsUpper));
            overrideCarrierConfig(
                    subId,
                    createBundle(
                            CarrierConfigManager.KEY_NTN_5G_NR_SSRSRQ_THRESHOLDS_INT_ARRAY,
                            outOfBoundsUpper));
            assertTrue(
                    validateThresholds(
                            SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_SSRSRQ,
                            carrierConfigManager
                                    .getConfigForSubId(subId)
                                    .getIntArray(
                                            CarrierConfigManager
                                                    .KEY_NTN_5G_NR_SSRSRQ_THRESHOLDS_INT_ARRAY),
                            true));
            overrideCarrierConfig(subId, null);
        } finally {
            // Final restoration to ensure defaults are set
            overrideCarrierConfig(subId, null);
            logd(TAG, "Test finished, restored default carrier config.");
        }
    }

    @Test
    public void testVoWiFiRoamingModeSettingUsingNonTerrestrialNetwork() throws Exception {
        logd(TAG, "testVoWiFiRoamingModeSettingUsingNonTerrestrialNetwork");
        if (!shouldTestSatelliteWithMockService()) return;

        CarrierRoamingNtnListenerTest listener = new CarrierRoamingNtnListenerTest();
        listener.clearModeChanges();

        adoptShellIdentity();
        sTelephonyManager.registerTelephonyCallback(getContext().getMainExecutor(), listener);
        try {
            // Get NTN mode immediately after registering
            assertTrue(listener.waitForModeChanged(1));
            assertTrue(listener.getNtnMode());

            ImsManager imsManager = getContext().getSystemService(ImsManager.class);
            int subId = SubscriptionManager.getSubscriptionId(SLOT_ID_0);
            ImsMmTelManager imsMmTelManager = imsManager.getImsMmTelManager(subId);

            // getVoWiFiRoamingModeSetting() should return WIFI_PREFERRED
            // when device is connected to NTN
            int wfcRoamingMode = imsMmTelManager.getVoWiFiRoamingModeSetting();
            assertEquals(ImsMmTelManager.WIFI_MODE_WIFI_PREFERRED, wfcRoamingMode);
        } finally {
            sTelephonyManager.unregisterTelephonyCallback(listener);
            dropShellIdentity();
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_SATELLITE_26Q2_APIS)
    public void testGetPlmnSatelliteConfig() {
        logd("testGetPlmnSatelliteConfig");
        if (!shouldTestSatelliteWithMockService()) return;

        int subId = SubscriptionManager.getSubscriptionId(SLOT_ID_0);

        CarrierRoamingNtnListenerTest listener = new CarrierRoamingNtnListenerTest();
        listener.clearModeChanges();

        adoptShellIdentity();
        sTelephonyManager.registerTelephonyCallback(getContext().getMainExecutor(), listener);
        try {
            // Get NTN available services immediately after registering
            assertTrue(listener.waitForNtnAvailableServicesChanged(1));
            listener.clearModeChanges();

            PersistableBundle bundle = new PersistableBundle();
            PersistableBundle plmnBundle = new PersistableBundle();
            int[] intArray1 = {3, 5};
            Set<Integer> setIntArray1 =
                    Arrays.stream(intArray1).boxed().collect(Collectors.toSet());
            int[] intArray2 = {3};
            Set<Integer> setIntArray2 =
                    Arrays.stream(intArray2).boxed().collect(Collectors.toSet());
            plmnBundle.putIntArray("123411", intArray1);
            plmnBundle.putIntArray("123412", intArray2);
            bundle.putPersistableBundle(
                    CarrierConfigManager
                            .KEY_CARRIER_SUPPORTED_SATELLITE_SERVICES_PER_PROVIDER_BUNDLE,
                    plmnBundle);
            overrideCarrierConfig(subId, bundle);

            assertTrue(listener.waitForNtnAvailableServicesChanged(1));

            PlmnSatelliteConfig config = sSatelliteManager.getPlmnSatelliteConfig(subId, "123411");
            Assert.assertEquals(setIntArray1, config.getSupportedServices());
            config = sSatelliteManager.getPlmnSatelliteConfig(subId, "123412");
            Assert.assertEquals(setIntArray2, config.getSupportedServices());
        } finally {
            sTelephonyManager.unregisterTelephonyCallback(listener);
            dropShellIdentity();
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_SATELLITE_26Q2_APIS)
    public void testGetCarrierRoamingNtnAvailableServices() {
        logd("testGetCarrierRoamingNtnAvailableServices");
        if (!shouldTestSatelliteWithMockService()) return;

        int subId = SubscriptionManager.getSubscriptionId(SLOT_ID_0);

        CarrierRoamingNtnListenerTest listener = new CarrierRoamingNtnListenerTest();
        listener.clearModeChanges();

        adoptShellIdentity();
        sTelephonyManager.registerTelephonyCallback(getContext().getMainExecutor(), listener);
        try {
            // Get NTN available services immediately after registering
            assertTrue(listener.waitForNtnAvailableServicesChanged(1));
            listener.clearModeChanges();

            PersistableBundle bundle = new PersistableBundle();
            PersistableBundle plmnBundle = new PersistableBundle();
            int[] intArray1 = {3, 5};
            int[] intArray2 = {3};
            plmnBundle.putIntArray("123411", intArray1);
            plmnBundle.putIntArray("123412", intArray2);
            bundle.putPersistableBundle(
                    CarrierConfigManager
                            .KEY_CARRIER_SUPPORTED_SATELLITE_SERVICES_PER_PROVIDER_BUNDLE,
                    plmnBundle);
            overrideCarrierConfig(subId, bundle);

            assertTrue(listener.waitForNtnAvailableServicesChanged(1));

            int[] availableServices =
                    sSatelliteManager.getCarrierRoamingNtnAvailableServices(subId);
            Assert.assertArrayEquals(new int[] {3, 5}, availableServices);
        } finally {
            sTelephonyManager.unregisterTelephonyCallback(listener);
            dropShellIdentity();
        }
    }

    @Test
    @RequiresFlagsEnabled({Flags.FLAG_NR_NTN, Flags.FLAG_SATELLITE_26Q2_APIS})
    public void testSetSatelliteNetworkInfo() throws Exception {
        logd(TAG, "testSetSatelliteNetworkInfo");
        if (!shouldTestSatelliteWithMockService()) return;
        assumeTrue(
                "Skipping test: HAL version is lower than 2.4",
                getHalVersion(TelephonyManager.HAL_SERVICE_NETWORK) >= RADIO_HAL_VERSION_2_4);

        int subId = SubscriptionManager.getSubscriptionId(SLOT_ID_0);
        sMockModemManager.clearEventOnSetSatelliteNetworkInfo();

        String satellitePlmn = sMockModemManager.getSimInfo(SLOT_ID_0, SIM_INFO_TYPE_MCC_MNC);
        logd(TAG, "satellitePlmn is " + satellitePlmn);

        try {
            PersistableBundle bundle = new PersistableBundle();
            PersistableBundle satellitePlmnBundle = new PersistableBundle();
            PersistableBundle plmnConfig = new PersistableBundle();
            plmnConfig.putIntArray(
                    CarrierConfigManager.KEY_SATELLITE_TECHNOLOGY_INT_ARRAY,
                    new int[] {NT_RADIO_TECHNOLOGY_LTE_DTC});
            satellitePlmnBundle.putPersistableBundle(satellitePlmn, plmnConfig);
            bundle.putPersistableBundle(
                    CarrierConfigManager.KEY_SATELLITE_CONFIGS_PER_PLMN_BUNDLE,
                    satellitePlmnBundle);
            overrideCarrierConfig(subId, bundle);
            assertTrue(
                    "Modem should receive setSatelliteNetworkInfo "
                            + "when CarrierConfig is updated",
                    sMockModemManager.waitForEventOnSetSatelliteNetworkInfo(1));

            logd("Verify if allowed network info list is configured as expected");
            List<NetworkInfo> configuredAllowedNetworkInfoList =
                    getAllowedSatelliteNetworkInfoListConfigured(SLOT_ID_0);

            NetworkInfo expectedNetworkInfo = new NetworkInfo();
            expectedNetworkInfo.plmn = satellitePlmn;
            expectedNetworkInfo.satelliteTechnology = SatelliteTechnology.SAT_TECH_DTC;
            expectedNetworkInfo.accessNetwork = AccessNetwork.EUTRAN;
            expectedNetworkInfo.arfcns = new int[] {};
            expectedNetworkInfo.hasSamePriorityAsTn = false;
            List<NetworkInfo> expectedAllowedNetworkInfoList = List.of(expectedNetworkInfo);

            assertTrue(
                    areNetworkInfoListsTheSame(
                            expectedAllowedNetworkInfoList, configuredAllowedNetworkInfoList));

            logd("Verify if disallowed network info list is configured as expected");
            List<String> satellitePlmnListFromOverlayConfig =
                    sMockSatelliteServiceManager.getPlmnListFromOverlayConfig();
            List<String> satellitePlmnListFromCarrier =
                    sSatelliteManager.getSatellitePlmnsForCarrier(subId);
            List<String> mergedAllSatellitePlmnList =
                    SatelliteServiceUtils.mergeStrLists(
                            satellitePlmnListFromOverlayConfig, satellitePlmnListFromCarrier);
            logd(TAG, "expectedAllSatellitePlmnList: " + mergedAllSatellitePlmnList);
            List<String> expectedAllSatellitePlmnList = new ArrayList<>(mergedAllSatellitePlmnList);
            assertTrue(
                    "carrier plmn should be included",
                    expectedAllSatellitePlmnList.remove(satellitePlmn));

            List<NetworkInfo> expectedDisallowedSatelliteNetworkInfoList =
                    getDefaultNetworkInfoList(expectedAllSatellitePlmnList);
            List<NetworkInfo> configuredDisallowedSatelliteNetworkInfoList =
                    getDisallowedSatelliteNetworkInfoListConfigured(SLOT_ID_0);
            assertNotNull(configuredDisallowedSatelliteNetworkInfoList);
            logd(
                    TAG,
                    "expectedDisallowedSatelliteNetworkInfoList:"
                            + logNetworkInfoList(expectedDisallowedSatelliteNetworkInfoList));
            logd(
                    TAG,
                    "configuredDisallowedSatelliteNetworkInfoList:"
                            + logNetworkInfoList(configuredDisallowedSatelliteNetworkInfoList));
            assertTrue(
                    areNetworkInfoListsTheSame(
                            expectedDisallowedSatelliteNetworkInfoList,
                            configuredDisallowedSatelliteNetworkInfoList));
            assertTrue(
                    "No common items allowed",
                    haveNoCommonNetworkInfoItems(
                            configuredAllowedNetworkInfoList,
                            configuredDisallowedSatelliteNetworkInfoList));
        } finally {
            overrideCarrierConfig(subId, null);
        }
    }

    protected static class NtnStateCallback extends TelephonyCallback
            implements TelephonyCallback.ServiceStateListener {
        private final boolean mExpectedNtnState;
        private final int mExpectedSatelliteTech;
        private final CountDownLatch mLatch = new CountDownLatch(1);

        /**
         * Constructs a new NtnStateCallback with the expected NTN state and satellite technology.
         *
         * @param expectedState The expected Non-Terrestrial Network (NTN) registration state.
         * @param satelliteTech The expected {@link SatelliteManager.NTRadioTechnology}.
         */
        NtnStateCallback(boolean expectedState, int satelliteTech) {
            super();
            mExpectedNtnState = expectedState;
            mExpectedSatelliteTech = satelliteTech;
        }

        @Override
        public void onServiceStateChanged(ServiceState serviceState) {
            if (serviceState == null) return;
            logd(TAG, "NtnStateCallback:onServiceStateChanged: " + serviceState);
            NetworkRegistrationInfo nri =
                    serviceState.getNetworkRegistrationInfo(
                            NetworkRegistrationInfo.DOMAIN_PS,
                            AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
            if (nri != null) {
                int sateTechFromNetwork = nri.getSatelliteTechnology();
                boolean isNtn = nri.isNonTerrestrialNetwork();
                String registeredPlmn = nri.getRegisteredPlmn();
                logd(
                        TAG,
                        "NtnStateCallback: isNonTerrestrialNetwork: "
                                + isNtn
                                + ", mExpectedNtnState: "
                                + mExpectedNtnState
                                + ", nri.getSatelliteTechnology: "
                                + sateTechFromNetwork
                                + ", mExpectedSatelliteTech: "
                                + mExpectedSatelliteTech);

                if (sateTechFromNetwork == mExpectedSatelliteTech && isNtn == mExpectedNtnState) {
                    if (mLatch.getCount() > 0) {
                        mLatch.countDown();
                    }
                }
            } else {
                logd(TAG, "NtnStateCallback: NetworkRegistrationInfo is null");
            }
        }

        public boolean awaitStateChange(long timeoutMillis) throws InterruptedException {
            boolean result = mLatch.await(timeoutMillis, TimeUnit.MILLISECONDS);
            logd(TAG, "NtnStateCallback: awaitStateChange: result is " + result);
            return result;
        }
    }

    private void clearCarrierConfigurationForPlmn(int subId) {
        logd(TAG, "Clear all carrier configuration for plmn");
        PersistableBundle bundle = new PersistableBundle();
        bundle.putPersistableBundle(
                CarrierConfigManager.KEY_CARRIER_SUPPORTED_SATELLITE_SERVICES_PER_PROVIDER_BUNDLE,
                PersistableBundle.EMPTY);
        bundle.putPersistableBundle(
                CarrierConfigManager.KEY_SATELLITE_CONFIGS_PER_PLMN_BUNDLE,
                PersistableBundle.EMPTY);
        bundle.putStringArray(
                CarrierConfigManager.KEY_SATELLITE_SUPPORTED_EMERGENCY_PLMN_STRING_ARRAY,
                new String[0]);
        bundle.putStringArray(
                CarrierConfigManager.KEY_SATELLITE_SUPPORTED_DISASTER_PLMN_STRING_ARRAY,
                new String[0]);
        overrideCarrierConfig(subId, bundle);
    }

    @Test
    @RequiresFlagsEnabled({Flags.FLAG_NR_NTN, Flags.FLAG_SATELLITE_26Q2_APIS})
    public void testNtnRecognition_WithUnlistedPlmn_BasedOnModemReport() throws Exception {
        logd(TAG, "testNtnRecognition_WithUnlistedPlmn_BasedOnModemReport");
        if (!shouldTestSatelliteWithMockService()) return;
        assumeTrue(
                "Skipping test: HAL version is lower than 2.4",
                getHalVersion(TelephonyManager.HAL_SERVICE_NETWORK) >= RADIO_HAL_VERSION_2_4);

        int subId = SubscriptionManager.getSubscriptionId(SLOT_ID_0);
        clearCarrierConfigurationForPlmn(subId);
        CarrierRoamingNtnListenerTest roamingListener = new CarrierRoamingNtnListenerTest();
        roamingListener.clearModeChanges();
        sTelephonyManager.registerTelephonyCallback(
                getContext().getMainExecutor(), roamingListener);
        try {
            logd(TAG, "Set the network mode to TN (NTN=false, Tech=UNKNOWN)");
            NtnStateCallback ntnCallbackFalse =
                    new NtnStateCallback(false, SatelliteManager.NT_RADIO_TECHNOLOGY_UNKNOWN);
            sTelephonyManager.registerTelephonyCallback(
                    getContext().getMainExecutor(), ntnCallbackFalse);
            try {
                logd(TAG, "No satellite technology (NONE) and a PLMN not in config");
                sMockModemManager.setSatelliteTechnology(
                        SLOT_ID_0, SatelliteTechnology.SAT_TECH_NONE);
                sMockModemManager.changeNetworkService(
                        SLOT_ID_0, MOCK_SIM_PROFILE_ID_TWN_CHT, true);

                logd(TAG, "Verify: NTN=false, Tech=UNKNOWN");
                assertTrue(
                        "Failed to receive ntn=false state change",
                        ntnCallbackFalse.awaitStateChange(TIMEOUT));
                logd(TAG, "Verify: Check the status of the roaming listener");
                assertFalse("Should be in terrestrial mode", roamingListener.getNtnMode());
            } finally {
                sTelephonyManager.unregisterTelephonyCallback(ntnCallbackFalse);
            }

            roamingListener.clearModeChanges();
            logd(TAG, "Set the network as NTN (NTN=true, Tech=LTE_DTC, Plmn=unconfigured)");
            NtnStateCallback ntnCallbackTrue =
                    new NtnStateCallback(true, SatelliteManager.NT_RADIO_TECHNOLOGY_LTE_DTC);
            sTelephonyManager.registerTelephonyCallback(
                    getContext().getMainExecutor(), ntnCallbackTrue);
            try {
                sMockModemManager.setSatelliteTechnology(
                        SLOT_ID_0, SatelliteTechnology.SAT_TECH_DTC);
                assertNotNull(sMockModemManager.getAllSatellitePlmnList(SLOT_ID_0));
                assertFalse(
                        "Should not be included in configured all plmn",
                        sMockModemManager
                                .getAllSatellitePlmnList(SLOT_ID_0)
                                .contains(
                                        sTelephonyManager.getServiceState().getOperatorNumeric()));
                assertTrue(
                        "Failed to receive ntn=true state change based on modem report",
                        ntnCallbackTrue.awaitStateChange(TIMEOUT));
                assertTrue(roamingListener.waitForModeChanged(1));
                assertTrue("Should be NTN mode", roamingListener.getNtnMode());
            } finally {
                sTelephonyManager.unregisterTelephonyCallback(ntnCallbackTrue);
            }
        } finally {
            sTelephonyManager.unregisterTelephonyCallback(roamingListener);
            overrideCarrierConfig(subId, null);
            sMockModemManager.setSatelliteTechnology(SLOT_ID_0, SatelliteTechnology.SAT_TECH_NONE);
        }
    }
}
