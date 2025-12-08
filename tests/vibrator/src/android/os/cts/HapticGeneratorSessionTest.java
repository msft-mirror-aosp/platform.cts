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

package android.os.cts;

import static android.os.vibrator.Flags.FLAG_HAPTIC_PCM_GENERATION;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.media.AudioFormat;
import android.os.OutcomeReceiver;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.os.vibrator.HapticGeneratorChannelStream;
import android.os.vibrator.HapticGeneratorSession;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.annotation.NonNull;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;
import com.android.compatibility.common.util.ApiTest;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.nio.channels.ClosedChannelException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Tests for {@link HapticGeneratorSession} and callbacks. */
@RunWith(Parameterized.class)
@RequiresFlagsEnabled(FLAG_HAPTIC_PCM_GENERATION)
public class HapticGeneratorSessionTest {
    @Rule(order = 0)
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Rule(order = 1)
    public final AdoptShellPermissionsRule mAdoptShellPermissionsRule =
            new AdoptShellPermissionsRule(
                    InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                    android.Manifest.permission.USE_VIBRATOR_HAPTIC_GENERATOR);

    /**
     * Provides the vibrator accessed with the given vibrator ID, at the time of test running. A
     * vibratorId of -1 indicates to use the system default vibrator.
     */
    private interface VibratorProvider {
        Vibrator getVibrator();
    }

    private static void addTestParameter(
            List<Object[]> data, String testLabel, VibratorProvider vibratorProvider) {
        data.add(new Object[] {testLabel, vibratorProvider});
    }

    @Parameterized.Parameters(name = "{0}")
    public static Iterable<Object[]> data() {
        // Test params are Name,Vibrator pairs. All vibrators on the system should conform to this
        // test.
        ArrayList<Object[]> data = new ArrayList<>();
        // These vibrators should be identical, but verify both APIs explicitly.
        addTestParameter(
                data,
                "systemVibrator",
                () ->
                        InstrumentationRegistry.getInstrumentation()
                                .getContext()
                                .getSystemService(Vibrator.class));
        // VibratorManager also presents getDefaultVibrator, but in VibratorManagerTest
        // it is asserted that the Vibrator system service and getDefaultVibrator are
        // the same object, so we don't test it twice here.

        VibratorManager vibratorManager =
                InstrumentationRegistry.getInstrumentation()
                        .getContext()
                        .getSystemService(VibratorManager.class);
        for (int vibratorId : vibratorManager.getVibratorIds()) {
            addTestParameter(
                    data,
                    "vibratorId:" + vibratorId,
                    () ->
                            InstrumentationRegistry.getInstrumentation()
                                    .getContext()
                                    .getSystemService(VibratorManager.class)
                                    .getVibrator(vibratorId));
        }
        return data;
    }

    private static final long TEST_TIMEOUT_MS = 5000; // 5 seconds
    private Vibrator mVibrator;
    private Executor mExecutor;

    // Test label is needed for execution history
    public HapticGeneratorSessionTest(String testLabel, VibratorProvider vibratorProvider) {
        mExecutor = Executors.newSingleThreadExecutor();
        mVibrator = vibratorProvider.getVibrator();
        assertThat(mVibrator).isNotNull();
    }

    @Test
    @ApiTest(
            apis = {
                "android.os.Vibrator#isHapticGeneratorSupported",
                "android.os.Vibrator#startHapticGeneratorSession",
                "android.os.Vibrator.HapticGeneratorSession#close",
            })
    public void testStartHapticGeneratorSession_success() throws Exception {
        assumeTrue(mVibrator.isHapticGeneratorSupported());

        HapticGeneratorSession.Config config = createValidConfig();
        HapticGeneratorOutcomeReceiver receiver = new HapticGeneratorOutcomeReceiver();

        HapticGeneratorSession session = startSessionAndWait(config, receiver);
        session.close();
    }

