/*
 * Copyright (C) 2016 The Android Open Source Project
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

import static org.junit.Assert.fail;

import android.content.Context;
import android.net.http.AndroidHttpClient;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.AssumptionViolatedException;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URL;
import java.net.UnknownHostException;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

public final class TestUtils {

    private TestUtils() {
    }

    public static void assertTlsConnectionSucceeds(String host, int port) throws Exception {
        assertSslSocketSucceeds(host, port);
        assertHttpClientSucceeds(host, port, true /* https */);
        assertUrlConnectionSucceeds(host, port, true /* https */);
    }

    public static void assertTlsConnectionFails(String host, int port) throws Exception {
        assertSslSocketFails(host, port);
        assertHttpClientFails(host, port, true /* https */);
        assertUrlConnectionFails(host, port, true /* https */);
    }

    public static void assertCleartextConnectionSucceeds(String host, int port) throws Exception {
        assertHttpClientSucceeds(host, port, false /* http */);
        assertUrlConnectionSucceeds(host, port, false /* http */);
    }

    public static void assertCleartextConnectionFails(String host, int port) throws Exception {
        assertHttpClientFails(host, port, false /* http */);
        assertUrlConnectionFails(host, port, false /* http */);
    }

    public static X509TrustManager getDefaultTrustManager() throws Exception {
        TrustManagerFactory tmf = TrustManagerFactory.getInstance("PKIX");
        tmf.init((KeyStore)null);
        for (TrustManager tm : tmf.getTrustManagers()) {
            if (tm instanceof X509TrustManager) {
                return (X509TrustManager) tm;
            }
        }
        fail("Unable to find X509TrustManager");
        return null;
    }

    public static List<X509Certificate> loadCertificates(InputStream is) throws Exception {
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        ArrayList<X509Certificate> result = new ArrayList<>();
        for (Certificate c : factory.generateCertificates(is)) {
            result.add((X509Certificate) c);
        }
        return result;
    }

    private static final String HTTP_RESPONSE =
            "HTTP/1.0 200 OK\r\nContent-Type: text/plain\r\nContent-length: 5\r\n\r\nhello";

    /**
     * Starts a fake TCP server using serverSocket on a separate thread. Callers can terminate the
     * server by closing the serverSocket (see {@link ServerSocket.close}).
     */
    public static Thread startMockServer(ServerSocket serverSocket) {
        Runnable serverRunnable =
                new Runnable() {
                    @Override
                    public void run() {
                        while (true) {
                            try {
                                Socket s = serverSocket.accept();
                                s.getOutputStream().write(HTTP_RESPONSE.getBytes());
                                s.getOutputStream().flush();
                                s.close();
                            } catch (SocketException e) {
                                // If serverSocket has been closed, we will
                                // receive this exception. Consider that the
                                // test is now terminated.
                                return;
                            } catch (IOException e) {
                                // Otherwise, ignore the error. Maybe the
                                // client closed the socket will we were
                                // writing. Let's get ready to serve another
                                // client.
                            }
                        }
                    }
                };
        Thread t = new Thread(serverRunnable);
        t.start();
        return t;
    }

    public static ServerSocket bindCleartextServer() throws IOException {
        ServerSocket serverSocket = new ServerSocket();
        serverSocket.bind(/* SocketAddress= */ null);
        return serverSocket;
    }

    /**
     * Binds a TLS server locally for testing.
     *
     * @param ctx the app context.
     * @param chainResId the resource ID of the certificate chain (PEM file).
     * @param keyResId the private key used to sign the certificates (PKCS8 file).
     */
    public static SSLServerSocket bindTLSServer(Context ctx, int chainResId, int keyResId)
            throws Exception {
        // Load certificate chain.
        X509Certificate[] certs;
        try (InputStream is = ctx.getResources().openRawResource(chainResId)) {
            certs = loadCertificates(is).toArray(new X509Certificate[0]);
        }

        // Load private key for the leaf.
        PrivateKey key;
        try (InputStream is = ctx.getResources().openRawResource(keyResId)) {
            byte[] keyBytes = is.readAllBytes();
            key = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
        }

        // Create KeyStore based on the private key/chain.
        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        ks.load(null);
        ks.setKeyEntry("name", key, null, certs);

        // Create SSLContext.
        TrustManagerFactory tmf = TrustManagerFactory.getInstance("PKIX");
        tmf.init(ks);
        KeyManagerFactory kmf =
                KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, null);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

        SSLServerSocket s = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();
        s.bind(/* SocketAddress= */ null);
        return s;
    }

    private static void assertSslSocketFails(String host, int port)
            throws Exception {
        try {
            SSLSocket s =
                    (SSLSocket) SSLContext.getDefault().getSocketFactory().createSocket(host, port);
            s.startHandshake();
            fail("Connection to " + host + ":" + port + " succeeded");
        } catch (UnknownHostException e) {
            throw new AssumptionViolatedException("Unable to resolve " + host, e);
        } catch (SSLHandshakeException expected) {
        }
    }

    private static void assertSslSocketSucceeds(String host, int port)
            throws Exception {
        try {
            SSLSocket s =
                    (SSLSocket) SSLContext.getDefault().getSocketFactory().createSocket(host, port);
            s.startHandshake();
        } catch (UnknownHostException e) {
            throw new AssumptionViolatedException("Unable to resolve " + host, e);
        }
    }

    private static void assertUrlConnectionFails(String host, int port, boolean https)
            throws Exception {
        URL url = new URL((https ? "https://" : "http://") + host + ":" + port);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        try {
            connection.getInputStream();
            fail("Connection to " + host + ":" + port + " succeeded");
        } catch (UnknownHostException e) {
            throw new AssumptionViolatedException("Unable to resolve " + host, e);
        } catch (IOException expected) {
        }
    }

    private static void assertUrlConnectionSucceeds(String host, int port, boolean https)
            throws Exception {
        try {
            URL url = new URL((https ? "https://" : "http://") + host + ":" + port);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.getInputStream();
        } catch (UnknownHostException e) {
            throw new AssumptionViolatedException("Unable to resolve " + host, e);
        }
    }

    private static void assertHttpClientSucceeds(String host, int port, boolean https)
            throws Exception {
        URL url = new URL((https ? "https://" : "http://") + host + ":" + port);
        AndroidHttpClient httpClient = AndroidHttpClient.newInstance(null);
        try {
            HttpResponse response = httpClient.execute(new HttpGet(url.toString()));
        } catch (UnknownHostException e) {
            throw new AssumptionViolatedException("Unable to resolve " + host, e);
        } finally {
            httpClient.close();
        }
    }

    private static void assertHttpClientFails(String host, int port, boolean https)
            throws Exception {
        URL url = new URL((https ? "https://" : "http://") + host + ":" + port);
        AndroidHttpClient httpClient = AndroidHttpClient.newInstance(null);
        try {
            HttpResponse response = httpClient.execute(new HttpGet(url.toString()));
            fail("Connection to " + host + ":" + port + " succeeded");
        } catch (UnknownHostException e) {
            throw new AssumptionViolatedException("Unable to resolve " + host, e);
        } catch (IOException expected) {
        } finally {
            httpClient.close();
        }
    }
}
