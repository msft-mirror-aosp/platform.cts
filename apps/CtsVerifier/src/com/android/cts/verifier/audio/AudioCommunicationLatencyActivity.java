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

package com.android.cts.verifier.audio;

import static com.android.cts.verifier.TestListActivity.sCurrentDisplayMode;
import static com.android.cts.verifier.TestListAdapter.setTestNameSuffix;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

import android.content.pm.PackageManager;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;
import com.android.cts.verifier.CtsVerifierReportLog;
import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;
import com.android.cts.verifier.audio.reportlog.TestStatus;

import com.google.common.collect.ImmutableList;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.DoubleStream;

/** CTS Verifier test to measure communication device switching latency. */
public class AudioCommunicationLatencyActivity extends PassFailButtons.Activity {
    private static final String TAG = "AudioCommunicationLatencyActivity";
    private static final Duration LATENCY_MUST_THRESHOLD = Duration.ofMillis(1500);
    private static final float LATENCY_MUST_REPORT = (float) LATENCY_MUST_THRESHOLD.toMillis();
    private static final Duration CALLBACK_WAIT_TIME = Duration.ofSeconds(2);
    private static final int NUM_TEST_RUNS = 10;
    // ReportLog Schema
    private static final String KEY_DEVICE_NAME = "device_name";
    private static final String KEY_DEVICE_TYPE = "device_type";
    private static final String KEY_NUM_TEST_RUNS = "num_test_runs";
    private static final String KEY_TEST_STATUS = "test_status";
    private static final String KEY_LATENCY_AVERAGE_MS = "latency_average_ms";
    private static final String KEY_LATENCY_MIN_MS = "latency_min_ms";
    private static final String KEY_LATENCY_MAX_MS = "latency_max_ms";
    private static final String KEY_STD_DEV_LATENCY_MS = "latency_std_dev_ms";
    private static final String KEY_AVG_THRESHOLD_MS = "latency_average_ms_threshold";
    private static final String KEY_LATENCY_RESULTS = "latency_results";
    private static final String KEY_OVERALL_STATUS = "overall_status";

    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();

    private AudioManager mAudioManager;
    private LinearLayout mDeviceListLayout;
    private List<AudioDeviceTestHost> mTestHosts;
    private AudioDeviceCallback mAudioDeviceCallback;
    private TextView mFinalResultsText;

    private static final ImmutableList<Integer> REQUIRED_DEVICES =
            ImmutableList.of(
                    AudioDeviceInfo.TYPE_USB_HEADSET,
                    AudioDeviceInfo.TYPE_WIRED_HEADSET,
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
                    AudioDeviceInfo.TYPE_BUILTIN_EARPIECE);

    // Enum for test result states.
    private enum TestResult {
        NOT_TESTED,
        RUNNING,
        PASS,
        FAIL,
        NOT_SUPPORTED
    }

    // Enum to control the test loop flow.
    private enum TestLoopAction {
        PROCEED,
        CONTINUE,
        BREAK_AND_PASS
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        setContentView(R.layout.audio_communication_latency);
        super.onCreate(savedInstanceState);
        setPassFailButtonClickListeners();
        setInfoResources(
                R.string.audio_communication_latency_test,
                R.string.audio_communication_latency_test_info,
                -1);
        getPassButton().setEnabled(false);

        mAudioManager = getSystemService(AudioManager.class);
        mDeviceListLayout = findViewById(R.id.device_list_layout);

        mFinalResultsText = findViewById(R.id.final_test_result_text);

        mAudioDeviceCallback = new TestAudioDeviceCallback();

        if (!hasValidCommunication()) {
            // Show a message that the test is not supported and enable the pass button.
            setTestResult(
                    null,
                    getString(R.string.audio_communication_no_support),
                    TestResult.NOT_SUPPORTED);
            getPassButton().setEnabled(true);
        } else {
            setupDeviceList();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mAudioManager.registerAudioDeviceCallback(mAudioDeviceCallback, mMainHandler);
        // Update device list on resume to reflect any changes while paused.
        setupDeviceList();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mAudioManager.unregisterAudioDeviceCallback(mAudioDeviceCallback);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mExecutor.shutdownNow(); // Shut down the executor when the activity is destroyed.
    }

