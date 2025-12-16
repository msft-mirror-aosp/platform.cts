/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static android.server.wm.BuildUtils.HW_TIMEOUT_MULTIPLIER;
import static android.server.wm.CtsWindowInfoUtils.getWindowBoundsInDisplaySpace;
import static android.server.wm.CtsWindowInfoUtils.getWindowCenter;
import static android.server.wm.CtsWindowInfoUtils.waitForNthWindowFromTop;
import static android.server.wm.CtsWindowInfoUtils.waitForWindowFocus;
import static android.server.wm.CtsWindowInfoUtils.waitForWindowInfo;
import static android.server.wm.CtsWindowInfoUtils.waitForWindowInfos;
import static android.server.wm.CtsWindowInfoUtils.waitForWindowOnTop;
import static android.server.wm.CtsWindowInfoUtils.waitForWindowVisible;
import static android.server.wm.MockImeHelper.createManagedMockImeSession;
import static android.server.wm.WindowManagerState.STATE_RESUMED;
import static android.server.wm.WindowManagerState.STATE_STOPPED;
import static android.view.SurfaceControlViewHost.SurfacePackage;
import static android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
import static android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
import static android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
import static android.view.WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.cts.input.inputeventmatchers.InputEventMatchersKt.withFlags;
import static com.android.cts.input.inputeventmatchers.InputEventMatchersKt.withMotionAction;
import static com.android.cts.mockime.ImeEventStreamTestUtils.editorMatcher;
import static com.android.cts.mockime.ImeEventStreamTestUtils.expectEvent;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Instrumentation;
import android.app.KeyguardManager;
import android.app.UiAutomation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ConfigurationInfo;
import android.content.pm.FeatureInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Region;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.SystemClock;
import android.platform.test.annotations.Presubmit;
import android.platform.test.annotations.RequiresDevice;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.server.wm.ActivityManagerTestBase;
import android.server.wm.CtsWindowInfoUtils;
import android.server.wm.FutureConnection;
import android.server.wm.TouchHelper;
import android.server.wm.WindowManagerState;
import android.server.wm.scvh.Components;
import android.server.wm.scvh.ICrossProcessSurfaceControlViewHostTestService;
import android.util.ArrayMap;
import android.view.Gravity;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceControl;
import android.view.SurfaceControlViewHost;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.window.InputTransferToken;
import android.window.WindowInfosListenerForTest.WindowInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.FeatureUtil;
import com.android.compatibility.common.util.PollingCheck;
import com.android.cts.input.BlockingQueueEventVerifier;
import com.android.cts.input.UinputTouchDevice;
import com.android.cts.input.UinputTouchScreen;
import com.android.cts.mockime.ImeEventStream;
import com.android.cts.mockime.MockImeSession;
import com.android.window.flags.Flags;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Ensure end-to-end functionality of SurfaceControlViewHost.
 * <p>
 * Build/Install/Run:
 * atest CtsWindowManagerDeviceTestCases:SurfaceControlViewHostTests
 */
