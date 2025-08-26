/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.media.drmframework.cts;

import static org.junit.Assume.assumeTrue;

import android.content.res.AssetFileDescriptor;
import android.media.MediaDrm;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.cts.MediaCodecBlockModelHelper;
import android.media.cts.TestArgs;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.platform.test.annotations.AppModeFull;

import androidx.test.filters.SmallTest;

import com.android.compatibility.common.util.ApiLevelUtil;
import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.MediaUtils;
import com.android.compatibility.common.util.Preconditions;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Media DRM Codec tests with CONFIGURE_FLAG_USE_BLOCK_MODEL.
 */
@AppModeFull(reason = "Instant apps cannot access the SD card")
@RunWith(Parameterized.class)
public class MediaDrmCodecBlockModelTest {
    private static final String TAG = "MediaDrmCodecBlockModelTest";
    private static final boolean VERBOSE = false;           // lots of logging

    private boolean mIsAtLeastR = ApiLevelUtil.isAtLeast(Build.VERSION_CODES.R);
    static final String mInpPrefix = WorkDir.getMediaDirString();

    private String mCodecName;
    private String mTestFile;

    public MediaDrmCodecBlockModelTest(String codecName, String testFile) {
        mCodecName = codecName;
        mTestFile = mInpPrefix + testFile;
    }

    private static List<Object[]> prepareParamList(List<Object[]> exhaustiveArgsList) {
        final List<Object[]> argsList = new ArrayList<>();
        int argLength = exhaustiveArgsList.get(0).length;
        for (Object[] arg : exhaustiveArgsList) {
            String mediaType = (String) arg[0];
            String testFile = (String) arg[1];
            String[] codecs = MediaUtils.getDecoderNamesForMime(mediaType);
            for (String codec : codecs) {
                if (TestArgs.shouldSkipCodec(codec)) {
                    continue;
                }
                Object[] testArgs = new Object[argLength];
                testArgs[0] = codec;
                testArgs[1] = testFile;
                argsList.add(testArgs);
            }
        }
        return argsList;
    }

