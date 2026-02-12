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
import android.app.ActivityManager.AppTask
import android.app.ActivityOptions
import android.app.TaskLocation
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.os.OutcomeReceiver
import android.server.wm.app.Components.PinnedWindowingLayerActivity.ACTION_ACTIVITY_FINISHED
import android.server.wm.app.Components.PinnedWindowingLayerActivity.ACTION_RELAUNCH_AS_RESIZABLE
import android.server.wm.app.Components.PinnedWindowingLayerActivity.ACTION_RELAUNCH_AS_RESIZABLE_RESULT
import android.server.wm.app.Components.PinnedWindowingLayerActivity.ACTION_REQUEST_WINDOWING_LAYER
import android.server.wm.app.Components.PinnedWindowingLayerActivity.ACTION_REQUEST_WINDOWING_LAYER_RESULT
import android.server.wm.app.Components.PinnedWindowingLayerActivity.ACTION_REQUEST_TASK_MOVE
import android.server.wm.app.Components.PinnedWindowingLayerActivity.ACTION_TASK_MOVE_RESULT
import android.server.wm.app.Components.PinnedWindowingLayerActivity.EXTRA_BOUNDS
import android.server.wm.app.Components.PinnedWindowingLayerActivity.EXTRA_EXCEPTION
import android.server.wm.app.Components.PinnedWindowingLayerActivity.EXTRA_RESULT_DETAILS
import android.server.wm.app.Components.PinnedWindowingLayerActivity.EXTRA_RESULT_SUCCESS
import android.server.wm.app.Components.PinnedWindowingLayerActivity.EXTRA_WINDOWING_LAYER_TYPE
import android.util.Log

class PinnedWindowingLayerActivity : Activity() {

