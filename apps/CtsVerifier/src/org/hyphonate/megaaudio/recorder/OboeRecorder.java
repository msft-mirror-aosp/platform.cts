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

import android.util.Log;

import org.hyphonate.megaaudio.common.BuilderBase;

public class OboeRecorder extends Recorder {
    @SuppressWarnings("unused")
    private static final String TAG = OboeRecorder.class.getSimpleName();
    @SuppressWarnings("unused")
    private static final boolean LOG = true;

    private int mRecorderSubtype;
    private long mNativeRecorder;
    JavaNativeFloatFifo mUpFifo;

    private class OboeRecorderRunnable extends RecorderRunnable {

        @Override
        public int read(float[] buffer, int offsetInSamples, int numSamples) {
            return mUpFifo.readBlocking(buffer, offsetInSamples, numSamples);
        }

        @Override
        public void onStop() {
            stopStreamN(mNativeRecorder);
        }
    }

    public OboeRecorder(AudioSinkProvider sinkProvider, int subType) {
        super(sinkProvider);
        if (LOG) {
            Log.d(TAG, "OboeRecorder()");
        }
        // Allocate a very large FIFO that can hold more than we need
        // to prevent overflows from the native stream.
        //
        // This can be bigger than it needs to be and it will not affect latency
        // because input FIFOs are kept near empty.
        // Allocate enough for 100 msec of data in a stereo buffer at 48000 Hz.
        // TODO b/418840924 use channelCount and sampleRate in build() method.
        int capacityInFloats = 2 * 48000 / 10;
        mUpFifo = new JavaNativeFloatFifo(JavaNativeFloatFifo.FROM_NATIVE, capacityInFloats);
        if (LOG) {
            Log.d(TAG, "OboeRecorder() JavaNativeFloatFifo capacity = " + capacityInFloats);
        }
        mRecorderSubtype = subType;
        mNativeRecorder = allocNativeRecorder(mUpFifo.getNativeToken(), mRecorderSubtype);
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
        buildCommon();

        return trackBuild(
                buildStreamN(
                        mNativeRecorder,
                        mChannelCount,
                        mSampleRate,
                        mPerformanceMode,
                        mSharingMode,
                        builder.getRouteDeviceId(),
                        mInputPreset));
    }

    @Override
    public int open() {
        if (LOG) {
            Log.d(TAG, "open()");
        }
        return trackOpen(openStreamN(mNativeRecorder));
    }

    @Override
    public int start() {
        if (LOG) {
            Log.d(TAG, "start()");
        }

        startSink();

        int retVal = startStreamN(mNativeRecorder, mRecorderSubtype);
        if (retVal == 0) {
            startRecordingThread(new OboeRecorderRunnable(), "OboeRecorder Thread");
        }
        return trackStart(retVal);
    }

    @Override
    public int stop() {
        if (LOG) {
            Log.d(TAG, "stop()");
        }
        mRecording = false;
        return trackStop(stopStreamN(mNativeRecorder));
    }

    @Override
    public int close() {
        if (LOG) {
            Log.d(TAG, "close()");
        }
        return trackClose(closeStreamN(mNativeRecorder));
    }

    /**
     * Stop and close the streams. Also delete any native resources that were allocated.
     *
     * <p>This should be called when the object is no longer needed. This object cannot be used
     * after calling this method.
     *
     * @return error code
     */
    @Override
    public int teardown() {
        if (LOG) {
            Log.d(TAG, "teardown()");
        }
        waitForStreamThreadToExit();

        int errCode = teardownStreamN(mNativeRecorder);
        mChannelCount = 0;
        mSampleRate = 0;
        // The recorder uses the FIFO so free the recorder first.
        if (mNativeRecorder != 0) {
            deleteNativeRecorder(mNativeRecorder);
            mNativeRecorder = 0;
        }
        if (mUpFifo != null) {
            mUpFifo.release(); // release native FIFO
            mUpFifo = null;
        }
        return trackTeardown(errCode);
    }

    //
    // Attributes
    //
    public int getNumBufferFrames() {
        return getNumBufferFramesN(mNativeRecorder);
    }

    @Override
    public int getRoutedDeviceId() {
        return getRoutedDeviceIdN(mNativeRecorder);
    }

    @Override
    public int getSharingMode() {
        return getSharingModeN(mNativeRecorder);
    }

    @Override
    public int getChannelCount() {
        return getChannelCountN(mNativeRecorder);
    }

    @Override
    public boolean isMMap() {
        return isMMapN(mNativeRecorder);
    }

    /**
     * @return See StreamState constants
     */
    public int getStreamState() {
        return getStreamStateN(mNativeRecorder);
    }

    /**
     * @return The last error callback result (these must match Oboe). See Oboe constants
     */
    public int getLastErrorCallbackResult() {
        return getLastErrorCallbackResultN(mNativeRecorder);
    }

    private native long allocNativeRecorder(long nativeFifoPtr, int recorderSubtype);

    private native long deleteNativeRecorder(long nativeRecorder);

    private native int getBufferFrameCountN(long nativeRecorder);
    private native void setInputPresetN(long nativeRecorder, int inputPreset);

    private native int getRoutedDeviceIdN(long nativeRecorder);

    private native int getSharingModeN(long nativeRecorder);

    private native int getChannelCountN(long nativeRecorder);

    private native boolean isMMapN(long nativeRecorder);

    private native int buildStreamN(long nativeRecorder, int channelCount, int sampleRate,
                                    int performanceMode, int sharingMode, int routeDeviceId,
                                    int inputPreset);

    private native int openStreamN(long nativeRecorder);

    private native int startStreamN(long nativeRecorder, int recorderSubtype);

    private native int stopStreamN(long nativeRecorder);

    private native int closeStreamN(long nativeRecorder);

    private native int teardownStreamN(long nativeRecorder);

    private native int getStreamStateN(long nativeRecorder);
    private native int getLastErrorCallbackResultN(long nativeRecorder);

    private native int getNumBufferFramesN(long nativeRecorder);
    private native int calcMinBufferFramesN(long nativeRecorder);
}
