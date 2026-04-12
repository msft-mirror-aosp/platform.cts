/*
 * Copyright (C) 2026 The Android Open Source Project
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

package android.media.cts;

import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.view.Display;

/**
 * Utilities for multi-display tests.
 */
public class MultiDisplayUtil {

    /**
     * Returns true if the device has more than one physical display.
     */
    public static boolean isMultiPhysicalDisplay(Context context) {
        DisplayManager displayManager = context.getSystemService(DisplayManager.class);
        // Note: getDisplays() only returns public displays and skips private ones (like
        // the instrument cluster). This is acceptable as MediaProjection is not
        // supported on private displays.
        Display[] displays = displayManager.getDisplays();
        int physicalDisplayCount = 0;
        for (Display display : displays) {
            if (display.getType() != Display.TYPE_VIRTUAL) {
                physicalDisplayCount++;
            }
        }
        return physicalDisplayCount > 1;
    }

    /**
     * Assumes that the device has only one physical display.
     */
    // TODO(b/500029429): Add the missing step to select the physical display when running on
    //     multi-physical-display targets, then remove this assumption.
    public static void assumeDeviceHasOnlyOnePhysicalDisplay(Context context) {
        assumeTrue(
                "This test requires a device with only one physical display to function correctly",
                !isMultiPhysicalDisplay(context));
    }
}
