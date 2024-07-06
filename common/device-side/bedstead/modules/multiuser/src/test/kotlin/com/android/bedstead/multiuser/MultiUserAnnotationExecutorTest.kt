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

import android.os.UserManager
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.harrier.UserType
import com.android.bedstead.harrier.annotations.EnsureDoesNotHaveUserRestriction
import com.android.bedstead.harrier.annotations.EnsureHasAdditionalUser
import com.android.bedstead.harrier.annotations.EnsureHasUserRestriction
import com.android.bedstead.multiuser.annotations.EnsureCanAddUser
import com.android.bedstead.multiuser.annotations.RequireGuestUserIsEphemeral
import com.android.bedstead.multiuser.annotations.RequireGuestUserIsNotEphemeral
import com.android.bedstead.multiuser.annotations.RequireHasMainUser
import com.android.bedstead.nene.TestApis.devicePolicy
import com.android.bedstead.nene.TestApis.resources
import com.android.bedstead.nene.TestApis.users
import com.google.common.truth.Truth.assertThat
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
        assertThat(resources().system().getBoolean("config_guestUserEphemeral")).isTrue()
    }

    @RequireGuestUserIsNotEphemeral
    @Test
    fun requireGuestUserIsNotEphemeral_guestUserIsNotEphemeral() {
        assertThat(resources().system().getBoolean("config_guestUserEphemeral")).isFalse()
    }

    @Test
    @RequireHasMainUser(reason = "Test")
    fun requireHasMainUser_hasMainUser() {
        assertThat(users().main()).isNotNull()
    }

    @EnsureHasUserRestriction(USER_RESTRICTION)
    @Test
    fun ensureHasUserRestrictionAnnotation_userRestrictionIsSet() {
        assertThat(devicePolicy().userRestrictions().isSet(USER_RESTRICTION)).isTrue()
    }

    @EnsureDoesNotHaveUserRestriction(USER_RESTRICTION)
    @Test
    fun ensureDoesNotHaveUserRestrictionAnnotation_userRestrictionIsNotSet() {
        assertThat(devicePolicy().userRestrictions().isSet(USER_RESTRICTION)).isFalse()
    }

    @EnsureHasUserRestriction(USER_RESTRICTION)
    @EnsureHasUserRestriction(SECOND_USER_RESTRICTION)
    @Test
    fun ensureHasUserRestrictionAnnotation_multipleRestrictions_userRestrictionsAreSet() {
        assertThat(devicePolicy().userRestrictions().isSet(USER_RESTRICTION)).isTrue()
        assertThat(devicePolicy().userRestrictions().isSet(SECOND_USER_RESTRICTION)).isTrue()
    }

    @EnsureDoesNotHaveUserRestriction(USER_RESTRICTION)
    @EnsureDoesNotHaveUserRestriction(SECOND_USER_RESTRICTION)
    @Test
    fun ensureDoesNotHaveUserRestrictionAnnotation_multipleRestrictions_userRestrictionsAreNotSet() {
        assertThat(devicePolicy().userRestrictions().isSet(USER_RESTRICTION)).isFalse()
        assertThat(devicePolicy().userRestrictions().isSet(SECOND_USER_RESTRICTION)).isFalse()
    }

    @EnsureHasAdditionalUser
    @EnsureHasUserRestriction(value = USER_RESTRICTION, onUser = UserType.ADDITIONAL_USER)
    @Test
    fun ensureHasUserRestrictionAnnotation_differentUser_userRestrictionIsSet() {
        assertThat(
            devicePolicy().userRestrictions(deviceState.additionalUser()).isSet(USER_RESTRICTION)
        ).isTrue()
    }

    @EnsureHasAdditionalUser
    @EnsureDoesNotHaveUserRestriction(value = USER_RESTRICTION, onUser = UserType.ADDITIONAL_USER)
    @Test
    fun ensureDoesNotHaveUserRestrictionAnnotation_differentUser_userRestrictionIsNotSet() {
        assertThat(
            devicePolicy().userRestrictions(deviceState.additionalUser()).isSet(USER_RESTRICTION)
        ).isFalse()
    }

    // TODO b/334025286 move here multi-user tests from DeviceState

    companion object {
        @JvmField
        @ClassRule
        @Rule
        val deviceState = DeviceState()

        private const val USER_RESTRICTION = UserManager.DISALLOW_AUTOFILL
        private const val SECOND_USER_RESTRICTION = UserManager.DISALLOW_AIRPLANE_MODE
    }
}
