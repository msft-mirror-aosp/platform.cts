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
package com.android.bedstead.harrier

import com.android.bedstead.harrier.annotations.AnnotationPriorityRunPrecedence
import com.android.bedstead.nene.exceptions.NeneException
import java.lang.reflect.InvocationTargetException

private val ANNOTATION_PRIORITY_CACHE = mutableMapOf<Annotation, Int>()

/**
 * Returns the run priority for this annotation.
 *
 * If no priority is defined,
 * [AnnotationPriorityRunPrecedence.PRECEDENCE_NOT_IMPORTANT] is returned.
 */
fun Annotation.priority() = ANNOTATION_PRIORITY_CACHE.getOrPut(this) {
    computeAnnotationPriority()
}

private fun Annotation.computeAnnotationPriority(): Int {
    if (this is DynamicParameterizedAnnotation) {
        return priority
    }

    return try {
        return annotationClass.java.getMethod("priority").invoke(this) as Int
    } catch (ignored: NoSuchMethodException) {
        AnnotationPriorityRunPrecedence.PRECEDENCE_NOT_IMPORTANT
    } catch (exception: Exception) {
        when (exception) {
            is IllegalAccessException,
            is InvocationTargetException,
            is ClassCastException -> {
                throw NeneException(
                    "Failed to invoke priority on this annotation: $this",
                    exception
                )
            }

            else -> throw exception
        }
    }
}

/**
 * Sorts the provided list of annotations using the priority method added to an annotation.
 * Lower priority numbers are earlier in the list.
 * If a priority is not provided,
 * [AnnotationPriorityRunPrecedence.PRECEDENCE_NOT_IMPORTANT] will be used.
 */
fun List<Annotation>.sortedByPriority(): MutableList<Annotation> = sortedBy {
    it.priority()
}.toMutableList()
