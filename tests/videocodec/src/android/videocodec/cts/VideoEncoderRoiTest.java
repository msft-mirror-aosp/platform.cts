/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.videocodec.cts;

import static android.media.codec.Flags.FLAG_REGION_OF_INTEREST;
import static android.media.MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR;
import static android.media.MediaFormat.QpOffsetRect;
import static android.mediav2.common.cts.CodecTestBase.ComponentClass.HARDWARE;
import static android.mediav2.common.cts.CodecTestBase.areFormatsSupported;
import static android.mediav2.common.cts.CodecTestBase.isFeatureSupported;
import static android.mediav2.common.cts.CodecTestBase.prepareParamList;
import static android.videocodec.cts.VideoEncoderInput.SELFIEGROUP_FULLHD_PORTRAIT;
import static android.videocodec.cts.VideoEncoderInput.getRawResource;
import static android.videocodec.cts.VideoEncoderInput.CompressedResource;

import static org.junit.Assume.assumeTrue;

import android.graphics.Rect;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.mediav2.common.cts.EncoderConfigParams;
import android.mediav2.common.cts.RawResource;
import android.os.Build;
import android.os.Bundle;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.LargeTest;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.util.Pair;

import androidx.test.filters.SdkSuppress;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Test for Feature_Roi.
 * <p>
 * For encoders that support Roi encoding, the test configures region of interest (foreground and
 * background) information and the corresponding QP offset for various frames during encoding.
 * The encoded output is analyzed to check if the Roi information is honored.
 */
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM, codeName =
        "VanillaIceCream")
@AppModeFull(reason = "Instant apps cannot access the SD card")
@RequiresFlagsEnabled(FLAG_REGION_OF_INTEREST)
@RunWith(Parameterized.class)
public class VideoEncoderRoiTest extends VideoEncoderQualityRegressionTestBase {
    private static final int[] BIT_RATES = {800000, 1500000, 2000000, 3000000, 4000000};
    private static final int FRAME_LIMIT = 10;
    private static final int FRAME_RATE = 30;
    private static final int KEY_FRAME_INTERVAL = 600;
    private static final double EXPECTED_BD_RATE = 0d;
    private static final int MAX_B_FRAMES = 0;
    private static final String[] FEATURES = {null, MediaCodecInfo.CodecCapabilities.FEATURE_Roi};
    private static final List<Object[]> exhaustiveArgsList = new ArrayList<>();

    private Map<Long, List<QpOffsetRect>> mRoiMetadata = new HashMap<>();

    /**
     * Helper class for {@link VideoEncoderRoiTest}
     */
    public static class VideoEncoderRoiHelper extends VideoEncoderValidationTestBase {
        private final Map<Long, List<QpOffsetRect>> mRoiMetadata;

        VideoEncoderRoiHelper(String encoder, String mediaType, EncoderConfigParams encCfgParams,
                Map<Long, List<QpOffsetRect>> roiMetadata, String allTestParams) {
            super(encoder, mediaType, encCfgParams, allTestParams);
            mRoiMetadata = roiMetadata;
        }

        private List<QpOffsetRect> getRoiMetadataForPts(Long pts) {
            final int roundToleranceUs = 10;
            if (mRoiMetadata.containsKey(pts)) return mRoiMetadata.get(pts);
            for (Map.Entry<Long, List<QpOffsetRect>> entry : mRoiMetadata.entrySet()) {
                Long keyPts = entry.getKey();
                if (Math.abs(keyPts - pts) < roundToleranceUs) {
                    return entry.getValue();
                }
            }
            return null;
        }

        @Override
        protected void enqueueInput(int bufferIndex) {
            long pts = mInputOffsetPts + mInputCount * 1000000L / mActiveEncCfg.mFrameRate;
            List<QpOffsetRect> qpOffsetRects = getRoiMetadataForPts(pts);
            if (qpOffsetRects != null) {
                Bundle param = new Bundle();
                param.putString(MediaCodec.PARAMETER_KEY_QP_OFFSET_RECTS,
                        QpOffsetRect.flattenToString(qpOffsetRects));
                mCodec.setParameters(param);
            }
            super.enqueueInput(bufferIndex);
        }
    }

