/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static android.media.codec.Flags.apvSupport;
import static android.mediav2.common.cts.CodecTestBase.SupportClass.CODEC_ALL;

import static com.android.media.extractor.flags.Flags.extractorMp4EnableApv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.mediav2.common.cts.CodecEncoderTestBase;
import android.mediav2.common.cts.EncoderConfigParams;
import android.mediav2.common.cts.OutputManager;
import android.util.Log;
import android.util.Range;

import androidx.test.filters.LargeTest;
import androidx.test.filters.SmallTest;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.CddTest;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Test mediacodec api, encoders and their interactions in bytebuffer mode.
 * <p>
 * The test feeds raw input data (audio/video) to the component and receives compressed bitstream
 * from the component.
 * <p>
 * At the end of encoding process, the test enforces following checks :-
 * <ul>
 *     <li> For audio components, the test expects the output timestamps to be strictly
 *     increasing.</li>
 *     <li>For video components the test expects the output frame count to be identical to input
 *     frame count and the output timestamp list to be identical to input timestamp list.</li>
 *     <li>As encoders are expected to give consistent output for a given input and configuration
 *     parameters, the test checks for consistency across runs. For now, this attribute is not
 *     strictly enforced in this test.</li>
 * </ul>
 * <p>
 * The test does not validate the integrity of the encoder output. That is done by
 * CodecEncoderValidationTest. This test checks only the framework <-> plugin <-> encoder
 * interactions.
 * <p>
 * The test runs mediacodec in synchronous and asynchronous mode.
 */
@RunWith(Parameterized.class)
public class CodecEncoderTest extends CodecEncoderTestBase {
    private static final String LOG_TAG = CodecEncoderTest.class.getSimpleName();

    private boolean mGotCSD;
    private final int mFrameLimit;

    static {
        System.loadLibrary("ctsmediav2codecenc_jni");
    }

    public CodecEncoderTest(String encoder, String mediaType, EncoderConfigParams[] cfgParams,
            String allTestParams) {
        super(encoder, mediaType, cfgParams, allTestParams);
        mFrameLimit = Math.max(cfgParams[0].mFrameRate, 30);
    }

    @Override
    protected void resetContext(boolean isAsync, boolean signalEOSWithLastFrame) {
        super.resetContext(isAsync, signalEOSWithLastFrame);
        mGotCSD = false;
    }

