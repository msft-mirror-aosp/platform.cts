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

import android.compat.cts.CompatChangeGatingTestCase;
import com.google.common.collect.ImmutableSet;

/** Tests for the deprecation of the {@code usesCleartextTraffic} attribute. */
public class HostsideUsesCleartextTrafficTests extends CompatChangeGatingTestCase {

    protected static final String TEST_APK = "CtsNetworkSecurityConfigHostDeviceApp.apk";
    protected static final String TEST_PKG = "com.android.cts.networksecurityconfig";
    private static final String DEVICE_SIDE_CLASS = ".DeviceSideUsesCleartextTrafficTests";
    private static final String TEST_METHOD_CHANGE_ENABLED =
            "deprecateUsesCleartextTraffic_changeEnabled";
    private static final String TEST_METHOD_CHANGE_DISABLED =
            "deprecateUsesCleartextTraffic_changeDisabled";

    private static final long DEPRECATE_USES_CLEARTEXT_TRAFFIC = 415007211L;

    @Override
    protected void setUp() throws Exception {
        installPackage(TEST_APK, true);
    }

    public void testDeprecateUsesCleartextTrafficChangeEnabled() throws Exception {
        runDeviceCompatTest(
                TEST_PKG,
                DEVICE_SIDE_CLASS,
                TEST_METHOD_CHANGE_ENABLED,
                /* enabledChanges= */ ImmutableSet.of(DEPRECATE_USES_CLEARTEXT_TRAFFIC),
                /* disabledChanges= */ ImmutableSet.of());
    }

    public void testDeprecateUsesCleartextTrafficChangeDisabled() throws Exception {
        runDeviceCompatTest(
                TEST_PKG,
                DEVICE_SIDE_CLASS,
                TEST_METHOD_CHANGE_DISABLED,
                /* enabledChanges= */ ImmutableSet.of(),
                /* disabledChanges= */ ImmutableSet.of(DEPRECATE_USES_CLEARTEXT_TRAFFIC));
    }
}
