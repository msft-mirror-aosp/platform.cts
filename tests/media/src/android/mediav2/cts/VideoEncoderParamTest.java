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

import static android.media.codec.Flags.apvSupport;
import static android.mediav2.common.cts.CodecTestBase.SupportClass.CODEC_ALL;
import static android.mediav2.common.cts.CodecTestBase.SupportClass.CODEC_ANY;
import static android.mediav2.common.cts.CodecTestBase.SupportClass.CODEC_OPTIONAL;

import static com.android.media.extractor.flags.Flags.extractorMp4EnableApv;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.mediav2.common.cts.CodecEncoderTestBase;
import android.mediav2.common.cts.EncoderConfigParams;
import android.mediav2.common.cts.OutputManager;
import android.os.Bundle;
import android.util.Log;

import androidx.test.filters.LargeTest;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.CddTest;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * This class comprises unit tests that test video encoders for dynamic change in parameters.
 * Refer to individual tests for details.
 * <p>
 * The tests in general tests mediacodec api, encoders and their interactions in bytebuffer mode.
 * <p>
 * The test feeds raw input data to the component and receives compressed bitstream from the
 * component.
 * <p>
 * At the end of encoding process, the test enforces following checks :-
 * <ul>
 *     <li> The test expects the output frame count to be identical to input frame count and the
 *     output timestamp list to be identical to input timestamp list.</li>
 * </ul>
 * <p>
 * The test runs mediacodec in synchronous and asynchronous mode.
 */
@RunWith(Parameterized.class)
public class VideoEncoderParamTest extends CodecEncoderTestBase {
    private static final String LOG_TAG = VideoEncoderParamTest.class.getSimpleName();
    private static final ArrayList<String> ABR_MEDIATYPE_LIST = new ArrayList<>();

    private int mNumSyncFramesReceived;
    private final ArrayList<Integer> mSyncFramesPos = new ArrayList<>();

    static {
        System.loadLibrary("ctsmediav2codecenc_jni");

        ABR_MEDIATYPE_LIST.add(MediaFormat.MIMETYPE_VIDEO_AVC);
        ABR_MEDIATYPE_LIST.add(MediaFormat.MIMETYPE_VIDEO_HEVC);
        ABR_MEDIATYPE_LIST.add(MediaFormat.MIMETYPE_VIDEO_VP8);
        ABR_MEDIATYPE_LIST.add(MediaFormat.MIMETYPE_VIDEO_VP9);
        ABR_MEDIATYPE_LIST.add(MediaFormat.MIMETYPE_VIDEO_AV1);
        ABR_MEDIATYPE_LIST.add(MediaFormat.MIMETYPE_VIDEO_APV);
    }

    public VideoEncoderParamTest(String encoder, String mediaType, EncoderConfigParams cfgParams,
            String allTestParams) {
        super(encoder, mediaType, new EncoderConfigParams[]{cfgParams}, allTestParams);
    }

    @Override
    protected void resetContext(boolean isAsync, boolean signalEOSWithLastFrame) {
        super.resetContext(isAsync, signalEOSWithLastFrame);
        mNumSyncFramesReceived = 0;
        mSyncFramesPos.clear();
    }

    @Override
    protected void dequeueOutput(int bufferIndex, MediaCodec.BufferInfo info) {
        if (info.size > 0 && (info.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0) {
            mNumSyncFramesReceived += 1;
            mSyncFramesPos.add(mOutputCount);
        }
        super.dequeueOutput(bufferIndex, info);
    }

    private void forceSyncFrame() {
        final Bundle syncFrame = new Bundle();
        syncFrame.putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0);
        if (ENABLE_LOGS) {
            Log.v(LOG_TAG, "requesting key frame");
        }
        mCodec.setParameters(syncFrame);
    }

    private void updateBitrate(int bitrate) {
        final Bundle bitrateUpdate = new Bundle();
        bitrateUpdate.putInt(MediaCodec.PARAMETER_KEY_VIDEO_BITRATE, bitrate);
        if (ENABLE_LOGS) {
            Log.v(LOG_TAG, "requesting bitrate to be changed to " + bitrate);
        }
        mCodec.setParameters(bitrateUpdate);
    }

