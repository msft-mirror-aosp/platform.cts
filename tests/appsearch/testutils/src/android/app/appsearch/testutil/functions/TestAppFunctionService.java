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

package android.app.appsearch.testutil.functions;

import static android.app.appsearch.AppSearchResult.RESULT_NOT_FOUND;
import static android.app.appsearch.AppSearchResult.newFailedResult;
import static android.app.appsearch.AppSearchResult.newSuccessfulResult;
import static android.app.appsearch.functions.ExecuteAppFunctionResponse.PROPERTY_RESULT;

import android.app.appsearch.AppSearchResult;
import android.app.appsearch.GenericDocument;
import android.app.appsearch.functions.AppFunctionService;
import android.app.appsearch.functions.ExecuteAppFunctionRequest;
import android.app.appsearch.functions.ExecuteAppFunctionResponse;
import android.content.Intent;

import androidx.annotation.NonNull;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * An implementation of {@link android.app.appsearch.functions.AppFunctionService} that provides
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
            @NonNull Consumer<AppSearchResult<ExecuteAppFunctionResponse>> callback) {
        switch (request.getFunctionIdentifier()) {
            case "add": {
                ExecuteAppFunctionResponse result = add(request);
                callback.accept(newSuccessfulResult(result));
                break;
            }
            case "add_invokeCallbackTwice": {
                ExecuteAppFunctionResponse result = add(request);
                callback.accept(newSuccessfulResult(result));
                callback.accept(newSuccessfulResult(result));
                break;
            }
            case "startActivity": {
                ExecuteAppFunctionResponse result = startActivity(request);
                callback.accept(newSuccessfulResult(result));
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
                    callback.accept(newSuccessfulResult(result));
                });
                break;
            }
            case "noOp": {
                callback.accept(
                        newSuccessfulResult(new ExecuteAppFunctionResponse.Builder().build()));
                break;
            }
            default:
                callback.accept(newFailedResult(RESULT_NOT_FOUND, "no such method"));
        }
    }

    private ExecuteAppFunctionResponse add(ExecuteAppFunctionRequest request) {
        long a = request.getParameters().getPropertyLong("a");
        long b = request.getParameters().getPropertyLong("b");
        GenericDocument result = new GenericDocument.Builder<>("", "", "")
                .setPropertyLong(PROPERTY_RESULT, a + b)
                .build();
        return new ExecuteAppFunctionResponse.Builder().setResult(result).build();
    }

    private ExecuteAppFunctionResponse startActivity(ExecuteAppFunctionRequest request) {
        Intent intent = new Intent(this, ActivityCreationSynchronizer.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);

        return new ExecuteAppFunctionResponse.Builder().build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        TestAppFunctionServiceLifecycleReceiver.notifyOnDestroyInvoked(this);
    }
}
