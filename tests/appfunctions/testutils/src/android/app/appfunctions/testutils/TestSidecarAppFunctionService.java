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

import android.app.appsearch.GenericDocument;
import android.os.CancellationSignal;

import androidx.annotation.NonNull;

import com.android.extensions.appfunctions.AppFunctionService;
import com.android.extensions.appfunctions.ExecuteAppFunctionRequest;
import com.android.extensions.appfunctions.ExecuteAppFunctionResponse;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * An implementation of {@link AppFunctionService} that provides some simple functions for testing
 * purposes.
 */
public class TestSidecarAppFunctionService extends AppFunctionService {
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
            @NonNull Consumer<ExecuteAppFunctionResponse> callback) {
        cancellationSignal.setOnCancelListener(
                () -> {
                    TestAppFunctionServiceLifecycleReceiver.notifyOnOperationCancelled(this);
                    cancelOperation();
                });
        switch (request.getFunctionIdentifier()) {
            case "add":
                {
                    ExecuteAppFunctionResponse result = add(request, callingPackage);
                    callback.accept(result);
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
                                            callback.accept(
                                                    ExecuteAppFunctionResponse.newFailure(
                                                            ExecuteAppFunctionResponse
                                                                    .RESULT_CANCELLED,
                                                            /* errorMessage= */ "Operation"
                                                                    + " Interrupted",
                                                            /* extras= */ null));
                                            return null;
                                        }
                                        callback.accept(
                                                ExecuteAppFunctionResponse.newSuccess(
                                                        new GenericDocument.Builder<>("", "", "")
                                                                .build(),
                                                        /* extras= */ null));
                                        return null;
                                    });
                    break;
                }
            default:
                callback.accept(
                        ExecuteAppFunctionResponse.newFailure(
                                ExecuteAppFunctionResponse.RESULT_APP_UNKNOWN_ERROR,
                                /* errorMessage= */ null,
                                /* extras= */ null));
        }
    }

    private void cancelOperation() {
        if (mCancellableFuture != null) {
            mCancellableFuture.cancel(true);
        }
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
        return ExecuteAppFunctionResponse.newSuccess(result, /* extras= */ null);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        TestAppFunctionServiceLifecycleReceiver.notifyOnDestroyInvoked(this);
    }
}
