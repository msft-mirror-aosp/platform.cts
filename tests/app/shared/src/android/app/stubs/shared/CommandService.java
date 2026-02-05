/*
 * Copyright (C) 2026 The Android Open Source Project
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

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

public class CommandService extends Service {
    private static final String TAG = "CommandService";
    private ServiceConnection mConnection;

    private final ICommandService.Stub mBinder =
            new ICommandService.Stub() {
                @Override
                public void bindToService(String packageName, String className) {
                    Log.i(TAG, "Binding to " + packageName + "/" + className);
                    final Intent intent = new Intent().setClassName(packageName, className);
                    mConnection =
                            new ServiceConnection() {
                                @Override
                                public void onServiceConnected(
                                        ComponentName name, IBinder service) {
                                    Log.i(TAG, "Connected to " + name);
                                }

                                @Override
                                public void onServiceDisconnected(ComponentName name) {
                                    Log.i(TAG, "Disconnected from " + name);
                                }
                            };
                    bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
                }

                @Override
                public void unbindService() {
                    if (mConnection != null) {
                        CommandService.this.unbindService(mConnection);
                        mConnection = null;
                    }
                }
            };

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }
}
