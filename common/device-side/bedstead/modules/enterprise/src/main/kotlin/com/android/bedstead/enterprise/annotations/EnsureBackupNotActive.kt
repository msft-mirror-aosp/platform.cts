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
import com.android.bedstead.harrier.annotations.UsesAnnotationExecutor

/**
 * Mark that a test requires that the backup service is not active for a given user.
 *
 * This is useful for tests that are running on a secondary user, where the backup service might
 * be active from a previous test.
 *
 * Note that this annotation does not affect private profiles, as toggling private profile
 * backup activation is not allowed by the framework.
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.TYPE, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@UsesAnnotationExecutor(UsesAnnotationExecutor.ENTERPRISE)
annotation class EnsureBackupNotActive(
    /** The user the backup service should not be active on. */
    val onUser: UserType = UserType.INSTRUMENTED_USER
)
