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

import static android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface;

import static org.junit.Assert.assertTrue;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.cts.OutputSurface;
import android.mediav2.common.cts.CodecDecoderTestBase;
import android.mediav2.common.cts.OutputManager;

import androidx.test.filters.SmallTest;

import com.android.compatibility.common.util.ApiTest;

import org.junit.After;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * SurfaceTexture captures frames from an image stream. The image stream could be either camera
 * preview or video decoder. It updates the texture image to the most recent frame from the image
 * stream. However if the producer is a video decoder and it is configured to not drop frames, then
 * it is supposed to update with the first frame in its queue. This test verifies the same.
 */
@SmallTest
@RunWith(Parameterized.class)
public class SurfaceTextureMediaCodecProducerTest extends CodecDecoderTestBase {
    private static final String MEDIA_DIR = WorkDir.getMediaDirString();
    private static final int FRAME_LIMIT = 2;

    private OutputSurface mEGLWindowOutSurface;
    private final long[] mRenderTimesNs = new long[FRAME_LIMIT];

    public SurfaceTextureMediaCodecProducerTest(String codecName, String mediaType, String testFile,
            String allTestParams) {
        super(codecName, mediaType, MEDIA_DIR + testFile, allTestParams);
    }

    @After
    public void tearDown() {
        mSurface = null;
        if (mEGLWindowOutSurface != null) {
            mEGLWindowOutSurface.release();
            mEGLWindowOutSurface = null;
        }
    }

    @Parameterized.Parameters(name = "{index}({0}_{1})")
    public static Collection<Object[]> input() {
        // mediaType, testfile
        final List<Object[]> exhaustiveArgsList = new ArrayList<>(Arrays.asList(new Object[][]{
                {MediaFormat.MIMETYPE_VIDEO_AVC, "bbb_320x240_30fps_avc_baseline_l13.mp4"},
        }));
        return prepareParamList(exhaustiveArgsList, false, false, true, false);
    }

    protected void dequeueOutput(int bufferIndex, MediaCodec.BufferInfo info) {
        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            mSawOutputEOS = true;
        }
        if (info.size > 0 && (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
            mOutputBuff.saveOutPTS(info.presentationTimeUs);
            mRenderTimesNs[mOutputCount] = info.presentationTimeUs * 1000;
            mCodec.releaseOutputBuffer(bufferIndex, mRenderTimesNs[mOutputCount]);
            mOutputCount++;
        }
    }

    /**
     * Check description of class {@link SurfaceTextureMediaCodecProducerTest}
     */
    @ApiTest(apis = {"android.graphics.SurfaceTexture#updateTexImage",
            "android.media.MediaFormat#KEY_ALLOW_FRAME_DROP"})
    @Test(timeout = PER_TEST_TIMEOUT_SMALL_TEST_MS)
    public void testUpdateTexImage() throws IOException, InterruptedException {
        MediaFormat format = setUpSource(mTestFile);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_ALLOW_FRAME_DROP, 0);
        mEGLWindowOutSurface = new OutputSurface(getWidth(format), getHeight(format));
        mSurface = mEGLWindowOutSurface.getSurface();
        mCodec = MediaCodec.createByCodecName(mCodecName);
        configureCodec(format, false, true, false);
        MediaFormat inpFormat = mCodec.getInputFormat();
        StringBuilder msg = new StringBuilder();
        msg.append("Component is configured with surface that is allowed to drop frames,"
                + " no definitive way to validate timestamps");
        Assume.assumeTrue(msg.toString(), inpFormat.containsKey(MediaFormat.KEY_ALLOW_FRAME_DROP));
        Assume.assumeTrue(msg.toString(),
                inpFormat.getInteger(MediaFormat.KEY_ALLOW_FRAME_DROP) == 0);
        mOutputBuff = new OutputManager();
        mCodec.start();
        doWork(FRAME_LIMIT);
        queueEOS();
        waitForAllOutputs();
        boolean isPass = true;
        msg.setLength(0);
        for (int i = 0; i < FRAME_LIMIT; i++) {
            mEGLWindowOutSurface.awaitNewImage();
            long receivedPts = mEGLWindowOutSurface.getTimestamp();
            if (receivedPts != mRenderTimesNs[i]) {
                msg.append(String.format("UpdateTexImage is not returning correct timestamp,"
                        + " expected %d, received %d", mRenderTimesNs[i], receivedPts));
                isPass = false;
                break;
            }
        }
        mCodec.stop();
        mCodec.release();
        assertTrue(msg.toString(), isPass);
    }
}
