/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.net.wifi.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.app.UiAutomation;
import android.content.Context;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiScanner;
import android.net.wifi.WifiScanner.ScanData;
import android.net.wifi.WifiSsid;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerExecutor;
import android.os.HandlerThread;
import android.os.Parcel;
import android.os.SystemClock;
import android.platform.test.annotations.AppModeFull;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "Cannot get WifiManager in instant app mode")
public class WifiScannerTest extends WifiJUnit4TestBase {

    public static String TAG = "WifiScannerTest";
    private static Context sContext;
    private static WifiScanner sWifiScanner;
    private static final long TEST_WAIT_DURATION_MS = 5000;
    private static final int POLL_WAIT_MSEC = 60;
    private static final String TEST_SSID = "TEST_SSID";
    public static final String TEST_BSSID = "04:ac:fe:45:34:10";
    public static final String TEST_CAPS = "CCMP";
    public static final int TEST_LEVEL = -56;
    public static final int TEST_FREQUENCY = 2412;
    public static final long TEST_TIMESTAMP = 4660L;
    private static final int BATCH_SCAN_PERIOD_MILLIS = 10 * 1000;
    private static final int BATCH_SCAN_PERIOD_DELTA_MILLIS = 5 * 1000;
    private static final int BATCH_SCAN_POOL_TIME_MILLIS = 60 * 1000;
    private static final int BATCH_SCAN_EXPONENTIAL_MAX_PERIOD_MILLIS = 50 * 1000;
    private static final int BATCH_SCAN_EXPONENTIAL_POOL_TIME_MILLIS = 180 * 1000;

    private final Object mLock = new Object();
    private boolean mCachedScanDataReturned = false;
    private final HandlerThread mHandlerThread = new HandlerThread("WifiScannerTest");
    protected final Executor mExecutor;
    {
        mHandlerThread.start();
        mExecutor = new HandlerExecutor(new Handler(mHandlerThread.getLooper()));
    }

    @Before
    public void setUp() throws Exception {
        sContext = InstrumentationRegistry.getInstrumentation().getContext();
        sWifiScanner =  sContext.getSystemService(WifiScanner.class);
    }

    @After
    public void tearDown() throws Exception {
    }

    private static WifiScanner.ScanSettings createRequest(WifiScanner.ChannelSpec[] channels,
            int period, int batch, int bssidsPerScan, int reportEvents) {
        WifiScanner.ScanSettings request = new WifiScanner.ScanSettings();
        request.band = WifiScanner.WIFI_BAND_UNSPECIFIED;
        request.channels = channels;
        request.periodInMs = period;
        request.numBssidsPerScan = bssidsPerScan;
        request.maxScansToCache = batch;
        request.reportEvents = reportEvents;
        return request;
    }

    private static WifiScanner.ScanSettings createRequest(int type, int band, int period, int batch,
            int bssidsPerScan, int reportEvents) {
        return createRequest(WifiScanner.SCAN_TYPE_HIGH_ACCURACY, band, period, 0, 0,
                batch, bssidsPerScan, reportEvents);
    }

    private static WifiScanner.ScanSettings createRequest(int band, int period, int batch,
            int bssidsPerScan, int reportEvents) {
        return createRequest(WifiScanner.SCAN_TYPE_HIGH_ACCURACY, band, period, 0, 0, batch,
                bssidsPerScan, reportEvents);
    }

    private static WifiScanner.ScanSettings createRequest(int type, int band, int period,
            int maxPeriod, int stepCount, int batch, int bssidsPerScan, int reportEvents) {
        WifiScanner.ScanSettings request = new WifiScanner.ScanSettings();
        request.type = type;
        request.band = band;
        request.channels = null;
        request.periodInMs = period;
        request.maxPeriodInMs = maxPeriod;
        request.stepCount = stepCount;
        request.numBssidsPerScan = bssidsPerScan;
        request.maxScansToCache = batch;
        request.reportEvents = reportEvents;
        return request;
    }

