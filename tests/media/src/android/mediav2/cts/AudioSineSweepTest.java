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
public class AudioSineSweepTest extends CodecEncoderTestBase {
    private static final String TAG = "AudioSineSweepTest";
    private static final boolean VERBOSE = true;

    // --- Sine Sweep Parameters ---
    private static final float SWEEP_AMPLITUDE = 0.8f;
    private static final int SWEEP_DURATION_SAMPLES = 48000; // 1 second of audio at 48kHz
    private static final int FFT_SIZE = 2048;

    private final float mStartFreq;
    private final float mEndFreq;
    private final float mMaxDeviationDb;

    private static class AudioSweepTestAttrib {
        final int[] mBitRates;
        final int[] mSampleRates;
        final int[] mChannelCounts;
        final int mProfile;
        final float mStartFreq;
        final float mEndFreq;
        final float mMaxDeviationDb;

        AudioSweepTestAttrib(
                int[] bitRates,
                int[] sampleRates,
                int[] channelCounts,
                int profile,
                float startFreq,
                float endFreq,
                float maxDeviationDb) {
            this.mBitRates = bitRates;
            this.mSampleRates = sampleRates;
            this.mChannelCounts = channelCounts;
            this.mProfile = profile;
            this.mStartFreq = startFreq;
            this.mEndFreq = endFreq;
            this.mMaxDeviationDb = maxDeviationDb;
        }
    }

    public AudioSineSweepTest(
            String encoder,
            String mediaType,
            EncoderConfigParams encCfgParams,
            float startFreq,
            float endFreq,
            float maxDeviationDb,
            String testLabel,
            String allTestParams) {
        super(encoder, mediaType, new EncoderConfigParams[] {encCfgParams}, allTestParams);
        mStartFreq = startFreq;
        mEndFreq = endFreq;
        mMaxDeviationDb = maxDeviationDb;
    }

    private static List<Object[]> flattenParams(List<Object[]> params) {
        List<Object[]> argsList = new ArrayList<>();
        for (Object[] param : params) {
            String encoderName = (String) param[0];
            String mediaType = (String) param[1];
            AudioSweepTestAttrib testAttrib = (AudioSweepTestAttrib) param[2];

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
                                            "%s_clvl%d_%dkhz_%dch_sweep",
                                            mediaType, bitRate, sampleRate / 1000, channelCount);
                        } else {
                            testLabel =
                                    String.format(
                                            "%s_%dkbps_%dkhz_%dch_p%d_sweep",
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
                                    testAttrib.mStartFreq,
                                    testAttrib.mEndFreq,
                                    testAttrib.mMaxDeviationDb,
                                    testLabel,
                                    cfg.toString()
                                });
                    }
                }
            }
        }
        return argsList;
    }

    @Parameterized.Parameters(name = "{index}_{0}_{6}")
    public static Collection<Object[]> input() {
        final boolean isEncoder = true;
        final boolean needAudio = true;
        final boolean needVideo = false;
        final List<Object[]> defArgsList =
                new ArrayList<>(
                        Arrays.asList(
                                new Object[][] {
                                    // mediaType, AudioSweepTestAttrib
                                    {
                                        MediaFormat.MIMETYPE_AUDIO_AAC,
                                        new AudioSweepTestAttrib(
                                                new int[] {192000},
                                                new int[] {48000},
                                                new int[] {1, 2},
                                                AACObjectLC,
                                                100.0f,
                                                20000.0f,
                                                20.0f)
                                    },
                                    {
                                        MediaFormat.MIMETYPE_AUDIO_AAC,
                                        new AudioSweepTestAttrib(
                                                new int[] {64000},
                                                new int[] {48000},
                                                new int[] {2},
                                                AACObjectHE,
                                                100.0f,
                                                20000.0f,
                                                35.0f)
                                    },
                                    {
                                        MediaFormat.MIMETYPE_AUDIO_AAC,
                                        new AudioSweepTestAttrib(
                                                new int[] {128000},
                                                new int[] {48000},
                                                new int[] {1, 2},
                                                AACObjectELD,
                                                100.0f,
                                                20000.0f,
                                                55.0f)
                                    },
                                    {
                                        MediaFormat.MIMETYPE_AUDIO_OPUS,
                                        new AudioSweepTestAttrib(
                                                new int[] {128000},
                                                new int[] {48000},
                                                new int[] {1, 2},
                                                -1,
                                                20.0f,
                                                20000.0f,
                                                21.0f)
                                    },
                                    {
                                        MediaFormat.MIMETYPE_AUDIO_AMR_NB,
                                        new AudioSweepTestAttrib(
                                                new int[] {12200},
                                                new int[] {8000},
                                                new int[] {1},
                                                -1,
                                                300.0f,
                                                3400.0f,
                                                9.0f)
                                    },
                                    {
                                        MediaFormat.MIMETYPE_AUDIO_AMR_WB,
                                        new AudioSweepTestAttrib(
                                                new int[] {23850},
                                                new int[] {16000},
                                                new int[] {1},
                                                -1,
                                                50.0f,
                                                7000.0f,
                                                20.0f)
                                    },
                                    {
                                        MediaFormat.MIMETYPE_AUDIO_FLAC,
                                        new AudioSweepTestAttrib(
                                                new int[] {5},
                                                new int[] {48000},
                                                new int[] {1, 2},
                                                -1,
                                                20.0f,
                                                20000.0f,
                                                20.0f)
                                    },
                                }));
        List<Object[]> argsList =
                prepareParamList(defArgsList, isEncoder, needAudio, needVideo, false);
        return flattenParams(argsList);
    }

    private void encodeDecodeAndValidate() throws IOException, InterruptedException {
        float[] audioToProcess =
                AudioAnalysisHelper.generateSineSweep(
                        mActiveEncCfg.mSampleRate,
                        mStartFreq,
                        mEndFreq,
                        SWEEP_AMPLITUDE,
                        SWEEP_DURATION_SAMPLES * mActiveEncCfg.mChannelCount,
                        mActiveEncCfg.mChannelCount);

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

        int channelCount = mActiveEncCfg.mChannelCount;
        int samplesPerChannel = (int) decodedSamples.size() / channelCount;
        // Analyze sine sweep by channel.
        for (int channel = 0; channel < channelCount; channel++) {
            float[] channelSamples = new float[samplesPerChannel];
            for (int sample = 0; sample < samplesPerChannel; sample++) {
                channelSamples[sample] = decodedSamples.get(sample * channelCount + channel);
            }

            float deviation =
                    AudioAnalysisHelper.SweepAnalyzer.analyze(
                            channelSamples,
                            mActiveEncCfg.mSampleRate,
                            mStartFreq,
                            mEndFreq,
                            FFT_SIZE);

            if (VERBOSE) {
                Log.d(TAG, "Frequency response deviation: " + deviation + " dB");
            }

            if (deviation > mMaxDeviationDb) {
                String failureDetails =
                        String.format(
                                " (Encoder: %s, Params: %s)", mCodecName, mActiveEncCfg.toString());
                fail(
                        String.format(
                                "Frequency response deviation is too high for channel %d."
                                        + "Got: %f, Limit: %f%s",
                                channel, deviation, mMaxDeviationDb, failureDetails));
            }
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
    public void testSineSweepEncodeDecode() throws IOException, InterruptedException {
        mActiveEncCfg = mEncCfgParams[0];
        encodeDecodeAndValidate();
    }
}
