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

package android.mediav2.cts;

import static android.mediav2.common.cts.CodecTestBase.BOARD_SDK_IS_AT_LEAST_202604;
import static android.mediav2.common.cts.CodecTestBase.SupportClass.CODEC_OPTIONAL;

import android.media.MediaFormat;
import android.mediav2.common.cts.DolbyVisionDecoderParamPreparerBase;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Helper class to manage {@link MediaFormat#MIMETYPE_VIDEO_DOLBY_VISION} decoder test parameters.
 * <p>
 * This class shapes {@link DolbyVisionDecoderParamPreparerBase} results to the test suite needs.
 */
public class DolbyVisionDecoderParamPreparer extends DolbyVisionDecoderParamPreparerBase {
    private static final VideoAsset[] DV_ASSETS = {
        // profile 5 level 2
        new VideoAsset("ChromaPulseCTS__HEVC_BT2020_ST2128__1280x720_25fps__DV_P5.mp4", 1280, 720),
        // profile 5 level 4
        new VideoAsset(
                "ChromaPulseCTS__HEVC_BT2020_ST2128__1920x1080_25fps__DV_P5.mp4", 1920, 1080),
        // profile 5 level 5
        new VideoAsset("video_dovi_1920x1080_60fps_dvhe_05.mp4", 1920, 1080),
        // profile 5 level 2
        new VideoAsset(
                "ChromaPulseCTS__HEVC_BT2020_ST2128__1280x720_29.97fps__DV_P5.mp4", 1280, 720),
        // profile 5 level 4
        new VideoAsset(
                "ChromaPulseCTS__HEVC_BT2020_ST2128__1920x1080_29.97fps__DV_P5.mp4", 1920, 1080),

        // profile 8.1 level 2
        new VideoAsset("ChromaPulseCTS__HEVC_BT2100_PQ__1280x720_25fps__DV_P8.1.mp4", 1280, 720),
        // profile 8.1 level 4
        new VideoAsset("ChromaPulseCTS__HEVC_BT2100_PQ__1920x1080_25fps__DV_P8.1.mp4", 1920, 1080),
        // profile 8.1 level 2
        new VideoAsset("ChromaPulseCTS__HEVC_BT2100_PQ__1280x720_29.97fps__DV_P8.1.mp4", 1280, 720),
        // profile 8.1 level 4
        new VideoAsset(
                "ChromaPulseCTS__HEVC_BT2100_PQ__1920x1080_29.97fps__DV_P8.1.mp4", 1920, 1080),

        // profile 8.4 level 2
        new VideoAsset("ChromaPulseCTS__HEVC_BT2100_HLG__1280x720_25fps__DV_P8.4.mp4", 1280, 720),
        // profile 8.4 level 4
        new VideoAsset("video_dovi_1920x1080_30fps_dvhe_08_04.mp4", 1920, 1080),
        // profile 8.4 level 2
        new VideoAsset(
                "ChromaPulseCTS__HEVC_BT2100_HLG__1280x720_29.97fps__DV_P8.4.mp4", 1280, 720),
        // profile 8.4 level 4
        new VideoAsset("ChromaPulseCTS__HEVC_BT2100_HLG__1920x1080_25fps__DV_P8.4.mp4", 1920, 1080),
        // profile 8.4 level 4
        new VideoAsset(
                "ChromaPulseCTS__HEVC_BT2100_HLG__1920x1080_29.97fps__DV_P8.4.mp4", 1920, 1080),

        // profile 10 level 3.1
        new VideoAsset("ChromaPulseCTS__DV__1280x720_25fps__P10.0__IPT_PQ_C2__AV1.mp4", 1280, 720),
        // profile 10 level 4
        new VideoAsset(
                "ChromaPulseCTS__DV__1920x1080_25fps__P10.0__IPT_PQ_C2__AV1.mp4", 1920, 1080),
        // profile 10 level 3.1
        new VideoAsset(
                "ChromaPulseCTS__DV__1280x720_29.97fps__P10.0__IPT_PQ_C2__AV1.mp4", 1280, 720),
        // profile 10 level 4
        new VideoAsset(
                "ChromaPulseCTS__DV__1920x1080_29.97fps__P10.0__IPT_PQ_C2__AV1.mp4", 1920, 1080),

        // profile 10.1 level 3.1
        new VideoAsset("ChromaPulseCTS__DV__1280x720_25fps__P10.1__BT2100_PQ__AV1.mp4", 1280, 720),
        // profile 10.1 level 4
        new VideoAsset(
                "ChromaPulseCTS__DV__1920x1080_25fps__P10.1__BT2100_PQ__AV1.mp4", 1920, 1080),
        // profile 10.1 level 3.1
        new VideoAsset(
                "ChromaPulseCTS__DV__1280x720_29.97fps__P10.1__BT2100_PQ__AV1.mp4", 1280, 720),
        // profile 10.1 level 4
        new VideoAsset(
                "ChromaPulseCTS__DV__1920x1080_29.97fps__P10.1__BT2100_PQ__AV1.mp4", 1920, 1080),

        // profile 10.4 level 3.1
        new VideoAsset(
                "ChromaPulseCTS__DV__1280x720_29.97fps__P10.4__BT2100_HLG__AV1.mp4", 1280, 720),
        // profile 10.4 level 4
        new VideoAsset(
                "ChromaPulseCTS__DV__1920x1080_25fps__P10.4__BT2100_HLG__AV1.mp4", 1920, 1080),
        // profile 10.4 level 3.1
        new VideoAsset("ChromaPulseCTS__DV__1280x720_25fps__P10.4__BT2100_HLG__AV1.mp4", 1280, 720),
        // profile 10.4 level 4
        new VideoAsset(
                "ChromaPulseCTS__DV__1920x1080_29.97fps__P10.4__BT2100_HLG__AV1.mp4", 1920, 1080),

        // profile 9 level 5
        new VideoAsset("video_dovi_1920x1080_60fps_dvav_09.mp4", 1920, 1080),

        // profile 10, level 7
        new VideoAsset("video_dovi_3840x2160_30fps_dav1_10.mp4", 3840, 2160),
        // profile 10, level 7
        new VideoAsset("video_dovi_3840x2160_30fps_dav1_10_2.mp4", 3840, 2160),
    };
    private static final Map<String, List<VideoAsset>> DV_DEC_ASSET_MAP =
            initDvDecoderAssetMap(WorkDir.getMediaDirString(), DV_ASSETS);

    public static List<Object[]> getDvTestParams(Class<?> clazz) {
        List<Object[]> argsList = new ArrayList<>();

        if (DV_DEC_ASSET_MAP == null || !BOARD_SDK_IS_AT_LEAST_202604) return argsList;

        for (Map.Entry<String, List<VideoAsset>> entry : DV_DEC_ASSET_MAP.entrySet()) {
            List<VideoAsset> assetList = entry.getValue();
            if (clazz.equals(AdaptivePlaybackTest.class)) {
                List<String> fileList = assetList.stream().map(param -> param.mFileName).toList();
                String[] files = fileList.toArray(new String[0]);
                argsList.add(new Object[]{MEDIA_TYPE_DV, files, CODEC_OPTIONAL});
            } else if (clazz.equals(VideoDecoderAvailabilityTest.class)) {
                List<String> fileList = assetList.stream().map(param -> param.mFileName).toList();
                String[] files = fileList.toArray(new String[0]);
                argsList.add(new Object[]{MEDIA_TYPE_DV, files});
            } else if (clazz.equals(CodecDecoderDetachedSurfaceTest.class)) {
                argsList.add(new Object[]{MEDIA_TYPE_DV, assetList.get(0).mFileName});
            } else if (clazz.equals(CodecDecoderPauseTest.class)
                    || clazz.equals(CodecDecoderSurfaceTest.class)) {
                argsList.add(
                        new Object[]{MEDIA_TYPE_DV, assetList.get(0).mFileName, CODEC_OPTIONAL});
            } else if (clazz.equals(CodecDecoderReconfigureTest.class)
                    || clazz.equals(CodecDecoderSurfaceReconfigureTest.class)) {
                argsList.add(new Object[]{MEDIA_TYPE_DV, assetList.get(0).mFileName,
                        assetList.get(1).mFileName, CODEC_OPTIONAL});
            } else if (clazz.equals(CodecDecoderTest.class)) {
                argsList.add(new Object[]{MEDIA_TYPE_DV, assetList.get(0).mFileName, null, -1.0f,
                        -1L, CODEC_OPTIONAL});
            } else if (clazz.equals(CodecDecoderValidationTest.class)) {
                for (int i = 0; i < assetList.size(); i++) {
                    argsList.add(new Object[]{MEDIA_TYPE_DV,
                            new String[]{assetList.get(i).mFileName}, null, -1.0f, -1L, -1, -1,
                            assetList.get(i).mWidth, assetList.get(i).mHeight, CODEC_OPTIONAL});
                }
            } else {
                throw new RuntimeException("DolbyVisionDecoderParamPreparer does not handle class "
                        + clazz.getSimpleName());
            }
        }
        return argsList;
    }
}
