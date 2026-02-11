/*
 * Copyright (C) 2024 The Android Open Source Project
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

import static android.security.net.config.cts.CertificateTransparencyTestUtils.HTTP_OK_RESPONSE_CODE;
import static android.security.net.config.cts.CertificateTransparencyTestUtils.INSTALL_COMPLETE_ACTION;
import static android.security.net.config.cts.CertificateTransparencyTestUtils.SCT_PROVIDED_DOMAIN;
import static android.security.net.config.cts.CertificateTransparencyTestUtils.SCT_PROVIDED_DOMAIN_2;
import static android.security.net.config.cts.CertificateTransparencyTestUtils.downloadLogList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.http.X509TrustManagerExtensions;
import android.os.Build;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.net.URL;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.X509TrustManager;

@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "Baklava")
public class LogListVerificationTest extends BaseTestCase {

    private static final Context sContext =
            InstrumentationRegistry.getInstrumentation().getContext();
    private static final CountDownLatch sLatch = new CountDownLatch(1);
    private static final BroadcastReceiver sInstallCompleteReceiver =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (intent.getAction().equals(INSTALL_COMPLETE_ACTION)) {
                        sLatch.countDown();
                    }
                }
            };

    @BeforeClass
    public static void setUpClass() {
        sContext.registerReceiver(
                sInstallCompleteReceiver,
                new IntentFilter(INSTALL_COMPLETE_ACTION),
                Context.RECEIVER_NOT_EXPORTED);

        downloadLogList();
    }

    @Test
    public void testCTVerification_whenLogListPresent_sctDomain_connectionSucceeds()
            throws Exception {
        assumeLogListPresent();
        // Check multiple domains as part of the retrospective for b/408109183
        URL url = new URL(SCT_PROVIDED_DOMAIN);
        URL url2 = new URL(SCT_PROVIDED_DOMAIN_2);

        HttpsURLConnection urlConnection = (HttpsURLConnection) url.openConnection();
        HttpsURLConnection urlConnection2 = (HttpsURLConnection) url2.openConnection();
        urlConnection.connect();
        urlConnection2.connect();

        assertEquals(urlConnection.getResponseCode(), HTTP_OK_RESPONSE_CODE);
        assertEquals(urlConnection2.getResponseCode(), HTTP_OK_RESPONSE_CODE);
        urlConnection.disconnect();
        urlConnection2.disconnect();
    }

    @Test
    public void testCTVerification_whenLogListAbsent_sctDomain_failsOpen() throws Exception {
        assumeLogListPresent();
        // Check multiple domains as part of the retrospective for b/408109183
        URL url = new URL(SCT_PROVIDED_DOMAIN);
        URL url2 = new URL(SCT_PROVIDED_DOMAIN_2);

        HttpsURLConnection urlConnection = (HttpsURLConnection) url.openConnection();
        HttpsURLConnection urlConnection2 = (HttpsURLConnection) url2.openConnection();
        urlConnection.connect();
        urlConnection2.connect();

        assertEquals(urlConnection.getResponseCode(), HTTP_OK_RESPONSE_CODE);
        assertEquals(urlConnection2.getResponseCode(), HTTP_OK_RESPONSE_CODE);
        urlConnection.disconnect();
        urlConnection2.disconnect();
    }

    @Test
    public void testX509TrustManagerExtensions_whenLogListPresent_sctDomain_connectionSucceeds()
            throws Exception {
        assumeLogListPresent();
        URL url = new URL(SCT_PROVIDED_DOMAIN);
        HttpsURLConnection urlConnection = (HttpsURLConnection) url.openConnection();
        // While the connection here already verifies the SCTs, we establish it
        // to collect the certificates and manually call checkServerTrusted.
        urlConnection.connect();
        Certificate[] certs = urlConnection.getServerCertificates();
        X509Certificate leaf = (X509Certificate) certs[0];
        X509TrustManager tm = TestUtils.getDefaultTrustManager();
        X509TrustManagerExtensions tme = new X509TrustManagerExtensions(tm);

        tme.checkServerTrusted(
                new X509Certificate[] {leaf},
                /* ocspData */ null,
                /* tlsSctData */ null,
                "RSA",
                url.getHost());

        // No assert needed as any failure would result in an exception being thrown
        urlConnection.disconnect();
    }

    private void assumeLogListPresent() throws InterruptedException {
        assumeTrue(
                "The test timed out while waiting for the log list download.",
                sLatch.await(30, TimeUnit.SECONDS));
    }
}
