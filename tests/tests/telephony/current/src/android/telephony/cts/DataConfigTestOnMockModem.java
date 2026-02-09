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

package android.telephony.cts;

import static android.telephony.mockmodem.MockSimService.MOCK_SIM_PROFILE_ID_TWN_CHT;
import static android.telephony.mockmodem.MockSimService.MOCK_SIM_PROFILE_ID_TWN_FET;

import static com.android.compatibility.common.util.SystemUtil.runShellCommand;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.radio.data.DataProfileInfo;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.Uri;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.telephony.ServiceState;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.ShellIdentityUtils;
import com.android.compatibility.common.util.SystemUtil;
import com.android.internal.telephony.flags.Flags;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/** Tests for Dynamic Data Config Update. */
public class DataConfigTestOnMockModem extends MockModemTestBase {
    private static final String TAG = "DataConfigTest";
    private static final int TIMEOUT_MS = 20000;
    private static final byte CAPABILITY_PRIORITIZE_LATENCY = (byte) 128;
    private static final String CONFIG_UPDATER_PACKAGE = "com.google.android.configupdater";
    private static final String UPDATE_TELEPHONY_CONFIG_INTENT =
            "com.google.android.configupdater.TelephonyConfigUpdate.UPDATE_CONFIG";
    private static final String TEST_V1_CONFIG_DATA_CONTENT_LOCAL_URI =
            "file:///cts_test_V1_telephony_config.pb";
    private static final String TEST_V1_CONFIG_DATA_METADATA_LOCAL_URI =
            "file:///cts_test_V1_telephony_config-metadata.txt";
    private static final String TEST_V2_CONFIG_DATA_CONTENT_LOCAL_URI =
            "file:///cts_test_V2_telephony_config.pb";
    private static final String TEST_V2_CONFIG_DATA_METADATA_LOCAL_URI =
            "file:///cts_test_V2_telephony_config-metadata.txt";
    private static final String TEST_V3_CONFIG_DATA_CONTENT_LOCAL_URI =
            "file:///cts_test_V3_telephony_config.pb";
    private static final String TEST_V3_CONFIG_DATA_METADATA_LOCAL_URI =
            "file:///cts_test_V3_telephony_config-metadata.txt";
    private static final String TEST_V4_CONFIG_DATA_CONTENT_LOCAL_URI =
            "file:///cts_test_V4_telephony_config.pb";
    private static final String TEST_V4_CONFIG_DATA_METADATA_LOCAL_URI =
            "file:///cts_test_V4_telephony_config-metadata.txt";
    private static final String TEST_V5_CONFIG_DATA_CONTENT_LOCAL_URI =
            "file:///cts_test_V5_telephony_config.pb";
    private static final String TEST_V5_CONFIG_DATA_METADATA_LOCAL_URI =
            "file:///cts_test_V5_telephony_config-metadata.txt";
    private static final String TEST_V6_CONFIG_DATA_CONTENT_LOCAL_URI =
            "file:///cts_test_V6_telephony_config.pb";
    private static final String TEST_V6_CONFIG_DATA_METADATA_LOCAL_URI =
            "file:///cts_test_V6_telephony_config-metadata.txt";

    private ConnectivityManager mConnectivityManager;
    private SubscriptionManager mSubscriptionManager;
    private TelephonyManager mTelephonyManager;
    private List<ConnectivityManager.NetworkCallback> mCallbacks = new ArrayList<>();

    @BeforeClass
    public static void beforeAllTests() throws Exception {
        if (!MockModemTestBase.beforeAllTestsCheck()) return;
        MockModemTestBase.createMockModemAndConnectToService();
    }

    @AfterClass
    public static void afterAllTests() throws Exception {
        MockModemTestBase.afterAllTestsBase();
    }

