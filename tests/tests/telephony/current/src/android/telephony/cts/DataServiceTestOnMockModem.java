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

package android.telephony.cts;

import static android.telephony.mockmodem.MockSimService.MOCK_SIM_PROFILE_ID_TWN_CHT;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.telephony.DisconnectCause;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;
import android.telephony.cts.util.TelephonyUtils;
import android.telephony.mockmodem.MockModemManager;
import android.util.Log;
import android.util.Pair;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.internal.telephony.flags.Flags;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

public class DataServiceTestOnMockModem {
    private static final String LOG_TAG = "DataServiceTestOnMockModem";
    public static final int RADIO_HAL_VERSION_2_4 = makeRadioVersion(2, 4);
    // the timeout to wait for latch countdonw in milliseconds
    private static final int WAIT_LATCH_TIMEOUT_MS = 10000;
    // the timeout to wait for result in milliseconds
    private static final int WAIT_UPDATE_TIMEOUT_MS = 5000;

    private static final int TEST_SLOT = 0;

    private static final int LATCH_SET_USER_DATA_ENABLED = 0;
    private static final int LATCH_SET_USER_DATA_ROAMING_ENABLED = 1;
    private static final int LATCH_NOTIFY_IMS_DATA_NETWORK = 2;

    private PackageManager mPackageManager;
    private MockModemManager mMockModemManager;
    private TelephonyManager mTelephonyManager;
    private ConnectivityManager.NetworkCallback mNetworkCallback;
    private int mTestSub = 0;

    private boolean mInitialIsUserDataEnabled = false;
    private boolean mInitialIsUserDataRoamingEnabled = false;

    private boolean mSupportTelephonyData = false;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void beforeTest() throws Exception {
        mPackageManager = getContext().getPackageManager();
        mSupportTelephonyData =
                mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_DATA);
        assumeTrue("Device does not support telephony data", mSupportTelephonyData);

        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(
                        Manifest.permission.MODIFY_PHONE_STATE,
                        Manifest.permission.CONNECTIVITY_USE_RESTRICTED_NETWORKS);

        MockModemManager.enforceMockModemDeveloperSetting();
        mMockModemManager = new MockModemManager();
        assertNotNull(mMockModemManager);
        assertTrue(mMockModemManager.connectMockModemService());
        assertTrue(mMockModemManager.insertSimCard(TEST_SLOT, MOCK_SIM_PROFILE_ID_TWN_CHT));

        int sub = SubscriptionManager.getSubscriptionId(TEST_SLOT);
        if (SubscriptionManager.isValidSubscriptionId(sub)) {
            mTestSub = sub;
        }

        mTelephonyManager =
                ((TelephonyManager) getContext().getSystemService(Context.TELEPHONY_SERVICE))
                        .createForSubscriptionId(mTestSub);

        TimeUnit.MILLISECONDS.sleep(WAIT_UPDATE_TIMEOUT_MS);

        int simCardState = mTelephonyManager.getSimCardState();
        assertEquals(TelephonyManager.SIM_STATE_PRESENT, simCardState);

        TimeUnit.MILLISECONDS.sleep(WAIT_UPDATE_TIMEOUT_MS);

        // Check SIM state ready
        simCardState = mTelephonyManager.getSimState();
        assertEquals(TelephonyManager.SIM_STATE_READY, simCardState);

        TimeUnit.MILLISECONDS.sleep(WAIT_UPDATE_TIMEOUT_MS);
        assertTrue(mMockModemManager.changeNetworkService(TEST_SLOT, 310260, true));

        mInitialIsUserDataEnabled =
                mTelephonyManager.isDataEnabledForReason(TelephonyManager.DATA_ENABLED_REASON_USER);
        mInitialIsUserDataRoamingEnabled = mTelephonyManager.isDataRoamingEnabled();

