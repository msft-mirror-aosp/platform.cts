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

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;

/** Helper for overlay-related needs. */
public final class OverlayUtil {

    private final ITestDevice mTestDevice;

    public OverlayUtil(ITestDevice testDevice) {
        mTestDevice = testDevice;
    }

    /** Gets the value of a config defined by the core framework resources. */
    public boolean getBooleanFrameworkConfig(String config) throws DeviceNotAvailableException {
        String cmd = "cmd overlay lookup android android:bool/" + config;
        String result = mTestDevice.executeShellCommand(cmd).trim();
        CLog.d("getBooleanFrameworkConfig(%s): overlay lookup returned '%s'", config, result);
        switch (result) {
            case "true":
            case "TRUE":
                return true;
            case "false":
            case "FALSE":
                return false;
            default:
                throw new IllegalStateException("invalid result for '" + cmd + "': " + result);
        }
    }
}
