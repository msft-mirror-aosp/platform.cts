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
import android.media.MediaFormat;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.UUID;

/**
 * MediaExtractor-compatible interface
 *
 * <p>abstracts the underlying media extractor implementation. This interface allows for a runtime
 * choice between either Platform MediaExtractor {@link android.media.MediaExtractor} or
 * MediaExtractorCompat {@link androidx.media3.exoplayer.MediaExtractorCompat} from the Media3
 * library
 *
 * <p>NOTE: This does not cover all the public APIs of media extractor. Only the ones used by the
 * current test suite. One can continue to amend this list as per the suite(s) needs.
 */
public interface IMediaExtractorInterface {

    boolean advance();

    public Map<UUID, byte[]> getPsshInfo();

    boolean getSampleCryptoInfo(MediaCodec.CryptoInfo info);

    int getSampleFlags();

    long getSampleSize();

    long getSampleTime();

    int getSampleTrackIndex();

    int getTrackCount();

    MediaFormat getTrackFormat(int index);

    int readSampleData(ByteBuffer byteBuf, int offset);

    void release();

    void seekTo(long timeUs, int mode);

    void selectTrack(int index);

    void setDataSource(String path) throws IOException;

    void unselectTrack(int index);
}
