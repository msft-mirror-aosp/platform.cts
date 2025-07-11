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

package android.supervision.cts

import android.Manifest.permission.BYPASS_ROLE_QUALIFICATION
import android.Manifest.permission.MANAGE_ROLE_HOLDERS
import android.Manifest.permission.OBSERVE_ROLE_HOLDERS
import android.Manifest.permission.QUERY_USERS
import android.app.admin.DevicePolicyManager
import android.app.admin.DevicePolicyManager.LOCK_TASK_FEATURE_BLOCK_ACTIVITY_START_IN_TASK
import android.app.admin.DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS
import android.app.admin.DevicePolicyManager.LOCK_TASK_FEATURE_HOME
import android.app.admin.DevicePolicyManager.LOCK_TASK_FEATURE_KEYGUARD
import android.app.admin.DevicePolicyManager.LOCK_TASK_FEATURE_NONE
import android.app.admin.DevicePolicyManager.LOCK_TASK_FEATURE_NOTIFICATIONS
import android.app.admin.DevicePolicyManager.LOCK_TASK_FEATURE_OVERVIEW
import android.app.admin.DevicePolicyManager.LOCK_TASK_FEATURE_QUICK_SETTINGS
import android.app.admin.DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO
import android.app.supervision.flags.Flags.FLAG_ENABLE_LOCK_TASK_FEATURE_QUICK_SETTINGS
import android.permission.flags.Flags.FLAG_ENABLE_SYSTEM_SUPERVISION_ROLE_BEHAVIOR
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.permissions.annotations.EnsureHasPermission
import com.android.compatibility.common.util.ApiTest
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BedsteadJUnit4::class)
@RequiresFlagsEnabled(
    FLAG_ENABLE_LOCK_TASK_FEATURE_QUICK_SETTINGS,
    FLAG_ENABLE_SYSTEM_SUPERVISION_ROLE_BEHAVIOR,
)
class LockTaskTest : BaseSupervisionTest() {
    @get:Rule val checkFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @Test
    @ApiTest(
        apis =
            [
                "android.app.admin.DevicePolicyManager#LOCK_TASK_FEATURE_SYSTEM_INFO",
                "android.app.admin.DevicePolicyManager#LOCK_TASK_FEATURE_NOTIFICATIONS",
                "android.app.admin.DevicePolicyManager#LOCK_TASK_FEATURE_HOME",
                "android.app.admin.DevicePolicyManager#LOCK_TASK_FEATURE_OVERVIEW",
                "android.app.admin.DevicePolicyManager#LOCK_TASK_FEATURE_GLOBAL_ACTIONS",
                "android.app.admin.DevicePolicyManager#LOCK_TASK_FEATURE_KEYGUARD",
                "android.app.admin.DevicePolicyManager#LOCK_TASK_FEATURE_BLOCK_ACTIVITY_START_IN_TASK",
                "android.app.admin.DevicePolicyManager#LOCK_TASK_FEATURE_QUICK_SETTINGS",
            ]
    )
    @EnsureHasPermission(
        BYPASS_ROLE_QUALIFICATION,
        MANAGE_ROLE_HOLDERS,
        QUERY_USERS,
        OBSERVE_ROLE_HOLDERS,
    )
    fun setLockTaskFeatures_withSystemSupervisionRole_succeeds() {
        withSystemSupervisionRoleHeld {
            for (features in lockTaskFlags) {
                verifySetLockTaskFeature(features)
            }
        }
    }

    @Test
    @ApiTest(
        apis =
            [
                "android.app.admin.DevicePolicyManager#LOCK_TASK_FEATURE_SYSTEM_INFO",
                "android.app.admin.DevicePolicyManager#LOCK_TASK_FEATURE_NOTIFICATIONS",
                "android.app.admin.DevicePolicyManager#LOCK_TASK_FEATURE_HOME",
                "android.app.admin.DevicePolicyManager#LOCK_TASK_FEATURE_OVERVIEW",
                "android.app.admin.DevicePolicyManager#LOCK_TASK_FEATURE_GLOBAL_ACTIONS",
                "android.app.admin.DevicePolicyManager#LOCK_TASK_FEATURE_KEYGUARD",
                "android.app.admin.DevicePolicyManager#LOCK_TASK_FEATURE_BLOCK_ACTIVITY_START_IN_TASK",
                "android.app.admin.DevicePolicyManager#LOCK_TASK_FEATURE_QUICK_SETTINGS",
            ]
    )
    @EnsureHasPermission(
        BYPASS_ROLE_QUALIFICATION,
        MANAGE_ROLE_HOLDERS,
        QUERY_USERS,
        OBSERVE_ROLE_HOLDERS,
    )
    fun setLockTaskFeatures_withSystemSupervisionRole_withLockTaskPackage_succeeds() {
        withSystemSupervisionRoleHeld {
            withLockTaskPackage {
                for (features in lockTaskFlags) {
                    verifySetLockTaskFeature(features)
                }
            }
        }
    }

    private fun verifySetLockTaskFeature(features: Int) {
        try {
            devicePolicyManager.setLockTaskFeatures(null, features)
            assertThat(devicePolicyManager.getLockTaskFeatures(null)).isEqualTo(features)
        } finally {
            devicePolicyManager.setLockTaskFeatures(null, LOCK_TASK_FEATURE_NONE)
            if (!devicePolicyManager.getLockTaskPackages(null).isEmpty()) {
                assertThat(devicePolicyManager.getLockTaskFeatures(null))
                    .isEqualTo(LOCK_TASK_FEATURE_NONE)
            }
        }
    }

    private fun withLockTaskPackage(action: () -> Unit) {
        try {
            devicePolicyManager.setLockTaskPackages(null, arrayOf(context.packageName))
            action()
        } finally {
            devicePolicyManager.setLockTaskPackages(null, emptyArray())
        }
    }

    companion object {
        val lockTaskFlags =
            listOf(
                // QUICK_SETTINGS flag requires both NOTIFICATIONS and HOME flags to be
                // set together
                (LOCK_TASK_FEATURE_QUICK_SETTINGS or
                    LOCK_TASK_FEATURE_NOTIFICATIONS or
                    LOCK_TASK_FEATURE_HOME),
                // LOCK_TASK_FEATURE_OVERVIEW and LOCK_TASK_FEATURE_NOTIFICATIONS flags can only be
                // set together with HOME flag
                (LOCK_TASK_FEATURE_OVERVIEW or LOCK_TASK_FEATURE_HOME),
                (LOCK_TASK_FEATURE_NOTIFICATIONS or LOCK_TASK_FEATURE_HOME),
                // individually settable flags
                LOCK_TASK_FEATURE_HOME,
                LOCK_TASK_FEATURE_SYSTEM_INFO,
                LOCK_TASK_FEATURE_GLOBAL_ACTIONS,
                LOCK_TASK_FEATURE_KEYGUARD,
                LOCK_TASK_FEATURE_BLOCK_ACTIVITY_START_IN_TASK,
            )
        val devicePolicyManager = context.getSystemService(DevicePolicyManager::class.java)
    }
}
