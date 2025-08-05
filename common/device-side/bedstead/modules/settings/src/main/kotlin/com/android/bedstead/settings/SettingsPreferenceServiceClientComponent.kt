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
import android.service.settings.preferences.SettingsPreferenceServiceClient
import android.service.settings.preferences.SettingsPreferenceValue
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.harrier.DeviceStateComponent

/**
 * Simplifies usage of [SettingsPreferenceServiceClient] from Bedstead tests.
 */
class SettingsPreferenceServiceClientComponent : DeviceStateComponent {

    private val repositories: MutableMap<String, SettingsPreferenceRepository> = mutableMapOf()

    internal fun getRepository(packageName: String): SettingsPreferenceRepository {
        return repositories[packageName] ?: SettingsPreferenceRepository(packageName).also {
            repositories[packageName] = it
        }
    }

    internal fun allMetadata(packageName: String): List<SettingsPreferenceMetadata> {
        return getRepository(packageName).allMetadata
    }

    override fun teardownShareableState() {
        repositories.values.forEach {
            it.closeClient()
        }
        repositories.clear()
    }

    override fun teardownNonShareableState() {
        repositories.values.forEach { repository ->
            repository.restorePreferencesToOldValuesAndClear()
        }
    }
}

/**
 * Get a SettingsPreferenceRepository for the [packageName]
 */
fun DeviceState.getSettingsPreferenceRepository(
    packageName: String = SETTINGS_PACKAGE_NAME
): SettingsPreferenceRepository {
    return getDependency(
        SettingsPreferenceServiceClientComponent::class.java
    ).getRepository(packageName)
}

/**
 * Get currently set value of the preference described by [screenKey] and [preferenceKey]; taken
 * from the state of the repository linked with specific [packageName].
 */
fun DeviceState.getCurrentlySetPreferenceValue(
    packageName: String = SETTINGS_PACKAGE_NAME,
    screenKey: String,
    preferenceKey: String
): SettingsPreferenceValue? {
    return getSettingsPreferenceRepository(packageName)
        .filterPreferenceStateThroughAllChangedPreferences(screenKey, preferenceKey).currentPreferenceValue
}

const val SETTINGS_PACKAGE_NAME = "com.android.settings"
