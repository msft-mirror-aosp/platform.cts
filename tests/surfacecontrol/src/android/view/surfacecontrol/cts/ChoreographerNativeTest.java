/*
 * Copyright (C) 2016 The Android Open Source Project
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

package android.view.surfacecontrol.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Display;
import android.view.Display.Mode;
import android.view.Window;
import android.view.WindowManager;

import androidx.test.InstrumentationRegistry;
import androidx.test.annotation.UiThreadTest;
import androidx.test.filters.FlakyTest;
import androidx.test.filters.MediumTest;
import androidx.test.filters.SmallTest;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;
import com.android.compatibility.common.util.DisplayUtil;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.IntStream;

@FlakyTest
@RunWith(AndroidJUnit4.class)
public class ChoreographerNativeTest {
    private static final String TAG = "ChoreographerNativeTest";
    private long mChoreographerPtr;
    private boolean mLastCallbackMismatched = false;

    @Rule
    public ActivityTestRule<CtsActivity> mTestActivityRule =
            new ActivityTestRule<>(
                CtsActivity.class);

    @Rule
    public final AdoptShellPermissionsRule mShellPermissionsRule =
            new AdoptShellPermissionsRule(
                    InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                    Manifest.permission.OVERRIDE_DISPLAY_MODE_REQUESTS,
                    Manifest.permission.MODIFY_REFRESH_RATE_SWITCHING_TYPE);

    private static native long nativeGetChoreographer();

    private static native boolean nativePrepareChoreographerTests(long ptr, long[] refreshPeriods);

    private static native void nativeTestPostCallbackWithoutDelayEventuallyRunsCallbacks(long ptr);
    private static native void nativeTestPostCallbackWithDelayEventuallyRunsCallbacks(long ptr);
    private static native void nativeTestPostCallback64WithoutDelayEventuallyRunsCallbacks(
            long ptr);
    private static native void nativeTestPostCallback64WithDelayEventuallyRunsCallbacks(long ptr);
    private static native void nativeTestPostVsyncCallbackWithoutDelayEventuallyRunsCallbacks(
            long ptr);
    private static native void nativeTestFrameCallbackDataVsyncIdValid(
            long ptr);
    private static native void nativeTestFrameCallbackDataDeadlineInFuture(
            long ptr);
    private static native void nativeTestFrameCallbackDataExpectedPresentTimeInFuture(
            long ptr);
    private static native void nativeTestPostCallbackMixedWithoutDelayEventuallyRunsCallbacks(
            long ptr);
    private static native void nativeTestPostCallbackMixedWithDelayEventuallyRunsCallbacks(
            long ptr);
    private static native void nativeTestRefreshRateCallback(
            long ptr);
    private static native void nativeTestUnregisteringRefreshRateCallback(long ptr);
    private static native void nativeTestMultipleRefreshRateCallbacks(long ptr);
    private static native void nativeTestAttemptToAddRefreshRateCallbackTwiceDoesNotAddTwice(
            long ptr);
    private static native void nativeTestRefreshRateCallbackMixedWithFrameCallbacks(long ptr);
    private native void nativeTestRefreshRateCallbacksAreSyncedWithDisplayManager();

    private Context mContext;
    private DisplayManager mDisplayManager;
    private Display mDefaultDisplay;
    private long[] mSupportedPeriods;

    static {
        System.loadLibrary("ctssurfacecontrol_jni");
    }

    @UiThreadTest
    @Before
    public void setup() {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mDisplayManager = (DisplayManager) mContext.getSystemService(Context.DISPLAY_SERVICE);

        Optional<Display> defaultDisplayOpt = Arrays.stream(mDisplayManager.getDisplays())
                .filter(display -> display.getDisplayId() == Display.DEFAULT_DISPLAY)
                .findFirst();

        assertTrue(defaultDisplayOpt.isPresent());
        mDefaultDisplay = defaultDisplayOpt.get();

        float[] refreshRates = mDefaultDisplay.getSupportedRefreshRates();
        mSupportedPeriods =
                IntStream.range(0, refreshRates.length)
                        .mapToLong(
                                i -> (long) (Duration.ofSeconds(1).toNanos() / refreshRates[i]))
                        .distinct()
                        .toArray();

        mChoreographerPtr = nativeGetChoreographer();
        if (!nativePrepareChoreographerTests(
                mChoreographerPtr,
                mSupportedPeriods)) {
            fail("Failed to setup choreographer tests");
        }
    }

    @MediumTest
    @Test
    public void testPostVsyncCallbackWithoutDelayEventuallyRunsCallbacks() {
        nativeTestPostVsyncCallbackWithoutDelayEventuallyRunsCallbacks(mChoreographerPtr);
    }

    @MediumTest
    @Test
    public void testFrameCallbackDataVsyncIdValid() {
        nativeTestFrameCallbackDataVsyncIdValid(mChoreographerPtr);
    }

    @MediumTest
    @Test
    public void testFrameCallbackDataDeadlineInFuture() {
        nativeTestFrameCallbackDataDeadlineInFuture(mChoreographerPtr);
    }

    @MediumTest
    @Test
    public void testFrameCallbackDataExpectedPresentTimeInFuture() {
        nativeTestFrameCallbackDataExpectedPresentTimeInFuture(mChoreographerPtr);
    }

    @MediumTest
    @Test
    public void testPostCallback64WithoutDelayEventuallyRunsCallbacks() {
        nativeTestPostCallback64WithoutDelayEventuallyRunsCallbacks(mChoreographerPtr);
    }

    @MediumTest
    @Test
    public void testPostCallback64WithDelayEventuallyRunsCallbacks() {
        nativeTestPostCallback64WithDelayEventuallyRunsCallbacks(mChoreographerPtr);
    }

    @MediumTest
    @Test
    public void testPostCallbackWithoutDelayEventuallyRunsCallbacks() {
        nativeTestPostCallbackWithoutDelayEventuallyRunsCallbacks(mChoreographerPtr);
    }

    @SmallTest
    @Test
    public void testPostCallbackWithDelayEventuallyRunsCallbacks() {
        nativeTestPostCallbackWithDelayEventuallyRunsCallbacks(mChoreographerPtr);
    }

    @SmallTest
    @Test
    public void testPostCallbackMixedWithoutDelayEventuallyRunsCallbacks() {
        nativeTestPostCallbackMixedWithoutDelayEventuallyRunsCallbacks(mChoreographerPtr);
    }

    @SmallTest
    @Test
    public void testPostCallbackMixedWithDelayEventuallyRunsCallbacks() {
        nativeTestPostCallbackMixedWithDelayEventuallyRunsCallbacks(mChoreographerPtr);
    }

    @SmallTest
    @Test
    public void testRefreshRateCallback() {
        nativeTestRefreshRateCallback(mChoreographerPtr);
    }

    @SmallTest
    @Test
    public void testUnregisteringRefreshRateCallback() {
        nativeTestUnregisteringRefreshRateCallback(mChoreographerPtr);
    }

    @SmallTest
    @Test
    public void testMultipleRefreshRateCallbacks() {
        nativeTestMultipleRefreshRateCallbacks(mChoreographerPtr);
    }

    @SmallTest
    @Test
    public void testAttemptToAddRefreshRateCallbackTwiceDoesNotAddTwice() {
        nativeTestAttemptToAddRefreshRateCallbackTwiceDoesNotAddTwice(mChoreographerPtr);
    }

    @UiThreadTest
    @SmallTest
    @Test
    public void testRefreshRateCallbackMixedWithFrameCallbacks() {
        nativeTestRefreshRateCallbackMixedWithFrameCallbacks(mChoreographerPtr);
    }

    @SmallTest
    @Test
    public void testRefreshRateCallbacksIsSyncedWithDisplayManager() {
        // We can't test switching if there's only one refresh rate.
        assumeTrue(mSupportedPeriods.length >= 2);

        // For non-ARR devices, we need a seamless mode switch option.
        if (!mDefaultDisplay.hasArrSupport()) {
            assumeTrue(
                    "Device does not support seamless mode switching",
                    findModeForSeamlessSwitch().isPresent());
        }

        int initialMatchContentFrameRate = DisplayManager.SWITCHING_TYPE_NONE;
        try {
            // Set-up just for this particular test:
            // We must force the screen to be on for this window, and DisplayManager must be
            // configured to always respect the app-requested refresh rate.
            Handler handler = new Handler(Looper.getMainLooper());
            handler.post(() -> {
                mTestActivityRule.getActivity().getWindow().addFlags(
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            });
            mDisplayManager.setShouldAlwaysRespectAppRequestedMode(true);

            // Save initial DisplayManager refresh rate switching preference for cleanup.
            initialMatchContentFrameRate =
                    toSwitchingType(mDisplayManager.getMatchContentFrameRateUserPreference());

            // For ARR devices, we must allow render frame rate switching using
            // SWITCHING_TYPE_RENDER_FRAME_RATE_ONLY. For non-ARR devices, we use
            // SWITCHING_TYPE_NONE to prevent any system-initiated changes, as the test manually
            // triggers a full display mode switch by setting preferredDisplayModeId.
            int switchingType =
                    mDefaultDisplay.hasArrSupport()
                            ? DisplayManager.SWITCHING_TYPE_RENDER_FRAME_RATE_ONLY
                            : DisplayManager.SWITCHING_TYPE_NONE;
            mDisplayManager.setRefreshRateSwitchingType(switchingType);

            mLastCallbackMismatched = false;
            nativeTestRefreshRateCallbacksAreSyncedWithDisplayManager();
            assertFalse(
                    "Test finished in a mismatched state between Choreographer and Display",
                    mLastCallbackMismatched);
        } finally {
            // Clean up the DisplayManager settings.
            mDisplayManager.setRefreshRateSwitchingType(initialMatchContentFrameRate);
            mDisplayManager.setShouldAlwaysRespectAppRequestedMode(false);
        }
    }

    // Called by jni in a refresh rate callback.
    private void checkRefreshRateIsCurrentAndSwitch(int choreographerRate) {
        final int currentDisplayRate = Math.round(mDefaultDisplay.getRefreshRate());
        Log.d(
                TAG,
                "Current Display Refresh Rate: "
                        + currentDisplayRate
                        + ", Choreographer Refresh Rate: "
                        + choreographerRate);

        if (mDefaultDisplay.hasArrSupport()) {
            handleArrDeviceSwitch(choreographerRate, currentDisplayRate);
        } else {
            handleNonArrDeviceSwitch(choreographerRate, currentDisplayRate);
        }
    }

    private void handleArrDeviceSwitch(int choreographerRate, int currentDisplayRate) {
        if (currentDisplayRate != choreographerRate) {
            // If there's a mismatch, it means a transient system event may have happened in the
            // middle of the test (like touch boosts).
            // We flag this as a mismatch. If the system is healthy, a subsequent Choreographer
            // callback will eventually fire with the correct rate and clear this flag.
            Log.w(TAG, "Transient mismatch on ARR device. Skipping switch request.");
            mLastCallbackMismatched = true;
            return;
        }
        mLastCallbackMismatched = false;

        // For ARR devices, we change the preferred render frame rate using
        // WindowManager.LayoutParams.preferredRefreshRate.
        float currentRate = mDefaultDisplay.getRefreshRate();
        float targetRate = 0f;
        // Find a different supported refresh rate to switch to for testing purposes.
        for (float rate : mDefaultDisplay.getSupportedRefreshRates()) {
            if (Math.round(rate) != Math.round(currentRate)) {
                targetRate = rate;
                break;
            }
        }
        assertTrue("Could not find a frame rate to switch to.", targetRate > 0f);

        final float finalTargetRate = targetRate;
        Log.d(TAG, "Current refresh rate: " + currentRate + ", Switching to: " + finalTargetRate);
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(
                () -> {
                    Window window = mTestActivityRule.getActivity().getWindow();
                    WindowManager.LayoutParams params = window.getAttributes();
                    // Set the preferred refresh rate to trigger a render frame rate switch.
                    params.preferredRefreshRate = finalTargetRate;
                    window.setAttributes(params);
                });
    }

    private void handleNonArrDeviceSwitch(int choreographerRate, int currentDisplayRate) {
        assertEquals("Non-ARR devices must match exactly", currentDisplayRate, choreographerRate);

        // For non-ARR devices, we perform a traditional display mode switch to a different
        // seamless mode.
        Optional<Mode> maybeNextMode = findModeForSeamlessSwitch();
        assertTrue(maybeNextMode.isPresent());
        Mode mode = maybeNextMode.get();
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(
                () -> {
                    Window window = mTestActivityRule.getActivity().getWindow();
                    WindowManager.LayoutParams params = window.getAttributes();
                    // Set the preferred display mode ID to trigger a full display mode switch.
                    params.preferredDisplayModeId = mode.getModeId();
                    window.setAttributes(params);
                });
    }

    private Optional<Mode> findModeForSeamlessSwitch() {
        Mode activeMode = mDefaultDisplay.getMode();
        int refreshRate = Math.round(mDefaultDisplay.getRefreshRate());
        return Arrays.stream(mDefaultDisplay.getSupportedModes())
                .filter(mode -> DisplayUtil.isModeSwitchSeamless(activeMode, mode))
                .filter(mode ->  Math.round(mode.getRefreshRate()) != refreshRate)
                .findFirst();
    }

    private int toSwitchingType(int matchContentFrameRateUserPreference) {
        switch (matchContentFrameRateUserPreference) {
            case DisplayManager.MATCH_CONTENT_FRAMERATE_NEVER:
                return DisplayManager.SWITCHING_TYPE_NONE;
            case DisplayManager.MATCH_CONTENT_FRAMERATE_SEAMLESSS_ONLY:
                return DisplayManager.SWITCHING_TYPE_WITHIN_GROUPS;
            case DisplayManager.MATCH_CONTENT_FRAMERATE_ALWAYS:
                return DisplayManager.SWITCHING_TYPE_ACROSS_AND_WITHIN_GROUPS;
            default:
                return -1;
        }
    }
}
