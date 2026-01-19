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
package com.android.bedstead.enterprise.annotations.parameterized

import com.android.bedstead.enterprise.annotations.EnsureHasDeviceController
import com.android.bedstead.harrier.annotations.AnnotationPriorityRunPrecedence.EARLY
import com.android.bedstead.harrier.annotations.UsesAnnotationExecutor
import com.android.bedstead.multiuser.annotations.RequireRunOnSystemUser
import com.google.auto.value.AutoAnnotation

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.TYPE)
@Retention(AnnotationRetention.RUNTIME)
@RequireRunOnSystemUser
@EnsureHasDeviceController(isPrimary = true)
@UsesAnnotationExecutor(UsesAnnotationExecutor.ENTERPRISE)
annotation class IncludeRunOnSystemUserWithDeviceController(
    /**
     * Priority sets the order that annotations will be resolved.
     *
     * <p>Annotations with a lower priority will be resolved before annotations with a higher
     * priority.
     *
     * <p>If there is an order requirement between annotations, ensure that the priority of the
     * annotation which must be resolved first is lower than the one which must be resolved later.
     *
     * <p>Priority can be set to a {@link AnnotationPriorityRunPrecedence} constant, or to any {@link int}.
     */
    val priority: Int = EARLY
)

@AutoAnnotation
fun includeRunOnSystemUserWithDeviceController(): IncludeRunOnSystemUserWithDeviceController {
    return AutoAnnotation_IncludeRunOnSystemUserWithDeviceControllerKt_includeRunOnSystemUserWithDeviceController()
}

