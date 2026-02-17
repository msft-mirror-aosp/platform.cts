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

package android.server.wm.other

import android.app.ActivityManager.AppTask
import android.app.AppOpsManager
import android.app.InfeasibleActivityOptionsException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.server.wm.ActivityLauncher.KEY_NEW_TASK
import android.server.wm.CliIntentExtra.extraBool
import android.server.wm.StateLogger.logAlways
import android.server.wm.WindowManagerState.dpToPx
import android.server.wm.WindowingLayerTestBase
import android.server.wm.app.Components.PINNED_WINDOWING_LAYER_ACTIVITY
import android.server.wm.app.Components.PinnedWindowingLayerActivity.ACTION_ACTIVITY_FINISHED
import android.server.wm.app.Components.PinnedWindowingLayerActivity.ACTION_RELAUNCH_AS_RESIZABLE
import android.server.wm.app.Components.PinnedWindowingLayerActivity.ACTION_RELAUNCH_AS_RESIZABLE_RESULT
import android.server.wm.app.Components.PinnedWindowingLayerActivity.ACTION_REQUEST_WINDOWING_LAYER
import android.server.wm.app.Components.PinnedWindowingLayerActivity.ACTION_REQUEST_WINDOWING_LAYER_RESULT
import android.server.wm.app.Components.PinnedWindowingLayerActivity.ACTION_REQUEST_TASK_MOVE
import android.server.wm.app.Components.PinnedWindowingLayerActivity.ACTION_TASK_MOVE_RESULT
import android.server.wm.app.Components.PinnedWindowingLayerActivity.EXTRA_BOUNDS
import android.server.wm.app.Components.PinnedWindowingLayerActivity.EXTRA_EXCEPTION
import android.server.wm.app.Components.PinnedWindowingLayerActivity.EXTRA_RESULT_DETAILS
import android.server.wm.app.Components.PinnedWindowingLayerActivity.EXTRA_RESULT_SUCCESS
import android.server.wm.app.Components.PinnedWindowingLayerActivity.EXTRA_WINDOWING_LAYER_TYPE
import android.server.wm.app.Components.TEST_ACTIVITY
import com.android.compatibility.common.util.ApiTest
import com.android.compatibility.common.util.FeatureUtil.isAutomotive
import com.android.window.flags.Flags
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Build/Install/Run: atest CtsWindowManagerDeviceOther:PinnedWindowingLayerTests */
@ApiTest(apis = ["android.app.ActivityManager.AppTask#requestWindowingLayer"])
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_INTERACTIVE_PICTURE_IN_PICTURE)
class PinnedWindowingLayerTests : WindowingLayerTestBase() {

    @get:Rule val mCheckFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()
    private lateinit var mPipAppOpSession: PipAppOpSession

    @Before
    override fun setUp() {
        super.setUp()
        mPipAppOpSession = PipAppOpSession(mContext)
        mObjectTracker.manage(mPipAppOpSession)
        mPipAppOpSession.setMode(AppOpsManager.MODE_ALLOWED)
    }

    /**
     * Verifies that a task can be moved from the pinned windowing layer back to the normal layer.
     * The test first moves the task to the pinned layer and then requests to move it back to the
     * normal layer, asserting that the operation is successful. It assumes that the task can be
     * pinned, which is verified by checking the callback that must always be invoked.
     */
    @Test
    fun unpinsTaskFromPinnedLayer() = runBlocking {
        launchTestActivity()
        assumeRequestingPinnedLayerIsSupportedWithSuccessfulResult()

        val result = requestWindowingLayer(AppTask.WINDOWING_LAYER_NORMAL_APP)

        assertSuccess(result, AppTask.WINDOWING_LAYER_NORMAL_APP)
    }

    /**
     * Verifies that requesting the pinned windowing layer for a task that is already in the pinned
     * layer is a successful operation. This test ensures idempotency of the pinning request. It
     * assumes that the task can be pinned, which is verified by checking the callback that must
     * always be invoked.
     */
    @Test
    fun requestPinnedLayer_whenTaskIsAlreadyPinned_returnsSuccess() = runBlocking {
        launchTestActivity()
        assumeRequestingPinnedLayerIsSupportedWithSuccessfulResult()

        val result = requestWindowingLayer(AppTask.WINDOWING_LAYER_PINNED)

        assertSuccess(result, AppTask.WINDOWING_LAYER_PINNED)
    }

