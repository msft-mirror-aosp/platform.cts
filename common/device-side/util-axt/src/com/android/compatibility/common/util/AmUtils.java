/*
 * Copyright (C) 2018 The Android Open Source Project
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

import android.os.Process;
import android.util.Log;

/** A helper class for calling {@code am} commands. */
public final class AmUtils {
    private static final String TAG = "CtsAmUtils";

    /** An invalid standby bucket value. */
    public static final int STANDBY_BUCKET_DOES_NOT_EXIST = -1;

    private AmUtils() {
    }

    /** Run "adb shell am make-uid-idle PACKAGE" */
    public static void runMakeUidIdle(String packageName) {
        SystemUtil.runShellCommandForNoOutput("am make-uid-idle " + packageName);
    }

    /**
     * Kills the specified package by sending a SIGKILL to its PID.
     *
     * @param packageName The package to kill.
     * @param wait whether to wait until the package's processes have been killed.
     * @throws Exception if the process is not killed in time.
     */
    public static void runKill(String packageName, boolean wait) throws Exception {
        // Step 1: Find the PID of the running process for the given package.
        // The 'pidof -s' command returns a single PID.
        final String pidString = SystemUtil.runShellCommand("pidof -s " + packageName).trim();

        // If pidString is empty, log a warning and return
        if (pidString.isEmpty()) {
            Log.w(TAG, "No PID found for " + packageName + ", skipping force kill.");
            return;
        }

        // Step 2: Send the SIGKILL signal directly to the PID.
        SystemUtil.runShellCommandForNoOutput("su 0 kill -9 " + pidString);

        if (!wait) {
            return;
        }

        TestUtils.waitUntil("package process was not killed:" + packageName,
                () -> !isProcessRunning(packageName));
    }

    /**
     * Runs "adb shell am stop-app PACKAGE".
     *
     * @param packageName The package to stop.
     */
    public static void runStopApp(String packageName) {
        SystemUtil.runShellCommandForNoOutput("am stop-app " + packageName);
    }

    /**
     * Checks if a process is running for the given package.
     *
     * @param packageName The package to check.
     * @return {@code true} if a process is running for the package, {@code false} otherwise.
     */
    public static boolean isProcessRunning(String packageName) {
        final String output = SystemUtil.runShellCommand("ps -A -o NAME");
        String[] packages = output.split("\\n");
        for (int i = packages.length - 1; i >= 0; --i) {
            if (packages[i].equals(packageName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Runs "adb shell am set-standby-bucket".
     *
     * @param packageName The package to set the standby bucket for.
     * @param value The standby bucket value.
     */
    public static void setStandbyBucket(String packageName, int value) {
        SystemUtil.runShellCommandForNoOutput("am set-standby-bucket " + packageName
                + " " + value);
    }

    /**
     * Runs "adb shell am set-standby-bucket --user".
     *
     * @param packageName The package to set the standby bucket for.
     * @param value The standby bucket value.
     * @param userId The user to set the standby bucket for.
     */
    public static void setStandbyBucketAsUser(String packageName, int value, int userId) {
        SystemUtil.runShellCommandForNoOutput(
                "am set-standby-bucket --user " + userId + " " + packageName + " " + value);
    }

    /**
     * Runs "adb shell am get-standby-bucket".
     *
     * @param packageName The package to get the standby bucket for.
     * @return The standby bucket value, or {@link #STANDBY_BUCKET_DOES_NOT_EXIST} for invalid
     *     packages.
     */
    public static int getStandbyBucket(String packageName) {
        final String value = SystemUtil.runShellCommand("am get-standby-bucket " + packageName);
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException nfe) {
            return STANDBY_BUCKET_DOES_NOT_EXIST;
        }
    }

    /**
     * Runs "adb shell am get-standby-bucket --user".
     *
     * @param packageName The package to get the standby bucket for.
     * @param userId The user to get the standby bucket for.
     * @return The standby bucket value, or {@link #STANDBY_BUCKET_DOES_NOT_EXIST} for invalid
     *     packages.
     */
    public static int getStandbyBucketAsUser(String packageName, int userId) {
        final String value = SystemUtil.runShellCommand(
                "am get-standby-bucket --user " + userId + " " + packageName);
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException nfe) {
            return STANDBY_BUCKET_DOES_NOT_EXIST;
        }
    }

    /**
     * Run "adb shell am stop-app" for the given package and the current user. This should force
     * kill the package even if it has active components.
     *
     * @param packageName The package to kill
     * @param wait whether to wait until the package process has gone.
     */
    public static void runStopApp(String packageName, boolean wait) throws Exception {
        runStopApp(packageName, wait, Process.myUserHandle().getIdentifier());
    }

    /**
     * Run "adb shell am stop-app --user" for the given package and user. This should force kill the
     * package even if it has active components.
     *
     * @param packageName The package to kill
     * @param wait whether to wait until the package process has gone.
     * @param userId the userId on which to kill the package.
     */
    public static void runStopApp(String packageName, boolean wait, int userId) throws Exception {
        // Explicit userId is required because it defaults to the system (not current) user.
        SystemUtil.runShellCommandForNoOutput("am stop-app --user " + userId + " " + packageName);
        if (wait) {
            TestUtils.waitUntil(
                    "package process was not killed:" + packageName,
                    () -> !isProcessRunning(packageName));
        }
    }

    /** Wait until all broad queues are idle. */
    public static void waitForBroadcastIdle() {
        SystemUtil.runCommandAndPrintOnLogcat(TAG, "am wait-for-broadcast-idle");
    }

    /** Wait until all broad queues have passed barrier. */
    public static void waitForBroadcastBarrier() {
        SystemUtil.runCommandAndPrintOnLogcat(TAG, "am wait-for-broadcast-barrier");
    }
}
