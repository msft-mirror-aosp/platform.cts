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

package android.security.cts.bug_435188844;

import static android.security.cts.bug_435188844.UriUtils.makeImageUri;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.service.chooser.AdditionalContentContract;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class ExtraContentProvider extends LoggingContentProvider {
    public static final String PARAM_COUNT = "count";
    public static final String PARAM_PROFILE = "profile";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    @Nullable
    public Cursor query(
            @NonNull Uri uri,
            @Nullable String[] projection,
            @Nullable Bundle queryArgs,
            @Nullable CancellationSignal cancellationSignal) {
        log("query", uri);
        int count = getIntParam(uri, PARAM_COUNT, 0);
        int profileId = getIntParam(uri, PARAM_PROFILE, -1);
        MatrixCursor cursor =
                new MatrixCursor(new String[] {AdditionalContentContract.Columns.URI});
        for (int i = 0; i < count; i++) {
            Uri extraItemUri = makeImageUri(i, profileId);
            cursor.addRow(new Object[] {extraItemUri});
        }
        return cursor;
    }

    private static int getIntParam(Uri uri, String name, int def) {
        try {
            String countString = uri.getQueryParameter(name);
            if (countString == null) {
                return def;
            }
            return Integer.parseInt(countString);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    @Override
    @Nullable
    public String getType(@NonNull Uri uri) {
        return null;
    }

    @Nullable
    @Override
    public Cursor query(
            @NonNull Uri uri,
            @Nullable String[] projection,
            @Nullable String selection,
            @Nullable String[] selectionArgs,
            @Nullable String sortOrder) {
        return null;
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        return null;
    }

    @Override
    public int delete(
            @NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(
            @NonNull Uri uri,
            @Nullable ContentValues values,
            @Nullable String selection,
            @Nullable String[] selectionArgs) {
        return 0;
    }
}
