/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.server.wm;

import static android.app.AppOpsManager.MODE_ALLOWED;
import static android.app.AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW;
import static android.server.wm.UiDeviceUtils.pressUnlockButton;
import static android.server.wm.UiDeviceUtils.pressWakeupButton;
import static android.server.wm.WindowManagerState.STATE_RESUMED;
import static android.server.wm.overlay.Components.TestCompanionService.ACTION_SHOW_SYSTEM_ALERT_WINDOW;
import static android.server.wm.overlay.Components.TestCompanionService.EXTRA_NAME;
import static android.server.wm.overlay.Components.TestCompanionService.EXTRA_OPACITY;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.fail;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.input.InputManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.platform.test.annotations.Presubmit;
import android.provider.Settings;
import android.server.wm.overlay.Components;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.test.rule.ActivityTestRule;

import com.android.compatibility.common.util.AppOpsUtils;
import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Presubmit
public class WindowUntrustedTouchTest {
    private static final String TAG = "WindowUntrustedTouchTest";

    /**
     * Opacity (or alpha) is represented as a half-precision floating point number (16b) in surface
     * flinger and the conversion from the single-precision float provided to window manager happens
     * in Layer::setAlpha() by android::half::ftoh(). So, many small non-zero values provided to
     * window manager end up becoming zero due to loss of precision (this is fine as long as the
     * zeros are also used to render the pixels on the screen). So, the minimum opacity possible is
     * actually the minimum positive value representable in half-precision float, which is
     * 0_00001_0000000000, whose equivalent in float is 0_01110001_00000000000000000000000.
     *
     * Note that from float -> half conversion code we don't produce any subnormal half-precision
     * floats during conversion.
     */
    public static final float MIN_POSITIVE_OPACITY =
            Float.intBitsToFloat(0b00111000100000000000000000000000);

    private static final float MAXIMUM_OBSCURING_OPACITY = .8f;
    private static final long TOUCH_TIME_OUT_MS = 1000L;
    private static final long PROCESS_RESPONSE_TIME_OUT_MS = 1000L;
    private static final int OVERLAY_COLOR = 0xFFFF0000;
    private static final int ACTIVITY_COLOR = 0xFFFFFFFF;

    private static final int FEATURE_MODE_DISABLED = 0;
    private static final int FEATURE_MODE_PERMISSIVE = 1;
    private static final int FEATURE_MODE_BLOCK = 2;

    private static final String APP_SELF =
            WindowUntrustedTouchTest.class.getPackage().getName() + ".cts";
    private static final String APP_A =
            android.server.wm.second.Components.class.getPackage().getName();
    private static final String APP_B =
            android.server.wm.third.Components.class.getPackage().getName();
    private static final String WINDOW_1 = "W1";
    private static final String WINDOW_2 = "W2";

    private static final String[] APPS = {APP_A, APP_B};

    private static final String SETTING_MAXIMUM_OBSCURING_OPACITY =
            "maximum_obscuring_opacity_for_touch";

    private final WindowManagerStateHelper mWmState = new WindowManagerStateHelper();
    private final Map<String, FutureConnection> mConnections = new ArrayMap<>();
    private Instrumentation mInstrumentation;
    private Context mContext;
    private ContentResolver mContentResolver;
    private TouchHelper mTouchHelper;
    private InputManager mInputManager;
    private WindowManager mWindowManager;
    private ActivityManager mActivityManager;
    private TestActivity mActivity;
    private View mContainer;
    private float mPreviousTouchOpacity;
    private int mPreviousMode;
    private int mPreviousSawAppOp;
    private final Set<String> mSawWindowsAdded = new ArraySet<>();
    private final AtomicInteger mTouchesReceived = new AtomicInteger(0);


    /** Can only be accessed from the main thread */
    private final Set<View> mSawViewsAdded = new ArraySet<>();
    private Toast mToast;

    @Rule
    public TestName testNameRule = new TestName();

    @Rule
    public ActivityTestRule<TestActivity> activityRule = new ActivityTestRule<>(TestActivity.class);

