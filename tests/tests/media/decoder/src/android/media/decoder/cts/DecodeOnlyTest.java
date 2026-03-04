/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.ActivityManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.cts.MediaHeavyPresubmitTest;
import android.media.cts.MediaTestBase;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemProperties;
import android.platform.test.annotations.AppModeFull;
import android.view.Surface;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.MediaUtils;
import com.android.compatibility.common.util.Preconditions;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

@MediaHeavyPresubmitTest
@AppModeFull(reason = "There should be no instant apps specific behavior related to decoders")
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
@RunWith(AndroidJUnit4.class)
@ApiTest(apis = {"android.media.MediaCodec#BUFFER_FLAG_DECODE_ONLY"})
public class DecodeOnlyTest extends MediaTestBase {
    public static final boolean WAS_LAUNCHED_ON_U_OR_LATER =
            SystemProperties.getInt("ro.product.first_api_level",
                                    Build.VERSION_CODES.CUR_DEVELOPMENT)
                    >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE;

    private static final String MEDIA_DIR_STRING = WorkDir.getMediaDirString();
    private static final String HEVC_VIDEO =
            "video_1280x720_mkv_h265_500kbps_25fps_aac_stereo_128kbps_44100hz.mkv";
    private static final String AVC_VIDEO =
            "video_480x360_mp4_h264_1000kbps_25fps_aac_stereo_128kbps_44100hz.mp4";
    private static final String VP9_VIDEO =
            "bbb_s1_640x360_webm_vp9_0p21_1600kbps_30fps_vorbis_stereo_128kbps_48000hz.webm";
    private static final String MIME_VIDEO_PREFIX = "video/";
    private static final String MIME_AUDIO_PREFIX = "audio/";
    private static final long EOS_TIMESTAMP_TUNNEL_MODE = Long.MAX_VALUE;

    static {
        System.loadLibrary("ctsmediadecodertest_jni");
    }

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

    /**
     * When testing perfect seek, assert that the first frame rendered after seeking is the exact
     * frame we seeked to
     */
    @Test
    @ApiTest(apis = {"android.media.MediaCodec#BUFFER_FLAG_DECODE_ONLY"})
    public void testTunneledPerfectSeekInitialPeekOnAvc() throws Exception {
        // Tunnel mode requires vendor support of the DECODE_ONLY feature
        Assume.assumeTrue("First API level is not Android 14 or later.",
                WAS_LAUNCHED_ON_U_OR_LATER);
        testTunneledPerfectSeek(AVC_VIDEO, true);
    }

    @Test
    @ApiTest(apis = {"android.media.MediaCodec#BUFFER_FLAG_DECODE_ONLY"})
    public void testTunneledPerfectSeekInitialPeekOnVp9() throws Exception {
        // Tunnel mode requires vendor support of the DECODE_ONLY feature
        Assume.assumeTrue("First API level is not Android 14 or later.",
                WAS_LAUNCHED_ON_U_OR_LATER);
        testTunneledPerfectSeek(VP9_VIDEO, true);
    }

    @Test
    @ApiTest(apis = {"android.media.MediaCodec#BUFFER_FLAG_DECODE_ONLY"})
    public void testTunneledPerfectSeekInitialPeekOnHevc() throws Exception {
        // Tunnel mode requires vendor support of the DECODE_ONLY feature
        Assume.assumeTrue("First API level is not Android 14 or later.",
                WAS_LAUNCHED_ON_U_OR_LATER);
        testTunneledPerfectSeek(HEVC_VIDEO, true);
    }

    @Test
    @ApiTest(apis = {"android.media.MediaCodec#BUFFER_FLAG_DECODE_ONLY"})
    public void testTunneledPerfectSeekInitialPeekOffAvc() throws Exception {
        // Tunnel mode requires vendor support of the DECODE_ONLY feature
        Assume.assumeTrue("First API level is not Android 14 or later.",
                WAS_LAUNCHED_ON_U_OR_LATER);
        testTunneledPerfectSeek(AVC_VIDEO, false);
    }

    @Test
    @ApiTest(apis = {"android.media.MediaCodec#BUFFER_FLAG_DECODE_ONLY"})
    public void testTunneledPerfectSeekInitialPeekOffVp9() throws Exception {
        // Tunnel mode requires vendor support of the DECODE_ONLY feature
        Assume.assumeTrue("First API level is not Android 14 or later.",
                WAS_LAUNCHED_ON_U_OR_LATER);
        testTunneledPerfectSeek(VP9_VIDEO, false);
    }

    @Test
    @ApiTest(apis = {"android.media.MediaCodec#BUFFER_FLAG_DECODE_ONLY"})
    public void testTunneledPerfectSeekInitialPeekOffHevc() throws Exception {
        // Tunnel mode requires vendor support of the DECODE_ONLY feature
        Assume.assumeTrue("First API level is not Android 14 or later.",
                WAS_LAUNCHED_ON_U_OR_LATER);
        testTunneledPerfectSeek(HEVC_VIDEO, false);
    }

