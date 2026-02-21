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
import android.app.supervision.flags.Flags
import android.os.UserManager
import com.android.bedstead.flags.annotations.RequireFlagsEnabled
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.nene.TestApis
import com.android.compatibility.common.util.ApiTest
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.testng.Assert.assertThrows

@RunWith(BedsteadJUnit4::class)
@RequireFlagsEnabled(Flags.FLAG_ENABLE_SUPERVISION_MANAGER_POLICY_APIS)
@ApiTest(
    apis =
        [
            "android.app.supervision.SupervisionManager#setShouldAllowBypassingSupervisionRoleQualification",
            "android.app.supervision.SupervisionManager#shouldAllowBypassingSupervisionRoleQualification",
        ]
)
class SupervisionRoleTest : BaseSupervisionTest() {

    @After
    fun tearDown() {
        callWithShellPermissionIdentity(BYPASS_ROLE_QUALIFICATION) {
            supervisionManager.setShouldAllowBypassingSupervisionRoleQualification(false)
        }
    }

    @Test
    fun setShouldAllowBypassingSupervisionRoleQualification_noPermission_throwsException() {
        assertThrows(SecurityException::class.java) {
            supervisionManager.setShouldAllowBypassingSupervisionRoleQualification(true)
        }
    }

    @Test
    fun setShouldAllowBypassingSupervisionRoleQualification_succeeds() {
        verifySetShouldAllowBypassingSupervisionRoleQualification()
    }

    @Test
    fun setShouldAllowBypassingSupervisionRoleQualification_withNonTestUsers_returnsFalse() {
        assumeTrue("Device does not support multiple users", UserManager.supportsMultipleUsers())

        verifySetShouldAllowBypassingSupervisionRoleQualification()
        withUser {
            assertThat(supervisionManager.shouldAllowBypassingSupervisionRoleQualification())
                .isFalse()
        }
    }

    @Test
    fun setShouldAllowBypassingSupervisionRoleQualification_withTestUsers_returnsTrue() {
        assumeTrue("Device does not support multiple users", UserManager.supportsMultipleUsers())

        verifySetShouldAllowBypassingSupervisionRoleQualification()
        withUserTestUser {
            assertThat(supervisionManager.shouldAllowBypassingSupervisionRoleQualification())
                .isTrue()
        }
    }

    private fun verifySetShouldAllowBypassingSupervisionRoleQualification() {
        callWithShellPermissionIdentity(BYPASS_ROLE_QUALIFICATION, MANAGE_ROLE_HOLDERS) {
            supervisionManager.setShouldAllowBypassingSupervisionRoleQualification(true)
            assertThat(supervisionManager.shouldAllowBypassingSupervisionRoleQualification())
                .isTrue()
        }
    }

    private fun withUser(forTesting: Boolean = false, action: () -> Unit) {
        TestApis.users().createUser().forTesting(forTesting).create().use { user ->
            try {
                callWithShellPermissionIdentity(MANAGE_ROLE_HOLDERS) { action() }
            } finally {
                user.remove()
            }
        }
    }

    private fun withUserTestUser(action: () -> Unit) {
        withUser(true, action)
    }
}
