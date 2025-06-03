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
import android.app.TaskLocation
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.os.OutcomeReceiver
import android.server.wm.app.Components.MoveTaskToBoundsActivity.ACTION_CHECK_IS_TASK_MOVE_ALLOWED
import android.server.wm.app.Components.MoveTaskToBoundsActivity.ACTION_NOTIFY_TASK_MOVE_ALLOWED_RESULT
import android.server.wm.app.Components.MoveTaskToBoundsActivity.ACTION_NOTIFY_TASK_MOVE_REQUEST_RESULT
import android.server.wm.app.Components.MoveTaskToBoundsActivity.ACTION_REQUEST_TASK_MOVE
import android.server.wm.app.Components.MoveTaskToBoundsActivity.EXTRA_BOUNDS_KEY
import android.server.wm.app.Components.MoveTaskToBoundsActivity.EXTRA_DISPLAY_ID_KEY
import android.server.wm.app.Components.MoveTaskToBoundsActivity.EXTRA_EXCEPTION_KEY
import android.server.wm.app.Components.MoveTaskToBoundsActivity.EXTRA_SYNC_EXCEPTION_KEY
import android.server.wm.app.Components.MoveTaskToBoundsActivity.EXTRA_TASK_MOVE_ALLOWED_RESULT
import android.view.Display
import java.lang.IllegalStateException

class MoveTaskToBoundsActivity : Activity() {

    private val mReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) {
                return
            }
            when (intent.action) {
                ACTION_CHECK_IS_TASK_MOVE_ALLOWED -> {
                    val displayId = intent.getIntExtra(
                        EXTRA_DISPLAY_ID_KEY,
                        getDisplayId()
                    )
                    sendIsTaskMoveAllowed(displayId)
                }
                ACTION_REQUEST_TASK_MOVE -> {
                    val displayId = intent.getIntExtra(
                        EXTRA_DISPLAY_ID_KEY,
                        Display.INVALID_DISPLAY
                    )
                    val bounds: Rect? = intent.getParcelableExtra(
                        EXTRA_BOUNDS_KEY,
                        Rect::class.java
                    )
                    if (bounds != null) {
                        executeTaskMoveRequest(displayId, bounds)
                    }
                }
            }
        }
    }

    private fun executeTaskMoveRequest(displayId: Int, bounds: Rect) {
        val listener = object : OutcomeReceiver<TaskLocation, Exception> {
            override fun onError(e: Exception) {
                val broadcast = Intent(ACTION_NOTIFY_TASK_MOVE_REQUEST_RESULT)
                broadcast.putExtra(EXTRA_EXCEPTION_KEY, e)
                sendBroadcast(broadcast)
            }

            override fun onResult(t: TaskLocation) {
                val broadcast = Intent(ACTION_NOTIFY_TASK_MOVE_REQUEST_RESULT)
                broadcast.putExtra(EXTRA_DISPLAY_ID_KEY, t.displayId)
                broadcast.putExtra(EXTRA_BOUNDS_KEY, t.bounds)
                sendBroadcast(broadcast)
            }
        }

        try {
            val appTask = getAppTask()
            if (appTask == null) {
                val broadcast = Intent(ACTION_NOTIFY_TASK_MOVE_REQUEST_RESULT)
                broadcast.putExtra(
                    EXTRA_SYNC_EXCEPTION_KEY,
                    IllegalStateException("Can't find app task for this activity")
                )
                sendBroadcast(broadcast)
                return
            }
            appTask.moveTaskTo(TaskLocation(displayId, bounds), Runnable::run, listener)
        } catch (e: Exception) {
            val broadcast = Intent(ACTION_NOTIFY_TASK_MOVE_REQUEST_RESULT)
            broadcast.putExtra(EXTRA_SYNC_EXCEPTION_KEY, e)
            sendBroadcast(broadcast)
        }
    }

    private fun sendIsTaskMoveAllowed(displayId: Int) {
        val activityManager = getSystemService(ActivityManager::class.java)
        val broadcast = Intent(ACTION_NOTIFY_TASK_MOVE_ALLOWED_RESULT)
        try {
            val result = activityManager.isTaskMoveAllowedOnDisplay(displayId)
            broadcast.putExtra(EXTRA_TASK_MOVE_ALLOWED_RESULT, result)
        } catch (e: Exception) {
            broadcast.putExtra(EXTRA_SYNC_EXCEPTION_KEY, e)
        }
        sendBroadcast(broadcast)
    }

    private fun getAppTask(): ActivityManager.AppTask? {
        val activityManager = getSystemService(ActivityManager::class.java)
        val appTasks = activityManager.appTasks
        for (task in appTasks) {
            if (task.taskInfo.taskId == taskId) {
                return task
            }
        }
        return null
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addAction(ACTION_CHECK_IS_TASK_MOVE_ALLOWED)
            addAction(ACTION_REQUEST_TASK_MOVE)
        }
        registerReceiver(mReceiver, filter, RECEIVER_EXPORTED)
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(mReceiver)
    }
}
