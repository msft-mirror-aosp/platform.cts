/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.compatibility.common.util;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.support.test.InstrumentationRegistry;

/**
 * Device-side utility class for PackageManager-related operations
 */
public class PackageUtil {

    private static final int SYSTEM_APP_MASK =
            ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP;

    /** Returns true if a package with the given name exists on the device */
    public static boolean exists(String packageName) {
        try {
            return (getPackageManager().getApplicationInfo(packageName,
                    PackageManager.GET_META_DATA) != null);
        } catch(PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /** Returns true if a package with the given name does NOT exist on the device */
    public static boolean doesNotExist(String packageName) {
        return !exists(packageName);
    }

    /** Returns true if the app for the given package name is a system app for this device */
    public static boolean isSystemApp(String packageName) {
        try {
            ApplicationInfo ai = getPackageManager().getApplicationInfo(packageName,
                    PackageManager.GET_META_DATA);
            return ai != null && ((ai.flags & SYSTEM_APP_MASK) != 0);
        } catch(PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /** Returns true if the app for the given package name is NOT a system app for this device */
    public static boolean isNotSystemApp(String packageName) {
        return isSystemApp(packageName);
    }

    private static PackageManager getPackageManager() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext().getPackageManager();
    }
}
