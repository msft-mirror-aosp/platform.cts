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
package com.android.bedstead.dpmwrapper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Process
import android.util.Log
import com.android.bedstead.dpmwrapper.DeviceOwnerHelper.Companion.runManagerMethod
import com.android.bedstead.dpmwrapper.Utils.Companion.toString

/**
 * [BroadcastReceiver] used to run [android.app.admin.DevicePolicyManager] methods in the DPC
 * running in the device owner user.
 *
 * Used in cases where the test app doesn't "naturally" have a [DeviceAdminReceiver] (for example,
 * when it uses a [android.app.admin.DelegatedAdminReceiver]).
 *
 * It must be declared in the manifest:
 * <pre>`
 * <receiver android:name="com.android.bedstead.dpmwrapper.IpcBroadcastReceiver" android:exported="true">
 * <intent-filter>
 * <action android:name="com.android.bedstead.dpmwrapper.action.WRAPPED_MANAGER_CALL"></action>
 * </intent-filter>
 * </receiver>
 * `</pre>
 */
// TODO(b/176993670): remove when DpmWrapper IPC mechanism changes
class IpcBroadcastReceiver : BroadcastReceiver() {
    companion object {
        private val TAG: String = IpcBroadcastReceiver::class.java.getSimpleName()
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "onReceive(" + Process.myUserHandle() + "): " + toString(intent))
        runManagerMethod(this, context, intent)
    }
}
