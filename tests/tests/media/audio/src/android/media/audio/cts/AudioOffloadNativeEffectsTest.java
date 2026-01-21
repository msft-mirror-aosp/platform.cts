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

package android.media.audio.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeNotNull;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.audiofx.BassBoost;
import android.media.audiofx.DynamicsProcessing;
import android.media.audiofx.Equalizer;
import android.media.audiofx.Virtualizer;
import android.media.audiofx.Visualizer;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.FrameworkSpecificTest;
import com.android.compatibility.common.util.MediaUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM)
@FrameworkSpecificTest
@RunWith(AndroidJUnit4.class)
public class AudioOffloadNativeEffectsTest {
    private static final String TAG = "AudioOffloadNativeEffectsTest";

    private static final int STATUS_OK = 0;
    private static final float AUDIOTRACK_DEFAULT_FREQUENCY_HZ = 910.0f;
    private static final float AUDIOTRACK_TEST_FREQUENCY_HZ = 100.0f;
    private static final int AUDIOTRACK_DEFAULT_SAMPLE_RATE = 48000;
    private static final int PER_TEST_TIMEOUT_LARGE_TEST_MS = 300000;
    private static final int PER_TEST_TIMEOUT_SMALL_TEST_MS = 60000;
    private static final int PLAYBACK_COMPLETION_DELAY_MS = 10000;
    private static final int VISUALIZER_SILENCE_MB = -9600; // RMS value for silence in millibels
    private static final float[] DP_TEST_GAINS_DB = {-24.0f, -12.0f, -6.0f, 0f, 6.0f, 12.0f, 24.0f};

    private final int mStreamType = AudioManager.STREAM_MUSIC;
    private AudioManager mAudioManager = null;
    private int mOriginalVolume;
    private int mSessionId = 0;
    private long mStreamHandle = 0;
    private BassBoost mBassBoost = null;
    private DynamicsProcessing mDynamicsProcessing = null;
    private Equalizer mEqualizer = null;
    private Virtualizer mVirtualizer = null;
    private Visualizer mVisualizer = null;

    private enum EffectType {
        BASS_BOOST,
        VIRTUALIZER
    }

    static {
        System.loadLibrary("audiocts_aaudio_jni");
    }

    @Before
    public void setup() throws Exception {
        assumeFalse("Skipping test, no need to test on emulator", Build.IS_EMULATOR);
        assumeFalse("Skipping test, no need to test on cuttlefish", MediaUtils.onCuttlefish());
        assumeFalse(
                "Skipping test, no need to test on gsi image running on cuttlefish",
                MediaUtils.gsiOnCuttleFish());

        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

        assumeTrue("Skipping test, no audio output found", hasAudioOutput(context));

        final int supportedOffloadFormat = findSupportedPcmOffloadFormat();
        assumeTrue(
                "Skipping test, no PCM offload support found on this device",
                supportedOffloadFormat != AudioFormat.ENCODING_INVALID);

        mAudioManager = context.getSystemService(AudioManager.class);
        mOriginalVolume = mAudioManager.getStreamVolume(mStreamType);

        // Set a reasonable volume to ensure the Visualizer captures a non-silent
        // signal.
        mAudioManager.setStreamVolume(
                AudioManager.STREAM_MUSIC,
                mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) / 2,
                /* flag= */ 0);

        mStreamHandle = nativeOpenStream(supportedOffloadFormat);
        assertTrue("Failed to open native stream", mStreamHandle != 0);

