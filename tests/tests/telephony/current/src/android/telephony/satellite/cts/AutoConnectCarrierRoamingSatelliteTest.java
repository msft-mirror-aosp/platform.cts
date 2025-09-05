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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.telephony.CarrierConfigManager;

import android.telephony.NetworkRegistrationInfo;
import android.telephony.SubscriptionManager;
import android.telephony.mockmodem.MockModemConfigBase;
import android.telephony.satellite.SatelliteManager;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

public class AutoConnectCarrierRoamingSatelliteTest extends CarrierRoamingSatelliteTestBase {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final String TAG = "AutoConnectCarrierRoamingSatelliteTest";

    /**
     * Setup before all tests.
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
        assumeTrue(shouldTestSatelliteWithMockService());
        setUpAutoConnectTestEnvironment(
            SLOT_ID_0, MOCK_SIM_PROFILE_ID_TWN_CHT, PHONE_NUMBER_0, true);
    }

    @After
    public void tearDown() throws Exception {
        logd(TAG, "tearDown()");
        assumeTrue(shouldTestSatelliteWithMockService());
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
        testQuerySatelliteEntitlementService_success(SLOT_ID_0,
            CarrierConfigManager.CARRIER_ROAMING_NTN_CONNECT_AUTOMATIC);
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
    public void testNoSatelliteConstrainedNetworkConnection_WithBandwidthNotConstrainedCapability()
            throws Exception {
        logd(TAG, "testNoConstrainedNetworkConnection");
        if (!shouldTestSatelliteWithMockService()) return;
        testNoSatelliteConstrainedNetworkConnection_WithBandwidthNotConstrainedCapability(SLOT_ID_0);
    }

    @Test
    public void testConfigureEmergencyAndDisasterPlmns() throws Exception {
        logd(TAG, "testConfigureEmergencyAndDisasterPlmns");
        if (!shouldTestSatelliteWithMockService()) return;

        assertTrue(sMockSatelliteServiceManager
                .overrideSatelliteEntilementQueryConditions(true, true));
        assertTrue(sMockSatelliteServiceManager.setSatelliteIgnorePlmnListFromStorage(true));
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
            String satellitePlmn = sMockModemManager.getSimInfo(SLOT_ID_0,
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
            sMockSatelliteServiceManager
                .overrideSatelliteEntilementQueryConditions(false, false);
            sMockSatelliteServiceManager
                .overrideSatelliteEntilementStatusResponseForCtsTest(null, false);
            sMockSatelliteServiceManager.setSatelliteIgnorePlmnListFromStorage(false);
        }
    }
}
