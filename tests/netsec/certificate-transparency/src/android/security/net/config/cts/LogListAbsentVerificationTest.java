/*
 * Copyright (C) 2026 The Android Open Source Project
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

import static android.security.net.config.cts.CertificateTransparencyTestUtils.CT_ROOT_DIRECTORY_PATH;
import static android.security.net.config.cts.CertificateTransparencyTestUtils.HTTP_OK_RESPONSE_CODE;
import static android.security.net.config.cts.CertificateTransparencyTestUtils.SCT_PROVIDED_DOMAIN;
import static android.security.net.config.cts.CertificateTransparencyTestUtils.SCT_PROVIDED_DOMAIN_2;
import static android.security.net.config.cts.CertificateTransparencyTestUtils.deleteLogList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import android.os.Build;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

/** CT tests when the log list is not present on the device. */
@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "Baklava")
public class LogListAbsentVerificationTest extends BaseTestCase {

    @BeforeClass
    public static void setUpClass() {
        deleteLogList();
    }

    @Test
    public void testCTVerification_sctDomain_failsOpen() throws Exception {
        assertLogListAbsent();
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

    private void assertLogListAbsent() {
        assertFalse(
                "The log list should not be present.", new File(CT_ROOT_DIRECTORY_PATH).exists());
    }
}
