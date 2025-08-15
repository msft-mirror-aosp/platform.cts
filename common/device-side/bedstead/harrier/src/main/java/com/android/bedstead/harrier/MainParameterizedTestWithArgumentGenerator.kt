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

import com.android.bedstead.harrier.annotations.EnumTestParameter
import com.android.bedstead.harrier.annotations.IntTestParameter
import com.android.bedstead.harrier.annotations.StringTestParameter
import org.junit.runners.model.FrameworkMethod

/**
 * [ParameterizedTestWithArgumentGenerator] for annotations that don't belong to specific modules.
 */
@Suppress("unused")
class MainParameterizedTestWithArgumentGenerator : ParameterizedTestWithArgumentGenerator {

    override fun handleFrameworkMethod(
        frameworkMethod: FrameworkMethod,
        annotation: Annotation
    ): List<FrameworkMethod> {
        return when (annotation) {
            is StringTestParameter -> annotation.logic(frameworkMethod)
            is IntTestParameter -> annotation.logic(frameworkMethod)
            is EnumTestParameter -> annotation.logic(frameworkMethod)
            else -> super.handleFrameworkMethod(frameworkMethod, annotation)
        }
    }

    private fun IntTestParameter.logic(frameworkMethod: FrameworkMethod) = value.map {
        FrameworkMethodWithParameter(frameworkMethod, it)
    }

    private fun StringTestParameter.logic(frameworkMethod: FrameworkMethod) = value.map {
        FrameworkMethodWithParameter(frameworkMethod, it)
    }

    private fun EnumTestParameter.logic(
        frameworkMethod: FrameworkMethod
    ) = value.java.enumConstants.map {
        FrameworkMethodWithParameter(frameworkMethod, it)
    }
}
