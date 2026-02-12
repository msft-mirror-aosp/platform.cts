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
 * limitations under the License
 */

package android.server.wm.keyguard;

import static android.Manifest.permission.SUBSCRIBE_TO_KEYGUARD_LOCKED_STATE;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD;
import static android.app.WindowConfiguration.WINDOWING_MODE_PINNED;
import static android.server.wm.CliIntentExtra.extraString;
import static android.server.wm.MockImeHelper.createManagedMockImeSession;
import static android.server.wm.UiDeviceUtils.pressBackButton;
import static android.server.wm.UiDeviceUtils.pressMenuButton;
import static android.server.wm.WindowManagerStateHelper.focusedActivity;
import static android.server.wm.app.Components.DISMISS_KEYGUARD_ACTIVITY;
import static android.server.wm.app.Components.DISMISS_KEYGUARD_METHOD_ACTIVITY;
import static android.server.wm.app.Components.PIP_ACTIVITY;
import static android.server.wm.app.Components.PipActivity.ACTION_ENTER_PIP;
import static android.server.wm.app.Components.PipActivity.EXTRA_DISMISS_KEYGUARD;
import static android.server.wm.app.Components.PipActivity.EXTRA_ENTER_PIP;
import static android.server.wm.app.Components.PipActivity.EXTRA_SHOW_OVER_KEYGUARD;
import static android.server.wm.app.Components.SHOW_WHEN_LOCKED_ACTIVITY;
import static android.server.wm.app.Components.SHOW_WHEN_LOCKED_ATTR_IME_ACTIVITY;
import static android.server.wm.app.Components.TURN_SCREEN_ON_ATTR_DISMISS_KEYGUARD_ACTIVITY;
import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.WindowInsets.Type.ime;
import static android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE;

import static androidx.test.InstrumentationRegistry.getInstrumentation;

import static com.android.cts.mockime.ImeEventStreamTestUtils.expectEvent;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.app.Activity;
import android.app.KeyguardManager;
import android.app.KeyguardManager.DeviceLockedStateListener;
import android.os.Bundle;
import android.os.SystemClock;
import android.platform.test.annotations.Presubmit;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.server.wm.ActivityManagerTestBase;
import android.server.wm.KeyguardTestBase;
import android.server.wm.LockScreenSession;
import android.server.wm.app.Components;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.FlakyTest;
import androidx.test.uiautomator.UiDevice;

import com.android.compatibility.common.util.CtsTouchUtils;
import com.android.compatibility.common.util.DeviceReportLog;
import com.android.compatibility.common.util.FeatureUtil;
import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;
import com.android.cts.mockime.ImeEventStream;
import com.android.cts.mockime.MockImeSession;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

/**
 * Build/Install/Run:
 *     atest CtsWindowManagerDeviceKeyguard:KeyguardLockedTests
 */
@Presubmit
@android.server.wm.annotation.Group2
public class KeyguardLockedTests extends KeyguardTestBase {

    private static final String REPORT_LOG_NAME = "CtsWindowManagerDeviceKeyguard";
    private static final String STREAM_NAME_KEYGUARD_STAYS_LOCKED =
            "test_keyguard_stays_locked_after_wrong_credentials";
    private static final String RECOMMENDED_TIMEOUT_ENFORCED_TAG = "recommended_timeout_enforced";
    private static final String RECOMMENDED_TIMEOUT_ENFORCED_MS_TAG =
            "recommended_timeout_enforced_ms";
    private static final String RECOMMENDED_TIMEOUT_ENFORCED_FIFTH_GUESS_MS_TAG =
            "recommended_timeout_enforced_fifth_guess_ms";
    private static final String RECOMMENDED_TIMEOUT_NOT_ENFORCED_TAG =
            "recommended_timeout_not_enforced";
    private static final String RECOMMENDED_TIMEOUT_NOT_ENFORCED_MS_TAG =
            "recommended_timeout_not_enforced_ms";
    private static final String RECOMMENDED_TIMEOUT_NOT_ENFORCED_FIFTH_GUESS_MS_TAG =
            "recommended_timeout_not_enforced_fifth_guess_ms";

    private static final String TAG = "KeyguardLockedTests";
    private static final long TIMEOUT_IME = TimeUnit.SECONDS.toMillis(5);

    private static final int EXPECTED_MINIMUM_LOCKOUT_AFTER_5_WRONG_GUESSES_SECONDS = 60;

    private final CtsTouchUtils mCtsTouchUtils =
            new CtsTouchUtils(InstrumentationRegistry.getTargetContext());

