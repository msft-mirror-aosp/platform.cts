/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.cts.content;

import static com.google.common.truth.Truth.assertThat;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.ContentProvider;
import android.content.ContentProvider.PipeDataWriter;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.UriMatcher;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

public class MockContentProvider extends ContentProvider implements PipeDataWriter<String> {
    private static final String TAG = "MockContentProvider";

    private static final String DEFAULT_AUTHORITY = "ctstest";
    private static final String DEFAULT_DBNAME = "ctstest.db";
    private static final int DBVERSION = 2;

    private static final int TESTTABLE1 = 1;
    private static final int TESTTABLE1_ID = 2;
    private static final int TESTTABLE1_CROSS = 3;
    private static final int TESTTABLE2 = 4;
    private static final int TESTTABLE2_ID = 5;
    private static final int CRASH_ID = 6;
    private static final int HANG_ID = 7;
    private static final String GROUP_BY = null;
    private static final String HAVING = null;
    private static final String LIMIT = null;
    private static final String SELECTION = null;
    private static final String[] SELECTION_ARGS = null;
    private static final String TEST_TABLE_NAME1 = "TestTable1";
    private static final String TEST_TABLE_NAME2 = "TestTable2";
    private static final String ID = "_id=";

    private static @Nullable Uri sRefreshedUri;
    private static boolean sRefreshReturnValue;

    private final String mAuthority;
    private final String mDbName;
    private final UriMatcher mUrlMatcher;
    private final HashMap<String, String> mCtsDbtable1ListProjectionMap;
    private final HashMap<String, String> mCtsDbtable2ListProjectionMap;

    private SQLiteOpenHelper mOpenHelper;

    private static final class DatabaseHelper extends SQLiteOpenHelper {

        DatabaseHelper(Context context, String dbname) {
            super(context, dbname, null, DBVERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(
                    "CREATE TABLE TestTable1 ("
                            + "_id INTEGER PRIMARY KEY, "
                            + "key TEXT, "
                            + "value INTEGER"
                            + ");");

            db.execSQL(
                    "CREATE TABLE TestTable2 ("
                            + "_id INTEGER PRIMARY KEY, "
                            + "key TEXT, "
                            + "value INTEGER"
                            + ");");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            db.execSQL("DROP TABLE IF EXISTS TestTable1");
            db.execSQL("DROP TABLE IF EXISTS TestTable2");
            onCreate(db);
        }
    }

    public MockContentProvider() {
        this(DEFAULT_AUTHORITY, DEFAULT_DBNAME);
    }

    public MockContentProvider(String authority, String dbName) {
        mAuthority = authority;
        mDbName = dbName;

        mUrlMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        mUrlMatcher.addURI(mAuthority, "testtable1", TESTTABLE1);
        mUrlMatcher.addURI(mAuthority, "testtable1/#", TESTTABLE1_ID);
        mUrlMatcher.addURI(mAuthority, "testtable1/cross", TESTTABLE1_CROSS);
        mUrlMatcher.addURI(mAuthority, "testtable2", TESTTABLE2);
        mUrlMatcher.addURI(mAuthority, "testtable2/#", TESTTABLE2_ID);
        mUrlMatcher.addURI(mAuthority, "crash", CRASH_ID);
        mUrlMatcher.addURI(mAuthority, "hang", HANG_ID);

        mCtsDbtable1ListProjectionMap = new HashMap<>();
        mCtsDbtable1ListProjectionMap.put("_id", "_id");
        mCtsDbtable1ListProjectionMap.put("key", "key");
        mCtsDbtable1ListProjectionMap.put("value", "value");

        mCtsDbtable2ListProjectionMap = new HashMap<>();
        mCtsDbtable2ListProjectionMap.put("_id", "_id");
        mCtsDbtable2ListProjectionMap.put("key", "key");
        mCtsDbtable2ListProjectionMap.put("value", "value");
    }

    @Override
    public boolean onCreate() {
        mOpenHelper = new DatabaseHelper(getContext(), mDbName);
        crashOnLaunchIfNeeded();
        return true;
    }

