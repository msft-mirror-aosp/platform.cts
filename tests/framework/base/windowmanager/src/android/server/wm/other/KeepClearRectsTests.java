/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.server.wm.other;

import static android.server.wm.app.Components.KEEP_CLEAR_RECTS_ACTIVITY;
import static android.server.wm.app.Components.KEEP_CLEAR_RECTS_ACTIVITY2;
import static android.server.wm.app.Components.KeepClearRectsActivity.EXTRA_KEEP_CLEAR_RECTS;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import static java.util.Collections.EMPTY_LIST;

import android.accessibility.cts.common.InstrumentedAccessibilityServiceTestRule;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Bundle;
import android.platform.test.annotations.Presubmit;
import android.server.wm.LaunchActivityBuilder;
import android.server.wm.WindowManagerState;
import android.server.wm.WindowManagerTestBase;
import android.server.wm.cts.R;
import android.view.Gravity;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.RelativeLayout;
import android.widget.RelativeLayout.LayoutParams;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.PollingCheck;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;

@Presubmit
public class KeepClearRectsTests extends WindowManagerTestBase {
    private static final long SAME_ELEMENT_ASSERTION_TIMEOUT = 3000;
    private static final List<Rect> TEST_KEEP_CLEAR_RECTS =
            Arrays.asList(new Rect(0, 0, 25, 25),
                          new Rect(30, 0, 50, 25),
                          new Rect(25, 25, 50, 50),
                          new Rect(10, 30, 20, 50));
    private static final List<Rect> TEST_KEEP_CLEAR_RECTS_2 =
            Arrays.asList(new Rect(55, 0, 75, 15),
                          new Rect(50, 15, 60, 25),
                          new Rect(75, 25, 90, 50),
                          new Rect(90, 0, 100, 10));
    private static final Rect TEST_VIEW_BOUNDS = new Rect(0, 0, 100, 100);
    private static final String USE_KEEP_CLEAR_ATTR_LAYOUT = "use_keep_clear_attr_layout";

    private TestActivitySession<TestActivity> mTestSession;

    private int mLaunchDisplayId;

    @Rule
    public InstrumentedAccessibilityServiceTestRule<AccessibilityTestService>
            mAccessibilityServiceRule = new InstrumentedAccessibilityServiceTestRule<>(
            AccessibilityTestService.class, false);

    @Before
    public void setUp() throws Exception {
        super.setUp();
        mTestSession = createManagedTestActivitySession();
        mLaunchDisplayId = getMainDisplayId();

        // Wait for any animations to be finished.
        mInstrumentation.getUiAutomation().syncInputTransactions();
    }

    @After
    public void tearDown() throws Exception {
        mWmState.setSuppressAccessibilityServices(true);
    }

    @Test
    @ApiTest(apis = {"android.view.View#setPreferKeepClear"})
    public void testSetPreferKeepClearAttr() throws Exception {
        final Intent intent = new Intent(mContext, TestActivity.class);
        intent.putExtra(USE_KEEP_CLEAR_ATTR_LAYOUT, true);
        mTestSession.launchTestActivityOnDisplaySync(null, intent, mLaunchDisplayId);
        final TestActivity activity = mTestSession.getActivity();

        // To be kept in sync with res/layout/keep_clear_attr_activity
        final Rect keepClearRect = new Rect();
        getViewBoundsInWindow(activity.findViewById(R.id.keepClearView), keepClearRect);

        assertSameElementsEventually(Arrays.asList(keepClearRect),
                () -> getKeepClearRectsForActivity(activity));
    }

    @Test
    @ApiTest(apis = {"android.view.View#setPreferKeepClear"})
    public void testSetPreferKeepClearSingleView() throws Exception {
        mTestSession.launchTestActivityOnDisplaySync(TestActivity.class, mLaunchDisplayId);
        final TestActivity activity = mTestSession.getActivity();

        final Rect keepClearRect = new Rect(0, 0, 25, 25);
        final Rect outViewBounds = new Rect();
        final View v = createTestViewInActivity(activity, keepClearRect, outViewBounds);
        mTestSession.runOnMainSyncAndWait(() -> v.setPreferKeepClear(true));

        assertSameElementsEventually(
                Arrays.asList(outViewBounds), () -> getKeepClearRectsForActivity(activity));
    }

    @Test
    @ApiTest(apis = {"android.view.View#setPreferKeepClear"})
    public void testSetPreferKeepClearTwoViews() throws Exception {
        mTestSession.launchTestActivityOnDisplaySync(TestActivity.class, mLaunchDisplayId);
        final TestActivity activity = mTestSession.getActivity();

        final Rect keepClearRect = new Rect(0, 0, 25, 25);
        final Rect outViewBounds = new Rect();
        final View v = createTestViewInActivity(activity, keepClearRect, outViewBounds);
        mTestSession.runOnMainSyncAndWait(() -> v.setPreferKeepClear(true));

        final List<Rect> expected = new ArrayList<>(Arrays.asList(outViewBounds));
        assertSameElementsEventually(expected, () -> getKeepClearRectsForActivity(activity));

        final Rect keepClearRect2 = new Rect(25, 25, 50, 50);
        final Rect outViewBounds2 = new Rect();
        final View v2 = createTestViewInActivity(activity, keepClearRect2, outViewBounds2);
        mTestSession.runOnMainSyncAndWait(() -> v2.setPreferKeepClear(true));

        expected.addAll(Arrays.asList(outViewBounds2));
        assertSameElementsEventually(expected, () -> getKeepClearRectsForActivity(activity));
    }

