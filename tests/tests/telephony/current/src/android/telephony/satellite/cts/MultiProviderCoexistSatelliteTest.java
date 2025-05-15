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
import static android.telephony.mockmodem.MockSimService.MOCK_SIM_PROFILE_ID_TWN_FET;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.telephony.satellite.SatelliteManager;
import android.util.Pair;

import com.android.internal.telephony.flags.Flags;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

public class MultiProviderCoexistSatelliteTest extends CarrierRoamingSatelliteTestBase {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final String TAG = "MultiProviderCoexistSatelliteTest";
    private static final int NTN_ONLY_SLOT_ID = SLOT_ID_0;
    private static final int NTN_ONLY_SIM_PROFILE_ID = MOCK_SIM_PROFILE_ID_TWN_FET;
    private static final String NTN_ONLY_PHONE_NUMBER = PHONE_NUMBER_0;
    private static final int ESOS_SLOT_ID = SLOT_ID_1;
    private static final int ESOS_SIM_PROFILE_ID = MOCK_SIM_PROFILE_ID_TWN_CHT;
    private static final String ESOS_PHONE_NUMBER = PHONE_NUMBER_1;

    private static boolean sIsMultiSimDevice = false;

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
        sIsMultiSimDevice = sTelephonyManager.isMultiSimEnabled();

