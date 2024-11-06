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
import android.os.SystemClock;
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
    private static final int NAV_BAR_MODE_3BUTTON = 0;
    private static final int NAV_BAR_MODE_GESTURAL = 2;

    private static final String NAV_BAR_MODE_3BUTTON_OVERLAY =
            "com.android.internal.systemui.navbar.threebutton";

    private static final String NAV_BAR_MODE_GESTURAL_OVERLAY =
            "com.android.internal.systemui.navbar.gestural";

    private static final int WAIT_OVERLAY_TIMEOUT = 3000;
    private static final int PEEK_INTERVAL = 200;

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
        if (isGestureMode()) {
            return true;
        }
        setNavigationMode(NAV_BAR_MODE_GESTURAL_OVERLAY);
        final boolean success = isGestureMode();
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
        setNavigationMode(NAV_BAR_MODE_3BUTTON_OVERLAY);
        final boolean success = isThreeButtonMode();
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
        if (isGestureMode() || !hasSystemGestureFeature()) {
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
     * Sets the navigation mode to the given one, and disables other navigation modes.
     *
     * @param navigationMode the navigation mode to set.
     */
    private void setNavigationMode(@NonNull String navigationMode) {
        try {
            if (!mDevice.executeShellCommand("cmd overlay list").contains(navigationMode)) {
                return;
            }
        } catch (IOException e) {
            Log.w(TAG, "Failed to get overlay list", e);
        }
        Log.d(TAG, "setNavigationMode: " + navigationMode);
        monitorOverlayChange(() -> {
            try {
                mDevice.executeShellCommand("cmd overlay enable-exclusive --category "
                        + navigationMode);
                mDevice.executeShellCommand("am wait-for-broadcast-barrier");
            } catch (IOException e) {
                Log.w(TAG, "Failed to set navigation mode", e);
            }
        });
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

    // Monitoring the navigation bar insets size change as a hint of gesture mode has changed, not
    // the best option for every kind of devices. We can consider listening OVERLAY_CHANGED
    // broadcast in U.
    private void monitorOverlayChange(@NonNull Runnable overlayChangeCommand) {
        if (mWindowManager != null) {
            final Rect initSize = new Rect();
            getCurrentInsetsSize(initSize);
            overlayChangeCommand.run();
            // wait for insets size change
            final Rect peekSize = new Rect();
            int t = 0;
            while (t < WAIT_OVERLAY_TIMEOUT) {
                SystemClock.sleep(PEEK_INTERVAL);
                t += PEEK_INTERVAL;
                getCurrentInsetsSize(peekSize);
                if (!peekSize.equals(initSize)) {
                    break;
                }
            }
        } else {
            // shouldn't happen
            overlayChangeCommand.run();
            SystemClock.sleep(WAIT_OVERLAY_TIMEOUT);
        }
    }

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
        return containsNavigationBar() && getCurrentNavMode() == NAV_BAR_MODE_3BUTTON;
    }

    /** Whether gesture navigation mode is enabled. */
    public boolean isGestureMode() {
        return containsNavigationBar() && getCurrentNavMode() == NAV_BAR_MODE_GESTURAL;
    }
}
