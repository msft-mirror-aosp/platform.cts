/*
 * Copyright 2019 The Android Open Source Project
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

package android.media.tv.tuner.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.media.tv.tuner.Descrambler;
import android.media.tv.tuner.LnbCallback;
import android.media.tv.tuner.Lnb;
import android.media.tv.tuner.Tuner;
import android.media.tv.tuner.dvr.DvrPlayback;
import android.media.tv.tuner.dvr.DvrRecorder;
import android.media.tv.tuner.dvr.OnPlaybackStatusChangedListener;
import android.media.tv.tuner.dvr.OnRecordStatusChangedListener;
import android.media.tv.tuner.filter.FilterCallback;
import android.media.tv.tuner.filter.FilterEvent;
import android.media.tv.tuner.filter.Filter;
import android.media.tv.tuner.filter.TimeFilter;

import android.media.tv.tuner.frontend.AnalogFrontendCapabilities;
import android.media.tv.tuner.frontend.AnalogFrontendSettings;
import android.media.tv.tuner.frontend.Atsc3FrontendCapabilities;
import android.media.tv.tuner.frontend.Atsc3FrontendSettings;
import android.media.tv.tuner.frontend.Atsc3PlpInfo;
import android.media.tv.tuner.frontend.Atsc3PlpSettings;
import android.media.tv.tuner.frontend.AtscFrontendCapabilities;
import android.media.tv.tuner.frontend.AtscFrontendSettings;
import android.media.tv.tuner.frontend.DvbcFrontendCapabilities;
import android.media.tv.tuner.frontend.DvbcFrontendSettings;
import android.media.tv.tuner.frontend.DvbsCodeRate;
import android.media.tv.tuner.frontend.DvbsFrontendCapabilities;
import android.media.tv.tuner.frontend.DvbsFrontendSettings;
import android.media.tv.tuner.frontend.DvbtFrontendCapabilities;
import android.media.tv.tuner.frontend.DvbtFrontendSettings;
import android.media.tv.tuner.frontend.FrontendCapabilities;
import android.media.tv.tuner.frontend.FrontendInfo;
import android.media.tv.tuner.frontend.FrontendSettings;
import android.media.tv.tuner.frontend.FrontendStatus;
import android.media.tv.tuner.frontend.Isdbs3FrontendCapabilities;
import android.media.tv.tuner.frontend.Isdbs3FrontendSettings;
import android.media.tv.tuner.frontend.IsdbsFrontendCapabilities;
import android.media.tv.tuner.frontend.IsdbsFrontendSettings;
import android.media.tv.tuner.frontend.IsdbtFrontendCapabilities;
import android.media.tv.tuner.frontend.IsdbtFrontendSettings;
import android.media.tv.tuner.frontend.ScanCallback;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class TunerTest {
    private static final String TAG = "MediaTunerTest";

    private static final int TIMEOUT_MS = 10000;

    private Context mContext;
    private Tuner mTuner;
    private CountDownLatch mLockLatch = new CountDownLatch(1);

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getTargetContext();
        InstrumentationRegistry
                .getInstrumentation().getUiAutomation().adoptShellPermissionIdentity();
        if (!hasTuner()) return;
        mTuner = new Tuner(mContext, null, 100);
    }

    @After
    public void tearDown() {
        if (mTuner != null) {
          mTuner.close();
          mTuner = null;
        }
    }

    @Test
    public void testTunerConstructor() throws Exception {
        if (!hasTuner()) return;
        assertNotNull(mTuner);
    }

    @Test
    public void testTuning() throws Exception {
        if (!hasTuner()) return;
        List<Integer> ids = mTuner.getFrontendIds();
        assertFalse(ids.isEmpty());

        FrontendInfo info = mTuner.getFrontendInfoById(ids.get(0));
        int res = mTuner.tune(createFrontendSettings(info));
        assertEquals(Tuner.RESULT_SUCCESS, res);
        res = mTuner.cancelTuning();
        assertEquals(Tuner.RESULT_SUCCESS, res);
    }

    @Test
    public void testScanning() throws Exception {
        if (!hasTuner()) return;
        List<Integer> ids = mTuner.getFrontendIds();
        assertFalse(ids.isEmpty());
        for (int id : ids) {
            FrontendInfo info = mTuner.getFrontendInfoById(id);
            if (info != null && info.getType() == FrontendSettings.TYPE_ATSC) {
                mLockLatch = new CountDownLatch(1);
                int res = mTuner.scan(
                        createFrontendSettings(info),
                        Tuner.SCAN_TYPE_AUTO,
                        getExecutor(),
                        getScanCallback());
               assertEquals(Tuner.RESULT_SUCCESS, res);
               assertTrue(mLockLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
               res = mTuner.cancelScanning();
               assertEquals(Tuner.RESULT_SUCCESS, res);
            }
        }
        mLockLatch = null;
    }

    @Test
    public void testFrontendStatus() throws Exception {
        if (!hasTuner()) return;
        List<Integer> ids = mTuner.getFrontendIds();
        assertFalse(ids.isEmpty());

        FrontendInfo info = mTuner.getFrontendInfoById(ids.get(0));
        int res = mTuner.tune(createFrontendSettings(info));
        FrontendStatus status = mTuner.getFrontendStatus(
                new int[] {
                        FrontendStatus.FRONTEND_STATUS_TYPE_DEMOD_LOCK,
                        FrontendStatus.FRONTEND_STATUS_TYPE_SNR,
                        FrontendStatus.FRONTEND_STATUS_TYPE_BER,
                        FrontendStatus.FRONTEND_STATUS_TYPE_PER,
                        FrontendStatus.FRONTEND_STATUS_TYPE_PRE_BER,
                        FrontendStatus.FRONTEND_STATUS_TYPE_SIGNAL_QUALITY,
                        FrontendStatus.FRONTEND_STATUS_TYPE_SIGNAL_STRENGTH,
                        FrontendStatus.FRONTEND_STATUS_TYPE_SYMBOL_RATE,
                        FrontendStatus.FRONTEND_STATUS_TYPE_FEC,
                        FrontendStatus.FRONTEND_STATUS_TYPE_MODULATION,
                        FrontendStatus.FRONTEND_STATUS_TYPE_SPECTRAL,
                        FrontendStatus.FRONTEND_STATUS_TYPE_LNB_VOLTAGE,
                        FrontendStatus.FRONTEND_STATUS_TYPE_PLP_ID,
                        FrontendStatus.FRONTEND_STATUS_TYPE_EWBS,
                        FrontendStatus.FRONTEND_STATUS_TYPE_AGC,
                        FrontendStatus.FRONTEND_STATUS_TYPE_LNA,
                        FrontendStatus.FRONTEND_STATUS_TYPE_LAYER_ERROR,
                        FrontendStatus.FRONTEND_STATUS_TYPE_MER,
                        FrontendStatus.FRONTEND_STATUS_TYPE_FREQ_OFFSET,
                        FrontendStatus.FRONTEND_STATUS_TYPE_HIERARCHY,
                        FrontendStatus.FRONTEND_STATUS_TYPE_RF_LOCK,
                        FrontendStatus.FRONTEND_STATUS_TYPE_ATSC3_PLP_INFO
                });
        assertNotNull(status);

        status.isDemodLocked();
        status.getSnr();
        status.getBer();
        status.getPer();
        status.getPerBer();
        status.getSignalQuality();
        status.getSignalStrength();
        status.getSymbolRate();
        status.getInnerFec();
        status.getModulation();
        status.getSpectralInversion();
        status.getLnbVoltage();
        status.getPlpId();
        status.isEwbs();
        status.getAgc();
        status.isLnaOn();
        status.getLayerErrors();
        status.getMer();
        status.getFreqOffset();
        status.getHierarchy();
        status.isRfLocked();
        status.getAtsc3PlpTuningInfo();
    }

    @Test
    public void testOpenLnb() throws Exception {
        if (!hasTuner()) return;
        Lnb lnb = mTuner.openLnb(getExecutor(), getLnbCallback());
        assertNotNull(lnb);
    }

    @Test
    public void testLnbSetVoltage() throws Exception {
        // TODO: move lnb-related tests to a separate file.
        if (!hasTuner()) return;
        Lnb lnb = mTuner.openLnb(getExecutor(), getLnbCallback());
        assertEquals(lnb.setVoltage(Lnb.VOLTAGE_5V), Tuner.RESULT_SUCCESS);
    }

    @Test
    public void testLnbSetTone() throws Exception {
        if (!hasTuner()) return;
        Lnb lnb = mTuner.openLnb(getExecutor(), getLnbCallback());
        assertEquals(lnb.setTone(Lnb.TONE_NONE), Tuner.RESULT_SUCCESS);
    }

    @Test
    public void testLnbSetPosistion() throws Exception {
        if (!hasTuner()) return;
        Lnb lnb = mTuner.openLnb(getExecutor(), getLnbCallback());
        assertEquals(
                lnb.setSatellitePosition(Lnb.POSITION_A), Tuner.RESULT_SUCCESS);
    }

    @Test
    public void testOpenFilter() throws Exception {
        if (!hasTuner()) return;
        Filter f = mTuner.openFilter(
                Filter.TYPE_TS, Filter.SUBTYPE_SECTION, 1000, getExecutor(), getFilterCallback());
        assertNotNull(f);
    }

    @Test
    public void testOpenTimeFilter() throws Exception {
        if (!hasTuner()) return;
        TimeFilter f = mTuner.openTimeFilter();
        assertNotNull(f);
    }

    @Test
    public void testOpenDescrambler() throws Exception {
        if (!hasTuner()) return;
        Descrambler d = mTuner.openDescrambler();
        assertNotNull(d);
    }

    @Test
    public void testOpenDvrRecorder() throws Exception {
        if (!hasTuner()) return;
        DvrRecorder d = mTuner.openDvrRecorder(100, getExecutor(), getRecordListener());
        assertNotNull(d);
    }

    @Test
    public void testOpenDvPlayback() throws Exception {
        if (!hasTuner()) return;
        DvrPlayback d = mTuner.openDvrPlayback(100, getExecutor(), getPlaybackListener());
        assertNotNull(d);
    }

    private boolean hasTuner() {
        return mContext.getPackageManager().hasSystemFeature("android.hardware.tv.tuner");
    }

    private Executor getExecutor() {
        return Runnable::run;
    }

    private LnbCallback getLnbCallback() {
        return new LnbCallback() {
            @Override
            public void onEvent(int lnbEventType) {}
            @Override
            public void onDiseqcMessage(byte[] diseqcMessage) {}
        };
    }

    private FilterCallback getFilterCallback() {
        return new FilterCallback() {
            @Override
            public void onFilterEvent(Filter filter, FilterEvent[] events) {}
            @Override
            public void onFilterStatusChanged(Filter filter, int status) {}
        };
    }

    private OnRecordStatusChangedListener getRecordListener() {
        return new OnRecordStatusChangedListener() {
            @Override
            public void onRecordStatusChanged(int status) {}
        };
    }

    private OnPlaybackStatusChangedListener getPlaybackListener() {
        return new OnPlaybackStatusChangedListener() {
            @Override
            public void onPlaybackStatusChanged(int status) {}
        };
    }

    private FrontendSettings createFrontendSettings(FrontendInfo info) {
            FrontendCapabilities caps = info.getFrontendCapabilities();
            int minFreq = info.getFrequencyRange().getLower();
            FrontendCapabilities feCaps = info.getFrontendCapabilities();
            switch(info.getType()) {
                case FrontendSettings.TYPE_ANALOG: {
                    AnalogFrontendCapabilities analogCaps = (AnalogFrontendCapabilities) caps;
                    int signalType = getFirstCapable(analogCaps.getSignalTypeCapability());
                    int sif = getFirstCapable(analogCaps.getSifStandardCapability());
                    return AnalogFrontendSettings
                            .builder()
                            .setFrequency(minFreq)
                            .setSignalType(signalType)
                            .setSifStandard(sif)
                            .build();
                }
                case FrontendSettings.TYPE_ATSC3: {
                    Atsc3FrontendCapabilities atsc3Caps = (Atsc3FrontendCapabilities) caps;
                    int bandwidth = getFirstCapable(atsc3Caps.getBandwidthCapability());
                    int demod = getFirstCapable(atsc3Caps.getDemodOutputFormatCapability());
                    return Atsc3FrontendSettings
                            .builder()
                            .setFrequency(minFreq)
                            .setBandwidth(bandwidth)
                            .setDemodOutputFormat(demod)
                            .build();
                }
                case FrontendSettings.TYPE_ATSC: {
                    AtscFrontendCapabilities atscCaps = (AtscFrontendCapabilities) caps;
                    int modulation = getFirstCapable(atscCaps.getModulationCapability());
                    return AtscFrontendSettings
                            .builder()
                            .setFrequency(minFreq)
                            .setModulation(modulation)
                            .build();
                }
                case FrontendSettings.TYPE_DVBC: {
                    DvbcFrontendCapabilities dvbcCaps = (DvbcFrontendCapabilities) caps;
                    int modulation = getFirstCapable(dvbcCaps.getModulationCapability());
                    int fec = getFirstCapable(dvbcCaps.getFecCapability());
                    int annex = getFirstCapable(dvbcCaps.getAnnexCapability());
                    return DvbcFrontendSettings
                            .builder()
                            .setFrequency(minFreq)
                            .setModulation(modulation)
                            .setInnerFec(fec)
                            .setAnnex(annex)
                            .build();
                }
                case FrontendSettings.TYPE_DVBS: {
                    DvbsFrontendCapabilities dvbsCaps = (DvbsFrontendCapabilities) caps;
                    int modulation = getFirstCapable(dvbsCaps.getModulationCapability());
                    int standard = getFirstCapable(dvbsCaps.getStandardCapability());
                    return DvbsFrontendSettings
                            .builder()
                            .setFrequency(minFreq)
                            .setModulation(modulation)
                            .setStandard(standard)
                            .build();
                }
                case FrontendSettings.TYPE_DVBT: {
                    DvbtFrontendCapabilities dvbtCaps = (DvbtFrontendCapabilities) caps;
                    int transmission = getFirstCapable(dvbtCaps.getTransmissionModeCapability());
                    int bandwidth = getFirstCapable(dvbtCaps.getBandwidthCapability());
                    int constellation = getFirstCapable(dvbtCaps.getConstellationCapability());
                    int codeRate = getFirstCapable(dvbtCaps.getCodeRateCapability());
                    int hierarchy = getFirstCapable(dvbtCaps.getHierarchyCapability());
                    int guardInterval = getFirstCapable(dvbtCaps.getGuardIntervalCapability());
                    return DvbtFrontendSettings
                            .builder()
                            .setFrequency(minFreq)
                            .setTransmissionMode(transmission)
                            .setBandwidth(bandwidth)
                            .setConstellation(constellation)
                            .setHierarchy(hierarchy)
                            .setHighPriorityCodeRate(codeRate)
                            .setLowPriorityCodeRate(codeRate)
                            .setGuardInterval(guardInterval)
                            .setStandard(DvbtFrontendSettings.STANDARD_T)
                            .setMiso(false)
                            .build();
                }
                case FrontendSettings.TYPE_ISDBS3: {
                    Isdbs3FrontendCapabilities isdbs3Caps = (Isdbs3FrontendCapabilities) caps;
                    int modulation = getFirstCapable(isdbs3Caps.getModulationCapability());
                    int codeRate = getFirstCapable(isdbs3Caps.getCodeRateCapability());
                    return Isdbs3FrontendSettings
                            .builder()
                            .setFrequency(minFreq)
                            .setModulation(modulation)
                            .setCodeRate(codeRate)
                            .build();
                }
                case FrontendSettings.TYPE_ISDBS: {
                    IsdbsFrontendCapabilities isdbsCaps = (IsdbsFrontendCapabilities) caps;
                    int modulation = getFirstCapable(isdbsCaps.getModulationCapability());
                    int codeRate = getFirstCapable(isdbsCaps.getCodeRateCapability());
                    return IsdbsFrontendSettings
                            .builder()
                            .setFrequency(minFreq)
                            .setModulation(modulation)
                            .setCodeRate(codeRate)
                            .build();
                }
                case FrontendSettings.TYPE_ISDBT: {
                    IsdbtFrontendCapabilities isdbtCaps = (IsdbtFrontendCapabilities) caps;
                    int mode = getFirstCapable(isdbtCaps.getModeCapability());
                    int bandwidth = getFirstCapable(isdbtCaps.getBandwidthCapability());
                    int modulation = getFirstCapable(isdbtCaps.getModulationCapability());
                    int codeRate = getFirstCapable(isdbtCaps.getCodeRateCapability());
                    int guardInterval = getFirstCapable(isdbtCaps.getGuardIntervalCapability());
                    return IsdbtFrontendSettings
                            .builder()
                            .setFrequency(minFreq)
                            .setModulation(modulation)
                            .setBandwidth(bandwidth)
                            .setMode(mode)
                            .setCodeRate(codeRate)
                            .setGuardInterval(guardInterval)
                            .build();
                }
                default:
                    break;
            }
        return null;
    }

    private int getFirstCapable(int caps) {
        if (caps == 0) return 0;
        int mask = 1;
        while ((mask & caps) == 0) {
            mask = mask << 1;
        }
        return (mask & caps);
    }

    private long getFirstCapable(long caps) {
        if (caps == 0) return 0;
        long mask = 1;
        while ((mask & caps) == 0) {
            mask = mask << 1;
        }
        return (mask & caps);
    }

    private ScanCallback getScanCallback() {
        return new ScanCallback() {
            @Override
            public void onLocked() {
                if (mLockLatch != null) {
                    mLockLatch.countDown();
                }
            }

            @Override
            public void onScanStopped() {}

            @Override
            public void onProgress(int percent) {}

            @Override
            public void onFrequenciesReported(int[] frequency) {}

            @Override
            public void onSymbolRatesReported(int[] rate) {}

            @Override
            public void onPlpIdsReported(int[] plpIds) {}

            @Override
            public void onGroupIdsReported(int[] groupIds) {}

            @Override
            public void onInputStreamIdsReported(int[] inputStreamIds) {}

            @Override
            public void onDvbsStandardReported(int dvbsStandard) {}

            @Override
            public void onDvbtStandardReported(int dvbtStandard) {}

            @Override
            public void onAnalogSifStandardReported(int sif) {}

            @Override
            public void onAtsc3PlpInfosReported(Atsc3PlpInfo[] atsc3PlpInfos) {}

            @Override
            public void onHierarchyReported(int hierarchy) {}

            @Override
            public void onSignalTypeReported(int signalType) {}
        };
    }
}
