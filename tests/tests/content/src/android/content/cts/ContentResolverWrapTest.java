/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.RETURNS_DEFAULTS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import android.content.ContentProvider;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.ProviderInfo;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.util.Size;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

@RunWith(AndroidJUnit4.class)
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public final class ContentResolverWrapTest {
    private static final String AUTHORITY = "com.example";
    private static final Uri URI =
            new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT).authority(AUTHORITY).build();
    private static final ArrayList<ContentProviderOperation> OPERATIONS = new ArrayList<>();
    private static final ContentProviderResult[] RESULTS = new ContentProviderResult[0];
    private static final ContentValues VALUES = new ContentValues();
    private static final ContentValues[] VALUES_ARRAY = new ContentValues[0];
    private static final String METHOD = "method";
    private static final String ARG = "arg";
    private static final String[] ARG_ARRAY = new String[0];
    private static final Bundle EXTRAS = new Bundle();
    private static final String TYPE = "mime/type";
    private static final String[] TYPE_ARRAY = new String[0];
    private static final CancellationSignal SIGNAL = new CancellationSignal();
    private static final String MODE = "rw";
    private static final ParcelFileDescriptor FD;
    private static final AssetFileDescriptor ASSET_FD;
    private static final Cursor CURSOR = new MatrixCursor(new String[0]);

    static {
        try {
            FD = ParcelFileDescriptor.open(new File("/dev/null"),
                    ParcelFileDescriptor.MODE_READ_ONLY);
            ASSET_FD = new AssetFileDescriptor(FD, 0, AssetFileDescriptor.UNKNOWN_LENGTH);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private ContentProvider mProvider;
    private ContentResolver mResolver;

    @Before
    public void setUp() throws Exception {
        Context context = mock(Context.class, RETURNS_DEFAULTS);
        mProvider = mock(ContentProvider.class, CALLS_REAL_METHODS);

        final ProviderInfo pi = new ProviderInfo();
        pi.authority = AUTHORITY;
        pi.exported = true;
        mProvider.attachInfo(context, pi);

        mResolver = ContentResolver.wrap(mProvider);
    }

    @Test
    public void testApplyBatch() throws Exception {
        doReturn(RESULTS).when(mProvider).applyBatch(AUTHORITY, OPERATIONS);
        assertThat(mResolver.applyBatch(AUTHORITY, OPERATIONS)).isEqualTo(RESULTS);
    }

    @Test
    public void testBulkInsert() throws Exception {
        doReturn(42).when(mProvider).bulkInsert(URI, VALUES_ARRAY);
        assertThat(mResolver.bulkInsert(URI, VALUES_ARRAY)).isEqualTo(42);
    }

    @Test
    public void testCall() throws Exception {
        doReturn(EXTRAS).when(mProvider).call(AUTHORITY, METHOD, ARG, EXTRAS);
        assertThat(mResolver.call(AUTHORITY, METHOD, ARG, EXTRAS)).isEqualTo(EXTRAS);
        assertThat(mResolver.call(URI, METHOD, ARG, EXTRAS)).isEqualTo(EXTRAS);
    }

    @Test
    public void testCanonicalize() throws Exception {
        doReturn(URI).when(mProvider).canonicalize(URI);
        assertThat(mResolver.canonicalize(URI)).isEqualTo(URI);
    }

    @Test
    public void testUncanonicalize() {
        doReturn(URI).when(mProvider).uncanonicalize(URI);
        assertThat(mResolver.uncanonicalize(URI)).isEqualTo(URI);
    }

    @Test
    public void testType() throws Exception {
        doReturn(TYPE).when(mProvider).getType(URI);
        assertEquals(TYPE, mResolver.getType(URI));
    }

    @Test
    public void testStreamTypes() {
        doReturn(TYPE_ARRAY).when(mProvider).getStreamTypes(URI, TYPE);
        assertThat(mResolver.getStreamTypes(URI, TYPE)).isEqualTo(TYPE_ARRAY);
    }

    @Test
    public void testInsert() throws Exception {
        doReturn(URI).when(mProvider).insert(URI, VALUES);
        assertThat(mResolver.insert(URI, VALUES)).isEqualTo(URI);
    }

    @Test
    public void testInsert_Extras() throws Exception {
        doReturn(URI).when(mProvider).insert(URI, VALUES, EXTRAS);
        assertThat(mResolver.insert(URI, VALUES, EXTRAS)).isEqualTo(URI);
    }

    @Test
    public void testUpdate() throws Exception {
        doReturn(42).when(mProvider).update(URI, VALUES, ARG, ARG_ARRAY);
        assertThat(mResolver.update(URI, VALUES, ARG, ARG_ARRAY)).isEqualTo(42);
    }

    @Test
    public void testUpdate_Extras() throws Exception {
        doReturn(21).when(mProvider).update(URI, VALUES, EXTRAS);
        assertThat(mResolver.update(URI, VALUES, EXTRAS)).isEqualTo(21);
    }

    @Test
    public void testDelete() throws Exception {
        doReturn(42).when(mProvider).delete(URI, ARG, ARG_ARRAY);
        assertThat(mResolver.delete(URI, ARG, ARG_ARRAY)).isEqualTo(42);
    }

    @Test
    public void testDelete_Extras() throws Exception {
        doReturn(21).when(mProvider).delete(URI, EXTRAS);
        assertThat(mResolver.delete(URI, EXTRAS)).isEqualTo(21);
    }

    @Test
    public void testRefresh() throws Exception {
        doReturn(true).when(mProvider).refresh(URI, EXTRAS, SIGNAL);
        assertThat(mResolver.refresh(URI, EXTRAS, SIGNAL)).isTrue();
    }

    @Test
    public void testOpenAssetFile() throws Exception {
        doReturn(ASSET_FD).when(mProvider).openAssetFile(URI, MODE, null);
        assertThat(mResolver.openAssetFile(URI, MODE, null)).isEqualTo(ASSET_FD);
        assertThat(mResolver.openAssetFileDescriptor(URI, MODE)).isEqualTo(ASSET_FD);
        assertThat(mResolver.openAssetFileDescriptor(URI, MODE, null)).isEqualTo(ASSET_FD);
    }

    @Test
    public void testOpenFile() throws Exception {
        doReturn(FD).when(mProvider).openFile(URI, MODE, null);
        assertThat(mResolver.openFile(URI, MODE, null)).isEqualTo(FD);
        assertThat(mResolver.openFileDescriptor(URI, MODE)).isEqualTo(FD);
        assertThat(mResolver.openFileDescriptor(URI, MODE, null)).isEqualTo(FD);
    }

    @Test
    public void testOpenStream() throws Exception {
        doReturn(ASSET_FD).when(mProvider).openAssetFile(URI, "r", null);
        doReturn(ASSET_FD).when(mProvider).openAssetFile(URI, "w", null);
        assertThat(mResolver.openInputStream(URI)).isNotNull();
        assertThat(mResolver.openOutputStream(URI)).isNotNull();
    }

    @Test
    public void testOpenTypedAssetFile() throws Exception {
        doReturn(ASSET_FD).when(mProvider).openTypedAssetFile(URI, TYPE, EXTRAS, null);
        assertThat(mResolver.openTypedAssetFile(URI, TYPE, EXTRAS, null)).isEqualTo(ASSET_FD);
        assertThat(mResolver.openTypedAssetFileDescriptor(URI, TYPE, EXTRAS)).isEqualTo(ASSET_FD);
        assertThat(mResolver.openTypedAssetFileDescriptor(URI, TYPE, EXTRAS, null))
                .isEqualTo(ASSET_FD);
    }

    @Test
    public void testQuery() throws Exception {
        doReturn(CURSOR).when(mProvider).query(eq(URI), eq(ARG_ARRAY), any(), any());
        assertThat(mResolver.query(URI, ARG_ARRAY, null, null)).isEqualTo(CURSOR);
        assertThat(mResolver.query(URI, ARG_ARRAY, null, null, null)).isEqualTo(CURSOR);
        assertThat(mResolver.query(URI, ARG_ARRAY, null, null, null, null)).isEqualTo(CURSOR);
    }

    @Test
    public void testLoadThumbnail() throws Exception {
        doReturn(ASSET_FD).when(mProvider).openTypedAssetFile(eq(URI), any(), any(), eq(SIGNAL));
        assertThrows(
                IOException.class, () -> mResolver.loadThumbnail(URI, new Size(32, 32), SIGNAL));
    }
}