@Presubmit
public class SurfaceControlViewHostTests extends ActivityManagerTestBase implements
        SurfaceHolder.Callback {

    public static class TestActivity extends Activity {
        @Override
        protected void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            KeyguardManager keyguardManager = getSystemService(KeyguardManager.class);
            if (keyguardManager.isKeyguardLocked()) {
                keyguardManager.requestDismissKeyguard(this, null);
            }
        }
    }

    public static class SecondActivity extends Activity {}

    private static final String TAG = "SurfaceControlViewHostTests";

    private static final long WAIT_TIMEOUT_S = 5L * HW_TIMEOUT_MULTIPLIER;

    private static final ComponentName TEST_ACTIVITY = new ComponentName(
            getInstrumentation().getContext(), TestActivity.class);

    private final ActivityTestRule<TestActivity> mActivityRule = new ActivityTestRule<>(
            TestActivity.class);

    @Rule
    public TestName mName = new TestName();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    private Instrumentation mInstrumentation = InstrumentationRegistry.getInstrumentation();
    private Activity mActivity;
    private SurfaceView mSurfaceView;
    private ViewGroup mViewParent;

    private SurfaceControlViewHost mVr;
    private View mEmbeddedView;

    private volatile PopupWindow mPopupWindow;

    private SurfaceControlViewHost.SurfacePackage mRemoteSurfacePackage;

    private final Map<String,
            FutureConnection<ICrossProcessSurfaceControlViewHostTestService>> mConnections =
            new ArrayMap<>();
    private ICrossProcessSurfaceControlViewHostTestService mTestService = null;
    private static final long TIMEOUT_MS = 3000L * HW_TIMEOUT_MULTIPLIER;

    /*
     * Configurable state to control how the surfaceCreated callback
     * will initialize the embedded view hierarchy.
     */
    int mEmbeddedViewWidth = 100;
    int mEmbeddedViewHeight = 100;

    private static final int DEFAULT_SURFACE_VIEW_WIDTH = 100;
    private static final int DEFAULT_SURFACE_VIEW_HEIGHT = 100;
    MockImeSession mImeSession;

    Consumer<MotionEvent> mSurfaceViewMotionConsumer = null;

    private CountDownLatch mSvCreatedLatch;
    private ViewInitializer mViewInitializer;

    UinputTouchDevice mTouchScreen;

    private int mDisplayId;

    class MotionConsumingSurfaceView extends SurfaceView {
        MotionConsumingSurfaceView(Context c) {
            super(c);
        }

        @Override
        public boolean onTouchEvent(MotionEvent ev) {
            if (mSurfaceViewMotionConsumer == null) {
                return false;
            } else {
                mSurfaceViewMotionConsumer.accept(ev);
                return true;
            }
        }
    }

    boolean mHostGotEvent = false;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        mPopupWindow = null;
        mRemoteSurfacePackage = null;
        mViewInitializer =
                (host, view) -> host.setView(view, mEmbeddedViewWidth, mEmbeddedViewHeight);

        if (supportsInstallableIme()) {
            mImeSession = createManagedMockImeSession(this);
        }

        mActivity = mActivityRule.launchActivity(null);
        mDisplayId = mActivity.getDisplayId();
        mTouchScreen = new UinputTouchScreen(mInstrumentation, mActivity.getDisplay());
        mInstrumentation.waitForIdleSync();
        // Wait for device animation that shows above the activity to leave.
        waitForWindowOnTop(mActivity.getWindow());

        // This is necessary to call waitForWindowInfos
        mInstrumentation.getUiAutomation().adoptShellPermissionIdentity(
                android.Manifest.permission.ACCESS_SURFACE_FLINGER);

        mSvCreatedLatch = new CountDownLatch(1);
    }

    /**
     * A button that keeps track of clicks that occur. This allows tests to assert that a click
     * happened (or did not happen) in a thread-safe manner.
     */
    private static class ClickableButton extends Button {
        // Keep track of clicks that this view receives.
        private final BlockingQueue<Object> mClicks = new LinkedBlockingQueue<>();

        ClickableButton(Context context) {
            super(context);
            setOnClickListener(
                    v -> {
                        try {
                            mClicks.put(new Object());
                        } catch (InterruptedException e) {
                            fail("Interrupted unexpectedly: " + e);
                        }
                    });
        }

        private void assertClicked(boolean shouldBeClicked, Duration timeout, String message) {
            try {
                final Object click = mClicks.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
                if (shouldBeClicked) {
                    assertNotNull(message, click);
                } else {
                    assertNull(message, click);
                }
            } catch (InterruptedException e) {
                fail("Interrupted while waiting for click: " + e);
            }
        }

        /**
         * Asserts that the button was clicked.
         *
         * <p>This will wait up to 5 seconds for the click to be registered. This long timeout is
         * used to avoid flakiness in tests, as the click event is asynchronous.
         */
        void assertClicked() {
            assertClicked(true, Duration.ofSeconds(5), "Button was not clicked within the timeout");
        }

        /**
         * Asserts that the button was not clicked.
         *
         * <p>This will wait for a short duration (50ms) to ensure no click event occurred. This
         * short timeout is used to keep tests fast when verifying the absence of a click.
         */
        void assertNotClicked() {
            assertClicked(false, Duration.ofMillis(50), "Button was clicked unexpectedly");
        }
    }

    @After
    public void tearDown() throws Throwable {
        for (FutureConnection<ICrossProcessSurfaceControlViewHostTestService> connection :
                mConnections.values()) {
            mInstrumentation.getContext().unbindService(connection);
        }
        mConnections.clear();
        mInstrumentation.getUiAutomation().dropShellPermissionIdentity();
        if (mTouchScreen != null) {
            mTouchScreen.close();
        }
        Components.forceStopPackage();
    }

    private static int[] getViewLocationOnScreen(@NonNull View view) {
        final int[] xy = new int[2];
        view.getLocationOnScreen(xy);
        return xy;
    }

    private static Point getViewCenterOnScreen(@NonNull View view) {
        final int[] xy = getViewLocationOnScreen(view);
        final int viewWidth = view.getWidth();
        final int viewHeight = view.getHeight();

        return new Point(xy[0] + viewWidth / 2, xy[1] + viewHeight / 2);
    }

    private void globalTapOnViewCenter(@NonNull View view) {
        final Point location = getViewCenterOnScreen(view);
        UinputTouchDevice.Pointer pointer = mTouchScreen.touchDown(location.x, location.y);
        pointer.lift();
    }

    private void globalTapOnWindowCenter(@NonNull Supplier<IBinder> windowTokenSupplier)
            throws InterruptedException {
        final Point center = getWindowCenter(windowTokenSupplier, mDisplayId);
        UinputTouchDevice.Pointer pointer = mTouchScreen.touchDown(center.x, center.y);
        pointer.lift();
    }

    private void globalTapOnWindowCorner(@NonNull Supplier<IBinder> windowTokenSupplier)
            throws InterruptedException {
        // offset the tap towards the bottom-right to avoid the top-left rounded corner
        Point offset = new Point(DEFAULT_SURFACE_VIEW_WIDTH - 1, DEFAULT_SURFACE_VIEW_HEIGHT - 1);
        globalTapOnWindow(windowTokenSupplier, offset.x /*xOffset*/, offset.y /*yOffset*/);
    }

    private void globalTapOnWindow(@NonNull Supplier<IBinder> windowTokenSupplier, int xOffset,
            int yOffset) throws InterruptedException {
        Rect bounds = getWindowBoundsInDisplaySpace(windowTokenSupplier, mDisplayId);
        if (bounds == null) {
            fail("Could not get bounds for window!");
        }

        UinputTouchDevice.Pointer pointer = mTouchScreen.touchDown(bounds.left + xOffset,
                bounds.top + yOffset);
        pointer.lift();
    }

    private void addSurfaceViewAbove(int width, int height) throws Throwable {
        addSurfaceView(
                width,
                height,
                true /* onTop */,
                0 /* leftMargin */,
                0 /* topMargin */,
                false /* asNewWindow */);
    }

    private void addSurfaceViewBelow(int width, int height) throws Throwable {
        addSurfaceView(
                width,
                height,
                false /* onTop */,
                0 /* leftMargin */,
                0 /* topMargin */,
                false /* asNewWindow */);
    }

    private void addSurfaceView(
            int width,
            int height,
            boolean onTop,
            int leftMargin,
            int topMargin,
            boolean asNewWindow)
            throws Throwable {
        mActivityRule.runOnUiThread(
                () -> {
                    final FrameLayout content = new FrameLayout(mActivity);
                    mSurfaceView = new MotionConsumingSurfaceView(mActivity);
                    mSurfaceView.setBackgroundColor(Color.BLACK);
                    mSurfaceView.setZOrderOnTop(onTop);
                    final FrameLayout.LayoutParams lp =
                            new FrameLayout.LayoutParams(width, height, Gravity.LEFT | Gravity.TOP);
                    lp.leftMargin = leftMargin;
                    lp.topMargin = topMargin;
                    content.addView(mSurfaceView, lp);
                    mViewParent = content;
                    if (asNewWindow) {
                        final WindowManager.LayoutParams windowLp =
                                new WindowManager.LayoutParams();
                        windowLp.width = width + leftMargin;
                        windowLp.height = height + topMargin;
                        mActivity.getWindowManager().addView(content, windowLp);
                    } else {
                        mActivity.setContentView(
                                content,
                                new ViewGroup.LayoutParams(width + leftMargin, height + topMargin));
                    }
                    mSurfaceView.getHolder().addCallback(this);
                });
    }

    private interface ViewInitializer {
        void initView(SurfaceControlViewHost host, View view);
    }

    private void addViewToSurfaceView(SurfaceView sv, View v) {
        mVr = new SurfaceControlViewHost(mActivity, mActivity.getDisplay(), sv.getHostToken());

        mViewInitializer.initView(mVr, v);

        sv.setChildSurfacePackage(mVr.getSurfacePackage());

        assertEquals(v, mVr.getView());
    }

    private void requestSurfaceViewFocus() throws Throwable {
        mActivityRule.runOnUiThread(() -> {
            mSurfaceView.setFocusableInTouchMode(true);
            mSurfaceView.requestFocusFromTouch();
        });
    }

    private void assertWindowFocused(final View view, boolean hasWindowFocus) {
        if (!waitForWindowFocus(view, hasWindowFocus)) {
            fail();
        }
    }

    private void waitUntilViewDrawn(View view) throws Throwable {
        // We use frameCommitCallback because we need to ensure HWUI
        // has actually queued the frame.
        final CountDownLatch latch = new CountDownLatch(1);
        mActivityRule.runOnUiThread(() -> {
            view.getViewTreeObserver().registerFrameCommitCallback(
                    latch::countDown);
            view.invalidate();
        });
        assertTrue(latch.await(HW_TIMEOUT_MULTIPLIER, TimeUnit.SECONDS));
    }

    private void waitUntilEmbeddedViewDrawn() throws Throwable {
        waitUntilViewDrawn(mEmbeddedView);
    }

    private void waitForEmbeddedWindowComposited(boolean onTop, boolean remote) throws Throwable {
        Duration timeout = Duration.ofSeconds(HW_TIMEOUT_MULTIPLIER * 5L);

        Supplier<IBinder> windowTokenSupplier;
        if (remote) {
            windowTokenSupplier =
                    () -> {
                        try {
                            return mTestService.getWindowToken();
                        } catch (RemoteException e) {
                            return null;
                        }
                    };
        } else {
            windowTokenSupplier = mEmbeddedView::getWindowToken;
        }

        boolean success =
                onTop
                        ? waitForWindowOnTop(timeout, windowTokenSupplier)
                        : waitForNthWindowFromTop(
                                timeout, windowTokenSupplier, 1 /*expectedOrder*/);

        assertTrue("Failed to wait for the embedded window to be composited", success);
    }

    private String getTouchableRegionFromDump() {
        final String output = runCommandAndPrintOutput("dumpsys window windows");
        boolean foundWindow = false;
        for (String line : output.split("\\n")) {
            if (line.contains("SurfaceControlViewHostTests$TestActivity")) {
                foundWindow = true;
            }
            if (foundWindow && line.contains("touchable region")) {
                return line;
            }
        }
        return null;
    }

    private boolean waitForTouchableRegionChanged(String originalTouchableRegion) {
        int retries = 0;
        while (retries < 50) {
            if (getTouchableRegionFromDump() != originalTouchableRegion) {
                return true;
            }
            try {
                Thread.sleep(100);
            } catch (Exception e) {
            }
        }
        return false;
    }

    public static boolean waitForViewFocus(final View view, boolean hasViewFocus) {
        final CountDownLatch latch = new CountDownLatch(1);

        view.getHandler().post(() -> {
            if (view.hasFocus() == hasViewFocus) {
                latch.countDown();
                return;
            }
            view.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasViewFocus == hasFocus) {
                    view.setOnFocusChangeListener(null);
                    latch.countDown();
                }
            });
        });

        try {
            if (!latch.await(WAIT_TIMEOUT_S, TimeUnit.SECONDS)) {
                return false;
            }
        } catch (InterruptedException e) {
            return false;
        }
        return true;
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (mTestService == null) {
            if (mEmbeddedView != null) {
                addViewToSurfaceView(mSurfaceView, mEmbeddedView);
            }
        } else if (mRemoteSurfacePackage == null) {
            try {
                mRemoteSurfacePackage = mTestService.getSurfacePackage(mSurfaceView.getHostToken());
            } catch (Exception e) {
            }
            mSurfaceView.setChildSurfacePackage(mRemoteSurfacePackage);
        } else {
            mSurfaceView.setChildSurfacePackage(mRemoteSurfacePackage);
        }
        mSvCreatedLatch.countDown();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width,
            int height) {
    }

    @Test
    public void testEmbeddedViewReceivesInput() throws Throwable {
        mEmbeddedView = new ClickableButton(mActivity);

        addSurfaceViewAbove(DEFAULT_SURFACE_VIEW_WIDTH, DEFAULT_SURFACE_VIEW_HEIGHT);
        mInstrumentation.waitForIdleSync();
        waitUntilEmbeddedViewDrawn();
        waitForEmbeddedWindowComposited(true /*onTop*/, false /*remote*/);

        globalTapOnViewCenter(mSurfaceView);
        ((ClickableButton) mEmbeddedView).assertClicked();
    }

    @Test
    public void testEmbeddedViewReceivesRawInputCoordinatesInDisplaySpace() throws Throwable {
        assumeFalse("Automotive splitscreen uses multi-window root tasks. Because of the "
                + "lack of a correct display coordinates transform from logical to physical this "
                + "test fails on android15", hasAutomotiveSplitscreenMultitaskingFeature());

        final UiAutomation uiAutomation = mInstrumentation.getUiAutomation();
        final int viewX = DEFAULT_SURFACE_VIEW_WIDTH / 2;
        final int viewY = DEFAULT_SURFACE_VIEW_HEIGHT / 2;

        // Verify the input coordinates received by the embedded view in three different locations.
        for (int i = 0; i < 3; i++) {
            final List<MotionEvent> events = new ArrayList<>();
            mEmbeddedView = new View(mActivity);
            mEmbeddedView.setOnTouchListener((v, e) -> events.add(MotionEvent.obtain(e)));

            // Add a margin to the SurfaceView to offset the embedded view's location on the screen.
            final int leftMargin = i * 20;
            final int topMargin = i * 10;
            addSurfaceView(
                    DEFAULT_SURFACE_VIEW_WIDTH,
                    DEFAULT_SURFACE_VIEW_HEIGHT,
                    true /*onTop*/,
                    leftMargin,
                    topMargin,
                    false /* asNewWindow */);
            mInstrumentation.waitForIdleSync();
            waitUntilEmbeddedViewDrawn();
            waitForEmbeddedWindowComposited(true /*onTop*/, false /*remote*/);

            final int[] surfaceLocation = new int[2];
            mSurfaceView.getLocationOnScreen(surfaceLocation);

            final int displayX = surfaceLocation[0] + viewX;
            final int displayY = surfaceLocation[1] + viewY;

            UinputTouchDevice.Pointer pointer = mTouchScreen.touchDown(displayX, displayY);
            pointer.lift();

            PollingCheck.waitFor(() -> (events.size() == 2));
            events.forEach(e -> {
                assertEquals("Expected to get the x coordinate in View space.",
                        viewX, e.getX(), UinputTouchDevice.TOUCH_COORDINATE_EPSILON);
                assertEquals("Expected to get the y coordinate in View space.",
                        viewY, e.getY(), UinputTouchDevice.TOUCH_COORDINATE_EPSILON);
                assertEquals("Expected to get raw x coordinate in Display space.",
                        displayX, e.getRawX(), UinputTouchDevice.TOUCH_COORDINATE_EPSILON);
                assertEquals("Expected to get raw y coordinate in Display space.",
                        displayY, e.getRawY(), UinputTouchDevice.TOUCH_COORDINATE_EPSILON);
            });
        }
    }

    private static int getGlEsVersion(Context context) {
        ActivityManager activityManager =
                (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        ConfigurationInfo configInfo = activityManager.getDeviceConfigurationInfo();
        if (configInfo.reqGlEsVersion != ConfigurationInfo.GL_ES_VERSION_UNDEFINED) {
            return getMajorVersion(configInfo.reqGlEsVersion);
        } else {
            return 1; // Lack of property means OpenGL ES version 1
        }
    }

    /**
     * @see FeatureInfo#getGlEsVersion()
     */
    private static int getMajorVersion(int glEsVersion) {
        return ((glEsVersion & 0xffff0000) >> 16);
    }

    private @NonNull String getImeTestMarker() {
        return mName + Long.toString(SystemClock.elapsedRealtimeNanos());
    }

    @Test
    @RequiresDevice
    public void testEmbeddedViewIsHardwareAccelerated() throws Throwable {
        // Hardware accel may not be supported on devices without GLES 2.0
        if (getGlEsVersion(mActivity) < 2) {
            return;
        }
        mEmbeddedView = new Button(mActivity);

        addSurfaceViewAbove(DEFAULT_SURFACE_VIEW_WIDTH, DEFAULT_SURFACE_VIEW_HEIGHT);
        mInstrumentation.waitForIdleSync();

        // If we don't support hardware acceleration on the main activity the embedded
        // view also won't be.
        if (!mSurfaceView.isHardwareAccelerated()) {
            return;
        }

        assertTrue(mEmbeddedView.isHardwareAccelerated());
    }

    @Test
    public void testEmbeddedViewResizes() throws Throwable {
        mEmbeddedView = new ClickableButton(mActivity);

        final int bigEdgeLength = mEmbeddedViewWidth * 3;

        // We make the SurfaceView more than twice as big as the embedded view
        // so that a touch in the middle of the SurfaceView won't land
        // on the embedded view.
        addSurfaceViewAbove(bigEdgeLength, bigEdgeLength);
        mInstrumentation.waitForIdleSync();
        waitForEmbeddedWindowComposited(true /*onTop*/, false /*remote*/);

        globalTapOnViewCenter(mSurfaceView);
        ((ClickableButton) mEmbeddedView).assertNotClicked();

        mActivityRule.runOnUiThread(() -> {
            mVr.relayout(bigEdgeLength, bigEdgeLength);
        });
        mInstrumentation.waitForIdleSync();
        waitUntilEmbeddedViewDrawn();

        // But after the click should hit.
        globalTapOnViewCenter(mSurfaceView);
        ((ClickableButton) mEmbeddedView).assertClicked();
    }

    @Test
    public void testEmbeddedViewReleases() throws Throwable {
        mEmbeddedView = new ClickableButton(mActivity);

        addSurfaceViewAbove(DEFAULT_SURFACE_VIEW_WIDTH, DEFAULT_SURFACE_VIEW_HEIGHT);
        mInstrumentation.waitForIdleSync();
        waitForEmbeddedWindowComposited(true /*onTop*/, false /*remote*/);

        globalTapOnViewCenter(mSurfaceView);
        ((ClickableButton) mEmbeddedView).assertClicked();

        mActivityRule.runOnUiThread(() -> {
            mVr.release();
        });
        mInstrumentation.waitForIdleSync();

        globalTapOnViewCenter(mSurfaceView);
        ((ClickableButton) mEmbeddedView).assertNotClicked();
    }

    @Test
    public void testDisableInputTouch() throws Throwable {
        mEmbeddedView = new ClickableButton(mActivity);

        final WindowManager.LayoutParams embeddedLayoutParams =
                new WindowManager.LayoutParams(
                        mEmbeddedViewWidth,
                        mEmbeddedViewHeight,
                        WindowManager.LayoutParams.TYPE_APPLICATION,
                        0,
                        PixelFormat.OPAQUE);
        mViewInitializer = (host, view) -> host.setView(view, embeddedLayoutParams);

        addSurfaceViewAbove(DEFAULT_SURFACE_VIEW_WIDTH, DEFAULT_SURFACE_VIEW_HEIGHT);
        mInstrumentation.waitForIdleSync();
        waitForEmbeddedWindowComposited(true /*onTop*/, false /*remote*/);

        mActivityRule.runOnUiThread(
                () -> {
                    embeddedLayoutParams.flags |= FLAG_NOT_TOUCHABLE;
                    mVr.relayout(embeddedLayoutParams);
                });
        mInstrumentation.waitForIdleSync();

        globalTapOnViewCenter(mSurfaceView);
        ((ClickableButton) mEmbeddedView).assertNotClicked();

        mActivityRule.runOnUiThread(
                () -> {
                    embeddedLayoutParams.flags &= ~FLAG_NOT_TOUCHABLE;
                    mVr.relayout(embeddedLayoutParams);
                });
        mInstrumentation.waitForIdleSync();

        globalTapOnViewCenter(mSurfaceView);
        ((ClickableButton) mEmbeddedView).assertClicked();
    }

    @Test
    public void testReceivesOutsideEvents() throws Throwable {
        final CountDownLatch outsideEventLatch = new CountDownLatch(1);
        mEmbeddedView = new Button(mActivity);
        mEmbeddedView.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_OUTSIDE) {
                outsideEventLatch.countDown();
            }
            return true;
        });

        final WindowManager.LayoutParams embeddedLayoutParams =
                new WindowManager.LayoutParams(
                        mEmbeddedViewWidth,
                        mEmbeddedViewHeight,
                        WindowManager.LayoutParams.TYPE_APPLICATION,
                        0,
                        PixelFormat.OPAQUE);
        mViewInitializer = (host, view) -> host.setView(view, embeddedLayoutParams);

        final int marginLeft = 20;
        final int marginTop = 20;
        addSurfaceView(
                DEFAULT_SURFACE_VIEW_WIDTH,
                DEFAULT_SURFACE_VIEW_HEIGHT,
                true /*onTop*/,
                marginLeft,
                marginTop,
                false /* asNewWindow */);
        mInstrumentation.waitForIdleSync();
        waitForEmbeddedWindowComposited(true /*onTop*/, false /*remote*/);

        mActivityRule.runOnUiThread(
                () -> {
                    embeddedLayoutParams.flags |= FLAG_WATCH_OUTSIDE_TOUCH;
                    mVr.relayout(embeddedLayoutParams);
                });
        mInstrumentation.waitForIdleSync();

        // Tap outside the embedded window.
        globalTapOnWindow(mEmbeddedView::getWindowToken, -10 /*xOffset*/, -10 /*yOffset*/);
        assertTrue(outsideEventLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testFocusable() throws Throwable {
        mEmbeddedView = new Button(mActivity);
        addSurfaceViewAbove(DEFAULT_SURFACE_VIEW_WIDTH, DEFAULT_SURFACE_VIEW_HEIGHT);
        mInstrumentation.waitForIdleSync();
        waitUntilEmbeddedViewDrawn();

        // When surface view is focused, it should transfer focus to the embedded view.
        requestSurfaceViewFocus();
        assertWindowFocused(mEmbeddedView, true);
        // assert host does not have focus
        assertWindowFocused(mSurfaceView, false);

        // When surface view is no longer focused, it should transfer focus back to the host window.
        mActivityRule.runOnUiThread(() -> mSurfaceView.setFocusable(false));
        assertWindowFocused(mEmbeddedView, false);
        // assert host has focus
        assertWindowFocused(mSurfaceView, true);
    }

    @Test
    public void testFocusWithTouch() throws Throwable {
        assumeFalse("Automotive splitscreen uses multi-window root tasks. Because of the "
                + "lack of a correct display coordinates transform from logical to physical this "
                + "test fails on android15", hasAutomotiveSplitscreenMultitaskingFeature());

        mEmbeddedView = new Button(mActivity);
        addSurfaceViewAbove(DEFAULT_SURFACE_VIEW_WIDTH, DEFAULT_SURFACE_VIEW_HEIGHT);
        mInstrumentation.waitForIdleSync();
        waitUntilEmbeddedViewDrawn();
        waitForEmbeddedWindowComposited(true /*onTop*/, false /*remote*/);

        // Tap where the embedded window is placed to ensure focus is given via touch
        globalTapOnWindowCenter(mEmbeddedView::getWindowToken);

        assertWindowFocused(mEmbeddedView, true);
        // assert host does not have focus
        assertWindowFocused(mSurfaceView, false);

        // Tap where the host window is placed to ensure focus is given back to host when touched
        globalTapOnWindowCenter(mViewParent::getWindowToken);
        assertWindowFocused(mEmbeddedView, false);
        // assert host does not have focus
        assertWindowFocused(mViewParent, true);
    }

    @Test
    public void testChildWindowFocusable() throws Throwable {
        assumeFalse("Automotive splitscreen uses multi-window root tasks. Because of the "
                + "lack of a correct display coordinates transform from logical to physical this "
                + "test fails on android15", hasAutomotiveSplitscreenMultitaskingFeature());

        mEmbeddedView = new Button(mActivity);
        mEmbeddedView.setBackgroundColor(Color.BLUE);
        View embeddedViewChild = new Button(mActivity);
        embeddedViewChild.setBackgroundColor(Color.RED);
        addSurfaceViewAbove(DEFAULT_SURFACE_VIEW_WIDTH, DEFAULT_SURFACE_VIEW_HEIGHT);
        mInstrumentation.waitForIdleSync();
        waitUntilEmbeddedViewDrawn();

        mActivityRule.runOnUiThread(() -> {
            final WindowManager.LayoutParams embeddedViewChildParams =
                    new WindowManager.LayoutParams(25, 25,
                            WindowManager.LayoutParams.TYPE_APPLICATION, 0, PixelFormat.OPAQUE);
            embeddedViewChildParams.token = mEmbeddedView.getWindowToken();
            WindowManager wm = mActivity.getSystemService(WindowManager.class);
            wm.addView(embeddedViewChild, embeddedViewChildParams);
        });

        waitUntilViewDrawn(embeddedViewChild);
        assertTrue(
                "Failed to wait for the embedded child window to be composited",
                waitForWindowOnTop(
                        Duration.ofSeconds(HW_TIMEOUT_MULTIPLIER * 5L),
                        embeddedViewChild::getWindowToken));

        globalTapOnWindowCenter(embeddedViewChild::getWindowToken);
        // When tapping on the child embedded window, it should gain focus.
        assertWindowFocused(embeddedViewChild, true);
        // assert parent embedded window does not have focus.
        assertWindowFocused(mEmbeddedView, false);
        // assert host does not have focus
        assertWindowFocused(mSurfaceView, false);

        globalTapOnWindowCorner(mEmbeddedView::getWindowToken);

        // When tapping on the parent embedded window, it should gain focus.
        assertWindowFocused(mEmbeddedView, true);
        // assert child embedded window does not have focus.
        assertWindowFocused(embeddedViewChild, false);
        // assert host does not have focus
        assertWindowFocused(mSurfaceView, false);
    }

    @Test
    public void testFocusWithTouchCrossProcess() throws Throwable {
        assumeFalse("Automotive splitscreen uses multi-window root tasks. Because of the "
                + "lack of a correct display coordinates transform from logical to physical this "
                + "test fails on android15", hasAutomotiveSplitscreenMultitaskingFeature());

        mTestService = getService();
        assertNotNull(mTestService);

        addSurfaceViewAbove(DEFAULT_SURFACE_VIEW_WIDTH, DEFAULT_SURFACE_VIEW_HEIGHT);
        mSvCreatedLatch.await(5, TimeUnit.SECONDS);
        waitForEmbeddedWindowComposited(true /*onTop*/, true /*remote*/);

        // Tap where the embedded window is placed to ensure focus is given via touch
        globalTapOnWindowCenter(() -> {
            try {
                return mTestService.getWindowToken();
            } catch (RemoteException e) {
                fail("Could not get token from service, got " + e);
                return null;
            }
        });
        assertTrue(mTestService.waitForFocus(true));
        // assert host does not have focus
        assertWindowFocused(mSurfaceView, false);

        // Tap where the host window is placed to ensure focus is given back to host when touched
        globalTapOnWindowCenter(mViewParent::getWindowToken);
        assertTrue(mTestService.waitForFocus(false));
        // assert host does not have focus
        assertWindowFocused(mViewParent, true);
    }

    @Test
    public void testWindowResumes_FocusTransfersToEmbedded() throws Throwable {
        mEmbeddedView = new Button(mActivity);
        addSurfaceViewAbove(DEFAULT_SURFACE_VIEW_WIDTH, DEFAULT_SURFACE_VIEW_HEIGHT);
        mInstrumentation.waitForIdleSync();
        waitUntilEmbeddedViewDrawn();

        // When surface view is focused, it should transfer focus to the embedded view.
        requestSurfaceViewFocus();
        assertWindowFocused(mEmbeddedView, true);
        // assert host does not have focus
        assertWindowFocused(mSurfaceView, false);

        WindowManager wm = mActivity.getSystemService(WindowManager.class);
        View childView = new Button(mActivity);
        mActivityRule.runOnUiThread(() -> {
            final WindowManager.LayoutParams childWindowParams =
                    new WindowManager.LayoutParams(25, 25,
                            WindowManager.LayoutParams.TYPE_APPLICATION, 0, PixelFormat.OPAQUE);
            wm.addView(childView, childWindowParams);
        });
        waitUntilViewDrawn(childView);
        assertWindowFocused(childView, true);
        // Neither host or embedded should be focus
        assertWindowFocused(mSurfaceView, false);
        assertWindowFocused(mEmbeddedView, false);

        mActivityRule.runOnUiThread(() -> wm.removeView(childView));
        mInstrumentation.waitForIdleSync();

        assertWindowFocused(mEmbeddedView, true);
        assertWindowFocused(mSurfaceView, false);
    }

    @Test
    public void testImeVisible() throws Throwable {
        assumeTrue(MSG_NO_MOCK_IME, supportsInstallableIme());
        EditText editText = new EditText(mActivity);

        mEmbeddedView = editText;
        editText.setBackgroundColor(Color.BLUE);
        editText.setPrivateImeOptions(getImeTestMarker());
        addSurfaceViewAbove(DEFAULT_SURFACE_VIEW_WIDTH, DEFAULT_SURFACE_VIEW_HEIGHT);
        mInstrumentation.waitForIdleSync();
        waitUntilEmbeddedViewDrawn();
        waitForEmbeddedWindowComposited(true /*onTop*/, false /*remote*/);

        // When surface view is focused, it should transfer focus to the embedded view.
        requestSurfaceViewFocus();
        assertWindowFocused(mEmbeddedView, true);
        // assert host does not have focus
        assertWindowFocused(mSurfaceView, false);

        globalTapOnViewCenter(mSurfaceView);
        final ImeEventStream stream = mImeSession.openEventStream();
        expectEvent(stream, editorMatcher("onStartInputView",
                editText.getPrivateImeOptions()), TIMEOUT_MS);
    }

    @Test
    public void testImeVisibleWithZBelowRequest() throws Throwable {
        assumeTrue(MSG_NO_MOCK_IME, supportsInstallableIme());
        EditText editText = new EditText(mActivity);

        mEmbeddedView = editText;
        editText.setBackgroundColor(Color.BLUE);
        editText.setPrivateImeOptions(getImeTestMarker());
        addSurfaceViewBelow(DEFAULT_SURFACE_VIEW_WIDTH, DEFAULT_SURFACE_VIEW_HEIGHT);
        mInstrumentation.waitForIdleSync();
        waitUntilEmbeddedViewDrawn();

        // When surface view is focused, it should transfer focus to the embedded view.
        requestSurfaceViewFocus();
        assertWindowFocused(mEmbeddedView, true);
        // assert host does not have focus
        assertWindowFocused(mSurfaceView, false);

        mActivityRule.runOnUiThread(
                () -> {
                    editText.requestFocus();
                });
        final ImeEventStream stream = mImeSession.openEventStream();
        expectEvent(stream, editorMatcher("onStartInput",
                editText.getPrivateImeOptions()), TIMEOUT_MS);

        mActivityRule.runOnUiThread(
                () -> {
                    final InputMethodManager imm =
                            mActivity.getSystemService(InputMethodManager.class);
                    imm.showSoftInput(editText, 0);
                });
        expectEvent(
                stream,
                editorMatcher("onStartInputView", editText.getPrivateImeOptions()),
                TIMEOUT_MS);
    }

    @Test
    public void testImeVisibleWithZBelowTouch() throws Throwable {
        assumeTrue(MSG_NO_MOCK_IME, supportsInstallableIme());
        EditText editText = new EditText(mActivity);

        mEmbeddedView = editText;
        editText.setBackgroundColor(Color.BLUE);
        editText.setPrivateImeOptions(getImeTestMarker());
        addSurfaceViewBelow(DEFAULT_SURFACE_VIEW_WIDTH, DEFAULT_SURFACE_VIEW_HEIGHT);
        mInstrumentation.waitForIdleSync();
        waitUntilEmbeddedViewDrawn();
        waitForEmbeddedWindowComposited(false /*onTop*/, false /*remote*/);

        // When surface view is focused, it should transfer focus to the embedded view.
        requestSurfaceViewFocus();
        assertWindowFocused(mEmbeddedView, true);
        // assert host does not have focus
        assertWindowFocused(mSurfaceView, false);

        CountDownLatch waitForClientDraw = new CountDownLatch(1);
        SurfaceControl.Transaction t = new SurfaceControl.Transaction();
        t.addTransactionCommittedListener(Runnable::run, waitForClientDraw::countDown);
        mActivityRule.runOnUiThread(
                () -> {
                    mSurfaceView.getRootSurfaceControl().applyTransactionOnDraw(t);
                    mSurfaceView.getRootSurfaceControl().setTouchableRegion(new Region());
                });
        assertTrue(
                "Failed to wait for touchable region to be updated",
                waitForClientDraw.await(WAIT_TIMEOUT_S, TimeUnit.SECONDS));

        // wait for input to get the updated touch regions
        mInstrumentation.getUiAutomation().syncInputTransactions(true);

        globalTapOnViewCenter(mSurfaceView);
        final ImeEventStream stream = mImeSession.openEventStream();
        expectEvent(
                stream,
                editorMatcher("onStartInputView", editText.getPrivateImeOptions()),
                TIMEOUT_MS);
    }

    // Verifies that IME is z-ordered above the embedded window when the embedded window
    // is in a child window.
    @Test
    public void testImeZOrderedAboveEmbeddedWindowInChildWindow() throws Throwable {
        assumeTrue(MSG_NO_MOCK_IME, supportsInstallableIme());

        EditText editText = new EditText(mActivity);
        editText.setBackgroundColor(Color.BLUE);
        editText.setPrivateImeOptions(getImeTestMarker());
        mEmbeddedView = editText;

        addSurfaceView(
                DEFAULT_SURFACE_VIEW_WIDTH,
                DEFAULT_SURFACE_VIEW_HEIGHT,
                true /* onTop */,
                0 /* leftMargin */,
                0 /* topMargin */,
                true /* asNewWindow */);
        mInstrumentation.waitForIdleSync();
        waitUntilEmbeddedViewDrawn();

        requestSurfaceViewFocus();
        assertWindowFocused(mEmbeddedView, true);
        // assert host does not have focus
        assertWindowFocused(mSurfaceView, false);

        globalTapOnViewCenter(mSurfaceView);

        // Verify that IME is z-ordered above the embedded window
        Predicate<List<WindowInfo>> imeInExpectedLocation =
                windowInfos -> {
                    // SurfaceView is the activity layer
                    int surfaceViewIndex = -1;
                    int imeIndex = -1;
                    for (int i = 0; i < windowInfos.size(); i++) {
                        var windowInfo = windowInfos.get(i);
                        if (Objects.equals(windowInfo.windowToken, mSurfaceView.getWindowToken())) {
                            surfaceViewIndex = i;
                        } else if (windowInfo.name.contains("InputMethod")) {
                            imeIndex = i;
                        }
                    }
                    if (surfaceViewIndex == -1 || imeIndex == -1) {
                        return false;
                    }
                    return imeIndex < surfaceViewIndex;
                };
        assertTrue(waitForWindowInfos(imeInExpectedLocation, Duration.ofSeconds(5)));
    }

    @Test
    public void testNotFocusable() throws Throwable {
        mEmbeddedView = new Button(mActivity);
        addSurfaceViewAbove(DEFAULT_SURFACE_VIEW_WIDTH, DEFAULT_SURFACE_VIEW_HEIGHT);
        final WindowManager.LayoutParams embeddedLayoutParams =
                new WindowManager.LayoutParams(
                        mEmbeddedViewWidth,
                        mEmbeddedViewHeight,
                        WindowManager.LayoutParams.TYPE_APPLICATION,
                        0,
                        PixelFormat.OPAQUE);
        mViewInitializer = (host, view) -> host.setView(view, embeddedLayoutParams);
        mActivityRule.runOnUiThread(
                () -> {
                    embeddedLayoutParams.flags |= FLAG_NOT_FOCUSABLE;
                    mVr.relayout(embeddedLayoutParams);
                });
        mInstrumentation.waitForIdleSync();
        waitUntilEmbeddedViewDrawn();

        // When surface view is focused, nothing should happen since the embedded view is not
        // focusable.
        requestSurfaceViewFocus();
        assertWindowFocused(mEmbeddedView, false);
        // assert host has focus
        assertWindowFocused(mSurfaceView, true);
    }

    @Test
    public void testFocusBeforeAddingEmbedded() throws Throwable {
        addSurfaceViewAbove(DEFAULT_SURFACE_VIEW_WIDTH, DEFAULT_SURFACE_VIEW_HEIGHT);
        // Request focus to the SV before adding the embedded.
        requestSurfaceViewFocus();
        mSvCreatedLatch.await();
        assertTrue("Failed to wait for sv to gain focus", waitForViewFocus(mSurfaceView, true));

        mEmbeddedView = new Button(mActivity);
        mActivityRule.runOnUiThread(
                () -> {
                    addViewToSurfaceView(mSurfaceView, mEmbeddedView);
                });
        waitForWindowVisible(mEmbeddedView);
        assertWindowFocused(mEmbeddedView, true);
        assertWindowFocused(mSurfaceView, false);
    }

    @Test
    public void testViewHostParentRemainConnected() throws Throwable {
        addSurfaceViewAbove(DEFAULT_SURFACE_VIEW_WIDTH, DEFAULT_SURFACE_VIEW_HEIGHT);
        requestSurfaceViewFocus();
        mSvCreatedLatch.await();
        Button embeddedView = new Button(mActivity);
        mActivityRule.runOnUiThread(
                () -> {
                    addViewToSurfaceView(mSurfaceView, embeddedView);
                });
        waitForWindowVisible(embeddedView);
        assertWindowFocused(embeddedView, true);
        assertWindowFocused(mSurfaceView, false);

        final ActivityTestRule<SecondActivity> secondActivityRule =
                new ActivityTestRule<>(SecondActivity.class);
        final Activity secondActivity = secondActivityRule.launchActivity(null);
        waitAndAssertActivityState(
                secondActivity.getComponentName(), STATE_RESUMED, "Top activity must be resumed.");
        waitAndAssertActivityState(
                mActivity.getComponentName(), STATE_STOPPED, "Test activity must be stopped.");

        secondActivity.finish();
        waitAndAssertActivityState(
                mActivity.getComponentName(), STATE_RESUMED, "Test activity must be resumed.");
        // Input focus should remained as the remote view.
        assertWindowFocused(embeddedView, true);
        // The remote view should forward the back key to host activity, which will finish itself.
        TouchHelper.injectKey(KeyEvent.KEYCODE_BACK, false /* longpress */, true /* sync */);
        mWmState.waitForHomeActivityVisible();
    }

    private static class SurfaceCreatedCallback implements SurfaceHolder.Callback {
        private final CountDownLatch mSurfaceCreated;

        SurfaceCreatedCallback(CountDownLatch latch) {
            mSurfaceCreated = latch;
        }

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            mSurfaceCreated.countDown();
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        }
    }

    @Test
    public void testCanCopySurfacePackage() throws Throwable {
        assumeFalse("Automotive splitscreen uses multi-window root tasks. Because of the "
                + "lack of a correct display coordinates transform from logical to physical this "
                + "test fails on android15", hasAutomotiveSplitscreenMultitaskingFeature());

        // Create a surface view and wait for its surface to be created.
        CountDownLatch surfaceCreated = new CountDownLatch(1);
        mActivityRule.runOnUiThread(
                () -> {
                    final FrameLayout content = new FrameLayout(mActivity);
                    mSurfaceView = new SurfaceView(mActivity);
                    mSurfaceView.setZOrderOnTop(true);
                    content.addView(
                            mSurfaceView,
                            new FrameLayout.LayoutParams(
                                    DEFAULT_SURFACE_VIEW_WIDTH,
                                    DEFAULT_SURFACE_VIEW_HEIGHT,
                                    Gravity.LEFT | Gravity.TOP));
                    mActivity.setContentView(
                            content,
                            new ViewGroup.LayoutParams(
                                    DEFAULT_SURFACE_VIEW_WIDTH, DEFAULT_SURFACE_VIEW_HEIGHT));
                    mSurfaceView
                            .getHolder()
                            .addCallback(new SurfaceCreatedCallback(surfaceCreated));

                    // Create an embedded view.
                    mVr =
                            new SurfaceControlViewHost(
                                    mActivity, mActivity.getDisplay(), mSurfaceView.getHostToken());
                    mEmbeddedView = new ClickableButton(mActivity);
                    mVr.setView(mEmbeddedView, mEmbeddedViewWidth, mEmbeddedViewHeight);
                });
        assertTrue("Failed to wait for SurfaceView created",
                surfaceCreated.await(WAIT_TIMEOUT_S, TimeUnit.SECONDS));

        // Make a copy of the SurfacePackage and release the original package.
        SurfacePackage surfacePackage = mVr.getSurfacePackage();
        SurfacePackage copy = new SurfacePackage(surfacePackage);
        surfacePackage.release();

        CountDownLatch surfacePackageReparented = new CountDownLatch(1);
        mActivityRule.runOnUiThread(() -> {
            mSurfaceView.setChildSurfacePackage(copy);
            SurfaceControl.Transaction t = new SurfaceControl.Transaction();
            t.addTransactionCommittedListener(Runnable::run, surfacePackageReparented::countDown);
            mSurfaceView.getRootSurfaceControl().applyTransactionOnDraw(t);
        });
        assertTrue("Failed to wait for surface package to get reparented",
                surfacePackageReparented.await(WAIT_TIMEOUT_S, TimeUnit.SECONDS));

        mInstrumentation.waitForIdleSync();
        waitUntilEmbeddedViewDrawn();
        waitForEmbeddedWindowComposited(true /*onTop*/, false /*remote*/);

        // Check if SurfacePackage copy remains valid even though the original package has
        // been released.
        globalTapOnWindowCenter(mEmbeddedView::getWindowToken);
        ((ClickableButton) mEmbeddedView).assertClicked();
    }

    @Test
    public void testTransferSurfacePackage() throws Throwable {
        // Create a surface view and wait for its surface to be created.
        CountDownLatch surfaceCreated = new CountDownLatch(1);
        CountDownLatch surface2Created = new CountDownLatch(1);
        CountDownLatch viewDetached = new CountDownLatch(1);
        AtomicReference<SurfacePackage> surfacePackageRef = new AtomicReference<>(null);
        AtomicReference<SurfacePackage> surfacePackageCopyRef = new AtomicReference<>(null);
        AtomicReference<SurfaceView> secondSurfaceRef = new AtomicReference<>(null);

        mActivityRule.runOnUiThread(
                () -> {
                    final FrameLayout content = new FrameLayout(mActivity);
                    mSurfaceView = new SurfaceView(mActivity);
                    mSurfaceView.setZOrderOnTop(true);
                    content.addView(
                            mSurfaceView,
                            new FrameLayout.LayoutParams(
                                    DEFAULT_SURFACE_VIEW_WIDTH,
                                    DEFAULT_SURFACE_VIEW_HEIGHT,
                                    Gravity.LEFT | Gravity.TOP));
                    mActivity.setContentView(
                            content,
                            new ViewGroup.LayoutParams(
                                    DEFAULT_SURFACE_VIEW_WIDTH, DEFAULT_SURFACE_VIEW_HEIGHT));
                    mSurfaceView
                            .getHolder()
                            .addCallback(new SurfaceCreatedCallback(surfaceCreated));

                    // Create an embedded view.
                    mVr =
                            new SurfaceControlViewHost(
                                    mActivity, mActivity.getDisplay(), mSurfaceView.getHostToken());
                    mEmbeddedView = new ClickableButton(mActivity);
                    mVr.setView(mEmbeddedView, mEmbeddedViewWidth, mEmbeddedViewHeight);

                    SurfacePackage surfacePackage = mVr.getSurfacePackage();
                    surfacePackageRef.set(surfacePackage);
                    surfacePackageCopyRef.set(new SurfacePackage(surfacePackage));

                    // Assign the surface package to the first surface
                    mSurfaceView.setChildSurfacePackage(surfacePackage);

                    // Create the second surface view to which we'll assign the surface package copy
                    SurfaceView secondSurface = new SurfaceView(mActivity);
                    secondSurfaceRef.set(secondSurface);

                    mSurfaceView.addOnAttachStateChangeListener(
                            new View.OnAttachStateChangeListener() {
                                @Override
                                public void onViewAttachedToWindow(View v) {}

                                @Override
                                public void onViewDetachedFromWindow(View v) {
                                    viewDetached.countDown();
                                }
                            });

                    secondSurface
                            .getHolder()
                            .addCallback(new SurfaceCreatedCallback(surface2Created));
                });
        surfaceCreated.await();

        // Add the second surface view and assign it the surface package copy
        mActivityRule.runOnUiThread(() -> {
            ViewGroup content = (ViewGroup) mSurfaceView.getParent();
            content.addView(secondSurfaceRef.get(),
                    new FrameLayout.LayoutParams(DEFAULT_SURFACE_VIEW_WIDTH,
                            DEFAULT_SURFACE_VIEW_HEIGHT, Gravity.TOP | Gravity.LEFT));
            secondSurfaceRef.get().setZOrderOnTop(true);
            surfacePackageRef.get().release();
            secondSurfaceRef.get().setChildSurfacePackage(surfacePackageCopyRef.get());

            content.removeView(mSurfaceView);
        });

        // Wait for the first surface to be removed
        surface2Created.await();
        viewDetached.await();

        mInstrumentation.waitForIdleSync();
        waitUntilEmbeddedViewDrawn();

        // Check if SurfacePackage copy remains valid even though the original package has
        // been released and the original surface view removed.
        globalTapOnViewCenter(secondSurfaceRef.get());
        ((ClickableButton) mEmbeddedView).assertClicked();
    }

    @Test
    public void testCanReplaceSurfacePackage() throws Throwable {
        assumeFalse("Automotive splitscreen uses multi-window root tasks. Because of the "
                + "lack of a correct display coordinates transform from logical to physical this "
                + "test fails on android15", hasAutomotiveSplitscreenMultitaskingFeature());

        // Create a surface view and wait for its surface to be created.
        CountDownLatch surfaceCreated = new CountDownLatch(1);
        mActivityRule.runOnUiThread(() -> {
            final FrameLayout content = new FrameLayout(mActivity);
            mSurfaceView = new SurfaceView(mActivity);
            mSurfaceView.setZOrderOnTop(true);
            content.addView(mSurfaceView, new FrameLayout.LayoutParams(
                    DEFAULT_SURFACE_VIEW_WIDTH, DEFAULT_SURFACE_VIEW_HEIGHT,
                    Gravity.LEFT | Gravity.TOP));
            mActivity.setContentView(content, new ViewGroup.LayoutParams(
                    DEFAULT_SURFACE_VIEW_WIDTH, DEFAULT_SURFACE_VIEW_HEIGHT));
            mSurfaceView.getHolder().addCallback(new SurfaceCreatedCallback(surfaceCreated));

            // Create an embedded view without click handling.
            mVr = new SurfaceControlViewHost(mActivity, mActivity.getDisplay(),
                    mSurfaceView.getHostToken());
            mEmbeddedView = new Button(mActivity);
            mVr.setView(mEmbeddedView, mEmbeddedViewWidth, mEmbeddedViewHeight);
            mSurfaceView.setChildSurfacePackage(mVr.getSurfacePackage());
        });
        surfaceCreated.await();
        mInstrumentation.waitForIdleSync();
        waitUntilEmbeddedViewDrawn();

        CountDownLatch hostReady = new CountDownLatch(1);
        // Create a second surface view and wait for its surface to be created.
        mActivityRule.runOnUiThread(
                () -> {
                    // Create an embedded view.
                    mVr =
                            new SurfaceControlViewHost(
                                    mActivity, mActivity.getDisplay(), mSurfaceView.getHostToken());
                    mEmbeddedView = new ClickableButton(mActivity);
                    mVr.setView(mEmbeddedView, mEmbeddedViewWidth, mEmbeddedViewHeight);
                    hostReady.countDown();
                    mSurfaceView.setChildSurfacePackage(mVr.getSurfacePackage());
                });
        hostReady.await();
        mInstrumentation.waitForIdleSync();
        waitUntilEmbeddedViewDrawn();
        waitForEmbeddedWindowComposited(true /*onTop*/, false /*remote*/);

        // Check to see if the click went through - this only would happen if the surface package
        // was replaced
        globalTapOnWindowCenter(mEmbeddedView::getWindowToken);
        ((ClickableButton) mEmbeddedView).assertClicked();
    }

    class MotionRecordingSurfaceView extends SurfaceView {
        private final BlockingQueue<InputEvent> mMotionEvents = new LinkedBlockingQueue<>();
        public final BlockingQueueEventVerifier verifier =
                new BlockingQueueEventVerifier(mMotionEvents);

        MotionRecordingSurfaceView(Context c) {
            super(c);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            super.onTouchEvent(e);
            mMotionEvents.add(MotionEvent.obtain(e));
            return true;
        }
    }

    static class TouchPunchingView extends View {
        TouchPunchingView(Context context) {
            super(context);
        }

        void punchHoleInTouchableRegion() {
            getRootSurfaceControl().setTouchableRegion(new Region());
        }
    }

    private void addMotionRecordingSurfaceView(int width, int height) throws Throwable {
        mActivityRule.runOnUiThread(() -> {
            final FrameLayout content = new FrameLayout(mActivity);
            mSurfaceView = new MotionRecordingSurfaceView(mActivity);
            mSurfaceView.setZOrderOnTop(true);
            content.addView(mSurfaceView, new FrameLayout.LayoutParams(
                    width, height, Gravity.LEFT | Gravity.TOP));
            mActivity.setContentView(content, new ViewGroup.LayoutParams(width, height));
            mSurfaceView.getHolder().addCallback(this);
        });
    }

    class ForwardingSurfaceView extends SurfaceView {
        SurfaceControlViewHost.SurfacePackage mPackage;

        ForwardingSurfaceView(Context c) {
            super(c);
        }

        @Override
        protected void onDetachedFromWindow() {
            if (mPackage == null) {
                return;
            }
            mPackage.notifyDetachedFromWindow();
        }

        @Override
        protected void onConfigurationChanged(Configuration newConfig) {
            super.onConfigurationChanged(newConfig);
            mPackage.notifyConfigurationChanged(newConfig);
        }

        @Override
        public void setChildSurfacePackage(SurfaceControlViewHost.SurfacePackage p) {
            super.setChildSurfacePackage(p);
            mPackage = p;
        }
    }

    class DetachRecordingView extends View {
        boolean mDetached = false;

        DetachRecordingView(Context c) {
            super(c);
        }

        @Override
        protected void onDetachedFromWindow() {
            mDetached = true;
        }
    }

    class ConfigRecordingView extends View {
        CountDownLatch mLatch;

        ConfigRecordingView(Context c, CountDownLatch latch) {
            super(c);
            mLatch = latch;
        }

        @Override
        protected void onConfigurationChanged(Configuration newConfig) {
            mLatch.countDown();
        }
    }

    private void addForwardingSurfaceView(int width, int height) throws Throwable {
        mActivityRule.runOnUiThread(() -> {
            final FrameLayout content = new FrameLayout(mActivity);
            mSurfaceView = new ForwardingSurfaceView(mActivity);
            mSurfaceView.setZOrderOnTop(true);
            content.addView(mSurfaceView, new FrameLayout.LayoutParams(
                    width, height, Gravity.LEFT | Gravity.TOP));
            mViewParent = content;
            mActivity.setContentView(content, new ViewGroup.LayoutParams(width, height));
            mSurfaceView.getHolder().addCallback(this);
        });
    }

    @Test
    public void testEmbeddedViewCanSetTouchableRegion() throws Throwable {
        TouchPunchingView tpv;
        mEmbeddedView = tpv = new TouchPunchingView(mActivity);

        addMotionRecordingSurfaceView(DEFAULT_SURFACE_VIEW_WIDTH, DEFAULT_SURFACE_VIEW_HEIGHT);
        mInstrumentation.waitForIdleSync();
        waitUntilEmbeddedViewDrawn();
        globalTapOnViewCenter(mSurfaceView);
        mInstrumentation.waitForIdleSync();

        MotionRecordingSurfaceView mrsv = (MotionRecordingSurfaceView) mSurfaceView;
        mrsv.verifier.assertNoEvents();
        mActivityRule.runOnUiThread(() -> {
            tpv.punchHoleInTouchableRegion();
        });
        mInstrumentation.waitForIdleSync();
        waitForEmbeddedWindowComposited(true /*onTop*/, false /*remote*/);

        globalTapOnViewCenter(mSurfaceView);
        mInstrumentation.waitForIdleSync();
        mrsv.verifier.assertReceivedMotion(withMotionAction(MotionEvent.ACTION_DOWN));
        mrsv.verifier.assertReceivedMotion(withMotionAction(MotionEvent.ACTION_UP));
        mrsv.verifier.assertNoEvents();
    }

    @Test
    public void forwardDetachedFromWindow() throws Throwable {
        DetachRecordingView drv = new DetachRecordingView(mActivity);
        mEmbeddedView = drv;
        addForwardingSurfaceView(100, 100);
        mInstrumentation.waitForIdleSync();
        waitUntilEmbeddedViewDrawn();

        assertFalse(drv.mDetached);
        mActivityRule.runOnUiThread(() -> {
            mViewParent.removeView(mSurfaceView);
        });
        mInstrumentation.waitForIdleSync();
        assertTrue(drv.mDetached);
    }

    @Test
    public void forwardConfigurationChange() throws Throwable {
        if (!supportsOrientationRequest()) {
            return;
        }
        final CountDownLatch embeddedConfigLatch = new CountDownLatch(1);
        ConfigRecordingView crv = new ConfigRecordingView(mActivity, embeddedConfigLatch);
        mEmbeddedView = crv;
        addForwardingSurfaceView(100, 100);
        mInstrumentation.waitForIdleSync();
        waitUntilEmbeddedViewDrawn();
        mActivityRule.runOnUiThread(() -> {
            int orientation = mActivity.getResources().getConfiguration().orientation;
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                orientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
            } else {
                orientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
            }
            mActivity.setRequestedOrientation(orientation);
        });
        embeddedConfigLatch.await(3, TimeUnit.SECONDS);
        mInstrumentation.waitForIdleSync();
        mActivityRule.runOnUiThread(() -> {
            assertEquals(mEmbeddedView.getResources().getConfiguration().orientation,
                    mSurfaceView.getResources().getConfiguration().orientation);
        });
    }

    @Test
    public void testEmbeddedViewReceivesInputOnBottom() throws Throwable {
        assumeFalse("Automotive splitscreen uses multi-window root tasks. Because of the "
                + "lack of a correct display coordinates transform from logical to physical this "
                + "test fails on android15", hasAutomotiveSplitscreenMultitaskingFeature());

        mEmbeddedView = new ClickableButton(mActivity);

        addSurfaceViewBelow(DEFAULT_SURFACE_VIEW_WIDTH, DEFAULT_SURFACE_VIEW_HEIGHT);
        mInstrumentation.waitForIdleSync();
        waitUntilEmbeddedViewDrawn();
        waitForEmbeddedWindowComposited(false /*onTop*/, false /*remote*/);

        // We should receive no input until we punch a hole
        globalTapOnViewCenter(mSurfaceView);
        mInstrumentation.waitForIdleSync();
        ((ClickableButton) mEmbeddedView).assertNotClicked();

        String originalRegion = getTouchableRegionFromDump();

        mActivityRule.runOnUiThread(() -> {
            mSurfaceView.getRootSurfaceControl().setTouchableRegion(new Region(0, 0, 1, 1));
        });
        mInstrumentation.waitForIdleSync();
        // ViewRootImpl sends the touchable region to the WM via a one-way call, which is great
        // for performance...however not so good for testability, we have no way
        // to verify it has arrived! It doesn't make so much sense to bloat
        // the system image size with a completion callback for just this one test
        // so we settle for some inelegant spin-polling on the WM dump.
        // In the future when we revisit WM/Client interface and transactionalize
        // everything, we should have a standard way to wait on the completion of async
        // operations
        waitForTouchableRegionChanged(originalRegion);

        globalTapOnViewCenter(mSurfaceView);
        ((ClickableButton) mEmbeddedView).assertClicked();
    }

    private ICrossProcessSurfaceControlViewHostTestService getService() throws Exception {
        return mConnections.computeIfAbsent("android.server.wm.scvh", this::connect)
                .get(TIMEOUT_MS);
    }

    private static ComponentName repackage(String packageName, ComponentName baseComponent) {
        return new ComponentName(packageName, baseComponent.getClassName());
    }

    private FutureConnection<ICrossProcessSurfaceControlViewHostTestService> connect(
            String packageName) {
        FutureConnection<ICrossProcessSurfaceControlViewHostTestService> connection =
                new FutureConnection<>(
                        ICrossProcessSurfaceControlViewHostTestService.Stub::asInterface);
        Intent intent = new Intent();
        intent.setComponent(repackage(packageName,
                Components.CrossProcessSurfaceControlViewHostTestService.COMPONENT));
        assertTrue(mInstrumentation.getContext().bindService(intent,
                connection, Context.BIND_AUTO_CREATE));
        return connection;
    }

    @Test
    public void testHostInputTokenAllowsObscuredTouches() throws Throwable {
        assumeFalse("XR device uses a custom window occlusion check tested via CTS Verifier.",
                FeatureUtil.isXrHeadset());
        assumeFalse("Automotive splitscreen uses multi-window root tasks. Because of the "
                + "lack of a correct display coordinates transform from logical to physical this "
                + "test fails on android15", hasAutomotiveSplitscreenMultitaskingFeature());

        mTestService = getService();
        assertTrue(mTestService != null);

        addSurfaceViewBelow(DEFAULT_SURFACE_VIEW_WIDTH, DEFAULT_SURFACE_VIEW_HEIGHT);
        assertTrue("Failed to wait for SV to get created",
                mSvCreatedLatch.await(5, TimeUnit.SECONDS));
        mActivityRule.runOnUiThread(() -> {
            mSurfaceView.getRootSurfaceControl().setTouchableRegion(new Region());
        });
        // TODO(b/279051608): Add touchable regions in WindowInfo test so we can make sure the
        // touchable regions for the host have been set before proceeding.
        assertTrue("Failed to wait for host window to be visible",
                waitForWindowVisible(mSurfaceView));
        assertTrue("Failed to wait for embedded window to be visible",
                waitForWindowVisible(mTestService.getWindowToken(),
                mDisplayId));

        waitForEmbeddedWindowComposited(false /*onTop*/, true /*remote*/);
        globalTapOnViewCenter(mSurfaceView);

        MotionEvent motionEvent = mTestService.getMotionEvent();
        assertThat(motionEvent, allOf(withMotionAction(MotionEvent.ACTION_DOWN),
                withFlags(MotionEvent.FLAG_WINDOW_IS_OBSCURED)));
        motionEvent = mTestService.getMotionEvent();
        assertThat(motionEvent, allOf(withMotionAction(MotionEvent.ACTION_UP),
                withFlags(MotionEvent.FLAG_WINDOW_IS_OBSCURED)));

    }

    @Test
    public void testNoHostInputTokenDisallowsObscuredTouches() throws Throwable {
        // TODO(b/398861504): Ensure this test case is covered by the CTS Verifier.
        assumeFalse("XR device uses a custom window occlusion check tested via CTS Verifier.",
                FeatureUtil.isXrHeadset());
        assumeFalse("Automotive splitscreen uses multi-window root tasks. Because of the "
                + "lack of a correct display coordinates transform from logical to physical this "
                + "test fails on android15", hasAutomotiveSplitscreenMultitaskingFeature());

        mTestService = getService();
        mRemoteSurfacePackage = mTestService.getSurfacePackage(new Binder());
        assertTrue(mRemoteSurfacePackage != null);

        addSurfaceViewBelow(DEFAULT_SURFACE_VIEW_WIDTH, DEFAULT_SURFACE_VIEW_HEIGHT);
        assertTrue("Failed to wait for SV to get created",
                mSvCreatedLatch.await(5, TimeUnit.SECONDS));
        mActivityRule.runOnUiThread(() -> {
            mSurfaceView.getRootSurfaceControl().setTouchableRegion(new Region());
        });
        // TODO(b/279051608): Add touchable regions in WindowInfo test so we can make sure the
        // touchable regions for the host have been set before proceeding.
        assertTrue("Failed to wait for host window to be visible",
                waitForWindowVisible(mSurfaceView));
        assertTrue("Failed to wait for embedded window to be visible",
                waitForWindowVisible(mTestService.getWindowToken(),
                mDisplayId));

        globalTapOnViewCenter(mSurfaceView);

        assertNull(mTestService.getMotionEvent());
    }

    @Test
    public void testPopupWindowReceivesInput() throws Throwable {
        mEmbeddedView = new ClickableButton(mActivity);
        addSurfaceViewAbove(DEFAULT_SURFACE_VIEW_WIDTH, DEFAULT_SURFACE_VIEW_HEIGHT);
        mInstrumentation.waitForIdleSync();
        waitUntilEmbeddedViewDrawn();

        ClickableButton[] popupButton = {null};
        mActivityRule.runOnUiThread(
                () -> {
                    PopupWindow pw = new PopupWindow();
                    mPopupWindow = pw;
                    popupButton[0] = new ClickableButton(mActivity);
                    pw.setWidth(DEFAULT_SURFACE_VIEW_WIDTH);
                    pw.setHeight(DEFAULT_SURFACE_VIEW_HEIGHT);
                    pw.setContentView(popupButton[0]);
                    pw.showAsDropDown(mEmbeddedView);
                });
        assertTrue(
                waitForWindowOnTop(
                        Duration.ofSeconds(HW_TIMEOUT_MULTIPLIER * 5L),
                        popupButton[0]::getWindowToken));
        mInstrumentation.waitForIdleSync();

        globalTapOnViewCenter(mSurfaceView);
        popupButton[0].assertClicked();
        ((ClickableButton) mEmbeddedView).assertNotClicked();

        mActivityRule.runOnUiThread(() -> {
            mPopupWindow.dismiss();
        });
        mInstrumentation.waitForIdleSync();

        globalTapOnViewCenter(mSurfaceView);
        ((ClickableButton) mEmbeddedView).assertClicked();
    }

    @Test
    public void testPopupWindowPosition() throws Throwable {
        mEmbeddedView = new View(mActivity);
        addSurfaceViewAbove(DEFAULT_SURFACE_VIEW_WIDTH, DEFAULT_SURFACE_VIEW_HEIGHT);
        mInstrumentation.waitForIdleSync();
        waitUntilEmbeddedViewDrawn();

        mActivityRule.runOnUiThread(() -> {
            View popupContent = new View(mActivity);
            popupContent.setBackgroundColor(Color.BLUE);

            mPopupWindow = new PopupWindow();
            mPopupWindow.setWidth(50);
            mPopupWindow.setHeight(50);
            mPopupWindow.setContentView(popupContent);
            mPopupWindow.showAtLocation(mEmbeddedView, Gravity.BOTTOM | Gravity.RIGHT, 0, 0);
        });

        Predicate<List<WindowInfo>> hasExpectedFrame = windowInfos -> {
            if (mPopupWindow == null) {
                return false;
            }

            IBinder parentWindowToken = mEmbeddedView.getWindowToken();
            IBinder popupWindowToken = mPopupWindow.getContentView().getWindowToken();
            if (parentWindowToken == null || popupWindowToken == null) {
                return false;
            }

            Rect parentBounds = null;
            Rect popupBounds = null;
            for (WindowInfo windowInfo : windowInfos) {
                if (!windowInfo.isVisible) {
                    continue;
                }
                if (windowInfo.windowToken == parentWindowToken) {
                    parentBounds = windowInfo.bounds;
                } else if (windowInfo.windowToken == popupWindowToken) {
                    popupBounds = windowInfo.bounds;
                }
            }

            if (parentBounds == null) {
                return false;
            }

            var expectedBounds = new Rect(parentBounds.left + 50, parentBounds.top + 50,
                    parentBounds.left + 100, parentBounds.top + 100);
            return expectedBounds.equals(popupBounds);
        };
        assertTrue(waitForWindowInfos(hasExpectedFrame, Duration.ofSeconds(5)));
    }

    @Test
    public void testFloatingWindowWrapContent() throws Throwable {
        mEmbeddedView = new View(mActivity);
        addSurfaceViewAbove(DEFAULT_SURFACE_VIEW_WIDTH, DEFAULT_SURFACE_VIEW_HEIGHT);
        mInstrumentation.waitForIdleSync();
        waitUntilEmbeddedViewDrawn();

        View popupContent = new View(mActivity);
        popupContent.setBackgroundColor(Color.BLUE);
        popupContent.setLayoutParams(new ViewGroup.LayoutParams(50, 50));

        FrameLayout popupView = new FrameLayout(mActivity);
        popupView.addView(popupContent);

        WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
        layoutParams.setTitle("FloatingWindow");
        layoutParams.gravity = Gravity.TOP | Gravity.LEFT;
        layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        layoutParams.width = ViewGroup.LayoutParams.WRAP_CONTENT;
        layoutParams.token = mEmbeddedView.getWindowToken();

        mActivityRule.runOnUiThread(() -> {
            WindowManager windowManager = mActivity.getSystemService(WindowManager.class);
            windowManager.addView(popupView, layoutParams);
        });

        Predicate<WindowInfo> hasExpectedDimensions =
                windowInfo -> windowInfo.bounds.width() == 50 && windowInfo.bounds.height() == 50;
        // We pass popupView::getWindowToken as a java.util.function.Supplier
        // because the popupView is initially unattached and doesn't have a
        // window token. The supplier is called each time the predicate is
        // tested, eventually returning the window token.
        assertTrue(waitForWindowInfo(hasExpectedDimensions, Duration.ofSeconds(5),
                popupView::getWindowToken, mDisplayId));
    }

    @Test
    public void testFloatingWindowMatchParent() throws Throwable {
        mEmbeddedView = new View(mActivity);
        mEmbeddedViewWidth = 50;
        mEmbeddedViewHeight = 50;
        addSurfaceViewAbove(100, 100);
        mInstrumentation.waitForIdleSync();

        View popupView = new FrameLayout(mActivity);
        popupView.setBackgroundColor(Color.BLUE);

        WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
        layoutParams.setTitle("FloatingWindow");
        layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
        layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
        layoutParams.token = mEmbeddedView.getWindowToken();

        mActivityRule.runOnUiThread(() -> {
            WindowManager windowManager = mActivity.getSystemService(WindowManager.class);
            windowManager.addView(popupView, layoutParams);
        });

        Predicate<WindowInfo> hasExpectedDimensions =
                windowInfo -> windowInfo.bounds.width() == 50 && windowInfo.bounds.height() == 50;
        assertTrue(waitForWindowInfo(hasExpectedDimensions, Duration.ofSeconds(5),
                popupView::getWindowToken, mDisplayId));
    }

    class TouchTransferringView extends View {
        boolean mExpectsFirstMotion = true;
        boolean mExpectsCancel = false;
        boolean mGotCancel = false;
        // True if the test should use the WindowManager#transferTouchGesture API.
        private final boolean mUseTransferTouchGestureApi;

        TouchTransferringView(Context c, boolean useTransferTouchGestureApi) {
            super(c);
            mUseTransferTouchGestureApi = useTransferTouchGestureApi;
        }

        @Override
        public boolean onTouchEvent(MotionEvent ev) {
            int action = ev.getAction();
            synchronized (this) {
                if (mExpectsFirstMotion) {
                    assertEquals(action, MotionEvent.ACTION_DOWN);
                    if (mUseTransferTouchGestureApi) {
                        assertTrue(mWm.transferTouchGesture(
                                mVr.getSurfacePackage().getInputTransferToken(),
                                mSurfaceView.getRootSurfaceControl().getInputTransferToken()));
                    } else {
                        assertTrue(mVr.transferTouchGestureToHost());
                    }
                    mExpectsFirstMotion = false;
                    mExpectsCancel = true;
                } else if (mExpectsCancel) {
                    assertEquals(action, MotionEvent.ACTION_CANCEL);
                    mExpectsCancel = false;
                    mGotCancel = true;
                }
                this.notifyAll();
            }
            return true;
        }

        void waitForEmbeddedTouch() {
            synchronized (this) {
                if (!mExpectsFirstMotion) {
                    assertTrue(mExpectsCancel || mGotCancel);
                    return;
                }
                try {
                    this.wait();
                } catch (Exception e) {
                }
                assertFalse(mExpectsFirstMotion);
            }
        }

        void waitForCancel() {
            synchronized (this) {
                if (!mExpectsCancel) {
                    return;
                }
                try {
                    this.wait();
                } catch (Exception e) {
                }
                assertTrue(mGotCancel);
            }
        }
    }

    private void testEmbeddedWindowCanTransferTouchGestureToHost(boolean useTransferTouchGestureApi)
            throws Throwable {
        // Inside the embedded view hierarchy, we set up a view that transfers touch
        // to the host upon receiving a touch event
        TouchTransferringView ttv = new TouchTransferringView(mActivity,
                useTransferTouchGestureApi);
        mEmbeddedView = ttv;
        addSurfaceViewAbove(DEFAULT_SURFACE_VIEW_WIDTH, DEFAULT_SURFACE_VIEW_HEIGHT);
        mInstrumentation.waitForIdleSync();
        waitUntilEmbeddedViewDrawn();
        waitForEmbeddedWindowComposited(true /*onTop*/, false /*remote*/);
        // On the host SurfaceView, we set a motion consumer which expects to receive one event.
        mHostGotEvent = false;
        mSurfaceViewMotionConsumer = (ev) -> {
            synchronized (this) {
                mHostGotEvent = true;
                this.notifyAll();
            }
        };

        // Prepare to inject an event offset one pixel from the top of the SurfaceViews location
        // on-screen.
        final int[] viewOnScreenXY = new int[2];
        mSurfaceView.getLocationOnScreen(viewOnScreenXY);
        // offset the inject towards the bottom-right to avoid the top-left rounded corner
        final int injectedX = viewOnScreenXY[0] + DEFAULT_SURFACE_VIEW_WIDTH - 1;
        final int injectedY = viewOnScreenXY[1] + DEFAULT_SURFACE_VIEW_HEIGHT - 1;

        // We inject a down event
        UinputTouchDevice.Pointer pointer = mTouchScreen.touchDown(injectedX, injectedY);

        // And this down event should arrive on the embedded view, which should transfer the touch
        // focus
        ttv.waitForEmbeddedTouch();
        ttv.waitForCancel();

        // Now we inject an up event
        pointer.lift();
        // This should arrive on the host now, since we have transferred the touch focus
        synchronized (this) {
            if (!mHostGotEvent) {
                try {
                    this.wait();
                } catch (Exception e) {
                }
            }
        }
        assertTrue(mHostGotEvent);
    }

    @Test
    public void testEmbeddedWindowCanTransferTouchGestureToHost_transferTouchGestureToHost()
            throws Throwable {
        testEmbeddedWindowCanTransferTouchGestureToHost(false);
    }

    @Test
    public void testEmbeddedWindowCanTransferTouchGestureToHost_transferTouchGesture()
            throws Throwable {
        testEmbeddedWindowCanTransferTouchGestureToHost(true);
    }

    @Test
    public void testEmbeddedCannotStealTouchGestureFromHost() throws Throwable {
        mTestService = getService();
        assertNotNull(mTestService);

        addSurfaceViewAbove(DEFAULT_SURFACE_VIEW_WIDTH, DEFAULT_SURFACE_VIEW_HEIGHT);
        mSvCreatedLatch.await(5, TimeUnit.SECONDS);

        InputTransferToken hostInputTransferToken = Objects.requireNonNull(
                mSurfaceView.getRootSurfaceControl()).getInputTransferToken();
        // Ask the embedded process to request gesture transfer from the host and then
        // verify that the call throws a security exception. We need to do the assertion
        // in the test process to handle the assertion correctly.
        assertTrue(mTestService.requestTouchGestureTransferFromHostThrows(hostInputTransferToken));
    }

    @Test
    public void testHostCannotStealTouchGestureFromEmbedded() throws Throwable {
        mTestService = getService();
        assertNotNull(mTestService);

        addSurfaceViewAbove(DEFAULT_SURFACE_VIEW_WIDTH, DEFAULT_SURFACE_VIEW_HEIGHT);
        mSvCreatedLatch.await(5, TimeUnit.SECONDS);

        InputTransferToken hostInputTransferToken = Objects.requireNonNull(
                mSurfaceView.getRootSurfaceControl()).getInputTransferToken();
        InputTransferToken surfacePackageInputTransferToken =
                mRemoteSurfacePackage.getInputTransferToken();
        WindowManager wm = mActivity.getSystemService(WindowManager.class);
        assertThrows(SecurityException.class,
                () -> wm.transferTouchGesture(surfacePackageInputTransferToken,
                        hostInputTransferToken));
    }

    @Test
    public void testKeepScreenOn() throws Throwable {
        mEmbeddedView = new Button(mActivity);
        addSurfaceViewAbove(DEFAULT_SURFACE_VIEW_WIDTH, DEFAULT_SURFACE_VIEW_HEIGHT);
        mInstrumentation.waitForIdleSync();
        waitUntilEmbeddedViewDrawn();

        mWmState.computeState();
        WindowManagerState.WindowState windowState = mWmState.getWindowState(TEST_ACTIVITY);
        // Assert the KEEP_SCREEN_ON flag is not set on the main window yet.
        assertNotEquals(FLAG_KEEP_SCREEN_ON, (windowState.getFlags() & FLAG_KEEP_SCREEN_ON));

        final CountDownLatch keepScreenOnSetLatch = new CountDownLatch(2);
        mActivityRule.runOnUiThread(() -> {
            mEmbeddedView.setKeepScreenOn(true);
            mEmbeddedView.getViewTreeObserver().addOnDrawListener(keepScreenOnSetLatch::countDown);
            mSurfaceView.getViewTreeObserver().addOnDrawListener(keepScreenOnSetLatch::countDown);
        });
        keepScreenOnSetLatch.await(WAIT_TIMEOUT_S, TimeUnit.SECONDS);

        mWmState.computeState();
        windowState = mWmState.getWindowState(TEST_ACTIVITY);
        // Assert the KEEP_SCREEN_ON flag is now set on the main window.
        assertEquals(FLAG_KEEP_SCREEN_ON, (windowState.getFlags() & FLAG_KEEP_SCREEN_ON));

        final CountDownLatch keepScreenOnUnsetLatch = new CountDownLatch(2);
        mActivityRule.runOnUiThread(() -> {
            mEmbeddedView.setKeepScreenOn(false);
            mEmbeddedView.getViewTreeObserver().addOnDrawListener(
                    keepScreenOnUnsetLatch::countDown);
            mSurfaceView.getViewTreeObserver().addOnDrawListener(keepScreenOnUnsetLatch::countDown);
        });
        keepScreenOnUnsetLatch.await(WAIT_TIMEOUT_S, TimeUnit.SECONDS);

        mWmState.computeState();
        windowState = mWmState.getWindowState(TEST_ACTIVITY);
        // Assert the KEEP_SCREEN_ON flag is removed from the main window.
        assertNotEquals(FLAG_KEEP_SCREEN_ON, (windowState.getFlags() & FLAG_KEEP_SCREEN_ON));
    }

    @Test
    public void testKeepScreenOnCrossProcess() throws Throwable {
        mTestService = getService();
        assertNotNull(mTestService);

        addSurfaceViewAbove(DEFAULT_SURFACE_VIEW_WIDTH, DEFAULT_SURFACE_VIEW_HEIGHT);
        mSvCreatedLatch.await(5, TimeUnit.SECONDS);

        mWmState.computeState();
        WindowManagerState.WindowState windowState = mWmState.getWindowState(TEST_ACTIVITY);
        // Assert the KEEP_SCREEN_ON flag is not set on the main window yet.
        assertNotEquals(FLAG_KEEP_SCREEN_ON, (windowState.getFlags() & FLAG_KEEP_SCREEN_ON));

        final CountDownLatch keepScreenOnSetLatch = new CountDownLatch(1);
        mActivityRule.runOnUiThread(() -> mSurfaceView.getViewTreeObserver().addOnDrawListener(
                keepScreenOnSetLatch::countDown));
        mTestService.setKeepScreenOnFlag(true);
        keepScreenOnSetLatch.await(WAIT_TIMEOUT_S, TimeUnit.SECONDS);

        mWmState.computeState();
        windowState = mWmState.getWindowState(TEST_ACTIVITY);
        // Assert the KEEP_SCREEN_ON flag is now set on the main window.
        assertEquals(FLAG_KEEP_SCREEN_ON, (windowState.getFlags() & FLAG_KEEP_SCREEN_ON));

        final CountDownLatch keepScreenOnUnsetLatch = new CountDownLatch(1);
        mActivityRule.runOnUiThread(() -> mSurfaceView.getViewTreeObserver().addOnDrawListener(
                keepScreenOnUnsetLatch::countDown));
        mTestService.setKeepScreenOnFlag(false);
        keepScreenOnUnsetLatch.await(WAIT_TIMEOUT_S, TimeUnit.SECONDS);

        mWmState.computeState();
        windowState = mWmState.getWindowState(TEST_ACTIVITY);
        // Assert the KEEP_SCREEN_ON flag is removed from the main window.
        assertNotEquals(FLAG_KEEP_SCREEN_ON, (windowState.getFlags() & FLAG_KEEP_SCREEN_ON));
    }

    @Test
    public void testKeepScreenOnAfterDetachSCVH() throws Throwable {
        mEmbeddedView = new Button(mActivity);
        mEmbeddedView.setKeepScreenOn(true);
        addSurfaceViewAbove(DEFAULT_SURFACE_VIEW_WIDTH, DEFAULT_SURFACE_VIEW_HEIGHT);
        mInstrumentation.waitForIdleSync();
        waitUntilEmbeddedViewDrawn();

        mWmState.computeState();
        WindowManagerState.WindowState windowState = mWmState.getWindowState(TEST_ACTIVITY);
        // Assert the KEEP_SCREEN_ON flag is not set on the main window yet.
        assertEquals(FLAG_KEEP_SCREEN_ON, (windowState.getFlags() & FLAG_KEEP_SCREEN_ON));

        // Remove the SurfaceView from main window.
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        mActivityRule.runOnUiThread(() -> {
            mViewParent.removeView(mSurfaceView);
            mSurfaceView.getViewTreeObserver().addOnDrawListener(countDownLatch::countDown);
        });
        countDownLatch.await(WAIT_TIMEOUT_S, TimeUnit.SECONDS);

        mWmState.computeState();
        windowState = mWmState.getWindowState(TEST_ACTIVITY);
        // Assert the KEEP_SCREEN_ON flag is removed from the main window.
        assertNotEquals(FLAG_KEEP_SCREEN_ON, (windowState.getFlags() & FLAG_KEEP_SCREEN_ON));
    }

    @Test
    public void testTransferHostTouchGestureToEmbedded() throws Throwable {
        mEmbeddedView = new Button(mActivity);
        addSurfaceViewBelow(DEFAULT_SURFACE_VIEW_WIDTH, DEFAULT_SURFACE_VIEW_HEIGHT);
        waitUntilEmbeddedViewDrawn();
        waitForEmbeddedWindowComposited(false /*onTop*/, false /*remote*/);

        CountDownLatch receivedTouches = new CountDownLatch(1);
        boolean[] hostGotEvent = new boolean[1];
        boolean[] embeddedGotEvent = new boolean[1];
        mSurfaceViewMotionConsumer = (ev) -> {
            if (hostGotEvent[0]) {
                return;
            }
            hostGotEvent[0] = true;
            mActivity.getWindowManager().transferTouchGesture(
                    mSurfaceView.getRootSurfaceControl().getInputTransferToken(),
                    mVr.getSurfacePackage().getInputTransferToken());
            receivedTouches.countDown();
        };

        mEmbeddedView.setOnTouchListener((v, event) -> {
            if (embeddedGotEvent[0]) {
                return false;
            }
            embeddedGotEvent[0] = true;
            receivedTouches.countDown();
            return false;
        });

        final int[] viewInWindow = new int[2];
        mSurfaceView.getLocationInWindow(viewInWindow);
        Point point = new Point(viewInWindow[0] + 1, viewInWindow[1] + 1);

        CtsWindowInfoUtils.tapOnWindow(mInstrumentation, mSurfaceView::getWindowToken, point,
                mDisplayId);

        assertTrue("Failed to receive touch from host=" + hostGotEvent[0] + " or embedded="
                + embeddedGotEvent[0], receivedTouches.await(WAIT_TIMEOUT_S, TimeUnit.SECONDS));

        assertTrue("Failed to receive touch event in host window", hostGotEvent[0]);
        assertTrue("Failed to receive touch event in embedded window", embeddedGotEvent[0]);
    }

    @Test
    public void testAddScvhToDetachedView() throws Throwable {
        // Create a surface view and wait for its surface to be created.
        CountDownLatch surfaceCreated = new CountDownLatch(1);
        mActivityRule.runOnUiThread(
                () -> {
                    final FrameLayout content = new FrameLayout(mActivity);
                    mSurfaceView = new SurfaceView(mActivity);
                    mSurfaceView.setZOrderOnTop(true);
                    content.addView(
                            mSurfaceView,
                            new FrameLayout.LayoutParams(
                                    DEFAULT_SURFACE_VIEW_WIDTH,
                                    DEFAULT_SURFACE_VIEW_HEIGHT,
                                    Gravity.LEFT | Gravity.TOP));
                    mActivity.setContentView(
                            content,
                            new ViewGroup.LayoutParams(
                                    DEFAULT_SURFACE_VIEW_WIDTH, DEFAULT_SURFACE_VIEW_HEIGHT));
                    mSurfaceView
                            .getHolder()
                            .addCallback(new SurfaceCreatedCallback(surfaceCreated));

                    // Create an embedded view.
                    mVr =
                            new SurfaceControlViewHost(
                                    (Context) mActivity, mActivity.getDisplay(), (IBinder) null);
                    mEmbeddedView = new ClickableButton(mActivity);
                    mVr.setView(mEmbeddedView, mEmbeddedViewWidth, mEmbeddedViewHeight);
                });
        assertTrue(
                "Failed to wait for SurfaceView created",
                surfaceCreated.await(WAIT_TIMEOUT_S, TimeUnit.SECONDS));

        CountDownLatch surfacePackageReparented = new CountDownLatch(1);
        mActivityRule.runOnUiThread(
                () -> {
                    mSurfaceView.setChildSurfacePackage(mVr.getSurfacePackage());
                    SurfaceControl.Transaction t = new SurfaceControl.Transaction();
                    t.addTransactionCommittedListener(
                            Runnable::run, surfacePackageReparented::countDown);
                    mSurfaceView.getRootSurfaceControl().applyTransactionOnDraw(t);
                });
        assertTrue(
                "Failed to wait for surface package to get reparented",
                surfacePackageReparented.await(WAIT_TIMEOUT_S, TimeUnit.SECONDS));

        mInstrumentation.waitForIdleSync();
        waitUntilEmbeddedViewDrawn();

        // When surface view is focused, it should transfer focus to the embedded view.
        requestSurfaceViewFocus();
        assertWindowFocused(mEmbeddedView, true);
        // assert host does not have focus
        assertWindowFocused(mSurfaceView, false);

        // When surface view is no longer focused, it should transfer focus back to the host window.
        mActivityRule.runOnUiThread(() -> mSurfaceView.setFocusable(false));
        assertWindowFocused(mEmbeddedView, false);
        // assert host has focus
        assertWindowFocused(mSurfaceView, true);
    }

    @ApiTest(
            apis = {
                "android.view.SurfaceControlViewHost#setView",
                "android.view.SurfaceControlViewHost#relayout",
                "android.view.SurfaceControlViewHost.LayoutParams#LayoutParams",
                "android.view.SurfaceControlViewHost.LayoutParams#setFocusable"
            })
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_SCVH_SET_FOCUSABLE_API)
    public void testLayoutParams_setFocusable() throws Throwable {
        mEmbeddedView = new Button(mActivity);

        final SurfaceControlViewHost.LayoutParams embeddedLayoutParams =
                new SurfaceControlViewHost.LayoutParams(
                        mEmbeddedViewWidth, mEmbeddedViewHeight, true /* focusable */);
        mViewInitializer = (host, view) -> host.setView(view, embeddedLayoutParams);

        addSurfaceViewAbove(DEFAULT_SURFACE_VIEW_WIDTH, DEFAULT_SURFACE_VIEW_HEIGHT);
        mInstrumentation.waitForIdleSync();
        waitUntilEmbeddedViewDrawn();

        // The embedded view should be focusable by default.
        requestSurfaceViewFocus();
        assertWindowFocused(mEmbeddedView, true);
        assertWindowFocused(mSurfaceView, false);

        // Make the embedded view non-focusable.
        mActivityRule.runOnUiThread(
                () -> {
                    SurfaceControlViewHost.LayoutParams lp =
                            new SurfaceControlViewHost.LayoutParams(
                                    mEmbeddedViewWidth,
                                    mEmbeddedViewHeight,
                                    /* focusable= */ false);
                    mVr.relayout(lp);
                });
        mInstrumentation.waitForIdleSync();

        // The embedded view should not be focusable.
        requestSurfaceViewFocus();
        assertWindowFocused(mEmbeddedView, false);
        assertWindowFocused(mSurfaceView, true);

        // Make the embedded view focusable again.
        mActivityRule.runOnUiThread(
                () -> {
                    SurfaceControlViewHost.LayoutParams lp =
                            new SurfaceControlViewHost.LayoutParams(
                                    mEmbeddedViewWidth, mEmbeddedViewHeight, /* focusable= */ true);
                    mVr.relayout(lp);
                });
        mInstrumentation.waitForIdleSync();

        // The embedded view should be focusable.
        requestSurfaceViewFocus();
        assertWindowFocused(mEmbeddedView, true);
        assertWindowFocused(mSurfaceView, false);
    }

    @ApiTest(
            apis = {
                "android.view.SurfaceControlViewHost#relayout",
                "android.view.SurfaceControlViewHost#getLayoutParams",
                "android.view.SurfaceControlViewHost.LayoutParams#LayoutParams",
                "android.view.SurfaceControlViewHost.LayoutParams#isFocusable",
                "android.view.SurfaceControlViewHost.LayoutParams#getWidth",
                "android.view.SurfaceControlViewHost.LayoutParams#getHeight"
            })
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_SCVH_SET_FOCUSABLE_API)
    public void testGetLayoutParams() throws Throwable {
        mEmbeddedView = new Button(mActivity);
        addSurfaceViewAbove(DEFAULT_SURFACE_VIEW_WIDTH, DEFAULT_SURFACE_VIEW_HEIGHT);
        mInstrumentation.waitForIdleSync();
        waitUntilEmbeddedViewDrawn();

        // Check default layout params.
        SurfaceControlViewHost.LayoutParams lp = mVr.getLayoutParams();
        assertEquals(mEmbeddedViewWidth, lp.getWidth());
        assertEquals(mEmbeddedViewHeight, lp.getHeight());
        assertTrue(lp.isFocusable());

        // Change layout params and check again.
        mActivityRule.runOnUiThread(
                () -> {
                    SurfaceControlViewHost.LayoutParams newLp =
                            new SurfaceControlViewHost.LayoutParams(200, 300, false);
                    mVr.relayout(newLp);
                });
        mInstrumentation.waitForIdleSync();

        lp = mVr.getLayoutParams();
        assertEquals(200, lp.getWidth());
        assertEquals(300, lp.getHeight());
        assertFalse(lp.isFocusable());
    }

    private void assertViewHasFocus(String message, View view) {
        if (!waitForViewFocus(view, true)) {
            final StringBuilder currentFocusInfo = new StringBuilder();
            try {
                mActivityRule.runOnUiThread(
                        () -> {
                            View hostFocus = mActivity.getCurrentFocus();
                            currentFocusInfo.append("Host focus: ").append(hostFocus);
                            if (mVr != null && mVr.getView() != null) {
                                View embeddedFocus = mVr.getView().findFocus();
                                currentFocusInfo.append(", Embedded focus: ").append(embeddedFocus);
                            }
                        });
            } catch (Throwable t) {
                currentFocusInfo.append("Error getting focus info: ").append(t);
            }
            fail(message + ". " + currentFocusInfo.toString());
        }
    }

    @Test
    @RequiresFlagsEnabled(android.view.flags.Flags.FLAG_ENABLE_WINDOWLESS_WINDOW_FOCUS_NAVIGATION)
    public void testFocusNavigationWithKeys() throws Throwable {
        mEmbeddedViewWidth = 300;
        mEmbeddedViewHeight = 300;

        LinearLayout embeddedLayout = new LinearLayout(mActivity);
        embeddedLayout.setOrientation(LinearLayout.VERTICAL);
        Button embeddedButton1 = new Button(mActivity);
        embeddedLayout.addView(embeddedButton1);
        Button embeddedButton2 = new Button(mActivity);
        embeddedLayout.addView(embeddedButton2);
        mEmbeddedView = embeddedLayout;

        final Button hostButton1 = new Button(mActivity);
        final Button hostButton2 = new Button(mActivity);

        mActivityRule.runOnUiThread(
                () -> {
                    LinearLayout hostLayout = new LinearLayout(mActivity);
                    hostLayout.setOrientation(LinearLayout.VERTICAL);
                    hostLayout.addView(hostButton1);
                    mSurfaceView = new MotionConsumingSurfaceView(mActivity);
                    mSurfaceView.setZOrderOnTop(true);
                    mSurfaceView.setFocusable(true);
                    hostLayout.addView(
                            mSurfaceView,
                            new LinearLayout.LayoutParams(mEmbeddedViewWidth, mEmbeddedViewHeight));
                    hostLayout.addView(hostButton2);
                    mViewParent = hostLayout;
                    mActivity.setContentView(hostLayout);
                    mSurfaceView.getHolder().addCallback(this);
                });
        mInstrumentation.waitForIdleSync();
        waitUntilEmbeddedViewDrawn();

        // Request focus on the first host button
        mActivityRule.runOnUiThread(hostButton1::requestFocus);
        assertViewHasFocus("Host button 1 should have focus", hostButton1);

        // Tab into the embedded window (EmbeddedButton1)
        mInstrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_TAB);
        assertViewHasFocus("Host mSurfaceView should have focus", mSurfaceView);
        assertWindowFocused(mEmbeddedView, true);
        assertViewHasFocus("Embedded button 1 should have focus", embeddedButton1);

        // Tab to next view in embedded window (EmbeddedButton2)
        mInstrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_TAB);
        assertViewHasFocus("Embedded button 2 should have focus", embeddedButton2);

        // Tab out of embedded window back to SurfaceView then HostButton2
        mInstrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_TAB);
        assertWindowFocused(mSurfaceView, true);
        assertViewHasFocus("Host button 2 should have focus", hostButton2);

        // To keep things interesting we provide embedded button 1 instead of 2 focused.
        // This is a limitation with the existing implementation of the focus search. Fix
        // is tracked by b/464073515
        // Shift+Tab to previous view in embedded window (EmbeddedButton1)
        sendShiftTab();
        assertViewHasFocus("Embedded button 1 should have focus", embeddedButton1);

        // Shift+Tab out of embedded window to HostButton1
        sendShiftTab();
        assertWindowFocused(mSurfaceView, true);
        assertViewHasFocus("Host button 1 should have focus", hostButton1);
    }

    private void sendShiftTab() {
        long downTime = SystemClock.uptimeMillis();
        long eventTime = SystemClock.uptimeMillis();
        KeyEvent down =
                new KeyEvent(
                        downTime,
                        eventTime,
                        KeyEvent.ACTION_DOWN,
                        KeyEvent.KEYCODE_TAB,
                        0,
                        KeyEvent.META_SHIFT_ON);
        mInstrumentation.sendKeySync(down);

        downTime = SystemClock.uptimeMillis();
        eventTime = SystemClock.uptimeMillis();
        KeyEvent up =
                new KeyEvent(
                        downTime,
                        eventTime,
                        KeyEvent.ACTION_UP,
                        KeyEvent.KEYCODE_TAB,
                        0,
                        KeyEvent.META_SHIFT_ON);
        mInstrumentation.sendKeySync(up);
    }
}
