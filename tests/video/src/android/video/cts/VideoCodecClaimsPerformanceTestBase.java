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

package android.video.cts;

import static android.mediav2.common.cts.CodecTestBase.ComponentClass.ALL;
import static android.mediav2.common.cts.CodecTestBase.SupportClass.*;
import static android.mediav2.common.cts.CodecTestBase.areFormatsSupported;
import static android.mediav2.common.cts.CodecTestBase.getCodecCapabilities;
import static android.mediav2.common.cts.CodecTestBase.isDefaultCodec;
import static android.mediav2.common.cts.CodecTestBase.isFeatureSupported;
import static android.mediav2.common.cts.CodecTestBase.isHardwareAcceleratedCodec;
import static android.mediav2.common.cts.CodecTestBase.selectCodecs;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.media.MediaCodecInfo;
import android.media.MediaCodecInfo.VideoCapabilities.PerformancePoint;
import android.media.MediaFormat;
import android.mediav2.common.cts.CodecTestBase.SupportClass;
import android.util.Range;

import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TestName;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper class for testing performance requirements of codecs
 */
public class VideoCodecClaimsPerformanceTestBase {
    protected final boolean mIsEncoder;
    protected final String mMediaType;
    protected final int mWidth;
    protected final int mHeight;
    protected final int mFps;
    protected final SupportClass mSupportRequirements;
    protected final String mTestArgs;

    protected final StringBuilder mTestConfig = new StringBuilder();

    public VideoCodecClaimsPerformanceTestBase(String mediaType, int width, int height, int fps,
            boolean isEncoder, SupportClass supportRequirements, String allTestParams) {
        mMediaType = mediaType;
        mWidth = width;
        mHeight = height;
        mFps = fps;
        mIsEncoder = isEncoder;
        mSupportRequirements = supportRequirements;
        mTestArgs = allTestParams;
    }

    @Rule
    public final TestName mTestName = new TestName();

    @Before
    public void setUpTestLogs() {
        mTestConfig.setLength(0);
        mTestConfig.append("\n##################       Test Details        ####################\n");
        mTestConfig.append("Test Name :- ").append(mTestName.getMethodName()).append("\n");
        mTestConfig.append("Test Parameters :- ").append(mTestArgs).append("\n");
    }

    boolean hasFormatSupport(String codecName, String mediaType, boolean isEncoder,
            ArrayList<MediaFormat> formats, SupportClass supportRequirements) throws IOException {
        boolean hasSupport = areFormatsSupported(codecName, mediaType, formats);
        if (!hasSupport) {
            StringBuilder msg = new StringBuilder("Media Type : " + mediaType).append("\n");
            msg.append("Formats :").append("\n");
            for (MediaFormat format : formats) {
                msg.append(format).append("\n");
            }
            switch (supportRequirements) {
                case CODEC_ALL:
                    fail(msg + " not supported by codec : " + codecName + "\n" + mTestConfig);
                    break;
                case CODEC_ANY:
                    if (selectCodecs(mediaType, formats, null, isEncoder).isEmpty()) {
                        fail(msg + " not supported by any component on the device\n" + mTestConfig);
                    }
                    break;
                case CODEC_DEFAULT:
                    if (isDefaultCodec(codecName, mediaType, isEncoder)) {
                        fail(msg + " not supported by default codec : " + codecName + "\n"
                                + mTestConfig);
                    }
                    break;
                case CODEC_HW:
                    if (isHardwareAcceleratedCodec(codecName)) {
                        fail(msg + " not supported by codec : " + codecName + "\n" + mTestConfig);
                    }
                    break;
                case CODEC_SHOULD:
                case CODEC_HW_RECOMMENDED:
                case CODEC_OPTIONAL:
                default:
                    break;
            }
        }
        return hasSupport;
    }

