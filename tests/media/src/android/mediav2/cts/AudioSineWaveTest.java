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
package android.mediav2.cts;

import static android.media.MediaCodecInfo.CodecProfileLevel.AACObjectELD;
import static android.media.MediaCodecInfo.CodecProfileLevel.AACObjectHE;
import static android.media.MediaCodecInfo.CodecProfileLevel.AACObjectLC;

import static org.junit.Assert.fail;

import android.media.AudioFormat;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.mediav2.common.cts.AudioAnalysisHelper;
import android.mediav2.common.cts.CodecEncoderTestBase;
import android.mediav2.common.cts.EncoderConfigParams;
import android.util.Log;

import androidx.test.filters.SmallTest;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.CddTest;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

@RunWith(Parameterized.class)
public class AudioSineWaveTest extends CodecEncoderTestBase {
    private static final String TAG = "AudioSineWaveTest";
    private static final boolean VERBOSE = true;

    // --- Sine Wave Generation Parameters ---
    private static final float FIRST_TARGET_SINE_FREQUENCY = 1000.0f; // 1 kHz
    private static final float TARGET_SINE_FREQUENCY_SPEECH = 400.0f;
    private static final float TARGET_SINE_FREQUENCY_MULTIPLIER = 1.4f;
    private static final float SINE_AMPLITUDE = 0.8f;
    private static final int FFT_SIZE_HIGH_RESOLUTION = 8192;
    private static final int FFT_SIZE_LOW_RESOLUTION = 1024;
    private static final int HARMONIC_ANALYZER_PEAK_MARGIN = 1;
    // Encode 10 FFTs worth of data.
    private static final int TARGET_NUMBER_OF_FFT_FOR_SAMPLES = 10;

    // --- Audio Quality Thresholds ---
    private final float mMaxThd;
    private final float mMaxThdN;
    private final float mMinSnr;
    private final float mMaxAmplitudeDifference;

    private static class AudioTestAttrib {
        final int[] mBitRates;
        final int[] mSampleRates;
        final int[] mChannelCounts;
        final int mProfile;
        final float mMaxThd;
        final float mMaxThdN;
        final float mMinSnr;
        final float mMaxAmplitudeDifference;

        AudioTestAttrib(
                int[] bitRates,
                int[] sampleRates,
                int[] channelCounts,
                int profile,
                float maxThd,
                float maxThdN,
                float minSnr,
                float maxAmplitudeDifference) {
            this.mBitRates = bitRates;
            this.mSampleRates = sampleRates;
            this.mChannelCounts = channelCounts;
            this.mProfile = profile;
            this.mMaxThd = maxThd;
            this.mMaxThdN = maxThdN;
            this.mMinSnr = minSnr;
            this.mMaxAmplitudeDifference = maxAmplitudeDifference;
        }
    }

    public AudioSineWaveTest(
            String encoder,
            String mediaType,
            EncoderConfigParams encCfgParams,
            float maxThd,
            float maxThdN,
            float minSnr,
            float maxAmplitudeDifference,
            String testLabel,
            String allTestParams) {
        super(encoder, mediaType, new EncoderConfigParams[] {encCfgParams}, allTestParams);
        mMaxThd = maxThd;
        mMaxThdN = maxThdN;
        mMinSnr = minSnr;
        mMaxAmplitudeDifference = maxAmplitudeDifference;
    }

