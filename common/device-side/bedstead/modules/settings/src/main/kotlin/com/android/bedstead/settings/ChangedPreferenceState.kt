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

/**
 * Data class caching state of the changed preference over the course of the Bedstead test to
 * restore its state when the test is finished.
 * @param metadata metadata of the cached preference
 * @param originalPreferenceValue value of the preference cached right before the first setting of
 * the new value; this value will be restored after the test
 * @param currentPreferenceValue value of the preference that is currently set
 */
data class ChangedPreferenceState(
    val metadata: SettingsPreferenceMetadata,
    val originalPreferenceValue: SettingsPreferenceValue?,
    val currentPreferenceValue: SettingsPreferenceValue?
)