    /**
     * In trick play, we expect to receive/render the non DECODE_ONLY frames only
     */
    @Test
    @ApiTest(apis = {"android.media.MediaCodec#BUFFER_FLAG_DECODE_ONLY"})
    public void testTunneledTrickPlayHevc() throws Exception {
        // Tunnel mode requires vendor support of the DECODE_ONLY feature
        Assume.assumeTrue("First API level is not Android 14 or later.",
                WAS_LAUNCHED_ON_U_OR_LATER);
        testTunneledTrickPlay(HEVC_VIDEO);
    }

    @Test
    @ApiTest(apis = {"android.media.MediaCodec#BUFFER_FLAG_DECODE_ONLY"})
    public void testTunneledTrickPlayAvc() throws Exception {
        // Tunnel mode requires vendor support of the DECODE_ONLY feature
        Assume.assumeTrue("First API level is not Android 14 or later.",
                WAS_LAUNCHED_ON_U_OR_LATER);
        testTunneledTrickPlay(AVC_VIDEO);
    }

    @Test
    @ApiTest(apis = {"android.media.MediaCodec#BUFFER_FLAG_DECODE_ONLY"})
    public void testTunneledTrickPlayVp9() throws Exception {
        // Tunnel mode requires vendor support of the DECODE_ONLY feature
        Assume.assumeTrue("First API level is not Android 14 or later.",
                WAS_LAUNCHED_ON_U_OR_LATER);
        testTunneledTrickPlay(VP9_VIDEO);
    }

    private static native boolean nativeTestNonTunneledTrickPlay(String fileName, Surface surface,
                      boolean isAsync);

    @Test
    @ApiTest(apis = {"android.media.MediaCodec#BUFFER_FLAG_DECODE_ONLY"})
    public void nativeTestNonTunneledTrickPlayHevc() {
        boolean[] boolStates = {true, false};
        for (boolean isAsync : boolStates) {
            assertTrue(nativeTestNonTunneledTrickPlay(MEDIA_DIR_STRING + HEVC_VIDEO,
                    getActivity().getSurfaceHolder().getSurface(), isAsync));
        }
    }

    @Test
    @ApiTest(apis = {"android.media.MediaCodec#BUFFER_FLAG_DECODE_ONLY"})
    public void testNonTunneledTrickPlayHevc() throws Exception {
        Assume.assumeTrue("codec is not supported on this device",
                MediaUtils.hasCodecsForResource(HEVC_VIDEO));
        testNonTunneledTrickPlay(HEVC_VIDEO);
    }

    @Test
    @ApiTest(apis = {"android.media.MediaCodec#BUFFER_FLAG_DECODE_ONLY"})
    public void testNonTunneledTrickPlayAvc() throws Exception {
        testNonTunneledTrickPlay(AVC_VIDEO);
    }

    @Test
    @ApiTest(apis = {"android.media.MediaCodec#BUFFER_FLAG_DECODE_ONLY"})
    public void testNonTunneledTrickPlayVp9() throws Exception {
        testNonTunneledTrickPlay(VP9_VIDEO);
    }

