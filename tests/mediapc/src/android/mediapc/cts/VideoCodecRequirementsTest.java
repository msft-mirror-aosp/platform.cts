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

import static android.media.MediaCodecInfo.CodecProfileLevel.AV1Level51;
import static android.media.MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10;
import static android.media.MediaCodecInfo.CodecProfileLevel.AV1ProfileMain8;
import static android.media.MediaFormat.MIMETYPE_VIDEO_AV1;
import static android.mediapc.cts.CodecTestBase.SELECT_HARDWARE;
import static android.mediapc.cts.CodecTestBase.SELECT_VIDEO;
import static android.mediapc.cts.CodecTestBase.getCodecCapabilities;
import static android.mediapc.cts.CodecTestBase.getCodecInfo;
import static android.mediapc.cts.CodecTestBase.getMediaTypesOfAvailableCodecs;
import static android.mediapc.cts.CodecTestBase.selectCodecs;
import static android.mediapc.cts.CodecTestBase.selectHardwareCodecs;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import static java.lang.Math.max;

import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.media.MediaCodecInfo;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.media.MediaCodecInfo.VideoCapabilities.PerformancePoint;
import android.media.MediaFormat;
import android.mediapc.cts.common.PerformanceClassEvaluator;
import android.mediapc.cts.common.PerformanceClassTestRule;
import android.mediapc.cts.common.Preconditions;
import android.mediapc.cts.common.Requirements;
import android.util.Log;
import android.util.Range;

import androidx.test.filters.LargeTest;
import androidx.test.filters.SmallTest;

import com.android.compatibility.common.util.CddTest;

