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

package com.android.cts.verifier.camera.its;

import com.android.cts.verifier.R;

import java.util.Collections;
import java.util.List;

/**
 * Feature combination query test activity for testing and collecting results for
 * test_feature_combination.
 */
public class FeatureCombinationTestActivity extends ItsTestActivity {

    @Override
    protected List<String> getSceneIds() {
        // Child provides the complete list of scene IDs.
        return List.of("feature_combination");
    }

    @Override
    protected List<String> getHiddenPhysicalCameraSceneIds() {
        return Collections.emptyList();
    }

    public FeatureCombinationTestActivity() {
        super(
                R.layout.its_main,
                R.string.camera_its_feature_combination_test,
                R.string.camera_its_test_info,
                R.string.camera_its_feature_combination_test);
    }
}
