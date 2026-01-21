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
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.server.wm.ActivityLauncher.KEY_NEW_TASK
import android.server.wm.CliIntentExtra.extraBool
import android.server.wm.StateLogger.logAlways
import android.server.wm.WindowingLayerTestBase
import android.server.wm.app.Components.PINNED_WINDOWING_LAYER_ACTIVITY
import android.server.wm.app.Components.PinnedWindowingLayerActivity.ACTION_ACTIVITY_FINISHED
import android.server.wm.app.Components.PinnedWindowingLayerActivity.ACTION_REQUEST_WINDOWING_LAYER
import android.server.wm.app.Components.PinnedWindowingLayerActivity.ACTION_REQUEST_WINDOWING_LAYER_RESULT
import android.server.wm.app.Components.PinnedWindowingLayerActivity.EXTRA_EXCEPTION_CLASS
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
        assumeFalse(isAutomotive()); // b/475160661 - not supported on automotive yet
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
        assertEquals("java.lang.SecurityException", result.errorClass)
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
        assertNull(result.errorClass)
        waitAndAssertResumedAndFocusedActivityOnDisplay(
            TEST_ACTIVITY,
            mainDisplayId,
            "Test activity should still be focused after the rejected pinning request",
        )
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

    /** Requests the pinned layer and assumes it is supported by veryfying the result. */
    private fun assumeRequestingPinnedLayerIsSupportedWithSuccessfulResult() = runBlocking {
        val result = requestWindowingLayer(AppTask.WINDOWING_LAYER_PINNED)
        assertNull(
            result.errorClass,
            "Request failed with an error (class=${result.errorClass}). " +
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
    }

    private fun assertSuccess(actualResult: WindowingLayerResult, expectedLayer: Int) {
        assertTrue(
            actualResult.success,
            "requestWindowingLayer failed, details: ${actualResult.details}",
        )
        assertEquals(expectedLayer, actualResult.layer, "Returned wrong layer")
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
        val errorClass: String?,
    ) {
        companion object {
            fun parse(intent: Intent): WindowingLayerResult {
                return WindowingLayerResult(
                    intent.getBooleanExtra(EXTRA_RESULT_SUCCESS, false),
                    intent.getIntExtra(EXTRA_WINDOWING_LAYER_TYPE, -1),
                    intent.getStringExtra(EXTRA_RESULT_DETAILS),
                    intent.getStringExtra(EXTRA_EXCEPTION_CLASS),
                )
            }
        }
    }
}
