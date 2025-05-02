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

package android.media.decoder.cts;

import static android.media.codec.Flags.apvSupport;
import static android.mediav2.common.cts.CodecTestBase.IS_AT_LEAST_B;

import static com.android.media.extractor.flags.Flags.extractorMp4EnableApv;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.cts.MediaCodecBlockModelHelper;
import android.media.cts.TestArgs;
import android.mediav2.common.cts.OutputManager;
import android.os.Build;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.Presubmit;

import androidx.test.filters.SdkSuppress;
import androidx.test.filters.SmallTest;

import com.android.compatibility.common.util.ApiLevelUtil;
import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.MediaUtils;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Decoder tests with CONFIGURE_FLAG_USE_BLOCK_MODEL.
 */
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.R)
@AppModeFull(reason = "Instant apps cannot access the SD card")
@RunWith(Parameterized.class)
public class DecoderBlockModelTest {
    static final String mInpPrefix = WorkDir.getMediaDirString();
    private static final String TAG = "DecoderBlockModelTest";
    // Input buffers from this input video are queued till LAST_BUFFER_TIMESTAMP_US.
    private static final long LAST_BUFFER_TIMESTAMP_US = 166666;
    private boolean mIsAtLeastR = ApiLevelUtil.isAtLeast(Build.VERSION_CODES.R);
    private String mCodecName;
    private String mTestFile;
    private String mMediaType;

    private static List<Object[]> prepareParamList(List<Object[]> exhaustiveArgsList) {
        final List<Object[]> argsList = new ArrayList<>();
        int argLength = exhaustiveArgsList.get(0).length;
        for (Object[] arg : exhaustiveArgsList) {
            String mediaType = (String) arg[0];
            String testFile = (String) arg[1];
            String[] codecs = MediaUtils.getDecoderNamesForMime(mediaType);
            for (String codec : codecs) {
                if (TestArgs.shouldSkipCodec(codec)) {
                    continue;
                }
                Object[] testArgs = new Object[argLength + 1];
                testArgs[0] = codec;
                testArgs[1] = testFile;
                testArgs[2] = mediaType;
                argsList.add(testArgs);
            }
        }
        return argsList;
    }

    @Parameterized.Parameters(name = "{index}_{0}_{2}")
    public static Collection<Object[]> input() {
        final List<Object[]> exhaustiveArgsList = new ArrayList<>(Arrays.asList(new Object[][]{
                {MediaFormat.MIMETYPE_AUDIO_RAW, "bbb_2ch_48kHz.wav"},
                {MediaFormat.MIMETYPE_AUDIO_MPEG, "sinesweepmp3smpb.mp3"},
                {MediaFormat.MIMETYPE_AUDIO_AMR_WB, "bbb_mono_16kHz_23.85kbps_amrwb.3gp"},
                {MediaFormat.MIMETYPE_AUDIO_AMR_NB, "bbb_mono_8kHz_4.75kbps_amrnb.3gp"},
                {MediaFormat.MIMETYPE_AUDIO_FLAC, "sinesweepflacmp4.mp4"},
                {MediaFormat.MIMETYPE_AUDIO_G711_ALAW, "bbb_2ch_8kHz_alaw.wav"},
                {MediaFormat.MIMETYPE_AUDIO_G711_MLAW, "bbb_2ch_8kHz_mulaw.wav"},
                {MediaFormat.MIMETYPE_AUDIO_MSGSM, "bbb_1ch_8kHz_gsm.wav"},
                {MediaFormat.MIMETYPE_AUDIO_VORBIS,
                        "video_480x360_webm_vp8_333kbps_25fps_vorbis_stereo_128kbps_48000hz.webm"},
                {MediaFormat.MIMETYPE_AUDIO_OPUS,
                        "bbb_s1_320x180_webm_vp8_800kbps_30fps_opus_5ch_320kbps_48000hz.webm"},
                {MediaFormat.MIMETYPE_AUDIO_AAC,
                        "video_480x360_mp4_h264_1000kbps_25fps_aac_stereo_128kbps_44100hz.mp4"},
                {MediaFormat.MIMETYPE_VIDEO_AVC,
                        "video_480x360_mp4_h264_1000kbps_25fps_aac_stereo_128kbps_44100hz.mp4"},
                {MediaFormat.MIMETYPE_VIDEO_HEVC,
                        "video_480x360_mp4_hevc_650kbps_30fps_aac_stereo_128kbps_48000hz.mp4"},
                {MediaFormat.MIMETYPE_VIDEO_VP8,
                        "video_480x360_webm_vp8_333kbps_25fps_vorbis_stereo_128kbps_48000hz.webm"},
                {MediaFormat.MIMETYPE_VIDEO_VP9,
                        "video_480x360_webm_vp9_333kbps_25fps_vorbis_stereo_128kbps_48000hz.webm"},
                {MediaFormat.MIMETYPE_VIDEO_AV1,
                        "video_480x360_webm_av1_400kbps_30fps_vorbis_stereo_128kbps_48000hz.webm"},
        }));
        if (IS_AT_LEAST_B && apvSupport() && extractorMp4EnableApv()) {
            exhaustiveArgsList.addAll(Arrays.asList(new Object[][]{
                    {MediaFormat.MIMETYPE_VIDEO_APV, "pattern_640x480_30fps_16mbps_apv_10bit.mp4"},
            }));
        }
        return prepareParamList(exhaustiveArgsList);
    }

    public DecoderBlockModelTest(String codecName, String testFile, String mediaType) {
        mCodecName = codecName;
        mTestFile = mInpPrefix + testFile;
        mMediaType = mediaType;
    }

