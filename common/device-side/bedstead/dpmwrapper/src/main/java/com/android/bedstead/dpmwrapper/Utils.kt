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
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.UserHandle
import android.os.UserManager
import android.util.Log
import com.android.internal.annotations.GuardedBy
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer

/** Generic helpers. */
class Utils private constructor() {
    init {
        throw UnsupportedOperationException("contains only static methods")
    }

    companion object {
        private const val TAG = "DpmWrapperUtils"

        const val VERBOSE: Boolean = false

        @JvmField val MY_USER_ID: Int = UserHandle.myUserId()

        const val ACTION_WRAPPED_MANAGER_CALL: String =
            "com.android.bedstead.dpmwrapper.action.WRAPPED_MANAGER_CALL"
        const val EXTRA_CLASS: String = "className"
        const val EXTRA_METHOD: String = "methodName"
        const val EXTRA_NUMBER_ARGS: String = "number_args"
        const val EXTRA_ARG_PREFIX: String = "arg_"

        private val LOCK = Any()

        @GuardedBy("LOCK") private var sHandlerThread: HandlerThread? = null

        @GuardedBy("LOCK") private var sHandler: Handler? = null

        @JvmStatic
        val isHeadlessSystemUserMode: Boolean
            get() =
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    UserManager.isHeadlessSystemUserMode()

        @JvmStatic
        val isHeadlessSystemUser: Boolean
            get() = isHeadlessSystemUserMode && MY_USER_ID == UserHandle.USER_SYSTEM

        @JvmStatic
        fun isCurrentUserOnHeadlessSystemUser(context: Context): Boolean {
            return isHeadlessSystemUserMode &&
                context.getSystemService(UserManager::class.java)?.isUserForeground ?: false
        }

        @SuppressLint("MissingPermission")
        @JvmStatic
        fun assertCurrentUserOnHeadlessSystemMode(context: Context) {
            if (isCurrentUserOnHeadlessSystemUser(context)) return

            throw IllegalStateException(
                ("Should only be called by current user (" +
                    ActivityManager.getCurrentUser() +
                    ") on headless system user device, but was " +
                    "called by process from user " +
                    MY_USER_ID)
            )
        }

        @JvmStatic
        fun toString(filter: IntentFilter): String? {
            val builder = StringBuilder("[")
            filter
                .actionsIterator()
                .forEachRemaining(Consumer { s: String? -> builder.append(s).append(",") })
            builder.deleteCharAt(builder.length - 1)
            return builder.append(']').toString()
        }

        @JvmStatic
        val handler: Handler
            get() {
                synchronized(LOCK) {
                    if (sHandler == null) {
                        sHandlerThread = HandlerThread("DpmWrapperHandlerThread")
                        Log.i(TAG, "Starting handler thread $sHandlerThread")
                        sHandlerThread!!.start()
                        sHandler = Handler(sHandlerThread!!.getLooper())
                    }
                }
                return sHandler!!
            }

        @Throws(Exception::class)
        @JvmStatic
        fun <T> callOnHandlerThread(callable: Callable<T?>): T? {
            if (VERBOSE) Log.v(TAG, "callOnHandlerThread(): called from " + Thread.currentThread())

            val latch = CountDownLatch(1)
            val returnRef = AtomicReference<T?>()
            val exceptionRef = AtomicReference<Exception?>()

            handler.post(
                Runnable {
                    Log.d(TAG, "Calling callable on handler thread " + Thread.currentThread())
                    try {
                        val result = callable.call()
                        if (VERBOSE) Log.v(TAG, "Got result: $result")
                        returnRef.set(result)
                    } catch (e: Exception) {
                        Log.e(TAG, "Got exception: $e")
                        exceptionRef.set(e)
                    } finally {
                        latch.countDown()
                    }
                }
            )

            if (!latch.await(50, TimeUnit.SECONDS)) {
                throw TimeoutException("didn't get result in 50 seconds")
            }

            val exception = exceptionRef.get()
            if (exception != null) throw exception

            return returnRef.get()
        }

        /** Gets a more detailed description of an intent (for example, including extras). */
        @JvmStatic
        fun toString(intent: Intent): String? {
            val builder = StringBuilder("[Intent: action=")
            val action = intent.action
            if (action == null) {
                builder.append("null")
            } else {
                builder.append(action)
            }
            val categories = intent.categories
            if (categories == null || categories.isEmpty()) {
                builder.append(", no_categories")
            } else {
                builder
                    .append(", ")
                    .append(categories.size)
                    .append(" categories: ")
                    .append(categories)
            }
            val extras = intent.extras
            builder.append(", ")
            if (extras == null || extras.isEmpty()) {
                builder.append("no_extras")
            } else {
                appendBundleExtras(builder, extras)
            }
            return builder.append(']').toString()
        }

        fun appendBundleExtras(builder: StringBuilder, bundle: Bundle) {
            builder.append(bundle.size()).append(" extras: ")
            bundle
                .keySet()
                .forEach(
                    Consumer { key: String? ->
                        builder.append(key).append('=').append(bundle.get(key)).append(',')
                    }
                )
            builder.deleteCharAt(builder.length - 1)
        }
    }
}
