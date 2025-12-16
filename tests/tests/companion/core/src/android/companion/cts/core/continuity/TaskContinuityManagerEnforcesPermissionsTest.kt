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

package android.companion.cts.core.continuity

import android.companion.cts.common.RecordingHandoffFeatureStateListener
import android.companion.cts.common.SIMPLE_EXECUTOR
import android.companion.cts.core.CoreTestBase
import android.companion.datatransfer.continuity.RemoteTask
import android.companion.datatransfer.continuity.TaskContinuityManager
import android.companion.datatransfer.continuity.TaskContinuityManager.HandoffRequestCallback
import android.platform.test.annotations.AppModeFull
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.compatibility.common.util.ApiTest
import kotlin.test.assertFailsWith
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test [android.companion.datatransfer.continuity.TaskContinuityManager].
 *
 * Run: atest CtsCompanionDeviceManagerCoreTestCases:TaskContinuityManagerEnforcesPermissionsTest
 */
@RequiresFlagsEnabled(android.companion.Flags.FLAG_TASK_CONTINUITY)
@AppModeFull(reason = "CompanionDeviceManager APIs are not available to the instant apps.")
@RunWith(AndroidJUnit4::class)
class TaskContinuityManagerEnforcesPermissionsTest : CoreTestBase() {

    @get:Rule val checkFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @ApiTest(
        apis =
            [
                "android.companion.datatransfer.continuity.TaskContinuityManager#setHandoffForDeviceEnabled"
            ]
    )
    @Test
    fun testSetHandoffForDeviceEnabled_failsWithoutPermission() {
        val taskContinuityManager = context.getSystemService(TaskContinuityManager::class.java)!!
        assertFailsWith(SecurityException::class) {
            taskContinuityManager.setHandoffForDeviceEnabled(true)
        }
    }

    @ApiTest(
        apis = ["android.companion.datatransfer.continuity.TaskContinuityManager#requestHandoff"]
    )
    @Test
    fun testRequestHandoff_failsWithoutPermission() {
        var invocationCount = 0
        val callback =
            object : HandoffRequestCallback {

                override fun onHandoffRequestFinished(
                    associationId: Int,
                    remoteTaskId: Int,
                    resultCode: Int,
                ) {
                    invocationCount++
                }
            }

        assertFailsWith(SecurityException::class) {
            val taskContinuityManager =
                context.getSystemService(TaskContinuityManager::class.java)!!
            taskContinuityManager.requestHandoff(0, 0, SIMPLE_EXECUTOR, callback)
        }

        assertEquals(invocationCount, 0)
    }

    @ApiTest(
        apis =
            [
                "android.companion.datatransfer.continuity.TaskContinuityManager#registerRemoteTaskListener"
            ]
    )
    @Test
    fun testRegisterRemoteTaskListener_failsWithoutPermission() {
        var invocationCount = 0
        val listener : (List<RemoteTask>) -> Unit  = {
            invocationCount++
        }

        assertFailsWith(SecurityException::class) {
            val taskContinuityManager =
                context.getSystemService(TaskContinuityManager::class.java)!!
            taskContinuityManager.registerRemoteTaskListener(SIMPLE_EXECUTOR, listener)
        }
        assertEquals(invocationCount, 0)
    }
}
