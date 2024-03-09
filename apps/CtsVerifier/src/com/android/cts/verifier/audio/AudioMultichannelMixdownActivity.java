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

import android.graphics.Color;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.WebView;
import android.widget.RadioButton;
import android.widget.TextView;

// CTS Verifier
import com.android.cts.verifier.R;
import com.android.cts.verifier.audio.analyzers.BaseSineAnalyzer;
import com.android.cts.verifier.audio.audiolib.AudioSystemFlags;
import com.android.cts.verifier.audio.audiolib.AudioUtils;
import com.android.cts.verifier.audio.audiolib.DisplayUtils;
import com.android.cts.verifier.audio.audiolib.WaveScopeView;
import com.android.cts.verifier.libs.ui.HtmlFormatter;

// MegaAudio
import org.hyphonate.megaaudio.common.StreamBase;
import org.hyphonate.megaaudio.duplex.DuplexAudioManager;
import org.hyphonate.megaaudio.player.sources.SparseChannelAudioSource;
import org.hyphonate.megaaudio.player.sources.SparseChannelAudioSourceProvider;
import org.hyphonate.megaaudio.recorder.AudioSinkProvider;
import org.hyphonate.megaaudio.recorder.sinks.AppCallback;
import org.hyphonate.megaaudio.recorder.sinks.AppCallbackAudioSinkProvider;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

/**
 * CtsVerifier Multichannel Mixdown Test
 */
