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
import android.app.appfunctions.testutils.TestAppFunctionFactory.buildEmptyGenericDocument
import android.app.appfunctions.testutils.TestContentProvider.Companion.getExecuteResponseWithUris
import android.app.appsearch.GenericDocument
import android.content.Context
import android.os.CancellationSignal
import android.os.OutcomeReceiver
import android.util.Log
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import kotlin.system.exitProcess

enum class FunctionType {
    CONCAT_STRINGS,
    LONG_RUNNING,
    OUTPUT_INVALID_ARGUMENT_EXCEPTION,
    THROW_UNKNOWN_EXCEPTION,
    THROW_INVALID_ARGUMENT_EXCEPTION,
    STOP_PROCESS,
    DISABLED_BY_DEFAULT,
    GET_URIS,
    CHECK_ATTRIBUTION,
    ACTIVITY_CONCAT_STRINGS
}

object TestAppFunctionFactory {
    fun createAppFunction(type: FunctionType, context: Context): AppFunction {
        return when (type) {
            FunctionType.CONCAT_STRINGS -> ConcatStrings()
            FunctionType.LONG_RUNNING -> LongRunning(context)
            FunctionType.OUTPUT_INVALID_ARGUMENT_EXCEPTION -> OutputInvalidArgumentException()
            FunctionType.THROW_UNKNOWN_EXCEPTION -> ThrowUnknownException()
            FunctionType.THROW_INVALID_ARGUMENT_EXCEPTION -> ThrowInvalidArgumentException()
            FunctionType.STOP_PROCESS -> StopProcess()
            FunctionType.DISABLED_BY_DEFAULT -> DisabledByDefault()
            FunctionType.GET_URIS -> GetUris()
            FunctionType.CHECK_ATTRIBUTION -> CheckAttribution()
            FunctionType.ACTIVITY_CONCAT_STRINGS -> ConcatStrings()
        }
    }

    fun getFunctionId(type: FunctionType): String {
        return when (type) {
            FunctionType.CONCAT_STRINGS -> ConcatStrings.CONCAT_STRINGS_FUNCTION_ID
            FunctionType.LONG_RUNNING -> LongRunning.LONG_RUNNING_FUNCTION_ID
            FunctionType.OUTPUT_INVALID_ARGUMENT_EXCEPTION ->
                OutputInvalidArgumentException.OUTPUT_INVALID_ARGUMENT_EXCEPTION_FUNCTION_ID
            FunctionType.THROW_UNKNOWN_EXCEPTION ->
                ThrowUnknownException.THROW_UNKNOWN_EXCEPTION_FUNCTION_ID
            FunctionType.THROW_INVALID_ARGUMENT_EXCEPTION ->
                ThrowInvalidArgumentException.THROW_INVALID_ARGUMENT_FUNCTION_ID
            FunctionType.STOP_PROCESS -> StopProcess.STOP_PROCESS_FUNCTION_ID
            FunctionType.DISABLED_BY_DEFAULT -> DisabledByDefault.DISABLED_BY_DEFAULT_FUNCTION_ID
            FunctionType.GET_URIS -> GetUris.GET_URIS_FUNCTION_ID
            FunctionType.CHECK_ATTRIBUTION -> CheckAttribution.CHECK_ATTRIBUTION_FUNCTION_ID
            FunctionType.ACTIVITY_CONCAT_STRINGS ->
                ConcatStrings.ACTIVITY_CONCAT_STRINGS_FUNCTION_ID
        }
    }

    fun buildEmptyGenericDocument(): GenericDocument {
        return GenericDocument.Builder<GenericDocument.Builder<*>>("namespace", "id", "schemaType")
            .build()
    }
}

class LongRunning(private val context: Context) : AppFunction {
    private var cancellableFuture: Future<*>? = null
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    override fun onExecuteAppFunction(
        request: ExecuteAppFunctionRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<ExecuteAppFunctionResponse, AppFunctionException>,
    ) {
        cancellationSignal.setOnCancelListener {
            cancellableFuture?.cancel(true)
            TestAppFunctionServiceLifecycleReceiver.notifyOnOperationCancelled(context)
        }
        TestAppFunctionServiceLifecycleReceiver.notifyOnCancelListenerSet(context)
        val task = Runnable {
            try {
                Log.d("LongRunning", "Running long thread")
                // Simulate a long-running operation.
                Thread.sleep(5000)

                val response = ExecuteAppFunctionResponse(buildEmptyGenericDocument())
                callback.onResult(response)
            } catch (e: InterruptedException) {
                Log.d("LongRunning", "Operation Interrupted")
                callback.onError(
                    AppFunctionException(
                        AppFunctionException.ERROR_CANCELLED,
                        "Operation Interrupted",
                    )
                )
            }
        }
        cancellableFuture = executor.submit(task)
    }