    @Override
    public int delete(
            @NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        String segment;
        int count;

        switch (mUrlMatcher.match(uri)) {
            case TESTTABLE1 -> {
                if (null == selection) {
                    // get the count when remove all rows
                    selection = "1";
                }
                count = db.delete(TEST_TABLE_NAME1, selection, selectionArgs);
            }
            case TESTTABLE1_ID -> {
                segment = uri.getPathSegments().get(1);
                count =
                        db.delete(
                                TEST_TABLE_NAME1,
                                ID
                                        + segment
                                        + (!TextUtils.isEmpty(selection)
                                                ? " AND (" + selection + ')'
                                                : ""),
                                selectionArgs);
            }
            case TESTTABLE2 -> count = db.delete(TEST_TABLE_NAME2, selection, selectionArgs);
            case TESTTABLE2_ID -> {
                segment = uri.getPathSegments().get(1);
                count =
                        db.delete(
                                TEST_TABLE_NAME2,
                                ID
                                        + segment
                                        + (!TextUtils.isEmpty(selection)
                                                ? " AND (" + selection + ')'
                                                : ""),
                                selectionArgs);
            }
            case CRASH_ID -> {
                // Wha...?  Delete ME?!?  O.K.!
                Log.i(TAG, "Delete self requested!");
                count = 1;
                Process.killProcess(Process.myPid());
            }
            default -> throw new IllegalArgumentException("Unknown URL " + uri);
        }

        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }

    @Override
    public String getType(@NonNull Uri uri) {
        return switch (mUrlMatcher.match(uri)) {
            case TESTTABLE1 -> "vnd.android.cursor.dir/com.android.content.testtable1";
            case TESTTABLE1_ID -> "vnd.android.cursor.item/com.android.content.testtable1";
            case TESTTABLE1_CROSS -> "vnd.android.cursor.cross/com.android.content.testtable1";
            case TESTTABLE2 -> "vnd.android.cursor.dir/com.android.content.testtable2";
            case TESTTABLE2_ID -> "vnd.android.cursor.item/com.android.content.testtable2";
            default -> throw new IllegalArgumentException("Unknown URL " + uri);
        };
    }

    @Override
    public String[] getStreamTypes(@NonNull Uri uri, @NonNull String mimeTypeFilter) {
        if (mUrlMatcher.match(uri) == TESTTABLE2_ID) {
            switch (Integer.parseInt(uri.getPathSegments().get(1)) % 10) {
                case 0:
                    return new String[] {"image/jpeg"};
                case 1:
                    return new String[] {"audio/mpeg"};
                case 2:
                    return new String[] {"video/mpeg", "audio/mpeg"};
            }
        }
        return super.getStreamTypes(uri, mimeTypeFilter);
    }

    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues initialValues) {
        long rowID;
        ContentValues values;
        String table;
        Uri testUri;

        if (initialValues != null) values = new ContentValues(initialValues);
        else values = new ContentValues();

        if (!values.containsKey("value")) values.put("value", -1);

        switch (mUrlMatcher.match(uri)) {
            case TESTTABLE1 -> {
                table = TEST_TABLE_NAME1;
                testUri =
                        new Uri.Builder()
                                .scheme(ContentResolver.SCHEME_CONTENT)
                                .authority(mAuthority)
                                .appendPath("testtable1")
                                .build();
            }
            case TESTTABLE2 -> {
                table = TEST_TABLE_NAME2;
                testUri =
                        new Uri.Builder()
                                .scheme(ContentResolver.SCHEME_CONTENT)
                                .authority(mAuthority)
                                .appendPath("testtable2")
                                .build();
            }
            default -> throw new IllegalArgumentException("Unknown URL " + uri);
        }

        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        rowID = db.insert(table, "key", values);

        if (rowID > 0) {
            Uri url = ContentUris.withAppendedId(testUri, rowID);
            getContext().getContentResolver().notifyChange(url, null);
            return url;
        }

        throw new SQLException("Failed to insert row into " + uri);
    }

