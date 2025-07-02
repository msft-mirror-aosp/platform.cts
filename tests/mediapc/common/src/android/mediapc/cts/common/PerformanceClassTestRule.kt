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
package android.mediapc.cts.common

import org.junit.Assert.fail
import org.junit.AssumptionViolatedException
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * TestRule for running Media Performance Class tests.
 *
 * Tests, including setup and teardown, are only executed if the precondition is met.
 *
 * If preconditions are met a [PerformanceClassEvaluator] is created with the results checked and
 * submitted.
 *
 * If a Media Performance Class is not declared then any failure or thrown exception is changed to
 * an [AssumptionViolatedException].
 */
class PerformanceClassTestRule private constructor(
    private val precondition: Precondition,
    private val hasDeclaredPC: Boolean = Utils.isPerfClass(),
    private val declaredPC: Int = Utils.getPerfClass()
) : TestRule {
    /**
     * The [PerformanceClassEvaluator] for this test.
     *
     * This is only available during test execution if the preconditions are met.
     */
     lateinit var performanceClassEvaluator: PerformanceClassEvaluator

    override fun apply(base: Statement, description: Description): Statement {
        if (!precondition.meetsPrecondition) {
            return object : Statement() {
                override fun evaluate() = evaluateDoesMeet()
            }
        }
        performanceClassEvaluator = PerformanceClassEvaluator(description.methodName)
        return object : Statement() {
            @Throws(Throwable::class)
            override fun evaluate() = evaluateMeets(base)
        }
    }

    private fun evaluateDoesMeet() {
        if (hasDeclaredPC &&
            precondition.failurePerformanceClassLevel < declaredPC
        ) {
            fail(
                "Media Performance Class  >= ${precondition.minPerformanceClassLevel} " +
                        "precondition failed: ${precondition.failureMessage}"
            )
        }
        throw AssumptionViolatedException(
            "Skipping the test, failed to meet precondition: ${precondition.failureMessage}"
        )
    }

    private fun evaluateMeets(base: Statement) {
        try {
            base.evaluate()
            performanceClassEvaluator.submitAndCheck()
        } catch (e: Exception) {
            if (hasDeclaredPC) {
                throw e
            } else if (e is AssumptionViolatedException) {
                throw e
            } else {
                throw AssumptionViolatedException(
                    "Ignoring failure because Media Performance Class is not declared",
                    e
                )
            }
        } finally {
            if (hasDeclaredPC) {
                // if there is a requirement left something went wrong before submitting
                // So submit and verify to upload and log best we can with out new
                // exceptions.
                performanceClassEvaluator.submitAndVerify()
            }
        }
    }

    companion object {
        /** Creates a [PerformanceClassTestRule] with the [BASELINE] precondition. */
        @JvmStatic
        fun withBaselinePreconditions(): PerformanceClassTestRule {
            return with(BASELINE)
        }

        /** Creates a [PerformanceClassTestRule] with no precondition. */
        @JvmStatic
        fun withNoPreconditions(): PerformanceClassTestRule {
            return with(EMPTY)
        }

        /** Creates a [PerformanceClassTestRule] with the given precondition. */
        @JvmStatic
        fun with(vararg preconditions: Precondition) =
            PerformanceClassTestRule(Precondition.inOrder(*preconditions))
    }
}
