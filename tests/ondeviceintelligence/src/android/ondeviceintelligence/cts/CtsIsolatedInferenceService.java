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

import static android.ondeviceintelligence.cts.OnDeviceIntelligenceManagerTest.TEST_KEY;

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
import android.os.Process;
import android.os.ParcelFileDescriptor;
import android.os.PersistableBundle;
import android.service.ondeviceintelligence.OnDeviceSandboxedInferenceService;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;

public class CtsIsolatedInferenceService extends OnDeviceSandboxedInferenceService {
    static final String TAG = "SampleIsolatedService";


    @NonNull
    @Override
    public void onTokenInfoRequest(int callerUid, @NonNull Feature feature, @NonNull Bundle request,
            @Nullable CancellationSignal cancellationSignal,
            @NonNull OutcomeReceiver<TokenInfo, OnDeviceIntelligenceException> callback) {
        callback.onResult(new TokenInfo(1));
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
                == OnDeviceIntelligenceManagerTest.REQUEST_TYPE_GET_FILE_FROM_NON_ISOLATED) {
            populateFileContentInCallback(feature, callback);
            return;
        }

        if (requestType == OnDeviceIntelligenceManagerTest.REQUEST_TYPE_GET_PACKAGE_NAME) {
            PackageManager mPm = getPackageManager();
            Bundle bundle = new Bundle();
            bundle.putString(TEST_KEY, mPm.getNameForUid(Process.myUid()));
            callback.onResult(bundle);
            return;
        }

        if (requestType
                == OnDeviceIntelligenceManagerTest.REQUEST_TYPE_PROCESS_CUSTOM_PARCELABLE_AS_BYTES) {
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
            callback.onResult(Bundle.EMPTY);
        }
    }

    @Override
    public void onUpdateProcessingState(@NonNull Bundle processingState,
            @NonNull OutcomeReceiver<PersistableBundle, OnDeviceIntelligenceException> callback) {
        Log.i(TAG, "onUpdateProcessingState invoked.");
    }

    private void populateFileContentInCallback(@NonNull Feature feature,
            @NonNull ProcessingCallback callback) {
        fetchFeatureFileDescriptorMap(feature, getMainExecutor(),
                fileMap -> {
                    Log.w(TAG, "Got Map of Length : " + fileMap.size() + " and has keys "
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

                        Bundle bundle = new Bundle();
                        bundle.putString(TEST_KEY, result);
                        callback.onResult(bundle);
                    } catch (FileNotFoundException e) {
                        throw new RuntimeException(e);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }

                    try (FileInputStream ignored = openFileInput(
                            OnDeviceIntelligenceManagerTest.TEST_FILE_NAME)) {
                    } catch (IOException e) {
                        Log.e(TAG, "Couldn't open file from openFileInput:", e);
                    }
                });
    }
}
