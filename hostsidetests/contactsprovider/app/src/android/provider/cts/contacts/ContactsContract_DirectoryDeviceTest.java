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

package android.provider.cts.contacts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Directory;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.PollingCheck;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ContactsContract_DirectoryDeviceTest {

    private ContentResolver mResolver;

    @Before
    public void setUp() throws Exception {
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(
                        "android.permission.READ_COMPAT_CHANGE_CONFIG",
                        "android.permission.LOG_COMPAT_CHANGE");

        mResolver = ApplicationProvider.getApplicationContext().getContentResolver();
    }

    @After
    public void tearDown() throws Exception {
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .dropShellPermissionIdentity();
        CtsDirectoryProvider.resetPhoneLookupQueryCount();
    }

    @Test
    public void testQueryDirectoryWithWriteContactsPermission() throws Exception {
        final long directoryId = getDirectoryId();
        final Uri contactsFilterUri = getContactsFilterUri(directoryId);
        try (Cursor c = mResolver.query(contactsFilterUri, null, null, null, null)) {
            assertNotNull(c);
        }
        final Uri phoneLookupUri = getPhoneLookupUri(directoryId);
        try (Cursor c = mResolver.query(phoneLookupUri, null, null, null, null)) {
            assertNull(
                    "Directories without READ_CALL_LOG should not handle PhoneLookup queries.", c);
        }
        // Phone lookup queries shouldn't be received by the provider.
        assertEquals(0, CtsDirectoryProvider.getPhoneLookupQueryCount());
    }

    @Test
    public void testQueryDirectoryWithWriteContactsAndReadCallLogPermissions() throws Exception {
        final long directoryId = waitForDirectoryId();
        final Uri phoneLookupUri = getPhoneLookupUri(directoryId);
        try (Cursor c = mResolver.query(phoneLookupUri, null, null, null, null)) {
            assertNotNull(c);
        }
    }

    @Test
    public void testQueryDirectoryWithReadCallLogPermissions() throws Exception {
        final long directoryId = waitForDirectoryId();
        final Uri contactsFilterUri = getContactsFilterUri(directoryId);
        try (Cursor c = mResolver.query(contactsFilterUri, null, null, null, null)) {
            assertNull(c);
        }
        final Uri phoneLookupUri = getPhoneLookupUri(directoryId);
        try (Cursor c = mResolver.query(phoneLookupUri, null, null, null, null)) {
            assertNull(c);
        }
    }

    @Test
    public void testQueryDirectoryWithoutPermissions() throws Exception {
        final long directoryId = waitForDirectoryId();
        final Uri contactsFilterUri = getContactsFilterUri(directoryId);
        try (Cursor c = mResolver.query(contactsFilterUri, null, null, null, null)) {
            // Without permissions, the query should fail and return null.
            assertNull("Directories without WRITE_CONTACTS should not receive queries", c);
        }
    }

    @Test
    public void testQueryDirectoryWithoutPermissions_requirePermissionsForDirectoryQueriesDisabled()
            throws Exception {
        final long directoryId = waitForDirectoryId();
        final Uri contactsFilterUri = getContactsFilterUri(directoryId);
        try (Cursor c = mResolver.query(contactsFilterUri, null, null, null, null)) {
            assertNotNull(c);
        }
        final Uri phoneLookupUri = getPhoneLookupUri(directoryId);
        try (Cursor c = mResolver.query(phoneLookupUri, null, null, null, null)) {
            assertNotNull(c);
        }
    }

    private Uri getPhoneLookupUri(long directoryId) {
        return ContactsContract.PhoneLookup.CONTENT_FILTER_URI
                .buildUpon()
                .appendPath("123")
                .appendQueryParameter(
                        ContactsContract.DIRECTORY_PARAM_KEY, String.valueOf(directoryId))
                .build();
    }

    private Uri getContactsFilterUri(long directoryId) {
        return Contacts.CONTENT_FILTER_URI
                .buildUpon()
                .appendPath("a")
                .appendQueryParameter(
                        ContactsContract.DIRECTORY_PARAM_KEY, String.valueOf(directoryId))
                .build();
    }

    private long waitForDirectoryId() throws Exception {
        // Directories are created asynchronously by the contacts provider and there doesn't appear
        // to be any guarantee that a new provider will be detected before returning the result
        // for the query. Hence poll until it exists.
        return PollingCheck.waitFor(10_000, () -> getDirectoryId(), (id) -> id != -1);
    }

    private long getDirectoryId() {
        try (Cursor c =
                mResolver.query(
                        Directory.CONTENT_URI,
                        new String[] {Directory._ID},
                        Directory.DIRECTORY_AUTHORITY + "=?",
                        new String[] {CtsDirectoryProvider.AUTHORITY},
                        null)) {
            if (c != null && c.moveToFirst()) {
                return c.getLong(0);
            }
        }
        return -1;
    }
}
