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

import static android.security.net.config.cts.TestUtils.assertSslSocketFails;
import static android.security.net.config.cts.TestUtils.assertSslSocketSucceeds;

import android.content.Context;
import android.security.net.config.cts.CtsNetSecConfigCustomTrustManagerTestCases.R;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Collection;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

@RunWith(AndroidJUnit4.class)
public class CustomTrustManagerTest {

    private Context mContext;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    @Test
    public void testKeystoreTrustManager_succeeds() throws Exception {
        SSLContext sslContext = getCustomSSLContext(R.raw.for_connecting_to_google_certs);

        assertSslSocketSucceeds(sslContext, "android.com", 443);
        // TODO(b/471062252): re-enable once badssl.com gets a new certificate.
        // assertSslSocketSucceeds(sslContext, "no-sct.badssl.com", 443);
    }

    @Test
    public void testKeystoreTrustManager_fails() throws Exception {
        SSLContext sslContext = getCustomSSLContext(R.raw.for_connecting_to_google_certs);

        assertSslSocketFails(sslContext, "expired.badssl.com", 443);
    }

    private SSLContext getCustomSSLContext(int chainResId) throws Exception {
        KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
        keystore.load(null);
        int i = 0;
        for (X509Certificate ca : loadCertificates(chainResId)) {
            keystore.setCertificateEntry(String.valueOf(i++), ca);
        }
        TrustManagerFactory tmf = TrustManagerFactory.getInstance("PKIX");
        tmf.init(keystore);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, tmf.getTrustManagers(), null);
        return sslContext;
    }

    private X509Certificate[] loadCertificates(int resId) throws Exception {
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        try (InputStream is = mContext.getResources().openRawResource(resId)) {
            Collection<? extends Certificate> collection = factory.generateCertificates(is);
            X509Certificate[] certs = new X509Certificate[collection.size()];
            int i = 0;
            for (Certificate cert : collection) {
                certs[i++] = (X509Certificate) cert;
            }
            return certs;
        }
    }
}
