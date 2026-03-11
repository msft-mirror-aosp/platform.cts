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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

class TestAppInteractionReceiver(private val context: Context) :
    AutoCloseable {

    private val interactionQueue = Channel<Interaction>(Channel.UNLIMITED)
    private var isBound = false
    private var isClosed = false

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

    fun bind() {
        ensureBound()
    }

    private fun ensureBound() {
        check(!isClosed) { "TestAppInteractionReceiver is already closed" }
        if (isBound) {
            return
        }
        Log.d(Constants.TAG, "Binding interaction receiver")
        val handler = IncomingHandler(interactionQueue::trySend)
        val messenger = Messenger(handler)
        val intent = Intent(Constants.ACTION_REMOTE_CALLBACK)
        intent.setPackage(Constants.TEST_APP_PACKAGE)
        intent.putExtra(Constants.EXTRA_REMOTE_MESSENGER, messenger)
        context.sendBroadcast(intent)
        isBound = true
    }

    override fun close() {
        isClosed = true
        interactionQueue.close()
    }

    suspend fun <T : Action> nextActionOrNull(clazz: Class<T>, timeout: Duration): T? {
        ensureBound()
        return withTimeoutOrNull(timeout) {
            while (isActive) {
                val result = interactionQueue.receiveCatching()
                if (result.isClosed) return@withTimeoutOrNull null
                val interaction = result.getOrThrow()
                val action = interaction.action
                if (clazz.isInstance(action)) {
                    @Suppress("UNCHECKED_CAST")
                    return@withTimeoutOrNull action as T
                }
            }
            null
        }
    }

    inline fun <reified T : Action> nextAction(): T = runBlocking {
        checkNotNull(nextActionOrNull(T::class.java, 20.seconds)) {
            "Timed out waiting to receive the next action of type ${T::class.simpleName}"
        }
    }

    inline fun <reified T : Action> assertNoAction(): Unit = runBlocking {
        if (nextActionOrNull(T::class.java, 2.seconds) != null) {
            throw IllegalStateException("Received unexpected action of type ${T::class.simpleName}")
        }
    }
}