    /**
     * Verifies that a task in the normal windowing layer can successfully request to stay in the
     * normal layer. This test ensures that requesting the current layer returns success, even if
     * it's a no-op.
     */
    @Test
    fun normalLayerRequests_whenWindowIsNotPinned_isAlwaysSuccessful() = runBlocking {
        launchTestActivity()
        val result = requestWindowingLayer(AppTask.WINDOWING_LAYER_NORMAL_APP)
        assertSuccess(result, AppTask.WINDOWING_LAYER_NORMAL_APP)
    }

    /**
     * Verifies that a task cannot be moved to the pinned windowing layer if the Picture-in-Picture
     * app op permission is revoked. The test first ensures the task can be pinned, then revokes the
     * permission and asserts that a subsequent pinning request fails.
     */
    @Test
    fun requestPinnedLayer_whenPipPermissionIsRevoked_fails() = runBlocking {
        launchTestActivity()
        assumeRequestingPinnedLayerIsSupportedWithSuccessfulResult()
        requestWindowingLayer(AppTask.WINDOWING_LAYER_NORMAL_APP)

        mPipAppOpSession.setMode(AppOpsManager.MODE_ERRORED)
        val result = requestWindowingLayer(AppTask.WINDOWING_LAYER_PINNED)

        assertFalse(result.success)
        assertTrue(result.error is SecurityException)
    }

    /**
     * Verifies that revoking the Picture-in-Picture app op permission for an app whose task is
     * currently in the pinned windowing layer causes the task's activity to be finished. The test
     * pins the task, revokes the permission, and then confirms that the activity is destroyed.
     */
    @Test
    fun revokingPipPermission_whenTaskIsPinned_finishesTheTask() = runBlocking {
        launchTestActivity()
        assumeRequestingPinnedLayerIsSupportedWithSuccessfulResult()

        mPipAppOpSession.setMode(AppOpsManager.MODE_ERRORED)
        assertNotNull(
            awaitBroadcast(ACTION_ACTIVITY_FINISHED),
            "Did not receive an intent indicating the activity has finished.",
        )
        mWmState.waitForActivityRemoved(PINNED_WINDOWING_LAYER_ACTIVITY)
    }

    @Test
    fun requestPinnedLayer_whenTaskIsNotFocused_fails() = runBlocking {
        // assume task can be pinned, and unpin it to prepare for the test
        launchTestActivity()
        assumeRequestingPinnedLayerIsSupportedWithSuccessfulResult()
        val unpinResult = requestWindowingLayer(AppTask.WINDOWING_LAYER_NORMAL_APP)
        assertSuccess(unpinResult, AppTask.WINDOWING_LAYER_NORMAL_APP)
        // unfocus the tested activity by launching a different new task
        launchActivityOnDisplay(TEST_ACTIVITY, mainDisplayId, extraBool(KEY_NEW_TASK, true))
        waitAndAssertResumedAndFocusedActivityOnDisplay(
            TEST_ACTIVITY,
            mainDisplayId,
            "Test activity should be the top resumed activity with focus",
        )

        val result = requestWindowingLayer(AppTask.WINDOWING_LAYER_PINNED)

        assertFalse(result.success)
        assertNull(result.error)
        waitAndAssertResumedAndFocusedActivityOnDisplay(
            TEST_ACTIVITY,
            mainDisplayId,
            "Test activity should still be focused after the rejected pinning request",
        )
    }

