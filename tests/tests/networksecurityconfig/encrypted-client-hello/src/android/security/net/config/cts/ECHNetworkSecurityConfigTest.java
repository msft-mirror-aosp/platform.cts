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

package android.security.net.config.cts;

import static android.security.Flags.FLAG_ENCRYPTED_CLIENT_HELLO_CONFIGURATION;
import static android.security.NetworkSecurityPolicy.DOMAIN_ENCRYPTION_MODE_DISABLED;
import static android.security.NetworkSecurityPolicy.DOMAIN_ENCRYPTION_MODE_ENABLED;
import static android.security.NetworkSecurityPolicy.DOMAIN_ENCRYPTION_MODE_OPPORTUNISTIC;

import static org.junit.Assert.assertEquals;

import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.security.NetworkSecurityPolicy;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ECHNetworkSecurityConfigTest {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private final NetworkSecurityPolicy mInstance = NetworkSecurityPolicy.getInstance();

    @Test
    @RequiresFlagsEnabled(FLAG_ENCRYPTED_CLIENT_HELLO_CONFIGURATION)
    public void testDomainEncryptionMode_opportunisticDefault() throws Exception {
        assertEquals(
                DOMAIN_ENCRYPTION_MODE_OPPORTUNISTIC,
                mInstance.getDomainEncryptionMode("example.com"));
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ENCRYPTED_CLIENT_HELLO_CONFIGURATION)
    public void testDomainEncryptionMode_domainConfigOverride() throws Exception {
        assertEquals(
                DOMAIN_ENCRYPTION_MODE_DISABLED, mInstance.getDomainEncryptionMode("android.com"));
        assertEquals(
                DOMAIN_ENCRYPTION_MODE_ENABLED, mInstance.getDomainEncryptionMode("brambonne.com"));
        // TODO(b/476104302): update this assert once we have added back in support for required.
        // Ensure that even with "required" set, we fall back to opportunistic at this point.
        assertEquals(
                DOMAIN_ENCRYPTION_MODE_OPPORTUNISTIC,
                mInstance.getDomainEncryptionMode("tls-ech.dev"));
    }
}
