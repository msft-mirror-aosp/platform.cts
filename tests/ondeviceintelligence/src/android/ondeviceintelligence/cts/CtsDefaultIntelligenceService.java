/*
 * Copyright (C) 2026 The Android Open Source Project
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

import android.app.ondeviceintelligence.DownloadCallback;
import android.app.ondeviceintelligence.Feature;
import android.app.ondeviceintelligence.FeatureDetails;
import android.app.ondeviceintelligence.OnDeviceIntelligenceException;
import android.os.CancellationSignal;
import android.os.OutcomeReceiver;
import android.os.ParcelFileDescriptor;
import android.os.PersistableBundle;
import android.service.ondeviceintelligence.OnDeviceIntelligenceService;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

/**
 * A default implementation of {@link OnDeviceIntelligenceService} that only overrides the
 * mandatory abstract methods. This service is intended for testing scenarios where the default
 * behavior of non-abstract methods is sufficient, or to verify that compilation succeeds with
 * minimal implementation and default behavior is as expected.
 */
public class CtsDefaultIntelligenceService extends OnDeviceIntelligenceService {

    @Override
    public void onInferenceServiceConnected() {
    }

    @Override
    public void onInferenceServiceDisconnected() {
    }

    @Override
    public void onGetReadOnlyFeatureFileDescriptorMap(@NonNull Feature feature,
            @NonNull Consumer<Map<String, ParcelFileDescriptor>> fileDescriptorMapConsumer) {
        fileDescriptorMapConsumer.accept(Collections.emptyMap());
    }

    @Override
    public void onDownloadFeature(int callerUid, @NonNull Feature feature,
            @Nullable CancellationSignal cancellationSignal,
            @NonNull DownloadCallback downloadCallback) {
        downloadCallback.onDownloadCompleted(PersistableBundle.EMPTY);
    }

    @Override
    public void onGetFeatureDetails(int callerUid, @NonNull Feature feature,
            @NonNull OutcomeReceiver<FeatureDetails, OnDeviceIntelligenceException> featureDetailsCallback) {
        featureDetailsCallback.onResult(new FeatureDetails(FeatureDetails.FEATURE_STATUS_AVAILABLE));
    }

    @Override
    public void onGetFeature(int callerUid, int featureId,
            @NonNull OutcomeReceiver<Feature, OnDeviceIntelligenceException> featureCallback) {
        featureCallback.onResult(new Feature.Builder(featureId).build());
    }

    @Override
    public void onListFeatures(int callerUid,
            @NonNull OutcomeReceiver<List<Feature>, OnDeviceIntelligenceException> listFeaturesCallback) {
        listFeaturesCallback.onResult(Collections.emptyList());
    }

    @Override
    public void onGetVersion(@NonNull LongConsumer versionConsumer) {
        versionConsumer.accept(1L);
    }
}
