/*
 * Copyright (C) 2011 The Android Open Source Project
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

package android.security.cts;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import android.content.pm.PackageManager;
import android.test.AndroidTestCase;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CertificateTest extends AndroidTestCase {
    // The directory for CA root certificates trusted by WFA (WiFi Alliance)
    static final String DIR_OF_CACERTS_FOR_WFA = "/etc/security/cacerts_wfa";

    // Fix for b/289965967 due to backport issues. These certs were deleted but may still be on some
    // devices
    private Set<String> mOptionalCertificates = new HashSet<String>(
            Arrays.asList(
            new String[] {"B8:BE:6D:CB:56:F1:55:B9:63:D4:12:CA:4E:06:34:C7:94:B2:1C:C0",
                "FF:BD:CD:E7:82:C8:43:5E:3C:6F:26:86:5C:CA:A8:3A:45:5B:C3:0A",
                "51:C6:E7:08:49:06:6E:F3:92:D4:5C:A0:0D:6D:A3:62:8F:C3:52:39",
                "58:D1:DF:95:95:67:6B:63:C0:F0:5B:1C:17:4D:8B:84:0B:C8:78:BD",
                "A8:98:5D:3A:65:E5:E5:C4:B2:D7:D6:6D:40:C6:DD:2F:B1:9C:54:36",
                "D8:C5:38:8A:B7:30:1B:1B:6E:D4:7A:E6:45:25:3A:6F:9F:1A:27:61",
                "66:31:BF:9E:F7:4F:9E:B6:C9:D5:A6:0C:BA:6A:BE:D1:F7:BD:EF:7B",
                "43:13:BB:96:F1:D5:86:9B:C1:4E:6A:92:F6:CF:F6:34:69:87:82:37",
                "05:63:B8:63:0D:62:D7:5A:BB:C8:AB:1E:4B:DF:B5:A8:99:B2:4D:43",
                "CA:3A:FB:CF:12:40:36:4B:44:B2:16:20:88:80:48:39:19:93:7C:F7",
                "5F:B7:EE:06:33:E2:59:DB:AD:0C:4C:9A:E6:D3:8F:1A:61:C7:DC:25",
                "29:36:21:02:8B:20:ED:02:F5:66:C5:32:D1:D6:ED:90:9F:45:00:2F",
                "FA:B7:EE:36:97:26:62:FB:2D:B0:2A:F6:BF:03:FD:E8:7C:4B:2F:9B",
                "1F:49:14:F7:D8:74:95:1D:DD:AE:02:C0:BE:FD:3A:2D:82:75:51:85",
                "3A:44:73:5A:E5:81:90:1F:24:86:61:46:1E:3B:9C:C4:5F:F5:3A:1B",
                "B8:23:6B:00:2F:1D:16:86:53:01:55:6C:11:A4:37:CA:EB:FF:C3:BB",
                "87:82:C6:C3:04:35:3B:CF:D2:96:92:D2:59:3E:7D:44:D9:34:FF:11",
                "D8:A6:33:2C:E0:03:6F:B1:85:F6:63:4F:7D:6A:06:65:26:32:28:27",
                "F9:B5:B6:32:45:5F:9C:BE:EC:57:5F:80:DC:E9:6E:2C:C7:B2:78:B7",
                "B1:2E:13:63:45:86:A4:6F:1A:B2:60:68:37:58:2D:C4:AC:FD:94:97"}));

    public void testNoRemovedCertificates() throws Exception {
        Set<String> expectedCertificates = new HashSet<String>(
                Arrays.asList(CertificateData.CERTIFICATE_DATA));
        Set<String> deviceCertificates = getDeviceCertificates();
        expectedCertificates.removeAll(deviceCertificates);
        expectedCertificates.removeAll(mOptionalCertificates);
        assertEquals("Missing CA certificates", Collections.EMPTY_SET, expectedCertificates);
    }

    /**
     * If you fail CTS as a result of adding a root CA that is not part of the Android root CA
     * store, please see the following.
     *
     * <p>This test exists because adding root CAs to a device has a very significant security
     * impact. Whoever has access to the signing keys of that CA can compromise secure network
     * traffic from affected Android devices, putting users at risk.
     *
     * <p>If you have a CA certificate which needs to be trusted by a particular app/service,
     * ask the developer of the app/service to modify it to trust this CA (e.g., using Network
     * Security Config feature). This avoids compromising the security of network traffic of other
     * apps on the device.
     *
     * <p>If you have a CA certificate that you believe should be present on all Android devices,
     * please file a public bug at https://code.google.com/p/android/issues/entry.
     *
     * <p>For questions, comments, and code reviews please contact security@android.com.
     */
    public void testNoAddedCertificates() throws Exception {
        Set<String> expectedCertificates = new HashSet<String>(
                Arrays.asList(CertificateData.CERTIFICATE_DATA));
        Set<String> deviceCertificates = getDeviceCertificates();
        deviceCertificates.removeAll(expectedCertificates);
        deviceCertificates.removeAll(mOptionalCertificates);
        assertEquals("Unknown CA certificates", Collections.EMPTY_SET, deviceCertificates);
    }

    public void testBlockCertificates() throws Exception {
        Set<String> blockCertificates = new HashSet<String>();
        blockCertificates.add("C0:60:ED:44:CB:D8:81:BD:0E:F8:6C:0B:A2:87:DD:CF:81:67:47:8C");

        Set<String> deviceCertificates = getDeviceCertificates();
        deviceCertificates.retainAll(blockCertificates);
        assertEquals("Blocked CA certificates", Collections.EMPTY_SET, deviceCertificates);
    }

    /**
     * This test exists because adding new ca certificate or removing the ca certificates trusted by
     * WFA (WiFi Alliance) is not allowed.
     *
     * For questions, comments, and code reviews please contact security@android.com.
     */
    public void testNoRemovedWfaCertificates() throws Exception {
        if (!supportPasspoint()) {
            return;
        }
        Set<String> expectedCertificates = new HashSet<>(
                Arrays.asList(CertificateData.WFA_CERTIFICATE_DATA));
        Set<String> deviceWfaCertificates = getDeviceWfaCertificates();
        expectedCertificates.removeAll(deviceWfaCertificates);
        assertEquals("Missing WFA CA certificates", Collections.EMPTY_SET, expectedCertificates);
    }

    public void testNoAddedWfaCertificates() throws Exception {
        if (!supportPasspoint()) {
            return;
        }
        Set<String> expectedCertificates = new HashSet<String>(
                Arrays.asList(CertificateData.WFA_CERTIFICATE_DATA));
        Set<String> deviceWfaCertificates = getDeviceWfaCertificates();
        deviceWfaCertificates.removeAll(expectedCertificates);
        assertEquals("Unknown WFA CA certificates", Collections.EMPTY_SET, deviceWfaCertificates);
    }

    private boolean supportPasspoint() {
        return mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI_PASSPOINT);
    }

    private KeyStore createWfaKeyStore(String dirPath) throws CertificateException, IOException,
            KeyStoreException, NoSuchAlgorithmException {
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);
        String wfaCertsDir = System.getenv("ANDROID_ROOT") + dirPath;
        int index = 0;
        for (X509Certificate cert : loadCertsFromDisk(wfaCertsDir)) {
            keyStore.setCertificateEntry(String.format("%d", index++), cert);
        }
        return keyStore;
    }

    private Set<X509Certificate> loadCertsFromDisk(String directory) throws CertificateException,
            IOException {
        Set<X509Certificate> certs = new HashSet<>();
        File certDir = new File(directory);
        File[] certFiles = certDir.listFiles();
        if (certFiles == null || certFiles.length <= 0) {
            return certs;
        }
        CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
        for (File certFile : certFiles) {
            FileInputStream fis = new FileInputStream(certFile);
            Certificate cert = certFactory.generateCertificate(fis);
            if (cert instanceof X509Certificate) {
                certs.add((X509Certificate) cert);
            }
            fis.close();
        }
        return certs;
    }

    private Set<String> getDeviceWfaCertificates() throws KeyStoreException,
            NoSuchAlgorithmException, CertificateException, IOException {
        KeyStore wfaKeyStore = createWfaKeyStore(DIR_OF_CACERTS_FOR_WFA);
        List<String> aliases = Collections.list(wfaKeyStore.aliases());
        assertFalse(aliases.isEmpty());

        Set<String> certificates = new HashSet<>();
        for (String alias : aliases) {
            assertTrue(wfaKeyStore.isCertificateEntry(alias));
            X509Certificate certificate = (X509Certificate) wfaKeyStore.getCertificate(alias);
            assertEquals(certificate.getSubjectUniqueID(), certificate.getIssuerUniqueID());
            assertNotNull(certificate.getSubjectDN());
            assertNotNull(certificate.getIssuerDN());
            String fingerprint = getFingerprint(certificate);
            certificates.add(fingerprint);
        }
        return certificates;
    }

    private Set<String> getDeviceCertificates() throws KeyStoreException,
            NoSuchAlgorithmException, CertificateException, IOException {
        KeyStore keyStore = KeyStore.getInstance("AndroidCAStore");
        keyStore.load(null, null);

        List<String> aliases = Collections.list(keyStore.aliases());
        assertFalse(aliases.isEmpty());

        Set<String> certificates = new HashSet<String>();
        for (String alias : aliases) {
            assertTrue(keyStore.isCertificateEntry(alias));
            X509Certificate certificate = (X509Certificate) keyStore.getCertificate(alias);
            assertEquals(certificate.getSubjectUniqueID(), certificate.getIssuerUniqueID());
            assertNotNull(certificate.getSubjectDN());
            assertNotNull(certificate.getIssuerDN());
            String fingerprint = getFingerprint(certificate);
            certificates.add(fingerprint);
        }
        return certificates;
    }

    private String getFingerprint(X509Certificate certificate) throws CertificateEncodingException,
            NoSuchAlgorithmException {
        MessageDigest messageDigest = MessageDigest.getInstance("SHA1");
        messageDigest.update(certificate.getEncoded());
        byte[] sha1 = messageDigest.digest();
        return convertToHexFingerprint(sha1);
    }

    private String convertToHexFingerprint(byte[] sha1) {
        StringBuilder fingerprint = new StringBuilder();
        for (int i = 0; i < sha1.length; i++) {
            fingerprint.append(String.format("%02X", sha1[i]));
            if (i + 1 < sha1.length) {
                fingerprint.append(":");
            }
        }
        return fingerprint.toString();
    }
}
