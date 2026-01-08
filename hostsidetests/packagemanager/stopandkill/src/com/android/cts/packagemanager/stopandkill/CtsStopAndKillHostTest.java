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

import static org.junit.Assert.fail;

import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.ReportUtils;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.host.HostFlagsValueProvider;

import com.android.os.AtomsProto;
import com.android.os.StatsLog;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.util.RunUtil;
import com.android.window.flags.Flags;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private static final String ALIAS_ACTIVITY = "AliasActivity";

    private static final int FLAG_ACTIVITY_NEW_TASK = 0x10000000;
    private static final int FLAG_ACTIVITY_MULTIPLE_TASK = 0x08000000;

    private static final int WINDOWING_MODE_FREEFORM = 5;

    private static final int STEP_FREEZE_INSTALL_STOP_AND_KILL = 9;

    private static final boolean DEBUG = true;

    private int mUserId;
    private TestApp mApp1 =
            new TestApp("com.android.cts.stopandkillapp1", "CtsStopAndKillTestApp1.apk");
    private TestApp mApp2 =
            new TestApp("com.android.cts.stopandkillapp2", "CtsStopAndKillTestApp2.apk");
    private TestApp mApp3 =
            new TestApp("com.android.cts.stopandkillapp3", "CtsStopAndKillTestApp3.apk");

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            HostFlagsValueProvider.createCheckFlagsRule(this::getDevice, this.getClass());

    @Before
    public void setUp() throws Exception {
        mUserId = getDevice().getCurrentUser();
        cleanUp();
    }

    @After
    public void tearDown() throws Exception {
        cleanUp();
    }

    private void cleanUp() throws Exception {
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());

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

        long startTime = System.currentTimeMillis();
        mApp1.installPackage("-r");
        long installTime = System.currentTimeMillis() - startTime;

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

        String setting = "enable_freeform_support";
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
            installer.forUser(mUserId).run();

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

        String setting = "enable_freeform_support";
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
            long installTime = installer.forUser(mUserId).run();

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

    /**
     * Verifies that when the 'wait_for_kill_on_package_update' flag is enabled, the app's instance
     * state is saved during an update and restored in the new version.
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_APP_RESTART_AFTER_UPDATE)
    public void testUpdate_flagEnabled_emitsStopAndKillMetric() throws Exception {
        ConfigUtils.uploadConfigForPushedAtom(
                getDevice(),
                mApp1.pkg,
                AtomsProto.Atom.PACKAGE_INSTALLATION_SESSION_REPORTED_FIELD_NUMBER);
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);

        mApp1.installPackage();

        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);

        List<AtomsProto.PackageInstallationSessionReported> reports = new ArrayList<>();
        for (StatsLog.EventMetricData data : ReportUtils.getEventMetricDataList(getDevice())) {
            if (data.getAtom().hasPackageInstallationSessionReported()) {
                reports.add(data.getAtom().getPackageInstallationSessionReported());
            }
        }

        assertThat(reports.size()).isEqualTo(1);
        AtomsProto.PackageInstallationSessionReported report = reports.get(0);

        // Find a report that contains the STEP_FREEZE_INSTALL_STOP_AND_KILL
        for (int i = 0; i < report.getInstallStepsCount(); i++) {
            if (report.getInstallSteps(i) == STEP_FREEZE_INSTALL_STOP_AND_KILL) {
                assertThat(report.getStepDurationMillis(i)).isAtLeast(0);
                // INSTALL_FROM_ADB is true
                assertThat(report.getInstallFlags() & 0x00000020).isNotEqualTo(0);
                // When installed from adb, package name is empty
                assertThat(report.getPackageName()).isEmpty();
                assertThat(report.getUid()).isEqualTo(getAppUid(mApp1.pkg));
                return;
            }
        }
        fail("Didn't find report with STOP_AND_KILL step");
    }

    /**
     * Verifies that when the 'wait_for_kill_on_package_update' flag is enabled, the app's instance
     * state is saved during an update and restored in the new version.
     */
    @Test
    @RequiresFlagsDisabled(Flags.FLAG_ENABLE_APP_RESTART_AFTER_UPDATE)
    public void testUpdate_flagDisabled_doesNotEmitStopAndKillMetric() throws Exception {
        ConfigUtils.uploadConfigForPushedAtom(
                getDevice(),
                mApp1.pkg,
                AtomsProto.Atom.PACKAGE_INSTALLATION_SESSION_REPORTED_FIELD_NUMBER);
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);

        mApp1.installPackage();

        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);

        List<AtomsProto.PackageInstallationSessionReported> reports = new ArrayList<>();
        for (StatsLog.EventMetricData data : ReportUtils.getEventMetricDataList(getDevice())) {
            if (data.getAtom().hasPackageInstallationSessionReported()) {
                reports.add(data.getAtom().getPackageInstallationSessionReported());
            }
        }

        assertThat(reports.size()).isEqualTo(1);
        AtomsProto.PackageInstallationSessionReported report = reports.get(0);

        // Find a report that contains the STEP_FREEZE_INSTALL_STOP_AND_KILL
        for (int i = 0; i < report.getInstallStepsCount(); i++) {
            if (report.getInstallSteps(i) == STEP_FREEZE_INSTALL_STOP_AND_KILL) {
                fail("Emitted STOP_AND_KILL step when flag disabled");
            }
        }
    }

    /**
     * Verifies that when launching an activity alias of a persistable activity, the app's instance
     * state is saved during an update and restored in the new version.
     */
    @Test
    @RequiresFlagsEnabled({
        Flags.FLAG_ENABLE_APP_RESTART_AFTER_UPDATE,
        com.android.internal.pm.pkg.component.flags.Flags
                .FLAG_ENABLE_ACTIVITY_ALIAS_PERSISTABLE_MODE_BUGFIX
    })
    public void testUpdate_activityAlias_stopsAppOnPackageUpdate() throws Exception {
        mApp1.installPackage();
        launchActivityAndAssertResumed(mApp1.aliasActivity);

        // Activity shouldn't have been stopped before we update
        mApp1.assertStateFileCreatedOnStop(/* shouldExist= */ false);

        mApp1.installPackage("-r");

        // Assert that the app was stopped since the activity was persistable
        mApp1.assertStateFileCreatedOnStop(/* shouldExist= */ true);
    }

    /**
     * Verifies that when the flag is disabled, the app is not stopped on update even when a
     * persistable activity alias is running.
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_APP_RESTART_AFTER_UPDATE)
    @RequiresFlagsDisabled(
            com.android.internal.pm.pkg.component.flags.Flags
                    .FLAG_ENABLE_ACTIVITY_ALIAS_PERSISTABLE_MODE_BUGFIX)
    public void testUpdate_activityAlias_flagDisabled_doesNotStopApp() throws Exception {
        mApp1.installPackage();
        launchActivityAndAssertResumed(mApp1.aliasActivity);

        // Activity shouldn't have been stopped before we update
        mApp1.assertStateFileCreatedOnStop(/* shouldExist= */ false);

        mApp1.installPackage("-r");

        // Assert that the app was NOT stopped since the flag is disabled.
        mApp1.assertStateFileCreatedOnStop(/* shouldExist= */ false);
    }

    private void launchActivityAndAssertResumed(String componentName) throws Exception {
        getDevice()
                .executeShellCommand(
                        "am start --user "
                                + mUserId
                                + " -W -n "
                                + componentName
                                + " -f "
                                + FLAG_ACTIVITY_NEW_TASK);

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
        int flags = FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_MULTIPLE_TASK;

        for (String component : componentNames) {
            String command =
                    "am start --user "
                            + mUserId
                            + " -W -n "
                            + component
                            + " -f "
                            + flags
                            + " --windowingMode "
                            + WINDOWING_MODE_FREEFORM;
            getDevice().executeShellCommand(command);
        }
    }

    private int getAppUid(String pkgName) throws Exception {
        String uidLine =
                getDevice()
                        .executeShellCommand(
                                "cmd package list packages --match-libraries -U --user "
                                        + mUserId
                                        + " "
                                        + pkgName);
        Pattern pattern = Pattern.compile("package:" + pkgName + " uid:(\\d+)");
        Matcher matcher = pattern.matcher(uidLine);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        throw new IllegalStateException(
                "Package " + pkgName + " is not installed for user " + mUserId);
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
        public final String persistableActivity;
        public final String nonPersistableActivity;
        public final String persistableTimeoutActivity;
        public final String aliasActivity;

        TestApp(String pkg, String apk) {
            this.pkg = pkg;
            this.apk = apk;
            this.persistableActivity = getComponentName(pkg, PERSISTABLE_ACTIVITY);
            this.nonPersistableActivity = getComponentName(pkg, NON_PERSISTABLE_ACTIVITY);
            this.persistableTimeoutActivity = getComponentName(pkg, PERSISTABLE_TIMEOUT_ACTIVITY);
            this.aliasActivity = getComponentName(pkg, ALIAS_ACTIVITY);
        }

        private String getStoragePath() {
            return String.format("/storage/emulated/%d/Documents/%s-state.txt", mUserId, pkg);
        }

        private String getComponentName(String pkgName, String activityName) {
            // Both apps use the same source code, so the activity class is always in the old
            // package name.
            return pkgName + "/" + "com.android.cts.stopandkillapp" + "." + activityName;
        }

        void installPackage(String... options) throws Exception {
            final String[] userOptions = new String[options.length + 3];
            userOptions[0] = "--user";
            userOptions[1] = String.valueOf(mUserId);
            userOptions[2] = "-g";
            System.arraycopy(options, 0, userOptions, 3, options.length);
            CtsStopAndKillHostTest.this.installPackage(apk, userOptions);
            getDevice()
                    .executeShellCommand(
                            "appops set --user "
                                    + mUserId
                                    + " "
                                    + pkg
                                    + " MANAGE_EXTERNAL_STORAGE allow");
        }

        void uninstall() throws Exception {
            getDevice().executeShellCommand("pm uninstall --user " + mUserId + " " + pkg);
        }

        void deleteStateFile() throws Exception {
            getDevice().deleteFile(getStoragePath(), mUserId);
        }

        /**
         * Asserts whether the state file was created, indicating the app's onSaveInstanceState was
         * called.
         *
         * <p>The app is programmed to create these files when onSaveInstanceState method is called.
         */
        void assertStateFileCreatedOnStop(boolean shouldExist) throws Exception {
            boolean fileExists = getDevice().doesFileExist(getStoragePath(), mUserId);

            assertWithMessage(
                            "Expected state file "
                                    + getStoragePath()
                                    + " to "
                                    + (shouldExist ? "exist" : "not exist"))
                    .that(fileExists)
                    .isEqualTo(shouldExist);
        }
    }
}
