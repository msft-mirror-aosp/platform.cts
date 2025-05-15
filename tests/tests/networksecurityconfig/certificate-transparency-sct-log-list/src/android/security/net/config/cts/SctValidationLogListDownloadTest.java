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
import static android.security.net.config.cts.CertificateTransparencyTestUtils.NO_SCT_PROVIDED_DOMAIN;
import static android.security.net.config.cts.CertificateTransparencyTestUtils.SCT_PROVIDED_DOMAIN;
import static android.security.net.config.cts.CertificateTransparencyTestUtils.SCT_PROVIDED_DOMAIN_2;
import static android.security.net.config.cts.CertificateTransparencyTestUtils.deleteLogList;
import static android.security.net.config.cts.CertificateTransparencyTestUtils.downloadLogList;
import static android.security.net.config.cts.CertificateTransparencyTestUtils.isLogListFilePresent;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.app.Instrumentation;
import android.os.Build;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.net.URL;
import java.security.cert.CertificateException;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLHandshakeException;

@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "Baklava")
public class SctValidationLogListDownloadTest extends BaseTestCase {

    public static final Instrumentation sInstrumentation =
            InstrumentationRegistry.getInstrumentation();

    private static final String TAG = "SctValidationLogListDownloadTest";

    @BeforeClass
    public static void setUpClass() throws Exception {
        downloadLogList();

        long delay = 1000; // 1sec
        for (int i = 0; i < 5; i++) {
            if (isLogListFilePresent()) {
                Log.d(TAG, "setUpClass: found the log list");
                break;
            }
            Log.d(TAG, "setUpClass: waiting " + delay + "ms");
            Thread.sleep(delay);
            delay += 1000;
        }
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        deleteLogList();
    }

    @Test
    public void testCTVerification_whenLogListDownloaded_sctDomain_connectionSucceeds()
            throws IOException {
        assumeTrue(isLogListFilePresent());
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
    public void testCTVerification_whenLogListDownloaded_noSctDomain_exceptionsThrown()
            throws IOException {
        assumeTrue(isLogListFilePresent());
        URL url = new URL(NO_SCT_PROVIDED_DOMAIN);

        HttpsURLConnection urlConnection = (HttpsURLConnection) url.openConnection();
        SSLHandshakeException expected =
                assertThrows(SSLHandshakeException.class, () -> urlConnection.connect());

        assertThat(expected.getCause()).isInstanceOf(CertificateException.class);
        assertTrue(expected.getMessage().contains("NOT_ENOUGH_SCTS"));
    }
}