    companion object {
        const val LONG_RUNNING_FUNCTION_ID = "contextLongRunning"
    }
}

class ConcatStrings() : AppFunction {
    override fun onExecuteAppFunction(
        request: ExecuteAppFunctionRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<ExecuteAppFunctionResponse?, AppFunctionException?>,
    ) {
        val result: GenericDocument =
            GenericDocument.Builder<GenericDocument.Builder<*>>("", "", "")
                .setPropertyString(
                    ExecuteAppFunctionResponse.PROPERTY_RETURN_VALUE,
                    request.parameters.getPropertyString("prefix") +
                        request.parameters.getPropertyString("suffix"),
                )
                .build()
        callback.onResult(ExecuteAppFunctionResponse(result))
    }

    companion object {
        const val CONCAT_STRINGS_FUNCTION_ID = "contextConcatStrings"
        const val ACTIVITY_CONCAT_STRINGS_FUNCTION_ID = "activityConcatStrings"
    }
}

class OutputInvalidArgumentException() : AppFunction {
    override fun onExecuteAppFunction(
        request: ExecuteAppFunctionRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<ExecuteAppFunctionResponse?, AppFunctionException?>,
    ) {
        callback.onError(
            AppFunctionException(
                AppFunctionException.ERROR_INVALID_ARGUMENT,
                INVALID_ARGUMENT_MESSAGE,
            )
        )
    }

    companion object {
        const val OUTPUT_INVALID_ARGUMENT_EXCEPTION_FUNCTION_ID = "contextOutputInvalidArgument"
        const val INVALID_ARGUMENT_MESSAGE = "Wrong parameter boo"
    }
}

class ThrowUnknownException() : AppFunction {
    override fun onExecuteAppFunction(
        request: ExecuteAppFunctionRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<ExecuteAppFunctionResponse?, AppFunctionException?>,
    ) {
        throw Exception(UNKNOWN_EXCEPTION_MESSAGE)
    }

    companion object {
        const val THROW_UNKNOWN_EXCEPTION_FUNCTION_ID = "contextThrowUnknownException"
        const val UNKNOWN_EXCEPTION_MESSAGE = "Unknown exception"
    }
}

class ThrowInvalidArgumentException() : AppFunction {
    override fun onExecuteAppFunction(
        request: ExecuteAppFunctionRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<ExecuteAppFunctionResponse?, AppFunctionException?>,
    ) {
        throw IllegalArgumentException(INVALID_ARGUMENT_MESSAGE)
    }

    companion object {
        const val THROW_INVALID_ARGUMENT_FUNCTION_ID = "contextThrowInvalidArgument"
        const val INVALID_ARGUMENT_MESSAGE = "Wrong argument"
    }
}

class DisabledByDefault() : AppFunction {
    override fun onExecuteAppFunction(
        request: ExecuteAppFunctionRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<ExecuteAppFunctionResponse?, AppFunctionException?>,
    ) {
        callback.onResult(ExecuteAppFunctionResponse(buildEmptyGenericDocument()))
    }

    companion object {
        const val DISABLED_BY_DEFAULT_FUNCTION_ID = "contextDisabledByDefault"
    }
}

class GetUris() : AppFunction {
    override fun onExecuteAppFunction(
        request: ExecuteAppFunctionRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<ExecuteAppFunctionResponse?, AppFunctionException?>,
    ) {
        callback.onResult(getExecuteResponseWithUris(URIS_FOLDER_PATH))
    }

    companion object {
        const val GET_URIS_FUNCTION_ID = "contextGetUris"
        const val URIS_FOLDER_PATH =
            "content://android.app.appfunctions.cts.dynamic.schema.provider"
    }
}

class StopProcess() : AppFunction {
    override fun onExecuteAppFunction(
        request: ExecuteAppFunctionRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<ExecuteAppFunctionResponse?, AppFunctionException?>,
    ) {
        exitProcess(0)
    }

    companion object {
        const val STOP_PROCESS_FUNCTION_ID = "contextStopProcess"
    }
}

class CheckAttribution : AppFunction {
    override fun onExecuteAppFunction(
        request: ExecuteAppFunctionRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<ExecuteAppFunctionResponse?, AppFunctionException?>,
    ) {
        val hasAttribution = request.attribution != null
        val result: GenericDocument =
            GenericDocument.Builder<GenericDocument.Builder<*>>("", "", "")
                .setPropertyBoolean(
                    ExecuteAppFunctionResponse.PROPERTY_RETURN_VALUE,
                    hasAttribution
                )
                .build()
        callback.onResult(ExecuteAppFunctionResponse(result))
    }

    companion object {
        const val CHECK_ATTRIBUTION_FUNCTION_ID = "contextCheckAttribution"
    }
}
