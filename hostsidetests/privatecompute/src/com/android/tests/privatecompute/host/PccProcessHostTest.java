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

package com.android.tests.privatecompute.host;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.app.privatecompute.flags.Flags;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.host.HostFlagsValueProvider;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class PccProcessHostTest extends BaseHostJUnit4Test {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            HostFlagsValueProvider.createCheckFlagsRule(this::getDevice, this.getClass());

    private static final String PCC_TEST_APK = "PccHostTestApp.apk";
    private static final String APP_PACKAGE_NAME = "com.example.pcc.host.test";

    // Equivalent to UserHandle.PER_USER_RANGE
    private static final int PER_USER_RANGE = 100000;

    private static final int SLEEP_MS_DURATION = 500;

    private int mUserId = -1;

    @Before
    public void setUp() throws Exception {
        getDevice().enableAdbRoot();
        mUserId = getDevice().getCurrentUser();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
    public void testForceStop_killsPccProcess() throws Exception {
        // 1. Install App
        installPackageAsUser(PCC_TEST_APK, true, mUserId);

        // 2. Start PCC Service to spin up the PCC process
        String startCmd =
                String.format(
                        "am start-foreground-service --user %d %s/.services.PccForegroundService",
                        mUserId, APP_PACKAGE_NAME);
        getDevice().executeShellCommand(startCmd);

        // 3. Get the expected PCC UID
        int pccUid = getPccUidForUser(APP_PACKAGE_NAME, mUserId);
        assertThat(pccUid).isGreaterThan(0);

        // 4. Assert PCC process is running
        boolean isRunning = waitForProcessToReachState(pccUid, true /* expectRunning */);
        assertWithMessage("PCC Process (uid=" + pccUid + ") running state")
                .that(isRunning)
                .isTrue();

        // 5. Force Stop
        getDevice().executeShellCommand("am force-stop --user " + mUserId + " " + APP_PACKAGE_NAME);

        // 6. Assert PCC process is killed
        boolean isGone = waitForProcessToReachState(pccUid, false /* expectRunning */);
        assertWithMessage("PCC Process (uid=" + pccUid + ") killed state").that(isGone).isTrue();
    }

    // --- Helper Methods ---

    /**
     * Parses 'dumpsys package' to find the assigned pccId and calculates the full UID for the user.
     */
    private int getPccUidForUser(String packageName, int userId) throws Exception {
        String output = getDevice().executeShellCommand("dumpsys package " + packageName);
        // Look for "pccId=12345"
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("pccId=(\\d+)");
        java.util.regex.Matcher matcher = pattern.matcher(output);

        if (matcher.find()) {
            int pccAppId = Integer.parseInt(matcher.group(1));
            // Calculate full UID: (User * 100000) + AppId
            return (userId * PER_USER_RANGE) + pccAppId;
        }
        return -1;
    }

    /** Polls 'ps' to see if a process with the specific UID exists/does not exist. */
    private boolean waitForProcessToReachState(int uid, boolean expectRunning) throws Exception {
        // Poll for up to 5 seconds
        for (int i = 0; i < 10; i++) {
            boolean isCurrentlyRunning = isUidRunning(uid);
            if (isCurrentlyRunning == expectRunning) {
                return true;
            }
            Thread.sleep(SLEEP_MS_DURATION);
        }
        return false;
    }

    private boolean isUidRunning(int uid) throws Exception {
        // List processes with UID and grep for specific UID.
        // We use the numeric UID to avoid ambiguity with process names.
        String psOutput = getDevice().executeShellCommand("ps -A -o UID");
        String uidStr = String.valueOf(uid);

        // Check if the UID appears in the list (exact match lines)
        for (String line : psOutput.split("\n")) {
            if (line.trim().equals(uidStr)) {
                return true;
            }
        }
        return false;
    }
}
