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

package android.telephony.cts;

import android.os.Build;
import android.os.SystemProperties;

// Utility for calculating and performing checks related to API levels.
public class ApiLevelUtil {
    /**
     * Calculate the vendor API level for a given Build.VERSION_CODES.
     *
     * @param versionCode The Build.VERSION_CODES to calculate the vendor API level of
     * @return The corresponding vendor API level
     */
    public static int getVendorApiLevelFor(int versionCode) {
        // Version codes prior to 35 use a 1-per-year scheme; however, starting with 36 the scheme
        // was changed "in place" so that the vendor API level no longer is an API level, but rather
        // an interface freeze date. It increments by 100 (1 year) for each major SDK beyond SDK 35.
        // By convention the "year" starts in April.
        if (versionCode < Build.VERSION_CODES.BAKLAVA) {
            return versionCode;
        }
        return 100 * (versionCode - 35) + 202404;
    }

    /**
     * Obtain the device's vendor API level.
     *
     * @return The device's vendor API level
     */
    public static int getDeviceVendorApiLevel() {
        return SystemProperties.getInt("ro.vendor.api_level", Build.VERSION.DEVICE_INITIAL_SDK_INT);
    }

    /**
     * Determine whether the device's vendor API level is at least the vendor API level for the
     * provided version code.
     *
     * @param versionCode The Build.VERSION_CODES to check against
     * @return true if the device's vendor API level is at least the vendor API level for
     *     versionCode
     */
    public static boolean isVendorApiLevelAtLeast(int versionCode) {
        return getDeviceVendorApiLevel() >= getVendorApiLevelFor(versionCode);
    }

    /**
     * Determine whether the device's vendor API level is greater than the vendor API level for the
     * provided version code.
     *
     * @param versionCode The Build.VERSION_CODES to check against
     * @return true if the device's vendor API level is greater than the vendor API level for
     *     versionCode
     */
    public static boolean isVendorApiLevelGreaterThan(int versionCode) {
        return getDeviceVendorApiLevel() > getVendorApiLevelFor(versionCode);
    }

    /**
     * Determine whether the device's vendor API level is less than the vendor API level for the
     * provided version code.
     *
     * @param versionCode The Build.VERSION_CODES to check against
     * @return true if the device's vendor API level is less than the vendor API level for
     *     versionCode
     */
    public static boolean isVendorApiLevelLessThan(int versionCode) {
        return getDeviceVendorApiLevel() < getVendorApiLevelFor(versionCode);
    }

    private ApiLevelUtil() {}
}