    /**
     * Verify WifiScanner ScanSettings setVendorIes() and getVendorIes() methods.
     * Test ScanSettings object being serialized and deserialized while vendorIes keeping the
     * values unchanged.
     */
    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public void testVendorIesParcelable() throws Exception {
        WifiScanner.ScanSettings requestSettings = createRequest(
                WifiScanner.WIFI_BAND_BOTH_WITH_DFS, 0,
                0, 20, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN);
        List<ScanResult.InformationElement> vendorIesList = new ArrayList<>();
        ScanResult.InformationElement vendorIe1 = new ScanResult.InformationElement(221, 0,
                new byte[]{0x00, 0x50, (byte) 0xf2, 0x08, 0x11, 0x22, 0x33});
        ScanResult.InformationElement vendorIe2 = new ScanResult.InformationElement(221, 0,
                new byte[]{0x00, 0x50, (byte) 0xf2, 0x08, (byte) 0xaa, (byte) 0xbb, (byte) 0xcc});
        vendorIesList.add(vendorIe1);
        vendorIesList.add(vendorIe2);
        requestSettings.setVendorIes(vendorIesList);
        assertEquals(vendorIesList, requestSettings.getVendorIes());

        Parcel parcel = Parcel.obtain();
        requestSettings.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        assertThat(
                WifiScanner.ScanSettings.CREATOR.createFromParcel(parcel).getVendorIes()).isEqualTo(
                requestSettings.getVendorIes());
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public void testPnoSettings() throws Exception {
        android.net.wifi.nl80211.PnoSettings pnoSettings =
                new android.net.wifi.nl80211.PnoSettings();
        pnoSettings.setScanIterations(3);
        pnoSettings.setScanIntervalMultiplier(4);
        assertEquals(3, pnoSettings.getScanIterations());
        assertEquals(4, pnoSettings.getScanIntervalMultiplier());
    }

    @Test
    public void testParcelableScanData() {
        ScanResult scanResult = new ScanResult();
        scanResult.SSID = TEST_SSID;
        scanResult.setWifiSsid(WifiSsid.fromBytes(TEST_SSID.getBytes(StandardCharsets.UTF_8)));
        scanResult.BSSID = TEST_BSSID;
        scanResult.capabilities = TEST_CAPS;
        scanResult.level = TEST_LEVEL;
        scanResult.frequency = TEST_FREQUENCY;
        scanResult.timestamp = TEST_TIMESTAMP;

        WifiScanner.ScanData scanData = new WifiScanner.ScanData(0, 0,
                new ScanResult[]{scanResult});
        WifiScanner.ParcelableScanData parcelableScanData = new WifiScanner
                .ParcelableScanData(new WifiScanner.ScanData[]{scanData});
        WifiScanner.ScanData[] result = parcelableScanData.getResults();
        assertThat(result.length).isEqualTo(1);
        ScanResult scanResult1 = result[0].getResults()[0];

        assertThat(scanResult1.SSID).isEqualTo(TEST_SSID);
        assertThat(scanResult1.getWifiSsid()).isEqualTo(scanResult.getWifiSsid());
        assertThat(scanResult1.BSSID).isEqualTo(TEST_BSSID);
        assertThat(scanResult1.capabilities).isEqualTo(TEST_CAPS);
        assertThat(scanResult1.level).isEqualTo(TEST_LEVEL);
        assertThat(scanResult1.frequency).isEqualTo(TEST_FREQUENCY);
        assertThat(scanResult1.timestamp).isEqualTo(TEST_TIMESTAMP);
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM)
    public void testGetCachedScanData() throws Exception {
        assumeTrue(WifiFeature.isWifiSupported(sContext));
        mCachedScanDataReturned = false;
        Consumer<ScanData> listener = new Consumer<ScanData>() {
            @Override
            public void accept(ScanData scanData) {
                synchronized (mLock) {
                    mCachedScanDataReturned = true;
                    mLock.notify();
                }
            }
        };
        assertThrows(SecurityException.class,
                () -> sWifiScanner.getCachedScanData(mExecutor, listener));
        // null executor
        assertThrows("null executor should trigger exception", NullPointerException.class,
                () -> sWifiScanner.getCachedScanData(null, listener));
        // null listener
        assertThrows("null listener should trigger exception", NullPointerException.class,
                () -> sWifiScanner.getCachedScanData(mExecutor, null));

        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();

        try {
            uiAutomation.adoptShellPermissionIdentity();
            sWifiScanner.getCachedScanData(mExecutor, listener);
            long timeout = System.currentTimeMillis() + TEST_WAIT_DURATION_MS;
            synchronized (mLock) {
                while (System.currentTimeMillis() < timeout && !mCachedScanDataReturned) {
                    mLock.wait(POLL_WAIT_MSEC);
                }
            }
            assertTrue(mCachedScanDataReturned);
        } catch (UnsupportedOperationException ex) {
            // Expected if the device does not support this API
        } catch (Exception e) {
            fail("getCachedScanData unexpected Exception " + e);
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    /**
     * Implementation of {@link WifiScanner.ScanListener} used for one-shot(none full results) or
     * batch scans.
     */
    public static class WifiScanListener implements WifiScanner.ScanListener {
        private final CountDownLatch mCountDownLatch = new CountDownLatch(1);
        private ScanData[] mScanData;
        public boolean onFailureCalled = false;
        public boolean onSuccessCalled = false;

        @Override
        public void onSuccess() {
            Log.d(TAG, "onSuccess called");
            onSuccessCalled = true;
        }

        @Override
        public void onPeriodChanged(int periodInMs) {} // Ignore period change.

        @Override
        public void onFailure(int reason, String description) {
            Log.d(TAG, "onFailure called: " + reason + ", " + description);
            onFailureCalled = true;
        }

        @Override
        public void onResults(ScanData[] scanData) {
            Log.d(TAG, "onResults called");
            mScanData = scanData;
            mCountDownLatch.countDown();
        }

        @Override
        public void onFullResult(ScanResult fullScanResult) {} // Ignore full scan results.

        /**
         * Wait for scan results to come back. Returns {@code false} if scan results haven't been
         * received after {@code timeoutMillis}.
         */
        public boolean await(int timeoutMillis) {
            try {
                return mCountDownLatch.await(timeoutMillis, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Log.d(TAG, "interrupted in await", e);
                return false;
            }
        }

        /** Resets internal variables */
        public void reset() {
            onSuccessCalled = false;
            onFailureCalled = false;
        }

        public ScanData[] getScanData() {
            return mScanData;
        }
    }

    // Cause the thread to sleep. Don't throw Exceptions.
    private void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Log.d(TAG, "sleep interrupted");
        }
    }

    // Verify scan timestamps in results are within [scanStartMillis, scanEndMillis].
    private void verifyScanTimestamp(long startMicros, long stopMicros, ScanResult[] results) {
        if (results.length == 0) {
            return;
        }
        for (ScanResult result : results) {
            assertTrue(
                    "device observed time "
                            + result.timestamp
                            + " outside of ["
                            + startMicros
                            + ", "
                            + stopMicros
                            + "]",
                    result.timestamp >= startMicros && result.timestamp <= stopMicros);
        }
    }

    /**
     * Test batching scans. Ensure timestamps of each scan are in order and within
     * BATCH_SCAN_INTERVAL_PERIOD_MILLIS of the scan interval.
     */
    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM)
    public void testBatchScanTimestamp() {
        assumeTrue(WifiFeature.isWifiSupported(sContext));
        WifiScanner.ScanSettings requestSettings =
                createRequest(
                        WifiScanner.WIFI_BAND_BOTH_WITH_DFS,
                        BATCH_SCAN_PERIOD_MILLIS,
                        5,
                        20,
                        WifiScanner.REPORT_EVENT_AFTER_BUFFER_FULL);
        WifiScanListener scanListener = new WifiScanListener();

        long batchStartMicros = SystemClock.elapsedRealtime() * 1000;
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            uiAutomation.adoptShellPermissionIdentity();
            sWifiScanner.startBackgroundScan(requestSettings, scanListener);
            long timeout = System.currentTimeMillis() + 10000;
            synchronized (mLock) {
                try {
                    // Wait for either onSuccess or onFailure to get called
                    while (System.currentTimeMillis() < timeout
                            && !scanListener.onFailureCalled
                            && !scanListener.onSuccessCalled) {
                        mLock.wait(POLL_WAIT_MSEC);
                    }
                } catch (InterruptedException e) {
                    return;
                }
            }
            // Batched scan is optional, allow devices to skip the test if they don't support it.
            if (scanListener.onFailureCalled) {
                Log.w(TAG, "Batched scan failed, skipping test");
                return;
            }
            sleep(BATCH_SCAN_POOL_TIME_MILLIS);
            assertTrue(sWifiScanner.getScanResults());
            boolean isScanResultsReceived = scanListener.await(10000);
            if (!isScanResultsReceived) {
                Log.w(
                        TAG,
                        "Batched scan failed - Didn't receive scan results in "
                                + BATCH_SCAN_POOL_TIME_MILLIS
                                + "ms"
                                + " skipping test");
                return;
            }
            sWifiScanner.stopBackgroundScan(scanListener);

            long prevScanMaxTimestamp = 0;
            int i = 0;
            ScanData[] scanDataArray = scanListener.getScanData();
            for (ScanData scanData : scanDataArray) {
                // Verify order of scans.
                long currScanMaxTimestamp = 0;
                for (ScanResult result : scanData.getResults()) {
                    assertTrue(
                            "scan timestamp out of order", result.timestamp > prevScanMaxTimestamp);
                    if (result.timestamp > currScanMaxTimestamp) {
                        currScanMaxTimestamp = result.timestamp;
                    }
                }
                prevScanMaxTimestamp = currScanMaxTimestamp;

                // Verify scans are within delta.
                long scanStartMicros =
                        batchStartMicros + (BATCH_SCAN_PERIOD_MILLIS * 1000 * (long) i);
                long scanEndMicros = scanStartMicros + (BATCH_SCAN_PERIOD_DELTA_MILLIS * 1000);
                verifyScanTimestamp(scanStartMicros, scanEndMicros, scanData.getResults());
                i += 1;
            }
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    /**
     * Ensure timestamps of each batch scan are within BATCH_SCAN_PERIOD_DELTA_MILLIS of the
     * exponential scan period.
     */
    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM)
    public void testBatchScanTimestampExponential() {
        assumeTrue(WifiFeature.isWifiSupported(sContext));
        WifiScanner.ScanSettings requestSettings =
                createRequest(
                        WifiScanner.WIFI_BAND_BOTH_WITH_DFS,
                        BATCH_SCAN_EXPONENTIAL_MAX_PERIOD_MILLIS,
                        5,
                        20,
                        WifiScanner.REPORT_EVENT_AFTER_BUFFER_FULL);
        requestSettings.stepCount = 1;
        WifiScanListener scanListener = new WifiScanListener();

        long batchStartMicros = SystemClock.elapsedRealtime() * 1000;
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            uiAutomation.adoptShellPermissionIdentity();
            sWifiScanner.startBackgroundScan(requestSettings, scanListener);
            long timeout = System.currentTimeMillis() + 10000;
            synchronized (mLock) {
                try {
                    // Wait for either onSuccess or onFailure to get called
                    while (System.currentTimeMillis() < timeout
                            && !scanListener.onFailureCalled
                            && !scanListener.onSuccessCalled) {
                        mLock.wait(POLL_WAIT_MSEC);
                    }
                } catch (InterruptedException e) {
                    return;
                }
            }
            // Batched scan is optional, allow devices to skip the test if they don't support it.
            if (scanListener.onFailureCalled) {
                Log.w(TAG, "Batched scan failed, skipping test");
                return;
            }
            sleep(BATCH_SCAN_EXPONENTIAL_POOL_TIME_MILLIS);
            // Batched scan is optional, allow devices to skip the test if they don't support it.
            if (scanListener.onFailureCalled) {
                Log.w(TAG, "Batched scan failed, skipping test");
                return;
            }
            assertTrue(sWifiScanner.getScanResults());
            boolean isScanResultsReceived = scanListener.await(10000);
            if (!isScanResultsReceived) {
                Log.w(
                        TAG,
                        "Batched scan failed - Didn't receive scan results in "
                                + BATCH_SCAN_POOL_TIME_MILLIS
                                + "ms"
                                + " skipping test");
                return;
            }
            sWifiScanner.stopBackgroundScan(scanListener);

            long scanStartMicros = batchStartMicros;
            long scanPeriodMillis = BATCH_SCAN_PERIOD_MILLIS;
            ScanData[] scanDataArray = scanListener.getScanData();
            for (ScanData scanData : scanDataArray) {
                long scanEndMicros = scanStartMicros + (BATCH_SCAN_PERIOD_DELTA_MILLIS * 1000);
                verifyScanTimestamp(scanStartMicros, scanEndMicros, scanData.getResults());
                scanStartMicros += scanPeriodMillis * 1000;
                scanPeriodMillis *= 2;
                if (scanPeriodMillis > BATCH_SCAN_EXPONENTIAL_MAX_PERIOD_MILLIS) {
                    scanPeriodMillis = BATCH_SCAN_EXPONENTIAL_MAX_PERIOD_MILLIS;
                }
            }
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    /** Ensure results for the correct band are returned when specified. */
    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM)
    public void testBatchScanCorrectBandResults() {
        assumeTrue(WifiFeature.isWifiSupported(sContext));
        WifiScanner.ScanSettings requestSettings =
                createRequest(
                        WifiScanner.WIFI_BAND_24_GHZ,
                        BATCH_SCAN_PERIOD_MILLIS,
                        5,
                        20,
                        WifiScanner.REPORT_EVENT_AFTER_BUFFER_FULL);
        WifiScanListener mScanListener24GHz = new WifiScanListener();
        WifiScanListener mScanListener5GHz = new WifiScanListener();
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            uiAutomation.adoptShellPermissionIdentity();
            sWifiScanner.startBackgroundScan(requestSettings, mScanListener24GHz);
            long timeout = System.currentTimeMillis() + 10000;
            synchronized (mLock) {
                try {
                    // Wait for either onSuccess or onFailure to get called
                    while (System.currentTimeMillis() < timeout
                            && !mScanListener24GHz.onFailureCalled
                            && !mScanListener24GHz.onSuccessCalled) {
                        mLock.wait(POLL_WAIT_MSEC);
                    }
                } catch (InterruptedException e) {
                    return;
                }
            }
            // Batched scan is optional, allow devices to skip the test if they don't support it.
            if (mScanListener24GHz.onFailureCalled) {
                Log.w(TAG, "Batched scan failed, skipping test");
                return;
            }
            sleep(BATCH_SCAN_POOL_TIME_MILLIS);
            assertTrue(sWifiScanner.getScanResults());
            boolean isScanResultsReceived = mScanListener24GHz.await(10000);
            if (!isScanResultsReceived) {
                Log.w(
                        TAG,
                        "2.4GHz Batched scan failed - Didn't receive scan results in "
                                + BATCH_SCAN_POOL_TIME_MILLIS
                                + "ms"
                                + " skipping test");
                return;
            }
            sWifiScanner.stopBackgroundScan(mScanListener24GHz);
            ScanData[] scanDataArray24GHz = mScanListener24GHz.getScanData();

            for (ScanData scanData : scanDataArray24GHz) {
                for (ScanResult result : scanData.getResults()) {
                    assertTrue(
                            "Non 2.4GHz result returned to 2.4GHz listener",
                            result.frequency >= 2400 && result.frequency <= 2500);
                }
            }

            requestSettings.band = WifiScanner.WIFI_BAND_5_GHZ;
            sWifiScanner.startBackgroundScan(requestSettings, mScanListener5GHz);
            timeout = System.currentTimeMillis() + 10000;
            synchronized (mLock) {
                try {
                    // Wait for either onSuccess or onFailure to get called
                    while (System.currentTimeMillis() < timeout
                            && !mScanListener5GHz.onFailureCalled
                            && !mScanListener5GHz.onSuccessCalled) {
                        mLock.wait(POLL_WAIT_MSEC);
                    }
                } catch (InterruptedException e) {
                    return;
                }
            }
            // Batched scan is optional, allow devices to skip the test if they don't support it.
            if (mScanListener5GHz.onFailureCalled) {
                Log.w(TAG, "Batched scan failed, skipping test");
                return;
            }
            sleep(BATCH_SCAN_POOL_TIME_MILLIS);
            assertTrue(sWifiScanner.getScanResults());
            isScanResultsReceived = mScanListener5GHz.await(10000);
            if (!isScanResultsReceived) {
                Log.w(
                        TAG,
                        "5GHz Batched scan failed - Didn't receive scan results in "
                                + BATCH_SCAN_POOL_TIME_MILLIS
                                + "ms"
                                + " skipping test");
                return;
            }
            sWifiScanner.stopBackgroundScan(mScanListener5GHz);

            ScanData[] scanDataArray5GHz = mScanListener5GHz.getScanData();
            for (ScanData scanData : scanDataArray5GHz) {
                for (ScanResult result : scanData.getResults()) {
                    assertTrue(
                            "Non 5GHz result returned to 5GHz listener",
                            result.frequency >= 4900 && result.frequency <= 5900);
                }
            }
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    /**
     * Ensure that the correct band results are returned to a client scanning 2.4GHz and a different
     * client scanning 5GHz.
     */
    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM)
    public void testBatchScanMultipleClientsDifferentBand() {
        assumeTrue(WifiFeature.isWifiSupported(sContext));
        WifiScanner.ScanSettings requestSettings =
                createRequest(
                        WifiScanner.WIFI_BAND_BOTH_WITH_DFS,
                        BATCH_SCAN_PERIOD_MILLIS,
                        5,
                        20,
                        WifiScanner.REPORT_EVENT_AFTER_BUFFER_FULL);
        WifiScanListener mScanListener24GHz = new WifiScanListener();
        WifiScanListener mScanListener5GHz = new WifiScanListener();

        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            uiAutomation.adoptShellPermissionIdentity();
            requestSettings.band = WifiScanner.WIFI_BAND_24_GHZ;
            sWifiScanner.startBackgroundScan(requestSettings, mScanListener24GHz);
            requestSettings.band = WifiScanner.WIFI_BAND_5_GHZ;
            sWifiScanner.startBackgroundScan(requestSettings, mScanListener5GHz);
            long timeout = System.currentTimeMillis() + 10000;
            synchronized (mLock) {
                try {
                    // Wait for either onSuccess or onFailure to get called
                    while (System.currentTimeMillis() < timeout
                            && !mScanListener24GHz.onFailureCalled
                            && !mScanListener24GHz.onSuccessCalled) {
                        mLock.wait(POLL_WAIT_MSEC);
                    }
                } catch (InterruptedException e) {
                    return;
                }
            }
            // Batched scan is optional, allow devices to skip the test if they don't support it.
            // Batched scan is optional, allow devices to skip the test if they don't support it.
            if (mScanListener24GHz.onFailureCalled || mScanListener5GHz.onFailureCalled) {
                Log.w(TAG, "Batched scan failed, skipping test");
                return;
            }
            sleep(BATCH_SCAN_POOL_TIME_MILLIS);
            assertTrue(sWifiScanner.getScanResults());
            boolean isScanResultsReceived = mScanListener24GHz.await(10000);
            if (!isScanResultsReceived) {
                Log.w(
                        TAG,
                        "2.4GHz Batched scan failed - Didn't receive scan results in "
                                + BATCH_SCAN_POOL_TIME_MILLIS
                                + "ms"
                                + " skipping test");
                return;
            }
            sWifiScanner.stopBackgroundScan(mScanListener24GHz);
            isScanResultsReceived = mScanListener5GHz.await(10000);
            if (!isScanResultsReceived) {
                Log.w(
                        TAG,
                        "5GHz Batched scan failed - Didn't receive scan results in "
                                + BATCH_SCAN_POOL_TIME_MILLIS
                                + "ms"
                                + " skipping test");
                return;
            }
            sWifiScanner.stopBackgroundScan(mScanListener5GHz);

            ScanData[] scanDataArray24GHz = mScanListener24GHz.getScanData();
            for (ScanData scanData : scanDataArray24GHz) {
                for (ScanResult result : scanData.getResults()) {
                    assertTrue(
                            "Non 2.4GHz result returned to 2.4GHz listener",
                            result.frequency >= 2400 && result.frequency <= 2500);
                }
            }
            ScanData[] scanDataArray5GHz = mScanListener5GHz.getScanData();
            for (ScanData scanData : scanDataArray5GHz) {
                for (ScanResult result : scanData.getResults()) {
                    assertTrue(
                            "Non 5GHz result returned to 5GHz listener",
                            result.frequency >= 4900 && result.frequency <= 5900);
                }
            }
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }
}
