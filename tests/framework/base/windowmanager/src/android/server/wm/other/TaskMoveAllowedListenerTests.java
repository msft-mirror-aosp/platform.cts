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

package android.server.wm.other;

import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.server.wm.StateLogger.logAlways;
import static android.server.wm.app.Components.TaskMoveTestActivity.ACTION_NOTIFY_LISTENER_CALLED;
import static android.server.wm.app.Components.TaskMoveTestActivity.ACTION_REGISTER_LISTENER;
import static android.server.wm.app.Components.TaskMoveTestActivity.ACTION_REGISTER_LISTENER_ACK;
import static android.server.wm.app.Components.TaskMoveTestActivity.ACTION_UNREGISTER_LISTENER;
import static android.server.wm.app.Components.TaskMoveTestActivity.ACTION_UNREGISTER_LISTENER_ACK;
import static android.server.wm.app.Components.TaskMoveTestActivity.EXTRA_TMA_KEYS_ARRAY_KEY;
import static android.server.wm.app.Components.TaskMoveTestActivity.EXTRA_TMA_VALUES_ARRAY_KEY;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.ActivityManager;
import android.app.TaskDisplayPolicyState;
import android.content.Intent;
import android.content.IntentFilter;
import android.platform.test.annotations.Presubmit;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.server.wm.TaskMoveTestBase;
import android.server.wm.WindowManagerState.DisplayContent;
import android.util.SparseBooleanArray;

import com.android.compatibility.common.util.ApiTest;
import com.android.window.flags.Flags;

import org.junit.Rule;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/** Build/Install/Run: atest CtsWindowManagerDeviceOther:TaskMoveAllowedListenerTests */
@Presubmit
@android.server.wm.annotation.Group3
@RequiresFlagsEnabled({
    Flags.FLAG_ENABLE_WINDOW_REPOSITIONING_API,
    Flags.FLAG_ENABLE_TASK_MOVE_ALLOWED_LISTENER_API
})
public class TaskMoveAllowedListenerTests extends TaskMoveTestBase {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Override
    protected IntentFilter getIntentFilter() {
        final IntentFilter filter = super.getIntentFilter();
        filter.addAction(ACTION_NOTIFY_LISTENER_CALLED);
        filter.addAction(ACTION_REGISTER_LISTENER_ACK);
        filter.addAction(ACTION_UNREGISTER_LISTENER_ACK);
        return filter;
    }

    /**
     * Tests that a listener registered using the {@link
     * ActivityManager#registerTaskDisplayPolicyStateListener} method gets notified after it is
     * registered.
     */
    @ApiTest(apis = "android.app.ActivityManager#registerTaskDisplayPolicyStateListener")
    @Test
    public void testRegisterTaskMoveAllowedListener_getsNotifiedWhenRegistered() {
        final int displayId = getMainDisplayId();
        launchActivityOnDisplay(TEST_ACTIVITY, displayId);
        mWmState.computeState(TEST_ACTIVITY);

        registerListener();

        assertListenerGotCalled();
    }

    /**
     * Tests that a listener registered using the {@link
     * ActivityManager#registerTaskDisplayPolicyStateListener} method gets notified after it is
     * registered and that the {@link List} of {@link TaskDisplayPolicyState} passed contains the
     * main {@link Display} and {@link TaskDisplayPolicyState#TASK_MOVE_ALLOWED} for move state.
     * Assumes that task moving is allowed on the main display.
     */
    @ApiTest(
            apis = {
                "android.app.ActivityManager#isTaskMoveAllowedOnDisplay",
                "android.app.ActivityManager#registerTaskDisplayPolicyStateListener"
            })
    @Test
    public void testRegisterTaskMoveAllowedListener_getsNotifiedWithCorrectValueWhenRegistered() {
        final int displayId = getMainDisplayId();
        launchActivityOnDisplay(TEST_ACTIVITY, displayId);
        mWmState.computeState(TEST_ACTIVITY);
        assumeTaskMoveAllowedOnDisplay(displayId);

        registerListener();

        final SparseBooleanArray tmaValues = assertListenerGotCalled();
        assertTrue(
                "The display ID of the main display is not present in the SparseBooleanArray passed"
                        + " to the listener",
                tmaValues.indexOfKey(displayId) >= 0);
        assertTrue(
                "The task-move-allowed state of the main display as passed in the"
                        + " SparseBooleanArray is incorrect",
                tmaValues.get(displayId));
    }

