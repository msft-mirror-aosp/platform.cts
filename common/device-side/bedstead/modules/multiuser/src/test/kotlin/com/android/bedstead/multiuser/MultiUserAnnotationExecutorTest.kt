/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.bedstead.multiuser

import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.multiuser.annotations.EnsureCanAddUser
import com.android.bedstead.multiuser.annotations.RequireGuestUserIsEphemeral
import com.android.bedstead.multiuser.annotations.RequireGuestUserIsNotEphemeral
import com.android.bedstead.multiuser.annotations.RequireHasMainUser
import com.android.bedstead.nene.TestApis.resources
import com.android.bedstead.nene.TestApis.users
import com.google.common.truth.Truth
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BedsteadJUnit4::class)
class MultiUserAnnotationExecutorTest {

    @EnsureCanAddUser(number = 2)
    @Test
    fun ensureCanAddUser_canAddUsers() {
        users().createUser().create().use { _ ->
            users().createUser().create().use { _ -> }
        }
    }

    @RequireGuestUserIsEphemeral
    @Test
    fun requireGuestUserIsEphemeral_guestUserIsEphemeral() {
        Truth.assertThat(
            resources().system().getBoolean("config_guestUserEphemeral")
        ).isTrue()
    }

    @RequireGuestUserIsNotEphemeral
    @Test
    fun requireGuestUserIsNotEphemeral_guestUserIsNotEphemeral() {
        Truth.assertThat(
            resources().system().getBoolean("config_guestUserEphemeral")
        ).isFalse()
    }

    @Test
    @RequireHasMainUser(reason = "Test")
    fun requireHasMainUser_hasMainUser() {
        Truth.assertThat(users().main()).isNotNull()
    }

    // TODO b/334025286 move here multi-user tests from DeviceState

    companion object {
        @JvmField
        @ClassRule
        @Rule
        val deviceState = DeviceState()
    }
}
