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

package android.cts.statsdatom.hardware.health;

import static com.google.common.truth.Truth.assertThat;

import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;

import com.android.os.AtomsProto;
import com.android.os.hardware.health.StorageExtensionAtoms;
import com.android.os.hardware.health.StorageHealth;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.util.RunUtil;

import com.google.common.collect.Range;
import com.google.protobuf.ExtensionRegistry;

import java.util.List;

public class StorageHealthTests extends DeviceTestCase implements IBuildReceiver {
    private IBuildInfo mCtsBuild;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        assertThat(mCtsBuild).isNotNull();
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        DeviceUtils.installStatsdTestApp(getDevice(), mCtsBuild);
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);
    }

    @Override
    protected void tearDown() throws Exception {
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        DeviceUtils.uninstallStatsdTestApp(getDevice());
        super.tearDown();
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mCtsBuild = buildInfo;
    }

    public void testStorageHealthAtomValid() throws Exception {
        List<AtomsProto.Atom> atoms = pullStorageHealthAsGaugeMetric();
        if (atoms.size() == 0) {
            CLog.w("Skipping test - no atom returned");
            return;
        }

        assertThat(atoms.size()).isEqualTo(1);
        StorageHealth bh = atoms.get(0).getExtension(StorageExtensionAtoms.storageHealth);
        assertThat(bh.getRemainingLifetimePercent()).isIn(Range.closed(-1, 100));
    }

    private List<AtomsProto.Atom> pullStorageHealthAsGaugeMetric() throws Exception {
        ConfigUtils.uploadConfigForPulledAtom(
                getDevice(),
                DeviceUtils.STATSD_ATOM_TEST_PKG,
                StorageExtensionAtoms.STORAGE_HEALTH_FIELD_NUMBER);

        AtomTestUtils.sendAppBreadcrumbReportedAtom(getDevice());
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);

        ExtensionRegistry registry = ExtensionRegistry.newInstance();
        StorageExtensionAtoms.registerAllExtensions(registry);

        List<AtomsProto.Atom> atoms = ReportUtils.getGaugeMetricAtoms(getDevice(), registry, false);

        return atoms;
    }
}