    private static void addParams(CompressedResource cRes) {
        final String[] mediaTypes =
                new String[]{MediaFormat.MIMETYPE_VIDEO_AVC, MediaFormat.MIMETYPE_VIDEO_HEVC,
                        MediaFormat.MIMETYPE_VIDEO_VP9, MediaFormat.MIMETYPE_VIDEO_AV1};
        RESOURCES.add(cRes);
        for (String mediaType : mediaTypes) {
            // mediaType, cfg
            exhaustiveArgsList.add(new Object[]{mediaType, cRes});
        }
    }

    @Parameterized.Parameters(name = "{index}_{0}_{1}")
    public static Collection<Object[]> input() {
        addParams(SELFIEGROUP_FULLHD_PORTRAIT);
        return prepareParamList(exhaustiveArgsList, true, false, true, false, HARDWARE);
    }

    public VideoEncoderRoiTest(String encoder, String mediaType, CompressedResource cRes,
            String allTestParams) {
        super(encoder, mediaType, cRes, allTestParams);
    }

    public Map<Long, List<Rect>> getPtsRectMap()
            throws NoSuchFieldException, IllegalAccessException {
        Map<Long, List<Rect>> ptsRectMap = new HashMap<>();
        for (Map.Entry<Long, List<QpOffsetRect>> entry :
                mRoiMetadata.entrySet()) {
            Long keyPts = entry.getKey();
            List<QpOffsetRect> qpOffsetRects = entry.getValue();
            List<Rect> rects = new ArrayList<>();
            for (QpOffsetRect qpOffsetRect : qpOffsetRects) {
                Field mContour = QpOffsetRect.class.getDeclaredField("mContour");
                mContour.setAccessible(true);
                rects.add((Rect) mContour.get(qpOffsetRect));
            }
            ptsRectMap.put(keyPts, rects);
        }
        return ptsRectMap;
    }

    @ApiTest(apis = {"android.media.MediaCodecInfo.CodecCapabilities#FEATURE_Roi",
            "android.media.MediaCodec#PARAMETER_KEY_QP_OFFSET_MAP",
            "android.media.MediaCodec#PARAMETER_KEY_QP_OFFSET_RECTS",
            "android.media.MediaFormat#QpOffsetRect"})
    @LargeTest
    @Test
    public void testRoiSupport()
            throws IOException, InterruptedException, NoSuchFieldException, IllegalAccessException {
        assumeTrue(mCodecName + " does not support FEATURE_Roi",
                isFeatureSupported(mCodecName, mMediaType,
                        MediaCodecInfo.CodecCapabilities.FEATURE_Roi));
        RawResource res = getRawResource(mCRes);
        mRoiMetadata = GenerateRoiMetadata.ROI_INFO.get(mCRes);
        VideoEncoderValidationTestBase[] testInstances =
                {new VideoEncoderValidationTestBase(null, mMediaType, null,
                        mAllTestParams), new VideoEncoderRoiHelper(null, mMediaType, null,
                        mRoiMetadata, mAllTestParams)};
        String[] encoderNames = new String[FEATURES.length];
        List<EncoderConfigParams[]> cfgsUnion = new ArrayList<>();
        for (int i = 0; i < FEATURES.length; i++) {
            EncoderConfigParams[] cfgs = new EncoderConfigParams[BIT_RATES.length];
            cfgsUnion.add(cfgs);
            ArrayList<MediaFormat> fmts = new ArrayList<>();
            for (int j = 0; j < cfgs.length; j++) {
                Pair<String, Boolean> feature = new Pair<>(FEATURES[i], FEATURES[i] != null);
                cfgs[j] = getVideoEncoderCfgParams(mMediaType, res.mWidth, res.mHeight,
                        BIT_RATES[j], BITRATE_MODE_VBR, KEY_FRAME_INTERVAL, FRAME_RATE,
                        MAX_B_FRAMES, feature);
                fmts.add(cfgs[j].getFormat());
            }
            assumeTrue("Encoder: " + mCodecName + " doesn't support formats.",
                    areFormatsSupported(mCodecName, mMediaType, fmts));
            encoderNames[i] = mCodecName;
        }
        Predicate<Double> predicate = bdRate -> bdRate <= EXPECTED_BD_RATE;
        Map<Long, List<Rect>> frameCropRects = getPtsRectMap();
        getQualityRegressionForCfgs(cfgsUnion, testInstances, encoderNames, res, FRAME_LIMIT,
                FRAME_RATE, frameCropRects, false, predicate);
    }
}