    @Test
    @ApiTest(apis = {"android.view.View#setPreferKeepClearRects"})
    public void testSetMultipleKeepClearRectsSingleView() throws Exception {
        mTestSession.launchTestActivityOnDisplaySync(TestActivity.class, mLaunchDisplayId);
        final TestActivity activity = mTestSession.getActivity();

        final View v = createTestViewInActivity(activity);
        mTestSession.runOnMainSyncAndWait(() -> v.setPreferKeepClearRects(TEST_KEEP_CLEAR_RECTS));

        assertSameElementsEventually(
                offsetRectsToLocationInWindow(TEST_KEEP_CLEAR_RECTS, v),
                () -> getKeepClearRectsForActivity(activity));
    }

    @Test
    @ApiTest(apis = {"android.view.View#setPreferKeepClearRects"})
    public void testSetMultipleKeepClearRectsTwoViews() throws Exception {
        mTestSession.launchTestActivityOnDisplaySync(TestActivity.class, mLaunchDisplayId);
        final TestActivity activity = mTestSession.getActivity();

        final View v1 = createTestViewInActivity(activity);
        final View v2 = createTestViewInActivity(activity);
        mTestSession.runOnMainSyncAndWait(() -> {
            v1.setPreferKeepClearRects(TEST_KEEP_CLEAR_RECTS);
            v2.setPreferKeepClearRects(TEST_KEEP_CLEAR_RECTS_2);
        });

        final List<Rect> expected =
                new ArrayList<>(offsetRectsToLocationInWindow(TEST_KEEP_CLEAR_RECTS, v1));
        expected.addAll(offsetRectsToLocationInWindow(TEST_KEEP_CLEAR_RECTS_2, v2));
        assertSameElementsEventually(expected, () -> getKeepClearRectsForActivity(activity));
    }

    @Test
    @ApiTest(apis = {"android.view.View#setPreferKeepClear",
                     "android.view.View#isPreferKeepClear"})
    public void testIsPreferKeepClearSingleView() {
        mTestSession.launchTestActivityOnDisplaySync(TestActivity.class, mLaunchDisplayId);
        final TestActivity activity = mTestSession.getActivity();

        final Rect viewBounds = new Rect(0, 0, 60, 60);
        final View v = createTestViewInActivity(activity, viewBounds);
        assertFalse(v.isPreferKeepClear());
        mTestSession.runOnMainSyncAndWait(() -> v.setPreferKeepClear(true));
        assertTrue(v.isPreferKeepClear());

        mTestSession.runOnMainSyncAndWait(() -> v.setPreferKeepClear(false));
        assertFalse(v.isPreferKeepClear());
    }

    @Test
    @ApiTest(apis = {"android.view.View#getPreferKeepClearRects",
                     "android.view.View#setPreferKeepClearRects"})
    public void testGetPreferKeepClearRectsSingleView() throws Exception {
        mTestSession.launchTestActivityOnDisplaySync(TestActivity.class, mLaunchDisplayId);
        final TestActivity activity = mTestSession.getActivity();

        final Rect viewBounds = new Rect(0, 0, 60, 60);
        final View v = createTestViewInActivity(activity, viewBounds);
        assertSameElementsEventually(EMPTY_LIST, () -> v.getPreferKeepClearRects());
        mTestSession.runOnMainSyncAndWait(() -> v.setPreferKeepClearRects(TEST_KEEP_CLEAR_RECTS));
        assertSameElementsEventually(TEST_KEEP_CLEAR_RECTS, () -> v.getPreferKeepClearRects());

        mTestSession.runOnMainSyncAndWait(() -> v.setPreferKeepClearRects(TEST_KEEP_CLEAR_RECTS_2));
        assertSameElementsEventually(TEST_KEEP_CLEAR_RECTS_2, () -> v.getPreferKeepClearRects());

        mTestSession.runOnMainSyncAndWait(() -> v.setPreferKeepClearRects(EMPTY_LIST));
        assertSameElementsEventually(EMPTY_LIST, () -> v.getPreferKeepClearRects());
    }

