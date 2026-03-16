/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.bedstead.settings.appfunctions

import android.Manifest
import android.app.appfunctions.AppFunctionException
import android.app.appfunctions.AppFunctionManager
import android.app.appfunctions.ExecuteAppFunctionRequest
import android.app.appfunctions.ExecuteAppFunctionResponse
import android.app.appsearch.GenericDocument
import android.os.CancellationSignal
import android.os.OutcomeReceiver
import android.util.Log
import com.android.bedstead.nene.TestApis
import com.android.bedstead.nene.utils.BlockingCallback.DefaultBlockingCallback
import com.android.bedstead.settings.LOG_TAG
import com.android.bedstead.settings.SETTINGS_PACKAGE_NAME
import java.util.concurrent.TimeUnit

/**
 * A blocking wrapper of [AppFunctionManager].
 */
class AppFunctionsBlockingClient(private val packageName: String = SETTINGS_PACKAGE_NAME) {

    private val context = TestApis.context().instrumentedContext()
    private val appFunctionManager: AppFunctionManager =
        context.getSystemService(AppFunctionManager::class.java)

    internal fun getDeviceState(functionIdentifier: String): List<PerScreenDeviceStates> {
        return getResultDocument(functionIdentifier)!!.asDeviceStateResult()
    }

    /**
     * Returns getDeviceStateMetadata as list of [PerScreenMetadata].
     */
    fun getDeviceStateMetadata(): List<PerScreenMetadata> {
        return getResultDocument("getDeviceStateMetadata")!!.asDeviceStateMetadataResult()
    }

    private fun getResultDocument(functionIdentifier: String): GenericDocument? {
        val request = ExecuteAppFunctionRequest.Builder(packageName, functionIdentifier).build()
        return executeAppFunction(request)?.resultDocument
    }

    private fun executeAppFunction(
        request: ExecuteAppFunctionRequest
    ): ExecuteAppFunctionResponse? {
        val callback = DefaultBlockingCallback<ExecuteAppFunctionResponse?>()

        TestApis.permissions().withPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS).use {
            appFunctionManager.executeAppFunction(
                request,
                context.mainExecutor,
                CancellationSignal(),
                object : OutcomeReceiver<ExecuteAppFunctionResponse, AppFunctionException> {
                    override fun onResult(result: ExecuteAppFunctionResponse) {
                        Log.d(LOG_TAG, "Successfully executed ${request.functionIdentifier}")
                        callback.triggerCallback(result)
                    }

                    override fun onError(error: AppFunctionException) {
                        Log.e(
                            LOG_TAG,
                            "Error executing ${request.functionIdentifier}: ${error.errorMessage}",
                            error
                        )
                        callback.triggerCallback(null)
                    }
                }
            )

            return callback.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        }
    }

    companion object {
        private const val TIMEOUT_SECONDS = 90L
    }
}