    @Test
    @RequiresFlagsEnabled(
        Flags.FLAG_ENABLE_REQUIRE_MOVABLE_TASK_API,
        Flags.FLAG_ENABLE_WINDOW_REPOSITIONING_API,
    )
    fun pinnedLayer_moveTaskTo_cannotBeMovedBySubsequentResizes() = runBlocking {
        runPinnedLayerResizableTestSuite {
            val initialBounds = getTaskBounds(PINNED_WINDOWING_LAYER_ACTIVITY)
            val display = mWmState.getDisplay(mainDisplayId)
            // establishing "base pixel unit" to avoid dp to px rounding issue
            val pxPer10Dp = dpToPx(10f, display.dpi).toInt()

            // Expand by 50dp each axis via left top corner
            var bounds = Rect(initialBounds)
            var changeByPx = pxPer10Dp * 5
            bounds.let {
                it.left -= changeByPx
                it.top -= changeByPx
            }
            requestMoveTaskTo(bounds)

            // Shrink by 70dp via right bottom corner
            bounds = getTaskBounds(PINNED_WINDOWING_LAYER_ACTIVITY)
            changeByPx = pxPer10Dp * 7
            bounds.let {
                it.right -= changeByPx
                it.bottom -= changeByPx
            }
            requestMoveTaskTo(bounds)

            // Expand by 20dp via top left corner
            bounds = getTaskBounds(PINNED_WINDOWING_LAYER_ACTIVITY)
            changeByPx = pxPer10Dp * 2
            bounds.let {
                it.left -= changeByPx
                it.top -= changeByPx
            }
            requestMoveTaskTo(bounds)

            val finalBounds = getTaskBounds(PINNED_WINDOWING_LAYER_ACTIVITY)
            assertEquals(initialBounds, finalBounds)
        }
    }

    private fun launchTestActivity() {
        launchActivityOnDisplay(PINNED_WINDOWING_LAYER_ACTIVITY, mainDisplayId)
        mWmState.computeState(PINNED_WINDOWING_LAYER_ACTIVITY)
    }

    private suspend fun requestWindowingLayer(type: Int): WindowingLayerResult {
        logAlways("Sending ACTION_REQUEST_WINDOWING_LAYER intent with layer=$type")
        val intent = Intent(ACTION_REQUEST_WINDOWING_LAYER)
        intent.putExtra(EXTRA_WINDOWING_LAYER_TYPE, type)
        mContext.sendBroadcast(intent)

        val response = awaitBroadcast(ACTION_REQUEST_WINDOWING_LAYER_RESULT)
        return WindowingLayerResult.parse(
            checkNotNull(response) { "Did not receive a broadcast with windowing layer result." }
        )
    }

    private suspend fun requestMoveTaskTo(bounds: Rect) {
        logAlways("Sending ACTION_REQUEST_TASK_MOVE intent with bounds=$bounds")
        val intent = Intent(ACTION_REQUEST_TASK_MOVE)
        intent.putExtra(EXTRA_BOUNDS, bounds)
        mContext.sendBroadcast(intent)

        val response = checkNotNull(awaitBroadcast(ACTION_TASK_MOVE_RESULT)) {
            "Did not receive a broadcast with task move result."
        }
        val error = response.getParcelableExtra(EXTRA_EXCEPTION, Exception::class.java)
        assertNull(error, "Failed to move task with error: ${error}")
    }

    /** Requests the pinned layer and assumes it is supported by veryfying the result. */
    private fun assumeRequestingPinnedLayerIsSupportedWithSuccessfulResult() = runBlocking {
        val result = requestWindowingLayer(AppTask.WINDOWING_LAYER_PINNED)
        assertNull(
            result.error,
            "Request failed with an error=${result.error}). " +
                "If pinned layer is not supported, the request must return a REJECTED code " +
                "via #onSuccess callback, as the request itself was valid.",
        )
        assumeTrue(
            "Pinned layer is not supported as the request failed, details: ${result.details}",
            result.success,
        )
        assertEquals(
            AppTask.WINDOWING_LAYER_PINNED,
            result.layer,
            "requestWindowingLayer returned the wrong layer, details?: ${result.details}",
        )
        waitAndAssertResumedAndFocusedActivityOnDisplay(
            PINNED_WINDOWING_LAYER_ACTIVITY,
            mainDisplayId,
            "Test activity should be resumed and focused after requesting pinned layer",
        )
    }

