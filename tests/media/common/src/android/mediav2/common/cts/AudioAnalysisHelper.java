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
package android.mediav2.common.cts;

import static org.junit.Assert.assertTrue;

import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.util.Log;

import org.apache.commons.math.transform.FastFourierTransformer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

public class AudioAnalysisHelper {
    private static final String TAG = "AudioAnalysisHelper";
    private static final boolean VERBOSE = false;

    // Some encoders send some initial frames of silence.
    private static final int INITIAL_FRAMES_TO_SKIP = 2;
    private static final long DEQUEUE_TIMEOUT_US = 10000;
    private static final long NO_WAIT = 0;
    private static final long MICROSECONDS_PER_SECOND = 1000000L;
    private static final int BYTES_PER_I16 = 2;
    private static final float I16_TO_FLOAT_SCALE = 32768.0f;
    private static final float MAX_I16_VALUE = 32767.0f;
    private static final float MIN_I16_VALUE = -32768.0f;
    private static final double TWO_PI = 2 * Math.PI;
    private static final float MAX_VALID_FLOAT = 5.0f;
    private static final float MIN_VALID_FLOAT = -5.0f;

    /**
     * Inner class for analyzing audio signals to determine harmonic content and noise. It
     * calculates THD, THD+N, SNR, and peak amplitude.
     *
     * <p>SNR is not particularly useful for perceptual codecs but used as a signal that the signal
     * isn't pure noise.
     */
    public static class HarmonicAnalyzer {
        // A very small number to prevent division by zero or log of zero.
        private static final double VERY_SMALL_NUMBER = 1.0e-12;
        // Energy from bins leaks a bit to nearby bins. 5 nearby bins is a reasonable number.
        private static final int MAX_PEAK_MARGIN = 5;
        private static final int MIN_DB_VALUE = -200;

        /** Data class to hold the results of the harmonic analysis. */
        public static class Result {
            public double totalHarmonicDistortion = 0.0; // THD as a ratio
            public double totalHarmonicDistortionPlusNoise = 0.0; // THD+N as a ratio
            public double signalNoiseRatioDB = 0.0; // SNR in dB
            public double peakAmplitude = 0.0; // Peak amplitude of the signal
            public double[] bins = null; // Magnitudes of FFT bins
        }

        /**
         * Analyzes an audio buffer to calculate THD, THD+N, SNR, and peak amplitude.
         *
         * @param buffer The audio samples as a double array.
         * @param numFrames The number of frames (samples) in the buffer (must be a power of two).
         * @param signalBin The FFT bin index corresponding to the fundamental frequency of the
         *     signal. If 0, only peak amplitude and bins are calculated.
         * @param peakMargin The number of bins around the fundamental and harmonics to include in
         *     power calculations. Clamped between 0 and 5.
         * @return A {@link Result} object containing the analysis metrics.
         */
        public static Result analyze(
                double[] buffer, int numFrames, int signalBin, int peakMargin) {
            assertTrue(
                    "numFrames should be a power of two, not " + numFrames,
                    (numFrames & (numFrames - 1)) == 0);
            // Clamp peakMargin to a reasonable range [0, 5]
            int clampedPeakMargin = Math.max(0, Math.min(peakMargin, MAX_PEAK_MARGIN));
            Result result = new Result();
            // Calculate the peak amplitude from the time-domain signal.
            result.peakAmplitude = calculatePeakAmplitude(buffer, numFrames);
            // Prepare an array for the imaginary part of the FFT (initially all zeros for a real
            // signal).
            double[] real = new double[numFrames];
            double[] imaginary = new double[numFrames];
            var fft = new FastFourierTransformer();
            var bins = fft.transform(buffer);
            for (int i = 0; i < bins.length; i++) {
                real[i] = bins[i].getReal();
                imaginary[i] = bins[i].getImaginary();
            }

            // If a valid signal bin is provided, perform detailed signal analysis.
            if (signalBin != 0) {
                analyzeSignal(result, real, imaginary, numFrames, signalBin, clampedPeakMargin);
            }
            // Calculate the magnitudes of all FFT bins.
            calculateMagnitudes(result, real, imaginary, numFrames);
            return result;
        }

