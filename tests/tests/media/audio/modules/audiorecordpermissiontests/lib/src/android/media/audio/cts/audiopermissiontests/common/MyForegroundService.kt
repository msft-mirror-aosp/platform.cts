/*
 * Copyright 2024 The Android Open Source Project
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

package android.media.audio.cts.audiopermissiontests.common

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.util.Log

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.ReceiveChannel


/**
 * Consume the data in the channel, and based on the current playback state return the next
 * state. Returns null to represent ending the task. If not curState, block until new data is
 * available in the channel
 */
suspend fun computeNextState(
    channel: ReceiveChannel<Boolean>,
    curState: Boolean
): Boolean? =
    channel.tryReceive().run {
        when {
            isClosed -> null
            // no update: only wait for state update if we are NOT playback
            isFailure ->
                curState ||
                    try {
                        channel.receive()
                    } catch (e: ClosedReceiveChannelException) {
                        return null
                    }
            // This shouldn't throw now. Non-blocking read of the playback state
            else -> getOrThrow()
        }
}


open class MyForegroundService : Service() {
    val TAG = getAppName() + "T"
    val PREFIX = "android.media.audio.cts." + getAppName()

    protected val mJob =
        SupervisorJob().apply {
            // Completer on the parent job for all coroutines, so test app is informed that teardown
            // completes
            invokeOnCompletion {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                respond(ACTION_TEARDOWN_FINISHED)
            }
        }
    private val handler = object : AbstractCoroutineContextElement(CoroutineExceptionHandler),
            CoroutineExceptionHandler {
        override fun handleException(context: CoroutineContext, exception: Throwable) =
                Log.wtf(TAG, "Uncaught exception", exception).let{}
    }
    // Parent scope executes on the main thread
    protected val mScope = CoroutineScope(mJob + Dispatchers.Main.immediate + handler)

    override fun onDestroy() {
        Log.i(TAG, "onDestroy")
        mJob.cancel()
    }

    // Binding cannot be used since that affects the proc state
    override fun onBind(intent: Intent): IBinder? = null

    /** For subclasses to return the package name for receiving intents. */
    open fun getAppName(): String = "Base"

    /** For subclasses to return the capabilities to start the service with. */
    open fun getCapabilities(): Int = 0

    protected fun respond(action: String) {
        Log.i(TAG, "Sending $action")
        sendBroadcast(
            Intent(PREFIX + action).apply {
                setPackage(TARGET_PACKAGE)
            })
    }

    /** Create a notification which is required to start a foreground service */
    protected fun buildNotification(msg: String): Notification {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        manager.createNotificationChannel(
            NotificationChannel("all", "All Notifications", NotificationManager.IMPORTANCE_NONE))

        return Notification.Builder(this, "all")
            .setContentTitle("$msg")
            .setContentText("$msg...")
            .setSmallIcon(R.drawable.ic_fg)
            .build()
    }
}

