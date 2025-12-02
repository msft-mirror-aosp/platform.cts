/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static android.media.MediaCodecInfo.CodecCapabilities.COLOR_Format32bitABGR2101010;
import static android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface;
import static android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible;
import static android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatYUVP010;
import static android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM;
import static android.media.codec.Flags.apvSupport;
import static android.mediav2.common.cts.CodecTestBase.SupportClass.CODEC_OPTIONAL;
import static android.mediav2.common.cts.MuxerUtils.getMuxerFormatsListForMediaType;
import static android.mediav2.common.cts.MuxerUtils.getTempFilePath;
import static android.mediav2.common.cts.MuxerUtils.muxOutput;
import static android.mediav2.common.cts.MuxerUtils.muxerFormatToString;

import static com.android.media.extractor.flags.Flags.extractorMp4EnableApv;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.hardware.DataSpace;
import android.media.Image;
import android.media.ImageWriter;
import android.media.MediaCodec;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.mediav2.common.cts.CodecDecoderTestBase;
import android.mediav2.common.cts.CodecEncoderTestBase;
import android.mediav2.common.cts.CodecTestBase;
import android.mediav2.common.cts.EncoderConfigParams;
import android.mediav2.common.cts.OutputManager;
import android.util.Log;
import android.util.Pair;
import android.view.Surface;

import androidx.test.filters.SmallTest;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.MediaUtils;

import org.junit.After;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

/**
 * Color Primaries, Color Standard and Color Transfer are essential information to display the
 * decoded YUV on an RGB display accurately. These 3 parameters can be signalled via containers
 * (mp4, mkv, ...) and some video standards also allow signalling this information in elementary
 * stream. Avc, Hevc, Av1, ... allow signalling this information in elementary stream, vpx relies
 * on webm/mkv or some other container for signalling.
 * <p>
 * If the encoder is configured with color aspects, then it is expected to place this information
 * in the elementary stream as-is if possible. The same goes for container as well. The test
 * validates this.
 * <p>
 * Hybrid log gamma transfer characteristics are applicable for high bit depth profiles. Standard
 * gamma curve characteristics are applicable for standard dynamic ranges. The test doesn't
 * exhaustively try all combinations of primaries, standard, transfer on all encoding profiles.
 * SDR specific characteristics are restricted to sdr profiles and HLG/HDR specific profiles are
 * restricted to HLG/HDR profiles.
 */
@RunWith(Parameterized.class)
public class EncoderColorAspectsTest extends CodecEncoderTestBase {
    private static final String LOG_TAG = EncoderColorAspectsTest.class.getSimpleName();
    private static final ArrayList<String> IGNORE_COLOR_BOX_LIST = new ArrayList<>();

    private Surface mInpSurface;
    private ImageWriter mImgWriter;

    private int mLatency;
    private boolean mReviseLatency;
    private final ArrayList<String> mTmpFiles = new ArrayList<>();

    static {
        IGNORE_COLOR_BOX_LIST.add(MediaFormat.MIMETYPE_VIDEO_AV1);
        IGNORE_COLOR_BOX_LIST.add(MediaFormat.MIMETYPE_VIDEO_AVC);
        IGNORE_COLOR_BOX_LIST.add(MediaFormat.MIMETYPE_VIDEO_HEVC);
        IGNORE_COLOR_BOX_LIST.add(MediaFormat.MIMETYPE_VIDEO_APV);
    }

    @After
    public void tearDown() {
        for (String tmpFile : mTmpFiles) {
            File tmp = new File(tmpFile);
            if (tmp.exists()) assertTrue("unable to delete file " + tmpFile, tmp.delete());
        }
        mTmpFiles.clear();
    }

    public EncoderColorAspectsTest(String encoder, String mediaType,
            EncoderConfigParams encCfgParams, @SuppressWarnings("unused") String testLabel,
            String allTestParams) {
        super(encoder, mediaType, new EncoderConfigParams[]{encCfgParams}, allTestParams);
        mLatency = encCfgParams.mMaxBFrames;
    }

