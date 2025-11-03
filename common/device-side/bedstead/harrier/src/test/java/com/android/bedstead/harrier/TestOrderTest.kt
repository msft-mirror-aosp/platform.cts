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

import com.android.bedstead.harrier.annotations.AfterClass
import com.android.bedstead.harrier.annotations.BeforeClass
import com.android.bedstead.harrier.annotations.TestOrder
import com.google.common.truth.Truth
import org.junit.Assert
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.JUnitCore
import org.junit.runner.RunWith

@RunWith(BedsteadJUnit4::class)
class TestOrderTest {
    @Test
    fun testOrder_initiallyEmpty_defaultOrder_shouldStayEmpty() {
        Truth.assertThat(sInitiallyEmptyTestSubject).isEmpty()
    }

    @TestOrder(order = -5)
    @Test
    fun testOrder_initiallyEmpty_manualOrder_shouldStayEmpty() {
        Truth.assertThat(sInitiallyEmptyTestSubject).isEmpty()
    }

    @TestOrder(order = TestOrder.FIRST)
    @Test
    fun testOrder_initiallyEmpty_fillWithValues_shouldApply() {
        sInitiallyEmptyTestSubject.addAll(listOf<String?>(SAMPLE_TEXT_1, SAMPLE_TEXT_2))
    }

    @TestOrder(order = TestOrder.EARLY)
    @Test
    fun testOrder_initiallyEmpty_containsValues() {
        Truth.assertThat(sInitiallyEmptyTestSubject).containsExactly(
            SAMPLE_TEXT_1,
            SAMPLE_TEXT_2,
        )
    }

    @TestOrder(order = TestOrder.MIDDLE)
    @Test
    fun testOrder_initiallyHas2_containsValues() {
        Truth.assertThat(sInitiallyNotEmptyTestSubject)
            .containsExactly(SAMPLE_TEXT_1, SAMPLE_TEXT_2)
    }

    @TestOrder(order = TestOrder.LATE)
    @Test
    fun testOrder_initiallyHas2_shouldApply() {
        sInitiallyNotEmptyTestSubject.add(SAMPLE_TEXT_3)
    }

    @TestOrder(order = TestOrder.LAST)
    @Test
    fun testOrder_initiallyHas2_lowestPriority_containsMoreValues() {
        Truth.assertThat(sInitiallyNotEmptyTestSubject).containsExactly(
            SAMPLE_TEXT_1,
            SAMPLE_TEXT_2,
            SAMPLE_TEXT_3,
        )
    }

    /**
     * Remove `@Ignore` along with test subject in [TestOrderInvalidOrderTestingSample]
     * to tes restricted value exception
     */
    @Ignore("Excluded from normal runs - used only in internal runner validation")
    @Test
    fun testOrder_invalidOrderValue_throwsException() {
        // given
        val expectedExceptionMessage: String? = String.format(
            "Value %s restricted for use with TestOrder annotation",
            Int.Companion.MIN_VALUE,
        )

        // when
        val underTest = JUnitCore.runClasses(TestOrderInvalidOrderTestingSample::class.java)

        // then
        Assert.assertEquals(1, underTest.runCount.toLong())
        Assert.assertEquals(1, underTest.failureCount.toLong())

        val exceptionThrown: Throwable? = underTest.failures.first().exception
        Assert.assertTrue(exceptionThrown is IllegalArgumentException)
        Assert.assertEquals(expectedExceptionMessage, exceptionThrown!!.message)
    }

    companion object {
        const val SAMPLE_TEXT_1: String = "sampleText1"
        const val SAMPLE_TEXT_2: String = "sampleText2"
        const val SAMPLE_TEXT_3: String = "sampleText3"

        val sInitiallyEmptyTestSubject: MutableSet<String?> = HashSet<String?>()

        val sInitiallyNotEmptyTestSubject: MutableSet<String?> = HashSet<String?>()

        @BeforeClass
        @JvmStatic
        fun setUp() {
            sInitiallyNotEmptyTestSubject.addAll(
                listOf<String?>(
                    SAMPLE_TEXT_1,
                    SAMPLE_TEXT_2,
                ),
            )
        }

        @AfterClass
        @JvmStatic
        fun tearDown() {
            sInitiallyEmptyTestSubject.clear()
            sInitiallyNotEmptyTestSubject.clear()
        }
    }
}
