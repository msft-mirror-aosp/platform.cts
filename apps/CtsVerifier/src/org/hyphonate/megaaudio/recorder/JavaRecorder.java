/*
 * Copyright 2020 The Android Open Source Project
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
package org.hyphonate.megaaudio.recorder;

import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.util.Log;

import org.hyphonate.megaaudio.common.BuilderBase;
import org.hyphonate.megaaudio.common.StreamBase;
import org.hyphonate.megaaudio.common.StreamState;

/**
 * Implementation of abstract Recorder class implemented for the Android Java-based audio record
 * API, i.e. AudioRecord.
 */
public class JavaRecorder extends Recorder {
    @SuppressWarnings("unused")
    private static final String TAG = JavaRecorder.class.getSimpleName();
    @SuppressWarnings("unused")
    private static final boolean LOG = true;

    /* The AudioRecord for recording the audio stream */
    private AudioRecord mAudioRecord = null;

    @Override
    public int getRoutedDeviceId() {
        if (mAudioRecord != null) {
            AudioDeviceInfo routedDevice = mAudioRecord.getRoutedDevice();
            return routedDevice != null
                    ? routedDevice.getId() : BuilderBase.ROUTED_DEVICE_ID_DEFAULT;
        } else {
            return BuilderBase.ROUTED_DEVICE_ID_DEFAULT;
        }
    }

    public JavaRecorder(AudioSinkProvider sinkProvider) {
        super(sinkProvider);
    }

    //
    // Lifecycle
    //
    @Override
    public int build(BuilderBase builder) {
        mChannelCount = builder.getChannelCount();
        mSampleRate = builder.getSampleRate();
        mNumExchangeFrames = builder.getNumExchangeFrames();
        mSharingMode = builder.getSharingMode();
        mPerformanceMode = builder.getPerformanceMode();
        mInputPreset = ((RecorderBuilder) builder).getInputPreset();

        if (LOG) {
            Log.i(TAG, "build()");
            Log.i(TAG, "  chans:" + mChannelCount);
            Log.i(TAG, "  rate: " + mSampleRate);
            Log.i(TAG, "  frames: " + mNumExchangeFrames);
            Log.i(TAG, "  perf mode: " + mPerformanceMode);
            Log.i(TAG, "  route device: " + builder.getRouteDeviceId());
            Log.i(TAG, "  preset: " + mInputPreset);
        }

        try {
            int bufferSizeInBytes = mNumExchangeFrames * mChannelCount
                    * sampleSizeInBytes(AudioFormat.ENCODING_PCM_FLOAT);
            Log.i(TAG, "  bufferSizeInBytes:" + bufferSizeInBytes);
            Log.i(TAG, "  (in frames)" + (bufferSizeInBytes / 4 / mChannelCount));

            AudioFormat.Builder formatBuilder = new AudioFormat.Builder();
            formatBuilder.setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(mSampleRate)
                    .setChannelIndexMask(StreamBase.channelCountToIndexMask(mChannelCount));

            AudioRecord.Builder recordBuilder = new AudioRecord.Builder();
            recordBuilder.setAudioFormat(formatBuilder.build())
                    .setBufferSizeInBytes(bufferSizeInBytes);
            if (mInputPreset != Recorder.INPUT_PRESET_NONE) {
                recordBuilder.setAudioSource(mInputPreset);
            }
            mAudioRecord = recordBuilder.build();
            mNumExchangeFrames = mAudioRecord.getBufferSizeInFrames();
            if (LOG) {
                Log.i(TAG, "  mAudioRecord.getBufferSizeInFrames(): "
                        + mAudioRecord.getBufferSizeInFrames());
            }
            mAudioRecord.setPreferredDevice(builder.getRouteDevice());

            buildCommon();
        } catch (UnsupportedOperationException ex) {
            if (LOG) {
                Log.e(TAG, "Couldn't open AudioRecord: " + ex);
            }
            return ERROR_UNSUPPORTED;
        } catch (java.lang.IllegalArgumentException ex) {
            if (LOG) {
                Log.e(TAG, "Invalid arguments to AudioRecord.Builder: " + ex);
            }
            return ERROR_INVALID_ARGUMENT;
        }
        return trackBuild(OK);
    }

    @Override
    public int open() {
        if (LOG) {
            Log.d(TAG, "open()");
        }
        return trackOpen(OK);
    }

    @Override
    public int start() {
        if (LOG) {
            Log.d(TAG, "start()");
        }
        if (mAudioRecord == null) {
            if (LOG) {
                Log.i(TAG, " - ERROR_INVALID_STATE");
            }
            return ERROR_INVALID_STATE;
        }

        startSink();

        try {
            mAudioRecord.startRecording();
        } catch (IllegalStateException ex) {
            Log.e(TAG, "startRecording exception: " + ex);
        }

        startRecordingThread(new JavaRecorderRunnable(), "JavaRecorder Thread");

        return trackStart(OK);
    }

    @Override
    public int stop() {
        if (LOG) {
            Log.d(TAG, "stop()");
        }
        mRecording = false;
        return trackStop(OK);
    }

    @Override
    public int close() {
        if (LOG) {
            Log.d(TAG, "close()");
        }
        return trackClose(OK);
    }

    @Override
    public int teardown() {
        if (LOG) {
            Log.i(TAG, "teardown()");
        }
        stop();

        waitForStreamThreadToExit();

        if (mAudioRecord != null) {
            mAudioRecord.release();
            mAudioRecord = null;
        }

        mChannelCount = 0;
        mSampleRate = 0;

        //TODO Retrieve errors from above
        return trackTeardown(OK);
    }

    //
    // Attributes
    //
    @Override
    public int getSharingMode() {
        // JAVA Audio API does not support a sharing mode
        return BuilderBase.SHARING_MODE_NOTSUPPORTED;
    }

    @Override
    public int getChannelCount() {
        return mAudioRecord != null ?  mAudioRecord.getChannelCount() : -1;
    }

    @Override
    public boolean isMMap() {
        // Java Streams are never MMAP
        return false;
    }

    // JavaRecorder-specific extension
    public AudioRecord getAudioRecord() {
        return mAudioRecord;
    }

    /**
     * @return See StreamState constants
     */
    public int getStreamState() {
        //TODO - track state so we can return something meaningful here.
        return StreamState.UNKNOWN;
    }

    /**
     * @return The last error callback result (these must match Oboe). See Oboe constants
     */
    public int getLastErrorCallbackResult() {
        //TODO - track errors so we can return something meaningful here.
        return ERROR_UNKNOWN;
    }

    private class JavaRecorderRunnable extends RecorderRunnable {

        @Override
        public int read(float[] buffer, int offsetInFloats, int numSamples) {
            return mAudioRecord.read(buffer, offsetInFloats, numSamples, AudioRecord.READ_BLOCKING);
        }

        @Override
        public void onStop() {
            mAudioRecord.stop();
        }
    }

}
