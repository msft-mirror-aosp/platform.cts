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

package android.os.cts;

import android.platform.test.annotations.AppModeFull;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.CollectingOutputReceiver;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IAbi;
import com.android.tradefed.testtype.IAbiReceiver;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.util.RunUtil;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class OsHostTests extends DeviceTestCase implements IBuildReceiver, IAbiReceiver {
    private static final String TEST_APP_PACKAGE = "android.os.app";

    private static final String TEST_NON_EXPORTED_ACTIVITY_CLASS = "TestNonExported";
    private static final String START_NON_EXPORTED_ACTIVITY_COMMAND = String.format(
            "am start -n %s/%s.%s",
            TEST_APP_PACKAGE, TEST_APP_PACKAGE, TEST_NON_EXPORTED_ACTIVITY_CLASS);

    private static final String TEST_FG_SERVICE_CLASS = "TestFgService";
    private static final String START_FG_SERVICE_COMMAND = String.format(
            "am start-foreground-service -n %s/%s.%s",
            TEST_APP_PACKAGE, TEST_APP_PACKAGE, TEST_FG_SERVICE_CLASS);
    private static final String FILTER_FG_SERVICE_REGEXP =
            "TestFgService starting foreground: pid=([0-9]*)";

    private static final int MAX_LOGCAT_RETRIES = 10;
    private static final long LOGCAT_RETRY_DELAY_MS = 1000;

    /**
     * A reference to the device under test.
     */
    private ITestDevice mDevice;
    private IAbi mAbi;
    private IBuildInfo mCtsBuild;

    @Override
    public void setAbi(IAbi abi) {
        mAbi = abi;
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mCtsBuild = buildInfo;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        // Get the device, this gives a handle to run commands and install APKs.
        mDevice = getDevice();
    }

    /**
     * Test whether non-exported activities are properly not launchable.
     */
    @AppModeFull(reason = "Error message is different for instant app (Activity does not exist)")
    public void testNonExportedActivities() throws Exception {
        // Run as unroot
        boolean wasRoot = mDevice.isAdbRoot();
        try {
            mDevice.disableAdbRoot();
            // Attempt to launch the non-exported activity in the test app
            CollectingOutputReceiver outputReceiver = new CollectingOutputReceiver();
            mDevice.executeShellCommand(START_NON_EXPORTED_ACTIVITY_COMMAND, outputReceiver);
            final String output = outputReceiver.getOutput();

            assertTrue(output.contains("Permission Denial") && output.contains(" not exported"));
        } finally {
            // Restore back to original root state
            if (wasRoot) {
                mDevice.enableAdbRoot();
            }
        }
    }

    /**
     * Test behavior of malformed Notifications w.r.t. foreground services
     */
    @AppModeFull(reason = "Instant apps may not start foreground services")
    public void testForegroundServiceBadNotification() throws Exception {
        mDevice.clearLogcat();
        mDevice.executeShellCommand(START_FG_SERVICE_COMMAND);

        String pid = findTestServicePidInLogcat(MAX_LOGCAT_RETRIES, LOGCAT_RETRY_DELAY_MS);
        assertNotNull("Didn't find test service statement in logcat", pid);

        final String procPath = "/proc/" + pid;
        final String lsOutput = mDevice.executeShellCommand("ls -d " + procPath).trim();
        // More robust check for non-existence, accounts for different error messages
        assertTrue(
                "Service process " + pid + " should not exist. ls output: " + lsOutput,
                lsOutput.contains("No such file or directory") || lsOutput.isEmpty());
    }

    /**
     * Polls Logcat for a line matching the regex for starting foreground test service and extracts
     * its pid.
     *
     * @param maxRetries The maximum number of times to check Logcat.
     * @param retryDelayMs The delay in milliseconds between retries.
     * @return The pid of the process starting foreground test service, or null if not found after
     *     retries.
     * @throws Exception If an error occurs during Logcat access or command execution.
     */
    private String findTestServicePidInLogcat(int maxRetries, long retryDelayMs) throws Exception {
        final Pattern pattern = Pattern.compile(FILTER_FG_SERVICE_REGEXP);
        String pid = null;
        int retries = 0;

        while (retries < maxRetries) {
            RunUtil.getDefault().sleep(retryDelayMs);
            retries++;

            try (InputStreamSource logSource = mDevice.getLogcat();
                    // Keep the stream open for the reader
                    InputStream inputStream = logSource.createInputStream();
                    InputStreamReader streamReader = new InputStreamReader(inputStream);
                    BufferedReader logReader = new BufferedReader(streamReader)) {

                String line;
                while ((line = logReader.readLine()) != null) {
                    Matcher matcher = pattern.matcher(line);
                    if (matcher.find()) {
                        pid = matcher.group(1);
                        break; // Found PID, exit inner loop
                    }
                }
            } // try-with-resources will close resources here
            if (pid != null) {
                break; // Found PID, exit outer loop
            }
        }
        return pid;
    }
}
