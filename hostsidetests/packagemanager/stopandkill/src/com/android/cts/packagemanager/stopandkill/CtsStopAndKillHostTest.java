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
import static com.google.common.truth.Truth.assertWithMessage;

import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.host.HostFlagsValueProvider;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.window.flags.Flags;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Host-side test for the PackageManager's wait-for-kill-on-package-update feature.
 *
 * <p>Verifies that when the 'wait_for_kill_on_package_update' flag is enabled, package updates wait
 * for the app to save its state.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class CtsStopAndKillHostTest extends BaseHostJUnit4Test {

    private static final String PERSISTABLE_ACTIVITY = "PersistableActivity";
    private static final String NON_PERSISTABLE_ACTIVITY = "NonPersistableActivity";
    private static final String PERSISTABLE_TIMEOUT_ACTIVITY = "PersistableTimeoutActivity";

    private static final int FLAG_ACTIVITY_NEW_TASK = 0x10000000;
    private static final int FLAG_ACTIVITY_MULTIPLE_TASK = 0x08000000;

    private static final int WINDOWING_MODE_FREEFORM = 5;

    private static final boolean DEBUG = true;

    private TestApp mApp1 =
            new TestApp("com.android.cts.stopandkillapp1", "CtsStopAndKillTestApp1.apk");
    private TestApp mApp2 =
            new TestApp("com.android.cts.stopandkillapp2", "CtsStopAndKillTestApp2.apk");
    private TestApp mApp3 =
            new TestApp("com.android.cts.stopandkillapp3", "CtsStopAndKillTestApp3.apk");

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            HostFlagsValueProvider.createCheckFlagsRule(this::getDevice);

    @Before
    public void setUp() throws Exception {
        cleanUp();
    }

    @After
    public void tearDown() throws Exception {
        cleanUp();
    }

    private void cleanUp() throws Exception {
        mApp1.uninstall();
        mApp2.uninstall();
        mApp3.uninstall();
        mApp1.deleteStateFile();
        mApp2.deleteStateFile();
        mApp3.deleteStateFile();
    }

    /**
     * Verifies that when the 'wait_for_kill_on_package_update' flag is disabled, the app's instance
     * state is NOT saved during an update.
     */
    @Test
    @RequiresFlagsDisabled(Flags.FLAG_ENABLE_APP_RESTART_AFTER_UPDATE)
    public void testUpdate_flagDisabled_doesNotStopAppOnPackageUpdate() throws Exception {
        mApp1.installPackage();
        launchActivityAndAssertResumed(mApp1.persistableActivity);

        // Activity shouldn't have been stopped before we update
        mApp1.assertStateFileCreatedOnStop(/* shouldExist= */ false);

        mApp1.installPackage("-r");

        // Assert that the state file does NOT exist after update when flag is disabled.
        mApp1.assertStateFileCreatedOnStop(/* shouldExist= */ false);
    }

    /**
     * Verifies that when the 'wait_for_kill_on_package_update' flag is enabled, the app's instance
     * state is saved during an update and restored in the new version.
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_APP_RESTART_AFTER_UPDATE)
    public void testUpdate_flagEnabled_stopsAppOnPackageUpdate() throws Exception {
        mApp1.installPackage();
        launchActivityAndAssertResumed(mApp1.persistableActivity);

        // Activity shouldn't have been stopped before we update
        mApp1.assertStateFileCreatedOnStop(/* shouldExist= */ false);

        mApp1.installPackage("-r");

        // Assert that the app was stopped since the activity was persistable
        mApp1.assertStateFileCreatedOnStop(/* shouldExist= */ true);
    }

    /**
     * Verifies that when the 'wait_for_kill_on_package_update' flag is enabled, but the activity is
     * not persistable, the app is NOT stopped.
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_APP_RESTART_AFTER_UPDATE)
    public void testUpdate_nonPersistableActivity_doesNotStopApp() throws Exception {
        mApp1.installPackage();
        launchActivityAndAssertResumed(mApp1.nonPersistableActivity);

        // Activity shouldn't have been stopped before we update
        mApp1.assertStateFileCreatedOnStop(/* shouldExist= */ false);

        mApp1.installPackage("-r");

        // Assert that the app wasn't stopped since the activity is not persistable.
        mApp1.assertStateFileCreatedOnStop(/* shouldExist= */ false);
    }

    /**
     * Verifies that the package update is still successful even if the app takes too long to save
     * its state.
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_APP_RESTART_AFTER_UPDATE)
    public void testUpdate_timeout_updateSuccess() throws Exception {
        mApp1.installPackage();
        launchActivityAndAssertResumed(mApp1.persistableTimeoutActivity);

        // Activity shouldn't have been stopped before we update
        mApp1.assertStateFileCreatedOnStop(/* shouldExist= */ false);

        final long startTime = System.currentTimeMillis();
        mApp1.installPackage("-r");
        final long installTime = System.currentTimeMillis() - startTime;

        // Assert that the app was not stopped because it timed out.
        mApp1.assertStateFileCreatedOnStop(/* shouldExist= */ false);

        // Assert that the installation did not wait for the full timeout of the app: 30s
        assertThat(installTime).isLessThan(25 * 1000);
    }

    /** Verifies that a multi-package install stops both apps to save their state. */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_APP_RESTART_AFTER_UPDATE)
    public void testUpdate_multiplePackages_stopsBothApps() throws Exception {
        mApp1.installPackage();
        mApp2.installPackage();

        final String setting = "enable_freeform_support";
        String originalSetting = getDevice().executeShellCommand("settings get global " + setting);
        try {
            getDevice().executeShellCommand("settings put global " + setting + " 1");
            // Launch both activities in split screen to ensure they are resumed.
            launchActivityInFreeForm(mApp1.persistableActivity, mApp2.persistableActivity);

            // Neither app should have been stopped yet.
            mApp1.assertStateFileCreatedOnStop(/* shouldExist= */ false);
            mApp2.assertStateFileCreatedOnStop(/* shouldExist= */ false);

            // Perform the multi-package update and time it.
            InstallMultiple installer = new InstallMultiple(this);
            installer.addArg("-r");
            installer.addApk(mApp1.apk);
            installer.addApk(mApp2.apk);
            installer.run();

            // Assert that both apps were stopped to save state.
            mApp1.assertStateFileCreatedOnStop(/* shouldExist= */ true);
            mApp2.assertStateFileCreatedOnStop(/* shouldExist= */ true);
        } finally {
            getDevice()
                    .executeShellCommand("settings put global " + setting + " " + originalSetting);
        }
    }

    /** Verifies that when we install multiple packages, we wait in parallel */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_APP_RESTART_AFTER_UPDATE)
    public void testUpdate_multiplePackages_waitsInParallel() throws Exception {
        mApp1.installPackage();
        mApp2.installPackage();
        mApp3.installPackage();

        final String setting = "enable_freeform_support";
        String originalSetting = getDevice().executeShellCommand("settings get global " + setting);
        try {
            getDevice().executeShellCommand("settings put global " + setting + " 1");
            // Launch both activities in split screen to ensure they are resumed.
            launchActivityInFreeForm(
                    mApp1.persistableTimeoutActivity,
                    mApp2.persistableTimeoutActivity,
                    mApp3.persistableTimeoutActivity);

            // Neither app should have been stopped yet.
            mApp1.assertStateFileCreatedOnStop(/* shouldExist= */ false);
            mApp2.assertStateFileCreatedOnStop(/* shouldExist= */ false);
            mApp3.assertStateFileCreatedOnStop(/* shouldExist= */ false);

            // Perform the multi-package update and time it.
            InstallMultiple installer = new InstallMultiple(this);
            installer.addArg("-r");
            installer.addApk(mApp1.apk);
            installer.addApk(mApp2.apk);
            installer.addApk(mApp3.apk);
            long installTime = installer.run();

            // Assert that both apps were killed before they were able to write file
            mApp1.assertStateFileCreatedOnStop(/* shouldExist= */ false);
            mApp2.assertStateFileCreatedOnStop(/* shouldExist= */ false);
            mApp3.assertStateFileCreatedOnStop(/* shouldExist= */ false);

            // Assert that the installation was reasonably fast. All apps timeout in 11 seconds
            // each, but the install should complete faster than 33 seconds since they should be
            // waiting in parallel.
            assertThat(installTime).isLessThan(25 * 1000);
        } finally {
            getDevice()
                    .executeShellCommand("settings put global " + setting + " " + originalSetting);
        }
    }

    private void launchActivityAndAssertResumed(String componentName) throws Exception {
        getDevice()
                .executeShellCommand(
                        "am start -W -n " + componentName + " -f " + FLAG_ACTIVITY_NEW_TASK);

        // Verify that the activity is in the resumed state.
        String result =
                getDevice()
                        .executeShellCommand(
                                "dumpsys activity activities | grep -E ' ResumedActivity.*"
                                        + componentName
                                        + "'");
        if (DEBUG && !result.contains(componentName)) {
            String fullDumpsys = getDevice().executeShellCommand("dumpsys activity activities");
            assertWithMessage(
                            "Activity "
                                    + componentName
                                    + " was not resumed. Full dumpsys:\n"
                                    + fullDumpsys)
                    .that(result)
                    .contains(componentName);
        } else {
            assertThat(result).contains(componentName);
        }
    }

    private void launchActivityInFreeForm(String... componentNames) throws Exception {
        final int flags = FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_MULTIPLE_TASK;

        for (String component : componentNames) {
            String command =
                    "am start -W -n "
                            + component
                            + " -f "
                            + flags
                            + " --windowingMode "
                            + WINDOWING_MODE_FREEFORM;
            getDevice().executeShellCommand(command);
        }
    }

    /**
     * Asserts whether the state file was created, indicating the app's onSaveInstanceState was
     * called.
     *
     * <p>The app is programmed to create these files when onSaveInstanceState method is called.
     */
    public final class TestApp {
        public final String pkg;
        public final String apk;
        public final String stateFilePath;
        public final String persistableActivity;
        public final String nonPersistableActivity;
        public final String persistableTimeoutActivity;

        TestApp(String pkg, String apk) {
            this.pkg = pkg;
            this.apk = apk;
            this.stateFilePath = "/sdcard/Android/data/" + pkg + "/files/state.txt";
            this.persistableActivity = getComponentName(pkg, PERSISTABLE_ACTIVITY);
            this.nonPersistableActivity = getComponentName(pkg, NON_PERSISTABLE_ACTIVITY);
            this.persistableTimeoutActivity = getComponentName(pkg, PERSISTABLE_TIMEOUT_ACTIVITY);
        }

        private String getComponentName(String pkgName, String activityName) {
            // Both apps use the same source code, so the activity class is always in the old
            // package name.
            return pkgName + "/" + "com.android.cts.stopandkillapp" + "." + activityName;
        }

        void installPackage(String... options) throws Exception {
            CtsStopAndKillHostTest.this.installPackage(apk, options);
        }

        void uninstall() throws Exception {
            getDevice().executeShellCommand("pm uninstall " + pkg);
        }

        void deleteStateFile() throws Exception {
            getDevice().executeShellCommand("rm -f " + stateFilePath);
        }

        /**
         * Asserts whether the state file was created, indicating the app's onSaveInstanceState was
         * called.
         *
         * <p>The app is programmed to create these files when onSaveInstanceState method is called.
         */
        void assertStateFileCreatedOnStop(boolean shouldExist) throws Exception {
            assertWithMessage(
                            "Expected file "
                                    + stateFilePath
                                    + " to "
                                    + (shouldExist ? "exist" : "not exist"))
                    .that(getDevice().doesFileExist(stateFilePath))
                    .isEqualTo(shouldExist);
        }
    }
}
