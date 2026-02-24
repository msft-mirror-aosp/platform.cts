/*
 * Copyright (C) 2018 The Android Open Source Project
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

package android.net.wifi.rtt.cts;

import static android.net.wifi.rtt.PasnConfig.AKM_PASN;
import static android.net.wifi.rtt.PasnConfig.AKM_SAE;
import static android.net.wifi.rtt.PasnConfig.CIPHER_GCMP_256;
import static android.net.wifi.rtt.ProximityDetectionConfig.RANGING_MEASUREMENT_ROLE_ISTA;
import static android.net.wifi.rtt.ProximityDetectionConfig.RANGING_SERVICE_ROLE_SEEKER;
import static android.net.wifi.rtt.ResponderConfig.PREAMBLE_HE;
import static android.net.wifi.rtt.ResponderConfig.RESPONDER_AP;
import static android.net.wifi.rtt.ResponderConfig.RESPONDER_AWARE;
import static android.net.wifi.rtt.ResponderConfig.RESPONDER_STA;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeNotNull;
import static org.junit.Assume.assumeTrue;
import static org.mockito.Mockito.mock;

import android.net.MacAddress;
import android.net.wifi.OuiKeyedData;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiSsid;
import android.net.wifi.aware.AttachCallback;
import android.net.wifi.aware.PeerHandle;
import android.net.wifi.aware.WifiAwareManager;
import android.net.wifi.aware.WifiAwareSession;
import android.net.wifi.cts.WifiBuildCompat;
import android.net.wifi.cts.WifiFeature;
import android.net.wifi.rtt.ContinuousRangingResultCallback;
import android.net.wifi.rtt.PasnConfig;
import android.net.wifi.rtt.ProximityDetectionCharacteristics;
import android.net.wifi.rtt.ProximityDetectionConfig;
import android.net.wifi.rtt.RangingRequest;
import android.net.wifi.rtt.RangingResult;
import android.net.wifi.rtt.ResponderConfig;
import android.net.wifi.rtt.ResponderLocation;
import android.net.wifi.rtt.SecureRangingConfig;
import android.net.wifi.rtt.WifiRttManager;
import android.os.Build;
import android.os.PersistableBundle;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.filters.SdkSuppress;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.DeviceReportLog;
import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;
import com.android.compatibility.common.util.ShellIdentityUtils;
import com.android.wifi.flags.Flags;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Wi-Fi RTT CTS test: range to all available Access Points which support IEEE 802.11mc.
 */
@AppModeFull(reason = "Cannot get WifiManager in instant app mode")
@LargeTest
@RunWith(AndroidJUnit4.class)
public class WifiRttTest extends TestBase {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    // Number of scans to do while searching for APs supporting IEEE 802.11mc
    private static final int NUM_SCANS_SEARCHING_FOR_IEEE80211MC_AP = 5;

    // Number of RTT measurements per AP
    private static final int NUM_OF_RTT_ITERATIONS = 10;

    // Maximum failure rate of RTT measurements (percentage)
    private static final int MAX_FAILURE_RATE_PERCENT = 20;

    // Maximum variation from the average measurement (measures consistency)
    private static final int MAX_VARIATION_FROM_AVERAGE_DISTANCE_MM = 2000;

    // Maximum failure rate of one-sided RTT measurements (percentage)
    private static final int MAX_NON11MC_FAILURE_RATE_PERCENT = 40;

    // Maximum non-8011mc variation from the average measurement (measures consistency)
    private static final int MAX_NON11MC_VARIATION_FROM_AVERAGE_DISTANCE_MM = 6000;

    // Minimum valid RSSI value
    private static final int MIN_VALID_RSSI = -100;

    // Valid Mac Address
    private static final MacAddress MAC = MacAddress.fromString("00:01:02:03:04:05");

    // Interval between two ranging request.
    private static final int INTERVAL_MS = 1000;
    private static final String TEST_SSID = "test";
    private static final String TEST_PASSWORD = "secret";
    private static final byte[] TEST_PASN_COMEBACK_COOKIE = new byte[] {1, 2, 3};

    // Test parameters for single device proximity detection tests
    private static final int TEST_DISCOVERY_CHANNEL_FREQUENCY = 2437;
    private static final int TEST_PREFERRED_RANGING_CHANNEL_FREQUENCY = 5745;
    private static final int TEST_RANGING_INTERVAL_MS = 250;
    private static final byte[] TEST_DEVICE_IDENTITY_KEY = {
        11, 22, 33, 44, 55, 66, 77, 88, 11, 22, 33, 44, 55, 66, 77, 88
    };
    private final Object mLock = new Object();

    /**
     * Test Wi-Fi RTT ranging operation using ScanResults in request:
     * - Scan for visible APs for the test AP (which is validated to support IEEE 802.11mc)
     * - Perform N (constant) RTT operations
     * - Validate:
     *   - Failure ratio < threshold (constant)
     *   - Result margin < threshold (constant)
     */
    @Test
    public void testRangingToTest11mcApUsingScanResult() throws InterruptedException {
        // Scan for IEEE 802.11mc supporting APs
        ScanResult testAp = getS11McScanResult();
        assertNotNull(
                "Cannot find any test APs which support RTT / IEEE 802.11mc - please verify that "
                        + "your test setup includes them!", testAp);
        // Perform RTT operations
        RangingRequest.Builder builder = new RangingRequest.Builder();
        builder.addAccessPoint(testAp);

        if (WifiBuildCompat.isPlatformOrWifiModuleAtLeastS(getContext())) {
            builder.setRttBurstSize(RangingRequest.getMaxRttBurstSize());
            assertTrue(RangingRequest.getDefaultRttBurstSize()
                    >= RangingRequest.getMinRttBurstSize());
            assertTrue(RangingRequest.getDefaultRttBurstSize()
                    <= RangingRequest.getMaxRttBurstSize());
        }

        RangingRequest request = builder.build();
        if (WifiBuildCompat.isPlatformOrWifiModuleAtLeastS(getContext())) {
            assertEquals(1, request.getRttResponders().size());
        }
        range11mcApRequest(request, testAp);
    }

    /**
     * Test Wi-Fi RTT ranging using ResponderConfig in the single responder RangingRequest API.
     * - Scan for visible APs for the test AP (which is validated to support IEEE 802.11mc)
     * - Perform N (constant) RTT operations
     * - Validate:
     *   - Failure ratio < threshold (constant)
     *   - Result margin < threshold (constant)
     */
    @Test
    public void testRangingToTest11mcApUsingResponderConfig() throws InterruptedException {
        // Scan for IEEE 802.11mc supporting APs
        ScanResult testAp = getS11McScanResult();
        assertNotNull(
                "Cannot find any test APs which support RTT / IEEE 802.11mc - please verify that "
                        + "your test setup includes them!", testAp);
        int preamble = ResponderConfig.fromScanResult(testAp).getPreamble();

        // Create a ResponderConfig from the builder API.
        ResponderConfig.Builder responderBuilder = new ResponderConfig.Builder();
        ResponderConfig responder = responderBuilder
                .setMacAddress(MacAddress.fromString(testAp.BSSID))
                .set80211mcSupported(testAp.is80211mcResponder())
                .setChannelWidth(testAp.channelWidth)
                .setFrequencyMhz(testAp.frequency)
                .setCenterFreq0Mhz(testAp.centerFreq0)
                .setCenterFreq1Mhz(testAp.centerFreq1)
                .setPreamble(preamble)
                .setResponderType(RESPONDER_AP)
                .build();

        // Validate ResponderConfig.Builder set method arguments match getter methods.
        assertTrue(responder.getMacAddress().toString().equalsIgnoreCase(testAp.BSSID)
                && responder.is80211mcSupported() == testAp.is80211mcResponder()
                && responder.getChannelWidth() == testAp.channelWidth
                && responder.getFrequencyMhz() == testAp.frequency
                && responder.getCenterFreq0Mhz() == testAp.centerFreq0
                && responder.getCenterFreq1Mhz() == testAp.centerFreq1
                && responder.getPreamble() == preamble
                && responder.getResponderType() == RESPONDER_AP);

        // Perform RTT operations
        RangingRequest.Builder builder = new RangingRequest.Builder();
        builder.addResponder(responder);

        if (WifiBuildCompat.isPlatformOrWifiModuleAtLeastS(getContext())) {
            builder.setRttBurstSize(RangingRequest.getMaxRttBurstSize());
            assertTrue(RangingRequest.getDefaultRttBurstSize()
                    >= RangingRequest.getMinRttBurstSize());
            assertTrue(RangingRequest.getDefaultRttBurstSize()
                    <= RangingRequest.getMaxRttBurstSize());
        }

        RangingRequest request = builder.build();

        if (WifiBuildCompat.isPlatformOrWifiModuleAtLeastS(getContext())) {
            assertEquals(1, request.getRttResponders().size());
        }
        range11mcApRequest(request, testAp);
    }

    /**
     * Test Wi-Fi RTT ranging using ResponderConfig in the multi-Responder RangingRequest API.
     * - Scan for visible APs for the test AP (which is validated to support IEEE 802.11mc)
     * - Perform N (constant) RTT operations
     * - Validate:
     *   - Failure ratio < threshold (constant)
     *   - Result margin < threshold (constant)
     */
    @Test
    public void testRangingToTest11mcApUsingListResponderConfig() throws InterruptedException {
        // Scan for IEEE 802.11mc supporting APs
        ScanResult testAp = getS11McScanResult();
        assertNotNull(
                "Cannot find any test APs which support RTT / IEEE 802.11mc - please verify that "
                        + "your test setup includes them!", testAp);
        ResponderConfig responder = ResponderConfig.fromScanResult(testAp);
        // Perform RTT operations
        RangingRequest.Builder builder = new RangingRequest.Builder();
        List<ResponderConfig> responders = new ArrayList<>();
        responders.add(responder);
        builder.addResponders(responders);

        if (WifiBuildCompat.isPlatformOrWifiModuleAtLeastS(getContext())) {
            builder.setRttBurstSize(RangingRequest.getMaxRttBurstSize());
            assertTrue(RangingRequest.getDefaultRttBurstSize()
                    >= RangingRequest.getMinRttBurstSize());
            assertTrue(RangingRequest.getDefaultRttBurstSize()
                    <= RangingRequest.getMaxRttBurstSize());
        }

        RangingRequest request = builder.build();

        if (WifiBuildCompat.isPlatformOrWifiModuleAtLeastS(getContext())) {
            assertEquals(1, request.getRttResponders().size());
        }
        range11mcApRequest(request, testAp);
    }

    static class ContinuousResultCallback extends ContinuousRangingResultCallback {
        private final CountDownLatch mBlocker = new CountDownLatch(1);
        private List<RangingResult> mResults;
        private int mFailureCode = -1;
        private int mStoppedReason = -1;

        @Override
        public void onRangingFailure(int code) {
            Log.d(TAG, "onRangingFailure: " + code);
            mFailureCode = code;
            mBlocker.countDown();
        }

        @Override
        public void onRangingResults(List<RangingResult> results) {
            Log.d(TAG, "onRangingResults: ");
            mResults = results;
            mBlocker.countDown();
        }

        @Override
        public void onRangingStopped(int reason) {
            Log.d(TAG, "onRangingStopped: " + reason);
            mStoppedReason = reason;
            mBlocker.countDown();
        }

        /**
         * Waits for the listener callback to be called - or an error (timeout, interruption).
         * Returns true on callback called, false on error (timeout, interruption).
         */
        boolean waitForCallback() throws InterruptedException {
            return mBlocker.await(10, TimeUnit.SECONDS);
        }

        int getFailureCode() {
            return mFailureCode;
        }

        List<RangingResult> getResults() {
            return mResults;
        }

