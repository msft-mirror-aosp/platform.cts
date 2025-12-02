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
package com.android.cts.managedprofile;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import org.junit.Test;

import java.util.List;

public class SetApplicationHiddenTest extends BaseManagedProfileTest {
    private static final String TAG = SetApplicationHiddenTest.class.getSimpleName();

    /** Hide all apps - not really a test, intended to be invoked for side effect. */
    @Test
    public void testHideAllApps() throws Exception {
        PackageManager pm = mContext.getPackageManager();
        List<PackageInfo> packages = pm.getInstalledPackages(0);
        for (PackageInfo pkg : packages) {
            if (mDevicePolicyManager.setApplicationHidden(
                    ADMIN_RECEIVER_COMPONENT, pkg.packageName, true)) {
                Log.d(TAG, "Hid package: " + pkg.packageName);
            } else {
                Log.d(TAG, "Couldn't hide package: " + pkg.packageName);
            }
        }
    }
}
