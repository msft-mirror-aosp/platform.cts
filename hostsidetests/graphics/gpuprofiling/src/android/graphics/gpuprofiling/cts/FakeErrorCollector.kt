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

package android.graphics.gpuprofiling.cts

import org.hamcrest.Matcher
import org.junit.rules.ErrorCollector

class FakeErrorCollector : ErrorCollector() {
    private val errors = mutableListOf<Throwable>()

    override fun addError(error: Throwable?) {
        if (error != null) {
            errors.add(error)
        }
    }

    override fun <T> checkThat(reason: String?, value: T, matcher: Matcher<T>) {
        try {
            super.checkThat(reason, value, matcher)
        } catch (e: Throwable) {
        }
    }

    override fun <T> checkThat(value: T, matcher: Matcher<T>) {
        checkThat("", value, matcher)
    }

    public override fun verify() {
        if (errors.isNotEmpty()) {
            throw AssertionError("Collected ${errors.size} errors:\n" + errors.joinToString("\n"))
        }
    }

    fun assertSuccess() {
        if (errors.isNotEmpty()) {
            throw AssertionError("Expected success but found errors: $errors")
        }
    }

    fun assertFailure() {
        if (errors.isEmpty()) {
            throw AssertionError("Expected failure but succeeded")
        }
    }

    fun getErrors(): List<Throwable> {
        return errors.toList()
    }

    fun getErrorMessages(): List<String> {
        return errors.mapNotNull { it.message?.substringBefore('\n') }
    }
}
