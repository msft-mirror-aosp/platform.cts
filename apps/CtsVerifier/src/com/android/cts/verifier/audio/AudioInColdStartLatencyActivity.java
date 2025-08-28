/*
 * Copyright (C) 2021 The Android Open Source Project
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

import android.os.Bundle;
import android.util.Log;

import com.android.compatibility.common.util.CddTest;
import com.android.cts.verifier.R;

import org.hyphonate.megaaudio.common.StreamBase;
import org.hyphonate.megaaudio.recorder.Recorder;
import org.hyphonate.megaaudio.recorder.RecorderBuilder;
import org.hyphonate.megaaudio.recorder.sinks.AppCallback;
import org.hyphonate.megaaudio.recorder.sinks.AppCallbackAudioSinkProvider;

/**
 * CTS-Test for cold-start latency measurements
 */
@CddTest(requirement = "5.6/C-3-2")
public class AudioInColdStartLatencyActivity
        extends AudioColdStartBaseActivity {
    private static final String TAG = "AudioInColdStartLatencyActivity";
    private static final boolean DEBUG = false;

    private static final int LATENCY_MS_MUST     = 500; // CDD C-3-2
    private static final int LATENCY_MS_RECOMMEND = 100; // CDD C-SR

    // MegaAudio
    private Recorder mRecorder;

    private long mFirstCallbackTime;

    // ReportLog Schema
    private static final String SECTION_INPUT_LATENCY = "in_coldlatency_activity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setContentView(R.layout.audio_coldstart_in_activity);
        super.onCreate(savedInstanceState);

        setPassFailButtonClickListeners();
        getPassButton().setEnabled(false);
        setInfoResources(
                R.string.audio_coldstart_inputlbl, R.string.audio_coldstart_input_info, -1);
    }

    @Override
    public String getTestId() {
        return setTestNameSuffix(sCurrentDisplayMode, getClass().getName());
    }

    boolean calcTestResult() {
        boolean pass = mColdStartlatencyMS <= 0 ? false : mColdStartlatencyMS <= LATENCY_MS_MUST;
        getPassButton().setEnabled(pass);
        return pass;
    }

    double calcColdStartLatency() {
        mColdStartlatencyMS = nanosToMs(mFirstCallbackTime - mPreOpenTime);
        return mColdStartlatencyMS;
    }

    void showInResults() {
        calcTestResult();
        showColdStartLatency();
    }

    @Override
    int getRequiredTimeMS() {
        return LATENCY_MS_MUST;
    }

    @Override
    int getRecommendedTimeMS() {
        return LATENCY_MS_RECOMMEND;
    }

    //
    // Audio Streaming
    //
    @Override
    boolean runAudioTest() {
        clearResults();

        mFirstCallbackTime = 0;

        int buildResult = StreamBase.ERROR_UNKNOWN;
        int openResult = StreamBase.ERROR_UNKNOWN;
        int startResult = StreamBase.ERROR_UNKNOWN;
        try {
            mPreOpenTime = System.nanoTime();
            RecorderBuilder builder = new RecorderBuilder();
            builder.setAudioSinkProvider(
                    new AppCallbackAudioSinkProvider(new ColdStartAppCallback()))
                .setRecorderType(mAudioApi)
                .setChannelCount(NUM_CHANNELS)
                .setSampleRate(mSampleRate)
                .setNumExchangeFrames(mNumExchangeFrames);
            mRecorder = builder.allocStream();
            mPreStartTime = System.nanoTime();
            if ((buildResult = mRecorder.build(builder)) == StreamBase.OK
                    && (openResult = mRecorder.open()) == StreamBase.OK) {
                mPostOpenTime = System.nanoTime();
                mIsTestRunning = true;
            }
        } catch (RecorderBuilder.BadStateException badStateException) {
            mLatencyTxt.setText(getString(R.string.audio_coldstart_badrecorderstate));
            Log.e(TAG, "BadStateException: " + badStateException);
            mIsTestRunning = false;
        }

        if (mIsTestRunning) {
            mPreStartTime = System.nanoTime();
            if ((startResult = mRecorder.start()) == StreamBase.OK) {
                mPostStartTime = System.nanoTime();
            } else {
                mIsTestRunning = false;
            }
        }

        if (mIsTestRunning) {
            mStartBtn.setEnabled(false);
            mStopBtn.setEnabled(true);
        } else {
            // report error...
            showStartupError("Recorder", buildResult, openResult, startResult);
            // Unwind...
            mRecorder.unwind();
        }
        return mIsTestRunning;
    }

    @Override
    void stopAudio() {
        if (!mIsTestRunning) {
            return;
        }

        mIsTestRunning = false;

        // Unwind will call stop()
        mRecorder.unwind();

        mStartBtn.setEnabled(true);
        mStopBtn.setEnabled(false);

        calcColdStartLatency();

        showInResults();

        reportLatency();
    }

    // Callback for Recorder
    /*
     * Get the first callback time.
     * Since we can't get the input timestamp for non-MMAP input streams, use the first callback
     * time to calculate cold start latency.
     */
    class ColdStartAppCallback implements AppCallback {
        public void onDataReady(float[] audioData, int numFrames) {
            long time = System.nanoTime();
            if (mFirstCallbackTime == 0) {
                mFirstCallbackTime = time;
                runOnUiThread(
                        new Runnable() {
                            @Override
                            public void run() {
                                stopAudio();
                                updateTestStateButtons();
                                calcColdStartLatency();
                                showInResults();
                            }
                        });
            }
        }
    }

    //
    // PassFailButtons Overrides
    //
    @Override
    public final String getReportSectionName() {
        return setTestNameSuffix(sCurrentDisplayMode, SECTION_INPUT_LATENCY);
    }
}
