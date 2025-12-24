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

import static android.telephony.mockmodem.MockSimService.MOCK_SIM_PROFILE_ID_TWN_FET;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.telephony.SubscriptionManager;
import android.telephony.satellite.SatelliteManager;
import android.telephony.satellite.SatelliteStateChangeListener;
import android.telephony.satellite.stub.SatelliteResult;

import com.android.internal.telephony.satellite.DatagramController;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TestName;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;

public class SatelliteManagerOnMockServiceTestBase extends CarrierRoamingSatelliteTestBase {
    protected static final long TEST_SATELLITE_LISTENING_TIMEOUT_MILLIS = 100;
    protected static final long TEST_SATELLITE_LISTENING_FOREVER_TIMEOUT_MILLIS = 60 * 10 * 1000;
    protected static final long WAIT_FOREVER_TIMEOUT_MILLIS = Duration.ofMinutes(10).toMillis();

    protected static final int NTN_ONLY_SLOT_ID = SLOT_ID_0;
    private static final int NTN_ONLY_SIM_PROFILE_ID = MOCK_SIM_PROFILE_ID_TWN_FET;
    private static final String NTN_ONLY_PHONE_NUMBER = PHONE_NUMBER_0;

    /* SatelliteCapabilities constant indicating that pointing to satellite is required. */
    protected static final boolean POINTING_TO_SATELLITE_REQUIRED = true;
    /* SatelliteCapabilities constant indicating the maximum number of characters per datagram. */
    protected static final int MAX_BYTES_PER_DATAGRAM = 339;

    protected static final String PACKAGE_CONFIGUPDATER = "com.google.android.configupdater";

    BTWifiNFCStateReceiver mBTWifiNFCSateReceiver = null;
    UwbAdapterStateCallback mUwbAdapterStateCallback = null;
    protected String mTestSatelliteModeRadios = null;
    boolean mBTInitState = false;
    boolean mWifiInitState = false;
    boolean mNfcInitState = false;
    boolean mUwbInitState = false;

    // Latch to prevent race condition between mIsEnabled state change and verification
    protected CountDownLatch mIsEnabledStateChangedLatch;
    protected boolean mIsEnabled;

    public class TestSatelliteStateChangeListener implements SatelliteStateChangeListener {
        @Override
        public void onEnabledStateChanged(boolean isEnabled) {
            final boolean isEnabledStateChanged = isEnabled != mIsEnabled;
            mIsEnabled = isEnabled;
            if (mIsEnabledStateChangedLatch != null
                    && mIsEnabledStateChangedLatch.getCount() > 0
                    && isEnabledStateChanged) {
                mIsEnabledStateChangedLatch.countDown();
            }
        }
    }

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Rule public final TestName testName = new TestName();

    protected static void beforeAllSatelliteManagerTestsOnMockService(
            int[] mSupportedRadioTechnologies) throws Exception {
        logd("beforeAllTests");

        sActiveSubscriptionRequired = false;
        if (!shouldTestSatelliteWithMockService()) return;

        try {
            beforeAllCarrierRoamingTestsBase();
        } catch (Exception e) {
            sInitError = new AssertionError("beforeAllCarrierRoamingTestsBase failed", e);
            return;
        }

        grantSatellitePermission();
        try {
            setupMockSatelliteService();
        } catch (AssertionError e) {
            sInitError = new AssertionError("setupMockSatelliteService failed", e);
            return;
        }
        sMockSatelliteServiceManager.setSupportedRadioTechnologies(mSupportedRadioTechnologies);

        setUpNtnOnlyTestEnvironment(
                NTN_ONLY_SLOT_ID, NTN_ONLY_SIM_PROFILE_ID, NTN_ONLY_PHONE_NUMBER);
        sNtnOnlySubId = SubscriptionManager.getSubscriptionId(NTN_ONLY_SLOT_ID);
        assumeTrue(
                "NTN only SubId is INVALID_SUBSCRIPTION_ID",
                sNtnOnlySubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID);

        // Enable CTS mode to ignore the requests from SG-APK and real Pointing UI app.
        assertTrue(sMockSatelliteServiceManager.setCtsMode(true));
        sMockSatelliteServiceManager.setDatagramControllerBooleanConfig(
                false,
                DatagramController.BOOLEAN_TYPE_WAIT_FOR_DEVICE_ALIGNMENT_IN_DEMO_DATAGRAM,
                true);
        assertTrue(sMockSatelliteServiceManager.overrideSatellitePointingUiClassName());
        setUpSatelliteAccessAllowedAtDefaultTestLocation();
        revokeSatellitePermission();
    }

