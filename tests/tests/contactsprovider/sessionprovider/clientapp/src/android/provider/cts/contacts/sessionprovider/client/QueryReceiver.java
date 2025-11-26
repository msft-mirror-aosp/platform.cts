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

package android.provider.cts.contacts.sessionprovider.client;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;

public class QueryReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Uri targetUri = intent.getParcelableExtra("target_uri");

        if (targetUri == null) {
            setResultCode(Activity.RESULT_CANCELED);
            return;
        }

        // If the grant is valid, this succeeds. If not, it throws a SecurityException.
        try (Cursor cursor =
                context.getContentResolver().query(targetUri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                setResultCode(Activity.RESULT_OK);
            } else {
                setResultCode(Activity.RESULT_CANCELED);
            }
        } catch (SecurityException e) {
            setResultCode(Activity.RESULT_FIRST_USER);
        }
    }
}
