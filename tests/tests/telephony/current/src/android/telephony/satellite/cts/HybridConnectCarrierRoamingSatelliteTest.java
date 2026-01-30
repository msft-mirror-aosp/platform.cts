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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.app.Notification;
import android.content.Context;
import android.os.Bundle;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.service.notification.StatusBarNotification;
import android.telephony.satellite.SatelliteManager;
import android.telephony.satellite.stub.SatelliteModemState;
import android.telephony.satellite.stub.SatelliteResult;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.annotations.AfterClass;
import com.android.bedstead.harrier.annotations.BeforeClass;
import com.android.bedstead.harrier.annotations.NotificationsTest;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.notifications.NotificationListener;
import com.android.bedstead.nene.notifications.NotificationListenerQuery;
import com.android.bedstead.nene.utils.Poll;
import com.android.internal.R;
import com.android.internal.telephony.flags.Flags;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

@RunWith(BedsteadJUnit4.class)
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
        if (!shouldTestHybridConnectCarrierRoamingSatellite()) return;

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
        if (!shouldTestHybridConnectCarrierRoamingSatellite()) return;
        afterAllCarrierRoamingTestsBase();
    }

    @Before
    public void setUp() throws Exception {
        logd(TAG, "setUp()");
        if (!shouldTestHybridConnectCarrierRoamingSatellite()) return;
    }

    @After
    public void tearDown() throws Exception {
        logd(TAG, "tearDown()");
    }

    protected static boolean shouldTestHybridConnectCarrierRoamingSatellite() {
        return (shouldTestSatelliteWithMockService() && Flags.vzwAstSkyloFallback());
    }

    /**
     * Set up before auto connect test cases
     *
     * @throws Exception exception
     */
    public void setUp_AutoConnect(boolean shouldMoveToInService) throws Exception {
        setUpHybridConnectAutoTestEnvironment(
                SLOT_ID_0, MOCK_SIM_PROFILE_ID_TWN_FET, PHONE_NUMBER_0, shouldMoveToInService);
    }

    /**
     * Set up before manual connect test cases
     *
     * @throws Exception exception
     */
    public void setUp_ManualConnect(boolean shouldMoveToInService) throws Exception {
        setUpHybridConnectManualTestEnvironment(
                ESOS_SLOT_ID,
                MOCK_SIM_PROFILE_ID_TWN_FET,
                PHONE_NUMBER_0,
                true,
                true,
                shouldMoveToInService);
        assumeTrue(shouldTestManualConnectCarrierRoaming());
        assumeTrue(sMockSatelliteServiceManager != null);

        addCtsPackageToSupportedSmsApps(sEsosSubId);

        assertTrue(
                "Failed to set satellite ignore cellular service state",
                sMockSatelliteServiceManager.setSatelliteIgnoreCellularServiceState(true));
        assertTrue(
                "Failed to set satellite TN scanning support",
                sMockSatelliteServiceManager.setSatelliteTnScanningSupport(false, false, true));
        assertTrue("Failed to set support disable satellite while enable in progress",
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

        assertTrue(
                "Failed to reset satellite ignore cellular service state",
                sMockSatelliteServiceManager.setSatelliteIgnoreCellularServiceState(false));
        assertTrue(
                "Failed to reset satellite TN scanning support",
                sMockSatelliteServiceManager.setSatelliteTnScanningSupport(true, false, false));
        assertTrue("Failed to reset support disable satellite while enable in progress",
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
        if (!shouldTestHybridConnectCarrierRoamingSatellite()) return;
        setUp_AutoConnect(true);

        CarrierRoamingNtnListenerTest listener = new CarrierRoamingNtnListenerTest();
        listener.clearModeChanges();

        grantSatellitePermission();
        SatelliteModemStateCallbackTest callback = new SatelliteModemStateCallbackTest();
        long registerResult =
                sSatelliteManager.registerForModemStateChanged(
                        getContext().getMainExecutor(), callback);
        assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerResult);
        assertTrue("Timed out waiting for modem state change result", callback.waitUntilResult(1));
        callback.clearModemStates();

        adoptShellIdentity();
        sTelephonyManager.registerTelephonyCallback(getContext().getMainExecutor(), listener);
        try {
            // Get NTN mode immediately after registering
            assertTrue("Timed out waiting for NTN mode change", listener.waitForModeChanged(1));
            assertTrue("Failed to verify NTN mode is enabled", listener.getNtnMode());
            assertEquals(SatelliteModemState.SATELLITE_MODEM_STATE_OFF, callback.modemState);
            listener.clearModeChanges();

            // Satellite network is lost, no callback as hysteresis timeout is not expired
            sMockModemManager.changeNetworkService(SLOT_ID_0, MOCK_SIM_PROFILE_ID_TWN_FET, false);
            assertFalse(listener.waitForModeChanged(1));
            assertEquals(SatelliteModemState.SATELLITE_MODEM_STATE_OFF, callback.modemState);
            listener.clearModeChanges();

            // Callback is received after hysteresis timeout
            assertTrue(
                    "Timed out waiting for NTN mode change after hysteresis",
                    listener.waitForModeChanged(1));
            assertFalse(listener.getNtnMode());
            assertEquals(SatelliteModemState.SATELLITE_MODEM_STATE_OFF, callback.modemState);

            // Move back to satellite in service mode
            sMockModemManager.changeNetworkService(SLOT_ID_0, MOCK_SIM_PROFILE_ID_TWN_FET, true);
            assertTrue("Timed out waiting for NTN mode change", listener.waitForModeChanged(1));
            assertTrue("Failed to verify NTN mode is enabled", listener.getNtnMode());
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
        if (!shouldTestHybridConnectCarrierRoamingSatellite()) return;
        setUp_ManualConnect(true);

        CarrierRoamingNtnListenerTest listener = new CarrierRoamingNtnListenerTest();
        listener.clearModeChanges();

        grantSatellitePermission();
        SatelliteModemStateCallbackTest callback = new SatelliteModemStateCallbackTest();
        long registerResult =
                sSatelliteManager.registerForModemStateChanged(
                        getContext().getMainExecutor(), callback);
        assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerResult);
        assertTrue("Timed out waiting for modem state change result", callback.waitUntilResult(1));
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
            assertTrue(
                    "Timed out waiting for modem state change result", callback.waitUntilResult(1));
            assertTrue("Timed out waiting for NTN mode change", listener.waitForModeChanged(1));
            assertTrue("Failed to verify NTN mode is enabled", listener.getNtnMode());
            listener.clearModeChanges();
            assertEquals(SatelliteModemState.SATELLITE_MODEM_STATE_IN_SERVICE, callback.modemState);

            // Satellite network is lost, no callback as hysteresis timeout is not expired
            sMockModemManager.changeNetworkService(SLOT_ID_0, MOCK_SIM_PROFILE_ID_TWN_FET, false);
            assertFalse(listener.waitForModeChanged(1));
            assertEquals(SatelliteModemState.SATELLITE_MODEM_STATE_IN_SERVICE, callback.modemState);
            sMockSatelliteServiceManager.sendOnSatelliteModemStateChanged(
                    SatelliteModemState.SATELLITE_MODEM_STATE_OUT_OF_SERVICE);

            // Callback is received after hysteresis timeout
            assertTrue(
                    "Timed out waiting for modem state change result", callback.waitUntilResult(1));
            //            assertTrue(listener.waitForModeChanged(1));
            assertFalse(listener.getNtnMode());
            assertEquals(
                    SatelliteModemState.SATELLITE_MODEM_STATE_OUT_OF_SERVICE, callback.modemState);

            // Clean up
            listener.clearModeChanges();
            moveSatelliteToOffState();
            assertTrue("Timed out waiting for modem to power off", callback.waitUntilModemOff());
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

    @Test
    @NotificationsTest
    public void testNotificationContent_AutoConnect() throws Exception {
        logd(TAG, "testNotificationContent_AutoConnect");
        if (!shouldTestHybridConnectCarrierRoamingSatellite()) return;

        CarrierRoamingNtnListenerTest ntnStateListener = new CarrierRoamingNtnListenerTest();
        ntnStateListener.clearModeChanges();

        try (NotificationListener listener = TestApis.notifications().createListener()) {
            try {
                adoptShellIdentity();
                sTelephonyManager.registerTelephonyCallback(
                        getContext().getMainExecutor(), ntnStateListener);

                setUp_AutoConnect(true);
                assertTrue(
                        "Timed out waiting for NTN mode change",
                        ntnStateListener.waitForModeChanged(1));
                assertTrue("Failed to verify NTN mode is enabled", ntnStateListener.getNtnMode());

                Context context = getContext();
                String expectedTitle = context.getString(R.string.satellite_notification_title);
                String expectedSummary = context.getString(R.string.satellite_notification_summary);

                NotificationListenerQuery satelliteNotificationQuery =
                        listener.query()
                                .wherePackageName()
                                .isEqualTo("com.android.phone")
                                .whereNotification()
                                .channelId()
                                .isEqualTo("satellite")
                                .whereNotification()
                                .tag()
                                .isEqualTo("SatelliteController");

                com.android.bedstead.nene.notifications.Notification satelliteNotification =
                        satelliteNotificationQuery.poll();

                assertNotNull(satelliteNotification);

                StatusBarNotification statusBarNotification =
                        satelliteNotification.getStatusBarNotification();

                assertNotNull(statusBarNotification);

                Notification notification = statusBarNotification.getNotification();
                assertNotNull(notification);

                Bundle notificationContents = notification.extras;
                assertNotNull(notificationContents);

                String actualTitle = notificationContents.getString(Notification.EXTRA_TITLE);
                String actualText = notificationContents.getString(Notification.EXTRA_TEXT);

                assertEquals(expectedTitle, actualTitle);
                assertEquals(expectedSummary, actualText);
            } finally {
                sTelephonyManager.unregisterTelephonyCallback(ntnStateListener);
                dropShellIdentity();
                tearDown_AutoConnect();
            }
        }
    }

    @Test
    @NotificationsTest
    public void testNotificationDismissed_AutoConnect() throws Exception {
        logd(TAG, "testNotificationDismissed_AutoConnect");
        if (!shouldTestHybridConnectCarrierRoamingSatellite()) return;

        CarrierRoamingNtnListenerTest ntnStateListener = new CarrierRoamingNtnListenerTest();
        ntnStateListener.clearModeChanges();

        try {
            adoptShellIdentity();
            sTelephonyManager.registerTelephonyCallback(
                    getContext().getMainExecutor(), ntnStateListener);

            setUp_AutoConnect(true);
            assertTrue(
                    "Timed out waiting for NTN mode change",
                    ntnStateListener.waitForModeChanged(1));
            assertTrue("Failed to verify NTN mode is enabled", ntnStateListener.getNtnMode());
            ntnStateListener.clearModeChanges();
            // triggers the satellite notification

            // satellite notification should be posted
            try (NotificationListener listener = TestApis.notifications().createListener()) {
                NotificationListenerQuery satelliteNotificationQuery =
                        listener.query()
                                .wherePackageName()
                                .isEqualTo("com.android.phone")
                                .whereNotification()
                                .channelId()
                                .isEqualTo("satellite")
                                .whereNotification()
                                .tag()
                                .isEqualTo("SatelliteController");
                Poll.forValue(
                                "testNotificationDismissed_AutoConnect: Notification posting",
                                satelliteNotificationQuery::poll)
                        .toNotBeNull()
                        .errorOnFail("Notification not posted")
                        .await();
            }

            // Satellite network is lost, no callback as hysteresis timeout is not expired
            sMockModemManager.changeNetworkService(SLOT_ID_0, MOCK_SIM_PROFILE_ID_TWN_FET, false);
            assertFalse(ntnStateListener.waitForModeChanged(1));
            ntnStateListener.clearModeChanges();

            // Callback is received after hysteresis timeout
            assertTrue(
                    "Timed out waiting for NTN mode change after hysteresis",
                    ntnStateListener.waitForModeChanged(1));
            assertFalse(ntnStateListener.getNtnMode());

            try (NotificationListener dismissListener = TestApis.notifications().createListener()) {
                NotificationListenerQuery satelliteNotificationQuery =
                        dismissListener
                                .query()
                                .wherePackageName()
                                .isEqualTo("com.android.phone")
                                .whereNotification()
                                .channelId()
                                .isEqualTo("satellite")
                                .whereNotification()
                                .tag()
                                .isEqualTo("SatelliteController");
                Poll.forValue(
                                "testNotificationDismissed_AutoConnect: Notification dismissal",
                                satelliteNotificationQuery::poll)
                        .toBeNull()
                        .errorOnFail("Notification not dismissed")
                        .await();
            }
        } finally {
            sTelephonyManager.unregisterTelephonyCallback(ntnStateListener);
            dropShellIdentity();
            tearDown_AutoConnect();
        }
    }
}
