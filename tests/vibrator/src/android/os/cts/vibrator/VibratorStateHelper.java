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

package android.os.cts.vibrator;

import static com.google.common.truth.Truth.assertWithMessage;

import android.os.Vibrator;

/** Helper class for assertions on the vibrator state. */
public final class VibratorStateHelper {

    /** Adds given state listener to vibrator and asserts on its initial state being idle. */
    public static void addListenerAndAssertInitialStateIdle(
            Vibrator vibrator, VibratorStateListener listener, long timeoutMs)
            throws InterruptedException {
        // Adding a listener to the Vibrator should trigger the callback once with the current state
        vibrator.addVibratorStateListener(listener);
        assertWithMessage("Expected vibrator initial state to be idle")
                .that(listener.getInitialStateWithTimeout(timeoutMs))
                .isFalse();
        assertWithMessage("Vibrator is vibrating after initial state, expected it to be idle")
                .that(vibrator.isVibrating())
                .isFalse();
    }

    /**
     * Triggers {@link Vibrator#cancel()} and asserts its state becomes idle.
     *
     * <p>Remove state listener from vibrator after assertions.
     */
    public static void removeListenerAndAssertStateIdle(
            Vibrator vibrator, VibratorStateListener listener, long timeoutMs)
            throws InterruptedException {
        if (vibrator.hasVibrator()) {
            assertWithMessage("Vibrator expected to turn off")
                    .that(listener.getCurrentStateWithTimeout(false, timeoutMs))
                    .isFalse();
        }
        assertWithMessage("Vibrator still vibrating, expected it to be idle")
                .that(vibrator.isVibrating())
                .isFalse();
        vibrator.removeVibratorStateListener(listener);
    }

    /** Asserts vibrator state changes to expected one within timeout. */
    public static void assertVibratorState(
            boolean expected, Vibrator vibrator, VibratorStateListener listener, long timeoutMs)
            throws InterruptedException {
        assertVibratorState(expected, vibrator, listener, timeoutMs, /* description= */ null);
    }

    /** Asserts vibrator state changes to expected one within timeout. */
    public static void assertVibratorState(
            boolean expected,
            Vibrator vibrator,
            VibratorStateListener listener,
            long timeoutMs,
            String description)
            throws InterruptedException {
        if (!vibrator.hasVibrator()) {
            return;
        }
        assertWithMessage(
                        "Vibrator expected to turn %s %s",
                        expected ? "on" : "off", description != null ? "for " + description : "")
                .that(listener.getCurrentStateWithTimeout(expected, timeoutMs))
                .isEqualTo(expected);
    }

    /** Asserts vibration starts then stops at least once within given timeout. */
    public static void assertStartsThenStopsVibrating(
            Vibrator vibrator, VibratorStateListener listener, long timeoutMs)
            throws InterruptedException {
        assertStartsThenStopsVibrating(vibrator, listener, timeoutMs, null);
    }

    /** Asserts vibration starts then stops at least once within given timeout. */
    public static void assertStartsThenStopsVibrating(
            Vibrator vibrator, VibratorStateListener listener, long timeoutMs, String description)
            throws InterruptedException {
        assertStartsThenStopsVibrating(0, vibrator, listener, timeoutMs, description);
    }

    /** Asserts vibration starts then stops at least one more time within given timeout. */
    public static void assertStartsThenStopsVibrating(
            int previousVibrationCount,
            Vibrator vibrator,
            VibratorStateListener listener,
            long timeoutMs,
            String description)
            throws InterruptedException {
        if (!vibrator.hasVibrator()) {
            return;
        }
        int expected = previousVibrationCount + 1;
        assertWithMessage(
                        "Expected vibration to start %s",
                        description != null ? "for " + description : "")
                .that(listener.getStartCountWithTimeout(expected, timeoutMs))
                .isAtLeast(expected);
        assertWithMessage(
                        "Expected vibration to stop %s",
                        description != null ? "for " + description : "")
                .that(listener.getStopCountWithTimeout(expected, timeoutMs))
                .isAtLeast(expected);
    }

    private VibratorStateHelper() {}
}
