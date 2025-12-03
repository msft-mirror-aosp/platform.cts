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
package com.android.bedstead.harrier.host

import com.android.bedstead.harrier.DynamicParameterizedAnnotation
import com.android.bedstead.harrier.annotations.AnnotationPriorityRunPrecedence
import com.android.bedstead.harrier.sortedByPriority
import com.android.bedstead.nene.exceptions.NeneException
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class AnnotationSorterTest {

    // region Test Annotations
    @Retention(AnnotationRetention.RUNTIME)
    internal annotation class FirstPriority(val priority: Int = AnnotationPriorityRunPrecedence.FIRST)

    @Retention(AnnotationRetention.RUNTIME)
    internal annotation class MiddlePriority(val priority: Int = AnnotationPriorityRunPrecedence.MIDDLE)

    @Retention(AnnotationRetention.RUNTIME)
    internal annotation class LatePriority(val priority: Int = AnnotationPriorityRunPrecedence.LATE)

    @Retention(AnnotationRetention.RUNTIME)
    internal annotation class NoPriority // No priority() method

    @Retention(AnnotationRetention.RUNTIME)
    internal annotation class BrokenPriority(val priority: String = "broken") // Wrong return type
    // endregion

    // region Test Helper Classes
    /**
     * A helper class just to hold annotation instances for easy reflective access.
     */
    internal class AnnotationHolder {
        @FirstPriority
        fun first() {
        }

        @MiddlePriority
        fun middle() {
        }

        @LatePriority
        fun late() {
        }

        @NoPriority
        fun no() {
        }

        @BrokenPriority
        fun broken() {
        }
    }
    // endregion

    // region Helper Methods
    /**
     * Helper to get an actual annotation instance from our holder class.
     */
    private fun getAnnotation(methodName: String): Annotation {
        return AnnotationHolder::class.java.getMethod(methodName).annotations.first()
    }

    /**
     * Helper to assert the final order of annotations.
     */
    private fun assertOrder(sorted: List<Annotation>, vararg expected: Annotation) {
        Assert.assertEquals("The sorted order is incorrect.", expected.toList(), sorted)
    }
    //endregion

    @Test
    fun testSort_reflectivePriority_sortsCorrectly() {
        val first = getAnnotation("first")
        val middle = getAnnotation("middle")
        val late = getAnnotation("late")

        // Add in jumbled order
        val annotations = mutableListOf(middle, late, first)
        val sortedAnnotations = annotations.sortedByPriority()

        // Assert they are sorted by priority number, ascending
        assertOrder(sortedAnnotations, first, middle, late)
    }

    @Test
    fun testSort_noPriorityMethod_defaultsToNotImportant() {
        val first = getAnnotation("first") // Priority 0
        val noPriority = getAnnotation("no") // Defaults to PRECEDENCE_NOT_IMPORTANT (20000)

        val annotations = mutableListOf(noPriority, first)
        val sortedAnnotations = annotations.sortedByPriority()

        // PRECEDENCE_NOT_IMPORTANT (20000) comes after FIRST (0)
        assertOrder(sortedAnnotations, first, noPriority)
    }

    @Test
    fun testSort_withDynamicParameterizedAnnotation_sortsCorrectly() {
        val first = getAnnotation("first") // Priority 0

        // Use the real DynamicParameterizedAnnotation class
        val emptyAnnotations = arrayOf<Annotation>()
        val dynamicEarly = DynamicParameterizedAnnotation(
            "early", emptyAnnotations, AnnotationPriorityRunPrecedence.EARLY // 5000
        )
        val dynamicLate = DynamicParameterizedAnnotation(
            "late", emptyAnnotations, AnnotationPriorityRunPrecedence.LATE // 15000
        )

        val annotations = mutableListOf(dynamicLate, first, dynamicEarly)
        val sortedAnnotations = annotations.sortedByPriority()

        assertOrder(sortedAnnotations, first, dynamicEarly, dynamicLate)
    }

    @Test(expected = NeneException::class)
    fun testSort_brokenPriorityMethod_throwsNeneException() {
        val broken = getAnnotation("broken") // priority() returns a String, not Int
        val annotations = mutableListOf(broken, broken)

        // This should throw a NeneException due to InvocationTargetException -> ClassCastException
        annotations.sortedByPriority()

        Assert.fail("NeneException was not thrown as expected.")
    }

    @Test
    fun testSort_emptyList_doesNotThrow() {
        val annotations = mutableListOf<Annotation>()
        val sorted = annotations.sortedByPriority()
        Assert.assertTrue("Sorted list should be empty", sorted.isEmpty())
    }

    @Test
    fun testSort_withDuplicateInstances_sortsCorrectly() {
        val first = getAnnotation("first")
        val late = getAnnotation("late")

        // Add duplicates of the *same instances*
        val annotations = mutableListOf(late, first, late, first)
        val sortedAnnotations = annotations.sortedByPriority()

        // The sort should be stable and correct, hitting the cache for duplicates
        assertOrder(sortedAnnotations, first, first, late, late)
    }

    @Test
    fun testSort_mixedReflectiveAndDynamic_sortsCorrectly() {
        val first = getAnnotation("first") // 0
        val middle = getAnnotation("middle") // 10000
        val noPriority = getAnnotation("no") // 20000

        val emptyAnnotations = arrayOf<Annotation>()
        val dynamicEarly = DynamicParameterizedAnnotation(
            "early", emptyAnnotations, AnnotationPriorityRunPrecedence.EARLY // 5000
        )
        val dynamicLate = DynamicParameterizedAnnotation(
            "late", emptyAnnotations, AnnotationPriorityRunPrecedence.LATE // 15000
        )
        val dynamicLast = DynamicParameterizedAnnotation(
            "last", emptyAnnotations, AnnotationPriorityRunPrecedence.LAST // MAX_VALUE
        )

        val annotations = mutableListOf(
            noPriority,
            middle,
            dynamicLate,
            first,
            dynamicLast,
            dynamicEarly
        )
        val sortedAnnotations = annotations.sortedByPriority()

        assertOrder(
            sortedAnnotations,
            first,
            dynamicEarly,
            middle,
            dynamicLate,
            noPriority,
            dynamicLast
        )
    }
}