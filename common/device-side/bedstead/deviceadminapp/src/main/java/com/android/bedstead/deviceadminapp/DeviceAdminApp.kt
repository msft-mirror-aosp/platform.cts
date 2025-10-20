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
package com.android.bedstead.deviceadminapp

import android.content.ComponentName
import android.content.Context
import com.android.eventlib.premade.EventLibDelegatedAdminReceiver
import com.android.eventlib.premade.EventLibDeviceAdminReceiver

/** Entry point for Device Admin App. */
object DeviceAdminApp {
    /** Get the [ComponentName] for the [DeviceAdminReceiver] subclass. */
    @JvmStatic
    fun deviceAdminComponentName(context: Context): ComponentName {
        return ComponentName(
            context.getPackageName(),
            EventLibDeviceAdminReceiver::class.java.getName(),
        )
    }

    /** Get the [ComponentName] for the [DelegatedAdminReceiver] subclass. */
    @JvmStatic
    fun delegatedAdminComponentName(context: Context): ComponentName {
        return ComponentName(
            context.getPackageName(),
            EventLibDelegatedAdminReceiver::class.java.getName(),
        )
    }
}
