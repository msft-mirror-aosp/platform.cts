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

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.telephony.CellInfo;
import android.telephony.CellSignalStrength;
import android.telephony.TelephonyManager;

import androidx.annotation.NonNull;
import androidx.test.InstrumentationRegistry;

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
    private Context mContext;
    private TelephonyManager mTelephonyManager;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getContext();
        mTelephonyManager = mContext.getSystemService(TelephonyManager.class);
    }

    /**
     * Verify that the device is connected to a network and is voice-, sms-, and data- capable.
     */
    @Test
    public void testBasicPhoneAttributes() throws Exception {
        assertThat((int) ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, TelephonyManager::getActiveModemCount)).isGreaterThan(0);
        assertThat((int) ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, TelephonyManager::getPhoneType))
                        .isEqualTo(TelephonyManager.PHONE_TYPE_GSM);
        assertThat((int) ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, TelephonyManager::getSimState))
                        .isEqualTo(TelephonyManager.SIM_STATE_READY);
        assertThat((String) ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, TelephonyManager::getNetworkOperatorName)).isNotEmpty();
        assertThat((String) ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, TelephonyManager::getLine1Number)).isNotEmpty();
        assertThat((String) ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, TelephonyManager::getVoiceMailNumber)).isNotEmpty();
        assertThat((boolean) ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, TelephonyManager::isNetworkRoaming)).isFalse();
        assertThat((boolean) ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, TelephonyManager::isDeviceSmsCapable)).isTrue();
        assertThat((boolean) ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, TelephonyManager::isDeviceVoiceCapable)).isTrue();
        assertThat((int) ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, TelephonyManager::getDataNetworkType))
                        .isGreaterThan(TelephonyManager.NETWORK_TYPE_UNKNOWN);
        assertThat((int) ShellIdentityUtils.invokeMethodWithShellPermissions(
                mTelephonyManager, TelephonyManager::getVoiceNetworkType))
                        .isGreaterThan(TelephonyManager.NETWORK_TYPE_UNKNOWN);
    }

    @Test
    public void testSignalLevels() throws Exception {
        List<CellInfo> cellInfos = new ArrayList<>();
        CountDownLatch cdl = new CountDownLatch(1);
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                mTelephonyManager, (tm) -> tm.requestCellInfoUpdate(mContext.getMainExecutor(),
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
        CellInfo cellInfo = cellInfos.get(0);
        CellSignalStrength signalStrength = cellInfo.getCellSignalStrength();
        assertThat(signalStrength.getLevel()).isGreaterThan(0);
    }
}
