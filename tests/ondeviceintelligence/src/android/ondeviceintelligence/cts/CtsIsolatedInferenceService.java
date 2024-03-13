/*
 * Copyright (C) 2021 The Android Open Source Project
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

import android.app.ondeviceintelligence.Feature;
import android.app.ondeviceintelligence.OnDeviceIntelligenceException;
import android.app.ondeviceintelligence.ProcessingCallback;
import android.app.ondeviceintelligence.ProcessingSignal;
import android.app.ondeviceintelligence.StreamingProcessingCallback;
import android.app.ondeviceintelligence.TokenInfo;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.OutcomeReceiver;
import android.os.PersistableBundle;
import android.service.ondeviceintelligence.OnDeviceSandboxedInferenceService;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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
        callback.onPartialResult(Bundle.EMPTY);
        callback.onResult(Bundle.EMPTY);
    }


    @NonNull
    @Override
    public void onProcessRequest(int callerUid, @NonNull Feature feature, @Nullable Bundle request,
            int requestType, @Nullable CancellationSignal cancellationSignal,
            @Nullable ProcessingSignal processingSignal,
            @NonNull ProcessingCallback callback) {
        callback.onResult(Bundle.EMPTY);
    }

    @Override
    public void onUpdateProcessingState(@NonNull Bundle processingState,
            @NonNull OutcomeReceiver<PersistableBundle, OnDeviceIntelligenceException> callback) {
        Log.i(TAG, "onUpdateProcessingState invoked.");
    }

}