    private static void prepareArgsList(List<Object[]> exhaustiveArgsList, List<String> mediaTypes,
            int[] ranges, int[] standards, int[] transfers, int colorFormat, int bitDepth) {
        // Assuming all combinations are supported by the standard which is true for AVC, HEVC, AV1,
        // VP8 and VP9.
        for (String mediaType : mediaTypes) {
            for (int range : ranges) {
                for (int standard : standards) {
                    for (int transfer : transfers) {
                        Object[] testArgs = new Object[3];
                        testArgs[0] = mediaType;
                        EncoderConfigParams.Builder foreman =
                                new EncoderConfigParams.Builder(mediaType)
                                        .setRange(range)
                                        .setStandard(standard)
                                        .setTransfer(transfer)
                                        .setColorFormat(colorFormat)
                                        .setInputBitDepth(bitDepth);
                        if ((colorFormat == COLOR_FormatSurface && bitDepth == 10)
                                || colorFormat == COLOR_FormatYUVP010) {
                            foreman.setProfile(
                                    Objects.requireNonNull(PROFILE_HLG_MAP.get(mediaType))[0]);
                        }
                        EncoderConfigParams cfg = foreman.build();
                        testArgs[1] = cfg;
                        testArgs[2] = String.format("%s_%s_%s_%s",
                                rangeToString(range),
                                colorStandardToString(standard),
                                colorTransferToString(transfer),
                                colorFormatToString(colorFormat, bitDepth));
                        exhaustiveArgsList.add(testArgs);
                    }
                }
            }
        }
    }

    @Parameterized.Parameters(name = "{index}_{0}_{1}_{3}")
    public static Collection<Object[]> input() {
        final boolean isEncoder = true;
        final boolean needAudio = false;
        final boolean needVideo = true;

        List<Object[]> exhaustiveArgsList = new ArrayList<>();

        List<String> mediaTypes = new ArrayList<>(Arrays.asList(
                MediaFormat.MIMETYPE_VIDEO_AV1,
                MediaFormat.MIMETYPE_VIDEO_AVC,
                MediaFormat.MIMETYPE_VIDEO_HEVC,
                MediaFormat.MIMETYPE_VIDEO_VP8,
                MediaFormat.MIMETYPE_VIDEO_VP9
        ));
        // ColorAspects for SDR profiles
        int[] ranges = {-1,
                UNSPECIFIED,
                MediaFormat.COLOR_RANGE_FULL,
                MediaFormat.COLOR_RANGE_LIMITED};
        int[] standards = {-1,
                UNSPECIFIED,
                MediaFormat.COLOR_STANDARD_BT709,
                MediaFormat.COLOR_STANDARD_BT601_PAL,
                MediaFormat.COLOR_STANDARD_BT601_NTSC};
        int[] transfers = {-1,
                UNSPECIFIED,
                MediaFormat.COLOR_TRANSFER_LINEAR,
                MediaFormat.COLOR_TRANSFER_SDR_VIDEO};

        prepareArgsList(exhaustiveArgsList, mediaTypes, ranges, standards, transfers,
                COLOR_FormatYUV420Flexible, -1);
        prepareArgsList(exhaustiveArgsList, mediaTypes, ranges, standards, transfers,
                COLOR_FormatSurface, 8);
        // P010 support was added in Android T, hence limit the following tests to Android T and
        // above
        if (IS_AT_LEAST_T) {
            // ColorAspects for HDR profiles
            List<String> mediaTypesHighBitDepth = new ArrayList<>(Arrays.asList(
                    MediaFormat.MIMETYPE_VIDEO_AV1,
                    MediaFormat.MIMETYPE_VIDEO_AVC,
                    MediaFormat.MIMETYPE_VIDEO_HEVC,
                    MediaFormat.MIMETYPE_VIDEO_VP9
            ));
            if (IS_AT_LEAST_B && apvSupport() && extractorMp4EnableApv()) {
                mediaTypesHighBitDepth.add(MediaFormat.MIMETYPE_VIDEO_APV);
            }
            int[] standardsHighBitDepth = {-1,
                    UNSPECIFIED,
                    MediaFormat.COLOR_STANDARD_BT709,
                    MediaFormat.COLOR_STANDARD_BT2020};
            int[] transfersHighBitDepth = {-1,
                    UNSPECIFIED,
                    MediaFormat.COLOR_TRANSFER_HLG,
                    MediaFormat.COLOR_TRANSFER_ST2084};

            prepareArgsList(exhaustiveArgsList, mediaTypesHighBitDepth, ranges,
                    standardsHighBitDepth, transfersHighBitDepth, COLOR_FormatYUVP010, -1);
            if (IS_AFTER_B) {
                prepareArgsList(exhaustiveArgsList, mediaTypesHighBitDepth, ranges,
                        standardsHighBitDepth, transfersHighBitDepth, COLOR_FormatSurface, 10);
            }
        }
        return prepareParamList(exhaustiveArgsList, isEncoder, needAudio, needVideo, false);
    }

