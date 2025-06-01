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
package com.android.bedstead.multiuser

import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.harrier.UserType
import com.android.bedstead.harrier.annotations.CrossUserTest
import com.android.bedstead.harrier.annotations.UserPair
import com.android.bedstead.harrier.annotations.UserTest
import com.android.bedstead.nene.TestApis.users
import com.google.common.truth.Truth
import org.junit.ClassRule
import org.junit.Rule
import org.junit.runner.RunWith

@RunWith(BedsteadJUnit4::class)
class MultiUserParameterizedTestGeneratorTest {

    @UserTest(UserType.INITIAL_USER, UserType.CLONE_PROFILE)
    fun userTestAnnotation_isRunningOnCorrectUsers() {
        if (users().instrumented() != sDeviceState.initialUser()) {
            Truth.assertThat(users().instrumented()).isEqualTo(sDeviceState.cloneProfile())
        }
    }

    @CrossUserTest(
        UserPair(from = UserType.INITIAL_USER, to = UserType.CLONE_PROFILE),
        UserPair(from = UserType.CLONE_PROFILE, to = UserType.INITIAL_USER)
    )
    fun crossUserTestAnnotation_isRunningWithCorrectUserPairs() {
        if (users().instrumented() == sDeviceState.initialUser()) {
            Truth.assertThat(sDeviceState.otherUser()).isEqualTo(sDeviceState.cloneProfile())
        } else {
            Truth.assertThat(users().instrumented()).isEqualTo(sDeviceState.cloneProfile())
            Truth.assertThat(sDeviceState.otherUser()).isEqualTo(sDeviceState.initialUser())
        }
    }

    companion object {
        @ClassRule
        @Rule
        @JvmField
        val sDeviceState = DeviceState()
    }
}