    private static EncoderConfigParams getVideoEncoderCfgParam(String mediaType, int width,
            int height, int bitRate, int maxBFrames) {
        return new EncoderConfigParams.Builder(mediaType).setWidth(width).setHeight(height)
                .setMaxBFrames(maxBFrames).setBitRate(bitRate).build();
    }

    private static List<Object[]> prepareTestArgs(List<Object[]> args) {
        List<Object[]> argsList = new ArrayList<>();
        for (Object[] arg : args) {
            String mediaType = (String) arg[0];
            int width = (int) arg[1];
            int height = (int) arg[2];
            int bitRate = (int) arg[3];
            int maxBFrames = (int) arg[4];
            Object[] testArgs = new Object[2];
            testArgs[0] = arg[0];
            testArgs[1] = getVideoEncoderCfgParam(mediaType, width, height, bitRate, maxBFrames);
            argsList.add(testArgs);
        }
        return argsList;
    }

    @Parameterized.Parameters(name = "{index}_{0}_{1}")
    public static Collection<Object[]> input() {
        final boolean isEncoder = true;
        final boolean needAudio = false;
        final boolean needVideo = true;
        final List<Object[]> exhaustiveArgsList = new ArrayList<>(Arrays.asList(new Object[][]{
                // mediaType, width, height, bitrate, maxBFrames
                {MediaFormat.MIMETYPE_VIDEO_H263, 176, 144, 32000, 0},
                {MediaFormat.MIMETYPE_VIDEO_MPEG4, 176, 144, 32000, 0},
                {MediaFormat.MIMETYPE_VIDEO_AVC, 176, 144, 512000, 0},
                {MediaFormat.MIMETYPE_VIDEO_AVC, 320, 240, 512000, 2},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, 176, 144, 512000, 0},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, 320, 240, 384000, 2},
                {MediaFormat.MIMETYPE_VIDEO_VP8, 176, 144, 512000, 0},
                {MediaFormat.MIMETYPE_VIDEO_VP9, 176, 144, 512000, 0},
                {MediaFormat.MIMETYPE_VIDEO_AV1, 176, 144, 512000, 0},
        }));
        if (IS_AT_LEAST_B && apvSupport() && extractorMp4EnableApv()) {
            exhaustiveArgsList.addAll(Arrays.asList(new Object[][]{
                    {MediaFormat.MIMETYPE_VIDEO_APV, 176, 144, 1024000, 0},
            }));
        }
        List<Object[]> argsList = prepareTestArgs(exhaustiveArgsList);
        return prepareParamList(argsList, isEncoder, needAudio, needVideo, true);
    }

    @Before
    public void setUp() throws IOException {
        mActiveEncCfg = mEncCfgParams[0];
        MediaFormat format = mActiveEncCfg.getFormat();
        ArrayList<MediaFormat> formatList = new ArrayList<>();
        formatList.add(format);
        SupportClass supportRequirements = CODEC_ALL;
        if (mMediaType.equals(MediaFormat.MIMETYPE_VIDEO_APV)) {
            supportRequirements = CODEC_OPTIONAL;
        } else if (mMediaType.equals(MediaFormat.MIMETYPE_VIDEO_AV1)) {
            supportRequirements = CODEC_ANY;
        }
        checkFormatSupport(mCodecName, mMediaType, true, formatList, null, supportRequirements);
        mActiveRawRes = EncoderInput.getRawResource(mActiveEncCfg);
        assertNotNull("no raw resource found for testing config : " + mActiveEncCfg + mTestConfig
                + mTestEnv, mActiveRawRes);
    }

    /**
     * Test video encoders for feature "request-sync". Video encoders are expected to give a sync
     * frame upon request. The test requests encoder to provide key frame every 'n' seconds.  The
     * test feeds encoder input for 'm' seconds. At the end, it expects to receive m/n key frames
     * at least. Also it checks if the key frame received is not too far from the point of request.
     */
    @ApiTest(apis = {"android.media.MediaCodec#PARAMETER_KEY_REQUEST_SYNC_FRAME"})
    @LargeTest
    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testSetForceSyncFrame()
            throws IOException, InterruptedException, CloneNotSupportedException {
        EncoderConfigParams currCfg = mActiveEncCfg.getBuilder().setKeyFrameInterval(500.f).build();
        MediaFormat format = currCfg.getFormat();
        // Maximum allowed key frame interval variation from the target value.
        final int maxKeyframeIntervalVariation = 3;
        final int keyFrameInterval = 2; // force key frame every 2 seconds.
        final int keyFramePos = currCfg.mFrameRate * keyFrameInterval;
        final int numKeyFrameRequests = 7;

        setUpSource(mActiveRawRes.mFileName);
        mOutputBuff = new OutputManager();
        boolean[] boolStates = {true, false};
        {
            mCodec = MediaCodec.createByCodecName(mCodecName);
            for (boolean isAsync : boolStates) {
                mOutputBuff.reset();
                mInfoList.clear();
                configureCodec(format, isAsync, false, true);
                mCodec.start();
                for (int i = 0; i < numKeyFrameRequests; i++) {
                    doWork(keyFramePos);
                    if (mSawInputEOS) {
                        fail(String.format("Unable to encode %d frames as the input resource "
                                + "contains only %d frames \n", keyFramePos, mInputCount));
                    }
                    forceSyncFrame();
                    mInputBufferReadOffset = 0;
                }
                queueEOS();
                waitForAllOutputs();
                /* TODO(b/147348711) */
                if (false) mCodec.stop();
                else mCodec.reset();
                String msg = String.format("Received only %d key frames for %d key frame "
                        + "requests \n", mNumSyncFramesReceived, numKeyFrameRequests);
                assertTrue(msg + mTestConfig + mTestEnv,
                        mNumSyncFramesReceived >= numKeyFrameRequests);
                for (int i = 0, expPos = 0, index = 0; i < numKeyFrameRequests; i++) {
                    int j = index;
                    for (; j < mSyncFramesPos.size(); j++) {
                        // Check key frame intervals:
                        // key frame position should not be greater than target value + 3
                        // key frame position should not be less than target value - 3
                        if (Math.abs(expPos - mSyncFramesPos.get(j)) <=
                                maxKeyframeIntervalVariation) {
                            index = j;
                            break;
                        }
                    }
                    if (j == mSyncFramesPos.size()) {
                        Log.w(LOG_TAG, "requested key frame at frame index " + expPos +
                                " none found near by");
                    }
                    expPos += keyFramePos;
                }
            }
            mCodec.release();
        }
    }

    private native boolean nativeTestSetForceSyncFrame(String encoder, String file,
            String mediaType, String cfgParams, String separator, StringBuilder retMsg);

    /**
     * Test is similar to {@link #testSetForceSyncFrame()} but uses ndk api
     */
    @ApiTest(apis = {"android.media.MediaCodec#PARAMETER_KEY_REQUEST_SYNC_FRAME"})
    @LargeTest
    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testSetForceSyncFrameNative() throws IOException, CloneNotSupportedException {
        int colorFormat = findByteBufferColorFormat(mCodecName, mMediaType);
        assertTrue("no valid color formats received \n" + mTestConfig + mTestEnv,
                colorFormat != -1);
        MediaFormat format =
                mActiveEncCfg.getBuilder().setColorFormat(colorFormat).setKeyFrameInterval(500.f)
                        .build().getFormat();
        boolean isPass = nativeTestSetForceSyncFrame(mCodecName, mActiveRawRes.mFileName,
                mMediaType, EncoderConfigParams.serializeMediaFormat(format),
                EncoderConfigParams.TOKEN_SEPARATOR, mTestConfig);
        assertTrue(mTestConfig.toString(), isPass);
    }

    /**
     * Test video encoders for feature adaptive bitrate. Video encoders are expected to honor
     * bitrate changes upon request. The test requests encoder to encode at new bitrate every 'n'
     * seconds.  The test feeds encoder input for 'm' seconds. At the end, it expects the output
     * file size to be around {sum of (n * Bi) for i in the range [0, (m/n)]} and Bi is the
     * bitrate chosen for the interval 'n' seconds
     */
    @CddTest(requirements = {"5.2/C-2-1"})
    @ApiTest(apis = {"android.media.MediaCodec#PARAMETER_KEY_VIDEO_BITRATE"})
    @LargeTest
    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testAdaptiveBitRate() throws IOException, InterruptedException {
        Assume.assumeTrue("Skipping AdaptiveBitrate test for " + mMediaType,
                ABR_MEDIATYPE_LIST.contains(mMediaType));
        MediaFormat format = mActiveEncCfg.getFormat();
        final int adaptiveBrInterval = 3; // change br every 3 seconds.
        final int adaptiveBrDurFrm = mActiveEncCfg.mFrameRate * adaptiveBrInterval;
        final int brChangeRequests = 7;
        // TODO(b/251265293) Reduce the allowed deviation after improving the test conditions
        final float maxBitrateDeviation = 60.0f; // allowed bitrate deviation in %

        boolean[] boolStates = {true, false};
        setUpSource(mActiveRawRes.mFileName);
        mOutputBuff = new OutputManager();
        mSaveToMem = true;
        {
            mCodec = MediaCodec.createByCodecName(mCodecName);
            for (boolean isAsync : boolStates) {
                mOutputBuff.reset();
                mInfoList.clear();
                configureCodec(format, isAsync, false, true);
                mCodec.start();
                int expOutSize = 0;
                int bitrate = format.getInteger(MediaFormat.KEY_BIT_RATE);
                for (int i = 0; i < brChangeRequests; i++) {
                    doWork(adaptiveBrDurFrm);
                    if (mSawInputEOS) {
                        fail(String.format("Unable to encode %d frames as the input resource "
                                + "contains only %d frames \n", adaptiveBrDurFrm, mInputCount));
                    }
                    expOutSize += adaptiveBrInterval * bitrate;
                    if ((i & 1) == 1) bitrate *= 2;
                    else bitrate /= 2;
                    updateBitrate(bitrate);
                    mInputBufferReadOffset = 0;
                }
                queueEOS();
                waitForAllOutputs();
                /* TODO(b/147348711) */
                if (false) mCodec.stop();
                else mCodec.reset();
                /* TODO: validate output br with sliding window constraints Sec 5.2 cdd */
                int outSize = mOutputBuff.getOutStreamSize() * 8;
                float brDev = Math.abs(expOutSize - outSize) * 100.0f / expOutSize;
                if (brDev > maxBitrateDeviation) {
                    fail("Relative Bitrate error is too large " + brDev + "\n" + mTestConfig
                            + mTestEnv);
                }
            }
            mCodec.release();
        }
    }

    private native boolean nativeTestAdaptiveBitRate(String encoder, String file, String mediaType,
            String cfgParams, String separator, StringBuilder retMsg);

    /**
     * Test is similar to {@link #testAdaptiveBitRate()} but uses ndk api
     */
    @CddTest(requirements = {"5.2/C-2-1"})
    @ApiTest(apis = {"android.media.MediaCodec#PARAMETER_KEY_VIDEO_BITRATE"})
    @LargeTest
    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testAdaptiveBitRateNative() throws IOException, CloneNotSupportedException {
        Assume.assumeTrue("Skipping Native AdaptiveBitrate test for " + mMediaType,
                ABR_MEDIATYPE_LIST.contains(mMediaType));
        int colorFormat = findByteBufferColorFormat(mCodecName, mMediaType);
        assertTrue("no valid color formats received \n" + mTestConfig + mTestEnv,
                colorFormat != -1);
        MediaFormat format =
                mActiveEncCfg.getBuilder().setColorFormat(colorFormat).build().getFormat();
        boolean isPass = nativeTestAdaptiveBitRate(mCodecName, mActiveRawRes.mFileName, mMediaType,
                EncoderConfigParams.serializeMediaFormat(format),
                EncoderConfigParams.TOKEN_SEPARATOR, mTestConfig);
        assertTrue(mTestConfig.toString(), isPass);
    }
}
