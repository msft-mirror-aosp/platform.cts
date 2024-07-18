/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.bedstead.harrier.TestRuleExecutor

/**
 * Annotation to apply to an annotation outside of Harrier to indicate it should be processed
 * with a particular [TestRuleExecutor].
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class UsesTestRuleExecutor(val value: String) {
    companion object {
        const val ONBOARDING = "com.android.bedstead.onboarding.OnboardingTestRuleExecutor"
    }
}

/**
 * Create class from the fully qualified name in [UsesTestRuleExecutor.value] parameter
 */
fun UsesTestRuleExecutor.getTestRuleExecutorClass(): Class<out TestRuleExecutor?> {
    if (value.isEmpty()) {
        throw IllegalStateException("@UsesTestRuleExecutor value is empty")
    } else {
        try {
            @Suppress("UNCHECKED_CAST")
            return Class.forName(value) as Class<out TestRuleExecutor?>
        } catch (ignored: ClassNotFoundException) {
            throw IllegalStateException(
                    "Could not find annotation executor $value. Probably a dependency issue."
            )
        }
    }
}