    private val broadcastReceiver: BroadcastReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    ACTION_REQUEST_WINDOWING_LAYER -> {
                        val layerType = intent.getIntExtra(EXTRA_WINDOWING_LAYER_TYPE, -1)
                        requestWindowingLayer(layerType)
                    }
                    ACTION_RELAUNCH_AS_RESIZABLE -> {
                        relaunchAsResizable()
                    }
                    ACTION_REQUEST_TASK_MOVE -> {
                        val bounds = intent.getParcelableExtra(EXTRA_BOUNDS, Rect::class.java)
                        executeTaskMoveRequest(bounds!!)
                    }
                }
            }
        }

    override fun onStart() {
        super.onStart()
        registerReceiver(
            broadcastReceiver,
            IntentFilter().apply {
                addAction(ACTION_REQUEST_WINDOWING_LAYER)
                addAction(ACTION_RELAUNCH_AS_RESIZABLE)
                addAction(ACTION_REQUEST_TASK_MOVE)
            },
            RECEIVER_EXPORTED,
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            sendBroadcast(Intent(ACTION_ACTIVITY_FINISHED))
        }
        unregisterReceiver(broadcastReceiver)
    }

    private fun requestWindowingLayer(type: Int) {
        Log.d(TAG, "requestWindowingLayer: type=$type")
        try {
            val appTask = getAppTask()
            appTask.requestWindowingLayer(type, mainExecutor, createApiOutcomeReceiver(type))
        } catch (e: Exception) {
            Log.e(TAG, "requestWindowingLayer error: msg=${e.message}")
            sendResultBroadcast(false, type, "requestWindowingLayer: ${e.message}")
        }
    }

    private fun relaunchAsResizable() {
        Log.d(TAG, "relaunchAsResizable")
        val intent = Intent(this, PinnedWindowingLayerActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        val options = ActivityOptions.makeBasic()
        options.launchDisplayId = displayId
        options.isMovableTaskRequired = true

        try {
            startActivity(intent, options.toBundle())
            sendRelaunchAsResizableResultBroadcast()
            Log.d(TAG, "started new activity as resizable task, finishing the original activity")
            finish()
        } catch (e: Exception) {
            Log.w(TAG, "relaunchAsResizable error: msg=${e.message}")
            sendRelaunchAsResizableResultBroadcast(e)
        }
    }

    private fun executeTaskMoveRequest(bounds: Rect?) {
        val listener = object : OutcomeReceiver<TaskLocation, Exception> {
            override fun onError(e: Exception) {
                Log.e(TAG, "moveTaskTo error: msg=${e.message}")
                sendTaskMoveResultBroadcast(e)
            }

            override fun onResult(t: TaskLocation) {
                Log.d(TAG, "moveTaskTo success")
                sendTaskMoveResultBroadcast()
            }
        }

        try {
            checkNotNull(bounds) { "Bounds must be provided for task move request." }
            Log.d(TAG, "moveTaskTo: bounds=$bounds, displayId=$displayId")
            val appTask = getAppTask()
            appTask.moveTaskTo(TaskLocation(displayId, bounds), mainExecutor, listener)
        } catch (e: Exception) {
            sendTaskMoveResultBroadcast(e)
        }
    }

    private fun createApiOutcomeReceiver(type: Int) =
        object : OutcomeReceiver<Int, Exception> {
            override fun onResult(result: Int?) {
                Log.d(TAG, "onResult: ${returnCodeAsString(result)}")
                when (result) {
                    AppTask.WINDOWING_LAYER_REQUEST_GRANTED -> sendResultBroadcast(true, type)
                    else ->
                        sendResultBroadcast(
                            false,
                            type,
                            "rejected with code ${returnCodeAsString(result)}",
                        )
                }
            }

            override fun onError(error: Exception) {
                Log.e(TAG, "onError: msg=${error.message}")
                sendResultBroadcast(false, type, "callback.onError: msg=${error.message}", error)
            }
        }

    private fun sendResultBroadcast(
        success: Boolean,
        layer: Int,
        details: String? = null,
        error: Exception? = null,
    ) {
        val broadcast = Intent(ACTION_REQUEST_WINDOWING_LAYER_RESULT)
        broadcast.putExtra(EXTRA_RESULT_SUCCESS, success)
        broadcast.putExtra(EXTRA_WINDOWING_LAYER_TYPE, layer)
        if (details != null) {
            broadcast.putExtra(EXTRA_RESULT_DETAILS, details)
        }
        if (error != null) {
            broadcast.putExtra(EXTRA_EXCEPTION, error)
        }
        sendBroadcast(broadcast)
    }

    private fun sendRelaunchAsResizableResultBroadcast(error: Exception? = null) {
        val broadcast = Intent(ACTION_RELAUNCH_AS_RESIZABLE_RESULT)
        if (error != null) {
            broadcast.putExtra(EXTRA_EXCEPTION, error)
            broadcast.putExtra(
                EXTRA_RESULT_DETAILS,
                "failed to start activity with movable flag. msg=${error.message}",
            )
        }
        sendBroadcast(broadcast)
    }

    public fun sendTaskMoveResultBroadcast(error: Exception? = null) {
        val broadcast = Intent(ACTION_TASK_MOVE_RESULT)
        if (error != null) {
            broadcast.putExtra(EXTRA_EXCEPTION, error)
        }
        sendBroadcast(broadcast)
    }

    private fun getAppTask(): AppTask {
        val activityManager: ActivityManager = getSystemService(ActivityManager::class.java)
        val appTasks = activityManager.getAppTasks()
        return checkNotNull(appTasks.filter { it.taskInfo.taskId == taskId }.firstOrNull()) {
            "AppTask with taskId $taskId not found."
        }
    }

    companion object {
        private const val TAG = "PinnedWindowingLayerActivity"

        private fun returnCodeAsString(code: Int?) =
            when (code) {
                AppTask.WINDOWING_LAYER_REQUEST_GRANTED -> "GRANTED"
                AppTask.WINDOWING_LAYER_REQUEST_REJECTED -> "REJECTED"
                else -> "Urecognized code=$code"
            }
    }
}
