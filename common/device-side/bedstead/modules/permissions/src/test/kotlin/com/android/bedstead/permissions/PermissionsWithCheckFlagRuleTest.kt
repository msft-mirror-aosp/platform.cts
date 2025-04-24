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
package com.android.bedstead.permissions

import android.Manifest.permission
import android.app.admin.flags.Flags
import android.platform.test.annotations.RequiresFlagsDisabled
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.nene.TestApis.permissions
import com.android.bedstead.permissions.annotations.EnsureDoesNotHavePermission
import com.android.bedstead.permissions.annotations.EnsureHasPermission
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * using CheckFlagRule with bedstead-permissions at the same test class used to cause
 * bedstead-permissions to not work at all
 * b/386333835
 */
@RunWith(BedsteadJUnit4::class)
class PermissionsWithCheckFlagRuleTest {

    @get:Rule
    val checkFlagRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @Test
    @EnsureHasPermission(permission.INTERACT_ACROSS_USERS_FULL)
    @RequiresFlagsDisabled(Flags.FLAG_SET_KEYGUARD_DISABLED_FEATURES_COEXISTENCE)
    fun hasPermission_permissionIsGranted_flagDisabled_returnsTrue() {
        assertThat(
            permissions().hasPermission(permission.INTERACT_ACROSS_USERS_FULL)
        ).isTrue()
    }

    @Test
    @EnsureHasPermission(permission.INTERACT_ACROSS_USERS_FULL)
    @RequiresFlagsEnabled(Flags.FLAG_ESIM_MANAGEMENT_ENABLED)
    fun hasPermission_permissionIsGranted_flagEnabled_returnsTrue() {
        assertThat(
            permissions().hasPermission(permission.INTERACT_ACROSS_USERS_FULL)
        ).isTrue()
    }

    @Test
    @EnsureDoesNotHavePermission(permission.INTERACT_ACROSS_USERS_FULL)
    @RequiresFlagsEnabled(Flags.FLAG_ESIM_MANAGEMENT_ENABLED)
    fun hasPermission_permissionIsNotGranted_flagEnabled_returnsFalse() {
        assertThat(
            permissions().hasPermission(permission.INTERACT_ACROSS_USERS_FULL)
        ).isFalse()
    }

    @Test
    @EnsureDoesNotHavePermission(permission.INTERACT_ACROSS_USERS_FULL)
    @RequiresFlagsDisabled(Flags.FLAG_SET_KEYGUARD_DISABLED_FEATURES_COEXISTENCE)
    fun hasPermission_permissionIsNotGranted_flagDisabled_returnsFalse() {
        assertThat(
            permissions().hasPermission(permission.INTERACT_ACROSS_USERS_FULL)
        ).isFalse()
    }
}
