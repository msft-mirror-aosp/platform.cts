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

package com.android.cts.networksecurityconfig;

import static android.security.net.config.cts.TestUtils.assertCleartextConnectionFails;
import static android.security.net.config.cts.TestUtils.assertCleartextConnectionSucceeds;

import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.security.Flags;
import androidx.test.runner.AndroidJUnit4;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for the deprecation of the {@code usesCleartextTraffic} attribute.
 *
 * <p>These test methods have additional setup and post run checks done host side by {@link
 * com.android.cts.networksecurityconfig.HostsideUsesCleartextTrafficTests}.
 */
@RunWith(AndroidJUnit4.class)
@RequiresFlagsEnabled(Flags.FLAG_DEPRECATE_USES_CLEARTEXT_TRAFFIC2)
public final class DeviceSideUsesCleartextTrafficTests {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    /* Run by HostsideUsesCleartextTrafficTests.testDeprecateUsesCleartextTrafficChangeEnabled */
    @Test
    public void deprecateUsesCleartextTraffic_changeDisabled() throws Exception {
        assertCleartextConnectionSucceeds("android.com");
    }

    /* Run by HostsideUsesCleartextTrafficTests.testDeprecateUsesCleartextTrafficChangeDisabled */
    @Test
    public void deprecateUsesCleartextTraffic_changeEnabled() throws Exception {
        assertCleartextConnectionFails("android.com");
    }
}
