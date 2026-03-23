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
package android.devicepolicy.cts;

import static com.android.bedstead.enterprise.EnterpriseDeviceStateExtensionsKt.dpc;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.security.AttestedKeyPair;
import android.security.KeyChainManager;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import com.android.bedstead.enterprise.annotations.CanSetPolicyTest;
import com.android.bedstead.enterprise.policies.Delegation;
import com.android.bedstead.flags.annotations.RequireFlagsEnabled;
import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.nene.TestApis;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;

@RunWith(BedsteadJUnit4.class)
public final class ScopedKeyManagementTest {
    @ClassRule @Rule public static final DeviceState sDeviceState = new DeviceState();

    private static final String RSA = "RSA";
    private static final String EC = "EC";
    private static final Context sContext = TestApis.context().instrumentedContext();
    private static final DevicePolicyManager sDevicePolicyManager =
            sContext.getSystemService(DevicePolicyManager.class);

    @Test
    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = Delegation.class)
    @RequireFlagsEnabled(android.security.Flags.FLAG_ENABLE_DEVICE_CERTIFICATES)
    public void generateKeyPair_callerNotAuthorized_throwsException() {
        String alias = "scoped-user-key-rsa";

        KeyGenParameterSpec spec =
                new KeyGenParameterSpec.Builder(
                        alias, KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
                        .setDigests(KeyProperties.DIGEST_SHA256)
                        .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                        .build();

        assertThrows(
                SecurityException.class,
                () ->
                        sDevicePolicyManager.generateKeyPair(
                                RSA, spec, 0, KeyChainManager.KEYPAIR_SCOPE_USER));
    }

    @Test
    @Postsubmit(reason = "new test")
    @RequireFlagsEnabled(android.security.Flags.FLAG_ENABLE_DEVICE_CERTIFICATES)
    @CanSetPolicyTest(policy = Delegation.class)
    public void generateKeyPair_userScope_rsa_success() throws Exception {
        String alias = "scoped-user-key-rsa";

        dpc(sDeviceState)
                .devicePolicyManager()
                .setDelegatedScopes(
                        dpc(sDeviceState).componentName(),
                        sContext.getPackageName(),
                        Collections.singletonList(DevicePolicyManager.DELEGATION_CERT_INSTALL));

        try {
            KeyGenParameterSpec spec =
                    new KeyGenParameterSpec.Builder(
                            alias,
                            KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
                            .setDigests(KeyProperties.DIGEST_SHA256)
                            .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                            .build();

            AttestedKeyPair generated =
                    sDevicePolicyManager.generateKeyPair(
                            RSA, spec, 0, KeyChainManager.KEYPAIR_SCOPE_USER);

            assertThat(generated).isNotNull();
            assertThat(sDevicePolicyManager.hasKeyPair(alias)).isTrue();
        } finally {
            sDevicePolicyManager.removeKeyPair(null, alias);
        }
    }

    @Test
    @Postsubmit(reason = "new test")
    @RequireFlagsEnabled(android.security.Flags.FLAG_ENABLE_DEVICE_CERTIFICATES)
    @CanSetPolicyTest(policy = Delegation.class)
    public void generateKeyPair_userScope_ec_success() throws Exception {
        String alias = "scoped-user-key-ec";

        dpc(sDeviceState)
                .devicePolicyManager()
                .setDelegatedScopes(
                        dpc(sDeviceState).componentName(),
                        sContext.getPackageName(),
                        Collections.singletonList(DevicePolicyManager.DELEGATION_CERT_INSTALL));

        try {
            KeyGenParameterSpec spec =
                    new KeyGenParameterSpec.Builder(
                            alias,
                            KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
                            .setDigests(KeyProperties.DIGEST_SHA256)
                            .build();

            AttestedKeyPair generated =
                    sDevicePolicyManager.generateKeyPair(
                            EC, spec, 0, KeyChainManager.KEYPAIR_SCOPE_USER);

            assertThat(generated).isNotNull();
            assertThat(sDevicePolicyManager.hasKeyPair(alias)).isTrue();
        } finally {
            sDevicePolicyManager.removeKeyPair(null, alias);
        }
    }

    @Test
    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = Delegation.class)
    @RequireFlagsEnabled(android.security.Flags.FLAG_ENABLE_DEVICE_CERTIFICATES)
    public void generateKeyPair_invalidAlgorithm_throwsException() {
        String alias = "scoped-user-key-invalid";

        dpc(sDeviceState)
                .devicePolicyManager()
                .setDelegatedScopes(
                        dpc(sDeviceState).componentName(),
                        sContext.getPackageName(),
                        Collections.singletonList(DevicePolicyManager.DELEGATION_CERT_INSTALL));

        KeyGenParameterSpec spec =
                new KeyGenParameterSpec.Builder(
                        alias, KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
                        .setDigests(KeyProperties.DIGEST_SHA256)
                        .build();

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        sDevicePolicyManager.generateKeyPair(
                                "INVALID_ALGO", spec, 0, KeyChainManager.KEYPAIR_SCOPE_USER));
    }
}
