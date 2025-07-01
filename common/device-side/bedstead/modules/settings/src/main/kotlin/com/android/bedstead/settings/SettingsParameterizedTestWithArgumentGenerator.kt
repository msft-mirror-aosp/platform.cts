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
package com.android.bedstead.settings

import android.service.settings.preferences.SettingsPreferenceMetadata
import com.android.bedstead.harrier.BedsteadServiceLocator
import com.android.bedstead.harrier.FrameworkMethodWithParameter
import com.android.bedstead.harrier.ParameterizedTestWithArgumentGenerator
import org.junit.runners.model.FrameworkMethod

/**
 * [ParameterizedTestWithArgumentGenerator] for bedstead-settings
 */
@Suppress("unused")
class SettingsParameterizedTestWithArgumentGenerator(
    locator: BedsteadServiceLocator
) : ParameterizedTestWithArgumentGenerator {

    private val clientComponent: SettingsPreferenceServiceClientComponent by locator

    override fun handleFrameworkMethod(
        frameworkMethod: FrameworkMethod,
        annotation: Annotation
    ): List<FrameworkMethod> {
        if (annotation is SettingsPreferenceMetadataParameter) {
            return handleSettingsPreferenceMetadataParameter(frameworkMethod)
        } else {
            throw IllegalStateException(
                "annotation $annotation isn't handled by " +
                        "SettingsParameterizedTestWithArgumentGenerator"
            )
        }
    }

    private fun handleSettingsPreferenceMetadataParameter(
        frameworkMethod: FrameworkMethod,
    ): List<FrameworkMethod> {
        return clientComponent.allMetadata.map {
            FrameworkMethodWithParameter(frameworkMethod, it, it.nameWithKeys())
        }
    }
}

/**
 * A pretty name of [SettingsPreferenceMetadata], the main use-case is the test parameter name
 */
fun SettingsPreferenceMetadata.nameWithKeys() = "$screenKey/$key"