    @Test
    @ApiTest(apis = {"android.view.View#setPreferKeepClearRects",
                     "android.view.View#setPreferKeepClear",
                     "android.view.View#getPreferKeepClearRects",
                     "android.view.View#isPreferKeepClear"})
    public void testGettersPreferKeepClearRectsTwoViews() throws Exception {
        mTestSession.launchTestActivityOnDisplaySync(TestActivity.class, mLaunchDisplayId);
        final TestActivity activity = mTestSession.getActivity();

        final Rect viewBounds1 = new Rect(0, 0, 60, 60);
        final Rect viewBounds2 = new Rect(0, 0, 90, 90);
        final View v1 = createTestViewInActivity(activity, viewBounds1);
        final View v2 = createTestViewInActivity(activity, viewBounds2);
        mTestSession.runOnMainSyncAndWait(() -> {
            v1.setPreferKeepClear(true);
            v2.setPreferKeepClearRects(TEST_KEEP_CLEAR_RECTS);
        });

        assertTrue(v1.isPreferKeepClear());
        assertSameElementsEventually(TEST_KEEP_CLEAR_RECTS, () -> v2.getPreferKeepClearRects());

        mTestSession.runOnMainSyncAndWait(() -> v1.setPreferKeepClear(false));
        assertFalse(v1.isPreferKeepClear());
        assertSameElementsEventually(TEST_KEEP_CLEAR_RECTS, () -> v2.getPreferKeepClearRects());

        mTestSession.runOnMainSyncAndWait(() -> {
            v1.setPreferKeepClear(true);
            v2.setPreferKeepClearRects(EMPTY_LIST);
        });
        assertTrue(v1.isPreferKeepClear());
        assertSameElementsEventually(EMPTY_LIST, () -> v2.getPreferKeepClearRects());
    }

    @Test
    @ApiTest(apis = {"android.view.View#setPreferKeepClearRects",
                     "android.view.View#setPreferKeepClear"})
    public void testSetPreferKeepClearCombinesWithMultipleRects() throws Exception {
        mTestSession.launchTestActivityOnDisplaySync(TestActivity.class, mLaunchDisplayId);
        final TestActivity activity = mTestSession.getActivity();

        final Rect viewBounds = new Rect(0, 0, 60, 60);
        final Rect outViewBounds = new Rect();
        final View v = createTestViewInActivity(activity, viewBounds, outViewBounds);
        mTestSession.runOnMainSyncAndWait(() -> v.setPreferKeepClearRects(TEST_KEEP_CLEAR_RECTS));
        List<Rect> offsetTestKeepClearRect =
                offsetRectsToLocationInWindow(TEST_KEEP_CLEAR_RECTS, v);
        assertSameElementsEventually(
                offsetTestKeepClearRect, () -> getKeepClearRectsForActivity(activity));

        mTestSession.runOnMainSyncAndWait(() -> v.setPreferKeepClear(true));
        final List<Rect> combinedRects = new ArrayList<>(offsetTestKeepClearRect);
        combinedRects.add(outViewBounds);
        assertSameElementsEventually(combinedRects, () -> getKeepClearRectsForActivity(activity));

        mTestSession.runOnMainSyncAndWait(() -> v.setPreferKeepClear(false));
        assertSameElementsEventually(
                offsetTestKeepClearRect, () -> getKeepClearRectsForActivity(activity));
    }

    @Test
    @ApiTest(apis = {"android.view.View#setPreferKeepClearRects",
                     "android.view.View#setPreferKeepClear"})
    public void testIgnoreKeepClearRectsFromGoneViews() throws Exception {
        mTestSession.launchTestActivityOnDisplaySync(TestActivity.class, mLaunchDisplayId);
        final TestActivity activity = mTestSession.getActivity();

        final Rect viewBounds = new Rect(0, 0, 60, 60);
        final Rect outViewBounds = new Rect();
        final View v = createTestViewInActivity(activity, viewBounds, outViewBounds);
        List<Rect> offsetTestKeepClearRect =
                offsetRectsToLocationInWindow(TEST_KEEP_CLEAR_RECTS, v);

        mTestSession.runOnMainSyncAndWait(() -> v.setPreferKeepClear(true));
        assertSameElementsEventually(
                Arrays.asList(outViewBounds), () -> getKeepClearRectsForActivity(activity));

        mTestSession.runOnMainSyncAndWait(() -> v.setVisibility(View.GONE));
        assertSameElementsEventually(EMPTY_LIST, () -> getKeepClearRectsForActivity(activity));

        mTestSession.runOnMainSyncAndWait(() -> v.setVisibility(View.VISIBLE));
        assertSameElementsEventually(
                Arrays.asList(outViewBounds), () -> getKeepClearRectsForActivity(activity));

        mTestSession.runOnMainSyncAndWait(() -> {
            v.setPreferKeepClear(false);
            v.setPreferKeepClearRects(TEST_KEEP_CLEAR_RECTS);
        });
        assertSameElementsEventually(
                offsetTestKeepClearRect, () -> getKeepClearRectsForActivity(activity));

        mTestSession.runOnMainSyncAndWait(() -> v.setVisibility(View.GONE));
        assertSameElementsEventually(EMPTY_LIST, () -> getKeepClearRectsForActivity(activity));

        mTestSession.runOnMainSyncAndWait(() -> v.setVisibility(View.VISIBLE));
        assertSameElementsEventually(
                offsetTestKeepClearRect, () -> getKeepClearRectsForActivity(activity));

        final Rect viewBounds2 = new Rect(60, 60, 90, 90);
        final Rect outViewBounds2 = new Rect();
        final View v2 = createTestViewInActivity(activity, viewBounds2, outViewBounds2);

        mTestSession.runOnMainSyncAndWait(() -> v2.setPreferKeepClear(true));

        final List<Rect> expected = new ArrayList<>(offsetTestKeepClearRect);
        expected.add(outViewBounds2);
        assertSameElementsEventually(expected, () -> getKeepClearRectsForActivity(activity));

        mTestSession.runOnMainSyncAndWait(() -> v.setVisibility(View.GONE));
        assertSameElementsEventually(
                Arrays.asList(outViewBounds2), () -> getKeepClearRectsForActivity(activity));

        mTestSession.runOnMainSyncAndWait(() -> {
            v.setVisibility(View.VISIBLE);
            v2.setVisibility(View.GONE);
        });
        assertSameElementsEventually(
                offsetTestKeepClearRect, () -> getKeepClearRectsForActivity(activity));

        mTestSession.runOnMainSyncAndWait(() -> {
            v.setVisibility(View.VISIBLE);
            v2.setVisibility(View.VISIBLE);
        });
        assertSameElementsEventually(expected, () -> getKeepClearRectsForActivity(activity));
    }

