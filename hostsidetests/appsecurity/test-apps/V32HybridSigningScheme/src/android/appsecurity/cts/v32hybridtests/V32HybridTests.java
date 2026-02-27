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

package android.appsecurity.cts.v32hybridtests;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.test.AndroidTestCase;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * On-device tests for the v3.2 hybrid signature scheme to verify signature related APIs behave as
 * expected after installation of packages signed with the v3.2 hybrid signature scheme.
 */
public class V32HybridTests extends AndroidTestCase {
    private static final String TEST_PACKAGE = "android.appsecurity.cts.tinyapp";
    private static final String COMPANION_PACKAGE = "android.appsecurity.cts.tinyapp_companion";
    private static final String PERMISSION_NAME = "android.appsecurity.cts.tinyapp.perm";
    private static final char[] HEX_VALUES = "0123456789abcdef".toCharArray();

    // ML-DSA certificates are prohibitively large to store as hex, so perform verification based
    // on the digest of the certificate.
    private static final String ML_DSA_65_CERT_SHA256_DIGEST =
            "6db4c701ac75b9a29b264dbae2027ad4d34792bee5b4f2c5d5834d960d2f4c81";
    private static final String ML_DSA_87_CERT_SHA256_DIGEST =
            "e25f17858d6500dc005cdbdae585ed4d1fdc4773f9701ae0ed9f05eb61839b9a";
    private static final String RSA_2048_SHA256_DIGEST =
            "fb5dbd3c669af9fc236c6991e6387b7f11ff0590997f22d0f5c74ff40e04fca8";
    private static final String RSA_2048_2_SHA256_DIGEST =
            "681b0e56a796350c08647352a4db800cc44b2adc8f4c72fa350bd05d4d50264d";
    private static final String RSA_2048_3_SHA256_DIGEST =
            "bb77a72efc60e66501ab75953af735874f82cfe52a70d035186a01b3482180f3";
    private static final String RSA_2048_4_SHA256_DIGEST =
            "fd2e84c802e0fee9f76ce0934f91c83cc0c300264e669ecfbbbd891d2af7b7e6";

    public void testV32_originalKeyAndV32HybridConfig() throws Exception {
        // Verifies that the platform recognizes the PQC signer as the current signer and the
        // classical key along with the original signing key are in the signing lineage.
        PackageManager packageManager = getContext().getPackageManager();
        PackageInfo packageInfo =
                packageManager.getPackageInfo(
                        TEST_PACKAGE, PackageManager.GET_SIGNING_CERTIFICATES);

        assertTrue(
                packageManager.hasSigningCertificate(
                        TEST_PACKAGE,
                        hexToBytes(ML_DSA_65_CERT_SHA256_DIGEST),
                        PackageManager.CERT_INPUT_SHA256));
        assertTrue(
                packageManager.hasSigningCertificate(
                        TEST_PACKAGE,
                        hexToBytes(RSA_2048_2_SHA256_DIGEST),
                        PackageManager.CERT_INPUT_SHA256));
        assertTrue(
                packageManager.hasSigningCertificate(
                        TEST_PACKAGE,
                        hexToBytes(RSA_2048_SHA256_DIGEST),
                        PackageManager.CERT_INPUT_SHA256));
        assertExpectedSignaturesDigests(
                packageInfo.signingInfo.getApkContentsSigners(), ML_DSA_65_CERT_SHA256_DIGEST);
        assertExpectedSignaturesDigests(
                packageInfo.signingInfo.getSigningCertificateHistory(),
                RSA_2048_SHA256_DIGEST,
                RSA_2048_2_SHA256_DIGEST,
                ML_DSA_65_CERT_SHA256_DIGEST);
    }

