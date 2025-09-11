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

package com.android.cts.startinfoapp;

import android.content.Context;
import android.content.Intent;

/**
 * Helper class to share constants and methods across test activities.
 *
 * <p>Constant values are kept in sync with {@link android.app.cts.ActivityManagerAppStartInfoTest}
 * to ensure successful communication.
 */
public class TestHelper {
    // LINT.IfChange
    protected static final String REQUEST_KEY_ACTION = "action";
    protected static final String REQUEST_KEY_TIMESTAMP_KEY_FIRST = "timestamp_key_first";
    protected static final String REQUEST_KEY_TIMESTAMP_VALUE_FIRST = "timestamp_value_first";
    protected static final String REQUEST_KEY_TIMESTAMP_KEY_LAST = "timestamp_key_last";
    protected static final String REQUEST_KEY_TIMESTAMP_VALUE_LAST = "timestamp_value_last";

    // Request value for app to query and verify its own start.
    protected static final int REQUEST_VALUE_QUERY_START = 1;

    // Request value for app to add the provided timestamp to start info.
    protected static final int REQUEST_VALUE_ADD_TIMESTAMP = 2;

    // Request value for app to add a listener and respond when it gets triggered.
    protected static final int REQUEST_VALUE_LISTENER_ADD_ONE = 3;

    // Request value for app to add 2 listeners and respond when each gets triggered.
    protected static final int REQUEST_VALUE_LISTENER_ADD_MULTIPLE = 4;

    // Request value for app to add 2 listeners, remove 1, and respond success when correct one
    // is triggered and failure if incorrect one is triggered.
    protected static final int REQUEST_VALUE_LISTENER_ADD_REMOVE = 5;

    // Request value for app to immediately crash. No reply will be sent.
    protected static final int REQUEST_VALUE_CRASH = 6;

    // Broadcast action to return result for request.
    protected static final String REPLY_ACTION_COMPLETE =
            "com.android.cts.startinfoapp.ACTION_COMPLETE";

    protected static final String REPLY_EXTRA_STATUS_KEY = "status";

    protected static final int REPLY_EXTRA_SUCCESS_VALUE = 1;
    protected static final int REPLY_EXTRA_FAILURE_VALUE = 2;

    protected static final int REPLY_STATUS_NONE = -1;

    // LINT.ThenChange(//tests/app/AppStartTest/src/android/app/cts/ActivityManagerAppStartInfoTest.java)

    /** Send a broadcast with a test result status. */
    protected static void reply(Context context, int status) {
        Intent reply = new Intent();
        reply.setAction(REPLY_ACTION_COMPLETE);
        if (status != REPLY_STATUS_NONE) {
            reply.putExtra(REPLY_EXTRA_STATUS_KEY, status);
        }
        context.sendBroadcast(reply);
    }
}
