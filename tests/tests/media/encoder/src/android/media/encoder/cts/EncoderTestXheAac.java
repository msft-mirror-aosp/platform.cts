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
 *
 */

package android.media.encoder.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.swcodec.flags.Flags;
import android.os.Build;
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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.BitSet;

@SmallTest
@AppModeFull(reason = "Instant apps cannot access the SD card")
public class EncoderTestXheAac {
    private static final String TAG = "EncoderTestXheAac";
    private static final int AAC_FRAME_SIZE = 1024;
    private static final long K_TIMEOUT_US = 100;

    /*
     * Set this to true to save the encoding results to /data/local/tmp
     * You will need to make /data/local/tmp writeable, run "setenforce 0",
     * and remove files left from a previous run.
     */
    private static boolean sSaveResults = false;

    static final String mInpPrefix = WorkDir.getMediaDirString();

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

    /* package */ static class WavInfo {
        int channels;
        int sampleRate;
        int bitsPerSample;
        int dataStart;
    }

    private static WavInfo parseWavHeader(InputStream fis) throws IOException {
        byte[] header = new byte[128];
        int bytesRead = fis.read(header, 0, 128);

        if (bytesRead < 44) {
            throw new IOException("Invalid WAV header - file too short");
        }

        String riff = new String(header, 0, 4);
        String wave = new String(header, 8, 4);
        if (!riff.equals("RIFF") || !wave.equals("WAVE")) {
            throw new IOException("Not a valid WAV file");
        }

        WavInfo info = new WavInfo();
        info.channels = (header[22] & 0xff) | ((header[23] & 0xff) << 8);
        info.sampleRate = (header[24] & 0xff) |
                ((header[25] & 0xff) << 8) |
                ((header[26] & 0xff) << 16) |
                ((header[27] & 0xff) << 24);
        info.bitsPerSample = (header[34] & 0xff) | ((header[35] & 0xff) << 8);

        int dataChunk = findDataChunk(header);
        if (dataChunk < 0) {
            throw new IOException("Could not find 'data' chunk in WAV file");
        }

        info.dataStart = dataChunk + 8;

        return info;
    }

    private static int findDataChunk(byte[] header) {
        byte[] dataMarker = {'d', 'a', 't', 'a'};
        for (int i = 0; i <= header.length - dataMarker.length; i++) {
            boolean found = true;
            for (int j = 0; j < dataMarker.length; j++) {
                if (header[i + j] != dataMarker[j]) {
                    found = false;
                    break;
                }
            }
            if (found) {
                return i;
            }
        }
        return -1;
    }

    /* package */ static WavInfo getWavFileInfo(String audioFileName) throws IOException {
        String audioFilePath = mInpPrefix + audioFileName;
        Preconditions.assertTestFileExists(audioFilePath);

        File audioFile = new File(audioFilePath);
        FileInputStream fis = new FileInputStream(audioFile);
        WavInfo info = parseWavHeader(fis);
        fis.close();
        return info;
    }

    private MediaCodec createCodec(MediaFormat format) throws IOException {
        MediaCodecList mcl = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
        String encoderName = mcl.findEncoderForFormat(format);
        return MediaCodec.createByCodecName(encoderName);
    }

    /* package */ static FileInputStream openWavFile(String audioFileName, WavInfo wavInfo)
            throws IOException {
        String audioFilePath = mInpPrefix + audioFileName;
        File audioFile = new File(audioFilePath);
        FileInputStream fis = new FileInputStream(audioFile);
        fis.skip(wavInfo.dataStart);
        return fis;
    }

