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

import android.Manifest.permission.CREATE_USERS
import android.Manifest.permission.MANAGE_USERS
import android.Manifest.permission.QUERY_USERS
import android.app.supervision.flags.Flags
import com.android.bedstead.flags.annotations.RequireFlagsEnabled
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.annotations.RequireNotAutomotive
import com.android.bedstead.harrier.annotations.RequireNotTv
import com.android.bedstead.harrier.annotations.RequireNotWatch
import com.android.bedstead.multiuser.annotations.RequireMultiUserSupport
import com.android.bedstead.permissions.annotations.EnsureDoesNotHavePermission
import com.android.bedstead.permissions.annotations.EnsureHasPermission
import com.android.compatibility.common.util.ApiTest
import com.android.xts.root.annotations.RequireRootInstrumentation
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertFailsWith
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BedsteadJUnit4::class)
@RequireFlagsEnabled(
    Flags.FLAG_SUPERVISION_MANAGER_APIS,
    android.multiuser.Flags.FLAG_ALLOW_SUPERVISING_PROFILE,
)
@RequireNotAutomotive(reason = "Supervision credentials are only supported on phones and tablets")
@RequireNotWatch(reason = "Supervision credentials are only supported on phones and tablets")
@RequireNotTv(reason = "Supervision credentials are only supported on phones and tablets")
@ApiTest(
    apis =
        ["android.app.supervision.SupervisionManager#createConfirmSupervisionCredentialsIntent"]
)
class SupervisionCredentialsTest : BaseSupervisionTest() {

    @Test
    @EnsureHasPermission(MANAGE_USERS)
    @RequireRootInstrumentation(reason = "Use of MANAGE_USERS")
    fun createConfirmSupervisionCredentialsIntent_hasManageUsersPermission_returnsValidIntent() {
        withSupervisingUser {
            verifyCreateConfirmSupervisionCredentialsIntentIsValid()
        }
    }

    @Test
    @EnsureHasPermission(QUERY_USERS)
    fun createConfirmSupervisionCredentialsIntent_hasQueryUsersPermission_returnsValidIntent() {
        withSupervisingUser {
            verifyCreateConfirmSupervisionCredentialsIntentIsValid()
        }
    }

    @Test
    @EnsureDoesNotHavePermission(MANAGE_USERS, QUERY_USERS)
    fun createConfirmSupervisionCredentialsIntent_noPermission_throwsException() {
        assertFailsWith<SecurityException> {
            supervisionManager.createConfirmSupervisionCredentialsIntent()
        }
    }

    @Test
    @RequireMultiUserSupport
    @EnsureHasPermission(QUERY_USERS, CREATE_USERS)
    fun createConfirmSupervisionCredentialsIntent_supervisionNotEnabled_returnsNull() {
        withSupervisingUser(supervisionEnabled = false) {
            verifyCreateConfirmSupervisionCredentialsIntentIsNull()
        }
    }

    @Test
    @EnsureHasPermission(QUERY_USERS)
    fun createConfirmSupervisionCredentialsIntent_noSupervisingUser_returnsNull() {
        setSupervisionEnabled(true)
        verifyCreateConfirmSupervisionCredentialsIntentIsNull()
    }

    @Test
    @RequireMultiUserSupport
    @EnsureHasPermission(QUERY_USERS, CREATE_USERS)
    fun createConfirmSupervisionCredentialsIntent_supervisingUserMissingSecureLock_returnsNull() {
        withSupervisingUser(hasPin = false) {
            verifyCreateConfirmSupervisionCredentialsIntentIsNull()
        }
    }

    private fun verifyCreateConfirmSupervisionCredentialsIntentIsValid() {
        val intent = supervisionManager.createConfirmSupervisionCredentialsIntent()
        assertThat(intent?.action).isEqualTo(ACTION_CONFIRM_SUPERVISION_CREDENTIALS)
        assertThat(intent?.getPackage()).isEqualTo(APPLICATION_PACKAGE)
    }

    private fun verifyCreateConfirmSupervisionCredentialsIntentIsNull() {
        val intent = supervisionManager.createConfirmSupervisionCredentialsIntent()
        assertThat(intent).isNull()
    }

    companion object {
        private const val ACTION_CONFIRM_SUPERVISION_CREDENTIALS =
            "android.app.supervision.action.CONFIRM_SUPERVISION_CREDENTIALS"
        private const val APPLICATION_PACKAGE = "com.android.settings"
    }
}
