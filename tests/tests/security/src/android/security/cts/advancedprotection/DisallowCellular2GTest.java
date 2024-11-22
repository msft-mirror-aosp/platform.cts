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

package android.security.cts.advancedprotection;

import static android.os.UserManager.DISALLOW_CELLULAR_2G;
import static android.security.advancedprotection.AdvancedProtectionManager.FEATURE_ID_DISALLOW_CELLULAR_2G;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.os.UserManager;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.security.Flags;
import android.telephony.TelephonyManager;

import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.ShellIdentityUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@RequiresFlagsEnabled(Flags.FLAG_AAPM_FEATURE_DISABLE_CELLULAR_2G)
public class DisallowCellular2GTest extends BaseAdvancedProtectionTest {
    // TODO(b/369361373): Replace sleep with a callback to ensure the restriction is set by the time
    //  we check it.
    private static final int TIMEOUT_S = 1;
    private UserManager mUserManager;

    @Override
    @Before
    public void setup() {
        super.setup();
        mUserManager = mInstrumentation.getContext().getSystemService(UserManager.class);
    }

    @ApiTest(
            apis = {
                "android.security.advancedprotection.AdvancedProtectionManager"
                        + "#getAdvancedProtectionFeatures",
                "android.security.advancedprotection.AdvancedProtectionManager"
                        + "#FEATURE_ID_DISALLOW_CELLULAR_2G"
            })
    @Test
    public void testGetFeatures() {
        assertEquals(
                "The Disallow Ceullar 2G feature is not in the feature list",
                1,
                mManager.getAdvancedProtectionFeatures().stream()
                        .filter(feature -> feature.getId().equals(FEATURE_ID_DISALLOW_CELLULAR_2G))
                        .count());
    }

    @ApiTest(
            apis = {
                "android.security.advancedprotection.AdvancedProtectionManager"
                        + "#setAdvancedProtectionEnabled"
            })
    @Test
    public void testEnableProtection() throws InterruptedException {
        mManager.setAdvancedProtectionEnabled(true);
        Thread.sleep(TIMEOUT_S * 1000);
        assertTrue(
                "The DISALLOW_CELLULAR_2G restriction is not set",
                mUserManager.hasUserRestriction(DISALLOW_CELLULAR_2G));
    }

    @ApiTest(
            apis = {
                "android.security.advancedprotection.AdvancedProtectionManager"
                        + "#setAdvancedProtectionEnabled"
            })
    @Test
    public void testDisableProtection() throws InterruptedException {
        mManager.setAdvancedProtectionEnabled(false);
        Thread.sleep(TIMEOUT_S * 1000);
        assertFalse(
                "The DISALLOW_CELLULAR_2G restriction is set",
                mUserManager.hasUserRestriction(DISALLOW_CELLULAR_2G));
    }

    @ApiTest(
            apis = {
                "android.security.advancedprotection.AdvancedProtectionManager"
                        + "#setAdvancedProtectionEnabled"
            })
    @Test
    public void testStateAfterToggle() throws InterruptedException {
        mManager.setAdvancedProtectionEnabled(true);
        Thread.sleep(TIMEOUT_S * 1000);
        mManager.setAdvancedProtectionEnabled(false);
        Thread.sleep(TIMEOUT_S * 1000);

        TelephonyManager telephonyManager =
                mInstrumentation.getContext().getSystemService(TelephonyManager.class);
        long allowedTypes =
                ShellIdentityUtils.invokeMethodWithShellPermissions(
                        telephonyManager,
                        (tm) ->
                                tm.getAllowedNetworkTypesForReason(
                                        TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_ENABLE_2G),
                        Manifest.permission.READ_PRIVILEGED_PHONE_STATE);

        assertTrue(
                "2G networking is still enabled after advanced protection is disabled",
                (allowedTypes & TelephonyManager.NETWORK_CLASS_BITMASK_2G) == 0);
    }
}