    private void testNonTunneledTrickPlay(String fileName) throws Exception {
        Preconditions.assertTestFileExists(MEDIA_DIR_STRING + fileName);
        // create the video extractor
        MediaExtractor videoExtractor = createMediaExtractor(fileName);

        // choose the first track that has the prefix "video/" and select it
        int videoTrackIndex = getFirstTrackWithMimePrefix(MIME_VIDEO_PREFIX, videoExtractor);
        videoExtractor.selectTrack(videoTrackIndex);

        // create the video codec
        MediaFormat videoFormat = videoExtractor.getTrackFormat(videoTrackIndex);
        String mime = videoFormat.getString(MediaFormat.KEY_MIME);
        MediaCodec videoCodec = MediaCodec.createDecoderByType(mime);

        CountDownLatch hasReceivedEos = new CountDownLatch(1);
        List<Long> expectedPresentationTimes = new ArrayList<>();
        List<Long> receivedPresentationTimes = new ArrayList<>();

        // set a callback on the video codec to process the frames
        videoCodec.setCallback(
                new MediaCodec.Callback() {
                    private boolean mHasQueuedEos;
                    int mDecodeOnlyCounter = 0;

                    // Before queueing a frame, check if it is the last frame and set the EOS flag
                    // to the frame if that's the case. If the frame is to be only decoded
                    // (every other frame), then set the DECODE_ONLY flag to the frame. Only frames
                    // not tagged with EOS or DECODE_ONLY are expected to be rendered and added
                    // to expectedPresentationTimes.
                    @Override
                    public void onInputBufferAvailable(MediaCodec codec, int index) {
                        if (mHasQueuedEos) {
                            return;
                        }
                        ByteBuffer inputBuffer = videoCodec.getInputBuffer(index);
                        long sampleSizeToRead = videoExtractor.getSampleSize();
                        int sampleSize = videoExtractor.readSampleData(inputBuffer, 0);
                        assertEquals(
                                "Test could not read the entire sample data into the buffer. "
                                        + "Size of sample: "
                                        + sampleSizeToRead
                                        + ". Size that was read: "
                                        + sampleSize
                                        + ".",
                                sampleSizeToRead,
                                (long) sampleSize);

                        long presentationTime = videoExtractor.getSampleTime();
                        int extractorFlags = videoExtractor.getSampleFlags();
                        int codecFlags = 0;

                        if (sampleSize < 0) {
                            codecFlags = MediaCodec.BUFFER_FLAG_END_OF_STREAM;
                            sampleSize = 0;
                            mHasQueuedEos = true;
                        } else {
                            if (mDecodeOnlyCounter % 2 == 0) {
                                codecFlags |= MediaCodec.BUFFER_FLAG_DECODE_ONLY;
                            }
                            if ((extractorFlags & MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME) != 0) {
                                codecFlags |= MediaCodec.BUFFER_FLAG_PARTIAL_FRAME;
                            } else {
                                if (mDecodeOnlyCounter % 2 != 0) {
                                    expectedPresentationTimes.add(presentationTime);
                                }
                                mDecodeOnlyCounter++;
                            }
                        }

                        videoCodec.queueInputBuffer(
                                index, 0, sampleSize, presentationTime, codecFlags);
                        videoExtractor.advance();
                    }

                    // Keep track of all received frames. Signal when the end of stream has been
                    // reached.
                    // The list of received frames is expected to be the same as all frames without
                    // the DECODE_ONLY flag.
                    @Override
                    public void onOutputBufferAvailable(
                            MediaCodec codec, int index, MediaCodec.BufferInfo info) {
                        videoCodec.releaseOutputBuffer(index, false);
                        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            hasReceivedEos.countDown();
                        } else {
                            receivedPresentationTimes.add(info.presentationTimeUs);
                        }
                    }

                    @Override
                    public void onError(MediaCodec codec, MediaCodec.CodecException e) {}

                    @Override
                    public void onOutputFormatChanged(MediaCodec codec, MediaFormat format) {}
                });

        videoCodec.configure(videoFormat, getActivity().getSurfaceHolder().getSurface(), null, 0);
        videoCodec.start();

        hasReceivedEos.await();

        videoCodec.stop();
        videoCodec.release();