    private void tryEncoderOutput() throws InterruptedException {
        if (!mAsyncHandle.hasSeenError() && !mSawOutputEOS) {
            while (mReviseLatency) {
                mAsyncHandle.waitOnFormatChange();
                mReviseLatency = false;
                int actualLatency = mAsyncHandle.getOutputFormat()
                        .getInteger(MediaFormat.KEY_LATENCY, mLatency);
                if (mLatency < actualLatency) {
                    mLatency = actualLatency;
                    return;
                }
            }
            Pair<Integer, MediaCodec.BufferInfo> element = mAsyncHandle.getOutput();
            if (element != null) {
                dequeueOutput(element.first, element.second);
            }
        }
    }

    protected void queueEOS() throws InterruptedException {
        if (mActiveEncCfg.mColorFormat != COLOR_FormatSurface) {
            super.queueEOS();
        } else {
            if (!mAsyncHandle.hasSeenError() && !mSawInputEOS) {
                mCodec.signalEndOfInputStream();
                mSawInputEOS = true;
                if (ENABLE_LOGS) Log.d(LOG_TAG, "signalled end of stream");
            }
        }
    }

    private void enqueueInput() throws InterruptedException {
        if (mIsLoopBack && mInputBufferReadOffset >= mInputData.length) {
            mInputBufferReadOffset = 0;
        }
        if (mInputBufferReadOffset >= mInputData.length) {
            queueEOS();
        } else {
            long pts = mInputOffsetPts;
            pts += mInputCount * 1000000L / mActiveEncCfg.mFrameRate;
            int size = getVideoFrameSize(mActiveEncCfg.mWidth, mActiveEncCfg.mHeight,
                    mActiveRawRes.mColorFormat);
            int frmSize = getVideoFrameSize(mActiveRawRes.mWidth, mActiveRawRes.mHeight,
                    mActiveRawRes.mColorFormat);
            if (mInputBufferReadOffset + frmSize > mInputData.length) {
                fail("received partial frame to encode \n" + mTestConfig + mTestEnv);
            } else {
                Image img = mImgWriter.dequeueInputImage();
                assertNotNull("mImgWriter.dequeueInputImage() expected to return non-null \n"
                        + mTestConfig + mTestEnv, img);
                fillImage(img);
                img.setTimestamp(pts * 1000);
                mImgWriter.queueInputImage(img);
                mInputBufferReadOffset += frmSize;
                mNumBytesSubmitted += size;
            }
            mOutputBuff.saveInPTS(pts);
            mInputCount++;
        }
    }

    protected void doWork(int frameLimit) throws IOException, InterruptedException {
        if (mActiveEncCfg.mColorFormat != COLOR_FormatSurface) {
            super.doWork(frameLimit);
        } else {
            while (!mAsyncHandle.hasSeenError() && !mSawInputEOS &&
                    mInputCount < frameLimit) {
                if (mInputCount - mOutputCount > mLatency) {
                    tryEncoderOutput();
                }
                enqueueInput();
            }
        }
    }

