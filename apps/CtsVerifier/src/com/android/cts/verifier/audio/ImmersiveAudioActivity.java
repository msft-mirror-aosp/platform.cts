/*
 * Copyright (C) 2024 The Android Open Source Project
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

import static android.content.Context.RECEIVER_EXPORTED;

import static com.android.cts.verifier.TestListActivity.sCurrentDisplayMode;
import static com.android.cts.verifier.TestListAdapter.setTestNameSuffix;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.webkit.WebView;
import android.widget.Toast;

import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;
import com.android.cts.verifier.CtsVerifierReportLog;
import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;
import com.android.cts.verifier.libs.ui.HtmlFormatter;

// Here is the current specification for the Intent from the Immersive Audio test harness
/*
adb shell am start -n com.android.cts.verifier/.audio.ImmersiveAudioActivity \
    --es test headtracking_latency \
    --es result "Pass" \
    --es err_code "n/a" \
    --ef latency 234.56 \
    --ef reliability 82.34 \
    --ei passing_threshold 300 \
    --ei passing_reliability 80 \
    --ei passing_range 40 \
    --es version_code "0.0.1"
 */

public class ImmersiveAudioActivity extends PassFailButtons.Activity {
    private static final String TAG = ImmersiveAudioActivity.class.getSimpleName();

    ImmersiveAudioActivity mTheIAActivity;

    // UI
    private WebView mResultsView;
    private HtmlFormatter mHtmlFormatter = new HtmlFormatter();

    // Intent Handling
    Intent mIntent;

    // Intent Extras
    String mTestCode;
    String mResultString;
    String mErrorCode;
    float mLatency;
    float mReliability;
    int mPassThreshold;
    int mPassReliability;
    int mPassRange;
    String mVersionCode;

    public static final String IMMERSIVE_AUDIO_RESULTS =
            "com.android.cts.verifier.audio.IMMERSIVE_AUDIO_RESULTS";
    IABroadcastReceiver mBroadcastReceiver;

    public ImmersiveAudioActivity() {
        mTheIAActivity = this;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mIntent = getIntent();
        Log.i(TAG, "mIntent:" + mIntent);

        setContentView(R.layout.immersive_audio_activity);
        setInfoResources(R.string.immersive_audio_test, R.string.immersive_audio_test_info, -1);

        mResultsView = (WebView) findViewById(R.id.immersive_test_result);

        setPassFailButtonClickListeners();
        getPassButton().setEnabled(true);

        mRequireReportLogToPass = true;

        // Context context = getApplicationContext();
        mBroadcastReceiver = new IABroadcastReceiver();

        IntentFilter filter = new IntentFilter();
        filter.addAction(IMMERSIVE_AUDIO_RESULTS);

        registerReceiver(mBroadcastReceiver, filter, RECEIVER_EXPORTED);

        displayIntent(mIntent);
    }
    private static final String INTENT_EXTRA_TESTCODE = "test";
    private static final String INTENT_EXTRA_RESULT = "result";
    private static final String INTENT_EXTRA_ERRORCODE = "err_code";
    private static final String INTENT_EXTRA_LATENCY = "latency";
    private static final String INTENT_EXTRA_RELIABILITY = "reliability";
    private static final String INTENT_EXTRA_PASSTHRESHOLD = "passing_threshold";
    private static final String INTENT_EXTRA_PASSRELIABILITY = "passing_reliability";
    private static final String INTENT_EXTRA_PASSRANGE = "passing_range";
    private static final String INTENT_EXTRA_VERSIONCODE = "version_code";

