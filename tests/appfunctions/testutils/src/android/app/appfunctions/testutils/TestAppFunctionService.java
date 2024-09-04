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
import android.app.appfunctions.ExecuteAppFunctionRequest;
import android.app.appfunctions.ExecuteAppFunctionResponse;
import android.app.appsearch.GenericDocument;

import androidx.annotation.NonNull;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * An implementation of {@link android.app.appfunctions.AppFunctionService} that provides
 * some simple functions for testing purposes.
 */
public class TestAppFunctionService extends AppFunctionService {
    private final Executor mExecutor = Executors.newSingleThreadExecutor();

    @Override
    public void onCreate() {
        super.onCreate();
        TestAppFunctionServiceLifecycleReceiver.notifyOnCreateInvoked(this);
    }

    @Override
    public void onExecuteFunction(
            @NonNull ExecuteAppFunctionRequest request,
            @NonNull Consumer<ExecuteAppFunctionResponse> callback) {
        switch (request.getFunctionIdentifier()) {
            case "add": {
                ExecuteAppFunctionResponse result = add(request);
                callback.accept(result);
                break;
            }
            case "add_invokeCallbackTwice": {
                ExecuteAppFunctionResponse result = add(request);
                callback.accept(result);
                callback.accept(result);
                break;
            }
            case "throwException": {
                throw new RuntimeException();
            }
            case "kill": {
                System.exit(0);
                break;
            }
            case "notInvokeCallback": {
                break;
            }
            case "addAsync": {
                mExecutor.execute(() -> {
                    ExecuteAppFunctionResponse result = add(request);
                    callback.accept(result);
                });
                break;
            }
            case "noOp": {
                callback.accept(ExecuteAppFunctionResponse.newSuccess(
                        buildEmptyGenericDocument(), /*extras=*/ null));
                break;
            }
            case "noSuchMethod": {
                callback.accept(ExecuteAppFunctionResponse.newFailure(
                        ExecuteAppFunctionResponse.RESULT_INVALID_ARGUMENT,
                        "Function does not exist",
                        /*extras=*/ null
                ));
                break;
            }
            default:
                callback.accept(
                        ExecuteAppFunctionResponse.newFailure(
                                ExecuteAppFunctionResponse.RESULT_APP_UNKNOWN_ERROR,
                                /*errorMessage=*/ null,
                                /*extras=*/ null)
                );
        }
    }

    private GenericDocument buildEmptyGenericDocument() {
        return new GenericDocument.Builder<>("", "", "").build();
    }

    private ExecuteAppFunctionResponse add(ExecuteAppFunctionRequest request) {
        long a = request.getParameters().getPropertyLong("a");
        long b = request.getParameters().getPropertyLong("b");
        GenericDocument result = new GenericDocument.Builder<>("", "", "")
                .setPropertyLong(ExecuteAppFunctionResponse.PROPERTY_RETURN_VALUE, a + b)
                .build();
        return ExecuteAppFunctionResponse.newSuccess(result, /*extras=*/ null);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        TestAppFunctionServiceLifecycleReceiver.notifyOnDestroyInvoked(this);
    }
}