        /**
         * Calculates the peak absolute amplitude from an audio buffer.
         *
         * @param buffer The audio samples.
         * @param numFrames The number of frames in the buffer.
         * @return The peak absolute amplitude.
         */
        private static double calculatePeakAmplitude(double[] buffer, int numFrames) {
            double peak = 0.0;
            for (int i = 0; i < numFrames; i++) {
                peak = Math.max(peak, Math.abs(buffer[i]));
            }
            return peak;
        }

        /**
         * Performs the core signal analysis after FFT. Calculates THD, THD+N, and SNR.
         *
         * @param result The Result object to store analysis metrics.
         * @param real The real part of the FFT output.
         * @param imaginary The imaginary part of the FFT output.
         * @param numFrames The number of FFT frames.
         * @param signalBin The bin index of the fundamental frequency.
         * @param peakMargin The margin around peaks for power calculation.
         */
        private static void analyzeSignal(
                Result result,
                double[] real,
                double[] imaginary,
                int numFrames,
                int signalBin,
                int peakMargin) {
            double signalMagSquared =
                    calculateSignalMagnitudeSquared(real, imaginary, signalBin, peakMargin);
            double totalHarmonicsMagSquared =
                    calculateTotalHarmonicsMagnitudeSquared(
                            real, imaginary, numFrames, signalBin, peakMargin);
            result.totalHarmonicDistortion = Math.sqrt(totalHarmonicsMagSquared / signalMagSquared);
            result.totalHarmonicDistortionPlusNoise =
                    calculateTHDPlusNoise(real, imaginary, numFrames, signalMagSquared);
            double noiseMagSquared =
                    calculateNoiseMagnitudeSquared(
                            real, imaginary, numFrames, signalMagSquared, totalHarmonicsMagSquared);
            result.signalNoiseRatioDB =
                    calculateSignalNoiseRatioDB(signalMagSquared, noiseMagSquared);

            Log.d(TAG, String.format("Signal Bin: %d", signalBin));
            Log.d(TAG, String.format("Signal Power (MagSquared): %g", signalMagSquared));
            Log.d(
                    TAG,
                    String.format("Total Harmonic Distortion: %g", result.totalHarmonicDistortion));
            Log.d(
                    TAG,
                    String.format(
                            "Total Harmonic Distortion: %g",
                            result.totalHarmonicDistortionPlusNoise));
            Log.d(TAG, String.format("Noise Power (MagSquared): %g", noiseMagSquared));
            Log.d(TAG, String.format("SNR (dB): %g", result.signalNoiseRatioDB));
        }

        /**
         * Calculates the squared magnitude of the signal at the fundamental frequency. Includes
         * power from adjacent bins defined by peakMargin.
         *
         * @param real The real part of the FFT output.
         * @param imaginary The imaginary part of the FFT output.
         * @param signalBin The bin index of the fundamental frequency.
         * @param peakMargin The margin around the signal bin.
         * @return The squared magnitude of the signal.
         */
        private static double calculateSignalMagnitudeSquared(
                double[] real, double[] imaginary, int signalBin, int peakMargin) {
            double signalMagSquared = 0.0;
            // Sum power in bins around the fundamental frequency
            for (int i = -peakMargin; i <= peakMargin; i++) {
                int bin = signalBin + i;
                // Ensure bin index is within valid range
                if (bin >= 0 && bin < real.length) {
                    signalMagSquared += magnitudeSquared(real[bin], imaginary[bin]);
                }
            }
            // Ensure a non-zero (or very small) value to prevent division by zero later.
            return Math.max(VERY_SMALL_NUMBER, signalMagSquared);
        }

