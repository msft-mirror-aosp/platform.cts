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
import android.os.UserManager
import android.platform.test.annotations.AppModeFull
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.bedstead.nene.TestApis
import com.android.xts.root.annotations.RequireAdbRoot
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test
 * [android.companion.datatransfer.continuity.TaskContinuityManager.setHandoffForDeviceEnabled].
 *
 * Run: atest CtsCompanionDeviceManagerCoreTestCases:EnableAndDisableHandoffTest
 */
@RequiresFlagsEnabled(android.companion.Flags.FLAG_TASK_CONTINUITY)
@AppModeFull(reason = "CompanionDeviceManager APIs are not available to the instant apps.")
@RunWith(AndroidJUnit4::class)
class EnableAndDisableHandoffTest : CoreTestBase() {

    @get:Rule val checkFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @Test
    fun testSetHandoffForDeviceEnabled_requiresPermission() {
        val taskContinuityManager = context.getSystemService(TaskContinuityManager::class.java)!!
        assertFailsWith(SecurityException::class) {
            taskContinuityManager.setHandoffForDeviceEnabled(true)
        }
    }

    @Test
    @RequireAdbRoot(reason = "b/322830652 Required for TestApis to set user restriction")
    fun testDisableHandoffByPolicy_setsHandoffDisabled() {
        TestApis.devicePolicy()
            .userRestrictions()
            .set(UserManager.DISALLOW_TASK_CONTINUITY_HANDOFF, true)

        withShellPermissionIdentity(
            Manifest.permission.READ_HANDOFF_SETTINGS,
            Manifest.permission.MODIFY_HANDOFF_SETTINGS,
        ) {
            val listener = RecordingHandoffFeatureStateListener()
            val taskContinuityManager =
                context.getSystemService(TaskContinuityManager::class.java)!!
            taskContinuityManager.setHandoffForDeviceEnabled(true)

            listener.assertInvokedByActions {
                taskContinuityManager.registerHandoffFeatureStateListener(SIMPLE_EXECUTOR, listener)
            }
            assertEquals(listener.invocations.size, 1)
            assertEquals(
                TaskContinuityManager.HANDOFF_AVAILABILITY_STATUS_DISABLED_BY_POLICY,
                listener.invocations[0].handoffAvailabilityStatus,
            )
            listener.clearRecordedInvocations()
        }

        TestApis.devicePolicy()
            .userRestrictions()
            .set(UserManager.DISALLOW_TASK_CONTINUITY_HANDOFF, false)
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
    fun testSetHandoffForDeviceEnabled_notifiesListeners() {
        withShellPermissionIdentity(
            Manifest.permission.READ_HANDOFF_SETTINGS,
            Manifest.permission.MODIFY_HANDOFF_SETTINGS,
        ) {
            val listener = RecordingHandoffFeatureStateListener()
            val taskContinuityManager =
                context.getSystemService(TaskContinuityManager::class.java)!!
            taskContinuityManager.setHandoffForDeviceEnabled(true)

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
                taskContinuityManager.setHandoffForDeviceEnabled(false)
            }
            assertEquals(listener.invocations.size, 1)
            assertFalse(listener.invocations[0].isHandoffEnabled)
            assertEquals(
                listener.invocations[0].handoffAvailabilityStatus,
                TaskContinuityManager.HANDOFF_AVAILABILITY_STATUS_AVAILABLE,
            )
            listener.clearRecordedInvocations()

            listener.assertInvokedByActions {
                taskContinuityManager.setHandoffForDeviceEnabled(true)
            }
            assertEquals(listener.invocations.size, 1)
            assertTrue(listener.invocations[0].isHandoffEnabled)
            assertEquals(
                listener.invocations[0].handoffAvailabilityStatus,
                TaskContinuityManager.HANDOFF_AVAILABILITY_STATUS_AVAILABLE,
            )
        }
    }
}