    private void displayIntent(Intent intent) {
        mHtmlFormatter.clear();
        mHtmlFormatter.openDocument();

        if (intent != null) {
            mTestCode = intent.getStringExtra(INTENT_EXTRA_TESTCODE);
            mResultString = intent.getStringExtra(INTENT_EXTRA_RESULT);
            mErrorCode = intent.getStringExtra(INTENT_EXTRA_ERRORCODE);
            mLatency = intent.getFloatExtra(INTENT_EXTRA_LATENCY, -1.0f);
            mReliability = intent.getFloatExtra(INTENT_EXTRA_RELIABILITY, -1.0f);
            mPassThreshold = intent.getIntExtra(INTENT_EXTRA_PASSTHRESHOLD, -1);
            mPassReliability = intent.getIntExtra(INTENT_EXTRA_PASSRELIABILITY, -1);
            mPassRange = intent.getIntExtra(INTENT_EXTRA_PASSRANGE, -1);
            mVersionCode = intent.getStringExtra(INTENT_EXTRA_VERSIONCODE);

            mHtmlFormatter.appendText(INTENT_EXTRA_TESTCODE + ": " + mTestCode);
            mHtmlFormatter.appendBreak();
            mHtmlFormatter.appendText(INTENT_EXTRA_RESULT + ": " + mResultString);
            mHtmlFormatter.appendBreak();
            mHtmlFormatter.appendText(INTENT_EXTRA_ERRORCODE + ": " + mErrorCode);
            mHtmlFormatter.appendBreak();
            mHtmlFormatter.appendText(INTENT_EXTRA_LATENCY + ": " + mLatency);
            mHtmlFormatter.appendBreak();
            mHtmlFormatter.appendText(INTENT_EXTRA_RELIABILITY + ": " + mReliability);
            mHtmlFormatter.appendBreak();
            mHtmlFormatter.appendText(INTENT_EXTRA_PASSTHRESHOLD + ": " + mPassThreshold);
            mHtmlFormatter.appendBreak();
            mHtmlFormatter.appendText(INTENT_EXTRA_PASSRELIABILITY + ": " + mPassReliability);
            mHtmlFormatter.appendBreak();
            mHtmlFormatter.appendText(INTENT_EXTRA_PASSRANGE + ": " + mPassRange);
            mHtmlFormatter.appendBreak();
            mHtmlFormatter.appendText(INTENT_EXTRA_VERSIONCODE + ": " + mVersionCode);
            mHtmlFormatter.appendBreak();
        } else {
            mHtmlFormatter.openBold();
            mHtmlFormatter.appendText("No Intent Received!");
            mHtmlFormatter.closeBold();
        }
        mHtmlFormatter.closeDocument();
        mResultsView.loadData(mHtmlFormatter.toString(),
                "text/html; charset=utf-8", "utf-8");
    }

    //
    // CtsVerifierReportLog handling
    //
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
        return setTestNameSuffix(sCurrentDisplayMode, "immersive_audio_latency_test");
    }

    void recordTestResults(CtsVerifierReportLog reportLog) {
        Log.i(TAG, "recordTestResults()");
        reportLog.addValue(
                INTENT_EXTRA_TESTCODE,
                mTestCode,
                ResultType.NEUTRAL,
                ResultUnit.NONE);
        reportLog.addValue(
                INTENT_EXTRA_RESULT,
                mResultString,
                ResultType.NEUTRAL,
                ResultUnit.NONE);
        reportLog.addValue(
                INTENT_EXTRA_ERRORCODE,
                mErrorCode,
                ResultType.NEUTRAL,
                ResultUnit.NONE);
        reportLog.addValue(
                INTENT_EXTRA_LATENCY,
                mLatency,
                ResultType.NEUTRAL,
                ResultUnit.NONE);
        reportLog.addValue(
                INTENT_EXTRA_RELIABILITY,
                mReliability,
                ResultType.NEUTRAL,
                ResultUnit.NONE);
        reportLog.addValue(
                INTENT_EXTRA_PASSTHRESHOLD,
                mPassThreshold,
                ResultType.NEUTRAL,
                ResultUnit.NONE);
        reportLog.addValue(
                INTENT_EXTRA_PASSRELIABILITY,
                mPassReliability,
                ResultType.NEUTRAL,
                ResultUnit.NONE);
        reportLog.addValue(
                INTENT_EXTRA_PASSRANGE,
                mPassRange,
                ResultType.NEUTRAL,
                ResultUnit.NONE);
        reportLog.addValue(
                INTENT_EXTRA_VERSIONCODE,
                mVersionCode,
                ResultType.NEUTRAL,
                ResultUnit.NONE);
    }

    private class IABroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "onReceive() - " + intent);

            mIntent = intent;
            displayIntent(mIntent);

            Toast.makeText(mTheIAActivity, "onReceive()", Toast.LENGTH_LONG).show();
        }
    }
}
