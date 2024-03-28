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
package com.android.bedstead.harrier.annotations

import com.android.bedstead.harrier.AnnotationExecutor
import kotlin.reflect.KClass

/**
 * Annotation to apply to an annotation outside of Harrier to indicate it should be processed
 * with a particular [AnnotationExecutor].
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class UsesAnnotationExecutor(
    /** The [AnnotationExecutor] to use when parsing this annotation.
     *
     * One of this and [weakValue] must be provided.
     */
    val value: KClass<out AnnotationExecutor> = AnnotationExecutor::class,

    /**
     * The fully qualified name of the [AnnotationExecutor] to use when parsing this annotation.
     *
     * This is available for cases where the annotation needs to be defined in a separate target
     * to the executor, for example where the annotation cannot be defined in an Android target.
     *
     * One of this and [value] must be provided.
     */
    val weakValue: String = ""
)
