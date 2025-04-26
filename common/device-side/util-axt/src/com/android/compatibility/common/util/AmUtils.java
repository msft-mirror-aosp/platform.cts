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

public class AmUtils {
    private static final String TAG = "CtsAmUtils";

    private static final String DUMPSYS_ACTIVITY_PROCESSES = "dumpsys activity --proto processes";

    public static int STANDBY_BUCKET_DOES_NOT_EXIST = -1;

    private AmUtils() {
    }

    /** Run "adb shell am make-uid-idle PACKAGE" */
    public static void runMakeUidIdle(String packageName) {
        SystemUtil.runShellCommandForNoOutput("am make-uid-idle " + packageName);
    }

    /** Run "adb shell am kill PACKAGE" */
    public static void runKill(String packageName) throws Exception {
        runKill(packageName, false /* wait */);
    }

    public static void runKill(String packageName, boolean wait) throws Exception {
        SystemUtil.runShellCommandForNoOutput(
                "am kill --user " + Process.myUserHandle().getIdentifier() + " " + packageName);

        if (!wait) {
            return;
        }

        TestUtils.waitUntil("package process was not killed:" + packageName,
                () -> !isProcessRunning(packageName));
    }

    private static boolean isProcessRunning(String packageName) {
        final String output = SystemUtil.runShellCommand("ps -A -o NAME");
        String[] packages = output.split("\\n");
        for (int i = packages.length -1; i >=0; --i) {
            if (packages[i].equals(packageName)) {
                return true;
            }
        }
        return false;
    }

    /** Run "adb shell am set-standby-bucket" */
    public static void setStandbyBucket(String packageName, int value) {
        SystemUtil.runShellCommandForNoOutput("am set-standby-bucket " + packageName
                + " " + value);
    }

    /** Run "adb shell am set-standby-bucket --user" */
    public static void setStandbyBucketAsUser(String packageName, int value, int userId) {
        SystemUtil.runShellCommandForNoOutput(
                "am set-standby-bucket --user " + userId + " " + packageName + " " + value);
    }

    /**
     * Run "adb shell am get-standby-bucket",
     * return #STANDBY_BUCKET_DOES_NOT_EXIST for invalid packages
     * */
    public static int getStandbyBucket(String packageName) {
        final String value = SystemUtil.runShellCommand("am get-standby-bucket " + packageName);
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException nfe) {
            return STANDBY_BUCKET_DOES_NOT_EXIST;
        }
    }

    /**
     * Run "adb shell am get-standby-bucket --user",
     * return #STANDBY_BUCKET_DOES_NOT_EXIST for invalid packages
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
