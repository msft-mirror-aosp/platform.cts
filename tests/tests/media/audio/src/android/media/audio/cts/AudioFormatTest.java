/*
 * Copyright (C) 2015 The Android Open Source Project
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
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.media.AudioFormat;
import android.media.audio.Flags;
import android.os.Parcel;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.FrameworkSpecificTest;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@FrameworkSpecificTest
@RunWith(AndroidJUnit4.class)
public class AudioFormatTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    // -----------------------------------------------------------------
    // AUDIOFORMAT TESTS:
    // ----------------------------------

    // -----------------------------------------------------------------
    // Builder tests
    // ----------------------------------

    // Test case 1: Use Builder to duplicate an AudioFormat with all fields supplied
    @Test
    public void testBuilderForCopy() throws Exception {
        final int TEST_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
        final int TEST_SR = 48000;
        final int TEST_CONF_POS = AudioFormat.CHANNEL_OUT_5POINT1;
        // 6ch, like in 5.1 above offset by a randomly chosen number
        final int TEST_CONF_IDX = 0x3F << 3;

        final AudioFormat formatToCopy = new AudioFormat.Builder()
                .setEncoding(TEST_ENCODING).setSampleRate(TEST_SR)
                .setChannelMask(TEST_CONF_POS).setChannelIndexMask(TEST_CONF_IDX).build();
        assertNotNull("Failure to create the AudioFormat to copy", formatToCopy);

        final AudioFormat copiedFormat = new AudioFormat.Builder(formatToCopy).build();
        assertNotNull("Failure to create AudioFormat copy with Builder", copiedFormat);
        assertEquals("New AudioFormat has wrong sample rate",
                TEST_SR, copiedFormat.getSampleRate());
        assertEquals("New AudioFormat has wrong encoding",
                TEST_ENCODING, copiedFormat.getEncoding());
        assertEquals("New AudioFormat has wrong channel mask",
                TEST_CONF_POS, copiedFormat.getChannelMask());
        assertEquals("New AudioFormat has wrong channel index mask",
                TEST_CONF_IDX, copiedFormat.getChannelIndexMask());
        assertEquals("New AudioFormat has wrong channel count",
                6, copiedFormat.getChannelCount());
        assertEquals("New AudioFormat has the wrong frame size",
                6 /* channels */ * 2 /* bytes per sample */, copiedFormat.getFrameSizeInBytes());
    }

    // Test case 2: Use Builder to duplicate an AudioFormat with only encoding supplied
    @Test
    public void testPartialFormatBuilderForCopyEncoding() throws Exception {
        final int TEST_ENCODING = AudioFormat.ENCODING_PCM_16BIT;

        final AudioFormat formatToCopy = new AudioFormat.Builder()
                .setEncoding(TEST_ENCODING).build();
        assertNotNull("Failure to create the AudioFormat to copy", formatToCopy);

        final AudioFormat copiedFormat = new AudioFormat.Builder(formatToCopy).build();
        assertNotNull("Failure to create AudioFormat copy with Builder", copiedFormat);
        assertEquals("New AudioFormat has wrong encoding",
                TEST_ENCODING, copiedFormat.getEncoding());
        // test expected values when none has been set
        assertEquals("New AudioFormat doesn't report expected sample rate",
                0, copiedFormat.getSampleRate());
        assertEquals("New AudioFormat doesn't report expected channel mask",
                AudioFormat.CHANNEL_INVALID, copiedFormat.getChannelMask());
        assertEquals("New AudioFormat doesn't report expected channel index mask",
                AudioFormat.CHANNEL_INVALID, copiedFormat.getChannelIndexMask());
    }

    // Test case 3: Use Builder to duplicate an AudioFormat with only sample rate supplied
    @Test
    public void testPartialFormatBuilderForCopyRate() throws Exception {
        final int TEST_SR = 48000;

        final AudioFormat formatToCopy = new AudioFormat.Builder()
                .setSampleRate(TEST_SR).build();
        assertNotNull("Failure to create the AudioFormat to copy", formatToCopy);

        final AudioFormat copiedFormat = new AudioFormat.Builder(formatToCopy).build();
        assertNotNull("Failure to create AudioFormat copy with Builder", copiedFormat);
        assertEquals("New AudioFormat has wrong sample rate",
                TEST_SR, copiedFormat.getSampleRate());
        // test expected values when none has been set
        assertEquals("New AudioFormat doesn't report expected encoding",
                AudioFormat.ENCODING_INVALID, copiedFormat.getEncoding());
        assertEquals("New AudioFormat doesn't report expected channel mask",
                AudioFormat.CHANNEL_INVALID, copiedFormat.getChannelMask());
        assertEquals("New AudioFormat doesn't report expected channel index mask",
                AudioFormat.CHANNEL_INVALID, copiedFormat.getChannelIndexMask());
    }

    // Test case 4: Use Builder to duplicate an AudioFormat with only channel mask supplied
    @Test
    public void testPartialFormatBuilderForCopyChanMask() throws Exception {
        final int TEST_CONF_POS = AudioFormat.CHANNEL_OUT_5POINT1;

        final AudioFormat formatToCopy = new AudioFormat.Builder()
                .setChannelMask(TEST_CONF_POS).build();
        assertNotNull("Failure to create the AudioFormat to copy", formatToCopy);

        final AudioFormat copiedFormat = new AudioFormat.Builder(formatToCopy).build();
        assertNotNull("Failure to create AudioFormat copy with Builder", copiedFormat);
        assertEquals("New AudioFormat has wrong channel mask",
                TEST_CONF_POS, copiedFormat.getChannelMask());
        // test expected values when none has been set
        assertEquals("New AudioFormat doesn't report expected encoding",
                AudioFormat.ENCODING_INVALID, copiedFormat.getEncoding());
        assertEquals("New AudioFormat doesn't report expected sample rate",
                0, copiedFormat.getSampleRate());
        assertEquals("New AudioFormat doesn't report expected channel index mask",
                AudioFormat.CHANNEL_INVALID, copiedFormat.getChannelIndexMask());
    }

    // Test case 5: Use Builder to duplicate an AudioFormat with only channel index mask supplied
    @Test
    public void testPartialFormatBuilderForCopyChanIdxMask() throws Exception {
        final int TEST_CONF_IDX = 0x30;

        final AudioFormat formatToCopy = new AudioFormat.Builder()
                .setChannelIndexMask(TEST_CONF_IDX).build();
        assertNotNull("Failure to create the AudioFormat to copy", formatToCopy);

        final AudioFormat copiedFormat = new AudioFormat.Builder(formatToCopy).build();
        assertNotNull("Failure to create AudioFormat copy with Builder", copiedFormat);
        assertEquals("New AudioFormat has wrong channel mask",
                TEST_CONF_IDX, copiedFormat.getChannelIndexMask());
        // test expected values when none has been set
        assertEquals("New AudioFormat doesn't report expected encoding",
                AudioFormat.ENCODING_INVALID, copiedFormat.getEncoding());
        assertEquals("New AudioFormat doesn't report expected sample rate",
                0, copiedFormat.getSampleRate());
        assertEquals("New AudioFormat doesn't report expected channel mask",
                AudioFormat.CHANNEL_INVALID, copiedFormat.getChannelMask());
    }

    // Test case 6: create an instance, marshall it and create a new instance,
    //      check for equality
    @Test
    public void testParcel() throws Exception {
        final int TEST_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
        final int TEST_SR = 48000;
        final int TEST_CONF_POS = AudioFormat.CHANNEL_OUT_5POINT1;
        // 6ch, like in 5.1 above offset by a randomly chosen number
        final int TEST_CONF_IDX = 0x3F << 3;

        final AudioFormat formatToMarshall = new AudioFormat.Builder()
                .setEncoding(TEST_ENCODING).setSampleRate(TEST_SR)
                .setChannelMask(TEST_CONF_POS).setChannelIndexMask(TEST_CONF_IDX).build();
        assertNotNull("Failure to create the AudioFormat to marshall", formatToMarshall);
        assertEquals(0, formatToMarshall.describeContents());

        final Parcel srcParcel = Parcel.obtain();
        final Parcel dstParcel = Parcel.obtain();

        formatToMarshall.writeToParcel(srcParcel, 0 /*no public flags for marshalling*/);
        final byte[] mbytes = srcParcel.marshall();
        dstParcel.unmarshall(mbytes, 0, mbytes.length);
        dstParcel.setDataPosition(0);
        final AudioFormat unmarshalledFormat = AudioFormat.CREATOR.createFromParcel(dstParcel);

        assertNotNull("Failure to unmarshall AudioFormat", unmarshalledFormat);
        assertEquals("Source and destination AudioFormat not equal",
                formatToMarshall, unmarshalledFormat);
    }

    // Test case 7: Check frame size for compressed, float formats.
    @Test
    public void testFrameSize() throws Exception {
        int[] encodings = {
            AudioFormat.ENCODING_MP3,
            AudioFormat.ENCODING_AAC_LC,
            AudioFormat.ENCODING_AAC_HE_V1,
            AudioFormat.ENCODING_AAC_HE_V2,
            AudioFormat.ENCODING_OPUS,
            AudioFormat.ENCODING_MPEGH_BL_L3,
            AudioFormat.ENCODING_MPEGH_BL_L4,
            AudioFormat.ENCODING_MPEGH_LC_L3,
            AudioFormat.ENCODING_MPEGH_LC_L4,
            AudioFormat.ENCODING_DTS_UHD,
            AudioFormat.ENCODING_DRA,
            AudioFormat.ENCODING_DTS_HD_MA,
            AudioFormat.ENCODING_DTS_UHD_P1,
            AudioFormat.ENCODING_DTS_UHD_P2,
        };
        for (int encoding : encodings) {
            final AudioFormat format = new AudioFormat.Builder()
                .setEncoding(encoding)
                .setSampleRate(44100)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .build();

            assertEquals("AudioFormat with encoding " + encoding + " has the wrong frame size",
                    1, format.getFrameSizeInBytes());
        }

        final AudioFormat formatPcmFloat = new AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setSampleRate(192000)
            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
            .build();

        assertEquals("Float AudioFormat has the wrong frame size",
            2 /* channels */ * 4 /* bytes per sample */, formatPcmFloat.getFrameSizeInBytes());
    }

    // Test case 8: Check setting valid encodings
    @Test
    public void testValidEncodings() throws Exception {
        int[] encodings = {
            AudioFormat.ENCODING_DEFAULT,
            AudioFormat.ENCODING_PCM_16BIT,
            AudioFormat.ENCODING_PCM_8BIT,
            AudioFormat.ENCODING_PCM_FLOAT,
            AudioFormat.ENCODING_AC3,
            AudioFormat.ENCODING_E_AC3,
            AudioFormat.ENCODING_DTS,
            AudioFormat.ENCODING_DTS_HD,
            AudioFormat.ENCODING_MP3,
            AudioFormat.ENCODING_AAC_LC,
            AudioFormat.ENCODING_AAC_HE_V1,
            AudioFormat.ENCODING_AAC_HE_V2,
            AudioFormat.ENCODING_IEC61937,
            AudioFormat.ENCODING_DOLBY_TRUEHD,
            AudioFormat.ENCODING_AAC_ELD,
            AudioFormat.ENCODING_AAC_XHE,
            AudioFormat.ENCODING_AC4,
            AudioFormat.ENCODING_E_AC3_JOC,
            AudioFormat.ENCODING_DOLBY_MAT,
            AudioFormat.ENCODING_OPUS,
            AudioFormat.ENCODING_PCM_24BIT_PACKED,
            AudioFormat.ENCODING_PCM_32BIT,
            AudioFormat.ENCODING_MPEGH_BL_L3,
            AudioFormat.ENCODING_MPEGH_BL_L4,
            AudioFormat.ENCODING_MPEGH_LC_L3,
            AudioFormat.ENCODING_MPEGH_LC_L4,
            AudioFormat.ENCODING_DTS_UHD_P1,
            AudioFormat.ENCODING_DRA,
            AudioFormat.ENCODING_DTS_HD_MA,
            AudioFormat.ENCODING_DTS_UHD_P2,
            AudioFormat.ENCODING_DSD,
        };
        for (int encoding : encodings) {
            final AudioFormat format = new AudioFormat.Builder()
                    .setEncoding(encoding)
                    .setSampleRate(44100)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build();
        }
    }

    /**
     * Check whether the bits in a are all present in b.
     *
     * Used for channel position mask verification.
     */
    private boolean subsetOf(int a, int b) {
        return Integer.bitCount(a ^ b) == Integer.bitCount(b) - Integer.bitCount(a);
    }

    /** Test case 8: Check validity of channel masks */
    @Test
    public void testChannelMasks() throws Exception {
        // Channel count check.
        int[][] maskCount = new int[][] {
                {AudioFormat.CHANNEL_OUT_MONO, 1},
                {AudioFormat.CHANNEL_OUT_STEREO, 2},
                {AudioFormat.CHANNEL_OUT_QUAD, 4},
                {AudioFormat.CHANNEL_OUT_SURROUND, 4},
                {AudioFormat.CHANNEL_OUT_5POINT1, 6},
                {AudioFormat.CHANNEL_OUT_6POINT1, 7},
                {AudioFormat.CHANNEL_OUT_5POINT1POINT2, 8},
                {AudioFormat.CHANNEL_OUT_7POINT1_SURROUND, 8},
                {AudioFormat.CHANNEL_OUT_7POINT1POINT2, 10},
                {AudioFormat.CHANNEL_OUT_5POINT1POINT4, 10},
                {AudioFormat.CHANNEL_OUT_7POINT1POINT2, 10},
                {AudioFormat.CHANNEL_OUT_7POINT1POINT4, 12},
                {AudioFormat.CHANNEL_OUT_13POINT0, 13},
                {AudioFormat.CHANNEL_OUT_9POINT1POINT4, 14},
                {AudioFormat.CHANNEL_OUT_9POINT1POINT6, 16},
                {AudioFormat.CHANNEL_OUT_22POINT2, 24},
        };
        for (int[] pair : maskCount) {
            assertEquals("Mask " + Integer.toHexString(pair[0])
                    + " should have " + pair[1] + " bits set#",
                    /*expected*/ pair[1], /*actual*/ Integer.bitCount(pair[0]));
        }

        // Check channel position masks that are a subset of other masks.
        assertTrue(subsetOf(AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.CHANNEL_OUT_STEREO));
        assertTrue(subsetOf(AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.CHANNEL_OUT_QUAD));
        assertTrue(subsetOf(AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.CHANNEL_OUT_SURROUND));
        assertTrue(subsetOf(AudioFormat.CHANNEL_OUT_QUAD,
                AudioFormat.CHANNEL_OUT_5POINT1));
        assertTrue(subsetOf(AudioFormat.CHANNEL_OUT_5POINT1,
                AudioFormat.CHANNEL_OUT_5POINT1POINT2));
        assertTrue(subsetOf(AudioFormat.CHANNEL_OUT_5POINT1,
                AudioFormat.CHANNEL_OUT_5POINT1POINT4));
        assertTrue(subsetOf(AudioFormat.CHANNEL_OUT_5POINT1,
                AudioFormat.CHANNEL_OUT_6POINT1));
        // Note CHANNEL_OUT_5POINT1POINT2 not a subset of CHANNEL_OUT_5POINT1POINT4
        assertTrue(subsetOf(AudioFormat.CHANNEL_OUT_7POINT1_SURROUND,
                AudioFormat.CHANNEL_OUT_7POINT1POINT2));
        assertTrue(subsetOf(AudioFormat.CHANNEL_OUT_7POINT1_SURROUND,
                AudioFormat.CHANNEL_OUT_7POINT1POINT4));
        // Note CHANNEL_OUT_7POINT1POINT2 not a subset of CHANNEL_OUT_7POINT1POINT4
        assertTrue(subsetOf(AudioFormat.CHANNEL_OUT_5POINT1POINT4,
                AudioFormat.CHANNEL_OUT_7POINT1POINT4));
        assertTrue(subsetOf(AudioFormat.CHANNEL_OUT_7POINT1POINT4,
                AudioFormat.CHANNEL_OUT_22POINT2));
        assertTrue(subsetOf(AudioFormat.CHANNEL_OUT_7POINT1POINT4,
                AudioFormat.CHANNEL_OUT_9POINT1POINT4));
        assertTrue(subsetOf(AudioFormat.CHANNEL_OUT_9POINT1POINT4,
                AudioFormat.CHANNEL_OUT_9POINT1POINT6));
        assertTrue(subsetOf(AudioFormat.CHANNEL_OUT_13POINT0,
                AudioFormat.CHANNEL_OUT_22POINT2));
    }

    /**
     * Test AudioFormat Builder error handling.
     *
     * @throws Exception
     */
    @Test
    public void testAudioFormatBuilderError() throws Exception {
        final int BIGNUM = Integer.MAX_VALUE;

        // Note: setChannelMask() and setChannelIndexMask() are
        // validated when used, i.e. in AudioTrack and AudioRecord.

        assertThrows(IllegalArgumentException.class, () -> {
            new AudioFormat.Builder()
                    .setEncoding(BIGNUM)
                    .build();
        });

        // Sample rate out of bounds. These cases caught in AudioFormat.
        for (int sampleRate : new int[] {
                -BIGNUM,
                -1,
                BIGNUM,
                AudioFormat.SAMPLE_RATE_HZ_MIN - 1,
                AudioFormat.SAMPLE_RATE_HZ_MAX + 1}) {
            assertThrows(IllegalArgumentException.class, () -> {
                new AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .build();
            });
        }
    }

    // -----------------------------------------------------------------
    // ACN Channel Mask Tests
    // ----------------------------------

    // Test case: Verify channel counts for ACN masks
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_AMBISONICS_SUPPORT_API)
    public void testAcnChannelMasks() throws Exception {
        assertEquals(
                "ACN Order 0 should have 1 channel",
                1,
                AudioFormat.channelCountFromAcnChannelMask(AudioFormat.CHANNEL_ACN_ORDER_0));
        assertEquals(
                "ACN Order 1 should have 4 channels",
                4,
                AudioFormat.channelCountFromAcnChannelMask(AudioFormat.CHANNEL_ACN_ORDER_1));
        assertEquals(
                "ACN Order 2 should have 9 channels",
                9,
                AudioFormat.channelCountFromAcnChannelMask(AudioFormat.CHANNEL_ACN_ORDER_2));
        assertEquals(
                "ACN Order 3 should have 16 channels",
                16,
                AudioFormat.channelCountFromAcnChannelMask(AudioFormat.CHANNEL_ACN_ORDER_3));

        // Horizontal-only masks
        assertEquals(
                "ACN Order 0 HRZ should have 1 channel",
                1,
                AudioFormat.channelCountFromAcnChannelMask(AudioFormat.CHANNEL_ACN_ORDER_0_HRZ));
        assertEquals(
                "ACN Order 1 HRZ should have 3 channels",
                3,
                AudioFormat.channelCountFromAcnChannelMask(AudioFormat.CHANNEL_ACN_ORDER_1_HRZ));
        assertEquals(
                "ACN Order 2 HRZ should have 5 channels",
                5,
                AudioFormat.channelCountFromAcnChannelMask(AudioFormat.CHANNEL_ACN_ORDER_2_HRZ));
        assertEquals(
                "ACN Order 3 HRZ should have 7 channels",
                7,
                AudioFormat.channelCountFromAcnChannelMask(AudioFormat.CHANNEL_ACN_ORDER_3_HRZ));
    }

    // Test case: Use Builder to create AudioFormat with ACN mask
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_AMBISONICS_SUPPORT_API)
    public void testBuilderAcnMask() throws Exception {
        final int testAcnMask = AudioFormat.CHANNEL_ACN_ORDER_1; // 4 channels
        final int testEncoding = AudioFormat.ENCODING_PCM_16BIT;
        final int testSampleRate = 48000;

        final AudioFormat format =
                new AudioFormat.Builder()
                        .setChannelAcnMask(testAcnMask)
                        .setEncoding(testEncoding)
                        .setSampleRate(testSampleRate)
                        .build();

        assertNotNull(format);
        assertEquals("AudioFormat has wrong ACN mask", testAcnMask, format.getChannelAcnMask());
        assertEquals("AudioFormat has wrong encoding", testEncoding, format.getEncoding());
        assertEquals("AudioFormat has wrong sample rate", testSampleRate, format.getSampleRate());
        assertEquals("AudioFormat has wrong channel count", 4, format.getChannelCount());

        // Verify frame size calculation (4 channels * 2 bytes)
        assertEquals("AudioFormat has wrong frame size", 8, format.getFrameSizeInBytes());
    }

    // Test case: Use Builder to duplicate an AudioFormat with ACN mask
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_AMBISONICS_SUPPORT_API)
    public void testBuilderForCopyAcnMask() throws Exception {
        final int testAcnMask = AudioFormat.CHANNEL_ACN_ORDER_2; // 9 channels

        final AudioFormat formatToCopy =
                new AudioFormat.Builder().setChannelAcnMask(testAcnMask).build();
        assertNotNull("Failure to create the AudioFormat to copy", formatToCopy);

        final AudioFormat copiedFormat = new AudioFormat.Builder(formatToCopy).build();
        assertNotNull("Failure to create AudioFormat copy with Builder", copiedFormat);
        assertEquals(
                "Copied AudioFormat has wrong ACN mask",
                testAcnMask,
                copiedFormat.getChannelAcnMask());
        assertEquals(
                "Copied AudioFormat has wrong channel count", 9, copiedFormat.getChannelCount());
    }

    // Test case: create an instance with ACN mask, marshall it and create a new instance
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_AMBISONICS_SUPPORT_API)
    public void testParcelAcnMask() throws Exception {
        final int testAcnMask = AudioFormat.CHANNEL_ACN_ORDER_1; // 4 channels
        final int testEncoding = AudioFormat.ENCODING_PCM_FLOAT;
        final int testSampleRate = 44100;

        final AudioFormat formatToMarshall =
                new AudioFormat.Builder()
                        .setChannelAcnMask(testAcnMask)
                        .setEncoding(testEncoding)
                        .setSampleRate(testSampleRate)
                        .build();
        assertNotNull(formatToMarshall);

        final Parcel srcParcel = Parcel.obtain();
        final Parcel dstParcel = Parcel.obtain();

        formatToMarshall.writeToParcel(srcParcel, 0);
        final byte[] mbytes = srcParcel.marshall();
        dstParcel.unmarshall(mbytes, 0, mbytes.length);
        dstParcel.setDataPosition(0);
        final AudioFormat unmarshalledFormat = AudioFormat.CREATOR.createFromParcel(dstParcel);

        assertNotNull("Failure to unmarshall AudioFormat", unmarshalledFormat);
        assertEquals(
                "Source and destination AudioFormat not equal",
                formatToMarshall,
                unmarshalledFormat);
        assertEquals(
                "Unmarshalled AudioFormat has wrong ACN mask",
                testAcnMask,
                unmarshalledFormat.getChannelAcnMask());
    }

    // Test case: Test AudioFormat Builder error handling for ACN masks
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_AMBISONICS_SUPPORT_API)
    public void testBuilderAcnMaskError() throws Exception {
        // ACN Order 0 has 1 channel
        final int testAcnMask1ch = AudioFormat.CHANNEL_ACN_ORDER_0;
        // Index mask 0x3 has 2 channels (bits 0 and 1 set)
        final int testIndexMask2ch = 0x3;

        // 1. Set ACN first (1ch), then Index (2ch) -> Should fail
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    new AudioFormat.Builder()
                            .setChannelAcnMask(testAcnMask1ch)
                            .setChannelIndexMask(testIndexMask2ch);
                });

        // 2. Set Index first (2ch), then ACN (1ch) -> Should fail
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    new AudioFormat.Builder()
                            .setChannelIndexMask(testIndexMask2ch)
                            .setChannelAcnMask(testAcnMask1ch);
                });

        // 3. Verify compatible masks do not throw
        // Index mask 0x1 has 1 channel, matches ACN Order 0
        final AudioFormat format =
                new AudioFormat.Builder()
                        .setChannelAcnMask(testAcnMask1ch)
                        .setChannelIndexMask(0x1)
                        .build();
        assertNotNull(format);
        assertEquals(testAcnMask1ch, format.getChannelAcnMask());
        assertEquals(0x1, format.getChannelIndexMask());
    }
}