    @Test
    @ApiTest(apis = {"android.view.View#setPreferKeepClear"})
    public void testIgnoreKeepClearRectsFromDetachedViews() throws Exception {
        mTestSession.launchTestActivityOnDisplaySync(TestActivity.class, mLaunchDisplayId);
        final TestActivity activity = mTestSession.getActivity();

        final Rect viewBounds = new Rect(0, 0, 60, 60);
        final Rect outViewBounds = new Rect();
        final View v = createTestViewInActivity(activity, viewBounds, outViewBounds);
        mTestSession.runOnMainSyncAndWait(() -> v.setPreferKeepClear(true));
        assertSameElementsEventually(
                Arrays.asList(outViewBounds), () -> getKeepClearRectsForActivity(activity));

        mTestSession.runOnMainSyncAndWait(() -> ((ViewGroup) v.getParent()).removeView(v));
        assertSameElementsEventually(EMPTY_LIST, () -> getKeepClearRectsForActivity(activity));
    }

    @Test
    public void testFocusedViewDeclaredAsKeepClearArea() throws Exception {
        assumeTrue(ViewConfiguration.get(mContext).isPreferKeepClearForFocusEnabled());

        mTestSession.launchTestActivityOnDisplaySync(TestActivity.class, mLaunchDisplayId);
        final TestActivity activity = mTestSession.getActivity();

        final Rect viewBounds = new Rect(0, 0, 60, 60);
        final View v = createTestViewInActivity(activity, viewBounds);
        assertSameElementsEventually(EMPTY_LIST, () -> getKeepClearRectsForActivity(activity));

        mTestSession.runOnMainSyncAndWait(() -> {
            v.setFocusableInTouchMode(true);
            v.setFocusable(true);
            v.requestFocus();
        });

        assertSameElementsEventually(Arrays.asList(viewBounds),
                () -> getKeepClearRectsForActivity(activity));

        mTestSession.runOnMainSyncAndWait(() -> v.setFocusable(false));
        assertSameElementsEventually(EMPTY_LIST, () -> getKeepClearRectsForActivity(activity));
    }

    @Test
    @ApiTest(apis = {"android.view.View#setPreferKeepClearRects"})
    public void testKeepClearRectsGetTranslatedToWindowSpace() throws Exception {
        mTestSession.launchTestActivityOnDisplaySync(TestActivity.class, mLaunchDisplayId);
        final TestActivity activity = mTestSession.getActivity();

        final Rect viewBounds = new Rect(30, 30, 60, 60);
        final Rect outViewBounds = new Rect();
        final View v = createTestViewInActivity(activity, viewBounds, outViewBounds);
        mTestSession.runOnMainSyncAndWait(() -> v.setPreferKeepClearRects(TEST_KEEP_CLEAR_RECTS));
        final List<Rect> expected = new ArrayList<>();
        for (Rect r : TEST_KEEP_CLEAR_RECTS) {
            Rect newRect = new Rect(r);
            newRect.offset(outViewBounds.left, outViewBounds.top);
            expected.add(newRect);
        }

        assertSameElementsEventually(expected, () -> getKeepClearRectsForActivity(activity));
    }

