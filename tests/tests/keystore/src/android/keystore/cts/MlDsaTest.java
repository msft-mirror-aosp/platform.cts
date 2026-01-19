/*
 * Copyright (C) 2025 The Android Open Source Project
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

import static org.junit.Assert.assertThrows;

import android.keystore.cts.util.TestUtils;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyInfo;
import android.security.keystore.KeyProperties;
import android.security.keystore2.AndroidKeyStoreMlDsaPrivateKey;
import android.security.keystore2.AndroidKeyStoreMlDsaPublicKey;

import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.CddTest;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Signature;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;

/**
 * Tests that the ML-DSA KeyPairGenerator, Signature, and KeyFactory implementations comply with <a
 * href="https://openjdk.org/jeps/497">JEP 497</a>. The actual functionality of these
 * implementations is tested in the respective test classes that cover all algorithms supported by
 * Android Keystore.
 */
@CddTest(requirements = {"9.11/C-1-2"})
@RequiresFlagsEnabled(android.security.keystore2.Flags.FLAG_MLDSA_SUPPORT)
@RunWith(AndroidJUnit4.class)
public class MlDsaTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    private KeyPairGenerator getKeyPairGenerator(String algorithm)
            throws NoSuchAlgorithmException, NoSuchProviderException {
        return KeyPairGenerator.getInstance(algorithm, TestUtils.EXPECTED_PROVIDER_NAME);
    }

    private KeyFactory getKeyFactory(String algorithm)
            throws NoSuchAlgorithmException, NoSuchProviderException {
        return KeyFactory.getInstance(algorithm, TestUtils.EXPECTED_PROVIDER_NAME);
    }

    private Signature getSignature(String algorithm)
            throws NoSuchAlgorithmException, NoSuchProviderException {
        return Signature.getInstance(algorithm, TestUtils.EXPECTED_CRYPTO_OP_PROVIDER_NAME);
    }

    private KeyPair generateKeyPair(String algorithm) throws Exception {
        KeyGenParameterSpec spec =
                new KeyGenParameterSpec.Builder("test", KeyProperties.PURPOSE_SIGN).build();
        KeyPairGenerator keyGenerator = getKeyPairGenerator(algorithm);
        keyGenerator.initialize(spec);
        return keyGenerator.generateKeyPair();
    }

    private void checkGeneratedKeys(String algorithm, String expectedParameterSetName)
            throws Exception {
        KeyPair kp = generateKeyPair(algorithm);
        AndroidKeyStoreMlDsaPublicKey publicKey = (AndroidKeyStoreMlDsaPublicKey) kp.getPublic();
        assertThat(publicKey.getAlgorithm()).isEqualTo(KeyProperties.KEY_ALGORITHM_ML_DSA);
        assertThat(publicKey.getMlDsaAlgorithm()).isEqualTo(expectedParameterSetName);
        AndroidKeyStoreMlDsaPrivateKey privateKey =
                (AndroidKeyStoreMlDsaPrivateKey) kp.getPrivate();
        assertThat(privateKey.getAlgorithm()).isEqualTo(KeyProperties.KEY_ALGORITHM_ML_DSA);
        assertThat(privateKey.getMlDsaAlgorithm()).isEqualTo(expectedParameterSetName);
    }

    @Test
    public void testGeneratedKeys() throws Exception {
        checkGeneratedKeys(
                /* algorithm= */ KeyProperties.KEY_ALGORITHM_ML_DSA,
                /* expectedParameterSetName= */ KeyProperties.KEY_ALGORITHM_ML_DSA_65);
        checkGeneratedKeys(
                /* algorithm= */ KeyProperties.KEY_ALGORITHM_ML_DSA_65,
                /* expectedParameterSetName= */ KeyProperties.KEY_ALGORITHM_ML_DSA_65);
        checkGeneratedKeys(
                /* algorithm= */ KeyProperties.KEY_ALGORITHM_ML_DSA_87,
                /* expectedParameterSetName= */ KeyProperties.KEY_ALGORITHM_ML_DSA_87);
    }

    private void expectKeyFactorySupports(KeyFactory keyFactory, String algorithm)
            throws Exception {
        KeyPair keyPair = generateKeyPair(algorithm);

        // We expect these calls to succeed. The return values can be discarded.
        keyFactory.getKeySpec(keyPair.getPrivate(), KeyInfo.class);
        keyFactory.getKeySpec(keyPair.getPublic(), X509EncodedKeySpec.class);
        keyFactory.translateKey(keyPair.getPrivate());
        keyFactory.translateKey(keyPair.getPublic());
    }

    private void expectKeyFactoryDoesNotSupport(KeyFactory keyFactory, String algorithm)
            throws Exception {
        KeyPair keyPair = generateKeyPair(algorithm);
        assertThrows(
                InvalidKeySpecException.class,
                () -> {
                    keyFactory.getKeySpec(keyPair.getPrivate(), KeyInfo.class);
                });
        assertThrows(
                InvalidKeySpecException.class,
                () -> {
                    keyFactory.getKeySpec(keyPair.getPublic(), X509EncodedKeySpec.class);
                });
        assertThrows(
                InvalidKeyException.class,
                () -> {
                    keyFactory.translateKey(keyPair.getPrivate());
                });
        assertThrows(
                InvalidKeyException.class,
                () -> {
                    keyFactory.translateKey(keyPair.getPublic());
                });
    }

    @Test
    public void testMlDsaKeyFactory() throws Exception {
        KeyFactory keyFactory = getKeyFactory(KeyProperties.KEY_ALGORITHM_ML_DSA);
        expectKeyFactorySupports(keyFactory, KeyProperties.KEY_ALGORITHM_ML_DSA_65);
        expectKeyFactorySupports(keyFactory, KeyProperties.KEY_ALGORITHM_ML_DSA_87);
    }

    @Test
    public void testMlDsa65KeyFactory() throws Exception {
        KeyFactory keyFactory = getKeyFactory(KeyProperties.KEY_ALGORITHM_ML_DSA_65);
        expectKeyFactorySupports(keyFactory, KeyProperties.KEY_ALGORITHM_ML_DSA_65);
        expectKeyFactoryDoesNotSupport(keyFactory, KeyProperties.KEY_ALGORITHM_ML_DSA_87);
    }

    @Test
    public void testMlDsa87KeyFactory() throws Exception {
        KeyFactory keyFactory = getKeyFactory(KeyProperties.KEY_ALGORITHM_ML_DSA_87);
        expectKeyFactoryDoesNotSupport(keyFactory, KeyProperties.KEY_ALGORITHM_ML_DSA_65);
        expectKeyFactorySupports(keyFactory, KeyProperties.KEY_ALGORITHM_ML_DSA_87);
    }

    private void expectSignatureSupports(Signature signature, String algorithm) throws Exception {
        KeyPair keyPair = generateKeyPair(algorithm);

        // We expect these calls to succeed.
        signature.initSign(keyPair.getPrivate());
        signature.initVerify(keyPair.getPublic());
    }

    private void expectSignatureDoesNotSupport(Signature signature, String algorithm)
            throws Exception {
        KeyPair keyPair = generateKeyPair(algorithm);
        assertThrows(
                InvalidKeyException.class,
                () -> {
                    signature.initSign(keyPair.getPrivate());
                });
        assertThrows(
                InvalidKeyException.class,
                () -> {
                    signature.initVerify(keyPair.getPublic());
                });
    }

    @Test
    public void testMlDsaSignature() throws Exception {
        Signature signature = getSignature(KeyProperties.KEY_ALGORITHM_ML_DSA);
        expectSignatureSupports(signature, KeyProperties.KEY_ALGORITHM_ML_DSA_65);
        expectSignatureSupports(signature, KeyProperties.KEY_ALGORITHM_ML_DSA_87);
    }

    @Test
    public void testMlDsa65Signature() throws Exception {
        Signature signature = getSignature(KeyProperties.KEY_ALGORITHM_ML_DSA_65);
        expectSignatureSupports(signature, KeyProperties.KEY_ALGORITHM_ML_DSA_65);
        expectSignatureDoesNotSupport(signature, KeyProperties.KEY_ALGORITHM_ML_DSA_87);
    }

    @Test
    public void testMlDsa87Signature() throws Exception {
        Signature signature = getSignature(KeyProperties.KEY_ALGORITHM_ML_DSA_87);
        expectSignatureDoesNotSupport(signature, KeyProperties.KEY_ALGORITHM_ML_DSA_65);
        expectSignatureSupports(signature, KeyProperties.KEY_ALGORITHM_ML_DSA_87);
    }
}
