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

package android.keystore.cts;

import static android.keystore.cts.Attestation.KM_SECURITY_LEVEL_SOFTWARE;
import static android.keystore.cts.Attestation.KM_SECURITY_LEVEL_TRUSTED_ENVIRONMENT;
import static android.keystore.cts.AuthorizationList.KM_ALGORITHM_EC;
import static android.keystore.cts.AuthorizationList.KM_ALGORITHM_RSA;
import static android.keystore.cts.AuthorizationList.KM_DIGEST_NONE;
import static android.keystore.cts.AuthorizationList.KM_DIGEST_SHA_2_256;
import static android.keystore.cts.AuthorizationList.KM_DIGEST_SHA_2_512;
import static android.keystore.cts.AuthorizationList.KM_ORIGIN_GENERATED;
import static android.keystore.cts.AuthorizationList.KM_ORIGIN_UNKNOWN;
import static android.keystore.cts.AuthorizationList.KM_PURPOSE_DECRYPT;
import static android.keystore.cts.AuthorizationList.KM_PURPOSE_ENCRYPT;
import static android.keystore.cts.AuthorizationList.KM_PURPOSE_SIGN;
import static android.keystore.cts.AuthorizationList.KM_PURPOSE_VERIFY;
import static android.keystore.cts.RootOfTrust.KM_VERIFIED_BOOT_VERIFIED;
import static android.security.keystore.KeyProperties.DIGEST_NONE;
import static android.security.keystore.KeyProperties.DIGEST_SHA256;
import static android.security.keystore.KeyProperties.DIGEST_SHA512;
import static android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE;
import static android.security.keystore.KeyProperties.ENCRYPTION_PADDING_RSA_OAEP;
import static android.security.keystore.KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1;
import static android.security.keystore.KeyProperties.KEY_ALGORITHM_EC;
import static android.security.keystore.KeyProperties.KEY_ALGORITHM_RSA;
import static android.security.keystore.KeyProperties.PURPOSE_DECRYPT;
import static android.security.keystore.KeyProperties.PURPOSE_ENCRYPT;
import static android.security.keystore.KeyProperties.PURPOSE_SIGN;
import static android.security.keystore.KeyProperties.PURPOSE_VERIFY;
import static android.security.keystore.KeyProperties.SIGNATURE_PADDING_RSA_PKCS1;
import static android.security.keystore.KeyProperties.SIGNATURE_PADDING_RSA_PSS;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.matchers.JUnitMatchers.either;
import static org.junit.matchers.JUnitMatchers.hasItems;

import com.google.common.collect.ImmutableSet;

import android.os.Build;
import android.os.SystemProperties;
import android.security.KeyStoreException;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.test.AndroidTestCase;
import android.util.ArraySet;

import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.ProviderException;
import java.security.SignatureException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.util.Arrays;
import java.util.Date;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.KeyGenerator;

/**
 * Tests for Android KeysStore attestation.
 */
public class KeyAttestationTest extends AndroidTestCase {

    private static final int ORIGINATION_TIME_OFFSET = 1000000;
    private static final int CONSUMPTION_TIME_OFFSET = 2000000;

    private static final int KEY_USAGE_BITSTRING_LENGTH = 9;
    private static final int KEY_USAGE_DIGITAL_SIGNATURE_BIT_OFFSET = 0;
    private static final int KEY_USAGE_KEY_ENCIPHERMENT_BIT_OFFSET = 2;
    private static final int KEY_USAGE_DATA_ENCIPHERMENT_BIT_OFFSET = 3;

    private static final int OS_MAJOR_VERSION_MATCH_GROUP_NAME = 1;
    private static final int OS_MINOR_VERSION_MATCH_GROUP_NAME = 2;
    private static final int OS_SUBMINOR_VERSION_MATCH_GROUP_NAME = 3;
    private static final Pattern OS_VERSION_STRING_PATTERN = Pattern
            .compile("([0-9]{1,2})(\\.([0-9]{1,2}))?(\\.([0-9]{1,2}))?.*");

    private static final int OS_PATCH_LEVEL_YEAR_GROUP_NAME = 1;
    private static final int OS_PATCH_LEVEL_MONTH_GROUP_NAME = 2;
    private static final Pattern OS_PATCH_LEVEL_STRING_PATTERN = Pattern
            .compile("([0-9]{4})-([0-9]{2})-[0-9]{2}");

    private static final int KM_ERROR_INVALID_INPUT_LENGTH = -21;

