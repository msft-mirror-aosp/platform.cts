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

package android.computercontrol.testapp.app

import android.computercontrol.testapp.common.Constants
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Messenger
import android.util.Log

class InteractionReceiverBinder : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        Log.d(Constants.TAG, "Received intent in InteractionReceiverBinder: $intent")
        if (intent?.action == Constants.ACTION_REMOTE_CALLBACK) {
            val remoteMessenger =
                intent.getParcelableExtra(
                    Constants.EXTRA_REMOTE_MESSENGER,
                    Messenger::class.java
                )
            InteractionSender.setRemoteMessenger(remoteMessenger)
        }
    }

    fun register(context: Context) {
        val filter = IntentFilter(Constants.ACTION_REMOTE_CALLBACK)
        context.registerReceiver(this, filter, Context.RECEIVER_EXPORTED)
    }

    fun unregister(context: Context) {
        context.unregisterReceiver(this)
    }
}
