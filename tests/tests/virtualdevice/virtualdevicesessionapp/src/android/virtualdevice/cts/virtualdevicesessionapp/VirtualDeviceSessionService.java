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
import android.content.Intent;
import android.os.IBinder;
import android.os.Process;
import android.virtualdevice.cts.common.IVirtualDeviceSession;

public class VirtualDeviceSessionService extends Service {

    private final IVirtualDeviceSession.Stub mBinder = new IVirtualDeviceSession.Stub() {
        @Override
        public String getCreateDeviceCommand(String deviceName) {
            return String.format(
                    "cmd virtualdevice create-device %s --owner-uid %d --owner-package %s",
                    deviceName, Process.myUid(), getPackageName());
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }
}
