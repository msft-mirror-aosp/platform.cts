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

package android.telephony.cts;

import static android.telephony.SubscriptionManager.INVALID_SUBSCRIPTION_ID;

import static com.android.internal.telephony.RILConstants.RIL_REQUEST_RADIO_POWER;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.ParcelUuid;
import android.os.SystemProperties;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;
import android.telephony.mockmodem.MockModemManager;
import android.util.Log;

import androidx.test.InstrumentationRegistry;

import com.android.compatibility.common.util.ShellIdentityUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** Base class for test cases performed on MockModem. */
public class MockModemTestBase {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final String TAG = "MockModemTestBase";
    private static final String RESOURCE_PACKAGE_NAME = "android";
    private static final boolean DEBUG_BUILD = !"user".equals(Build.TYPE);
    private static final int TIMEOUT_WAIT_FOR_SIM_STATE_SECONDS = 30;
    private static final int TIMEOUT_WAIT_FOR_SUB_STATE_SECONDS = 30;

    protected static final boolean VDBG = true;

    protected static MockModemManager sMockModemManager;
    protected static TelephonyManager sTelephonyManager;
    protected static SubscriptionManager sSubscriptionManager;
    protected static boolean sIsMultiSimDevice;
    private static final int NUM_SLOTS_PROPERTY = SystemProperties.getInt(
        "ro.telephony.sim_slots.count", -1);
    // This system property is set to 1 for eSIM-only devices.
    // For other devices, the value is not set (returning default -1).
    protected static boolean IS_ESIM_ONLY_DEVICE = NUM_SLOTS_PROPERTY == 1;

    protected static class ActiveDataSubscriptionIdListener extends TelephonyCallback
            implements TelephonyCallback.ActiveDataSubscriptionIdListener {
        private int mExpectedSubId;
        private CountDownLatch mLatch;

        ActiveDataSubscriptionIdListener(int expectedSubId, CountDownLatch latch) {
            this.mExpectedSubId = expectedSubId;
            this.mLatch = latch;
        }

        @Override
        public void onActiveDataSubscriptionIdChanged(int subId) {
            if (mExpectedSubId == subId) {
                mLatch.countDown();
            }
        }
    }

    protected static boolean beforeAllTestsCheck() throws Exception {
        if (VDBG) Log.d(TAG, "beforeAllTests()");

        if (!hasTelephonyFeature()) {
            Log.d(TAG, "Skipping test that requires FEATURE_TELEPHONY");
            return false;
        }
        try {
            MockModemManager.enforceMockModemDeveloperSetting();
        } catch (IllegalStateException ex) {
            if (!DEBUG_BUILD) {
                Log.d(TAG, "Skipping test on user build");
                return false;
            }
            throw ex;
        }
        sTelephonyManager =
                (TelephonyManager) getContext().getSystemService(Context.TELEPHONY_SERVICE);
        sIsMultiSimDevice = isMultiSim(sTelephonyManager);
        sSubscriptionManager = getContext().getSystemService(SubscriptionManager.class);

        return true;
    }

    protected static void createMockModemAndConnectToService() throws Exception {
        sMockModemManager = new MockModemManager();
        assertNotNull(sMockModemManager);
        assertTrue(sMockModemManager.connectMockModemService());
    }

    protected static boolean afterAllTestsBase() throws Exception {
        if (VDBG) Log.d(TAG, "afterAllTestsBase()");

        if (!hasTelephonyFeature() || sTelephonyManager == null) {
            return false;
        }

        // Rebind all interfaces which is binding to MockModemService to default.
        assertNotNull(sMockModemManager);
        // Reset the modified error response of RIL_REQUEST_RADIO_POWER to the original behavior
        // and -1 means to disable the modifed mechanism in mock modem
        sMockModemManager.forceErrorResponse(0, RIL_REQUEST_RADIO_POWER, -1);
        assertTrue(sMockModemManager.disconnectMockModemService());
        sMockModemManager = null;

        return true;
    }

    @Before
    public void beforeTest() throws Exception {
        if (VDBG) Log.d(TAG, "beforeTest");
        assumeTrue("Device does not have TELEPHONY feature", hasTelephonyFeature());
    }

