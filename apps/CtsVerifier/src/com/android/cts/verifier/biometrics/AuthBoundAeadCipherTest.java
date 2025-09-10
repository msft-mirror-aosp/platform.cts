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

import java.time.Duration;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;

public class AuthBoundAeadCipherTest {
    private Cipher mCipher;

    void createUserAuthenticationKey(
            String keyName, Duration timeout, int authType, boolean useStrongBox) throws Exception {
        KeyGenParameterSpec spec =
                new KeyGenParameterSpec.Builder(
                        keyName, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setUserAuthenticationRequired(true)
                        .setUserAuthenticationParameters((int) timeout.toSeconds(), authType)
                        .setIsStrongBoxBacked(useStrongBox)
                        .build();

        KeyGenerator keyGenerator =
                KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        keyGenerator.init(spec);
        keyGenerator.generateKey();
    }

    void initializeKeystoreOperation(String keyName) throws Exception {
        mCipher = Utils.initAeadCipher(keyName);
    }

    Cipher getCryptoObject() {
        return mCipher;
    }

    void doKeystoreOperation(byte[] payload) throws Exception {
        try {
            byte[] aad = "Test aad data".getBytes();
            mCipher.updateAAD(aad);
            Utils.doEncrypt(mCipher, payload);
        } finally {
            mCipher = null;
        }
    }
}
