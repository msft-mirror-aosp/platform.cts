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

import android.Manifest.permission.MANAGE_USERS
import android.Manifest.permission.QUERY_USERS
import android.app.supervision.SupervisionManager
import android.app.supervision.flags.Flags
import com.android.bedstead.flags.annotations.RequireFlagsEnabled
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.nene.TestApis
import com.android.bedstead.permissions.annotations.EnsureDoesNotHavePermission
import com.android.bedstead.permissions.annotations.EnsureHasPermission
import com.android.compatibility.common.util.ApiTest
import com.android.xts.root.annotations.RequireRootInstrumentation
import com.google.common.truth.Truth.assertThat
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.testng.Assert.assertThrows

@RunWith(BedsteadJUnit4::class)
@RequireFlagsEnabled(Flags.FLAG_SUPERVISION_MANAGER_APIS)
class SupervisionCredentialsTest {
    @Test
    @ApiTest(
        apis =
            ["android.app.supervision.SupervisionManager#createConfirmSupervisionCredentialsIntent"]
    )
    @EnsureHasPermission(MANAGE_USERS)
    @RequireRootInstrumentation(reason = "Use of MANAGE_USERS")
    fun createConfirmSupervisionCredentialsIntent_hasManageUsersPermission_returnsValidIntent() {
        val intent = supervisionManager.createConfirmSupervisionCredentialsIntent()
        assertThat(intent?.action).isEqualTo(ACTION_CONFIRM_SUPERVISION_CREDENTIALS)
        assertThat(intent?.getPackage()).isEqualTo(APPLICATION_PACKAGE)
    }

    @Test
    @ApiTest(
        apis =
            ["android.app.supervision.SupervisionManager#createConfirmSupervisionCredentialsIntent"]
    )
    @EnsureHasPermission(QUERY_USERS)
    fun createConfirmSupervisionCredentialsIntent_hasQueryUsersPermission_returnsValidIntent() {
        val intent = supervisionManager.createConfirmSupervisionCredentialsIntent()
        assertThat(intent?.action).isEqualTo(ACTION_CONFIRM_SUPERVISION_CREDENTIALS)
        assertThat(intent?.getPackage()).isEqualTo(APPLICATION_PACKAGE)
    }

    @Test
    @ApiTest(
        apis =
            ["android.app.supervision.SupervisionManager#createConfirmSupervisionCredentialsIntent"]
    )
    @EnsureDoesNotHavePermission(MANAGE_USERS, QUERY_USERS)
    fun createConfirmSupervisionCredentialsIntent_noPermission_throwsException() {
        assertThrows(SecurityException::class.java) {
            supervisionManager.createConfirmSupervisionCredentialsIntent()
        }
    }

    // TODO: b/400776510: Add tests to cover the case when supervision is not enabled and PIN is not
    // available.

    companion object {
        @[JvmField ClassRule Rule]
        val deviceState = DeviceState()

        private const val ACTION_CONFIRM_SUPERVISION_CREDENTIALS =
            "android.app.supervision.action.CONFIRM_SUPERVISION_CREDENTIALS"
        private const val APPLICATION_PACKAGE = "com.android.settings"
        private val context = TestApis.context().instrumentedContext()
        private val supervisionManager = context.getSystemService(SupervisionManager::class.java)
    }
}
