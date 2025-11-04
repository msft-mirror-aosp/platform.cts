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

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Directory;
import android.util.Log;

import java.util.concurrent.atomic.AtomicInteger;

/** Directory provider for CTS. */
public class CtsDirectoryProvider extends ContentProvider {
    private static final String TAG = "DirProvider";

    public static final String AUTHORITY =
            "android.provider.cts.contacts.CtsDirectoryProvider.AUTHORITY";

    public static final String ACCOUNT_NAME = "cts";
    public static final String ACCOUNT_TYPE = "com.android.cts.contactsprovider";

    public static final String DISPLAY_NAME = "cts-contacts-directory";

    private static final AtomicInteger sPhoneLookupQueryCount = new AtomicInteger(0);

    private static final int DIRECTORIES = 0;
    private static final int CONTACTS_FILTER = 1;
    private static final int PHONE_LOOKUP = 2;

    private static final UriMatcher sURIMatcher = new UriMatcher(UriMatcher.NO_MATCH);

    static {
        sURIMatcher.addURI(AUTHORITY, "directories", DIRECTORIES);
        sURIMatcher.addURI(AUTHORITY, "contacts/filter/*", CONTACTS_FILTER);
        sURIMatcher.addURI(AUTHORITY, "phone_lookup/*", PHONE_LOOKUP);
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(
            Uri uri,
            String[] projection,
            String selection,
            String[] selectionArgs,
            String sortOrder) {
        Log.d(TAG, "uri: " + uri);
        final int match = sURIMatcher.match(uri);

        return switch (match) {
            case DIRECTORIES -> handleDirectories(projection);
            case CONTACTS_FILTER -> handleFilter(projection);
            case PHONE_LOOKUP -> handlePhoneLookup(projection);
            default -> null;
        };
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }

    /**
     * Build a cursor containing the directory information.
     *
     * <p>See com.android.providers.contacts.ContactDirectoryManager.
     */
    private Cursor handleDirectories(String[] projection) {
        MatrixCursor cursor = new MatrixCursor(projection);
        MatrixCursor.RowBuilder rowBuilder = cursor.newRow();
        rowBuilder.add(Directory.ACCOUNT_NAME, ACCOUNT_NAME);
        rowBuilder.add(Directory.ACCOUNT_TYPE, ACCOUNT_TYPE);
        rowBuilder.add(Directory.DISPLAY_NAME, DISPLAY_NAME);
        return cursor;
    }

    private Cursor handleFilter(String[] projection) {
        // Return an empty cursor.
        return new MatrixCursor(getContactsResultProjection(projection));
    }

    private Cursor handlePhoneLookup(String[] projection) {
        int unused = sPhoneLookupQueryCount.getAndIncrement();
        // Return an empty cursor.
        return new MatrixCursor(getContactsResultProjection(projection));
    }

    private String[] getContactsResultProjection(String[] requestedProjection) {
        return requestedProjection != null ? requestedProjection : new String[] {Contacts._ID};
    }

    /**
     * Gets the count of {@link android.provider.ContactsContract.PhoneLookup#CONTENT_FILTER_URI}
     * queries received by this provider.
     */
    public static int getPhoneLookupQueryCount() {
        return sPhoneLookupQueryCount.get();
    }

    /**
     * Resets the count of {@link android.provider.ContactsContract.PhoneLookup#CONTENT_FILTER_URI}
     * queries received by this provider.
     */
    public static void resetPhoneLookupQueryCount() {
        sPhoneLookupQueryCount.set(0);
    }
}
