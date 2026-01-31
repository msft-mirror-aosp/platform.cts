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
import android.os.SystemProperties
import android.util.Log
import android.util.Size
import android.view.accessibility.AccessibilityWindowInfo
import com.android.extensions.computercontrol.ComputerControlSession
import java.lang.reflect.Proxy
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

class TestAppAgent(private val context: Context, private val session: ComputerControlSession) :
    AutoCloseable {
    private val interactionQueue = Channel<Interaction>(Channel.UNLIMITED)
    private var interactionReceiver: InteractionReceiver? = null
    private var testAppFocusRequester: TestAppFocusRequester? = null
    private val sessionCloseFuture = CompletableFuture<Void>()

    /** Lifecycle callback that can be installed on the test agent. */
    var lifecycleCallback: AtomicReference<ComputerControlSession.LifecycleCallback?> =
        AtomicReference(null)

    constructor(
        context: Context,
        session: ComputerControlSession,
        packageName: String,
        className: String? = null,
    ) : this(context, session) {
        launchApplication(packageName, className)
    }

    init {
        val proxyLifecycleCallback = polledDelegate { lifecycleCallback.get() }
        session.setLifecycleCallback(
            Executors.newSingleThreadExecutor(),
            object : ComputerControlSession.LifecycleCallback by proxyLifecycleCallback {
                override fun onClosed(reason: Int) {
                    proxyLifecycleCallback.onClosed(reason)
                    sessionCloseFuture.complete(null)
                }
            },
        )
        Log.d("TestAppAgent", "TestAppAgent initialized")
    }

    override fun close() {
        session.close()
        // Wait for the onClosed() callback to be invoked.
        sessionCloseFuture.get(SESSION_CLOSE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    }

    fun launchApplication(packageName: String, className: String? = null) {
        Log.d("TestAppAgent", "Launching application: $packageName")
        actAndWaitForStable {
            if (className != null) {
                session.launchApplication(ComponentName(packageName, className))
                Log.d("TestAppAgent", "Launched application: $className")
            } else {
                session.launchApplication(packageName)
                Log.d("TestAppAgent", "Launched application: $packageName")
            }
        }

        interactionReceiver = InteractionReceiver(context)
        interactionReceiver!!.bind { interaction ->
            Log.d("TestAppAgent", "Interaction received")
            interactionQueue.trySend(interaction)
        }
        testAppFocusRequester = TestAppFocusRequester(context)

        // TODO: look into how to get rid of this sleep for interaction receiver
        // binding.
        Thread.sleep(1000)
    }

    fun handOverApplications() {
        session.handOverApplications()
    }

    fun requestFocus(textFieldId: String) {
        testAppFocusRequester!!.requestFocus(textFieldId)
        // TODO: look into how to get rid of this sleep for requesting focus.
        Thread.sleep(1000)
    }

    // When session is closed, tap is no op, and no need to wait for stability.
    fun noOpTap() {
        session.tap(0, 0)
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

    fun getAccessibilityWindows(): List<AccessibilityWindowInfo> {
        return session.getAccessibilityWindows()
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
        try {
            action()
            Log.d("TestAppAgent", "Waiting for session to be stable")
            future.get(5, TimeUnit.SECONDS)
            Log.d("TestAppAgent", "Session is stable")
        } finally {
            session.clearStabilityListener()
        }
    }

    companion object {
        private val HW_TIMEOUT_MULTIPLIER = SystemProperties.getInt("ro.hw_timeout_multiplier", 1)

        const val SESSION_CREATION_TIMEOUT_SECONDS = 5L

        // TODO: b/454903475 - Reduce this timeout once the bug is fixed.
        val SESSION_CLOSE_TIMEOUT_SECONDS = 120L * HW_TIMEOUT_MULTIPLIER

        /**
         * Creates a proxy object of type T that polls the [provider] every time on each method
         * invocation.
         */
        private inline fun <reified T : Any> polledDelegate(crossinline provider: () -> T?): T {
            return Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) {
                _,
                method,
                args ->
                // Poll the provider on each invocation.
                provider()?.let { method.invoke(it, *(args ?: arrayOf())) }
            } as T
        }
    }
}
