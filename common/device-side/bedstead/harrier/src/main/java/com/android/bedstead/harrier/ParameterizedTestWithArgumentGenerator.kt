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

import org.junit.runners.model.FrameworkMethod

/**
 * Interface used for handling BedsteadJUnit4-compatible annotations responsible for generating new
 * tests with an argument based on its annotation.
 *
 * See SettingsParameterizedTestWithArgumentGenerator for example
 *
 * This is used to add BedsteadJUnit4-compatible annotations without modifying BedsteadJUnit4.
 */
interface ParameterizedTestWithArgumentGenerator {

    /**
     * Generates list of new FrameworkMethods that correspond to the original [frameworkMethod]
     */
    fun handleFrameworkMethod(
        frameworkMethod: FrameworkMethod,
        annotation: Annotation
    ): List<FrameworkMethod> {
        throw IllegalStateException("annotation $annotation isn't handled by ${this.javaClass}")
    }
}