    private void encode(MediaCodec codec, MediaMuxer muxer, InputStream fis, WavInfo wavInfo,
                        StreamVerifier verifier) throws IOException {
        codec.start();

        boolean sawInputEOS = false;
        boolean sawOutputEOS = false;
        boolean muxerStarted = false;
        int trackIndex = -1;

        long presentationTimeUs = 0;
        byte[] buffer = new byte[AAC_FRAME_SIZE * wavInfo.channels * (wavInfo.bitsPerSample / 8)];
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

        while (!sawOutputEOS) {
            // Feed input
            if (!sawInputEOS) {
                int inputBufferId = codec.dequeueInputBuffer(K_TIMEOUT_US);
                if (inputBufferId >= 0) {
                    ByteBuffer inputBuffer = codec.getInputBuffer(inputBufferId);
                    inputBuffer.clear();

                    int read = fis.read(buffer);

                    if (read == -1) {
                        codec.queueInputBuffer(inputBufferId, 0, 0, presentationTimeUs,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        sawInputEOS = true;
                    } else {
                        inputBuffer.put(buffer, 0, read);
                        codec.queueInputBuffer(inputBufferId, 0, read, presentationTimeUs, 0);
                        presentationTimeUs += AAC_FRAME_SIZE * 1000000L / wavInfo.sampleRate;
                    }
                }
            }

            // Get output
            int outputBufferId = codec.dequeueOutputBuffer(info, K_TIMEOUT_US);

            if (outputBufferId >= 0) {
                ByteBuffer outputBuffer = codec.getOutputBuffer(outputBufferId);

                if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    if (!muxerStarted) {
                        MediaFormat outFormat = codec.getOutputFormat();
                        trackIndex = muxer.addTrack(outFormat);
                        muxer.start();
                        muxerStarted = true;
                    }
                    verifier.checkDecoderConfig(outputBuffer);
                } else if (info.size > 0 ||
                        (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    if (!muxerStarted) {
                        MediaFormat outFormat = codec.getOutputFormat();
                        trackIndex = muxer.addTrack(outFormat);
                        muxer.start();
                        muxerStarted = true;
                    }

                    if (info.size > 0) {
                        outputBuffer.position(info.offset);
                        outputBuffer.limit(info.offset + info.size);
                        verifier.checkFrame(outputBuffer,
                                (info.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0);
                        muxer.writeSampleData(trackIndex, outputBuffer, info);
                    }
                }

                codec.releaseOutputBuffer(outputBufferId, false);

                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    sawOutputEOS = true;
                    verifier.checkEndOfStream();
                }
            }
        }
    }

    private void encodeXheAacOnePass(String inputFileName, String outputPath,
            MediaFormat format, StreamVerifier verifier) throws Exception {
        WavInfo wavInfo = getWavFileInfo(inputFileName);
        format.setInteger(MediaFormat.KEY_SAMPLE_RATE, wavInfo.sampleRate);
        format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, wavInfo.channels);

        FileInputStream fis = openWavFile(inputFileName, wavInfo);
        encodeXheAacOnePass(fis, wavInfo, outputPath, format, verifier);
    }

    private void encodeXheAacOnePass(InputStream fis, WavInfo wavInfo, String outputPath,
            MediaFormat format, StreamVerifier verifier) throws Exception {
        MediaCodec codec = createCodec(format);
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

        MediaMuxer muxer = null;
        if (sSaveResults) {
            new File(outputPath).delete();
            muxer = new MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        }

        try {
            if (sSaveResults) {
                encode(codec, muxer, fis, wavInfo, verifier);
            } else {
                encodeWithoutMuxer(codec, fis, wavInfo, verifier);
            }
        } finally {
            if (fis != null) {
                fis.close();
            }
            if (codec != null) {
                try {
                    codec.stop();
                } catch (Exception e) {
                    Log.w(TAG, "Exception while stopping codec", e);
                }
                codec.release();
            }
            if (muxer != null) {
                try {
                    muxer.stop();
                } catch (Exception e) {
                    Log.w(TAG, "Exception while stopping muxer", e);
                }
                muxer.release();
            }
        }
    }