    @After
    public void afterTest() {
        if (VDBG) Log.d(TAG, "afterTest");
    }

    protected static boolean hasTelephonyFeature() {
        final PackageManager pm = getContext().getPackageManager();
        if (!pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            return false;
        }
        return true;
    }

    protected static boolean hasTelephonyFeature(String featureName) {
        final PackageManager pm = getContext().getPackageManager();
        if (!pm.hasSystemFeature(featureName)) {
            return false;
        }
        return true;
    }

    protected static Context getContext() {
        return InstrumentationRegistry.getInstrumentation().getContext();
    }

    protected static boolean isMultiSim(TelephonyManager tm) {
        return tm != null && tm.getPhoneCount() > 1;
    }

    protected static boolean isSimHotSwapCapable() {
        boolean isSimHotSwapCapable = false;
        int resourceId =
                getContext()
                        .getResources()
                        .getIdentifier("config_hotswapCapable", "bool", RESOURCE_PACKAGE_NAME);

        if (resourceId > 0) {
            isSimHotSwapCapable = getContext().getResources().getBoolean(resourceId);
        } else {
            Log.d(TAG, "Fail to get the resource Id, using default.");
        }

        Log.d(TAG, "isSimHotSwapCapable = " + (isSimHotSwapCapable ? "true" : "false"));

        return isSimHotSwapCapable;
    }

    // Return the same MockModemManager object for test case which can't inherit MockModemTestBase
    // but still want to depend on it to simplify the setUp/tearDown processes.
    public static MockModemManager getMockModemManager() {
        return sMockModemManager;
    }

