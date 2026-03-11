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

import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaCodecList;
import android.media.swcodec.flags.Flags;
import android.os.Build;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.util.Log;

import androidx.test.filters.SmallTest;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.DataOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import android.media.encoder.cts.EncoderTestXheAac.WavInfo;
import java.util.Arrays;
import java.util.Random;

@SmallTest
@AppModeFull(reason = "Instant apps cannot access the SD card")
public class EncDecTestXheAac {
    private static final String TAG = "EncDecTestXheAac";
    private static final int AAC_FRAME_SIZE = 1024;
    private static final long K_TIMEOUT_US = 5000;

    /*
     * Set this to true to save the decoding results to /data/local/tmp
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

    private MediaCodec createEncoder(MediaFormat format)
            throws IOException {
        MediaCodecList mcl = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
        String encoderName = mcl.findEncoderForFormat(format);
        MediaCodec codec = MediaCodec.createByCodecName(encoderName);
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        return codec;
    }

    private MediaCodec createDecoder(MediaFormat format) throws IOException {
        MediaCodec codec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
        format.setString(MediaFormat.KEY_MIME, MediaFormat.MIMETYPE_AUDIO_AAC);
        format.setInteger(MediaFormat.KEY_AAC_PROFILE,
                MediaCodecInfo.CodecProfileLevel.AACObjectXHE);
        codec.configure(format, null, null, 0 /* decode */);

        return codec;
    }

    private double[] calculateRMS(short[] decSamples, int nCh, int sampleRate,
                int measureAfterSeconds) {
        int startIdx = measureAfterSeconds * sampleRate * nCh;
        if (startIdx > decSamples.length) {
            throw new IllegalArgumentException(
                    "measureAfterSeconds exceeds available audio data");
        }
        double[] rmsDb = new double[nCh + 1];
        double sumRmsSquared = 0.0;

        // Calculate RMS per channel
        for (int ch = 0; ch < nCh; ch++) {
            double sumSquares = 0.0;
            int count = 0;

            for (int i = startIdx + ch; i < decSamples.length; i += nCh) {
                double sample = decSamples[i] / 32768.0;
                sumSquares += sample * sample;
                count++;
            }

            double rms = Math.sqrt(sumSquares / count);
            rmsDb[ch + 1] = 20 * Math.log10(rms);
            sumRmsSquared += rms * rms;
        }

        // Combined RMS
        rmsDb[0] = 20 * Math.log10(Math.sqrt(sumRmsSquared));

        return rmsDb;
    }

    private static int nextPowerOf2(int n) {
        int power = 1;
        while (power < n) {
            power <<= 1;
        }
        return power;
    }

    public static double estimateBandwidth(short[] samples, int sampleRate, int channels) {
        int numSamples = samples.length / channels;
        int n = nextPowerOf2(numSamples);

        // FFT (real + imaginary parts interleaved, Hann windowed & normalized)
        double[] fft = new double[n * 2];
        for (int i = 0; i < numSamples; i++) {
            double w = 0.5 * (1 - Math.cos(2 * Math.PI * i / (numSamples - 1)));
            fft[2 * i] = samples[i*channels] * w;
            fft[2 * i + 1] = 0;
        }
        fft(fft, n);

        // Compute magnitude spectrum (only first half — up to Nyquist)
        int half = n / 2;
        double[] magnitude = new double[half];
        double peakMag = 0;
        for (int i = 0; i < half; i++) {
            double re = fft[2 * i];
            double im = fft[2 * i + 1];
            magnitude[i] = Math.sqrt(re * re + im * im);
            if (magnitude[i] > peakMag) peakMag = magnitude[i];
        }

        // Find highest frequency bin above threshold (e.g. 1% of peak)
        double threshold = peakMag * 0.01;
        int lastBin = 0;
        for (int i = 0; i < half; i++) {
            if (magnitude[i] >= threshold) lastBin = i;
        }

        // Convert bin index to frequency
        double bandwidth = (double) lastBin * sampleRate / n;
        return bandwidth;
    }

    // In-place Cooley-Tukey FFT
    // data: interleaved [re0, im0, re1, im1, ...], length must be 2*n where n is a power of 2
    private static void fft(double[] data, int n) {
        // Bit-reversal permutation
        int j = 0;
        for (int i = 1; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) {
                j ^= bit;
            }
            j ^= bit;
            if (i < j) {
                double tmpRe = data[2*i];
                data[2*i] = data[2*j];
                data[2*j] = tmpRe;
                double tmpIm = data[2*i+1];
                data[2*i+1] = data[2*j+1];
                data[2*j+1] = tmpIm;
            }
        }
        // Butterfly passes
        for (int len = 2; len <= n; len <<= 1) {
            double angle = -2 * Math.PI / len;
            double wRe = Math.cos(angle);
            double wIm = Math.sin(angle);
            for (int i = 0; i < n; i += len) {
                double curRe = 1;
                double curIm = 0;
                for (int k = 0; k < len / 2; k++) {
                    int u = i + k;
                    int v = i + k + len / 2;
                    double uRe = data[2*u];
                    double uIm = data[2*u+1];
                    double vRe = data[2*v] * curRe - data[2*v+1] * curIm;
                    double vIm = data[2*v] * curIm + data[2*v+1] * curRe;
                    data[2*u]   = uRe + vRe;
                    data[2*u+1] = uIm + vIm;
                    data[2*v]   = uRe - vRe;
                    data[2*v+1] = uIm - vIm;
                    double newCurRe = curRe * wRe - curIm * wIm;
                    curIm = curRe * wIm + curIm * wRe;
                    curRe = newCurRe;
                }
            }
        }
    }

    private short[] generateStereoSine(int fs, int durationSeconds, int frequency,
                                       double amplitude) {
        int numSamples = fs * durationSeconds;
        short[] samples = new short[numSamples * 2];  // 2 channels

        double phaseIncrement = 2.0 * Math.PI * frequency / fs;

        for (int i = 0; i < numSamples; i++) {
            short sample = (short) (amplitude * Math.sin(phaseIncrement * i));

            // Interleave for stereo
            samples[i * 2] = sample;      // Left channel
            samples[i * 2 + 1] = sample;  // Right channel
        }

        return samples;
    }

    private short[] generateNoise(int fs, int durationSeconds, int channels, double amplitude) {
        short[] samples = new short[fs * durationSeconds * channels];
        Random random = new Random();

        for (int i = 0; i < samples.length; i++) {
            samples[i] = (short) (amplitude * (random.nextFloat() - 0.5));
        }

        return samples;
    }

    private void encodeDecode(MediaCodec encoder, MediaCodec decoder, FileInputStream fis,
                              File outputPath, WavInfo wavInfo) throws IOException {
        encoder.start();
        decoder.start();

        final int inputBytesPerSample = wavInfo.channels * (wavInfo.bitsPerSample / 8);

        boolean sawInputEOS = false;
        boolean sawEncodedEOS = false;
        boolean sawOutputEOS = false;

        byte[] buffer = new byte[AAC_FRAME_SIZE * inputBytesPerSample];
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        MediaFormat outputFormat = null;

        long inputPresentationTimeUs = 0;
        long encodedPresentTimeUs = 0;
        long outputPresentationTimeUs = 0;

        DataOutputStream out = null;

        while (!sawOutputEOS) {
            // Feed input
            if (!sawInputEOS) {
                int inputBufferId = encoder.dequeueInputBuffer(K_TIMEOUT_US);
                if (inputBufferId >= 0) {
                    ByteBuffer inputBuffer = encoder.getInputBuffer(inputBufferId);
                    inputBuffer.clear();

                    int read = fis.read(buffer);

                    if (read == -1) {
                        encoder.queueInputBuffer(inputBufferId, 0, 0, inputPresentationTimeUs,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        sawInputEOS = true;
                    } else {
                        inputBuffer.put(buffer, 0, read);
                        encoder.queueInputBuffer(inputBufferId, 0, read, inputPresentationTimeUs,
                                0);
                        inputPresentationTimeUs += (read / inputBytesPerSample) * 1000000L /
                                wavInfo.sampleRate;
                    }
                }
            }

            // Feed encoded to the decoder
            if(!sawEncodedEOS) {
                int outputBufferId = encoder.dequeueOutputBuffer(info, K_TIMEOUT_US);

                if (outputBufferId >= 0) {
                    ByteBuffer outputBuffer = encoder.getOutputBuffer(outputBufferId);

                    int inputBufferId = decoder.dequeueInputBuffer(K_TIMEOUT_US);
                    if (inputBufferId >= 0) {
                        ByteBuffer inputBuffer = decoder.getInputBuffer(inputBufferId);
                        inputBuffer.put(outputBuffer);
                        inputBuffer.flip();
                        decoder.queueInputBuffer(inputBufferId, info.offset, info.size,
                                info.presentationTimeUs, info.flags);
                    }

                    encodedPresentTimeUs = info.presentationTimeUs;

                    encoder.releaseOutputBuffer(outputBufferId, false);

                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        sawEncodedEOS = true;
                    }
                }
            }

            // Get output
            int outputBufferId = decoder.dequeueOutputBuffer(info, K_TIMEOUT_US);

            if (outputBufferId >= 0) {
                ByteBuffer outputBuffer = decoder.getOutputBuffer(outputBufferId);

                if (outputPath != null) {
                    if (out == null) {
                        out = writeSndHeader(outputPath, outputFormat);
                    }
                    writeBuffer(out, outputBuffer, info);
                }

                outputPresentationTimeUs = info.presentationTimeUs;

                decoder.releaseOutputBuffer(outputBufferId, false);

                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    sawOutputEOS = true;
                    if (out != null) {
                        out.flush();
                        out.close();
                    }
                }
            } else if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                outputFormat = decoder.getOutputFormat();
            }
        }

        assertNotNull("No INFO_OUTPUT_FORMAT_CHANGED event was fired", outputFormat);
        assertEquals("The number of input and output channels does not match", wavInfo.channels,
                outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT));
        int sampleRate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        assertTrue("The output sample rate does not match the expected values: " +
                Integer.toString(sampleRate), sampleRate == 44100 || sampleRate == 48000);

        // The PTS of the last encoded/decoded frames must differ by no more than 1 frame length
        //from the total input duration
        long tolerance = AAC_FRAME_SIZE * 1000000L /
                outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        assertTrue("The last output timestamp does not match the end-of-stream timestamp",
                encodedPresentTimeUs <= inputPresentationTimeUs &&
                        (encodedPresentTimeUs + tolerance) >= inputPresentationTimeUs);
        assertTrue("The last output timestamp does not match the end-of-stream timestamp",
                outputPresentationTimeUs <= inputPresentationTimeUs &&
                        (outputPresentationTimeUs + tolerance) >= inputPresentationTimeUs);
    }

    private void encodeDecodeXheAacOnePass(String inputFileName, String outputPath,
                                           MediaFormat format) throws Exception {
        WavInfo wavInfo = EncoderTestXheAac.getWavFileInfo(inputFileName);
        format.setInteger(MediaFormat.KEY_SAMPLE_RATE, wavInfo.sampleRate);
        format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, wavInfo.channels);
        MediaCodec encoder = createEncoder(format);
        MediaCodec decoder = createDecoder(format);

        FileInputStream fis = null;

        try {
            fis = EncoderTestXheAac.openWavFile(inputFileName, wavInfo);
            encodeDecode(encoder, decoder, fis, sSaveResults ? new File(outputPath) : null,
                    wavInfo);
        } finally {
            if (fis != null) {
                fis.close();
            }
            if (encoder != null) {
                try {
                    encoder.stop();
                } catch (Exception e) {
                    Log.w(TAG, "Exception while stopping encoder", e);
                }
                encoder.release();
            }
            if (decoder != null) {
                try {
                    decoder.stop();
                } catch (Exception e) {
                    Log.w(TAG, "Exception while stopping decoder", e);
                }
                decoder.release();
            }
        }
    }

    private void encodeDecodeXheAacOnePassWithBandwidth(short[] samples, String outputPath,
            MediaFormat format, int expectedBandwidth) throws Exception {
        WavInfo wavInfo = new WavInfo();
        wavInfo.sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        wavInfo.channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        wavInfo.bitsPerSample = 16;

        ByteBuffer inputData = ByteBuffer.allocate(samples.length * wavInfo.channels);
        inputData.order(ByteOrder.LITTLE_ENDIAN);
        for (short sample : samples) {
            inputData.putShort(sample);
        }
        inputData.rewind();

        File outputFile = sSaveResults ? new File(outputPath) : null;

        MediaCodec encoder = createEncoder(format);
        MediaCodec decoder = createDecoder(format);
        try {
            encoder.start();
            decoder.start();

            final int inputBytesPerSample = wavInfo.channels * (wavInfo.bitsPerSample / 8);

            boolean sawInputEOS = false;
            boolean sawEncodedEOS = false;
            boolean sawOutputEOS = false;

            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            MediaFormat outputFormat = null;

            long inputPresentationTimeUs = 0;
            long totalSamplesQueued = 0;
            long encodedPresentTimeUs = 0;
            long outputPresentationTimeUs = 0;

            DataOutputStream out = null;

            short[] decoded = new short[0];
            int decodedIdx = 0;

            while (!sawOutputEOS) {
                // Feed input
                if (!sawInputEOS) {
                    int inputBufferId = encoder.dequeueInputBuffer(K_TIMEOUT_US);
                    if (inputBufferId >= 0) {
                        ByteBuffer inputBuffer = encoder.getInputBuffer(inputBufferId);
                        inputBuffer.clear();

                        int read = Math.min(inputBuffer.remaining(), inputData.remaining());
                        inputPresentationTimeUs = totalSamplesQueued * 1000000L /
                                wavInfo.sampleRate;

                        if (read == 0 || !inputData.hasRemaining()) {
                            encoder.queueInputBuffer(inputBufferId, 0, 0, inputPresentationTimeUs,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            sawInputEOS = true;
                        } else {
                            int oldLimit = inputData.limit();
                            inputData.limit(inputData.position() + read);
                            inputBuffer.put(inputData);
                            inputData.limit(oldLimit);
                            encoder.queueInputBuffer(inputBufferId, 0, read,
                                    inputPresentationTimeUs, 0);
                            totalSamplesQueued += (read / inputBytesPerSample);
                        }
                    }
                }

                // Feed encoded to the decoder
                if(!sawEncodedEOS) {
                    int outputBufferId = encoder.dequeueOutputBuffer(info, K_TIMEOUT_US);

                    if (outputBufferId >= 0) {
                        ByteBuffer outputBuffer = encoder.getOutputBuffer(outputBufferId);

                        int inputBufferId = decoder.dequeueInputBuffer(K_TIMEOUT_US);
                        if (inputBufferId >= 0) {
                            ByteBuffer inputBuffer = decoder.getInputBuffer(inputBufferId);
                            inputBuffer.put(outputBuffer);
                            inputBuffer.flip();
                            decoder.queueInputBuffer(inputBufferId, info.offset, info.size,
                                    info.presentationTimeUs, info.flags);
                        }

                        encodedPresentTimeUs = info.presentationTimeUs;

                        encoder.releaseOutputBuffer(outputBufferId, false);

                        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            sawEncodedEOS = true;
                        }
                    }
                }

                // Get output
                int outputBufferId = decoder.dequeueOutputBuffer(info, K_TIMEOUT_US);

                if (outputBufferId >= 0) {
                    ByteBuffer outputBuffer = decoder.getOutputBuffer(outputBufferId);

                    // Expand array if needed
                    if (decodedIdx + (info.size / 2) >= decoded.length) {
                        decoded = Arrays.copyOf(decoded, decodedIdx + (info.size / 2));
                    }

                    // Copy samples directly into array
                    outputBuffer.position(info.offset);
                    for (int i = 0; i < info.size; i += 2) {
                        decoded[decodedIdx++] = outputBuffer.getShort();
                    }

                    outputBuffer.position(info.offset);

                    if (outputFile != null) {
                        if (out == null) {
                            out = writeSndHeader(outputFile, outputFormat);
                        }
                        writeBuffer(out, outputBuffer, info);
                    }

                    outputPresentationTimeUs = info.presentationTimeUs;

                    decoder.releaseOutputBuffer(outputBufferId, false);

                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        sawOutputEOS = true;
                        if (out != null) {
                            out.flush();
                            out.close();
                        }
                    }
                } else if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    outputFormat = decoder.getOutputFormat();
                }
            }

            assertNotNull("No INFO_OUTPUT_FORMAT_CHANGED event was fired", outputFormat);
            assertEquals("The number of input and output channels does not match",
                    wavInfo.channels, outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT));
            int sampleRate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            assertTrue("The output sample rate does not match the expected values: " +
                    Integer.toString(sampleRate), sampleRate == 44100 || sampleRate == 48000);

            // The PTS of the last encoded/decoded frames must differ by no more than 1 frame
            // length from the total input duration
            long tolerance = AAC_FRAME_SIZE * 1000000L / outputFormat.getInteger(
                    MediaFormat.KEY_SAMPLE_RATE);
            assertTrue("The last output timestamp does not match the end-of-stream timestamp",
                    encodedPresentTimeUs <= inputPresentationTimeUs &&
                            (encodedPresentTimeUs + tolerance) >= inputPresentationTimeUs);
            assertTrue("The last output timestamp does not match the end-of-stream timestamp",
                    outputPresentationTimeUs <= inputPresentationTimeUs &&
                            (outputPresentationTimeUs + tolerance) >= inputPresentationTimeUs);

            double bandwidth = estimateBandwidth(decoded, sampleRate,
                    format.getInteger(MediaFormat.KEY_CHANNEL_COUNT));
            Log.d(TAG, "Bandwidth: " + bandwidth);
            if (bandwidth < expectedBandwidth) {
                throw new Exception("Bandwidth of decoded signal not as expected");
            }

        } finally {
            try {
                encoder.stop();
            } catch (Exception e) {
                Log.w(TAG, "Exception while stopping encoder", e);
            }
            encoder.release();
            try {
                decoder.stop();
            } catch (Exception e) {
                Log.w(TAG, "Exception while stopping decoder", e);
            }
            decoder.release();
        }
    }

    private double encodeDecodeXheAacOnePassWithMeasurement(short[] samples, String outputPath,
            MediaFormat format, int skipSecondsForRMS) throws Exception {
        WavInfo wavInfo = new WavInfo();
        wavInfo.sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        wavInfo.channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        wavInfo.bitsPerSample = 16;

        ByteBuffer inputData = ByteBuffer.allocate(samples.length * wavInfo.channels);
        inputData.order(ByteOrder.LITTLE_ENDIAN);
        for (short sample : samples) {
            inputData.putShort(sample);
        }
        inputData.rewind();

        File outputFile = sSaveResults ? new File(outputPath) : null;

        MediaCodec encoder = createEncoder(format);
        MediaCodec decoder = createDecoder(format);
        try {
            encoder.start();
            decoder.start();

            final int inputBytesPerSample = wavInfo.channels * (wavInfo.bitsPerSample / 8);

            boolean sawInputEOS = false;
            boolean sawEncodedEOS = false;
            boolean sawOutputEOS = false;

            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            MediaFormat outputFormat = null;

            long inputPresentationTimeUs = 0;
            long totalSamplesQueued = 0;
            long encodedPresentTimeUs = 0;
            long outputPresentationTimeUs = 0;

            DataOutputStream out = null;

            short[] decoded = new short[0];
            int decodedIdx = 0;

            while (!sawOutputEOS) {
                // Feed input
                if (!sawInputEOS) {
                    int inputBufferId = encoder.dequeueInputBuffer(K_TIMEOUT_US);
                    if (inputBufferId >= 0) {
                        ByteBuffer inputBuffer = encoder.getInputBuffer(inputBufferId);
                        inputBuffer.clear();

                        int read = Math.min(inputBuffer.remaining(), inputData.remaining());
                        inputPresentationTimeUs = totalSamplesQueued * 1000000L
                                / wavInfo.sampleRate;

                        if (read == 0 || !inputData.hasRemaining()) {
                            encoder.queueInputBuffer(inputBufferId, 0, 0, inputPresentationTimeUs,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            sawInputEOS = true;
                        } else {
                            int oldLimit = inputData.limit();
                            inputData.limit(inputData.position() + read);
                            inputBuffer.put(inputData);
                            inputData.limit(oldLimit);
                            encoder.queueInputBuffer(inputBufferId, 0, read,
                                    inputPresentationTimeUs, 0);
                            totalSamplesQueued += (read / inputBytesPerSample);
                        }
                    }
                }

                // Feed encoded to the decoder
                if(!sawEncodedEOS) {
                    int outputBufferId = encoder.dequeueOutputBuffer(info, K_TIMEOUT_US);

                    if (outputBufferId >= 0) {
                        ByteBuffer outputBuffer = encoder.getOutputBuffer(outputBufferId);

                        int inputBufferId = decoder.dequeueInputBuffer(K_TIMEOUT_US);
                        if (inputBufferId >= 0) {
                            ByteBuffer inputBuffer = decoder.getInputBuffer(inputBufferId);
                            inputBuffer.put(outputBuffer);
                            inputBuffer.flip();
                            decoder.queueInputBuffer(inputBufferId, info.offset, info.size,
                                    info.presentationTimeUs, info.flags);
                        }

                        encodedPresentTimeUs = info.presentationTimeUs;

                        encoder.releaseOutputBuffer(outputBufferId, false);

                        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            sawEncodedEOS = true;
                        }
                    }
                }

                // Get output
                int outputBufferId = decoder.dequeueOutputBuffer(info, K_TIMEOUT_US);

                if (outputBufferId >= 0) {
                    ByteBuffer outputBuffer = decoder.getOutputBuffer(outputBufferId);

                    // Expand array if needed
                    if (decodedIdx + (info.size / 2) >= decoded.length) {
                        decoded = Arrays.copyOf(decoded, decodedIdx + (info.size / 2));
                    }

                    // Copy samples directly into array
                    outputBuffer.position(info.offset);
                    for (int i = 0; i < info.size; i += 2) {
                        decoded[decodedIdx++] = outputBuffer.getShort();
                    }

                    outputBuffer.position(info.offset);

                    if (outputFile != null) {
                        if (out == null) {
                            out = writeSndHeader(outputFile, outputFormat);
                        }
                        writeBuffer(out, outputBuffer, info);
                    }

                    outputPresentationTimeUs = info.presentationTimeUs;

                    decoder.releaseOutputBuffer(outputBufferId, false);

                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        sawOutputEOS = true;
                        if (out != null) {
                            out.flush();
                            out.close();
                        }
                    }
                } else if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    outputFormat = decoder.getOutputFormat();
                }
            }

            assertNotNull("No INFO_OUTPUT_FORMAT_CHANGED event was fired", outputFormat);
            assertEquals("The number of input and output channels does not match",
                    wavInfo.channels, outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT));
            int sampleRate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            assertTrue("The output sample rate does not match the expected values: " +
                    Integer.toString(sampleRate), sampleRate == 44100 || sampleRate == 48000);

            // The PTS of the last encoded/decoded frames must differ by no more than 1 frame
            // length from the total input duration
            long tolerance = AAC_FRAME_SIZE * 1000000L
                    / outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            assertTrue("The last output timestamp does not match the end-of-stream timestamp",
                    encodedPresentTimeUs <= inputPresentationTimeUs &&
                            (encodedPresentTimeUs + tolerance) >= inputPresentationTimeUs);
            assertTrue("The last output timestamp does not match the end-of-stream timestamp",
                    outputPresentationTimeUs <= inputPresentationTimeUs
                            && (outputPresentationTimeUs + tolerance) >= inputPresentationTimeUs);

            double[] rms = calculateRMS(decoded, wavInfo.channels, sampleRate, skipSecondsForRMS);
            Log.d(TAG, "RMS: " + rms[0]);

            return rms[0]; // return combined RMS for l+r

        } finally {
            try {
                encoder.stop();
            } catch (Exception e) {
                Log.w(TAG, "Exception while stopping encoder", e);
            }
            encoder.release();
            try {
                decoder.stop();
            } catch (Exception e) {
                Log.w(TAG, "Exception while stopping decoder", e);
            }
            decoder.release();
        }
    }

    private DataOutputStream writeSndHeader(File outputPath, MediaFormat format)
            throws IOException {
        outputPath.delete();

        DataOutputStream out = new DataOutputStream(new FileOutputStream(outputPath));
        out.writeInt(0x2e736e64);
        out.writeInt(32); // data offset
        out.writeInt(-1); // unknown data size
        int sampleFormat = format.getInteger(MediaFormat.KEY_PCM_ENCODING,
                AudioFormat.ENCODING_PCM_16BIT);
        out.writeInt(sampleFormat == AudioFormat.ENCODING_PCM_16BIT ?
                3 : sampleFormat == AudioFormat.ENCODING_PCM_32BIT ? 5 : 6);
        out.writeInt(format.getInteger(MediaFormat.KEY_SAMPLE_RATE));
        out.writeInt(format.getInteger(MediaFormat.KEY_CHANNEL_COUNT));
        out.writeInt(0);
        out.writeInt(0);
        out.flush();

        return out;
    }

    private void writeBuffer(DataOutputStream out, ByteBuffer buffer,
                             MediaCodec.BufferInfo info) throws IOException {
        for (int i = 0; i < info.size / 2; ++i) {
            out.writeShort(buffer.getShort());
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
        format.setLong(MediaFormat.KEY_AUDIO_PRESENTATION_ID, 128);
        format.setFloat(MediaFormat.KEY_I_FRAME_INTERVAL, .896f);
        encodeDecodeXheAacOnePass("okgoogle123_good.wav",
                "/data/local/tmp/testXheAacOnePassCBR128.snd",
                format);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_XHE_AAC_SOFTWARE_ENCODER)
    public void testXheAacOnePassVerifyLoudness() throws Exception {
        MediaFormat format = new MediaFormat();
        format.setString(MediaFormat.KEY_MIME, MediaFormat.MIMETYPE_AUDIO_AAC);
        format.setInteger(
                MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectXHE);
        format.setInteger(MediaFormat.KEY_BITRATE_MODE,
                MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR);
        format.setInteger(MediaFormat.KEY_BIT_RATE, 128000);
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, AAC_FRAME_SIZE);
        format.setFloat(MediaFormat.KEY_I_FRAME_INTERVAL, .896f);

        // use 5s 1kHz sine with 0.5 amplitude as test-signal
        int fs = 48000;
        int channels = 2;
        format.setInteger(MediaFormat.KEY_SAMPLE_RATE, fs);
        format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, channels);
        format.setInteger(MediaFormat.KEY_AAC_DRC_EFFECT_TYPE, -1); // switch off DRC application
        // explicitly set target level to -16.0 LUFS
        format.setInteger(MediaFormat.KEY_AAC_DRC_TARGET_REFERENCE_LEVEL, 64);
        short[] sineWave = generateStereoSine(fs, 5, 1000, Short.MAX_VALUE * 0.5);

        // Measure RMS after 3 seconds to allow leveler to reach target loudness
        int skipSecondsForRMS = 3;
        double rmsDb = encodeDecodeXheAacOnePassWithMeasurement(sineWave,
                "/data/local/tmp/sine_stereo.snd",
                format,
                skipSecondsForRMS);

        // The RMS of a 1kHz sine equals its loudness in LUFS. Therefore the target level of -16.0
        // LUFS should be reached.
        double expectedRmsDb = -16.0;
        double tolerance = 0.5;

        if (Math.abs(rmsDb - expectedRmsDb) > tolerance){
            throw new Exception("RMS value not as expected");
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_XHE_AAC_SOFTWARE_ENCODER)
    public void testXheAacOnePassCBR16_VerifyBandwidth() throws Exception {
        MediaFormat format = new MediaFormat();
        format.setString(MediaFormat.KEY_MIME, MediaFormat.MIMETYPE_AUDIO_AAC);
        format.setInteger(
                MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectXHE);
        format.setInteger(MediaFormat.KEY_BITRATE_MODE,
                MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR);
        format.setInteger(MediaFormat.KEY_BIT_RATE, 16000);
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, AAC_FRAME_SIZE);
        format.setFloat(MediaFormat.KEY_I_FRAME_INTERVAL, .896f);
        int expectedBw = 18000;

        // use full-band noise as test-signal
        int fs = 48000;
        int channels = 2;
        format.setInteger(MediaFormat.KEY_SAMPLE_RATE, fs);
        format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, channels);
        short[] noise = generateNoise(fs, 5, channels, Short.MAX_VALUE / 2.0);

        encodeDecodeXheAacOnePassWithBandwidth(noise,
                "/data/local/tmp/testXheAacOnePassCBR16_VerifyBandwidth.snd", format, expectedBw);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_XHE_AAC_SOFTWARE_ENCODER)
    public void testXheAacOnePassCBR24_VerifyBandwidth() throws Exception {
        MediaFormat format = new MediaFormat();
        format.setString(MediaFormat.KEY_MIME, MediaFormat.MIMETYPE_AUDIO_AAC);
        format.setInteger(
                MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectXHE);
        format.setInteger(MediaFormat.KEY_BITRATE_MODE,
                MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR);
        format.setInteger(MediaFormat.KEY_BIT_RATE, 24000);
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, AAC_FRAME_SIZE);
        format.setFloat(MediaFormat.KEY_I_FRAME_INTERVAL, .896f);
        int expectedBw = 18000;

        // use full-band noise as test-signal
        int fs = 48000;
        int channels = 2;
        format.setInteger(MediaFormat.KEY_SAMPLE_RATE, fs);
        format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, channels);
        short[] noise = generateNoise(fs, 5, channels, Short.MAX_VALUE / 2.0);

        encodeDecodeXheAacOnePassWithBandwidth(noise,
                "/data/local/tmp/testXheAacOnePassCBR24_VerifyBandwidth.snd", format, expectedBw);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_XHE_AAC_SOFTWARE_ENCODER)
    public void testXheAacOnePassCBR32_VerifyBandwidth() throws Exception {
        MediaFormat format = new MediaFormat();
        format.setString(MediaFormat.KEY_MIME, MediaFormat.MIMETYPE_AUDIO_AAC);
        format.setInteger(
                MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectXHE);
        format.setInteger(MediaFormat.KEY_BITRATE_MODE,
                MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR);
        format.setInteger(MediaFormat.KEY_BIT_RATE, 32000);
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, AAC_FRAME_SIZE);
        format.setFloat(MediaFormat.KEY_I_FRAME_INTERVAL, .896f);
        int expectedBw = 18000;

        // use full-band noise as test-signal
        int fs = 48000;
        int channels = 2;
        format.setInteger(MediaFormat.KEY_SAMPLE_RATE, fs);
        format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, channels);
        short[] noise = generateNoise(fs, 5, channels, Short.MAX_VALUE / 2.0);

        encodeDecodeXheAacOnePassWithBandwidth(noise,
                "/data/local/tmp/testXheAacOnePassCBR32_VerifyBandwidth.snd", format, expectedBw);
    }
}
