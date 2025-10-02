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
import static android.telephony.TelephonyManager.SET_OPPORTUNISTIC_SUB_INACTIVE_SUBSCRIPTION;
import static android.telephony.TelephonyManager.SET_OPPORTUNISTIC_SUB_SUCCESS;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Ignore;
import org.junit.Test;

public class OpportunisticNetworkServiceTestOnMockModem extends OpportunisticMockModemTestBase {
    private static final String TAG = "ONSTestOnMockModem";

    @Test
    @ApiTest(apis = "android.telephony.TelephonyManager#getPreferredOpportunisticDataSubscription")
    public void testGetPreferredOpptSub_asSystemApp_isPrimarySubByDefault() throws Throwable {
        assertThat(getPreferredOpptDataSubWithShellPermissions())
                .isEqualTo(DEFAULT_SUBSCRIPTION_ID);
    }

    @Test
    @ApiTest(apis = "android.telephony.TelephonyManager#getPreferredOpportunisticDataSubscription")
    public void testGetPreferredOpptSub_asCarrierApp_isPrimarySubByDefault() throws Exception {
        assertThat(getPreferredOpptDataSubWithCarrierPrivileges())
                .isEqualTo(DEFAULT_SUBSCRIPTION_ID);
    }

    @Test
    @ApiTest(
            apis = {
                "android.telephony.TelephonyManager#getPreferredOpportunisticDataSubscription",
                "android.telephony.TelephonyManager#setPreferredOpportunisticDataSubscription"
            })
    public void testSetPreferredOpptSub_asSystemApp_toInServiceOpptSub_withoutValidation()
            throws Throwable {
        assertThat(getPreferredOpptDataSubWithShellPermissions())
                .isEqualTo(DEFAULT_SUBSCRIPTION_ID);
        waitForActiveDataSub(sPrimarySubId);
        setPreferredOpptDataSubWithShellPermissions(
                sOpptSubId, false, SET_OPPORTUNISTIC_SUB_SUCCESS);
        assertThat(getPreferredOpptDataSubWithShellPermissions()).isEqualTo(sOpptSubId);
        waitForActiveDataSub(sOpptSubId);
    }

    @Test
    @ApiTest(
            apis = {
                "android.telephony.TelephonyManager#getPreferredOpportunisticDataSubscription",
                "android.telephony.TelephonyManager#setPreferredOpportunisticDataSubscription"
            })
    public void testSetPreferredOpptSub_asSystemApp_toInServiceOpptSub_withValidation()
            throws Throwable {
        assertThat(getPreferredOpptDataSubWithShellPermissions())
                .isEqualTo(DEFAULT_SUBSCRIPTION_ID);
        waitForActiveDataSub(sPrimarySubId);
        setPreferredOpptDataSubWithShellPermissions(
                sOpptSubId, true, SET_OPPORTUNISTIC_SUB_SUCCESS);
        assertThat(getPreferredOpptDataSubWithShellPermissions()).isEqualTo(sOpptSubId);
        waitForActiveDataSub(sOpptSubId);
    }

    @Test
    @ApiTest(
            apis = {
                "android.telephony.TelephonyManager#getPreferredOpportunisticDataSubscription",
                "android.telephony.TelephonyManager#setPreferredOpportunisticDataSubscription"
            })
    public void testSetPreferredOpptSub_asSystemApp_toOutOfServiceOpptSub_withoutValidation()
            throws Throwable {
        assertThat(getPreferredOpptDataSubWithShellPermissions())
                .isEqualTo(DEFAULT_SUBSCRIPTION_ID);
        waitForActiveDataSub(sPrimarySubId);
        enableOpptSubNetworkService(false);
        setPreferredOpptDataSubWithShellPermissions(
                sOpptSubId, false, SET_OPPORTUNISTIC_SUB_SUCCESS);
        assertThat(getPreferredOpptDataSubWithShellPermissions()).isEqualTo(sOpptSubId);
        waitForActiveDataSub(sOpptSubId);
    }

    @Ignore("Ignored due to lack of support for network invalidation (b/457792035).")
    @Test
    @ApiTest(
            apis = {
                "android.telephony.TelephonyManager#getPreferredOpportunisticDataSubscription",
                "android.telephony.TelephonyManager#setPreferredOpportunisticDataSubscription"
            })
    public void testSetPreferredOpptSub_asSystemApp_toOutOfServiceOpptSub_withValidation()
            throws Throwable {
        assertThat(getPreferredOpptDataSubWithShellPermissions())
                .isEqualTo(DEFAULT_SUBSCRIPTION_ID);
        waitForActiveDataSub(sPrimarySubId);
        enableOpptSubNetworkService(false);
        setPreferredOpptDataSubWithShellPermissions(
                sOpptSubId, true, SET_OPPORTUNISTIC_SUB_INACTIVE_SUBSCRIPTION);
        assertThat(getPreferredOpptDataSubWithShellPermissions()).isEqualTo(sPrimarySubId);
        waitForActiveDataSub(sPrimarySubId);
    }

    @Test
    @ApiTest(
            apis = {
                "android.telephony.TelephonyManager#getPreferredOpportunisticDataSubscription",
                "android.telephony.TelephonyManager#setPreferredOpportunisticDataSubscription"
            })
    public void testSetPreferredOpptSub_asSystemApp_resetToDefault() throws Throwable {
        assertThat(getPreferredOpptDataSubWithShellPermissions())
                .isEqualTo(DEFAULT_SUBSCRIPTION_ID);
        waitForActiveDataSub(sPrimarySubId);
        setPreferredOpptDataSubWithShellPermissions(
                sOpptSubId, false, SET_OPPORTUNISTIC_SUB_SUCCESS);
        assertThat(getPreferredOpptDataSubWithShellPermissions()).isEqualTo(sOpptSubId);
        waitForActiveDataSub(sOpptSubId);
        setPreferredOpptDataSubWithShellPermissions(
                DEFAULT_SUBSCRIPTION_ID, false, SET_OPPORTUNISTIC_SUB_SUCCESS);
        assertThat(getPreferredOpptDataSubWithShellPermissions())
                .isEqualTo(DEFAULT_SUBSCRIPTION_ID);
        waitForActiveDataSub(sPrimarySubId);
    }

    @Ignore("Ignored due to b/454690834 causing SecurityException to not be thrown.")
    @Test
    @ApiTest(apis = "android.telephony.TelephonyManager#getPreferredOpportunisticDataSubscription")
    public void testGetPreferredOpptDataSubscription_withoutPermission_shouldThrow() {
        assertThrows(
                SecurityException.class,
                () -> sTelephonyManager.getPreferredOpportunisticDataSubscription());
    }

    @Test
    @ApiTest(apis = "android.telephony.TelephonyManager#setPreferredOpportunisticDataSubscription")
    public void testSetPreferredOpptDataSubscription_withoutPermission_shouldThrow() {
        assertThrows(
                SecurityException.class,
                () ->
                        sTelephonyManager.setPreferredOpportunisticDataSubscription(
                                DEFAULT_SUBSCRIPTION_ID, false, Runnable::run, result -> {}));
    }
}