    @Test
    @ApiTest(
            apis = {
                "android.os.Vibrator#isHapticGeneratorSupported",
                "android.os.Vibrator#startHapticGeneratorSession",
                "android.os.Vibrator.HapticGeneratorSession#close",
            })
    public void testStartHapticGeneratorSession_allValidEncodings_success() throws Exception {
        assumeTrue(mVibrator.isHapticGeneratorSupported());

        int[] validEncodings =
                new int[] {
                    AudioFormat.ENCODING_PCM_16BIT,
                    AudioFormat.ENCODING_PCM_8BIT,
                    AudioFormat.ENCODING_PCM_FLOAT,
                    AudioFormat.ENCODING_PCM_24BIT_PACKED,
                    AudioFormat.ENCODING_PCM_32BIT,
                    AudioFormat.ENCODING_DEFAULT
                };

        for (int encoding : validEncodings) {
            HapticGeneratorSession.Config config =
                    createConfig(encoding, 48000, AudioFormat.CHANNEL_OUT_HAPTIC_A);
            try (HapticGeneratorSession session =
                    startSessionAndWait(config, new HapticGeneratorOutcomeReceiver())) {
                assertThat(session).isNotNull();
            } catch (Exception e) {
                throw new AssertionError("Failed for encoding=" + encoding, e);
            }
        }
    }

    @Test
    @ApiTest(
            apis = {
                "android.os.Vibrator#isHapticGeneratorSupported",
                "android.os.Vibrator#startHapticGeneratorSession",
                "android.os.Vibrator.HapticGeneratorSession#close",
            })
    public void testStartHapticGeneratorSession_allValidChannelMasks_success() throws Exception {
        assumeTrue(mVibrator.isHapticGeneratorSupported());

        int[] validMasks =
                new int[] {
                    AudioFormat.CHANNEL_OUT_HAPTIC_A,
                    AudioFormat.CHANNEL_OUT_HAPTIC_B,
                    AudioFormat.CHANNEL_OUT_HAPTIC_A | AudioFormat.CHANNEL_OUT_HAPTIC_B
                };

        for (int mask : validMasks) {
            HapticGeneratorSession.Config config =
                    createConfig(AudioFormat.ENCODING_PCM_16BIT, 48000, mask);
            try (HapticGeneratorSession session =
                    startSessionAndWait(config, new HapticGeneratorOutcomeReceiver())) {
                assertThat(session).isNotNull();
            } catch (Exception e) {
                throw new AssertionError("Failed for mask=" + Integer.toHexString(mask), e);
            }
        }
    }

    @Test
    @ApiTest(
            apis = {
                "android.os.Vibrator#isHapticGeneratorSupported",
                "android.os.Vibrator#startHapticGeneratorSession",
            })
    public void testStartHapticGeneratorSession_nullCallback_throwsException() throws Exception {
        assumeTrue(mVibrator.isHapticGeneratorSupported());

        HapticGeneratorSession.Config config = createValidConfig();

        assertThrows(
                NullPointerException.class,
                () -> {
                    mVibrator.startHapticGeneratorSession(config, mExecutor, null);
                });
    }

    @Test
    @ApiTest(
            apis = {
                "android.os.Vibrator#isHapticGeneratorSupported",
                "android.os.Vibrator#startHapticGeneratorSession",
            })
    public void testStartHapticGeneratorSession_nullExecutor_throwsException() throws Exception {
        assumeTrue(mVibrator.isHapticGeneratorSupported());

        HapticGeneratorSession.Config config = createValidConfig();
        HapticGeneratorOutcomeReceiver receiver = new HapticGeneratorOutcomeReceiver();

        assertThrows(
                NullPointerException.class,
                () -> {
                    mVibrator.startHapticGeneratorSession(config, null, receiver);
                });
    }

    @Test
    @ApiTest(
            apis = {
                "android.os.Vibrator#isHapticGeneratorSupported",
                "android.os.Vibrator#startHapticGeneratorSession",
            })
    public void testStartHapticGeneratorSession_notSupported_failsWithUnsupportedException()
            throws Exception {
        assumeTrue(mVibrator.hasVibrator());
        assumeFalse(mVibrator.isHapticGeneratorSupported());

        HapticGeneratorSession.Config config = createValidConfig();
        HapticGeneratorOutcomeReceiver receiver = new HapticGeneratorOutcomeReceiver();

        mVibrator.startHapticGeneratorSession(config, mExecutor, receiver);
        assertThrows(
                UnsupportedOperationException.class, () -> receiver.getSession(TEST_TIMEOUT_MS));
    }

