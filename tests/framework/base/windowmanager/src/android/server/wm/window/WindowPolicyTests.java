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

package android.server.wm.window;

import static android.content.res.Resources.ID_NULL;
import static android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Bundle;
import android.platform.test.annotations.Presubmit;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.server.wm.WindowManagerTestBase;
import android.server.wm.cts.R;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;

import com.android.window.flags.Flags;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Ensure window policies are applied as expected.
 *
 * Build/Install/Run:
 *     atest CtsWindowManagerDeviceWindow:WindowPolicyTests
 */
@RequiresFlagsEnabled(Flags.FLAG_ENFORCE_EDGE_TO_EDGE)
@Presubmit
public class WindowPolicyTests extends WindowManagerTestBase {

    @Override
    public void setUp() throws Exception {
        super.setUp();
        TestActivity.sStyleId = ID_NULL;
        TestActivity.sLayoutInDisplayCutoutMode =
                TestActivity.LAYOUT_IN_DISPLAY_CUTOUT_MODE_UNSPECIFIED;
    }

    @Test
    public void testWindowInsets() {
        final TestActivity activity = startTestActivitySync();

        getInstrumentation().runOnMainSync(() -> {
            assertEquals(
                    "WindowInsets must not be changed.",
                    activity.mContentView.getRootWindowInsets(),
                    activity.mContentView.mWindowInsets);
        });
    }

    private static void assertFillWindowBounds(TestActivity activity) {
        getInstrumentation().runOnMainSync(() -> {
            assertEquals(
                    "Decor view must fill window bounds.",
                    activity.getResources().getConfiguration().windowConfiguration.getBounds(),
                    getFrameOnScreen(activity.getWindow().getDecorView()));
            assertEquals(
                    "Content view must fill window bounds.",
                    activity.getResources().getConfiguration().windowConfiguration.getBounds(),
                    getFrameOnScreen(activity.mContentView));
        });
    }

    private static Rect getFrameOnScreen(View view) {
        final int[] location = {0, 0};
        view.getLocationOnScreen(location);
        return new Rect(
                location[0],
                location[1],
                location[0] + view.getWidth(),
                location[1] + view.getHeight());
    }

    @Test
    public void testWindowStyleLayoutInDisplayCutoutMode_unspecified() {
        TestActivity.sStyleId = R.style.LayoutInDisplayCutoutModeAlways;
        assertFillWindowBounds(startTestActivitySync());
    }

    @Test
    public void testWindowStyleLayoutInDisplayCutoutMode_never() {
        TestActivity.sStyleId = R.style.LayoutInDisplayCutoutModeAlways;
        assertFillWindowBounds(startTestActivitySync());
    }

    @Test
    public void testWindowStyleLayoutInDisplayCutoutMode_default() {
        TestActivity.sStyleId = R.style.LayoutInDisplayCutoutModeAlways;
        assertFillWindowBounds(startTestActivitySync());
    }

    @Test
    public void testWindowStyleLayoutInDisplayCutoutMode_shortEdges() {
        TestActivity.sStyleId = R.style.LayoutInDisplayCutoutModeAlways;
        assertFillWindowBounds(startTestActivitySync());
    }

    @Test
    public void testWindowStyleLayoutInDisplayCutoutMode_always() {
        TestActivity.sStyleId = R.style.LayoutInDisplayCutoutModeAlways;
        assertFillWindowBounds(startTestActivitySync());
    }

    @Test
    public void testLayoutParamsLayoutInDisplayCutoutMode_unspecified() {
        assertFillWindowBounds(startTestActivitySync());
    }

    @Test
    public void testLayoutParamsLayoutInDisplayCutoutMode_never() {
        TestActivity.sLayoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        assertFillWindowBounds(startTestActivitySync());
    }

    @Test
    public void testLayoutParamsLayoutInDisplayCutoutMode_default() {
        TestActivity.sLayoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        assertFillWindowBounds(startTestActivitySync());
    }

    @Test
    public void testLayoutParamsLayoutInDisplayCutoutMode_shortEdges() {
        TestActivity.sLayoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        assertFillWindowBounds(startTestActivitySync());
    }

    @Test
    public void testLayoutParamsLayoutInDisplayCutoutMode_always() {
        TestActivity.sLayoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        assertFillWindowBounds(startTestActivitySync());
    }

    @Test
    public void testSystemBarColor() {
        TestActivity.sStyleId = R.style.BlackSystemBars;
        final TestActivity activity = startTestActivitySync();
        getInstrumentation().runOnMainSync(() -> {
            final Window window = activity.getWindow();
            assertEquals("Status bar color must be transparent.",
                    Color.TRANSPARENT, Color.alpha(window.getStatusBarColor()));
            assertEquals("Navigation bar color must be transparent.",
                    Color.TRANSPARENT, window.getNavigationBarColor());
            assertEquals("Navigation bar divider color must be transparent.",
                    Color.TRANSPARENT, window.getNavigationBarDividerColor());

            window.setStatusBarColor(Color.BLACK);
            assertEquals("Status bar color must not be changed.",
                    Color.TRANSPARENT, window.getStatusBarColor());
            window.setNavigationBarColor(Color.BLACK);
            assertEquals("Navigation bar color must not be changed.",
                    Color.TRANSPARENT, window.getNavigationBarColor());
            window.setNavigationBarDividerColor(Color.BLACK);
            assertEquals("Navigation bar divider color not be changed.",
                    Color.TRANSPARENT, window.getNavigationBarDividerColor());
        });
    }

    private static TestActivity startTestActivitySync() {
        final TestActivity activity = startActivity(TestActivity.class);
        activity.waitForLayout();
        return activity;
    }

    public static class TestActivity extends FocusableActivity {

        private static final int LAYOUT_IN_DISPLAY_CUTOUT_MODE_UNSPECIFIED = -1;

        private static int sStyleId = ID_NULL;
        private static int sLayoutInDisplayCutoutMode = -1;
        private final CountDownLatch mLayoutLatch = new CountDownLatch(1);
        private ContentView mContentView;

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            if (sStyleId != ID_NULL) {
                getTheme().applyStyle(sStyleId, true /* force */);
            }
            if (sLayoutInDisplayCutoutMode != LAYOUT_IN_DISPLAY_CUTOUT_MODE_UNSPECIFIED) {
                getWindow().getAttributes().layoutInDisplayCutoutMode = sLayoutInDisplayCutoutMode;
            }
            mContentView = new ContentView(this);
            mContentView.getViewTreeObserver().addOnGlobalLayoutListener(mLayoutLatch::countDown);
            setContentView(mContentView);
        }

        private void waitForLayout() {
            final String errorMessage = "Unable to wait for layout.";
            try {
                assertTrue(errorMessage, mLayoutLatch.await(3, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
                fail(errorMessage);
            }
        }

        private static class ContentView extends View {

            private WindowInsets mWindowInsets;

            private ContentView(Context context) {
                super(context);
            }

            @Override
            public WindowInsets onApplyWindowInsets(WindowInsets insets) {
                mWindowInsets = insets;
                return super.onApplyWindowInsets(insets);
            }
        }

    }
}