    protected static void waitForSimCardState(int slotIndex, int expectedState) throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        BroadcastReceiver receiver =
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        if (!TelephonyManager.ACTION_SIM_CARD_STATE_CHANGED.equals(
                                intent.getAction())) {
                            return;
                        }
                        if (intent.getIntExtra(SubscriptionManager.EXTRA_SLOT_INDEX, -1)
                                == slotIndex) {
                            int currentState =
                                    intent.getIntExtra(
                                            TelephonyManager.EXTRA_SIM_STATE,
                                            TelephonyManager.SIM_STATE_UNKNOWN);
                            if (currentState == expectedState) {
                                latch.countDown();
                            }
                        }
                    }
                };
        // TODO(b/457756111): Migrate to Bedstead.
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity();
        try {
            if (sTelephonyManager.getSimCardState(sMockModemManager.getSimPhysicalSlotId(slotIndex))
                    == expectedState) {
                return;
            }

            IntentFilter filter = new IntentFilter(TelephonyManager.ACTION_SIM_CARD_STATE_CHANGED);
            try {
                getContext().registerReceiver(receiver, filter);

                if (!latch.await(TIMEOUT_WAIT_FOR_SIM_STATE_SECONDS, TimeUnit.SECONDS)) {
                    fail(
                            "Timeout waiting for SIM state "
                                    + expectedState
                                    + " on slot "
                                    + slotIndex
                                    + ". Current state is "
                                    + sTelephonyManager.getSimCardState(slotIndex));
                }
            } finally {
                getContext().unregisterReceiver(receiver);
            }
        } finally {
            InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }

    protected static int getActiveSubId(int phoneId) {
        // TODO(b/457756111): Migrate to Bedstead.
        int[] allSubs =
                ShellIdentityUtils.invokeMethodWithShellPermissions(
                        sSubscriptionManager, (sm) -> sm.getActiveSubscriptionIdList());
        int sub = SubscriptionManager.getSubscriptionId(phoneId);
        return Arrays.stream(allSubs).anyMatch(e -> e == sub)
                ? sub
                : SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    }

    protected static void waitForActiveDataSub(int expectedSubId) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        ActiveDataSubscriptionIdListener callback =
                new ActiveDataSubscriptionIdListener(expectedSubId, latch);
        // The onActiveDataSubscriptionIdChanged callback is invoked at registration so no need to
        // check current state explicitly.
        sTelephonyManager.registerTelephonyCallback(getContext().getMainExecutor(), callback);
        assertThat(latch.await(TIMEOUT_WAIT_FOR_SUB_STATE_SECONDS, TimeUnit.SECONDS)).isTrue();
        sTelephonyManager.unregisterTelephonyCallback(callback);
    }

    // TODO(b/445733351): Consolidate duplicate implementations into a utility class.
    protected static void waitForSubscriptionCondition(String message, Supplier<Boolean> condition)
            throws Exception {
        if (condition.get()) {
            return;
        }

        final CountDownLatch latch = new CountDownLatch(1);
        SubscriptionManager.OnSubscriptionsChangedListener listener =
                new SubscriptionManager.OnSubscriptionsChangedListener() {
                    @Override
                    public void onSubscriptionsChanged() {
                        if (condition.get()) {
                            latch.countDown();
                        }
                    }
                };

        sSubscriptionManager.addOnSubscriptionsChangedListener(
                getContext().getMainExecutor(), listener);

        try {
            if (!latch.await(TIMEOUT_WAIT_FOR_SUB_STATE_SECONDS, TimeUnit.SECONDS)) {
                fail("Timeout waiting for " + message);
            }
        } finally {
            sSubscriptionManager.removeOnSubscriptionsChangedListener(listener);
        }
    }

    protected static int waitForValidSubscriptionId(int slotIndex) throws Exception {
        final int[] subId = new int[] {INVALID_SUBSCRIPTION_ID};
        waitForSubscriptionCondition(
                "valid subscription ID on slot " + slotIndex,
                () -> {
                    SubscriptionInfo subInfo =
                            sSubscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(
                                    slotIndex);
                    if (subInfo != null) {
                        subId[0] = subInfo.getSubscriptionId();
                        return true;
                    }
                    return false;
                });
        return subId[0];
    }

    protected static int insertSimCard(int simSlotIndex, int simProfileId) throws Exception {
        // TODO(b/457756111): Migrate to Bedstead.
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity();
        try {
            assertThat(sMockModemManager.insertSimCard(simSlotIndex, simProfileId)).isTrue();
            waitForSimCardState(simSlotIndex, TelephonyManager.SIM_STATE_PRESENT);
            return waitForValidSubscriptionId(simSlotIndex);
        } finally {
            InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }

    protected static void removeSimCard(int simSlotIndex) throws Exception {
        if (!sMockModemManager.isSimCardPresent(simSlotIndex)) {
            return;
        }
        // TODO(b/457756111): Migrate to Bedstead.
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity();
        try {
            assertThat(sMockModemManager.removeSimCard(simSlotIndex)).isTrue();
            // TODO(b/454087220): Wait for the SIM slot to become SIM_STATE_ABSENT once this is
            // correctly reported by MockModem.
        } finally {
            InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }

    protected static void setSubAsOpportunistic(int subId, boolean opportunistic) throws Exception {
        // TODO(b/457756111): Migrate to Bedstead.
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity();
        try {
            assertThat(sSubscriptionManager.setOpportunistic(opportunistic, subId)).isTrue();
            waitForSubscriptionCondition(
                    "opportunistic status on sub " + subId,
                    () -> {
                        SubscriptionInfo subInfo =
                                sSubscriptionManager.getActiveSubscriptionInfo(subId);
                        return subInfo != null && subInfo.isOpportunistic() == opportunistic;
                    });
        } finally {
            InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }

    protected static ParcelUuid createSubGroupOf(List<Integer> subIds) throws Exception {
        // TODO(b/457756111): Migrate to Bedstead.
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity();
        try {
            ParcelUuid subGroupUuid = sSubscriptionManager.createSubscriptionGroup(subIds);
            assertThat(subGroupUuid).isNotNull();
            waitForSubscriptionCondition(
                    "subscription group should have sub count: " + subIds.size(),
                    () -> {
                        List<SubscriptionInfo> subInfos =
                                sSubscriptionManager.getSubscriptionsInGroup(subGroupUuid);
                        return subInfos != null && subInfos.size() == subIds.size();
                    });
            List<SubscriptionInfo> subInfos =
                    sSubscriptionManager.getSubscriptionsInGroup(subGroupUuid);
            assertThat(subInfos.size()).isEqualTo(subIds.size());
            for (SubscriptionInfo subInfo : subInfos) {
                assertThat(subInfo.getSubscriptionId()).isIn(subIds);
            }
            return subGroupUuid;
        } finally {
            InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }
}