    /**
     * Runs the provided test body with the test activity in a pinned windowing layer as a resizable
     * task (programmatically).
     *
     * Sets up the test case by placing the movable task in the center of the display with a size of
     * 300dp x 300dp. This size is chosen to be safely within typical display (min 440dp) and
     * minimum task size constraints (min 220dp) so the test can resize the task in various ways
     * while staying within the bounds of the display.
     *
     * @param testBody Actual test logic to be executed within the configured pinned, movable task
     *   environment.
     */
    private suspend fun runPinnedLayerResizableTestSuite(testBody: suspend () -> Unit) {
        launchTestActivity()
        try {
            grantBrowserRole() // The moveTaskTo API is limited to apps with the browser role.
            assumeRelaunchTestActivityAsResizableTask()
            assumeRequestingPinnedLayerIsSupportedWithSuccessfulResult()
            val display = mWmState.getDisplay(mainDisplayId)
            val sizePx = dpToPx(300f, display.dpi)
            val left = display.bounds.centerX() - sizePx / 2
            val top = display.bounds.centerY() - sizePx / 2
            resizeActivityTask(
                PINNED_WINDOWING_LAYER_ACTIVITY,
                left,
                top,
                left + sizePx,
                top + sizePx,
            )
            mWmState.computeState(PINNED_WINDOWING_LAYER_ACTIVITY)
            testBody()
        } finally {
            revokeBrowserRole()
        }
    }

    private suspend fun assumeRelaunchTestActivityAsResizableTask() {
        logAlways("Sending ACTION_RELAUNCH_AS_RESIZABLE intent")
        mContext.sendBroadcast(Intent(ACTION_RELAUNCH_AS_RESIZABLE))

        val response = awaitBroadcast(ACTION_RELAUNCH_AS_RESIZABLE_RESULT)
        assertNotNull(response, "Did not receive a broadcast with relaunch as resizable result.")
        val error = response!!.getParcelableExtra(EXTRA_EXCEPTION, Exception::class.java)
        assumeFalse(
            "Task movability is not supported on this display/config",
            error is InfeasibleActivityOptionsException,
        )
        assertNull(error, "Failed to relaunch activity as resizable task with error: ${error}")
        waitAndAssertResumedAndFocusedActivityOnDisplay(
            PINNED_WINDOWING_LAYER_ACTIVITY,
            mainDisplayId,
            "Test activity should be relaunched and focused after requesting resizable task",
        )
    }

    private fun assertSuccess(actualResult: WindowingLayerResult, expectedLayer: Int) {
        assertTrue(
            actualResult.success,
            "requestWindowingLayer failed, details: ${actualResult.details}",
        )
        assertEquals(expectedLayer, actualResult.layer, "Returned wrong layer")
    }

    private fun getTaskBounds(activity: ComponentName): Rect {
        mWmState.computeState(activity)
        val task = mWmState.getTaskByActivity(activity)
        assertNotNull(task, "Task not found for activity: $activity")
        return Rect(task!!.bounds)
    }

    private inner class PipAppOpSession(private val context: Context) : AutoCloseable {
        private val appOpsManager: AppOpsManager =
            context.getSystemService(AppOpsManager::class.java)
        private val packageName = PINNED_WINDOWING_LAYER_ACTIVITY.packageName
        private val packageManager: PackageManager = context.packageManager
        private val uid = packageManager.getPackageUid(packageName, /* flags= */ 0)
        private val initAppOpMode: Int

        init {
            initAppOpMode =
                appOpsManager.checkOpNoThrow(
                    AppOpsManager.OPSTR_PICTURE_IN_PICTURE,
                    uid,
                    packageName,
                )
        }

        fun setMode(mode: Int) {
            this@PinnedWindowingLayerTests.runWithShellPermission {
                appOpsManager.setMode(
                    AppOpsManager.OPSTR_PICTURE_IN_PICTURE,
                    uid,
                    packageName,
                    mode,
                )
            }
        }

        override fun close() {
            setMode(initAppOpMode)
        }
    }

    private data class WindowingLayerResult(
        val success: Boolean,
        val layer: Int,
        val details: String?,
        val error: Exception?,
    ) {
        companion object {
            fun parse(intent: Intent): WindowingLayerResult {
                return WindowingLayerResult(
                    intent.getBooleanExtra(EXTRA_RESULT_SUCCESS, false),
                    intent.getIntExtra(EXTRA_WINDOWING_LAYER_TYPE, -1),
                    intent.getStringExtra(EXTRA_RESULT_DETAILS),
                    intent.getParcelableExtra(EXTRA_EXCEPTION, Exception::class.java),
                )
            }
        }
    }
}
