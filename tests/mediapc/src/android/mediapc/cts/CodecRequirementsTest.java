/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static android.media.MediaCodecInfo.CodecCapabilities.COLOR_Format32bitABGR2101010;
import static android.media.MediaCodecInfo.CodecCapabilities.FEATURE_DynamicColorAspects;
import static android.media.MediaCodecInfo.CodecCapabilities.FEATURE_HlgEditing;
import static android.mediapc.cts.CodecTestBase.getCodecInfo;
import static android.mediapc.cts.CodecTestBase.selectHardwareCodecs;
import static android.mediav2.common.cts.CodecTestBase.isDefaultCodec;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.TruthJUnit.assume;

import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.media.codec.Flags;
import android.mediapc.cts.common.PerformanceClassEvaluator;
import android.mediapc.cts.common.PerformanceClassTestRule;
import android.mediapc.cts.common.Preconditions;
import android.mediapc.cts.common.Requirements;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.util.Size;

import androidx.annotation.Nullable;
import androidx.test.filters.SmallTest;

import com.android.compatibility.common.util.CddTest;
import com.android.compatibility.common.util.MediaUtils;

import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.IntStream;

@RunWith(TestParameterInjector.class)
public class CodecRequirementsTest {

    @TestParameter({
        MediaFormat.MIMETYPE_VIDEO_AVC, MediaFormat.MIMETYPE_VIDEO_HEVC,
        MediaFormat.MIMETYPE_VIDEO_AV1, MediaFormat.MIMETYPE_VIDEO_VP9
    })
    private String mMediaType;

    @Rule
    public final PerformanceClassTestRule pcRule =
            PerformanceClassTestRule.with(Preconditions.BASELINE);