    private void setupDeviceList() {
        if (mTestHosts == null) {
            mTestHosts = new ArrayList<>();
            for (int deviceType : REQUIRED_DEVICES) {
                mTestHosts.add(new AudioDeviceTestHost(deviceType));
            }

            mDeviceListLayout.removeAllViews();
            LayoutInflater inflater = getLayoutInflater();
            for (AudioDeviceTestHost host : mTestHosts) {
                View view = host.inflate(inflater, mDeviceListLayout);
                mDeviceListLayout.addView(view);
            }
        }

        Set<Integer> supportedDeviceTypes =
                mAudioManager.getSupportedDeviceTypes(AudioManager.GET_DEVICES_OUTPUTS);
        List<AudioDeviceInfo> availableDevices = mAudioManager.getAvailableCommunicationDevices();

        for (AudioDeviceTestHost host : mTestHosts) {
            DeviceData deviceData = host.mDeviceData;
            int deviceType = host.getDeviceType();

            boolean isSupported = supportedDeviceTypes.contains(deviceType);
            AudioDeviceInfo deviceInfo =
                    availableDevices.stream()
                            .filter(d -> d.getType() == deviceType)
                            .findFirst()
                            .orElse(null);
            boolean isAvailable = (deviceInfo != null);

            String name =
                    (deviceInfo != null)
                            ? getDescriptiveDeviceName(deviceInfo)
                            : getDeviceName(deviceType);

            deviceData.mName = name;
            deviceData.mIsSupported = isSupported;
            deviceData.mIsAvailable = isAvailable;
            if (!isSupported) {
                deviceData.mTestResult = TestResult.NOT_SUPPORTED;
            }
            host.updateView();
        }

        if (!hasMoreThanOneDeviceToTest()) {
            mDeviceListLayout.setVisibility(View.GONE);
        } else {
            mDeviceListLayout.setVisibility(View.VISIBLE);
        }
        updatePassButton(); // Update pass button state after setting up the device list.
    }

    private boolean hasMoreThanOneDeviceToTest() {
        return mTestHosts != null
                && mTestHosts.stream().filter(h -> h.mDeviceData.isSupported()).count() > 1;
    }

    private String getDeviceName(int deviceType) {
        return switch (deviceType) {
            case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER ->
                    getString(R.string.audio_communication_latency_device_speaker);
            case AudioDeviceInfo.TYPE_BUILTIN_EARPIECE ->
                    getString(R.string.audio_communication_latency_device_earpiece);
            case AudioDeviceInfo.TYPE_WIRED_HEADSET ->
                    getString(R.string.audio_communication_latency_device_wired_headset);
            case AudioDeviceInfo.TYPE_USB_HEADSET ->
                    getString(R.string.audio_communication_latency_device_usb_headset);
            default -> getString(R.string.audio_communication_latency_device_unknown);
        };
    }

    private String getDeviceInstruction(int deviceType) {
        return switch (deviceType) {
            case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER ->
                    getString(R.string.audio_communication_latency_device_instructions_speaker);
            case AudioDeviceInfo.TYPE_BUILTIN_EARPIECE ->
                    getString(R.string.audio_communication_latency_device_instructions_earpiece);
            case AudioDeviceInfo.TYPE_WIRED_HEADSET ->
                    getString(
                            R.string.audio_communication_latency_device_instructions_wired_headset);
            case AudioDeviceInfo.TYPE_USB_HEADSET ->
                    getString(R.string.audio_communication_latency_device_instructions_usb_headset);
            default -> "";
        };
    }

