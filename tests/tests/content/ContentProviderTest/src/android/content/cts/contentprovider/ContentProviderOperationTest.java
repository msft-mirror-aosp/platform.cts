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

package android.content.cts.contentprovider;

import static android.content.ContentResolver.QUERY_ARG_SQL_SELECTION;
import static android.content.ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.ContentProvider;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentValues;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.platform.test.annotations.AppModeSdkSandbox;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;

import java.util.Objects;

@RunWith(AndroidJUnit4.class)
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public final class ContentProviderOperationTest {
    private static final Uri TEST_URI = Uri.parse("content://com.example");
    private static final Uri TEST_URI_RESULT = Uri.parse("content://com.example/12");
    private static final String TEST_SELECTION = "foo=?";
    private static final String[] TEST_SELECTION_ARGS = new String[] {"bar"};
    private static final String TEST_METHOD = "test_method";
    private static final String TEST_ARG = "test_arg";

    private static final ContentValues TEST_VALUES = new ContentValues();
    private static final Bundle TEST_EXTRAS = new Bundle();
    private static final Bundle TEST_EXTRAS_WITH_SQL = new Bundle();
    private static final Bundle TEST_EXTRAS_RESULT = new Bundle();
    public static final ContentProviderResult[] BACK_REFS = new ContentProviderResult[0];

    static {
        TEST_VALUES.put("test_key", "test_value");

        TEST_EXTRAS.putString("test_key", "test_value");

        TEST_EXTRAS_WITH_SQL.putAll(TEST_EXTRAS);
        TEST_EXTRAS_WITH_SQL.putString(QUERY_ARG_SQL_SELECTION, TEST_SELECTION);
        TEST_EXTRAS_WITH_SQL.putStringArray(QUERY_ARG_SQL_SELECTION_ARGS, TEST_SELECTION_ARGS);

        TEST_EXTRAS_RESULT.putString("test_result", "42");
    }

    private static final ContentProviderResult[] TEST_RESULTS =
            new ContentProviderResult[] {
                new ContentProviderResult(TEST_URI_RESULT),
                new ContentProviderResult(84),
                new ContentProviderResult(TEST_EXTRAS_RESULT),
                new ContentProviderResult(new IllegalArgumentException()),
            };

    private ContentProvider mContentProvider;

    private ContentProviderOperation mContentProviderOperation;
    private ContentProviderResult mContentProviderResult;

    @Before
    public void setUp() throws Exception {
        mContentProvider = mock(ContentProvider.class);
    }

    @Test
    public void testInsert() throws Exception {
        mContentProviderOperation =
                ContentProviderOperation.newInsert(TEST_URI)
                        .withValues(TEST_VALUES)
                        .withExtras(TEST_EXTRAS)
                        .build();

        assertThat(mContentProviderOperation.getUri()).isEqualTo(TEST_URI);
        assertThat(mContentProviderOperation.isInsert()).isTrue();
        assertThat(mContentProviderOperation.isWriteOperation()).isTrue();

        when(mContentProvider.insert(eq(TEST_URI), eq(TEST_VALUES), eqBundle(TEST_EXTRAS)))
                .thenReturn(TEST_URI_RESULT);
        mContentProviderResult = mContentProviderOperation.apply(mContentProvider, BACK_REFS, 0);
        assertThat(mContentProviderResult.uri).isEqualTo(TEST_URI_RESULT);
    }

    @Test
    public void testUpdate() throws Exception {
        mContentProviderOperation =
                ContentProviderOperation.newUpdate(TEST_URI)
                        .withSelection(TEST_SELECTION, TEST_SELECTION_ARGS)
                        .withValues(TEST_VALUES)
                        .withExtras(TEST_EXTRAS)
                        .build();

        assertThat(mContentProviderOperation.getUri()).isEqualTo(TEST_URI);
        assertThat(mContentProviderOperation.isUpdate()).isTrue();
        assertThat(mContentProviderOperation.isWriteOperation()).isTrue();

        when(mContentProvider.update(eq(TEST_URI), eq(TEST_VALUES), eqBundle(TEST_EXTRAS_WITH_SQL)))
                .thenReturn(1);
        mContentProviderResult = mContentProviderOperation.apply(mContentProvider, BACK_REFS, 0);
        assertThat(mContentProviderResult.count).isEqualTo(1);
    }

    @Test
    public void testDelete() throws Exception {
        mContentProviderOperation =
                ContentProviderOperation.newDelete(TEST_URI)
                        .withSelection(TEST_SELECTION, TEST_SELECTION_ARGS)
                        .withExtras(TEST_EXTRAS)
                        .build();

        assertThat(mContentProviderOperation.getUri()).isEqualTo(TEST_URI);
        assertThat(mContentProviderOperation.isDelete()).isTrue();
        assertThat(mContentProviderOperation.isWriteOperation()).isTrue();

        when(mContentProvider.delete(eq(TEST_URI), eqBundle(TEST_EXTRAS_WITH_SQL))).thenReturn(1);
        mContentProviderResult = mContentProviderOperation.apply(mContentProvider, BACK_REFS, 0);
        assertThat(mContentProviderResult.count).isEqualTo(1);
    }

    @Test
    public void testAssertQuery() throws Exception {
        mContentProviderOperation =
                ContentProviderOperation.newAssertQuery(TEST_URI)
                        .withSelection(TEST_SELECTION, TEST_SELECTION_ARGS)
                        .withExtras(TEST_EXTRAS)
                        .withValues(TEST_VALUES)
                        .build();

        assertThat(mContentProviderOperation.getUri()).isEqualTo(TEST_URI);
        assertThat(mContentProviderOperation.isAssertQuery()).isTrue();
        assertThat(mContentProviderOperation.isReadOperation()).isTrue();

        final MatrixCursor cursor = new MatrixCursor(new String[] {"test_key"});
        cursor.addRow(new Object[] {"test_value"});

        when(mContentProvider.query(
                        eq(TEST_URI),
                        eq(new String[] {"test_key"}),
                        eqBundle(TEST_EXTRAS_WITH_SQL),
                        eq(null)))
                .thenReturn(cursor);
        mContentProviderOperation.apply(mContentProvider, BACK_REFS, 0);
    }

    @Test
    public void testCall() throws Exception {
        mContentProviderOperation =
                ContentProviderOperation.newCall(TEST_URI, TEST_METHOD, TEST_ARG)
                        .withExtras(TEST_EXTRAS)
                        .build();

        assertThat(mContentProviderOperation.getUri()).isEqualTo(TEST_URI);
        assertThat(mContentProviderOperation.isCall()).isTrue();

        when(mContentProvider.call(
                        eq(TEST_URI.getAuthority()),
                        eq(TEST_METHOD),
                        eq(TEST_ARG),
                        eqBundle(TEST_EXTRAS)))
                .thenReturn(TEST_EXTRAS_RESULT);
        mContentProviderResult = mContentProviderOperation.apply(mContentProvider, BACK_REFS, 0);
        assertThat(mContentProviderResult.extras).isEqualTo(TEST_EXTRAS_RESULT);
    }

    @Test
    public void testBackReferenceSelection() throws Exception {
        mContentProviderOperation =
                ContentProviderOperation.newDelete(TEST_URI)
                        .withSelection(null, new String[] {"a", "b", "c", "d"})
                        .withSelectionBackReference(0, 0)
                        .withSelectionBackReference(1, 1)
                        .withSelectionBackReference(2, 2, "test_result")
                        .build();

        final String[] res =
                mContentProviderOperation.resolveSelectionArgsBackReferences(
                        TEST_RESULTS, TEST_RESULTS.length);
        assertThat(res[0]).isEqualTo("12");
        assertThat(res[1]).isEqualTo("84");
        assertThat(res[2]).isEqualTo("42");
        assertThat(res[3]).isEqualTo("d");
    }

    @Test
    public void testBackReferenceValue() throws Exception {
        final ContentValues values = new ContentValues();
        values.put("a", "a");
        values.put("b", "b");
        values.put("c", "c");
        values.put("d", "d");

        mContentProviderOperation =
                ContentProviderOperation.newUpdate(TEST_URI)
                        .withValues(values)
                        .withValueBackReference("a", 0)
                        .withValueBackReference("b", 1)
                        .withValueBackReference("c", 2, "test_result")
                        .build();

        final ContentValues res =
                mContentProviderOperation.resolveValueBackReferences(
                        TEST_RESULTS, TEST_RESULTS.length);
        assertThat((long) res.get("a")).isEqualTo(12L);
        assertThat((long) res.get("b")).isEqualTo(84L);
        assertThat(res.get("c")).isEqualTo("42");
        assertThat(res.get("d")).isEqualTo("d");
    }

    @Test
    public void testBackReferenceExtra() throws Exception {
        final Bundle extras = new Bundle();
        extras.putString("a", "a");
        extras.putString("b", "b");
        extras.putString("c", "c");
        extras.putString("d", "d");

        mContentProviderOperation =
                ContentProviderOperation.newCall(TEST_URI, TEST_METHOD, TEST_ARG)
                        .withExtras(extras)
                        .withExtraBackReference("a", 0)
                        .withExtraBackReference("b", 1)
                        .withExtraBackReference("c", 2, "test_result")
                        .build();

        final Bundle res =
                mContentProviderOperation.resolveExtrasBackReferences(
                        TEST_RESULTS, TEST_RESULTS.length);
        assertThat(res.getLong("a")).isEqualTo(12L);
        assertThat(res.getLong("b")).isEqualTo(84L);
        assertThat(res.getString("c")).isEqualTo("42");
        assertThat(res.getString("d")).isEqualTo("d");
    }

    @Test
    public void testExceptionAllowed() throws Exception {
        mContentProviderOperation =
                ContentProviderOperation.newCall(TEST_URI, TEST_METHOD, TEST_ARG)
                        .withExtras(TEST_EXTRAS)
                        .withExceptionAllowed(true)
                        .build();

        assertThat(mContentProviderOperation.isExceptionAllowed()).isTrue();

        when(mContentProvider.call(
                        eq(TEST_URI.getAuthority()),
                        eq(TEST_METHOD),
                        eq(TEST_ARG),
                        eqBundle(TEST_EXTRAS)))
                .thenThrow(new IllegalArgumentException());
        mContentProviderResult = mContentProviderOperation.apply(mContentProvider, BACK_REFS, 0);
        assertThat((mContentProviderResult.exception instanceof IllegalArgumentException)).isTrue();
    }

    @Test
    public void testLayering() {
        mContentProviderOperation =
                ContentProviderOperation.newAssertQuery(TEST_URI)
                        .withSelection(TEST_SELECTION, TEST_SELECTION_ARGS)
                        .withExtras(TEST_EXTRAS)
                        .withExtra("test_key", "other_extra")
                        .withValues(TEST_VALUES)
                        .withValue("test_key", "other_value")
                        .build();

        assertThat(
                        mContentProviderOperation
                                .resolveExtrasBackReferences(BACK_REFS, 0)
                                .getString("test_key"))
                .isEqualTo("other_extra");
        assertThat(
                        mContentProviderOperation
                                .resolveValueBackReferences(BACK_REFS, 0)
                                .getAsString("test_key"))
                .isEqualTo("other_value");
    }

    private static Bundle eqBundle(Bundle bundle) {
        return ArgumentMatchers.argThat(
                (other) -> {
                    // Ideally we'd use something like Bundle.kindofEquals() here, but
                    // it doesn't perform deep equals inside String[] values, so the
                    // best we can do is a simple string equality check
                    return Objects.equals(bundle.toString(), other.toString());
                });
    }
}
