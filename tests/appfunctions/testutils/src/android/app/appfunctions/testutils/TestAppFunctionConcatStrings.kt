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

import android.app.appfunctions.AppFunction
import android.app.appfunctions.AppFunctionException
import android.app.appfunctions.ExecuteAppFunctionRequest
import android.app.appfunctions.ExecuteAppFunctionResponse
import android.app.appsearch.GenericDocument
import android.os.CancellationSignal
import android.os.OutcomeReceiver

class TestAppFunctionConcatStrings() : AppFunction {
    override fun onExecuteAppFunction(
        request: ExecuteAppFunctionRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<ExecuteAppFunctionResponse?, AppFunctionException?>
    ) {
        val result: GenericDocument =
            GenericDocument.Builder<GenericDocument.Builder<*>>("", "", "").setPropertyString(
                ExecuteAppFunctionResponse.PROPERTY_RETURN_VALUE,
                request.parameters.getPropertyString("prefix") +
                        request.parameters.getPropertyString("suffix")
            ).build()
        callback.onResult(ExecuteAppFunctionResponse(result))
    }

    companion object {
        const val CONCAT_STRINGS_FUNCTION_ID = "contextConcatStrings"
    }
}
