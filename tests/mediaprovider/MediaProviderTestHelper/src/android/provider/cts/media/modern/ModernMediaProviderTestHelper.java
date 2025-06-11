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

package src.android.provider.cts.media.modern;

import static src.android.provider.cts.media.modern.MediaStoreTestUtils.FAV_API_EXCEPTION;
import static src.android.provider.cts.media.modern.MediaStoreTestUtils.FAV_API_URI;
import static src.android.provider.cts.media.modern.MediaStoreTestUtils.FAV_API_VALUE;
import static src.android.provider.cts.media.modern.MediaStoreTestUtils.IS_CALL_SUCCESSFUL;
import static src.android.provider.cts.media.modern.MediaStoreTestUtils.MEDIASTORE_GET_DB_ROW_COUNT_ARG_TRASHED;
import static src.android.provider.cts.media.modern.MediaStoreTestUtils.MEDIASTORE_GET_DB_ROW_COUNT_CALL;
import static src.android.provider.cts.media.modern.MediaStoreTestUtils.MEDIASTORE_GET_DB_ROW_COUNT_DIR_NAME;
import static src.android.provider.cts.media.modern.MediaStoreTestUtils.MEDIASTORE_GET_DB_ROW_COUNT_VALUE;
import static src.android.provider.cts.media.modern.MediaStoreTestUtils.MEDIASTORE_MARK_FILE_AS_RESTORED_CALL;
import static src.android.provider.cts.media.modern.MediaStoreTestUtils.MEDIASTORE_MARK_FILE_AS_RESTORED_DIR_PATH;
import static src.android.provider.cts.media.modern.MediaStoreTestUtils.MEDIASTORE_MARK_FILE_AS_RESTORED_VALUE;
import static src.android.provider.cts.media.modern.MediaStoreTestUtils.MEDIASTORE_MARK_FILE_AS_RESTORE_EXCEPTION;
import static src.android.provider.cts.media.modern.MediaStoreTestUtils.MEDIASTORE_MARK_FILE_AS_TRASHED_CALL;
import static src.android.provider.cts.media.modern.MediaStoreTestUtils.MEDIASTORE_MARK_FILE_AS_TRASHED_DIR_PATH;
import static src.android.provider.cts.media.modern.MediaStoreTestUtils.MEDIASTORE_MARK_FILE_AS_TRASHED_EXCEPTION;
import static src.android.provider.cts.media.modern.MediaStoreTestUtils.MEDIASTORE_MARK_FILE_AS_TRASHED_VALUE;
import static src.android.provider.cts.media.modern.MediaStoreTestUtils.MEDIASTORE_MARK_MEDIA_AS_FAV_API_CALL;
import static src.android.provider.cts.media.modern.MediaStoreTestUtils.MEDIA_PROVIDER_INTENT_EXCEPTION;
import static src.android.provider.cts.media.modern.MediaStoreTestUtils.QUERY_TYPE;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;

import java.util.Collections;