    /**
     * Tests that a listener registered using the {@link
     * ActivityManager#registerTaskDisplayPolicyStateListener} method gets notified after a new
     * {@link Display} is added to the system and that the {@link List} of {@link
     * TaskDisplayPolicyState} passed contains the new {@link Display}.
     */
    @ApiTest(apis = "android.app.ActivityManager#registerTaskDisplayPolicyStateListener")
    @Test
    public void testRegisterTaskMoveAllowedListener_getsNotifiedWhenDisplayConnected() {
        launchActivityOnDisplay(TEST_ACTIVITY, getMainDisplayId());
        mWmState.computeState(TEST_ACTIVITY);
        registerListener();

        // The listener is called immediately upon registration. Reset the signal here so we can
        // wait for the *next* callback, which should be triggered by the display connection.
        clearBroadcastData(ACTION_NOTIFY_LISTENER_CALLED);

        final int newDisplayId = createNewDisplay();

        final SparseBooleanArray tmaValues = assertListenerGotCalled();
        assertTrue(
                "The display ID of the added display is not present in the SparseBooleanArray"
                        + " received by the listener",
                tmaValues.indexOfKey(newDisplayId) >= 0);
    }

    /**
     * Tests that a listener registered using the {@link
     * ActivityManager#registerTaskDisplayPolicyStateListener} method and then unregistered using
     * the {@link ActivityManager#unregisterTaskDisplayPolicyStateListener} method does not get
     * notified after a new display is added to the system.
     */
    @ApiTest(
            apis = {
                "android.app.ActivityManager#registerTaskDisplayPolicyStateListener",
                "android.app.ActivityManager#unregisterTaskDisplayPolicyStateListener"
            })
    @Test
    public void testTaskMoveAllowedListener_doesNotGetNotifiedAfterUnregistered() {
        launchActivityOnDisplay(TEST_ACTIVITY, getMainDisplayId());
        mWmState.computeState(TEST_ACTIVITY);

        registerListener();
        unregisterListener();

        // Clear any received broadcasts related to the listener getting called.
        clearBroadcastData(ACTION_NOTIFY_LISTENER_CALLED);

        createNewDisplay();

        assertListenerNotCalled();
    }

    /**
     * Tests that a listener registered using the {@link
     * ActivityManager#registerTaskDisplayPolicyStateListener} method gets notified after it is
     * registered and that the {@link List} of {@link TaskDisplayPolicyState} passed to the listener
     * is precisely equal to set of all {@link Display}s of all currently active {@link Display}s of
     * the system.
     */
    @ApiTest(apis = "android.app.ActivityManager#registerTaskDisplayPolicyStateListener")
    @Test
    public void testRegisterTaskMoveAllowedListener_getsNotifiedWithMapWithCorrectKeySet() {
        launchActivityOnDisplay(TEST_ACTIVITY, getMainDisplayId());
        mWmState.computeState(TEST_ACTIVITY);

        registerListener();

        final SparseBooleanArray tmaValuesMap = assertListenerGotCalled();
        final List<DisplayContent> systemDisplays = mWmState.getDisplays();

        final int[] tmaValuesMapKeys = new int[tmaValuesMap.size()];
        for (int i = 0; i < tmaValuesMap.size(); i++) {
            tmaValuesMapKeys[i] = tmaValuesMap.keyAt(i);
        }
        final int[] systemDisplayIds = new int[systemDisplays.size()];
        for (int i = 0; i < systemDisplays.size(); i++) {
            systemDisplayIds[i] = systemDisplays.get(i).mId;
        }

        // Both arrays passed here should not contain duplicates so equality of sets (not multisets)
        // represented by these arrays would also be sufficient.
        assertArraysRepresentEqualMultisets(
                "Display IDs from the system do not match keys in the SparseBooleanArray",
                systemDisplayIds,
                tmaValuesMapKeys);
    }