    public void testV32_originalKeyAndHybridV32Disabled() throws Exception {
        // If the v3.2 signature scheme is disabled, then the platform should ignore the new
        // hybrid block and fall back to the v3.0 / v3.1 signature blocks; this test verifies that
        // the platform only recognizes the original signer in the v3.0 block when v3.2 is disabled.
        PackageManager packageManager = getContext().getPackageManager();
        PackageInfo packageInfo =
                packageManager.getPackageInfo(
                        TEST_PACKAGE, PackageManager.GET_SIGNING_CERTIFICATES);

        assertFalse(
                packageManager.hasSigningCertificate(
                        TEST_PACKAGE,
                        hexToBytes(ML_DSA_65_CERT_SHA256_DIGEST),
                        PackageManager.CERT_INPUT_SHA256));
        assertFalse(
                packageManager.hasSigningCertificate(
                        TEST_PACKAGE,
                        hexToBytes(RSA_2048_2_SHA256_DIGEST),
                        PackageManager.CERT_INPUT_SHA256));
        assertTrue(
                packageManager.hasSigningCertificate(
                        TEST_PACKAGE,
                        hexToBytes(RSA_2048_SHA256_DIGEST),
                        PackageManager.CERT_INPUT_SHA256));
        assertExpectedSignaturesDigests(
                packageInfo.signingInfo.getApkContentsSigners(), RSA_2048_SHA256_DIGEST);
    }

    public void testV32_v3OriginalV31RotatedV32HybridConfig() throws Exception {
        // Verifies that an APK signed with the original key in the v3.0 block, a rotated key in
        // the v3.1 block, and a hybrid config in the v3.2 block reports the PQC signer as the
        // current signing key and the rest of the keys in signing history.
        PackageManager packageManager = getContext().getPackageManager();
        PackageInfo packageInfo =
                packageManager.getPackageInfo(
                        TEST_PACKAGE, PackageManager.GET_SIGNING_CERTIFICATES);

        assertTrue(
                packageManager.hasSigningCertificate(
                        TEST_PACKAGE,
                        hexToBytes(ML_DSA_65_CERT_SHA256_DIGEST),
                        PackageManager.CERT_INPUT_SHA256));
        assertTrue(
                packageManager.hasSigningCertificate(
                        TEST_PACKAGE,
                        hexToBytes(RSA_2048_3_SHA256_DIGEST),
                        PackageManager.CERT_INPUT_SHA256));
        assertTrue(
                packageManager.hasSigningCertificate(
                        TEST_PACKAGE,
                        hexToBytes(RSA_2048_2_SHA256_DIGEST),
                        PackageManager.CERT_INPUT_SHA256));
        assertTrue(
                packageManager.hasSigningCertificate(
                        TEST_PACKAGE,
                        hexToBytes(RSA_2048_SHA256_DIGEST),
                        PackageManager.CERT_INPUT_SHA256));
        assertExpectedSignaturesDigests(
                packageInfo.signingInfo.getApkContentsSigners(), ML_DSA_65_CERT_SHA256_DIGEST);
        assertExpectedSignaturesDigests(
                packageInfo.signingInfo.getSigningCertificateHistory(),
                RSA_2048_SHA256_DIGEST,
                RSA_2048_2_SHA256_DIGEST,
                RSA_2048_3_SHA256_DIGEST,
                ML_DSA_65_CERT_SHA256_DIGEST);
    }

    public void testV32_v3OriginalV31RotatedV32HybridDisabled() throws Exception {
        // This test verifies if the v3.2 signature scheme is disabled, then an APK signed with
        // v3.0, v3.1, and v3.2 blocks should ignore the v3.2 block and verify the APK using the
        // v3.1 rotated signer.
        PackageManager packageManager = getContext().getPackageManager();
        PackageInfo packageInfo =
                packageManager.getPackageInfo(
                        TEST_PACKAGE, PackageManager.GET_SIGNING_CERTIFICATES);

        assertFalse(
                packageManager.hasSigningCertificate(
                        TEST_PACKAGE,
                        hexToBytes(ML_DSA_65_CERT_SHA256_DIGEST),
                        PackageManager.CERT_INPUT_SHA256));
        assertFalse(
                packageManager.hasSigningCertificate(
                        TEST_PACKAGE,
                        hexToBytes(RSA_2048_3_SHA256_DIGEST),
                        PackageManager.CERT_INPUT_SHA256));
        assertTrue(
                packageManager.hasSigningCertificate(
                        TEST_PACKAGE,
                        hexToBytes(RSA_2048_2_SHA256_DIGEST),
                        PackageManager.CERT_INPUT_SHA256));
        assertTrue(
                packageManager.hasSigningCertificate(
                        TEST_PACKAGE,
                        hexToBytes(RSA_2048_SHA256_DIGEST),
                        PackageManager.CERT_INPUT_SHA256));
        assertExpectedSignaturesDigests(
                packageInfo.signingInfo.getApkContentsSigners(), RSA_2048_2_SHA256_DIGEST);
        assertExpectedSignaturesDigests(
                packageInfo.signingInfo.getSigningCertificateHistory(),
                RSA_2048_SHA256_DIGEST,
                RSA_2048_2_SHA256_DIGEST);
    }

