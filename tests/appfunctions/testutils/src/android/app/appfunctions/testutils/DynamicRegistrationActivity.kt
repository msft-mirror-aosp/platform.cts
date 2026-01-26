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
 * See the License for the a specific language governing permissions and
 * limitations under the License.
 */

package android.app.appfunctions.testutils

import android.app.Activity
import android.app.appfunctions.AppFunctionManager
import android.app.appfunctions.AppFunctionRegistration
import android.content.Intent
import android.os.Bundle
import android.util.Log
import java.util.UUID

/** An activity that registers an AppFunction based on Intent actions. */
class DynamicRegistrationActivity : Activity() {
    // TODO(b/478873466): add cross app test
    private lateinit var manager: AppFunctionManager
    private var registration: AppFunctionRegistration? = null
    private lateinit var instanceId: String

    val isRegistered: Boolean
        get() = registration != null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instanceId = UUID.randomUUID().toString()
        manager = getSystemService(AppFunctionManager::class.java)
        Log.d(TAG, "[$instanceId] onCreate")
    }

    fun handleIntent(intent: Intent?) {
        if (intent == null) {
            return
        }

        Log.d(TAG, "[$instanceId] handleIntent with action: ${intent.action}")
        when (intent.action) {
            ACTION_REGISTER_APP_FUNCTION -> {
                try {
                    val functionId = intent.getStringExtra(EXTRA_FUNCTION_ID)!!
                    registration = manager.registerAppFunction(
                            functionId,
                            mainExecutor,
                            ConcatStrings()
                    )
                    Log.d(TAG, "[$instanceId] successfully registered a function.")
                } catch (e: Exception) {
                    Log.e(TAG, "[$instanceId] failed to register function.", e)
                    registration = null
                }
            }
            ACTION_UNREGISTER_APP_FUNCTION -> {
                if (registration != null) {
                    registration!!.unregister()
                    registration = null
                    Log.d(TAG, "[$instanceId] successfully unregistered a function.")
                } else {
                    Log.d(TAG, "[$instanceId] couldn't unregister a function, not registered.")
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "[$instanceId] onDestroy")
        if (registration != null) {
            registration!!.unregister()
            Log.d(TAG, "[$instanceId] successfully unregistered a function in onDestroy.")
        }
    }

    companion object {
        private const val TAG = "AppFunctionDynamicRegistrationActivity"
        const val EXTRA_FUNCTION_ID = "FUNCTION_ID"
        const val ACTION_REGISTER_APP_FUNCTION = "REGISTER_APP_FUNCTION"
        const val ACTION_UNREGISTER_APP_FUNCTION = "UNREGISTER_APP_FUNCTION"
    }
}
