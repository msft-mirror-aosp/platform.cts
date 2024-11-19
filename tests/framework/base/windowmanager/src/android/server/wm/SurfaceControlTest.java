/*
 * Copyright 2019 The Android Open Source Project
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

import static android.server.wm.ActivityManagerTestBase.createFullscreenActivityScenarioRule;
import static android.server.wm.SurfaceControlTestActivity.MultiRectChecker;
import static android.server.wm.SurfaceControlTestActivity.RectChecker;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.SystemClock;
import android.platform.test.annotations.Presubmit;
import android.view.Surface;
import android.view.SurfaceControl;
import android.view.cts.surfacevalidator.PixelColor;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.rules.ActivityScenarioRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

@Presubmit
public class SurfaceControlTest {
    private static final int DEFAULT_SURFACE_SIZE = 100;
    /**
     * Use a rect that doesn't include 1 pixel in the border since some composers add blending at
     * the edges. It's easier to just ignore those pixels and ensure the rest are correct.
     */
    private static final Rect DEFAULT_RECT = new Rect(1, 1, DEFAULT_SURFACE_SIZE - 1,
            DEFAULT_SURFACE_SIZE - 1);

    @Rule
    public ActivityScenarioRule<SurfaceControlTestActivity> mActivityRule =
            createFullscreenActivityScenarioRule(SurfaceControlTestActivity.class);

    @Rule
    public TestName mName = new TestName();

    private SurfaceControlTestActivity mActivity;

    private ActivityScenario<SurfaceControlTestActivity> mScenario;

    /**
     * Shorty delay to allow for SurfaceTransactions applying to SurfaceView so it will
     * be picked up in screenshot for comparison.
     */
    private static final Long SURFACE_TRANSACTION_APPLY_DURATION = 200L; //ms

    @Before
    public void setup() {
        mScenario = mActivityRule.getScenario();
        mScenario.onActivity(activity -> {
                    assumeFalse("Test/capture infrastructure not supported on Watch",
                            activity.getPackageManager().hasSystemFeature(
                                    PackageManager.FEATURE_WATCH));
                    mActivity = activity;
                }
        );
    }

    @After
    public void tearDown() {
        mScenario.close();
    }

    @Test
    public void testLifecycle() {
        final SurfaceControl.Builder b = new SurfaceControl.Builder();
        final SurfaceControl sc = b.setName("CTS").build();
        assertNotNull("Failed to build SurfaceControl", sc);
        assertTrue(sc.isValid());
        sc.release();
        assertFalse(sc.isValid());
    }

    @Test
    public void testSameSurface() {
        final SurfaceControl.Builder b = new SurfaceControl.Builder();
        final SurfaceControl sc = b.setName("CTS").build();
        SurfaceControl copy = new SurfaceControl(sc, "SurfaceControlTest.testSameSurface");
        assertTrue(copy.isSameSurface(sc));
        sc.release();
        copy.release();
    }

    private SurfaceControl buildDefaultSurface(SurfaceControl parent) {
        return new SurfaceControl.Builder()
            .setBufferSize(DEFAULT_SURFACE_SIZE, DEFAULT_SURFACE_SIZE)
            .setName("CTS surface")
            .setParent(parent)
            .build();

    }

    void fillWithColor(SurfaceControl sc, int color) {
        Surface s = new Surface(sc);

        Canvas c = s.lockHardwareCanvas();
        c.drawColor(color);
        s.unlockCanvasAndPost(c);
    }

    private SurfaceControl buildDefaultSurface(SurfaceControl parent, int color) {
        final SurfaceControl sc = buildDefaultSurface(parent);
        fillWithColor(sc, color);
        return sc;
    }

    private SurfaceControl buildDefaultRedSurface(SurfaceControl parent) {
        return buildDefaultSurface(parent, Color.RED);
    }
    private SurfaceControl buildSmallRedSurface(SurfaceControl parent) {
        SurfaceControl surfaceControl = new SurfaceControl.Builder()
                .setBufferSize(DEFAULT_SURFACE_SIZE / 2, DEFAULT_SURFACE_SIZE / 2)
                .setName("CTS surface")
                .setParent(parent)
                .build();
        fillWithColor(surfaceControl, Color.RED);
        return surfaceControl;
    }

    /**
     * Verify that showing a 100x100 surface filled with RED produces roughly 10,000 red pixels.
     */
    @Test
    public void testShow() throws Throwable {
        final SurfaceControl parent = mActivity.awaitSurfaceControl();
        final SurfaceControl sc = buildDefaultRedSurface(parent);
        try (SurfaceControl.Transaction transaction =
                     new SurfaceControl.Transaction().setVisibility(
                             sc, true)) {
            transaction.apply();
            SystemClock.sleep(SURFACE_TRANSACTION_APPLY_DURATION);
            final Bitmap bm = mActivity.screenShot();
            sc.release();
            verifyResult(bm, new RectChecker(DEFAULT_RECT, PixelColor.RED));
        }
    }

    /**
     * The same setup as testShow, however we hide the surface and verify that we don't see Red.
     */
    @Test
    public void testHide() throws Throwable {
        final SurfaceControl parent = mActivity.awaitSurfaceControl();
        final SurfaceControl sc = buildDefaultRedSurface(parent);
        try (SurfaceControl.Transaction transaction =
                     new SurfaceControl.Transaction().setVisibility(
                             sc, false)) {
            transaction.apply();
            SystemClock.sleep(SURFACE_TRANSACTION_APPLY_DURATION);
            final Bitmap bm = mActivity.screenShot();
            sc.release();
            verifyResult(bm, new RectChecker(DEFAULT_RECT, PixelColor.BLACK));
        }
    }

    /**
     * Like testHide but we reparent the surface off-screen instead.
     */
    @Test
    public void testReparentOff() throws Throwable {
        final SurfaceControl parent = mActivity.awaitSurfaceControl();
        final SurfaceControl sc = buildDefaultRedSurface(parent);
        try (SurfaceControl.Transaction transaction = new SurfaceControl.Transaction().reparent(
                sc, parent)) {
            transaction.apply();
        }
        try (SurfaceControl.Transaction transaction = new SurfaceControl.Transaction().reparent(
                sc, null)) {
            transaction.apply();
        }
        SystemClock.sleep(SURFACE_TRANSACTION_APPLY_DURATION);
        final Bitmap bm = mActivity.screenShot();
        sc.release();
        verifyResult(bm, new RectChecker(DEFAULT_RECT, PixelColor.BLACK));
    }

    /**
     * Here we use the same red-surface set up but construct it off-screen and then re-parent it.
     */
    @Test
    public void testReparentOn() throws Throwable {
        final SurfaceControl parent = mActivity.awaitSurfaceControl();
        final SurfaceControl sc = buildDefaultRedSurface(null);
        try (SurfaceControl.Transaction transaction =
                     new SurfaceControl.Transaction().setVisibility(
                                     sc, true)
                             .reparent(sc, parent)) {
            transaction.apply();
            SystemClock.sleep(SURFACE_TRANSACTION_APPLY_DURATION);
            final Bitmap bm = mActivity.screenShot();
            sc.release();
            verifyResult(bm, new RectChecker(DEFAULT_RECT, PixelColor.RED));
        }
    }

    /**
     * Test that a surface with Layer "2" appears over a surface with Layer "1".
     */
    @Test
    public void testSetLayer() throws Throwable {
        final SurfaceControl parent = mActivity.awaitSurfaceControl();
        final SurfaceControl sc = buildDefaultRedSurface(parent);
        final SurfaceControl sc2 = buildDefaultSurface(parent, Color.GREEN);
        try (SurfaceControl.Transaction transaction =
                     new SurfaceControl.Transaction().setVisibility(
                                     sc, true)
                             .setVisibility(sc2, true)
                             .setLayer(sc, 1)
                             .setLayer(sc2, 2)) {
            transaction.apply();
            SystemClock.sleep(SURFACE_TRANSACTION_APPLY_DURATION);
            final Bitmap bm = mActivity.screenShot();
            sc.release();
            verifyResult(bm, new RectChecker(DEFAULT_RECT, PixelColor.GREEN));
        }
    }

    /**
     * Try setting the position of a surface with the top-left corner off-screen.
     */
    @Test
    public void testSetGeometry_dstBoundsOffScreen() throws Throwable {
        final SurfaceControl parent = mActivity.awaitSurfaceControl();
        final SurfaceControl sc = buildDefaultRedSurface(parent);
        try (SurfaceControl.Transaction transaction =
                     new SurfaceControl.Transaction().setVisibility(sc, true)
                             .setGeometry(sc, null,
                                     new Rect(-50, -50, 50, 50),
                                     Surface.ROTATION_0)
        ) {
            transaction.apply();
            SystemClock.sleep(SURFACE_TRANSACTION_APPLY_DURATION);
            final Bitmap bm = mActivity.screenShot();
            sc.release();
            // The rect should be offset by -50 pixels
            final RectChecker rc = new MultiRectChecker(DEFAULT_RECT) {
                final PixelColor red = new PixelColor(PixelColor.RED);
                final PixelColor black = new PixelColor(PixelColor.BLACK);

                @Override
                public PixelColor getExpectedColor(int x, int y) {
                    if (x < 50 && y < 50) {
                        return red;
                    } else {
                        return black;
                    }
                }
            };
            verifyResult(bm, rc);
        }
    }

    /**
     * Try setting the position of a surface with the top-left corner on-screen.
     */
    @Test
    public void testSetGeometry_dstBoundsOnScreen() throws Throwable {
        final SurfaceControl parent = mActivity.awaitSurfaceControl();
        final SurfaceControl sc = buildDefaultRedSurface(parent);
        try (SurfaceControl.Transaction transaction =
                     new SurfaceControl.Transaction().setVisibility(sc, true)
                             .setGeometry(sc, null,
                                     new Rect(50, 50, 150, 150),
                                     Surface.ROTATION_0)) {
            transaction.apply();
            SystemClock.sleep(SURFACE_TRANSACTION_APPLY_DURATION);
            final Bitmap bm = mActivity.screenShot();
            sc.release();
            // The rect should be offset by 50 pixels
            final RectChecker rc = new MultiRectChecker(DEFAULT_RECT) {
                final PixelColor red = new PixelColor(PixelColor.RED);
                final PixelColor black = new PixelColor(PixelColor.BLACK);

                @Override
                public PixelColor getExpectedColor(int x, int y) {
                    if (x >= 50 && y >= 50) {
                        return red;
                    } else {
                        return black;
                    }
                }
            };
            verifyResult(bm, rc);
        }
    }

    /**
     * Try scaling a surface.
     */
    @Test
    public void testSetGeometry_dstBoundsScaled() throws Throwable {
        final SurfaceControl parent = mActivity.awaitSurfaceControl();
        final SurfaceControl sc = buildSmallRedSurface(parent);
        try (SurfaceControl.Transaction transaction =
                     new SurfaceControl.Transaction().setVisibility(sc, true)
                             .setGeometry(sc, new Rect(0, 0, DEFAULT_SURFACE_SIZE / 2,
                                             DEFAULT_SURFACE_SIZE / 2),
                                     new Rect(0, 0, DEFAULT_SURFACE_SIZE, DEFAULT_SURFACE_SIZE),
                                     Surface.ROTATION_0)) {
            transaction.apply();
            SystemClock.sleep(SURFACE_TRANSACTION_APPLY_DURATION);
            final Bitmap bm = mActivity.screenShot();
            sc.release();
            verifyResult(bm, new RectChecker(DEFAULT_RECT, PixelColor.RED));
        }
    }

    private void verifyResult(final Bitmap actual, final RectChecker desired) {
        int numMatchingPixels = desired.getNumMatchingPixels(actual, mActivity.getWindow());
        Rect bounds = desired.getBoundsToCheck(actual);
        boolean success = desired.checkPixels(numMatchingPixels, actual.getWidth(),
                actual.getHeight());
        assertTrue("Actual matched pixels:" + numMatchingPixels
                + " Bitmap size:" + bounds.width() + "x" + bounds.height(), success);
    }
}