    public void testEcAttestation() throws Exception {
        // Note: Curve and key sizes arrays must correspond.
        String[] curves = {
                "secp224r1", "secp256r1", "secp384r1", "secp521r1"
        };
        int[] keySizes = {
                224, 256, 384, 521
        };
        byte[][] challenges = {
                new byte[0], // empty challenge
                "challenge".getBytes(), // short challenge
                new byte[128], // long challenge
        };
        int[] purposes = {
                KM_PURPOSE_SIGN, KM_PURPOSE_VERIFY, KM_PURPOSE_SIGN | KM_PURPOSE_VERIFY
        };

        for (int curveIndex = 0; curveIndex < curves.length; ++curveIndex) {
            for (int challengeIndex = 0; challengeIndex < challenges.length; ++challengeIndex) {
                for (int purposeIndex = 0; purposeIndex < purposes.length; ++purposeIndex) {
                    try {
                        testEcAttestation(challenges[challengeIndex],
                                true /* includeValidityDates */,
                                curves[curveIndex], keySizes[curveIndex], purposes[purposeIndex]);
                        testEcAttestation(challenges[challengeIndex],
                                false /* includeValidityDates */,
                                curves[curveIndex], keySizes[curveIndex], purposes[purposeIndex]);
                    } catch (Throwable e) {
                        throw new Exception(
                                "Failed on curve " + curveIndex + " and challege " + challengeIndex,
                                e);
                    }
                }
            }
        }
    }

    public void testEcAttestation_TooLargeChallenge() throws Exception {
        try {
            testEcAttestation(new byte[129], true /* includeValidityDates */, "secp256r1", 256,
                    KM_PURPOSE_SIGN);
            fail("Attestation challenges larger than 128 bytes should be rejected");
        } catch (ProviderException e) {
            KeyStoreException cause = (KeyStoreException) e.getCause();
            assertEquals(KM_ERROR_INVALID_INPUT_LENGTH, cause.getErrorCode());
        }
    }

    public void testEcAttestation_NoChallenge() throws Exception {
        String keystoreAlias = "test_key";
        Date now = new Date();
        Date originationEnd = new Date(now.getTime() + ORIGINATION_TIME_OFFSET);
        Date consumptionEnd = new Date(now.getTime() + CONSUMPTION_TIME_OFFSET);
        KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(keystoreAlias, PURPOSE_SIGN)
                .setAlgorithmParameterSpec(new ECGenParameterSpec("secp256r1"))
                .setDigests(DIGEST_NONE, DIGEST_SHA256, DIGEST_SHA512)
                .setAttestationChallenge(null)
                .setKeyValidityStart(now)
                .setKeyValidityForOriginationEnd(originationEnd)
                .setKeyValidityForConsumptionEnd(consumptionEnd)
                .build();

        generateKeyPair(KEY_ALGORITHM_EC, spec);

        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);

