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

package android.appsecurity.cts;

import com.android.ddmlib.Log.LogLevel;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.UserInfo;
import com.android.tradefed.log.ITestLogger;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestLoggerReceiver;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.ITargetCleaner;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.targetprep.TargetSetupError;

import java.util.Arrays;

/**
 * Creates secondary and tertiary users for use during a test suite.
 */
public class AppSecurityPreparer implements ITargetPreparer, ITargetCleaner, ITestLoggerReceiver {

    private ITestLogger mLogger;

    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo)
            throws TargetSetupError, BuildError, DeviceNotAvailableException {
        // Clean up any lingering users from other tests to ensure that we have
        // best shot at creating the users we need below.
        removeExtraUsers(device);

        int maxUsers = device.getMaxNumberOfUsersSupported();
        CLog.d("setUp(): maxUsers=%d, currentUser=%d", maxUsers, device.getCurrentUser());
        try {
            if (maxUsers > 1) {
                String name = "CTS_User1_" + System.nanoTime();
                int userId = device.createUser(name);
                CLog.logAndDisplay(
                        LogLevel.INFO, "Created 1st user (id=%d, name=%s)", userId, name);
            }
            if (maxUsers > 2) {
                String name = "CTS_User2_" + System.nanoTime();
                int userId = device.createUser(name);
                CLog.logAndDisplay(
                        LogLevel.INFO, "Created 2nd user (id=%d, name=%s)", userId, name);
            }
        } catch (IllegalStateException e) {
            try (InputStreamSource logcat = device.getLogcatDump()) {
                mLogger.testLog("AppSecurityPrep_failed_create_user", LogDataType.LOGCAT, logcat);
            }
            throw new TargetSetupError("Failed to create user.", e, device.getDeviceDescriptor());
        }
    }

    @Override
    public void tearDown(ITestDevice device, IBuildInfo buildInfo, Throwable throwable)
            throws DeviceNotAvailableException {
        removeExtraUsers(device);
    }

    private void removeExtraUsers(ITestDevice device) throws DeviceNotAvailableException {
        int[] userIds = Utils.getAllUsers(device);
        Integer mainUserId = device.getMainUserId();
        int currentUserId = device.getCurrentUser();
        CLog.d(
                "removeExtraUsers(): allUsers=%s, mainUserId=%s, currentUserId=%d",
                Arrays.toString(userIds), mainUserId, currentUserId);
        for (int i = 1; i < userIds.length; i++) {
            int userId = userIds[i];
            if (userId != currentUserId
                    && userId != UserInfo.USER_SYSTEM
                    && (mainUserId == null || userId != mainUserId)) {
                CLog.logAndDisplay(LogLevel.INFO, "Removing user (id=%d)", userId);
                device.removeUser(userId);
            }
        }
    }

    @Override
    public void setTestLogger(ITestLogger testLogger) {
        mLogger = testLogger;
    }
}
