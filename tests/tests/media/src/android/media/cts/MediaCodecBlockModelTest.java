/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.media.cts;

import android.content.res.AssetFileDescriptor;
import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaCodec.CodecException;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.cts.R;
import android.platform.test.annotations.Presubmit;
import android.platform.test.annotations.RequiresDevice;
import android.test.AndroidTestCase;
import android.util.Log;
import android.view.Surface;

import androidx.test.filters.SmallTest;

import com.android.compatibility.common.util.MediaUtils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/**
 * MediaCodec tests with CONFIGURE_FLAG_USE_BLOCK_MODEL.
 */
@Presubmit
@SmallTest
@RequiresDevice
public class MediaCodecBlockModelTest extends AndroidTestCase {
    private static final String TAG = "MediaCodecBlockModelTest";
    private static final boolean VERBOSE = false;           // lots of logging

                                                            // H.264 Advanced Video Coding
    private static final String MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC;

    private static final int APP_BUFFER_SIZE = 1024 * 1024;  // 1 MB

    // Input buffers from this input video are queued up to and including the video frame with
    // timestamp LAST_BUFFER_TIMESTAMP_US.
    private static final int INPUT_RESOURCE_ID =
            R.raw.video_480x360_mp4_h264_1350kbps_30fps_aac_stereo_192kbps_44100hz;
    private static final long LAST_BUFFER_TIMESTAMP_US = 166666;

    // The test should fail if the decoder never produces output frames for the truncated input.
    // Time out decoding, as we have no way to query whether the decoder will produce output.
    private static final int DECODING_TIMEOUT_MS = 2000;

    /**
     * Tests whether decoding a short group-of-pictures succeeds. The test queues a few video frames
     * then signals end-of-stream. The test fails if the decoder doesn't output the queued frames.
     */
    public void testDecodeShortVideo() throws InterruptedException {
        runThread(() -> runDecodeShortVideo(
                INPUT_RESOURCE_ID,
                LAST_BUFFER_TIMESTAMP_US,
                true /* obtainBlockForEachBuffer */));
        runThread(() -> runDecodeShortVideo(
                INPUT_RESOURCE_ID,
                LAST_BUFFER_TIMESTAMP_US,
                false /* obtainBlockForEachBuffer */));
    }

    /**
     * Tests whether decoding a short audio succeeds. The test queues a few audio frames
     * then signals end-of-stream. The test fails if the decoder doesn't output the queued frames.
     */
    public void testDecodeShortAudio() throws InterruptedException {
        runThread(() -> runDecodeShortAudio(
                INPUT_RESOURCE_ID,
                LAST_BUFFER_TIMESTAMP_US,
                true /* obtainBlockForEachBuffer */));
        runThread(() -> runDecodeShortAudio(
                INPUT_RESOURCE_ID,
                LAST_BUFFER_TIMESTAMP_US,
                false /* obtainBlockForEachBuffer */));
    }

    /**
     * Tests whether encoding a short audio succeeds. The test queues a few audio frames
     * then signals end-of-stream. The test fails if the encoder doesn't output the queued frames.
     */
    public void testEncodeShortAudio() throws InterruptedException {
        runThread(() -> runEncodeShortAudio());
    }

    private void runThread(BooleanSupplier supplier) throws InterruptedException {
        final AtomicBoolean completed = new AtomicBoolean();
        completed.set(false);
        Thread videoDecodingThread = new Thread(new Runnable() {
            public void run() {
                completed.set(supplier.getAsBoolean());
            }
        });
        videoDecodingThread.start();
        videoDecodingThread.join(DECODING_TIMEOUT_MS);
        if (!completed.get()) {
            throw new RuntimeException("timed out decoding to end-of-stream");
        }
    }

    private static class InputBlock {
        MediaCodec.LinearBlock block;
        ByteBuffer buffer;
        int offset;
    }

    private static interface InputSlotListener {
        public void onInputSlot(MediaCodec codec, int index, InputBlock input) throws Exception;
    }

    private static class ExtractorInputSlotListener implements InputSlotListener {
        public ExtractorInputSlotListener(
                MediaExtractor extractor,
                long lastBufferTimestampUs,
                boolean obtainBlockForEachBuffer) {
            mExtractor = extractor;
            mLastBufferTimestampUs = lastBufferTimestampUs;
            mObtainBlockForEachBuffer = obtainBlockForEachBuffer;
        }

