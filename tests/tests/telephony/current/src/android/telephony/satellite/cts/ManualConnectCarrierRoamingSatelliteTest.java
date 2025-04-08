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

import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.telephony.CarrierConfigManager;
import android.telephony.satellite.SatelliteManager;
import android.telephony.satellite.stub.SatelliteResult;

import com.android.internal.telephony.flags.Flags;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

public class ManualConnectCarrierRoamingSatelliteTest extends CarrierRoamingSatelliteTestBase {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final String TAG = "ManualConnectCarrierRoamingSatelliteTest";
    private static final int ESOS_SLOT_ID = SLOT_ID_0;
    private static final int ESOS_SIM_PROFILE_ID = MOCK_SIM_PROFILE_ID_TWN_CHT;
    private static final String ESOS_PHONE_NUMBER = PHONE_NUMBER_0;

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
            ESOS_SLOT_ID, ESOS_SIM_PROFILE_ID, ESOS_PHONE_NUMBER);
    }

    /**
     * Cleanup resources after all tests.
     * @throws Exception exception
     */
    @AfterClass
    public static void afterAllTests() throws Exception {
        logd(TAG, "afterAllTests");
        cleanUpManualConnectTestEnvironment(ESOS_SLOT_ID, ESOS_SIM_PROFILE_ID);
        afterAllCarrierRoamingTestsBase();
    }

    @Before
    public void setUp() throws Exception {
        logd(TAG, "setUp()");
        if (!shouldTestManualConnectCarrierRoaming()) return;

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
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CARRIER_ROAMING_NB_IOT_NTN)
    public void testCarrierRoamingNtnEligible() throws Exception {
        if (!shouldTestManualConnectCarrierRoaming()) return;

        CarrierRoamingNtnListenerTest listener = new CarrierRoamingNtnListenerTest();
        listener.clearModeChanges();
        adoptShellIdentity();
        boolean originalWifiState = sWifiManager.isWifiEnabled();

        try {
            // Get NTN eligibility immediately after registering
            sTelephonyManager.registerTelephonyCallback(getContext().getMainExecutor(), listener);
            assertTrue(listener.waitForNtnEligible(1));
            assertFalse(listener.getNtnEligible());
            listener.clearModeChanges();

            if (originalWifiState) {
                logd(TAG, "Disable wifi");
                sWifiManager.setWifiEnabled(false);
                sWifiStateReceiver.setWifiExpectedState(false);
                assertTrue(sWifiStateReceiver.waitUntilWifiStateChanged());
            }
            listener.clearModeChanges();

            // Network is lost
            logd(TAG, "Move device to out of service state");
            sMockModemManager.changeNetworkService(ESOS_SLOT_ID, ESOS_SIM_PROFILE_ID, false);
            // The disconnected state will be processed only after hysteresis timeout
            assertFalse(listener.waitForNtnEligible(1));
            listener.clearModeChanges();

            // Callback is received after hysteresis timeout
            assertTrue(listener.waitForNtnEligible(1));
            assertTrue(listener.getNtnEligible());
        } finally {
            sWifiManager.setWifiEnabled(originalWifiState);
            sTelephonyManager.unregisterTelephonyCallback(listener);
            dropShellIdentity();
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
}
