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

import static android.security.advancedprotection.AdvancedProtectionManager.FEATURE_ID_ENABLE_MTE;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.admin.DevicePolicyManager;
import android.os.SystemProperties;

public class MemoryTaggingExtensionTest extends BaseAdvancedProtectionFeatureTest {
    private static final String MTE_CONTROL_PROPERTY = "arm64.memtag.bootctl";

    @Override
    protected int getFeatureId() {
        return FEATURE_ID_ENABLE_MTE;
    }

    @Override
    protected String getFeatureName() {
        return "Memory Tagging";
    }

    @Override
    protected boolean isSupportedOnDevice() {
        final String mteDpmSystemProperty = "ro.arm64.memtag.bootctl_device_policy_manager";
        final String mteSettingsSystemProperty = "ro.arm64.memtag.bootctl_settings_toggle";

        return SystemProperties.getBoolean(
                mteDpmSystemProperty,
                SystemProperties.getBoolean(mteSettingsSystemProperty, false));
    }

    @Override
    protected void assertFeatureEnabled() {
        assertEquals(
                "The MTE system is not enabled",
                "memtag",
                SystemProperties.get(MTE_CONTROL_PROPERTY));
        assertEquals(
                "The DevicePolicyManager did not return correct value",
                DevicePolicyManager.MTE_ENABLED,
                getMtePolicyFromDpm());
    }

    @Override
    protected void assertFeatureDisabled() {
        String mtePropertyValue = SystemProperties.get(MTE_CONTROL_PROPERTY);
        // "default" is the value that should be set by the DevicePolicyManager, but
        // "none" is also valid since that's the default value on the device.
        boolean mteIsDefaultOrNone =
                mtePropertyValue.equals("default") || mtePropertyValue.equals("none");
        assertTrue(
                "The MTE system is not in default state: " + mtePropertyValue, mteIsDefaultOrNone);
        assertEquals(
                "The DevicePolicyManager did not return correct value",
                DevicePolicyManager.MTE_NOT_CONTROLLED_BY_POLICY,
                getMtePolicyFromDpm());
    }

    private int getMtePolicyFromDpm() {
        return mInstrumentation
                .getContext()
                .getSystemService(DevicePolicyManager.class)
                .getMtePolicy();
    }
}