    @Test
    @ApiTest(apis = {"android.view.View#setPreferKeepClear",
                     "android.view.View#setPreferKeepClearRects"})
    public void testSetKeepClearRectsOnDisplaySingleWindow() throws Exception {
        mTestSession.launchTestActivityOnDisplaySync(TestActivity.class, mLaunchDisplayId);
        final TestActivity activity = mTestSession.getActivity();

        final Rect keepClearRect = new Rect(0, 0, 25, 25);
        final Rect outViewBounds = new Rect();
        final View v = createTestViewInActivity(activity, keepClearRect, outViewBounds);
        final List<Rect> prevKeepClearRectsOnDisplay = getKeepClearRectsOnDefaultDisplay();
        mTestSession.runOnMainSyncAndWait(() -> v.setPreferKeepClear(true));
        assertSameElementsEventually(
                Arrays.asList(outViewBounds), () -> getKeepClearRectsForActivity(activity));

        mTestSession.runOnMainSyncAndWait(() -> {
            v.setPreferKeepClear(false);
            v.setPreferKeepClearRects(TEST_KEEP_CLEAR_RECTS);
        });
        List<Rect> offsetTestKeepClearRect =
                offsetRectsToLocationInWindow(TEST_KEEP_CLEAR_RECTS, v);
        assertSameElementsEventually(
                offsetTestKeepClearRect, () -> getKeepClearRectsForActivity(activity));

        final List<Rect> expectedRectsOnDisplay = new ArrayList<Rect>();
        expectedRectsOnDisplay.addAll(prevKeepClearRectsOnDisplay);
        expectedRectsOnDisplay.addAll(
                getRectsInScreenSpace(offsetTestKeepClearRect, activity.getComponentName()));
        assertSameElementsEventually(expectedRectsOnDisplay,
                () -> getKeepClearRectsOnDefaultDisplay());
    }

    @Test
    @ApiTest(apis = {"android.view.View#setPreferKeepClearRects"})
    public void testFinishingActivityRemovesItsKeepClearRects() throws Exception {
        final List<Rect> prevKeepClearRectsOnDisplay = getKeepClearRectsOnDefaultDisplay();

        mTestSession.launchTestActivityOnDisplaySync(TestActivity.class, mLaunchDisplayId);
        final TestActivity activity = mTestSession.getActivity();

        final Rect viewBounds = new Rect(0, 0, 60, 60);
        final View v = createTestViewInActivity(activity, viewBounds);
        mTestSession.runOnMainSyncAndWait(() -> v.setPreferKeepClearRects(TEST_KEEP_CLEAR_RECTS));
        List<Rect> offsetTestKeepClearRect =
                offsetRectsToLocationInWindow(TEST_KEEP_CLEAR_RECTS, v);
        assertSameElementsEventually(
                offsetTestKeepClearRect, () -> getKeepClearRectsForActivity(activity));

        activity.finishAndRemoveTask();
        assertSameElementsEventually(prevKeepClearRectsOnDisplay,
                () -> getKeepClearRectsOnDefaultDisplay());
    }

    @Test
    @ApiTest(apis = {"android.view.View#setPreferKeepClear"})
    public void testKeepClearRectsOnDisplayTwoWindows() throws Exception {
        mTestSession.launchTestActivityOnDisplaySync(TestActivity.class, mLaunchDisplayId);
        final TestActivity activity = mTestSession.getActivity();

        final Rect viewBounds = new Rect(0, 0, 25, 25);
        final Rect outViewBounds = new Rect();
        final View v1 = createTestViewInActivity(activity, viewBounds, outViewBounds);
        mTestSession.runOnMainSyncAndWait(() -> v1.setPreferKeepClear(true));
        assertSameElementsEventually(
                Arrays.asList(outViewBounds), () -> getKeepClearRectsForActivity(activity));

        final String title = "KeepClearRectsTestWindow";
        mTestSession.runOnMainSyncAndWait(() -> {
            final View testView = new View(activity);
            testView.setPreferKeepClear(true);
            testView.setBackgroundColor(Color.argb(20, 255, 0, 0));
            WindowManager.LayoutParams params = new WindowManager.LayoutParams();
            params.gravity = Gravity.TOP | Gravity.START;
            params.width = 50;
            params.height = 50;
            params.setTitle(title);
            activity.getWindowManager().addView(testView, params);
        });
        mWmState.waitAndAssertWindowSurfaceShown(title, true);

        assertSameElementsEventually(
                Arrays.asList(outViewBounds), () -> getKeepClearRectsForActivity(activity));
    }

    @Test
    @ApiTest(apis = {"android.view.View#setPreferKeepClear",
                     "android.view.View#setPreferKeepClearRects"})
    public void testKeepClearRectsOnDisplayTwoFullscreenActivities() throws Exception {
        mTestSession.launchTestActivityOnDisplaySync(TestActivity.class, mLaunchDisplayId);
        final TestActivity activity1 = mTestSession.getActivity();

        final Rect viewBounds = new Rect(0, 0, 25, 25);
        final Rect outViewBounds = new Rect();
        final View v1 = createTestViewInActivity(activity1, viewBounds, outViewBounds);
        final List<Rect> prevKeepClearRectsOnDisplay = getKeepClearRectsOnDefaultDisplay();
        mTestSession.runOnMainSyncAndWait(() -> v1.setPreferKeepClear(true));
        assertSameElementsEventually(
                Arrays.asList(outViewBounds), () -> getKeepClearRectsForActivity(activity1));

        final TestActivitySession<TranslucentTestActivity> translucentTestSession =
                createManagedTestActivitySession();
        translucentTestSession.launchTestActivityOnDisplaySync(
                TranslucentTestActivity.class, mLaunchDisplayId);
        final TestActivity activity2 = translucentTestSession.getActivity();

        final View v2 = createTestViewInActivity(activity2);
        mTestSession.runOnMainSyncAndWait(() -> v2.setPreferKeepClearRects(TEST_KEEP_CLEAR_RECTS));
        List<Rect> offsetTestKeepClearRect =
                offsetRectsToLocationInWindow(TEST_KEEP_CLEAR_RECTS, v2);
        assertSameElementsEventually(
                offsetTestKeepClearRect, () -> getKeepClearRectsForActivity(activity2));

        mWmState.assertVisibility(activity1.getComponentName(), true);
        mWmState.assertVisibility(activity2.getComponentName(), true);

        // Since both activities are fullscreen, WM only takes the keep clear areas from the top one
        final List<Rect> expectedRectsOnDisplay = new ArrayList<Rect>();
        expectedRectsOnDisplay.addAll(prevKeepClearRectsOnDisplay);
        expectedRectsOnDisplay.addAll(
                getRectsInScreenSpace(offsetTestKeepClearRect, activity2.getComponentName()));
        assertSameElementsEventually(expectedRectsOnDisplay,
                () -> getKeepClearRectsOnDefaultDisplay());
    }