        @Override
        public void onInputSlot(MediaCodec codec, int index, InputBlock input) throws Exception {
            // Try to feed more data into the codec.
            if (mExtractor.getSampleTrackIndex() == -1 || mSignaledEos) {
                return;
            }
            long size = mExtractor.getSampleSize();
            String[] codecNames = new String[]{ codec.getName() };
            if (mObtainBlockForEachBuffer) {
                input.block.recycle();
                input.block = MediaCodec.LinearBlock.obtain(Math.toIntExact(size), codecNames);
                input.buffer = input.block.map();
                input.offset = 0;
            }
            if (input.buffer.capacity() < size) {
                input.block.recycle();
                input.block = MediaCodec.LinearBlock.obtain(
                        Math.toIntExact(size * 2), codecNames);
                input.buffer = input.block.map();
                input.offset = 0;
            } else if (input.buffer.capacity() - input.offset < size) {
                long capacity = input.buffer.capacity();
                input.block.recycle();
                input.block = MediaCodec.LinearBlock.obtain(
                        Math.toIntExact(capacity), codecNames);
                input.buffer = input.block.map();
                input.offset = 0;
            }
            long timestampUs = mExtractor.getSampleTime();
            int written = mExtractor.readSampleData(input.buffer, input.offset);
            mExtractor.advance();
            mSignaledEos = mExtractor.getSampleTrackIndex() == -1
                    || timestampUs >= mLastBufferTimestampUs;
            codec.getQueueRequest(index)
                    .setLinearBlock(
                            input.block,
                            input.offset,
                            written,
                            null /* cryptoInfo */)
                    .setPresentationTimeUs(timestampUs)
                    .setFlags(
                            mSignaledEos ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0)
                    .queue();
            input.offset += written;
        }

        private final MediaExtractor mExtractor;
        private final long mLastBufferTimestampUs;
        private final boolean mObtainBlockForEachBuffer;
        private boolean mSignaledEos = false;
    }

    private static interface OutputSlotListener {
        // Returns true if EOS is met
        public boolean onOutputSlot(MediaCodec codec, int index) throws Exception;
    }

    private static class DummyOutputSlotListener implements OutputSlotListener {
        public DummyOutputSlotListener(boolean graphic) {
            mGraphic = graphic;
        }

        @Override
        public boolean onOutputSlot(MediaCodec codec, int index) throws Exception {
            MediaCodec.OutputFrame frame = codec.getOutputFrame(index);
            boolean eos = (frame.getFlags() & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;

            if (mGraphic && frame.getGraphicBlock() != null) {
                frame.getGraphicBlock().recycle();
            }
            if (!mGraphic && frame.getLinearBlock() != null) {
                frame.getLinearBlock().recycle();
            }
            codec.releaseOutputBuffer(index, false);

            return eos;
        }

        private final boolean mGraphic;
    }

    private static class SurfaceOutputSlotListener implements OutputSlotListener {
        public SurfaceOutputSlotListener(OutputSurface surface) {
            mOutputSurface = surface;
        }

        @Override
        public boolean onOutputSlot(MediaCodec codec, int index) throws Exception {
            MediaCodec.OutputFrame frame = codec.getOutputFrame(index);
            boolean eos = (frame.getFlags() & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;

            boolean render = false;
            if (frame.getGraphicBlock() != null) {
                frame.getGraphicBlock().recycle();
                render = true;
            }
            codec.releaseOutputBuffer(index, render);
            if (render) {
                mOutputSurface.awaitNewImage();
            }

            return eos;
        }

        private final OutputSurface mOutputSurface;
    }

    private static class SlotEvent {
        SlotEvent(boolean input, int index) {
            this.input = input;
            this.index = index;
        }
        final boolean input;
        final int index;
    }

    private boolean runDecodeShortVideo(
            int inputResourceId,
            long lastBufferTimestampUs,
            boolean obtainBlockForEachBuffer) {
        OutputSurface outputSurface = null;
        MediaExtractor mediaExtractor = null;
        MediaCodec mediaCodec = null;
        try {
            outputSurface = new OutputSurface(1, 1);
            mediaExtractor = getMediaExtractorForMimeType(inputResourceId, "video/");
            MediaFormat mediaFormat =
                    mediaExtractor.getTrackFormat(mediaExtractor.getSampleTrackIndex());
            // TODO: b/147748978
            String[] codecs = MediaUtils.getDecoderNames(true /* isGoog */, mediaFormat);
            if (codecs.length == 0) {
                Log.i(TAG, "No decoder found for format= " + mediaFormat);
                return true;
            }
            mediaCodec = MediaCodec.createByCodecName(codecs[0]);

            return runComponentWithLinearInput(
                    mediaCodec,
                    mediaFormat,
                    outputSurface.getSurface(),
                    false,  // encoder
                    new ExtractorInputSlotListener(
                            mediaExtractor, lastBufferTimestampUs, obtainBlockForEachBuffer),
                    new SurfaceOutputSlotListener(outputSurface));
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
            if (outputSurface != null) {
                outputSurface.release();
            }
        }
    }

