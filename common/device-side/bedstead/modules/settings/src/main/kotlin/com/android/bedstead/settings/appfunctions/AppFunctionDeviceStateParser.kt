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
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Parses data from AppFunctions into [PerScreenDeviceStates]
 *
 * @property packageName The package name of the target app providing the app function to
 * invoke.
 * @property functionIdentifier The identifier used by the [AppFunctionService] from the
 * target app to uniquely identify the function to be invoked.
 */
class AppFunctionDeviceStateParser(
    private val packageName: String,
    private val functionIdentifier: String
) {

    private val context = TestApis.context().instrumentedContext()
    private val callbackExecutor = Executors.newSingleThreadExecutor()

    /**
     * @return all PerScreenDeviceStates from the document
     */
    fun getAllPerScreenDeviceStates(): List<PerScreenDeviceStates> {
        val document = getGenericDocument() ?: return emptyList()
        return parseDeviceStateResult(document)
    }

    private fun getGenericDocument(): GenericDocument? {
        val appFunctionManager = context.getSystemService(AppFunctionManager::class.java)
        val request = ExecuteAppFunctionRequest.Builder(packageName, functionIdentifier).build()
        val callback = DefaultBlockingCallback<GenericDocument>()
        TestApis.permissions().withPermission(Manifest.permission.EXECUTE_APP_FUNCTIONS).use {
            appFunctionManager.executeAppFunction(
                request,
                callbackExecutor,
                CancellationSignal(),
                object : OutcomeReceiver<ExecuteAppFunctionResponse, AppFunctionException> {
                    override fun onResult(result: ExecuteAppFunctionResponse?) {
                        callback.triggerCallback(result?.resultDocument)
                    }

                    override fun onError(error: AppFunctionException) {
                        Log.e(LOG_TAG, "error while executing app functionsL ${error.errorMessage}")
                        throw error
                    }
                }
            )
            return callback.await(90, TimeUnit.SECONDS)
        }
    }

    private fun parseDeviceStateResult(document: GenericDocument): List<PerScreenDeviceStates> {
        val states = document.getPropertyDocumentArray(
            "androidAppfunctionsReturnValue.perScreenDeviceStates"
        ) ?: arrayOf()
        return states.filter { it.schemaType == PER_SCREEN_DEVICE_STATES_SCHEMA }.map {
            parsePerScreenDeviceStates(it)
        }
    }

    private fun parsePerScreenDeviceStates(document: GenericDocument): PerScreenDeviceStates {
        val deviceStateItems = document.getPropertyDocumentArray("deviceStateItems") ?: arrayOf()
        val intentUri = document.getPropertyString("intentUri")
        return PerScreenDeviceStates(intentUri)
    }

    companion object {
        internal const val LOG_TAG = "DeviceStateParser"
        private const val PER_SCREEN_DEVICE_STATES_SCHEMA =
            "com.google.android.appfunctions.schema.common.v1.devicestate.PerScreenDeviceStates"
    }
}
