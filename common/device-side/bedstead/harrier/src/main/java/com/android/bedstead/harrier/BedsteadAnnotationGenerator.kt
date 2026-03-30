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

import com.android.bedstead.harrier.annotations.RequireRunOnInitialUser
import com.android.bedstead.harrier.annotations.UsesParameterizedTestGenerator
import com.android.bedstead.harrier.annotations.meta.ParameterizedAnnotation
import com.android.bedstead.harrier.annotations.meta.RepeatingAnnotation
import com.android.bedstead.harrier.annotations.meta.RequireRunOnAnnotation
import com.android.bedstead.harrier.annotations.parameterized.IncludeNone
import com.android.bedstead.multiuser.annotations.EnsureHasSecondaryUser
import com.android.bedstead.multiuser.annotations.RequireRunOnAdditionalUser
import com.android.bedstead.multiuser.annotations.RequireRunOnPrimaryUser
import com.android.bedstead.multiuser.annotations.RequireRunOnSecondaryUser
import com.android.bedstead.nene.exceptions.NeneException
import com.android.bedstead.nene.types.OptionalBoolean
import com.google.auto.value.AutoAnnotation
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import com.google.errorprone.annotations.CanIgnoreReturnValue
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.function.Function
import org.junit.runners.model.FrameworkMethod

/** This class exposes a number of annotation-related helper methods. */
class BedsteadAnnotationGenerator(val isHeadlessSystemUserMode: Boolean) {

    /**
     * Special annotations that get handled at a different level and can therefore be skipped during
     * annotation generation.
     */
    // TODO(b/489627134): Move [UsesAnnotationExecutor] into meta folder
    private val IGNORED_ANNOTATIONS: ImmutableSet<String> =
        ImmutableSet.of("com.android.bedstead.harrier.annotations.UsesAnnotationExecutor")

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

    private val ANNOTATION_REPLACEMENTS:
        ImmutableMap<Class<out Annotation>, Function<Annotation, List<Annotation>>> =
        ImmutableMap.of<Class<out Annotation>, Function<Annotation, List<Annotation>>>(
            RequireRunOnInitialUser::class.java,
            Function { a: Annotation ->
                val requireRunOnInitialUserAnnotation = a as RequireRunOnInitialUser
                return@Function listOf(
                    a,
                    requireRunOnPrimaryUser(requireRunOnInitialUserAnnotation.switchedToUser),
                )
            },
            RequireRunOnAdditionalUser::class.java,
            Function { a: Annotation? ->
                val requireRunOnAdditionalUserAnnotation = a as RequireRunOnAdditionalUser
                return@Function listOf(
                    a,
                    requireRunOnSecondaryUser(requireRunOnAdditionalUserAnnotation.switchedToUser),
                )
            },
        )

    private val ANNOTATION_REPLACEMENTS_HEADLESS:
        ImmutableMap<Class<out Annotation>, Function<Annotation, List<Annotation>>> =
        ImmutableMap.of<Class<out Annotation>, Function<Annotation, List<Annotation>>>(
            RequireRunOnInitialUser::class.java,
            Function { a: Annotation ->
                val requireRunOnInitialUserAnnotation = a as RequireRunOnInitialUser
                return@Function listOf(
                    a,
                    ensureHasSecondaryUser(),
                    requireRunOnSecondaryUser(requireRunOnInitialUserAnnotation.switchedToUser),
                )
            },
            RequireRunOnAdditionalUser::class.java,
            Function { a: Annotation? ->
                val requireRunOnAdditionalUserAnnotation = a as RequireRunOnAdditionalUser
                return@Function listOf(ensureHasSecondaryUser(), a)
            },
        )

    private val mLocator: BedsteadServiceLocator = BedsteadServiceLocator()

    @AutoAnnotation
    private fun requireRunOnSecondaryUser(
        switchedToUser: OptionalBoolean?
    ): RequireRunOnSecondaryUser {
        return AutoAnnotation_BedsteadAnnotationGenerator_requireRunOnSecondaryUser(switchedToUser)
    }

