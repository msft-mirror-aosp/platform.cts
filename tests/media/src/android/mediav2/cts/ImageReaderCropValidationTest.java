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

import static android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface;
import static android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible;
import static android.mediav2.common.cts.CodecTestBase.SupportClass.CODEC_OPTIONAL;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.media.Image;
import android.media.ImageWriter;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.mediav2.common.cts.CodecDecoderTestBase;
import android.mediav2.common.cts.ImageSurface;
import android.mediav2.common.cts.OutputManager;
import android.util.Log;

import androidx.test.filters.SmallTest;

import com.android.compatibility.common.util.ApiTest;

import org.junit.After;
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
 * Verify if crop attributes are supported correctly in ImageReader.
 *
 * <p>The test decodes a media resource that has crop parameters and releases the output to image
 * surface. If the decoded output buffer has crop attributes, then the test expects the image
 * dequeued from image reader surface also to have the same attributes. The image crop attributes
 * are then modified using setRect() and passed to another image reader via image writer. The test
 * expects the dequeued image crop attributes to be same as modified crop attributes.
 */
@RunWith(Parameterized.class)
public class ImageReaderCropValidationTest extends CodecDecoderTestBase {
    private static final String LOG_TAG = ImageReaderCropValidationTest.class.getSimpleName();
    private static final String MEDIA_DIR = WorkDir.getMediaDirString();

    private final int mColorFormat;
    private ImageSurface mImageSurface2;
    private ImageWriter mWriter;
    private int mWidth;
    private int mHeight;

    static {
        System.loadLibrary("ctsmediav2codecdecsurface_jni");
    }

    public ImageReaderCropValidationTest(String decoder, String mediaType, String testFile,
            int colorFormat, String allTestParams) {
        super(decoder, mediaType, MEDIA_DIR + testFile, allTestParams);
        mColorFormat = colorFormat;
    }

