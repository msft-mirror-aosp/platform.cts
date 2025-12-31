/*
 * Copyright 2025 The Android Open Source Project
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
import static java.lang.Math.max;

import android.mediapc.cts.common.PerformanceClassEvaluator;
import android.mediapc.cts.common.Requirements;
import android.mediapc.cts.common.Requirements.AudioCPUWorkloadRequirement;

import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import com.android.compatibility.common.util.CddTest;
import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;
import com.android.cts.verifier.CtsVerifierReportLog;
import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;
import com.android.cts.verifier.audio.audiolib.MultiLineChart;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.rules.TestName;

@CddTest(requirements = "5.6/H-3-1")
public class AudioWorkloadTestActivity extends PassFailButtons.Activity {
    private static final String TAG = AudioWorkloadTestActivity.class.getSimpleName();

    private static final String LOG_ERROR_STR = "Could not log metric.";

    private static final String SECTION_AUDIO_WORKLOAD = "audio_workload_test";
    // ReportLog Schema
    private static final String KEY_TARGET_DURATION_MS = "target_duration_ms";
    private static final String KEY_FINAL_XRUN_COUNT = "final_xrun_count";
    private static final String KEY_TEST_DURATION_MS = "test_duration_ms";
    private static final String KEY_CALLBACK_STATUSES = "callback_statuses";
    private static final String KEY_NUM_VOICES = "num_voices";
    private static final String KEY_NUM_BURSTS = "num_bursts";
    private static final String KEY_ALTERNATE_NUM_VOICES = "alternate_num_voices";
    private static final String KEY_ALTERNATING_PERIOD_MS = "alternating_period_ms";
    private static final String KEY_ENABLE_ADPF = "enable_adpf";
    private static final String KEY_ENABLE_ADPF_WORKLOAD_INCREASE = "enable_adpf_workload_increase";
    private static final String KEY_HEAR_WORKLOAD = "hear_workload";
    private static final String KEY_CPU_AFFINITY_MASK = "cpu_affinity_mask";
    private static final String KEY_XRUN_COUNT_THRESHOLD = "xrun_count_threshold";
    private static final String KEY_MEDIA_PERFORMANCE_CLASS = "media_performance_class";

    private List<CallbackStatus> mCallbackStatuses = null;
    private boolean mTestRunning = false;
    private long mTestDurationMs = 0;
    private int mFinalXRunCount = 0;
    private boolean mWorkloadTestPassed = false;

    public final TestName mTestName = new TestName();
    private static final int MEDIA_PERFORMANCE_CLASS = Build.VERSION.MEDIA_PERFORMANCE_CLASS;
    private static final boolean CLAIMS_MEDIA_PERFORMANCE = MEDIA_PERFORMANCE_CLASS != 0;
    public static final int MPC_CINNAMON_BUN = Build.VERSION_CODES.CINNAMON_BUN;
    private static final int TARGET_DURATION_MS = 3000;
    private static final int NUM_BURSTS = 2;
    private static final int NUM_VOICES = 1;
    private static final int ALTERNATE_NUM_VOICES = 50;
    private static final int ALTERNATING_PERIOD_MS = 50;
    private static final boolean ENABLE_ADPF = true;
    private static final boolean ENABLE_ADPF_WORKLOAD_INCREASE = false;
    private static final boolean HEAR_WORKLOAD = false;
    private static final int CPU_AFFINITY_MASK = 0;
    private static final int XRUN_COUNT_THRESHOLD = 0;

    // JNI load
    static {
        try {
            System.loadLibrary("audio_workload_jni");
            Log.i(TAG, "Audio Workload JNI library loaded");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Error loading Audio Workload JNI library", e);
        }
    }

    private Button mStartButton;
    private Button mStopButton;

    private TextView mStreamInfoView;
    private TextView mCurrentStatusView;
    private MultiLineChart mMultiLineChart;
    private MultiLineChart.Trace mCpuLoadTrace;
    private MultiLineChart.Trace mWorkloadTrace;

    private UpdateThread mUpdateThread;

    private static final int OPERATION_SUCCESS = 0;
    private static final long NANOS_PER_MILLI = 1_000_000;
    private static final long NANOS_PER_SECOND = 1_000_000_000;
    private static final float MARGIN_ABOVE_WORKLOAD_FOR_CPU = 1.2f;

    // Must match the NewObject call in jni-bridge.cpp
    public static class CallbackStatus {
        public int numVoices;
        public long beginTimeNs;
        public long finishTimeNs;
        public int xRunCount;
        public int cpuIndex;

        public CallbackStatus(
                int numVoices, long beginTimeNs, long finishTimeNs, int xRunCount, int cpuIndex) {
            this.numVoices = numVoices;
            this.beginTimeNs = beginTimeNs;
            this.finishTimeNs = finishTimeNs;
            this.xRunCount = xRunCount;
            this.cpuIndex = cpuIndex;
        }

        @NonNull
        @Override
        public String toString() {
            return "CallbackStatus{"
                    + "numVoices="
                    + numVoices
                    + ", beginTime="
                    + beginTimeNs
                    + ", finishTime="
                    + finishTimeNs
                    + ", xRunCount="
                    + xRunCount
                    + ", cpuIndex="
                    + cpuIndex
                    + '}';
        }
    }

    // Periodically query the status of the streams.
    protected class UpdateThread {
        private Handler mHandler;
        public static final int SNIFFER_UPDATE_PERIOD_MSEC = 40;
        public static final int SNIFFER_UPDATE_DELAY_MSEC = 300;

        // Display status info for the stream.
        private Runnable runnableCode =
                new Runnable() {
                    @Override
                    public void run() {
                        mCurrentStatusView.setText(
                                String.format(
                                        "#%d, xRuns: %d, time: %.3fms",
                                        getCallbackCount(),
                                        getXRunCount(),
                                        getLastDurationNs() / (float) NANOS_PER_MILLI));
                        if (isRunning()) {
                            mHandler.postDelayed(runnableCode, SNIFFER_UPDATE_PERIOD_MSEC);
                        } else {
                            stopTest();
                        }
                    }
                };

        private void start() {
            stop();
            mHandler = new Handler(Looper.getMainLooper());
            // Start the initial runnable task by posting through the handler
            mHandler.postDelayed(runnableCode, SNIFFER_UPDATE_DELAY_MSEC);
            Log.i(TAG, "UpdateThread started");
        }

        private void stop() {
            if (mHandler != null) {
                mHandler.removeCallbacks(runnableCode);
            }
            Log.i(TAG, "UpdateThread stopped");
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.audio_workload_activity);
        setPassFailButtonClickListeners();
        setInfoResources(R.string.audio_workload_title, R.string.audio_workload_info, -1);
        getPassButton().setEnabled(false);
        mRequireReportLogToPass = true;

        ((TextView) findViewById(R.id.audio_workload_mpc))
                .setText(
                        (CLAIMS_MEDIA_PERFORMANCE
                                ? String.valueOf(MEDIA_PERFORMANCE_CLASS)
                                : "No"));

        // Control buttons.
        mStartButton = (Button) findViewById(R.id.button_start);
        mStopButton = (Button) findViewById(R.id.button_stop);

        mStreamInfoView = (TextView) findViewById(R.id.stream_info_view);
        mCurrentStatusView = (TextView) findViewById(R.id.current_status_view);

        mMultiLineChart = (MultiLineChart) findViewById(R.id.multiline_chart);
        mCpuLoadTrace =
                mMultiLineChart.createTrace(
                        "CPU", Color.GREEN, Color.RED, /* min */ 0.0f, /* max */ 2.0f);
        mWorkloadTrace =
                mMultiLineChart.createTrace(
                        "Work", Color.DKGRAY, 0.0f, MARGIN_ABOVE_WORKLOAD_FOR_CPU);

        setCpuAffinityForCallback(CPU_AFFINITY_MASK);

        mStartButton.setEnabled(true);
        mStopButton.setEnabled(false);

        updatePassButtonState();
    }

    @Override
    protected void onStop() {
        close();
        super.onStop();
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
        return setTestNameSuffix(sCurrentDisplayMode, SECTION_AUDIO_WORKLOAD);
    }

    private void recordCtsVerifierReportLog(){
        CtsVerifierReportLog reportLog = getReportLog();

        // ReportLog Schema
        reportLog.addValue(
                KEY_TARGET_DURATION_MS, TARGET_DURATION_MS, ResultType.NEUTRAL, ResultUnit.MS);
        reportLog.addValue(KEY_NUM_BURSTS, NUM_BURSTS, ResultType.NEUTRAL, ResultUnit.NONE);
        reportLog.addValue(KEY_NUM_VOICES, NUM_VOICES, ResultType.NEUTRAL, ResultUnit.NONE);
        reportLog.addValue(
                KEY_ALTERNATE_NUM_VOICES,
                ALTERNATE_NUM_VOICES,
                ResultType.NEUTRAL,
                ResultUnit.NONE);
        reportLog.addValue(
                KEY_ALTERNATING_PERIOD_MS,
                ALTERNATING_PERIOD_MS,
                ResultType.NEUTRAL,
                ResultUnit.MS);
        reportLog.addValue(KEY_ENABLE_ADPF, ENABLE_ADPF, ResultType.NEUTRAL, ResultUnit.NONE);
        reportLog.addValue(
                KEY_ENABLE_ADPF_WORKLOAD_INCREASE,
                ENABLE_ADPF_WORKLOAD_INCREASE,
                ResultType.NEUTRAL,
                ResultUnit.NONE);
        reportLog.addValue(KEY_HEAR_WORKLOAD, HEAR_WORKLOAD, ResultType.NEUTRAL, ResultUnit.NONE);
        reportLog.addValue(
                KEY_CPU_AFFINITY_MASK, CPU_AFFINITY_MASK, ResultType.NEUTRAL, ResultUnit.NONE);
        reportLog.addValue(
                KEY_XRUN_COUNT_THRESHOLD,
                XRUN_COUNT_THRESHOLD,
                ResultType.NEUTRAL,
                ResultUnit.NONE);
        reportLog.addValue(
                KEY_MEDIA_PERFORMANCE_CLASS,
                MEDIA_PERFORMANCE_CLASS,
                ResultType.NEUTRAL,
                ResultUnit.NONE);

        // Report results if test is run
        if (mCallbackStatuses != null && !mCallbackStatuses.isEmpty()) {
            reportLog.addValue(
                    KEY_FINAL_XRUN_COUNT,
                    mFinalXRunCount,
                    ResultType.LOWER_BETTER,
                    ResultUnit.COUNT);
            reportLog.addValue(
                    KEY_TEST_DURATION_MS, mTestDurationMs, ResultType.NEUTRAL, ResultUnit.MS);
        }
        reportLog.submit();

    }

    private void recordPerformanceClassTestResults() {
        PerformanceClassEvaluator pce = new PerformanceClassEvaluator(mTestName);
        AudioCPUWorkloadRequirement audioWorkloadRequirement = Requirements.addR5_6__H_3_1().to(pce);
        audioWorkloadRequirement.setUnderrunCount(mFinalXRunCount);
        pce.submitAndVerify();
    }

    @Override
    public void recordTestResults() {
        recordCtsVerifierReportLog();
        recordPerformanceClassTestResults();
    }

    public void startTest(View view) {
        int result = open();
        if (result != OPERATION_SUCCESS) {
            showErrorToast("open failed! Error:" + result);
            return;
        }

        mTestRunning = true;
        updatePassButtonState();
        result =
                start(
                        TARGET_DURATION_MS,
                        NUM_BURSTS,
                        NUM_VOICES,
                        ALTERNATE_NUM_VOICES,
                        ALTERNATING_PERIOD_MS,
                        ENABLE_ADPF,
                        ENABLE_ADPF_WORKLOAD_INCREASE,
                        HEAR_WORKLOAD);
        if (result != OPERATION_SUCCESS) {
            showErrorToast("start failed! Error:" + result);
            close();
            return;
        }

        updateStreamInfoView();

        mStartButton.setEnabled(false);
        mStopButton.setEnabled(true);
        mCallbackStatuses = null;

        mUpdateThread = new UpdateThread();
        mUpdateThread.start();
    }

    public void stopTest(View view) {
        stopTest();
    }

    private void stopTest() {
        int result = stop();
        if (result != OPERATION_SUCCESS) {
            showErrorToast("stop failed! Error:" + result);
        }

        mCallbackStatuses = getCallbackStatistics();
        drawCallbackStatistics(mCallbackStatuses);
        evaluateTestResults();

        if (mUpdateThread != null) {
            mUpdateThread.stop();
        }

        result = close();
        if (result != OPERATION_SUCCESS) {
            showErrorToast("close failed! Error:" + result);
            return;
        }

        mStartButton.setEnabled(true);
        mStopButton.setEnabled(false);

        mTestRunning = false;
        updatePassButtonState();
    }

    private void evaluateTestResults() {
        mWorkloadTestPassed = false;
        if (mCallbackStatuses != null && !mCallbackStatuses.isEmpty()) {
            CallbackStatus firstCallbackStatus = mCallbackStatuses.get(0);
            CallbackStatus lastCallbackStatus = mCallbackStatuses.get(mCallbackStatuses.size() - 1);
            long durationNs = lastCallbackStatus.finishTimeNs - firstCallbackStatus.beginTimeNs;
            mTestDurationMs =  durationNs / NANOS_PER_MILLI;
            mFinalXRunCount = lastCallbackStatus.xRunCount;
            mWorkloadTestPassed =
                    (mTestDurationMs >= TARGET_DURATION_MS)
                            && (mFinalXRunCount <= XRUN_COUNT_THRESHOLD);
        }
    }

    private void updatePassButtonState() {
        if (!CLAIMS_MEDIA_PERFORMANCE || MEDIA_PERFORMANCE_CLASS < MPC_CINNAMON_BUN) {
            getPassButton().setEnabled(true);
            return;
        }

        if (mTestRunning) {
            getPassButton().setEnabled(false);
            return;
        }

        getPassButton().setEnabled(mWorkloadTestPassed && isReportLogOkToPass());
    }

    private void drawCallbackStatistics(List<CallbackStatus> callbackStatuses) {
        if (callbackStatuses == null) {
            showErrorToast("empty callback status!");
        } else {
            mMultiLineChart.reset();
            long firstTimeNs = 0;
            int lastXRuns = 0;
            // Time between callbacks in nanoseconds.
            double expectedCallbackTimeSeconds = (double) getBufferSizeInFrames() / getSampleRate();

            float maxWorkloadValue = max(NUM_VOICES, ALTERNATE_NUM_VOICES);
            for (CallbackStatus callbackStatus : callbackStatuses) {
                if (firstTimeNs == 0) {
                    firstTimeNs = callbackStatus.beginTimeNs;
                }
                long elapsedTime = callbackStatus.beginTimeNs - firstTimeNs;
                mMultiLineChart.addX(elapsedTime / (float) NANOS_PER_SECOND);

                long callbackDuration = callbackStatus.finishTimeNs - callbackStatus.beginTimeNs;
                double cpuLoad = (callbackDuration / (double) NANOS_PER_SECOND) / expectedCallbackTimeSeconds;
                boolean hasXRun = (callbackStatus.xRunCount > lastXRuns);
                lastXRuns = callbackStatus.xRunCount;

                mCpuLoadTrace.add((float) cpuLoad, hasXRun);
                mWorkloadTrace.add(callbackStatus.numVoices / maxWorkloadValue, false);
            }
            mMultiLineChart.update();
        }
    }

    public void updateStreamInfoView() {
        mStreamInfoView.setText(
                String.format(
                        "burst: %d, sr: %d, buffer: %d",
                        getFramesPerBurst(), getSampleRate(), getBufferSizeInFrames()));
    }

    protected void showErrorToast(String message) {
        showToast("Error: " + message);
    }

    protected void showToast(final String message) {
        runOnUiThread(
                new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(AudioWorkloadTestActivity.this, message, Toast.LENGTH_SHORT)
                                .show();
                    }
                });
    }

    private native int open();

    private native int getFramesPerBurst();

    private native int getSampleRate();

    private native int getBufferSizeInFrames();

    private native int start(
            int targetDurationMs,
            int numBursts,
            int numVoices,
            int numAlternateVoices,
            int alternatingPeriodMs,
            boolean adpfEnabled,
            boolean adpfWorkloadIncreaseEnabled,
            boolean hearWorkload);

    private native int getCpuCount();

    private native int setCpuAffinityForCallback(int mask);

    private native int getXRunCount();

    private native int getCallbackCount();

    private native long getLastDurationNs();

    private native boolean isRunning();

    private native int stop();

    private native int close();

    private native List<CallbackStatus> getCallbackStatistics();
}
