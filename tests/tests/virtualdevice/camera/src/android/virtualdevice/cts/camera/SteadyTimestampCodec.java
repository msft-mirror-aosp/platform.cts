/*
 * Copyright 2024 The Android Open Source Project
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

package android.virtualdevice.cts.camera;

import static android.virtualdevice.cts.camera.util.VirtualCameraUtils.createHandler;

import static com.google.common.truth.Truth.assertThat;

import android.media.Image;
import android.media.ImageWriter;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Handler;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Arrays;

/**
 * A fake pair of video encoder/decoder writing mock data
 * on a surface and incrementing by 1 the provided timestamp for each decoded frame.
 */
public class SteadyTimestampCodec implements AutoCloseable {

    private static final int VIDEO_BITRATE = 4000000;
    private static final int FRAME_RATE = 30;
    private static final int I_FRAME_INTERVAL = 0;
    private static final String MIMETYPE = MediaFormat.MIMETYPE_VIDEO_AVC;
    private static final int TIMEOUT_MILLIS = 1000;
    private static final String TAG = "SteadyTimestampCodec";
    private static final boolean DEBUG = false;
    public static final int TIMESTAMP_INCREMENT_NS = 1_000_000_000 / FRAME_RATE;
    private final AtomicReference<MediaCodec> mDecoderRef;
    private final AtomicReference<MediaCodec> mEncoderRef;

    private final AtomicReference<Boolean> mCodecRunning = new AtomicReference<>(false);
    private final LinkedBlockingDeque<byte[]> mBufferQueue = new LinkedBlockingDeque<>();
    private final int mWidth;
    private final int mHeight;
    private long mRenderTimestampNs = 0;
    private Handler mDecoderHandler;
    private Handler mEncoderHandler;

    private long mLastWrittenTimestampNs = -1L;

    private abstract static class MediaCodecCallback extends MediaCodec.Callback {
        @Override
        public void onError(@NonNull MediaCodec mediaCodec,
                @NonNull MediaCodec.CodecException exception) {
            throw exception;
        }

        @Override
        public void onOutputFormatChanged(@NonNull MediaCodec mediaCodec,
                @NonNull MediaFormat mediaFormat) {
            // Do nothing;
        }
    }

    /**
     * Create a codec with presentation timestamp starting at 0.
     *
     * @param width             The width of the video to encode/decode
     * @param height            The height of the video to encode/decode
     */
    public SteadyTimestampCodec(int width, int height) {
        mWidth = width;
        mHeight = height;
        mEncoderRef = new AtomicReference<>(createEncoder());
        mDecoderRef = new AtomicReference<>(null);
    }

    private static void writeBlankFrame(@NonNull Surface surface) {
        ImageWriter imageWriter = ImageWriter.newInstance(surface, 1);
        Image image = imageWriter.dequeueInputImage();
        image.setTimestamp(1);
        imageWriter.queueInputImage(image);
        imageWriter.close();
    }

    private MediaCodec createEncoder() {
        MediaCodec.Callback encoderCallback = new MediaCodecCallback() {
            @Override
            public void onInputBufferAvailable(@NonNull MediaCodec encoder, int i) {
                if (DEBUG) {
                    Log.d(TAG,
                            "encoder onInputBufferAvailable() called with: codec = [" + encoder
                                    + "], i = ["
                                    + i + "] mCodecRunning = " + mCodecRunning.get());
                }
                if (!mCodecRunning.get()) {
                    return;
                }
                try {
                    if (i >= 0) {
                        ByteBuffer inputBuffer = encoder.getInputBuffer(i);
                        assertThat(inputBuffer).isNotNull();
                        inputBuffer.clear();
                        byte[] blackFrameData = generateBlackFrameData(mWidth, mHeight);
                        inputBuffer.put(blackFrameData);
                        if (DEBUG) {
                            Log.d(TAG, "encoder queueInputBuffer() called with: codec = ["
                                    + encoder + "], i = [" + i + "]");
                        }
                        encoder.queueInputBuffer(i, 0, blackFrameData.length, 0, 0); // Custom PTS
                    }
                } catch (IllegalStateException exception) {
                    mCodecRunning.set(false);
                }
            }

            @Override
            public void onOutputBufferAvailable(@NonNull MediaCodec mediaCodec, int i,
                    @NonNull MediaCodec.BufferInfo bufferInfo) {
                if (DEBUG) {
                    Log.d(TAG,
                            "encoder onOutputBufferAvailable() called with: codec = ["
                                    + mediaCodec
                                    + "], i = ["
                                    + i + "] mCodecRunning = " + mCodecRunning.get());
                }
                if (!mCodecRunning.get()) {
                    return;
                }
                ByteBuffer outputBuffer = mediaCodec.getOutputBuffer(i);
                assertThat(outputBuffer).isNotNull();
                byte[] bytes = new byte[outputBuffer.remaining()];
                outputBuffer.get(bytes);
                mBufferQueue.offer(bytes);
                mediaCodec.releaseOutputBuffer(i, false);
            }
        };

        MediaFormat format = MediaFormat.createVideoFormat(MIMETYPE, mWidth, mHeight);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
        format.setInteger(MediaFormat.KEY_BIT_RATE, VIDEO_BITRATE);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL);

