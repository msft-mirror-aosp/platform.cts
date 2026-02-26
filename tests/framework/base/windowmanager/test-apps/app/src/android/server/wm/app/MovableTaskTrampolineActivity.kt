/*
 * Copyright (C) 2026 The Android Open Source Project
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
import android.app.ActivityOptions
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.server.wm.app.Components.MovableTaskTrampolineActivity.ACTION_NOTIFY_START_ACTIVITY_WITH_MOVABLE_FLAG_RESULT
import android.server.wm.app.Components.MovableTaskTrampolineActivity.ACTION_START_ACTIVITY_WITH_MOVABLE_FLAG
import android.server.wm.app.Components.MovableTaskTrampolineActivity.EXTRA_ACTIVITY_NAME_KEY
import android.server.wm.app.Components.MovableTaskTrampolineActivity.EXTRA_DISPLAY_ID_KEY
import android.server.wm.app.Components.MovableTaskTrampolineActivity.EXTRA_SYNC_EXCEPTION_KEY

class MovableTaskTrampolineActivity : Activity() {

    private val mReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
            ACTION_START_ACTIVITY_WITH_MOVABLE_FLAG -> {
                val displayId = intent.getIntExtra(EXTRA_DISPLAY_ID_KEY, getDisplayId())
                val activityName = intent.getParcelableExtra(
                    EXTRA_ACTIVITY_NAME_KEY,
                    ComponentName::class.java
                )!!
                launchWithMovableTaskRequired(activityName, displayId)
                finishAffinity()
            }
        }
        }
    }

    override fun onStart() {
        super.onStart()
        val intentFilter = IntentFilter().apply {
            addAction(ACTION_START_ACTIVITY_WITH_MOVABLE_FLAG)
        }
        registerReceiver(mReceiver, intentFilter, RECEIVER_EXPORTED)
    }

    override fun onStop() {
        unregisterReceiver(mReceiver)
        super.onStop()
    }

    private fun launchWithMovableTaskRequired(activityName: ComponentName, displayId: Int) {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.component = activityName
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)

        val options = ActivityOptions.makeBasic()
        options.launchDisplayId = displayId
        options.isMovableTaskRequired = true

        val broadcast = Intent(ACTION_NOTIFY_START_ACTIVITY_WITH_MOVABLE_FLAG_RESULT)

        try {
            startActivity(intent, options.toBundle())
        } catch (e: Exception) {
            broadcast.putExtra(EXTRA_SYNC_EXCEPTION_KEY, e)
        }

        sendBroadcast(broadcast)
    }
}
