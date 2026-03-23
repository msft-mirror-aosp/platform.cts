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

package android.app.appfunctions.testutils

import android.app.appfunctions.AppFunctionException
import android.app.appfunctions.AppFunctionService
import android.app.appfunctions.ExecuteAppFunctionRequest
import android.app.appfunctions.ExecuteAppFunctionResponse
import android.app.appsearch.GenericDocument
import android.content.pm.SigningInfo
import android.os.CancellationSignal
import android.os.OutcomeReceiver

class TestAppFunctionService2 : AppFunctionService() {

    override fun onExecuteFunction(
        request: ExecuteAppFunctionRequest,
        callingPackage: String,
        callingPackageSigningInfo: SigningInfo,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<ExecuteAppFunctionResponse, AppFunctionException>,
    ) {
        when (request.functionIdentifier) {
            "echo" -> handleEcho(request, callback)
            else -> {
                callback.onError(
                    AppFunctionException(
                        AppFunctionException.ERROR_FUNCTION_NOT_FOUND,
                        "Function not found: ${request.functionIdentifier}",
                    )
                )
            }
        }
    }

    private fun handleEcho(
        request: ExecuteAppFunctionRequest,
        callback: OutcomeReceiver<ExecuteAppFunctionResponse, AppFunctionException>,
    ) {
        val message = request.parameters.getPropertyString("message")
        val resultDocument: GenericDocument =
            GenericDocument.Builder<GenericDocument.Builder<*>>("", "", "")
                .setPropertyString(ExecuteAppFunctionResponse.PROPERTY_RETURN_VALUE, message)
                .build()
        callback.onResult(ExecuteAppFunctionResponse(resultDocument))
    }
}
