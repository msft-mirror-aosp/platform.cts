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
package android.videoencoding.transcoders;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.mediav2.common.cts.EncoderConfigParams;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;

import androidx.media3.common.Effect;
import androidx.media3.common.MediaItem;
import androidx.media3.transformer.Composition;
import androidx.media3.transformer.DefaultEncoderFactory;
import androidx.media3.transformer.EditedMediaItem;
import androidx.media3.transformer.Effects;
import androidx.media3.transformer.ExportException;
import androidx.media3.transformer.ExportResult;
import androidx.media3.transformer.Transformer;
import androidx.media3.transformer.VideoEncoderSettings;

import com.google.common.collect.ImmutableList;

import java.io.IOException;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

/**
 * Video Transcoder that leverages the media3 Transformer library to perform transcoding based on
 * the constructor-provided parameters.
 */
public final class TransformerTranscoder implements Transformer.Listener {
    private final Context mContext;
    private final String mInputFilePath;
    private final String mOutputFilePath;
    private final String mOutputMime;
    private final VideoEncoderSettings mVideoEncoderSettings;
    private final CyclicBarrier mTranscodeBarrier;
    private final ImmutableList<Effect> mEffects;

    public TransformerTranscoder(
            Context context,
            String inputFilePath,
            String outputFilePath,
            String outputMime,
            VideoEncoderSettings videoEncoderSettings,
            ImmutableList<Effect> effects) {
        mContext = context;
        mInputFilePath = inputFilePath;
        mOutputFilePath = outputFilePath;
        mOutputMime = outputMime;
        mVideoEncoderSettings = videoEncoderSettings;
        mTranscodeBarrier = new CyclicBarrier(2);
        mEffects = effects;
    }

    /**
     * Converts the CTS used {@link EncoderConfigParams} to the Transformer equivalent for use in
     * the transcoding process.
     */
    public static VideoEncoderSettings convertEncoderConfigParamsToSettings(
            EncoderConfigParams params) {
        VideoEncoderSettings.Builder settingsBuilder = new VideoEncoderSettings.Builder();

        settingsBuilder.setBitrate(params.mBitRate);
        settingsBuilder.setiFrameIntervalSeconds(params.mKeyFrameInterval);
        settingsBuilder.setMaxBFrames(params.mMaxBFrames);

        // Disable Transformer's automatic performance tuning which generally improves throughput at
        // the expense of quality.
        settingsBuilder.setEncoderPerformanceParameters(VideoEncoderSettings.RATE_UNSET, VideoEncoderSettings.RATE_UNSET);
        settingsBuilder.setEncodingProfileLevel(params.mProfile, params.mLevel);

        return settingsBuilder.build();
    }

    /**
     * Performs a transcode based on the constructor-provided input parameters. While this
     * <i>can</i> be called multiple times, there is no reason to do so as the result should be the
     * same.
     *
     * @throws IOException If the transcode fails.
     * @return The statistics of the transcode operation.
     */
    public TranscodeStats transcode() throws IOException {
        HandlerThread handlerThread = new HandlerThread("TransformerTranscoder");
        handlerThread.start();

        Handler handler = new Handler(handlerThread.getLooper());

        Transformer transformer =
                new Transformer.Builder(mContext)
                        .setVideoMimeType(mOutputMime)
                        .setLooper(handlerThread.getLooper())
                        .setEncoderFactory(
                                new DefaultEncoderFactory.Builder(mContext)
                                        .setRequestedVideoEncoderSettings(mVideoEncoderSettings)
                                        .build())
                        .addListener(this)
                        .build();

        EditedMediaItem item =
                new EditedMediaItem.Builder(MediaItem.fromUri(mInputFilePath))
                        .setRemoveAudio(true)
                        .setEffects(
                                new Effects(/* audioProcessors= */ ImmutableList.of(), mEffects))
                        .build();

        long startTime = SystemClock.elapsedRealtimeNanos();
        handler.post(() -> transformer.start(item, mOutputFilePath));

        // Sleep the test thread until Transformer finishes.
        awaitTranscodeBarrier();
        long endTime = SystemClock.elapsedRealtimeNanos();

        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource(mOutputFilePath);
        String frameCountStr =
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT);
        long frameCount = Long.parseLong(frameCountStr);
        String durationStr =
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
        long durationMs = Long.parseLong(durationStr);
        retriever.release();

        double durationSec = (endTime - startTime) / 1_000_000_000.0;
        double encodeFps = frameCount / (durationMs / 1000.0);
        return new TranscodeStats(frameCount, durationSec, encodeFps);
    }

    private void awaitTranscodeBarrier() {
        try {
            this.mTranscodeBarrier.await();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        } catch (BrokenBarrierException be) {
            throw new IllegalStateException(be);
        }
    }

    @Override
    public void onCompleted(Composition composition, ExportResult exportResult) {
        Transformer.Listener.super.onCompleted(composition, exportResult);
        awaitTranscodeBarrier();
    }

    @Override
    public void onError(
            Composition composition, ExportResult exportResult, ExportException exportException) {
        Transformer.Listener.super.onError(composition, exportResult, exportException);
        awaitTranscodeBarrier();
    }
}