    private void encodeWithoutMuxer(MediaCodec codec, InputStream fis, WavInfo wavInfo,
                                    StreamVerifier verifier) throws IOException {
        codec.start();

        final int inputBytesPerSample = wavInfo.channels * (wavInfo.bitsPerSample / 8);

        boolean sawInputEOS = false;
        boolean sawOutputEOS = false;

        long presentationTimeUs = 0;
        long totalSamplesQueued = 0;
        byte[] buffer = new byte[AAC_FRAME_SIZE * inputBytesPerSample];
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        MediaFormat outputFormat = null;

        while (!sawOutputEOS) {
            // Feed input
            if (!sawInputEOS) {
                int inputBufferId = codec.dequeueInputBuffer(K_TIMEOUT_US);
                if (inputBufferId >= 0) {
                    ByteBuffer inputBuffer = codec.getInputBuffer(inputBufferId);
                    inputBuffer.clear();

                    int read = fis.read(buffer);
                    presentationTimeUs = totalSamplesQueued * 1000000L / wavInfo.sampleRate;

                    if (read == -1) {
                        codec.queueInputBuffer(inputBufferId, 0, 0, presentationTimeUs,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        sawInputEOS = true;
                    } else {
                        inputBuffer.put(buffer, 0, read);
                        codec.queueInputBuffer(inputBufferId, 0, read, presentationTimeUs, 0);
                        totalSamplesQueued += (read / inputBytesPerSample);
                        presentationTimeUs += (read / inputBytesPerSample)
                                * 1000000L / wavInfo.sampleRate;
                    }
                }
            }

            // Get output
            int outputBufferId = codec.dequeueOutputBuffer(info, K_TIMEOUT_US);

            if (outputBufferId >= 0) {
                assertTrue("The output timestamp is larger than the input timestamp",
                        presentationTimeUs >= info.presentationTimeUs);

                ByteBuffer outputBuffer = codec.getOutputBuffer(outputBufferId);
                if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    verifier.checkDecoderConfig(outputBuffer);
                } else {
                    outputBuffer.position(info.offset);
                    outputBuffer.limit(info.offset + info.size);
                    verifier.checkFrame(outputBuffer,
                            (info.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0);
                }
                codec.releaseOutputBuffer(outputBufferId, false);

                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    sawOutputEOS = true;
                    verifier.checkEndOfStream();
                }
            } else if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                outputFormat = codec.getOutputFormat();
            }
        }
        assertNotNull("No INFO_OUTPUT_FORMAT_CHANGED event was fired", outputFormat);
        assertEquals("The output sample format does not match", MediaFormat.MIMETYPE_AUDIO_AAC,
                outputFormat.getString(MediaFormat.KEY_MIME));
        assertEquals("The number of input and output channels does not match", wavInfo.channels,
                outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT));
        int sampleRate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        assertTrue("The output sample rate does not match the expected values: "
                + Integer.toString(sampleRate), sampleRate == 44100 || sampleRate == 48000);

        // The PTS of the last output frame must differ by no more than 1 frame length from the
        // total input duration
        long tolerance = AAC_FRAME_SIZE * 1000000L /
                outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        assertTrue("The last output timestamp does not match the end-of-stream timestamp",
                info.presentationTimeUs <= presentationTimeUs
                        && (info.presentationTimeUs + tolerance) >= presentationTimeUs);
    }

    private static class BitParser {
        private int bitIndex;
        private BitSet bitSet;

        BitParser(ByteBuffer buffer) {
            byte[] bytes = new byte[buffer.remaining()];
            int oldPos = buffer.position();
            buffer.get(bytes);
            buffer.position(oldPos);

            // Reverse the bits, so we can use the "little-endian" BitSet for parsing the
            // "big-endian" data
            for(int i = 0; i < bytes.length; i++) {
                bytes[i] = (byte) (Integer.reverse(bytes[i]) >>> (Integer.SIZE - Byte.SIZE));
            }
            bitSet = BitSet.valueOf(bytes);
            bitIndex = 0;
        }

        long size() {
            return bitSet.size();
        }

        int read(int numBits) {
            long[] bits = bitSet.get(bitIndex, bitIndex + numBits).toLongArray();
            bitIndex += numBits;
            if (bits.length == 0) {
                return 0;
            }
            return (int) (Long.reverse(bits[0]) >>> (Long.SIZE - numBits));
        }

        int readEscaped(int firstBits, int secondBits, int thirdBits) {
            long tmp0 = read(firstBits);
            int result = (int) tmp0;
            if(tmp0 == (1 << firstBits) - 1) {
                long tmp1 = read(secondBits);
                result += (int) tmp1;
                if (tmp1 == (1 << secondBits) - 1) {
                    long tmp2 = read(thirdBits);
                    result += (int) tmp2;
                }
            }
            return result;
        }
    }

    /* package */ static class StreamVerifier {
        private final MediaFormat format;
        private final int expectedSbrFrameLengthIndex;
        private final int expectedMaxBitRate;
        private final int expectedCoreMode;
        private final int expectedStereoConfigIndex;
        private final int expectedSampleRate;

        private int[] elementTypes;
        private int[] defaultLengths;
        private boolean[] payloadFragmentations;
        private int[] extElementTypes;
        private long accumulatedBits;
        private long accumulatedDuration;
        private boolean firstFrame = false;

        public StreamVerifier(MediaFormat format, int sbrFrameLengthIndex, int maxBitRate,
                              int coreMode, int stereoConfigIndex, int sampleRate) {
            this.format = format;
            this.expectedSbrFrameLengthIndex = sbrFrameLengthIndex;
            this.expectedMaxBitRate = maxBitRate + 3 * 1024;
            this.expectedCoreMode = coreMode;
            this.expectedStereoConfigIndex = stereoConfigIndex;
            this.expectedSampleRate = sampleRate;
        }

        public void checkDecoderConfig(ByteBuffer buffer) {
            // As specified in ISO/IEC 23003-3
            final int[] SBR_RATIO_INDICES = {0 /* no SBR */, 0 /* no SBR */, 2 /* 8:3 */,
                    3 /* 2:1 */, 1 /* 4:1 */};
            final int[] SAMPLING_FREQUENCIES = {96000, 88200, 64000, 48000, 44100, 32000, 24000,
                    22050, 16000, 12000, 11025, 8000, 7350};

            assertTrue(buffer.remaining() > 0);

            // Check some DSC values and compare with expected format
            // NOTE: We only parse the DSC part/format relevant to the tests!
            BitParser bits = new BitParser(buffer);

            // Generic AAC config
            assertEquals(31, bits.read(5)); // AOT
            assertEquals(10, bits.read(6)); // extAOT 10 + 32 == 42 for xHE
            int frequencyIndex = bits.read(4);
            assertTrue(frequencyIndex < SAMPLING_FREQUENCIES.length);
            assertEquals(expectedSampleRate, SAMPLING_FREQUENCIES[frequencyIndex]);
            assertEquals(format.getInteger(MediaFormat.KEY_CHANNEL_COUNT), bits.read(4));

            // UsacConfig
            frequencyIndex = bits.read(5);
            assertTrue(frequencyIndex < SAMPLING_FREQUENCIES.length);
            assertEquals(expectedSampleRate, SAMPLING_FREQUENCIES[frequencyIndex]);
            int sbrFrameLengthIndex = bits.read(3);
            assertEquals(expectedSbrFrameLengthIndex, sbrFrameLengthIndex);
            int sbrRatioIndex = SBR_RATIO_INDICES[sbrFrameLengthIndex];
            assertEquals(format.getInteger(MediaFormat.KEY_CHANNEL_COUNT), bits.read(5));

            // UsacDecoderConfig
            int numElements = 1 + bits.readEscaped(4, 8, 16);
            elementTypes = new int[numElements];
            defaultLengths = new int[numElements];
            payloadFragmentations = new boolean[numElements];
            extElementTypes = new int[numElements];
            for (int i = 0; i < numElements; ++i) {
                elementTypes[i] = bits.read(2);
                switch (elementTypes[i]) {
                    case 0, 1 -> { // ID_USAC_SCE, ID_USAC_CPE
                        bits.read(2); // UsacCoreConfig
                        if (sbrRatioIndex > 0) {
                            // Skip SbrConfig
                            bits.read(3 + 4 + 4);
                            boolean headerExtra1 = bits.read(1) != 0;
                            boolean headerExtra2 = bits.read(1) != 0;
                            if (headerExtra1) {
                                bits.read(5);
                            }
                            if (headerExtra2) {
                                bits.read(6);
                            }

                            if (elementTypes[i] == 1) {
                                assertEquals(expectedStereoConfigIndex, bits.read(2));
                                if (expectedStereoConfigIndex > 0) {
                                    // Skip Mps212Config
                                    bits.read(6);
                                    int tempShapeConfig = bits.read(2);
                                    bits.read(4);
                                    if (bits.read(1) != 0) {
                                        bits.read(5);
                                    }
                                    if (expectedStereoConfigIndex >= 2) {
                                        bits.read(6);
                                    }
                                    if (tempShapeConfig == 2) {
                                        bits.read(1);
                                    }
                                }
                            }
                        }
                    }
                    case 2 -> { // ID_USAC_LFE
                        // nothing to parse
                    }
                    case 3 -> { // ID_USAC_EXT
                        // UsacExtElementConfig
                        extElementTypes[i] = bits.readEscaped(4, 8, 16);
                        int extLength = bits.readEscaped(4, 8, 16);
                        defaultLengths[i] = 0;
                        if (bits.read(1) != 0) {
                            defaultLengths[i] = bits.readEscaped(8, 16, 0) + 1;
                        }
                        payloadFragmentations[i] = bits.read(1) != 0;
                        // Skip all extensions for now
                        for (int k = 0; k < extLength; ++k) {
                            bits.read(8);
                        }
                    }
                }
            }

            int streamId = 0;
            if (bits.read(1) != 0) {
                // UsacConfigExtension
                int numExtensions = bits.readEscaped(2, 4, 8) + 1;
                for(int i = 0; i < numExtensions; i++) {
                    int extType = bits.readEscaped(4, 8, 16);
                    int extLength = bits.readEscaped(4, 8, 16);

                    if (extType == 7) {
                        streamId = bits.read(16);
                        extLength -= 2;
                    }

                    // Skip all extensions for now
                    for (int k = 0; k < extLength; ++k) {
                        bits.read(8);
                    }
                }
            }

            // if no explicit stream ID is set in the format, the encoder wrapper uses a default
            // value of 0
            assertEquals(format.getLong(MediaFormat.KEY_AUDIO_PRESENTATION_ID, 0), streamId);
        }

        public void checkFrame(ByteBuffer buffer, boolean isKeyFrame) {
            final int[] SBR_OUTPUT_FRAME_LENGTHS = {768, 1024, 2048, 2048, 4096};

            if (buffer.remaining() == 0) {
                return;
            }

            BitParser bits = new BitParser(buffer);

            assertTrue(bits.size() > 0);
            accumulatedBits += bits.size();
            accumulatedDuration += SBR_OUTPUT_FRAME_LENGTHS[expectedSbrFrameLengthIndex];

            // UsacFrame
            boolean isIndependent = bits.read(1) != 0;

            boolean hasAudioPreroll = false;
            for (int i = 0; i < elementTypes.length; ++i) {
                switch (elementTypes[i]) {
                    case 0, 1 -> { // ID_USAC_SCE, ID_USAC_CPE
                        int numChannels = elementTypes[i] == 0 ? 1 :
                                (expectedStereoConfigIndex == 1 ? 1 : 2);
                        // UsacCoreCoderData
                        for (int c = 0; c < numChannels; c++) {
                            int coreMode = bits.read(1);
                            if (expectedCoreMode == 0 || expectedCoreMode == 3) {
                                assertEquals(0, coreMode);
                            } else if (expectedCoreMode == 1) {
                                assertEquals(1, coreMode);
                            }
                        }
                    }
                    case 2 -> { // ID_USAC_LFE
                        assertTrue("Parsing for LFE elements is not implemented", false);
                    }
                    case 3 -> { // ID_USAC_EXT
                        // UsacExtElement
                        if (bits.read(1) != 0) {
                            int extElementPayloadLength = 0;
                            if (bits.read(1) != 0) {
                                extElementPayloadLength = defaultLengths[i];
                            } else {
                                extElementPayloadLength = bits.read(8);
                                if (extElementPayloadLength == 255) {
                                    extElementPayloadLength += bits.read(16) - 2;
                                }
                            }
                            if (extElementPayloadLength > 0) {
                                if (payloadFragmentations[i]) {
                                    bits.read(2);
                                }

                                if (extElementTypes[i] == 3) { // ID_EXT_ELE_AUDIOPREROLL
                                    hasAudioPreroll = true;
                                }

                                // Skip all extension payloads for now
                                for (int k = 0; k < extElementPayloadLength; ++k) {
                                    bits.read(8);
                                }
                            }
                        }
                    }
                }
            }

            if(isKeyFrame) {
                assertTrue(isIndependent);
            }
            assertEquals(isKeyFrame, isIndependent && hasAudioPreroll);
            if(firstFrame) {
                assertTrue(isKeyFrame);
            }
            firstFrame = false;
        }

        public void checkEndOfStream() {
            final int SAMPLE_RATE = 48000;
            int bitRate = (int) (accumulatedBits * SAMPLE_RATE / accumulatedDuration);
            assertTrue("Bitrate of " + bitRate + " b/s exceeds maximum of " +
                    expectedMaxBitRate + " b/s", bitRate <= expectedMaxBitRate);
            assertTrue(expectedMaxBitRate >= format.getInteger(MediaFormat.KEY_BIT_RATE));
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_XHE_AAC_SOFTWARE_ENCODER)
    public void testXheAacOnePassCBR128() throws Exception {
        MediaFormat format = new MediaFormat();
        format.setString(MediaFormat.KEY_MIME, MediaFormat.MIMETYPE_AUDIO_AAC);
        format.setInteger(
                MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectXHE);
        format.setInteger(MediaFormat.KEY_BITRATE_MODE,
                MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR);
        format.setInteger(MediaFormat.KEY_BIT_RATE, 128000);
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, AAC_FRAME_SIZE);
        format.setFloat(MediaFormat.KEY_I_FRAME_INTERVAL, .896f);
        format.setLong(MediaFormat.KEY_AUDIO_PRESENTATION_ID, 128);
        encodeXheAacOnePass("okgoogle123_good.wav",
                "/data/local/tmp/testXheAacOnePassCBR128.mp4",
                format, new StreamVerifier(format, 1, 163920, 3 /* FD */, 0, 48000));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_XHE_AAC_SOFTWARE_ENCODER)
    public void testXheAacOnePassSilence44100() throws Exception {
        byte[] pcm = new byte[2 * 4 * 44100];
        WavInfo info = new WavInfo();
        info.channels = 2;
        info.sampleRate = 44100;
        info.bitsPerSample = 16;
        info.dataStart = 0;

        MediaFormat format = new MediaFormat();
        format.setString(MediaFormat.KEY_MIME, MediaFormat.MIMETYPE_AUDIO_AAC);
        format.setInteger(
                MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectXHE);
        format.setInteger(MediaFormat.KEY_BITRATE_MODE,
                MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR);
        format.setInteger(MediaFormat.KEY_BIT_RATE, 128000);
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, AAC_FRAME_SIZE);
        format.setFloat(MediaFormat.KEY_I_FRAME_INTERVAL, .896f);
        format.setInteger(MediaFormat.KEY_SAMPLE_RATE, info.sampleRate);
        format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, info.channels);

        encodeXheAacOnePass(new ByteArrayInputStream(pcm), info,
                "/data/local/tmp/silence_44100.mp4", format, new StreamVerifier(format, 1, 163920,
                3 /* FD */, 0, 44100));
    }
}
