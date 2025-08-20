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

import android.content.ContentProvider;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public abstract class LoggingContentProvider extends ContentProvider {
    public static final String METHOD_VERIFY = "verify";

    private final Map<String, ArrayList<String>> mRequestedUris = new HashMap<>();

    protected void log(String method, Uri uri) {
        synchronized (mRequestedUris) {
            mRequestedUris.computeIfAbsent(method, k -> new ArrayList<>()).add(uri.toString());
        }
    }

    @Nullable
    @Override
    public Bundle call(@NonNull String method, @Nullable String arg, @Nullable Bundle extras) {
        if (METHOD_VERIFY.equals(method)) {
            Bundle result = new Bundle();
            synchronized (mRequestedUris) {
                for (Map.Entry<String, ArrayList<String>> entry : mRequestedUris.entrySet()) {
                    result.putStringArrayList(entry.getKey(), entry.getValue());
                }
            }
            return result;
        }
        return null;
    }

    /** Pull a content provider method invocation log */
    public static Map<String, ArrayList<String>> getInvokedMethods(Context context, Uri uri) {
        Bundle result = context.getContentResolver().call(uri, METHOD_VERIFY, null, null);
        if (result == null) {
            return Collections.emptyMap();
        }
        Map<String, ArrayList<String>> map = new HashMap<>();
        for (String key : result.keySet()) {
            map.put(key, result.getStringArrayList(key));
        }
        return map;
    }
}
