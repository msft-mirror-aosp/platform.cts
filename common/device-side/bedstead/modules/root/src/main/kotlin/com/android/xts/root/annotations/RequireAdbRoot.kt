/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.xts.root.annotations

import com.android.bedstead.harrier.annotations.AnnotationPriorityRunPrecedence
import com.android.bedstead.harrier.annotations.FailureMode
import com.android.bedstead.harrier.annotations.UsesAnnotationExecutor
import com.google.auto.value.AutoAnnotation

/**
 * Mark that a test method requires adb to be able to access root capabilities.
 *
 * You can use `DeviceState` to ensure that the device enters
 * the correct state for the method.
 */
// This can only be applied directly to tests to make it clear to test authors
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@UsesAnnotationExecutor(weakValue = "com.android.xts.root.RootAnnotationExecutor")
annotation class RequireAdbRoot(
    val reason: String = "",

    val failureMode: FailureMode = FailureMode.SKIP,

    /**
     * Priority sets the order that annotations will be resolved.
     *
     *
     * Annotations with a lower priority will be resolved before annotations with a higher
     * priority.
     *
     *
     * If there is an order requirement between annotations, ensure that the priority of the
     * annotation which must be resolved first is lower than the one which must be resolved later.
     *
     *
     * Priority can be set to a [AnnotationPriorityRunPrecedence] constant, or to any [int].
     */
    val priority: Int = AnnotationPriorityRunPrecedence.FIRST)

@AutoAnnotation
fun requireAdbRoot(
    reason: String,
    failureMode: FailureMode): RequireAdbRoot =
    AutoAnnotation_RequireAdbRootKt_requireAdbRoot(reason, failureMode)