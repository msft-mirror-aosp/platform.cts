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

package android.provider.cts.contacts.sessionprovider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Process;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import android.text.TextUtils;
import android.util.Log;

import androidx.test.filters.MediumTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.permissions.PermissionContext;
import com.android.xts.root.annotations.RequireRootInstrumentation;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@MediumTest
@RequireRootInstrumentation(
        reason = "To grant MANAGE_CONTACTS_PICKER_SESSION permission for all tests")
@RequiresFlagsEnabled(android.content.flags.Flags.FLAG_ENABLE_SYSTEM_CONTACTS_PICKER)
@RunWith(BedsteadJUnit4.class)
public class ContactsPickerSessionProviderTest {

    private static final String TAG = "ContactsPickerSessionProviderTest";
    private static final String AUTHORITY = "com.android.contacts.picker.sessions";
    private static final Uri BASE_URI = Uri.parse("content://" + AUTHORITY);
    private static final Uri SESSIONS_URI = Uri.withAppendedPath(BASE_URI, "sessions");

    private static final String PERMISSION_MANAGE_CONTACTS_PICKER_SESSION =
            "android.permission.MANAGE_CONTACTS_PICKER_SESSION";

    private static final String KEY_CONTACT_DATA_IDS = "data_ids";
    private static final String KEY_SESSION_REQUESTER_UID = "requester_uid";

    private static final String SESSION_CLIENT_APP =
            "android.provider.cts.contacts.sessionprovider.client";
    private static final String ACTION_QUERY =
            "android.provider.cts.contacts.sessionprovider.client.ACTION_QUERY";

    private ContentResolver mResolver;
    private Context mContext;