    @AfterClass
    public static void afterAllTests() throws Exception {
        logd("afterAllTests start");
        if (!shouldTestSatelliteWithMockService()) return;
        if (sInitError == null) {
            grantSatellitePermission();
            sActiveSubscriptionRequired = false;
            sMockSatelliteServiceManager.setDatagramControllerBooleanConfig(
                    true,
                    DatagramController.BOOLEAN_TYPE_WAIT_FOR_DEVICE_ALIGNMENT_IN_DEMO_DATAGRAM,
                    false);

            SatelliteModemStateCallbackTest callback = new SatelliteModemStateCallbackTest();
            long registerResult =
                    sSatelliteManager.registerForModemStateChanged(
                            getContext().getMainExecutor(), callback);
            assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerResult);
            assertTrue(callback.waitUntilResult(1));

            if (isSatelliteEnabled()) {
                logd("Disable satellite");
                // Disable satellite modem to clean up all pending resources and reset telephony
                // states.
                requestSatelliteEnabled(false);
                assertTrue(callback.waitUntilModemOff());
                assertFalse(isSatelliteEnabled());
            }

            assertTrue(sMockSatelliteServiceManager.restoreSatelliteServicePackageName());
            waitFor(2000);
            sSatelliteManager.unregisterForModemStateChanged(callback);
            resetSatelliteAccessControlOverlayConfigs();
            resetSatelliteAccessForSatelliteSubscriptions();
            restoreSupportedMsgAppsForSatelliteSubscriptions();
            restoreDeviceProvisionedState();
            restoreNtnOnlySubscriptions();
            assertTrue(
                    sMockSatelliteServiceManager
                            .setIsSatelliteCommunicationAllowedForCurrentLocationCache("enable"));
            // Disable CTS mode to accept the requests from SG-APK and real Pointing UI app.
            assertTrue(sMockSatelliteServiceManager.setCtsMode(false));
            assertTrue(sMockSatelliteServiceManager.restoreSatellitePointingUiClassName());
            cleanUpNtnOnlyTestEnvironment(NTN_ONLY_SLOT_ID, NTN_ONLY_SIM_PROFILE_ID);
            revokeSatellitePermission();
        }
        afterAllCarrierRoamingTestsBase();
        sMockSatelliteServiceManager = null;
        logd("afterAllTests end");
    }

    @Before
    public void setUp() throws Exception {
        logd("setUp start: " + testName.getMethodName());
        if (sInitError != null) throw sInitError;
        assumeTrue(
                "Device does not support to test satellite with mock service",
                shouldTestSatelliteWithMockService());
        assumeTrue(
                "MockSatelliteServiceManager is null but expected not null",
                sMockSatelliteServiceManager != null);

        sMockSatelliteServiceManager.executeTelephonyDebugServiceDumpsys(
                "--clearatoms", "--saveFileImmediately");
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

        // Initialize radio state
        mBTInitState = false;
        mWifiInitState = false;
        mNfcInitState = false;
        mUwbInitState = false;
        mTestSatelliteModeRadios = "";

        SatelliteModeRadiosUpdater satelliteRadiosModeUpdater =
                new SatelliteModeRadiosUpdater(getContext());
        assertTrue(satelliteRadiosModeUpdater.setSatelliteModeRadios(""));
        setUpNtnOnlySubscription();

        grantSatellitePermission();
        if (!isSatelliteEnabled()) {
            logd("Enable satellite");

            SatelliteModemStateCallbackTest callback = new SatelliteModemStateCallbackTest();
            long registerResult =
                    sSatelliteManager.registerForModemStateChanged(
                            getContext().getMainExecutor(), callback);
            assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerResult);
            assertTrue(callback.waitUntilResult(1));

            int i = 0;
            while (requestSatelliteEnabledWithResult(true, EXTERNAL_DEPENDENT_TIMEOUT)
                            != SatelliteManager.SATELLITE_RESULT_SUCCESS
                    && i < 3) {
                waitFor(500);
                i++;
                logd("requestSatelliteEnabledWithResult failed, retrying, iteration=" + i);
            }

            assertTrue(callback.waitUntilModemIdleOrNotConnected());
            assertTrue(isSatelliteEnabled());
            sSatelliteManager.unregisterForModemStateChanged(callback);
            // Set initial mIsEnabled to match the actual satellite state
            mIsEnabled = true;
            mIsEnabledStateChangedLatch = new CountDownLatch(1);
        }
        logd("Satellite enabled");

        clearAllEventsInMockServiceManagers();

        revokeSatellitePermission();
        logd("setUp end: " + testName.getMethodName());
    }

    @After
    public void tearDown() throws Exception {
        logd("tearDown start");
        if (!shouldTestSatelliteWithMockService()) return;
        assumeTrue(
                "MockSatelliteServiceManager is null but expected not null",
                sMockSatelliteServiceManager != null);
        sMockSatelliteServiceManager.setErrorCode(SatelliteResult.SATELLITE_RESULT_SUCCESS);
        sMockSatelliteServiceManager.setWaitToSend(false);
        sMockSatelliteServiceManager.setShouldRespondTelephony(true);
        sMockSatelliteServiceManager.setShouldRespondEnableRequest(true);
        sMockSatelliteServiceManager.clearSatelliteEnableRequestQueues();

        assertTrue(sMockSatelliteServiceManager.setSatelliteIgnoreCellularServiceState(false));
        assertTrue(sMockSatelliteServiceManager.setSatelliteTnScanningSupport(true, false, false));
        assertTrue(
                sMockSatelliteServiceManager.setSupportDisableSatelliteWhileEnableInProgress(
                        true, false));

        // Move satellite to off state to clean up all pending resources
        // and reset telephony states.
        moveSatelliteToOffState();

        grantSatellitePermission();
        sMockSatelliteServiceManager.clearSentSatelliteDatagramInfo();
        sMockSatelliteServiceManager.clearMockPointingUiActivityStatusChanges();
        sMockSatelliteServiceManager.clearListeningEnabledList();
        unregisterTestLocationProvider();
        sMockSatelliteServiceManager.executeTelephonyDebugServiceDumpsys("--clearatoms", null);
        revokeSatellitePermission();
        sMockSatelliteServiceManager.mIsPointingUiOverridden = false;

        logd("tearDown end");
    }
}
