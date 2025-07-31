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
 * limitations under the License
 */

package android.server.wm

import android.app.ActivityManager
import android.app.ActivityManager.LOCK_TASK_MODE_LOCKED
import android.app.ActivityManager.LOCK_TASK_MODE_NONE
import android.app.ActivityManager.LOCK_TASK_MODE_PINNED
import android.app.ActivityTaskManager
import android.server.wm.ActivityManagerTestBase.waitForOrFail
import android.util.Log
import androidx.core.content.getSystemService
import androidx.test.platform.app.InstrumentationRegistry

/**
 * A wrapper for starting and stopping system lock task mode for a specific task.
 * This class implements [AutoCloseable] to ensure that lock task mode is always
 * stopped when the session is closed, even in the case of exceptions.
 *
 * @param taskIdForLocking the ID of the task to be put into lock task mode.
 *
 * ### Kotlin Test Usage Example:
 * ```kotlin
 * @Test
 * fun testLockTaskKotlinUsageExample() {
 *   // ... setup code to get taskId ...
 *   SystemLockTaskModeSession(taskIdForLocking = taskId).use {
 *     // Test logic while in lock task mode
 *     // The lock task mode will be stopped automatically at the end of this block
 *   }
 * }
 * ```
 *
 * ### Java Test Usage Example:
 * ```java
 * @Test
 * public void testLockTaskJavaUsageExample() {
 *   // ... setup code to get taskId ...
 *   try (SystemLockTaskModeSession session = new SystemLockTaskModeSession(taskId)) {
 *     // Test logic while in lock task mode
 *     // The lock task mode will be stopped automatically at the end of this block
 *   }
 * }
 * ```
 */
class SystemLockTaskModeSession(private val taskIdForLocking: Int) : AutoCloseable {
    private val context = InstrumentationRegistry.getInstrumentation().context
    private val am = context.getSystemService<ActivityManager>()!!
    private val atm = context.getSystemService<ActivityTaskManager>()!!

    private val currentLockState: Int
        get() = am.lockTaskModeState

    init {
        NestedShellPermission.run { atm.startSystemLockTaskMode(taskIdForLocking) }
        waitForOrFail(
            "Fail to enter locked task mode for task $taskIdForLocking, " +
                    "final state: ${currentLockState.toLockModeName()}"
        ) { currentLockState != LOCK_TASK_MODE_NONE }
        Log.d(
            TAG,
            "Successfully locked task $taskIdForLocking, " +
                    "current state: ${currentLockState.toLockModeName()}"
        )
    }

    override fun close() {
        NestedShellPermission.run { atm.stopSystemLockTaskMode() }
        Log.d(TAG, "Successfully exited lock task mode.")
    }

    companion object {
        private const val TAG = "SystemLockTaskMode"

        /** Converts an ActivityManager lock task mode to a human-readable string. */
        private fun Int.toLockModeName(): String = when (this) {
            LOCK_TASK_MODE_LOCKED -> "LOCKED"
            LOCK_TASK_MODE_PINNED -> "PINNED"
            LOCK_TASK_MODE_NONE -> "NONE"
            else -> "UNKNOWN($this)"
        }
    }
}
