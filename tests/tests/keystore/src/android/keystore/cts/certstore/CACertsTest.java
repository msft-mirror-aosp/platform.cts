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

package android.keystore.cts.certstore;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import libcore.java.security.StandardNames;

import org.junit.Test;

import java.security.KeyStore;
import java.security.KeyStore.TrustedCertificateEntry;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Collections;

public class CACertsTest {

    @Test
    public void testCerts() throws Exception {
        if (StandardNames.IS_RI) {
            return;
        }
        KeyStore ks = KeyStore.getInstance("AndroidCAStore");
        assertEquals("AndroidCAStore", ks.getType());
        assertEquals("HarmonyJSSE", ks.getProvider().getName());

        ks.load(null, null);
        for (String alias : Collections.list(ks.aliases())) {
            Certificate c = null;
            try {
                c = ks.getCertificate(alias);
                assertNotNull("Certificate not found", c);
                assertTrue("This is not a certificate", ks.isCertificateEntry(alias));
                assertTrue(
                        "Certificate is not an instance of TrustedCertificateEntry",
                        ks.entryInstanceOf(alias, TrustedCertificateEntry.class));
                assertEquals(
                        "No matching certificate entry found in keystore",
                        alias,
                        ks.getCertificateAlias(c));

                assertTrue("This is not X.509 type certificate ", c instanceof X509Certificate);
                X509Certificate cert = (X509Certificate) c;
                assertEquals(
                        "Mismatch in subject-unique-identifier and issuer-unique-identifier",
                        cert.getSubjectUniqueID(),
                        cert.getIssuerUniqueID());
                assertNotNull("Public key not found in certificate", cert.getPublicKey());

                assertTrue("Alias doesn't exist in keystore", ks.containsAlias(alias));
                assertNotNull(
                        "Creation date of the entry identified by the given alias not found",
                        ks.getCreationDate(alias));
                assertNotNull(
                        "Keystore entry for specified alias doesn't exist",
                        ks.getEntry(alias, null));

                assertFalse(
                        "Keystore entry identified by the alias is not a key-related entry",
                        ks.isKeyEntry(alias));
                assertNull(
                        "Alias doesn't exist or doesn't identify a key-related entry.",
                        ks.getKey(alias, null));
                assertNull(
                        "Alias doesn't exist or doesn't contain certificate chain",
                        ks.getCertificateChain(alias));

            } catch (Throwable t) {
                throw new Exception("alias=" + alias + " cert=" + c, t);
            }
        }
    }
}
