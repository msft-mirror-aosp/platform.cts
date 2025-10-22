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

package android.companion.cts.common

import android.app.Activity
import android.app.HandoffActivityData
import android.app.HandoffActivityDataRequestInfo
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.os.Bundle
import android.os.PersistableBundle
import android.util.Log
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** An [Activity] for testing Handoff of a simple integer between devices. */
class HandoffActivity : Activity() {

    // A number which will be reported as part of onHandoffActivityDataRequested.
    private var handoffData: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "$this.onCreate()")
        super.onCreate(savedInstanceState)
        handoffData = intent.getIntExtra(HANDOFF_DATA_KEY, 0)
        Log.d(TAG, "handoffData: $handoffData")
        currentInstance = this
        setHandoffEnabled(true, null)
        Log.d(TAG, "Handoff enabled")
    }

    override fun onDestroy() {
        Log.d(TAG, "$this.onDestroy()")
        currentInstance = null
        super.onDestroy()
    }

    override fun onHandoffActivityDataRequested(
        request: HandoffActivityDataRequestInfo
    ): HandoffActivityData? {
        val bundle = PersistableBundle()
        bundle.putInt(HANDOFF_DATA_KEY, handoffData)
        return HandoffActivityData.Builder(this.componentName).setExtras(bundle).build()
    }

    companion object {

        private const val HANDOFF_DATA_KEY = "handoffData"

        private var currentInstance: HandoffActivity? = null

        /**
         * Wait for an instance of HandoffActivity to appear locally, and return its [handoffData].
         */
        fun waitForHandoff(): Int? {
            return waitForResult(timeout = 3.seconds, interval = 100.milliseconds) {
                currentInstance?.takeIf { it.isResumed }?.handoffData
            } ?: error("HandoffActivity has not appeared")
        }

        /** Launch a HandoffActivity instance with [handoffData] and wait for it to appear. */
        fun launchHandoffActivity(context: Context, handoffData: Int): Int {
            val intent = Intent(context, HandoffActivity::class.java)
            intent.addFlags(FLAG_ACTIVITY_NEW_TASK)
            intent.putExtra(HANDOFF_DATA_KEY, handoffData)
            context.startActivity(intent)

            return waitForResult(timeout = 3.seconds, interval = 100.milliseconds) {
                currentInstance?.takeIf { it.isResumed }?.taskId
            } ?: error("HandoffActivity has not appeared")
        }
    }
}