/**
 * Generates ROI Metadata for {@link VideoEncoderRoiTest}.
 */
class GenerateRoiMetadata {
    static final Map<CompressedResource, Map<Long, List<QpOffsetRect>>> ROI_INFO =
            new HashMap<>();

    static {
        Map<Long, List<QpOffsetRect>> roiMetadata = new HashMap<>();
        roiMetadata.put(0L, new ArrayList<>(
                Arrays.asList(new QpOffsetRect(new Rect(694, 668, 991, 1487), -5),
                        new QpOffsetRect(new Rect(18, 627, 770, 1957), -5))));
        roiMetadata.put(33333L, new ArrayList<>(
                Arrays.asList(new QpOffsetRect(new Rect(688, 643, 991, 1531), -5),
                        new QpOffsetRect(new Rect(21, 645, 762, 1946), -5))));
        roiMetadata.put(66666L, new ArrayList<>(
                Arrays.asList(new QpOffsetRect(new Rect(673, 613, 965, 1562), -5),
                        new QpOffsetRect(new Rect(26, 636, 761, 1945), -5))));
        roiMetadata.put(100000L, new ArrayList<>(
                Arrays.asList(new QpOffsetRect(new Rect(672, 642, 949, 1541), -5),
                        new QpOffsetRect(new Rect(15, 639, 867, 1956), -5))));
        roiMetadata.put(133333L, new ArrayList<>(
                Arrays.asList(new QpOffsetRect(new Rect(657, 668, 944, 1499), -5),
                        new QpOffsetRect(new Rect(20, 638, 761, 1957), -5))));
        roiMetadata.put(166666L, new ArrayList<>(
                Arrays.asList(new QpOffsetRect(new Rect(643, 674, 942, 1526), -5),
                        new QpOffsetRect(new Rect(8, 647, 761, 1946), -5))));
        roiMetadata.put(200000L, new ArrayList<>(
                Arrays.asList(new QpOffsetRect(new Rect(638, 694, 940, 1472), -5),
                        new QpOffsetRect(new Rect(4, 653, 769, 1939), -5))));
        roiMetadata.put(233333L, new ArrayList<>(
                Arrays.asList(new QpOffsetRect(new Rect(630, 693, 953, 1472), -5),
                        new QpOffsetRect(new Rect(15, 652, 764, 1936), -5))));
        roiMetadata.put(266666L, new ArrayList<>(
                Arrays.asList(new QpOffsetRect(new Rect(627, 687, 961, 1486), -5),
                        new QpOffsetRect(new Rect(20, 661, 752, 1939), -5))));
        roiMetadata.put(300000L, new ArrayList<>(
                Arrays.asList(new QpOffsetRect(new Rect(634, 682, 926, 1466), -5),
                        new QpOffsetRect(new Rect(18, 644, 758, 1946), -5))));
        ROI_INFO.put(SELFIEGROUP_FULLHD_PORTRAIT, roiMetadata);
    }
}