        try {
            MediaCodec encoder = MediaCodec.createEncoderByType(MIMETYPE);
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mEncoderHandler = createHandler("encoder-callback");
            encoder.setCallback(encoderCallback, mEncoderHandler);
            return encoder;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private MediaCodec createDecoder(Surface surface) {
        MediaCodec.Callback decoderCallback = new MediaCodecCallback() {
            @Override
            public void onInputBufferAvailable(@NonNull MediaCodec mediaCodec, int i) {
                if (DEBUG) {
                    Log.d(TAG,
                            "decoder onInputBufferAvailable() called with: codec = ["
                                    + mediaCodec
                                    + "], i = ["
                                    + i + "] mCodecRunning = " + mCodecRunning.get());
                }
                if (!mCodecRunning.get()) {
                    return;
                }
                try {
                    byte[] bytes = mBufferQueue.poll(TIMEOUT_MILLIS,
                            TimeUnit.MILLISECONDS);
                    if (!mCodecRunning.get()) {
                        return;
                    }
                    if (bytes == null) {
                        Log.w(TAG, "decoder: onInputBufferAvailable() no data queued");
                        return;
                    }
                    ByteBuffer inputBuffer = mediaCodec.getInputBuffer(i);
                    assertThat(inputBuffer).isNotNull();
                    inputBuffer.put(bytes);
                    mediaCodec.queueInputBuffer(i, 0, bytes.length, 0, 0);
                } catch (InterruptedException e) {
                    throw new RuntimeException("Timeout polling for encoded buffer", e);
                } catch (IllegalStateException e) {
                    mCodecRunning.set(false);
                }
            }

            @Override
            public void onOutputBufferAvailable(@NonNull MediaCodec mediaCodec, int i,
                    @NonNull MediaCodec.BufferInfo bufferInfo) {
                if (DEBUG) {
                    Log.d(TAG,
                            "decoder onOutputBufferAvailable() called with: codec = [" + mediaCodec
                                    + "], i = [" + i
                                    + "] mCodecRunning = \" + mCodecRunning.get());");
                }
                if (!mCodecRunning.get()) {
                    return;
                }
                if (DEBUG) {
                    Log.d(TAG, "decoder onOutputBufferAvailable() mRenderTimestampNs:"
                            + mRenderTimestampNs);
                }
                mediaCodec.releaseOutputBuffer(i, mRenderTimestampNs);
                mLastWrittenTimestampNs = mRenderTimestampNs;
                mRenderTimestampNs += TIMESTAMP_INCREMENT_NS;
            }
        };
        try {
            MediaFormat format = MediaFormat.createVideoFormat(MIMETYPE, mWidth, mHeight);
            MediaCodec decoder = MediaCodec.createDecoderByType(MIMETYPE);
            decoder.configure(format, surface, null, 0);
            mDecoderHandler = createHandler("decoder-callback");
            decoder.setCallback(decoderCallback, mDecoderHandler);
            return decoder;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static byte[] generateBlackFrameData(int width, int height) {
        int ySize = width * height;
        int uvSize = ySize / 4;
        byte[] data = new byte[ySize + uvSize * 2];

        // Y plane (black)
        Arrays.fill(data, 0, ySize, (byte) 0);
        // U and V planes (neutral gray)
        Arrays.fill(data, ySize, ySize + uvSize * 2, (byte) 0xFF);
        return data;
    }

    public long getLastRenderTimestampNs() {
        return mLastWrittenTimestampNs;
    }

    /**
     * Set the output surface onto which the decoded data should be written and start the codec.
     */
    public void setSurfaceAndStart(@NonNull Surface surface) {
        writeBlankFrame(surface);
        MediaCodec decoder = createDecoder(surface);
        mDecoderRef.set(decoder);
        mCodecRunning.set(true);
        mEncoderRef.get().start();
        decoder.start();
    }


    /** Stops and release the codecs */
    @Override
    public void close() {
        mCodecRunning.set(false);
        mDecoderRef.get().stop();
        mEncoderRef.get().stop();
        mDecoderRef.get().release();
        mEncoderRef.get().release();
        mDecoderHandler.getLooper().quit();
        mEncoderHandler.getLooper().quit();
    }
}
