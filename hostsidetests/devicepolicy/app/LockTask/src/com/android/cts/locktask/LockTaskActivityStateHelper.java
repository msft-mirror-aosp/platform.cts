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
package com.android.cts.locktask;

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

/** Helper to query whether the lock task activity is in resumed state. */
public class LockTaskActivityStateHelper {
    private static final String TAG = "LockTaskActivityStateHelper";

    static final String AUTHORITY = "com.android.cts.locktask.locktaskactivitystateprovider";
    static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/resumed_state");
    static final String COLUMN_RESUMED_VALUE = "resumed_value";

    /** Package name of the lock task package */
    public static final String LOCK_TASK_PACKAGE_NAME = "com.android.cts.locktask";

    /** Activity name of the lock task activity */
    public static final String LOCK_TASK_ACTIVITY_NAME =
            "com.android.cts.locktask.LockTaskActivity";

    /** Component name of the lock task activity */
    public static final ComponentName LOCK_TASK_ACTIVITY =
            new ComponentName(LOCK_TASK_PACKAGE_NAME, LOCK_TASK_ACTIVITY_NAME);

    /**
     * Intent extra key for lock task activity. When set to true, the activity will start lock task.
     */
    public static final String EXTRA_START_LOCK_TASK = "startLockTask";

    /** Returns whether the lock task activity is currently in the resumed state. */
    public static boolean isLockTaskActivityResumed(Context context) {
        ContentResolver resolver = context.getContentResolver();
        Cursor cursor = null;
        try {
            cursor = resolver.query(CONTENT_URI, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int value = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_RESUMED_VALUE));
                return value == 1;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error querying provider", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return false; // Default value in case of error or no data
    }
}
