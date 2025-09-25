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
import android.mediapc.cts.common.CodecMetrics;
import android.mediav2.common.cts.EncoderConfigParams;
import android.mediav2.common.cts.OutputManager;
import android.util.Log;

import java.io.IOException;
import java.util.concurrent.Callable;

/**
 * Wrapper class for trying and testing encoder components.
 */
public class Encode extends CodecEncoderPerformanceClassTestBase implements Callable<CodecMetrics> {
    private static final String LOG_TAG = Encode.class.getSimpleName();

    private final boolean mIsAsync;

    private int mInitialFramesToIgnoreCount = 1;
    private long mStartTimeMillis = 0;
    private long mEndTimeMillis = 0;

    Encode(String mediaType, String codecName, boolean isAsync, EncoderConfigParams[] params) {
        super(mediaType, codecName, params);
        mIsAsync = isAsync;
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

    private CodecMetrics doEncode() throws IOException, InterruptedException {
        mActiveEncCfg = mEncCfgParams[0];
        mActiveRawRes = mIsAudio ? INPUT_AUDIO_FILE : INPUT_VIDEO_FILE;
        setUpSource(mActiveRawRes.mFileName);
        mOutputBuff = new OutputManager();
        mCodec = MediaCodec.createByCodecName(mCodecName);
        configureCodec(mActiveEncCfg.getFormat(), mIsAsync, false, true);
        mCodec.start();
        // capture timestamps at receipt of output buffers
        setOutputCountListener(i -> onOutputCountListener(i));
        doWork(Integer.MAX_VALUE);
        queueEOS();
        waitForAllOutputs();
        mCodec.stop();
        mCodec.release();
        double fps = (mOutputCount - mInitialFramesToIgnoreCount)
                / ((mEndTimeMillis - mStartTimeMillis) / 1000.0);
        Log.d(LOG_TAG,
                "MediaType: " + mMediaType + " Codec: " + mCodecName + " Achieved fps: " + fps);
        return getMetrics(fps, 0.0);
    }

    @Override
    public CodecMetrics call() throws Exception {
        CodecMetrics metrics = getMetrics(0.0, 0.0);
        try {
            metrics = doEncode();
        } finally {
            tearDownCodecEncoderTestBase();
            tearDownCodecTestBase();
        }
        return metrics;
    }
}