    @Test
    @ApiTest(apis = {"android.view.View#setPreferKeepClear",
                     "android.view.View#setPreferKeepClearRects"})
    public void testDisplayHasKeepClearRectsOnlyFromVisibleWindows() throws Exception {
        final TestActivitySession<TranslucentTestActivity> translucentTestSession =
                createManagedTestActivitySession();
        translucentTestSession.launchTestActivityOnDisplaySync(
                TranslucentTestActivity.class, mLaunchDisplayId);
        final TestActivity activity1 = translucentTestSession.getActivity();
        final Rect viewBounds = new Rect(0, 0, 25, 25);
        final Rect outViewBounds = new Rect();
        final View v1 = createTestViewInActivity(activity1, viewBounds, outViewBounds);
        final List<Rect> prevKeepClearRectsOnDisplay = getKeepClearRectsOnDefaultDisplay();
        translucentTestSession.runOnMainSyncAndWait(() -> v1.setPreferKeepClear(true));

        // Add keep-clear rects in the activity
        final List<Rect> expectedRectsOnDisplay = new ArrayList<Rect>();
        expectedRectsOnDisplay.addAll(prevKeepClearRectsOnDisplay);
        expectedRectsOnDisplay.addAll(
                getRectsInScreenSpace(Arrays.asList(outViewBounds), activity1.getComponentName()));
        assertSameElementsEventually(expectedRectsOnDisplay,
                () -> getKeepClearRectsOnDefaultDisplay());

        // Start an opaque activity on top
        mTestSession.launchTestActivityOnDisplaySync(TestActivity.class, mLaunchDisplayId);
        final TestActivity activity2 = mTestSession.getActivity();

        // Add keep-clear rects in the opaque activity
        final View v2 = createTestViewInActivity(activity2);
        mTestSession.runOnMainSyncAndWait(() -> v2.setPreferKeepClearRects(TEST_KEEP_CLEAR_RECTS));
        List<Rect> offsetTestKeepClearRect =
                offsetRectsToLocationInWindow(TEST_KEEP_CLEAR_RECTS, v2);
        assertSameElementsEventually(
                offsetTestKeepClearRect, () -> getKeepClearRectsForActivity(activity2));

        mWmState.waitAndAssertVisibilityGone(activity1.getComponentName());
        mWmState.assertVisibility(activity2.getComponentName(), true);

        // Only the opaque activity's keep-clear areas should be reported on the display
        expectedRectsOnDisplay.clear();
        expectedRectsOnDisplay.addAll(prevKeepClearRectsOnDisplay);
        expectedRectsOnDisplay.addAll(
                getRectsInScreenSpace(offsetTestKeepClearRect, activity2.getComponentName()));
        assertSameElementsEventually(expectedRectsOnDisplay,
                () -> getKeepClearRectsOnDefaultDisplay());
    }

    @Test
    @ApiTest(apis = {"android.view.View#setPreferKeepClearRects"})
    public void testDisplayHasKeepClearAreasFromTwoActivitiesInSplitscreen() throws Exception {
        assumeTrue("Skipping test: no split multi-window support",
                supportsSplitScreenMultiWindow());

        startKeepClearActivitiesInSplitscreen(KEEP_CLEAR_RECTS_ACTIVITY,
                KEEP_CLEAR_RECTS_ACTIVITY2, Collections.emptyList(), Collections.emptyList());
        final List<Rect> prevKeepClearRectsOnDisplay = getKeepClearRectsOnDefaultDisplay();

        removeRootTask(mWmState.getTaskByActivity(KEEP_CLEAR_RECTS_ACTIVITY).getTaskId());
        removeRootTask(mWmState.getTaskByActivity(KEEP_CLEAR_RECTS_ACTIVITY2).getTaskId());

        startKeepClearActivitiesInSplitscreen(KEEP_CLEAR_RECTS_ACTIVITY,
                KEEP_CLEAR_RECTS_ACTIVITY2, TEST_KEEP_CLEAR_RECTS, TEST_KEEP_CLEAR_RECTS_2);

        assertSameElementsEventually(TEST_KEEP_CLEAR_RECTS,
                () -> getKeepClearRectsForActivity(KEEP_CLEAR_RECTS_ACTIVITY));
        assertSameElementsEventually(TEST_KEEP_CLEAR_RECTS_2,
                () -> getKeepClearRectsForActivity(KEEP_CLEAR_RECTS_ACTIVITY2));

        final List<Rect> expected = new ArrayList<>();
        expected.addAll(prevKeepClearRectsOnDisplay);
        expected.addAll(getRectsInScreenSpace(TEST_KEEP_CLEAR_RECTS, KEEP_CLEAR_RECTS_ACTIVITY));
        expected.addAll(getRectsInScreenSpace(TEST_KEEP_CLEAR_RECTS_2, KEEP_CLEAR_RECTS_ACTIVITY2));
        assertSameElementsEventually(expected, () -> getKeepClearRectsOnDefaultDisplay());
    }

