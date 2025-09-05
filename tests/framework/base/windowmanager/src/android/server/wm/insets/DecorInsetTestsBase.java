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

package android.server.wm.insets;

import static org.junit.Assert.assertEquals;

import android.app.Activity;
import android.app.ActivityOptions;
import android.app.WindowConfiguration;
import android.content.Intent;
import android.graphics.Insets;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowInsets;

import androidx.annotation.Nullable;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public class DecorInsetTestsBase {

    public static final String ARG_DECOR_FITS_SYSTEM_WINDOWS = "decorFitsSystemWindows";
    public static final String ARG_LAYOUT_STABLE = "flagLayoutStable";
    public static final String ARG_LAYOUT_FULLSCREEN = "flagLayoutFullscreen";
    public static final String ARG_LAYOUT_HIDE_NAV = "flagLayoutHideNav";

    protected TestActivity mActivity;

    public static class TestActivity extends Activity {
        WindowInsets mLastContentInsets;
        WindowInsets mLastDecorInsets;
        final CountDownLatch mLaidOut = new CountDownLatch(1);

        @Override
        protected void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            getWindow().requestFeature(Window.FEATURE_NO_TITLE);

            getWindow().setDecorFitsSystemWindows(
                    getIntent().getBooleanExtra(ARG_DECOR_FITS_SYSTEM_WINDOWS, false));

            View view = new View(this);
            view.setSystemUiVisibility(intentToSysuiVisibility(getIntent()));

            view.setOnApplyWindowInsetsListener((v, wi) -> {
                mLastContentInsets = wi;
                return WindowInsets.CONSUMED;
            });
            setContentView(view);
            getWindow().getDecorView().setOnApplyWindowInsetsListener((v, wi) -> {
                mLastDecorInsets = wi;
                return v.onApplyWindowInsets(wi);
            });

            view.getViewTreeObserver().addOnGlobalLayoutListener(
                    new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    view.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    mLaidOut.countDown();
                }
            });
        }

        public WindowInsets getLastContentInsets() {
            return mLastContentInsets;
        }

        public WindowInsets getLastDecorInsets() {
            return mLastDecorInsets;
        }

        public CountDownLatch getLaidOut() {
            return mLaidOut;
        }

        private static int intentToSysuiVisibility(Intent intent) {
            int vis = 0;
            vis |= intent.getBooleanExtra(ARG_LAYOUT_HIDE_NAV, false)
                    ? View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION : 0;
            vis |= intent.getBooleanExtra(ARG_LAYOUT_FULLSCREEN, false)
                    ? View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN : 0;
            vis |= intent.getBooleanExtra(ARG_LAYOUT_STABLE, false)
                    ? View.SYSTEM_UI_FLAG_LAYOUT_STABLE : 0;
            return vis;
        }
    }

    protected ActivityScenario<TestActivity> launchTestActivityInFullscreen(Intent intent) {
        final String targetPackage =
                InstrumentationRegistry.getInstrumentation().getTargetContext().getPackageName();
        intent.setClassName(targetPackage, TestActivity.class.getName());

        final ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchWindowingMode(WindowConfiguration.WINDOWING_MODE_FULLSCREEN);
        final ActivityScenario<TestActivity> scenario =
                ActivityScenario.launch(intent, options.toBundle());
        final AtomicReference<TestActivity> activity = new AtomicReference<>();
        scenario.moveToState(Lifecycle.State.RESUMED).onActivity(activity::set);
        mActivity = activity.get();
        return scenario;
    }

    protected void assertContentViewLocationMatchesInsets() {
        Insets insetsConsumedByDecor =
                Insets.subtract(
                        systemWindowInsetsOrZero(mActivity.mLastDecorInsets),
                        systemWindowInsetsOrZero(mActivity.mLastContentInsets));
        Rect expectedContentRect = rectInWindow(mActivity.getWindow().getDecorView());
        insetRect(expectedContentRect, insetsConsumedByDecor);

        Rect actualContentRect = rectInWindow(mActivity.findViewById(android.R.id.content));

        assertEquals("Decor consumed " + insetsConsumedByDecor + ", content rect:",
                expectedContentRect, actualContentRect);
    }

    public Insets systemWindowInsetsOrZero(WindowInsets wi) {
        if (wi == null) {
            return Insets.NONE;
        }
        return wi.getSystemWindowInsets();
    }

    private Rect rectInWindow(View view) {
        int[] loc = new int[2];
        view.getLocationInWindow(loc);
        return new Rect(loc[0], loc[1], loc[0] + view.getWidth(), loc[1] + view.getHeight());
    }

    private static void insetRect(Rect rect, Insets insets) {
        rect.left += insets.left;
        rect.top += insets.top;
        rect.right -= insets.right;
        rect.bottom -= insets.bottom;
    }
}
