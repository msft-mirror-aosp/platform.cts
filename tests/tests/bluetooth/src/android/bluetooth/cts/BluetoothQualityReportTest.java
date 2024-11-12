/*
 * Copyright 2023 The Android Open Source Project
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

package android.bluetooth.cts;

import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothQualityReport;
import android.bluetooth.BluetoothQualityReport.BqrCommon;
import android.bluetooth.BluetoothQualityReport.BqrConnectFail;
import android.bluetooth.BluetoothQualityReport.BqrEnergyMonitor;
import android.bluetooth.BluetoothQualityReport.BqrRfStats;
import android.bluetooth.BluetoothQualityReport.BqrVsA2dpChoppy;
import android.bluetooth.BluetoothQualityReport.BqrVsLsto;
import android.bluetooth.BluetoothQualityReport.BqrVsScoChoppy;
import android.os.Parcel;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.bluetooth.flags.Flags;

import com.google.common.truth.Expect;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

@RunWith(AndroidJUnit4.class)
public final class BluetoothQualityReportTest {

    @Rule public final Expect expect = Expect.create();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final String TAG = "BluetoothQualityReportTest";

    private static String mRemoteAddress = "01:02:03:04:05:06";
    private static String mDefaultAddress = "00:00:00:00:00:00";
    private static String mRemoteName = "DeviceName";
    private static String mDefaultName = "";
    private static int mLmpVer = 0;
    private static int mLmpSubVer = 1;
    private static int mManufacturerId = 3;
    private static int mRemoteCoD = 4;

    private void assertBqrCommon(BQRParameters bqrp, BluetoothQualityReport bqr) {
        // BQR Common
        BqrCommon bqrCommon = bqr.getBqrCommon();
        Assert.assertNotNull(bqrCommon);
        Assert.assertEquals(bqr.getQualityReportId(), bqrp.getQualityReportId());
        if ((bqr.getQualityReportId() == BluetoothQualityReport.QUALITY_REPORT_ID_ENERGY_MONITOR)
                || (bqr.getQualityReportId()
                        == BluetoothQualityReport.QUALITY_REPORT_ID_RF_STATS)) {
            return;
        }
        Assert.assertEquals(bqrp.mPacketType, bqrCommon.getPacketType());
        Assert.assertEquals("TYPE_NULL", BqrCommon.packetTypeToString(bqrCommon.getPacketType()));
        Assert.assertEquals(bqrp.mConnectionHandle, bqrCommon.getConnectionHandle());
        Assert.assertTrue(
                bqrp.mConnectionRoleCentral.equals(
                        BqrCommon.connectionRoleToString(bqrCommon.getConnectionRole())));
        Assert.assertEquals(bqrp.mConnectionRole, bqrCommon.getConnectionRole());
        Assert.assertEquals(bqrp.mTxPowerLevel, bqrCommon.getTxPowerLevel());
        Assert.assertEquals(bqrp.mRssi, bqrCommon.getRssi());
        Assert.assertEquals(bqrp.mSnr, bqrCommon.getSnr());
        Assert.assertEquals(bqrp.mUnusedAfhChannelCount, bqrCommon.getUnusedAfhChannelCount());
        Assert.assertEquals(
                bqrp.mAfhSelectUnidealChannelCount, bqrCommon.getAfhSelectUnidealChannelCount());
        Assert.assertEquals(bqrp.mLsto, bqrCommon.getLsto());
        Assert.assertEquals(bqrp.mPiconetClock, bqrCommon.getPiconetClock());
        Assert.assertEquals(bqrp.mRetransmissionCount, bqrCommon.getRetransmissionCount());
        Assert.assertEquals(bqrp.mNoRxCount, bqrCommon.getNoRxCount());
        Assert.assertEquals(bqrp.mNakCount, bqrCommon.getNakCount());
        Assert.assertEquals(bqrp.mLastTxAckTimestamp, bqrCommon.getLastTxAckTimestamp());
        Assert.assertEquals(bqrp.mFlowOffCount, bqrCommon.getFlowOffCount());
        Assert.assertEquals(bqrp.mLastFlowOnTimestamp, bqrCommon.getLastFlowOnTimestamp());
        Assert.assertEquals(bqrp.mOverflowCount, bqrCommon.getOverflowCount());
        Assert.assertEquals(bqrp.mUnderflowCount, bqrCommon.getUnderflowCount());
        Assert.assertEquals(bqrp.mCalFailedItemCount, bqrCommon.getCalFailedItemCount());
    }

    private void assertBqrApproachLsto(BQRParameters bqrp, BluetoothQualityReport bqr) {
        // BQR VS LSTO
        BqrVsLsto bqrVsLsto = (BqrVsLsto) bqr.getBqrEvent();
        Assert.assertNotNull(bqrVsLsto);
        Assert.assertEquals(
                "Approaching LSTO",
                BluetoothQualityReport.qualityReportIdToString(bqr.getQualityReportId()));
        Assert.assertEquals(bqrp.mConnState & 0xFF, bqrVsLsto.getConnState());
        Assert.assertEquals(
                "CONN_UNPARK_ACTIVE", BqrVsLsto.connStateToString(bqrVsLsto.getConnState()));
        Assert.assertEquals(bqrp.mBasebandStats, bqrVsLsto.getBasebandStats());
        Assert.assertEquals(bqrp.mSlotsUsed, bqrVsLsto.getSlotsUsed());
        Assert.assertEquals(bqrp.mCxmDenials, bqrVsLsto.getCxmDenials());
        Assert.assertEquals(bqrp.mTxSkipped, bqrVsLsto.getTxSkipped());
        Assert.assertEquals(bqrp.mRfLoss, bqrVsLsto.getRfLoss());
        Assert.assertEquals(bqrp.mNativeClock, bqrVsLsto.getNativeClock());
        Assert.assertEquals(bqrp.mLastTxAckTimestampLsto, bqrVsLsto.getLastTxAckTimestamp());
        Assert.assertEquals(0, bqrVsLsto.describeContents());
    }

    private void assertBqrA2dpChoppy(BQRParameters bqrp, BluetoothQualityReport bqr) {
        // BQR VS A2DP Choppy
        BqrVsA2dpChoppy bqrVsA2dpChoppy = (BqrVsA2dpChoppy) bqr.getBqrEvent();
        Assert.assertNotNull(bqrVsA2dpChoppy);
        Assert.assertEquals(
                "A2DP choppy",
                BluetoothQualityReport.qualityReportIdToString(bqr.getQualityReportId()));
        Assert.assertEquals(bqrp.mArrivalTime, bqrVsA2dpChoppy.getArrivalTime());
        Assert.assertEquals(bqrp.mScheduleTime, bqrVsA2dpChoppy.getScheduleTime());
        Assert.assertEquals(bqrp.mGlitchCountA2dp, bqrVsA2dpChoppy.getGlitchCount());
        Assert.assertEquals(bqrp.mTxCxmDenialsA2dp, bqrVsA2dpChoppy.getTxCxmDenials());
        Assert.assertEquals(bqrp.mRxCxmDenialsA2dp, bqrVsA2dpChoppy.getRxCxmDenials());
        Assert.assertEquals(bqrp.mAclTxQueueLength, bqrVsA2dpChoppy.getAclTxQueueLength());
        Assert.assertEquals(bqrp.mLinkQuality, bqrVsA2dpChoppy.getLinkQuality());
        Assert.assertEquals(
                "MEDIUM", BqrVsA2dpChoppy.linkQualityToString(bqrVsA2dpChoppy.getLinkQuality()));
        Assert.assertEquals(0, bqrVsA2dpChoppy.describeContents());
    }

    private void assertBqrScoChoppy(BQRParameters bqrp, BluetoothQualityReport bqr) {
        // BQR VS SCO Choppy
        BqrVsScoChoppy bqrVsScoChoppy = (BqrVsScoChoppy) bqr.getBqrEvent();
        Assert.assertNotNull(bqrVsScoChoppy);
        Assert.assertEquals(
                "SCO choppy",
                BluetoothQualityReport.qualityReportIdToString(bqr.getQualityReportId()));
        Assert.assertEquals(bqrp.mGlitchCountSco, bqrVsScoChoppy.getGlitchCount());
        Assert.assertEquals(bqrp.mIntervalEsco, bqrVsScoChoppy.getIntervalEsco());
        Assert.assertEquals(bqrp.mWindowEsco, bqrVsScoChoppy.getWindowEsco());
        Assert.assertEquals(bqrp.mAirFormat, bqrVsScoChoppy.getAirFormat());
        Assert.assertEquals(
                "CVSD", BqrVsScoChoppy.airFormatToString(bqrVsScoChoppy.getAirFormat()));
        Assert.assertEquals(bqrp.mInstanceCount, bqrVsScoChoppy.getInstanceCount());
        Assert.assertEquals(bqrp.mTxCxmDenialsSco, bqrVsScoChoppy.getTxCxmDenials());
        Assert.assertEquals(bqrp.mRxCxmDenialsSco, bqrVsScoChoppy.getRxCxmDenials());
        Assert.assertEquals(bqrp.mTxAbortCount, bqrVsScoChoppy.getTxAbortCount());
        Assert.assertEquals(bqrp.mLateDispatch, bqrVsScoChoppy.getLateDispatch());
        Assert.assertEquals(bqrp.mMicIntrMiss, bqrVsScoChoppy.getMicIntrMiss());
        Assert.assertEquals(bqrp.mLpaIntrMiss, bqrVsScoChoppy.getLpaIntrMiss());
        Assert.assertEquals(bqrp.mSprIntrMiss, bqrVsScoChoppy.getSprIntrMiss());
        Assert.assertEquals(bqrp.mPlcFillCount, bqrVsScoChoppy.getPlcFillCount());
        Assert.assertEquals(bqrp.mPlcDiscardCount, bqrVsScoChoppy.getPlcDiscardCount());
        Assert.assertEquals(bqrp.mMissedInstanceCount, bqrVsScoChoppy.getMissedInstanceCount());
        Assert.assertEquals(bqrp.mTxRetransmitSlotCount, bqrVsScoChoppy.getTxRetransmitSlotCount());
        Assert.assertEquals(bqrp.mRxRetransmitSlotCount, bqrVsScoChoppy.getRxRetransmitSlotCount());
        Assert.assertEquals(bqrp.mGoodRxFrameCount, bqrVsScoChoppy.getGoodRxFrameCount());
        Assert.assertEquals(0, bqrVsScoChoppy.describeContents());
    }

    private void assertBqrEnergyMonitor(BQRParameters bqrp, BluetoothQualityReport bqr) {
        BqrEnergyMonitor bqrEnergyMonitor = (BqrEnergyMonitor) bqr.getBqrEvent();
        expect.that(bqrEnergyMonitor).isNotNull();
        expect.that(BluetoothQualityReport.qualityReportIdToString(bqr.getQualityReportId()))
                .isEqualTo("Energy Monitor");
        expect.that(bqrp.mAvgCurrentConsume)
                .isEqualTo(bqrEnergyMonitor.getAverageCurrentConsumptionMicroamps());
        expect.that(bqrp.mIdleTotalTime).isEqualTo(bqrEnergyMonitor.getIdleStateTotalTimeMillis());
        expect.that(bqrp.mIdleStateEnterCount).isEqualTo(bqrEnergyMonitor.getIdleStateEnterCount());
        expect.that(bqrp.mActiveTotalTime)
                .isEqualTo(bqrEnergyMonitor.getActiveStateTotalTimeMillis());
        expect.that(bqrp.mActiveStateEnterCount)
                .isEqualTo(bqrEnergyMonitor.getActiveStateEnterCount());
        expect.that(bqrp.mBredrTxTotalTime).isEqualTo(bqrEnergyMonitor.getBredrTxTotalTimeMillis());
        expect.that(bqrp.mBredrTxStateEnterCount)
                .isEqualTo(bqrEnergyMonitor.getBredrTxStateEnterCount());
        expect.that(bqrp.mBredrTxAvgPowerLevel)
                .isEqualTo(bqrEnergyMonitor.getBredrAverageTxPowerLeveldBm());
        expect.that(bqrp.mBredrRxTotalTime).isEqualTo(bqrEnergyMonitor.getBredrRxTotalTimeMillis());
        expect.that(bqrp.mBredrRxStateEnterCount)
                .isEqualTo(bqrEnergyMonitor.getBredrRxStateEnterCount());
        expect.that(bqrp.mLeTxTotalTime).isEqualTo(bqrEnergyMonitor.getLeTsTotalTimeMillis());
        expect.that(bqrp.mLeTxStateEnterCount).isEqualTo(bqrEnergyMonitor.getLeTxStateEnterCount());
        expect.that(bqrp.mLeTxAvgPowerLevel)
                .isEqualTo(bqrEnergyMonitor.getLeAverageTxPowerLeveldBm());
        expect.that(bqrp.mLeRxTotalTime).isEqualTo(bqrEnergyMonitor.getLeRxTotalTimeMillis());
        expect.that(bqrp.mLeRxStateEnterCount).isEqualTo(bqrEnergyMonitor.getLeRxStateEnterCount());
        expect.that(bqrp.mReportTotalTime)
                .isEqualTo(bqrEnergyMonitor.getPowerDataTotalTimeMillis());
        expect.that(bqrp.mRxActiveOneChainTime)
                .isEqualTo(bqrEnergyMonitor.getRxSingleChainActiveDurationMillis());
        expect.that(bqrp.mRxActiveTwoChainTime)
                .isEqualTo(bqrEnergyMonitor.getRxDualChainActiveDurationMillis());
        expect.that(bqrp.mTxiPaActiveOneChainTime)
                .isEqualTo(bqrEnergyMonitor.getTxInternalPaSingleChainActiveDurationMillis());
        expect.that(bqrp.mTxiPaActiveTwoChainTime)
                .isEqualTo(bqrEnergyMonitor.getTxInternalPaDualChainActiveDurationMillis());
        expect.that(bqrp.mTxePaActiveOneChainTime)
                .isEqualTo(bqrEnergyMonitor.getTxExternalPaSingleChainActiveDurationMillis());
        expect.that(bqrp.mTxePaActiveTwoChainTime)
                .isEqualTo(bqrEnergyMonitor.getTxExternalPaDualChainActiveDurationMillis());
        expect.that(bqrEnergyMonitor.describeContents()).isEqualTo(0);
    }

    private void assertBqrConnectFail(BQRParameters bqrp, BluetoothQualityReport bqr) {
        // BQR VS Connect Fail
        BqrConnectFail bqrConnectFail = (BqrConnectFail) bqr.getBqrEvent();
        Assert.assertNotNull(bqrConnectFail);
        Assert.assertEquals(
                "Connect fail",
                BluetoothQualityReport.qualityReportIdToString(bqr.getQualityReportId()));
        Assert.assertEquals(bqrp.mFailReason, bqrConnectFail.getFailReason());
        Assert.assertEquals(0, bqrConnectFail.describeContents());
    }

    private void assertBqrRfStats(BQRParameters bqrp, BluetoothQualityReport bqr) {
        BqrRfStats bqrRfStats = (BqrRfStats) bqr.getBqrEvent();
        expect.that(bqrRfStats).isNotNull();
        expect.that(BluetoothQualityReport.qualityReportIdToString(bqr.getQualityReportId()))
                .isEqualTo("RF Stats");
        expect.that(bqrp.mExtensionInfo).isEqualTo(bqrRfStats.getExtensionInfo());
        expect.that(bqrp.mReportTimePeriod).isEqualTo(bqrRfStats.getPerformanceDurationMillis());
        expect.that(bqrp.mTxPoweriPaBf)
                .isEqualTo(bqrRfStats.getTxPowerInternalPaBeamformingCount());
        expect.that(bqrp.mTxPowerePaBf)
                .isEqualTo(bqrRfStats.getTxPowerExternalPaBeamformingCount());
        expect.that(bqrp.mTxPoweriPaDiv).isEqualTo(bqrRfStats.getTxPowerInternalPaDiversityCount());
        expect.that(bqrp.mTxPowerePaDiv).isEqualTo(bqrRfStats.getTxPowerExternalPaDiversityCount());
        expect.that(bqrp.mRssiChainOver50)
                .isEqualTo(bqrRfStats.getPacketsWithRssiAboveMinus50dBm());
        expect.that(bqrp.mRssiChain50To55).isEqualTo(bqrRfStats.getPacketsWithRssi50To55dBm());
        expect.that(bqrp.mRssiChain55To60).isEqualTo(bqrRfStats.getPacketsWithRssi55To60dBm());
        expect.that(bqrp.mRssiChain60To65).isEqualTo(bqrRfStats.getPacketsWithRssi60To65dBm());
        expect.that(bqrp.mRssiChain65To70).isEqualTo(bqrRfStats.getPacketsWithRssi65To70dBm());
        expect.that(bqrp.mRssiChain70To75).isEqualTo(bqrRfStats.getPacketsWithRssi70To75dBm());
        expect.that(bqrp.mRssiChain75To80).isEqualTo(bqrRfStats.getPacketsWithRssi75To80dBm());
        expect.that(bqrp.mRssiChain80To85).isEqualTo(bqrRfStats.getPacketsWithRssi80To85dBm());
        expect.that(bqrp.mRssiChain85To90).isEqualTo(bqrRfStats.getPacketsWithRssi85To90dBm());
        expect.that(bqrp.mRssiChainUnder90)
                .isEqualTo(bqrRfStats.getPacketsWithRssiBelowMinus90dBm());
        expect.that(bqrp.mRssiDeltaUnder2).isEqualTo(bqrRfStats.getPacketsWithRssiDeltaBelow2dBm());
        expect.that(bqrp.mRssiDelta2To5).isEqualTo(bqrRfStats.getPacketsWithRssiDelta2To5dBm());
        expect.that(bqrp.mRssiDelta5To8).isEqualTo(bqrRfStats.getPacketsWithRssiDelta5To8dBm());
        expect.that(bqrp.mRssiDelta8To11).isEqualTo(bqrRfStats.getPacketsWithRssiDelta8To11dBm());
        expect.that(bqrp.mRssiDeltaOver11)
                .isEqualTo(bqrRfStats.getPacketsWithRssiDeltaAbove11dBm());
        expect.that(bqrRfStats.describeContents()).isEqualTo(0);
    }

    private static BluetoothClass getBluetoothClassHelper(int remoteCoD) {
        Parcel p = Parcel.obtain();
        p.writeInt(remoteCoD);
        p.setDataPosition(0);
        BluetoothClass bluetoothClass = BluetoothClass.CREATOR.createFromParcel(p);
        p.recycle();
        return bluetoothClass;
    }

    private BluetoothQualityReport initBqrCommon(
            BQRParameters bqrp,
            String remoteAddr,
            int lmpVer,
            int lmpSubVer,
            int manufacturerId,
            String remoteName,
            int remoteCoD) {

        BluetoothClass bluetoothClass = getBluetoothClassHelper(remoteCoD);

        BluetoothQualityReport bqr =
                new BluetoothQualityReport.Builder(bqrp.getByteArray())
                        .setRemoteAddress(remoteAddr)
                        .setLmpVersion(lmpVer)
                        .setLmpSubVersion(lmpSubVer)
                        .setManufacturerId(manufacturerId)
                        .setRemoteName(remoteName)
                        .setBluetoothClass(bluetoothClass)
                        .build();

        Log.i(TAG, bqr.toString());

        Assert.assertTrue(remoteAddr.equals(bqr.getRemoteAddress()));
        Assert.assertEquals(lmpVer, bqr.getLmpVersion());
        Assert.assertEquals(lmpSubVer, bqr.getLmpSubVersion());
        Assert.assertEquals(manufacturerId, bqr.getManufacturerId());
        Assert.assertTrue(remoteName.equals(bqr.getRemoteName()));
        Assert.assertEquals(bluetoothClass, bqr.getBluetoothClass());

        assertBqrCommon(bqrp, bqr);

        return bqr;
    }

    @Test
    public void bqrMonitor() {
        BQRParameters bqrp = BQRParameters.getInstance();
        Assert.assertNotNull(bqrp);

        bqrp.setQualityReportId((byte) BluetoothQualityReport.QUALITY_REPORT_ID_MONITOR);
        Assert.assertEquals(
                bqrp.getQualityReportId(), BluetoothQualityReport.QUALITY_REPORT_ID_MONITOR);

        BluetoothQualityReport bqr =
                initBqrCommon(
                        bqrp,
                        mRemoteAddress,
                        mLmpVer,
                        mLmpSubVer,
                        mManufacturerId,
                        mRemoteName,
                        mRemoteCoD);
    }

    @Test
    public void bqrApproachLsto() {
        BQRParameters bqrp = BQRParameters.getInstance();
        Assert.assertNotNull(bqrp);

        bqrp.setQualityReportId((byte) BluetoothQualityReport.QUALITY_REPORT_ID_APPROACH_LSTO);
        Assert.assertEquals(
                bqrp.getQualityReportId(), BluetoothQualityReport.QUALITY_REPORT_ID_APPROACH_LSTO);

        BluetoothQualityReport bqr =
                initBqrCommon(
                        bqrp,
                        mRemoteAddress,
                        mLmpVer,
                        mLmpSubVer,
                        mManufacturerId,
                        mRemoteName,
                        mRemoteCoD);

        assertBqrApproachLsto(bqrp, bqr);
    }

    @Test
    public void bqrA2dpChoppy() {
        BQRParameters bqrp = BQRParameters.getInstance();
        Assert.assertNotNull(bqrp);

        bqrp.setQualityReportId((byte) BluetoothQualityReport.QUALITY_REPORT_ID_A2DP_CHOPPY);
        Assert.assertEquals(
                bqrp.getQualityReportId(), BluetoothQualityReport.QUALITY_REPORT_ID_A2DP_CHOPPY);

        BluetoothQualityReport bqr =
                initBqrCommon(
                        bqrp,
                        mRemoteAddress,
                        mLmpVer,
                        mLmpSubVer,
                        mManufacturerId,
                        mRemoteName,
                        mRemoteCoD);

        assertBqrA2dpChoppy(bqrp, bqr);
    }

    @Test
    public void bqrScoChoppy() {
        BQRParameters bqrp = BQRParameters.getInstance();
        Assert.assertNotNull(bqrp);

        bqrp.setQualityReportId((byte) BluetoothQualityReport.QUALITY_REPORT_ID_SCO_CHOPPY);
        Assert.assertEquals(
                bqrp.getQualityReportId(), BluetoothQualityReport.QUALITY_REPORT_ID_SCO_CHOPPY);

        BluetoothQualityReport bqr =
                initBqrCommon(
                        bqrp,
                        mRemoteAddress,
                        mLmpVer,
                        mLmpSubVer,
                        mManufacturerId,
                        mRemoteName,
                        mRemoteCoD);

        assertBqrScoChoppy(bqrp, bqr);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_SUPPORT_BLUETOOTH_QUALITY_REPORT_V6)
    public void bqrEnergyMonitor() {
        BQRParameters bqrp = BQRParameters.getInstance();
        Assert.assertNotNull(bqrp);

        bqrp.setQualityReportId((byte) BluetoothQualityReport.QUALITY_REPORT_ID_ENERGY_MONITOR);
        Assert.assertEquals(
                bqrp.getQualityReportId(), BluetoothQualityReport.QUALITY_REPORT_ID_ENERGY_MONITOR);

        BluetoothQualityReport bqr =
                initBqrCommon(
                        bqrp,
                        mRemoteAddress,
                        mLmpVer,
                        mLmpSubVer,
                        mManufacturerId,
                        mRemoteName,
                        mRemoteCoD);

        assertBqrEnergyMonitor(bqrp, bqr);
    }

    @Test
    public void bqrConnectFail() {
        BQRParameters bqrp = BQRParameters.getInstance();
        Assert.assertNotNull(bqrp);

        bqrp.setQualityReportId((byte) BluetoothQualityReport.QUALITY_REPORT_ID_CONN_FAIL);
        Assert.assertEquals(
                bqrp.getQualityReportId(), BluetoothQualityReport.QUALITY_REPORT_ID_CONN_FAIL);

        BluetoothQualityReport bqr =
                initBqrCommon(
                        bqrp,
                        mRemoteAddress,
                        mLmpVer,
                        mLmpSubVer,
                        mManufacturerId,
                        mRemoteName,
                        mRemoteCoD);

        assertBqrConnectFail(bqrp, bqr);

        BqrConnectFail bqrConnectFail = (BqrConnectFail) bqr.getBqrEvent();
        Assert.assertNotNull(bqrConnectFail);

        Assert.assertEquals(
                "No error",
                BqrConnectFail.connectFailIdToString(BqrConnectFail.CONNECT_FAIL_ID_NO_ERROR));
        Assert.assertEquals(
                "Page Timeout",
                BqrConnectFail.connectFailIdToString(BqrConnectFail.CONNECT_FAIL_ID_PAGE_TIMEOUT));
        Assert.assertEquals(
                "Connection Timeout",
                BqrConnectFail.connectFailIdToString(
                        BqrConnectFail.CONNECT_FAIL_ID_CONNECTION_TIMEOUT));
        Assert.assertEquals(
                "ACL already exists",
                BqrConnectFail.connectFailIdToString(
                        BqrConnectFail.CONNECT_FAIL_ID_ACL_ALREADY_EXIST));
        Assert.assertEquals(
                "Controller busy",
                BqrConnectFail.connectFailIdToString(
                        BqrConnectFail.CONNECT_FAIL_ID_CONTROLLER_BUSY));
        Assert.assertEquals("INVALID", BqrConnectFail.connectFailIdToString(0xFF));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_SUPPORT_BLUETOOTH_QUALITY_REPORT_V6)
    public void bqrRfStats() {
        BQRParameters bqrp = BQRParameters.getInstance();
        Assert.assertNotNull(bqrp);

        bqrp.setQualityReportId((byte) BluetoothQualityReport.QUALITY_REPORT_ID_RF_STATS);
        Assert.assertEquals(
                bqrp.getQualityReportId(), BluetoothQualityReport.QUALITY_REPORT_ID_RF_STATS);

        BluetoothQualityReport bqr =
                initBqrCommon(
                        bqrp,
                        mRemoteAddress,
                        mLmpVer,
                        mLmpSubVer,
                        mManufacturerId,
                        mRemoteName,
                        mRemoteCoD);

        assertBqrRfStats(bqrp, bqr);
    }

    @Test
    public void defaultNameAddress() {
        BQRParameters bqrp = BQRParameters.getInstance();
        Assert.assertNotNull(bqrp);

        bqrp.setQualityReportId((byte) BluetoothQualityReport.QUALITY_REPORT_ID_MONITOR);
        Assert.assertEquals(
                bqrp.getQualityReportId(), BluetoothQualityReport.QUALITY_REPORT_ID_MONITOR);

        BluetoothClass bluetoothClass = getBluetoothClassHelper(mRemoteCoD);

        BluetoothQualityReport bqr =
                new BluetoothQualityReport.Builder(bqrp.getByteArray())
                        .setRemoteAddress("123456123456")
                        .setLmpVersion(mLmpVer)
                        .setLmpSubVersion(mLmpSubVer)
                        .setManufacturerId(mManufacturerId)
                        .setBluetoothClass(bluetoothClass)
                        .build();

        Assert.assertTrue(bqr.getRemoteAddress().equals(mDefaultAddress));
        Assert.assertTrue(bqr.getRemoteName().equals(mDefaultName));
    }

    @Test
    public void invalidQualityReportId() {
        BQRParameters bqrp = BQRParameters.getInstance();
        Assert.assertNotNull(bqrp);

        bqrp.setQualityReportId((byte) 123);
        Assert.assertEquals(bqrp.getQualityReportId(), 123);

        Assert.assertThrows(
                IllegalArgumentException.class,
                () ->
                        initBqrCommon(
                                bqrp,
                                mRemoteAddress,
                                mLmpVer,
                                mLmpSubVer,
                                mManufacturerId,
                                mRemoteName,
                                mRemoteCoD));
    }

    @Test
    public void rawDataNull() {
        BluetoothClass bluetoothClass = getBluetoothClassHelper(mRemoteCoD);

        Assert.assertThrows(
                NullPointerException.class,
                () ->
                        new BluetoothQualityReport.Builder(null)
                                .setRemoteAddress(mRemoteAddress)
                                .setLmpVersion(mLmpVer)
                                .setLmpSubVersion(mLmpSubVer)
                                .setManufacturerId(mManufacturerId)
                                .setRemoteName(mRemoteName)
                                .setBluetoothClass(bluetoothClass)
                                .build());
    }

    @Test
    public void invalidRawData() {
        BQRParameters bqrp = BQRParameters.getInstance();
        Assert.assertNotNull(bqrp);

        BluetoothClass bluetoothClass = getBluetoothClassHelper(mRemoteCoD);

        for (int id : BQRParameters.QualityReportId) {
            bqrp.setQualityReportId((byte) id);
            Assert.assertEquals(bqrp.getQualityReportId(), id);

            byte[] rawData = {0};

            switch (id) {
                case BluetoothQualityReport.QUALITY_REPORT_ID_MONITOR:
                    rawData = ByteBuffer.allocate(BQRParameters.mBqrCommonSize - 1).array();
                    break;
                case BluetoothQualityReport.QUALITY_REPORT_ID_APPROACH_LSTO:
                    rawData =
                            ByteBuffer.allocate(
                                            BQRParameters.mBqrCommonSize
                                                    + BQRParameters.mBqrVsLstoSize
                                                    - 1)
                                    .array();
                    break;
                case BluetoothQualityReport.QUALITY_REPORT_ID_A2DP_CHOPPY:
                    rawData =
                            ByteBuffer.allocate(
                                            BQRParameters.mBqrCommonSize
                                                    + BQRParameters.mBqrVsA2dpChoppySize
                                                    - 1)
                                    .array();
                    break;
                case BluetoothQualityReport.QUALITY_REPORT_ID_SCO_CHOPPY:
                    rawData =
                            ByteBuffer.allocate(
                                            BQRParameters.mBqrCommonSize
                                                    + BQRParameters.mBqrVsScoChoppySize
                                                    - 1)
                                    .array();
                    break;
                case BluetoothQualityReport.QUALITY_REPORT_ID_CONN_FAIL:
                    rawData =
                            ByteBuffer.allocate(
                                            BQRParameters.mBqrCommonSize
                                                    + BQRParameters.mBqrVsScoChoppySize
                                                    - 1)
                                    .array();
                    break;
            }

            final byte[] data = rawData;

            Assert.assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            new BluetoothQualityReport.Builder(data)
                                    .setRemoteAddress(mRemoteAddress)
                                    .setLmpVersion(mLmpVer)
                                    .setLmpSubVersion(mLmpSubVer)
                                    .setManufacturerId(mManufacturerId)
                                    .setRemoteName(mRemoteName)
                                    .setBluetoothClass(bluetoothClass)
                                    .build());
        }
    }

    @Test
    public void readWriteBqrParcel() {
        BQRParameters bqrp = BQRParameters.getInstance();
        Assert.assertNotNull(bqrp);

        for (int id : BQRParameters.QualityReportId) {
            if ((id == BluetoothQualityReport.QUALITY_REPORT_ID_ENERGY_MONITOR)
                    || (id == BluetoothQualityReport.QUALITY_REPORT_ID_RF_STATS)) {
                continue;
            }
            bqrp.setQualityReportId((byte) id);
            Assert.assertEquals(bqrp.getQualityReportId(), id);

            BluetoothQualityReport bqr =
                    initBqrCommon(
                            bqrp,
                            mRemoteAddress,
                            mLmpVer,
                            mLmpSubVer,
                            mManufacturerId,
                            mRemoteName,
                            mRemoteCoD);

            Parcel parcel = Parcel.obtain();
            bqr.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);

            BluetoothQualityReport bqrFromParcel =
                    BluetoothQualityReport.CREATOR.createFromParcel(parcel);

            assertBqrCommon(bqrp, bqrFromParcel);

            switch (id) {
                case BluetoothQualityReport.QUALITY_REPORT_ID_MONITOR:
                    break;
                case BluetoothQualityReport.QUALITY_REPORT_ID_APPROACH_LSTO:
                    assertBqrApproachLsto(bqrp, bqr);
                    break;
                case BluetoothQualityReport.QUALITY_REPORT_ID_A2DP_CHOPPY:
                    assertBqrA2dpChoppy(bqrp, bqr);
                    break;
                case BluetoothQualityReport.QUALITY_REPORT_ID_SCO_CHOPPY:
                    assertBqrScoChoppy(bqrp, bqr);
                    break;
                case BluetoothQualityReport.QUALITY_REPORT_ID_CONN_FAIL:
                    assertBqrConnectFail(bqrp, bqr);
                    break;
            }
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_SUPPORT_BLUETOOTH_QUALITY_REPORT_V6)
    public void readWriteBqrParcelV6Flag() {
        BQRParameters bqrp = BQRParameters.getInstance();
        Assert.assertNotNull(bqrp);

        for (int id : BQRParameters.QualityReportId) {
            bqrp.setQualityReportId((byte) id);
            Assert.assertEquals(bqrp.getQualityReportId(), id);

            BluetoothQualityReport bqr =
                    initBqrCommon(
                            bqrp,
                            mRemoteAddress,
                            mLmpVer,
                            mLmpSubVer,
                            mManufacturerId,
                            mRemoteName,
                            mRemoteCoD);

            Parcel parcel = Parcel.obtain();
            bqr.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);

            BluetoothQualityReport bqrFromParcel =
                    BluetoothQualityReport.CREATOR.createFromParcel(parcel);

            assertBqrCommon(bqrp, bqrFromParcel);

            switch (id) {
                case BluetoothQualityReport.QUALITY_REPORT_ID_MONITOR:
                    break;
                case BluetoothQualityReport.QUALITY_REPORT_ID_APPROACH_LSTO:
                    assertBqrApproachLsto(bqrp, bqr);
                    break;
                case BluetoothQualityReport.QUALITY_REPORT_ID_A2DP_CHOPPY:
                    assertBqrA2dpChoppy(bqrp, bqr);
                    break;
                case BluetoothQualityReport.QUALITY_REPORT_ID_SCO_CHOPPY:
                    assertBqrScoChoppy(bqrp, bqr);
                    break;
                case BluetoothQualityReport.QUALITY_REPORT_ID_CONN_FAIL:
                    assertBqrConnectFail(bqrp, bqr);
                    break;
                case BluetoothQualityReport.QUALITY_REPORT_ID_ENERGY_MONITOR:
                    assertBqrEnergyMonitor(bqrp, bqr);
                    break;
                case BluetoothQualityReport.QUALITY_REPORT_ID_RF_STATS:
                    assertBqrRfStats(bqrp, bqr);
                    break;
            }
        }
    }

    @Test
    public void readWriteBqrCommonParcel() {
        BQRParameters bqrp = BQRParameters.getInstance();
        Assert.assertNotNull(bqrp);

        bqrp.setQualityReportId((byte) BluetoothQualityReport.QUALITY_REPORT_ID_MONITOR);
        Assert.assertEquals(
                bqrp.getQualityReportId(), BluetoothQualityReport.QUALITY_REPORT_ID_MONITOR);

        BluetoothQualityReport bqr =
                initBqrCommon(
                        bqrp,
                        mRemoteAddress,
                        mLmpVer,
                        mLmpSubVer,
                        mManufacturerId,
                        mRemoteName,
                        mRemoteCoD);

        Assert.assertEquals(
                "Quality monitor",
                BluetoothQualityReport.qualityReportIdToString(bqr.getQualityReportId()));

        Parcel parcel = Parcel.obtain();
        bqr.getBqrCommon().writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        BqrCommon bqrCommonFromParcel = BqrCommon.CREATOR.createFromParcel(parcel);

        // BQR Common
        Assert.assertNotNull(bqrCommonFromParcel);
        Assert.assertEquals(bqrp.mPacketType, bqrCommonFromParcel.getPacketType());
        Assert.assertEquals(
                "TYPE_NULL", BqrCommon.packetTypeToString(bqrCommonFromParcel.getPacketType()));
        Assert.assertEquals(bqrp.mConnectionHandle, bqrCommonFromParcel.getConnectionHandle());
        Assert.assertTrue(
                bqrp.mConnectionRoleCentral.equals(
                        BqrCommon.connectionRoleToString(bqrCommonFromParcel.getConnectionRole())));
        Assert.assertEquals(bqrp.mTxPowerLevel, bqrCommonFromParcel.getTxPowerLevel());
        Assert.assertEquals(bqrp.mRssi, bqrCommonFromParcel.getRssi());
        Assert.assertEquals(bqrp.mSnr, bqrCommonFromParcel.getSnr());
        Assert.assertEquals(
                bqrp.mUnusedAfhChannelCount, bqrCommonFromParcel.getUnusedAfhChannelCount());
        Assert.assertEquals(
                bqrp.mAfhSelectUnidealChannelCount,
                bqrCommonFromParcel.getAfhSelectUnidealChannelCount());
        Assert.assertEquals(bqrp.mLsto, bqrCommonFromParcel.getLsto());
        Assert.assertEquals(bqrp.mPiconetClock, bqrCommonFromParcel.getPiconetClock());
        Assert.assertEquals(
                bqrp.mRetransmissionCount, bqrCommonFromParcel.getRetransmissionCount());
        Assert.assertEquals(bqrp.mNoRxCount, bqrCommonFromParcel.getNoRxCount());
        Assert.assertEquals(bqrp.mNakCount, bqrCommonFromParcel.getNakCount());
        Assert.assertEquals(bqrp.mLastTxAckTimestamp, bqrCommonFromParcel.getLastTxAckTimestamp());
        Assert.assertEquals(bqrp.mFlowOffCount, bqrCommonFromParcel.getFlowOffCount());
        Assert.assertEquals(
                bqrp.mLastFlowOnTimestamp, bqrCommonFromParcel.getLastFlowOnTimestamp());
        Assert.assertEquals(bqrp.mOverflowCount, bqrCommonFromParcel.getOverflowCount());
        Assert.assertEquals(bqrp.mUnderflowCount, bqrCommonFromParcel.getUnderflowCount());
    }

    @Test
    public void readWriteBqrVsApproachLstoParcel() {
        BQRParameters bqrp = BQRParameters.getInstance();
        Assert.assertNotNull(bqrp);

        bqrp.setQualityReportId((byte) BluetoothQualityReport.QUALITY_REPORT_ID_APPROACH_LSTO);
        Assert.assertEquals(
                bqrp.getQualityReportId(), BluetoothQualityReport.QUALITY_REPORT_ID_APPROACH_LSTO);

        BluetoothQualityReport bqr =
                initBqrCommon(
                        bqrp,
                        mRemoteAddress,
                        mLmpVer,
                        mLmpSubVer,
                        mManufacturerId,
                        mRemoteName,
                        mRemoteCoD);

        Assert.assertEquals(
                "Approaching LSTO",
                BluetoothQualityReport.qualityReportIdToString(bqr.getQualityReportId()));

        BqrVsLsto bqrVsLsto = (BqrVsLsto) bqr.getBqrEvent();
        Assert.assertNotNull(bqrVsLsto);
        Parcel parcel = Parcel.obtain();
        bqrVsLsto.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        BqrVsLsto bqrVsLstoFromParcel = BqrVsLsto.CREATOR.createFromParcel(parcel);

        // BQR VS LSTO
        Assert.assertNotNull(bqrVsLstoFromParcel);
        Assert.assertEquals(bqrp.mConnState & 0xFF, bqrVsLstoFromParcel.getConnState());
        Assert.assertEquals(
                "CONN_UNPARK_ACTIVE",
                BqrVsLsto.connStateToString(bqrVsLstoFromParcel.getConnState()));
        Assert.assertEquals(bqrp.mBasebandStats, bqrVsLstoFromParcel.getBasebandStats());
        Assert.assertEquals(bqrp.mSlotsUsed, bqrVsLstoFromParcel.getSlotsUsed());
        Assert.assertEquals(bqrp.mCxmDenials, bqrVsLstoFromParcel.getCxmDenials());
        Assert.assertEquals(bqrp.mTxSkipped, bqrVsLstoFromParcel.getTxSkipped());
        Assert.assertEquals(bqrp.mRfLoss, bqrVsLstoFromParcel.getRfLoss());
        Assert.assertEquals(bqrp.mNativeClock, bqrVsLstoFromParcel.getNativeClock());
        Assert.assertEquals(
                bqrp.mLastTxAckTimestampLsto, bqrVsLstoFromParcel.getLastTxAckTimestamp());
        Assert.assertEquals(0, bqrVsLstoFromParcel.describeContents());
    }

    @Test
    public void readWriteBqrVsA2dpChoppyParcel() {
        BQRParameters bqrp = BQRParameters.getInstance();
        Assert.assertNotNull(bqrp);

        bqrp.setQualityReportId((byte) BluetoothQualityReport.QUALITY_REPORT_ID_A2DP_CHOPPY);
        Assert.assertEquals(
                bqrp.getQualityReportId(), BluetoothQualityReport.QUALITY_REPORT_ID_A2DP_CHOPPY);

        BluetoothQualityReport bqr =
                initBqrCommon(
                        bqrp,
                        mRemoteAddress,
                        mLmpVer,
                        mLmpSubVer,
                        mManufacturerId,
                        mRemoteName,
                        mRemoteCoD);

        Assert.assertEquals(
                "A2DP choppy",
                BluetoothQualityReport.qualityReportIdToString(bqr.getQualityReportId()));

        BqrVsA2dpChoppy bqrVsA2dpChoppy = (BqrVsA2dpChoppy) bqr.getBqrEvent();
        Assert.assertNotNull(bqrVsA2dpChoppy);
        Parcel parcel = Parcel.obtain();
        bqrVsA2dpChoppy.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        BqrVsA2dpChoppy bqrVsA2dpChoppyFromParcel =
                BqrVsA2dpChoppy.CREATOR.createFromParcel(parcel);

        // BQR VS A2DP Choppy
        Assert.assertNotNull(bqrVsA2dpChoppyFromParcel);
        Assert.assertEquals(bqrp.mArrivalTime, bqrVsA2dpChoppyFromParcel.getArrivalTime());
        Assert.assertEquals(bqrp.mScheduleTime, bqrVsA2dpChoppyFromParcel.getScheduleTime());
        Assert.assertEquals(bqrp.mGlitchCountA2dp, bqrVsA2dpChoppyFromParcel.getGlitchCount());
        Assert.assertEquals(bqrp.mTxCxmDenialsA2dp, bqrVsA2dpChoppyFromParcel.getTxCxmDenials());
        Assert.assertEquals(bqrp.mRxCxmDenialsA2dp, bqrVsA2dpChoppyFromParcel.getRxCxmDenials());
        Assert.assertEquals(
                bqrp.mAclTxQueueLength, bqrVsA2dpChoppyFromParcel.getAclTxQueueLength());
        Assert.assertEquals(bqrp.mLinkQuality, bqrVsA2dpChoppyFromParcel.getLinkQuality());
        Assert.assertEquals(
                "MEDIUM",
                BqrVsA2dpChoppy.linkQualityToString(bqrVsA2dpChoppyFromParcel.getLinkQuality()));
        Assert.assertEquals(0, bqrVsA2dpChoppyFromParcel.describeContents());
    }

    @Test
    public void readWriteBqrVsScoChoppyParcel() {
        BQRParameters bqrp = BQRParameters.getInstance();
        Assert.assertNotNull(bqrp);

        bqrp.setQualityReportId((byte) BluetoothQualityReport.QUALITY_REPORT_ID_SCO_CHOPPY);
        Assert.assertEquals(
                bqrp.getQualityReportId(), BluetoothQualityReport.QUALITY_REPORT_ID_SCO_CHOPPY);

        BluetoothQualityReport bqr =
                initBqrCommon(
                        bqrp,
                        mRemoteAddress,
                        mLmpVer,
                        mLmpSubVer,
                        mManufacturerId,
                        mRemoteName,
                        mRemoteCoD);

        Assert.assertEquals(
                "SCO choppy",
                BluetoothQualityReport.qualityReportIdToString(bqr.getQualityReportId()));

        BqrVsScoChoppy bqrVsScoChoppy = (BqrVsScoChoppy) bqr.getBqrEvent();
        Assert.assertNotNull(bqrVsScoChoppy);
        Parcel parcel = Parcel.obtain();
        bqrVsScoChoppy.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        BqrVsScoChoppy bqrVsScoChoppyFromParcel = BqrVsScoChoppy.CREATOR.createFromParcel(parcel);

        // BQR VS SCO Choppy
        Assert.assertNotNull(bqrVsScoChoppyFromParcel);
        Assert.assertEquals(bqrp.mGlitchCountSco, bqrVsScoChoppyFromParcel.getGlitchCount());
        Assert.assertEquals(bqrp.mIntervalEsco, bqrVsScoChoppyFromParcel.getIntervalEsco());
        Assert.assertEquals(bqrp.mWindowEsco, bqrVsScoChoppyFromParcel.getWindowEsco());
        Assert.assertEquals(bqrp.mAirFormat, bqrVsScoChoppyFromParcel.getAirFormat());
        Assert.assertEquals(
                "CVSD", BqrVsScoChoppy.airFormatToString(bqrVsScoChoppyFromParcel.getAirFormat()));
        Assert.assertEquals(bqrp.mInstanceCount, bqrVsScoChoppyFromParcel.getInstanceCount());
        Assert.assertEquals(bqrp.mTxCxmDenialsSco, bqrVsScoChoppyFromParcel.getTxCxmDenials());
        Assert.assertEquals(bqrp.mRxCxmDenialsSco, bqrVsScoChoppyFromParcel.getRxCxmDenials());
        Assert.assertEquals(bqrp.mTxAbortCount, bqrVsScoChoppyFromParcel.getTxAbortCount());
        Assert.assertEquals(bqrp.mLateDispatch, bqrVsScoChoppyFromParcel.getLateDispatch());
        Assert.assertEquals(bqrp.mMicIntrMiss, bqrVsScoChoppyFromParcel.getMicIntrMiss());
        Assert.assertEquals(bqrp.mLpaIntrMiss, bqrVsScoChoppyFromParcel.getLpaIntrMiss());
        Assert.assertEquals(bqrp.mSprIntrMiss, bqrVsScoChoppyFromParcel.getSprIntrMiss());
        Assert.assertEquals(bqrp.mPlcFillCount, bqrVsScoChoppyFromParcel.getPlcFillCount());
        Assert.assertEquals(bqrp.mPlcDiscardCount, bqrVsScoChoppyFromParcel.getPlcDiscardCount());

        Assert.assertEquals(
                bqrp.mMissedInstanceCount, bqrVsScoChoppyFromParcel.getMissedInstanceCount());
        Assert.assertEquals(
                bqrp.mTxRetransmitSlotCount, bqrVsScoChoppyFromParcel.getTxRetransmitSlotCount());
        Assert.assertEquals(
                bqrp.mRxRetransmitSlotCount, bqrVsScoChoppyFromParcel.getRxRetransmitSlotCount());
        Assert.assertEquals(bqrp.mGoodRxFrameCount, bqrVsScoChoppyFromParcel.getGoodRxFrameCount());
        Assert.assertEquals(0, bqrVsScoChoppyFromParcel.describeContents());
    }

    @Test
    public void readWriteBqrConnectFailParcel() {
        BQRParameters bqrp = BQRParameters.getInstance();
        Assert.assertNotNull(bqrp);

        bqrp.setQualityReportId((byte) BluetoothQualityReport.QUALITY_REPORT_ID_CONN_FAIL);
        Assert.assertEquals(
                bqrp.getQualityReportId(), BluetoothQualityReport.QUALITY_REPORT_ID_CONN_FAIL);

        BluetoothQualityReport bqr =
                initBqrCommon(
                        bqrp,
                        mRemoteAddress,
                        mLmpVer,
                        mLmpSubVer,
                        mManufacturerId,
                        mRemoteName,
                        mRemoteCoD);

        Assert.assertEquals(
                "Connect fail",
                BluetoothQualityReport.qualityReportIdToString(bqr.getQualityReportId()));

        BqrConnectFail bqrConnectFail = (BqrConnectFail) bqr.getBqrEvent();
        Assert.assertNotNull(bqrConnectFail);
        Parcel parcel = Parcel.obtain();
        bqrConnectFail.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        BqrConnectFail bqrConnFailFromParcel = BqrConnectFail.CREATOR.createFromParcel(parcel);

        // BQR VS Connect Fail
        Assert.assertNotNull(bqrConnFailFromParcel);
        Assert.assertEquals(bqrp.mFailReason, bqrConnFailFromParcel.getFailReason());
        Assert.assertEquals(0, bqrConnFailFromParcel.describeContents());
    }

    /**
     * Get the test object of BluetoothQualityReport based on given Quality Report Id.
     *
     * @param qualityReportId Quality Report Id
     * @return Bluetooth Quality Report object
     */
    public static BluetoothQualityReport getBqr(int qualityReportId) {
        BQRParameters bqrp = BQRParameters.getInstance();
        Assert.assertNotNull(bqrp);

        bqrp.setQualityReportId((byte) qualityReportId);
        Assert.assertEquals(bqrp.getQualityReportId(), qualityReportId);
        BluetoothClass bluetoothClass = getBluetoothClassHelper(mRemoteCoD);

        BluetoothQualityReport bqr =
                new BluetoothQualityReport.Builder(bqrp.getByteArray())
                        .setRemoteAddress(mRemoteAddress)
                        .setLmpVersion(mLmpVer)
                        .setLmpSubVersion(mLmpSubVer)
                        .setManufacturerId(mManufacturerId)
                        .setRemoteName(mRemoteName)
                        .setBluetoothClass(bluetoothClass)
                        .build();
        return bqr;
    }

    private static final class BQRParameters {
        private static BQRParameters INSTANCE;
        private static String TAG = "BQRParameters";

        public static int mBqrCommonSize = 85;
        public static int mBqrVsLstoSize = 23;
        public static int mBqrVsA2dpChoppySize = 16;
        public static int mBqrVsScoChoppySize = 33;
        public static int mBqrConnectFailSize = 1;
        public static int mBqrEnergyMonitorSize = 81;
        public static int mBqrRfStatsSize = 82;

        // BQR Common
        public byte mQualityReportId = 1;
        public byte mPacketType = 2;
        public short mConnectionHandle = 3;
        public byte mConnectionRole = 0; // Central
        public String mConnectionRoleCentral = "Central";
        public byte mTxPowerLevel = 5;
        public byte mRssi = 6;
        public byte mSnr = 7;
        public byte mUnusedAfhChannelCount = 8;
        public byte mAfhSelectUnidealChannelCount = 9;
        public short mLsto = 10;
        public int mPiconetClock = 11;
        public int mRetransmissionCount = 12;
        public int mNoRxCount = 13;
        public int mNakCount = 14;
        public int mLastTxAckTimestamp = 15;
        public int mFlowOffCount = 16;
        public int mLastFlowOnTimestamp = 17;
        public int mOverflowCount = 18;
        public int mUnderflowCount = 19;
        public String mAddressStr = "01:02:03:04:05:06";
        public byte[] mAddress = {6, 5, 4, 3, 2, 1};
        public byte mCalFailedItemCount = 50;

        public int mTxTotalPackets = 20;
        public int mTxUnackPackets = 21;
        public int mTxFlushPackets = 22;
        public int mTxLastSubeventPackets = 23;
        public int mCrcErrorPackets = 24;
        public int mRxDupPackets = 25;
        public int mRxUnRecvPackets = 26;
        public short mCoexInfoMask = 1;

        // BQR VS LSTO
        public byte mConnState = (byte) 0x89;
        public int mBasebandStats = 21;
        public int mSlotsUsed = 22;
        public short mCxmDenials = 23;
        public short mTxSkipped = 24;
        public short mRfLoss = 25;
        public int mNativeClock = 26;
        public int mLastTxAckTimestampLsto = 27;

        // BQR VS A2DP Choppy
        public int mArrivalTime = 28;
        public int mScheduleTime = 29;
        public short mGlitchCountA2dp = 30;
        public short mTxCxmDenialsA2dp = 31;
        public short mRxCxmDenialsA2dp = 32;
        public byte mAclTxQueueLength = 33;
        public byte mLinkQuality = 3;

        // BQR VS SCO Choppy
        public short mGlitchCountSco = 35;
        public byte mIntervalEsco = 36;
        public byte mWindowEsco = 37;
        public byte mAirFormat = 2;
        public short mInstanceCount = 39;
        public short mTxCxmDenialsSco = 40;
        public short mRxCxmDenialsSco = 41;
        public short mTxAbortCount = 42;
        public short mLateDispatch = 43;
        public short mMicIntrMiss = 44;
        public short mLpaIntrMiss = 45;
        public short mSprIntrMiss = 46;
        public short mPlcFillCount = 47;
        public short mPlcDiscardCount = 51;
        public short mMissedInstanceCount = 52;
        public short mTxRetransmitSlotCount = 53;
        public short mRxRetransmitSlotCount = 54;
        public short mGoodRxFrameCount = 55;

        // BQR VS Connect Fail
        public byte mFailReason = 0x3a;

        // BQR Energy Monitor
        public short mAvgCurrentConsume = 56;
        public int mIdleTotalTime = 57;
        public int mIdleStateEnterCount = 58;
        public int mActiveTotalTime = 59;
        public int mActiveStateEnterCount = 60;
        public int mBredrTxTotalTime = 61;
        public int mBredrTxStateEnterCount = 62;
        public byte mBredrTxAvgPowerLevel = 63;
        public int mBredrRxTotalTime = 64;
        public int mBredrRxStateEnterCount = 65;
        public int mLeTxTotalTime = 66;
        public int mLeTxStateEnterCount = 67;
        public byte mLeTxAvgPowerLevel = 68;
        public int mLeRxTotalTime = 69;
        public int mLeRxStateEnterCount = 70;
        public int mReportTotalTime = 71;
        public int mRxActiveOneChainTime = 72;
        public int mRxActiveTwoChainTime = 73;
        public int mTxiPaActiveOneChainTime = 74;
        public int mTxiPaActiveTwoChainTime = 75;
        public int mTxePaActiveOneChainTime = 76;
        public int mTxePaActiveTwoChainTime = 77;

        // BQR Rf Stats
        public byte mExtensionInfo = 78;
        public int mReportTimePeriod = 79;
        public int mTxPoweriPaBf = 80;
        public int mTxPowerePaBf = 81;
        public int mTxPoweriPaDiv = 82;
        public int mTxPowerePaDiv = 83;
        public int mRssiChainOver50 = 84;
        public int mRssiChain50To55 = 85;
        public int mRssiChain55To60 = 86;
        public int mRssiChain60To65 = 87;
        public int mRssiChain65To70 = 88;
        public int mRssiChain70To75 = 89;
        public int mRssiChain75To80 = 90;
        public int mRssiChain80To85 = 91;
        public int mRssiChain85To90 = 92;
        public int mRssiChainUnder90 = 93;
        public int mRssiDeltaUnder2 = 94;
        public int mRssiDelta2To5 = 95;
        public int mRssiDelta5To8 = 96;
        public int mRssiDelta8To11 = 97;
        public int mRssiDeltaOver11 = 98;

        public static int[] QualityReportId = {
            BluetoothQualityReport.QUALITY_REPORT_ID_MONITOR,
            BluetoothQualityReport.QUALITY_REPORT_ID_APPROACH_LSTO,
            BluetoothQualityReport.QUALITY_REPORT_ID_A2DP_CHOPPY,
            BluetoothQualityReport.QUALITY_REPORT_ID_SCO_CHOPPY,
            BluetoothQualityReport.QUALITY_REPORT_ID_CONN_FAIL,
            BluetoothQualityReport.QUALITY_REPORT_ID_ENERGY_MONITOR,
            BluetoothQualityReport.QUALITY_REPORT_ID_RF_STATS,
        };

        private BQRParameters() {}

        public static BQRParameters getInstance() {
            if (INSTANCE == null) {
                INSTANCE = new BQRParameters();
            }
            return INSTANCE;
        }

        public void setQualityReportId(byte id) {
            mQualityReportId = id;
        }

        public int getQualityReportId() {
            return (int) mQualityReportId;
        }

        public byte[] getByteArray() {
            ByteBuffer ba;
            ByteBuffer addrBuff = ByteBuffer.wrap(mAddress, 0, mAddress.length);

            switch ((int) mQualityReportId) {
                case BluetoothQualityReport.QUALITY_REPORT_ID_MONITOR:
                    ba = ByteBuffer.allocate(mBqrCommonSize);
                    break;
                case BluetoothQualityReport.QUALITY_REPORT_ID_APPROACH_LSTO:
                    ba = ByteBuffer.allocate(mBqrCommonSize + mBqrVsLstoSize);
                    break;
                case BluetoothQualityReport.QUALITY_REPORT_ID_A2DP_CHOPPY:
                    ba = ByteBuffer.allocate(mBqrCommonSize + mBqrVsA2dpChoppySize);
                    break;
                case BluetoothQualityReport.QUALITY_REPORT_ID_SCO_CHOPPY:
                    ba = ByteBuffer.allocate(mBqrCommonSize + mBqrVsScoChoppySize);
                    break;
                case BluetoothQualityReport.QUALITY_REPORT_ID_CONN_FAIL:
                    ba = ByteBuffer.allocate(mBqrCommonSize + mBqrConnectFailSize);
                    break;
                case BluetoothQualityReport.QUALITY_REPORT_ID_ENERGY_MONITOR:
                    ba = ByteBuffer.allocate(1 + mBqrEnergyMonitorSize);
                    break;
                case BluetoothQualityReport.QUALITY_REPORT_ID_RF_STATS:
                    ba = ByteBuffer.allocate(1 + mBqrRfStatsSize);
                    break;
                default:
                    ba = ByteBuffer.allocate(mBqrCommonSize);
                    break;
            }

            ba.order(ByteOrder.LITTLE_ENDIAN);

            ba.put(mQualityReportId);

            if (mQualityReportId
                    == (byte) BluetoothQualityReport.QUALITY_REPORT_ID_ENERGY_MONITOR) {
                ba.putShort(mAvgCurrentConsume);
                ba.putInt(mIdleTotalTime);
                ba.putInt(mIdleStateEnterCount);
                ba.putInt(mActiveTotalTime);
                ba.putInt(mActiveStateEnterCount);
                ba.putInt(mBredrTxTotalTime);
                ba.putInt(mBredrTxStateEnterCount);
                ba.put(mBredrTxAvgPowerLevel);
                ba.putInt(mBredrRxTotalTime);
                ba.putInt(mBredrRxStateEnterCount);
                ba.putInt(mLeTxTotalTime);
                ba.putInt(mLeTxStateEnterCount);
                ba.put(mLeTxAvgPowerLevel);
                ba.putInt(mLeRxTotalTime);
                ba.putInt(mLeRxStateEnterCount);
                ba.putInt(mReportTotalTime);
                ba.putInt(mRxActiveOneChainTime);
                ba.putInt(mRxActiveTwoChainTime);
                ba.putInt(mTxiPaActiveOneChainTime);
                ba.putInt(mTxiPaActiveTwoChainTime);
                ba.putInt(mTxePaActiveOneChainTime);
                ba.putInt(mTxePaActiveTwoChainTime);
            } else if (mQualityReportId
                    == (byte) BluetoothQualityReport.QUALITY_REPORT_ID_RF_STATS) {
                ba.put(mExtensionInfo);
                ba.putInt(mReportTimePeriod);
                ba.putInt(mTxPoweriPaBf);
                ba.putInt(mTxPowerePaBf);
                ba.putInt(mTxPoweriPaDiv);
                ba.putInt(mTxPowerePaDiv);
                ba.putInt(mRssiChainOver50);
                ba.putInt(mRssiChain50To55);
                ba.putInt(mRssiChain55To60);
                ba.putInt(mRssiChain60To65);
                ba.putInt(mRssiChain65To70);
                ba.putInt(mRssiChain70To75);
                ba.putInt(mRssiChain75To80);
                ba.putInt(mRssiChain80To85);
                ba.putInt(mRssiChain85To90);
                ba.putInt(mRssiChainUnder90);
                ba.putInt(mRssiDeltaUnder2);
                ba.putInt(mRssiDelta2To5);
                ba.putInt(mRssiDelta5To8);
                ba.putInt(mRssiDelta8To11);
                ba.putInt(mRssiDeltaOver11);
            } else {
                ba.put(mPacketType);
                ba.putShort(mConnectionHandle);
                ba.put(mConnectionRole);
                ba.put(mTxPowerLevel);
                ba.put(mRssi);
                ba.put(mSnr);
                ba.put(mUnusedAfhChannelCount);
                ba.put(mAfhSelectUnidealChannelCount);
                ba.putShort(mLsto);
                ba.putInt(mPiconetClock);
                ba.putInt(mRetransmissionCount);
                ba.putInt(mNoRxCount);
                ba.putInt(mNakCount);
                ba.putInt(mLastTxAckTimestamp);
                ba.putInt(mFlowOffCount);
                ba.putInt(mLastFlowOnTimestamp);
                ba.putInt(mOverflowCount);
                ba.putInt(mUnderflowCount);
                ba.put(addrBuff);
                ba.put(mCalFailedItemCount);
                ba.putInt(mTxTotalPackets);
                ba.putInt(mTxUnackPackets);
                ba.putInt(mTxFlushPackets);
                ba.putInt(mTxLastSubeventPackets);
                ba.putInt(mCrcErrorPackets);
                ba.putInt(mRxDupPackets);
                ba.putInt(mRxUnRecvPackets);
                ba.putShort(mCoexInfoMask);

                if (mQualityReportId
                        == (byte) BluetoothQualityReport.QUALITY_REPORT_ID_APPROACH_LSTO) {
                    ba.put(mConnState);
                    ba.putInt(mBasebandStats);
                    ba.putInt(mSlotsUsed);
                    ba.putShort(mCxmDenials);
                    ba.putShort(mTxSkipped);
                    ba.putShort(mRfLoss);
                    ba.putInt(mNativeClock);
                    ba.putInt(mLastTxAckTimestampLsto);
                } else if (mQualityReportId
                        == (byte) BluetoothQualityReport.QUALITY_REPORT_ID_A2DP_CHOPPY) {
                    ba.putInt(mArrivalTime);
                    ba.putInt(mScheduleTime);
                    ba.putShort(mGlitchCountA2dp);
                    ba.putShort(mTxCxmDenialsA2dp);
                    ba.putShort(mRxCxmDenialsA2dp);
                    ba.put(mAclTxQueueLength);
                    ba.put(mLinkQuality);
                } else if (mQualityReportId
                        == (byte) BluetoothQualityReport.QUALITY_REPORT_ID_SCO_CHOPPY) {
                    ba.putShort(mGlitchCountSco);
                    ba.put(mIntervalEsco);
                    ba.put(mWindowEsco);
                    ba.put(mAirFormat);
                    ba.putShort(mInstanceCount);
                    ba.putShort(mTxCxmDenialsSco);
                    ba.putShort(mRxCxmDenialsSco);
                    ba.putShort(mTxAbortCount);
                    ba.putShort(mLateDispatch);
                    ba.putShort(mMicIntrMiss);
                    ba.putShort(mLpaIntrMiss);
                    ba.putShort(mSprIntrMiss);
                    ba.putShort(mPlcFillCount);
                    ba.putShort(mPlcDiscardCount);
                    ba.putShort(mMissedInstanceCount);
                    ba.putShort(mTxRetransmitSlotCount);
                    ba.putShort(mRxRetransmitSlotCount);
                    ba.putShort(mGoodRxFrameCount);

                } else if (mQualityReportId
                        == (byte) BluetoothQualityReport.QUALITY_REPORT_ID_CONN_FAIL) {
                    ba.put(mFailReason);
                }
            }
            return ba.array();
        }
    }
}
