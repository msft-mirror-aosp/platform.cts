/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.compatibility.common.preconditions;

import android.content.Context;
import android.content.pm.PackageManager;

/**
 * SystemUiHelper is used to check SystemUI related status such as whether or not a
 * traditional status bar is supported on this device platform.
 */
public class SystemUiHelper {
    /**
     * This helper returns true if the device's system UI does not have a traditional android
     * system status bar.  E.g. Android Automotive, WearOS, etc.
     */
    public static boolean hasNoTraditionalStatusBar(Context context) {
        PackageManager packageManager = context.getPackageManager();
        boolean isTelevision = packageManager != null
                && (packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
                || packageManager.hasSystemFeature(PackageManager.FEATURE_TELEVISION));

        boolean isWatch = context.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_WATCH);

        return isTelevision || isWatch || Boolean.getBoolean(
                "persist.sysui.nostatusbar");

    }
}