    /**
     * Tests that a listener registered using the {@link
     * ActivityManager#registerTaskDisplayPolicyStateListener} method gets notified when the
     * task-move-allowed state changes on the main display.
     */
    @ApiTest(
            apis = {
                "android.app.ActivityManager#registerTaskDisplayPolicyStateListener",
                "android.app.ActivityManager#unregisterTaskDisplayPolicyStateListener"
            })
    @Test
    public void testRegisterTaskMoveAllowedListener_getsNotifiedWhenTmaStateChanges() {
        final int displayId = getMainDisplayId();
        launchActivityOnDisplay(TEST_ACTIVITY, displayId);
        mWmState.computeState(TEST_ACTIVITY);
        assumeTaskMoveAllowedOnDisplay(displayId);

        registerListener();
        // The listener is called immediately upon registration. Reset the signal here so we can
        // wait for the *next* callback, which should be triggered by the display connection.
        clearBroadcastData(ACTION_NOTIFY_LISTENER_CALLED);

        // Try to make TMA state on the display false by changing the windowing mode to fullscreen.
        launchActivityOnDisplay(TEST_ACTIVITY, WINDOWING_MODE_FULLSCREEN, displayId);
        mWmState.computeState(TEST_ACTIVITY);

        // Check whether the TMA state was actually changed to false.
        assumeTaskMoveNotAllowedOnDisplay(displayId);

        final SparseBooleanArray tmaValues = assertListenerGotCalled();
        assertFalse(
                "The task-move-allowed state of the main display as passed in the"
                        + " SparseBooleanArray is incorrect",
                tmaValues.get(displayId));
    }

    private SparseBooleanArray assertListenerGotCalled() {
        final boolean notified = awaitBroadcast(ACTION_NOTIFY_LISTENER_CALLED);
        final Intent response = getIntentOfBroadcast(ACTION_NOTIFY_LISTENER_CALLED);

        if (!notified || response == null) {
            fail("The activity has not notified about the listener getting called");
        }

        if (!response.hasExtra(EXTRA_TMA_KEYS_ARRAY_KEY)
                || !response.hasExtra(EXTRA_TMA_VALUES_ARRAY_KEY)) {
            fail(
                    "The activity notified about the listener getting called but intent does not"
                        + " hold a correct representation of the SparseBooleanArray passed to the"
                        + " listener");
        }

        final SparseBooleanArray array = new SparseBooleanArray();
        final int[] keys = response.getIntArrayExtra(EXTRA_TMA_KEYS_ARRAY_KEY);
        final boolean[] values = response.getBooleanArrayExtra(EXTRA_TMA_VALUES_ARRAY_KEY);

        for (int i = 0; i < keys.length; i++) {
            array.append(keys[i], values[i]);
        }

        return array;
    }

    private void assertListenerNotCalled() {
        assertFalse(
                "The activity notified about the listener getting called (it shouldn't have been)",
                awaitBroadcast(ACTION_NOTIFY_LISTENER_CALLED));
    }

    /**
     * This returns only after an acknowledgement from the test activity has been received. If the
     * acknowledgement has not been received in a reasonable time, it fails the test.
     */
    private void registerListener() {
        logAlways("Sending ACTION_REGISTER_LISTENER intent");
        mContext.sendBroadcast(
                new Intent(ACTION_REGISTER_LISTENER).setFlags(Intent.FLAG_RECEIVER_FOREGROUND));

        assertTrue(
                "The activity has not acknowledged the ACTION_REGISTER_LISTENER intent",
                awaitBroadcast(ACTION_REGISTER_LISTENER_ACK));
    }

    /**
     * This returns only after an acknowledgement from the test activity has been received. If the
     * acknowledgement has not been received in a reasonable time, it fails the test.
     */
    private void unregisterListener() {
        logAlways("Sending ACTION_UNREGISTER_LISTENER intent");
        mContext.sendBroadcast(
                new Intent(ACTION_UNREGISTER_LISTENER).setFlags(Intent.FLAG_RECEIVER_FOREGROUND));

        assertTrue(
                "The activity has not acknowledged the ACTION_UNREGISTER_LISTENER intent",
                awaitBroadcast(ACTION_UNREGISTER_LISTENER_ACK));
    }

    private void assertArraysRepresentEqualMultisets(String message, int[] expected, int[] actual) {
        final int[] expectedSorted = expected.clone();
        Arrays.sort(expectedSorted);

        final int[] actualSorted = actual.clone();
        Arrays.sort(actualSorted);

        assertArrayEquals(message, expectedSorted, actualSorted);
    }
}
