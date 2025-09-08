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

import static android.security.cts.bug_435188844.ExtraContentProvider.PARAM_COUNT;
import static android.security.cts.bug_435188844.ExtraContentProvider.PARAM_PROFILE;

import android.net.Uri;

public class UriUtils {
    private static final Uri IMAGE_PROVIDER_URI =
            Uri.parse("content://android.security.cts.bug_435188844.provider_image");

    /** Create an image URI for the given index. */
    public static Uri makeImageUri(int index) {
        return makeImageUri(index, -1);
    }

    /** Create an image URI for the given index and profile ID. */
    public static Uri makeImageUri(int index, int profileId) {
        Uri.Builder builder = IMAGE_PROVIDER_URI.buildUpon();
        if (profileId >= 0) {
            builder.encodedAuthority(profileId + "@" + IMAGE_PROVIDER_URI.getEncodedAuthority());
        }
        builder.appendPath(Integer.toString(index));
        return builder.build();
    }

    /**
     * Create a URI for the a extra content provider that will return the given number of extra
     * items.
     */
    public static Uri makeExtraContentUri(Uri providerUri, int count) {
        return makeExtraContentUri(providerUri, count, -1);
    }

    /**
     * Create a URI for the a extra content provider in the specified profile that will return the
     * given number of extra items.
     */
    public static Uri makeExtraContentUri(Uri providerUri, int count, int profileId) {
        Uri.Builder builder = providerUri.buildUpon();
        builder.appendQueryParameter(PARAM_COUNT, Integer.toString(count));
        if (profileId >= 0) {
            builder.appendQueryParameter(PARAM_PROFILE, Integer.toString(profileId));
        }
        return builder.build();
    }

    /** Add a profile ID to the given URI. */
    public static Uri addProfileId(Uri uri, int profileId) {
        Uri.Builder builder = uri.buildUpon();
        builder.encodedAuthority(profileId + "@" + uri.getEncodedAuthority());
        return builder.build();
    }
}
