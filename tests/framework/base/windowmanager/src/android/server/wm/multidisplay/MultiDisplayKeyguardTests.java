/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.server.wm.multidisplay;

import static android.server.wm.UiDeviceUtils.pressBackButton;
import static android.server.wm.app.Components.DISMISS_KEYGUARD_ACTIVITY;
import static android.view.Display.DEFAULT_DISPLAY;

import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.platform.test.annotations.Presubmit;
import android.server.wm.DisplayMetricsSession;
import android.server.wm.LockScreenSession;
import android.server.wm.MultiDisplayTestBase;
import android.server.wm.WindowManagerState.DisplayContent;
import android.util.Size;

import com.android.window.flags.Flags;

import org.junit.Before;
import org.junit.Test;

/**
 * Display tests that require a keyguard.
 *
 * <p>Build/Install/Run:
 *     atest CtsWindowManagerDeviceMultiDisplay:MultiDisplayKeyguardTests
 */
@Presubmit
@android.server.wm.annotation.Group3
public class MultiDisplayKeyguardTests extends MultiDisplayTestBase {

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();

        assumeTrue(supportsMultiDisplay());
        assumeTrue(supportsInsecureLock());
    }

    /**
     * Tests whether a FLAG_DISMISS_KEYGUARD activity on a secondary display is visible (for an
     * insecure keyguard).
     */
    @Test
    public void testDismissKeyguardActivity_secondaryDisplay() {
        // TODO(b/330152508): Remove check once legacy multi-display behaviour can coexist with
        //  desktop windowing mode
        // Ignore test if desktop windowing is enabled on tablets as legacy multi-display
        // behaviour will not be respected
        assumeFalse(Flags.enableDesktopWindowingMode() && isTablet());

        final LockScreenSession lockScreenSession = createManagedLockScreenSession();
        final DisplayContent newDisplay = createManagedVirtualDisplaySession().createDisplay();

        lockScreenSession.gotoKeyguard();
        mWmState.assertKeyguardShowingAndNotOccluded();
        launchActivityOnDisplay(DISMISS_KEYGUARD_ACTIVITY, newDisplay.mId);
        mWmState.waitForKeyguardShowingAndNotOccluded();
        mWmState.assertKeyguardShowingAndNotOccluded();
        mWmState.assertVisibility(DISMISS_KEYGUARD_ACTIVITY, true);
    }

    /**
     * Tests keyguard dialog shows on secondary display.
     */
    @Test
    public void testShowKeyguardDialogOnSecondaryDisplay() {
        final LockScreenSession lockScreenSession = createManagedLockScreenSession();
        final DisplayContent publicDisplay = createManagedVirtualDisplaySession()
                .setPublicDisplay(true)
                .createDisplay();

        lockScreenSession.gotoKeyguard();
        mWmState.waitAndAssertKeyguardShownOnSecondaryDisplay(publicDisplay.mId);

        // Keyguard dialog mustn't be removed when press back key
        pressBackButton();
        mWmState.computeState();
        mWmState.assertKeyguardShownOnSecondaryDisplay(publicDisplay.mId);
    }

    /**
     * Tests keyguard dialog should exist after secondary display changed.
     */
    @Test
    public void testShowKeyguardDialogSecondaryDisplayChange() {
        final LockScreenSession lockScreenSession = createManagedLockScreenSession();
        final VirtualDisplaySession virtualDisplaySession = createManagedVirtualDisplaySession();

        final DisplayContent publicDisplay = virtualDisplaySession
                .setPublicDisplay(true)
                .createDisplay();

        lockScreenSession.gotoKeyguard();
        mWmState.waitAndAssertKeyguardShownOnSecondaryDisplay(publicDisplay.mId);

        // By default, a Presentation object should be dismissed if the DisplayMetrics changed.
        // But this rule should not apply to KeyguardPresentation.
        virtualDisplaySession.resizeDisplay();
        mWmState.computeState();
        mWmState.waitAndAssertKeyguardShownOnSecondaryDisplay(publicDisplay.mId);
    }

    /**
     * Tests keyguard dialog should exist after default display changed.
     */
    @Test
    public void testShowKeyguardDialogDefaultDisplayChange() {
        final LockScreenSession lockScreenSession = createManagedLockScreenSession();
        final VirtualDisplaySession virtualDisplaySession = createManagedVirtualDisplaySession();
        final DisplayMetricsSession displayMetricsSession =
                createManagedDisplayMetricsSession(DEFAULT_DISPLAY);

        // Use simulate display instead of virtual display, because VirtualDisplayActivity will
        // relaunch after configuration change.
        final DisplayContent publicDisplay = virtualDisplaySession
                .setSimulateDisplay(true)
                .createDisplay();

        lockScreenSession.gotoKeyguard();
        mWmState.waitAndAssertKeyguardShownOnSecondaryDisplay(publicDisplay.mId);

        // Unlock then lock again, to ensure the display metrics has updated.
        lockScreenSession.wakeUpDevice().unlockDevice();
        // Overriding the display metrics on the default display should not affect Keyguard to show
        // on secondary display.
        final ReportedDisplayMetrics originalDisplayMetrics =
                displayMetricsSession.getInitialDisplayMetrics();
        final Size overrideSize =
                new Size(
                        (int) (originalDisplayMetrics.getPhysicalSize().getWidth() * 1.5),
                        (int) (originalDisplayMetrics.getPhysicalSize().getHeight() * 1.5));
        final Integer overrideDensity = (int) (originalDisplayMetrics.getPhysicalDensity() * 1.1);
        displayMetricsSession.overrideDisplayMetrics(overrideSize, overrideDensity);

        lockScreenSession.gotoKeyguard();
        mWmState.waitAndAssertKeyguardShownOnSecondaryDisplay(publicDisplay.mId);
    }

    /**
     * Tests keyguard dialog cannot be shown on private display.
     */
    @Test
    public void testNoKeyguardDialogOnPrivateDisplay() {
        final LockScreenSession lockScreenSession = createManagedLockScreenSession();
        final VirtualDisplaySession virtualDisplaySession = createManagedVirtualDisplaySession();

        final DisplayContent privateDisplay =
                virtualDisplaySession.setPublicDisplay(false).createDisplay();
        final DisplayContent publicDisplay =
                virtualDisplaySession.setPublicDisplay(true).createDisplay();

        lockScreenSession.gotoKeyguard();
        mWmState.waitAndAssertKeyguardShownOnSecondaryDisplay(publicDisplay.mId);
        mWmState.assertKeyguardGoneOnSecondaryDisplay(privateDisplay.mId);
    }

    @Test
    public void testUnlockScreen_secondDisplayChanged_dismissesKeyguardOnUnlock() {
        final LockScreenSession lockScreenSession = createManagedLockScreenSession();
        final VirtualDisplaySession virtualDisplaySession = createManagedVirtualDisplaySession();
        lockScreenSession.setLockCredential();

        // Create second screen
        final DisplayContent secondDisplay = virtualDisplaySession
                .setPublicDisplay(true)
                .createDisplay();
        final int secondDisplayId = secondDisplay.mId;

        // Lock screen. Keyguard should be shown on the second display
        lockScreenSession.gotoKeyguard();
        mWmState.assertKeyguardShowingAndNotOccluded();
        mWmState.waitAndAssertKeyguardShownOnSecondaryDisplay(secondDisplayId);

        // Change second display. Keyguard should still be shown on the second display
        virtualDisplaySession.resizeDisplay();
        mWmState.computeState();
        mWmState.waitAndAssertKeyguardShownOnSecondaryDisplay(secondDisplayId);

        // Unlock device. Keyguard should be dismissed on the second display
        lockScreenSession.unlockDevice();
        lockScreenSession.enterAndConfirmLockCredential();
        mWmState.waitAndAssertKeyguardGone();
        mWmState.waitAndAssertKeyguardGoneOnSecondaryDisplay(secondDisplayId);
    }

    @Test
    public void testUnlockScreen_decoredSystemDisplayChanged_dismissesKeyguardOnUnlock() {
        final LockScreenSession lockScreenSession = createManagedLockScreenSession();
        final VirtualDisplaySession virtualDisplaySession = createManagedVirtualDisplaySession();
        lockScreenSession.setLockCredential();

        // Create decored system screen
        final DisplayContent decoredSystemDisplay = virtualDisplaySession
                .setSimulateDisplay(true)
                .setShowSystemDecorations(true)
                .createDisplay();
        final int decoredSystemDisplayId = decoredSystemDisplay.mId;

        // Lock screen. Keyguard should be shown on the decored system display
        lockScreenSession.gotoKeyguard();
        mWmState.assertKeyguardShowingAndNotOccluded();
        mWmState.waitAndAssertKeyguardShownOnSecondaryDisplay(decoredSystemDisplayId);

        // Resize decored display. Keyguard should still be shown on the decored system display
        final ReportedDisplayMetrics displayMetrics =
                ReportedDisplayMetrics.getDisplayMetrics(decoredSystemDisplayId);
        final Size overrideSize =
                new Size(
                        (int) (displayMetrics.getPhysicalSize().getWidth() * 0.5),
                        (int) (displayMetrics.getPhysicalSize().getHeight() * 0.5));
        final DisplayMetricsSession displayMetricsSession =
                createManagedDisplayMetricsSession(decoredSystemDisplayId);
        displayMetricsSession.overrideDisplayMetrics(
                overrideSize, displayMetrics.getPhysicalDensity());
        mWmState.computeState();
        mWmState.waitAndAssertKeyguardShownOnSecondaryDisplay(decoredSystemDisplayId);

        // Unlock device. Keyguard should be dismissed on the decored system display
        lockScreenSession.unlockDevice();
        lockScreenSession.enterAndConfirmLockCredential();
        mWmState.waitAndAssertKeyguardGone();
        mWmState.waitAndAssertKeyguardGoneOnSecondaryDisplay(decoredSystemDisplayId);
    }
}