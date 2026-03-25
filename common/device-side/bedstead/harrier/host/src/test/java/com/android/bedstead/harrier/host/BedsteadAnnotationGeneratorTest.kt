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
package com.android.bedstead.harrier.host

import com.android.bedstead.harrier.BedsteadAnnotationGenerator
import com.android.bedstead.harrier.BedsteadFrameworkMethod
import com.android.bedstead.harrier.BedsteadServiceLocator
import com.android.bedstead.harrier.DynamicParameterizedAnnotation
import com.android.bedstead.harrier.ParameterizedTestGenerator
import com.android.bedstead.harrier.annotations.ParameterizedAnnotationScope
import com.android.bedstead.harrier.annotations.RequireRunOnInitialUser
import com.android.bedstead.harrier.annotations.UsesParameterizedTestGenerator
import com.android.bedstead.harrier.annotations.meta.ParameterizedAnnotation
import com.android.bedstead.harrier.annotations.parameterized.IncludeNone
import com.android.bedstead.multiuser.annotations.RequireRunOnPrimaryUser
import com.android.bedstead.nene.types.OptionalBoolean
import com.google.auto.value.AutoAnnotation
import com.google.common.collect.ImmutableList
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.runners.model.FrameworkMethod

class FakeParameterizedTestGenerator(locator: BedsteadServiceLocator) : ParameterizedTestGenerator {
    companion object {
        var replacements: List<Annotation> = emptyList()
    }

    override fun generateReplacementAnnotations(
        annotation: Annotation,
        classAnnotations: List<Annotation>,
    ): List<Annotation> {
        return replacements
    }
}

@RunWith(JUnit4::class)
class BedsteadAnnotationGeneratorTest {
    @Retention(AnnotationRetention.RUNTIME)
    @Target(AnnotationTarget.FUNCTION)
    @UsesParameterizedTestGenerator(
        value = "com.android.bedstead.harrier.host.FakeParameterizedTestGenerator"
    )
    internal annotation class FakeParameterizationAnnotation()

    @Retention(AnnotationRetention.RUNTIME)
    @Target(AnnotationTarget.FUNCTION)
    @ParameterizedAnnotation(scope = ParameterizedAnnotationScope.ENTERPRISE)
    internal annotation class FakeEnterpriseAnnotation1()

    @Retention(AnnotationRetention.RUNTIME)
    @Target(AnnotationTarget.FUNCTION)
    @ParameterizedAnnotation(scope = ParameterizedAnnotationScope.ENTERPRISE)
    internal annotation class FakeEnterpriseAnnotation2()

    @Retention(AnnotationRetention.RUNTIME)
    @Target(AnnotationTarget.FUNCTION, AnnotationTarget.ANNOTATION_CLASS)
    internal annotation class ChildAnnotation()

    @Retention(AnnotationRetention.RUNTIME)
    @Target(AnnotationTarget.FUNCTION)
    @ChildAnnotation
    internal annotation class ParentAnnotation()

    internal class TestMethods {
        fun noAnnotation() {}

        @FakeParameterizationAnnotation fun parameterizedAnnotation() {}

        @ChildAnnotation fun childAnnotation() {}

        @ParentAnnotation fun parentAnnotation() {}
    }

    @Before
    fun setUp() {
        FakeParameterizedTestGenerator.replacements = emptyList()
    }

    @Test
    fun testResolveRecursiveAnnotations_emptyList() {
        val annotationGenerator = createBedsteadAnnotationGenerator()
        val result = annotationGenerator.resolveRecursiveAnnotations(listOf())

        assertThat(result).isEmpty()
    }

    @Test
    fun testResolveRecursiveAnnotations_childAnnotation() {
        val annotationGenerator = createBedsteadAnnotationGenerator()
        val result = annotationGenerator.resolveRecursiveAnnotations(listOf(ChildAnnotation()))

        assertThat(result).containsExactly(ChildAnnotation())
    }

    @Test
    fun testResolveRecursiveAnnotations_parentAnnotation() {
        val annotationGenerator = createBedsteadAnnotationGenerator()
        val result = annotationGenerator.resolveRecursiveAnnotations(listOf(ParentAnnotation()))

        assertThat(result).containsExactly(ParentAnnotation(), ChildAnnotation())
    }

    @Test
    fun testComputeTestMethods_noAnnotations() {
        val annotationGenerator = createBedsteadAnnotationGenerator()
        val result =
            annotationGenerator.computeTestMethodsForBasicTest(
                getFrameworkMethod("noAnnotation"),
                runtimeClassAnnotations = emptyList(),
            )

        assertResultContainsExactly(
            result,
            createBedsteadFrameworkMethod(
                methodName = "noAnnotation",
                annotations = listOf(),
            ),
        )
    }

    @Test
    fun testComputeTestMethods_parameterizedAnnotationEmpty() {
        FakeParameterizedTestGenerator.replacements = emptyList()
        val annotationGenerator = createBedsteadAnnotationGenerator()
        val result =
            annotationGenerator.computeTestMethodsForBasicTest(
                getFrameworkMethod("parameterizedAnnotation"),
                runtimeClassAnnotations = emptyList(),
            )

        assertResultContainsExactly(result,
                createBedsteadFrameworkMethod(
                    methodName = "parameterizedAnnotation",
                    annotations = emptyList(),
                )
            )
    }

    @Test
    fun testComputeTestMethods_parameterizedAnnotationIncludeNone() {
        FakeParameterizedTestGenerator.replacements = listOf(IncludeNone())
        val annotationGenerator = createBedsteadAnnotationGenerator()
        val result =
            annotationGenerator.computeTestMethodsForBasicTest(
                getFrameworkMethod("parameterizedAnnotation"),
                runtimeClassAnnotations = emptyList(),
            )

        assertThat(result).isEmpty()
    }

