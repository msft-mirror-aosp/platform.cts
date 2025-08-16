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
package android.contextualsearch.caller

import android.app.Activity
import android.app.contextualsearch.ContextualSearchManager
import android.os.Bundle
import android.util.Log
import com.android.compatibility.common.util.BroadcastMessenger
import java.util.concurrent.CountDownLatch

class BackgroundCallerActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")
        WATCHER?.instance = this
        WATCHER?.created?.countDown()
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume")
        BroadcastMessenger.send(
            this,
            RESUMED_TAG,
            ContextualSearchMessage(ContextualSearchMessage.RESULT_OK)
        )
        WATCHER?.resumed?.countDown()
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "onStop")
        val manager = getSystemService(ContextualSearchManager::class.java)
        try {
            manager.startContextualSearch(this, null)
            BroadcastMessenger.send(
                this,
                ContextualSearchMessage.TAG,
                ContextualSearchMessage(ContextualSearchMessage.RESULT_OK)
            )
        } catch (e: SecurityException) {
            BroadcastMessenger.send(
                this,
                ContextualSearchMessage.TAG,
                ContextualSearchMessage(ContextualSearchMessage.RESULT_EXCEPTION)
            )
        }
        finish()
    }

    companion object {
        private val TAG = BackgroundCallerActivity::class.java.simpleName
        val RESUMED_TAG = "$TAG.RESUMED"
        var WATCHER: Watcher? = null
            set(value) {
                if (field == null) {
                    Log.d(TAG, "setting WATCHER.")
                } else {
                    if (value != null) {
                        throw IllegalStateException("WATCHER already set. Cannot set again.")
                    } else {
                        Log.d(TAG, "clearing WATCHER.")
                    }
                }
                field = value
            }
    }

    class Watcher {
        var created = CountDownLatch(1)
        var resumed = CountDownLatch(1)
        var instance: BackgroundCallerActivity? = null
    }
}
