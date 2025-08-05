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

import android.annotation.SuppressLint
import android.service.settings.preferences.GetValueResult
import android.service.settings.preferences.SetValueResult
import android.service.settings.preferences.SettingsPreferenceMetadata
import android.service.settings.preferences.SettingsPreferenceValue
import android.util.Log

/**
 * Additional layer for handling settings preferences, containing state and [BlockingSettingsPreferenceServiceClient].
 */
class SettingsPreferenceRepository(
    private val packageName: String,
    private val changedPreferences: MutableMap<String, ChangedPreferenceState> = mutableMapOf()
) {

    private val blockingClient by lazy { BlockingSettingsPreferenceServiceClient(packageName) }

    /**
     * Wrapper for [BlockingSettingsPreferenceServiceClient.allMetadata]
     */
    val allMetadata by lazy { blockingClient.allMetadata }

    private fun cachePreferenceStateIfNew(metadata: SettingsPreferenceMetadata) {
        if (changedPreferences[metadata.nameWithKeys()] == null) {
            changedPreferences[metadata.nameWithKeys()] = ChangedPreferenceState(
                metadata = metadata,
                originalPreferenceValue = blockingClient.getValueResult(metadata).value,
                currentPreferenceValue = null
            )
        }
    }

    internal fun filterPreferenceStateThroughAllChangedPreferences(
        screenKey: String,
        preferenceKey: String
    ): ChangedPreferenceState {
        return changedPreferences.values.single { preference ->
            preference.metadata.screenKey == screenKey && preference.metadata.key == preferenceKey
        }
    }

    /**
     * Wrapper for [BlockingSettingsPreferenceServiceClient.getValueResult]
     */
    fun getValueResult(
        metadata: SettingsPreferenceMetadata,
        grantRequiredPermissions: Boolean = true
    ): GetValueResult {
        return blockingClient.getValueResult(metadata, grantRequiredPermissions)
    }

    /**
     * Wrapper for [BlockingSettingsPreferenceServiceClient.setValueResult]
     */
    fun setValueResult(
        metadata: SettingsPreferenceMetadata,
        settingsPreferenceValue: SettingsPreferenceValue,
        grantRequiredPermissions: Boolean = true,
    ): SetValueResult {
        cachePreferenceStateIfNew(metadata)
        return blockingClient.setValueResult(
            metadata = metadata,
            settingsPreferenceValue = settingsPreferenceValue,
            grantRequiredPermissions = grantRequiredPermissions
        ).also { setResult ->
            if (setResult.resultCode == SetValueResult.RESULT_OK) {
                changedPreferences[metadata.nameWithKeys()]?.copy(currentPreferenceValue = settingsPreferenceValue)?.let { newState ->
                    changedPreferences[metadata.nameWithKeys()] = newState
                }
            }
        }
    }

    @SuppressLint("LongLogTag")
    internal fun restorePreferencesToOldValuesAndClear() {
        changedPreferences.values.forEach { preference ->
            blockingClient.setValueResult(
                metadata = preference.metadata,
                settingsPreferenceValue = preference.originalPreferenceValue!!
            ).also { result ->
                if (result.resultCode != SetValueResult.RESULT_OK) {
                    Log.e(TAG, "Restoring preference $preference to old state unsuccessful. " +
                            "Result code: ${result.resultCode}")
                }
            }
        }
        changedPreferences.clear()
    }

    internal fun closeClient() {
        blockingClient.client.close()
    }

    companion object {
        private const val TAG = "SettingsPreferenceRepository"
    }
}