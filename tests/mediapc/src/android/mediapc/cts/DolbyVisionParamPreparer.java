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

package android.mediapc.cts;

import android.mediav2.common.cts.DolbyVisionDecoderParamPreparerBase;

import java.util.List;
import java.util.Map;

/**
 * This class shapes {@link DolbyVisionDecoderParamPreparerBase} results to the test suite needs.
 */
public class DolbyVisionParamPreparer extends DolbyVisionDecoderParamPreparerBase {
    private static final VideoAsset[] DV_ASSETS = {
        new VideoAsset("video_dovi_1920x1080_30fps_dvhe_08_04.mp4", 1920, 1080),
        new VideoAsset("video_dovi_1920x1080_60fps_dvhe_05.mp4", 1920, 1080),
        new VideoAsset("video_dovi_1920x1080_60fps_dvav_09.mp4", 1920, 1080),
        new VideoAsset(
                "ChromaPulseCTS__DV__1920x1080_25fps__P10.0__IPT_PQ_C2__AV1.mp4", 1920, 1080),
        new VideoAsset(
                "ChromaPulseCTS__DV__1920x1080_25fps__P10.1__BT2100_PQ__AV1.mp4", 1920, 1080),
        new VideoAsset(
                "ChromaPulseCTS__DV__1920x1080_25fps__P10.4__BT2100_HLG__AV1.mp4", 1920, 1080),
    };

    private static final Map<String, List<VideoAsset>> DV_DEC_ASSET_MAP =
            initDvDecoderAssetMap(WorkDir.getMediaDirString(), DV_ASSETS);

    public static String getDvResForInitializationLatencyTest(String codecName) {
        if (DV_DEC_ASSET_MAP != null && DV_DEC_ASSET_MAP.containsKey(codecName)) {
            List<VideoAsset> resources = DV_DEC_ASSET_MAP.get(codecName);
            return resources.stream()
                    .findFirst()
                    .map(res -> res.mFileName)
                    .orElse(DUMMY_RES.mFileName);
        }
        return DUMMY_RES.mFileName;
    }
}
