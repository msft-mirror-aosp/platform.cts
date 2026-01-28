/*
 * Copyright (C) 2015 The Android Open Source Project
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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;

import android.content.Context;
import android.keystore.cts.R;
import android.keystore.cts.util.TestUtils;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyInfo;
import android.security.keystore.KeyProperties;
import android.test.MoreAsserts;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.CddTest;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.Provider.Service;
import java.security.PublicKey;
import java.security.Security;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPrivateKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

@RunWith(AndroidJUnit4.class)
@CddTest(requirements = {"9.11/C-1-2"})
public abstract class KeyFactoryTest {
    private static final String EXPECTED_PROVIDER_NAME = TestUtils.EXPECTED_PROVIDER_NAME;

    protected abstract String[] getExpectedAlgorithms();

    private Context getContext() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    @Test
    public void testAlgorithmList() {
        // Assert that Android Keystore Provider exposes exactly the expected KeyFactory algorithms.
        // We don't care whether the algorithms are exposed via aliases, as long as canonical names
        // of algorithms are accepted. If the Provider exposes extraneous algorithms, it'll be
        // caught because it'll have to expose at least one Service for such an algorithm, and this
        // Service's algorithm will not be in the expected set.

        Provider provider = Security.getProvider(EXPECTED_PROVIDER_NAME);
        Set<Service> services = provider.getServices();
        Set<String> actualAlgsLowerCase = new HashSet<String>();
        Set<String> expectedAlgsLowerCase = new HashSet<String>(
                Arrays.asList(TestUtils.toLowerCase(getExpectedAlgorithms())));

        // XDH is also a supported algorithm, but not available for other tests as the keys
        // generated with it have more limited set of uses.
        expectedAlgsLowerCase.add("xdh");
        if (TestUtils.isEd25519AlgorithmExpectedToSupport()) {
            // AndroidKeyStore supports key generation of curve Ed25519 from Android V preview
            expectedAlgsLowerCase.add("ed25519");
        }

        for (Service service : services) {
            if ("KeyFactory".equalsIgnoreCase(service.getType())) {
                String algLowerCase = service.getAlgorithm().toLowerCase(Locale.US);
                actualAlgsLowerCase.add(algLowerCase);
            }
        }

        TestUtils.assertContentsInAnyOrder(actualAlgsLowerCase,
                expectedAlgsLowerCase.toArray(new String[0]));
    }

    @Test
    public void testGetKeySpecWithKeystorePrivateKeyAndKeyInfoReflectsAllAuthorizations()
            throws Exception {
        Date keyValidityStart = new Date(System.currentTimeMillis() - TestUtils.DAY_IN_MILLIS);
        Date keyValidityForOriginationEnd =
                new Date(System.currentTimeMillis() + TestUtils.DAY_IN_MILLIS);
        Date keyValidityForConsumptionEnd =
                new Date(System.currentTimeMillis() + 3 * TestUtils.DAY_IN_MILLIS);
        for (String algorithm : getExpectedAlgorithms()) {
            try {
                String[] blockModes = new String[] {KeyProperties.BLOCK_MODE_ECB};
                String[] encryptionPaddings =
                        new String[] {KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1,
                                KeyProperties.ENCRYPTION_PADDING_RSA_OAEP};
                String[] digests;
                if (algorithm.startsWith("ML-DSA")) {
                    digests = new String[] {KeyProperties.DIGEST_NONE};
                } else {
                    digests =
                            new String[] {
                                KeyProperties.DIGEST_SHA1,
                                KeyProperties.DIGEST_SHA224,
                                KeyProperties.DIGEST_SHA384,
                                KeyProperties.DIGEST_SHA512
                            };
                }
                int purposes = KeyProperties.PURPOSE_SIGN;
                KeyPairGenerator keyGenerator =
                        KeyPairGenerator.getInstance(algorithm, EXPECTED_PROVIDER_NAME);
                keyGenerator.initialize(new KeyGenParameterSpec.Builder("test1", purposes)
                        .setBlockModes(blockModes)
                        .setEncryptionPaddings(encryptionPaddings)
                        .setDigests(digests)
                        .setKeyValidityStart(keyValidityStart)
                        .setKeyValidityForOriginationEnd(keyValidityForOriginationEnd)
                        .setKeyValidityForConsumptionEnd(keyValidityForConsumptionEnd)
                        .build());
                KeyPair keyPair = keyGenerator.generateKeyPair();
                KeyFactory keyFactory = getKeyFactory(algorithm);
                KeyInfo keyInfo = keyFactory.getKeySpec(keyPair.getPrivate(), KeyInfo.class);
                assertEquals("test1", keyInfo.getKeystoreAlias());
                assertEquals(purposes, keyInfo.getPurposes());
                TestUtils.assertContentsInAnyOrder(
                        Arrays.asList(keyInfo.getBlockModes()), blockModes);

                List<String> actualEncryptionPaddings =
                        new ArrayList<String>(Arrays.asList(keyInfo.getEncryptionPaddings()));
                // Keystore may have added ENCRYPTION_PADDING_NONE to allow software padding.
                actualEncryptionPaddings.remove(KeyProperties.ENCRYPTION_PADDING_NONE);
                TestUtils.assertContentsInAnyOrder(
                        actualEncryptionPaddings, encryptionPaddings);

                List<String> actualDigests =
                        new ArrayList<String>(Arrays.asList(keyInfo.getDigests()));
                if (!algorithm.startsWith("ML-DSA")) {
                    // Keystore may have added DIGEST_NONE to allow software digesting. For ML-DSA,
                    // we set DIGEST_NONE in the spec, so we expect it to appear in the KeyInfo.
                    actualDigests.remove(KeyProperties.DIGEST_NONE);
                }
                TestUtils.assertContentsInAnyOrder(actualDigests, digests);

                MoreAsserts.assertEmpty(Arrays.asList(keyInfo.getSignaturePaddings()));
                assertEquals(keyValidityStart, keyInfo.getKeyValidityStart());
                assertEquals(keyValidityForOriginationEnd,
                        keyInfo.getKeyValidityForOriginationEnd());
                assertEquals(keyValidityForConsumptionEnd,
                        keyInfo.getKeyValidityForConsumptionEnd());
                assertFalse(keyInfo.isUserAuthenticationRequired());
                assertFalse(keyInfo.isUserAuthenticationRequirementEnforcedBySecureHardware());
            } catch (Throwable e) {
                throw new RuntimeException("Failed for " + algorithm, e);
            }
        }
    }

    @Test
    public void testGetKeySpecWithKeystorePublicKeyRejectsKeyInfo()
            throws Exception {
        for (String algorithm : getExpectedAlgorithms()) {
            try {
                KeyPairGenerator keyGenerator =
                        KeyPairGenerator.getInstance(algorithm, EXPECTED_PROVIDER_NAME);
                keyGenerator.initialize(
                        new KeyGenParameterSpec.Builder("test1", KeyProperties.PURPOSE_SIGN)
                                .build());
                KeyPair keyPair = keyGenerator.generateKeyPair();
                KeyFactory keyFactory = getKeyFactory(algorithm);
                try {
                    keyFactory.getKeySpec(keyPair.getPublic(), KeyInfo.class);
                    fail();
                } catch (InvalidKeySpecException expected) {}
            } catch (Throwable e) {
                throw new RuntimeException("Failed for " + algorithm, e);
            }
        }
    }

    // A "transparent key spec" is a class that implements java.security.spec.KeySpec and exposes
    // information about a key. The only such class that Android Keystore supports for its
    // private keys is android.security.keystore.KeyInfo.
    @Test
    public void testGetKeySpecWithKeystorePrivateKeyRejectsTransparentKeySpec()
            throws Exception {
        for (String algorithm : getExpectedAlgorithms()) {
            try {
                Class<? extends KeySpec> transparentKeySpecClass;
                if ("EC".equalsIgnoreCase(algorithm)) {
                    transparentKeySpecClass = ECPrivateKeySpec.class;
                } else if ("RSA".equalsIgnoreCase(algorithm)) {
                    transparentKeySpecClass = RSAPrivateKeySpec.class;
                } else if (algorithm.startsWith("ML-DSA")) {
                    // There is no transparent key spec for ML-DSA private keys, so skip.
                    continue;
                } else {
                    throw new RuntimeException("Unsupported key algorithm: " + algorithm);
                }

                KeyPairGenerator keyGenerator =
                        KeyPairGenerator.getInstance(algorithm, EXPECTED_PROVIDER_NAME);
                keyGenerator.initialize(
                        new KeyGenParameterSpec.Builder("test1", KeyProperties.PURPOSE_SIGN)
                                .build());
                KeyPair keyPair = keyGenerator.generateKeyPair();

                KeyFactory keyFactory = getKeyFactory(algorithm);
                try {
                    keyFactory.getKeySpec(keyPair.getPrivate(), transparentKeySpecClass);
                    fail();
                } catch (InvalidKeySpecException expected) {}
            } catch (Throwable e) {
                throw new RuntimeException("Failed for " + algorithm, e);
            }
        }
    }

    @Test
    public void testGetKeySpecWithKeystorePrivateKeyRejectsPKCS8EncodedKeySpec()
            throws Exception {
        for (String algorithm : getExpectedAlgorithms()) {
            try {
                KeyPairGenerator keyGenerator =
                        KeyPairGenerator.getInstance(algorithm, EXPECTED_PROVIDER_NAME);
                keyGenerator.initialize(
                        new KeyGenParameterSpec.Builder("test1", KeyProperties.PURPOSE_SIGN)
                                .build());
                KeyPair keyPair = keyGenerator.generateKeyPair();
                KeyFactory keyFactory = getKeyFactory(algorithm);
                try {
                    keyFactory.getKeySpec(keyPair.getPrivate(), PKCS8EncodedKeySpec.class);
                    fail();
                } catch (InvalidKeySpecException expected) {}
            } catch (Throwable e) {
                throw new RuntimeException("Failed for " + algorithm, e);
            }
        }
    }

    @Test
    public void testGetKeySpecWithKeystorePublicKeyAcceptsX509EncodedKeySpec()
            throws Exception {
        for (String algorithm : getExpectedAlgorithms()) {
            try {
                KeyPairGenerator keyGenerator =
                        KeyPairGenerator.getInstance(algorithm, EXPECTED_PROVIDER_NAME);
                keyGenerator.initialize(
                        new KeyGenParameterSpec.Builder("test1", KeyProperties.PURPOSE_SIGN)
                                .build());
                KeyPair keyPair = keyGenerator.generateKeyPair();
                PublicKey publicKey = keyPair.getPublic();

                KeyFactory keyFactory = getKeyFactory(algorithm);
                X509EncodedKeySpec x509EncodedSpec =
                        keyFactory.getKeySpec(publicKey, X509EncodedKeySpec.class);
                MoreAsserts.assertEquals(publicKey.getEncoded(), x509EncodedSpec.getEncoded());
            } catch (Throwable e) {
                throw new RuntimeException("Failed for " + algorithm, e);
            }
        }
    }

    @Test
    public void testGetKeySpecWithKeystorePublicKeyAcceptsTransparentKeySpec()
            throws Exception {
        for (String algorithm : getExpectedAlgorithms()) {
            try {
                KeyPairGenerator keyGenerator =
                        KeyPairGenerator.getInstance(algorithm, EXPECTED_PROVIDER_NAME);
                keyGenerator.initialize(
                        new KeyGenParameterSpec.Builder("test1", KeyProperties.PURPOSE_SIGN)
                                .build());
                KeyPair keyPair = keyGenerator.generateKeyPair();
                PublicKey publicKey = keyPair.getPublic();

                KeyFactory keyFactory = getKeyFactory(algorithm);
                if ("EC".equalsIgnoreCase(algorithm)) {
                    ECPublicKey ecPublicKey = (ECPublicKey) publicKey;
                    ECPublicKeySpec spec =
                            keyFactory.getKeySpec(publicKey, ECPublicKeySpec.class);
                    assertEquals(ecPublicKey.getW(), spec.getW());
                    TestUtils.assertECParameterSpecEqualsIgnoreSeedIfNotPresent(
                            ecPublicKey.getParams(), spec.getParams());
                } else if ("RSA".equalsIgnoreCase(algorithm)) {
                    RSAPublicKey rsaPublicKey = (RSAPublicKey) publicKey;
                    RSAPublicKeySpec spec =
                            keyFactory.getKeySpec(publicKey, RSAPublicKeySpec.class);
                    assertEquals(rsaPublicKey.getModulus(), spec.getModulus());
                    assertEquals(rsaPublicKey.getPublicExponent(), spec.getPublicExponent());
                } else if (algorithm.startsWith("ML-DSA")) {
                    X509EncodedKeySpec keySpec =
                            keyFactory.getKeySpec(publicKey, X509EncodedKeySpec.class);
                    assertEquals(keySpec.getFormat(), "X.509");
                    assertArrayEquals(keySpec.getEncoded(), publicKey.getEncoded());
                } else {
                    throw new RuntimeException("Unsupported key algorithm: " + algorithm);
                }
            } catch (Throwable e) {
                throw new RuntimeException("Failed for " + algorithm, e);
            }
        }
    }

    @Test
    public void testTranslateKeyWithNullKeyThrowsInvalidKeyException() throws Exception {
        for (String algorithm : getExpectedAlgorithms()) {
            try {
                KeyFactory keyFactory = getKeyFactory(algorithm);
                try {
                    keyFactory.translateKey(null);
                    fail();
                } catch (InvalidKeyException expected) {}
            } catch (Throwable e) {
                throw new RuntimeException("Failed for " + algorithm, e);
            }
        }
    }

    @Test
    public void testTranslateKeyRejectsNonAndroidKeystoreKeys() throws Exception {
        for (String algorithm : getExpectedAlgorithms()) {
            try {
                SecretKey key = new SecretKeySpec(new byte[16], algorithm);
                KeyFactory keyFactory = getKeyFactory(algorithm);
                try {
                    keyFactory.translateKey(key);
                    fail();
                } catch (InvalidKeyException expected) {}
            } catch (Throwable e) {
                throw new RuntimeException("Failed for " + algorithm, e);
            }
        }
    }

    @Test
    public void testTranslateKeyAcceptsAndroidKeystoreKeys() throws Exception {
        for (String algorithm : getExpectedAlgorithms()) {
            try {
                KeyPairGenerator keyGenerator =
                        KeyPairGenerator.getInstance(algorithm, EXPECTED_PROVIDER_NAME);
                keyGenerator.initialize(
                        new KeyGenParameterSpec.Builder("test1", KeyProperties.PURPOSE_SIGN)
                                .build());
                KeyPair keyPair = keyGenerator.generateKeyPair();

                KeyFactory keyFactory = getKeyFactory(algorithm);
                assertSame(keyPair.getPrivate(), keyFactory.translateKey(keyPair.getPrivate()));
                assertSame(keyPair.getPublic(), keyFactory.translateKey(keyPair.getPublic()));
            } catch (Throwable e) {
                throw new RuntimeException("Failed for " + algorithm, e);
            }
        }
    }

    @Test
    public void testGeneratePrivateWithNullSpecThrowsInvalidKeySpecException() throws Exception {
        for (String algorithm : getExpectedAlgorithms()) {
            try {
                KeyFactory keyFactory = getKeyFactory(algorithm);
                try {
                    keyFactory.generatePrivate(null);
                    fail();
                } catch (InvalidKeySpecException expected) {}
            } catch (Throwable e) {
                throw new RuntimeException("Failed for " + algorithm, e);
            }
        }
    }

    @Test
    public void testGeneratePublicWithNullSpecThrowsInvalidKeySpecException() throws Exception {
        for (String algorithm : getExpectedAlgorithms()) {
            try {
                KeyFactory keyFactory = getKeyFactory(algorithm);
                try {
                    keyFactory.generatePublic(null);
                    fail();
                } catch (InvalidKeySpecException expected) {}
            } catch (Throwable e) {
                throw new RuntimeException("Failed for " + algorithm, e);
            }
        }
    }

    @Test
    public void testGeneratePrivateRejectsPKCS8EncodedKeySpec() throws Exception {
        for (String algorithm : getExpectedAlgorithms()) {
            int resId;
            if ("EC".equalsIgnoreCase(algorithm)) {
                resId = R.raw.ec_key1_pkcs8;
            } else if ("RSA".equalsIgnoreCase(algorithm)) {
                resId = R.raw.rsa_key2_pkcs8;
            } else if ("ML-DSA".equalsIgnoreCase(algorithm)
                    || "ML-DSA-65".equalsIgnoreCase(algorithm)) {
                resId = R.raw.mldsa65_pkcs8;
            } else if ("ML-DSA-87".equalsIgnoreCase(algorithm)) {
                resId = R.raw.mldsa87_pkcs8;
            } else {
                throw new RuntimeException("Unsupported key algorithm: " + algorithm);
            }

            byte[] pkcs8EncodedForm;
            try (InputStream in = getContext().getResources().openRawResource(resId)) {
                pkcs8EncodedForm = TestUtils.drain(in);
            }
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(pkcs8EncodedForm);
            try {
                KeyFactory keyFactory = getKeyFactory(algorithm);
                try {
                    keyFactory.generatePrivate(spec);
                    fail();
                } catch (InvalidKeySpecException expected) {}
            } catch (Throwable e) {
                throw new RuntimeException("Failed for " + algorithm, e);
            }
        }
    }

    @Test
    public void testGeneratePublicRejectsX509EncodedKeySpec() throws Exception {
        for (String algorithm : getExpectedAlgorithms()) {
            int resId;
            if ("EC".equalsIgnoreCase(algorithm)) {
                resId = R.raw.ec_key2_cert;
            } else if ("RSA".equalsIgnoreCase(algorithm)) {
                resId = R.raw.rsa_key1_cert;
            } else if ("ML-DSA".equalsIgnoreCase(algorithm)
                    || "ML-DSA-65".equalsIgnoreCase(algorithm)) {
                resId = R.raw.mldsa65_cert;
            } else if ("ML-DSA-87".equalsIgnoreCase(algorithm)) {
                resId = R.raw.mldsa87_cert;
            } else {
                throw new RuntimeException("Unsupported key algorithm: " + algorithm);
            }

            byte[] x509EncodedForm;
            try (InputStream in = getContext().getResources().openRawResource(resId)) {
                x509EncodedForm = TestUtils.drain(in);
            }
            X509EncodedKeySpec spec = new X509EncodedKeySpec(x509EncodedForm);
            try {
                KeyFactory keyFactory = getKeyFactory(algorithm);
                try {
                    keyFactory.generatePublic(spec);
                    fail();
                } catch (InvalidKeySpecException expected) {}
            } catch (Throwable e) {
                throw new RuntimeException("Failed for " + algorithm, e);
            }
        }
    }

    @Test
    public void testGeneratePrivateRejectsTransparentKeySpec() throws Exception {
        for (String algorithm : getExpectedAlgorithms()) {
            // TODO(b/395069350): Remove this once testGeneratePrivateRejectsTransparentKeySpecMlDsa
            // is merged into this test (see TODO on that test).
            if (algorithm.startsWith("ML-DSA")) {
                continue;
            }

            int resId;
            Class<? extends KeySpec> keySpecClass;
            if ("EC".equalsIgnoreCase(algorithm)) {
                resId = R.raw.ec_key2_pkcs8;
                keySpecClass = ECPrivateKeySpec.class;
            } else if ("RSA".equalsIgnoreCase(algorithm)) {
                resId = R.raw.rsa_key2_pkcs8;
                keySpecClass = RSAPrivateKeySpec.class;
            } else {
                throw new RuntimeException("Unsupported key algorithm: " + algorithm);
            }
            PrivateKey key = TestUtils.getRawResPrivateKey(getContext(), resId);
            KeyFactory anotherKeyFactory = KeyFactory.getInstance(algorithm);
            KeySpec spec = anotherKeyFactory.getKeySpec(key, keySpecClass);

            try {
                KeyFactory keyFactory = getKeyFactory(algorithm);
                try {
                    keyFactory.generatePrivate(spec);
                    fail();
                } catch (InvalidKeySpecException expected) {}
            } catch (Throwable e) {
                throw new RuntimeException("Failed for " + algorithm, e);
            }
        }
    }

    // TODO(b/395069350): Merge this test into testGeneratePrivateRejectsTransparentKeySpec once
    // we figure out why key generation fails with the ML-DSA test resources.
    @Test
    public void testGeneratePrivateRejectsTransparentKeySpecMlDsa() throws Exception {
        KeyFactory keyFactory = getKeyFactory(KeyProperties.KEY_ALGORITHM_ML_DSA);
        KeyFactory anotherKeyFactory = KeyFactory.getInstance(KeyProperties.KEY_ALGORITHM_ML_DSA);

        // ML-DSA-65
        PrivateKey key = TestUtils.getMlDsa65PrivateKey();
        KeySpec spec65 = anotherKeyFactory.getKeySpec(key, PKCS8EncodedKeySpec.class);
        assertThrows(InvalidKeySpecException.class, () -> keyFactory.generatePrivate(spec65));

        // ML-DSA-87
        key = TestUtils.getMlDsa87PrivateKey();
        KeySpec spec87 = anotherKeyFactory.getKeySpec(key, PKCS8EncodedKeySpec.class);
        assertThrows(InvalidKeySpecException.class, () -> keyFactory.generatePrivate(spec87));
    }

    @Test
    public void testGeneratePublicRejectsTransparentKeySpec() throws Exception {
        for (String algorithm : getExpectedAlgorithms()) {
            int resId;
            Class<? extends KeySpec> keySpecClass;
            if ("EC".equalsIgnoreCase(algorithm)) {
                resId = R.raw.ec_key2_cert;
                keySpecClass = ECPublicKeySpec.class;
            } else if ("RSA".equalsIgnoreCase(algorithm)) {
                resId = R.raw.rsa_key2_cert;
                keySpecClass = RSAPublicKeySpec.class;
            } else if ("ML-DSA".equalsIgnoreCase(algorithm)
                    || "ML-DSA-65".equalsIgnoreCase(algorithm)) {
                resId = R.raw.mldsa65_cert;
                keySpecClass = X509EncodedKeySpec.class;
            } else if ("ML-DSA-87".equalsIgnoreCase(algorithm)) {
                resId = R.raw.mldsa87_cert;
                keySpecClass = X509EncodedKeySpec.class;
            } else {
                throw new RuntimeException("Unsupported key algorithm: " + algorithm);
            }
            PublicKey key = TestUtils.getRawResX509Certificate(getContext(), resId).getPublicKey();
            KeyFactory anotherKeyFactory = KeyFactory.getInstance(algorithm);
            KeySpec spec = anotherKeyFactory.getKeySpec(key, keySpecClass);

            try {
                KeyFactory keyFactory = getKeyFactory(algorithm);
                try {
                    keyFactory.generatePublic(spec);
                    fail();
                } catch (InvalidKeySpecException expected) {}
            } catch (Throwable e) {
                throw new RuntimeException("Failed for " + algorithm, e);
            }
        }
    }

    @Test
    public void testGeneratePrivateAndPublicRejectKeyInfo() throws Exception {
        for (String algorithm : getExpectedAlgorithms()) {
            try {
                KeyPairGenerator keyGenerator =
                        KeyPairGenerator.getInstance(algorithm, EXPECTED_PROVIDER_NAME);
                keyGenerator.initialize(
                        new KeyGenParameterSpec.Builder("test1", KeyProperties.PURPOSE_SIGN)
                                .build());
                KeyPair keyPair = keyGenerator.generateKeyPair();
                KeyInfo keyInfo = TestUtils.getKeyInfo(keyPair.getPrivate());

                KeyFactory keyFactory = getKeyFactory(algorithm);
                try {
                    keyFactory.generatePrivate(keyInfo);
                    fail();
                } catch (InvalidKeySpecException expected) {}

                try {
                    keyFactory.generatePublic(keyInfo);
                    fail();
                } catch (InvalidKeySpecException expected) {}
            } catch (Throwable e) {
                throw new RuntimeException("Failed for " + algorithm, e);
            }
        }
    }

    private KeyFactory getKeyFactory(String algorithm) throws NoSuchAlgorithmException,
            NoSuchProviderException {
        return KeyFactory.getInstance(algorithm, EXPECTED_PROVIDER_NAME);
    }
}
