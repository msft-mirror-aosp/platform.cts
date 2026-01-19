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

import static android.telephony.SubscriptionManager.DEFAULT_SUBSCRIPTION_ID;
import static android.telephony.SubscriptionManager.INVALID_SUBSCRIPTION_ID;
import static android.telephony.TelephonyManager.SET_OPPORTUNISTIC_SUB_REMOTE_SERVICE_EXCEPTION;
import static android.telephony.TelephonyManager.SET_OPPORTUNISTIC_SUB_SUCCESS;
import static android.telephony.mockmodem.MockSimService.MOCK_SIM_PROFILE_ID_TWN_FET;
import static android.telephony.mockmodem.MockSimService.MOCK_SIM_PROFILE_ID_US_FI;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;

import android.hardware.radio.V1_5.Domain;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.ParcelUuid;
import android.util.Log;

import androidx.test.InstrumentationRegistry;

import com.android.compatibility.common.util.CarrierPrivilegeUtils;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

// TODO(b/458347303): Generalize this test base to handle different SIM/profile configurations.
public class OpportunisticMockModemTestBase extends MockModemTestBase {
    protected static final int PRIMARY_SIM_SLOT_INDEX = 0;
    protected static final int OPPT_SIM_SLOT_INDEX = 1;
    protected static final long TIMEOUT_SET_PREFERRED_OPPT_SUB_SECONDS = 30;
    protected static int sPrimarySubId;
    protected static int sOpptSubId;
    protected static ParcelUuid sSubGroupUuid;
    protected HandlerThread mHandlerThread;
    protected Handler mHandler;
    protected static boolean sShouldTearDownSims = false;
    protected static final String TAG = "OpptMockModemTestBase";

    @BeforeClass
    public static void beforeAllTests() throws Exception {
        if (!MockModemTestBase.beforeAllTestsCheck()) return;
        // These must be true to perform the following SIM operations, but cannot be checked by
        // assumeTrue at this stage. They are checked in beforeTest and afterAllTests to skip
        // closing mockModem since it is not created during test setup.
        if (!shouldTestOpportunisticSubscription()) return;

        MockModemTestBase.createMockModemAndConnectToService();
        sShouldTearDownSims = true;
        removeSimCard(PRIMARY_SIM_SLOT_INDEX);
        removeSimCard(OPPT_SIM_SLOT_INDEX);
        sPrimarySubId = insertSimCard(PRIMARY_SIM_SLOT_INDEX, MOCK_SIM_PROFILE_ID_US_FI);
        sOpptSubId = insertSimCard(OPPT_SIM_SLOT_INDEX, MOCK_SIM_PROFILE_ID_TWN_FET);
        sSubGroupUuid = createSubGroupOf(List.of(sPrimarySubId, sOpptSubId));
        setSubAsOpportunistic(sOpptSubId, true);
    }

    @AfterClass
    public static void afterAllTests() throws Exception {
        // Skip close MockModem for following condition as the MockModem would not be created in
        // beforeTest.
        if (!shouldTestOpportunisticSubscription()) return;

        if (sShouldTearDownSims) {
            // Get ICCID of subs before removing the SIM cards.
            setSubAsOpportunistic(sOpptSubId, false);
            String primarySubIccid = "";
            String opptSubIccid = "";
            // TODO(b/457756111): Migrate to Bedstead.
            InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .adoptShellPermissionIdentity();
            try {
                primarySubIccid =
                    sSubscriptionManager.getActiveSubscriptionInfo(sPrimarySubId).getIccId();
                opptSubIccid =
                        sSubscriptionManager.getActiveSubscriptionInfo(sOpptSubId).getIccId();
            } finally {
                InstrumentationRegistry.getInstrumentation()
                        .getUiAutomation()
                        .dropShellPermissionIdentity();
            }

            removeSimCard(OPPT_SIM_SLOT_INDEX);
            removeSimCard(PRIMARY_SIM_SLOT_INDEX);

            // Remove SIMs from database, otherwise changes to test setup may not take.
            // TODO(b/457756111): Migrate to Bedstead.
            InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .adoptShellPermissionIdentity();
            try {
                sSubscriptionManager.removeSubscriptionInfoRecord(primarySubIccid, 0);
                sSubscriptionManager.removeSubscriptionInfoRecord(opptSubIccid, 0);
            } finally {
                InstrumentationRegistry.getInstrumentation()
                        .getUiAutomation()
                        .dropShellPermissionIdentity();
            }
        }

        MockModemTestBase.afterAllTestsBase();
    }

    @Before
    public void beforeTest() throws Exception {
        super.beforeTest();
        assumeTrue("Skip tests on non multi-SIM devices", sIsMultiSimDevice);
        assumeTrue("Skip tests because SIM is not hot-swap capable", isSimHotSwapCapable());

        mHandlerThread = new HandlerThread("PreferredOpptSubTest");
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());
        // Reset the opportunistic sub to be the default.
        setPreferredOpptDataSubWithShellPermissions(
                DEFAULT_SUBSCRIPTION_ID, false, SET_OPPORTUNISTIC_SUB_SUCCESS);
    }

    @After
    public void afterTest() {
        super.afterTest();
        if (mHandlerThread != null) {
            mHandlerThread.quitSafely();
            mHandlerThread = null;
            mHandler = null;
        }
    }

    protected int getPreferredOpptDataSubWithShellPermissions() {
        // TODO(b/457756111): Migrate to Bedstead.
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity();
        try {
            return sTelephonyManager.getPreferredOpportunisticDataSubscription();
        } finally {
            InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }

    protected int getPreferredOpptDataSubWithCarrierPrivileges() throws Exception {
        final int[] preferredOpptSub = new int[] {INVALID_SUBSCRIPTION_ID};
        CarrierPrivilegeUtils.withCarrierPrivileges(
                getContext(),
                sOpptSubId,
                () ->
                        preferredOpptSub[0] =
                                sTelephonyManager.getPreferredOpportunisticDataSubscription());
        return preferredOpptSub[0];
    }

    protected void enableOpptSubNetworkService(boolean inService) throws Exception {
        assertThat(
                        sMockModemManager.changeNetworkService(
                                OPPT_SIM_SLOT_INDEX,
                                MOCK_SIM_PROFILE_ID_TWN_FET,
                                inService,
                                Domain.PS,
                                /* regFailCause= */ 0))
                .isTrue();
    }

    protected void setPreferredOpptDataSubWithShellPermissions(
            int subId, boolean needsValidation, int expectedResult) throws Exception {
        final int[] setOpptResult = new int[] {SET_OPPORTUNISTIC_SUB_REMOTE_SERVICE_EXCEPTION};
        CountDownLatch latch = new CountDownLatch(1);
        // TODO(b/457756111): Migrate to Bedstead.
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity();
        try {
            sTelephonyManager.setPreferredOpportunisticDataSubscription(
                    subId,
                    needsValidation,
                    mHandler::post,
                    new Consumer<Integer>() {
                        @Override
                        public void accept(Integer result) {
                            Log.d(
                                    TAG,
                                    "setPreferredOpportunisticDataSubscription for sub "
                                            + subId
                                            + " and returns "
                                            + result);
                            setOpptResult[0] = result;
                            latch.countDown();
                        }
                    });
            assertThat(latch.await(TIMEOUT_SET_PREFERRED_OPPT_SUB_SECONDS, TimeUnit.SECONDS))
                    .isTrue();
            assertThat(setOpptResult[0]).isEqualTo(expectedResult);
        } finally {
            InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }

    private static boolean shouldTestOpportunisticSubscription() {
        return (sIsMultiSimDevice && isSimHotSwapCapable());
    }
}