        /**
         * Calculates the squared magnitude of the harmonics.
         *
         * @param real The real part of the FFT output.
         * @param imaginary The imaginary part of the FFT output.
         * @param numFrames The number of FFT frames.
         * @param signalBin The bin index of the fundamental frequency.
         * @param peakMargin The margin around harmonic bins.
         * @return The squared magnitude of the harmonics.
         */
        private static double calculateTotalHarmonicsMagnitudeSquared(
                double[] real, double[] imaginary, int numFrames, int signalBin, int peakMargin) {
            double totalHarmonicsMagSquared = 0.0;
            // No harmonics if fundamental is DC or not present
            if (signalBin <= 0) {
                return 0;
            }
            // Iterate through harmonics (2nd, 3rd, ... up to Nyquist frequency)
            // Nyquist limit for harmonics: harmonicScaler * signalBin < numFrames / 2
            int limit = numFrames / (2 * signalBin);
            for (int harmonicScaler = 2; harmonicScaler < limit; harmonicScaler++) {
                // Sum power in bins around each harmonic frequency
                for (int i = -peakMargin; i <= peakMargin; i++) {
                    int bin = (signalBin * harmonicScaler) + i;
                    if (bin >= 0 && bin < real.length) { // Check bounds
                        totalHarmonicsMagSquared += magnitudeSquared(real[bin], imaginary[bin]);
                    }
                }
            }
            return totalHarmonicsMagSquared;
        }

        /**
         * Calculates THD plus Noise (THD+N). THD+N = sqrt(sum of powers of all non-fundamental
         * components) / sqrt(power of fundamental).
         *
         * @param real The real part of the FFT output.
         * @param imaginary The imaginary part of the FFT output.
         * @param numFrames The number of FFT frames.
         * @param signalMagSquared The squared magnitude of the fundamental signal.
         * @return The THD+N as a ratio.
         */
        private static double calculateTHDPlusNoise(
                double[] real, double[] imaginary, int numFrames, double signalMagSquared) {
            double totalMagSquared = 0.0;
            // Sum power of all frequency bins (excluding DC component at bin 0) up to Nyquist.
            for (int i = 1; i < numFrames / 2; i++) {
                totalMagSquared += magnitudeSquared(real[i], imaginary[i]);
            }
            // Noise+Harmonics power = Total power - Signal power
            double noiseMagSquared =
                    Math.max(VERY_SMALL_NUMBER, totalMagSquared - signalMagSquared);
            if (signalMagSquared < VERY_SMALL_NUMBER) {
                return Double.POSITIVE_INFINITY;
            }
            return Math.sqrt(noiseMagSquared / signalMagSquared);
        }

        /**
         * Calculates the squared magnitude of the noise. Noise power = Total power (excluding DC) -
         * Signal power - Harmonics power.
         *
         * @param real The real part of the FFT output.
         * @param imaginary The imaginary part of the FFT output.
         * @param numFrames The number of FFT frames.
         * @param signalMagSquared The squared magnitude of the fundamental signal.
         * @param totalHarmonicsMagSquared The squared magnitude of the harmonics.
         * @return The squared magnitude of the noise.
         */
        private static double calculateNoiseMagnitudeSquared(
                double[] real,
                double[] imaginary,
                int numFrames,
                double signalMagSquared,
                double totalHarmonicsMagSquared) {
            double totalMagSquared = 0.0;
            // Sum power of all frequency bins (excluding DC) up to Nyquist.
            // Skip 0th bin because that represents 0 Hz.
            for (int i = 1; i < numFrames / 2; i++) {
                totalMagSquared += magnitudeSquared(real[i], imaginary[i]);
            }
            // Ensure a non-zero (or very small) value.
            return Math.max(
                    VERY_SMALL_NUMBER,
                    totalMagSquared - signalMagSquared - totalHarmonicsMagSquared);
        }

        /**
         * Calculates the Signal-to-Noise Ratio (SNR) in decibels (dB). SNR (dB) = 10 * log10(Signal
         * Power / Noise Power).
         *
         * @param signalMagSquared The squared magnitude of the signal.
         * @param noiseMagSquared The squared magnitude of the noise.
         * @return The SNR in dB.
         */
        private static double calculateSignalNoiseRatioDB(
                double signalMagSquared, double noiseMagSquared) {
            if (noiseMagSquared < VERY_SMALL_NUMBER) {
                return Double.POSITIVE_INFINITY; // Effectively infinite SNR
            }
            if (signalMagSquared < VERY_SMALL_NUMBER) {
                return Double.NEGATIVE_INFINITY; // Effectively -inf SNR if no signal
            }
            double signalNoisePowerRatio = signalMagSquared / noiseMagSquared;
            return powerToDecibels(signalNoisePowerRatio);
        }

