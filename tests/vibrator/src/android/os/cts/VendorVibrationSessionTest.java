/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static android.os.vibrator.Flags.FLAG_VENDOR_VIBRATION_EFFECTS;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.os.CancellationSignal;
import android.os.SystemClock;
import android.os.VibrationAttributes;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.os.vibrator.VendorVibrationSession;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;
import com.android.compatibility.common.util.ApiTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Tests for {@link VendorVibrationSession} and callbacks.
 */
@RunWith(Parameterized.class)
@RequiresFlagsEnabled(FLAG_VENDOR_VIBRATION_EFFECTS)
public class VendorVibrationSessionTest {
    @Rule(order = 0)
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Rule(order = 1)
    public final AdoptShellPermissionsRule mAdoptShellPermissionsRule =
            new AdoptShellPermissionsRule(
                    InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                    android.Manifest.permission.START_VIBRATION_SESSIONS,
                    android.Manifest.permission.VIBRATE_VENDOR_EFFECTS,
                    android.Manifest.permission.WRITE_SETTINGS);

    /**
     *  Provides the vibrator accessed with the given vibrator ID, at the time of test running.
     *  A vibratorId of -1 indicates to use the system default vibrator.
     */
    private interface VibratorProvider {
        Vibrator getVibrator();
    }

    private static void addVibratorProvider(List<Object[]> data, VibratorProvider provider) {
        data.add(new Object[]{ provider });
    }

    @Parameterized.Parameters(name = "{0}")
    public static Iterable<Object[]> data() {
        // Test params are Name,Vibrator pairs. All vibrators on the system should conform to this
        // test.
        ArrayList<Object[]> data = new ArrayList<>();
        // These vibrators should be identical, but verify both APIs explicitly.
        addVibratorProvider(data,
                () -> InstrumentationRegistry.getInstrumentation().getContext()
                        .getSystemService(Vibrator.class));
        // VibratorManager also presents getDefaultVibrator, but in VibratorManagerTest
        // it is asserted that the Vibrator system service and getDefaultVibrator are
        // the same object, so we don't test it twice here.

        VibratorManager vibratorManager = InstrumentationRegistry.getInstrumentation().getContext()
                .getSystemService(VibratorManager.class);
        for (int vibratorId : vibratorManager.getVibratorIds()) {
            addVibratorProvider(data,
                    () -> InstrumentationRegistry.getInstrumentation().getContext()
                            .getSystemService(VibratorManager.class).getVibrator(vibratorId));
        }
        return data;
    }

    private static final long CALLBACK_TIMEOUT_MILLIS = 5000;
    private static final VibrationAttributes TOUCH_ATTRIBUTES =
            new VibrationAttributes.Builder()
                    .setUsage(VibrationAttributes.USAGE_TOUCH)
                    .build();
    private static final VibrationAttributes RINGTONE_ATTRIBUTES =
            new VibrationAttributes.Builder()
                    .setUsage(VibrationAttributes.USAGE_RINGTONE)
                    .build();

    private final Vibrator mVibrator;
    private final Executor mExecutor;
    private final List<TestCallback> mPendingCallbacks = new ArrayList<>();

    // vibratorLabel is used by the parameterized test infrastructure.
    public VendorVibrationSessionTest(VibratorProvider vibratorProvider) {
        mVibrator = vibratorProvider.getVibrator();
        mExecutor = Executors.newSingleThreadExecutor();
        assertThat(mVibrator).isNotNull();
    }

    @Before
    public void setUp() {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        Settings.System.putInt(context.getContentResolver(), Settings.System.VIBRATE_ON, 1);
    }

    @After
    public void cleanUp() throws Exception {
        for (TestCallback callback : mPendingCallbacks) {
            VendorVibrationSession session = callback.waitForSession(CALLBACK_TIMEOUT_MILLIS);
            if (session != null) {
                session.cancel();
            }
        }
    }

    @Test
    @ApiTest(apis = {
            "android.os.Vibrator#areVendorSessionsSupported",
            "android.os.Vibrator#startVendorSession",
            "android.os.vibrator.VendorVibrationSession.Callback#onStarted",
    })
    public void testVendorSessionsNotSupported_returnsUnsupportedStatus() throws Exception {
        assumeFalse(mVibrator.areVendorSessionsSupported());

        TestCallback callback = startSession(TOUCH_ATTRIBUTES);
        assertThat(callback.waitToBeFinished(CALLBACK_TIMEOUT_MILLIS)).isTrue();
        assertThat(callback.mStatus).isEqualTo(VendorVibrationSession.STATUS_UNSUPPORTED);
    }

    @Test
    @ApiTest(apis = {
            "android.os.Vibrator#areVendorSessionsSupported",
            "android.os.Vibrator#startVendorSession",
            "android.os.vibrator.VendorVibrationSession#close",
            "android.os.vibrator.VendorVibrationSession.Callback#onStarted",
            "android.os.vibrator.VendorVibrationSession.Callback#onFinishing",
            "android.os.vibrator.VendorVibrationSession.Callback#onFinished",
    })
    public void testStartThenEndSession() throws Exception {
        assumeTrue(mVibrator.areVendorSessionsSupported());

        TestCallback callback = startSession(TOUCH_ATTRIBUTES);
        VendorVibrationSession session = callback.waitForSession(CALLBACK_TIMEOUT_MILLIS);
        assertThat(session).isNotNull();
        assertThat(callback.isFinishing()).isFalse();
        assertThat(callback.isFinished()).isFalse();

        session.close();
        assertThat(callback.waitToBeFinished(CALLBACK_TIMEOUT_MILLIS)).isTrue();
        assertThat(callback.mStatus).isEqualTo(VendorVibrationSession.STATUS_SUCCESS);
    }

