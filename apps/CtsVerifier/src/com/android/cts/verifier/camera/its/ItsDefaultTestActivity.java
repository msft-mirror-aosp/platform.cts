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

import java.util.List;

/** Test Activity for all camera ITS tests excluding test_feature_combination. */
public class ItsDefaultTestActivity extends ItsTestActivity {

    @Override
    protected List<String> getSceneIds() {
        return List.of(
                "scene0",
                "scene1_1",
                "scene1_2",
                "scene1_3",
                "scene2_a",
                "scene2_b",
                "scene2_c",
                "scene2_d",
                "scene2_e",
                "scene2_f",
                "scene2_g",
                "scene3",
                "scene4",
                "scene5",
                "scene6",
                "scene7",
                "scene8",
                "scene9",
                "scene_extensions/scene_hdr",
                "scene_extensions/scene_low_light",
                "scene_tele/scene6_tele",
                "scene_tele/scene7_tele",
                "sensor_fusion",
                "scene_flash",
                "scene_ip",
                "scene_gen2_chart",
                "scene_wide_gamut");
    }

    @Override
    protected List<String> getHiddenPhysicalCameraSceneIds() {
        // This must match scenes of SUB_CAMERA_TESTS in tools/run_all_tests.py
        return List.of(
                "scene0",
                "scene1_1",
                "scene1_2",
                "scene1_3",
                "scene2_a",
                "scene4",
                "scene_tele/scene6_tele",
                "scene_tele/scene7_tele",
                "sensor_fusion");
    }

    public ItsDefaultTestActivity() {
        super(
                R.layout.its_main,
                R.string.camera_its_test,
                R.string.camera_its_test_info,
                R.string.camera_its_test);
    }
}
