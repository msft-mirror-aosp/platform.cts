/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static android.multiuser.Flags.FLAG_PROFILES_FOR_ALL;

import android.platform.test.flag.junit.host.DeviceFlags;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;

/**
 * Helper for user-related needs.
 */
public final class UserUtil {

    public static final String CONFIG_SUPPORT_PROFILES_ON_NON_MAIN_USER =
            "config_supportProfilesOnNonMainUser";

    private final ITestDevice mTestDevice;

    public UserUtil(ITestDevice testDevice) {
        mTestDevice = testDevice;
    }

    /** Checks whether the device supports profile on non-main user. */
    public boolean isProfilesOnNonMainUserSupported() throws DeviceNotAvailableException {
        var flags = DeviceFlags.createDeviceFlags(mTestDevice);
        String flagValue = flags.getFlagValue(FLAG_PROFILES_FOR_ALL);
        CLog.v(
                "isProfilesOnNonMainUserSupported(): flag %s is %s",
                FLAG_PROFILES_FOR_ALL, flagValue);
        if (!Boolean.valueOf(flagValue)) {
            return false;
        }

        boolean configValue =
                new OverlayUtil(mTestDevice)
                        .getBooleanFrameworkConfig(CONFIG_SUPPORT_PROFILES_ON_NON_MAIN_USER);
        CLog.v(
                "isProfilesOnNonMainUserSupported(): config %s is %b",
                CONFIG_SUPPORT_PROFILES_ON_NON_MAIN_USER, configValue);
        return configValue;
    }

}