        try {
            Certificate certificates[] = keyStore.getCertificateChain(keystoreAlias);
            assertEquals(1, certificates.length);

            X509Certificate attestationCert = (X509Certificate) certificates[0];
            assertNull(attestationCert.getExtensionValue(Attestation.KEY_DESCRIPTION_OID));
        } finally {
            keyStore.deleteEntry(keystoreAlias);
        }
    }

    public void testRsaAttestation() throws Exception {
        int[] keySizes = { // Smallish sizes to keep test runtimes down.
                512, 768, 1024
        };
        byte[][] challenges = {
                new byte[0], // empty challenge
                "challenge".getBytes(), // short challenge
                new byte[128] // long challenge
        };
        int[] purposes = {
                PURPOSE_SIGN | PURPOSE_VERIFY,
                PURPOSE_ENCRYPT | PURPOSE_DECRYPT,
        };
        String[][] encryptionPaddingModes = {
                {
                        ENCRYPTION_PADDING_NONE
                },
                {
                        ENCRYPTION_PADDING_RSA_OAEP,
                },
                {
                        ENCRYPTION_PADDING_RSA_PKCS1,
                },
                {
                        ENCRYPTION_PADDING_RSA_OAEP,
                        ENCRYPTION_PADDING_RSA_PKCS1,
                },
        };
        String[][] signaturePaddingModes = {
                {
                        SIGNATURE_PADDING_RSA_PKCS1,
                },
                {
                        SIGNATURE_PADDING_RSA_PSS,
                },
                {
                        SIGNATURE_PADDING_RSA_PKCS1,
                        SIGNATURE_PADDING_RSA_PSS,
                },
        };

        for (int keySize : keySizes) {
            for (byte[] challenge : challenges) {
                for (int purpose : purposes) {
                    if (isEncryptionPurpose(purpose)) {
                        testRsaAttestations(keySize, challenge, purpose, encryptionPaddingModes);
                    } else {
                        testRsaAttestations(keySize, challenge, purpose, signaturePaddingModes);
                    }
                }
            }
        }
    }

    public void testRsaAttestation_TooLargeChallenge() throws Exception {
        try {
            testRsaAttestation(new byte[129], true /* includeValidityDates */, 512, PURPOSE_SIGN,
                    null /* paddingModes; may be empty because we'll never test them */);
            fail("Attestation challenges larger than 128 bytes should be rejected");
        } catch (ProviderException e) {
            KeyStoreException cause = (KeyStoreException) e.getCause();
            assertEquals(KM_ERROR_INVALID_INPUT_LENGTH, cause.getErrorCode());
        }
    }

    public void testRsaAttestation_NoChallenge() throws Exception {
        String keystoreAlias = "test_key";
        Date now = new Date();
        Date originationEnd = new Date(now.getTime() + ORIGINATION_TIME_OFFSET);
        Date consumptionEnd = new Date(now.getTime() + CONSUMPTION_TIME_OFFSET);
        KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(keystoreAlias, PURPOSE_SIGN)
                .setDigests(DIGEST_NONE, DIGEST_SHA256, DIGEST_SHA512)
                .setAttestationChallenge(null)
                .setKeyValidityStart(now)
                .setKeyValidityForOriginationEnd(originationEnd)
                .setKeyValidityForConsumptionEnd(consumptionEnd)
                .build();

        generateKeyPair(KEY_ALGORITHM_RSA, spec);

        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);

        try {
            Certificate certificates[] = keyStore.getCertificateChain(keystoreAlias);
            assertEquals(1, certificates.length);

            X509Certificate attestationCert = (X509Certificate) certificates[0];
            assertNull(attestationCert.getExtensionValue(Attestation.KEY_DESCRIPTION_OID));
        } finally {
            keyStore.deleteEntry(keystoreAlias);
        }
    }

    public void testAesAttestation() throws Exception {
        String keystoreAlias = "test_key";
        KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(keystoreAlias, PURPOSE_ENCRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setAttestationChallenge(new byte[0])
                .build();
        generateKey(spec, KeyProperties.KEY_ALGORITHM_AES);

        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        try {
            assertNull(keyStore.getCertificateChain(keystoreAlias));
        } finally {
            keyStore.deleteEntry(keystoreAlias);
        }
    }

    public void testHmacAttestation() throws Exception {
        String keystoreAlias = "test_key";
        KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(keystoreAlias, PURPOSE_SIGN)
                .build();

        generateKey(spec, KeyProperties.KEY_ALGORITHM_HMAC_SHA256);

        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        try {
            assertNull(keyStore.getCertificateChain(keystoreAlias));
        } finally {
            keyStore.deleteEntry(keystoreAlias);
        }
    }

    private void testRsaAttestations(int keySize, byte[] challenge, int purpose,
            String[][] paddingModes) throws Exception {
        for (String[] paddings : paddingModes) {
            try {
                testRsaAttestation(challenge, true /* includeValidityDates */, keySize, purpose,
                        paddings);
                testRsaAttestation(challenge, false /* includeValidityDates */, keySize, purpose,
                        paddings);
            } catch (Throwable e) {
                throw new Exception("Failed on key size " + keySize + " challenge [" +
                        new String(challenge) + "], purposes " +
                        buildPurposeSet(purpose) + " and paddings " +
                        ImmutableSet.copyOf(paddings),
                        e);
            }
        }
    }

    @SuppressWarnings("deprecation")
    private void testRsaAttestation(byte[] challenge, boolean includeValidityDates, int keySize,
            int purposes, String[] paddingModes) throws Exception {
        String keystoreAlias = "test_key";

        Date startTime = new Date();
        Date originationEnd = new Date(startTime.getTime() + ORIGINATION_TIME_OFFSET);
        Date consumptionEnd = new Date(startTime.getTime() + CONSUMPTION_TIME_OFFSET);
        KeyGenParameterSpec.Builder builder =
            new KeyGenParameterSpec.Builder(keystoreAlias, purposes)
                        .setKeySize(keySize)
                        .setDigests(DIGEST_NONE, DIGEST_SHA256, DIGEST_SHA512)
                        .setAttestationChallenge(challenge);

        if (includeValidityDates) {
            builder.setKeyValidityStart(startTime)
                    .setKeyValidityForOriginationEnd(originationEnd)
                    .setKeyValidityForConsumptionEnd(consumptionEnd);
        }
        if (isEncryptionPurpose(purposes)) {
            builder.setEncryptionPaddings(paddingModes);
            // Because we sometimes set "no padding", allow non-randomized encryption.
            builder.setRandomizedEncryptionRequired(false);
        }
        if (isSignaturePurpose(purposes)) {
            builder.setSignaturePaddings(paddingModes);
        }

        generateKeyPair(KEY_ALGORITHM_RSA, builder.build());

        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);

        try {
            Certificate certificates[] = keyStore.getCertificateChain(keystoreAlias);
            verifyCertificateSignatures(certificates);

            X509Certificate attestationCert = (X509Certificate) certificates[0];
            Attestation attestation = new Attestation(attestationCert);

            checkRsaKeyDetails(attestation, keySize, purposes, ImmutableSet.copyOf(paddingModes));
            checkKeyUsage(attestationCert, purposes);
            checkKeyIndependentAttestationInfo(challenge, purposes, startTime, includeValidityDates,
                    attestation);
        } finally {
            keyStore.deleteEntry(keystoreAlias);
        }
    }

    private void checkKeyUsage(X509Certificate attestationCert, int purposes) {

        boolean[] expectedKeyUsage = new boolean[KEY_USAGE_BITSTRING_LENGTH];
        if (isSignaturePurpose(purposes)) {
            expectedKeyUsage[KEY_USAGE_DIGITAL_SIGNATURE_BIT_OFFSET] = true;
        }
        if (isEncryptionPurpose(purposes)) {
            expectedKeyUsage[KEY_USAGE_KEY_ENCIPHERMENT_BIT_OFFSET] = true;
            expectedKeyUsage[KEY_USAGE_DATA_ENCIPHERMENT_BIT_OFFSET] = true;
        }
        assertThat(attestationCert.getKeyUsage(), is(expectedKeyUsage));
    }

    @SuppressWarnings("deprecation")
    private void testEcAttestation(byte[] challenge, boolean includeValidityDates, String ecCurve,
            int keySize, int purposes) throws Exception {
        String keystoreAlias = "test_key";

        Date startTime = new Date();
        Date originationEnd = new Date(startTime.getTime() + ORIGINATION_TIME_OFFSET);
        Date consumptionEnd = new Date(startTime.getTime() + CONSUMPTION_TIME_OFFSET);
        KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec.Builder(keystoreAlias,
                purposes)
                        .setAlgorithmParameterSpec(new ECGenParameterSpec(ecCurve))
                        .setDigests(DIGEST_NONE, DIGEST_SHA256, DIGEST_SHA512)
                        .setAttestationChallenge(challenge);

        if (includeValidityDates) {
            builder.setKeyValidityStart(startTime)
                    .setKeyValidityForOriginationEnd(originationEnd)
                    .setKeyValidityForConsumptionEnd(consumptionEnd);
        }

        generateKeyPair(KEY_ALGORITHM_EC, builder.build());

        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);

        try {
            Certificate certificates[] = keyStore.getCertificateChain(keystoreAlias);
            verifyCertificateSignatures(certificates);

            X509Certificate attestationCert = (X509Certificate) certificates[0];
            Attestation attestation = new Attestation(attestationCert);

            checkEcKeyDetails(attestation, ecCurve, keySize);
            checkKeyUsage(attestationCert, purposes);
            checkKeyIndependentAttestationInfo(challenge, purposes, startTime, includeValidityDates,
                    attestation);
        } finally {
            keyStore.deleteEntry(keystoreAlias);
        }
    }

    private void checkKeyIndependentAttestationInfo(byte[] challenge, int purposes, Date startTime,
            boolean includesValidityDates, Attestation attestation) {
        checkAttestationSecurityLevelDependentParams(attestation);
        assertNotNull(attestation.getAttestationChallenge());
        assertTrue(Arrays.equals(challenge, attestation.getAttestationChallenge()));
        assertNotNull(attestation.getUniqueId());
        assertEquals(0, attestation.getUniqueId().length);
        checkPurposes(attestation, purposes);
        checkDigests(attestation,
                ImmutableSet.of(KM_DIGEST_NONE, KM_DIGEST_SHA_2_256, KM_DIGEST_SHA_2_512));
        checkValidityPeriod(attestation, startTime, includesValidityDates);
        checkFlags(attestation);
        checkOrigin(attestation);
    }

    private int getSystemPatchLevel() {
        Matcher matcher = OS_PATCH_LEVEL_STRING_PATTERN.matcher(Build.VERSION.SECURITY_PATCH);
        assertTrue(matcher.matches());
        String year_string = matcher.group(OS_PATCH_LEVEL_YEAR_GROUP_NAME);
        String month_string = matcher.group(OS_PATCH_LEVEL_MONTH_GROUP_NAME);
        int patch_level = Integer.parseInt(year_string) * 100 + Integer.parseInt(month_string);
        return patch_level;
    }

    private int getSystemOsVersion() {
        Matcher matcher = OS_VERSION_STRING_PATTERN.matcher(Build.VERSION.RELEASE);
        if (!matcher.matches()) {
            return 0;
        }

        int version = 0;
        String major_string = matcher.group(OS_MAJOR_VERSION_MATCH_GROUP_NAME);
        String minor_string = matcher.group(OS_MINOR_VERSION_MATCH_GROUP_NAME);
        String subminor_string = matcher.group(OS_SUBMINOR_VERSION_MATCH_GROUP_NAME);
        if (major_string != null) {
            version += Integer.parseInt(major_string) * 10000;
        }
        if (minor_string != null) {
            version += Integer.parseInt(minor_string) * 100;
        }
        if (subminor_string != null) {
            version += Integer.parseInt(subminor_string);
        }
        return version;
    }

    private void checkOrigin(Attestation attestation) {
        assertTrue("Origin must be defined",
                attestation.getSoftwareEnforced().getOrigin() != null ||
                        attestation.getTeeEnforced().getOrigin() != null);
        if (attestation.getKeymasterVersion() != 0) {
            assertTrue("Origin may not be defined in both SW and TEE, except on keymaster0",
                    attestation.getSoftwareEnforced().getOrigin() == null ||
                            attestation.getTeeEnforced().getOrigin() == null);
        }

        if (attestation.getKeymasterSecurityLevel() == KM_SECURITY_LEVEL_SOFTWARE) {
            assertThat(attestation.getSoftwareEnforced().getOrigin(), is(KM_ORIGIN_GENERATED));
        } else if (attestation.getKeymasterVersion() == 0) {
            assertThat(attestation.getTeeEnforced().getOrigin(), is(KM_ORIGIN_UNKNOWN));
        } else {
            assertThat(attestation.getTeeEnforced().getOrigin(), is(KM_ORIGIN_GENERATED));
        }
    }

    private void checkFlags(Attestation attestation) {
        assertFalse("All applications was not requested",
                attestation.getSoftwareEnforced().isAllApplications());
        assertFalse("All applications was not requested",
                attestation.getTeeEnforced().isAllApplications());
        assertFalse("Allow while on body was not requested",
                attestation.getSoftwareEnforced().isAllowWhileOnBody());
        assertFalse("Allow while on body was not requested",
                attestation.getTeeEnforced().isAllowWhileOnBody());
        assertNull("Auth binding was not requiested",
                attestation.getSoftwareEnforced().getUserAuthType());
        assertNull("Auth binding was not requiested",
                attestation.getTeeEnforced().getUserAuthType());
        assertTrue("noAuthRequired must be true",
                attestation.getSoftwareEnforced().isNoAuthRequired()
                        || attestation.getTeeEnforced().isNoAuthRequired());
        assertFalse("auth is either software or TEE",
                attestation.getSoftwareEnforced().isNoAuthRequired()
                        && attestation.getTeeEnforced().isNoAuthRequired());
        assertFalse("Software cannot implement rollback resistance",
                attestation.getSoftwareEnforced().isRollbackResistant());
    }

    private void checkValidityPeriod(Attestation attestation, Date startTime,
            boolean includesValidityDates) {
        AuthorizationList validityPeriodList;
        AuthorizationList nonValidityPeriodList;
        if (attestation.getTeeEnforced().getCreationDateTime() != null) {
            validityPeriodList = attestation.getTeeEnforced();
            nonValidityPeriodList = attestation.getSoftwareEnforced();
        } else {
            validityPeriodList = attestation.getSoftwareEnforced();
            nonValidityPeriodList = attestation.getTeeEnforced();
        }

        if (attestation.getKeymasterVersion() == 2) {
            Date creationDateTime = validityPeriodList.getCreationDateTime();

            assertNotNull(creationDateTime);
            assertNull(nonValidityPeriodList.getCreationDateTime());

            // We allow a little slop on creation times because the TEE/HAL may not be quite synced
            // up with the system.
            assertTrue("Test start time (" + startTime.getTime() + ") and key creation time (" +
                    creationDateTime.getTime() + ") should be close",
                    Math.abs(creationDateTime.getTime() - startTime.getTime()) <= 2000);
        }

        if (includesValidityDates) {
            Date activeDateTime = validityPeriodList.getActiveDateTime();
            Date originationExpirationDateTime = validityPeriodList.getOriginationExpireDateTime();
            Date usageExpirationDateTime = validityPeriodList.getUsageExpireDateTime();

            assertNotNull(activeDateTime);
            assertNotNull(originationExpirationDateTime);
            assertNotNull(usageExpirationDateTime);

            assertNull(nonValidityPeriodList.getActiveDateTime());
            assertNull(nonValidityPeriodList.getOriginationExpireDateTime());
            assertNull(nonValidityPeriodList.getUsageExpireDateTime());

            assertThat(originationExpirationDateTime.getTime(),
                    is(startTime.getTime() + ORIGINATION_TIME_OFFSET));
            assertThat(usageExpirationDateTime.getTime(),
                    is(startTime.getTime() + CONSUMPTION_TIME_OFFSET));
        }
    }

    private void checkDigests(Attestation attestation, Set<Integer> expectedDigests) {
        Set<Integer> softwareEnforcedDigests = attestation.getSoftwareEnforced().getDigests();
        Set<Integer> teeEnforcedDigests = attestation.getTeeEnforced().getDigests();

        if (softwareEnforcedDigests == null) {
            softwareEnforcedDigests = ImmutableSet.of();
        }
        if (teeEnforcedDigests == null) {
            teeEnforcedDigests = ImmutableSet.of();
        }

        Set<Integer> allDigests = ImmutableSet.<Integer> builder()
                .addAll(softwareEnforcedDigests)
                .addAll(teeEnforcedDigests)
                .build();
        Set<Integer> intersection = new ArraySet<>();
        intersection.addAll(softwareEnforcedDigests);
        intersection.retainAll(teeEnforcedDigests);

        assertThat(allDigests, is(expectedDigests));
        assertTrue("Digest sets must be disjoint", intersection.isEmpty());

        if (attestation.getKeymasterSecurityLevel() == KM_SECURITY_LEVEL_SOFTWARE
                || attestation.getKeymasterVersion() == 0) {
            assertThat("Digests in software-enforced",
                    softwareEnforcedDigests, is(expectedDigests));
        } else {
            switch (attestation.getKeymasterVersion()) {
                case 1:
                    // KM1 implementations may not support SHA512 in the TEE
                    assertTrue(softwareEnforcedDigests.contains(KM_DIGEST_SHA_2_512)
                            || teeEnforcedDigests.contains(KM_DIGEST_SHA_2_512));

                    assertThat(teeEnforcedDigests, hasItems(KM_DIGEST_NONE, KM_DIGEST_SHA_2_256));
                    break;

                case 2:
                    assertThat(teeEnforcedDigests, is(expectedDigests));
                    break;

                default:
                    fail("Broken CTS test. Should be impossible to get here.");
            }
        }
    }

    private Set<Integer> checkPurposes(Attestation attestation, int purposes) {
        Set<Integer> expectedPurposes = buildPurposeSet(purposes);
        if (attestation.getKeymasterSecurityLevel() == KM_SECURITY_LEVEL_SOFTWARE
                || attestation.getKeymasterVersion() == 0) {
            assertThat("Purposes in software-enforced should match expected set",
                    attestation.getSoftwareEnforced().getPurposes(), is(expectedPurposes));
            assertNull("Should be no purposes in TEE-enforced",
                    attestation.getTeeEnforced().getPurposes());
        } else {
            assertThat("Purposes in TEE-enforced should match expected set",
                    attestation.getTeeEnforced().getPurposes(), is(expectedPurposes));
            assertNull("No purposes in software-enforced",
                    attestation.getSoftwareEnforced().getPurposes());
        }
        return expectedPurposes;
    }

    @SuppressWarnings("unchecked")
    private void checkAttestationSecurityLevelDependentParams(Attestation attestation) {
        assertEquals("Attestation version must be 1", 1, attestation.getAttestationVersion());

        AuthorizationList teeEnforced = attestation.getTeeEnforced();
        AuthorizationList softwareEnforced = attestation.getSoftwareEnforced();

        int systemOsVersion = getSystemOsVersion();
        int systemPatchLevel = getSystemPatchLevel();

        switch (attestation.getAttestationSecurityLevel()) {
            case KM_SECURITY_LEVEL_TRUSTED_ENVIRONMENT:
                assertThat("TEE attestation can only come from TEE keymaster",
                        attestation.getKeymasterSecurityLevel(),
                        is(KM_SECURITY_LEVEL_TRUSTED_ENVIRONMENT));
                assertThat(attestation.getKeymasterVersion(), is(2));

                checkRootOfTrust(attestation);
                assertThat(teeEnforced.getOsVersion(), is(systemOsVersion));
                assertThat(teeEnforced.getOsPatchLevel(), is(systemPatchLevel));
                break;

            case KM_SECURITY_LEVEL_SOFTWARE:
                if (attestation
                        .getKeymasterSecurityLevel() == KM_SECURITY_LEVEL_TRUSTED_ENVIRONMENT) {
                    assertThat("TEE KM version must be 0 or 1 with software attestation",
                            attestation.getKeymasterVersion(), either(is(0)).or(is(1)));
                } else {
                    assertThat("Software KM is version 2", attestation.getKeymasterVersion(),
                            is(2));
                    assertThat(softwareEnforced.getOsVersion(), is(systemOsVersion));
                    assertThat(softwareEnforced.getOsPatchLevel(), is(systemPatchLevel));
                }

                assertNull("Software attestation cannot provide root of trust",
                        teeEnforced.getRootOfTrust());

                break;

            default:
                fail("Invalid attestation security level: "
                        + attestation.getAttestationSecurityLevel());
                break;
        }

        assertNull("Software-enforced list must not contain root of trust",
                softwareEnforced.getRootOfTrust());
    }

    private void checkRootOfTrust(Attestation attestation) {
        RootOfTrust rootOfTrust = attestation.getTeeEnforced().getRootOfTrust();
        assertNotNull(rootOfTrust);
        assertNotNull(rootOfTrust.getVerifiedBootKey());
        assertTrue(rootOfTrust.getVerifiedBootKey().length >= 32);
        assertTrue(rootOfTrust.isDeviceLocked());
        assertEquals(KM_VERIFIED_BOOT_VERIFIED, rootOfTrust.getVerifiedBootState());
    }

    private void checkRsaKeyDetails(Attestation attestation, int keySize, int purposes,
            Set<String> expectedPaddingModes) throws CertificateParsingException {
        AuthorizationList keyDetailsList;
        AuthorizationList nonKeyDetailsList;
        if (attestation.getKeymasterSecurityLevel() == KM_SECURITY_LEVEL_TRUSTED_ENVIRONMENT) {
            keyDetailsList = attestation.getTeeEnforced();
            nonKeyDetailsList = attestation.getSoftwareEnforced();
        } else {
            keyDetailsList = attestation.getSoftwareEnforced();
            nonKeyDetailsList = attestation.getTeeEnforced();
        }
        assertEquals(keySize, keyDetailsList.getKeySize().intValue());
        assertNull(nonKeyDetailsList.getKeySize());

        assertEquals(KM_ALGORITHM_RSA, keyDetailsList.getAlgorithm().intValue());
        assertNull(nonKeyDetailsList.getAlgorithm());

        assertNull(keyDetailsList.getEcCurve());
        assertNull(nonKeyDetailsList.getEcCurve());

        assertEquals(65537, keyDetailsList.getRsaPublicExponent().longValue());
        assertNull(nonKeyDetailsList.getRsaPublicExponent());

        Set<String> paddingModes;
        if (attestation.getKeymasterVersion() == 0) {
            // KM0 implementations don't support padding info, so it's always in the
            // software-enforced list.
            paddingModes = attestation.getSoftwareEnforced().getPaddingModesAsStrings();
            assertNull(attestation.getTeeEnforced().getPaddingModes());
        } else {
            paddingModes = keyDetailsList.getPaddingModesAsStrings();
            assertNull(nonKeyDetailsList.getPaddingModes());
        }

        // KM1 implementations may add ENCRYPTION_PADDING_NONE to the list of paddings.
        Set<String> km1PossiblePaddingModes = expectedPaddingModes;
        if (attestation.getKeymasterVersion() == 1 &&
                attestation.getKeymasterSecurityLevel() == KM_SECURITY_LEVEL_TRUSTED_ENVIRONMENT) {
            ImmutableSet.Builder<String> builder = ImmutableSet.builder();
            builder.addAll(expectedPaddingModes);
            builder.add(ENCRYPTION_PADDING_NONE);
            km1PossiblePaddingModes = builder.build();
        }

        assertThat(paddingModes, either(is(expectedPaddingModes)).or(is(km1PossiblePaddingModes)));
    }

    private void checkEcKeyDetails(Attestation attestation, String ecCurve, int keySize) {
        AuthorizationList keyDetailsList;
        AuthorizationList nonKeyDetailsList;
        if (attestation.getKeymasterSecurityLevel() == KM_SECURITY_LEVEL_TRUSTED_ENVIRONMENT) {
            keyDetailsList = attestation.getTeeEnforced();
            nonKeyDetailsList = attestation.getSoftwareEnforced();
        } else {
            keyDetailsList = attestation.getSoftwareEnforced();
            nonKeyDetailsList = attestation.getTeeEnforced();
        }
        assertEquals(keySize, keyDetailsList.getKeySize().intValue());
        assertNull(nonKeyDetailsList.getKeySize());
        assertEquals(KM_ALGORITHM_EC, keyDetailsList.getAlgorithm().intValue());
        assertNull(nonKeyDetailsList.getAlgorithm());
        assertEquals(ecCurve, keyDetailsList.ecCurveAsString());
        assertNull(nonKeyDetailsList.getEcCurve());
        assertNull(keyDetailsList.getRsaPublicExponent());
        assertNull(nonKeyDetailsList.getRsaPublicExponent());
        assertNull(keyDetailsList.getPaddingModes());
        assertNull(nonKeyDetailsList.getPaddingModes());
    }

    private boolean isEncryptionPurpose(int purposes) {
        return (purposes & PURPOSE_DECRYPT) != 0 || (purposes & PURPOSE_ENCRYPT) != 0;
    }

    private boolean isSignaturePurpose(int purposes) {
        return (purposes & PURPOSE_SIGN) != 0 || (purposes & PURPOSE_VERIFY) != 0;
    }

    private ImmutableSet<Integer> buildPurposeSet(int purposes) {
        ImmutableSet.Builder<Integer> builder = ImmutableSet.builder();
        if ((purposes & PURPOSE_SIGN) != 0)
            builder.add(KM_PURPOSE_SIGN);
        if ((purposes & PURPOSE_VERIFY) != 0)
            builder.add(KM_PURPOSE_VERIFY);
        if ((purposes & PURPOSE_ENCRYPT) != 0)
            builder.add(KM_PURPOSE_ENCRYPT);
        if ((purposes & PURPOSE_DECRYPT) != 0)
            builder.add(KM_PURPOSE_DECRYPT);
        return builder.build();
    }

    private void generateKey(KeyGenParameterSpec spec, String algorithm)
            throws NoSuchAlgorithmException, NoSuchProviderException,
            InvalidAlgorithmParameterException {
        KeyGenerator keyGenerator = KeyGenerator.getInstance(algorithm, "AndroidKeyStore");
        keyGenerator.init(spec);
        keyGenerator.generateKey();
    }

    private void generateKeyPair(String algorithm, KeyGenParameterSpec spec)
            throws NoSuchAlgorithmException, NoSuchProviderException,
            InvalidAlgorithmParameterException {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(algorithm,
                "AndroidKeyStore");
        keyPairGenerator.initialize(spec);
        keyPairGenerator.generateKeyPair();
    }

    private void verifyCertificateSignatures(Certificate[] certChain)
            throws GeneralSecurityException {
        assertNotNull(certChain);
        for (int i = 1; i < certChain.length; ++i) {
            try {
                certChain[i - 1].verify(certChain[i].getPublicKey());
            } catch (InvalidKeyException | CertificateException | NoSuchAlgorithmException
                    | NoSuchProviderException | SignatureException e) {
                throw new GeneralSecurityException("Failed to verify certificate "
                        + certChain[i - 1] + " with public key " + certChain[i].getPublicKey(), e);
            }
        }
    }
}