    @Override
    public @Nullable Cursor query(
            @NonNull Uri uri,
            @Nullable String[] projection,
            @Nullable Bundle queryArgs,
            @Nullable CancellationSignal cancellationSignal) {
        if (queryArgs != null && queryArgs.containsKey(ContentResolver.QUERY_ARG_SORT_LOCALE)) {
            final SQLiteDatabase db = mOpenHelper.getReadableDatabase();
            final String locale = queryArgs.getString(ContentResolver.QUERY_ARG_SORT_LOCALE);
            final String safeLocale = locale.replaceAll("[^a-zA-Z]", "");
            try (Cursor c =
                    db.rawQuery(
                            "SELECT icu_load_collation(?, ?);",
                            new String[] {locale, safeLocale},
                            cancellationSignal)) {
                while (c.moveToNext()) {
                    // Expected
                }
            }

            final SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
            qb.setTables(TEST_TABLE_NAME1);
            qb.setProjectionMap(mCtsDbtable1ListProjectionMap);

            final String sortOrder =
                    TextUtils.join(
                            ", ", queryArgs.getStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS));
            return qb.query(
                    db,
                    projection,
                    SELECTION,
                    SELECTION_ARGS,
                    GROUP_BY,
                    HAVING,
                    sortOrder + " COLLATE " + safeLocale,
                    LIMIT,
                    cancellationSignal);
        } else {
            return super.query(uri, projection, queryArgs, cancellationSignal);
        }
    }

    @Override
    public Cursor query(
            @NonNull Uri uri,
            @Nullable String[] projection,
            @Nullable String selection,
            @Nullable String[] selectionArgs,
            @Nullable String sortOrder) {
        return query(uri, projection, selection, selectionArgs, sortOrder, null);
    }

    @Override
    public Cursor query(
            @NonNull Uri uri,
            @Nullable String[] projection,
            @Nullable String selection,
            @Nullable String[] selectionArgs,
            @Nullable String sortOrder,
            @Nullable CancellationSignal cancellationSignal) {

        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();

        switch (mUrlMatcher.match(uri)) {
            case TESTTABLE1 -> {
                qb.setTables(TEST_TABLE_NAME1);
                qb.setProjectionMap(mCtsDbtable1ListProjectionMap);
            }
            case TESTTABLE1_ID -> {
                qb.setTables(TEST_TABLE_NAME1);
                qb.appendWhere(ID + uri.getPathSegments().get(1));
            }
            case TESTTABLE1_CROSS ->
                    // Create a ridiculous cross-product of the test table.  This is done
                    // to create an artificially long-running query to enable us to test
                    // remote query cancellation in ContentResolverTest.
                    qb.setTables(
                            "TestTable1 a, TestTable1 b, TestTable1 c, TestTable1 d, TestTable1 e");
            case TESTTABLE2 -> {
                qb.setTables(TEST_TABLE_NAME2);
                qb.setProjectionMap(mCtsDbtable2ListProjectionMap);
            }
            case TESTTABLE2_ID -> {
                qb.setTables(TEST_TABLE_NAME2);
                qb.appendWhere(ID + uri.getPathSegments().get(1));
            }
            case CRASH_ID -> {
                crashOnLaunchIfNeeded();
                qb.setTables(TEST_TABLE_NAME1);
                qb.setProjectionMap(mCtsDbtable1ListProjectionMap);
            }
            case HANG_ID -> {
                while (true) {
                    Log.i(TAG, "Hanging provider by request...");
                    SystemClock.sleep(1000);
                }
            }
            default -> throw new IllegalArgumentException("Unknown URL " + uri);
        }

        /* If no sort order is specified use the default */
        String orderBy = TextUtils.isEmpty(sortOrder) ? "_id" : sortOrder;

        SQLiteDatabase db = mOpenHelper.getReadableDatabase();
        Cursor c =
                qb.query(
                        db,
                        projection,
                        selection,
                        selectionArgs,
                        GROUP_BY,
                        HAVING,
                        orderBy,
                        LIMIT,
                        cancellationSignal);

        c.setNotificationUri(getContext().getContentResolver(), uri);
        return c;
    }

    @Override
    public int update(
            @NonNull Uri uri,
            @Nullable ContentValues values,
            @Nullable String selection,
            @Nullable String[] selectionArgs) {
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        String segment;
        int count = 0;
        switch (mUrlMatcher.match(uri)) {
            case TESTTABLE1 ->
                    count = db.update(TEST_TABLE_NAME1, values, selection, selectionArgs);
            case TESTTABLE1_ID -> {
                segment = uri.getPathSegments().get(1);
                count =
                        db.update(
                                TEST_TABLE_NAME1,
                                values,
                                ID
                                        + segment
                                        + (!TextUtils.isEmpty(selection)
                                                ? " AND (" + selection + ')'
                                                : ""),
                                selectionArgs);
            }
            case TESTTABLE2 -> db.update(TEST_TABLE_NAME2, values, selection, selectionArgs);
            case TESTTABLE2_ID -> {
                segment = uri.getPathSegments().get(1);
                count =
                        db.update(
                                TEST_TABLE_NAME2,
                                values,
                                ID
                                        + segment
                                        + (!TextUtils.isEmpty(selection)
                                                ? " AND (" + selection + ')'
                                                : ""),
                                selectionArgs);
            }
            default -> throw new IllegalArgumentException("Unknown URL " + uri);
        }

        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }

    @Override
    public AssetFileDescriptor openAssetFile(@NonNull Uri uri, @NonNull String mode)
            throws FileNotFoundException {
        if (mUrlMatcher.match(uri) == CRASH_ID) {
            crashOnLaunchIfNeeded();
            return new AssetFileDescriptor(
                    openPipeHelper(uri, null, null, "This is the openAssetFile test data!", this),
                    0,
                    AssetFileDescriptor.UNKNOWN_LENGTH);
        }
        return super.openAssetFile(uri, mode);
    }

    @Override
    public AssetFileDescriptor openTypedAssetFile(
            @NonNull Uri uri, @NonNull String mimeTypeFilter, @Nullable Bundle opts)
            throws FileNotFoundException {
        if (mUrlMatcher.match(uri) == CRASH_ID) {
            crashOnLaunchIfNeeded();
            return new AssetFileDescriptor(
                    openPipeHelper(
                            uri, null, null, "This is the openTypedAssetFile test data!", this),
                    0,
                    AssetFileDescriptor.UNKNOWN_LENGTH);
        }
        return super.openTypedAssetFile(uri, mimeTypeFilter, opts);
    }

    @Override
    public void writeDataToPipe(
            @NonNull ParcelFileDescriptor output,
            @NonNull Uri uri,
            @NonNull String mimeType,
            @Nullable Bundle opts,
            @Nullable String args) {
        try (FileOutputStream fout = new FileOutputStream(output.getFileDescriptor())) {
            try (PrintWriter pw =
                    new PrintWriter(new OutputStreamWriter(fout, StandardCharsets.UTF_8))) {
                pw.print(args);
            }
        } catch (IOException e) {
            Log.e(TAG, "Exception in writing data to pipe", e);
        }
    }

    @Override
    public boolean refresh(
            Uri uri, @Nullable Bundle args, @Nullable CancellationSignal cancellationSignal) {
        sRefreshedUri = uri;
        return sRefreshReturnValue;
    }

    @Override
    public int checkUriPermission(@NonNull Uri uri, int uid, @Intent.AccessUriMode int modeFlags) {
        if ((modeFlags & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0) {
            return PackageManager.PERMISSION_GRANTED;
        } else {
            return PackageManager.PERMISSION_DENIED;
        }
    }

    private void crashOnLaunchIfNeeded() {
        if (getCrashOnLaunch(getContext())) {
            // The test case wants us to crash our process on first launch.
            // Well, okay then!
            Log.i(TAG, "TEST IS CRASHING SELF, CROSS FINGERS!");
            setCrashOnLaunch(getContext(), false);
            Process.killProcess(Process.myPid());
        }
    }

    public static boolean getCrashOnLaunch(Context context) {
        File file = getCrashOnLaunchFile(context);
        return file.exists();
    }

    public static void setCrashOnLaunch(Context context, boolean value) {
        File file = getCrashOnLaunchFile(context);
        if (value) {
            try {
                file.createNewFile();
            } catch (IOException ex) {
                throw new RuntimeException("Could not create crash on launch file.", ex);
            }
        } else {
            file.delete();
        }
    }

    public static void setRefreshReturnValue(boolean value) {
        sRefreshReturnValue = value;
    }

    public static void assertRefreshed(Uri expectedUri) {
        assertThat(expectedUri).isEqualTo(sRefreshedUri);
    }

    private static File getCrashOnLaunchFile(Context context) {
        return context.getFileStreamPath("MockContentProvider.crashonlaunch");
    }
}
