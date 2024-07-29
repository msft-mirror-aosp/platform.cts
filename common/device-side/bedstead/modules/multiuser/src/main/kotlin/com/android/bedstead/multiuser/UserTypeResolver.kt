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

import com.android.bedstead.enterprise.EnterpriseComponent
import com.android.bedstead.harrier.BedsteadServiceLocator
import com.android.bedstead.harrier.UserType
import com.android.bedstead.harrier.UserType.ADDITIONAL_USER
import com.android.bedstead.harrier.UserType.ADMIN_USER
import com.android.bedstead.harrier.UserType.ANY
import com.android.bedstead.harrier.UserType.CLONE_PROFILE
import com.android.bedstead.harrier.UserType.CURRENT_USER
import com.android.bedstead.harrier.UserType.DPC_USER
import com.android.bedstead.harrier.UserType.INITIAL_USER
import com.android.bedstead.harrier.UserType.INSTRUMENTED_USER
import com.android.bedstead.harrier.UserType.PRIMARY_USER
import com.android.bedstead.harrier.UserType.PRIVATE_PROFILE
import com.android.bedstead.harrier.UserType.SECONDARY_USER
import com.android.bedstead.harrier.UserType.SYSTEM_USER
import com.android.bedstead.harrier.UserType.TV_PROFILE
import com.android.bedstead.harrier.UserType.WORK_PROFILE
import com.android.bedstead.nene.TestApis.users
import com.android.bedstead.nene.users.UserReference
import com.android.bedstead.nene.users.UserType.SECONDARY_USER_TYPE_NAME

/**
 * A tool to create the appropriate [UserReference] for the [UserType]
 */
class UserTypeResolver(locator: BedsteadServiceLocator) {

    private val usersComponent: UsersComponent by locator
    private val enterpriseComponent: EnterpriseComponent by locator

    /**
     * Creates appropriate [UserReference] for the [UserType]
     */
    @Suppress("DEPRECATION")
    fun toUser(userType: UserType): UserReference {
        return when (userType) {
            SYSTEM_USER -> users().system()
            INSTRUMENTED_USER -> users().instrumented()
            CURRENT_USER -> users().current()
            PRIMARY_USER -> users().primary()
            SECONDARY_USER -> usersComponent.user(SECONDARY_USER_TYPE_NAME)
            WORK_PROFILE -> enterpriseComponent.workProfile()
            TV_PROFILE -> usersComponent.tvProfile()
            DPC_USER -> enterpriseComponent.dpc().user()
            INITIAL_USER -> users().initial()
            ADDITIONAL_USER -> usersComponent.additionalUser()
            CLONE_PROFILE -> usersComponent.cloneProfile()
            PRIVATE_PROFILE -> usersComponent.privateProfile()
            ADMIN_USER -> users().admin()
            ANY -> throw IllegalStateException("ANY UserType can not be used here")
        }
    }
}
