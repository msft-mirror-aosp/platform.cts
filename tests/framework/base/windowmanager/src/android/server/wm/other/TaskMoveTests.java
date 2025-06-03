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

import static android.server.wm.app.Components.MoveTaskToBoundsActivity.ACTION_CHECK_IS_TASK_MOVE_ALLOWED;
import static android.server.wm.app.Components.MoveTaskToBoundsActivity.ACTION_NOTIFY_TASK_MOVE_ALLOWED_RESULT;
import static android.server.wm.app.Components.MoveTaskToBoundsActivity.ACTION_NOTIFY_TASK_MOVE_REQUEST_RESULT;
import static android.server.wm.app.Components.MoveTaskToBoundsActivity.ACTION_REQUEST_TASK_MOVE;
import static android.server.wm.app.Components.MoveTaskToBoundsActivity.EXTRA_BOUNDS_KEY;
import static android.server.wm.app.Components.MoveTaskToBoundsActivity.EXTRA_DISPLAY_ID_KEY;
import static android.server.wm.app.Components.MoveTaskToBoundsActivity.EXTRA_EXCEPTION_KEY;
import static android.server.wm.app.Components.MoveTaskToBoundsActivity.EXTRA_SYNC_EXCEPTION_KEY;
import static android.server.wm.app.Components.MoveTaskToBoundsActivity.EXTRA_TASK_MOVE_ALLOWED_RESULT;
import static android.view.WindowManager.DISPLAY_IME_POLICY_LOCAL;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Rect;
import android.os.ConditionVariable;
import android.os.UserHandle;
import android.platform.test.annotations.Presubmit;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.server.wm.MultiDisplayTestBase;
import android.server.wm.ShellCommandHelper;
import android.server.wm.WindowManagerState;
import android.server.wm.WindowManagerState.Task;
import android.server.wm.app.Components;
import android.view.Display;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.CddTest;
import com.android.window.flags.Flags;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Build/Install/Run:
 *     atest CtsWindowManagerDeviceOther:TaskMoveTests
 */
@Presubmit
@android.server.wm.annotation.Group3
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_WINDOW_REPOSITIONING_API)
public class TaskMoveTests extends MultiDisplayTestBase {