    private void runTest(AudioDeviceTestHost host) {
        DeviceData deviceData = host.mDeviceData;
        if (!deviceData.mIsSupported) {
            setTestResult(
                    host,
                    getString(R.string.audio_communication_latency_not_supported),
                    TestResult.NOT_SUPPORTED);
            return;
        }

        deviceData.mTestResult = TestResult.RUNNING;
        host.updateView();
        getPassButton().setEnabled(false);

        mExecutor.execute(
                () -> {
                    List<AudioDeviceInfo> availableDevices =
                            mAudioManager.getAvailableCommunicationDevices();
                    AudioDeviceInfo deviceToTest =
                            availableDevices.stream()
                                    .filter(d -> d.getType() == host.getDeviceType())
                                    .findFirst()
                                    .orElse(null);

                    if (deviceToTest == null) {
                        setTestResult(
                                host,
                                getString(
                                        R.string.audio_communication_latency_device_not_available),
                                TestResult.FAIL);
                        return;
                    }

                    StringBuilder results = new StringBuilder();
                    boolean success = testDevice(deviceData, deviceToTest, results);
                    runOnUiThread(
                            () -> {
                                deviceData.mTestResultText = results.toString();
                                deviceData.mTestResult =
                                        success ? TestResult.PASS : TestResult.FAIL;
                                host.updateView();
                                updatePassButton();
                            });
                });
    }

    /** Enables the pass button only if all device tests are completed (PASS or NOT_SUPPORTED). */
    private void updatePassButton() {
        // Auto pass if there is only one device or less since there is no communication switch
        if (!hasMoreThanOneDeviceToTest()) {
            getPassButton().setEnabled(true);
            mFinalResultsText.setText(getString(R.string.not_enought_devices_to_run));
            return;
        }

        boolean allTestsCompleted =
                mTestHosts.stream()
                        .allMatch(
                                host ->
                                        host.mDeviceData.mTestResult == TestResult.PASS
                                                || host.mDeviceData.mTestResult
                                                        == TestResult.NOT_SUPPORTED);

        getPassButton().setEnabled(allTestsCompleted);
        mFinalResultsText.setVisibility(View.GONE);
    }

    private void setTestResult(
            @Nullable AudioDeviceTestHost host, String message, TestResult result) {
        mMainHandler.post(
                () -> {
                    if (host != null) {
                        host.mDeviceData.mTestResultText = message;
                        host.mDeviceData.mTestResult = result;
                        host.updateView();
                    }
                    updatePassButton();
                });
    }

    private boolean switchCommunicationDeviceAndWait(
            AudioDeviceInfo device,
            int run,
            StringBuilder results,
            TestOnCommunicationDeviceChangedListener callback) {
        String deviceName = getDescriptiveDeviceName(device);
        callback.reset(device);

        try {
            if (!mAudioManager.setCommunicationDevice(device)) {
                results.append(getString(R.string.audio_communication_latency_run_failed, run))
                        .append("\n");
                return false;
            }

            if (!callback.waitForDevice()) {
                results.append(
                                getString(
                                        R.string.audio_communication_latency_run_timeout,
                                        run,
                                        deviceName))
                        .append("\n");
                return false;
            }

            AudioDeviceInfo receivedDevice = callback.getReceivedDevice();
            if (!device.equals(receivedDevice)) {
                results.append(
                                getString(
                                        R.string.audio_communication_latency_run_mismatch,
                                        run,
                                        deviceName,
                                        getDescriptiveDeviceName(receivedDevice)))
                        .append("\n");
                return false;
            }
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // Restore the interrupted status
            results.append(
                            getString(
                                    R.string.audio_communication_latency_run_exception,
                                    run,
                                    e.getMessage()))
                    .append("\n");
            return false;
        }
    }

    @Nullable
    private AudioDeviceInfo findFallbackDevice(
            AudioDeviceInfo currentDevice, List<AudioDeviceInfo> availableDevices) {
        int fallbackType =
                (currentDevice.getType() == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)
                        ? AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
                        : AudioDeviceInfo.TYPE_BUILTIN_SPEAKER;

        return availableDevices.stream()
                .filter(d -> d.getType() == fallbackType)
                .findFirst()
                .orElse(null);
    }

