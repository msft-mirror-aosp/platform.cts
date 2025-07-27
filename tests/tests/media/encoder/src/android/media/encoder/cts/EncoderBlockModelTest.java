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

package android.media.encoder.cts;

import static android.media.codec.Flags.apvSupport;
import static android.mediav2.common.cts.CodecTestBase.IS_AT_LEAST_B;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.hardware.HardwareBuffer;
import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaCodec.CodecException;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.cts.MediaCodecAsyncHelper;
import android.media.cts.MediaCodecBlockModelHelper;
import android.media.cts.TestArgs;
import android.mediav2.common.cts.OutputManager;
import android.os.Build;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.Presubmit;
import android.util.Log;

import androidx.test.filters.SdkSuppress;
import androidx.test.filters.SmallTest;

import com.android.compatibility.common.util.ApiLevelUtil;
import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.MediaUtils;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Encoder tests with CONFIGURE_FLAG_USE_BLOCK_MODEL.
 */
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.R)
@AppModeFull(reason = "Instant apps cannot access the SD card")
@RunWith(Parameterized.class)
public class EncoderBlockModelTest {
    private static final String TAG = "EncoderBlockModelTest";
    private static final long LAST_BUFFER_TIMESTAMP_US = 1000000;
    private boolean mIsAtLeastR = ApiLevelUtil.isAtLeast(Build.VERSION_CODES.R);
    static final String mInpPrefix = WorkDir.getMediaDirString();

    private String mCodecName;
    private String mMediaType;

    @Parameterized.Parameters(name = "{index}_{0}_{1}")
    public static Collection<Object[]> input() {
        final List<Object[]> exhaustiveArgsList = new ArrayList<>();
        List<String> mediaTypes = new ArrayList<>(Arrays.asList(
                MediaFormat.MIMETYPE_AUDIO_AAC,
                MediaFormat.MIMETYPE_AUDIO_OPUS,
                MediaFormat.MIMETYPE_AUDIO_AMR_NB,
                MediaFormat.MIMETYPE_AUDIO_AMR_WB,
                MediaFormat.MIMETYPE_AUDIO_FLAC,
                MediaFormat.MIMETYPE_VIDEO_AVC,
                MediaFormat.MIMETYPE_VIDEO_HEVC,
                MediaFormat.MIMETYPE_VIDEO_VP8,
                MediaFormat.MIMETYPE_VIDEO_VP9,
                MediaFormat.MIMETYPE_VIDEO_AV1
        ));
        if (IS_AT_LEAST_B && apvSupport()) {
            mediaTypes.add(MediaFormat.MIMETYPE_VIDEO_APV);
        }
        for (String mediaType : mediaTypes) {
            String[] codecs = MediaUtils.getEncoderNamesForMime(mediaType);
            for (String codec : codecs) {
                if (TestArgs.shouldSkipCodec(codec)) {
                    continue;
                }
                exhaustiveArgsList.add(new Object[] {codec, mediaType});
            }
        }
        return exhaustiveArgsList;
    }

    public EncoderBlockModelTest(String codecName, String mediaType) {
        mCodecName = codecName;
        mMediaType = mediaType;
    }

    @Presubmit
    @SmallTest
    @ApiTest(apis = "MediaCodec#CONFIGURE_FLAG_USE_BLOCK_MODEL")
    @Test
    public void testEncoderBlockModel() throws InterruptedException {
        if (mMediaType.startsWith("video/")) {
            testEncodeShortVideo();
        } else if (mMediaType.startsWith("audio/")) {
            testEncodeShortAudio();
        } else {
            fail("unexpected track format: " + mMediaType);
        }
    }

    /**
     * Tests whether encoding a short audio succeeds. The test queues a few audio frames
     * then signals end-of-stream. The test fails if the encoder doesn't output the queued frames.
     */
    public void testEncodeShortAudio() throws InterruptedException {
        assumeTrue("Test needs Android 11", mIsAtLeastR);
        MediaCodecBlockModelHelper.runThread(() -> runEncodeShortAudio());
    }

