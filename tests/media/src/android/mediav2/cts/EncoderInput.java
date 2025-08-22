/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.mediav2.cts;

import static android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible;
import static android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatYUVP010;
import static android.mediav2.common.cts.CodecEncoderTestBase.audioEncodingToString;

import android.graphics.ImageFormat;
import android.media.AudioFormat;
import android.mediav2.common.cts.EncoderConfigParams;
import android.mediav2.common.cts.RawResource;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Class containing encoder input resources.
 */
public class EncoderInput {
    private static final String MEDIA_DIR = WorkDir.getMediaDirString();

    // files are in WorkDir.getMediaDirString();
    private static final RawResource INPUT_VIDEO_FILE =
            new RawResource.Builder()
                    .setFileName(MEDIA_DIR + "bbb_cif_yuv420p_30fps.yuv", false)
                    .setDimension(352, 288)
                    .setBytesPerSample(1)
                    .setColorFormat(ImageFormat.YUV_420_888)
                    .build();
    private static final RawResource INPUT_VIDEO_FILE_HBD =
            new RawResource.Builder()
                    .setFileName(MEDIA_DIR + "cosmat_cif_24fps_yuv420p16le.yuv", false)
                    .setDimension(352, 288)
                    .setBytesPerSample(2)
                    .setColorFormat(ImageFormat.YCBCR_P010)
                    .build();

    private static final List<RawResource> INPUT_AUDIO_FILES = new ArrayList<>();

    static {
        int[] sampleRates = new int[]{8000, 12000, 16000, 22050, 24000, 32000, 44100, 48000, 96000,
                176000, 192000};
        int[] channelCounts = new int[]{1, 2, 5, 6};
        int[] audioFormats =
                new int[]{AudioFormat.ENCODING_PCM_16BIT, AudioFormat.ENCODING_PCM_FLOAT};
        for (int audioFormat : audioFormats) {
            for (int channelCount : channelCounts) {
                for (int sampleRate : sampleRates) {
                    ArrayList<String> files = new ArrayList<>();
                    files.add(String.format(MEDIA_DIR + "audio/bbb_%dch_%dkHz_%s_3s.raw",
                            channelCount, sampleRate / 1000, audioEncodingToString(audioFormat)));
                    files.add(String.format(MEDIA_DIR + "audio/highres_%dch_%dkHz_%s_5s.raw",
                            channelCount, sampleRate / 1000, audioEncodingToString(audioFormat)));
                    files.add(String.format(MEDIA_DIR + "audio/cand_%dch_%dkHz_%s_3s.raw",
                            channelCount, sampleRate / 1000, audioEncodingToString(audioFormat)));
                    for (String file : files) {
                        if (new File(file).exists()) {
                            INPUT_AUDIO_FILES.add(new RawResource.Builder()
                                    .setFileName(file, true)
                                    .setSampleRate(sampleRate)
                                    .setChannelCount(channelCount)
                                    .setBytesPerSample(AudioFormat.getBytesPerSample(audioFormat))
                                    .setAudioEncoding(audioFormat)
                                    .build());
                        }
                    }
                }
            }
        }
    }

    public static RawResource getRawResource(EncoderConfigParams cfg) {
        if (cfg.mIsAudio) {
            for (RawResource res : INPUT_AUDIO_FILES) {
                if (cfg.mChannelCount == res.mChannelCount && cfg.mSampleRate == res.mSampleRate
                        && cfg.mPcmEncoding == res.mAudioEncoding) {
                    return res;
                }
            }
            for (RawResource res : INPUT_AUDIO_FILES) {
                if (cfg.mChannelCount == res.mChannelCount && 48000 == res.mSampleRate
                        && cfg.mPcmEncoding == res.mAudioEncoding) {
                    return res;
                }
            }
            for (RawResource res : INPUT_AUDIO_FILES) {
                if (2 == res.mChannelCount && 48000 == res.mSampleRate
                        && cfg.mPcmEncoding == res.mAudioEncoding) {
                    return res;
                }
            }
            return null;
        } else {
            if (cfg.mColorFormat == COLOR_FormatYUV420Flexible) {
                return INPUT_VIDEO_FILE;
            } else if (cfg.mColorFormat == COLOR_FormatYUVP010) {
                return INPUT_VIDEO_FILE_HBD;
            }
        }
        return null;
    }
}