    @Presubmit
    @SmallTest
    @ApiTest(apis = "MediaCodec#CONFIGURE_FLAG_USE_BLOCK_MODEL")
    @Test
    public void testDecoderBlockModel() throws InterruptedException {
        if (mMediaType.startsWith("video/")) {
            testDecodeShortVideoWithBlockPerBuffer();
            testDecodeShortVideoWithSharedBlock();
        } else if (mMediaType.startsWith("audio/")) {
            testDecodeShortAudioWithBlockPerBuffer();
            testDecodeShortAudioWithSharedBlock();
        } else {
            fail("unexpected track format: " + mMediaType);
        }
    }

    /**
     * Tests whether decoding a short group-of-pictures succeeds. The test queues a few video
     * frames by obtaining a new block for each frame then signals end-of-stream. The test fails
     * if the decoder doesn't output the queued frames.
     */
    public void testDecodeShortVideoWithBlockPerBuffer() throws InterruptedException {
        assumeTrue("Test needs Android 11", mIsAtLeastR);
        MediaCodecBlockModelHelper.runThread(() -> runDecodeShortVideo(
                LAST_BUFFER_TIMESTAMP_US,
                true /* obtainBlockForEachBuffer */));
    }

    /**
     * Tests whether decoding a short group-of-pictures succeeds. The test queues a few video
     * frames by reusing the existing block then signals end-of-stream. The test fails if the
     * decoder doesn't output the queued frames.
     */
    public void testDecodeShortVideoWithSharedBlock() throws InterruptedException {
        assumeTrue("Test needs Android 11", mIsAtLeastR);
        MediaCodecBlockModelHelper.runThread(() -> runDecodeShortVideo(
                LAST_BUFFER_TIMESTAMP_US,
                false /* obtainBlockForEachBuffer */));
    }

    /**
     * Tests whether decoding a short audio succeeds. The test queues a few audio frames by
     * obtaining a new block for each frame then signals end-of-stream. The test fails if the
     * decoder doesn't output the queued frames.
     */
    public void testDecodeShortAudioWithBlockPerBuffer() throws InterruptedException {
        assumeTrue("Test needs Android 11", mIsAtLeastR);
        MediaCodecBlockModelHelper.runThread(() -> runDecodeShortAudio(
                LAST_BUFFER_TIMESTAMP_US,
                true /* obtainBlockForEachBuffer */));
    }

    /**
     * Tests whether decoding a short audio succeeds. The test queues a few audio frames by reusing
     * the existing block then signals end-of-stream. The test fails if the decoder doesn't
     * output the queued frames.
     */
    public void testDecodeShortAudioWithSharedBlock() throws InterruptedException {
        assumeTrue("Test needs Android 11", mIsAtLeastR);
        MediaCodecBlockModelHelper.runThread(() -> runDecodeShortAudio(
                LAST_BUFFER_TIMESTAMP_US,
                false /* obtainBlockForEachBuffer */));
    }

    private MediaCodecBlockModelHelper.Result runDecodeShortVideo(long lastBufferTimestampUs,
            boolean obtainBlockForEachBuffer) {
        return MediaCodecBlockModelHelper.runDecodeShortVideo(mCodecName,
                MediaCodecBlockModelHelper.getMediaExtractorForMimeType(mTestFile, "video/"),
                lastBufferTimestampUs, obtainBlockForEachBuffer, null, null, null);
    }

    private MediaCodecBlockModelHelper.Result runDecodeShortAudio(long lastBufferTimestampUs,
            boolean obtainBlockForEachBuffer) {
        MediaExtractor mediaExtractor = null;
        MediaCodec mediaCodec = null;
        try {
            mediaExtractor =
                    MediaCodecBlockModelHelper.getMediaExtractorForMimeType(mTestFile, "audio/");
            MediaFormat mediaFormat =
                    mediaExtractor.getTrackFormat(mediaExtractor.getSampleTrackIndex());
            mediaCodec = MediaCodec.createByCodecName(mCodecName);

            List<Long> inputTimestampList = Collections.synchronizedList(new ArrayList<>());
            List<Long> outputTimestampList = Collections.synchronizedList(new ArrayList<>());
            MediaCodecBlockModelHelper.Result result =
                    MediaCodecBlockModelHelper.runComponentWithLinearInput(
                            mediaCodec,
                            null,  // crypto
                            mediaFormat,
                            null,  // surface
                            false,  // encoder
                            new MediaCodecBlockModelHelper.ExtractorInputSlotListener
                                    .Builder()
                                    .setExtractor(mediaExtractor)
                                    .setLastBufferTimestampUs(lastBufferTimestampUs)
                                    .setObtainBlockForEachBuffer(obtainBlockForEachBuffer)
                                    .setTimestampQueue(inputTimestampList)
                                    .build(),
                            new MediaCodecBlockModelHelper.DummyOutputSlotListener(
                                    false /* graphic */, outputTimestampList));
            if (result == MediaCodecBlockModelHelper.Result.SUCCESS) {
                StringBuilder msg = new StringBuilder();
                boolean isOk = OutputManager.isPtsStrictlyIncreasing(
                        new ArrayList<Long>(outputTimestampList), Long.MIN_VALUE, msg);
                assertTrue(msg.toString(), isOk);
            }
            return result;
        } catch (IOException e) {
            throw new RuntimeException("error reading input resource", e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (mediaCodec != null) {
                mediaCodec.stop();
                mediaCodec.release();
            }
            if (mediaExtractor != null) {
                mediaExtractor.release();
            }
        }
    }
}