        int getRangingStoppedReason() {
            return mStoppedReason;
        }
    }

    /**
     * Utility method for validating 11mc ranging request.
     *
     * @param request the ranging request that is being tested
     * @param testAp the original test scan result to provide feedback on failure conditions
     */
    private void range11mcApRequest(RangingRequest request, ScanResult testAp)
            throws InterruptedException {
        Thread.sleep(5000);
        List<RangingResult> allResults = new ArrayList<>();
        int numFailures = 0;
        int distanceSum = 0;
        int distanceMin = Integer.MAX_VALUE;
        int distanceMax = Integer.MIN_VALUE;
        int[] statuses = new int[NUM_OF_RTT_ITERATIONS];
        int[] distanceMms = new int[NUM_OF_RTT_ITERATIONS];
        int[] distanceStdDevMms = new int[NUM_OF_RTT_ITERATIONS];
        int[] rssis = new int[NUM_OF_RTT_ITERATIONS];
        int[] numAttempted = new int[NUM_OF_RTT_ITERATIONS];
        int[] numSuccessful = new int[NUM_OF_RTT_ITERATIONS];
        int[] frequencies = new int[NUM_OF_RTT_ITERATIONS];
        int[] packetBws = new int[NUM_OF_RTT_ITERATIONS];
        long[] timestampsMs = new long[NUM_OF_RTT_ITERATIONS];
        byte[] lastLci = null;
        byte[] lastLcr = null;
        for (int i = 0; i < NUM_OF_RTT_ITERATIONS; ++i) {
            ResultCallback callback = new ResultCallback();
            mWifiRttManager.startRanging(request, mExecutor, callback);
            assertTrue("Wi-Fi RTT results: no callback on iteration " + i,
                    callback.waitForCallback());

            List<RangingResult> currentResults = callback.getResults();
            assertNotNull("Wi-Fi RTT results: null results (onRangingFailure) on iteration " + i,
                    currentResults);
            assertEquals("Wi-Fi RTT results: unexpected # of results (expect 1) on iteration " + i,
                    1, currentResults.size());
            RangingResult result = currentResults.get(0);
            assertEquals("Wi-Fi RTT results: invalid result (wrong BSSID) entry on iteration " + i,
                    result.getMacAddress().toString(), testAp.BSSID);
            assertNull("Wi-Fi RTT results: invalid result (non-null PeerHandle) entry on iteration "
                    + i, result.getPeerHandle());

            allResults.add(result);
            int status = result.getStatus();
            statuses[i] = status;
            if (status == RangingResult.STATUS_SUCCESS) {
                if (WifiBuildCompat.isPlatformOrWifiModuleAtLeastS(getContext())) {
                    assertEquals(
                            "Wi-Fi RTT results: invalid result (wrong rttBurstSize) entry on "
                                    + "iteration "
                                    + i,
                            result.getNumAttemptedMeasurements(),
                            RangingRequest.getMaxRttBurstSize());
                    assertTrue("Wi-Fi RTT results: should be a 802.11MC measurement",
                            result.is80211mcMeasurement());
                }
                distanceSum += result.getDistanceMm();
                distanceMin = Math.min(distanceMin, result.getDistanceMm());
                distanceMax = Math.max(distanceMax, result.getDistanceMm());

                assertTrue("Wi-Fi RTT results: invalid RSSI on iteration " + i,
                        result.getRssi() >= MIN_VALID_RSSI);

                distanceMms[i - numFailures] = result.getDistanceMm();
                distanceStdDevMms[i - numFailures] = result.getDistanceStdDevMm();
                rssis[i - numFailures] = result.getRssi();
                numAttempted[i - numFailures] = result.getNumAttemptedMeasurements();
                numSuccessful[i - numFailures] = result.getNumSuccessfulMeasurements();
                timestampsMs[i - numFailures] = result.getRangingTimestampMillis();
                frequencies[i - numFailures] = result.getMeasurementChannelFrequencyMHz();
                packetBws[i - numFailures] = result.getMeasurementBandwidth();

                byte[] currentLci = result.getLci();
                byte[] currentLcr = result.getLcr();
                if (i - numFailures > 0) {
                    assertArrayEquals(
                            "Wi-Fi RTT results: invalid result (LCI mismatch) on iteration " + i,
                            currentLci, lastLci);
                    assertArrayEquals(
                            "Wi-Fi RTT results: invalid result (LCR mismatch) on iteration " + i,
                            currentLcr, lastLcr);
                }
                lastLci = currentLci;
                lastLcr = currentLcr;
            } else {
                numFailures++;
            }
            // Sleep a while to avoid stress AP.
            Thread.sleep(INTERVAL_MS);
        }

        // Save results to log
        int numGoodResults = NUM_OF_RTT_ITERATIONS - numFailures;
        DeviceReportLog reportLog = new DeviceReportLog(TAG, "testRangingToTestAp");
        reportLog.addValues("status_codes", statuses, ResultType.NEUTRAL, ResultUnit.NONE);
        reportLog.addValues("distance_mm", Arrays.copyOf(distanceMms, numGoodResults),
                ResultType.NEUTRAL, ResultUnit.NONE);
        reportLog.addValues("distance_stddev_mm", Arrays.copyOf(distanceStdDevMms, numGoodResults),
                ResultType.NEUTRAL, ResultUnit.NONE);
        reportLog.addValues("rssi_dbm", Arrays.copyOf(rssis, numGoodResults), ResultType.NEUTRAL,
                ResultUnit.NONE);
        reportLog.addValues("num_attempted", Arrays.copyOf(numAttempted, numGoodResults),
                ResultType.NEUTRAL, ResultUnit.NONE);
        reportLog.addValues("num_successful", Arrays.copyOf(numSuccessful, numGoodResults),
                ResultType.NEUTRAL, ResultUnit.NONE);
        reportLog.addValues("timestamps", Arrays.copyOf(timestampsMs, numGoodResults),
                ResultType.NEUTRAL, ResultUnit.NONE);
        reportLog.addValues("frequencies", Arrays.copyOf(frequencies, numGoodResults),
                ResultType.NEUTRAL, ResultUnit.NONE);
        reportLog.addValues("packetBws", Arrays.copyOf(packetBws, numGoodResults),
                ResultType.NEUTRAL, ResultUnit.NONE);
        reportLog.submit();

        // Analyze results
        assertTrue("Wi-Fi RTT failure rate exceeds threshold: FAIL=" + numFailures + ", ITERATIONS="
                        + NUM_OF_RTT_ITERATIONS + ", AP=" + testAp,
                numFailures <= NUM_OF_RTT_ITERATIONS * MAX_FAILURE_RATE_PERCENT / 100);
        if (numFailures != NUM_OF_RTT_ITERATIONS) {
            double distanceAvg = (double) distanceSum / (NUM_OF_RTT_ITERATIONS - numFailures);
            assertTrue("Wi-Fi RTT: Variation (max direction) exceeds threshold, Variation ="
                            + (distanceMax - distanceAvg),
                    (distanceMax - distanceAvg) <= MAX_VARIATION_FROM_AVERAGE_DISTANCE_MM);
            assertTrue("Wi-Fi RTT: Variation (min direction) exceeds threshold, Variation ="
                            + (distanceAvg - distanceMin),
                    (distanceAvg - distanceMin) <= MAX_VARIATION_FROM_AVERAGE_DISTANCE_MM);
            for (int i = 0; i < numGoodResults; ++i) {
                assertNotEquals("Number of attempted measurements is 0", 0, numAttempted[i]);
                assertNotEquals("Number of successful measurements is 0", 0, numSuccessful[i]);
            }
        }
    }