    private static List<Object[]> flattenParams(List<Object[]> params) {
        List<Object[]> argsList = new ArrayList<>();
        for (Object[] param : params) {
            String encoderName = (String) param[0];
            String mediaType = (String) param[1];
            AudioTestAttrib testAttrib = (AudioTestAttrib) param[2];

            if (testAttrib.mProfile != -1) {
                MediaCodecInfo.CodecCapabilities caps =
                        getCodecCapabilities(encoderName, mediaType);
                boolean profileSupported = false;
                if (caps != null) {
                    for (MediaCodecInfo.CodecProfileLevel pl : caps.profileLevels) {
                        if (pl.profile == testAttrib.mProfile) {
                            profileSupported = true;
                            break;
                        }
                    }
                }
                if (!profileSupported) continue;
            }

            for (int bitRate : testAttrib.mBitRates) {
                for (int sampleRate : testAttrib.mSampleRates) {
                    for (int channelCount : testAttrib.mChannelCounts) {
                        // TODO(b/258222051): Re-enable single channel test for AAC-HE
                        if (testAttrib.mProfile == AACObjectHE && channelCount == 1) continue;

                        // Only FLAC supports float PCM encoding
                        int pcmEncoding =
                                mediaType.equals(MediaFormat.MIMETYPE_AUDIO_FLAC)
                                        ? AudioFormat.ENCODING_PCM_FLOAT
                                        : AudioFormat.ENCODING_PCM_16BIT;
                        EncoderConfigParams cfg =
                                AudioAnalysisHelper.getAudioEncoderCfgParams(
                                        mediaType,
                                        bitRate,
                                        sampleRate,
                                        channelCount,
                                        pcmEncoding,
                                        testAttrib.mProfile);

                        String testLabel;
                        if (mediaType.equals(MediaFormat.MIMETYPE_AUDIO_FLAC)) {
                            testLabel =
                                    String.format(
                                            "%s_clvl%d_%dkhz_%dch",
                                            mediaType, bitRate, sampleRate / 1000, channelCount);
                        } else {
                            testLabel =
                                    String.format(
                                            "%s_%dkbps_%dkhz_%dch_p%d",
                                            mediaType,
                                            bitRate / 1000,
                                            sampleRate / 1000,
                                            channelCount,
                                            testAttrib.mProfile);
                        }
                        argsList.add(
                                new Object[] {
                                    encoderName,
                                    mediaType,
                                    cfg,
                                    testAttrib.mMaxThd,
                                    testAttrib.mMaxThdN,
                                    testAttrib.mMinSnr,
                                    testAttrib.mMaxAmplitudeDifference,
                                    testLabel,
                                    cfg.toString()
                                });
                    }
                }
            }
        }
        return argsList;
    }

    @Parameterized.Parameters(name = "{index}_{0}_{7}")
    public static Collection<Object[]> input() {
        final boolean isEncoder = true;
        final boolean needAudio = true;
        final boolean needVideo = false;
        final List<Object[]> defArgsList =
                new ArrayList<>(
                        Arrays.asList(
                                new Object[][] {
                                    {
                                        MediaFormat.MIMETYPE_AUDIO_AAC,
                                        new AudioTestAttrib(
                                                new int[] {192000},
                                                new int[] {44100, 48000},
                                                new int[] {1, 2},
                                                AACObjectLC,
                                                0.01f,
                                                0.50f,
                                                15.0f,
                                                0.15f)
                                    },
                                    {
                                        MediaFormat.MIMETYPE_AUDIO_AAC,
                                        new AudioTestAttrib(
                                                new int[] {64000},
                                                new int[] {44100, 48000},
                                                new int[] {2},
                                                AACObjectHE,
                                                0.6f,
                                                0.7f,
                                                8.0f,
                                                0.25f)
                                    },
                                    {
                                        MediaFormat.MIMETYPE_AUDIO_AAC,
                                        new AudioTestAttrib(
                                                new int[] {128000},
                                                new int[] {44100, 48000},
                                                new int[] {1, 2},
                                                AACObjectELD,
                                                0.1f,
                                                0.6f,
                                                10.0f,
                                                0.20f)
                                    },
                                    {
                                        MediaFormat.MIMETYPE_AUDIO_OPUS,
                                        new AudioTestAttrib(
                                                new int[] {64000, 128000},
                                                new int[] {48000},
                                                new int[] {1, 2},
                                                -1,
                                                0.1f,
                                                0.6f,
                                                8.0f,
                                                0.25f)
                                    },
                                    {
                                        MediaFormat.MIMETYPE_AUDIO_AMR_NB,
                                        new AudioTestAttrib(
                                                new int[] {12200},
                                                new int[] {8000},
                                                new int[] {1},
                                                -1,
                                                0.1f,
                                                7.0f,
                                                3.0f,
                                                0.30f)
                                    },
                                    {
                                        MediaFormat.MIMETYPE_AUDIO_AMR_WB,
                                        new AudioTestAttrib(
                                                new int[] {23850},
                                                new int[] {16000},
                                                new int[] {1},
                                                -1,
                                                0.6f,
                                                4.0f,
                                                5.0f,
                                                0.25f)
                                    },
                                    {
                                        MediaFormat.MIMETYPE_AUDIO_FLAC,
                                        new AudioTestAttrib(
                                                new int[] {0, 5, 8},
                                                new int[] {44100, 48000},
                                                new int[] {1, 2},
                                                -1,
                                                0.001f,
                                                0.10f,
                                                40.0f,
                                                0.05f)
                                    },
                                }));
        List<Object[]> argsList =
                prepareParamList(defArgsList, isEncoder, needAudio, needVideo, false);
        return flattenParams(argsList);
    }

