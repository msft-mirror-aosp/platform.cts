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

import android.content.Context;
import android.content.res.Resources;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.media.PlaybackParams;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;
import com.android.compatibility.common.util.CddTest;
import com.android.cts.verifier.CtsVerifierReportLog;
import com.android.cts.verifier.R;
import com.android.compatibility.common.util.CddTest;
import com.android.cts.verifier.CtsVerifierReportLog;
import com.android.cts.verifier.R;
import com.android.cts.verifier.audio.wavelib.DspBufferDouble;
import com.android.cts.verifier.audio.wavelib.DspBufferMath;
import com.android.cts.verifier.audio.wavelib.PipeShort;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;

@CddTest(requirement = "5.5.5")
public class AudioPlaybackParametersActivity
        extends AudioFrequencyActivity implements View.OnClickListener {
    private static final String TAG = "AudioPlaybackParametersActivity";

    private static final int TEST_PLAYBACK_PARAMS = 0;

    private static final int RESULT_CODE_OK = 0;
    private static final int RESULT_CODE_FAILED = 1;
    private static final int RESULT_CODE_NOT_RUN = 2;

    private static final double CORRELATION_PASS_THRESHOLD = 0.7;
    private static final double WEIGHING_CONSTANT = 0.9;

    private Button mButtonTest;
    private ProgressBar mProgress;
    private TextView mResultText;
    private MediaPlayer mMediaPlayer;

    private Button mButtonPlayOriginal;
    private Button mButtonPlayRecording1;
    private Button mButtonPlayRecording2;
    private TextView mAnalysisResultText;

    private File mRecording1File;
    private File mRecording2File;
    private File mControlRecordingFile;

    private int mResultCode = RESULT_CODE_NOT_RUN;
    private double mCorrelation = 0.0;

    private final int mSelectedRecordSource = MediaRecorder.AudioSource.UNPROCESSED;
    private final int mBlockSizeSamples = 4096;

    private final float MIN_RMS_DB = -60.0f; //dB
    private final float MIN_RMS_VAL = (float)Math.pow(10,(MIN_RMS_DB/20));
    private static final float MAX_VAL = (float)(1 << 15);

    private Thread mTestThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.audio_playback_params_activity);

        mButtonTest = (Button) findViewById(R.id.audio_playback_params_button_test);
        mButtonTest.setOnClickListener(this);
        mProgress = (ProgressBar) findViewById(R.id.audio_playback_params_progress_bar);
        mResultText = (TextView) findViewById(R.id.audio_playback_params_test_result);

        mButtonPlayOriginal = (Button) findViewById(
                R.id.audio_playback_params_button_play_original);
        mButtonPlayOriginal.setOnClickListener(this);
        mButtonPlayRecording1 = (Button) findViewById(
                R.id.audio_playback_params_button_play_rec1);
        mButtonPlayRecording1.setOnClickListener(this);
        mButtonPlayRecording2 = (Button) findViewById(
                R.id.audio_playback_params_button_play_rec2);
        mButtonPlayRecording2.setOnClickListener(this);
        mAnalysisResultText = (TextView) findViewById(
                R.id.audio_playback_params_analysis_result);

        showView(mProgress, false);
        setPlaybackButtonsEnabled(false);

        setPassFailButtonClickListeners();
        getPassButton().setEnabled(false);

        setInfoResources(R.string.audio_playback_params_test,
                R.string.audio_playback_params_info, -1);
    }

    private void showView(View v, boolean show) {
        v.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.audio_playback_params_button_test) {
            startTest();
        } else if (id == R.id.audio_playback_params_button_play_original) {
            playRawAudio(mControlRecordingFile);
        } else if (id == R.id.audio_playback_params_button_play_rec1) {
            playRawAudio(mRecording1File);
        } else if (id == R.id.audio_playback_params_button_play_rec2) {
            playRawAudio(mRecording2File);
        }
    }

    private android.media.MediaFormat getAudioFormat(Object source) throws java.io.IOException {
        android.media.MediaExtractor extractor = new android.media.MediaExtractor();
        if (source instanceof Integer) {
            android.content.res.AssetFileDescriptor afd =
                    getResources().openRawResourceFd((Integer) source);
            extractor.setDataSource(
                    afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
        } else if (source instanceof File) {
            extractor.setDataSource(((File) source).getAbsolutePath());
        } else {
            throw new IllegalArgumentException("Unsupported audio source type");
        }
        android.media.MediaFormat format = extractor.getTrackFormat(0);
        extractor.release();
        return format;
    }

    private short[] readPcmData(Object source) throws java.io.IOException {
        android.media.MediaExtractor extractor = new android.media.MediaExtractor();
        if (source instanceof Integer) {
            android.content.res.AssetFileDescriptor afd =
                    getResources().openRawResourceFd((Integer) source);
            extractor.setDataSource(
                    afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
        } else if (source instanceof File) {
            extractor.setDataSource(((File) source).getAbsolutePath());
        } else {
            throw new IllegalArgumentException("Unsupported audio source type");
        }
        extractor.selectTrack(0);
        android.media.MediaFormat format = extractor.getTrackFormat(0);

        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        // Allocate a large buffer to read the audio data. 1-2 MB is recommended.
        java.nio.ByteBuffer byteBuffer = java.nio.ByteBuffer.allocate(16 * 1024 * 100);
        while (true) {
            byteBuffer.clear();
            Log.d(TAG, "reading data");
            int sampleSize = extractor.readSampleData(byteBuffer, 0);
            Log.d(TAG, "read data " + sampleSize);
            if (sampleSize < 0) {
                break;
            }
            byte[] buffer = new byte[sampleSize];
            byteBuffer.position(0);
            byteBuffer.get(buffer, 0, sampleSize);
            baos.write(buffer);

            if (!extractor.advance()) {
                break;
            }
        }
        extractor.release();

        byte[] pcmBytes = baos.toByteArray();
        int pcmEncoding = format.containsKey(android.media.MediaFormat.KEY_PCM_ENCODING)
                ? format.getInteger(android.media.MediaFormat.KEY_PCM_ENCODING)
                : AudioFormat.ENCODING_PCM_16BIT;

        if (pcmEncoding == AudioFormat.ENCODING_PCM_8BIT) {
            short[] shorts = new short[pcmBytes.length];
            for (int i = 0; i < pcmBytes.length; i++) {
                shorts[i] = (short) (((pcmBytes[i] & 0xFF) - 128) << 8);
            }
            return shorts;
        } else { // Assume 16-bit
            return byteToShort(pcmBytes);
        }
    }

    private void startTest() {
        if (mTestThread != null && mTestThread.isAlive()) {
            Log.v(TAG,"test Thread already running.");
            return;
        }

        mTestThread = new Thread(new AudioTestRunner(TAG, TEST_PLAYBACK_PARAMS, mMessageHandler) {
            public void run() {
                super.run();
                mResultCode = RESULT_CODE_NOT_RUN;
                setPlaybackButtonsEnabled(false);

                // Step 1: Play speech at 1.0x speed and record it as a control.
                sendMessage(AudioTestRunner.TEST_MESSAGE,
                        "Playing at 1.0x speed and recording (control)...");
                mControlRecordingFile = recordPlayback(1.0f, 1.0f, R.raw.speech);
                if (mControlRecordingFile == null) {
                    sendMessage(AudioTestRunner.TEST_ENDED_ERROR,
                            "Failed to record control playback.");
                    return;
                }
                Log.d(TAG, "Control Recording size: " + mControlRecordingFile.length());
                sendMessage(AudioTestRunner.TEST_MESSAGE,
                        "Control recording saved to: " + mControlRecordingFile.getAbsolutePath());

                // Step 2: Play speech at 2.0x speed and record it.
                sendMessage(AudioTestRunner.TEST_MESSAGE,
                        "Playing at 2.0x speed and recording...");
                mRecording1File = recordPlayback(2.0f, 1.0f, R.raw.speech);
                if (mRecording1File == null) {
                    sendMessage(AudioTestRunner.TEST_ENDED_ERROR,
                            "Failed to record first playback.");
                    return;
                }
                Log.d(TAG, "Recording 1 size: " + mRecording1File.length());
                sendMessage(AudioTestRunner.TEST_MESSAGE,
                        "Recording 1 saved to: " + mRecording1File.getAbsolutePath());

                // Step 3: Play the first recording at 0.5x speed and record it again.
                sendMessage(AudioTestRunner.TEST_MESSAGE,
                        "Playing first recording at 0.5x speed and recording...");
                mRecording2File = recordPlayback(0.5f, 1.0f, mRecording1File);
                if (mRecording2File == null) {
                    sendMessage(AudioTestRunner.TEST_ENDED_ERROR,
                            "Failed to record second playback.");
                    return;
                }
                Log.d(TAG, "Recording 2 size: " + mRecording2File.length());
                sendMessage(AudioTestRunner.TEST_MESSAGE,
                        "Recording 2 saved to: " + mRecording2File.getAbsolutePath());

                try {
                    File publicDir = Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_DOWNLOADS);
                    File publicFile = new File(publicDir, "playback_params_final_recording.wav");
                    try (FileInputStream in = new FileInputStream(mRecording2File);
                         FileOutputStream out = new FileOutputStream(publicFile)) {
                        byte[] buffer = new byte[1024];
                        int read;
                        while ((read = in.read(buffer)) != -1) {
                            out.write(buffer, 0, read);
                        }
                    }
                    sendMessage(AudioTestRunner.TEST_MESSAGE,
                            "Final recording saved to: " + publicFile.getAbsolutePath());
                } catch (IOException e) {
                    Log.e(TAG, "Error saving final recording to public directory", e);
                }

                // Step 4: Analyze the recordings.
                sendMessage(AudioTestRunner.TEST_MESSAGE, "Analyzing recordings...");
                analyzeRecordings();

                if (mCorrelation >= CORRELATION_PASS_THRESHOLD) {
                    mResultCode = RESULT_CODE_OK;
                    sendMessage(AudioTestRunner.TEST_ENDED_OK, "Test passed.");
                } else {
                    mResultCode = RESULT_CODE_FAILED;
                    sendMessage(AudioTestRunner.TEST_ENDED_ERROR,
                            "Test failed. Correlation too low.");
                }
            }
        });
        mTestThread.start();
    }

    private File recordPlayback(float speed, float pitch, Object source) {
        File rawFile = null;
        File wavFile = null;
        android.media.MediaFormat format;
        int sampleRate;
        int channelCount;
        int channelConfig;

        try {
            format = getAudioFormat(source);
            sampleRate = format.getInteger(android.media.MediaFormat.KEY_SAMPLE_RATE);
            channelCount = format.getInteger(android.media.MediaFormat.KEY_CHANNEL_COUNT);
            int pcmEncoding = format.containsKey(android.media.MediaFormat.KEY_PCM_ENCODING)
                    ? format.getInteger(android.media.MediaFormat.KEY_PCM_ENCODING)
                    : AudioFormat.ENCODING_PCM_16BIT;
            int bitsPerSample = pcmEncoding == AudioFormat.ENCODING_PCM_8BIT ? 8 : 16;


            switch (channelCount) {
                case 1 -> channelConfig = AudioFormat.CHANNEL_IN_MONO;
                case 2 -> channelConfig = AudioFormat.CHANNEL_IN_STEREO;
                default -> {
                    Log.e(TAG, "Unsupported channel count: " + channelCount);
                    return null;
                }
            }

            Log.e(TAG, String.format("Audio source format: Sample Rate: %d, Channels: %d, "
                    + "Encoding: %d", sampleRate, channelCount, pcmEncoding));

            rawFile = File.createTempFile("playback_record", ".raw", getCacheDir());
            FileOutputStream fos = new FileOutputStream(rawFile);

            if (source instanceof Integer) {
                mMediaPlayer = MediaPlayer.create(getApplicationContext(), (Integer) source);
            } else if (source instanceof File) {
                mMediaPlayer = new MediaPlayer();
                mMediaPlayer.setDataSource(((File) source).getAbsolutePath());
                mMediaPlayer.prepare();
            } else {
                return null; // Should not happen
            }

            PlaybackParams params = new PlaybackParams();
            params.setSpeed(speed);
            params.setPitch(pitch);
            mMediaPlayer.setPlaybackParams(params);

            final CountDownLatch latch = new CountDownLatch(1);
            mMediaPlayer.setOnCompletionListener(mp -> latch.countDown());

            int minRecordBuffSizeInBytes = AudioRecord.getMinBufferSize(sampleRate,
                    channelConfig, pcmEncoding);
            final AudioRecord recorder = new AudioRecord(mSelectedRecordSource, sampleRate,
                    channelConfig, pcmEncoding,
                    2 * minRecordBuffSizeInBytes);

            final Thread recordingThread = new Thread(() -> {
                recorder.startRecording();
                byte[] buffer = new byte[minRecordBuffSizeInBytes];
                while (!Thread.currentThread().isInterrupted()) {
                    int bytesRead = recorder.read(buffer, 0, buffer.length);
                    if (bytesRead > 0) {
                        try {
                            fos.write(buffer, 0, bytesRead);
                        } catch (IOException e) {
                            Log.e(TAG, "Error writing to recording file", e);
                            break;
                        }
                    } else if (bytesRead < 0) {
                        Log.e(TAG, "AudioRecord read error: " + bytesRead);
                        break;
                    }
                }
                recorder.stop();
                recorder.release();
                try {
                    fos.close();
                } catch (IOException e) {
                    Log.e(TAG, "Error closing file stream", e);
                }
            });

            recordingThread.start();
            mMediaPlayer.start();

            latch.await();

            recordingThread.interrupt();
            recordingThread.join();
            mMediaPlayer.release();
            mMediaPlayer = null;

            Log.d(TAG, "Raw recording size: " + rawFile.length());

            wavFile = File.createTempFile("playback_record", ".wav", getCacheDir());
            try (FileInputStream rawIs = new FileInputStream(rawFile);
                 FileOutputStream wavOs = new FileOutputStream(wavFile)) {
                writeWavHeader(wavOs, (int) rawFile.length(), sampleRate, channelCount,
                        bitsPerSample);
                byte[] buffer = new byte[1024 * 4];
                int bytesRead;
                while ((bytesRead = rawIs.read(buffer)) != -1) {
                    wavOs.write(buffer, 0, bytesRead);
                }
            }
            return wavFile;

        } catch (IOException | InterruptedException e) {
            Log.e(TAG, "Exception during recordPlayback", e);
            if (wavFile != null) {
                wavFile.delete();
            }
            return null;
        } finally {
            if (rawFile != null) {
                rawFile.delete();
            }
        }
    }

    private void setPlaybackButtonsEnabled(boolean enabled) {
        runOnUiThread(() -> {
            mButtonPlayOriginal.setEnabled(enabled);
            mButtonPlayRecording1.setEnabled(enabled);
            mButtonPlayRecording2.setEnabled(enabled);
        });
    }

    private void playRawAudio(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        try {
            // Play using media player now that it is a wav file
            mMediaPlayer = new MediaPlayer();
            mMediaPlayer.setDataSource(file.getAbsolutePath());
            mMediaPlayer.prepare();
            mMediaPlayer.setOnCompletionListener(mp -> {
                mp.release();
                mMediaPlayer = null;
            });
            mMediaPlayer.start();
        } catch (IOException e) {
            Log.e(TAG, "Error playing raw file", e);
        }
    }

    private static void writeWavHeader(FileOutputStream out, int pcmDataSize, int sampleRate, int channels,
            int bitsPerSample)
            throws IOException {
        long byteRate = (long) sampleRate * channels * (bitsPerSample / 8);
        int blockAlign = channels * (bitsPerSample / 8);

        int headerSize = 44;
        long riffChunkSize = (long) pcmDataSize + headerSize - 8;

        byte[] header = new byte[headerSize];

        // RIFF chunk
        header[0] = 'R'; header[1] = 'I'; header[2] = 'F'; header[3] = 'F';
        header[4] = (byte) (riffChunkSize & 0xff);
        header[5] = (byte) ((riffChunkSize >> 8) & 0xff);
        header[6] = (byte) ((riffChunkSize >> 16) & 0xff);
        header[7] = (byte) ((riffChunkSize >> 24) & 0xff);
        header[8] = 'W'; header[9] = 'A'; header[10] = 'V'; header[11] = 'E';

        // fmt sub-chunk
        header[12] = 'f'; header[13] = 'm'; header[14] = 't'; header[15] = ' ';
        header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0; // sub-chunk size 16
        header[20] = 1; header[21] = 0; // audio format 1 (PCM)
        header[22] = (byte) channels; header[23] = 0;
        header[24] = (byte) (sampleRate & 0xff);
        header[25] = (byte) ((sampleRate >> 8) & 0xff);
        header[26] = (byte) ((sampleRate >> 16) & 0xff);
        header[27] = (byte) ((sampleRate >> 24) & 0xff);
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        header[32] = (byte) blockAlign; header[33] = 0;
        header[34] = (byte) bitsPerSample; header[35] = 0;

        // data sub-chunk
        header[36] = 'd'; header[37] = 'a'; header[38] = 't'; header[39] = 'a';
        header[40] = (byte) (pcmDataSize & 0xff);
        header[41] = (byte) ((pcmDataSize >> 8) & 0xff);
        header[42] = (byte) ((pcmDataSize >> 16) & 0xff);
        header[43] = (byte) ((pcmDataSize >> 24) & 0xff);

        out.write(header);
    }

    private void analyzeRecordings() {
        try {
            // Read original and final recordings as shorts
            short[] originalShorts = readPcmData(mControlRecordingFile);
            short[] finalShorts = readPcmData(mRecording2File);

            Log.d(TAG, "Original audio length (samples): " + originalShorts.length);
            Log.d(TAG, "Final audio length (samples): " + finalShorts.length);

            // Calculate RMS snapshots for both
            int originalShotCount = (int)Math.ceil(
                    (double)originalShorts.length / mBlockSizeSamples);
            RmsHelper originalRms = new RmsHelper(mBlockSizeSamples, originalShotCount);
            originalRms.addSamples(originalShorts);
            Log.v(TAG, "Original RMS Snapshots: " + Arrays.toString(
                    originalRms.getRmsSnapshots().mData));

            int finalShotCount = (int)Math.ceil((double)finalShorts.length / mBlockSizeSamples);
            RmsHelper finalRms = new RmsHelper(mBlockSizeSamples, finalShotCount);
            finalRms.addSamples(finalShorts);
            Log.v(TAG, "Final RMS Snapshots: "
            + Arrays.toString(finalRms.getRmsSnapshots().mData));

            // Correlate the RMS snapshots
            int shotCount = Math.min(originalShotCount, finalShotCount);
            // Correlate the middle 80% of the recording to avoid startup/shutdown artifacts.
            int firstShot = shotCount / 10;
            int lastShot = shotCount * 9 / 10;
            mCorrelation = computeAcousticCouplingFactor(originalRms.getRmsSnapshots(),
                    finalRms.getRmsSnapshots(), firstShot, lastShot);

            runOnUiThread(() -> {
                mAnalysisResultText.setText(String.format("Correlation: %.4f", mCorrelation));
            });

        } catch (IOException e) {
            Log.e(TAG, "Error during analysis", e);
        }
    }

    //RMS helpers
    public class RmsHelper {
        private double mRmsCurrent;
        public int mBlockSize;
        private int mShotCount;
        public boolean mRunning = false;

        private DspBufferDouble mRmsSnapshots;
        private int mShotIndex;

        public RmsHelper(int blockSize, int shotCount) {
            mBlockSize = blockSize;
            mShotCount = shotCount;
            reset();
        }

        public void reset() {
            mRmsSnapshots = new DspBufferDouble(mShotCount);
            mShotIndex = 0;
            mRmsCurrent = 0;
            mRunning = false;
        }

        public void captureShot() {
            if (mShotIndex >= 0 && mShotIndex < mRmsSnapshots.getSize()) {
                mRmsSnapshots.setValue(mShotIndex++, mRmsCurrent);
            }
        }

        public void setRunning(boolean running) {
            mRunning = running;
        }

        public double getRmsCurrent() {
            return mRmsCurrent;
        }

        public DspBufferDouble getRmsSnapshots() {
            return mRmsSnapshots;
        }

        public void addSamples(short[] samples) {
            for (int i = 0; i < samples.length; i += mBlockSize) {
                int size = Math.min(mBlockSize, samples.length - i);
                double rmsTempSum = 0;
                for (int j = 0; j < size; j++) {
                    float value = samples[i + j] / MAX_VAL;
                    rmsTempSum += value * value;
                }
                double rms = size > 0 ? Math.sqrt(rmsTempSum / size) : 0f;
                if (rms < MIN_RMS_VAL) {
                    rms = MIN_RMS_VAL;
                }

                double alpha = WEIGHING_CONSTANT;
                mRmsCurrent = rms * alpha + mRmsCurrent * (1.0 - alpha);

                captureShot();
            }
        }
    }

    //compute Acoustic Coupling Factor
    private double computeAcousticCouplingFactor(DspBufferDouble buffRmsPlayer,
                                                 DspBufferDouble buffRmsRecorder,
                                                 int firstShot, int lastShot) {
        int len = Math.min(buffRmsPlayer.getSize(), buffRmsRecorder.getSize());

        firstShot = Math.max(firstShot, 0);
        lastShot = Math.min(lastShot, len -1);

        int actualLen = lastShot - firstShot + 1;

        double maxValue = 0;
        if (actualLen > 0) {
            DspBufferDouble rmsPlayerdB = new DspBufferDouble(actualLen);
            DspBufferDouble rmsRecorderdB = new DspBufferDouble(actualLen);
            DspBufferDouble crossCorr = new DspBufferDouble(actualLen);

            for (int i = firstShot, index = 0; i <= lastShot; ++i, ++index) {
                double valPlayerdB
                         = Math.max(20 * Math.log10(buffRmsPlayer.mData[i]), MIN_RMS_DB);
                rmsPlayerdB.setValue(index, valPlayerdB);
                double valRecorderdB = Math.max(20 * Math.log10(buffRmsRecorder.mData[i]),
                        MIN_RMS_DB);
                rmsRecorderdB.setValue(index, valRecorderdB);
                Log.v(TAG, String.format("RMS dB values at index %d - Player: %.4f, Recorder: %.4f",
                        i, valPlayerdB, valRecorderdB));
            }

            //cross correlation...
            if (DspBufferMath.crossCorrelation(crossCorr, rmsPlayerdB, rmsRecorderdB) !=
                    DspBufferMath.MATH_RESULT_SUCCESS) {
                Log.v(TAG, "math error in cross correlation");
            }
            // Also test reverse correlation to check for negative lag.
            DspBufferDouble crossCorrReverse = new DspBufferDouble(actualLen);
            if (DspBufferMath.crossCorrelation(crossCorrReverse, rmsRecorderdB, rmsPlayerdB) !=
                    DspBufferMath.MATH_RESULT_SUCCESS) {
                Log.v(TAG, "math error in reverse cross correlation");
            }

            Log.v(TAG, "Cross-correlation buffer (positive lag): " +
                  Arrays.toString(crossCorr.mData));
            Log.v(TAG, "Cross-correlation buffer (negative lag): " +
                  Arrays.toString(crossCorrReverse.mData));

            int maxIndex = -1;
            for (int i = 0; i < actualLen; i++) {
                if (Math.abs(crossCorr.mData[i]) > maxValue) {
                    maxValue = Math.abs(crossCorr.mData[i]);
                    maxIndex = i;
                }
            }

            double maxReverseValue = 0;
            int maxReverseIndex = -1;
            for (int i = 0; i < actualLen; i++) {
                if (Math.abs(crossCorrReverse.mData[i]) > maxReverseValue) {
                    maxReverseValue = Math.abs(crossCorrReverse.mData[i]);
                    maxReverseIndex = i;
                }
            }
            if (maxReverseValue > maxValue) {
                maxValue = maxReverseValue;
                // This now represents a negative lag.
                maxIndex = -maxReverseIndex;
            }
            Log.v(TAG, String.format("Max correlation of %.4f found at lag %d",
                    maxValue, maxIndex));
        }
        return maxValue;
    }

    private short[] byteToShort(byte[] bytes) {
        short[] shorts = new short[bytes.length / 2];
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts);
        return shorts;
    }

    private static final String SECTION_PLAYBACK_PARAMS = "playback_params_activity";
    private static final String KEY_RESULT_CODE = "result_code";
    private static final String KEY_CORRELATION = "correlation";

    @Override
    public final String getReportSectionName() {
        return setTestNameSuffix(sCurrentDisplayMode, SECTION_PLAYBACK_PARAMS);
    }

    @Override
    public void recordTestResults() {
        CtsVerifierReportLog reportLog = getReportLog();
        reportLog.addValue(
                KEY_RESULT_CODE,
                mResultCode,
                ResultType.NEUTRAL,
                ResultUnit.NONE);
        reportLog.addValue(
                KEY_CORRELATION,
                mCorrelation,
                ResultType.NEUTRAL,
                ResultUnit.NONE);
        reportLog.submit();
    }

    private AudioTestRunner.AudioTestRunnerMessageHandler mMessageHandler =
            new AudioTestRunner.AudioTestRunnerMessageHandler() {
        @Override
        public void testStarted(int testId, String str) {
            super.testStarted(testId, str);
            Log.v(TAG, "Test Started! " + testId + " str:"+str);
            showView(mProgress, true);
            getPassButton().setEnabled(false);
            mResultText.setText("test in progress..");
        }

        @Override
        public void testMessage(int testId, String str) {
            super.testMessage(testId, str);
            Log.v(TAG, "Message TestId: " + testId + " str:"+str);
            mResultText.setText("test in progress.. " + str);
        }

        @Override
        public void testEndedOk(int testId, String str) {
            super.testEndedOk(testId, str);
            Log.v(TAG, "Test EndedOk. " + testId + " str:" + str);
            showView(mProgress, false);
            mResultText.setText("test completed. " + str);
            if (mResultCode == RESULT_CODE_OK) {
                getPassButton().setEnabled(true);
            }
            setPlaybackButtonsEnabled(true);
        }

        @Override
        public void testEndedError(int testId, String str) {
            super.testEndedError(testId, str);
            Log.v(TAG, "Test EndedError. " + testId + " str:"+str);
            showView(mProgress, false);
            mResultText.setText("test failed. " + str);
            setPlaybackButtonsEnabled(true);
        }
    };
}
