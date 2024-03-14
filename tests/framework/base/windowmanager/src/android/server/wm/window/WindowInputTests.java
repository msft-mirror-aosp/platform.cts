/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static android.server.wm.ActivityManagerTestBase.launchHomeActivityNoWait;
import static android.server.wm.BarTestUtils.assumeHasStatusBar;
import static android.server.wm.CtsWindowInfoUtils.waitForStableWindowGeometry;
import static android.server.wm.CtsWindowInfoUtils.waitForWindowInfo;
import static android.server.wm.UiDeviceUtils.pressUnlockButton;
import static android.server.wm.UiDeviceUtils.pressWakeupButton;
import static android.server.wm.app.Components.OverlayTestService.EXTRA_LAYOUT_PARAMS;
import static android.server.wm.window.WindowUntrustedTouchTest.MIN_POSITIVE_OPACITY;
import static android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
import static android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
import static android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
import static android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
import static android.view.WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ContentResolver;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.input.InputManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.platform.test.annotations.Presubmit;
import android.provider.Settings;
import android.server.wm.CtsWindowInfoUtils;
import android.server.wm.WindowManagerStateHelper;
import android.server.wm.app.Components;
import android.server.wm.settings.SettingsSession;
import android.util.ArraySet;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;
import android.window.WindowInfosListenerForTest.WindowInfo;

import androidx.test.rule.ActivityTestRule;

import com.android.compatibility.common.util.CtsTouchUtils;
import com.android.compatibility.common.util.SystemUtil;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Ensure moving windows and tapping is done synchronously.
 *
 * <p>Build/Install/Run: atest CtsWindowManagerDeviceWindow:WindowInputTests
 */
@Presubmit
public class WindowInputTests {
    private static final String TAG = "WindowInputTests";
    private final ActivityTestRule<TestActivity> mActivityRule =
            new ActivityTestRule<>(TestActivity.class);
    private static final int TAPPING_TARGET_WINDOW_SIZE = 100;
    private static final int PARTIAL_OBSCURING_WINDOW_SIZE = 30;

    private static final String SECOND_WINDOW_NAME = TAG + ": Second Activity Window";
    private static final String OVERLAY_WINDOW_NAME = TAG + ": Overlay Window";

    private Instrumentation mInstrumentation;
    private CtsTouchUtils mCtsTouchUtils;
    private TestActivity mActivity;
    private InputManager mInputManager;

    private View mView;
    private final Random mRandom = new Random(1);

    private int mClickCount = 0;
    private final long EVENT_FLAGS_WAIT_TIME = 2;

    @Before
    public void setUp() throws InterruptedException {
        pressWakeupButton();
        pressUnlockButton();
        launchHomeActivityNoWait();

        mInstrumentation = getInstrumentation();
        mCtsTouchUtils = new CtsTouchUtils(mInstrumentation.getTargetContext());
        mActivity = mActivityRule.launchActivity(null);
        mInputManager = mActivity.getSystemService(InputManager.class);
        mInstrumentation.waitForIdleSync();
        CtsWindowInfoUtils.waitForWindowOnTop(mActivity.getWindow());
        assertTrue("Failed to reach stable window geometry",
                waitForStableWindowGeometry(5, TimeUnit.SECONDS));
        mClickCount = 0;
    }

    /** Synchronously adds a window that is owned by the test activity. */
    private View addActivityWindow(BiConsumer<View, WindowManager.LayoutParams> windowConfig)
            throws Throwable {
        // Initialize layout params with default values for the activity window
        final var lp = new WindowManager.LayoutParams();
        lp.setTitle(SECOND_WINDOW_NAME);
        lp.flags = FLAG_NOT_TOUCH_MODAL | FLAG_LAYOUT_IN_SCREEN;
        lp.width = TAPPING_TARGET_WINDOW_SIZE;
        lp.height = TAPPING_TARGET_WINDOW_SIZE;
        lp.type = WindowManager.LayoutParams.TYPE_APPLICATION;
        lp.gravity = Gravity.CENTER;

        View view = new View(mActivity);
        mActivityRule.runOnUiThread(() -> {
            windowConfig.accept(view, lp);
            mActivity.addWindow(view, lp);
        });
        mInstrumentation.waitForIdleSync();
        waitForWindowOnTop(lp.getTitle().toString());
        return view;
    }

