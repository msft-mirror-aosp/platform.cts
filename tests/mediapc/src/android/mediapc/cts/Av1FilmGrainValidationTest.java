/*
 * Copyright (C) 2024 The Android Open Source Project
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

import static android.mediapc.cts.CodecTestBase.selectHardwareCodecs;
import static android.mediapc.cts.CodecTestBase.PER_TEST_TIMEOUT_SMALL_TEST_MS;

import android.media.MediaFormat;
import android.mediapc.cts.Av1FilmGrainValidationTestBase.FrameMetadata;
import android.mediapc.cts.common.PerformanceClassEvaluator;
import android.mediapc.cts.common.Requirements;
import android.util.Log;

import androidx.test.filters.SmallTest;

import com.android.compatibility.common.util.CddTest;

import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The test verifies if film grain effect is applied to the output of av1 decoder.
 * <p>
 * An av1 test clip with film grain enabled is decoded using av1 decoders present on the device.
 * For a select few frames, their variance is computed. This metric is compared against a
 * reference variance value which is obtained by decoding the same clip with film grain filter
 * disabled. The test value is expected to be larger than the reference value by a threshold margin.
 */
public class Av1FilmGrainValidationTest {
    private static final String LOG_TAG = Av1FilmGrainValidationTest.class.getSimpleName();
    private static final String INPUT_FILE = "crowd_run_854x480_1mbps_av1_40fg.mp4";
    private static final double TOLERANCE = 0.95;

    @Rule
    public final TestName mTestName = new TestName();

    /**
     * Check description of class {@link Av1FilmGrainValidationTest}
     */
    @SmallTest
    @Test(timeout = PER_TEST_TIMEOUT_SMALL_TEST_MS)
    @CddTest(requirement = "2.2.7.1/5.1/H-1-14")
    public void testAv1FilmGrainRequirement() {
        int width = 854;
        int height = 480;
        String mediaType = MediaFormat.MIMETYPE_VIDEO_AV1;
        MediaFormat format = MediaFormat.createVideoFormat(mediaType, width, height);
        ArrayList<MediaFormat> formats = new ArrayList<>();
        formats.add(format);
        ArrayList<String> av1HwDecoders =
                selectHardwareCodecs(mediaType, formats, null, false);

        boolean isDecoded = false;
        int numFramesWithoutFilmGrain = Integer.MAX_VALUE;
        for (String av1HwDecoder : av1HwDecoders) {
            Av1FilmGrainValidationTestBase av1Dec =
                    new Av1FilmGrainValidationTestBase(av1HwDecoder, mediaType, INPUT_FILE, null);
            try {
                av1Dec.doDecode();
                isDecoded = true;
            } catch (Exception e) {
                isDecoded = false;
                Log.v(LOG_TAG, av1HwDecoder + " failed to decode input av1 clip.");
            }

            if (isDecoded) {
                numFramesWithoutFilmGrain = 0;
                for (Map.Entry<Integer, FrameMetadata> entry : av1Dec.mRefFrameVarList.entrySet()) {
                    Integer frameId = entry.getKey();
                    FrameMetadata metadata = entry.getValue();
                    double refVariance = metadata.mVarWithoutFilmGrain + (
                            (metadata.mVarWithFilmGrain - metadata.mVarWithoutFilmGrain)
                                    * TOLERANCE);
                    double testVariance = av1Dec.mTestFrameVarList.get(frameId);
                    if (testVariance < refVariance) {
                        numFramesWithoutFilmGrain++;
                    }
                }
            }
            if (isDecoded && numFramesWithoutFilmGrain == 0) {
                break;
            }
        }

        PerformanceClassEvaluator pce = new PerformanceClassEvaluator(this.mTestName);
        Requirements.AV1HardwareDecoderRequirement rAV1DecoderReq =
                Requirements.addR5_1__H_1_14().to(pce);
        rAV1DecoderReq.setAv1DecoderRequirementBoolean(isDecoded);
        rAV1DecoderReq.setAv1FramesWithoutFilmGrain(numFramesWithoutFilmGrain);
        pce.submitAndCheck();
    }
}
