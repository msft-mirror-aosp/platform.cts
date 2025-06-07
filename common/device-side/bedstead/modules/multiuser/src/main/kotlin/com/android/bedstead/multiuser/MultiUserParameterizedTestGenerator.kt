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

import com.android.bedstead.enterprise.annotations.ensureHasWorkProfile
import com.android.bedstead.enterprise.annotations.requireRunOnWorkProfile
import com.android.bedstead.harrier.DynamicParameterizedAnnotation
import com.android.bedstead.harrier.ParameterizedTestGenerator
import com.android.bedstead.harrier.UserType
import com.android.bedstead.harrier.annotations.CrossUserTest
import com.android.bedstead.harrier.annotations.RequireRunOnInitialUser
import com.android.bedstead.harrier.annotations.UserTest
import com.android.bedstead.multiuser.annotations.EnsureHasAdditionalUser
import com.android.bedstead.multiuser.annotations.EnsureHasCloneProfile
import com.android.bedstead.multiuser.annotations.EnsureHasPrivateProfile
import com.android.bedstead.multiuser.annotations.EnsureHasSecondaryUser
import com.android.bedstead.multiuser.annotations.EnsureHasTvProfile
import com.android.bedstead.multiuser.annotations.OtherUser
import com.android.bedstead.multiuser.annotations.RequireNotHeadlessSystemUserMode
import com.android.bedstead.multiuser.annotations.RequireRunOnAdditionalUser
import com.android.bedstead.multiuser.annotations.RequireRunOnCloneProfile
import com.android.bedstead.multiuser.annotations.RequireRunOnPrimaryUser
import com.android.bedstead.multiuser.annotations.RequireRunOnPrivateProfile
import com.android.bedstead.multiuser.annotations.RequireRunOnSecondaryUser
import com.android.bedstead.multiuser.annotations.RequireRunOnSystemUser
import com.android.bedstead.multiuser.annotations.RequireRunOnTvProfile
import com.android.bedstead.nene.types.OptionalBoolean

/**
 * [ParameterizedTestGenerator] for MultiUser
 */
@Suppress("unused")
class MultiUserParameterizedTestGenerator : ParameterizedTestGenerator {

    override fun generateReplacementAnnotations(
        annotation: Annotation
    ): List<DynamicParameterizedAnnotation> {
        return when (annotation) {
            is UserTest -> generateUserAnnotations(annotation)
            is CrossUserTest -> generateCrossUserAnnotations(annotation)
            else -> emptyList()
        }
    }

    private fun generateUserAnnotations(
        annotation: UserTest
    ): List<DynamicParameterizedAnnotation> {
        val replacementAnnotations: MutableList<DynamicParameterizedAnnotation> = mutableListOf()

        for (userType in annotation.value) {
            val runOnUserAnnotation = getRunOnAnnotation(userType, "@UserTest")
            replacementAnnotations.add(
                DynamicParameterizedAnnotation(
                    userType.name,
                    arrayOf(runOnUserAnnotation),
                    annotation.priority
                )
            )
        }

        return replacementAnnotations
    }

    private fun generateCrossUserAnnotations(
        annotation: CrossUserTest
    ): List<DynamicParameterizedAnnotation> {
        val replacementAnnotations: MutableList<DynamicParameterizedAnnotation> = mutableListOf()

        for (userPair in annotation.value) {
            var annotations = arrayOf(
                getRunOnAnnotation(userPair.from, "@CrossUserTest"),
                OtherUser(userPair.to)
            )
            if (userPair.from != userPair.to) {
                getHasUserAnnotation(userPair.to, "@CrossUserTest")?.let {
                    annotations += it
                }
            }

            replacementAnnotations.add(
                DynamicParameterizedAnnotation(
                    userPair.from.name + "_to_" + userPair.to.name,
                    annotations,
                    annotation.priority
                )
            )
        }

        return replacementAnnotations
    }

    private fun getHasUserAnnotation(userType: UserType, annotationName: String): Annotation? {
        return when (userType) {
            UserType.SYSTEM_USER -> null // We always have a system user
            UserType.CURRENT_USER -> null // We always have a current user
            UserType.INITIAL_USER -> null // We always have an initial user
            UserType.ADDITIONAL_USER -> EnsureHasAdditionalUser()
            UserType.PRIMARY_USER -> RequireNotHeadlessSystemUserMode(
                reason = "Headless System User Mode Devices do not have a primary user"
            )

            UserType.SECONDARY_USER -> EnsureHasSecondaryUser()
            UserType.WORK_PROFILE -> ensureHasWorkProfile()
            UserType.TV_PROFILE -> EnsureHasTvProfile()
            UserType.CLONE_PROFILE -> EnsureHasCloneProfile()
            UserType.PRIVATE_PROFILE -> EnsureHasPrivateProfile()
            else -> throw IllegalStateException(
                "UserType $userType is not compatible with $annotationName"
            )
        }
    }

    private fun getRunOnAnnotation(userType: UserType, annotationName: String): Annotation? {
        return when (userType) {
            UserType.SYSTEM_USER -> RequireRunOnSystemUser()
            UserType.CURRENT_USER -> null // No requirement, run on current user
            UserType.INITIAL_USER -> RequireRunOnInitialUser()
            UserType.ADDITIONAL_USER -> RequireRunOnAdditionalUser()
            UserType.PRIMARY_USER -> RequireRunOnPrimaryUser(switchedToUser = OptionalBoolean.ANY)
            UserType.SECONDARY_USER -> RequireRunOnSecondaryUser()
            UserType.WORK_PROFILE -> requireRunOnWorkProfile()
            UserType.TV_PROFILE -> RequireRunOnTvProfile()
            UserType.CLONE_PROFILE -> RequireRunOnCloneProfile()
            UserType.PRIVATE_PROFILE -> RequireRunOnPrivateProfile()
            else -> throw java.lang.IllegalStateException(
                "UserType $userType is not compatible with $annotationName"
            )
        }
    }
}
