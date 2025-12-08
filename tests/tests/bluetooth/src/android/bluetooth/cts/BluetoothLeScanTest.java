/*
 * Copyright (C) 2015 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import android.app.Instrumentation;
import android.app.PendingIntent;
import android.app.UiAutomation;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.bluetooth.test_utils.BlockingBluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.os.ParcelUuid;
import android.os.SystemClock;
import android.util.Log;
import android.util.SparseArray;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.CddTest;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Test cases for Bluetooth LE scans.
 *
 * <p>To run the test, the device must be placed in an environment that has at least 3 beacons, all
 * placed less than 5 meters away from the DUT.
 *
 * <p>Run 'run cts --class android.bluetooth.cts.BluetoothLeScanTest' in cts-tradefed to run the
 * test cases.
 */
@RunWith(AndroidJUnit4.class)
public class BluetoothLeScanTest {
    private static final String TAG = BluetoothLeScanTest.class.getSimpleName();

    private static final int SCAN_DURATION_MILLIS = 10000;
    private static final int BATCH_SCAN_REPORT_DELAY_MILLIS = 20000;
    private static final int SCAN_STOP_TIMEOUT = 2000;

    private final BluetoothAdapter mAdapter = BlockingBluetoothAdapter.getAdapter();
    private final Instrumentation mInstrumentation = InstrumentationRegistry.getInstrumentation();
    private final Context mContext = mInstrumentation.getContext();
    private final UiAutomation mUIAutomation = mInstrumentation.getUiAutomation();

    private CountDownLatch mFlushBatchScanLatch;
    private BluetoothLeScanner mScanner;
    // Whether location is on before running the tests.
    private boolean mLocationOn;

    @Before
    public void setUp() {
        Assume.assumeTrue(TestUtils.isBleSupported(mContext));

        assertThat(BlockingBluetoothAdapter.enable()).isTrue();

        mScanner = mAdapter.getBluetoothLeScanner();

        mLocationOn = TestUtils.isLocationOn(mContext);
        if (!mLocationOn) {
            TestUtils.enableLocation(mContext);
        }

        mUIAutomation.grantRuntimePermission(
                "android.bluetooth.cts", android.Manifest.permission.ACCESS_FINE_LOCATION);
    }

    @After
    public void tearDown() {
        if (!mLocationOn) {
            TestUtils.disableLocation(mContext);
        }
        mUIAutomation.dropShellPermissionIdentity();
    }

    /** Basic test case for BLE scans. Checks BLE scan timestamp is within correct range. */
    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @MediumTest
    @Test
    public void basicBleScan() {
        mUIAutomation.adoptShellPermissionIdentity(android.Manifest.permission.BLUETOOTH_SCAN);

        long scanStartMillis = SystemClock.elapsedRealtime();
        Collection<ScanResult> scanResults = scan();
        long scanEndMillis = SystemClock.elapsedRealtime();
        Log.d(TAG, "scan result size:" + scanResults.size());

        assertThat(scanResults).isNotEmpty();
        verifyTimestamp(scanResults, scanStartMillis, scanEndMillis);
    }