    /**
     * Utility method for validating 11az ranging request.
     *
     * @param request the ranging request that is being tested
     * @param testAp the original test scan result to provide feedback on failure conditions
     * @param isSecure whether the ranging is secure or not
     */
    private void range11azApRequest(RangingRequest request, ScanResult testAp, boolean isSecure)
            throws InterruptedException {
        Thread.sleep(5000);
        List<RangingResult> allResults = new ArrayList<>();
        int numFailures = 0;
        int distanceSum = 0;
        int distanceMin = Integer.MAX_VALUE;
        int distanceMax = Integer.MIN_VALUE;
        int[] statuses = new int[NUM_OF_RTT_ITERATIONS];
        int[] distanceMms = new int[NUM_OF_RTT_ITERATIONS];
        int[] distanceStdDevMms = new int[NUM_OF_RTT_ITERATIONS];
        int[] rssis = new int[NUM_OF_RTT_ITERATIONS];
        int[] numAttempted = new int[NUM_OF_RTT_ITERATIONS];
        int[] numSuccessful = new int[NUM_OF_RTT_ITERATIONS];
        int[] frequencies = new int[NUM_OF_RTT_ITERATIONS];
        int[] packetBws = new int[NUM_OF_RTT_ITERATIONS];
        long[] timestampsMs = new long[NUM_OF_RTT_ITERATIONS];
        int[] i2rTxLtfRepetitions = new int[NUM_OF_RTT_ITERATIONS];
        int[] r2iTxLtfRepetitions = new int[NUM_OF_RTT_ITERATIONS];
        int[] numRxSts = new int[NUM_OF_RTT_ITERATIONS];
        int[] numTxSts = new int[NUM_OF_RTT_ITERATIONS];
        long[] maxNtbMeasurementTime = new long[NUM_OF_RTT_ITERATIONS];
        long[] minNtbMeasurementTime = new long[NUM_OF_RTT_ITERATIONS];

        byte[] lastLci = null;
        byte[] lastLcr = null;
        for (int i = 0; i < NUM_OF_RTT_ITERATIONS; ++i) {
            ResultCallback callback = new ResultCallback();
            mWifiRttManager.startRanging(request, mExecutor, callback);
            assertTrue("Wi-Fi RTT results: no callback on iteration " + i,
                    callback.waitForCallback());

            List<RangingResult> currentResults = callback.getResults();
            assertNotNull("Wi-Fi RTT results: null results (onRangingFailure) on iteration " + i,
                    currentResults);
            assertEquals("Wi-Fi RTT results: unexpected # of results (expect 1) on iteration " + i,
                    1, currentResults.size());
            RangingResult result = currentResults.get(0);
            assertEquals("Wi-Fi RTT results: invalid result (wrong BSSID) entry on iteration " + i,
                    result.getMacAddress().toString(), testAp.BSSID);
            assertNull("Wi-Fi RTT results: invalid result (non-null PeerHandle) entry on iteration "
                    + i, result.getPeerHandle());

            allResults.add(result);
            int status = result.getStatus();
            statuses[i] = status;
            if (status == RangingResult.STATUS_SUCCESS) {
                assertTrue("Wi-Fi RTT results: should be a 802.11az measurement",
                        result.is80211azNtbMeasurement());
                if (isSecure) {
                    assertTrue("Ranging frames should be protected",
                            result.isRangingFrameProtected());
                    assertTrue("Secure HE-LTF should be enabled", result.isSecureHeLtfEnabled());
                    assertTrue("Ranging should be authenticated", result.isRangingAuthenticated());
                }
                distanceSum += result.getDistanceMm();
                distanceMin = Math.min(distanceMin, result.getDistanceMm());
                distanceMax = Math.max(distanceMax, result.getDistanceMm());

                assertTrue("Wi-Fi RTT results: invalid RSSI on iteration " + i,
                        result.getRssi() >= MIN_VALID_RSSI);

                distanceMms[i - numFailures] = result.getDistanceMm();
                distanceStdDevMms[i - numFailures] = result.getDistanceStdDevMm();
                rssis[i - numFailures] = result.getRssi();
                numAttempted[i - numFailures] = result.getNumAttemptedMeasurements();
                numSuccessful[i - numFailures] = result.getNumSuccessfulMeasurements();
                timestampsMs[i - numFailures] = result.getRangingTimestampMillis();
                frequencies[i - numFailures] = result.getMeasurementChannelFrequencyMHz();
                packetBws[i - numFailures] = result.getMeasurementBandwidth();
                i2rTxLtfRepetitions[i - numFailures] =
                        result.get80211azInitiatorTxLtfRepetitionsCount();
                r2iTxLtfRepetitions[i - numFailures] =
                        result.get80211azResponderTxLtfRepetitionsCount();
                numRxSts[i - numFailures] = result.get80211azNumberOfRxSpatialStreams();
                numTxSts[i - numFailures] = result.get80211azNumberOfTxSpatialStreams();
                maxNtbMeasurementTime[i - numFailures] =
                        result.getMaxTimeBetweenNtbMeasurementsMicros();
                minNtbMeasurementTime[i - numFailures] =
                        result.getMinTimeBetweenNtbMeasurementsMicros();

                byte[] currentLci = result.getLci();
                byte[] currentLcr = result.getLcr();
                if (i - numFailures > 0) {
                    assertArrayEquals(
                            "Wi-Fi RTT results: invalid result (LCI mismatch) on iteration " + i,
                            currentLci, lastLci);
                    assertArrayEquals(
                            "Wi-Fi RTT results: invalid result (LCR mismatch) on iteration " + i,
                            currentLcr, lastLcr);
                }
                lastLci = currentLci;
                lastLcr = currentLcr;
            } else {
                numFailures++;
            }
            long minWait = TimeUnit.MICROSECONDS.toMillis(
                    result.getMinTimeBetweenNtbMeasurementsMicros());
            if (isSecure && result.getPasnComebackCookie() != null) {
                minWait = Math.max(minWait, result.getPasnComebackAfterMillis());
            }
            // Wait for the minimum measurement time
            Thread.sleep(minWait);
        }

        // Save results to log
        int numGoodResults = NUM_OF_RTT_ITERATIONS - numFailures;
        DeviceReportLog reportLog = new DeviceReportLog(TAG, "testRangingToTestAp");
        reportLog.addValues("status_codes", statuses, ResultType.NEUTRAL, ResultUnit.NONE);
        reportLog.addValues("distance_mm", Arrays.copyOf(distanceMms, numGoodResults),
                ResultType.NEUTRAL, ResultUnit.NONE);
        reportLog.addValues("distance_stddev_mm", Arrays.copyOf(distanceStdDevMms, numGoodResults),
                ResultType.NEUTRAL, ResultUnit.NONE);
        reportLog.addValues("rssi_dbm", Arrays.copyOf(rssis, numGoodResults), ResultType.NEUTRAL,
                ResultUnit.NONE);
        reportLog.addValues("num_attempted", Arrays.copyOf(numAttempted, numGoodResults),
                ResultType.NEUTRAL, ResultUnit.NONE);
        reportLog.addValues("num_successful", Arrays.copyOf(numSuccessful, numGoodResults),
                ResultType.NEUTRAL, ResultUnit.NONE);
        reportLog.addValues("timestamps", Arrays.copyOf(timestampsMs, numGoodResults),
                ResultType.NEUTRAL, ResultUnit.NONE);
        reportLog.addValues("frequencies", Arrays.copyOf(frequencies, numGoodResults),
                ResultType.NEUTRAL, ResultUnit.NONE);
        reportLog.addValues("packetBws", Arrays.copyOf(packetBws, numGoodResults),
                ResultType.NEUTRAL, ResultUnit.NONE);
        reportLog.addValues("i2rTxLtfRepetitions",
                Arrays.copyOf(i2rTxLtfRepetitions, numGoodResults),
                ResultType.NEUTRAL, ResultUnit.NONE);
        reportLog.addValues("r2iTxLtfRepetitions",
                Arrays.copyOf(r2iTxLtfRepetitions, numGoodResults),
                ResultType.NEUTRAL, ResultUnit.NONE);
        reportLog.addValues("numRxSts", Arrays.copyOf(numRxSts, numGoodResults),
                ResultType.NEUTRAL, ResultUnit.NONE);
        reportLog.addValues("numTxSts", Arrays.copyOf(numRxSts, numGoodResults),
                ResultType.NEUTRAL, ResultUnit.NONE);
        reportLog.addValues("maxNtbMeasurementTime",
                Arrays.copyOf(maxNtbMeasurementTime, numGoodResults),
                ResultType.NEUTRAL, ResultUnit.NONE);
        reportLog.addValues("minNtbMeasurementTime",
                Arrays.copyOf(minNtbMeasurementTime, numGoodResults),
                ResultType.NEUTRAL, ResultUnit.NONE);
        reportLog.submit();

        // Analyze results
        assertTrue("Wi-Fi RTT failure rate exceeds threshold: FAIL=" + numFailures + ", ITERATIONS="
                        + NUM_OF_RTT_ITERATIONS + ", AP=" + testAp,
                numFailures <= NUM_OF_RTT_ITERATIONS * MAX_FAILURE_RATE_PERCENT / 100);
        if (numFailures != NUM_OF_RTT_ITERATIONS) {
            double distanceAvg = (double) distanceSum / (NUM_OF_RTT_ITERATIONS - numFailures);
            assertTrue("Wi-Fi RTT: Variation (max direction) exceeds threshold, Variation ="
                            + (distanceMax - distanceAvg),
                    (distanceMax - distanceAvg) <= MAX_VARIATION_FROM_AVERAGE_DISTANCE_MM);
            assertTrue("Wi-Fi RTT: Variation (min direction) exceeds threshold, Variation ="
                            + (distanceAvg - distanceMin),
                    (distanceAvg - distanceMin) <= MAX_VARIATION_FROM_AVERAGE_DISTANCE_MM);
            for (int i = 0; i < numGoodResults; ++i) {
                assertNotEquals("Number of attempted measurements is 0", 0, numAttempted[i]);
                assertNotEquals("Number of successful measurements is 0", 0, numSuccessful[i]);
            }
        }
    }

    /**
     * Validate that when a request contains more range operations than allowed (by API) that we
     * get an exception.
     */
    @Test
    public void testRequestTooLarge() throws InterruptedException {
        ScanResult testAp = getS11McScanResult();
        assertNotNull(
                "Cannot find any test APs which support RTT / IEEE 802.11mc - please verify that "
                        + "your test setup includes them!", testAp);

        RangingRequest.Builder builder = new RangingRequest.Builder();
        List<ScanResult> scanResults = new ArrayList<>();
        for (int i = 0; i < RangingRequest.getMaxPeers() - 2; ++i) {
            scanResults.add(testAp);
        }
        builder.addAccessPoints(scanResults);

        ScanResult testApNon80211mc = null;
        if (WifiBuildCompat.isPlatformOrWifiModuleAtLeastS(getContext())) {
            testApNon80211mc = getLegacyScanResult();
        }
        if (testApNon80211mc == null) {
            builder.addAccessPoints(List.of(testAp, testAp, testAp));
        } else {
            builder.addNon80211mcCapableAccessPoints(List.of(testApNon80211mc, testApNon80211mc,
                    testApNon80211mc));
        }

        try {
            mWifiRttManager.startRanging(builder.build(), mExecutor, new ResultCallback());
        } catch (IllegalArgumentException e) {
            return;
        }

        fail("Did not receive expected IllegalArgumentException when tried to range to too "
                + "many peers");
    }

    /**
     * Verify ResponderLocation API
     */
    @Test
    public void testRangingToTestApWithResponderLocation() throws InterruptedException {
        // Scan for IEEE 802.11mc supporting APs
        ScanResult testAp = getS11McScanResult();
        assertNotNull(
                "Cannot find any test APs which support RTT / IEEE 802.11mc - please verify that "
                        + "your test setup includes them!", testAp);

        // Perform RTT operations
        RangingRequest request = new RangingRequest.Builder().addAccessPoint(testAp).build();
        ResultCallback callback = new ResultCallback();
        mWifiRttManager.startRanging(request, mExecutor, callback);
        assertTrue("Wi-Fi RTT results: no callback! ",
                callback.waitForCallback());

        RangingResult result = callback.getResults().get(0);
        assertEquals("Ranging request not success",
                result.getStatus(), RangingResult.STATUS_SUCCESS);
        ResponderLocation responderLocation = result.getUnverifiedResponderLocation();
        if (responderLocation == null) {
            return;
        }
        assertTrue("ResponderLocation is not valid", responderLocation.isLciSubelementValid());

        // Check LCI related APIs
        int exceptionCount = 0;
        int apiCount = 0;
        try {
            apiCount++;
            responderLocation.getLatitudeUncertainty();
        } catch (IllegalStateException e) {
            exceptionCount++;
        }
        try {
            apiCount++;
            responderLocation.getLatitude();
        } catch (IllegalStateException e) {
            exceptionCount++;
        }
        try {
            apiCount++;
            responderLocation.getLongitudeUncertainty();
        } catch (IllegalStateException e) {
            exceptionCount++;
        }
        try {
            apiCount++;
            responderLocation.getLongitude();
        } catch (IllegalStateException e) {
            exceptionCount++;
        }
        try {
            apiCount++;
            responderLocation.getAltitudeType();
        } catch (IllegalStateException e) {
            exceptionCount++;
        }
        try {
            apiCount++;
            responderLocation.getAltitudeUncertainty();
        } catch (IllegalStateException e) {
            exceptionCount++;
        }
        try {
            apiCount++;
            responderLocation.getAltitude();
        } catch (IllegalStateException e) {
            exceptionCount++;
        }
        try {
            apiCount++;
            responderLocation.getDatum();
        } catch (IllegalStateException e) {
            exceptionCount++;
        }
        try {
            apiCount++;
            responderLocation.getRegisteredLocationAgreementIndication();
        } catch (IllegalStateException e) {
            exceptionCount++;
        }
        try {
            apiCount++;
            responderLocation.getLciVersion();
        } catch (IllegalStateException e) {
            exceptionCount++;
        }
        try {
            apiCount++;
            assertNotNull(responderLocation.toLocation());
        } catch (IllegalStateException e) {
            exceptionCount++;
        }
        // If LCI is not valid, all APIs should throw exception, otherwise no exception.
        assertEquals("Exception number should equal to API number",
                responderLocation.isLciSubelementValid()? 0 : apiCount, exceptionCount);

        // Verify ZaxisSubelement APIs
        apiCount = 0;
        exceptionCount = 0;

        try {
            apiCount++;
            responderLocation.getExpectedToMove();
        } catch (IllegalStateException e) {
            exceptionCount++;
        }

        try {
            apiCount++;
            responderLocation.getFloorNumber();
        } catch (IllegalStateException e) {
            exceptionCount++;
        }

        try {
            apiCount++;
            responderLocation.getHeightAboveFloorMeters();
        } catch (IllegalStateException e) {
            exceptionCount++;
        }

        try {
            apiCount++;
            responderLocation.getHeightAboveFloorUncertaintyMeters();
        } catch (IllegalStateException e) {
            exceptionCount++;
        }
        // If Zaxis is not valid, all APIs should throw exception, otherwise no exception.
        assertEquals("Exception number should equal to API number",
                responderLocation.isZaxisSubelementValid() ? 0 : apiCount, exceptionCount);
        // Verify civic location
        if (responderLocation.toCivicLocationAddress() == null) {
            assertNull(responderLocation.toCivicLocationSparseArray());
        } else {
            assertNotNull(responderLocation.toCivicLocationSparseArray());
        }
        // Verify map image
        if (responderLocation.getMapImageUri() == null) {
            assertNull(responderLocation.getMapImageMimeType());
        } else {
            assertNotNull(responderLocation.getMapImageMimeType());
        }
        boolean extraInfoOnAssociationIndication =
                responderLocation.getExtraInfoOnAssociationIndication();
        assertNotNull("ColocatedBSSID list should be nonNull",
                responderLocation.getColocatedBssids());
    }

