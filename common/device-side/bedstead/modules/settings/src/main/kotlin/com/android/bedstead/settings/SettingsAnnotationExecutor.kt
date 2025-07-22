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
import android.service.settings.preferences.SettingsPreferenceValue
import com.android.bedstead.harrier.AnnotationExecutor
import com.android.bedstead.harrier.AnnotationExecutorUtil
import com.android.bedstead.harrier.BedsteadServiceLocator
import com.android.bedstead.harrier.annotations.FailureMode

@Suppress("unused")
class SettingsAnnotationExecutor(bedsteadServiceLocator: BedsteadServiceLocator) : AnnotationExecutor {

    private val settingsPreferenceServiceClientComponent: SettingsPreferenceServiceClientComponent by bedsteadServiceLocator

    override fun applyAnnotation(annotation: Annotation) {
        if (annotation is PreferenceTest) {
            val chosenPreferenceMetadata = filterThroughAllMetadata(annotation)
            val preferenceValueToSet = buildPreferenceValue(annotation)

            when (chosenPreferenceMetadata.writeSensitivity) {
                SettingsPreferenceMetadata.NO_SENSITIVITY,
                SettingsPreferenceMetadata.EXPECT_POST_CONFIRMATION -> {
                    settingsPreferenceServiceClientComponent.getRepository(SETTINGS_PACKAGE_NAME).setValueResult(
                        metadata = chosenPreferenceMetadata,
                        settingsPreferenceValue = preferenceValueToSet
                    )
                }
                SettingsPreferenceMetadata.DEEPLINK_ONLY,
                SettingsPreferenceMetadata.NO_DIRECT_ACCESS -> {
                    AnnotationExecutorUtil.failOrSkip(
                        "Preference with this sensitivity" +
                                " cannot be set right now.", FailureMode.SKIP
                    )
                    //TODO: b/431183636 - Wait for the production code to make it at all possible.
                }
                else -> throw IllegalArgumentException("Unexpected type of preference sensitivity encountered")
            }
        }
        else throw IllegalArgumentException("Annotation @${annotation.annotationClass} cannot be " +
                "handled by SettingsAnnotationExecutor")
    }

    private fun filterThroughAllMetadata(annotation: PreferenceTest) : SettingsPreferenceMetadata {
        return settingsPreferenceServiceClientComponent
            .getRepository(SETTINGS_PACKAGE_NAME).allMetadata
            .single { preference ->
                preference.screenKey == annotation.screenKey && preference.key == annotation.preferenceKey
            }
    }

    private fun buildPreferenceValue(annotation: PreferenceTest) : SettingsPreferenceValue {
        val settingsPreferenceValueBuilder = SettingsPreferenceValue.Builder(annotation.valueType)
        return when (annotation.valueType) {
            SettingsPreferenceValue.TYPE_BOOLEAN -> settingsPreferenceValueBuilder
                .setBooleanValue(annotation.valueToSet.toBoolean()).build()
            SettingsPreferenceValue.TYPE_INT -> settingsPreferenceValueBuilder
                .setIntValue(annotation.valueToSet.toInt()).build()
            SettingsPreferenceValue.TYPE_LONG -> settingsPreferenceValueBuilder
                .setLongValue(annotation.valueToSet.toLong()).build()
            SettingsPreferenceValue.TYPE_DOUBLE -> settingsPreferenceValueBuilder
                .setDoubleValue(annotation.valueToSet.toDouble()).build()
            SettingsPreferenceValue.TYPE_STRING -> settingsPreferenceValueBuilder
                .setStringValue(annotation.valueToSet).build()
            else -> throw IllegalArgumentException("Unknown type of SettingPreferenceValue")
        }
    }
}
