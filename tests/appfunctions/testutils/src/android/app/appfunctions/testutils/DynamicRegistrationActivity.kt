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
    lateinit var manager: AppFunctionManager
    private var registration: AppFunctionRegistration? = null
    private lateinit var instanceId: String

    val isRegistered: Boolean
        get() = registration != null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instanceId = UUID.randomUUID().toString()
        manager = getSystemService(AppFunctionManager::class.java)
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    fun handleIntent(intent: Intent) {
        val action = intent.action
        if (action == ACTION_REGISTER_APP_FUNCTION) {
            logDebugMessage("handle registration intent")
            val functionId = intent.getStringExtra(EXTRA_FUNCTION_ID)!!
            registerAppFunction(functionId)
        }
    }

    fun registerAppFunction(functionId: String) {
        try {
            registration = manager.registerAppFunction(
                functionId,
                mainExecutor,
                ConcatStrings()
            )
            logDebugMessage("successfully registered a function.")
        } catch (e: Exception) {
            logDebugMessage("failed to register function: " + e.message)
            registration = null
        }
    }

    fun unregisterAppFunction(numTimes: Int = 1) {
        if (registration != null) {
            for (i in 0 until numTimes) {
                registration!!.unregister()
            }
            registration = null
            logDebugMessage("successfully unregistered a function.")
        } else {
            logDebugMessage("couldn't unregister a function, not registered.")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        logDebugMessage("onDestroy")
        if (registration != null) {
            registration!!.unregister()
            logDebugMessage("successfully unregistered a function in onDestroy.")
        }
    }

    fun logDebugMessage(message: String) {
        Log.d("DynamicActivity", "[$instanceId] " + message)
    }

    companion object {
        const val ACTION_REGISTER_APP_FUNCTION = "android.cts.appfunctions.REGISTER_APP_FUNCTION"
        const val EXTRA_FUNCTION_ID = "FUNCTION_ID"
    }
}
