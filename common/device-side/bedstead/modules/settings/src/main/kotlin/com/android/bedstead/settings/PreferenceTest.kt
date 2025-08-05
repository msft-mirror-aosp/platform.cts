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

import com.android.bedstead.harrier.annotations.UsesAnnotationExecutor

/**
 * Marks tests that need to have a specific settings preference in a particular state before the
 * test itself starts.
 *
 * @param screenKey key associated with settings screen; see [android.service.settings.preferences.SetValueRequest.getScreenKey]
 * @param preferenceKey key associated with a preference within that settings screen; see [android.service.settings.preferences.SetValueRequest.getPreferenceKey]
 * @param valueType type of the value to be set; currently available are:
 * - [android.service.settings.preferences.SettingsPreferenceValue.TYPE_BOOLEAN]
 * - [android.service.settings.preferences.SettingsPreferenceValue.TYPE_INT]
 * - [android.service.settings.preferences.SettingsPreferenceValue.TYPE_DOUBLE]
 * - [android.service.settings.preferences.SettingsPreferenceValue.TYPE_LONG]
 * - [android.service.settings.preferences.SettingsPreferenceValue.TYPE_STRING]
 * @param valueToSet desired value of the preference; of type String due to annotation limitations, but will be processed based on valueType
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@UsesAnnotationExecutor(UsesAnnotationExecutor.SETTINGS)
annotation class PreferenceTest(
    val screenKey: String,
    val preferenceKey: String,
    val valueType: Int,
    val valueToSet: String
)
