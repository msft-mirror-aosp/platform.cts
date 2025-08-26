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

package android.scopedstorage.cts.device;

import static android.scopedstorage.cts.lib.TestUtils.getContentResolver;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.MediaStore;
import android.scopedstorage.cts.lib.TestUtils;

import java.io.File;
import java.io.IOException;

public class StorageUtils {

    /**
     * Grants/Removes media grants for given image. Also removes owner_package_name for given image
     * in files table when grant is removed.
     */
    public static void modifyReadAccess(
            File imageFile, String currentPackageName, GrantModifications modification)
            throws IOException {
        final String pickerUri1 = buildPhotopickerUriWithStringEscaping(imageFile);

        String adbCommand =
                "content call "
                        + " --method "
                        + ((modification == GrantModifications.GRANT)
                                ? "grant_media_read_for_package"
                                : "revoke_media_read_for_package")
                        + " --user "
                        + UserHandle.myUserId()
                        + " --uri content://media/external/file"
                        + " --extra uri:s:"
                        + pickerUri1
                        + " --extra "
                        + Intent.EXTRA_PACKAGE_NAME
                        + ":s:"
                        + currentPackageName;
        TestUtils.executeShellCommand(adbCommand);
    }

    private static String buildPhotopickerUriWithStringEscaping(File imageFile) {
        /*
        adb shell content call  --method 'grant_media_read_for_package'
        --uri content://media/external/file
        --extra uri:s:content\\://media/picker/0/com.android.providers.media
        .photopicker/media/1000000089
        --extra android.intent.extra.PACKAGE_NAME:s:android.scopedstorage.cts.device
         */
        final Uri originalUri = MediaStore.scanFile(getContentResolver(), imageFile);
        long fileId = ContentUris.parseId(originalUri);

        // We are forced to build the URI string this way due to various layers of string escaping
        // we are hitting when using uris in adb shell commands from tests.
        return "content\\://"
                + MediaStore.AUTHORITY
                + Uri.EMPTY
                        .buildUpon()
                        .appendPath("picker") // PickerUriResolver.PICKER_SEGMENT
                        .appendPath(String.valueOf(UserHandle.myUserId()))
                        .appendPath("com.android.providers.media.photopicker") //
                        .appendPath(MediaStore.AUTHORITY)
                        .appendPath(Long.toString(fileId))
                        .build();
    }

    /** Queries files table for external_primary with given query arguments. */
    public static Cursor getResultForFilesQuery(ContentResolver contentResolver, Bundle queryArgs) {
        return contentResolver.query(
                MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                null,
                queryArgs,
                null);
    }

    public enum GrantModifications {
        GRANT,
        REVOKE;
    }
}
