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
import com.android.server.display.feature.flags.Flags;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class DisplayDeviceInfo extends DeviceInfo {

    private static final String HDR_CAPABILITIES = "hdr_capabilities";
    private static final String SUPPORTED_HDR_TYPES = "supported_hdr_types";
    private static final String MAX_LUMINANCE = "max_luminance";
    private static final String MAX_AVERAGE_LUMINANCE = "max_average_luminance";
    private static final String MIN_LUMINANCE = "min_luminance";
    private static final String MAX_HDR_SDR_RATIO = "max_hdr_sdr_ratio";
    private static final String DISPLAY_INFOS = "display_infos";
    private static final String HAS_ARR_SUPPORT = "has_arr_support";
    private static final String SUPPORTED_REFRESH_RATES = "supported_refresh_rates";
    private static final String DISPLAY_MODES = "display_modes";
    private static final String VSYNC_RATE = "vsync_rate";
    private static final String PEAK_REFRESH_RATE = "peak_refresh_rate";
    private static final String IS_SYNTHETIC = "is_synthetic";
    private static final String WIDTH = "width";
    private static final String HEIGHT = "height";

    @Override
    protected void collectDeviceInfo(DeviceInfoStore store) throws Exception {
        DisplayManager displayManager = (DisplayManager)
                getContext().getSystemService(DisplayManager.class);

        List<Display> internalDisplays = Arrays.stream(displayManager.getDisplays())
                .filter(d -> d != null)
                .filter(d -> d.getType() == Display.TYPE_INTERNAL)
                .collect(Collectors.toList());

        store.startArray(HDR_CAPABILITIES);
        for (Display display : internalDisplays) {
            if (display.isHdr()) {
                store.startGroup();
                int[] hdrTypes =
                        Arrays.stream(display.getSupportedModes())
                                .map(Mode::getSupportedHdrTypes)
                                .flatMapToInt(Arrays::stream)
                                .distinct()
                                .toArray();
                store.addArrayResult(SUPPORTED_HDR_TYPES, hdrTypes);

                HdrCapabilities hdrCapabilities = display.getHdrCapabilities();

                store.addResult(MAX_LUMINANCE, hdrCapabilities.getDesiredMaxLuminance());
                store.addResult(
                        MAX_AVERAGE_LUMINANCE, hdrCapabilities.getDesiredMaxAverageLuminance());
                store.addResult(MIN_LUMINANCE, hdrCapabilities.getDesiredMinLuminance());
                if (Flags.highestHdrSdrRatioApi()) {
                    store.addResult(MAX_HDR_SDR_RATIO, display.getHighestHdrSdrRatio());
                }
                store.endGroup();
            }
        }
        store.endArray(); // HDR_CAPABILITIES

        store.startArray(DISPLAY_INFOS);
        for (Display display : internalDisplays) {
            store.startGroup();
            if (Flags.enableHasArrSupport()) {
                store.addResult(HAS_ARR_SUPPORT, display.hasArrSupport());
            }
            if (Flags.enableGetSupportedRefreshRates()) {
                store.addArrayResult(SUPPORTED_REFRESH_RATES, display.getSupportedRefreshRates());
            }

            store.startArray(DISPLAY_MODES);
            for (Mode mode : display.getSupportedModes()) {
                store.startGroup();
                store.addResult(VSYNC_RATE, mode.getVsyncRate());
                store.addResult(PEAK_REFRESH_RATE, mode.getRefreshRate());
                store.addResult(IS_SYNTHETIC, mode.isSynthetic());
                store.addResult(WIDTH, mode.getPhysicalWidth());
                store.addResult(HEIGHT, mode.getPhysicalHeight());
                store.endGroup();
            }
            store.endArray(); // DISPLAY_MODES
            store.endGroup();
        }
        store.endArray(); // DISPLAY_INFOS
    }
}
