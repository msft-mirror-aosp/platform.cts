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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;

import android.content.Context;
import android.security.KeyPairGeneratorSpec;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import java.math.BigInteger;
import java.util.Date;

import javax.security.auth.x500.X500Principal;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class KeyPairGeneratorSpecTest {
    private static final String TEST_ALIAS_1 = "test1";

    private static final X500Principal TEST_DN_1 = new X500Principal("CN=test1");

    private static final long NOW_MILLIS = System.currentTimeMillis();

    private static final BigInteger SERIAL_1 = BigInteger.ONE;

    /* We have to round this off because X509v3 doesn't store milliseconds. */
    private static final Date NOW = new Date(NOW_MILLIS - (NOW_MILLIS % 1000L));

    @SuppressWarnings("deprecation")
    private static final Date NOW_PLUS_10_YEARS = new Date(NOW.getYear() + 10, 0, 1);

    private Context getContext() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    @Test
    public void testBuilder_Unencrypted_Success() throws Exception {
        KeyPairGeneratorSpec spec = new KeyPairGeneratorSpec.Builder(getContext())
                .setAlias(TEST_ALIAS_1)
                .setSubject(TEST_DN_1)
                .setSerialNumber(SERIAL_1)
                .setStartDate(NOW)
                .setEndDate(NOW_PLUS_10_YEARS)
                .build();

        assertEquals("Context should be the one specified", getContext(), spec.getContext());

        assertEquals("Alias should be the one specified", TEST_ALIAS_1, spec.getKeystoreAlias());

        assertEquals("subjectDN should be the one specified", TEST_DN_1, spec.getSubjectDN());

        assertEquals("startDate should be the one specified", NOW, spec.getStartDate());

        assertEquals("endDate should be the one specified", NOW_PLUS_10_YEARS, spec.getEndDate());

        assertFalse("encryption flag should not be on", spec.isEncryptionRequired());
    }

    @Test
    public void testBuilder_NullContext_Failure() throws Exception {
        assertThrows(
                "Should throw NullPointerException when context is null",
                NullPointerException.class,
                () -> new KeyPairGeneratorSpec.Builder(null));
    }

    @Test
    public void testBuilder_MissingAlias_Failure() throws Exception {
        assertThrows(
                "Should throw IllegalArgumentException when alias is missing",
                IllegalArgumentException.class,
                () -> new KeyPairGeneratorSpec.Builder(getContext())
                    .setSubject(TEST_DN_1)
                    .setSerialNumber(SERIAL_1)
                    .setStartDate(NOW)
                    .setEndDate(NOW_PLUS_10_YEARS)
                    .build());
    }

    @Test
    public void testBuilder_MissingSubjectDN_Failure() throws Exception {
        assertThrows(
                "Should throw IllegalArgumentException when subject is missing",
                IllegalArgumentException.class,
                () -> new KeyPairGeneratorSpec.Builder(getContext())
                    .setAlias(TEST_ALIAS_1)
                    .setSerialNumber(SERIAL_1)
                    .setStartDate(NOW)
                    .setEndDate(NOW_PLUS_10_YEARS)
                    .build());
    }

    @Test
    public void testBuilder_MissingSerialNumber_Failure() throws Exception {
        assertThrows(
                "Should throw IllegalArgumentException when serialNumber is missing",
                IllegalArgumentException.class,
                () -> new KeyPairGeneratorSpec.Builder(getContext())
                    .setAlias(TEST_ALIAS_1)
                    .setSubject(TEST_DN_1)
                    .setStartDate(NOW)
                    .setEndDate(NOW_PLUS_10_YEARS)
                    .build());
    }

    @Test
    public void testBuilder_MissingStartDate_Failure() throws Exception {
        assertThrows(
                "Should throw IllegalArgumentException when startDate is missing",
                IllegalArgumentException.class,
                () -> new KeyPairGeneratorSpec.Builder(getContext())
                    .setAlias(TEST_ALIAS_1)
                    .setSubject(TEST_DN_1)
                    .setSerialNumber(SERIAL_1)
                    .setEndDate(NOW_PLUS_10_YEARS)
                    .build());
    }

    @Test
    public void testBuilder_MissingEndDate_Failure() throws Exception {
        assertThrows(
                "Should throw IllegalArgumentException when endDate is missing",
                IllegalArgumentException.class,
                () -> new KeyPairGeneratorSpec.Builder(getContext())
                    .setAlias(TEST_ALIAS_1)
                    .setSubject(TEST_DN_1)
                    .setSerialNumber(SERIAL_1)
                    .setStartDate(NOW)
                    .build());
    }

    @Test
    public void testBuilder_EndBeforeStart_Failure() throws Exception {
        assertThrows(
                "Should throw IllegalArgumentException when end is before start",
                IllegalArgumentException.class,
                () -> new KeyPairGeneratorSpec.Builder(getContext())
                    .setAlias(TEST_ALIAS_1)
                    .setSubject(TEST_DN_1)
                    .setSerialNumber(SERIAL_1)
                    .setStartDate(NOW_PLUS_10_YEARS)
                    .setEndDate(NOW)
                    .build());
    }
}
