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

import android.app.contentsafety.IsFeatureEnabledCallback;
import android.os.CancellationSignal;
import android.os.UserHandle;
import android.service.contentsafety.ContentSafetySettingsService;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class CtsContentSafetySettingsService extends ContentSafetySettingsService {
    static final String TAG = "SampleContentSafetySettingsService";

    @Override
    public void onIsFeatureEnabled(
            int featureType,
            @NonNull UserHandle userId,
            @Nullable CancellationSignal cancellationSignal,
            @NonNull IsFeatureEnabledCallback callback) {
        Log.i(TAG, "Received onIsFeatureEnabled");
        callback.onSuccess(true);
    }
}
