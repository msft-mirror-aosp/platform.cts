/*
 * Copyright (C) 2021 The Android Open Source Project
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

import android.mediapc.cts.common.AutoConstants;
import android.mediapc.cts.common.PerformanceClassEvaluator;
import android.mediapc.cts.common.PerformanceClassTestRule;
import android.mediapc.cts.common.Preconditions;
import android.mediapc.cts.common.Requirements;
import android.mediapc.cts.common.Utils;
import android.mediav2.common.cts.CodecTestBase;

import androidx.test.filters.LargeTest;

import com.android.compatibility.common.util.CddTest;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Collection;

/**
 * The following test class validates the frame drops of a playback for the hardware decoders
 * under the load condition (Transcode + Audio Playback).
 */
@RunWith(Parameterized.class)
public class FrameDropTest extends FrameDropTestBase {
    private static final String LOG_TAG = FrameDropTest.class.getSimpleName();

    @Rule(order = 1)
    public final PerformanceClassTestRule pcRule =
            PerformanceClassTestRule.with(
                    Preconditions.BASELINE, REQUIRES_AAC_DECODER, AVC_PRE_CONDITIONS);

    public FrameDropTest(String mediaType, String decoderName, boolean isAsync) {
        super(mediaType, decoderName, isAsync);
    }

    // Returns the list of parameters with mediaTypes and their hardware decoders
    // combining with sync and async modes.
    // Parameters {0}_{1}_{2} -- MediaType_DecoderName_isAsync
    @Parameterized.Parameters(name = "{index}_{0}_{1}_{2}")
    public static Collection<Object[]> inputParams() {
        return prepareArgumentsList(null);
    }

    private int testDecodeToSurface(int frameRate, String[] testFiles) throws Exception {
        PlaybackFrameDrop playbackFrameDrop = new PlaybackFrameDrop(mMediaType, mDecoderName,
                testFiles, mSurface, frameRate, mIsAsync);
        return playbackFrameDrop.getFrameDropCount();
    }

    /**
     * This test validates that the playback of 1920x1080 resolution asset of 3 seconds duration
     * at 30 fps for R perf class, for at least 30 seconds worth of  frames or for 31 seconds of
     * elapsed time. must not drop more than 3 frames for R perf class.
     */
    @LargeTest
    @Test(timeout = CodecTestBase.PER_TEST_TIMEOUT_LARGE_TEST_MS)
    @CddTest(requirements = {"2.2.7.1/5.3/H-1-1"})
    public void test30Fps() throws Exception {
        Utils.assumeMpcIfDeclaredIsAnyOf(AutoConstants.MPC_30);
        int frameRate = 30;

        PerformanceClassEvaluator pce = pcRule.getPerformanceClassEvaluator();
        Requirements.FrameDropRequirement r5_3__H_1_1_R =
                Requirements.addR5_3__H_1_1().withConfig1080P30Fps().to(pce);

        String[] testFiles = new String[]{m1080p30FpsTestFiles.get(mMediaType)};
        int framesDropped = testDecodeToSurface(frameRate, testFiles);

        r5_3__H_1_1_R.setFrameDropsPer30Sec(framesDropped);
    }

    /**
     * This test validates that the playback of 1920x1080 resolution asset of 3 seconds duration
     * at 60 fps for S/T perf class,  for at least 30 seconds worth of  frames or for 31 seconds of
     * elapsed time. must not drop more than 6 frames for S perf class / 3 frames for T perf class.
     */
    @LargeTest
    @Test(timeout = CodecTestBase.PER_TEST_TIMEOUT_LARGE_TEST_MS)
    @CddTest(requirements = {"2.2.7.1/5.3/H-1-1"})
    public void test60Fps() throws Exception {
        Utils.assumeMpcIfDeclaredIsAnyOf(AutoConstants.MPC_31, AutoConstants.MPC_33);
        int frameRate = 60;

        PerformanceClassEvaluator pce = pcRule.getPerformanceClassEvaluator();
        Requirements.FrameDropRequirement r5_3__H_1_1_ST =
                Requirements.addR5_3__H_1_1().withConfig1080P60Fps().to(pce);

        String[] testFiles = new String[]{m1080p60FpsTestFiles.get(mMediaType)};
        int framesDropped = testDecodeToSurface(frameRate, testFiles);

        r5_3__H_1_1_ST.setFrameDropsPer30Sec(framesDropped);
    }

    /**
     * This test validates that the playback of 3840x2160 resolution asset of 3 seconds duration
     * at 60 fps for U perf class, for at least 30 seconds worth of frames or for 31 seconds of
     * elapsed time. must not drop more than 3 frames for U perf class.
     */
    @LargeTest
    @Test(timeout = CodecTestBase.PER_TEST_TIMEOUT_LARGE_TEST_MS)
    @CddTest(requirements = {"2.2.7.1/5.3/H-1-1"})
    public void test4k() throws Exception {
        Utils.assumeMpcIfDeclaredIsAtLeast(AutoConstants.MPC_34);
        int frameRate = 60;

        PerformanceClassEvaluator pce = pcRule.getPerformanceClassEvaluator();
        Requirements.FrameDropRequirement r5_3__H_1_1_U =
                Requirements.addR5_3__H_1_1().withConfig4K60Fps().to(pce);

        String[] testFiles = new String[]{m2160p60FpsTestFiles.get(mMediaType)};
        int framesDropped = testDecodeToSurface(frameRate, testFiles);

        r5_3__H_1_1_U.setFrameDropsPer30Sec(framesDropped);
    }
}