    private void encodeDecodeAndValidate() throws IOException, InterruptedException {
        final int fftSize;
        final int numSamples;
        float currentTargetFrequency;

        if (mMediaType.equals(MediaFormat.MIMETYPE_AUDIO_AMR_NB)
                || mMediaType.equals(MediaFormat.MIMETYPE_AUDIO_AMR_WB)) {
            fftSize = FFT_SIZE_LOW_RESOLUTION;
            currentTargetFrequency = TARGET_SINE_FREQUENCY_SPEECH;
        } else {
            fftSize = FFT_SIZE_HIGH_RESOLUTION;
            currentTargetFrequency = FIRST_TARGET_SINE_FREQUENCY;
        }
        numSamples = fftSize * TARGET_NUMBER_OF_FFT_FOR_SAMPLES;

        // Generate input data
        float[] audioToProcess = new float[numSamples * mActiveEncCfg.mChannelCount];
        float[] sineFrequencies = new float[mActiveEncCfg.mChannelCount];

        for (int channel = 0; channel < mActiveEncCfg.mChannelCount; channel++) {
            if (channel > 0) {
                currentTargetFrequency *= TARGET_SINE_FREQUENCY_MULTIPLIER;
            }
            int generationBin =
                    AudioAnalysisHelper.calculateNearestBin(
                            mActiveEncCfg.mSampleRate, currentTargetFrequency, fftSize);
            sineFrequencies[channel] =
                    AudioAnalysisHelper.calculateBinFrequency(
                            mActiveEncCfg.mSampleRate, generationBin, fftSize);

            float[] generatedSineWave =
                    AudioAnalysisHelper.generateSineWave(
                            mActiveEncCfg.mSampleRate,
                            sineFrequencies[channel],
                            SINE_AMPLITUDE,
                            numSamples);
            for (int sample = 0; sample < numSamples; sample++) {
                audioToProcess[sample * mActiveEncCfg.mChannelCount + channel] =
                        generatedSineWave[sample];
            }
        }

        byte[] inputData;
        if (mActiveEncCfg.mPcmEncoding == AudioFormat.ENCODING_PCM_FLOAT) {
            inputData =
                    AudioAnalysisHelper.floatArrayToByteArray(
                            audioToProcess, 0, audioToProcess.length);
        } else {
            inputData =
                    AudioAnalysisHelper.floatArrayToI16ByteArray(
                            audioToProcess, 0, audioToProcess.length);
        }

        List<Float> decodedSamples =
                AudioAnalysisHelper.encodeAndDecode(
                        mActiveEncCfg, mMediaType, mCodecName, inputData);

        if (VERBOSE) {
            StringBuilder sb = new StringBuilder();
            int samplesToLog = Math.min(128, decodedSamples.size());
            sb.append("Decoded samples (first ").append(samplesToLog).append("): ");
            for (int i = 0; i < samplesToLog; i++) {
                sb.append(String.format("%.4f ", decodedSamples.get(i)));
            }
            Log.d(TAG, sb.toString());
        }

        int samplesPerChannel = decodedSamples.size() / mActiveEncCfg.mChannelCount;
        for (int i = 0; i < mActiveEncCfg.mChannelCount; i++) {
            float[] channelSamples = new float[samplesPerChannel];
            for (int j = 0; j < samplesPerChannel; j++) {
                channelSamples[j] = decodedSamples.get(j * mActiveEncCfg.mChannelCount + i);
            }

            int analysisBin =
                    AudioAnalysisHelper.calculateNearestBin(
                            mActiveEncCfg.mSampleRate, sineFrequencies[i], fftSize);

            // Call analyzeAudio for each FFT window (constant times)
            for (int window = 0; window < TARGET_NUMBER_OF_FFT_FOR_SAMPLES; window++) {
                int startOffset = window * fftSize;

                // Ensure we don't exceed the actual decoded sample count
                if (startOffset + fftSize <= samplesPerChannel) {
                    float[] fftWindowSamples = new float[fftSize];
                    System.arraycopy(channelSamples, startOffset, fftWindowSamples, 0, fftSize);
                    analyzeAudio(fftWindowSamples, analysisBin, fftSize);
                }
            }
        }
    }