        mSessionId = nativeGetSessionId(mStreamHandle);
        assumeTrue("Failed to get a valid session ID", mSessionId > 0);
    }

    @After
    public void teardown() {
        if (mAudioManager != null) {
            mAudioManager.setStreamVolume(mStreamType, mOriginalVolume, /* flag */ 0);
        }
        if (mBassBoost != null) {
            mBassBoost.release();
        }
        if (mDynamicsProcessing != null) {
            mDynamicsProcessing.release();
        }
        if (mEqualizer != null) {
            mEqualizer.release();
        }
        if (mVirtualizer != null) {
            mVirtualizer.release();
        }
        if (mVisualizer != null) {
            mVisualizer.release();
        }
        if (mStreamHandle != 0) {
            nativeCloseStream(mStreamHandle);
        }
    }

    private boolean hasAudioOutput(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUDIO_OUTPUT);
    }

    private int findSupportedPcmOffloadFormat() {
        // Define formats in order of preference.
        final int[] formatsToCheck = {
            AudioFormat.ENCODING_PCM_FLOAT,
            AudioFormat.ENCODING_PCM_16BIT,
            AudioFormat.ENCODING_PCM_32BIT,
            AudioFormat.ENCODING_PCM_24BIT_PACKED
        };

        final AudioAttributes attributes = new AudioAttributes.Builder().build();

        for (int format : formatsToCheck) {
            // The encoding, sample rate and the channel mask should be the same as the
            // one in native for creating stream.
            final AudioFormat audioFormat =
                    new AudioFormat.Builder()
                            .setEncoding(format)
                            .setSampleRate(AUDIOTRACK_DEFAULT_SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                            .build();

            if (AudioManager.isOffloadedPlaybackSupported(audioFormat, attributes)) {
                Log.i(TAG, "Device supports offload for format: " + format);
                return format;
            }
        }
        return AudioFormat.ENCODING_INVALID;
    }

    private void setupVisualizer() {
        mVisualizer = new Visualizer(mSessionId);
        assumeNotNull("Failed to create Visualizer effect", mVisualizer);
        assumeTrue(
                Visualizer.SUCCESS
                        == mVisualizer.setCaptureSize(
                                Visualizer.getCaptureSizeRange()[1])); // Max size
        assumeTrue(
                Visualizer.SUCCESS
                        == mVisualizer.setMeasurementMode(Visualizer.MEASUREMENT_MODE_PEAK_RMS));
        assumeTrue(Visualizer.SUCCESS == mVisualizer.setEnabled(true));
    }

    private int playAndGetRms(float testFrequencyHz) throws InterruptedException {
        assertNotNull("Visualizer must be initialized first", mVisualizer);
        assertEquals(Visualizer.SUCCESS, mVisualizer.setEnabled(false)); // Reset measurement
        assertEquals(Visualizer.SUCCESS, mVisualizer.setEnabled(true));

        assertEquals(
                "Native playback failed",
                STATUS_OK,
                nativePlayAndSignalEnd(mStreamHandle, testFrequencyHz));
        SystemClock.sleep(PLAYBACK_COMPLETION_DELAY_MS);

        Visualizer.MeasurementPeakRms measurement = new Visualizer.MeasurementPeakRms();
        assertEquals(Visualizer.SUCCESS, mVisualizer.getMeasurementPeakRms(measurement));
        return measurement.mRms;
    }

    private void testIncreasingStrength(EffectType effect) throws InterruptedException {
        // Initialize the Visualizer to capture and measure the rms of audio output.
        setupVisualizer();

        final float testFrequencyHz = 100.0f;
        final short[] testIncreasingStrength = {0, 500, 1000};
        int prevRmsMb = Integer.MIN_VALUE;

        for (short strength : testIncreasingStrength) {
            switch (effect) {
                case BASS_BOOST:
                    mBassBoost.setStrength(strength);
                    break;

                case VIRTUALIZER:
                    mVirtualizer.setStrength(strength);
                    break;
            }
            final int currRmsMb = playAndGetRms(testFrequencyHz);
            assumeTrue(
                    "Curr Rms ( "
                            + currRmsMb
                            + " ) at strength "
                            + strength
                            + " should be more than Prev Rms ( "
                            + prevRmsMb
                            + " )",
                    currRmsMb > prevRmsMb);
            prevRmsMb = currRmsMb;
        }
    }

    /**
     * Helper to test the EQ stages (PreEQ or PostEQ) of DynamicsProcessing.
     *
     * @param isPreEq true to test PreEQ, false to test PostEQ.
     */
    private void runDynamicsProcessingEqStageTest(boolean isPreEq) throws InterruptedException {
        final int channelCount = 2;
        final int bandCount = 4; // Divide spectrum into 4 bands
        final int testBandIndex = 0; // Ensure test frequency falls within this band

        DynamicsProcessing.Config.Builder configBuilder =
                new DynamicsProcessing.Config.Builder(
                        DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                        channelCount,
                        isPreEq /* preEqInUse */,
                        (isPreEq ? bandCount : 0),
                        false /* mbcInUse */,
                        0,
                        !isPreEq /* postEqInUse */,
                        (!isPreEq ? bandCount : 0),
                        false /* limiterInUse */);

        mDynamicsProcessing = new DynamicsProcessing(0, mSessionId, configBuilder.build());
        assertNotNull("Failed to create DynamicsProcessing", mDynamicsProcessing);
        mDynamicsProcessing.setEnabled(true);

        DynamicsProcessing.EqBand bassBand;
        if (isPreEq) {
            bassBand = mDynamicsProcessing.getPreEqBandByChannelIndex(0, 0); // Ch 0, Band 0
        } else {
            bassBand = mDynamicsProcessing.getPostEqBandByChannelIndex(0, 0);
        }

        // Set cutoff to 200Hz to ensure Band 0 includes AUDIOTRACK_TEST_FREQUENCY_HZ.
        bassBand.setCutoffFrequency(AUDIOTRACK_TEST_FREQUENCY_HZ * 2);

        if (isPreEq) {
            mDynamicsProcessing.setPreEqBandAllChannelsTo(0, bassBand);
        } else {
            mDynamicsProcessing.setPostEqBandAllChannelsTo(0, bassBand);
        }

        // Initialize the Visualizer to capture and measure the rms of audio output.
        setupVisualizer();

        int prevRmsMb = Integer.MIN_VALUE;
        for (final float gainDb : DP_TEST_GAINS_DB) {
            DynamicsProcessing.EqBand bandConfig;
            if (isPreEq) {
                bandConfig = mDynamicsProcessing.getPreEqBandByChannelIndex(0, testBandIndex);
                bandConfig.setGain(gainDb);
                mDynamicsProcessing.setPreEqBandAllChannelsTo(testBandIndex, bandConfig);
            } else {
                bandConfig = mDynamicsProcessing.getPostEqBandByChannelIndex(0, testBandIndex);
                bandConfig.setGain(gainDb);
                mDynamicsProcessing.setPostEqBandAllChannelsTo(testBandIndex, bandConfig);
            }

            final int currRmsMb = playAndGetRms(AUDIOTRACK_TEST_FREQUENCY_HZ);
            assumeTrue(
                    "Curr Rms ( "
                            + currRmsMb
                            + " ) at gain "
                            + gainDb
                            + " should be more than Prev Rms ( "
                            + prevRmsMb
                            + " )",
                    currRmsMb > prevRmsMb);

            prevRmsMb = currRmsMb;
        }
    }

    @Test(timeout = PER_TEST_TIMEOUT_SMALL_TEST_MS)
    public void testMmapPcmOffload() throws InterruptedException {
        assertEquals(
                "Native playback failed",
                STATUS_OK,
                nativePlayAndSignalEnd(mStreamHandle, AUDIOTRACK_DEFAULT_FREQUENCY_HZ));
        SystemClock.sleep(PLAYBACK_COMPLETION_DELAY_MS);
    }

    @Test(timeout = PER_TEST_TIMEOUT_SMALL_TEST_MS)
    public void testMmapPcmOffloadWithVisualizerEffect() throws InterruptedException {
        setupVisualizer();
        final int rmsMb = playAndGetRms(AUDIOTRACK_DEFAULT_FREQUENCY_HZ);
        Log.i(TAG, "Measured Rms: " + rmsMb);
        assumeTrue("rms should not represent silence", rmsMb != VISUALIZER_SILENCE_MB);
    }

    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testMmapPcmOffloadWithEqualizerEffect() throws InterruptedException {
        mEqualizer = new Equalizer(0, mSessionId);
        assumeNotNull("Failed to create Equalizer effect", mEqualizer);
        assumeTrue(Equalizer.SUCCESS == mEqualizer.setEnabled(true));

        // Initialize the Visualizer to capture and measure the rms of audio output.
        setupVisualizer();

        final short bandToBoost =
                mEqualizer.getBand((int) AUDIOTRACK_DEFAULT_FREQUENCY_HZ * 1000 /*convert to mHz*/);

        // Define the sequence of decreasing band levels to test, in millibels
        // (0 dB, -6dB, -12 dB).
        final short[] testDecreasingBandLevelsMb = {0, -600, -1200};
        int prevRmsMb = Integer.MAX_VALUE;

        for (short bandLevelMb : testDecreasingBandLevelsMb) {
            mEqualizer.setBandLevel(bandToBoost, bandLevelMb);
            final int currRmsMb = playAndGetRms(AUDIOTRACK_DEFAULT_FREQUENCY_HZ);
            Log.i(TAG, "Measured Curr Rms : " + currRmsMb);
            assumeTrue(
                    "Curr Rms at band level " + bandLevelMb + " should be less than " + prevRmsMb,
                    currRmsMb < prevRmsMb);
            prevRmsMb = currRmsMb;
        }
    }

    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testMmapPcmOffloadWithBassBoostEffect() throws InterruptedException {
        mBassBoost = new BassBoost(0, mSessionId);
        assumeNotNull("Failed to create BassBoost effect", mBassBoost);
        assumeTrue(BassBoost.SUCCESS == mBassBoost.setEnabled(true));

        testIncreasingStrength(EffectType.BASS_BOOST);
    }

    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testMmapPcmOffloadWithVirtualizerEffect() throws InterruptedException {
        mVirtualizer = new Virtualizer(0, mSessionId);
        assumeNotNull("Failed to create Virtualizer effect", mVirtualizer);
        assumeTrue(Virtualizer.SUCCESS == mVirtualizer.setEnabled(true));

        testIncreasingStrength(EffectType.VIRTUALIZER);
    }

    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testMmapPcmOffloadWithDynamicsProcessingInputGain() throws InterruptedException {
        // Create a "pass-through" config with all internal stages disabled.
        DynamicsProcessing.Config.Builder configBuilder =
                new DynamicsProcessing.Config.Builder(
                        DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                        2, /* stereo channel */
                        false /* preEqInUse */,
                        0 /* preEqBandCount */,
                        false /* mbcInUse */,
                        0 /* mbcBandCount */,
                        false /* postEqInUse */,
                        0 /* postEqBandCount */,
                        false /* limiterInUse */);
        mDynamicsProcessing = new DynamicsProcessing(0, mSessionId, configBuilder.build());
        assumeNotNull("Failed to create DynamicsProcessing effect", mDynamicsProcessing);
        mDynamicsProcessing.setEnabled(true);

        // Initialize the Visualizer to capture and measure the rms of audio output.
        setupVisualizer();

        int prevRmsMb = Integer.MIN_VALUE;
        for (float gainDb : DP_TEST_GAINS_DB) {
            mDynamicsProcessing.setInputGainAllChannelsTo(gainDb);

            final int currRmsMb = playAndGetRms(AUDIOTRACK_TEST_FREQUENCY_HZ);
            assumeTrue(
                    "Curr Rms ( "
                            + currRmsMb
                            + " ) at gain "
                            + gainDb
                            + " should be more than Prev Rms ( "
                            + prevRmsMb
                            + " )",
                    currRmsMb > prevRmsMb);
            prevRmsMb = currRmsMb;
        }
    }

    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testMmapPcmOffloadWithDynamicsProcessingPreEq() throws InterruptedException {
        // Run the test for the Pre-Equalizer stage
        runDynamicsProcessingEqStageTest(true);
    }

    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testMmapPcmOffloadWithDynamicsProcessingPostEq() throws InterruptedException {
        // Run the test for the Post-Equalizer stage
        runDynamicsProcessingEqStageTest(false);
    }

    private static native long nativeOpenStream(int supportedOffloadFormat);

    private static native int nativeGetSessionId(long streamHandle);

    private static native int nativePlayAndSignalEnd(long streamHandle, float testFrequencyHz);

    private static native void nativeCloseStream(long streamHandle);
}
