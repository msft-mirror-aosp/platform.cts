/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.compatibility.common.util;

import static com.google.common.truth.Truth.assertWithMessage;

import android.app.Instrumentation;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Insets;
import android.graphics.Rect;
import android.util.Log;
import android.view.WindowInsets;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import java.io.IOException;

/**
 * Helper class to enable gesture navigation on the device.
 */
public final class GestureNavSwitchHelper {

    private static final String TAG = "GestureNavSwitchHelper";

    private static final String NAV_BAR_INTERACTION_MODE_RES_NAME = "config_navBarInteractionMode";
    private static final int NAV_BAR_MODE_GESTURAL = 2;

    private static final String NAV_BAR_MODE_3BUTTON_OVERLAY =
            "com.android.internal.systemui.navbar.threebutton";

    private static final String NAV_BAR_MODE_GESTURAL_OVERLAY =
            "com.android.internal.systemui.navbar.gestural";

    private static final String STATE_ENABLED = "STATE_ENABLED";

    private static final long OVERLAY_WAIT_TIMEOUT = 10000;

    private final Instrumentation mInstrumentation;
    private final UiDevice mDevice;
    private final WindowManager mWindowManager;
    /** Failed to enable gesture navigation. */
    private boolean mEnableGestureNavFailed;
    /** Failed to enable three button navigation. */
    private boolean mEnableThreeButtonNavFailed;

    /**
     * Initialize all options in System Gesture.
     */
    public GestureNavSwitchHelper() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mDevice = UiDevice.getInstance(mInstrumentation);
        final Context context = mInstrumentation.getTargetContext();