    private void analyzeAudio(
            float[] audioSamplesForOneChannel, int fundamentalBin, int numSamples) {
        // Prepare for analysis
        float[] audioToAnalyze = new float[numSamples];
        int numOutputSamples = audioSamplesForOneChannel.length;
        int samplesToCopy = Math.min(numOutputSamples, numSamples);
        int sourceOffset = numOutputSamples - samplesToCopy;
        System.arraycopy(audioSamplesForOneChannel, sourceOffset, audioToAnalyze, 0, samplesToCopy);

        double[] doubleBuffer = new double[audioToAnalyze.length];
        for (int i = 0; i < audioToAnalyze.length; i++) {
            doubleBuffer[i] = audioToAnalyze[i];
        }

        AudioAnalysisHelper.HarmonicAnalyzer.Result result =
                AudioAnalysisHelper.HarmonicAnalyzer.analyze(
                        doubleBuffer, numSamples, fundamentalBin, HARMONIC_ANALYZER_PEAK_MARGIN);

        if (VERBOSE) {
            Log.d(
                    TAG,
                    "Analysis: THD="
                            + result.totalHarmonicDistortion
                            + ", THD+N="
                            + result.totalHarmonicDistortionPlusNoise
                            + ", SNR="
                            + result.signalNoiseRatioDB
                            + ", Amp="
                            + result.peakAmplitude);
        }

        String failureDetails =
                String.format(" (Encoder: %s, Params: %s)", mCodecName, mActiveEncCfg.toString());

        if (result.totalHarmonicDistortion >= mMaxThd) {
            fail(
                    String.format(
                            "THD too high. Got: %f, Limit: %f%s",
                            result.totalHarmonicDistortion, mMaxThd, failureDetails));
        }
        if (result.totalHarmonicDistortionPlusNoise >= mMaxThdN) {
            fail(
                    String.format(
                            "THD+N too high. Got: %f, Limit: %f%s",
                            result.totalHarmonicDistortionPlusNoise, mMaxThdN, failureDetails));
        }
        if (result.signalNoiseRatioDB <= mMinSnr) {
            fail(
                    String.format(
                            "SNR too low. Got: %f, Limit: %f%s",
                            result.signalNoiseRatioDB, mMinSnr, failureDetails));
        }
        double amplitudeDifference = Math.abs(result.peakAmplitude - SINE_AMPLITUDE);
        if (amplitudeDifference >= mMaxAmplitudeDifference) {
            fail(
                    String.format(
                            "Amplitude difference too high. Got: %f, Limit: %f%s",
                            amplitudeDifference, mMaxAmplitudeDifference, failureDetails));
        }
    }

    @ApiTest(
            apis = {
                "android.media.AudioFormat#ENCODING_PCM_16BIT",
                "android.media.AudioFormat#ENCODING_PCM_FLOAT",
                "android.media.MediaFormat#KEY_MAX_INPUT_SIZE"
            })
    @CddTest(
            requirements = {
                "2.2.2/5.1/H-0-1",
                "2.2.2/5.1/H-0-2",
                "2.2.2/5.1/H-0-3",
                "2.2.2/5.1/H-0-4",
                "2.2.2/5.1/H-0-5",
                "2.3.2/5.1/T-0-1",
                "2.3.2/5.1/T-0-2",
                "2.3.2/5.1/T-0-3",
                "2.5.2/5.1/A-0-1",
                "2.5.2/5.1/A-0-2",
                "2.5.2/5.1/A-0-3",
                "5.1.1/C-1-2",
                "5.1.1/C-1-3",
                "5.1.1/C-3-1"
            })
    @SmallTest
    @Test(timeout = PER_TEST_TIMEOUT_SMALL_TEST_MS)
    public void testSineWaveEncodeDecode() throws IOException, InterruptedException {
        mActiveEncCfg = mEncCfgParams[0];
        encodeDecodeAndValidate();
    }
}
