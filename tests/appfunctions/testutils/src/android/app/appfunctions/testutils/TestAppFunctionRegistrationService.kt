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
import android.app.appfunctions.AppFunctionManager
import android.app.appfunctions.AppFunctionRegistration
import android.app.appfunctions.RegisterAppFunctionRequest
import android.app.appfunctions.testutils.TestAppFunctionFactory.createAppFunction
import android.app.appfunctions.testutils.TestAppFunctionFactory.getFunctionId
import android.content.Intent
import android.os.IBinder
import android.util.Log

/** A service that runs in a separate process to register AppFunctions. */
class TestAppFunctionRegistrationService : Service() {
    private lateinit var manager: AppFunctionManager

    private val registrations = HashMap<String, AppFunctionRegistration>()

    private val binder =
        object : ITestAppFunctionRegistrationService.Stub() {
            override fun registerAppFunction(functionType: String): Boolean {
                val functionType = FunctionType.valueOf(functionType)
                val functionId = getFunctionId(functionType)
                val function =
                    createAppFunction(functionType, this@TestAppFunctionRegistrationService)
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

            override fun registerAppFunctions(functionTypes: List<String>): Boolean {
                val requests: MutableList<RegisterAppFunctionRequest> = mutableListOf()
                for (functionType in functionTypes) {
                    val functionType = FunctionType.valueOf(functionType)
                    requests.add(RegisterAppFunctionRequest(
                        getFunctionId(functionType),
                        mainExecutor,
                        createAppFunction(functionType, this@TestAppFunctionRegistrationService)
                    ))
                }
                val registrationId = requests.joinToString(",") { it.functionIdentifier }
                registrations[registrationId] = manager.registerAppFunctions(requests)
                return true
            }

            override fun unregisterAppFunction(functionType: String): Boolean {
                val functionId = getFunctionId(FunctionType.valueOf(functionType))
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
