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
import com.android.os.packagemanager.PackageInstallerSessionReported;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RunWith(DeviceJUnit4ClassRunner.class)
@AppModeFull
public class PackageInstallerSessionReportedStatsTests extends BaseHostJUnit4Test {
    private static final String HELPER_PACKAGE = "com.android.cts.packagemanager.stats.device";
    private static final String HELPER_CLASS = ".PackageInstallerSessionReportedStatsTestsHelper";

    @Before
    public void setUp() throws Exception {
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);
    }

    @After
    public void tearDown() throws Exception {
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
    }

    @Test
    public void testMetricsReportedForAbandonedSession() throws Exception {
        ConfigUtils.uploadConfigForPushedAtom(
                getDevice(),
                DeviceUtils.STATSD_ATOM_TEST_PKG,
                PackagemanagerExtensionAtoms.PACKAGE_INSTALLER_SESSION_REPORTED_FIELD_NUMBER);
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);
        ExtensionRegistry registry = ExtensionRegistry.newInstance();
        PackagemanagerExtensionAtoms.registerAllExtensions(registry);

        Map<String, String> testResult =
                Utils.runDeviceTests(
                        getDevice(),
                        HELPER_PACKAGE,
                        HELPER_CLASS,
                        "createSessionAndAbandon",
                        new HashMap<>());
        assertThat(testResult).isNotNull();
        assertThat(testResult.size()).isEqualTo(2);
        int sessionId = Integer.parseInt(testResult.get("sessionId"));
        int installerUid = Integer.parseInt(testResult.get("installerUid"));
        List<StatsLog.EventMetricData> data =
                ReportUtils.getEventMetricDataList(getDevice(), registry);
        data = retrieveSessionMetrics(data, sessionId);
        assertThat(data.size()).isEqualTo(1);
        PackageInstallerSessionReported atom =
                data.getFirst()
                        .getAtom()
                        .getExtension(PackagemanagerExtensionAtoms.packageInstallerSessionReported);
        assertThat(atom.getSessionId()).isEqualTo(sessionId);
        assertThat(atom.getInstallerUid()).isEqualTo(installerUid);
        assertThat(atom.getStatusCode())
                .isEqualTo(PackageInstallerSessionReported.StatusCode.STATUS_FAILURE_ABORTED);
        assertThat(atom.getUserId()).isEqualTo(getDevice().getCurrentUser());
    }

    private List<StatsLog.EventMetricData> retrieveSessionMetrics(
            List<StatsLog.EventMetricData> eventMetricData, int sessionId) throws Exception {
        List<StatsLog.EventMetricData> dataList = new ArrayList<>();
        if (eventMetricData == null || eventMetricData.isEmpty()) {
            return dataList;
        }
        for (int i = 0; i < eventMetricData.size(); i++) {
            PackageInstallerSessionReported atom =
                    eventMetricData
                            .get(i)
                            .getAtom()
                            .getExtension(
                                    PackagemanagerExtensionAtoms.packageInstallerSessionReported);
            if (atom.getSessionId() == sessionId) {
                dataList.add(eventMetricData.get(i));
            }
        }
        return dataList;
    }
}
