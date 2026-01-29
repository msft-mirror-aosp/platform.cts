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

package android.settings.cts;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeFalse;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.Flags;
import android.provider.Settings;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests to ensure the Activity to handle {@link Settings#ACTION_VPN_APP_EXCLUSION_SETTINGS} */
@RunWith(AndroidJUnit4.class)
public final class VpnAppExclusionSettingsTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @RequiresFlagsEnabled(Flags.FLAG_EXPOSE_VPN_APP_EXCLUSION_SETTINGS)
    @Test
    public void testVpnAppExclusionSettingsExist() {
        assumeFalse(
                "Skipping test: VPN app exclusion settings are not supported in AAOS",
                SettingsTestUtils.isAutomotive());
        assumeFalse(
                "Skipping test: VPN app exclusion settings are not supported in Wear",
                SettingsTestUtils.isWatch());
        assumeFalse(
                "Skipping test: VPN app exclusion settings are not supported in TV",
                SettingsTestUtils.isTelevision());

        Context targetContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final Intent intent = new Intent(Settings.ACTION_VPN_APP_EXCLUSION_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        final ComponentName componentName =
                intent.resolveActivity(targetContext.getPackageManager());
        assertNotNull(componentName);
        targetContext.startActivity(intent);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }
}
