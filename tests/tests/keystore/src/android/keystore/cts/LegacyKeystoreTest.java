/*
 * Copyright (C) 2024 The Android Open Source Project
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

import static org.junit.Assert.assertThrows;

import android.os.ServiceManager;
import android.os.ServiceSpecificException;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.security.keystore2.Flags;
import android.security.legacykeystore.ILegacyKeystore;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * Tests for the Legacy Keystore service, focusing on its behavior under different flag settings.
 */
public class LegacyKeystoreTest {
    private static final String LEGACY_KEYSTORE_SERVICE_NAME = "android.security.legacykeystore";
    private static final String TEST_DATABASE_NAME = "test_database";

    private static final byte[] TEST_DATA = new byte[512];

    @ClassRule public static final SetFlagsRule.ClassRule mClassRule = new SetFlagsRule.ClassRule();
    @Rule public final SetFlagsRule mSetFlagsRule = mClassRule.createSetFlagsRule();


    /** Obtains an interface to the Legacy Keystore service. */
    public static ILegacyKeystore getLegacyKeystore() {
        return ILegacyKeystore.Stub.asInterface(
                ServiceManager.checkService(LEGACY_KEYSTORE_SERVICE_NAME));
    }

    @Test
    @DisableFlags(Flags.FLAG_DISABLE_LEGACY_KEYSTORE_PUT_V2)
    public void testPutWithPutFlagDisabled() throws Exception {
        ILegacyKeystore legacyKeystore = getLegacyKeystore();
        // put operation should not throw an exception when the flag is disabled.
        legacyKeystore.put(TEST_DATABASE_NAME, ILegacyKeystore.UID_SELF, TEST_DATA);
    }

    @Test
    @DisableFlags(Flags.FLAG_DISABLE_LEGACY_KEYSTORE_GET)
    public void testGetWithGetFlagDisabled() throws Exception {
        ILegacyKeystore legacyKeystore = getLegacyKeystore();
        // get operation should not throw an exception when the flag is disabled.
        legacyKeystore.get(TEST_DATABASE_NAME, ILegacyKeystore.UID_SELF);
    }

    @Test
    @EnableFlags(Flags.FLAG_DISABLE_LEGACY_KEYSTORE_GET)
    public void testGetWithGetFlagEnabled_ThrowsException() {
        ILegacyKeystore legacyKeystore = getLegacyKeystore();
        assertThrows(
                ServiceSpecificException.class,
                () -> legacyKeystore.get(TEST_DATABASE_NAME, ILegacyKeystore.UID_SELF));
    }
}