    @Before
    public void setUp() throws Exception {
        mActivity = activityRule.getActivity();
        mContainer = mActivity.view;
        mContainer.setOnTouchListener(this::onTouchEvent);
        mInstrumentation = getInstrumentation();
        mContext = mInstrumentation.getContext();
        mContentResolver = mContext.getContentResolver();
        mTouchHelper = new TouchHelper(mInstrumentation, mWmState);
        mInputManager = mContext.getSystemService(InputManager.class);
        mWindowManager = mContext.getSystemService(WindowManager.class);
        mActivityManager = mContext.getSystemService(ActivityManager.class);

        mPreviousSawAppOp = AppOpsUtils.getOpMode(APP_SELF, OPSTR_SYSTEM_ALERT_WINDOW);
        AppOpsUtils.setOpMode(APP_SELF, OPSTR_SYSTEM_ALERT_WINDOW, MODE_ALLOWED);
        mPreviousTouchOpacity = setMaximumObscuringOpacityForTouch(MAXIMUM_OBSCURING_OPACITY);
        mPreviousMode = setBlockUntrustedTouchesMode(FEATURE_MODE_BLOCK);

        pressWakeupButton();
        pressUnlockButton();
    }

    @After
    public void tearDown() throws Throwable {
        mTouchesReceived.set(0);
        for (FutureConnection connection : mConnections.values()) {
            mContext.unbindService(connection);
        }
        removeOverlays();
        for (String app : APPS) {
            stopPackage(app);
        }
        setBlockUntrustedTouchesMode(mPreviousMode);
        setMaximumObscuringOpacityForTouch(mPreviousTouchOpacity);
        AppOpsUtils.setOpMode(APP_SELF, OPSTR_SYSTEM_ALERT_WINDOW, mPreviousSawAppOp);
    }

    @Test
    public void testWhenFeatureInDisabledModeAndActivityWindowAbove_allowsTouch()
            throws Throwable {
        setBlockUntrustedTouchesMode(FEATURE_MODE_DISABLED);
        addActivityOverlay(APP_A, /* opacity */ .9f);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchReceived();
    }

    @Test
    public void testWhenFeatureInPermissiveModeAndActivityWindowAbove_allowsTouch()
            throws Throwable {
        setBlockUntrustedTouchesMode(FEATURE_MODE_PERMISSIVE);
        addActivityOverlay(APP_A, /* opacity */ .9f);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchReceived();
    }

    @Test
    public void testWhenFeatureInBlockModeAndActivityWindowAbove_blocksTouch()
            throws Throwable {
        setBlockUntrustedTouchesMode(FEATURE_MODE_BLOCK);
        addActivityOverlay(APP_A, /* opacity */ .9f);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchNotReceived();
    }

    @Test
    public void testAfterSettingThreshold_returnsThresholdSet()
            throws Throwable {
        float threshold = .123f;
        setMaximumObscuringOpacityForTouch(threshold);

        assertEquals(threshold, mInputManager.getMaximumObscuringOpacityForTouch(mContext));
    }

