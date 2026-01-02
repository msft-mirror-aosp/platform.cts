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

package android.display.cts;

import static android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY;
import static android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC;
import static android.util.DisplayMetrics.DENSITY_HIGH;
import static android.util.DisplayMetrics.DENSITY_MEDIUM;
import static android.view.Display.INVALID_DISPLAY;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.util.SparseArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import java.util.Arrays;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/** Tests that applications can receive display events correctly. */
@RunWith(Parameterized.class)
public class DisplayEventDeliveryTest extends EventDeliveryTestBase {
    private static final String TAG = "DisplayEventDeliveryTest";

    private static final String NAME = TAG;
    private static final int WIDTH = 720;
    private static final int HEIGHT = 480;

    private static final int DISPLAY_ADDED = 1;
    private static final int DISPLAY_CHANGED = 2;
    private static final int DISPLAY_REMOVED = 3;

    private static final String TEST_PACKAGE =
            "com.android.servicestests.apps.displaymanagertestapp";
    private static final String TEST_ACTIVITY = TEST_PACKAGE + ".DisplayEventActivity";
    private static final String TEST_DISPLAYS = "DISPLAYS";
    private static final String TEST_EVENT_MASK = "EVENT_MASK";

    private final Object mLock = new Object();

    private DisplayManager mDisplayManager;

    /**
     * Array of DisplayBundle. The test handler uses it to check if certain display events have been
     * sent to DisplayEventActivity. Key: displayId of each new VirtualDisplay created by this test
     * Value: DisplayBundle, storing the VirtualDisplay and its expected display events
     *
     * <p>NOTE: The lock is required when adding and removing virtual displays. Otherwise it's not
     * necessary to lock mDisplayBundles when accessing it from the test function.
     */
    private SparseArray<DisplayBundle> mDisplayBundles;

    /**
     * Helper class to store VirtualDisplay and its corresponding display events expected to be sent
     * to DisplayEventActivity.
     */
    private static final class DisplayBundle {
        private VirtualDisplay mVirtualDisplay;
        private final int mDisplayId;

        // Display events we expect to receive before timeout
        private final LinkedBlockingQueue<Integer> mExpectations;

        DisplayBundle(@Nullable VirtualDisplay display) {
            mVirtualDisplay = display;
            mDisplayId = display != null ? display.getDisplay().getDisplayId() : INVALID_DISPLAY;
            mExpectations = new LinkedBlockingQueue<>();
        }

        public void releaseDisplay() {
            if (mVirtualDisplay != null) {
                mVirtualDisplay.release();
            }
            mVirtualDisplay = null;
        }

        /**
         * Add the received display event from the test activity to the queue
         *
         * @param event The corresponding display event
         */
        public void addDisplayEvent(int event) {
            Log.d(TAG, "Received " + mDisplayId + " " + event);
            mExpectations.offer(event);
        }

