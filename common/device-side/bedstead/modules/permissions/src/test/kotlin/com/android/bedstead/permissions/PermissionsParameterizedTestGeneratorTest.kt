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

import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.permissions.annotations.PermissionTest
import com.android.bedstead.nene.TestApis.permissions
import com.android.bedstead.permissions.annotations.EnsureHasPermission
import com.google.common.truth.Truth.assertThat
import org.junit.runner.RunWith

@RunWith(BedsteadJUnit4::class)
class PermissionsParameterizedTestGeneratorTest {

    @PermissionTest(
        CommonPermissions.INTERACT_ACROSS_PROFILES,
        CommonPermissions.INTERACT_ACROSS_USERS
    )
    @EnsureHasPermission(CommonPermissions.INTERACT_ACROSS_USERS_FULL)
    fun permissionTestAnnotation_generatesRunsWithOnePermissionOrOther() {
        assertThat(
            permissions().hasPermission(CommonPermissions.INTERACT_ACROSS_USERS_FULL)
        ).isTrue()
        if (permissions().hasPermission(CommonPermissions.INTERACT_ACROSS_PROFILES)) {
            assertThat(
                permissions().hasPermission(CommonPermissions.INTERACT_ACROSS_USERS)
            ).isFalse()
        } else {
            assertThat(
                permissions().hasPermission(CommonPermissions.INTERACT_ACROSS_USERS)
            ).isTrue()
        }
    }
}