        mWindowManager = context.getSystemService(WindowManager.class);
    }

    /** Whether the device supports gesture navigation bar. */
    public boolean hasSystemGestureFeature() {
        if (!containsNavigationBar()) {
            return false;
        }
        final Context context = mInstrumentation.getTargetContext();
        final PackageManager pm = context.getPackageManager();

        // No bars on embedded devices.
        // No bars on TVs and watches.
        return !(pm.hasSystemFeature(PackageManager.FEATURE_WATCH)
                || pm.hasSystemFeature(PackageManager.FEATURE_EMBEDDED)
                || pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
                || pm.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE));
    }

    private void insetsToRect(Insets insets, Rect outRect) {
        outRect.set(insets.left, insets.top, insets.right, insets.bottom);
    }

    /**
     * Enable gesture navigation mode.
     *
     * @return Whether the navigation mode was successfully set. This is {@code true} if the
     * requested mode is already set.
     */
    public boolean enableGestureNavigationMode() {
        // skip retry
        if (mEnableGestureNavFailed) {
            return false;
        }
        if (!hasSystemGestureFeature()) {
            return false;
        }
        if (isGestureModeWithOverlay()) {
            return true;
        }
        final boolean success = setNavigationMode(NAV_BAR_MODE_GESTURAL_OVERLAY);
        mEnableGestureNavFailed = !success;
        return success;
    }

    /**
     * Enable three button navigation mode.
     *
     * @return Whether the navigation mode was successfully set. This is {@code true} if the
     * requested mode is already set.
     */
    public boolean enableThreeButtonNavigationMode() {
        // skip retry
        if (mEnableThreeButtonNavFailed) {
            return false;
        }
        if (!hasSystemGestureFeature()) {
            return true;
        }
        if (isThreeButtonMode()) {
            return true;
        }
        final boolean success = setNavigationMode(NAV_BAR_MODE_3BUTTON_OVERLAY);
        mEnableThreeButtonNavFailed = !success;
        return success;
    }

    /**
     * Sets the navigation mode to gesture navigation, if necessary.
     *
     * @return an {@link AutoCloseable} that resets the navigation mode, if necessary.
     */
    @NonNull
    public AutoCloseable withGestureNavigationMode() {
        if (isGestureModeWithOverlay() || !hasSystemGestureFeature()) {
            return () -> {};
        }

        assertWithMessage("Gesture navigation mode set")
                .that(enableGestureNavigationMode()).isTrue();
        return () -> assertWithMessage("Gesture navigation mode unset")
                .that(enableThreeButtonNavigationMode()).isTrue();
    }

    /**
     * Sets the navigation mode to three button navigation, if necessary.
     *
     * @return an {@link AutoCloseable} that resets the navigation mode, if necessary.
     */
    @NonNull
    public AutoCloseable withThreeButtonNavigationMode() {
        if (isThreeButtonMode() || !hasSystemGestureFeature()) {
            return () -> {};
        }

        assertWithMessage("Three button navigation mode set")
                .that(enableThreeButtonNavigationMode()).isTrue();
        return () -> assertWithMessage("Three button navigation mode unset")
                .that(enableGestureNavigationMode()).isTrue();
    }

    /**
     * Sets the navigation mode exclusively (disabling all other modes).
     *
     * @param navigationModePkgName the package name of the navigation mode to be set.
     *
     * @return whether the navigation mode was successfully set.
     */
    private boolean setNavigationMode(@NonNull String navigationModePkgName) {
        try {
            final boolean hasOverlay = mDevice.executeShellCommand(
                    "cmd overlay list --user current").contains(navigationModePkgName);
            if (!hasOverlay) {
                Log.i(TAG, "setNavigationMode, overlay: " + navigationModePkgName
                        + " does not exist");
                return false;
            }
        } catch (IOException e) {
            Log.w(TAG, "Failed to get overlay list", e);
            return false;
        }
        Log.d(TAG, "setNavigationMode: " + navigationModePkgName);
        try {
            mDevice.executeShellCommand("cmd overlay enable-exclusive --category --user current "
                    + navigationModePkgName);
            mDevice.executeShellCommand("am wait-for-broadcast-barrier");
        } catch (IOException e) {
            Log.w(TAG, "Failed to set navigation mode", e);
            return false;
        }
        return waitForOverlayState(navigationModePkgName, STATE_ENABLED);
    }

    private void getCurrentInsetsSize(@NonNull Rect outSize) {
        outSize.setEmpty();
        if (mWindowManager != null) {
            WindowInsets insets = mWindowManager.getCurrentWindowMetrics().getWindowInsets();
            Insets navInsets = insets.getInsetsIgnoringVisibility(
                    WindowInsets.Type.navigationBars());
            insetsToRect(navInsets, outSize);
        }
    }

    /**
     * Returns the current navigation mode, as set on the resource with id
     * {@link #NAV_BAR_INTERACTION_MODE_RES_NAME}. Note on instant app mode, as well as in general
     * on some targets, this will return the incorrect value. The actual state of the overlay
     * should be checked instead ({@link #getStateForOverlay}).
     *
     * @return {@code 0} for three button navigation mode, {@code 2} for gesture navigation mode.
     */
    private int getCurrentNavMode() {
        final var res = mInstrumentation.getTargetContext().getResources();
        int naviModeId = res.getIdentifier(NAV_BAR_INTERACTION_MODE_RES_NAME, "integer", "android");
        return res.getInteger(naviModeId);
    }

    private boolean containsNavigationBar() {
        final var peekSize = new Rect();
        getCurrentInsetsSize(peekSize);
        return peekSize.height() != 0;
    }

    /** Whether three button navigation mode is enabled. */
    public boolean isThreeButtonMode() {
        return containsNavigationBar()
                && STATE_ENABLED.equals(getStateForOverlay(NAV_BAR_MODE_3BUTTON_OVERLAY));
    }

    /**
     * Whether gesture navigation mode is enabled. Differs from {@link #isGestureMode} by checking
     * the actual overlay being used. */
    public boolean isGestureModeWithOverlay() {
        return containsNavigationBar()
                && STATE_ENABLED.equals(getStateForOverlay(NAV_BAR_MODE_GESTURAL_OVERLAY));
    }

    /**
     * Whether gesture navigation mode is enabled. Left to avoid breakages to existing tests.
     */
    public boolean isGestureMode() {
        // TODO(b/377912666): replace with {@link #isGestureModeWithOverlay} after ensuring tests
        //  don't break.
        return containsNavigationBar() && getCurrentNavMode() == NAV_BAR_MODE_GESTURAL;
    }

    /**
     * Waits for the state of the overlay with the given package name to match the expected state.
     *
     * @param overlayPackage the package name of the overlay to check.
     * @param expectedState  the expected overlay state.
     *
     * @return whether the overlay state eventually matched the expected state.
     */
    private boolean waitForOverlayState(@NonNull String overlayPackage,
            @NonNull String expectedState) {
        final long startTime = System.currentTimeMillis();

        while (System.currentTimeMillis() - startTime < OVERLAY_WAIT_TIMEOUT) {
            final String state = getStateForOverlay(overlayPackage);
            if (expectedState.equals(state)) {
                return true;
            }
        }

        Log.i(TAG, "waitForOverlayState, overlayPackage: " + overlayPackage + " state was not: "
                + expectedState);
        return false;
    }

    /**
     * Returns the state of the overlay with the given package name.
     *
     * @param overlayPackage the package name of the overlay to check.
     */
    @NonNull
    private String getStateForOverlay(@NonNull String overlayPackage) {
        // TODO(b/377912666): create TestApi for checking overlay state directly
        final String dumpResult;
        try {
            dumpResult = mDevice.executeShellCommand("cmd overlay dump");
        } catch (IOException e) {
            Log.w(TAG, "Failed to get overlay dump", e);
            return "";
        }

        final String overlayPackageForCurrentUser =
                overlayPackage + ":" + mInstrumentation.getContext().getUserId();

        final int startIndex = dumpResult.indexOf(overlayPackageForCurrentUser);
        if (startIndex < 0) {
            Log.i(TAG, "getStateForOverlay, " + overlayPackageForCurrentUser + " not found");
            return "";
        }

        final int endIndex = dumpResult.indexOf('}', startIndex);
        if (endIndex <= startIndex) {
            Log.i(TAG, "getStateForOverlay, state closing bracket not found");
            return "";
        }

        final int stateIndex = dumpResult.indexOf("mState", startIndex);
        if (stateIndex <= startIndex || stateIndex >= endIndex) {
            Log.i(TAG, "getStateForOverlay, mState not found");
            return "";
        }

        final int colonIndex = dumpResult.indexOf(':', stateIndex);
        if (colonIndex <= stateIndex || colonIndex >= endIndex) {
            Log.i(TAG, "getStateForOverlay, colon separator not found");
            return "";
        }

        final int endLineIndex = dumpResult.indexOf('\n', colonIndex);
        if (endLineIndex <= colonIndex || endLineIndex >= endIndex) {
            Log.i(TAG, "getStateForOverlay, line end not found");
            return "";
        }

        final var overlayState = dumpResult.substring(colonIndex + 2, endLineIndex);
        return overlayState;
    }
}
