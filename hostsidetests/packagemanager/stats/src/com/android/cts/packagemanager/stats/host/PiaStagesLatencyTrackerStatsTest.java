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
package com.android.cts.packagemanager.stats.host;

import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;

import com.android.os.StatsLog;
import com.android.os.packagemanager.PackagemanagerExtensionAtoms;
import com.android.os.packagemanager.PiaInstallStagesReported;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.util.RunUtil;

import com.google.common.truth.Truth;
import com.google.protobuf.ExtensionRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(DeviceJUnit4ClassRunner.class)
public class PiaStagesLatencyTrackerStatsTest extends BaseHostJUnit4Test {

    private static final String DEVICE_TEST_PKG = "android.packageinstaller.install.cts";
    private static final String DEVICE_TEST_CLASS = ".PiaStagesLatencyTrackerTest";

    @Before
    public void setUp() throws Exception {
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
    }

    @After
    public void tearDown() throws Exception {
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
    }

    @Test
    public void testPiaInstall_uriBasedInstall_StagesReported() throws Exception {
        // 1. Find the correct Package Installer package name for the current device
        // (AOSP uses com.android..., while Pixel devices use com.google.android...)
        String piPackageName = "com.android.packageinstaller";
        String pmList =
                getDevice()
                        .executeShellCommand(
                                "pm list packages com.google.android.packageinstaller");
        if (pmList != null && pmList.contains("com.google.android.packageinstaller")) {
            piPackageName = "com.google.android.packageinstaller";
        }

        // 2. Upload config whitelisting the Package Installer so StatsD accepts its logs
        ConfigUtils.uploadConfigForPushedAtom(
                getDevice(),
                piPackageName,
                PackagemanagerExtensionAtoms.PIA_INSTALL_STAGES_REPORTED_FIELD_NUMBER);

        // 2. Trigger your device-side Kotlin test to run the UI
        DeviceUtils.runDeviceTests(
                getDevice(),
                DEVICE_TEST_PKG,
                DEVICE_TEST_CLASS,
                "testTracker_UriBasedInstall_ExecutesWithoutCrashing");

        // 3. Wait a moment for StatsD to process the event
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);

        // 4. Use ReportUtils to pull the data from the device to the host
        ExtensionRegistry registry = ExtensionRegistry.newInstance();
        PackagemanagerExtensionAtoms.registerAllExtensions(registry);

        List<StatsLog.EventMetricData> data =
                ReportUtils.getEventMetricDataList(getDevice(), registry);
        Truth.assertThat(data).isNotEmpty();

        // 5. Verify your atom was logged correctly!
        boolean foundTargetAtom = false;
        for (StatsLog.EventMetricData event : data) {

            // Check if the atom has your specific extension
            if (event.getAtom()
                    .hasExtension(PackagemanagerExtensionAtoms.piaInstallStagesReported)) {

                // Extract the extension payload
                PiaInstallStagesReported atom =
                        event.getAtom()
                                .getExtension(
                                        PackagemanagerExtensionAtoms.piaInstallStagesReported);

                Truth.assertThat(atom.getInstallStagesList().size()).isGreaterThan(0);
                Truth.assertThat(atom.getInstallStagesList())
                        .containsExactly(
                                PiaInstallStagesReported.InstallStage.STAGE_STAGING,
                                PiaInstallStagesReported.InstallStage.STAGE_USER_ACTION_REQUIRED,
                                PiaInstallStagesReported.InstallStage.STAGE_INSTALLING,
                                PiaInstallStagesReported.InstallStage.STAGE_SUCCESS);
                Truth.assertThat(atom.getInstallStagesLatencyMsList().size())
                        .isEqualTo(atom.getInstallStagesList().size());

                foundTargetAtom = true;
                break;
            }
        }

        Truth.assertThat(foundTargetAtom).isTrue();
    }

    @Test
    public void testPiaInstall_installFailed_StagesReported() throws Exception {
        // 1. Find the correct Package Installer package name for the current device
        String piPackageName = "com.android.packageinstaller";
        String pmList =
                getDevice()
                        .executeShellCommand(
                                "pm list packages com.google.android.packageinstaller");
        if (pmList != null && pmList.contains("com.google.android.packageinstaller")) {
            piPackageName = "com.google.android.packageinstaller";
        }

        // 2. Upload config whitelisting the Package Installer so StatsD accepts its logs
        ConfigUtils.uploadConfigForPushedAtom(
                getDevice(),
                piPackageName,
                PackagemanagerExtensionAtoms.PIA_INSTALL_STAGES_REPORTED_FIELD_NUMBER);

        // 3. Trigger your device-side Kotlin test for a failing install
        DeviceUtils.runDeviceTests(
                getDevice(),
                DEVICE_TEST_PKG,
                DEVICE_TEST_CLASS,
                "testTracker_InstallFails_ExecutesWithoutCrashing");

        // 4. Wait a moment for StatsD to process the event
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);

        // 5. Use ReportUtils to pull the data from the device to the host
        ExtensionRegistry registry = ExtensionRegistry.newInstance();
        PackagemanagerExtensionAtoms.registerAllExtensions(registry);

        List<StatsLog.EventMetricData> data =
                ReportUtils.getEventMetricDataList(getDevice(), registry);
        Truth.assertThat(data).isNotEmpty();

        // 6. Verify your atom was logged correctly despite the failure
        boolean foundTargetAtom = false;
        for (StatsLog.EventMetricData event : data) {
            if (event.getAtom()
                    .hasExtension(PackagemanagerExtensionAtoms.piaInstallStagesReported)) {
                PiaInstallStagesReported atom =
                        event.getAtom()
                                .getExtension(
                                        PackagemanagerExtensionAtoms.piaInstallStagesReported);

                // Even on failure, the tracker should have recorded the initial stages
                Truth.assertThat(atom.getInstallStagesList().size()).isGreaterThan(0);
                Truth.assertThat(atom.getInstallStagesLatencyMsList().size())
                        .isEqualTo(atom.getInstallStagesList().size());
                Truth.assertThat(atom.getInstallStagesList())
                        .containsExactly(
                                PiaInstallStagesReported.InstallStage.STAGE_STAGING,
                                PiaInstallStagesReported.InstallStage.STAGE_USER_ACTION_REQUIRED);

                foundTargetAtom = true;
                break;
            }
        }

        Truth.assertThat(foundTargetAtom).isTrue();
    }
}