    @Override
    protected void dequeueOutput(int bufferIndex, MediaCodec.BufferInfo info) {
        if (info.size > 0 && ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0)) {
            mGotCSD = true;
        }
        super.dequeueOutput(bufferIndex, info);
    }

    private static EncoderConfigParams getVideoEncoderCfgParam(String mediaType, int width,
            int height, int bitRate) {
        return new EncoderConfigParams.Builder(mediaType).setWidth(width).setHeight(height)
                .setBitRate(bitRate).build();
    }

    private static EncoderConfigParams getAudioEncoderCfgParam(String mediaType, int sampleRate,
            int channelCount, int qualityPreset) {
        EncoderConfigParams.Builder foreman =
                new EncoderConfigParams.Builder(mediaType).setSampleRate(sampleRate)
                        .setChannelCount(channelCount);
        if (mediaType.equals(MediaFormat.MIMETYPE_AUDIO_FLAC)) {
            foreman = foreman.setCompressionLevel(qualityPreset);
        } else {
            foreman = foreman.setBitRate(qualityPreset);
        }
        return foreman.build();
    }

    private static EncoderConfigParams[] getAudioCfgParams(String codecName, String mediaType) {
        EncoderConfigParams[] params = new EncoderConfigParams[2];
        MediaCodecInfo.CodecCapabilities caps = getCodecCapabilities(codecName, mediaType);
        MediaCodecInfo.AudioCapabilities audioCaps = caps.getAudioCapabilities();
        Range<Integer>[] sampleRateRanges = audioCaps.getSupportedSampleRateRanges();
        Range<Integer> qualityPresets = mediaType.equals(MediaFormat.MIMETYPE_AUDIO_FLAC)
                ? caps.getEncoderCapabilities().getComplexityRange() : audioCaps.getBitrateRange();
        params[0] = getAudioEncoderCfgParam(mediaType, sampleRateRanges[0].getLower(),
                audioCaps.getMinInputChannelCount(), qualityPresets.getLower());
        params[1] = getAudioEncoderCfgParam(mediaType,
                sampleRateRanges[sampleRateRanges.length - 1].getUpper(),
                audioCaps.getMaxInputChannelCount(), qualityPresets.getUpper());
        return params;
    }

    private static Range<Integer> getSDBitrateRange(String mediaType) {
        Range<Integer> bitratesSD;
        if (mediaType.equals(MediaFormat.MIMETYPE_VIDEO_MPEG2)
                || mediaType.equals(MediaFormat.MIMETYPE_VIDEO_MPEG4)
                || mediaType.equals(MediaFormat.MIMETYPE_VIDEO_H263)) {
            // For low bitrate codecs: 32 kbps to 3 Mbps
            bitratesSD = new Range<>(32000, 3000000);
        } else if (mediaType.equals(MediaFormat.MIMETYPE_VIDEO_APV)) {
            // For high bitrate codecs: 1 Mbps to 8 Mbps
            bitratesSD = new Range<>(1000000, 8000000);
        } else {
            // Default for other codecs: 512 kbps to 3 Mbps
            bitratesSD = new Range<>(512000, 3000000);
        }
        return bitratesSD;
    }

    private static EncoderConfigParams[] getVideoCfgParams(String codecName, String mediaType) {
        EncoderConfigParams[] params = new EncoderConfigParams[2];
        Range<Integer> widthsSD = new Range<>(176, 720); // SD width range
        Range<Integer> heightsSD = new Range<>(144, 576); // SD height range
        Range<Integer> bitratesSD = getSDBitrateRange(mediaType); // SD bitrate range
        MediaCodecInfo.CodecCapabilities caps = getCodecCapabilities(codecName, mediaType);
        MediaCodecInfo.VideoCapabilities videoCaps = caps.getVideoCapabilities();
        Range<Integer> widthRange = videoCaps.getSupportedWidths();
        widthRange = widthRange.intersect(widthsSD);
        Range<Integer> heightRange = videoCaps.getSupportedHeights();
        heightRange = heightRange.intersect(heightsSD);
        Range<Integer> bitrateRange = videoCaps.getBitrateRange();
        bitrateRange = bitrateRange.intersect(bitratesSD);
        // These codecs allow crop attributes in their CSD. So they are expected to support any
        // resolution.
        if (mediaType.equals(MediaFormat.MIMETYPE_VIDEO_AVC)
                || mediaType.equals(MediaFormat.MIMETYPE_VIDEO_HEVC)) {
            if (widthRange.getLower() != 176) {
                widthRange = new Range<>(176, widthRange.getUpper());
            }
            if (heightRange.getLower() != 144) {
                heightRange = new Range<>(144, heightRange.getUpper());
            }
        }
        params[0] = getVideoEncoderCfgParam(mediaType, widthRange.getLower(),
                heightRange.getLower(), bitrateRange.getLower());
        params[1] = getVideoEncoderCfgParam(mediaType, widthRange.getUpper(),
                heightRange.getUpper(), bitrateRange.getUpper());
        return params;
    }

    private static List<Object[]> flattenParams(List<Object[]> params) {
        List<Object[]> argsList = new ArrayList<>();
        for (Object[] param : params) {
            Object[] testArgs = new Object[param.length + 1];
            String codecName = (String) param[0];
            String mediaType = (String) param[1];
            testArgs[0] = codecName;
            testArgs[1] = mediaType;
            testArgs[2] = mediaType.startsWith("audio/") ? getAudioCfgParams(codecName, mediaType)
                    : getVideoCfgParams(codecName, mediaType);
            testArgs[3] = paramToString(testArgs);
            argsList.add(testArgs);
        }
        return argsList;
    }

    @Parameterized.Parameters(name = "{index}_{0}_{1}")
    public static Collection<Object[]> input() {
        final boolean isEncoder = true;
        final boolean needAudio = true;
        final boolean needVideo = true;
        final List<Object[]> exhaustiveArgsList = new ArrayList<>(Arrays.asList(new Object[][]{
                // mediaType
                {MediaFormat.MIMETYPE_AUDIO_AAC},
                {MediaFormat.MIMETYPE_AUDIO_OPUS},
                {MediaFormat.MIMETYPE_AUDIO_AMR_NB},
                {MediaFormat.MIMETYPE_AUDIO_AMR_WB},
                {MediaFormat.MIMETYPE_AUDIO_FLAC},
                {MediaFormat.MIMETYPE_VIDEO_H263},
                {MediaFormat.MIMETYPE_VIDEO_MPEG4},
                {MediaFormat.MIMETYPE_VIDEO_AVC},
                {MediaFormat.MIMETYPE_VIDEO_HEVC},
                {MediaFormat.MIMETYPE_VIDEO_VP8},
                {MediaFormat.MIMETYPE_VIDEO_VP9},
                {MediaFormat.MIMETYPE_VIDEO_AV1},
        }));

        if (IS_AT_LEAST_B && apvSupport() && extractorMp4EnableApv()) {
            exhaustiveArgsList.addAll(Arrays.asList(new Object[][]{
                    {MediaFormat.MIMETYPE_VIDEO_APV},
            }));
        }
        return flattenParams(
                prepareParamList(exhaustiveArgsList, isEncoder, needAudio, needVideo, true));
    }

    @Before
    public void setUp() throws IOException {
        mActiveEncCfg = mEncCfgParams[0];
        MediaFormat format = mActiveEncCfg.getFormat();
        ArrayList<MediaFormat> formatList = new ArrayList<>();
        formatList.add(format);
        checkFormatSupport(mCodecName, mMediaType, true, formatList, null, CODEC_ALL);
        mActiveRawRes = EncoderInput.getRawResource(mActiveEncCfg);
        assertNotNull("no raw resource found for testing config : " + mActiveEncCfg + mTestConfig
                + mTestEnv, mActiveRawRes);
    }

    private void validateCSD() {
        boolean requireCSD = false;
        if (mMediaType.equals(MediaFormat.MIMETYPE_VIDEO_VP9)) {
            if (!IS_AT_LEAST_V) {
                assertFalse("components that support mediaType: " + mMediaType
                        + " must not generate CodecPrivateData before Android V\n"
                        + mTestConfig + mTestEnv, mGotCSD);
            } else if (BOARD_FIRST_SDK_IS_AT_LEAST_202404) {
                // For devices launching with Android V, CSD is mandated for VP9 encoders
                requireCSD = true;
            } else {
                // For devices upgrading to Android V, CSD is not mandated for VP9 encoders
                requireCSD = false;
            }
        } else if (mMediaType.equals(MediaFormat.MIMETYPE_AUDIO_AAC)
                || mMediaType.equals(MediaFormat.MIMETYPE_AUDIO_OPUS)
                || mMediaType.equals(MediaFormat.MIMETYPE_AUDIO_FLAC)
                || mMediaType.equals(MediaFormat.MIMETYPE_VIDEO_MPEG4)
                || mMediaType.equals(MediaFormat.MIMETYPE_VIDEO_AVC)
                || mMediaType.equals(MediaFormat.MIMETYPE_VIDEO_HEVC)) {
            requireCSD = true;
        } else if (IS_AT_LEAST_B
                && apvSupport()
                && extractorMp4EnableApv()
                && mMediaType.equals(MediaFormat.MIMETYPE_VIDEO_APV)) {
            requireCSD = true;
        }

        if (requireCSD) {
            assertTrue("components that support mediaType: " + mMediaType
                    + " must generate CodecPrivateData \n" + mTestConfig + mTestEnv, mGotCSD);
        }
    }

    /**
     * Checks if the component under test can encode the test file correctly. The encoding
     * happens in synchronous, asynchronous mode, eos flag signalled with last raw frame and
     * eos flag signalled separately after sending all raw frames. It expects consistent
     * output in all these runs. That is, the ByteBuffer info and output timestamp list has to be
     * same in all the runs. Further for audio, the output timestamp has to be strictly
     * increasing. For video the output timestamp list has to be same as input timestamp list. As
     * encoders are expected to give consistent output for a given input and configuration
     * parameters, the test checks for consistency across runs. Although the test collects the
     * output in a byte buffer, no analysis is done that checks the integrity of the bitstream.
     */
    @CddTest(requirements = {"2.2.2", "2.3.2", "2.5.2", "5.1.1/C-1-2", "5.1.1/C-1-3", "5.2/C-1-1",
            "5.2.4/C-1-3"})
    @ApiTest(apis = {"android.media.MediaCodecInfo.CodecCapabilities#COLOR_FormatYUV420Flexible",
            "android.media.AudioFormat#ENCODING_PCM_16BIT"})
    @LargeTest
    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testSimpleEncode() throws IOException, InterruptedException {
        boolean[] boolStates = {true, false};
        setUpSource(mActiveRawRes.mFileName);
        OutputManager ref = new OutputManager();
        OutputManager test = new OutputManager(ref.getSharedErrorLogs());
        {
            mCodec = MediaCodec.createByCodecName(mCodecName);
            assertEquals("codec name act/got: " + mCodec.getName() + '/' + mCodecName,
                    mCodec.getName(), mCodecName);
            assertTrue("error! codec canonical name is null or empty",
                    mCodec.getCanonicalName() != null && !mCodec.getCanonicalName().isEmpty());
            mSaveToMem = true;
            MediaFormat format = mActiveEncCfg.getFormat();
            {
                int loopCounter = 0;
                for (boolean eosType : boolStates) {
                    for (boolean isAsync : boolStates) {
                        mOutputBuff = loopCounter == 0 ? ref : test;
                        mOutputBuff.reset();
                        mInfoList.clear();
                        validateMetrics(mCodecName);
                        configureCodec(format, isAsync, eosType, true);
                        mCodec.start();
                        doWork(mFrameLimit);
                        queueEOS();
                        waitForAllOutputs();
                        validateMetrics(mCodecName, format);
                        validateCSD();
                        /* TODO(b/147348711) */
                        if (false) mCodec.stop();
                        else mCodec.reset();
                        if (loopCounter != 0 && !ref.equals(test)) {
                            // TODO(b/456805087) if inconsistent, check for perceptual similarity
                            Log.e(LOG_TAG, "Encoder output is not consistent across runs\n"
                                    + test.getErrMsg() + mTestConfig + mTestEnv);
                        }
                        loopCounter++;
                    }
                }
            }
            mCodec.release();
        }
    }

    private native boolean nativeTestSimpleEncode(String encoder, String file, int inpWidth,
            int inpHeight, String mediaType, String cfgParams, String separator,
            StringBuilder retMsg, int frameLimit);

    /**
     * Test is similar to {@link #testSimpleEncode()} but uses ndk api
     */
    @CddTest(requirements = {"2.2.2", "2.3.2", "2.5.2", "5.1.1/C-1-2", "5.1.1/C-1-3",
            "5.1.7/C-1-3"})
    @ApiTest(apis = {"android.media.MediaCodecInfo.CodecCapabilities#COLOR_FormatYUV420SemiPlanar",
            "android.media.MediaCodecInfo.CodecCapabilities#COLOR_FormatYUV420Planar",
            "android.media.AudioFormat#ENCODING_PCM_16BIT"})
    @LargeTest
    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testSimpleEncodeNative() throws IOException, CloneNotSupportedException {
        MediaFormat format = mActiveEncCfg.getFormat();
        if (mIsVideo) {
            int colorFormat = findByteBufferColorFormat(mCodecName, mMediaType);
            assertTrue("no valid color formats received \n" + mTestConfig + mTestEnv,
                    colorFormat != -1);
            format = mActiveEncCfg.getBuilder().setColorFormat(colorFormat).build().getFormat();
        }
        boolean isPass = nativeTestSimpleEncode(mCodecName, mActiveRawRes.mFileName,
                mActiveRawRes.mWidth, mActiveRawRes.mHeight, mMediaType,
                EncoderConfigParams.serializeMediaFormat(format),
                EncoderConfigParams.TOKEN_SEPARATOR, mTestConfig, mFrameLimit);
        assertTrue(mTestConfig.toString(), isPass);
    }

    /**
     * Checks component and framework behaviour on parameter (resolution, samplerate/channel
     * count, ...) change. The reconfiguring of media codec component happens at various points.
     * <ul>
     *     <li>After initial configuration (stopped state).</li>
     *     <li>In running state, before queueing any input.</li>
     *     <li>In running state, after queueing n frames.</li>
     *     <li>In eos state.</li>
     * </ul>
     * In eos state,
     * <ul>
     *     <li>reconfigure with same clip.</li>
     *     <li>reconfigure with different clip (different resolution).</li>
     * </ul>
     * <p>
     * In all situations (pre-reconfigure or post-reconfigure), the test expects the output
     * timestamps to be strictly increasing. The reconfigure call makes the output received
     * non-deterministic even for a given input. Hence, besides timestamp checks, no additional
     * validation is done for outputs received before reconfigure. Post reconfigure, the encode
     * begins from a sync frame. So the test expects consistent output and this needs to be
     * identical to the reference.
     * <p>
     * The test runs mediacodec in synchronous and asynchronous mode.
     * <p>
     * During reconfiguration, the mode of operation is toggled. That is, if first configure
     * operates the codec in sync mode, then next configure operates the codec in async mode and
     * so on.
     */
    @Ignore("TODO(b/148523403)")
    @ApiTest(apis = {"android.media.MediaCodec#configure"})
    @LargeTest
    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testReconfigure() throws IOException, InterruptedException {
        ArrayList<MediaFormat> formatList = new ArrayList<>();
        formatList.add(mEncCfgParams[0].getFormat());
        formatList.add(mEncCfgParams[1].getFormat());
        checkFormatSupport(mCodecName, mMediaType, true, formatList, null, CODEC_ALL);
        boolean[] boolStates = {true, false};
        {
            boolean saveToMem = true;
            OutputManager configRef = null;
            OutputManager configTest = null;
            if (mEncCfgParams.length > 1) {
                encodeToMemory(mCodecName, mEncCfgParams[1], mActiveRawRes, mFrameLimit,
                        saveToMem, mMuxOutput);
                configRef = mOutputBuff;
                configTest = new OutputManager(configRef.getSharedErrorLogs());
            }
            encodeToMemory(mCodecName, mEncCfgParams[0], mActiveRawRes, mFrameLimit,
                    saveToMem, mMuxOutput);
            OutputManager ref = mOutputBuff;
            OutputManager test = new OutputManager(ref.getSharedErrorLogs());
            MediaFormat format = mEncCfgParams[0].getFormat();
            mCodec = MediaCodec.createByCodecName(mCodecName);
            for (boolean isAsync : boolStates) {
                mOutputBuff = test;
                configureCodec(format, isAsync, true, true);

                /* test reconfigure in stopped state */
                reConfigureCodec(format, !isAsync, false, true);
                mCodec.start();

                /* test reconfigure in running state before queuing input */
                reConfigureCodec(format, !isAsync, false, true);
                mCodec.start();
                doWork(23);

                if (mOutputCount != 0) validateMetrics(mCodecName, format);

                /* test reconfigure codec in running state */
                reConfigureCodec(format, isAsync, true, true);
                mCodec.start();
                mSaveToMem = saveToMem;
                test.reset();
                doWork(mFrameLimit);
                queueEOS();
                waitForAllOutputs();
                /* TODO(b/147348711) */
                if (false) mCodec.stop();
                else mCodec.reset();
                if (!ref.equals(test)) {
                    Log.e(LOG_TAG, "Encoder output is not consistent across runs\n"
                            + test.getErrMsg() + mTestConfig + mTestEnv);
                }

                /* test reconfigure codec at eos state */
                reConfigureCodec(format, !isAsync, false, true);
                mCodec.start();
                test.reset();
                doWork(mFrameLimit);
                queueEOS();
                waitForAllOutputs();
                /* TODO(b/147348711) */
                if (false) mCodec.stop();
                else mCodec.reset();
                if (!ref.equals(test)) {
                    Log.e(LOG_TAG, "Encoder output is not consistent across runs\n"
                            + test.getErrMsg() + mTestConfig + mTestEnv);
                }

                /* test reconfigure codec for new format */
                if (mEncCfgParams.length > 1) {
                    mOutputBuff = configTest;
                    reConfigureCodec(mEncCfgParams[1].getFormat(), isAsync, false, true);
                    mCodec.start();
                    configTest.reset();
                    doWork(mFrameLimit);
                    queueEOS();
                    waitForAllOutputs();
                    /* TODO(b/147348711) */
                    if (false) mCodec.stop();
                    else mCodec.reset();
                    if (!configRef.equals(configTest)) {
                        Log.e(LOG_TAG, "Encoder output is not consistent across runs\n"
                                + configTest.getErrMsg() + mTestConfig + mTestEnv);
                    }
                }
                mSaveToMem = false;
            }
            mCodec.release();
        }
    }

    private native boolean nativeTestReconfigure(String encoder, String file, int inpWidth,
            int inpHeight, String mediaType, String cfgParams, String cfgReconfigParams,
            String separator, StringBuilder retMsg, int frameLimit);

    /**
     * Test is similar to {@link #testReconfigure()} but uses ndk api
     */
    @Ignore("TODO(b/147348711, b/149981033)")
    @ApiTest(apis = {"android.media.MediaCodec#configure"})
    @LargeTest
    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testReconfigureNative() throws IOException, CloneNotSupportedException {
        MediaFormat format = mEncCfgParams[0].getFormat();
        MediaFormat reconfigFormat = mEncCfgParams.length > 1 ? mEncCfgParams[1].getFormat() : null;
        if (mIsVideo) {
            int colorFormat = findByteBufferColorFormat(mCodecName, mMediaType);
            assertTrue("no valid color formats received \n" + mTestConfig + mTestEnv,
                    colorFormat != -1);
            format = mEncCfgParams[0].getBuilder().setColorFormat(colorFormat).build().getFormat();
            if (mEncCfgParams.length > 1) {
                reconfigFormat = mEncCfgParams[1].getBuilder().setColorFormat(colorFormat).build()
                        .getFormat();
            }
        }
        boolean isPass = nativeTestReconfigure(mCodecName, mActiveRawRes.mFileName,
                mActiveRawRes.mWidth, mActiveRawRes.mHeight, mMediaType,
                EncoderConfigParams.serializeMediaFormat(format), reconfigFormat == null ? null :
                        EncoderConfigParams.serializeMediaFormat(reconfigFormat),
                EncoderConfigParams.TOKEN_SEPARATOR, mTestConfig, mFrameLimit);
        assertTrue(mTestConfig.toString(), isPass);
    }

    /**
     * Test encoder for EOS only input. As BUFFER_FLAG_END_OF_STREAM is queued with an input buffer
     * of size 0, during dequeue the test expects to receive BUFFER_FLAG_END_OF_STREAM with an
     * output buffer of size 0. No input is given, so no output shall be received.
     */
    @ApiTest(apis = "android.media.MediaCodec#BUFFER_FLAG_END_OF_STREAM")
    @SmallTest
    @Test(timeout = PER_TEST_TIMEOUT_SMALL_TEST_MS)
    public void testOnlyEos() throws IOException, InterruptedException {
        boolean[] boolStates = {true, false};
        OutputManager ref = new OutputManager();
        OutputManager test = new OutputManager(ref.getSharedErrorLogs());
        {
            mCodec = MediaCodec.createByCodecName(mCodecName);
            mSaveToMem = true;
            int loopCounter = 0;
            MediaFormat format = mActiveEncCfg.getFormat();
            for (boolean isAsync : boolStates) {
                configureCodec(format, isAsync, false, true);
                mOutputBuff = loopCounter == 0 ? ref : test;
                mOutputBuff.reset();
                mInfoList.clear();
                mCodec.start();
                queueEOS();
                waitForAllOutputs();
                /* TODO(b/147348711) */
                if (false) mCodec.stop();
                else mCodec.reset();
                if (loopCounter != 0 && !ref.equals(test)) {
                    fail("Encoder output is not consistent across runs\n" + mTestConfig
                            + mTestEnv + test.getErrMsg());
                }
                loopCounter++;
            }
            mCodec.release();
        }
    }

    private native boolean nativeTestOnlyEos(String encoder, String mediaType, String cfgParams,
            String separator, StringBuilder retMsg);

    /**
     * Test is similar to {@link #testOnlyEos()} but uses ndk api
     */
    @ApiTest(apis = "android.media.MediaCodec#BUFFER_FLAG_END_OF_STREAM")
    @SmallTest
    @Test(timeout = PER_TEST_TIMEOUT_SMALL_TEST_MS)
    public void testOnlyEosNative() throws IOException, CloneNotSupportedException {
        MediaFormat format = mActiveEncCfg.getFormat();
        if (mIsVideo) {
            int colorFormat = findByteBufferColorFormat(mCodecName, mMediaType);
            assertTrue("no valid color formats received \n" + mTestConfig + mTestEnv,
                    colorFormat != -1);
            format = mActiveEncCfg.getBuilder().setColorFormat(colorFormat).build().getFormat();
        }
        boolean isPass = nativeTestOnlyEos(mCodecName, mMediaType,
                EncoderConfigParams.serializeMediaFormat(format),
                EncoderConfigParams.TOKEN_SEPARATOR, mTestConfig);
        assertTrue(mTestConfig.toString(), isPass);
    }
}