    private final ArrayList<Long> mCreatedRawContactIds = new ArrayList<>();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mResolver = mContext.getContentResolver();
    }

    @After
    public void tearDown() {
        if (!mCreatedRawContactIds.isEmpty()) {
            try (PermissionContext withPermission =
                    TestApis.permissions()
                            .withPermission(
                                    android.Manifest.permission.WRITE_CONTACTS,
                                    android.Manifest.permission.READ_CONTACTS)) {
                ArrayList<ContentProviderOperation> ops = new ArrayList<>();
                for (Long rawContactId : mCreatedRawContactIds) {
                    ops.add(
                            ContentProviderOperation.newDelete(RawContacts.CONTENT_URI)
                                    .withSelection(
                                            RawContacts._ID + "=?",
                                            new String[] {String.valueOf(rawContactId)})
                                    .build());
                }
                mResolver.applyBatch(ContactsContract.AUTHORITY, ops);
            } catch (Exception e) {
                Log.e(TAG, "Cleanup failure", e);
            } finally {
                mCreatedRawContactIds.clear();
            }
        }
    }

    @Test
    public void testInsert_withoutPermission_throwsSecurityException() {
        ContentValues values = new ContentValues();
        values.put(KEY_CONTACT_DATA_IDS, "1,2");
        values.put(KEY_SESSION_REQUESTER_UID, Process.myUid());

        try (PermissionContext withoutPermission =
                TestApis.permissions()
                        .withoutPermission(PERMISSION_MANAGE_CONTACTS_PICKER_SESSION)) {
            assertThrows(
                    "Insert without permission should throw SecurityException",
                    SecurityException.class,
                    () -> mResolver.insert(SESSIONS_URI, values));
        }
    }

    @Test
    public void testInsert_withPermission_returnsUri() {
        try (PermissionContext withPermission =
                TestApis.permissions().withPermission(PERMISSION_MANAGE_CONTACTS_PICKER_SESSION)) {
            ContentValues values = new ContentValues();
            values.put(KEY_CONTACT_DATA_IDS, "1,2");
            values.put(KEY_SESSION_REQUESTER_UID, Process.myUid());

            Uri result = mResolver.insert(SESSIONS_URI, values);

            assertNotNull("Insert with permission should return a URI", result);
            assertEquals("URI authority should match", AUTHORITY, result.getAuthority());
            assertTrue(
                    "URI should contain sessions path",
                    Objects.requireNonNull(result.getPath()).contains("sessions"));
        }
    }

    @Test
    public void testInsert_withSpacesInDataIds_isTrimmedAndValid() {
        try (PermissionContext withPermission =
                TestApis.permissions().withPermission(PERMISSION_MANAGE_CONTACTS_PICKER_SESSION)) {
            ContentValues values = new ContentValues();
            values.put(KEY_CONTACT_DATA_IDS, " 1 , 2 ");
            values.put(KEY_SESSION_REQUESTER_UID, Process.myUid());

            Uri result = mResolver.insert(SESSIONS_URI, values);

            assertNotNull("Insert with spaces in data IDs should succeed", result);
        }
    }

    @Test
    public void testInsert_invalidDataIds_throwsIllegalArgumentException() {
        String[] invalidDataIds = {
            "", // Empty
            "1,,2", // Empty element in middle
            ",", // Only comma
            "1,2,a", // Non-numeric
            ",,", // Only commas
            " , , ", // Spaces and commas
            " ", // Only space
            "1, ,3" // Valid and empty/space
        };

        try (PermissionContext withPermission =
                TestApis.permissions().withPermission(PERMISSION_MANAGE_CONTACTS_PICKER_SESSION)) {
            for (String invalidId : invalidDataIds) {
                ContentValues values = new ContentValues();
                values.put(KEY_CONTACT_DATA_IDS, invalidId);
                values.put(KEY_SESSION_REQUESTER_UID, Process.myUid());

                assertThrows(
                        "Should throw IllegalArgumentException for dataIds: " + invalidId,
                        IllegalArgumentException.class,
                        () -> mResolver.insert(SESSIONS_URI, values));
            }
        }
    }

    @Test
    public void testInsert_missingDataIds_throwsIllegalArgumentException() {
        ContentValues missingDataIds = new ContentValues();
        missingDataIds.put(KEY_SESSION_REQUESTER_UID, Process.myUid());

        try (PermissionContext withPermission =
                TestApis.permissions().withPermission(PERMISSION_MANAGE_CONTACTS_PICKER_SESSION)) {
            assertThrows(
                    "Should throw IllegalArgumentException for missing dataIds",
                    IllegalArgumentException.class,
                    () -> mResolver.insert(SESSIONS_URI, missingDataIds));
        }
    }

    @Test
    public void testInsert_missingRequesterUid_throwsIllegalArgumentException() {
        ContentValues missingUid = new ContentValues();
        missingUid.put(KEY_CONTACT_DATA_IDS, "1,2,3");

        try (PermissionContext withPermission =
                TestApis.permissions().withPermission(PERMISSION_MANAGE_CONTACTS_PICKER_SESSION)) {
            assertThrows(
                    "Should throw IllegalArgumentException for missing requester UID",
                    IllegalArgumentException.class,
                    () -> mResolver.insert(SESSIONS_URI, missingUid));
        }
    }

    @Test
    public void testInsert_invalidUri_throwsException() {
        try (PermissionContext withPermission =
                TestApis.permissions().withPermission(PERMISSION_MANAGE_CONTACTS_PICKER_SESSION)) {
            ContentValues values = new ContentValues();
            values.put(KEY_CONTACT_DATA_IDS, "1,2,3");
            values.put(KEY_SESSION_REQUESTER_UID, Process.myUid());

            Uri invalidUri = Uri.parse("content://" + AUTHORITY + "/invalid");

            assertThrows(
                    IllegalArgumentException.class, () -> mResolver.insert(invalidUri, values));
        }
    }

    @Test
    public void testInsert_withDifferentUid_preventsQuery() {
        try (PermissionContext withPermission =
                TestApis.permissions().withPermission(PERMISSION_MANAGE_CONTACTS_PICKER_SESSION)) {
            int differentUid = Process.myUid() + 100;
            ContentValues values = new ContentValues();
            values.put(KEY_CONTACT_DATA_IDS, "1,2");
            values.put(KEY_SESSION_REQUESTER_UID, differentUid);

            Uri sessionUri = mResolver.insert(SESSIONS_URI, values);

            assertNotNull(sessionUri);
            assertThrows(
                    "Should throw SecurityException because stored UID does not match calling UID",
                    SecurityException.class,
                    () -> mResolver.query(sessionUri, null, null, null, null));
        }
    }

    @Test
    public void testQuery_emptySessionUri_returnsNull() {
        try (PermissionContext withPermission =
                TestApis.permissions().withPermission(PERMISSION_MANAGE_CONTACTS_PICKER_SESSION)) {
            Cursor cursor = mResolver.query(SESSIONS_URI, null, null, null, null);
            assertNull("Query on base sessions URI without ID should return null", cursor);
        }
    }

    @Test
    public void testQuery_validSession_returnsData() throws Exception {
        long dataId;
        ContentValues directValues = new ContentValues();
        try (PermissionContext withPermission =
                TestApis.permissions()
                        .withPermission(
                                android.Manifest.permission.WRITE_CONTACTS,
                                android.Manifest.permission.READ_CONTACTS)) {
            dataId = createContact("Test User A");

            try (Cursor cursor =
                    mResolver.query(
                            Data.CONTENT_URI,
                            null,
                            Data._ID + "=?",
                            new String[] {String.valueOf(dataId)},
                            null)) {
                assertNotNull(cursor);
                assertTrue(cursor.moveToFirst());

                for (String column : cursor.getColumnNames()) {
                    directValues.put(column, cursor.getString(cursor.getColumnIndex(column)));
                }
            }
        }

        try (PermissionContext withPermission =
                TestApis.permissions().withPermission(PERMISSION_MANAGE_CONTACTS_PICKER_SESSION)) {
            ContentValues values = new ContentValues();
            values.put(KEY_CONTACT_DATA_IDS, String.valueOf(dataId));
            values.put(KEY_SESSION_REQUESTER_UID, Process.myUid());

            Uri sessionUri = mResolver.insert(SESSIONS_URI, values);
            assertNotNull(sessionUri);

            try (Cursor cursor = mResolver.query(sessionUri, null, null, null, null)) {
                assertNotNull("Cursor should not be null", cursor);
                assertTrue("Cursor should have rows", cursor.moveToFirst());

                ContentValues sessionValues = new ContentValues();
                for (String column : cursor.getColumnNames()) {
                    sessionValues.put(column, cursor.getString(cursor.getColumnIndex(column)));
                }

                assertEquals(
                        "Column sets should match", directValues.keySet(), sessionValues.keySet());

                for (String key : directValues.keySet()) {
                    assertEquals(
                            "Value mismatch for column: " + key,
                            directValues.getAsString(key),
                            sessionValues.getAsString(key));
                }
            }
        }
    }

    @Test
    public void testQuery_withUriPermission_returnsData() throws Exception {
        long dataId;
        ContentValues directValues = new ContentValues();
        try (PermissionContext withPermission =
                     TestApis.permissions()
                             .withPermission(
                                     android.Manifest.permission.WRITE_CONTACTS,
                                     android.Manifest.permission.READ_CONTACTS)) {
            dataId = createContact("Test User Grant");

            try (Cursor cursor =
                         mResolver.query(
                                 Data.CONTENT_URI,
                                 null,
                                 Data._ID + "=?",
                                 new String[]{String.valueOf(dataId)},
                                 null)) {
                assertNotNull(cursor);
                assertTrue(cursor.moveToFirst());

                for (String column : cursor.getColumnNames()) {
                    directValues.put(column, cursor.getString(cursor.getColumnIndex(column)));
                }
            }
        }
        Uri sessionUri;
        try (PermissionContext withPermission =
                     TestApis.permissions().withPermission(
                             PERMISSION_MANAGE_CONTACTS_PICKER_SESSION)) {
            PackageManager pm = mContext.getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(SESSION_CLIENT_APP, 0);

            ContentValues values = new ContentValues();
            values.put(KEY_CONTACT_DATA_IDS, String.valueOf(dataId));
            values.put(KEY_SESSION_REQUESTER_UID, ai.uid);

            sessionUri = mResolver.insert(SESSIONS_URI, values);
            assertNotNull(sessionUri);

            mContext.grantUriPermission(
                    SESSION_CLIENT_APP, sessionUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }

        try (PermissionContext withoutPermission =
                     TestApis.permissions()
                             .withoutPermission(PERMISSION_MANAGE_CONTACTS_PICKER_SESSION)) {

            final CountDownLatch latch = new CountDownLatch(1);
            final int[] clientAppResult = new int[1];

            Intent queryIntent = new Intent(ACTION_QUERY);
            queryIntent.setPackage(SESSION_CLIENT_APP);
            queryIntent.putExtra("target_uri", sessionUri);
            queryIntent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);

            mContext.sendOrderedBroadcast(
                    queryIntent,
                    null,
                    new BroadcastReceiver() {
                        @Override
                        public void onReceive(Context context, Intent intent) {
                            clientAppResult[0] = getResultCode();
                            latch.countDown();
                        }
                    },
                    null,
                    Activity.RESULT_CANCELED,
                    null,
                    null);

            boolean received = latch.await(10, TimeUnit.SECONDS);
            assertTrue("Timed out waiting for Client App", received);
            assertEquals(
                    "Client App should have access via the URI grant",
                    Activity.RESULT_OK,
                    clientAppResult[0]);
        }
    }

    @Test
    public void testQuery_sessionWithDeletedData_excludesData() throws Exception {
        long dataId;
        long rawContactId;
        try (PermissionContext withPermission =
                TestApis.permissions()
                        .withPermission(
                                android.Manifest.permission.WRITE_CONTACTS,
                                android.Manifest.permission.READ_CONTACTS)) {
            dataId = createContact("User To Delete");
            rawContactId = mCreatedRawContactIds.getLast();
        }

        Uri sessionUri;
        try (PermissionContext withPermission =
                TestApis.permissions().withPermission(PERMISSION_MANAGE_CONTACTS_PICKER_SESSION)) {
            ContentValues values = new ContentValues();
            values.put(KEY_CONTACT_DATA_IDS, String.valueOf(dataId));
            values.put(KEY_SESSION_REQUESTER_UID, Process.myUid());
            sessionUri = mResolver.insert(SESSIONS_URI, values);
        }

        try (PermissionContext withPermission =
                TestApis.permissions().withPermission(android.Manifest.permission.WRITE_CONTACTS)) {
            mResolver.delete(
                    RawContacts.CONTENT_URI,
                    RawContacts._ID + "=?",
                    new String[] {String.valueOf(rawContactId)});
        }

        try (PermissionContext withPermission =
                TestApis.permissions().withPermission(PERMISSION_MANAGE_CONTACTS_PICKER_SESSION)) {
            assertNotNull(sessionUri);
            try (Cursor cursor = mResolver.query(sessionUri, null, null, null, null)) {
                assertNotNull("Cursor should not be null", cursor);
                assertEquals("Cursor should be empty as data was deleted", 0, cursor.getCount());
            }
        }
    }

    @Test
    public void testQuery_unknownSessionId_returnsNull() {
        try (PermissionContext withPermission =
                TestApis.permissions().withPermission(PERMISSION_MANAGE_CONTACTS_PICKER_SESSION)) {
            Uri invalidUri = SESSIONS_URI.buildUpon().appendPath("foo").build();
            Cursor cursor = mResolver.query(invalidUri, null, null, null, null);
            assertNull("Query with unknown/invalid session ID should return null", cursor);
        }
    }

    @Test
    public void testQuery_invalidUri_throwsIllegalArgumentException() {
        try (PermissionContext withPermission =
                TestApis.permissions().withPermission(PERMISSION_MANAGE_CONTACTS_PICKER_SESSION)) {
            Uri totallyInvalidUri = BASE_URI.buildUpon().appendPath("foo").build();

            assertThrows(
                    "Query with invalid URI structure should throw IllegalArgumentException",
                    IllegalArgumentException.class,
                    () -> mResolver.query(totallyInvalidUri, null, null, null, null));
        }
    }

    @Test
    public void testQuery_withoutPermission_throwsSecurityException() {
        Uri sessionUri;
        try (PermissionContext withPermission =
                TestApis.permissions().withPermission(PERMISSION_MANAGE_CONTACTS_PICKER_SESSION)) {
            ContentValues values = new ContentValues();
            values.put(KEY_CONTACT_DATA_IDS, "1,2");
            values.put(KEY_SESSION_REQUESTER_UID, Process.myUid());
            sessionUri = mResolver.insert(SESSIONS_URI, values);
            assertNotNull(sessionUri);
        }

        try (PermissionContext withoutPermission =
                TestApis.permissions()
                        .withoutPermission(PERMISSION_MANAGE_CONTACTS_PICKER_SESSION)) {
            final Uri finalSessionUri = sessionUri;
            assertThrows(
                    "Query without permission should throw SecurityException",
                    SecurityException.class,
                    () -> mResolver.query(finalSessionUri, null, null, null, null));
        }
    }

    @Test
    public void testQuery_withSelectionAndSort_filtersCorrectly() throws Exception {
        long idAlice;
        long idBob;
        try (PermissionContext withPermission =
                TestApis.permissions()
                        .withPermission(
                                android.Manifest.permission.WRITE_CONTACTS,
                                android.Manifest.permission.READ_CONTACTS)) {
            idAlice = createContact("Alice");
            idBob = createContact("Bob");
        }

        try (PermissionContext withPermission =
                TestApis.permissions().withPermission(PERMISSION_MANAGE_CONTACTS_PICKER_SESSION)) {
            ContentValues values = new ContentValues();
            values.put(KEY_CONTACT_DATA_IDS, idAlice + "," + idBob);
            values.put(KEY_SESSION_REQUESTER_UID, Process.myUid());
            Uri sessionUri = mResolver.insert(SESSIONS_URI, values);

            String selection = StructuredName.DISPLAY_NAME + "=?";
            String[] selectionArgs = new String[] {"Bob"};
            String sortOrder = StructuredName.DISPLAY_NAME + " ASC";

            assertNotNull(sessionUri);

            try (Cursor cursor =
                    mResolver.query(
                            sessionUri,
                            new String[] {StructuredName.DISPLAY_NAME},
                            selection,
                            selectionArgs,
                            sortOrder)) {

                assertNotNull(cursor);
                assertEquals(
                        "Should only return 1 row matching the selection", 1, cursor.getCount());
                assertTrue(cursor.moveToFirst());
                assertEquals("Bob", cursor.getString(0));
            }
        }
    }

    @Test
    public void testQuery_withSortOrder_sortsCorrectly() throws Exception {
        String[] names = {"Charlie", "Alice", "David", "Bob", "Eve"};
        ArrayList<Long> dataIds = new ArrayList<>();
        try (PermissionContext withPermission =
                TestApis.permissions()
                        .withPermission(
                                android.Manifest.permission.WRITE_CONTACTS,
                                android.Manifest.permission.READ_CONTACTS)) {
            for (String name : names) {
                dataIds.add(createContact(name));
            }
        }

        try (PermissionContext withPermission =
                TestApis.permissions().withPermission(PERMISSION_MANAGE_CONTACTS_PICKER_SESSION)) {
            ContentValues values = new ContentValues();
            values.put(KEY_CONTACT_DATA_IDS, TextUtils.join(",", dataIds));
            values.put(KEY_SESSION_REQUESTER_UID, Process.myUid());
            Uri sessionUri = mResolver.insert(SESSIONS_URI, values);
            assertNotNull(sessionUri);

            // Test ascending order
            String[] expectedAsc = {"Alice", "Bob", "Charlie", "David", "Eve"};
            try (Cursor cursor =
                    mResolver.query(
                            sessionUri,
                            new String[] {StructuredName.DISPLAY_NAME},
                            null,
                            null,
                            StructuredName.DISPLAY_NAME + " ASC")) {
                assertNotNull(cursor);
                assertEquals(names.length, cursor.getCount());
                int i = 0;
                while (cursor.moveToNext()) {
                    assertEquals(expectedAsc[i++], cursor.getString(0));
                }
            }

            // Test descending order
            String[] expectedDesc = {"Eve", "David", "Charlie", "Bob", "Alice"};
            try (Cursor cursor =
                    mResolver.query(
                            sessionUri,
                            new String[] {StructuredName.DISPLAY_NAME},
                            null,
                            null,
                            StructuredName.DISPLAY_NAME + " DESC")) {
                assertNotNull(cursor);
                assertEquals(names.length, cursor.getCount());
                int i = 0;
                while (cursor.moveToNext()) {
                    assertEquals(expectedDesc[i++], cursor.getString(0));
                }
            }
        }
    }

    @Test
    public void testUpdate_throwsUnsupportedOperationException() {
        try (PermissionContext withPermission =
                TestApis.permissions().withPermission(PERMISSION_MANAGE_CONTACTS_PICKER_SESSION)) {
            assertThrows(
                    UnsupportedOperationException.class,
                    () -> mResolver.update(SESSIONS_URI, new ContentValues(), null, null));
        }
    }

    @Test
    public void testDelete_throwsUnsupportedOperationException() {
        try (PermissionContext withPermission =
                TestApis.permissions().withPermission(PERMISSION_MANAGE_CONTACTS_PICKER_SESSION)) {
            assertThrows(
                    UnsupportedOperationException.class,
                    () -> mResolver.delete(SESSIONS_URI, null, null));
        }
    }

    @Test
    public void testGetType_validSessionUri_returnsDataContentType() {
        try (PermissionContext withPermission =
                TestApis.permissions().withPermission(PERMISSION_MANAGE_CONTACTS_PICKER_SESSION)) {
            ContentValues values = new ContentValues();
            values.put(KEY_CONTACT_DATA_IDS, "1");
            values.put(KEY_SESSION_REQUESTER_UID, Process.myUid());
            Uri sessionUri = mResolver.insert(SESSIONS_URI, values);

            assertNotNull(sessionUri);
            assertEquals(Data.CONTENT_TYPE, mResolver.getType(sessionUri));
        }
    }

    @Test
    public void testGetType_sessionsBaseUri_returnsNull() {
        try (PermissionContext withPermission =
                TestApis.permissions().withPermission(PERMISSION_MANAGE_CONTACTS_PICKER_SESSION)) {
            assertNull(mResolver.getType(SESSIONS_URI));
        }
    }

    @Test
    public void testGetType_invalidUri_returnsNull() {
        try (PermissionContext withPermission =
                TestApis.permissions().withPermission(PERMISSION_MANAGE_CONTACTS_PICKER_SESSION)) {
            Uri invalidUri = BASE_URI.buildUpon().appendPath("invalid").build();
            assertNull(mResolver.getType(invalidUri));
        }
    }

    @Test
    public void testCall_invalidMethod_doesNothing() {
        try (PermissionContext withPermission =
                TestApis.permissions().withPermission(PERMISSION_MANAGE_CONTACTS_PICKER_SESSION)) {

            assertNull(mResolver.call(BASE_URI, "invalidMethodName", null, null));
        }
    }

    private long createContact(String displayName) throws Exception {
        ArrayList<ContentProviderOperation> ops = new ArrayList<>();

        int rawContactOpIndex = ops.size();
        ops.add(
                ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
                        .withValue(RawContacts.ACCOUNT_TYPE, null)
                        .withValue(RawContacts.ACCOUNT_NAME, null)
                        .build());

        ops.add(
                ContentProviderOperation.newInsert(Data.CONTENT_URI)
                        .withValueBackReference(Data.RAW_CONTACT_ID, rawContactOpIndex)
                        .withValue(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE)
                        .withValue(StructuredName.DISPLAY_NAME, displayName)
                        .build());

        android.content.ContentProviderResult[] results =
                mResolver.applyBatch(ContactsContract.AUTHORITY, ops);

        assertNotNull(results[0].uri);
        long rawContactId = Long.parseLong(results[0].uri.getLastPathSegment());
        mCreatedRawContactIds.add(rawContactId);

        assertNotNull(results[1].uri);
        return Long.parseLong(results[1].uri.getLastPathSegment());
    }
}
