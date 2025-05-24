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

package android.content.cts;

import static android.content.ContentResolver.NOTIFY_INSERT;
import static android.content.ContentResolver.NOTIFY_UPDATE;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;

import android.accounts.Account;
import android.annotation.NonNull;
import android.annotation.UserIdInt;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.ContentResolver.MimeTypeInfo;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.database.ContentObserver;
import android.database.Cursor;
import android.icu.text.Collator;
import android.icu.util.ULocale;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.OperationCanceledException;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.UserHandle;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.ShellIdentityUtils;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.util.ArrayUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
@RunWith(AndroidJUnit4.class)
public final class ContentResolverTest {
    private static final String TAG = "ContentResolverTest";
    private final static String COLUMN_ID_NAME = "_id";
    private final static String COLUMN_KEY_NAME = "key";
    private final static String COLUMN_VALUE_NAME = "value";

    private static final String AUTHORITY = "ctstest";
    private static final Uri TABLE1_URI =
            new Uri.Builder()
                    .scheme(ContentResolver.SCHEME_CONTENT)
                    .authority(AUTHORITY)
                    .appendPath("testtable1")
                    .build();
    private static final Uri TABLE1_CROSS_URI =
            new Uri.Builder()
                    .scheme(ContentResolver.SCHEME_CONTENT)
                    .authority(AUTHORITY)
                    .appendPath("testtable1")
                    .appendPath("cross")
                    .build();
    private static final Uri TABLE2_URI =
            new Uri.Builder()
                    .scheme(ContentResolver.SCHEME_CONTENT)
                    .authority(AUTHORITY)
                    .appendPath("testtable2")
                    .build();

    private static final Uri LEVEL1_URI =
            new Uri.Builder()
                    .scheme(ContentResolver.SCHEME_CONTENT)
                    .authority(AUTHORITY)
                    .appendPath("level")
                    .build();
    private static final Uri LEVEL2_URI =
            new Uri.Builder()
                    .scheme(ContentResolver.SCHEME_CONTENT)
                    .authority(AUTHORITY)
                    .appendPath("level")
                    .appendPath("child")
                    .build();
    private static final Uri LEVEL3_URI =
            new Uri.Builder()
                    .scheme(ContentResolver.SCHEME_CONTENT)
                    .authority(AUTHORITY)
                    .appendPath("level")
                    .appendPath("child")
                    .appendPath("grandchild")
                    .build();
    private static final String REMOTE_AUTHORITY = "remotectstest";
    private static final Uri REMOTE_TABLE1_URI =
            new Uri.Builder()
                    .scheme(ContentResolver.SCHEME_CONTENT)
                    .authority(REMOTE_AUTHORITY)
                    .appendPath("testtable1")
                    .build();
    private static final Uri REMOTE_CRASH_URI =
            new Uri.Builder()
                    .scheme(ContentResolver.SCHEME_CONTENT)
                    .authority(REMOTE_AUTHORITY)
                    .appendPath("crash")
                    .build();
    private static final Uri REMOTE_HANG_URI =
            new Uri.Builder()
                    .scheme(ContentResolver.SCHEME_CONTENT)
                    .authority(REMOTE_AUTHORITY)
                    .appendPath("hang")
                    .build();

    private static final String RESTRICTED_AUTHORITY = "restrictedctstest";
    private static final Uri RESTRICTED_TABLE1_URI =
            new Uri.Builder()
                    .scheme(ContentResolver.SCHEME_CONTENT)
                    .authority(RESTRICTED_AUTHORITY)
                    .appendPath("testtable1")
                    .build();

    private static final Uri RESTRICTED_TABLE1_ITEM_URI =
            new Uri.Builder()
                    .scheme(ContentResolver.SCHEME_CONTENT)
                    .authority(RESTRICTED_AUTHORITY)
                    .appendPath("testtable1")
                    .appendPath("1")
                    .build();

    private static final Uri INVALID_URI = Uri.parse("abc");
    private static final Account ACCOUNT = new Account("cts", "cts");

    private static final String KEY1 = "key1";
    private static final String KEY2 = "key2";
    private static final String KEY3 = "key3";
    private static final int VALUE1 = 1;
    private static final int VALUE2 = 2;
    private static final int VALUE3 = 3;

    private static final String TEST_PACKAGE_NAME = "android.content.cts";

    private Context mContext;
    private ContentResolver mContentResolver;
    private Cursor mCursor;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mContentResolver = mContext.getContentResolver();

        MockContentProvider.setCrashOnLaunch(mContext, false);

        // add three rows to database when every test case start.
        ContentValues values = new ContentValues();

        values.put(COLUMN_KEY_NAME, KEY1);
        values.put(COLUMN_VALUE_NAME, VALUE1);
        mContentResolver.insert(TABLE1_URI, values);
        mContentResolver.insert(REMOTE_TABLE1_URI, values);

        values.put(COLUMN_KEY_NAME, KEY2);
        values.put(COLUMN_VALUE_NAME, VALUE2);
        mContentResolver.insert(TABLE1_URI, values);
        mContentResolver.insert(REMOTE_TABLE1_URI, values);

