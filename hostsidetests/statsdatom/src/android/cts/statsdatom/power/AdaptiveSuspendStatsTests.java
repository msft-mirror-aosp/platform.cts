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

package android.cts.statsdatom.power;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static org.junit.Assert.fail;

import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.ReportUtils;
import android.cts.statsdatom.lib.DeviceUtils;

import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.host.HostFlagsValueProvider;

import com.android.os.power.AdaptiveSuspendStats;
import com.android.os.power.PowerExtensionAtoms;

import com.android.os.AtomsProto;
import com.android.internal.os.StatsdConfigProto;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.util.RunUtil;
import com.google.protobuf.ExtensionRegistry;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.stream.Collectors;

import com.android.server.stats.Flags;

@RunWith(DeviceJUnit4ClassRunner.class)
public class AdaptiveSuspendStatsTests extends BaseHostJUnit4Test {
    private ExtensionRegistry mRegistry;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
        HostFlagsValueProvider.createCheckFlagsRule(this::getDevice);

    @Before
    public void setUp() throws Exception {
        mRegistry = ExtensionRegistry.newInstance();
        PowerExtensionAtoms.registerAllExtensions(mRegistry);
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
    }

    @After
    public void tearDown() throws Exception {
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
    }

    private List<AdaptiveSuspendStats> getPulledAtoms() throws Exception {
        StatsdConfigProto.StatsdConfig.Builder config =
                ConfigUtils.createConfigBuilder(DeviceUtils.STATSD_ATOM_TEST_PKG);
        ConfigUtils.addGaugeMetric(config, PowerExtensionAtoms.ADAPTIVE_SUSPEND_STATS_FIELD_NUMBER);
        ConfigUtils.uploadConfig(getDevice(), config);

        AtomTestUtils.sendAppBreadcrumbReportedAtom(getDevice());
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);

        final List<AtomsProto.Atom> atoms =
                ReportUtils.getGaugeMetricAtoms(getDevice(), mRegistry, true);

        return atoms.stream()
                .filter(atom -> atom.hasExtension(PowerExtensionAtoms.adaptiveSuspendStats))
                .map(atom -> atom.getExtension(PowerExtensionAtoms.adaptiveSuspendStats))
                .collect(Collectors.toList());
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ADD_ADAPTIVE_SUSPEND_STATS_PULLER)
    public void testAdaptiveSuspendStats() throws Exception {
        List<AdaptiveSuspendStats> statsList = getPulledAtoms();
        assertThat(statsList).isNotEmpty();

        for (AdaptiveSuspendStats stats : statsList) {
            assertThat(stats.getTotalSuspendAttempts()).isAtLeast(0);
            assertThat(stats.getTotalFailedSuspends()).isAtLeast(0);
            assertThat(stats.getTotalShortSuspends()).isAtLeast(0);
            assertThat(stats.getTimeSuspendedLongMillis()).isAtLeast(0L);
            assertThat(stats.getTimeSuspendedShortMillis()).isAtLeast(0L);
            assertThat(stats.getBreakEvenMillis()).isAtLeast(0);
            assertThat(stats.getTimeSuspendingSuccessMillis()).isAtLeast(0L);
            assertThat(stats.getTimeSuspendingFailMillis()).isAtLeast(0L);
            assertThat(stats.getNewBackoffs()).isAtLeast(0);
            assertThat(stats.getBackoffContinuations()).isAtLeast(0);
            assertThat(stats.getTimeBackedOffMillis()).isAtLeast(0L);
            assertThat(stats.getMaxBackoffContinuations()).isAtLeast(0);
            assertThat(stats.getNewBadSuspends()).isAtLeast(0);
            assertThat(stats.getEarlyRecoveryBadSuspends()).isAtLeast(0);
            assertThat(stats.getSuspendDurationMillisBinsList().stream()
                    .allMatch(bin -> bin >= 0)).isTrue();
            assertThat(stats.getSuspendDurationMillisBinsList()).isNotEmpty();
            assertThat(stats.getConsecutiveBadSuspendBinsList().stream()
                    .allMatch(bin -> bin >= 0)).isTrue();
            assertThat(stats.getConsecutiveBadSuspendBinsList()).isNotEmpty();

            // Logic checks
            assertThat(stats.getTotalFailedSuspends()).isAtMost(stats.getTotalSuspendAttempts());
            assertThat(stats.getTotalShortSuspends()).isAtMost(stats.getTotalSuspendAttempts());
            long sumOfDurationBins = stats.getSuspendDurationMillisBinsList().stream()
                    .mapToInt(Integer::intValue)
                    .sum();
            assertThat(sumOfDurationBins)
                    .isEqualTo(stats.getTotalSuspendAttempts() - stats.getTotalFailedSuspends());
        }
    }
}
