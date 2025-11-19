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

import com.android.bedstead.harrier.FrameworkMethodSorter
import com.android.bedstead.harrier.annotations.AnnotationCostRunPrecedence
import com.android.bedstead.harrier.annotations.TestOrder
import com.android.bedstead.nene.exceptions.NeneException
import java.lang.IllegalArgumentException
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.runners.model.FrameworkMethod

@RunWith(JUnit4::class)
class FrameworkMethodSorterTest {

    @Test
    fun testTestOrderSort_Simple() {
        val m10 = getFrameworkMethod("methodOrder10")
        val m1 = getFrameworkMethod("methodOrder1")
        val m100 = getFrameworkMethod("methodOrder100")

        val methods = arrayListOf(m10, m1, m100)
        val sorted = FrameworkMethodSorter.sort(methods)

        assertOrder(sorted, m1, m10, m100)
    }

    @Test
    fun testTestOrderSort_WithUnordered() {
        val m10 = getFrameworkMethod("methodOrder10")
        val mUnordered = getFrameworkMethod("methodUnordered")
        val m1 = getFrameworkMethod("methodOrder1")

        val methods = arrayListOf(m10, mUnordered, m1)
        val sorted = FrameworkMethodSorter.sort(methods)

        // Unordered methods have default priority Integer.MIN_VALUE, so they run first.
        assertOrder(sorted, mUnordered, m1, m10)
    }

