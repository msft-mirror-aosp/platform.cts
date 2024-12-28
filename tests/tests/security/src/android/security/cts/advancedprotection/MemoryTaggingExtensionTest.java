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
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.os.SystemProperties;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.security.Flags;

import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@RequiresFlagsEnabled(Flags.FLAG_AAPM_FEATURE_MEMORY_TAGGING_EXTENSION)
public class MemoryTaggingExtensionTest extends BaseAdvancedProtectionTest {
    private static final int TIMEOUT_S = 1;
    private static final String MTE_CONTROL_PROPERTY = "arm64.memtag.bootctl";

    @Override
    @Before
    public void setup() {
        super.setup();
    }

    boolean isAvailable() {
        final String mteDpmSystemProperty =
                "ro.arm64.memtag.bootctl_device_policy_manager";
        final String mteSettingsSystemProperty =
                "ro.arm64.memtag.bootctl_settings_toggle";

        return SystemProperties.getBoolean(mteDpmSystemProperty,
                SystemProperties.getBoolean(mteSettingsSystemProperty, false));
    }

    @ApiTest(apis = {
            "android.security.advancedprotection.AdvancedProtectionManager"
                    + "#getAdvancedProtectionFeatures",
            "android.security.advancedprotection.AdvancedProtectionManager"
                    + "#FEATURE_ID_ENABLE_MTE"
    })
    @Test
    public void testGetFeatures_mteAvailable() {
        assumeTrue(isAvailable());
        assertEquals("The Memory Tagging feature is not in the feature list",
                1,
                mManager.getAdvancedProtectionFeatures()
                        .stream()
                        .filter(feature -> feature.getId().equals(
                                FEATURE_ID_ENABLE_MTE))
                        .count());
    }

    @ApiTest(apis = {
            "android.security.advancedprotection.AdvancedProtectionManager"
                    + "#getAdvancedProtectionFeatures",
            "android.security.advancedprotection.AdvancedProtectionManager"
                    + "#FEATURE_ID_ENABLE_MTE"
    })
    @Test
    public void testGetFeatures_mteUnavailable() {
        assumeFalse(isAvailable());
        assertEquals("The memory tagging feature should not be in the feature list",
                0,
                mManager.getAdvancedProtectionFeatures()
                        .stream()
                        .filter(feature -> feature.getId().equals(
                                FEATURE_ID_ENABLE_MTE))
                        .count());
    }

    @ApiTest(apis = {
            "android.security.advancedprotection.AdvancedProtectionManager"
                    + "#setAdvancedProtectionEnabled"})
    @Test
    public void testEnableProtection() throws InterruptedException {
        assumeTrue(isAvailable());
        mManager.setAdvancedProtectionEnabled(true);
        Thread.sleep(TIMEOUT_S * 1000);
        assertEquals("The MTE system is not enabled",
                "memtag",
                SystemProperties.get(MTE_CONTROL_PROPERTY));
    }

    @ApiTest(apis = {
            "android.security.advancedprotection.AdvancedProtectionManager"
                    + "#setAdvancedProtectionEnabled"})
    @Test
    public void testDisableProtection() throws InterruptedException {
        assumeTrue(isAvailable());
        mManager.setAdvancedProtectionEnabled(false);
        Thread.sleep(TIMEOUT_S * 1000);
        assertEquals("The MTE system is not in default state",
                "default",
                SystemProperties.get(MTE_CONTROL_PROPERTY));
    }
}