    @Before
    public void setUp() throws Exception {
        super.beforeTest();

        mConnectivityManager =
                (ConnectivityManager) getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        mSubscriptionManager =
                (SubscriptionManager)
                        getContext().getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
        mTelephonyManager =
                (TelephonyManager) getContext().getSystemService(Context.TELEPHONY_SERVICE);

        // Skip the tests if ConfigUpdater is not installed
        assumeTrue("CONFIGUPDATER is not installed", isConfigUpdaterInstalled());

        Log.d(TAG, "setUp: Resetting version...");
        runShellCommand("cmd phone override-config-data-version -r");
        runShellCommand("am wait-for-broadcast-idle");

        // Enable history collection for this test suite
        int modemCount = mTelephonyManager.getActiveModemCount();
        for (int i = 0; i < modemCount; i++) {
            sMockModemManager.setSetupDataCallHistoryEnabled(i, true);
        }
        for (int i = 0; i < modemCount; i++) {
            sMockModemManager.clearSetupDataCallHistory(i);
        }
    }

    @After
    public void tearDown() throws Exception {
        Log.d(TAG, "tearDown START");
        for (ConnectivityManager.NetworkCallback callback : mCallbacks) {
            try {
                mConnectivityManager.unregisterNetworkCallback(callback);
            } catch (Exception e) {
                Log.e(TAG, "Failed to unregister callback during tearDown", e);
            }
        }
        mCallbacks.clear();

        // Ensure data is disabled
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                mTelephonyManager, (tm) -> tm.setDataEnabled(false));
        try {
            PollingCheck.check(
                    "Data did not disable during tearDown",
                    15000,
                    () ->
                            !ShellIdentityUtils.invokeMethodWithShellPermissions(
                                    mTelephonyManager, TelephonyManager::isDataEnabled));
        } catch (Exception e) {
            Log.e(TAG, "TearDown: Data disable check failed", e);
        }

        // Remove SIMs and wait for clean state
        sMockModemManager.removeSimCard(0);
        sMockModemManager.removeSimCard(1);
        try {
            PollingCheck.check(
                    "Subscription list did not empty during tearDown",
                    30000,
                    () -> {
                        List<SubscriptionInfo> subList =
                                ShellIdentityUtils.invokeMethodWithShellPermissions(
                                        mSubscriptionManager,
                                        SubscriptionManager::getActiveSubscriptionInfoList);
                        return subList == null || subList.isEmpty();
                    });
        } catch (Exception e) {
            Log.e(TAG, "TearDown: Subscription removal check failed", e);
        }

        int modemCount = mTelephonyManager.getActiveModemCount();
        for (int i = 0; i < modemCount; i++) {
            sMockModemManager.setSetupDataCallHistoryEnabled(i, false);
        }

