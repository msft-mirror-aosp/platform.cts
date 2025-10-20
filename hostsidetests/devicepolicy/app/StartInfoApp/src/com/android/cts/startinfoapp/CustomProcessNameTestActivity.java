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

import static com.android.cts.startinfoapp.TestHelper.REPLY_EXTRA_FAILURE_VALUE;
import static com.android.cts.startinfoapp.TestHelper.REPLY_EXTRA_SUCCESS_VALUE;
import static com.android.cts.startinfoapp.TestHelper.reply;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ApplicationStartInfo;
import android.os.Bundle;

import java.util.List;

/**
 * An activity with a custom process name to install to test ApplicationStartInfo.
 *
 * <p>A result will be provided back via a broadcast with action {@link REPLY_ACTION_COMPLETE} set
 * for all cases, success and failure.
 */
public class CustomProcessNameTestActivity extends Activity {

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        ActivityManager am = getSystemService(ActivityManager.class);
        List<ApplicationStartInfo> starts = am.getHistoricalProcessStartReasons(1);

        boolean success = starts != null && starts.size() == 1;
        reply(this, success ? REPLY_EXTRA_SUCCESS_VALUE : REPLY_EXTRA_FAILURE_VALUE);
    }
}