        Collections.sort(expectedPresentationTimes);
        assertEquals(expectedPresentationTimes, receivedPresentationTimes);
    }

    private void testTunneledTrickPlay(String fileName) throws Exception {
        Preconditions.assertTestFileExists(MEDIA_DIR_STRING + fileName);

        // generate the audio session id needed for tunnel mode playback
        AudioManager audioManager = mContext.getSystemService(AudioManager.class);
        int audioSessionId = audioManager.generateAudioSessionId();

        // create the video extractor
        MediaExtractor videoExtractor = createMediaExtractor(fileName);

        // choose the first track that has the prefix "video/" and select it
        int videoTrackIndex = getFirstTrackWithMimePrefix(MIME_VIDEO_PREFIX, videoExtractor);
        videoExtractor.selectTrack(videoTrackIndex);

        // create the video codec for tunneled play
        MediaFormat videoFormat = videoExtractor.getTrackFormat(videoTrackIndex);
        videoFormat.setFeatureEnabled(MediaCodecInfo.CodecCapabilities.FEATURE_TunneledPlayback,
                true);
        MediaCodecList mcl = new MediaCodecList(MediaCodecList.ALL_CODECS);
        String codecName = mcl.findDecoderForFormat(videoFormat);
        Assume.assumeTrue("Codec is not supported on this device",
                codecName != null);
        videoFormat.setInteger(MediaFormat.KEY_AUDIO_SESSION_ID, audioSessionId);
        MediaCodec videoCodec = MediaCodec.createByCodecName(codecName);

        // create the audio extractor
        MediaExtractor audioExtractor = createMediaExtractor(fileName);

        // choose the first track that has the prefix "audio/" and select it
        int audioTrackIndex = getFirstTrackWithMimePrefix(MIME_AUDIO_PREFIX, audioExtractor);
        audioExtractor.selectTrack(audioTrackIndex);

        // create the audio codec
        MediaFormat audioFormat = audioExtractor.getTrackFormat(audioTrackIndex);
        String mime = audioFormat.getString(MediaFormat.KEY_MIME);
        MediaCodec audioCodec = MediaCodec.createDecoderByType(mime);

        // audio track used by audio codec
        AudioTrack audioTrack = createAudioTrack(audioFormat, audioSessionId);

        List<Long> expectedPresentationTimes = new ArrayList<>();

        videoCodec.setCallback(
                new MediaCodec.Callback() {
                    boolean mHasQueuedEos = false;
                    int mDecodeOnlyCounter = 0;

                    // Before queueing a frame, check if it is the last frame and set the EOS flag
                    // to the frame if that's the case. If the frame is to be only decoded
                    // (every other frame), then set the DECODE_ONLY flag to the frame. Only frames
                    // not tagged with EOS or DECODE_ONLY are expected to be rendered and added
                    // to expectedPresentationTimes
                    @Override
                    public void onInputBufferAvailable(MediaCodec codec, int index) {
                        if (mHasQueuedEos) {
                            return;
                        }
                        ByteBuffer inputBuffer = videoCodec.getInputBuffer(index);
                        long sampleSizeToRead = videoExtractor.getSampleSize();
                        int sampleSize = videoExtractor.readSampleData(inputBuffer, 0);
                        assertEquals(
                                "Test could not read the entire sample data into the buffer. "
                                        + "Size of sample: "
                                        + sampleSizeToRead
                                        + ". Size that was read: "
                                        + sampleSize
                                        + ".",
                                sampleSizeToRead,
                                (long) sampleSize);

                        long presentationTime = videoExtractor.getSampleTime();
                        int extractorFlags = videoExtractor.getSampleFlags();
                        int codecFlags = 0;

                        if (sampleSize < 0) {
                            codecFlags |= MediaCodec.BUFFER_FLAG_END_OF_STREAM;
                            sampleSize = 0;
                            mHasQueuedEos = true;
                        } else {
                            if (mDecodeOnlyCounter % 2 == 0) {
                                codecFlags |= MediaCodec.BUFFER_FLAG_DECODE_ONLY;
                            }
                            if ((extractorFlags & MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME) != 0) {
                                codecFlags |= MediaCodec.BUFFER_FLAG_PARTIAL_FRAME;
                            } else {
                                if (mDecodeOnlyCounter % 2 != 0) {
                                    expectedPresentationTimes.add(presentationTime);
                                }
                                mDecodeOnlyCounter++;
                            }
                        }

                        videoCodec.queueInputBuffer(
                                index, 0, sampleSize, presentationTime, codecFlags);
                        videoExtractor.advance();
                    }

                    // nothing to do here - in tunneled mode, the frames are rendered directly by
                    // the hardware, they are not sent back to the codec for extra processing
                    @Override
                    public void onOutputBufferAvailable(
                            MediaCodec codec, int index, MediaCodec.BufferInfo info) {
                        Assert.fail("onOutputBufferAvailable should not be called in tunnel mode.");
                    }

                    @Override
                    public void onError(MediaCodec codec, MediaCodec.CodecException e) {
                        Assert.fail(
                                "Encountered unexpected error while decoding video: "
                                        + e.getMessage());
                    }

                    @Override
                    public void onOutputFormatChanged(MediaCodec codec, MediaFormat format) {}
                });
        videoCodec.configure(videoFormat, getActivity().getSurfaceHolder().getSurface(), null, 0);
        // Since data is written to AudioTrack in a blocking manner, run it in a separate thread to
        // not block other operations.
        HandlerThread audioThread = new HandlerThread("audioThread");
        audioThread.start();
        AudioCallback audioCallback = new AudioCallback(audioCodec, audioExtractor, audioTrack);
        audioCodec.setCallback(audioCallback, new Handler(audioThread.getLooper()));
        audioCodec.configure(audioFormat, null, null, 0);

        // Keep track of all rendered frames. If it is the last frame, then signal a boolean that
        // playback has finished. The list of rendered frames is compared against a list of frames
        // that were not marked as DECODE_ONLY.
        List<Long> renderedPresentationTimes = new ArrayList<>();
        CountDownLatch hasRenderedEos = new CountDownLatch(1);
        videoCodec.setOnFrameRenderedListener(
                (codec, presentationTimeUs, nanoTime) -> {
                    if (presentationTimeUs == EOS_TIMESTAMP_TUNNEL_MODE) {
                        hasRenderedEos.countDown();
                    } else {
                        renderedPresentationTimes.add(presentationTimeUs);
                    }
                },
                new Handler(Looper.getMainLooper()));

        videoCodec.start();
        audioCodec.start();
        audioTrack.play();

        hasRenderedEos.await();

        audioTrack.stop();
        audioTrack.release();
        videoCodec.stop();
        videoCodec.release();
        audioCodec.stop();
        audioCodec.release();

        Collections.sort(expectedPresentationTimes);
        Collections.sort(renderedPresentationTimes);
        assertEquals(expectedPresentationTimes, renderedPresentationTimes);
    }

    private void sleepUntil(Supplier<Boolean> supplier, Duration maxWait) throws Exception {
        final long deadLineMs = System.currentTimeMillis() + maxWait.toMillis();
        do {
            Thread.sleep(50);
        } while (!supplier.get() && System.currentTimeMillis() < deadLineMs);
    }

    private void testTunneledPerfectSeek(String fileName,
            final boolean initialPeek) throws Exception {
        Preconditions.assertTestFileExists(MEDIA_DIR_STRING + fileName);

        // generate the audio session id needed for tunnel mode playback
        AudioManager audioManager = mContext.getSystemService(AudioManager.class);
        int audioSessionId = audioManager.generateAudioSessionId();

        // create the video extractor
        MediaExtractor videoExtractor = createMediaExtractor(fileName);

        // choose the first track that has the prefix "video/" and select it
        int videoTrackIndex = getFirstTrackWithMimePrefix(MIME_VIDEO_PREFIX, videoExtractor);
        videoExtractor.selectTrack(videoTrackIndex);

        // create the video codec for tunneled play
        MediaFormat videoFormat = videoExtractor.getTrackFormat(videoTrackIndex);
        videoFormat.setFeatureEnabled(MediaCodecInfo.CodecCapabilities.FEATURE_TunneledPlayback,
                true);
        MediaCodecList mcl = new MediaCodecList(MediaCodecList.ALL_CODECS);
        String codecName = mcl.findDecoderForFormat(videoFormat);
        Assume.assumeTrue("Codec is not supported on this device",
                codecName != null);
        videoFormat.setInteger(MediaFormat.KEY_AUDIO_SESSION_ID, audioSessionId);
        MediaCodec videoCodec = MediaCodec.createByCodecName(codecName);

        // create the audio extractor
        MediaExtractor audioExtractor = createMediaExtractor(fileName);

        // choose the first track that has the prefix "audio/" and select it
        int audioTrackIndex = getFirstTrackWithMimePrefix(MIME_AUDIO_PREFIX, audioExtractor);
        audioExtractor.selectTrack(audioTrackIndex);

        // create the audio codec
        MediaFormat audioFormat = audioExtractor.getTrackFormat(audioTrackIndex);
        String mime = audioFormat.getString(MediaFormat.KEY_MIME);
        MediaCodec audioCodec = MediaCodec.createDecoderByType(mime);

        // audio track used by audio codec
        AudioTrack audioTrack = createAudioTrack(audioFormat, audioSessionId);

        // Frames at 2s of each file are not key frame
        AtomicLong seekTime = new AtomicLong(2000 * 1000);
        videoExtractor.seekTo(seekTime.get(), MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
        long audioSeekTime = videoExtractor.getSampleTime();
        audioExtractor.seekTo(audioSeekTime, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);

        List<Long> expectedPresentationTimes = new ArrayList<>();
        AtomicBoolean hasDecodeOnlyFrames = new AtomicBoolean(false);

        class VideoCallback extends MediaCodec.Callback {
            private final Queue<Integer> mAvailableInputIndices = new ArrayDeque<>();
            private final Lock mLock = new ReentrantLock();
            boolean mHasQueuedEos = false;
            boolean mShouldProcessBuffers = true;

            private void queueInput(MediaCodec codec, int index) {
                if (mHasQueuedEos) {
                    return;
                }
                ByteBuffer inputBuffer = codec.getInputBuffer(index);
                int sampleSize = videoExtractor.readSampleData(inputBuffer, 0);
                long presentationTime = videoExtractor.getSampleTime();
                int flags = 0;
                if (sampleSize < 0) {
                    flags = MediaCodec.BUFFER_FLAG_END_OF_STREAM;
                    sampleSize = 0;
                    mHasQueuedEos = true;
                } else if (presentationTime < seekTime.get()) {
                    flags = MediaCodec.BUFFER_FLAG_DECODE_ONLY;
                    hasDecodeOnlyFrames.set(true);
                } else {
                    expectedPresentationTimes.add(presentationTime);
                }
                codec.queueInputBuffer(index, 0, sampleSize, presentationTime, flags);
                videoExtractor.advance();
            }

            @Override
            public void onInputBufferAvailable(MediaCodec codec, int index) {
                mLock.lock();
                try {
                    if (mShouldProcessBuffers) {
                        queueInput(codec, index);
                    } else {
                        mAvailableInputIndices.offer(index);
                    }
                } finally {
                    mLock.unlock();
                }
            }

            void setShouldProcessBuffers(boolean shouldProcessBuffers) {
                mLock.lock();
                try {
                    mShouldProcessBuffers = shouldProcessBuffers;
                    if (shouldProcessBuffers) {
                        while (!mAvailableInputIndices.isEmpty()) {
                            queueInput(videoCodec, mAvailableInputIndices.poll());
                        }
                    }
                } finally {
                    mLock.unlock();
                }
            }

            void clearBufferQueue() {
                mLock.lock();
                try {
                    mAvailableInputIndices.clear();
                    mHasQueuedEos = false;
                } finally {
                    mLock.unlock();
                }
            }

            @Override
            public void onOutputBufferAvailable(MediaCodec codec, int index,
                    MediaCodec.BufferInfo info) {
                Assert.fail("onOutputBufferAvailable should not be called in tunnel mode.");
            }

            @Override
            public void onError(MediaCodec codec, MediaCodec.CodecException e) {
                Assert.fail("Encountered unexpected error while decoding video: " + e.getMessage());
            }

            @Override
            public void onOutputFormatChanged(MediaCodec codec, MediaFormat format) {

            }
        }

        VideoCallback videoCallback = new VideoCallback();
        videoCodec.setCallback(videoCallback);
        videoCodec.configure(videoFormat, getActivity().getSurfaceHolder().getSurface(), null, 0);

        // Since data is written to AudioTrack in a blocking manner, run it in a separate thread to
        // not block other operations.
        HandlerThread audioThread = new HandlerThread("audioThread");
        audioThread.start();
        AudioCallback audioCallback = new AudioCallback(audioCodec, audioExtractor, audioTrack);
        audioCodec.setCallback(audioCallback, new Handler(audioThread.getLooper()));
        audioCodec.configure(audioFormat, null, null, 0);

        // Keep track of all rendered frames. Signal once a frame 500ms after the seek position is
        // rendered. The list of rendered frames is examined to verify the correct frame is peeked.
        List<Long> renderedPresentationTimes = new ArrayList<>();
        CountDownLatch hasRenderedAfterSeekTime = new CountDownLatch(1);
        videoCodec.setOnFrameRenderedListener(
                (codec, presentationTimeUs, nanoTime) -> {
                    renderedPresentationTimes.add(presentationTimeUs);
                    if (presentationTimeUs >= seekTime.get() + 500 * 1000) {
                        hasRenderedAfterSeekTime.countDown();
                    }
                },
                new Handler(Looper.getMainLooper()));

        AtomicBoolean firstTunnelFrameReady = new AtomicBoolean(false);
        videoCodec.setOnFirstTunnelFrameReadyListener(new Handler(Looper.getMainLooper()),
                (codec) -> {
                    firstTunnelFrameReady.set(true);
                });

        // Peek needs to be called after start, but before processing buffers
        videoCallback.setShouldProcessBuffers(false);
        videoCodec.start();
        audioCodec.start();
        boolean isPeeking = setKeyTunnelPeek(videoCodec, initialPeek ? 1 : 0);
        videoCallback.setShouldProcessBuffers(true);

        // When video codecs are started, large chunks of contiguous physical memory need to be
        // allocated, which, on low-RAM devices, can trigger high CPU usage for moving memory
        // around to create contiguous space for the video decoder. This can cause an increase in
        // startup time for playback.
        ActivityManager activityManager = mContext.getSystemService(ActivityManager.class);
        final int firstFrameReadyTimeoutSeconds = activityManager.isLowRamDevice() ? 3 : 1;

        // Verify the first tunnel frame ready signal is received in a reasonable amount of time.
        sleepUntil(firstTunnelFrameReady::get, Duration.ofSeconds(firstFrameReadyTimeoutSeconds));
        assertTrue(String.format("onFirstTunnelFrameReady not called within %d seconds",
                firstFrameReadyTimeoutSeconds), firstTunnelFrameReady.get());

        // Sleep for 1s here to ensure that either (1) when peek is on, high-latency display
        // pipelines have enough time to render the first frame, or (2) when peek is off, the
        // frame isn't rendered after long time.
        final int waitForRenderingMs = 1000;
        Thread.sleep(waitForRenderingMs);
        if (isPeeking) {
            assertEquals(1, renderedPresentationTimes.size());
            assertEquals(seekTime.get(), (long) renderedPresentationTimes.get(0));
        } else {
            assertTrue(renderedPresentationTimes.isEmpty());
        }

        assertTrue("No DECODE_ONLY frames have been produced, "
                        + "try changing the offset for the seek. To do this, find a timestamp "
                        + "that falls between two sync frames to ensure that there will "
                        + "be a few DECODE_ONLY frames. For example \"ffprobe -show_frames $video\""
                        + " can be used to list all the frames of a certain video and will show"
                        + " info about key frames and their timestamps.",
                hasDecodeOnlyFrames.get());

        // Run the playback to verify that the seek frame is rendered when peek is off.
        audioTrack.play();
        hasRenderedAfterSeekTime.await();

        // Verify the first rendered frame is the seek frame, and not the preceding key frame.
        if (!isPeeking) {
            assertFalse(renderedPresentationTimes.isEmpty());
            assertEquals(seekTime.get(), (long) renderedPresentationTimes.get(0));
        }

        // Pause and setup seeking to a new position.
        audioTrack.pause();
        // TODO(b/291959069): Remove once MediaCodec stops sending stale callbacks when flushed.
        videoCallback.setShouldProcessBuffers(false);
        // TODO(b/291959069): Remove once MediaCodec stops sending stale callbacks when flushed.
        audioCallback.setShouldProcessBuffers(false);
        // Just be safe that pause may take some time.
        Thread.sleep(500);
        audioTrack.flush();
        videoCodec.flush();
        audioCodec.flush();

        // Clear all buffers from callbacks that occurred prior to the flush
        videoCallback.clearBufferQueue();

        // Frames at 7s of each file are not key frame, and there is non-zero key frame before it.
        seekTime.set(7000 * 1000);
        videoExtractor.seekTo(seekTime.get(), MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
        audioSeekTime = videoExtractor.getSampleTime();
        audioExtractor.seekTo(audioSeekTime, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
        expectedPresentationTimes.clear();
        renderedPresentationTimes.clear();

        // Keep track of all rendered frames. Signal once the last frame has been rendered. The list
        // of rendered frames is examined to verify the correct frame is peeked.
        CountDownLatch hasRenderedEos = new CountDownLatch(1);
        videoCodec.setOnFrameRenderedListener(
                (codec, presentationTimeUs, nanoTime) -> {
                    if (presentationTimeUs == EOS_TIMESTAMP_TUNNEL_MODE) {
                        hasRenderedEos.countDown();
                    } else {
                        renderedPresentationTimes.add(presentationTimeUs);
                    }
                },
                new Handler(Looper.getMainLooper()));

        // Restart media playback at the new seek position.
        firstTunnelFrameReady.set(false);
        // TODO(b/291959069): Remove once MediaCodec stops sending stale callbacks when flushed.
        audioCallback.setShouldProcessBuffers(true);
        videoCodec.start();
        audioCodec.start();
        // Set peek on when it was off, and set it off when it was on.
        isPeeking = setKeyTunnelPeek(videoCodec, isPeeking ? 0 : 1);
        // TODO(b/291959069): Remove once MediaCodec stops sending stale callbacks when flushed.
        videoCallback.setShouldProcessBuffers(true);

        // Verify the first tunnel frame ready signal is received in a reasonable amount of time.
        sleepUntil(firstTunnelFrameReady::get, Duration.ofSeconds(firstFrameReadyTimeoutSeconds));
        assertTrue(String.format("onFirstTunnelFrameReady not called within %d seconds",
                firstFrameReadyTimeoutSeconds), firstTunnelFrameReady.get());

        // Sleep for 1s here to ensure that either (1) when peek is on, high-latency display
        // pipelines have enough time to render the first frame, or (2) when peek is off, the
        // frame isn't rendered after long time.
        Thread.sleep(waitForRenderingMs);
        if (isPeeking) {
            assertEquals(1, renderedPresentationTimes.size());
            assertEquals(seekTime.get(), (long) renderedPresentationTimes.get(0));
        } else {
            assertTrue(renderedPresentationTimes.isEmpty());

            // First frame should be rendered immediately after setting peek on.
            setKeyTunnelPeek(videoCodec, 1);
            // Sleep to allow for high-latency display pipelines on TV devices.
            Thread.sleep(waitForRenderingMs);
            assertEquals(1, renderedPresentationTimes.size());
            assertEquals(seekTime.get(), (long) renderedPresentationTimes.get(0));
        }

        audioTrack.play();

        hasRenderedEos.await();

        // TODO(b/291959069): Remove once MediaCodec stops sending stale callbacks when stopped.
        videoCallback.setShouldProcessBuffers(false);
        // TODO(b/291959069): Remove once MediaCodec stops sending stale callbacks when stopped.
        audioCallback.setShouldProcessBuffers(false);

        audioTrack.stop();
        audioTrack.release();
        videoCodec.stop();
        videoCodec.release();
        audioCodec.stop();
        audioCodec.release();
        Collections.sort(expectedPresentationTimes);
        Collections.sort(renderedPresentationTimes);
        assertEquals(expectedPresentationTimes, renderedPresentationTimes);
        assertEquals(seekTime.get(), (long) renderedPresentationTimes.get(0));
    }

    // 1 is on, 0 is off.
    private boolean setKeyTunnelPeek(MediaCodec videoCodec, int value) {
        Bundle parameters = new Bundle();
        parameters.putInt(MediaCodec.PARAMETER_KEY_TUNNEL_PEEK, value);
        videoCodec.setParameters(parameters);
        return value != 0;
    }

    private AudioTrack createAudioTrack(MediaFormat audioFormat, int audioSessionId) {
        int sampleRate = audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        int channelCount = audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        int channelConfig;

        switch (channelCount) {
            case 1:
                channelConfig = AudioFormat.CHANNEL_OUT_MONO;
                break;
            case 2:
                channelConfig = AudioFormat.CHANNEL_OUT_STEREO;
                break;
            case 6:
                channelConfig = AudioFormat.CHANNEL_OUT_5POINT1;
                break;
            default:
                throw new IllegalArgumentException();
        }

        int minBufferSize =
                AudioTrack.getMinBufferSize(
                        sampleRate,
                        channelConfig,
                        AudioFormat.ENCODING_PCM_16BIT);
        AudioAttributes audioAttributes = (new AudioAttributes.Builder())
                .setLegacyStreamType(AudioManager.STREAM_MUSIC)
                .setFlags(AudioAttributes.FLAG_HW_AV_SYNC)
                .build();
        AudioFormat af = (new AudioFormat.Builder())
                .setChannelMask(channelConfig)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .build();
        return new AudioTrack(audioAttributes, af, 2 * minBufferSize,
                AudioTrack.MODE_STREAM, audioSessionId);
    }

    private int getFirstTrackWithMimePrefix(String prefix, MediaExtractor videoExtractor) {
        int trackIndex = -1;
        for (int i = 0; i < videoExtractor.getTrackCount(); ++i) {
            MediaFormat format = videoExtractor.getTrackFormat(i);
            if (format.getString(MediaFormat.KEY_MIME).startsWith(prefix)) {
                trackIndex = i;
                break;
            }
        }
        assertTrue("Video track was not found.", trackIndex >= 0);
        return trackIndex;
    }

    private MediaExtractor createMediaExtractor(String fileName) throws IOException {
        MediaExtractor mediaExtractor = new MediaExtractor();
        mediaExtractor.setDataSource(MEDIA_DIR_STRING + fileName);
        return mediaExtractor;
    }

    private static class AudioCallback extends MediaCodec.Callback {
        private final MediaCodec mAudioCodec;
        private final MediaExtractor mAudioExtractor;
        private final AudioTrack mAudioTrack;
        // TODO(b/291959069): Remove after MediaCodec stops sending stale callbacks
        private final AtomicBoolean mShouldProcessBuffers = new AtomicBoolean(true);

        AudioCallback(MediaCodec audioCodec, MediaExtractor audioExtractor, AudioTrack audioTrack) {
            this.mAudioCodec = audioCodec;
            this.mAudioExtractor = audioExtractor;
            this.mAudioTrack = audioTrack;
        }

        // TODO(b/291959069): Remove after MediaCodec stops sending stale callbacks
        void setShouldProcessBuffers(boolean shouldProcessBuffers) {
            mShouldProcessBuffers.set(shouldProcessBuffers);
        }

        @Override
        public void onInputBufferAvailable(MediaCodec codec, int index) {
            // TODO(b/291959069): Remove after MediaCodec stops sending stale callbacks
            if (!mShouldProcessBuffers.get()) {
                return;
            }
            ByteBuffer audioInputBuffer = mAudioCodec.getInputBuffer(index);
            int audioSampleSize = mAudioExtractor.readSampleData(audioInputBuffer, 0);
            long presentationTime = mAudioExtractor.getSampleTime();
            int flags = 0;
            if (audioSampleSize < 0) {
                flags = MediaCodec.BUFFER_FLAG_END_OF_STREAM;
                audioSampleSize = 0;
                presentationTime = 0;
            }
            mAudioCodec.queueInputBuffer(index, 0, audioSampleSize, presentationTime, flags);
            mAudioExtractor.advance();
        }

        @Override
        public void onOutputBufferAvailable(MediaCodec codec, int index,
                MediaCodec.BufferInfo info) {
            // TODO(b/291959069): Remove after MediaCodec stops sending stale callbacks
            if (!mShouldProcessBuffers.get()) {
                return;
            }
            ByteBuffer outputBuffer = mAudioCodec.getOutputBuffer(index);
            byte[] copyOfAudioData = new byte[info.size];
            outputBuffer.get(copyOfAudioData);
            outputBuffer.clear();
            mAudioCodec.releaseOutputBuffer(index, false);
            ByteBuffer copyOfOutputBuffer = ByteBuffer.wrap(copyOfAudioData);
            while (copyOfOutputBuffer.remaining() > 0 && mShouldProcessBuffers.get()) {
                int written =
                        mAudioTrack.write(
                                copyOfOutputBuffer,
                                copyOfOutputBuffer.remaining(),
                                AudioTrack.WRITE_BLOCKING,
                                info.presentationTimeUs * 1000);
                if (written == 0) {
                    // When audio track is not in playing state, the write operation does not
                    // block in WRITE_BLOCKING mode. Sleep to avoid busy looping.
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException ignored) {
                    }
                } else if (written < 0) {
                    Assert.fail("AudioTrack write failure.");
                }
            }
        }

        @Override
        public void onError(MediaCodec codec, MediaCodec.CodecException e) {

        }

        @Override
        public void onOutputFormatChanged(MediaCodec codec, MediaFormat format) {
        }
    }
}
