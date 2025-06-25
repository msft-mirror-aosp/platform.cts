/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.os.CombinedVibration;
import android.os.SystemClock;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.os.cts.vibrator.VibratorStateHelper;
import android.os.cts.vibrator.VibratorStateListener;
import android.provider.Settings;
import android.util.SparseArray;

import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

@RunWith(AndroidJUnit4.class)
public class VibratorManagerTest {

    @Rule
    public final AdoptShellPermissionsRule mAdoptShellPermissionsRule =
            new AdoptShellPermissionsRule(
                    InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                    android.Manifest.permission.ACCESS_VIBRATOR_STATE,
                    android.Manifest.permission.WRITE_SETTINGS);

    private static final long CALLBACK_TIMEOUT_MILLIS = 5_000;
    private static final VibrationAttributes VIBRATION_ATTRIBUTES =
            new VibrationAttributes.Builder()
                    .setUsage(VibrationAttributes.USAGE_TOUCH)
                    .build();

    /** Keep track of any listener created to be added to a vibrator, for cleanup purposes. */
    private final SparseArray<VibratorStateListener> mStateListeners = new SparseArray<>();

    private VibratorManager mVibratorManager;

    @Before
    public void setUp() throws InterruptedException {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        Settings.System.putInt(context.getContentResolver(), Settings.System.VIBRATE_ON, 1);

        mVibratorManager = context.getSystemService(VibratorManager.class);
        for (int vibratorId : mVibratorManager.getVibratorIds()) {
            Vibrator vibrator = mVibratorManager.getVibrator(vibratorId);
            VibratorStateListener listener = new VibratorStateListener();
            VibratorStateHelper.addListenerAndAssertInitialStateIdle(
                    vibrator, listener, CALLBACK_TIMEOUT_MILLIS);
            mStateListeners.put(vibratorId, listener);
        }
    }

    @After
    public void cleanUp() throws InterruptedException {
        mVibratorManager.cancel();
        for (int i = 0; i < mStateListeners.size(); i++) {
            int vibratorId = mStateListeners.keyAt(i);
            VibratorStateListener listener = mStateListeners.valueAt(i);
            Vibrator vibrator = mVibratorManager.getVibrator(vibratorId);
            VibratorStateHelper.removeListenerAndAssertStateIdle(
                    vibrator, listener, CALLBACK_TIMEOUT_MILLIS);
        }
    }

    @Test
    public void testGetVibratorIds() {
        // Just make sure it doesn't crash or return null when this is called; we don't really have
        // a way to test which vibrators will be returned.
        assertThat(mVibratorManager.getVibratorIds()).isNotNull();
        assertThat(mVibratorManager.getVibratorIds()).asList().containsNoDuplicates();
    }

    @Test
    public void testGetNonExistentVibratorId() {
        int missingId = Arrays.stream(mVibratorManager.getVibratorIds()).max().orElse(0) + 1;
        Vibrator vibrator = mVibratorManager.getVibrator(missingId);
        assertThat(vibrator).isNotNull();
        assertThat(vibrator.hasVibrator()).isFalse();
    }

    @Test
    public void testGetDefaultVibratorIsSameAsVibratorService() {
        // Note that VibratorTest parameterization relies on these two vibrators being identical.
        // It only runs vibrator tests on the result of one of the APIs.
        Vibrator systemVibrator =
                InstrumentationRegistry.getInstrumentation().getContext().getSystemService(
                        Vibrator.class);
        assertThat(mVibratorManager.getDefaultVibrator()).isSameInstanceAs(systemVibrator);
    }

    @Test
    public void testCancel() throws InterruptedException {
        mVibratorManager.vibrate(CombinedVibration.createParallel(
                VibrationEffect.createOneShot(10_000, VibrationEffect.DEFAULT_AMPLITUDE)));
        assertVibratorStateChangesTo(true);

        mVibratorManager.cancel();
        assertVibratorStateChangesTo(false);
    }