    public void testV32_onlyHybridBlock() throws Exception {
        // If the APK is only signed with the hybrid block, then the platform APIs should see the
        // PQC signer as the current signer and the classical signer as the only other signer in the
        // lineage.
        PackageManager packageManager = getContext().getPackageManager();
        PackageInfo packageInfo =
                packageManager.getPackageInfo(
                        TEST_PACKAGE, PackageManager.GET_SIGNING_CERTIFICATES);

        assertTrue(
                packageManager.hasSigningCertificate(
                        TEST_PACKAGE,
                        hexToBytes(ML_DSA_65_CERT_SHA256_DIGEST),
                        PackageManager.CERT_INPUT_SHA256));
        assertTrue(
                packageManager.hasSigningCertificate(
                        TEST_PACKAGE,
                        hexToBytes(RSA_2048_SHA256_DIGEST),
                        PackageManager.CERT_INPUT_SHA256));
        assertExpectedSignaturesDigests(
                packageInfo.signingInfo.getApkContentsSigners(), ML_DSA_65_CERT_SHA256_DIGEST);
        assertExpectedSignaturesDigests(
                packageInfo.signingInfo.getSigningCertificateHistory(),
                RSA_2048_SHA256_DIGEST,
                ML_DSA_65_CERT_SHA256_DIGEST);
    }

    public void testV32_v3OriginalV32TargetsMaxInt() throws Exception {
        // If the APK is signed with a v3.2 block that targets an SDK range later than that on the
        // device, then the platform should fall back to verifying the APK using one of the previous
        // v3 signature schemes; for this test, the APK is signed with the original key in the v3.0
        // block.
        PackageManager packageManager = getContext().getPackageManager();
        PackageInfo packageInfo =
                packageManager.getPackageInfo(
                        TEST_PACKAGE, PackageManager.GET_SIGNING_CERTIFICATES);

        assertFalse(
                packageManager.hasSigningCertificate(
                        TEST_PACKAGE,
                        hexToBytes(ML_DSA_65_CERT_SHA256_DIGEST),
                        PackageManager.CERT_INPUT_SHA256));
        assertFalse(
                packageManager.hasSigningCertificate(
                        TEST_PACKAGE,
                        hexToBytes(RSA_2048_2_SHA256_DIGEST),
                        PackageManager.CERT_INPUT_SHA256));
        assertTrue(
                packageManager.hasSigningCertificate(
                        TEST_PACKAGE,
                        hexToBytes(RSA_2048_SHA256_DIGEST),
                        PackageManager.CERT_INPUT_SHA256));
        assertExpectedSignaturesDigests(
                packageInfo.signingInfo.getApkContentsSigners(), RSA_2048_SHA256_DIGEST);
    }

    public void testV32_v3OriginalV31RotatedV32SdkRangeOutsideDeviceSdk() throws Exception {
        // If the APK is signed with a v3.2 block that targets an SDK range outside that of the
        // device, then the platform should fall back to verifying the APK using one of the previous
        // v3 signature schemes; for this test, the APK is signed with the original key in the v3.0
        // block and the rotated key in the v3.1 block targeting the platform SDK version.
        PackageManager packageManager = getContext().getPackageManager();
        PackageInfo packageInfo =
                packageManager.getPackageInfo(
                        TEST_PACKAGE, PackageManager.GET_SIGNING_CERTIFICATES);

        assertFalse(
                packageManager.hasSigningCertificate(
                        TEST_PACKAGE,
                        hexToBytes(ML_DSA_65_CERT_SHA256_DIGEST),
                        PackageManager.CERT_INPUT_SHA256));
        assertFalse(
                packageManager.hasSigningCertificate(
                        TEST_PACKAGE,
                        hexToBytes(RSA_2048_3_SHA256_DIGEST),
                        PackageManager.CERT_INPUT_SHA256));
        assertTrue(
                packageManager.hasSigningCertificate(
                        TEST_PACKAGE,
                        hexToBytes(RSA_2048_2_SHA256_DIGEST),
                        PackageManager.CERT_INPUT_SHA256));
        assertTrue(
                packageManager.hasSigningCertificate(
                        TEST_PACKAGE,
                        hexToBytes(RSA_2048_SHA256_DIGEST),
                        PackageManager.CERT_INPUT_SHA256));
        assertExpectedSignaturesDigests(
                packageInfo.signingInfo.getApkContentsSigners(), RSA_2048_2_SHA256_DIGEST);
        assertExpectedSignaturesDigests(
                packageInfo.signingInfo.getSigningCertificateHistory(),
                RSA_2048_SHA256_DIGEST,
                RSA_2048_2_SHA256_DIGEST);
    }

