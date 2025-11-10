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

import android.Manifest
import android.companion.cts.common.RecordingHandoffFeatureStateListener
import android.companion.cts.common.SIMPLE_EXECUTOR
import android.companion.cts.core.CoreTestBase
import android.companion.datatransfer.continuity.TaskContinuityManager
import android.platform.test.annotations.AppModeFull
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test [android.companion.datatransfer.continuity.TaskContinuityManager.enableHandoffForDevice].
 *
 * Run: atest CtsCompanionDeviceManagerCoreTestCases:EnableAndDisableHandoffTest
 */
@RequiresFlagsEnabled(android.companion.Flags.FLAG_TASK_CONTINUITY)
@AppModeFull(reason = "CompanionDeviceManager APIs are not available to the instant apps.")
@RunWith(AndroidJUnit4::class)
class EnableAndDisableHandoffTest : CoreTestBase() {

    @get:Rule
    val checkFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @Test
    fun testEnableHandoffForDevice_requiresPermission() {
        val taskContinuityManager = context.getSystemService(TaskContinuityManager::class.java)!!
        assertFailsWith(SecurityException::class) {
            taskContinuityManager.enableHandoffForDevice(true)
        }
    }

    @Test
    fun testRegisterHandoffFeatureStateListener_requiresPermission() {
        val listener = RecordingHandoffFeatureStateListener()
        assertFailsWith(SecurityException::class) {
            val taskContinuityManager =
                context.getSystemService(TaskContinuityManager::class.java)!!
            taskContinuityManager.registerHandoffFeatureStateListener(SIMPLE_EXECUTOR, listener)
        }
        assertEquals(listener.invocations.size, 0)
    }

    @Test
    fun testEnableHandoffForDevice_notifiesListeners() {
        withShellPermissionIdentity(
            Manifest.permission.READ_HANDOFF_SETTINGS,
            Manifest.permission.MODIFY_HANDOFF_SETTINGS,
        ) {
            val listener = RecordingHandoffFeatureStateListener()
            val taskContinuityManager =
                context.getSystemService(TaskContinuityManager::class.java)!!
            taskContinuityManager.enableHandoffForDevice(true)

            listener.assertInvokedByActions {
                taskContinuityManager.registerHandoffFeatureStateListener(SIMPLE_EXECUTOR, listener)
            }
            assertEquals(listener.invocations.size, 1)
            assertTrue(listener.invocations[0].isHandoffEnabled)
            assertEquals(
                listener.invocations[0].handoffAvailabilityStatus,
                TaskContinuityManager.HANDOFF_AVAILABILITY_STATUS_AVAILABLE,
            )
            listener.clearRecordedInvocations()

            listener.assertInvokedByActions(timeout = 10.seconds) {
                taskContinuityManager.enableHandoffForDevice(false)
            }
            assertEquals(listener.invocations.size, 1)
            assertFalse(listener.invocations[0].isHandoffEnabled)
            assertEquals(
                listener.invocations[0].handoffAvailabilityStatus,
                TaskContinuityManager.HANDOFF_AVAILABILITY_STATUS_AVAILABLE,
            )
            listener.clearRecordedInvocations()

            listener.assertInvokedByActions { taskContinuityManager.enableHandoffForDevice(true) }
            assertEquals(listener.invocations.size, 1)
            assertTrue(listener.invocations[0].isHandoffEnabled)
            assertEquals(
                listener.invocations[0].handoffAvailabilityStatus,
                TaskContinuityManager.HANDOFF_AVAILABILITY_STATUS_AVAILABLE,
            )
        }
    }
}
