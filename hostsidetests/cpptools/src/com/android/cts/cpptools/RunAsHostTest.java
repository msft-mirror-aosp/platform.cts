/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.cts.cpptools;

import static com.android.tradefed.device.UserInfo.PER_USER_RANGE;
import static com.android.tradefed.device.UserInfo.USER_SYSTEM;

import static com.google.common.truth.Truth.assertWithMessage;

import com.android.tradefed.testtype.DeviceTestCase;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

/**
 * Test to check the host can execute commands via "adb shell run-as".
 */
public class RunAsHostTest extends DeviceTestCase {

    private String mOriginalUseCurrentUser;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mOriginalUseCurrentUser = getRunAsShouldUseCurrentUser();
    }

    @Override
    protected void tearDown() throws Exception {
        setRunAsShouldUseCurrentUser(mOriginalUseCurrentUser);
        super.tearDown();
    }

    /**
     * Tests that host can execute shell commands as a debuggable app via adb, as user 0.
     *
     * @throws Exception on device communication errors
     */
    public void testRunAs_systemUser() throws Exception {
        setRunAsShouldUseCurrentUser("false");
        verifyRunAsUser(USER_SYSTEM);
    }

    /**
     * Tests that host can execute shell commands as a debuggable app via adb, as current user.
     *
     * @throws Exception on device communication errors
     */
    public void testRunAs_currentUser() throws Exception {
        setRunAsShouldUseCurrentUser("true");
        verifyRunAsUser(getDevice().getCurrentUser());
    }

    private void verifyRunAsUser(int targetUserId) throws Exception {
        String runAsResult = getDevice().executeShellCommand("run-as android.cpptools.app id -u");
        assertNotNull("adb shell command failed", runAsResult);
        runAsResult = runAsResult.trim();
        Matcher appIdMatcher = Pattern.compile("^([0-9]+)$").matcher(runAsResult);
        assertWithMessage("Result from adb shell should be an app id: \"%s\"", runAsResult)
                .that(appIdMatcher.matches())
                .isTrue();
        String appUidString = appIdMatcher.group(1);
        int appUid = Integer.parseInt(appUidString);
        int appId = appUid % PER_USER_RANGE;
        int userId = appUid / PER_USER_RANGE;
        assertWithMessage("App UID should be greater than 10000: \"%s\"", runAsResult)
                .that(appId >= 10000)
                .isTrue();
        assertWithMessage("User ID extracted from app UID is different than expected")
                .that(userId)
                .isEqualTo(targetUserId);
    }

    private @Nullable String getRunAsShouldUseCurrentUser() throws Exception {
        return getDevice().getProperty("debug.run-as.use_current_user");
    }

    private void setRunAsShouldUseCurrentUser(@Nullable String useCurrentUser) throws Exception {
        if (useCurrentUser != null) {
            getDevice().setProperty("debug.run-as.use_current_user", useCurrentUser);
        } else {
            getDevice().executeShellCommand("setprop debug.run-as.use_current_user \"\"");
        }
    }
}
