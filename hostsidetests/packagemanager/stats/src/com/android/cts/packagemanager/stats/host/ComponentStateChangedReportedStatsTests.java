/*
 * Copyright (C) 2024 The Android Open Source Project
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

import static android.content.pm.Flags.FLAG_COMPONENT_STATE_CHANGED_METRICS;

import static com.android.os.packagemanager.ComponentStateChangedReported.ComponentState.COMPONENT_STATE_DEFAULT;
import static com.android.os.packagemanager.ComponentStateChangedReported.ComponentState.COMPONENT_STATE_ENABLED;

import static com.google.common.truth.Truth.assertThat;

import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;
import android.platform.test.annotations.RequiresFlagsEnabled;

import com.android.os.StatsLog;
import com.android.os.packagemanager.ComponentStateChangedReported;
import com.android.os.packagemanager.PackagemanagerExtensionAtoms;
import com.android.tradefed.util.RunUtil;

import com.google.protobuf.ExtensionRegistry;

import java.util.List;

/**
 * Tests for ComponentStateChangedReported logging.
 */
public class ComponentStateChangedReportedStatsTests extends PackageManagerStatsTestsBase {
    private static final String TEST_INSTALL_APK = "CtsStatsdAtomTestComponentStateApp.apk";
    private static final String TEST_INSTALL_PACKAGE =
            "com.android.cts.packagemanager.stats.testcomponentstateapp";
    private static final String HELPER_PACKAGE = "com.android.cts.packagemanager.stats.device";
    private static final String HELPER_CLASS = ".ComponentStateChangedReportedStatsTestsHelper";
    private static final String TEST_METHOD_SET_APPLICATION_ENABLED_SETTING =
            "testSetApplicationEnabledSetting";
    private static final String TEST_METHOD_SET_COMPONENT_ENABLED_SETTING_FOR_LAUNCHER_ACTIVITY =
            "testSetComponentEnabledSettingForLauncherActivity";
    private static final String TEST_METHOD_SET_COMPONENT_ENABLED_SETTING_FOR_NO_LAUNCHER_ACTIVITY =
            "testSetComponentEnabledSettingForNoLauncherActivity";
    private static final String TEST_METHOD_SET_COMPONENT_ENABLED_SETTING_ENABLED_THEN_DISABLED =
            "testSetComponentEnabledSettingEnabledThenDisabled";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);
    }

    @Override
    protected void tearDown() throws Exception {
        getDevice().uninstallPackage(TEST_INSTALL_PACKAGE);
        super.tearDown();
    }

    @RequiresFlagsEnabled(FLAG_COMPONENT_STATE_CHANGED_METRICS)
    public void testComponentStateChangedReportedForWholeApp() throws Throwable {
        ConfigUtils.uploadConfigForPushedAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                PackagemanagerExtensionAtoms.COMPONENT_STATE_CHANGED_REPORTED_FIELD_NUMBER);
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);

        ExtensionRegistry registry = ExtensionRegistry.newInstance();
        PackagemanagerExtensionAtoms.registerAllExtensions(registry);

        DeviceUtils.installTestApp(getDevice(), TEST_INSTALL_APK, TEST_INSTALL_PACKAGE,
                mCtsBuild);
        assertThat(getDevice().isPackageInstalled(TEST_INSTALL_PACKAGE,
                String.valueOf(getDevice().getCurrentUser()))).isTrue();

        // Run test in CTS package
        DeviceUtils.runDeviceTests(getDevice(), HELPER_PACKAGE, HELPER_CLASS,
                TEST_METHOD_SET_APPLICATION_ENABLED_SETTING);
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice(),
                registry);
        assertThat(data).isNotNull();

        ComponentStateChangedReported atom = data.get(0).getAtom().getExtension(
                PackagemanagerExtensionAtoms.componentStateChangedReported);
        assertThat(atom.getUid()).isEqualTo(getAppUid(TEST_INSTALL_PACKAGE));
        assertThat(atom.getComponentOldState()).isEqualTo(COMPONENT_STATE_DEFAULT);
        assertThat(atom.getComponentNewState()).isEqualTo(COMPONENT_STATE_ENABLED);
        assertThat(atom.getIsLauncher()).isFalse();
        assertThat(atom.getIsForWholeApp()).isTrue();
    }

    @RequiresFlagsEnabled(FLAG_COMPONENT_STATE_CHANGED_METRICS)
    public void testComponentStateChangedReportedForLauncherActivity() throws Throwable {
        ConfigUtils.uploadConfigForPushedAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                PackagemanagerExtensionAtoms.COMPONENT_STATE_CHANGED_REPORTED_FIELD_NUMBER);
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);

        ExtensionRegistry registry = ExtensionRegistry.newInstance();
        PackagemanagerExtensionAtoms.registerAllExtensions(registry);

        DeviceUtils.installTestApp(getDevice(), TEST_INSTALL_APK, TEST_INSTALL_PACKAGE,
                mCtsBuild);
        assertThat(getDevice().isPackageInstalled(TEST_INSTALL_PACKAGE,
                String.valueOf(getDevice().getCurrentUser()))).isTrue();

        // Run test in CTS package
        DeviceUtils.runDeviceTests(getDevice(), HELPER_PACKAGE, HELPER_CLASS,
                TEST_METHOD_SET_COMPONENT_ENABLED_SETTING_FOR_LAUNCHER_ACTIVITY);
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice(),
                registry);
        assertThat(data).isNotNull();

        ComponentStateChangedReported atom = data.get(0).getAtom().getExtension(
                PackagemanagerExtensionAtoms.componentStateChangedReported);
        assertThat(atom.getUid()).isEqualTo(getAppUid(TEST_INSTALL_PACKAGE));
        assertThat(atom.getComponentOldState()).isEqualTo(COMPONENT_STATE_DEFAULT);
        assertThat(atom.getComponentNewState()).isEqualTo(COMPONENT_STATE_ENABLED);
        assertThat(atom.getIsLauncher()).isTrue();
        assertThat(atom.getIsForWholeApp()).isFalse();
    }

    @RequiresFlagsEnabled(FLAG_COMPONENT_STATE_CHANGED_METRICS)
    public void testComponentStateChangedReportedForNoLauncherActivity() throws Throwable {
        ConfigUtils.uploadConfigForPushedAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                PackagemanagerExtensionAtoms.COMPONENT_STATE_CHANGED_REPORTED_FIELD_NUMBER);
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);

        ExtensionRegistry registry = ExtensionRegistry.newInstance();
        PackagemanagerExtensionAtoms.registerAllExtensions(registry);

        DeviceUtils.installTestApp(getDevice(), TEST_INSTALL_APK, TEST_INSTALL_PACKAGE,
                mCtsBuild);
        assertThat(getDevice().isPackageInstalled(TEST_INSTALL_PACKAGE,
                String.valueOf(getDevice().getCurrentUser()))).isTrue();

        // Run test in CTS package
        DeviceUtils.runDeviceTests(getDevice(), HELPER_PACKAGE, HELPER_CLASS,
                TEST_METHOD_SET_COMPONENT_ENABLED_SETTING_FOR_NO_LAUNCHER_ACTIVITY);
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice(),
                registry);
        assertThat(data).isNotNull();

        ComponentStateChangedReported atom = data.get(0).getAtom().getExtension(
                PackagemanagerExtensionAtoms.componentStateChangedReported);
        assertThat(atom.getUid()).isEqualTo(getAppUid(TEST_INSTALL_PACKAGE));
        assertThat(atom.getComponentOldState()).isEqualTo(COMPONENT_STATE_DEFAULT);
        assertThat(atom.getComponentNewState()).isEqualTo(COMPONENT_STATE_ENABLED);
        assertThat(atom.getIsLauncher()).isFalse();
        assertThat(atom.getIsForWholeApp()).isFalse();
    }

    @RequiresFlagsEnabled(FLAG_COMPONENT_STATE_CHANGED_METRICS)
    public void testComponentStateChangedReportedEnabledThenDisabled() throws Throwable {
        ConfigUtils.uploadConfigForPushedAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                PackagemanagerExtensionAtoms.COMPONENT_STATE_CHANGED_REPORTED_FIELD_NUMBER);
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);

        ExtensionRegistry registry = ExtensionRegistry.newInstance();
        PackagemanagerExtensionAtoms.registerAllExtensions(registry);

        DeviceUtils.installTestApp(getDevice(), TEST_INSTALL_APK, TEST_INSTALL_PACKAGE,
                mCtsBuild);
        assertThat(getDevice().isPackageInstalled(TEST_INSTALL_PACKAGE,
                String.valueOf(getDevice().getCurrentUser()))).isTrue();

        // Run test in CTS package
        DeviceUtils.runDeviceTests(getDevice(), HELPER_PACKAGE, HELPER_CLASS,
                TEST_METHOD_SET_COMPONENT_ENABLED_SETTING_ENABLED_THEN_DISABLED);
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice(),
                registry);
        assertThat(data.size()).isEqualTo(2);
    }
}