    public void testV32_v3OriginalV32HybridV31RotatedConfig() throws Exception {
        // Verifies that an APK signed with the original key in the v3.0 block, both previous hybrid
        // signers in the lineage, and a a rotated key in the v3.1 block reports the new single
        // classical signer as the current signing key and the rest of the keys in the signing
        // history.
        PackageManager packageManager = getContext().getPackageManager();
        PackageInfo packageInfo =
                packageManager.getPackageInfo(
                        TEST_PACKAGE, PackageManager.GET_SIGNING_CERTIFICATES);

        assertTrue(
                packageManager.hasSigningCertificate(
                        TEST_PACKAGE,
                        hexToBytes(RSA_2048_3_SHA256_DIGEST),
                        PackageManager.CERT_INPUT_SHA256));
        assertTrue(
                packageManager.hasSigningCertificate(
                        TEST_PACKAGE,
                        hexToBytes(ML_DSA_65_CERT_SHA256_DIGEST),
                        PackageManager.CERT_INPUT_SHA256));
        assertTrue(
                packageManager.hasSigningCertificate(
                        TEST_PACKAGE,
                        hexToBytes(RSA_2048_2_SHA256_DIGEST),
                        PackageManager.CERT_INPUT_SHA256));
        assertTrue(
                packageManager.hasSigningCertificate(
                        TEST_PACKAGE,
                        hexToBytes(RSA_2048_SHA256_DIGEST),
                        PackageManager.CERT_INPUT_SHA256));
        assertExpectedSignaturesDigests(
                packageInfo.signingInfo.getApkContentsSigners(), RSA_2048_3_SHA256_DIGEST);
        assertExpectedSignaturesDigests(
                packageInfo.signingInfo.getSigningCertificateHistory(),
                RSA_2048_SHA256_DIGEST,
                RSA_2048_2_SHA256_DIGEST,
                ML_DSA_65_CERT_SHA256_DIGEST,
                RSA_2048_3_SHA256_DIGEST);
    }

    public void testV32_v3OriginalConfig() throws Exception {
        // Verifies that a rollback from a V3.2 hybrid config to the original signer only returns
        // the original signer as the signing identity.
        PackageManager packageManager = getContext().getPackageManager();
        PackageInfo packageInfo =
                packageManager.getPackageInfo(
                        TEST_PACKAGE, PackageManager.GET_SIGNING_CERTIFICATES);

        assertFalse(
                packageManager.hasSigningCertificate(
                        TEST_PACKAGE,
                        hexToBytes(RSA_2048_2_SHA256_DIGEST),
                        PackageManager.CERT_INPUT_SHA256));
        assertFalse(
                packageManager.hasSigningCertificate(
                        TEST_PACKAGE,
                        hexToBytes(ML_DSA_65_CERT_SHA256_DIGEST),
                        PackageManager.CERT_INPUT_SHA256));
        assertTrue(
                packageManager.hasSigningCertificate(
                        TEST_PACKAGE,
                        hexToBytes(RSA_2048_SHA256_DIGEST),
                        PackageManager.CERT_INPUT_SHA256));
        assertExpectedSignaturesDigests(
                packageInfo.signingInfo.getApkContentsSigners(), RSA_2048_SHA256_DIGEST);
    }

