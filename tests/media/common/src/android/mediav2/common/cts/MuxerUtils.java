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

package android.mediav2.common.cts;

import static android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_FIRST;
import static android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_LAST;
import static android.media.codec.Flags.apvSupport;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Wrapper class for trying and testing muxers.
 */
public class MuxerUtils {
    private static final String LOG_TAG = MuxerUtils.class.getSimpleName();

    // muxer formats, media type map as per CDD and media codec developers guide
    private static final List<String> MEDIATYPE_LIST_FOR_TYPE_MP4 = new ArrayList<>(
            Arrays.asList(MediaFormat.MIMETYPE_VIDEO_MPEG4, MediaFormat.MIMETYPE_VIDEO_H263,
                    MediaFormat.MIMETYPE_VIDEO_AVC, MediaFormat.MIMETYPE_VIDEO_HEVC,
                    MediaFormat.MIMETYPE_AUDIO_AAC, MediaFormat.MIMETYPE_IMAGE_ANDROID_HEIC,
                    MediaFormat.MIMETYPE_TEXT_SUBRIP));
    static {
        if (CodecTestBase.IS_AT_LEAST_U) {
            MEDIATYPE_LIST_FOR_TYPE_MP4.add(MediaFormat.MIMETYPE_VIDEO_AV1);
            MEDIATYPE_LIST_FOR_TYPE_MP4.add(MediaFormat.MIMETYPE_IMAGE_AVIF);
        }
        if (CodecTestBase.IS_AT_LEAST_B && apvSupport()) {
            MEDIATYPE_LIST_FOR_TYPE_MP4.add(MediaFormat.MIMETYPE_VIDEO_APV);
        }
    }
    private static final List<String> MEDIATYPE_LIST_FOR_TYPE_WEBM =
            Arrays.asList(MediaFormat.MIMETYPE_VIDEO_VP8, MediaFormat.MIMETYPE_VIDEO_VP9,
                    MediaFormat.MIMETYPE_AUDIO_VORBIS, MediaFormat.MIMETYPE_AUDIO_OPUS);
    private static final List<String> MEDIATYPE_LIST_FOR_TYPE_3GP =
            Arrays.asList(MediaFormat.MIMETYPE_VIDEO_MPEG4, MediaFormat.MIMETYPE_VIDEO_H263,
                    MediaFormat.MIMETYPE_VIDEO_AVC, MediaFormat.MIMETYPE_AUDIO_AAC,
                    MediaFormat.MIMETYPE_AUDIO_AMR_NB, MediaFormat.MIMETYPE_AUDIO_AMR_WB);
    private static final List<String> MEDIATYPE_LIST_FOR_TYPE_OGG =
            Collections.singletonList(MediaFormat.MIMETYPE_AUDIO_OPUS);

    private static final Map<String, int[]> MEDIATYPE_MUX_FORMATS_MAP_CACHE = new HashMap<>();

    public static void muxOutput(String filePath, int muxerFormat, MediaFormat format,
            ByteBuffer buffer, ArrayList<MediaCodec.BufferInfo> infos) throws IOException {
        MediaMuxer muxer = null;
        try {
            muxer = new MediaMuxer(filePath, muxerFormat);
            int trackID = muxer.addTrack(format);
            muxer.start();
            for (MediaCodec.BufferInfo info : infos) {
                if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                    muxer.writeSampleData(trackID, buffer, info);
                }
            }
            muxer.stop();
        } finally {
            if (muxer != null) muxer.release();
        }
    }

    private static boolean isMediaTypeContainerPairSupportedLookUp(String mediaType,
            int muxFormat) {
        boolean result = false;
        if (muxFormat == MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4) {
            result = MEDIATYPE_LIST_FOR_TYPE_MP4.contains(mediaType)
                    || mediaType.startsWith("application/");
        } else if (muxFormat == MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM) {
            result = MEDIATYPE_LIST_FOR_TYPE_WEBM.contains(mediaType);
        } else if (muxFormat == MediaMuxer.OutputFormat.MUXER_OUTPUT_3GPP) {
            result = MEDIATYPE_LIST_FOR_TYPE_3GP.contains(mediaType);
        } else if (muxFormat == MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG) {
            result = MEDIATYPE_LIST_FOR_TYPE_OGG.contains(mediaType);
        }
        return result;
    }

    public static boolean isMediaTypeContainerPairSupported(String mediaType, int muxFormat)
            throws IOException {
        boolean result = isMediaTypeContainerPairSupportedLookUp(mediaType, muxFormat);
        if (!result) {
            MediaFormat mediaFormat;
            if (mediaType.startsWith("audio/")) {
                mediaFormat = MediaFormat.createAudioFormat(mediaType, 16000, 2);
            } else if (mediaType.startsWith("video/") || mediaType.startsWith("image/")) {
                mediaFormat = MediaFormat.createVideoFormat(mediaType, 352, 288);
            } else {
                mediaFormat = new MediaFormat();
                mediaFormat.setString(MediaFormat.KEY_MIME, mediaType);
            }
            String tmpPath = getTempFilePath("muxer");
            MediaMuxer muxer = new MediaMuxer(tmpPath, muxFormat);
            try {
                muxer.addTrack(mediaFormat);
                result = true;
            } catch (RuntimeException ignored) {
                /* ignored */
            } finally {
                muxer.release();
                File tmp = new File(tmpPath);
                if (tmp.exists()) assertTrue("unable to delete file " + tmpPath, tmp.delete());
            }
        }
        return result;
    }

    public static int[] getMuxerFormatsListForMediaType(String mediaType) throws IOException {
        if (!MEDIATYPE_MUX_FORMATS_MAP_CACHE.containsKey(mediaType)) {
            ArrayList<Integer> muxerFormats = new ArrayList<>();
            for (int muxFormat = MUXER_OUTPUT_FIRST; muxFormat <= MUXER_OUTPUT_LAST; muxFormat++) {
                if (isMediaTypeContainerPairSupported(mediaType, muxFormat)) {
                    muxerFormats.add(muxFormat);
                }
            }
            MEDIATYPE_MUX_FORMATS_MAP_CACHE.put(mediaType,
                    muxerFormats.stream().mapToInt(i -> i).toArray());
        }
        return MEDIATYPE_MUX_FORMATS_MAP_CACHE.get(mediaType);
    }

    public static int getMuxerFormatForMediaType(String mediaType) throws IOException {
        int[] muxerFormats = getMuxerFormatsListForMediaType(mediaType);
        if (muxerFormats.length == 0) {
            fail("no muxer support for " + mediaType);
        }
        return muxerFormats[0];
    }

    public static String getTempFilePath(String infix) throws IOException {
        return File.createTempFile("tmp" + infix, ".bin").getAbsolutePath();
    }

    public static String muxerFormatToString(int muxFormat) {
        return switch (muxFormat) {
            case MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4 -> "mp4";
            case MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM -> "webm";
            case MediaMuxer.OutputFormat.MUXER_OUTPUT_3GPP -> "3gp";
            case MediaMuxer.OutputFormat.MUXER_OUTPUT_HEIF -> "heif";
            case MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG -> "ogg";
            default -> "muxFormat" + muxFormat;
        };
    }
}
