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

package android.telephony.cts.cuj;

import static androidx.test.InstrumentationRegistry.getContext;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeNoException;
import static org.junit.Assume.assumeTrue;

import android.platform.test.annotations.PlatinumTest;
import android.telephony.CellIdentityNr;
import android.telephony.CellInfo;
import android.telephony.CellInfoCdma;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoNr;
import android.telephony.CellInfoTdscdma;
import android.telephony.CellInfoWcdma;
import android.telephony.TelephonyManager;

import androidx.annotation.NonNull;

import com.android.compatibility.common.util.ShellIdentityUtils;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * Data CUJ tests for physical devices with a SIM.
 */
public class DataTest {
    private TelephonyManager mTelephonyManager;

    @Before
    public void setUp() throws Exception {
        mTelephonyManager = getContext().getSystemService(TelephonyManager.class);
        // For some reason PackageManager is always null, but the HAL version check should fail on
        // devices without FEATURE_TELEPHONY anyways.
        // assumeTrue("Skipping test without FEATURE_TELEPHONY.",
        //         getContext().getSystemService(PackageManager.class)
        //                 .hasSystemFeature(PackageManager.FEATURE_TELEPHONY));
        try {
            mTelephonyManager.getHalVersion(TelephonyManager.HAL_SERVICE_DATA);
        } catch (IllegalStateException e) {
            assumeNoException("Skipping test because Telephony service is null.", e);
        }
        assumeTrue("Skipping test because SIM is not present.", TelephonyManager.SIM_STATE_READY
                == (int) ShellIdentityUtils.invokeMethodWithShellPermissions(
                        mTelephonyManager, TelephonyManager::getSimState));
    }

    /**
     * Verify that the device is connected to a network and is voice-, sms-, and data- capable.
     */
    @Test
    @PlatinumTest(focusArea = "telephony")
    public void testBasicPhoneAttributes() {
        assertThat((int) ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, TelephonyManager::getActiveModemCount)).isGreaterThan(0);
        assertThat((String) ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, TelephonyManager::getNetworkOperatorName)).isNotEmpty();
        assertThat((String) ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, TelephonyManager::getLine1Number)).isNotEmpty();
        assertThat((int) ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, TelephonyManager::getDataNetworkType))
                        .isGreaterThan(TelephonyManager.NETWORK_TYPE_UNKNOWN);
    }

    @Test
    @PlatinumTest(focusArea = "telephony")
    public void testCellInfoList() throws Exception {
        List<CellInfo> cellInfos = new ArrayList<>();
        CountDownLatch cdl = new CountDownLatch(1);
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                mTelephonyManager, (tm) -> tm.requestCellInfoUpdate(getContext().getMainExecutor(),
                        new TelephonyManager.CellInfoCallback() {
                            @Override
                            public void onCellInfo(@NonNull List<CellInfo> cellInfo) {
                                if (cellInfo != null) {
                                    cellInfos.addAll(cellInfo);
                                }
                                cdl.countDown();
                            }
                        }));
        cdl.await();
        assertThat(cellInfos.size()).isGreaterThan(0);

        for (CellInfo cellInfo : cellInfos) {
            if (cellInfo instanceof CellInfoGsm gsm) {
                assertThat(gsm.getCellIdentity().getCid()).isNotEqualTo(CellInfo.UNAVAILABLE);
            } else if (cellInfo instanceof CellInfoCdma cdma) {
                assertThat(cdma.getCellIdentity().getBasestationId()).isNotEqualTo(
                        CellInfo.UNAVAILABLE);
            } else if (cellInfo instanceof CellInfoLte lte) {
                assertThat(lte.getCellIdentity().getCi()).isNotEqualTo(
                        CellInfo.UNAVAILABLE);
            } else if (cellInfo instanceof CellInfoWcdma wcdma) {
                assertThat(wcdma.getCellIdentity().getCid()).isNotEqualTo(
                        CellInfo.UNAVAILABLE);
            } else if (cellInfo instanceof CellInfoTdscdma tdscdma) {
                assertThat(tdscdma.getCellIdentity().getCid()).isNotEqualTo(
                        CellInfo.UNAVAILABLE);
            } else if (cellInfo instanceof CellInfoNr nr) {
                assertThat(((CellIdentityNr) nr.getCellIdentity()).getNci())
                        .isNotEqualTo(CellInfo.UNAVAILABLE);
            } else {
                assertThat(cellInfo.getCellSignalStrength().getLevel()).isGreaterThan(0);
            }
        }
    }
}
