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
import android.app.appfunctions.AppFunction
import android.app.appfunctions.AppFunctionManager
import android.app.appfunctions.AppFunctionRegistration
import android.app.appfunctions.ExecuteAppFunctionResponse
import android.app.appfunctions.testutils.DynamicRegistrationActivity.Companion.ACTION_REGISTER_APP_FUNCTION
import android.app.appfunctions.testutils.DynamicRegistrationActivity.Companion.EXTRA_FUNCTION_ID
import android.app.appsearch.GenericDocument
import android.content.Intent
import android.os.Bundle
import android.util.Log

/**
 * Test activity which has same functionality as {@link DynamicRegistrationActivity} but registers
 * different implementation of the App Function. Used for activity-scoped multiregistration tests.
 */
class DynamicRegistrationActivity2 : Activity() {

    private lateinit var manager: AppFunctionManager
    private var registration: AppFunctionRegistration? = null
    private val testAppFunction = AppFunction { request, cancellationSignal, callback ->
        val result: GenericDocument =
            GenericDocument.Builder<GenericDocument.Builder<*>>("", "", "")
                .setPropertyString(
                    ExecuteAppFunctionResponse.PROPERTY_RETURN_VALUE,
                    CUSTOM_PREFIX +
                            request.parameters.getPropertyString("prefix") +
                            request.parameters.getPropertyString("suffix"),
                )
                .build()
        callback.onResult(ExecuteAppFunctionResponse(result))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        manager = getSystemService(AppFunctionManager::class.java)
        logDebugMessage("onCreate")
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        logDebugMessage("onNewIntent")
        handleIntent(intent)
    }

    fun handleIntent(intent: Intent) {
        logDebugMessage("handleIntent")
        val action = intent.action
        if (action == ACTION_REGISTER_APP_FUNCTION) {
            val functionId = intent.getStringExtra(EXTRA_FUNCTION_ID)!!
            registerAppFunction(functionId)
        }
    }

    fun registerAppFunction(functionId: String) {
        try {
            registration = manager.registerAppFunction(
                functionId,
                mainExecutor,
                testAppFunction
            )
            logDebugMessage("successfully registered a function.")
        } catch (e: Exception) {
            logDebugMessage("failed to register function: " + e.message)
            registration = null
        }
    }

     fun logDebugMessage(message: String) {
        Log.d("DynamicActivity2", message)
    }

    override fun onDestroy() {
        super.onDestroy()
        logDebugMessage("onDestroy")
        if (registration != null) {
            registration!!.unregister()
            logDebugMessage("successfully unregistered a function in onDestroy.")
        }
    }

    companion object {
        const val CUSTOM_PREFIX = "Activity2"
    }
}
