/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.server.wm.app

import android.app.Activity
import android.app.ActivityManager
import android.app.TaskDisplayPolicyState
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.server.wm.app.Components.TaskMoveAllowedListenerActivity.ACTION_CHECK_IS_TASK_MOVE_ALLOWED
import android.server.wm.app.Components.TaskMoveAllowedListenerActivity.ACTION_NOTIFY_LISTENER_CALLED
import android.server.wm.app.Components.TaskMoveAllowedListenerActivity.ACTION_NOTIFY_TASK_MOVE_ALLOWED_RESULT
import android.server.wm.app.Components.TaskMoveAllowedListenerActivity.ACTION_REGISTER_LISTENER
import android.server.wm.app.Components.TaskMoveAllowedListenerActivity.ACTION_REGISTER_LISTENER_ACK
import android.server.wm.app.Components.TaskMoveAllowedListenerActivity.ACTION_UNREGISTER_LISTENER
import android.server.wm.app.Components.TaskMoveAllowedListenerActivity.ACTION_UNREGISTER_LISTENER_ACK
import android.server.wm.app.Components.TaskMoveAllowedListenerActivity.EXTRA_DISPLAY_ID_KEY
import android.server.wm.app.Components.TaskMoveAllowedListenerActivity.EXTRA_TASK_MOVE_ALLOWED_RESULT
import android.server.wm.app.Components.TaskMoveAllowedListenerActivity.EXTRA_TMA_KEYS_ARRAY_KEY
import android.server.wm.app.Components.TaskMoveAllowedListenerActivity.EXTRA_TMA_VALUES_ARRAY_KEY
import java.util.function.Consumer

class TaskMoveAllowedListenerActivity : Activity() {

    private val mListener = Consumer<List<TaskDisplayPolicyState>> { displayPolicyStates ->
        val size = displayPolicyStates.size
        val keys = IntArray(size)
        val values = BooleanArray(size)
        displayPolicyStates.forEachIndexed { i, state ->
            keys[i] = state.displayId
            values[i] = state.taskMoveState == TaskDisplayPolicyState.TASK_MOVE_ALLOWED
        }

        sendBroadcast(Intent(ACTION_NOTIFY_LISTENER_CALLED).apply {
            putExtra(EXTRA_TMA_KEYS_ARRAY_KEY, keys)
            putExtra(EXTRA_TMA_VALUES_ARRAY_KEY, values)
        })
    }

    private val mReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_CHECK_IS_TASK_MOVE_ALLOWED -> {
                    val displayId = intent.getIntExtra(
                        EXTRA_DISPLAY_ID_KEY,
                        getDisplayId()
                    )
                    sendIsTaskMoveAllowed(displayId)
                }
                ACTION_REGISTER_LISTENER -> {
                    getSystemService(
                        ActivityManager::class.java
                    ).registerTaskDisplayPolicyStateListener(Runnable::run, mListener)
                    sendBroadcast(Intent(ACTION_REGISTER_LISTENER_ACK))
                }
                ACTION_UNREGISTER_LISTENER -> {
                    getSystemService(
                        ActivityManager::class.java
                    ).unregisterTaskDisplayPolicyStateListener(mListener)
                    sendBroadcast(Intent(ACTION_UNREGISTER_LISTENER_ACK))
                }
            }
        }
    }

    private fun sendIsTaskMoveAllowed(displayId: Int) {
        val activityManager = getSystemService(ActivityManager::class.java)
        val broadcast = Intent(ACTION_NOTIFY_TASK_MOVE_ALLOWED_RESULT)
        val result = activityManager.isTaskMoveAllowedOnDisplay(displayId)
        broadcast.putExtra(EXTRA_TASK_MOVE_ALLOWED_RESULT, result)
        sendBroadcast(broadcast)
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addAction(ACTION_CHECK_IS_TASK_MOVE_ALLOWED)
            addAction(ACTION_REGISTER_LISTENER)
            addAction(ACTION_UNREGISTER_LISTENER)
        }
        registerReceiver(mReceiver, filter, RECEIVER_EXPORTED)
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(mReceiver)
    }
}
