/*
 * Copyright 2020 The Android Open Source Project
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

package android.app.stubs.shared;

import android.os.ResultReceiver;

interface ICloseSystemDialogsTestsService {
    void sendCloseSystemDialogsBroadcast();

    const int RESULT_OK = 0;
    const int RESULT_SECURITY_EXCEPTION = 1;

    /**
     * Posts a notification with id {@code notificationId} with a broadcast pending intent, then in
     * that pending intent sends {@link android.content.Intent#ACTION_CLOSE_SYSTEM_DIALOGS}.
     *
     * The caller is responsible for trigerring the notification. The passed in {@code receiver}
     * will be called once the intent has been sent.
     */
    void postNotification(int notificationId, in ResultReceiver receiver);
}
