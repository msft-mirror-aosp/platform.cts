/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.interactive.steps

import android.os.Build
import com.android.bedstead.adb.adb
import com.android.bedstead.nene.TestApis
import com.android.bedstead.usb.usb
import java.lang.reflect.Field

// TODO(b/349136331): AdbManager is not aware of the active connections. This needs to be
// added in AdbDebuggingManager and adbd.
class ConnectViaAdbToHostStep : ActAndWaitStep(
    "Connect this device via ADB to the host",
    {
        /*
        Build.IS_EMULATOR can be accessed in Gerrit, but we use reflection for the sake of Google3
        repository, where the field is unavailable and there is no clear way on how to fix that.
        */
        val isEmulator = Build::class.java.getDeclaredField("IS_EMULATOR").getBoolean(Build::class.java)

        TestApis.adb().isEnabledOverWifi() || TestApis.usb().isConnected() || isEmulator
    }
)
