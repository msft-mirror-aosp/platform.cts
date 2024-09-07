/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.bestead.onboarding

import com.android.bedstead.onboarding.*
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.harrier.HarrierRule
import com.android.bedstead.harrier.TestRuleExecutor
import com.android.bedstead.onboarding.annotations.OnboardingTest
import com.android.bedstead.onboarding.extensions.onboarding
import com.android.bedstead.onboarding.extensions.teardownExternalRule
import org.junit.runner.Description

/**
 * An implementation of [TestRuleExecutor] to apply the onboarding test rule.
 */
class OnboardingTestRuleExecutor : TestRuleExecutor {

    override fun applyTestRule(
            deviceState: HarrierRule,
            annotation: Annotation,
            description: Description) {
        when (annotation) {
            is OnboardingTest -> initializeOnboardingTestsRule(deviceState as DeviceState, description)
        }
    }

    override fun teardown(deviceState: HarrierRule) {
        (deviceState as DeviceState).teardownExternalRule()
    }

    private fun initializeOnboardingTestsRule(deviceState: DeviceState, description: Description) {
        deviceState.onboarding().init(description)
    }
}