    /**
     * Verify ranging request with aware peer Mac address and peer handle.
     */
    @Test
    public void testAwareRttWithMacAddress() throws InterruptedException {
        if (!WifiFeature.isAwareSupported(getContext())) {
            return;
        }
        WifiAwareManager awareManager = sContext.getSystemService(WifiAwareManager.class);
        assertNotNull(awareManager);
        CountDownLatch countDownLatch = new CountDownLatch(1);
        final WifiAwareSession[] awareSession = {null};
        awareManager.attach(
                new AttachCallback() {
                    @Override
                    public void onAttached(WifiAwareSession session) {
                        awareSession[0] = session;
                        countDownLatch.countDown();
                    }
                },
                mHandler);
        countDownLatch.await();
        try {
            RangingRequest request = new RangingRequest.Builder().addWifiAwarePeer(MAC).build();
            ResultCallback callback = new ResultCallback();
            mWifiRttManager.startRanging(request, mExecutor, callback);
            assertTrue("Wi-Fi RTT results: no callback", callback.waitForCallback());
            List<RangingResult> rangingResults = callback.getResults();
            assertNotNull("Wi-Fi RTT results: null results", rangingResults);
            assertEquals(1, rangingResults.size());
            assertEquals(RangingResult.STATUS_FAIL, rangingResults.get(0).getStatus());
        } finally {
            awareSession[0].close();
        }
    }

    /**
     * Verify ranging request with aware peer handle.
     */
    @Test
    public void testAwareRttWithPeerHandle() throws InterruptedException {
        if (!WifiFeature.isAwareSupported(getContext())) {
            return;
        }
        WifiAwareManager awareManager = sContext.getSystemService(WifiAwareManager.class);
        assertNotNull(awareManager);
        CountDownLatch countDownLatch = new CountDownLatch(1);
        final WifiAwareSession[] awareSession = {null};
        awareManager.attach(
                new AttachCallback() {
                    @Override
                    public void onAttached(WifiAwareSession session) {
                        awareSession[0] = session;
                        countDownLatch.countDown();
                    }
                },
                mHandler);
        countDownLatch.await();
        try {
            PeerHandle peerHandle = mock(PeerHandle.class);
            RangingRequest request =
                    new RangingRequest.Builder().addWifiAwarePeer(peerHandle).build();
            ResultCallback callback = new ResultCallback();
            mWifiRttManager.startRanging(request, mExecutor, callback);
            assertTrue("Wi-Fi RTT results: no callback", callback.waitForCallback());
            List<RangingResult> rangingResults = callback.getResults();
            assertNotNull("Wi-Fi RTT results: null results", rangingResults);
            assertEquals("Invalid peerHandle should return 0 result", 0, rangingResults.size());
        } finally {
            awareSession[0].close();
        }
    }

