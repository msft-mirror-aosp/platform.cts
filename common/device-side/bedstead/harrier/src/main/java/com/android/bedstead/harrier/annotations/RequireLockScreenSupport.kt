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
package com.android.bedstead.harrier.annotations

import android.content.pm.PackageManager.FEATURE_SECURE_LOCK_SCREEN

/**
 * Mark that a test requires a device to have a lock screen available.
 */
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION
)
@Retention(AnnotationRetention.RUNTIME)
@RequireFeature(FEATURE_SECURE_LOCK_SCREEN)
annotation class RequireLockScreenSupport(
    /**
     The action to be taken if Lock Screen Support is not available for a particular device
     */
    val failureMode: FailureMode = FailureMode.SKIP,
    /**
     Annotation should be considered at the earliest stage of test run because of requirement to
     skip if LockScreenSupport is not available
     */
    val priority: Int = AnnotationPriorityRunPrecedence.EARLY,
)
