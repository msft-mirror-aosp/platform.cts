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

package android.telephony2.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.net.Uri;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class TelephonyProviderPermissionTest {
    private static final String CARRIER_PROVIDER_AUTHORITY = "carrier_information";
    private static final String HBPCD_LOOKUP_PROVIDER_AUTHORITY = "hbpcd_lookup";
    private static final String EXPECTED_READ_PERMISSION = Manifest.permission.READ_PHONE_STATE;

    @Before
    public void setUp() throws Exception {
        PackageManager pm =
                InstrumentationRegistry.getInstrumentation().getContext().getPackageManager();
        assumeTrue(
                "Skipping test: Telephony feature is not supported on this device",
                pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY));
    }

    @Test
    public void testCarrierProviderReadPermission() {
        verifyReadPermission(CARRIER_PROVIDER_AUTHORITY);
        // Verify that access is denied without permission
        verifyQueryWithNoPermission(Uri.parse("content://carrier_information/carrier"));
    }

    @Test
    public void testHbpcdLookupProviderReadPermission() {
        verifyReadPermission(HBPCD_LOOKUP_PROVIDER_AUTHORITY);
        // Verify that access is denied without permission
        verifyQueryWithNoPermission(Uri.parse("content://hbpcd_lookup/mcc_idd"));
    }

    private void verifyReadPermission(String authority) {
        PackageManager pm =
                InstrumentationRegistry.getInstrumentation().getContext().getPackageManager();
        ProviderInfo info = pm.resolveContentProvider(authority, 0);
        assertNotNull("Provider with authority " + authority + " not found", info);
        assertEquals("Provider " + authority + " has incorrect read permission",
                EXPECTED_READ_PERMISSION, info.readPermission);
    }

    private void verifyQueryWithNoPermission(Uri uri) {
        // The Telephony2 CTS package does NOT have READ_PHONE_STATE permission.
        // So we can directly query and expect SecurityException.
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        assertEquals("Test package should not have READ_PHONE_STATE permission",
                PackageManager.PERMISSION_DENIED,
                context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE));

        ContentResolver resolver =
                InstrumentationRegistry.getInstrumentation().getContext().getContentResolver();
        try (Cursor c = resolver.query(uri, null, null, null, null)) {
            fail("Expected SecurityException for unauthorized query to " + uri);
        } catch (SecurityException e) {
            // Expected behavior
        }
    }
}