    @Test
    @ApiTest(apis = {
            "android.os.Vibrator#areVendorSessionsSupported",
            "android.os.Vibrator#startVendorSession",
            "android.os.vibrator.VendorVibrationSession#cancel",
            "android.os.vibrator.VendorVibrationSession.Callback#onStarted",
            "android.os.vibrator.VendorVibrationSession.Callback#onFinishing",
            "android.os.vibrator.VendorVibrationSession.Callback#onFinished",
    })
    public void testStartThenCancelSession() throws Exception {
        assumeTrue(mVibrator.areVendorSessionsSupported());

        TestCallback callback = startSession(TOUCH_ATTRIBUTES);
        VendorVibrationSession session = callback.waitForSession(CALLBACK_TIMEOUT_MILLIS);
        assertThat(session).isNotNull();
        assertThat(callback.isFinishing()).isFalse();
        assertThat(callback.isFinished()).isFalse();

        session.cancel();
        assertThat(callback.waitToBeFinished(CALLBACK_TIMEOUT_MILLIS)).isTrue();
        assertThat(callback.mStatus).isEqualTo(VendorVibrationSession.STATUS_CANCELED);
    }

    @Test
    @ApiTest(apis = {
            "android.os.Vibrator#areVendorSessionsSupported",
            "android.os.Vibrator#startVendorSession",
            "android.os.vibrator.VendorVibrationSession.Callback#onFinished",
    })
    public void testStartSessionThenSendCancelSignal() throws Exception {
        assumeTrue(mVibrator.areVendorSessionsSupported());

        CancellationSignal cancellationSignal = new CancellationSignal();
        TestCallback callback = startSession(TOUCH_ATTRIBUTES, cancellationSignal);
        cancellationSignal.cancel();

        assertThat(callback.waitToBeFinished(CALLBACK_TIMEOUT_MILLIS)).isTrue();
        assertThat(callback.mStatus).isEqualTo(VendorVibrationSession.STATUS_CANCELED);
    }

    @Test
    @ApiTest(apis = {
            "android.os.Vibrator#areVendorSessionsSupported",
            "android.os.Vibrator#startVendorSession",
            "android.os.vibrator.VendorVibrationSession#close",
            "android.os.vibrator.VendorVibrationSession.Callback#onStarted",
            "android.os.vibrator.VendorVibrationSession.Callback#onFinishing",
            "android.os.vibrator.VendorVibrationSession.Callback#onFinished",
    })
    public void testStartSecondSessionEndsFirst() throws Exception {
        assumeTrue(mVibrator.areVendorSessionsSupported());

        TestCallback firstCallback = startSession(TOUCH_ATTRIBUTES);
        assertThat(firstCallback.waitForSession(CALLBACK_TIMEOUT_MILLIS)).isNotNull();
        assertThat(firstCallback.isFinishing()).isFalse();
        assertThat(firstCallback.isFinished()).isFalse();

        TestCallback secondCallback = startSession(RINGTONE_ATTRIBUTES);
        assertThat(secondCallback.waitForSession(CALLBACK_TIMEOUT_MILLIS)).isNotNull();
        assertThat(secondCallback.isFinishing()).isFalse();
        assertThat(secondCallback.isFinished()).isFalse();

        assertThat(firstCallback.waitToBeFinished(CALLBACK_TIMEOUT_MILLIS)).isTrue();
        // First session started, so it gets notified when it starts finishing
        assertThat(firstCallback.isFinishing()).isTrue();
        assertThat(firstCallback.mStatus).isEqualTo(VendorVibrationSession.STATUS_CANCELED);
    }

    private TestCallback startSession(VibrationAttributes attrs) {
        return startSession(attrs, /* cancellationSignal= */ null);
    }

    private TestCallback startSession(VibrationAttributes attrs,
            CancellationSignal cancellationSignal) {
        TestCallback callback = new TestCallback();
        mVibrator.startVendorSession(attrs, "reason", cancellationSignal, mExecutor, callback);
        mPendingCallbacks.add(callback);
        return callback;
    }

    /** Test implementation for {@link VendorVibrationSession.Callback}. */
    private static final class TestCallback implements VendorVibrationSession.Callback {

        private VendorVibrationSession mSession;
        private boolean mNotifiedFinishing;
        private int mStatus = VendorVibrationSession.STATUS_UNKNOWN;

        @Override
        public synchronized void onStarted(@NonNull VendorVibrationSession session) {
            mSession = session;
            notifyAll();
        }

        @Override
        public synchronized void onFinishing() {
            mNotifiedFinishing = true;
            notifyAll();
        }

        @Override
        public synchronized void onFinished(int status) {
            mStatus = status;
            notifyAll();
        }

        synchronized boolean isFinishing() {
            return mNotifiedFinishing;
        }

        synchronized boolean isFinished() {
            return mStatus != VendorVibrationSession.STATUS_UNKNOWN;
        }

        synchronized VendorVibrationSession waitForSession(long timeoutMs)
                throws InterruptedException {
            long now = SystemClock.elapsedRealtime();
            long deadline = now + timeoutMs;
            while (true) {
                if (mSession != null) {
                    return mSession;
                }
                if (isFinished() || now >= deadline) {
                    return null;
                }
                wait(deadline - now);
                now = SystemClock.elapsedRealtime();
            }
        }

        synchronized boolean waitToBeFinished(long timeoutMs) throws InterruptedException {
            long now = SystemClock.elapsedRealtime();
            long deadline = now + timeoutMs;
            while (true) {
                if (isFinished()) {
                    return true;
                }
                if (now >= deadline) {
                    return false;
                }
                wait(deadline - now);
                now = SystemClock.elapsedRealtime();
            }
        }
    }
}
