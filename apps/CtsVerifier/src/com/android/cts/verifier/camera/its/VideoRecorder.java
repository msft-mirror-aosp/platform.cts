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

package com.android.cts.verifier.camera.its;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaRecorder;
import android.os.ConditionVariable;
import android.os.Handler;
import android.util.Size;
import android.view.Surface;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Base class to record a video stream for ITS tests.
 */
class VideoRecorder implements AutoCloseable {
    private static final String TAG = VideoRecorder.class.getSimpleName();

    protected boolean mRecordingStarted = false; // tracks if the MediaRecorder/MediaCodec instance
                                               // was already used to record a video.

    // Lock to protect reads/writes to the various Surfaces below.
    protected final Object mRecordLock = new Object();
    // Tracks if the mMediaRecorder/mMediaCodec is currently recording. Protected by mRecordLock.
    protected volatile boolean mIsRecording = false;

    protected final Size mRecordingSize;
    protected final int mMaxFps;
    protected final Handler mHandler;

    protected Surface mRecordSurface; // MediaRecorder/MediaCodec source.

    protected MediaRecorder mMediaRecorder;

    protected MediaCodec mMediaCodec;
    protected MediaMuxer mMediaMuxer;
    protected Object mMediaCodecCondition;

    VideoRecorder(int cameraId, Size recordingSize, int maxFps,
            String outputFile, Handler handler, boolean hlg10Enabled,
            Context context) throws ItsException {
        // Ensure that we can record the given size
        int maxSupportedResolution = ItsUtils.RESOLUTION_TO_CAMCORDER_PROFILE
                                        .stream()
                                        .map(p -> p.first)
                                        .max(Integer::compareTo)
                                        .orElse(0);
        int currentResolution = recordingSize.getHeight() * recordingSize.getWidth();
        if (currentResolution > maxSupportedResolution) {
            throw new ItsException("Requested recording size is greater than maximum "
                    + "supported size.");
        }

        mHandler = handler;
        mRecordingSize = recordingSize;
        mMaxFps = maxFps;

        if (hlg10Enabled) {
            Logt.i(TAG, "HLG10 Enabled, using MediaCodec");
            setupMediaCodec(cameraId, outputFile, context);
        } else {
            Logt.i(TAG, "HLG10 Disabled, using MediaRecorder");
            setupMediaRecorder(cameraId, outputFile, context);
        }
    }

    protected void setupMediaRecorder(int cameraId, String outputFile, Context context)
            throws ItsException {
        mRecordSurface = MediaCodec.createPersistentInputSurface();

        mMediaRecorder = new MediaRecorder(context);
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.DEFAULT);

        mMediaRecorder.setVideoSize(mRecordingSize.getWidth(), mRecordingSize.getHeight());
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.DEFAULT);
        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT);
        mMediaRecorder.setVideoEncodingBitRate(
                ItsUtils.calculateBitrate(cameraId, mRecordingSize, mMaxFps));
        mMediaRecorder.setInputSurface(mRecordSurface);
        mMediaRecorder.setVideoFrameRate(mMaxFps);
        mMediaRecorder.setOutputFile(outputFile);

        try {
            mMediaRecorder.prepare();
        } catch (IOException e) {
            throw new ItsException("Error preparing MediaRecorder", e);
        }
    }

    protected void setupMediaCodec(int cameraId, String outputFilePath, Context context)
            throws ItsException {
        MediaCodecList list = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
        int videoBitRate = ItsUtils.calculateBitrate(cameraId, mRecordingSize, mMaxFps);
        MediaFormat format = ItsUtils.initializeHLG10Format(mRecordingSize, videoBitRate, mMaxFps);
        String codecName = list.findEncoderForFormat(format);
        assert (codecName != null);

        try {
            mMediaMuxer = new MediaMuxer(outputFilePath,
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (IOException e) {
            throw new ItsException("Error preparing the MediaMuxer.", e);
        }

        try {
            mMediaCodec = MediaCodec.createByCodecName(codecName);
        } catch (IOException e) {
            throw new ItsException("Error preparing the MediaCodec.", e);
        }

        mMediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mMediaCodecCondition = new Object();
        mMediaCodec.setCallback(
                new ItsUtils.MediaCodecListener(mMediaMuxer, mMediaCodecCondition), mHandler);

        mRecordSurface = mMediaCodec.createInputSurface();
        assert (mRecordSurface != null);
    }

    public Surface getCameraSurface() {
        return mRecordSurface;
    }

    /**
     * Starts recording frames. This method should only be called once.
     */
    void startRecording() throws ItsException {
        synchronized (mRecordLock) {
            if (mRecordingStarted) {
                throw new ItsException("Attempting to record on a stale VideoRecorder. "
                        + "Create a new instance instead.");
            }
            mRecordingStarted = true;
            mIsRecording = true;
            if (mMediaRecorder != null) {
                mMediaRecorder.start();
            } else {
                mMediaCodec.start();
            }
        }
    }

    /**
     * Stops recording frames.
     */
    void stopRecording() throws ItsException {
        synchronized (mRecordLock) {
            stopRecordingLocked();
        }
    }

    protected void stopRecordingLocked() throws ItsException {
        mIsRecording = false;
        if (mMediaRecorder != null) {
            mMediaRecorder.stop();
        } else if (mMediaCodec != null) {
            mMediaCodec.signalEndOfInputStream();

            synchronized (mMediaCodecCondition) {
                try {
                    mMediaCodecCondition.wait(ItsUtils.SESSION_CLOSE_TIMEOUT_MS);
                } catch (InterruptedException e) {
                    throw new ItsException("Unexpected InterruptedException: ", e);
                }
            }

            mMediaMuxer.stop();
            mMediaCodec.stop();
        }
    }

    @Override
    public void close() throws ItsException {
        synchronized (mRecordLock) {
            if (mIsRecording) {
                stopRecordingLocked();
            }
            if (mMediaRecorder != null) {
                mMediaRecorder.release();
            }
            if (mMediaCodec != null) {
                mMediaCodec.release();
            }
            if (mMediaMuxer != null) {
                mMediaMuxer.release();
            }
            if (mRecordSurface != null) {
                mRecordSurface.release();
            }
        }
    }

    public void getFrame(OutputStream outputStream) throws ItsException {
        throw new ItsException("getFrame() is not supported when using direct recording.");
    }

    public void overrideCameraFrames(boolean recordGreenFrames) throws ItsException {
        throw new ItsException(
                "overrideCameraFrames() is not supported when using direct video recording.");
    }

    public List<Long> getFrameTimeStamps() throws ItsException {
        throw new ItsException(
                "getFrameTimestamps() is not supported when using direct video recording.");
    }
}
