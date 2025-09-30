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

package android.mediav2.common.cts;

import static android.mediav2.common.cts.CodecTestBase.areFormatsSupported;
import static android.mediav2.common.cts.CodecTestBase.selectCodecs;
import static android.mediav2.common.cts.DecodeStreamToYuv.getFormatInStream;

import android.media.MediaFormat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Helper class to manage {@link MediaFormat#MIMETYPE_VIDEO_DOLBY_VISION} decoder test parameters.
 *
 * <p>Dolby Vision media types have various profile classes based on their base layer codec:
 * <ul>
 *   <li>DolbyVisionProfileDvav* - AVC base layer</li>
 *   <li>DolbyVisionProfileDvHe* - HEVC base layer</li>
 *   <li>DolbyVisionProfileDvav1* - AV1 base layer</li>
 * </ul>
 *
 * <p>A Dolby Vision decoder component may / may not support profiles from different classes
 * simultaneously. For example, a component may / may not support both DolbyVisionProfileDvavPen
 * and DolbyVisionProfileDvheDer at the same time. Also, a device may have multiple Dolby Vision
 * decoder components, each handling profiles from a specific class only or multiple classes.
 *
 * <p>Instead of listing all available Dolby Vision resources as test parameters and
 * skipping them during execution, this class provides a mapping of Dolby Vision decoders
 * to their corresponding supported assets. Test suites can use this information to
 * selectively add appropriate assets when preparing test parameters.
 *
 * <p><strong>Note:</strong> If a Dolby Vision component is present but no test assets
 * are available for verification, this class adds mock assets to ensure the tests fail.
 * This notifies users that the component remains untested.
 */
public class DolbyVisionDecoderParamPreparerBase {
    protected static final String MEDIA_TYPE_DV = MediaFormat.MIMETYPE_VIDEO_DOLBY_VISION;
    // dummy resource
    protected static final VideoAsset DUMMY_RES =
            new VideoAsset("dv_placeholder.mp4", -1 /* width */, -1 /* height */);

    protected static class VideoAsset {
        public final String mFileName;
        public final int mWidth;
        public final int mHeight;

        public VideoAsset(String fileName, int width, int height) {
            mFileName = fileName;
            mWidth = width;
            mHeight = height;
        }
    }

    protected static Map<String, List<VideoAsset>> initDvDecoderAssetMap(String mediaDir,
            VideoAsset[] assets) {
        ArrayList<String> decoders = selectCodecs(MEDIA_TYPE_DV, null, null, false);
        Map<String, List<VideoAsset>> dvDecAssetMap;
        if (!decoders.isEmpty()) {
            dvDecAssetMap = new HashMap<>();
            for (String decoder : decoders) {
                dvDecAssetMap.put(decoder, new ArrayList<>());
            }
            ArrayList<MediaFormat> formats = new ArrayList<>();
            try {
                for (VideoAsset asset : assets) {
                    formats.clear();
                    formats.add(getFormatInStream(MEDIA_TYPE_DV, mediaDir + asset.mFileName));
                    for (String decoder : decoders) {
                        if (areFormatsSupported(decoder, MEDIA_TYPE_DV, formats)) {
                            Objects.requireNonNull(dvDecAssetMap.get(decoder)).add(asset);
                        }
                    }
                }
            } catch (IOException | IllegalArgumentException ignored) {
            }
            for (Map.Entry<String, List<VideoAsset>> entry : dvDecAssetMap.entrySet()) {
                List<VideoAsset> decAssets = entry.getValue();
                // tests like testAdaptivePlayback, testReconfigure requires 2 resources. other
                // decoder tests need 1 resource. If minimum number of resources are not present,
                // fill them with mock resources to ensure test failure
                for (int size = decAssets.size(); size < 2; size++) {
                    decAssets.add(DUMMY_RES);
                }
            }
        } else {
            dvDecAssetMap = null;
        }
        return dvDecAssetMap;
    }
}
