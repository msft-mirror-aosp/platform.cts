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
package com.android.bedstead.enterprise

import com.android.bedstead.enterprise.annotations.PolicyArgument
import com.android.bedstead.harrier.ParameterizedTestWithArgumentGenerator
import org.junit.runners.model.FrameworkMethod

/**
 * [ParameterizedTestWithArgumentGenerator] for bedstead-enterprise.
 */
@Suppress("unused")
class EnterpriseParameterizedTestWithArgumentGenerator : ParameterizedTestWithArgumentGenerator {

    override fun handleFrameworkMethod(
        frameworkMethod: FrameworkMethod,
        annotation: Annotation
    ): List<FrameworkMethod> {
        return if (annotation is PolicyArgument) {
            PolicyArgumentTestsGenerator.generate(frameworkMethod)
        } else {
            super.handleFrameworkMethod(frameworkMethod, annotation)
        }
    }
}
