/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.bedstead.enterprise.annotations

import com.android.bedstead.harrier.UserType
import com.android.bedstead.harrier.annotations.AnnotationPriorityRunPrecedence
import com.android.bedstead.harrier.annotations.RequireNotInstantApp
import com.android.bedstead.harrier.annotations.UsesAnnotationExecutor
import com.google.auto.value.AutoAnnotation
import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.ANNOTATION_CLASS
import kotlin.annotation.AnnotationTarget.CLASS
import kotlin.annotation.AnnotationTarget.FUNCTION
import kotlin.annotation.AnnotationTarget.PROPERTY_GETTER
import kotlin.annotation.AnnotationTarget.PROPERTY_SETTER

/**
 * Mark that a test requires a SystemSupervision role holder.
 */
@Target(FUNCTION, PROPERTY_GETTER, PROPERTY_SETTER, ANNOTATION_CLASS, CLASS)
@Retention(RUNTIME)
@RequireNotInstantApp(reason = "Instant Apps cannot run Enterprise Tests")
@UsesAnnotationExecutor(UsesAnnotationExecutor.ENTERPRISE)
annotation class EnsureHasSystemSupervisionRoleHolder(
    /** Which user type the system supvervision role holder should be installed on.  */
    val onUser: UserType = UserType.INSTRUMENTED_USER,
    /**
     * Whether this delegate should be returned by calls to `DeviceState#dpc()`.
     *
     * Only one policy manager per test should be marked as primary.
     */
    val isPrimary: Boolean = false,
    /**
     * Priority sets the order that annotations will be resolved.
     *
     * Annotations with a lower priority will be resolved before annotations with a higher
     * priority.
     *
     * If there is an order requirement between annotations, ensure that the priority of the
     * annotation which must be resolved first is lower than the one which must be resolved later.
     *
     * Priority can be set to a [AnnotationPriorityRunPrecedence] constant, or to any [int].
     */
    val priority: Int = ENSURE_HAS_SYSTEM_SUPERVISION_ROLE_HOLDER_PRIORITY
) {
    companion object {
        /**
         * We want the isPrimary here to take precedence over any other
         */
        const val ENSURE_HAS_SYSTEM_SUPERVISION_ROLE_HOLDER_PRIORITY: Int =
            AnnotationPriorityRunPrecedence.LATE
    }
}

@AutoAnnotation
fun ensureHasSystemSupervisionRoleHolder(
    onUser: UserType,
    isPrimary: Boolean
): EnsureHasSystemSupervisionRoleHolder {
    return AutoAnnotation_EnsureHasSystemSupervisionRoleHolderKt_ensureHasSystemSupervisionRoleHolder(
        onUser,
        isPrimary
    )
}
