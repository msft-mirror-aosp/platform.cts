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

package com.android.cts.verifier.audio;

import static com.android.cts.verifier.TestListActivity.sCurrentDisplayMode;
import static com.android.cts.verifier.TestListAdapter.setTestNameSuffix;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;

import com.android.compatibility.common.util.CddTest;
import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;
import com.android.cts.verifier.CtsVerifierReportLog;
import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;
import com.android.cts.verifier.audio.audiolib.AudioSystemFlags;

@CddTest(requirement = "5.10/C-1-1,C-1-3,C-1-4")
public class ProAudioActivity
        extends PassFailButtons.Activity {
    private static final String TAG = ProAudioActivity.class.getSimpleName();
    private static final boolean DEBUG = false;

    // Flags
    private boolean mClaimsProAudio;
    private boolean mClaimsLowLatencyAudio;    // CDD ProAudio section C-1-1
    private boolean mClaimsMIDI;               // CDD ProAudio section C-1-4
    private boolean mClaimsUSBHostMode;        // CDD ProAudio section C-1-3
    private boolean mClaimsUSBPeripheralMode;  // CDD ProAudio section C-1-3

    // Widgets
    TextView mTestStatusLbl;

    // Borrowed from PassFailButtons.java
    private static final int INFO_DIALOG_ID = 1337;
    private static final String INFO_DIALOG_TITLE_ID = "infoDialogTitleId";
    private static final String INFO_DIALOG_MESSAGE_ID = "infoDialogMessageId";

    // ReportLog Schema
    private static final String SECTION_PRO_AUDIO_ACTIVITY = "pro_audio_activity";
    private static final String KEY_CLAIMS_PRO = "claims_pro_audio";
    private static final String KEY_CLAIMS_LOW_LATENCY = "claims_low_latency_audio";
    private static final String KEY_CLAIMS_MIDI = "claims_midi";
    private static final String KEY_CLAIMS_USB_HOST = "claims_usb_host";
    private static final String KEY_CLAIMS_USB_PERIPHERAL = "claims_usb_peripheral";

    public ProAudioActivity() {
    }

    private boolean calculatePass() {
        boolean usbOK = mClaimsUSBHostMode && mClaimsUSBPeripheralMode;

        boolean hasPassed = isReportLogOkToPass()
                && !mClaimsProAudio
                || (mClaimsLowLatencyAudio && mClaimsMIDI && usbOK);

        getPassButton().setEnabled(hasPassed);
        return hasPassed;
    }

    private void displayTestResults() {
        boolean hasPassed = calculatePass();

        Resources strings = getResources();
        if (!isReportLogOkToPass()) {
            mTestStatusLbl.setText(getResources().getString(R.string.audio_general_reportlogtest));
        } else  if (hasPassed) {
            mTestStatusLbl.setText(strings.getString(R.string.audio_proaudio_pass));
        } else if (!mClaimsMIDI) {
            mTestStatusLbl.setText(strings.getString(R.string.audio_proaudio_midinotreported));
        } else if (!mClaimsUSBHostMode) {
            mTestStatusLbl.setText(strings.getString(R.string.audio_proaudio_usbhostnotreported));
        } else if (!mClaimsUSBPeripheralMode) {
            mTestStatusLbl.setText(strings.getString(
                    R.string.audio_proaudio_usbperipheralnotreported));
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setContentView(R.layout.pro_audio);

        super.onCreate(savedInstanceState);

        setPassFailButtonClickListeners();
        setInfoResources(R.string.proaudio_test, R.string.proaudio_info, -1);

        mClaimsProAudio = AudioSystemFlags.claimsProAudio(this);
        ((TextView)findViewById(R.id.proAudioHasProAudioLbl)).setText("" + mClaimsProAudio);

        if (!mClaimsProAudio) {
            Bundle args = new Bundle();
            args.putInt(INFO_DIALOG_TITLE_ID, R.string.pro_audio_latency_test);
            args.putInt(INFO_DIALOG_MESSAGE_ID, R.string.audio_proaudio_nopa_message);
            showDialog(INFO_DIALOG_ID, args);
        }

        mClaimsLowLatencyAudio = AudioSystemFlags.claimsLowLatencyAudio(this);
        ((TextView)findViewById(R.id.proAudioHasLLALbl)).setText("" + mClaimsLowLatencyAudio);

        mClaimsMIDI = AudioSystemFlags.claimsMIDI(this);
        ((TextView)findViewById(R.id.proAudioHasMIDILbl)).setText("" + mClaimsMIDI);

        mClaimsUSBHostMode = AudioSystemFlags.claimsUSBHostMode(this);
        ((TextView)findViewById(R.id.proAudioMidiHasUSBHostLbl)).setText("" + mClaimsUSBHostMode);

        mClaimsUSBPeripheralMode = AudioSystemFlags.claimsUSBPeripheralMode(this);
        ((TextView)findViewById(
                R.id.proAudioMidiHasUSBPeripheralLbl)).setText("" + mClaimsUSBPeripheralMode);

        mTestStatusLbl = (TextView)findViewById(R.id.proAudioTestStatusLbl);

        displayTestResults();
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onStop() {
        super.onStop();
    }
    /**
     * Store test results in log
     */
    @Override
    public String getTestId() {
        return setTestNameSuffix(sCurrentDisplayMode, getClass().getName());
    }

    //
    // PassFailButtons Overrides
    //
    @Override
    public boolean requiresReportLog() {
        return true;
    }

    @Override
    public String getReportFileName() { return PassFailButtons.AUDIO_TESTS_REPORT_LOG_NAME; }

    @Override
    public final String getReportSectionName() {
        return setTestNameSuffix(sCurrentDisplayMode, SECTION_PRO_AUDIO_ACTIVITY);
    }

    @Override
    public void recordTestResults() {

        CtsVerifierReportLog reportLog = getReportLog();
        reportLog.addValue(
                KEY_CLAIMS_PRO,
                mClaimsProAudio,
                ResultType.NEUTRAL,
                ResultUnit.NONE);

        reportLog.addValue(
                KEY_CLAIMS_LOW_LATENCY,
                mClaimsLowLatencyAudio,
                ResultType.NEUTRAL,
                ResultUnit.NONE);

        reportLog.addValue(
                KEY_CLAIMS_MIDI,
                mClaimsMIDI,
                ResultType.NEUTRAL,
                ResultUnit.NONE);

        reportLog.addValue(
                KEY_CLAIMS_USB_HOST,
                mClaimsUSBHostMode,
                ResultType.NEUTRAL,
                ResultUnit.NONE);

        reportLog.addValue(
                KEY_CLAIMS_USB_PERIPHERAL,
                mClaimsUSBPeripheralMode,
                ResultType.NEUTRAL,
                ResultUnit.NONE);

        reportLog.submit();
    }
}
