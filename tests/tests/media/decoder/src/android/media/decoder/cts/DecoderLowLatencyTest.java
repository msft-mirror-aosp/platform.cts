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

package android.media.decoder.cts;

import static android.media.decoder.cts.DecoderTest.getAssetFileDescriptorFor;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.res.AssetFileDescriptor;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.cts.MediaCodecWrapper;
import android.media.cts.MediaHeavyPresubmitTest;
import android.media.cts.MediaTestBase;
import android.media.cts.NdkMediaCodec;
import android.media.cts.SdkMediaCodec;
import android.os.Build;
import android.platform.test.annotations.AppModeFull;
import android.util.Log;
import android.view.Surface;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;

import com.android.compatibility.common.util.DeviceReportLog;
import com.android.compatibility.common.util.MediaUtils;
import com.android.compatibility.common.util.NonMainlineTest;
import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.ByteBuffer;

@MediaHeavyPresubmitTest
@AppModeFull(reason = "There should be no instant apps specific behavior related to decoders")
@RunWith(AndroidJUnit4.class)
public class DecoderLowLatencyTest extends MediaTestBase {
    private static final String TAG = "DecoderLowLatencyTest";
    private static final String REPORT_LOG_NAME = "CtsMediaDecoderTestCases";

    @Before
    @Override
    public void setUp() throws Throwable {
        super.setUp();
    }

    @After
    @Override
    public void tearDown() {
        super.tearDown();
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.R)
    @Test
    public void testLowLatencyVp9At1280x720() throws Exception {
        testLowLatencyVideo(
                "video_1280x720_webm_vp9_csd_309kbps_25fps_vorbis_stereo_128kbps_48000hz.webm", 300,
                false /* useNdk */);
        testLowLatencyVideo(
                "video_1280x720_webm_vp9_csd_309kbps_25fps_vorbis_stereo_128kbps_48000hz.webm", 300,
                true /* useNdk */);
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.R)
    @Test
    public void testLowLatencyVp9At1920x1080() throws Exception {
        testLowLatencyVideo(
                "bbb_s2_1920x1080_webm_vp9_0p41_10mbps_60fps_vorbis_6ch_384kbps_22050hz.webm", 300,
                false /* useNdk */);
        testLowLatencyVideo(
                "bbb_s2_1920x1080_webm_vp9_0p41_10mbps_60fps_vorbis_6ch_384kbps_22050hz.webm", 300,
                true /* useNdk */);
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.R)
    @Test
    public void testLowLatencyVp9At3840x2160() throws Exception {
        testLowLatencyVideo(
                "bbb_s2_3840x2160_webm_vp9_0p51_20mbps_60fps_vorbis_6ch_384kbps_32000hz.webm", 300,
                false /* useNdk */);
        testLowLatencyVideo(
                "bbb_s2_3840x2160_webm_vp9_0p51_20mbps_60fps_vorbis_6ch_384kbps_32000hz.webm", 300,
                true /* useNdk */);
    }

    @NonMainlineTest
    @Test
    public void testLowLatencyAVCAt1280x720() throws Exception {
        testLowLatencyVideo(
                "video_1280x720_mp4_h264_1000kbps_25fps_aac_stereo_128kbps_44100hz.mp4", 300,
                false /* useNdk */);
        testLowLatencyVideo(
                "video_1280x720_mp4_h264_1000kbps_25fps_aac_stereo_128kbps_44100hz.mp4", 300,
                true /* useNdk */);
    }

    @NonMainlineTest
    @Test
    public void testLowLatencyHEVCAt480x360() throws Exception {
        testLowLatencyVideo(
                "video_480x360_mp4_hevc_650kbps_30fps_aac_stereo_128kbps_48000hz.mp4", 300,
                false /* useNdk */);
        testLowLatencyVideo(
                "video_480x360_mp4_hevc_650kbps_30fps_aac_stereo_128kbps_48000hz.mp4", 300,
                true /* useNdk */);
    }

    private void testLowLatencyVideo(String testVideo, int frameCount, boolean useNdk)
            throws Exception {
        AssetFileDescriptor fd = getAssetFileDescriptorFor(testVideo);
        MediaExtractor extractor = new MediaExtractor();
        extractor.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
        fd.close();

        MediaFormat format = null;
        int trackIndex = -1;
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            format = extractor.getTrackFormat(i);
            if (format.getString(MediaFormat.KEY_MIME).startsWith("video/")) {
                trackIndex = i;
                break;
            }
        }

        assertTrue("No video track was found", trackIndex >= 0);

        extractor.selectTrack(trackIndex);
        format.setFeatureEnabled(MediaCodecInfo.CodecCapabilities.FEATURE_LowLatency,
                true /* enable */);

        MediaCodecList mcl = new MediaCodecList(MediaCodecList.ALL_CODECS);
        String decoderName = mcl.findDecoderForFormat(format);
        if (decoderName == null) {
            MediaUtils.skipTest("no low latency decoder for " + format);
            return;
        }
        String entry = (useNdk ? "NDK" : "SDK");
        Log.v(TAG, "found " + entry + " decoder " + decoderName + " for format: " + format);

