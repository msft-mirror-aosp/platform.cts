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

package android.media.decoder.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.Instrumentation;
import android.content.res.AssetFileDescriptor;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.decoder.cts.DecoderTest.AudioParameter;
import android.media.swcodec.flags.Flags;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.util.Log;

import androidx.test.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiLevelUtil;
import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.MediaUtils;
import com.android.compatibility.common.util.Preconditions;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collection;

@AppModeFull(reason = "Instant apps cannot access the SD card")
@RunWith(Parameterized.class)
public class DecoderTestIamf {
    private static final String TAG = "DecoderTestIamf";

    static final String mInpPrefix = WorkDir.getMediaDirString();
    private static final String MIMETYPE_IAMF = MediaFormat.MIMETYPE_AUDIO_IAMF;

    private final String mFilename;
    private final int mChannelMask;
    private final int mExpectedChannelCount;
    private final boolean mBreakOnFormatChange;

    public DecoderTestIamf(String filename, int channelMask, int expectedChannelCount,
            boolean breakOnFormatChange) {
        mFilename = filename;
        mChannelMask = channelMask;
        mExpectedChannelCount = expectedChannelCount;
        mBreakOnFormatChange = breakOnFormatChange;
    }

    @Parameters(name = "{index}: file={0} mask={1} expected={2} break={3}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                { "iamf_7_1_4_opus_no_video.mp4", AudioFormat.CHANNEL_OUT_7POINT1POINT4, 12,
                    true },
                { "iamf_7_1_4_opus_no_video.mp4", AudioFormat.CHANNEL_OUT_STEREO, 2, true },
                { "iamf_7_1_4_opus_no_video.mp4", AudioFormat.CHANNEL_OUT_5POINT1, 6, true },
                { "iamf_7_1_4_opus_no_video.mp4", AudioFormat.CHANNEL_OUT_7POINT1POINT4, 12,
                    false },
                { "iamf_7_1_4_opus_no_video.mp4", AudioFormat.CHANNEL_OUT_STEREO, 2, false },
                { "iamf_7_1_4_opus_no_video.mp4", AudioFormat.CHANNEL_OUT_5POINT1, 6, false }
        });
    }

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setUp() throws Exception {
        final Instrumentation inst = InstrumentationRegistry.getInstrumentation();
        assertNotNull(inst);
    }

    /**
     * Verify correct decoding of IAMF Opus 7.1.4 channel streams.
     * Also verifies that the decoder handles setting the channel mask.
     */
    @Test
    @ApiTest(apis = {"android.media.MediaFormat#KEY_CHANNEL_MASK"})
    @RequiresFlagsEnabled(Flags.FLAG_IAMF_SOFTWARE_DECODER)
    public void testIamfChannelMask() throws Exception {
        int apiLevel = Build.VERSION.SDK_INT;
        if (Build.VERSION.PREVIEW_SDK_INT > 0) {
            apiLevel++;
        }
        Assume.assumeTrue("Test only runs on Android C or later",
                apiLevel >= Build.VERSION_CODES.BAKLAVA + 1);
        AudioParameter audioParams = new AudioParameter();
        decodeUpdateFormat(null /* decoderName */, mFilename, audioParams,
                mChannelMask /* value */,
                MediaFormat.KEY_CHANNEL_MASK,
                mBreakOnFormatChange);

        assertEquals("Number of channels differs",
                mExpectedChannelCount, audioParams.getNumChannels());
        assertEquals("Wrong channel mask",
                mChannelMask,
                audioParams.getChannelMask());
    }

    private void decodeUpdateFormat(String decoderName, final String testInput,
            AudioParameter audioParams, int value,
            String key, boolean breakOnFormatChange)
            throws IOException
    {
        Preconditions.assertTestFileExists(mInpPrefix + testInput);
        File inpFile = new File(mInpPrefix + testInput);
        ParcelFileDescriptor parcelFD =
                ParcelFileDescriptor.open(inpFile, ParcelFileDescriptor.MODE_READ_ONLY);
        AssetFileDescriptor testFd = new AssetFileDescriptor(parcelFD, 0, parcelFD.getStatSize());

        MediaExtractor extractor = new MediaExtractor();
        extractor.setDataSource(testFd.getFileDescriptor(), testFd.getStartOffset(),
                testFd.getLength());
        testFd.close();

        assertEquals("wrong number of tracks", 1, extractor.getTrackCount());
        MediaFormat format = extractor.getTrackFormat(0);
        String mime = format.getString(MediaFormat.KEY_MIME);
        assertTrue("not an IAMF audio file", mime.equals(MIMETYPE_IAMF));

        MediaCodec decoder;
        if (decoderName == null) {
            decoder = MediaCodec.createDecoderByType(mime);
        } else {
            decoder = MediaCodec.createByCodecName(decoderName);
        }

        MediaFormat configFormat = format;
        if (value > 0 && key != null) {
            configFormat.setInteger(key, value);
        }

        Log.v(TAG, "configuring with " + configFormat);
        decoder.configure(configFormat, null /* surface */, null /* crypto */, 0 /* flags */);

        decoder.start();
        ByteBuffer[] codecInputBuffers = decoder.getInputBuffers();
        ByteBuffer[] codecOutputBuffers = decoder.getOutputBuffers();

        extractor.selectTrack(0);

        // start decoding
        final long kTimeOutUs = 5000;
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        boolean sawInputEOS = false;
        boolean sawOutputEOS = false;
        int noOutputCounter = 0;

        while (!sawOutputEOS && noOutputCounter < 50) {
            noOutputCounter++;
            if (!sawInputEOS) {
                int inputBufIndex = decoder.dequeueInputBuffer(kTimeOutUs);

                if (inputBufIndex >= 0) {
                    ByteBuffer dstBuf = codecInputBuffers[inputBufIndex];

                    int sampleSize =
                            extractor.readSampleData(dstBuf, 0 /* offset */);

                    long presentationTimeUs = 0;

                    if (sampleSize < 0) {
                        Log.d(TAG, "saw input EOS.");
                        sawInputEOS = true;
                        sampleSize = 0;
                    } else {
                        presentationTimeUs = extractor.getSampleTime();
                    }
                    decoder.queueInputBuffer(
                            inputBufIndex,
                            0 /* offset */,
                            sampleSize,
                            presentationTimeUs,
                            sawInputEOS ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);

                    if (!sawInputEOS) {
                        extractor.advance();
                    }
                }
            }

            int res = decoder.dequeueOutputBuffer(info, kTimeOutUs);

            if (res >= 0) {
                if (info.size > 0) {
                    noOutputCounter = 0;
                }
                decoder.releaseOutputBuffer(res, false /* render */);

                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    Log.d(TAG, "saw output EOS.");
                    sawOutputEOS = true;
                }
            } else if (res == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                codecOutputBuffers = decoder.getOutputBuffers();
                Log.d(TAG, "output buffers have changed.");
            } else if (res == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                MediaFormat outputFormat = decoder.getOutputFormat();
                try {
                    audioParams.setNumChannels(
                            outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT));
                } catch (NullPointerException e) {
                    fail("KEY_CHANNEL_COUNT not found on output format");
                }
                try {
                    audioParams.setSamplingRate(
                            outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE));
                } catch (NullPointerException e) {
                    fail("KEY_SAMPLE_RATE not found on output format");
                }
                try {
                    audioParams.setChannelMask(
                            outputFormat.getInteger(MediaFormat.KEY_CHANNEL_MASK));
                } catch (NullPointerException e) {
                    fail("KEY_CHANNEL_MASK not found on output format");
                }
                Log.i(TAG, "output format has changed to " + outputFormat);
                if (breakOnFormatChange) {
                    sawOutputEOS = true; // Format found, stop
                }
            } else {
                Log.d(TAG, "dequeueOutputBuffer returned " + res);
            }
        }
        decoder.stop();
        decoder.release();
        extractor.release();
    }
}
