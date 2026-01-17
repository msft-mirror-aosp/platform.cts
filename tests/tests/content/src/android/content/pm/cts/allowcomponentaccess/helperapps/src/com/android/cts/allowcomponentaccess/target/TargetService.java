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

package com.android.cts.allowcomponentaccess.target;

import android.app.Service;
import android.content.Intent;
import android.content.pm.cts.allowcomponentaccess.Constants;
import android.content.pm.cts.allowcomponentaccess.ITestCallback;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;

/**
 * A service running in the Target app. Execution of this code confirms that the Source was allowed
 * access.
 */
public class TargetService extends Service {

    @Override
    public IBinder onBind(Intent intent) {
        Bundle extras = intent.getExtras();
        if (extras != null) {
            IBinder binder = extras.getBinder(Constants.CALLBACK_BINDER);
            if (binder != null) {
                try {
                    // Notify the Test Runner that the connection succeeded
                    ITestCallback callback = ITestCallback.Stub.asInterface(binder);
                    callback.onActionReceived();
                } catch (Exception e) {
                    // Ignore failures; test will time out
                }
            }
        }
        return new Binder();
    }
}