    /** Type alias for a configuration function. */
    private interface OverlayConfig extends Consumer<WindowManager.LayoutParams> {}

    /**
     * Synchronously adds an overlay window that is owned by a different UID and process by
     * using the OverlayTestService. Returns the cleanup function to close the service
     * and remove the overlay.
     */
    private AutoCloseable addForeignOverlayWindow(OverlayConfig overlayConfig)
            throws InterruptedException {
        // Initialize the layout params with default values for the overlay
        var lp = new WindowManager.LayoutParams();
        lp.setTitle(OVERLAY_WINDOW_NAME);
        lp.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        lp.flags = FLAG_NOT_TOUCH_MODAL | FLAG_LAYOUT_IN_SCREEN;
        lp.width = TAPPING_TARGET_WINDOW_SIZE;
        lp.height = TAPPING_TARGET_WINDOW_SIZE;
        lp.gravity = Gravity.CENTER;
        lp.setFitInsetsTypes(0);

        overlayConfig.accept(lp);

        final Intent intent = new Intent();
        intent.setComponent(Components.OVERLAY_TEST_SERVICE);
        intent.putExtra(EXTRA_LAYOUT_PARAMS, lp);
        mActivity.startForegroundService(intent);

        mInstrumentation.waitForIdleSync();
        waitForWindowOnTop(lp.getTitle().toString());
        return () -> mActivity.stopService(intent);
    }

    @Test
    public void testMoveWindowAndTap() throws Throwable {
        // Set up window.
        mView = addActivityWindow((view, lp) -> {
            view.setBackgroundColor(Color.RED);
            view.setOnClickListener((v) -> mClickCount++);
            lp.setFitInsetsTypes(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.systemGestures());
            lp.flags =
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            lp.width = lp.height = 20;
            lp.gravity = Gravity.LEFT | Gravity.TOP;
        });

        // The window location will be picked randomly from the selectBounds. Because the x, y of
        // LayoutParams is the offset from the gravity edge, make sure it offsets to (0,0) in case
        // the activity is not fullscreen, and insets system bar and window width.
        final WindowManager wm = mActivity.getWindowManager();
        final WindowMetrics windowMetrics = wm.getCurrentWindowMetrics();
        final WindowInsets windowInsets = windowMetrics.getWindowInsets();
        final Rect selectBounds = new Rect(windowMetrics.getBounds());
        selectBounds.offsetTo(0, 0);
        mActivityRule.runOnUiThread(() -> {
            var lp = (WindowManager.LayoutParams) mView.getLayoutParams();
            var insets = windowInsets.getInsetsIgnoringVisibility(lp.getFitInsetsTypes());
            selectBounds.inset(
                    0, 0, insets.left + insets.right + lp.width,
                    insets.top + insets.bottom + lp.height);
        });

        // Move the window to a random location in the window and attempt to tap on view multiple
        // times.
        final Point locationInWindow = new Point();
        final int totalClicks = 50;
        for (int i = 0; i < totalClicks; i++) {
            selectRandomLocationInWindow(selectBounds, locationInWindow);
            mActivityRule.runOnUiThread(() -> {
                var lp = (WindowManager.LayoutParams) mView.getLayoutParams();
                lp.x = locationInWindow.x;
                lp.y = locationInWindow.y;
                wm.updateViewLayout(mView, lp);
            });
            mInstrumentation.waitForIdleSync();
            mInstrumentation.getUiAutomation().syncInputTransactions(true /*waitForAnimations*/);
            final int previousCount = mClickCount;

            mCtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mActivityRule, mView);

            mInstrumentation.waitForIdleSync();
            assertEquals(previousCount + 1, mClickCount);
        }

