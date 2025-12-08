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

package android.voiceinteraction.nopermission;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.service.voice.VoiceInteractionService;
import android.util.Log;

public class NoPermissionInteractionService extends VoiceInteractionService {
    private static final String TAG = "NoPermissionInteractionService";
    // Matching constants from MainInteractionService/Session for the test to consume
    public static final String ACTION_READY = "android.voiceinteraction.service.ACTION_READY";

    private boolean mReady = false;
    private Intent mIntent;

    @Override
    public void onReady() {
        super.onReady();
        mReady = true;
        sendBroadcast(new Intent(ACTION_READY).setPackage("android.voiceinteraction.cts"));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "onStartCommand received");
        mIntent = intent;

        if (mIntent == null || !mReady) {
            Log.w(TAG, "Can't start: intent=" + mIntent + ", mReady=" + mReady);
            return START_NOT_STICKY;
        }

        Bundle args = new Bundle();
        Intent activityIntent =
                new Intent()
                        .setAction(Intent.ACTION_VIEW)
                        .addCategory(Intent.CATEGORY_VOICE)
                        .addCategory(Intent.CATEGORY_BROWSABLE)
                        .setData(Uri.parse("https://android.voiceinteraction.testapp/TestApp"));
        args.putParcelable("intent", activityIntent);

        int showFlags = mIntent.getIntExtra("showFlags", 0);
        Log.v(TAG, "showSession() with flags: " + showFlags);
        showSession(args, showFlags);

        return START_NOT_STICKY;
    }
}