    /**
     * Test of scan filters. Ensures only beacons matching certain type of scan filters were
     * reported.
     */
    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @MediumTest
    @Test
    public void scanFilter() {
        mUIAutomation.adoptShellPermissionIdentity(android.Manifest.permission.BLUETOOTH_SCAN);

        var filters = scanToCreateFiltersForTopStrongestNearbyBeacons();
        if (filters.isEmpty()) {
            Log.d(TAG, "No appropriate filter can be set");
            return;
        }

        var filterLeScanCallback = new BleScanCallback();
        var settings = createScanSettings(ScanSettings.SCAN_MODE_LOW_LATENCY);
        mScanner.startScan(filters, settings, filterLeScanCallback);
        TestUtils.sleep(SCAN_DURATION_MILLIS);

        mScanner.stopScan(filterLeScanCallback);
        TestUtils.sleep(SCAN_STOP_TIMEOUT);

        Collection<ScanResult> scanResults = filterLeScanCallback.getScanResults();
        for (ScanResult result : scanResults) {
            boolean matchesAnyFilter = false;
            for (ScanFilter filter : filters) {
                if (filter.matches(result)) {
                    matchesAnyFilter = true;
                    break;
                }
            }
            assertThat(matchesAnyFilter).isTrue();
        }
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @MediumTest
    @Test
    public void scanFromSourceWithoutFilters() {
        mUIAutomation.adoptShellPermissionIdentity(
                android.Manifest.permission.BLUETOOTH_PRIVILEGED,
                android.Manifest.permission.BLUETOOTH_SCAN,
                android.Manifest.permission.UPDATE_DEVICE_STATS);

        var filterLeScanCallback = new BleScanCallback();
        mScanner.startScanFromSource(null, filterLeScanCallback);
        TestUtils.sleep(SCAN_DURATION_MILLIS);

        mScanner.stopScan(filterLeScanCallback);
        TestUtils.sleep(SCAN_STOP_TIMEOUT);
        assertThat(filterLeScanCallback.getScanResults()).isNotEmpty();
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @MediumTest
    @Test
    public void scanFromSourceWithFilters() {
        mUIAutomation.adoptShellPermissionIdentity(
                android.Manifest.permission.BLUETOOTH_PRIVILEGED,
                android.Manifest.permission.BLUETOOTH_SCAN,
                android.Manifest.permission.UPDATE_DEVICE_STATS);

        var filterLeScanCallback = new BleScanCallback();
        var settings = createScanSettings(ScanSettings.SCAN_MODE_LOW_LATENCY);
        mScanner.startScanFromSource(null, settings, null, filterLeScanCallback);
        TestUtils.sleep(SCAN_DURATION_MILLIS);

        mScanner.stopScan(filterLeScanCallback);
        TestUtils.sleep(SCAN_STOP_TIMEOUT);

        assertThat(filterLeScanCallback.getScanResults()).isNotEmpty();
    }

    /** Test of opportunistic BLE scans. */
    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @MediumTest
    @Test
    @Ignore("b/70865144 - Test fails because it obtains results from GmsCore explicit scan.")
    public void opportunisticScan() {
        mUIAutomation.adoptShellPermissionIdentity(android.Manifest.permission.BLUETOOTH_SCAN);

        var opportunisticScanSettings = createScanSettings(ScanSettings.SCAN_MODE_OPPORTUNISTIC);
        var emptyScanCallback = new BleScanCallback();
        assertThat(emptyScanCallback.getScanResults()).isEmpty();

        // No scans are really started with opportunistic scans only.
        mScanner.startScan(Collections.emptyList(), opportunisticScanSettings, emptyScanCallback);
        TestUtils.sleep(SCAN_DURATION_MILLIS);
        assertThat(emptyScanCallback.getScanResults()).isEmpty();

        var regularScanCallback = new BleScanCallback();
        var regularScanSettings = createScanSettings(ScanSettings.SCAN_MODE_LOW_LATENCY);
        var filters = scanToCreateFiltersForTopStrongestNearbyBeacons();
        if (filters.isEmpty()) {
            Log.d(TAG, "No appropriate filter can be set");
        }

        mScanner.startScan(filters, regularScanSettings, regularScanCallback);
        TestUtils.sleep(SCAN_DURATION_MILLIS);

        // With normal BLE scan client, opportunistic scan client will get scan results.
        assertThat(emptyScanCallback.getScanResults()).isNotEmpty();

        // No more scan results for opportunistic scan clients once the normal BLE scan clients
        // stops.
        mScanner.stopScan(regularScanCallback);
        // In case we got scan results before scan was completely stopped.
        TestUtils.sleep(SCAN_STOP_TIMEOUT);

        emptyScanCallback.clear();
        TestUtils.sleep(SCAN_DURATION_MILLIS);

        assertThat(emptyScanCallback.getScanResults()).isEmpty();
    }

    /** Test case for BLE Batch scan. */
    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @MediumTest
    @Test
    public void batchScan() {
        Assume.assumeTrue(isBleBatchScanSupported());
        mUIAutomation.adoptShellPermissionIdentity(android.Manifest.permission.BLUETOOTH_SCAN);

        var batchScanSettings =
                createScanSettings(
                        ScanSettings.SCAN_MODE_LOW_LATENCY, BATCH_SCAN_REPORT_DELAY_MILLIS);
        var batchScanCallback = new BleScanCallback();
        mScanner.startScan(Collections.emptyList(), batchScanSettings, batchScanCallback);
        TestUtils.sleep(SCAN_DURATION_MILLIS);

        mScanner.flushPendingScanResults(batchScanCallback);
        mFlushBatchScanLatch = new CountDownLatch(1);
        Collection<ScanResult> results = batchScanCallback.getBatchScanResults();
        try {
            mFlushBatchScanLatch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            // Nothing to do.
            Log.e(TAG, "interrupted!");
        }
        assertThat(results).isNotEmpty();

        long scanEndMillis = SystemClock.elapsedRealtime();
        mScanner.stopScan(batchScanCallback);
        verifyTimestamp(results, 0, scanEndMillis);
    }

    /** Test case for starting a scan with a PendingIntent. */
    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @MediumTest
    @Test
    public void startScanPendingIntent_nullnull() throws Exception {
        Assume.assumeTrue(isBleBatchScanSupported());
        mUIAutomation.adoptShellPermissionIdentity(android.Manifest.permission.BLUETOOTH_SCAN);

        Intent broadcastIntent = new Intent();
        broadcastIntent.setClass(mContext, BluetoothScanReceiver.class);
        PendingIntent pi =
                PendingIntent.getBroadcast(
                        mContext, 1, broadcastIntent, PendingIntent.FLAG_IMMUTABLE);
        CountDownLatch latch = BluetoothScanReceiver.createCountDownLatch();
        mScanner.startScan(null, null, pi);
        boolean gotResults = latch.await(20, TimeUnit.SECONDS);

        mScanner.stopScan(pi);
        assertThat(gotResults).isTrue();
    }

    /** Test case for starting a scan with a PendingIntent. */
    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @MediumTest
    @Test
    public void startScanPendingIntent() throws Exception {
        Assume.assumeTrue(isBleBatchScanSupported());
        mUIAutomation.adoptShellPermissionIdentity(android.Manifest.permission.BLUETOOTH_SCAN);

        var batchScanSettings = createScanSettings(ScanSettings.SCAN_MODE_LOW_LATENCY, 0);
        var filters = scanToCreateFiltersForTopStrongestNearbyBeacons();
        if (filters.isEmpty()) {
            Log.d(TAG, "No appropriate filter can be set");
        }

        Intent broadcastIntent = new Intent();
        broadcastIntent.setClass(mContext, BluetoothScanReceiver.class);
        PendingIntent pi =
                PendingIntent.getBroadcast(
                        mContext, 1, broadcastIntent, PendingIntent.FLAG_IMMUTABLE);
        CountDownLatch latch = BluetoothScanReceiver.createCountDownLatch();
        mScanner.startScan(filters, batchScanSettings, pi);
        boolean gotResults = latch.await(20, TimeUnit.SECONDS);

        mScanner.stopScan(pi);
        assertThat(gotResults).isTrue();
    }

    // Perform a BLE scan to get results of nearby BLE devices.
    private Set<ScanResult> scan() {
        var regularLeScanCallback = new BleScanCallback();
        mScanner.startScan(regularLeScanCallback);
        TestUtils.sleep(SCAN_DURATION_MILLIS);

        mScanner.stopScan(regularLeScanCallback);
        TestUtils.sleep(SCAN_STOP_TIMEOUT);

        return regularLeScanCallback.getScanResults();
    }

    // Verify timestamp of all scan results are within [scanStartMillis, scanEndMillis].
    private void verifyTimestamp(
            Collection<ScanResult> results, long scanStartMillis, long scanEndMillis) {
        for (ScanResult result : results) {
            long timestampMillis = TimeUnit.NANOSECONDS.toMillis(result.getTimestampNanos());
            assertThat(timestampMillis).isAtLeast(scanStartMillis);
            assertThat(timestampMillis).isAtMost(scanEndMillis);
        }
    }

    private ScanSettings createScanSettings(int scanMode) {
        return createScanSettings(scanMode, 0); // 0 is the default report delay in ScanSettings
    }

    private ScanSettings createScanSettings(int scanMode, long reportDelay) {
        return new ScanSettings.Builder().setScanMode(scanMode).setReportDelay(reportDelay).build();
    }

    // Create a list of scan filters for up to 10 nearby beacons with highest signal strength.
    private List<ScanFilter> scanToCreateFiltersForTopStrongestNearbyBeacons() {
        // Get a list of nearby beacons.
        List<ScanResult> scanResults = new ArrayList<>(scan());
        assertThat(scanResults).isNotEmpty();

        Collections.sort(scanResults, new RssiComparator());

        List<ScanFilter> filters = new ArrayList<>();
        int devicesToCheck = Math.min(10, scanResults.size());

        for (int i = 0; i < devicesToCheck; i++) {
            ScanResult result = scanResults.get(i);
            ScanRecord record = result.getScanRecord();
            if (record == null) {
                continue;
            }

            Map<ParcelUuid, byte[]> serviceData = record.getServiceData();
            if (serviceData != null && !serviceData.isEmpty()) {
                ParcelUuid uuid = serviceData.keySet().iterator().next();
                filters.add(
                        new ScanFilter.Builder()
                                .setServiceData(uuid, new byte[] {0}, new byte[] {0})
                                .build());
                continue;
            }

            SparseArray<byte[]> manufacturerSpecificData = record.getManufacturerSpecificData();
            if (manufacturerSpecificData != null && manufacturerSpecificData.size() > 0) {
                filters.add(
                        new ScanFilter.Builder()
                                .setManufacturerData(
                                        manufacturerSpecificData.keyAt(0),
                                        new byte[] {0},
                                        new byte[] {0})
                                .build());
                continue;
            }

            List<ParcelUuid> serviceUuids = record.getServiceUuids();
            if (serviceUuids != null && !serviceUuids.isEmpty()) {
                filters.add(new ScanFilter.Builder().setServiceUuid(serviceUuids.get(0)).build());
            }
        }

        return filters;
    }

    private boolean isBleBatchScanSupported() {
        return mAdapter.isOffloadedScanBatchingSupported();
    }

    // Helper class for BLE scan callback.
    private class BleScanCallback extends ScanCallback {
        private final Set<ScanResult> mResults = new HashSet<>();
        private final Collection<ScanResult> mBatchScanResults = new ConcurrentLinkedQueue<>();

        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            if (callbackType == ScanSettings.CALLBACK_TYPE_ALL_MATCHES) {
                mResults.add(result);
            }
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            // In case onBatchScanResults are called due to buffer full, we want to collect all
            // scan results.
            mBatchScanResults.addAll(results);
            if (mFlushBatchScanLatch != null) {
                mFlushBatchScanLatch.countDown();
            }
        }

        // Clear regular and batch scan results.
        public synchronized void clear() {
            mResults.clear();
            mBatchScanResults.clear();
        }

        // Return regular BLE scan results accumulated so far.
        synchronized Set<ScanResult> getScanResults() {
            return Collections.unmodifiableSet(mResults);
        }

        // Return batch scan results.
        synchronized Collection<ScanResult> getBatchScanResults() {
            return Collections.unmodifiableCollection(mBatchScanResults);
        }
    }

    private class RssiComparator implements Comparator<ScanResult> {

        @Override
        public int compare(ScanResult lhs, ScanResult rhs) {
            return rhs.getRssi() - lhs.getRssi();
        }
    }
}