    @Test
    fun testTestOrderSort_WithConstants() {
        val methodOrderFirst = getFrameworkMethod("methodOrderFirst")
        val methodOrderEarly = getFrameworkMethod("methodOrderEarly")
        val methodOrderMiddle = getFrameworkMethod("methodOrderMiddle")
        val methodOrderLate = getFrameworkMethod("methodOrderLate")
        val methodOrderLast = getFrameworkMethod("methodOrderLast")
        val methods = arrayListOf(
            methodOrderMiddle,
            methodOrderEarly,
            methodOrderLast,
            methodOrderFirst,
            methodOrderLate
        )

        val sorted = FrameworkMethodSorter.sort(methods)

        assertOrder(
            sorted,
            methodOrderFirst,
            methodOrderEarly,
            methodOrderMiddle,
            methodOrderLate,
            methodOrderLast
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun testIllegalTestOrder_ThrowsException() {
        val mIllegal = getFrameworkMethod("methodIllegalOrder")
        val methods = arrayListOf(mIllegal)

        // This should throw IllegalArgumentException
        FrameworkMethodSorter.sort(methods)
    }

    @Test
    fun testBedsteadCostSort_WithinDefaultPriority() {
        val mHigh = getFrameworkMethod("methodHighCost")
        val mLow = getFrameworkMethod("methodLowCost")
        val mNoCost = getFrameworkMethod("methodNoCost") // Defaults to MIDDLE
        val mNone = getFrameworkMethod("methodNoBedsteadAnnotations")

        val methods = arrayListOf(mHigh, mLow, mNoCost, mNone)
        val sorted = FrameworkMethodSorter.sort(methods)

        assertOrder(
            sorted,
            mLow,
            mNoCost, // Defaults to MIDDLE, comes after LOW
            mHigh,
            mNone
        )
    }

    @Test
    fun testBedsteadCostSort_WithMultipleAnnotations() {
        val mLow = getFrameworkMethod("methodLowCost")
        val mLowAndHigh = getFrameworkMethod("methodLowAndHighCost")

        val methods = arrayListOf(mLow, mLowAndHigh)
        val sorted = FrameworkMethodSorter.sort(methods)

        // Both have @LowCost, so the comparator checks the next cost tier.
        // mLowAndHigh has @HighCost, mLow does not.
        // The cost comparator (o1Has && !o2Has) returns -1, so mLowAndHigh comes first.
        assertOrder(sorted, mLowAndHigh, mLow)
    }

    @Test
    fun testFullMixedSort_PriorityAndCost() {
        val mixedAOrder1High = getFrameworkMethod("mixedA_Order1_High")
        val mixedBOrder1Low = getFrameworkMethod("mixedB_Order1_Low")
        val mixedCOrder2Low = getFrameworkMethod("mixedC_Order2_Low")
        val mixedDOrderDefaultHigh = getFrameworkMethod("mixedD_OrderDefault_High")
        val mixedEOrderDefaultNone = getFrameworkMethod("mixedE_OrderDefault_None")

        val methods = arrayListOf(
            mixedAOrder1High,
            mixedBOrder1Low,
            mixedCOrder2Low,
            mixedDOrderDefaultHigh,
            mixedEOrderDefaultNone
        )
        val sorted = FrameworkMethodSorter.sort(methods)

        // Expected Order:
        // 1. Default Priority Group (MIN_VALUE)
        //    - mixedD (@HighCost)
        //    - mixedE (None)
        //    (Cost sort puts annotated 'D' before unannotated 'E')
        // 2. Priority 1 Group
        //    - mixedB (@LowCost)
        //    - mixedA (@HighCost)
        //    (Cost sort puts 'B' before 'A')
        // 3. Priority 2 Group
        //    - mixedC (@LowCost)
        assertOrder(
            sorted,
            mixedDOrderDefaultHigh,
            mixedEOrderDefaultNone,
            mixedBOrder1Low,
            mixedAOrder1High,
            mixedCOrder2Low
        )
    }

    @Test(expected = NeneException::class)
    fun testBrokenCostAnnotation_ThrowsNeneException() {
        val mBroken = getFrameworkMethod("methodBrokenCost")
        val methods = listOf(mBroken)

        // Sorting triggers annotation cost computation
        FrameworkMethodSorter.sort(methods)

        Assert.fail("NeneException was not thrown as expected.")
    }

    @Retention(AnnotationRetention.RUNTIME)
    @Target(AnnotationTarget.FUNCTION)
    internal annotation class LowCost(val cost: Int = AnnotationCostRunPrecedence.LOW)

    @Retention(AnnotationRetention.RUNTIME)
    @Target(AnnotationTarget.FUNCTION)
    internal annotation class MiddleCost(val cost: Int = AnnotationCostRunPrecedence.MIDDLE)

    @Retention(AnnotationRetention.RUNTIME)
    @Target(AnnotationTarget.FUNCTION)
    internal annotation class HighCost(val cost: Int = AnnotationCostRunPrecedence.HIGH)

    @Retention(AnnotationRetention.RUNTIME)
    @Target(AnnotationTarget.FUNCTION)
    internal annotation class NoCost

    @Retention(AnnotationRetention.RUNTIME)
    @Target(AnnotationTarget.FUNCTION)
    internal annotation class BrokenCost(val cost: Double = 1.1)

    /**
     * Helper class containing methods with different annotations
     * for testing the sorter.
     */
    internal class TestMethods {
        // For @TestOrder sorting
        @TestOrder(order = 10)
        fun methodOrder10() {
        }

        @TestOrder(order = 1)
        fun methodOrder1() {
        }

        @TestOrder(order = 100)
        fun methodOrder100() {
        }

        fun methodUnordered() {}

        @TestOrder(order = Int.MIN_VALUE)
        fun methodIllegalOrder() {
        }

        @TestOrder(order = TestOrder.FIRST)
        fun methodOrderFirst() {
        }

        @TestOrder(order = TestOrder.EARLY)
        fun methodOrderEarly() {
        }

        @TestOrder(order = TestOrder.MIDDLE)
        fun methodOrderMiddle() {
        }

        @TestOrder(order = TestOrder.LATE)
        fun methodOrderLate() {
        }

        @TestOrder(order = TestOrder.LAST)
        fun methodOrderLast() {
        }

        // For "Bedstead" (cost) sorting
        @HighCost
        fun methodHighCost() {
        }

        @LowCost
        fun methodLowCost() {
        }

        @MiddleCost
        fun methodMiddleCost() {
        }

        @NoCost // Should default to MIDDLE cost
        fun methodNoCost() {
        }

        fun methodNoBedsteadAnnotations() {}

        @LowCost
        @HighCost
        fun methodLowAndHighCost() {
        }

        // For mixed sorting
        @TestOrder(order = 1)
        @HighCost
        fun mixedA_Order1_High() {
        }

        @TestOrder(order = 1)
        @LowCost
        fun mixedB_Order1_Low() {
        }

        @TestOrder(order = 2)
        @LowCost
        fun mixedC_Order2_Low() {
        }

        @HighCost // Default priority (Integer.MIN_VALUE)
        fun mixedD_OrderDefault_High() {
        }

        // Default priority (Integer.MIN_VALUE)
        fun mixedE_OrderDefault_None() {}

        // For exception testing
        @BrokenCost
        fun methodBrokenCost() {
        }
    }

    /**
     * Helper to get a FrameworkMethod from the TestMethods class by name.
     */
    private fun getFrameworkMethod(name: String): FrameworkMethod {
        try {
            return FrameworkMethod(TestMethods::class.java.getMethod(name))
        } catch (e: NoSuchMethodException) {
            throw RuntimeException("Could not find helper method: $name", e)
        }
    }

    private fun assertOrder(sorted: List<FrameworkMethod>, vararg expected: FrameworkMethod) {
        Assert.assertEquals(
            "The sorted order of methods is incorrect.",
            expected.map { it.name },
            sorted.map { it.name }
        )
    }
}
