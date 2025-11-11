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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.UserManager;
import android.telephony.TelephonyManager;

import org.junit.Before;

public class DisallowCellular2GTest extends BaseAdvancedProtectionFeatureTest {
    private UserManager mUserManager;
    private PackageManager mPackageManager;
    private TelephonyManager mTelephonyManager;

    @Override
    @Before
    public void setup() {
        super.setup();

        mUserManager = mInstrumentation.getContext().getSystemService(UserManager.class);
        mPackageManager = mInstrumentation.getContext().getPackageManager();
        mTelephonyManager = mInstrumentation.getContext().getSystemService(TelephonyManager.class);

        mInstrumentation
                .getUiAutomation()
                .adoptShellPermissionIdentity(
                        Manifest.permission.QUERY_ADVANCED_PROTECTION_MODE,
                        Manifest.permission.MANAGE_ADVANCED_PROTECTION_MODE,
                        Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
    }

    @Override
    protected int getFeatureId() {
        return FEATURE_ID_DISALLOW_CELLULAR_2G;
    }

    @Override
    protected String getFeatureName() {
        return "Disallow Cellular 2G";
    }

    @Override
    protected boolean isSupportedOnDevice() {
        return mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
                && mTelephonyManager.isRadioInterfaceCapabilitySupported(
                        TelephonyManager.CAPABILITY_USES_ALLOWED_NETWORK_TYPES_BITMASK);
    }

    @Override
    protected void assertFeatureEnabled() {
        assertTrue(
                "The DISALLOW_CELLULAR_2G restriction is not set",
                mUserManager.hasUserRestriction(DISALLOW_CELLULAR_2G));
    }

    @Override
    protected void assertFeatureDisabled() {
        assertFalse(
                "The DISALLOW_CELLULAR_2G restriction is set",
                mUserManager.hasUserRestriction(DISALLOW_CELLULAR_2G));
    }
}