    @Parameterized.Parameters(name = "{index}_{0}_{1}")
    public static Collection<Object[]> input() {
        // mediaType, testClip, colorFormat
        final List<Object[]> exhaustiveArgsList = new ArrayList<>(Arrays.asList(new Object[][] {
                {MediaFormat.MIMETYPE_VIDEO_AVC, "bbb_760x480_60fps_crf24_allpad_avc.mp4",
                        COLOR_FormatSurface},
                {MediaFormat.MIMETYPE_VIDEO_AVC, "bbb_760x480_60fps_crf24_allpad_avc.mp4",
                        COLOR_FormatYUV420Flexible},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, "bbb_760x480_60fps_1mbps_allpad_hevc.mp4",
                        COLOR_FormatSurface},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, "bbb_760x480_60fps_1mbps_allpad_hevc.mp4",
                        COLOR_FormatYUV420Flexible},
        }));
        return prepareParamList(exhaustiveArgsList, false, false, true, false);
    }

    @Before
    public void setUp() throws IOException {
        assumeTrue("Skip crop tests on devices Baklava and below", IS_AFTER_B);
        MediaFormat format = setUpSource(mTestFile);
        mExtractor.release();
        ArrayList<MediaFormat> formatList = new ArrayList<>();
        formatList.add(format);
        checkFormatSupport(mCodecName, mMediaType, false, formatList, null, CODEC_OPTIONAL);
    }

    @After
    public void tearDown() {
        if (mImageSurface2 != null) {
            mImageSurface2.release();
            mImageSurface2 = null;
        }
        if (mWriter != null) {
            mWriter.close();
            mWriter = null;
        }
    }

    protected void dequeueOutput(int bufferIndex, MediaCodec.BufferInfo info) {
        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            mSawOutputEOS = true;
        }
        if (ENABLE_LOGS) {
            Log.v(LOG_TAG, "output: id: " + bufferIndex + " flags: " + info.flags + " size: "
                    + info.size + " timestamp: " + info.presentationTimeUs);
        }
        if (info.size > 0 && (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
            mOutputBuff.saveOutPTS(info.presentationTimeUs);
            mOutputCount++;
        }
        MediaFormat format = mCodec.getOutputFormat(bufferIndex);
        int width = format.getInteger(MediaFormat.KEY_WIDTH, -1);
        int height = format.getInteger(MediaFormat.KEY_HEIGHT, -1);
        int cropLeft = format.getInteger("crop-left", 0);
        int cropRight = format.getInteger("crop-right", width - 1);
        int cropTop = format.getInteger("crop-top", 0);
        int cropBottom = format.getInteger("crop-bottom", height - 1);
        Rect cropRectBuffer = new Rect(cropLeft, cropTop, cropRight + 1, cropBottom + 1);
        mCodec.releaseOutputBuffer(bufferIndex, mSurface != null);
        if (info.size > 0 && (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
            boolean gotImage1 = false;
            Rect cropRect1 = null;
            try (Image image = mImageSurface.getImage(1000)) {
                gotImage1 = image != null;
                assertNotNull("no image received from IR1 \n" + mTestConfig + mTestEnv, image);
                cropRect1 = image.getCropRect();
                assertEquals("buffer crop rect: " + cropRectBuffer + " IR1 crop rect: " + cropRect1
                                + " are not equal\n" + mTestConfig + mTestEnv,
                        cropRectBuffer, cropRect1);
                cropRect1.inset(64, 64);
                image.setCropRect(cropRect1);
                mWriter.queueInputImage(image);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } finally {
                if (gotImage1) mImageSurface.pop();
            }
            assertTrue("no image received from IR1\n" + mTestConfig + mTestEnv, gotImage1);
            boolean gotImage2 = false;
            Rect cropRect2 = null;
            try (Image image = mImageSurface2.getImage(1000)) {
                gotImage2 = image != null;
                assertNotNull("no image received from IR1 \n" + mTestConfig + mTestEnv, image);
                cropRect2 = image.getCropRect();
                assertEquals("IR1 crop rect: " + cropRect1 + " IR2 crop rect: " + cropRect2
                                + " are not equal\n" + mTestConfig + mTestEnv,
                        cropRect1, cropRect2);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } finally {
                if (gotImage2) mImageSurface2.pop();
            }
            assertTrue("no image received from IR2\n" + mTestConfig + mTestEnv, gotImage2);
        }
    }

    /**
     * Check description of class {@link ImageReaderCropValidationTest}
     */
    @ApiTest(apis = {"android.media.ImageReader#acquireNextImage",
            "android.media.Image#getCropRect", "android.media.Image#setCropRect"})
    @SmallTest
    @Test(timeout = PER_TEST_TIMEOUT_SMALL_TEST_MS)
    public void testSimpleDecodeToSurface() throws IOException, InterruptedException {
        MediaFormat format = setUpSource(mTestFile);
        mWidth = getWidth(format);
        mHeight = getHeight(format);
        int imageFormat = ImageFormat.UNKNOWN;
        if (mColorFormat == COLOR_FormatSurface) imageFormat = ImageFormat.PRIVATE;
        else if (mColorFormat == COLOR_FormatYUV420Flexible) imageFormat = ImageFormat.YUV_420_888;
        mImageSurface = new ImageSurface();
        setUpSurface(mWidth, mHeight, imageFormat, 1, 0, null);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, mColorFormat);
        mImageSurface2 = new ImageSurface();
        mImageSurface2.createSurface(mWidth, mHeight, imageFormat, 1, 1, null);
        mWriter = ImageWriter.newInstance(mImageSurface2.getSurface(), 1, imageFormat);
        mOutputBuff = new OutputManager();
        mCodec = MediaCodec.createByCodecName(mCodecName);
        configureCodec(format, true, true, false);
        mCodec.start();
        doWork(5);
        queueEOS();
        waitForAllOutputs();
        mCodec.stop();
        mCodec.release();
        mExtractor.release();
    }

    private native boolean nativeTestImageSurfaceCropRect(String decoder, String mediaType,
            String testFile, int colorFormat, StringBuilder retMsg);

    /**
     * Check description of class {@link ImageReaderCropValidationTest}
     */
    @ApiTest(apis = {"AImageReader#acquireNextImage", "AImage#getCropRect"})
    @SmallTest
    @Test(timeout = PER_TEST_TIMEOUT_SMALL_TEST_MS)
    public void testSimpleDecodeToSurfaceNative() throws IOException {
        boolean isPass = nativeTestImageSurfaceCropRect(mCodecName, mMediaType, mTestFile,
                mColorFormat, mTestConfig);
        assertTrue(mTestConfig.toString(), isPass);
    }

}