        // Radio power cycle for HAL reset
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                mTelephonyManager, (tm) -> tm.setRadioPower(false));
        try {
            PollingCheck.check(
                    "Radio did not power off during tearDown",
                    15000,
                    () ->
                            ShellIdentityUtils.invokeMethodWithShellPermissions(
                                            mTelephonyManager, TelephonyManager::getRadioPowerState)
                                    == TelephonyManager.RADIO_POWER_OFF);
        } catch (Exception e) {
            Log.e(TAG, "TearDown: Radio power off check failed", e);
        }
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                mTelephonyManager, (tm) -> tm.setRadioPower(true));

        for (int i = 0; i < modemCount; i++) {
            sMockModemManager.clearSetupDataCallHistory(i);
        }

        runShellCommand("cmd phone override-config-data-version -r");
        runShellCommand("am wait-for-broadcast-idle");
        runShellCommand("am wait-for-broadcast-idle");

        super.afterTest();
        Log.d(TAG, "tearDown END");
    }

    private boolean isConfigUpdaterInstalled() {
        try {
            getContext().getPackageManager().getPackageInfo(CONFIG_UPDATER_PACKAGE, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_TRAFFIC_DESCRIPTOR_CONNECTION_CAPABILITY)
    @Test
    public void testApnRequiredBehavior() throws Exception {
        int slotId = 0;
        byte capability128 = (byte) 128;

        sMockModemManager.insertSimCard(slotId, MOCK_SIM_PROFILE_ID_TWN_CHT);
        sMockModemManager.changeNetworkService(slotId, MOCK_SIM_PROFILE_ID_TWN_CHT, true);
        ensureDataEnabled(slotId);

        NetworkRequest request =
                new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_OEM_PAID)
                        .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                        .build();

        // Case A: ApnRequired = false (V2 Config)
        injectDynamicConfig(
                TEST_V2_CONFIG_DATA_CONTENT_LOCAL_URI,
                TEST_V2_CONFIG_DATA_METADATA_LOCAL_URI,
                true);

        restartData(slotId);
        ConnectivityManager.NetworkCallback callbackA =
                new ConnectivityManager.NetworkCallback() {
                    @Override
                    public void onAvailable(Network network) {}
                };
        mCallbacks.add(callbackA);
        sMockModemManager.clearSetupDataCallHistory(slotId);
        SystemUtil.runWithShellPermissionIdentity(
                () -> {
                    mConnectivityManager.requestNetwork(request, callbackA);
                });

        boolean found128 = waitForSetupDataCall(slotId, capability128, true);
        assertTrue("V2 Config (ApnRequired=false) should allow connection with cap 128", found128);

        // Case B: ApnRequired = true (V6 Config)
        mConnectivityManager.unregisterNetworkCallback(callbackA);
        mCallbacks.remove(callbackA);

        injectDynamicConfig(
                TEST_V6_CONFIG_DATA_CONTENT_LOCAL_URI,
                TEST_V6_CONFIG_DATA_METADATA_LOCAL_URI,
                false);

        restartData(slotId);
        ConnectivityManager.NetworkCallback callbackB =
                new ConnectivityManager.NetworkCallback() {
                    @Override
                    public void onAvailable(Network network) {}
                };
        mCallbacks.add(callbackB);
        sMockModemManager.clearSetupDataCallHistory(slotId);
        SystemUtil.runWithShellPermissionIdentity(
                () -> {
                    mConnectivityManager.requestNetwork(request, callbackB);
                });

        boolean found128Blocked = waitForSetupDataCall(slotId, capability128, true);
        assertTrue(
                "V6 Config (ApnRequired=true) should block connection (cap 128 not found)",
                !found128Blocked);
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_TRAFFIC_DESCRIPTOR_CONNECTION_CAPABILITY)
    @Test
    public void testNewNetworkCapability() throws Exception {
        int slotId = 0;
        byte capabilityMapped = (byte) 128;

        sMockModemManager.insertSimCard(slotId, MOCK_SIM_PROFILE_ID_TWN_CHT);
        sMockModemManager.changeNetworkService(slotId, MOCK_SIM_PROFILE_ID_TWN_CHT, true);
        ensureDataEnabled(slotId);

        NetworkRequest request =
                new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_OEM_PAID)
                        .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                        .build();

        ConnectivityManager.NetworkCallback callback =
                new ConnectivityManager.NetworkCallback() {
                    @Override
                    public void onAvailable(Network network) {}
                };
        mCallbacks.add(callback);

        injectDynamicConfig(
                TEST_V2_CONFIG_DATA_CONTENT_LOCAL_URI,
                TEST_V2_CONFIG_DATA_METADATA_LOCAL_URI,
                true);

        restartData(slotId);
        sMockModemManager.clearSetupDataCallHistory(slotId);
        SystemUtil.runWithShellPermissionIdentity(
                () -> {
                    mConnectivityManager.requestNetwork(request, callback);
                });

        boolean found = waitForSetupDataCall(slotId, capabilityMapped, true);
        assertTrue(
                "Dynamic mapping for OEM_PAID should map to capability "
                        + (capabilityMapped & 0xFF),
                found);

        Log.d(TAG, "Resetting config...");
        mConnectivityManager.unregisterNetworkCallback(callback);
        mCallbacks.remove(callback);
        runShellCommand("cmd phone override-config-data-version -r");
        runShellCommand("am wait-for-broadcast-idle");

        restartData(slotId);
        sMockModemManager.clearSetupDataCallHistory(slotId);

        ConnectivityManager.NetworkCallback callback2 =
                new ConnectivityManager.NetworkCallback() {
                    @Override
                    public void onAvailable(Network network) {}
                };
        mCallbacks.add(callback2);
        SystemUtil.runWithShellPermissionIdentity(
                () -> {
                    mConnectivityManager.requestNetwork(request, callback2);
                });

        boolean foundDefault = waitForSetupDataCall(slotId, capabilityMapped, false);
        assertTrue(
                "Config should be reset (Cap should NOT be " + (capabilityMapped & 0xFF) + ")",
                foundDefault);
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_TRAFFIC_DESCRIPTOR_CONNECTION_CAPABILITY)
    @Test
    public void testDynamicMappingUpdate() throws Exception {
        int slotId = 0;

        sMockModemManager.insertSimCard(slotId, MOCK_SIM_PROFILE_ID_TWN_CHT);
        sMockModemManager.changeNetworkService(slotId, MOCK_SIM_PROFILE_ID_TWN_CHT, true);
        ensureDataEnabled(slotId);

        NetworkRequest request =
                new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_PRIORITIZE_LATENCY)
                        .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                        .build();

        ConnectivityManager.NetworkCallback callback =
                new ConnectivityManager.NetworkCallback() {
                    @Override
                    public void onAvailable(Network network) {}
                };
        mCallbacks.add(callback);

        sMockModemManager.clearSetupDataCallHistory(slotId);
        mConnectivityManager.requestNetwork(request, callback);

        boolean foundBaseline = waitForSetupDataCall(slotId, CAPABILITY_PRIORITIZE_LATENCY, false);
        assertTrue(
                "Baseline SetupDataCall should not have connectionCapability "
                        + (CAPABILITY_PRIORITIZE_LATENCY & 0xFF),
                foundBaseline);

        mConnectivityManager.unregisterNetworkCallback(callback);
        mCallbacks.remove(callback);

        injectDynamicConfig(
                TEST_V1_CONFIG_DATA_CONTENT_LOCAL_URI,
                TEST_V1_CONFIG_DATA_METADATA_LOCAL_URI,
                true);

        restartData(slotId);
        sMockModemManager.clearSetupDataCallHistory(slotId);

        ConnectivityManager.NetworkCallback callback2 =
                new ConnectivityManager.NetworkCallback() {
                    @Override
                    public void onAvailable(Network network) {}
                };
        mCallbacks.add(callback2);
        mConnectivityManager.requestNetwork(request, callback2);

        boolean found = waitForSetupDataCall(slotId, CAPABILITY_PRIORITIZE_LATENCY, true);
        assertTrue(
                "SetupDataCall with connectionCapability "
                        + (CAPABILITY_PRIORITIZE_LATENCY & 0xFF)
                        + " was not called",
                found);

        Log.d(TAG, "Resetting config...");
        mConnectivityManager.unregisterNetworkCallback(callback2);
        mCallbacks.remove(callback2);
        runShellCommand("cmd phone override-config-data-version -r");
        runShellCommand("am wait-for-broadcast-idle");

        restartData(slotId);
        sMockModemManager.clearSetupDataCallHistory(slotId);

        ConnectivityManager.NetworkCallback callback3 =
                new ConnectivityManager.NetworkCallback() {
                    @Override
                    public void onAvailable(Network network) {}
                };
        mCallbacks.add(callback3);
        mConnectivityManager.requestNetwork(request, callback3);

        boolean foundDefault = waitForSetupDataCall(slotId, CAPABILITY_PRIORITIZE_LATENCY, false);
        assertTrue("Config should be reset (Cap should NOT be 128)", foundDefault);
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_TRAFFIC_DESCRIPTOR_CONNECTION_CAPABILITY)
    @Test
    public void testCarrierSpecificMapping() throws Exception {
        int slotId = 0;
        byte capabilityCHT = (byte) 128;
        byte capabilityFET = (byte) 200;

        sMockModemManager.insertSimCard(slotId, MOCK_SIM_PROFILE_ID_TWN_CHT);
        sMockModemManager.changeNetworkService(slotId, MOCK_SIM_PROFILE_ID_TWN_CHT, true);
        ensureDataEnabled(slotId);

        NetworkRequest request =
                new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_PRIORITIZE_LATENCY)
                        .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                        .build();

        injectDynamicConfig(
                TEST_V3_CONFIG_DATA_CONTENT_LOCAL_URI,
                TEST_V3_CONFIG_DATA_METADATA_LOCAL_URI,
                true);

        restartData(slotId);
        sMockModemManager.clearSetupDataCallHistory(slotId);
        ConnectivityManager.NetworkCallback callback =
                new ConnectivityManager.NetworkCallback() {
                    @Override
                    public void onAvailable(Network network) {}
                };
        mCallbacks.add(callback);
        mConnectivityManager.requestNetwork(request, callback);

        boolean foundCHT = waitForSetupDataCall(slotId, capabilityCHT, true);
        assertTrue("CHT SIM should map to capability " + (capabilityCHT & 0xFF), foundCHT);

        Log.d(TAG, "Switching to FET SIM...");
        mConnectivityManager.unregisterNetworkCallback(callback);
        mCallbacks.remove(callback);
        sMockModemManager.changeNetworkService(slotId, MOCK_SIM_PROFILE_ID_TWN_CHT, false);
        sMockModemManager.removeSimCard(slotId);

        PollingCheck.check(
                "Subscription list did not empty during switch",
                20000,
                () -> {
                    List<SubscriptionInfo> subList =
                            ShellIdentityUtils.invokeMethodWithShellPermissions(
                                    mSubscriptionManager,
                                    SubscriptionManager::getActiveSubscriptionInfoList);
                    return subList == null || subList.isEmpty();
                });

        sMockModemManager.insertSimCard(slotId, MOCK_SIM_PROFILE_ID_TWN_FET);
        sMockModemManager.changeNetworkService(slotId, MOCK_SIM_PROFILE_ID_TWN_FET, true);

        ensureDataEnabled(slotId);

        sMockModemManager.clearSetupDataCallHistory(slotId);
        ConnectivityManager.NetworkCallback callback2 =
                new ConnectivityManager.NetworkCallback() {
                    @Override
                    public void onAvailable(Network network) {}
                };
        mCallbacks.add(callback2);
        mConnectivityManager.requestNetwork(request, callback2);

        boolean foundFET = waitForSetupDataCall(slotId, capabilityFET, true);
        assertTrue("FET SIM should map to capability " + (capabilityFET & 0xFF), foundFET);
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_TRAFFIC_DESCRIPTOR_CONNECTION_CAPABILITY)
    @Test
    public void testConfigResetAndRecovery() throws Exception {
        int slotId = 0;
        byte capability128 = (byte) 128;

        sMockModemManager.insertSimCard(slotId, MOCK_SIM_PROFILE_ID_TWN_CHT);
        sMockModemManager.changeNetworkService(slotId, MOCK_SIM_PROFILE_ID_TWN_CHT, true);
        ensureDataEnabled(slotId);

        NetworkRequest request =
                new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_PRIORITIZE_LATENCY)
                        .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                        .build();

        ConnectivityManager.NetworkCallback callback =
                new ConnectivityManager.NetworkCallback() {
                    @Override
                    public void onAvailable(Network network) {}
                };
        mCallbacks.add(callback);

        injectDynamicConfig(
                TEST_V1_CONFIG_DATA_CONTENT_LOCAL_URI,
                TEST_V1_CONFIG_DATA_METADATA_LOCAL_URI,
                true);

        restartData(slotId);
        sMockModemManager.clearSetupDataCallHistory(slotId);
        mConnectivityManager.requestNetwork(request, callback);

        boolean found128 = waitForSetupDataCall(slotId, capability128, true);
        assertTrue("Config should be applied (Cap 128)", found128);

        Log.d(TAG, "Resetting config...");
        mConnectivityManager.unregisterNetworkCallback(callback);
        mCallbacks.remove(callback);
        runShellCommand("cmd phone override-config-data-version -r");
        runShellCommand("am wait-for-broadcast-idle");

        restartData(slotId);
        sMockModemManager.clearSetupDataCallHistory(slotId);

        ConnectivityManager.NetworkCallback callback2 =
                new ConnectivityManager.NetworkCallback() {
                    @Override
                    public void onAvailable(Network network) {}
                };
        mCallbacks.add(callback2);
        mConnectivityManager.requestNetwork(request, callback2);

        boolean foundDefault = waitForSetupDataCall(slotId, capability128, false);
        assertTrue("Config should be reset (Cap should NOT be 128)", foundDefault);
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_TRAFFIC_DESCRIPTOR_CONNECTION_CAPABILITY)
    @Test
    public void testConfigUpdateTriggersReconnect() throws Exception {
        int slotId = 0;
        byte capability128 = (byte) 128;
        byte capability200 = (byte) 200;

        sMockModemManager.insertSimCard(slotId, MOCK_SIM_PROFILE_ID_TWN_CHT);
        sMockModemManager.changeNetworkService(slotId, MOCK_SIM_PROFILE_ID_TWN_CHT, true);
        ensureDataEnabled(slotId);

        NetworkRequest request =
                new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_PRIORITIZE_LATENCY)
                        .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                        .build();

        injectDynamicConfig(
                TEST_V4_CONFIG_DATA_CONTENT_LOCAL_URI,
                TEST_V4_CONFIG_DATA_METADATA_LOCAL_URI,
                true);

        restartData(slotId);
        ConnectivityManager.NetworkCallback callback =
                new ConnectivityManager.NetworkCallback() {
                    @Override
                    public void onAvailable(Network network) {}
                };
        mCallbacks.add(callback);
        sMockModemManager.clearSetupDataCallHistory(slotId);
        SystemUtil.runWithShellPermissionIdentity(
                () -> {
                    mConnectivityManager.requestNetwork(request, callback);
                });

        boolean found128 = waitForSetupDataCall(slotId, capability128, true);
        assertTrue("V4 Config should map to capability " + (capability128 & 0xFF), found128);

        sMockModemManager.clearSetupDataCallHistory(slotId);

        injectDynamicConfig(
                TEST_V5_CONFIG_DATA_CONTENT_LOCAL_URI,
                TEST_V5_CONFIG_DATA_METADATA_LOCAL_URI,
                false);

        boolean found200 = waitForSetupDataCall(slotId, capability200, true);
        assertTrue(
                "V5 Config should automatically trigger reconnect with capability "
                        + (capability200 & 0xFF),
                found200);
    }

    private void ensureDataEnabled(int slotId) throws Exception {
        PollingCheck.check(
                "Failed to enable data",
                30000,
                () -> {
                    SubscriptionInfo info =
                            ShellIdentityUtils.invokeMethodWithShellPermissions(
                                    mSubscriptionManager,
                                    (sm) -> sm.getActiveSubscriptionInfoForSimSlotIndex(slotId));
                    if (info != null) {
                        int subId = info.getSubscriptionId();
                        TelephonyManager tmForSub =
                                mTelephonyManager.createForSubscriptionId(subId);

                        ServiceState state =
                                ShellIdentityUtils.invokeMethodWithShellPermissions(
                                        tmForSub, TelephonyManager::getServiceState);
                        if (state == null || state.getState() != ServiceState.STATE_IN_SERVICE) {
                            return false;
                        }

                        int carrierId =
                                ShellIdentityUtils.invokeMethodWithShellPermissions(
                                        tmForSub, TelephonyManager::getSimCarrierId);
                        if (carrierId == TelephonyManager.UNKNOWN_CARRIER_ID) return false;

                        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                                mSubscriptionManager, (sm) -> sm.setDefaultDataSubId(subId));
                        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                                tmForSub, (tm) -> tm.setDataEnabled(true));
                        return ShellIdentityUtils.invokeMethodWithShellPermissions(
                                tmForSub, TelephonyManager::isDataEnabled);
                    }
                    return false;
                });
    }

    private void injectDynamicConfig(
            String contentUriString, String metadataUriString, boolean shouldResetVersion)
            throws Exception {
        if (shouldResetVersion) {
            Log.d(TAG, "injectDynamicConfig: Resetting version first...");
            runShellCommand("cmd phone override-config-data-version -r");
            runShellCommand("am wait-for-broadcast-idle");
        }

        Uri contentUri = Uri.parse(contentUriString);
        Uri metadataUri = Uri.parse(metadataUriString);

        String command =
                "am broadcast -a "
                        + UPDATE_TELEPHONY_CONFIG_INTENT
                        + " --es CONTENT_URL "
                        + contentUri.toString()
                        + " --es METADATA_URL "
                        + metadataUri.toString()
                        + " -p "
                        + CONFIG_UPDATER_PACKAGE;

        Log.d(TAG, "Sending UPDATE_CONFIG broadcast: " + command);
        runShellCommand(command);
        // Double idle wait to ensure ConfigUpdater processing and subsequent framework updates
        runShellCommand("am wait-for-broadcast-idle");
        runShellCommand("am wait-for-broadcast-idle");
    }

    private boolean waitForSetupDataCall(int slotId, byte targetCapability, boolean shouldEqual)
            throws Exception {
        final boolean[] foundMatch = new boolean[1];
        final boolean[] historySeen = new boolean[1];

        Log.d(
                TAG,
                "waitForSetupDataCall START: targetCap="
                        + (targetCapability & 0xFF)
                        + " shouldEqual="
                        + shouldEqual);
        try {
            PollingCheck.check(
                    "SetupDataCall verify failed",
                    TIMEOUT_MS,
                    () -> {
                        List<DataProfileInfo> history =
                                sMockModemManager.getSetupDataCallHistory(slotId);
                        if (history == null || history.isEmpty()) {
                            return false;
                        }

                        historySeen[0] = true;
                        boolean matchInHistory = false;
                        for (DataProfileInfo dpi : history) {
                            if (dpi.trafficDescriptor != null) {
                                int dpiCap = dpi.trafficDescriptor.connectionCapability;
                                Log.d(
                                        TAG,
                                        "waitForSetupDataCall: Checking history item with cap="
                                                + (dpiCap & 0xFF));
                                if ((dpiCap & 0xFF) == (targetCapability & 0xFF)) {
                                    matchInHistory = true;
                                    break;
                                }
                            }
                        }

                        if (shouldEqual) {
                            foundMatch[0] = matchInHistory;
                            return foundMatch[0];
                        } else {
                            if (matchInHistory) {
                                foundMatch[0] = true;
                                Log.e(
                                        TAG,
                                        "waitForSetupDataCall: FAILURE! Found forbidden match: "
                                                + (targetCapability & 0xFF));
                                return true;
                            }
                            return false;
                        }
                    });
        } catch (AssertionError e) {
            Log.d(TAG, "waitForSetupDataCall: Polling finished (timeout or success)");
        }

        boolean result = shouldEqual ? foundMatch[0] : (historySeen[0] && !foundMatch[0]);
        Log.d(
                TAG,
                "waitForSetupDataCall END: result="
                        + result
                        + " (historySeen="
                        + historySeen[0]
                        + " foundMatch="
                        + foundMatch[0]
                        + ")");
        return result;
    }

    private void restartData(int slotId) throws Exception {
        Log.d(TAG, "restartData: Powering OFF radio...");
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                mTelephonyManager, (tm) -> tm.setRadioPower(false));

        PollingCheck.check(
                "Radio did not power off",
                15000,
                () ->
                        ShellIdentityUtils.invokeMethodWithShellPermissions(
                                        mTelephonyManager, TelephonyManager::getRadioPowerState)
                                == TelephonyManager.RADIO_POWER_OFF);

        Log.d(TAG, "restartData: Powering ON radio...");
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                mTelephonyManager, (tm) -> tm.setRadioPower(true));

        PollingCheck.check(
                "Radio did not power on or service not restored",
                30000,
                () -> {
                    int powerState =
                            ShellIdentityUtils.invokeMethodWithShellPermissions(
                                    mTelephonyManager, TelephonyManager::getRadioPowerState);
                    ServiceState ss =
                            ShellIdentityUtils.invokeMethodWithShellPermissions(
                                    mTelephonyManager, TelephonyManager::getServiceState);
                    return (powerState == TelephonyManager.RADIO_POWER_ON)
                            && (ss != null && ss.getState() == ServiceState.STATE_IN_SERVICE);
                });

        ensureDataEnabled(slotId);
    }
}
