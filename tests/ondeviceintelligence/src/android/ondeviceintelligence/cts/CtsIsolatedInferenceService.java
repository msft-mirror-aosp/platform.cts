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

package android.ondeviceintelligence.cts;

import static android.ondeviceintelligence.cts.OnDeviceIntelligenceManagerTest.EXCEPTION_MESSAGE_KEY;
import static android.ondeviceintelligence.cts.OnDeviceIntelligenceManagerTest.EXCEPTION_PARAMS_KEY;
import static android.ondeviceintelligence.cts.OnDeviceIntelligenceManagerTest.EXCEPTION_STATUS_CODE_KEY;
import static android.ondeviceintelligence.cts.OnDeviceIntelligenceManagerTest.TEST_FILE_NAME;
import static android.ondeviceintelligence.cts.OnDeviceIntelligenceManagerTest.TEST_KEY;
import static android.ondeviceintelligence.cts.OnDeviceIntelligenceManagerTest.TOKEN_INFO_COUNT_KEY;
import static android.ondeviceintelligence.cts.OnDeviceIntelligenceManagerTest.TOKEN_INFO_PARAMS_KEY;

import android.app.ondeviceintelligence.Feature;
import android.app.ondeviceintelligence.OnDeviceIntelligenceException;
import android.app.ondeviceintelligence.ProcessingCallback;
import android.app.ondeviceintelligence.ProcessingSignal;
import android.app.ondeviceintelligence.StreamingProcessingCallback;
import android.app.ondeviceintelligence.TokenInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.OutcomeReceiver;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.PersistableBundle;
import android.os.Process;
import android.service.ondeviceintelligence.OnDeviceSandboxedInferenceService;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.concurrent.futures.CallbackToFutureAdapter;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;

public class CtsIsolatedInferenceService extends OnDeviceSandboxedInferenceService {
    static final String TAG = "SampleIsolatedService";

    // TODO(339594686): replace with API constants
    private static final String REGISTER_MODEL_UPDATE_CALLBACK_BUNDLE_KEY =
            "register_model_update_callback";
    private static final String MODEL_LOADED_BUNDLE_KEY = "model_loaded";

    public static TokenInfo constructTokenInfo(int status, PersistableBundle persistableBundle) {
        if (persistableBundle == null) {
            return new TokenInfo(status);
        } else {
            return new TokenInfo(status, persistableBundle);
        }
    }

    @NonNull
    @Override
    public Executor getCallbackExecutor() {
        return getMainExecutor();
    }

    @NonNull
    @Override
    public void onTokenInfoRequest(int callerUid, @NonNull Feature feature, @NonNull Bundle request,
            @Nullable CancellationSignal cancellationSignal,
            @NonNull OutcomeReceiver<TokenInfo, OnDeviceIntelligenceException> callback) {
        callback.onResult(constructTokenInfo(request.getInt(TOKEN_INFO_COUNT_KEY),
                request.getParcelable(TOKEN_INFO_PARAMS_KEY, PersistableBundle.class)));
    }