public class AudioMultichannelMixdownActivity
        extends AudioMultiApiActivity
        implements View.OnClickListener, AppCallback {
    private static final String TAG = "AudioMultichannelMixdownActivity";

    protected AudioManager mAudioManager;

    // UI
    private View mStartButton;
    private View mStopButton;
    private View mClearResultsButton;

    private RadioButton mSpeakerMicButton;
    private RadioButton mHeadsetButton;
    private RadioButton mUsbInterfaceButton;
    private RadioButton mUsbHeadsetButton;

    private WaveScopeView mWaveView = null;

    private TextView mPhasesView;

    private WebView mResultsView;

    private  HtmlFormatter mHtmlFormatter = new HtmlFormatter();

    protected boolean mIsHandheld;

    // Routes
    // - USB Interface (w/loopback)
    AudioDeviceInfo mUsbInterfaceOut;
    AudioDeviceInfo mUsbInterfaceIn;
    // - USB Headset (w/funplug)
    AudioDeviceInfo mUsbHeadsetOut;
    AudioDeviceInfo mUsbHeadsetIn;
    // - Analog Headset (w/funplug)
    AudioDeviceInfo mAnalogHeadsetOut;
    AudioDeviceInfo mAnalogHeadsetIn;
    // - Speaker/Mic
    AudioDeviceInfo mSpeakerOut;
    AudioDeviceInfo mMicrophoneIn;

    AudioDeviceInfo mSelectedOutput;
    AudioDeviceInfo mSelectedInput;

    private static final int OUT_SAMPLE_RATE = 48000;
    private static final int IN_SAMPLE_RATE = 48000;

    private static final int IN_CHANNEL_COUNT = 2;
    private static final int IN_CHANNEL_LEFT = 0;
    private static final int IN_CHANNEL_RIGHT = 1;

    private int mTestPhase = TestManager.TESTPHASE_NONE;
    private boolean mIsDuplexRunning;

    private static final int MS_PER_SEC = 1000;
    private static final int TEST_TIME_IN_SECONDS = 1;

    private Timer mTimer;

    // Analysis
    // Pass/Fail criteria (with default)
    static final double MIN_SIGNAL_PASS_MAGNITUDE = 0.01;
    static final double MAX_SIGNAL_PASS_JITTER = 0.1;

    // an analyzer per channel
    private BaseSineAnalyzer[] mAnalyzers = new BaseSineAnalyzer[IN_CHANNEL_COUNT];

    private AppCallback mAnalysisCallbackHandler;

    private DuplexAudioManager mDuplexAudioManager;
    int mCurrentPlayerMask;

    public SparseChannelAudioSourceProvider mSourceProvider;
    private SparseChannelAudioSource    mAudioSource;

    public AudioSinkProvider mSinkProvider =
            new AppCallbackAudioSinkProvider(mAnalysisCallbackHandler);

    private TestManager mTestManager = new TestManager();
    private boolean mTestCanceled;

    class TestPhase {
        final int mOutputMask;      // 7.1, 5.1...
        final int mOutputChannel;   // The index of the channel to play (for the
                                    // SparseChannelAudioSourceProvider)

        // A description of the channel being "listened for".
        final String mDescription;

        // Leave this null for all but the first phase for a new channel config
        String mHeading;

        boolean mPass;

        public double[] mMagnitude;
        public double[] mMaxMagnitude;
        public double[] mPhaseOffset;
        public double[] mPhaseJitter;

        public static final int STATUS_NOTRUN   = 0;
        public static final int STATUS_COMPLETE = 1;
        public int mState = STATUS_NOTRUN;

        TestPhase(int outMask, int outChannel, String description) {
            mOutputMask = outMask;
            mOutputChannel = outChannel;
            mDescription = description;

            mMagnitude = new double[IN_CHANNEL_COUNT];
            mMaxMagnitude = new double[IN_CHANNEL_COUNT];
            mPhaseOffset = new double[IN_CHANNEL_COUNT];
            mPhaseJitter = new double[IN_CHANNEL_COUNT];
        }

        void clearState() {
            mState = STATUS_NOTRUN;

            for (int channelIndex = 0; channelIndex < IN_CHANNEL_COUNT; channelIndex++) {
                mMagnitude[channelIndex] = 0.0;
                mMaxMagnitude[channelIndex] = 0.0;
                mPhaseOffset[channelIndex] = 0.0;
                mPhaseJitter[channelIndex] = 0.0;
            }
        }

        void analyze() {
            mState = STATUS_COMPLETE;

            for (int channelIndex = 0; channelIndex < IN_CHANNEL_COUNT; channelIndex++) {
                mMagnitude[channelIndex] = mAnalyzers[channelIndex].getMagnitude();
                mMaxMagnitude[channelIndex] = mAnalyzers[channelIndex].getMaxMagnitude();
                mPhaseOffset[channelIndex] = mAnalyzers[channelIndex].getPhaseOffset();
                mPhaseJitter[channelIndex] = mAnalyzers[channelIndex].getPhaseJitter();
            }

            // Pass?
            mPass = hasSignal();
        }

        boolean hasSignal() {
            return (mMagnitude[IN_CHANNEL_LEFT] >= MIN_SIGNAL_PASS_MAGNITUDE
                        && mPhaseJitter[IN_CHANNEL_LEFT] < MAX_SIGNAL_PASS_JITTER)
                    || (mMagnitude[IN_CHANNEL_RIGHT] >= MIN_SIGNAL_PASS_MAGNITUDE
                        && mPhaseJitter[IN_CHANNEL_RIGHT] < MAX_SIGNAL_PASS_JITTER);
        }
    }

    class TestManager {
        static final String TAG = "TestManager";

        static final int TESTPHASE_NONE = -1;

        public static final int TESTSTATUS_NOT_RUN = 0;
        public static final int TESTSTATUS_RUNNING = 1;
        public static final int TESTSTATUS_COMPLETE = 2;
        public static final int TESTSTATUS_CANCELED = 3;
        public static final int TESTSTATUS_BADSTART = 4;

        public int mState = TESTSTATUS_NOT_RUN;

        private ArrayList<TestPhase> mTestModules = new ArrayList<TestPhase>();

        TestManager() {
        }

        void initializeTests() {
            TestPhase testPhase;

            String separatorStr = " - ";
            // AudioFormat.CHANNEL_OUT_STEREO
            // Just a basic functionality test.
            String stereoString = getString(R.string.audio_mixdown_stereo) + separatorStr;
            mTestModules.add(testPhase = new TestPhase(AudioFormat.CHANNEL_OUT_STEREO, 0,
                    stereoString + getString(R.string.audio_channel_mask_left)));
            testPhase.mHeading = AudioUtils.channelOutPositionMaskToString(
                    AudioFormat.CHANNEL_OUT_STEREO);
            mTestModules.add(new TestPhase(AudioFormat.CHANNEL_OUT_STEREO, 1,
                    stereoString + getString(R.string.audio_channel_mask_right)));

            // AudioFormat.CHANNEL_OUT_QUAD
            String quadString = getString(R.string.audio_mixdown_quad) + separatorStr;
            mTestModules.add(testPhase = new TestPhase(AudioFormat.CHANNEL_OUT_QUAD, 0,
                    quadString + getString(R.string.audio_channel_mask_left)));
            testPhase.mHeading = AudioUtils.channelOutPositionMaskToString(
                    AudioFormat.CHANNEL_OUT_QUAD);
            mTestModules.add(new TestPhase(AudioFormat.CHANNEL_OUT_QUAD, 1,
                    quadString + getString(R.string.audio_channel_mask_right)));
            mTestModules.add(new TestPhase(AudioFormat.CHANNEL_OUT_QUAD, 2,
                    quadString + getString(R.string.audio_channel_mask_left)));
            mTestModules.add(new TestPhase(AudioFormat.CHANNEL_OUT_QUAD, 3,
                    quadString + getString(R.string.audio_channel_mask_right)));

            // AudioFormat.CHANNEL_OUT_5POINT1
            String fiveDotOneString = getString(R.string.audio_mixdown_fivedotone) + separatorStr;
            mTestModules.add(testPhase = new TestPhase(AudioFormat.CHANNEL_OUT_5POINT1, 0,
                    fiveDotOneString + getString(R.string.audio_channel_mask_left)));
            testPhase.mHeading = AudioUtils.channelOutPositionMaskToString(
                    AudioFormat.CHANNEL_OUT_5POINT1);
            mTestModules.add(new TestPhase(AudioFormat.CHANNEL_OUT_5POINT1, 1,
                    fiveDotOneString + getString(R.string.audio_channel_mask_right)));
            mTestModules.add(new TestPhase(AudioFormat.CHANNEL_OUT_5POINT1, 2,
                    fiveDotOneString + getString(R.string.audio_channel_mask_center)));
            mTestModules.add(new TestPhase(AudioFormat.CHANNEL_OUT_5POINT1, 3,
                    fiveDotOneString + getString(R.string.audio_channel_mask_lowfreq)));
            mTestModules.add(new TestPhase(AudioFormat.CHANNEL_OUT_5POINT1, 4,
                    fiveDotOneString + getString(R.string.audio_channel_mask_backleft)));
            mTestModules.add(new TestPhase(AudioFormat.CHANNEL_OUT_5POINT1, 5,
                    fiveDotOneString + getString(R.string.audio_channel_mask_backright)));

            // AudioFormat.CHANNEL_OUT_7POINT1_SURROUND
            String sevenDotOneString = getString(R.string.audio_mixdown_sevendotone) + separatorStr;
            mTestModules.add(testPhase = new TestPhase(AudioFormat.CHANNEL_OUT_7POINT1_SURROUND, 0,
                    sevenDotOneString + getString(R.string.audio_channel_mask_left)));
            testPhase.mHeading = AudioUtils.channelOutPositionMaskToString(
                    AudioFormat.CHANNEL_OUT_7POINT1_SURROUND);
            mTestModules.add(new TestPhase(AudioFormat.CHANNEL_OUT_7POINT1_SURROUND, 1,
                    sevenDotOneString + getString(R.string.audio_channel_mask_center)));
            mTestModules.add(new TestPhase(AudioFormat.CHANNEL_OUT_7POINT1_SURROUND, 2,
                    sevenDotOneString + getString(R.string.audio_channel_mask_right)));
            mTestModules.add(new TestPhase(AudioFormat.CHANNEL_OUT_7POINT1_SURROUND, 3,
                    sevenDotOneString + getString(R.string.audio_channel_mask_sideleft)));
            mTestModules.add(new TestPhase(AudioFormat.CHANNEL_OUT_7POINT1_SURROUND, 4,
                    sevenDotOneString + getString(R.string.audio_channel_mask_sideright)));
            mTestModules.add(new TestPhase(AudioFormat.CHANNEL_OUT_7POINT1_SURROUND, 5,
                    sevenDotOneString + getString(R.string.audio_channel_mask_backleft)));
            mTestModules.add(new TestPhase(AudioFormat.CHANNEL_OUT_7POINT1_SURROUND, 6,
                    sevenDotOneString + getString(R.string.audio_channel_mask_backright)));
            mTestModules.add(new TestPhase(AudioFormat.CHANNEL_OUT_7POINT1_SURROUND, 7,
                    sevenDotOneString + getString(R.string.audio_channel_mask_lowfreq)));

            // AudioFormat.CHANNEL_OUT_7POINT1POINT4
            String sevenDotOneDot4String = getString(R.string.audio_mixdown_sevendotonedotfour)
                    + separatorStr;
            mTestModules.add(testPhase = new TestPhase(AudioFormat.CHANNEL_OUT_7POINT1POINT4, 0,
                    sevenDotOneDot4String + getString(R.string.audio_channel_mask_left)));
            testPhase.mHeading = AudioUtils.channelOutPositionMaskToString(
                    AudioFormat.CHANNEL_OUT_7POINT1POINT4);
            mTestModules.add(new TestPhase(AudioFormat.CHANNEL_OUT_7POINT1POINT4, 1,
                    sevenDotOneDot4String + getString(R.string.audio_channel_mask_center)));
            mTestModules.add(new TestPhase(AudioFormat.CHANNEL_OUT_7POINT1POINT4, 2,
                    sevenDotOneDot4String + getString(R.string.audio_channel_mask_right)));
            mTestModules.add(new TestPhase(AudioFormat.CHANNEL_OUT_7POINT1POINT4, 3,
                    sevenDotOneDot4String + getString(R.string.audio_channel_mask_sideleft)));
            mTestModules.add(new TestPhase(AudioFormat.CHANNEL_OUT_7POINT1POINT4, 4,
                    sevenDotOneDot4String + getString(R.string.audio_channel_mask_sideright)));
            mTestModules.add(new TestPhase(AudioFormat.CHANNEL_OUT_7POINT1POINT4, 5,
                    sevenDotOneDot4String + getString(R.string.audio_channel_mask_backleft)));
            mTestModules.add(new TestPhase(AudioFormat.CHANNEL_OUT_7POINT1POINT4, 6,
                    sevenDotOneDot4String + getString(R.string.audio_channel_mask_backright)));
            mTestModules.add(new TestPhase(AudioFormat.CHANNEL_OUT_7POINT1POINT4, 7,
                    sevenDotOneDot4String + getString(R.string.audio_channel_mask_lowfreq)));
            mTestModules.add(new TestPhase(AudioFormat.CHANNEL_OUT_7POINT1POINT4, 8,
                    sevenDotOneDot4String + getString(R.string.audio_channel_mask_topfrontleft)));
            mTestModules.add(new TestPhase(AudioFormat.CHANNEL_OUT_7POINT1POINT4, 9,
                    sevenDotOneDot4String + getString(R.string.audio_channel_mask_topfrontright)));
            mTestModules.add(new TestPhase(AudioFormat.CHANNEL_OUT_7POINT1POINT4, 10,
                    sevenDotOneDot4String + getString(R.string.audio_channel_mask_topbackleft)));
            mTestModules.add(new TestPhase(AudioFormat.CHANNEL_OUT_7POINT1POINT4, 11,
                    sevenDotOneDot4String + getString(R.string.audio_channel_mask_topbackright)));
        }

        public int getNumTestPhases() {
            return mTestModules.size();
        }

        public TestPhase getTestPhase(int index) {
            return index != TestManager.TESTPHASE_NONE ? mTestModules.get(index) : null;
        }

        public void clearResults() {
            for (TestPhase testPhase : mTestModules) {
                testPhase.clearState();
            }
        }

        public void displayTestPhases() {
            if (!mIsHandheld) {
                return;
            }
            mWaveView.setVisibility(View.VISIBLE);
            mPhasesView.setVisibility(View.VISIBLE);
            mResultsView.setVisibility(View.GONE);
            StringBuilder sb = new StringBuilder();
            TestPhase currentTestPhase = getTestPhase(mTestPhase);
            for (TestPhase testPhase : mTestModules) {
                if (testPhase.mHeading != null) {
                    sb.append(testPhase.mHeading + "\n");
                }

                if (testPhase == currentTestPhase) {
                    sb.append(">> ");
                }

                sb.append(testPhase.mDescription);

                if (testPhase == currentTestPhase) {
                    sb.append(" <<");
                }
                sb.append("\n");
            }
            mPhasesView.setText(sb.toString());
        }

        public void displayTestResults() {
            mHtmlFormatter.clear();
            mHtmlFormatter.openDocument();

            Locale locale = Locale.getDefault();

            // "Bad Start"
            if (mTestManager.mState == TESTSTATUS_BADSTART) {
                mHtmlFormatter.openBold()
                    .appendText("Couldn't Start Duplex Audio.")
                        .closeBold();
            } else {
                // "Normal" Run
                if (mSelectedInput == null || mSelectedOutput == null) {
                    mHtmlFormatter.openBold();
                    if (mSelectedInput == null) {
                        mHtmlFormatter.appendText(getString(R.string.audio_mixdown_invalidinput));
                        mHtmlFormatter.appendBreak();
                    }
                    if (mSelectedOutput == null) {
                        mHtmlFormatter.appendText(getString(R.string.audio_mixdown_invalidoutput));
                        mHtmlFormatter.appendBreak();
                    }
                    mHtmlFormatter.appendText(getString(R.string.audio_mixdown_testcanceled));
                    mHtmlFormatter.closeBold();
                }

                for (TestPhase testPhase : mTestModules) {
                    if (testPhase.mState != TestPhase.STATUS_COMPLETE) {
                        mHtmlFormatter.openBold();
                        mHtmlFormatter.appendText("Test Canceled.");
                        mHtmlFormatter.closeBold();
                        break;
                    }

                    if (testPhase.mHeading != null) {
                        mHtmlFormatter.openBold();
                        mHtmlFormatter.appendText(testPhase.mHeading);
                        mHtmlFormatter.closeBold();
                        mHtmlFormatter.appendBreak();
                    }

                    // Description
                    String separatorStr = " - ";
                    mHtmlFormatter.appendText(testPhase.mDescription + separatorStr);
                    mHtmlFormatter.openBold();
                    mHtmlFormatter.appendText((testPhase.mPass
                            ? getString(R.string.audio_general_pass)
                            : getString(R.string.audio_general_fail)));
                    mHtmlFormatter.closeBold();
                    mHtmlFormatter.appendBreak();

                    mHtmlFormatter.openTextColor(testPhase.mPass ? "green" : "red");

                    // poor person's indent
                    mHtmlFormatter.appendText("  ");

                    // Left
                    String leftMagString =
                            String.format(locale, " mag:%.5f",
                                    testPhase.mMagnitude[IN_CHANNEL_LEFT]);
                    mHtmlFormatter.appendText(
                            getString(R.string.audio_general_left) + leftMagString + " ");

                    String leftJitterString = String.format(
                            locale, "jit:%.5f" , testPhase.mPhaseJitter[IN_CHANNEL_LEFT]);
                    mHtmlFormatter.appendText(leftJitterString);

                    mHtmlFormatter.appendBreak();
                    // poor person's indent
                    mHtmlFormatter.appendText("  ");

                    // Right
                    String rightMagString = String.format(
                            locale, " mag:%.5f", testPhase.mMagnitude[IN_CHANNEL_RIGHT]);
                    mHtmlFormatter.appendText(
                            getString(R.string.audio_general_right) + rightMagString);
                    String rightJitterString = String.format(
                            locale, " jit:%.5f" , testPhase.mPhaseJitter[IN_CHANNEL_RIGHT]);
                    mHtmlFormatter.appendText(rightJitterString);

                    mHtmlFormatter.closeTextColor();
                    mHtmlFormatter.appendBreak();
                }
            }
            mHtmlFormatter.closeDocument();
            mResultsView.loadData(mHtmlFormatter.toString(),
                    "text/html; charset=utf-8", "utf-8");

            showResultsView();
        }
    }

    void showResultsView() {
        mWaveView.setVisibility(View.GONE);
        mPhasesView.setVisibility(View.GONE);
        mResultsView.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setContentView(R.layout.audio_multichannel_mixdown_activity);

        super.onCreate(savedInstanceState);

        mTestManager.initializeTests();

        // MegaAudio Initialization
        StreamBase.setup(this);

        mIsHandheld = AudioSystemFlags.isHandheld(this);

        setPassFailButtonClickListeners();
        setInfoResources(R.string.audio_multichannel_mixdown_test,
                R.string.audio_multichannel_mixdown_info, -1);

        mStartButton = findViewById(R.id.audio_mixdown_start);
        mStartButton.setOnClickListener(this);
        mStartButton.setEnabled(mIsHandheld);

        mStopButton = findViewById(R.id.audio_mixdown_stop);
        mStopButton.setOnClickListener(this);
        mStopButton.setEnabled(false);

        mClearResultsButton = findViewById(R.id.audio_mixdown_clearresults);
        mClearResultsButton.setOnClickListener(this);

        mSpeakerMicButton = (RadioButton) findViewById(R.id.audio_mixdown_micspeaker);
        mSpeakerMicButton.setOnClickListener(this);
        mHeadsetButton = (RadioButton) findViewById(R.id.audio_mixdown_analogheadset);
        mHeadsetButton.setOnClickListener(this);
        mUsbInterfaceButton = (RadioButton) findViewById(R.id.audio_mixdown_usbdevice);
        mUsbInterfaceButton.setOnClickListener(this);
        mUsbHeadsetButton = (RadioButton) findViewById(R.id.audio_mixdown_usbheadset);
        mUsbHeadsetButton.setOnClickListener(this);

        findViewById(R.id.audio_mixdown_calibrate_button).setOnClickListener(this);

        mWaveView = (WaveScopeView) findViewById(R.id.uap_recordWaveView);
        mWaveView.setBackgroundColor(Color.DKGRAY);
        mWaveView.setTraceColor(Color.WHITE);
        mWaveView.setNumChannels(IN_CHANNEL_COUNT);

        mPhasesView = (TextView) findViewById(R.id.audio_mixdown_channels);

        mResultsView = (WebView) findViewById(R.id.audio_mixdown_results);

        mTestManager.displayTestPhases();

        mAudioManager = getSystemService(AudioManager.class);
        mAudioManager.registerAudioDeviceCallback(new AudioDeviceConnectionCallback(), null);

        enableRouteButtons();

        // Analyzers
        //  - Left
        mAnalyzers[IN_CHANNEL_LEFT] = new BaseSineAnalyzer();
        mAnalyzers[IN_CHANNEL_LEFT].setInputChannel(IN_CHANNEL_LEFT);
        //  - Right
        mAnalyzers[IN_CHANNEL_RIGHT] = new BaseSineAnalyzer();
        mAnalyzers[IN_CHANNEL_RIGHT].setInputChannel(IN_CHANNEL_RIGHT);

        mAnalysisCallbackHandler = this;

        mSinkProvider = new AppCallbackAudioSinkProvider(mAnalysisCallbackHandler);

        DisplayUtils.setKeepScreenOn(this, true);

        getPassButton().setEnabled(!mIsHandheld);
        if (!mIsHandheld) {
            displayNonHandheldMessage();
        }
    }

    @Override
    public void onStop() {
        stopTest();
        super.onStop();
    }

    //
    // UI Helpers
    //
    void displayNonHandheldMessage() {
        mHtmlFormatter.clear();
        mHtmlFormatter.openDocument();
        mHtmlFormatter.openParagraph();
        mHtmlFormatter.appendText(getResources().getString(R.string.audio_exempt_nonhandheld));
        mHtmlFormatter.closeParagraph();

        mHtmlFormatter.closeDocument();
        mResultsView.loadData(mHtmlFormatter.toString(),
                "text/html; charset=utf-8", "utf-8");
        showResultsView();
    }

    private void enableRouteButtons() {
        mSpeakerMicButton.setEnabled(mSpeakerOut != null && mMicrophoneIn != null);
        mHeadsetButton.setEnabled(mAnalogHeadsetOut != null && mAnalogHeadsetIn != null);
        mUsbInterfaceButton.setEnabled(mUsbInterfaceOut != null && mUsbInterfaceIn != null);
        mUsbHeadsetButton.setEnabled(mUsbHeadsetOut != null && mUsbHeadsetIn != null);
    }

    private void setSelectedRouteButton() {
        if (mSelectedOutput == null) {
            return;
        }

        if (mSelectedOutput == mSpeakerOut) {
            selectRouteButton(mSpeakerMicButton.getId());
        } else if (mSelectedOutput == mAnalogHeadsetOut) {
            selectRouteButton(mHeadsetButton.getId());
        } else if (mSelectedOutput == mUsbHeadsetOut) {
            selectRouteButton(mUsbHeadsetButton.getId());
        } else if (mSelectedOutput == mUsbInterfaceOut) {
            selectRouteButton(mUsbInterfaceButton.getId());
        }
    }

    //
    // Routes
    //
    // Handle "RadioButton" behavior.
    // Note: RadioGroup doesn't support multiple rows, so to get everything to fit w/o
    // taking up too much of the screen, do this ourselves
    //
    // Something based on this might be a better solution:
    // https://stackoverflow.com/questions/2961777/android-linearlayout-horizontal-with-wrapping-children
    private void selectRouteButton(int btnID) {
        // clear them...
        mSpeakerMicButton.setChecked(false);
        mHeadsetButton.setChecked(false);
        mUsbInterfaceButton.setChecked(false);
        mUsbHeadsetButton.setChecked(false);
        // select "the one"
        ((RadioButton) findViewById(btnID)).setChecked(true);
    }

    //
    // Process
    //
    private boolean startTest() {
        if (mSelectedOutput == null || mSelectedInput == null) {
            stopTest();
            return false;
        }

        mStartButton.setEnabled(false);
        mStopButton.setEnabled(true);
        mClearResultsButton.setEnabled(false);

        // setup for the first phase
        mTestManager.mState = TestManager.TESTSTATUS_RUNNING;

        mTestPhase = 0;
        mTestManager.displayTestPhases();

        // roll it back because advanceTestPhase() will increment this
        mTestPhase = TestManager.TESTPHASE_NONE;
        mTestCanceled = false;

        // This will force the duplex manager to restart
        mCurrentPlayerMask = 0;

        (mTimer = new Timer()).schedule(new TimerTask() {
            @Override
            public void run() {
                if (mTestPhase != TestManager.TESTPHASE_NONE) {
                    completeTestPhase();
                }
                advanceTestPhase();
            }
        }, 0, TEST_TIME_IN_SECONDS * MS_PER_SEC);

        return true;
    }

    private void completeTestPhase() {
        TestPhase testPhase = mTestManager.getTestPhase(mTestPhase);

        testPhase.analyze();

        mAnalyzers[IN_CHANNEL_LEFT].reset();
        mAnalyzers[IN_CHANNEL_RIGHT].reset();

        mWaveView.resetPersistentMaxMagnitude();
    }

    private void advanceTestPhase() {
        if (mTestCanceled) {
            // Don't overwrite error code
            if (mTestManager.mState != TestManager.TESTSTATUS_BADSTART) {
                mTestManager.mState = TestManager.TESTSTATUS_CANCELED;
            }
            stopTest();
        } else if (mTestPhase == mTestManager.getNumTestPhases() - 1) {
            mTestManager.mState = TestManager.TESTSTATUS_COMPLETE;
            stopTest();
        } else {
            mTestPhase++;

            TestPhase testPhase = mTestManager.getTestPhase(mTestPhase);
            if (mCurrentPlayerMask != testPhase.mOutputMask) {
                stopDuplex();
                startDuplex(testPhase);
            }
            mTestManager.displayTestPhases();

            mAudioSource.setMask(1 << testPhase.mOutputChannel);
        }
    }

    private void stopTest() {
        if (mTimer != null) {
            mTimer.cancel();
            mTimer = null;
            mTestPhase = TestManager.TESTPHASE_NONE;
            stopDuplex();
        }

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mStartButton.setEnabled(true);
                mStopButton.setEnabled(false);
                mClearResultsButton.setEnabled(true);

                mTestManager.displayTestResults();

                getPassButton().setEnabled(calculatePass());
            }
        });
    }

    private void startDuplex(TestPhase testPhase) {
        if (mDuplexAudioManager == null) {
            mDuplexAudioManager = new DuplexAudioManager(null, null);
        }

        if (mIsDuplexRunning) {
            mDuplexAudioManager.stop();
        }

        mSourceProvider = new SparseChannelAudioSourceProvider(1 << testPhase.mOutputChannel);
        mDuplexAudioManager.setSources(mSourceProvider, mSinkProvider);

        // Player
        mDuplexAudioManager.setPlayerRouteDevice(mSelectedOutput);
        mDuplexAudioManager.setPlayerSampleRate(OUT_SAMPLE_RATE);
        mCurrentPlayerMask = testPhase.mOutputMask;
        mDuplexAudioManager.setPlayerChannelMask(mCurrentPlayerMask);

        // Recorder
        mDuplexAudioManager.setRecorderRouteDevice(mSelectedInput);
        mDuplexAudioManager.setRecorderSampleRate(IN_SAMPLE_RATE);
        mDuplexAudioManager.setNumRecorderChannels(IN_CHANNEL_COUNT);

        // Open the streams.
        // Note AudioSources and AudioSinks get allocated at this point
        mDuplexAudioManager.buildStreams(mAudioApi, mAudioApi);

        // (potentially) Adjust AudioSource parameters
        mAudioSource = (SparseChannelAudioSource) mSourceProvider.getActiveSource();

        // Set the sample rate for the source (the sample rate for the player gets
        // set in the DuplexAudioManager.Builder.
        mAudioSource.setSampleRate(OUT_SAMPLE_RATE);
        mAudioSource.setMask(1 << testPhase.mOutputChannel);

        // Adjust the player frequency to match with the quantized frequency
        // of the analyzer.
        mAudioSource.setFreq((float) mAnalyzers[IN_CHANNEL_LEFT].getAdjustedFrequency());

        if (mDuplexAudioManager.start() != StreamBase.OK) {
            Log.e(TAG, "Couldn't Start Duplex Stream.");
            mTestManager.mState = TestManager.TESTSTATUS_BADSTART;
            mTestCanceled = true;
            mIsDuplexRunning = true;
        } else {
            mIsDuplexRunning = true;
        }
    }

    private void stopDuplex() {
        if (mIsDuplexRunning) {
            mDuplexAudioManager.stop();
            mIsDuplexRunning = false;
        }
    }

    private boolean calculatePass() {
        boolean pass =
                mIsHandheld
                // run at least once
                || mTestManager.mState == TestManager.TESTSTATUS_COMPLETE;

        return pass;
    }

    //
    // AudioMultiApiActivity Overrides
    //
    @Override
    public void onApiChange(int api) {
        mTestManager.displayTestPhases();
    }

    //
    // View.OnClickHandler
    //
    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.audio_mixdown_start) {
            startTest();
        } else if (id == R.id.audio_mixdown_stop) {
            stopTest();
        } else if (id == R.id.audio_mixdown_clearresults) {
            mTestManager.clearResults();
            mTestManager.displayTestPhases();
        } else if (id == R.id.audioJavaApiBtn || id == R.id.audioNativeApiBtn) {
            super.onClick(view);
        } else if (id == R.id.audio_mixdown_calibrate_button) {
            AudioLoopbackCalibrationDialog calibrationDialog =
                    new AudioLoopbackCalibrationDialog(this);
            calibrationDialog.show();
        } else if (id == R.id.audio_mixdown_micspeaker) {
            selectRouteButton(id);
            mSelectedOutput = mSpeakerOut;
            mSelectedInput = mMicrophoneIn;
        } else if (id == R.id.audio_mixdown_analogheadset) {
            selectRouteButton(id);
            mSelectedOutput = mAnalogHeadsetOut;
            mSelectedInput = mAnalogHeadsetIn;
        } else if (id == R.id.audio_mixdown_usbdevice) {
            selectRouteButton(id);
            mSelectedOutput = mUsbInterfaceOut;
            mSelectedInput = mUsbInterfaceIn;
        } else if (id == R.id.audio_mixdown_usbheadset) {
            selectRouteButton(id);
            mSelectedOutput = mUsbHeadsetOut;
            mSelectedInput = mUsbHeadsetIn;
        }
    }

    //
    // (MegaAudio) AppCallback overrides
    //
    @Override
    public void onDataReady(float[] audioData, int numFrames) {
        mAnalyzers[IN_CHANNEL_LEFT].analyzeBuffer(audioData, IN_CHANNEL_COUNT, numFrames);
        mAnalyzers[IN_CHANNEL_RIGHT].analyzeBuffer(audioData, IN_CHANNEL_COUNT, numFrames);
        mWaveView.setPCMFloatBuff(audioData, IN_CHANNEL_COUNT, numFrames);
    }

    //
    // AudioDeviceCallback overrides
    //
    private class AudioDeviceConnectionCallback extends AudioDeviceCallback {
        private void clearRoutes() {
            // Routes
            mUsbInterfaceOut =
                mUsbInterfaceIn =
                mUsbHeadsetOut =
                mUsbHeadsetIn =
                mAnalogHeadsetOut =
                mAnalogHeadsetIn =
                mSpeakerOut =
                mMicrophoneIn = null;

            // Selection
            mSelectedOutput = mSelectedInput = null;
        }

        void selectInputDevice(AudioDeviceInfo devInfo) {
            if (devInfo.getType() == AudioDeviceInfo.TYPE_WIRED_HEADSET) {
                mSelectedInput = mAnalogHeadsetIn = devInfo;
            } else if (devInfo.getType() == AudioDeviceInfo.TYPE_USB_DEVICE) {
                mSelectedInput = mUsbInterfaceIn = devInfo;
            } else if (devInfo.getType() == AudioDeviceInfo.TYPE_USB_HEADSET) {
                mSelectedInput = mUsbHeadsetIn = devInfo;
            } else if (devInfo.getType() == AudioDeviceInfo.TYPE_BUILTIN_MIC) {
                mSelectedInput = mMicrophoneIn = devInfo;
            }
        }

        void selectOutputDevice(AudioDeviceInfo devInfo) {
            if (devInfo.getType() == AudioDeviceInfo.TYPE_WIRED_HEADSET) {
                mSelectedOutput = mAnalogHeadsetOut = devInfo;
            } else if (devInfo.getType() == AudioDeviceInfo.TYPE_USB_DEVICE) {
                mSelectedOutput = mUsbInterfaceOut = devInfo;
            } else if (devInfo.getType() == AudioDeviceInfo.TYPE_USB_HEADSET) {
                mSelectedOutput = mUsbHeadsetOut = devInfo;
            } else if (devInfo.getType() == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                mSelectedOutput = mSpeakerOut = devInfo;
            }
        }

        void recalcIODevices() {
            clearRoutes();

            AudioDeviceInfo[] inputDevices =
                    mAudioManager.getDevices(AudioManager.GET_DEVICES_INPUTS);
            for (AudioDeviceInfo devInfo : inputDevices) {
                selectInputDevice(devInfo);
            }

            AudioDeviceInfo[] outputDevices =
                    mAudioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
            for (AudioDeviceInfo devInfo : outputDevices) {
                selectOutputDevice(devInfo);
            }

            enableRouteButtons();
            setSelectedRouteButton();
        }

        /**
         * @param addedDevices from onAudioDevicesAdded() callback.
         * @return true if a useable pair of I/O devices is connected
         */
        boolean addDeviceHandler(AudioDeviceInfo[] addedDevices) {
            // it is possible to connect analog headphones (i.e. no mic) though a USB dongle
            // which would show up as TYPE_USB_HEADSET (there is no TYPE_USB_HEADPHONES).
            // This would cause only 1 AudioDeviceInfo to be sent, so can't be used in this test.
            if (addedDevices.length == 1) {
                return false;
            }

            for (AudioDeviceInfo devInfo : addedDevices) {
                if (devInfo.isSource()) {
                    selectInputDevice(devInfo);
                } else {
                    selectOutputDevice(devInfo);
                }
            }
            enableRouteButtons();
            setSelectedRouteButton();
            return true;
        }

        @Override
        public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
            if (addDeviceHandler(addedDevices)) {
                mTestManager.displayTestPhases();
            }
        }

        @Override
        public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
            recalcIODevices();
        }
    }
}