    private boolean switchToTemporaryDevice(
            AudioDeviceInfo deviceToTest,
            int run,
            StringBuilder results,
            TestOnCommunicationDeviceChangedListener callback) {
        List<AudioDeviceInfo> availableDevices = mAudioManager.getAvailableCommunicationDevices();
        AudioDeviceInfo fallbackDevice = findFallbackDevice(deviceToTest, availableDevices);

        if (fallbackDevice != null && !fallbackDevice.equals(deviceToTest)) {
            int fallbackStringId =
                    (fallbackDevice.getType() == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE)
                            ? R.string.audio_communication_latency_setting_earpiece
                            : R.string.audio_communication_latency_setting_speaker;
            results.append(getString(fallbackStringId)).append("\n");
            return switchCommunicationDeviceAndWait(fallbackDevice, run, results, callback);
        }
        return false; // Could not find or switch to a suitable fallback device.
    }

    private TestLoopAction performSingleTestRun(
            AudioDeviceInfo device,
            int run,
            StringBuilder results,
            TestOnCommunicationDeviceChangedListener callback,
            List<Long> latenciesMs) {
        String deviceName = getDescriptiveDeviceName(device);

        try {
            // If the device to test is already the current communication device,
            // switch to a temporary fallback device first.
            if (device.equals(mAudioManager.getCommunicationDevice())) {
                if (!switchToTemporaryDevice(device, run, results, callback)) {
                    // If switching to a temporary device fails, we cannot properly test the
                    // switch *to* the target device. Treat this run as unskippable for this device.
                    results.append(
                                    getString(
                                            R.string.audio_communication_latency_skipping_device,
                                            deviceName))
                            .append("\n");
                    return TestLoopAction.BREAK_AND_PASS; // Cannot test this device type.
                }
            }

            long startTime = System.nanoTime();
            if (!switchCommunicationDeviceAndWait(device, run, results, callback)) {
                return TestLoopAction.CONTINUE; // Failed to switch, try again in the next run.
            }
            long totalTimeMs = NANOSECONDS.toMillis(System.nanoTime() - startTime);

            results.append(
                            getString(
                                    R.string.audio_communication_latency_run_success,
                                    run,
                                    totalTimeMs))
                    .append("\n");
            latenciesMs.add(totalTimeMs);
        } finally {
            // Always attempt to clear the communication device to a default state.
            mAudioManager.clearCommunicationDevice();
        }
        return TestLoopAction.PROCEED; // Successfully completed one test run.
    }

    private boolean runTestLoop(
            DeviceData deviceData, AudioDeviceInfo device, StringBuilder results) {
        List<Long> latenciesMs = new ArrayList<>();
        TestOnCommunicationDeviceChangedListener callback =
                new TestOnCommunicationDeviceChangedListener();
        mAudioManager.addOnCommunicationDeviceChangedListener(getMainExecutor(), callback);

        try {
            for (int i = 0; i < NUM_TEST_RUNS; i++) {
                TestLoopAction action =
                        performSingleTestRun(device, i + 1, results, callback, latenciesMs);
                if (action == TestLoopAction.BREAK_AND_PASS) {
                    // Unable to perform the test for this device type.
                    results.append(getString(R.string.audio_communication_latency_test_skipped))
                            .append("\n");
                    return true; // Consider this device type "passed" as it's not testable.
                }
            }
        } finally {
            mAudioManager.removeOnCommunicationDeviceChangedListener(callback);
            // Ensure communication device is cleared after the test loop.
            mAudioManager.clearCommunicationDevice();
        }

        if (latenciesMs.isEmpty()) {
            results.append(getString(R.string.audio_communication_latency_no_valid_runs))
                    .append("\n\n");
            return false;
        }

        DoubleStream doubleLatencyStream = latenciesMs.stream().mapToDouble(Long::doubleValue);
        Double average = doubleLatencyStream.average().orElse(0.0);
        Double stdDevDouble =
                Math.sqrt(
                        latenciesMs.stream()
                                .mapToDouble(
                                        l ->
                                                (l.doubleValue() - average)
                                                        * (l.doubleValue() - average))
                                .average()
                                .orElse(0.0));
        Long min = latenciesMs.stream().mapToLong(Long::longValue).min().orElse(0);
        Long max = latenciesMs.stream().mapToLong(Long::longValue).max().orElse(0);

        deviceData.mAverageLatency = average.floatValue();
        deviceData.mStdDevLatency = stdDevDouble.floatValue();
        deviceData.mMinLatency = min.floatValue();
        deviceData.mMaxLatency = max.floatValue();

        results.append(getString(R.string.audio_communication_latency_avg, average)).append("\n");
        results.append(getString(R.string.audio_communication_latency_std_dev, stdDevDouble))
                .append("\n");
        results.append(getString(R.string.audio_communication_latency_min, min)).append("\n");
        results.append(getString(R.string.audio_communication_latency_max, max)).append("\n");

        if (average < LATENCY_MUST_THRESHOLD.toMillis()) {
            results.append(getString(R.string.audio_communication_latency_overall_passed))
                    .append("\n");
            return true;
        }
        results.append(getString(R.string.audio_communication_latency_overall_failed)).append("\n");
        return false;
    }

