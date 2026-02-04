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
import com.android.bedstead.flags.annotations.RequireFlagsEnabled
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.compatibility.common.util.ApiTest
import com.google.common.truth.Truth.assertThat
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

    @Test
    fun setShouldAllowBypassingSupervisionRoleQualification_noPermission_throwsException() {
        assertThrows(SecurityException::class.java) {
            supervisionManager.setShouldAllowBypassingSupervisionRoleQualification(true)
        }
    }

    @Test
    fun setShouldAllowBypassingSupervisionRoleQualification_succeeds() {
        callWithShellPermissionIdentity(BYPASS_ROLE_QUALIFICATION, MANAGE_ROLE_HOLDERS) {
            supervisionManager.setShouldAllowBypassingSupervisionRoleQualification(true)
            assertThat(supervisionManager.shouldAllowBypassingSupervisionRoleQualification())
                .isTrue()

            supervisionManager.setShouldAllowBypassingSupervisionRoleQualification(false)
            assertThat(supervisionManager.shouldAllowBypassingSupervisionRoleQualification())
                .isFalse()
        }
    }
}
