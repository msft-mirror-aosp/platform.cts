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

import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.ReportUtils;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.host.HostFlagsValueProvider;

import com.android.os.AtomsProto;
import com.android.os.StatsLog;
import com.android.tradefed.device.ITestDevice;
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
    private static final String PERSISTABLE_TIMEOUT_ACTIVITY = "PersistableTimeoutActivity";
    private static final String PERSISTABLE_DELAYED_ACTIVITY = "PersistableDelayedActivity";
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
    private TestApp mSharedApp1 =
            new TestApp("com.android.cts.stopandkillsharedapp1", "CtsStopAndKillSharedUidApp1.apk");
    private TestApp mSharedApp2 =
            new TestApp("com.android.cts.stopandkillsharedapp2", "CtsStopAndKillSharedUidApp2.apk");

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
        mSharedApp1.uninstall();
        mSharedApp2.uninstall();
        mApp1.deleteStateFile();
        mApp2.deleteStateFile();
        mApp3.deleteStateFile();
        mSharedApp1.deleteStateFile();
        mSharedApp2.deleteStateFile();
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

        PackageInstallMetrics.setup(getDevice(), mApp1.pkg);
        mApp1.installPackage("-r");
        PackageInstallMetrics metrics =
                PackageInstallMetrics.collect(getDevice(), getAppUid(mApp1.pkg));

        // Assert that the app was not stopped because it timed out.
        mApp1.assertStateFileCreatedOnStop(/* shouldExist= */ false);

        // Assert that the wait step duration was high (indicates it hit the system STOP_TIMEOUT of
        // 11s).
        long duration = metrics.getStopAndKillDurationMs();
        assertWithMessage("Expected long wait duration for timeout activity")
                .that(duration)
                .isAtLeast(10000);
        assertThat(duration).isLessThan(20000);
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
        PackageInstallMetrics.setup(getDevice(), mApp1.pkg);

        mApp1.installPackage();

        PackageInstallMetrics metrics =
                PackageInstallMetrics.collect(getDevice(), getAppUid(mApp1.pkg));
        long duration = metrics.getStopAndKillDurationMs();

        assertWithMessage("Didn't find report with STOP_AND_KILL step").that(duration).isAtLeast(0);

        AtomsProto.PackageInstallationSessionReported report = metrics.getReport(0);
        // INSTALL_FROM_ADB is true
        assertThat(report.getInstallFlags() & 0x00000020).isNotEqualTo(0);
        // When installed from adb, package name is empty
        assertThat(report.getPackageName()).isEmpty();
        assertThat(report.getUid()).isEqualTo(getAppUid(mApp1.pkg));
    }

    /**
     * Verifies that when the 'wait_for_kill_on_package_update' flag is enabled, the app's instance
     * state is saved during an update and restored in the new version.
     */
    @Test
    @RequiresFlagsDisabled(Flags.FLAG_ENABLE_APP_RESTART_AFTER_UPDATE)
    public void testUpdate_flagDisabled_doesNotEmitStopAndKillMetric() throws Exception {
        PackageInstallMetrics.setup(getDevice(), mApp1.pkg);

        mApp1.installPackage();

        PackageInstallMetrics metrics =
                PackageInstallMetrics.collect(getDevice(), getAppUid(mApp1.pkg));
        long duration = metrics.getStopAndKillDurationMs();

        assertWithMessage("Emitted STOP_AND_KILL step when flag disabled")
                .that(duration)
                .isEqualTo(-1);
    }

    /** Verifies that the app's importance is captured and reported in the installation metrics. */
    @Test
    public void testUpdate_emitsAppImportanceMetric() throws Exception {
        PackageInstallMetrics.setup(getDevice(), mApp1.pkg);

        mApp1.installPackage();
        launchActivityAndAssertResumed(mApp1.persistableActivity);

        // Update the package
        mApp1.installPackage("-r");

        PackageInstallMetrics metrics =
                PackageInstallMetrics.collect(getDevice(), getAppUid(mApp1.pkg));

        // We expect two reports: one for the initial install, and one for the update (-r)
        assertThat(metrics.getReportCount()).isAtLeast(2);

        // The update report should have the app_importance captured
        boolean foundUpdateReportWithImportance = false;
        for (int i = 0; i < metrics.getReportCount(); i++) {
            AtomsProto.PackageInstallationSessionReported report = metrics.getReport(i);
            if (report.getIsReplace()) {
                // ActivityManager.IMPORTANCE_FOREGROUND == 100
                assertThat(report.getAppImportance()).isEqualTo(100);
                foundUpdateReportWithImportance = true;
            }
        }
        assertWithMessage("Didn't find update report with non-zero app_importance")
                .that(foundUpdateReportWithImportance)
                .isTrue();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_APP_RESTART_AFTER_UPDATE)
    public void testUpdate_sharedUid_waitsOnlyThreeSeconds() throws Exception {
        mSharedApp1.installPackage();
        mSharedApp2.installPackage();

        String setting = "enable_freeform_support";
        String originalSetting = getDevice().executeShellCommand("settings get global " + setting);
        try {
            getDevice().executeShellCommand("settings put global " + setting + " 1");
            // Launch both shared UID activities.
            launchActivityInFreeForm(
                    mSharedApp1.persistableActivity, mSharedApp2.persistableActivity);

            PackageInstallMetrics.setup(getDevice(), mSharedApp1.pkg);
            // Update shared app 1. This should trigger the shared UID timeout logic.
            mSharedApp1.installPackage("-r");
            PackageInstallMetrics metrics =
                    PackageInstallMetrics.collect(getDevice(), getAppUid(mSharedApp1.pkg));

            // Assert that the installation was fast (timeout is 3s).
            long duration = metrics.getStopAndKillDurationMs();
            assertWithMessage("Expected shared-UID optimized wait duration (3s)")
                    .that(duration)
                    .isAtLeast(2500);
            assertThat(duration).isLessThan(4000);
        } finally {
            getDevice()
                    .executeShellCommand("settings put global " + setting + " " + originalSetting);
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_APP_RESTART_AFTER_UPDATE)
    public void testUpdate_sharedUid_forceKillsUpdatingPackage() throws Exception {
        mSharedApp1.installPackage();
        mSharedApp2.installPackage();

        String setting = "enable_freeform_support";
        String originalSetting = getDevice().executeShellCommand("settings get global " + setting);
        try {
            getDevice().executeShellCommand("settings put global " + setting + " 1");
            // Launch both shared UID activities. Shared app 1 uses timeout activity.
            launchActivityInFreeForm(
                    mSharedApp1.persistableTimeoutActivity, mSharedApp2.persistableActivity);

            mSharedApp1.assertProcessRunning(true);
            mSharedApp2.assertProcessRunning(true);

            // Update shared app 1.
            mSharedApp1.installPackage("-r");

            // Assert that shared app 1 was terminated.
            mSharedApp1.assertProcessRunning(false);

            // Assert that shared app 2 is STILL RUNNING.
            mSharedApp2.assertProcessRunning(true);
        } finally {
            getDevice()
                    .executeShellCommand("settings put global " + setting + " " + originalSetting);
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_APP_RESTART_AFTER_UPDATE)
    public void testUpdate_sharedUid_noSiblingRunning_waitsFullTimeout() throws Exception {
        mSharedApp1.installPackage();
        mSharedApp2.installPackage();

        // Launch only shared app 1 with a 9s delay (shared app 2 is NOT running).
        launchActivityAndAssertResumed(mSharedApp1.persistableDelayedActivity);

        mSharedApp1.assertProcessRunning(true);
        mSharedApp2.assertProcessRunning(false);

        PackageInstallMetrics.setup(getDevice(), mSharedApp1.pkg);
        // Update shared app 1. Since no sibling apps are running, it should wait for the full
        // timeout (15s) instead of the optimized shared-UID timeout (3s).
        // A 9s delay is enough for the app to save its state if we wait 15s, but NOT if we wait 3s.
        mSharedApp1.installPackage("-r");
        PackageInstallMetrics metrics =
                PackageInstallMetrics.collect(getDevice(), getAppUid(mSharedApp1.pkg));

        // Confirm wait duration was ~9s (not 3s).
        long duration = metrics.getStopAndKillDurationMs();
        assertWithMessage("Expected long wait duration for non-shared update")
                .that(duration)
                .isAtLeast(8000);
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
        public final String persistableTimeoutActivity;
        public final String persistableDelayedActivity;
        public final String aliasActivity;

        TestApp(String pkg, String apk) {
            this.pkg = pkg;
            this.apk = apk;
            this.persistableActivity = getComponentName(pkg, PERSISTABLE_ACTIVITY);
            this.persistableTimeoutActivity = getComponentName(pkg, PERSISTABLE_TIMEOUT_ACTIVITY);
            this.persistableDelayedActivity = getComponentName(pkg, PERSISTABLE_DELAYED_ACTIVITY);
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

        boolean isProcessRunning() throws Exception {
            String pid = getDevice().executeShellCommand("pidof " + pkg).trim();
            return !pid.isEmpty();
        }

        void assertProcessRunning(boolean shouldBeRunning) throws Exception {
            assertWithMessage(
                            "Expected process "
                                    + pkg
                                    + " to be "
                                    + (shouldBeRunning ? "running" : "not running"))
                    .that(isProcessRunning())
                    .isEqualTo(shouldBeRunning);
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

    public static class PackageInstallMetrics {
        private final List<AtomsProto.PackageInstallationSessionReported> mReports;

        private PackageInstallMetrics(List<AtomsProto.PackageInstallationSessionReported> reports) {
            mReports = reports;
        }

        /** Prepares the device to collect metrics for a specific package. */
        public static void setup(ITestDevice device, String pkgName) throws Exception {
            ConfigUtils.removeConfig(device);
            ReportUtils.clearReports(device);
            ConfigUtils.uploadConfigForPushedAtom(
                    device,
                    pkgName,
                    AtomsProto.Atom.PACKAGE_INSTALLATION_SESSION_REPORTED_FIELD_NUMBER);
            // Short sleep to ensure config is active
            RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);
        }

        /** Collects the reports from the device for a specific package. */
        public static PackageInstallMetrics collect(ITestDevice device, int uid) throws Exception {
            RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);
            List<AtomsProto.PackageInstallationSessionReported> reports = new ArrayList<>();
            for (StatsLog.EventMetricData data : ReportUtils.getEventMetricDataList(device)) {
                if (data.getAtom().hasPackageInstallationSessionReported()) {
                    AtomsProto.PackageInstallationSessionReported report =
                            data.getAtom().getPackageInstallationSessionReported();
                    if (report.getUid() == uid) {
                        reports.add(report);
                    }
                }
            }
            return new PackageInstallMetrics(reports);
        }

        /** Returns the total number of collected reports. */
        public int getReportCount() {
            return mReports.size();
        }

        /** Returns the report at the specified index. */
        public AtomsProto.PackageInstallationSessionReported getReport(int index) {
            return mReports.get(index);
        }

        /** Extracts the duration of the 'Stop and Kill' step (Step 9). */
        public long getStopAndKillDurationMs() {
            for (int i = 0; i < mReports.size(); i++) {
                AtomsProto.PackageInstallationSessionReported report = mReports.get(i);
                if (report.getInstallStepsCount() == 0) continue;
                for (int j = 0; j < report.getInstallStepsCount(); j++) {
                    if (report.getInstallSteps(j) == STEP_FREEZE_INSTALL_STOP_AND_KILL) {
                        return report.getStepDurationMillis(j);
                    }
                }
            }
            return -1;
        }
    }
}