    private boolean testDevice(
            DeviceData deviceData, AudioDeviceInfo device, StringBuilder results) {
        String deviceName = getDescriptiveDeviceName(device);
        results.append(getString(R.string.audio_communication_latency_testing_device, deviceName))
                .append("\n");
        return runTestLoop(deviceData, device, results);
    }

    private String getDescriptiveDeviceName(AudioDeviceInfo device) {
        if (device == null) {
            return "NULL";
        }
        CharSequence productName = device.getProductName();
        if (productName == null || productName.isEmpty()) {
            return getDeviceName(device.getType());
        } else {
            return String.format("%s (%s)", getDeviceName(device.getType()), productName);
        }
    }

    @Override
    public String getTestId() {
        return setTestNameSuffix(sCurrentDisplayMode, getClass().getName());
    }

    @Override
    public boolean requiresReportLog() {
        return true;
    }

    @Override
    public String getReportFileName() {
        return PassFailButtons.AUDIO_TESTS_REPORT_LOG_NAME;
    }

    @Override
    public final String getReportSectionName() {
        return setTestNameSuffix(sCurrentDisplayMode, "audio_communication_latency_activity");
    }

    @Override
    public void recordTestResults() {
        CtsVerifierReportLog reportLog = getReportLog();
        JSONArray resultsArray = new JSONArray();
        TestStatus status = TestStatus.TEST_STATUS_PASSED;
        int notSupportedCount = 0;

        for (AudioDeviceTestHost host : mTestHosts) {
            DeviceData deviceData = host.mDeviceData;
            boolean notSupported = deviceData.mTestResult == TestResult.NOT_SUPPORTED;
            if (notSupported) {
                notSupportedCount++;
            } else if (deviceData.mTestResult != TestResult.PASS) {
                status = TestStatus.TEST_STATUS_FAILED;
            }

            try {
                resultsArray.put(deviceData.toJson());
            } catch (JSONException e) {
                Log.e(TAG, "Error building report JSON", e);
            }
        }
        // For the cases when there is less than one supported device,
        // there are not enough devices to run the test thus not supported.
        if (notSupportedCount >= mTestHosts.size() - 1) {
            status = TestStatus.TEST_STATUS_SKIPPED_UNSUPPORTED_DEVICE;
        }
        reportLog.addValues(KEY_LATENCY_RESULTS, resultsArray);
        reportLog.addValue(KEY_OVERALL_STATUS, status.name(), ResultType.NEUTRAL, ResultUnit.NONE);
        reportLog.submit();
    }

    private boolean hasValidCommunication() {
        PackageManager pm = getPackageManager();
        // Must support audio communication testing:
        //   - Must support audio output
        //   - Must support audio telephony
        //   - Must not be TV or Auto (audio policy mix do not support set communication
        //     output switching due to the audio policy manager routing priority)
        return pm.hasSystemFeature(PackageManager.FEATURE_AUDIO_OUTPUT)
                && pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
                && !pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
                && !pm.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE);
    }

