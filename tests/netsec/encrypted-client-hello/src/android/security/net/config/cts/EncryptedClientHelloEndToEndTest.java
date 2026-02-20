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

import static android.security.Flags.FLAG_ENCRYPTED_CLIENT_HELLO_CONFIGURATION;

import static com.android.org.conscrypt.net.flags.Flags.FLAG_ENCRYPTED_CLIENT_HELLO_PLATFORM;
import static com.android.tethering.flags.Flags.FLAG_ENCRYPTED_CLIENT_HELLO_DNS;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.net.DnsResolver;
import android.net.dns.HttpsEndpoint;
import android.net.dns.HttpsRecord;
import android.net.ssl.EchConfigList;
import android.net.ssl.SSLSockets;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

@RunWith(AndroidJUnit4.class)
public class EncryptedClientHelloEndToEndTest extends BaseTestCase {

    public static final String TAG = EncryptedClientHelloEndToEndTest.class.getName();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final String ECH_DOMAIN = "tls-ech.dev";
    private static final String ECH_USED_STRING = "You are using ECH. :)";
    private static final String ECH_NOT_USED_STRING = "You are not using ECH. :(";
    private static final int TIMEOUT_MS = 12_000;
    private DnsResolver mDnsResolver;
    private Executor mExecutor;

    @Before
    public void setUp() {
        mDnsResolver = DnsResolver.getInstance();
        mExecutor = Executors.newSingleThreadExecutor();
    }

    static class DnsCallback<T> implements DnsResolver.Callback<T> {
        private final CountDownLatch mLatch = new CountDownLatch(1);
        private T mAnswer;

        public boolean waitForAnswer() throws InterruptedException {
            return mLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        }

        public T getAnswer() {
            return mAnswer;
        }

        @Override
        public void onAnswer(T answer, int rcode) {
            mAnswer = answer;
            mLatch.countDown();
        }

        @Override
        public void onError(DnsResolver.DnsException error) {
            mLatch.countDown();
        }
    }

    @Test
    public void testConnectWithoutEch_connectionSucceeds() throws Exception {
        DnsCallback<List<InetAddress>> callback = new DnsCallback<>();
        mDnsResolver.query(
                /* network= */ null,
                ECH_DOMAIN,
                /* flags= */ 0,
                mExecutor,
                /* cancellationSignal= */ null,
                callback);
        callback.waitForAnswer();
        List<InetAddress> addresses = callback.getAnswer();
        assumeTrue("No DNS response", addresses != null);

        SSLSocket sslSocket = createSocketWithSNI(addresses.get(0));

        sslSocket.startHandshake();
        Log.d(TAG, "successfully started handshake");

        assertWebpageContent(sslSocket, ECH_NOT_USED_STRING);
    }

    @Test
    @RequiresFlagsEnabled({
        FLAG_ENCRYPTED_CLIENT_HELLO_CONFIGURATION,
        FLAG_ENCRYPTED_CLIENT_HELLO_PLATFORM,
        FLAG_ENCRYPTED_CLIENT_HELLO_DNS
    })
    public void testSetValidEchConfigList_connectionSucceeds() throws Exception {
        DnsCallback<HttpsEndpoint> callback = new DnsCallback<>();
        mDnsResolver.query(
                /* network= */ null,
                ECH_DOMAIN,
                /* flags= */ 0,
                mExecutor,
                /* httpsTimeoutMillis= */ 1000,
                /* cancellationSignal= */ null,
                callback);
        callback.waitForAnswer();

        HttpsEndpoint endpoint = callback.getAnswer();
        assumeTrue("No DNS response", endpoint != null);

        List<HttpsRecord> httpsRecords = endpoint.getHttpsRecords();
        assumeTrue("No HTTPS records returned", !httpsRecords.isEmpty());

        List<InetAddress> addresses = endpoint.getIpAddresses();
        assumeTrue("No addresses returned", !addresses.isEmpty());

        EchConfigList config = httpsRecords.get(0).getEchConfigList();
        assertNotNull(config);

        SSLSocket sslSocket = createSocketWithSNI(addresses.get(0));
        SSLSockets.setEchConfigList(sslSocket, config);

        sslSocket.startHandshake();
        Log.d(TAG, "successfully started handshake");

        assertWebpageContent(sslSocket, ECH_USED_STRING);
    }

    @Test
    @RequiresFlagsEnabled({
        FLAG_ENCRYPTED_CLIENT_HELLO_CONFIGURATION,
        FLAG_ENCRYPTED_CLIENT_HELLO_PLATFORM,
        FLAG_ENCRYPTED_CLIENT_HELLO_DNS
    })
    public void testCorruptedEchConfigList_throwsEchConfigMismatchException() throws Exception {
        DnsCallback<HttpsEndpoint> callback = new DnsCallback<>();
        mDnsResolver.query(
                /* network= */ null,
                ECH_DOMAIN,
                /* flags= */ 0,
                mExecutor,
                /* httpsTimeoutMillis= */ 1000,
                /* cancellationSignal= */ null,
                callback);
        callback.waitForAnswer();

        HttpsEndpoint endpoint = callback.getAnswer();
        assumeTrue("No DNS response", endpoint != null);

        List<HttpsRecord> httpsRecords = endpoint.getHttpsRecords();
        assumeTrue("No HTTPS records returned", !httpsRecords.isEmpty());

        List<InetAddress> addresses = endpoint.getIpAddresses();
        assumeTrue("No addresses returned", !addresses.isEmpty());

        EchConfigList config = httpsRecords.get(0).getEchConfigList();
        assertNotNull(config);

        // Arbitrary corruption.
        byte[] tmp = config.toBytes();
        tmp[12] = 0x00;
        config = EchConfigList.fromBytes(tmp);

        SSLSocket sslSocket = createSocketWithSNI(addresses.get(0));
        SSLSockets.setEchConfigList(sslSocket, config);

        try {
            sslSocket.startHandshake();
            fail("The connection should have failed");
        } catch (android.net.ssl.EchConfigMismatchException e) {
            // Validate the hostname before retrieving the config list.
            HostnameVerifier hv = HttpsURLConnection.getDefaultHostnameVerifier();
            assertTrue(
                    "Hostname should match",
                    hv.verify(e.getPublicHostname(), sslSocket.getSession()));
            assertNotNull(e.getRetryConfigList());
        }
    }

    private SSLSocket createSocketWithSNI(InetAddress address) throws Exception {
        SSLSocket sslSocket = (SSLSocket) SSLSocketFactory.getDefault().createSocket(address, 443);

        // Enable SNI.
        SSLParameters sslParams = sslSocket.getSSLParameters();
        sslParams.setServerNames(
                Collections.<SNIServerName>singletonList(new SNIHostName(ECH_DOMAIN)));
        sslSocket.setSSLParameters(sslParams);

        return sslSocket;
    }

    private void assertWebpageContent(SSLSocket sslSocket, String expectedString)
            throws IOException {
        OutputStream outputStream = sslSocket.getOutputStream();
        PrintWriter writer = new PrintWriter(outputStream, /* autoFlush= */ true);

        InputStream inputStream = sslSocket.getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

        // Send a simple HTTP GET request
        String request =
                "GET / HTTP/1.1\r\n" + "Host: " + ECH_DOMAIN + "\r\n" + "Connection: close\r\n\r\n";
        writer.print(request);
        writer.flush();

        // Read the response
        char[] response = new char[1024];
        reader.read(response);
        String responseStr = new String(response);
        assertTrue(responseStr.contains(expectedString));
    }
}
