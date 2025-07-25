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

import static org.junit.Assert.assertEquals;

import android.os.Build;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "Baklava")
public class SctValidationNoLogListPresentTest extends BaseTestCase {

    @Test
    public void testCTVerification_whenLogListAbsent_noSctDomain_failsOpen() throws IOException {
        URL url = new URL(NO_SCT_PROVIDED_DOMAIN);

        HttpsURLConnection urlConnection = (HttpsURLConnection) url.openConnection();
        urlConnection.connect();

        assertEquals(urlConnection.getResponseCode(), HTTP_OK_RESPONSE_CODE);
        urlConnection.disconnect();
    }
}
