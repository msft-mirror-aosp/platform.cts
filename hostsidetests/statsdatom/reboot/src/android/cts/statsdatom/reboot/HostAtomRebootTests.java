/*
 * Copyright (C) 2020 The Android Open Source Project
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
package android.cts.statsdatom.reboot;

import static com.google.common.truth.Truth.assertThat;

import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;

import com.android.os.AtomsProto.Atom;
import com.android.os.StatsLog.EventMetricData;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.util.RunUtil;

import com.google.protobuf.ExtensionRegistry;

import java.util.List;
import java.util.stream.Collectors;

/** Statsd atom tests that involve device reboot. */
public class HostAtomRebootTests extends DeviceTestCase implements IBuildReceiver {

    private static final String FEATURE_AUTOMOTIVE = "android.hardware.type.automotive";

    private IBuildInfo mCtsBuild;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        assertThat(mCtsBuild).isNotNull();
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        DeviceUtils.installStatsdTestApp(getDevice(), mCtsBuild);
        DeviceUtils.turnBatteryStatsAutoResetOff(getDevice());
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);
    }

    @Override
    protected void tearDown() throws Exception {
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        DeviceUtils.uninstallStatsdTestApp(getDevice());
        DeviceUtils.turnBatteryStatsAutoResetOn(getDevice());
        super.tearDown();
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mCtsBuild = buildInfo;
    }

    public void testAtomsLoggedOnBoot() throws Exception {
        ConfigUtils.uploadConfigForPushedAtoms(
                getDevice(),
                DeviceUtils.STATSD_ATOM_TEST_PKG,
                new int[] {
                    Atom.DEVICE_IDLE_MODE_STATE_CHANGED_FIELD_NUMBER,
                    Atom.SCREEN_STATE_CHANGED_FIELD_NUMBER,
                    Atom.BATTERY_LEVEL_CHANGED_FIELD_NUMBER,
                    Atom.CHARGING_STATE_CHANGED_FIELD_NUMBER,
                    Atom.PLUGGED_STATE_CHANGED_FIELD_NUMBER,
                    Atom.BOOT_TIME_EVENT_ELAPSED_TIME_REPORTED_FIELD_NUMBER
                });

        DeviceUtils.rebootDeviceAndWaitUntilReady(getDevice());
        RunUtil.getDefault().sleep(10_000);

        // Get events from the report after boot.
        List<Atom> atoms =
                ReportUtils.getEventMetricDataList(
                                getDevice(),
                                ExtensionRegistry.getEmptyRegistry(), /*reportIndex*/
                                1)
                        .stream()
                        .map(EventMetricData::getAtom)
                        .collect(Collectors.toList());

        assertThat(atoms.stream().anyMatch(Atom::hasDeviceIdleModeStateChanged)).isTrue();
        assertThat(atoms.stream().anyMatch(Atom::hasScreenStateChanged)).isTrue();
        assertThat(atoms.stream().anyMatch(Atom::hasBootTimeEventElapsedTimeReported)).isTrue();
        if (!DeviceUtils.hasFeature(getDevice(), FEATURE_AUTOMOTIVE)) {
            assertThat(atoms.stream().anyMatch(Atom::hasBatteryLevelChanged)).isTrue();
            assertThat(atoms.stream().anyMatch(Atom::hasChargingStateChanged)).isTrue();
            assertThat(atoms.stream().anyMatch(Atom::hasPluggedStateChanged)).isTrue();
        }
    }
}
