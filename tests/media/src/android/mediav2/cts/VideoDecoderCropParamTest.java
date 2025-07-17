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

import static android.mediav2.cts.AdaptivePlaybackTest.getSupportedFiles;
import static android.mediav2.cts.AdaptivePlaybackTest.prepareInputList;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.mediav2.common.cts.CodecDecoderTestBase;
import android.mediav2.common.cts.CodecTestActivity;
import android.mediav2.common.cts.OutputManager;
import android.view.PixelCopy;
import android.view.SurfaceView;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.filters.LargeTest;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.SynchronousPixelCopy;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Test video decoders support for frame cropping.
 * <p>
 * Some video standards (avc, hevc, ...) allow signalling frame cropping parameters in the
 * bitstream. For instance avc signals crop parameters in sps under frame_cropping_flag. Hevc
 * signals crop parameters in sps under conformance_window_flag. Decoders even though decode the
 * entire frame, they are expected to present only the conformance section. This test validates
 * the same.
 * <p>
 * At the end of decode session, additionally the test checks if the output count is same as
 * input count and output timestamps list and input timestamps list are same.
 */
@RunWith(Parameterized.class)
public class VideoDecoderCropParamTest extends CodecDecoderTestBase {
    private static final String MEDIA_DIR = WorkDir.getMediaDirString();
    private static final float TOLERANCE = 40 / 255.0f;
    private static final int PAD_DIM = 60; // in pixels

    private final String[] mSrcFiles;
    private final SynchronousPixelCopy mCopyHelper;
    private SurfaceView mSurfaceView;
    private int mFrameWidth;
    private int mFrameHeight;

    public VideoDecoderCropParamTest(String decoder, String mediaType, String[] srcFiles,
            String allTestParams) {
        super(decoder, mediaType, null, allTestParams);
        mSrcFiles = new String[srcFiles.length];
        for (int i = 0; i < srcFiles.length; i++) {
            mSrcFiles[i] = MEDIA_DIR + srcFiles[i];
        }
        mCopyHelper = new SynchronousPixelCopy();
        mFrameWidth = 0;
        mFrameHeight = 0;
    }

    @Rule
    public ActivityScenarioRule<CodecTestActivity> mActivityRule =
            new ActivityScenarioRule<>(CodecTestActivity.class);

    @Before
    public void setUp() throws IOException, InterruptedException {
        mActivityRule.getScenario().onActivity(activity -> mActivity = activity);
        setUpSurface(mActivity);
        mSurfaceView = mActivity.getSurfaceView();
    }

