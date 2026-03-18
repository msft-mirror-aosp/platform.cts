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
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
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
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

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
    private Button mButtonTestOffload;
    private ProgressBar mProgress;
    private TextView mResultText;

    private Button mButtonPlayOriginal;
    private Button mButtonPlayRecording1;
    private Button mButtonPlayRecording2;
    private TextView mAnalysisResultText;

    private File mRecording1File;
    private File mRecording2File;
    private File mControlRecordingFile;

    private int mResultCode = RESULT_CODE_NOT_RUN;
    private double mCorrelation = 0.0;
    private double mCorrelationOffload = 0.0;
    private boolean mTestNonOffloadPassed = false;
    private boolean mTestOffloadPassed = false;

    private static final int SELECTED_RECORD_SOURCE =
            android.media.MediaRecorder.AudioSource.UNPROCESSED;
    private static final int BLOCK_SIZE_SAMPLES = 4096;

    private final float MIN_RMS_DB = -60.0f; //dB
    private final float MIN_RMS_VAL = (float)Math.pow(10,(MIN_RMS_DB/20));
    private static final float MAX_VAL = (float)(1 << 15);

    private static final int PLAYBACK_WAIT_BUFFER_MS = 2000;
    private static final int CODEC_TIMEOUT_US = 5000;
    private static final int RAW_BUFFER_SIZE = 1024 * 64;
    private static final int RECORD_BUFFER_MULTIPLIER = 2;
    private static final int WAV_HEADER_SIZE = 44;
    private static final int WAV_WRITE_BUFFER_SIZE = 1024 * 4;
    private static final int FILE_COPY_BUFFER_SIZE = 1024;

    private Thread mTestThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.audio_playback_params_activity);

        mButtonTest = (Button) findViewById(R.id.audio_playback_params_button_test);
        mButtonTest.setText("Test Non-Offload");
        mButtonTest.setOnClickListener(this);
        mButtonTestOffload = (Button) findViewById(
                R.id.audio_playback_params_button_test_offload);
        mButtonTestOffload.setOnClickListener(this);
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
        enableTestButtons(true);
        enablePlayButtons(false);

        setPassFailButtonClickListeners();
        getPassButton().setEnabled(false);

        setInfoResources(R.string.audio_playback_params_test,
                R.string.audio_playback_params_info, -1);

        // Show the info dialog so the user knows how to run the test
        View infoButton = findViewById(R.id.info_button);
        if (infoButton != null) {
            infoButton.performClick();
        }
    }

    private void showView(View v, boolean show) {
        v.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.audio_playback_params_button_test) {
            startTest(false);
        } else if (id == R.id.audio_playback_params_button_test_offload) {
            startTest(true);
        } else if (id == R.id.audio_playback_params_button_play_original) {
            playRawAudio(mControlRecordingFile);
        } else if (id == R.id.audio_playback_params_button_play_rec1) {
            playRawAudio(mRecording1File);
        } else if (id == R.id.audio_playback_params_button_play_rec2) {
            playRawAudio(mRecording2File);
        }
    }

    private static class AudioDataSource {
        final int sampleRate;
        final int channelCount;
        final int encoding;
        final byte[] data;

        AudioDataSource(int sampleRate, int channelCount, int encoding, byte[] data) {
            this.sampleRate = sampleRate;
            this.channelCount = channelCount;
            this.encoding = encoding;
            this.data = data;
        }
    }

    private AudioDataSource getAudioData(Object source) throws IOException {
        MediaExtractor extractor = new MediaExtractor();
        if (source instanceof Integer) {
            try (android.content.res.AssetFileDescriptor afd =
                    getResources().openRawResourceFd((Integer) source)) {
                extractor.setDataSource(
                        afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            }
        } else if (source instanceof File) {
            extractor.setDataSource(((File) source).getAbsolutePath());
        } else {
            throw new IllegalArgumentException("Unsupported audio source type");
        }

        MediaFormat format = null;
        String mime = null;
        int trackIndex = -1;
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            format = extractor.getTrackFormat(i);
            mime = format.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith("audio/")) {
                trackIndex = i;
                break;
            }
        }

        if (trackIndex < 0) {
            extractor.release();
            throw new IOException("No audio track found");
        }

        extractor.selectTrack(trackIndex);

        int sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        int channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        int encoding = format.containsKey(MediaFormat.KEY_PCM_ENCODING) ?
                format.getInteger(MediaFormat.KEY_PCM_ENCODING) : AudioFormat.ENCODING_PCM_16BIT;

        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();

        if (mime.startsWith("audio/raw")) {
            ByteBuffer buffer = ByteBuffer.allocate(RAW_BUFFER_SIZE);
            while (true) {
                int size = extractor.readSampleData(buffer, 0);
                if (size < 0) break;
                byte[] bytes = new byte[size];
                buffer.get(bytes, 0, size);
                baos.write(bytes);
                buffer.clear();
                extractor.advance();
            }
        } else {
            MediaCodecList codecList = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
            String decoderName = codecList.findDecoderForFormat(format);
            if (decoderName == null) {
                throw new IOException("No decoder found for " + mime);
            }
            MediaCodec codec = MediaCodec.createByCodecName(decoderName);
            codec.configure(format, null, null, 0);
            codec.start();

            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean inputDone = false;
            boolean outputDone = false;

            while (!outputDone) {
                if (!inputDone) {
                    int inputIndex = codec.dequeueInputBuffer(CODEC_TIMEOUT_US);
                    if (inputIndex >= 0) {
                        ByteBuffer buffer = codec.getInputBuffer(inputIndex);
                        int size = extractor.readSampleData(buffer, 0);
                        if (size < 0) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone = true;
                        } else {
                            codec.queueInputBuffer(inputIndex, 0, size,
                                    extractor.getSampleTime(), 0);
                            extractor.advance();
                        }
                    }
                }

                int outputIndex = codec.dequeueOutputBuffer(info, CODEC_TIMEOUT_US);
                if (outputIndex >= 0) {
                    ByteBuffer buffer = codec.getOutputBuffer(outputIndex);
                    byte[] bytes = new byte[info.size];
                    buffer.get(bytes);
                    baos.write(bytes);
                    codec.releaseOutputBuffer(outputIndex, false);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outputDone = true;
                    }
                } else if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat newFormat = codec.getOutputFormat();
                    sampleRate = newFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    channelCount = newFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    if (newFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                        encoding = newFormat.getInteger(MediaFormat.KEY_PCM_ENCODING);
                    }
                }
            }
            codec.stop();
            codec.release();
        }
        extractor.release();
        return new AudioDataSource(sampleRate, channelCount, encoding, baos.toByteArray());
    }

    private void playAudio(AudioDataSource data, float speed, float pitch, boolean isOffload,
            CountDownLatch releaseLatch) {
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();

        if (data.channelCount != 1 && data.channelCount != 2) {
            throw new IllegalArgumentException("Unsupported channel count: " + data.channelCount);
        }

        int channelMask = (data.channelCount == 1) ? AudioFormat.CHANNEL_OUT_MONO
                : AudioFormat.CHANNEL_OUT_STEREO;
        AudioFormat audioFormat = new AudioFormat.Builder()
                .setSampleRate(data.sampleRate)
                .setChannelMask(channelMask)
                .setEncoding(data.encoding)
                .build();

        if (isOffload) {
            if (!AudioManager.isOffloadedPlaybackSupported(audioFormat, audioAttributes)) {
                throw new UnsupportedOperationException("Offload not supported for this format");
            }
        }

        AudioTrack.Builder builder = new AudioTrack.Builder()
                .setAudioAttributes(audioAttributes)
                .setAudioFormat(audioFormat)
                .setTransferMode(AudioTrack.MODE_STREAM);

        if (isOffload) {
            builder.setOffloadedPlayback(true);
        } else {
            int minBufferSize = AudioTrack.getMinBufferSize(data.sampleRate, channelMask,
                    data.encoding);
            // Ensure the buffer size is large enough (at least ~1 second of audio) to prevent
            // underruns when playback parameters (like speed) are modified during the test.
            builder.setBufferSizeInBytes(Math.max(minBufferSize,
                    data.sampleRate * data.channelCount * 2));
        }

        final AudioTrack audioTrack = builder.build();

        int bytesPerSample = 2;
        if (data.encoding == AudioFormat.ENCODING_PCM_8BIT) {
            bytesPerSample = 1;
        } else if (data.encoding == AudioFormat.ENCODING_PCM_FLOAT) {
            bytesPerSample = 4;
        } else if (data.encoding != AudioFormat.ENCODING_PCM_16BIT) {
            throw new IllegalArgumentException("Unsupported encoding: " + data.encoding);
        }
        int frameSize = data.channelCount * bytesPerSample;
        int totalFrames = data.data.length / frameSize;

        final CountDownLatch latch = new CountDownLatch(1);
        final Executor executor = Executors.newSingleThreadExecutor();
        final AudioTrack.StreamEventCallback callback = new AudioTrack.StreamEventCallback() {
            @Override
            public void onPresentationEnded(AudioTrack track) {
                latch.countDown();
            }
        };

        if (isOffload) {
            audioTrack.registerStreamEventCallback(executor, callback);
        } else {
            audioTrack.setNotificationMarkerPosition(totalFrames);
            audioTrack.setPositionNotificationPeriod(data.sampleRate / 10); // 100ms
            audioTrack.setPlaybackPositionUpdateListener(
                    new AudioTrack.OnPlaybackPositionUpdateListener() {
                @Override
                public void onMarkerReached(AudioTrack track) {
                    latch.countDown();
                }

                @Override
                public void onPeriodicNotification(AudioTrack track) {
                    if (track.getPlaybackHeadPosition() >= totalFrames) {
                        latch.countDown();
                    }
                }
            }, new android.os.Handler(android.os.Looper.getMainLooper()));
        }

        // Only set PlaybackParams if they are non-default.
        // Some offload implementations might behave unexpectedly if setPlaybackParams is called
        // with default values or if variable speed is not fully supported.
        if (speed != 1.0f || pitch != 1.0f) {
            PlaybackParams params = new PlaybackParams();
            params.setSpeed(speed);
            params.setPitch(pitch);
            try {
                audioTrack.setPlaybackParams(params);
            } catch (IllegalArgumentException | IllegalStateException e) {
                Log.e(TAG, "Failed to set playback params", e);
                throw new IllegalArgumentException("Failed to set playback params", e);
            }
        }

        int bytesWritten = 0;
        int bufferSize = Math.max(
                AudioTrack.getMinBufferSize(data.sampleRate, channelMask, data.encoding),
                data.sampleRate * data.channelCount * 2);

        // For offload, prime the buffer before playing.
        if (isOffload) {
            int primeSize = Math.min(bufferSize, data.data.length);
            int written = audioTrack.write(data.data, 0, primeSize);
            if (written < 0) {
                Log.e(TAG, "AudioTrack write error during priming: " + written);
                audioTrack.release();
                return;
            }
            bytesWritten += written;
        }

        audioTrack.play();

        while (bytesWritten < data.data.length) {
            int toWrite = Math.min(bufferSize, data.data.length - bytesWritten);
            int written = audioTrack.write(data.data, bytesWritten, toWrite);
            if (written < 0) {
                Log.e(TAG, "AudioTrack write error: " + written);
                break;
            }
            bytesWritten += written;
        }

        if (isOffload) {
            audioTrack.setOffloadEndOfStream();
        } else {
            // Update marker if we didn't write everything
            int actualFrames = bytesWritten / frameSize;
            if (actualFrames < totalFrames) {
                audioTrack.setNotificationMarkerPosition(actualFrames);
            }
        }

        if (bytesWritten > 0) {
            try {
                int actualFrames = bytesWritten / frameSize;
                long durationMs = (long) ((actualFrames / (double) data.sampleRate) /
                        speed * 1000);
                long timeoutMs = durationMs + PLAYBACK_WAIT_BUFFER_MS;
                if (!latch.await(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                    Log.w(TAG, "Timeout waiting for playback to complete. Head: "
                            + audioTrack.getPlaybackHeadPosition() + " Target: " + actualFrames);
                }
            } catch (InterruptedException e) {
                Log.e(TAG, "Interrupted while waiting for playback", e);
                Thread.currentThread().interrupt();
            }
        }

        if (releaseLatch != null) {
            try {
                // Wait for the recording thread to capture the tail acoustic delay
                releaseLatch.await(3000, java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Log.e(TAG, "Interrupted while waiting for recording to finish", e);
                Thread.currentThread().interrupt();
            }
        }

        audioTrack.stop();
        if (isOffload) {
            audioTrack.unregisterStreamEventCallback(callback);
        }
        audioTrack.release();
    }

    private void startTest(boolean isOffload) {
        if (mTestThread != null && mTestThread.isAlive()) {
            Log.v(TAG,"test Thread already running.");
            return;
        }

        mTestThread = new Thread(new AudioTestRunner(TAG, TEST_PLAYBACK_PARAMS, mMessageHandler) {
            public void run() {
                super.run();
                mResultCode = RESULT_CODE_NOT_RUN;
                mControlRecordingFile = null;
                mRecording1File = null;
                mRecording2File = null;
                enableTestButtons(false);
                enablePlayButtons(false);

                if (isOffload) {
                    try {
                        AudioDataSource audioData = getAudioData(R.raw.speech);
                        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build();
                        int channelMask = (audioData.channelCount == 1)
                                ? AudioFormat.CHANNEL_OUT_MONO
                                : AudioFormat.CHANNEL_OUT_STEREO;
                        AudioFormat audioFormat = new AudioFormat.Builder()
                                .setSampleRate(audioData.sampleRate)
                                .setChannelMask(channelMask)
                                .setEncoding(audioData.encoding)
                                .build();
                        if (!AudioManager.isOffloadedPlaybackSupported(
                                audioFormat, audioAttributes)) {
                            mResultCode = RESULT_CODE_OK;
                            mTestOffloadPassed = true;
                            sendMessage(AudioTestRunner.TEST_ENDED_OK,
                                    "Test passed because PCM offload is not supported.");
                            return;
                        }
                    } catch (IOException e) {
                        sendMessage(AudioTestRunner.TEST_ENDED_ERROR,
                                "Failed to read audio data");
                        return;
                    }
                }

                // Step 1: Play speech at 1.0x speed and record it as a control.
                sendMessage(AudioTestRunner.TEST_MESSAGE,
                        "Playing at 1.0x speed and recording (control)"
                        + (isOffload ? " [Offload]" : "") + "...");
                mControlRecordingFile = recordPlayback(1.0f, 1.0f, R.raw.speech, isOffload);
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
                mRecording1File = recordPlayback(2.0f, 1.0f, R.raw.speech, isOffload);
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
                mRecording2File = recordPlayback(0.5f, 1.0f, mRecording1File, isOffload);
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
                        byte[] buffer = new byte[FILE_COPY_BUFFER_SIZE];
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
                analyzeRecordings(isOffload);

                double correlation = isOffload ? mCorrelationOffload : mCorrelation;

                if (correlation >= CORRELATION_PASS_THRESHOLD) {
                    mResultCode = RESULT_CODE_OK;
                    if (isOffload) {
                        mTestOffloadPassed = true;
                    } else {
                        mTestNonOffloadPassed = true;
                    }
                    sendMessage(AudioTestRunner.TEST_ENDED_OK, "Test passed.");
                } else {
                    mResultCode = RESULT_CODE_FAILED;
                    if (isOffload) {
                        mTestOffloadPassed = false;
                    } else {
                        mTestNonOffloadPassed = false;
                    }
                    sendMessage(AudioTestRunner.TEST_ENDED_ERROR,
                            "Test failed. Correlation too low.");
                }
            }
        });
        mTestThread.start();
    }

    private File recordPlayback(float speed, float pitch, Object source, boolean isOffload) {
        File rawFile = null;
        File wavFile = null;
        int sampleRate;
        int channelCount;
        int channelConfig;
        int encoding;

        try {
            AudioDataSource audioData = getAudioData(source);
            sampleRate = audioData.sampleRate;
            channelCount = audioData.channelCount;
            encoding = audioData.encoding;
            int bitsPerSample = (encoding == AudioFormat.ENCODING_PCM_8BIT) ? 8 : 16;

            switch (channelCount) {
                case 1 -> channelConfig = AudioFormat.CHANNEL_IN_MONO;
                case 2 -> channelConfig = AudioFormat.CHANNEL_IN_STEREO;
                default -> {
                    Log.e(TAG, "Unsupported channel count: " + channelCount);
                    return null;
                }
            }

            Log.e(TAG, String.format("Audio source format: Sample Rate: %d, Channels: %d, "
                    + "Encoding: %d", sampleRate, channelCount, encoding));

            rawFile = File.createTempFile("playback_record", ".raw", getCacheDir());
            FileOutputStream fos = new FileOutputStream(rawFile);

            int minRecordBuffSizeInBytes = AudioRecord.getMinBufferSize(sampleRate,
                    channelConfig, encoding);
            final AudioRecord recorder = new AudioRecord(SELECTED_RECORD_SOURCE, sampleRate,
                    channelConfig, encoding,
                    RECORD_BUFFER_MULTIPLIER * minRecordBuffSizeInBytes);

            int bytesPerSamplePlayback = 2;
            if (audioData.encoding == AudioFormat.ENCODING_PCM_8BIT) {
                bytesPerSamplePlayback = 1;
            } else if (audioData.encoding == AudioFormat.ENCODING_PCM_FLOAT) {
                bytesPerSamplePlayback = 4;
            }
            int frameSizePlayback = audioData.channelCount * bytesPerSamplePlayback;
            int actualFramesPlayback = audioData.data.length / frameSizePlayback;
            double playbackDurationSec = (actualFramesPlayback / (double) audioData.sampleRate)
                    / speed;

            // Add 1 second to capture the acoustic and buffering delay
            double recordDurationSec = playbackDurationSec + 1.0;
            int bytesPerSampleRecorder = (encoding == AudioFormat.ENCODING_PCM_8BIT) ? 1 : 2;
            int frameSizeRecorder = channelCount * bytesPerSampleRecorder;
            long maxRecordBytes = (long) (recordDurationSec * sampleRate * frameSizeRecorder);

            CountDownLatch releaseLatch = new CountDownLatch(1);

            final Thread recordingThread = new Thread(() -> {
                recorder.startRecording();
                byte[] buffer = new byte[minRecordBuffSizeInBytes];
                long totalBytesRead = 0;
                while (!Thread.currentThread().isInterrupted()
                        && totalBytesRead < maxRecordBytes) {
                    int bytesRead = recorder.read(buffer, 0, buffer.length);
                    if (bytesRead > 0) {
                        try {
                            fos.write(buffer, 0, bytesRead);
                            totalBytesRead += bytesRead;
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
                releaseLatch.countDown();
            });

            recordingThread.start();
            try {
                playAudio(audioData, speed, pitch, isOffload, releaseLatch);
            } catch (UnsupportedOperationException e) {
                Log.e(TAG, "Offload not supported", e);
                releaseLatch.countDown(); // unblock if error
            }

            // Wait for the recording thread to capture the required frames.
            // It will naturally exit when totalBytesRead >= maxRecordBytes.
            // Add a timeout of 3 seconds over the expected duration as a fallback.
            recordingThread.join(3000);
            if (recordingThread.isAlive()) {
                recordingThread.interrupt();
                recordingThread.join();
            }

            Log.d(TAG, "Raw recording size: " + rawFile.length());

            wavFile = File.createTempFile("playback_record", ".wav", getCacheDir());
            try (FileInputStream rawIs = new FileInputStream(rawFile);
                 FileOutputStream wavOs = new FileOutputStream(wavFile)) {
                writeWavHeader(wavOs, (int) rawFile.length(), sampleRate, channelCount,
                        bitsPerSample);
                byte[] buffer = new byte[WAV_WRITE_BUFFER_SIZE];
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

    private void enableTestButtons(boolean enabled) {
        runOnUiThread(() -> {
            if (mButtonTest != null) mButtonTest.setEnabled(enabled);
            if (mButtonTestOffload != null) mButtonTestOffload.setEnabled(enabled);
        });
    }

    private void enablePlayButtons(boolean enabled) {
        runOnUiThread(() -> {
            if (mButtonPlayOriginal != null) {
                mButtonPlayOriginal.setEnabled(enabled && mControlRecordingFile != null);
            }
            if (mButtonPlayRecording1 != null) {
                mButtonPlayRecording1.setEnabled(enabled && mRecording1File != null);
            }
            if (mButtonPlayRecording2 != null) {
                mButtonPlayRecording2.setEnabled(enabled && mRecording2File != null);
            }
        });
    }

    private void playRawAudio(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        new Thread(() -> {
            try {
                AudioDataSource data = getAudioData(file);
                playAudio(data, 1.0f, 1.0f, false /* isOffload */, null);
            } catch (IOException e) {
                Log.e(TAG, "Error playing raw file", e);
            }  catch (UnsupportedOperationException e) {
                Log.e(TAG, "Offload not supported", e);
            }
        }).start();
    }

    private static void writeWavHeader(FileOutputStream out, int pcmDataSize, int sampleRate,
            int channels, int bitsPerSample)
            throws IOException {
        long byteRate = (long) sampleRate * channels * (bitsPerSample / 8);
        int blockAlign = channels * (bitsPerSample / 8);

        long riffChunkSize = (long) pcmDataSize + WAV_HEADER_SIZE - 8;

        byte[] header = new byte[WAV_HEADER_SIZE];

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

    private void analyzeRecordings(boolean isOffload) {
        try {
            // Read original and final recordings as shorts
            AudioDataSource originalData = getAudioData(mControlRecordingFile);
            short[] originalShorts = byteToShort(originalData.data);
            AudioDataSource finalData = getAudioData(mRecording2File);
            short[] finalShorts = byteToShort(finalData.data);

            Log.d(TAG, "Original audio length (samples): " + originalShorts.length);
            Log.d(TAG, "Final audio length (samples): " + finalShorts.length);

            // Calculate RMS snapshots for both
            int originalShotCount = (int)Math.ceil(
                    (double)originalShorts.length / BLOCK_SIZE_SAMPLES);
            RmsHelper originalRms = new RmsHelper(BLOCK_SIZE_SAMPLES, originalShotCount);
            originalRms.addSamples(originalShorts);
            Log.v(TAG, "Original RMS Snapshots: " + Arrays.toString(
                    originalRms.getRmsSnapshots().mData));

            int finalShotCount = (int)Math.ceil((double)finalShorts.length / BLOCK_SIZE_SAMPLES);
            RmsHelper finalRms = new RmsHelper(BLOCK_SIZE_SAMPLES, finalShotCount);
            finalRms.addSamples(finalShorts);
            Log.v(TAG, "Final RMS Snapshots: "
            + Arrays.toString(finalRms.getRmsSnapshots().mData));

            // Correlate the RMS snapshots
            int shotCount = Math.min(originalShotCount, finalShotCount);
            // Correlate the middle 80% of the recording to avoid startup/shutdown artifacts.
            int firstShot = shotCount / 10;
            int lastShot = shotCount * 9 / 10;
            double correlation = computeAcousticCouplingFactor(originalRms.getRmsSnapshots(),
                    finalRms.getRmsSnapshots(), firstShot, lastShot);

            if (isOffload) {
                mCorrelationOffload = correlation;
            } else {
                mCorrelation = correlation;
            }

            runOnUiThread(() -> {
                mAnalysisResultText.setText(String.format("Correlation: %.4f", correlation));
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

            double maxRecorderRms = 0.0;
            for (int i = firstShot, index = 0; i <= lastShot; ++i, ++index) {
                if (buffRmsRecorder.mData[i] > maxRecorderRms) {
                    maxRecorderRms = buffRmsRecorder.mData[i];
                }
                double valPlayerdB
                         = Math.max(20 * Math.log10(buffRmsPlayer.mData[i]), MIN_RMS_DB);
                rmsPlayerdB.setValue(index, valPlayerdB);
                double valRecorderdB = Math.max(20 * Math.log10(buffRmsRecorder.mData[i]),
                        MIN_RMS_DB);
                rmsRecorderdB.setValue(index, valRecorderdB);
                Log.v(TAG, String.format("RMS dB values at index %d - Player: %.4f," +
                        " Recorder: %.4f", i, valPlayerdB, valRecorderdB));
            }

            if (maxRecorderRms <= MIN_RMS_VAL * 2.0) {
                Log.w(TAG, "Recorded volume is too low or zero. Failing correlation.");
                runOnUiThread(() -> {
                    mAnalysisResultText.setText("Recorded volume is too low or zero.\n" +
                            "Make sure the device volume is not muted.");
                });
                return 0.0;
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
    private static final String KEY_CORRELATION_OFFLOAD = "correlation_offload";

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
        reportLog.addValue(
                KEY_CORRELATION_OFFLOAD,
                mCorrelationOffload,
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
            if (mResultCode == RESULT_CODE_OK && mTestNonOffloadPassed && mTestOffloadPassed) {
                getPassButton().setEnabled(true);
            }
            enableTestButtons(true);
            enablePlayButtons(true);
        }

        @Override
        public void testEndedError(int testId, String str) {
            super.testEndedError(testId, str);
            Log.v(TAG, "Test EndedError. " + testId + " str:"+str);
            showView(mProgress, false);
            mResultText.setText("test failed. " + str);
            enableTestButtons(true);
            enablePlayButtons(true);
        }
    };
}
