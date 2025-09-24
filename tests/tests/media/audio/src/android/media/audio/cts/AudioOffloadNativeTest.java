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

package android.media.audio.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.os.Build;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.FrameworkSpecificTest;

import org.junit.Test;
import org.junit.runner.RunWith;

@SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM)
@FrameworkSpecificTest
@RunWith(AndroidJUnit4.class)
public class AudioOffloadNativeTest {
    private static final String TAG = "AudioOffloadNativeTest";

    private static final int STATUS_OK = 0;
    private static final int AUDIOTRACK_DEFAULT_SAMPLE_RATE = 48000;
    private static final int PER_TEST_TIMEOUT_SMALL_TEST_MS = 60000;

    static {
        System.loadLibrary("audiocts_aaudio_jni");
    }

    private boolean hasAudioOutput() {
        return InstrumentationRegistry.getInstrumentation()
                .getTargetContext()
                .getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_AUDIO_OUTPUT);
    }

    @Test(timeout = PER_TEST_TIMEOUT_SMALL_TEST_MS)
    public void testMmapPcmOffload() {
        assumeTrue("Skipping test, no audio output found", hasAudioOutput());

        // The encoding, sample rate and the channel mask should be the same as the
        // one in native for creating stream.
        final boolean offloadSupported =
                AudioManager.isOffloadedPlaybackSupported(
                        new AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                                .setSampleRate(AUDIOTRACK_DEFAULT_SAMPLE_RATE)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                                .build(),
                        new AudioAttributes.Builder().build());

        assumeTrue("Skipping test, offload support not found", offloadSupported);

        int result = nativeTestMmapPcmOffload();
        assertEquals(STATUS_OK, result);
    }

    private static native int nativeTestMmapPcmOffload();
}
