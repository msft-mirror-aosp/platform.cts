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

package android.nfc.cts;

import static android.nfc.cts.DefaultPaymentProviderTestUtils.CTS_MY_HOSTAPDU_SERVICE;
import static android.nfc.cts.DefaultPaymentProviderTestUtils.runWithDefaultPaymentSetting;
import static android.nfc.cts.WalletRoleTestUtils.CTS_PACKAGE_NAME;
import static android.nfc.cts.WalletRoleTestUtils.canAssignRoleToPackage;
import static android.nfc.cts.WalletRoleTestUtils.clearRoleHolders;
import static android.nfc.cts.WalletRoleTestUtils.getDefaultWalletRoleHolder;
import static android.nfc.cts.WalletRoleTestUtils.getOverLayDefaultHolder;

import android.content.Context;
import android.content.pm.PackageManager;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.InstrumentationRegistry;


import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.testng.Assert;

@RunWith(JUnit4.class)
@RequiresFlagsEnabled(android.permission.flags.Flags.FLAG_WALLET_ROLE_ENABLED)
public class WalletRoleTest {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    Context mContext;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getContext();
        Assume.assumeTrue(supportsHardware());
    }

    private boolean supportsHardware() {
        final PackageManager pm = mContext.getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_NFC);
    }

    @Test
    public void testMigrationFromOverlay() {
        String overlayConfig = getOverLayDefaultHolder(mContext);
        Assume.assumeNotNull(overlayConfig);
        Assume.assumeTrue(canAssignRoleToPackage(mContext, overlayConfig));
        runWithDefaultPaymentSetting(mContext,
                null,
                () -> {
                    clearRoleHolders(mContext);
                    String currentHolder = getDefaultWalletRoleHolder(mContext);

                    Assert.assertEquals(currentHolder, overlayConfig);
                });
    }

    @Test
    public void testMigrationFromDefaultPaymentProvider() {
        runWithDefaultPaymentSetting(mContext,
                CTS_MY_HOSTAPDU_SERVICE,
                () -> {
                    clearRoleHolders(mContext);
                    String currentHolder = getDefaultWalletRoleHolder(mContext);

                    Assert.assertEquals(currentHolder, CTS_PACKAGE_NAME);
                });
    }

}
