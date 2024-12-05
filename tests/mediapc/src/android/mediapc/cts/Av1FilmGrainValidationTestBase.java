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

import static android.mediav2.common.cts.DecodeStreamToYuv.getImage;
import static android.mediav2.common.cts.DecodeStreamToYuv.unWrapYUVImage;
import static android.mediav2.common.cts.VideoErrorManager.computeFrameVariance;

import static org.junit.Assert.assertNotNull;

import android.graphics.ImageFormat;
import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.mediav2.common.cts.CodecDecoderTestBase;
import android.mediav2.common.cts.ImageSurface;
import android.mediav2.common.cts.OutputManager;
import android.util.Log;
import android.util.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class Av1FilmGrainValidationTestBase extends CodecDecoderTestBase {
    private static final String LOG_TAG = Av1FilmGrainValidationTestBase.class.getSimpleName();
    private static final String MEDIA_DIR = WorkDir.getMediaDirString();


    /**
     * **Frame Metadata Structure:**
     *  - Frame Index: The index of the frame in the clip.
     *  - Variance Without Film-Grain: The variance of the frame when film-grain is disabled.
     *  - Variance With Film-Grain: The variance of the frame when film-grain is enabled.
     */
    public static class FrameMetadata {
        public final int mFrameIndex;
        public final double mVarWithoutFilmGrain;
        public final double mVarWithFilmGrain;

        FrameMetadata(int frameIndex, double varWithoutFilmGrain, double varWithFilmGrain) {
            mFrameIndex = frameIndex;
            mVarWithoutFilmGrain = varWithoutFilmGrain;
            mVarWithFilmGrain = varWithFilmGrain;
        }
    }

    /**
     * Please refer to the Av1FilmGrainValidationTest.md for details.
     */
    final Map<Integer, FrameMetadata> mRefFrameVarList = Map.ofEntries(
            Map.entry(1, new FrameMetadata(1, 902.981148, 910.197185)),
            Map.entry(3, new FrameMetadata(3, 865.235099, 869.224709)),
            Map.entry(5, new FrameMetadata(5, 871.873536, 878.137057)),
            Map.entry(7, new FrameMetadata(7, 865.805314, 867.937594)),
            Map.entry(11, new FrameMetadata(11, 861.05864, 865.446286)),
            Map.entry(12, new FrameMetadata(12, 1739.076607, 1742.521371)),
            Map.entry(15, new FrameMetadata(15, 863.359298, 867.886144)),
            Map.entry(19, new FrameMetadata(19, 861.721434, 866.071932)),
            Map.entry(24, new FrameMetadata(24, 1729.799078, 1732.138971)),
            Map.entry(25, new FrameMetadata(25, 879.232184, 884.244364)),
            Map.entry(27, new FrameMetadata(27, 857.220242, 860.515852)),
            Map.entry(29, new FrameMetadata(29, 883.072407, 886.974015))
    );
    Map<Integer, Double> mTestFrameVarList = new HashMap<>();

    public Av1FilmGrainValidationTestBase(String decoder, String mediaType, String testFile,
            String allTestParams) {
        super(decoder, mediaType, MEDIA_DIR + testFile, allTestParams);
    }

    @Override
    protected void dequeueOutput(int bufferIndex, MediaCodec.BufferInfo info) {
        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            mSawOutputEOS = true;
        }
        if (ENABLE_LOGS) {
            Log.v(LOG_TAG, "output: id: " + bufferIndex + " flags: " + info.flags + " size: "
                    + info.size + " timestamp: " + info.presentationTimeUs);
        }
        if (info.size > 0 && (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
            mOutputBuff.saveOutPTS(info.presentationTimeUs);
            mOutputCount++;
        }
        mCodec.releaseOutputBuffer(bufferIndex, mSurface != null);
        if (info.size > 0) {
            try (Image image = mImageSurface.getImage(1000)) {
                assertNotNull("no image received from surface \n" + mTestConfig + mTestEnv, image);
                if (mRefFrameVarList.containsKey(mOutputCount - 1)) {
                    MediaFormat format = getOutputFormat();
                    ArrayList<byte[]> data = unWrapYUVImage(getImage(image));
                    Pair<Double, Integer> var =
                            computeFrameVariance(getWidth(format), getHeight(format), data.get(0));
                    double frameVariance = var.first / var.second;
                    mTestFrameVarList.put(mOutputCount - 1, frameVariance);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // Restore the interrupted status
                throw new RuntimeException(e);
            }
        }
    }

    public void doDecode() throws Exception {
        MediaFormat format = setUpSource(mTestFile);
        mImageSurface = new ImageSurface();
        setUpSurface(getWidth(format), getHeight(format), ImageFormat.YUV_420_888,
                1, 0, null);
        mOutputBuff = new OutputManager();
        mCodec = MediaCodec.createByCodecName(mCodecName);
        configureCodec(format, true, true, false);
        mCodec.start();
        doWork(Integer.MAX_VALUE);
        queueEOS();
        waitForAllOutputs();
        mCodec.stop();
        mCodec.release();
        mExtractor.release();
    }
}
