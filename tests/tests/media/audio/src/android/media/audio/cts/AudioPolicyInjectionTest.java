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

package android.media.audio.cts;

import static android.Manifest.permission.MODIFY_AUDIO_ROUTING;
import static android.Manifest.permission.RECORD_AUDIO;
import static android.media.AudioManager.AUDIO_SESSION_ID_GENERATE;
import static android.media.audio.Flags.FLAG_DAP_INJECTION_STARVE_MANAGEMENT;
import static android.media.audiopolicy.AudioMixingRule.MIX_ROLE_INJECTOR;
import static android.media.audiopolicy.AudioMixingRule.RULE_MATCH_AUDIO_SESSION_ID;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.Assert.*;

import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.media.audiopolicy.AudioMix;
import android.media.audiopolicy.AudioMixingRule;
import android.media.audiopolicy.AudioPolicy;
import android.os.Process;
import android.os.SystemClock;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;
import com.android.compatibility.common.util.FrameworkSpecificTest;
import com.android.media.mediatestutils.PermissionUpdateBarrierRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Class to exercise injecting an audio signal for it to be captured for a given recording source.
 */
@FrameworkSpecificTest
@RunWith(AndroidJUnit4.class)
public class AudioPolicyInjectionTest {
    private static final String TAG = AudioPolicyInjectionTest.class.getSimpleName();
    private static final boolean DEBUG = false;

    private static final short SIGNAL_AMPLITUDE = 30000;
    private static final int READ_BUFFER_SIZE_MS = 48;
    private static final int INJECTION_TIME_MS = 2400;
    private static final int CAPTURE_TIME_MS = 1100;
    private static final int ZERO_READS_TO_FINISH_MS = 3000;
    private static final long DEFAULT_TIMEOUT_MS = 5000;
    // longer than 3s
    private static final long SLEEP_AUDIO_MIX_SILENCE_ON_STARVE_MS = 4000;
    private static final long SLEEP_SHORT_TIMEOUT_MS = 500;
    private static final int FAKE_APPLICATION_UID = 1234;

    /**
     * captured signal should be mostly single frequency and power of that frequency should be over
     * this much of total power.
     */
    private static final double POWER_THRESHOLD_FOR_PASS = 0.4f;

    /** the other signal should have very weak power and should not exceed this value */
    private static final double POWER_THRESHOLD_FOR_OTHER_SIGNAL = 0.09f;

