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

import static junit.framework.Assert.assertEquals;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.os.PersistableBundle;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.telephony.AccessNetworkConstants;
import android.telephony.CarrierConfigManager;
import android.telephony.ims.ImsManager;
import android.telephony.ims.ImsMmTelManager;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.ServiceState;
import android.telephony.SignalThresholdInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyCallback;
import android.telephony.mockmodem.MockModemConfigBase;
import android.telephony.satellite.PlmnSatelliteConfig;
import android.telephony.satellite.SatelliteManager;

import com.android.internal.telephony.flags.Flags;

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
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final String TAG = "AutoConnectCarrierRoamingSatelliteTest";

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
    }

    @After
    public void tearDown() throws Exception {
        logd(TAG, "tearDown()");
        if (!shouldTestSatelliteWithMockService()) return;
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

    @Test
    public void testConfigureEmergencyAndDisasterPlmns() throws Exception {
        logd(TAG, "testConfigureEmergencyAndDisasterPlmns");
        if (!shouldTestSatelliteWithMockService()) return;

        assertTrue(sMockSatelliteServiceManager
                .overrideSatelliteEntilementQueryConditions(true, true));
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
            String satellitePlmn =
                    sMockModemManager.getSimInfo(
                            SLOT_ID_0,
                            MockModemConfigBase.SimInfoChangedResult.SIM_INFO_TYPE_MCC_MNC);
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
            logd("Testing boundary values: " + Arrays.toString(boundaryThresholds));
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
            logd("Testing boundary values: " + Arrays.toString(boundaryThresholds));
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
            logd("Testing boundary values: " + Arrays.toString(boundaryThresholds));
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

    /**
     * Test that NetworkRegistrationInfo.isNonTerrestrialNetwork() reflects the state set in the
     * MockModemService.
     */
    @Test
    public void testIsNonTerrestrialNetworkState() throws Exception {
        if (!shouldTestSatelliteWithMockService()) {
            logd(TAG, "Skipping testIsNonTerrestrialNetworkState: Mock service not available.");
            return;
        }

        logd(TAG,
             "testIsNonTerrestrialNetworkState: satellite plmn registered, set network"
                 + " ntn=false");
        sMockModemManager.setNetworkIsNtn(SLOT_ID_0, false);
        ServiceState serviceState = sTelephonyManager.getServiceStateForSlot(SLOT_ID_0);
        if (serviceState != null) {
            NetworkRegistrationInfo nri =
                    serviceState.getNetworkRegistrationInfo(
                            NetworkRegistrationInfo.DOMAIN_PS,
                            AccessNetworkConstants.TRANSPORT_TYPE_WWAN);

            if (nri != null) {
                logd("testIsNonTerrestrialNetworkState: nri.isNonTerrestrialNetwork()");
                assertTrue(nri.isNonTerrestrialNetwork());
            }
        }

        logd(TAG, "testIsNonTerrestrialNetworkState: remove registered satellite plmn");
        disableSatellitePlmns(SLOT_ID_0);

        logd(TAG, "testIsNonTerrestrialNetworkState: set network ntn=false");
        NtnStateCallback ntnCallbackFalse = new NtnStateCallback(false);
        sTelephonyManager.registerTelephonyCallback(
                getContext().getMainExecutor(), ntnCallbackFalse);
        try {
            sMockModemManager.setNetworkIsNtn(SLOT_ID_0, false);
            assertTrue(
                    "Failed to receive ntn=false state change",
                    ntnCallbackFalse.awaitStateChange(TIMEOUT));
        } finally {
            sTelephonyManager.unregisterTelephonyCallback(ntnCallbackFalse);
        }

        logd(TAG, "testIsNonTerrestrialNetworkState: set network ntn=true");
        NtnStateCallback ntnCallbackTrue = new NtnStateCallback(true);
        sTelephonyManager.registerTelephonyCallback(
                getContext().getMainExecutor(), ntnCallbackTrue);
        try {
            sMockModemManager.setNetworkIsNtn(SLOT_ID_0, true);
            assertTrue(
                    "Failed to receive ntn=true state change",
                    ntnCallbackTrue.awaitStateChange(TIMEOUT));
        } finally {
            sTelephonyManager.unregisterTelephonyCallback(ntnCallbackTrue);
        }
    }

    protected static class NtnStateCallback extends TelephonyCallback
            implements TelephonyCallback.ServiceStateListener {
        private final boolean mExpectedState;
        private final CountDownLatch mLatch = new CountDownLatch(1);

        NtnStateCallback(boolean expectedState) {
            super();
            mExpectedState = expectedState;
        }

        @Override
        public void onServiceStateChanged(ServiceState serviceState) {
            if (serviceState == null) return;
            logd("NtnStateCallback:onServiceStateChanged: " + serviceState);

            NetworkRegistrationInfo nri =
                    serviceState.getNetworkRegistrationInfo(
                            NetworkRegistrationInfo.DOMAIN_PS,
                            AccessNetworkConstants.TRANSPORT_TYPE_WWAN);

            if (nri != null) {
                logd(
                        "NtnStateCallback: isNonTerrestrialNetwork: "
                                + nri.isNonTerrestrialNetwork()
                                + ", Expected: "
                                + mExpectedState);
                if (nri.isNonTerrestrialNetwork() == mExpectedState) {
                    if (mLatch.getCount() > 0) {
                        mLatch.countDown();
                    }
                }
            } else {
                logd("NtnStateCallback: NetworkRegistrationInfo is null");
            }
        }

        public boolean awaitStateChange(long timeoutMillis) throws InterruptedException {
            boolean result = mLatch.await(timeoutMillis, TimeUnit.MILLISECONDS);
            logd("NtnStateCallback: awaitStateChange: result is " + result);
            return result;
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
}
