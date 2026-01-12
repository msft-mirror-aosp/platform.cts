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
import android.content.Intent
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.server.wm.StateLogger.logAlways
import android.server.wm.WindowingLayerTestBase
import android.server.wm.app.Components.PINNED_WINDOWING_LAYER_ACTIVITY
import android.server.wm.app.Components.PinnedWindowingLayerActivity.ACTION_REQUEST_WINDOWING_LAYER
import android.server.wm.app.Components.PinnedWindowingLayerActivity.ACTION_REQUEST_WINDOWING_LAYER_RESULT
import android.server.wm.app.Components.PinnedWindowingLayerActivity.EXTRA_RESULT_DETAILS
import android.server.wm.app.Components.PinnedWindowingLayerActivity.EXTRA_RESULT_SUCCESS
import android.server.wm.app.Components.PinnedWindowingLayerActivity.EXTRA_WINDOWING_LAYER_TYPE
import com.android.compatibility.common.util.ApiTest
import com.android.compatibility.common.util.FeatureUtil.isAutomotive
import com.android.window.flags.Flags
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Build/Install/Run: atest CtsWindowManagerDeviceOther:PinnedWindowingLayerTests */
@ApiTest(apis = ["android.app.ActivityManager.AppTask#requestWindowingLayer"])
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_INTERACTIVE_PICTURE_IN_PICTURE)
class PinnedWindowingLayerTests : WindowingLayerTestBase() {

    @get:Rule val mCheckFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

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

    private data class WindowingLayerResult(
        val success: Boolean,
        val layer: Int,
        val details: String?,
    ) {
        companion object {
            fun parse(intent: Intent): WindowingLayerResult {
                return WindowingLayerResult(
                    intent.getBooleanExtra(EXTRA_RESULT_SUCCESS, false),
                    intent.getIntExtra(EXTRA_WINDOWING_LAYER_TYPE, -1),
                    intent.getStringExtra(EXTRA_RESULT_DETAILS),
                )
            }
        }
    }
}
