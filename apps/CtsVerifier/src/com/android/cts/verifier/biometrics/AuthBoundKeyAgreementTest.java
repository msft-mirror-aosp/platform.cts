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

import org.junit.Assert;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.time.Duration;

import javax.crypto.KeyAgreement;

public class AuthBoundKeyAgreementTest {
    private KeyAgreement mKeyAgreement;
    private KeyPair mKeyPair;

    void createUserAuthenticationKey(
            String keyName, Duration timeout, int authType, boolean useStrongBox) throws Exception {
        KeyGenParameterSpec spec =
                new KeyGenParameterSpec.Builder(keyName, KeyProperties.PURPOSE_AGREE_KEY)
                        .setUserAuthenticationRequired(true)
                        .setAlgorithmParameterSpec(new ECGenParameterSpec("secp256r1"))
                        .setUserAuthenticationParameters((int) timeout.toSeconds(), authType)
                        .setIsStrongBoxBacked(useStrongBox)
                        .build();

        KeyPairGenerator keyPairGenerator =
                KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore");
        keyPairGenerator.initialize(spec);
        mKeyPair = keyPairGenerator.generateKeyPair();
    }

    void initializeKeystoreOperation(String keyName) throws Exception {
        mKeyAgreement = Utils.initKeyAgreement(keyName);
    }

    KeyAgreement getCryptoObject() {
        return mKeyAgreement;
    }

    void doKeystoreOperation(byte[] payload) throws Exception {
        try {
            KeyPairGenerator otherKPG = KeyPairGenerator.getInstance("EC");
            otherKPG.initialize(256);
            KeyPair otherKP = otherKPG.generateKeyPair();

            // Generate shared secret of the first keyAgreement
            mKeyAgreement.doPhase(otherKP.getPublic(), true);
            byte[] ourSharedSecret = mKeyAgreement.generateSecret();

            // Generate second Shared Secret
            KeyAgreement secondKeyAgreement = KeyAgreement.getInstance("ECDH");
            secondKeyAgreement.init(otherKP.getPrivate());
            secondKeyAgreement.doPhase(mKeyPair.getPublic(), true);
            byte[] theirSharedSecret = secondKeyAgreement.generateSecret();

            Assert.assertArrayEquals(ourSharedSecret, theirSharedSecret);
        } finally {
            mKeyAgreement = null;
            mKeyPair = null;
        }
    }
}