    @Nullable
    private static Size getMaxSupportedRecordingSize() throws CameraAccessException {
        if (!MediaUtils.hasCamera()) return null;

        Context context = getInstrumentation().getTargetContext();
        CameraManager cm = context.getSystemService(CameraManager.class);
        String[] cameraIdList = cm.getCameraIdList();

        for (String cameraId : cameraIdList) {
            CameraCharacteristics characteristics = cm.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map =
                    characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map != null) {
                return Arrays.stream(map.getOutputSizes(MediaRecorder.class))
                        .max(Comparator.comparingInt(size -> size.getWidth() * size.getHeight()))
                        .orElse(null);
            }
        }
        return null;
    }

    /**
     * MUST support the Feature_HlgEditing feature for default hardware AV1 and HEVC
     * encoders present on the device at 4K resolution or the largest Camera-supported
     * resolution, whichever is less.
     */
    @SmallTest
    @RequiresFlagsEnabled(Flags.FLAG_HLG_EDITING)
    @Test(timeout = CodecTestBase.PER_TEST_TIMEOUT_SMALL_TEST_MS)
    @CddTest(requirements = {"5.1/H-1-20"})
    public void testHlgEditingSupport() throws CameraAccessException, IOException {
        assume().withMessage("Test is limited to HEVC and AV1 mediaTypes only")
                .that(mMediaType)
                .isAnyOf(MediaFormat.MIMETYPE_VIDEO_HEVC, MediaFormat.MIMETYPE_VIDEO_AV1);

        boolean isFeatureSupported = true;
        Size size4k = new Size(3840, 2160);
        int frameSize4k = size4k.getWidth() * size4k.getHeight();
        Size maxRecordingSize = getMaxSupportedRecordingSize();
        if (maxRecordingSize == null) {
            maxRecordingSize = size4k;
        } else {
            int frameSize = maxRecordingSize.getWidth() * maxRecordingSize.getHeight();
            maxRecordingSize = frameSize < frameSize4k ? maxRecordingSize : size4k;
        }

        ArrayList<String> hwEncoders = selectHardwareCodecs(mMediaType, null, null, true);
        for (String encoder : hwEncoders) {
            if (!isDefaultCodec(encoder, mMediaType, true)) {
                continue;
            }
            MediaFormat format = MediaFormat.createVideoFormat(mMediaType,
                    maxRecordingSize.getWidth(), maxRecordingSize.getHeight());
            format.setFeatureEnabled(FEATURE_HlgEditing, true);
            if (!MediaUtils.supports(encoder, format)) {
                isFeatureSupported = false;
                break;
            }
        }

        PerformanceClassEvaluator pce = pcRule.getPerformanceClassEvaluator();
        Requirements.VideoCodecHlgEditingRequirement hlgEditingSupportReq =
                Requirements.addR5_1__H_1_20().to(pce);
        hlgEditingSupportReq.setHlgEditing(isFeatureSupported);
    }

    /**
     * [5.1/H-1-21] MUST support FEATURE_DynamicColorAspects for all hardware video decoders
     *  (AVC, HEVC, VP9, AV1 or later)
     */
    @SmallTest
    @RequiresFlagsEnabled(Flags.FLAG_DYNAMIC_COLOR_ASPECTS)
    @Test(timeout = CodecTestBase.PER_TEST_TIMEOUT_SMALL_TEST_MS)
    @CddTest(requirements = {"5.1/H-1-21"})
    public void testDynamicColorAspectFeature() {
        boolean isSupported = selectHardwareCodecs(mMediaType, null, null, false).stream()
                .allMatch(decoder -> {
                    CodecCapabilities caps =
                            getCodecInfo(decoder).getCapabilitiesForType(mMediaType);
                    return caps != null && caps.isFeatureSupported(FEATURE_DynamicColorAspects);
                });

        PerformanceClassEvaluator pce = pcRule.getPerformanceClassEvaluator();
        Requirements.VideoCodecDynamicColorAspectRequirement dynamicColorAspectsReq =
                Requirements.addR5_1__H_1_21().to(pce);
        dynamicColorAspectsReq.setDynamicColorAspects(isSupported);
    }

    /**
     * MUST support portrait resolution for all hardware codecs that support landscape. AV1 codecs
     * are limited to only 1080p resolution while others should support 4k or camera preferred
     * resolution (whichever is less)
     */
    @SmallTest
    @Test(timeout = CodecTestBase.PER_TEST_TIMEOUT_SMALL_TEST_MS)
    @CddTest(requirements = {"5.1/H-1-22"})
    public void testPortraitResolutionSupport() throws CameraAccessException {
        boolean isSupported = true;
        Size requiredSize, maxRequiredSize, maxRecordingSize;

        maxRequiredSize = mMediaType.equals(MediaFormat.MIMETYPE_VIDEO_AV1)
                ? new Size(1920, 1080) : new Size(3840, 2160);
        maxRecordingSize = getMaxSupportedRecordingSize();
        if (maxRecordingSize == null) {
            requiredSize = maxRequiredSize;
        } else {
            int maxRequiredFrameSize = maxRequiredSize.getWidth() * maxRequiredSize.getHeight();
            int maxRecFrameSize = maxRecordingSize.getWidth() * maxRecordingSize.getHeight();
            requiredSize = maxRequiredFrameSize < maxRecFrameSize
                    ? maxRequiredSize : maxRecordingSize;
        }

        for (boolean isEncoder : new boolean[] {true, false}) {
            Size finalRequiredSize = requiredSize;
            Size rotatedSize = new Size(requiredSize.getHeight(), requiredSize.getWidth());
            isSupported = selectHardwareCodecs(mMediaType, null, null, isEncoder).stream()
                    .allMatch(codec -> MediaUtils.supports(codec, mMediaType, finalRequiredSize)
                            && MediaUtils.supports(codec, mMediaType, rotatedSize));
            if (!isSupported) break;
        }

        PerformanceClassEvaluator pce = pcRule.getPerformanceClassEvaluator();
        Requirements.VideoCodecPortraitResolutionRequirement portraitResolutionSupportReq =
                Requirements.addR5_1__H_1_22().to(pce);
        portraitResolutionSupportReq.setPortraitResolution(isSupported);
    }

    /**
     * MUST support RGBA_1010102 color format for all hardware AV1 and HEVC encoders present on
     * the device.
     */
    @SmallTest
    @Test(timeout = CodecTestBase.PER_TEST_TIMEOUT_SMALL_TEST_MS)
    @CddTest(requirements = {"5.12/H-1-2"})
    public void testColorFormatSupport() throws IOException {
        assume().withMessage("Test is limited to HEVC and AV1 mediaTypes only")
                .that(mMediaType)
                .isAnyOf(MediaFormat.MIMETYPE_VIDEO_HEVC, MediaFormat.MIMETYPE_VIDEO_AV1);
        boolean isSupported = true;
        ArrayList<String> hwEncoders = selectHardwareCodecs(mMediaType, null, null, true);
        for (String encoder : hwEncoders) {
            CodecCapabilities caps = getCodecInfo(encoder).getCapabilitiesForType(mMediaType);
            if (IntStream.of(caps.colorFormats).noneMatch(x -> x == COLOR_Format32bitABGR2101010)) {
                isSupported = false;
                break;
            }
        }

        PerformanceClassEvaluator pce = pcRule.getPerformanceClassEvaluator();
        Requirements.RGBA1010102ColorFormatRequirement colorFormatSupportReq =
                Requirements.addR5_12__H_1_2().to(pce);
        colorFormatSupportReq.setRgba1010102ColorFormat(isSupported);
    }
}
