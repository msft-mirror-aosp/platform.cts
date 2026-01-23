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

package com.android.cts.verifier.biometrics;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.time.Duration;

public class AuthBoundSignatureTest {
    private Signature mSignature;

    void createUserAuthenticationKey(
            String keyName, Duration timeout, int authType, boolean useStrongBox) throws Exception {
        KeyGenParameterSpec spec =
                new KeyGenParameterSpec.Builder(keyName, KeyProperties.PURPOSE_SIGN)
                        .setDigests(KeyProperties.DIGEST_SHA256)
                        .setAlgorithmParameterSpec(new ECGenParameterSpec("secp256r1"))
                        .setUserAuthenticationRequired(true)
                        .setIsStrongBoxBacked(useStrongBox)
                        .setUserAuthenticationParameters((int) timeout.toSeconds(), authType)
                        .build();

        KeyPairGenerator keyPairGenerator =
                KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore");
        keyPairGenerator.initialize(spec);
        keyPairGenerator.generateKeyPair();
    }

    void initializeKeystoreOperation(String keyName) throws Exception {
        mSignature = Utils.initSignature(keyName);
    }

    Signature getCryptoObject() {
        return mSignature;
    }

    void doKeystoreOperation(byte[] payload) throws Exception {
        try {
            Utils.doSign(mSignature, payload);
        } finally {
            mSignature = null;
        }
    }
}