    /**
     * Tests whether encoding a short video succeeds. The test queues a few video frames
     * then signals end-of-stream. The test fails if the encoder doesn't output the queued frames.
     */
    public void testEncodeShortVideo() throws InterruptedException {
        assumeTrue("Test needs Android 11", mIsAtLeastR);
        MediaCodecBlockModelHelper.runThread(() -> runEncodeShortVideo());
    }

    private MediaCodecBlockModelHelper.Result runEncodeShortAudio() {
        MediaExtractor mediaExtractor = null;
        MediaCodec mediaCodec = null;
        try {
            mediaExtractor = MediaCodecBlockModelHelper.getMediaExtractorForMimeType(
                    mInpPrefix + "okgoogle123_good.wav", MediaFormat.MIMETYPE_AUDIO_RAW);
            MediaFormat mediaFormat = new MediaFormat(
                    mediaExtractor.getTrackFormat(mediaExtractor.getSampleTrackIndex()));
            mediaFormat.setString(MediaFormat.KEY_MIME, mMediaType);
            mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 128000);
            mediaCodec = MediaCodec.createByCodecName(mCodecName);

            List<Long> inputTimestampList = Collections.synchronizedList(new ArrayList<>());
            List<Long> outputTimestampList = Collections.synchronizedList(new ArrayList<>());
            MediaCodecBlockModelHelper.Result result =
                    MediaCodecBlockModelHelper.runComponentWithLinearInput(
                            mediaCodec,
                            null,  // crypto
                            mediaFormat,
                            null,  // surface
                            true,  // encoder
                            new MediaCodecBlockModelHelper.ExtractorInputSlotListener
                                    .Builder()
                                    .setExtractor(mediaExtractor)
                                    .setLastBufferTimestampUs(LAST_BUFFER_TIMESTAMP_US)
                                    .setTimestampQueue(inputTimestampList)
                                    .build(),
                            new MediaCodecBlockModelHelper.DummyOutputSlotListener(
                                    false /* graphic */, outputTimestampList));
            if (result == MediaCodecBlockModelHelper.Result.SUCCESS) {
                StringBuilder msg = new StringBuilder();
                boolean isOk = OutputManager.isPtsStrictlyIncreasing(
                        new ArrayList<Long>(outputTimestampList), -1L, msg);
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

    private MediaCodecBlockModelHelper.Result runEncodeShortVideo() {
        final int kWidth = 176;
        final int kHeight = 144;
        final int kFrameRate = 15;
        MediaCodec mediaCodec = null;
        ArrayList<HardwareBuffer> hardwareBuffers = new ArrayList<>();
        try {
            MediaFormat mediaFormat = MediaFormat.createVideoFormat(mMediaType, kWidth, kHeight);
            mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, kFrameRate);
            mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 1000000);
            mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
            mediaFormat.setInteger(
                    MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
            mediaCodec = MediaCodec.createByCodecName(mCodecName);

            long usage = HardwareBuffer.USAGE_CPU_READ_OFTEN;
            usage |= HardwareBuffer.USAGE_CPU_WRITE_OFTEN;
            if (mediaCodec.getCodecInfo().isHardwareAccelerated()) {
                usage |= HardwareBuffer.USAGE_VIDEO_ENCODE;
            }
            if (!HardwareBuffer.isSupported(
                    kWidth, kHeight, HardwareBuffer.YCBCR_420_888, 1 /* layer */, usage)) {
                Log.i(TAG, "HardwareBuffer doesn't support " + kWidth + "x" + kHeight
                        + "; YCBCR_420_888; usage(" + Long.toHexString(usage) + ")");
                return MediaCodecBlockModelHelper.Result.SKIP;
            }

            List<Long> timestampList = Collections.synchronizedList(new ArrayList<>());

            final LinkedBlockingQueue<MediaCodecAsyncHelper.SlotEvent> queue =
                    new LinkedBlockingQueue<>();
            mediaCodec.setCallback(new MediaCodec.Callback() {
                @Override
                public void onInputBufferAvailable(MediaCodec codec, int index) {
                    queue.offer(new MediaCodecAsyncHelper.SlotEvent(true, index));
                }

                @Override
                public void onOutputBufferAvailable(
                        MediaCodec codec, int index, MediaCodec.BufferInfo info) {
                    queue.offer(new MediaCodecAsyncHelper.SlotEvent(false, index));
                }

                @Override
                public void onOutputFormatChanged(MediaCodec codec, MediaFormat format) {
                }

                @Override
                public void onError(MediaCodec codec, CodecException e) {
                }
            });

            int flags = MediaCodec.CONFIGURE_FLAG_USE_BLOCK_MODEL;
            flags |= MediaCodec.CONFIGURE_FLAG_ENCODE;

            mediaCodec.configure(mediaFormat, null, null, flags);
            mediaCodec.start();
            boolean eos = false;
            boolean signaledEos = false;
            int frameIndex = 0;
            while (!eos && !Thread.interrupted()) {
                MediaCodecAsyncHelper.SlotEvent event;
                try {
                    event = queue.take();
                } catch (InterruptedException e) {
                    return MediaCodecBlockModelHelper.Result.FAIL;
                }

                if (event.input) {
                    if (signaledEos) {
                        continue;
                    }
                    while (hardwareBuffers.size() <= event.index) {
                        hardwareBuffers.add(null);
                    }
                    HardwareBuffer buffer = hardwareBuffers.get(event.index);
                    if (buffer == null) {
                        buffer = HardwareBuffer.create(
                                kWidth, kHeight, HardwareBuffer.YCBCR_420_888, 1, usage);
                        hardwareBuffers.set(event.index, buffer);
                    }
                    try (Image image = MediaCodec.mapHardwareBuffer(buffer)) {
                        assertNotNull("CPU readable/writable image must be mappable", image);
                        assertEquals(kWidth, image.getWidth());
                        assertEquals(kHeight, image.getHeight());
                        // For Y plane
                        int rowSampling = 1;
                        for (Image.Plane plane : image.getPlanes()) {
                            ByteBuffer planeBuffer = plane.getBuffer();
                            for (int row = 0; row < kHeight / rowSampling; ++row) {
                                int rowOffset = row * plane.getRowStride();
                                for (int col = 0; col < kWidth / rowSampling; ++col) {
                                    planeBuffer.put(
                                            rowOffset + col * plane.getPixelStride(),
                                            (byte)(frameIndex * 4));
                                }
                            }
                            // For Cb and Cr planes
                            rowSampling = 2;
                        }
                    }

                    long timestampUs = 1000000l * frameIndex / kFrameRate;
                    ++frameIndex;
                    if (frameIndex >= 32) {
                        signaledEos = true;
                    }
                    timestampList.add(timestampUs);
                    mediaCodec.getQueueRequest(event.index)
                            .setHardwareBuffer(buffer)
                            .setPresentationTimeUs(timestampUs)
                            .setFlags(signaledEos ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0)
                            .queue();
                } else {
                    MediaCodec.OutputFrame frame = mediaCodec.getOutputFrame(event.index);
                    eos = (frame.getFlags() & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;

                    if (!eos) {
                        assertNotNull(frame.getLinearBlock());
                        frame.getLinearBlock().recycle();
                    }

                    timestampList.remove(frame.getPresentationTimeUs());

                    mediaCodec.releaseOutputBuffer(event.index, false);
                }
            }

            if (!timestampList.isEmpty()) {
                assertTrue("Timestamp should match between input / output: " + timestampList,
                        timestampList.isEmpty());
            }
            return eos ? MediaCodecBlockModelHelper.Result.SUCCESS
                    : MediaCodecBlockModelHelper.Result.FAIL;
        } catch (IOException e) {
            throw new RuntimeException("error reading input resource", e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (mediaCodec != null) {
                mediaCodec.stop();
                mediaCodec.release();
            }
            for (HardwareBuffer buffer : hardwareBuffers) {
                if (buffer != null) {
                    buffer.close();
                }
            }
        }
    }
}
