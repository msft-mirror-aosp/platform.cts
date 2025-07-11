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

package com.android.cts.packagemanager.stats.host;

import static com.google.common.truth.Truth.assertThat;

import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;
import android.platform.test.annotations.AppModeFull;

import com.android.os.StatsLog;
import com.android.os.packagemanager.PackageChangedBroadcastReported;
import com.android.os.packagemanager.PackageChangedBroadcastReported.PackageChangedReason;
import com.android.os.packagemanager.PackagemanagerExtensionAtoms;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.util.RunUtil;

import com.google.protobuf.ExtensionRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

/** Tests for PackageChangedBroadcastReported logging. */
@RunWith(DeviceJUnit4ClassRunner.class)
@AppModeFull
public class PackageChangedBroadcastReportedStatsTests extends BaseHostJUnit4Test {
    private static final int SYSTEM_UID = 1000;
    private static final int PER_USER_RANGE = 100000;

    private static final String TEST_INSTALL_APK = "CtsStatsdAtomTestComponentStateApp.apk";
    private static final String TEST_INSTALL_PACKAGE =
            "com.android.cts.packagemanager.stats.testcomponentstateapp";
    private static final String HELPER_PACKAGE = "com.android.cts.packagemanager.stats.device";
    private static final String HELPER_CLASS =
            HELPER_PACKAGE + ".ComponentStateChangedReportedStatsTestsHelper";
    private static final String STATIC_SHARED_LIB_PROVIDER1_APK =
            "CtsStatsdAtomStaticSharedLibProviderApp1.apk";
    private static final String STATIC_SHARED_LIB_PROVIDER2_APK =
            "CtsStatsdAtomStaticSharedLibProviderApp2.apk";
    private static final String STATIC_SHARED_LIB_PROVIDER_PACKAGE =
            "com.android.cts.packagemanager.stats.staticsharedlibprovider";
    private static final String STATIC_SHARED_LIB_CONSUMER_APK =
            "CtsStatsdAtomStaticSharedLibConsumerApp.apk";
    private static final String STATIC_SHARED_LIB_CONSUMER_PACKAGE =
            "com.android.cts.packagemanager.stats.staticsharedlibconsumerapp";
    private static final String TEST_METHOD_SET_APPLICATION_ENABLED_SETTING =
            "testSetApplicationEnabledSetting";
    private static final String TEST_METHOD_SET_COMPONENT_ENABLED_SETTING_FOR_LAUNCHER_ACTIVITY =
            "testSetComponentEnabledSettingForLauncherActivity";
    private static final String TEST_METHOD_SET_COMPONENT_ENABLED_SETTING_ENABLED_THEN_DISABLED =
            "testSetComponentEnabledSettingEnabledThenDisabled";
    private static final String TEST_METHOD_SET_COMPONENT_ENABLED_SETTING_TWO_LAUNCHER_ACTIVITIES =
            "testComponentStateChangedReportedForTwoDifferentStateLauncherActivities";
    private static final String TEST_METHOD_CALL_SET_MIME_GROUP = "testCallSetMimeGroup";