        if (mMockModemManager != null) {
            mTelephonyManager.setDataEnabledForReason(
                    TelephonyManager.DATA_ENABLED_REASON_USER, false);
            mTelephonyManager.setDataRoamingEnabled(false);

            TimeUnit.MILLISECONDS.sleep(WAIT_UPDATE_TIMEOUT_MS);

            mMockModemManager.resetDataAllLatchCountdown(TEST_SLOT);
        }
    }

    private void restoreUserDataEnabledWithCallback(boolean targetState) {
        if (mTelephonyManager.isDataEnabledForReason(TelephonyManager.DATA_ENABLED_REASON_USER)
                == targetState) {
            return;
        }

        final CountDownLatch latch = new CountDownLatch(1);

            class DataStateCallback extends TelephonyCallback
                    implements TelephonyCallback.UserMobileDataStateListener {
                @Override
                public void onUserMobileDataStateChanged(boolean enabled) {
                    if (enabled == targetState) {
                        latch.countDown();
                    }
                }
            }

        DataStateCallback callback = new DataStateCallback();
        Executor executor = getContext().getMainExecutor();

        try {
            mTelephonyManager.registerTelephonyCallback(executor, callback);
            mTelephonyManager.setDataEnabledForReason(
                    TelephonyManager.DATA_ENABLED_REASON_USER, targetState);

            boolean success = latch.await(5, TimeUnit.SECONDS);
            if (!success) {
                Log.w(LOG_TAG, "Timeout waiting for User Data to restore to: " + targetState);
            }
        } catch (Exception e) {
            Log.w(LOG_TAG, "Exception while restoring User Data state", e);
        } finally {
            mTelephonyManager.unregisterTelephonyCallback(callback);
        }
    }

    @After
    public void afterTest() throws Exception {
        if (!mSupportTelephonyData) return;

        if (mNetworkCallback != null) {
            ConnectivityManager cm =
                    (ConnectivityManager) getContext().getSystemService(ConnectivityManager.class);
            try {
                cm.unregisterNetworkCallback(mNetworkCallback);
            } catch (IllegalArgumentException e) {
                // ignore the case where the callback is already unregistered
            }
            mNetworkCallback = null;
        }

        if (mMockModemManager != null) {
            mMockModemManager.clearAllCalls(TEST_SLOT, DisconnectCause.POWER_OFF);
        }

        TelephonyUtils.endBlockSuppression(InstrumentationRegistry.getInstrumentation());
        if (mTelephonyManager != null) {
            try {
                restoreUserDataEnabledWithCallback(mInitialIsUserDataEnabled);
                mTelephonyManager.setDataRoamingEnabled(mInitialIsUserDataRoamingEnabled);
            } catch (Exception e) {
                Log.w(LOG_TAG, "Failed to restore data state", e);
            }
        }

        // Rebind all interfaces which is binding to MockModemService to default.
        if (mMockModemManager != null) {
            assertTrue(mMockModemManager.changeNetworkService(TEST_SLOT, 310260, false));
            mMockModemManager.removeSimCard(TEST_SLOT);
            assertTrue(mMockModemManager.disconnectMockModemService());
            mMockModemManager = null;
            TimeUnit.MILLISECONDS.sleep(WAIT_UPDATE_TIMEOUT_MS);
        }

        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .dropShellPermissionIdentity();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_DATA_SERVICE_USER_DATA_TOGGLE_NOTIFY)
    public void testNotifyUserDataEnabled() {
        assumeTrue("Device does not support RADIO HAL V2_4", canMakeRequest(RADIO_HAL_VERSION_2_4));
        mTelephonyManager.setDataEnabledForReason(TelephonyManager.DATA_ENABLED_REASON_USER, true);
        waitForDataLatchCountdown(LATCH_SET_USER_DATA_ENABLED);
        assertTrue(mMockModemManager.getIsUserDataEnabled(TEST_SLOT));

        mMockModemManager.resetDataAllLatchCountdown(TEST_SLOT);

        mTelephonyManager.setDataEnabledForReason(TelephonyManager.DATA_ENABLED_REASON_USER, false);
        waitForDataLatchCountdown(LATCH_SET_USER_DATA_ENABLED);
        assertFalse(mMockModemManager.getIsUserDataEnabled(TEST_SLOT));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_DATA_SERVICE_USER_DATA_TOGGLE_NOTIFY)
    public void testNotifyUserDataRoamingEnabled() {
        assumeTrue("Device does not support RADIO HAL V2_4", canMakeRequest(RADIO_HAL_VERSION_2_4));
        mTelephonyManager.setDataRoamingEnabled(true);
        waitForDataLatchCountdown(LATCH_SET_USER_DATA_ROAMING_ENABLED);
        assertTrue(mMockModemManager.getIsUserDataRoamingEnabled(TEST_SLOT));

        mMockModemManager.resetDataAllLatchCountdown(TEST_SLOT);

        mTelephonyManager.setDataRoamingEnabled(false);
        waitForDataLatchCountdown(LATCH_SET_USER_DATA_ROAMING_ENABLED);
        assertFalse(mMockModemManager.getIsUserDataRoamingEnabled(TEST_SLOT));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_DATA_SERVICE_NOTIFY_IMS_DATA_NETWORK)
    public void testNotifyImsDataNetwork() {
        assumeTrue(
                "Device does not have FEATURE_TELEPHONY_IMS",
                mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_IMS));
        assumeTrue("Device does not support RADIO HAL V2_4", canMakeRequest(RADIO_HAL_VERSION_2_4));
        ConnectivityManager connectivityManager =
                (ConnectivityManager) getContext().getSystemService(ConnectivityManager.class);
        NetworkRequest.Builder builder = new NetworkRequest.Builder();
        builder.addCapability(NetworkCapabilities.NET_CAPABILITY_IMS);
        builder.addCapability(NetworkCapabilities.NET_CAPABILITY_MMTEL);
        mNetworkCallback = createSimpleCallback();
        connectivityManager.requestNetwork(builder.build(), mNetworkCallback);

        waitForDataLatchCountdown(LATCH_NOTIFY_IMS_DATA_NETWORK);
        assertTrue(mMockModemManager.getIsImsDataNetworkNotified(TEST_SLOT));
    }

    private static ConnectivityManager.NetworkCallback createSimpleCallback() {
        return new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {}

            @Override
            public void onLost(Network network) {}
        };
    }

    private static boolean canMakeRequest(int minHalVersion) {
        TelephonyManager tm =
                (TelephonyManager) getContext().getSystemService(Context.TELEPHONY_SERVICE);
        Pair<Integer, Integer> halVersion = tm.getHalVersion(TelephonyManager.HAL_SERVICE_DATA);
        int currentHalVersion = makeRadioVersion(halVersion.first, halVersion.second);
        if (currentHalVersion < minHalVersion) {
            Log.d(LOG_TAG, "canMakeRequest: the current HAL version = " + currentHalVersion);
            return false;
        }
        return true;
    }

    private static int makeRadioVersion(int major, int minor) {
        if (major < 0 || minor < 0) return 0;
        return major * 100 + minor;
    }

    private boolean waitForDataLatchCountdown(int latchIndex) {
        return waitForDataLatchCountdown(latchIndex, WAIT_LATCH_TIMEOUT_MS);
    }

    private boolean waitForDataLatchCountdown(int latchIndex, int waitMs) {
        return mMockModemManager.waitForDataLatchCountdown(TEST_SLOT, latchIndex, waitMs);
    }

    protected static Context getContext() {
        return InstrumentationRegistry.getInstrumentation().getContext();
    }
}
