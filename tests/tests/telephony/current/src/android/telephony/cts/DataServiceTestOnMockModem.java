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

import android.Manifest;
import android.content.Context;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.telephony.DisconnectCause;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.cts.util.TelephonyUtils;
import android.telephony.mockmodem.MockModemManager;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.internal.telephony.flags.Flags;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

public class DataServiceTestOnMockModem {
    // the timeout to wait for latch countdonw in milliseconds
    private static final int WAIT_LATCH_TIMEOUT_MS = 10000;
    // the timeout to wait for result in milliseconds
    private static final int WAIT_UPDATE_TIMEOUT_MS = 5000;

    private static final int TEST_SLOT = 0;

    private static final int LATCH_SET_USER_DATA_ENABLED = 0;
    private static final int LATCH_SET_USER_DATA_ROAMING_ENABLED = 1;

    private MockModemManager mMockModemManager;
    private TelephonyManager mTelephonyManager;
    private int mTestSub = 0;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void beforeTest() throws Exception {
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.MODIFY_PHONE_STATE);

        MockModemManager.enforceMockModemDeveloperSetting();
        mMockModemManager = new MockModemManager();
        assertNotNull(mMockModemManager);
        assertTrue(mMockModemManager.connectMockModemService(MOCK_SIM_PROFILE_ID_TWN_CHT));

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

        if (mMockModemManager != null) {
            mTelephonyManager.setDataEnabledForReason(
                    TelephonyManager.DATA_ENABLED_REASON_USER, false);
            mTelephonyManager.setDataRoamingEnabled(false);

            TimeUnit.MILLISECONDS.sleep(WAIT_UPDATE_TIMEOUT_MS);

            mMockModemManager.resetDataAllLatchCountdown(TEST_SLOT);
        }
    }

    @After
    public void afterTest() throws Exception {
        if (mMockModemManager != null) {
            mMockModemManager.clearAllCalls(TEST_SLOT, DisconnectCause.POWER_OFF);
        }

        TelephonyUtils.endBlockSuppression(InstrumentationRegistry.getInstrumentation());

        // Rebind all interfaces which is binding to MockModemService to default.
        if (mMockModemManager != null) {
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
        mTelephonyManager.setDataRoamingEnabled(true);
        waitForDataLatchCountdown(LATCH_SET_USER_DATA_ROAMING_ENABLED);
        assertTrue(mMockModemManager.getIsUserDataRoamingEnabled(TEST_SLOT));

        mMockModemManager.resetDataAllLatchCountdown(TEST_SLOT);

        mTelephonyManager.setDataRoamingEnabled(false);
        waitForDataLatchCountdown(LATCH_SET_USER_DATA_ROAMING_ENABLED);
        assertFalse(mMockModemManager.getIsUserDataRoamingEnabled(TEST_SLOT));
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
