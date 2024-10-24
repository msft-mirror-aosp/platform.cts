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
            Map.entry(3, new FrameMetadata(3, 2385.037126, 2417.27729)),
            Map.entry(10, new FrameMetadata(10, 2342.782887, 2384.061935)),
            Map.entry(55, new FrameMetadata(55, 2204.930969, 2240.242686)),
            Map.entry(70, new FrameMetadata(70, 2286.123113, 2319.397801)),
            Map.entry(92, new FrameMetadata(92, 2365.240550, 2396.21792)),
            Map.entry(122, new FrameMetadata(122, 2253.176403, 2287.724775)),
            Map.entry(131, new FrameMetadata(131, 2144.242954, 2177.774318)),
            Map.entry(139, new FrameMetadata(139, 2158.459979, 2191.296022)),
            Map.entry(151, new FrameMetadata(151, 2180.469236, 2214.399074)),
            Map.entry(169, new FrameMetadata(169, 2356.567787, 2390.686406)),
            Map.entry(174, new FrameMetadata(174, 2360.921796, 2391.112868)),
            Map.entry(178, new FrameMetadata(178, 2398.315402, 2432.657012))
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