    private static final int FREQ_VOICE_STIMULI_HZ = 2000; // 2kHz
    private static final int SAMPLE_RATE_VOICE_HZ = 16000;
    private static final AudioFormat FORMAT_VOICE_INJECTION =
            new AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE_VOICE_HZ)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build();
    private static final AudioFormat FORMAT_VOICE_RECORDING =
            new AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE_VOICE_HZ)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build();
    private static final AudioAttributes AUDIO_ATTRIBUTES_VOICE_COMMUNICATION =
            new AudioAttributes.Builder()
                    .setCapturePreset(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                    .build();
    private static final AudioAttributes AUDIO_ATTRIBUTES_VOICE_RECOGNITION =
            new AudioAttributes.Builder()
                    .setCapturePreset(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                    .build();

    @Rule(order = 1)
    public final AdoptShellPermissionsRule mPermissionRule =
            new AdoptShellPermissionsRule(
                    InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                    MODIFY_AUDIO_ROUTING,
                    RECORD_AUDIO);

    @Rule(order = 2)
    public final PermissionUpdateBarrierRule mBarrierRule =
            new PermissionUpdateBarrierRule(
                    InstrumentationRegistry.getInstrumentation().getContext());

    @Rule(order = 3)
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private AudioManager mAudioManager;
    private AudioPolicy mAudioPolicy;
    // track to inject audio into AudioRecord for VOICE_RECOGNITION
    private AudioTrack mInjectionTrack;
    // recorder to capture on the VOICE_RECOGNITION source
    private AudioRecord mVoiceRecord;

    /** Test setup */
    @Before
    public void setUp() throws Exception {
        mAudioManager =
                ApplicationProvider.getApplicationContext().getSystemService(AudioManager.class);
    }

    /** Test teardown */
    @After
    public void tearDown() {
        if (mInjectionTrack != null) {
            mInjectionTrack.stop();
            mInjectionTrack.release();
            mInjectionTrack = null;
        }
        if (mVoiceRecord != null) {
            mVoiceRecord.stop();
            mVoiceRecord.release();
            mVoiceRecord = null;
        }
        if (mAudioPolicy != null) {
            mAudioManager.unregisterAudioPolicyAsync(mAudioPolicy);
            mAudioPolicy = null;
        }
    }

    /**
     * Create an AudioPolicy with a mix for injection on VOICE_RECOGNITION. Inject a sine wave at
     * FREQ_VOICE_STIMULI_HZ Hz and verify it's in the recording.
     */
    @Test
    public void testInjectionForAudioRecord() {
        setupInjectionWithMixRules(
                MixRule.createRule(
                        AudioMixingRule.RULE_MATCH_ATTRIBUTE_CAPTURE_PRESET,
                        AUDIO_ATTRIBUTES_VOICE_RECOGNITION));
        mVoiceRecord = buildAudioRecordForSource(MediaRecorder.AudioSource.VOICE_RECOGNITION);

        injectAndVerifyCapture(/* present= */ true);
    }

    /**
     * Tests that audio injection works fine for VOICE_COMMUNICATION audio source, which is used in
     * VOIP calls. Test executed by setting up an audio mix for VOICE_COMMUNICATION, inject sine
     * wave and verify it's in the recording.
     */
    @Test
    public void testVoipAudioInjection() {
        setupInjectionWithMixRules(
                MixRule.createRule(
                        AudioMixingRule.RULE_MATCH_ATTRIBUTE_CAPTURE_PRESET,
                        AUDIO_ATTRIBUTES_VOICE_COMMUNICATION));
        mVoiceRecord = buildAudioRecordForSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION);

        injectAndVerifyCapture(/* present= */ true);
    }

    /**
     * Create an AudioPolicy with a mix for injection by application uid. Inject a sine wave at
     * FREQ_VOICE_STIMULI_HZ Hz and verify it's in the recording.
     */
    @Test
    public void testInjectionForUidMatched() {
        setupInjectionWithMixRules(
                MixRule.createRule(AudioMixingRule.RULE_MATCH_UID, Process.myUid()));
        mVoiceRecord = buildAudioRecordForSource(MediaRecorder.AudioSource.VOICE_RECOGNITION);

        injectAndVerifyCapture(/* present= */ true);
    }

    /**
     * Create an AudioPolicy with a mix for injection by application uid. Inject a sine wave at
     * FREQ_VOICE_STIMULI_HZ Hz and verify it's not in the recording if uid not matched.
     */
    @Test
    public void testInjectionForUidNotMatched() {
        setupInjectionWithMixRules(
                MixRule.createRule(AudioMixingRule.RULE_MATCH_UID, FAKE_APPLICATION_UID));
        mVoiceRecord = buildAudioRecordForSource(MediaRecorder.AudioSource.VOICE_RECOGNITION);

        injectAndVerifyCapture(/* present= */ false);
    }

    /**
     * Create an AudioPolicy with a mix for injection by session id. Inject a sine wave at
     * FREQ_VOICE_STIMULI_HZ Hz and verify it's in the recording.
     */
    @Test
    public void testInjectionForAudioSessionIdMatched() {
        int sessionId = mAudioManager.generateAudioSessionId();
        setupInjectionWithMixRules(MixRule.createRule(RULE_MATCH_AUDIO_SESSION_ID, sessionId));
        mVoiceRecord = buildAudioRecordForSessionId(sessionId);

        injectAndVerifyCapture(/* present= */ true);
    }

    /**
     * Create an AudioPolicy with a mix for injection by audio session id. Inject a sine wave at
     * FREQ_VOICE_STIMULI_HZ Hz and verify it's not in the recording if session id not matched.
     */
    @Test
    public void testInjectionForAudioSessionIdNotMatched() {
        int sessionId = mAudioManager.generateAudioSessionId();
        setupInjectionWithMixRules(MixRule.createRule(RULE_MATCH_AUDIO_SESSION_ID, sessionId));
        // Generate new unique session id for the record, so it won't match the session id
        // in the mix rule.
        mVoiceRecord = buildAudioRecordForSessionId(AUDIO_SESSION_ID_GENERATE);

        injectAndVerifyCapture(/* present= */ false);
    }

    /**
     * Create an AudioPolicy with mix containing 2 rules: - Require match of the VOICE_COMMUNICATION
     * capture preset - Require match of the test app UID. Inject a sine wave and expect it will be
     * captured in the audio record with VOICE_COMMUNICATION source (attributed to the test app
     * UID).
     */
    @Test
    public void testInjectionWithMultipleRulesMatched() {
        setupInjectionWithMixRules(
                MixRule.createRule(
                        AudioMixingRule.RULE_MATCH_ATTRIBUTE_CAPTURE_PRESET,
                        AUDIO_ATTRIBUTES_VOICE_COMMUNICATION),
                MixRule.createRule(AudioMixingRule.RULE_MATCH_UID, Process.myUid()));

        mVoiceRecord = buildAudioRecordForSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION);

        injectAndVerifyCapture(/* present= */ true);
    }

    /**
     * Create an AudioPolicy with mix containing 2 rules: - Require match of the VOICE_COMMUNICATION
     * capture preset - Require match of the non existing UID. Inject a sine wave and expect it will
     * NOT be captured in the audio record with VOICE_COMMUNICATION source - the source will match,
     * but UID won't.
     */
    @Test
    public void testInjectionWithMultipleRulesNotMatched() {
        setupInjectionWithMixRules(
                MixRule.createRule(
                        AudioMixingRule.RULE_MATCH_ATTRIBUTE_CAPTURE_PRESET,
                        AUDIO_ATTRIBUTES_VOICE_COMMUNICATION),
                MixRule.createRule(AudioMixingRule.RULE_MATCH_UID, FAKE_APPLICATION_UID));
        mVoiceRecord = buildAudioRecordForSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION);

        injectAndVerifyCapture(/* present= */ false);
    }

    /**
     * Create an AudioPolicy with mix containing 2 rules: - Require match of the VOICE_COMMUNICATION
     * capture preset - Exclude non-existing UID. Inject a sine wave and expect it will NOT be
     * captured in the audio record with VOICE_RECOGNITION source - the mix exclusion won't exclude
     * the mix, but it will not match because the capture preset of the record differs -
     * (VOICE_COMMUNICATION != VOICE_RECOGNITION).
     */
    @Test
    public void testInjectionWithPositiveAndExcludeRuleNotMatched() {
        setupInjectionWithMixRules(
                MixRule.createRule(
                        AudioMixingRule.RULE_MATCH_ATTRIBUTE_CAPTURE_PRESET,
                        AUDIO_ATTRIBUTES_VOICE_COMMUNICATION),
                MixRule.createExcludeRule(AudioMixingRule.RULE_MATCH_UID, FAKE_APPLICATION_UID));
        mVoiceRecord = buildAudioRecordForSource(MediaRecorder.AudioSource.VOICE_RECOGNITION);

        injectAndVerifyCapture(/* present= */ false);
    }

    /**
     * Tests that a recording from a mix configured to be persistent records silence even
     * without a source AudioTrack.
     */
    @Test
    @RequiresFlagsEnabled(FLAG_DAP_INJECTION_STARVE_MANAGEMENT)
    public void testPersistentMixNoSourceAudioTrack() {
        int sessionId = mAudioManager.generateAudioSessionId();
        setupInjectionWithMixRules(
                true /* isPersistent */,
                MixRule.createRule(RULE_MATCH_AUDIO_SESSION_ID, sessionId));

        verifySilenceRecording(sessionId);
        // check also a second recording on the same mix
        verifySilenceRecording(sessionId);
    }

    /**
     * Tests that a recording from a mix configured to be persistent records silence even
     * with a source AudioTrack that underruns.
     */
    @Test
    @RequiresFlagsEnabled(FLAG_DAP_INJECTION_STARVE_MANAGEMENT)
    public void testPersistentMixUnderrunSourceAudioTrack() {
        int sessionId = mAudioManager.generateAudioSessionId();
        setupInjectionWithMixRules(
                true /* isPersistent */,
                MixRule.createRule(RULE_MATCH_AUDIO_SESSION_ID, sessionId));

        mInjectionTrack.play();

        verifySilenceRecording(sessionId);
        // check also a second recording on the same mix
        verifySilenceRecording(sessionId);
    }

    /**
     * Tests that a recording from a mix configured to be persistent records silence from
     * a source AudioTrack that starts while the recording is in progress.
     */
    @Test
    @RequiresFlagsEnabled(FLAG_DAP_INJECTION_STARVE_MANAGEMENT)
    public void testPersistentMixInProgressSourceAudioTrack() {
        int sessionId = mAudioManager.generateAudioSessionId();
        setupInjectionWithMixRules(
                true /* isPersistent */,
                MixRule.createRule(RULE_MATCH_AUDIO_SESSION_ID, sessionId));

        AudioRecord loopbackRecord = buildAudioRecordForSessionId(sessionId);

        SilenceCapturingThread silenceThread = new SilenceCapturingThread(loopbackRecord);
        silenceThread.start();

        SystemClock.sleep(SLEEP_AUDIO_MIX_SILENCE_ON_STARVE_MS);
        mInjectionTrack.play();

        SystemClock.sleep(SLEEP_AUDIO_MIX_SILENCE_ON_STARVE_MS);
        silenceThread.stopAndCheckSuccessfulRun();

        loopbackRecord.release();
    }

    /**
     * Tests that a recording from a mix configured to be persistent records silence from
     * a source AudioTrack that stops while the recording is in progress.
     */
    @Test
    @RequiresFlagsEnabled(FLAG_DAP_INJECTION_STARVE_MANAGEMENT)
    public void testPersistentMixStopSourceAudioTrack() {
        int sessionId = mAudioManager.generateAudioSessionId();
        setupInjectionWithMixRules(
                true /* isPersistent */,
                MixRule.createRule(RULE_MATCH_AUDIO_SESSION_ID, sessionId));

        mInjectionTrack.play();

        AudioRecord loopbackRecord = buildAudioRecordForSessionId(sessionId);
        SilenceCapturingThread silenceThread = new SilenceCapturingThread(loopbackRecord);
        silenceThread.start();

        SystemClock.sleep(SLEEP_SHORT_TIMEOUT_MS);
        mInjectionTrack.stop();
        SystemClock.sleep(SLEEP_SHORT_TIMEOUT_MS);
        mInjectionTrack.play();
        SystemClock.sleep(SLEEP_SHORT_TIMEOUT_MS);
        mInjectionTrack.stop();
        mInjectionTrack.release();
        mInjectionTrack = null;

        SystemClock.sleep(SLEEP_AUDIO_MIX_SILENCE_ON_STARVE_MS);
        silenceThread.stopAndCheckSuccessfulRun();

        loopbackRecord.release();
    }

    /**
     * Tests that multiple recordings from a mix configured to be persistent record
     * silence from a source AudioTrack that underruns.
     */
    @Test
    @RequiresFlagsEnabled(FLAG_DAP_INJECTION_STARVE_MANAGEMENT)
    public void testPersistentMixMultipleRecordings() {
        int sessionId = mAudioManager.generateAudioSessionId();
        setupInjectionWithMixRules(
                true /* isPersistent */,
                MixRule.createRule(RULE_MATCH_AUDIO_SESSION_ID, sessionId));

        mInjectionTrack.play();

        AudioRecord loopbackRecord = buildAudioRecordForSessionId(sessionId);
        SilenceCapturingThread silenceThread = new SilenceCapturingThread(loopbackRecord);
        silenceThread.start();

        AudioRecord loopbackRecord2 = buildAudioRecordForSessionId(sessionId);
        SilenceCapturingThread silenceThread2 = new SilenceCapturingThread(loopbackRecord2);
        silenceThread2.start();

        SystemClock.sleep(SLEEP_AUDIO_MIX_SILENCE_ON_STARVE_MS);
        silenceThread.stopAndCheckSuccessfulRun();
        silenceThread2.stopAndCheckSuccessfulRun();

        loopbackRecord.release();
        loopbackRecord2.release();
    }

    /** Tests regular capture from a mix that is persistent. */
    @Test
    @RequiresFlagsEnabled(FLAG_DAP_INJECTION_STARVE_MANAGEMENT)
    public void testPersistentMixRegularCapture() {
        int sessionId = mAudioManager.generateAudioSessionId();
        setupInjectionWithMixRules(
                true /* isPersistent */,
                MixRule.createRule(RULE_MATCH_AUDIO_SESSION_ID, sessionId));

        mVoiceRecord = buildAudioRecordForSessionId(sessionId);

        injectAndVerifyCapture(/* present= */ true);
    }

    private void verifySilenceRecording(int sessionId) {
        AudioRecord loopbackRecord = buildAudioRecordForSessionId(sessionId);

        SilenceCapturingThread silenceThread = new SilenceCapturingThread(loopbackRecord);
        silenceThread.start();

        // wait for a longer than the 3s standard delay for the injection track standby
        // and check if recording in still running
        SystemClock.sleep(SLEEP_AUDIO_MIX_SILENCE_ON_STARVE_MS);
        silenceThread.stopAndCheckSuccessfulRun();

        loopbackRecord.release();
    }

    private static AudioRecord buildAudioRecordForSource(int source) {
        AudioRecord record =
                new AudioRecord.Builder()
                        .setAudioSource(source)
                        .setAudioFormat(FORMAT_VOICE_RECORDING)
                        .build();
        assertEquals(
                "AudioRecord not initialized", AudioRecord.STATE_INITIALIZED, record.getState());
        return record;
    }

    private static AudioRecord buildAudioRecordForSessionId(int sessionId) {
        AudioRecord record =
                new AudioRecord.Builder()
                        .setSessionId(sessionId)
                        .setAudioFormat(FORMAT_VOICE_RECORDING)
                        .build();
        assertEquals(
                "AudioRecord not initialized", AudioRecord.STATE_INITIALIZED, record.getState());
        return record;
    }

    private void setupInjectionWithMixRules(MixRule... mixRules) {
        setupInjectionWithMixRules(false, mixRules);
    }

    private void setupInjectionWithMixRules(boolean isPersistent, MixRule... mixRules) {
        AudioMixingRule.Builder mixingRuleBuilder =
                new AudioMixingRule.Builder().setTargetMixRole(MIX_ROLE_INJECTOR);
        for (MixRule mixRule : mixRules) {
            mixRule.addToMixingRuleBuilder(mixingRuleBuilder);
        }
        AudioMix.Builder audioMixBuilder =
                new AudioMix.Builder(mixingRuleBuilder.build())
                        .setFormat(FORMAT_VOICE_INJECTION)
                        .setRouteFlags(AudioMix.ROUTE_FLAG_LOOP_BACK);

        if (isPersistent) {
            Log.i(TAG, "Set persistent for the AudioMix");
            audioMixBuilder.setPersistent(true);
        }

        AudioMix audioMix = audioMixBuilder.build();

        mAudioPolicy =
                new AudioPolicy.Builder(ApplicationProvider.getApplicationContext())
                        .addMix(audioMix)
                        .build();

        int result = mAudioManager.registerAudioPolicy(mAudioPolicy);
        assertEquals("AudioPolicy registration failed", AudioManager.SUCCESS, result);

        mInjectionTrack = mAudioPolicy.createAudioTrackSource(audioMix);
        assertEquals(
                "Injection track not initialized",
                AudioTrack.STATE_INITIALIZED,
                mInjectionTrack.getState());
    }

    private static class MixRule {
        private final int mRule;
        private final Object mValueToMatch;
        private final boolean mIsExcludeRule;

        private MixRule(int rule, Object valueToMatch, boolean exclude) {
            this.mRule = rule;
            this.mValueToMatch = valueToMatch;
            this.mIsExcludeRule = exclude;
        }

        static MixRule createRule(int rule, Object valueToMatch) {
            return new MixRule(rule, valueToMatch, /* exclude= */ false);
        }

        static MixRule createExcludeRule(int rule, Object valueToMatch) {
            return new MixRule(rule, valueToMatch, /* exclude= */ true);
        }

        void addToMixingRuleBuilder(AudioMixingRule.Builder builder) {
            if (mIsExcludeRule) {
                builder.excludeMixRule(mRule, mValueToMatch);
            } else {
                builder.addMixRule(mRule, mValueToMatch);
            }
        }
    }

    private void injectAndVerifyCapture(boolean present) {
        // prepare audio data to inject
        final int totalSamples =
                computeNumSamples(
                        INJECTION_TIME_MS,
                        mInjectionTrack.getSampleRate(),
                        mInjectionTrack.getChannelCount());
        final short[] playBuffer =
                createAudioData(
                        mInjectionTrack.getSampleRate(),
                        totalSamples,
                        mInjectionTrack.getChannelCount(),
                        FREQ_VOICE_STIMULI_HZ,
                        SIGNAL_AMPLITUDE);

        // prime the injection track with data
        final int primeFrames = Math.min(totalSamples, mInjectionTrack.getBufferSizeInFrames());
        int samplesWritten =
                mInjectionTrack.write(
                        playBuffer,
                        0,
                        primeFrames * mInjectionTrack.getChannelCount(),
                        AudioTrack.WRITE_BLOCKING);
        if (DEBUG) {
            Log.i(
                    TAG,
                    "wrote "
                            + samplesWritten
                            + " out of "
                            + totalSamples
                            + " bufferSize="
                            + mInjectionTrack.getBufferSizeInFrames()
                            + " in injection track");
        }
        assertTrue(samplesWritten > 0);

        AudioCapturingThread captureThread =
                new AudioCapturingThread(
                        mVoiceRecord,
                        READ_BUFFER_SIZE_MS,
                        CAPTURE_TIME_MS,
                        ZERO_READS_TO_FINISH_MS,
                        "capture");
        captureThread.start();
        mInjectionTrack.play();

        // keep injecting until recording is done
        while (samplesWritten < totalSamples) {
            if (DEBUG) {
                Log.i(TAG, "TEST inject " + samplesWritten + " out of " + totalSamples);
            }
            final int lastSamplesWritten =
                    mInjectionTrack.write(
                            playBuffer,
                            samplesWritten,
                            (totalSamples - samplesWritten) * mInjectionTrack.getChannelCount(),
                            AudioTrack.WRITE_NON_BLOCKING);
            assertTrue(lastSamplesWritten >= 0);
            samplesWritten += lastSamplesWritten;
            if (!captureThread.isAlive()) {
                Log.i(TAG, "capture stopped, stopping playback");
                break;
            }
        }
        captureThread.quit();
        captureThread.checkSuccessfulRun();
        // allow the capture thread to finish
        try {
            captureThread.join(500);
        } catch (InterruptedException e) {
            // ignore interruption
        }
        assertFalse("voice capture thread did not stop", captureThread.isAlive());

        assertFrequencyPresence("Voice", captureThread, FREQ_VOICE_STIMULI_HZ, present);

        mInjectionTrack.stop();
        mVoiceRecord.stop();
    }

    // FIXME: Candidate for reuse across tests
    static short[] createAudioData(
            int samplingRate,
            int numSamples,
            int channelCount,
            double signalFrequencyHz,
            float amplitude) {
        if (DEBUG) {
            Log.i(
                    TAG,
                    "createAudioData samplingRate "
                            + samplingRate
                            + " num samples "
                            + numSamples
                            + " channelCount "
                            + channelCount
                            + " signal f "
                            + signalFrequencyHz);
        }
        short[] playBuffer = new short[numSamples];
        final double multiplier = 2f * Math.PI * signalFrequencyHz / samplingRate;
        for (int i = 0; i < numSamples; ) {
            double vDouble = amplitude * Math.sin(multiplier * ((double) i / channelCount));
            short v = (short) vDouble;
            for (int c = 0; c < channelCount; c++) {
                playBuffer[i] = v;
                i++;
            }
        }
        return playBuffer;
    }

    // FIXME: Candidate for reuse across tests
    /**
     * Computes the relative power of a given frequency within a frame of the signal. See:
     * http://en.wikipedia.org/wiki/Goertzel_algorithm
     */
    static double goertzel(
            int signalFreq, int samplingFreq, short[] samples, int offset, int length, int stride) {
        final int n = length / stride;
        final double coeff = Math.cos(signalFreq * 2 * Math.PI / samplingFreq) * 2;
        double s1 = 0;
        double s2 = 0;
        double rms = 0;
        for (int i = 0; i < n; i++) {
            double hamming = 0.54 - 0.46 * Math.cos(2 * Math.PI * i / (n - 1));
            double x = samples[i * stride + offset] * hamming; // apply hamming window
            double s = x + coeff * s1 - s2;
            s2 = s1;
            s1 = s;
            rms += x * x;
        }
        rms = Math.sqrt(rms / n);
        double magnitude = s2 * s2 + s1 * s1 - coeff * s1 * s2;
        return Math.sqrt(magnitude) / n / rms;
    }

    // FIXME: Candidate for reuse across tests
    /**
     * @return the buffer size in sample for provided time.
     */
    private static int computeNumSamples(int timeMs, int samplingRate, int channelCount) {
        return (int) ((long) timeMs * samplingRate * channelCount / 1000);
    }

    // FIXME: Candidate for reuse across tests
    private void assertFrequencyPresence(
            String msg, AudioCapturingThread thread, int freq, boolean present) {
        assertThat(
                msg + " power",
                thread.getCapturedPowerSpectrum(freq),
                present
                        ? greaterThan(POWER_THRESHOLD_FOR_PASS)
                        : lessThan(POWER_THRESHOLD_FOR_OTHER_SIGNAL));
    }

    // FIXME: Candidate for reuse across tests
    private static class AudioCapturingThread extends Thread {
        private final AudioRecord mAudioRecord;
        private final int mReadBufferSize;
        private final int mNumNonZeroReadsToFinish;
        private final int mNumZeroReadsToFinish;
        private final List<short[]> mCapturedData = new ArrayList<>();
        private volatile Exception mException;
        private final String mDebugId;
        private final Semaphore mStartWait = new Semaphore(0);
        private final Semaphore mQuitWait = new Semaphore(0);
        private boolean mQuit = false;

        private int computeNumSamples(int timeMs) {
            return AudioPolicyInjectionTest.computeNumSamples(
                    timeMs, mAudioRecord.getSampleRate(), mAudioRecord.getChannelCount());
        }

        AudioCapturingThread(
                AudioRecord record,
                int readBufferSizeMs,
                int nonZeroTimeToFinishMs,
                int zeroTimeToFinishMs,
                String debugId) {
            super();
            mAudioRecord = record;
            mReadBufferSize = computeNumSamples(readBufferSizeMs);
            mNumNonZeroReadsToFinish = computeNumSamples(nonZeroTimeToFinishMs) / mReadBufferSize;
            mNumZeroReadsToFinish = computeNumSamples(zeroTimeToFinishMs) / mReadBufferSize;
            mDebugId = debugId;
        }

        void checkSuccessfulRun() {
            if (mException != null) {
                Log.e(TAG, "AudioCapturingThread failed", mException);
                fail(mException.getMessage());
            }
        }

        double getCapturedPowerSpectrum(int expectedSignalFreq) {
            int samplingFreq = mAudioRecord.getSampleRate();
            int channelCount = mAudioRecord.getChannelCount();
            if (DEBUG) {
                Log.i(
                        TAG,
                        "checkCapturedPowerSpectrum expected f "
                                + expectedSignalFreq
                                + " samplingFreq "
                                + samplingFreq
                                + ", "
                                + mDebugId);
            }
            int numBuffers = mCapturedData.size();
            double power = 0;
            int sampleNb = 0;
            for (short[] data : mCapturedData) {
                for (int i = 0; i < channelCount; i++) {
                    // Get the power in that channel
                    power +=
                            goertzel(
                                            expectedSignalFreq,
                                            samplingFreq,
                                            data,
                                            i /*offset*/,
                                            data.length,
                                            channelCount)
                                    / channelCount;
                }
                sampleNb += data.length;
            }
            if (numBuffers != 0) {
                power /= numBuffers; // If there are no samples return 0 as silence was dropped
            }
            if (DEBUG) {
                Log.i(
                        TAG,
                        "Power of captured signal "
                                + power
                                + " averaged over "
                                + sampleNb * 1000 / (samplingFreq * channelCount)
                                + "ms ("
                                + sampleNb
                                + ") frames");
            }
            return power;
        }

        @Override
        public synchronized void start() {
            super.start();
            try {
                mStartWait.tryAcquire(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                // ignore
            }
        }

        synchronized void quit() {
            mQuit = true;
            interrupt();
            try {
                mQuitWait.tryAcquire(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                // ignore
            }
        }

        @Override
        public void run() {
            if (DEBUG) {
                Log.i(TAG, "start audio capturing, " + mDebugId);
            }
            mAudioRecord.startRecording();
            if (DEBUG) {
                Log.i(TAG, "before release");
            }
            mStartWait.release();
            if (DEBUG) {
                Log.i(TAG, "after release");
            }
            assertEquals(
                    "AudioRecord is not recording",
                    AudioRecord.RECORDSTATE_RECORDING,
                    mAudioRecord.getRecordingState());
            int numNonZeroReads = 0;
            int numZeroRead = 0;
            short[] readBuffer = null;
            while (numNonZeroReads < mNumNonZeroReadsToFinish
                    && numZeroRead < mNumZeroReadsToFinish
                    && !mQuit) {
                if (readBuffer == null) {
                    readBuffer = new short[mReadBufferSize];
                }
                int toRead = mReadBufferSize;
                while (toRead > 0) {
                    int read = mAudioRecord.read(readBuffer, mReadBufferSize - toRead, toRead);
                    if (DEBUG) {
                        Log.i(
                                TAG,
                                "read "
                                        + read
                                        + "/"
                                        + mReadBufferSize
                                        + ", "
                                        + mDebugId
                                        + ", Data buffer left: "
                                        + numNonZeroReads
                                        + "/"
                                        + mNumNonZeroReadsToFinish
                                        + " || empty: "
                                        + numZeroRead
                                        + "/"
                                        + mNumZeroReadsToFinish);
                    }
                    if (read < 0) {
                        mException = new Exception("read returned " + read);
                        break;
                    }
                    toRead -= read;
                }
                if (mException != null) {
                    break;
                }
                if (checkIfNonZeroFast(readBuffer)) {
                    if (DEBUG) {
                        Log.i(TAG, "found non-zero data, " + mDebugId);
                    }
                    numZeroRead = 0;
                    mCapturedData.add(readBuffer);
                    readBuffer = null; // re-allocate at the beginning of while
                    numNonZeroReads++;
                } else {
                    numZeroRead++;
                    if (DEBUG) {
                        Log.i(TAG, "zero data, " + mDebugId);
                    }
                }
            }
            try {
                mAudioRecord.stop();
            } catch (Exception e) {
                if (mException == null) {
                    mException = e;
                } else {
                    Log.w(TAG, "Error stopping audio record during exception handling: ", e);
                }
            }
            if (DEBUG) {
                Log.i(TAG, "stopped audio capturing, " + mDebugId);
            }
            mQuitWait.release();
        }

        private boolean checkIfNonZeroFast(short[] data) {
            final int kReadLength = 20; // read this much for one zone.
            if (data.length <= kReadLength * 3) {
                return checkIfNonZero(data, 0, data.length);
            }
            // check only start, end, and middle parts.
            int[] startOffsets = {0, (data.length - kReadLength) / 2, data.length - kReadLength};
            for (int offset : startOffsets) {
                if (checkIfNonZero(data, offset, kReadLength)) {
                    return true;
                }
            }
            return false;
        }

        private boolean checkIfNonZero(short[] data, int offset, int length) {
            for (int i = offset; i < offset + length; i++) {
                if (data[i] != 0) {
                    return true;
                }
            }
            return false;
        }
    }

    private static class SilenceCapturingThread extends Thread {
        private final AudioRecord mAudioRecord;
        private final int mReadBufferSize;
        private volatile Exception mException;
        private volatile boolean mQuit = false;
        private final Semaphore mQuitWait = new Semaphore(0);

        SilenceCapturingThread(AudioRecord record) {
            super();
            mAudioRecord = record;
            mReadBufferSize =
                    AudioPolicyInjectionTest.computeNumSamples(
                            READ_BUFFER_SIZE_MS,
                            mAudioRecord.getSampleRate(),
                            mAudioRecord.getChannelCount());
        }

        synchronized void stopAndCheckSuccessfulRun() {
            mQuit = true;
            try {
                mQuitWait.tryAcquire(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                // ignore
            }

            if (mException != null) {
                Log.e(TAG, "SilenceCapturingThread failed", mException);
                fail(mException.getMessage());
            }
        }

        @Override
        public void run() {
            mAudioRecord.startRecording();
            assertEquals(
                    "AudioRecord is not recording (silence)",
                    AudioRecord.RECORDSTATE_RECORDING,
                    mAudioRecord.getRecordingState());

            short[] readBuffer = new short[mReadBufferSize];
            while (!mQuit) {
                // keep reading silent buffers
                int read = mAudioRecord.read(readBuffer, 0, mReadBufferSize);

                if (read < 0) {
                    mException = new Exception("Silence read returned " + read);
                    break;
                }

                if (read == 0) {
                    mException = new Exception("Silence read returned no data");
                    break;
                }

                if (!isSilentBuffer(readBuffer)) {
                    Log.w(TAG, "Read non silence data: " + Arrays.toString(readBuffer));
                    mException = new Exception("Read NON silence data returned " + read);
                    break;
                }

                // silence recording is expected only from the remote submix device
                if (mAudioRecord.getRoutedDevice() == null
                        || mAudioRecord.getRoutedDevice().getType()
                                != AudioDeviceInfo.TYPE_REMOTE_SUBMIX) {
                    mException = new Exception("Silence read from unexpected source.");
                    break;
                }
            }

            try {
                mAudioRecord.stop();
            } catch (Exception e) {
                if (mException == null) {
                    mException = e;
                } else {
                    Log.w(TAG, "Error stopping silence audio record: ", e);
                }
            }

            mQuitWait.release();
        }

        private static boolean isSilentBuffer(short[] data) {
            for (int i = 0; i < data.length; i++) {
                if (data[i] != 0) {
                    return false;
                }
            }
            return true;
        }
    }
}
