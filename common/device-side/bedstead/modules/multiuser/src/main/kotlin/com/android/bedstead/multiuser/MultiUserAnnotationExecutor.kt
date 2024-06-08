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

import com.android.bedstead.harrier.AnnotationExecutor
import com.android.bedstead.harrier.BedsteadServiceLocator
import com.android.bedstead.harrier.DeviceState.INSTALL_INSTRUMENTED_APP
import com.android.bedstead.harrier.annotations.EnsureHasAdditionalUser
import com.android.bedstead.harrier.annotations.EnsureHasNoAdditionalUser
import com.android.bedstead.harrier.annotations.OtherUser
import com.android.bedstead.harrier.annotations.RequireHeadlessSystemUserMode
import com.android.bedstead.harrier.annotations.RequireMultiUserSupport
import com.android.bedstead.harrier.annotations.RequireNotHeadlessSystemUserMode
import com.android.bedstead.harrier.annotations.RequireNotVisibleBackgroundUsers
import com.android.bedstead.harrier.annotations.RequireNotVisibleBackgroundUsersOnDefaultDisplay
import com.android.bedstead.harrier.annotations.RequirePrivateSpaceSupported
import com.android.bedstead.harrier.annotations.RequireRunNotOnVisibleBackgroundNonProfileUser
import com.android.bedstead.harrier.annotations.RequireRunOnAdditionalUser
import com.android.bedstead.harrier.annotations.RequireRunOnSingleUser
import com.android.bedstead.harrier.annotations.RequireRunOnVisibleBackgroundNonProfileUser
import com.android.bedstead.harrier.annotations.RequireUserSupported
import com.android.bedstead.harrier.annotations.RequireVisibleBackgroundUsers
import com.android.bedstead.harrier.annotations.RequireVisibleBackgroundUsersOnDefaultDisplay
import com.android.bedstead.harrier.annotations.meta.EnsureHasNoUserAnnotation
import com.android.bedstead.harrier.annotations.meta.EnsureHasUserAnnotation
import com.android.bedstead.harrier.annotations.meta.RequireRunOnUserAnnotation
import com.android.bedstead.multiuser.annotations.EnsureCanAddUser
import com.android.bedstead.multiuser.annotations.RequireHasMainUser
import com.android.bedstead.nene.types.OptionalBoolean

@Suppress("unused")
class MultiUserAnnotationExecutor(locator: BedsteadServiceLocator) : AnnotationExecutor {

    private val usersComponent: UsersComponent by locator

    override fun applyAnnotation(annotation: Annotation): Unit = annotation.run {
        when (this) {
            is EnsureCanAddUser -> usersComponent.ensureCanAddUser(number, failureMode)
            is RequireUserSupported -> usersComponent.requireUserSupported(value, failureMode)
            is EnsureHasNoUserAnnotation -> usersComponent.ensureHasNoUser(userType = value)

            is RequireRunOnAdditionalUser -> usersComponent.requireRunOnAdditionalUser(
                switchedToUser
            )

            is EnsureHasAdditionalUser -> usersComponent.ensureHasAdditionalUser(
                installInstrumentedApp,
                switchedToUser
            )

            is EnsureHasNoAdditionalUser -> usersComponent.ensureHasNoAdditionalUser()
            is OtherUser -> usersComponent.handleOtherUser(value)
            is RequireHasMainUser -> logic()
            is RequireRunOnSingleUser -> logic()
            is RequireMultiUserSupport -> logic()
            is RequirePrivateSpaceSupported -> logic()
            is RequireNotHeadlessSystemUserMode -> logic()
            is RequireHeadlessSystemUserMode -> logic()
            is RequireVisibleBackgroundUsers -> logic()
            is RequireNotVisibleBackgroundUsers -> logic()
            is RequireVisibleBackgroundUsersOnDefaultDisplay -> logic()
            is RequireNotVisibleBackgroundUsersOnDefaultDisplay -> logic()
            is RequireRunOnVisibleBackgroundNonProfileUser -> logic()
            is RequireRunNotOnVisibleBackgroundNonProfileUser -> logic()
            else -> applyAnnotationUsingReflection(annotation)
        }
    }

    private fun applyAnnotationUsingReflection(annotation: Annotation) {
        val annotationType = annotation.annotationClass.java
        annotationType.applyEnsureHasUserAnnotation(annotation)
        annotationType.applyRequireRunOnUserAnnotation(annotation)
    }

    private fun Class<out Annotation>.applyEnsureHasUserAnnotation(annotation: Annotation) {
        getAnnotation(EnsureHasUserAnnotation::class.java)?.let { ensureHasUser ->
            usersComponent.ensureHasUser(
                userType = ensureHasUser.value,
                installInstrumentedApp(annotation),
                switchedToUser(annotation)
            )
        }
    }

    private fun Class<out Annotation>.applyRequireRunOnUserAnnotation(annotation: Annotation) {
        getAnnotation(RequireRunOnUserAnnotation::class.java)?.let { requireRunOnUser ->
            usersComponent.requireRunOnUser(
                userTypes = requireRunOnUser.value,
                switchedToUser(annotation)
            )
        }
    }

    private fun Class<out Annotation>.installInstrumentedApp(annotation: Annotation) =
        getMethod(INSTALL_INSTRUMENTED_APP).invoke(annotation) as OptionalBoolean

    private fun Class<out Annotation>.switchedToUser(annotation: Annotation) =
        getMethod(SWITCHED_TO_USER).invoke(annotation) as OptionalBoolean

    companion object {
        private const val SWITCHED_TO_USER = "switchedToUser"
    }
}
