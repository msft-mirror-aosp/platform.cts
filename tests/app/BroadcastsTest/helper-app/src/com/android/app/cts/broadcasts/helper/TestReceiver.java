/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.app.cts.broadcasts.helper;

import static com.android.app.cts.broadcasts.Common.ACTION_QUERY_PACKAGE_NAME;
import static com.android.app.cts.broadcasts.Common.EXTRA_PACKAGE_NAMES;
import static com.android.app.cts.broadcasts.Common.ORDERED_BROADCAST_ACTION;
import static com.android.app.cts.broadcasts.Common.ORDERED_BROADCAST_RESULT_DATA;
import static com.android.app.cts.broadcasts.Common.TAG;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import java.util.ArrayList;

public class TestReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(TAG, "TestReceiver received intent: " + intent);
        if (ORDERED_BROADCAST_ACTION.equals(intent.getAction())) {
            setResultData(ORDERED_BROADCAST_RESULT_DATA);
        } else if (ACTION_QUERY_PACKAGE_NAME.equals(intent.getAction())) {
            final Bundle resultExtras = getOrCreateResultExtras();
            ArrayList<String> packages = resultExtras.getStringArrayList(EXTRA_PACKAGE_NAMES);
            if (packages == null) {
                packages = new ArrayList<>();
            }
            packages.add(context.getPackageName());
            resultExtras.putStringArrayList(EXTRA_PACKAGE_NAMES, packages);
            setResultExtras(resultExtras);
        }
    }

    private Bundle getOrCreateResultExtras() {
        final Bundle resultExtras = getResultExtras(false /* makeMap */);
        return resultExtras == null ? new Bundle() : resultExtras;
    }
}