    @NonNull
    @Override
    public void onProcessRequestStreaming(int callerUid, @NonNull Feature feature,
            @NonNull Bundle request,
            int requestType, @Nullable CancellationSignal cancellationSignal,
            @Nullable ProcessingSignal processingSignal,
            @NonNull StreamingProcessingCallback callback) {
        if (processingSignal != null) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            processingSignal.setOnProcessingSignalCallback(getCallbackExecutor(),
                    actionParams -> {
                        Log.i(TAG,
                                "Received processing signal which has action Params : "
                                        + actionParams);
                        callback.onPartialResult(new Bundle(actionParams));
                    });
        }
        if (cancellationSignal != null) {
            cancellationSignal.setOnCancelListener(() -> {
                Log.i(TAG,
                        "Received cancellation signal");
                Bundle bundle = new Bundle();
                bundle.putBoolean(OnDeviceIntelligenceManagerTest.TEST_KEY, true);
                callback.onResult(bundle);
            });
            return;
        }
        callback.onResult(Bundle.EMPTY);
    }

    @NonNull
    @Override
    public void onProcessRequest(int callerUid, @NonNull Feature feature, @Nullable Bundle request,
            int requestType, @Nullable CancellationSignal cancellationSignal,
            @Nullable ProcessingSignal processingSignal,
            @NonNull ProcessingCallback callback) {
        if (requestType
                == OnDeviceIntelligenceManagerTest.REQUEST_TYPE_GET_FILE_FROM_MAP) {
            try {
                Bundle bundle = new Bundle();
                bundle.putString(TEST_KEY, getFileContentFromFdMap(feature).get());
                callback.onResult(bundle);
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
            return;
        }

        if (requestType
                == OnDeviceIntelligenceManagerTest.REQUEST_TYPE_GET_FILE_FROM_STREAM) {
            Bundle bundle = new Bundle();
            try {
                bundle.putString(TEST_KEY, fetchFileContent());
                callback.onResult(bundle);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return;
        }

        if (requestType
                == OnDeviceIntelligenceManagerTest.REQUEST_TYPE_GET_FILE_FROM_PFD) {
            Bundle bundle = new Bundle();
            try {
                bundle.putString(TEST_KEY, fetchFileContentFromPfd().get());
                callback.onResult(bundle);
            } catch (IOException | InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
            return;
        }

        if (requestType == OnDeviceIntelligenceManagerTest.REQUEST_TYPE_GET_PACKAGE_NAME) {
            PackageManager mPm = getPackageManager();
            Bundle bundle = new Bundle();
            bundle.putString(TEST_KEY, mPm.getNameForUid(Process.myUid()));
            callback.onResult(bundle);
            return;
        }

        if (requestType == OnDeviceIntelligenceManagerTest.REQUEST_TYPE_GET_CALLER_UID) {
            Bundle bundle = new Bundle();
            bundle.putInt(TEST_KEY, callerUid);
            callback.onResult(bundle);
            return;
        }

        if (requestType
                == BundleValidationTest.REQUEST_TYPE_PROCESS_CUSTOM_PARCELABLE_AS_BYTES) {
            byte[] bytes = request.getByteArray("request");
            Parcel parcel = Parcel.obtain();
            parcel.unmarshall(bytes, 0, bytes.length);
            parcel.setDataPosition(0);
            Log.i(TAG, "Bytes : "
                    + bytes.length);
            SimpleParcelable simpleParcelable = SimpleParcelable.CREATOR.createFromParcel(parcel);
            Bundle bundle = new Bundle();
            bundle.putString(TEST_KEY, simpleParcelable.getMyString());
            callback.onResult(bundle);
            Log.i(TAG, "My Simple Parcelable : " + simpleParcelable.getMyString());
            parcel.recycle();
            return;
        }

        if (requestType
                == OnDeviceIntelligenceManagerTest.REQUEST_TYPE_GET_AUGMENTED_DATA) {
            callback.onDataAugmentRequest(Bundle.EMPTY,
                    callback::onResult);
            return;
        }

        if (request.containsKey(EXCEPTION_STATUS_CODE_KEY)) {
            populateExceptionInCallback(request, callback);
            return;
        }

        if (cancellationSignal != null) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            cancellationSignal.setOnCancelListener(() -> {
                Bundle bundle = new Bundle();
                bundle.putBoolean(OnDeviceIntelligenceManagerTest.TEST_KEY, true);
                callback.onResult(bundle);
                Log.i(TAG,
                        "Received cancellation signal");
            });
        } else {
            Log.i(TAG,
                    "Received NULL cancellation signal.");
            callback.onResult(request);
        }
    }

    private void populateExceptionInCallback(Bundle request, ProcessingCallback callback) {
        callback.onError(constructException(request));
    }

    @Override
    public void onUpdateProcessingState(@NonNull Bundle processingState,
            @NonNull OutcomeReceiver<PersistableBundle, OnDeviceIntelligenceException> callback) {
        Log.i(TAG, "onUpdateProcessingState invoked.");
        if (processingState.containsKey(REGISTER_MODEL_UPDATE_CALLBACK_BUNDLE_KEY)) {
            Log.i(TAG, "ModelUpdate callback received.");
            PersistableBundle resultBundle = new PersistableBundle();
            resultBundle.putBoolean(MODEL_LOADED_BUNDLE_KEY, true);
            callback.onResult(resultBundle);
        }
        callback.onResult(PersistableBundle.EMPTY);
    }

    private Future<String> getFileContentFromFdMap(@NonNull Feature feature) {
        return CallbackToFutureAdapter.getFuture(
                completer -> {
                    fetchFeatureFileDescriptorMap(feature, getMainExecutor(),
                            fileMap -> {
                                Log.w(TAG,
                                        "Got Map of Length : " + fileMap.size() + " and has keys "
                                                + fileMap.keySet());

                                try (ParcelFileDescriptor pfd = fileMap.get(
                                        OnDeviceIntelligenceManagerTest.TEST_FILE_NAME);
                                     InputStreamReader isr = new InputStreamReader(
                                             new FileInputStream(pfd.getFileDescriptor()));
                                     BufferedReader br = new BufferedReader(isr)) {
                                    String line;
                                    String result = "";
                                    while ((line = br.readLine()) != null) {
                                        Log.w(TAG, line);
                                        result = result.concat(line);
                                    }
                                    completer.set(result);
                                } catch (IOException e) {
                                    completer.setException(e);
                                }
                            });
                    // Used only for debugging.
                    return "Fetch file contents from map";
                });
    }

    private String fetchFileContent() throws IOException {
        FileInputStream fileInputStream = openFileInput(TEST_FILE_NAME);
        String result = "";
        try (InputStreamReader isr = new InputStreamReader(fileInputStream);
             BufferedReader br = new BufferedReader(isr)) {
            String line;
            while ((line = br.readLine()) != null) {
                Log.w(TAG, line);
                result = result.concat(line);
            }
            return result;
        } catch (IOException e) {
            throw e;
        }
    }

    private Future<String> fetchFileContentFromPfd() throws IOException {
        return CallbackToFutureAdapter.getFuture(
                completer -> {
                    getReadOnlyFileDescriptor(TEST_FILE_NAME, getMainExecutor(),
                            pfd -> {
                                try (InputStreamReader isr = new InputStreamReader(
                                        new FileInputStream(pfd.getFileDescriptor()));
                                     BufferedReader br = new BufferedReader(isr)) {
                                    String line;
                                    String result = "";
                                    while ((line = br.readLine()) != null) {
                                        Log.w(TAG, line);
                                        result = result.concat(line);
                                    }
                                    completer.set(result);
                                } catch (IOException e) {
                                    completer.setException(e);
                                }
                            });
                    // Used only for debugging.
                    return "Fetch file contents from map";
                });
    }

    public static OnDeviceIntelligenceException constructException(Bundle bundle) {
        boolean hasStatusCode = bundle.containsKey(EXCEPTION_STATUS_CODE_KEY);
        boolean hasMessage = bundle.containsKey(EXCEPTION_MESSAGE_KEY);

        if (hasStatusCode && bundle.size() == 1) {
            return new OnDeviceIntelligenceException(bundle.getInt(EXCEPTION_STATUS_CODE_KEY));
        }

        if (bundle.size() == 2) {
            if (hasStatusCode && hasMessage) {
                return new OnDeviceIntelligenceException(bundle.getInt(EXCEPTION_STATUS_CODE_KEY),
                        bundle.getString(EXCEPTION_MESSAGE_KEY));
            } else {
                return new OnDeviceIntelligenceException(bundle.getInt(EXCEPTION_STATUS_CODE_KEY),
                        bundle.getParcelable(EXCEPTION_PARAMS_KEY, PersistableBundle.class));
            }
        }

        return new OnDeviceIntelligenceException(bundle.getInt(EXCEPTION_STATUS_CODE_KEY),
                bundle.getString(EXCEPTION_MESSAGE_KEY),
                bundle.getParcelable(EXCEPTION_PARAMS_KEY, PersistableBundle.class));
    }
}
