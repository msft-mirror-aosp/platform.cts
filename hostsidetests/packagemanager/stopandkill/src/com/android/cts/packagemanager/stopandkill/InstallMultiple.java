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

package com.android.cts.packagemanager.stopandkill;

import static com.google.common.truth.Truth.assertThat;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import java.util.ArrayList;
import java.util.List;

/**
 * A host-side helper class to support multi-package installation via the pm command. This class
 * orchestrates the creation, writing, and committing of an install session.
 */
public class InstallMultiple {
    private final BaseHostJUnit4Test mTest;
    private final List<String> mArgs = new ArrayList<>();
    private final List<String> mApkFileNames = new ArrayList<>();
    private int mUserId = -1;

    public InstallMultiple(BaseHostJUnit4Test test) {
        mTest = test;
    }

    /**
     * Adds a command-line argument to the 'pm install-create' command. For example, "-r" for
     * reinstall.
     */
    public InstallMultiple addArg(String arg) {
        mArgs.add(arg);
        return this;
    }

    /**
     * Adds the file name of an APK to be included in the installation. The helper will find, push,
     * and clean up the file on the device.
     */
    public InstallMultiple addApk(String apkFileName) {
        mApkFileNames.add(apkFileName);
        return this;
    }

    /** Sets the user for this installation. */
    public InstallMultiple forUser(int userId) {
        mUserId = userId;
        return this;
    }

    /**
     * Executes the multi-package installation sequence.
     *
     * @return the time in milliseconds it took to commit the session.
     */
    public long run() throws Exception {
        final List<Integer> childSessionIds = new ArrayList<>();
        int parentSessionId = -1;
        try {
            // Create a parent session for the multi-package install.
            parentSessionId = createSession(true);

            // Create a child session for each APK.
            for (String apkFileName : mApkFileNames) {
                final String onDevicePath = "/data/local/tmp/stopandkill/" + apkFileName;
                final int childSessionId = createSession(false);
                childSessionIds.add(childSessionId);
                writeSession(childSessionId, onDevicePath);
            }

            // Add the child sessions to the parent.
            addSessionsToParent(parentSessionId, childSessionIds);

            // Commit the parent session to install all packages atomically.
            final long startTime = System.currentTimeMillis();
            commitSession(parentSessionId);
            return System.currentTimeMillis() - startTime;
        } finally {
            // Clean up the sessions.
            if (parentSessionId != -1) {
                abandonSession(parentSessionId);
            }
            for (int childSessionId : childSessionIds) {
                abandonSession(childSessionId);
            }
        }
    }

    private int createSession(boolean isMultiPackage) throws DeviceNotAvailableException {
        final StringBuilder cmd = new StringBuilder("pm install-create");
        if (mUserId != -1) {
            cmd.append(" --user ").append(mUserId);
        }
        if (isMultiPackage) {
            cmd.append(" --multi-package");
        }
        for (String arg : mArgs) {
            cmd.append(' ').append(arg);
        }
        final String result = mTest.getDevice().executeShellCommand(cmd.toString());
        assertThat(result).startsWith("Success");

        final int start = result.lastIndexOf("[");
        final int end = result.lastIndexOf("]");
        int sessionId = -1;
        try {
            if (start != -1 && end != -1 && start < end) {
                sessionId = Integer.parseInt(result.substring(start + 1, end));
            }
        } catch (NumberFormatException e) {
            // Fall through
        }
        if (sessionId == -1) {
            throw new IllegalStateException("Failed to create install session: " + result);
        }
        return sessionId;
    }

    private void writeSession(int sessionId, String onDevicePath)
            throws DeviceNotAvailableException {
        final String apkName = onDevicePath.substring(onDevicePath.lastIndexOf('/') + 1);
        final StringBuilder cmd = new StringBuilder("pm install-write");
        cmd.append(' ').append(sessionId);
        cmd.append(' ').append(apkName);
        cmd.append(' ').append(onDevicePath);

        final String result = mTest.getDevice().executeShellCommand(cmd.toString());
        assertThat(result).startsWith("Success");
    }

    private void addSessionsToParent(int parentSessionId, List<Integer> childSessionIds)
            throws DeviceNotAvailableException {
        final StringBuilder cmd = new StringBuilder("pm install-add-session");
        cmd.append(' ').append(parentSessionId);
        for (int childSessionId : childSessionIds) {
            cmd.append(' ').append(childSessionId);
        }
        final String result = mTest.getDevice().executeShellCommand(cmd.toString());
        assertThat(result).startsWith("Success");
    }

    private void commitSession(int sessionId) throws DeviceNotAvailableException {
        final StringBuilder cmd = new StringBuilder("pm install-commit");
        cmd.append(' ').append(sessionId);

        final String result = mTest.getDevice().executeShellCommand(cmd.toString());
        assertThat(result).startsWith("Success");
    }

    private void abandonSession(int sessionId) throws DeviceNotAvailableException {
        final StringBuilder cmd = new StringBuilder("pm install-abandon");
        cmd.append(' ').append(sessionId);
        mTest.getDevice().executeShellCommand(cmd.toString());
    }
}
