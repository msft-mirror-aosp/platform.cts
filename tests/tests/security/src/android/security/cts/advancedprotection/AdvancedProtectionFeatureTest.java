/*
 * Copyright (C) 2026 The Android Open Source Project
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.os.Parcel;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.security.Flags;
import android.security.advancedprotection.AdvancedProtectionFeature;

import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class AdvancedProtectionFeatureTest {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final int FEATURE_ID = 1;

    @RequiresFlagsEnabled(Flags.FLAG_AAPM_API_V2)
    @ApiTest(apis = {"android.security.advancedprotection.AdvancedProtectionFeature#AdvancedProtectionFeature"})
    @Test
    public void testConstructor_deprecated() {
        AdvancedProtectionFeature feature = new AdvancedProtectionFeature(FEATURE_ID);

        assertEquals(FEATURE_ID, feature.getId());
        assertTrue(feature.isEnabled());
        assertEquals(AdvancedProtectionFeature.PROVISIONING_MODE_PROVISIONED_BY_DEFAULT, feature.getProvisioningMode());
        assertTrue(feature.isProvisioned());
    }

    @RequiresFlagsEnabled(Flags.FLAG_AAPM_API_V2)
    @ApiTest(apis = {"android.security.advancedprotection.AdvancedProtectionFeature#AdvancedProtectionFeature"})
    @Test
    public void testConstructor() {
        AdvancedProtectionFeature feature = new AdvancedProtectionFeature(
                FEATURE_ID,
                /* enabled= */ false,
                AdvancedProtectionFeature.PROVISIONING_MODE_PROVISIONED_BY_FEATURE_ADMIN);

        assertEquals(FEATURE_ID, feature.getId());
        assertFalse(feature.isEnabled());
        assertEquals(AdvancedProtectionFeature.PROVISIONING_MODE_PROVISIONED_BY_FEATURE_ADMIN, feature.getProvisioningMode());
        assertTrue(feature.isProvisioned());
    }

    @RequiresFlagsEnabled(Flags.FLAG_AAPM_API_V2)
    @ApiTest(apis = {"android.security.advancedprotection.AdvancedProtectionFeature#isProvisioned"})
    @Test
    public void testIsProvisioned() {
        assertTrue(new AdvancedProtectionFeature(FEATURE_ID, true,
                AdvancedProtectionFeature.PROVISIONING_MODE_PROVISIONED_BY_DEFAULT).isProvisioned());
        assertTrue(new AdvancedProtectionFeature(FEATURE_ID, true,
                AdvancedProtectionFeature.PROVISIONING_MODE_PROVISIONED_BY_FEATURE_ADMIN).isProvisioned());
        assertTrue(new AdvancedProtectionFeature(FEATURE_ID, true,
                AdvancedProtectionFeature.PROVISIONING_MODE_PROVISIONED_BY_ADB).isProvisioned());

        assertFalse(new AdvancedProtectionFeature(FEATURE_ID, true,
                AdvancedProtectionFeature.PROVISIONING_MODE_DEPROVISIONED_BY_DEFAULT).isProvisioned());
        assertFalse(new AdvancedProtectionFeature(FEATURE_ID, true,
                AdvancedProtectionFeature.PROVISIONING_MODE_DEPROVISIONED_BY_FEATURE_ADMIN).isProvisioned());
        assertFalse(new AdvancedProtectionFeature(FEATURE_ID, true,
                AdvancedProtectionFeature.PROVISIONING_MODE_DEPROVISIONED_BY_ADB).isProvisioned());
    }

    @RequiresFlagsEnabled(Flags.FLAG_AAPM_API_V2)
    @ApiTest(apis = {
            "android.security.advancedprotection.AdvancedProtectionFeature#writeToParcel",
            "android.security.advancedprotection.AdvancedProtectionFeature#CREATOR"
    })
    @Test
    public void testParcelable() {
        AdvancedProtectionFeature feature = new AdvancedProtectionFeature(
                FEATURE_ID,
                /* enabled= */ false,
                AdvancedProtectionFeature.PROVISIONING_MODE_PROVISIONED_BY_FEATURE_ADMIN);

        Parcel parcel = Parcel.obtain();
        feature.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        AdvancedProtectionFeature createdFeature =
                AdvancedProtectionFeature.CREATOR.createFromParcel(parcel);

        assertEquals(FEATURE_ID, createdFeature.getId());
        assertFalse(createdFeature.isEnabled());
        assertEquals(AdvancedProtectionFeature.PROVISIONING_MODE_PROVISIONED_BY_FEATURE_ADMIN, createdFeature.getProvisioningMode());
        assertTrue(createdFeature.isProvisioned());

        parcel.recycle();
    }
}
