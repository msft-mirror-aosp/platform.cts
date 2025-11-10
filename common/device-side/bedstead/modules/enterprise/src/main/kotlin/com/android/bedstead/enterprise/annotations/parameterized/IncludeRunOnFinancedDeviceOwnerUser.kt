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
package com.android.bedstead.enterprise.annotations.parameterized

import com.android.bedstead.enterprise.annotations.EnsureHasDeviceOwner
import com.android.bedstead.enterprise.annotations.EnsureHasNoDelegate
import com.android.bedstead.enterprise.annotations.EnsureHasNoWorkProfile
import com.android.bedstead.harrier.annotations.AnnotationPriorityRunPrecedence
import com.android.bedstead.harrier.annotations.ParameterizedAnnotationScope
import com.android.bedstead.harrier.annotations.meta.ParameterizedAnnotation
import com.android.bedstead.multiuser.annotations.RequireRunOnSystemUser
import com.android.bedstead.nene.devicepolicy.DeviceOwnerType
import com.android.bedstead.nene.types.OptionalBoolean
import com.google.auto.value.AutoAnnotation
import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.CLASS
import kotlin.annotation.AnnotationTarget.FUNCTION
import kotlin.annotation.AnnotationTarget.PROPERTY_GETTER
import kotlin.annotation.AnnotationTarget.PROPERTY_SETTER

/** Parameterize a test so that it runs on the same user as the financed device owner.  */
@Target(FUNCTION, PROPERTY_GETTER, PROPERTY_SETTER, CLASS)
@Retention(RUNTIME)
@ParameterizedAnnotation(scope = ParameterizedAnnotationScope.ENTERPRISE)
@RequireRunOnSystemUser(switchedToUser = OptionalBoolean.ANY)
@EnsureHasNoWorkProfile
@EnsureHasDeviceOwner(isPrimary = true, type = DeviceOwnerType.FINANCED, key = "dpc")
@EnsureHasNoDelegate
annotation class IncludeRunOnFinancedDeviceOwnerUser(
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
    val priority: Int = AnnotationPriorityRunPrecedence.LATE
)

@AutoAnnotation
fun includeRunOnFinancedDeviceOwnerUser(): IncludeRunOnFinancedDeviceOwnerUser {
    return AutoAnnotation_IncludeRunOnFinancedDeviceOwnerUserKt_includeRunOnFinancedDeviceOwnerUser()
}
