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
package com.android.cts.deviceinfo;

import android.hardware.display.DisplayManager;
import android.view.Display;
import android.view.Display.HdrCapabilities;
import android.view.Display.Mode;

import com.android.compatibility.common.deviceinfo.DeviceInfo;
import com.android.compatibility.common.util.DeviceInfoStore;

import java.util.Arrays;

public class DisplayDeviceInfo extends DeviceInfo {

    private static final String HDR_CAPABILITIES = "hdr_capabilities";
    private static final String SUPPORTED_HDR_TYPES = "supported_hdr_types";
    private static final String MAX_LUMINANCE = "max_luminance";
    private static final String MAX_AVERAGE_LUMINANCE = "max_average_luminance";
    private static final String MIN_LUMINANCE = "min_luminance";

    @Override
    protected void collectDeviceInfo(DeviceInfoStore store) throws Exception {
        DisplayManager displayManager = (DisplayManager)
                getContext().getSystemService(DisplayManager.class);
        store.startArray(HDR_CAPABILITIES);

        //TODO(b/331960279): folables support different hdr capabilities
        //on different displays
        //Currently, we only collect the information on the default display.
        store.startGroup();
        Display display = displayManager.getDisplay(Display.DEFAULT_DISPLAY);
        if (display != null && display.isHdr()) {
            int[] hdrTypes = Arrays.stream(display.getSupportedModes())
                    .map(Mode::getSupportedHdrTypes)
                    .flatMapToInt(Arrays::stream)
                    .distinct().toArray();
            store.addArrayResult(SUPPORTED_HDR_TYPES, hdrTypes);

            HdrCapabilities hdrCapabilities = display.getHdrCapabilities();

            store.addResult(MAX_LUMINANCE, hdrCapabilities.getDesiredMaxLuminance());
            store.addResult(MAX_AVERAGE_LUMINANCE,
                    hdrCapabilities.getDesiredMaxAverageLuminance());
            store.addResult(MIN_LUMINANCE, hdrCapabilities.getDesiredMinLuminance());
            //TODO(b/329466383): add max hdr/sdr ratio to the collector
        }
        store.endGroup();

        store.endArray(); // HDR_CAPABILITIES
    }
}
