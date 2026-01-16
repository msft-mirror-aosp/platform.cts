/*
 * Copyright 2019 The Android Open Source Project
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

package android.security.identity.cts;

import android.content.Context;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageManager;
import android.security.identity.IdentityCredentialStore;

import androidx.test.InstrumentationRegistry;

class TestUtil {
    private static final String TAG = "Util";

    // Returns 0 if not implemented. Otherwise returns the feature version.
    //
    static int getFeatureVersion() {
        Context appContext = InstrumentationRegistry.getTargetContext();
        PackageManager pm = appContext.getPackageManager();

        int featureVersionFromPm = 0;
        if (pm.hasSystemFeature(PackageManager.FEATURE_IDENTITY_CREDENTIAL_HARDWARE)) {
            FeatureInfo info = null;
            FeatureInfo[] infos = pm.getSystemAvailableFeatures();
            for (int n = 0; n < infos.length; n++) {
                FeatureInfo i = infos[n];
                if (i.name.equals(PackageManager.FEATURE_IDENTITY_CREDENTIAL_HARDWARE)) {
                    info = i;
                    break;
                }
            }
            if (info != null) {
                featureVersionFromPm = info.version;
            }
        }

        // Use of the system feature is not required since Android 12. So for Android 11
        // return 202009 which is the feature version shipped with Android 11.
        if (featureVersionFromPm == 0) {
            IdentityCredentialStore store = IdentityCredentialStore.getInstance(appContext);
            if (store != null) {
                featureVersionFromPm = 202009;
            }
        }

        return featureVersionFromPm;
    }

    // Returns true if, and only if, the Identity Credential HAL (and credstore) is implemented
    // on the device under test.
    static boolean isHalImplemented() {
        Context appContext = InstrumentationRegistry.getTargetContext();
        IdentityCredentialStore store = IdentityCredentialStore.getInstance(appContext);
        PackageManager pm = appContext.getPackageManager();

        if (store != null && pm.hasSystemFeature(
                PackageManager.FEATURE_IDENTITY_CREDENTIAL_HARDWARE)) {
            return true;
        }
        return false;
    }

    // Returns true if, and only if, the Direct Access Identity Credential HAL (and credstore) is
    // implemented on the device under test.
    static boolean isDirectAccessHalImplemented() {
        Context appContext = InstrumentationRegistry.getTargetContext();
        IdentityCredentialStore store = IdentityCredentialStore.getDirectAccessInstance(appContext);
        if (store != null) {
            return true;
        }
        return false;
    }

    // Returns true if the device supports secure lock screen.
    static boolean isLockScreenSupported() {
        Context appContext = InstrumentationRegistry.getTargetContext();
        PackageManager pm = appContext.getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_SECURE_LOCK_SCREEN);
    }
}
