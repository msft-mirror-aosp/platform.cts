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
import android.content.pm.PackageManager;
import android.content.pm.SigningInfo;
import android.os.CancellationSignal;
import android.os.OutcomeReceiver;

import androidx.annotation.NonNull;

import com.android.extensions.appfunctions.AppFunctionException;
import com.android.extensions.appfunctions.AppFunctionService;
import com.android.extensions.appfunctions.ExecuteAppFunctionRequest;
import com.android.extensions.appfunctions.ExecuteAppFunctionResponse;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
            @NonNull SigningInfo callingPackageSigningInfo,
            @NonNull CancellationSignal cancellationSignal,
            @NonNull OutcomeReceiver<ExecuteAppFunctionResponse, AppFunctionException> callback) {
        cancellationSignal.setOnCancelListener(
                () -> {
                    TestAppFunctionServiceLifecycleReceiver.notifyOnOperationCancelled(this);
                    cancelOperation();
                });
        switch (request.getFunctionIdentifier()) {
            case "add": {
                    ExecuteAppFunctionResponse result = add(request);
                    callback.onResult(result);
                    break;
                }
            case "noOp":
                {
                    ExecuteAppFunctionResponse result =
                            noop(callingPackage, callingPackageSigningInfo);
                callback.onResult(result);
                break;
            }
            case "longRunningFunction": {
                mCancellableFuture =
                        mExecutor.submit(
                                () -> {
                                    try {
                                        Thread.sleep(2000);
                                    } catch (InterruptedException e) {
                                        callback.onError(
                                                new AppFunctionException(
                                                        AppFunctionException.ERROR_CANCELLED,
                                                        "Operation Interrupted",
                                                        /* extras= */ null));
                                        return null;
                                    }
                                    callback.onResult(
                                            new ExecuteAppFunctionResponse(
                                                    new GenericDocument.Builder<>("", "", "")
                                                            .build()));
                                    return null;
                                });
                break;
            }
            default:
                callback.onError(
                        new AppFunctionException(
                                AppFunctionException.ERROR_APP_UNKNOWN_ERROR,
                                /* errorMessage= */ null,
                                /* extras= */ null));
        }
    }

    @Override
    public void onExecuteFunction(
            @NonNull ExecuteAppFunctionRequest request,
            @NonNull String callingPackage,
            @NonNull CancellationSignal cancellationSignal,
            @NonNull OutcomeReceiver<ExecuteAppFunctionResponse, AppFunctionException> callback) {
        throw new UnsupportedOperationException("Method now deprecated");
    }

    private boolean verifyPackageInfo(SigningInfo callingPackageSigningInfo) {
        String appFunctionExecutorPackageName = "android.app.appfunctions.cts";
        SigningInfo actualSigningInfo;
        try {
            actualSigningInfo =
                    getPackageManager()
                            .getPackageInfo(
                                    appFunctionExecutorPackageName,
                                    PackageManager.GET_SIGNING_CERTIFICATES)
                            .signingInfo;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
        // TODO(oadesina): getSigningDetails is not public api.
        // return Objects.requireNonNull(actualSigningInfo)
        //        .getSigningDetails()
        //        .equals(callingPackageSigningInfo.getSigningDetails());
        return true;
    }

    private void cancelOperation() {
        if (mCancellableFuture != null) {
            mCancellableFuture.cancel(true);
        }
    }

    private ExecuteAppFunctionResponse add(ExecuteAppFunctionRequest request) {
        long a = request.getParameters().getPropertyLong("a");
        long b = request.getParameters().getPropertyLong("b");
        GenericDocument result =
                new GenericDocument.Builder<>("", "", "")
                        .setPropertyLong(ExecuteAppFunctionResponse.PROPERTY_RETURN_VALUE, a + b)
                        .build();
        return new ExecuteAppFunctionResponse(result);
    }

    private ExecuteAppFunctionResponse noop(
            String callingPackage, SigningInfo callingPackageSigningInfo) {
        GenericDocument result =
                new GenericDocument.Builder<>("", "", "")
                        .setPropertyString("TEST_PROPERTY_CALLING_PACKAGE", callingPackage)
                        .setPropertyBoolean(
                                "TEST_PROPERTY_HAS_CALLER_VISIBILITY",
                                verifyPackageInfo(callingPackageSigningInfo))
                        .build();
        return new ExecuteAppFunctionResponse(result);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        TestAppFunctionServiceLifecycleReceiver.notifyOnDestroyInvoked(this);
    }
}
