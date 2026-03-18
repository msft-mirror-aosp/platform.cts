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
import android.os.Binder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.util.Log
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

class TestAppInteractionReceiver(private val context: Context) :
    AutoCloseable {

    private val token = Binder()
    private val interactionQueue = Channel<Interaction>(Channel.UNLIMITED)
    private var isBound = false
    private val closeJob = Job()

    private class IncomingHandler(
        private val onInteractionReceived: (Interaction) -> Unit,
        private val onClosed: () -> Unit
    ) :
        Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            msg.data?.classLoader = Interaction::class.java.classLoader
            val interaction =
                msg.data?.getParcelable(Constants.KEY_INTERACTION, Interaction::class.java)
            if (interaction != null) {
                Log.d(Constants.TAG, "Received message with interaction: $interaction")
                if (interaction.action is Action.CallbackRemoved) {
                    onClosed()
                    return
                }
                onInteractionReceived.invoke(interaction)
            } else {
                Log.w(Constants.TAG, "Received message without interaction")
            }
        }
    }

    private fun ensureBound() {
        check(closeJob.isActive) { "TestAppInteractionReceiver is already closed" }
        if (isBound) {
            return
        }
        Log.d(Constants.TAG, "Binding interaction receiver")
        val handler = IncomingHandler(interactionQueue::trySend, closeJob::complete)
        val intent = Intent(Constants.ACTION_SET_REMOTE_CALLBACK)
        intent.setComponent(Constants.RECEIVER_COMPONENT)
        val bundle = Bundle()
        bundle.putBinder(Constants.EXTRA_REMOTE_CALLBACK_TOKEN, token)
        intent.putExtras(bundle)
        intent.putExtra(Constants.EXTRA_REMOTE_MESSENGER, Messenger(handler))
        context.sendBroadcast(intent)
        isBound = true
    }

    override fun close() {
        if (!isBound) {
            closeJob.complete()
            return
        }
        interactionQueue.close()
        val intent = Intent(Constants.ACTION_REMOVE_REMOTE_CALLBACK)
        intent.setComponent(Constants.RECEIVER_COMPONENT)
        val bundle = Bundle()
        bundle.putBinder(Constants.EXTRA_REMOTE_CALLBACK_TOKEN, token)
        intent.putExtras(bundle)
        context.sendBroadcast(intent)

        runBlocking { withTimeout(5.seconds) { closeJob.join() } }
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
