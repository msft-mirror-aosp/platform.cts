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

package com.android.bedstead.enterprise.annotations

import com.android.bedstead.harrier.annotations.AnnotationPriorityRunPrecedence.DO_PO_PRIORITY
import com.android.bedstead.harrier.annotations.UsesAnnotationExecutor
import com.android.bedstead.multiuser.annotations.RequireHeadlessSystemUserMode

/**
 * Mark that a test requires that a device controller is set.
 *
 * @param isPrimary Whether this DPC should be returned by calls to [DeviceState#dpc()].
 *  Only one policy manager per test should be marked as primary.
 * @param priority Priority sets the order that annotations will be resolved.
 *  Annotations with a lower priority will be resolved before annotations with a higher
 *  priority.
 *
 *  If there is an order requirement between annotations, ensure that the priority of the
 *  annotation which must be resolved first is lower than the one which must be resolved later.
 *
 *  Priority can be set to a [AnnotationPriorityRunPrecedence] constant, or to any [int].
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.TYPE, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
// TODO(b/461439459): Require feature flag when the API lands.
@RequireHeadlessSystemUserMode(reason = "Multi-User Management assumes HSUM")
@UsesAnnotationExecutor(UsesAnnotationExecutor.ENTERPRISE)
annotation class EnsureHasDeviceController(
    val isPrimary: Boolean = false,
    val priority: Int = DO_PO_PRIORITY,
)
