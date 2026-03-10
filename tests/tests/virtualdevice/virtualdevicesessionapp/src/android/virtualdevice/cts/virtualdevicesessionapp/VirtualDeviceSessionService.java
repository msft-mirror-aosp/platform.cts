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

package android.virtualdevice.cts.virtualdevicesessionapp;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Process;
import android.virtualdevice.cts.common.IVirtualDeviceSession;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class VirtualDeviceSessionService extends Service {

    private final CountDownLatch mCountDownLatch = new CountDownLatch(1);
    private boolean mIsBound = false;

    private final ServiceConnection mServiceConnection =
            new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    mCountDownLatch.countDown();
                    mIsBound = true;
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {
                    mIsBound = false;
                }
            };

    private final IVirtualDeviceSession.Stub mBinder =
            new IVirtualDeviceSession.Stub() {
                @Override
                public String getCreateDeviceCommand(String deviceName) {
                    return String.format(
                            "cmd virtualdevice create-device %s --owner-uid %d --owner-package %s",
                            deviceName, Process.myUid(), getPackageName());
                }

                @Override
                public void bindService(ComponentName componentName) {
                    if (mIsBound) {
                        throw new IllegalStateException("Only one service may be bound");
                    }
                    final Intent intent = new Intent().setComponent(componentName);
                    if (!VirtualDeviceSessionService.this.bindService(
                            intent, mServiceConnection, Context.BIND_AUTO_CREATE)) {
                        throw new RuntimeException("Failed to bind to " + componentName);
                    }
                    try {
                        if (!mCountDownLatch.await(30, TimeUnit.SECONDS)) {
                            throw new RuntimeException(
                                    "Timed out waiting for binding to " + componentName);
                        }
                    } catch (InterruptedException e) {
                        throw new RuntimeException(
                                "Interrupted waiting for binding to " + componentName);
                    }
                }
            };

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onDestroy() {
        if (mIsBound) {
            unbindService(mServiceConnection);
        }
    }
}
