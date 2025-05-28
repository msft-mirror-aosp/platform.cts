/*
 * Copyright (C) 2025 The Android Open Source Project
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

import static android.view.WindowManager.DISPLAY_IME_POLICY_LOCAL;

import static com.android.server.display.feature.flags.Flags.FLAG_DISPLAY_TOPOLOGY;
import static com.android.server.display.feature.flags.Flags.FLAG_DISPLAY_TOPOLOGY_API;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import android.content.Intent;
import android.hardware.display.DisplayTopology;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.server.wm.ActivityManagerTestBase;
import android.server.wm.WindowManagerState;
import android.util.Log;

import androidx.annotation.NonNull;

import org.junit.Rule;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/** Tests that applications can receive topology updates correctly. */
public class TopologyUpdateDeliveryTest extends EventDeliveryTestBase {
    private static final String TAG = TopologyUpdateDeliveryTest.class.getSimpleName();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final String TEST_PACKAGE = "com.android.servicestests.apps.topologytestapp";
    private static final String TEST_ACTIVITY = TEST_PACKAGE + ".TopologyUpdateActivity";

    // Topology updates we expect to receive before timeout
    private final LinkedBlockingQueue<DisplayTopology> mExpectations = new LinkedBlockingQueue<>();

    /**
     * Add the received topology update from the test activity to the queue
     *
     * @param topology The corresponding topology update
     */
    private void addTopologyUpdate(DisplayTopology topology) {
        Log.d(TAG, "Received " + topology);
        mExpectations.offer(topology);
    }

    /** Assert that there isn't any unexpected display event from the test activity */
    private void assertNoTopologyUpdates() {
        try {
            assertNull(mExpectations.poll(EVENT_TIMEOUT_MSEC, TimeUnit.MILLISECONDS));
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Wait for the expected topology update from the test activity
     *
     * @param predicate The predicate that the new topology needs to satisfy
     */
    private void waitTopologyUpdate(Predicate<DisplayTopology> predicate) {
        while (true) {
            try {
                DisplayTopology update =
                        mExpectations.poll(TEST_FAILURE_TIMEOUT_MSEC, TimeUnit.MILLISECONDS);
                assertNotNull(update);
                if (predicate.test(update)) {
                    Log.d(TAG, "Found " + update);
                    return;
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
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
                    break;
                case MESSAGE_CALLBACK:
                    DisplayTopology topology = (DisplayTopology) msg.obj;
                    Log.d(TAG, "Callback " + topology);
                    addTopologyUpdate(topology);
                    break;
                default:
                    fail("Unexpected value: " + msg.what);
                    break;
            }
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

    @Override
    protected void putExtra(Intent intent) {}

    private void testTopologyUpdateInternal(boolean cached) {
        Log.d(TAG, "Start test testTopologyUpdate " + cached);
        // Launch activity and start listening to topology updates
        int pid = launchTestActivity();

        // The test activity in cached or frozen mode won't receive the pending topology updates.
        if (cached) {
            makeTestActivityCached();
        }

        // Create a new display that will be added to the topology
        final List<WindowManagerState.DisplayContent> originalDs = getDisplaysStates();
        int initialNumberOfDisplays = originalDs.size();
        try (ActivityManagerTestBase.VirtualDisplaySession virtualDisplaySession =
                new ActivityManagerTestBase.VirtualDisplaySession()) {
            virtualDisplaySession.setSimulateDisplay(true);
            virtualDisplaySession.setAllowContentModeSwitch(true);
            virtualDisplaySession.setDisplayImePolicy(DISPLAY_IME_POLICY_LOCAL);
            virtualDisplaySession.createDisplays(2);
            getDisplayStateAfterChange(initialNumberOfDisplays + 2);
            Predicate<DisplayTopology> predicate =
                    topology -> topology.getAbsoluteBounds().size() > initialNumberOfDisplays;

            if (cached) {
                assertNoTopologyUpdates();
            } else {
                waitTopologyUpdate(predicate);
            }

            if (cached) {
                // Always ensure the test activity is not cached.
                bringTestActivityTop();

                // The test activity becomes non-cached and should receive the pending topology
                // updates
                waitTopologyUpdate(predicate);
            }
        }
    }

    @Test
    @RequiresFlagsEnabled({FLAG_DISPLAY_TOPOLOGY, FLAG_DISPLAY_TOPOLOGY_API})
    public void testTopologyUpdate() {
        testTopologyUpdateInternal(false);
    }

    /**
     * The app is moved to cached and the test verifies that no updates are delivered to the cached
     * app.
     */
    @Test
    @RequiresFlagsEnabled({FLAG_DISPLAY_TOPOLOGY, FLAG_DISPLAY_TOPOLOGY_API})
    public void testTopologyUpdateCached() {
        testTopologyUpdateInternal(true);
    }
}