    private boolean runDecodeShortAudio(
            int inputResourceId,
            long lastBufferTimestampUs,
            boolean obtainBlockForEachBuffer) {
        MediaExtractor mediaExtractor = null;
        MediaCodec mediaCodec = null;
        try {
            mediaExtractor = getMediaExtractorForMimeType(inputResourceId, "audio/");
            MediaFormat mediaFormat =
                    mediaExtractor.getTrackFormat(mediaExtractor.getSampleTrackIndex());
            // TODO: b/147748978
            String[] codecs = MediaUtils.getDecoderNames(true /* isGoog */, mediaFormat);
            if (codecs.length == 0) {
                Log.i(TAG, "No decoder found for format= " + mediaFormat);
                return true;
            }
            mediaCodec = MediaCodec.createByCodecName(codecs[0]);

            return runComponentWithLinearInput(
                    mediaCodec,
                    mediaFormat,
                    null,  // surface
                    false,  // encoder
                    new ExtractorInputSlotListener(
                            mediaExtractor, lastBufferTimestampUs, obtainBlockForEachBuffer),
                    new DummyOutputSlotListener(false /* graphic */));
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

    private boolean runEncodeShortAudio() {
        MediaExtractor mediaExtractor = null;
        MediaCodec mediaCodec = null;
        try {
            mediaExtractor = getMediaExtractorForMimeType(
                    R.raw.okgoogle123_good, MediaFormat.MIMETYPE_AUDIO_RAW);
            MediaFormat mediaFormat = new MediaFormat(
                    mediaExtractor.getTrackFormat(mediaExtractor.getSampleTrackIndex()));
            mediaFormat.setString(MediaFormat.KEY_MIME, MediaFormat.MIMETYPE_AUDIO_AAC);
            mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 128000);
            // TODO: b/147748978
            String[] codecs = MediaUtils.getEncoderNames(true /* isGoog */, mediaFormat);
            if (codecs.length == 0) {
                Log.i(TAG, "No encoder found for format= " + mediaFormat);
                return true;
            }
            mediaCodec = MediaCodec.createByCodecName(codecs[0]);

            return runComponentWithLinearInput(
                    mediaCodec,
                    mediaFormat,
                    null,  // surface
                    true,  // encoder
                    new ExtractorInputSlotListener(
                            mediaExtractor, LAST_BUFFER_TIMESTAMP_US, false),
                    new DummyOutputSlotListener(false /* graphic */));
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

    private boolean runComponentWithLinearInput(
            MediaCodec mediaCodec,
            MediaFormat mediaFormat,
            Surface surface,
            boolean encoder,
            InputSlotListener inputListener,
            OutputSlotListener outputListener) throws Exception {
        final LinkedBlockingQueue<SlotEvent> queue = new LinkedBlockingQueue<>();
        mediaCodec.setCallback(new MediaCodec.Callback() {
            @Override
            public void onInputBufferAvailable(MediaCodec codec, int index) {
                queue.offer(new SlotEvent(true, index));
            }

            @Override
            public void onOutputBufferAvailable(
                    MediaCodec codec, int index, MediaCodec.BufferInfo info) {
                queue.offer(new SlotEvent(false, index));
            }

            @Override
            public void onOutputFormatChanged(MediaCodec codec, MediaFormat format) {
            }

            @Override
            public void onError(MediaCodec codec, CodecException e) {
            }
        });
        String[] codecNames = new String[]{ mediaCodec.getName() };
        InputBlock input = new InputBlock();
        input.block = MediaCodec.LinearBlock.obtain(
                APP_BUFFER_SIZE, codecNames);
        input.buffer = input.block.map();
        input.offset = 0;

        int flags = MediaCodec.CONFIGURE_FLAG_USE_BLOCK_MODEL;
        if (encoder) {
            flags |= MediaCodec.CONFIGURE_FLAG_ENCODE;
        }
        mediaCodec.configure(mediaFormat, surface, null, flags);
        mediaCodec.start();
        boolean eos = false;
        boolean signaledEos = false;
        while (!eos && !Thread.interrupted()) {
            SlotEvent event;
            try {
                event = queue.take();
            } catch (InterruptedException e) {
                return false;
            }

            if (event.input) {
                inputListener.onInputSlot(mediaCodec, event.index, input);
            } else {
                eos = outputListener.onOutputSlot(mediaCodec, event.index);
            }
        }

        input.block.recycle();
        return eos;
    }

    private MediaExtractor getMediaExtractorForMimeType(int resourceId, String mimeTypePrefix)
            throws IOException {
        MediaExtractor mediaExtractor = new MediaExtractor();
        AssetFileDescriptor afd = mContext.getResources().openRawResourceFd(resourceId);
        try {
            mediaExtractor.setDataSource(
                    afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
        } finally {
            afd.close();
        }
        int trackIndex;
        for (trackIndex = 0; trackIndex < mediaExtractor.getTrackCount(); trackIndex++) {
            MediaFormat trackMediaFormat = mediaExtractor.getTrackFormat(trackIndex);
            if (trackMediaFormat.getString(MediaFormat.KEY_MIME).startsWith(mimeTypePrefix)) {
                mediaExtractor.selectTrack(trackIndex);
                break;
            }
        }
        if (trackIndex == mediaExtractor.getTrackCount()) {
            throw new IllegalStateException("couldn't get a video track");
        }

        return mediaExtractor;
    }

    private static boolean supportsCodec(String mimeType, boolean encoder) {
        MediaCodecList list = new MediaCodecList(MediaCodecList.ALL_CODECS);
        for (MediaCodecInfo info : list.getCodecInfos()) {
            if (encoder != info.isEncoder()) {
                continue;
            }

            for (String type : info.getSupportedTypes()) {
                if (type.equalsIgnoreCase(mimeType)) {
                    return true;
                }
            }
        }
        return false;
    }
}