public class ModernMediaProviderTestHelper extends Activity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String queryType = getIntent().getStringExtra(QUERY_TYPE);
        queryType = queryType == null ? "null" : queryType;
        Intent returnIntent;
        try {
            switch (queryType) {
                case MEDIASTORE_MARK_MEDIA_AS_FAV_API_CALL: {
                    returnIntent = callMarkAsFavFromMediaStore(queryType);
                    break;
                }
                case MEDIASTORE_GET_DB_ROW_COUNT_CALL: {
                    returnIntent = callGetDBRowCount(queryType);
                    break;
                }
                case MEDIASTORE_MARK_FILE_AS_TRASHED_CALL: {
                    returnIntent = markFileAsTrashed(queryType);
                    break;
                }
                case MEDIASTORE_MARK_FILE_AS_RESTORED_CALL: {
                    returnIntent = restoreFileFromTrash(queryType);
                    break;
                }
                case "null":
                default:
                    throw new IllegalStateException(
                            "Unknown query received from launcher app: " + queryType);
            }
        } catch (Exception ex) {
            returnIntent = new Intent(queryType);
            returnIntent.putExtra(MEDIA_PROVIDER_INTENT_EXCEPTION, ex);
        }
        sendBroadcast(returnIntent);
    }

    private Intent callMarkAsFavFromMediaStore(String queryType) {
        final Intent intent = new Intent(queryType);
        final Uri uri = getIntent().getParcelableExtra(FAV_API_URI);
        final boolean value = getIntent().getBooleanExtra(FAV_API_VALUE, true);

        try {
            MediaStore.markIsFavoriteStatus(getContentResolver(), Collections.singletonList(uri),
                    value);
            intent.putExtra(IS_CALL_SUCCESSFUL, true);
        } catch (Exception ex) {
            intent.putExtra(IS_CALL_SUCCESSFUL, false);
            intent.putExtra(FAV_API_EXCEPTION, ex);
        }

        return intent;
    }

    /**
     * Queries the MediaStore for file counts within a specified directory, including or excluding
     * trashed items based on the provided match trashed type.
     *
     * @param queryType The action string for the intent.
     * @return An Intent containing the query result (count) and success status.
     */
    private Intent callGetDBRowCount(String queryType) {
        final Intent intent = new Intent(queryType);
        final String dirName = getIntent().getStringExtra(MEDIASTORE_GET_DB_ROW_COUNT_DIR_NAME);
        final int matchTrashValue =
                getIntent()
                        .getIntExtra(
                                MEDIASTORE_GET_DB_ROW_COUNT_ARG_TRASHED, MediaStore.MATCH_INCLUDE);

        try {
            String[] projection =
                    new String[]{
                            MediaStore.MediaColumns._ID,
                            MediaStore.MediaColumns.DATA,
                            MediaStore.MediaColumns.RELATIVE_PATH,
                            MediaStore.MediaColumns.IS_TRASHED
                    };

            String[] selectionArgs = new String[]{"%/" + dirName + "%"};

            Bundle queryArgs = new Bundle();
            queryArgs.putString(
                    ContentResolver.QUERY_ARG_SQL_SELECTION,
                    MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ?");
            queryArgs.putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs);
            queryArgs.putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, matchTrashValue);

            try (Cursor cursor =
                         getContentResolver()
                                 .query(
                                         MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL),
                                         projection,
                                         queryArgs,
                                         null)) {
                int count = cursor.getCount();
                intent.putExtra(MEDIASTORE_GET_DB_ROW_COUNT_VALUE, count);
            }
            intent.putExtra(IS_CALL_SUCCESSFUL, true);
        } catch (Exception ex) {
            intent.putExtra(IS_CALL_SUCCESSFUL, false);
            intent.putExtra(MEDIASTORE_GET_DB_ROW_COUNT_VALUE, 0);
        }

        return intent;
    }

    /**
     * Marks a file or directory in MediaStore as trashed
     *
     * @param queryType The action string for the intent.
     * @return An Intent indicating the success of the operation and the path that was trashed.
     */
    private Intent markFileAsTrashed(String queryType) {
        final Intent intent = new Intent(queryType);
        final String trashPath =
                getIntent().getStringExtra(MEDIASTORE_MARK_FILE_AS_TRASHED_DIR_PATH);
        try {
            String trashedPath = MediaStore.trashFile(getContentResolver(), trashPath);

            intent.putExtra(IS_CALL_SUCCESSFUL, true);
            intent.putExtra(MEDIASTORE_MARK_FILE_AS_TRASHED_VALUE, trashedPath);
        } catch (Exception ex) {
            intent.putExtra(MEDIASTORE_MARK_FILE_AS_TRASHED_EXCEPTION, ex);
            intent.putExtra(IS_CALL_SUCCESSFUL, false);
        }

        return intent;
    }

    /**
     * Restores a file or directory in MediaStore from the trash.
     *
     * @param queryType The action string for the intent.
     * @return An Intent indicating the success of the operation and the path that was restored.
     */
    private Intent restoreFileFromTrash(String queryType) {
        final Intent intent = new Intent(queryType);
        final String trashPath =
                getIntent().getStringExtra(MEDIASTORE_MARK_FILE_AS_RESTORED_DIR_PATH);
        try {
            String restoredPath = MediaStore.restoreFileFromTrash(getContentResolver(), trashPath,
                    null);
            intent.putExtra(IS_CALL_SUCCESSFUL, true);
            intent.putExtra(MEDIASTORE_MARK_FILE_AS_RESTORED_VALUE, restoredPath);
        } catch (Exception ex) {
            intent.putExtra(IS_CALL_SUCCESSFUL, false);
            intent.putExtra(MEDIASTORE_MARK_FILE_AS_RESTORE_EXCEPTION, ex);
        }
        return intent;
    }
}