    @LargeTest
    @Test
    public void testCombinedVibrationOneShotStartsAndFinishesVibration()
            throws InterruptedException {
        VibrationEffect oneShot =
                VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE);
        mVibratorManager.vibrate(CombinedVibration.createParallel(oneShot));
        assertStartsThenStopsVibrating();
    }

    @Test
    public void testCombinedVibrationOneShotMaxAmplitude() throws InterruptedException {
        VibrationEffect oneShot = VibrationEffect.createOneShot(10_000, 255 /* Max amplitude */);
        mVibratorManager.vibrate(CombinedVibration.createParallel(oneShot));
        assertVibratorStateChangesTo(true);
    }

    @Test
    public void testCombinedVibrationOneShotMinAmplitude() throws InterruptedException {
        VibrationEffect oneShot = VibrationEffect.createOneShot(10_000, 1 /* Min amplitude */);
        mVibratorManager.vibrate(CombinedVibration.createParallel(oneShot),
                VIBRATION_ATTRIBUTES);
        assertVibratorStateChangesTo(true);
    }

    @LargeTest
    @Test
    public void testCombinedVibrationWaveformStartsAndFinishesVibration()
            throws InterruptedException {
        final long[] timings = new long[]{100, 200, 300, 400, 500};
        final int[] amplitudes = new int[]{64, 128, 255, 128, 64};
        VibrationEffect waveform = VibrationEffect.createWaveform(timings, amplitudes, -1);
        mVibratorManager.vibrate(CombinedVibration.createParallel(waveform));
        assertStartsThenStopsVibrating();
    }

    @LargeTest
    @Test
    public void testCombinedVibrationWaveformRepeats() throws InterruptedException {
        final long[] timings = new long[]{100, 200, 300, 400, 500};
        final int[] amplitudes = new int[]{64, 128, 255, 128, 64};
        VibrationEffect waveform = VibrationEffect.createWaveform(timings, amplitudes, 0);
        mVibratorManager.vibrate(CombinedVibration.createParallel(waveform));
        assertVibratorStateChangesTo(true);

        SystemClock.sleep(2000);
        int[] vibratorIds = mVibratorManager.getVibratorIds();
        for (int vibratorId : vibratorIds) {
            assertWithMessage(
                    "Expected repeating parallel waveform to continue vibrating on vibrator %s"
                            + " after initial duration", vibratorId)
                    .that(mVibratorManager.getVibrator(vibratorId).isVibrating()).isTrue();
        }
    }

    @Test
    public void testCombinedVibrationTargetingSingleVibrator() throws InterruptedException {
        int[] vibratorIds = mVibratorManager.getVibratorIds();
        assumeTrue(vibratorIds.length >= 2);

        VibrationEffect oneShot =
                VibrationEffect.createOneShot(10_000, VibrationEffect.DEFAULT_AMPLITUDE);

        // Vibrate each vibrator in turn, and assert that all the others are off.
        for (int vibratorId : vibratorIds) {
            Vibrator vibrator = mVibratorManager.getVibrator(vibratorId);
            mVibratorManager.vibrate(
                    CombinedVibration.startParallel()
                            .addVibrator(vibratorId, oneShot)
                            .combine());
            assertVibratorStateChangesTo(vibratorId, true);

            for (int otherVibratorId : vibratorIds) {
                if (otherVibratorId != vibratorId) {
                    assertWithMessage(
                            "Expected vibrator %s not vibrating when combined vibration started"
                                    + " on vibrator %s", otherVibratorId, vibratorId)
                            .that(mVibratorManager.getVibrator(otherVibratorId).isVibrating())
                            .isFalse();
                }
            }

            // Stop vibrator before next round.
            vibrator.cancel();
            assertVibratorStateChangesTo(false);
        }
    }

    private void assertStartsThenStopsVibrating() throws InterruptedException {
        for (int i = 0; i < mStateListeners.size(); i++) {
            assertStartsThenStopsVibrating(mStateListeners.keyAt(i));
        }
    }

    private void assertStartsThenStopsVibrating(int vibratorId) throws InterruptedException {
        VibratorStateHelper.assertStartsThenStopsVibrating(
                mVibratorManager.getVibrator(vibratorId),
                mStateListeners.get(vibratorId),
                CALLBACK_TIMEOUT_MILLIS,
                " vibrator id=" + vibratorId);
    }

    private void assertVibratorStateChangesTo(boolean expected) throws InterruptedException {
        for (int i = 0; i < mStateListeners.size(); i++) {
            assertVibratorStateChangesTo(mStateListeners.keyAt(i), expected);
        }
    }

    private void assertVibratorStateChangesTo(int vibratorId, boolean expected)
            throws InterruptedException {
        VibratorStateHelper.assertVibratorState(
                expected,
                mVibratorManager.getVibrator(vibratorId),
                mStateListeners.get(vibratorId),
                CALLBACK_TIMEOUT_MILLIS,
                " vibrator id=" + vibratorId);
    }
}
