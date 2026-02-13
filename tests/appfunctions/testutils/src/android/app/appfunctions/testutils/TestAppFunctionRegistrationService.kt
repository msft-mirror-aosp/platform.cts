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

package android.app.appfunctions.testutils

import android.app.Service
import android.app.appfunctions.AppFunction
import android.app.appfunctions.AppFunctionManager
import android.app.appfunctions.AppFunctionRegistration
import android.app.appfunctions.RegisterAppFunctionRequest
import android.content.Intent
import android.os.IBinder
import android.util.Log

/** A service that runs in a separate process to register AppFunctions. */
class TestAppFunctionRegistrationService : Service() {
    private lateinit var manager: AppFunctionManager

    private val registrations = HashMap<String, AppFunctionRegistration>()

    private val binder =
        object : ITestAppFunctionRegistrationService.Stub() {
            override fun registerAppFunction(functionId: String): Boolean {
                val function =
                    createAppFunctionFromId(functionId, this@TestAppFunctionRegistrationService)
                try {
                    val registration =
                        manager.registerAppFunction(functionId, mainExecutor, function)
                    registrations[functionId] = registration
                } catch (e: Exception) {
                    return false
                }
                Log.d(TAG, "Registered callback for function id $functionId")
                return true
            }

            override fun registerAppFunctions(functionIds: List<String>): Boolean {
                val requests: MutableList<RegisterAppFunctionRequest> = mutableListOf()
                for (functionId in functionIds) {
                    requests.add(
                        RegisterAppFunctionRequest(
                            functionId,
                            mainExecutor,
                            createAppFunctionFromId(
                                functionId,
                                this@TestAppFunctionRegistrationService
                            )
                        )
                    )
                }
                val registrationId = requests.joinToString(",") { it.functionIdentifier }
                registrations[registrationId] = manager.registerAppFunctions(requests)
                return true
            }

            override fun unregisterAppFunction(functionId: String): Boolean {
                if (!registrations.containsKey(functionId)) {
                    throw IllegalStateException(
                        "Callback for function id $functionId not registered"
                    )
                }
                try {
                    registrations[functionId]?.unregister()
                } catch (e: Exception) {
                    return false
                }
                registrations.remove(functionId)
                Log.d(TAG, "Unregistered callback for function id $functionId")
                return true
            }
        }

    override fun onCreate() {
        super.onCreate()
        manager = getSystemService(AppFunctionManager::class.java)
    }

    private fun createAppFunctionFromId(functionId: String, context: Service): AppFunction {
        return when (functionId) {
            ConcatStrings.CONCAT_STRINGS_FUNCTION_ID -> ConcatStrings()
            LongRunning.LONG_RUNNING_FUNCTION_ID -> LongRunning(context)
            OutputInvalidArgumentException.OUTPUT_INVALID_ARGUMENT_EXCEPTION_FUNCTION_ID ->
                OutputInvalidArgumentException()
            ThrowUnknownException.THROW_UNKNOWN_EXCEPTION_FUNCTION_ID -> ThrowUnknownException()
            ThrowInvalidArgumentException.THROW_INVALID_ARGUMENT_FUNCTION_ID ->
                ThrowInvalidArgumentException()
            StopProcess.STOP_PROCESS_FUNCTION_ID -> StopProcess()
            DisabledByDefault.DISABLED_BY_DEFAULT_FUNCTION_ID -> DisabledByDefault()
            GetUris.GET_URIS_FUNCTION_ID -> GetUris()
            ConcatStrings.ACTIVITY_CONCAT_STRINGS_FUNCTION_ID -> ConcatStrings()
            CheckAttribution.CHECK_ATTRIBUTION_FUNCTION_ID -> CheckAttribution()
            else -> throw IllegalArgumentException("Unknown function id $functionId")
        }
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onDestroy() {
        for (registration in registrations.values) {
            try {
                registration.unregister()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to unregister callback", e)
            }
        }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "TestAppFunctionRegistrationService"
    }
}
