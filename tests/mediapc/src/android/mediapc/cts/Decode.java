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

import static android.mediapc.cts.common.CodecMetrics.getMetrics;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.mediapc.cts.common.CodecMetrics;
import android.util.Log;
import android.view.Surface;

import org.junit.After;

import java.util.ArrayList;
import java.util.concurrent.Callable;


/**
 * Wrapper class for trying and testing mediacodec decoder components.
 */
public class Decode extends CodecDecoderPerformanceClassTestBase implements Callable<CodecMetrics> {
    private static final String LOG_TAG = Decode.class.getSimpleName();
    private static final long EACH_FRAME_TIME_INTERVAL_US = 1000000 / 30;
    private static final String WIDEVINE_LICENSE_SERVER_URL =
            "https://proxy.uat.widevine.com/proxy";
    private static final String PROVIDER = "widevine_test";
    private final String mServerURL =
            String.format(
                    "%s?video_id=%s&provider=%s",
                    WIDEVINE_LICENSE_SERVER_URL, "GTS_HW_SECURE_ALL", PROVIDER);

    protected final boolean mIsAsync;
    private final boolean mOwnsSurface;

    private int mInitialFramesToIgnoreCount = 1;
    private long mStartTimeMillis = 0;
    private long mEndTimeMillis = 0;
    private double mFrameDrops;
    private long mRenderedStartTimeUs;

    Decode(String mediaType, String testFile, String codecName, boolean isAsync,
           boolean secureMode) {
        super(mediaType, testFile, codecName, secureMode);
        mIsAsync = isAsync;
        mSurface = MediaCodec.createPersistentInputSurface();
        mOwnsSurface = true;
    }

    Decode(String mediaType, String testFile, String codecName, boolean isAsync) {
        this(mediaType, testFile, codecName, isAsync, false);
    }

    Decode(String mediaType, String testFile, String codecName, Surface surface, boolean isAsync) {
        super(mediaType, testFile, codecName, false);
        mIsAsync = isAsync;
        mSurface = surface;
        mOwnsSurface = false;
    }

    @After
    public void tearDownDecode() {
        if (mSurface != null) {
            if (mOwnsSurface) mSurface.release();
            mSurface = null;
        }
    }

    public void setInitialFramesToIgnoreCount(int count) {
        mInitialFramesToIgnoreCount = count;
    }

    // measure throughput at the output port
    private void onOutputCountListener(int count) {
        // keep the timestamp of the last output frame
        mEndTimeMillis = System.currentTimeMillis();

        // don't count the time for the initial frames that are ignored
        if (count == mInitialFramesToIgnoreCount) {
            mStartTimeMillis = mEndTimeMillis;
        }
    }

    private long getRenderedTimeUs(int frameIndex) {
        return mRenderedStartTimeUs + frameIndex * EACH_FRAME_TIME_INTERVAL_US;
    }

    @Override
    protected void dequeueOutput(int bufferIndex, MediaCodec.BufferInfo info) {
        evalFrameDropsWhileDequeue(bufferIndex, info, mMediaType);
    }

    private void evalFrameDropsWhileDequeue(int bufferIndex, MediaCodec.BufferInfo info,
                             String mediaType) {
        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            mSawOutputEOS = true;
        }

        int outputCount = mOutputCount;
        long nowUs = System.nanoTime() / 1000;
        int initialDelay = mediaType.equals(MediaFormat.MIMETYPE_VIDEO_AV1) ? 8 : 0;

        if (outputCount == 0) {
            // delay rendering the first frame by the specific delay
            mRenderedStartTimeUs = nowUs + initialDelay * EACH_FRAME_TIME_INTERVAL_US;
        }

        if (nowUs > getRenderedTimeUs(outputCount + 1)) {
            // If the current sample timeStamp is greater than the actual presentation timeStamp
            // of the next sample, we will consider it as a frame drop and don't render.
            mFrameDrops++;
            mCodec.releaseOutputBuffer(bufferIndex, false);
        } else if (nowUs > getRenderedTimeUs(outputCount)) {
            // If the current sample timeStamp is greater than the actual presentation timeStamp
            // of the current sample, we can render it.
            mCodec.releaseOutputBuffer(bufferIndex, true);
        } else {
            // If the current sample timestamp is less than the actual presentation timeStamp,
            // We are okay with directly rendering the sample if we are less by not more than
            // half of one sample duration. Otherwise we sleep for how much more we are less
            // than the half of one sample duration.
            if ((getRenderedTimeUs(outputCount) - nowUs) > (EACH_FRAME_TIME_INTERVAL_US / 2)) {
                try {
                    Thread.sleep(((getRenderedTimeUs(outputCount) - nowUs)
                            - (EACH_FRAME_TIME_INTERVAL_US / 2)) / 1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // Restore the interrupted status
                    throw new RuntimeException("the thread caught an interrupted exception"
                            + "instead of sleeping before rendering the sample timestamp" + e);
                }
            }
            mCodec.releaseOutputBuffer(bufferIndex, true);
        }

        if (info.size > 0 && (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
            mOutputCount++;
            if (mOutputCountListener != null) {
                mOutputCountListener.accept(mOutputCount);
            }
        }
    }

    @Override
    protected void resetContext(boolean isAsync, boolean signalEOSWithLastFrame) {
        mInitialFramesToIgnoreCount = 1;
        mStartTimeMillis = 0;
        mEndTimeMillis = 0;
        mFrameDrops = 0;
        mRenderedStartTimeUs = 0;
        super.resetContext(isAsync, signalEOSWithLastFrame);
    }

    private CodecMetrics doDecode() throws Exception {
        MediaFormat format = setUpSource(mTestFile);
        ArrayList<MediaFormat> formats = new ArrayList<>();
        formats.add(format);
        // If the decoder doesn't support the formats, then return 0 to indicate that decode failed
        if (!areFormatsSupported(mCodecName, mMediaType, formats)) {
            return getMetrics(0.0, 0.0);
        }
        mCodec = MediaCodec.createByCodecName(mCodecName);
        mExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
        configureCodec(format, mIsAsync, false, false, mServerURL);
        // TODO(b/251003943) Remove once Surface from SurfaceView is used for secure decoders
        try {
            mCodec.start();
        } catch (Exception e) {
            Log.e(LOG_TAG, "Stopping the test because codec.start() failed.", e);
            mCodec.release();
            return getMetrics(0.0, 0.0);
        }
        // capture timestamps at receipt of output buffers
        setOutputCountListener(i -> onOutputCountListener(i));
        doWork(Integer.MAX_VALUE);
        queueEOS();
        waitForAllOutputs();
        mCodec.stop();
        mCodec.release();
        mExtractor.release();
        tearDownDecode();
        double fps = (mOutputCount - mInitialFramesToIgnoreCount) /
                ((mEndTimeMillis - mStartTimeMillis) / 1000.0);
        Log.d(LOG_TAG, "Decode MediaType: " + mMediaType + " Decoder: " + mCodecName
                + " Achieved fps: " + fps);
        return getMetrics(fps, mFrameDrops / 30);
    }

    @Override
    public CodecMetrics call() throws Exception {
        CodecMetrics metrics = getMetrics(-1.0, 0.0);
        try {
            metrics = doDecode();
        } finally {
            tearDownDecode();
            tearDownCodecDecoderPerformanceClassTestBase();
            tearDownCodecDecoderTestBase();
            tearDownCodecTestBase();
        }
        return metrics;
    }
}
