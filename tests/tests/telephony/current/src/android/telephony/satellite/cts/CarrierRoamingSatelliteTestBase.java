/*
 * Copyright (C) 2024 The Android Open Source Project
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

import static android.telephony.satellite.SatelliteManager.EMERGENCY_CALL_TO_SATELLITE_HANDOVER_TYPE_SOS;

import static com.android.internal.telephony.satellite.SatelliteController.TIMEOUT_TYPE_EVALUATE_ESOS_PROFILES_PRIORITIZATION_DURATION_MILLIS;

import static com.google.common.truth.Truth.assertThat;

import static junit.framework.Assert.assertTrue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assume.assumeTrue;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.WifiManager;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.ServiceState;
import android.telephony.SmsManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;
import android.telephony.mockmodem.MockModemConfigBase;
import android.telephony.mockmodem.MockModemManager;
import android.telephony.satellite.SatelliteManager;
import android.telephony.satellite.stub.NTRadioTechnology;
import android.util.Log;
import android.util.Pair;

import androidx.test.InstrumentationRegistry;

import com.android.compatibility.common.util.CarrierPrivilegeUtils;
import com.android.compatibility.common.util.ShellIdentityUtils;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.telephony.satellite.DatagramController;

import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class CarrierRoamingSatelliteTestBase extends SatelliteManagerTestBase {
    private static final String TAG = "CarrierRoamingSatelliteTestBase";

    protected static final int WAIT_UPDATE_TIMEOUT_MS = 3000;
    // This value should be greater than the value of TIMEOUT (5 seconds) defined in
    // SatelliteManagerTestBase.
    protected static final int HYSTERESIS_TIMEOUT_SEC = 8;
    protected static final int SLOT_ID_0 = 0;
    protected static final int SLOT_ID_1 = 1;
    protected static final String PHONE_NUMBER_0 = "1234567890";
    protected static final String PHONE_NUMBER_1 = "1230123456";
    protected static final String NIDD_APN_NAME = "test_nidd.apn";

    protected static MockModemManager sMockModemManager;
    protected static WifiStateReceiver sWifiStateReceiver = null;

    private static final Context sContext = InstrumentationRegistry.getContext();
    private static final ConnectivityManager sConnectivityManager =
            sContext.getSystemService(ConnectivityManager.class);

    @Nullable
    private static NetworkCapabilities sCurrentNetworkCapabilities;
    public static final int DEFAULT_CALLBACK_TIMEOUT_MS = 15_000;
    private static final int NETWORK_REQUEST_TIMEOUT_MS = 5_000;
    private static final int CALLBACK_TIMEOUT_MS = 8_000;

    public static class NetworkCallback extends ConnectivityManager.NetworkCallback {
        private int mCallbackTimeoutInMs;
        private CountDownLatch mOnAvailableBlocker = new CountDownLatch(1);
        private CountDownLatch mOnUnAvailableBlocker = new CountDownLatch(1);
        private CountDownLatch mOnLostBlocker = new CountDownLatch(1);
        // This is invoked multiple times, so initialize only when waitForCapabilitiesChanged() is
        // invoked.
        @Nullable
        private CountDownLatch mOnCapabilitiesChangedBlocker = null;
        @Nullable
        private Network mNetwork;

        public NetworkCallback() {
            mCallbackTimeoutInMs = DEFAULT_CALLBACK_TIMEOUT_MS;
        }

        public void setNetworkCallbackTimeOut(int callbackTimeoutInMs) {
            mCallbackTimeoutInMs = callbackTimeoutInMs;
        }

        @Override
        public void onAvailable(Network network) {
            logd(TAG, "onAvailable: " + network);
            mNetwork = network;
            mOnAvailableBlocker.countDown();
        }

        @Override
        public void onCapabilitiesChanged(Network network, NetworkCapabilities networkCapabilities) {
            logd(TAG, "onCapabilitiesChanged: " + network);
            mNetwork = network;
            sCurrentNetworkCapabilities = networkCapabilities;
            if (mOnCapabilitiesChangedBlocker != null) mOnCapabilitiesChangedBlocker.countDown();
        }

        @Override
        public void onUnavailable() {
            logd(TAG, "onUnavailable");
            mOnUnAvailableBlocker.countDown();
        }

        @Override
        public void onLost(Network network) {
            logd(TAG, "onLost: " + network);
            mNetwork = network;
            mOnLostBlocker.countDown();
        }

        public Network getNetwork() {
            return mNetwork;
        }

        public NetworkCapabilities getNetworkCapabilities() {
            return sCurrentNetworkCapabilities;
        }

        /**
         * Wait (blocks) for {@link #onAvailable(Network)} or timeout.
         *
         * @return A pair of values: whether the callback was invoked and the Network object
         * created when successful - null otherwise.
         */
        public Pair<Boolean, Network> waitForAvailable() throws InterruptedException {
            if (mOnAvailableBlocker.await(mCallbackTimeoutInMs, TimeUnit.MILLISECONDS)) {
                return Pair.create(true, mNetwork);
            }
            return Pair.create(false, null);
        }

        /**
         * Wait (blocks) for {@link #onUnavailable()} or timeout.
         *
         * @return true whether the callback was invoked.
         */
        public boolean waitForUnavailable() throws InterruptedException {
            return mOnUnAvailableBlocker.await(mCallbackTimeoutInMs, TimeUnit.MILLISECONDS);
        }

        /**
         * Wait (blocks) for {@link #onLost(Network)} or timeout.
         *
         * @return true whether the callback was invoked.
         */
        public boolean waitForLost() throws InterruptedException {
            return mOnLostBlocker.await(mCallbackTimeoutInMs, TimeUnit.MILLISECONDS);
        }

        /**
         * Wait (blocks) for {@link #onCapabilitiesChanged(Network, NetworkCapabilities)} or
         * timeout.
         *
         * @return true whether the callback was invoked.
         */
        public boolean waitForCapabilitiesChanged() throws InterruptedException {
            mOnCapabilitiesChangedBlocker = new CountDownLatch(1);
            return mOnCapabilitiesChangedBlocker.await(mCallbackTimeoutInMs, TimeUnit.MILLISECONDS);
        }
    }

    protected static void beforeAllCarrierRoamingTestsBase() throws Exception {
        beforeAllTestsBase();
        logd(TAG, "beforeAllCarrierRoamingTestsBase");

        MockModemManager.enforceMockModemDeveloperSetting();
        sMockModemManager = new MockModemManager();
        assertNotNull(sMockModemManager);
        assertTrue(sMockModemManager.connectMockModemService());

        sWifiStateReceiver = new WifiStateReceiver();
        IntentFilter wifiStateIntentFilter = new IntentFilter();
        wifiStateIntentFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        getContext().registerReceiver(sWifiStateReceiver, wifiStateIntentFilter);
    }

    protected static void afterAllCarrierRoamingTestsBase() throws Exception {
        logd(TAG, "afterAllCarrierRoamingTestsBase");

        if (sMockModemManager != null) {
            assertTrue(sMockModemManager.disconnectMockModemService());
            sMockModemManager = null;
            TimeUnit.MILLISECONDS.sleep(WAIT_UPDATE_TIMEOUT_MS);
        }

        if (sWifiStateReceiver != null) {
            getContext().unregisterReceiver(sWifiStateReceiver);
            sWifiStateReceiver = null;
        }

        afterAllTestsBase();
    }

    protected static class ServiceStateListenerTest extends TelephonyCallback
            implements TelephonyCallback.ServiceStateListener {

        private final Semaphore mSemaphore = new Semaphore(0);
        private final Semaphore mNtnConnectedSemaphore = new Semaphore(0);
        private final Semaphore mNtnDisconnetedSemaphore = new Semaphore(0);
        private final Semaphore mInServiceSemaphore = new Semaphore(0);
        private final Semaphore mOutOfServiceSemaphore = new Semaphore(0);
        @Nullable
        private ServiceState mServiceState = null;

        @Override
        public void onServiceStateChanged(ServiceState serviceState) {
            logd(TAG, "onServiceStateChanged: serviceState=" + serviceState);
            mServiceState = serviceState;

            try {
                mSemaphore.release();

                if (serviceState.isUsingNonTerrestrialNetwork()) {
                    mNtnConnectedSemaphore.release();
                } else {
                    mNtnDisconnetedSemaphore.release();
                }

                int currentState = serviceState.getState();
                if (currentState == ServiceState.STATE_IN_SERVICE) {
                    mInServiceSemaphore.release();
                } else if (currentState == ServiceState.STATE_OUT_OF_SERVICE) {
                    mOutOfServiceSemaphore.release();
                }
            } catch (Exception e) {
                loge(TAG, "onServiceStateChanged: Got exception=" + e);
            }
        }

        public boolean waitUntilServiceStateChanged() {
            try {
                if (!mSemaphore.tryAcquire(TIMEOUT, TimeUnit.MILLISECONDS)) {
                    loge(TAG, "Timeout to wait for service state changed");
                    return false;
                }
            } catch (Exception e) {
                loge(TAG, "ServiceStateListenerTest waitUntilServiceStateChanged: "
                        + "Got exception=" + e);
                return false;
            }
            return true;
        }

        public boolean waitUntilNonTerrestrialNetworkConnected() {
            try {
                if (!mNtnConnectedSemaphore.tryAcquire(TIMEOUT, TimeUnit.MILLISECONDS)) {
                    loge(TAG, "Timeout to connect to non-terrestrial network");
                    return false;
                }
            } catch (Exception e) {
                loge(TAG, "ServiceStateListenerTest waitUntilNonTerrestrialNetworkConnected: "
                        + "Got exception=" + e);
                return false;
            }
            return true;
        }

        public boolean waitUntilNonTerrestrialNetworkDisconnected() {
            try {
                if (!mNtnDisconnetedSemaphore.tryAcquire(TIMEOUT, TimeUnit.MILLISECONDS)) {
                    loge(TAG, "Timeout to disconnect to non-terrestrial network");
                    return false;
                }
            } catch (Exception e) {
                loge(TAG, "ServiceStateListenerTest waitUntilNonTerrestrialNetworkDisconnected: "
                        + "Got exception=" + e);
                return false;
            }
            return true;
        }

        public boolean waitUntilInService() {
            try {
                if (!mInServiceSemaphore.tryAcquire(TIMEOUT, TimeUnit.MILLISECONDS)) {
                    loge(TAG, "Timeout to enter in service state");
                    return false;
                }
            } catch (Exception e) {
                loge(TAG, "ServiceStateListenerTest waitUntilInService: Got exception=" + e);
                return false;
            }
            return true;
        }

        public boolean waitUntilOutOfService() {
            try {
                if (!mOutOfServiceSemaphore.tryAcquire(TIMEOUT, TimeUnit.MILLISECONDS)) {
                    loge(TAG, "Timeout to enter out of service state");
                    return false;
                }
            } catch (Exception e) {
                loge(TAG, "ServiceStateListenerTest waitUntilOutOfService: Got exception=" + e);
                return false;
            }
            return true;
        }

        public void clearServiceStateChanges() {
            logd(TAG, "clearServiceStateChanges()");
            mNtnConnectedSemaphore.drainPermits();
            mNtnDisconnetedSemaphore.drainPermits();
            mInServiceSemaphore.drainPermits();
            mOutOfServiceSemaphore.drainPermits();
        }

        public boolean isInService() {
            return mServiceState != null && mServiceState.getState() == ServiceState.STATE_IN_SERVICE;
        }
    }

    protected static class SmsMmsBroadcastReceiver extends BroadcastReceiver {
        private final Semaphore mSemaphore = new Semaphore(0);
        private final Object mActionLock = new Object();
        @GuardedBy("mActionLock")
        private String mAction;

        public void setAction(String action) {
            synchronized (mActionLock) {
                mAction = action;
                mSemaphore.drainPermits();
            }
        }

        public String getAction() {
            synchronized (mActionLock) {
                return mAction;
            }
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            synchronized (mActionLock) {
                logd(TAG, "onReceive: " + intent.getAction());
                if (intent.getAction().equals(mAction)) {
                    mSemaphore.release();
                }
            }
        }

        public boolean waitForBroadcast(int expectedNumberOfEvents) {
            logd(TAG, "waitForBroadcast()");
            for (int i = 0; i < expectedNumberOfEvents; i++) {
                try {
                    if (!mSemaphore.tryAcquire(TIMEOUT, TimeUnit.MILLISECONDS)) {
                        loge(TAG, "Timeout to receive sms/mms broadcast");
                        return false;
                    }
                } catch (Exception ex) {
                    loge(TAG, "waitForBroadcast: Got exception=" + ex);
                    return false;
                }
            }
            return true;
        }
    }

    protected static class CarrierRoamingNtnListenerTest extends TelephonyCallback
            implements TelephonyCallback.CarrierRoamingNtnListener {
        private final Semaphore mActiveSemaphore = new Semaphore(0);
        private final Semaphore mEligibleSemaphore = new Semaphore(0);
        private final Object mLock = new Object();

        @GuardedBy("mLock")
        public boolean mActive;
        @GuardedBy("mLock")
        public boolean mEligible;

        @Override
        public void onCarrierRoamingNtnModeChanged(boolean active) {
            logd(TAG, "onCarrierRoamingNtnModeChanged active:" + active);
            synchronized (mLock) {
                mActive = active;
            }

            try {
                mActiveSemaphore.release();
            } catch (Exception e) {
                loge(TAG, "onCarrierRoamingNtnModeChanged: Got exception, ex=" + e);
            }
        }

        @Override
        public void onCarrierRoamingNtnEligibleStateChanged(boolean eligible) {
            logd(TAG, "onCarrierRoamingNtnEligibleStateChanged eligible:" + eligible);
            synchronized (mLock) {
                mEligible = eligible;
            }

            try {
                mEligibleSemaphore.release();
            } catch (Exception e) {
                loge(TAG, "onCarrierRoamingNtnEligible: Got exception, ex=" + e);
            }
        }

        public boolean waitForModeChanged(int expectedNumOfEvents) {
            for (int i = 0; i < expectedNumOfEvents; i++) {
                try {
                    if (!mActiveSemaphore.tryAcquire(TIMEOUT, TimeUnit.MILLISECONDS)) {
                        loge(TAG, "Timeout to receive onCarrierRoamingNtnModeChanged");
                        return false;
                    }
                } catch (Exception ex) {
                    loge(TAG, "onCarrierRoamingNtnModeChanged: Got exception=" + ex);
                    return false;
                }
            }
            return true;
        }

        public boolean waitForNtnEligible(int expectedNumOfEvents) {
            for (int i = 0; i < expectedNumOfEvents; i++) {
                try {
                    if (!mEligibleSemaphore.tryAcquire(TIMEOUT, TimeUnit.MILLISECONDS)) {
                        loge(TAG, "Timeout to receive onCarrierRoamingNtnEligible");
                        return false;
                    }
                } catch (Exception ex) {
                    loge(TAG, "onCarrierRoamingEligible: Got exception=" + ex);
                    return false;
                }
            }
            return true;
        }

        public boolean getNtnMode() {
            synchronized (mLock) {
                return mActive;
            }
        }

        public boolean getNtnEligible() {
            synchronized (mLock) {
                return mEligible;
            }
        }

        public void clearModeChanges() {
            synchronized (mLock) {
                mActive = false;
                mEligible = false;
            }
            mActiveSemaphore.drainPermits();
            mEligibleSemaphore.drainPermits();
        }
    }

    protected static void setUpAutoConnectTestEnvironment(int slotId, int profile,
            String phoneNumber, boolean shouldMoveToInServiceState) throws Exception {
        logd(TAG, "setUpAutoConnectTestEnvironment() slotId:" + slotId
            + " profile:" + profile);

        assertTrue(sMockModemManager.insertSimCard(slotId, profile));
        TimeUnit.MILLISECONDS.sleep(TIMEOUT);

        int subId = SubscriptionManager.getSubscriptionId(slotId);
        // Set phone number
        setPhoneNumber(subId, phoneNumber);

        enableCarrierRoamingSatelliteConfigs(
            slotId, CarrierConfigManager.CARRIER_ROAMING_NTN_CONNECT_AUTOMATIC);

        if (shouldMoveToInServiceState) {
            // Register service state listener
            ServiceStateListenerTest serviceStateListener = registerServiceStateListener(subId);

            // Enter service
            sMockModemManager.changeNetworkService(slotId, profile, true);
            assertTrue(serviceStateListener.waitUntilNonTerrestrialNetworkConnected());

            sTelephonyManager.unregisterTelephonyCallback(serviceStateListener);
        }
    }

    protected static void setUpMockSim(int slotId, int simProfileId, String phoneNumber)
        throws Exception {
        logd(TAG, "setUpMockSim: slotId=" + slotId
            + ", simProfileId=" + simProfileId + ", phoneNumber=" + phoneNumber);
        // Insert sim card
        assertTrue(sMockModemManager.insertSimCard(slotId, simProfileId));
        TimeUnit.MILLISECONDS.sleep(TIMEOUT);

        int subId = SubscriptionManager.getSubscriptionId(slotId);
        assertTrue(subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        setPhoneNumber(subId, phoneNumber);
        assertTrue(isSimReady(slotId));
    }

    protected static void cleanUpMockSim(int slotId, int profile,
            boolean shouldMoveToOutOfServiceState) throws Exception {
        logd(TAG, "cleanUpAndRemoveInsertedSim: slotId:" + slotId + " profile:" + profile);
        int subId = SubscriptionManager.getSubscriptionId(slotId);

        if (isActiveSubId(subId)) {
            overrideCarrierConfig(subId, null);
        } else {
            logd(TAG, "cleanUpAndRemoveInsertedSim: subId:" + subId + " is not active.");
        }

        if (shouldMoveToOutOfServiceState) {
            // Leave service
            sMockModemManager.changeNetworkService(slotId, profile, false);
        }

        // Remove the SIM
        sMockModemManager.removeSimCard(slotId);
    }

    protected static void enableCarrierRoamingSatelliteConfigs(int slotId,
            @CarrierConfigManager.CARRIER_ROAMING_NTN_CONNECT_TYPE int connectType)
            throws Exception {
        int subId = SubscriptionManager.getSubscriptionId(slotId);
        logd(TAG, "enableCarrierRoamingSatelliteConfigs slotId:" + slotId
            + " connectType:" + connectType + " subId:" + subId);
        assertTrue(isActiveSubId(subId));

        String satellitePlmn = sMockModemManager.getSimInfo(slotId,
                MockModemConfigBase.SimInfoChangedResult.SIM_INFO_TYPE_MCC_MNC);
        int[] supportedServices = {
          NetworkRegistrationInfo.SERVICE_TYPE_SMS,
          NetworkRegistrationInfo.SERVICE_TYPE_EMERGENCY,
          NetworkRegistrationInfo.SERVICE_TYPE_MMS
        };

        PersistableBundle bundle = new PersistableBundle();
        bundle.putBoolean(
                CarrierConfigManager.KEY_SATELLITE_ATTACH_SUPPORTED_BOOL, true);
        bundle.putInt(CarrierConfigManager.KEY_SATELLITE_CONNECTION_HYSTERESIS_SEC_INT,
                HYSTERESIS_TIMEOUT_SEC);
        bundle.putInt(CarrierConfigManager
                        .KEY_CARRIER_SUPPORTED_SATELLITE_NOTIFICATION_HYSTERESIS_SEC_INT,
                HYSTERESIS_TIMEOUT_SEC);
        bundle.putInt(CarrierConfigManager.KEY_CARRIER_ROAMING_NTN_CONNECT_TYPE_INT, connectType);
        bundle.putIntArray(
            CarrierConfigManager.KEY_CARRIER_ROAMING_SATELLITE_DEFAULT_SERVICES_INT_ARRAY,
            supportedServices);
        bundle.putString(CarrierConfigManager.KEY_SATELLITE_NIDD_APN_NAME_STRING, NIDD_APN_NAME);

        PersistableBundle plmnBundle = new PersistableBundle();
        plmnBundle.putIntArray(satellitePlmn, supportedServices);
        bundle.putPersistableBundle(
                CarrierConfigManager.KEY_CARRIER_SUPPORTED_SATELLITE_SERVICES_PER_PROVIDER_BUNDLE,
                plmnBundle);

        // Configs for automatic connect type
        if (connectType == CarrierConfigManager.CARRIER_ROAMING_NTN_CONNECT_AUTOMATIC) {
            bundle.putBoolean(
                CarrierConfigManager.KEY_EMERGENCY_MESSAGING_SUPPORTED_BOOL, true);
        }

        // Configs for manual connect type only
        if (connectType == CarrierConfigManager.CARRIER_ROAMING_NTN_CONNECT_MANUAL) {
            bundle.putBoolean(
                CarrierConfigManager.KEY_SATELLITE_ESOS_SUPPORTED_BOOL, true);
            bundle.putBoolean(
                CarrierConfigManager.KEY_SATELLITE_ROAMING_P2P_SMS_SUPPORTED_BOOL, true);
            bundle.putInt(CarrierConfigManager
                .KEY_CARRIER_ROAMING_NTN_EMERGENCY_CALL_TO_SATELLITE_HANDOVER_TYPE_INT,
                EMERGENCY_CALL_TO_SATELLITE_HANDOVER_TYPE_SOS);
        }
        sMockSatelliteServiceManager.setSatelliteIgnorePlmnListFromStorage(false);
        overrideCarrierConfig(subId, bundle);
    }

    protected static void enableSatelliteEntitlementSupport(int subId)
            throws Exception {
        logd(TAG, "enableSatelliteEntitlementSupport subId:" + subId);
        assertTrue(isActiveSubId(subId));

        PersistableBundle bundle = new PersistableBundle();
        bundle.putBoolean(
                CarrierConfigManager.KEY_SATELLITE_ENTITLEMENT_SUPPORTED_BOOL, true);
        bundle.putString(
                CarrierConfigManager.ImsServiceEntitlement.KEY_ENTITLEMENT_SERVER_URL_STRING,
                EntilementStatusResponseGenerator.MOCK_ENTITLEMENT_SERVER_URL);
        overrideCarrierConfig(subId, bundle);
    }

    protected static void disableSatellitePlmns(int slotId) {
        int subId = SubscriptionManager.getSubscriptionId(slotId);
        logd(TAG, "disableSatellitePlmns slotId:" + slotId + " subId:" + subId);

        PersistableBundle bundle = new PersistableBundle();
        PersistableBundle plmnBundle = new PersistableBundle();
        bundle.putPersistableBundle(
                CarrierConfigManager.KEY_CARRIER_SUPPORTED_SATELLITE_SERVICES_PER_PROVIDER_BUNDLE,
                plmnBundle);
        sMockSatelliteServiceManager.setSatelliteIgnorePlmnListFromStorage(true);
        overrideCarrierConfig(subId, bundle);
    }

    protected static void setPhoneNumber(
        int subId, String carrierPhoneNumber) throws Exception {
        logd(TAG, "setPhoneNumber: subId=" + subId
            + ", carrierPhoneNumber=" + carrierPhoneNumber);
        CarrierPrivilegeUtils.withCarrierPrivileges(
                InstrumentationRegistry.getContext(),
                subId,
                () -> {
                    sSubscriptionManager.setCarrierPhoneNumber(subId, carrierPhoneNumber);
                    assertEquals(carrierPhoneNumber,
                        sSubscriptionManager.getPhoneNumber(
                            subId, SubscriptionManager.PHONE_NUMBER_SOURCE_CARRIER));
                });
    }

    protected static SmsManager getSmsManager() {
        return SmsManager.getDefault();
    }

    /**
     * Adopts shell permission identity
     */
    protected static void adoptShellIdentity() {
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity();
    }

    /**
     * Drop shell permission identity
     */
    protected static void dropShellIdentity() {
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .dropShellPermissionIdentity();
    }

    protected static void logd(@NonNull String tag, @NonNull String log) {
        Log.d(tag, log);
    }

    protected static void loge(@NonNull String tag, @NonNull String log) {
        Log.e(tag, log);
    }

    protected static class WifiStateReceiver extends BroadcastReceiver {
        private final Semaphore mWifiSemaphore = new Semaphore(0);
        private final Object mWifiExpectedStateLock = new Object();
        private boolean mWifiExpectedState = false;

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action == null) {
                logd(TAG, "WifiStateReceiver NULL action for intent " + intent);
                return;
            }
            logd(TAG, "WifiStateReceiver onReceive: action = " + action);

            switch (action) {
                case WifiManager.WIFI_STATE_CHANGED_ACTION: {
                    int wifiState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE,
                            WifiManager.WIFI_STATE_UNKNOWN);
                    logd(TAG, "Wifi state updated to " + wifiState);

                    synchronized (mWifiExpectedStateLock) {
                        if (mWifiExpectedState == sWifiManager.isWifiEnabled()) {
                            try {
                                mWifiSemaphore.release();
                            } catch (Exception e) {
                                loge(TAG, "BTWifiNFCStateReceiver onReceive(): "
                                        + "Got exception, ex=" + e);
                            }
                        }
                    }
                    break;
                }
                default:
                    break;
            }
        }

        public void setWifiExpectedState(boolean expectedState) {
            synchronized (mWifiExpectedStateLock) {
                mWifiExpectedState = expectedState;
                mWifiSemaphore.drainPermits();
            }
        }

        public boolean waitUntilWifiStateChanged() {
            synchronized (mWifiExpectedStateLock) {
                if (mWifiExpectedState == sWifiManager.isWifiEnabled()) {
                    return true;
                }
            }

            try {
                if (!mWifiSemaphore.tryAcquire(EXTERNAL_DEPENDENT_TIMEOUT,
                        TimeUnit.MILLISECONDS)) {
                    loge(TAG, "WifiStateReceiver waitUntilWifiStateChanged: "
                            + "Timeout to receive onStateChanged() callback");
                    return false;
                }
            } catch (Exception ex) {
                loge(TAG, "WifiStateReceiver waitUntilWifiStateChanged: Got exception=" + ex);
                return false;
            }
            return true;
        }
    }

    protected static void setUpManualConnectTestEnvironment(int eSosSlotId, int eSosSimProfileId,
        String phoneNumber, boolean supportCtsSmsApp, boolean shouldSetUpMockSatelliteService,
        boolean shouldMoveSimToInService) throws Exception {
        logd(TAG, "setUpManualConnectTestEnvironment: eSosSlotId=" + eSosSlotId
            + ", eSosSimProfileId=" + eSosSimProfileId);
        // Insert sim card
        assertTrue(sMockModemManager.insertSimCard(eSosSlotId, eSosSimProfileId));
        TimeUnit.MILLISECONDS.sleep(TIMEOUT);
        if (shouldMoveSimToInService) {
            moveSimToInService(eSosSlotId, eSosSimProfileId);
        }

        sEsosSubId = SubscriptionManager.getSubscriptionId(eSosSlotId);
        assumeTrue(sEsosSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        logd(TAG, "setUpManualConnectTestEnvironment: sEsosSubId=" + sEsosSubId);
        if (!isActiveSubId(sEsosSubId)) {
            logd(TAG, "Skip the test because the ESOS subId is not active.");
            return;
        }
        setPhoneNumber(sEsosSubId, phoneNumber);

        grantSatelliteAndSendSmsPermissions();
        if (shouldSetUpMockSatelliteService) {
            setupMockSatelliteService();
            sMockSatelliteServiceManager.setSupportedRadioTechnologies(
                new int[]{NTRadioTechnology.NB_IOT_NTN});
            assertTrue(
                sMockSatelliteServiceManager.connectExternalSatelliteGatewayService());
            sMockSatelliteServiceManager.setDatagramControllerBooleanConfig(
                false,
                DatagramController.BOOLEAN_TYPE_WAIT_FOR_DEVICE_ALIGNMENT_IN_DEMO_DATAGRAM,
                true);
        }
        assertTrue(sMockSatelliteServiceManager.setSatelliteControllerTimeoutDuration(false,
                TIMEOUT_TYPE_EVALUATE_ESOS_PROFILES_PRIORITIZATION_DURATION_MILLIS, 5));

        // Enable CTS mode to ignore the requests from SG-APK and real Pointing UI app.
        assertTrue(sMockSatelliteServiceManager.setCtsMode(true));
        sSatelliteManager.setNtnSmsSupported(true);
        setUpSatelliteAccessAllowedAtDefaultTestLocation();

        setUpEsosSubscription(supportCtsSmsApp);
        enableCarrierRoamingSatelliteConfigs(
            eSosSlotId, CarrierConfigManager.CARRIER_ROAMING_NTN_CONNECT_MANUAL);
    }

    protected static void cleanUpManualConnectTestEnvironment(
        int slotId, int simProfileId) throws Exception {
        grantSatelliteAndSendSmsPermissions();

        if (sMockSatelliteServiceManager == null) return;
        sMockSatelliteServiceManager.setDatagramControllerBooleanConfig(true,
                DatagramController.BOOLEAN_TYPE_WAIT_FOR_DEVICE_ALIGNMENT_IN_DEMO_DATAGRAM, false);

        SatelliteModemStateCallbackTest callback = new SatelliteModemStateCallbackTest();
        long registerResult = sSatelliteManager.registerForModemStateChanged(
                getContext().getMainExecutor(), callback);
        assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerResult);
        assertTrue(callback.waitUntilResult(1));

        if (isSatelliteEnabled()) {
            logd(TAG, "Disable satellite");
            // Disable satellite modem to clean up all pending resources and reset telephony states.
            requestSatelliteEnabled(false);
            assertTrue(callback.waitUntilModemOff());
            assertFalse(isSatelliteEnabled());
        }

        assertTrue(sMockSatelliteServiceManager.restoreSatelliteGatewayServicePackageName());
        assertTrue(sMockSatelliteServiceManager.restoreSatelliteServicePackageName());
        waitFor(2000);
        sSatelliteManager.unregisterForModemStateChanged(callback);
        resetSatelliteAccessControlOverlayConfigs();
        resetSatelliteAccessForSatelliteSubscriptions();
        restoreSupportedMsgAppsForSatelliteSubscriptions();
        restoreProvisionedStates();
        restoreEsosSupportForActiveSubscriptions();
        assertTrue(sMockSatelliteServiceManager
                .setIsSatelliteCommunicationAllowedForCurrentLocationCache(
                        "cache_clear_and_not_allowed"));
        // Disable CTS mode to accept the requests from SG-APK and real Pointing UI app.
        assertTrue(sMockSatelliteServiceManager.setCtsMode(false));
        sSatelliteManager.setNtnSmsSupported(false);
        assertTrue(sMockSatelliteServiceManager.setSatelliteControllerTimeoutDuration(true,
                TIMEOUT_TYPE_EVALUATE_ESOS_PROFILES_PRIORITIZATION_DURATION_MILLIS, 0));
        revokeSatellitePermission();

        cleanUpMockSim(slotId, simProfileId, true);
    }

    protected static ServiceStateListenerTest registerServiceStateListener(int subId) {
        ServiceStateListenerTest serviceStateListener = new ServiceStateListenerTest();
        TelephonyManager telephonyManager = sTelephonyManager.createForSubscriptionId(subId);
        telephonyManager.registerTelephonyCallback(getContext().getMainExecutor(),
                serviceStateListener);
        // Current service state is broadcasted immediately at registration.
        serviceStateListener.waitUntilServiceStateChanged();
        serviceStateListener.clearServiceStateChanges();
        return serviceStateListener;
    }

    protected static void moveSimToInService(int slotId, int simProfileId) throws Exception {
        logd(TAG, "moveSimToInService: slotId=" + slotId + ", simProfileId=" + simProfileId);
        int subId = SubscriptionManager.getSubscriptionId(slotId);
        ServiceStateListenerTest serviceStateListener = registerServiceStateListener(subId);
        try {
            if (serviceStateListener.isInService()) {
                logd(TAG, "SIM slot is already in service");
            } else {
                sMockModemManager.changeNetworkService(slotId, simProfileId, true);
                assertTrue(serviceStateListener.waitUntilInService());
            }
        } finally {
            TelephonyManager telephonyManager = sTelephonyManager.createForSubscriptionId(subId);
            telephonyManager.unregisterTelephonyCallback(serviceStateListener);
        }
    }

    protected static boolean waitForEventOnSetSatelliteEnabledForCarrier(int expectedNumOfEvents) {
        if (getHalVersion(TelephonyManager.HAL_SERVICE_NETWORK) < RADIO_HAL_VERSION_2_3) {
            return sMockSatelliteServiceManager.waitForEventOnSetSatelliteEnabledForCarrier(
                    expectedNumOfEvents);
        }
        return sMockModemManager.waitForEventOnSetSatelliteEnabledForCarrier(expectedNumOfEvents);
    }

    protected static boolean waitForEventOnSatelliteEnabledForCarrierStateChanged(
            int expectedNumOfEvents) {
        if (getHalVersion(TelephonyManager.HAL_SERVICE_NETWORK) < RADIO_HAL_VERSION_2_3) {
            return sMockSatelliteServiceManager.waitForEventOnSatelliteEnabledForCarrierStateChanged(
                    expectedNumOfEvents);
        }
        return sMockModemManager.waitForEventOnSatelliteEnabledForCarrierStateChanged(
                expectedNumOfEvents);
    }

    protected static boolean waitForEventOnSetSatellitePlmn(int expectedNumOfEvents) {
        if (getHalVersion(TelephonyManager.HAL_SERVICE_NETWORK) < RADIO_HAL_VERSION_2_3) {
            return sMockSatelliteServiceManager.waitForEventOnSetSatellitePlmn(expectedNumOfEvents);
        }
        return sMockModemManager.waitForEventOnSetSatellitePlmn(expectedNumOfEvents);
    }

    @Nullable
    protected static List<String> getCarrierPlmnListConfigured(int slotId) {
        if (getHalVersion(TelephonyManager.HAL_SERVICE_NETWORK) < RADIO_HAL_VERSION_2_3) {
            return sMockSatelliteServiceManager.getCarrierPlmnList();
        }
        return sMockModemManager.getCarrierPlmnList(slotId);
    }

    @Nullable
    protected static List<String> getAllSatellitePlmnListConfigured(int slotId) {
        if (getHalVersion(TelephonyManager.HAL_SERVICE_NETWORK) < RADIO_HAL_VERSION_2_3) {
            return sMockSatelliteServiceManager.getAllSatellitePlmnList();
        }
        return sMockModemManager.getAllSatellitePlmnList(slotId);
    }

    protected static boolean getIsSatelliteEnabledForCarrierInMockService(int slotId) {
        if (getHalVersion(TelephonyManager.HAL_SERVICE_NETWORK) < RADIO_HAL_VERSION_2_3) {
            Boolean receivedResult = sMockSatelliteServiceManager.getIsSatelliteEnabledForCarrier();
            return receivedResult != null ? receivedResult : false;
        }
        return sMockModemManager.getIsSatelliteEnabledForCarrier(slotId);
    }

    protected static void testQuerySatelliteEntitlementService_success(int slotId,
        @CarrierConfigManager.CARRIER_ROAMING_NTN_CONNECT_TYPE int connectType) throws Exception {
        logd(TAG, "testQuerySatelliteEntitlementService_success: slotId=" + slotId);

        assertTrue(sMockSatelliteServiceManager
                .overrideSatelliteEntilementQueryConditions(true, true));
        assertTrue(sMockSatelliteServiceManager.setSatelliteIgnorePlmnListFromStorage(true));
        sMockSatelliteServiceManager.setMaxAllowedDataModeForCtsTest(
            SatelliteManager.SATELLITE_DATA_SUPPORT_UNCONSTRAINED);
        try {
            logd(TAG, "testQuerySatelliteEntitlementService_success: test entitlement disabled");
            int subId = SubscriptionManager.getSubscriptionId(slotId);
            sMockModemManager.clearEventOnSetSatellitePlmn();
            sMockModemManager.clearEventOnSetSatelliteEnabledForCarrier();
            sMockSatelliteServiceManager.clearEventOnSetSatellitePlmn();
            sMockSatelliteServiceManager.clearEventOnSetSatelliteEnabledForCarrier();
            prepareValidDisabledEntitlementStatus();
            enableSatelliteEntitlementSupport(subId);

            // Telephony should have requested the modem to disable satellite for the carrier.
            waitForAccessRestrictionReason(subId,
                    SatelliteManager.SATELLITE_COMMUNICATION_RESTRICTION_REASON_ENTITLEMENT);
            waitForSatelliteDisabledForCarrier(slotId);
            // Verify that the PLMN list come from carrier config.
            String satellitePlmn = sMockModemManager.getSimInfo(slotId,
                MockModemConfigBase.SimInfoChangedResult.SIM_INFO_TYPE_MCC_MNC);
            List<String> expectedTelephonyCarrierPlmnList = new ArrayList<>();
            expectedTelephonyCarrierPlmnList.add(satellitePlmn);
            waitForCarrierPlmnListConfigured(slotId, expectedTelephonyCarrierPlmnList);
            waitForCarrierPlmnListAvailableInTelephony(subId, expectedTelephonyCarrierPlmnList);
            int dataMode = sSatelliteManager.getSatelliteDataSupportMode(subId);
            assertEquals((long) SatelliteManager.SATELLITE_DATA_SUPPORT_RESTRICTED,
                (long) dataMode);

            logd(TAG, "testQuerySatelliteEntitlementService_success: test entitlement enabled");
            sMockModemManager.clearEventOnSetSatellitePlmn();
            sMockModemManager.clearEventOnSetSatelliteEnabledForCarrier();
            sMockSatelliteServiceManager.clearEventOnSetSatellitePlmn();
            sMockSatelliteServiceManager.clearEventOnSetSatelliteEnabledForCarrier();
            EntilementStatusResponseGenerator entilementStatusResponseGenerator =
                prepareValidEnabledEntitlementStatus(false);
            enableSatelliteEntitlementSupport(subId);

            // The allowed and barred PLMNs received from the entitlement service should
            // be configured to modem.
            List<String> allowedPlmnList = entilementStatusResponseGenerator.getAllowedPlmns();
            List<String> barredPlmnList = entilementStatusResponseGenerator.getBarredPlmns();
            logd(TAG, "allowedPlmnList: " + String.join(", ", allowedPlmnList));
            logd(TAG, "barredPlmnList: " + String.join(", ", barredPlmnList));
            waitForCarrierPlmnListConfigured(slotId, allowedPlmnList);

            // Verify that the allowed and barred PLMNs are configured correctly.
            List<String> allSatellitePlmnListConfigured = getAllSatellitePlmnListConfigured(slotId);
            List<String> carrierPlmnListConfigured = getCarrierPlmnListConfigured(slotId);
            logd(TAG, "allSatellitePlmnListConfigured: "
                + String.join(", ", allSatellitePlmnListConfigured));
            assertThat(allSatellitePlmnListConfigured).containsAtLeastElementsIn(allowedPlmnList);
            assertThat(allSatellitePlmnListConfigured).containsAtLeastElementsIn(barredPlmnList);
            assertThat(carrierPlmnListConfigured).containsNoneIn(barredPlmnList);

            // Verify that Telephony has updated its internal state correctly.
            waitForCarrierPlmnListAvailableInTelephony(subId, allowedPlmnList);
            dataMode = sSatelliteManager.getSatelliteDataSupportMode(subId);
            assertEquals((long) SatelliteManager.SATELLITE_DATA_SUPPORT_UNCONSTRAINED,
                    (long) dataMode);

            waitForAccessRestrictionReasonToBeRemoved(subId,
                    SatelliteManager.SATELLITE_COMMUNICATION_RESTRICTION_REASON_ENTITLEMENT);

            if (connectType == CarrierConfigManager.CARRIER_ROAMING_NTN_CONNECT_AUTOMATIC) {
                waitForSatelliteEnabledForCarrier(slotId);
            } else {
                waitForSatelliteDisabledForCarrier(slotId);
            }
        } finally {
            logd(TAG, "testQuerySatelliteEntitlementService_success: restore test environment");
            sMockSatelliteServiceManager
                .overrideSatelliteEntilementQueryConditions(false, false);
            sMockSatelliteServiceManager
                .overrideSatelliteEntilementStatusResponseForCtsTest(null, false);
            sMockSatelliteServiceManager.setSatelliteIgnorePlmnListFromStorage(false);
            sMockSatelliteServiceManager.setMaxAllowedDataModeForCtsTest(-1);
        }
    }

    private static void setupEnvironmentForSatelliteDataTest(int slotId,
            int subId) throws Exception {
        logd(TAG, "setupEnvironmentForSatelliteDataTest");
        sMockModemManager.clearEventOnSetSatellitePlmn();
        sMockModemManager.clearEventOnSetSatelliteEnabledForCarrier();
        sMockSatelliteServiceManager.clearEventOnSetSatellitePlmn();
        sMockSatelliteServiceManager.clearEventOnSetSatelliteEnabledForCarrier();
        prepareValidDisabledEntitlementStatus();
        enableSatelliteEntitlementSupport(subId);

        // Telephony should have requested the modem to disable satellite for the carrier.
        waitForAccessRestrictionReason(subId,
                SatelliteManager.SATELLITE_COMMUNICATION_RESTRICTION_REASON_ENTITLEMENT);
        waitForSatelliteDisabledForCarrier(slotId);

        sMockModemManager.clearEventOnSetSatellitePlmn();
        sMockModemManager.clearEventOnSetSatelliteEnabledForCarrier();
        sMockSatelliteServiceManager.clearEventOnSetSatellitePlmn();
        sMockSatelliteServiceManager.clearEventOnSetSatelliteEnabledForCarrier();
    }

    private static void enableSatelliteEntitlementSupportAndValidate(int slotId, int subId)
            throws Exception {
        logd(TAG, "enableSatelliteEntitlementSupportAndValidate");
        EntilementStatusResponseGenerator entilementStatusResponseGenerator =
                prepareValidEnabledEntitlementStatus(true);
        enableSatelliteEntitlementSupport(subId);

        // The allowed and barred PLMNs received from the entitlement service should
        // be configured to modem.
        List<String> allowedPlmnList = entilementStatusResponseGenerator.getAllowedPlmns();
        logd(TAG, "allowedPlmnList: " + String.join(", ", allowedPlmnList));
        waitForCarrierPlmnListConfigured(slotId, allowedPlmnList);

        // Verify that the allowed and barred PLMNs are configured correctly.
        List<String> allSatellitePlmnListConfigured = getAllSatellitePlmnListConfigured(slotId);
        assertThat(allSatellitePlmnListConfigured).containsAtLeastElementsIn(allowedPlmnList);

        // Verify that Telephony has updated its internal state correctly.
        waitForCarrierPlmnListAvailableInTelephony(subId, allowedPlmnList);
    }

    private static void resetSatelliteDataRelatedConfigurations() {
        logd(TAG, "resetSatelliteDataRelatedConfigurations");
        sMockSatelliteServiceManager
                .overrideSatelliteEntilementQueryConditions(false, false);
        sMockSatelliteServiceManager
                .overrideSatelliteEntilementStatusResponseForCtsTest(null, false);
        sMockSatelliteServiceManager.setSatelliteIgnorePlmnListFromStorage(false);
        sMockSatelliteServiceManager.setMaxAllowedDataModeForCtsTest(-1);
    }

    protected static void testSatelliteConstrainedNetwork(int slotId) throws Exception {

        logd(TAG, "testSatelliteConstrainedNetwork: slotId=" + slotId);

        assertTrue(sMockSatelliteServiceManager
                .overrideSatelliteEntilementQueryConditions(true, true));
        assertTrue(sMockSatelliteServiceManager.setSatelliteIgnorePlmnListFromStorage(true));
        sMockSatelliteServiceManager.setMaxAllowedDataModeForCtsTest(
                SatelliteManager.SATELLITE_DATA_SUPPORT_CONSTRAINED);
        NetworkCallback testNetworkCallback = null;
        try {
            int subId = SubscriptionManager.getSubscriptionId(slotId);
            setupEnvironmentForSatelliteDataTest(slotId, subId);

            enableSatelliteEntitlementSupportAndValidate(slotId, subId);

            // validate data mode is constrained mode
            int dataMode = sSatelliteManager.getSatelliteDataSupportMode(subId);
            assertEquals((long) SatelliteManager.SATELLITE_DATA_SUPPORT_CONSTRAINED,
                    (long) dataMode);

            // Disable and enable data back to bring fresh pdn connection with new data mode
            if (sTelephonyManager.isDataEnabled()) {
                logd(TAG, "data is disabled");
                ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                        sTelephonyManager,
                        (tm) -> tm.setDataEnabledForReason(
                                TelephonyManager.DATA_ENABLED_REASON_USER, false));
            }

            TimeUnit.MILLISECONDS.sleep(3000);

            logd(TAG, "data is enabled back");
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                    sTelephonyManager,
                    (tm) -> tm.setDataEnabledForReason(
                            TelephonyManager.DATA_ENABLED_REASON_USER, true));

            // validate for satellite constrained data network
            testNetworkCallback = new NetworkCallback();
            testNetworkCallback.setNetworkCallbackTimeOut(CALLBACK_TIMEOUT_MS);

            NetworkRequest networkRequest = new NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VCN_MANAGED)
                    .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_BANDWIDTH_CONSTRAINED)
                    .addTransportType(NetworkCapabilities.TRANSPORT_SATELLITE)
                    .build();

            if (sConnectivityManager != null) {
                sConnectivityManager.registerNetworkCallback(networkRequest, testNetworkCallback);
                sConnectivityManager.requestNetwork(networkRequest, testNetworkCallback,
                        NETWORK_REQUEST_TIMEOUT_MS);

                // validate if onAvailable was received
                Pair<Boolean, Network> cbStatusForAvailable =
                        testNetworkCallback.waitForAvailable();

                // Validate onAvailable callback() for csn connection
                logd(TAG, "received network available callback");
                assertThat(cbStatusForAvailable.first).isTrue();

                // assert the network is not null
                assertNotNull(cbStatusForAvailable.second);

                // validate if onCapabilityChanged callback was received
                boolean cbStatusForCapabilityChanged =
                        testNetworkCallback.waitForCapabilitiesChanged();

                logd(TAG, "received network capability changed callback");
                assertThat(cbStatusForCapabilityChanged).isTrue();

                // Validate if satellite network is constrained
                assertFalse(sCurrentNetworkCapabilities.hasCapability(
                        NetworkCapabilities.NET_CAPABILITY_NOT_BANDWIDTH_CONSTRAINED));
            } else {
                loge(TAG, "connecivityManager initialisation failure");
            }
        } finally {
            if (sConnectivityManager != null  && testNetworkCallback != null) {
                sConnectivityManager.unregisterNetworkCallback(testNetworkCallback);
            }
            resetSatelliteDataRelatedConfigurations();
        }
    }

    protected static void
    testNoSatelliteConstrainedNetworkConnection_WithNonConstrainedDataMode(int slotId)
            throws Exception {
        logd(TAG, "testNoConstrainedNetworkConnection: slotId=" + slotId);

        assertTrue(sMockSatelliteServiceManager
                .overrideSatelliteEntilementQueryConditions(true, true));
        assertTrue(sMockSatelliteServiceManager.setSatelliteIgnorePlmnListFromStorage(true));
        sMockSatelliteServiceManager.setMaxAllowedDataModeForCtsTest(
                SatelliteManager.SATELLITE_DATA_SUPPORT_RESTRICTED);
        NetworkCallback testNetworkCallback = null;
        try {
            int subId = SubscriptionManager.getSubscriptionId(slotId);
            setupEnvironmentForSatelliteDataTest(slotId, subId);

            enableSatelliteEntitlementSupportAndValidate(slotId, subId);

            // validate data mode is restricted since max allowed data mode is restricted mode
            int dataMode = sSatelliteManager.getSatelliteDataSupportMode(subId);
            assertEquals((long) SatelliteManager.SATELLITE_DATA_SUPPORT_RESTRICTED,
                    (long) dataMode);

            // Disable and enable data back to bring fresh pdn connection with new data mode
            if (sTelephonyManager.isDataEnabled()) {
                logd(TAG, "data is disabled");
                ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                        sTelephonyManager,
                        (tm) -> tm.setDataEnabledForReason(
                                TelephonyManager.DATA_ENABLED_REASON_USER, false));
            }

            TimeUnit.MILLISECONDS.sleep(3000);

            logd(TAG, "data is enabled back");
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                    sTelephonyManager,
                    (tm) -> tm.setDataEnabledForReason(
                            TelephonyManager.DATA_ENABLED_REASON_USER, true));

            TimeUnit.MILLISECONDS.sleep(3000);

            testNetworkCallback = new NetworkCallback();
            testNetworkCallback.setNetworkCallbackTimeOut(CALLBACK_TIMEOUT_MS);

            NetworkRequest networkRequest = new NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VCN_MANAGED)
                    .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_BANDWIDTH_CONSTRAINED)
                    .addTransportType(NetworkCapabilities.TRANSPORT_SATELLITE)
                    .build();

            // validate there is no satellite constrained data network connection
            if (sConnectivityManager != null) {
                sConnectivityManager.registerNetworkCallback(networkRequest, testNetworkCallback);
                sConnectivityManager.requestNetwork(networkRequest, testNetworkCallback,
                        NETWORK_REQUEST_TIMEOUT_MS);

               Pair<Boolean, Network> cbStatusForAvailable =
                        testNetworkCallback.waitForAvailable();

               // Validate there is no callback and network is null
               assertThat(cbStatusForAvailable.first).isFalse();
               assertNull(cbStatusForAvailable.second);

            } else {
                loge(TAG, "connecivityManager initialisation failure");
            }
        } finally {
            if (sConnectivityManager != null  && testNetworkCallback != null) {
                sConnectivityManager.unregisterNetworkCallback(testNetworkCallback);
            }
            resetSatelliteDataRelatedConfigurations();
        }
    }

    protected static void
    testNoSatelliteConstrainedNetworkConnection_WithBandwidthNotConstrainedCapability(int slotId)
            throws Exception {
        logd(TAG, "testNoConstrainedNetworkConnection: slotId=" + slotId);

        assertTrue(sMockSatelliteServiceManager
                .overrideSatelliteEntilementQueryConditions(true, true));
        assertTrue(sMockSatelliteServiceManager.setSatelliteIgnorePlmnListFromStorage(true));
        sMockSatelliteServiceManager.setMaxAllowedDataModeForCtsTest(
                SatelliteManager.SATELLITE_DATA_SUPPORT_UNCONSTRAINED);
        NetworkCallback testNetworkCallback = null;
        try {
            int subId = SubscriptionManager.getSubscriptionId(slotId);
            setupEnvironmentForSatelliteDataTest(slotId, subId);

            enableSatelliteEntitlementSupportAndValidate(slotId, subId);

            // validate data mode is restricted since max allowed data mode is restricted mode
            int dataMode = sSatelliteManager.getSatelliteDataSupportMode(subId);
            assertEquals((long) SatelliteManager.SATELLITE_DATA_SUPPORT_CONSTRAINED,
                    (long) dataMode);

            testNetworkCallback = new NetworkCallback();
            testNetworkCallback.setNetworkCallbackTimeOut(CALLBACK_TIMEOUT_MS);

            NetworkRequest networkRequest = new NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VCN_MANAGED)
                    .addTransportType(NetworkCapabilities.TRANSPORT_SATELLITE)
                    .build();

            // validate there is no satellite constrained data network connection
            if (sConnectivityManager != null) {
                sConnectivityManager.registerNetworkCallback(networkRequest, testNetworkCallback);
                sConnectivityManager.requestNetwork(networkRequest, testNetworkCallback,
                        NETWORK_REQUEST_TIMEOUT_MS);

                Pair<Boolean, Network> cbStatusForAvailable =
                        testNetworkCallback.waitForAvailable();
                // Validate there is no callback and network is null
                assertThat(cbStatusForAvailable.first).isFalse();
                assertNull(cbStatusForAvailable.second);

                sConnectivityManager.unregisterNetworkCallback(testNetworkCallback);
            } else {
                loge(TAG, "connecivityManager initialisation failure");
            }
        } finally {
            if (sConnectivityManager != null  && testNetworkCallback != null) {
                sConnectivityManager.unregisterNetworkCallback(testNetworkCallback);
            }
            resetSatelliteDataRelatedConfigurations();
        }
    }

    private static EntilementStatusResponseGenerator prepareValidDisabledEntitlementStatus() {
        logd(TAG, "prepareValidDisabledEntitlementStatus");
        EntilementStatusResponseGenerator entilementStatusResponseGenerator =
                new EntilementStatusResponseGenerator();
        entilementStatusResponseGenerator.setEntitlementStatus(
                EntilementStatusResponseGenerator.SATELLITE_ENTITLEMENT_STATUS_DISABLED);
        String t43Response = entilementStatusResponseGenerator.createTS43Response();
        assertTrue(sMockSatelliteServiceManager
                .overrideSatelliteEntilementStatusResponseForCtsTest(t43Response, false));
        return entilementStatusResponseGenerator;
    }

    private static EntilementStatusResponseGenerator prepareValidEnabledEntitlementStatus(
            boolean isConstrained) {
        logd(TAG, "prepareValidEnabledEntitlementStatus");
        EntilementStatusResponseGenerator entilementStatusResponseGenerator =
                new EntilementStatusResponseGenerator();
        entilementStatusResponseGenerator.setEntitlementStatus(
                EntilementStatusResponseGenerator.SATELLITE_ENTITLEMENT_STATUS_ENABLED);
        List<EntilementStatusResponseGenerator.SatelliteNetworkInfo> supportedPlmnsAndServices =
                EntilementStatusResponseGenerator.createDefaultValidSatelliteNetworkInfoList(
                        isConstrained);
        entilementStatusResponseGenerator.setSupportedPlmnsAndServices(supportedPlmnsAndServices);
        List<String> barredPlmnList = ImmutableList.of("46601", "46602");
        entilementStatusResponseGenerator.setBarredPlmns(barredPlmnList);
        String t43Response = entilementStatusResponseGenerator.createTS43Response();
        assertTrue(sMockSatelliteServiceManager
                .overrideSatelliteEntilementStatusResponseForCtsTest(t43Response, false));
        return entilementStatusResponseGenerator;
    }

    private static void waitForAccessRestrictionReason(int subId,
        @SatelliteManager.SatelliteCommunicationRestrictionReason int expectedRestrictionReason) {
        logd(TAG, "waitForAccessRestrictionReason: subId=" + subId
                + ", expectedRestrictionReason=" + expectedRestrictionReason);
        int i = 0;
        int maxRetry = 5;
        for (; i < maxRetry; i++) {
            assertTrue(waitForEventOnSetSatelliteEnabledForCarrier(1));
            Set<Integer> restrictionReasons =
                sSatelliteManager.getAttachRestrictionReasonsForCarrier(subId);
            logd(TAG, "testQuerySatelliteEntitlementService_success: restrictionReasons="
                + restrictionReasons);
            if (restrictionReasons.contains(expectedRestrictionReason)) {
                break;
            }
        }
        assertThat(i).isLessThan(maxRetry);
    }

    private static void waitForAccessRestrictionReasonToBeRemoved(int subId,
        @SatelliteManager.SatelliteCommunicationRestrictionReason int expectedRestrictionReason) {
        logd(TAG, "waitForAccessRestrictionReasonToBeRemoved: subId=" + subId
                + ", expectedRestrictionReason=" + expectedRestrictionReason);
        int i = 0;
        int maxRetry = 5;
        for (; i < maxRetry; i++) {
            assertTrue(waitForEventOnSetSatelliteEnabledForCarrier(1));
            Set<Integer> restrictionReasons =
                sSatelliteManager.getAttachRestrictionReasonsForCarrier(subId);
            logd(TAG, "testQuerySatelliteEntitlementService_success: restrictionReasons="
                + restrictionReasons);
            if (!restrictionReasons.contains(expectedRestrictionReason)) {
                break;
            }
        }
        assertThat(i).isLessThan(maxRetry);
    }

    private static void waitForSatelliteEnabledForCarrier(int slotId) {
        logd(TAG, "waitForSatelliteEnabledForCarrier: slotId=" + slotId);
        if (getIsSatelliteEnabledForCarrierInMockService(slotId)) {
            return;
        }

        int i = 0;
        int maxRetry = 5;
        for (; i < maxRetry; i++) {
            assertTrue(waitForEventOnSatelliteEnabledForCarrierStateChanged(1));
            if (getIsSatelliteEnabledForCarrierInMockService(slotId)) {
                break;
            }
        }
        assertThat(i).isLessThan(maxRetry);
    }

    private static void waitForSatelliteDisabledForCarrier(int slotId) {
        logd(TAG, "waitForSatelliteDisabledForCarrier: slotId=" + slotId);
        if (!getIsSatelliteEnabledForCarrierInMockService(slotId)) {
                return;
        }

        int i = 0;
        int maxRetry = 5;
        for (; i < maxRetry; i++) {
            assertTrue(waitForEventOnSatelliteEnabledForCarrierStateChanged(1));
            if (!getIsSatelliteEnabledForCarrierInMockService(slotId)) {
                break;
            }
        }
        assertThat(i).isLessThan(maxRetry);
    }

    private static void waitForCarrierPlmnListConfigured(
        int slotId, List<String> expectedCarrierPlmnList) {
        logd(TAG, "waitForCarrierPlmnListConfigured: slotId=" + slotId
                + ", expectedCarrierPlmnList=" + String.join(", ", expectedCarrierPlmnList));
        int i = 0;
        int maxRetry = 5;
        for (; i < maxRetry; i++) {
            assertTrue(waitForEventOnSetSatellitePlmn(1));
            List<String> carrierPlmnListConfigured = getCarrierPlmnListConfigured(slotId);
            logd(TAG, "carrierPlmnListConfigured=" + carrierPlmnListConfigured);
            if (expectedCarrierPlmnList.size() == carrierPlmnListConfigured.size()
                && carrierPlmnListConfigured.containsAll(expectedCarrierPlmnList)) {
                break;
            }
        }
        assertThat(i).isLessThan(maxRetry);
    }

    private static void waitForCarrierPlmnListAvailableInTelephony(
        int subId, List<String> expectedCarrierPlmnList) {
        logd(TAG, "waitForCarrierPlmnListAvailableInTelephony: subId=" + subId
                + ", expectedCarrierPlmnList=" + String.join(", ", expectedCarrierPlmnList));
        int i = 0;
        int maxRetry = 5;
        for (; i < maxRetry; i++) {
            List<String> carrierPlmnList = sSatelliteManager.getSatellitePlmnsForCarrier(subId);
            logd(TAG, "carrierPlmnList=" + String.join(", ", carrierPlmnList));
            if (areListsTheSame(carrierPlmnList, expectedCarrierPlmnList)) {
                break;
            }
            waitFor(500);
        }
        assertThat(i).isLessThan(maxRetry);
    }
}
