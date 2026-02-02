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

import static android.mediav2.common.cts.CodecTestBase.compileMediaTypesList;
import static android.mediav2.common.cts.CodecTestBase.hasSupportForProfile;
import static android.mediav2.common.cts.CodecTestBase.selectCodecs;
import static android.view.Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION;
import static android.view.Display.HdrCapabilities.HDR_TYPE_HDR10;
import static android.view.Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS;
import static android.view.Display.HdrCapabilities.HDR_TYPE_HLG;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.hardware.display.DisplayManager;
import android.media.MediaFormat;
import android.mediav2.common.cts.CodecTestBase;
import android.mediav2.common.cts.CodecTestBase.ComponentClass;
import android.view.Display;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.CddTest;
import com.android.compatibility.common.util.MediaUtils;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Checks if all required codecs are listed in media codec list. The scope of this test is to only
 * check if the device has advertised all the required codecs. Their functionality and other cdd
 * requirements are not verified.
 */
@SmallTest
/**
 * The tests of this class verify device requirements and not component requirements. It collects
 * all components present on the device - both mainline and vendor - evaluates their capabilities,
 * and validates compliance with CDD requirements. Because the test assesses the complete device
 * configuration, it cannot be executed with component filtering for CTS or MCTS modes. It cannot be
 * annotated as ModuleSpecific or FrameworkSpecific.
 */
@RunWith(AndroidJUnit4.class)
public class CodecListTest {
    static final String MEDIA_TYPE_PREFIX_KEY = "media-type-prefix";
    static String mediaTypePrefix;

    static {
        android.os.Bundle args = InstrumentationRegistry.getArguments();
        mediaTypePrefix = args.getString(MEDIA_TYPE_PREFIX_KEY);
    }

    /**
     * Tests if the device under test has support for required codecs as guided by cdd
     */
    @CddTest(requirements = {"2.2.2/5.1/H-0-1", "2.2.2/5.1/H-0-2", "2.2.2/5.1/H-0-3",
            "2.2.2/5.2/H-0-1", "2.2.2/5.2/H-0-2", "2.2.2/5.2/H-0-3", "2.2.2/5.3/H-0-1",
            "2.2.2/5.3/H-0-2", "2.2.2/5.3/H-0-3", "2.2.2/5.3/H-0-4", "2.2.2/5.3/H-0-5",
            "2.2.2/5.3/H-0-6", "2.3.2/5.2/T-0-1", "2.3.2/5.2/T-0-2", "2.3.2/5.2/T-0-3",
            "2.3.2/5.3.3/T-0-1", "2.3.2/5.3.4/T-0-2", "2.3.2/5.3.5/T-0-3", "2.3.2/5.3.6/T-0-4",
            "2.3.2/5.3.7/T-0-5", "2.3.2/5.3.1/T-0-6", "2.3.2/5.3.2/T-0-7", "2.5.2/5.2/A-0-1",
            "2.5.2/5.2/A-0-2", "2.5.2/5.3/A-0-1", "2.5.2/5.3/A-0-2", "2.5.2/5.3/A-0-3",
            "2.5.2/5.3/A-0-4", "5/C-0-2", "5.1.1/C-1-2", "5.1.1/C-1-3", "5.1.2/C-1-5",
            "5.1.2/C-1-6", "5.1.2/C-1-8", "5.1.2/C-1-9", "5.1.2/C-1-10", "5.2/C-1-1"})
    @Test
    public void testCddRequiredCodecsAvailability() {
        final boolean needAudio = mediaTypePrefix == null || mediaTypePrefix.startsWith("audio");
        final boolean needVideo = mediaTypePrefix == null || mediaTypePrefix.startsWith("video");
        boolean[] modes = {true, false};
        for (boolean isEncoder : modes) {
            ArrayList<String> cddRequiredMediaTypeList =
                    CodecTestBase.compileRequiredMediaTypeList(isEncoder, needAudio, needVideo);
            for (String mediaType : cddRequiredMediaTypeList) {
                String log = String.format("no %s found for mediaType %s as required by cdd ",
                        isEncoder ? "encoder" : "decoder", mediaType);
                assertTrue(log, isEncoder ? CodecTestBase.hasEncoder(mediaType) :
                        CodecTestBase.hasDecoder(mediaType));
            }
        }
        if (MediaUtils.hasCamera()) {
            assertTrue("device has neither VP8 or AVC encoding",
                    CodecTestBase.hasEncoder(MediaFormat.MIMETYPE_VIDEO_AVC) ||
                            CodecTestBase.hasEncoder(MediaFormat.MIMETYPE_VIDEO_VP8));
        }
    }

    /**
     * For all the available decoders on the device, the test checks if their decoding capabilities
     * are in sync with the device's display capabilities. Precisely, if device implementations
     * advertise a HDR profile support through Display.HdrCapabilities, then there should be at
     * least one decoder capable of handling that profile.
     */
    @Test
    @CddTest(requirements = {"5.1.7/C-2-1"})
    @ApiTest(apis = {"android.media.MediaCodecInfo.CodecCapabilities#profileLevels"})
    public void testHDRDisplayCapabilities() {
        assumeTrue("Test needs Android 13", CodecTestBase.IS_AT_LEAST_T);

        DisplayManager dm = CodecTestBase.getContext().getSystemService(DisplayManager.class);
        int[] hdrTypes = dm.getDisplay(Display.DEFAULT_DISPLAY).getMode().getSupportedHdrTypes();
        assumeTrue("Device display has no hdr capabilities", hdrTypes.length > 0);

        for (int hdrType : hdrTypes) {
            HashMap<String, int[]> profileMap = null;
            if (hdrType == HDR_TYPE_HLG) {
                profileMap = CodecTestBase.PROFILE_HLG_MAP;
            } else if (hdrType == HDR_TYPE_HDR10) {
                profileMap = CodecTestBase.PROFILE_HDR10_MAP;
            } else if (hdrType == HDR_TYPE_HDR10_PLUS) {
                profileMap = CodecTestBase.PROFILE_HDR10_PLUS_MAP;
            } else if (hdrType == HDR_TYPE_DOLBY_VISION) {
                profileMap = CodecTestBase.PROFILE_DOLBY_HDR_MAP;
            }
            assertNotNull("Did not find a profile list for hdrType " + hdrType, profileMap);
            boolean foundDecoder = false;
            outerloop:
            for (Map.Entry<String, int[]> entry : profileMap.entrySet()) {
                ArrayList<String> decoders = selectCodecs(entry.getKey(), null, null, false);
                for (String decoder : decoders) {
                    for (int profile : entry.getValue()) {
                        if (hasSupportForProfile(decoder, entry.getKey(), profile)) {
                            foundDecoder = true;
                            break outerloop;
                        }
                    }
                }
            }
            assertTrue("Device display advertises support for hdrType " + hdrType
                    + " but there is no decoder capable of handling that profile", foundDecoder);
        }
    }

    /**
     * For all the available encoders on the device, the test checks if their encoding
     * capabilities are in sync with the device's decoding capabilities.
     */
    @CddTest(requirements = {"5/C-0-3"})
    @Test
    public void testDecoderAvailability() {
        ArrayList<String> mediaTypes = compileMediaTypesList(ComponentClass.ALL, true, true);
        for (String mediaType : mediaTypes) {
            if (selectCodecs(mediaType, null, null, true).size() > 0) {
                assertTrue("Device advertises support for encoding " + mediaType +
                                ", but not decoding it",
                        selectCodecs(mediaType, null, null, false).size() > 0);
            }
        }
    }
}