        values.put(COLUMN_KEY_NAME, KEY3);
        values.put(COLUMN_VALUE_NAME, VALUE3);
        mContentResolver.insert(TABLE1_URI, values);
        mContentResolver.insert(REMOTE_TABLE1_URI, values);
    }

    @After
    public void tearDown() throws Exception {
        MockContentProvider.setRefreshReturnValue(false);
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .dropShellPermissionIdentity();

        mContentResolver.delete(TABLE1_URI, null, null);
        if (null != mCursor && !mCursor.isClosed()) {
            mCursor.close();
        }
        mContentResolver.delete(REMOTE_TABLE1_URI, null, null);
        if (null != mCursor && !mCursor.isClosed()) {
            mCursor.close();
        }
    }

    @Test
    public void testConstructor() {
        assertThat(mContentResolver).isNotNull();
    }

    @Test
    public void testCrashOnLaunch() {
        // This test is going to make sure that the platform deals correctly
        // with a content provider process going away while a client is waiting
        // for it to come up.
        // First, we need to make sure our provider process is gone.  Goodbye!
        ContentProviderClient client = mContentResolver.acquireContentProviderClient(
                REMOTE_AUTHORITY);
        // We are going to do something wrong here...  release the client first,
        // so the act of killing it doesn't kill our own process.
        client.close();
        try {
            client.delete(REMOTE_CRASH_URI, null, null);
        } catch (RemoteException e) {
            // Expected
        }
        // Now make sure the thing is actually gone.
        boolean gone = true;
        try {
            client.getType(REMOTE_TABLE1_URI);
            gone = false;
        } catch (RemoteException e) {
            // Expected
        }
        if (!gone) {
            assertWithMessage("Content provider process is not gone!").fail();
        }
        try {
            MockContentProvider.setCrashOnLaunch(mContext, true);
            String type1 = mContentResolver.getType(REMOTE_TABLE1_URI);
            assertThat(MockContentProvider.getCrashOnLaunch(mContext)).isFalse();
            assertThat(type1.startsWith(ContentResolver.CURSOR_DIR_BASE_TYPE)).isTrue();
        } finally {
            MockContentProvider.setCrashOnLaunch(mContext, false);
        }
    }

    @Test
    public void testUnstableToStableRefs() {
        // Get an unstable reference on the remote content provider.
        ContentProviderClient uClient =
                mContentResolver.acquireUnstableContentProviderClient(REMOTE_AUTHORITY);
        // Verify we can access it.
        String type1 = mContentResolver.getType(REMOTE_TABLE1_URI);
        assertThat(type1.startsWith(ContentResolver.CURSOR_DIR_BASE_TYPE)).isTrue();

        // Get a stable reference on the remote content provider.
        ContentProviderClient sClient = mContentResolver.acquireContentProviderClient(
                REMOTE_AUTHORITY);
        // Verify we can still access it.
        type1 = mContentResolver.getType(REMOTE_TABLE1_URI);
        assertThat(type1.startsWith(ContentResolver.CURSOR_DIR_BASE_TYPE)).isTrue();

        // Release unstable reference.
        uClient.close();
        // Verify we can still access it.
        type1 = mContentResolver.getType(REMOTE_TABLE1_URI);
        assertThat(type1.startsWith(ContentResolver.CURSOR_DIR_BASE_TYPE)).isTrue();

        // Release stable reference, removing last ref.
        sClient.close();
        // Kill it.  Note that a bug at this point where it causes our own
        // process to be killed will result in the entire test failing.
        try {
            Log.i("ContentResolverTest",
                    "Killing remote client -- if test process goes away, that is why!");
            uClient.delete(REMOTE_CRASH_URI, null, null);
        } catch (RemoteException e) {
            // Expected
        }
        // Make sure the remote client is actually gone.
        boolean gone = true;
        try {
            sClient.getType(REMOTE_TABLE1_URI);
            gone = false;
        } catch (RemoteException e) {
            // Expected
        }
        if (!gone) {
            assertWithMessage("Content provider process is not gone!").fail();
        }
    }

    @Test
    public void testStableToUnstableRefs() {
        // Get a stable reference on the remote content provider.
        ContentProviderClient sClient = mContentResolver.acquireContentProviderClient(
                REMOTE_AUTHORITY);
        // Verify we can still access it.
        String type1 = mContentResolver.getType(REMOTE_TABLE1_URI);
        assertThat(type1.startsWith(ContentResolver.CURSOR_DIR_BASE_TYPE)).isTrue();

        // Get an unstable reference on the remote content provider.
        ContentProviderClient uClient =
                mContentResolver.acquireUnstableContentProviderClient(REMOTE_AUTHORITY);
        // Verify we can access it.
        type1 = mContentResolver.getType(REMOTE_TABLE1_URI);
        assertThat(type1.startsWith(ContentResolver.CURSOR_DIR_BASE_TYPE)).isTrue();

        // Release stable reference, leaving only an unstable ref.
        sClient.close();

        // Kill it.  Note that a bug at this point where it causes our own
        // process to be killed will result in the entire test failing.
        try {
            Log.i("ContentResolverTest",
                    "Killing remote client -- if test process goes away, that is why!");
            uClient.delete(REMOTE_CRASH_URI, null, null);
        } catch (RemoteException e) {
            // Expected
        }
        // Make sure the remote client is actually gone.
        boolean gone = true;
        try {
            uClient.getType(REMOTE_TABLE1_URI);
            gone = false;
        } catch (RemoteException e) {
            // Expected
        }
        if (!gone) {
            assertWithMessage("Content provider process is not gone!").fail();
        }

        // Release unstable reference.
        uClient.close();
    }

    @Test
    public void testGetType() {
        String type1 = mContentResolver.getType(TABLE1_URI);
        assertThat(type1.startsWith(ContentResolver.CURSOR_DIR_BASE_TYPE)).isTrue();

        String type2 = mContentResolver.getType(TABLE2_URI);
        assertThat(type2.startsWith(ContentResolver.CURSOR_DIR_BASE_TYPE)).isTrue();

        assertThat(mContentResolver.getType(INVALID_URI)).isNull();

        assertThrows(
                "did not throw NullPointerException when Uri is null.",
                NullPointerException.class,
                () -> mContentResolver.getType(null));
    }

    @AppModeFull
    @Test
    public void testGetTypeAnonymous() {
        String type1 = mContentResolver.getType(RESTRICTED_TABLE1_URI);
        assertThat(type1.startsWith(ContentResolver.CURSOR_DIR_BASE_TYPE)).isTrue();
        String type2 = mContentResolver.getType(RESTRICTED_TABLE1_ITEM_URI);
        assertThat(type2).isNull();
    }

    @Test
    public void testUnstableGetType() {
        // Get an unstable reference on the remote content provider.
        try (ContentProviderClient client =
                mContentResolver.acquireUnstableContentProviderClient(REMOTE_AUTHORITY)) {
            // Verify we can access it.
            String type1 = mContentResolver.getType(REMOTE_TABLE1_URI);
            assertThat(type1.startsWith(ContentResolver.CURSOR_DIR_BASE_TYPE)).isTrue();

            // Kill it.  Note that a bug at this point where it causes our own
            // process to be killed will result in the entire test failing.
            try {
                Log.i(
                        "ContentResolverTest",
                        "Killing remote client -- if test process goes away, that is why!");
                client.delete(REMOTE_CRASH_URI, null, null);
            } catch (RemoteException e) {
                // Expected
            }
            // Make sure the remote client is actually gone.
            boolean gone = true;
            try {
                client.getType(REMOTE_TABLE1_URI);
                gone = false;
            } catch (RemoteException e) {
                // Expected
            }
            if (!gone) {
                assertWithMessage("Content provider process is not gone!").fail();
            }
        }

        // Get a new reference.
        try (ContentProviderClient unused =
                mContentResolver.acquireUnstableContentProviderClient(REMOTE_AUTHORITY)) {
            // Verify we can access it.
            String type1 = mContentResolver.getType(REMOTE_TABLE1_URI);
            assertThat(type1.startsWith(ContentResolver.CURSOR_DIR_BASE_TYPE)).isTrue();
        }
    }

    @Test
    public void testQuery() {
        mCursor = mContentResolver.query(TABLE1_URI, null, null, null);

        assertThat(mCursor).isNotNull();
        assertThat(mCursor.getCount()).isEqualTo(3);
        assertThat(mCursor.getColumnCount()).isEqualTo(3);

        mCursor.moveToLast();
        assertThat(mCursor.getInt(mCursor.getColumnIndexOrThrow(COLUMN_ID_NAME))).isEqualTo(3);
        assertThat(mCursor.getString(mCursor.getColumnIndexOrThrow(COLUMN_KEY_NAME)))
                .isEqualTo(KEY3);
        assertThat(mCursor.getInt(mCursor.getColumnIndexOrThrow(COLUMN_VALUE_NAME)))
                .isEqualTo(VALUE3);

        mCursor.moveToPrevious();
        assertThat(mCursor.getInt(mCursor.getColumnIndexOrThrow(COLUMN_ID_NAME))).isEqualTo(2);
        assertThat(mCursor.getString(mCursor.getColumnIndexOrThrow(COLUMN_KEY_NAME)))
                .isEqualTo(KEY2);
        assertThat(mCursor.getInt(mCursor.getColumnIndexOrThrow(COLUMN_VALUE_NAME)))
                .isEqualTo(VALUE2);
        mCursor.close();
    }

    @Test
    public void testQuery_WithSqlSelectionArgs() {
        Bundle queryArgs = new Bundle();
        queryArgs.putString(ContentResolver.QUERY_ARG_SQL_SELECTION, COLUMN_ID_NAME + "=?");
        queryArgs.putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, new String[] {"1"});

        mCursor = mContentResolver.query(TABLE1_URI, null, queryArgs, null);
        assertThat(mCursor).isNotNull();
        assertThat(mCursor.getCount()).isEqualTo(1);
        assertThat(mCursor.getColumnCount()).isEqualTo(3);

        mCursor.moveToFirst();
        assertThat(mCursor.getInt(mCursor.getColumnIndexOrThrow(COLUMN_ID_NAME))).isEqualTo(1);
        assertThat(mCursor.getString(mCursor.getColumnIndexOrThrow(COLUMN_KEY_NAME)))
                .isEqualTo(KEY1);
        assertThat(mCursor.getInt(mCursor.getColumnIndexOrThrow(COLUMN_VALUE_NAME)))
                .isEqualTo(VALUE1);
        mCursor.close();

        queryArgs.putString(ContentResolver.QUERY_ARG_SQL_SELECTION, COLUMN_KEY_NAME + "=?");
        queryArgs.putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, new String[] {KEY3});
        mCursor = mContentResolver.query(TABLE1_URI, null, queryArgs, null);
        assertThat(mCursor).isNotNull();
        assertThat(mCursor.getCount()).isEqualTo(1);
        assertThat(mCursor.getColumnCount()).isEqualTo(3);

        mCursor.moveToFirst();
        assertThat(mCursor.getInt(mCursor.getColumnIndexOrThrow(COLUMN_ID_NAME))).isEqualTo(3);
        assertThat(mCursor.getString(mCursor.getColumnIndexOrThrow(COLUMN_KEY_NAME)))
                .isEqualTo(KEY3);
        assertThat(mCursor.getInt(mCursor.getColumnIndexOrThrow(COLUMN_VALUE_NAME)))
                .isEqualTo(VALUE3);
        mCursor.close();
    }

    /*
     * NOTE: this test is implicitly coupled to the implementation
     * of MockContentProvider#query, specifically the facts:
     *
     * - it does *not* override the query w/ Bundle methods
     * - it receives the auto-generated sql format arguments (supplied by the framework)
     * - it is backed by sqlite and forwards the sql formatted args.
     */
    @Test
    public void testQuery_SqlSortingFromBundleArgs() {
        mContentResolver.delete(TABLE1_URI, null, null);
        ContentValues values = new ContentValues();

        values.put(COLUMN_KEY_NAME, "0");
        values.put(COLUMN_VALUE_NAME, "abc");
        mContentResolver.insert(TABLE1_URI, values);

        values.put(COLUMN_KEY_NAME, "1");
        values.put(COLUMN_VALUE_NAME, "DEF");
        mContentResolver.insert(TABLE1_URI, values);

        values.put(COLUMN_KEY_NAME, "2");
        values.put(COLUMN_VALUE_NAME, "ghi");
        mContentResolver.insert(TABLE1_URI, values);

        String[] sortCols = new String[] {COLUMN_VALUE_NAME};
        Bundle queryArgs = new Bundle();
        queryArgs.putStringArray(
                ContentResolver.QUERY_ARG_SORT_COLUMNS,
                sortCols);

        // Sort ascending...
        queryArgs.putInt(
                ContentResolver.QUERY_ARG_SORT_DIRECTION,
                ContentResolver.QUERY_SORT_DIRECTION_ASCENDING);

        mCursor = mContentResolver.query(TABLE1_URI, sortCols, queryArgs, null);
        int col = mCursor.getColumnIndexOrThrow(COLUMN_VALUE_NAME);

        mCursor.moveToNext();
        assertThat(mCursor.getString(col)).isEqualTo("DEF");
        mCursor.moveToNext();
        assertThat(mCursor.getString(col)).isEqualTo("abc");
        mCursor.moveToNext();
        assertThat(mCursor.getString(col)).isEqualTo("ghi");

        mCursor.close();

        // Nocase collation, descending...
        queryArgs.putInt(
                ContentResolver.QUERY_ARG_SORT_DIRECTION,
                ContentResolver.QUERY_SORT_DIRECTION_DESCENDING);
        queryArgs.putInt(
                ContentResolver.QUERY_ARG_SORT_COLLATION,
                java.text.Collator.SECONDARY);

        mCursor = mContentResolver.query(TABLE1_URI, null, queryArgs, null);
        col = mCursor.getColumnIndexOrThrow(COLUMN_VALUE_NAME);

        mCursor.moveToNext();
        assertThat(mCursor.getString(col)).isEqualTo("ghi");
        mCursor.moveToNext();
        assertThat(mCursor.getString(col)).isEqualTo("DEF");
        mCursor.moveToNext();
        assertThat(mCursor.getString(col)).isEqualTo("abc");

        mCursor.close();
    }

    @Test
    public void testQuery_SqlSortingFromBundleArgs_Locale() {
        mContentResolver.delete(TABLE1_URI, null, null);

        final List<String> data = Arrays.asList(
                "ABC", "abc", "pinyin", "가나다", "바사", "테스트", "马",
                "嘛", "妈", "骂", "吗", "码", "玛", "麻", "中", "梵", "苹果", "久了", "伺候");

        for (String s : data) {
            final ContentValues values = new ContentValues();
            values.put(COLUMN_KEY_NAME, s.hashCode());
            values.put(COLUMN_VALUE_NAME, s);
            mContentResolver.insert(TABLE1_URI, values);
        }

        String[] sortCols = new String[] {COLUMN_VALUE_NAME};
        Bundle queryArgs = new Bundle();
        queryArgs.putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, sortCols);

        for (String locale :
                new String[] {
                    "zh", "zh@collation=pinyin", "zh@collation=stroke", "zh@collation=zhuyin",
                }) {
            // Assert that sorting is identical between SQLite and ICU4J
            queryArgs.putString(ContentResolver.QUERY_ARG_SORT_LOCALE, locale);
            try (Cursor c = mContentResolver.query(TABLE1_URI, sortCols, queryArgs, null)) {
                data.sort(Collator.getInstance(new ULocale(locale)));
                assertThat(collect(c)).isEqualTo(data);
            }
        }
    }

    private static List<String> collect(Cursor c) {
        List<String> res = new ArrayList<>();
        while (c.moveToNext()) {
            res.add(c.getString(0));
        }
        return res;
    }

    /**
     * Verifies that paging information is correctly relayed, and that honored arguments from a
     * supporting client are returned correctly.
     */
    @Test
    public void testQuery_PagedResults() {
        Bundle queryArgs = new Bundle();
        queryArgs.putInt(ContentResolver.QUERY_ARG_OFFSET, 10);
        queryArgs.putInt(ContentResolver.QUERY_ARG_LIMIT, 3);
        queryArgs.putInt(TestPagingContentProvider.RECORD_COUNT, 100);

        mCursor = mContentResolver.query(
                TestPagingContentProvider.PAGED_DATA_URI, null, queryArgs, null);

        Bundle extras = mCursor.getExtras();
        extras = extras != null ? extras : Bundle.EMPTY;

        assertThat(mCursor.getCount()).isEqualTo(3);
        assertThat(extras.containsKey(ContentResolver.EXTRA_TOTAL_COUNT)).isTrue();
        assertThat(extras.getInt(ContentResolver.EXTRA_TOTAL_COUNT)).isEqualTo(100);

        String[] honoredArgs = extras.getStringArray(ContentResolver.EXTRA_HONORED_ARGS);
        assertThat(honoredArgs).isNotNull();
        assertThat(ArrayUtils.contains(honoredArgs, ContentResolver.QUERY_ARG_OFFSET)).isTrue();
        assertThat(ArrayUtils.contains(honoredArgs, ContentResolver.QUERY_ARG_LIMIT)).isTrue();

        int col = mCursor.getColumnIndexOrThrow(TestPagingContentProvider.COLUMN_POS);

        mCursor.moveToNext();
        assertThat(mCursor.getInt(col)).isEqualTo(10);
        mCursor.moveToNext();
        assertThat(mCursor.getInt(col)).isEqualTo(11);
        mCursor.moveToNext();
        assertThat(mCursor.getInt(col)).isEqualTo(12);

        assertThat(mCursor.moveToNext()).isFalse();

        mCursor.close();
    }

    @Test
    public void testQuery_NullUriThrows() {
        assertThrows(
                "did not throw NullPointerException when uri is null.",
                NullPointerException.class,
                () -> mContentResolver.query(null, null, null, null, null));
    }

    @Test
    public void testCrashingQuery() {
        try {
            MockContentProvider.setCrashOnLaunch(mContext, true);
            mCursor = mContentResolver.query(REMOTE_CRASH_URI, null, null, null, null);
            assertThat(MockContentProvider.getCrashOnLaunch(mContext)).isFalse();
        } finally {
            MockContentProvider.setCrashOnLaunch(mContext, false);
        }

        assertThat(mCursor).isNotNull();
        assertThat(mCursor.getCount()).isEqualTo(3);
        assertThat(mCursor.getColumnCount()).isEqualTo(3);

        mCursor.moveToLast();
        assertThat(mCursor.getInt(mCursor.getColumnIndexOrThrow(COLUMN_ID_NAME))).isEqualTo(3);
        assertThat(mCursor.getString(mCursor.getColumnIndexOrThrow(COLUMN_KEY_NAME)))
                .isEqualTo(KEY3);
        assertThat(mCursor.getInt(mCursor.getColumnIndexOrThrow(COLUMN_VALUE_NAME)))
                .isEqualTo(VALUE3);

        mCursor.moveToPrevious();
        assertThat(mCursor.getInt(mCursor.getColumnIndexOrThrow(COLUMN_ID_NAME))).isEqualTo(2);
        assertThat(mCursor.getString(mCursor.getColumnIndexOrThrow(COLUMN_KEY_NAME)))
                .isEqualTo(KEY2);
        assertThat(mCursor.getInt(mCursor.getColumnIndexOrThrow(COLUMN_VALUE_NAME)))
                .isEqualTo(VALUE2);
        mCursor.close();
    }

    @Test
    public void testCancelableQuery_WhenNotCanceled_ReturnsResultSet() {
        CancellationSignal cancellationSignal = new CancellationSignal();

        Cursor cursor = mContentResolver.query(TABLE1_URI, null, null, null, null,
                cancellationSignal);
        assertThat(cursor.getCount()).isEqualTo(3);
        cursor.close();
    }

    @Test
    public void testCancelableQuery_WhenCanceledBeforeQuery_ThrowsImmediately() {
        CancellationSignal cancellationSignal = new CancellationSignal();
        cancellationSignal.cancel();

        assertThrows(
                "Expected OperationCanceledException",
                OperationCanceledException.class,
                () ->
                        mContentResolver.query(
                                TABLE1_URI, null, null, null, null, cancellationSignal));
    }

    @Test
    public void testCancelableQuery_WhenCanceledDuringLongRunningQuery_CancelsQueryAndThrows() {
        // Populate a table with a bunch of integers.
        mContentResolver.delete(TABLE1_URI, null, null);
        ContentValues values = new ContentValues();
        for (int i = 0; i < 100; i++) {
            values.put(COLUMN_KEY_NAME, i);
            values.put(COLUMN_VALUE_NAME, i);
            mContentResolver.insert(TABLE1_URI, values);
        }

        for (int i = 0; i < 5; i++) {
            final CancellationSignal cancellationSignal = new CancellationSignal();
            Thread cancellationThread =
                    new Thread(
                            () -> {
                                SystemClock.sleep(300);
                                cancellationSignal.cancel();
                            });
            try {
                // Build an unsatisfiable 5-way cross-product query over 100 values but
                // produces no output.  This should force SQLite to loop for a long time
                // as it tests 10^10 combinations.
                cancellationThread.start();

                final long startTime = System.nanoTime();
                assertThrows(
                        "Expected OperationCanceledException",
                        OperationCanceledException.class,
                        () ->
                                mContentResolver.query(
                                        TABLE1_CROSS_URI,
                                        null,
                                        "a.value + b.value + c.value + d.value + e.value > 1000000",
                                        null,
                                        null,
                                        cancellationSignal));
                // We want to confirm that the query really was running and then got
                // canceled midway.
                final long waitTime = System.nanoTime() - startTime;
                if (waitTime > 150 * 1000000L && waitTime < 600 * 1000000L) {
                    return; // success!
                }
            } finally {
                try {
                    cancellationThread.join();
                } catch (InterruptedException e) {
                    // Expected
                }
            }
        }

        // Occasionally we might miss the timing deadline due to factors in the
        // environment, but if after several trials we still couldn't demonstrate
        // that the query was canceled, then the test must be broken.
        assertWithMessage(
                        "Could not prove that the query actually canceled midway during execution.")
                .fail();
    }

    @Test
    public void testOpenInputStream() throws IOException {
        final Uri uri =
                new Uri.Builder()
                        .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
                        .authority(TEST_PACKAGE_NAME)
                        .appendPath(String.valueOf(R.drawable.pass))
                        .build();
        InputStream is = mContentResolver.openInputStream(uri);
        assertThat(is).isNotNull();
        is.close();

        final Uri invalidUri = Uri.parse("abc");

        assertThrows(
                "did not throw FileNotFoundException when uri is invalid.",
                FileNotFoundException.class,
                () -> mContentResolver.openInputStream(invalidUri));
    }

    @Test
    public void testOpenOutputStream() throws IOException {
        Uri uri =
                new Uri.Builder()
                        .scheme(ContentResolver.SCHEME_FILE)
                        .appendPath(mContext.getCacheDir().getAbsolutePath())
                        .appendPath("temp.jpg")
                        .build();

        OutputStream os = mContentResolver.openOutputStream(uri);
        assertThat(os).isNotNull();
        os.close();

        os = mContentResolver.openOutputStream(uri, "wa");
        assertThat(os).isNotNull();
        os.close();

        Uri badSchemeUri =
                new Uri.Builder()
                        .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
                        .authority(TEST_PACKAGE_NAME)
                        .appendPath(String.valueOf(R.raw.testimage))
                        .build();

        assertThrows(
                "did not throw FileNotFoundException when scheme is not accepted.",
                FileNotFoundException.class,
                () -> mContentResolver.openOutputStream(badSchemeUri));

        assertThrows(
                "did not throw FileNotFoundException when scheme is not accepted.",
                FileNotFoundException.class,
                () -> mContentResolver.openOutputStream(badSchemeUri, "w"));

        Uri invalidUri = Uri.parse("abc");

        assertThrows(
                "did not throw FileNotFoundException when uri is invalid.",
                FileNotFoundException.class,
                () -> mContentResolver.openOutputStream(invalidUri));

        assertThrows(
                "did not throw FileNotFoundException when uri is invalid.",
                FileNotFoundException.class,
                () -> mContentResolver.openOutputStream(invalidUri, "w"));
    }

    @Test
    public void testOpenAssetFileDescriptor() throws IOException {
        Uri uri =
                new Uri.Builder()
                        .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
                        .authority(TEST_PACKAGE_NAME)
                        .appendPath(String.valueOf(R.raw.testimage))
                        .build();

        AssetFileDescriptor afd = mContentResolver.openAssetFileDescriptor(uri, "r");
        assertThat(afd).isNotNull();
        afd.close();

        assertThrows(
                "did not throw FileNotFoundException when mode is unknown.",
                FileNotFoundException.class,
                () -> mContentResolver.openAssetFileDescriptor(uri, "d"));

        Uri invalidUri = Uri.parse("abc");
        assertThrows(
                "did not throw FileNotFoundException when uri is invalid.",
                FileNotFoundException.class,
                () -> mContentResolver.openAssetFileDescriptor(invalidUri, "r"));
    }

    private String consumeAssetFileDescriptor(AssetFileDescriptor afd)
            throws IOException {
        try (FileInputStream stream = afd.createInputStream()) {
            InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8);

            // Got it...  copy the stream into a local string and return it.
            StringBuilder builder = new StringBuilder(128);
            char[] buffer = new char[8192];
            int len;
            while ((len = reader.read(buffer)) > 0) {
                builder.append(buffer, 0, len);
            }
            return builder.toString();
        }
    }

    @Test
    public void testCrashingOpenAssetFileDescriptor() throws IOException {
        AssetFileDescriptor afd = null;
        try {
            MockContentProvider.setCrashOnLaunch(mContext, true);
            afd = mContentResolver.openAssetFileDescriptor(REMOTE_CRASH_URI, "rw");
            assertThat(MockContentProvider.getCrashOnLaunch(mContext)).isFalse();
            assertThat(afd).isNotNull();
            String str = consumeAssetFileDescriptor(afd);
            afd = null;
            assertThat(str).isEqualTo("This is the openAssetFile test data!");
        } finally {
            MockContentProvider.setCrashOnLaunch(mContext, false);
            if (afd != null) {
                afd.close();
            }
        }

        // Make sure a content provider crash at this point won't hurt us.
        ContentProviderClient uClient = mContentResolver.acquireUnstableContentProviderClient(
                REMOTE_AUTHORITY);
        // Kill it.  Note that a bug at this point where it causes our own
        // process to be killed will result in the entire test failing.
        try {
            Log.i("ContentResolverTest",
                    "Killing remote client -- if test process goes away, that is why!");
            uClient.delete(REMOTE_CRASH_URI, null, null);
        } catch (RemoteException e) {
            // Expected
        }
        uClient.close();
    }

    @Test
    public void testCrashingOpenTypedAssetFileDescriptor() throws IOException {
        AssetFileDescriptor afd = null;
        try {
            MockContentProvider.setCrashOnLaunch(mContext, true);
            afd = mContentResolver.openTypedAssetFileDescriptor(
                    REMOTE_CRASH_URI, "text/plain", null);
            assertThat(MockContentProvider.getCrashOnLaunch(mContext)).isFalse();
            assertThat(afd).isNotNull();
            String str = consumeAssetFileDescriptor(afd);
            afd = null;
            assertThat(str).isEqualTo("This is the openTypedAssetFile test data!");
        } finally {
            MockContentProvider.setCrashOnLaunch(mContext, false);
            if (afd != null) {
                afd.close();
            }
        }

        // Make sure a content provider crash at this point won't hurt us.
        ContentProviderClient uClient = mContentResolver.acquireUnstableContentProviderClient(
                REMOTE_AUTHORITY);
        // Kill it.  Note that a bug at this point where it causes our own
        // process to be killed will result in the entire test failing.
        try {
            Log.i("ContentResolverTest",
                    "Killing remote client -- if test process goes away, that is why!");
            uClient.delete(REMOTE_CRASH_URI, null, null);
        } catch (RemoteException e) {
            // Expected
        }
        uClient.close();
    }

    @Test
    public void testOpenFileDescriptor() throws IOException {
        Uri uri =
                new Uri.Builder()
                        .scheme(ContentResolver.SCHEME_FILE)
                        .appendPath(mContext.getCacheDir().getAbsolutePath())
                        .appendPath("temp.jpg")
                        .build();

        ParcelFileDescriptor pfd = mContentResolver.openFileDescriptor(uri, "w");
        assertThat(pfd).isNotNull();
        pfd.close();

        assertThrows(
                "did not throw IllegalArgumentException when mode is unknown.",
                IllegalArgumentException.class,
                () -> mContentResolver.openFileDescriptor(uri, "d"));

        Uri invalidUri = Uri.parse("abc");

        assertThrows(
                "did not throw FileNotFoundException when uri is invalid.",
                FileNotFoundException.class,
                () -> mContentResolver.openFileDescriptor(invalidUri, "w"));

        Uri unknownUri =
                new Uri.Builder()
                        .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
                        .authority(TEST_PACKAGE_NAME)
                        .appendPath(String.valueOf(R.raw.testimage))
                        .build();

        assertThrows(
                "did not throw FileNotFoundException when scheme is not accepted.",
                FileNotFoundException.class,
                () -> mContentResolver.openFileDescriptor(unknownUri, "w"));
    }

    @Test
    public void testInsert() {
        String key4 = "key4";
        String key5 = "key5";
        int value4 = 4;
        int value5 = 5;
        String key4Selection = COLUMN_KEY_NAME + "=\"" + key4 + "\"";

        mCursor = mContentResolver.query(TABLE1_URI, null, key4Selection, null, null);
        assertThat(mCursor.getCount()).isEqualTo(0);
        mCursor.close();

        ContentValues values = new ContentValues();
        values.put(COLUMN_KEY_NAME, key4);
        values.put(COLUMN_VALUE_NAME, value4);
        Uri uri = mContentResolver.insert(TABLE1_URI, values);
        assertThat(uri).isNotNull();

        mCursor = mContentResolver.query(TABLE1_URI, null, key4Selection, null, null);
        assertThat(mCursor).isNotNull();
        assertThat(mCursor.getCount()).isEqualTo(1);

        mCursor.moveToFirst();
        assertThat(mCursor.getInt(mCursor.getColumnIndexOrThrow(COLUMN_ID_NAME))).isEqualTo(4);
        assertThat(mCursor.getString(mCursor.getColumnIndexOrThrow(COLUMN_KEY_NAME)))
                .isEqualTo(key4);
        assertThat(mCursor.getInt(mCursor.getColumnIndexOrThrow(COLUMN_VALUE_NAME)))
                .isEqualTo(value4);
        mCursor.close();

        values.put(COLUMN_KEY_NAME, key5);
        values.put(COLUMN_VALUE_NAME, value5);
        uri = mContentResolver.insert(TABLE1_URI, values);
        assertThat(uri).isNotNull();

        // check returned uri
        mCursor = mContentResolver.query(uri, null, null, null, null);
        assertThat(mCursor).isNotNull();
        assertThat(mCursor.getCount()).isEqualTo(1);

        mCursor.moveToLast();
        assertThat(mCursor.getInt(mCursor.getColumnIndexOrThrow(COLUMN_ID_NAME))).isEqualTo(5);
        assertThat(mCursor.getString(mCursor.getColumnIndexOrThrow(COLUMN_KEY_NAME)))
                .isEqualTo(key5);
        assertThat(mCursor.getInt(mCursor.getColumnIndexOrThrow(COLUMN_VALUE_NAME)))
                .isEqualTo(value5);
        mCursor.close();

        assertThrows(
                "did not throw NullPointerException when uri is null.",
                NullPointerException.class,
                () -> mContentResolver.insert(null, values));
    }

    @Test
    public void testBulkInsert() {
        String key4 = "key4";
        String key5 = "key5";
        int value4 = 4;
        int value5 = 5;

        mCursor = mContentResolver.query(TABLE1_URI, null, null, null, null);
        assertThat(mCursor).isNotNull();
        assertThat(mCursor.getCount()).isEqualTo(3);
        mCursor.close();

        ContentValues[] cvs = new ContentValues[2];
        cvs[0] = new ContentValues();
        cvs[0].put(COLUMN_KEY_NAME, key4);
        cvs[0].put(COLUMN_VALUE_NAME, value4);

        cvs[1] = new ContentValues();
        cvs[1].put(COLUMN_KEY_NAME, key5);
        cvs[1].put(COLUMN_VALUE_NAME, value5);

        assertThat(mContentResolver.bulkInsert(TABLE1_URI, cvs)).isEqualTo(2);
        mCursor = mContentResolver.query(TABLE1_URI, null, null, null, null);
        assertThat(mCursor).isNotNull();
        assertThat(mCursor.getCount()).isEqualTo(5);

        mCursor.moveToLast();
        assertThat(mCursor.getInt(mCursor.getColumnIndexOrThrow(COLUMN_ID_NAME))).isEqualTo(5);
        assertThat(mCursor.getString(mCursor.getColumnIndexOrThrow(COLUMN_KEY_NAME)))
                .isEqualTo(key5);
        assertThat(mCursor.getInt(mCursor.getColumnIndexOrThrow(COLUMN_VALUE_NAME)))
                .isEqualTo(value5);

        mCursor.moveToPrevious();
        assertThat(mCursor.getInt(mCursor.getColumnIndexOrThrow(COLUMN_ID_NAME))).isEqualTo(4);
        assertThat(mCursor.getString(mCursor.getColumnIndexOrThrow(COLUMN_KEY_NAME)))
                .isEqualTo(key4);
        assertThat(mCursor.getInt(mCursor.getColumnIndexOrThrow(COLUMN_VALUE_NAME)))
                .isEqualTo(value4);
        mCursor.close();

        assertThrows(
                "did not throw NullPointerException when uri is null.",
                NullPointerException.class,
                () -> mContentResolver.bulkInsert(null, cvs));
    }

    @Test
    public void testDelete() {
        mCursor = mContentResolver.query(TABLE1_URI, null, null, null, null);
        assertThat(mCursor).isNotNull();
        assertThat(mCursor.getCount()).isEqualTo(3);
        mCursor.close();

        assertThat(mContentResolver.delete(TABLE1_URI, null, null)).isEqualTo(3);
        mCursor = mContentResolver.query(TABLE1_URI, null, null, null, null);
        assertThat(mCursor).isNotNull();
        assertThat(mCursor.getCount()).isEqualTo(0);
        mCursor.close();

        // add three rows to database.
        ContentValues values = new ContentValues();
        values.put(COLUMN_KEY_NAME, KEY1);
        values.put(COLUMN_VALUE_NAME, VALUE1);
        mContentResolver.insert(TABLE1_URI, values);

        values.put(COLUMN_KEY_NAME, KEY2);
        values.put(COLUMN_VALUE_NAME, VALUE2);
        mContentResolver.insert(TABLE1_URI, values);

        values.put(COLUMN_KEY_NAME, KEY3);
        values.put(COLUMN_VALUE_NAME, VALUE3);
        mContentResolver.insert(TABLE1_URI, values);

        // test delete row using selection
        String selection = COLUMN_ID_NAME + "=2";
        assertThat(mContentResolver.delete(TABLE1_URI, selection, null)).isEqualTo(1);

        mCursor = mContentResolver.query(TABLE1_URI, null, null, null, null);
        assertThat(mCursor).isNotNull();
        assertThat(mCursor.getCount()).isEqualTo(2);

        mCursor.moveToFirst();
        assertThat(mCursor.getInt(mCursor.getColumnIndexOrThrow(COLUMN_ID_NAME))).isEqualTo(1);
        assertThat(mCursor.getString(mCursor.getColumnIndexOrThrow(COLUMN_KEY_NAME)))
                .isEqualTo(KEY1);
        assertThat(mCursor.getInt(mCursor.getColumnIndexOrThrow(COLUMN_VALUE_NAME)))
                .isEqualTo(VALUE1);

        mCursor.moveToNext();
        assertThat(mCursor.getInt(mCursor.getColumnIndexOrThrow(COLUMN_ID_NAME))).isEqualTo(3);
        assertThat(mCursor.getString(mCursor.getColumnIndexOrThrow(COLUMN_KEY_NAME)))
                .isEqualTo(KEY3);
        assertThat(mCursor.getInt(mCursor.getColumnIndexOrThrow(COLUMN_VALUE_NAME)))
                .isEqualTo(VALUE3);
        mCursor.close();

        selection = COLUMN_VALUE_NAME + "=3";
        assertThat(mContentResolver.delete(TABLE1_URI, selection, null)).isEqualTo(1);

        mCursor = mContentResolver.query(TABLE1_URI, null, null, null, null);
        assertThat(mCursor).isNotNull();
        assertThat(mCursor.getCount()).isEqualTo(1);

        mCursor.moveToFirst();
        assertThat(mCursor.getInt(mCursor.getColumnIndexOrThrow(COLUMN_ID_NAME))).isEqualTo(1);
        assertThat(mCursor.getString(mCursor.getColumnIndexOrThrow(COLUMN_KEY_NAME)))
                .isEqualTo(KEY1);
        assertThat(mCursor.getInt(mCursor.getColumnIndexOrThrow(COLUMN_VALUE_NAME)))
                .isEqualTo(VALUE1);
        mCursor.close();

        selection = COLUMN_KEY_NAME + "=\"" + KEY1 + "\"";
        assertThat(mContentResolver.delete(TABLE1_URI, selection, null)).isEqualTo(1);

        mCursor = mContentResolver.query(TABLE1_URI, null, null, null, null);
        assertThat(mCursor).isNotNull();
        assertThat(mCursor.getCount()).isEqualTo(0);
        mCursor.close();

        assertThrows(
                "did not throw NullPointerException when uri is null.",
                NullPointerException.class,
                () -> mContentResolver.delete(null, null, null));
    }

    @Test
    public void testUpdate() {
        ContentValues values = new ContentValues();
        String key10 = "key10";
        String key20 = "key20";
        int value10 = 10;
        int value20 = 20;

        values.put(COLUMN_KEY_NAME, key10);
        values.put(COLUMN_VALUE_NAME, value10);

        // test update all the rows.
        assertThat(mContentResolver.update(TABLE1_URI, values, null, null)).isEqualTo(3);
        mCursor = mContentResolver.query(TABLE1_URI, null, null, null, null);
        assertThat(mCursor).isNotNull();
        assertThat(mCursor.getCount()).isEqualTo(3);

        mCursor.moveToFirst();
        assertThat(mCursor.getInt(mCursor.getColumnIndexOrThrow(COLUMN_ID_NAME))).isEqualTo(1);
        assertThat(mCursor.getString(mCursor.getColumnIndexOrThrow(COLUMN_KEY_NAME)))
                .isEqualTo(key10);
        assertThat(mCursor.getInt(mCursor.getColumnIndexOrThrow(COLUMN_VALUE_NAME)))
                .isEqualTo(value10);

        mCursor.moveToNext();
        assertThat(mCursor.getInt(mCursor.getColumnIndexOrThrow(COLUMN_ID_NAME))).isEqualTo(2);
        assertThat(mCursor.getString(mCursor.getColumnIndexOrThrow(COLUMN_KEY_NAME)))
                .isEqualTo(key10);
        assertThat(mCursor.getInt(mCursor.getColumnIndexOrThrow(COLUMN_VALUE_NAME)))
                .isEqualTo(value10);

        mCursor.moveToLast();
        assertThat(mCursor.getInt(mCursor.getColumnIndexOrThrow(COLUMN_ID_NAME))).isEqualTo(3);
        assertThat(mCursor.getString(mCursor.getColumnIndexOrThrow(COLUMN_KEY_NAME)))
                .isEqualTo(key10);
        assertThat(mCursor.getInt(mCursor.getColumnIndexOrThrow(COLUMN_VALUE_NAME)))
                .isEqualTo(value10);
        mCursor.close();

        // test update one row using selection.
        String selection = COLUMN_ID_NAME + "=1";
        values.put(COLUMN_KEY_NAME, key20);
        values.put(COLUMN_VALUE_NAME, value20);

        assertThat(mContentResolver.update(TABLE1_URI, values, selection, null)).isEqualTo(1);
        mCursor = mContentResolver.query(TABLE1_URI, null, null, null, null);
        assertThat(mCursor).isNotNull();
        assertThat(mCursor.getCount()).isEqualTo(3);

        mCursor.moveToFirst();
        assertThat(mCursor.getInt(mCursor.getColumnIndexOrThrow(COLUMN_ID_NAME))).isEqualTo(1);
        assertThat(mCursor.getString(mCursor.getColumnIndexOrThrow(COLUMN_KEY_NAME)))
                .isEqualTo(key20);
        assertThat(mCursor.getInt(mCursor.getColumnIndexOrThrow(COLUMN_VALUE_NAME)))
                .isEqualTo(value20);

        mCursor.moveToNext();
        assertThat(mCursor.getInt(mCursor.getColumnIndexOrThrow(COLUMN_ID_NAME))).isEqualTo(2);
        assertThat(mCursor.getString(mCursor.getColumnIndexOrThrow(COLUMN_KEY_NAME)))
                .isEqualTo(key10);
        assertThat(mCursor.getInt(mCursor.getColumnIndexOrThrow(COLUMN_VALUE_NAME)))
                .isEqualTo(value10);

        mCursor.moveToLast();
        assertThat(mCursor.getInt(mCursor.getColumnIndexOrThrow(COLUMN_ID_NAME))).isEqualTo(3);
        assertThat(mCursor.getString(mCursor.getColumnIndexOrThrow(COLUMN_KEY_NAME)))
                .isEqualTo(key10);
        assertThat(mCursor.getInt(mCursor.getColumnIndexOrThrow(COLUMN_VALUE_NAME)))
                .isEqualTo(value10);
        mCursor.close();

        assertThrows(
                "did not throw NullPointerException when uri is null.",
                NullPointerException.class,
                () -> mContentResolver.update(null, values, null, null));

        try {
            mContentResolver.update(TABLE1_URI, null, null, null);
            assertWithMessage("did not throw required exception when values are null.").fail();
        } catch (Exception e) {
            // If this test is running in an SDK sandbox instead of a regular app, the
            // ContentProvider runs in the app process. When the SDK sandbox interacts with the
            // ContentProvider, and values are null, an NPE is thrown in ContentProviderNative. In
            // apps however, since there is no IPC, this does not happen and an
            // IllegalArgumentException is thrown instead when values are null.
            Class<?> expectedErrorType =
                    Process.isSdkSandbox()
                            ? NullPointerException.class
                            : IllegalArgumentException.class;
            assertThat(e.getClass()).isEqualTo(expectedErrorType);
        }
    }

    @Test
    public void testRefresh_DefaultImplReturnsFalse() {
        boolean refreshed = mContentResolver.refresh(TABLE1_URI, null, null);
        assertThat(refreshed).isFalse();
        MockContentProvider.assertRefreshed(TABLE1_URI);
    }

    @Test
    public void testRefresh_ReturnsProviderValue() {
        MockContentProvider.setRefreshReturnValue(true);
        boolean refreshed = mContentResolver.refresh(TABLE1_URI, null, null);
        assertThat(refreshed).isTrue();
        MockContentProvider.assertRefreshed(TABLE1_URI);
    }

    @Test
    public void testRefresh_NullUriThrowsImmediately() {
        assertThrows(
                "did not throw NullPointerException when uri is null.",
                NullPointerException.class,
                () -> mContentResolver.refresh(null, null, null));
    }

    public void testRefresh_CancellableThrowsImmediately() {
        CancellationSignal cancellationSignal = new CancellationSignal();
        cancellationSignal.cancel();

        assertThrows(
                "Expected OperationCanceledException",
                OperationCanceledException.class,
                () -> mContentResolver.refresh(TABLE1_URI, null, cancellationSignal));
    }

    @Test
    public void testCheckUriPermission() {
        assertThat(
                        mContentResolver.checkUriPermission(
                                TABLE1_URI,
                                android.os.Process.myUid(),
                                Intent.FLAG_GRANT_READ_URI_PERMISSION))
                .isEqualTo(PackageManager.PERMISSION_GRANTED);
        assertThat(
                        mContentResolver.checkUriPermission(
                                TABLE1_URI,
                                android.os.Process.myUid(),
                                Intent.FLAG_GRANT_WRITE_URI_PERMISSION))
                .isEqualTo(PackageManager.PERMISSION_DENIED);
    }

    @Test
    public void testRegisterContentObserver() {
        final MockContentObserver mco = new MockContentObserver();

        mContentResolver.registerContentObserver(TABLE1_URI, true, mco);
        assertThat(mco.hadOnChanged()).isFalse();

        ContentValues values = new ContentValues();
        values.put(COLUMN_KEY_NAME, "key10");
        values.put(COLUMN_VALUE_NAME, 10);
        mContentResolver.update(TABLE1_URI, values, null, null);
        new PollingCheck() {
            @Override
            protected boolean check() {
                return mco.hadOnChanged();
            }
        }.run();

        mco.reset();
        mContentResolver.unregisterContentObserver(mco);
        assertThat(mco.hadOnChanged()).isFalse();
        mContentResolver.update(TABLE1_URI, values, null, null);

        assertThat(mco.hadOnChanged()).isFalse();

        try {
            mContentResolver.registerContentObserver(null, false, mco);
            assertWithMessage(
                            "did not throw NullPointerException or IllegalArgumentException when"
                                    + " uri is null.")
                    .fail();
        } catch (NullPointerException e) {
            //expected.
        } catch (IllegalArgumentException e) {
            // also expected
        }

        assertThrows(
                "did not throw NullPointerException when register null content observer.",
                NullPointerException.class,
                () -> mContentResolver.registerContentObserver(TABLE1_URI, false, null));

        assertThrows(
                "did not throw NullPointerException when unregister null content observer.",
                NullPointerException.class,
                () -> mContentResolver.unregisterContentObserver(null));
    }

    // Tests registerContentObserverForAllUsers without INTERACT_ACROSS_USERS_FULL: verify
    // SecurityException.
    @Test
    public void testRegisterContentObserverForAllUsersWithoutPermission() {
        final MockContentObserver mco = new MockContentObserver();
        assertThrows(
                "testRegisterContentObserverForAllUsers: "
                        + "SecurityException expected on testRegisterContentObserverForAllUsers",
                SecurityException.class,
                () ->
                        mContentResolver.registerContentObserverAsUser(
                                TABLE1_URI, true, mco, UserHandle.ALL));
    }

    @Test
    public void testRegisterContentObserverAsUser() {
        final MockContentObserver mco = new MockContentObserver();

        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                mContentResolver,
                (cr) -> cr.registerContentObserverAsUser(TABLE1_URI, true, mco, mContext.getUser())
        );
        assertThat(mco.hadOnChanged()).isFalse();

        ContentValues values = new ContentValues();
        values.put(COLUMN_KEY_NAME, "key10");
        values.put(COLUMN_VALUE_NAME, 10);
        mContentResolver.update(TABLE1_URI, values, null, null);
        new PollingCheck() {
            @Override
            protected boolean check() {
                return mco.hadOnChanged();
            }
        }.run();

        mco.reset();
        mContentResolver.unregisterContentObserver(mco);
        assertThat(mco.hadOnChanged()).isFalse();
        mContentResolver.update(TABLE1_URI, values, null, null);

        assertThat(mco.hadOnChanged()).isFalse();
    }

    @Test
    public void testRegisterContentObserverForAllUsers() {
        final MockContentObserver mco = new MockContentObserver();

        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                mContentResolver,
                (cr) -> cr.registerContentObserverAsUser(TABLE1_URI, true, mco, UserHandle.ALL)
        );
        assertThat(mco.hadOnChanged()).isFalse();

        ContentValues values = new ContentValues();
        values.put(COLUMN_KEY_NAME, "key10");
        values.put(COLUMN_VALUE_NAME, 10);
        mContentResolver.update(TABLE1_URI, values, null, null);
        new PollingCheck() {
            @Override
            protected boolean check() {
                return mco.hadOnChanged();
            }
        }.run();

        mco.reset();
        mContentResolver.unregisterContentObserver(mco);
        assertThat(mco.hadOnChanged()).isFalse();
        mContentResolver.update(TABLE1_URI, values, null, null);

        assertThat(mco.hadOnChanged()).isFalse();

        try {
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                    mContentResolver,
                    (cr) -> cr.registerContentObserverAsUser(null, false, mco, UserHandle.ALL)
            );
            assertWithMessage(
                            "did not throw NullPointerException or IllegalArgumentException when"
                                    + " uri is null.")
                    .fail();
        } catch (NullPointerException e) {
            //expected.
        } catch (IllegalArgumentException e) {
            // also expected
        }

        assertThrows(
                "did not throw NullPointerException when register null content observer.",
                NullPointerException.class,
                () ->
                        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                                mContentResolver,
                                (cr) ->
                                        cr.registerContentObserverAsUser(
                                                TABLE1_URI, false, null, UserHandle.ALL)));
        assertThrows(
                "did not throw NullPointerException when unregister null content observer.",
                NullPointerException.class,
                () -> mContentResolver.unregisterContentObserver(null));
    }

    @Test
    public void testRegisterContentObserverDescendantBehavior() throws Exception {
        final MockContentObserver mco1 = new MockContentObserver();
        final MockContentObserver mco2 = new MockContentObserver();

        // Register one content observer with notifyDescendants set to false, and
        // another with true.
        mContentResolver.registerContentObserver(LEVEL2_URI, false, mco1);
        mContentResolver.registerContentObserver(LEVEL2_URI, true, mco2);

        // Initially nothing has happened.
        assertThat(mco1.hadOnChanged()).isFalse();
        assertThat(mco2.hadOnChanged()).isFalse();

        // Fire a change with the exact URI.
        // Should signal both observers due to exact match, notifyDescendants doesn't matter.
        mContentResolver.notifyChange(LEVEL2_URI, null);
        Thread.sleep(200);
        assertThat(mco1.hadOnChanged()).isTrue();
        assertThat(mco2.hadOnChanged()).isTrue();
        mco1.reset();
        mco2.reset();

        // Fire a change with a descendant URI.
        // Should only signal observer with notifyDescendants set to true.
        mContentResolver.notifyChange(LEVEL3_URI, null);
        Thread.sleep(200);
        assertThat(mco1.hadOnChanged()).isFalse();
        assertThat(mco2.hadOnChanged()).isTrue();
        mco2.reset();

        // Fire a change with an ancestor URI.
        // Should signal both observers due to ancestry, notifyDescendants doesn't matter.
        mContentResolver.notifyChange(LEVEL1_URI, null);
        Thread.sleep(200);
        assertThat(mco1.hadOnChanged()).isTrue();
        assertThat(mco2.hadOnChanged()).isTrue();
        mco1.reset();
        mco2.reset();

        // Fire a change with an unrelated URI.
        // Should signal neither observer.
        mContentResolver.notifyChange(TABLE1_URI, null);
        Thread.sleep(200);
        assertThat(mco1.hadOnChanged()).isFalse();
        assertThat(mco2.hadOnChanged()).isFalse();
    }

    @Test
    public void testRegisterContentObserverForAllUsersDescendantBehavior() throws Exception {
        final MockContentObserver mco1 = new MockContentObserver();
        final MockContentObserver mco2 = new MockContentObserver();

        // Register one content observer with notifyDescendants set to false, and
        // another with true.
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                mContentResolver,
                (cr) -> cr.registerContentObserverAsUser(LEVEL2_URI, false, mco1, UserHandle.ALL)
        );
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                mContentResolver,
                (cr) -> cr.registerContentObserverAsUser(LEVEL2_URI, true, mco2, UserHandle.ALL)
        );

        // Initially nothing has happened.
        assertThat(mco1.hadOnChanged()).isFalse();
        assertThat(mco2.hadOnChanged()).isFalse();

        // Fire a change with the exact URI.
        // Should signal both observers due to exact match, notifyDescendants doesn't matter.
        mContentResolver.notifyChange(LEVEL2_URI, null);
        Thread.sleep(200);
        assertThat(mco1.hadOnChanged()).isTrue();
        assertThat(mco2.hadOnChanged()).isTrue();
        mco1.reset();
        mco2.reset();

        // Fire a change with a descendant URI.
        // Should only signal observer with notifyDescendants set to true.
        mContentResolver.notifyChange(LEVEL3_URI, null);
        Thread.sleep(200);
        assertThat(mco1.hadOnChanged()).isFalse();
        assertThat(mco2.hadOnChanged()).isTrue();
        mco2.reset();

        // Fire a change with an ancestor URI.
        // Should signal both observers due to ancestry, notifyDescendants doesn't matter.
        mContentResolver.notifyChange(LEVEL1_URI, null);
        Thread.sleep(200);
        assertThat(mco1.hadOnChanged()).isTrue();
        assertThat(mco2.hadOnChanged()).isTrue();
        mco1.reset();
        mco2.reset();

        // Fire a change with an unrelated URI.
        // Should signal neither observer.
        mContentResolver.notifyChange(TABLE1_URI, null);
        Thread.sleep(200);
        assertThat(mco1.hadOnChanged()).isFalse();
        assertThat(mco2.hadOnChanged()).isFalse();
    }

    @Test
    public void testNotifyChange1() {
        final MockContentObserver mco = new MockContentObserver();

        mContentResolver.registerContentObserver(TABLE1_URI, true, mco);
        assertThat(mco.hadOnChanged()).isFalse();

        mContentResolver.notifyChange(TABLE1_URI, mco);
        new PollingCheck() {
            @Override
            protected boolean check() {
                return mco.hadOnChanged();
            }
        }.run();

        mContentResolver.unregisterContentObserver(mco);
    }

    @Test
    public void testNotifyChange2() {
        final MockContentObserver mco = new MockContentObserver();

        mContentResolver.registerContentObserver(TABLE1_URI, true, mco);
        assertThat(mco.hadOnChanged()).isFalse();

        mContentResolver.notifyChange(TABLE1_URI, mco, 0);
        new PollingCheck() {
            @Override
            protected boolean check() {
                return mco.hadOnChanged();
            }
        }.run();

        mContentResolver.unregisterContentObserver(mco);
    }

    /**
     * Verify that callers using the {@link Iterable} version of {@link
     * ContentResolver#notifyChange} are correctly split and delivered to disjoint listeners.
     */
    @Test
    public void testNotifyChange_MultipleSplit() {
        final MockContentObserver observer1 = new MockContentObserver();
        final MockContentObserver observer2 = new MockContentObserver();

        mContentResolver.registerContentObserver(TABLE1_URI, true, observer1);
        mContentResolver.registerContentObserver(TABLE2_URI, true, observer2);

        assertThat(observer1.hadOnChanged()).isFalse();
        assertThat(observer2.hadOnChanged()).isFalse();

        final ArrayList<Uri> list = new ArrayList<>();
        list.add(TABLE1_URI);
        list.add(TABLE2_URI);
        mContentResolver.notifyChange(list, null, 0);

        new PollingCheck() {
            @Override
            protected boolean check() {
                return observer1.hadOnChanged() && observer2.hadOnChanged();
            }
        }.run();

        mContentResolver.unregisterContentObserver(observer1);
        mContentResolver.unregisterContentObserver(observer2);
    }

    /**
     * Verify that callers using the {@link Iterable} version of {@link
     * ContentResolver#notifyChange} are correctly grouped and delivered to overlapping listeners,
     * including untouched flags.
     */
    @Test
    public void testNotifyChange_MultipleFlags() {
        final MockContentObserver observer1 = new MockContentObserver();
        final MockContentObserver observer2 = new MockContentObserver();

        mContentResolver.registerContentObserver(LEVEL1_URI, false, observer1);
        mContentResolver.registerContentObserver(LEVEL2_URI, false, observer2);

        mContentResolver.notifyChange(List.of(LEVEL1_URI), null, 0);
        mContentResolver.notifyChange(
                Arrays.asList(LEVEL1_URI, LEVEL2_URI), null, NOTIFY_INSERT);
        mContentResolver.notifyChange(List.of(LEVEL2_URI), null, NOTIFY_UPDATE);

        final List<Change> expected1 =
                Arrays.asList(
                        new Change(false, List.of(LEVEL1_URI), 0),
                        new Change(false, List.of(LEVEL1_URI), NOTIFY_INSERT));

        final List<Change> expected2 =
                Arrays.asList(
                        new Change(false, List.of(LEVEL1_URI), 0),
                        new Change(false, Arrays.asList(LEVEL1_URI, LEVEL2_URI), NOTIFY_INSERT),
                        new Change(false, List.of(LEVEL2_URI), NOTIFY_UPDATE));

        new PollingCheck() {
            @Override
            protected boolean check() {
                return observer1.hadChanges(expected1)
                        && observer2.hadChanges(expected2);
            }
        }.run();

        mContentResolver.unregisterContentObserver(observer1);
        mContentResolver.unregisterContentObserver(observer2);
    }

    @Test
    public void testStartCancelSync() {
        Bundle extras = new Bundle();

        extras.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);

        ContentResolver.requestSync(ACCOUNT, AUTHORITY, extras);
        //FIXME: how to get the result to assert.

        ContentResolver.cancelSync(ACCOUNT, AUTHORITY);
        //FIXME: how to assert.
    }

    @Test
    public void testStartSyncFailure() {
        assertThrows(
                "did not throw IllegalArgumentException when extras is null.",
                IllegalArgumentException.class,
                () -> ContentResolver.requestSync(null, null, null));
    }

    @Test
    public void testValidateSyncExtrasBundle() {
        Bundle extras = new Bundle();
        extras.putInt("Integer", 20);
        extras.putLong("Long", 10L);
        extras.putBoolean("Boolean", true);
        extras.putFloat("Float", 5.5f);
        extras.putDouble("Double", 2.5);
        extras.putString("String", "cts");
        extras.putCharSequence("CharSequence", null);

        ContentResolver.validateSyncExtrasBundle(extras);

        extras.putChar("Char", 'a'); // type Char is invalid
        assertThrows(
                "did not throw IllegalArgumentException when extras is invalid.",
                IllegalArgumentException.class,
                () -> ContentResolver.validateSyncExtrasBundle(extras));
    }

    @AppModeFull
    @Test
    public void testHangRecover() throws Exception {
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity(android.Manifest.permission.REMOVE_TASKS);

        final CountDownLatch latch = new CountDownLatch(1);
        new Thread(
                        () -> {
                            try (ContentProviderClient client =
                                    mContentResolver.acquireUnstableContentProviderClient(
                                            REMOTE_AUTHORITY)) {
                                client.setDetectNotResponding(2_000);
                                try {
                                    client.query(REMOTE_HANG_URI, null, null, null);
                                    assertWithMessage("Funky, we somehow returned?").fail();
                                } catch (RemoteException e) {
                                    latch.countDown();
                                }
                            }
                        })
                .start();

        // The remote process should have been killed after the ANR was detected
        // above, causing our pending call to return and release our latch above
        // within 10 seconds; if our Binder thread hasn't been freed, then we
        // fail with a timeout.
        latch.await(10, TimeUnit.SECONDS);
    }

    @Test
    public void testGetTypeInfo() {
        for (String mimeType :
                new String[] {
                    "image/png",
                    "IMage/PnG",
                    "image/x-custom",
                    "application/x-flac",
                    "application/rdf+xml",
                    "x-custom/x-custom",
                }) {
            final MimeTypeInfo ti = mContentResolver.getTypeInfo(mimeType);
            assertThat(ti).isNotNull();
            assertThat(ti.getLabel()).isNotNull();
            assertThat(ti.getContentDescription()).isNotNull();
            assertThat(ti.getIcon()).isNotNull();
        }
    }

    @Test
    public void testGetTypeInfo_Invalid() {
        assertThrows(
                "Expected exception for null",
                NullPointerException.class,
                () -> mContentResolver.getTypeInfo(null));
    }

    @Test
    public void testWrapContentProvider() {
        try (ContentProviderClient local =
                mContext.getContentResolver().acquireContentProviderClient(AUTHORITY)) {
            final ContentResolver resolver = ContentResolver.wrap(local.getLocalContentProvider());
            assertThat(resolver.getType(TABLE1_URI)).isNotNull();
            try {
                resolver.getType(REMOTE_TABLE1_URI);
                assertWithMessage("").fail();
            } catch (SecurityException | IllegalArgumentException expected) {
            }
        }
    }

    @Test
    public void testWrapContentProviderClient() {
        try (ContentProviderClient remote =
                mContext.getContentResolver().acquireContentProviderClient(REMOTE_AUTHORITY)) {
            final ContentResolver resolver = ContentResolver.wrap(remote);
            assertThat(resolver.getType(REMOTE_TABLE1_URI)).isNotNull();
            try {
                resolver.getType(TABLE1_URI);
                assertWithMessage("").fail();
            } catch (SecurityException | IllegalArgumentException expected) {
            }
        }
    }

    @AppModeFull
    @Test
    public void testContentResolverCaching() {
        InstrumentationRegistry.getInstrumentation().getUiAutomation().adoptShellPermissionIdentity(
                android.Manifest.permission.CACHE_CONTENT,
                android.Manifest.permission.INTERACT_ACROSS_USERS_FULL);

        Bundle cached = new Bundle();
        cached.putString("key", "value");
        mContentResolver.putCache(TABLE1_URI, cached);

        Bundle response = mContentResolver.getCache(TABLE1_URI);
        assertThat(response.getString("key")).isEqualTo("value");

        ContentValues values = new ContentValues();
        values.put(COLUMN_KEY_NAME, "key10");
        values.put(COLUMN_VALUE_NAME, 10);
        mContentResolver.update(TABLE1_URI, values, null, null);

        response = mContentResolver.getCache(TABLE1_URI);
        assertThat(response).isNull();
    }

    @Test
    public void testEncodeDecode() {
        final Uri expected =
                new Uri.Builder()
                        .scheme(ContentResolver.SCHEME_CONTENT)
                        .authority(AUTHORITY)
                        .appendPath("com.example")
                        .appendPath("item")
                        .appendPath("23")
                        .build();
        final File file = ContentResolver.encodeToFile(expected);
        assertThat(file).isNotNull();

        final Uri actual = ContentResolver.decodeFromFile(file);
        assertThat(actual).isNotNull();
        assertThat(actual).isEqualTo(expected);
    }

    private static final class Change {
        private final boolean mSelfChange;
        private final Iterable<Uri> mUris;
        private final int mFlags;
        @UserIdInt private final int mUserId;

        Change(boolean selfChange, Iterable<Uri> uris, int flags) {
            mSelfChange = selfChange;
            mUris = uris;
            mFlags = flags;
            mUserId = -1;
        }

        Change(boolean selfChange, Iterable<Uri> uris, int flags, @UserIdInt int userId) {
            mSelfChange = selfChange;
            mUris = uris;
            mFlags = flags;
            mUserId = userId;
        }

        @Override
        public String toString() {
            return String.format(
                    "onChange(%b, %s, %d, %d)", mSelfChange, asSet(mUris), mFlags, mUserId);
        }

        @Override
        public boolean equals(Object other) {
            if (other instanceof Change change) {
                return change.mSelfChange == mSelfChange
                        && Objects.equals(asSet(change.mUris), asSet(mUris))
                        && change.mFlags == mFlags
                        && change.mUserId == mUserId;
            } else {
                return false;
            }
        }

        private static Set<Uri> asSet(Iterable<Uri> uris) {
            final Set<Uri> asSet = new HashSet<>();
            uris.forEach(asSet::add);
            return asSet;
        }
    }

    private static final class MockContentObserver extends ContentObserver {
        private boolean mHadOnChanged = false;
        private final List<Change> mChanges = new ArrayList<>();

        MockContentObserver() {
            super(null);
        }

        @Override
        public boolean deliverSelfNotifications() {
            return true;
        }

        @Override
        public synchronized void onChange(
                boolean selfChange, @NonNull Collection<Uri> uris, int flags) {
            doOnChangeLocked(selfChange, uris, flags, /*userId=*/ -1);
        }

        @Override
        public synchronized void onChange(boolean selfChange, @NonNull Collection<Uri> uris,
                @ContentResolver.NotifyFlags int flags, UserHandle user) {
            doOnChangeLocked(selfChange, uris, flags, user.getIdentifier());
        }

        private synchronized boolean hadOnChanged() {
            return mHadOnChanged;
        }

        private synchronized void reset() {
            mHadOnChanged = false;
        }

        private synchronized boolean hadChanges(Collection<Change> changes) {
            return mChanges.containsAll(changes);
        }

        @GuardedBy("this")
        private void doOnChangeLocked(boolean selfChange, @NonNull Collection<Uri> uris,
                @ContentResolver.NotifyFlags int flags, @UserIdInt int userId) {
            final Change change = new Change(selfChange, uris, flags, userId);
            Log.v(TAG, change.toString());

            mHadOnChanged = true;
            mChanges.add(change);
        }
    }
}
