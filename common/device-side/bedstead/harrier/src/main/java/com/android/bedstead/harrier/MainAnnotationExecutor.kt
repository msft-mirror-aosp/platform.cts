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
package com.android.bedstead.harrier

import com.android.bedstead.harrier.annotations.EnsureScreenIsOn
import com.android.bedstead.harrier.annotations.EnsureUnlocked
import com.android.bedstead.harrier.annotations.RequireDoesNotHaveFeature
import com.android.bedstead.harrier.annotations.RequireFactoryResetProtectionPolicySupported
import com.android.bedstead.harrier.annotations.RequireFeature
import com.android.bedstead.harrier.annotations.RequireHeadlessSystemUserMode
import com.android.bedstead.harrier.annotations.RequireLowRamDevice
import com.android.bedstead.harrier.annotations.RequireNotHeadlessSystemUserMode
import com.android.bedstead.harrier.annotations.RequireNotLowRamDevice
import com.android.bedstead.harrier.annotations.RequireNotVisibleBackgroundUsers
import com.android.bedstead.harrier.annotations.RequireNotVisibleBackgroundUsersOnDefaultDisplay
import com.android.bedstead.harrier.annotations.RequirePrivateSpaceSupported
import com.android.bedstead.harrier.annotations.RequireResourcesBooleanValue
import com.android.bedstead.harrier.annotations.RequireRunNotOnVisibleBackgroundNonProfileUser
import com.android.bedstead.harrier.annotations.RequireRunOnVisibleBackgroundNonProfileUser
import com.android.bedstead.harrier.annotations.RequireStorageEncryptionSupported
import com.android.bedstead.harrier.annotations.RequireStorageEncryptionUnsupported
import com.android.bedstead.harrier.annotations.RequireUsbDataSignalingCanBeDisabled
import com.android.bedstead.harrier.annotations.RequireVisibleBackgroundUsers
import com.android.bedstead.harrier.annotations.RequireVisibleBackgroundUsersOnDefaultDisplay

@Suppress("unused")
class MainAnnotationExecutor : AnnotationExecutor {
    override fun applyAnnotation(annotation: Annotation): Unit = annotation.run {
        when (this) {
            is RequirePrivateSpaceSupported -> logic()
            is RequireLowRamDevice -> logic()
            is RequireNotLowRamDevice -> logic()
            is RequireVisibleBackgroundUsers -> logic()
            is RequireNotVisibleBackgroundUsers -> logic()
            is RequireVisibleBackgroundUsersOnDefaultDisplay -> logic()
            is RequireNotVisibleBackgroundUsersOnDefaultDisplay -> logic()
            is RequireRunOnVisibleBackgroundNonProfileUser -> logic()
            is RequireRunNotOnVisibleBackgroundNonProfileUser -> logic()
            is RequireNotHeadlessSystemUserMode -> logic()
            is RequireHeadlessSystemUserMode -> logic()
            is EnsureScreenIsOn -> logic()
            is EnsureUnlocked -> logic()
            is RequireFeature -> logic()
            is RequireDoesNotHaveFeature -> logic()
            is RequireStorageEncryptionSupported -> logic()
            is RequireStorageEncryptionUnsupported -> logic()
            is RequireFactoryResetProtectionPolicySupported -> logic()
            is RequireResourcesBooleanValue -> logic()
            is RequireUsbDataSignalingCanBeDisabled -> logic()
        }
    }
}