    private void startKeepClearActivitiesInSplitscreen(ComponentName activity1,
            ComponentName activity2, List<Rect> keepClearRects1, List<Rect> keepClearRects2) {
        final LaunchActivityBuilder activityBuilder1 =
                getLaunchActivityBuilder()
                        .setUseInstrumentation()
                        .setTargetActivity(activity1)
                        .setIntentExtra(
                                extra -> {
                                    extra.putParcelableArrayList(
                                            EXTRA_KEEP_CLEAR_RECTS,
                                            new ArrayList<>(keepClearRects1));
                                });

        final LaunchActivityBuilder activityBuilder2 =
                getLaunchActivityBuilder()
                        .setUseInstrumentation()
                        .setTargetActivity(activity2)
                        .setIntentExtra(
                                extra -> {
                                    extra.putParcelableArrayList(
                                            EXTRA_KEEP_CLEAR_RECTS,
                                            new ArrayList<>(keepClearRects2));
                                });

        launchActivitiesInSplitScreen(activityBuilder1, activityBuilder2);

        waitAndAssertResumedActivity(activity1, activity1 + " must be resumed");
        waitAndAssertResumedActivity(activity2, activity2 + " must be resumed");
        mWmState.assertVisibility(activity1, true);
        mWmState.assertVisibility(activity2, true);
    }

    @Test
    @ApiTest(apis = {"android.view.View#setUnrestrictedPreferKeepClearRects",
                     "android.view.View#setPreferKeepClearRects"})
    public void testUnrestrictedKeepClearRects() throws Exception {
        mTestSession.launchTestActivityOnDisplaySync(TestActivity.class, mLaunchDisplayId);
        final TestActivity activity = mTestSession.getActivity();

        final View v = createTestViewInActivity(activity);
        mTestSession.runOnMainSyncAndWait(() -> {
            v.setUnrestrictedPreferKeepClearRects(TEST_KEEP_CLEAR_RECTS);
        });

        assertSameElementsEventually(EMPTY_LIST, () -> getKeepClearRectsForActivity(activity));
        assertSameElementsEventually(EMPTY_LIST,
                () -> getUnrestrictedKeepClearRectsForActivity(activity));

        mTestSession.runOnMainSyncAndWait(() -> {
            v.setPreferKeepClearRects(TEST_KEEP_CLEAR_RECTS);
        });
        List<Rect> offsetTestKeepClearRect =
                offsetRectsToLocationInWindow(TEST_KEEP_CLEAR_RECTS, v);

        assertSameElementsEventually(
                offsetTestKeepClearRect, () -> getKeepClearRectsForActivity(activity));
        assertSameElementsEventually(EMPTY_LIST,
                () -> getUnrestrictedKeepClearRectsForActivity(activity));
    }

    @Test
    public void testAccessibilityFocusCreatesKeepClearRect() throws Exception {
        assumeTrue(ViewConfiguration.get(mContext).isPreferKeepClearForFocusEnabled());

        mTestSession.launchTestActivityOnDisplaySync(TestActivity.class, mLaunchDisplayId);
        final TestActivity activity = mTestSession.getActivity();

        mWmState.setSuppressAccessibilityServices(false);
        mAccessibilityServiceRule.enableService();

        final View v = createTestViewInActivity(activity, TEST_VIEW_BOUNDS);
        v.setFocusable(false);

        mTestSession.runOnMainSyncAndWait(() -> {
            v.performAccessibilityAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null);
        });

