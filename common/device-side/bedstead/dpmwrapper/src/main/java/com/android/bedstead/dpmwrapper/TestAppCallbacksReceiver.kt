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
package com.android.bedstead.dpmwrapper

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.HandlerThread
import android.os.UserHandle
import android.util.ArrayMap
import android.util.Log
import com.android.bedstead.dpmwrapper.Utils.Companion.assertCurrentUserOnHeadlessSystemMode
import com.android.internal.annotations.GuardedBy
import java.util.function.Consumer

/**
 * [BroadcastReceiver] used in the test apps to receive intents that were originally sent to the
 * device owner's [android.app.admin.DeviceAdminReceiver].
 *
 * It must be declared in the manifest:
 * <pre>`
 * <receiver android:name="com.android.bedstead.dpmwrapper.TestAppCallbacksReceiver" android:exported="true"></receiver>
 * `</pre>
 */
class TestAppCallbacksReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, " received intent on user " + context.userId + ": " + intent)
        assertCurrentUserOnHeadlessSystemMode(context)
        setHandlerThread()

        val realIntent = intent.getParcelableExtra(EXTRA, Intent::class.java)
        if (realIntent == null) {
            Log.e(TAG, "No $EXTRA on intent $intent")
            return
        }
        val action = realIntent.action
        val receivers: ArrayList<BroadcastReceiver?>?
        synchronized(LOCK) { receivers = sRealReceivers.get(action) }
        if (receivers == null || receivers.isEmpty()) {
            Log.e(TAG, "onReceive(): no receiver for $action: $sRealReceivers")
            return
        }
        Log.d(TAG, "Will dispatch intent to " + receivers.size + " on handler thread")
        receivers.forEach(
            Consumer { r: BroadcastReceiver? ->
                sHandler!!.post(Runnable { handleDispatchIntent(r!!, context, realIntent) })
            }
        )
    }

    private fun handleDispatchIntent(
        receiver: BroadcastReceiver,
        context: Context?,
        intent: Intent?,
    ) {
        Log.d(
            TAG,
            ("Dispatching " + intent + " to " + receiver + " on thread " + Thread.currentThread()),
        )
        receiver.onReceive(context, intent)
    }

    companion object {
        private val TAG: String = TestAppCallbacksReceiver::class.java.getSimpleName()
        private const val EXTRA = "relayed_intent"

        private val LOCK = Any()
        private var sHandlerThread: HandlerThread? = null
        private var sHandler: Handler? = null

        /** Map of receivers per intent action. */
        @GuardedBy("LOCK")
        private val sRealReceivers = ArrayMap<String?, ArrayList<BroadcastReceiver?>>()

        private fun setHandlerThread() {
            if (sHandlerThread != null) return

            sHandlerThread = HandlerThread("TestAppCallbacksReceiverThread")
            Log.i(
                TAG,
                "Starting thread " + sHandlerThread + " on user " + Utils.Companion.MY_USER_ID,
            )
            sHandlerThread!!.start()
            sHandler = Handler(sHandlerThread!!.getLooper())
        }

        fun registerReceiver(
            context: Context?,
            receiver: BroadcastReceiver?,
            filter: IntentFilter,
        ) {
            if (Utils.Companion.VERBOSE) {
                Log.v(TAG, "registerReceiver(): $receiver")
            }
            synchronized(LOCK) {
                filter
                    .actionsIterator()
                    .forEachRemaining(
                        Consumer { action: String? ->
                            Log.d(TAG, "Registering $receiver for $action")
                            var receivers: ArrayList<BroadcastReceiver?>? =
                                sRealReceivers.get(action)
                            if (receivers == null) {
                                receivers = ArrayList<BroadcastReceiver?>()
                                if (Utils.Companion.VERBOSE) {
                                    Log.v(TAG, "Creating list of receivers for $action")
                                }
                                sRealReceivers.put(action, receivers)
                            }
                            receivers.add(receiver)
                        }
                    )
            }
        }

        fun unregisterReceiver(context: Context?, receiver: BroadcastReceiver?) {
            if (Utils.Companion.VERBOSE) {
                Log.v(TAG, "unregisterReceiver(): $receiver")
            }

            synchronized(LOCK) {
                for (i in 0..<sRealReceivers.size) {
                    val action: String? = sRealReceivers.keyAt(i)
                    val receivers: ArrayList<BroadcastReceiver?> = sRealReceivers.valueAt(i)
                    val removed = receivers.remove(receiver)
                    if (removed) {
                        Log.d(TAG, "Removed $receiver for action $action")
                    }
                }
            }
        }

        @SuppressLint("MissingPermission")
        fun sendBroadcast(context: Context?, intent: Intent?) {
            val currentUserId = ActivityManager.getCurrentUser()
            val bridgeIntent =
                Intent(context, TestAppCallbacksReceiver::class.java).putExtra(EXTRA, intent)
            Log.d(
                TAG,
                ("Relaying " +
                    intent +
                    " from user " +
                    Utils.Companion.MY_USER_ID +
                    " to user " +
                    currentUserId +
                    " using " +
                    bridgeIntent),
            )
            context?.sendBroadcastAsUser(bridgeIntent, UserHandle.of(currentUserId))
        }
    }
}
