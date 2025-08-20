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

package android.net.cts;

import static com.android.net.thread.platform.flags.Flags.FLAG_THREAD_MOBILE_ENABLED;

import static org.junit.Assert.assertEquals;

import android.net.NetworkCapabilities;
import android.net.NetworkSpecifier;
import android.os.Build;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import com.android.testutils.DevSdkIgnoreRule;
import com.android.testutils.DevSdkIgnoreRunner;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DevSdkIgnoreRunner.class)
public final class NetworkSpecifierTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Rule public DevSdkIgnoreRule mIgnoreRule = new DevSdkIgnoreRule();

    private static final class TestNetworkSpecifier extends NetworkSpecifier {}

    @Test
    @RequiresFlagsEnabled(FLAG_THREAD_MOBILE_ENABLED)
    @DevSdkIgnoreRule.IgnoreUpTo(Build.VERSION_CODES.BAKLAVA)
    public void testRedactWithRedactions_defaultAlwaysReturnsThis() {
        final NetworkSpecifier specifier = new TestNetworkSpecifier();

        assertEquals(specifier, specifier.redact(NetworkCapabilities.REDACT_NONE));
        assertEquals(specifier, specifier.redact(NetworkCapabilities.REDACT_ALL));
        assertEquals(specifier, specifier.redact());
    }

    @Test
    @RequiresFlagsEnabled(FLAG_THREAD_MOBILE_ENABLED)
    @DevSdkIgnoreRule.IgnoreUpTo(Build.VERSION_CODES.BAKLAVA)
    public void testGetApplicableRedactions_defaultAlwaysReturnRedactionNone() {
        final NetworkSpecifier specifier = new TestNetworkSpecifier();

        assertEquals(NetworkCapabilities.REDACT_NONE, specifier.getApplicableRedactions());
    }
}
