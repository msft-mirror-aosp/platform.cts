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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.cts.allowcomponentaccess.Constants;
import android.content.pm.cts.allowcomponentaccess.ITestCallback;
import android.os.Bundle;
import android.os.IBinder;

/**
 * A receiver running in the Target app.
 *
 * <p>Its job is to receive a Broadcast and use the binder to tell the Test Runner that the
 * broadcast was successfully delivered.
 *
 * <p>If this code executes, it proves the OS allowed the Source to broadcast to the Target.
 */
public class TargetReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        // 1. Check if the binder is inside the intent
        Bundle extras = intent.getExtras();
        if (extras != null) {
            IBinder binder = extras.getBinder(Constants.CALLBACK_BINDER);
            if (binder != null) {
                try {
                    // 2. Notify the Test Runner: "Success! Broadcast Received."
                    ITestCallback callback = ITestCallback.Stub.asInterface(binder);
                    callback.onActionReceived();
                } catch (Exception e) {
                    // Ignore failures (the test will handle the timeout)
                }
            }
        }
    }
}
