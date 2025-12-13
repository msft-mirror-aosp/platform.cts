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
package android.computercontrol.testapp.common

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.util.Log

class InteractionReceiver(private val context: Context) {

    private class IncomingHandler(private val onInteractionReceived: (Interaction) -> Unit) :
        Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            Log.d(Constants.TAG, "Received message: $msg")
            msg.data?.classLoader = Interaction::class.java.classLoader
            val interaction =
                msg.data?.getParcelable(Constants.KEY_INTERACTION, Interaction::class.java)
            if (interaction != null) {
                onInteractionReceived.invoke(interaction)
            } else {
                Log.w(Constants.TAG, "Received message without interaction")
            }
        }
    }

    fun bind(onInteractionReceived: (Interaction) -> Unit) {
        Log.d(Constants.TAG, "Binding interaction receiver")
        val handler = IncomingHandler(onInteractionReceived)
        val messenger = Messenger(handler)
        val intent = Intent(Constants.ACTION_REMOTE_CALLBACK)
        intent.setPackage(Constants.TEST_APP_PACKAGE)
        intent.putExtra(Constants.EXTRA_REMOTE_MESSENGER, messenger)
        context.sendBroadcast(intent)
    }
}
