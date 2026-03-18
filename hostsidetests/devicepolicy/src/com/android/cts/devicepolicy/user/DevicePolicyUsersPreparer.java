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
package com.android.cts.devicepolicy.user;


import com.android.compatibility.common.util.BaseSwitchFullUserTargetPreparer;
import com.android.ddmlib.Log.LogLevel;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.SwitchUserTargetPreparer;
import com.android.tradefed.targetprep.TargetSetupError;

import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;

import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Nullable;

/**
 * Convenience {@link SwitchUserTargetPreparer} that provides a {@link UsersOracle} singleton.
 *
 * <p><b>NOTE:</b> callers <b>MUST</b> include a {@code DevicePolicyUsersPreparer} in their {@code
 * AndroidTest.xml} before calling {@link #getUsersOracleInstance(TestInformation)}.
 */
public final class DevicePolicyUsersPreparer extends BaseSwitchFullUserTargetPreparer {

    private static final AtomicReference<UsersOracle> sOracle = new AtomicReference<>();

    @Override
    public void setUp(TestInformation testInformation)
            throws TargetSetupError, BuildError, DeviceNotAvailableException {
        super.setUp(testInformation);
        var oracle = sOracle.get();
        if (oracle != null) {
            CLog.w("setUp(): sOracle already set (%s)", oracle);
            return;
        }
        createAndSetUsersOracleInstance("setUp()", testInformation);
    }

    @Override
    public void tearDown(TestInformation testInformation, Throwable e)
            throws DeviceNotAvailableException {
        CLog.d("tearDown(): resetting sOracle (%s)", sOracle.get());
        sOracle.set(null);
        super.tearDown(testInformation, e);
    }

    @FormatMethod
    private static void logAndDisplay(@FormatString String msgFmt, @Nullable Object... msgArgs) {
        CLog.logAndDisplay(LogLevel.INFO, msgFmt, msgArgs);
    }

    /**
     * Gets the {@link UsersOracle} singleton.
     *
     * @return singleton created on {@code setUp()}, or a new one if {@code setUp()} was not called
     *     (or if it {@code #tearDown()} was called afterwards).
     */
    public static UsersOracle getUsersOracleInstance(TestInformation testInformation)
            throws DeviceNotAvailableException {
        var oracle = sOracle.get();
        if (oracle != null) {
            return oracle;
        }
        return createAndSetUsersOracleInstance("getUsersOracleInstance()", testInformation);
    }

    private static UsersOracle createAndSetUsersOracleInstance(
            String method, TestInformation testInformation) throws DeviceNotAvailableException {
        var newOracle = UsersOracle.createInstance(testInformation);
        if (sOracle.compareAndSet(null, newOracle)) {
            logAndDisplay("%s: set sOracle (%s)", method, newOracle);
            return newOracle;
        }
        var existingOracle = sOracle.get();
        // Not a big deal, but better log it..
        CLog.w(
                "%s: sOracle (%s) was set by another thread in parallel after a new one (%s) was "
                        + "created; returning that one instead");
        return existingOracle;
    }
}
