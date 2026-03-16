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

package android.view.surfacecontrol.cts;

import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.graphics.Region;
import android.os.IBinder;
import android.server.wm.BuildUtils;
import android.server.wm.CtsWindowInfoUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.PopupWindow;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.CtsTouchUtils;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public class SetTouchableRegionTest {
    private static final long WAIT_TIME_MS = 5000L * BuildUtils.HW_TIMEOUT_MULTIPLIER;
    private static final String TAG = "SetTouchableRegionTest";

    @Rule
    public TestName mName = new TestName();

    private Instrumentation mInstrumentation;
    private CtsTouchUtils mCtsTouchUtils;
    private Activity mActivity;
    @Rule
    public ActivityTestRule<CtsActivity> mActivityRule = new ActivityTestRule<>(CtsActivity.class);

    class MotionRecordingView extends View {
        public MotionRecordingView(Context context) {
            super(context);
        }

        private boolean mGotEvent = false;

        public boolean onTouchEvent(MotionEvent e) {
            super.onTouchEvent(e);
            synchronized (this) {
                mGotEvent = true;
                notifyAll();
            }
            return true;
        }

        boolean waitForEvent(boolean receivedEvent) throws InterruptedException {
            synchronized (this) {
                if (mGotEvent != receivedEvent) {
                    wait(WAIT_TIME_MS);
                }
                return mGotEvent == receivedEvent;
            }
        }

        void reset() {
            synchronized (this) {
                mGotEvent = false;
            }
        }
    }

    MotionRecordingView mMotionRecordingView;
    View mPopupView;

    @Before
    public void setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mCtsTouchUtils = new CtsTouchUtils(mInstrumentation.getTargetContext());
        mActivity = mActivityRule.getActivity();
    }

    private void waitForPopupOnTop() throws InterruptedException {
        final AtomicReference<IBinder> popupTokenRef = new AtomicReference<>();
        mInstrumentation.runOnMainSync(() -> popupTokenRef.set(mPopupView.getWindowToken()));
        assertTrue(
                "Popup window not on top",
                CtsWindowInfoUtils.waitForWindowOnTop(
                        Duration.ofMillis(WAIT_TIME_MS), () -> popupTokenRef.get()));
    }

    private void waitForWindowTouchableRegion(View view, Region expectedRegion)
            throws InterruptedException {
        final AtomicReference<IBinder> tokenRef = new AtomicReference<>();
        mInstrumentation.runOnMainSync(() -> tokenRef.set(view.getWindowToken()));
        assertTrue(
                "Window " + view + " never reached expected touchable region",
                CtsWindowInfoUtils.waitForWindowInfos(
                        windowInfos -> {
                            for (var windowInfo : windowInfos) {
                                if (windowInfo.windowToken == tokenRef.get()) {
                                    return expectedRegion.equals(windowInfo.touchableRegion);
                                }
                            }
                            return false;
                        },
                        Duration.ofMillis(WAIT_TIME_MS)));
    }

    void tapSync() throws InterruptedException {
        mInstrumentation.getUiAutomation().syncInputTransactions();

        mInstrumentation.waitForIdleSync();
        assertTrue(mMotionRecordingView.waitForEvent(false /* receivedEvent */));

        mCtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mActivityRule,
                mMotionRecordingView);
        mInstrumentation.waitForIdleSync();
    }

    @Test
    public void testClickthroughRegion() throws Throwable {
        CountDownLatch waitForContent = new CountDownLatch(1);
        mActivityRule.runOnUiThread(() -> {
            mMotionRecordingView = new MotionRecordingView(mActivity);
            mActivity.setContentView(mMotionRecordingView);
            mMotionRecordingView.getViewTreeObserver().addOnPreDrawListener(
                    new ViewTreeObserver.OnPreDrawListener() {
                        @Override
                        public boolean onPreDraw() {
                            waitForContent.countDown();
                            mMotionRecordingView.getViewTreeObserver().removeOnPreDrawListener(
                                    this);
                            return true;
                        }
                    });
        });
        assertTrue("Failed to wait for content to draw",
                waitForContent.await(WAIT_TIME_MS, TimeUnit.MILLISECONDS));

        tapSync();
        // We have a view filling our entire hierarchy and so a tap should reach it
        assertTrue("Failed to wait for initial motion event",
                mMotionRecordingView.waitForEvent(true /* receivedEvent */));

        CountDownLatch waitForPopupView = new CountDownLatch(1);
        mActivityRule.runOnUiThread(() -> {
            mPopupView = new View(mActivity);
            PopupWindow popup = new PopupWindow(mPopupView,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
            popup.showAtLocation(mMotionRecordingView, Gravity.NO_GRAVITY, 0, 0);
            mPopupView.getViewTreeObserver().addOnPreDrawListener(
                    new ViewTreeObserver.OnPreDrawListener() {
                        @Override
                        public boolean onPreDraw() {
                            waitForPopupView.countDown();
                            mPopupView.getViewTreeObserver().removeOnPreDrawListener(this);
                            return true;
                        }
                    });
        });
        assertTrue("Failed to add popup view",
                waitForPopupView.await(WAIT_TIME_MS, TimeUnit.MILLISECONDS));

        waitForPopupOnTop();

        mMotionRecordingView.reset();
        tapSync();

        // However now we have covered ourselves with a MATCH_PARENT popup window
        // and so the tap should not reach us
        CtsWindowInfoUtils.assertAndDumpWindowState(
                TAG,
                "Received motion event",
                mMotionRecordingView.waitForEvent(false /* receivedEvent */));

        CountDownLatch updateTouchableRegionsLatch = new CountDownLatch(1);
        mActivityRule.runOnUiThread(() -> {
            // Ensure the performTraversal in VRI runs to push the touchable regions to WMS
            mPopupView.getViewTreeObserver().addOnPreDrawListener(
                    new ViewTreeObserver.OnPreDrawListener() {
                        @Override
                        public boolean onPreDraw() {
                            updateTouchableRegionsLatch.countDown();
                            mPopupView.getViewTreeObserver().removeOnPreDrawListener(this);
                            return true;
                        }
                    });
            mPopupView.getRootSurfaceControl().setTouchableRegion(new Region());
        });
        assertTrue("Failed to update touchable regions for popup view",
                updateTouchableRegionsLatch.await(WAIT_TIME_MS, TimeUnit.MILLISECONDS));

        waitForWindowTouchableRegion(mPopupView, new Region());

        tapSync();
        // But now we have punched a touchable region hole in the popup window and
        // we should be reachable again.
        CtsWindowInfoUtils.assertAndDumpWindowState(
                TAG,
                "Failed to receive motion event",
                mMotionRecordingView.waitForEvent(true /* receivedEvent */));
    }
}
