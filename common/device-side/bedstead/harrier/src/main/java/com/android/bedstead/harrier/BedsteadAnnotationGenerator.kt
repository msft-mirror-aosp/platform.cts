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
package com.android.bedstead.harrier

import com.android.bedstead.harrier.annotations.meta.ParameterizedAnnotation
import com.android.bedstead.harrier.annotations.meta.RepeatingAnnotation

/**
 * This class exposes a number of annotation-related helper methods.
 */
object BedsteadAnnotationGenerator {
    /**
     * Returns whether a given annotation is a parameterized annotation.
     *
     * Returns true for @link DynamicParameterizedAnnotation.
     */
    fun isParameterizedAnnotation(annotation: Annotation): Boolean {
        if (annotation is DynamicParameterizedAnnotation) {
            return true
        }

        return isAnnotationClassParameterizedAnnotation(annotation)
    }

    /**
     * Returns whether a given annotation is a parameterized annotation.
     *
     * Returns false for @link DynamicParameterizedAnnotation.
     */
    fun isAnnotationClassParameterizedAnnotation(annotation: Annotation): Boolean {
        if (annotation is DynamicParameterizedAnnotation) {
            return false
        }

        return annotation.annotationClass.java.isAnnotationPresent(
            ParameterizedAnnotation::class.java
        )
    }

    /** Returns whether a given annotation is a repeating annotation. */
    fun isRepeatingAnnotation(annotation: Annotation): Boolean {
        if (annotation is DynamicParameterizedAnnotation) {
            return false
        }

        return annotation.annotationClass.java.isAnnotationPresent(RepeatingAnnotation::class.java)
    }

    /**
     * Returns all indirect annotations of a given annotation. Those are the annotations that are
     * annotating the annotation class.
     */
    fun getIndirectAnnotations(annotation: Annotation): Array<Annotation> {
        if (annotation is DynamicParameterizedAnnotation) {
            return annotation.annotations()
        }
        return annotation.annotationClass.java.getAnnotations()
    }
}