    @Test
    public void testAfterSettingFeatureMode_returnsModeSet()
            throws Throwable {
        // Make sure the previous mode is different
        setBlockUntrustedTouchesMode(FEATURE_MODE_BLOCK);
        assertEquals(FEATURE_MODE_BLOCK, mInputManager.getBlockUntrustedTouchesMode(mContext));
        setBlockUntrustedTouchesMode(FEATURE_MODE_PERMISSIVE);

        assertEquals(FEATURE_MODE_PERMISSIVE, mInputManager.getBlockUntrustedTouchesMode(mContext));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAfterSettingThresholdLessThan0_throws() throws Throwable {
        setMaximumObscuringOpacityForTouch(-.5f);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAfterSettingThresholdGreaterThan1_throws() throws Throwable {
        setMaximumObscuringOpacityForTouch(1.5f);
    }

    /** This is testing what happens if setting is overridden manually */
    @Test
    public void testAfterSettingThresholdGreaterThan1ViaSettings_previousThresholdIsUsed()
            throws Throwable {
        setMaximumObscuringOpacityForTouch(.8f);
        assertEquals(.8f, mInputManager.getMaximumObscuringOpacityForTouch(mContext));
        SystemUtil.runWithShellPermissionIdentity(() -> {
            Settings.Global.putFloat(mContentResolver, SETTING_MAXIMUM_OBSCURING_OPACITY, 1.5f);
        });
        addSawOverlay(APP_A, WINDOW_1, 9.f);

        mTouchHelper.tapOnViewCenter(mContainer);

        // Blocks because it's using previous maximum of .8
        assertTouchNotReceived();
    }

    /** This is testing what happens if setting is overridden manually */
    @Test
    public void testAfterSettingThresholdLessThan0ViaSettings_previousThresholdIsUsed()
            throws Throwable {
        setMaximumObscuringOpacityForTouch(.8f);
        assertEquals(.8f, mInputManager.getMaximumObscuringOpacityForTouch(mContext));
        SystemUtil.runWithShellPermissionIdentity(() -> {
            Settings.Global.putFloat(mContentResolver, SETTING_MAXIMUM_OBSCURING_OPACITY, -.5f);
        });
        addSawOverlay(APP_A, WINDOW_1, .7f);

        mTouchHelper.tapOnViewCenter(mContainer);

        // Allows because it's using previous maximum of .8
        assertTouchReceived();
    }

    /** SAWs */

    @Test
    public void testWhenOneSawWindowAboveThreshold_blocksTouch() throws Throwable {
        addSawOverlay(APP_A, WINDOW_1, .9f);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchNotReceived();
    }

    @Test
    public void testWhenOneSawWindowBelowThreshold_allowsTouch() throws Throwable {
        addSawOverlay(APP_A, WINDOW_1, .7f);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchReceived();
    }

    @Test
    public void testWhenOneSawWindowWithZeroOpacity_allowsTouch() throws Throwable {
        addSawOverlay(APP_A, WINDOW_1, 0f);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchReceived();
    }

    @Test
    public void testWhenOneSawWindowAtThreshold_allowsTouch() throws Throwable {
        addSawOverlay(APP_A, WINDOW_1, MAXIMUM_OBSCURING_OPACITY);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchReceived();
    }

    @Test
    public void testWhenTwoSawWindowsFromSameAppTogetherBelowThreshold_allowsTouch()
            throws Throwable {
        // Resulting opacity = 1 - (1 - 0.5)*(1 - 0.5) = .75
        addSawOverlay(APP_A, WINDOW_1, .5f);
        addSawOverlay(APP_A, WINDOW_2, .5f);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchReceived();
    }

    @Test
    public void testWhenTwoSawWindowsFromSameAppTogetherAboveThreshold_blocksTouch()
            throws Throwable {
        // Resulting opacity = 1 - (1 - 0.7)*(1 - 0.7) = .91
        addSawOverlay(APP_A, WINDOW_1, .7f);
        addSawOverlay(APP_A, WINDOW_2, .7f);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchNotReceived();
    }

    @Test
    public void testWhenTwoSawWindowsFromDifferentAppsEachBelowThreshold_allowsTouch()
            throws Throwable {
        addSawOverlay(APP_A, WINDOW_1, .7f);
        addSawOverlay(APP_B, WINDOW_2, .7f);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchReceived();
    }

    @Test
    public void testWhenOneSawWindowAboveThresholdAndSelfSawWindow_blocksTouch()
            throws Throwable {
        addSawOverlay(APP_A, WINDOW_1, .9f);
        addSawOverlay(APP_SELF, WINDOW_1, .7f);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchNotReceived();
    }

    @Test
    public void testWhenOneSawWindowBelowThresholdAndSelfSawWindow_allowsTouch()
            throws Throwable {
        addSawOverlay(APP_A, WINDOW_1, .7f);
        addSawOverlay(APP_SELF, WINDOW_1, .7f);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchReceived();
    }

    @Test
    public void testWhenTwoSawWindowsTogetherBelowThresholdAndSelfSawWindow_allowsTouch()
            throws Throwable {
        // Resulting opacity for A = 1 - (1 - 0.5)*(1 - 0.5) = .75
        addSawOverlay(APP_A, WINDOW_1, .5f);
        addSawOverlay(APP_A, WINDOW_1, .5f);
        addSawOverlay(APP_SELF, WINDOW_1, .7f);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchReceived();
    }

    @Test
    public void testWhenThresholdIs0AndSawWindowAtThreshold_allowsTouch()
            throws Throwable {
        setMaximumObscuringOpacityForTouch(0);
        addSawOverlay(APP_A, WINDOW_1, 0);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchReceived();
    }

    @Test
    public void testWhenThresholdIs0AndSawWindowAboveThreshold_blocksTouch()
            throws Throwable {
        setMaximumObscuringOpacityForTouch(0);
        addSawOverlay(APP_A, WINDOW_1, .1f);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchNotReceived();
    }

    @Test
    public void testWhenThresholdIs1AndSawWindowAtThreshold_allowsTouch()
            throws Throwable {
        setMaximumObscuringOpacityForTouch(1);
        addSawOverlay(APP_A, WINDOW_1, 1);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchReceived();
    }

    @Test
    public void testWhenThresholdIs1AndSawWindowBelowThreshold_allowsTouch()
            throws Throwable {
        setMaximumObscuringOpacityForTouch(1);
        addSawOverlay(APP_A, WINDOW_1, .9f);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchReceived();
    }

    /** Activity windows */

    @Test
    public void testWhenOneActivityWindowBelowThreshold_blocksTouch()
            throws Throwable {
        addActivityOverlay(APP_A, /* opacity */ .5f);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchNotReceived();
    }

    @Test
    public void testWhenOneActivityWindowAboveThreshold_blocksTouch()
            throws Throwable {
        addActivityOverlay(APP_A, /* opacity */ .9f);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchNotReceived();
    }

    @Test
    public void testWhenOneActivityWindowWithZeroOpacity_allowsTouch()
            throws Throwable {
        addActivityOverlay(APP_A, /* opacity */ 0f);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchReceived();
    }

    @Test
    public void testWhenOneActivityWindowWithMinPositiveOpacity_blocksTouch()
            throws Throwable {
        addActivityOverlay(APP_A, /* opacity */ MIN_POSITIVE_OPACITY);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchNotReceived();
    }

    @Test
    public void testWhenOneActivityWindowWithSmallOpacity_blocksTouch()
            throws Throwable {
        addActivityOverlay(APP_A, /* opacity */ .01f);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchNotReceived();
    }

    @Test
    public void testWhenOneSelfActivityWindow_allowsTouch() throws Throwable {
        addActivityOverlay(APP_SELF, /* opacity */ .9f);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchReceived();
    }

    @Test
    public void testWhenTwoActivityWindowsFromDifferentAppsTogetherBelowThreshold_blocksTouch()
            throws Throwable {
        addActivityOverlay(APP_A, /* opacity */ .7f);
        addActivityOverlay(APP_B, /* opacity */ .7f);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchNotReceived();
    }

    @Test
    public void testWhenOneActivityWindowAndOneSawWindowTogetherBelowThreshold_blocksTouch()
            throws Throwable {
        addActivityOverlay(APP_A, /* opacity */ .5f);
        addSawOverlay(APP_A, WINDOW_1, .5f);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchNotReceived();
    }

    @Test
    public void testWhenOneActivityWindowAndOneSelfCustomToastWindow_blocksTouch()
            throws Throwable {
        // Toast has to be before otherwise it would be blocked from background
        addToastOverlay(APP_SELF, /* custom */ true);
        addActivityOverlay(APP_A, /* opacity */ .5f);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchNotReceived();
    }

    @Test
    public void testWhenOneActivityWindowAndOneSelfSawWindow_blocksTouch()
            throws Throwable {
        addActivityOverlay(APP_A, /* opacity */ .5f);
        addSawOverlay(APP_SELF, WINDOW_1, .5f);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchNotReceived();
    }

    @Test
    public void testWhenOneActivityWindowAndOneSawWindowBelowThreshold_blocksTouch()
            throws Throwable {
        addActivityOverlay(APP_A, /* opacity */ .5f);
        addSawOverlay(APP_A, WINDOW_1, .5f);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchNotReceived();
    }

    @Test
    public void testWhenOneActivityWindowAndOneSawWindowBelowThresholdFromDifferentApp_blocksTouch()
            throws Throwable {
        addActivityOverlay(APP_A, /* opacity */ .5f);
        addSawOverlay(APP_B, WINDOW_1, .5f);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchNotReceived();
    }

    /** Toast windows */

    @Test
    public void testWhenSelfTextToastWindow_allowsTouch() throws Throwable {
        addToastOverlay(APP_SELF, /* custom */ false);
        Rect toast = mWmState.waitForResult("toast bounds",
                state -> state.findFirstWindowWithType(LayoutParams.TYPE_TOAST).getContentFrame());

        mTouchHelper.tapOnCenter(toast, mActivity.getDisplayId());

        assertTouchReceived();
    }

    @Test
    public void testWhenTextToastWindow_allowsTouch() throws Throwable {
        addToastOverlay(APP_A, /* custom */ false);
        Rect toast = mWmState.waitForResult("toast bounds",
                state -> state.findFirstWindowWithType(LayoutParams.TYPE_TOAST).getContentFrame());

        mTouchHelper.tapOnCenter(toast, mActivity.getDisplayId());

        assertTouchReceived();
    }

    @Test
    public void testWhenOneCustomToastWindow_blocksTouch() throws Throwable {
        addToastOverlay(APP_A, /* custom */ true);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchNotReceived();
    }

    @Test
    public void testWhenOneSelfCustomToastWindow_allowsTouch() throws Throwable {
        addToastOverlay(APP_SELF, /* custom */ true);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchReceived();
    }

    @Test
    public void testWhenOneCustomToastWindowAndOneSelfSawWindow_blocksTouch()
            throws Throwable {
        addSawOverlay(APP_SELF, WINDOW_1, .9f);
        addToastOverlay(APP_A, /* custom */ true);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchNotReceived();
    }

    @Test
    public void testWhenOneCustomToastWindowAndOneSawWindowBelowThreshold_blocksTouch()
            throws Throwable {
        addSawOverlay(APP_A, WINDOW_1, .5f);
        addToastOverlay(APP_A, /* custom */ true);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchNotReceived();
    }

    @Test
    public void testWhenOneCustomToastWindowAndOneSawWindowBelowThresholdFromDifferentApp_blocksTouch()
            throws Throwable {
        addSawOverlay(APP_A, WINDOW_1, .5f);
        addToastOverlay(APP_B, /* custom */ true);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchNotReceived();
    }

    @Test
    public void testWhenOneSelfCustomToastWindowOneSelfActivityWindowAndOneSawBelowThreshold_allowsTouch()
            throws Throwable {
        addActivityOverlay(APP_SELF, /* opacity */ .9f);
        addSawOverlay(APP_A, WINDOW_1, .5f);
        addToastOverlay(APP_SELF, /* custom */ true);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchReceived();
    }

    private boolean onTouchEvent(View view, MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            mTouchesReceived.incrementAndGet();
        }
        return true;
    }

    private void assertTouchReceived() {
        mInstrumentation.waitForIdleSync();
        assertThat(mTouchesReceived.get()).isEqualTo(1);
    }

    private void assertTouchNotReceived() {
        mInstrumentation.waitForIdleSync();
        assertThat(mTouchesReceived.get()).isEqualTo(0);
    }

    private void addToastOverlay(String packageName, boolean custom) throws Exception {
        // Making sure there are no toasts currently since we can only check for the presence of
        // *any* toast afterwards and we don't want to be in a situation where this method returned
        // because another toast was being displayed.
        waitForNoToastOverlays();
        if (packageName.equals(APP_SELF)) {
            addMyToastOverlay(custom);
        } else {
            if (custom) {
                // We have to use an activity that will display the toast then finish itself because
                // custom toasts cannot be posted from the background.
                Intent intent = new Intent();
                intent.setComponent(repackage(packageName, Components.ToastActivity.COMPONENT));
                mActivity.startActivity(intent);
            } else {
                sendMessage(packageName, Components.TestCompanionService.ACTION_SHOW_TOAST);
            }
        }
        String message = "Toast from app " + packageName + " did not appear on time";
        // TODO: WindowStateProto does not have package/UID information from the window, the current
        //  package test relies on the window name, which is not how toast windows are named. We
        //  should ideally incorporate that information in WindowStateProto and use here.
        if (!mWmState.waitFor("toast window", this::hasVisibleToast)) {
            fail(message);
        }
    }

    private boolean hasVisibleToast(WindowManagerState state) {
        return !state.getMatchingWindowType(LayoutParams.TYPE_TOAST).isEmpty()
                && state.findFirstWindowWithType(LayoutParams.TYPE_TOAST).isSurfaceShown();
    }

    private void addMyToastOverlay(boolean custom) {
        mActivity.runOnUiThread(() -> {
            if (custom) {
                mToast = new Toast(mContext);
                View view = new View(mContext);
                view.setBackgroundColor(OVERLAY_COLOR);
                mToast.setView(view);
                mToast.setGravity(Gravity.FILL, 0, 0);
                mToast.setDuration(Toast.LENGTH_LONG);
            } else {
                String test =
                        getClass().getSimpleName() + "." + testNameRule.getMethodName() + "()";
                mToast = Toast.makeText(mContext, "Toast from " + test, Toast.LENGTH_LONG);
            }
            mToast.show();
        });
        mInstrumentation.waitForIdleSync();
    }

    private void removeMyToastOverlay() {
        mActivity.runOnUiThread(() -> {
            if (mToast != null) {
                mToast.cancel();
                mToast = null;
            }
        });
        mInstrumentation.waitForIdleSync();
    }

    private void waitForNoToastOverlays() {
        waitForNoToastOverlays("Toast windows did not hide on time");
    }

    private void waitForNoToastOverlays(String message) {
        if (!mWmState.waitFor("no toast windows",
                state -> state.getMatchingWindowType(LayoutParams.TYPE_TOAST).isEmpty())) {
            fail(message);
        }
    }

    private void addActivityOverlay(String packageName, float opacity) {
        ComponentName activityComponent = (packageName.equals(APP_SELF))
                ? new ComponentName(mContext, OverlayActivity.class)
                : repackage(packageName, Components.OverlayActivity.COMPONENT);
        Intent intent = new Intent();
        intent.setComponent(activityComponent);
        intent.putExtra(Components.OverlayActivity.EXTRA_OPACITY, opacity);
        mActivity.startActivity(intent);
        String message = "Activity from app " + packageName + " did not appear on time";
        String activity = ComponentNameUtils.getActivityName(activityComponent);
        if (!mWmState.waitFor("activity window " + activity,
                state -> activity.equals(state.getFocusedActivity())
                        && state.hasActivityState(activityComponent, STATE_RESUMED))) {
            fail(message);
        }
    }

    private void removeActivityOverlays() {
        Intent intent = new Intent(mContext, mActivity.getClass());
        // Will clear any activity on top of it and it will become the new top
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        mActivity.startActivity(intent);
    }

    private void waitForNoActivityOverlays(String message) {
        // Base activity focused means no activities on top
        ComponentName component = mActivity.getComponentName();
        String name = ComponentNameUtils.getActivityName(component);
        if (!mWmState.waitFor("test rule activity focused",
                state -> name.equals(state.getFocusedActivity())
                        && state.hasActivityState(component, STATE_RESUMED))) {
            fail(message);
        }
    }

    private void addSawOverlay(String packageName, String windowSuffix, float opacity)
            throws Throwable {
        String name = packageName + "." + windowSuffix;
        if (packageName.equals(APP_SELF)) {
            addMySawOverlay(name, opacity);
        } else {
            Bundle data = new Bundle();
            data.putString(EXTRA_NAME, name);
            data.putFloat(EXTRA_OPACITY, opacity);
            sendMessage(packageName, ACTION_SHOW_SYSTEM_ALERT_WINDOW, data);
        }
        mSawWindowsAdded.add(name);
        String message = "Window " + name + " did not appear on time";
        if (!mWmState.waitFor("window " + name,
                state -> state.isWindowVisible(name) && state.isWindowSurfaceShown(name))) {
            fail(message);
        }
    }

    private void addMySawOverlay(String name, float opacity) throws Throwable {
        activityRule.runOnUiThread(() -> {
            View view = new View(mContext);
            view.setBackgroundColor(OVERLAY_COLOR);
            LayoutParams params =
                    new LayoutParams(
                            LayoutParams.MATCH_PARENT,
                            LayoutParams.MATCH_PARENT,
                            LayoutParams.TYPE_APPLICATION_OVERLAY,
                            LayoutParams.FLAG_NOT_TOUCHABLE | LayoutParams.FLAG_NOT_FOCUSABLE,
                            PixelFormat.TRANSLUCENT);
            params.setTitle(name);
            params.alpha = opacity;
            mWindowManager.addView(view, params);
            mSawViewsAdded.add(view);
        });
        mInstrumentation.waitForIdleSync();
    }

    private void removeMySawOverlays() throws Throwable {
        activityRule.runOnUiThread(() -> {
            for (View view : mSawViewsAdded) {
                mWindowManager.removeViewImmediate(view);
            }
            mSawViewsAdded.clear();
        });
        mInstrumentation.waitForIdleSync();
    }

    private void waitForNoSawOverlays(String message) {
        if (!mWmState.waitFor("no SAW windows",
                state -> mSawWindowsAdded.stream().allMatch(w -> !state.isWindowVisible(w)))) {
            fail(message);
        }
        mSawWindowsAdded.clear();
    }

    private void removeOverlays() throws Throwable {
        for (String app : APPS) {
            stopPackage(app);
        }
        removeMySawOverlays();
        waitForNoSawOverlays("SAWs not removed on time");
        removeActivityOverlays();
        waitForNoActivityOverlays("Activities not removed on time");
        removeMyToastOverlay();
        waitForNoToastOverlays("Toasts not removed on time");
    }

    private void stopPackage(String packageName) {
        SystemUtil.runWithShellPermissionIdentity(
                () -> mActivityManager.forceStopPackage(packageName));
    }

    private int setBlockUntrustedTouchesMode(int mode) throws Exception {
        return SystemUtil.callWithShellPermissionIdentity(() -> {
            int previous = mInputManager.getBlockUntrustedTouchesMode(mContext);
            mInputManager.setBlockUntrustedTouchesMode(mContext, mode);
            return previous;
        });
    }

    private float setMaximumObscuringOpacityForTouch(float opacity) throws Exception {
        return SystemUtil.callWithShellPermissionIdentity(() -> {
            float previous = mInputManager.getMaximumObscuringOpacityForTouch(mContext);
            mInputManager.setMaximumObscuringOpacityForTouch(mContext, opacity);
            return previous;
        });
    }

    private ComponentName repackage(String packageName, ComponentName baseComponent) {
        return new ComponentName(packageName, baseComponent.getClassName());
    }

    private void sendMessage(String packageName, int action) throws Exception {
        sendMessage(packageName, action, /* data */ null);
    }

    private void sendMessage(String packageName, int action, Bundle data) throws Exception {
        Message message = Message.obtain(null, action);
        message.setData(data);
        FutureConnection connection = mConnections.computeIfAbsent(packageName, this::connect);
        Messenger messenger = new Messenger(connection.get(PROCESS_RESPONSE_TIME_OUT_MS));
        messenger.send(message);
    }

    private FutureConnection connect(String packageName) {
        FutureConnection connection = new FutureConnection();
        Intent intent = new Intent();
        intent.setComponent(repackage(packageName, Components.TestCompanionService.COMPONENT));
        assertTrue(mContext.bindService(intent, connection, Context.BIND_AUTO_CREATE));
        return connection;
    }

    public static class TestActivity extends Activity {
        public View view;

        @Override
        protected void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            view = new View(this);
            view.setBackgroundColor(ACTIVITY_COLOR);
            setContentView(view);
        }
    }

    public static class OverlayActivity extends Activity {
        @Override
        protected void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            View view = new View(this);
            view.setBackgroundColor(OVERLAY_COLOR);
            setContentView(view);
            Window window = getWindow();
            window.getAttributes().alpha = getIntent().getFloatExtra(
                    Components.OverlayActivity.EXTRA_OPACITY, 1f);
            window.addFlags(LayoutParams.FLAG_NOT_TOUCHABLE);
            window.addFlags(LayoutParams.FLAG_NOT_FOCUSABLE);
        }
    }

    private static class FutureConnection implements ServiceConnection {
        public final CompletableFuture<IBinder> future = new CompletableFuture<>();

        public IBinder get(long timeoutMs) throws Exception {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            if (future.isDone()) {
                Log.d(TAG, name.flattenToShortString() + " reconnected");
            }
            future.obtrudeValue(service);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            // The helper app died, windows will be removed and assertions will fail, so just log.
            Log.e(TAG, name.flattenToShortString() + " disconnected");
        }
    }
}