    @Parameterized.Parameters(name = "{index}_{0}_{1}")
    public static Collection<Object[]> input() {
        final boolean isEncoder = false;
        final boolean needAudio = false;
        final boolean needVideo = true;
        // mediaType, array list of test files

        // the test assets are created such that all of the area outside of the crop rectangle is
        // solid red. If the decoded output contains areas of these red pixels, that means the crop
        // was incorrectly applied -- which includes not applying the crop as well as misalignment
        // of the crop. Also the display region is ensured to not contain any solid red pixels.
        final List<Object[]> exhaustiveArgsList = new ArrayList<>(Arrays.asList(new Object[][]{
                {MediaFormat.MIMETYPE_VIDEO_AVC, new String[]{
                        "bbb_760x480_crf24_60fps_avc.mp4",
                        "bbb_760x480_60fps_crf24_botnopad_avc.mp4",
                        "bbb_760x480_60fps_crf24_botrightnopad_avc.mp4",
                        "bbb_760x480_60fps_crf24_horznopad_avc.mp4",
                        "bbb_760x480_60fps_crf24_leftbotnopad_avc.mp4",
                        "bbb_760x480_60fps_crf24_leftnopad_avc.mp4",
                        "bbb_760x480_60fps_crf24_rightnopad_avc.mp4",
                        "bbb_760x480_60fps_crf24_topleftnopad_avc.mp4",
                        "bbb_760x480_60fps_crf24_topnopad_avc.mp4",
                        "bbb_760x480_60fps_crf24_toprightnopad_avc.mp4",
                        "bbb_760x480_60fps_crf24_vertnopad_avc.mp4"}},
                {MediaFormat.MIMETYPE_VIDEO_AVC, new String[]{
                        "bbb_760x480_60fps_crf24_allpad_avc.mp4"}},
                {MediaFormat.MIMETYPE_VIDEO_AVC, new String[]{
                        "bbb_760x480_60fps_crf24_allpad_avc.mp4",
                        "bbb_600x360_60fps_crf24_allpad_avc.mp4"}},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, new String[]{
                        "bbb_760x480_60fps_1mbps_hevc.mp4",
                        "bbb_760x480_60fps_1mbps_botnopad_hevc.mp4",
                        "bbb_760x480_60fps_1mbps_botrightnopad_hevc.mp4",
                        "bbb_760x480_60fps_1mbps_horznopad_hevc.mp4",
                        "bbb_760x480_60fps_1mbps_leftbotnopad_hevc.mp4",
                        "bbb_760x480_60fps_1mbps_leftnopad_hevc.mp4",
                        "bbb_760x480_60fps_1mbps_rightnopad_hevc.mp4",
                        "bbb_760x480_60fps_1mbps_topleftnopad_hevc.mp4",
                        "bbb_760x480_60fps_1mbps_topnopad_hevc.mp4",
                        "bbb_760x480_60fps_1mbps_toprightnopad_hevc.mp4",
                        "bbb_760x480_60fps_1mbps_vertnopad_hevc.mp4"}},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, new String[]{
                        "bbb_760x480_60fps_1mbps_allpad_hevc.mp4"}},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, new String[]{
                        "bbb_760x480_60fps_1mbps_allpad_hevc.mp4",
                        "bbb_600x360_60fps_1mbps_allpad_hevc.mp4"}},
        }));
        return prepareParamList(exhaustiveArgsList, isEncoder, needAudio, needVideo, false);
    }

    @Override
    protected void dequeueOutput(int bufferIndex, MediaCodec.BufferInfo info) {
        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            mSawOutputEOS = true;
        }
        if (info.size > 0 && (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
            mOutputBuff.saveOutPTS(info.presentationTimeUs);
            mOutputCount++;
            MediaFormat format = mCodec.getOutputFormat(bufferIndex);
            int width = getWidth(format);
            int height = getHeight(format);
            if (width != mFrameWidth || height != mFrameHeight) {
                mFrameWidth = width;
                mFrameHeight = height;
                mActivity.setScreenParams(mFrameWidth, mFrameHeight, true);
                try {
                    mActivity.waitTillSurfaceIsChanged();
                } catch (InterruptedException e) {
                    Assert.fail(e + mTestConfig.toString() + mTestEnv.toString());
                }
                mActivity.resetSurfaceChanged();
            }
        }
        mCodec.releaseOutputBuffer(bufferIndex, mSurface != null);
        if (info.size > 0 && (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
            checkFrame();
        }
    }

    private void checkFrame() {
        checkFrame(new Rect(0, 0, mFrameWidth, PAD_DIM)); // top border
        checkFrame(new Rect(0, mFrameHeight - PAD_DIM, mFrameWidth, mFrameHeight)); // bottom border
        checkFrame(new Rect(0, PAD_DIM, PAD_DIM, mFrameHeight - PAD_DIM)); // left border
        checkFrame(new Rect(mFrameWidth - PAD_DIM, PAD_DIM, mFrameWidth,
                mFrameHeight - PAD_DIM)); // right border
    }

    private void checkFrame(Rect rect) {
        int width = rect.width();
        int height = rect.height();
        // cpu doesn't always have access to the raw surface, so we must make a
        // cpu-readable copy of the interesting pieces
        Bitmap img = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        int result = mCopyHelper.request(mSurfaceView, rect, img);
        Assert.assertEquals(String.format(Locale.getDefault(),
                "PixelCopy request failed for output frame : %d \n", mOutputCount - 1)
                + mTestConfig + mTestEnv, PixelCopy.SUCCESS, result);
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                Color pixel = img.getColor(j, i);
                float r = pixel.red();
                float g = pixel.green();
                float b = pixel.blue();
                if (r >= 1.0f - TOLERANCE && g <= TOLERANCE && b <= TOLERANCE) {
                    String msg = String.format(Locale.getDefault(),
                            "for frame : %d, at %s, at pixel (%d, %d), color is %s, this color is"
                                    + " set exclusively for no-show regions, but currently shown\n",
                            mOutputCount - 1, rect, j, i, pixel);
                    Assert.fail(msg + mTestConfig + mTestEnv);
                }
            }
        }
    }

    /**
     * Check description of class {@link VideoDecoderCropParamTest}
     */
    @ApiTest(apis = {"android.media.MediaCodecInfo.CodecCapabilities#FEATURE_AdaptivePlayback"})
    @LargeTest
    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testAdaptivePlayback() throws IOException, InterruptedException {
        List<String> resFiles = getSupportedFiles(mSrcFiles, mCodecName, mMediaType);
        Assume.assumeTrue("none of the given test clips are supported by the codec: " + mCodecName,
                !resFiles.isEmpty());
        if (resFiles.size() > 1) {
            Assume.assumeTrue("codec: " + mCodecName + " does not support FEATURE_AdaptivePlayback",
                    isFeatureSupported(mCodecName, mMediaType,
                            MediaCodecInfo.CodecCapabilities.FEATURE_AdaptivePlayback));
        }

        AdaptivePlaybackTest.APBTestInputData testInput = prepareInputList(resFiles, mMediaType);
        MediaFormat format = testInput.mFormats.get(0);
        mOutputBuff = new OutputManager();
        mCodec = MediaCodec.createByCodecName(mCodecName);
        mOutputBuff.reset();
        configureCodec(format, true, false, false);
        mCodec.start();
        doWork(testInput.mByteBuffer, (ArrayList<MediaCodec.BufferInfo>) testInput.mInfoList);
        queueEOS();
        waitForAllOutputs();
        mCodec.stop();
        mCodec.release();
    }
}
