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

import static android.telephony.mockmodem.MockSimService.MOCK_SIM_PROFILE_ID_TWN_FET;
import static android.telephony.satellite.cts.ManualConnectCarrierRoamingSatelliteTest.addCtsPackageToSupportedSmsApps;
import static android.telephony.satellite.cts.ManualConnectCarrierRoamingSatelliteTest.shouldTestManualConnectCarrierRoaming;

import static junit.framework.Assert.assertTrue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assume.assumeTrue;

import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.telephony.satellite.SatelliteManager;
import android.telephony.satellite.stub.SatelliteModemState;
import android.telephony.satellite.stub.SatelliteResult;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

public class HybridConnectCarrierRoamingSatelliteTest extends CarrierRoamingSatelliteTestBase {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final String TAG = "HybridConnectCarrierRoamingSatelliteTest";

    public static final int ESOS_SLOT_ID = SLOT_ID_0;

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
        assumeTrue(shouldTestSatelliteWithMockService());
    }

    @After
    public void tearDown() throws Exception {
        logd(TAG, "tearDown()");
    }

    /**
     * Set up before auto connect test cases
     *
     * @throws Exception exception
     */
    public void setUp_AutoConnect() throws Exception {
        setUpHybridConnectAutoTestEnvironment(
                SLOT_ID_0, MOCK_SIM_PROFILE_ID_TWN_FET, PHONE_NUMBER_0, true);
    }

    /**
     * Set up before manual connect test cases
     *
     * @throws Exception exception
     */
    public void setUp_ManualConnect() throws Exception {
        setUpHybridConnectManualTestEnvironment(
                ESOS_SLOT_ID, MOCK_SIM_PROFILE_ID_TWN_FET, PHONE_NUMBER_0, true, true, true);
        assumeTrue(shouldTestManualConnectCarrierRoaming());
        assumeTrue(sMockSatelliteServiceManager != null);

        addCtsPackageToSupportedSmsApps(sEsosSubId);

        assertTrue(sMockSatelliteServiceManager.setSatelliteIgnoreCellularServiceState(true));
        assertTrue(sMockSatelliteServiceManager.setSatelliteTnScanningSupport(false, false, true));
        assertTrue(
                sMockSatelliteServiceManager.setSupportDisableSatelliteWhileEnableInProgress(
                        false, true));
        sMockSatelliteServiceManager.setErrorCode(SatelliteResult.SATELLITE_RESULT_SUCCESS);
        sMockSatelliteServiceManager.setWaitToSend(false);
        sMockSatelliteServiceManager.setShouldRespondTelephony(true);
        sMockSatelliteServiceManager.setShouldRespondEnableRequest(true);
        sMockSatelliteServiceManager.mIsPointingUiOverridden = false;
        setUpSatelliteAccessAllowedAtDefaultTestLocation();
    }

    /**
     * Clean up after manual connect test cases
     *
     * @throws Exception exception
     */
    public void tearDown_ManualConnect() throws Exception {
        if (!shouldTestManualConnectCarrierRoaming()) return;
        assumeTrue(sMockSatelliteServiceManager != null);

        assertTrue(sMockSatelliteServiceManager.setSatelliteIgnoreCellularServiceState(false));
        assertTrue(sMockSatelliteServiceManager.setSatelliteTnScanningSupport(true, false, false));
        assertTrue(
                sMockSatelliteServiceManager.setSupportDisableSatelliteWhileEnableInProgress(
                        true, false));

        // Move satellite to off state to clean up all pending resources
        // and reset telephony states.
        moveSatelliteToOffState();
    }

    /**
     * Clean up after auto connect test cases
     *
     * @throws Exception exception
     */
    public void tearDown_AutoConnect() throws Exception {
        cleanUpMockSim(SLOT_ID_0, MOCK_SIM_PROFILE_ID_TWN_FET, true);
    }

    @Test
    @Ignore("b/449612427 - Need to fix and re-enable this test.")
    public void testCarrierRoamingNtnModeListener_AutoConnect() throws Exception {
        logd(TAG, "testCarrierRoamingNtnModeListener_AutoConnect");
        setUp_AutoConnect();

        CarrierRoamingNtnListenerTest listener = new CarrierRoamingNtnListenerTest();
        listener.clearModeChanges();

        grantSatellitePermission();
        SatelliteModemStateCallbackTest callback = new SatelliteModemStateCallbackTest();
        long registerResult =
                sSatelliteManager.registerForModemStateChanged(
                        getContext().getMainExecutor(), callback);
        assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerResult);
        assertTrue(callback.waitUntilResult(1));
        callback.clearModemStates();

        adoptShellIdentity();
        sTelephonyManager.registerTelephonyCallback(getContext().getMainExecutor(), listener);
        try {
            // Get NTN mode immediately after registering
            assertTrue(listener.waitForModeChanged(1));
            assertTrue(listener.getNtnMode());
            assertEquals(SatelliteModemState.SATELLITE_MODEM_STATE_OFF, callback.modemState);
            listener.clearModeChanges();

            // Satellite network is lost, no callback as hysteresis timeout is not expired
            sMockModemManager.changeNetworkService(SLOT_ID_0, MOCK_SIM_PROFILE_ID_TWN_FET, false);
            assertFalse(listener.waitForModeChanged(1));
            assertEquals(SatelliteModemState.SATELLITE_MODEM_STATE_OFF, callback.modemState);
            listener.clearModeChanges();

            // Callback is received after hysteresis timeout
            assertTrue(listener.waitForModeChanged(1));
            assertFalse(listener.getNtnMode());
            assertEquals(SatelliteModemState.SATELLITE_MODEM_STATE_OFF, callback.modemState);

            // Move back to satellite in service mode
            sMockModemManager.changeNetworkService(SLOT_ID_0, MOCK_SIM_PROFILE_ID_TWN_FET, true);
            assertTrue(listener.waitForModeChanged(1));
            assertTrue(listener.getNtnMode());
            assertEquals(SatelliteModemState.SATELLITE_MODEM_STATE_OFF, callback.modemState);
            listener.clearModeChanges();
        } finally {
            sTelephonyManager.unregisterTelephonyCallback(listener);
            sSatelliteManager.unregisterForModemStateChanged(callback);
            revokeSatellitePermission();
            dropShellIdentity();
            tearDown_AutoConnect();
        }
    }

    @Test
    public void testCarrierRoamingNtnModeListener_ManualConnect() throws Exception {
        logd(TAG, "testCarrierRoamingNtnModeListener_ManualConnect");
        setUp_ManualConnect();

        CarrierRoamingNtnListenerTest listener = new CarrierRoamingNtnListenerTest();
        listener.clearModeChanges();

        grantSatellitePermission();
        SatelliteModemStateCallbackTest callback = new SatelliteModemStateCallbackTest();
        long registerResult =
                sSatelliteManager.registerForModemStateChanged(
                        getContext().getMainExecutor(), callback);
        assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerResult);
        assertTrue(callback.waitUntilResult(1));
        callback.clearModemStates();

        adoptShellIdentity();
        sTelephonyManager.registerTelephonyCallback(getContext().getMainExecutor(), listener);
        try {
            // Satellite modem will be in not connected state after powered on
            assertEquals(
                    SatelliteModemState.SATELLITE_MODEM_STATE_OUT_OF_SERVICE, callback.modemState);
            // Send satellite modem to in service
            sMockSatelliteServiceManager.sendOnSatelliteModemStateChanged(
                    SatelliteModemState.SATELLITE_MODEM_STATE_IN_SERVICE);
            assertTrue(callback.waitUntilResult(1));
            assertTrue(listener.waitForModeChanged(1));
            assertTrue(listener.getNtnMode());
            listener.clearModeChanges();
            assertEquals(SatelliteModemState.SATELLITE_MODEM_STATE_IN_SERVICE, callback.modemState);

            // Satellite network is lost, no callback as hysteresis timeout is not expired
            sMockModemManager.changeNetworkService(SLOT_ID_0, MOCK_SIM_PROFILE_ID_TWN_FET, false);
            assertFalse(listener.waitForModeChanged(1));
            assertEquals(SatelliteModemState.SATELLITE_MODEM_STATE_IN_SERVICE, callback.modemState);
            sMockSatelliteServiceManager.sendOnSatelliteModemStateChanged(
                    SatelliteModemState.SATELLITE_MODEM_STATE_OUT_OF_SERVICE);

            // Callback is received after hysteresis timeout
            assertTrue(callback.waitUntilResult(1));
            //            assertTrue(listener.waitForModeChanged(1));
            assertFalse(listener.getNtnMode());
            assertEquals(
                    SatelliteModemState.SATELLITE_MODEM_STATE_OUT_OF_SERVICE, callback.modemState);

            // Clean up
            listener.clearModeChanges();
            moveSatelliteToOffState();
            assertTrue(callback.waitUntilModemOff());
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback.modemState);
        } finally {
            sTelephonyManager.unregisterTelephonyCallback(listener);
            sSatelliteManager.unregisterForModemStateChanged(callback);
            revokeSatellitePermission();
            dropShellIdentity();
            tearDown_ManualConnect();
            cleanUpMockSim(SLOT_ID_0, MOCK_SIM_PROFILE_ID_TWN_FET, true);
        }
    }
}
