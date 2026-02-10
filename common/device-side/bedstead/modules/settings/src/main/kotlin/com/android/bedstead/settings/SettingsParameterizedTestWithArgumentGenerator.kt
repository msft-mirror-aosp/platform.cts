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

import android.service.settings.preferences.GetValueResult
import android.service.settings.preferences.SettingsPreferenceMetadata
import com.android.bedstead.harrier.BedsteadServiceLocator
import com.android.bedstead.harrier.FrameworkMethodWithParameter
import com.android.bedstead.harrier.ParameterizedTestWithArgumentGenerator
import com.android.bedstead.nene.types.OptionalBoolean
import com.android.bedstead.settings.SettingsPreferenceMetadataParameter.Companion.PREFERENCE_FILTER_LAUNCH_INTENT_NOT_NULL
import com.android.bedstead.settings.SettingsPreferenceMetadataParameter.Companion.PREFERENCE_FILTER_READ_PERMISSIONS_NOT_EMPTY
import com.android.bedstead.settings.SettingsPreferenceMetadataParameter.Companion.PREFERENCE_FILTER_WRITE_PERMISSIONS_NOT_EMPTY
import com.android.bedstead.settings.appfunctions.AppFunctionsComponent
import com.android.bedstead.settings.appfunctions.DeviceStateItemsParameter
import com.android.bedstead.settings.appfunctions.PerScreenDeviceStatesParameter
import org.junit.runners.model.FrameworkMethod

/**
 * [ParameterizedTestWithArgumentGenerator] for bedstead-settings
 */
@Suppress("unused")
class SettingsParameterizedTestWithArgumentGenerator(
    locator: BedsteadServiceLocator
) : ParameterizedTestWithArgumentGenerator {

    private val clientComponent: SettingsPreferenceServiceClientComponent by locator
    private val appFunctionsComponent: AppFunctionsComponent by locator

    override fun handleFrameworkMethod(
        frameworkMethod: FrameworkMethod,
        annotation: Annotation
    ): List<FrameworkMethod> {
        return when (annotation) {
            is SettingsPreferenceMetadataParameter -> annotation.logic(frameworkMethod)
            is PerScreenDeviceStatesParameter -> appFunctionsComponent
                .perScreenDeviceStatesParameter(
                    annotation,
                    frameworkMethod
                )
            is DeviceStateItemsParameter -> appFunctionsComponent
                .deviceStateItemsParameter(
                    annotation,
                    frameworkMethod
                )
            else -> super.handleFrameworkMethod(frameworkMethod, annotation)
        }
    }

    private fun SettingsPreferenceMetadataParameter.logic(
        frameworkMethod: FrameworkMethod
    ): List<FrameworkMethod> = clientComponent.allMetadata(packageName)
        .applyKeysFilter(keys)
        .applyWriteSensitivityFilter(writeSensitivity)
        .applyIsWritableFilter(isWritable)
        .applyOtherFilters(otherFilters)
        .applySkipUnsupportedPreferences(skipUnsupportedPreferences)
        .map { metadata ->
            FrameworkMethodWithParameter(frameworkMethod, metadata, metadata.nameWithKeys())
        }

    private fun List<SettingsPreferenceMetadata>.applyKeysFilter(
        keys: Array<String>
    ): List<SettingsPreferenceMetadata> = if (keys.isEmpty()) {
        this
    } else {
        filter { keys.contains(it.toString()) }
    }

    private fun List<SettingsPreferenceMetadata>.applyWriteSensitivityFilter(
        writeSensitivity: IntArray
    ): List<SettingsPreferenceMetadata> {
        if (writeSensitivity.isNotEmpty()) {
            return filter {
                it.writeSensitivity in writeSensitivity
            }
        }
        return this
    }

    private fun List<SettingsPreferenceMetadata>.applyIsWritableFilter(
        isWritable: OptionalBoolean
    ): List<SettingsPreferenceMetadata> {
        return if (isWritable == OptionalBoolean.ANY) {
            this
        } else {
            filter {
                it.isWritable == isWritable.toBoolean()
            }
        }
    }

    private fun List<SettingsPreferenceMetadata>.applyOtherFilters(
        otherFilters: IntArray
    ): List<SettingsPreferenceMetadata> {
        var filteredMetadata = this
        otherFilters.forEach { filter ->
            filteredMetadata = when (filter) {
                PREFERENCE_FILTER_READ_PERMISSIONS_NOT_EMPTY -> filteredMetadata.filter {
                    it.readPermissions.isNotEmpty()
                }

                PREFERENCE_FILTER_WRITE_PERMISSIONS_NOT_EMPTY -> filteredMetadata.filter {
                    it.writePermissions.isNotEmpty()
                }

                PREFERENCE_FILTER_LAUNCH_INTENT_NOT_NULL -> filteredMetadata.filter {
                    it.launchIntent != null
                }

                else -> {
                    throw IllegalStateException("$filter is not a supported preference filter")
                }
            }
        }
        return filteredMetadata
    }

    private fun List<SettingsPreferenceMetadata>.applySkipUnsupportedPreferences(
        skipUnsupportedPreferences: Boolean
    ): List<SettingsPreferenceMetadata> {
        return if (skipUnsupportedPreferences) {
            filter {
                clientComponent
                    .getRepository(SETTINGS_PACKAGE_NAME)
                    .getValueResult(it).resultCode != GetValueResult.RESULT_UNSUPPORTED
            }
        } else {
            this
        }
    }
}

/**
 * A pretty name of [SettingsPreferenceMetadata], the main use-case is the test parameter name
 */
fun SettingsPreferenceMetadata.nameWithKeys() = "$screenKey/$key"
