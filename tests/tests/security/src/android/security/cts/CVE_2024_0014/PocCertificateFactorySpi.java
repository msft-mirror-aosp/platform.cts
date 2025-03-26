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

import java.io.InputStream;
import java.security.cert.CRL;
import java.security.cert.CRLException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactorySpi;
import java.util.Collection;

class PocCertificateFactorySpi extends CertificateFactorySpi {

    @Override
    public Certificate engineGenerateCertificate(InputStream inStream) throws CertificateException {
        return new PocX509Certificate();
    }

    @Override
    public Collection<? extends Certificate> engineGenerateCertificates(InputStream inStream)
            throws CertificateException {
        return null;
    }

    @Override
    public CRL engineGenerateCRL(InputStream inStream) throws CRLException {
        return null;
    }

    @Override
    public Collection<? extends CRL> engineGenerateCRLs(InputStream inStream) throws CRLException {
        return null;
    }
}
