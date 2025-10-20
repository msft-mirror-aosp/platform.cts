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

package android.mediapc.cts;

import static android.media.MediaCodecInfo.CodecCapabilities.FEATURE_SecurePlayback;
import static android.media.MediaDrm.SECURITY_LEVEL_HW_SECURE_ALL;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertTrue;

import android.hardware.display.DisplayManager;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaDrm;
import android.media.MediaFormat;
import android.media.UnsupportedSchemeException;
import android.media.performanceclass.MediaPerformanceClass;
import android.mediapc.cts.common.PerformanceClassEvaluator;
import android.mediapc.cts.common.PerformanceClassTestRule;
import android.mediapc.cts.common.Preconditions;
import android.mediapc.cts.common.Requirements;
import android.mediapc.cts.common.Requirements.Android11MemoryRequirement;
import android.mediapc.cts.common.Requirements.HDRDisplayRequirement;
import android.mediapc.cts.common.Requirements.MediaDrmSecurityLevelHardwareSecureAllRequirement;
import android.mediapc.cts.common.Requirements.MemoryRequirement;
import android.mediapc.cts.common.Requirements.ScreenDensityRRequirement;
import android.mediapc.cts.common.Requirements.ScreenDensityRequirement;
import android.mediapc.cts.common.Requirements.ScreenResolutionRRequirement;
import android.mediapc.cts.common.Requirements.ScreenResolutionRequirement;
import android.mediapc.cts.common.Requirements.SecureHardwareDecodersRequirement;
import android.mediapc.cts.common.Utils;
import android.os.Build;
import android.util.Log;
import android.view.Display;

import androidx.test.filters.SmallTest;

import com.android.compatibility.common.util.CddTest;
import com.android.compatibility.common.util.MediaUtils;

import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Tests the basic aspects of the media performance class.
 */
public class PerformanceClassTest {
    private static final String TAG = "PerformanceClassTest";
    public static final String[] VIDEO_CONTAINER_MEDIA_TYPES =
        {"video/mp4", "video/webm", "video/3gpp", "video/3gpp2", "video/avi", "video/x-ms-wmv",
            "video/x-ms-asf"};
    static ArrayList<String> mMediaTypeSecureSupport = new ArrayList<>();

    @Rule
    public final PerformanceClassTestRule pcRule =
            PerformanceClassTestRule.with(Preconditions.EMPTY);

    static {
        mMediaTypeSecureSupport.add(MediaFormat.MIMETYPE_VIDEO_AVC);
        mMediaTypeSecureSupport.add(MediaFormat.MIMETYPE_VIDEO_HEVC);
        mMediaTypeSecureSupport.add(MediaFormat.MIMETYPE_VIDEO_VP9);
        mMediaTypeSecureSupport.add(MediaFormat.MIMETYPE_VIDEO_AV1);
    }

    @SmallTest
    @Test
    @CddTest(requirements = {"2.2.7.1/5.1/H-1-11"})
    public void testSecureHwDecodeSupport() {
        Utils.assumeDeviceMeetsPerformanceClassPreconditions();
        ArrayList<String> noSecureHwDecoderForMediaTypes = new ArrayList<>();
        for (String mediaType : mMediaTypeSecureSupport) {
            boolean isSecureHwDecoderFoundForMediaType = false;
            boolean isHwDecoderFoundForMediaType = false;
            MediaCodecList codecList = new MediaCodecList(MediaCodecList.ALL_CODECS);
            MediaCodecInfo[] codecInfos = codecList.getCodecInfos();
            for (MediaCodecInfo info : codecInfos) {
                if (info.isEncoder() || !info.isHardwareAccelerated() || info.isAlias()) continue;
                try {
                    MediaCodecInfo.CodecCapabilities caps = info.getCapabilitiesForType(mediaType);
                    if (caps != null) {
                        isHwDecoderFoundForMediaType = true;
                        if (caps.isFeatureSupported(FEATURE_SecurePlayback))
                            isSecureHwDecoderFoundForMediaType = true;
                    }
                } catch (Exception ignored) {
                }
            }
            if (isHwDecoderFoundForMediaType && !isSecureHwDecoderFoundForMediaType)
                noSecureHwDecoderForMediaTypes.add(mediaType);
        }

        boolean secureDecodeSupportIfHwDecoderPresent = noSecureHwDecoderForMediaTypes.isEmpty();

        PerformanceClassEvaluator pce = pcRule.getPerformanceClassEvaluator();
        SecureHardwareDecodersRequirement r5_1__H_1_11 = Requirements.addR5_1__H_1_11().to(pce);
        r5_1__H_1_11.setSecureRequirementSatisfiedBoolean(secureDecodeSupportIfHwDecoderPresent);
    }

