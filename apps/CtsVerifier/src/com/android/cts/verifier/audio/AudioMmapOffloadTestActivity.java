/*
 * Copyright (C) 2026 The Android Open Source Project
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

import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.compatibility.common.util.CddTest;
import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;
import com.android.cts.verifier.CtsVerifierReportLog;
import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

import org.hyphonate.megaaudio.common.BuilderBase;
import org.hyphonate.megaaudio.common.StreamBase;
import org.hyphonate.megaaudio.player.OboePlayer;
import org.hyphonate.megaaudio.player.PlayerBuilder;
import org.hyphonate.megaaudio.player.sources.SilenceAudioSourceProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** CTS Verifier test for MMAP PCM offload support (CDD 5.5.4/C-1-1,C-1-2). */
@CddTest(requirements = {"5.5.4/C-1-1,C-1-2"})
public class AudioMmapOffloadTestActivity extends PassFailButtons.Activity {
    private static final String TAG = "AudioMmapOffloadTest";

    private static final int[] SAMPLE_RATES = {44100, 48000};
    private static final int[] CHANNEL_COUNTS = {1, 2};

    private static final int TEST_NOT_TESTED = -1;
    private static final int TEST_PASS = 0;
    private static final int TEST_SKIP_NOT_SUPPORTED = 1;
    private static final int TEST_FAIL_CAPACITY = 2;

    private AudioDeviceConnectionCallback mConnectionCallback = new AudioDeviceConnectionCallback();

