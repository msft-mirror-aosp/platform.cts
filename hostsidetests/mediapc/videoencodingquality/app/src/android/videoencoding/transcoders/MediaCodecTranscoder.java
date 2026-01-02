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

import android.mediav2.common.cts.CodecEncoderSurfaceTestBase;
import android.mediav2.common.cts.EncoderConfigParams;
import android.mediav2.common.cts.OutputManager;

import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Video Transcoder that uses the MediaCodec APIs (via the shared {@link
 * CodecEncoderSurfaceTestBase} to transcode a video based on the constructor-provided parameters.
 */
public final class MediaCodecTranscoder extends CodecEncoderSurfaceTestBase {

    private final String mOutputFileName;
    private final String mPathPrefix;

    public MediaCodecTranscoder(
            String encoder,
            String mediaType,
            String decoder,
            String testFileMediaType,
            String testFile,
            EncoderConfigParams encCfgParams,
            String outputFileName,
            int decColorFormat,
            boolean isOutputToneMapped,
            boolean usePersistentSurface,
            String allTestParams,
            String pathPrefix) {
        super(
                encoder,
                mediaType,
                decoder,
                testFileMediaType,
                testFile,
                encCfgParams,
                decColorFormat,
                isOutputToneMapped,
                usePersistentSurface,
                allTestParams);
        mOutputFileName = outputFileName;
        mPathPrefix = pathPrefix;
    }

    @Override
    public void setUpCodecEncoderSurfaceTestBase() throws IOException, CloneNotSupportedException {
        super.setUpCodecEncoderSurfaceTestBase();
        mEncoderFormat = mEncCfgParams.getFormat();
    }

    private String getTempFilePath(String infix) throws IOException {
        String totalPath = mPathPrefix + infix + ".mp4";
        new FileOutputStream(totalPath).close();
        return totalPath;
    }

    /**
     * Performs the video transcode described by this class using the framework provided media
     * components (e.g. {@link MediaCodec}).
     */
    public void doTranscode() throws IOException, InterruptedException, CloneNotSupportedException {
        try {
            setUpCodecEncoderSurfaceTestBase();
            encodeToMemory(
                    false,
                    false,
                    false,
                    new OutputManager(),
                    true,
                    getTempFilePath(mOutputFileName));
        } finally {
            tearDownCodecEncoderSurfaceTestBase();
        }
    }
}