    protected boolean deviceClaimsPerformanceSupported() throws IOException {
        ArrayList<MediaFormat> formats = new ArrayList<>();
        MediaFormat format = MediaFormat.createVideoFormat(mMediaType, mWidth, mHeight);
        formats.add(format);
        ArrayList<String> codecs = selectCodecs(mMediaType, null, null, mIsEncoder, ALL, true);
        boolean hasHwCodec = false;
        boolean hasSecureCodec = false;
        boolean coversTarget = false;
        boolean secureCodecCoversTarget = false;
        StringBuilder msg = new StringBuilder();
        for (String codecName : codecs) {
            if (!hasFormatSupport(codecName, mMediaType, mIsEncoder, formats,
                    mSupportRequirements)) {
                continue;
            }
            MediaCodecInfo.CodecCapabilities cap = getCodecCapabilities(codecName, mMediaType);
            assertNotNull(codecName + " didn't provide capabilities \n" + mTestConfig, cap);
            MediaCodecInfo.VideoCapabilities videoCaps = cap.getVideoCapabilities();
            assertNotNull(codecName + " didn't provide video capabilities \n" + mTestConfig,
                    videoCaps);
            List<PerformancePoint> pps = videoCaps.getSupportedPerformancePoints();
            boolean isSecure = isFeatureSupported(codecName, mMediaType,
                    MediaCodecInfo.CodecCapabilities.FEATURE_SecurePlayback);
            hasSecureCodec |= isSecure;
            boolean isHw = isHardwareAcceleratedCodec(codecName);
            hasHwCodec |= isHw;
            if (isHw && (pps == null || pps.isEmpty())) {
                fail(codecName + " didn't publish any performance point information\n"
                        + mTestConfig);
            }
            if (pps != null && pps.size() > 0) {
                PerformancePoint PPReq = new PerformancePoint(mWidth, mHeight, mFps);
                boolean covers = false;
                for (PerformancePoint pp : pps) {
                    if (pp.covers(PPReq)) {
                        covers = true;
                        coversTarget = true;
                        if (isSecure) secureCodecCoversTarget = true;
                        break;
                    }
                }
                if (!covers) {
                    msg.append(String.format(
                            "codec: %s and for media type: %s, width %d, height %d, fps %d not "
                            + "covered by any hardware performance point\n",
                            codecName, mMediaType, mWidth, mHeight, mFps));
                }
            }
            if (isHw) continue; // for hw codecs, achievableFrameRatesFor() is not relevant

            // As per CDD 5.1.10/C-2-1, all sw video codecs must publish achievable frame rate data
            // if the video size is supported by the codec.

            // Also, For non-HW accelerated (SW) encoders we have to rely on their published
            // achievable rates as they do not advertise performance points.
            // The test relies on getLower() as that is the best approximation for what
            // can be achieved.
            Range<Double> reported = videoCaps.getAchievableFrameRatesFor(mWidth, mHeight);
            assertNotNull(String.format("%s did not publish achievable frame rate data for video "
                                          + "size: %dx%d\n%s",
                                  codecName, mWidth, mHeight, mTestConfig),
                    reported);
            if (reported.getLower() >= mFps) {
                coversTarget = true;
                if (isSecure) secureCodecCoversTarget = true;
            } else {
                msg.append(String.format("codec: %s and for media type: %s, width %d, height %d, "
                                         + "required fps is %d, got is %s\n",
                        codecName, mMediaType, mWidth, mHeight, mFps, reported));
            }
        }
        if (mSupportRequirements == CODEC_ALL || mSupportRequirements == CODEC_ANY
                || mSupportRequirements == CODEC_DEFAULT
                || (mSupportRequirements == CODEC_HW && hasHwCodec)) {
            if (!coversTarget) {
                msg.append("none of the regular codecs achieve requested rate \n");
                fail(msg.toString() + mTestConfig);
            }
            if (hasSecureCodec && !secureCodecCoversTarget) {
                msg.append("none of the secure codecs achieve requested rate \n");
                fail(msg.toString() + mTestConfig);
            }
        }
        assumeTrue("no components available for mediaType: " + mMediaType, !codecs.isEmpty());
        return coversTarget;
    }
}
