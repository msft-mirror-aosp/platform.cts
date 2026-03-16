/*
 * Copyright 2013 The Android Open Source Project
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

package android.keystore.cts;

import static com.google.common.truth.Truth.assertThat;

import static android.keystore.cts.util.TestUtils.assumeKmAlgorithmSupport;
import static android.keystore.cts.util.TestUtils.KmType;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.content.pm.PackageManager;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.keystore.cts.util.AndroidKeyStoreTestInputs.EcInputs;
import android.keystore.cts.util.AndroidKeyStoreTestInputs.Ed25519Inputs;
import android.keystore.cts.util.AndroidKeyStoreTestInputs.MlDsa65Inputs;
import android.keystore.cts.util.AndroidKeyStoreTestInputs.RsaInputs;
import android.keystore.cts.util.ImportedKey;
import android.keystore.cts.util.TestUtils;
import android.security.KeyPairGeneratorSpec;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.security.keystore.KeyProtection;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.LargeTest;

import com.android.bedstead.nene.annotations.Nullable;
import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.CddTest;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import junitparams.naming.TestCaseName;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.lang.Math;
import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStore.Entry;
import java.security.KeyStore.PrivateKeyEntry;
import java.security.KeyStore.TrustedCertificateEntry;
import java.security.KeyStoreException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.interfaces.ECKey;
import java.security.interfaces.RSAKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.security.auth.x500.X500Principal;

@RunWith(JUnitParamsRunner.class)
public class AndroidKeyStoreTest {
    private static final String TAG = AndroidKeyStoreTest.class.getSimpleName();

    private KeyStore mKeyStore;

    // Use methods so that we get a different object each time for the different aliases.
    // This helps flush out any bugs where we might have been using == instead of .equals().
    private static String getTestAlias1() {
        return new String("test1");
    }

    private static String getTestAlias2() {
        return new String("test2");
    }

    private static String getTestAlias3() {
        return new String("test3");
    }

    private static int getMaxThreadCount() {
        // Not including 1 operation reserved for passwords.
        // https://source.android.com/docs/security/features/keystore/implementer-ref#begin
        int apiSpecMaxConcurrentOps = 15;
        int availableCPUs = Runtime.getRuntime().availableProcessors();
        return Math.min(apiSpecMaxConcurrentOps, availableCPUs);
    }

    // The maximum amount of time the "large number of keys" tests will spend on importing keys
    // into key store. This is used as a time box so that lower-power devices don't take too long
    // to run the tests.
    private Duration mMaxImportDuration;

    private static final Map<String, byte[]> FAKE_CA = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private static final Map<String, byte[]> FAKE_KEY =
            new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private static final Map<String, byte[]> FAKE_USER =
            new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    static {
        FAKE_CA.put("RSA", HexEncoding.decode(RsaInputs.FAKE_CA_HEX));
        FAKE_CA.put("EC", HexEncoding.decode(EcInputs.FAKE_CA_HEX));
        FAKE_CA.put("ED25519", HexEncoding.decode(Ed25519Inputs.FAKE_CA_HEX));
        FAKE_CA.put("ML-DSA-65", HexEncoding.decode(MlDsa65Inputs.FAKE_CA_HEX));

        FAKE_KEY.put("RSA", HexEncoding.decode(RsaInputs.FAKE_KEY_HEX));
        FAKE_KEY.put("EC", HexEncoding.decode(EcInputs.FAKE_KEY_HEX));
        FAKE_KEY.put("ED25519", HexEncoding.decode(Ed25519Inputs.FAKE_KEY_HEX));
        FAKE_KEY.put("ML-DSA-65", HexEncoding.decode(MlDsa65Inputs.FAKE_KEY_HEX));

        FAKE_USER.put("RSA", HexEncoding.decode(RsaInputs.FAKE_USER_HEX));
        FAKE_USER.put("EC", HexEncoding.decode(EcInputs.FAKE_USER_HEX));
        FAKE_USER.put("ED25519", HexEncoding.decode(Ed25519Inputs.FAKE_USER_HEX));
        FAKE_USER.put("ML-DSA-65", HexEncoding.decode(MlDsa65Inputs.FAKE_USER_HEX));
    }

    // The parameters used in parameterized test names must match a regex that doesn't allow dashes.
    // This means algorithm names like "ML-DSA-65" can't be used and we add a third parameter that's
    // only used to generate the test name.
    private static Object[] getAlgorithms() {
        // In each nested array:
        //   - First element is the string to include in the test name.
        //   - Second element is the Java Security Standard Algorithm Name (not case sensitive)
        return new Object[][] {
            {"RSA", "RSA"},
            {"EC", "EC"},
            {"ED25519", "Ed25519"},
            {"MLDSA65", "ML-DSA-65"},
        };
    }

    /** The amount of time to allow before and after expected time for variance in timing tests. */
    private static final long SLOP_TIME_MILLIS = 15000L;

    private Context getContext() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setUp() throws Exception {
        // Wipe any existing entries in the KeyStore
        KeyStore ksTemp = KeyStore.getInstance("AndroidKeyStore");
        ksTemp.load(null, null);
        Enumeration<String> aliases = ksTemp.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            ksTemp.deleteEntry(alias);
        }

        // Get a new instance because some tests need it uninitialized
        mKeyStore = KeyStore.getInstance("AndroidKeyStore");

        // Use a longer timeout on watches, which are generally less performant.
        mMaxImportDuration =
                getContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH)
                        ? LARGE_NUMBER_OF_KEYS_TEST_MAX_DURATION_WATCH
                        : LARGE_NUMBER_OF_KEYS_TEST_MAX_DURATION;
    }

    @After
    public void tearDown() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null, null);
        Enumeration<String> aliases = keyStore.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            keyStore.deleteEntry(alias);
        }
    }

    private PrivateKey generatePrivateKey(String algorithm) throws Exception {
        KeyFactory kf = KeyFactory.getInstance(algorithm);
        return kf.generatePrivate(new PKCS8EncodedKeySpec(FAKE_KEY.get(algorithm)));
    }

    private Certificate generateUserCertificate(String algorithm) throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        return cf.generateCertificate(new ByteArrayInputStream(FAKE_USER.get(algorithm)));
    }

    private Certificate generateCaCertificate(String algorithm) throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        return cf.generateCertificate(new ByteArrayInputStream(FAKE_CA.get(algorithm)));
    }

    private PrivateKeyEntry makeUserKey(String algorithm) throws Exception {
        return new KeyStore.PrivateKeyEntry(
                generatePrivateKey(algorithm),
                new Certificate[] {
                    generateUserCertificate(algorithm), generateCaCertificate(algorithm)
                });
    }

    private Entry makeCa(String algorithm) throws Exception {
        return new KeyStore.TrustedCertificateEntry(generateCaCertificate(algorithm));
    }

    private void assertAliases(final String[] expectedAliases) throws KeyStoreException {
        final Enumeration<String> aliases = mKeyStore.aliases();
        int count = 0;

        final Set<String> expectedSet = new HashSet<String>();
        expectedSet.addAll(Arrays.asList(expectedAliases));

        while (aliases.hasMoreElements()) {
            count++;
            final String alias = aliases.nextElement();
            assertTrue("The alias should be in the expected set", expectedSet.contains(alias));
            expectedSet.remove(alias);
        }
        assertTrue(
                "The expected set and actual set should be exactly equal", expectedSet.isEmpty());
        assertEquals(
                "There should be the correct number of keystore entries",
                expectedAliases.length,
                count);
    }

    private void deleteEntryIfNotNull(@Nullable String alias) throws Exception {
        if (alias != null) {
            mKeyStore.deleteEntry(alias);
        }
    }

    // Previously called testKeyStore_Aliases_Unencrypted_Success
    @Test
    @Parameters(method = "getAlgorithms")
    @TestCaseName(value = "{method}_{0}")
    public void aliases_success(String algorithmNameForTest, String algorithm) throws Exception {
        assumeKmAlgorithmSupport(KmType.TEE, algorithm);
        mKeyStore.load(null, null);
        assertAliases(new String[] {});

        mKeyStore.setEntry(getTestAlias1(), makeUserKey(algorithm), null);
        assertAliases(new String[] {getTestAlias1()});

        mKeyStore.setEntry(getTestAlias2(), makeCa(algorithm), null);
        assertAliases(new String[] {getTestAlias1(), getTestAlias2()});
    }

    // Previously called testKeyStore_Aliases_NotInitialized_Unencrypted_Failure
    @Test
    public void aliases_keystoreNotInitialized_throwsException() throws Exception {
        assertThrows(
                "KeyStore should throw an exception when not initialized",
                KeyStoreException.class,
                () -> mKeyStore.aliases());
    }

    // Previously called testKeyStore_ContainsAliases_PrivateAndCA_Unencrypted_Success
    @Test
    @Parameters(method = "getAlgorithms")
    @TestCaseName(value = "{method}_{0}")
    public void containsAlias_privateKeyAndCA_success(String algorithmNameForTest, String algorithm)
            throws Exception {
        assumeKmAlgorithmSupport(KmType.TEE, algorithm);
        mKeyStore.load(null, null);
        assertAliases(new String[] {});

        mKeyStore.setEntry(getTestAlias1(), makeUserKey(algorithm), null);
        assertTrue("Should contain added private key", mKeyStore.containsAlias(getTestAlias1()));

        mKeyStore.setEntry(getTestAlias2(), makeCa(algorithm), null);
        assertTrue("Should contain added CA certificate", mKeyStore.containsAlias(getTestAlias2()));
    }

    // Previously called testKeyStore_ContainsAliases_CAOnly_Unencrypted_Success
    @Test
    @Parameters(method = "getAlgorithms")
    @TestCaseName(value = "{method}_{0}")
    public void containsAlias_CA_success(String algorithmNameForTest, String algorithm)
            throws Exception {
        assumeKmAlgorithmSupport(KmType.TEE, algorithm);
        mKeyStore.load(null, null);
        mKeyStore.setEntry(getTestAlias1(), makeCa(algorithm), null);
        assertTrue("Should contain added CA certificate", mKeyStore.containsAlias(getTestAlias1()));
    }

    // Previously called testKeyStore_ContainsAliases_NonExistent_Unencrypted_Failure
    @Test
    public void containsAlias_nonExistent_returnsFalse() throws Exception {
        mKeyStore.load(null, null);
        assertFalse(
                "Should not contain non-existent alias", mKeyStore.containsAlias(getTestAlias1()));
    }

    // Previously called testKeyStore_DeleteEntry_Unencrypted_Success
    @Test
    @Parameters(method = "getAlgorithms")
    @TestCaseName(value = "{method}_{0}")
    public void deleteEntry_success(String algorithmNameForTest, String algorithm)
            throws Exception {
        assumeKmAlgorithmSupport(KmType.TEE, algorithm);
        mKeyStore.load(null, null);
        mKeyStore.setEntry(getTestAlias1(), makeUserKey(algorithm), null);
        mKeyStore.setCertificateEntry(getTestAlias2(), generateCaCertificate(algorithm));
        mKeyStore.setCertificateEntry(getTestAlias3(), generateCaCertificate(algorithm));
        assertAliases(new String[] {getTestAlias1(), getTestAlias2(), getTestAlias3()});

        mKeyStore.deleteEntry(getTestAlias1());
        assertAliases(new String[] {getTestAlias2(), getTestAlias3()});

        mKeyStore.deleteEntry(getTestAlias3());
        assertAliases(new String[] {getTestAlias2()});

        mKeyStore.deleteEntry(getTestAlias2());
        assertAliases(new String[] {});
    }

    // Previously called testKeyStore_DeleteEntry_EmptyStore_Unencrypted_Success
    @Test
    public void deleteEntry_emptyKeystore_success() throws Exception {
        mKeyStore.load(null, null);
        mKeyStore.deleteEntry(getTestAlias1()); // Should not throw.
    }

    // Previously called testKeyStore_DeleteEntry_NonExistent_Unencrypted_Success
    @Test
    @Parameters(method = "getAlgorithms")
    @TestCaseName(value = "{method}_{0}")
    public void deleteEntry_nonExistent_success(String algorithmNameForTest, String algorithm)
            throws Exception {
        assumeKmAlgorithmSupport(KmType.TEE, algorithm);
        mKeyStore.load(null, null);
        mKeyStore.setEntry(getTestAlias1(), makeUserKey(algorithm), null);
        mKeyStore.deleteEntry(getTestAlias2()); // Should not throw.
    }

    // Previously called testKeyStore_GetCertificate_Single_Unencrypted_Success
    @Test
    @Parameters(method = "getAlgorithms")
    @TestCaseName(value = "{method}_{0}")
    public void getCertificate_success(String algorithmNameForTest, String algorithm)
            throws Exception {
        assumeKmAlgorithmSupport(KmType.TEE, algorithm);
        mKeyStore.load(null, null);
        mKeyStore.setCertificateEntry(getTestAlias1(), generateCaCertificate(algorithm));
        assertAliases(new String[] {getTestAlias1()});
        assertNull(
                "Certificate should not exist in keystore",
                mKeyStore.getCertificate(getTestAlias2()));

        Certificate retrieved = mKeyStore.getCertificate(getTestAlias1());
        assertNotNull("Retrieved certificate should not be null", retrieved);

        CertificateFactory f = CertificateFactory.getInstance("X.509");
        Certificate actual =
                f.generateCertificate(new ByteArrayInputStream(FAKE_CA.get(algorithm)));
        assertEquals("Actual and retrieved certificates should be the same", actual, retrieved);
    }

    // Previously called testKeyStore_GetCertificate_NonExist_Unencrypted_Failure
    @Test
    public void getCertificate_nonExistent_returnsNull() throws Exception {
        mKeyStore.load(null, null);
        assertNull(
                "Certificate should not exist in keystore",
                mKeyStore.getCertificate(getTestAlias1()));
    }

    // Previously called testKeyStore_GetCertificateAlias_CAEntry_Unencrypted_Success
    @Test
    @Parameters(method = "getAlgorithms")
    @TestCaseName(value = "{method}_{0}")
    public void getCertificateAlias_CAEntry_success(String algorithmNameForTest, String algorithm)
            throws Exception {
        assumeKmAlgorithmSupport(KmType.TEE, algorithm);
        mKeyStore.load(null, null);
        Certificate cert = generateCaCertificate(algorithm);
        mKeyStore.setCertificateEntry(getTestAlias1(), cert);
        assertEquals(
                "Stored certificate alias should be found",
                getTestAlias1(),
                mKeyStore.getCertificateAlias(cert));
    }

    // Previously called testKeyStore_GetCertificateAlias_PrivateKeyEntry_Unencrypted_Success
    @Test
    @Parameters(method = "getAlgorithms")
    @TestCaseName(value = "{method}_{0}")
    public void getCertificateAlias_privateKeyEntry_success(
            String algorithmNameForTest, String algorithm) throws Exception {
        assumeKmAlgorithmSupport(KmType.TEE, algorithm);
        mKeyStore.load(null, null);
        mKeyStore.setEntry(getTestAlias1(), makeUserKey(algorithm), null);
        CertificateFactory f = CertificateFactory.getInstance("X.509");
        Certificate actual =
                f.generateCertificate(new ByteArrayInputStream(FAKE_USER.get(algorithm)));
        assertEquals(
                "Stored certificate alias should be found",
                getTestAlias1(),
                mKeyStore.getCertificateAlias(actual));
    }

    // Previously called
    // testKeyStore_GetCertificateAlias_CAEntry_WithPrivateKeyUsingCA_Unencrypted_Success
    @Test
    @Parameters(method = "getAlgorithms")
    @TestCaseName(value = "{method}_{0}")
    public void getCertificateAlias_CAEntryAndPrivateKeyEntryUsingCA_success(
            String algorithmNameForTest, String algorithm) throws Exception {
        assumeKmAlgorithmSupport(KmType.TEE, algorithm);
        mKeyStore.load(null, null);
        Certificate actual = generateCaCertificate(algorithm);

        // Insert TrustedCertificateEntry with CA name
        mKeyStore.setCertificateEntry(getTestAlias2(), actual);

        // Insert PrivateKeyEntry that uses the same CA
        mKeyStore.setEntry(getTestAlias1(), makeUserKey(algorithm), null);

        assertEquals(
                "Stored certificate alias should be found",
                getTestAlias2(),
                mKeyStore.getCertificateAlias(actual));
    }

    // Previously called testKeyStore_GetCertificateAlias_NonExist_Empty_Unencrypted_Failure
    @Test
    @Parameters(method = "getAlgorithms")
    @TestCaseName(value = "{method}_{0}")
    public void getCertificateAlias_emptyKeystore_returnsNull(
            String algorithmNameForTest, String algorithm) throws Exception {
        assumeKmAlgorithmSupport(KmType.TEE, algorithm);
        mKeyStore.load(null, null);
        CertificateFactory f = CertificateFactory.getInstance("X.509");
        Certificate actual =
                f.generateCertificate(new ByteArrayInputStream(FAKE_CA.get(algorithm)));
        assertNull(
                "Non-existent certificate alias should not be found",
                mKeyStore.getCertificateAlias(actual));
    }

    // Previously called testKeyStore_GetCertificateAlias_NonExist_Unencrypted_Failure
    @Test
    @Parameters(method = "getAlgorithms")
    @TestCaseName(value = "{method}_{0}")
    public void getCertificateAlias_nonExistent_returnsNull(
            String algorithmNameForTest, String algorithm) throws Exception {
        assumeKmAlgorithmSupport(KmType.TEE, algorithm);
        mKeyStore.load(null, null);

        // Insert TrustedCertificateEntry with CA name
        Certificate ca = generateCaCertificate(algorithm);
        mKeyStore.setCertificateEntry(getTestAlias1(), ca);

        // Generate a certificate but don't insert it in the KeyStore.
        Certificate userCert = generateUserCertificate(algorithm);

        assertNull(
                "Non-existent certificate alias should not be found",
                mKeyStore.getCertificateAlias(userCert));
    }

    // Previously called testKeyStore_GetCertificateChain_SingleLength_Unencrypted_Success
    @Test
    @Parameters(method = "getAlgorithms")
    @TestCaseName(value = "{method}_{0}")
    public void getCertificateChain_success(String algorithmNameForTest, String algorithm)
            throws Exception {
        assumeKmAlgorithmSupport(KmType.TEE, algorithm);
        mKeyStore.load(null, null);
        mKeyStore.setEntry(getTestAlias1(), makeUserKey(algorithm), null);

        Certificate[] expected = new Certificate[2];
        expected[0] = generateUserCertificate(algorithm);
        expected[1] = generateCaCertificate(algorithm);

        Certificate[] actual = mKeyStore.getCertificateChain(getTestAlias1());

        assertNotNull("Returned certificate chain should not be null", actual);
        assertEquals(
                "Returned certificate chain should be correct size",
                expected.length,
                actual.length);
        assertEquals("First certificate should be user certificate", expected[0], actual[0]);
        assertEquals("Second certificate should be CA certificate", expected[1], actual[1]);

        // Negative test when keystore is populated.
        assertNull(
                "Stored certificate alias should not be found",
                mKeyStore.getCertificateChain(getTestAlias2()));
    }

    // Previously called testKeyStore_GetCertificateChain_NonExist_Unencrypted_Failure
    @Test
    public void getCertificateChain_nonExistent_returnsNull() throws Exception {
        mKeyStore.load(null, null);
        assertNull(
                "Stored certificate alias should not be found",
                mKeyStore.getCertificateChain(getTestAlias1()));
    }

    // Previously called testKeyStore_GetCreationDate_PrivateKeyEntry_Unencrypted_Success
    @Test
    @Parameters(method = "getAlgorithms")
    @TestCaseName(value = "{method}_{0}")
    public void getCreationDate_privateKey_success(String algorithmNameForTest, String algorithm)
            throws Exception {
        assumeKmAlgorithmSupport(KmType.TEE, algorithm);
        mKeyStore.load(null, null);

        // getTestAlias1()
        mKeyStore.setEntry(getTestAlias1(), makeUserKey(algorithm), null);

        Date now = new Date();
        Date actual = mKeyStore.getCreationDate(getTestAlias1());

        Date expectedAfter = new Date(now.getTime() - SLOP_TIME_MILLIS);
        Date expectedBefore = new Date(now.getTime() + SLOP_TIME_MILLIS);

        assertTrue("Time should be close to current time", actual.before(expectedBefore));
        assertTrue("Time should be close to current time", actual.after(expectedAfter));
    }

    // Previously called testKeyStore_GetCreationDate_CAEntry_Unencrypted_Success
    @Test
    @Parameters(method = "getAlgorithms")
    @TestCaseName(value = "{method}_{0}")
    public void getCreationDate_CA_success(String algorithmNameForTest, String algorithm)
            throws Exception {
        assumeKmAlgorithmSupport(KmType.TEE, algorithm);
        mKeyStore.load(null, null);

        // Insert TrustedCertificateEntry with CA name
        mKeyStore.setCertificateEntry(getTestAlias1(), generateCaCertificate(algorithm));

        Date now = new Date();
        Date actual = mKeyStore.getCreationDate(getTestAlias1());
        assertNotNull("Certificate should be found", actual);

        Date expectedAfter = new Date(now.getTime() - SLOP_TIME_MILLIS);
        Date expectedBefore = new Date(now.getTime() + SLOP_TIME_MILLIS);

        assertTrue("Time should be close to current time", actual.before(expectedBefore));
        assertTrue("Time should be close to current time", actual.after(expectedAfter));
    }

    // Replaces three previous tests:
    //   - testKeyStore_GetEntry_EC_NullParams_Unencrypted_Success
    //   - testKeyStore_GetEntry_RSA_NullParams_Unencrypted_Success
    //   - testKeyStore_GetEntry_NullParams_Unencrypted_Success
    @Test
    @Parameters(method = "getAlgorithms")
    @TestCaseName(value = "{method}_{0}")
    public void getEntry_nullParams_success(String algorithmNameForTest, String algorithm)
            throws Exception {
        assumeKmAlgorithmSupport(KmType.TEE, algorithm);
        mKeyStore.load(null, null);
        mKeyStore.setEntry(getTestAlias1(), makeUserKey(algorithm), null);

        Entry entry = mKeyStore.getEntry(getTestAlias1(), null);
        assertNotNull("Entry should exist", entry);
        assertTrue("Should be a PrivateKeyEntry", entry instanceof PrivateKeyEntry);

        PrivateKeyEntry keyEntry = (PrivateKeyEntry) entry;
        assertPrivateKeyEntryEquals(
                keyEntry,
                algorithm,
                FAKE_KEY.get(algorithm),
                FAKE_USER.get(algorithm),
                FAKE_CA.get(algorithm));
    }

    @SuppressWarnings("unchecked")
    private void assertPrivateKeyEntryEquals(
            PrivateKeyEntry keyEntry, String algorithm, byte[] key, byte[] cert, byte[] ca)
            throws Exception {
        KeyFactory keyFact = KeyFactory.getInstance(algorithm);
        PrivateKey expectedKey = keyFact.generatePrivate(new PKCS8EncodedKeySpec(key));

        CertificateFactory certFact = CertificateFactory.getInstance("X.509");
        Certificate expectedCert = certFact.generateCertificate(new ByteArrayInputStream(cert));

        final Collection<Certificate> expectedChain;
        if (ca != null) {
            expectedChain =
                    (Collection<Certificate>)
                            certFact.generateCertificates(new ByteArrayInputStream(ca));
        } else {
            expectedChain = null;
        }

        assertPrivateKeyEntryEquals(keyEntry, expectedKey, expectedCert, expectedChain);
    }

    private void assertPrivateKeyEntryEquals(
            PrivateKeyEntry keyEntry,
            PrivateKey expectedKey,
            Certificate expectedCert,
            Collection<Certificate> expectedChain)
            throws Exception {
        final PrivateKey privKey = keyEntry.getPrivateKey();
        final PublicKey pubKey = keyEntry.getCertificate().getPublicKey();

        if (expectedKey instanceof ECKey) {
            assertTrue(
                    "Returned PrivateKey " + privKey.getClass() + " should be instanceof ECKey",
                    privKey instanceof ECKey);
            assertEquals(
                    "Returned PrivateKey should be what we inserted",
                    ((ECKey) expectedKey).getParams().getCurve(),
                    ((ECKey) privKey).getParams().getCurve());
        } else if (expectedKey instanceof RSAKey) {
            assertTrue(
                    "Returned PrivateKey " + privKey.getClass() + " should be instanceof RSAKey",
                    privKey instanceof RSAKey);
            assertEquals(
                    "Returned PrivateKey should be what we inserted",
                    ((RSAKey) expectedKey).getModulus(),
                    ((RSAKey) privKey).getModulus());
        }

        assertNull("getFormat() should return null", privKey.getFormat());
        assertNull("getEncoded() should return null", privKey.getEncoded());

        assertEquals("Public keys should be in X.509 format", "X.509", pubKey.getFormat());
        assertNotNull("Public keys should be encodable", pubKey.getEncoded());

        assertEquals(
                "Returned Certificate should be what we inserted",
                expectedCert,
                keyEntry.getCertificate());

        Certificate[] actualChain = keyEntry.getCertificateChain();

        assertEquals(
                "First certificate in chain should be user cert", expectedCert, actualChain[0]);

        if (expectedChain == null) {
            assertEquals("Certificate chain should not include CAs", 1, actualChain.length);
        } else {
            assertEquals(
                    "Chains should be the same size", expectedChain.size() + 1, actualChain.length);
            int i = 1;
            final Iterator<Certificate> it = expectedChain.iterator();
            while (it.hasNext() && i < actualChain.length) {
                assertEquals(
                        "CA chain certificate should equal what we put in",
                        it.next(),
                        actualChain[i++]);
            }
        }
    }

    // Previously called testKeyStore_GetEntry_Nonexistent_NullParams_Unencrypted_Failure
    @Test
    public void getEntry_nonExistent_returnsNull() throws Exception {
        mKeyStore.load(null, null);
        assertNull(
                "Should return null for a non-existent entry",
                mKeyStore.getEntry(getTestAlias1(), null));
    }

    // Previously called testKeyStore_GetKey_NoPassword_Unencrypted_Success
    @Test
    public void getKey_noPassword_success_RSA() throws Exception {
        mKeyStore.load(null, null);
        mKeyStore.setEntry(getTestAlias1(), makeUserKey("RSA"), null);

        Key key = mKeyStore.getKey(getTestAlias1(), null);
        assertNotNull("Key should exist", key);
        assertTrue("Should be a PrivateKey", key instanceof PrivateKey);
        assertTrue("Should be a RSAKey", key instanceof RSAKey);

        RSAKey actualKey = (RSAKey) key;
        KeyFactory keyFact = KeyFactory.getInstance("RSA");
        PrivateKey expectedKey =
                keyFact.generatePrivate(new PKCS8EncodedKeySpec(FAKE_KEY.get("RSA")));
        assertEquals(
                "Inserted key should be same as retrieved key",
                ((RSAKey) expectedKey).getModulus(),
                actualKey.getModulus());
    }

    // Previously called testKeyStore_GetKey_Certificate_Unencrypted_Failure
    @Test
    @Parameters(method = "getAlgorithms")
    @TestCaseName(value = "{method}_{0}")
    public void getKey_certificate_returnsNull(String algorithmNameForTest, String algorithm)
            throws Exception {
        assumeKmAlgorithmSupport(KmType.TEE, algorithm);
        mKeyStore.load(null, null);
        mKeyStore.setCertificateEntry(getTestAlias1(), generateCaCertificate(algorithm));
        assertNull(
                "Certificate entries should return null", mKeyStore.getKey(getTestAlias1(), null));
    }

    // Previously called testKeyStore_GetKey_NonExistent_Unencrypted_Failure
    @Test
    public void getKey_nonExistent_returnsNull() throws Exception {
        mKeyStore.load(null, null);
        assertNull(
                "A non-existent entry should return null", mKeyStore.getKey(getTestAlias1(), null));
    }

    // Previously called testKeyStore_GetProvider_Unencrypted_Success
    @Test
    public void getProvider() throws Exception {
        assertEquals("AndroidKeyStore", mKeyStore.getProvider().getName());
    }

    // Previously called testKeyStore_GetType_Unencrypted_Success
    @Test
    public void getType() throws Exception {
        assertEquals("AndroidKeyStore", mKeyStore.getType());
    }

    // Previously called testKeyStore_IsCertificateEntry_CA_Unencrypted_Success
    @Test
    @Parameters(method = "getAlgorithms")
    @TestCaseName(value = "{method}_{0}")
    public void isCertificateEntry_CA_returnsTrue(String algorithmNameForTest, String algorithm)
            throws Exception {
        assumeKmAlgorithmSupport(KmType.TEE, algorithm);
        mKeyStore.load(null, null);
        mKeyStore.setCertificateEntry(getTestAlias1(), generateCaCertificate(algorithm));
        assertTrue(
                "Should return true for CA certificate",
                mKeyStore.isCertificateEntry(getTestAlias1()));
    }

    // Previously called testKeyStore_IsCertificateEntry_PrivateKey_Unencrypted_Failure
    @Test
    @Parameters(method = "getAlgorithms")
    @TestCaseName(value = "{method}_{0}")
    public void isCertificateEntry_privateKey_returnsFalse(
            String algorithmNameForTest, String algorithm) throws Exception {
        assumeKmAlgorithmSupport(KmType.TEE, algorithm);
        mKeyStore.load(null, null);
        mKeyStore.setEntry(getTestAlias1(), makeUserKey(algorithm), null);
        assertFalse(
                "Should return false for PrivateKeyEntry",
                mKeyStore.isCertificateEntry(getTestAlias1()));
    }

    // Previously called testKeyStore_IsCertificateEntry_NonExist_Unencrypted_Failure
    @Test
    public void isCertificateEntry_nonExistent_returnsFalse() throws Exception {
        mKeyStore.load(null, null);
        assertFalse(
                "Should return false for non-existent entry",
                mKeyStore.isCertificateEntry(getTestAlias1()));
    }

    // Previously called testKeyStore_IsKeyEntry_PrivateKey_Unencrypted_Success
    @Test
    @Parameters(method = "getAlgorithms")
    @TestCaseName(value = "{method}_{0}")
    public void isKeyEntry_privateKey_returnsTrue(String algorithmNameForTest, String algorithm)
            throws Exception {
        assumeKmAlgorithmSupport(KmType.TEE, algorithm);
        mKeyStore.load(null, null);
        mKeyStore.setEntry(getTestAlias1(), makeUserKey(algorithm), null);
        assertTrue("Should return true for PrivateKeyEntry", mKeyStore.isKeyEntry(getTestAlias1()));
    }

    // Previously called testKeyStore_IsKeyEntry_CA_Unencrypted_Failure
    @Test
    @Parameters(method = "getAlgorithms")
    @TestCaseName(value = "{method}_{0}")
    public void isKeyEntry_CA_returnsFalse(String algorithmNameForTest, String algorithm)
            throws Exception {
        assumeKmAlgorithmSupport(KmType.TEE, algorithm);
        mKeyStore.load(null, null);
        mKeyStore.setCertificateEntry(getTestAlias1(), generateCaCertificate(algorithm));
        assertFalse(
                "Should return false for CA certificate", mKeyStore.isKeyEntry(getTestAlias1()));
    }

    // Previously called testKeyStore_IsKeyEntry_NonExist_Unencrypted_Failure
    @Test
    public void isKeyEntry_nonExistent_returnsFalse() throws Exception {
        mKeyStore.load(null, null);
        assertFalse(
                "Should return false for non-existent entry",
                mKeyStore.isKeyEntry(getTestAlias1()));
    }

    // Previously called testKeyStore_SetCertificate_CA_Unencrypted_Success
    @Test
    @Parameters(method = "getAlgorithms")
    @TestCaseName(value = "{method}_{0}")
    public void setCertificateEntry_CA_success(String algorithmNameForTest, String algorithm)
            throws Exception {
        assumeKmAlgorithmSupport(KmType.TEE, algorithm);
        final Certificate actual = generateCaCertificate(algorithm);
        mKeyStore.load(null, null);
        mKeyStore.setCertificateEntry(getTestAlias1(), actual);
        assertAliases(new String[] {getTestAlias1()});

        Certificate retrieved = mKeyStore.getCertificate(getTestAlias1());
        assertEquals(
                "Retrieved certificate should be the same as the one inserted", actual, retrieved);
    }

    // Previously called testKeyStore_SetCertificate_CAExists_Overwrite_Unencrypted_Success
    @Test
    @Parameters(method = "getAlgorithms")
    @TestCaseName(value = "{method}_{0}")
    public void setCertificateEntry_overwriteCAWithCA_success(
            String algorithmNameForTest, String algorithm) throws Exception {
        assumeKmAlgorithmSupport(KmType.TEE, algorithm);
        mKeyStore.load(null, null);
        mKeyStore.setCertificateEntry(getTestAlias1(), generateCaCertificate(algorithm));
        assertAliases(new String[] {getTestAlias1()});

        // TODO(b/491452429): Use different test vector for the overwrite
        final Certificate cert = generateCaCertificate(algorithm);
        mKeyStore.setCertificateEntry(getTestAlias1(), cert);
        assertAliases(new String[] {getTestAlias1()});
    }

    // Previously called testKeyStore_SetCertificate_PrivateKeyExists_Unencrypted_Failure
    @Test
    @Parameters(method = "getAlgorithms")
    @TestCaseName(value = "{method}_{0}")
    public void setCertificateEntry_overwritePrivateKeyWithCA_throwsException(
            String algorithmNameForTest, String algorithm) throws Exception {
        assumeKmAlgorithmSupport(KmType.TEE, algorithm);
        mKeyStore.load(null, null);
        mKeyStore.setEntry(getTestAlias1(), makeUserKey(algorithm), null);
        assertAliases(new String[] {getTestAlias1()});

        final Certificate cert = generateCaCertificate(algorithm);
        assertThrows(
                "Should throw when trying to overwrite a PrivateKey entry with a Certificate",
                KeyStoreException.class,
                () -> mKeyStore.setCertificateEntry(getTestAlias1(), cert));
    }

    // Previously called testKeyStore_SetEntry_PrivateKeyEntry_Unencrypted_Success
    @Test
    @Parameters(method = "getAlgorithms")
    @TestCaseName(value = "{method}_{0}")
    public void setEntry_privateKeyEntry(String algorithmNameForTest, String algorithm)
            throws Exception {
        assumeKmAlgorithmSupport(KmType.TEE, algorithm);
        mKeyStore.load(null, null);
        KeyFactory keyFact = KeyFactory.getInstance(algorithm);
        PrivateKey expectedKey =
                keyFact.generatePrivate(new PKCS8EncodedKeySpec(FAKE_KEY.get(algorithm)));

        final CertificateFactory f = CertificateFactory.getInstance("X.509");
        final Certificate[] expectedChain = new Certificate[2];
        expectedChain[0] =
                f.generateCertificate(new ByteArrayInputStream(FAKE_USER.get(algorithm)));
        expectedChain[1] = f.generateCertificate(new ByteArrayInputStream(FAKE_CA.get(algorithm)));

        PrivateKeyEntry expected = new PrivateKeyEntry(expectedKey, expectedChain);
        mKeyStore.setEntry(getTestAlias1(), expected, null);

        Entry actualEntry = mKeyStore.getEntry(getTestAlias1(), null);
        assertNotNull("Retrieved entry should exist", actualEntry);
        assertTrue(
                "Retrieved entry should be of type PrivateKeyEntry",
                actualEntry instanceof PrivateKeyEntry);

        PrivateKeyEntry actual = (PrivateKeyEntry) actualEntry;
        assertPrivateKeyEntryEquals(
                actual,
                algorithm,
                FAKE_KEY.get(algorithm),
                FAKE_USER.get(algorithm),
                FAKE_CA.get(algorithm));
    }

    // Previously called
    // testKeyStore_SetEntry_PrivateKeyEntry_Overwrites_PrivateKeyEntry_Unencrypted_Success
    @Test
    @Parameters(method = "getAlgorithms")
    @TestCaseName(value = "{method}_{0}")
    public void setEntry_overwritePrivateKeyWithPrivateKey_success(
            String algorithmNameForTest, String algorithm) throws Exception {
        assumeKmAlgorithmSupport(KmType.TEE, algorithm);
        mKeyStore.load(null, null);
        final KeyFactory keyFact = KeyFactory.getInstance(algorithm);
        final CertificateFactory f = CertificateFactory.getInstance("X.509");

        // Start with PrivateKeyEntry
        {
            PrivateKey expectedKey =
                    keyFact.generatePrivate(new PKCS8EncodedKeySpec(FAKE_KEY.get(algorithm)));

            final Certificate[] expectedChain = new Certificate[2];
            expectedChain[0] =
                    f.generateCertificate(new ByteArrayInputStream(FAKE_USER.get(algorithm)));
            expectedChain[1] =
                    f.generateCertificate(new ByteArrayInputStream(FAKE_CA.get(algorithm)));

            PrivateKeyEntry expected = new PrivateKeyEntry(expectedKey, expectedChain);
            mKeyStore.setEntry(getTestAlias1(), expected, null);

            Entry actualEntry = mKeyStore.getEntry(getTestAlias1(), null);
            assertNotNull("Retrieved entry should exist", actualEntry);
            assertTrue(
                    "Retrieved entry should be of type PrivateKeyEntry",
                    actualEntry instanceof PrivateKeyEntry);

            PrivateKeyEntry actual = (PrivateKeyEntry) actualEntry;
            assertPrivateKeyEntryEquals(
                    actual,
                    algorithm,
                    FAKE_KEY.get(algorithm),
                    FAKE_USER.get(algorithm),
                    FAKE_CA.get(algorithm));
        }

        // TODO(b/491452429): Use different test vectors for the overwrite
        // Replace with PrivateKeyEntry
        {
            PrivateKey expectedKey =
                    keyFact.generatePrivate(new PKCS8EncodedKeySpec(FAKE_KEY.get(algorithm)));
            final Certificate[] expectedChain = new Certificate[2];
            expectedChain[0] =
                    f.generateCertificate(new ByteArrayInputStream(FAKE_USER.get(algorithm)));
            expectedChain[1] =
                    f.generateCertificate(new ByteArrayInputStream(FAKE_CA.get(algorithm)));

            PrivateKeyEntry expected = new PrivateKeyEntry(expectedKey, expectedChain);
            mKeyStore.setEntry(getTestAlias1(), expected, null);

            Entry actualEntry = mKeyStore.getEntry(getTestAlias1(), null);
            assertNotNull("Retrieved entry should exist", actualEntry);
            assertTrue(
                    "Retrieved entry should be of type PrivateKeyEntry",
                    actualEntry instanceof PrivateKeyEntry);

            PrivateKeyEntry actual = (PrivateKeyEntry) actualEntry;
            assertPrivateKeyEntryEquals(
                    actual,
                    algorithm,
                    FAKE_KEY.get(algorithm),
                    FAKE_USER.get(algorithm),
                    FAKE_CA.get(algorithm));
        }
    }

    // Previously called
    // testKeyStore_SetEntry_CAEntry_Overwrites_PrivateKeyEntry_Unencrypted_Success
    @Test
    @Parameters(method = "getAlgorithms")
    @TestCaseName(value = "{method}_{0}")
    public void setEntry_overwriteCAEntryWithPrivateKey_success(
            String algorithmNameForTest, String algorithm) throws Exception {
        assumeKmAlgorithmSupport(KmType.TEE, algorithm);
        mKeyStore.load(null, null);
        final CertificateFactory f = CertificateFactory.getInstance("X.509");

        // Start with TrustedCertificateEntry
        {
            final Certificate caCert =
                    f.generateCertificate(new ByteArrayInputStream(FAKE_CA.get(algorithm)));

            TrustedCertificateEntry expectedCertEntry = new TrustedCertificateEntry(caCert);
            mKeyStore.setEntry(getTestAlias1(), expectedCertEntry, null);

            Entry actualEntry = mKeyStore.getEntry(getTestAlias1(), null);
            assertNotNull("Retrieved entry should exist", actualEntry);
            assertTrue(
                    "Retrieved entry should be of type TrustedCertificateEntry",
                    actualEntry instanceof TrustedCertificateEntry);
            TrustedCertificateEntry actualCertEntry = (TrustedCertificateEntry) actualEntry;
            assertEquals(
                    "Stored and retrieved certificates should be the same",
                    expectedCertEntry.getTrustedCertificate(),
                    actualCertEntry.getTrustedCertificate());
        }

        // Replace with PrivateKeyEntry
        {
            KeyFactory keyFact = KeyFactory.getInstance(algorithm);
            PrivateKey expectedKey =
                    keyFact.generatePrivate(new PKCS8EncodedKeySpec(FAKE_KEY.get(algorithm)));
            final Certificate[] expectedChain = new Certificate[2];
            expectedChain[0] =
                    f.generateCertificate(new ByteArrayInputStream(FAKE_USER.get(algorithm)));
            expectedChain[1] =
                    f.generateCertificate(new ByteArrayInputStream(FAKE_CA.get(algorithm)));

            PrivateKeyEntry expectedPrivEntry = new PrivateKeyEntry(expectedKey, expectedChain);
            mKeyStore.setEntry(getTestAlias1(), expectedPrivEntry, null);

            Entry actualEntry = mKeyStore.getEntry(getTestAlias1(), null);
            assertNotNull("Retrieved entry should exist", actualEntry);
            assertTrue(
                    "Retrieved entry should be of type PrivateKeyEntry",
                    actualEntry instanceof PrivateKeyEntry);

            PrivateKeyEntry actualPrivEntry = (PrivateKeyEntry) actualEntry;
            assertPrivateKeyEntryEquals(
                    actualPrivEntry,
                    algorithm,
                    FAKE_KEY.get(algorithm),
                    FAKE_USER.get(algorithm),
                    FAKE_CA.get(algorithm));
        }
    }

    // Previously called
    // testKeyStore_SetEntry_PrivateKeyEntry_Overwrites_CAEntry_Unencrypted_Success
    @Test
    @Parameters(method = "getAlgorithms")
    @TestCaseName(value = "{method}_{0}")
    public void setEntry_overwritePrivateKeyWithCA_success(
            String algorithmNameForTest, String algorithm) throws Exception {
        assumeKmAlgorithmSupport(KmType.TEE, algorithm);
        mKeyStore.load(null, null);
        final CertificateFactory f = CertificateFactory.getInstance("X.509");
        final Certificate caCert =
                f.generateCertificate(new ByteArrayInputStream(FAKE_CA.get(algorithm)));

        // Start with PrivateKeyEntry
        {
            KeyFactory keyFact = KeyFactory.getInstance(algorithm);
            PrivateKey expectedKey =
                    keyFact.generatePrivate(new PKCS8EncodedKeySpec(FAKE_KEY.get(algorithm)));
            final Certificate[] expectedChain = new Certificate[2];
            expectedChain[0] =
                    f.generateCertificate(new ByteArrayInputStream(FAKE_USER.get(algorithm)));
            expectedChain[1] = caCert;

            PrivateKeyEntry expectedPrivEntry = new PrivateKeyEntry(expectedKey, expectedChain);
            mKeyStore.setEntry(getTestAlias1(), expectedPrivEntry, null);

            Entry actualEntry = mKeyStore.getEntry(getTestAlias1(), null);
            assertNotNull("Retrieved entry should exist", actualEntry);
            assertTrue(
                    "Retrieved entry should be of type PrivateKeyEntry",
                    actualEntry instanceof PrivateKeyEntry);

            PrivateKeyEntry actualPrivEntry = (PrivateKeyEntry) actualEntry;
            assertPrivateKeyEntryEquals(
                    actualPrivEntry,
                    algorithm,
                    FAKE_KEY.get(algorithm),
                    FAKE_USER.get(algorithm),
                    FAKE_CA.get(algorithm));
        }

        // Replace with TrustedCertificateEntry
        {
            TrustedCertificateEntry expectedCertEntry = new TrustedCertificateEntry(caCert);
            mKeyStore.setEntry(getTestAlias1(), expectedCertEntry, null);

            Entry actualEntry = mKeyStore.getEntry(getTestAlias1(), null);
            assertNotNull("Retrieved entry should exist", actualEntry);
            assertTrue(
                    "Retrieved entry should be of type TrustedCertificateEntry",
                    actualEntry instanceof TrustedCertificateEntry);
            TrustedCertificateEntry actualCertEntry = (TrustedCertificateEntry) actualEntry;
            assertEquals(
                    "Stored and retrieved certificates should be the same",
                    expectedCertEntry.getTrustedCertificate(),
                    actualCertEntry.getTrustedCertificate());
        }
    }

    // Previously called
    // testKeyStore_SetEntry_PrivateKeyEntry_Overwrites_ShortPrivateKeyEntry_Unencrypted_Success
    @Test
    @Parameters(method = "getAlgorithms")
    @TestCaseName(value = "{method}_{0}")
    public void setEntry_overwritePrivateKeyWithPrivateKeyWithoutChain_success(
            String algorithmNameForTest, String algorithm) throws Exception {
        assumeKmAlgorithmSupport(KmType.TEE, algorithm);
        mKeyStore.load(null, null);
        final CertificateFactory f = CertificateFactory.getInstance("X.509");
        final Certificate caCert =
                f.generateCertificate(new ByteArrayInputStream(FAKE_CA.get(algorithm)));

        // Start with PrivateKeyEntry
        {
            KeyFactory keyFact = KeyFactory.getInstance(algorithm);
            PrivateKey expectedKey =
                    keyFact.generatePrivate(new PKCS8EncodedKeySpec(FAKE_KEY.get(algorithm)));
            final Certificate[] expectedChain = new Certificate[2];
            expectedChain[0] =
                    f.generateCertificate(new ByteArrayInputStream(FAKE_USER.get(algorithm)));
            expectedChain[1] = caCert;

            PrivateKeyEntry expectedPrivEntry = new PrivateKeyEntry(expectedKey, expectedChain);
            mKeyStore.setEntry(getTestAlias1(), expectedPrivEntry, null);

            Entry actualEntry = mKeyStore.getEntry(getTestAlias1(), null);
            assertNotNull("Retrieved entry should exist", actualEntry);
            assertTrue(
                    "Retrieved entry should be of type PrivateKeyEntry",
                    actualEntry instanceof PrivateKeyEntry);

            PrivateKeyEntry actualPrivEntry = (PrivateKeyEntry) actualEntry;
            assertPrivateKeyEntryEquals(
                    actualPrivEntry,
                    algorithm,
                    FAKE_KEY.get(algorithm),
                    FAKE_USER.get(algorithm),
                    FAKE_CA.get(algorithm));
        }

        // Replace with PrivateKeyEntry that has no chain
        {
            KeyFactory keyFact = KeyFactory.getInstance(algorithm);
            PrivateKey expectedKey =
                    keyFact.generatePrivate(new PKCS8EncodedKeySpec(FAKE_KEY.get(algorithm)));
            final Certificate[] expectedChain = new Certificate[1];
            expectedChain[0] =
                    f.generateCertificate(new ByteArrayInputStream(FAKE_USER.get(algorithm)));

            PrivateKeyEntry expectedPrivEntry = new PrivateKeyEntry(expectedKey, expectedChain);
            mKeyStore.setEntry(getTestAlias1(), expectedPrivEntry, null);

            Entry actualEntry = mKeyStore.getEntry(getTestAlias1(), null);
            assertNotNull("Retrieved entry should exist", actualEntry);
            assertTrue(
                    "Retrieved entry should be of type PrivateKeyEntry",
                    actualEntry instanceof PrivateKeyEntry);

            PrivateKeyEntry actualPrivEntry = (PrivateKeyEntry) actualEntry;
            assertPrivateKeyEntryEquals(
                    actualPrivEntry,
                    algorithm,
                    FAKE_KEY.get(algorithm),
                    FAKE_USER.get(algorithm),
                    null);
        }
    }

    // Previously called testKeyStore_SetEntry_CAEntry_Overwrites_CAEntry_Unencrypted_Success
    @Test
    @Parameters(method = "getAlgorithms")
    @TestCaseName(value = "{method}_{0}")
    public void setEntry_overwriteCAEntryWithCAEntry_success(
            String algorithmNameForTest, String algorithm) throws Exception {
        assumeKmAlgorithmSupport(KmType.TEE, algorithm);
        mKeyStore.load(null, null);
        final CertificateFactory f = CertificateFactory.getInstance("X.509");

        // Insert TrustedCertificateEntry
        {
            final Certificate caCert =
                    f.generateCertificate(new ByteArrayInputStream(FAKE_CA.get(algorithm)));

            TrustedCertificateEntry expectedCertEntry = new TrustedCertificateEntry(caCert);
            mKeyStore.setEntry(getTestAlias1(), expectedCertEntry, null);

            Entry actualEntry = mKeyStore.getEntry(getTestAlias1(), null);
            assertNotNull("Retrieved entry should exist", actualEntry);
            assertTrue(
                    "Retrieved entry should be of type TrustedCertificateEntry",
                    actualEntry instanceof TrustedCertificateEntry);
            TrustedCertificateEntry actualCertEntry = (TrustedCertificateEntry) actualEntry;
            assertEquals(
                    "Stored and retrieved certificates should be the same",
                    expectedCertEntry.getTrustedCertificate(),
                    actualCertEntry.getTrustedCertificate());
        }

        // Replace with TrustedCertificateEntry of USER
        {
            final Certificate userCert =
                    f.generateCertificate(new ByteArrayInputStream(FAKE_USER.get(algorithm)));

            TrustedCertificateEntry expectedUserEntry = new TrustedCertificateEntry(userCert);
            mKeyStore.setEntry(getTestAlias1(), expectedUserEntry, null);

            Entry actualEntry = mKeyStore.getEntry(getTestAlias1(), null);
            assertNotNull("Retrieved entry should exist", actualEntry);
            assertTrue(
                    "Retrieved entry should be of type TrustedCertificateEntry",
                    actualEntry instanceof TrustedCertificateEntry);
            TrustedCertificateEntry actualUserEntry = (TrustedCertificateEntry) actualEntry;
            assertEquals(
                    "Stored and retrieved certificates should be the same",
                    expectedUserEntry.getTrustedCertificate(),
                    actualUserEntry.getTrustedCertificate());
        }
    }

    // Previously called testKeyStore_SetKeyEntry_ProtectedKey_Unencrypted_Failure
    @Test
    @Parameters(method = "getAlgorithms")
    @TestCaseName(value = "{method}_{0}")
    public void setKeyEntry_protectedKey_throwsException(
            String algorithmNameForTest, String algorithm) throws Exception {
        assumeKmAlgorithmSupport(KmType.TEE, algorithm);
        mKeyStore.load(null, null);
        final CertificateFactory f = CertificateFactory.getInstance("X.509");
        final Certificate caCert =
                f.generateCertificate(new ByteArrayInputStream(FAKE_CA.get(algorithm)));

        KeyFactory keyFact = KeyFactory.getInstance(algorithm);
        PrivateKey privKey =
                keyFact.generatePrivate(new PKCS8EncodedKeySpec(FAKE_KEY.get(algorithm)));
        final Certificate[] chain = new Certificate[2];
        chain[0] = f.generateCertificate(new ByteArrayInputStream(FAKE_USER.get(algorithm)));
        chain[1] = caCert;

        assertThrows(
                "Should fail when a password is specified",
                KeyStoreException.class,
                () -> mKeyStore.setKeyEntry(getTestAlias1(), privKey, "foo".toCharArray(), chain));
    }

    // Previously called testKeyStore_SetKeyEntry_Unencrypted_Success
    @Test
    @Parameters(method = "getAlgorithms")
    @TestCaseName(value = "{method}_{0}")
    public void setKeyEntry_success(String algorithmNameForTest, String algorithm)
            throws Exception {
        assumeKmAlgorithmSupport(KmType.TEE, algorithm);
        mKeyStore.load(null, null);
        final CertificateFactory f = CertificateFactory.getInstance("X.509");
        final Certificate caCert =
                f.generateCertificate(new ByteArrayInputStream(FAKE_CA.get(algorithm)));

        KeyFactory keyFact = KeyFactory.getInstance(algorithm);
        PrivateKey privKey =
                keyFact.generatePrivate(new PKCS8EncodedKeySpec(FAKE_KEY.get(algorithm)));
        final Certificate[] chain = new Certificate[2];
        chain[0] = f.generateCertificate(new ByteArrayInputStream(FAKE_USER.get(algorithm)));
        chain[1] = caCert;

        mKeyStore.setKeyEntry(getTestAlias1(), privKey, null, chain);

        Entry actualEntry = mKeyStore.getEntry(getTestAlias1(), null);
        assertNotNull("Retrieved entry should exist", actualEntry);

        assertTrue(
                "Retrieved entry should be of type PrivateKeyEntry",
                actualEntry instanceof PrivateKeyEntry);

        PrivateKeyEntry actual = (PrivateKeyEntry) actualEntry;

        assertPrivateKeyEntryEquals(
                actual,
                algorithm,
                FAKE_KEY.get(algorithm),
                FAKE_USER.get(algorithm),
                FAKE_CA.get(algorithm));
    }

    // Previously called testKeyStore_SetKeyEntry_Replaced_Unencrypted_Success
    @Test
    @Parameters(method = "getAlgorithms")
    @TestCaseName(value = "{method}_{0}")
    public void setKeyEntry_replaceExistingEntryWithSameAlias_success(
            String algorithmNameForTest, String algorithm) throws Exception {
        assumeKmAlgorithmSupport(KmType.TEE, algorithm);
        mKeyStore.load(null, null);
        final CertificateFactory f = CertificateFactory.getInstance("X.509");
        final Certificate caCert =
                f.generateCertificate(new ByteArrayInputStream(FAKE_CA.get(algorithm)));

        // Insert initial key
        {
            KeyFactory keyFact = KeyFactory.getInstance(algorithm);
            PrivateKey privKey =
                    keyFact.generatePrivate(new PKCS8EncodedKeySpec(FAKE_KEY.get(algorithm)));
            final Certificate[] chain = new Certificate[2];
            chain[0] = f.generateCertificate(new ByteArrayInputStream(FAKE_USER.get(algorithm)));
            chain[1] = caCert;

            mKeyStore.setKeyEntry(getTestAlias1(), privKey, null, chain);

            Entry actualEntry = mKeyStore.getEntry(getTestAlias1(), null);
            assertNotNull("Retrieved entry should exist", actualEntry);
            assertTrue(
                    "Retrieved entry should be of type PrivateKeyEntry",
                    actualEntry instanceof PrivateKeyEntry);

            PrivateKeyEntry actual = (PrivateKeyEntry) actualEntry;
            assertPrivateKeyEntryEquals(
                    actual,
                    algorithm,
                    FAKE_KEY.get(algorithm),
                    FAKE_USER.get(algorithm),
                    FAKE_CA.get(algorithm));
        }

        // TODO(b/491452429): Use different test vectors for the overwrite
        // Replace key by using the existing key's alias
        {
            KeyFactory keyFact = KeyFactory.getInstance(algorithm);
            PrivateKey privKey =
                    keyFact.generatePrivate(new PKCS8EncodedKeySpec(FAKE_KEY.get(algorithm)));
            final Certificate[] chain = new Certificate[2];
            chain[0] = f.generateCertificate(new ByteArrayInputStream(FAKE_USER.get(algorithm)));
            chain[1] = caCert;
            mKeyStore.setKeyEntry(getTestAlias1(), privKey, null, chain);

            Entry actualEntry = mKeyStore.getEntry(getTestAlias1(), null);
            assertNotNull("Retrieved entry should exist", actualEntry);
            assertTrue(
                    "Retrieved entry should be of type PrivateKeyEntry",
                    actualEntry instanceof PrivateKeyEntry);

            PrivateKeyEntry actual = (PrivateKeyEntry) actualEntry;
            assertPrivateKeyEntryEquals(
                    actual,
                    algorithm,
                    FAKE_KEY.get(algorithm),
                    FAKE_USER.get(algorithm),
                    FAKE_CA.get(algorithm));
        }
    }

    // Previously called testKeyStore_SetKeyEntry_ReplacedChain_Unencrypted_Success
    @Test
    @Parameters(method = "getAlgorithms")
    @TestCaseName(value = "{method}_{0}")
    public void setKeyEntry_replaceKeyWithCertificateChain_success(
            String algorithmNameForTest, String algorithm) throws Exception {
        assumeKmAlgorithmSupport(KmType.TEE, algorithm);
        mKeyStore.load(null, null);

        // Add key to the keystore with setEntry.
        {
            KeyStore.PrivateKeyEntry privEntry = makeUserKey(algorithm);
            mKeyStore.setEntry(getTestAlias1(), privEntry, null);
            Entry entry = mKeyStore.getEntry(getTestAlias1(), null);
            assertTrue(entry instanceof PrivateKeyEntry);

            PrivateKeyEntry keyEntry = (PrivateKeyEntry) entry;
            ArrayList<Certificate> chain = new ArrayList<Certificate>();
            chain.add(generateCaCertificate(algorithm));
            assertPrivateKeyEntryEquals(
                    keyEntry, privEntry.getPrivateKey(), privEntry.getCertificate(), chain);
        }

        // Add a certificate chain to the keystore with setKeyEntry, using the same alias as the
        // existing key.
        {
            Key key = mKeyStore.getKey(getTestAlias1(), null);
            assertTrue(key instanceof PrivateKey);

            PrivateKey expectedKey = (PrivateKey) key;
            Certificate expectedCert = generateUserCertificate(algorithm);
            mKeyStore.setKeyEntry(
                    getTestAlias1(), expectedKey, null, new Certificate[] {expectedCert});
            Entry entry = mKeyStore.getEntry(getTestAlias1(), null);
            assertTrue(entry instanceof PrivateKeyEntry);

            PrivateKeyEntry keyEntry = (PrivateKeyEntry) entry;
            assertPrivateKeyEntryEquals(keyEntry, expectedKey, expectedCert, null);
        }
    }

    // Previously called
    // testKeyStore_SetKeyEntry_ReplacedChain_DifferentPrivateKey_Unencrypted_Failure
    @Test
    @Parameters(method = "getAlgorithms")
    @TestCaseName(value = "{method}_{0}")
    public void setKeyEntry_replaceChainForDifferentPrivateKey_throwsException(
            String algorithmNameForTest, String algorithm) throws Exception {
        assumeKmAlgorithmSupport(KmType.TEE, algorithm);
        mKeyStore.load(null, null);

        // Create key #1
        mKeyStore.setEntry(getTestAlias1(), makeUserKey(algorithm), null);

        // Create key #2
        mKeyStore.setEntry(getTestAlias2(), makeUserKey(algorithm), null);

        // Try to replace key #1 with key #2 by using key #2's alias
        Key key2 = mKeyStore.getKey(getTestAlias2(), null);
        Certificate key1Cert = generateUserCertificate(algorithm);
        assertThrows(
                "Should not allow setting of KeyEntry with wrong PrivateKey",
                KeyStoreException.class,
                () ->
                        mKeyStore.setKeyEntry(
                                getTestAlias1(), key2, null, new Certificate[] {key1Cert}));
    }

    // Previously called testKeyStore_SetKeyEntry_ReplacedWithSame_UnencryptedToUnencrypted_Failure
    @Test
    @Parameters(method = "getAlgorithms")
    @TestCaseName(value = "{method}_{0}")
    public void setEntry_replaceWithSameEntry_success(String algorithmNameForTest, String algorithm)
            throws Exception {
        assumeKmAlgorithmSupport(KmType.TEE, algorithm);
        mKeyStore.load(null, null);
        mKeyStore.setEntry(getTestAlias1(), makeUserKey(algorithm), null);
        Entry entry = mKeyStore.getEntry(getTestAlias1(), null);
        mKeyStore.setEntry(getTestAlias1(), entry, null);
    }

    // Previously called testKeyStore_SetKeyEntry_ReplacedWithSameGeneratedSecretKey
    @Test
    public void setKeyEntry_replacedWithSameGeneratedSecretKey_success_AES() throws Exception {
        final String plaintext = "My awesome plaintext message!";
        final String algorithm = "AES/GCM/NoPadding";

        final KeyGenerator generator = KeyGenerator.getInstance("AES", "AndroidKeyStore");
        final KeyGenParameterSpec spec =
                new KeyGenParameterSpec.Builder(
                                getTestAlias1(),
                                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                        .setKeySize(256)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .build();
        generator.init(spec);
        final SecretKey key = generator.generateKey();

        Cipher cipher = Cipher.getInstance(algorithm);
        cipher.init(Cipher.ENCRYPT_MODE, key);
        AlgorithmParameters params = cipher.getParameters();
        final byte[] ciphertext = cipher.doFinal(plaintext.getBytes());

        mKeyStore.load(null, null);

        // This should succeed.
        mKeyStore.setKeyEntry(getTestAlias1(), key, null, null);
        // And it should not change the key under getTestAlias1(). And what better way to test
        // then to use it on some cipher text generated with that key.
        final Key key2 = mKeyStore.getKey(getTestAlias1(), null);
        cipher = Cipher.getInstance(algorithm);
        cipher.init(Cipher.DECRYPT_MODE, key2, params);
        byte[] plaintext2 = cipher.doFinal(ciphertext);
        assertArrayEquals(
                "The plaintext2 should match the original plaintext.",
                plaintext2,
                plaintext.getBytes());
    }

    // Previously called testKeyStore_Size_Unencrypted_Success
    @Test
    @Parameters(method = "getAlgorithms")
    @TestCaseName(value = "{method}_{0}")
    public void size_success(String algorithmNameForTest, String algorithm) throws Exception {
        assumeKmAlgorithmSupport(KmType.TEE, algorithm);
        mKeyStore.load(null, null);
        mKeyStore.setCertificateEntry(getTestAlias1(), generateCaCertificate(algorithm));
        assertEquals("The keystore size should match expected", 1, mKeyStore.size());
        assertAliases(new String[] {getTestAlias1()});

        mKeyStore.setCertificateEntry(getTestAlias2(), generateCaCertificate(algorithm));
        assertEquals("The keystore size should match expected", 2, mKeyStore.size());
        assertAliases(new String[] {getTestAlias1(), getTestAlias2()});

        mKeyStore.setEntry(getTestAlias3(), makeUserKey(algorithm), null);
        assertEquals("The keystore size should match expected", 3, mKeyStore.size());
        assertAliases(new String[] {getTestAlias1(), getTestAlias2(), getTestAlias3()});

        mKeyStore.deleteEntry(getTestAlias1());
        assertEquals("The keystore size should match expected", 2, mKeyStore.size());
        assertAliases(new String[] {getTestAlias2(), getTestAlias3()});

        mKeyStore.deleteEntry(getTestAlias3());
        assertEquals("The keystore size should match expected", 1, mKeyStore.size());
        assertAliases(new String[] {getTestAlias2()});
    }

    // Previously called testKeyStore_Store_LoadStoreParam_Unencrypted_Failure
    @Test
    public void store_nullLoadStoreParameter_throwsException() throws Exception {
        mKeyStore.load(null, null);
        assertThrows(
                "Should throw UnsupportedOperationException when trying to store",
                UnsupportedOperationException.class,
                () -> mKeyStore.store(/* param= */ null));
    }

    // Previously called testKeyStore_Load_InputStreamSupplied_Unencrypted_Failure
    @Test
    public void load_nonNullInputStream_throwsException() throws Exception {
        byte[] buf = "FAKE KEYSTORE".getBytes();
        ByteArrayInputStream is = new ByteArrayInputStream(buf);
        assertThrows(
                "Should throw IllegalArgumentException when InputStream is supplied",
                IllegalArgumentException.class,
                () -> mKeyStore.load(/* stream= */ is, /* password= */ null));
    }

    // Previously called testKeyStore_Load_PasswordSupplied_Unencrypted_Failure
    @Test
    public void load_nonNullPassword_throwsException() throws Exception {
        assertThrows(
                "Should throw IllegalArgumentException when password is supplied",
                IllegalArgumentException.class,
                () -> mKeyStore.load(/* stream= */ null, /* password= */ "password".toCharArray()));
    }

    // Previously called testKeyStore_Store_OutputStream_Unencrypted_Failure
    @Test
    public void store_nonNullArguments_throwsException() throws Exception {
        mKeyStore.load(null, null);

        OutputStream sink = new ByteArrayOutputStream();
        assertThrows(
                "Should throw UnsupportedOperationException when trying to store",
                UnsupportedOperationException.class,
                () -> mKeyStore.store(/* stream= */ sink, /* password= */ null));

        assertThrows(
                "Should throw UnsupportedOperationException when trying to store",
                UnsupportedOperationException.class,
                () -> mKeyStore.store(/* stream= */ sink, /* password= */ "blah".toCharArray()));
    }

    // Previously called testKeyStore_KeyOperations_Wrap_Unencrypted_Success
    @Test
    public void cipherWrap_success() throws Exception {
        mKeyStore.load(null, null);
        mKeyStore.setEntry(getTestAlias1(), makeUserKey("RSA"), null);

        // Test key usage
        Entry e = mKeyStore.getEntry(getTestAlias1(), null);
        assertNotNull(e);
        assertTrue(e instanceof PrivateKeyEntry);

        PrivateKeyEntry privEntry = (PrivateKeyEntry) e;
        PrivateKey privKey = privEntry.getPrivateKey();
        assertNotNull(privKey);

        PublicKey pubKey = privEntry.getCertificate().getPublicKey();
        Cipher c = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        c.init(Cipher.WRAP_MODE, pubKey);

        byte[] expectedKey =
                new byte[] {0x00, 0x05, (byte) 0xAA, (byte) 0x0A5, (byte) 0xFF, 0x55, 0x0A};
        SecretKey expectedSecret = new TransparentSecretKey(expectedKey, "AES");
        byte[] wrappedExpected = c.wrap(expectedSecret);

        c.init(Cipher.UNWRAP_MODE, privKey);
        SecretKey actualSecret = (SecretKey) c.unwrap(wrappedExpected, "AES", Cipher.SECRET_KEY);

        assertEquals(
                Arrays.toString(expectedSecret.getEncoded()),
                Arrays.toString(actualSecret.getEncoded()));
    }

    // Previously called testKeyStore_Encrypting_RSA_NONE_NOPADDING
    @Test
    public void cipherEncrypt_success_RSA_NONE_NOPADDING() throws Exception {
        String alias = "MyKey";
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        assertNotNull(ks);
        ks.load(null);

        Calendar cal = Calendar.getInstance();
        cal.set(1944, 5, 6);
        Date now = cal.getTime();
        cal.clear();

        cal.set(1945, 8, 2);
        Date end = cal.getTime();

        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA", "AndroidKeyStore");
        assertNotNull(kpg);
        kpg.initialize(
                new KeyPairGeneratorSpec.Builder(getContext())
                        .setAlias(alias)
                        .setStartDate(now)
                        .setEndDate(end)
                        .setSerialNumber(BigInteger.valueOf(1))
                        .setSubject(new X500Principal("CN=test1"))
                        .build());

        kpg.generateKeyPair();

        PrivateKey privateKey = (PrivateKey) ks.getKey(alias, null);
        assertNotNull(privateKey);
        PublicKey publicKey = ks.getCertificate(alias).getPublicKey();
        assertNotNull(publicKey);
        String cipher = privateKey.getAlgorithm() + "/NONE/NOPADDING";
        Cipher encrypt = Cipher.getInstance(cipher);
        assertNotNull(encrypt);
        encrypt.init(Cipher.ENCRYPT_MODE, privateKey);

        int modulusSizeBytes = (((RSAKey) publicKey).getModulus().bitLength() + 7) / 8;
        byte[] plainText = new byte[modulusSizeBytes];
        Arrays.fill(plainText, (byte) 0xFF);

        // We expect a BadPaddingException here as the message size (plaintext)
        // is bigger than the modulus.
        try {
            encrypt.doFinal(plainText);
            fail("Expected BadPaddingException or IllegalBlockSizeException");
        } catch (BadPaddingException e) {
            // pass on exception as it is expected
        } catch (IllegalBlockSizeException e) {
            // pass on exception as it is expected
        }
    }

    // Previously called testKeyStore_PrivateKeyEntry_RSA_PublicKeyWorksWithCrypto
    @Test
    public void setKeyEntry_RSAPublicKeyCryptoUsage_success() throws Exception {
        mKeyStore.load(null, null);
        mKeyStore.setKeyEntry(
                getTestAlias2(),
                KeyFactory.getInstance("RSA")
                        .generatePrivate(new PKCS8EncodedKeySpec(FAKE_KEY.get("RSA"))),
                null, // no password (it's not even supported)
                new Certificate[] {generateUserCertificate("RSA")});
        PublicKey publicKey = mKeyStore.getCertificate(getTestAlias2()).getPublicKey();
        assertNotNull(publicKey);

        Signature.getInstance("SHA256withRSA").initVerify(publicKey);
        Signature.getInstance("NONEwithRSA").initVerify(publicKey);
        Signature.getInstance("SHA256withRSA/PSS").initVerify(publicKey);

        Cipher.getInstance("RSA/ECB/PKCS1Padding").init(Cipher.ENCRYPT_MODE, publicKey);
        Cipher.getInstance("RSA/ECB/NoPadding").init(Cipher.ENCRYPT_MODE, publicKey);
        Cipher.getInstance("RSA/ECB/OAEPPadding").init(Cipher.ENCRYPT_MODE, publicKey);
    }

    // Previously called testKeyStore_PrivateKeyEntry_EC_PublicKeyWorksWithCrypto
    @Test
    public void setKeyEntry_ECPublicKeyCryptoUsage_success() throws Exception {
        mKeyStore.load(null, null);
        mKeyStore.setKeyEntry(
                getTestAlias1(),
                KeyFactory.getInstance("EC")
                        .generatePrivate(new PKCS8EncodedKeySpec(FAKE_KEY.get("EC"))),
                null, // no password (it's not even supported)
                new Certificate[] {generateUserCertificate("EC")});
        PublicKey publicKey = mKeyStore.getCertificate(getTestAlias1()).getPublicKey();
        assertNotNull(publicKey);

        Signature.getInstance("SHA256withECDSA").initVerify(publicKey);
        Signature.getInstance("NONEwithECDSA").initVerify(publicKey);
    }

    // Previously called testKeyStore_TrustedCertificateEntry_RSA_PublicKeyWorksWithCrypto
    @Test
    public void setCertificateEntry_RSAPublicKeyCryptoUsage_success() throws Exception {
        mKeyStore.load(null, null);
        mKeyStore.setCertificateEntry(getTestAlias2(), generateUserCertificate("RSA"));
        PublicKey publicKey = mKeyStore.getCertificate(getTestAlias2()).getPublicKey();
        assertNotNull(publicKey);

        Signature.getInstance("SHA256withRSA").initVerify(publicKey);
        Signature.getInstance("NONEwithRSA").initVerify(publicKey);

        Cipher.getInstance("RSA/ECB/PKCS1Padding").init(Cipher.ENCRYPT_MODE, publicKey);
        Cipher.getInstance("RSA/ECB/NoPadding").init(Cipher.ENCRYPT_MODE, publicKey);
    }

    // Previously called testKeyStore_TrustedCertificateEntry_EC_PublicKeyWorksWithCrypto
    @Test
    public void setCertificateEntry_ECPublicKeyCryptoUsage_success() throws Exception {
        mKeyStore.load(null, null);
        mKeyStore.setCertificateEntry(getTestAlias1(), generateUserCertificate("EC"));
        PublicKey publicKey = mKeyStore.getCertificate(getTestAlias1()).getPublicKey();
        assertNotNull(publicKey);

        Signature.getInstance("SHA256withECDSA").initVerify(publicKey);
        Signature.getInstance("NONEwithECDSA").initVerify(publicKey);
    }

    private static final int MIN_SUPPORTED_KEY_COUNT = 1200;
    private static final Duration LARGE_NUMBER_OF_KEYS_TEST_MAX_DURATION = Duration.ofMinutes(4);
    private static final Duration LARGE_NUMBER_OF_KEYS_TEST_MAX_DURATION_WATCH =
            Duration.ofMinutes(6);

    // Helper that tells callers if a given Duration has been exceeded since creation.
    private static class TimeBox {
        private long mStartTimeNanos = System.nanoTime();
        private Duration mMaxDuration;

        public TimeBox(Duration maxDuration) {
            mMaxDuration = maxDuration;
        }

        public boolean isOutOfTime() {
            long nowNanos = System.nanoTime();
            if (nowNanos < mStartTimeNanos) {
                return true;
            }
            return nowNanos - mStartTimeNanos > mMaxDuration.toNanos();
        }

        public Duration elapsed() {
            return Duration.ofNanos(System.nanoTime() - mStartTimeNanos);
        }
    }

    // Previously called testKeyStore_LargeNumberOfKeysSupported_RSA
    @LargeTest
    @Test
    public void importLargeNumberOfKeys_success_RSA() throws Exception {
        // This test imports key1, then lots of other keys, then key2, and then confirms that
        // key1 and key2 backed by Android Keystore work fine. The assumption is that if the
        // underlying implementation has a limit on the number of keys, it'll either delete the
        // oldest key (key1), or will refuse to add keys (key2).
        // The test imports up MAX_NUMBER_OF_KEYS in a fixed amount of time, balancing the desire
        // to load many keys while also limiting maximum test time. This allows fast hardware to
        // run the test more quickly while also ensuring slower hardware loads as many keys as
        // possible within mMaxImportDuration.

        Certificate cert1 = TestUtils.getRawResX509Certificate(getContext(), R.raw.rsa_key1_cert);
        PrivateKey privateKey1 = TestUtils.getRawResPrivateKey(getContext(), R.raw.rsa_key1_pkcs8);
        String entryName1 = "test0";

        Certificate cert2 = TestUtils.getRawResX509Certificate(getContext(), R.raw.rsa_key2_cert);
        PrivateKey privateKey2 = TestUtils.getRawResPrivateKey(getContext(), R.raw.rsa_key2_pkcs8);

        Certificate cert3 = generateUserCertificate("RSA");
        PrivateKey privateKey3 = generatePrivateKey("RSA");

        final int MAX_NUMBER_OF_KEYS = 2500;
        final StringBuilder aliasPrefix = new StringBuilder("test_large_number_of_rsa_keys_");
        int keyCount = 0;
        String entryName2 = null;

        mKeyStore.load(null);
        try {
            KeyProtection protectionParams =
                    new KeyProtection.Builder(KeyProperties.PURPOSE_SIGN)
                            .setDigests(KeyProperties.DIGEST_SHA256)
                            .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                            .build();
            mKeyStore.setEntry(
                    entryName1,
                    new KeyStore.PrivateKeyEntry(privateKey1, new Certificate[] {cert1}),
                    protectionParams);

            keyCount =
                    importKeyManyTimes(
                            MAX_NUMBER_OF_KEYS,
                            aliasPrefix,
                            new PrivateKeyEntry(privateKey3, new Certificate[] {cert3}),
                            protectionParams);

            keyCount++;
            entryName2 = "test" + keyCount;
            mKeyStore.setEntry(
                    entryName2,
                    new KeyStore.PrivateKeyEntry(privateKey2, new Certificate[] {cert2}),
                    protectionParams);
            PrivateKey keystorePrivateKey2 = (PrivateKey) mKeyStore.getKey(entryName2, null);
            PrivateKey keystorePrivateKey1 = (PrivateKey) mKeyStore.getKey(entryName1, null);

            byte[] message = "This is a test".getBytes("UTF-8");

            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initSign(keystorePrivateKey1);
            sig.update(message);
            byte[] signature = sig.sign();
            sig = Signature.getInstance(sig.getAlgorithm());
            sig.initVerify(cert1.getPublicKey());
            sig.update(message);
            assertTrue(sig.verify(signature));

            sig = Signature.getInstance(sig.getAlgorithm());
            sig.initSign(keystorePrivateKey2);
            sig.update(message);
            signature = sig.sign();
            sig = Signature.getInstance(sig.getAlgorithm());
            sig.initVerify(cert2.getPublicKey());
            sig.update(message);
            assertTrue(sig.verify(signature));
        } finally {
            mKeyStore.deleteEntry(entryName1);
            deleteEntryIfNotNull(entryName2);
            deleteManyTestKeys(keyCount, aliasPrefix);
        }
    }

    // Previously called testKeyStore_LargeNumberOfKeysSupported_EC
    @LargeTest
    @Test
    public void importLargeNumberOfKeys_success_EC() throws Exception {
        // This test imports key1, then lots of other keys, then key2, and then confirms that
        // key1 and key2 backed by Android Keystore work fine. The assumption is that if the
        // underlying implementation has a limit on the number of keys, it'll either delete the
        // oldest key (key1), or will refuse to add keys (key2).
        // The test imports as many keys as it can in a fixed amount of time instead of stopping
        // at MIN_SUPPORTED_KEY_COUNT to balance the desire to support an unlimited number of keys
        // with the constraints on how long the test can run and performance differences of hardware
        // under test.

        TimeBox timeBox = new TimeBox(mMaxImportDuration);

        Certificate cert1 = TestUtils.getRawResX509Certificate(getContext(), R.raw.ec_key1_cert);
        PrivateKey privateKey1 = TestUtils.getRawResPrivateKey(getContext(), R.raw.ec_key1_pkcs8);
        String entryName1 = "test0";

        Certificate cert2 = TestUtils.getRawResX509Certificate(getContext(), R.raw.ec_key2_cert);
        PrivateKey privateKey2 = TestUtils.getRawResPrivateKey(getContext(), R.raw.ec_key2_pkcs8);

        Certificate cert3 = generateUserCertificate("EC");
        PrivateKey privateKey3 = generatePrivateKey("EC");

        final int MAX_NUMBER_OF_KEYS = 2500;
        final StringBuilder aliasPrefix = new StringBuilder("test_large_number_of_ec_keys_");
        int keyCount = 0;
        String entryName2 = null;

        mKeyStore.load(null);
        try {
            KeyProtection protectionParams =
                    new KeyProtection.Builder(KeyProperties.PURPOSE_SIGN)
                            .setDigests(KeyProperties.DIGEST_SHA256)
                            .build();
            mKeyStore.setEntry(
                    entryName1,
                    new KeyStore.PrivateKeyEntry(privateKey1, new Certificate[] {cert1}),
                    protectionParams);

            keyCount =
                    importKeyManyTimes(
                            MAX_NUMBER_OF_KEYS,
                            aliasPrefix,
                            new KeyStore.PrivateKeyEntry(privateKey3, new Certificate[] {cert3}),
                            protectionParams);

            keyCount++;
            entryName2 = "test" + keyCount;
            mKeyStore.setEntry(
                    entryName2,
                    new KeyStore.PrivateKeyEntry(privateKey2, new Certificate[] {cert2}),
                    protectionParams);
            PrivateKey keystorePrivateKey2 = (PrivateKey) mKeyStore.getKey(entryName2, null);
            PrivateKey keystorePrivateKey1 = (PrivateKey) mKeyStore.getKey(entryName1, null);

            byte[] message = "This is a test".getBytes("UTF-8");

            Signature sig = Signature.getInstance("SHA256withECDSA");
            sig.initSign(keystorePrivateKey1);
            sig.update(message);
            byte[] signature = sig.sign();
            sig = Signature.getInstance(sig.getAlgorithm());
            sig.initVerify(cert1.getPublicKey());
            sig.update(message);
            assertTrue(sig.verify(signature));

            sig = Signature.getInstance(sig.getAlgorithm());
            sig.initSign(keystorePrivateKey2);
            sig.update(message);
            signature = sig.sign();
            sig = Signature.getInstance(sig.getAlgorithm());
            sig.initVerify(cert2.getPublicKey());
            sig.update(message);
            assertTrue(sig.verify(signature));
        } finally {
            mKeyStore.deleteEntry(entryName1);
            deleteEntryIfNotNull(entryName2);
            deleteManyTestKeys(keyCount, aliasPrefix);
        }
    }

    // Previously called testKeyStore_LargeNumberOfKeysSupported_AES
    @LargeTest
    @Test
    public void importLargeNumberOfKeys_AES_success() throws Exception {
        // This test imports key1, then lots of other keys, then key2, and then confirms that
        // key1 and key2 backed by Android Keystore work fine. The assumption is that if the
        // underlying implementation has a limit on the number of keys, it'll either delete the
        // oldest key (key1), or will refuse to add keys (key2).
        // The test imports up MAX_NUMBER_OF_KEYS in a fixed amount of time, balancing the desire
        // to load many keys while also limiting maximum test time. This allows fast hardware to
        // run the test more quickly while also ensuring slower hardware loads as many keys as
        // possible within mMaxImportDuration.

        SecretKey key1 =
                new TransparentSecretKey(
                        HexEncoding.decode("010203040506070809fafbfcfdfeffcc"), "AES");
        String entryName1 = "test0";

        SecretKey key2 =
                new TransparentSecretKey(
                        HexEncoding.decode("808182838485868788897a7b7c7d7e7f"), "AES");

        SecretKey key3 =
                new TransparentSecretKey(
                        HexEncoding.decode("33333333333333333333777777777777"), "AES");

        final int MAX_NUMBER_OF_KEYS = 10000;
        final StringBuilder aliasPrefix = new StringBuilder("test_large_number_of_aes_keys_");
        int keyCount = 0;
        String entryName2 = null;

        mKeyStore.load(null);
        try {
            KeyProtection protectionParams =
                    new KeyProtection.Builder(KeyProperties.PURPOSE_ENCRYPT)
                            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                            .build();
            mKeyStore.setEntry(entryName1, new KeyStore.SecretKeyEntry(key1), protectionParams);

            keyCount =
                    importKeyManyTimes(
                            MAX_NUMBER_OF_KEYS,
                            aliasPrefix,
                            new KeyStore.SecretKeyEntry(key3),
                            protectionParams);

            ++keyCount;
            entryName2 = "test" + keyCount;
            mKeyStore.setEntry(entryName2, new KeyStore.SecretKeyEntry(key2), protectionParams);
            SecretKey keystoreKey2 = (SecretKey) mKeyStore.getKey(entryName2, null);
            SecretKey keystoreKey1 = (SecretKey) mKeyStore.getKey(entryName1, null);

            byte[] plaintext = "This is a test".getBytes("UTF-8");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keystoreKey1);
            byte[] ciphertext = cipher.doFinal(plaintext);
            AlgorithmParameters cipherParams = cipher.getParameters();
            cipher = Cipher.getInstance(cipher.getAlgorithm());
            cipher.init(Cipher.DECRYPT_MODE, key1, cipherParams);
            assertArrayEquals(plaintext, cipher.doFinal(ciphertext));

            cipher = Cipher.getInstance(cipher.getAlgorithm());
            cipher.init(Cipher.ENCRYPT_MODE, keystoreKey2);
            ciphertext = cipher.doFinal(plaintext);
            cipherParams = cipher.getParameters();
            cipher = Cipher.getInstance(cipher.getAlgorithm());
            cipher.init(Cipher.DECRYPT_MODE, key2, cipherParams);
            assertArrayEquals(plaintext, cipher.doFinal(ciphertext));
        } finally {
            mKeyStore.deleteEntry(entryName1);
            deleteEntryIfNotNull(entryName2);
            deleteManyTestKeys(keyCount, aliasPrefix);
        }
    }

    // Previously called testKeyStore_LargeNumberOfKeysSupported_HMAC
    @LargeTest
    @Test
    public void importLargeNumberOfKeys_HMAC_success() throws Exception {
        // This test imports key1, then lots of other keys, then key2, and then confirms that
        // key1 and key2 backed by Android Keystore work fine. The assumption is that if the
        // underlying implementation has a limit on the number of keys, it'll either delete the
        // oldest key (key1), or will refuse to add keys (key2).
        // The test imports as many keys as it can in a fixed amount of time instead of stopping
        // at MIN_SUPPORTED_KEY_COUNT to balance the desire to support an unlimited number of keys
        // with the constraints on how long the test can run and performance differences of hardware
        // under test.

        TimeBox timeBox = new TimeBox(mMaxImportDuration);

        SecretKey key1 =
                new TransparentSecretKey(
                        HexEncoding.decode("010203040506070809fafbfcfdfeffcc"), "HmacSHA256");
        String entryName1 = "test0";

        SecretKey key2 =
                new TransparentSecretKey(
                        HexEncoding.decode("808182838485868788897a7b7c7d7e7f"), "HmacSHA256");

        SecretKey key3 =
                new TransparentSecretKey(
                        HexEncoding.decode("33333333333333333333777777777777"), "HmacSHA256");

        final int MAX_NUMBER_OF_KEYS = 10000;
        final StringBuilder aliasPrefix = new StringBuilder("test_large_number_of_hmac_keys_");
        int keyCount = 0;
        String entryName2 = null;

        mKeyStore.load(null);
        try {
            KeyProtection protectionParams =
                    new KeyProtection.Builder(KeyProperties.PURPOSE_SIGN).build();
            mKeyStore.setEntry(entryName1, new KeyStore.SecretKeyEntry(key1), protectionParams);

            keyCount =
                    importKeyManyTimes(
                            MAX_NUMBER_OF_KEYS,
                            aliasPrefix,
                            new KeyStore.SecretKeyEntry(key3),
                            protectionParams);

            keyCount++;
            entryName2 = "test" + keyCount;
            mKeyStore.setEntry(entryName2, new KeyStore.SecretKeyEntry(key2), protectionParams);
            SecretKey keystoreKey2 = (SecretKey) mKeyStore.getKey(entryName2, null);
            SecretKey keystoreKey1 = (SecretKey) mKeyStore.getKey(entryName1, null);

            byte[] message = "This is a test".getBytes("UTF-8");
            Mac mac = Mac.getInstance(key1.getAlgorithm());
            mac.init(keystoreKey1);
            assertArrayEquals(
                    HexEncoding.decode(
                            "905e36f5a175f4ca54ad56b860b46f6502f883a90628dca2d33a953fb7224eaf"),
                    mac.doFinal(message));

            mac = Mac.getInstance(key2.getAlgorithm());
            mac.init(keystoreKey2);
            assertArrayEquals(
                    HexEncoding.decode(
                            "59b57e77e4e2cb36b5c7b84af198ac004327bc549de6931a1b5505372dd8c957"),
                    mac.doFinal(message));
        } finally {
            mKeyStore.deleteEntry(entryName1);
            deleteEntryIfNotNull(entryName2);
            deleteManyTestKeys(keyCount, aliasPrefix);
        }
    }

    // Previously called testKeyStore_OnlyOneDigestCanBeAuthorized_HMAC
    @Test
    public void onlyOneDigestCanBeAuthorized_HMAC() throws Exception {
        mKeyStore.load(null);

        for (String algorithm : KeyGeneratorTest.EXPECTED_ALGORITHMS) {
            if (!TestUtils.isHmacAlgorithm(algorithm)) {
                continue;
            }
            try {
                String digest = TestUtils.getHmacAlgorithmDigest(algorithm);
                assertNotNull(digest);
                SecretKey keyBeingImported = new TransparentSecretKey(new byte[16], algorithm);

                KeyProtection.Builder goodSpec =
                        new KeyProtection.Builder(KeyProperties.PURPOSE_SIGN);

                // Digests authorization not specified in import parameters
                assertFalse(goodSpec.build().isDigestsSpecified());
                mKeyStore.setEntry(
                        getTestAlias1(),
                        new KeyStore.SecretKeyEntry(keyBeingImported),
                        goodSpec.build());
                SecretKey key = (SecretKey) mKeyStore.getKey(getTestAlias1(), null);
                TestUtils.assertContentsInAnyOrder(
                        Arrays.asList(TestUtils.getKeyInfo(key).getDigests()), digest);

                // The same digest is specified in import parameters
                mKeyStore.setEntry(
                        getTestAlias1(),
                        new KeyStore.SecretKeyEntry(keyBeingImported),
                        TestUtils.buildUpon(goodSpec).setDigests(digest).build());
                key = (SecretKey) mKeyStore.getKey(getTestAlias1(), null);
                TestUtils.assertContentsInAnyOrder(
                        Arrays.asList(TestUtils.getKeyInfo(key).getDigests()), digest);

                // Empty set of digests specified in import parameters
                assertThrows(
                        KeyStoreException.class,
                        () ->
                                mKeyStore.setEntry(
                                        getTestAlias1(),
                                        new KeyStore.SecretKeyEntry(keyBeingImported),
                                        TestUtils.buildUpon(goodSpec).setDigests().build()));

                // A different digest specified in import parameters
                String anotherDigest = "SHA-256".equalsIgnoreCase(digest) ? "SHA-384" : "SHA-256";
                assertThrows(
                        KeyStoreException.class,
                        () ->
                                mKeyStore.setEntry(
                                        getTestAlias1(),
                                        new KeyStore.SecretKeyEntry(keyBeingImported),
                                        TestUtils.buildUpon(goodSpec)
                                                .setDigests(anotherDigest)
                                                .build()));
                assertThrows(
                        KeyStoreException.class,
                        () ->
                                mKeyStore.setEntry(
                                        getTestAlias1(),
                                        new KeyStore.SecretKeyEntry(keyBeingImported),
                                        TestUtils.buildUpon(goodSpec)
                                                .setDigests(digest, anotherDigest)
                                                .build()));
            } catch (Throwable e) {
                throw new RuntimeException("Failed for " + algorithm, e);
            }
        }
    }

    // Previously called testKeyStore_ImportSupportedSizes_AES
    @Test
    public void import_AES_success() throws Exception {
        mKeyStore.load(null);

        KeyProtection params =
                new KeyProtection.Builder(
                                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .build();
        String alias = "test1";
        mKeyStore.deleteEntry(alias);
        assertFalse(mKeyStore.containsAlias(alias));
        for (int keySizeBytes = 0; keySizeBytes <= 512 / 8; keySizeBytes++) {
            int keySizeBits = keySizeBytes * 8;
            try {
                KeyStore.SecretKeyEntry entry =
                        new KeyStore.SecretKeyEntry(
                                new TransparentSecretKey(new byte[keySizeBytes], "AES"));
                if (TestUtils.contains(KeyGeneratorTest.AES_SUPPORTED_KEY_SIZES, keySizeBits)) {
                    mKeyStore.setEntry(alias, entry, params);
                    SecretKey key = (SecretKey) mKeyStore.getKey(alias, null);
                    assertEquals("AES", key.getAlgorithm());
                    assertEquals(keySizeBits, TestUtils.getKeyInfo(key).getKeySize());
                } else {
                    mKeyStore.deleteEntry(alias);
                    assertFalse(mKeyStore.containsAlias(alias));
                    assertThrows(
                            KeyStoreException.class,
                            () -> mKeyStore.setEntry(alias, entry, params));
                    assertFalse(mKeyStore.containsAlias(alias));
                }
            } catch (Throwable e) {
                throw new RuntimeException("Failed for key size " + keySizeBits, e);
            }
        }
    }

    // Previously called testKeyStore_ImportSupportedSizes_HMAC
    @Test
    public void import_HMAC_success() throws Exception {
        mKeyStore.load(null);

        KeyProtection params = new KeyProtection.Builder(KeyProperties.PURPOSE_SIGN).build();
        String alias = "test1";
        mKeyStore.deleteEntry(alias);
        assertFalse(mKeyStore.containsAlias(alias));
        for (String algorithm : KeyGeneratorTest.EXPECTED_ALGORITHMS) {
            if (!TestUtils.isHmacAlgorithm(algorithm)) {
                continue;
            }
            for (int keySizeBytes = 8; keySizeBytes <= 1024 / 8; keySizeBytes++) {
                try {
                    KeyStore.SecretKeyEntry entry =
                            new KeyStore.SecretKeyEntry(
                                    new TransparentSecretKey(new byte[keySizeBytes], algorithm));
                    if (keySizeBytes > 0) {
                        mKeyStore.setEntry(alias, entry, params);
                        SecretKey key = (SecretKey) mKeyStore.getKey(alias, null);
                        assertEquals(algorithm, key.getAlgorithm());
                        assertEquals(keySizeBytes * 8, TestUtils.getKeyInfo(key).getKeySize());
                    } else {
                        mKeyStore.deleteEntry(alias);
                        assertFalse(mKeyStore.containsAlias(alias));
                        assertThrows(
                                KeyStoreException.class,
                                () -> mKeyStore.setEntry(alias, entry, params));
                    }
                } catch (Throwable e) {
                    throw new RuntimeException(
                            "Failed for " + algorithm + " with key size " + (keySizeBytes * 8), e);
                }
            }
        }
    }

    // Previously called testKeyStore_ImportSupportedSizes_EC
    @Test
    public void import_success_EC() throws Exception {
        mKeyStore.load(null);
        KeyProtection params =
                TestUtils.getMinimalWorkingImportParametersForSigningWith("SHA256withECDSA");
        checkKeyPairImportSucceeds(
                "secp224r1", R.raw.ec_key3_secp224r1_pkcs8, R.raw.ec_key3_secp224r1_cert, params);
        checkKeyPairImportSucceeds(
                "secp256r1", R.raw.ec_key4_secp256r1_pkcs8, R.raw.ec_key4_secp256r1_cert, params);
        checkKeyPairImportSucceeds(
                "secp384r1", R.raw.ec_key5_secp384r1_pkcs8, R.raw.ec_key5_secp384r1_cert, params);
        checkKeyPairImportSucceeds(
                "secp512r1", R.raw.ec_key6_secp521r1_pkcs8, R.raw.ec_key6_secp521r1_cert, params);
    }

    // Previously called testKeyStore_ImportSupportedSizes_RSA
    @Test
    public void import_success_RSA() throws Exception {
        mKeyStore.load(null);
        KeyProtection params =
                TestUtils.getMinimalWorkingImportParametersForSigningWith("SHA256withRSA");
        checkKeyPairImportSucceeds(
                "512", R.raw.rsa_key5_512_pkcs8, R.raw.rsa_key5_512_cert, params);
        checkKeyPairImportSucceeds(
                "768", R.raw.rsa_key6_768_pkcs8, R.raw.rsa_key6_768_cert, params);
        checkKeyPairImportSucceeds(
                "1024", R.raw.rsa_key3_1024_pkcs8, R.raw.rsa_key3_1024_cert, params);
        checkKeyPairImportSucceeds(
                "2048", R.raw.rsa_key8_2048_pkcs8, R.raw.rsa_key8_2048_cert, params);
        checkKeyPairImportSucceeds(
                "3072", R.raw.rsa_key7_3072_pksc8, R.raw.rsa_key7_3072_cert, params);
        checkKeyPairImportSucceeds(
                "4096", R.raw.rsa_key4_4096_pkcs8, R.raw.rsa_key4_4096_cert, params);
    }

    // Previously called testKeyStore_ImportSupported_X25519
    @Test
    public void import_success_X25519() throws Exception {
        mKeyStore.load(null);
        KeyProtection params = new KeyProtection.Builder(KeyProperties.PURPOSE_AGREE_KEY).build();
        checkKeyPairImportSucceeds("x25519", R.raw.x25519_pkcs8, R.raw.x25519_cert, params);
    }

    @Test
    public void import_success_Ed25519() throws Exception {
        mKeyStore.load(null);
        KeyProtection params = TestUtils.getMinimalWorkingImportParametersForSigningWith("Ed25519");
        checkKeyPairImportSucceeds("ed25519", R.raw.ed25519_pkcs8, R.raw.ed25519_cert, params);
    }

    private void checkKeyPairImportSucceeds(
            String alias, int privateResId, int certResId, KeyProtection params) throws Exception {
        try {
            mKeyStore.deleteEntry(alias);
            TestUtils.importIntoAndroidKeyStore(
                    alias, getContext(), privateResId, certResId, params);
        } catch (Throwable e) {
            throw new RuntimeException("Failed for " + alias, e);
        } finally {
            try {
                mKeyStore.deleteEntry(alias);
            } catch (Exception ignored) {
            }
        }
    }

    // TODO(b/395069350): Delete this method once parsing the test resources works.
    // Note: This can't be defined in TestUtils because it's in a different package and can't
    // access the test resources.
    private ImportedKey importMlDsaKeyIntoAndroidKeyStore(
            String alias, Context context, String algorithm) throws Exception {
        Certificate cert;
        PrivateKey privateKey;
        if (algorithm.equals(KeyProperties.KEY_ALGORITHM_ML_DSA_65)) {
            cert = TestUtils.getRawResX509Certificate(context, R.raw.mldsa65_cert);
            privateKey = TestUtils.getMlDsa65PrivateKey();
        } else if (algorithm.equals(KeyProperties.KEY_ALGORITHM_ML_DSA_87)) {
            cert = TestUtils.getRawResX509Certificate(context, R.raw.mldsa87_cert);
            privateKey = TestUtils.getMlDsa87PrivateKey();
        } else {
            throw new IllegalArgumentException("Unsupported ML-DSA algorithm: " + algorithm);
        }
        PublicKey publicKey = cert.getPublicKey();
        KeyProtection params = TestUtils.getMinimalWorkingImportParametersForSigningWith("ML-DSA");
        return TestUtils.importIntoAndroidKeyStore(alias, cert, publicKey, privateKey, params);
    }

    // Previously called testKeyStore_ImportSupported_MlDsa
    @Test
    @CddTest(requirements = {"9.11/C-1-2"})
    @RequiresFlagsEnabled(android.security.keystore2.Flags.FLAG_MLDSA_SUPPORT)
    public void import_success_MLDSA() throws Exception {
        TestUtils.assumeMlDsaSupported(/* useStrongBox= */ false);
        mKeyStore.load(null);
        String alias = "import-mldsa";
        String[] algorithms = {
            KeyProperties.KEY_ALGORITHM_ML_DSA_65, KeyProperties.KEY_ALGORITHM_ML_DSA_87
        };

        for (String algorithm : algorithms) {
            try {
                mKeyStore.deleteEntry(alias);
                // TODO(b/395069350): Call TestUtils.importIntoAndroidKeyStore to use the
                // DER-encoded private keys in cts/tests/tests/keystore/res/raw once parsing them
                // works.
                importMlDsaKeyIntoAndroidKeyStore(alias, getContext(), algorithm);
            } catch (Throwable e) {
                throw new RuntimeException("Failed for algorithm: " + algorithm, e);
            } finally {
                try {
                    mKeyStore.deleteEntry(alias);
                } catch (Exception ignored) {
                }
            }
        }
    }

    /**
     * Import <code>key</code> up to <code>numberOfKeys</code> times, using parameters generated by
     * <code>paramsBuilder</code>. This operation is done with multiple threads (one per logical
     * CPU) to both stress keystore as well as improve throughput. Each key alias is prefixed with
     * <code>aliasPrefix</code>.
     *
     * <p>This method is time-bounded
     */
    private int importKeyManyTimes(
            int numberOfKeys,
            StringBuilder aliasPrefix,
            Entry keyEntry,
            KeyProtection protectionParams,
            boolean isTimeBound)
            throws InterruptedException {
        TimeBox timeBox = new TimeBox(mMaxImportDuration);
        AtomicInteger keyCounter = new AtomicInteger(0);
        ArrayList<Thread> threads = new ArrayList<>();
        for (int i = 0; i < getMaxThreadCount(); ++i) {
            threads.add(
                    new Thread(
                            () -> {
                                // Import key lots of times, under different aliases. Do this until
                                // we either run
                                // out of time or we import the key numberOfKeys times.
                                while (!isTimeBound || !timeBox.isOutOfTime()) {
                                    int count = keyCounter.incrementAndGet();
                                    if (count > numberOfKeys) {
                                        // The loop is inherently racy, as multiple threads are
                                        // simultaneously
                                        // performing incrementAndGet operations. We only know if
                                        // we've hit the
                                        // limit _after_ we already incremented the counter. "Give
                                        // the count back"
                                        // before breaking so that we ensure keyCounter is accurate.
                                        keyCounter.decrementAndGet();
                                        break;
                                    }
                                    if ((count % 1000) == 0) {
                                        Log.i(TAG, "Imported " + count + " keys");
                                    }
                                    String entryAlias = aliasPrefix.toString() + count;
                                    try {
                                        mKeyStore.setEntry(entryAlias, keyEntry, protectionParams);
                                    } catch (Throwable e) {
                                        throw new RuntimeException(
                                                "Entry " + entryAlias + " import failed", e);
                                    }
                                }
                            }));
        }
        // Start all the threads as close to one another as possible to spread the load evenly
        for (int i = 0; i < threads.size(); ++i) {
            threads.get(i).start();
        }
        for (int i = 0; i < threads.size(); ++i) {
            threads.get(i).join();
        }
        Log.i(TAG, "Imported " + keyCounter.get() + " keys in " + timeBox.elapsed());
        if (keyCounter.get() != numberOfKeys && keyCounter.get() < MIN_SUPPORTED_KEY_COUNT) {
            fail(
                    "Failed to import "
                            + MIN_SUPPORTED_KEY_COUNT
                            + " keys in "
                            + timeBox.elapsed()
                            + ". Imported: "
                            + keyCounter.get()
                            + " keys");
        }

        return keyCounter.get();
    }

    private int importKeyManyTimes(
            int numberOfKeys,
            StringBuilder aliasPrefix,
            Entry keyEntry,
            KeyProtection protectionParams)
            throws InterruptedException {
        return importKeyManyTimes(numberOfKeys, aliasPrefix, keyEntry, protectionParams, true);
    }

    private int importKeyManyTimesWithoutTimeLimit(
            int numberOfKeys,
            StringBuilder aliasPrefix,
            Entry keyEntry,
            KeyProtection protectionParams)
            throws InterruptedException {
        return importKeyManyTimes(numberOfKeys, aliasPrefix, keyEntry, protectionParams, false);
    }

    /**
     * Delete <code>numberOfKeys</code> keys that follow the pattern "[aliasPrefix][keyCounter]".
     * This is done across multiple threads to both increase throughput as well as stress keystore.
     */
    private void deleteManyTestKeys(int numberOfKeys, StringBuilder aliasPrefix)
            throws InterruptedException {
        // Clean up Keystore without using KeyStore.aliases() which can't handle this many
        // entries.
        AtomicInteger keyCounter = new AtomicInteger(numberOfKeys);
        Log.i(TAG, "Deleting imported keys");
        ArrayList<Thread> threads = new ArrayList<>();
        for (int i = 0; i < getMaxThreadCount(); ++i) {
            Log.i(TAG, "Spinning up cleanup thread " + i);
            threads.add(
                    new Thread(
                            () -> {
                                for (int key = keyCounter.getAndDecrement();
                                        key > 0;
                                        key = keyCounter.getAndDecrement()) {
                                    if ((key > 0) && ((key % 1000) == 0)) {
                                        Log.i(TAG, "Deleted " + key + " keys");
                                    }
                                    String entryAlias = aliasPrefix.toString() + key;
                                    try {
                                        mKeyStore.deleteEntry(entryAlias);
                                    } catch (Exception e) {
                                        fail("Unexpected exception in key cleanup: " + e);
                                    }
                                }
                            }));
        }
        for (int i = 0; i < threads.size(); ++i) {
            threads.get(i).start();
        }
        for (int i = 0; i < threads.size(); ++i) {
            Log.i(TAG, "Joining test thread " + i);
            threads.get(i).join();
        }
        Log.i(TAG, "Deleted " + numberOfKeys + " keys");
    }

    private Set<String> createLargeNumberOfRsaKeyStoreEntryAliases(
            int numberOfKeys, StringBuilder aliasPrefix) throws Exception {
        Certificate cert = generateUserCertificate("RSA");
        PrivateKey privateKey = generatePrivateKey("RSA");

        mKeyStore.load(null);
        KeyProtection protectionParams =
                new KeyProtection.Builder(KeyProperties.PURPOSE_SIGN)
                        .setDigests(KeyProperties.DIGEST_SHA256)
                        .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                        .build();

        int keyCount =
                importKeyManyTimesWithoutTimeLimit(
                        numberOfKeys,
                        aliasPrefix,
                        new PrivateKeyEntry(privateKey, new Certificate[] {cert}),
                        protectionParams);

        // Construct expected aliases list.
        final Set<String> expectedAliases = new HashSet<>(keyCount);
        for (int count = 1; count <= keyCount; count++) {
            String entryAlias = aliasPrefix.toString() + count;
            expectedAliases.add(entryAlias);
        }

        return expectedAliases;
    }

    private void importLargeNumberOfRsaKeysAndValidateAliases(
            int numberOfKeys, StringBuilder aliasPrefix) throws Exception {
        Set<String> importedKeyAliases =
                createLargeNumberOfRsaKeyStoreEntryAliases(numberOfKeys, aliasPrefix);
        assertThat(importedKeyAliases.size()).isEqualTo(numberOfKeys);

        try {
            // b/222287335 Currently, limiting Keystore `listEntries` API to return subset of the
            // Keystore entries to avoid running out of binder buffer space.
            // To verify that all the imported key aliases are present in Keystore, get the list of
            // aliases from Keystore, delete the matched aliases from Keystore and imported list of
            // key aliases, continue this till all the imported key aliases are matched.
            while (!importedKeyAliases.isEmpty()) {
                // List the keystore entries aliases until all the imported key aliases are matched.
                Set<String> aliases = new HashSet<String>(Collections.list(mKeyStore.aliases()));

                // Try to match the aliases with imported key aliases.
                // Cleanup matching aliases from Keystore and imported key aliases list.
                for (String alias : aliases) {
                    if (importedKeyAliases.contains(alias)) {
                        mKeyStore.deleteEntry(alias);
                        importedKeyAliases.remove(alias);
                    }
                }
            }
            assertTrue("Failed to match imported keystore entries.", importedKeyAliases.isEmpty());
        } finally {
            if (!importedKeyAliases.isEmpty()) {
                Log.i(TAG, "Final cleanup of imported keys");
                for (String alias : importedKeyAliases) {
                    mKeyStore.deleteEntry(alias);
                }
            }
        }
        assertTrue(importedKeyAliases.isEmpty());
    }

    /** Create long alias prefix of length 6000 characters. */
    private StringBuilder createLongAliasPrefix() {
        char[] prefixChar = new char[6000];
        Arrays.fill(prefixChar, 'T');
        StringBuilder prefixAlias = new StringBuilder();
        prefixAlias.append(prefixChar);

        return prefixAlias;
    }

    /**
     * Create large number of Keystore entries with long aliases and try to list aliases of all the
     * entries in the keystore.
     */
    // Previously called testKeyStore_LargeNumberOfLongAliases
    @ApiTest(apis = {"java.security.KeyStore#aliases"})
    @Test
    public void importLargeNumberOfLongAliases_success_RSA() throws Exception {
        final int maxNumberOfKeys = 100;
        importLargeNumberOfRsaKeysAndValidateAliases(maxNumberOfKeys, createLongAliasPrefix());
    }

    /**
     * Create limited number of Keystore entries with long aliases and try to list aliases of all
     * the entries in the keystore. Test should successfully list all the Keystore entries aliases.
     */
    // Previously called testKeyStore_LimitedNumberOfLongAliasesSuccess
    @ApiTest(apis = {"java.security.KeyStore#aliases"})
    @Test
    public void importLimitedNumberOfLongAliases_success_RSA() throws Exception {
        final int maxNumberOfKeys = 10;
        importLargeNumberOfRsaKeysAndValidateAliases(maxNumberOfKeys, createLongAliasPrefix());
    }

    /**
     * Create large number of Keystore entries with short length aliases and try to list aliases of
     * all the entries in the keystore. Test should successfully list all the Keystore entries
     * aliases.
     */
    // Previously called testKeyStore_LargeNumberShortAliasesSuccess
    @ApiTest(apis = {"java.security.KeyStore#aliases"})
    @Test
    public void importLargeNumberOfShortAliases_success_RSA() throws Exception {
        final int maxNumberOfKeys = 2500;
        final StringBuilder aliasPrefix = new StringBuilder("test_short_key_alias_");
        importLargeNumberOfRsaKeysAndValidateAliases(maxNumberOfKeys, aliasPrefix);
    }

    /**
     * Some VTS tests are skipped on pre-production devices. Ensure that a pre-production device
     * does not pass CTS (so a passing CTS result implies that the full set of VTS tests apply).
     */
    @Test
    public void testProductionDevice() throws Exception {
        assertFalse(
                "The device must not be marked as a pre-production device (in the "
                        + "ro.boot.hardware.revision property)",
                KeyGenerationUtils.isPreProductionDevice());
    }
}