    @Test
    @ApiTest(apis = "android.os.Vibrator#startHapticGeneratorSession")
    public void testStartHapticGeneratorSession_noVibrator_failsWithIllegalStateException()
            throws Exception {
        assumeFalse(mVibrator.hasVibrator());

        HapticGeneratorSession.Config config = createValidConfig();
        HapticGeneratorOutcomeReceiver receiver = new HapticGeneratorOutcomeReceiver();

        mVibrator.startHapticGeneratorSession(config, mExecutor, receiver);
        assertThrows(IllegalStateException.class, () -> receiver.getSession(TEST_TIMEOUT_MS));
    }

    @Test
    @ApiTest(
            apis = {
                "android.os.Vibrator#isHapticGeneratorSupported",
                "android.os.Vibrator.HapticGeneratorSession#Config",
            })
    public void testHapticGeneratorSessionConfig_invalidSampleRate_fails() throws Exception {
        assumeTrue(mVibrator.isHapticGeneratorSupported());

        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    createConfig(
                            AudioFormat.ENCODING_PCM_16BIT,
                            /* sampleRate= */ 0,
                            AudioFormat.CHANNEL_OUT_HAPTIC_A);
                });

        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    createConfig(
                            AudioFormat.ENCODING_PCM_16BIT,
                            /* sampleRate= */ -1,
                            AudioFormat.CHANNEL_OUT_HAPTIC_A);
                });
    }

    @Test
    @ApiTest(
            apis = {
                "android.os.Vibrator#isHapticGeneratorSupported",
                "android.os.Vibrator.HapticGeneratorSession#Config",
            })
    public void testHapticGeneratorSessionConfig_invalidEmptyChannelMask_fails() throws Exception {
        assumeTrue(mVibrator.isHapticGeneratorSupported());

        AudioFormat formatWithoutMask =
                new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(48000)
                        // .setChannelMask() is skipped, defaults to 0 (INVALID)
                        .build();

        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    new HapticGeneratorSession.Config(formatWithoutMask, null);
                });
    }

    @Test
    @ApiTest(
            apis = {
                "android.os.Vibrator#isHapticGeneratorSupported",
                "android.os.Vibrator.HapticGeneratorSession#Config",
            })
    public void testHapticGeneratorSessionConfig_invalidChannelMaskButValidIndexMask_succeeds()
            throws Exception {
        assumeTrue(mVibrator.isHapticGeneratorSupported());

        AudioFormat formatWithIndexMask =
                new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(48000)
                        // .setChannelMask() is skipped, defaults to 0 (INVALID)
                        .setChannelIndexMask(0x3)
                        .build();

        HapticGeneratorSession.Config config =
                new HapticGeneratorSession.Config(formatWithIndexMask, null);

        assertThat(config).isNotNull();
    }

    @Test
    @ApiTest(
            apis = {
                "android.os.Vibrator#isHapticGeneratorSupported",
                "android.os.Vibrator#startHapticGeneratorSession",
                "android.os.Vibrator.HapticGeneratorSession#generateHapticChannelStream",
                "android.os.Vibrator.HapticGeneratorSession#close",
            })
    public void testHapticGeneratorSession_generateHapticChannelStreamAfterClose_fails()
            throws Exception {
        assumeTrue(mVibrator.isHapticGeneratorSupported());

        HapticGeneratorSession session =
                startSessionAndWait(createValidConfig(), new HapticGeneratorOutcomeReceiver());

        session.close();

        VibrationEffect effect =
                VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE);
        assertThrows(
                IllegalStateException.class,
                () -> {
                    session.generateHapticChannelStream(effect);
                });
    }

    @Test
    @ApiTest(
            apis = {
                "android.os.Vibrator#isHapticGeneratorSupported",
                "android.os.Vibrator#startHapticGeneratorSession",
                "android.os.Vibrator.HapticGeneratorSession#generateHapticChannelStream",
            })
    public void testHapticChannelStream_generateWithRepeatingEffect_throwsException()
            throws Exception {
        assumeTrue(mVibrator.isHapticGeneratorSupported());

        try (HapticGeneratorSession session =
                startSessionAndWait(createValidConfig(), new HapticGeneratorOutcomeReceiver())) {

            // Create a repeating waveform
            VibrationEffect repeatingEffect =
                    VibrationEffect.createWaveform(new long[] {10, 20}, new int[] {255, 0}, 0);

            assertThrows(
                    IllegalArgumentException.class,
                    () -> {
                        session.generateHapticChannelStream(repeatingEffect);
                    });
        }
    }

    @Test
    @ApiTest(
            apis = {
                "android.os.Vibrator#isHapticGeneratorSupported",
                "android.os.Vibrator#startHapticGeneratorSession",
                "android.os.Vibrator.HapticGeneratorSession#generateHapticChannelStream",
                "android.os.Vibrator.HapticGeneratorSession#close",
                "android.os.Vibrator.HapticGeneratorChannelStream#read",
                "android.os.Vibrator.HapticGeneratorChannelStream#close",
            })
    public void testHapticChannelStream_readSuccess() throws Exception {
        assumeTrue(mVibrator.isHapticGeneratorSupported());

        try (HapticGeneratorSession session =
                startSessionAndWait(createValidConfig(), new HapticGeneratorOutcomeReceiver())) {
            VibrationEffect effect =
                    VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE);

            try (HapticGeneratorChannelStream stream =
                    session.generateHapticChannelStream(effect)) {
                assertThat(stream).isNotNull();

                byte[] buffer = new byte[1024];
                int bytesRead = stream.read(buffer);

                // Ensure bytesRead are >= 0 for data, or -1 for EOF.
                assertThat(bytesRead).isAtLeast(-1);
            }
        }
    }

    @Test
    @ApiTest(
            apis = {
                "android.os.Vibrator#isHapticGeneratorSupported",
                "android.os.Vibrator#startHapticGeneratorSession",
                "android.os.Vibrator.HapticGeneratorSession#generateHapticChannelStream",
                "android.os.Vibrator.HapticGeneratorSession#close",
                "android.os.Vibrator.HapticGeneratorChannelStream#read",
                "android.os.Vibrator.HapticGeneratorChannelStream#close",
            })
    public void testHapticChannelStream_sequentialStreams_produceIdenticalOutput()
            throws Exception {
        assumeTrue(mVibrator.isHapticGeneratorSupported());

        try (HapticGeneratorSession session =
                startSessionAndWait(createValidConfig(), new HapticGeneratorOutcomeReceiver())) {

            VibrationEffect effect =
                    VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE);

            int readSize = 128;
            byte[] output1 = new byte[readSize];
            byte[] output2 = new byte[readSize];

            try (HapticGeneratorChannelStream stream1 =
                    session.generateHapticChannelStream(effect)) {
                assertThat(stream1).isNotNull();
                int bytesRead = stream1.read(output1);
                assertThat(bytesRead).isEqualTo(readSize);
            }

            try (HapticGeneratorChannelStream stream2 =
                    session.generateHapticChannelStream(effect)) {
                assertThat(stream2).isNotNull();
                int bytesRead = stream2.read(output2);
                assertThat(bytesRead).isEqualTo(readSize);
            }

            assertThat(output1).isEqualTo(output2);
        }
    }

    @Test
    @ApiTest(
            apis = {
                "android.os.Vibrator#isHapticGeneratorSupported",
                "android.os.Vibrator#startHapticGeneratorSession",
                "android.os.Vibrator.HapticGeneratorSession#generateHapticChannelStream",
                "android.os.Vibrator.HapticGeneratorSession#close",
                "android.os.Vibrator.HapticGeneratorChannelStream#read",
                "android.os.Vibrator.HapticGeneratorChannelStream#close",
            })
    public void testHapticChannelStream_closeStream_readFails() throws Exception {
        assumeTrue(mVibrator.isHapticGeneratorSupported());

        HapticGeneratorSession session =
                startSessionAndWait(createValidConfig(), new HapticGeneratorOutcomeReceiver());
        VibrationEffect effect =
                VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE);

        try (session) {
            HapticGeneratorChannelStream stream = session.generateHapticChannelStream(effect);
            assertThat(stream).isNotNull();
            // close stream
            stream.close();

            // Attempt to read from it. Should throw exception
            byte[] buffer = new byte[100];
            assertThrows(ClosedChannelException.class, () -> stream.read(buffer));
        }
    }

    @Test
    @ApiTest(
            apis = {
                "android.os.Vibrator#isHapticGeneratorSupported",
                "android.os.Vibrator#startHapticGeneratorSession",
                "android.os.Vibrator.HapticGeneratorSession#generateHapticChannelStream",
                "android.os.Vibrator.HapticGeneratorSession#close",
                "android.os.Vibrator.HapticGeneratorChannelStream#read",
                "android.os.Vibrator.HapticGeneratorChannelStream#close",
            })
    public void testHapticChannelStream_closeSession_readFails() throws Exception {
        assumeTrue(mVibrator.isHapticGeneratorSupported());

        HapticGeneratorSession session =
                startSessionAndWait(createValidConfig(), new HapticGeneratorOutcomeReceiver());
        VibrationEffect effect =
                VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE);

        try (HapticGeneratorChannelStream stream = session.generateHapticChannelStream(effect)) {
            assertThat(stream).isNotNull();
            // close session
            session.close();

            // Attempt to read from it. Should through exception
            byte[] buffer = new byte[100];
            assertThrows(ClosedChannelException.class, () -> stream.read(buffer));
        }
    }

    @Test
    @ApiTest(
            apis = {
                "android.os.Vibrator#isHapticGeneratorSupported",
                "android.os.Vibrator#startHapticGeneratorSession",
                "android.os.Vibrator.HapticGeneratorSession#generateHapticChannelStream",
                "android.os.Vibrator.HapticGeneratorSession#close",
                "android.os.Vibrator.HapticGeneratorChannelStream#read",
                "android.os.Vibrator.HapticGeneratorChannelStream#close",
            })
    public void testHapticChannelStream_startNewStreamClosesOldStream() throws Exception {
        assumeTrue(mVibrator.isHapticGeneratorSupported());

        HapticGeneratorSession session =
                startSessionAndWait(createValidConfig(), new HapticGeneratorOutcomeReceiver());
        VibrationEffect effect =
                VibrationEffect.createOneShot(5000, VibrationEffect.DEFAULT_AMPLITUDE);

        try (session) {
            // Start Stream 1
            HapticGeneratorChannelStream stream1 = session.generateHapticChannelStream(effect);
            assertThat(stream1).isNotNull();

            assertThat(stream1.read(new byte[10])).isAtLeast(-1);

            // Start Stream 2
            HapticGeneratorChannelStream stream2 = session.generateHapticChannelStream(effect);
            assertThat(stream2).isNotNull();

            // Verify Stream 1 is now closed. It should throw ClosedChannelException on the next
            // operation
            byte[] buffer = new byte[10];
            assertThrows(ClosedChannelException.class, () -> stream1.read(buffer));

            // Verify Stream 2 is still working properly
            assertThat(stream2.read(buffer)).isAtLeast(-1);

            stream2.close();
        }
    }

    private HapticGeneratorSession.Config createValidConfig() {
        return createConfig(
                AudioFormat.ENCODING_PCM_16BIT, 48000, AudioFormat.CHANNEL_OUT_HAPTIC_A);
    }

    private HapticGeneratorSession.Config createConfig(
            int encoding, int sampleRate, int channelMask) {
        AudioFormat audioFormat =
                new AudioFormat.Builder()
                        .setEncoding(encoding)
                        .setSampleRate(sampleRate)
                        .setChannelMask(channelMask)
                        .build();
        return new HapticGeneratorSession.Config(audioFormat, null);
    }

    private HapticGeneratorSession startSessionAndWait(
            HapticGeneratorSession.Config config, HapticGeneratorOutcomeReceiver receiver)
            throws Exception {

        mVibrator.startHapticGeneratorSession(config, mExecutor, receiver);
        HapticGeneratorSession session = receiver.getSession(TEST_TIMEOUT_MS);
        assertThat(session).isNotNull();
        return session;
    }

    private static class HapticGeneratorOutcomeReceiver
            implements OutcomeReceiver<HapticGeneratorSession, Exception> {

        private final CountDownLatch mLatch = new CountDownLatch(1);
        private HapticGeneratorSession mSession;
        private Exception mException = null;

        @Override
        public void onResult(HapticGeneratorSession session) {
            mSession = session;
            mLatch.countDown();
        }

        @Override
        public void onError(@NonNull Exception error) {
            mException = error;
            mLatch.countDown();
        }

        HapticGeneratorSession getSession(long timeoutMs) throws TimeoutException, Exception {
            if (!mLatch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                throw new TimeoutException("Callback was not received within " + timeoutMs + "ms");
            }

            if (mException != null) {
                throw mException;
            }
            return mSession;
        }
    }
}