    /** Callback for audio device changes. Updates the availability status in the UI. */
    private class TestAudioDeviceCallback extends AudioDeviceCallback {
        @Override
        public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
            updateDeviceAvailability(addedDevices, true);
        }

        @Override
        public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
            updateDeviceAvailability(removedDevices, false);
        }

        private void updateDeviceAvailability(AudioDeviceInfo[] devices, boolean isAvailable) {
            boolean changed = false;
            for (AudioDeviceInfo newDevice : devices) {
                for (AudioDeviceTestHost host : mTestHosts) {
                    DeviceData deviceData = host.mDeviceData;
                    if (host.getDeviceType() == newDevice.getType()
                            && deviceData.mIsAvailable != isAvailable) {
                        deviceData.mIsAvailable = isAvailable;
                        changed = true;
                        mMainHandler.post(host::updateView);
                    }
                }
            }
            if (changed) {
                mMainHandler.post(AudioCommunicationLatencyActivity.this::updatePassButton);
            }
        }
    }

    /** Listener to wait for a specific communication device change. Made static to avoid leaks. */
    private static class TestOnCommunicationDeviceChangedListener
            implements AudioManager.OnCommunicationDeviceChangedListener {
        private CountDownLatch mCountDownLatch;
        @Nullable private AudioDeviceInfo mExpectedDevice;
        @Nullable private volatile AudioDeviceInfo mReceivedDevice;

        TestOnCommunicationDeviceChangedListener() {
            // Initial setup, but will be reset before each use.
        }

        @Override
        public void onCommunicationDeviceChanged(@Nullable AudioDeviceInfo device) {
            mReceivedDevice = device;
            // The latch is counted down if the device matches, or if the expected device was
            // cleared (null).
            if (mExpectedDevice == null || mExpectedDevice.equals(device)) {
                if (mCountDownLatch != null) {
                    mCountDownLatch.countDown();
                }
            }
        }

        private void reset(@Nullable AudioDeviceInfo expectedDevice) {
            mExpectedDevice = expectedDevice;
            mReceivedDevice = null;
            mCountDownLatch = new CountDownLatch(1);
        }

        @Nullable
        private AudioDeviceInfo getReceivedDevice() {
            return mReceivedDevice;
        }

        /**
         * Waits for the expected device change with a timeout.
         *
         * @return true if the expected device was received within the timeout, false otherwise.
         */
        private boolean waitForDevice() throws InterruptedException {
            if (mCountDownLatch == null) {
                return false;
            }
            return mCountDownLatch.await(CALLBACK_WAIT_TIME.toMillis(), MILLISECONDS);
        }
    }

    /** Data class to hold information for each audio device in the list. */
    private static class DeviceData {
        final int mDeviceType;
        final String mInstruction;
        String mName;
        boolean mIsSupported;
        boolean mIsAvailable;
        TestResult mTestResult = TestResult.NOT_TESTED;
        String mTestResultText = "";
        float mAverageLatency;
        float mStdDevLatency;
        float mMinLatency;
        float mMaxLatency;

        DeviceData(int deviceType, String instruction) {
            mDeviceType = deviceType;
            mInstruction = instruction;
        }

        boolean isSupported() {
            return mIsSupported;
        }

        JSONObject toJson() throws JSONException {
            JSONObject json = new JSONObject();
            json.put(KEY_DEVICE_NAME, mName);
            json.put(KEY_DEVICE_TYPE, mDeviceType);
            json.put(KEY_NUM_TEST_RUNS, NUM_TEST_RUNS);
            json.put(KEY_TEST_STATUS, getTestStatusFromCurrentResults());

            if (mTestResult == TestResult.PASS || mTestResult == TestResult.FAIL) {
                json.put(KEY_LATENCY_AVERAGE_MS, mAverageLatency);
                json.put(KEY_LATENCY_MIN_MS, mMinLatency);
                json.put(KEY_LATENCY_MAX_MS, mMaxLatency);
                json.put(KEY_STD_DEV_LATENCY_MS, mStdDevLatency);
                // Reporting latency threshold since we may change this in the future,
                // but furthermore this may be device type dependent depending on future test
                // results.
                json.put(KEY_AVG_THRESHOLD_MS, LATENCY_MUST_REPORT);
            }
            return json;
        }

        private TestStatus getTestStatusFromCurrentResults() {
            return switch (mTestResult) {
                case NOT_TESTED -> TestStatus.TEST_STATUS_NOT_RUN;
                case FAIL -> TestStatus.TEST_STATUS_FAILED;
                case PASS -> TestStatus.TEST_STATUS_PASSED;
                case NOT_SUPPORTED -> TestStatus.TEST_STATUS_SKIPPED_UNSUPPORTED_DEVICE;
                default -> TestStatus.TEST_STATUS_UNSPECIFIED;
            };
        }
    }

    private class AudioDeviceTestHost {
        final DeviceData mDeviceData;
        View mView;
        TextView mDeviceName;
        TextView mDeviceInstructions;
        Button mRunTestButton;
        TextView mTestResultTextView;

        AudioDeviceTestHost(int deviceType) {
            mDeviceData = new DeviceData(deviceType, getDeviceInstruction(deviceType));
        }

        int getDeviceType() {
            return mDeviceData.mDeviceType;
        }

        View inflate(LayoutInflater inflater, ViewGroup parent) {
            mView = inflater.inflate(R.layout.audio_comm_latency_list_item, parent, false);
            mDeviceName = mView.findViewById(R.id.device_name);
            mDeviceInstructions = mView.findViewById(R.id.device_instructions);
            mRunTestButton = mView.findViewById(R.id.run_test_button);
            mTestResultTextView = mView.findViewById(R.id.test_result);

            mRunTestButton.setOnClickListener(v -> runTest(this));
            return mView;
        }

        void updateView() {
            mDeviceName.setText(mDeviceData.mName);

            if (!mDeviceData.mIsSupported) {
                mRunTestButton.setVisibility(View.GONE);
                mDeviceInstructions.setVisibility(View.GONE);
                mTestResultTextView.setText(R.string.audio_communication_latency_not_supported);
                mTestResultTextView.setVisibility(View.VISIBLE);
                return;
            }

            // Handle visibility and state based on availability and test result
            if (mDeviceData.mTestResult == TestResult.PASS) {
                mRunTestButton.setVisibility(View.GONE);
                mDeviceInstructions.setVisibility(View.GONE);
            } else if (mDeviceData.mIsAvailable) {
                mRunTestButton.setEnabled(mDeviceData.mTestResult != TestResult.RUNNING);
                mRunTestButton.setVisibility(View.VISIBLE);
                mDeviceInstructions.setVisibility(View.GONE);
            } else {
                mRunTestButton.setEnabled(false);
                mRunTestButton.setVisibility(View.VISIBLE);
                mDeviceInstructions.setText(mDeviceData.mInstruction);
                mDeviceInstructions.setVisibility(View.VISIBLE);
            }

            // Set test result text and visibility
            switch (mDeviceData.mTestResult) {
                case NOT_TESTED -> mTestResultTextView.setVisibility(View.GONE);
                case RUNNING -> {
                    mTestResultTextView.setText(R.string.audio_communication_latency_running);
                    mTestResultTextView.setVisibility(View.VISIBLE);
                }
                case PASS -> {
                    mTestResultTextView.setText(
                            getString(
                                    R.string.audio_communication_latency_pass,
                                    mDeviceData.mTestResultText));
                    mTestResultTextView.setVisibility(View.VISIBLE);
                }
                case FAIL -> {
                    mTestResultTextView.setText(
                            getString(
                                    R.string.audio_communication_latency_fail,
                                    mDeviceData.mTestResultText));
                    mTestResultTextView.setVisibility(View.VISIBLE);
                }
                default -> {
                    mTestResultTextView.setText(R.string.audio_communication_latency_not_supported);
                    mTestResultTextView.setVisibility(View.VISIBLE);
                }
            }
        }
    }
}
