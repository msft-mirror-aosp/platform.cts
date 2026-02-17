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

package android.security.cts.advancedprotection;

import static android.security.advancedprotection.AdvancedProtectionManager.FEATURE_ID_DISALLOW_INSECURE_WIFI_AUTOJOIN;

import android.platform.test.annotations.RequiresFlagsEnabled;
import android.security.Flags;

@RequiresFlagsEnabled(Flags.FLAG_AAPM_FEATURE_DISABLE_INSECURE_WIFI_AUTOJOIN)
public class DisallowInsecureWifiAutojoinTest extends BaseAdvancedProtectionFeatureTest {
    @Override
    protected int getFeatureId() {
        return FEATURE_ID_DISALLOW_INSECURE_WIFI_AUTOJOIN;
    }

    @Override
    protected String getFeatureName() {
        return "Disallow Insecure WiFi Autojoin";
    }

    @Override
    protected boolean isSupportedOnDevice() {
        // This feature is supported on all devices
        return true;
    }

    @Override
    protected void assertFeatureEnabled() {
        // The changes are in the Wifi mainline module with no APIs exposed,
        // we cannot easily check if the feature is enabled.
    }

    @Override
    protected void assertFeatureDisabled() {
        // The changes are in the Wifi mainline module with no APIs exposed,
        // we cannot easily check if the feature is disabled.
    }
}