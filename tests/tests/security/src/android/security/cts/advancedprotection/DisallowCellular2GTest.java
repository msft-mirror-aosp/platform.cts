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
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.os.UserManager;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.security.Flags;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.ShellIdentityUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

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

    private static boolean isEmbeddedSubscriptionVisible(SubscriptionInfo subInfo) {
        if (subInfo.isEmbedded()
                && (subInfo.getProfileClass() == SubscriptionManager.PROFILE_CLASS_PROVISIONING
                        || (com.android.internal.telephony.flags.Flags.oemEnabledSatelliteFlag()
                                && subInfo.isOnlyNonTerrestrialNetwork()))) {
            return false;
        }

        return true;
    }

    private List<TelephonyManager> getTelephonyManagers() {
        SubscriptionManager subscriptionManager =
                mInstrumentation.getContext().getSystemService(SubscriptionManager.class);
        List<SubscriptionInfo> subscriptions =
                ShellIdentityUtils.invokeMethodWithShellPermissions(
                        subscriptionManager,
                        (sm) -> sm.getActiveSubscriptionInfoList(),
                        Manifest.permission.READ_PRIVILEGED_PHONE_STATE);

        List<TelephonyManager> managers = new ArrayList<>();
        for (SubscriptionInfo info : subscriptions) {
            if (isEmbeddedSubscriptionVisible(info)) {
                managers.add(
                        mInstrumentation
                                .getContext()
                                .getSystemService(TelephonyManager.class)
                                .createForSubscriptionId(info.getSubscriptionId()));
            }
        }

        return managers;
    }

    private boolean isAvailable() {
        for (TelephonyManager telephonyManager : getTelephonyManagers()) {
            boolean hasCellular =
                    ShellIdentityUtils.invokeMethodWithShellPermissions(
                            telephonyManager,
                            (tm) ->
                                    tm.isRadioInterfaceCapabilitySupported(
                                        tm.CAPABILITY_USES_ALLOWED_NETWORK_TYPES_BITMASK)
                                    && tm.isDataCapable(),
                            Manifest.permission.READ_PRIVILEGED_PHONE_STATE);

            if (hasCellular) {
                return true;
            }
        }

        return false;
    }

    private void setAdvancedProtectionModeEnabled(boolean enabled) {
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                mManager,
                (m) -> m.setAdvancedProtectionEnabled(enabled),
                Manifest.permission.MANAGE_ADVANCED_PROTECTION_MODE);
    }

    private long getNumFeatures() {
        return ShellIdentityUtils.invokeMethodWithShellPermissions(
                mManager,
                (m) ->
                        m.getAdvancedProtectionFeatures().stream()
                                .filter(
                                        feature ->
                                                feature.getId()
                                                        .equals(FEATURE_ID_DISALLOW_CELLULAR_2G))
                                .count(),
                Manifest.permission.MANAGE_ADVANCED_PROTECTION_MODE);
    }

    @ApiTest(
            apis = {
                "android.security.advancedprotection.AdvancedProtectionManager"
                        + "#getAdvancedProtectionFeatures",
                "android.security.advancedprotection.AdvancedProtectionManager"
                        + "#FEATURE_ID_DISALLOW_CELLULAR_2G"
            })
    @Test
    public void testGetFeatures_cellularAvailable() {
        assumeTrue(isAvailable());

        assertEquals(
                "The Disallow Cellular 2G feature is not in the feature list", 1, getNumFeatures());
    }

    @ApiTest(
            apis = {
                "android.security.advancedprotection.AdvancedProtectionManager"
                        + "#getAdvancedProtectionFeatures",
                "android.security.advancedprotection.AdvancedProtectionManager"
                        + "#FEATURE_ID_DISALLOW_CELLULAR_2G"
            })
    @Test
    public void testGetFeatures_cellularUnavailable() {
        assumeFalse(isAvailable());

        assertEquals(
                "The Disallow Cellular 2G feature should not be in the feature list",
                0,
                getNumFeatures());
    }

    @ApiTest(
            apis = {
                "android.security.advancedprotection.AdvancedProtectionManager"
                        + "#setAdvancedProtectionEnabled"
            })
    @Test
    public void testEnableProtection() throws InterruptedException {
        assumeTrue(isAvailable());

        setAdvancedProtectionModeEnabled(true);

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
        assumeTrue(isAvailable());

        setAdvancedProtectionModeEnabled(false);

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
        assumeTrue(isAvailable());

        setAdvancedProtectionModeEnabled(true);
        Thread.sleep(TIMEOUT_S * 1000);
        setAdvancedProtectionModeEnabled(false);
        Thread.sleep(TIMEOUT_S * 1000);

        for (TelephonyManager telephonyManager : getTelephonyManagers()) {
            long allowedTypes =
                    ShellIdentityUtils.invokeMethodWithShellPermissions(
                            telephonyManager,
                            (tm) ->
                                    tm.getAllowedNetworkTypesForReason(
                                            TelephonyManager
                                                    .ALLOWED_NETWORK_TYPES_REASON_ENABLE_2G),
                            Manifest.permission.READ_PRIVILEGED_PHONE_STATE);

            assertTrue(
                    "2G networking is still enabled after advanced protection is disabled",
                    (allowedTypes & TelephonyManager.NETWORK_CLASS_BITMASK_2G) == 0);
        }
    }
}
