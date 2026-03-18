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

/** Statistics for a transcode operation. */
public final class TranscodeStats {
    public final long framesOut;
    public final double elapsedRealtimeSec;
    /** The throughput of the decode/encode operation in frames per second. */
    public final double transcodingFps;
    /** The average FPS of the produced content. */
    public final double encodeFps;

    public TranscodeStats(long framesOut, double elapsedRealtimeSec, double encodeFps) {
        this.framesOut = framesOut;
        this.elapsedRealtimeSec = elapsedRealtimeSec;
        this.transcodingFps = framesOut / elapsedRealtimeSec;
        this.encodeFps = encodeFps;
    }
}