    @AutoAnnotation
    private fun ensureHasSecondaryUser(): EnsureHasSecondaryUser {
        return AutoAnnotation_BedsteadAnnotationGenerator_ensureHasSecondaryUser()
    }

    @AutoAnnotation
    private fun requireRunOnPrimaryUser(switchedToUser: OptionalBoolean?): RequireRunOnPrimaryUser {
        return AutoAnnotation_BedsteadAnnotationGenerator_requireRunOnPrimaryUser(switchedToUser)
    }

    @AutoAnnotation
    private fun requireRunOnInitialUser(switchedToUser: OptionalBoolean?): RequireRunOnInitialUser {
        return AutoAnnotation_BedsteadAnnotationGenerator_requireRunOnInitialUser(switchedToUser)
    }

    /**
     * Returns whether a given annotation is a parameterized annotation.
     *
     * Returns true for @link DynamicParameterizedAnnotation.
     */
    private fun isParameterizedAnnotation(annotation: Annotation): Boolean {
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
    private fun isAnnotationClassParameterizedAnnotation(annotation: Annotation): Boolean {
        if (annotation is DynamicParameterizedAnnotation) {
            return false
        }

        return annotation.annotationClass.java.isAnnotationPresent(
            ParameterizedAnnotation::class.java
        )
    }

    /** Returns whether a given annotation is a repeating annotation. */
    private fun isRepeatingAnnotation(annotation: Annotation): Boolean {
        if (annotation is DynamicParameterizedAnnotation) {
            return false
        }

        return annotation.annotationClass.java.isAnnotationPresent(RepeatingAnnotation::class.java)
    }

    private val INDIRECT_ANNOTATIONS_CACHE = mutableMapOf<String, List<Annotation>>()

    /**
     * Returns relevant indirect annotations of a given annotation.
     *
     * Those are the annotations that are annotating the annotation class. These get filtered via
     * [shouldSkipAnnotation].
     */
    private fun getIndirectAnnotations(annotation: Annotation): List<Annotation> {
        if (annotation is DynamicParameterizedAnnotation) {
            return annotation
                .annotations()
                .filterNot(this::shouldSkipAnnotation)
        }
        return INDIRECT_ANNOTATIONS_CACHE.getOrPut(annotation.annotationClass.java.name) {
            annotation.annotationClass.java
                .getAnnotations()
                .filterNot(this::shouldSkipAnnotation)
        }
    }

    /**
     * Return whether the given annotation should be disregarded by the annotation processing. This
     * is the case for standard java / kotlin annotations.
     */
    private fun shouldSkipAnnotation(annotation: Annotation): Boolean {
        if (annotation is DynamicParameterizedAnnotation) {
            return false
        }

        if (annotation is IncludeNone) {
            return true
        }

        val annotationType = (annotation as java.lang.annotation.Annotation).annotationType()
        val annotationPackage: String = annotationType.`package`.name

        return IGNORED_ANNOTATIONS.contains(annotationType.name) ||
            IGNORED_ANNOTATION_PACKAGES.contains(annotationPackage) ||
            IGNORED_ANNOTATION_PREFIXES.stream().anyMatch { annotationPackage.startsWith(it) }
    }

    private fun createRunOnAnnotationsIfNeeded(annotations: List<Annotation>): List<Annotation> {
        val hasRequireRunOnAnnotation =
            annotations.any {
                (it.annotationClass.java.getDeclaredAnnotation(
                    RequireRunOnAnnotation::class.java
                ) != null)
            }

        return if (hasRequireRunOnAnnotation) {
            listOf()
        } else {
            getReplacementAnnotations(
                requireRunOnInitialUser(OptionalBoolean.ANY),
                ImmutableList.of(),
            )
        }
    }

    /**
     * Replace the given annotation using the related [ParameterizedTestGenerator].
     *
     * To be used before general annotation processing.
     */
    private fun maybeReplaceUsingParameterizedTestGenerator(
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
     * Replace all given annotations using the related [ParameterizedTestGenerator]. Keep those that
     * don't have a generator.
     */
    private fun maybeReplaceUsingParameterizedTestGenerator(
        sourceAnnotations: Array<Annotation>,
        classAnnotations: List<Annotation>,
    ): List<Annotation> {
        return sourceAnnotations
            .flatMap {
                maybeReplaceUsingParameterizedTestGenerator(it, classAnnotations) ?: listOf(it)
            }
            .toList()
            .sortedByPriority()
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
    ): Set<Annotation> {
        val parameterizedAnnotations =
            methodAnnotations
                .flatMap {
                    maybeReplaceUsingParameterizedTestGenerator(it, classAnnotations) ?: listOf()
                }
                .toSet()

        return parameterizedAnnotations + methodAnnotations.filter(this::isParameterizedAnnotation)
    }

    /**
     * Creates a list of annotations for the given [Method] that will be used during test execution.
     *
     * The steps to generate the list are:
     * 1. Extract all class and method-level annotations.
     * 2. Run them through [maybeReplaceUsingParameterizedTestGenerator] to generate bedstead
     *    replacements.
     * 3. Run them all through [resolveRecursiveAnnotations] to resolve recursive annotations.
     * 4. Ensure that at least one [RequireRunOnAnnotation] is present.
     */
    private fun calculateAnnotationsForMethod(
        method: Method,
        runtimeClassAnnotations: List<Annotation>,
        parameterizedAnnotations: ImmutableList<Annotation>,
    ): ImmutableList<Annotation> {
        val localAnnotations =
            maybeReplaceUsingParameterizedTestGenerator(
                method.declaringClass.annotations,
                runtimeClassAnnotations,
            ) +
                maybeReplaceUsingParameterizedTestGenerator(
                    method.annotations,
                    runtimeClassAnnotations,
                )

        val resolvedAnnotations =
            resolveRecursiveAnnotations(localAnnotations, parameterizedAnnotations)

        return ImmutableList.copyOf(
            resolvedAnnotations + createRunOnAnnotationsIfNeeded(resolvedAnnotations)
        )
    }

    /** Construct a [BedsteadFrameworkMethod] for the given [Method] and parameterization. */
    private fun constructBedsteadFrameworkMethod(
        method: FrameworkMethod,
        runtimeClassAnnotations: List<Annotation>,
        parameterizedAnnotations: ImmutableList<Annotation> = ImmutableList.of(),
    ): BedsteadFrameworkMethod {
        return BedsteadFrameworkMethod(
            method.method,
            calculateAnnotationsForMethod(
                method.method,
                runtimeClassAnnotations,
                parameterizedAnnotations,
            ),
            parameterizedAnnotations,
        )
    }

    /**
     * Some annotations have hardcoded replacements as per [ANNOTATION_REPLACEMENTS]. Return these
     * if present.
     */
    private fun getSpecialReplacementFunction(
        annotation: Annotation
    ): Function<Annotation, List<Annotation>>? {
        if (annotation is DynamicParameterizedAnnotation) {
            return null
        }

        return if (isHeadlessSystemUserMode) {
            ANNOTATION_REPLACEMENTS_HEADLESS[annotation.annotationClass.java]
        } else {
            ANNOTATION_REPLACEMENTS[annotation.annotationClass.java]
        }
    }

    private fun getReplacementForRepeatingAnnotation(annotation: Annotation): List<Annotation> {
        try {
            val annotations =
                annotation.annotationClass.java.getMethod("value").invoke(annotation)
                    as Array<Annotation>
            return annotations.asList()
        } catch (e: IllegalAccessException) {
            throw NeneException("Error expanding repeated annotations", e)
        } catch (e: InvocationTargetException) {
            throw NeneException("Error expanding repeated annotations", e)
        } catch (e: NoSuchMethodException) {
            throw NeneException("Error expanding repeated annotations", e)
        }
    }

    /** Recursively expand an annotation by its indirect annotations. */
    private fun getReplacementAnnotations(
        annotation: Annotation,
        parameterizedAnnotations: ImmutableList<Annotation>,
    ): List<Annotation> {
        val specialReplaceFunction = getSpecialReplacementFunction(annotation)
        if (specialReplaceFunction != null) {
            return specialReplaceFunction.apply(annotation)
        }

        if (isRepeatingAnnotation(annotation)) {
            return getReplacementForRepeatingAnnotation(annotation)
        }

        if (
            isParameterizedAnnotation(annotation) && !parameterizedAnnotations.contains(annotation)
        ) {
            return listOf()
        }

        val replacementAnnotations =
            getIndirectAnnotations(annotation)
                .flatMap { getReplacementAnnotations(it, parameterizedAnnotations) }
                .toList()

        return if (annotation is DynamicParameterizedAnnotation) {
            replacementAnnotations
        } else {
            replacementAnnotations + annotation
        }
    }

    /**
     * Resolves annotations recursively.
     *
     * @param parameterizedAnnotations The class of the parameterized annotation to expand, if any
     */
    @JvmOverloads
    fun resolveRecursiveAnnotations(
        annotations: List<Annotation>,
        parameterizedAnnotations: ImmutableList<Annotation> = ImmutableList.of(),
    ): List<Annotation> {
        return annotations
            .flatMap { getReplacementAnnotations(it, parameterizedAnnotations).sortedByPriority() }
            .toList()
    }

    /**
     * Generates a cartesian product of multiple sets of annotations. For example: If the
     * [annotations] param has value [[A1, A2], [A3, A4]] then it will return [[A1, A3], [A1, A4],
     * [A2, A3], [A2, A4]].
     *
     * @return cartesian product of the annotation sets
     */
    private fun Collection<List<Annotation>>.cartesianProduct(): List<List<Annotation>> {
        return if (isEmpty()) {
            emptyList()
        } else {
            fold(listOf(emptyList())) { acc, set ->
                acc.flatMap { combination -> set.map { element -> combination + element } }
            }
        }
    }

    /**
     * Calculate test runs for a given basic test, i.e. the test that is actually written in code in
     * the test class.
     *
     * Those are generated by looking through the parameterized annotations on the test as follows:
     * 1. If no parameterized annotations are present, return a single test run.
     * 2. For every instance of [DynamicParameterizedAnnotation] return one test run.
     * 3. For all remaining parameterized annotations generate one test run per combination (one per
     *    [ParameterizedAnnotationScope])
     */
    fun computeTestMethodsForBasicTest(
        method: FrameworkMethod,
        runtimeClassAnnotations: List<Annotation>,
    ): List<FrameworkMethod> {
        val parameterizedAnnotations =
            getParameterizedAnnotations(method.annotations, runtimeClassAnnotations)

        if (parameterizedAnnotations.isEmpty()) {
            // Unparameterized, just add the original
            return listOf(constructBedsteadFrameworkMethod(method, runtimeClassAnnotations))
        }

        // Create [BedsteadFrameworkMethod] for parameterized annotation of instance
        // [DynamicParameterizedAnnotation].
        val dynamicParameterizedTests =
            parameterizedAnnotations
                .filterNot(this::shouldSkipAnnotation)
                .filterIsInstance<DynamicParameterizedAnnotation>()
                .map { annotation ->
                    constructBedsteadFrameworkMethod(
                        method,
                        runtimeClassAnnotations,
                        ImmutableList.of(annotation),
                    )
                }
                .toList()

        // Group all remaining ParameterizedAnnotations by their scope
        val parameterizedAnnotationsByScope =
            parameterizedAnnotations
                .filterNot(this::shouldSkipAnnotation)
                .filter(this::isAnnotationClassParameterizedAnnotation)
                .groupBy { annotation ->
                    annotation.annotationClass.java
                        .getAnnotation(ParameterizedAnnotation::class.java)
                        .scope
                }

        // For each combination of one-per-scope, add a new test run
        val testsPerScope =
            parameterizedAnnotationsByScope.values
                .cartesianProduct()
                .map {
                    constructBedsteadFrameworkMethod(
                        method,
                        runtimeClassAnnotations,
                        ImmutableList.copyOf(it),
                    )
                }
                .toList()

        return dynamicParameterizedTests + testsPerScope
    }
}