    @Before
    public void setUp() throws Exception {
        installPackage("CtsStatsdAtomApp.apk");
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);
    }

    @After
    public void tearDown() throws Exception {
        getDevice().uninstallPackage(TEST_INSTALL_PACKAGE);
        getDevice().uninstallPackage(STATIC_SHARED_LIB_PROVIDER_PACKAGE);
        getDevice().uninstallPackage(STATIC_SHARED_LIB_CONSUMER_PACKAGE);
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
    }

    @Test
    public void testPackageChangedBroadcastReportedForWholeAppChange() throws Throwable {
        ConfigUtils.uploadConfigForPushedAtom(
                getDevice(),
                DeviceUtils.STATSD_ATOM_TEST_PKG,
                PackagemanagerExtensionAtoms.PACKAGE_CHANGED_BROADCAST_REPORTED_FIELD_NUMBER);
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);

        ExtensionRegistry registry = ExtensionRegistry.newInstance();
        PackagemanagerExtensionAtoms.registerAllExtensions(registry);

        installPackage(TEST_INSTALL_APK);
        assertThat(
                        getDevice()
                                .isPackageInstalled(
                                        TEST_INSTALL_PACKAGE,
                                        String.valueOf(getDevice().getCurrentUser())))
                .isTrue();

        // Run test in CTS package
        runDeviceTests(
                getDevice(),
                HELPER_PACKAGE,
                HELPER_CLASS,
                TEST_METHOD_SET_APPLICATION_ENABLED_SETTING);
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);

        List<StatsLog.EventMetricData> data =
                ReportUtils.getEventMetricDataList(getDevice(), registry);
        data =
                retrieveEventMetricDataChangeFromTestApp(
                        data,
                        PackageChangedReason.PACKAGE_CHANGED_REASON_PACKAGE_STATE_CHANGED,
                        TEST_INSTALL_PACKAGE);
        assertThat(data.isEmpty()).isFalse();

        PackageChangedBroadcastReported atom =
                data.get(0)
                        .getAtom()
                        .getExtension(PackagemanagerExtensionAtoms.packageChangedBroadcastReported);
        assertThat(atom.getCallingUid())
                .isEqualTo(PackageManagerStatsTestsBase.getAppUid(getDevice(), HELPER_PACKAGE));
    }

    @Test
    public void testPackageChangedBroadcastReportedForComponentChange() throws Throwable {
        ConfigUtils.uploadConfigForPushedAtom(
                getDevice(),
                DeviceUtils.STATSD_ATOM_TEST_PKG,
                PackagemanagerExtensionAtoms.PACKAGE_CHANGED_BROADCAST_REPORTED_FIELD_NUMBER);
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);

        ExtensionRegistry registry = ExtensionRegistry.newInstance();
        PackagemanagerExtensionAtoms.registerAllExtensions(registry);

        installPackage(TEST_INSTALL_APK);
        assertThat(
                        getDevice()
                                .isPackageInstalled(
                                        TEST_INSTALL_PACKAGE,
                                        String.valueOf(getDevice().getCurrentUser())))
                .isTrue();

        // Run test in CTS package
        runDeviceTests(
                getDevice(),
                HELPER_PACKAGE,
                HELPER_CLASS,
                TEST_METHOD_SET_COMPONENT_ENABLED_SETTING_FOR_LAUNCHER_ACTIVITY);
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);

        List<StatsLog.EventMetricData> data =
                ReportUtils.getEventMetricDataList(getDevice(), registry);
        data =
                retrieveEventMetricDataChangeFromTestApp(
                        data,
                        PackageChangedReason.PACKAGE_CHANGED_REASON_COMPONENT_STATE_CHANGED,
                        TEST_INSTALL_PACKAGE);
        assertThat(data.isEmpty()).isFalse();

        PackageChangedBroadcastReported atom =
                data.get(0)
                        .getAtom()
                        .getExtension(PackagemanagerExtensionAtoms.packageChangedBroadcastReported);
        assertThat(atom.getCallingUid())
                .isEqualTo(PackageManagerStatsTestsBase.getAppUid(getDevice(), HELPER_PACKAGE));
    }

    @Test
    public void testPackageChangedBroadcastReportedEnabledThenDisabledComponent() throws Throwable {
        ConfigUtils.uploadConfigForPushedAtom(
                getDevice(),
                DeviceUtils.STATSD_ATOM_TEST_PKG,
                PackagemanagerExtensionAtoms.PACKAGE_CHANGED_BROADCAST_REPORTED_FIELD_NUMBER);
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);

        ExtensionRegistry registry = ExtensionRegistry.newInstance();
        PackagemanagerExtensionAtoms.registerAllExtensions(registry);

        installPackage(TEST_INSTALL_APK);
        assertThat(
                        getDevice()
                                .isPackageInstalled(
                                        TEST_INSTALL_PACKAGE,
                                        String.valueOf(getDevice().getCurrentUser())))
                .isTrue();

        // Run test in CTS package
        runDeviceTests(
                getDevice(),
                HELPER_PACKAGE,
                HELPER_CLASS,
                TEST_METHOD_SET_COMPONENT_ENABLED_SETTING_ENABLED_THEN_DISABLED);
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);

        List<StatsLog.EventMetricData> data =
                ReportUtils.getEventMetricDataList(getDevice(), registry);
        data =
                retrieveEventMetricDataChangeFromTestApp(
                        data,
                        PackageChangedReason.PACKAGE_CHANGED_REASON_COMPONENT_STATE_CHANGED,
                        TEST_INSTALL_PACKAGE);
        assertThat(data.size()).isEqualTo(2);

        PackageChangedBroadcastReported atom1 =
                data.get(0)
                        .getAtom()
                        .getExtension(PackagemanagerExtensionAtoms.packageChangedBroadcastReported);
        assertThat(atom1.getCallingUid())
                .isEqualTo(PackageManagerStatsTestsBase.getAppUid(getDevice(), HELPER_PACKAGE));
        assertThat(atom1.getChangedUid())
                .isEqualTo(
                        PackageManagerStatsTestsBase.getAppUid(getDevice(), TEST_INSTALL_PACKAGE));

        PackageChangedBroadcastReported atom2 =
                data.get(1)
                        .getAtom()
                        .getExtension(PackagemanagerExtensionAtoms.packageChangedBroadcastReported);
        assertThat(atom2.getCallingUid())
                .isEqualTo(PackageManagerStatsTestsBase.getAppUid(getDevice(), HELPER_PACKAGE));
        assertThat(atom2.getChangedUid())
                .isEqualTo(
                        PackageManagerStatsTestsBase.getAppUid(getDevice(), TEST_INSTALL_PACKAGE));
    }

    @Test
    public void testPackageChangedBroadcastReportedForTwoDifferentComponents() throws Throwable {
        ConfigUtils.uploadConfigForPushedAtom(
                getDevice(),
                DeviceUtils.STATSD_ATOM_TEST_PKG,
                PackagemanagerExtensionAtoms.PACKAGE_CHANGED_BROADCAST_REPORTED_FIELD_NUMBER);
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);

        ExtensionRegistry registry = ExtensionRegistry.newInstance();
        PackagemanagerExtensionAtoms.registerAllExtensions(registry);

        installPackage(TEST_INSTALL_APK);
        assertThat(
                        getDevice()
                                .isPackageInstalled(
                                        TEST_INSTALL_PACKAGE,
                                        String.valueOf(getDevice().getCurrentUser())))
                .isTrue();

        // Run test in CTS package
        runDeviceTests(
                getDevice(),
                HELPER_PACKAGE,
                HELPER_CLASS,
                TEST_METHOD_SET_COMPONENT_ENABLED_SETTING_TWO_LAUNCHER_ACTIVITIES);
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);

        List<StatsLog.EventMetricData> data =
                ReportUtils.getEventMetricDataList(getDevice(), registry);
        data =
                retrieveEventMetricDataChangeFromTestApp(
                        data,
                        PackageChangedReason.PACKAGE_CHANGED_REASON_COMPONENT_STATE_CHANGED,
                        TEST_INSTALL_PACKAGE);
        assertThat(data.size()).isEqualTo(2);

        PackageChangedBroadcastReported atom1 =
                data.get(0)
                        .getAtom()
                        .getExtension(PackagemanagerExtensionAtoms.packageChangedBroadcastReported);
        assertThat(atom1.getCallingUid())
                .isEqualTo(PackageManagerStatsTestsBase.getAppUid(getDevice(), HELPER_PACKAGE));
        assertThat(atom1.getChangedUid())
                .isEqualTo(
                        PackageManagerStatsTestsBase.getAppUid(getDevice(), TEST_INSTALL_PACKAGE));

        PackageChangedBroadcastReported atom2 =
                data.get(1)
                        .getAtom()
                        .getExtension(PackagemanagerExtensionAtoms.packageChangedBroadcastReported);
        assertThat(atom2.getCallingUid())
                .isEqualTo(PackageManagerStatsTestsBase.getAppUid(getDevice(), HELPER_PACKAGE));
        assertThat(atom2.getChangedUid())
                .isEqualTo(
                        PackageManagerStatsTestsBase.getAppUid(getDevice(), TEST_INSTALL_PACKAGE));
    }

    @Test
    public void testPackageChangedBroadcastReportedForResetComponentEnabledSettings()
            throws Throwable {
        ConfigUtils.uploadConfigForPushedAtom(
                getDevice(),
                DeviceUtils.STATSD_ATOM_TEST_PKG,
                PackagemanagerExtensionAtoms.PACKAGE_CHANGED_BROADCAST_REPORTED_FIELD_NUMBER);
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);

        ExtensionRegistry registry = ExtensionRegistry.newInstance();
        PackagemanagerExtensionAtoms.registerAllExtensions(registry);

        installPackage(TEST_INSTALL_APK);
        assertThat(
                        getDevice()
                                .isPackageInstalled(
                                        TEST_INSTALL_PACKAGE,
                                        String.valueOf(getDevice().getCurrentUser())))
                .isTrue();

        // Run test in CTS package to change the component state.
        runDeviceTests(
                getDevice(),
                HELPER_PACKAGE,
                HELPER_CLASS,
                TEST_METHOD_SET_COMPONENT_ENABLED_SETTING_FOR_LAUNCHER_ACTIVITY);
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);

        // Clear the application user data to reset the component state
        getDevice()
                .executeShellCommand(
                        "pm clear --user "
                                + getDevice().getCurrentUser()
                                + " "
                                + TEST_INSTALL_PACKAGE);

        // After resetting the component state, the PackageManagerService will delay 1 second to
        // trigger package changed. Delay 3 seconds to make sure that the PackageManagerService
        // has triggered package changed.
        RunUtil.getDefault().sleep(3000);

        List<StatsLog.EventMetricData> data =
                ReportUtils.getEventMetricDataList(getDevice(), registry);
        data =
                retrieveEventMetricDataChangeFromTestApp(
                        data,
                        PackageChangedReason.PACKAGE_CHANGED_REASON_COMPONENT_STATE_RESET,
                        TEST_INSTALL_PACKAGE);
        assertThat(data.size()).isEqualTo(1);

        PackageChangedBroadcastReported atom =
                data.get(0)
                        .getAtom()
                        .getExtension(PackagemanagerExtensionAtoms.packageChangedBroadcastReported);
        assertThat(atom.getCallingUid()).isEqualTo(SYSTEM_UID);
    }

    @Test
    public void testPackageChangedBroadcastReportedForMimeGroupChanged() throws Throwable {
        ConfigUtils.uploadConfigForPushedAtom(
                getDevice(),
                DeviceUtils.STATSD_ATOM_TEST_PKG,
                PackagemanagerExtensionAtoms.PACKAGE_CHANGED_BROADCAST_REPORTED_FIELD_NUMBER);
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);

        ExtensionRegistry registry = ExtensionRegistry.newInstance();
        PackagemanagerExtensionAtoms.registerAllExtensions(registry);

        installPackage(TEST_INSTALL_APK);
        assertThat(
                        getDevice()
                                .isPackageInstalled(
                                        TEST_INSTALL_PACKAGE,
                                        String.valueOf(getDevice().getCurrentUser())))
                .isTrue();

        // Run test in CTS package to change the mime group.
        runDeviceTests(getDevice(), HELPER_PACKAGE, HELPER_CLASS, TEST_METHOD_CALL_SET_MIME_GROUP);
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);

        List<StatsLog.EventMetricData> data =
                ReportUtils.getEventMetricDataList(getDevice(), registry);
        data =
                retrieveEventMetricDataChangeFromTestApp(
                        data,
                        PackageChangedReason.PACKAGE_CHANGED_REASON_MIME_GROUP_CHANGED,
                        HELPER_PACKAGE);
        assertThat(data.size()).isEqualTo(1);

        PackageChangedBroadcastReported atom =
                data.get(0)
                        .getAtom()
                        .getExtension(PackagemanagerExtensionAtoms.packageChangedBroadcastReported);
        assertThat(atom.getCallingUid())
                .isEqualTo(PackageManagerStatsTestsBase.getAppUid(getDevice(), HELPER_PACKAGE));
    }

    @Test
    public void testPackageChangedBroadcastReportedForOverlayChanged() throws Throwable {
        ConfigUtils.uploadConfigForPushedAtom(
                getDevice(),
                DeviceUtils.STATSD_ATOM_TEST_PKG,
                PackagemanagerExtensionAtoms.PACKAGE_CHANGED_BROADCAST_REPORTED_FIELD_NUMBER);
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);

        ExtensionRegistry registry = ExtensionRegistry.newInstance();
        PackagemanagerExtensionAtoms.registerAllExtensions(registry);

        // It will trigger overlay changed after installing the test apk.
        installPackage(TEST_INSTALL_APK);
        assertThat(
                        getDevice()
                                .isPackageInstalled(
                                        TEST_INSTALL_PACKAGE,
                                        String.valueOf(getDevice().getCurrentUser())))
                .isTrue();

        List<StatsLog.EventMetricData> data =
                ReportUtils.getEventMetricDataList(getDevice(), registry);
        data =
                retrieveEventMetricDataChangeFromTestApp(
                        data,
                        PackageChangedReason.PACKAGE_CHANGED_REASON_OVERLAY_CHANGED,
                        TEST_INSTALL_PACKAGE);
        assertThat(data.size()).isEqualTo(1);

        PackageChangedBroadcastReported atom =
                data.get(0)
                        .getAtom()
                        .getExtension(PackagemanagerExtensionAtoms.packageChangedBroadcastReported);
        assertThat(atom.getCallingUid()).isEqualTo(SYSTEM_UID);
    }

    @Test
    public void testPackageChangedBroadcastReportedForStaticSharedLibraryChanged()
            throws Throwable {
        ConfigUtils.uploadConfigForPushedAtom(
                getDevice(),
                DeviceUtils.STATSD_ATOM_TEST_PKG,
                PackagemanagerExtensionAtoms.PACKAGE_CHANGED_BROADCAST_REPORTED_FIELD_NUMBER);
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);

        ExtensionRegistry registry = ExtensionRegistry.newInstance();
        PackagemanagerExtensionAtoms.registerAllExtensions(registry);

        // Install the static shared library.
        installPackage(STATIC_SHARED_LIB_PROVIDER1_APK);

        // Install the client
        installPackage(STATIC_SHARED_LIB_CONSUMER_APK);

        // Update the static shared library.
        installPackage(STATIC_SHARED_LIB_PROVIDER2_APK);

        List<StatsLog.EventMetricData> data =
                ReportUtils.getEventMetricDataList(getDevice(), registry);
        data =
                retrieveEventMetricDataChangeFromTestApp(
                        data,
                        PackageChangedReason.PACKAGE_CHANGED_REASON_STATIC_SHARED_LIBRARY_CHANGED,
                        STATIC_SHARED_LIB_CONSUMER_PACKAGE);
        assertThat(data.size()).isEqualTo(1);

        PackageChangedBroadcastReported atom =
                data.get(0)
                        .getAtom()
                        .getExtension(PackagemanagerExtensionAtoms.packageChangedBroadcastReported);
        assertThat(atom.getCallingUid()).isEqualTo(SYSTEM_UID);
    }

    List<StatsLog.EventMetricData> retrieveEventMetricDataChangeFromTestApp(
            List<StatsLog.EventMetricData> eventMetricData,
            PackageChangedReason packageChangedReason,
            String testPackageName)
            throws Exception {
        List<StatsLog.EventMetricData> dataList = new ArrayList<>();
        if (eventMetricData == null || eventMetricData.size() == 0) {
            return dataList;
        }
        int packageUid =
                getPackageUid(
                        PackageManagerStatsTestsBase.getAppUid(getDevice(), testPackageName),
                        packageChangedReason);
        for (int i = 0; i < eventMetricData.size(); i++) {
            PackageChangedBroadcastReported atom =
                    eventMetricData
                            .get(i)
                            .getAtom()
                            .getExtension(
                                    PackagemanagerExtensionAtoms.packageChangedBroadcastReported);
            if (atom != null
                    && atom.getReason() == packageChangedReason
                    && atom.getChangedUid() == packageUid) {
                dataList.add(eventMetricData.get(i));
            }
        }
        return dataList;
    }

    private static int getPackageUid(int appId, PackageChangedReason packageChangedReason) {
        // It uses AndroidPackage#getUid() method to report the UID when overlay change and static
        // shared library change. The UID will not compose from the userId and the appId. It will
        // cause test failure. The remaining cases use UserHandle#getUid() to report the UID. The
        // UID is composed from the userId and the appId. The workaround is only to check the appId
        // when overlay change or static shared library change.
        return switch (packageChangedReason) {
            case PackageChangedReason.PACKAGE_CHANGED_REASON_OVERLAY_CHANGED,
                    PackageChangedReason.PACKAGE_CHANGED_REASON_STATIC_SHARED_LIBRARY_CHANGED ->
                    appId % PER_USER_RANGE;
            default -> appId;
        };
    }
}
