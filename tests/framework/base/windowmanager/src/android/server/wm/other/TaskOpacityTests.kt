/*
 * Copyright (C) 2026 The Android Open Source Project
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

import android.Manifest
import android.app.AppOpsManager
import android.app.InfeasibleActivityOptionsException
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.os.ParcelFileDescriptor
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.server.wm.StateLogger.logAlways
import android.server.wm.TaskMoveTestBase
import android.server.wm.app.Components
import android.server.wm.app.Components.MovableTaskTrampolineActivity.ACTION_NOTIFY_START_ACTIVITY_WITH_MOVABLE_FLAG_RESULT
import android.server.wm.app.Components.MovableTaskTrampolineActivity.ACTION_START_ACTIVITY_WITH_MOVABLE_FLAG
import android.server.wm.app.Components.MovableTaskTrampolineActivity.EXTRA_ACTIVITY_NAME_KEY
import android.server.wm.app.Components.MovableTaskTrampolineActivity.EXTRA_DISPLAY_ID_KEY
import android.server.wm.app.Components.MovableTaskTrampolineActivity.EXTRA_SYNC_EXCEPTION_KEY
import com.android.bedstead.nene.TestApis
import com.android.bedstead.nene.appops.AppOpsMode
import com.android.compatibility.common.util.CddTest
import com.android.window.flags.Flags
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import org.junit.After
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import perfetto.protos.SurfaceflingerLayers.FloatRectProto
import perfetto.protos.SurfaceflingerLayers.LayersTraceFileProto

/** Build/Install/Run: atest CtsWindowManagerDeviceOther:TaskOpacityTests */
class TaskOpacityTests : TaskMoveTestBase() {
    @get:Rule val mCheckFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @Before
    override fun setUp() {
        super.setUp()

        // Revoke SYSTEM_ALERT_WINDOW permissions of the package because it may impact how
        // transparent activities are launched.
        TestApis.packages().find(
            Components.getPackageName()
        ).appOps().set(AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, AppOpsMode.IGNORED)
        TestApis.packages().find(
            Components.getPackageName()
        ).denyPermission(Manifest.permission.SYSTEM_ALERT_WINDOW)
    }

    @After
    override fun tearDown() {
        // Clean up after revocation of SYSTEM_ALERT_WINDOW permissions.
        TestApis.packages().find(
            Components.getPackageName()
        ).appOps().set(AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, AppOpsMode.DEFAULT)
        TestApis.packages().find(
            Components.getPackageName()
        ).grantPermission(Manifest.permission.SYSTEM_ALERT_WINDOW)

        super.tearDown()
    }

    /**
     * Launches a movable task and asserts the alpha channel value of the color of its associated
     * layer provided by SurfaceFlinger.
     */
    @CddTest(requirements = ["3.8.14/C-1-5"])
    @RequiresFlagsEnabled(
        Flags.FLAG_ENABLE_WINDOW_REPOSITIONING_API,
        Flags.FLAG_ENABLE_REQUIRE_MOVABLE_TASK_API
    )
    @Test
    fun testMovableTaskOpaque() {
        val displayId = getMainDisplayId()

        // Launch a movable task via trampoline activity.
        launchMovableActivityOnDisplay(
            Components.TRANSPARENT_ACTIVITY,
            Components.MOVABLE_TASK_TRAMPOLINE_ACTIVITY,
            displayId
        )
        mWmState.waitAndAssertActivityRemoved(Components.MOVABLE_TASK_TRAMPOLINE_ACTIVITY)

        val task = mWmState.getTaskByActivity(Components.TRANSPARENT_ACTIVITY)
        val taskId = task.taskId
        val taskBounds = task.bounds

        val stream = ParcelFileDescriptor.AutoCloseInputStream(
            mInstrumentation.uiAutomation.executeShellCommand("dumpsys SurfaceFlinger --proto")
        )
        val rawBytes = stream.use { it.readBytes() }
        val layers = LayersTraceFileProto.parseFrom(rawBytes).entryList.last().layers.layersList

        val taskLayer = layers.find { it.name.contains("Task=$taskId") }

        assertNotNull(
            taskLayer,
            "Could not find SurfaceFlinger layer for Task ID: $taskId"
        ) { taskLayer ->
        assertEquals(1.0f, taskLayer.color.a, 0f)
        assertTrue(taskLayer.isOpaque, "The layer for Task ID: $taskId is not marked as opaque")
        assertEquals(
            taskBounds,
            floatRectProtoToRect(taskLayer.screenBounds),
            "The screen bounds of the layer for Task ID: $taskId do not match task bounds"
        )
        }
    }

    override fun getIntentFilter(): IntentFilter {
        val filter: IntentFilter = IntentFilter()
        filter.addAction(ACTION_NOTIFY_START_ACTIVITY_WITH_MOVABLE_FLAG_RESULT)
        return filter
    }

    fun launchMovableActivityOnDisplay(
            activityName: ComponentName,
            trampolineActivity: ComponentName,
            displayId: Int,
        ) {
        launchActivityOnDisplay(trampolineActivity, displayId)
        mWmState.computeState(trampolineActivity)

        logAlways("Sending ACTION_START_ACTIVITY_WITH_MOVABLE_FLAG intent for display $displayId")

        mContext.sendBroadcast(
            Intent(ACTION_START_ACTIVITY_WITH_MOVABLE_FLAG).apply {
                putExtra(EXTRA_DISPLAY_ID_KEY, displayId)
                putExtra(EXTRA_ACTIVITY_NAME_KEY, activityName)
            }
        )

        val notified = awaitBroadcast(ACTION_NOTIFY_START_ACTIVITY_WITH_MOVABLE_FLAG_RESULT)
        val intent = getIntentOfBroadcast(ACTION_NOTIFY_START_ACTIVITY_WITH_MOVABLE_FLAG_RESULT)

        if (!notified || intent == null) {
            fail("The activity has not notified about the launch result.")
        }

        val syncException = intent.getParcelableExtra(
            EXTRA_SYNC_EXCEPTION_KEY,
            Exception::class.java
        )

        assumeFalse(
            "Task movability is not supported on this display/config",
            syncException is InfeasibleActivityOptionsException
        )
        assertNull(syncException, "Failed to launch movable activity: $syncException")

        mWmState.waitForAppTransitionIdleOnDisplay(displayId)
        mWmState.computeState(activityName)
    }

    fun floatRectProtoToRect(floatRectProto: FloatRectProto): Rect {
        return Rect(
            floatRectProto.left.toInt(),
            floatRectProto.top.toInt(),
            floatRectProto.right.toInt(),
            floatRectProto.bottom.toInt()
        )
    }
}
