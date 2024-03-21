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


import static android.ondeviceintelligence.cts.OnDeviceIntelligenceManagerTest.TEST_CONTENT;
import static android.ondeviceintelligence.cts.OnDeviceIntelligenceManagerTest.TEST_FILE_NAME;

import android.app.ondeviceintelligence.DownloadCallback;
import android.app.ondeviceintelligence.Feature;
import android.app.ondeviceintelligence.FeatureDetails;
import android.app.ondeviceintelligence.OnDeviceIntelligenceException;
import android.os.CancellationSignal;
import android.os.OutcomeReceiver;
import android.os.ParcelFileDescriptor;
import android.os.PersistableBundle;
import android.service.ondeviceintelligence.OnDeviceIntelligenceService;
import android.util.ArrayMap;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

public class CtsIntelligenceService extends OnDeviceIntelligenceService {
    static final String TAG = "SampleIntelligenceService";

    @Override
    public void onInferenceServiceConnected() {
        Log.i(TAG, "Received onInferenceServiceStarted");
    }

    @Override
    public void onInferenceServiceDisconnected() {
        Log.i(TAG, "Received onInferenceServiceDisconnected");
    }

    @Override
    public void onGetReadOnlyFeatureFileDescriptorMap(@NonNull Feature feature,
            @NonNull Consumer<Map<String, ParcelFileDescriptor>> fileDescriptorMapConsumer) {
        try {
            File testFile = getTestFile();
            try (ParcelFileDescriptor pfd = ParcelFileDescriptor.open(testFile,
                    ParcelFileDescriptor.MODE_READ_ONLY)) {
                Map<String, ParcelFileDescriptor> fileDescriptorMap = new ArrayMap<>();
                fileDescriptorMap.put(testFile.getName(), pfd);
                fileDescriptorMapConsumer.accept(fileDescriptorMap);
            } catch (FileNotFoundException e) {
                Log.e(TAG, "File Not Found", e);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onDownloadFeature(int callerUid, @NonNull Feature feature,
            @Nullable CancellationSignal cancellationSignal,
            @NonNull DownloadCallback downloadCallback) {
        Log.w(TAG, "Received onDownloadFeature call from: " + callerUid);
        downloadCallback.onDownloadCompleted(PersistableBundle.EMPTY);
    }

    @Override
    public void onGetFeatureDetails(int callerUid, @NonNull Feature feature,
            @NonNull OutcomeReceiver<FeatureDetails, OnDeviceIntelligenceException> featureDetailsCallback) {
        featureDetailsCallback.onResult(new FeatureDetails(1));
    }

    @Override
    public void onGetFeature(int callerUid, int featureId,
            @NonNull OutcomeReceiver<Feature, OnDeviceIntelligenceException> featureCallback) {
        featureCallback.onResult(new Feature.Builder(0).build());
    }

    @Override
    public void onListFeatures(int callerUid,
            @NonNull OutcomeReceiver<List<Feature>, OnDeviceIntelligenceException> listFeaturesCallback) {
        List<Feature> featureList = new ArrayList<>();
        featureList.add(new Feature.Builder(0).build());
        listFeaturesCallback.onResult(featureList);
    }

    @Override
    public void onGetVersion(@NonNull LongConsumer versionConsumer) {
        versionConsumer.accept(1);

    }

    private File getTestFile() throws IOException {
        File path = this.getFilesDir();
        File file = new File(path, TEST_FILE_NAME);
        if (file.exists()) {
            return file;
        }

        try (FileOutputStream stream = new FileOutputStream(file)) {
            stream.write(TEST_CONTENT.getBytes());
        }

        return file;
    }

}
