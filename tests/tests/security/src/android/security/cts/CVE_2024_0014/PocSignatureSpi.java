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

package android.security.cts.CVE_2024_0014;

import java.security.InvalidKeyException;
import java.security.InvalidParameterException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SignatureException;
import java.security.SignatureSpi;

class PocSignatureSpi extends SignatureSpi {

    @Override
    protected void engineInitVerify(PublicKey publicKey) throws InvalidKeyException {}

    @Override
    protected void engineInitSign(PrivateKey privateKey) throws InvalidKeyException {}

    @Override
    protected void engineUpdate(byte b) throws SignatureException {}

    @Override
    protected void engineUpdate(byte[] b, int off, int len) throws SignatureException {}

    @Override
    protected byte[] engineSign() throws SignatureException {
        return new byte[0];
    }

    @Override
    protected boolean engineVerify(byte[] sigBytes) throws SignatureException {
        // With fix, a check has been added to verify the return status.
        // Return 'false' to cause an 'IllegalStateException' if the check is
        // not present.
        return false;
    }

    @Override
    protected void engineSetParameter(String param, Object value)
            throws InvalidParameterException {}

    @Override
    protected Object engineGetParameter(String param) throws InvalidParameterException {
        return null;
    }
}
