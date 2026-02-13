/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.cts;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

public final class BinderPermissionTestService extends Service {

    private static final String TEST_NOT_ALLOWED_MESSAGE = "Test: you're not allowed to do this.";

    private final IBinder mBinder =
            new IBinderPermissionTestService.Stub() {
                @Override
                public void doEnforceCallingPermission(String permission) {
                    enforceCallingPermission(permission, TEST_NOT_ALLOWED_MESSAGE);
                }

                @Override
                public int doCheckCallingPermission(String permission) {
                    return checkCallingPermission(permission);
                }

                @Override
                public void doEnforceCallingOrSelfPermission(String permission) {
                    enforceCallingOrSelfPermission(permission, TEST_NOT_ALLOWED_MESSAGE);
                }

                @Override
                public int doCheckCallingOrSelfPermission(String permission) {
                    return checkCallingOrSelfPermission(permission);
                }

                @Override
                public void doBindServiceExpectingFailure(Intent intent, long flags) {
                    bindService(
                            intent,
                            new ServiceConnection() {
                                @Override
                                public void onServiceConnected(
                                        ComponentName name, IBinder service) {}

                                @Override
                                public void onServiceDisconnected(ComponentName name) {}
                            },
                            Context.BindServiceFlags.of(flags));
                }
            };

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }
}
