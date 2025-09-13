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
package android.app.appsearch.testutil;

import android.app.appsearch.GenericDocument;
import android.app.appsearch.GetByDocumentIdRequest;
import android.app.appsearch.PutDocumentsRequest;
import android.app.appsearch.flags.Flags;
import android.util.Log;

/** Helper class for objects that don't override {@code toString}. */
@SuppressWarnings("UnnecessaryStringBuilder") // They will make it easier to add more info later
public final class FriendlyNeighborhoodStringerMan {

    private static final String TAG = FriendlyNeighborhoodStringerMan.class.getSimpleName();

    /** Gets a friendly string for such {@code request}. */
    public static String toString(PutDocumentsRequest request) {
        StringBuilder string =
                new StringBuilder("PutDocumentsRequest[genericDocs=")
                        .append(request.getGenericDocuments());
        if (hasFlagEnablePutDocumentsRequestAddTakenActions()) {
            string.append(", takenActionDocs=").append(request.getTakenActionGenericDocuments());
        }
        return string.append(']').toString();
    }

    /** Gets a friendly string for such {@code document}. */
    public static String toString(GenericDocument document) {
        StringBuilder string =
                new StringBuilder("Document[namespace=")
                        .append(document.getNamespace())
                        .append("id=")
                        .append(document.getId());
        return string.append(']').toString();
    }

    /** Gets a friendly string for such {@code request}. */
    public static String toString(GetByDocumentIdRequest request) {
        StringBuilder string =
                new StringBuilder("GetByDocumentIdRequest[namespace=")
                        .append(request.getNamespace())
                        .append("id=")
                        .append(request.getIds());
        return string.append(']').toString();
    }

    private FriendlyNeighborhoodStringerMan() {
        throw new UnsupportedOperationException("provides only static methods)");
    }

    private static boolean hasFlagEnablePutDocumentsRequestAddTakenActions() {
        try {
            return Flags.enablePutDocumentsRequestAddTakenActions();
        } catch (Throwable t) {
            // It shouldn't happen as the flag library is statically importeed, but if it does, we
            // don't care...
            Log.w(
                    TAG,
                    "failed to get flag enablePutDocumentsRequestAddTakenActions() ("
                            + t
                            + "); returning false");
            return false;
        }
    }
}
