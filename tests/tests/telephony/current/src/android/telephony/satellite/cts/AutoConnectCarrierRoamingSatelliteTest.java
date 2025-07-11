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

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
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
        setUpAutoConnectTestEnvironment(
            SLOT_ID_0, MOCK_SIM_PROFILE_ID_TWN_CHT, PHONE_NUMBER_0, true);
    }

    /**
     * Cleanup resources after all tests.
     * @throws Exception exception
     */
    @AfterClass
    public static void afterAllTests() throws Exception {
        logd(TAG, "afterAllTests");
        if (!shouldTestSatelliteWithMockService()) return;

        cleanUpMockSim(SLOT_ID_0, MOCK_SIM_PROFILE_ID_TWN_CHT, true);
        afterAllCarrierRoamingTestsBase();
    }

    @Before
    public void setUp() throws Exception {
        logd(TAG, "setUp()");
        assumeTrue(shouldTestSatelliteWithMockService());
    }

    @After
    public void tearDown() throws Exception {
        logd(TAG, "tearDown()");
    }

    @Test
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
    public void testQuerySatelliteEntitlementService_success() throws Exception {
        logd(TAG, "testQuerySatelliteEntitlementService_success");
        if (!shouldTestSatelliteWithMockService()) return;
        testQuerySatelliteEntitlementService_success(SLOT_ID_0,
            CarrierConfigManager.CARRIER_ROAMING_NTN_CONNECT_AUTOMATIC);
    }

    @Test
    public void testSatelliteConstrainedNetwork() throws Exception {
        logd(TAG, "testSatelliteConstrainedNetwork");
        if (!shouldTestSatelliteWithMockService()) return;
        testSatelliteConstrainedNetwork(SLOT_ID_0);
    }

    @Test
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
}