    private AudioManager mAudioManager;
    private Set<Integer> mAllSupportedOutputDeviceTypes;
    private List<TestRunner> mRunners = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.audio_mmap_offload_activity);

        setPassFailButtonClickListeners();
        setInfoResources(R.string.audio_mmap_offload_test, R.string.audio_mmap_offload_info, -1);

        mAudioManager = getSystemService(AudioManager.class);

        mAudioManager.registerAudioDeviceCallback(mConnectionCallback, null);

        mAllSupportedOutputDeviceTypes =
                mAudioManager.getSupportedDeviceTypes(AudioManager.GET_DEVICES_OUTPUTS);

        mRunners.add(
                new TestRunner(
                        R.id.audio_mmap_offload_speaker_layout,
                        R.id.audio_mmap_offload_speaker_status,
                        R.id.audio_mmap_offload_speaker_btn,
                        Set.of(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER),
                        "speaker"));

        mRunners.add(
                new TestRunner(
                        R.id.audio_mmap_offload_wired_headset_layout,
                        R.id.audio_mmap_offload_wired_headset_status,
                        R.id.audio_mmap_offload_wired_headset_btn,
                        Set.of(AudioDeviceInfo.TYPE_WIRED_HEADSET),
                        "wired headset"));

        mRunners.add(
                new TestRunner(
                        R.id.audio_mmap_offload_usb_layout,
                        R.id.audio_mmap_offload_usb_status,
                        R.id.audio_mmap_offload_usb_btn,
                        Set.of(AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE),
                        "USB"));

        getPassButton().setEnabled(false);
    }

    @Override
    protected void onDestroy() {
        mAudioManager.unregisterAudioDeviceCallback(mConnectionCallback);
        super.onDestroy();
    }

    AudioDeviceInfo getAnyDeviceByTypes(Set<Integer> deviceTypes) {
        AudioDeviceInfo[] devices = mAudioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
        for (AudioDeviceInfo device : devices) {
            if (deviceTypes.contains(device.getType())) {
                return device;
            }
        }
        return null;
    }

    static boolean isTestResultAccepted(int testResult) {
        return testResult == TEST_PASS || testResult == TEST_SKIP_NOT_SUPPORTED;
    }

    String getTestResultStr(int testResult) {
        return switch (testResult) {
            case TEST_NOT_TESTED -> getString(R.string.audio_general_not_tested);
            case TEST_PASS -> getString(R.string.audio_general_pass);
            case TEST_SKIP_NOT_SUPPORTED ->
                    getString(R.string.audio_mmap_offload_skip_not_supported);
            case TEST_FAIL_CAPACITY -> getString(R.string.audio_mmap_offload_fail_capacity);
            default -> "Unknown result";
        };
    }

    void updateAllRunnersUI() {
        for (TestRunner runner : mRunners) {
            runner.updateUI();
        }
        checkFinalTestResult();
    }

    void checkFinalTestResult() {
        if (mRunners.stream()
                .allMatch(runner -> isTestResultAccepted(runner.mTestResult.mResult))) {
            getPassButton().setEnabled(true);
        }
    }

    private static class TestResult {
        int mResult = TEST_NOT_TESTED;
        int mBufferCapacityInFrames = -1;

        TestResult(int result, int bufferCapacity) {
            mResult = result;
            mBufferCapacityInFrames = bufferCapacity;
        }
    }

    private class TestRunner {
        LinearLayout mLayout;
        TextView mStatusText;
        Button mStartButton;
        Set<Integer> mDeviceTypes;
        AudioDeviceInfo mDevice = null;
        String mDeviceDesc = "";
        TestResult mTestResult = new TestResult(TEST_NOT_TESTED, -1);

        TestRunner(
                int layoutId,
                int statusId,
                int buttonId,
                Set<Integer> deviceTypes,
                String deviceDesc) {
            mLayout = (LinearLayout) findViewById(layoutId);
            mStatusText = (TextView) findViewById(statusId);
            mStartButton = (Button) findViewById(buttonId);
            mDeviceTypes = deviceTypes;
            mDeviceDesc = deviceDesc;
            mStartButton.setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            runTest();
                        }
                    });

            if (deviceTypes.stream().noneMatch(dt -> mAllSupportedOutputDeviceTypes.contains(dt))) {
                mLayout.setVisibility(View.GONE);
                mTestResult.mResult = TEST_SKIP_NOT_SUPPORTED;
                mTestResult.mBufferCapacityInFrames = -1;
            } else {
                updateDevice();
            }
        }

        void updateUI() {
            updateDevice();
            if (mTestResult.mResult != TEST_NOT_TESTED) {
                mStartButton.setEnabled(false);
                mStatusText.setText(getTestResultStr(mTestResult.mResult));
            }
        }

        private void updateDevice() {
            mDevice = getAnyDeviceByTypes(mDeviceTypes);
            if (mTestResult.mResult != TEST_NOT_TESTED) {
                return;
            }
            if (mDevice == null) {
                mStartButton.setEnabled(false);
                mStatusText.setText(
                        String.format(
                                getString(R.string.audio_mmap_offload_connect_peripherals),
                                mDeviceDesc));
            } else {
                mStartButton.setEnabled(true);
                mStatusText.setText(R.string.audio_mmap_offload_start_test);
            }
        }

        private void runTest() {
            if (mTestResult.mResult != TEST_NOT_TESTED) {
                return;
            }
            mStatusText.setText(
                    String.format(
                            getString(R.string.audio_mmap_offload_running_test), mDeviceDesc));
            new Thread(
                            new Runnable() {
                                @Override
                                public void run() {
                                    for (int sampleRate : SAMPLE_RATES) {
                                        for (int channelCount : CHANNEL_COUNTS) {
                                            TestResult result =
                                                    testConfig(mDevice, sampleRate, channelCount);
                                            mTestResult = result;
                                            if (!isTestResultAccepted(mTestResult.mResult)) {
                                                break;
                                            }
                                        }
                                        if (!isTestResultAccepted(mTestResult.mResult)) {
                                            break;
                                        }
                                    }

                                    runOnUiThread(
                                            new Runnable() {
                                                @Override
                                                public void run() {
                                                    updateUI();
                                                    checkFinalTestResult();
                                                }
                                            });
                                }
                            })
                    .start();
        }

        private TestResult testConfig(AudioDeviceInfo device, int sampleRate, int channelCount) {
            OboePlayer player =
                    new OboePlayer(
                            new SilenceAudioSourceProvider(), BuilderBase.SUB_TYPE_OBOE_AAUDIO);
            PlayerBuilder builder = new PlayerBuilder();
            builder.setSampleRate(sampleRate)
                    .setChannelCount(channelCount)
                    .setPerformanceMode(BuilderBase.PERFORMANCE_MODE_POWER_SAVING_OFFLOADED)
                    .setSharingMode(BuilderBase.SHARING_MODE_EXCLUSIVE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setRouteDevice(device);

            if (player.build(builder) != StreamBase.OK) {
                Log.d(TAG, "Failed to build player with device(" + device.getType() + ")");
                return new TestResult(TEST_SKIP_NOT_SUPPORTED, -1);
            }
            if (player.open() != StreamBase.OK || !player.isMMap()) {
                Log.d(TAG, "Failed to open player with device(" + device.getType() + ")");
                player.teardown();
                return new TestResult(TEST_SKIP_NOT_SUPPORTED, -1);
            }

            int result = TEST_PASS;
            int capacity = player.getBufferCapacityInFrames();
            int minCapacity = 5 * sampleRate; // 5 seconds at 48kHz
            if (capacity < minCapacity) {
                result = TEST_FAIL_CAPACITY;
            }

            player.teardown();
            Log.d(TAG, "Test " + mDeviceDesc + ", result=" + result + ", capacity=" + capacity);
            return new TestResult(result, capacity);
        }

        void reportResult(CtsVerifierReportLog reportLog) {
            reportLog.addValue(KEY_TEST_PATH, mDeviceDesc, ResultType.NEUTRAL, ResultUnit.NONE);
            reportLog.addValue(
                    KEY_RESULT_CODE, mTestResult.mResult, ResultType.NEUTRAL, ResultUnit.NONE);
            reportLog.addValue(
                    KEY_BUFFER_CAPACITY,
                    mTestResult.mBufferCapacityInFrames,
                    ResultType.NEUTRAL,
                    ResultUnit.NONE);
        }
    }

    private class AudioDeviceConnectionCallback extends AudioDeviceCallback {
        @Override
        public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
            updateAllRunnersUI();
        }

        @Override
        public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
            updateAllRunnersUI();
        }
    }

    private static final String SECTION_MMAP_OFFLOAD = "mmap_offload_activity";
    private static final String KEY_TEST_PATH = "test_path";
    private static final String KEY_RESULT_CODE = "result_code";
    private static final String KEY_BUFFER_CAPACITY = "capacity";

    @Override
    public final String getReportSectionName() {
        return setTestNameSuffix(sCurrentDisplayMode, SECTION_MMAP_OFFLOAD);
    }

    @Override
    public void recordTestResults() {
        CtsVerifierReportLog reportLog = getReportLog();
        if (reportLog == null) {
            Log.e(TAG, "Failed to report log due to null report log");
            return;
        }
        for (TestRunner runner : mRunners) {
            runner.reportResult(reportLog);
        }
        reportLog.submit();
    }
}
