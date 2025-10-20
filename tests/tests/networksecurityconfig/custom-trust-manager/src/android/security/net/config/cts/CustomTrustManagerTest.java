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

import android.content.Context;
import android.security.net.config.cts.CtsNetSecConfigCustomTrustManagerTestCases.R;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@Ignore("b/449012599")
public class CustomTrustManagerTest {

    private Context mContext;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    @Test
    public void testKeystoreTrustManager() throws Exception {
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, getKeystoreTrustManager(), null);

        // TODO(b/407952621): Add test for "no-sct.badssl.com".
        SSLSocket s = (SSLSocket) sslContext.getSocketFactory().createSocket("android.com", 443);
        s.startHandshake();
        s.getInputStream();
    }

    private TrustManager[] getKeystoreTrustManager() throws Exception {
        KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
        keystore.load(null);
        X509Certificate cert = null;
        try (InputStream input = mContext.getResources().openRawResource(R.raw.gts_root_r4)) {
            cert =
                    (X509Certificate)
                            CertificateFactory.getInstance("X.509").generateCertificate(input);
        }
        keystore.setEntry("0", new KeyStore.TrustedCertificateEntry(cert), null);

        TrustManagerFactory tmf = TrustManagerFactory.getInstance("PKIX");
        tmf.init(keystore);
        return tmf.getTrustManagers();
    }
}
