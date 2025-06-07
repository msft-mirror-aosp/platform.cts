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

package android.wearable.cts;

import android.content.Context;
import android.content.pm.PackageManager;

/** Utils for Wearable Sensing tests. */
public class TestUtils {
    private final PackageManager mPackageManager;

    public TestUtils(Context context) {
        mPackageManager = context.getPackageManager();
    }

    /** Returns whether Wearable Sensing tests should be skipped. */
    public boolean shouldSkipWearableSensingTest() {
        return !hasCompanionDeviceSetupFeature() || isTelevision() || isWatch() || isAutomotive();
    }

    private boolean hasCompanionDeviceSetupFeature() {
        return mPackageManager.hasSystemFeature(PackageManager.FEATURE_COMPANION_DEVICE_SETUP);
    }

    private boolean isTelevision() {
        return mPackageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK);
    }

    private boolean isWatch() {
        return mPackageManager.hasSystemFeature(PackageManager.FEATURE_WATCH);
    }

    private boolean isAutomotive() {
        return mPackageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE);
    }
}