        Surface surface = getActivity().getSurfaceHolder().getSurface();
        MediaCodecWrapper decoder = null;
        if (useNdk) {
            decoder = new NdkMediaCodec(decoderName);
        } else {
            decoder = new SdkMediaCodec(MediaCodec.createByCodecName(decoderName));
        }
        format.removeFeature(MediaCodecInfo.CodecCapabilities.FEATURE_LowLatency);
        format.setInteger(MediaFormat.KEY_LOW_LATENCY, 1);
        decoder.configure(format, 0 /* flags */, surface);
        decoder.start();

        if (!useNdk) {
            decoder.getInputBuffers();
        }
        ByteBuffer[] codecOutputBuffers = decoder.getOutputBuffers();
        String decoderOutputFormatString = null;

        // start decoding
        final long kTimeOutUs = 1000000;  // 1 second
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        int bufferCounter = 0;
        long[] latencyMs = new long[frameCount];
        boolean waitingForOutput = false;
        long startTimeMs = System.currentTimeMillis();
        while (bufferCounter < frameCount) {
            if (!waitingForOutput) {
                int inputBufferId = decoder.dequeueInputBuffer(kTimeOutUs);
                if (inputBufferId < 0) {
                    Log.v(TAG, "no input buffer");
                    break;
                }

                ByteBuffer dstBuf = decoder.getInputBuffer(inputBufferId);

                int sampleSize = extractor.readSampleData(dstBuf, 0 /* offset */);
                long presentationTimeUs = 0;
                if (sampleSize < 0) {
                    Log.v(TAG, "had input EOS, early termination at frame " + bufferCounter);
                    break;
                } else {
                    presentationTimeUs = extractor.getSampleTime();
                }

                startTimeMs = System.currentTimeMillis();
                decoder.queueInputBuffer(
                        inputBufferId,
                        0 /* offset */,
                        sampleSize,
                        presentationTimeUs,
                        0 /* flags */);

                extractor.advance();
                waitingForOutput = true;
            }

            int outputBufferId = decoder.dequeueOutputBuffer(info, kTimeOutUs);

            if (outputBufferId >= 0) {
                waitingForOutput = false;
                //Log.d(TAG, "got output, size " + info.size + ", time " + info.presentationTimeUs);
                latencyMs[bufferCounter++] = System.currentTimeMillis() - startTimeMs;
                // TODO: render the frame and find the rendering time to calculate the total delay
                decoder.releaseOutputBuffer(outputBufferId, false /* render */);
            } else if (outputBufferId == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                codecOutputBuffers = decoder.getOutputBuffers();
                Log.d(TAG, "output buffers have changed.");
            } else if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                decoderOutputFormatString = decoder.getOutputFormatString();
                Log.d(TAG, "output format has changed to " + decoderOutputFormatString);
            } else {
                fail("No output buffer returned without frame delay, status " + outputBufferId);
            }
        }

        assertTrue("No INFO_OUTPUT_FORMAT_CHANGED from decoder", decoderOutputFormatString != null);

        long latencyMean = 0;
        long latencyMax = 0;
        int maxIndex = 0;
        for (int i = 0; i < bufferCounter; ++i) {
            latencyMean += latencyMs[i];
            if (latencyMs[i] > latencyMax) {
                latencyMax = latencyMs[i];
                maxIndex = i;
            }
        }
        if (bufferCounter > 0) {
            latencyMean /= bufferCounter;
        }
        Log.d(TAG, entry + " latency average " + latencyMean + " ms, max " + latencyMax +
                " ms at frame " + maxIndex);

        DeviceReportLog log = new DeviceReportLog(REPORT_LOG_NAME, "video_decoder_latency");
        String mime = format.getString(MediaFormat.KEY_MIME);
        int width = format.getInteger(MediaFormat.KEY_WIDTH);
        int height = format.getInteger(MediaFormat.KEY_HEIGHT);
        log.addValue("codec_name", decoderName, ResultType.NEUTRAL, ResultUnit.NONE);
        log.addValue("mime_type", mime, ResultType.NEUTRAL, ResultUnit.NONE);
        log.addValue("width", width, ResultType.NEUTRAL, ResultUnit.NONE);
        log.addValue("height", height, ResultType.NEUTRAL, ResultUnit.NONE);
        log.addValue("video_res", testVideo, ResultType.NEUTRAL, ResultUnit.NONE);
        log.addValue("decode_to", surface == null ? "buffer" : "surface",
                ResultType.NEUTRAL, ResultUnit.NONE);

        log.addValue("average_latency", latencyMean, ResultType.LOWER_BETTER, ResultUnit.MS);
        log.addValue("max_latency", latencyMax, ResultType.LOWER_BETTER, ResultUnit.MS);

        log.submit(getInstrumentation());

        decoder.stop();
        decoder.release();
        extractor.release();
    }
}