    @SmallTest
    @Test
    @CddTest(requirements = {"2.2.7.1/5.7/H-1-2"})
    public void testMediaDrmSecurityLevelHwSecureAll() throws UnsupportedSchemeException {
        List<UUID> drmList = MediaDrm.getSupportedCryptoSchemes();
        List<UUID> supportedHwSecureAllSchemes = new ArrayList<>();

        for (UUID cryptoSchemeUUID : drmList) {
            boolean cryptoSchemeSupportedForAtleastOneMediaType = false;
            for (String mediaType : VIDEO_CONTAINER_MEDIA_TYPES) {
                cryptoSchemeSupportedForAtleastOneMediaType |= MediaDrm
                    .isCryptoSchemeSupported(cryptoSchemeUUID, mediaType,
                        SECURITY_LEVEL_HW_SECURE_ALL);
            }
            if (cryptoSchemeSupportedForAtleastOneMediaType) {
                supportedHwSecureAllSchemes.add(cryptoSchemeUUID);
            }
        }

        PerformanceClassEvaluator pce = pcRule.getPerformanceClassEvaluator();
        MediaDrmSecurityLevelHardwareSecureAllRequirement r5_7__H_1_2 =
                Requirements.addR5_7__H_1_2().to(pce);

        r5_7__H_1_2.setNumberCryptoHwSecureAllSupport(supportedHwSecureAllSchemes.size());
    }

    @SmallTest
    @Test
    public void testMediaPerformanceClassScope() throws Exception {
        // if device is not of a performance class, we are done.
        Assume.assumeTrue("not a device of a valid media performance class", Utils.isPerfClass());

        if (Utils.isPerfClass()) {
            assertTrue("performance class is only defined for Handheld devices",
                    MediaUtils.isHandheld());
        }
    }

    @Test
    @CddTest(
            requirements = {
                "2.2.7.3/7.1.1.1/H-1-1",
                "2.2.7.3/7.1.1.1/H-2-1",
                "2.2.7.3/7.1.1.3/H-1-1",
                "2.2.7.3/7.1.1.3/H-2-1",
            })
    public void testMinimumResolutionAndDensity() {
        int density = Utils.getDisplayDpi();
        int longPix = Utils.getMaxDisplayDim();
        int shortPix = Utils.getMinDisplayDim();

        Log.i(TAG, String.format("dpi=%d size=%dx%dpix", density, longPix, shortPix));

        PerformanceClassEvaluator pce = pcRule.getPerformanceClassEvaluator();
        ScreenResolutionRRequirement r7_1_1_1__h_1_1 = Requirements.addR7_1_1_1__H_1_1().to(pce);
        ScreenResolutionRequirement r7_1_1_1__h_2_1 = Requirements.addR7_1_1_1__H_2_1().to(pce);
        ScreenDensityRRequirement r7_1_1_3__h_1_1 = Requirements.addR7_1_1_3__H_1_1().to(pce);
        ScreenDensityRequirement r7_1_1_3__h_2_1 = Requirements.addR7_1_1_3__H_2_1().to(pce);

        r7_1_1_1__h_1_1.setLongResolutionPixels(longPix);
        r7_1_1_1__h_2_1.setLongResolutionPixels(longPix);

        r7_1_1_1__h_1_1.setShortResolutionPixels(shortPix);
        r7_1_1_1__h_2_1.setShortResolutionPixels(shortPix);

        r7_1_1_3__h_1_1.setDisplayDensityDpi(density);
        r7_1_1_3__h_2_1.setDisplayDensityDpi(density);
    }

    @Test
    @CddTest(
            requirements = {
                "2.2.7.3/7.6.1/H-1-1",
                "2.2.7.3/7.6.1/H-2-1",
            })
    public void testMinimumMemory() {
        long totalMemoryMb = Utils.getTotalMemoryMb();

        Log.i(TAG, String.format("Total device memory = %,d MB", totalMemoryMb));

        PerformanceClassEvaluator pce = pcRule.getPerformanceClassEvaluator();
        Android11MemoryRequirement r7_6_1_h_1_1 = Requirements.addR7_6_1__H_1_1().to(pce);
        MemoryRequirement r7_6_1_h_2_1 = Requirements.addR7_6_1__H_2_1().to(pce);

        r7_6_1_h_1_1.setPhysicalMemoryMb(totalMemoryMb);
        r7_6_1_h_2_1.setPhysicalMemoryMb(totalMemoryMb);
    }

    @Test
    @CddTest(requirements = {"2.2.7.3/7.1.1.3/H-3-1"})
    public void testDisplayHdr() {
        PerformanceClassEvaluator pce = pcRule.getPerformanceClassEvaluator();
        HDRDisplayRequirement req = Requirements.addR7_1_1_3__H_3_1().to(pce);

        DisplayManager dm = Utils.getContext().getSystemService(DisplayManager.class);
        Display dd = dm.getDisplay(Display.DEFAULT_DISPLAY);
        req.setIsHdr(dd.isHdr());
        req.setDisplayLuminanceNits(dd.getHdrCapabilities().getDesiredMaxAverageLuminance());
    }

    @Test
    @CddTest(requirements = {"7.11/C-1-3"})
    public void validBuildConstant() {
        var validValues =
                Arrays.stream(MediaPerformanceClass.values())
                        .map(MediaPerformanceClass::getNumber)
                        .filter(x -> x >= 0)
                        .toList();
        assertWithMessage("android.os.Build.VERSION.MEDIA_PERFORMANCE_CLASS)")
                .that(Build.VERSION.MEDIA_PERFORMANCE_CLASS)
                .isIn(validValues);
    }
}