        /**
         * Calculates the magnitudes of the first half of the FFT bins (up to Nyquist frequency).
         * Stores the result in the {@link Result} object.
         *
         * @param result The Result object to store the bin magnitudes.
         * @param real The real part of the FFT output.
         * @param imaginary The imaginary part of the FFT output.
         * @param numFrames The number of FFT frames.
         */
        private static void calculateMagnitudes(
                Result result, double[] real, double[] imaginary, int numFrames) {
            result.bins = new double[numFrames / 2]; // Only need up to Nyquist frequency
            for (int i = 0; i < numFrames / 2; i++) {
                result.bins[i] = Math.sqrt(magnitudeSquared(real[i], imaginary[i]));
            }
        }

        /**
         * Converts an amplitude ratio to decibels (dB). dB = 20 * log10(amplitude).
         *
         * @param amplitude The amplitude ratio.
         * @return The equivalent value in dB.
         */
        public static double amplitudeToDecibels(double amplitude) {
            if (amplitude <= VERY_SMALL_NUMBER) {
                return MIN_DB_VALUE; // Effectively negative infinity for log
            }
            return 20.0 * Math.log10(amplitude);
        }

        /**
         * Converts a power ratio to decibels (dB). dB = 10 * log10(powerRatio).
         *
         * @param powerRatio The power ratio.
         * @return The equivalent value in dB.
         */
        public static double powerToDecibels(double powerRatio) {
            if (powerRatio <= VERY_SMALL_NUMBER) {
                return MIN_DB_VALUE; // Effectively negative infinity for log
            }
            return 10.0 * Math.log10(powerRatio);
        }
    }

    /** Analyzes the frequency response of a decoded sine sweep. */
    public static class SweepAnalyzer {
        private static final double HANN_WINDOW_FACTOR = 0.5;
        private static final double ENERGY_THRESHOLD = 1.0e-3;
        private static final double MIN_PEAK_MAGNITUDE_THRESHOLD = 1.0e-9;
        private static final double AMPLITUDE_TO_DB_FACTOR = 20.0;

        /**
         * Analyzes the decoded audio buffer to check for frequency response flatness.
         *
         * @param decodedSamples The decoded audio samples.
         * @param sampleRate The sample rate of the audio.
         * @param startFreq The start frequency of the original sweep.
         * @param endFreq The end frequency of the original sweep.
         * @param fftSize The size of the FFT to use.
         * @return The maximum deviation in dB across the frequency band.
         */
        public static float analyze(
                float[] decodedSamples,
                int sampleRate,
                float startFreq,
                float endFreq,
                int fftSize) {
            double[] doubleSamples = new double[fftSize];
            int numChunks = decodedSamples.length / fftSize;
            if (numChunks == 0) {
                return Float.POSITIVE_INFINITY; // Not enough data
            }

            // Create a Hann window
            double[] window = new double[fftSize];
            for (int i = 0; i < fftSize; i++) {
                window[i] = HANN_WINDOW_FACTOR * (1 - Math.cos(TWO_PI * i / (fftSize - 1)));
            }

            double maxPeakMagnitude = 0.0;
            double minPeakMagnitude = Double.MAX_VALUE;
            boolean firstPeakFound = false;

            int startBin = (int) (startFreq * fftSize / sampleRate);
            int endBin = (int) (endFreq * fftSize / sampleRate);

            for (int chunk = 0; chunk < numChunks; chunk++) {
                // Apply window to the chunk
                for (int i = 0; i < fftSize; i++) {
                    doubleSamples[i] = decodedSamples[chunk * fftSize + i] * window[i];
                }

                var fft = new FastFourierTransformer();
                var bins = fft.transform(doubleSamples);

                // Find the peak magnitude in the current chunk within the sweep's frequency range
                double chunkMaxMagnitude = 0.0;
                for (int i = startBin; i < Math.min(endBin, fftSize / 2); i++) {
                    double real = bins[i].getReal();
                    double imaginary = bins[i].getImaginary();
                    double magnitude = Math.sqrt(magnitudeSquared(real, imaginary));
                    chunkMaxMagnitude = Math.max(chunkMaxMagnitude, magnitude);
                }

                // Only consider chunks with significant energy to avoid silence/padding
                if (chunkMaxMagnitude > ENERGY_THRESHOLD) { // Heuristic threshold
                    if (!firstPeakFound) {
                        maxPeakMagnitude = chunkMaxMagnitude;
                        minPeakMagnitude = chunkMaxMagnitude;
                        firstPeakFound = true;
                    } else {
                        maxPeakMagnitude = Math.max(maxPeakMagnitude, chunkMaxMagnitude);
                        minPeakMagnitude = Math.min(minPeakMagnitude, chunkMaxMagnitude);
                    }
                }
            }

            if (!firstPeakFound || minPeakMagnitude <= MIN_PEAK_MAGNITUDE_THRESHOLD) {
                return Float.POSITIVE_INFINITY;
            }

            return (float)
                    (AMPLITUDE_TO_DB_FACTOR * Math.log10(maxPeakMagnitude / minPeakMagnitude));
        }
    }

