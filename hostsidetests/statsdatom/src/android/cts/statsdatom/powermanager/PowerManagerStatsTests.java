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

package android.cts.statsdatom.powermanager;

import static com.google.common.truth.Truth.assertThat;

import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;
import android.os.WakeLockLevelEnum;

import com.android.ddmlib.testrunner.TestResult.TestStatus;
import com.android.os.AtomsProto;
import com.android.os.StatsLog;
import com.android.os.adpf.AdpfExtensionAtoms;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.result.TestRunResult;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.util.RunUtil;

import com.google.protobuf.ExtensionRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

/**
 * Test for Power Manager stats.
 *
 * <p>Build/Install/Run:
 * atest CtsStatsdAtomHostTestCases:PowerManagerStatsTests
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class PowerManagerStatsTests extends BaseHostJUnit4Test implements IBuildReceiver {
    private static final String DEVICE_TEST_PKG = "com.android.server.cts.device.statsdatom";
    private static final String DEVICE_TEST_CLASS = ".PowerManagerTests";
    private static final String ADPF_ATOM_APP_PKG = "com.android.server.cts.device.statsdatom";
    private static final String ADPF_ATOM_APP_APK = "CtsStatsdAdpfApp.apk";

    private IBuildInfo mCtsBuild;

    @Before
    public void setUp() throws Exception {
        assertThat(mCtsBuild).isNotNull();
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        DeviceUtils.installStatsdTestApp(getDevice(), mCtsBuild);
        DeviceUtils.installTestApp(getDevice(), ADPF_ATOM_APP_APK, ADPF_ATOM_APP_PKG, mCtsBuild);
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);
    }

    @After
    public void tearDown() throws Exception {
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        DeviceUtils.uninstallStatsdTestApp(getDevice());
        DeviceUtils.uninstallTestApp(getDevice(), ADPF_ATOM_APP_APK);
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mCtsBuild = buildInfo;
    }

    @Test
    public void testAcquireModifyAndReleasedWakelockIsPushed() throws Exception {
        int atomId = AtomsProto.Atom.WAKELOCK_STATE_CHANGED_FIELD_NUMBER;

        String testMethod = "testAcquireModifyAndReleasedWakelock";
        TestDescription desc = TestDescription.fromString(
                DEVICE_TEST_PKG + DEVICE_TEST_CLASS + "#" + testMethod);
        ConfigUtils.uploadConfigForPushedAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                atomId);
        TestRunResult testRunResult = DeviceUtils.runDeviceTestsOnStatsdApp(getDevice(),
                DEVICE_TEST_CLASS, testMethod);

        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);

        TestStatus status = testRunResult.getTestResults().get(desc).getStatus();
        assertThat(status).isEqualTo(TestStatus.PASSED);
        ExtensionRegistry registry = ExtensionRegistry.newInstance();
        AdpfExtensionAtoms.registerAllExtensions(registry);
        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice(),
                        registry).stream()
                .filter(eventMetricData -> eventMetricData.getAtom().getWakelockStateChanged()
                        .getTag().equals("TestWakelockForCts")).toList();
        assertThat(data.size()).isEqualTo(8);

        validateWakelockAtomFields(data.get(0).getAtom().getWakelockStateChanged(),
                "TestWakelockForCts", WakeLockLevelEnum.PARTIAL_WAKE_LOCK,
                AtomsProto.WakelockStateChanged.State.ACQUIRE);

        // Validate the uid which acquired the wakelock is released because of worksource
        int acquirerUid =
                data.get(0).getAtom().getWakelockStateChanged().getAttributionNode(0).getUid();
        validateWakelockAtomFields(acquirerUid, data.get(1).getAtom().getWakelockStateChanged(),
                "TestWakelockForCts", WakeLockLevelEnum.PARTIAL_WAKE_LOCK,
                AtomsProto.WakelockStateChanged.State.RELEASE);

        // Validate the UIDs supplied in the worksource are acquired
        validateWakelockAtomFields(1010, data.get(2).getAtom().getWakelockStateChanged(),
                "TestWakelockForCts", WakeLockLevelEnum.PARTIAL_WAKE_LOCK,
                AtomsProto.WakelockStateChanged.State.ACQUIRE);
        validateWakelockAtomFields(2010, data.get(3).getAtom().getWakelockStateChanged(),
                "TestWakelockForCts", WakeLockLevelEnum.PARTIAL_WAKE_LOCK,
                AtomsProto.WakelockStateChanged.State.ACQUIRE);

        // Validate the UIDs supplied in the new worksource are acquired, and the old UIDs are
        // released
        validateWakelockAtomFields(3010, data.get(4).getAtom().getWakelockStateChanged(),
                "TestWakelockForCts", WakeLockLevelEnum.PARTIAL_WAKE_LOCK,
                AtomsProto.WakelockStateChanged.State.ACQUIRE);
        validateWakelockAtomFields(1010, data.get(5).getAtom().getWakelockStateChanged(),
                "TestWakelockForCts", WakeLockLevelEnum.PARTIAL_WAKE_LOCK,
                AtomsProto.WakelockStateChanged.State.RELEASE);
        validateWakelockAtomFields(2010, data.get(6).getAtom().getWakelockStateChanged(),
                "TestWakelockForCts", WakeLockLevelEnum.PARTIAL_WAKE_LOCK,
                AtomsProto.WakelockStateChanged.State.RELEASE);

        // With the release of the wakelock, we release the acquired UIDs
        validateWakelockAtomFields(3010, data.get(7).getAtom().getWakelockStateChanged(),
                "TestWakelockForCts", WakeLockLevelEnum.PARTIAL_WAKE_LOCK,
                AtomsProto.WakelockStateChanged.State.RELEASE);
    }

    private void validateWakelockAtomFields(int uid,
                                            AtomsProto.WakelockStateChanged wakelockStateChanged,
                                            String tag, WakeLockLevelEnum wakeLockLevelEnum,
                                            AtomsProto.WakelockStateChanged.State state) {
        assertThat(wakelockStateChanged.getAttributionNode(0).getUid()).isEqualTo(uid);
        validateWakelockAtomFields(wakelockStateChanged, tag, wakeLockLevelEnum, state);

    }

    private void validateWakelockAtomFields(AtomsProto.WakelockStateChanged wakelockStateChanged,
                                            String tag, WakeLockLevelEnum wakeLockLevelEnum,
                                            AtomsProto.WakelockStateChanged.State state) {
        assertThat(wakelockStateChanged.getTag()).isEqualTo(tag);
        assertThat(wakelockStateChanged.getType()).isEqualTo(wakeLockLevelEnum);
        assertThat(wakelockStateChanged.getState()).isEqualTo(state);
    }
}
