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

package com.android.compatibility.common.util;

import android.content.Context;
import android.content.res.Resources;


/**
 * XrUtil is used to check properties and configs related to Android XR.
 */
public class XrUtil {
    /**
     * This checks if we support third party magnification on XR devices. By default,
     * Android XR does not support third party magnification.
     */
    public static boolean supportsXrThirdPartyMagnificationServices(Context context) {
        return !FeatureUtil.isXrHeadset() || hasXrThirdPartyMagnificationServicesConfig(context);
    }


    private static boolean hasXrThirdPartyMagnificationServicesConfig(Context context) {
        try {
            return context.getResources().getBoolean(
                    Resources.getSystem().getIdentifier(
                            "config_supportsXRThirdPartyMagnification", "bool", "android"));
        } catch (Resources.NotFoundException e) {
            // Third party magnification is opt-in.
            return false;
        }
    }
}

