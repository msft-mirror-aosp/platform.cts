/*
 * Copyright (C) 2025 The Android Open Source Project
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

package android.contentsafety.cts;

import android.os.CancellationSignal;
import android.os.OutcomeReceiver;
import android.os.ParcelFileDescriptor;
import android.service.contentsafety.ContentSafetyException;
import android.service.contentsafety.ContentSafetySandboxedService;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class CtsContentSafetyPccService extends ContentSafetySandboxedService {
    static final String TAG = "SampleCtsContentSafetyPccService";

    @Override
    public void onCheckContent(
            int featureType,
            @NonNull Map<Integer, List<ParcelFileDescriptor>> contentPayloadMap,
            @Nullable CancellationSignal cancellationSignal,
            @NonNull Consumer<Map<String, List<Integer>>> callback) {
        Log.i(TAG, "Received onCheckContent");
        Map<String, List<Integer>> map = new HashMap<>();

        ArrayList<Integer> l = new ArrayList<>();
        l.add(2);
        map.putIfAbsent("1", l);
        callback.accept(map);
    }

    @Override
    public void onLoadFeature(
            @NonNull Map<String, ParcelFileDescriptor> features,
            @Nullable CancellationSignal cancellationSignal,
            @NonNull OutcomeReceiver<Void, ContentSafetyException> callback) {
        Log.i(TAG, "Received onLoadFeature");
        callback.onResult(null);
    }
}
