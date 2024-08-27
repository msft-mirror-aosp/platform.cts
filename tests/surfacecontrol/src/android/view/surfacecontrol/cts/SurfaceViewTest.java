/*
 * Copyright (C) 2009 The Android Open Source Project
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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.graphics.Canvas;
import android.graphics.PixelFormat;
import android.graphics.Region;
import android.platform.test.annotations.Presubmit;
import android.server.wm.CtsWindowInfoUtils;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;

import androidx.test.annotation.UiThreadTest;
import androidx.test.filters.MediumTest;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.WidgetTestUtils;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

@Presubmit
@MediumTest
@RunWith(AndroidJUnit4.class)
public class SurfaceViewTest {
    private SurfaceViewCtsActivity mActivity;
    private SurfaceViewCtsActivity.TestSurfaceView mTestSurfaceView;

    @Rule
    public ActivityTestRule<SurfaceViewCtsActivity> mActivityRule =
            new ActivityTestRule<>(SurfaceViewCtsActivity.class);

    @Before
    public void setup() {
        mActivity = mActivityRule.getActivity();
        mTestSurfaceView = mActivity.getSurfaceView();
        CtsWindowInfoUtils.waitForWindowFocus(mTestSurfaceView, true);
    }

    @UiThreadTest
    @Test
    public void testConstructor() {
        new SurfaceView(mActivity);
        new SurfaceView(mActivity, null);
        new SurfaceView(mActivity, null, 0);
    }

    @Test
    public void testSurfaceView() {
        final int left = 40;
        final int top = 30;
        final int right = 320;
        final int bottom = 240;

        assertTrue(mTestSurfaceView.isDraw());
        assertTrue(mTestSurfaceView.isOnAttachedToWindow());
        assertTrue(mTestSurfaceView.isDispatchDraw());
        assertTrue(mTestSurfaceView.isSurfaceCreatedCalled());
        assertTrue(mTestSurfaceView.isSurfaceChanged());

        assertTrue(mTestSurfaceView.isOnWindowVisibilityChanged());
        int expectedVisibility = mTestSurfaceView.getVisibility();
        int actualVisibility = mTestSurfaceView.getVInOnWindowVisibilityChanged();
        assertEquals(expectedVisibility, actualVisibility);

        assertTrue(mTestSurfaceView.isOnMeasureCalled());
        int expectedWidth = mTestSurfaceView.getMeasuredWidth();
        int expectedHeight = mTestSurfaceView.getMeasuredHeight();
        int actualWidth = mTestSurfaceView.getWidthInOnMeasure();
        int actualHeight = mTestSurfaceView.getHeightInOnMeasure();
        assertEquals(expectedWidth, actualWidth);
        assertEquals(expectedHeight, actualHeight);

        Region region = new Region();
        region.set(left, top, right, bottom);
        assertTrue(mTestSurfaceView.gatherTransparentRegion(region));

        mTestSurfaceView.setFormat(PixelFormat.TRANSPARENT);
        assertFalse(mTestSurfaceView.gatherTransparentRegion(region));

        SurfaceHolder actual = mTestSurfaceView.getHolder();
        assertNotNull(actual);
        assertTrue(actual instanceof SurfaceHolder);
    }

    /**
     * check point:
     * check surfaceView size before and after layout
     */
    @UiThreadTest
    @Test
    public void testOnSizeChanged() {
        final int left = 40;
        final int top = 30;
        final int right = 320;
        final int bottom = 240;

        // change the SurfaceView size
        int beforeLayoutWidth = mTestSurfaceView.getWidth();
        int beforeLayoutHeight = mTestSurfaceView.getHeight();
        mTestSurfaceView.resetOnSizeChangedFlag(false);
        assertFalse(mTestSurfaceView.isOnSizeChangedCalled());
        mTestSurfaceView.layout(left, top, right, bottom);
        assertTrue(mTestSurfaceView.isOnSizeChangedCalled());
        assertEquals(beforeLayoutWidth, mTestSurfaceView.getOldWidth());
        assertEquals(beforeLayoutHeight, mTestSurfaceView.getOldHeight());
        assertEquals(right - left, mTestSurfaceView.getWidth());
        assertEquals(bottom - top, mTestSurfaceView.getHeight());
    }

    /**
     * check point:
     * check surfaceView scroll X and y before and after scrollTo
     */
    @UiThreadTest
    @Test
    public void testOnScrollChanged() {
        final int scrollToX = 200;
        final int scrollToY = 200;

        int oldHorizontal = mTestSurfaceView.getScrollX();
        int oldVertical = mTestSurfaceView.getScrollY();
        assertFalse(mTestSurfaceView.isOnScrollChanged());
        mTestSurfaceView.scrollTo(scrollToX, scrollToY);
        assertTrue(mTestSurfaceView.isOnScrollChanged());
        assertEquals(oldHorizontal, mTestSurfaceView.getOldHorizontal());
        assertEquals(oldVertical, mTestSurfaceView.getOldVertical());
        assertEquals(scrollToX, mTestSurfaceView.getScrollX());
        assertEquals(scrollToY, mTestSurfaceView.getScrollY());
    }

    @Test
    public void testOnDetachedFromWindow() {
        assertFalse(mTestSurfaceView.isDetachedFromWindow());
        assertTrue(mTestSurfaceView.isShown());
        mActivityRule.finishActivity();
        PollingCheck.waitFor(() -> mTestSurfaceView.isDetachedFromWindow()
                && !mTestSurfaceView.isShown());
    }

    @Test
    public void surfaceInvalidatedWhileDetaching() throws Throwable {
        assertTrue(mTestSurfaceView.mSurface.isValid());
        assertFalse(mTestSurfaceView.isDetachedFromWindow());
        WidgetTestUtils.runOnMainAndLayoutSync(mActivityRule, () -> {
            ((ViewGroup) mTestSurfaceView.getParent()).removeView(mTestSurfaceView);
        }, false);
        assertTrue(mTestSurfaceView.isDetachedFromWindow());
        assertFalse(mTestSurfaceView.mSurface.isValid());
    }

    @Test
    public void testSurfaceRemainsValidWhileCanvasLocked() throws Throwable {
        assertFalse(mTestSurfaceView.isDetachedFromWindow());
        assertTrue(mTestSurfaceView.isShown());
        mTestSurfaceView.awaitSurfaceCreated(3, TimeUnit.SECONDS);
        assertTrue(mTestSurfaceView.isSurfaceCreatedCalled());
        Canvas canvas = mTestSurfaceView.getHolder().lockCanvas();
        assertNotNull(canvas);
        // Try to detach the surfaceview. Since the surface is locked by the lock canvas called,
        // the surface will remain valid.
        mActivityRule.getActivity().runOnUiThread(() ->
                ((ViewGroup) mTestSurfaceView.getParent()).removeView(mTestSurfaceView));
        assertTrue(mTestSurfaceView.mSurface.isValid());
        mTestSurfaceView.getHolder().unlockCanvasAndPost(canvas);
        PollingCheck.waitFor(() -> mTestSurfaceView.isDetachedFromWindow()
                && !mTestSurfaceView.isShown());
    }
}