    public void testV32_v31RotatedConfig() throws Exception {
        // Verifies that a rollback from a V3.2 hybrid config to the rotated signer only returns
        // the rotated and original signatures.
        PackageManager packageManager = getContext().getPackageManager();
        PackageInfo packageInfo =
                packageManager.getPackageInfo(
                        TEST_PACKAGE, PackageManager.GET_SIGNING_CERTIFICATES);

        assertFalse(
                packageManager.hasSigningCertificate(
                        TEST_PACKAGE,
                        hexToBytes(RSA_2048_3_SHA256_DIGEST),
                        PackageManager.CERT_INPUT_SHA256));
        assertFalse(
                packageManager.hasSigningCertificate(
                        TEST_PACKAGE,
                        hexToBytes(ML_DSA_65_CERT_SHA256_DIGEST),
                        PackageManager.CERT_INPUT_SHA256));
        assertTrue(
                packageManager.hasSigningCertificate(
                        TEST_PACKAGE,
                        hexToBytes(RSA_2048_2_SHA256_DIGEST),
                        PackageManager.CERT_INPUT_SHA256));
        assertTrue(
                packageManager.hasSigningCertificate(
                        TEST_PACKAGE,
                        hexToBytes(RSA_2048_SHA256_DIGEST),
                        PackageManager.CERT_INPUT_SHA256));
        assertExpectedSignaturesDigests(
                packageInfo.signingInfo.getApkContentsSigners(), RSA_2048_2_SHA256_DIGEST);
        assertExpectedSignaturesDigests(
                packageInfo.signingInfo.getSigningCertificateHistory(),
                RSA_2048_SHA256_DIGEST,
                RSA_2048_2_SHA256_DIGEST);
    }

    public void testV32_v3OriginalV32RotatedConfig() throws Exception {
        // This test verifies that all of the expected signatures are in the lineage for a package
        // that has been updated with a v3 original signer, a v3.2 original signer, and a v3.2
        // rotated signer. The platform verifies that both of the original v3.2 signers are in the
        // lineage when updating to a rotated v3.2 config.
        PackageManager packageManager = getContext().getPackageManager();
        PackageInfo packageInfo =
                packageManager.getPackageInfo(
                        TEST_PACKAGE, PackageManager.GET_SIGNING_CERTIFICATES);

        assertTrue(
                packageManager.hasSigningCertificate(
                        TEST_PACKAGE,
                        hexToBytes(ML_DSA_87_CERT_SHA256_DIGEST),
                        PackageManager.CERT_INPUT_SHA256));
        assertTrue(
                packageManager.hasSigningCertificate(
                        TEST_PACKAGE,
                        hexToBytes(RSA_2048_3_SHA256_DIGEST),
                        PackageManager.CERT_INPUT_SHA256));
        assertTrue(
                packageManager.hasSigningCertificate(
                        TEST_PACKAGE,
                        hexToBytes(ML_DSA_65_CERT_SHA256_DIGEST),
                        PackageManager.CERT_INPUT_SHA256));
        assertTrue(
                packageManager.hasSigningCertificate(
                        TEST_PACKAGE,
                        hexToBytes(RSA_2048_2_SHA256_DIGEST),
                        PackageManager.CERT_INPUT_SHA256));
        assertTrue(
                packageManager.hasSigningCertificate(
                        TEST_PACKAGE,
                        hexToBytes(RSA_2048_SHA256_DIGEST),
                        PackageManager.CERT_INPUT_SHA256));
        assertExpectedSignaturesDigests(
                packageInfo.signingInfo.getApkContentsSigners(), ML_DSA_87_CERT_SHA256_DIGEST);
        assertExpectedSignaturesDigests(
                packageInfo.signingInfo.getSigningCertificateHistory(),
                RSA_2048_SHA256_DIGEST,
                RSA_2048_2_SHA256_DIGEST,
                ML_DSA_65_CERT_SHA256_DIGEST,
                RSA_2048_3_SHA256_DIGEST,
                ML_DSA_87_CERT_SHA256_DIGEST);
    }

