/*
 * Copyright (C) 2016 The Android Open Source Project
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

import android.annotation.RawRes;
import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.cts.util.CtsAndroidTestCase;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTimestamp;
import android.media.AudioTrack;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Random;

// Test the Java AudioTrack surround sound and HDMI passthrough.
// Most tests involve creating a track with a given format and then playing
// a few seconds of audio. The playback is verified by measuring the output
// sample rate based on the AudioTimestamps.

public class AudioTrackSurroundTest extends CtsAndroidTestCase {
    private static final String TAG = "AudioTrackSurroundTest";

    // We typically find tolerance to be within 0.2 percent, but we allow one percent.
    private static final double MAX_RATE_TOLERANCE_FRACTION = 0.01;
    private static final double MAX_INSTANTANEOUS_RATE_TOLERANCE_FRACTION = 0.15;
    private static final boolean LOG_TIMESTAMPS = false; // set true for debugging

    // Set this true to prefer the device that supports the particular encoding.
    // But note that as of 3/25/2016, a bug causes Direct tracks to fail.
    // So only set true when debugging that problem.
    private static final boolean USE_PREFERRED_DEVICE = false;

    private final static long NANOS_PER_MILLISECOND = 1000000L;
    private final static int MILLIS_PER_SECOND = 1000;
    private final static long NANOS_PER_SECOND = NANOS_PER_MILLISECOND * MILLIS_PER_SECOND;

    private final static int RES_AC3_VOICE_48000 = R.raw.voice12_48k_128kbps_15s_ac3;

    // Devices that support various encodings.
    private static boolean mDeviceScanComplete = false;
    private static AudioDeviceInfo mInfoPCM16 = null;
    private static AudioDeviceInfo mInfoAC3 = null;
    private static AudioDeviceInfo mInfoE_AC3 = null;
    private static AudioDeviceInfo mInfoDTS = null;
    private static AudioDeviceInfo mInfoDTS_HD = null;
    private static AudioDeviceInfo mInfoIEC61937 = null;

    private static void log(String testName, String message) {
        Log.i(TAG, "[" + testName + "] " + message);
    }

    private static void logw(String testName, String message) {
        Log.w(TAG, "[" + testName + "] " + message);
    }

    private static void loge(String testName, String message) {
        Log.e(TAG, "[" + testName + "] " + message);
    }

    // This is a special method that is called automatically before each test.
    @Override
    protected void setUp() throws Exception {
        // Note that I tried to only scan for encodings once but the static
        // data did not persist properly. That may be a bug.
        // For now, just scan before every test.
        scanDevicesForEncodings();
    }

    private void scanDevicesForEncodings() throws Exception {
        // Scan devices to see which encodings are supported.
        AudioManager audioManager = (AudioManager) getContext()
                .getSystemService(Context.AUDIO_SERVICE);
        AudioDeviceInfo[] infos = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
        for (AudioDeviceInfo info : infos) {
            log("scanDevicesForEncodings", "scanning devices, info = " + info
                    + ", id = " + info.getId() + "---------------");
            if (info.isSink()) {
                for (int encoding : info.getEncodings()) {
                    switch (encoding) {
                        case AudioFormat.ENCODING_PCM_16BIT:
                            mInfoPCM16 = info;
                            log(TAG, "mInfoPCM16 set to " + info);
                            break;
                        case AudioFormat.ENCODING_AC3:
                            mInfoAC3 = info;
                            log(TAG, "mInfoAC3 set to " + info);
                            break;
                        case AudioFormat.ENCODING_E_AC3:
                            mInfoE_AC3 = info;
                            log(TAG, "mInfoE_AC3 set to " + info);
                            break;
                        case AudioFormat.ENCODING_DTS:
                            mInfoDTS = info;
                            log(TAG, "mInfoDTS set to " + info);
                            break;
                        case AudioFormat.ENCODING_DTS_HD:
                            mInfoDTS_HD = info;
                            log(TAG, "mInfoDTS_HD set to " + info);
                            break;
                        case AudioFormat.ENCODING_IEC61937:
                            mInfoIEC61937 = info;
                            log(TAG, "mInfoIEC61937 set to " + info);
                            break;
                        default:
                            // This is OK. It is just an encoding that we don't care about.
                            break;
                    }
                }
            }
        }
    }

    // Load a resource into a byte[]
    private byte[] loadRawResourceBytes(@RawRes int id) throws Exception {
        AssetFileDescriptor masterFd = getContext().getResources().openRawResourceFd(id);
        long masterLength = masterFd.getLength();
        byte[] masterBuffer = new byte[(int) masterLength];
        InputStream is = masterFd.createInputStream();
        BufferedInputStream bis = new BufferedInputStream(is);
        int result = bis.read(masterBuffer);
        bis.close();
        masterFd.close();
        return masterBuffer;
    }

    // Load a resource into a short[]
    private short[] loadRawResourceShorts(@RawRes int id) throws Exception {
        AssetFileDescriptor masterFd = getContext().getResources().openRawResourceFd(id);
        long masterLength = masterFd.getLength();
        short[] masterBuffer = new short[(int) (masterLength / 2)];
        InputStream is = masterFd.createInputStream();
        BufferedInputStream bis = new BufferedInputStream(is);
        for (int i = 0; i < masterBuffer.length; i++) {
            int lo = bis.read(); // assume Little Endian
            int hi = bis.read();
            masterBuffer[i] = (short) (hi * 256 + lo);
        }
        bis.close();
        masterFd.close();
        return masterBuffer;
    }

    public void testLoadSineSweep() throws Exception {
        final String TEST_NAME = "testLoadSineSweep";
        short[] shortData = loadRawResourceShorts(R.raw.sinesweepraw);
        assertTrue(TEST_NAME + ": load sinesweepraw as shorts", shortData.length > 100);
        byte[] byteData = loadRawResourceBytes(R.raw.sinesweepraw);
        assertTrue(TEST_NAME + ": load sinesweepraw as bytes", byteData.length > shortData.length);
    }

    private static AudioTrack createAudioTrack(int sampleRate, int encoding, int channelConfig) {
        final String TEST_NAME = "createAudioTrack";
        int minBufferSize = AudioTrack.getMinBufferSize(
                sampleRate, channelConfig,
                encoding);
        assertTrue(TEST_NAME + ": getMinBufferSize", minBufferSize > 0);
        int bufferSize = minBufferSize * 3; // plenty big
        AudioTrack track = new AudioTrack(AudioManager.STREAM_MUSIC,
                sampleRate, channelConfig,
                encoding, bufferSize,
                AudioTrack.MODE_STREAM);
        return track;
    }

    static class TimestampAnalyzer {
        ArrayList<AudioTimestamp> mTimestamps = new ArrayList<AudioTimestamp>();
        AudioTimestamp mPreviousTimestamp = null;

        static String timestampToString(AudioTimestamp timestamp) {
            if (timestamp == null)
                return "null";
            return "(pos = " + timestamp.framePosition + ", nanos = " + timestamp.nanoTime + ")";
        }

        // Add timestamp if unique and valid.
        void addTimestamp(AudioTrack track) {
            AudioTimestamp timestamp = new AudioTimestamp();
            boolean gotTimestamp = track.getTimestamp(timestamp);
            if (gotTimestamp) {
                // Only save timestamps after the data is flowing.
                if (mPreviousTimestamp != null) {
                    if ((timestamp.framePosition > 0)
                            && (timestamp.nanoTime != mPreviousTimestamp.nanoTime)) {
                        mTimestamps.add(timestamp);
                    }
                }
                mPreviousTimestamp = timestamp;
            }
        }

        // Use collected timestamps to estimate a sample rate.
        double estimateSampleRate() {
            assertTrue("expect many timestamps, got " + mTimestamps.size(),
                    mTimestamps.size() > 10);
            // Use first and last timestamp to get the most accurate rate.
            AudioTimestamp first = mTimestamps.get(0);
            AudioTimestamp last = mTimestamps.get(mTimestamps.size() - 1);
            double measuredRate = calculateSampleRate(first, last);

            AudioTimestamp previous = null;
            // Make sure the timestamps are smooth and don't go retrograde.
            for (AudioTimestamp timestamp : mTimestamps) {
                if (previous != null) {
                    double instantaneousRate = calculateSampleRate(previous, timestamp);
                    assertEquals("instantaneous sample rate should match long term rate",
                            measuredRate, instantaneousRate,
                            measuredRate * MAX_INSTANTANEOUS_RATE_TOLERANCE_FRACTION);
                    assertTrue("framePosition should be monotonic",
                            timestamp.framePosition > previous.framePosition);
                    assertTrue("nanoTime should be monotonic",
                            timestamp.nanoTime > previous.nanoTime);
                }
                previous = timestamp;
            }
            return measuredRate;
        }

        /**
         * @param timestamp1
         * @param timestamp2
         */
        private double calculateSampleRate(AudioTimestamp timestamp1, AudioTimestamp timestamp2) {
            long elapsedFrames = timestamp2.framePosition - timestamp1.framePosition;
            long elapsedNanos = timestamp2.nanoTime - timestamp1.nanoTime;
            double measuredRate = elapsedFrames * (double) NANOS_PER_SECOND / elapsedNanos;
            if (LOG_TIMESTAMPS) {
                Log.i(TAG, "calculateSampleRate(), elapsedFrames =, " + elapsedFrames
                        + ", measuredRate =, "
                        + (int) measuredRate);
            }
            return measuredRate;
        }
    }

    // Class for looping a recording for several seconds and measuring the sample rate.
    // This is not static because it needs to call getContext().
    abstract class SamplePlayerBase {
        private final int mSampleRate;
        private final int mEncoding;
        private final int mChannelConfig;
        private int mBlockSize = 512;
        protected int mOffset = 0;
        protected AudioTrack mTrack;
        private final TimestampAnalyzer mTimestampAnalyzer = new TimestampAnalyzer();

        SamplePlayerBase(int sampleRate, int encoding, int channelConfig) {
            mSampleRate = sampleRate;
            mEncoding = encoding;
            mChannelConfig = channelConfig;
        }

        // Use abstract write to handle byte[] or short[] data.
        protected abstract int writeBlock(int numSamples);

        private int primeBuffer() {
            // Will not block when track is stopped.
            return writeBlock(Integer.MAX_VALUE);
        }

        /**
         * Use a device that we know supports the current encoding.
         */
        private void usePreferredDevice() {
            AudioDeviceInfo info = null;
            switch (mEncoding) {
                case AudioFormat.ENCODING_PCM_16BIT:
                    info = mInfoPCM16;
                    break;
                case AudioFormat.ENCODING_AC3:
                    info = mInfoAC3;
                    break;
                case AudioFormat.ENCODING_E_AC3:
                    info = mInfoE_AC3;
                    break;
                case AudioFormat.ENCODING_DTS:
                    info = mInfoDTS;
                    break;
                case AudioFormat.ENCODING_DTS_HD:
                    info = mInfoDTS_HD;
                    break;
                case AudioFormat.ENCODING_IEC61937:
                    info = mInfoIEC61937;
                    break;
                default:
                    break;
            }

            if (info != null) {
                log(TAG, "track.setPreferredDevice(" + info + ")");
                mTrack.setPreferredDevice(info);
            }
        }

        public void playAndMeasureRate() throws Exception {
            final String TEST_NAME = "playAndMeasureRate";
            final long TEST_DURATION_MILLIS = 5000; // just long enough to measure the rate
            log(TEST_NAME, String.format("test using rate = %d, encoding = 0x%08x",
                    mSampleRate, mEncoding));
            // Create a track and prime it.
            mTrack = createAudioTrack(mSampleRate, mEncoding, mChannelConfig);
            try {
                assertEquals(TEST_NAME + ": track created", AudioTrack.STATE_INITIALIZED,
                        mTrack.getState());

                if (USE_PREFERRED_DEVICE) {
                    usePreferredDevice();
                }

                int bytesWritten = 0;
                mOffset = primeBuffer(); // prime the buffer
                assertTrue(TEST_NAME + ": priming offset = " + mOffset, mOffset > 0);
                bytesWritten += mOffset;

                // Play for a while.
                mTrack.play();
                long elapsedMillis = 0;
                long startTime = System.currentTimeMillis();
                while (elapsedMillis < TEST_DURATION_MILLIS) {
                    writeBlock(mBlockSize);
                    elapsedMillis = System.currentTimeMillis() - startTime;
                    mTimestampAnalyzer.addTimestamp(mTrack);
                }

                // Did we underrun? Allow 0 or 1 because there is sometimes
                // an underrun on startup.
                int underrunCount1 = mTrack.getUnderrunCount();
                assertTrue(TEST_NAME + ": too many underruns, got underrunCount1",
                        underrunCount1 < 2);

                // Estimate the sample rate and compare it with expected.
                double estimatedRate = mTimestampAnalyzer.estimateSampleRate();
                assertEquals(TEST_NAME + ": measured sample rate",
                        mSampleRate, estimatedRate, mSampleRate * MAX_RATE_TOLERANCE_FRACTION);
            } finally {
                mTrack.release();
            }
        }
    }

    // Create player for short[]
    class SamplePlayerShorts extends SamplePlayerBase {
        private final short[] mData;

        SamplePlayerShorts(int sampleRate, int encoding, int channelConfig) {
            super(sampleRate, encoding, channelConfig);
            mData = new short[64 * 1024];
            // Fill with noise. We should not hear the noise for IEC61937.
            int amplitude = 8000;
            Random random = new Random();
            for (int i = 0; i < mData.length; i++) {
                mData[i] = (short)(random.nextInt(amplitude) - (amplitude / 2));
            }
        }

        SamplePlayerShorts(int sampleRate, int encoding, int channelConfig, @RawRes int resourceId)
                throws Exception {
            super(sampleRate, encoding, channelConfig);
            mData = loadRawResourceShorts(resourceId);
            assertTrue("SamplePlayerShorts: load resource file as shorts", mData.length > 0);
        }

        @Override
        protected int writeBlock(int numShorts) {
            int result = 0;
            int shortsToWrite = numShorts;
            int shortsLeft = mData.length - mOffset;
            if (shortsToWrite > shortsLeft) {
                shortsToWrite = shortsLeft;
            }
            if (shortsToWrite > 0) {
                result = mTrack.write(mData, mOffset, shortsToWrite);
                mOffset += result;
            } else {
                mOffset = 0; // rewind
            }
            return result;
        }
    }

    // Create player for byte[]
    class SamplePlayerBytes extends SamplePlayerBase {
        private final byte[] mData;

        SamplePlayerBytes(int sampleRate, int encoding, int channelConfig) {
            super(sampleRate, encoding, channelConfig);
            mData = new byte[128 * 1024];
        }

        SamplePlayerBytes(int sampleRate, int encoding, int channelConfig, @RawRes int resourceId)
                throws Exception {
            super(sampleRate, encoding, channelConfig);
            mData = loadRawResourceBytes(resourceId);
            assertTrue("SamplePlayerBytes: load resource file as bytes", mData.length > 0);
        }

        @Override
        protected int writeBlock(int numBytes) {
            int result = 0;
            int bytesToWrite = numBytes;
            int bytesLeft = mData.length - mOffset;
            if (bytesToWrite > bytesLeft) {
                bytesToWrite = bytesLeft;
            }
            if (bytesToWrite > 0) {
                result = mTrack.write(mData, mOffset, bytesToWrite);
                mOffset += result;
            } else {
                mOffset = 0; // rewind
            }
            return result;
        }
    }

    public void testPlayAC3Bytes() throws Exception {
        if (mInfoAC3 != null) {
            SamplePlayerBytes player = new SamplePlayerBytes(
                    48000, AudioFormat.ENCODING_AC3, AudioFormat.CHANNEL_OUT_STEREO,
                    RES_AC3_VOICE_48000);
            player.playAndMeasureRate();
        }
    }

    public void testPlayAC3Shorts() throws Exception {
        if (mInfoAC3 != null) {
            SamplePlayerShorts player = new SamplePlayerShorts(
                    48000, AudioFormat.ENCODING_AC3, AudioFormat.CHANNEL_OUT_STEREO,
                    RES_AC3_VOICE_48000);
            player.playAndMeasureRate();
        }
    }

    // Note that for testing IEC61937, the Audio framework does not look at the
    // wrapped data. It just passes it through over HDMI. See we can just use
    // zeros instead of real data.
    public void testPlayIEC61937_32000() throws Exception {
        if (mInfoIEC61937 != null) {
            SamplePlayerShorts player = new SamplePlayerShorts(
                    32000, AudioFormat.ENCODING_IEC61937, AudioFormat.CHANNEL_OUT_STEREO);
            player.playAndMeasureRate();
        }
    }

    public void testPlayIEC61937_44100() throws Exception {
        if (mInfoIEC61937 != null) {
            SamplePlayerShorts player = new SamplePlayerShorts(
                    44100, AudioFormat.ENCODING_IEC61937, AudioFormat.CHANNEL_OUT_STEREO);
            player.playAndMeasureRate();
        }
    }

    public void testPlayIEC61937_48000() throws Exception {
        if (mInfoIEC61937 != null) {
            SamplePlayerShorts player = new SamplePlayerShorts(
                    48000, AudioFormat.ENCODING_IEC61937, AudioFormat.CHANNEL_OUT_STEREO);
            player.playAndMeasureRate();
        }
    }

    public void testIEC61937_Errors() throws Exception {
        if (mInfoIEC61937 != null) {
            final String TEST_NAME = "testIEC61937_Errors";
            try {
                AudioTrack track = createAudioTrack(48000, AudioFormat.ENCODING_IEC61937,
                        AudioFormat.CHANNEL_OUT_MONO);
                assertTrue(TEST_NAME + ": IEC61937 track creation should fail for mono", false);
            } catch (IllegalArgumentException e) {
                // This is expected behavior.
            }

            try {
                AudioTrack track = createAudioTrack(48000, AudioFormat.ENCODING_IEC61937,
                        AudioFormat.CHANNEL_OUT_5POINT1);
                assertTrue(TEST_NAME + ": IEC61937 track creation should fail for 5.1", false);
            } catch (IllegalArgumentException e) {
                // This is expected behavior.
            }
        }
    }

    public void testPlaySineSweepShorts() throws Exception {
        SamplePlayerShorts player = new SamplePlayerShorts(
                44100, AudioFormat.ENCODING_PCM_16BIT, AudioFormat.CHANNEL_OUT_STEREO,
                R.raw.sinesweepraw);
        player.playAndMeasureRate();
    }

    public void testPlaySineSweepBytes() throws Exception {
        SamplePlayerBytes player = new SamplePlayerBytes(
                44100, AudioFormat.ENCODING_PCM_16BIT, AudioFormat.CHANNEL_OUT_STEREO,
                R.raw.sinesweepraw);
        player.playAndMeasureRate();
    }

    public void testPlaySineSweepBytes48000() throws Exception {
        SamplePlayerBytes player = new SamplePlayerBytes(
                48000, AudioFormat.ENCODING_PCM_16BIT, AudioFormat.CHANNEL_OUT_STEREO,
                R.raw.sinesweepraw);
        player.playAndMeasureRate();
    }

    public void testPlaySineSweepShortsMono() throws Exception {
        SamplePlayerShorts player = new SamplePlayerShorts(44100, AudioFormat.ENCODING_PCM_16BIT,
                AudioFormat.CHANNEL_OUT_MONO,
                R.raw.sinesweepraw);
        player.playAndMeasureRate();
    }

    public void testPlaySineSweepBytesMono()
            throws Exception {
        SamplePlayerBytes player = new SamplePlayerBytes(44100,
                AudioFormat.ENCODING_PCM_16BIT, AudioFormat.CHANNEL_OUT_MONO, R.raw.sinesweepraw);
        player.playAndMeasureRate();
    }

}
