/*
 * Copyright (C) 2026 The Android Open Source Project
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.res.AssetFileDescriptor;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.swcodec.flags.Flags;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.util.Log;

import androidx.test.filters.SmallTest;

import com.android.compatibility.common.util.Preconditions;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

@SmallTest
@AppModeFull(reason = "Instant apps cannot access the SD card")
public class EncoderTestXheAac {
    private static final String TAG = "EncoderTestXheAac";
    private static final int AAC_FRAME_SIZE = 1024;
    private static final long TIMEOUT_US = 100;
    private static final long US_PER_SEC = 1000000L;
    /*
     * Set this to true to save the encoding results to /data/local/tmp
     * You will need to make /data/local/tmp writeable, run "setenforce 0",
     * and remove files left from a previous run.
     */
    private static final boolean SAVE_RESULTS = false;
    private static final String sInpPrefix = WorkDir.getMediaDirString();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void before() {
        int apiLevel = Build.VERSION.SDK_INT;
        if (Build.VERSION.PREVIEW_SDK_INT > 0) {
            apiLevel++;
        }
        Assume.assumeTrue("Test only runs on Android C or later",
                apiLevel >= Build.VERSION_CODES.BAKLAVA + 1);
    }

    private MediaCodec createCodec() throws IOException {
        MediaFormat format = new MediaFormat();
        format.setString(MediaFormat.KEY_MIME, MediaFormat.MIMETYPE_AUDIO_AAC);
        format.setInteger(
                MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectXHE);
        MediaCodecList list = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
        String codecName = list.findEncoderForFormat(format);
        return MediaCodec.createByCodecName(codecName);
    }

    private static MediaExtractor createMediaExtractor(String resource) throws IOException {
        Preconditions.assertTestFileExists(sInpPrefix + resource);
        MediaExtractor mediaExtractor = new MediaExtractor();
        File inpFile = new File(sInpPrefix + resource);
        ParcelFileDescriptor parcelFD =
                ParcelFileDescriptor.open(inpFile, ParcelFileDescriptor.MODE_READ_ONLY);
        AssetFileDescriptor afd = new AssetFileDescriptor(parcelFD, 0, parcelFD.getStatSize());
        try {
            mediaExtractor.setDataSource(
                    afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
        } finally {
            afd.close();
        }
        int trackIndex = 0;
        for (; trackIndex < mediaExtractor.getTrackCount(); trackIndex++) {
            MediaFormat trackMediaFormat = mediaExtractor.getTrackFormat(trackIndex);
            if (trackMediaFormat.getString(MediaFormat.KEY_MIME).startsWith("audio/")) {
                mediaExtractor.selectTrack(trackIndex);
                break;
            }
        }
        if (trackIndex == mediaExtractor.getTrackCount()) {
            throw new IllegalStateException("couldn't get an audio track");
        }
        return mediaExtractor;
    }

    /**
     * Helper class to read fixed-size audio frames from MediaExtractor.
     *
     * <p>MediaExtractor returns audio data in "samples" (access units), which may not align with
     * the frame size required by the encoder (e.g. 1024 samples for AAC). This class buffers the
     * input from MediaExtractor and allows reading arbitrary amounts of data (up to the requested
     * length) to feed the encoder.
     */
    private static class ExtractorInput {
        MediaExtractor mExtractor;
        ByteBuffer mBuffer;
        boolean mExtractorEOS = false;

        ExtractorInput(MediaExtractor extractor) {
            mExtractor = extractor;
            mBuffer = ByteBuffer.allocate(0);
        }

        int read(ByteBuffer dst, int length) throws IOException {
            int bytesRead = 0;
            if (mBuffer.hasRemaining()) {
                int toCopy = Math.min(length, mBuffer.remaining());
                int oldLimit = mBuffer.limit();
                mBuffer.limit(mBuffer.position() + toCopy);
                dst.put(mBuffer);
                mBuffer.limit(oldLimit);
                bytesRead += toCopy;
                length -= toCopy;
            }
            if (length > 0 && !mExtractorEOS) {
                while (length > 0) {
                    long sampleSize = mExtractor.getSampleSize();
                    if (sampleSize < 0) {
                        mExtractorEOS = true;
                        break;
                    }
                    if (sampleSize <= length) {
                        int n = mExtractor.readSampleData(dst, dst.position());
                        if (n < 0) {
                            mExtractorEOS = true;
                            break;
                        }
                        dst.position(dst.position() + n);
                        mExtractor.advance();
                        bytesRead += n;
                        length -= n;
                    } else {
                        if (mBuffer.capacity() < sampleSize) {
                            mBuffer = ByteBuffer.allocate((int) sampleSize);
                        }
                        mBuffer.clear();
                        int n = mExtractor.readSampleData(mBuffer, 0);
                        if (n < 0) {
                            mExtractorEOS = true;
                            break;
                        }
                        mExtractor.advance();
                        mBuffer.position(0);
                        mBuffer.limit(n);
                        int toCopy = Math.min(length, mBuffer.remaining());
                        int oldLimit = mBuffer.limit();
                        mBuffer.limit(mBuffer.position() + toCopy);
                        dst.put(mBuffer);
                        mBuffer.limit(oldLimit);
                        bytesRead += toCopy;
                        length -= toCopy;
                    }
                }
            }
            return (bytesRead == 0 && mExtractorEOS) ? -1 : bytesRead;
        }
    }

    private interface OutputHandler {
        void onOutputBufferAvailable(MediaCodec codec, int index, MediaCodec.BufferInfo info)
                throws IOException;

        void onOutputFormatChanged(MediaCodec codec, MediaFormat format);
    }

    private long runEncodeLoop(
            MediaCodec codec,
            ExtractorInput extractorInput,
            int sampleRate,
            int inputBytesPerSample,
            MediaCodec.BufferInfo info,
            OutputHandler outputHandler)
            throws IOException {
        codec.start();
        boolean sawInputEOS = false;
        boolean sawOutputEOS = false;
        long presentationTimeUs = 0;
        int frameBytes = AAC_FRAME_SIZE * inputBytesPerSample;
        while (!sawOutputEOS) {
            // Feed input
            if (!sawInputEOS) {
                int inputBufferId = codec.dequeueInputBuffer(TIMEOUT_US);
                if (inputBufferId >= 0) {
                    ByteBuffer inputBuffer = codec.getInputBuffer(inputBufferId);
                    inputBuffer.clear();
                    int read = extractorInput.read(inputBuffer, frameBytes);
                    if (read == -1) {
                        codec.queueInputBuffer(
                                inputBufferId,
                                0,
                                0,
                                presentationTimeUs,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        sawInputEOS = true;
                    } else {
                        codec.queueInputBuffer(inputBufferId, 0, read, presentationTimeUs, 0);
                        presentationTimeUs +=
                                (read / inputBytesPerSample) * US_PER_SEC / sampleRate;
                    }
                }
            }
            // Get output
            int outputBufferId = codec.dequeueOutputBuffer(info, TIMEOUT_US);
            if (outputBufferId >= 0) {
                outputHandler.onOutputBufferAvailable(codec, outputBufferId, info);
                codec.releaseOutputBuffer(outputBufferId, false);
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    sawOutputEOS = true;
                }
            } else if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                outputHandler.onOutputFormatChanged(codec, codec.getOutputFormat());
            }
        }
        return presentationTimeUs;
    }

    private void encode(
            MediaCodec codec,
            MediaMuxer muxer,
            ExtractorInput extractorInput,
            int sampleRate,
            int inputBytesPerSample)
            throws IOException {
        final MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        final MediaFormat[] outputFormat = new MediaFormat[1];
        final boolean[] muxerStarted = new boolean[1];
        final int[] trackIndex = new int[] {-1};
        long finalInputTimeUs =
                runEncodeLoop(
                        codec,
                        extractorInput,
                        sampleRate,
                        inputBytesPerSample,
                        info,
                        new OutputHandler() {
                            @Override
                            public void onOutputBufferAvailable(
                                    MediaCodec codec, int index, MediaCodec.BufferInfo info)
                                    throws IOException {
                                ByteBuffer outputBuffer = codec.getOutputBuffer(index);
                                if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                                    if (muxer != null && !muxerStarted[0]) {
                                        MediaFormat outFormat = codec.getOutputFormat();
                                        trackIndex[0] = muxer.addTrack(outFormat);
                                        muxer.start();
                                        muxerStarted[0] = true;
                                    }
                                } else if (info.size > 0
                                        || (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                                != 0) {
                                    if (muxer != null && !muxerStarted[0]) {
                                        MediaFormat outFormat = codec.getOutputFormat();
                                        trackIndex[0] = muxer.addTrack(outFormat);
                                        muxer.start();
                                        muxerStarted[0] = true;
                                    }
                                    if (info.size > 0 && muxer != null) {
                                        outputBuffer.position(info.offset);
                                        outputBuffer.limit(info.offset + info.size);
                                        muxer.writeSampleData(trackIndex[0], outputBuffer, info);
                                    }
                                }
                            }

                            @Override
                            public void onOutputFormatChanged(
                                    MediaCodec codec, MediaFormat format) {
                                outputFormat[0] = format;
                            }
                        });
        if (muxer == null) {
            assertNotNull("No INFO_OUTPUT_FORMAT_CHANGED event was fired", outputFormat[0]);
            assertEquals(
                    "The output sample MIME type does not match",
                    MediaFormat.MIMETYPE_AUDIO_AAC,
                    outputFormat[0].getString(MediaFormat.KEY_MIME));
            assertEquals(
                    "The number of input and output channels does not match",
                    inputBytesPerSample / 2, // Assuming 16-bit stereo for check
                    outputFormat[0].getInteger(MediaFormat.KEY_CHANNEL_COUNT));
            int outSampleRate = outputFormat[0].getInteger(MediaFormat.KEY_SAMPLE_RATE);
            assertTrue(
                    "The output sample rate does not match the expected values: "
                            + Integer.toString(outSampleRate),
                    outSampleRate == 44100 || outSampleRate == 48000);
            // The PTS of the last output frame must differ by no more than 1 frame length
            // from the total input duration
            long tolerance =
                    AAC_FRAME_SIZE
                            * 1000000L
                            / outputFormat[0].getInteger(MediaFormat.KEY_SAMPLE_RATE);
            assertTrue(
                    "The last output timestamp does not match the end-of-stream timestamp",
                    info.presentationTimeUs <= finalInputTimeUs
                            && (info.presentationTimeUs + tolerance) >= finalInputTimeUs);
        }
    }

    /* package */ ByteBuffer encodeFirstPass(
            MediaCodec codec,
            ExtractorInput extractorInput,
            int sampleRate,
            int inputBytesPerSample)
            throws IOException {
        final ByteBuffer[] loudnessData = new ByteBuffer[1];
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        runEncodeLoop(
                codec,
                extractorInput,
                sampleRate,
                inputBytesPerSample,
                info,
                new OutputHandler() {
                    @Override
                    public void onOutputBufferAvailable(
                            MediaCodec codec, int index, MediaCodec.BufferInfo info) {
                        ByteBuffer originalBuffer = codec.getOutputBuffer(index);
                        if (originalBuffer != null) {
                            loudnessData[0] = ByteBuffer.allocate(originalBuffer.remaining());
                            loudnessData[0].put(originalBuffer);
                            loudnessData[0].flip();
                        }
                    }

                    @Override
                    public void onOutputFormatChanged(MediaCodec codec, MediaFormat format) {}
                });
        return loudnessData[0];
    }

    private static int getInputBytesPerSample(MediaFormat inputFormat) {
        int channelCount = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        int pcmEncoding =
                inputFormat.getInteger(
                        MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT);
        int bytesPerSample;
        if (pcmEncoding == AudioFormat.ENCODING_PCM_8BIT) {
            bytesPerSample = 1;
        } else if (pcmEncoding == AudioFormat.ENCODING_PCM_FLOAT) {
            bytesPerSample = 4;
        } else if (pcmEncoding == AudioFormat.ENCODING_PCM_16BIT) {
            bytesPerSample = 2;
        } else {
            throw new IllegalArgumentException("Unsupported PCM encoding: " + pcmEncoding);
        }
        return channelCount * bytesPerSample;
    }

    private static class InputConfig {
        MediaExtractor mExtractor;
        int mSampleRate;
        int mChannelCount;
        int mInputBytesPerSample;
    }

    private InputConfig setupInput(String inputFileName) throws IOException {
        InputConfig config = new InputConfig();
        config.mExtractor = createMediaExtractor(inputFileName);
        MediaFormat inputFormat =
                config.mExtractor.getTrackFormat(config.mExtractor.getSampleTrackIndex());
        config.mSampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        config.mChannelCount = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        config.mInputBytesPerSample = getInputBytesPerSample(inputFormat);
        return config;
    }

    private MediaMuxer createMuxer(String outputPath) throws IOException {
        if (SAVE_RESULTS) {
            new File(outputPath).delete();
            return new MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        }
        return null;
    }

    private interface EncodeOperation {
        void execute(
                MediaCodec codec,
                MediaMuxer muxer,
                ExtractorInput input,
                int sampleRate,
                int inputBytesPerSample)
                throws IOException;
    }

    private void runEncodeOperation(
            MediaCodec codec,
            MediaMuxer muxer,
            MediaExtractor extractor,
            int sampleRate,
            int inputBytesPerSample,
            EncodeOperation operation)
            throws Exception {
        ExtractorInput extractorInput = new ExtractorInput(extractor);
        try {
            operation.execute(codec, muxer, extractorInput, sampleRate, inputBytesPerSample);
        } finally {
            if (extractor != null) {
                extractor.release();
            }
        }
    }

    /**
     * Encodes the input file using xHE-AAC encoder in one pass.
     *
     * @param inputFileName The name of the input file.
     * @param outputPath The path to the output file.
     * @param format The MediaFormat for the encoder.
     */
    private void encodeXheAacOnePass(String inputFileName, String outputPath, MediaFormat format)
            throws Exception {
        InputConfig input = setupInput(inputFileName);
        MediaCodec codec = null;
        MediaMuxer muxer = null;
        try {
            format.setInteger(MediaFormat.KEY_SAMPLE_RATE, input.mSampleRate);
            format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, input.mChannelCount);
            codec = createCodec();
            // surface and crypto are null because we are encoding from a buffer and not using
            // crypto
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            muxer = createMuxer(outputPath);
            runEncodeOperation(
                    codec,
                    muxer,
                    input.mExtractor,
                    input.mSampleRate,
                    input.mInputBytesPerSample,
                    (c, m, inp, sr, bps) -> encode(c, m, inp, sr, bps));
        } finally {
            if (codec != null) {
                try {
                    codec.stop();
                } catch (Exception e) {
                    Log.i(TAG, "caught exception stopping codec: " + e);
                } finally {
                    codec.release();
                }
            }
            if (muxer != null) {
                try {
                    muxer.stop();
                } catch (Exception e) {
                    Log.i(TAG, "caught exception stopping muxer: " + e);
                } finally {
                    muxer.release();
                }
            }
        }
    }

    private MediaFormat createBaseFormat() {
        MediaFormat format = new MediaFormat();
        format.setString(MediaFormat.KEY_MIME, MediaFormat.MIMETYPE_AUDIO_AAC);
        format.setInteger(
                MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectXHE);
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, AAC_FRAME_SIZE);
        return format;
    }

    private ByteBuffer createRapIntervals(int[] intervals) {
        ByteBuffer rapIntervals =
                ByteBuffer.allocateDirect(intervals.length * Integer.BYTES)
                        .order(ByteOrder.nativeOrder());
        rapIntervals.asIntBuffer().put(intervals);
        return rapIntervals;
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_XHE_AAC_SOFTWARE_ENCODER)
    public void testXheAacOnePassIFrameIntervalCBR128() throws Exception {
        MediaFormat format = createBaseFormat();
        format.setInteger(
                MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR);
        format.setInteger(MediaFormat.KEY_BIT_RATE, 128000);
        format.setFloat(MediaFormat.KEY_I_FRAME_INTERVAL, .896f);
        encodeXheAacOnePass(
                "okgoogle123_good.wav",
                "/data/local/tmp/testXheAacOnePassIFrameIntervalCBR128.mp4",
                format);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_XHE_AAC_SOFTWARE_ENCODER)
    public void testXheAacOnePassVBR4() throws Exception {
        MediaFormat format = createBaseFormat();
        format.setInteger(
                MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR);
        format.setInteger(MediaFormat.KEY_QUALITY, 4);
        format.setInteger(MediaFormat.KEY_BIT_RATE, 100000);
        encodeXheAacOnePass(
                "okgoogle123_good.wav", "/data/local/tmp/testXheAacOnePassCBR128.mp4", format);
    }
}