    /**
     * Calculates the squared magnitude of a complex number. Magnitude^2 = real^2 + imaginary^2.
     *
     * @param real The real part.
     * @param imaginary The imaginary part.
     * @return The squared magnitude.
     */
    private static double magnitudeSquared(double real, double imaginary) {
        return real * real + imaginary * imaginary;
    }

    /**
     * Converts a segment of a float array (representing PCM float audio) to a byte array. Each
     * float is converted to 4 bytes in little-endian order.
     *
     * @param input The source float array.
     * @param offset The starting offset in the input array.
     * @param length The number of floats to convert.
     * @return A byte array containing the converted audio data.
     */
    public static byte[] floatArrayToByteArray(float[] input, int offset, int length) {
        ByteBuffer buffer = ByteBuffer.allocate(length * Float.BYTES);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < length; i++) {
            buffer.putFloat(input[offset + i]);
        }
        return buffer.array();
    }

    /**
     * Converts a segment of a float array (where values are typically -1.0 to 1.0) to a byte array
     * representing 16-bit PCM audio. Each float is scaled and converted to a short (2 bytes) in
     * little-endian order.
     */
    public static byte[] floatArrayToI16ByteArray(float[] inputData, int offset, int length) {
        ByteBuffer buffer = ByteBuffer.allocate(length * BYTES_PER_I16);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < length; i++) {
            float floatVal = inputData[offset + i];
            assertTrue(
                    "Invalid audio sample (NaN/Inf) detected at index " + (offset + i),
                    Float.isFinite(floatVal));
            assertTrue(
                    "Invalid audio sample " + floatVal + " too large at index " + (offset + i),
                    floatVal < MAX_VALID_FLOAT);
            assertTrue(
                    "Invalid audio sample " + floatVal + " too small at index " + (offset + i),
                    floatVal > MIN_VALID_FLOAT);
            // Clamp values to be between -1.0f and 1.0f. Check for overflow after conversion
            // since the floats can be extremely large and it makes sure the short value work
            // as expected.
            float scaledVal = floatVal * MAX_I16_VALUE;
            short shortVal;
            if (scaledVal > MAX_I16_VALUE) {
                shortVal = (short) MAX_I16_VALUE;
            } else if (scaledVal < MIN_I16_VALUE) {
                shortVal = (short) MIN_I16_VALUE;
            } else {
                shortVal = (short) Math.round(scaledVal);
            }
            buffer.putShort(shortVal);
        }
        return buffer.array();
    }

    /**
     * Encodes and then decodes raw audio data using the specified codec and parameters.
     *
     * @param activeEncCfg The encoder configuration parameters.
     * @param mediaType The MIME type of the audio data.
     * @param codecName The name of the codec to use for encoding.
     * @param inputData The raw PCM audio data as a byte array.
     * @return The decoded audio data as a float array.
     */
    public static List<Float> encodeAndDecode(
            EncoderConfigParams activeEncCfg,
            String mediaType,
            String codecName,
            byte[] inputData) {
        // --- Encoder Setup ---
        MediaCodec encoder;
        try {
            encoder = MediaCodec.createByCodecName(codecName);
        } catch (IOException e) {
            throw new AssertionError("Failed to create encoder: " + codecName, e);
        }
        MediaFormat encoderFormat =
                MediaFormat.createAudioFormat(
                        mediaType, activeEncCfg.mSampleRate, activeEncCfg.mChannelCount);
        if (mediaType.equals(MediaFormat.MIMETYPE_AUDIO_FLAC)) {
            encoderFormat.setInteger(MediaFormat.KEY_FLAC_COMPRESSION_LEVEL, activeEncCfg.mBitRate);
        } else {
            encoderFormat.setInteger(MediaFormat.KEY_BIT_RATE, activeEncCfg.mBitRate);
        }
        if (mediaType.equals(MediaFormat.MIMETYPE_AUDIO_AAC) && activeEncCfg.mProfile != 0) {
            encoderFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, activeEncCfg.mProfile);
        }
        encoderFormat.setInteger(MediaFormat.KEY_PCM_ENCODING, activeEncCfg.mPcmEncoding);

        if (VERBOSE) {
            Log.d(TAG, "Encoder format: " + encoderFormat);
        }
        encoder.configure(
                encoderFormat,
                null /* Surface */,
                null /* MediaCrypto */,
                MediaCodec.CONFIGURE_FLAG_ENCODE);

        encoder.start();

        // --- Encoding Loop ---
        ArrayList<byte[]> encodedBuffers = new ArrayList<>();
        ArrayList<MediaCodec.BufferInfo> bufferInfoList = new ArrayList<>();
        MediaFormat outFormat = encoder.getOutputFormat();
        boolean encoderDone = false;
        int inputBufferOffset = 0;
        long pts = 0;

        assertTrue(
                "encoder pcmEncoding " + activeEncCfg.mPcmEncoding + " invalid",
                activeEncCfg.mPcmEncoding == AudioFormat.ENCODING_PCM_FLOAT
                        || activeEncCfg.mPcmEncoding == AudioFormat.ENCODING_PCM_16BIT);

        int bytesPerSample =
                (activeEncCfg.mPcmEncoding == AudioFormat.ENCODING_PCM_FLOAT
                                ? Float.BYTES
                                : BYTES_PER_I16)
                        * activeEncCfg.mChannelCount;

        while (!encoderDone) {
            int inputBufferIndex = encoder.dequeueInputBuffer(DEQUEUE_TIMEOUT_US);
            if (inputBufferIndex >= 0) {
                ByteBuffer inputBuffer = encoder.getInputBuffer(inputBufferIndex);
                inputBuffer.clear();
                int bytesToCopy =
                        Math.min(inputBuffer.remaining(), inputData.length - inputBufferOffset);
                if (bytesToCopy > 0) {
                    inputBuffer.put(inputData, inputBufferOffset, bytesToCopy);
                    inputBufferOffset += bytesToCopy;
                }

                pts =
                        MICROSECONDS_PER_SECOND
                                * (inputBufferOffset / bytesPerSample)
                                / activeEncCfg.mSampleRate;

                if (bytesToCopy <= 0) {
                    encoder.queueInputBuffer(
                            inputBufferIndex, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                } else {
                    encoder.queueInputBuffer(inputBufferIndex, 0, bytesToCopy, pts, 0);
                }
            }

            MediaCodec.BufferInfo encoderBufferInfo = new MediaCodec.BufferInfo();
            int outputBufferIndex = encoder.dequeueOutputBuffer(encoderBufferInfo, NO_WAIT);
            if (outputBufferIndex >= 0) {
                boolean isConfig =
                        (encoderBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0;
                if (encoderBufferInfo.size > 0) {
                    ByteBuffer encodedBuffer = encoder.getOutputBuffer(outputBufferIndex);
                    if (!isConfig) {
                        byte[] frame = new byte[encoderBufferInfo.size];
                        encodedBuffer.get(frame);
                        encodedBuffers.add(frame);
                        bufferInfoList.add(encoderBufferInfo);
                    }
                }
                encoder.releaseOutputBuffer(outputBufferIndex, false);
                if ((encoderBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    encoderDone = true;
                }
            } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                outFormat = encoder.getOutputFormat();
            }
        }
        encoder.stop();
        encoder.release();

        // --- Decoder Setup ---
        ArrayList<String> listOfDecoders =
                CodecTestBase.selectCodecs(
                        mediaType, new ArrayList<>(List.of(outFormat)), null, false);
        assertTrue("No suitable decoder found for " + mediaType, !listOfDecoders.isEmpty());

        MediaCodec decoder;
        try {
            decoder = MediaCodec.createByCodecName(listOfDecoders.get(0));
        } catch (IOException e) {
            throw new AssertionError("Failed to create decoder: " + listOfDecoders.get(0), e);
        }
        if (VERBOSE) {
            Log.d(TAG, "Decoder format: " + outFormat);
        }
        decoder.configure(outFormat, null /* Surface */, null /* MediaCrypto */, 0 /* flags */);
        decoder.start();

        // --- Decoding Loop ---
        ArrayList<float[]> decodedFrames = new ArrayList<>();
        boolean decoderDone = false;
        int encodedBufferOffset = 0;

        while (!decoderDone) {
            int inputBufferIndex = decoder.dequeueInputBuffer(DEQUEUE_TIMEOUT_US);
            if (inputBufferIndex >= 0) {
                ByteBuffer inputBuffer = decoder.getInputBuffer(inputBufferIndex);
                inputBuffer.clear();

                if (encodedBufferOffset < bufferInfoList.size()) {
                    MediaCodec.BufferInfo info = bufferInfoList.get(encodedBufferOffset);
                    byte[] encodedFrame = encodedBuffers.get(encodedBufferOffset);
                    inputBuffer.put(encodedFrame);
                    decoder.queueInputBuffer(
                            inputBufferIndex, 0, info.size, info.presentationTimeUs, info.flags);
                    encodedBufferOffset++;
                } else {
                    decoder.queueInputBuffer(
                            inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                }
            }

            MediaCodec.BufferInfo decoderBufferInfo = new MediaCodec.BufferInfo();
            int outputBufferIndex = decoder.dequeueOutputBuffer(decoderBufferInfo, NO_WAIT);
            if (outputBufferIndex >= 0) {
                if (decoderBufferInfo.size > 0) {
                    ByteBuffer decoderOutputBuffer = decoder.getOutputBuffer(outputBufferIndex);
                    int pcmEncoding =
                            decoder.getOutputFormat()
                                    .getInteger(
                                            MediaFormat.KEY_PCM_ENCODING,
                                            AudioFormat.ENCODING_PCM_16BIT);
                    float[] decodedSamples;
                    if (pcmEncoding == AudioFormat.ENCODING_PCM_16BIT) {
                        decodedSamples = new float[decoderBufferInfo.size / BYTES_PER_I16];
                        decoderOutputBuffer.order(ByteOrder.LITTLE_ENDIAN);
                        for (int i = 0; i < decodedSamples.length; i++) {
                            decodedSamples[i] = decoderOutputBuffer.getShort() / I16_TO_FLOAT_SCALE;
                        }
                    } else if (pcmEncoding == AudioFormat.ENCODING_PCM_FLOAT) {
                        decodedSamples = new float[decoderBufferInfo.size / Float.BYTES];
                        decoderOutputBuffer
                                .order(ByteOrder.LITTLE_ENDIAN)
                                .asFloatBuffer()
                                .get(decodedSamples);
                    } else {
                        assertTrue("decoder pcmEncoding " + pcmEncoding + " invalid", false);
                        decodedSamples = new float[0];
                    }
                    decodedFrames.add(decodedSamples);
                }
                decoder.releaseOutputBuffer(outputBufferIndex, false);
                if ((decoderBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    decoderDone = true;
                }
            }
        }

        decoder.stop();
        decoder.release();

        assertTrue(
                "not enough decoded frames " + decodedFrames.size(),
                decodedFrames.size() > INITIAL_FRAMES_TO_SKIP);

        // --- Copy decoded samples to a single array ---
        List<Float> effectiveFrames = new ArrayList<>();
        for (int frame = INITIAL_FRAMES_TO_SKIP; frame < decodedFrames.size(); frame++) {
            for (float sample : decodedFrames.get(frame)) {
                effectiveFrames.add(sample);
            }
        }
        return effectiveFrames;
    }

    /**
     * Creates an {@link EncoderConfigParams} object for audio encoding based on the provided
     * parameters.
     *
     * @param mediaType The MIME type of the audio format (e.g., {@link
     *     MediaFormat#MIMETYPE_AUDIO_AAC}).
     * @param qualityPreset For FLAC, this is the compression level. For other formats, it's the
     *     bitrate.
     * @param sampleRate The sample rate of the audio in Hz.
     * @param channelCount The number of audio channels.
     * @param pcmEncoding The PCM encoding (e.g., {@link MediaFormat#MIMETYPE_AUDIO_RAW}).
     * @param profile The codec profile (e.g., {@link MediaFormat#MIMETYPE_AUDIO_AAC}).
     * @return An {@link EncoderConfigParams} object configured with the specified audio encoding
     *     parameters.
     */
    public static EncoderConfigParams getAudioEncoderCfgParams(
            String mediaType,
            int qualityPreset,
            int sampleRate,
            int channelCount,
            int pcmEncoding,
            int profile) {
        EncoderConfigParams.Builder foreman =
                new EncoderConfigParams.Builder(mediaType)
                        .setSampleRate(sampleRate)
                        .setChannelCount(channelCount)
                        .setPcmEncoding(pcmEncoding)
                        .setProfile(profile);
        if (mediaType.equals(MediaFormat.MIMETYPE_AUDIO_FLAC)) {
            foreman.setCompressionLevel(qualityPreset);
        } else {
            foreman.setBitRate(qualityPreset);
        }
        return foreman.build();
    }

    /**
     * Calculates the nearest FFT bin index for a given frequency.
     *
     * @param sampleRate The sample rate of the audio.
     * @param sineFrequency The frequency to find the bin for.
     * @param fftSize The size of the FFT.
     * @return The nearest bin index.
     */
    public static int calculateNearestBin(int sampleRate, float sineFrequency, int fftSize) {
        return (int) Math.round((double) fftSize * sineFrequency / sampleRate);
    }

    /**
     * Calculates the frequency corresponding to a given FFT bin index.
     *
     * @param sampleRate The sample rate of the audio.
     * @param bin The FFT bin index.
     * @param fftSize The size of the FFT.
     * @return The frequency of the bin.
     */
    public static float calculateBinFrequency(int sampleRate, int bin, int fftSize) {
        return ((float) sampleRate * bin) / (float) fftSize;
    }

    /**
     * Generates a sine wave.
     *
     * @param sampleRate The sample rate of the audio.
     * @param frequency The frequency of the sine wave.
     * @param amplitude The amplitude of the sine wave.
     * @param numSamples The number of samples to generate.
     * @return An array of floats representing the sine wave.
     */
    public static float[] generateSineWave(
            int sampleRate, float frequency, float amplitude, int numSamples) {
        float[] sineWave = new float[numSamples];
        double timeIncrement = 1.0 / sampleRate;
        double currentTime = 0.0;
        for (int i = 0; i < numSamples; i++) {
            sineWave[i] = (float) (amplitude * Math.sin(TWO_PI * frequency * currentTime));
            currentTime += timeIncrement;
        }
        return sineWave;
    }

    /**
     * Generates a logarithmic sine sweep.
     *
     * @param sampleRate The sample rate of the audio.
     * @param startFreq The starting frequency of the sweep.
     * @param endFreq The ending frequency of the sweep.
     * @param amplitude The amplitude of the sweep.
     * @param numSamples The total number of samples to generate for all channels.
     * @param channelCount The number of channels.
     * @return An array of floats representing the sine sweep.
     */
    public static float[] generateSineSweep(
            int sampleRate,
            float startFreq,
            float endFreq,
            float amplitude,
            int numSamples,
            int channelCount) {
        float[] sweep = new float[numSamples];
        // T is the duration of the sweep in seconds.
        double sweepDurationSeconds = (double) numSamples / sampleRate / channelCount;
        double w1 = TWO_PI * startFreq;
        double w2 = TWO_PI * endFreq;
        for (int i = 0; i < numSamples; i += channelCount) {
            double t = (double) i / channelCount / sampleRate;
            double logRatio = Math.log(w2 / w1);
            double exponent = t / sweepDurationSeconds * logRatio;
            double phase = w1 * sweepDurationSeconds / logRatio * (Math.exp(exponent) - 1.0);
            for (int j = 0; j < channelCount; j++) {
                sweep[i + j] = (float) (amplitude * Math.sin(phase));
            }
        }
        return sweep;
    }
}