    private UiDevice mUiDevice;
    private LockScreenSession mLockScreenSession;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        assumeTrue(supportsSecureLock());
        assumeRunNotOnVisibleBackgroundNonProfileUser(
                "Keyguard not supported for visible background users");
        mUiDevice = UiDevice.getInstance(getInstrumentation());
    }

    @After
    public void tearDown() {
        if (mLockScreenSession != null) {
            mLockScreenSession.close();
        }
        Components.forceStopPackage();
    }

    @Test
    @RequiresFlagsDisabled(android.app.Flags.FLAG_DEVICE_UNLOCK_LISTENER)
    public void testLockAndUnlock() {
        mLockScreenSession = createManagedLockScreenSession();
        mLockScreenSession.setLockCredential().gotoKeyguard();

        assertTrue(mKeyguardManager.isKeyguardLocked());
        assertTrue(mKeyguardManager.isDeviceLocked());
        assertTrue(mKeyguardManager.isDeviceSecure());
        assertTrue(mKeyguardManager.isKeyguardSecure());
        mWmState.assertKeyguardShowingAndNotOccluded();

        mLockScreenSession.unlock();

        mWmState.waitAndAssertKeyguardGone();
        assertFalse(mKeyguardManager.isDeviceLocked());
        assertFalse(mKeyguardManager.isKeyguardLocked());
    }

    @Test
    public void testDisableKeyguard_thenSettingCredential_reenablesKeyguard_b119322269() {
        final KeyguardManager.KeyguardLock keyguardLock = mContext.getSystemService(
                KeyguardManager.class).newKeyguardLock("KeyguardLockedTests");

        mLockScreenSession = new LockScreenSession(mInstrumentation, mWmState);
        try {
            mLockScreenSession.gotoKeyguard();
            keyguardLock.disableKeyguard();

            mLockScreenSession.setLockCredential();
            mLockScreenSession.gotoKeyguard();

            mWmState.waitForKeyguardShowingAndNotOccluded();
            mWmState.assertKeyguardShowingAndNotOccluded();
            assertTrue(mKeyguardManager.isKeyguardLocked());
            assertTrue(mKeyguardManager.isDeviceLocked());
            assertTrue(mKeyguardManager.isDeviceSecure());
            assertTrue(mKeyguardManager.isKeyguardSecure());
        } finally {
            keyguardLock.reenableKeyguard();
        }
    }

    @Test
    public void testDismissKeyguard() {
        mLockScreenSession = createManagedLockScreenSession();
        mLockScreenSession.setLockCredential().gotoKeyguard();

        mWmState.assertKeyguardShowingAndNotOccluded();
        launchActivity(DISMISS_KEYGUARD_ACTIVITY);
        mLockScreenSession.enterLockCredentialAndConfirm();

        mWmState.waitAndAssertKeyguardGone();
        mWmState.computeState(DISMISS_KEYGUARD_ACTIVITY);
        mWmState.assertVisibility(DISMISS_KEYGUARD_ACTIVITY, true);
    }

    @Test
    @RequiresFlagsEnabled(android.app.Flags.FLAG_DEVICE_UNLOCK_LISTENER)
    public void testLockAndUnlockWithStateListener() {
        mInstrumentation.getUiAutomation().adoptShellPermissionIdentity(
                SUBSCRIBE_TO_KEYGUARD_LOCKED_STATE);
        mLockScreenSession = createManagedLockScreenSession();

        final DeviceLockedStateListener listener = mock(DeviceLockedStateListener.class);
        mKeyguardManager.addDeviceLockedStateListener(mContext.getMainExecutor(), listener);
        try {
            mLockScreenSession.setLockCredential().gotoKeyguard();
            assertTrue(mKeyguardManager.isKeyguardLocked());
            assertTrue(mKeyguardManager.isDeviceLocked());
            assertTrue(mKeyguardManager.isDeviceSecure());
            assertTrue(mKeyguardManager.isKeyguardSecure());
            mWmState.assertKeyguardShowingAndNotOccluded();
            verify(listener, times(1)).onDeviceLockedStateChanged(true);

            mLockScreenSession.unlock();

            mWmState.waitAndAssertKeyguardGone();
            PollingCheck.waitFor(
                    () -> !mKeyguardManager.isKeyguardLocked(),
                    "keyguard locked state must pass to listener");
            assertFalse(mKeyguardManager.isDeviceLocked());
            verify(listener, times(1)).onDeviceLockedStateChanged(false);
        } finally {
            mInstrumentation.getUiAutomation().adoptShellPermissionIdentity(
                    SUBSCRIBE_TO_KEYGUARD_LOCKED_STATE);
            mKeyguardManager.removeDeviceLockedStateListener(listener);
            mInstrumentation.getUiAutomation().dropShellPermissionIdentity();
        }
    }

    /**
     * Checks whether the device locks out for around 60 seconds within the first 5 unique wrong
     * guesses of a credential.
     *
     * <p>Validates compliance with CDD STRONGLY_RECOMMENDED requirement [C-SR-1] of 9.11. Keys and
     * Credentials.
     *
     * <p>As the requirement is STRONGLY_RECOMMENDED, the test is skipped if the device does not
     * lock out for the expected duration. However, the requirement is planned to be become MUST in
     * a future release of Android. The test will be updated to fail at that point.
     *
     * <p>Asserts the following as pre-requisites. These will fail the test if not met.
     *
     * <ol>
     *   <li>The keyguard locks after setting a credential and powering off the screen
     *   <li>The keyguard unlocks with the configured credential
     * </ol>
     *
     * <p>Enters 5 wrong credentials in succession, and checks for device lockout. Metrics are
     * logged in both the succeeded and skipped cases. See details below.
     *
     * <ul>
     *   <li>During the period of 60 seconds starting from when the first wrong guess was made:
     *       <ol>
     *         <li>Enter the correct configured credential
     *         <li>Wait for any device animations
     *         <li>Check if the device is still locked
     *       </ol>
     * </ul>
     *
     * <p>If the device does lock out for the expected 60 seconds, the {@link DeviceReportLog} is
     * populated with the following report:
     *
     * <pre>
     * {
     *   "test_keyguard_stays_locked_after_wrong_credentials": {
     *     "recommended_timeout_enforced": true,
     *     "recommended_timeout_enforced_ms": 61110,
     *     "recommended_timeout_enforced_fifth_guess_ms": 56320
     *   }
     * }
     * </pre>
     *
     * <p>In the case of a failure, the {@link DeviceReportLog} receives the following report:
     *
     * <pre>
     * {
     *   "test_keyguard_stays_locked_after_wrong_credentials": {
     *     "recommended_timeout_not_enforced": true,
     *     "recommended_timeout_not_enforced_ms": 48194,
     *     "recommended_timeout_not_enforced_fifth_guess_ms": 43367
     *   }
     * }
     * </pre>
     */
    @Test
    public void testKeyguardStaysLocked_afterWrongCredentials() {
        mLockScreenSession =
                createManagedLockScreenSession()
                        .setLockCredential()
                        .gotoKeyguard()
                        .requestKeyguardDismissal();
        assertTrue("Keyguard is not secure", mKeyguardManager.isKeyguardSecure());
        assertKeyguardLocked("after setting credential");

        // Ensure that the device is unlockable with the credential so that lockouts are because of
        // repeated wrong credentials, not simply that the device cannot unlock with any credential.
        mLockScreenSession.enterLockCredentialAndConfirm();
        mWmState.waitAndAssertKeyguardGone();
        assertFalse(
                "Keyguard is not unlocked after using configured credential",
                mKeyguardManager.isKeyguardLocked());

        mLockScreenSession.gotoKeyguard();
        // Show bouncer by pressing the Menu button
        pressMenuButton();
        mUiDevice.waitForIdle();
        assertKeyguardLocked("after turning screen off and waking up");

        long startTime = -1;
        for (int i = 0; i < 5; i++) {
            if (i > 0) {
                SystemClock.sleep(1000);
            }
            mLockScreenSession.enterWrongCredentialAndConfirm(i);
            if (startTime == -1) {
                startTime = SystemClock.elapsedRealtime();
            }
            assertKeyguardLocked("after entering a wrong credential with index " + i);
        }
        long timeAfterFifthGuess = SystemClock.elapsedRealtime();

        long attemptFinishedTime = timeAfterFifthGuess;
        for (long attemptTriggerTime = SystemClock.elapsedRealtime();
                attemptTriggerTime - startTime
                        < EXPECTED_MINIMUM_LOCKOUT_AFTER_5_WRONG_GUESSES_SECONDS * 1000;
                attemptTriggerTime = SystemClock.elapsedRealtime()) {
            Log.i(
                    TAG,
                    "Trying correct credential "
                            + (attemptTriggerTime - startTime)
                            + "ms after first wrong guess, "
                            + (attemptTriggerTime - timeAfterFifthGuess)
                            + "ms after fifth wrong guess.");
            mLockScreenSession.unlock();
            // The above action may wait before entering credentials, so record right after the
            // guess to avoid false negatives near the pass/fail boundary.
            attemptFinishedTime = SystemClock.elapsedRealtime();
            long msSinceFirstGuess = attemptFinishedTime - startTime;
            long msSinceFifthGuess = attemptFinishedTime - timeAfterFifthGuess;
            Log.i(
                    TAG,
                    "Correct credential attempt finished at "
                            + msSinceFirstGuess
                            + "ms after first wrong guess, "
                            + msSinceFifthGuess
                            + "ms after fifth wrong guess.");
            mUiDevice.waitForIdle();
            boolean keyguardLocked = mKeyguardManager.isKeyguardLocked();
            if (!keyguardLocked) {
                reportTimeoutNotEnforced(msSinceFirstGuess, msSinceFifthGuess);
                Log.w(
                        TAG,
                        "Device was unlocked before recommended "
                                + EXPECTED_MINIMUM_LOCKOUT_AFTER_5_WRONG_GUESSES_SECONDS
                                + "s lockout period!");
                assumeTrue(keyguardLocked);
                return;
            }
        }
        long msSinceFirstGuess = attemptFinishedTime - startTime;
        long msSinceFifthGuess = attemptFinishedTime - timeAfterFifthGuess;
        Log.i(
                TAG,
                "Device was locked for "
                        + msSinceFirstGuess
                        + "ms after first wrong guess, "
                        + msSinceFifthGuess
                        + "ms after fifth wrong guess.");
        reportTimeoutEnforced(msSinceFirstGuess, msSinceFifthGuess);
    }

    private void reportTimeoutEnforced(long msSinceFirstGuess, long msSinceFifthGuess) {
        DeviceReportLog reportLog =
                new DeviceReportLog(REPORT_LOG_NAME, STREAM_NAME_KEYGUARD_STAYS_LOCKED);
        reportLog.addValue(
                RECOMMENDED_TIMEOUT_ENFORCED_TAG, true, ResultType.NEUTRAL, ResultUnit.NONE);
        reportLog.addValue(
                RECOMMENDED_TIMEOUT_ENFORCED_MS_TAG,
                msSinceFirstGuess,
                ResultType.NEUTRAL,
                ResultUnit.MS);
        reportLog.addValue(
                RECOMMENDED_TIMEOUT_ENFORCED_FIFTH_GUESS_MS_TAG,
                msSinceFifthGuess,
                ResultType.NEUTRAL,
                ResultUnit.MS);
        reportLog.submit(mInstrumentation);
    }

    private void reportTimeoutNotEnforced(long msSinceFirstGuess, long msSinceFifthGuess) {
        DeviceReportLog reportLog =
                new DeviceReportLog(REPORT_LOG_NAME, STREAM_NAME_KEYGUARD_STAYS_LOCKED);
        reportLog.addValue(
                RECOMMENDED_TIMEOUT_NOT_ENFORCED_TAG,
                true,
                ResultType.WARNING, // Presence of this value indicates a non-fatal problem
                ResultUnit.NONE);
        reportLog.addValue(
                RECOMMENDED_TIMEOUT_NOT_ENFORCED_MS_TAG,
                msSinceFirstGuess,
                ResultType.WARNING, // Presence of this value indicates a non-fatal problem
                ResultUnit.MS);
        reportLog.addValue(
                RECOMMENDED_TIMEOUT_NOT_ENFORCED_FIFTH_GUESS_MS_TAG,
                msSinceFifthGuess,
                ResultType.WARNING, // Presence of this value indicates a non-fatal problem
                ResultUnit.MS);
        reportLog.submit(mInstrumentation);
    }

    private void assertKeyguardLocked(String suffix) {
        assertTrue("Keyguard is not locked " + suffix, mKeyguardManager.isKeyguardLocked());
    }

    @Test
    public void testDismissKeyguard_whileOccluded() {
        mLockScreenSession = createManagedLockScreenSession();
        mLockScreenSession.setLockCredential().gotoKeyguard();

        mWmState.assertKeyguardShowingAndNotOccluded();
        launchActivity(SHOW_WHEN_LOCKED_ACTIVITY);
        mWmState.computeState(SHOW_WHEN_LOCKED_ACTIVITY);
        mWmState.assertVisibility(SHOW_WHEN_LOCKED_ACTIVITY, true);

        launchActivity(DISMISS_KEYGUARD_ACTIVITY);
        mLockScreenSession.enterLockCredentialAndConfirm();
        mWmState.waitAndAssertKeyguardGone();
        mWmState.computeState(DISMISS_KEYGUARD_ACTIVITY);

        final boolean isDismissTranslucent = mWmState
                .isActivityTranslucent(DISMISS_KEYGUARD_ACTIVITY);
        mWmState.assertVisibility(DISMISS_KEYGUARD_ACTIVITY, true);
        mWmState.assertVisibility(SHOW_WHEN_LOCKED_ACTIVITY, isDismissTranslucent);
    }

    @Test
    public void testDismissKeyguard_fromShowWhenLocked_notAllowed() {
        mLockScreenSession = createManagedLockScreenSession();
        mLockScreenSession.setLockCredential().gotoKeyguard();

        mWmState.assertKeyguardShowingAndNotOccluded();
        launchActivity(SHOW_WHEN_LOCKED_ACTIVITY);
        mWmState.computeState(SHOW_WHEN_LOCKED_ACTIVITY);
        mWmState.assertVisibility(SHOW_WHEN_LOCKED_ACTIVITY, true);
        mBroadcastActionTrigger.dismissKeyguardByFlag();
        mLockScreenSession.enterLockCredentialAndConfirm();

        // Make sure we stay on Keyguard.
        mWmState.assertKeyguardShowingAndOccluded();
        mWmState.assertVisibility(SHOW_WHEN_LOCKED_ACTIVITY, true);
    }

    @Test
    public void testDismissKeyguardIfInsecure_notAllowed() {
        mLockScreenSession = createManagedLockScreenSession();
        mLockScreenSession.setLockCredential().gotoKeyguard();

        mWmState.assertKeyguardShowingAndNotOccluded();
        launchActivityWithDismissKeyguardIfInsecure(SHOW_WHEN_LOCKED_ACTIVITY);
        mWmState.computeState(SHOW_WHEN_LOCKED_ACTIVITY);
        mWmState.assertVisibility(SHOW_WHEN_LOCKED_ACTIVITY, true);

        // Make sure we stay on Keyguard.
        mWmState.assertKeyguardShowingAndOccluded();
    }

    @Test
    public void testDismissKeyguardActivity_method() {
        mLockScreenSession = createManagedLockScreenSession();
        mLockScreenSession.setLockCredential();
        separateTestJournal();

        mLockScreenSession.gotoKeyguard();
        mWmState.computeState();
        assertTrue(mWmState.getKeyguardControllerState().isKeyguardShowing());

        launchActivity(DISMISS_KEYGUARD_METHOD_ACTIVITY);
        mLockScreenSession.enterLockCredentialAndConfirm();
        mWmState.waitForKeyguardGone();
        mWmState.computeState(DISMISS_KEYGUARD_METHOD_ACTIVITY);
        mWmState.assertVisibility(DISMISS_KEYGUARD_METHOD_ACTIVITY, true);
        assertFalse(mWmState.getKeyguardControllerState().isKeyguardShowing());
        assertOnDismissSucceeded(DISMISS_KEYGUARD_METHOD_ACTIVITY);
    }

    @Test
    public void testDismissKeyguardActivity_method_cancelled() {
        // Pressing the back button does not cancel Keyguard in AAOS or XR.
        assumeFalse(isCar());
        assumeFalse(FeatureUtil.isXrHeadset());

        mLockScreenSession = createManagedLockScreenSession();
        mLockScreenSession.setLockCredential();
        separateTestJournal();

        mLockScreenSession.gotoKeyguard();
        mWmState.computeState();
        assertTrue(mWmState.getKeyguardControllerState().isKeyguardShowing());

        launchActivity(DISMISS_KEYGUARD_METHOD_ACTIVITY);
        pressBackButton();
        assertOnDismissCancelled(DISMISS_KEYGUARD_METHOD_ACTIVITY);
        mWmState.computeState();
        mWmState.assertVisibility(DISMISS_KEYGUARD_METHOD_ACTIVITY, false);
        assertTrue(mWmState.getKeyguardControllerState().isKeyguardShowing());
    }

    @Test
    public void testDismissKeyguardAttrActivity_method_turnScreenOn_withSecureKeyguard() {
        mLockScreenSession = createManagedLockScreenSession();
        mLockScreenSession.setLockCredential().sleepDevice();
        mWmState.computeState();
        assertTrue(mWmState.getKeyguardControllerState().isKeyguardShowing());

        launchActivity(TURN_SCREEN_ON_ATTR_DISMISS_KEYGUARD_ACTIVITY);
        mWmState.waitForKeyguardShowingAndNotOccluded();
        mWmState.assertVisibility(TURN_SCREEN_ON_ATTR_DISMISS_KEYGUARD_ACTIVITY, false);
        assertTrue(mWmState.getKeyguardControllerState().isKeyguardShowing());
        assertTrue(ActivityManagerTestBase.isDisplayOn(DEFAULT_DISPLAY));
    }

    @Test
    public void testShowWhenLockedActivity_removeAttr_hideImmediately() {
        mLockScreenSession = createManagedLockScreenSession();
        mLockScreenSession.gotoKeyguard();

        // Start on Keyguard.
        mWmState.waitForKeyguardShowingAndNotOccluded();
        mWmState.waitAndAssertNonActivityWindowFocused();

        // Add Activity with showWhenLocked="true".
        final var activity = createManagedTestActivitySession(ShowWhenLockedActivity.class)
                .launchTestActivityOnDisplaySync(ShowWhenLockedActivity.class, DEFAULT_DISPLAY)
                .getActivity();
        mWmState.waitForKeyguardShowingAndOccluded();
        mWmState.waitAndAssert(focusedActivity(activity.getComponentName()),
                "Activity to be focused");

        // Remove showWhenLocked attribute.
        activity.setShowWhenLocked(false);

        // Activity Window should be removed within well under 5 seconds.
        mWmState.waitAndAssertNonActivityWindowFocused();
    }

    @Test
    public void testEnterPipOverKeyguard() {
        assumeTrue(supportsPip());

        mLockScreenSession = createManagedLockScreenSession();
        mLockScreenSession.setLockCredential();

        // Show the PiP activity in fullscreen.
        launchActivity(PIP_ACTIVITY, extraString(EXTRA_SHOW_OVER_KEYGUARD, "true"));

        // Lock the screen and ensure that the PiP activity showing over the LockScreen.
        mLockScreenSession.gotoKeyguard(PIP_ACTIVITY);
        mWmState.waitForKeyguardShowingAndOccluded();
        mWmState.assertKeyguardShowingAndOccluded();

        // Request that the PiP activity enter picture-in-picture mode (ensure it does not).
        mBroadcastActionTrigger.doAction(ACTION_ENTER_PIP);
        waitForEnterPip(PIP_ACTIVITY);
        mWmState.assertDoesNotContainStack("Must not contain pinned stack.",
                WINDOWING_MODE_PINNED, ACTIVITY_TYPE_STANDARD);

        // Enter the credentials and ensure that the activity actually entered picture-in-picture.
        mLockScreenSession.enterLockCredentialAndConfirm();
        mWmState.waitAndAssertKeyguardGone();
        waitForEnterPip(PIP_ACTIVITY);
        mWmState.assertContainsStack("Must contain pinned stack.", WINDOWING_MODE_PINNED,
                ACTIVITY_TYPE_STANDARD);
    }

    @Test
    public void testShowWhenLockedActivityAndPipActivity() {
        assumeTrue(supportsPip());

        mLockScreenSession = createManagedLockScreenSession();
        mLockScreenSession.setLockCredential();

        // Show an activity in PIP.
        launchActivity(PIP_ACTIVITY, extraString(EXTRA_ENTER_PIP, "true"));
        waitForEnterPip(PIP_ACTIVITY);
        mWmState.assertContainsStack("Must contain pinned stack.", WINDOWING_MODE_PINNED,
                ACTIVITY_TYPE_STANDARD);
        mWmState.assertVisibility(PIP_ACTIVITY, true);

        // Show an activity that will keep above the keyguard.
        launchActivity(SHOW_WHEN_LOCKED_ACTIVITY);
        mWmState.computeState(SHOW_WHEN_LOCKED_ACTIVITY);
        mWmState.assertVisibility(SHOW_WHEN_LOCKED_ACTIVITY, true);

        // Lock the screen and ensure that the fullscreen activity showing over the lockscreen
        // is visible, but not the PiP activity.
        mLockScreenSession.gotoKeyguard(SHOW_WHEN_LOCKED_ACTIVITY);
        mWmState.computeState();
        mWmState.assertKeyguardShowingAndOccluded();
        mWmState.assertVisibility(SHOW_WHEN_LOCKED_ACTIVITY, true);
        mWmState.assertVisibility(PIP_ACTIVITY, false);
    }

    @Test
    public void testShowWhenLockedPipActivity() {
        assumeTrue(supportsPip());

        mLockScreenSession = createManagedLockScreenSession();
        mLockScreenSession.setLockCredential();

        // Show an activity in PIP.
        launchActivity(PIP_ACTIVITY, extraString(EXTRA_ENTER_PIP, "true"),
                extraString(EXTRA_SHOW_OVER_KEYGUARD, "true"));
        waitForEnterPip(PIP_ACTIVITY);
        mWmState.assertContainsStack("Must contain pinned stack.", WINDOWING_MODE_PINNED,
                ACTIVITY_TYPE_STANDARD);
        mWmState.assertVisibility(PIP_ACTIVITY, true);

        // Lock the screen and ensure the PiP activity is not visible on the lockscreen even
        // though it's marked as showing over the lockscreen itself.
        mLockScreenSession.gotoKeyguard();
        mWmState.assertKeyguardShowingAndNotOccluded();
        mWmState.assertVisibility(PIP_ACTIVITY, false);
    }

    @Test
    public void testDismissKeyguardPipActivity() {
        assumeTrue(supportsPip());

        mLockScreenSession = createManagedLockScreenSession();
        // Show an activity in PIP.
        launchActivity(PIP_ACTIVITY, extraString(EXTRA_ENTER_PIP, "true"),
                extraString(EXTRA_DISMISS_KEYGUARD, "true"));
        waitForEnterPip(PIP_ACTIVITY);
        mWmState.assertContainsStack("Must contain pinned stack.", WINDOWING_MODE_PINNED,
                ACTIVITY_TYPE_STANDARD);
        mWmState.assertVisibility(PIP_ACTIVITY, true);

        // Lock the screen and ensure the PiP activity is not visible on the lockscreen even
        // though it's marked as dismiss keyguard.
        mLockScreenSession.gotoKeyguard();
        mWmState.computeState();
        mWmState.assertKeyguardShowingAndNotOccluded();
        mWmState.assertVisibility(PIP_ACTIVITY, false);
    }

    @Test
    public void testDismissKeyguardPipActivityWhenKeyguardOccluded() {
        assumeTrue(supportsPip());

        mLockScreenSession = createManagedLockScreenSession();
        // Start a show-when-lock activity
        launchActivity(SHOW_WHEN_LOCKED_ACTIVITY);
        mWmState.computeState(SHOW_WHEN_LOCKED_ACTIVITY);
        mWmState.assertVisibility(SHOW_WHEN_LOCKED_ACTIVITY, true);

        // Start another activit into PIP
        launchActivity(
                PIP_ACTIVITY,
                extraString(EXTRA_ENTER_PIP, "true"),
                extraString(EXTRA_DISMISS_KEYGUARD, "true"));
        waitForEnterPip(PIP_ACTIVITY);
        mWmState.assertContainsStack(
                "Must contain pinned stack.", WINDOWING_MODE_PINNED, ACTIVITY_TYPE_STANDARD);
        mWmState.assertVisibility(PIP_ACTIVITY, true);
        mWmState.assertVisibility(SHOW_WHEN_LOCKED_ACTIVITY, true);

        // Lock the screen and ensure the PiP activity is not visible on the lockscreen
        // ShowWhenLockActivity should still be visible and keyguard is occluded
        mLockScreenSession.gotoKeyguard(SHOW_WHEN_LOCKED_ACTIVITY);
        mWmState.computeState();
        mWmState.assertKeyguardShowingAndOccluded();
        mWmState.assertVisibility(PIP_ACTIVITY, false);
        mWmState.assertVisibility(SHOW_WHEN_LOCKED_ACTIVITY, true);
    }

    @Test
    public void testShowWhenLockedAttrImeActivityAndShowSoftInput() throws Exception {
        assumeTrue(MSG_NO_MOCK_IME, supportsInstallableIme());

        mLockScreenSession = createManagedLockScreenSession();
        final MockImeSession mockImeSession = createManagedMockImeSession(this);

        mLockScreenSession.setLockCredential().gotoKeyguard();
        mWmState.assertKeyguardShowingAndNotOccluded();
        launchActivity(SHOW_WHEN_LOCKED_ATTR_IME_ACTIVITY);
        mWmState.computeState(SHOW_WHEN_LOCKED_ATTR_IME_ACTIVITY);
        mWmState.assertVisibility(SHOW_WHEN_LOCKED_ATTR_IME_ACTIVITY, true);

        // Make sure the activity has been called showSoftInput & IME window is visible.
        final ImeEventStream stream = mockImeSession.openEventStream();
        expectEvent(stream, event -> "showSoftInput".equals(event.getEventName()),
                TIMEOUT_IME);
        // Assert the IME is shown on the expected display.
        mWmState.waitAndAssertImeWindowShownOnDisplay(DEFAULT_DISPLAY);
    }

    @Test
    public void testShowWhenLockedImeActivityAndShowSoftInput() throws Exception {
        assumeTrue(MSG_NO_MOCK_IME, supportsInstallableIme());

        mLockScreenSession = createManagedLockScreenSession();
        final MockImeSession mockImeSession = createManagedMockImeSession(this);
        final TestActivitySession<ShowWhenLockedImeActivity> imeTestActivitySession =
                createManagedTestActivitySession();

        mLockScreenSession.setLockCredential().gotoKeyguard();
        mWmState.assertKeyguardShowingAndNotOccluded();
        imeTestActivitySession.launchTestActivityOnDisplaySync(ShowWhenLockedImeActivity.class,
                DEFAULT_DISPLAY);

        // Make sure the activity has been called showSoftInput & IME window is visible.
        final ImeEventStream stream = mockImeSession.openEventStream();
        expectEvent(stream, event -> "showSoftInput".equals(event.getEventName()),
                TIMEOUT_IME);
        // Assert the IME is shown on the expected display.
        mWmState.waitAndAssertImeWindowShownOnDisplay(DEFAULT_DISPLAY);

    }

    @Test
    @FlakyTest(bugId = 297247946)
    public void testImeShowsAfterLockScreenOnEditorTap() throws Exception {
        assumeTrue(MSG_NO_MOCK_IME, supportsInstallableIme());

        final MockImeSession mockImeSession = createManagedMockImeSession(this);
        mLockScreenSession = createManagedLockScreenSession();
        final TestActivitySession<ShowImeAfterLockscreenActivity> imeTestActivitySession =
                createManagedTestActivitySession();
        imeTestActivitySession.launchTestActivityOnDisplaySync(ShowImeAfterLockscreenActivity.class,
                DEFAULT_DISPLAY);

        final ShowImeAfterLockscreenActivity activity = imeTestActivitySession.getActivity();
        final View rootView = activity.getWindow().getDecorView();

        mCtsTouchUtils.emulateTapOnViewCenter(getInstrumentation(), null, activity.mEditor);
        PollingCheck.waitFor(
                TIMEOUT_IME,
                () -> rootView.getRootWindowInsets().isVisible(ime()));

        mLockScreenSession.setLockCredential().gotoKeyguard();
        assertTrue(
                "Keyguard is showing", mWmState.getKeyguardControllerState().isKeyguardShowing());
        mLockScreenSession.unlock();
        mWmState.waitAndAssertKeyguardGone();

        // Wait for the UI idle, make sure the application is idle and input windows is up-to-date.
        getInstrumentation().getUiAutomation().syncInputTransactions();
        mUiDevice.waitForIdle();

        final ImeEventStream stream = mockImeSession.openEventStream();

        mCtsTouchUtils.emulateTapOnViewCenter(getInstrumentation(), null, activity.mEditor);

        // Make sure the activity has been called showSoftInput & IME window is visible.
        expectEvent(stream, event -> "showSoftInput".equals(event.getEventName()),
                TimeUnit.SECONDS.toMillis(5) /* eventTimeout */);
        // Assert the IME is shown event on the expected display.
        mWmState.waitAndAssertImeWindowShownOnDisplay(DEFAULT_DISPLAY);
        // Check if IME is actually visible.
        PollingCheck.waitFor(
                TIMEOUT_IME,
                () -> rootView.getRootWindowInsets().isVisible(ime()));
    }

    public static class ShowWhenLockedActivity extends Activity {
    }

    public static class ShowImeAfterLockscreenActivity extends Activity {

        EditText mEditor;

        @Override
        protected void onCreate(Bundle icicle) {
            super.onCreate(icicle);
            mEditor = createViews(this, false /* showWhenLocked */);
        }
    }

    public static class ShowWhenLockedImeActivity extends Activity {

        @Override
        protected void onCreate(Bundle icicle) {
            super.onCreate(icicle);
            createViews(this, true /* showWhenLocked */);
        }
    }

    private static EditText createViews(
            Activity activity, boolean showWhenLocked /* showWhenLocked */) {
        EditText editor = new EditText(activity);
        // Set private IME option for editorMatcher to identify which TextView received
        // onStartInput event.
        editor.setPrivateImeOptions(
                activity.getClass().getName()
                        + "/" + Long.toString(SystemClock.elapsedRealtimeNanos()));
        final LinearLayout layout = new LinearLayout(activity);
        layout.setFitsSystemWindows(true);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.addView(editor);
        activity.setContentView(layout);

        if (showWhenLocked) {
            // Set showWhenLocked as true & request focus for showing soft input.
            activity.setShowWhenLocked(true);
            activity.getWindow().setSoftInputMode(SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        }
        editor.requestFocus();
        return editor;
    }
}