        grantSatellitePermission();
        setupMockSatelliteService();
        assertTrue(sMockSatelliteServiceManager.setCtsMode(true));
    }

    /**
     * Cleanup resources after all tests.
     * @throws Exception exception
     */
    @AfterClass
    public static void afterAllTests() throws Exception {
        logd(TAG, "afterAllTests");
        if (!shouldTestSatelliteWithMockService()) return;

        assertTrue(sMockSatelliteServiceManager.setCtsMode(false));
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
    public void testSelectBindingSatelliteSubscription_ntnOnly_manualConnect()
        throws Exception {
        logd(TAG, "testSelectBindingSatelliteSubscription_ntnOnly_manualConnect");
        assumeTrue(shouldTestSatelliteWithMockService());
        assumeTrue("Skip test on single SIM device", sIsMultiSimDevice);
        assertEquals(getNumberOfActiveSubscriptions(), 0L);

        grantSatellitePermission();
        // Check if the device has a binding satellite subscription
        Pair<Integer, Integer> selectedSatelliteSubIdPairResult =
                requestSelectedNbIotSatelliteSubscriptionId();
        boolean isSatelliteSubIdSelected = false;
        int ntnOnlySubId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        if (selectedSatelliteSubIdPairResult.first != null
                && selectedSatelliteSubIdPairResult.first
                != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            isSatelliteSubIdSelected = true;
            ntnOnlySubId = selectedSatelliteSubIdPairResult.first;
            assertTrue(isNtnOnlySubscription(ntnOnlySubId));
        }

        // Register callback for satellite subscription id changed event
        SelectedNbIotSatelliteSubscriptionCallbackTest
            selectedNbIotSatelliteSubscriptionCallbackTest =
                new SelectedNbIotSatelliteSubscriptionCallbackTest();
        long registerResult =
            sSatelliteManager.registerForSelectedNbIotSatelliteSubscriptionChanged(
                getContext().getMainExecutor(),
                selectedNbIotSatelliteSubscriptionCallbackTest);
        assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerResult);
        selectedNbIotSatelliteSubscriptionCallbackTest.drainPermits();

        try {
            if (!isSatelliteSubIdSelected) {
                // Insert NTN-only SIM and set up NTN-only subscription
                logd(TAG, "testSelectBindingSatelliteSubscription_ntnOnly_manualConnect: "
                    + "insert NTN-only SIM and set up NTN-only subscription");
                setUpNtnOnlyTestEnvironment();

                // The NTN-only subscription should be selected as the binding
                // satellite subscription.
                waitForSelectedSatelliteSubscriptionChanged(
                        selectedNbIotSatelliteSubscriptionCallbackTest, sNtnOnlySubId);
                ntnOnlySubId = sNtnOnlySubId;
                selectedNbIotSatelliteSubscriptionCallbackTest.drainPermits();
            } else {
                logd(TAG, "testSelectBindingSatelliteSubscription_ntnOnly_manualConnect: "
                    + "no need to set up NTN-only subscription.");
            }

            // Insert manul-connect SIM and set up eSOS support for the subscription
            logd(TAG, "testSelectBindingSatelliteSubscription_ntnOnly_manualConnect: "
                + "insert manual-connect SIM and set up eSOS support for the subscription");
            setUpManualConnectTestEnvironment(
                ESOS_SLOT_ID, ESOS_SIM_PROFILE_ID, ESOS_PHONE_NUMBER, false, false);

            // The manual-connect subscription should be selected as the binding satellite
            // subscription.
            waitForSelectedSatelliteSubscriptionChanged(
                    selectedNbIotSatelliteSubscriptionCallbackTest, sEsosSubId);
            selectedNbIotSatelliteSubscriptionCallbackTest.drainPermits();

            // Move the device to out of the geofence region of the manual-connect subscription
            logd(TAG, "testSelectBindingSatelliteSubscription_ntnOnly_manualConnect: "
                + "move the device to out of the geofence region of the manual-connect subscription");
            assertTrue(sMockSatelliteServiceManager.setSatelliteAccessAllowedForSubscriptions(
                false, null));

            // The NTN-only subscription should be selected as the binding
            // satellite subscription.
            waitForSelectedSatelliteSubscriptionChanged(
                    selectedNbIotSatelliteSubscriptionCallbackTest, ntnOnlySubId);
            selectedNbIotSatelliteSubscriptionCallbackTest.drainPermits();
        } finally {
            logd(TAG, "testSelectBindingSatelliteSubscription_ntnOnly_manualConnect: "
                + "clean up test environments");
            grantSatellitePermission();
            sSatelliteManager.unregisterForSelectedNbIotSatelliteSubscriptionChanged(
                selectedNbIotSatelliteSubscriptionCallbackTest);
            cleanUpManualConnectTestEnvironment(ESOS_SLOT_ID, ESOS_SIM_PROFILE_ID);
            cleanUpNtnOnlyTestEnvironment();
        }
    }

    private static void setUpNtnOnlyTestEnvironment() throws Exception {
        logd(TAG, "setUpNtnOnlyTestEnvironment");
        assertTrue(sMockModemManager.insertSimCard(NTN_ONLY_SLOT_ID, NTN_ONLY_SIM_PROFILE_ID));
        TimeUnit.MILLISECONDS.sleep(TIMEOUT);
        moveSimToInService(NTN_ONLY_SLOT_ID, NTN_ONLY_SIM_PROFILE_ID);
        sNtnOnlySubId = SubscriptionManager.getSubscriptionId(NTN_ONLY_SLOT_ID);
        assumeTrue(sNtnOnlySubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        logd(TAG, "setUpNtnOnlyTestEnvironment: sNtnOnlySubId=" + sNtnOnlySubId);
        if (!isActiveSubId(sNtnOnlySubId)) {
            logd(TAG, "Skip the test because the NTN only subId is not active.");
            return;
        }
        // Set phone number
        setPhoneNumber(sNtnOnlySubId, NTN_ONLY_PHONE_NUMBER);
        setUpNtnOnlySubscription();
    }

    private static void cleanUpNtnOnlyTestEnvironment() throws Exception {
        logd(TAG, "cleanUpNtnOnlyTestEnvironment");
        restoreDeviceProvisionedState();
        restoreNtnOnlySubscriptions();
        cleanUpMockSim(NTN_ONLY_SLOT_ID, NTN_ONLY_SIM_PROFILE_ID);
        sNtnOnlySubId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    }

    private static void waitForSelectedSatelliteSubscriptionChanged(
            SelectedNbIotSatelliteSubscriptionCallbackTest
                    selectedNbIotSatelliteSubscriptionCallbackTest,
            int expectedSatelliteSubId) throws Exception {
        int i = 0;
        while (i < 3) {
            selectedNbIotSatelliteSubscriptionCallbackTest.waitUntilResult(1);
            if (selectedNbIotSatelliteSubscriptionCallbackTest.mSelectedSubId
                    == expectedSatelliteSubId) {
                break;
            }
            i++;
        }
        if (i == 3) {
            fail("waitForSelectedSatelliteSubscriptionChanged: Timeout to receive "
                    + "onSelectedNbIotSatelliteSubscriptionChanged for subId="
                    + expectedSatelliteSubId);
        }
        selectedNbIotSatelliteSubscriptionCallbackTest.drainPermits();
    }
}