    @Parameterized.Parameters(name = "{index}_{0}_{2}")
    public static Collection<Object[]> input() {
        final List<Object[]> exhaustiveArgsList = new ArrayList<>(Arrays.asList(new Object[][]{
                {MediaFormat.MIMETYPE_VIDEO_AVC, "llama_h264_main_720p_8000.mp4"},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, "llama_hevc_240p_30fps_600_cenc.mp4"},
                {MediaFormat.MIMETYPE_VIDEO_VP8, "bbb_520x390_1mbps_30fps_vp8_cenc.webm"},
                {MediaFormat.MIMETYPE_VIDEO_VP9, "bbb_520x390_1mbps_30fps_vp9_cenc.webm"},
                {MediaFormat.MIMETYPE_VIDEO_AV1, "bbb_640x360_512kbps_30fps_av1_cenc.webm"},
        }));
        return prepareParamList(exhaustiveArgsList);
    }

    protected AssetFileDescriptor getAssetFileDescriptorFor() throws FileNotFoundException {
        File inpFile = new File(mTestFile);
        Preconditions.assertTestFileExists(mTestFile);
        ParcelFileDescriptor parcelFD =
                ParcelFileDescriptor.open(inpFile, ParcelFileDescriptor.MODE_READ_ONLY);
        return new AssetFileDescriptor(parcelFD, 0, parcelFD.getStatSize());
    }

    /**
     * Tests whether decoding a short encrypted group-of-pictures succeeds.
     * The test queues a few encrypted video frames by obtaining new block for each frame
     * then signals end-of-stream. The test fails if the decoder doesn't output the queued frames.
     */
    @SmallTest
    @ApiTest(apis = "MediaCodec#CONFIGURE_FLAG_USE_BLOCK_MODEL")
    @Test
    public void testDecodeShortEncryptedVideoWithBlockPerBuffer() throws InterruptedException {
        assumeTrue("Test needs Android 11", mIsAtLeastR);
        MediaCodecBlockModelHelper.runThread(() -> runDecodeShortEncryptedVideo(
                true /* obtainBlockForEachBuffer */));
    }

    /**
     * Tests whether decoding a short encrypted group-of-pictures succeeds.
     * The test queues a few encrypted video frames by reusing the existing block
     * then signals end-of-stream. The test fails if the decoder doesn't output the queued frames.
     */
    @SmallTest
    @ApiTest(apis = "MediaCodec#CONFIGURE_FLAG_USE_BLOCK_MODEL")
    @Test
    public void testDecodeShortEncryptedVideoWithSharedBlock() throws InterruptedException {
        assumeTrue("Test needs Android 11", mIsAtLeastR);
        MediaCodecBlockModelHelper.runThread(() -> runDecodeShortEncryptedVideo(
                false /* obtainBlockForEachBuffer */));
    }

    private static final UUID CLEARKEY_SCHEME_UUID =
            new UUID(0x1077efecc0b24d02L, 0xace33c1e52e2fb4bL);

    private static final byte[] CLEAR_KEY_CENC = convert(new int[] {
            0x3f, 0x0a, 0x33, 0xf3, 0x40, 0x98, 0xb9, 0xe2,
            0x2b, 0xc0, 0x78, 0xe0, 0xa1, 0xb5, 0xe8, 0x54 });

    private static final byte[] DRM_INIT_DATA = convert(new int[] {
            // BMFF box header (4 bytes size + 'pssh')
            0x00, 0x00, 0x00, 0x34, 0x70, 0x73, 0x73, 0x68,
            // Full box header (version = 1 flags = 0)
            0x01, 0x00, 0x00, 0x00,
            // SystemID
            0x10, 0x77, 0xef, 0xec, 0xc0, 0xb2, 0x4d, 0x02, 0xac, 0xe3, 0x3c,
            0x1e, 0x52, 0xe2, 0xfb, 0x4b,
            // Number of key ids
            0x00, 0x00, 0x00, 0x01,
            // Key id
            0x30, 0x30, 0x30, 0x30, 0x30, 0x30, 0x30, 0x30, 0x30, 0x30, 0x30,
            0x30, 0x30, 0x30, 0x30, 0x30,
            // size of data, must be zero
            0x00, 0x00, 0x00, 0x00 });

    private static final long ENCRYPTED_CONTENT_FIRST_BUFFER_TIMESTAMP_US = 12083333;
    private static final long ENCRYPTED_CONTENT_LAST_BUFFER_TIMESTAMP_US = 15041666;

    private static byte[] convert(int[] intArray) {
        byte[] byteArray = new byte[intArray.length];
        for (int i = 0; i < intArray.length; ++i) {
            byteArray[i] = (byte)intArray[i];
        }
        return byteArray;
    }

    private MediaCodecBlockModelHelper.Result runDecodeShortEncryptedVideo(
            boolean obtainBlockForEachBuffer) {
        MediaExtractor extractor = new MediaExtractor();

        try (final MediaDrm drm = new MediaDrm(CLEARKEY_SCHEME_UUID)) {
            extractor.setDataSource(mTestFile, null);
            extractor.selectTrack(0);
            extractor.seekTo(ENCRYPTED_CONTENT_FIRST_BUFFER_TIMESTAMP_US,
                    MediaExtractor.SEEK_TO_CLOSEST_SYNC);
            drm.setOnEventListener(
                    (MediaDrm mediaDrm, byte[] sessionId, int event, int extra, byte[] data) -> {
                        if (event == MediaDrm.EVENT_KEY_REQUIRED
                                || event == MediaDrm.EVENT_KEY_EXPIRED) {
                            MediaDrmClearkeyTest.retrieveKeys(
                                    mediaDrm, "cenc", sessionId, DRM_INIT_DATA,
                                    MediaDrm.KEY_TYPE_STREAMING,
                                    new byte[][] { CLEAR_KEY_CENC });
                        }
                    });
            byte[] sessionId = drm.openSession();
            MediaDrmClearkeyTest.retrieveKeys(
                    drm, "cenc", sessionId, DRM_INIT_DATA, MediaDrm.KEY_TYPE_STREAMING,
                    new byte[][] { CLEAR_KEY_CENC });
            MediaCodecBlockModelHelper.Result result =
                MediaCodecBlockModelHelper.runDecodeShortVideo(mCodecName, extractor,
                        ENCRYPTED_CONTENT_LAST_BUFFER_TIMESTAMP_US, obtainBlockForEachBuffer,
                        null /* format */, null /* events */, sessionId);
            drm.closeSession(sessionId);
            return result;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
