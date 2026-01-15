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

package android.server.wm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.server.wm.ActivityManagerTestBase
import android.server.wm.BuildUtils
import android.server.wm.StateLogger.logAlways
import android.server.wm.app.Components
import android.server.wm.app.Components.PinnedWindowingLayerActivity.ACTION_ACTIVITY_FINISHED
import android.server.wm.app.Components.PinnedWindowingLayerActivity.ACTION_REQUEST_WINDOWING_LAYER_RESULT
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Before

public abstract class WindowingLayerTestBase : ActivityManagerTestBase() {

    private val TIMEOUT_MS = 5000L * BuildUtils.HW_TIMEOUT_MULTIPLIER

    private val broadcastsReceived: ConcurrentHashMap<String, Channel<Intent>> =
        ConcurrentHashMap<String, Channel<Intent>>()
    private val broadcastIntentFilter =
        IntentFilter().apply {
            addAction(ACTION_REQUEST_WINDOWING_LAYER_RESULT)
            addAction(ACTION_ACTIVITY_FINISHED)
        }
    private val broadcastReceiver: BroadcastReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                logAlways("Received an intent with action ${intent.action}")
                intent.action ?: return
                getBroadcastChannel(intent.action!!).trySend(intent)
            }
        }

    @Before
    override fun setUp() {
        super.setUp()
        broadcastsReceived.clear()
        mContext.registerReceiver(
            broadcastReceiver,
            broadcastIntentFilter,
            Context.RECEIVER_EXPORTED,
        )
    }

    @After
    fun tearDown() {
        mContext.unregisterReceiver(broadcastReceiver)
        Components.forceStopPackage()
    }

    suspend fun awaitBroadcast(action: String): Intent? {
        return withTimeoutOrNull(TIMEOUT_MS) { getBroadcastChannel(action).receive() }
    }

    private fun getBroadcastChannel(action: String): Channel<Intent> {
        return broadcastsReceived.getOrPut(action) { Channel(Channel.UNLIMITED) }
    }
}
