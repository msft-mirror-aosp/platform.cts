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

package android.voiceinteraction.service;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.voiceinteraction.common.Utils;

public class TrampolineReceiver extends BroadcastReceiver {
    static final String TAG = "TrampolineReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Intent serviceIntent = new Intent();
        serviceIntent.setComponent(new ComponentName(context, MainInteractionService.class));
        serviceIntent.putExtra(Utils.KEY_TEST_EVENT, Utils.VIS_NORMAL_TEST);

        final Bundle intentExtras = intent.getExtras();
        if (intentExtras != null) {
            serviceIntent.putExtras(intentExtras);
        }

        ComponentName serviceName = context.startService(serviceIntent);
        if (serviceName != null) {
            Log.i(TAG, "Started service: " + serviceName);
        } else {
            Log.e(TAG, "Failed to start service.");
        }
    }
}
