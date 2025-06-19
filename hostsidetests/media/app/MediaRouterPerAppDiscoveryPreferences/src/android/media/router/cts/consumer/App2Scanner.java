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

package android.media.router.cts.consumer;

import android.media.cts.app.common.BaseScanningActivity;

import java.util.List;

public class App2Scanner extends BaseScanningActivity {
    public static final String APP2_PREFERRED_FEATURE = "app2_preferred_feature";

    @Override
    public List<String> getPreferredFeatures() {
        return List.of(APP2_PREFERRED_FEATURE);
    }

    @Override
    public boolean shouldRequestActiveScanning() {
        return true;
    }
}