        /** Assert that there isn't any unexpected display event from the test activity */
        public void assertNoDisplayEvents() {
            try {
                assertNull(mExpectations.poll(EVENT_TIMEOUT_MSEC, TimeUnit.MILLISECONDS));
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        /**
         * Wait for the expected display event from the test activity
         *
         * @param expect The expected display event
         */
        public void waitDisplayEvent(int expect) {
            while (true) {
                try {
                    final Integer event;
                    event = mExpectations.poll(TEST_FAILURE_TIMEOUT_MSEC, TimeUnit.MILLISECONDS);
                    assertNotNull(event);
                    if (expect == event) {
                        Log.d(TAG, "Found    " + mDisplayId + " " + event);
                        return;
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    /** How many virtual displays to create during the test */
    @Parameter(0)
    public int mDisplayCount;

    /** True if running the test activity in cached mode False if running it in non-cached mode */
    @Parameter(1)
    public boolean mCached;

    /** Represents the combination of number of displays and the cache state */
    @Parameters(name = "#{index}: {0} {1}")
    public static Iterable<? extends Object> data() {
        return Arrays.asList(
                new Object[][] {
                    {1, false}, {2, false}, {3, false}, {10, false},
                    {1, true}, {2, true}, {3, true}, {10, true}
                });
    }

    private class TestHandler extends Handler {
        TestHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                case MESSAGE_LAUNCHED:
                    mPid = msg.arg1;
                    mUid = msg.arg2;
                    Log.d(TAG, "Launched " + mPid + " " + mUid);
                    mLatchActivityLaunch.countDown();
                    break;
                case MESSAGE_CALLBACK:
                    synchronized (mLock) {
                        // arg1: displayId
                        DisplayBundle bundle = mDisplayBundles.get(msg.arg1);
                        if (bundle != null) {
                            bundle.addDisplayEvent(msg.arg2);
                        }
                    }
                    break;
                default:
                    fail("Unexpected value: " + msg.what);
                    break;
            }
        }
    }

    @Before
    public void setUp() {
        super.setUp();
        mDisplayManager = mContext.getSystemService(DisplayManager.class);
        mDisplayBundles = new SparseArray<>();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
        synchronized (mLock) {
            for (int i = 0; i < mDisplayBundles.size(); i++) {
                DisplayBundle bundle = mDisplayBundles.valueAt(i);
                // Clean up unreleased virtual display
                bundle.releaseDisplay();
            }
            mDisplayBundles.clear();
        }
    }

    @Override
    protected String getTag() {
        return TAG;
    }

    @Override
    protected Handler getHandler(Looper looper) {
        return new TestHandler(looper);
    }

    @Override
    protected String getTestPackage() {
        return TEST_PACKAGE;
    }

    @Override
    protected String getTestActivity() {
        return TEST_ACTIVITY;
    }

    /**
     * Create virtual displays, change their configurations and release them mDisplays: the amount
     * of virtual displays to be created mCached: true to run the test activity in cached mode;
     * false in non-cached mode
     */
    @Test
    public void testDisplayEvents() {
        testDisplayEventsInternal();
    }

    private void testDisplayEventsInternal() {
        Log.d(TAG, "Start test testDisplayEvents " + mDisplayCount + " " + mCached);
        // Launch DisplayEventActivity and start listening to display events
        launchTestActivity(
                intent -> {
                    intent.putExtra(TEST_DISPLAYS, mDisplayCount);
                    intent.putExtra(
                            TEST_EVENT_MASK,
                            DisplayManager.EVENT_TYPE_DISPLAY_ADDED
                                    | DisplayManager.EVENT_TYPE_DISPLAY_CHANGED
                                    | DisplayManager.EVENT_TYPE_DISPLAY_REMOVED);
                });

        if (mCached) {
            // The test activity in cached mode won't receive the pending display events
            makeTestActivityCached();
        }

        // Create new virtual displays
        for (int i = 0; i < mDisplayCount; i++) {
            // Lock is needed here to ensure the handler can query the displays
            synchronized (mLock) {
                VirtualDisplay display = createVirtualDisplay(NAME + i);
                DisplayBundle bundle = new DisplayBundle(display);
                mDisplayBundles.put(bundle.mDisplayId, bundle);
            }
        }

        for (int i = 0; i < mDisplayCount; i++) {
            if (mCached) {
                // DISPLAY_ADDED should be deferred for cached process
                mDisplayBundles.valueAt(i).assertNoDisplayEvents();
            } else {
                // DISPLAY_ADDED should arrive immediately for non-cached process
                mDisplayBundles.valueAt(i).waitDisplayEvent(DISPLAY_ADDED);
            }
        }

        // Change the virtual displays
        for (int i = 0; i < mDisplayCount; i++) {
            DisplayBundle bundle = mDisplayBundles.valueAt(i);
            bundle.mVirtualDisplay.resize(WIDTH, HEIGHT, DENSITY_HIGH);
        }

        for (int i = 0; i < mDisplayCount; i++) {
            if (mCached) {
                // DISPLAY_CHANGED should be deferred for cached process
                mDisplayBundles.valueAt(i).assertNoDisplayEvents();
            } else {
                // DISPLAY_CHANGED should arrive immediately for non-cached process
                mDisplayBundles.valueAt(i).waitDisplayEvent(DISPLAY_CHANGED);
            }
        }

        if (mCached) {
            // The test activity becomes non-cached and should receive the pending display events
            bringTestActivityTop();

            for (int i = 0; i < mDisplayCount; i++) {
                // The pending DISPLAY_ADDED & DISPLAY_CHANGED should arrive now
                mDisplayBundles.valueAt(i).waitDisplayEvent(DISPLAY_ADDED);
                mDisplayBundles.valueAt(i).waitDisplayEvent(DISPLAY_CHANGED);
            }
        }

        // Release the virtual displays
        for (int i = 0; i < mDisplayCount; i++) {
            mDisplayBundles.valueAt(i).releaseDisplay();
        }

        // DISPLAY_REMOVED should arrive now
        for (int i = 0; i < mDisplayCount; i++) {
            mDisplayBundles.valueAt(i).waitDisplayEvent(DISPLAY_REMOVED);
        }
    }

    /**
     * Create a virtual display
     *
     * @param name The name of the new virtual display
     * @return The new virtual display
     */
    private VirtualDisplay createVirtualDisplay(String name) {
        return mDisplayManager.createVirtualDisplay(
                name,
                WIDTH,
                HEIGHT,
                DENSITY_MEDIUM,
                null /* surface: as we don't actually draw anything, null is enough */,
                VIRTUAL_DISPLAY_FLAG_PUBLIC | VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY);
    }
}
