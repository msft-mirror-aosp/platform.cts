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

package android.mediav2.common.cts;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;

import androidx.media3.exoplayer.MediaExtractorCompat;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.UUID;


/**
 * Maps {@link IMediaExtractorInterface} to the framework's {@link android.media.MediaExtractor}
 */
class FrameworkMediaExtractor implements IMediaExtractorInterface {

    public FrameworkMediaExtractor() {
        mExtractor = new MediaExtractor();
    }

    @Override
    public boolean advance() {
        return mExtractor.advance();
    }

    @Override
    public Map<UUID, byte[]> getPsshInfo() {
        return mExtractor.getPsshInfo();
    }

    @Override
    public int getSampleFlags() {
        return mExtractor.getSampleFlags();
    }

    @Override
    public long getSampleSize() {
        return mExtractor.getSampleSize();
    }

    @Override
    public long getSampleTime() {
        return mExtractor.getSampleTime();
    }

    @Override
    public int getSampleTrackIndex() {
        return mExtractor.getSampleTrackIndex();
    }

    @Override
    public int getTrackCount() {
        return mExtractor.getTrackCount();
    }

    @Override
    public MediaFormat getTrackFormat(int index) {
        return mExtractor.getTrackFormat(index);
    }

    @Override
    public int readSampleData(ByteBuffer byteBuf, int offset) {
        return mExtractor.readSampleData(byteBuf, offset);
    }

    @Override
    public void release() {
        mExtractor.release();
    }

    @Override
    public void seekTo(long timeUs, int mode) {
        mExtractor.seekTo(timeUs, mode);
    }

    @Override
    public void selectTrack(int index) {
        mExtractor.selectTrack(index);
    }

    @Override
    public void setDataSource(String path) throws IOException {
        mExtractor.setDataSource(path);
    }

    @Override
    public void unselectTrack(int index) {
        mExtractor.unselectTrack(index);
    }

    @Override
    public boolean getSampleCryptoInfo(MediaCodec.CryptoInfo info) {
        return mExtractor.getSampleCryptoInfo(info);
    }

    private final MediaExtractor mExtractor;
}

/**
 * Maps {@link IMediaExtractorInterface} to the media3's {@link
 * androidx.media3.exoplayer.MediaExtractorCompat}
 */
class Media3MediaExtractor implements IMediaExtractorInterface {

    public Media3MediaExtractor() {
        mExtractor = new MediaExtractorCompat(CodecTestBase.getContext());
    }

    @Override
    public boolean advance() {
        return mExtractor.advance();
    }

    @Override
    public Map<UUID, byte[]> getPsshInfo() {
        return mExtractor.getPsshInfo();
    }

    @Override
    public int getSampleFlags() {
        return mExtractor.getSampleFlags();
    }

    @Override
    public long getSampleSize() {
        return mExtractor.getSampleSize();
    }

    @Override
    public long getSampleTime() {
        return mExtractor.getSampleTime();
    }

    @Override
    public int getSampleTrackIndex() {
        return mExtractor.getSampleTrackIndex();
    }

    @Override
    public int getTrackCount() {
        return mExtractor.getTrackCount();
    }

    @Override
    public MediaFormat getTrackFormat(int index) {
        return mExtractor.getTrackFormat(index);
    }

    @Override
    public int readSampleData(ByteBuffer byteBuf, int offset) {
        return mExtractor.readSampleData(byteBuf, offset);
    }

    @Override
    public void release() {
        mExtractor.release();
    }

    @Override
    public void seekTo(long timeUs, int mode) {
        mExtractor.seekTo(timeUs, mode);
    }

    @Override
    public void selectTrack(int index) {
        mExtractor.selectTrack(index);
    }

    @Override
    public void setDataSource(String path) throws IOException {
        mExtractor.setDataSource(path);
    }

    @Override
    public void unselectTrack(int index) {
        mExtractor.unselectTrack(index);
    }

    @Override
    public boolean getSampleCryptoInfo(MediaCodec.CryptoInfo info) {
        return mExtractor.getSampleCryptoInfo(info);
    }

    private final MediaExtractorCompat mExtractor;
}

/**
 * MediaExtractorProvider class that offers services of android media extractor and android
 * media3 extractor at once.
 */
public class MediaExtractorProvider {
    public enum ExtractorType {
        FRAMEWORK,
        MEDIA3,
        UNDEFINED;

        public static String toString(ExtractorType selectSwitch) {
            switch (selectSwitch) {
                case FRAMEWORK:
                    return "framework-media-extractor";
                case MEDIA3:
                    return "media3-media-extractor";
                case UNDEFINED:
                default:
                    return "Unknown select switch";
            }
        }
    }

    public static IMediaExtractorInterface createMediaExtractor(ExtractorType type) {
        if (type == ExtractorType.FRAMEWORK) {
            return new FrameworkMediaExtractor();
        } else if (type == ExtractorType.MEDIA3) {
            return new Media3MediaExtractor();
        } else {
            throw new IllegalArgumentException(
                    "unrecognized extractor type : " + ExtractorType.toString(type));
        }
    }
}
