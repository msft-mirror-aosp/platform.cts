/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.bedstead.harrier.annotations;

import com.android.bedstead.enterprise.annotations.EnsureHasNoDeviceOwner
import com.android.bedstead.harrier.UserType
import com.android.bedstead.harrier.annotations.meta.EnsureHasProfileAnnotation
import com.android.bedstead.nene.types.OptionalBoolean
import com.android.queryable.annotations.Query

/**
 * This is just a temporary class for compatibility with other repositories
 * use [com.android.bedstead.enterprise.annotations.EnsureHasWorkProfile] instead
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.TYPE)
@Retention(AnnotationRetention.RUNTIME)
@EnsureHasProfileAnnotation(value = "android.os.usertype.profile.MANAGED", hasProfileOwner = true)
@RequireFeature("android.software.managed_users")
@EnsureHasNoDeviceOwner // TODO: This should only apply on Android R+
@Deprecated("this is just a temporary class for SettingsProviderMultiUsersTest")
annotation class EnsureHasWorkProfile(
    val forUser: UserType = UserType.INITIAL_USER,
    val installInstrumentedApp: OptionalBoolean = OptionalBoolean.ANY,
    val dpcKey: String = DEFAULT_DPC_KEY,
    val dpc: Query = Query(),
    val dpcIsPrimary: Boolean = false,
    val isOrganizationOwned: Boolean = false,
    val useParentInstanceOfDpc: Boolean = false,
    val switchedToParentUser: OptionalBoolean = OptionalBoolean.ANY,
    val isQuietModeEnabled: OptionalBoolean = OptionalBoolean.FALSE,
    val priority: Int = ENSURE_HAS_WORK_PROFILE_PRIORITY
)

const val ENSURE_HAS_WORK_PROFILE_PRIORITY = AnnotationPriorityRunPrecedence.REQUIRE_RUN_ON_PRECEDENCE - 1

const val DEFAULT_DPC_KEY = "profileOwner"
