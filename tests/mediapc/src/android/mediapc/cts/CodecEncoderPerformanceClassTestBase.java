/*
 * Copyright (C) 2025 The Android Open Source Project
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

package android.mediapc.cts;


import android.graphics.ImageFormat;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.mediav2.common.cts.CodecEncoderTestBase;
import android.mediav2.common.cts.EncoderConfigParams;
import android.mediav2.common.cts.RawResource;

import java.util.function.Consumer;

/**
 * Wrapper class for trying and testing encoder components.
 */
public class CodecEncoderPerformanceClassTestBase extends CodecEncoderTestBase {
    private static final String LOG_TAG =
            CodecEncoderPerformanceClassTestBase.class.getSimpleName();

    private static final String MEDIA_DIR = WorkDir.getMediaDirString();
    // files are in WorkDir.getMediaDirString();
    protected static final RawResource INPUT_VIDEO_FILE =
            new RawResource.Builder()
                    .setFileName(MEDIA_DIR + "bbb_cif_yuv420p_30fps.yuv", false)
                    .setDimension(352, 288)
                    .setBytesPerSample(1)
                    .setColorFormat(ImageFormat.YUV_420_888)
                    .build();
    protected static final RawResource INPUT_AUDIO_FILE =
            new RawResource.Builder()
                    .setFileName(MEDIA_DIR + "bbb_2ch_44kHz_s16le.raw", true)
                    .setSampleRate(44100)
                    .setChannelCount(2)
                    .setBytesPerSample(2)
                    .setAudioEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build();

    static EncoderConfigParams getAudioEncoderCfgParams(String mediaType, int qualityPreset,
            int sampleRate, int channelCount) {
        EncoderConfigParams.Builder foreman = new EncoderConfigParams.Builder(mediaType)
                .setSampleRate(sampleRate)
                .setChannelCount(channelCount);
        if (mediaType.equals(MediaFormat.MIMETYPE_AUDIO_FLAC)) {
            foreman = foreman.setCompressionLevel(qualityPreset);
        } else {
            foreman = foreman.setBitRate(qualityPreset);
        }
        return foreman.build();
    }

    static EncoderConfigParams getVideoEncoderCfgParams(String mediaType, int bitRate, int width,
            int height, int frameRate, int colorFormat) {
        return new EncoderConfigParams.Builder(mediaType)
                .setBitRate(bitRate)
                .setWidth(width)
                .setHeight(height)
                .setFrameRate(frameRate)
                .setKeyFrameInterval(1.0f)
                .setColorFormat(colorFormat)
                .build();
    }

    // Callback for each time the output count changes.
    // This can be used to measure codec performance.
    protected Consumer<Integer> mOutputCountListener;

    CodecEncoderPerformanceClassTestBase(String mediaType, String codecName,
            EncoderConfigParams[] encCfgParams) {
        super(codecName, mediaType, encCfgParams, "params not filled by test suite");
    }

    // must not be called during doWork
    protected void setOutputCountListener(Consumer<Integer> listener) {
        mOutputCountListener = listener;
    }

    @Override
    protected void dequeueOutput(int bufferIndex, MediaCodec.BufferInfo info) {
        if (info.size > 0 && (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
            if (mOutputCountListener != null) {
                mOutputCountListener.accept(mOutputCount + 1);
            }
        }
        super.dequeueOutput(bufferIndex, info);
    }

    @Override
    protected void validateTestState() {}
}
