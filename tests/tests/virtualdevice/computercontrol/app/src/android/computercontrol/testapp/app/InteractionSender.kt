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
import android.computercontrol.testapp.common.Interaction
import android.os.Bundle
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import android.util.Log

object InteractionSender {

    private var remoteMessenger: Messenger? = null

    fun sendInteraction(interaction: Interaction) {
        Log.d(Constants.TAG, "Sending interaction: $interaction")
        if (remoteMessenger == null) {
            Log.w(Constants.TAG, "remoteMessenger is null, cannot send interaction")
            return
        }
        val msg = Message.obtain()
        val bundle = Bundle()
        bundle.putParcelable(Constants.KEY_INTERACTION, interaction)
        msg.data = bundle
        try {
            Log.d(Constants.TAG, "Sending interaction to remote messenger: $interaction")
            remoteMessenger?.send(msg)
            Log.d(Constants.TAG, "Sent interaction to remote messenger")
        } catch (e: RemoteException) {
            Log.e(Constants.TAG, "Error sending interaction", e)
        }
    }

    fun setRemoteMessenger(messenger: Messenger?) {
        Log.d(Constants.TAG, "Setting remote messenger: $messenger")
        remoteMessenger = messenger
    }
}
