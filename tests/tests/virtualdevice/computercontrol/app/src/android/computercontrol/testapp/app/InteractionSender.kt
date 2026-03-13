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

import android.computercontrol.testapp.common.Action
import android.computercontrol.testapp.common.Constants
import android.computercontrol.testapp.common.Interaction
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

object InteractionSender {

    private var remoteCallback = MutableStateFlow<RemoteCallback?>(null)

    fun sendInteractionAsync(interaction: Interaction) {
        CoroutineScope(Dispatchers.Default).launch {
            // If a callback is not already set, wait for one to be set.
            val callback = withTimeout(20.seconds) { remoteCallback.first { it != null }!! }
            callback.messenger.send(
                Message.obtain().apply {
                    data.putParcelable(Constants.KEY_INTERACTION, interaction)
                }
            )
        }
    }

    fun setRemoteMessenger(messenger: Messenger, token: IBinder) {
        if (remoteCallback.value != null) {
            throw IllegalStateException("Remote messenger is already set!")
        }
        remoteCallback.value = RemoteCallback(messenger, token)
    }

    fun removeRemoteMessenger(token: IBinder) {
        val callback = remoteCallback.value
        if (callback?.token != token) {
            throw IllegalStateException("Token mismatch: expected ${callback?.token}, got $token")
        }

        callback.messenger.send(
            Message.obtain().apply {
                data.putParcelable(Constants.KEY_INTERACTION, Interaction(Action.CallbackRemoved))
            }
        )
        remoteCallback.value = null
    }

    private data class RemoteCallback(val messenger: Messenger, val token: IBinder)
}