    public void testV32_v32UpdateHybridToSingleToHybridConfig() throws Exception {
        // This test verifies that all of the expected signatures are in the lineage for a package
        // that has been updated with a v3 original signer, a v3.2 original signer, a v3.1 rotated
        // signer, and a v3.2 rotated signer.
        PackageManager packageManager = getContext().getPackageManager();
        PackageInfo packageInfo =
                packageManager.getPackageInfo(
                        TEST_PACKAGE, PackageManager.GET_SIGNING_CERTIFICATES);

        assertTrue(
                packageManager.hasSigningCertificate(
                        TEST_PACKAGE,
                        hexToBytes(ML_DSA_87_CERT_SHA256_DIGEST),
                        PackageManager.CERT_INPUT_SHA256));
        assertTrue(
                packageManager.hasSigningCertificate(
                        TEST_PACKAGE,
                        hexToBytes(RSA_2048_4_SHA256_DIGEST),
                        PackageManager.CERT_INPUT_SHA256));
        assertTrue(
                packageManager.hasSigningCertificate(
                        TEST_PACKAGE,
                        hexToBytes(RSA_2048_3_SHA256_DIGEST),
                        PackageManager.CERT_INPUT_SHA256));
        assertTrue(
                packageManager.hasSigningCertificate(
                        TEST_PACKAGE,
                        hexToBytes(ML_DSA_65_CERT_SHA256_DIGEST),
                        PackageManager.CERT_INPUT_SHA256));
        assertTrue(
                packageManager.hasSigningCertificate(
                        TEST_PACKAGE,
                        hexToBytes(RSA_2048_2_SHA256_DIGEST),
                        PackageManager.CERT_INPUT_SHA256));
        assertTrue(
                packageManager.hasSigningCertificate(
                        TEST_PACKAGE,
                        hexToBytes(RSA_2048_SHA256_DIGEST),
                        PackageManager.CERT_INPUT_SHA256));
        assertExpectedSignaturesDigests(
                packageInfo.signingInfo.getApkContentsSigners(), ML_DSA_87_CERT_SHA256_DIGEST);
        assertExpectedSignaturesDigests(
                packageInfo.signingInfo.getSigningCertificateHistory(),
                RSA_2048_SHA256_DIGEST,
                RSA_2048_2_SHA256_DIGEST,
                ML_DSA_65_CERT_SHA256_DIGEST,
                RSA_2048_3_SHA256_DIGEST,
                RSA_2048_4_SHA256_DIGEST,
                ML_DSA_87_CERT_SHA256_DIGEST);
    }

    public void testV32_companionPackageGrantedPerm() throws Exception {
        PackageManager pm = getContext().getPackageManager();
        assertTrue(
                pm.checkPermission(PERMISSION_NAME, COMPANION_PACKAGE)
                        == PackageManager.PERMISSION_GRANTED);
    }

    public void testV32_companionPackageDeniedPerm() throws Exception {
        PackageManager pm = getContext().getPackageManager();
        assertFalse(
                pm.checkPermission(PERMISSION_NAME, COMPANION_PACKAGE)
                        == PackageManager.PERMISSION_GRANTED);
    }

    /**
     * Asserts that the provided {@code signatures} array contains the specified {@code
     * expectedSignatureDigests}; this method can be used for assertions for both the current signer
     * as well as the signing lineage.
     *
     * <p>Note, this method will check that the signatures are in the provided order; this is
     * particularly required for the hybrid scheme since the classical and PQC signers are expected
     * to be the last two signers, in that order, in the lineage.
     */
    private static void assertExpectedSignaturesDigests(
            Signature[] signatures, String... expectedSignaturesDigests) throws Exception {
        assertEquals(expectedSignaturesDigests.length, signatures.length);

        Set<String> expectedSignatureSet = new HashSet<>(Arrays.asList(expectedSignaturesDigests));
        for (int i = 0; i < signatures.length; i++) {
            String reportedCertDigest =
                    bytesToHex(computeSha256DigestBytes(signatures[i].toByteArray()));
            assertEquals(expectedSignaturesDigests[i], reportedCertDigest);
        }
    }

    /** Returns the sha256 digest for the provided {@code data}. */
    private static byte[] computeSha256DigestBytes(byte[] data) throws NoSuchAlgorithmException {
        MessageDigest messageDigest = MessageDigest.getInstance("SHA256");
        messageDigest.update(data);
        return messageDigest.digest();
    }

    /** Returns a String hex representation of the provided {@code bytes} array. */
    private static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int byteValue = bytes[i] & 0xFF;
            hexChars[i * 2] = HEX_VALUES[byteValue >>> 4];
            hexChars[i * 2 + 1] = HEX_VALUES[byteValue & 0x0F];
        }
        return new String(hexChars);
    }

    /** Returns the byte array containing the byte values for the provided {@code hex} String. */
    private static byte[] hexToBytes(String hex) {
        if (hex == null || hex.length() == 0 || hex.length() % 2 != 0) {
            return null;
        }
        final char[] chars = hex.toCharArray();
        final byte[] bytes = new byte[chars.length / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] =
                    (byte)
                            (((Character.digit(chars[i * 2], 16) << 4) & 0xF0)
                                    | (Character.digit(chars[i * 2 + 1], 16) & 0x0F));
        }
        return bytes;
    }
}
