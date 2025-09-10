/*
 * Copyright (C) 2022 The Android Open Source Project
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
 * An abstract base class to add Aead Cipher tests.
 */
public abstract class AbstractUserAuthenticationAeadCipherTest
        extends AbstractUserAuthenticationTest {
    private AuthBoundAeadCipherTest mAuthBoundAeadCipherTest =
            new AuthBoundAeadCipherTest();

    @Override
    void createUserAuthenticationKey(String keyName, Duration timeout, int authType,
            boolean useStrongBox) throws Exception {
        mAuthBoundAeadCipherTest.createUserAuthenticationKey(
                keyName, timeout, authType, useStrongBox);
    }

    @Override
    void initializeKeystoreOperation(String keyName) throws Exception {
        mAuthBoundAeadCipherTest.initializeKeystoreOperation(keyName);
    }

    @Override
    BiometricPrompt.CryptoObject getCryptoObject() {
        return new BiometricPrompt.CryptoObject(mAuthBoundAeadCipherTest.getCryptoObject());
    }

    @Override
    void doKeystoreOperation(byte[] payload) throws Exception {
        mAuthBoundAeadCipherTest.doKeystoreOperation(payload);
    }
}
