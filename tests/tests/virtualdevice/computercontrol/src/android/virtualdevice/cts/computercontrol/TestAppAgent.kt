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

package android.virtualdevice.cts.computercontrol

import android.computercontrol.testapp.common.Action
import android.computercontrol.testapp.common.Interaction
import android.computercontrol.testapp.common.InteractionReceiver
import android.computercontrol.testapp.common.TestAppFocusRequester
import android.content.ComponentName
import android.content.Context
import android.media.Image
import android.util.Log
import android.util.Size
import com.android.extensions.computercontrol.ComputerControlSession
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

class TestAppAgent(
    private val context: Context,
    private val session: ComputerControlSession,
    private val packageName: String,
    private val className: String? = null,
) : AutoCloseable {
    private val interactionQueue = Channel<Interaction>(Channel.UNLIMITED)
    private val interactionReceiver: InteractionReceiver
    private val testAppFocusRequester: TestAppFocusRequester

    init {
        actAndWaitForStable {
            if (className != null) {
                session.launchApplication(ComponentName(packageName, className))
                Log.d("TestAppAgent", "Launched application: $className")
            } else {
                session.launchApplication(packageName)
                Log.d("TestAppAgent", "Launched application: $packageName")
            }
        }
        Log.d("TestAppAgent", "TestAppAgent init, binding InteractionReceiver")
        interactionReceiver = InteractionReceiver(context)
        interactionReceiver.bind { interaction ->
            Log.d("TestAppAgent", "Interaction received")
            interactionQueue.trySend(interaction)
        }
        testAppFocusRequester = TestAppFocusRequester(context)
        // TODO: look into how to get rid of this sleep for interaction receiver
        // binding.
        Thread.sleep(1000)
    }

    override fun close() {
        val future = CompletableFuture<Void>()
        session.setLifecycleCallback(
            Executors.newSingleThreadExecutor(),
            object : ComputerControlSession.LifecycleCallback {
                override fun onActive() {}

                override fun onBlocked(reason: Int, blockingPackage: String?) {}

                override fun onClosed(reason: Int) {
                    future.complete(null)
                }
            }
        )
        session.close()
        // Wait for the onClosed() callback to be invoked.
        future.get(SESSION_CLOSE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    }

    fun requestFocus(textFieldId: String) {
        testAppFocusRequester.requestFocus(textFieldId)
        // TODO: look into how to get rid of this sleep for requesting focus.
        Thread.sleep(1000)
    }

    fun tap(x: Int, y: Int) {
        actAndWaitForStable { session.tap(x, y) }
    }

    fun longPress(x: Int, y: Int) {
        actAndWaitForStable { session.longPress(x, y) }
    }

    fun swipe(x1: Int, y1: Int, x2: Int, y2: Int) {
        actAndWaitForStable { session.swipe(x1, y1, x2, y2) }
    }

    fun performAction(actionCode: Int) {
        actAndWaitForStable { session.performAction(actionCode) }
    }

    fun insertText(text: String) {
        actAndWaitForStable { session.insertText(text, true, true) }
    }

    fun getDisplaySize(): Size {
        return session.getDisplaySize()
    }

    fun getScreenshot(): Image? {
        return session.getScreenshot()
    }

    fun <T : Action> nextAction(clazz: Class<T>): T? = runBlocking {
        withTimeoutOrNull(TimeUnit.SECONDS.toMillis(20)) {
            while (true) {
                val interaction = interactionQueue.receive()
                val action = interaction.action
                if (clazz.isInstance(action)) {
                    @Suppress("UNCHECKED_CAST")
                    return@withTimeoutOrNull action as T
                }
            }
            @Suppress("UNREACHABLE_CODE") null
        }
    }

    fun actAndWaitForStable(action: () -> Unit) {
        val future = CompletableFuture<Void>()
        Log.d("TestAppAgent", "Setting stability listener")
        session.setStabilityListener(
            Executors.newSingleThreadExecutor(),
            object : ComputerControlSession.StabilityListener {
                override fun onSessionStable() {
                    Log.d("TestAppAgent", "Session is stable")
                    future.complete(null)
                }
            },
        )
        action()
        try {
            Log.d("TestAppAgent", "Waiting for session to be stable")
            future.get(5, TimeUnit.SECONDS)
            Log.d("TestAppAgent", "Session is stable")
        } finally {
            session.clearStabilityListener()
        }
    }

    companion object {
        const val SESSION_CREATION_TIMEOUT_SECONDS = 5L
        const val SESSION_CLOSE_TIMEOUT_SECONDS = 30L
    }
}
