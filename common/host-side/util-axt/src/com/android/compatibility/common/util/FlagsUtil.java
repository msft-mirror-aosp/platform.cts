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

import static android.multiuser.Flags.FLAG_PROFILES_FOR_ALL;

import android.platform.test.flag.junit.host.DeviceFlags;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;

/** Helper for flag-related needs. */
public final class FlagsUtil {

    private final ITestDevice mTestDevice;

    public FlagsUtil(ITestDevice testDevice) {
        mTestDevice = testDevice;
    }

    /**
     * Gets the value of the given boolean {@code flag}.
     *
     * @return flag value or {@code false} in case it cannot be parsed.
     */
    public boolean getBooleanFlag(String flag) throws DeviceNotAvailableException {
        try {
            var flags = DeviceFlags.createDeviceFlags(mTestDevice);
            String flagValue = flags.getFlagValue(FLAG_PROFILES_FOR_ALL);
            CLog.v("getBooleanFlag(%s): returning value of '%s'", flag, flagValue);
            return Boolean.valueOf(flagValue);
        } catch (RuntimeException | Error e) {
            CLog.e("getBooleanFlag(%s) threw unexpected exception; returning false", flag);
            CLog.e(e);
            return false;
        }
    }
}