    /**
     * ColorAspects are passed to the encoder at the time of configuration. The encoder is
     * expected to pass this information to outputFormat() so that muxer can use this information
     * to populate color metadata. If the bitstream is capable of capturing color metadata
     * losslessly then encoder is also expected to use this information during bitstream
     * generation. Although a given media type can be muxed using many containers, the test does
     * not use all available ones. Instead the most preferred one is selected.
     * vpx streams are muxed using webm writer and others are muxed using mp4 writer.
     * Briefly, the test checks OMX/c2 framework, plugins, encoder, muxer ability to SIGNAL color
     * metadata.
     * <p>
     * As muxer is not a mainline module, validating of containers for color aspects is done only
     * in cts runs and skipped in mts runs.
     */
    @ApiTest(apis = {"android.media.MediaFormat#KEY_COLOR_RANGE",
            "android.media.MediaFormat#KEY_COLOR_STANDARD",
            "android.media.MediaFormat#KEY_COLOR_TRANSFER"})
    @SmallTest
    @Test(timeout = PER_TEST_TIMEOUT_SMALL_TEST_MS)
    public void testColorAspects() throws IOException, InterruptedException {
        Assume.assumeTrue("Test introduced with Android 11", IS_AT_LEAST_R);
        /* TODO(b/181126614, b/268175825) */
        Assume.assumeTrue("test skipped due to b/181126614, b/268175825", !MediaUtils.isPc());

        mActiveEncCfg = mEncCfgParams[0];

        if (mActiveEncCfg.mInputBitDepth > 8) {
            // Check if encoder is capable of supporting HDR profiles.
            // Previous check doesn't verify this as profile isn't set in the format
            Assume.assumeTrue(mCodecName + " doesn't support HDR encoding",
                    CodecTestBase.doesCodecSupportHDRProfile(mCodecName, mMediaType));

            // Encoder surface mode tests are to be enabled only if an encoder supports
            // COLOR_Format32bitABGR2101010
            int colorFormat = mActiveEncCfg.mColorFormat == COLOR_FormatSurface ?
                    COLOR_Format32bitABGR2101010 : mActiveEncCfg.mColorFormat;
            Assume.assumeTrue(mCodecName + " doesn't support " + colorFormatToString(colorFormat,
                            mActiveEncCfg.mInputBitDepth),
                    hasSupportForColorFormat(mCodecName, mMediaType, colorFormat));
        }

        ArrayList<MediaFormat> formats = new ArrayList<>();
        formats.add(mActiveEncCfg.getFormat());
        checkFormatSupport(mCodecName, mMediaType, true, formats, null, CODEC_OPTIONAL);

        if (mActiveEncCfg.mColorFormat == COLOR_FormatSurface) {
            Assume.assumeTrue("Surface mode tests are limited to devices launching with Android T",
                    FIRST_SDK_IS_AT_LEAST_T && VNDK_IS_AT_LEAST_T);
            // Few cuttlefish specific color conversion issues were fixed after Android T.
            if (MediaUtils.onCuttlefish()) {
                Assume.assumeTrue("Color conversion related tests are not valid on cuttlefish "
                        + "releases through android T", IS_AT_LEAST_U);
            }
        }
        mActiveRawRes = EncoderInput.getRawResource(mActiveEncCfg);
        assertNotNull("no raw resource found for testing config : " + mActiveEncCfg
                + mTestConfig + mTestEnv, mActiveRawRes);
        setUpSource(mActiveRawRes.mFileName);

        int[] muxerFormats = getMuxerFormatsListForMediaType(mMediaType);
        assertTrue("no muxers available for media Type : " + mMediaType + "\n" + mTestConfig
                        + mTestEnv, muxerFormats.length > 0);

        {
            mSaveToMem = true;
            mOutputBuff = new OutputManager();
            mCodec = MediaCodec.createByCodecName(mCodecName);

            // When in surface mode, encoder needs to be configured in async mode
            boolean isAsync = mActiveEncCfg.mColorFormat == COLOR_FormatSurface;
            configureCodec(mActiveEncCfg.getFormat(), isAsync, true, true);

            if (mActiveEncCfg.mColorFormat == COLOR_FormatSurface) {
                if (mCodec.getInputFormat().containsKey(MediaFormat.KEY_LATENCY)) {
                    mReviseLatency = true;
                    mLatency = mCodec.getInputFormat().getInteger(MediaFormat.KEY_LATENCY);
                }
                mInpSurface = mCodec.createInputSurface();
                assertTrue("Surface is not valid \n" + mTestConfig + mTestEnv,
                        mInpSurface.isValid());
                // HardwareBuffer and PixelFormat formats have same enum values. We can use them
                // as-is in setHardwareBufferFormat() without additional mapping.
                mImgWriter = new ImageWriter.Builder(mInpSurface)
                        .setMaxImages(mLatency + 2)
                        .setHardwareBufferFormat(mActiveRawRes.mColorFormat)
                        .setWidthAndHeight(mActiveEncCfg.mWidth, mActiveEncCfg.mHeight)
                        .setDataSpace(DataSpace.DATASPACE_UNKNOWN).build();
            }
            mCodec.start();
            int frameLimit = 4;
            // WebM writers in pre-U versions suffered from a race condition that MAY result in
            // last few frames to be omitted from the final muxed output. If only 1 to 2 frames were
            // encoded and muxed, it is possible that writer does not write any frame and closes the
            // file. For details refer b/267933226. This was fixed. But, as muxer is not a mainline
            // module, this fix may not be present on older revisions. To avoid failures on older
            // revisions, encode 10 frames and mux.
            if (IS_BEFORE_U && IntStream.of(muxerFormats).anyMatch(x -> x == MUXER_OUTPUT_WEBM)) {
                frameLimit = 10;
            }
            doWork(frameLimit);
            queueEOS();
            waitForAllOutputs();

            if (mImgWriter != null) {
                mImgWriter.close();
                mImgWriter = null;
            }
            if (mInpSurface != null) {
                mInpSurface.release();
                mInpSurface = null;
            }

            // verify if the out fmt contains color aspects as expected
            MediaFormat outFormat = mCodec.getOutputFormat();
            validateColorAspects(outFormat, mActiveEncCfg.mRange, mActiveEncCfg.mStandard,
                    mActiveEncCfg.mTransfer);
            mCodec.stop();
            mCodec.release();

            MediaCodecList codecList = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
            String decoder = codecList.findDecoderForFormat(outFormat);
            assertNotNull("Device advertises support for encoding " + outFormat + " but not "
                    + "decoding it. \n" + mTestConfig + mTestEnv, decoder);

            // write the output with all muxers and verify if the muxed file contains
            // color-aspects as expected
            {
                for (int muxFormat : muxerFormats) {
                    String tmpPath =
                            getTempFilePath((mActiveEncCfg.mInputBitDepth == 10) ? "10bit" : "");
                    mTmpFiles.add(tmpPath);
                    muxOutput(tmpPath, muxFormat, outFormat, mOutputBuff.getBuffer(), mInfoList);
                    String testParamsExtended = String.format(
                            "\nTest Parameters Extended :- [ muxer format: %s, file: %s ]",
                            muxerFormatToString(muxFormat), tmpPath);
                    CodecDecoderTestBase cdtb = new CodecDecoderTestBase(
                            decoder, mMediaType, tmpPath, mAllTestParams + testParamsExtended);
                    cdtb.setUpCodecDecoderTestBase();
                    cdtb.setUpCodecTestBase();
                    cdtb.validateColorAspects(mActiveEncCfg.mRange, mActiveEncCfg.mStandard,
                            mActiveEncCfg.mTransfer, false);

                    // if color metadata can also be signalled via elementary stream then verify
                    // if the elementary stream contains color aspects as expected
                    if (IGNORE_COLOR_BOX_LIST.contains(mMediaType)) {
                        cdtb.validateColorAspects(mActiveEncCfg.mRange, mActiveEncCfg.mStandard,
                                mActiveEncCfg.mTransfer, true);
                    }
                }
            }
        }
    }
}