    @Test
    fun testComputeTestMethods_parameterizedAnnotationEnterpriseAnnotation() {
        FakeParameterizedTestGenerator.replacements = listOf(FakeEnterpriseAnnotation1())
        val annotationGenerator = createBedsteadAnnotationGenerator()
        val result =
            annotationGenerator.computeTestMethodsForBasicTest(
                getFrameworkMethod("parameterizedAnnotation"),
                runtimeClassAnnotations = emptyList(),
            )

        assertResultContainsExactly(result,
                createBedsteadFrameworkMethod(
                    methodName = "parameterizedAnnotation",
                    annotations = listOf(FakeEnterpriseAnnotation1()),
                    parameterizedAnnotations = listOf(FakeEnterpriseAnnotation1()),
                )
            )
    }

    @Test
    fun testComputeTestMethods_parameterizedAnnotationTwoTestCases() {
        FakeParameterizedTestGenerator.replacements =
            listOf(FakeEnterpriseAnnotation1(), FakeEnterpriseAnnotation2())
        val annotationGenerator = createBedsteadAnnotationGenerator()
        val result =
            annotationGenerator.computeTestMethodsForBasicTest(
                getFrameworkMethod("parameterizedAnnotation"),
                runtimeClassAnnotations = emptyList(),
            )

        assertResultContainsExactly(result,
                createBedsteadFrameworkMethod(
                    methodName = "parameterizedAnnotation",
                    annotations = listOf(FakeEnterpriseAnnotation1()),
                    parameterizedAnnotations = listOf(FakeEnterpriseAnnotation1()),
                ),
                createBedsteadFrameworkMethod(
                    methodName = "parameterizedAnnotation",
                    annotations = listOf(FakeEnterpriseAnnotation2()),
                    parameterizedAnnotations = listOf(FakeEnterpriseAnnotation2()),
                ),
            )
    }

    @Test
    fun testComputeTestMethods_parameterizedAnnotationDynamicParameterizedAnnotation() {
        val dynamic1 = DynamicParameterizedAnnotation("one", arrayOf(FakeEnterpriseAnnotation1()))
        val dynamic2 = DynamicParameterizedAnnotation("two", arrayOf(FakeEnterpriseAnnotation2()))
        FakeParameterizedTestGenerator.replacements = listOf(dynamic1, dynamic2)

        val annotationGenerator = createBedsteadAnnotationGenerator()
        val result =
            annotationGenerator.computeTestMethodsForBasicTest(
                getFrameworkMethod("parameterizedAnnotation"),
                runtimeClassAnnotations = emptyList(),
            )

        assertResultContainsExactly(result,
                createBedsteadFrameworkMethod(
                    methodName = "parameterizedAnnotation",
                    annotations = listOf(),
                    parameterizedAnnotations = listOf(dynamic1),
                ),
                createBedsteadFrameworkMethod(
                    methodName = "parameterizedAnnotation",
                    annotations = listOf(),
                    parameterizedAnnotations = listOf(dynamic2),
                ),
            )
    }

    /**
     * Helper method to compare the expected lists of [FrameworkMethod]s.
     *
     * This is required, because BedsteadFrameworkMethod.equals doesn't look at the annotations.
     */
    private fun assertResultContainsExactly(
        actual: List<FrameworkMethod>,
        vararg expected: FrameworkMethod,
    ) {
        assertThat(actual).containsExactly(*expected)

        actual.zip(expected, this::assertAnnotationsAreEqual)
    }

    /**
     * Helper method to compare the expected annotations.
     *
     * We add the following annotations here:
     * 1. kotlin.metadata annotation that is auto-added to the declaring class
     * 2. [RequireRunOnInitialUser] that is added by [BedsteadAnnotationGenerator]
     * 3. [RequireRunOnPrimaryUser] that is a recursive annotation on [RequireRunOnInitialUser]
     * logic.
     */
    private fun assertAnnotationsAreEqual(actual: FrameworkMethod, expected: FrameworkMethod) {
        val fullExpectedAnnotations =
            expected.annotations.toList() +
                RequireRunOnInitialUser(switchedToUser = OptionalBoolean.ANY) +
                RequireRunOnPrimaryUser(switchedToUser = OptionalBoolean.ANY) +
                expected.method.declaringClass.annotations.toList()

        assertWithMessage("Annotations don't match for method: " + expected)
            .that(actual.annotations.toList())
            .containsExactlyElementsIn(fullExpectedAnnotations)
    }

    private fun createBedsteadAnnotationGenerator(
        isHeadlessSystemUserMode: Boolean = false
    ): BedsteadAnnotationGenerator {
        return BedsteadAnnotationGenerator(isHeadlessSystemUserMode)
    }

    private fun createBedsteadFrameworkMethod(
        methodName: String,
        annotations: List<Annotation>,
        parameterizedAnnotations: List<Annotation> = emptyList(),
    ): BedsteadFrameworkMethod {
        return BedsteadFrameworkMethod(
            getFrameworkMethod(methodName).method,
            ImmutableList.copyOf(annotations),
            ImmutableList.copyOf(parameterizedAnnotations),
        )
    }

    /** Helper to get a FrameworkMethod from the TestMethods class by name. */
    private fun getFrameworkMethod(name: String): FrameworkMethod {
        try {
            return FrameworkMethod(TestMethods::class.java.getMethod(name))
        } catch (e: NoSuchMethodException) {
            throw RuntimeException("Could not find helper method: $name", e)
        }
    }
}