    /** Verify ranging request with aware peer handle set by setPeerHandle(). */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_IMPROVE_RANGING_API)
    @ApiTest(apis = {"android.net.wifi.rtt.ResponderConfig.Builder#setPeerHandle"})
    public void testAwareRttWithSetPeerHandle() throws InterruptedException {
        if (!WifiFeature.isAwareSupported(getContext())) {
            return;
        }
        WifiAwareManager awareManager = sContext.getSystemService(WifiAwareManager.class);
        assertNotNull(awareManager);
        CountDownLatch countDownLatch = new CountDownLatch(1);
        final WifiAwareSession[] awareSession = {null};
        awareManager.attach(
                new AttachCallback() {
                    @Override
                    public void onAttached(WifiAwareSession session) {
                        awareSession[0] = session;
                        countDownLatch.countDown();
                    }
                },
                mHandler);
        countDownLatch.await();
        try {
            PeerHandle peerHandle = mock(PeerHandle.class);
            ResponderConfig awareResponder =
                    new ResponderConfig.Builder()
                            .setPeerHandle(peerHandle)
                            .setResponderType(RESPONDER_AWARE)
                            .build();
            RangingRequest request =
                    new RangingRequest.Builder().addResponder(awareResponder).build();
            ResultCallback callback = new ResultCallback();
            mWifiRttManager.startRanging(request, mExecutor, callback);
            assertTrue("Wi-Fi RTT results: no callback", callback.waitForCallback());
            List<RangingResult> rangingResults = callback.getResults();
            assertNotNull("Wi-Fi RTT results: null results", rangingResults);
            assertEquals("Invalid peerHandle should return 0 result", 0, rangingResults.size());
        } finally {
            awareSession[0].close();
        }
    }

    /**
     * Test Wi-Fi One-sided RTT ranging operation using ScanResult in request:
     * - Scan for visible APs for the test AP (which do not support IEEE 802.11mc) and are operating
     * - in the 5GHz band.
     * - Perform N (constant) RTT operations
     * - Remove outliers while insuring greater than 50% of the results still remain
     * - Validate:
     *   - Failure ratio < threshold (constant)
     *   - Result margin < threshold (constant)
     */
    @Test
    public void testRangingToTestNon11mcApUsingScanResult() throws InterruptedException {
        if (!WifiBuildCompat.isPlatformOrWifiModuleAtLeastS(getContext())) {
            return;
        }

        // Scan for Non-IEEE 802.11mc supporting APs
        ScanResult testAp = getLegacyScanResult();
        assertNotNull(
                "Cannot find any test APs which are Non-IEEE 802.11mc - please verify that"
                        + " your test setup includes them!", testAp);

        // Perform RTT operations
        RangingRequest.Builder builder = new RangingRequest.Builder();
        builder.addNon80211mcCapableAccessPoint(testAp);
        builder.setRttBurstSize(RangingRequest.getMaxRttBurstSize());
        RangingRequest request = builder.build();

        // Perform the request
        rangeNon11mcApRequest(request, testAp, MAX_NON11MC_VARIATION_FROM_AVERAGE_DISTANCE_MM);
    }

    /**
     * Test Wi-Fi one-sided RTT ranging operation using ResponderConfig in request:
     * - Scan for visible APs for the test AP (which do not support IEEE 802.11mc) and are operating
     * - in the 5GHz band.
     * - Perform N (constant) RTT operations
     * - Remove outliers while insuring greater than 50% of the results still remain
     * - Validate:
     *   - Failure ratio < threshold (constant)
     *   - Result margin < threshold (constant)
     */
    @Test
    public void testRangingToTestNon11mcApUsingResponderConfig() throws InterruptedException {
        if (!WifiBuildCompat.isPlatformOrWifiModuleAtLeastS(getContext())) {
            return;
        }

        // Scan for Non-IEEE 802.11mc supporting APs
        ScanResult testAp = getLegacyScanResult();
        assertNotNull(
                "Cannot find any test APs which are Non-IEEE 802.11mc - please verify that"
                        + " your test setup includes them!", testAp);

        ResponderConfig responder = ResponderConfig.fromScanResult(testAp);

        // Perform RTT operations
        RangingRequest.Builder builder = new RangingRequest.Builder();
        builder.addResponder(responder);
        builder.setRttBurstSize(RangingRequest.getMaxRttBurstSize());
        RangingRequest request = builder.build();



        // Perform the request
        rangeNon11mcApRequest(request, testAp, MAX_NON11MC_VARIATION_FROM_AVERAGE_DISTANCE_MM);
    }

    /**
     * Utility method for validating a ranging request to a non-80211mc AP.
     *
     * @param request the ranging request that is being tested
     * @param testAp the original test scan result to provide feedback on failure conditions
     */
    private void rangeNon11mcApRequest(RangingRequest request, ScanResult testAp,
            int variationLimit) throws InterruptedException {
        Thread.sleep(5000);
        List<RangingResult> allResults = new ArrayList<>();
        int numFailures = 0;
        int distanceSum = 0;
        int distanceMin = 0;
        int distanceMax = 0;
        int[] statuses = new int[NUM_OF_RTT_ITERATIONS];
        int[] distanceMms = new int[NUM_OF_RTT_ITERATIONS];
        boolean[] distanceInclusionMap = new boolean[NUM_OF_RTT_ITERATIONS];
        int[] distanceStdDevMms = new int[NUM_OF_RTT_ITERATIONS];
        int[] rssis = new int[NUM_OF_RTT_ITERATIONS];
        int[] numAttempted = new int[NUM_OF_RTT_ITERATIONS];
        int[] numSuccessful = new int[NUM_OF_RTT_ITERATIONS];
        long[] timestampsMs = new long[NUM_OF_RTT_ITERATIONS];
        byte[] lastLci = null;
        byte[] lastLcr = null;
        for (int i = 0; i < NUM_OF_RTT_ITERATIONS; ++i) {
            ResultCallback callback = new ResultCallback();
            mWifiRttManager.startRanging(request, mExecutor, callback);
            assertTrue("Wi-Fi RTT results: no callback on iteration " + i,
                    callback.waitForCallback());

            List<RangingResult> currentResults = callback.getResults();
            assertNotNull(
                    "Wi-Fi RTT results: null results (onRangingFailure) on iteration " + i,
                    currentResults);
            assertEquals(
                    "Wi-Fi RTT results: unexpected # of results (expect 1) on iteration " + i,
                    1, currentResults.size());
            RangingResult result = currentResults.get(0);
            assertEquals(
                    "Wi-Fi RTT results: invalid result (wrong BSSID) entry on iteration " + i,
                    result.getMacAddress().toString(), testAp.BSSID);

            assertNull(
                    "Wi-Fi RTT results: invalid result (non-null PeerHandle) entry on iteration "
                            + i, result.getPeerHandle());

            allResults.add(result);
            int status = result.getStatus();
            statuses[i] = status;
            if (status == RangingResult.STATUS_SUCCESS) {
                assertFalse("Wi-Fi RTT results: should not be a 802.11MC measurement",
                        result.is80211mcMeasurement());
                distanceSum += result.getDistanceMm();

                assertTrue("Wi-Fi RTT results: invalid RSSI on iteration " + i,
                        result.getRssi() >= MIN_VALID_RSSI);

                distanceMms[i - numFailures] = result.getDistanceMm();
                distanceStdDevMms[i - numFailures] = result.getDistanceStdDevMm();
                rssis[i - numFailures] = result.getRssi();
                // For one-sided RTT the number of packets attempted in a burst is not available,
                // So we set the result to be the same as used in the request.
                numAttempted[i - numFailures] = request.getRttBurstSize();
                numSuccessful[i - numFailures] = result.getNumSuccessfulMeasurements();
                timestampsMs[i - numFailures] = result.getRangingTimestampMillis();

                byte[] currentLci = result.getLci();
                byte[] currentLcr = result.getLcr();
                if (i - numFailures > 0) {
                    assertArrayEquals(
                            "Wi-Fi RTT results: invalid result (LCI mismatch) on iteration " + i,
                            currentLci, lastLci);
                    assertArrayEquals(
                            "Wi-Fi RTT results: invalid result (LCR mismatch) on iteration " + i,
                            currentLcr, lastLcr);
                }
                lastLci = currentLci;
                lastLcr = currentLcr;
            } else {
                numFailures++;
            }
            // Sleep a while to avoid stress AP.
            Thread.sleep(INTERVAL_MS);
        }
        // Save results to log
        int numGoodResults = NUM_OF_RTT_ITERATIONS - numFailures;
        DeviceReportLog reportLog = new DeviceReportLog(TAG, "testRangingToTestAp");
        reportLog.addValues("status_codes", statuses, ResultType.NEUTRAL, ResultUnit.NONE);
        reportLog.addValues("distance_mm", Arrays.copyOf(distanceMms, numGoodResults),
                ResultType.NEUTRAL, ResultUnit.NONE);
        reportLog.addValues("distance_stddev_mm",
                Arrays.copyOf(distanceStdDevMms, numGoodResults),
                ResultType.NEUTRAL, ResultUnit.NONE);
        reportLog.addValues("rssi_dbm", Arrays.copyOf(rssis, numGoodResults),
                ResultType.NEUTRAL,
                ResultUnit.NONE);
        reportLog.addValues("num_attempted", Arrays.copyOf(numAttempted, numGoodResults),
                ResultType.NEUTRAL, ResultUnit.NONE);
        reportLog.addValues("num_successful", Arrays.copyOf(numSuccessful, numGoodResults),
                ResultType.NEUTRAL, ResultUnit.NONE);
        reportLog.addValues("timestamps", Arrays.copyOf(timestampsMs, numGoodResults),
                ResultType.NEUTRAL, ResultUnit.NONE);
        reportLog.submit();

        if (mCharacteristics != null && mCharacteristics.getBoolean(WifiRttManager
                .CHARACTERISTICS_KEY_BOOLEAN_ONE_SIDED_RTT)) {
            // Analyze results
            assertTrue("Wi-Fi RTT failure rate exceeds threshold: FAIL=" + numFailures
                            + ", ITERATIONS="
                            + NUM_OF_RTT_ITERATIONS + ", AP=" + testAp,
                    numFailures <= NUM_OF_RTT_ITERATIONS * MAX_NON11MC_FAILURE_RATE_PERCENT / 100);
        }

        if (numFailures != NUM_OF_RTT_ITERATIONS) {
            // Calculate an initial average using all measurements to determine distance outliers
            double distanceAvg = (double) distanceSum / (NUM_OF_RTT_ITERATIONS - numFailures);
            // Now figure out the distance outliers and mark them in the distance inclusion map
            int validDistances = 0;
            for (int i = 0; i < (NUM_OF_RTT_ITERATIONS - numFailures); i++) {
                if (distanceMms[i] - variationLimit < distanceAvg) {
                    // Distances that are in range for the distribution are included in the map
                    distanceInclusionMap[i] = true;
                    validDistances++;
                } else {
                    // Distances that are out of range for the distribution are excluded in the map
                    distanceInclusionMap[i] = false;
                }
            }

            assertTrue("After fails+outlier removal greater that 50% distances must remain: "
                    + NUM_OF_RTT_ITERATIONS / 2, validDistances > NUM_OF_RTT_ITERATIONS / 2);

            // Remove the distance outliers and find the new average, min and max.
            distanceSum = 0;
            distanceMax = Integer.MIN_VALUE;
            distanceMin = Integer.MAX_VALUE;
            for (int i = 0; i < (NUM_OF_RTT_ITERATIONS - numFailures); i++) {
                if (distanceInclusionMap[i]) {
                    distanceSum += distanceMms[i];
                    distanceMin = Math.min(distanceMin, distanceMms[i]);
                    distanceMax = Math.max(distanceMax, distanceMms[i]);
                }
            }
            distanceAvg = (double) distanceSum / validDistances;
            assertTrue("Wi-Fi RTT: Variation (max direction) exceeds threshold, Variation ="
                            + (distanceMax - distanceAvg),
                    (distanceMax - distanceAvg) <= variationLimit);
            assertTrue("Wi-Fi RTT: Variation (min direction) exceeds threshold, Variation ="
                            + (distanceAvg - distanceMin),
                    (distanceAvg - distanceMin) <= variationLimit);
            for (int i = 0; i < numGoodResults; ++i) {
                assertNotEquals("Number of attempted measurements is 0", 0, numAttempted[i]);
                assertNotEquals("Number of successful measurements is 0", 0, numSuccessful[i]);
            }
        }

    }

    /**
     * Test RangingResult.Builder
     */
    @RequiresFlagsEnabled(Flags.FLAG_ANDROID_V_WIFI_API)
    @Test
    @ApiTest(apis = { "android.net.wifi.rtt.RangingResult.Builder#setMacAddress",
            "android.net.wifi.rtt.RangingResult.Builder#setPeerHandle",
            "android.net.wifi.rtt.RangingResult.Builder#setStatus",
            "android.net.wifi.rtt.RangingResult.Builder#setDistanceMm",
            "android.net.wifi.rtt.RangingResult.Builder#setDistanceStdDevMm",
            "android.net.wifi.rtt.RangingResult.Builder#setLci",
            "android.net.wifi.rtt.RangingResult.Builder#setLcr",
            "android.net.wifi.rtt.RangingResult.Builder#setNumAttemptedMeasurements",
            "android.net.wifi.rtt.RangingResult.Builder#setNumSuccessfulMeasurements",
            "android.net.wifi.rtt.RangingResult.Builder#setRangingTimestampMillis",
            "android.net.wifi.rtt.RangingResult.Builder#setRssi",
            "android.net.wifi.rtt.RangingResult.Builder#setMeasurementChannelFrequencyMHz",
            "android.net.wifi.rtt.RangingResult.Builder#setMeasurementBandwidth",
            "android.net.wifi.rtt.RangingResult.Builder#set80211azNtbMeasurement",
            "android.net.wifi.rtt.RangingResult.Builder#set80211mcMeasurement",
            "android.net.wifi.rtt.RangingResult.Builder#set80211azInitiatorTxLtfRepetitionsCount",
            "android.net.wifi.rtt.RangingResult.Builder#set80211azResponderTxLtfRepetitionsCount",
            "android.net.wifi.rtt.RangingResult.Builder#set80211azNumberOfRxSpatialStreams",
            "android.net.wifi.rtt.RangingResult.Builder#set80211azNumberOfTxSpatialStreams",
            "android.net.wifi.rtt.RangingResult.Builder#setMinTimeBetweenNtbMeasurementsMicros",
            "android.net.wifi.rtt.RangingResult.Builder#setMaxTimeBetweenNtbMeasurementsMicros",
            "android.net.wifi.rtt.RangingResult.Builder#setUnverifiedResponderLocation",
            "android.net.wifi.rtt.RangingResult#Builder"})
    public void testRangingResultBuilder() {
        byte[] lci = {1, 2, 3, 4};
        byte[] lcr = {10, 20, 30, 40};
        RangingResult rangingResult = new RangingResult.Builder()
                .setMacAddress(MacAddress.fromString("00:11:22:33:44:55"))
                .setPeerHandle(null)
                .setStatus(RangingResult.STATUS_SUCCESS)
                .setDistanceMm(100)
                .setDistanceStdDevMm(33)
                .setLci(lci)
                .setLcr(lcr)
                .setNumAttemptedMeasurements(10)
                .setNumSuccessfulMeasurements(5)
                .setRangingTimestampMillis(12345)
                .setRssi(-77)
                .setMeasurementChannelFrequencyMHz(5180)
                .setMeasurementBandwidth(ScanResult.CHANNEL_WIDTH_40MHZ)
                .set80211azNtbMeasurement(true)
                .set80211mcMeasurement(false)
                .set80211azInitiatorTxLtfRepetitionsCount(2)
                .set80211azResponderTxLtfRepetitionsCount(1)
                .set80211azNumberOfRxSpatialStreams(2)
                .set80211azNumberOfTxSpatialStreams(1)
                .setMinTimeBetweenNtbMeasurementsMicros(1000)
                .setMaxTimeBetweenNtbMeasurementsMicros(10000)
                .setUnverifiedResponderLocation(null)
                .build();

        assertEquals(MacAddress.fromString("00:11:22:33:44:55"), rangingResult.getMacAddress());
        assertEquals(null, rangingResult.getPeerHandle());
        assertEquals(RangingResult.STATUS_SUCCESS, rangingResult.getStatus());
        assertEquals(100, rangingResult.getDistanceMm());
        assertEquals(33, rangingResult.getDistanceStdDevMm());
        assertArrayEquals(lci, rangingResult.getLci());
        assertArrayEquals(lcr, rangingResult.getLcr());
        assertEquals(10, rangingResult.getNumAttemptedMeasurements());
        assertEquals(5, rangingResult.getNumSuccessfulMeasurements());
        assertEquals(12345, rangingResult.getRangingTimestampMillis());
        assertEquals(-77, rangingResult.getRssi());
        assertEquals(5180, rangingResult.getMeasurementChannelFrequencyMHz());
        assertEquals(ScanResult.CHANNEL_WIDTH_40MHZ, rangingResult.getMeasurementBandwidth());
        assertTrue(rangingResult.is80211azNtbMeasurement());
        assertFalse(rangingResult.is80211mcMeasurement());
        assertEquals(2, rangingResult.get80211azInitiatorTxLtfRepetitionsCount());
        assertEquals(1, rangingResult.get80211azResponderTxLtfRepetitionsCount());
        assertEquals(2, rangingResult.get80211azNumberOfRxSpatialStreams());
        assertEquals(1, rangingResult.get80211azNumberOfTxSpatialStreams());
        assertEquals(1000, rangingResult.getMinTimeBetweenNtbMeasurementsMicros());
        assertEquals(10000, rangingResult.getMaxTimeBetweenNtbMeasurementsMicros());
        assertEquals(null, rangingResult.getUnverifiedResponderLocation());
        try {
            rangingResult = new RangingResult.Builder()
                    .setStatus(RangingResult.STATUS_SUCCESS)
                    .setDistanceMm(100)
                    .setDistanceStdDevMm(33)
                    .build();
            assertEquals(RangingResult.STATUS_SUCCESS, rangingResult.getStatus());
            fail("RangeResult need MAC address or Peer handle");
        } catch (IllegalArgumentException e) {

        }
    }

    /** Test Secure RangingResult.Builder */
    @RequiresFlagsEnabled(Flags.FLAG_SECURE_RANGING)
    @Test
    @ApiTest(
            apis = {
                "android.net.wifi.rtt.RangingResult.Builder#setRangingFrameProtected",
                "android.net.wifi.rtt.RangingResult.Builder#setRangingAuthenticated",
                "android.net.wifi.rtt.RangingResult.Builder#setSecureHeLtfEnabled",
                "android.net.wifi.rtt.RangingResult.Builder#setSecureHeLtfProtocolVersion",
                "android.net.wifi.rtt.RangingResult.Builder#setPasnComebackCookie",
                "android.net.wifi.rtt.RangingResult.Builder#setPasnComebackAfterMillis",
                "android.net.wifi.rtt.RangingResult#isRangingFrameProtected",
                "android.net.wifi.rtt.RangingResult#isRangingAuthenticated",
                "android.net.wifi.rtt.RangingResult#isSecureHeLtfEnabled",
                "android.net.wifi.rtt.RangingResult#getPasnComebackCookie",
                "android.net.wifi.rtt.RangingResult#getPasnComebackAfterMillis"
            })
    public void testSecureRangingResultBuilder() {
        RangingResult rangingResult =
                new RangingResult.Builder()
                        .setMacAddress(MacAddress.fromString("00:11:22:33:44:55"))
                        .setRangingFrameProtected(true)
                        .setRangingAuthenticated(true)
                        .setSecureHeLtfEnabled(true)
                        .setSecureHeLtfProtocolVersion(1)
                        .setPasnComebackCookie(TEST_PASN_COMEBACK_COOKIE)
                        .setPasnComebackAfterMillis(INTERVAL_MS)
                        .build();

        assertEquals(MacAddress.fromString("00:11:22:33:44:55"), rangingResult.getMacAddress());
        assertTrue(rangingResult.isRangingFrameProtected());
        assertTrue(rangingResult.isRangingAuthenticated());
        assertTrue(rangingResult.isSecureHeLtfEnabled());
        assertEquals(1, rangingResult.getSecureHeLtfProtocolVersion());
        assertArrayEquals(TEST_PASN_COMEBACK_COOKIE, rangingResult.getPasnComebackCookie());
        assertEquals(INTERVAL_MS, rangingResult.getPasnComebackAfterMillis());
    }

    /**
     * Test Wi-Fi RTT ranging operation using ScanResults in request:
     * - Scan for visible APs for the test AP (which is validated to support IEEE 802.11az)
     * - Perform N (constant) RTT operations
     * - Validate:
     *   - Failure ratio < threshold (constant)
     *   - Result margin < threshold (constant)
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ANDROID_V_WIFI_API)
    public void testRangingToTest11azApUsingScanResult() throws InterruptedException {
        assumeTrue(mCharacteristics != null && mCharacteristics.getBoolean(
                WifiRttManager.CHARACTERISTICS_KEY_BOOLEAN_NTB_INITIATOR));
        ScanResult testAp = getS11AzScanResult();
        assertNotNull("Cannot find any test APs which support RTT / IEEE 802.11az"
                + " - please verify that your test setup includes them!", testAp);
        RangingRequest.Builder builder = new RangingRequest.Builder();
        builder.addAccessPoint(testAp);
        RangingRequest request = builder.build();
        range11azApRequest(request, testAp, false);
    }

    /*
     * Test that vendor data can be set and retrieved properly in RangingRequest and RangingResult.
     */
    @RequiresFlagsEnabled(Flags.FLAG_ANDROID_V_WIFI_API)
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM,
            codeName = "VanillaIceCream")
    @Test
    public void testRangingRequestVendorData() {
        // Default value should be an empty list
        RangingRequest emptyRequest = new RangingRequest.Builder().build();
        assertNotNull(emptyRequest.getVendorData());
        assertTrue(emptyRequest.getVendorData().isEmpty());

        RangingResult emptyResult = new RangingResult.Builder().setMacAddress(MAC).build();
        assertNotNull(emptyResult.getVendorData());
        assertTrue(emptyResult.getVendorData().isEmpty());

        // Set and get vendor data
        OuiKeyedData vendorDataElement =
                new OuiKeyedData.Builder(0x00aabbcc, new PersistableBundle()).build();
        List<OuiKeyedData> vendorData = Arrays.asList(vendorDataElement);

        RangingRequest requestWithData = new RangingRequest.Builder()
                .setVendorData(vendorData)
                .build();
        assertTrue(vendorData.equals(requestWithData.getVendorData()));

        RangingResult resultWithData = new RangingResult.Builder()
                .setMacAddress(MAC)
                .setVendorData(vendorData)
                .build();
        assertTrue(vendorData.equals(resultWithData.getVendorData()));
    }

    /**
     * Test Wi-Fi RTT ranging using ResponderConfig in the single responder RangingRequest API.
     * - Scan for visible APs for the test AP (which is validated to support IEEE 802.11az)
     * - Perform N (constant) RTT operations
     * - Validate:
     *   - Failure ratio < threshold (constant)
     *   - Result margin < threshold (constant)
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ANDROID_V_WIFI_API)
    @ApiTest(apis = {"android.net.wifi.rtt.ResponderConfig.Builder#set80211azNtbSupported",
            "android.net.wifi.rtt.ResponderConfig#is80211azNtbSupported"})
    public void testRangingToTest11azApUsingResponderConfig() throws InterruptedException {
        assumeTrue(mCharacteristics != null && mCharacteristics.getBoolean(
                WifiRttManager.CHARACTERISTICS_KEY_BOOLEAN_NTB_INITIATOR));
        // Scan for IEEE 802.11az supporting APs
        ScanResult testAp = getS11AzScanResult();
        assertNotNull(
                "Cannot find any test APs which support RTT / IEEE 802.11az - please verify that "
                        + "your test setup includes them!", testAp);
        int preamble = ResponderConfig.fromScanResult(testAp).getPreamble();

        // Create a ResponderConfig from the builder API.
        ResponderConfig.Builder responderBuilder = new ResponderConfig.Builder();
        ResponderConfig responder = responderBuilder
                .setMacAddress(MacAddress.fromString(testAp.BSSID))
                .set80211azNtbSupported(testAp.is80211azNtbResponder())
                .setChannelWidth(testAp.channelWidth)
                .setFrequencyMhz(testAp.frequency)
                .setCenterFreq0Mhz(testAp.centerFreq0)
                .setCenterFreq1Mhz(testAp.centerFreq1)
                .setPreamble(preamble)
                .setResponderType(RESPONDER_AP)
                .build();

        // Validate ResponderConfig.Builder set method arguments match getter methods.
        assertTrue(responder.getMacAddress().toString().equalsIgnoreCase(testAp.BSSID)
                && responder.is80211azNtbSupported() == testAp.is80211azNtbResponder()
                && responder.getChannelWidth() == testAp.channelWidth
                && responder.getFrequencyMhz() == testAp.frequency
                && responder.getCenterFreq0Mhz() == testAp.centerFreq0
                && responder.getCenterFreq1Mhz() == testAp.centerFreq1
                && responder.getPreamble() == preamble
                && responder.getResponderType() == RESPONDER_AP);

        // Perform RTT operations
        RangingRequest.Builder builder = new RangingRequest.Builder();
        builder.addResponder(responder);

        RangingRequest request = builder.build();

        if (WifiBuildCompat.isPlatformOrWifiModuleAtLeastS(getContext())) {
            assertEquals(1, request.getRttResponders().size());
        }
        range11azApRequest(request, testAp, false);
    }

    /**
     * Test Wi-Fi RTT secure ranging operation using ScanResults in request:
     * - Scan for visible APs for the test AP (which is validated to support IEEE 802.11az secure
     * ranging)
     * - Perform N (constant) RTT operations
     * - Validate:
     *   - Failure ratio < threshold (constant)
     *   - Result margin < threshold (constant)
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_SECURE_RANGING)
    @ApiTest(
            apis = {
                "android.net.wifi.rtt.RangingRequest#getSecurityMode",
                "android.net.wifi.rtt.ResponderConfig.Builder#set80211azNtbSupported",
                "android.net.wifi.rtt.ResponderConfig#is80211azNtbSupported",
                "android.net.wifi.rtt.ResponderConfig#getSecureRangingConfig",
                "android.net.wifi.ScanResult#isRangingFrameProtectionRequired",
                "android.net.wifi.rtt.PasnConfig#Builder",
                "android.net.wifi.rtt.PasnConfig.Builder#setWifiSsid",
                "android.net.wifi.rtt.PasnConfig.Builder#setPassword",
                "android.net.wifi.rtt.PasnConfig.Builder#build",
                "android.net.wifi.rtt.SecureRangingConfig#Builder",
                "android.net.wifi.rtt.SecureRangingConfig.Builder#setSecureHeLtfEnabled",
                "android.net.wifi.rtt.SecureRangingConfig.Builder#setRangingFrameProtectionEnabled",
                "android.net.wifi.rtt.SecureRangingConfig.Builder#build",
                "android.net.wifi.rtt.SecureRangingConfig.Builder#setSecureRangingConfig"
            })
    public void testSecureRangingToTest11azApUsingScanResult() throws InterruptedException {
        // Check Device capabilities
        assumeNotNull(mCharacteristics);
        assumeTrue(mCharacteristics.getBoolean(
                WifiRttManager.CHARACTERISTICS_KEY_BOOLEAN_NTB_INITIATOR));
        assumeTrue(mCharacteristics.getBoolean(
                WifiRttManager.CHARACTERISTICS_KEY_BOOLEAN_RANGING_FRAME_PROTECTION_SUPPORTED));
        assumeTrue(mCharacteristics.getBoolean(
                WifiRttManager.CHARACTERISTICS_KEY_BOOLEAN_SECURE_HE_LTF_SUPPORTED));
        assertTrue(mCharacteristics.getInt(
                WifiRttManager.CHARACTERISTICS_KEY_INT_MAX_SUPPORTED_SECURE_HE_LTF_PROTO_VERSION)
                >= 0);

        // Check for responder
        ScanResult testAp = getS11AzSecureScanResult();
        assertNotNull("Cannot find any test APs which support IEEE 802.11az Secure Ranging"
                + " - please verify that your test setup includes them!", testAp);
        RangingRequest.Builder builder = new RangingRequest.Builder();
        builder.addAccessPoint(testAp);
        RangingRequest request = builder.build();

        // Validate responder configuration
        assertEquals(1, request.getRttResponders().size());
        // check security mode is opportunistic by default
        assertEquals(RangingRequest.SECURITY_MODE_OPPORTUNISTIC, request.getSecurityMode());
        ResponderConfig responderConfig = request.getRttResponders().getFirst();
        SecureRangingConfig secureRangingConfig = responderConfig.getSecureRangingConfig();
        assertNotNull(secureRangingConfig);
        assertTrue(secureRangingConfig.isSecureHeLtfEnabled());
        assumeTrue(secureRangingConfig.isRangingFrameProtectionEnabled());
        PasnConfig pasnConfig = secureRangingConfig.getPasnConfig();
        assertNotNull(pasnConfig);
        assertTrue(pasnConfig.getBaseAkms() != PasnConfig.AKM_NONE);
        assertTrue(pasnConfig.getCiphers() != PasnConfig.CIPHER_NONE);
        assertNotNull(pasnConfig.getWifiSsid());

        range11azApRequest(request, testAp, true);
    }

    /**
     * Test secure ranging config builder. This also includes PASN config builder as PASN config is
     * a required configuration for secure ranging config.
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_SECURE_RANGING)
    @ApiTest(
            apis = {
                "android.net.wifi.rtt.PasnConfig#Builder",
                "android.net.wifi.rtt.PasnConfig.Builder#setWifiSsid",
                "android.net.wifi.rtt.PasnConfig.Builder#setPassword",
                "android.net.wifi.rtt.PasnConfig.Builder#build",
                "android.net.wifi.rtt.PasnConfig#getWifiSsid",
                "android.net.wifi.rtt.PasnConfig#getPassword",
                "android.net.wifi.rtt.PasnConfig#getCiphers",
                "android.net.wifi.rtt.PasnConfig#getBaseAkms",
                "android.net.wifi.rtt.PasnConfig#getPasnComebackCookie",
                "android.net.wifi.rtt.SecureRangingConfig#Builder",
                "android.net.wifi.rtt.SecureRangingConfig.Builder#setSecureHeLtfEnabled",
                "android.net.wifi.rtt.SecureRangingConfig.Builder#setRangingFrameProtectionEnabled",
                "android.net.wifi.rtt.SecureRangingConfig.Builder#build",
                "android.net.wifi.rtt.SecureRangingConfig#getPasnConfig",
                "android.net.wifi.rtt.SecureRangingConfig#isSecureHeLtfEnabled",
                "android.net.wifi.rtt.SecureRangingConfig#isRangingFrameProtectionEnabled",
            })
    public void testSecureRangingConfigBuilder() {
        PasnConfig pasnConfig =
                new PasnConfig.Builder(AKM_SAE | AKM_PASN, CIPHER_GCMP_256)
                        .setWifiSsid(WifiSsid.fromBytes(TEST_SSID.getBytes(StandardCharsets.UTF_8)))
                        .setPassword(TEST_PASSWORD)
                        .setPasnComebackCookie(TEST_PASN_COMEBACK_COOKIE)
                        .build();
        SecureRangingConfig secureRangingConfig =
                new SecureRangingConfig.Builder(pasnConfig)
                        .setSecureHeLtfEnabled(true)
                        .setRangingFrameProtectionEnabled(true)
                        .build();
        assertTrue(secureRangingConfig.isSecureHeLtfEnabled());
        assertTrue(secureRangingConfig.isRangingFrameProtectionEnabled());
        assertEquals(
                WifiSsid.fromBytes(TEST_SSID.getBytes(StandardCharsets.UTF_8)),
                pasnConfig.getWifiSsid());
        assertEquals(TEST_PASSWORD, pasnConfig.getPassword());
        assertEquals(CIPHER_GCMP_256, pasnConfig.getCiphers());
        assertEquals(AKM_SAE | AKM_PASN, pasnConfig.getBaseAkms());
        assertArrayEquals(TEST_PASN_COMEBACK_COOKIE, pasnConfig.getPasnComebackCookie());
    }

    /**
     * Test the copy constructor of {@link ResponderConfig.Builder}. Verifies that all fields from
     * an existing {@link ResponderConfig} are correctly copied to a new {@link ResponderConfig}
     * instance created via the builder's copy constructor. Also checks that modifications to the
     * new builder do not affect the original configuration.
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_IMPROVE_RANGING_API)
    @ApiTest(
            apis = {
                "android.net.wifi.rtt.ResponderConfig#Builder",
                "android.net.wifi.rtt.ResponderConfig.Builder#setPeerHandle",
                "android.net.wifi.rtt.ResponderConfig#getPeerHandle"
            })
    public void testResponderConfigBuilderCopyConstructor() {
        // 1. Create an original ResponderConfig with specific values
        MacAddress originalMac = MacAddress.fromString("A0:B1:C2:D3:E4:F5");
        PasnConfig pasnConfig =
                new PasnConfig.Builder(PasnConfig.AKM_SAE, PasnConfig.CIPHER_GCMP_256)
                        .setWifiSsid(
                                WifiSsid.fromBytes("TestSSID".getBytes(StandardCharsets.UTF_8)))
                        .setPassword("TestPassword")
                        .build();
        SecureRangingConfig originalSecureConfig =
                new SecureRangingConfig.Builder(pasnConfig)
                        .setSecureHeLtfEnabled(true)
                        .setRangingFrameProtectionEnabled(true)
                        .build();

        ResponderConfig originalConfig =
                new ResponderConfig.Builder()
                        .setMacAddress(originalMac)
                        .setResponderType(ResponderConfig.RESPONDER_AP)
                        .set80211mcSupported(true)
                        .set80211azNtbSupported(false) // Test a mix of true/false
                        .setChannelWidth(ScanResult.CHANNEL_WIDTH_40MHZ)
                        .setFrequencyMhz(2437)
                        .setCenterFreq0Mhz(2437)
                        .setCenterFreq1Mhz(0)
                        .setPreamble(ScanResult.PREAMBLE_HT)
                        .setSecureRangingConfig(originalSecureConfig)
                        .build();

        // 2. Use the copy constructor in ResponderConfig.Builder
        ResponderConfig.Builder copiedBuilder = new ResponderConfig.Builder(originalConfig);

        // 3. Build the new ResponderConfig from the copied builder
        ResponderConfig copiedConfig = copiedBuilder.build();

        // 4. Assert that all fields are equal between originalConfig and copiedConfig
        assertTrue(copiedConfig.equals(originalConfig));

        // 5. Test that modifying the copiedBuilder does not affect the originalConfig
        MacAddress newMac = MacAddress.fromString("FF:EE:DD:CC:BB:AA");
        copiedBuilder
                .setMacAddress(newMac)
                .setFrequencyMhz(5200)
                .setPreamble(PREAMBLE_HE)
                .set80211azNtbSupported(true);

        ResponderConfig modifiedFromCopiedBuilder = copiedBuilder.build();

        // Assert changes in the newly built config
        assertEquals(
                "MAC address should be updated in modified config",
                newMac,
                modifiedFromCopiedBuilder.getMacAddress());
        assertEquals(
                "Frequency should be updated in modified config",
                5200,
                modifiedFromCopiedBuilder.getFrequencyMhz());
        assertTrue(
                "802.11az NTB support should be updated in modified config",
                modifiedFromCopiedBuilder.is80211azNtbSupported());
        assertEquals(
                "Preamble should be updated in modified config",
                PREAMBLE_HE,
                modifiedFromCopiedBuilder.getPreamble());

        // Assert that originalConfig remains unchanged
        assertEquals(
                "Original MAC address should NOT change",
                originalMac,
                originalConfig.getMacAddress());
        assertEquals(
                "Original frequency should NOT change", 2437, originalConfig.getFrequencyMhz());
        assertFalse(
                "Original 802.11az NTB support should NOT change",
                originalConfig.is80211azNtbSupported());
        assertEquals(
                "Original preamble should NOT change",
                ScanResult.PREAMBLE_HT,
                originalConfig.getPreamble());

        // 6. Test with Aware PeerHandle
        PeerHandle peerHandle = mock(PeerHandle.class);
        originalConfig =
                new ResponderConfig.Builder()
                        .setPeerHandle(peerHandle)
                        .setResponderType(RESPONDER_AWARE)
                        .build();
        copiedBuilder = new ResponderConfig.Builder(originalConfig);
        copiedConfig = copiedBuilder.build();
        assertTrue(copiedConfig.equals(originalConfig));
    }

    /** Test RangingResult with STATUS_BUSY_TRY_LATER. */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_RTT_BUSY_TRY_LATER_API)
    public void testRangingResultBusyTryLaterStatus() {
        final int retryAfterDurationMillis = 1000;
        RangingResult rangingResult =
                new RangingResult.Builder()
                        .setMacAddress(MacAddress.fromString("00:11:22:33:44:55"))
                        .setStatus(RangingResult.STATUS_BUSY_TRY_LATER)
                        .setRetryAfterDurationMillis(retryAfterDurationMillis)
                        .build();

        assertEquals(RangingResult.STATUS_BUSY_TRY_LATER, rangingResult.getStatus());
        assertEquals(retryAfterDurationMillis, rangingResult.getRetryAfterDurationMillis());

        try {
            rangingResult.getDistanceMm();
            fail("getDistanceMm should throw IllegalStateException for STATUS_BUSY_TRY_LATER");
        } catch (IllegalStateException e) {
            // expected
        }

        try {
            rangingResult.getDistanceStdDevMm();
            fail(
                    "getDistanceStdDevMm should throw IllegalStateException for "
                            + "STATUS_BUSY_TRY_LATER");
        } catch (IllegalStateException e) {
            // expected
        }

        try {
            rangingResult.getRssi();
            fail("getRssi should throw IllegalStateException for STATUS_BUSY_TRY_LATER");
        } catch (IllegalStateException e) {
            // expected
        }

        RangingResult successResult =
                new RangingResult.Builder()
                        .setMacAddress(MacAddress.fromString("00:11:22:33:44:55"))
                        .setStatus(RangingResult.STATUS_SUCCESS)
                        .build();
        try {
            successResult.getRetryAfterDurationMillis();
            fail(
                    "getRetryAfterDurationMillis should throw IllegalStateException for non-busy "
                            + "status");
        } catch (IllegalStateException e) {
            // expected
        }
    }

    /**
     * Test Wi-Fi RTT 11az ranging operation against the conservative error model.
     *
     * The test performs the following steps:
     * - Scan for visible APs for the test AP (which is validated to support IEEE 802.11az).
     * - Perform N (constant) RTT operations.
     * - For each successful result, calculate the maximum allowed standard deviation using the
     * formula from go/rtt-11az-error-guide.
     * - Validate that the reported Standard Deviation (result.getDistanceStdDevMm()) is less than
     * or equal to the calculated model error.
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ANDROID_V_WIFI_API)
    public void testRangingToTest11azApErrorModel() throws InterruptedException {
        assumeTrue(mCharacteristics != null && mCharacteristics.getBoolean(
                WifiRttManager.CHARACTERISTICS_KEY_BOOLEAN_NTB_INITIATOR));
        ScanResult testAp = getS11AzScanResult();
        assertNotNull("Cannot find any test APs which support RTT / IEEE 802.11az"
                + " - please verify that your test setup includes them!", testAp);
        RangingRequest.Builder builder = new RangingRequest.Builder();
        builder.addAccessPoint(testAp);
        RangingRequest request = builder.build();

        for (int i = 0; i < NUM_OF_RTT_ITERATIONS; ++i) {
            ResultCallback callback = new ResultCallback();
            mWifiRttManager.startRanging(request, mExecutor, callback);
            assertTrue("Wi-Fi RTT results: no callback on iteration " + i,
                    callback.waitForCallback());

            List<RangingResult> currentResults = callback.getResults();
            assertNotNull("Wi-Fi RTT results: null results (onRangingFailure) on iteration " + i,
                    currentResults);
            assertEquals("Wi-Fi RTT results: unexpected # of results (expect 1) on iteration " + i,
                    1, currentResults.size());
            RangingResult result = currentResults.get(0);
            assertEquals("Wi-Fi RTT results: invalid result (wrong BSSID) entry on iteration " + i,
                    result.getMacAddress().toString(), testAp.BSSID);

            if (result.getStatus() == RangingResult.STATUS_SUCCESS) {
                assertTrue("Wi-Fi RTT results: should be a 802.11az measurement",
                        result.is80211azNtbMeasurement());

                int bandwidth = result.getMeasurementBandwidth();
                int numTxStreams = result.get80211azNumberOfTxSpatialStreams();
                int numRxStreams = result.get80211azNumberOfRxSpatialStreams();
                int pathDiversity = numTxStreams * numRxStreams;
                assertTrue("Path diversity must be positive", pathDiversity > 0);
                if (pathDiversity <= 2) {
                    Log.w(TAG, "Low path diversity (" + pathDiversity
                            + "), may not be sufficient for good multipath mitigation.");
                }

                // Convert channel width to MHz
                double b = switch (bandwidth) {
                    case ScanResult.CHANNEL_WIDTH_20MHZ -> 20;
                    case ScanResult.CHANNEL_WIDTH_40MHZ -> 40;
                    case ScanResult.CHANNEL_WIDTH_80MHZ -> 80;
                    case ScanResult.CHANNEL_WIDTH_160MHZ -> 160;
                    case ScanResult.CHANNEL_WIDTH_320MHZ -> 320;
                    default -> {
                        Log.e(TAG, "Unknown bandwidth: " + bandwidth);
                        yield -1.0; // Indicate error
                    }
                };

                assertTrue("Unknown bandwidth received: " + bandwidth, b > 0);

                // Fixed parameters from the guide
                double s = 100.0;
                double m = 0.8;
                double q = 0.5;
                double p = (double) pathDiversity;

                // Model formula components
                double q_val = 0.41 / q;
                double b_val = (-23198.38 / (b * b * b)) + (2098.56 / (b * b)) + (38.29 / b) + 0.46;
                double s_val = 1.0 / Math.sqrt(s / 100.0);
                double p_val = Math.pow(0.75 + 0.25 * (1.0 - m), Math.log(p) / Math.log(2));

                double modeledErrorMeters = 1.5 * q_val * b_val * s_val * p_val;
                double modeledErrorMm = modeledErrorMeters * 1000.0;

                int reportedStdDevMm = result.getDistanceStdDevMm();
                assertTrue("Reported Standard Deviation should be positive, but was "
                        + reportedStdDevMm, reportedStdDevMm > 0);

                Log.d(TAG, "Iteration " + i + ": Bandwidth=" + b + "MHz, PathDiversity=" + p
                        + ", ReportedStdDevMm=" + reportedStdDevMm + ", ModeledErrorMm="
                        + String.format("%.2f", modeledErrorMm));

                assertTrue("Reported Standard Deviation (" + reportedStdDevMm
                        + " mm) exceeds the calculated model error (" + String.format("%.2f",
                        modeledErrorMm) + " mm)", reportedStdDevMm <= modeledErrorMm);
            }
            Thread.sleep(INTERVAL_MS);
        }
    }


    /** Test setProximityDetectionDeviceName API */
    @SdkSuppress(minSdkVersion = 37)
    @RequiresFlagsEnabled(Flags.FLAG_PROXIMITY_RANGING)
    @Test
    @ApiTest(
            apis = {
                "android.net.wifi.rtt.WifiRttManager#setProximityDetectionDeviceName",
                "android.net.wifi.rtt.WifiRttManager#getProximityDetectionCharacteristics",
                "android.net.wifi.rtt"
                        + ".ProximityDetectionCharacteristics#getProximityDetectionDeviceName"
            })
    public void testSetProximityDetectionDeviceName() {
        // Check for Proximity Detection support
        if (mProximityDetectionCharacteristics == null) {
            Log.d(TAG, "Skipping test as Proximity Detection is not supported");
            return;
        }
        ProximityDetectionCharacteristics pdCharacteristics =
                mWifiRttManager.getProximityDetectionCharacteristics();
        assertNotNull("Proximity detection characteristics should not be null", pdCharacteristics);
        String originalDeviceName = pdCharacteristics.getProximityDetectionDeviceName();
        assertNotNull("Proximity detection device name should not be null", originalDeviceName);

        final String testDeviceName = "TestDeviceName123";
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> mWifiRttManager.setProximityDetectionDeviceName(testDeviceName));
        pdCharacteristics = mWifiRttManager.getProximityDetectionCharacteristics();
        assertNotNull(pdCharacteristics);
        assertEquals(testDeviceName, pdCharacteristics.getProximityDetectionDeviceName());

        // Restore the original device name
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> mWifiRttManager.setProximityDetectionDeviceName(originalDeviceName));
    }

    /**
     * Test getProximityDetectionRandomizedMacAddress API.
     *
     * <p>Verifies that a valid, locally-administered, unicast MAC address is returned when
     * Proximity Detection is supported.
     */
    @SdkSuppress(minSdkVersion = 37)
    @RequiresFlagsEnabled(Flags.FLAG_PROXIMITY_RANGING)
    @Test
    @ApiTest(
            apis = {
                "android.net.wifi.rtt.WifiRttManager#getProximityDetectionRandomizedMacAddress"
            })
    public void testGetProximityDetectionRandomizedMacAddress() {
        // Check for Proximity Detection support
        if (mProximityDetectionCharacteristics == null) {
            Log.d(TAG, "Skipping test as Proximity Detection is not supported");
            return;
        }

        // Call the API to get the randomized MAC address.
        MacAddress macAddress =
                ShellIdentityUtils.invokeWithShellPermissions(
                        () -> mWifiRttManager.getProximityDetectionRandomizedMacAddress());

        // Validate the returned MAC address.
        assertNotNull(
                "Randomized MAC address for Proximity Detection should not be null", macAddress);
        assertTrue(
                "The MAC address should be a locally-administered address.",
                macAddress.isLocallyAssigned());
        assertEquals(
                "The MAC address should be a unicast address (not multicast).",
                MacAddress.TYPE_UNICAST,
                macAddress.getAddressType());
    }

    /** Test that ProximityDetectionMacAddressCallback receives MacAddress changed callback. */
    @SdkSuppress(minSdkVersion = 37)
    @RequiresFlagsEnabled(Flags.FLAG_PROXIMITY_RANGING)
    @Test
    @ApiTest(
            apis = {
                "android.net.wifi.rtt.WifiRttManager#registerProximityDetectionMacAddressCallback",
                "android.net.wifi.rtt.WifiRttManager"
                        + "#unregisterProximityDetectionMacAddressCallback",
                "android.net.wifi.rtt.WifiRttManager.ProximityDetectionMacAddressCallback"
                        + "#onProximityDetectionMacAddressChanged"
            })
    public void testProximityDetectionMacAddressCallback() throws Exception {
        // Check for Proximity Detection support
        if (mProximityDetectionCharacteristics == null) {
            Log.d(TAG, "Skipping test as Proximity Detection is not supported");
            return;
        }

        synchronized (mLock) {
            Consumer<MacAddress> callback =
                    new Consumer<MacAddress>() {
                        @Override
                        public void accept(MacAddress macAddress) {
                            synchronized (mLock) {
                                mLock.notify();
                            }
                        }
                    };

            ShellIdentityUtils.invokeWithShellPermissions(
                    () ->
                            mWifiRttManager.registerProximityDetectionMacAddressCallback(
                                    mExecutor, callback));
            ShellIdentityUtils.invokeWithShellPermissions(
                    () -> mWifiRttManager.unregisterProximityDetectionMacAddressCallback(callback));
        }
    }

    /**
     * Test Wi-Fi RTT continuous ranging operation. - Build Responder configuration - Start
     * continuous RTT operations - Stop continuous RTT
     */
    @SdkSuppress(minSdkVersion = 37)
    @RequiresFlagsEnabled(Flags.FLAG_PROXIMITY_RANGING)
    @Test
    @ApiTest(
            apis = {
                "android.net.wifi.rtt.WifiRttManager#startContinuousRanging",
                "android.net.wifi.rtt.WifiRttManager#stopContinuousRanging",
                "android.net.wifi.rtt.PasnConfig.Builder#setPassword",
                "android.net.wifi.rtt.PasnConfig.Builder"
                        + "#setProximityDetectionSeekerDeviceIdentityKey",
                "android.net.wifi.rtt.SecureRangingConfig.Builder#setSecureHeLtfEnabled",
                "android.net.wifi.rtt.SecureRangingConfig.Builder"
                        + "#setRangingFrameProtectionEnabled",
                "android.net.wifi.rtt.ProximityDetectionConfig.Builder"
                        + "#setDiscoveryChannelFrequencyMhz",
                "android.net.wifi.rtt.ProximityDetectionConfig.Builder"
                        + "#setPreferredRangingChannelFrequencyMhz",
                "android.net.wifi.rtt.ProximityDetectionConfig.Builder"
                        + "#setContinuousRangingIntervalMillis",
                "android.net.wifi.rtt.ProximityDetectionConfig.Builder"
                        + "#setPreferredRangingMeasurementRole",
                "android.net.wifi.rtt.ProximityDetectionConfig.Builder#setIngressDistanceMm",
                "android.net.wifi.rtt.ProximityDetectionConfig.Builder#setEgressDistanceMm",
                "android.net.wifi.rtt.ResponderConfig.Builder#setMacAddress",
                "android.net.wifi.rtt.ResponderConfig.Builder#set80211azNtbSupported",
                "android.net.wifi.rtt.ResponderConfig.Builder#setPreamble",
                "android.net.wifi.rtt.ResponderConfig.Builder#setChannelWidth",
                "android.net.wifi.rtt.ResponderConfig.Builder#setResponderType",
                "android.net.wifi.rtt.ResponderConfig.Builder#setSecureRangingConfig",
                "android.net.wifi.rtt.ResponderConfig.Builder#setProximityDetectionConfig",
                "android.net.wifi.rtt.RangingRequest.Builder#setSecurityMode",
                "android.net.wifi.rtt.RangingRequest.Builder#addResponder"
            })
    public void testContinuousRanging() throws InterruptedException {
        // Proximity detection feature is the main user of continuous ranging.
        if (mProximityDetectionCharacteristics == null) {
            Log.d(TAG, "Skipping test as Proximity Detection is not supported");
            return;
        }

        // Create a ResponderConfig from the builder API.
        PasnConfig pasnConfig =
                new PasnConfig.Builder(PasnConfig.AKM_SAE, PasnConfig.CIPHER_GCMP_256)
                        .setPassword("TestPassword")
                        .setProximityDetectionSeekerDeviceIdentityKey(TEST_DEVICE_IDENTITY_KEY)
                        .build();
        SecureRangingConfig originalSecureConfig =
                new SecureRangingConfig.Builder(pasnConfig)
                        .setSecureHeLtfEnabled(true)
                        .setRangingFrameProtectionEnabled(true)
                        .build();
        ProximityDetectionConfig pdConfig =
                new ProximityDetectionConfig.Builder(RANGING_SERVICE_ROLE_SEEKER)
                        .setDiscoveryChannelFrequencyMhz(TEST_DISCOVERY_CHANNEL_FREQUENCY)
                        .setPreferredRangingChannelFrequencyMhz(
                                TEST_PREFERRED_RANGING_CHANNEL_FREQUENCY)
                        .setContinuousRangingIntervalMillis(TEST_RANGING_INTERVAL_MS)
                        .setPreferredRangingMeasurementRole(RANGING_MEASUREMENT_ROLE_ISTA)
                        .setIngressDistanceMm(0)
                        .setEgressDistanceMm(0)
                        .build();
        ResponderConfig.Builder responderBuilder = new ResponderConfig.Builder();
        ResponderConfig responder =
                responderBuilder
                        .setMacAddress(MAC)
                        .set80211azNtbSupported(true)
                        .setPreamble(PREAMBLE_HE)
                        .setChannelWidth(ScanResult.CHANNEL_WIDTH_80MHZ)
                        .setResponderType(RESPONDER_STA)
                        .setSecureRangingConfig(originalSecureConfig)
                        .setProximityDetectionConfig(pdConfig)
                        .build();

        RangingRequest.Builder builder = new RangingRequest.Builder();
        builder.setSecurityMode(RangingRequest.SECURITY_MODE_SECURE_AUTH);
        builder.addResponder(responder);
        RangingRequest secureRangingRequest = builder.build();

        ContinuousResultCallback callback = new ContinuousResultCallback();

        ShellIdentityUtils.invokeWithShellPermissions(
                () ->
                        mWifiRttManager.startContinuousRanging(
                                null, secureRangingRequest, mExecutor, callback));
        assertTrue("Wi-Fi RTT results: no callback ", callback.waitForCallback());
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> mWifiRttManager.stopContinuousRanging(null));
    }

    /** Test RangingResult for proximity Ranging */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_PROXIMITY_RANGING)
    @ApiTest(
            apis = {
                "android.net.wifi.rtt.RangingResult.Builder#setUsdPeerId",
                "android.net.wifi.rtt.RangingResult.Builder#setNominalTimeMillis",
                "android.net.wifi.rtt.RangingResult"
                        + ".Builder#setAvailabilityWindowDurationMillis",
                "android.net.wifi.rtt.RangingResult"
                        + ".Builder#setNumNtbRepetitionsPerMeasurement",
                "android.net.wifi.rtt.RangingResult.Builder#setDelayedLmrEnabled",
                "android.net.wifi.rtt.RangingResult#getUsdPeerId",
                "android.net.wifi.rtt.RangingResult#getNominalTimeMillis",
                "android.net.wifi.rtt.RangingResult#getAvailabilityWindowDurationMillis",
                "android.net.wifi.rtt.RangingResult#getNumNtbRepetitionsPerMeasurement",
                "android.net.wifi.rtt.RangingResult#isLmrDelayed"
            })
    public void testProximityRangingResultBuilder() {
        RangingResult rangingResult =
                new RangingResult.Builder()
                        .setStatus(RangingResult.STATUS_SUCCESS)
                        .setUsdPeerId(1)
                        .setNominalTimeMillis(250)
                        .setAvailabilityWindowDurationMillis(32)
                        .setNumNtbRepetitionsPerMeasurement(4)
                        .setLmrDelayed(true)
                        .build();

        assertEquals(1, rangingResult.getUsdPeerId());
        assertEquals(250, rangingResult.getNominalTimeMillis());
        assertEquals(32, rangingResult.getAvailabilityWindowDurationMillis());
        assertEquals(4, rangingResult.getNumNtbRepetitionsPerMeasurement());
        assertTrue(rangingResult.isLmrDelayed());
    }
}
