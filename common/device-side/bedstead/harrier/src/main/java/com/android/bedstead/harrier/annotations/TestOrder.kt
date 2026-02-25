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

package com.android.bedstead.harrier.annotations

import com.android.bedstead.harrier.annotations.meta.RequiresBedsteadJUnit4

/**
 * Annotation for use to decorate test methods which should be executed in a specific order.
 * The purpose of this Annotation is to make specific ordered tests co-exists with Bedstead
 * annotations execution order within jUnit deterministic unpredictable order.
 *
 * Test methods decorated with this annotation will be executed after all test methods within a
 * test class that are not decorated with this annotation. Bedstead annotations resolving order
 * applies to all tests executed before ordered tests, as well as for ordered methods with tests
 * execution order maintained in a way that test method execution order set by this annotation take
 * precedence over bedstead annotation resolve order.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@RequiresBedsteadJUnit4
annotation class TestOrder(

    /**
     * The order in which test should be run.
     *
     * Order can be set to any [Int] (except [Integer.MIN_VALUE]) or by using
     * predefined helper values available in [TestOrder]
     *
     * Annotations with lower order will have privilege to run before annotations with a higher
     * order value.
     *
     * [Integer.MIN_VALUE] is a restricted value used for instrumentation of precedence for
     * not explicitly ordered tests, thus using it in the annotation will lead to unpredictable
     * tests execution when order might be important.
     */
    val order: Int
) {
    companion object {
        const val FIRST = 0
        const val EARLY = 500
        const val MIDDLE = 1000
        const val LATE = 2000
        const val LAST = Integer.MAX_VALUE
    }
}