    private static final ComponentName TEST_ACTIVITY = Components.MOVE_TASK_TO_BOUNDS_ACTIVITY;
    private Map<String, ConditionVariable> mBroadcastsReceived;
    private Map<String, Intent> mBroadcastsContentsReceived;
    private BroadcastReceiver mAppCommunicator =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    getBroadcastReceivedVariable(intent.getAction()).open();
                    mBroadcastsContentsReceived.put(intent.getAction(), intent);
                }
            };

    private ConditionVariable getBroadcastReceivedVariable(String action) {
        return mBroadcastsReceived.computeIfAbsent(action, k -> new ConditionVariable());
    }

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();

        mBroadcastsReceived = Collections.synchronizedMap(new HashMap<>());
        mBroadcastsContentsReceived = Collections.synchronizedMap(new HashMap<>());
        final IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_NOTIFY_TASK_MOVE_ALLOWED_RESULT);
        filter.addAction(ACTION_NOTIFY_TASK_MOVE_REQUEST_RESULT);
        mContext.registerReceiver(mAppCommunicator, filter, Context.RECEIVER_EXPORTED);
        grantBrowserRole();
    }

    @After
    public void tearDown() {
        revokeBrowserRole();
        mContext.unregisterReceiver(mAppCommunicator);
        Components.forceStopPackage();
    }

    /**
     * Tests that a {@link android.app.ActivityManager.AppTask#moveTaskTo} call succeeds when the
     * request is to move the task 10 pixels to the right. Assumes that task moving is allowed on
     * the host display.
     */
    @ApiTest(
            apis = {
                "android.app.ActivityManager.AppTask#moveTaskTo",
                "android.app.ActivityManager#isTaskMoveAllowedOnDisplay"
            })
    @Test
    public void testMoveTaskTo_move10PxRight() {
        final int displayId = Display.DEFAULT_DISPLAY;
        launchActivityOnDisplay(TEST_ACTIVITY, displayId);
        mWmState.computeState(TEST_ACTIVITY);

        assumeTaskMoveAllowedOnDisplay(displayId);

        final WindowManagerState.Task task = mWmState.getTaskByActivity(TEST_ACTIVITY);
        final Rect initialBounds = task.getBounds();
        final Rect requestedBounds = new Rect(initialBounds);
        requestedBounds.offset(10, 0);

        sendTaskMoveRequest(displayId, requestedBounds);
        assertTaskMoveRequestReportedSuccess();
        assertSaneTaskLocation(TEST_ACTIVITY, displayId, initialBounds, displayId, requestedBounds);
    }

    /**
     * Tests that a {@link android.app.ActivityManager.AppTask#moveTaskTo} call whose requested task
     * location has bounds smaller than the CDD requires (width and height no smaller than 220dp)
     * does not resize the task to be smaller than the CDD requirement. Assumes that task moving is
     * allowed on the host display.
     */
    @ApiTest(
            apis = {
                "android.app.ActivityManager.AppTask#moveTaskTo",
                "android.app.ActivityManager#isTaskMoveAllowedOnDisplay"
            })
    @CddTest(requirements = "3.8.14/C-1-4")
    @Test
    public void testMoveTaskTo_resizeToTooSmallBounds() {
        final int displayId = Display.DEFAULT_DISPLAY;
        launchActivityOnDisplay(TEST_ACTIVITY, displayId);
        mWmState.computeState(TEST_ACTIVITY);

        assumeTaskMoveAllowedOnDisplay(displayId);

        final WindowManagerState.DisplayContent dc = mWmState.getDisplay(displayId);
        final int minimalTaskSize = WindowManagerState.dpToPx(220f, dc.getDpi());
        final int tooSmallSize = WindowManagerState.dpToPx(100f, dc.getDpi());
        final Rect requestedBounds = new Rect(0, 0, tooSmallSize, tooSmallSize);
        sendTaskMoveRequest(displayId, requestedBounds);

        assertTaskMoveRequestReportedError(IllegalArgumentException.class);
        mWmState.waitForAppTransitionIdleOnDisplay(displayId);
        final Rect bounds = mWmState.getTaskByActivity(TEST_ACTIVITY).getBounds();
        assertTrue(
                "Bounds are too small (current bounds = "
                        + bounds
                        + "), expected at least "
                        + minimalTaskSize
                        + " x "
                        + minimalTaskSize
                        + ".",
                bounds.width() >= minimalTaskSize && bounds.height() >= minimalTaskSize);
    }

    /**
     * Tests that a {@link android.app.ActivityManager.AppTask#moveTaskTo} call whose requested task
     * location has bounds partially off screen does not result in the task being off screen in any
     * part. Assumes that task moving is allowed on the host display.
     */
    @ApiTest(
            apis = {
                "android.app.ActivityManager.AppTask#moveTaskTo",
                "android.app.ActivityManager#isTaskMoveAllowedOnDisplay"
            })
    @Test
    public void testMoveTaskTo_moveToPartiallyOffscreenBounds() {
        final int displayId = Display.DEFAULT_DISPLAY;
        launchActivityOnDisplay(TEST_ACTIVITY, displayId);
        mWmState.computeState(TEST_ACTIVITY);

        assumeTaskMoveAllowedOnDisplay(displayId);

        final WindowManagerState.DisplayContent dc = mWmState.getDisplay(displayId);
        final Rect displayRect = dc.getDisplayRect();
        final Rect requestedBounds = new Rect(-100, -100, 600, 600);
        sendTaskMoveRequest(displayId, requestedBounds);

        assertTaskMoveRequestReportedError(IllegalArgumentException.class);
        mWmState.waitForAppTransitionIdleOnDisplay(displayId);
        final Rect bounds = mWmState.getTaskByActivity(TEST_ACTIVITY).getBounds();
        assertTrue(
                "Bounds are offscreen (current bounds = "
                        + bounds
                        + "), expected them inside "
                        + displayRect
                        + ").",
                displayRect.contains(bounds));
    }

    /**
     * Tests that a {@link android.app.ActivityManager.AppTask#moveTaskTo} call whose requested task
     * location has bounds fully off screen does not result in the task being off screen in any
     * part. Assumes that task moving is allowed on the host display.
     */
    @ApiTest(
            apis = {
                "android.app.ActivityManager.AppTask#moveTaskTo",
                "android.app.ActivityManager#isTaskMoveAllowedOnDisplay"
            })
    @Test
    public void testMoveTaskTo_moveToFullyOffscreenBounds() {
        final int displayId = Display.DEFAULT_DISPLAY;
        launchActivityOnDisplay(TEST_ACTIVITY, displayId);
        mWmState.computeState(TEST_ACTIVITY);

        assumeTaskMoveAllowedOnDisplay(displayId);

        final WindowManagerState.DisplayContent dc = mWmState.getDisplay(displayId);
        final Rect displayRect = dc.getDisplayRect();
        final Rect requestedBounds = new Rect(-700, -700, -100, -100);
        sendTaskMoveRequest(displayId, requestedBounds);

        assertTaskMoveRequestReportedError(IllegalArgumentException.class);
        mWmState.waitForAppTransitionIdleOnDisplay(displayId);
        final Rect bounds = mWmState.getTaskByActivity(TEST_ACTIVITY).getBounds();
        assertTrue(
                "Bounds are offscreen (current bounds = "
                        + bounds
                        + "), expected them inside "
                        + displayRect
                        + ").",
                displayRect.contains(bounds));
    }

    /**
     * Tests that a {@link android.app.ActivityManager.AppTask#moveTaskTo} call succeeds when the
     * requested task location points to a different display than the current host display of the
     * task being moved. Assumes that task moving is allowed on the original host display.
     */
    @ApiTest(
            apis = {
                "android.app.ActivityManager.AppTask#moveTaskTo",
                "android.app.ActivityManager#isTaskMoveAllowedOnDisplay"
            })
    @Test
    public void testMoveTaskTo_moveToAnotherDisplay() {
        assumeTrue("Only test on device with multi-display support", supportsMultiDisplay());
        final int targetDisplayId = createNewDisplay();

        final int sourceDisplayId = Display.DEFAULT_DISPLAY;
        launchActivityOnDisplay(TEST_ACTIVITY, sourceDisplayId);
        mWmState.computeState(TEST_ACTIVITY);
        final Rect initialBounds = mWmState.getTaskByActivity(TEST_ACTIVITY).getBounds();

        assumeTaskMoveAllowedOnDisplay(sourceDisplayId);

        final Rect requestedBounds = new Rect(100, 100, 600, 600);
        sendTaskMoveRequest(targetDisplayId, requestedBounds);

        assertTaskMoveRequestReportedSuccess();
        assertSaneTaskLocation(
                TEST_ACTIVITY, sourceDisplayId, initialBounds, targetDisplayId, requestedBounds);
    }

    /**
     * Tests that the {@link android.app.ActivityManager.AppTask#isTaskMoveAllowedOnDisplay} method
     * returns {@code false} when the caller does not hold the {@link
     * android.Manifest.permission.REPOSITION_SELF_WINDOWS} permission.
     */
    @ApiTest(apis = {"android.app.ActivityManager#isTaskMoveAllowedOnDisplay"})
    @Test
    public void testIsTaskMoveAllowedOnDisplay_withoutPermission() {
        final int displayId = Display.DEFAULT_DISPLAY;

        revokeBrowserRole();

        launchActivityOnDisplay(TEST_ACTIVITY, displayId);
        mWmState.computeState(TEST_ACTIVITY);

        assertFalse(
                "The isTaskMoveAllowedOnDisplay call should return false as the caller does not"
                        + " hold permission REPOSITION_SELF_WINDOWS (granted by the BROWSER role)",
                getIsTaskMoveAllowedOnDisplay(displayId));
    }

    /**
     * Tests that a {@link android.app.ActivityManager.AppTask#moveTaskTo} call throws a {@link
     * SecurityException} when the caller does not hold the {@link
     * android.Manifest.permission.REPOSITION_SELF_WINDOWS} permission.
     */
    @ApiTest(
            apis = {
                "android.app.ActivityManager.AppTask#moveTaskTo",
                "android.app.ActivityManager#isTaskMoveAllowedOnDisplay"
            })
    @Test
    public void testMoveTaskTo_withoutPermission() {
        final int displayId = Display.DEFAULT_DISPLAY;
        launchActivityOnDisplay(TEST_ACTIVITY, displayId);
        mWmState.computeState(TEST_ACTIVITY);

        assumeTaskMoveAllowedOnDisplay(displayId);

        revokeBrowserRole();

        launchActivityOnDisplay(TEST_ACTIVITY, displayId);
        mWmState.computeState(TEST_ACTIVITY);

        final Rect initialBounds = mWmState.getTaskByActivity(TEST_ACTIVITY).getBounds();
        final Rect requestedBounds = new Rect(initialBounds);
        requestedBounds.offset(10, 0);

        sendTaskMoveRequest(displayId, requestedBounds);
        assertTaskMoveRequestReportedError(SecurityException.class);
        assertExactTaskLocation(TEST_ACTIVITY, displayId, initialBounds);
    }

    private void assertExactTaskLocation(
            ComponentName activityName, int displayId, Rect expectedBounds) {
        mWmState.waitForAppTransitionIdleOnDisplay(displayId);
        final Task task = mWmState.getTaskByActivity(activityName);
        assertTrue(
                "Wrong final location of the task, "
                        + "expected {displayId = "
                        + displayId
                        + ", bounds = "
                        + expectedBounds
                        + "}, "
                        + "got {displayId = "
                        + task.mDisplayId
                        + ", bounds = "
                        + task.getBounds()
                        + "}",
                task.getBounds().equals(expectedBounds) && task.mDisplayId == displayId);
    }

    private void assertSaneTaskLocation(
            ComponentName activityName,
            int sourceDisplayId,
            Rect sourceBounds,
            int targetDisplayId,
            Rect targetBounds) {
        mWmState.waitForAppTransitionIdleOnDisplay(sourceDisplayId);
        final Task task = mWmState.getTaskByActivity(activityName);
        final int finalDisplayId = task.mDisplayId;
        final Rect finalBounds = task.getBounds();

        assertTrue(
                "Final display ID differs from requested display ID (expected "
                        + targetDisplayId
                        + ", got "
                        + finalDisplayId
                        + ")",
                finalDisplayId == targetDisplayId);

        if (sourceDisplayId != targetDisplayId) return;

        // In this block we can assume that the request was a pure move without size changes.
        if (sourceBounds.width() == targetBounds.width()
                && sourceBounds.height() == targetBounds.height()) {
            assertTrue(
                    "Final bounds differ in size from requested bounds. The request was a pure move"
                            + " (without size change). (requested "
                            + targetBounds
                            + ", got "
                            + finalBounds
                            + ")",
                    finalBounds.width() == targetBounds.width()
                            && finalBounds.height() == targetBounds.height());

            int requestedDeltaX = targetBounds.left - sourceBounds.left;
            int requestedDeltaY = targetBounds.top - sourceBounds.top;

            int finalDeltaX = finalBounds.left - sourceBounds.left;
            int finalDeltaY = finalBounds.top - sourceBounds.top;

            assumeTrue(
                    "Final bounds differ in the direction of X coordinate change from requested"
                        + " bounds. The request was a pure move (without size change). (requested "
                            + targetBounds
                            + ", got "
                            + finalBounds
                            + ")",
                    Math.signum(finalDeltaX) == Math.signum(requestedDeltaX) || finalDeltaX == 0);
            assumeTrue(
                    "Final bounds differ in the direction of Y coordinate change from requested"
                        + " bounds. The request was a pure move (without size change). (requested "
                            + targetBounds
                            + ", got "
                            + finalBounds
                            + ")",
                    Math.signum(finalDeltaY) == Math.signum(requestedDeltaY) || finalDeltaY == 0);
        }

        final Rect sourceAndTargetBoundingBox = new Rect(sourceBounds);
        sourceAndTargetBoundingBox.union(targetBounds);

        assumeTrue(
                "Final bounds are outside of bounding box of original and requested bounds."
                        + " (requested "
                        + targetBounds
                        + ", got "
                        + finalBounds
                        + ", original bounds "
                        + sourceBounds
                        + ")",
                sourceAndTargetBoundingBox.contains(finalBounds));
    }

    private boolean getIsTaskMoveAllowedOnDisplay(int displayId) {
        mContext.sendBroadcast(
                new Intent(ACTION_CHECK_IS_TASK_MOVE_ALLOWED)
                        .setFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                        .putExtra(EXTRA_DISPLAY_ID_KEY, displayId));
        final boolean notified =
                getBroadcastReceivedVariable(ACTION_NOTIFY_TASK_MOVE_ALLOWED_RESULT).block(1000);
        getBroadcastReceivedVariable(ACTION_NOTIFY_TASK_MOVE_ALLOWED_RESULT).close();
        final Intent intent =
                mBroadcastsContentsReceived.get(ACTION_NOTIFY_TASK_MOVE_ALLOWED_RESULT);

        if (!notified || intent == null || !intent.hasExtra(EXTRA_TASK_MOVE_ALLOWED_RESULT)) {
            if (intent != null && intent.hasExtra(EXTRA_SYNC_EXCEPTION_KEY)) {
                fail(
                        "The activity notified that the isTaskMoveAllowedOnDisplay request thrown"
                            + " an exception: "
                                + intent.getParcelableExtra(
                                                EXTRA_SYNC_EXCEPTION_KEY, Exception.class)
                                        .getMessage());
            } else {
                fail(
                        "The activity has not notified about task movability on display "
                                + displayId
                                + " .");
            }
        }

        return intent.getBooleanExtra(EXTRA_TASK_MOVE_ALLOWED_RESULT, false);
    }

    private void assumeTaskMoveAllowedOnDisplay(int displayId) {
        assumeTrue(
                "Only test when display allows task moving",
                getIsTaskMoveAllowedOnDisplay(displayId));
    }

    private void assertTaskMoveRequestReportedSuccess() {
        final boolean notified =
                getBroadcastReceivedVariable(ACTION_NOTIFY_TASK_MOVE_REQUEST_RESULT).block(1000);
        getBroadcastReceivedVariable(ACTION_NOTIFY_TASK_MOVE_REQUEST_RESULT).close();
        final Intent intent =
                mBroadcastsContentsReceived.get(ACTION_NOTIFY_TASK_MOVE_REQUEST_RESULT);

        if (!notified || intent == null) {
            fail("The activity has not notified about the moveTaskTo request result.");
        }

        if (!intent.hasExtra(EXTRA_DISPLAY_ID_KEY) || !intent.hasExtra(EXTRA_BOUNDS_KEY)) {
            if (intent.getParcelableExtra(EXTRA_EXCEPTION_KEY, Exception.class) != null) {
                fail(
                        "The activity notified that the moveTaskTo request resulted in error: "
                                + intent.getParcelableExtra(EXTRA_EXCEPTION_KEY, Exception.class)
                                        .getMessage());
            } else if (intent.getParcelableExtra(EXTRA_SYNC_EXCEPTION_KEY, Exception.class)
                    != null) {
                fail(
                        "The activity notified that the moveTaskTo request thrown an exception: "
                                + intent.getParcelableExtra(
                                                EXTRA_SYNC_EXCEPTION_KEY, Exception.class)
                                        .getMessage());
            } else {
                fail("The activity has not notified about the moveTaskTo request result.");
            }
        }
    }

    private void assertTaskMoveRequestReportedError(Class<?> exceptionClass) {
        final boolean notified =
                getBroadcastReceivedVariable(ACTION_NOTIFY_TASK_MOVE_REQUEST_RESULT).block(1000);
        getBroadcastReceivedVariable(ACTION_NOTIFY_TASK_MOVE_REQUEST_RESULT).close();
        final Intent intent =
                mBroadcastsContentsReceived.get(ACTION_NOTIFY_TASK_MOVE_REQUEST_RESULT);

        if (!notified || intent == null) {
            fail("The activity has not notified about the moveTaskTo request result.");
        }

        if (!intent.hasExtra(EXTRA_EXCEPTION_KEY)) {
            if (intent.hasExtra(EXTRA_DISPLAY_ID_KEY) && intent.hasExtra(EXTRA_BOUNDS_KEY)) {
                fail("The activity notified that the moveTaskTo request succeeded.");
            } else if (intent.getParcelableExtra(EXTRA_SYNC_EXCEPTION_KEY, Exception.class)
                    != null) {
                fail(
                        "The activity notified that the moveTaskTo request thrown an exception: "
                                + intent.getParcelableExtra(
                                                EXTRA_SYNC_EXCEPTION_KEY, Exception.class)
                                        .getMessage());
            } else {
                fail("The activity has not notified about the moveTaskTo request result.");
            }
        }

        assertTrue(intent.getParcelableExtra(EXTRA_EXCEPTION_KEY, exceptionClass) != null);
    }

    private void grantBrowserRole() {
        ShellCommandHelper.executeShellCommand(
                "cmd role add-role-holder --user "
                        + UserHandle.myUserId()
                        + " android.app.role.BROWSER "
                        + Components.getPackageName());
    }

    private void revokeBrowserRole() {
        ShellCommandHelper.executeShellCommand(
                "cmd role remove-role-holder --user "
                        + UserHandle.myUserId()
                        + " android.app.role.BROWSER "
                        + Components.getPackageName());
    }

    private void sendTaskMoveRequest(int displayId, Rect bounds) {
        mContext.sendBroadcast(
                new Intent(ACTION_REQUEST_TASK_MOVE)
                        .setFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                        .putExtra(EXTRA_DISPLAY_ID_KEY, displayId)
                        .putExtra(EXTRA_BOUNDS_KEY, bounds));
    }

    private int createNewDisplay() {
        return createManagedVirtualDisplaySession()
                .setSimulateDisplay(true)
                .setSimulationDisplaySize(1920 /* width */, 1080 /* height */)
                .setDisplayImePolicy(DISPLAY_IME_POLICY_LOCAL)
                .createDisplay()
                .mId;
    }
}
