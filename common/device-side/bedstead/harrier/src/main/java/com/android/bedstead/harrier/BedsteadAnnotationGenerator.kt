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

import com.android.bedstead.harrier.annotations.UsesParameterizedTestGenerator
import com.android.bedstead.harrier.annotations.meta.ParameterizedAnnotation
import com.android.bedstead.harrier.annotations.meta.RepeatingAnnotation
import com.android.bedstead.harrier.annotations.parameterized.IncludeNone
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import com.google.errorprone.annotations.CanIgnoreReturnValue

/** This class exposes a number of annotation-related helper methods. */
object BedsteadAnnotationGenerator {

    /** Standard java annotations that our processing logic ignores. */
    private val IGNORED_ANNOTATION_PACKAGES: ImmutableSet<String> =
        ImmutableSet.of(
            "java.lang.annotation",
            "com.android.bedstead.harrier.annotations.meta",
            "org.junit",
        )

    /** Standard java / kotlin annotation prefixes that our processing logic ignores. */
    private val IGNORED_ANNOTATION_PREFIXES: ImmutableList<String> =
        ImmutableList.of("kotlin", "com.android.networkstack.kotlin")

    private val mLocator: BedsteadServiceLocator = BedsteadServiceLocator()

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

    /**
     * Return whether the given annotation should be disregarded by the annotation processing. This
     * is the case for standard java / kotlin annotations.
     */
    fun shouldSkipAnnotation(annotation: Annotation): Boolean {
        if (annotation is DynamicParameterizedAnnotation) {
            return false
        }

        if (annotation.annotationClass.java == IncludeNone::class.java) {
            return true
        }

        val annotationPackage: String = annotation.annotationClass.java.getPackage().name

        if (IGNORED_ANNOTATION_PACKAGES.contains(annotationPackage)) {
            return true
        }

        return IGNORED_ANNOTATION_PREFIXES.stream().anyMatch { annotationPackage.startsWith(it) }
    }

    /**
     * Replace the given annotation using the related [ParameterizedTestGenerator].
     *
     * To be used before general annotation processing.
     */
    fun maybeReplaceUsingParameterizedTestGenerator(
        annotation: Annotation,
        classAnnotations: List<Annotation>,
    ): List<Annotation>? {
        val parameterizedTestGenerator =
            annotation.annotationClass.java.getAnnotation(
                UsesParameterizedTestGenerator::class.java
            )
        return parameterizedTestGenerator?.let {
            val generator: ParameterizedTestGenerator = mLocator.get(it.value)
            val replacementAnnotations: List<Annotation> =
                generator.generateReplacementAnnotations(annotation, classAnnotations)
            return replacementAnnotations.sortedByPriority()
        }
    }

    /**
     * First expands the list of annotations using [maybeReplaceUsingParameterizedTestGenerator] and
     * then gathers all parameterized annotations as defined by [isParameterizedAnnotation].
     *
     * @param methodAnnotations the array of annotations of test method
     * @param classAnnotations the array of annotations of test class. These should not be filtered
     *   or expanded, but they can be used to resolve references in the test method annotations.
     */
    @CanIgnoreReturnValue
    fun getParameterizedAnnotations(
        methodAnnotations: Array<Annotation>,
        classAnnotations: List<Annotation>,
    ): MutableSet<Annotation> {
        val parameterizedAnnotations: MutableSet<Annotation> = HashSet()
        val annotations: List<Annotation> = methodAnnotations.toList()

        for (annotation in annotations) {
            val replacements =
                maybeReplaceUsingParameterizedTestGenerator(annotation, classAnnotations)
            replacements?.let { parameterizedAnnotations.addAll(it) }

            if (isParameterizedAnnotation(annotation)) {
                parameterizedAnnotations.add(annotation)
            }
        }

        return parameterizedAnnotations
    }
}
