/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.app.appfunctions.testutils;

import android.app.appfunctions.AppFunctionService;
import android.app.appfunctions.AppFunctionException;
import android.app.appfunctions.ExecuteAppFunctionRequest;
import android.app.appfunctions.ExecuteAppFunctionResponse;
import android.app.appsearch.GenericDocument;
import android.os.CancellationSignal;
import android.os.OutcomeReceiver;

import androidx.annotation.NonNull;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * An implementation of {@link android.app.appfunctions.AppFunctionService} that provides some
 * simple functions for testing purposes.
 */
public class TestAppFunctionService extends AppFunctionService {
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();

    private Future<Void> mCancellableFuture = null;

    @Override
    public void onCreate() {
        super.onCreate();
        TestAppFunctionServiceLifecycleReceiver.notifyOnCreateInvoked(this);
    }

    @Override
    public void onExecuteFunction(
            @NonNull ExecuteAppFunctionRequest request,
            @NonNull String callingPackage,
            @NonNull CancellationSignal cancellationSignal,
            @NonNull
                    OutcomeReceiver<ExecuteAppFunctionResponse, AppFunctionException>
                            callback) {

        cancellationSignal.setOnCancelListener(
                () -> {
                    TestAppFunctionServiceLifecycleReceiver.notifyOnOperationCancelled(this);
                    cancelOperation();
                });
        switch (request.getFunctionIdentifier()) {
            case "addWithRestrictCallersWithExecuteAppFunctionsTrue":
            case "addWithRestrictCallersWithExecuteAppFunctionsFalse":
            case "add", "add_disabledByDefault":
                {
                    ExecuteAppFunctionResponse result = add(request, callingPackage);
                    callback.onResult(result);
                    break;
                }
            case "add_invokeCallbackTwice":
                {
                    ExecuteAppFunctionResponse result = add(request, callingPackage);
                    callback.onResult(result);
                    callback.onResult(result);
                    break;
                }
            case "throwException":
                {
                    throw new RuntimeException();
                }
            case "kill":
                {
                    System.exit(0);
                    break;
                }
            case "notInvokeCallback":
                {
                    break;
                }
            case "addAsync":
                {
                    mExecutor.execute(
                            () -> {
                                ExecuteAppFunctionResponse result = add(request, callingPackage);
                                callback.onResult(result);
                            });
                    break;
                }
            case "noOp":
                {
                    callback.onResult(new ExecuteAppFunctionResponse(buildEmptyGenericDocument()));
                    break;
                }
            case "noSuchMethod":
                {
                    callback.onError(
                            new AppFunctionException(
                                    AppFunctionException.ERROR_INVALID_ARGUMENT,
                                    "Function does not exist"));
                    break;
                }
            case "longRunningFunction":
                {
                    mCancellableFuture =
                            mExecutor.submit(
                                    () -> {
                                        try {
                                            Thread.sleep(2000);
                                        } catch (InterruptedException e) {
                                            callback.onError(
                                                    new AppFunctionException(
                                                            AppFunctionException
                                                                    .ERROR_CANCELLED,
                                                            "Operation Interrupted"));
                                            return null;
                                        }
                                        callback.onResult(
                                                new ExecuteAppFunctionResponse(
                                                        buildEmptyGenericDocument()));
                                        return null;
                                    });
                    break;
                }
            default:
                callback.onError(
                        new AppFunctionException(
                                AppFunctionException.ERROR_APP_UNKNOWN_ERROR,
                                /* errorMessage= */ null));
        }
    }

    private void cancelOperation() {
        if (mCancellableFuture != null) {
            mCancellableFuture.cancel(true);
        }
    }

    private GenericDocument buildEmptyGenericDocument() {
        return new GenericDocument.Builder<>("", "", "").build();
    }

    private ExecuteAppFunctionResponse add(
            ExecuteAppFunctionRequest request, String callingPackage) {
        long a = request.getParameters().getPropertyLong("a");
        long b = request.getParameters().getPropertyLong("b");
        GenericDocument result =
                new GenericDocument.Builder<>("", "", "")
                        .setPropertyLong(ExecuteAppFunctionResponse.PROPERTY_RETURN_VALUE, a + b)
                        .setPropertyString("TEST_PROPERTY_CALLING_PACKAGE", callingPackage)
                        .build();
        return new ExecuteAppFunctionResponse(result);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        TestAppFunctionServiceLifecycleReceiver.notifyOnDestroyInvoked(this);
    }
}
