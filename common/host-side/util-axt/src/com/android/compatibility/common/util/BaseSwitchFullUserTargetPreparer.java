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
import com.android.tradefed.device.UserInfo.UserType;
import com.android.tradefed.device.UserSwitcher;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.targetprep.BaseSwitchUserTargetPreparer;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.TargetSetupError;

import javax.annotation.Nullable;

/**
 * Base class for custom {@link BaseSwitchUserTargetPreparer}s that always switch to a {@link
 * android.content.pm.UserInfo#isFull() full user}.
 */
public abstract class BaseSwitchFullUserTargetPreparer extends BaseSwitchUserTargetPreparer {

    // NOTE: this class body is pretty much the same as SwitchUserTargetPreparer, except that it
    // doesn't have an @Option attribute to set the UserType (which is hard-coded)

    private @Nullable UserSwitcher mUserSwitcher; // instantiated on setUp

    @Override
    public void setUp(TestInformation testInformation)
            throws TargetSetupError, BuildError, DeviceNotAvailableException {
        mUserSwitcher = new UserSwitcher(testInformation.getDevice(), UserType.FULL);
        int switchedUser = mUserSwitcher.switchUser();
        setPreparedUser(testInformation, switchedUser);
    }

    @Override
    public void tearDown(TestInformation testInformation, Throwable e)
            throws DeviceNotAvailableException {
        mUserSwitcher.switchBack();
        setPreparedUser(testInformation, /* userId= */ null);
    }
}