        assertEquals(totalClicks, mClickCount);
    }

    private void selectRandomLocationInWindow(Rect bounds, Point outLocation) {
        int randomX = mRandom.nextInt(bounds.right - bounds.left) + bounds.left;
        int randomY = mRandom.nextInt(bounds.bottom - bounds.top) + bounds.top;
        outLocation.set(randomX, randomY);
    }

    @Test
    public void testTouchModalWindow() throws Throwable {
        // Set up 2 touch modal windows, expect the last one will receive all touch events.
        mView = addActivityWindow((view, lp) -> {
            lp.width = 20;
            lp.height = 20;
            lp.gravity = Gravity.LEFT | Gravity.CENTER_VERTICAL;
            lp.flags &= ~FLAG_NOT_TOUCH_MODAL;
            view.setFilterTouchesWhenObscured(true);
            view.setOnClickListener((v) -> mClickCount++);
        });
        addActivityWindow((view, lp) -> {
            lp.setTitle("Additional Window");
            lp.width = 20;
            lp.height = 20;
            lp.gravity = Gravity.RIGHT | Gravity.CENTER_VERTICAL;
            lp.flags &= ~FLAG_NOT_TOUCH_MODAL;
        });

        mCtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mActivityRule, mView);
        assertEquals(0, mClickCount);
    }

    // If a window is obscured by another window from the same app, touches should still get
    // delivered to the bottom window, and the FLAG_WINDOW_IS_OBSCURED should not be set.
    @Test
    public void testFilterTouchesWhenObscuredByWindowFromSameUid() throws Throwable {
        final AtomicBoolean touchReceived = new AtomicBoolean(false);
        final CompletableFuture<Integer> eventFlags = new CompletableFuture<>();

        // Set up a touchable window.
        mView = addActivityWindow((view, lp) -> {
            view.setFilterTouchesWhenObscured(true);
            view.setOnClickListener((v) -> mClickCount++);
            view.setOnTouchListener((v, ev) -> {
                touchReceived.set(true);
                eventFlags.complete(ev.getFlags());
                return false;
            });
        });

        // Set up an overlay window that is not touchable on top of the previous one.
        addActivityWindow((view, lp) -> {
            lp.setTitle("Overlay Window");
            lp.flags |= FLAG_NOT_TOUCHABLE;
        });

        mCtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mActivityRule, mView);

        assertTrue(touchReceived.get());
        assertEquals(
                0,
                eventFlags.get(EVENT_FLAGS_WAIT_TIME, TimeUnit.SECONDS)
                        & MotionEvent.FLAG_WINDOW_IS_OBSCURED);
        assertEquals(1, mClickCount);
    }

    @Test
    public void testFilterTouchesWhenObscuredByWindowFromDifferentUid() throws Throwable {
        final AtomicBoolean touchReceived = new AtomicBoolean(false);

        // Set up a touchable window (similar to before)
        mView = addActivityWindow((view, lp) -> {
            view.setFilterTouchesWhenObscured(true);
            view.setOnClickListener((v) -> mClickCount++);
            view.setOnTouchListener((v, ev) -> {
                touchReceived.set(true);
                return false;
            });
        });

        // Launch overlapping window owned by a different app and process.
        final OverlayConfig overlayConfig = lp -> {
            placeWindowAtCenterOfView(mView, lp);
            lp.flags |= FLAG_NOT_TOUCHABLE;
            // Any opacity higher than this would make InputDispatcher block the touch
            lp.alpha = mInputManager.getMaximumObscuringOpacityForTouch();
        };

        try (var overlay = addForeignOverlayWindow(overlayConfig)) {
            mCtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mActivityRule, mView);

            // Touch not received due to setFilterTouchesWhenObscured(true)
            assertFalse(touchReceived.get());
            assertEquals(0, mClickCount);
        }
    }

    @Test
    public void testFlagTouchesWhenObscuredByWindowFromDifferentUid() throws Throwable {
        final AtomicBoolean touchReceived = new AtomicBoolean(false);
        final CompletableFuture<Integer> eventFlags = new CompletableFuture<>();

        // Set up a touchable window
        mView = addActivityWindow((view, lp) -> {
            view.setOnClickListener((v) -> mClickCount++);
            view.setOnTouchListener((v, ev) -> {
                touchReceived.set(true);
                eventFlags.complete(ev.getFlags());
                return false;
            });
        });

        // Set up an overlap window from service
        final OverlayConfig overlayConfig = lp -> {
            placeWindowAtCenterOfView(mView, lp);
            lp.flags |= FLAG_NOT_TOUCHABLE;
            // Any opacity higher than this would make InputDispatcher block the touch
            lp.alpha = mInputManager.getMaximumObscuringOpacityForTouch();
        };

        try (var overlay = addForeignOverlayWindow(overlayConfig)) {
            mCtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mActivityRule, mView);

            assertTrue(touchReceived.get());
            assertEquals(
                    MotionEvent.FLAG_WINDOW_IS_OBSCURED,
                    eventFlags.get(EVENT_FLAGS_WAIT_TIME, TimeUnit.SECONDS)
                            & MotionEvent.FLAG_WINDOW_IS_OBSCURED);
            assertEquals(1, mClickCount);
        }
    }

    @Test
    public void testDoNotFlagTouchesWhenObscuredByZeroOpacityWindow() throws Throwable {
        final AtomicBoolean touchReceived = new AtomicBoolean(false);
        final CompletableFuture<Integer> eventFlags = new CompletableFuture<>();

        // Set up a touchable window
        mView = addActivityWindow((view, lp) -> {
            view.setOnClickListener((v) -> mClickCount++);
            view.setOnTouchListener((v, ev) -> {
                touchReceived.set(true);
                eventFlags.complete(ev.getFlags());
                return false;
            });
        });

        // Set up an overlay window with zero opacity
        final OverlayConfig overlayConfig = lp -> {
            placeWindowAtCenterOfView(mView, lp);
            lp.flags |= FLAG_NOT_TOUCHABLE;
            lp.alpha = 0;
        };

        try (var overlay = addForeignOverlayWindow(overlayConfig)) {
            mCtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mActivityRule, mView);

            assertTrue(touchReceived.get());
            assertEquals(
                    0,
                    eventFlags.get(EVENT_FLAGS_WAIT_TIME, TimeUnit.SECONDS)
                            & MotionEvent.FLAG_WINDOW_IS_OBSCURED);
            assertEquals(1, mClickCount);
        }
    }

    @Test
    public void testFlagTouchesWhenObscuredByMinPositiveOpacityWindow() throws Throwable {
        final CompletableFuture<Integer> eventFlags = new CompletableFuture<>();
        final AtomicBoolean touchReceived = new AtomicBoolean(false);

        // Set up a touchable window
        mView = addActivityWindow((view, lp) -> {
            view.setOnClickListener((v) -> mClickCount++);
            view.setOnTouchListener((v, ev) -> {
                touchReceived.set(true);
                eventFlags.complete(ev.getFlags());
                return false;
            });
        });

        // Set up an overlay window with minimum positive opacity
        final OverlayConfig overlayConfig = lp -> {
            placeWindowAtCenterOfView(mView, lp);
            lp.flags |= FLAG_NOT_TOUCHABLE;
            lp.alpha = MIN_POSITIVE_OPACITY;
        };

        try (var overlay = addForeignOverlayWindow(overlayConfig)) {
            mCtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mActivityRule, mView);

            assertTrue(touchReceived.get());
            assertEquals(
                    MotionEvent.FLAG_WINDOW_IS_OBSCURED,
                    eventFlags.get(EVENT_FLAGS_WAIT_TIME, TimeUnit.SECONDS)
                            & MotionEvent.FLAG_WINDOW_IS_OBSCURED);
            assertEquals(1, mClickCount);
        }
    }

    @Test
    public void testFlagTouchesWhenPartiallyObscuredByZeroOpacityWindow() throws Throwable {
        final CompletableFuture<Integer> eventFlags = new CompletableFuture<>();
        final AtomicBoolean touchReceived = new AtomicBoolean(false);

        // Set up the touchable window
        mView = addActivityWindow((view, lp) -> {
            view.setOnClickListener((v) -> mClickCount++);
            view.setOnTouchListener((v, ev) -> {
                touchReceived.set(true);
                eventFlags.complete(ev.getFlags());
                return false;
            });
        });

        // Partially obscuring overlay
        // TODO(b/327663469): Should the opacity be set to zero, as suggested by the test name?
        final OverlayConfig overlayConfig = lp -> {
            lp.width = PARTIAL_OBSCURING_WINDOW_SIZE;
            lp.height = PARTIAL_OBSCURING_WINDOW_SIZE;
            placeWindowAtCenterOfView(mView, lp);
            // Offset y-position to move it off the touch path (center) but still have it
            // overlap with the view.
            lp.y += PARTIAL_OBSCURING_WINDOW_SIZE;
        };

        try (var overlay = addForeignOverlayWindow(overlayConfig)) {
            mCtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mActivityRule, mView);

            assertTrue(touchReceived.get());
            assertEquals(
                    MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED,
                    eventFlags.get(EVENT_FLAGS_WAIT_TIME, TimeUnit.SECONDS)
                            & MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED);
            assertEquals(1, mClickCount);
        }
    }

    @Test
    public void testDoNotFlagTouchesWhenPartiallyObscuredByNotTouchableZeroOpacityWindow()
            throws Throwable {
        final CompletableFuture<Integer> eventFlags = new CompletableFuture<>();
        final AtomicBoolean touchReceived = new AtomicBoolean(false);

        // Set up the touchable window
        mView = addActivityWindow((view, lp) -> {
            view.setOnClickListener((v) -> mClickCount++);
            view.setOnTouchListener((v, ev) -> {
                touchReceived.set(true);
                eventFlags.complete(ev.getFlags());
                return false;
            });
        });

        // Partially obscuring overlay (not touchable, zero opacity)
        final OverlayConfig overlayConfig = lp -> {
            lp.width = PARTIAL_OBSCURING_WINDOW_SIZE;
            lp.height = PARTIAL_OBSCURING_WINDOW_SIZE;
            lp.flags |= FLAG_NOT_TOUCHABLE;
            lp.alpha = 0;
            placeWindowAtCenterOfView(mView, lp);
        };

        try (var overlay = addForeignOverlayWindow(overlayConfig)) {
            mCtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mActivityRule, mView);

            assertTrue(touchReceived.get());
            assertEquals(0, eventFlags.get(EVENT_FLAGS_WAIT_TIME, TimeUnit.SECONDS) & (
                    MotionEvent.FLAG_WINDOW_IS_OBSCURED
                            | MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED));
            assertEquals(1, mClickCount);
        }
    }

    @Test
    public void testTrustedOverlapWindow() throws Throwable {
        try (final PointerLocationSession session = new PointerLocationSession()) {
            session.set(true);
            PointerLocationSession.waitUntilPointerLocationShown(mActivity.getDisplayId());

            // Set up window.
            mView = addActivityWindow((view, lp) -> {
                view.setFilterTouchesWhenObscured(true);
                view.setOnClickListener((v) -> mClickCount++);
            });

            mCtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mActivityRule, mView);
        }
        assertEquals(1, mClickCount);
    }

    @Test
    public void testWindowBecomesUnTouchable() throws Throwable {
        mView = addActivityWindow((view, lp) -> {
            lp.width = 20;
            lp.height = 20;
            view.setOnClickListener((v) -> mClickCount++);
        });

        final View overlapView = addActivityWindow((view, lp) -> {
            lp.setTitle("Overlap Window");
            lp.width = 100;
            lp.height = 100;
        });

        mCtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mActivityRule, mView);
        assertEquals(0, mClickCount);

        mActivityRule.runOnUiThread(() -> {
            var lp = (WindowManager.LayoutParams) overlapView.getLayoutParams();
            lp.flags = FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCHABLE;
            mActivity.getWindowManager().updateViewLayout(overlapView, lp);
        });
        mInstrumentation.waitForIdleSync();
        Predicate<WindowInfo> hasInputConfigFlags =
                windowInfo -> !windowInfo.isTouchable && !windowInfo.isFocusable;
        assertTrue(waitForWindowInfo(hasInputConfigFlags, 5, TimeUnit.SECONDS,
                overlapView::getWindowToken, overlapView.getDisplay().getDisplayId()));

        mCtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mActivityRule, mView);
        assertEquals(1, mClickCount);
    }

    @Test
    public void testTapInsideUntouchableWindowResultInOutsideTouches() throws Throwable {
        final Set<MotionEvent> events = new ArraySet<>();

        mView = addActivityWindow((view, lp) -> {
            lp.width = 20;
            lp.height = 20;
            lp.flags = FLAG_NOT_TOUCHABLE | FLAG_WATCH_OUTSIDE_TOUCH;
            view.setOnTouchListener((v, e) -> {
                events.add(MotionEvent.obtain(e)); // Copy to avoid reused objects
                return false;
            });
        });

        mCtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mActivityRule, mView);

        assertEquals(1, events.size());
        MotionEvent event = events.iterator().next();
        assertEquals(MotionEvent.ACTION_OUTSIDE, event.getAction());
    }

    @Test
    public void testTapOutsideUntouchableWindowResultInOutsideTouches() throws Throwable {
        final Set<MotionEvent> events = new ArraySet<>();
        final int size = 20;

        // Set up the touchable window
        mView = addActivityWindow((view, lp) -> {
            lp.width = size;
            lp.height = size;
            lp.flags = FLAG_NOT_TOUCHABLE | FLAG_WATCH_OUTSIDE_TOUCH;
            view.setOnTouchListener((v, e) -> {
                events.add(MotionEvent.obtain(e)); // Copy to avoid reused objects
                return false;
            });
        });

        // Tap outside the untouchable window
        mCtsTouchUtils.emulateTapOnView(mInstrumentation, mActivityRule, mView, size + 5, size + 5);

        assertEquals(1, events.size());
        MotionEvent event = events.iterator().next();
        assertEquals(MotionEvent.ACTION_OUTSIDE, event.getAction());
    }

    @Test
    public void testInjectToStatusBar() {
        // Try to inject event to status bar.
        assumeHasStatusBar(mActivityRule);
        final long downTime = SystemClock.uptimeMillis();
        final MotionEvent eventHover =
                MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_HOVER_MOVE, 0, 0, 0);
        eventHover.setSource(InputDevice.SOURCE_MOUSE);
        try {
            mInstrumentation.sendPointerSync(eventHover);
            fail("Not allowed to inject to windows owned by another uid from Instrumentation.");
        } catch (RuntimeException e) {
            // Should not be allowed to inject event to a window owned by another uid from the
            // Instrumentation class.
        }
    }

    @Test
    public void testInjectFromThread() throws InterruptedException {
        // Continually inject event to activity from thread.
        final int[] decorViewLocation = new int[2];
        final View decorView = mActivity.getWindow().getDecorView();
        decorView.getLocationOnScreen(decorViewLocation);
        // Tap at the center of the view. Calculate and tap at the absolute view center location on
        // screen, so that the tapping location is always as expected regardless of windowing mode.
        final Point testPoint =
                new Point(
                        decorViewLocation[0] + decorView.getWidth() / 2,
                        decorViewLocation[1] + decorView.getHeight() / 2);

        final long downTime = SystemClock.uptimeMillis();
        final MotionEvent eventDown =
                MotionEvent.obtain(
                        downTime,
                        downTime,
                        MotionEvent.ACTION_DOWN,
                        testPoint.x,
                        testPoint.y,
                        /* metaState= */ 0);
        mInstrumentation.sendPointerSync(eventDown);

        final ExecutorService executor = Executors.newSingleThreadExecutor();
        boolean[] securityExceptionCaught = new boolean[1];
        Exception[] illegalArgumentException = new Exception[1];
        executor.execute(
                () -> {
                    for (int i = 0; i < 20; i++) {
                        final long eventTime = SystemClock.uptimeMillis();
                        final MotionEvent eventMove =
                                MotionEvent.obtain(
                                        downTime,
                                        eventTime,
                                        MotionEvent.ACTION_MOVE,
                                        testPoint.x,
                                        testPoint.y,
                                        /* metaState= */ 0);
                        try {
                            mInstrumentation.sendPointerSync(eventMove);
                        } catch (SecurityException e) {
                            securityExceptionCaught[0] = true;
                            return;
                        } catch (IllegalArgumentException e) {
                            // InputManagerService throws this exception when input target does not
                            // match.
                            // Store the exception, and raise test failure later to avoid cts thread
                            // crash.
                            illegalArgumentException[0] = e;
                            return;
                        }
                    }
                });

        // Launch another activity, should not crash the process.
        final Intent intent = new Intent(mActivity, TestActivity.class);
        mActivityRule.launchActivity(intent);
        mInstrumentation.waitForIdleSync();

        executor.shutdown();
        executor.awaitTermination(5L, TimeUnit.SECONDS);

        if (securityExceptionCaught[0]) {
            // Fail the test here instead of in the executor lambda,
            // so the failure is thrown in the test thread.
            fail("Should be allowed to inject event.");
        }

        if (illegalArgumentException[0] != null) {
            fail(
                    "Failed to inject event due to input target mismatch: "
                            + illegalArgumentException[0].getMessage());
        }
    }

    private void waitForWindowOnTop(String name) throws InterruptedException {
        assertTrue("Timed out waiting for window to be on top; window: '" + name + "'",
                CtsWindowInfoUtils.waitForWindowOnTop(5, TimeUnit.SECONDS,
                        windowInfo -> windowInfo.name.contains(name)));
    }

    public static class TestActivity extends Activity {
        private ArrayList<View> mViews = new ArrayList<>();

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
        }

        void addWindow(View view, WindowManager.LayoutParams attrs) {
            getWindowManager().addView(view, attrs);
            mViews.add(view);
        }

        void removeAllWindows() {
            for (View view : mViews) {
                getWindowManager().removeViewImmediate(view);
            }
            mViews.clear();
        }

        @Override
        protected void onPause() {
            super.onPause();
            removeAllWindows();
        }
    }

    /**
     * Position the layout params over the center of the given view.
     * @param view the target view that must already be attached to a window
     * @param lp the layout params to configure, with its width and height set to positive values
     */
    private static void placeWindowAtCenterOfView(View view, WindowManager.LayoutParams lp) {
        if (!view.isAttachedToWindow()) {
            throw new IllegalArgumentException(
                    "View must be attached to window to get layout bounds");
        }
        if (lp.width <= 0 || lp.height <= 0) {
            throw new IllegalArgumentException(
                    "Window layout params must be configured to have a positive size to use this "
                            + "method");
        }
        final int[] viewLocation = new int[2];
        view.getLocationOnScreen(viewLocation);
        lp.x = viewLocation[0] + (view.getWidth() - lp.width) / 2;
        lp.y = viewLocation[1] + (view.getHeight() - lp.height) / 2;
        lp.gravity = Gravity.TOP | Gravity.LEFT;
    }

    /** Helper class to save, set, and restore pointer location preferences. */
    private static class PointerLocationSession extends SettingsSession<Boolean> {
        PointerLocationSession() {
            super(
                    Settings.System.getUriFor("pointer_location" /* POINTER_LOCATION */),
                    PointerLocationSession::get,
                    PointerLocationSession::put);
        }

        private static void put(ContentResolver contentResolver, String s, boolean v) {
            SystemUtil.runShellCommand(
                    "settings put system " + "pointer_location" + " " + (v ? 1 : 0));
        }

        private static boolean get(ContentResolver contentResolver, String s) {
            try {
                return Integer.parseInt(
                                SystemUtil.runShellCommand(
                                                "settings get system " + "pointer_location")
                                        .trim())
                        == 1;
            } catch (NumberFormatException e) {
                return false;
            }
        }

        private static void waitUntilPointerLocationShown(int displayId) {
            final WindowManagerStateHelper wmState = new WindowManagerStateHelper();
            final String windowName = "PointerLocation - display " + displayId;
            wmState.waitForWithAmState(state -> state.isWindowSurfaceShown(windowName),
                    windowName + "'s surface is appeared");
        }
    }
}
