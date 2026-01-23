/*
 * Copyright (C) 2020 The Android Open Source Project
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

import android.hardware.biometrics.BiometricPrompt;

import java.time.Duration;

/**
 * This class enables testing the behaviour of various types of keys bound to user
 * authentication. It ensures that keys for cryptographic primitives supported
 * by the "AndroidKeyStore" provider are successfully generated and can only be
 * used after a successful authentication of the expected kind (e.g. biometic
 * and/or device credential) within the expected timeout.
 */
public abstract class AbstractUserAuthenticationTimedKeyTest
        extends AbstractUserAuthenticationTest {
    private AuthBoundCipherTest mAuthBoundCipherTest = new AuthBoundCipherTest();
    private AuthBoundAeadCipherTest mAuthBoundAeadCipherTest =
            new AuthBoundAeadCipherTest();
    private AuthBoundKeyAgreementTest mAuthBoundKeyAgreementTest = new AuthBoundKeyAgreementTest();
    private AuthBoundMacTest mAuthBoundMacTest = new AuthBoundMacTest();
    private AuthBoundSignatureTest mAuthBoundSignatureTest = new AuthBoundSignatureTest();

    private String mKeyName;

    @Override
    void createUserAuthenticationKey(
            String keyName, Duration timeout, int authType, boolean useStrongBox) throws Exception {
        mKeyName = keyName;
        mAuthBoundCipherTest.createUserAuthenticationKey(
                "cipher_" + keyName, timeout, authType, useStrongBox);
        mAuthBoundAeadCipherTest.createUserAuthenticationKey(
                "aead_cipher_" + keyName, timeout, authType, useStrongBox);
        mAuthBoundKeyAgreementTest.createUserAuthenticationKey(
                "agree_key_" + keyName, timeout, authType, useStrongBox);
        mAuthBoundMacTest.createUserAuthenticationKey(
                "mac_key_" + keyName, timeout, authType, useStrongBox);
        mAuthBoundSignatureTest.createUserAuthenticationKey(
                "sign_key_" + keyName, timeout, authType, useStrongBox);
    }

    @Override
    void initializeKeystoreOperation(String keyName) throws Exception {
        // Taken care while performing the operation in `doKeystoreOperation`.
        // Due to limited number operations slots in STRONGBOX, we can't hold more than four
        // operations. So, performing the tests sequentially instead of running them in two
        // steps - initialize and finish the operation.
    }

    @Override
    BiometricPrompt.CryptoObject getCryptoObject() {
        return null;
    }

    @Override
    void doKeystoreOperation(byte[] payload) throws Exception {
        mAuthBoundCipherTest.initializeKeystoreOperation("cipher_" + mKeyName);
        mAuthBoundCipherTest.doKeystoreOperation(payload);

        mAuthBoundAeadCipherTest.initializeKeystoreOperation("aead_cipher_" + mKeyName);
        mAuthBoundAeadCipherTest.doKeystoreOperation(payload);

        mAuthBoundKeyAgreementTest.initializeKeystoreOperation("agree_key_" + mKeyName);
        mAuthBoundKeyAgreementTest.doKeystoreOperation(payload);

        mAuthBoundMacTest.initializeKeystoreOperation("mac_key_" + mKeyName);
        mAuthBoundMacTest.doKeystoreOperation(payload);

        mAuthBoundSignatureTest.initializeKeystoreOperation("sign_key_" + mKeyName);
        mAuthBoundSignatureTest.doKeystoreOperation(payload);
    }
}
