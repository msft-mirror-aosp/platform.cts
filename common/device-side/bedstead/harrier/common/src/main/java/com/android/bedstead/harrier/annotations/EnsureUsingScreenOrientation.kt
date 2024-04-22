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

package com.android.bedstead.harrier.annotations

import com.android.bedstead.nene.display.DisplayProperties

/**
 * Ensure that the device is in given screen orientation.
 *
 * <p>You can use {@code DeviceState} to ensure that the device enters
 * the correct state for the method.
 */
@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.CLASS
)
@Retention(AnnotationRetention.RUNTIME)
@RequireRunOnSystemUser
annotation class EnsureUsingScreenOrientation(
    val orientation: DisplayProperties.ScreenOrientation,

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
    val priority: Int = AnnotationPriorityRunPrecedence.MIDDLE
)