import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class VideoCodecRequirementsTest {
    private static final String LOG_TAG = VideoCodecRequirementsTest.class.getSimpleName();
    private static final String INPUT_FILE = "bbb_3840x2160_AVIF.avif";

    @Rule
    public final PerformanceClassTestRule pcRule =
            PerformanceClassTestRule.with(Preconditions.BASELINE);

    private boolean decodeAVIF(File inputfile) throws IOException {
        ImageDecoder.Source src = ImageDecoder.createSource(inputfile);
        Bitmap bm = ImageDecoder.decodeBitmap(src);
        return true;
    }

    private Set<String> get4k60HwCodecSet(boolean isEncoder) throws IOException {
        Set<String> codecSet = new HashSet<>();
        Set<String> codecMediaTypes = getMediaTypesOfAvailableCodecs(SELECT_VIDEO, SELECT_HARDWARE);
        PerformancePoint PP4k60 = new PerformancePoint(3840, 2160, 60);
        for (String codecMediaType : codecMediaTypes) {
            ArrayList<String> hwVideoCodecs =
                    selectHardwareCodecs(codecMediaType, null, null, isEncoder);
            for (String hwVideoCodec : hwVideoCodecs) {
                CodecCapabilities capabilities = getCodecCapabilities(hwVideoCodec, codecMediaType);
                assertNotNull("did not receive capabilities for codec: " + hwVideoCodec
                        + ", media type: " + codecMediaType + "\n", capabilities);
                List<PerformancePoint> pps =
                        capabilities.getVideoCapabilities().getSupportedPerformancePoints();
                assertTrue(hwVideoCodec + " doesn't advertise performance points", pps.size() > 0);
                for (PerformancePoint pp : pps) {
                    if (pp.covers(PP4k60)) {
                        codecSet.add(hwVideoCodec);
                        Log.d(LOG_TAG,
                                "Performance point 4k60 supported by codec: " + hwVideoCodec);
                        break;
                    }
                }
            }
        }
        return codecSet;
    }

    /**
     * Validates if a hardware decoder that supports 4k60 is present
     */
    @LargeTest
    @Test(timeout = CodecTestBase.PER_TEST_TIMEOUT_LARGE_TEST_MS)
    @CddTest(requirements = {"2.2.7.1/5.1/H-1-15"})
    public void test4k60Decoder() throws IOException {
        Set<String> decoderSet = get4k60HwCodecSet(false);

        PerformanceClassEvaluator pce = pcRule.getPerformanceClassEvaluator();
        Requirements.HardwareDecoder4K60Requirement r4k60HwDecoder =
                Requirements.addR5_1__H_1_15().to(pce);
        r4k60HwDecoder.setNumber4KHwDecoders(decoderSet.size());
    }

    /**
     * Validates if a hardware encoder that supports 4k60 is present
     */
    @LargeTest
    @Test(timeout = CodecTestBase.PER_TEST_TIMEOUT_LARGE_TEST_MS)
    @CddTest(requirements = {"2.2.7.1/5.1/H-1-16"})
    public void test4k60Encoder() throws IOException {
        Set<String> encoderSet = get4k60HwCodecSet(true);

        PerformanceClassEvaluator pce = pcRule.getPerformanceClassEvaluator();
        Requirements.HardwareEncoder4K60Requirement r4k60HwEncoder =
                Requirements.addR5_1__H_1_16().to(pce);
        r4k60HwEncoder.setNumber4KHwEncoders(encoderSet.size());
    }

    /**
     * MUST have at least 1 hardware image decoder supporting AVIF Baseline Profile.
     */
    @LargeTest
    @Test(timeout = CodecTestBase.PER_TEST_TIMEOUT_LARGE_TEST_MS)
    @CddTest(requirements = {"5.1/H-1-17"})
    public void testAVIFHwDecoderRequirements() throws Exception {
        int[] profiles = {AV1ProfileMain8, AV1ProfileMain10};
        ArrayList<MediaFormat> formats = new ArrayList<>();
        for (int profile : profiles) {
            MediaFormat format = MediaFormat.createVideoFormat(MIMETYPE_VIDEO_AV1, 3840, 2160);
            format.setInteger(MediaFormat.KEY_PROFILE, profile);
            format.setInteger(MediaFormat.KEY_LEVEL, AV1Level51);
            formats.add(format);
        }
        ArrayList<String> av1HwDecoders =
                selectHardwareCodecs(MIMETYPE_VIDEO_AV1, formats, null, false);
        boolean isDecoded = false;
        if (av1HwDecoders.size() != 0) {
            isDecoded = decodeAVIF(new File(WorkDir.getMediaDirString() + INPUT_FILE));
        }

        PerformanceClassEvaluator pce = pcRule.getPerformanceClassEvaluator();
        Requirements.AVIFBaselineProfileRequirement rAVIFDecoderReq =
                Requirements.addR5_1__H_1_17().to(pce);
        rAVIFDecoderReq.setAvifImageDecoderBoolean(isDecoded);
    }

    /**
     * MUST support AV1 encoder which can encode up to 480p resolution
     * at 30fps and 1Mbps.
     */
    @SmallTest
    @Test(timeout = CodecTestBase.PER_TEST_TIMEOUT_SMALL_TEST_MS)
    @CddTest(requirements = {"2.2.7.1/5.1/H-1-18"})
    public void testAV1EncoderRequirements() throws Exception {
        int width = 720;
        int height = 480;
        String mediaType = MIMETYPE_VIDEO_AV1;
        int requiredFps = 30;
        MediaFormat format = MediaFormat.createVideoFormat(mediaType, width, height);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
        ArrayList<MediaFormat> formats = new ArrayList<>();
        formats.add(format);
        ArrayList<String> av1Encoders = selectCodecs(mediaType, formats, null, true);
        boolean found = false;
        double fps = 0;
        for (String codecName : av1Encoders) {
            MediaCodecInfo info = getCodecInfo(codecName);
            MediaCodecInfo.VideoCapabilities videoCaps =
                            info.getCapabilitiesForType(mediaType).getVideoCapabilities();
            List<PerformancePoint> pps = videoCaps.getSupportedPerformancePoints();
            if (pps != null && pps.size() > 0) {
                PerformancePoint PPRes = new PerformancePoint(width, height, requiredFps);
                for (PerformancePoint pp : pps) {
                    if (pp.covers(PPRes)) {
                        fps = max(fps, pp.getMaxFrameRate());
                        found = true;
                    }
                }
                // found encoder advertising required performance point
                if (found) {
                    break;
                }
            }
            // For non-HW accelerated (SW) encoders we have to rely on their published
            // achievable rates as they do not advertise performance points.
            // The test relies on getLower() as that is the best approximation for what
            // can be achieved.
            Range<Double> reported = videoCaps.getAchievableFrameRatesFor(width, height);
            if (reported != null && reported.getLower() >= requiredFps) {
                fps = reported.getLower();
                found = true;
            }
            if (found) {
                break;
            }
        }

        PerformanceClassEvaluator pce = pcRule.getPerformanceClassEvaluator();
        Requirements.AV1EncoderRequirement rAV1EncoderReq = Requirements.addR5_1__H_1_18().to(pce);
        rAV1EncoderReq.setAv1EncoderFps(fps);
    }
}