        assertSameElementsEventually(Collections.singletonList(TEST_VIEW_BOUNDS),
                () -> getKeepClearRectsForActivity(activity));
    }

    @Test
    public void testAccessibilityAndInputFocusCreateKeepClearRects() throws Exception {
        assumeTrue(ViewConfiguration.get(mContext).isPreferKeepClearForFocusEnabled());

        mTestSession.launchTestActivityOnDisplaySync(TestActivity.class, mLaunchDisplayId);
        final TestActivity activity = mTestSession.getActivity();

        mWmState.setSuppressAccessibilityServices(false);
        mAccessibilityServiceRule.enableService();

        final Rect a11yFocusViewBounds = new Rect(30, 0, 50, 25);
        final Rect inputFocusViewBounds = new Rect(0, 0, 25, 25);

        final View a11yFocusView = createTestViewInActivity(activity, a11yFocusViewBounds);
        final View inputFocusView = createTestViewInActivity(activity, inputFocusViewBounds);

        mTestSession.runOnMainSyncAndWait(() -> {
            a11yFocusView.performAccessibilityAction(
                    AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null);

            inputFocusView.setFocusableInTouchMode(true);
            inputFocusView.setFocusable(true);
            inputFocusView.requestFocus();
        });

        assertSameElementsEventually(Arrays.asList(a11yFocusViewBounds, inputFocusViewBounds),
                () -> getKeepClearRectsForActivity(activity));
    }

    private View createTestViewInActivity(
            TestActivity activity, Rect viewBounds, Rect outViewBounds) {
        final View newView = createTestViewInActivity(activity, viewBounds);
        getViewBoundsInWindow(newView, outViewBounds);
        return newView;
    }

    private View createTestViewInActivity(TestActivity activity) {
        return createTestViewInActivity(activity, TEST_VIEW_BOUNDS);
    }

    private View createTestViewInActivity(TestActivity activity, Rect viewBounds) {
        final View newView = new View(activity);
        final LayoutParams params = new LayoutParams(viewBounds.width(), viewBounds.height());
        params.leftMargin = viewBounds.left;
        params.topMargin = viewBounds.top;
        mTestSession.runOnMainSyncAndWait(() -> {
            activity.addView(newView, params);
        });
        waitForIdle();
        return newView;
    }

    private static List<Rect> offsetListOfRect(List<Rect> rects, int dx, int dy) {
        return rects.stream()
                .map(
                        r -> {
                            Rect offsetKeepClearRect = new Rect(r);
                            offsetKeepClearRect.offset(dx, dy);
                            return offsetKeepClearRect;
                        })
                .toList();
    }

    private List<Rect> getKeepClearRectsForActivity(Activity activity) {
        return getKeepClearRectsForActivity(activity.getComponentName());
    }

    private List<Rect> getKeepClearRectsForActivity(ComponentName activityComponent) {
        mWmState.computeState();
        return mWmState.getWindowState(activityComponent).getKeepClearRects();
    }

    private List<Rect> getKeepClearRectsOnDefaultDisplay() {
        mWmState.computeState();
        return mWmState.getDisplay(mLaunchDisplayId).getKeepClearRects();
    }

    private List<Rect> getUnrestrictedKeepClearRectsForActivity(Activity activity) {
        mWmState.computeState();
        return mWmState.getWindowState(activity.getComponentName()).getUnrestrictedKeepClearRects();
    }

    public static class TestActivity extends FocusableActivity {
        private RelativeLayout mRootView;

        public void addView(View v, LayoutParams params) {
            mRootView.addView(v, params);
        }

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            if (getIntent().getBooleanExtra(USE_KEEP_CLEAR_ATTR_LAYOUT, false)) {
                setContentView(R.layout.keep_clear_attr_activity);
            } else {
                setContentView(R.layout.keep_clear_rects_activity);
            }
            mRootView = findViewById(R.id.root);

            getWindow().setDecorFitsSystemWindows(false);
        }
    }

    public static class TranslucentTestActivity extends TestActivity {}

    private static void getViewBoundsInWindow(View view, Rect outRect) {
        int[] location = new int[2];
        view.getLocationInWindow(location);
        outRect.left = location[0];
        outRect.top = location[1];
        outRect.right = location[0] + view.getWidth();
        outRect.bottom = location[1] + view.getHeight();
    }

    private static List<Rect> offsetRectsToLocationInWindow(List<Rect> rects, View view) {
        int[] location = new int[2];
        view.getLocationInWindow(location);
        return offsetListOfRect(rects, location[0], location[1]);
    }

    private List<Rect> getRectsInScreenSpace(List<Rect> rects, ComponentName componentName) {
        mWmState.computeState();
        final WindowManagerState.WindowState windowState =
                mWmState.getWindowState(componentName);
        final List<Rect> result = new ArrayList<>();
        for (Rect r : rects) {
            Rect rectInScreenSpace = new Rect(r);
            rectInScreenSpace.offset(windowState.getFrame().left, windowState.getFrame().top);
            result.add(rectInScreenSpace);
        }
        return result;
    }

    private static <T> void assertSameElementsEventually(List<T> expected, Callable<List<T>> actual)
            throws Exception {
        PollingCheck.check("Lists do not have the same elements."
                + "Expected=" + expected + ", actual=" + actual.call(),
                SAME_ELEMENT_ASSERTION_TIMEOUT,
                () -> hasSameElements(expected, actual.call()));
    }

    private static <T> boolean hasSameElements(List<T> fst, List<T> snd) {
        if (fst.size() != snd.size()) return false;

        for (T a : fst) {
            if (!snd.contains(a)) {
                return false;
            }
        }
        return true;
    }
}
